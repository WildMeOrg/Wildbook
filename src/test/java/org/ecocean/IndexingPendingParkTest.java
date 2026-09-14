package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import javax.jdo.datastore.DataStoreCache;
import javax.transaction.Status;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

/**
 * The per-PersistenceManager park for reindex requests raised inside a transaction.
 *
 * WildbookLifecycleListener.postStore() fires on JDO flush, not on commit. Enqueuing there let the
 * background indexer -- which opens its own connection -- read and index pre-commit state. So a
 * request is now PARKED against the writing PM and released only by the transaction's completion
 * callback (IndexingTransactionSynchronization): drained if the transaction committed, dropped if
 * it rolled back.
 *
 * Two layers are tested. PendingBucket is the state machine; it is tested directly because a late
 * parker that read the bucket reference just before completion is exactly "a thread holding the
 * bucket object", and that race is not reachable through the PM-level statics in a unit test. The
 * PM-level statics are then tested for their wiring: user-object map, transaction state, successor
 * installation, level-2 eviction by DataNucleus identity, per-entry failure isolation, and the
 * immediate fallback. No datastore anywhere.
 */
class IndexingPendingParkTest {
    /** A PersistenceManager stub whose user-object map actually behaves like one. */
    private static PersistenceManager pmWithUserObjects(PersistenceManagerFactory pmf,
        Transaction tx) {
        PersistenceManager pm = mock(PersistenceManager.class);
        final Map<Object, Object> userObjects = new HashMap<Object, Object>();

        when(pm.currentTransaction()).thenReturn(tx);
        when(pm.isClosed()).thenReturn(false);
        when(pm.getPersistenceManagerFactory()).thenReturn(pmf);
        when(pm.getUserObject(any())).thenAnswer((InvocationOnMock inv) ->
            userObjects.get(inv.getArgument(0)));
        when(pm.putUserObject(any(), any())).thenAnswer((InvocationOnMock inv) ->
            userObjects.put(inv.getArgument(0), inv.getArgument(1)));
        when(pm.removeUserObject(any())).thenAnswer((InvocationOnMock inv) ->
            userObjects.remove(inv.getArgument(0)));
        return pm;
    }

    private static Transaction activeTx() {
        Transaction tx = mock(Transaction.class);

        when(tx.isActive()).thenReturn(true);
        return tx;
    }

    // a PM that Shepherd has already prepared: park installed, transaction open
    private static PersistenceManager preparedPm(PersistenceManagerFactory pmf) {
        PersistenceManager pm = pmWithUserObjects(pmf, activeTx());

        IndexingManager.installPendingBucket(pm);
        assertNotNull(pm.getUserObject(IndexingManager.PENDING_USER_OBJECT_KEY),
            "precondition: park installed on the PM");
        return pm;
    }

    private static IndexingManager.PendingBucket bucketOf(PersistenceManager pm) {
        return (IndexingManager.PendingBucket)pm.getUserObject(
            IndexingManager.PENDING_USER_OBJECT_KEY);
    }

    private static Encounter encounterWithId(String id) {
        Encounter enc = new Encounter();

        enc.setCatalogNumber(id);
        enc.setSkipAutoIndexing(false);
        return enc;
    }

    private static IndexingManager.PendingEntry entry(String id, boolean unindex) {
        return new IndexingManager.PendingEntry(id, Encounter.class, unindex, null);
    }

    // ---- Outcome mapping -----------------------------------------------------------------------

