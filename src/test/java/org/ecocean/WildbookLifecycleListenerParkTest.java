package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import javax.jdo.listener.InstanceLifecycleEvent;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

/**
 * postStore() fires on JDO flush, not on commit. It must PARK the reindex request against the
 * object's PersistenceManager (released by the transaction's completion callback), not enqueue
 * it -- enqueuing there is what let the background indexer read pre-commit state.
 */
class WildbookLifecycleListenerParkTest {
    private static PersistenceManager preparedPm() {
        PersistenceManager pm = mock(PersistenceManager.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        Transaction tx = mock(Transaction.class);
        final Map<Object, Object> userObjects = new HashMap<Object, Object>();

        when(tx.isActive()).thenReturn(true);
        when(pm.currentTransaction()).thenReturn(tx);
        when(pm.isClosed()).thenReturn(false);
        when(pm.getPersistenceManagerFactory()).thenReturn(pmf);
        when(pm.getUserObject(any())).thenAnswer((InvocationOnMock inv) ->
            userObjects.get(inv.getArgument(0)));
        when(pm.putUserObject(any(), any())).thenAnswer((InvocationOnMock inv) ->
            userObjects.put(inv.getArgument(0), inv.getArgument(1)));
        IndexingManager.installPendingBucket(pm);
        return pm;
    }

    private static int parked(PersistenceManager pm) {
        return ((IndexingManager.PendingBucket)pm.getUserObject(
            IndexingManager.PENDING_USER_OBJECT_KEY)).size();
    }

    private static InstanceLifecycleEvent storeEventFor(Object source) {
        InstanceLifecycleEvent event = mock(InstanceLifecycleEvent.class);

        when(event.getSource()).thenReturn(source);
        return event;
    }

    @Test void postStore_parksAgainstTheObjectsPersistenceManager_insteadOfEnqueuing() {
        PersistenceManager pm = preparedPm();
        Encounter enc = new Encounter();

        enc.setCatalogNumber("enc-store-1");
        enc.setSkipAutoIndexing(false);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class);
            MockedStatic<JDOHelper> jdo = mockStatic(JDOHelper.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);
            jdo.when(() -> JDOHelper.getPersistenceManager(enc)).thenReturn(pm);

            new WildbookLifecycleListener().postStore(storeEventFor(enc));
        }
        verify(im, never()).addIndexingQueueEntry(any(Base.class), anyBoolean());
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        assertEquals(1, parked(pm), "the request waits for the transaction to complete");
    }

    // The park then hands the request over by identity when the transaction commits.
    @Test void postStore_parkedRequest_isEnqueuedByIdentityOnCommit() {
        PersistenceManager pm = preparedPm();
        Encounter enc = new Encounter();

        enc.setCatalogNumber("enc-store-2");
        enc.setSkipAutoIndexing(false);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class);
            MockedStatic<JDOHelper> jdo = mockStatic(JDOHelper.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);
            jdo.when(() -> JDOHelper.getPersistenceManager(enc)).thenReturn(pm);

            new WildbookLifecycleListener().postStore(storeEventFor(enc));
            IndexingManager.completePendingEntries(pm, IndexingManager.Outcome.COMMITTED);
        }
        verify(im).addIndexingQueueEntry(eq("enc-store-2"), eq(Encounter.class), eq(false));
    }

    // Both skip guards still apply before anything is parked.
    @Test void postStore_honorsSkipAutoIndexing() {
        PersistenceManager pm = preparedPm();
        Encounter enc = new Encounter();

        enc.setCatalogNumber("enc-skip-1");
        enc.setSkipAutoIndexing(true);
        IndexingManager im = mock(IndexingManager.class);

        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class);
            MockedStatic<JDOHelper> jdo = mockStatic(JDOHelper.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);
            jdo.when(() -> JDOHelper.getPersistenceManager(enc)).thenReturn(pm);

            new WildbookLifecycleListener().postStore(storeEventFor(enc));
        }
        assertEquals(0, parked(pm));
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
    }
}
