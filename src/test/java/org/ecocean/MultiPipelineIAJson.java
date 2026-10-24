package org.ecocean;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Shared IA.json fixtures for the multiple-ml-service-pipelines tests
 * (docs/plans/2026-10-16-mlservice-multi-config.md): a dog with a body
 * pipeline on one ml-service and a face pipeline on another.
 */
public final class MultiPipelineIAJson {
    public static final Taxonomy DOG = new Taxonomy("Canis familiaris");
    public static final String BODY_ENDPOINT = "http://body.example.org:6050";
    public static final String FACE_ENDPOINT = "https://face.example.org:5050";

    private MultiPipelineIAJson() {}

    public static JSONObject bodyPipeline() {
        return new JSONObject()
            .put("api_endpoint", BODY_ENDPOINT)
            .put("description", "body pipeline")
            .put("predict_model_id", "MDV6-yolov9-c")
            .put("classify_model_id", "msv2_multilabel_effnet_v0")
            .put("extract_model_id", "miewid-msv4.1");
    }

    public static JSONObject facePipeline() {
        return new JSONObject()
            .put("api_endpoint", FACE_ENDPOINT)
            .put("description", "face pipeline")
            .put("predict_model_id", "obb-animal-face-v2")
            .put("extract_model_id", "miewid-pet_v1.1.0");
    }

    public static JSONArray idConf(String version, String description) {
        return new JSONArray()
            .put(new JSONObject()
                .put("pipeline_root", "vector")
                .put("method", "miewid")
                .put("version", version)
                .put("description", description)
                .put("default", true))
            .put(new JSONObject()
                .put("query_config_dict", new JSONObject().put("sv_on", false))
                .put("description", "HotSpotter pattern-matcher")
                .put("default", false));
    }

    /** Body + face pipelines; per-class extraction and match configs. */
    public static JSONObject dogBodyAndFace() {
        JSONObject species = new JSONObject()
            .put("_mlservice_conf", new JSONArray().put(bodyPipeline()).put(facePipeline()))
            .put("animal", new JSONObject().put("_save_as", "dog"))
            .put("dog", new JSONObject()
                .put("_save_as", "dog")
                .put("_id_conf", idConf("msv4.1", "MiewID v4.1 (vector)"))
                .put("_mlservice_conf", new JSONArray().put(bodyPipeline())))
            .put("dog_face", new JSONObject().put("_save_as", "dog+face"))
            .put("dog+face", new JSONObject()
                .put("_save_as", "dog+face")
                .put("_id_conf", idConf("pet_v1.1.0", "MiewID pet (vector)"))
                .put("_mlservice_conf", new JSONArray().put(facePipeline())))
            .put("_default", new JSONObject()
                .put("_id_conf", idConf("msv4.1", "MiewID v4.1 (vector)"))
                .put("_mlservice_conf", new JSONArray().put(bodyPipeline())));
        return new JSONObject().put("Canis", new JSONObject().put("familiaris", species));
    }

    /** Face pipeline only, as Whiskerbook was configured before this change. */
    public static JSONObject dogFaceOnly() {
        JSONObject species = new JSONObject()
            .put("_mlservice_conf", new JSONArray().put(facePipeline()))
            .put("dog_face", new JSONObject()
                .put("_id_conf", idConf("pet_v1.1.0", "MiewID pet (vector)"))
                .put("_mlservice_conf", new JSONArray().put(bodyPipeline())))
            .put("_default", new JSONObject()
                .put("_id_conf", idConf("pet_v1.1.0", "MiewID pet (vector)")));
        return new JSONObject().put("Canis", new JSONObject().put("familiaris", species));
    }
}
