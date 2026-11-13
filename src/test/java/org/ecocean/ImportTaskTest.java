package org.ecocean;

import org.ecocean.ia.Task;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
/*
   import static org.mockito.Mockito.doNothing;
   import static org.mockito.Mockito.doThrow;
   import static org.mockito.Mockito.mock;
   import static org.mockito.Mockito.mockConstruction;
   import static org.mockito.Mockito.mockStatic;
   import static org.mockito.Mockito.verify;
 */
import static org.mockito.Mockito.when;

import java.util.Calendar;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

// these are just covering the bulk-import-related patches for now
class ImportTaskTest {
    @Test void testSourceName() {
        ImportTask itask = new ImportTask();

        // some of the empty/null cases
        assertNull(itask.getSourceName());
        itask.setParameters(new JSONObject());
        assertNull(itask.getSourceName());
        // now the simplest (non-legacy) case
        JSONObject pparam = new JSONObject();
        pparam.put("sourceName", "test");
        itask.setPassedParameters(pparam);
        assertEquals(itask.getSourceName(), "test");
        pparam.remove("sourceName");
        JSONArray farr = new JSONArray();
        farr.put("test2");
        pparam.put("originalFilename", farr);
        itask.setPassedParameters(pparam);
        assertEquals(itask.getSourceName(), "test2");
    }

    @Test void testLegacy() {
        ImportTask itask = new ImportTask();

        // some of the empty/null cases
        assertTrue(itask.isLegacy());
        itask.setParameters(new JSONObject());
        assertTrue(itask.isLegacy());
        // now the simplest (non-legacy) case
        JSONObject pparam = new JSONObject();
        pparam.put("bulkImportId", "test");
        itask.setPassedParameters(pparam);
        assertFalse(itask.isLegacy());
    }

    // TODO this can use much improvement once we believe it works at all
    @Test void testIaSummary() {
        ImportTask itask = mock(ImportTask.class);

        // this lets us use the real thing to test it
        // (which calls many of the things we mock here)
        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();

        MediaAsset mockMA = mock(MediaAsset.class);
        when(mockMA.numAnnotations()).thenReturn(9);
        when(mockMA.getDetectionStatus()).thenReturn("complete");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(mockMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);

        Task iaTask = mock(Task.class);
        when(itask.getIATask()).thenReturn(iaTask);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        // System.out.println("################################ " + sum.toString(10));
        assertEquals(sum.optInt("numberAnnotations"), 9);
        assertEquals(sum.optInt("numberMediaAssets"), 1);
        assertEquals(sum.optInt("detectionNumberComplete"), 1);
        assertEquals(sum.optInt("detectionNumberError"), 0);
    }

