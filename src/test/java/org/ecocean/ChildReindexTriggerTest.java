package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

/**
 * Model-level unit tests for the child-reindex membership hooks (Spec-A Task 7).
 * Verifies that encounter<->individual membership changes enqueue a deep reindex of the
 * affected encounter + old/new individuals via IndexingManager, and that skipAutoIndexing
 * suppresses the enqueue (so bulk import / deserialization do not storm the queue).
 *
 * enqueueAclReindex() routes through IndexingManager.addPendingEntry(), which PARKS the request
 * while the object's PersistenceManager has an active transaction and releases it when that
 * transaction completes. The objects here are transient (no PersistenceManager), so they take the
 * immediate fallback and land on the (id, class, unindex) overload -- same request, different
 * signature. The two tests at the bottom pin the parked path with a stubbed PersistenceManager.
 *
 * The ACL-propagation path inside Encounter.opensearchIndexPermissions() requires a live
 * Shepherd + OpenSearch and is covered by the Task 9 integration test, not here.
 */
class ChildReindexTriggerTest {

    // The triggers honor the global /tmp/skipAutoIndexing kill-switch (OpenSearch.skipAutoIndexing
    // reads the host filesystem). Another test in the suite (EncounterExportImagesTest) creates
    // that file and only removes it at JVM exit, so these tests MUST stub the static to false —
    // otherwise they fail with "zero interactions" whenever they run after it (or on a dev
    // machine with a leftover /tmp/skipAutoIndexing).
    private static MockedStatic<OpenSearch> mockNoGlobalSkip() {
        MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class);
        os.when(OpenSearch::skipAutoIndexing).thenReturn(false);
        return os;
    }

    // sets the private Encounter.individual field directly, so we can establish a
    // pre-existing "old" individual without triggering the setIndividual() hook.
    private static void setIndividualField(Encounter enc, MarkedIndividual indiv) throws Exception {
        Field f = Encounter.class.getDeclaredField("individual");
        f.setAccessible(true);
        f.set(enc, indiv);
    }

    // setIndividual enqueues the encounter (covers new individual + annotations via deep index)
    // AND the old individual it left.
    @Test void setIndividual_enqueuesEncounterAndOldIndividual() throws Exception {
        Encounter enc = spy(new Encounter());
        enc.setSkipAutoIndexing(false);
        enc.setCatalogNumber("enc-0"); // a request needs an id; nothing is enqueued for id-less objects
        MarkedIndividual oldInd = spy(new MarkedIndividual());
        oldInd.setSkipAutoIndexing(false);
        oldInd.setIndividualID("ind-old");
        setIndividualField(enc, oldInd);
        MarkedIndividual newInd = mock(MarkedIndividual.class);

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            enc.setIndividual(newInd);
        }
        // enc (its new individual + annotations refresh via deep index)
        verify(im).addIndexingQueueEntry(eq("enc-0"), eq(Encounter.class), eq(false));
        // old individual the encounter left
        verify(im).addIndexingQueueEntry(eq("ind-old"), eq(MarkedIndividual.class), eq(false));
    }

    // skipAutoIndexing on the encounter (and old individual) suppresses all enqueues.
    @Test void setIndividual_skipAutoIndexing_noEnqueue() throws Exception {
        Encounter enc = spy(new Encounter());
        enc.setSkipAutoIndexing(true);
        enc.setCatalogNumber("enc-skip");
        MarkedIndividual oldInd = spy(new MarkedIndividual());
        oldInd.setSkipAutoIndexing(true); // both skip => assert NO enqueue at all
        oldInd.setIndividualID("ind-skip");
        setIndividualField(enc, oldInd);
        MarkedIndividual newInd = mock(MarkedIndividual.class);

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) { // global off: proves the ENTITY flag suppresses
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            enc.setIndividual(newInd);
        }
        verify(im, never()).addIndexingQueueEntry(any(Base.class), anyBoolean());
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
    }

    // MarkedIndividual.removeEncounter enqueues the individual AND the departed encounter.
    @Test void removeEncounter_enqueuesIndividualAndEncounter() throws Exception {
        MarkedIndividual ind = spy(new MarkedIndividual());
        ind.setSkipAutoIndexing(false);
        ind.setIndividualID("ind-1");
        Encounter enc = spy(new Encounter());
        enc.setSkipAutoIndexing(false);
        enc.setCatalogNumber("enc-1");
        // addEncounterNoCommit avoids firing the addEncounter hook so we isolate removeEncounter.
        ind.addEncounterNoCommit(enc);

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            ind.removeEncounter(enc);
        }
        verify(im).addIndexingQueueEntry(eq("ind-1"), eq(MarkedIndividual.class), eq(false));
        verify(im).addIndexingQueueEntry(eq("enc-1"), eq(Encounter.class), eq(false));
    }

    // MarkedIndividual.addEncounter enqueues the joining encounter.
    @Test void addEncounter_enqueuesEncounter() throws Exception {
        MarkedIndividual ind = spy(new MarkedIndividual());
        ind.setSkipAutoIndexing(false);
        ind.setIndividualID("ind-2");
        Encounter enc = spy(new Encounter());
        enc.setSkipAutoIndexing(false);
        enc.setCatalogNumber("enc-2");

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            ind.addEncounter(enc);
        }
        verify(im).addIndexingQueueEntry(eq("enc-2"), eq(Encounter.class), eq(false));
    }

    // ---- the parked path ----------------------------------------------------------------------

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

    // Inside a transaction the request must wait for the commit: parked, not enqueued.
    @Test void enqueueAclReindex_parksWhileTheObjectsTransactionIsActive() {
        PersistenceManager pm = preparedPm();
        Encounter enc = new Encounter();
        enc.setSkipAutoIndexing(false);
        enc.setCatalogNumber("enc-parked");

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip();
            MockedStatic<JDOHelper> jdo = mockStatic(JDOHelper.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            jdo.when(() -> JDOHelper.getPersistenceManager(enc)).thenReturn(pm);
            enc.enqueueAclReindex();
        }
        verify(im, never()).addIndexingQueueEntry(any(Base.class), anyBoolean());
        verify(im, never()).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        assertEquals(1, parked(pm));
    }

    // The permissions pass runs in a read transaction that is ROLLED BACK at the end; parking
    // there would drop the request. It uses the Now flavor, which enqueues immediately.
    @Test void enqueueAclReindexNow_enqueuesImmediatelyEvenInsideATransaction() {
        PersistenceManager pm = preparedPm();
        Encounter enc = new Encounter();
        enc.setSkipAutoIndexing(false);
        enc.setCatalogNumber("enc-now");

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip();
            MockedStatic<JDOHelper> jdo = mockStatic(JDOHelper.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            jdo.when(() -> JDOHelper.getPersistenceManager(enc)).thenReturn(pm);
            enc.enqueueAclReindexNow();
        }
        verify(im).addIndexingQueueEntry(eq("enc-now"), eq(Encounter.class), eq(false));
        assertEquals(0, parked(pm));
    }
}
