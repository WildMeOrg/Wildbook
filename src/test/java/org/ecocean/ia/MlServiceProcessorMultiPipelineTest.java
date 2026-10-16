package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.IAJsonProperties;
import org.ecocean.MultiPipelineIAJson;
import org.ecocean.Taxonomy;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for running several ml-service pipelines per species
 * (docs/plans/2026-10-16-mlservice-multi-config.md).
 */
class MlServiceProcessorMultiPipelineTest {

    // --- model id identity ----------------------------------------------

    @Test void splitModelIdSplitsOnFirstDash() {
        assertArrayEquals(new String[] { "miewid", "msv4.1" },
            MlServiceProcessor.splitModelId("miewid-msv4.1"));
        assertArrayEquals(new String[] { "miewid", "pet_v1.1.0" },
            MlServiceProcessor.splitModelId("miewid-pet_v1.1.0"));
        assertArrayEquals(new String[] { "miewid", "pet-v1" },
            MlServiceProcessor.splitModelId("miewid-pet-v1"));
    }

    @Test void splitModelIdNullWithoutUsableDash() {
        assertNull(MlServiceProcessor.splitModelId("msv3"));
        assertNull(MlServiceProcessor.splitModelId("-msv3"));
        assertNull(MlServiceProcessor.splitModelId("msv3-"));
        assertNull(MlServiceProcessor.splitModelId(""));
        assertNull(MlServiceProcessor.splitModelId(null));
    }

    @Test void extractorIdentityReadsExtractModelId() {
        assertArrayEquals(new String[] { "miewid", "pet_v1.1.0" },
            MlServiceProcessor.extractorIdentity(
                new JSONObject().put("extract_model_id", "miewid-pet_v1.1.0")));
        assertNull(MlServiceProcessor.extractorIdentity(
            new JSONObject().put("predict_model_id", "MDV6-yolov9-c")));
        assertNull(MlServiceProcessor.extractorIdentity(null));
    }

    private static final Taxonomy DOG = MultiPipelineIAJson.DOG;
    private static final String[] MSV41 = { "miewid", "msv4.1" };
    private static final String[] PET = { "miewid", "pet_v1.1.0" };

