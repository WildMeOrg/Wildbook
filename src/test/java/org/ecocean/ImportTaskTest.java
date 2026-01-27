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
    }
}
