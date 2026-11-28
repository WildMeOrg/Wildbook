package org.ecocean;

import java.util.List;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.transaction.Synchronization;

/**
 * Bridges a JDO transaction's completion to the deferred-indexing park in IndexingManager.
 *
 * DataNucleus invokes Synchronization.afterCompletion(status) from inside Transaction.commit() and
 * Transaction.rollback(), after its own post-commit processing, on the committing thread. That is
 * the one moment we know how the writing transaction ended, so it is the ONLY place a park is
 * terminalized: drained if the transaction committed, dropped if it rolled back (see
 * IndexingManager.Outcome for the unexpected-status rule). Because the callback comes from the
 * transaction itself, a raw pm.currentTransaction().commit() is covered exactly like
 * Shepherd.commitDBTransaction(); nothing in Shepherd's commit/rollback/close paths is involved.
 *
 * JDO allows one Synchronization per Transaction, so this wraps whatever was registered before it
 * (nothing, in Wildbook today) and keeps calling it. The order inside afterCompletion is fixed and
 * load-bearing:
 *   1. capture: terminalize the park, take the requests that should run, install the successor
 *      park -- no external work, cannot throw;
 *   2. delegate.afterCompletion(status) -- may close the PM, may throw; neither can lose the
 *      snapshot, and a throw is logged, never propagated;
 *   3. drain the snapshot -- level-2 eviction and queue handoff, independent of the PM.
 * beforeCompletion() is the delegate's chance to veto the commit, so its exceptions propagate.
 *
 * Registered once per PersistenceManager by Shepherd.beginDBTransaction() via
 * IndexingManager.registerSynchronization(); DataNucleus keeps the registration across the PM's
 * successive transactions.
 */
public final class IndexingTransactionSynchronization implements Synchronization {
    private final PersistenceManager pm;
    // captured at registration: needed for level-2 eviction after a delegate may have closed pm
    private final PersistenceManagerFactory pmf;
    private final Synchronization delegate; // may be null

    public IndexingTransactionSynchronization(PersistenceManager pm, PersistenceManagerFactory pmf,
        Synchronization delegate) {
        this.pm = pm;
        this.pmf = pmf;
        this.delegate = delegate;
    }

    @Override public void beforeCompletion() {
        if (delegate != null) delegate.beforeCompletion(); // a veto here must reach DataNucleus
    }

    @Override public void afterCompletion(int status) {
        IndexingManager.Outcome how = IndexingManager.outcomeOf(status);
        List<IndexingManager.PendingEntry> snapshot = IndexingManager.capturePendingEntries(pm, how);

        if (delegate != null) {
            try {
                delegate.afterCompletion(status);
            } catch (RuntimeException ex) {
                System.out.println("IndexingTransactionSynchronization: wrapped Synchronization " +
                    delegate.getClass().getName() + " threw from afterCompletion(" + status +
                    "); continuing with the indexing handoff. " + ex);
                ex.printStackTrace();
            }
        }
        IndexingManager.drainCaptured(snapshot, pmf);
    }
}