    private static IAJsonProperties iac(JSONObject json) {
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac, "IAJsonProperties.iaConfig() must not be null in tests");
        iac.setJson(json);
        return iac;
    }

    // --- match config resolution ----------------------------------------

    @Test void resolveUsesClassDefaultVectorEntry() {
        JSONObject cfg = MlServiceProcessor.resolveMultiPipelineMatchConfig(
            iac(MultiPipelineIAJson.dogBodyAndFace()), DOG, "dog+face", PET,
            MultiPipelineIAJson.facePipeline());
        assertEquals("pet_v1.1.0", cfg.getString("version"));
        assertEquals("MiewID pet (vector)", cfg.getString("description"));
        assertEquals(MultiPipelineIAJson.FACE_ENDPOINT, cfg.getString("api_endpoint"),
            "api_endpoint defaults to the producing pipeline's");
    }

    @Test void resolveFallsBackToDefaultWhenClassHasNoIdConf() {
        // "animal" has no _id_conf; _default is given a description distinct
        // from the "dog" class entry so the source is unambiguous.
        JSONObject json = MultiPipelineIAJson.dogBodyAndFace();
        json.getJSONObject("Canis").getJSONObject("familiaris").getJSONObject("_default")
            .put("_id_conf", MultiPipelineIAJson.idConf("msv4.1", "species default"));
        JSONObject cfg = MlServiceProcessor.resolveMultiPipelineMatchConfig(iac(json), DOG,
            "animal", MSV41, MultiPipelineIAJson.bodyPipeline());
        assertEquals("species default", cfg.getString("description"));
        assertEquals(MultiPipelineIAJson.BODY_ENDPOINT, cfg.getString("api_endpoint"));
    }

    @Test void resolveFallsBackToEmbeddingWhenConfiguredModelDiffers() {
        // dog+face is configured for the pet model, but this annotation was
        // embedded with msv4.1: match in msv4.1 space, not the pet corpus.
        JSONObject cfg = MlServiceProcessor.resolveMultiPipelineMatchConfig(
            iac(MultiPipelineIAJson.dogBodyAndFace()), DOG, "dog+face", MSV41,
            MultiPipelineIAJson.bodyPipeline());
        assertEquals("vector", cfg.getString("pipeline_root"));
        assertEquals("miewid", cfg.getString("method"));
        assertEquals("msv4.1", cfg.getString("version"));
        assertEquals("body pipeline", cfg.getString("description"));
        assertEquals(MultiPipelineIAJson.BODY_ENDPOINT, cfg.getString("api_endpoint"));
    }

    @Test void resolveKeepsExplicitEndpoint() {
        JSONObject json = MultiPipelineIAJson.dogBodyAndFace();
        json.getJSONObject("Canis").getJSONObject("familiaris").getJSONObject("dog")
            .getJSONArray("_id_conf").getJSONObject(0).put("api_endpoint", "http://other");
        JSONObject cfg = MlServiceProcessor.resolveMultiPipelineMatchConfig(iac(json), DOG,
            "dog", MSV41, MultiPipelineIAJson.bodyPipeline());
        assertEquals("http://other", cfg.getString("api_endpoint"));
    }

    @Test void resolveWithoutConfigUsesEmbedding() {
        JSONObject cfg = MlServiceProcessor.resolveMultiPipelineMatchConfig(null, null, "dog",
            PET, null);
        assertEquals("pet_v1.1.0", cfg.getString("version"));
        assertEquals("ml-service vector matching", cfg.getString("description"));
        assertFalse(cfg.has("api_endpoint"));
    }

    // --- grouping -------------------------------------------------------

    private static MlServiceProcessor.DetectedAnnotation det(String id, String iaClass,
        String[] mv, JSONObject producedBy) {
        return new MlServiceProcessor.DetectedAnnotation(id, iaClass, mv, producedBy);
    }

    @Test void groupsBodyAndFaceSeparatelyInFirstSeenOrder() {
        JSONObject body = MultiPipelineIAJson.bodyPipeline();
        JSONObject face = MultiPipelineIAJson.facePipeline();
        List<MlServiceProcessor.MatchGroup> groups = MlServiceProcessor.groupByMatchConfig(
            iac(MultiPipelineIAJson.dogBodyAndFace()), DOG, Arrays.asList(
                det("b1", "dog", MSV41, body), det("f1", "dog+face", PET, face),
                det("b2", "dog", MSV41, body), det("f1", "dog+face", PET, face)));
        assertEquals(2, groups.size());
        assertEquals("msv4.1", groups.get(0).matchConfig.getString("version"));
        assertEquals(Arrays.asList("b1", "b2"), groups.get(0).annotationIds);
        assertEquals("pet_v1.1.0", groups.get(1).matchConfig.getString("version"));
        assertEquals(Arrays.asList("f1"), groups.get(1).annotationIds,
            "an annotation id is matched once");
    }

    @Test void groupingSkipsNullsAndHandlesEmpty() {
        assertTrue(MlServiceProcessor.groupByMatchConfig(null, null, null).isEmpty());
        List<MlServiceProcessor.DetectedAnnotation> in = new ArrayList<>();
        in.add(null);
        in.add(det(null, "dog", MSV41, null));
        assertTrue(MlServiceProcessor.groupByMatchConfig(null, null, in).isEmpty());
    }

    // --- multi-pipeline config validation ---------------------------------

    @Test void validMultiPipelineEntriesPass() {
        assertNull(MlServiceProcessor.invalidMultiPipelineEntry(bodyThenFace()));
    }

    @Test void multiPipelineEntryNeedsPredictModelId() {
        List<JSONObject> pipelines = new ArrayList<>(bodyThenFace());
        pipelines.get(1).remove("predict_model_id");
        assertEquals("_mlservice_conf[1] missing predict_model_id",
            MlServiceProcessor.invalidMultiPipelineEntry(pipelines));
        pipelines.set(1, null);
        assertEquals("_mlservice_conf[1] missing predict_model_id",
            MlServiceProcessor.invalidMultiPipelineEntry(pipelines));
    }

    @Test void multiPipelineEntryNeedsSplittableExtractModelId() {
        List<JSONObject> pipelines = new ArrayList<>(bodyThenFace());
        pipelines.get(0).put("extract_model_id", "msv3");
        assertTrue(MlServiceProcessor.invalidMultiPipelineEntry(pipelines)
            .startsWith("_mlservice_conf[0]: "));
        pipelines.get(0).remove("extract_model_id");
        assertTrue(MlServiceProcessor.invalidMultiPipelineEntry(pipelines)
            .startsWith("_mlservice_conf[0]: "));
    }

    // --- pipeline fan-out -------------------------------------------------

    /** Records each /pipeline/ call; can fail on the Nth call. */
    private static final class StubClient extends MlServiceClient {
        final List<String> calls = new ArrayList<>();
        int failOnCall = -1;
        boolean retryable;

        @Override public JSONObject pipeline(String apiEndpoint, String imageUri,
            JSONObject config) throws IAException {
            calls.add(apiEndpoint + " " + config.optString("predict_model_id"));
            if (calls.size() == failOnCall)
                throw new IAException("SERVER_ERROR", "boom", retryable, retryable);
            JSONArray results = new JSONArray();
            if (config.optString("predict_model_id").startsWith("MDV6")) {
                results.put(new JSONObject().put("detection_class", "animal"));
            } else {
                results.put(new JSONObject().put("detection_class", "dog_face"));
                results.put(new JSONObject().put("detection_class", "dog_face"));
            }
            return new JSONObject().put("results", results);
        }
    }

    private static List<JSONObject> bodyThenFace() {
        return Arrays.asList(MultiPipelineIAJson.bodyPipeline(),
            MultiPipelineIAJson.facePipeline());
    }

    @Test void runPipelinesCallsEachEntryOnItsOwnEndpointInOrder() throws IAException {
        StubClient client = new StubClient();
        List<JSONObject> pipelines = bodyThenFace();
        MlServiceProcessor.PipelineRun run = MlServiceProcessor.runPipelines(client, pipelines,
            "http://img");
        assertEquals(Arrays.asList(
            MultiPipelineIAJson.BODY_ENDPOINT + " MDV6-yolov9-c",
            MultiPipelineIAJson.FACE_ENDPOINT + " obb-animal-face-v2"), client.calls);
        assertEquals(3, run.results.length());
        assertEquals(3, run.producedBy.size());
        assertEquals("animal", run.results.getJSONObject(0).getString("detection_class"));
        assertSame(pipelines.get(0), run.producedBy.get(0));
        assertSame(pipelines.get(1), run.producedBy.get(1));
        assertSame(pipelines.get(1), run.producedBy.get(2));
    }

    @Test void runPipelinesPropagatesFailureOfLaterEntry() {
        StubClient client = new StubClient();
        client.failOnCall = 2;
        client.retryable = true;
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceProcessor.runPipelines(client, bodyThenFace(), "http://img"));
        assertTrue(ex.shouldRequeue());
        assertEquals(2, client.calls.size());
    }

    @Test void runPipelinesStopsAtFirstFailure() {
        StubClient client = new StubClient();
        client.failOnCall = 1;
        assertThrows(IAException.class,
            () -> MlServiceProcessor.runPipelines(client, bodyThenFace(), "http://img"));
        assertEquals(1, client.calls.size(), "nothing after a failed pipeline is called");
    }

    // --- class-aware dedup ------------------------------------------------

    private static MediaAsset maWithBox(String iaClass) {
        MediaAsset ma = new MediaAsset(null, null);
        JSONObject p = new JSONObject().put("x", 10).put("y", 20).put("width", 30)
            .put("height", 40).put("theta", 0.0);
        Feature ft = new Feature(new FeatureType("org.ecocean.boundingBox"), p);
        Annotation ann = new Annotation("Canis familiaris", ft);
        ann.setIAClass(iaClass);
        ft.setAnnotation(ann);
        ma.addFeature(ft);
        return ma;
    }

    @Test void classAwareDedupKeepsBodyAndPartWithSameGeometry() {
        MediaAsset ma = maWithBox("dog");
        double[] box = { 10, 20, 30, 40 };
        assertNotNull(MlServiceProcessor.findExistingAnnotation(ma, box, 0.0, "dog"));
        assertNull(MlServiceProcessor.findExistingAnnotation(ma, box, 0.0, "dog+face"));
        assertNull(MlServiceProcessor.findExistingAnnotation(ma, box, 0.0, null));
        // the geometry-only (single-pipeline) dedup still matches across classes
        assertNotNull(MlServiceProcessor.findExistingAnnotation(ma, box, 0.0));
    }

    @Test void classAwareDedupStillChecksGeometry() {
        MediaAsset ma = maWithBox("dog");
        assertNull(MlServiceProcessor.findExistingAnnotation(ma,
            new double[] { 11, 20, 30, 40 }, 0.0, "dog"));
        assertNull(MlServiceProcessor.findExistingAnnotation(ma,
            new double[] { 10, 20, 30, 40 }, 0.5, "dog"));
    }

    // --- canonical group key ----------------------------------------------

    @Test void canonicalJsonIgnoresKeyOrder() {
        JSONObject a = new JSONObject().put("method", "miewid").put("version", "msv4.1")
            .put("q", new JSONObject().put("x", 1).put("y", "z"));
        JSONObject b = new JSONObject().put("q", new JSONObject().put("y", "z").put("x", 1))
            .put("version", "msv4.1").put("method", "miewid");
        assertEquals(MlServiceProcessor.canonicalJson(a), MlServiceProcessor.canonicalJson(b));
    }

    @Test void groupsDistinguishConfigsBeyondIdentityAndDelimiters() {
        // Same method/version/endpoint/description, different extra field;
        // and values containing the old "|" delimiter.
        JSONObject json = MultiPipelineIAJson.dogBodyAndFace();
        JSONObject species = json.getJSONObject("Canis").getJSONObject("familiaris");
        species.put("pup", new JSONObject().put("_id_conf",
            MultiPipelineIAJson.idConf("msv4.1", "MiewID v4.1 (vector)")));
        species.getJSONObject("pup").getJSONArray("_id_conf").getJSONObject(0)
            .put("k", 5);
        species.getJSONObject("dog").getJSONArray("_id_conf").getJSONObject(0)
            .put("description", "a|b");
        species.put("cat", new JSONObject().put("_id_conf",
            MultiPipelineIAJson.idConf("msv4.1", "a")));
        species.getJSONObject("cat").getJSONArray("_id_conf").getJSONObject(0)
            .put("api_endpoint", "b|" + MultiPipelineIAJson.BODY_ENDPOINT);
        JSONObject body = MultiPipelineIAJson.bodyPipeline();
        List<MlServiceProcessor.MatchGroup> groups = MlServiceProcessor.groupByMatchConfig(
            iac(json), DOG, Arrays.asList(det("d1", "dog", MSV41, body),
            det("p1", "pup", MSV41, body), det("c1", "cat", MSV41, body)));
        assertEquals(3, groups.size());
    }

    // --- match groups stay local across deferral ----------------------------

    private static final class Recorder implements DeferredMatchPublisher {
        final List<JSONObject> published = new ArrayList<>();
        @Override public void publish(JSONObject payload) { published.add(payload); }
    }

    private static final class DeferGate implements MatchVisibilityGate {
        @Override public GateOutcome gateForBatch(Collection<String> ids, String taskId,
            JSONObject matchConfig, int attempt, Long firstDeferredAt) {
            return GateOutcome.defer(attempt, 1000L, "sibling pending");
        }
    }

    private static MlServiceProcessor deferringProcessor(Recorder publisher) {
        return new MlServiceProcessor("context0", new MlServiceClient(), new DeferGate(),
            publisher) {
            @Override boolean readSkipIdent(String taskId) { return false; }
        };
    }

    private static JSONObject deferredPayload(Boolean matchGroup) {
        JSONObject jo = new JSONObject().put("mlServiceV2", true).put("deferredMatch", true)
            .put("annotationIds", new JSONArray().put("f1")).put("taskId", "task-1")
            .put("matchConfig", new JSONObject().put("method", "miewid")
                .put("version", "pet_v1.1.0"))
            .put("attempt", 2).put("firstDeferredAt", 1000L);
        if (matchGroup != null) jo.put("matchGroup", matchGroup.booleanValue());
        return jo;
    }

    @Test void deferredMatchGroupKeepsItsFlagOnReFire() {
        Recorder publisher = new Recorder();
        deferringProcessor(publisher).runDeferredMatch(deferredPayload(true));
        assertEquals(1, publisher.published.size());
        assertTrue(publisher.published.get(0).optBoolean("matchGroup", false));
        assertEquals("pet_v1.1.0",
            publisher.published.get(0).getJSONObject("matchConfig").getString("version"));
    }

    @Test void singlePipelineDeferredPayloadHasNoMatchGroupKey() {
        Recorder publisher = new Recorder();
        deferringProcessor(publisher).runDeferredMatch(deferredPayload(null));
        assertEquals(1, publisher.published.size());
        assertFalse(publisher.published.get(0).has("matchGroup"),
            "single-pipeline deferred payloads must be unchanged");
    }

    @Test void emptyMatchGroupDoesNotTouchTheSharedParent() {
        // The non-group variant marks the parent complete through a Shepherd;
        // a group must return without any DB work (no Shepherd in this test).
        MlServiceProcessor p = deferringProcessor(new Recorder());
        assertEquals(MlServiceJobOutcome.Kind.OK,
            p.runMatchProspects(new ArrayList<String>(), "task-1", new JSONObject(), true)
                .getKind());
    }
}
