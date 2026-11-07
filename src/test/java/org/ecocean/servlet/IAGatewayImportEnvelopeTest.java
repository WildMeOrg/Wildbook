package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.jdo.PersistenceManager;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * Bulk-import boxes PR A, task 4 (spec 4.4): one envelope builder for the bulk-import hand-off.
 * An import stamped annotationsImported sends its imported-box annotation ids in extraction mode
 * instead of media asset ids; every other import publishes today's message. The hand-off commits
 * before it publishes and reports failure instead of today's unconditional success.
 */
class IAGatewayImportEnvelopeTest {
    private static final String CTX = "context0";
    private static final String BASE = "https://example.test";

    private static Annotation marked(String id, String importId) {
        JSONObject params = new JSONObject().put("x", 1).put("y", 1).put("width", 5).put("height", 5);
        if (importId != null)
            params.put(Annotation.FEATURE_PARAM_IMPORTED_BOX, new JSONObject().put("importTaskId", importId));
        Annotation a = new Annotation(null, new Feature(new FeatureType("org.ecocean.boundingBox"), params), "body");
        a.setId(id);
        return a;
    }

    private static Encounter encounterWith(Annotation... anns) {
        Encounter enc = mock(Encounter.class);
        when(enc.getAnnotations()).thenReturn(new ArrayList<Annotation>(Arrays.asList(anns)));
        return enc;
    }

    private static ImportTask unboxedTask(String... assetIds) {
        ImportTask itask = mock(ImportTask.class);
        when(itask.getId()).thenReturn("job");
        when(itask.annotationsImported()).thenReturn(false);
        List<MediaAsset> assets = new ArrayList<MediaAsset>();
        for (String id : assetIds) {
            MediaAsset ma = mock(MediaAsset.class);
            when(ma.getId()).thenReturn(id);
            assets.add(ma);
        }
        when(itask.getMediaAssets()).thenReturn(assets);
        return itask;
    }

    private static ImportTask boxedTask(Encounter... encs) {
        ImportTask itask = mock(ImportTask.class);
        when(itask.getId()).thenReturn("job");
        when(itask.annotationsImported()).thenReturn(true);
        when(itask.getMediaAssets()).thenReturn(new ArrayList<MediaAsset>());
        when(itask.getEncounters()).thenReturn(new ArrayList<Encounter>(Arrays.asList(encs)));
        return itask;
    }

    private static JSONObject data() {
        return new JSONObject().put("taskParameters",
            new JSONObject().put("importTaskId", "job").put("skipIdent", false))
            .put("bulkImport", new JSONObject());
    }

    /** A mocked Shepherd whose persistence manager hands objects back and whose checked commit answers as given. */
    private static final class Harness {
        final Shepherd sh = mock(Shepherd.class);
        final PersistenceManager pm = mock(PersistenceManager.class);
        final AtomicReference<String> published = new AtomicReference<String>();
        Harness(ImportTask itask, Boolean... commitResults) {
            when(sh.getPM()).thenReturn(pm);
            when(sh.getImportTask("job")).thenReturn(itask);
            when(pm.makePersistent(any())).thenAnswer(inv -> inv.getArgument(0));
            if (commitResults.length == 1) when(sh.commitDBTransactionWithStatus()).thenReturn(commitResults[0]);
            else when(sh.commitDBTransactionWithStatus()).thenReturn(commitResults[0], Arrays.copyOfRange(commitResults, 1, commitResults.length));
        }
    }

    // ---- buildImportEnvelope -------------------------------------------------------------

    @Test void unboxedImportProducesMediaAssetIdsOnly() {
        JSONObject env = IAGateway.buildImportEnvelope(unboxedTask("42", "43"));
        assertEquals("[\"42\",\"43\"]", env.getJSONArray("mediaAssetIds").toString());
        assertFalse(env.has("annotationIds"));
        assertFalse(env.has("annotationMode"));
    }

    @Test void boxedImportProducesOnlyItsOwnImportedBoxes() {
        Annotation a = marked("A", "job"), b = marked("B", "imp-9"), c = marked("C", null);
        JSONObject env = IAGateway.buildImportEnvelope(boxedTask(encounterWith(a, b, c), encounterWith(a)));
        assertEquals("[\"A\"]", env.getJSONArray("annotationIds").toString());
        assertEquals(IAGateway.ANNOTATION_MODE_EXTRACT, env.getString("annotationMode"));
        assertFalse(env.has("mediaAssetIds"));
    }

