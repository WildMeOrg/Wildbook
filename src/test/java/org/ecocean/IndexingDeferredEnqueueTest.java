package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import javax.jdo.datastore.DataStoreCache;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

/**
 * Unit tests for the post-commit indexing handoff (the pre-commit enqueue race) and for the
 * generation-aware dedupe that keeps a follow-up pass alive when a job is already in flight.
 *
 * No Shepherd, no datastore, no OpenSearch: the IndexingManager instances here are built with the
 * test-seam constructor and a mock scheduler, so no indexing job ever actually runs.
 */
class IndexingDeferredEnqueueTest {
    // must match IndexingManager.PENDING_USER_OBJECT_KEY -- asserted on directly so a park that
    // silently no-ops cannot make a test pass for the wrong reason
    private static final String PENDING_KEY = "org.ecocean.IndexingManager.pending";

    // OpenSearch.skipAutoIndexing() reads the host filesystem for /tmp/skipAutoIndexing, and
    // another test in the suite creates that file and only removes it at JVM exit. Stub it, or
    // these fail depending on ordering. (Same reason as ChildReindexTriggerTest.)
    private static MockedStatic<OpenSearch> mockNoGlobalSkip() {
        MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class);

        os.when(OpenSearch::skipAutoIndexing).thenReturn(false);
        return os;
    }

    /** A PersistenceManager stub whose user-object map actually behaves like one. */
    private static PersistenceManager pmWithUserObjects(PersistenceManagerFactory pmf,
        boolean txActive) {
        PersistenceManager pm = mock(PersistenceManager.class);
        Transaction tx = mock(Transaction.class);
        final Map<Object, Object> userObjects = new HashMap<Object, Object>();

        when(tx.isActive()).thenReturn(txActive);
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

    // a PM that Shepherd has already prepared: park installed, transaction open
    private static PersistenceManager preparedPm(PersistenceManagerFactory pmf) {
        PersistenceManager pm = pmWithUserObjects(pmf, true);

        IndexingManager.installPendingBucket(pm);
        assertNotNull(pm.getUserObject(PENDING_KEY), "precondition: park installed on the PM");
        return pm;
    }

    private static Encounter encounterWithId(String id) {
        Encounter enc = new Encounter();

        enc.setCatalogNumber(id);
        enc.setSkipAutoIndexing(false);
        return enc;
    }

    // ---- park / drain / discard -------------------------------------------------------------

    // Parking must NOT enqueue -- the transaction has not committed yet. The follow-up drain
    // proves the request was really parked rather than silently dropped.
    @Test void addPendingEntry_parksWithoutEnqueuing_thenDrainReleasesIt() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-park-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
            verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
            verify(im, never()).addIndexingQueueEntry(any(Base.class), anyBoolean());

            IndexingManager.drainPendingEntries(pm);
        }
        verify(im).addIndexingQueueEntry(eq("enc-park-1"), eq(Encounter.class), eq(false));
    }

    // Draining evicts from the level-2 cache BEFORE scheduling -- otherwise the job it just
    // scheduled could read a pre-commit copy out of the cache instead of the committed row.
    @Test void drain_evictsLevel2ThenEnqueues() {
        DataStoreCache l2 = mock(DataStoreCache.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);

        when(pmf.getDataStoreCache()).thenReturn(l2);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-drain-1");
        IndexingManager im = mock(IndexingManager.class);
        // a transient Encounter has no JDO identity, so stub one -- otherwise this test would
        // silently assert nothing about eviction
        Object oid = new Object();

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<JDOHelper> jdo = mockStatic(JDOHelper.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            jdo.when(() -> JDOHelper.getObjectId(any())).thenReturn(oid);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.drainPendingEntries(pm);
        }
        InOrder inOrder = inOrder(l2, im);

        inOrder.verify(l2).evict(oid);
        inOrder.verify(im).addIndexingQueueEntry(eq("enc-drain-1"), eq(Encounter.class), eq(false));
        inOrder.verifyNoMoreInteractions();
    }

    // A level-2 cache that throws must not stop the object from being indexed.
    @Test void drain_evictFailure_stillEnqueues() {
        DataStoreCache l2 = mock(DataStoreCache.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);

        when(pmf.getDataStoreCache()).thenReturn(l2);
        doThrow(new RuntimeException("boom")).when(l2).evict(any());
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-evictfail-1");
        IndexingManager im = mock(IndexingManager.class);
        Object oid = new Object();

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<JDOHelper> jdo = mockStatic(JDOHelper.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            jdo.when(() -> JDOHelper.getObjectId(any())).thenReturn(oid);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.drainPendingEntries(pm);
        }
        verify(im).addIndexingQueueEntry(eq("enc-evictfail-1"), eq(Encounter.class), eq(false));
    }

    // postStore fires many times per transaction; repeated parks of one object must collapse.
    @Test void drain_collapsesDuplicateParks() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-dupe-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.drainPendingEntries(pm);
        }
        verify(im, times(1)).addIndexingQueueEntry(eq("enc-dupe-1"), eq(Encounter.class), eq(false));
    }

    // commitDBTransaction() drains, then closeDBTransaction() discards the same PM: the second
    // pass must be a no-op, not a re-enqueue.
    @Test void drainThenDiscard_isIdempotent() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-idem-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.drainPendingEntries(pm);
            IndexingManager.discardPendingEntries(pm);
            IndexingManager.drainPendingEntries(pm);
        }
        verify(im, times(1)).addIndexingQueueEntry(eq("enc-idem-1"), eq(Encounter.class), eq(false));
    }

    // A rolled-back transaction must index nothing. The park is asserted present first, so this
    // cannot pass merely because nothing was ever parked.
    @Test void discard_dropsEverything() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-discard-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
            assertFalse(pendingEntryCount(pm) == 0, "precondition: something was actually parked");
            IndexingManager.discardPendingEntries(pm);
            IndexingManager.drainPendingEntries(pm);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
    }

    // With no usable PersistenceManager there is nothing to defer to, so fall back to the old
    // immediate behavior rather than silently losing the request.
    @Test void addPendingEntry_nullPm_enqueuesImmediately() {
        Encounter enc = encounterWithId("enc-nopm-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(null, enc, false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-nopm-1"), eq(Encounter.class), eq(false));
    }

    // A caller that has already committed (transaction no longer active) must NOT be deferred --
    // deferring it would mean discarding it at close. This is the IndividualRemoveEncounter shape.
    @Test void addPendingEntry_inactiveTransaction_enqueuesImmediately() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = pmWithUserObjects(pmf, false); // committed already

        IndexingManager.installPendingBucket(pm);
        Encounter enc = encounterWithId("enc-postcommit-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-postcommit-1"), eq(Encounter.class), eq(false));
        assertEquals(0, pendingEntryCount(pm), "nothing should have been parked");
    }

    // ---- park racing the end of the transaction ---------------------------------------------

    // addPendingEntry() checks "transaction active" and locks the bucket as two separate steps, so
    // a commit can drain in between. The late request must be queued directly, not added to a
    // bucket nobody will read again.
    @Test void parkAfterDrain_enqueuesImmediately() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-late-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.drainPendingEntries(pm); // commit happened first (park was empty)
            IndexingManager.addPendingEntry(pm, enc, false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-late-1"), eq(Encounter.class), eq(false));
        assertEquals(0, pendingEntryCount(pm), "must not accumulate in a drained park");
    }

    // Same race against a rollback: the transaction did not commit, so the late request is dropped.
    @Test void parkAfterDiscard_isDropped() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-late-2");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.discardPendingEntries(pm);
            IndexingManager.addPendingEntry(pm, enc, false);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        assertEquals(0, pendingEntryCount(pm), "must not accumulate in a discarded park");
    }

    // closeDBTransaction() discards the SAME bucket a successful commitDBTransaction() just
    // drained. That close must not flip the park into "drop everything" for a later parker.
    @Test void discardAfterDrain_doesNotMaskTheDrainedState() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-late-3");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.drainPendingEntries(pm);   // commit
            IndexingManager.discardPendingEntries(pm); // close
            IndexingManager.addPendingEntry(pm, enc, false);
        }
        verify(im).addIndexingQueueEntry(eq("enc-late-3"), eq(Encounter.class), eq(false));
    }

    // beginDBTransaction() runs again for an already-active transaction; installing a fresh park
    // there would drop everything the open transaction had already parked.
    @Test void installPendingBucket_doesNotClobberALivePark() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-live-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, enc, false);
            IndexingManager.installPendingBucket(pm); // a redundant begin
            assertEquals(1, pendingEntryCount(pm), "live park must survive a redundant begin");
            IndexingManager.drainPendingEntries(pm);
        }
        verify(im).addIndexingQueueEntry(eq("enc-live-1"), eq(Encounter.class), eq(false));
    }

    // ...but after the transaction ends, the next begin must get a usable (non-terminal) park.
    @Test void installPendingBucket_replacesATerminalPark() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-cycle-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.drainPendingEntries(pm);  // commit ends transaction 1
            IndexingManager.installPendingBucket(pm); // updateDBTransaction() begins transaction 2
            IndexingManager.addPendingEntry(pm, enc, false);
            // parked, not immediately enqueued -- this is a fresh transaction
            verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
            assertEquals(1, pendingEntryCount(pm), "second transaction gets a live park");
            IndexingManager.drainPendingEntries(pm);
        }
        verify(im).addIndexingQueueEntry(eq("enc-cycle-1"), eq(Encounter.class), eq(false));
    }

    // The reason Shepherd installs the park BEFORE activating the next transaction: a store
    // callback that lands while the transaction is active but the park still belongs to the
    // PREVIOUS (terminal) transaction gets the wrong answer -- immediate enqueue off a drained
    // park (which is the pre-commit stale read again), or a silent drop off a discarded one.
    // These two pin the IndexingManager half of that contract; the Shepherd half (install strictly
    // before Transaction.begin()) is asserted in ShepherdCommitHandoffTest.
    @Test void parkAgainstAStaleDrainedPark_wouldEnqueueEarly_soInstallMustPrecedeBegin() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-order-1");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.drainPendingEntries(pm);  // transaction 1 committed
            // transaction 2 begins. Shepherd installs FIRST, so the park is live again:
            IndexingManager.installPendingBucket(pm);
            IndexingManager.addPendingEntry(pm, enc, false);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        assertEquals(1, pendingEntryCount(pm),
            "with install before begin the request parks against the NEW transaction");
    }

    @Test void parkAgainstAStaleDiscardedPark_wouldBeDropped_soInstallMustPrecedeBegin() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf);
        Encounter enc = encounterWithId("enc-order-2");
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.discardPendingEntries(pm); // transaction 1 rolled back
            IndexingManager.installPendingBucket(pm);  // transaction 2 prepared before begin
            IndexingManager.addPendingEntry(pm, enc, false);
            assertEquals(1, pendingEntryCount(pm),
                "a rolled-back predecessor must not swallow the new transaction's request");
            IndexingManager.drainPendingEntries(pm);
        }
        verify(im).addIndexingQueueEntry(eq("enc-order-2"), eq(Encounter.class), eq(false));
    }

    // ---- generation-aware dedupe (deterministic: mock scheduler, nothing runs) ---------------

    private static ScheduledExecutorService recordingExecutor() {
        return mock(ScheduledExecutorService.class);
    }

    private static IndexingManager managerWith(ScheduledExecutorService exec) throws Exception {
        java.lang.reflect.Constructor<IndexingManager> c =
            IndexingManager.class.getDeclaredConstructor(ScheduledExecutorService.class);

        c.setAccessible(true);
        return c.newInstance(exec);
    }

    private static List<Runnable> scheduled(ScheduledExecutorService exec, int expected) {
        ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);

        verify(exec, times(expected)).schedule(cap.capture(), anyLong(), any(TimeUnit.class));
        return cap.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> requeueOf(IndexingManager im) throws Exception {
        Field f = IndexingManager.class.getDeclaredField("requeue");

        f.setAccessible(true);
        return (Map<String, ?>)f.get(im);
    }

    private static void finishJob(IndexingManager im, String id) throws Exception {
        Method m = IndexingManager.class.getDeclaredMethod("finishIndexingJob", String.class);

        m.setAccessible(true);
        m.invoke(im, id);
    }

    @SuppressWarnings("unchecked")
    private static int pendingEntryCount(PersistenceManager pm) {
        try {
            Object bucket = pm.getUserObject(PENDING_KEY);
            if (bucket == null) return 0;
            Field f = bucket.getClass().getDeclaredField("entries");
            f.setAccessible(true);
            return ((java.util.Collection<?>)f.get(bucket)).size();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // The old code did `if (queue.contains(id)) return;` -- silently dropping the post-commit
    // request whenever a pre-commit job was still in flight. It must be remembered instead, and
    // released as one more pass when the in-flight job reaches a terminal outcome.
    @Test void secondEnqueueWhileInFlight_runsFollowUpPassOnTerminal() throws Exception {
        ScheduledExecutorService exec = recordingExecutor();
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-race-1", Encounter.class, false);
        assertTrue(im.getIndexingQueue().contains("enc-race-1"), "first enqueue takes the slot");
        scheduled(exec, 1);

        im.addIndexingQueueEntry("enc-race-1", Encounter.class, false);
        assertTrue(requeueOf(im).containsKey("enc-race-1"),
            "a second request while in flight must be remembered, not dropped");
        scheduled(exec, 1); // still only the first job scheduled

        finishJob(im, "enc-race-1");
        scheduled(exec, 2); // the follow-up pass
        assertTrue(im.getIndexingQueue().contains("enc-race-1"),
            "id stays queued across the follow-up so concurrent events keep deduping");
        assertTrue(requeueOf(im).isEmpty(), "follow-up consumed");

        finishJob(im, "enc-race-1");
        scheduled(exec, 2); // nothing further
        assertFalse(im.getIndexingQueue().contains("enc-race-1"), "id released when clean");
    }

    // The retry give-up path is terminal too: a post-commit request that arrived while the job
    // was burning retries is exactly the one that CAN succeed, so it must not be swallowed.
    @Test void giveUp_stillRunsFollowUpPass() throws Exception {
        ScheduledExecutorService exec = recordingExecutor();
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-giveup-1", Encounter.class, false);
        im.addIndexingQueueEntry("enc-giveup-1", Encounter.class, false);
        scheduled(exec, 1);

        Method m = IndexingManager.class.getDeclaredMethod("handleObjectNotFound", String.class,
            Class.class, boolean.class, int.class);

        m.setAccessible(true);
        m.invoke(im, "enc-giveup-1", Encounter.class, false, 6); // 6 == MAX_INDEXING_ATTEMPTS

        scheduled(exec, 2);
        assertTrue(requeueOf(im).isEmpty(), "follow-up consumed by the give-up path");
    }

    // A hard release drops the follow-up with it (used by reset / executor-shutdown paths).
    @Test void removeIndexingQueueEntry_clearsFollowUp() throws Exception {
        ScheduledExecutorService exec = recordingExecutor();
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-race-2", Encounter.class, false);
        im.addIndexingQueueEntry("enc-race-2", Encounter.class, false);
        assertTrue(requeueOf(im).containsKey("enc-race-2"), "precondition: follow-up recorded");

        im.removeIndexingQueueEntry("enc-race-2");
        assertFalse(im.getIndexingQueue().contains("enc-race-2"), "id released from queue");
        assertFalse(requeueOf(im).containsKey("enc-race-2"),
            "a hard release must not leave a stale follow-up behind");
    }

    @Test void resetQueue_clearsFollowUps() throws Exception {
        ScheduledExecutorService exec = recordingExecutor();
        IndexingManager im = managerWith(exec);

        im.addIndexingQueueEntry("enc-race-3", Encounter.class, false);
        im.addIndexingQueueEntry("enc-race-3", Encounter.class, false);
        im.resetIndexingQueuehWithInitialCapacity(10);
        assertTrue(requeueOf(im).isEmpty(), "reset must clear follow-ups too");
    }

    // ---- Encounter.setIndividual version bump ------------------------------------------------

    private static void setIndividualField(Encounter enc, MarkedIndividual indiv) throws Exception {
        Field f = Encounter.class.getDeclaredField("individual");

        f.setAccessible(true);
        f.set(enc, indiv);
    }

    // Without a version bump the reconciler can never repair a document that went stale on this
    // path, because it only reindexes when the DB version is strictly greater.
    @Test void setIndividual_bumpsModified() throws Exception {
        Encounter enc = spy(new Encounter());

        enc.setSkipAutoIndexing(false);
        enc.setDWCDateLastModified("2000-01-01 00:00:00");
        long before = enc.getVersion();
        MarkedIndividual newInd = mock(MarkedIndividual.class);

        try (MockedStatic<OpenSearch> os = mockNoGlobalSkip()) {
            enc.setIndividual(newInd);
        }
        assertNotEquals(before, enc.getVersion(),
            "assigning an individual must advance the version the reconciler compares");
    }

    // A no-op set is not a modification and must not churn the version (or the index).
    @Test void setIndividual_noOp_doesNotBumpModified() throws Exception {
        Encounter enc = spy(new Encounter());

        enc.setSkipAutoIndexing(false);
        MarkedIndividual ind = mock(MarkedIndividual.class);

        setIndividualField(enc, ind);
        enc.setDWCDateLastModified("2000-01-01 00:00:00");
        long before = enc.getVersion();
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            enc.setIndividual(ind);
        }
        assertEquals(before, enc.getVersion(), "a no-op set must not advance the version");
        verify(im, never()).addIndexingQueueEntry(any(Base.class), anyBoolean());
    }
}
