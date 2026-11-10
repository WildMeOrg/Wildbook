package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.ecocean.OpenSearch;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.identity.IBEISIA;
import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bulk-import boxes PR A, task 9 (spec 4.4, recovery at parity with detection): once per
 * startup, non-terminal imported-box children older than the stale threshold have their queue
 * message republished. Duplicate-tolerant by design: a job that is merely still queued is
 * republished too, and the processor's embedding-exists path makes that harmless.
 */
@Testcontainers
class ImportedBoxReplayDbTest extends IdentificationDbTestSupport {
    private static final long HOUR = 60L * 60L * 1000L;

    @AfterAll static void reset() {
        IA.setDispatcherForTests(null);
        TestIAJsonPropertiesUtil.override(null);
    }

    static String job(String taskId) {
        return new JSONObject().put("mlServiceV2", true).put("annotationId", "ann-" + taskId)
            .put("taxonomyString", "Genus species").put("taskId", taskId).put("__context", CTX).toString();
    }

    /** A child of a fresh root; status set BEFORE the test fixes its modified time. */
    static String persistChild(Shepherd sh, boolean imported, String status, long modified, boolean withMessage) {
        Task root = new Task();
        root.setParameters(new JSONObject().put("importTaskId", nextImportId()));
        Task child = new Task(root);
        if (imported) {
            child.addParameter(Task.PARAM_IMPORTED_BOX, true);
            child.addParameter(Task.PARAM_EMBEDDING_EXTRACTION, true);
        } else {
            child.addParameter("ibeis.detection", true);
        }
        child.setStatus(status);
        if (withMessage) child.setQueueResumeMessage(job(child.getId()));
        child.setModifiedForTests(modified);
        sh.getPM().makePersistent(root);
        sh.getPM().makePersistent(child);
        return child.getId();
    }

