package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jdo.JDOObjectNotFoundException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * IndexingManagerRequeueTest marks "the job finished" by hand. This test proves the job itself
 * does so on every outcome: it runs a real IndexingJob on the test thread with its datastore and
 * index access replaced by a fake IndexingWork, then asserts on what the scheduler and the public
 * queue saw. No Shepherd, no datastore, no OpenSearch.
 */
class IndexingJobWiringTest {
    /** Scripted stand-in for "open a Shepherd, load by id, index, close". */
    private static final class FakeWork implements IndexingManager.IndexingWork {
        Base toLoad;                         // null => throw JDOObjectNotFoundException from load()
        final RuntimeException indexFailure; // non-null => index() throws it
        final List<String> events = new ArrayList<String>();

        FakeWork(Base toLoad, RuntimeException indexFailure) {
            this.toLoad = toLoad;
            this.indexFailure = indexFailure;
        }

        public Base load(Class myClass, String objectID) {
            events.add("load");
            if (toLoad == null) throw new JDOObjectNotFoundException("not visible: " + objectID);
            return toLoad;
        }

        public void index(Base base, boolean unindex) {
            events.add(unindex ? "unindex" : "index");
            if (indexFailure != null) throw indexFailure;
        }

        public void close() {
            events.add("close");
        }
    }

    private static IndexingManager managerUsing(ScheduledExecutorService exec, FakeWork work) {
        return new IndexingManager(exec) {
            @Override IndexingManager.IndexingWork newIndexingWork(String objectID) {
                return work;
            }
        };
    }

    private static List<Runnable> scheduled(ScheduledExecutorService exec, int expected) {
        ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);

        verify(exec, times(expected)).schedule(cap.capture(), anyLong(), any(TimeUnit.class));
        return cap.getAllValues();
    }

    private static Encounter encounter(String id) {
        Encounter enc = new Encounter();

        enc.setCatalogNumber(id);
        return enc;
    }

    @Test void successfulJob_indexesThenReleasesTheId_andCloses() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        FakeWork work = new FakeWork(encounter("enc-ok"), null);
        IndexingManager im = managerUsing(exec, work);

        im.addIndexingQueueEntry("enc-ok", Encounter.class, false);
        scheduled(exec, 1).get(0).run();

        assertEquals(List.of("load", "index", "close"), work.events);
        assertFalse(im.getIndexingQueue().contains("enc-ok"), "terminal success releases the id");
        scheduled(exec, 1); // nothing further
    }

    @Test void unindexJob_callsUnindex() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        FakeWork work = new FakeWork(encounter("enc-un"), null);
        IndexingManager im = managerUsing(exec, work);

        im.addIndexingQueueEntry("enc-un", Encounter.class, true);
        scheduled(exec, 1).get(0).run();
        assertEquals(List.of("load", "unindex", "close"), work.events);
    }

    // The whole point of the follow-up: a request that arrives while the job is running must run
    // once more after it, because the running job may have loaded pre-commit state.
    @Test void successfulJob_withARequestRecordedWhileRunning_schedulesExactlyOneFollowUp() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        FakeWork work = new FakeWork(encounter("enc-again"), null);
        IndexingManager im = managerUsing(exec, work);

        im.addIndexingQueueEntry("enc-again", Encounter.class, false);
        Runnable job = scheduled(exec, 1).get(0);

        im.addIndexingQueueEntry("enc-again", Encounter.class, false); // arrives "while running"
        job.run();

        List<Runnable> all = scheduled(exec, 2);
        IndexingManager.IndexingJob followUp = (IndexingManager.IndexingJob)all.get(1);
        assertEquals("enc-again", followUp.objectID);
        assertEquals(1, followUp.attempt, "a follow-up is a fresh pass");
        assertTrue(im.getIndexingQueue().contains("enc-again"), "still queued until the follow-up ends");

        followUp.run();
        scheduled(exec, 2);
        assertFalse(im.getIndexingQueue().contains("enc-again"));
    }

    // A genuine indexing failure is terminal: the id is released (the reconciler is the backstop)
    // and a remembered follow-up still runs -- it may well succeed.
    @Test void indexingFailure_isTerminal_releasesOrRunsFollowUp_andCloses() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        FakeWork work = new FakeWork(encounter("enc-fail"), new RuntimeException("opensearch down"));
        IndexingManager im = managerUsing(exec, work);

        im.addIndexingQueueEntry("enc-fail", Encounter.class, false);
        scheduled(exec, 1).get(0).run();
        assertEquals(List.of("load", "index", "close"), work.events);
        assertFalse(im.getIndexingQueue().contains("enc-fail"), "failure is terminal");

        // and with a follow-up recorded:
        im.addIndexingQueueEntry("enc-fail", Encounter.class, false);
        im.addIndexingQueueEntry("enc-fail", Encounter.class, false);
        scheduled(exec, 2).get(1).run();
        scheduled(exec, 3); // the follow-up
        assertTrue(im.getIndexingQueue().contains("enc-fail"));
    }

    // "Row not visible" is NOT terminal below the retry limit: schedule attempt+1, keep the id
    // queued, and do NOT consume a remembered follow-up yet.
    @Test void rowNotVisible_schedulesARetry_keepsTheIdAndTheFollowUp_andCloses() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        FakeWork work = new FakeWork(null, null); // load() throws JDOObjectNotFoundException
        IndexingManager im = managerUsing(exec, work);

        im.addIndexingQueueEntry("enc-late", Encounter.class, false);
        im.addIndexingQueueEntry("enc-late", Encounter.class, false); // follow-up recorded
        Runnable first = scheduled(exec, 1).get(0);

        first.run();
        assertEquals(List.of("load", "close"), work.events, "close runs even on the not-found path");

        List<Runnable> all = scheduled(exec, 2);
        IndexingManager.IndexingJob retry = (IndexingManager.IndexingJob)all.get(1);
        assertEquals(2, retry.attempt, "a retry, not a follow-up");
        assertTrue(im.getIndexingQueue().contains("enc-late"));

        // the retry finds the row (the writer committed meanwhile): success, and only NOW does
        // the remembered follow-up run
        work.toLoad = encounter("enc-late");
        retry.run();
        assertEquals(List.of("load", "close", "load", "index", "close"), work.events);
        IndexingManager.IndexingJob followUp = (IndexingManager.IndexingJob)scheduled(exec, 3).get(2);
        assertEquals(1, followUp.attempt, "a follow-up is a fresh pass, not attempt 3");
        assertTrue(im.getIndexingQueue().contains("enc-late"));

        followUp.run();
        scheduled(exec, 3);
        assertFalse(im.getIndexingQueue().contains("enc-late"));
    }

    // A missing row on the UNINDEX path is terminal immediately (nothing to unindex).
    @Test void rowNotVisibleForUnindex_isTerminal() {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        FakeWork work = new FakeWork(null, null);
        IndexingManager im = managerUsing(exec, work);

        im.addIndexingQueueEntry("enc-gone", Encounter.class, true);
        scheduled(exec, 1).get(0).run();
        assertFalse(im.getIndexingQueue().contains("enc-gone"));
        scheduled(exec, 1);
    }
}
