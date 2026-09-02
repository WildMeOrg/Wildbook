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
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
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

    // Objects whose state changed again while an indexing job for them was already scheduled or
    // running. The dedupe in addIndexingQueueEntry() must NOT simply drop the later request: the
    // in-flight job may have loaded the object BEFORE that change was committed, which is exactly
    // the pre-commit stale read this class used to bake into the index. We instead remember the
    // newest (class, unindex) for the id and run one more pass when the in-flight job reaches a
    // terminal outcome. Guarded by queueLock.
    private Map<String, PendingEntry> requeue = new HashMap<String, PendingEntry>();

    // Total number of attempts (the initial attempt plus retries) before giving up on an object that
    // cannot be found in the datastore. The common cause is the postStore-before-commit race: the
    // indexing job is enqueued on JDO flush, but the creating transaction has not committed yet, so the
    // row is not visible to this background thread's separate connection (JDOObjectNotFoundException).
    private static final int MAX_INDEXING_ATTEMPTS = 6;

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
     * Test seam. Lets a test drive the scheduler deterministically -- the real constructor's pool
     * runs jobs that open a Shepherd and hit the database, which a unit test must not do.
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
     * Same as above, by identity rather than instance. Used by drainPendingEntries(), which runs
     * after the writing PersistenceManager's transaction has committed and must not touch the
     * (now potentially closed) persistent instance.
     */
    public void addIndexingQueueEntry(String objectID, Class myClass, boolean unindex) {
        if ((objectID == null) || (myClass == null)) return;
        // Atomic check-then-act so two concurrent postStore events for the same object schedule
        // only one job.
        synchronized (queueLock) {
            if (indexingQueue.contains(objectID)) {
                // A job for this id is already scheduled or running. Do NOT drop this request:
                // that job may have loaded the object before the change behind this call was
                // committed. Record the newest request; finishIndexingJob() runs it once the
                // in-flight job finishes. Later requests overwrite earlier ones on purpose --
                // the last one describes the object's final state.
                requeue.put(objectID, new PendingEntry(objectID, myClass, unindex, null));
                return;
            }
            indexingQueue.add(objectID);
        }
        // IMPORTANT - no persistent objects can be referenced inside the job; we carry only the
        // (id, class) and re-fetch by id on each attempt.
        scheduleIndexingJob(objectID, myClass, unindex, 1, 0L);
    }

    /*
     * Terminal outcome for one object's indexing job (success, give-up, or a genuine failure).
     * If a newer request for the same id arrived while the job was scheduled, running, or
     * retrying, consume it and run exactly one more pass -- leaving the id in indexingQueue so
     * concurrent events keep deduping against the follow-up. Otherwise release the id.
     *
     * This is what makes the post-commit handoff correct even when a pre-commit caller (e.g.
     * Encounter.enqueueAclReindex(), which business code still calls inside the transaction) has
     * already started a job: drainPendingEntries() runs after commit() returns, so the follow-up
     * pass it triggers is guaranteed to load committed state.
     */
    private void finishIndexingJob(String objectID) {
        PendingEntry again = null;

        synchronized (queueLock) {
            again = requeue.remove(objectID);
            if (again == null) {
                indexingQueue.remove(objectID);
                return;
            }
        }
        scheduleIndexingJob(again.objectID, again.myClass, again.unindex, 1, 0L);
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
        final Runnable rn = new Runnable() {
            public void run() {
                Shepherd bgShepherd = null;
                try {
                    bgShepherd = new Shepherd("context0");
                    bgShepherd.setAction("IndexingManager_" + objectID);
                    bgShepherd.beginDBTransaction();
                    Base base = null;
                    try {
                        base = (Base)bgShepherd.getPM().getObjectById(myClass, objectID);
                    } catch (javax.jdo.JDOObjectNotFoundException nf) {
                        // Object not visible to this connection. Almost always the postStore-before-commit
                        // race; handle (retry the index path) and stop here for this attempt.
                        handleObjectNotFound(objectID, myClass, unindex, attempt);
                        return;
                    }
                    if (unindex) {
                        base.opensearchUnindexDeep();
                    } else {
                        base.opensearchIndexDeep();
                    }
                    // success - terminal (unless a newer change landed while we were running)
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
                    if (bgShepherd != null) bgShepherd.rollbackAndClose();
                }
            }
        };

        try {
            executor.schedule(rn, delaySeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException rex) {
            // Executor is shutting down; do not leak the queue entry.
            // Deliberately a hard remove rather than finishIndexingJob(): the executor is gone, so
            // re-scheduling a follow-up would only be rejected again. Anything queued for this id
            // is dropped with it; the background reconciler is the backstop.
            System.out.println("IndexingManager: could not schedule indexing job for " + objectID +
                " (executor shutdown?); removing from queue. " + rex);
            removeIndexingQueueEntry(objectID);
        }
    }

    // Handles the "row not visible" case. For the index path this is the commit-visibility race, so we
    // retry with backoff (leaving the id in the queue to dedup concurrent events). For the unindex path a
    // missing row means the object is already gone and there is nothing to deep-unindex, so we give up.
    private void handleObjectNotFound(final String objectID, final Class myClass, final boolean unindex,
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
            // Still a terminal outcome, so a queued follow-up request must not be swallowed: a
            // post-commit drain may have marked this id while we were burning retries, and that
            // request is precisely the one that CAN succeed now.
            finishIndexingJob(objectID);
        }
    }

    // Removes an object's UUID from the queue, dropping any queued follow-up with it. This is the
    // hard release; the job path uses finishIndexingJob() so a follow-up survives.
    public void removeIndexingQueueEntry(String objectID) {
        synchronized (queueLock) {
            indexingQueue.remove(objectID);
            requeue.remove(objectID);
        }
    }

    // Resets the indexing queue
    public void resetIndexingQueuehWithInitialCapacity(int initialCapacity) {
        synchronized (queueLock) {
            indexingQueue = Collections.synchronizedList(new ArrayList<String>());
            requeue = new HashMap<String, PendingEntry>();
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
    // the job reports success and the stale document sticks. On the paths that never touch
    // Encounter.modified it then never self-heals either, because the reconciler only reindexes
    // when the DB version is strictly greater than the indexed version.
    //
    // So postStore no longer enqueues. It parks (id, class, unindex) against the PersistenceManager
    // doing the write, and Shepherd drains the park AFTER commit() returns.
    //
    // The park lives in the PM's own JDO user-object map rather than a static Map keyed by PM:
    // that is identity-scoped by construction (no reliance on PersistenceManager equals/hashCode,
    // no pooled-PM aliasing) and it is collected together with the PM if a request dies without
    // reaching commit or rollback.
    // =====================================================================================

    private static final String PENDING_USER_OBJECT_KEY = "org.ecocean.IndexingManager.pending";

    // One deferred index request. Only identity is kept -- never the Persistable, whose
    // PersistenceManager is typically closed by the time the entry is drained.
    private static class PendingEntry {
        final String objectID;
        final Class myClass;
        final boolean unindex;
        // JDO identity, captured while the PM is open, used to evict the object from the
        // level-2 cache at drain time. May be null if it could not be resolved.
        final Object jdoObjectId;

        PendingEntry(String objectID, Class myClass, boolean unindex, Object jdoObjectId) {
            this.objectID = objectID;
            this.myClass = myClass;
            this.unindex = unindex;
            this.jdoObjectId = jdoObjectId;
        }

        // identity of the REQUEST, so repeated parks of the same object collapse. jdoObjectId is
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
     * The per-PersistenceManager park. Installed once by Shepherd.beginDBTransaction() so that
     * nothing else ever has to create it, which is what lets every other operation here take the
     * bucket's OWN monitor and never a shared static lock.
     *
     * Locking rule, and it matters: we must NEVER hold a lock of ours while calling into a
     * PersistenceManager. Lifecycle callbacks (postStore) can run while DataNucleus holds
     * PM-internal locks -- datanucleus.Multithreaded is enabled -- so a static lock held across
     * pm.getUserObject() would give PM-lock -> ours on one thread and ours -> PM-lock on another.
     * Every method below therefore reads the bucket from the PM first, then synchronizes only on
     * the bucket to touch its contents.
     */
    private static class PendingBucket {
        // captured while the PM is open, so drain can still reach the level-2 cache afterwards
        final PersistenceManagerFactory pmf;
        final Set<PendingEntry> entries = new LinkedHashSet<PendingEntry>(); // guarded by `this`

        // Terminal state, guarded by `this`. addPendingEntry() checks "is the transaction active"
        // and locks the bucket as two separate steps, so a commit can drain (or a rollback can
        // discard) in between. Without this the late request would be added to a bucket nobody
        // will ever look at again and silently lost -- which main did not do.
        boolean drained = false;   // commit already took the contents: a late request must run now
        boolean discarded = false; // transaction did not commit: a late request must be dropped

        PendingBucket(PersistenceManagerFactory pmf) { this.pmf = pmf; }
    }

    /**
     * Give this PersistenceManager somewhere to park deferred index requests. Called from
     * Shepherd.beginDBTransaction(); idempotent, and never throws.
     */
    public static void installPendingBucket(PersistenceManager pm) {
        if (pm == null) return;
        try {
            if (pm.isClosed()) return;
            PendingBucket existing = (PendingBucket)pm.getUserObject(PENDING_USER_OBJECT_KEY);
            if (existing != null) {
                boolean terminal = false;
                synchronized (existing) {
                    terminal = existing.drained || existing.discarded;
                }
                // A live park belongs to a transaction still in progress -- beginDBTransaction()
                // is also called for an already-active transaction. Replacing it would drop it.
                if (!terminal) return;
            }
            pm.putUserObject(PENDING_USER_OBJECT_KEY,
                new PendingBucket(pm.getPersistenceManagerFactory()));
        } catch (Exception ex) {
            System.out.println("IndexingManager.installPendingBucket() failed: " + ex);
        }
    }

    // Non-throwing read of the park. Returns null when there is nothing to defer to.
    private static PendingBucket pendingBucket(PersistenceManager pm) {
        if (pm == null) return null;
        try {
            if (pm.isClosed()) return null;
            return (PendingBucket)pm.getUserObject(PENDING_USER_OBJECT_KEY);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Park an index/unindex request until the PersistenceManager's transaction commits.
     *
     * Deferral only applies while a transaction is ACTIVE. A caller that has already committed
     * (e.g. IndividualRemoveEncounter, which reindexes right after commitDBTransaction()) gets the
     * old immediate behavior -- parking those would mean discarding them at close.
     */
    public static void addPendingEntry(PersistenceManager pm, Base base, boolean unindex) {
        if (base == null) return;
        String objectID = base.getId();
        if (objectID == null) return;
        Class myClass = base.getClass();
        PendingBucket bucket = null;
        try {
            if ((pm != null) && !pm.isClosed() && pm.currentTransaction().isActive())
                bucket = pendingBucket(pm);
        } catch (Exception ex) {
            bucket = null;
        }
        if (bucket == null) {
            // no open transaction to defer to (or a PM that Shepherd did not set up)
            enqueueNow(objectID, myClass, unindex);
            return;
        }
        Object oid = null;
        try {
            oid = JDOHelper.getObjectId(base);
        } catch (Exception ex) {
            // non-fatal: we simply skip the level-2 eviction for this object
        }
        PendingEntry pe = new PendingEntry(objectID, myClass, unindex, oid);
        boolean tooLate = false;
        synchronized (bucket) {
            if (bucket.discarded) return;      // transaction did not commit; nothing to index
            if (bucket.drained) {
                tooLate = true;                // commit beat us here; queue it directly instead
            } else {
                bucket.entries.add(pe);
            }
        }
        if (tooLate) enqueueNow(objectID, myClass, unindex);
    }

    /**
     * Enqueue everything parked against this PersistenceManager. Called by Shepherd once the
     * transaction has ended in a way that may have persisted data (see Shepherd.commitDBTransaction()).
     *
     * Each object is evicted from the (PMF-wide, shared) level-2 cache immediately BEFORE its job
     * is scheduled. DataNucleus level-2 caching is on by default (type=soft) and Wildbook does not
     * disable it, so without this both the indexing job and any other in-flight request can read a
     * pre-commit copy of the object out of the cache instead of the committed row. Evicting after
     * scheduling would be too late -- the job can already be running.
     */
    public static void drainPendingEntries(PersistenceManager pm) {
        PendingBucket bucket = pendingBucket(pm);

        if (bucket == null) return;
        List<PendingEntry> taken = null;
        synchronized (bucket) {
            bucket.drained = true; // must be set even when empty, so a late parker queues directly
            if (bucket.entries.isEmpty()) return;
            taken = new ArrayList<PendingEntry>(bucket.entries);
            bucket.entries.clear();
        }
        for (PendingEntry pe : taken) {
            if ((bucket.pmf != null) && (pe.jdoObjectId != null)) {
                try {
                    bucket.pmf.getDataStoreCache().evict(pe.jdoObjectId);
                } catch (Exception ex) {
                    System.out.println("IndexingManager: level-2 evict failed for " + pe.objectID +
                        ": " + ex);
                }
            }
            enqueueNow(pe.objectID, pe.myClass, pe.unindex);
        }
    }

    /**
     * Drop everything parked against this PersistenceManager -- the transaction did not commit,
     * so there is nothing to index. Also used by closeDBTransaction() as pure cleanup; close is
     * never treated as evidence that a commit happened.
     */
    public static void discardPendingEntries(PersistenceManager pm) {
        PendingBucket bucket = pendingBucket(pm);

        if (bucket == null) return;
        synchronized (bucket) {
            // Only a bucket that has NOT been drained becomes discarded: commitDBTransaction()
            // drains and closeDBTransaction() then discards the same bucket, and that close must
            // not turn a committed transaction's park into a "drop everything" state.
            if (!bucket.drained) bucket.discarded = true;
            bucket.entries.clear();
        }
    }

    private static void enqueueNow(String objectID, Class myClass, boolean unindex) {
        try {
            IndexingManager im = IndexingManagerFactory.getIndexingManager();
            if (im != null) im.addIndexingQueueEntry(objectID, myClass, unindex);
        } catch (Exception ex) {
            System.out.println("IndexingManager.enqueueNow() failed for " + objectID + ": " + ex);
        }
    }

}
