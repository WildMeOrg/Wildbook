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

import javax.jdo.JDOException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;

/**
 * commitDBTransaction() hands parked index requests to IndexingManager only after the commit has
 * returned. That handoff runs in a finally, so nothing on the failure path may throw its way out
 * of the method -- a durable-but-unacknowledged commit would otherwise lose every parked request.
 *
 * The nested-exception dump used to be the hazard: JDOException.getNestedExceptions() can be null
 * (the message-only constructors leave it unset) and SQLException.getNextException() can be null,
 * and both were dereferenced unguarded.
 */
class ShepherdCommitHandoffTest {
    @Test void logNestedExceptions_nullException() {
        Shepherd.logNestedExceptions(null);
    }

    @Test void logNestedExceptions_nullNestedArray() {
        // message-only constructor: getNestedExceptions() returns null
        Shepherd.logNestedExceptions(new JDOException("boom"));
    }

    @Test void logNestedExceptions_nullElementInNestedArray() {
        Shepherd.logNestedExceptions(new JDOException("boom", new Throwable[] { null }));
    }

    @Test void logNestedExceptions_sqlExceptionWithNoNextException() {
        Shepherd.logNestedExceptions(
            new JDOException("boom", new Throwable[] { new java.sql.SQLException("x") }));
    }

    // ---- transaction-start ordering ----------------------------------------------------------
    //
    // The deferred-indexing park must be LIVE before the transaction is active. A store callback
    // fires only once the transaction is active, and if it arrives while the park still belongs to
    // the previous (terminal) transaction it either enqueues pre-commit off a drained park or is
    // dropped off a discarded one. These tests fail if that order is ever reversed.

    // must match IndexingManager.PENDING_USER_OBJECT_KEY
    private static final String PENDING_KEY = "org.ecocean.IndexingManager.pending";

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

    @Test void prepareAndBegin_registersListenerAndInstallsParkBeforeActivating() {
        Transaction tx = mock(Transaction.class);
        PersistenceManager pm = mockPm(tx, false);

        PersistenceManager registeredOn = Shepherd.prepareAndBeginTransaction(pm, null);

        assertSame(pm, registeredOn, "listener should now be registered on this pm");
        InOrder inOrder = inOrder(pm, tx);

        inOrder.verify(pm).addInstanceLifecycleListener(any(), any());
        inOrder.verify(pm).putUserObject(eq(PENDING_KEY), any());
        inOrder.verify(tx).begin();
        assertNotNull(pm.getUserObject(PENDING_KEY), "park must exist once the transaction is on");
    }

    // Recycled PM, next transaction: the listener is already registered, but the park must still
    // be refreshed before the transaction goes active.
    @Test void prepareAndBegin_recycledPm_doesNotReRegisterButStillInstallsBeforeBegin() {
        Transaction tx = mock(Transaction.class);
        PersistenceManager pm = mockPm(tx, false);

        PersistenceManager registeredOn = Shepherd.prepareAndBeginTransaction(pm, pm);

        assertSame(pm, registeredOn);
        verify(pm, never()).addInstanceLifecycleListener(any(), any());
        InOrder inOrder = inOrder(pm, tx);

        inOrder.verify(pm).putUserObject(eq(PENDING_KEY), any());
        inOrder.verify(tx).begin();
    }

    // A redundant beginDBTransaction() on an already-active transaction must not re-begin.
    @Test void prepareAndBegin_alreadyActive_doesNotBeginAgain() {
        Transaction tx = mock(Transaction.class);
        PersistenceManager pm = mockPm(tx, true);

        Shepherd.prepareAndBeginTransaction(pm, pm);
        verify(tx, never()).begin();
    }

    @Test void prepareAndBegin_nullPm_isANoOp() {
        assertNull(Shepherd.prepareAndBeginTransaction(null, null));
    }
}
