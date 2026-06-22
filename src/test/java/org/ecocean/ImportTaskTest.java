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
}
