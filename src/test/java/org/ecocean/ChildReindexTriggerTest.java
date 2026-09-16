package org.ecocean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Model-level unit tests for the child-reindex membership hooks (Spec-A Task 7).
 * Verifies that encounter<->individual membership changes enqueue a deep reindex of the
 * affected encounter + old/new individuals via IndexingManager, and that skipAutoIndexing
 * suppresses the enqueue (so bulk import / deserialization do not storm the queue).
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
        MarkedIndividual oldInd = spy(new MarkedIndividual());
        oldInd.setSkipAutoIndexing(false);
        setIndividualField(enc, oldInd);
        MarkedIndividual newInd = mock(MarkedIndividual.class);

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            enc.setIndividual(newInd);
        }
        // enc (its new individual + annotations refresh via deep index)
        verify(im).addIndexingQueueEntry(eq(enc), eq(false));
        // old individual the encounter left
        verify(im).addIndexingQueueEntry(eq(oldInd), eq(false));
    }

    // skipAutoIndexing on the encounter (and old individual) suppresses all enqueues.
    @Test void setIndividual_skipAutoIndexing_noEnqueue() throws Exception {
        Encounter enc = spy(new Encounter());
        enc.setSkipAutoIndexing(true);
        MarkedIndividual oldInd = spy(new MarkedIndividual());
        oldInd.setSkipAutoIndexing(true); // both skip => assert NO enqueue at all
        setIndividualField(enc, oldInd);
        MarkedIndividual newInd = mock(MarkedIndividual.class);

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) { // global off: proves the ENTITY flag suppresses
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            enc.setIndividual(newInd);
        }
        verify(im, never()).addIndexingQueueEntry(any(), anyBoolean());
    }

    // MarkedIndividual.removeEncounter enqueues the individual AND the departed encounter.
    @Test void removeEncounter_enqueuesIndividualAndEncounter() throws Exception {
        MarkedIndividual ind = spy(new MarkedIndividual());
        ind.setSkipAutoIndexing(false);
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
        verify(im).addIndexingQueueEntry(eq(ind), eq(false));
        verify(im).addIndexingQueueEntry(eq(enc), eq(false));
    }

    // MarkedIndividual.addEncounter enqueues the joining encounter.
    @Test void addEncounter_enqueuesEncounter() throws Exception {
        MarkedIndividual ind = spy(new MarkedIndividual());
        ind.setSkipAutoIndexing(false);
        Encounter enc = spy(new Encounter());
        enc.setSkipAutoIndexing(false);
        enc.setCatalogNumber("enc-2");

        IndexingManager im = mock(IndexingManager.class);
        try (MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockNoGlobalSkip()) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            ind.addEncounter(enc);
        }
        verify(im).addIndexingQueueEntry(eq(enc), eq(false));
    }
}
