package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import javax.transaction.Status;
import javax.transaction.Synchronization;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

/**
 * The bridge from the JDO transaction's completion callback to the park in IndexingManager.
 *
 * DataNucleus calls Synchronization.afterCompletion(status) from inside commit() and rollback(),
 * after its own post-commit work. That callback -- and only that callback -- terminalizes the
 * park. Order inside it is fixed and load-bearing: (1) capture + terminalize the park and install
 * its successor, (2) call the delegate Synchronization we wrapped (if any), (3) drain the captured
 * snapshot. A delegate can legitimately close the PM; doing (1) first means that cannot lose the
 * snapshot, and doing (3) last preserves delegate-before-enqueue for anyone who relies on it.
 */
class IndexingTransactionSynchronizationTest {
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
        return pm;
    }

    /** A Transaction mock that actually remembers the Synchronization set on it. */
    private static Transaction transactionHoldingSynchronization(boolean active) {
        Transaction tx = mock(Transaction.class);
        final Synchronization[] held = new Synchronization[1];

        when(tx.isActive()).thenReturn(active);
        doAnswer((InvocationOnMock inv) -> {
            held[0] = inv.getArgument(0);
            return null;
        }).when(tx).setSynchronization(any());
        when(tx.getSynchronization()).thenAnswer((InvocationOnMock inv) -> held[0]);
        return tx;
    }

    private static PersistenceManager preparedPm(PersistenceManagerFactory pmf, Transaction tx) {
        PersistenceManager pm = pmWithUserObjects(pmf, tx);

        IndexingManager.installPendingBucket(pm);
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

    // ---- outcome routing ----------------------------------------------------------------------

    @Test void afterCompletion_committed_drainsTheParkToTheQueue() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        IndexingManager im = mock(IndexingManager.class);
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, null);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-c"), false);
            verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
        verify(im).addIndexingQueueEntry(eq("enc-c"), eq(Encounter.class), eq(false));
    }

    @Test void afterCompletion_rolledBack_dropsThePark() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        IndexingManager im = mock(IndexingManager.class);
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, null);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-r"), false);
            sync.afterCompletion(Status.STATUS_ROLLEDBACK);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
    }

    @Test void afterCompletion_unexpectedStatus_runsIndexRequestsButNeverAnUnindex() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        IndexingManager im = mock(IndexingManager.class);
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, null);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-u-idx"), false);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-u-gone"), true);
            sync.afterCompletion(Status.STATUS_UNKNOWN);
        }
        verify(im).addIndexingQueueEntry(eq("enc-u-idx"), eq(Encounter.class), eq(false));
        verify(im, never()).addIndexingQueueEntry(eq("enc-u-gone"), any(), anyBoolean());
    }

    @Test void afterCompletion_leavesAFreshLiveParkForTheNextTransaction() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        IndexingManager.PendingBucket first = bucketOf(pm);
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, null);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(
                mock(IndexingManager.class));
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
        assertTrue(first.isCompleted());
        assertNotSame(first, bucketOf(pm));
        assertFalse(bucketOf(pm).isCompleted());
    }

    // ---- delegate handling --------------------------------------------------------------------

    // beforeCompletion is the delegate's chance to veto the commit; we must not swallow that.
    @Test void beforeCompletion_callsTheDelegate_andLetsItsExceptionPropagate() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        Synchronization delegate = mock(Synchronization.class);
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, delegate);

        sync.beforeCompletion();
        verify(delegate).beforeCompletion();

        doThrow(new IllegalStateException("veto")).when(delegate).beforeCompletion();
        assertThrows(IllegalStateException.class, sync::beforeCompletion);
    }

    @Test void beforeCompletion_withoutDelegate_isANoOp() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));

        new IndexingTransactionSynchronization(pm, pmf, null).beforeCompletion();
    }

    // The delegate runs AFTER the park is terminalized and its successor installed, and BEFORE
    // anything is handed to the queue.
    @Test void afterCompletion_callsDelegate_afterCaptureAndBeforeDrain_withTheSameStatus() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        IndexingManager.PendingBucket first = bucketOf(pm);
        IndexingManager im = mock(IndexingManager.class);
        Synchronization delegate = mock(Synchronization.class);
        final boolean[] observedInsideDelegate = new boolean[2]; // [firstCompleted, successorLive]

        doAnswer((InvocationOnMock inv) -> {
            observedInsideDelegate[0] = first.isCompleted();
            IndexingManager.PendingBucket now = bucketOf(pm);
            observedInsideDelegate[1] = (now != first) && !now.isCompleted();
            return null;
        }).when(delegate).afterCompletion(anyInt());
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, delegate);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-d"), false);
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
        assertTrue(observedInsideDelegate[0], "park already terminalized when the delegate ran");
        assertTrue(observedInsideDelegate[1], "successor already installed when the delegate ran");
        InOrder inOrder = inOrder(delegate, im);

        inOrder.verify(delegate).afterCompletion(Status.STATUS_COMMITTED);
        inOrder.verify(im).addIndexingQueueEntry(eq("enc-d"), eq(Encounter.class), eq(false));
    }

    // A delegate may close the PM (its transaction is over). The snapshot was captured first, and
    // the drain must not depend on the PM, so nothing is lost.
    @Test void afterCompletion_delegateClosesThePm_snapshotStillDrains() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        IndexingManager im = mock(IndexingManager.class);
        Synchronization delegate = mock(Synchronization.class);

        doAnswer((InvocationOnMock inv) -> {
            when(pm.isClosed()).thenReturn(true);
            when(pm.getUserObject(any())).thenThrow(new javax.jdo.JDOFatalUserException("closed"));
            return null;
        }).when(delegate).afterCompletion(anyInt());
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, delegate);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-closed"), false);
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
        verify(im).addIndexingQueueEntry(eq("enc-closed"), eq(Encounter.class), eq(false));
    }

    // A delegate that throws from afterCompletion must not prevent the handoff, and must not
    // propagate out of a completion callback.
    @Test void afterCompletion_delegateThrows_snapshotStillDrains_andNothingPropagates() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = preparedPm(pmf, transactionHoldingSynchronization(true));
        IndexingManager im = mock(IndexingManager.class);
        Synchronization delegate = mock(Synchronization.class);

        doThrow(new RuntimeException("delegate broke")).when(delegate).afterCompletion(anyInt());
        IndexingTransactionSynchronization sync =
            new IndexingTransactionSynchronization(pm, pmf, delegate);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            IndexingManager.addPendingEntry(pm, encounterWithId("enc-throw"), false);
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
        verify(im).addIndexingQueueEntry(eq("enc-throw"), eq(Encounter.class), eq(false));
    }

    // ---- registration -------------------------------------------------------------------------

    @Test void registerSynchronization_installsOurs_andIsIdempotent() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        Transaction tx = transactionHoldingSynchronization(false);
        PersistenceManager pm = pmWithUserObjects(pmf, tx);

        IndexingManager.registerSynchronization(pm);
        IndexingManager.registerSynchronization(pm);
        IndexingManager.registerSynchronization(pm);

        verify(tx, times(1)).setSynchronization(any(IndexingTransactionSynchronization.class));
        assertTrue(tx.getSynchronization() instanceof IndexingTransactionSynchronization);
    }

    // JDO allows exactly one Synchronization per Transaction. Anyone who registered before us
    // must keep receiving both callbacks, with the same status.
    @Test void registerSynchronization_wrapsAPreExistingSynchronization() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        Transaction tx = transactionHoldingSynchronization(false);
        PersistenceManager pm = pmWithUserObjects(pmf, tx);
        Synchronization foreign = mock(Synchronization.class);

        tx.setSynchronization(foreign);
        IndexingManager.registerSynchronization(pm);

        Synchronization installed = tx.getSynchronization();
        assertTrue(installed instanceof IndexingTransactionSynchronization);
        assertNotSame(foreign, installed);

        installed.beforeCompletion();
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(
                mock(IndexingManager.class));
            installed.afterCompletion(Status.STATUS_ROLLEDBACK);
        }
        verify(foreign).beforeCompletion();
        verify(foreign).afterCompletion(Status.STATUS_ROLLEDBACK);
    }

    @Test void registerSynchronization_neverThrows_onNullOrClosedPm() {
        IndexingManager.registerSynchronization(null);

        PersistenceManager closed = mock(PersistenceManager.class);
        when(closed.isClosed()).thenReturn(true);
        IndexingManager.registerSynchronization(closed);

        PersistenceManager broken = mock(PersistenceManager.class);
        when(broken.currentTransaction()).thenThrow(new javax.jdo.JDOFatalUserException("boom"));
        IndexingManager.registerSynchronization(broken);
    }

    @Test void afterCompletion_onAPmThatWasNeverPrepared_isANoOp() {
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        PersistenceManager pm = pmWithUserObjects(pmf, transactionHoldingSynchronization(true));
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            new IndexingTransactionSynchronization(pm, pmf, null).afterCompletion(
                Status.STATUS_COMMITTED);
        }
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        assertNull(pm.getUserObject(IndexingManager.PENDING_USER_OBJECT_KEY),
            "nothing was installed on a PM that never had a park");
    }
}
