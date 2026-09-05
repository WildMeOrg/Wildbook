package org.ecocean;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.transaction.Status;
import org.datanucleus.enhancement.Persistable;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;



public class IndexingManager {

    // The ScheduledExecutorService executes indexing jobs (and re-schedules retries).
    private final ScheduledExecutorService executor;

    // The indexingQueue is a List of Strings that represent the UUIDs of Base class-implementing
    // objects (Encounter, MarkedIndividual, Annotation, etc.) that need to be indexed or unindexed.
    // The queue ensures that overzealous calls from the WildbookLifecycleListener do not cause
    // unnecessary, duplicate indexing jobs. The UUIDs of the objects being indexed are removed
    // from the queue once the job reaches a terminal outcome (success, give-up, or could-not-schedule).
    // An id intentionally stays in the queue across retries so concurrent postStore events for the
    // same object are deduped while a job is pending.
    private List<String> indexingQueue = Collections.synchronizedList(new ArrayList<String>());

    // Stable lock for all compound (check-then-act) mutations of indexingQueue. We cannot synchronize
    // on indexingQueue itself because resetIndexingQueuehWithInitialCapacity() reassigns the field.
    private final Object queueLock = new Object();

    // Objects that were requested again while a job for them was already scheduled or running.
    // The dedupe in addIndexingQueueEntry() must NOT simply drop that later request: the in-flight
    // job may have loaded the object BEFORE the change behind the later request was committed, so
    // the later request is the one that describes the object's final state. We remember the newest
    // (class, unindex) per id and run exactly one more pass when the in-flight job reaches a
    // terminal outcome (finishIndexingJob). Guarded by queueLock.
    private Map<String, Requeued> requeue = new HashMap<String, Requeued>();

    private static final class Requeued {
        final Class myClass;
        final boolean unindex;

        Requeued(Class myClass, boolean unindex) {
            this.myClass = myClass;
            this.unindex = unindex;
        }
    }

    // Total number of attempts (the initial attempt plus retries) before giving up on an object that
    // cannot be found in the datastore. The common cause is the postStore-before-commit race: the
    // indexing job is enqueued on JDO flush, but the creating transaction has not committed yet, so the
    // row is not visible to this background thread's separate connection (JDOObjectNotFoundException).
    // Package-private so tests can drive the give-up path without hardcoding the number.
    static final int MAX_INDEXING_ATTEMPTS = 6;

    // Delay (seconds) BEFORE the next attempt, indexed by the just-failed attempt number (1-based).
    // Length is MAX_INDEXING_ATTEMPTS - 1. Cumulative budget ~62s, comfortably longer than a typical
    // detection-callback transaction. Retries are scheduled (not slept), so no worker thread is held.
    private static final long[] RETRY_DELAY_SECONDS = { 2L, 4L, 8L, 16L, 32L };

    public IndexingManager() {
        int numAllowedThreads = 4;
        Properties props = ShepherdProperties.getProperties("OpenSearch.properties", "", "context0");
        if (props != null) {
            String indexingNumAllowedThreads = props.getProperty("indexingNumAllowedThreads");
            if (indexingNumAllowedThreads != null) {
                Integer allowThreads = Integer.getInteger(indexingNumAllowedThreads);
                if (allowThreads != null) numAllowedThreads = allowThreads.intValue();
            }
        }
        executor = Executors.newScheduledThreadPool(numAllowedThreads);
    }