    @Test void boxedImportToleratesNullEncounterLists() {
        ImportTask itask = mock(ImportTask.class);
        when(itask.getId()).thenReturn("job");
        when(itask.annotationsImported()).thenReturn(true);
        when(itask.getEncounters()).thenReturn(null);
        JSONObject env = IAGateway.buildImportEnvelope(itask);
        assertEquals(0, env.getJSONArray("annotationIds").length());
    }

    // ---- handleBulkImport, successful paths ----------------------------------------------

    @Test void successfulUnboxedHandoffPublishesTodaysMessage() throws Exception { // (regression)
        Harness h = new Harness(unboxedTask("42", "43"), true);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                h.published.set(call.getArgument(1, String.class)); return true; });
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertTrue(res.getBoolean("success"));
            assertEquals(1, res.getInt("queuedCount"));
            JSONObject msg = new JSONObject(h.published.get());
            assertEquals("[\"42\",\"43\"]", msg.getJSONArray("mediaAssetIds").toString());
            assertTrue(msg.getBoolean("v2"));
            assertFalse(msg.has("annotationMode"));
            assertEquals(CTX, msg.getString("__context"));
            ArgumentCaptor<Task> root = ArgumentCaptor.forClass(Task.class);
            ImportTask itask = h.sh.getImportTask("job");
            verify(itask).setIATask(root.capture());
            Task child = root.getValue().getChildren().get(0);
            assertEquals(child.getId(), msg.getString("taskId"));
            assertEquals(h.published.get(), child.getQueueResumeMessage());
            verify(h.sh, times(1)).commitDBTransactionWithStatus();
            verify(h.sh, never()).storeNewTask(any());
        }
    }

    @Test void unboxedInputKeepsLegacyAnnotationFields() throws Exception { // (regression)
        Harness h = new Harness(unboxedTask("42"), true);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                h.published.set(call.getArgument(1, String.class)); return true; });
            JSONObject in = data().put("annotationIds", new JSONArray().put("z"));
            // annotationIds dominate laneFor: the legacy clone still carries them, so this
            // message lands on the IA queue, exactly as today
            gateway.when(() -> IAGateway.addToQueue(eq(CTX), anyString())).thenAnswer(call -> {
                h.published.set(call.getArgument(1, String.class)); return true; });
            JSONObject res = IAGateway.handleBulkImport(in, new JSONObject(), h.sh, CTX, BASE);
            assertTrue(res.getBoolean("success"));
            JSONObject msg = new JSONObject(h.published.get());
            assertEquals("[\"z\"]", msg.getJSONArray("annotationIds").toString());
            assertEquals("[\"42\"]", msg.getJSONArray("mediaAssetIds").toString());
        }
    }

    @Test void boxedImportPublishesTheModedEnvelopeOnTheDetectionLane() throws Exception {
        Annotation a = marked("A", "job");
        Harness h = new Harness(boxedTask(encounterWith(a)), true);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                h.published.set(call.getArgument(1, String.class)); return true; });
            JSONObject in = data().put("mediaAssetIds", new JSONArray().put("9"));
            JSONObject res = IAGateway.handleBulkImport(in, new JSONObject(), h.sh, CTX, BASE);
            assertTrue(res.getBoolean("success"));
            JSONObject msg = new JSONObject(h.published.get());
            assertFalse(msg.has("mediaAssetIds"), "a boxed import drops media ids from the clone");
            assertEquals("[\"A\"]", msg.getJSONArray("annotationIds").toString());
            assertEquals(IAGateway.ANNOTATION_MODE_EXTRACT, msg.getString("annotationMode"));
            assertEquals(IAGateway.LANE_DETECTION, IAGateway.laneFor(msg));
            ArgumentCaptor<Task> root = ArgumentCaptor.forClass(Task.class);
            verify(h.sh.getImportTask("job")).setIATask(root.capture());
            assertEquals(msg.getString("taskId"), root.getValue().getChildren().get(0).getId());
        }
    }

    @Test void boxedImportWithNoSurvivingBoxesCompletesTheRoot() throws Exception {
        Harness h = new Harness(boxedTask(encounterWith(marked("C", null))), true);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertTrue(res.getBoolean("success"));
            assertEquals(0, res.getInt("queuedCount"));
            gateway.verify(() -> IAGateway.addToDetectionQueue(anyString(), anyString()), never());
            ArgumentCaptor<Task> root = ArgumentCaptor.forClass(Task.class);
            ImportTask itask = h.sh.getImportTask("job");
            verify(itask).setIATask(root.capture());
            assertEquals("completed", root.getValue().getStoredStatus());
            verify(itask).addLog(contains("no imported boxes survived"));
            verify(h.sh, times(1)).commitDBTransactionWithStatus();
        }
    }

    // ---- handleBulkImport, failure paths -------------------------------------------------

    @Test void failedCommitReturnsFalseAndPublishesNothing() throws Exception {
        Harness h = new Harness(unboxedTask("42"), false, true);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertFalse(res.getBoolean("success"));
            assertEquals("HANDOFF_COMMIT", res.getString("error"));
            gateway.verify(() -> IAGateway.addToDetectionQueue(anyString(), anyString()), never());
            verify(h.sh, times(2)).commitDBTransactionWithStatus();
            verify(h.sh.getImportTask("job")).addLog(contains("HANDOFF_COMMIT"));
        }
    }

    @Test void throwingPersistIsRecovered() throws Exception {
        Harness h = new Harness(unboxedTask("42"), true);
        when(h.pm.makePersistent(any())).thenThrow(new RuntimeException("dn")).thenAnswer(inv -> inv.getArgument(0));
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertFalse(res.getBoolean("success"));
            assertEquals("HANDOFF_COMMIT", res.getString("error"));
            gateway.verify(() -> IAGateway.addToDetectionQueue(anyString(), anyString()), never());
        }
    }

    @Test void failedPublishMarksRootErrorWithAnErrorChildHoldingTheBoxes() throws Exception {
        Annotation a = marked("A", "job");
        Harness h = new Harness(boxedTask(encounterWith(a)), true);
        when(h.sh.getAnnotation("A")).thenReturn(a);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenReturn(false);
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertFalse(res.getBoolean("success"));
            assertEquals("HANDOFF_PUBLISH", res.getString("error"));
            ArgumentCaptor<Task> root = ArgumentCaptor.forClass(Task.class);
            ImportTask itask = h.sh.getImportTask("job");
            verify(itask).setIATask(root.capture());
            assertEquals("error", root.getValue().getStoredStatus());
            List<Task> kids = root.getValue().getChildren();
            Task errorChild = kids.get(kids.size() - 1);
            assertEquals("error", errorChild.getStoredStatus());
            assertNotNull(errorChild.getObjectAnnotations());
            assertEquals("A", errorChild.getObjectAnnotations().get(0).getId());
            verify(itask).addLog(contains("HANDOFF_PUBLISH"));
            verify(h.sh, times(2)).commitDBTransactionWithStatus();
        }
    }

    @Test void throwingPublishIsRecovered() throws Exception {
        Annotation a = marked("A", "job");
        Harness h = new Harness(boxedTask(encounterWith(a)), true);
        when(h.sh.getAnnotation("A")).thenReturn(a);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenThrow(new IOException("disk"));
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertFalse(res.getBoolean("success"));
            assertEquals("HANDOFF_PUBLISH", res.getString("error"));
            ArgumentCaptor<Task> root = ArgumentCaptor.forClass(Task.class);
            verify(h.sh.getImportTask("job")).setIATask(root.capture());
            assertEquals("error", root.getValue().getStoredStatus());
            List<Task> kids = root.getValue().getChildren();
            assertEquals("A", kids.get(kids.size() - 1).getObjectAnnotations().get(0).getId());
        }
    }

    @Test void failedRecoveryCommitStillReportsFailure() throws Exception {
        Harness h = new Harness(unboxedTask("42"), false, false);
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertFalse(res.getBoolean("success"));
            assertEquals("HANDOFF_COMMIT", res.getString("error"));
        }
    }

    @Test void throwingRecoveryStillReportsFailure() throws Exception {
        Harness h = new Harness(unboxedTask("42"), false);
        when(h.pm.makePersistent(any())).thenAnswer(inv -> inv.getArgument(0)).thenAnswer(inv -> inv.getArgument(0))
            .thenAnswer(inv -> inv.getArgument(0)).thenThrow(new RuntimeException("dn in recovery"));
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            JSONObject res = IAGateway.handleBulkImport(data(), new JSONObject(), h.sh, CTX, BASE);
            assertFalse(res.getBoolean("success"));
        }
    }
}
