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
        // the message's media ids now come from the ImportTask through the shared envelope (bulk-import boxes PR E)
        org.ecocean.media.MediaAsset a = mock(org.ecocean.media.MediaAsset.class), b = mock(org.ecocean.media.MediaAsset.class);
        when(a.getId()).thenReturn("42"); when(b.getId()).thenReturn("43"); when(imported.getMediaAssets()).thenReturn(java.util.Arrays.asList(a, b));
        try (MockedStatic<IA> ia = mockStatic(IA.class)) {
            ia.when(() -> IA.getBaseURL("context0")).thenReturn("https://example.test");
            JSONObject message = new SubmissionProcessing(() -> sh).prepare(draft, sh);
            assertFalse(message.has("annotationMode"));
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
            when(sh.commitDBTransactionWithStatus()).thenReturn(true); // the hand-off now commits with status before it publishes (bulk-import boxes PR A)
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

    // ---- bulk-import boxes PR E (spec 4.9): boxed drafts ----
    static Submission boxedDraft(String mode) {
        Submission draft = unimported(mode); draft.boxed(true);
        draft.imported(new JSONObject().put("rows", new JSONArray()).put("records", new JSONObject().put("mediaAssets", new JSONArray().put(42))).toString());
        return draft;
    }

    @Test void boxedImportOnlyDraftRequestsAiButNotIdentification() {
        Submission draft = boxedDraft("import-only");
        assertTrue(draft.requestsAi()); assertFalse(draft.requestsIdentification());
        assertEquals("pending", draft.getAiState());
        JSONObject json = draft.json(false);
        assertEquals("skipped", json.getJSONObject("detection").getString("state"));
        assertEquals("BOXES_SUPPLIED", json.getJSONObject("detection").getString("code"));
        JSONObject id = json.getJSONObject("identification");
        assertEquals("pending", id.getString("state"));
        assertEquals("BOXES_EXTRACTED_ONLY", id.getString("code"));
        assertTrue(id.getString("message").contains("Extraction-only") && id.getString("message").contains("identification is not run"), id.toString());
        assertEquals("import-only", json.getJSONObject("processing").getString("mode"));
        draft.claimAi(1); draft.aiState("dispatched");
        assertEquals("dispatched", draft.identificationPhase().getString("state"));
        assertEquals("BOXES_EXTRACTED_ONLY", draft.identificationPhase().getString("code"));
    }

    @Test void boxedDetectAndIdentifyDraftSkipsDetectionOnly() {
        Submission draft = boxedDraft("detect-and-identify");
        JSONObject json = draft.json(false);
        assertEquals("BOXES_SUPPLIED", json.getJSONObject("detection").getString("code"));
        JSONObject id = json.getJSONObject("identification");
        assertEquals("pending", id.getString("state")); assertFalse(id.has("code"));
        assertTrue(id.getString("message").contains("Extraction and identification"), id.toString());
    }

    @Test void boxedDraftFailuresKeepTheirCodesAndMessages() {
        for (String mode : new String[]{"import-only", "detect-and-identify"}) {
            Submission failed = unimported(mode); failed.boxed(true); failed.fail("IMPORT_FAILED", false);
            JSONObject id = failed.identificationPhase();
            assertEquals("not_started", id.getString("state"), mode); assertEquals("IMPORT_FAILED", id.getString("code"), mode);
            assertTrue(id.getString("message").contains("Inspect the import outcome"), id.toString());
            assertFalse(id.getString("message").toLowerCase().contains("extract"), id.toString());
            assertEquals("BOXES_SUPPLIED", failed.detectionPhase().getString("code"));
            Submission uncertain = unimported(mode); uncertain.boxed(true); uncertain.fail("COMMIT_OUTCOME_UNCERTAIN", true);
            assertEquals("IMPORT_OUTCOME_UNCERTAIN", uncertain.identificationPhase().getString("code"), mode);
            for (String state : new String[]{"failed", "unknown"}) {
                Submission handoff = boxedDraft(mode); handoff.aiState(state);
                JSONObject phase = handoff.identificationPhase();
                assertEquals(state, phase.getString("state"));
                assertEquals("unknown".equals(state) ? "AI_HANDOFF_UNKNOWN" : "AI_HANDOFF_FAILED", phase.getString("code"));
                assertTrue(phase.getString("message").contains("operator inspection"), phase.toString());
                assertFalse(phase.getString("message").toLowerCase().contains("extract"), phase.toString());
            }
        }
    }

    @Test void unboxedDraftsAreUnchangedByTheBoxViews() {   // (regression)
        Submission importOnly = draft("import-only");
        assertFalse(importOnly.isBoxed()); assertFalse(importOnly.requestsAi());
        assertEquals(importOnly.aiPhase().toString(), importOnly.detectionPhase().toString());
        assertEquals(importOnly.aiPhase().toString(), importOnly.identificationPhase().toString());
        assertFalse(importOnly.identificationPhase().has("code"));
        Submission detect = draft("detect-and-identify");
        assertEquals("pending", detect.detectionPhase().getString("state"));
        assertTrue(detect.identificationPhase().getString("message").startsWith("Detection and identification"));
    }

    @Test void hasBoxesReadsTheApprovedRows() {
        assertFalse(SubmissionJobs.hasBoxes(null));
        JSONObject empty = new JSONObject().put("fields", new JSONObject().put("Encounter.mediaAsset0", "a.png").put("Encounter.mediaAsset0.bbox", ""));
        JSONObject boxed = new JSONObject().put("fields", new JSONObject().put("Encounter.mediaAsset0", "a.png").put("Encounter.mediaAsset0.bbox", "0,0,1,1"));
        assertFalse(SubmissionJobs.hasBoxes(new JSONArray().put(empty)));
        assertTrue(SubmissionJobs.hasBoxes(new JSONArray().put(empty).put(boxed)));
        assertFalse(SubmissionJobs.hasBoxes(new JSONArray().put(new JSONObject().put("fields", new JSONObject().put("Encounter.year", 2020)))));
    }

    // ---- Task 4: the hand-off through the shared envelope ----
    static ImportTask boxedImportTask() {
        ImportTask task = mock(ImportTask.class);
        when(task.getId()).thenReturn("job"); when(task.annotationsImported()).thenReturn(true);
        org.ecocean.Annotation ann = mock(org.ecocean.Annotation.class);
        when(ann.isImportedBox()).thenReturn(true); when(ann.importedBoxImportTaskId()).thenReturn("job"); when(ann.getId()).thenReturn("ann-1");
        org.ecocean.Encounter enc = mock(org.ecocean.Encounter.class);
        when(enc.getAnnotations()).thenReturn(new java.util.ArrayList<>(java.util.List.of(ann)));
        when(task.getEncounters()).thenReturn(new java.util.ArrayList<>(java.util.List.of(enc)));
        return task;
    }

    @Test void prepareUsesTheSharedEnvelopeForABoxedDraft() throws Exception {
        for (String mode : new String[]{"import-only", "detect-and-identify"}) {
            Submission draft = boxedDraft(mode); Shepherd sh = mock(Shepherd.class);
            when(sh.getPM()).thenReturn(mock(javax.jdo.PersistenceManager.class));
            ImportTask task = boxedImportTask(); when(sh.getImportTask("job")).thenReturn(task);
            try (MockedStatic<IA> ia = mockStatic(IA.class)) {
                ia.when(() -> IA.getBaseURL("context0")).thenReturn("https://example.test");
                JSONObject message = new SubmissionProcessing(() -> sh).prepare(draft, sh);
                assertEquals("extract-then-identify", message.getString("annotationMode"), mode);
                assertEquals("[\"ann-1\"]", message.getJSONArray("annotationIds").toString(), mode);
                assertFalse(message.has("mediaAssetIds"), mode);
                assertEquals("import-only".equals(mode), message.getJSONObject("taskParameters").getBoolean("skipIdent"), mode);
            }
        }
    }

    @Test void boxedMessageMatchesTheLegacyBulkImportContract() throws Exception {
        Shepherd sh = mock(Shepherd.class); javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class); when(sh.getPM()).thenReturn(pm);
        ImportTask task = boxedImportTask(); when(sh.getImportTask("job")).thenReturn(task);
        try (MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<org.ecocean.servlet.IAGateway> gateway = mockStatic(org.ecocean.servlet.IAGateway.class, CALLS_REAL_METHODS)) {
            ia.when(() -> IA.getBaseURL("context0")).thenReturn("https://example.test");
            JSONObject actual = new SubmissionProcessing(() -> sh).prepare(boxedDraft("detect-and-identify"), sh);
            java.util.concurrent.atomic.AtomicReference<JSONObject> legacy = new java.util.concurrent.atomic.AtomicReference<>();
            gateway.when(() -> org.ecocean.servlet.IAGateway.addToDetectionQueue(eq("context0"), anyString())).thenAnswer(call -> { legacy.set(new JSONObject(call.getArgument(1, String.class))); return true; });
            when(sh.commitDBTransactionWithStatus()).thenReturn(true);
            when(sh.isDBTransactionActive()).thenReturn(true);
            JSONObject data = new JSONObject().put("taskParameters", new JSONObject().put("importTaskId", "job").put("skipIdent", false)).put("bulkImport", new JSONObject());
            org.ecocean.servlet.IAGateway.handleBulkImport(data, new JSONObject(), sh, "context0", "https://example.test");
            assertNotNull(legacy.get(), "the bulk-import hand-off published its message");
            for (String volatileKey : new String[]{"taskId", "__handleBulkImport"}) { actual.remove(volatileKey); legacy.get().remove(volatileKey); }
            assertEquals(SubmissionJson.canonical(legacy.get()), SubmissionJson.canonical(actual));
        }
    }

    // ---- Codex branch round 1 regressions ----
    @Test void unboxedPhaseObjectsAreLiterallyUnchanged() {
        assertEquals("{\"message\":\"Explicit import-only mode\",\"state\":\"skipped\"}", SubmissionJson.canonical(draft("import-only").identificationPhase()));
        assertEquals("{\"message\":\"Explicit import-only mode\",\"state\":\"skipped\"}", SubmissionJson.canonical(draft("import-only").detectionPhase()));
        JSONObject pending = new JSONObject().put("state", "pending").put("message", "Detection and identification workflow handoff; dispatched does not mean completed. See import task for progress and match candidates.");
        assertEquals(SubmissionJson.canonical(pending), SubmissionJson.canonical(draft("detect-and-identify").detectionPhase()));
        assertEquals(SubmissionJson.canonical(pending), SubmissionJson.canonical(draft("detect-and-identify").identificationPhase()));
    }

    @Test void boxesExtractedOnlyIsAddedToNormalStatesOnly() {
        for (String state : new String[]{"pending", "dispatching", "dispatched"}) {
            Submission d = boxedDraft("import-only"); d.aiState(state);
            assertEquals("BOXES_EXTRACTED_ONLY", d.identificationPhase().getString("code"), state);
        }
        Submission skipped = boxedDraft("import-only"); skipped.aiState("skipped"); // not a hand-off state
        assertFalse(skipped.identificationPhase().has("code"));
    }

    @Test void hasBoxesIgnoresNonStringValues() {
        JSONObject numeric = new JSONObject().put("fields", new JSONObject().put("Encounter.mediaAsset0", "a.png").put("Encounter.mediaAsset0.bbox", 5));
        assertFalse(SubmissionJobs.hasBoxes(new JSONArray().put(numeric)));
    }

    @Test void prepareRejectsADraftWithNoImportedMedia() {
        Submission draft = unimported("detect-and-identify");
        draft.imported(new JSONObject().put("rows", new JSONArray()).put("records", new JSONObject().put("mediaAssets", new JSONArray())).toString());
        Shepherd sh = mock(Shepherd.class); when(sh.getImportTask("job")).thenReturn(mock(ImportTask.class));
        try (MockedStatic<IA> ia = mockStatic(IA.class)) {
            ia.when(() -> IA.getBaseURL("context0")).thenReturn("https://example.test");
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new SubmissionProcessing(() -> sh).prepare(draft, sh));
            assertEquals("No imported media", ex.getMessage());
        }
    }
}
