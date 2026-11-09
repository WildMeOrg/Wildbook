package org.ecocean.ia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.IndexingManager;
import org.ecocean.IndexingManagerFactory;
import org.ecocean.RestClient;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.identity.IAQueryCache;
import org.ecocean.identity.IBEISIA;
import org.ecocean.ia.plugin.WildbookIAM;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.AssetStore;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shared datastore/media fixtures for registration and identification tests. */
@Testcontainers
abstract class IdentificationDbTestSupport {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static final String CTX = "context0";
    static final String BODY = "guitarfish";
    static final String ROSTRUM = "guitarfish+rostrum";
    static int seq = 0;
    static Path imageDir;
    static File testImage;
    static Integer storeId;   // the persisted LocalAssetStore; reloaded ATTACHED inside each fixture transaction

    /**
     * Scalars of a fixture encounter, snapshotted while its objects are attached. DataNucleus
     * clears non-key fields at commit (RetainValues=false) and disconnects objects at close, so
     * a test never reads a fixture Encounter / Annotation / MediaAsset after its transaction: it
     * goes through this registry, keyed by catalogNumber (a primary key, which stays readable).
     */
    static final class Fx {
        final String catalogNumber;
        final List<String> annIds = new ArrayList<String>();
        final Map<String, String> acmByAnn = new HashMap<String, String>();
        final Map<String, String> assetAcmByAnn = new HashMap<String, String>();

        Fx(String catalogNumber) {
            this.catalogNumber = catalogNumber;
        }
    }
    static final Map<String, Fx> fixtures = new HashMap<String, Fx>();

    static Fx fx(Encounter enc) {
        return fixtures.get(enc.getCatalogNumber());
    }

    static String acmOf(Encounter enc, int i) {
        return fx(enc).acmByAnn.get(fx(enc).annIds.get(i));
    }

    static String assetAcmOf(Encounter enc, int i) {
        return fx(enc).assetAcmByAnn.get(fx(enc).annIds.get(i));
    }

    // Production code reloads annotations (their transient skipAutoIndexing flag is gone) and
    // commits changes to them (wbiaRegistered, acmId); postStore would then hand them to the real
    // IndexingManager, whose background Shepherds hold open transactions against this PMF (and
    // make tearDown's closePMF fail). Every test runs on the thread that registers this mock.
    static MockedStatic<IndexingManagerFactory> noIndexing;

