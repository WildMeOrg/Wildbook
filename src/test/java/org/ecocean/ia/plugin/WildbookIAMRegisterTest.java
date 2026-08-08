package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * ml-service migration v2 §commit #11 fix-pass. Pure-function coverage of
 * the no-Shepherd WBIA registration helpers introduced in
 * {@link org.ecocean.ia.plugin.WildbookIAM}. Network-bound behavior of
 * {@code registerOneByDto} itself is exercised end-to-end by the
 * polling thread integration in a dev deployment; here we cover the
 * pieces that can be tested without WireMock/Tomcat.
 */
class WildbookIAMRegisterTest {

    private static WbiaRegisterRequest sampleDto() {
        return new WbiaRegisterRequest(
            "ann-uuid-1", "ann-acm-1", "ma-acm-1",
            new int[] { 10, 20, 100, 200 },
            0.0d, "right_dorsalfin", "indiv-1");
    }

    // --- buildForcedRequestMap -------------------------------------------

    @Test void buildForcedRequestMapPopulatesAllLists() {
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
        assertEquals(1, map.get("image_uuid_list").size());
        assertEquals(1, map.get("annot_uuid_list").size());
        assertEquals(1, map.get("annot_species_list").size());
        assertEquals(1, map.get("annot_bbox_list").size());
        assertEquals(1, map.get("annot_name_list").size());
        assertEquals(1, map.get("annot_theta_list").size());
        assertEquals("right_dorsalfin", map.get("annot_species_list").get(0));
        assertEquals("indiv-1", map.get("annot_name_list").get(0));
    }

    @Test void buildForcedRequestMapWrapsUuidsInFancyForm() {
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
        JSONObject annUuid = (JSONObject) map.get("annot_uuid_list").get(0);
        JSONObject imgUuid = (JSONObject) map.get("image_uuid_list").get(0);
        assertEquals("ann-uuid-1", WildbookIAM.fromFancyUUID(annUuid));
        assertEquals("ma-acm-1",   WildbookIAM.fromFancyUUID(imgUuid));
    }

    @Test void buildForcedRequestMapNullIndividualSerializesUnderscores() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-2", "ann-acm-2", "ma-2", new int[] { 0, 0, 1, 1 },
            1.5d, "iaClass", null);
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(dto);
        assertEquals("____", map.get("annot_name_list").get(0));
    }

    // --- validateForcedResponse ------------------------------------------

    @Test void validateForcedResponseAcceptsMatchingId() throws IOException {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("ann-uuid-1")));
        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
    }

    @Test void validateForcedResponseRejectsNull() {
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("x", null));
    }

    @Test void validateForcedResponseRejectsMissingArray() {
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("x", new JSONObject()));
    }

    @Test void validateForcedResponseRejectsWrongLength() {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("a")).put(makeFancy("b")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("a", resp));
    }

    @Test void validateForcedResponseRejectsIdMismatch() {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("other-id")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
    }

    @Test void validateForcedResponseRejectsStatusSuccessFalse() {
        JSONObject resp = new JSONObject()
            .put("status", new JSONObject().put("success", false))
            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
    }

    @Test void validateForcedResponseTreatsStatusSuccessTrueAsOK() throws IOException {
        JSONObject resp = new JSONObject()
            .put("status", new JSONObject().put("success", true))
            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
    }

    // --- parseAnnotationIdsArray -----------------------------------------

    @Test void parseAnnotationIdsArrayReturnsEmptyOnNull() {
        assertTrue(WildbookIAM.parseAnnotationIdsArray(null).isEmpty());
    }

    @Test void parseAnnotationIdsArrayExtractsUuids() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(makeFancy("u2"));
        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("u1"));
        assertTrue(ids.contains("u2"));
    }

    @Test void parseAnnotationIdsArraySkipsNonObjectEntries() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put("not-an-object");
        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
        assertEquals(1, ids.size());
        assertEquals("u1", ids.get(0));
    }

    // --- parseAnnotationIdsArrayStrict -----------------------------------

    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
    }

    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(makeFancy("u2"));
        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("u1"));
        assertTrue(ids.contains("u2"));
    }

    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put("not-an-object");
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
        // Guards against accidental wrapper-label swap with parseImageIdsArrayStrict
        // after the C4 shared-helper extraction.
        assertTrue(ex.getMessage().startsWith("iaAnnotationIds entry 1"),
            "expected iaAnnotationIds label, got: " + ex.getMessage());
    }

    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(new JSONObject().put("not_uuid_key", "x"));
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
        assertTrue(ex.getMessage().startsWith("iaAnnotationIds entry 1"),
            "expected iaAnnotationIds label, got: " + ex.getMessage());
    }

    // --- helpers ---------------------------------------------------------

    /**
     * Builds a "fancy UUID" wrapper that {@link WildbookIAM#fromFancyUUID}
     * decodes back to the raw string. The wire format ({@code __UUID__})
     * is opaque to this test, so go through the public factory.
     */
    private static JSONObject makeFancy(String raw) {
        JSONObject jo = WildbookIAM.toFancyUUID(raw);
        assertNotNull(jo, "toFancyUUID returned null for " + raw);
        return jo;
    }
}
