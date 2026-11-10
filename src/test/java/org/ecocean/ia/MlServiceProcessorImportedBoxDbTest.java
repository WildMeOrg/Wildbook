package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.MultiPipelineIAJson;
import org.ecocean.OpenSearch;
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
 * Bulk-import boxes PR A, task 8 (spec 4.4): the extraction lifecycle of an imported-box child
 * against real DataNucleus on real Postgres, the REAL visibility gate (OpenSearch stubbed to
 * "visible") and the real identification intake observed through the dispatcher seam. The
 * children and jobs come from the real intake (handleRest); the ml-service client is a fake.
 */
@Testcontainers
class MlServiceProcessorImportedBoxDbTest extends IdentificationDbTestSupport {
    private static final String BASE = "https://base.test";
    private static final String ML_GENUS = "Pseudobatos", ML_SPECIES = "lentiginosus";
    private static final String MODEL_ID = "miewid-msv4_v3";

    /** ml-service species: the fixture species gains a vector default and an extractor. */
    static JSONObject iaJson(boolean explicitModelIdentity, boolean withExtractor) {
        JSONObject json = MultiAlgorithmIntakeDbTest.iaJson();
        JSONObject lent = json.getJSONObject(ML_GENUS).getJSONObject(ML_SPECIES);
        JSONArray idConf = explicitModelIdentity
            ? MultiPipelineIAJson.idConf("msv4_v3", "MiewID")
            : new JSONArray().put(new JSONObject().put("pipeline_root", "vector").put("description", "MiewID, identity from the embedding").put("default", true));
        lent.put("_default", new JSONObject().put("_id_conf", idConf));
        lent.getJSONObject(BODY).put("_id_conf", new JSONArray(idConf.toString()));
        if (withExtractor) lent.put("_mlservice_conf", new JSONArray().put(MultiPipelineIAJson.bodyPipeline()));
        else lent.remove("_mlservice_conf");
        return json;
    }

    static void configure(boolean explicitModelIdentity, boolean withExtractor) {
        IAJsonProperties config = IAJsonProperties.iaConfig();
        config.setJson(iaJson(explicitModelIdentity, withExtractor));
        TestIAJsonPropertiesUtil.override(config);
    }

