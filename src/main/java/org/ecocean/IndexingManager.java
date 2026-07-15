package org.ecocean;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    // Returns the indexing queue List of Strings
    public List<String> getIndexingQueue() { return indexingQueue; }

    /*
     * Adds a Base object to the queue for indexing or unindexing
     * @Base base The Base-class implementing object to be indexed or unindexed
     * @boolean unindex Whether the object is to be indexed or unindexed.
     */
    public void addIndexingQueueEntry(Base base, boolean unindex) {
        String objectID = base.getId();
        Class myClass = base.getClass();
        // Atomic check-then-add so two concurrent postStore events for the same object schedule
        // only one job.
        synchronized (queueLock) {
            if (indexingQueue.contains(objectID)) return;
            indexingQueue.add(objectID);
        }
        // IMPORTANT - no persistent objects, such as the passed in Base, can be referenced inside the
        // job; we carry only the (id, class) and re-fetch by id on each attempt.
        scheduleIndexingJob(objectID, myClass, unindex, 1, 0L);
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
        IndexingManager im = IndexingManagerFactory.getIndexingManager();
        if (im == null) return;
        String context = (myShepherd != null) ? myShepherd.getContext() : "context0";
        Shepherd shep = new Shepherd(context);
        shep.setAction("IndexingManager.queueIndividualsByIdForDeepReindex");
        shep.beginDBTransaction();
        try {
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
                    // success - terminal
                    removeIndexingQueueEntry(objectID);
                } catch (Exception e) {
                    // A genuine indexing failure (not the commit race), or a failure setting up the
                    // Shepherd. Make it visible rather than silently swallowing it, then drop it; the
                    // background reconciler (OpenSearch.opensearchSyncIndex) will recover it on its next
                    // pass if the row exists.
                    System.out.println("IndexingManager: WARNING - indexing failed for " + objectID +
                        " (attempt " + attempt + "/" + MAX_INDEXING_ATTEMPTS + "); dropping. " +
                        "Background reconciler will recover it if it exists. " + e);
                    e.printStackTrace();
                    removeIndexingQueueEntry(objectID);
                } finally {
                    if (bgShepherd != null) bgShepherd.rollbackAndClose();
                }
            }
        };

        try {
            executor.schedule(rn, delaySeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException rex) {
            // Executor is shutting down; do not leak the queue entry.
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
            removeIndexingQueueEntry(objectID);
        }
    }

    // Removes an object's UUID from the queue
    public void removeIndexingQueueEntry(String objectID) {
        synchronized (queueLock) {
            indexingQueue.remove(objectID);
        }
    }

    // Resets the indexing queue
    public void resetIndexingQueuehWithInitialCapacity(int initialCapacity) {
        synchronized (queueLock) {
            indexingQueue = Collections.synchronizedList(new ArrayList<String>());
        }
    }

    public void shutdown() {
        if (executor != null) executor.shutdown();
    }

}
