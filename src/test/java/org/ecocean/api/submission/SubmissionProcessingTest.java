package org.ecocean.api.submission;

import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionProcessingTest {
    static Submission unimported(String mode) {
        JSONObject create = new JSONObject().put("source", new JSONObject().put("name", "test"));
        if (mode != null) create.put("processing", new JSONObject().put("mode", mode));
        Submission draft = new Submission("id", "context0", "owner", "key", "hash", create.toString(), 0, Long.MAX_VALUE);
        draft.queue("job", "key", "hash", "{}");
        return draft;
    }
    static Submission draft(String mode) {
        Submission draft = unimported(mode);
        draft.imported(new JSONObject().put("rows", new JSONArray()).put("records", new JSONObject().put("mediaAssets", new JSONArray().put(42).put(43))).toString());
        return draft;
    }
    @Test void oldMissingModeAndExplicitImportOnlyRemainSkipped() {
        for (String mode : new String[]{null, "import-only"}) {
            Submission draft = draft(mode);
            assertFalse(draft.requestsIdentification());
            assertEquals("skipped", draft.json(false).getJSONObject("detection").getString("state"));
            assertEquals("import-only", draft.json(false).getJSONObject("processing").getString("mode"));
        }
        assertEquals("pending", draft("detect-and-identify").getAiState());
    }
    @Test void recordImportFailuresAreNotMisreportedAsAiHandoffFailures() {
        Submission failed = unimported("detect-and-identify"); failed.fail("IMPORT_FAILED", false);
        assertEquals("IMPORT_FAILED", failed.aiPhase().getString("code"));
        assertEquals("not_started", failed.aiPhase().getString("state"));
        assertFalse(failed.aiPhase().getString("message").contains("records remain"));
        Submission uncertain = unimported("detect-and-identify"); uncertain.fail("COMMIT_OUTCOME_UNCERTAIN", true);
        assertEquals("IMPORT_OUTCOME_UNCERTAIN", uncertain.aiPhase().getString("code"));
        assertEquals("not_started", uncertain.aiPhase().getString("state"));
    }
    @Test void preparesExistingPipelineMessageAndTasksWithoutCommitting() throws Exception {
        Submission draft = draft("detect-and-identify"); Shepherd sh = mock(Shepherd.class);
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class); when(sh.getPM()).thenReturn(pm);
        ImportTask imported = mock(ImportTask.class); when(sh.getImportTask("job")).thenReturn(imported);
        try (MockedStatic<IA> ia = mockStatic(IA.class)) {
            ia.when(() -> IA.getBaseURL("context0")).thenReturn("https://example.test");
            JSONObject message = new SubmissionProcessing(() -> sh).prepare(draft, sh);
            assertEquals("context0", message.getString("__context")); assertEquals("https://example.test", message.getString("__baseUrl"));
            assertTrue(message.getBoolean("v2")); assertEquals("[\"42\",\"43\"]", message.getJSONArray("mediaAssetIds").toString());
            assertEquals("job", message.getJSONObject("taskParameters").getString("importTaskId"));
            assertFalse(message.getJSONObject("taskParameters").getBoolean("skipIdent"));
            org.mockito.ArgumentCaptor<Task> root = org.mockito.ArgumentCaptor.forClass(Task.class);
            verify(imported).setIATask(root.capture()); Task child = root.getValue().getChildren().get(0);
            assertEquals(child.getId(), message.getString("taskId")); assertEquals(message.toString(), child.getQueueResumeMessage());
            verify(sh, never()).storeNewTask(any()); verify(sh, never()).commitDBTransaction(); verify(sh, never()).commitDBTransactionWithStatus();
            verify(pm).makePersistent(root.getValue()); verify(pm).makePersistent(child);
        }
    }
    @Test void messageMatchesLegacyBulkImportPipelineContract() throws Exception {
        Shepherd sh = mock(Shepherd.class); javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class); when(sh.getPM()).thenReturn(pm);
        ImportTask task = mock(ImportTask.class); when(sh.getImportTask("job")).thenReturn(task);
        org.ecocean.media.MediaAsset a = mock(org.ecocean.media.MediaAsset.class), b = mock(org.ecocean.media.MediaAsset.class);
        when(a.getId()).thenReturn("42"); when(b.getId()).thenReturn("43"); when(task.getMediaAssets()).thenReturn(java.util.Arrays.asList(a, b));
        try (MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<org.ecocean.servlet.IAGateway> gateway = mockStatic(org.ecocean.servlet.IAGateway.class, CALLS_REAL_METHODS)) {
            ia.when(() -> IA.getBaseURL("context0")).thenReturn("https://example.test");
            JSONObject actual = new SubmissionProcessing(() -> sh).prepare(draft("detect-and-identify"), sh);
            java.util.concurrent.atomic.AtomicReference<JSONObject> legacy = new java.util.concurrent.atomic.AtomicReference<>();
            gateway.when(() -> org.ecocean.servlet.IAGateway.addToDetectionQueue(eq("context0"), anyString())).thenAnswer(call -> { legacy.set(new JSONObject(call.getArgument(1, String.class))); return true; });
            JSONObject data = new JSONObject().put("taskParameters", new JSONObject().put("importTaskId", "job").put("skipIdent", false)).put("bulkImport", new JSONObject());
            org.ecocean.servlet.IAGateway.handleBulkImport(data, new JSONObject(), sh, "context0", "https://example.test");
            for (String volatileKey : new String[]{"taskId", "__handleBulkImport"}) { actual.remove(volatileKey); legacy.get().remove(volatileKey); }
            assertEquals(SubmissionJson.canonical(legacy.get()), SubmissionJson.canonical(actual));
        }
    }
    @Test void missingCallbackConfigurationAndExistingTaskDoNotPrepareNewWork() throws Exception {
        Shepherd sh = mock(Shepherd.class); ImportTask task = mock(ImportTask.class); when(sh.getImportTask("job")).thenReturn(task);
        SubmissionProcessing processing = new SubmissionProcessing(() -> sh);
        try (MockedStatic<IA> ia = mockStatic(IA.class)) {
            assertThrows(IllegalStateException.class, () -> processing.prepare(draft("detect-and-identify"), sh));
            ia.when(() -> IA.getBaseURL("context0")).thenReturn("https://example.test");
            when(task.getIATask()).thenReturn(new Task());
            assertThrows(IllegalStateException.class, () -> processing.prepare(draft("detect-and-identify"), sh));
            verify(sh, never()).getPM();
        }
    }
}
