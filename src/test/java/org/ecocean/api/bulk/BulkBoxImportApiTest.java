package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.ImageFixtures;
import org.ecocean.Occurrence;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.User;
import org.ecocean.ia.IA;
import org.ecocean.media.AssetStore;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Bulk-import boxes PR B, task 5 (spec 4.2 import layer, 4.5, decision 8): the endpoint checks
 * boxed files from their headers in both modes, keeps rows consistent across the import, warns
 * about un-boxed photos, stamps annotationsImported from its own predicate, and hands a boxed
 * import off regardless of the skip boxes. Harness: mocked Shepherd/PMF as the other bulk API
 * tests, real in-memory MediaAssets per uploaded file (the corrupt fixtures go through the real
 * UploadedFiles.makeMediaAsset and AssetStore.isValidImage), one held ImportTask, the IA
 * hand-off captured.
 */
class BulkBoxImportApiTest {
    static final String BULK_ID = "00000000-0000-0000-0000-00000000b0b0";
    static final String[] BASE = { "Encounter.genus", "Encounter.specificEpithet", "Encounter.year" };
    static final Object[] BASE_VALUES = { "Genus", "species", 2020 };
    @TempDir static Path uploadDir;

    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    BulkImport apiServlet;
    StringWriter responseOut;
    PersistenceManager mockPM = mock(PersistenceManager.class);
    PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
    ImportTask held;
    User user;
    Occurrence occ;
    final Map<String, MediaAsset> assets = new HashMap<String, MediaAsset>();
    final List<JSONObject> handoffs = new ArrayList<JSONObject>();
    Set<String> corrupt = new HashSet<String>();   // go through the real makeMediaAsset (real decoder)
    Set<String> failing = new HashSet<String>();   // creation fails for an unrelated reason
    int nextAssetId = 100;

    @BeforeAll static void fixtures() throws Exception {
        BufferedImage img = ImageFixtures.noise(200, 150, 42);
        for (String n : new String[] { "a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg", "f.jpg" })
            ImageFixtures.write(img, "jpeg", uploadDir.resolve(n).toFile(), false);
        File png = ImageFixtures.write(img, "png", uploadDir.resolve("whole.png").toFile(), false);
        ImageFixtures.truncate(png, uploadDir.resolve("trunc.png").toFile(), 0.6);
        ImageFixtures.jpegMagic(uploadDir.resolve("magic.jpg").toFile());
        Files.copy(new File("src/test/bulk-images/image-invalid-mime-type.jpg").toPath(),
            uploadDir.resolve("image-invalid-mime-type.jpg"));
    }

