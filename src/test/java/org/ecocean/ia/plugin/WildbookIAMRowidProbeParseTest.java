package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the pure helpers behind
 * {@link WildbookIAM#iaMissingImageIds(java.util.List, String)}:
 * {@link WildbookIAM#chunkList(List, int)} and
 * {@link WildbookIAM#parseRowidProbeResponse(List, JSONArray)}.
 * The WBIA endpoint /api/image/rowid/uuid/ returns a rowid per requested
 * image UUID, with JSON null where the UUID is unknown; null entries mark
 * the acmId as missing from WBIA. (AcmIdBot reconciliation sweep spec §3.)
 */
class WildbookIAMRowidProbeParseTest {

    // ---------- chunkList ----------

    @Test void chunkListSplitsAtExactBoundary() {
        List<String> in = new ArrayList<String>();
        for (int i = 0; i < 100; i++) in.add("u" + i);
        List<List<String>> out = WildbookIAM.chunkList(in, 50);
        assertEquals(2, out.size());
        assertEquals(50, out.get(0).size());
        assertEquals(50, out.get(1).size());
        assertEquals("u0", out.get(0).get(0));
        assertEquals("u99", out.get(1).get(49));
    }

    @Test void chunkListHandlesRemainderAndSmallInput() {
        List<List<String>> out = WildbookIAM.chunkList(Arrays.asList("a", "b", "c"), 2);
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).size());
        assertEquals(1, out.get(1).size());
        out = WildbookIAM.chunkList(Arrays.asList("solo"), 50);
        assertEquals(1, out.size());
        assertEquals("solo", out.get(0).get(0));
    }

    @Test void chunkListEmptyOrNullOrBadSizeReturnsEmpty() {
        assertEquals(0, WildbookIAM.chunkList(new ArrayList<String>(), 50).size());
        assertEquals(0, WildbookIAM.chunkList(null, 50).size());
        assertEquals(0, WildbookIAM.chunkList(Arrays.asList("a"), 0).size());
    }

    // ---------- parseRowidProbeResponse ----------

    @Test void nullRowidEntriesAreMissing() throws IOException {
        List<String> chunk = Arrays.asList("aaa", "bbb", "ccc");
        JSONArray resp = new JSONArray();
        resp.put(101);
        resp.put(org.json.JSONObject.NULL);
        resp.put(303);
        List<String> missing = WildbookIAM.parseRowidProbeResponse(chunk, resp);
        assertEquals(1, missing.size());
        assertEquals("bbb", missing.get(0));
    }

    @Test void allKnownReturnsEmpty() throws IOException {
        List<String> chunk = Arrays.asList("aaa", "bbb");
        JSONArray resp = new JSONArray();
        resp.put(1);
        resp.put(2);
        assertEquals(0, WildbookIAM.parseRowidProbeResponse(chunk, resp).size());
    }

    @Test void lengthMismatchThrows() {
        List<String> chunk = Arrays.asList("aaa", "bbb");
        JSONArray resp = new JSONArray();
        resp.put(1);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseRowidProbeResponse(chunk, resp));
        assertTrue(ex.getMessage().contains("length"),
            "message should mention length: " + ex.getMessage());
    }

    @Test void nullResponseThrows() {
        List<String> chunk = Arrays.asList("aaa");
        assertThrows(IOException.class,
            () -> WildbookIAM.parseRowidProbeResponse(chunk, null));
    }
}
