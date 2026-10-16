package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
