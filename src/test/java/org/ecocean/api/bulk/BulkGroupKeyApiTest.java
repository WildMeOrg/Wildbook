package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.IAJsonProperties;
import org.ecocean.ImageFixtures;
import org.ecocean.Occurrence;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.User;
import org.ecocean.api.bulk.BulkImporter;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.ia.IA;
import org.ecocean.media.AssetStore;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.AdvisoryLock;
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
 * Issue #1789, P3 task 7: the bulk-import servlet collapses rows that share an
 * Encounter.groupKey into one encounter row. Harness as BulkBoxImportApiTest (mocked
 * Shepherd/PMF, real fixture files in a temp upload dir, in-memory MediaAssets); the importer
 * is always a construction mock that records what it was given.
 */
class BulkGroupKeyApiTest {
    static final String BULK_ID = "00000000-0000-0000-0000-00000000a789";
    static final String KEY = "Encounter.groupKey";
    static final String[] BASE = { "Encounter.genus", "Encounter.specificEpithet", "Encounter.year" };
    static final Object[] BASE_VALUES = { "Genus", "species", 2020 };
    static final String GOLDEN = "src/test/resources/bulk-import/ungrouped-response-golden.json";
    @TempDir static Path uploadDir;

    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    BulkImport apiServlet;
    StringWriter responseOut;
    PersistenceManager mockPM;
    PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
    ImportTask held;
    boolean reserved;                          // an ImportTask was persisted
    User user;
    Occurrence occ;
    final Map<String, MediaAsset> assets = new HashMap<String, MediaAsset>();
    final List<String> created = new ArrayList<String>();   // every filename makeMediaAsset was asked for
    final Set<String> invalidFiles = new HashSet<String>(); // Util.fastFileValidation says no
    final Set<String> nullAssets = new HashSet<String>();   // makeMediaAsset returns null
    // what each (mocked) BulkImporter construction received
    final List<List<Object> > importerArgs = new ArrayList<List<Object> >();
    int nextAssetId = 100;

    @BeforeAll static void fixtures() throws Exception {
        BufferedImage img = ImageFixtures.noise(200, 150, 42);
        for (String n : new String[] { "a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg" })
            ImageFixtures.write(img, "jpeg", uploadDir.resolve(n).toFile(), false);
    }

