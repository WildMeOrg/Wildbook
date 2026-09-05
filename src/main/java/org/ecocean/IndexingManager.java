package org.ecocean;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
                if (bgShepherd != null) bgShepherd.rollbackAndClose();
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

}