    @Test void outcomeOf_mapsJtaStatusExplicitly() {
        assertEquals(IndexingManager.Outcome.COMMITTED,
            IndexingManager.outcomeOf(Status.STATUS_COMMITTED));
        assertEquals(IndexingManager.Outcome.ROLLED_BACK,
            IndexingManager.outcomeOf(Status.STATUS_ROLLEDBACK));
        // DataNucleus only ever reports the two above; anything else is evidence we must not
        // silently treat as a proven commit OR a proven rollback
        assertEquals(IndexingManager.Outcome.UNKNOWN, IndexingManager.outcomeOf(Status.STATUS_UNKNOWN));
        assertEquals(IndexingManager.Outcome.UNKNOWN, IndexingManager.outcomeOf(Status.STATUS_ACTIVE));
        assertEquals(IndexingManager.Outcome.UNKNOWN,
            IndexingManager.outcomeOf(Status.STATUS_NO_TRANSACTION));
    }

    // ---- PendingBucket: the state machine ------------------------------------------------------

    @Test void bucket_parksUntilCompleted_thenCommittedCompletionHandsBackEverything() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        assertEquals(IndexingManager.ParkResult.PARKED, b.park(entry("a", false)));
        assertEquals(IndexingManager.ParkResult.PARKED, b.park(entry("b", false)));
        assertFalse(b.isCompleted());
        assertEquals(2, b.size());

