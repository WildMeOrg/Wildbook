package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.api.bulk.BulkImporter;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bulk-import boxes PR B, task 6 (spec 4.3), on a REAL Shepherd: the importer's normal path
 * persists each boxed slot's feature, annotation and encounter links, storeNewEncounter reports
 * every encounter's id (never "fail"), and a reloaded ImportTask over those encounters yields the
 * extraction-mode envelope PR A hands off, holding exactly the box ids.
 */
@Testcontainers
class BulkImporterBoxedDbTest extends IdentificationDbTestSupport {
    static Map<String, Object> validRow(Shepherd sh, Object... kv) throws Exception {
        Map<String, Object> m = new HashMap<String, Object>();
        put(m, sh, "Encounter.genus", "Pseudobatos");
        put(m, sh, "Encounter.specificEpithet", "lentiginosus");
        put(m, sh, "Encounter.year", 2020);
        put(m, sh, "Encounter.submitterID", "public");
        for (int i = 0; i < kv.length; i += 2) put(m, sh, (String)kv[i], kv[i + 1]);
        return m;
    }

    static void put(Map<String, Object> m, Shepherd sh, String name, Object value) throws Exception {
        m.put(name, new BulkValidator(name, value, sh));
    }

    static List<String> boxIds(Encounter enc) {
        List<String> ids = new ArrayList<String>();
        if (enc.getAnnotations() != null) for (Annotation a : enc.getAnnotations()) if (a.isImportedBox()) ids.add(a.getId());
        return ids;
    }