    @BeforeEach void setUp() throws Exception {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        apiServlet = new BulkImport();
        responseOut = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseOut));
        user = mock(User.class);
        when(user.getId()).thenReturn("00000000-0000-4000-8000-0000000000aa");
        when(user.getUsername()).thenReturn("fake-username");
        occ = mock(Occurrence.class);
        held = new ImportTask(null, BULK_ID);
        assets.clear();
        handoffs.clear();
        corrupt.clear();
        failing.clear();
        JSONObject json = new JSONObject().put("Genus", new JSONObject().put("species", new JSONObject()
            .put("body", idConf(true)).put("fin", idConf(false))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(json);
        TestIAJsonPropertiesUtil.override(iac);
    }

    @AfterEach void tearDown() {
        TestIAJsonPropertiesUtil.override(null);
    }

    static JSONObject idConf(boolean dflt) {
        return new JSONObject().put("_id_conf", new JSONArray().put(new JSONObject().put("default", dflt)));
    }

    MediaAsset assetFor(File f) {
        return assets.computeIfAbsent(f.getName(), name -> {
            MediaAsset ma = new MediaAsset(nextAssetId++, mock(AssetStore.class), null);
            MediaAssetMetadata md = new MediaAssetMetadata();
            md.getData().put("attributes", new JSONObject().put("width", 200).put("height", 150).put("contentType", "image/jpeg"));
            ma.setMetadata(md);
            ma.setSkipAutoIndexing(true);
            return ma;
        });
    }

    /** fieldNames + array rows; the three BASE columns are appended to every row. */
    static JSONObject payload(String[] fields, Object[]... rows) {
        String[] names = Arrays.copyOf(fields, fields.length + BASE.length);
        System.arraycopy(BASE, 0, names, fields.length, BASE.length);
        Object[][] full = new Object[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            full[i] = Arrays.copyOf(rows[i], rows[i].length + BASE_VALUES.length);
            System.arraycopy(BASE_VALUES, 0, full[i], rows[i].length, BASE_VALUES.length);
        }
        return payloadRaw(names, full);
    }

    /** fieldNames + array rows exactly as given (null cells become JSON null). */
    static JSONObject payloadRaw(String[] fields, Object[]... rows) {
        JSONObject p = new JSONObject().put("bulkImportId", BULK_ID).put("tolerance", new JSONObject()).put("verbose", true);
        JSONArray names = new JSONArray();
        for (String f : fields) names.put(f);
        p.put("fieldNames", names);
        JSONArray rs = new JSONArray();
        for (Object[] row : rows) {
            JSONArray r = new JSONArray();
            for (Object v : row) r.put(v == null ? JSONObject.NULL : v);
            rs.put(r);
        }
        p.put("rows", rs);
        return p;
    }

    /** The held task's encounter holding an annotation on the asset created for {@code filename}. */
    Encounter encounterWith(String filename) {
        MediaAsset ma = assets.get(filename);
        for (Encounter enc : held.getEncounters()) {
            if (enc.getAnnotations() == null) continue;
            for (Annotation a : enc.getAnnotations()) if (a.getMediaAsset() == ma) return enc;
        }
        return null;
    }

    static List<String> boxIds(Encounter enc) {
        List<String> ids = new ArrayList<String>();
        if (enc.getAnnotations() != null) for (Annotation a : enc.getAnnotations()) if (a.isImportedBox()) ids.add(a.getId());
        return ids;
    }

    List<Object> envelopeIds() {
        return IAGateway.buildImportEnvelope(held).getJSONArray("annotationIds").toList();
    }

    static JSONObject tolerant(JSONObject p) {
        return p.put("tolerance", new JSONObject().put("failImportOnError", false));
    }

    JSONObject post(JSONObject payload) throws Exception {
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload.toString())));
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (mock, ctx) -> {
                 when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                 when(mock.getUser(any(String.class))).thenReturn(user);
                 when(mock.getUserByUUID(any(String.class))).thenReturn(user);
                 when(mock.isValidTaxonomyName(any(String.class))).thenAnswer(inv -> !"Genus nope".equals(inv.getArgument(0)));
                 when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
                 when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
                 when(mock.getImportTask(any(String.class))).thenReturn(held);
                 when(mock.getPM()).thenReturn(mockPM);
                 when(mock.getContext()).thenReturn("context0");
             });
             MockedStatic<UploadedFiles> files = mockStatic(UploadedFiles.class, CALLS_REAL_METHODS);
             MockedStatic<ShepherdPMF> pmf = mockStatic(ShepherdPMF.class);
             MockedStatic<FeatureType> ft = mockStatic(FeatureType.class);
             MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS);
             MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<MediaAsset> media = mockStatic(MediaAsset.class, CALLS_REAL_METHODS);   // a full static mock would also stub the enhancer's dnGet* accessors
             MockedStatic<AssetStore> stores = mockStatic(AssetStore.class, CALLS_REAL_METHODS)) {
            files.when(() -> UploadedFiles.getUploadDir(any(HttpServletRequest.class), any(String.class))).thenReturn(uploadDir.toFile());
            files.when(() -> UploadedFiles.makeMediaAsset(any(String.class), any(File.class), any(Shepherd.class))).thenAnswer(inv -> {
                File f = inv.getArgument(1);
                if (corrupt.contains(f.getName())) return inv.callRealMethod();
                if (failing.contains(f.getName()))
                    throw new ApiException(f.getName() + " creation failed", new JSONObject().put("code", ApiException.ERROR_RETURN_CODE_INVALID).put("filename", f.getName()));
                return assetFor(f);
            });
            stores.when(() -> AssetStore.getDefault(any(Shepherd.class))).thenReturn(mock(AssetStore.class));
            pmf.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
            ft.when(() -> FeatureType.load(eq("org.ecocean.boundingBox"), any(Shepherd.class))).thenReturn(new FeatureType("org.ecocean.boundingBox"));
            gateway.when(() -> IAGateway.handleBulkImport(any(JSONObject.class), any(JSONObject.class), any(Shepherd.class), anyString(), any()))
                .thenAnswer(inv -> { handoffs.add(inv.getArgument(0)); return new JSONObject().put("success", true); });
            ia.when(() -> IA.getBaseURL(anyString())).thenReturn("https://base.test");
            media.when(() -> MediaAsset.updateStandardChildrenBackground(anyString(), any(List.class), any(Runnable.class))).thenAnswer(inv -> null);
            apiServlet.doPost(mockRequest, mockResponse);
            responseOut.flush();
            return new JSONObject(responseOut.toString());
        }
    }

    static JSONObject error(JSONObject res, int rowNumber, String fieldName) {
        JSONArray errors = res.optJSONArray("errors");
        if (errors == null) return null;
        for (int i = 0; i < errors.length(); i++) {
            JSONObject e = errors.getJSONObject(i);
            if ((e.optInt("rowNumber", -1) == rowNumber) && fieldName.equals(e.optString("fieldName"))) return e;
        }
        return null;
    }

    static JSONObject warning(JSONObject res, String type) {
        JSONArray w = res.optJSONArray("warnings");
        if (w == null) return null;
        for (int i = 0; i < w.length(); i++) if (type.equals(w.getJSONObject(i).optString("type"))) return w.getJSONObject(i);
        return null;
    }

    static boolean stamped(ImportTask t) {
        return t.getPassedParameters().optBoolean(ImportTask.PARAM_ANNOTATIONS_IMPORTED, false);
    }

    static final String[] BOXED = { "Encounter.mediaAsset0", "Encounter.mediaAsset0.bbox", "Encounter.mediaAsset0.iaClass" };

    @Test void validateOnlyChecksBoundsFromTheHeader() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "a.jpg", "10,10,200,10", "body" }).put("validateOnly", true));
        assertEquals(200, res.getInt("statusCode"));
        assertFalse(res.getBoolean("success"));
        assertEquals(1, res.getJSONArray("errors").length());
        JSONObject e = error(res, 0, "Encounter.mediaAsset0.bbox");
        assertNotNull(e);
        assertEquals("INVALID_VALUE", e.getString("type"));
        assertTrue(e.getString("details").contains("exceeds image 200x150"), e.toString());
    }

    @Test void boxTouchingTheEdgeIsAccepted() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "a.jpg", "0,0,200,150", "body" }, new Object[] { "b.jpg", "196,142,4,8", "body" }).put("validateOnly", true));
        assertTrue(res.getBoolean("success"), res.toString());
        assertNull(res.optJSONArray("warnings"));
        assertTrue(res.getBoolean("annotationsImported"));
    }

    @Test void photoBoxedOnOneRowAndNotAnotherIsRejectedOnTheUnboxedRow() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "a.jpg", "1,1,5,5", "body" }, new Object[] { "a.jpg", null, null }).put("validateOnly", true));
        assertNotNull(error(res, 1, "Encounter.mediaAsset0"), res.toString());
        assertNull(error(res, 0, "Encounter.mediaAsset0"));
        setUp();
        res = post(payload(BOXED, new Object[] { "a.jpg", null, null }, new Object[] { "a.jpg", "1,1,5,5", "body" }).put("validateOnly", true));
        assertNotNull(error(res, 0, "Encounter.mediaAsset0"), res.toString());
        assertNull(error(res, 1, "Encounter.mediaAsset0"));
    }

    @Test void identicalBoxOnTwoRowsIsADuplicate() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "a.jpg", "1,1,5,5", "body" }, new Object[] { "a.jpg", "1,1,5,5", "body" }).put("validateOnly", true));
        JSONObject e = error(res, 1, "Encounter.mediaAsset0.bbox");
        assertNotNull(e, res.toString());
        assertEquals("INVALID_DUPLICATE", e.getString("type"));
        setUp();
        res = post(payload(BOXED, new Object[] { "a.jpg", "1,1,5,5", "body" }, new Object[] { "a.jpg", "20,20,5,5", "body" }).put("validateOnly", true));
        assertTrue(res.getBoolean("success"), res.toString());
    }

    @Test void unboxedPhotosInABoxedImportAreAWarning() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "a.jpg", "1,1,5,5", "body" }, new Object[] { "b.jpg", null, null }).put("validateOnly", true));
        assertTrue(res.getBoolean("success"), res.toString());
        JSONObject w = warning(res, "UNBOXED_PHOTOS");
        assertNotNull(w, res.toString());
        assertEquals(List.of("b.jpg"), w.getJSONArray("filenames").toList());
        assertTrue(res.getBoolean("annotationsImported"));
    }

    @Test void emptyBoxColumnsMakeAnOrdinaryImport() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "a.jpg", null, null }, new Object[] { "b.jpg", null, null }).put("validateOnly", true));
        assertTrue(res.getBoolean("success"));
        assertFalse(res.getBoolean("annotationsImported"));
        assertNull(res.optJSONArray("warnings"));
        setUp();
        res = post(payload(BOXED, new Object[] { "a.jpg", null, null }).put("skipDetection", true));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals("complete", held.getStatus());
        assertFalse(stamped(held));
        assertTrue(handoffs.isEmpty());
        setUp();
        res = post(payload(BOXED, new Object[] { "a.jpg", null, null }));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals(1, handoffs.size());
        JSONObject bulk = handoffs.get(0).getJSONObject("bulkImport");
        assertEquals(1, bulk.length());
        assertEquals(1, bulk.getJSONArray(bulk.keys().next()).length());
        JSONObject env = IAGateway.buildImportEnvelope(held);
        assertTrue(env.has("mediaAssetIds"), env.toString());
        assertFalse(env.has("annotationMode"));
    }

    @Test void badMimeAndUnreadableHeaderFailValidateOnlyWhenBoxed() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "image-invalid-mime-type.jpg", "1,1,5,5", "body" }).put("validateOnly", true));
        JSONObject e = error(res, 0, "Encounter.mediaAsset0");
        assertNotNull(e, res.toString());
        assertTrue(e.getString("details").contains("is not a valid file"), e.toString());
        setUp();
        res = post(payload(BOXED, new Object[] { "magic.jpg", "1,1,5,5", "body" }).put("validateOnly", true));
        e = error(res, 0, "Encounter.mediaAsset0");
        assertNotNull(e, res.toString());
        assertTrue(e.getString("details").contains("image size could not be read"), e.toString());
        setUp();
        res = post(payload(new String[] { "Encounter.mediaAsset0" }, new Object[] { "image-invalid-mime-type.jpg" }, new Object[] { "magic.jpg" }).put("validateOnly", true));
        assertTrue(res.getBoolean("success"), "today's validate-only does not open un-boxed files: " + res);
    }

    @Test void headerReadableTruncatedPhotoPassesValidateOnly() throws Exception {
        JSONObject res = post(payload(BOXED, new Object[] { "trunc.png", "0,0,100,100", "body" }).put("validateOnly", true));
        assertTrue(res.getBoolean("success"), res.toString());
    }

    @Test void objectRowsAreCheckedToo() throws Exception {
        JSONObject row0 = new JSONObject().put("Encounter.mediaAsset0", "a.jpg").put("Encounter.mediaAsset0.bbox", "1,1,5,5").put("Encounter.mediaAsset0.iaClass", "body");
        JSONObject row1 = new JSONObject().put("Encounter.mediaAsset1", "a.jpg");
        for (JSONObject r : new JSONObject[] { row0, row1 }) for (int i = 0; i < BASE.length; i++) r.put(BASE[i], BASE_VALUES[i]);
        JSONObject p = new JSONObject().put("bulkImportId", BULK_ID).put("tolerance", new JSONObject()).put("validateOnly", true)
            .put("rows", new JSONArray().put(row0).put(row1));
        JSONObject res = post(p);
        assertNotNull(error(res, 1, "Encounter.mediaAsset1"), res.toString());
    }

    @Test void clientSuppliedAnnotationsImportedIsOverwritten() throws Exception {
        JSONObject res = post(payload(new String[] { "Encounter.mediaAsset0" }, new Object[] { "a.jpg" }).put("skipDetection", true).put("annotationsImported", true));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertFalse(stamped(held));
        assertEquals("complete", held.getStatus());
        assertTrue(handoffs.isEmpty());
        setUp();
        res = post(payload(BOXED, new Object[] { "a.jpg", "1,1,5,5", "body" }).put("skipDetection", true).put("annotationsImported", false));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertTrue(stamped(held));
    }

    @Test void boxedImportStampsTheTaskAndHandsOffUnderEverySkipCombination() throws Exception {
        for (boolean skipDetection : new boolean[] { false, true }) {
            for (boolean skipIdentification : new boolean[] { false, true }) {
                setUp();
                String label = "skipDetection=" + skipDetection + " skipIdentification=" + skipIdentification;
                JSONObject res = post(payload(BOXED, new Object[] { "a.jpg", "1,1,5,5", "body" }).put("skipDetection", skipDetection).put("skipIdentification", skipIdentification));
                assertEquals(200, res.getInt("statusCode"), label + ": " + res);
                assertTrue(res.getBoolean("annotationsImported"), label);
                assertTrue(stamped(held), label);
                assertEquals("processing-detection", held.getStatus(), label);
                assertEquals(1, handoffs.size(), label);
                assertEquals(skipDetection || skipIdentification, handoffs.get(0).getJSONObject("taskParameters").getBoolean("skipIdent"), label);
                assertEquals("extract-then-identify", IAGateway.buildImportEnvelope(held).getString("annotationMode"), label);
                assertEquals(boxIds(encounterWith("a.jpg")), envelopeIds(), label);
                assertEquals(1, envelopeIds().size(), label);
            }
        }
    }

    @Test void noSurvivingMediaLeavesTheTaskImportedWithoutAHandoff() throws Exception {
        failing.add("a.jpg");
        JSONObject res = post(tolerant(payload(BOXED, new Object[] { "a.jpg", "1,1,5,5", "body" })));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals("imported", held.getStatus());
        assertTrue(stamped(held));
        assertTrue(handoffs.isEmpty());
    }

    @Test void survivingUnboxedMediaStillHandsOffWhenEveryBoxedFileFailed() throws Exception {
        corrupt.add("trunc.png");
        JSONObject res = post(tolerant(payload(BOXED, new Object[] { "trunc.png", "0,0,100,100", "body" }, new Object[] { "b.jpg", null, null })));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals(1, handoffs.size());
        JSONObject env = IAGateway.buildImportEnvelope(held);
        assertEquals("extract-then-identify", env.getString("annotationMode"));
        assertEquals(0, env.getJSONArray("annotationIds").length());
    }

    @Test void corruptBoxedPhotoIsReportedAndItsBoxDroppedInATolerantImport() throws Exception {
        corrupt.add("trunc.png");
        JSONObject res = post(tolerant(payload(BOXED, new Object[] { "trunc.png", "0,0,100,100", "body" }, new Object[] { "a.jpg", "1,1,5,5", "body" })));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        boolean reported = false;
        for (Object o : res.getJSONArray("errors").toList()) {
            Map<?, ?> e = (Map<?, ?>)o;
            if ("trunc.png".equals(e.get("filename")) && Boolean.TRUE.equals(e.get("mediaAssetError"))) reported = true;
        }
        assertTrue(reported, res.toString());
        assertEquals(2, held.getEncounters().size());
        Encounter good = encounterWith("a.jpg");
        assertNotNull(good);
        assertEquals(1, boxIds(good).size());
        for (Encounter enc : held.getEncounters()) if (enc != good) assertTrue((enc.getAnnotations() == null) || enc.getAnnotations().isEmpty(), "the corrupt photo's slot and its box were skipped");
        assertEquals(boxIds(good), envelopeIds());
        assertEquals(1, handoffs.size());
    }

    @Test void tolerantImportKeepsTheValidBoxedFileWhenAnotherBoxedFileIsBadMime() throws Exception {
        JSONObject res = post(tolerant(payload(BOXED, new Object[] { "image-invalid-mime-type.jpg", "1,1,5,5", "body" }, new Object[] { "a.jpg", "1,1,5,5", "body" })));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals(1, res.getJSONArray("errors").length(), res.toString());
        JSONObject e = error(res, 0, "Encounter.mediaAsset0");
        assertNotNull(e, res.toString());
        assertTrue(e.getString("details").contains("is not a valid file"));
        Encounter good = encounterWith("a.jpg");
        assertNotNull(good, "the valid boxed file was created and imported");
        assertEquals(boxIds(good), envelopeIds());
        assertEquals(1, envelopeIds().size());
        assertEquals(1, handoffs.size());
    }

    @Test void tolerantImportNeverCreatesAClasslessBox() throws Exception {
        String[] fields = { "Encounter.mediaAsset0", "Encounter.mediaAsset0.bbox", "Encounter.mediaAsset0.iaClass", "Encounter.genus", "Encounter.specificEpithet", "Encounter.year" };
        JSONObject res = post(tolerant(payloadRaw(fields,
            new Object[] { "a.jpg", "1,1,5,5", null, "Genus", "species", 2020 },      // class missing
            new Object[] { "b.jpg", "1,1,5,5", "fin", "Genus", "species", 2020 },     // class rejected (identification on)
            new Object[] { "c.jpg", "1,1,5,5", "body", "Genus", "nope", 2020 },       // taxonomy rejected
            new Object[] { "e.jpg", "1,1,5,5", "body", "Genus", null, 2020 },         // epithet missing
            new Object[] { "f.jpg", "1,1,5,5", "body", null, "species", 2020 },       // genus missing
            new Object[] { "d.jpg", "1,1,5,5", "body", "Genus", "species", 2020 })));  // valid
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals("REQUIRED_VALUE", error(res, 0, "Encounter.mediaAsset0.iaClass").getString("type"));
        assertEquals("INVALID_VALUE", error(res, 1, "Encounter.mediaAsset0.iaClass").getString("type"));
        assertNotNull(error(res, 2, "Encounter.genus"));
        assertNotNull(error(res, 2, "Encounter.specificEpithet"));
        assertEquals("REQUIRED_VALUE", error(res, 3, "Encounter.specificEpithet").getString("type"));
        assertEquals("REQUIRED_VALUE", error(res, 4, "Encounter.genus").getString("type"));
        assertEquals(6, held.getEncounters().size());
        for (String rejected : new String[] { "a.jpg", "b.jpg", "c.jpg", "e.jpg", "f.jpg" }) {
            Encounter enc = encounterWith(rejected);
            assertNotNull(enc, rejected);
            assertEquals(1, enc.getAnnotations().size(), rejected);
            assertFalse(enc.getAnnotations().get(0).isImportedBox(), rejected + " must not become a classless box");
            assertNull(enc.getAnnotations().get(0).getIAClass(), rejected);
        }
        assertEquals(boxIds(encounterWith("d.jpg")), envelopeIds());
        assertEquals(1, envelopeIds().size());
        assertTrue(stamped(held));
    }

    @Test void tolerantImportDoesNotLendAReusedEncountersTaxonomyToARejectedRow() throws Exception {
        String x = "00000000-0000-4000-8000-00000000000a", y = "00000000-0000-4000-8000-00000000000b";
        String[] fields = { "Encounter.id", "Encounter.mediaAsset0", "Encounter.mediaAsset0.bbox", "Encounter.mediaAsset0.iaClass", "Encounter.genus", "Encounter.specificEpithet", "Encounter.year" };
        JSONObject res = post(tolerant(payloadRaw(fields,
            new Object[] { x, "a.jpg", null, null, "Genus", "species", 2020 },
            new Object[] { x, "b.jpg", "1,1,5,5", "body", null, null, 2020 },
            new Object[] { y, "c.jpg", "1,1,5,5", "body", "Genus", "species", 2020 })));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals("REQUIRED_VALUE", error(res, 1, "Encounter.genus").getString("type"));
        assertEquals("REQUIRED_VALUE", error(res, 1, "Encounter.specificEpithet").getString("type"));
        assertEquals(2, held.getEncounters().size());
        Encounter shared = encounterWith("a.jpg");
        assertNotNull(shared);
        assertEquals(x, shared.getId());
        assertEquals("Genus", shared.getGenus());
        assertEquals("species", shared.getSpecificEpithet());
        assertEquals(2, shared.getAnnotations().size());
        for (Annotation a : shared.getAnnotations()) assertFalse(a.isImportedBox(), "row B's box must not borrow row A's taxonomy");
        assertEquals(boxIds(encounterWith("c.jpg")), envelopeIds());
        assertEquals(1, envelopeIds().size());
        assertTrue(stamped(held));
    }

    @Test void unboxedImportKeepsTodaysSkipDetectionBehavior() throws Exception {   // (regression)
        JSONObject res = post(payload(new String[] { "Encounter.mediaAsset0" }, new Object[] { "a.jpg" }).put("skipDetection", true));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertEquals("complete", held.getStatus());
        assertTrue(handoffs.isEmpty());
        assertFalse(res.getBoolean("annotationsImported"));
    }
}
