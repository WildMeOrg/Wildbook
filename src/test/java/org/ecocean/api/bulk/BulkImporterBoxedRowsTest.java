package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.api.bulk.BulkImporter;
import org.ecocean.media.AssetStore;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Bulk-import boxes PR B, task 6 (spec 4.1 / 4.3): BulkImporter.processRow turns a boxed slot
 * into a bounding-box feature and a matchable annotation carrying the provenance marker, reads
 * per-slot metadata by exact slot number for the whole import once any row carries a .bbox
 * column, and never builds a box without a validated class and taxonomy. Harness as
 * BulkImporterMissingAssetTest: mocked Shepherd/PMF, real in-memory MediaAssets.
 */
class BulkImporterBoxedRowsTest {
    static final String IMPORT_ID = "import-boxed";
    static final String TX = "Genus species";
    PersistenceManager mockPM = mock(PersistenceManager.class);
    PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
    User user = mock(User.class);
    Occurrence occ = mock(Occurrence.class);
    final Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();
    final List<List<Base> > indexed = new ArrayList<List<Base> >();   // the lists handed to bulkOpensearchIndex

    @BeforeEach void fixture() {
        BulkImportUtil.labeledKeywordMap = new LinkedHashMap<String, Set<String> >();
        BulkImportUtil.labeledKeywordMap.put("colour", new LinkedHashSet<String>(List.of("red", "blue")));
        JSONObject json = new JSONObject().put("Genus", new JSONObject().put("species", new JSONObject()
            .put("body", idConf(true)).put("fin", idConf(false))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(json);
        TestIAJsonPropertiesUtil.override(iac);
        maMap.clear();
        for (String n : new String[] { "p.jpg", "q.jpg", "r.jpg" }) maMap.put(n, asset());
        indexed.clear();
    }

    @AfterEach void clear() {
        BulkImportUtil.labeledKeywordMap = null;
        TestIAJsonPropertiesUtil.override(null);
    }

    static JSONObject idConf(boolean dflt) {
        return new JSONObject().put("_id_conf", new JSONArray().put(new JSONObject().put("default", dflt)));
    }

    static MediaAsset asset() {
        MediaAsset ma = new MediaAsset(0, mock(AssetStore.class), null);
        MediaAssetMetadata md = new MediaAssetMetadata();
        md.getData().put("attributes", new JSONObject().put("width", 4608).put("height", 3456).put("contentType", "image/jpeg"));
        ma.setMetadata(md);
        return ma;
    }

    /** A row with the required columns; pairs override or add. JSONObject.NULL for an empty cell. */
    static JSONObject row(Object... kv) {
        JSONObject r = new JSONObject().put("Encounter.submitterID", "fakeSubmitterId").put("Encounter.year", 2020)
            .put("Encounter.genus", "Genus").put("Encounter.specificEpithet", "species");
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] == null) r.remove((String)kv[i]);
            else r.put((String)kv[i], kv[i + 1]);
        }
        return r;
    }

    static final Object[] BOXED0 = { "Encounter.mediaAsset0", "p.jpg", "Encounter.mediaAsset0.bbox", "10,20,300,400", "Encounter.mediaAsset0.iaClass", "body" };

    static JSONObject boxedRow(Object... more) {
        Object[] all = new Object[BOXED0.length + more.length];
        System.arraycopy(BOXED0, 0, all, 0, BOXED0.length);
        System.arraycopy(more, 0, all, BOXED0.length, more.length);
        return row(all);
    }

    BulkImporter run(JSONObject... rows) throws Exception {
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class, (mockSh, context) -> {
                 when(mockSh.getContext()).thenReturn("context0");
                 when(mockSh.getPM()).thenReturn(mockPM);
                 when(mockSh.getUser(any(String.class))).thenReturn(user);
                 when(mockSh.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
                 when(mockSh.getOrCreateOccurrence(null)).thenReturn(occ);
                 when(mockSh.isValidTaxonomyName(any(String.class))).thenReturn(true);
                 when(mockSh.getOrCreateLabeledKeyword(anyString(), anyString(), anyBoolean()))
                     .thenAnswer(inv -> new LabeledKeyword(inv.getArgument(0), inv.getArgument(1)));
             });
             MockedStatic<ShepherdPMF> pmf = mockStatic(ShepherdPMF.class);
             MockedStatic<FeatureType> ft = mockStatic(FeatureType.class);
             MockedStatic<MediaAsset> media = mockStatic(MediaAsset.class, CALLS_REAL_METHODS);
             MockedStatic<BulkImportUtil> util = mockStatic(BulkImportUtil.class, CALLS_REAL_METHODS)) {
            pmf.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
            ft.when(() -> FeatureType.load(eq("org.ecocean.boundingBox"), any(Shepherd.class))).thenReturn(new FeatureType("org.ecocean.boundingBox"));
            media.when(() -> MediaAsset.updateStandardChildrenBackground(anyString(), any(List.class), any(Runnable.class)))
                .thenAnswer(inv -> { ((Runnable)inv.getArgument(2)).run(); return null; });
            util.when(() -> BulkImportUtil.bulkOpensearchIndex(any(List.class)))
                .thenAnswer(inv -> { indexed.add(new ArrayList<Base>(inv.getArgument(0))); return null; });
            Shepherd sh = new Shepherd("context0");
            List<Map<String, Object> > validated = new ArrayList<Map<String, Object> >();
            for (JSONObject r : rows) validated.add(BulkImportUtil.validateRow(r, sh));
            BulkImporter imp = new BulkImporter(IMPORT_ID, validated, maMap, null, sh);
            imp.createImport();
            return imp;
        }
    }

    static Annotation annotationOn(Encounter enc, MediaAsset ma) {
        if (enc.getAnnotations() == null) return null;
        for (Annotation a : enc.getAnnotations()) if (a.getMediaAsset() == ma) return a;
        return null;
    }

    // the importer returns its encounters in cache (hash) order, so find them by their photo
    static Encounter encounterWith(BulkImporter imp, MediaAsset ma) {
        for (Encounter enc : imp.getEncounters()) if (annotationOn(enc, ma) != null) return enc;
        return null;
    }

    static Set<String> keywordNames(MediaAsset ma) {
        Set<String> names = new TreeSet<String>();
        if (ma.getKeywords() != null) for (Keyword k : ma.getKeywords()) names.add(k.getReadableName());
        return names;
    }

    @Test void boxedSlotBecomesAMatchableBoxAnnotation() throws Exception {
        BulkImporter imp = run(boxedRow("Encounter.mediaAsset0.viewpoint", "left", "Encounter.mediaAsset0.theta", "0.25",
            "Encounter.mediaAsset0.bboxSource", "yolo", "Encounter.quality0", "0.93"));
        Encounter enc = imp.getEncounters().get(0);
        assertEquals(1, enc.getAnnotations().size());
        Annotation ann = enc.getAnnotations().get(0);
        assertTrue(ann.isImportedBox());
        assertFalse(ann.isTrivial());
        Feature f = ann.getFeature();
        JSONObject p = f.getParameters();
        assertEquals(10.0, p.getDouble("x"));
        assertEquals(20.0, p.getDouble("y"));
        assertEquals(300.0, p.getDouble("width"));
        assertEquals(400.0, p.getDouble("height"));
        assertEquals(0.25, p.getDouble("theta"));
        assertEquals("left", p.getString("viewpoint"));
        JSONObject marker = p.getJSONObject(Annotation.FEATURE_PARAM_IMPORTED_BOX);
        assertEquals(IMPORT_ID, marker.getString("importTaskId"));
        assertEquals("yolo", marker.getString("source"));
        assertEquals("detector", marker.getString("scoreSource"));
        assertTrue(marker.getLong("created") > 0);
        assertSame(ann, f.getAnnotation());
        assertSame(maMap.get("p.jpg"), f.getMediaAsset());
        assertEquals("body", ann.getIAClass());
        assertEquals(ann.getId(), ann.getAcmId());
        assertTrue(ann.getMatchAgainst());
        assertEquals(Boolean.FALSE, ann.getWbiaRegistered());
        assertEquals(0, ann.getWbiaRegisterAttempts());
        assertTrue(ann.getIsExemplar());
        assertTrue(ann.getSkipAutoIndexing());
        assertEquals(0.93, ann.getQuality());
        assertEquals("left", ann.getViewpoint());
        assertNull(ann.getIdentificationStatus());
        assertNull(maMap.get("p.jpg").getDetectionStatus());
        assertEquals(1, indexed.size());
        assertTrue(indexed.get(0).contains(ann), "the box is queued for indexing");
        assertTrue(indexed.get(0).contains(enc));
    }

    @Test void wholePhotoBoxStaysNonTrivialAndMatchable() throws Exception {
        Encounter enc = run(row("Encounter.mediaAsset0", "p.jpg", "Encounter.mediaAsset0.bbox", "0,0,4608,3456", "Encounter.mediaAsset0.iaClass", "body")).getEncounters().get(0);
        Annotation ann = enc.getAnnotations().get(0);
        assertTrue(ann.isImportedBox());
        assertFalse(ann.isTrivial());
        assertTrue(ann.getMatchAgainst());
    }

    @Test void emptyBboxCellCreatesNoBox() throws Exception {
        Encounter enc = run(row("Encounter.mediaAsset0", "p.jpg", "Encounter.mediaAsset0.bbox", JSONObject.NULL, "Encounter.mediaAsset0.iaClass", JSONObject.NULL)).getEncounters().get(0);
        assertEquals(1, enc.getAnnotations().size());
        assertFalse(enc.getAnnotations().get(0).isImportedBox());
        assertTrue(enc.getAnnotations().get(0).isTrivial());
    }

    @Test void unboxedSlotInABoxedRowStaysTrivial() throws Exception {
        Encounter enc = run(boxedRow("Encounter.mediaAsset1", "q.jpg")).getEncounters().get(0);
        assertEquals(2, enc.getAnnotations().size());
        Annotation plain = annotationOn(enc, maMap.get("q.jpg"));
        assertNotNull(plain);
        assertFalse(plain.isImportedBox());
        assertTrue(plain.isTrivial());
        assertFalse(indexed.get(0).contains(plain));
        assertTrue(indexed.get(0).contains(annotationOn(enc, maMap.get("p.jpg"))));
    }

    static JSONObject gappedRow() {
        return row("Encounter.mediaAsset0", "q.jpg", "Encounter.mediaAsset2", "r.jpg", "Encounter.keyword1", "k1", "Encounter.quality1", "0.1",
            "Encounter.keyword2", "k2", "Encounter.mediaAsset2.keywords", "m_n", "Encounter.mediaAsset2.colour", "red", "Encounter.quality2", "0.5");
    }

    @Test void unboxedRowWithSlotGapsKeepsItsKeywordsAndQualityInExactSlotMode() throws Exception {
        BulkImporter imp = run(boxedRow(), gappedRow());
        assertEquals(2, imp.getEncounters().size());
        Encounter enc = encounterWith(imp, maMap.get("r.jpg"));
        assertNotNull(enc);
        Annotation r = annotationOn(enc, maMap.get("r.jpg"));
        Annotation q = annotationOn(enc, maMap.get("q.jpg"));
        assertNotNull(r);
        assertNotNull(q);
        assertEquals(0.5, r.getQuality());
        assertEquals(Set.of("k2", "m", "n", "red"), keywordNames(maMap.get("r.jpg")));
        LabeledKeyword lk = null;
        for (Keyword k : maMap.get("r.jpg").getKeywords()) if (k instanceof LabeledKeyword) lk = (LabeledKeyword)k;
        assertNotNull(lk);
        assertEquals("colour", lk.getLabel());
        assertEquals("red", lk.getValue());
        assertNull(q.getQuality());
        assertTrue(keywordNames(maMap.get("q.jpg")).isEmpty());
        for (MediaAsset ma : maMap.values()) assertFalse(keywordNames(ma).contains("k1"));
    }

    @Test void positionalModeIsUnchangedWithoutABboxColumn() throws Exception {   // (regression)
        Encounter enc = run(gappedRow().put("Encounter.mediaAsset0.theta", JSONObject.NULL)).getEncounters().get(0);
        Annotation r = annotationOn(enc, maMap.get("r.jpg"));
        assertEquals(0.1, r.getQuality(), "the second occupied slot reads list index 1, as today");
        assertEquals(Set.of("k1"), keywordNames(maMap.get("r.jpg")));
        assertNull(annotationOn(enc, maMap.get("q.jpg")).getQuality());
        for (MediaAsset ma : maMap.values()) {
            Set<String> names = keywordNames(ma);
            assertFalse(names.contains("k2") || names.contains("m") || names.contains("red"), names.toString());
        }
    }

    @Test void labeledAndOrdinaryKeywordsWithTheSameTextNeverShareACacheEntry() throws Exception {
        Encounter enc = run(boxedRow("Encounter.keyword0", "colour=red", "Encounter.mediaAsset1", "q.jpg", "Encounter.mediaAsset1.colour", "red")).getEncounters().get(0);
        assertNotNull(enc);
        assertPlainKeyword(maMap.get("p.jpg"), "colour=red");
        assertLabeledKeyword(maMap.get("q.jpg"), "colour", "red");
        fixture();
        enc = run(boxedRow("Encounter.mediaAsset0.colour", "red", "Encounter.mediaAsset1", "q.jpg", "Encounter.keyword1", "colour=red")).getEncounters().get(0);
        assertNotNull(enc);
        assertLabeledKeyword(maMap.get("p.jpg"), "colour", "red");
        assertPlainKeyword(maMap.get("q.jpg"), "colour=red");
    }

    static void assertPlainKeyword(MediaAsset ma, String name) {
        assertEquals(1, ma.getKeywords().size(), ma.getKeywords().toString());
        Keyword k = ma.getKeywords().get(0);
        assertFalse(k instanceof LabeledKeyword, k.toString());
        assertEquals(name, k.getReadableName());
    }

    static void assertLabeledKeyword(MediaAsset ma, String label, String value) {
        assertEquals(1, ma.getKeywords().size(), ma.getKeywords().toString());
        Keyword k = ma.getKeywords().get(0);
        assertTrue(k instanceof LabeledKeyword, k.toString());
        assertEquals(label, ((LabeledKeyword)k).getLabel());
        assertEquals(value, ((LabeledKeyword)k).getValue());
    }

    @Test void toleratedUnknownColumnsDoNotBreakModeDetection() throws Exception {
        BulkImporter imp = run(boxedRow("Encounter.bogus", "x"), gappedRow());
        Encounter boxed = encounterWith(imp, maMap.get("p.jpg"));
        assertEquals(1, boxed.getAnnotations().size());
        assertTrue(boxed.getAnnotations().get(0).isImportedBox());
        // exact-slot mode is on: the gapped row's slot-2 metadata lands on slot 2
        Encounter gapped = encounterWith(imp, maMap.get("r.jpg"));
        assertEquals(0.5, annotationOn(gapped, maMap.get("r.jpg")).getQuality());
    }

    @Test void metadataWithoutABboxColumnDoesNotSwitchToExactSlotMode() throws Exception {
        // a tolerant import whose sheet has .iaClass but no .bbox column: validation rejects the
        // class (REQUIRED on .iaClass) without fabricating a .bbox key, so the import stays positional
        BulkImporter imp = run(row("Encounter.mediaAsset0", "p.jpg", "Encounter.mediaAsset0.iaClass", "body"), gappedRow());
        Encounter gapped = encounterWith(imp, maMap.get("r.jpg"));
        assertEquals(0.1, annotationOn(gapped, maMap.get("r.jpg")).getQuality(), "positional: the second occupied slot reads index 1");
        assertFalse(annotationOn(encounterWith(imp, maMap.get("p.jpg")), maMap.get("p.jpg")).isImportedBox());
    }

    @Test void boxedSlotWhoseAssetFailedIsSkippedWithItsBox() throws Exception {
        maMap.remove("p.jpg");
        Encounter enc = run(boxedRow("Encounter.mediaAsset1", "q.jpg")).getEncounters().get(0);
        assertEquals(1, enc.getAnnotations().size());
        Annotation only = enc.getAnnotations().get(0);
        assertSame(maMap.get("q.jpg"), only.getMediaAsset());
        assertTrue(only.isTrivial());
    }

    @Test void boxWithoutAValidatedClassOrTaxonomyImportsAsATrivialAnnotation() throws Exception {
        JSONObject[] shapes = {
            boxedRow("Encounter.mediaAsset0.iaClass", null),                                  // (a) no class column
            boxedRow("Encounter.mediaAsset0.iaClass", "fin"),                                 // (b) class rejected (identification on)
            boxedRow("Encounter.genus", null, "Encounter.specificEpithet", null),             // (c) taxonomy absent
            boxedRow("Encounter.specificEpithet", null),                                      // (d) genus only
            boxedRow("Encounter.genus", null),                                                // (e) epithet only
        };
        for (int i = 0; i < shapes.length; i++) {
            fixture();
            Encounter enc = run(shapes[i]).getEncounters().get(0);
            assertEquals(1, enc.getAnnotations().size(), "shape " + i);
            Annotation ann = enc.getAnnotations().get(0);
            assertFalse(ann.isImportedBox(), "shape " + i);
            assertTrue(ann.isTrivial(), "shape " + i);
            assertNull(ann.getIAClass(), "shape " + i);
        }
    }

    @Test void reusedEncounterWithAnEarlierValidTaxonomyDoesNotLendItToARejectedRow() throws Exception {
        String id = "00000000-0000-4000-8000-000000000011";
        JSONObject a = row("Encounter.id", id, "Encounter.mediaAsset0", "p.jpg");
        JSONObject b = row("Encounter.id", id, "Encounter.genus", null, "Encounter.specificEpithet", null,
            "Encounter.mediaAsset1", "q.jpg", "Encounter.mediaAsset1.bbox", "1,1,50,50", "Encounter.mediaAsset1.iaClass", "body");
        BulkImporter imp = run(a, b);
        assertEquals(1, imp.getEncounters().size());
        Encounter enc = imp.getEncounters().get(0);
        assertEquals("Genus", enc.getGenus());
        assertEquals("species", enc.getSpecificEpithet());
        assertEquals(2, enc.getAnnotations().size());
        Annotation onQ = annotationOn(enc, maMap.get("q.jpg"));
        assertNotNull(onQ);
        assertFalse(onQ.isImportedBox());
        assertTrue(onQ.isTrivial());
    }

    // ---- zero-padded slot columns, and a rejected angle ----

    @Test void zeroPaddedBoxColumnsStillMakeTheBox() throws Exception {
        Encounter enc = run(row("Encounter.mediaAsset01", "p.jpg", "Encounter.mediaAsset01.bbox", "10,20,300,400",
            "Encounter.mediaAsset01.iaClass", "body", "Encounter.mediaAsset01.theta", "0.25",
            "Encounter.mediaAsset01.viewpoint", "left", "Encounter.mediaAsset01.bboxSource", "yolo")).getEncounters().get(0);
        assertEquals(1, enc.getAnnotations().size());
        Annotation ann = enc.getAnnotations().get(0);
        assertTrue(ann.isImportedBox(), "the padded box column is read");
        JSONObject p = ann.getFeature().getParameters();
        assertEquals(10.0, p.getDouble("x"));
        assertEquals(300.0, p.getDouble("width"));
        assertEquals(0.25, p.getDouble("theta"));
        assertEquals("left", ann.getViewpoint());
        assertEquals("body", ann.getIAClass());
        assertEquals("yolo", p.getJSONObject(Annotation.FEATURE_PARAM_IMPORTED_BOX).getString("source"));
    }

    @Test void rejectedAngleImportsThePhotoWithoutTheBox() throws Exception {
        for (String bad : new String[] { "sideways", "7" }) {
            fixture();
            Encounter enc = run(boxedRow("Encounter.mediaAsset0.theta", bad)).getEncounters().get(0);
            assertEquals(1, enc.getAnnotations().size());
            Annotation ann = enc.getAnnotations().get(0);
            assertFalse(ann.isImportedBox(), "no box at a made-up angle for theta=" + bad);
            assertTrue(ann.isTrivial());
        }
    }

    @Test void rejectedAngleOnOneSlotLeavesTheOtherSlotsBox() throws Exception {
        Encounter enc = run(boxedRow("Encounter.mediaAsset0.theta", "sideways", "Encounter.mediaAsset1", "q.jpg",
            "Encounter.mediaAsset1.bbox", "1,2,30,40", "Encounter.mediaAsset1.iaClass", "body")).getEncounters().get(0);
        assertFalse(annotationOn(enc, maMap.get("p.jpg")).isImportedBox());
        Annotation q = annotationOn(enc, maMap.get("q.jpg"));
        assertTrue(q.isImportedBox());
        assertEquals(0.0, q.getFeature().getParameters().getDouble("theta"), "an absent angle still means 0");
    }

    @Test void rejectedAngleIsMatchedToItsBoxByNumberNotSpelling() throws Exception {
        Encounter enc = run(row("Encounter.mediaAsset01", "p.jpg", "Encounter.mediaAsset1.bbox", "10,20,300,400",
            "Encounter.mediaAsset001.iaClass", "body", "Encounter.mediaAsset01.theta", "sideways")).getEncounters().get(0);
        assertFalse(enc.getAnnotations().get(0).isImportedBox());
    }

    @Test void zeroPaddedSlotsKeepTheirKeywordsQualityAndLabels() throws Exception {
        // slots 0 and 2 only, every column of slot 2 padded
        Encounter enc = run(boxedRow("Encounter.keyword0", "kw-zero", "Encounter.mediaAsset02", "q.jpg",
            "Encounter.mediaAsset02.bbox", "1,2,30,40", "Encounter.mediaAsset02.iaClass", "body",
            "Encounter.keyword02", "kw-two", "Encounter.quality02", "0.4", "Encounter.mediaAsset02.colour", "red")).getEncounters().get(0);
        Annotation q = annotationOn(enc, maMap.get("q.jpg"));
        assertTrue(q.isImportedBox());
        assertEquals(0.4, q.getQuality());
        assertEquals(Set.of("kw-two", "red"), keywordNames(maMap.get("q.jpg")));
        assertEquals(Set.of("kw-zero"), keywordNames(maMap.get("p.jpg")));
    }
}
