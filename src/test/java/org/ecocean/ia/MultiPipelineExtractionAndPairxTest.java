package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.IAJsonProperties;
import org.ecocean.MultiPipelineIAJson;
import org.ecocean.Taxonomy;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Multi-pipeline species (docs/plans/2026-10-16-mlservice-multi-config.md):
 * the manual/API extraction path (MLService.send) and PairX config selection.
 */
class MultiPipelineExtractionAndPairxTest {
    private static final Taxonomy DOG = MultiPipelineIAJson.DOG;
    private static final String DOG_TX = "Canis familiaris";

    private static IAJsonProperties iac(JSONObject json) {
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac, "IAJsonProperties.iaConfig() must not be null in tests");
        iac.setJson(json);
        return iac;
    }

    private static Annotation ann(String iaClass) {
        Feature ft = new Feature(new FeatureType("org.ecocean.boundingBox"),
            new JSONObject().put("x", 0).put("y", 0).put("width", 10).put("height", 10));
        return new Annotation(DOG_TX, ft, iaClass);
    }

    // --- MLService.send(Annotation) config selection --------------------

    @Test void multiPipelineAnnotationIsExtractedByItsClassExtractorOnly() throws IAException {
        MLService mls = new MLService(iac(MultiPipelineIAJson.dogBodyAndFace()));
        List<JSONObject> face = mls.extractionConfigs(ann("dog+face"), DOG_TX);
        assertEquals(1, face.size());
        assertEquals("miewid-pet_v1.1.0", face.get(0).getString("extract_model_id"));
        assertEquals(MultiPipelineIAJson.FACE_ENDPOINT, face.get(0).getString("api_endpoint"));
        List<JSONObject> body = mls.extractionConfigs(ann("dog"), DOG_TX);
        assertEquals(1, body.size());
        assertEquals("miewid-msv4.1", body.get(0).getString("extract_model_id"));
    }

    @Test void multiPipelineUnknownClassUsesFirstSpeciesEntryOnly() throws IAException {
        MLService mls = new MLService(iac(MultiPipelineIAJson.dogBodyAndFace()));
        List<JSONObject> confs = mls.extractionConfigs(ann("puppy"), DOG_TX);
        assertEquals(1, confs.size());
        assertEquals("MDV6-yolov9-c", confs.get(0).getString("predict_model_id"));
    }

    @Test void singlePipelineStillUsesEverySpeciesEntry() throws IAException {
        // Back-compat: not multi-pipeline (one active entry), so the legacy
        // getConfigs() list is returned untouched, class entries ignored.
        MLService mls = new MLService(iac(MultiPipelineIAJson.dogFaceOnly()));
        List<JSONObject> confs = mls.extractionConfigs(ann("dog_face"), DOG_TX);
        assertEquals(1, confs.size());
        assertEquals(MultiPipelineIAJson.facePipeline().toString(), confs.get(0).toString());
    }

    @Test void notVectorRoutedStillUsesEverySpeciesEntry() throws IAException {
        // Two entries but HotSpotter default: not multi-pipeline, legacy loop.
        JSONObject json = MultiPipelineIAJson.dogBodyAndFace();
        json.getJSONObject("Canis").getJSONObject("familiaris").put("_default",
            new JSONObject().put("_id_conf", new JSONArray()
                .put(new JSONObject().put("default", true).put("pipeline_root", "HotSpotter"))));
        MLService mls = new MLService(iac(json));
        assertEquals(2, mls.extractionConfigs(ann("dog+face"), DOG_TX).size());
    }

    @Test void extractionConfigsThrowsWhenSpeciesUnconfigured() {
        MLService mls = new MLService(iac(MultiPipelineIAJson.dogBodyAndFace()));
        assertThrows(IAException.class, () -> mls.extractionConfigs(ann("x"), "Felis catus"));
    }

    @Test void multiPipelineRejectsExtractorIdWithoutDash() {
        JSONObject json = MultiPipelineIAJson.dogBodyAndFace();
        json.getJSONObject("Canis").getJSONObject("familiaris").getJSONObject("dog+face")
            .getJSONArray("_mlservice_conf").getJSONObject(0).put("extract_model_id", "msv3");
        MLService mls = new MLService(iac(json));
        IAException ex = assertThrows(IAException.class,
            () -> mls.extractionConfigs(ann("dog+face"), DOG_TX));
        assertEquals("INVALID", ex.getCode());
        assertFalse(ex.shouldRequeue(), "a config error must not be retried");
    }

    @Test void annotationResultIdentitySplitsOnFirstDashOnlyForMultiPipeline() {
        JSONObject res = new JSONObject().put("model_id", "miewid-pet-v1");
        assertArrayEquals(new String[] { "miewid", "pet-v1" },
            MLService.annotationResultIdentity(res, true));
        // legacy rule kept for everything else: second dash token only
        assertArrayEquals(new String[] { "miewid", "pet" },
            MLService.annotationResultIdentity(res, false));
        // no dash: both rules fall through to getMethodValues
        assertArrayEquals(new String[] { "msv3", null },
            MLService.annotationResultIdentity(new JSONObject().put("model_id", "msv3"), true));
    }

    // --- PairX config selection -----------------------------------------

    @Test void pairxUsesConfigWhoseExtractorMatchesTheMatchIdentity() {
        IAJsonProperties iac = iac(MultiPipelineIAJson.dogBodyAndFace());
        JSONObject face = MatchResult.selectMultiPipelinePairxConfig(iac, DOG, "dog+face",
            "miewid", "pet_v1.1.0");
        assertEquals(MultiPipelineIAJson.FACE_ENDPOINT, face.getString("api_endpoint"));
        // identity wins over class: a dog+face query matched in msv4.1 space
        // (a §3 fallback) is explained with the msv4.1 extractor
        JSONObject body = MatchResult.selectMultiPipelinePairxConfig(iac, DOG, "dog+face",
            "miewid", "msv4.1");
        assertEquals(MultiPipelineIAJson.BODY_ENDPOINT, body.getString("api_endpoint"));
    }

    @Test void pairxFindsClassOnlyExtractor() {
        // An extractor configured only at class level is still found.
        JSONObject json = MultiPipelineIAJson.dogBodyAndFace();
        json.getJSONObject("Canis").getJSONObject("familiaris").getJSONObject("dog+face")
            .getJSONArray("_mlservice_conf").getJSONObject(0)
            .put("extract_model_id", "miewid-pet_v2");
        JSONObject conf = MatchResult.selectMultiPipelinePairxConfig(iac(json), DOG,
            "dog+face", "miewid", "pet_v2");
        assertNotNull(conf);
        assertEquals("miewid-pet_v2", conf.getString("extract_model_id"));
    }

    @Test void pairxSkipsWhenNoExtractorProducesTheMatchIdentity() {
        assertNull(MatchResult.selectMultiPipelinePairxConfig(
            iac(MultiPipelineIAJson.dogBodyAndFace()), DOG, "dog+face", "miewid", "msv3"));
    }

    @Test void pairxWithoutIdentityUsesClassExtractor() {
        IAJsonProperties iac = iac(MultiPipelineIAJson.dogBodyAndFace());
        assertEquals(MultiPipelineIAJson.FACE_ENDPOINT, MatchResult
            .selectMultiPipelinePairxConfig(iac, DOG, "dog+face", null, null)
            .getString("api_endpoint"));
        assertEquals(MultiPipelineIAJson.BODY_ENDPOINT, MatchResult
            .selectMultiPipelinePairxConfig(iac, DOG, "dog", "", "")
            .getString("api_endpoint"));
    }

    @Test void pairxSkipsPartialIdentity() {
        IAJsonProperties iac = iac(MultiPipelineIAJson.dogBodyAndFace());
        assertNull(MatchResult.selectMultiPipelinePairxConfig(iac, DOG, "dog", "miewid", null));
        assertNull(MatchResult.selectMultiPipelinePairxConfig(iac, DOG, "dog", null, "msv4.1"));
    }

    @Test void taskMatchIdentityReadsIbeisIdentification() {
        JSONObject params = new JSONObject().put("ibeis.identification",
            new JSONObject().put("method", "miewid").put("version", "pet_v1.1.0"));
        assertArrayEquals(new String[] { "miewid", "pet_v1.1.0" },
            MatchResult.taskMatchIdentity(params));
        assertArrayEquals(new String[] { null, null }, MatchResult.taskMatchIdentity(null));
        assertArrayEquals(new String[] { null, null },
            MatchResult.taskMatchIdentity(new JSONObject()));
    }
}
