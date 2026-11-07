package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jdo.PersistenceManager;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.MultiPipelineIAJson;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bulk-import boxes PR A, task 6 (spec 4.4): handleRest's extract-then-identify branch against
 * real DataNucleus on real Postgres. Pseudobatos lentiginosus is configured with an ml-service
 * extractor (per-box extraction children and jobs); Rhina ancylostoma is HotSpotter-only (the
 * existing identification intake through runMatchProspects, observed via the dispatcher seam).
 */
@Testcontainers
class ImportedBoxIntakeDbTest extends IdentificationDbTestSupport {
    private static final String BASE = "https://base.test";
    private static final String ML_GENUS = "Pseudobatos", ML_SPECIES = "lentiginosus";
    private static final String WBIA_GENUS = "Rhina", WBIA_SPECIES = "ancylostoma";

    static JSONObject iaJson() {
        JSONObject json = MultiAlgorithmIntakeDbTest.iaJson();
        JSONObject lent = json.getJSONObject(ML_GENUS).getJSONObject(ML_SPECIES);
        lent.put("_default", new JSONObject().put("_id_conf", MultiPipelineIAJson.idConf("msv4_v3", "MiewID")));
        lent.put("_mlservice_conf", new JSONArray().put(MultiPipelineIAJson.bodyPipeline()));
        return json;
    }

    IAIntakeCoreTest.RecordingDispatcher dispatcher;

    @BeforeEach void fresh() {
        IAJsonProperties config = IAJsonProperties.iaConfig();
        config.setJson(iaJson());
        TestIAJsonPropertiesUtil.override(config);
        dispatcher = new IAIntakeCoreTest.RecordingDispatcher();
        IA.setDispatcherForTests(dispatcher);
        IA.setMatchProspectsRunnerForTests(null);
    }

    @AfterAll static void reset() {
        IA.setDispatcherForTests(null);
        IA.setMatchProspectsRunnerForTests(null);
        TestIAJsonPropertiesUtil.override(null);
    }

    // ---- fixtures ----

    /** root (import stamp) -> request task (what handleBulkImport's message names), both persisted. */
    static Task requestTask(Shepherd sh, String importId, boolean skipIdent) {
        Task root = new Task();
        root.setParameters(new JSONObject().put("importTaskId", importId).put("skipIdent", skipIdent));
        Task req = new Task();
        req.setParameters(new JSONObject(root.getParameters().toString()));
        root.addChild(req);
        sh.getPM().makePersistent(root);
        sh.getPM().makePersistent(req);
        return req;
    }

    static JSONObject envelope(String requestTaskId, List<String> annIds, String mode) {
        JSONObject j = new JSONObject().put("v2", true).put("taskId", requestTaskId)
            .put("annotationIds", new JSONArray(annIds)).put("__context", CTX).put("__baseUrl", BASE);
        if (mode != null) j.put("annotationMode", mode);
        return j;
    }