    /*
     * Test seam: drive the scheduler deterministically. The production constructor's pool runs
     * jobs that open a Shepherd and hit the database, which a unit test must never do.
     */
    IndexingManager(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    // Returns the indexing queue List of Strings
    public List<String> getIndexingQueue() { return indexingQueue; }

    /*
     * Adds a Base object to the queue for indexing or unindexing
     * @Base base The Base-class implementing object to be indexed or unindexed
     * @boolean unindex Whether the object is to be indexed or unindexed.
     */
    public void addIndexingQueueEntry(Base base, boolean unindex) {
        if (base == null) return;
        addIndexingQueueEntry(base.getId(), base.getClass(), unindex);
    }

    /*
     * Same, by identity rather than instance. Callers that run after a transaction has ended must
     * use this form: the persistent instance may belong to a PersistenceManager that is closed.
     */
    public void addIndexingQueueEntry(String objectID, Class myClass, boolean unindex) {
        if ((objectID == null) || (myClass == null)) return;
        // Atomic check-then-act so two concurrent requests for the same object schedule only one job.
        synchronized (queueLock) {
            if (indexingQueue.contains(objectID)) {
                // A job for this id is already scheduled or running. Remember this request rather
                // than dropping it: the in-flight job may have loaded the object before the change
                // behind this call was committed. Newer requests overwrite older ones on purpose --
                // the newest describes the object's final state. finishIndexingJob() runs it.
                requeue.put(objectID, new Requeued(myClass, unindex));
                return;
            }
            indexingQueue.add(objectID);
        }
        // IMPORTANT - no persistent objects can be referenced inside the job; we carry only the
        // (id, class) and re-fetch by id on each attempt.
        scheduleIndexingJob(objectID, myClass, unindex, 1, 0L);
    }

    /*
     * Terminal outcome of one object's indexing job: success, a genuine failure, or a retry give-up.
     * If a newer request for the same id arrived while that job was scheduled, running, or retrying,
     * consume it and run exactly one more pass -- leaving the id in indexingQueue so concurrent
     * requests keep deduping against the follow-up. Otherwise release the id.
     *
     * Package-private so tests can mark "the job finished" without running a job (which would
     * open a Shepherd and touch the database).
     */
    void finishIndexingJob(String objectID) {
        Requeued again = null;

        synchronized (queueLock) {
            again = requeue.remove(objectID);
            if (again == null) {
                indexingQueue.remove(objectID);
                return;
            }
        }
        scheduleIndexingJob(objectID, again.myClass, again.unindex, 1, 0L);
    }

    // GH-1514: queue deep reindex for each MarkedIndividual identified by id,
    // so sibling encounters pick up refreshed individualNumberEncounters (and
    // the other individual-derived denormalized fields on the encounter index).
    // Safe to call with an empty or null set. Callers should invoke this AFTER
    // the caller's DB transaction has committed, since IndexingManager spins
    // a background Shepherd that reads the individual by id.
    //
    // Opens its own short-lived read-only Shepherd for the id->object resolution
    // rather than reusing the caller's. Callers in servlets typically close their
    // Shepherd in a finally block before (or alongside) queueing; reusing it here
    // would silently no-op because getMarkedIndividualQuiet uses the underlying
    // closed PersistenceManager. The passed-in Shepherd is used only for its
    // context string.
    public static void queueIndividualsByIdForDeepReindex(Shepherd myShepherd,
        java.util.Collection<String> individualIds) {
        if ((individualIds == null) || individualIds.isEmpty()) return;
        // honor the global ops kill-switch, same as enqueueAclReindex()
        if (OpenSearch.skipAutoIndexing()) return;
        IndexingManager im = IndexingManagerFactory.getIndexingManager();
        if (im == null) return;
        String context = (myShepherd != null) ? myShepherd.getContext() : "context0";
        Shepherd shep = new Shepherd(context);
        shep.setAction("IndexingManager.queueIndividualsByIdForDeepReindex");
        try {
            shep.beginDBTransaction();
            for (String id : individualIds) {
                if (id == null) continue;
                MarkedIndividual indiv = shep.getMarkedIndividualQuiet(id);
                if (indiv != null) im.addIndexingQueueEntry(indiv, false);
            }
        } finally {
            shep.rollbackAndClose();
        }
    }

    // Schedules (or immediately submits, when delaySeconds==0) one indexing attempt. Guarantees that the
    // queue entry is either handed to a running/scheduled job or removed if scheduling is rejected
    // (e.g. executor shutdown), so an id can never be orphaned in the queue.
    private void scheduleIndexingJob(final String objectID, final Class myClass, final boolean unindex,
        final int attempt, long delaySeconds) {
        final Runnable rn = new IndexingJob(objectID, myClass, unindex, attempt);

        try {
            executor.schedule(rn, delaySeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException rex) {
            // Executor is shutting down; do not leak the queue entry. Deliberately a hard remove
            // rather than finishIndexingJob(): the executor is gone, so scheduling a follow-up would
            // only be rejected again. The background reconciler is the backstop.
            System.out.println("IndexingManager: could not schedule indexing job for " + objectID +
                " (executor shutdown?); removing from queue. " + rex);
            removeIndexingQueueEntry(objectID);
        }
    }

    /*
     * How one indexing attempt reaches the datastore and the index. Production opens a Shepherd
     * (ShepherdIndexingWork); tests substitute a scripted implementation so a real IndexingJob can
     * run on the test thread and prove it reports every outcome correctly.
     */
    interface IndexingWork {
        /** Load the object by id; throws javax.jdo.JDOObjectNotFoundException if not visible. */
        Base load(Class myClass, String objectID) throws Exception;

        void index(Base base, boolean unindex) throws Exception;

        /** Always called, exactly once, after load/index -- whatever happened. */
        void close();
    }

    // Overridable seam (package-private) -- see IndexingJobWiringTest.
    IndexingWork newIndexingWork(String objectID) {
        return new ShepherdIndexingWork(objectID);
    }

    /*
     * The production IndexingWork: a fresh Shepherd on its own connection.
     *
     * The reader PersistenceManager bypasses the level-2 cache. DataNucleus publishes an object's
     * new state into L2 during its preCommit -- BEFORE the datastore commit -- so with L2 reads on,
     * this job could pick up another in-flight transaction's not-yet-committed (possibly about to
     * be rolled back) copy instead of the committed row. Bypassing L2 for reads makes the job read
     * the database, which is the only thing that is guaranteed committed by the time the job runs.
     * L2 is still written to, so nothing else is affected.
     */
    static final class ShepherdIndexingWork implements IndexingWork {
        static final String L2_RETRIEVE_MODE_PROPERTY = "datanucleus.cache.level2.retrieveMode";
        private final Shepherd shepherd;

        ShepherdIndexingWork(String objectID) {
            shepherd = new Shepherd("context0");
            shepherd.setAction("IndexingManager_" + objectID);
            shepherd.beginDBTransaction();
            try {
                PersistenceManager pm = shepherd.getPM();
                if (pm != null) pm.setProperty(L2_RETRIEVE_MODE_PROPERTY, "bypass");
            } catch (Exception ex) {
                System.out.println("IndexingManager: could not set " + L2_RETRIEVE_MODE_PROPERTY +
                    "=bypass on the reader PersistenceManager for " + objectID + ": " + ex);
            }
        }

        Shepherd shepherd() {
            return shepherd;
        }

        public Base load(Class myClass, String objectID) {
            return (Base)shepherd.getPM().getObjectById(myClass, objectID);
        }

        public void index(Base base, boolean unindex) throws Exception {
            if (unindex) {
                base.opensearchUnindexDeep();
            } else {
                base.opensearchIndexDeep();
            }
        }

        public void close() {
            shepherd.rollbackAndClose();
        }
    }

    /*
     * One indexing attempt for one object. A named class (not an anonymous Runnable) so the request
     * it carries is observable: tests capture what was handed to the scheduler and assert on it.
     */
    final class IndexingJob implements Runnable {
        final String objectID;
        final Class myClass;
        final boolean unindex;
        final int attempt;

        IndexingJob(String objectID, Class myClass, boolean unindex, int attempt) {
            this.objectID = objectID;
            this.myClass = myClass;
            this.unindex = unindex;
            this.attempt = attempt;
        }

        public void run() {
            IndexingWork work = null;
            try {
                work = newIndexingWork(objectID);
                Base base = null;
                try {
                    base = work.load(myClass, objectID);
                } catch (javax.jdo.JDOObjectNotFoundException nf) {
                    // Object not visible to this connection. Almost always the postStore-before-commit
                    // race; handle (retry the index path) and stop here for this attempt.
                    handleObjectNotFound(objectID, myClass, unindex, attempt);
                    return;
                }
                work.index(base, unindex);
                // success - terminal (unless a newer request landed while we were running)
                finishIndexingJob(objectID);
            } catch (Exception e) {
                // A genuine indexing failure (not the commit race), or a failure setting up the
                // Shepherd. Make it visible rather than silently swallowing it, then drop it; the
                // background reconciler (OpenSearch.opensearchSyncIndex) will recover it on its next
                // pass if the row exists.
                System.out.println("IndexingManager: WARNING - indexing failed for " + objectID +
                    " (attempt " + attempt + "/" + MAX_INDEXING_ATTEMPTS + "); dropping. " +
                    "Background reconciler will recover it if it exists. " + e);
                e.printStackTrace();
                finishIndexingJob(objectID);
            } finally {
                if (work != null) work.close();
            }
        }
    }

    // Handles the "row not visible" case. For the index path this is the commit-visibility race, so we
    // retry with backoff (leaving the id in the queue to dedup concurrent events). For the unindex path a
    // missing row means the object is already gone and there is nothing to deep-unindex, so we give up.
    // Package-private so tests can drive the retry and give-up paths without a datastore.
    void handleObjectNotFound(final String objectID, final Class myClass, final boolean unindex,
        final int attempt) {
        if (!unindex && attempt < MAX_INDEXING_ATTEMPTS) {
            long delay = RETRY_DELAY_SECONDS[attempt - 1];
            System.out.println("IndexingManager: object " + objectID + " not yet visible (attempt " +
                attempt + "/" + MAX_INDEXING_ATTEMPTS + "); likely an uncommitted transaction, retrying in " +
                delay + "s");
            scheduleIndexingJob(objectID, myClass, unindex, attempt + 1, delay);
            // NOTE: id intentionally stays in indexingQueue across retries.
        } else {
            System.out.println("IndexingManager: object " + objectID + " still not found after " + attempt +
                " attempt(s); giving up (object may have been rolled back / never committed, or is an " +
                "unindex of an already-deleted row). Background reconciler will index it if it exists.");
            // Still a terminal outcome, so a remembered follow-up must not be swallowed: a request
            // that arrived while we were burning retries is precisely the one that CAN succeed now.
            finishIndexingJob(objectID);
        }
    }

    // Removes an object's UUID from the queue, dropping any remembered follow-up with it. This is the
    // hard release; job outcomes go through finishIndexingJob() so a follow-up survives.
    public void removeIndexingQueueEntry(String objectID) {
        synchronized (queueLock) {
            indexingQueue.remove(objectID);
            requeue.remove(objectID);
        }
    }

    // Resets the indexing queue (and any remembered follow-ups)
    public void resetIndexingQueuehWithInitialCapacity(int initialCapacity) {
        synchronized (queueLock) {
            indexingQueue = Collections.synchronizedList(new ArrayList<String>());
            requeue = new HashMap<String, Requeued>();
        }
    }

    public void shutdown() {
        if (executor != null) executor.shutdown();
    }

    // =====================================================================================
    // Deferred (post-commit) indexing
    //
    // WildbookLifecycleListener.postStore() fires on JDO flush/store, NOT on commit. Enqueuing
    // there means the background job -- which opens its own Shepherd and therefore its own JDBC
    // connection -- can load the row BEFORE the writing transaction commits, and index pre-change
    // state. Nothing detects that: for a row that already exists the reload SUCCEEDS (only a
    // brand-new row raises JDOObjectNotFoundException, which is what the retry path handles), so
    // the job reports success and the stale document sticks.
    //
    // So postStore no longer enqueues. It PARKS (id, class, unindex, DataNucleus identity) against
    // the PersistenceManager doing the write -- in the PM's own JDO user-object map, which is
    // identity-scoped by construction and is collected together with the PM. The park is released
    // by the transaction's own completion callback (IndexingTransactionSynchronization, a
    // javax.transaction.Synchronization registered once per PM): drained if the transaction
    // committed, dropped if it rolled back. Nothing else may terminalize a park: not "commit threw"
    // (DataNucleus leaves the transaction ACTIVE after a NucleusUserException, and the park must
    // stay live with it), not close.
    //
    // Locking: bucket state is guarded by the bucket's own monitor and nothing else. We never hold
    // a lock of ours while calling into a PersistenceManager, and the PM's user-object map is a
    // plain HashMap -- concurrent use of one PM from two threads is unsupported here as everywhere.
    // =====================================================================================

    public static final String PENDING_USER_OBJECT_KEY = "org.ecocean.IndexingManager.pending";

    /** How the writing transaction ended, as reported by the JDO/JTA completion callback. */
    enum Outcome { COMMITTED, ROLLED_BACK, UNKNOWN }

    /*
     * Explicit mapping of the javax.transaction.Status the Synchronization receives. DataNucleus
     * 5.2 only ever reports COMMITTED or ROLLEDBACK from its resource-local transaction (heuristic
     * outcomes are turned into a rollback attempt first). Anything else is evidence we do not
     * understand, and must be treated as neither a proven commit nor a proven rollback.
     */
    static Outcome outcomeOf(int jtaStatus) {
        switch (jtaStatus) {
        case Status.STATUS_COMMITTED:
            return Outcome.COMMITTED;
        case Status.STATUS_ROLLEDBACK:
            return Outcome.ROLLED_BACK;
        default:
            System.out.println("IndexingManager: WARNING - transaction completed with unexpected " +
                "javax.transaction.Status " + jtaStatus + "; treating as UNKNOWN (index requests " +
                "will run, unindex requests will not)");
            return Outcome.UNKNOWN;
        }
    }

    /** What PendingBucket.park() decided for one request. */
    enum ParkResult { PARKED, ENQUEUE_NOW, DROP }

    // One deferred index request. Only identity is kept -- never the Persistable, whose
    // PersistenceManager may be closed by the time the entry is drained.
    static final class PendingEntry {
        final String objectID;
        final Class myClass;
        final boolean unindex;
        // DataNucleus INTERNAL identity (Persistable.dnGetObjectId()), captured while the PM is
        // open, for level-2 eviction at drain time. The level-2 cache is keyed by this, not by
        // the javax.jdo.identity.* object JDOHelper.getObjectId() returns -- evicting with the
        // latter silently misses. May be null (transient object, or capture failed).
        final Object dnObjectId;

        PendingEntry(String objectID, Class myClass, boolean unindex, Object dnObjectId) {
            this.objectID = objectID;
            this.myClass = myClass;
            this.unindex = unindex;
            this.dnObjectId = dnObjectId;
        }

        // identity of the REQUEST, so repeated parks of the same request collapse. dnObjectId is
        // derived from objectID and is deliberately excluded.
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PendingEntry)) return false;
            PendingEntry pe = (PendingEntry)other;
            return this.unindex == pe.unindex && this.objectID.equals(pe.objectID) &&
                   this.myClass.equals(pe.myClass);
        }

