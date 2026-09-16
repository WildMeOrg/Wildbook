package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of Phase 0 helpers introduced in C6:
 * {@link WildbookIAM#buildImageRequestMap} and
 * {@link WildbookIAM#validateImageResponse}. The network-bound
 * {@code registerImageIfMissing} entry point is exercised end-to-end
 * by the WBIA registration polling thread in a dev deployment.
 * (Empty-match-prospects design Track 1 C6.)
 */
class WildbookIAMImagePhase0Test {

    private static WbiaRegisterRequest sampleDtoWithImage() {
        return new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            12.34d, -56.78d, 1700000000000L);
    }

    // --- buildImageRequestMap --------------------------------------------

    @Test void buildImageRequestMapHasAllFiveLists() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        assertNotNull(map.get("image_uri_list"));
        assertNotNull(map.get("image_uuid_list"));
        assertNotNull(map.get("image_unixtime_list"));
        assertNotNull(map.get("image_gps_lat_list"));
        assertNotNull(map.get("image_gps_lon_list"));
        assertEquals(1, map.get("image_uri_list").size());
    }

    @Test void buildImageRequestMapPopulatesScalarFields() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        assertEquals("https://example.com/img.jpg",
            map.get("image_uri_list").get(0));
        assertEquals(12.34d, map.get("image_gps_lat_list").get(0));
        assertEquals(-56.78d, map.get("image_gps_lon_list").get(0));
    }

    @Test void buildImageRequestMapWrapsImageUuidInFancyForm() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        Object wrapped = map.get("image_uuid_list").get(0);
        assertTrue(wrapped instanceof JSONObject,
            "expected JSONObject fancy-uuid wrapper, got " +
            (wrapped == null ? "null" : wrapped.getClass().getName()));
        assertEquals("ma-acm-uuid-1",
            ((JSONObject) wrapped).optString("__UUID__"));
    }

    @Test void buildImageRequestMapConvertsMillisToUnixSeconds() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        // 1700000000000ms = 1700000000s
        assertEquals(1700000000, map.get("image_unixtime_list").get(0));
    }

    @Test void buildImageRequestMapPassesNullForNullDateTime() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            12.34d, -56.78d, null);
        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
        assertNull(map.get("image_unixtime_list").get(0));
    }

    @Test void buildImageRequestMapPassesNullsForOptionalGps() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            null, null, 1700000000000L);
        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
        assertNull(map.get("image_gps_lat_list").get(0));
        assertNull(map.get("image_gps_lon_list").get(0));
    }

    // --- validateImageResponse -------------------------------------------

    private static JSONObject okResponse(String returnedUuid) {
        JSONObject jo = new JSONObject();
        JSONObject status = new JSONObject();
        status.put("success", true);
        jo.put("status", status);
        JSONArray arr = new JSONArray();
        JSONObject fancy = new JSONObject();
        fancy.put("__UUID__", returnedUuid);
        arr.put(fancy);
        jo.put("response", arr);
        return jo;
    }

    @Test void validateImageResponse_acceptsMatchingFancyUuid()
    throws IOException {
        WildbookIAM.validateImageResponse("ma-acm-uuid-1",
            okResponse("ma-acm-uuid-1"));
    }

    @Test void validateImageResponse_throwsOnNullResponse() {
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", null));
        assertTrue(ex.getMessage().contains("null"),
            "message should mention null: " + ex.getMessage());
    }

    @Test void validateImageResponse_throwsOnStatusSuccessFalse() {
        JSONObject resp = okResponse("ma-acm-uuid-1");
        resp.getJSONObject("status").put("success", false);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("success=false"));
    }

    @Test void validateImageResponse_throwsOnMissingResponseArray() {
        JSONObject resp = new JSONObject();
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("no response array"));
    }

    @Test void validateImageResponse_throwsOnArrayLengthMismatch() {
        JSONObject resp = okResponse("ma-acm-uuid-1");
        resp.getJSONArray("response").put(new JSONObject().put("__UUID__", "x"));
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("length 1"));
    }

    @Test void validateImageResponse_throwsOnUuidMismatch() {
        JSONObject resp = okResponse("different-uuid");
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("mismatch"));
        assertTrue(ex.getMessage().contains("ma-acm-uuid-1"));
        assertTrue(ex.getMessage().contains("different-uuid"));
    }

    @Test void validateImageResponse_throwsOnUndecodableResponseElement() {
        JSONObject resp = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("not_uuid_key", "x"));  // no __UUID__
        resp.put("response", arr);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("could not be decoded"));
    }

    // --- registerImageIfMissing contract guard rails ---------------------
    //
    // These cover the DTO-contract short-circuit paths that fire BEFORE any
    // network call. The load-bearing sequencing tests (no annotation POST
    // after Phase 0 failure, both cache invalidations firing on success,
    // already-present skip behavior) are covered by the live integration
    // harness on the dev deployment — same precedent as the existing
    // iaAnnotationIdsStrict path, which relies on the polling thread's
    // integration coverage for its non-pure paths.

    @Test void registerImageIfMissing_returnsResponseBad_onNullDto() {
        WildbookIAM plugin = new WildbookIAM("context0");
        assertEquals(WildbookIAM.WbiaRegisterOutcome.RESPONSE_BAD,
            plugin.registerImageIfMissing(null));
    }

    @Test void registerImageIfMissing_returnsResponseBad_whenMediaAssetAcmIdBlank() {
        WildbookIAM plugin = new WildbookIAM("context0");
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "" /* blank mediaAssetAcmId */,
            new int[] { 0, 0, 1, 1 }, 0.0d, "iaClass", "____",
            "https://example.com/img.jpg", null, null, null);
        assertEquals(WildbookIAM.WbiaRegisterOutcome.RESPONSE_BAD,
            plugin.registerImageIfMissing(dto));
    }

    @Test void registerImageIfMissing_returnsResponseBad_whenImageUriBlank() {
        WildbookIAM plugin = new WildbookIAM("context0");
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 1, 1 }, 0.0d, "iaClass", "____",
            "" /* blank imageUri */, null, null, null);
        assertEquals(WildbookIAM.WbiaRegisterOutcome.RESPONSE_BAD,
            plugin.registerImageIfMissing(dto));
    }
}