    /** Runs handleRest with the detection queue captured; every captured job's task must already be durable. */
    static List<JSONObject> handleRestCapturing(JSONObject envelope, int failPublishAt) {
        List<JSONObject> jobs = new ArrayList<JSONObject>();
        AtomicInteger calls = new AtomicInteger();
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                JSONObject job = new JSONObject(call.getArgument(1, String.class));
                Shepherd probe = open("probe");
                try {
                    assertNotNull(Task.load(job.getString("taskId"), probe), "the job's task is committed before it is published");
                } finally {
                    probe.rollbackAndClose();
                }
                int n = calls.incrementAndGet();
                if ((failPublishAt == 0) || (n == failPublishAt)) return false;
                jobs.add(job);
                return true;
            });
            IA.handleRest(envelope);
        }
        return jobs;
    }

    static List<JSONObject> handleRestCapturing(JSONObject envelope) {
        return handleRestCapturing(envelope, -1);
    }

    static int assetOf(Encounter enc) {
        return fx(enc).assetIdByAnn.get(fx(enc).annIds.get(0)).intValue();
    }

    static Task onlyChild(Task t) {
        assertNotNull(t.getChildren());
        assertEquals(1, t.getChildren().size(), "exactly one child under " + t.getId());
        return t.getChildren().get(0);
    }

    static Task childHolding(Task parent, String annId) {
        for (Task c : parent.getChildren()) if (c.holdsAnnotationId(annId)) return c;
        return null;
    }

    // ---- ml-service species ----

    @Test void mlServiceSpeciesGetsOneChildAndOneJobPerBoxAfterASingleCommit() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter two = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 2);
        Encounter one = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(two, one);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(3, jobs.size());

        Shepherd check = open("check");
        try {
            Task reqTask = Task.load(req.getId(), check);
            assertNull(reqTask.getStoredStatus(), "the request task is untouched");
            Task species = onlyChild(reqTask);
            assertEquals(2, species.getObjectMediaAssets().size(), "the species task holds the two boxed assets");
            assertEquals("Pseudobatos lentiginosus", species.getParameters() == null ? null : "Pseudobatos lentiginosus");
            assertEquals(importId, species.getParameters().getString("importTaskId"));
            assertEquals(3, species.getChildren().size());
            for (String annId : ids) {
                Task child = childHolding(species, annId);
                assertNotNull(child, "a child holds box " + annId);
                assertEquals(1, child.getObjectMediaAssets().size());
                assertTrue(child.getParameters().getBoolean(Task.PARAM_IMPORTED_BOX));
                assertTrue(child.getParameters().getBoolean(Task.PARAM_EMBEDDING_EXTRACTION));
                assertEquals(importId, child.getParameters().getString("importTaskId"));
                assertEquals("initiated", child.getStoredStatus());
                JSONObject job = null;
                for (JSONObject j : jobs) if (child.getId().equals(j.getString("taskId"))) job = j;
                assertNotNull(job, "one job names child " + child.getId());
                assertEquals(annId, job.getString("annotationId"));
                assertEquals("Pseudobatos lentiginosus", job.getString("taxonomyString"));
                assertTrue(job.getBoolean("mlServiceV2"));
                assertEquals(BASE, job.getString("__baseUrl"));
                assertEquals(job.toString(), child.getQueueResumeMessage());
                assertEquals(IBEISIA.STATUS_PROCESSING_MLSERVICE, check.getAnnotation(annId).getIdentificationStatus());
            }
            assertNull(check.getMediaAsset(String.valueOf(assetOf(two))).getDetectionStatus(), "a boxed asset stays unsettled until its boxes extract");
            assertEquals(0, dispatcher.published.size(), "no identification run yet");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void skipIdentStillEnqueuesExtraction() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 2);
        Task req = requestTask(sh, importId, true);
        List<String> ids = annotationIds(enc);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(2, jobs.size(), "extraction runs even when identification is skipped");
        Shepherd check = open("check");
        try {
            Task species = onlyChild(Task.load(req.getId(), check));
            for (Task child : species.getChildren()) assertTrue(child.getParameters().getBoolean("skipIdent"));
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void enqueueFailureMarksThatChildErrorAndLeavesTheAssetPending() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 2);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(enc);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT), 2);
        assertEquals(1, jobs.size());
        Shepherd check = open("check");
        try {
            Task species = onlyChild(Task.load(req.getId(), check));
            int initiated = 0, errored = 0;
            for (Task child : species.getChildren()) {
                if ("initiated".equals(child.getStoredStatus())) initiated++;
                if ("error".equals(child.getStoredStatus())) {
                    errored++;
                    assertEquals("ENQUEUE", MultiAlgorithmIntakeDbTest.errorCode(child));
                }
            }
            assertEquals(1, initiated);
            assertEquals(1, errored);
            assertNull(check.getMediaAsset(String.valueOf(assetOf(enc))).getDetectionStatus(), "one box is still pending");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void enqueueFailureOnEveryBoxSettlesTheAsset() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 2);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(enc);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT), 0);
        assertEquals(0, jobs.size());
        Shepherd check = open("check");
        try {
            Task species = onlyChild(Task.load(req.getId(), check));
            for (Task child : species.getChildren()) assertEquals("error", child.getStoredStatus());
            assertEquals(IBEISIA.STATUS_COMPLETE, check.getMediaAsset(String.valueOf(assetOf(enc))).getDetectionStatus(),
                "every box errored, so the asset settles and no sibling waits on it");
        } finally {
            check.rollbackAndClose();
        }
    }

    // ---- WBIA-only species ----

    @Test void wbiaOnlySpeciesRunsIdentificationThroughRunMatchProspects() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = boxedEncounter(sh, WBIA_GENUS, WBIA_SPECIES, importId, 1);
        Encounter e2 = boxedEncounter(sh, WBIA_GENUS, WBIA_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(0, jobs.size(), "no extraction job for a species without an ml-service extractor");
        assertEquals(2, dispatcher.published.size(), "one HotSpotter job per encounter group");
        Shepherd check = open("check");
        try {
            Task species = onlyChild(Task.load(req.getId(), check));
            assertEquals(2, species.getChildren().size(), "one umbrella per encounter under the species task");
            for (Task u : species.getChildren()) {
                assertTrue(Task.isUmbrella(u));
                assertEquals(importId, u.getParameters().getString("importTaskId"));
                assertFalse(u.getParameters().has(Task.PARAM_IMPORTED_BOX));
            }
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void wbiaOnlySpeciesWithSkipIdentDoesNothing() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = boxedEncounter(sh, WBIA_GENUS, WBIA_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, true);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        assertEquals(0, handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT)).size());
        assertEquals(0, dispatcher.published.size());
        Shepherd check = open("check");
        try {
            Task species = onlyChild(Task.load(req.getId(), check));
            assertTrue((species.getChildren() == null) || species.getChildren().isEmpty());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void twoSpeciesOnOnePhotoAreRoutedByTheirOwnTaxonomy() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter mlEnc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        MediaAsset shared = sh.getMediaAsset(String.valueOf(assetOf(mlEnc)));
        Encounter wbiaEnc = new Encounter();
        wbiaEnc.setSkipAutoIndexing(true);
        wbiaEnc.setCatalogNumber("enc-shared-" + nextImportId());
        wbiaEnc.setGenus(WBIA_GENUS);
        wbiaEnc.setSpecificEpithet(WBIA_SPECIES);
        Annotation wbiaBox = addBoxTo(sh, wbiaEnc, shared, importId, BODY, 40);
        sh.getPM().makePersistent(wbiaEnc);
        Task req = requestTask(sh, importId, false);
        List<String> ids = new ArrayList<String>(annotationIds(mlEnc));
        ids.add(wbiaBox.getId());
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(1, jobs.size());
        assertEquals("Pseudobatos lentiginosus", jobs.get(0).getString("taxonomyString"));
        assertEquals(annotationIds(mlEnc).get(0), jobs.get(0).getString("annotationId"));
        assertEquals(1, dispatcher.published.size(), "the WBIA-only box on the same photo is identified through its own species");
        Shepherd check = open("check");
        try {
            assertEquals(2, Task.load(req.getId(), check).getChildren().size(), "one species task each");
        } finally {
            check.rollbackAndClose();
        }
    }

    // ---- envelope validation ----

    @Test void mixedEnvelopeIsRejectedBeforeAnyIntake() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(enc);
        int assetId = assetOf(enc);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        JSONObject env = envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT)
            .put("mediaAssetIds", new JSONArray().put(assetId));
        assertEquals(0, handleRestCapturing(env).size(), "nothing published: no detection, no extraction");
        Shepherd check = open("check");
        try {
            Task reqTask = Task.load(req.getId(), check);
            assertEquals("error", reqTask.getStoredStatus());
            assertEquals("INVALID_ENVELOPE", MultiAlgorithmIntakeDbTest.errorCode(reqTask));
            assertTrue((reqTask.getChildren() == null) || reqTask.getChildren().isEmpty());
            assertNull(check.getMediaAsset(String.valueOf(assetId)).getDetectionStatus());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void unknownModeIsRejectedBeforeAnyIntake() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(enc);
        int assetId = assetOf(enc);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        JSONObject env = envelope(req.getId(), ids, "bogus").put("mediaAssetIds", new JSONArray().put(assetId));
        assertEquals(0, handleRestCapturing(env).size());
        Shepherd check = open("check");
        try {
            Task reqTask = Task.load(req.getId(), check);
            assertEquals("error", reqTask.getStoredStatus());
            assertTrue((reqTask.getChildren() == null) || reqTask.getChildren().isEmpty());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void plainAnnotationIdsStillTakeTheIdentificationBranch() throws Exception { // (regression)
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(sh, WBIA_GENUS, WBIA_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(enc);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        assertEquals(0, handleRestCapturing(envelope(req.getId(), ids, null)).size());
        assertEquals(1, dispatcher.published.size(), "today's identification intake ran");
    }

    // ---- failure isolation ----

    @Test void stagingExceptionYieldsOneErrorChildHoldingEveryBox() throws Exception {
        Shepherd fixture = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(fixture, ML_GENUS, ML_SPECIES, importId, 2);
        Task req = requestTask(fixture, importId, false);
        List<String> ids = annotationIds(enc);
        fixture.commitDBTransaction();
        fixture.closeDBTransaction();

        Shepherd real = open("intake");
        Shepherd sh = spy(real);
        PersistenceManager pm = spy(real.getPM());
        when(sh.getPM()).thenReturn(pm);
        AtomicInteger persists = new AtomicInteger();
        doAnswer(inv -> {
            if (persists.incrementAndGet() == 2) throw new RuntimeException("datastore gone");
            return inv.callRealMethod();
        }).when(pm).makePersistent(any());
        List<JSONObject> jobs = new ArrayList<JSONObject>();
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                jobs.add(new JSONObject(call.getArgument(1, String.class))); return true; });
            Task root = Task.load(req.getId(), sh);
            assertEquals(0, IA.intakeImportedBoxes(sh, ids, root, CTX, BASE));
            sh.commitDBTransaction();
        } finally {
            real.rollbackAndClose();
        }
        assertEquals(0, jobs.size(), "nothing was published");
        Shepherd check = open("check");
        try {
            Task reqTask = Task.load(req.getId(), check);
            assertEquals("error", reqTask.getStoredStatus());
            assertEquals("EXTRACTION_PERSIST", MultiAlgorithmIntakeDbTest.errorCode(reqTask));
            Task errorChild = onlyChild(reqTask);
            assertEquals("error", errorChild.getStoredStatus());
            for (String id : ids) assertTrue(errorChild.holdsAnnotationId(id), "the error child holds " + id);
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void wbiaPublicationFailureIsHandledByTheExistingIntakeRecovery() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = boxedEncounter(sh, WBIA_GENUS, WBIA_SPECIES, importId, 1);
        Encounter e2 = boxedEncounter(sh, WBIA_GENUS, WBIA_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        dispatcher.publishThrows = true;
        handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        Shepherd check = open("check");
        try {
            Task species = onlyChild(Task.load(req.getId(), check));
            assertEquals(2, species.getChildren().size(), "two umbrellas, no MATCH_DISPATCH child");
            for (Task u : species.getChildren()) {
                assertTrue(Task.isUmbrella(u));
                // a single-option species: the umbrella is the option itself (Task.PARAM_OPTION doc)
                boolean queueFailed = "QUEUE_FAILED".equals(MultiAlgorithmIntakeDbTest.errorCode(u));
                if (u.getChildren() != null)
                    for (Task opt : u.getChildren()) if ("QUEUE_FAILED".equals(MultiAlgorithmIntakeDbTest.errorCode(opt))) queueFailed = true;
                assertTrue(queueFailed, "the existing intake marked the option QUEUE_FAILED");
            }
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void wbiaRunnerExceptionIsIsolated() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter mlEnc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        Encounter wbiaEnc = boxedEncounter(sh, WBIA_GENUS, WBIA_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(mlEnc, wbiaEnc);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        IA.setMatchProspectsRunnerForTests((context, annIds, parentId) -> { throw new IllegalStateException("runner down"); });
        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(1, jobs.size(), "the ml-service species is unaffected");
        Shepherd check = open("check");
        try {
            Task reqTask = Task.load(req.getId(), check);
            assertEquals(2, reqTask.getChildren().size());
            Task wbiaSpecies = null;
            for (Task t : reqTask.getChildren()) if (childHolding(t, annotationIds(wbiaEnc).get(0)) != null) wbiaSpecies = t;
            assertNotNull(wbiaSpecies);
            Task errorChild = childHolding(wbiaSpecies, annotationIds(wbiaEnc).get(0));
            assertEquals("error", errorChild.getStoredStatus());
            assertEquals("MATCH_DISPATCH", MultiAlgorithmIntakeDbTest.errorCode(errorChild));
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void deletedAnnotationInTheEnvelopeDoesNotBlockTheOthers() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter a = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        Encounter b = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(a, b);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        Shepherd del = open("delete");
        try {
            Annotation gone = del.getAnnotation(ids.get(0));
            Encounter encA = del.getEncounter(a.getCatalogNumber());
            encA.removeAnnotation(gone);
            del.getPM().deletePersistent(gone);
            del.commitDBTransaction();
        } finally {
            del.closeDBTransaction();
        }

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(1, jobs.size());
        assertEquals(ids.get(1), jobs.get(0).getString("annotationId"));
        Shepherd check = open("check");
        try {
            Task reqTask = Task.load(req.getId(), check);
            assertNull(reqTask.getStoredStatus(), "the request task is not an error");
            Task species = onlyChild(reqTask);
            assertEquals(1, species.getChildren().size());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void annotationWithoutTaxonomyGetsAnErrorChild() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter ok = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        Encounter nameless = boxedEncounter(sh, null, null, importId, 1);
        Task req = requestTask(sh, importId, false);
        List<String> ids = annotationIds(ok, nameless);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        List<JSONObject> jobs = handleRestCapturing(envelope(req.getId(), ids, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(1, jobs.size());
        Shepherd check = open("check");
        try {
            Task reqTask = Task.load(req.getId(), check);
            Task errorChild = childHolding(reqTask, annotationIds(nameless).get(0));
            assertNotNull(errorChild, "the nameless box is held by an error child under the request task");
            assertEquals("error", errorChild.getStoredStatus());
            assertEquals("NO_TAXONOMY", MultiAlgorithmIntakeDbTest.errorCode(errorChild));
        } finally {
            check.rollbackAndClose();
        }
    }
}