    @BeforeAll
    static void setUp() throws Exception {
        fixtures.clear();
        CommonConfiguration.initialize(CTX, new Properties());
        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF(CTX);
        noIndexing = mockStatic(IndexingManagerFactory.class);
        noIndexing.when(IndexingManagerFactory::getIndexingManager).thenReturn(Mockito.mock(IndexingManager.class));
        imageDir = Files.createTempDirectory("multialgo-1787");
        testImage = Files.copy(Paths.get("src", "test", "bulk-images", "image-ok-0.jpg").toAbsolutePath(),
            imageDir.resolve("image.jpg"), StandardCopyOption.REPLACE_EXISTING).toFile();
        LocalAssetStore store = new LocalAssetStore("local-1787", imageDir, "file://" + imageDir, false);
        Shepherd sh = new Shepherd(CTX, props);
        try {
            FeatureType.initAll(sh);          // opens, queries and rolls back (or commits) its own transaction
            sh.beginDBTransaction();          // so begin again before persisting the store
            sh.getPM().makePersistent(store);
            sh.commitDBTransaction();
            storeId = store.getId();          // the primary key survives commit and close; nothing else is read later
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        if (noIndexing != null) noIndexing.close();
        TestPMFUtil.closePMF(CTX);
    }

    static String nextImportId() {
        return "import-" + (++seq);
    }

    static Encounter encounter(Shepherd sh, String genus, String species, String... iaClasses) throws Exception {
        Encounter enc = new Encounter();
        enc.setSkipAutoIndexing(true);
        enc.setCatalogNumber("enc-" + (++seq));
        enc.setGenus(genus);
        enc.setSpecificEpithet(species);
        Fx snapshot = new Fx(enc.getCatalogNumber());
        LocalAssetStore attachedStore = (LocalAssetStore)AssetStore.get(sh, storeId.intValue());   // attached: its config loads
        for (String iaClass : iaClasses) {
            MediaAsset ma = attachedStore.create(testImage);
            ma.setId(100000 + (++seq));
            ma.setSkipAutoIndexing(true);
            ma.setAcmId(ma.getUUID());
            // dimensions + mime type: WildbookIAM.validMediaAsset (the real eligibility chain) reads them
            try {
                ma.updateMetadata();
            } catch (Exception ex) {
                System.out.println("WARN: updateMetadata failed in fixture: " + ex);
            }
            if (ma.getImageAttributes() == null) {
                MediaAssetMetadata md = (ma.getMetadata() == null) ? new MediaAssetMetadata() : ma.getMetadata();
                md.getData().put("attributes", new JSONObject().put("width", 640).put("height", 480).put("contentType", "image/jpeg"));
                ma.setMetadata(md);
            }
            // the FeatureType is loaded ATTACHED: the initAll cache holds instances of a closed
            // manager, which this manager would try to INSERT again
            Feature f = new Feature(FeatureType.load("org.ecocean.boundingBox", sh),
                new JSONObject().put("x", 1).put("y", 1).put("width", 10).put("height", 10));
            ma.addFeature(f);
            Annotation ann = new Annotation(null, f, iaClass);
            f.setAnnotation(ann);
            ann.setSkipAutoIndexing(true);
            ann.setMatchAgainst(true);
            ann.setAcmId(ann.getId());
            sh.getPM().makePersistent(ma);
            sh.getPM().makePersistent(ann);
            enc.addAnnotation(ann);
            snapshot.annIds.add(ann.getId());
            snapshot.acmByAnn.put(ann.getId(), ann.getAcmId());
            snapshot.assetAcmByAnn.put(ann.getId(), ma.getAcmId());
        }
        sh.getPM().makePersistent(enc);
        fixtures.put(snapshot.catalogNumber, snapshot);
        return enc;
    }

    static Encounter guitarfish(Shepherd sh, String... iaClasses) throws Exception {
        return encounter(sh, "Pseudobatos", "lentiginosus", iaClasses);
    }

    static Task detectionTask(Shepherd sh, String importId) {
        Task root = new Task();
        root.setParameters(new JSONObject().put("importTaskId", importId));
        Task d = new Task(root);
        d.addParameter("ibeis.detection", true);
        d.setStatus("completed");
        sh.getPM().makePersistent(root);
        sh.getPM().makePersistent(d);
        return d;
    }

    static ImportTask importWith(Shepherd sh, String importId, Task root, Encounter... encs) {
        ImportTask itask = new ImportTask(null, importId);
        for (Encounter e : encs) itask.addEncounter(e);
        itask.setIATask(root);
        sh.storeNewImportTask(itask);
        return itask;
    }

    static List<String> annotationIds(Encounter... encs) {
        List<String> ids = new ArrayList<String>();
        for (Encounter e : encs) ids.addAll(fx(e).annIds);
        return ids;
    }

    static Set<String> idSet(Encounter enc) {
        return new HashSet<String>(annotationIds(enc));
    }

    static Shepherd open(String action) {
        Shepherd sh = new Shepherd(CTX);
        sh.setAction("IdentificationDbTestSupport." + action);
        sh.beginDBTransaction();
        return sh;
    }

    interface Body { void run() throws Exception; }

    static final class WbiaFake {
        final List<String> knownAnnotIds = new ArrayList<String>();
        final List<String> knownImageIds = new ArrayList<String>();
        final Map<String, String> eligibilityOverride = new HashMap<String, String>();
        boolean echoWrongAnnotationUuid = false;
        final List<JSONObject> posts = new ArrayList<JSONObject>();
        final List<ArrayList<Annotation>> sentTargets = new ArrayList<ArrayList<Annotation>>();

        List<JSONObject> imagePosts() {
            List<JSONObject> out = new ArrayList<JSONObject>();
            for (JSONObject p : posts) if (p.has("image_uri_list")) out.add(p);
            return out;
        }

        List<JSONObject> annotationPosts() {
            List<JSONObject> out = new ArrayList<JSONObject>();
            for (JSONObject p : posts) if (p.has("annot_bbox_list")) out.add(p);
            return out;
        }

        static JSONObject fancyList(List<String> ids) {
            JSONArray arr = new JSONArray();
            for (String id : ids) arr.put(WildbookIAM.toFancyUUID(id));
            return new JSONObject().put("status", new JSONObject().put("success", true)).put("response", arr);
        }

        JSONObject reply(URL url, JSONObject body) {
            posts.add(body);
            JSONArray echo = new JSONArray();
            if (url.getPath().contains("image")) {
                JSONArray sent = body.getJSONArray("image_uuid_list");
                for (int i = 0; i < sent.length(); i++) echo.put(sent.getJSONObject(i));
            } else if (body.has("annot_uuid_list")) {               // forced: echo (or refuse)
                JSONArray sent = body.getJSONArray("annot_uuid_list");
                for (int i = 0; i < sent.length(); i++) {
                    echo.put(echoWrongAnnotationUuid ? WildbookIAM.toFancyUUID(java.util.UUID.randomUUID().toString()) : sent.getJSONObject(i));
                }
            } else {                                                 // legacy: WBIA mints
                JSONArray sent = body.getJSONArray("image_uuid_list");
                for (int i = 0; i < sent.length(); i++) echo.put(WildbookIAM.toFancyUUID(java.util.UUID.randomUUID().toString()));
            }
            return new JSONObject().put("status", new JSONObject().put("success", true)).put("response", echo);
        }
    }

    static void withRealWbia(WbiaFake wbia, Body body) throws Exception {
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("getPluginInstance")) return new WildbookIAM(CTX);   // the REAL plugin
                 if (m.equals("sendIdentify")) {
                     wbia.sentTargets.add(inv.getArgument(1));
                     // code 200 like real WBIA: without it iaCheckMissing reads "response" as an object
                     return new JSONObject().put("status", new JSONObject().put("code", 200).put("success", true).put("message", "ok"))
                         .put("response", "job-1");
                 }
                 if (m.equals("log")) return null;
                 return inv.callRealMethod();
             });
             MockedStatic<WildbookIAM> iam = mockStatic(WildbookIAM.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("iaAnnotationIdsStrict")) return new ArrayList<String>(wbia.knownAnnotIds);
                 if (m.equals("iaImageIdsStrict")) return new ArrayList<String>(wbia.knownImageIds);
                 if (m.equals("wbiaEligibilityReason")) {
                     Annotation a = inv.getArgument(0);
                     if ((a != null) && wbia.eligibilityOverride.containsKey(a.getId())) return wbia.eligibilityOverride.get(a.getId());
                     return inv.callRealMethod();
                 }
                 return inv.callRealMethod();
             });
             MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<RestClient> rest = mockStatic(RestClient.class);
             MockedStatic<QueryCacheFactory> qc = mockStatic(QueryCacheFactory.class)) {
            ia.when(() -> IA.getProperty(anyString(), eq("IBEISIARestUrlAddAnnotations"))).thenReturn("http://wbia/api/annot/json/");
            ia.when(() -> IA.getProperty(anyString(), eq("IBEISIARestUrlAddImages"))).thenReturn("http://wbia/api/image/json/");
            rest.when(() -> RestClient.post(any(URL.class), any(JSONObject.class)))
                .thenAnswer(inv -> wbia.reply(inv.getArgument(0), inv.getArgument(1)));
            rest.when(() -> RestClient.get(any(URL.class))).thenAnswer(inv -> {
                URL url = inv.getArgument(0);
                return WbiaFake.fancyList(url.getPath().contains("image") ? wbia.knownImageIds : wbia.knownAnnotIds);
            });
            body.run();
        }
    }

    static String fancyAt(JSONObject post, String key, int i) {
        return WildbookIAM.fromFancyUUID(post.getJSONArray(key).getJSONObject(i));
    }

    static JSONObject missingAnnotationReply(String acmId) {
        return new JSONObject()
            .put("status", new JSONObject().put("code", 600).put("success", false).put("message", "missing"))
            .put("response", new JSONObject().put("missing_annot_uuid_list",
                new JSONArray().put(WildbookIAM.toFancyUUID(acmId))));
    }

    static String identifyTaskFor(Shepherd sh, Annotation query) {
        Task t = new Task();
        t.addParameter("ibeis.identification", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"));
        t.addObject(query);
        sh.getPM().makePersistent(t);
        return t.getId();
    }

    static List<String> idsOf(List<Annotation> anns) {
        List<String> ids = new ArrayList<String>();
        for (Annotation a : anns) ids.add(a.getId());
        return ids;
    }

    static void withIdentifyStubs(JSONObject sendResult, Answer<Boolean> requeue, Body body)
    throws Exception {
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("waitForIAPriming")) return null;
                 if (m.equals("queryConfigDict")) return new JSONObject();
                 if (m.equals("beginIdentifyAnnotations")) return sendResult;
                 if (m.equals("log")) return null;
                 return inv.callRealMethod();
             });
             MockedStatic<IAQueryCache> cache = mockStatic(IAQueryCache.class);
             MockedStatic<IAGateway> gw = mockStatic(IAGateway.class, inv -> {
                 // _doIdentify asks for the typed verdict: true = queued, false = budget exhausted
                 if (inv.getMethod().getName().equals("requeueJobResult"))
                     return requeue.answer(inv) ? IAGateway.RequeueResult.QUEUED : IAGateway.RequeueResult.RETRY_CAP;
                 return inv.callRealMethod();
             })) {
            cache.when(() -> IAQueryCache.tryTargetAnnotationsCache(anyString(), any(Annotation.class),
                any(JSONObject.class), any(Shepherd.class))).thenReturn(null);
            body.run();
        }
    }

    static List<Task> childrenOf(Task t) {
        return (t.getChildren() == null) ? new ArrayList<Task>() : t.getChildren();
    }

    static String errorCode(Task t) {
        JSONObject sd = t.getStatusDetails();
        JSONArray errs = (sd == null) ? null : sd.optJSONArray("errors");
        if ((errs == null) || (errs.length() == 0)) return null;
        return errs.getJSONObject(errs.length() - 1).optString("code", null);
    }

    static void setStatus(String taskId, String status) {
        Shepherd sh = open("setStatus");
        try {
            Task t = Task.load(taskId, sh);
            t.setStatus(status);
            if ("completed".equals(status) || "error".equals(status)) t.setCompletionDateInMilliseconds();
            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
    }
}
