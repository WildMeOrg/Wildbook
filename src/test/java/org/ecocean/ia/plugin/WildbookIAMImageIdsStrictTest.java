package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of {@link WildbookIAM#parseImageIdsArrayStrict}.
 * Network-bound behavior of {@link WildbookIAM#iaImageIdsStrict} itself
 * is exercised end-to-end by Phase 0 of the WBIA registration polling
 * thread in a dev deployment; here we cover the pieces that can be tested
 * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
 */
class WildbookIAMImageIdsStrictTest {

    private static JSONObject fancyUuid(String uuid) {
        JSONObject jo = new JSONObject();
        jo.put("__UUID__", uuid);
        return jo;
    }

    @Test void parseImageIdsArrayStrict_returnsEmptyList_whenInputIsNull()
    throws IOException {
        List<String> out = WildbookIAM.parseImageIdsArrayStrict(null);
        assertEquals(0, out.size());
    }

    @Test void parseImageIdsArrayStrict_decodesValidFancyUuids()
    throws IOException {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        jids.put(fancyUuid("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb"));
        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
        assertEquals(2, out.size());
        assertEquals("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa", out.get(0));
        assertEquals("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb", out.get(1));
    }

    @Test void parseImageIdsArrayStrict_throwsIoException_onNonJsonObjectEntry() {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        jids.put("not-an-object");  // string entry, not JSONObject
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
        assertEquals(true, ex.getMessage().contains("iaImageIds entry 1"));
    }

    @Test void parseImageIdsArrayStrict_throwsIoException_onUndecodableEntry() {
        JSONArray jids = new JSONArray();
        // Empty JSONObject with no __UUID__ field — fromFancyUUID returns null.
        jids.put(new JSONObject());
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
        assertEquals(true, ex.getMessage().contains("could not be decoded"));
    }

    @Test void parseImageIdsArrayStrict_preservesOrder()
    throws IOException {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("00000000-0000-4000-8000-000000000003"));
        jids.put(fancyUuid("00000000-0000-4000-8000-000000000001"));
        jids.put(fancyUuid("00000000-0000-4000-8000-000000000002"));
        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
        assertEquals("00000000-0000-4000-8000-000000000003", out.get(0));
        assertEquals("00000000-0000-4000-8000-000000000001", out.get(1));
        assertEquals("00000000-0000-4000-8000-000000000002", out.get(2));
    }
}
