package org.ecocean.shepherd.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

import org.ecocean.IndexingManager;
import org.ecocean.IndexingTransactionSynchronization;
import org.ecocean.WildbookLifecycleListener;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;

/**
 * beginDBTransaction() is the ONE Shepherd lifecycle method this change touches. For a freshly
 * obtained PersistenceManager it must register the store listener, register the indexing
 * Synchronization, and install the deferred-indexing park -- and only THEN activate the
 * transaction. A store callback can only fire once the transaction is active, and by then it
 * needs both the listener and a live park in place.
 *
 * It must also do the registrations once per PM, not once per begin(): beginDBTransaction() runs
 * again on every updateDBTransaction(), and on main each run added ANOTHER WildbookLifecycleListener,
 * so one store event fanned out to N duplicate callbacks.
 */
class ShepherdBeginRegistrationTest {
    private static final String PENDING_KEY = IndexingManager.PENDING_USER_OBJECT_KEY;

    private static PersistenceManager mockPm(Transaction tx, boolean txActive) {
        PersistenceManager pm = mock(PersistenceManager.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        final Map<Object, Object> userObjects = new HashMap<Object, Object>();

        when(tx.isActive()).thenReturn(txActive);
        when(pm.currentTransaction()).thenReturn(tx);
        when(pm.isClosed()).thenReturn(false);
        when(pm.getPersistenceManagerFactory()).thenReturn(pmf);
        when(pm.getUserObject(any())).thenAnswer((InvocationOnMock inv) ->
            userObjects.get(inv.getArgument(0)));
        when(pm.putUserObject(any(), any())).thenAnswer((InvocationOnMock inv) ->
            userObjects.put(inv.getArgument(0), inv.getArgument(1)));
        return pm;
    }

    @Test void freshPm_registersListenerAndSynchronization_installsPark_thenBegins() {
        Transaction tx = mock(Transaction.class);
        PersistenceManager pm = mockPm(tx, false);

        PersistenceManager registeredOn = Shepherd.prepareAndBeginTransaction(pm, null);

        assertSame(pm, registeredOn, "the caller records which pm is now registered");
        InOrder inOrder = inOrder(pm, tx);

        inOrder.verify(pm).addInstanceLifecycleListener(any(WildbookLifecycleListener.class), any());
        inOrder.verify(tx).setSynchronization(any(IndexingTransactionSynchronization.class));
        inOrder.verify(pm).putUserObject(eq(PENDING_KEY), any());
        inOrder.verify(tx).begin();
        assertNotNull(pm.getUserObject(PENDING_KEY), "park must exist once the transaction is on");
    }

    // Same PM, next transaction (updateDBTransaction shape): no duplicate listener, no second
    // Synchronization; the park is refreshed only if needed, and still before begin().
    @Test void recycledPm_doesNotReRegister_butStillInstallsParkBeforeBegin() {
        Transaction tx = mock(Transaction.class);
        PersistenceManager pm = mockPm(tx, false);

        PersistenceManager registeredOn = Shepherd.prepareAndBeginTransaction(pm, pm);

        assertSame(pm, registeredOn);
        verify(pm, never()).addInstanceLifecycleListener(any(), any());
        verify(tx, never()).setSynchronization(any());
        InOrder inOrder = inOrder(pm, tx);

        inOrder.verify(pm).putUserObject(eq(PENDING_KEY), any());
        inOrder.verify(tx).begin();
    }

    // A redundant beginDBTransaction() on an already-active transaction must not re-begin, and
    // must not disturb the live park.
    @Test void alreadyActiveTransaction_doesNotBeginAgain_andKeepsTheLivePark() {
        Transaction tx = mock(Transaction.class);
        PersistenceManager pm = mockPm(tx, true);

        IndexingManager.installPendingBucket(pm);
        Object live = pm.getUserObject(PENDING_KEY);

        Shepherd.prepareAndBeginTransaction(pm, pm);
        verify(tx, never()).begin();
        assertSame(live, pm.getUserObject(PENDING_KEY), "live park survives a redundant begin");
    }

    @Test void nullPm_isANoOp() {
        assertNull(Shepherd.prepareAndBeginTransaction(null, null));
        PersistenceManager previous = mock(PersistenceManager.class);
        assertSame(previous, Shepherd.prepareAndBeginTransaction(null, previous));
    }
}