        @Override public int hashCode() {
            return (objectID.hashCode() * 31 + myClass.hashCode()) * 31 + (unindex ? 1 : 0);
        }
    }

    /*
     * The per-PersistenceManager park: a set of requests plus, once the transaction has completed,
     * its outcome. The first completion outcome sticks; later completion signals are ignored.
     *
     * A request that arrives AFTER completion (a parker that read this bucket just before the
     * transaction ended) is never retained: if the transaction committed the change is durable and
     * the request must run now; if it rolled back the request is dropped; if the outcome is unknown,
     * an index request runs now (harmless -- the job re-reads the database) and an unindex is
     * dropped (never run an ambiguous delete).
     */
    static final class PendingBucket {
        final PersistenceManagerFactory pmf; // for level-2 eviction after the PM may be closed
        private final Set<PendingEntry> entries = new LinkedHashSet<PendingEntry>(); // guarded by this
        private Outcome outcome = null; // guarded by this; non-null once completed

        PendingBucket(PersistenceManagerFactory pmf) {
            this.pmf = pmf;
        }

        synchronized ParkResult park(PendingEntry pe) {
            if (outcome == null) {
                entries.add(pe);
                return ParkResult.PARKED;
            }
            switch (outcome) {
            case COMMITTED:
                return ParkResult.ENQUEUE_NOW;
            case ROLLED_BACK:
                return ParkResult.DROP;
            default:
                return pe.unindex ? ParkResult.DROP : ParkResult.ENQUEUE_NOW;
            }
        }

        /** Terminalize with this outcome; returns the requests that should now run (maybe none). */
        synchronized List<PendingEntry> complete(Outcome how) {
            if (outcome != null) return Collections.<PendingEntry>emptyList();
            outcome = how;
            List<PendingEntry> toRun = new ArrayList<PendingEntry>();
            for (PendingEntry pe : entries) {
                if ((how == Outcome.COMMITTED) || ((how == Outcome.UNKNOWN) && !pe.unindex))
                    toRun.add(pe);
            }
            entries.clear();
            return toRun;
        }

        synchronized boolean isCompleted() {
            return outcome != null;
        }

        synchronized int size() {
            return entries.size();
        }
    }

    // Non-throwing read of the park on an OPEN pm (the transaction need not be active: completion
    // callbacks run after it has ended). Returns null when there is nothing there.
    private static PendingBucket bucketOf(PersistenceManager pm) {
        if (pm == null) return null;
        try {
            if (pm.isClosed()) return null;
            Object o = pm.getUserObject(PENDING_USER_OBJECT_KEY);
            return (o instanceof PendingBucket) ? (PendingBucket)o : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static PersistenceManagerFactory pmfOf(PersistenceManager pm) {
        if (pm == null) return null;
        try {
            return pm.getPersistenceManagerFactory();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Make this PersistenceManager's transaction report its completion to the park. Called once
     * per PM from Shepherd.beginDBTransaction(), before the first begin(); DataNucleus keeps the
     * registration across the PM's successive transactions. Idempotent; wraps (and keeps calling)
     * any Synchronization somebody else registered first, because JDO allows exactly one; never
     * throws.
     */
    public static void registerSynchronization(PersistenceManager pm) {
        if (pm == null) return;
        try {
            if (pm.isClosed()) return;
            javax.jdo.Transaction tx = pm.currentTransaction();
            javax.transaction.Synchronization existing = tx.getSynchronization();
            if (existing instanceof IndexingTransactionSynchronization) return;
            tx.setSynchronization(new IndexingTransactionSynchronization(pm, pmfOf(pm), existing));
        } catch (Exception ex) {
            System.out.println("IndexingManager.registerSynchronization() failed: " + ex);
        }
    }

    /**
     * Give this PersistenceManager a live park. Called from Shepherd.beginDBTransaction() for a
     * freshly obtained PM; idempotent, never throws, and never replaces a LIVE park (a redundant
     * begin on an already-active transaction must not drop what that transaction has parked).
     * A completed park is replaced -- normally completion installs its own successor, so this is
     * only a safety net.
     */
    public static void installPendingBucket(PersistenceManager pm) {
        if (pm == null) return;
        try {
            if (pm.isClosed()) return;
            PendingBucket existing = bucketOf(pm);
            if ((existing != null) && !existing.isCompleted()) return;
            pm.putUserObject(PENDING_USER_OBJECT_KEY, new PendingBucket(pmfOf(pm)));
        } catch (Exception ex) {
            System.out.println("IndexingManager.installPendingBucket() failed: " + ex);
        }
    }

    /**
     * Park an index/unindex request until the PersistenceManager's transaction completes.
     *
     * Deferral only applies while a transaction is ACTIVE on an open PM that has a park. Anything
     * else -- no PM (transient object), closed PM, no transaction (caller already committed, e.g.
     * IndividualRemoveEncounter), a PM Shepherd never prepared -- gets the old immediate enqueue:
     * there is no completion coming that could release a parked request, so parking would lose it.
     */
    public static void addPendingEntry(PersistenceManager pm, Base base, boolean unindex) {
        if (base == null) return;
        String objectID = base.getId();
        if (objectID == null) return;
        Class myClass = base.getClass();
        PendingBucket bucket = null;
        try {
            if ((pm != null) && !pm.isClosed() && pm.currentTransaction().isActive())
                bucket = bucketOf(pm);
        } catch (Exception ex) {
            bucket = null;
        }
        if (bucket == null) {
            enqueueNow(objectID, myClass, unindex);
            return;
        }
        PendingEntry pe = new PendingEntry(objectID, myClass, unindex, dataNucleusIdentity(base));
        if (bucket.park(pe) == ParkResult.ENQUEUE_NOW) enqueueNow(objectID, myClass, unindex);
    }

    // The DataNucleus internal identity -- the key the level-2 cache actually uses.
    private static Object dataNucleusIdentity(Base base) {
        if (!(base instanceof Persistable)) return null;
        try {
            return ((Persistable)base).dnGetObjectId();
        } catch (Exception ex) {
            return null; // non-fatal: we simply skip the level-2 eviction for this object
        }
    }

    /**
     * Step one of completion: terminalize the PM's park with this outcome, take the requests that
     * should now run, and install a fresh live park so the PM's NEXT transaction has somewhere to
     * park -- however it is begun (Shepherd, or a raw pm.currentTransaction().begin()). Does no
     * external work and never throws, so it can run before any delegate Synchronization gets a
     * chance to close the PM. Follow with drainCaptured().
     */
    static List<PendingEntry> capturePendingEntries(PersistenceManager pm, Outcome how) {
        PendingBucket bucket = bucketOf(pm);

        if (bucket == null) return Collections.<PendingEntry>emptyList();
        List<PendingEntry> snapshot = bucket.complete(how);
        try {
            if (!pm.isClosed()) pm.putUserObject(PENDING_USER_OBJECT_KEY, new PendingBucket(bucket.pmf));
        } catch (Exception ex) {
            System.out.println("IndexingManager: could not install the successor park: " + ex);
        }
        return snapshot;
    }

    /**
     * Step two of completion: hand each captured request to the queue. Each object is first evicted
     * from the (PMF-wide, shared) level-2 cache by its DataNucleus identity, so no reader picks up
     * a copy published before this commit; the indexing job itself additionally bypasses L2 (see
     * ShepherdIndexingWork). Every step is isolated per entry: one failure never takes the rest of
     * the snapshot down, and nothing here throws. Independent of the PM, which may be closed.
     */
    static void drainCaptured(List<PendingEntry> snapshot, PersistenceManagerFactory pmf) {
        if ((snapshot == null) || snapshot.isEmpty()) return;
        for (PendingEntry pe : snapshot) {
            if ((pmf != null) && (pe.dnObjectId != null)) {
                try {
                    pmf.getDataStoreCache().evict(pe.dnObjectId);
                } catch (Exception ex) {
                    System.out.println("IndexingManager: level-2 evict failed for " + pe.objectID +
                        ": " + ex);
                }
            }
            try {
                enqueueNow(pe.objectID, pe.myClass, pe.unindex);
            } catch (Exception ex) {
                System.out.println("IndexingManager: post-commit enqueue failed for " + pe.objectID +
                    "; background reconciler is the backstop. " + ex);
            }
        }
    }

    /** capturePendingEntries() + drainCaptured(), for callers without a delegate to interleave. */
    public static void completePendingEntries(PersistenceManager pm, Outcome how) {
        PersistenceManagerFactory pmf = pmfOf(pm);

        drainCaptured(capturePendingEntries(pm, how), pmf);
    }

    private static void enqueueNow(String objectID, Class myClass, boolean unindex) {
        IndexingManager im = IndexingManagerFactory.getIndexingManager();

        if (im == null) {
            System.out.println("IndexingManager: no IndexingManager available; dropping request for " +
                objectID + " (background reconciler is the backstop)");
            return;
        }
        im.addIndexingQueueEntry(objectID, myClass, unindex);
    }

}