    @BeforeEach void setUp() throws Exception {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        apiServlet = new BulkImport();
        apiServlet.launcher = Runnable::run;
        responseOut = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseOut));
        user = mock(User.class);
        when(user.getId()).thenReturn("00000000-0000-4000-8000-0000000000aa");
        when(user.getUsername()).thenReturn("fake-username");
        occ = mock(Occurrence.class);
        held = new ImportTask(null, BULK_ID);
        reserved = false;
        mockPM = mock(PersistenceManager.class);
        doAnswer(inv -> { held = inv.getArgument(0); reserved = true; return held; }).when(mockPM).makePersistent(any(ImportTask.class));
        when(mockPM.getObjectById(eq(User.class), any())).thenReturn(user);
        assets.clear();
        created.clear();
        invalidFiles.clear();
        nullAssets.clear();
        importerArgs.clear();
        JSONObject json = new JSONObject().put("Genus", new JSONObject().put("species", new JSONObject()
            .put("body", new JSONObject().put("_id_conf", new JSONArray().put(new JSONObject().put("default", true))))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(json);
        TestIAJsonPropertiesUtil.override(iac);
    }

    @AfterEach void tearDown() {
        TestIAJsonPropertiesUtil.override(null);
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

    // ---- payloads ----

    static JSONObject obj(Object... kv) {
        JSONObject row = new JSONObject();
        for (int i = 0; i < kv.length; i += 2) row.put((String)kv[i], kv[i + 1]);
        return row;
    }

    /** an object row with the BASE species + year and the given cells */
    static JSONObject row(Object... kv) {
        JSONObject r = obj(kv);
        for (int i = 0; i < BASE.length; i++) if (!r.has(BASE[i])) r.put(BASE[i], BASE_VALUES[i]);
        return r;
    }

    static JSONObject objects(JSONObject... rows) {
        JSONObject p = new JSONObject().put("bulkImportId", BULK_ID).put("tolerance", new JSONObject());
        JSONArray rs = new JSONArray();
        for (JSONObject r : rows) rs.put(r);
        return p.put("rows", rs);
    }

    static JSONObject positional(String[] fields, Object[]... rows) {
        JSONObject p = new JSONObject().put("bulkImportId", BULK_ID).put("tolerance", new JSONObject());
        p.put("fieldNames", new JSONArray(fields));
        JSONArray rs = new JSONArray();
        for (Object[] row : rows) {
            JSONArray r = new JSONArray();
            for (Object v : row) r.put(v == null ? JSONObject.NULL : v);
            rs.put(r);
        }
        return p.put("rows", rs);
    }

    static JSONObject validateOnly(JSONObject p) {
        return p.put("validateOnly", true);
    }

    static JSONObject tolerant(JSONObject p) {
        p.getJSONObject("tolerance").put("failImportOnError", false);
        return p;
    }

    static JSONObject background(JSONObject p) {
        return p.put("processInBackground", true);
    }

    // ---- harness ----

    JSONObject post(JSONObject payload) throws Exception {
        return postRaw(payload.toString());
    }

    /** posts the body exactly as given (a JSONObject cannot carry a duplicate key) */
    JSONObject postRaw(String body) throws Exception {
        responseOut.getBuffer().setLength(0);
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        // registered before the static mocks and closed after them (Mockito resolves mocks in LIFO order)
        try (MockedConstruction<BulkImporter> importer = mockConstruction(BulkImporter.class, (mock, ctx) -> {
                 importerArgs.add(new ArrayList<Object>(ctx.arguments()));
                 when(mock.createImport()).thenReturn(new JSONObject());
                 when(mock.getEncounters()).thenReturn(new ArrayList<org.ecocean.Encounter>());
                 when(mock.getTouchedIndividualIds()).thenReturn(new HashSet<String>());
             })) {
            return postWithMocks();
        }
    }

    private JSONObject postWithMocks() throws Exception {
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (mock, ctx) -> {
                 when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                 when(mock.getUser(any(String.class))).thenAnswer(inv -> "nobody".equals(inv.getArgument(0)) ? null : user);
                 when(mock.getUserByUUID(any(String.class))).thenReturn(user);
                 when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
                 when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
                 when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
                 when(mock.findImportTask(any())).thenReturn(null);
                 when(mock.getImportTask(any(String.class))).thenAnswer(inv -> held);
                 when(mock.isDBTransactionActive()).thenReturn(true);
                 when(mock.commitDBTransactionWithStatus()).thenReturn(true);
                 when(mock.getPM()).thenReturn(mockPM);
                 when(mock.getContext()).thenReturn("context0");
             });
             MockedStatic<AdvisoryLock> lock = mockStatic(AdvisoryLock.class, CALLS_REAL_METHODS);
             MockedStatic<UploadedFiles> files = mockStatic(UploadedFiles.class, CALLS_REAL_METHODS);
             MockedStatic<ShepherdPMF> pmf = mockStatic(ShepherdPMF.class);
             MockedStatic<FeatureType> ft = mockStatic(FeatureType.class);
             MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS);
             MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<AssetStore> stores = mockStatic(AssetStore.class, CALLS_REAL_METHODS);
             MockedStatic<org.ecocean.Util> util = mockStatic(org.ecocean.Util.class, CALLS_REAL_METHODS)) {
            lock.when(() -> AdvisoryLock.tryXact(any(), anyLong())).thenReturn(true);
            util.when(() -> org.ecocean.Util.fastFileValidation(any(File.class))).thenAnswer(inv ->
                invalidFiles.contains(((File)inv.getArgument(0)).getName()) ? false : inv.callRealMethod());
            files.when(() -> UploadedFiles.getUploadDir(any(HttpServletRequest.class), any(String.class))).thenReturn(uploadDir.toFile());
            files.when(() -> UploadedFiles.makeMediaAsset(any(String.class), any(File.class), any(Shepherd.class))).thenAnswer(inv -> {
                File f = inv.getArgument(1);
                created.add(f.getName());
                if (nullAssets.contains(f.getName())) return null;
                return assetFor(f);
            });
            stores.when(() -> AssetStore.getDefault(any(Shepherd.class))).thenReturn(mock(AssetStore.class));
            pmf.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
            ft.when(() -> FeatureType.load(eq("org.ecocean.boundingBox"), any(Shepherd.class))).thenReturn(new FeatureType("org.ecocean.boundingBox"));
            gateway.when(() -> IAGateway.handleBulkImport(any(JSONObject.class), any(JSONObject.class), any(Shepherd.class), anyString(), any()))
                .thenAnswer(inv -> new JSONObject().put("success", true));
            ia.when(() -> IA.getBaseURL(anyString())).thenReturn("https://base.test");
            apiServlet.doPost(mockRequest, mockResponse);
            responseOut.flush();
            return new JSONObject(responseOut.toString());
        }
    }

    static JSONObject error(JSONObject res, int rowNumber, String fieldName) {
        JSONArray errors = res.optJSONArray("errors");
        if (errors == null) return null;
        for (int i = 0; i < errors.length(); i++) {
            JSONObject e = errors.optJSONObject(i);
            if (e == null) continue;
            if ((e.optInt("rowNumber", -1) == rowNumber) && fieldName.equals(e.optString("fieldName"))) return e;
        }
        return null;
    }

    static List<JSONObject> errorsOfType(JSONObject res, String type) {
        List<JSONObject> rtn = new ArrayList<JSONObject>();
        JSONArray errors = res.optJSONArray("errors");
        if (errors == null) return rtn;
        for (int i = 0; i < errors.length(); i++) {
            JSONObject e = errors.optJSONObject(i);
            if ((e != null) && type.equals(e.optString("type"))) rtn.add(e);
        }
        return rtn;
    }

    static List<Integer> rowNumbers(JSONObject res) {
        List<Integer> rtn = new ArrayList<Integer>();
        JSONArray errors = res.optJSONArray("errors");
        if (errors == null) return rtn;
        for (int i = 0; i < errors.length(); i++) {
            JSONObject e = errors.getJSONObject(i);
            if (e.has("rowNumber")) rtn.add(e.getInt("rowNumber"));
        }
        return rtn;
    }

    void assertNothingCreated(JSONObject res) {
        assertFalse(reserved, "no ImportTask may be reserved: " + res);
        assertTrue(created.isEmpty(), "no media may be created: " + created);
        assertEquals(0, importerArgs.size(), "BulkImporter must not be constructed");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object> > importerRows() {
        assertEquals(1, importerArgs.size());
        return (List<Map<String, Object> >)importerArgs.get(0).get(1);
    }

    // ---- tests ----

    @Test void validateOnlyReportsConflictOnSourceCells() throws Exception {
        JSONObject res = post(validateOnly(objects(
            row(KEY, "G1", "Encounter.sex", "male", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "g1", "Encounter.sex", "female", "Encounter.mediaAsset0", "b.jpg"))));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertFalse(res.getBoolean("success"));
        JSONObject e0 = error(res, 0, "Encounter.sex");
        JSONObject e1 = error(res, 1, "Encounter.sex");
        assertNotNull(e0, res.toString());
        assertNotNull(e1, res.toString());
        assertEquals("G1", e0.getString("groupKey"));
        assertEquals("g1", e1.getString("groupKey"));
        assertEquals(BulkValidatorException.TYPE_GROUP_CONFLICT, e0.getString("type"));
        assertEquals(2, res.getJSONArray("errors").length(), res.toString());
    }

    @Test void conflictReturns400AndCreatesNothing() throws Exception {
        JSONObject res = post(objects(
            row(KEY, "G1", "Encounter.sex", "male", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G1", "Encounter.sex", "female", "Encounter.mediaAsset0", "b.jpg")));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertFalse(res.getBoolean("success"));
        assertNotNull(error(res, 1, "Encounter.sex"), res.toString());
        assertNothingCreated(res);
    }

    @Test void groupedUnknownFieldFailsEveryMode() throws Exception {
        JSONObject[] rows = {
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg", "Bogus.field", "x") };
        JSONObject res = post(validateOnly(objects(rows)));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertFalse(res.getBoolean("success"), res.toString());
        JSONObject e = error(res, 1, "Bogus.field");
        assertNotNull(e, res.toString());
        assertEquals("G", e.getString("groupKey"));
        setUp();
        res = post(objects(rows));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertNothingCreated(res);
        setUp();
        res = post(background(objects(rows)));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertNothingCreated(res);
    }

    @Test void toleranceNeverDropsAGroupedCell() throws Exception {
        JSONObject res = post(tolerant(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg", "Encounter.sex", "x"))));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        JSONObject e = error(res, 1, "Encounter.sex");
        assertNotNull(e, res.toString());
        assertEquals("G", e.getString("groupKey"));
        assertNothingCreated(res);
    }

    @Test void toleranceStillAppliesToUngroupedRows() throws Exception {
        JSONObject res = post(tolerant(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg"),
            row("Encounter.mediaAsset0", "c.jpg", "Encounter.sex", "x"))));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        JSONObject e = error(res, 2, "Encounter.sex");
        assertNotNull(e, res.toString());
        assertFalse(e.has("groupKey"));
        assertEquals(1, importerArgs.size());
        assertEquals(3, res.getInt("numberRows"));
        assertEquals(2, res.getInt("numberRowsValid"));
    }

    @Test void missingPhotoInGroupFailsWholeImport() throws Exception {
        JSONObject res = post(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "nope.jpg")));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        JSONObject e = error(res, 1, "Encounter.mediaAsset0");
        assertNotNull(e, res.toString());
        assertEquals("G", e.getString("groupKey"));
        assertTrue(e.getString("details").contains("nope.jpg"), e.toString());
        assertNull(error(res, 0, "Encounter.mediaAsset0"), res.toString());
        assertNothingCreated(res);
        // tolerance does not save it
        setUp();
        res = post(tolerant(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "nope.jpg"))));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertNothingCreated(res);
    }

    @Test void invalidUnboxedGroupedFileFailsValidateOnly() throws Exception {
        invalidFiles.add("b.jpg");
        JSONObject res = post(validateOnly(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg"),
            row("Encounter.mediaAsset0", "c.jpg"))));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertFalse(res.getBoolean("success"), res.toString());
        List<JSONObject> bad = errorsOfType(res, "INVALID_IMAGE");
        assertEquals(1, bad.size(), res.toString());
        assertEquals("b.jpg", bad.get(0).getString("filename"));
        assertFalse(bad.get(0).has("rowNumber"));
        assertEquals(3, res.getInt("numberRows"));
        assertEquals(2, res.getInt("numberRowsValid"), res.toString());
        // an ungrouped invalid file is still not opened by validate-only (today's behaviour)
        setUp();
        invalidFiles.add("c.jpg");
        res = post(validateOnly(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row("Encounter.mediaAsset0", "c.jpg"))));
        assertTrue(res.getBoolean("success"), res.toString());
    }

    @Test void backgroundNullAssetFailsWithoutImporting() throws Exception {
        nullAssets.add("b.jpg");
        JSONObject res = post(background(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg"))));
        assertEquals(200, res.getInt("statusCode"), res.toString());   // backgrounded
        assertEquals("failed", held.getStatus());
        assertEquals(0, importerArgs.size(), "BulkImporter must not be constructed");
        assertTrue(held.getErrors().toString().contains("b.jpg could not be imported"), String.valueOf(held.getErrors()));
        // foreground: 400, nothing imported, regardless of tolerance
        setUp();
        nullAssets.add("b.jpg");
        res = post(tolerant(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg"))));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertEquals(0, importerArgs.size());
        List<JSONObject> bad = errorsOfType(res, "INVALID_IMAGE");
        assertEquals(1, bad.size(), res.toString());
        assertEquals("b.jpg", bad.get(0).getString("filename"));
        assertEquals("b.jpg could not be imported", bad.get(0).getString("details"));
    }

    @Test void sourceRowCountsReported() throws Exception {
        JSONObject res = post(validateOnly(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "c.jpg"),
            row("Encounter.mediaAsset0", "d.jpg"))));
        assertTrue(res.getBoolean("success"), res.toString());
        assertEquals(4, res.getInt("numberRows"));
        assertEquals(4, res.getInt("numberRowsValid"));
    }

    @Test void errorsInSourceOrderForInterleavedGroups() throws Exception {
        JSONObject res = post(validateOnly(objects(
            row(KEY, "G1", "Encounter.sex", "male", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G2", "Encounter.sex", "male", "Encounter.mediaAsset0", "b.jpg"),
            row(KEY, "G1", "Encounter.sex", "female", "Encounter.mediaAsset0", "c.jpg"),
            row(KEY, "G2", "Encounter.sex", "female", "Encounter.mediaAsset0", "d.jpg"))));
        assertEquals(List.of(0, 1, 2, 3), rowNumbers(res), res.toString());
    }

    @Test void boxMessageCitesSourceRow() throws Exception {
        JSONObject res = post(validateOnly(objects(
            row("Encounter.mediaAsset0", "b.jpg"),
            row("Encounter.mediaAsset0", "c.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "d.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5",
                "Encounter.mediaAsset0.iaClass", "body"),
            row("Encounter.mediaAsset0", "a.jpg"))));
        JSONObject e = error(res, 4, "Encounter.mediaAsset0");
        assertNotNull(e, res.toString());
        assertTrue(e.getString("details").contains("has a box on row 3 but none here"), e.toString());
    }

    @Test void duplicateBoxBlamedInSourceOrder() throws Exception {
        JSONObject res = post(tolerant(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row("Encounter.mediaAsset0", "b.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5",
                "Encounter.mediaAsset0.iaClass", "body"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5",
                "Encounter.mediaAsset0.iaClass", "body"))));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        JSONObject e = error(res, 2, "Encounter.mediaAsset0.bbox");
        assertNotNull(e, res.toString());
        assertEquals(BulkValidatorException.TYPE_INVALID_DUPLICATE, e.getString("type"));
        assertEquals("G", e.getString("groupKey"));
        assertNull(error(res, 1, "Encounter.mediaAsset0.bbox"), res.toString());
        assertNothingCreated(res);
    }

    @Test void malformedRowRejectedWithIndex() throws Exception {
        JSONObject p = positional(new String[] { "Encounter.genus", "Encounter.specificEpithet", "Encounter.year" },
            new Object[] { "Genus", "species", 2020 }, new Object[] { "Genus", "species", 2021 });
        p.getJSONArray("rows").put(new JSONObject().put("Encounter.genus", "Genus"));
        JSONObject res = post(validateOnly(p));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        JSONObject e = res.getJSONArray("errors").getJSONObject(0);
        assertEquals(BulkValidatorException.TYPE_MALFORMED_ROW, e.getString("type"));
        assertEquals(2, e.getInt("rowNumber"));
        // an object request with a non-object row, and a null row
        setUp();
        JSONObject q = objects(row("Encounter.mediaAsset0", "a.jpg"));
        q.getJSONArray("rows").put(new JSONArray().put("x"));
        res = post(q);
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertEquals(1, res.getJSONArray("errors").getJSONObject(0).getInt("rowNumber"));
        assertNothingCreated(res);
        setUp();
        q = objects(row("Encounter.mediaAsset0", "a.jpg"));
        q.getJSONArray("rows").put(JSONObject.NULL);
        res = post(q);
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertEquals(BulkValidatorException.TYPE_MALFORMED_ROW, res.getJSONArray("errors").getJSONObject(0).getString("type"));
    }

    @Test void duplicateJsonKeyIs400() throws Exception {
        JSONObject res = postRaw("{\"bulkImportId\":\"x\",\"rows\":[{\"Encounter.genus\":\"A\",\"Encounter.genus\":\"B\"}]}");
        assertEquals(400, res.getInt("statusCode"), res.toString());
        JSONObject e = res.getJSONArray("errors").getJSONObject(0);
        assertEquals(BulkValidatorException.TYPE_MALFORMED_ROW, e.getString("type"));
        assertTrue(e.getString("details").contains("Encounter.genus"), e.toString());
        assertNothingCreated(res);
    }

    @Test void positionalAndObjectRequestsCollapseAlike() throws Exception {
        String[] fields = { KEY, "Encounter.genus", "Encounter.specificEpithet", "Encounter.year",
            "Encounter.sex", "Encounter.mediaAsset0", "Encounter.verbatimLocality" };
        Object[][] rows = {
            { "G", "Genus", "species", 2020, "male", "a.jpg", "" },
            { "g", null, "", null, "", "b.jpg", "reef" },
            { "H", "Genus", "species", 2021, "female", "c.jpg", null } };
        JSONObject[] objRows = {
            obj(KEY, "G", "Encounter.genus", "Genus", "Encounter.specificEpithet", "species", "Encounter.year", 2020,
                "Encounter.sex", "male", "Encounter.mediaAsset0", "a.jpg"),
            obj(KEY, "g", "Encounter.mediaAsset0", "b.jpg", "Encounter.verbatimLocality", "reef"),
            obj(KEY, "H", "Encounter.genus", "Genus", "Encounter.specificEpithet", "species", "Encounter.year", 2021,
                "Encounter.sex", "female", "Encounter.mediaAsset0", "c.jpg") };
        post(positional(fields, rows));
        Map<String, String> fromPositional = stored(importerRows());
        setUp();
        post(objects(objRows));
        Map<String, String> fromObjects = stored(importerRows());
        assertEquals(fromPositional, fromObjects);
        assertTrue(fromObjects.containsKey("0/Encounter.mediaAsset1"), fromObjects.toString());
        assertEquals("reef", fromObjects.get("0/Encounter.verbatimLocality"));
        // the same conflict reported alike
        setUp();
        rows[1][4] = "female";
        JSONObject a = post(validateOnly(positional(fields, rows)));
        setUp();
        objRows[1].put("Encounter.sex", "female");
        JSONObject b = post(validateOnly(objects(objRows)));
        assertTrue(a.getJSONArray("errors").similar(b.getJSONArray("errors")), a + "\n" + b);
        assertEquals(a.getInt("numberRowsValid"), b.getInt("numberRowsValid"));
    }

    // merged row index / field -> stored value, every validated non-null value
    static Map<String, String> stored(List<Map<String, Object> > rows) {
        Map<String, String> rtn = new TreeMap<String, String>();
        for (int i = 0; i < rows.size(); i++) {
            for (Map.Entry<String, Object> e : rows.get(i).entrySet()) {
                if (!(e.getValue() instanceof BulkValidator)) continue;
                BulkValidator bv = (BulkValidator)e.getValue();
                if (bv.getValue() != null) rtn.put(i + "/" + e.getKey(), bv.getValueString());
            }
        }
        return rtn;
    }

    @Test void blankOutOfRangeHeaderRejectedBothFormats() throws Exception {
        JSONObject res = post(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset200", ""),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg")));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertNotNull(error(res, 0, "Encounter.mediaAsset200"), res.toString());
        assertNothingCreated(res);
        setUp();
        String[] fields = { KEY, "Encounter.genus", "Encounter.specificEpithet", "Encounter.year",
            "Encounter.mediaAsset0", "Encounter.mediaAsset200" };
        res = post(positional(fields, new Object[] { "G", "Genus", "species", 2020, "a.jpg", "" },
            new Object[] { "G", "Genus", "species", 2020, "b.jpg", null }));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertNothingCreated(res);
    }

    @Test void objectRowIndexAliasIs400EvenTolerant() throws Exception {
        JSONObject res = post(tolerant(objects(
            row("Encounter.mediaAsset01", "a.jpg", "Encounter.mediaAsset1", "b.jpg"))));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        JSONObject e = res.getJSONArray("errors").getJSONObject(0);
        assertEquals(BulkValidatorException.TYPE_INVALID_DUPLICATE, e.getString("type"));
        assertEquals(0, e.getInt("rowNumber"));
        assertTrue(Set.of("Encounter.mediaAsset01", "Encounter.mediaAsset1").contains(e.getString("fieldName")));
        assertNothingCreated(res);
    }

    @Test void blankUnknownColumnOnGroupDefaultIsWarning() throws Exception {
        JSONObject res = post(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg", "Bogus.field", "")));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        JSONArray w = res.getJSONArray("warnings");
        assertEquals(BulkValidatorException.TYPE_UNKNOWN_FIELDNAME, w.getJSONObject(0).getString("type"));
        assertEquals(1, importerArgs.size());
    }

    @Test void blankUnknownColumnOnGroupStrictIsFatal() throws Exception {
        JSONObject p = objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg", "Bogus.field", ""));
        p.getJSONObject("tolerance").put("badFieldnamesAreWarnings", false);
        JSONObject res = post(new JSONObject(p.toString()));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        JSONObject e = error(res, 1, "Bogus.field");
        assertNotNull(e, res.toString());
        assertEquals("G", e.getString("groupKey"));
        assertNothingCreated(res);
        setUp();
        res = post(tolerant(new JSONObject(p.toString())));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertNothingCreated(res);
    }

    @Test void yearAndMillisInDifferentGroupsRejectedBothEncodings() throws Exception {
        String millis = "Encounter.dateInMilliseconds";
        String[] fields = { KEY, "Encounter.genus", "Encounter.specificEpithet", "Encounter.year", millis,
            "Encounter.mediaAsset0" };
        JSONObject res = post(positional(fields, new Object[] { "A", "Genus", "species", 2020, null, "a.jpg" },
            new Object[] { "B", "Genus", "species", null, 1600000000000L, "b.jpg" }));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertEquals(BulkValidatorException.TYPE_INVALID_SYNONYM, res.getJSONArray("errors").getJSONObject(0).getString("type"));
        JSONObject posErrors = res;
        setUp();
        JSONObject a = obj(KEY, "A", "Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            "Encounter.year", 2020, "Encounter.mediaAsset0", "a.jpg");
        JSONObject b = obj(KEY, "B", "Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            millis, 1600000000000L, "Encounter.mediaAsset0", "b.jpg");
        res = post(objects(a, b));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertTrue(posErrors.getJSONArray("errors").similar(res.getJSONArray("errors")), posErrors + "\n" + res);
        assertNothingCreated(res);
        // a grouped year row next to an ungrouped millis row
        setUp();
        JSONObject ungrouped = new JSONObject(b.toString());
        ungrouped.remove(KEY);
        res = post(objects(a, ungrouped));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        assertEquals(BulkValidatorException.TYPE_INVALID_SYNONYM, res.getJSONArray("errors").getJSONObject(0).getString("type"));
        // the blank cell counts: the key is on the sheet
        setUp();
        JSONObject blankMillis = new JSONObject(a.toString()).put(millis, "");
        JSONObject c = obj(KEY, "C", "Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            "Encounter.year", 2021, "Encounter.mediaAsset0", "c.jpg");
        res = post(validateOnly(objects(blankMillis, c)));
        assertEquals(400, res.getInt("statusCode"), res.toString());
        // no groupKey at all: today's per-row result
        setUp();
        JSONObject a2 = new JSONObject(a.toString());
        a2.remove(KEY);
        res = post(validateOnly(objects(a2, ungrouped)));
        assertEquals(200, res.getInt("statusCode"), res.toString());
        assertTrue(res.getBoolean("success"), res.toString());
    }

    @Test void sparseContinuationMissingClassNamesSourceSlot() throws Exception {
        JSONObject res = post(validateOnly(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5",
                "Encounter.mediaAsset0.iaClass", "body"),
            row(KEY, "G", "Encounter.mediaAsset7", "b.jpg", "Encounter.mediaAsset7.bbox", "1,1,5,5"))));
        JSONObject e = error(res, 1, "Encounter.mediaAsset7.bbox");
        assertNotNull(e, res.toString());
        assertEquals("G", e.getString("groupKey"));
        assertTrue(e.getString("details").contains("Encounter.mediaAsset7.iaClass"), e.toString());
        assertFalse(e.toString().contains("mediaAsset1"), e.toString());
    }

    @Test void ungroupedUploadResponseUnchanged() throws Exception {
        JSONObject golden = new JSONObject(new String(Files.readAllBytes(Paths.get(GOLDEN)), StandardCharsets.UTF_8));
        for (String name : new TreeMap<String, Object>(golden.toMap()).keySet()) {
            setUp();
            JSONObject res = post(ungroupedRequest(name));
            res.remove("processingTime");
            JSONObject want = golden.getJSONObject(name);
            assertTrue(want.similar(res), name + "\nwant " + want + "\ngot  " + res);
        }
    }

    /** the requests whose responses the golden file holds, as recorded before grouping existed */
    JSONObject ungroupedRequest(String name) {
        switch (name) {
        case "objectTolerantInvalidFile":
            invalidFiles.add("c.jpg");
            return tolerant(objects(
                row("Encounter.mediaAsset0", "a.jpg", "Encounter.sex", "x"),
                row("Encounter.mediaAsset0", "c.jpg")));
        case "objectValidateOnly":
            return validateOnly(objects(
                row("Encounter.sex", "male", "Encounter.mediaAsset0", "a.jpg", "Bogus.field", "x"),
                row("Encounter.sex", "x", "Encounter.mediaAsset0", "missing.jpg", "Bogus.field", "y"),
                row("Encounter.mediaAsset0", "b.jpg", "Encounter.submitterID", "nobody"),
                row("Encounter.mediaAsset0", "c.jpg", "Encounter.year", "")));
        case "objectTolerantImport":
            return tolerant(objects(
                row("Encounter.sex", "male", "Encounter.mediaAsset0", "a.jpg", "Bogus.field", "x"),
                row("Encounter.sex", "x", "Encounter.mediaAsset0", "missing.jpg"),
                row("Encounter.mediaAsset0", "b.jpg", "Encounter.mediaAsset1", "c.jpg")));
        case "positionalStrict":
            return positional(new String[] { "Encounter.genus", "Encounter.specificEpithet", "Encounter.year",
                "Encounter.mediaAsset0", "Encounter.mediaAsset0.bbox", "Encounter.mediaAsset0.iaClass", "Encounter.sex" },
                new Object[] { "Genus", "species", 2020, "a.jpg", "1,1,5,5", "body", "male" },
                new Object[] { "Genus", "species", 2021, "a.jpg", null, null, "female" },
                new Object[] { "Genus", "species", null, "d.jpg", "1,1,500,5", "body", null });
        case "positionalImport":
            return positional(new String[] { "Encounter.genus", "Encounter.specificEpithet", "Encounter.year",
                "Encounter.mediaAsset0", "Encounter.sex", "Encounter.groupKey" },
                new Object[] { "Genus", "species", 2020, "a.jpg", "male", "" },
                new Object[] { "Genus", "species", 2021, "b.jpg", "female", null });
        default:
            throw new IllegalArgumentException(name);
        }
    }

    @Test void blankOwnerUsesEitherDefault() throws Exception {
        post(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg", "Encounter.submitterID", "  ")));
        Object bv = importerRows().get(0).get("Encounter.submitterID");
        assertTrue(bv instanceof BulkValidator, String.valueOf(bv));
        assertEquals("fake-username", ((BulkValidator)bv).getValueString());
        setUp();
        post(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg")).put("nullSubmitterIsPublic", true));
        bv = importerRows().get(0).get("Encounter.submitterID");
        assertEquals("public", ((BulkValidator)bv).getValueString());
    }

    @Test void importerGetsExactSlotModeAndGroupedIndexes() throws Exception {
        // the sheet's only .bbox column is blank: the merged rows have none, the mode still holds
        post(objects(
            row("Encounter.mediaAsset0", "c.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", ""),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg")));
        assertEquals(1, importerArgs.size());
        List<Object> args = importerArgs.get(0);
        assertEquals(7, args.size());
        assertEquals(Boolean.TRUE, args.get(5));
        assertEquals(Set.of(1), args.get(6));
        // background passes them too
        setUp();
        post(background(objects(
            row(KEY, "G", "Encounter.mediaAsset0", "a.jpg"),
            row(KEY, "G", "Encounter.mediaAsset0", "b.jpg"))));
        assertEquals(1, importerArgs.size());
        assertEquals(Boolean.FALSE, importerArgs.get(0).get(5));
        assertEquals(Set.of(0), importerArgs.get(0).get(6));
    }
}
