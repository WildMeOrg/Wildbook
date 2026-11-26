package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.IndexingManager;
import org.ecocean.IndexingManagerFactory;
import org.ecocean.Keyword;
import org.ecocean.LabeledKeyword;
import org.ecocean.OpenSearch;
import org.ecocean.media.AssetStore;
import org.ecocean.media.Feature;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Issue #1291: a keyword added to (or removed from) an image on the encounter page must reach
 * the encounter's search document, which carries its images' keywords. Drives the real
 * AddLabeledKeyword / RestKeyword servlets against real DataNucleus + Postgres and records which
 * encounters are queued for reindex after each request.
 */
@Testcontainers
class KeywordEditReindexesEncounterDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static final String CTX = "context0";
    static final String OLD_MODIFIED = "2000-01-01 00:00:00";
    static int seq = 0;
    static Path imageDir;
    static File testImage;
    static Integer storeId;

    @BeforeAll
    static void setUp() throws Exception {
        CommonConfiguration.initialize(CTX, new Properties());
        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF(CTX);
        imageDir = Files.createTempDirectory("kw-1291");
        testImage = Files.copy(Paths.get("src", "test", "bulk-images", "image-ok-0.jpg").toAbsolutePath(),
            imageDir.resolve("image.jpg"), StandardCopyOption.REPLACE_EXISTING).toFile();
        LocalAssetStore store = new LocalAssetStore("local-1291", imageDir, "file://" + imageDir, false);
        Shepherd sh = new Shepherd(CTX, props);
        try {
            sh.beginDBTransaction();
            sh.getPM().makePersistent(store);
            sh.commitDBTransaction();
            storeId = store.getId();
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF(CTX);
    }

    // ---- fixtures -----------------------------------------------------------------------------

    /** What the test needs from a seeded world; plain ids, readable after the seeding tx closes. */
    static final class World {
        final List<Integer> assetIds = new ArrayList<Integer>();
        final List<String> encIds = new ArrayList<String>();
    }

    interface Seeder {
        void seed(Shepherd sh, World w) throws Exception;
    }

    static World seed(Seeder seeder) throws Exception {
        World w = new World();
        Shepherd sh = new Shepherd(CTX);
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(mock(IndexingManager.class));
            sh.beginDBTransaction();
            seeder.seed(sh, w);
            sh.commitDBTransaction();
        } finally {
            sh.rollbackAndClose();
        }
        for (String id : w.encIds) setModified(id, OLD_MODIFIED);
        return w;
    }

    static MediaAsset asset(Shepherd sh, World w) throws Exception {
        LocalAssetStore store = (LocalAssetStore)AssetStore.get(sh, storeId.intValue());
        MediaAsset ma = store.create(testImage);
        ma.setId(200000 + (++seq));
        ma.setSkipAutoIndexing(true);
        sh.getPM().makePersistent(ma);
        w.assetIds.add(ma.getIdInt());
        return ma;
    }

    static Encounter encounterWith(Shepherd sh, World w, Annotation ann) {
        Encounter enc = new Encounter();
        enc.setSkipAutoIndexing(true);
        enc.setCatalogNumber("enc-1291-" + (++seq));
        ann.setSkipAutoIndexing(true);
        sh.getPM().makePersistent(ann);
        enc.addAnnotation(ann);
        sh.getPM().makePersistent(enc);
        w.encIds.add(enc.getCatalogNumber());
        return enc;
    }

    /** The usual shape: one annotation whose (only) feature is on the asset. */
    static Encounter ownerOf(Shepherd sh, World w, MediaAsset ma) throws Exception {
        Feature f = ma.generateUnityFeature();
        Annotation ann = new Annotation(null, f, "body");
        f.setAnnotation(ann);
        return encounterWith(sh, w, ann);
    }

    // ---- requests -----------------------------------------------------------------------------

    /** Encounter ids queued for (non-un)index, in order, from one request. */
    static List<String> run(ServletCall call) throws Exception {
        List<String> queued = new ArrayList<String>();
        IndexingManager im = mock(IndexingManager.class);
        doAnswer((InvocationOnMock inv) -> {
            if (Encounter.class.equals(inv.getArgument(1)) && !((Boolean)inv.getArgument(2)))
                queued.add(inv.getArgument(0));
            return null;
        }).when(im).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class);
            MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);
            su.when(() -> ServletUtilities.getContext(any())).thenReturn(CTX);
            call.run(su);
        }
        return queued;
    }

    interface ServletCall {
        void run(MockedStatic<ServletUtilities> su) throws Exception;
    }

    /** POST /AddLabeledKeyword; returns the parsed response body. */
    static JSONObject addLabeled(MockedStatic<ServletUtilities> su, int assetId, String label,
        String value) throws Exception {
        StringWriter body = new StringWriter();
        return addLabeled(assetId, label, value, mock(HttpServletResponse.class),
            new PrintWriter(body), body);
    }

    static JSONObject addLabeled(int assetId, String label, String value,
        HttpServletResponse resp, PrintWriter writer, StringWriter body) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("label")).thenReturn(label);
        when(req.getParameter("value")).thenReturn(value);
        when(req.getParameter("mid")).thenReturn(Integer.toString(assetId));
        when(resp.getWriter()).thenReturn(writer);
        new AddLabeledKeyword().doPost(req, resp);
        return new JSONObject(body.toString().trim());
    }

    /** POST /RestKeyword onMediaAssets; op is add (keyword ids), newAdd (names) or remove (ids). */
    static JSONObject restKeyword(MockedStatic<ServletUtilities> su, int assetId, String op,
        String keyword) throws Exception {
        JSONObject jin = new JSONObject().put("onMediaAssets", new JSONObject()
            .put("assetIds", new JSONArray().put(assetId))
            .put(op, new JSONArray().put(keyword)));
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getUserPrincipal()).thenReturn(() -> "researcher");
        su.when(() -> ServletUtilities.jsonFromHttpServletRequest(req)).thenReturn(jin);
        StringWriter body = new StringWriter();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(body));
        new RestKeyword().doPost(req, resp);
        return new JSONObject(body.toString().trim());
    }

    // ---- committed state, read over plain JDBC or a fresh Shepherd ----------------------------

    static Connection jdbc() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword());
    }

    static String modified(String encId) throws SQLException {
        try (Connection c = jdbc(); PreparedStatement ps = c.prepareStatement(
            "SELECT \"MODIFIED\" FROM \"ENCOUNTER\" WHERE \"CATALOGNUMBER\" = ?")) {
            ps.setString(1, encId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "encounter row " + encId + " exists");
                return rs.getString(1);
            }
        }
    }

    static void setModified(String encId, String value) throws SQLException {
        try (Connection c = jdbc(); PreparedStatement ps = c.prepareStatement(
            "UPDATE \"ENCOUNTER\" SET \"MODIFIED\" = ? WHERE \"CATALOGNUMBER\" = ?")) {
            ps.setString(1, value);
            ps.setString(2, encId);
            assertEquals(1, ps.executeUpdate());
        }
    }

    /** label=value pairs of the encounter's labeled keywords, as its search document builds them. */
    static Set<String> labeledKeywordsOf(String encId) {
        Set<String> out = new HashSet<String>();
        Shepherd sh = new Shepherd(CTX);
        try {
            sh.beginDBTransaction();
            for (Keyword kw : sh.getEncounter(encId).getMediaAssetKeywords()) {
                if (kw instanceof LabeledKeyword)
                    out.add(((LabeledKeyword)kw).getLabel() + "=" + ((LabeledKeyword)kw).getValue());
            }
        } finally {
            sh.rollbackAndClose();
        }
        return out;
    }

    /** Display names of the asset's keywords, committed state. */
    static Set<String> assetKeywords(int assetId) {
        Set<String> out = new HashSet<String>();
        Shepherd sh = new Shepherd(CTX);
        try {
            sh.beginDBTransaction();
            out.addAll(sh.getMediaAsset(Integer.toString(assetId)).getKeywordNames());
        } finally {
            sh.rollbackAndClose();
        }
        return out;
    }

    /** Wait until just past a second boundary, so the next few hundred ms share one second. */
    static void startOfSecond() throws InterruptedException {
        Thread.sleep(1000 - (System.currentTimeMillis() % 1000) + 20);
    }

    static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    // ---- the contract -------------------------------------------------------------------------

    @Test void addingLabeledKeywordsQueuesTheEncounterEachTime() throws Exception {
        World w = seed((sh, world) -> ownerOf(sh, world, asset(sh, world)));
        int assetId = w.assetIds.get(0);
        String encId = w.encIds.get(0);

        List<String> first = run(su -> assertTrue(
            addLabeled(su, assetId, "quality", "1").getBoolean("success")));
        assertEquals(List.of(encId), first, "adding quality=1 queues the owning encounter");
        assertTrue(!OLD_MODIFIED.equals(modified(encId)), "MODIFIED advanced");

        // the second edit lands in the same formatted second as the stored MODIFIED, so
        // setDWCDateLastModified() writes an unchanged value; it must still queue the encounter
        boolean sameSecond = false;
        for (int attempt = 0; (attempt < 5) && !sameSecond; attempt++) {
            startOfSecond();
            String pinned = now();
            setModified(encId, pinned);
            String value = Integer.toString(attempt);
            List<String> second = run(su -> assertTrue(
                addLabeled(su, assetId, "distinctiveness", value).getBoolean("success")));
            sameSecond = pinned.equals(modified(encId));
            assertEquals(List.of(encId), second, "adding distinctiveness=" + value +
                " queues the owning encounter");
        }
        assertTrue(sameSecond, "at least one edit ran within the pinned second");
        Set<String> kws = labeledKeywordsOf(encId);
        assertTrue(kws.contains("quality=1"), "quality=1 on the encounter: " + kws);
        assertTrue(kws.contains("distinctiveness=0"), "distinctiveness=0 on the encounter: " + kws);
    }

    @Test void reAddingTheSameLabeledKeywordQueuesNothing() throws Exception {
        World w = seed((sh, world) -> ownerOf(sh, world, asset(sh, world)));
        int assetId = w.assetIds.get(0);
        String encId = w.encIds.get(0);

        run(su -> assertTrue(addLabeled(su, assetId, "quality", "2").getBoolean("success")));
        assertEquals(Set.of("quality=2"), labeledKeywordsOf(encId));
        setModified(encId, OLD_MODIFIED);
        List<String> again = run(su -> assertTrue(
            addLabeled(su, assetId, "quality", "2").getBoolean("success")));
        assertEquals(List.of(), again);
        assertEquals(OLD_MODIFIED, modified(encId));
    }

    @Test void everyEncounterShowingTheAssetIsQueued_includingLegacyFeaturelessOwners()
    throws Exception {
        World w = seed((sh, world) -> {
            MediaAsset ma = asset(sh, world);
            ownerOf(sh, world, ma);
            ownerOf(sh, world, ma);
            // legacy shape: no features, only the deprecated direct association
            Annotation legacy = new Annotation(null, new ArrayList<Feature>());
            Field direct = Annotation.class.getDeclaredField("mediaAsset");
            direct.setAccessible(true);
            direct.set(legacy, ma);
            encounterWith(sh, world, legacy);
        });
        int assetId = w.assetIds.get(0);

        List<String> queued = run(su -> assertTrue(
            addLabeled(su, assetId, "quality", "3").getBoolean("success")));
        assertEquals(new HashSet<String>(w.encIds), new HashSet<String>(queued));
        assertEquals(w.encIds.size(), queued.size(), "each encounter queued once: " + queued);
    }

    @Test void anEncounterThatDoesNotShowTheAssetIsLeftAlone() throws Exception {
        // the annotation's first feature is on asset 0 (what the encounter shows); a second
        // feature is on asset 1
        World w = seed((sh, world) -> {
            MediaAsset shown = asset(sh, world);
            MediaAsset other = asset(sh, world);
            ArrayList<Feature> fts = new ArrayList<Feature>();
            fts.add(shown.generateUnityFeature());
            fts.add(other.generateUnityFeature());
            Annotation ann = new Annotation(null, fts, "body");
            for (Feature f : fts) f.setAnnotation(ann);
            encounterWith(sh, world, ann);
        });
        int otherId = w.assetIds.get(1);
        String encId = w.encIds.get(0);

        List<String> queued = run(su -> assertTrue(
            addLabeled(su, otherId, "quality", "4").getBoolean("success")));
        assertEquals(List.of(), queued);
        assertEquals(OLD_MODIFIED, modified(encId));
    }

    @Test void removingAKeywordQueuesTheEncounter_removingAnAbsentOneDoesNot() throws Exception {
        World w = seed((sh, world) -> ownerOf(sh, world, asset(sh, world)));
        int assetId = w.assetIds.get(0);
        String encId = w.encIds.get(0);
        List<String> kwIds = new ArrayList<String>();
        run(su -> kwIds.addAll(
            addLabeled(su, assetId, "quality", "5").getJSONObject("newKeywords").keySet()));
        assertEquals(1, kwIds.size());
        String kwId = kwIds.get(0);

        setModified(encId, OLD_MODIFIED);
        List<String> removed = run(su -> assertTrue(
            restKeyword(su, assetId, "remove", kwId).getBoolean("success")));
        assertEquals(List.of(encId), removed, "removing the last keyword queues the encounter");
        assertEquals(Set.of(), labeledKeywordsOf(encId));

        setModified(encId, OLD_MODIFIED);
        List<String> absent = run(su -> assertTrue(
            restKeyword(su, assetId, "remove", kwId).getBoolean("success")));
        assertEquals(List.of(), absent, "removing an absent keyword queues nothing");
        assertEquals(OLD_MODIFIED, modified(encId));
    }

    @Test void addingAPlainKeywordThroughRestKeywordQueuesTheEncounter() throws Exception {
        World w = seed((sh, world) -> ownerOf(sh, world, asset(sh, world)));
        int assetId = w.assetIds.get(0);
        String encId = w.encIds.get(0);

        List<String> queued = run(su -> assertTrue(
            restKeyword(su, assetId, "newAdd", "spots-1291").getBoolean("success")));
        assertEquals(List.of(encId), queued);
        assertEquals(Set.of("spots-1291"), assetKeywords(assetId));
        assertTrue(!OLD_MODIFIED.equals(modified(encId)), "MODIFIED advanced");
    }

    @Test void aFailedEncounterLookupRollsTheKeywordBackAndReports400() throws Exception {
        World w = seed((sh, world) -> ownerOf(sh, world, asset(sh, world)));
        int assetId = w.assetIds.get(0);
        String encId = w.encIds.get(0);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        PrintWriter writer = spy(new PrintWriter(body));

        List<String> queued = run(su -> {
            try (MockedStatic<Encounter> enc = mockStatic(Encounter.class, Mockito.CALLS_REAL_METHODS)) {
                enc.when(() -> Encounter.touchEncountersOf(any(), any()))
                    .thenThrow(new RuntimeException("lookup failed"));
                JSONObject out = addLabeled(assetId, "quality", "6", resp, writer, body);
                assertTrue(!out.optBoolean("success", false), "reported as failed: " + out);
                assertTrue(out.has("error"), "has an error: " + out);
            }
        });
        InOrder order = Mockito.inOrder(resp, writer);
        order.verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        order.verify(writer).close(); // status set before the body is flushed and closed
        assertEquals(List.of(), queued);
        assertEquals(Set.of(), assetKeywords(assetId), "the keyword edit rolled back");
        assertEquals(OLD_MODIFIED, modified(encId));
    }
}
