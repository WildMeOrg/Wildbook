package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The indexing queue dedupes by object id. On main a second request for an id whose job is
 * already scheduled or running is simply DROPPED. That is wrong whenever the in-flight job may
 * have loaded the object before the change behind the second request was committed: the second
 * request is precisely the one that would index the right state.
 *
 * These tests pin the replacement behavior: a second request is remembered and runs as exactly
 * one follow-up pass once the in-flight job reaches a terminal outcome (success, failure, or
 * retry give-up). Only observable behavior is asserted: what gets handed to the scheduler and
 * what is in the public queue. No job ever runs -- the scheduler is a mock, so nothing touches a
 * datastore.
 */
class IndexingManagerRequeueTest {
    private static IndexingManager managerWith(ScheduledExecutorService exec) {
        return new IndexingManager(exec);
    }

    private static void assertScheduledCount(ScheduledExecutorService exec, int expected) {
        verify(exec, times(expected)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test void secondRequestWhileInFlight_runsExactlyOneFollowUpWhenTheJobFinishes() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-race-1", Encounter.class, false);
        assertTrue(im.getIndexingQueue().contains("enc-race-1"), "first request takes the slot");
        assertScheduledCount(exec, 1);

        // the change behind this second request may have committed AFTER the first job loaded
        im.addIndexingQueueEntry("enc-race-1", Encounter.class, false);
        assertScheduledCount(exec, 1); // deduped: still only the first job is scheduled

        im.finishIndexingJob("enc-race-1"); // first job reaches a terminal outcome
        assertScheduledCount(exec, 2); // ...so the remembered request runs as a follow-up pass
        assertTrue(im.getIndexingQueue().contains("enc-race-1"),
            "id stays queued across the follow-up so concurrent requests keep deduping");

        im.finishIndexingJob("enc-race-1"); // follow-up finishes; nothing newer arrived
        assertScheduledCount(exec, 2);
        assertFalse(im.getIndexingQueue().contains("enc-race-1"), "id released once clean");
    }

    @Test void threeRequestsWhileInFlight_collapseToOneFollowUp() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-race-2", Encounter.class, false);
        im.addIndexingQueueEntry("enc-race-2", Encounter.class, false);
        im.addIndexingQueueEntry("enc-race-2", Encounter.class, false);
        im.addIndexingQueueEntry("enc-race-2", Encounter.class, false);
        assertScheduledCount(exec, 1);

        im.finishIndexingJob("enc-race-2");
        assertScheduledCount(exec, 2); // one follow-up describes the final state; not three

        im.finishIndexingJob("enc-race-2");
        assertScheduledCount(exec, 2);
        assertFalse(im.getIndexingQueue().contains("enc-race-2"));
    }

    // The retry give-up path is a terminal outcome too. A request that arrived while the job was
    // burning "row not visible" retries is exactly the one that CAN succeed now.
    @Test void giveUpAfterMaxAttempts_stillRunsTheFollowUp() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-giveup-1", Encounter.class, false);
        im.addIndexingQueueEntry("enc-giveup-1", Encounter.class, false);
        assertScheduledCount(exec, 1);

        im.handleObjectNotFound("enc-giveup-1", Encounter.class, false,
            IndexingManager.MAX_INDEXING_ATTEMPTS);
        assertScheduledCount(exec, 2); // the follow-up, not another retry
        assertTrue(im.getIndexingQueue().contains("enc-giveup-1"));

        im.finishIndexingJob("enc-giveup-1");
        assertFalse(im.getIndexingQueue().contains("enc-giveup-1"));
    }

    // A retry that is NOT yet at the limit must keep retrying and must NOT consume the follow-up.
    @Test void retryBelowMaxAttempts_reschedulesAndKeepsTheFollowUp() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-retry-1", Encounter.class, false);
        im.addIndexingQueueEntry("enc-retry-1", Encounter.class, false);
        assertScheduledCount(exec, 1);

        im.handleObjectNotFound("enc-retry-1", Encounter.class, false, 1);
        assertScheduledCount(exec, 2); // the retry

        im.finishIndexingJob("enc-retry-1"); // the retry eventually succeeds
        assertScheduledCount(exec, 3); // and only now does the follow-up run
        im.finishIndexingJob("enc-retry-1");
        assertScheduledCount(exec, 3);
        assertFalse(im.getIndexingQueue().contains("enc-retry-1"));
    }

    // Hard release (used when the executor rejects work, and by the queue reset) drops the
    // follow-up along with the id. Otherwise a later request for the same id would find a stale
    // follow-up and run one pass too many -- or worse, an unindex that no longer applies.
    @Test void hardRemove_dropsTheFollowUpWithTheId() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-hard-1", Encounter.class, false);
        im.addIndexingQueueEntry("enc-hard-1", Encounter.class, false);
        assertScheduledCount(exec, 1);

        im.removeIndexingQueueEntry("enc-hard-1");
        assertFalse(im.getIndexingQueue().contains("enc-hard-1"));

        // a brand-new request must behave like a first request: one job, no phantom follow-up
        im.addIndexingQueueEntry("enc-hard-1", Encounter.class, false);
        assertScheduledCount(exec, 2);
        im.finishIndexingJob("enc-hard-1");
        assertScheduledCount(exec, 2);
        assertFalse(im.getIndexingQueue().contains("enc-hard-1"));
    }

    @Test void resetQueue_dropsAllFollowUps() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-reset-1", Encounter.class, false);
        im.addIndexingQueueEntry("enc-reset-1", Encounter.class, false);
        im.resetIndexingQueuehWithInitialCapacity(10);
        assertFalse(im.getIndexingQueue().contains("enc-reset-1"));

        im.addIndexingQueueEntry("enc-reset-1", Encounter.class, false);
        assertScheduledCount(exec, 2);
        im.finishIndexingJob("enc-reset-1");
        assertScheduledCount(exec, 2); // no phantom follow-up survived the reset
    }

    // The newest request wins: an index followed by an unindex for the same id must run the
    // UNINDEX as the follow-up, because it describes the object's final state.
    @Test void followUpCarriesTheNewestRequest() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-newest-1", Encounter.class, false);
        im.addIndexingQueueEntry("enc-newest-1", Encounter.class, false);
        im.addIndexingQueueEntry("enc-newest-1", Encounter.class, true);
        im.finishIndexingJob("enc-newest-1");

        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        verify(exec, times(2)).schedule(jobs.capture(), anyLong(), any(TimeUnit.class));
        IndexingManager.IndexingJob first = (IndexingManager.IndexingJob)jobs.getAllValues().get(0);
        IndexingManager.IndexingJob followUp =
            (IndexingManager.IndexingJob)jobs.getAllValues().get(1);
        assertFalse(first.unindex, "the first job is the original index request");
        assertTrue(followUp.unindex,
            "follow-up must carry the newest request (unindex), not the first remembered one");
        assertEquals("enc-newest-1", followUp.objectID);
        assertEquals(1, followUp.attempt, "a follow-up is a fresh pass, not a retry");
    }
}