        List<IndexingManager.PendingEntry> drained = b.complete(IndexingManager.Outcome.COMMITTED);
        assertTrue(b.isCompleted());
        assertEquals(2, drained.size());
        assertEquals("a", drained.get(0).objectID);
        assertEquals("b", drained.get(1).objectID);
        assertEquals(0, b.size(), "completion takes the contents");
    }

    @Test void bucket_rolledBackCompletion_handsBackNothing() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        b.park(entry("a", false));
        List<IndexingManager.PendingEntry> drained = b.complete(IndexingManager.Outcome.ROLLED_BACK);
        assertTrue(b.isCompleted());
        assertTrue(drained.isEmpty(), "nothing committed, so nothing to index");
    }

    // An outcome we cannot interpret must not silently drop evidence, and must never run an
    // ambiguous unindex: index requests are harmless (the job re-reads the database), so they run.
    @Test void bucket_unknownCompletion_handsBackOnlyIndexRequests() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        b.park(entry("idx", false));
        b.park(entry("gone", true));
        List<IndexingManager.PendingEntry> drained = b.complete(IndexingManager.Outcome.UNKNOWN);
        assertEquals(1, drained.size());
        assertEquals("idx", drained.get(0).objectID);
        assertFalse(drained.get(0).unindex);
    }

    // postStore fires many times per transaction; repeated parks of one request must collapse.
    @Test void bucket_collapsesDuplicateParks_butKeepsDistinctRequests() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        b.park(entry("a", false));
        b.park(entry("a", false));
        b.park(entry("a", false));
        b.park(entry("a", true)); // an unindex for the same id is a different request
        assertEquals(2, b.complete(IndexingManager.Outcome.COMMITTED).size());
    }

    // A parker that grabbed the bucket just before the transaction completed must not add to a
    // bucket nobody will look at again. Committed: the change is durable, run now. Rolled back:
    // drop. Unknown: index runs now, unindex is dropped.
    @Test void bucket_lateParkAfterCommittedCompletion_saysEnqueueNow() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        b.complete(IndexingManager.Outcome.COMMITTED);
        assertEquals(IndexingManager.ParkResult.ENQUEUE_NOW, b.park(entry("late", false)));
        assertEquals(IndexingManager.ParkResult.ENQUEUE_NOW, b.park(entry("late", true)));
        assertEquals(0, b.size(), "a late request is never retained");
    }

    @Test void bucket_lateParkAfterRolledBackCompletion_saysDrop() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        b.complete(IndexingManager.Outcome.ROLLED_BACK);
        assertEquals(IndexingManager.ParkResult.DROP, b.park(entry("late", false)));
        assertEquals(IndexingManager.ParkResult.DROP, b.park(entry("late", true)));
    }

    @Test void bucket_lateParkAfterUnknownCompletion_runsIndexDropsUnindex() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        b.complete(IndexingManager.Outcome.UNKNOWN);
        assertEquals(IndexingManager.ParkResult.ENQUEUE_NOW, b.park(entry("late", false)));
        assertEquals(IndexingManager.ParkResult.DROP, b.park(entry("late", true)));
    }

    @Test void bucket_firstCompletionOutcomeSticks() {
        IndexingManager.PendingBucket b = new IndexingManager.PendingBucket(null);

        b.park(entry("a", false));
        assertEquals(1, b.complete(IndexingManager.Outcome.COMMITTED).size());
        assertTrue(b.complete(IndexingManager.Outcome.COMMITTED).isEmpty(), "idempotent");
        assertTrue(b.complete(IndexingManager.Outcome.ROLLED_BACK).isEmpty(),
            "a later rollback signal cannot un-commit");
        assertEquals(IndexingManager.ParkResult.ENQUEUE_NOW, b.park(entry("late", false)),
            "the first completion outcome (committed) is the one that sticks");
    }

    // ---- PM-level wiring ----------------------------------------------------------------------

    // Parking must NOT enqueue -- the transaction has not committed yet.
    @Test void addPendingEntry_parksWithoutEnqueuing() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-park-1"), false);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        verify(im, never()).addIndexingQueueEntry(any(Base.class), anyBoolean());
        assertEquals(1, bucketOf(pm).size(), "the request is parked, not lost");
    }

    // The level-2 cache is keyed by DataNucleus's INTERNAL identity, not the javax.jdo.identity.*
    // object JDOHelper.getObjectId() returns -- evicting with the latter silently misses. The park
    // must capture Persistable.dnGetObjectId() and evict with that, BEFORE enqueuing.
    @Test void completeCommitted_evictsDataNucleusIdentityThenEnqueuesByIdentity() {
        DataStoreCache l2 = mock(DataStoreCache.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);

        when(pmf.getDataStoreCache()).thenReturn(l2);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);
        Object dnId = new Object();
        Encounter enc = spy(encounterWithId("enc-drain-1")); // transient: no real identity, stub it
        doReturn(dnId).when(enc).dnGetObjectId();

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        InOrder inOrder = inOrder(l2, im);

        inOrder.verify(l2).evict(dnId);
        inOrder.verify(im).addIndexingQueueEntry(eq("enc-drain-1"), eq(Encounter.class), eq(false));
        inOrder.verifyNoMoreInteractions();
    }

    @Test void completeRolledBack_enqueuesNothing() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-rb-1"), false);
            assertEquals(1, bucketOf(pm).size(), "precondition: something was actually parked");
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.ROLLED_BACK);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
    }

    @Test void completeUnknown_enqueuesIndexRequestsOnly() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-unk-idx"), false);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-unk-gone"), true);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.UNKNOWN);
        }
        verify(im).addIndexingQueueEntry(eq("enc-unk-idx"), eq(Encounter.class), eq(false));
        verify(im, never()).addIndexingQueueEntry(eq("enc-unk-gone"), any(), anyBoolean());
    }

    // A level-2 cache that throws must not stop the object from being indexed.
    @Test void completeCommitted_evictFailure_stillEnqueues() {
        DataStoreCache l2 = mock(DataStoreCache.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);

        when(pmf.getDataStoreCache()).thenReturn(l2);
        doThrow(new RuntimeException("boom")).when(l2).evict(any());
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);
        Encounter enc = spy(encounterWithId("enc-evictfail-1"));
        doReturn(new Object()).when(enc).dnGetObjectId();

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        verify(im).addIndexingQueueEntry(eq("enc-evictfail-1"), eq(Encounter.class), eq(false));
    }

    // One failing handoff must not take the rest of the snapshot down with it, and must not stop
    // the successor park from being installed.
    @Test void completeCommitted_oneEnqueueThrows_othersStillEnqueue_andSuccessorIsInstalled() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager.PendingBucket first = bucketOf(pm);
        IndexingManager im = mock(IndexingManager.class);

        doThrow(new RuntimeException("queue exploded")).when(im).addIndexingQueueEntry(
            eq("enc-boom"), any(), anyBoolean());
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-boom"), false);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-fine"), false);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        verify(im).addIndexingQueueEntry(eq("enc-fine"), eq(Encounter.class), eq(false));
        assertNotSame(first, bucketOf(pm), "successor installed despite the failure");
        assertFalse(bucketOf(pm).isCompleted());
    }

    // No IndexingManager at all (factory initialization failed) must not throw out of completion.
    @Test void completeCommitted_nullIndexingManager_doesNotThrow() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(null);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-nomgr"), false);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        assertFalse(bucketOf(pm).isCompleted(), "a fresh park is in place regardless");
    }

    // The successor park must be installed BEFORE any external handoff work, so a failure there
    // (or a delegate that runs in between) can never leave the PM without a live park.
    @Test void capture_installsSuccessorBeforeReturning_andDrainIsIndependentOfThePm() {
        DataStoreCache l2 = mock(DataStoreCache.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);

        when(pmf.getDataStoreCache()).thenReturn(l2);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager.PendingBucket first = bucketOf(pm);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-cap-1"), false);

            List<IndexingManager.PendingEntry> snapshot =
                IndexingManager.capturePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
            assertEquals(1, snapshot.size());
            assertTrue(first.isCompleted());
            assertNotSame(first, bucketOf(pm), "successor already installed");
            verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());

            // simulate a delegate closing the PM in between: the snapshot must still drain
            when(pm.isClosed()).thenReturn(true);
            IndexingManager.drainCaptured(snapshot, pmf);
        }
        verify(im).addIndexingQueueEntry(eq("enc-cap-1"), eq(Encounter.class), eq(false));
    }

    @Test void drainCaptured_evictsEachDataNucleusIdentityBeforeItsEnqueue() {
        DataStoreCache l2 = mock(DataStoreCache.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);

        when(pmf.getDataStoreCache()).thenReturn(l2);
        IndexingManager im = mock(IndexingManager.class);
        Object idA = new Object();
        Object idB = new Object();

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.drainCaptured(Arrays.asList(
                new IndexingManager.PendingEntry("a", Encounter.class, false, idA),
                new IndexingManager.PendingEntry("b", MarkedIndividual.class, true, idB),
                new IndexingManager.PendingEntry("c", Encounter.class, false, null)), pmf);
        }
        InOrder inOrder = inOrder(l2, im);

        inOrder.verify(l2).evict(idA);
        inOrder.verify(im).addIndexingQueueEntry("a", Encounter.class, false);
        inOrder.verify(l2).evict(idB);
        inOrder.verify(im).addIndexingQueueEntry("b", MarkedIndividual.class, true);
        inOrder.verify(im).addIndexingQueueEntry("c", Encounter.class, false); // no id: no evict
        verify(l2, times(2)).evict(any());
    }

    // Completion is the ONLY way a park becomes terminal, and it must leave a fresh, live park
    // behind so the PM's next transaction has somewhere to park -- however that transaction is
    // begun (Shepherd or a raw pm.currentTransaction().begin()).
    @Test void completion_installsAFreshLivePark_forTheNextTransaction() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager.PendingBucket first = bucketOf(pm);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-t1"), false);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
            verify(im).addIndexingQueueEntry(eq("enc-t1"), eq(Encounter.class), eq(false));

            IndexingManager.PendingBucket second = bucketOf(pm);
            assertNotSame(first, second, "a fresh park replaces the completed one");
            assertTrue(first.isCompleted());
            assertFalse(second.isCompleted());

            // transaction 2 on the same PM: parked, not enqueued
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-t2"), false);
            verify(im, never()).addIndexingQueueEntry(eq("enc-t2"), any(), anyBoolean());
            assertEquals(1, second.size());

            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        verify(im).addIndexingQueueEntry(eq("enc-t2"), eq(Encounter.class), eq(false));
    }

    @Test void completion_afterRollback_alsoInstallsAFreshLivePark() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-rb-t1"), false);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.ROLLED_BACK);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-rb-t2"), false);
            assertEquals(1, bucketOf(pm).size(),
                "a rolled-back predecessor must not swallow the next transaction's request");
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        verify(im, never()).addIndexingQueueEntry(eq("enc-rb-t1"), any(), anyBoolean());
        verify(im).addIndexingQueueEntry(eq("enc-rb-t2"), eq(Encounter.class), eq(false));
    }

    // Completion on a PM that has since been closed must not try to install a successor (the
    // user-object map is gone with the PM) and must not throw.
    @Test void completion_onClosedPm_doesNotInstallOrThrow() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-closed-t1"), false);
            when(pm.isClosed()).thenReturn(true);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        verify(pm, times(1)).putUserObject(any(), any()); // only the original install
    }

    // beginDBTransaction() runs again for an already-active transaction; installing a fresh park
    // there would drop everything the open transaction had already parked.
    @Test void installPendingBucket_doesNotClobberALivePark() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager.PendingBucket live = bucketOf(pm);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-live-1"), false);
            IndexingManager.installPendingBucket(pm); // a redundant begin
            assertSame(live, bucketOf(pm), "live park must survive a redundant begin");
            assertEquals(1, live.size());
        }
    }

    @Test void installPendingBucket_replacesACompletedPark() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager.PendingBucket stale = bucketOf(pm);

        stale.complete(IndexingManager.Outcome.COMMITTED); // completed with no successor installed
        IndexingManager.installPendingBucket(pm);
        assertNotSame(stale, bucketOf(pm));
        assertFalse(bucketOf(pm).isCompleted());
    }

    // With no usable PersistenceManager there is nothing to defer to: fall back to the immediate
    // behavior rather than silently losing the request.
    @Test void addPendingEntry_nullPm_enqueuesImmediately() {
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(null, encounterWithId("enc-nopm-1"), false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-nopm-1"), eq(Encounter.class), eq(false));
    }

    @Test void addPendingEntry_closedPm_enqueuesImmediately() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);

        when(pm.isClosed()).thenReturn(true);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-closed-1"), false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-closed-1"), eq(Encounter.class), eq(false));
    }

    // A caller that has already committed (transaction no longer active) must NOT be deferred:
    // there is no completion coming to release it. This is the IndividualRemoveEncounter shape.
    @Test void addPendingEntry_inactiveTransaction_enqueuesImmediately() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        Transaction tx = mock(Transaction.class);

        when(tx.isActive()).thenReturn(false);
        PersistenceManager pm = pmWithUserObjects(pmf, tx);
        IndexingManager.installPendingBucket(pm);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-postcommit-1"), false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-postcommit-1"), eq(Encounter.class), eq(false));
        assertEquals(0, bucketOf(pm).size(), "nothing should have been parked");
    }

    // A PM nobody prepared (no park installed) cannot defer either.
    @Test void addPendingEntry_pmWithoutPark_enqueuesImmediately() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = pmWithUserObjects(pmf, activeTx());
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-nopark-1"), false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-nopark-1"), eq(Encounter.class), eq(false));
    }

    @Test void addPendingEntry_nullOrIdlessBase_isANoOp() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        IndexingManager im = mock(IndexingManager.class);

        Base idless = mock(Base.class); // getId() returns null

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, null, false);
            IndexingManager.addPendingEntry(pm, idless, false);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        assertEquals(0, bucketOf(pm).size());
    }

    @Test void completePendingEntries_onPmWithoutPark_isANoOp() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = pmWithUserObjects(pmf, activeTx());
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
            IndexingManager.completePendingEntries(null, IndexingManager.Outcome.COMMITTED);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        verify(pm, times(0)).putUserObject(any(), any());
    }
}