    // builds a started, non-legacy task over the given assets
    private ImportTask summaryTask(MediaAsset... assets) {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (MediaAsset ma : assets) mas.add(ma);
        when(itask.getMediaAssets()).thenReturn(mas);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);
        return itask;
    }

    private MediaAsset asset(String detectionStatus, Boolean validForIA) {
        MediaAsset ma = mock(MediaAsset.class);

        when(ma.getDetectionStatus()).thenReturn(detectionStatus);
        when(ma.isValidImageForIA()).thenReturn(validForIA);
        return ma;
    }

    // validateSourceImage() decodes the whole image and the GET never persists it;
    // a terminal asset's validity cannot change detection progress, so skip it.
    // this is what made an 8000-encounter import's status GET never return.
    @Test void testIaSummary_terminalUnknownValidityNotDecoded() {
        MediaAsset ma = asset("complete", null);
        JSONObject sum = summaryTask(ma).iaSummaryJson(mock(Shepherd.class));

        verify(ma, never()).validateSourceImage();
        assertEquals(0, sum.optInt("numberMediaAssetValidIA"));
        assertEquals(1, sum.optInt("numberMediaAssetValidIAUnknown"));
        assertEquals("complete", sum.optString("detectionStatus"));
    }

    // still in flight with unknown validity: validate (as before), and a valid
    // result keeps detection "sent" even when identification was skipped
    @Test void testIaSummary_inFlightUnknownValidityIsValidated() {
        MediaAsset ma = mock(MediaAsset.class);

        when(ma.getDetectionStatus()).thenReturn(null);
        when(ma.isValidImageForIA()).thenReturn(null, true);
        ImportTask itask = summaryTask(ma);
        when(itask.skippedIdentification()).thenReturn(true);
        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        verify(ma, times(1)).validateSourceImage();
        assertEquals(1, sum.optInt("numberMediaAssetValidIA"));
        assertEquals("sent", sum.optString("detectionStatus"));
        assertEquals("skipped", sum.optString("identificationStatus"));
        assertFalse(sum.optBoolean("pipelineComplete", false));
        assertEquals(0.0, sum.optDouble("detectionPercent"), 0.0001);
    }

    // in flight, validation says invalid: not outstanding, so detection completes
    @Test void testIaSummary_inFlightValidatedInvalidNotOutstanding() {
        MediaAsset ma = mock(MediaAsset.class);

        when(ma.getDetectionStatus()).thenReturn(null);
        when(ma.isValidImageForIA()).thenReturn(null, false);
        JSONObject sum = summaryTask(ma).iaSummaryJson(mock(Shepherd.class));
        assertEquals(0, sum.optInt("numberMediaAssetValidIAUnknown"));
        assertEquals("complete", sum.optString("detectionStatus"));
        assertEquals(1.0, sum.optDouble("detectionPercent"), 0.0001);
    }

    @Test void testIaSummary_noAssets() {
        ImportTask itask = summaryTask();

        when(itask.getMediaAssets()).thenReturn(null);
        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(0, sum.optInt("numberMediaAssets"));
        assertEquals("complete", sum.optString("detectionStatus"));
    }

    // non-LOCAL stores leave the flag null after validation; unknown must not be
    // treated as ineligible, or detection would report complete too early
    @Test void testIaSummary_inFlightStillUnknownIsOutstanding() {
        MediaAsset ma = asset(null, null);
        ImportTask itask = summaryTask(ma);

        when(itask.skippedIdentification()).thenReturn(true);
        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(1, sum.optInt("numberMediaAssetValidIAUnknown"));
        assertEquals("sent", sum.optString("detectionStatus"));
        assertFalse(sum.optBoolean("pipelineComplete", false));
    }

    // never sent because the image is invalid: must not strand the import
    @Test void testIaSummary_invalidNeverSentDoesNotStrand() {
        JSONObject sum = summaryTask(asset("complete", true),
            asset(null, false)).iaSummaryJson(mock(Shepherd.class));

        assertEquals("complete", sum.optString("detectionStatus"));
        assertEquals(1, sum.optInt("numberMediaAssetValidIA"));
    }

    // old totals comparison (terminal >= valid) let a terminal ineligible asset
    // mask a valid one still in flight
    @Test void testIaSummary_terminalIneligibleDoesNotMaskInFlight() {
        JSONObject sum = summaryTask(asset("error", false),
            asset(null, true)).iaSummaryJson(mock(Shepherd.class));

        assertEquals(1, sum.optInt("detectionNumberComplete"));
        assertEquals("sent", sum.optString("detectionStatus"));
    }

    @Test void testGetMediaAssetsDedupesKeepingOrder() {
        MediaAsset ma1 = mock(MediaAsset.class);
        MediaAsset ma2 = mock(MediaAsset.class);
        Encounter enc1 = mock(Encounter.class);
        Encounter enc2 = mock(Encounter.class);

        when(enc1.getMedia()).thenReturn(new ArrayList<MediaAsset>(List.of(ma1, ma2)));
        when(enc2.getMedia()).thenReturn(new ArrayList<MediaAsset>(List.of(ma2, ma1)));
        ImportTask itask = new ImportTask();
        itask.setEncounters(new ArrayList<Encounter>(List.of(enc1, enc2)));
        assertEquals(List.of(ma1, ma2), itask.getMediaAssets());
    }

    // pending-species is a taxonomy-pending terminal status set by
    // MlServiceProcessor.loadDetectionContext when configs are
    // unavailable for the upload's taxonomy. Pre-fix, this status was
    // not counted as detection-terminal, so the frontend polled forever.
    // Also asserts the aggregate detectionStatus == "complete" so the
    // polling-stop signal is verified end-to-end (Codex C2 round-1).
    @Test void testIaSummary_pendingSpecies() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset mockMA = mock(MediaAsset.class);
        when(mockMA.getDetectionStatus()).thenReturn("pending-species");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(mockMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        // Required so iaSummaryJson enters the non-legacy aggregate
        // status branch — otherwise detectionStatus is never set and
        // the frontend's polling whitelist would keep polling.
        when(itask.iaTaskStarted()).thenReturn(true);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optInt("detectionNumberComplete"), 1);
        assertEquals(sum.optInt("detectionNumberError"), 0);
        assertEquals(sum.optString("detectionStatus"), "complete");
    }

    // STATUS_ERROR is the terminal status set by markDetectionFailure
    // on non-retryable detection failures. Counted as terminal AND
    // surfaced separately via detectionNumberError so the UI can
    // distinguish "complete with errors" from "complete cleanly".
    @Test void testIaSummary_error() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset mockMA = mock(MediaAsset.class);
        when(mockMA.getDetectionStatus()).thenReturn("error");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(mockMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optInt("detectionNumberComplete"), 1);
        assertEquals(sum.optInt("detectionNumberError"), 1);
        // Aggregate still "complete" — the error count is a separate
        // signal, so the frontend's polling whitelist still terminates.
        assertEquals(sum.optString("detectionStatus"), "complete");
    }

    // Mixed bag: one complete + one errored. Both count as terminal;
    // detectionNumberError tracks only the failure.
    @Test void testIaSummary_mixedCompleteAndError() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset okMA = mock(MediaAsset.class);
        when(okMA.getDetectionStatus()).thenReturn("complete-mlservice");
        MediaAsset errMA = mock(MediaAsset.class);
        when(errMA.getDetectionStatus()).thenReturn("error");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(okMA);
        someMAs.add(errMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optInt("detectionNumberComplete"), 2);
        assertEquals(sum.optInt("detectionNumberError"), 1);
        assertEquals(sum.optString("detectionStatus"), "complete");
    }

    // Identification-side mirror of testIaSummary_error: a match task
    // landing in status "error" must count as identification-terminal,
    // otherwise the import strands at identificationStatus="sent" and
    // the frontend polls forever even when nothing's running.
    @Test void testIaSummary_identificationError() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset mockMA = mock(MediaAsset.class);
        when(mockMA.getDetectionStatus()).thenReturn("complete");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(mockMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);
        when(itask.iaTaskRequestedIdentification()).thenReturn(true);
        JSONObject statsAnn = new JSONObject();
        statsAnn.put("numLatestTasks", 1);
        statsAnn.put("numLatestTask_error", 1);
        when(itask.statsAnnotations(any(Shepherd.class))).thenReturn(statsAnn);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optInt("identificationNumberComplete"), 1);
        assertEquals(sum.optInt("identificationNumberError"), 1);
        // Aggregate still "complete" — error count is the separate UI
        // signal; polling whitelist still terminates.
        assertEquals(sum.optString("identificationStatus"), "complete");
    }

    // Mixed: 3 match tasks completed, 1 errored. All 4 are terminal, so
    // the aggregate should flip to "complete" and identificationNumberError
    // tracks just the failure.
    @Test void testIaSummary_identificationMixedCompleteAndError() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset mockMA = mock(MediaAsset.class);
        when(mockMA.getDetectionStatus()).thenReturn("complete");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(mockMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);
        when(itask.iaTaskRequestedIdentification()).thenReturn(true);
        JSONObject statsAnn = new JSONObject();
        statsAnn.put("numLatestTasks", 4);
        statsAnn.put("numLatestTask_completed", 3);
        statsAnn.put("numLatestTask_error", 1);
        when(itask.statsAnnotations(any(Shepherd.class))).thenReturn(statsAnn);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optInt("identificationNumberComplete"), 4);
        assertEquals(sum.optInt("identificationNumberError"), 1);
        assertEquals(sum.optString("identificationStatus"), "complete");
    }

    // Negative case: 1 completed + 1 errored out of 4 — still in flight
    // (2 pending). Aggregate must stay "sent" so the frontend keeps
    // polling. Without including error in the terminal count, the
    // pre-fix behavior would still be "sent" via a different path —
    // but with the fix, we must explicitly check that error doesn't
    // OVER-count and prematurely flip to "complete".
    @Test void testIaSummary_identificationInProgressDoesntOvercount() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset mockMA = mock(MediaAsset.class);
        when(mockMA.getDetectionStatus()).thenReturn("complete");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(mockMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);
        when(itask.iaTaskRequestedIdentification()).thenReturn(true);
        JSONObject statsAnn = new JSONObject();
        statsAnn.put("numLatestTasks", 4);
        statsAnn.put("numLatestTask_completed", 1);
        statsAnn.put("numLatestTask_error", 1);
        when(itask.statsAnnotations(any(Shepherd.class))).thenReturn(statsAnn);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optInt("identificationNumberComplete"), 2);
        assertEquals(sum.optInt("identificationNumberError"), 1);
        assertEquals(sum.optString("identificationStatus"), "sent");
    }

    // Locks the polling contract for "Skip identification": when the
    // bulk-import user checks Skip ID, MlServiceProcessor short-circuits
    // before any match Task is created, so iaTaskRequestedIdentification
    // returns false AND skippedIdentification returns true.
    // iaSummaryJson must then emit identificationStatus="skipped"
    // (not in the frontend in-flight whitelist, so polling stops) AND
    // refrain from setting identificationNumberComplete / numTotal —
    // identification didn't run, so there's nothing to count.
    @Test void testIaSummary_skipIdentificationYieldsSkippedStatus() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset mockMA = mock(MediaAsset.class);
        when(mockMA.getDetectionStatus()).thenReturn("complete-mlservice");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(mockMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);
        // skipIdent path: iaTaskRequestedIdentification is FALSE (Skip
        // ID was checked), and skippedIdentification stamps the
        // "skipped" status explicitly.
        when(itask.iaTaskRequestedIdentification()).thenReturn(false);
        when(itask.skippedIdentification()).thenReturn(true);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optString("detectionStatus"), "complete");
        assertEquals(sum.optString("identificationStatus"), "skipped");
        // pipelineComplete must be true when detection complete + ident skipped
        // (otherwise frontend polling would not stop).
        assertEquals(true, sum.optBoolean("pipelineComplete"));
    }

    // Legacy flavor (getIATask() == null) hits the second `>=`
    // predicate. The original `==` would have stranded the import at
    // "sent" if any terminal-but-non-IA-eligible MA showed up.
    @Test void testIaSummary_legacyError() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset errMA = mock(MediaAsset.class);
        when(errMA.getDetectionStatus()).thenReturn("error");
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(errMA);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(null);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals(sum.optInt("detectionNumberComplete"), 1);
        assertEquals(sum.optInt("detectionNumberError"), 1);
        assertEquals(sum.optString("detectionStatus"), "complete");
    }
    // ---- bulk-import boxes PR A, task 2: annotationsImported flag ----

    @Test void iaSummaryForSuppliedBoxesShowsDetectionDone() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset boxed = mock(MediaAsset.class);
        MediaAsset unboxed = mock(MediaAsset.class);
        when(boxed.getDetectionStatus()).thenReturn("complete");
        // both photos are IA-valid, so the plain calculation would give 1 of 2 = 0.5
        when(boxed.isValidImageForIA()).thenReturn(true);
        when(unboxed.isValidImageForIA()).thenReturn(true);
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(boxed);
        someMAs.add(unboxed);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);
        when(itask.annotationsImported()).thenReturn(true);
        when(itask.skippedDetection()).thenReturn(true);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals("skipped", sum.optString("detectionStatus"));
        assertTrue(sum.optBoolean("annotationsImported"));
        assertEquals(1.0, sum.optDouble("detectionPercent"), 0.0);
    }

    @Test void iaSummaryForSuppliedBoxesImportOnlySkipsIdentification() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset boxed = mock(MediaAsset.class);
        when(boxed.isValidImageForIA()).thenReturn(true);
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(boxed);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(mock(Task.class));
        when(itask.iaTaskStarted()).thenReturn(true);
        when(itask.annotationsImported()).thenReturn(true);
        when(itask.skippedDetection()).thenReturn(true);
        when(itask.skippedIdentification()).thenReturn(true);
        // its one box has been extracted
        when(itask.statsAnnotations(any(Shepherd.class))).thenReturn(
            new JSONObject().put("numLatestTasks", 1).put("numLatestTask_completed", 1));

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals("skipped", sum.optString("detectionStatus"));
        assertEquals("skipped", sum.optString("identificationStatus"));
        assertTrue(sum.optBoolean("annotationsImported"));
        assertEquals(1.0, sum.optDouble("detectionPercent"), 0.0);
        assertTrue(sum.optBoolean("pipelineComplete"));
    }

    // ---- a boxed import is finished when its boxes are, not when its labels say "skipped" ----

    /** A started boxed import with one IA-valid photo; stats and root as given. */
    static ImportTask boxedSummaryTask(boolean skipIdent, JSONObject stats, Task root) {
        ImportTask itask = mock(ImportTask.class);
        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        MediaAsset boxed = mock(MediaAsset.class);
        when(boxed.isValidImageForIA()).thenReturn(true);
        List<MediaAsset> someMAs = new ArrayList<MediaAsset>();
        someMAs.add(boxed);
        when(itask.getMediaAssets()).thenReturn(someMAs);
        when(itask.getIATask()).thenReturn(root);
        when(itask.iaTaskStarted()).thenReturn(true);
        when(itask.annotationsImported()).thenReturn(true);
        when(itask.skippedDetection()).thenReturn(true);
        when(itask.skippedIdentification()).thenReturn(skipIdent);
        when(itask.iaTaskRequestedIdentification()).thenReturn(!skipIdent);
        when(itask.statsAnnotations(any(Shepherd.class))).thenReturn(stats);
        return itask;
    }

    static Task rootWithStatus(String status) {
        Task root = new Task();
        if (status != null) root.setStatus(status);
        return root;
    }

    @Test void boxedImportOnlyIsNotCompleteWhileBoxesAreStillExtracting() {
        JSONObject stats = new JSONObject().put("numLatestTasks", 3).put("numLatestTask_completed", 1)
            .put("numLatestTask_initiated", 1).put("numLatestTask_processing", 1);
        JSONObject sum = boxedSummaryTask(true, stats, rootWithStatus(null)).iaSummaryJson(mock(Shepherd.class));
        assertEquals("skipped", sum.optString("detectionStatus"));
        assertEquals("skipped", sum.optString("identificationStatus"));
        assertFalse("two boxes have not been extracted yet", sum.optBoolean("pipelineComplete"));
        assertTrue(sum.optBoolean("pipelineStarted"));
    }

    @Test void boxedImportOnlyIsCompleteOnceEveryBoxEnded() {
        // an errored or dropped box has ended too
        JSONObject stats = new JSONObject().put("numLatestTasks", 3).put("numLatestTask_completed", 1)
            .put("numLatestTask_error", 1).put("numLatestTask_dropped-stale", 1);
        JSONObject sum = boxedSummaryTask(true, stats, rootWithStatus(null)).iaSummaryJson(mock(Shepherd.class));
        assertTrue(sum.optBoolean("pipelineComplete"));
    }

    @Test void boxedImportWithNoSurvivingBoxFinishesWithItsRoot() {
        // identification was requested, no box survived: the completed root ends the pipeline
        JSONObject none = new JSONObject().put("numLatestTasks", 0);
        JSONObject sum = boxedSummaryTask(false, none, rootWithStatus("completed")).iaSummaryJson(mock(Shepherd.class));
        assertEquals("complete", sum.optString("identificationStatus"));
        assertEquals(1.0, sum.optDouble("identificationPercent"), 0.0);
        assertTrue(sum.optBoolean("pipelineComplete"));
        // an error root ends it as well
        sum = boxedSummaryTask(false, none, rootWithStatus("error")).iaSummaryJson(mock(Shepherd.class));
        assertTrue(sum.optBoolean("pipelineComplete"));
        // a root that has not ended does not
        sum = boxedSummaryTask(false, none, rootWithStatus(null)).iaSummaryJson(mock(Shepherd.class));
        assertEquals("identification not started", sum.optString("identificationStatus"));
        assertFalse(sum.optBoolean("pipelineComplete"));
    }

    static Task child(Task parent, String status) {
        Task kid = rootWithStatus(status);
        parent.addChild(kid);
        return kid;
    }

    @Test void boxedImportWithNoBoxFinishesWhenEveryTaskBelowItsRootEnded() {   // review: UrbanFercec
        JSONObject none = new JSONObject().put("numLatestTasks", 0);
        for (boolean skipIdent : new boolean[] { true, false }) {
            // the submissions hand-off: the request found no box and ended in error; the root has no status
            Task root = rootWithStatus(null);
            child(root, "error");
            JSONObject sum = boxedSummaryTask(skipIdent, none, root).iaSummaryJson(mock(Shepherd.class));
            assertTrue("request ended in error, skipIdent=" + skipIdent, sum.optBoolean("pipelineComplete"));
            if (!skipIdent) assertEquals("complete", sum.optString("identificationStatus"));
            // every box was deleted before intake: the request ended as completed
            root = rootWithStatus(null);
            child(root, "completed");
            sum = boxedSummaryTask(skipIdent, none, root).iaSummaryJson(mock(Shepherd.class));
            assertTrue("request completed, skipIdent=" + skipIdent, sum.optBoolean("pipelineComplete"));
        }
    }

    @Test void boxedImportWithNoBoxWaitsForEveryTaskBelowItsRoot() {
        JSONObject none = new JSONObject().put("numLatestTasks", 0);
        // a sibling still pending
        Task root = rootWithStatus(null);
        child(root, "completed");
        child(root, "initiated");
        assertFalse("a pending sibling", boxedSummaryTask(false, none, root).iaSummaryJson(mock(Shepherd.class))
            .optBoolean("pipelineComplete"));
        // an ended task whose own child is still running: the walk does not stop at an ended task
        root = rootWithStatus(null);
        child(child(root, "completed"), "processing");
        assertFalse("a running grandchild", boxedSummaryTask(false, none, root).iaSummaryJson(mock(Shepherd.class))
            .optBoolean("pipelineComplete"));
        // a request that has not ended, even with an ended task below it (conservative on purpose)
        root = rootWithStatus(null);
        child(child(root, null), "error");
        assertFalse("a request without a status", boxedSummaryTask(false, none, root).iaSummaryJson(mock(Shepherd.class))
            .optBoolean("pipelineComplete"));
        // a childless root without a status
        assertFalse("nothing below the root", boxedSummaryTask(false, none, rootWithStatus(null))
            .iaSummaryJson(mock(Shepherd.class)).optBoolean("pipelineComplete"));
        // boxes still counted: the walk is only for the no-box case (Codex: skipIdent=true too, where
        // identification is "skipped" and only the box count can hold the import back)
        JSONObject running = new JSONObject().put("numLatestTasks", 1).put("numLatestTask_processing", 1);
        for (boolean skipIdent : new boolean[] { true, false }) {
            Task spied = org.mockito.Mockito.spy(rootWithStatus(null));
            child(spied, "completed");
            org.mockito.Mockito.clearInvocations(spied);
            assertFalse("a box still running, skipIdent=" + skipIdent, boxedSummaryTask(skipIdent, running, spied)
                .iaSummaryJson(mock(Shepherd.class)).optBoolean("pipelineComplete"));
            org.mockito.Mockito.verify(spied, org.mockito.Mockito.never()).getChildren();
        }
    }

    @Test void everyDescendantEndedSurvivesACycle() {
        Task root = rootWithStatus(null);
        Task a = child(root, "completed");
        a.addChild(root);   // malformed: the root under its own child; the walk still ends
        assertTrue(boxedSummaryTask(false, new JSONObject().put("numLatestTasks", 0), root)
            .iaSummaryJson(mock(Shepherd.class)).optBoolean("pipelineComplete"));
    }

    @Test void boxedImportWithIdentificationFollowsItsBoxes() {
        JSONObject running = new JSONObject().put("numLatestTasks", 2).put("numLatestTask_completed", 1)
            .put("numLatestTask_processing", 1);
        JSONObject sum = boxedSummaryTask(false, running, rootWithStatus(null)).iaSummaryJson(mock(Shepherd.class));
        assertEquals("sent", sum.optString("identificationStatus"));
        assertFalse(sum.optBoolean("pipelineComplete"));
        // every way a box can end counts, here one of each
        JSONObject mixed = new JSONObject().put("numLatestTasks", 3).put("numLatestTask_completed", 1)
            .put("numLatestTask_error", 1).put("numLatestTask_dropped-stale", 1);
        sum = boxedSummaryTask(false, mixed, rootWithStatus(null)).iaSummaryJson(mock(Shepherd.class));
        assertEquals("complete", sum.optString("identificationStatus"));
        assertTrue(sum.optBoolean("pipelineComplete"));
        JSONObject done = new JSONObject().put("numLatestTasks", 2).put("numLatestTask_completed", 2);
        sum = boxedSummaryTask(false, done, rootWithStatus(null)).iaSummaryJson(mock(Shepherd.class));
        assertEquals("complete", sum.optString("identificationStatus"));
        assertTrue(sum.optBoolean("pipelineComplete"));
    }

    @Test void importWithoutSuppliedBoxesKeepsItsCompletionRule() { // (regression)
        ImportTask itask = mock(ImportTask.class);
        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        when(itask.getMediaAssets()).thenReturn(new ArrayList<MediaAsset>());
        when(itask.getIATask()).thenReturn(rootWithStatus(null));
        when(itask.iaTaskStarted()).thenReturn(true);
        when(itask.skippedDetection()).thenReturn(true);
        when(itask.skippedIdentification()).thenReturn(true);
        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertTrue("skipped + skipped is complete when no boxes were supplied", sum.optBoolean("pipelineComplete"));
    }

    @Test void iaSummaryWithoutSuppliedBoxesOmitsTheFlag() {
        ImportTask itask = mock(ImportTask.class);

        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
        when(itask.getMediaAssets()).thenReturn(new ArrayList<MediaAsset>());
        when(itask.skippedDetection()).thenReturn(true);

        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
        assertEquals("skipped", sum.optString("detectionStatus"));
        assertFalse(sum.has("annotationsImported"));
    }

    @Test void annotationsImportedSkipsDetectionButNotIdentification() {
        ImportTask t = new ImportTask();
        t.setPassedParameters(new JSONObject().put(ImportTask.PARAM_ANNOTATIONS_IMPORTED, true));
        Task ia = new Task();
        ia.setParameters(new JSONObject().put("skipIdent", false));
        t.setIATask(ia);
        assertTrue(t.annotationsImported());
        assertTrue(t.skippedDetection());
        assertFalse(t.skippedIdentification());
    }

    @Test void annotationsImportedWithSkipIdentSkipsIdentification() { // (regression)
        ImportTask t = new ImportTask();
        t.setPassedParameters(new JSONObject().put(ImportTask.PARAM_ANNOTATIONS_IMPORTED, true)
            .put("skipDetection", true));
        Task ia = new Task();
        ia.setParameters(new JSONObject().put("skipIdent", true));
        t.setIATask(ia);
        assertTrue(t.skippedDetection());
        assertTrue(t.skippedIdentification());
    }

    @Test void plainSkipDetectionStillImpliesSkippedIdentification() { // (regression)
        ImportTask t = new ImportTask();
        t.setPassedParameters(new JSONObject().put("skipDetection", true));
        assertTrue(t.skippedDetection());
        assertTrue(t.skippedIdentification());
    }

    @Test void nullPassedParametersMeanNothingImported() {
        ImportTask t = new ImportTask();
        assertFalse(t.annotationsImported());
        assertFalse(t.skippedDetection());
    }
}
