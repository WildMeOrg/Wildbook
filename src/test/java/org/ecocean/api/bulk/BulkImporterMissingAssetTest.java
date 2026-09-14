package org.ecocean;

import org.ecocean.api.bulk.*;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.ServletException;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Keyword;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Covers the BulkImporter.processRow() tolerance guard: a row referencing a
 * mediaAsset whose MediaAsset is absent from the maMap (which happens when an
 * image failed validation at creation time -- see AssetStore.isValidImage)
 * must NOT abort the import. Instead the bad image is skipped and the encounter
 * still imports with its remaining valid images.
 */
class BulkImporterMissingAssetTest {
    PersistenceManagerFactory mockPMF;
    PersistenceManager mockPM = mock(PersistenceManager.class);

    @Test void missingAssetIsSkippedNotThrown()
    throws ServletException {
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);

        // A row with two images: index 0 is present in the maMap, index 1 is
        // NOT (simulating an image that failed isValidImage and so never got a
        // MediaAsset created for it).
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("Encounter.submitterID", "fakeSubmitterId");
        row.put("Encounter.year", 2000);
        row.put("Encounter.genus", "genus");
        row.put("Encounter.specificEpithet", "specificEpithet");
        row.put("Encounter.mediaAsset0", "good.jpg");
        row.put("Encounter.mediaAsset1", "corrupt.jpg");

        // maMap holds ONLY the valid image; "corrupt.jpg" is intentionally absent.
        // A mocked store keeps the diagnostic MediaAsset.toString() (logged during
        // the persistence loop) from NPEing on an otherwise-bare asset.
        MediaAsset goodMA = new MediaAsset(0, mock(AssetStore.class), null);
        Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();
        maMap.put("good.jpg", goodMA);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mockSh, context) -> {
            when(mockSh.getContext()).thenReturn("context0");
            when(mockSh.getPM()).thenReturn(mockPM);
            when(mockSh.getUser(any(String.class))).thenReturn(user);
            when(mockSh.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mockSh.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mockSh.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);

                Shepherd myShepherd = new Shepherd("context0");
                Map<String, Object> validated = BulkImportUtil.validateRow(
                    new JSONObject(row), myShepherd);
                List<Map<String, Object> > allRows = new ArrayList<Map<String, Object> >();
                allRows.add(validated);

                BulkImporter imp = new BulkImporter("test-missing-asset", allRows, maMap, null,
                    myShepherd);
                // Before the guard this threw RuntimeException (wrapped as
                // ServletException) on the missing "corrupt.jpg" asset.
                imp.createImport();

                assertEquals(1, Util.collectionSize(imp.getEncounters()));
                Encounter enc = imp.getEncounters().get(0);
                // Only the present ("good.jpg") image yields an annotation; the
                // missing one was skipped, not fatal.
                assertEquals(1, Util.collectionSize(enc.getAnnotations()));
                assertEquals(goodMA, enc.getAnnotations().get(0).getMediaAsset());
            }
        }
    }

    /**
     * Alignment guard: when the CORRUPT/missing image is at index 0 and a VALID
     * image is at index 1, the keyword/quality lists are POSITIONAL, so the
     * surviving valid image must read keyword1/quality1 -- NOT keyword0/quality0
     * (the corrupt image's metadata). This proves processRow() advances `offset`
     * when it skips a missing MediaAsset, consuming the corrupt image's column
     * slot so later images stay column-aligned.
     */
    @Test void corruptFirstKeepsKeywordQualityAligned()
    throws ServletException {
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);

        // index 0 = corrupt (absent from maMap) with its own keyword/quality;
        // index 1 = the valid image with DISTINCT keyword/quality.
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("Encounter.submitterID", "fakeSubmitterId");
        row.put("Encounter.year", 2000);
        row.put("Encounter.genus", "genus");
        row.put("Encounter.specificEpithet", "specificEpithet");
        row.put("Encounter.mediaAsset0", "corrupt.jpg");
        row.put("Encounter.keyword0", "corruptKeyword");
        row.put("Encounter.quality0", 1.0);
        row.put("Encounter.mediaAsset1", "good.jpg");
        row.put("Encounter.keyword1", "goodKeyword");
        row.put("Encounter.quality1", 5.0);

        MediaAsset goodMA = new MediaAsset(0, mock(AssetStore.class), null);
        Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();
        maMap.put("good.jpg", goodMA); // "corrupt.jpg" intentionally absent

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mockSh, context) -> {
            when(mockSh.getContext()).thenReturn("context0");
            when(mockSh.getPM()).thenReturn(mockPM);
            when(mockSh.getUser(any(String.class))).thenReturn(user);
            when(mockSh.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mockSh.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mockSh.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);

                Shepherd myShepherd = new Shepherd("context0");
                Map<String, Object> validated = BulkImportUtil.validateRow(
                    new JSONObject(row), myShepherd);
                List<Map<String, Object> > allRows = new ArrayList<Map<String, Object> >();
                allRows.add(validated);

                BulkImporter imp = new BulkImporter("test-corrupt-first", allRows, maMap, null,
                    myShepherd);
                imp.createImport();

                assertEquals(1, Util.collectionSize(imp.getEncounters()));
                Encounter enc = imp.getEncounters().get(0);
                // Only the present ("good.jpg") image yields an annotation.
                assertEquals(1, Util.collectionSize(enc.getAnnotations()));
                Annotation ann = enc.getAnnotations().get(0);
                assertEquals(goodMA, ann.getMediaAsset());

                // The CORE assertion: the surviving valid image must carry its
                // OWN positional metadata (keyword1/quality1), not the corrupt
                // image's (keyword0/quality0). Before the offset++ fix it would
                // inherit "corruptKeyword"/1.0.
                assertEquals(Double.valueOf(5.0), ann.getQuality());
                List<Keyword> kws = goodMA.getKeywords();
                assertEquals(1, Util.collectionSize(kws));
                assertEquals("goodKeyword", kws.get(0).getReadableName());
            }
        }
    }
}