    static List<String> capturedReplay(long cutoff) {
        List<String> captured = new ArrayList<String>();
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                captured.add(call.getArgument(1, String.class)); return true; });
            ImportedBoxReplay.replay(CTX, cutoff);
        }
        return captured;
    }

    @Test void onlyStaleNonTerminalImportedBoxChildrenAreReplayed() throws Exception {
        long now = System.currentTimeMillis();
        Shepherd sh = open("fixture");
        String stale = persistChild(sh, true, "initiated", now - 2 * HOUR, true);
        String done = persistChild(sh, true, "completed", now - 2 * HOUR, true);
        String fresh = persistChild(sh, true, "initiated", now - 5 * 60 * 1000L, true);
        String detection = persistChild(sh, false, "initiated", now - 2 * HOUR, true);
        String silent = persistChild(sh, true, "initiated", now - 2 * HOUR, false);
        String processing = persistChild(sh, true, "processing", now - 3 * HOUR, true);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        Shepherd q = open("query");
        try {
            List<String> messages = ImportedBoxReplay.fetchStaleResumeMessages(q, now - HOUR, 500);
            assertTrue(messages.contains(job(stale)), "the stale initiated imported child is replayed");
            assertTrue(messages.contains(job(processing)), "a stale processing imported child is replayed");
            assertFalse(messages.contains(job(done)), "a completed child is not");
            assertFalse(messages.contains(job(fresh)), "a child younger than the threshold is not");
            assertFalse(messages.contains(job(detection)), "a detection child is not (no importedBox marker)");
            assertFalse(messages.contains(job(silent)), "a child without a resume message yields nothing");
            assertEquals(messages.indexOf(job(processing)) < messages.indexOf(job(stale)), true, "oldest first");
            assertEquals(1, ImportedBoxReplay.fetchStaleResumeMessages(q, now - HOUR, 1).size(), "the range bounds the batch");
        } finally {
            q.rollbackAndClose();
        }
    }

    @Test void replayPublishesThroughTheLaneClassifierAndIsDuplicateTolerant() throws Exception {
        long now = System.currentTimeMillis();
        Shepherd sh = open("fixture");
        String stale = persistChild(sh, true, "initiated", now - 2 * HOUR, true);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<String> first = capturedReplay(now - HOUR);
        assertTrue(first.contains(job(stale)), "the message is republished on the detection lane");
        List<String> second = capturedReplay(now - HOUR);
        assertTrue(second.contains(job(stale)), "a second startup republishes it again: no pending-job check, by design");
    }

    @Test void replayAfterCrashProducesTheRun() throws Exception {
        MlServiceProcessorImportedBoxDbTest.configure(true, true);
        MlServiceProcessorImportedBoxDbTest.StatusAtDispatch dispatcher = new MlServiceProcessorImportedBoxDbTest.StatusAtDispatch();
        IA.setDispatcherForTests(dispatcher);
        MlServiceProcessorImportedBoxDbTest.Fixture f = MlServiceProcessorImportedBoxDbTest.boxedImport(1, false);
        String a = f.annIds.get(0);
        String childId = f.childFor(a);

        // the crash: the embedding is committed, then the JVM dies before the match is dispatched
        MlServiceProcessorImportedBoxDbTest.FakeClient crashedClient = new MlServiceProcessorImportedBoxDbTest.FakeClient();
        MatchVisibilityGate dying = (ids, task, cfg, attempt, first) -> { throw new IllegalStateException("crash before dispatch"); };
        MlServiceProcessor crashing = new MlServiceProcessor(CTX, crashedClient, dying,
            new MlServiceProcessorImportedBoxDbTest.RecordingPublisher());
        assertThrows(IllegalStateException.class, () -> crashing.process(f.jobFor(a)));
        assertEquals(1, MlServiceProcessorImportedBoxDbTest.embeddings(a));
        assertEquals("processing", MlServiceProcessorImportedBoxDbTest.status(childId), "non-terminal with its job consumed");
        assertEquals(IBEISIA.STATUS_COMPLETE, MlServiceProcessorImportedBoxDbTest.assetStatus(f.assetId));

        // the restart, an hour later
        Shepherd age = open("age");
        try {
            Task.load(childId, age).setModifiedForTests(System.currentTimeMillis() - 2 * HOUR);
            age.commitDBTransaction();
        } finally {
            age.closeDBTransaction();
        }
        List<String> replayed = capturedReplay(System.currentTimeMillis() - HOUR);
        List<String> ours = new ArrayList<String>();
        for (String m : replayed) if (childId.equals(new JSONObject(m).getString("taskId"))) ours.add(m);
        assertEquals(1, ours.size(), "the crashed child's extraction job is republished");

        // the republished job on a healthy processor: no second extraction, a durable run
        OpenSearch os = mock(OpenSearch.class);
        when(os.waitForVisibility(anyString(), any(), anyLong())).thenReturn(true);
        when(os.waitForAnnotationMatchableIds(any(), any(), any(), anyLong())).thenReturn(true);
        MlServiceProcessorImportedBoxDbTest.FakeClient healthyClient = new MlServiceProcessorImportedBoxDbTest.FakeClient();
        MlServiceProcessor healthy = new MlServiceProcessor(CTX, healthyClient, new MatchVisibilityGateImpl(CTX, os),
            new MlServiceProcessorImportedBoxDbTest.RecordingPublisher());
        dispatcher.childId = childId;
        assertEquals(MlServiceJobOutcome.Kind.OK, healthy.process(new JSONObject(ours.get(0))).getKind());
        assertEquals(0, healthyClient.calls, "the stored embedding is reused");
        assertEquals(1, MlServiceProcessorImportedBoxDbTest.runsUnder(childId).size(), "the run exists now");
        assertEquals("completed", MlServiceProcessorImportedBoxDbTest.status(childId));
        assertEquals("processing", dispatcher.statuses.get(0), "still non-terminal at dispatch");
    }
}
