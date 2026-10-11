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
        JSONObject p = MlServiceProcessor.matchIntakeParams(d);
        assertFalse(p.has("ibeis.detection"));
        assertFalse(p.has(Task.PARAM_RUN_ID));
        assertFalse(p.has(Task.PARAM_V2_MATCH));
        assertFalse(p.has(Task.PARAM_OPTION));
        assertFalse(p.has("ibeis.identification"));
        assertEquals("IMP1", p.getString("importTaskId"));
        assertEquals(1, p.getJSONObject("matchingSetFilter").getInt("x"));
        assertFalse(p.getBoolean("skipIdent"));
        assertTrue(d.has("ibeis.detection"), "input not mutated");
        assertEquals(0, MlServiceProcessor.matchIntakeParams(null).length());
    }

    @Test void groupByEncounterKeepsFirstSeenOrderAndIsolatesOrphans() {
        Annotation a = ann("A"), b = ann("B"), c = ann("C"), d = ann("D");
        Map<String, String> enc = new HashMap<String, String>();
        enc.put("A", "enc-1");
        enc.put("B", "enc-2");
        enc.put("C", "enc-1");
        // D has no encounter
        List<List<Annotation> > groups = MlServiceProcessor.groupByEncounter(Arrays.asList(a, b, c, d), enc);
        assertEquals(3, groups.size());
        assertEquals(Arrays.asList(a, c), groups.get(0));
        assertEquals(Arrays.asList(b), groups.get(1));
        assertEquals(Arrays.asList(d), groups.get(2));
        assertSame(a, groups.get(0).get(0));
    }
}
