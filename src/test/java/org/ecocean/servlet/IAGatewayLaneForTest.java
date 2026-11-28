package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Bulk-import boxes PR A, task 3 (spec 4.4): an extract-then-identify envelope enqueues per-box
 * jobs and intakes nothing synchronously, so it belongs on the detection lane, whatever the
 * fastlane flag says. The plain identification shapes keep their lanes.
 */
class IAGatewayLaneForTest {
    @Test void extractionModeGoesToTheDetectionLaneEvenWithFastlane() {
        JSONObject j = new JSONObject().put("v2", true).put("fastlane", true)
            .put("annotationIds", new JSONArray().put("a1"))
            .put("annotationMode", IAGateway.ANNOTATION_MODE_EXTRACT);
        assertEquals(IAGateway.LANE_DETECTION, IAGateway.laneFor(j));
    }

    @Test void plainAnnotationIdsKeepTheIaLane() { // (regression)
        JSONObject j = new JSONObject().put("v2", true)
            .put("annotationIds", new JSONArray().put("a1"));
        assertEquals(IAGateway.LANE_IA, IAGateway.laneFor(j));
        assertEquals(IAGateway.LANE_FASTLANE, IAGateway.laneFor(j.put("fastlane", true)));
    }

    @Test void mediaAssetIdsKeepTheDetectionLane() { // (regression)
        JSONObject j = new JSONObject().put("v2", true)
            .put("mediaAssetIds", new JSONArray().put("7"));
        assertEquals(IAGateway.LANE_DETECTION, IAGateway.laneFor(j));
    }
}