    /** Fake extractor: returns one embedding per call, or throws, or deletes the annotation first. */
    static final class FakeClient extends MlServiceClient {
        int calls = 0;
        boolean throwNonRetryable = false;
        String deleteAnnotationId = null;
        @Override public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
            double theta, JSONObject config) throws IAException {
            calls++;
            if (throwNonRetryable) throw new IAException("ml-service rejected the request", false, false);
            if (deleteAnnotationId != null) deleteAnnotation(deleteAnnotationId);
            return new JSONObject().put("embedding", new JSONArray().put(0.1).put(0.2).put(0.3))
                .put("embedding_model_id", MODEL_ID);
        }
    }

    static final class RecordingPublisher implements DeferredMatchPublisher {
        final List<JSONObject> published = new ArrayList<JSONObject>();
        boolean result = true;
        @Override public boolean publish(JSONObject payload) {
            published.add(payload);
            return result;
        }
    }

    /** Records the extraction child's stored status at the moment its run is dispatched. */
    static final class StatusAtDispatch extends IAIntakeCoreTest.RecordingDispatcher {
        final List<String> statuses = new ArrayList<String>();
        String childId;
        @Override public void runVector(JSONObject opt, Task optionTask, Shepherd shep) {
            super.runVector(opt, optionTask, shep);
            if (childId == null) return;
            Shepherd probe = open("probe");
            try {
                statuses.add(Task.load(childId, probe).getStoredStatus());
            } finally {
                probe.rollbackAndClose();
            }
        }
    }

    FakeClient client;
    RecordingPublisher publisher;
    StatusAtDispatch dispatcher;
    MlServiceProcessor processor;

    @BeforeEach void fresh() {
        configure(true, true);
        client = new FakeClient();
        publisher = new RecordingPublisher();
        dispatcher = new StatusAtDispatch();
        IA.setDispatcherForTests(dispatcher);
        OpenSearch os = mock(OpenSearch.class);
        try {
            when(os.waitForVisibility(anyString(), any(), anyLong())).thenReturn(true);
            when(os.waitForAnnotationMatchableIds(any(), any(), any(), anyLong())).thenReturn(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        processor = new MlServiceProcessor(CTX, client, new MatchVisibilityGateImpl(CTX, os), publisher);
    }

    @AfterAll static void reset() {
        IA.setDispatcherForTests(null);
        TestIAJsonPropertiesUtil.override(null);
    }

    // ---- fixtures: the real intake builds the species task, the children and the jobs ----

    static final class Fixture {
        String importId;
        Encounter enc;
        List<String> annIds;
        int assetId;
        List<JSONObject> jobs;
        JSONObject jobFor(String annId) {
            for (JSONObject j : jobs) if (annId.equals(j.getString("annotationId"))) return j;
            return null;
        }
        String childFor(String annId) {
            return jobFor(annId).getString("taskId");
        }
    }

    static Fixture boxedImport(int boxes, boolean skipIdent) throws Exception {
        Shepherd sh = open("fixture");
        Fixture f = new Fixture();
        f.importId = nextImportId();
        f.enc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, f.importId, boxes);
        f.annIds = annotationIds(f.enc);
        f.assetId = fx(f.enc).assetIdByAnn.get(f.annIds.get(0)).intValue();
        Task req = ImportedBoxIntakeDbTest.requestTask(sh, f.importId, skipIdent);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        f.jobs = ImportedBoxIntakeDbTest.handleRestCapturing(
            ImportedBoxIntakeDbTest.envelope(req.getId(), f.annIds, IAGateway.ANNOTATION_MODE_EXTRACT));
        assertEquals(boxes, f.jobs.size(), "the intake published one job per box");
        return f;
    }

    static String status(String taskId) {
        Shepherd check = open("status");
        try {
            Task t = Task.load(taskId, check);
            return (t == null) ? null : t.getStoredStatus();
        } finally {
            check.rollbackAndClose();
        }
    }

    static String assetStatus(int assetId) {
        Shepherd check = open("asset");
        try {
            return check.getMediaAsset(String.valueOf(assetId)).getDetectionStatus();
        } finally {
            check.rollbackAndClose();
        }
    }

    static int embeddings(String annId) {
        Shepherd check = open("embeddings");
        try {
            Annotation a = check.getAnnotation(annId);
            return (a == null) ? -1 : a.numberEmbeddings();
        } finally {
            check.rollbackAndClose();
        }
    }

    /** The run umbrellas under the child (an imported child parents its own run). */
    static List<Task> runsUnder(String childId) {
        Shepherd check = open("runs");
        try {
            Task child = Task.load(childId, check);
            List<Task> runs = new ArrayList<Task>();
            if (child.getChildren() != null)
                for (Task t : child.getChildren()) if (Task.isUmbrella(t)) runs.add(t);
            // the umbrella's parameters are read while attached
            for (Task u : runs) {
                assertNotNull(u.getParameters());
                assertFalse(u.getParameters().has(Task.PARAM_IMPORTED_BOX), "no orchestration marker on a run");
                assertTrue(u.getParameters().has("importTaskId"));
            }
            return runs;
        } finally {
            check.rollbackAndClose();
        }
    }

    static void deleteAnnotation(String annId) {
        Shepherd del = open("delete");
        try {
            Annotation gone = del.getAnnotation(annId);
            Encounter enc = gone.findEncounter(del);
            if (enc != null) enc.removeAnnotation(gone);
            del.getPM().deletePersistent(gone);
            del.commitDBTransaction();
        } finally {
            del.closeDBTransaction();
        }
    }

    static void failChildForTest(String childId, String code) {
        Shepherd sh = open("failChild");
        try {
            ImportedBoxes.failChild(sh, Task.load(childId, sh), code, "test");
            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
    }

    // ---- the lifecycle ----

    @Test void firstBoxDefersUntilItsSiblingExtracts() throws Exception {
        Fixture f = boxedImport(2, false);
        String a = f.annIds.get(0);
        MlServiceJobOutcome out = processor.process(f.jobFor(a));
        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertEquals(1, embeddings(a));
        assertEquals("processing", status(f.childFor(a)), "the child stays non-terminal until its run exists");
        assertNull(assetStatus(f.assetId), "the sibling box is still pending");
        assertEquals(1, publisher.published.size(), "the real gate deferred on the sibling");
        assertEquals(0, dispatcher.vectorRuns.size());
    }

    @Test void lastBoxSettlesTheAssetAndBothRunsAreCreated() throws Exception {
        Fixture f = boxedImport(2, false);
        String a = f.annIds.get(0), b = f.annIds.get(1);
        processor.process(f.jobFor(a));
        dispatcher.childId = f.childFor(b);
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(f.jobFor(b)).getKind());
        assertEquals(IBEISIA.STATUS_COMPLETE, assetStatus(f.assetId), "every box on the asset is extracted");
        assertEquals(1, runsUnder(f.childFor(b)).size(), "B's run was created");
        assertEquals("completed", status(f.childFor(b)));
        assertEquals(1, dispatcher.statuses.size());
        assertEquals("processing", dispatcher.statuses.get(0), "the child was still non-terminal when its run was dispatched");

        dispatcher.childId = f.childFor(a);
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(publisher.published.get(0)).getKind());
        assertEquals(1, runsUnder(f.childFor(a)).size(), "A's deferred match created its run");
        assertEquals("completed", status(f.childFor(a)));
    }

    @Test void settleAfterSiblingError() throws Exception {
        Fixture f = boxedImport(2, false);
        String a = f.annIds.get(0), b = f.annIds.get(1);
        processor.process(f.jobFor(a));
        client.throwNonRetryable = true;
        MlServiceJobOutcome out = processor.process(f.jobFor(b));
        assertNotEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertEquals("error", status(f.childFor(b)));
        assertEquals(IBEISIA.STATUS_COMPLETE, assetStatus(f.assetId), "A has its embedding and B's task is terminal");
    }

    @Test void deferredReFireProceedsAfterSiblingEnqueueFailure() throws Exception {
        Fixture f = boxedImport(2, false);
        String a = f.annIds.get(0), b = f.annIds.get(1);
        processor.process(f.jobFor(a));
        assertNull(assetStatus(f.assetId));
        failChildForTest(f.childFor(b), "ENQUEUE");
        assertEquals(IBEISIA.STATUS_COMPLETE, assetStatus(f.assetId), "an ENQUEUE failure settles the asset");
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(publisher.published.get(0)).getKind());
        assertEquals(1, runsUnder(f.childFor(a)).size());
        assertEquals("completed", status(f.childFor(a)));
    }

    @Test void embeddingExistsStillMatchesWithDefaultedConfig() throws Exception {
        configure(false, true);   // the _id_conf names no method/version: they come from the embedding
        Fixture f = boxedImport(1, false);
        String a = f.annIds.get(0);
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(f.jobFor(a)).getKind());
        assertEquals(1, client.calls);
        assertEquals(1, runsUnder(f.childFor(a)).size());
        assertEquals("completed", status(f.childFor(a)));
        JSONObject firstOpt = dispatcher.vectorOpts.get(0);

        // the same job again (a replay): no extraction, but a second run with the same identity
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(f.jobFor(a)).getKind());
        assertEquals(1, client.calls, "no second extraction");
        assertEquals(2, runsUnder(f.childFor(a)).size(), "embedding-exists still gates and matches");
        assertEquals(1, embeddings(a));
        JSONObject secondOpt = dispatcher.vectorOpts.get(1);
        assertEquals("miewid", secondOpt.getString("method"));
        assertEquals("msv4_v3", secondOpt.getString("version"));
        assertEquals(firstOpt.getString("method"), secondOpt.getString("method"));
        assertEquals(firstOpt.getString("version"), secondOpt.getString("version"));
    }

    @Test void pendingSpeciesIsErrorForImportedBox() throws Exception {
        Fixture f = boxedImport(1, false);
        configure(true, false);   // the extractor configuration is gone by the time the job runs
        String a = f.annIds.get(0);
        MlServiceJobOutcome out = processor.process(f.jobFor(a));
        assertNotEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertEquals("error", status(f.childFor(a)));
        assertEquals("PENDING_SPECIES", errorCodeOf(f.childFor(a)));
        assertEquals(IBEISIA.STATUS_COMPLETE, assetStatus(f.assetId));
        assertEquals(0, client.calls);
    }

    @Test void annotationDeletedDuringExtractionIsErrorStale() throws Exception {
        Fixture f = boxedImport(1, false);
        String a = f.annIds.get(0);
        client.deleteAnnotationId = a;
        MlServiceJobOutcome out = processor.process(f.jobFor(a));
        assertNotEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertEquals("error", status(f.childFor(a)));
        assertEquals("STALE", errorCodeOf(f.childFor(a)));
        assertEquals(IBEISIA.STATUS_COMPLETE, assetStatus(f.assetId), "settled from the asset the task holds");
    }

    @Test void annotationDeletedBeforeDeferredReFireIsErrorStale() throws Exception {
        Fixture f = boxedImport(2, false);
        String a = f.annIds.get(0), b = f.annIds.get(1);
        processor.process(f.jobFor(a));
        failChildForTest(f.childFor(b), "ENQUEUE");   // the asset settles, so the re-fire reaches the match
        deleteAnnotation(a);
        MlServiceJobOutcome out = processor.process(publisher.published.get(0));
        assertNotEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertEquals("error", status(f.childFor(a)));
        assertEquals("STALE", errorCodeOf(f.childFor(a)));
        assertEquals(IBEISIA.STATUS_COMPLETE, assetStatus(f.assetId));
    }

    @Test void skipIdentCompletesTheChildWithoutARun() throws Exception {
        Fixture f = boxedImport(1, true);
        String a = f.annIds.get(0);
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(f.jobFor(a)).getKind());
        assertEquals(1, embeddings(a), "extraction still ran");
        assertEquals("completed", status(f.childFor(a)));
        assertEquals(0, runsUnder(f.childFor(a)).size(), "no run when identification is skipped");
        assertEquals(0, dispatcher.vectorRuns.size());
        assertEquals(IBEISIA.STATUS_COMPLETE, assetStatus(f.assetId));
    }

    @Test void detectionChildLifecycleUnchanged() throws Exception { // (regression)
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter enc = boxedEncounter(sh, ML_GENUS, ML_SPECIES, importId, 1);
        String a = annotationIds(enc).get(0);
        int assetId = fx(enc).assetIdByAnn.get(a).intValue();
        Task root = new Task();
        root.setParameters(new JSONObject().put("importTaskId", importId).put("skipIdent", false));
        Task species = new Task(root);
        List<MediaAsset> assets = new ArrayList<MediaAsset>();
        assets.add(sh.getMediaAsset(String.valueOf(assetId)));
        assets.get(0).setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);   // as detection leaves it
        species.setObjectMediaAssets(assets);
        Task child = new Task(species);              // a plain extraction child: no importedBox marker
        child.addObject(sh.getAnnotation(a));
        child.addParameter(Task.PARAM_EMBEDDING_EXTRACTION, true);
        sh.getPM().makePersistent(root);
        sh.getPM().makePersistent(species);
        sh.getPM().makePersistent(child);
        String childId = child.getId();
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        JSONObject job = new JSONObject().put("mlServiceV2", true).put("annotationId", a)
            .put("taxonomyString", "Pseudobatos lentiginosus").put("taskId", childId).put("__context", CTX);
        dispatcher.childId = childId;
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(job).getKind());
        assertEquals("completed", dispatcher.statuses.get(0), "today's extraction child is completed before its match");
        assertEquals("completed", status(childId));
        assertEquals(IBEISIA.STATUS_COMPLETE_MLSERVICE, assetStatus(assetId), "untouched: no settle for a child without the marker");
    }

    /** The task's last error code, read while the task is attached (fields are gone after close). */
    static String errorCodeOf(String id) {
        Shepherd check = open("load");
        try {
            return errorCode(Task.load(id, check));
        } finally {
            check.rollbackAndClose();
        }
    }


    // ---- an imported-box child works only on the box the server stored on it ----

    /** Every job shape that names child A but not A's own box is dropped with nothing changed. */
    void assertForeignJobsDropped(boolean skipIdent) throws Exception {
        Fixture f = boxedImport(2, skipIdent);
        String a = f.annIds.get(0), b = f.annIds.get(1);
        Shepherd sh = open("fixture");
        Encounter plain = encounter(sh, ML_GENUS, ML_SPECIES, BODY);
        Encounter otherImport = boxedEncounter(sh, ML_GENUS, ML_SPECIES, nextImportId(), 1);
        String plainId = annotationIds(plain).get(0);
        String otherId = annotationIds(otherImport).get(0);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String childA = f.childFor(a);
        String statusBefore = status(childA);
        String assetBefore = assetStatus(f.assetId);

        List<JSONObject> jobs = new ArrayList<JSONObject>();
        for (String foreign : new String[] { plainId, otherId, b, "no-such-annotation" }) {
            jobs.add(new JSONObject(f.jobFor(a).toString()).put("annotationId", foreign));
            jobs.add(new JSONObject().put("mlServiceV2", true).put("deferredMatch", true)
                .put("taskId", childA).put("annotationIds", new JSONArray().put(foreign)).put("__context", CTX));
        }
        jobs.add(new JSONObject().put("mlServiceV2", true).put("deferredMatch", true).put("taskId", childA)
            .put("annotationIds", new JSONArray().put(a).put(plainId)).put("__context", CTX));
        jobs.add(new JSONObject().put("mlServiceV2", true).put("deferredMatch", true).put("taskId", childA)
            .put("annotationIds", new JSONArray()).put("__context", CTX));
        jobs.add(new JSONObject().put("mlServiceV2", true).put("mediaAssetId", f.assetId)
            .put("taxonomyString", "Pseudobatos lentiginosus").put("taskId", childA).put("__context", CTX));
        // the child's own job with a media asset added: it would be dispatched as detection
        jobs.add(new JSONObject(f.jobFor(a).toString()).put("mediaAssetId", f.assetId));
        for (JSONObject job : jobs) {
            MlServiceJobOutcome out = processor.process(job);
            assertEquals(MlServiceJobOutcome.Kind.STALE, out.getKind(), "dropped: " + job);
            assertEquals(statusBefore, status(childA), "the child is untouched by " + job);
            assertEquals(assetBefore, assetStatus(f.assetId), "the asset is untouched by " + job);
        }
        for (String foreign : new String[] { plainId, otherId, b }) {
            assertEquals(0, embeddings(foreign), "nothing was extracted for " + foreign);
            Shepherd check = open("check");
            try {
                assertFalse(Task.load(childA, check).holdsAnnotationId(foreign));
                if (!foreign.equals(b))
                    assertNull(check.getAnnotation(foreign).getIdentificationStatus(), "status untouched for " + foreign);
            } finally {
                check.rollbackAndClose();
            }
        }
        assertEquals(0, client.calls);
        assertEquals(0, publisher.published.size(), "no deferred match was published");
        assertEquals(0, dispatcher.published.size(), "no identification run");
    }

    @Test void jobsNotNamingTheChildsOwnBoxAreDropped() throws Exception {
        assertForeignJobsDropped(false);
    }

    @Test void jobsNotNamingTheChildsOwnBoxAreDroppedWhenIdentificationIsSkipped() throws Exception {
        assertForeignJobsDropped(true);
    }

    /** A processor whose check fails its task read (whole Shepherd) or its annotation read, once. */
    MlServiceProcessor flakyCheck(boolean failAnnotationRead, java.util.concurrent.atomic.AtomicBoolean failed) {
        return new MlServiceProcessor(CTX, client, null, publisher) {
            @Override Shepherd openShepherd(String action) {
                Shepherd real = super.openShepherd(action);
                if (!"importedBoxJobMismatch".equals(action) || failed.get()) return real;
                Shepherd spied = org.mockito.Mockito.spy(real);
                if (!failAnnotationRead) {
                    org.mockito.Mockito.doAnswer(inv -> {
                        if (failed.getAndSet(true)) return inv.callRealMethod();
                        throw new javax.jdo.JDODataStoreException("datastore down");
                    }).when(spied).getPM();
                    return spied;
                }
                javax.jdo.PersistenceManager pm = org.mockito.Mockito.spy(real.getPM());
                org.mockito.Mockito.doReturn(pm).when(spied).getPM();
                org.mockito.Mockito.doAnswer(inv -> {
                    if (failed.getAndSet(true)) return inv.callRealMethod();
                    throw new javax.jdo.JDODataStoreException("datastore down");
                }).when(pm).getObjectById(eq(Annotation.class), any());
                return spied;
            }
        };
    }

    @Test void taskLookupFailureInTheCheckIsAnErrorNotAPass() throws Exception {
        Fixture f = boxedImport(1, false);
        String a = f.annIds.get(0);
        String childA = f.childFor(a);
        Shepherd sh = open("fixture");
        Encounter plain = encounter(sh, ML_GENUS, ML_SPECIES, BODY);
        String plainId = annotationIds(plain).get(0);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String statusBefore = status(childA);
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
        JSONObject foreign = new JSONObject(f.jobFor(a).toString()).put("annotationId", plainId);
        MlServiceJobOutcome out = flakyCheck(false, failed).process(foreign);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_PERSIST, out.getKind(), "a failed check never lets the job through");
        assertTrue(failed.get());
        assertEquals(statusBefore, status(childA));
        assertEquals(0, client.calls);
        assertEquals(0, embeddings(plainId));
    }

    @Test void annotationLookupFailureInTheCheckIsAnErrorNotAPass() throws Exception {
        // the child's own box, but stamped by another import: only the annotation read can tell
        Fixture f = boxedImport(1, false);
        String a = f.annIds.get(0);
        String childA = f.childFor(a);
        restamp(childA, "some-other-import");
        String statusBefore = status(childA);
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
        MlServiceJobOutcome out = flakyCheck(true, failed).process(f.jobFor(a));
        assertEquals(MlServiceJobOutcome.Kind.ERROR_PERSIST, out.getKind(), "a failed read is not a deleted annotation");
        assertTrue(failed.get());
        assertEquals(statusBefore, status(childA));
        assertEquals(0, client.calls);
        assertEquals(0, embeddings(a));
        assertEquals(0, publisher.published.size());
    }

    @Test void unreadableStoredParametersAreAnErrorNotAPass() throws Exception {
        Fixture f = boxedImport(1, false);
        String a = f.annIds.get(0);
        String childA = f.childFor(a);
        // "none" and "unknown" are stored text that some helpers treat as "no value"
        for (String corrupt : new String[] { "{\"importedBox\":true,", "none", "Unknown" }) {
            Shepherd sh = open("corrupt");
            try {
                Task.load(childA, sh).setParameters(corrupt);
                sh.commitDBTransaction();
            } finally {
                sh.closeDBTransaction();
            }
            MlServiceJobOutcome out = processor.process(f.jobFor(a));
            assertEquals(MlServiceJobOutcome.Kind.ERROR_PERSIST, out.getKind(), "stored parameters: " + corrupt);
        }
        assertEquals(0, client.calls);
        assertEquals(0, embeddings(a));
    }

    static void restamp(String childId, String importId) {
        Shepherd sh = open("restamp");
        try {
            Task child = Task.load(childId, sh);
            JSONObject params = child.getParameters();
            params.put("importTaskId", importId);
            child.setParameters(params);
            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
    }

    @Test void legitimateDeferredMatchPassesTheCheck() throws Exception {
        Fixture f = boxedImport(1, true);   // skipIdent: the deferred job completes the child
        String a = f.annIds.get(0);
        String childA = f.childFor(a);
        JSONObject deferred = new JSONObject().put("mlServiceV2", true).put("deferredMatch", true)
            .put("taskId", childA).put("annotationIds", new JSONArray().put(a)).put("__context", CTX);
        assertEquals(MlServiceJobOutcome.Kind.OK, processor.process(deferred).getKind());
        assertEquals("completed", status(childA));
    }

    @Test void childHoldingAnotherImportsBoxDoesNotExtractIt() throws Exception {
        // a child whose stored job names a box that carries a different import's stamp
        Fixture f = boxedImport(1, false);
        String a = f.annIds.get(0);
        String childA = f.childFor(a);
        restamp(childA, "some-other-import");
        String statusBefore = status(childA);
        MlServiceJobOutcome out = processor.process(f.jobFor(a));
        assertEquals(MlServiceJobOutcome.Kind.STALE, out.getKind());
        assertEquals(statusBefore, status(childA));
        assertEquals(0, client.calls);
        assertEquals(0, embeddings(a));
    }
}
