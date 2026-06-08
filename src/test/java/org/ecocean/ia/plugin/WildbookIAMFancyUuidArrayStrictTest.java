package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage of the shared
 * {@link WildbookIAM#parseFancyUuidArrayStrict(JSONArray, String)} body.
 * The two named entry points
 * ({@link WildbookIAM#parseAnnotationIdsArrayStrict} and
 * {@link WildbookIAM#parseImageIdsArrayStrict}) have their own tests
 * for grep-friendly call coverage; this class verifies the shared
 * body's label propagation into error messages so both endpoints
 * report which WBIA response was malformed. (Empty-match-prospects
 * design Track 1 C4.)
 */
class WildbookIAMFancyUuidArrayStrictTest {

    private static JSONObject fancyUuid(String uuid) {
        JSONObject jo = new JSONObject();
        jo.put("__UUID__", uuid);
        return jo;
    }

    @Test void labelAppearsInIoMessage_forNonJsonObjectEntry() {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        jids.put("not-an-object");
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "customLabel"));
        assertTrue(ex.getMessage().startsWith("customLabel entry 1"),
            "label was not propagated: " + ex.getMessage());
    }

    @Test void labelAppearsInIoMessage_forUndecodableEntry() {
        JSONArray jids = new JSONArray();
        jids.put(new JSONObject());  // no __UUID__ — fromFancyUUID returns null
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "iaCustomIds"));
        assertTrue(ex.getMessage().startsWith("iaCustomIds entry 0"),
            "label was not propagated: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("could not be decoded"));
    }

    @Test void emptyInputReturnsEmptyList_forAnyLabel()
    throws IOException {
        List<String> out = WildbookIAM.parseFancyUuidArrayStrict(null, "anything");
        assertEquals(0, out.size());
        out = WildbookIAM.parseFancyUuidArrayStrict(new JSONArray(), "anything");
        assertEquals(0, out.size());
    }
}