    @Test void boxedAnnotationsPersistWithTheirFeaturesAndEncountersAndReachTheHandoff() throws Exception {
        String importId = nextImportId();
        Shepherd sh = spy(open("boxed-import"));
        List<String> storeResults = new ArrayList<String>();
        doAnswer(inv -> {
            Object r = inv.callRealMethod();
            storeResults.add(((Encounter)inv.getArgument(0)).getId() + "=" + r);
            return r;
        }).when(sh).storeNewEncounter(any(Encounter.class), anyString());
        List<String> encIds = new ArrayList<String>();
        Set<String> expectedBoxIds = new HashSet<String>();
        Map<String, Set<String> > boxesByEncounter = new HashMap<String, Set<String> >();
        Map<String, Integer> annotationsByEncounter = new HashMap<String, Integer>();
        Map<String, String> featureIdByBox = new HashMap<String, String>();
        Map<String, int[]> geometryByBox = new HashMap<String, int[]>();
        int aId, bId;
        try {
            MediaAsset a = boxedAsset(sh);
            MediaAsset b = boxedAsset(sh);
            aId = a.getIdInt();
            bId = b.getIdInt();
            Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();
            maMap.put("a.jpg", a);
            maMap.put("b.jpg", b);
            List<Map<String, Object> > rows = new ArrayList<Map<String, Object> >();
            rows.add(validRow(sh, "Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "10,20,300,400", "Encounter.mediaAsset0.iaClass", BODY));
            rows.add(validRow(sh, "Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "500,600,200,100", "Encounter.mediaAsset0.iaClass", BODY,
                "Encounter.mediaAsset1", "b.jpg"));
            try (MockedStatic<MediaAsset> media = mockStatic(MediaAsset.class, CALLS_REAL_METHODS)) {
                media.when(() -> MediaAsset.updateStandardChildrenBackground(anyString(), any(List.class), any(Runnable.class))).thenAnswer(inv -> null);
                BulkImporter imp = new BulkImporter(importId, rows, maMap, null, sh);
                imp.createImport();   // the normal persistence path: storeNewEncounter, storeNewOccurrence, MediaAssetFactory.save
                for (Encounter enc : imp.getEncounters()) {
                    encIds.add(enc.getId());
                    expectedBoxIds.addAll(boxIds(enc));
                    boxesByEncounter.put(enc.getId(), new HashSet<String>(boxIds(enc)));
                    annotationsByEncounter.put(enc.getId(), enc.getAnnotations().size());
                    for (Annotation ann : enc.getAnnotations()) {
                        if (!ann.isImportedBox()) continue;
                        JSONObject p = ann.getFeature().getParameters();
                        featureIdByBox.put(ann.getId(), ann.getFeature().getId());
                        geometryByBox.put(ann.getId(), new int[] { p.getInt("x"), p.getInt("y"), p.getInt("width"), p.getInt("height") });
                    }
                }
            }
            sh.commitDBTransaction();
        } finally {
            sh.rollbackAndClose();
        }
        assertEquals(2, encIds.size());
        assertEquals(2, expectedBoxIds.size());
        assertEquals(Set.of(1, 2), new HashSet<Integer>(annotationsByEncounter.values()), "one encounter holds a box, the other a box and the trivial b.jpg");
        assertEquals(2, geometryByBox.size());
        assertEquals(2, storeResults.size(), storeResults.toString());
        for (String r : storeResults) {
            String[] parts = r.split("=");
            assertEquals(parts[0], parts[1], "storeNewEncounter returned the encounter id, not \"fail\": " + r);
        }

        Shepherd check = open("check");
        try {
            Set<String> boxesSeen = new HashSet<String>();
            for (String encId : encIds) {
                Encounter enc = check.getEncounter(encId);
                assertNotNull(enc, encId);
                assertNotNull(enc.getAnnotations(), encId);
                assertEquals(annotationsByEncounter.get(encId).intValue(), enc.getAnnotations().size(), "ENCOUNTER_ANNOTATIONS of " + encId);
                assertEquals(boxesByEncounter.get(encId), new HashSet<String>(boxIds(enc)), "this encounter's own boxes, " + encId);
                for (Annotation ann : enc.getAnnotations()) {
                    if (!ann.isImportedBox()) continue;
                    boxesSeen.add(ann.getId());
                    Feature f = ann.getFeature();
                    assertNotNull(f, ann.getId());
                    assertEquals(featureIdByBox.get(ann.getId()), f.getId(), "ANNOTATION_FEATURES holds the feature built at import");
                    assertEquals(importId, ann.importedBoxImportTaskId());
                    int[] g = geometryByBox.get(ann.getId());
                    JSONObject p = f.getParameters();
                    assertEquals(g[0], p.getInt("x"));
                    assertEquals(g[1], p.getInt("y"));
                    assertEquals(g[2], p.getInt("width"));
                    assertEquals(g[3], p.getInt("height"));
                    assertNotNull(f.getAnnotation(), "FEATURE.ANNOTATION");
                    assertEquals(ann.getId(), f.getAnnotation().getId());
                    assertNotNull(f.getMediaAsset(), "MEDIAASSET_FEATURES");
                    assertEquals(aId, f.getMediaAsset().getIdInt());
                    assertTrue(ann.getMatchAgainst());
                    assertEquals(ann.getId(), ann.getAcmId());
                }
            }
            assertEquals(expectedBoxIds, boxesSeen);
            MediaAsset ra = MediaAssetFactory.load(aId, check);
            assertEquals(2, ra.getFeatures().size(), "a.jpg carries the two boxes and nothing trivial");
            Set<String> assetFeatureIds = new HashSet<String>();
            for (Feature f : ra.getFeatures()) {
                assertFalse(f.isUnity());
                assetFeatureIds.add(f.getId());
            }
            assertEquals(new HashSet<String>(featureIdByBox.values()), assetFeatureIds, "the asset's features are exactly the two boxes' features");
            MediaAsset rb = MediaAssetFactory.load(bId, check);
            assertEquals(1, rb.getFeatures().size());
            assertTrue(rb.getFeatures().get(0).isUnity());
            assertNull(ra.getDetectionStatus());
            assertNull(rb.getDetectionStatus());
            ImportTask itask = new ImportTask(null, importId);
            for (String encId : encIds) itask.addEncounter(check.getEncounter(encId));   // reloaded in THIS context
            itask.setPassedParameters(new JSONObject().put(ImportTask.PARAM_ANNOTATIONS_IMPORTED, true));
            check.storeNewImportTask(itask);
            check.commitDBTransaction();
        } finally {
            check.rollbackAndClose();
        }

        Shepherd verify = open("verify");
        try {
            ImportTask reloaded = verify.getImportTask(importId);
            assertNotNull(reloaded);
            JSONObject env = IAGateway.buildImportEnvelope(reloaded);
            assertEquals("extract-then-identify", env.getString("annotationMode"));
            assertEquals(2, env.getJSONArray("annotationIds").length());
            assertEquals(expectedBoxIds, new HashSet<Object>(env.getJSONArray("annotationIds").toList()));
            assertFalse(env.has("mediaAssetIds"));
        } finally {
            verify.rollbackAndClose();
        }
    }
}
