package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ecocean.servlet.IAGateway;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Bulk-import boxes PR A, task 6 (spec 4.4): handleRest validates the envelope shape before any
 * intake. Three shapes are permitted; everything else is rejected with a reason.
 */
class IAEnvelopeRejectionTest {
    private static JSONObject media(String... ids) {
        return new JSONObject().put("v2", true).put("mediaAssetIds", new JSONArray(ids));
    }

    private static JSONObject ann(String... ids) {
        return new JSONObject().put("v2", true).put("annotationIds", new JSONArray(ids));
    }

    @Test void permittedShapesAreNotRejected() {
        assertNull(IA.envelopeRejection(media("7")));
        assertNull(IA.envelopeRejection(ann("a1")));
        assertNull(IA.envelopeRejection(ann("a1").put("annotationMode", IAGateway.ANNOTATION_MODE_EXTRACT)));
        assertNull(IA.envelopeRejection(ann("a1").put("annotationMode", IAGateway.ANNOTATION_MODE_EXTRACT)
            .put("mediaAssetIds", new JSONArray())));
        assertNull(IA.envelopeRejection(new JSONObject().put("v2", true)));
        assertNull(IA.envelopeRejection(media("7").put("annotationIds", new JSONArray().put("a1"))), "today's mixed identification request stays accepted");
    }

    @Test void unknownModeIsRejected() {
        assertEquals("unknown annotationMode: bogus", IA.envelopeRejection(ann("a1").put("annotationMode", "bogus")));
        assertEquals("unknown annotationMode: bogus",
            IA.envelopeRejection(media("7").put("annotationIds", new JSONArray().put("a1")).put("annotationMode", "bogus")));
    }

    @Test void extractionModeWithMediaIsRejected() {
        assertEquals("extraction mode with mediaAssetIds",
            IA.envelopeRejection(ann("a1").put("annotationMode", IAGateway.ANNOTATION_MODE_EXTRACT)
                .put("mediaAssetIds", new JSONArray().put("7"))));
    }

    @Test void modeWithoutAnnotationsIsRejected() {
        assertEquals("annotationMode without annotationIds",
            IA.envelopeRejection(media("7").put("annotationMode", IAGateway.ANNOTATION_MODE_EXTRACT)));
        assertEquals("annotationMode without annotationIds",
            IA.envelopeRejection(new JSONObject().put("v2", true).put("annotationMode", IAGateway.ANNOTATION_MODE_EXTRACT)
                .put("annotationIds", new JSONArray())));
    }
}
