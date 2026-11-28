package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Spec §4.1 steps 2-3: the pure helpers of the ml-service match step. */
class MlServiceProcessorMatchIntakeTest {

    private static Annotation ann(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        return a;
    }

    @Test void matchIntakeParamsScrubsRunKeysAndKeepsTheRest() {
        JSONObject d = new JSONObject()
            .put("ibeis.detection", true).put(Task.PARAM_RUN_ID, "OLD").put(Task.PARAM_V2_MATCH, true)
            .put(Task.PARAM_OPTION, true).put("ibeis.identification", new JSONObject())
            .put("importTaskId", "IMP1").put("matchingSetFilter", new JSONObject().put("x", 1)).put("skipIdent", false);
        String before = d.toString();
        JSONObject p = MlServiceProcessor.matchIntakeParams(d);
        assertFalse(p.has("ibeis.detection"));
        assertFalse(p.has(Task.PARAM_RUN_ID));
        assertFalse(p.has(Task.PARAM_V2_MATCH));
        assertFalse(p.has(Task.PARAM_OPTION));
        assertFalse(p.has("ibeis.identification"));
        assertEquals("IMP1", p.getString("importTaskId"));
        assertEquals(1, p.getJSONObject("matchingSetFilter").getInt("x"));
        assertFalse(p.getBoolean("skipIdent"));
        assertEquals(before, d.toString(), "input not mutated");
        p.getJSONObject("matchingSetFilter").put("x", 99);
        assertEquals(1, d.getJSONObject("matchingSetFilter").getInt("x"), "nested copy independent");
        assertEquals(0, MlServiceProcessor.matchIntakeParams(null).length());
    }

    @Test void groupByEncounterKeepsFirstSeenOrderAndIsolatesOrphans() {
        Annotation a = ann("A"), b = ann("B"), c = ann("C"), d = ann("D"), e = ann("E");
        Map<String, String> enc = new HashMap<String, String>();
        enc.put("A", "enc-1");
        enc.put("B", "enc-2");
        enc.put("C", "enc-1");
        // D and E have no encounter
        List<List<Annotation> > groups = MlServiceProcessor.groupByEncounter(Arrays.asList(a, b, c, d, e), enc);
        assertEquals(3, groups.size());
        assertEquals(Arrays.asList(a, c), groups.get(0));
        assertEquals(Arrays.asList(b), groups.get(1));
        assertEquals(Arrays.asList(d, e), groups.get(2));
        assertSame(a, groups.get(0).get(0));
    }
    // bulk-import boxes PR A, task 7 (spec 4.4): the extraction markers never reach a run
    @Test void matchIntakeParamsStripsTheExtractionMarkers() {
        JSONObject p = new JSONObject().put("importTaskId", "i").put("skipIdent", false)
            .put("matchingSetFilter", new JSONObject().put("locationIds", "x"))
            .put(Task.PARAM_IMPORTED_BOX, true).put(Task.PARAM_EMBEDDING_EXTRACTION, true)
            .put("ibeis.detection", true).put(Task.PARAM_RUN_ID, "r");
        JSONObject out = MlServiceProcessor.matchIntakeParams(p);
        assertFalse(out.has(Task.PARAM_IMPORTED_BOX));
        assertFalse(out.has(Task.PARAM_EMBEDDING_EXTRACTION));
        assertFalse(out.has("ibeis.detection"));
        assertFalse(out.has(Task.PARAM_RUN_ID));
        assertEquals("i", out.getString("importTaskId"));
        assertFalse(out.getBoolean("skipIdent"));
        assertEquals("x", out.getJSONObject("matchingSetFilter").getString("locationIds"));
    }
}
