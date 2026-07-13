package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the match-result serialization path batch-loads prospect encounters (one loader
 * call for the whole result) instead of one Annotation.findEncounter per prospect, WITHOUT changing
 * the serialized JSON.
 */
class MatchResultSerializationBatchTest {

    // Per-test registry: annotation id -> parent Encounter. mockAnn populates it; newShepherd()
    // stubs getEncountersByAnnotationIds from it (the batch seam).
    private final Map<String, Encounter> encByAnnId = new HashMap<String, Encounter>();
    private final List<Annotation> allAnns = new ArrayList<Annotation>();
    private int annCounter = 0;

    @BeforeEach void reset() {
        encByAnnId.clear();
        allAnns.clear();
        annCounter = 0;
    }

    private Shepherd newShepherd() {
        Shepherd s = mock(Shepherd.class);
        lenient().when(s.getEncountersByAnnotationIds(any())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            Map<String, Encounter> out = new HashMap<String, Encounter>();
            if (ids != null) {
                for (String id : ids) {
                    if (encByAnnId.containsKey(id)) out.put(id, encByAnnId.get(id));
                }
            }
            return out;
        });
        return s;
    }

    // A shepherd whose batch loader always returns an empty map -> every prospect takes the
    // findEncounter fallback. Used to prove the batch and fallback JSON are identical.
    private Shepherd emptyMapShepherd() {
        Shepherd s = mock(Shepherd.class);
        lenient().when(s.getEncountersByAnnotationIds(any())).thenReturn(
            Collections.<String, Encounter>emptyMap());
        return s;
    }

    private MarkedIndividual mockIndiv(String id) {
        MarkedIndividual indiv = mock(MarkedIndividual.class);
        lenient().when(indiv.getId()).thenReturn(id);
        lenient().when(indiv.getTaxonomyString()).thenReturn("Genus species");
        lenient().when(indiv.getDisplayName()).thenReturn("disp-" + id);
        lenient().when(indiv.getNickName()).thenReturn("nick-" + id);
        lenient().when(indiv.getSex()).thenReturn("unknown");
        lenient().when(indiv.getNumEncounters()).thenReturn(3);
        return indiv;
    }

    // mock Annotation with a unique id, a registered parent Encounter (in encByAnnId), and
    // findEncounter stubbed to the SAME encounter so the fallback path yields identical JSON.
    // registerInMap=false leaves it out of the batch map (forces the fallback in the batch run).
    private Annotation mockAnn(double score, MarkedIndividual indiv, boolean registerInMap) {
        Annotation a = mock(Annotation.class);
        String id = "ann-" + (++annCounter);
        lenient().when(a.getId()).thenReturn(id);
        lenient().when(a.getOpensearchScore()).thenReturn(score);
        Encounter enc = mock(Encounter.class);
        lenient().when(enc.getId()).thenReturn("enc-" + id);
        lenient().when(enc.getTaxonomyString()).thenReturn("Genus species");
        lenient().when(enc.getLocationID()).thenReturn("loc-1");
        lenient().when(enc.getIndividual()).thenReturn(indiv);
        lenient().when(a.findEncounter(any())).thenReturn(enc);
        if (registerInMap) encByAnnId.put(id, enc);
        allAnns.add(a);
        return a;
    }

    private MatchResult buildMatchResult(List<Annotation> candidates, Shepherd shepherd)
    throws Exception {
        Task task = mock(Task.class);
        when(task.countObjectAnnotations()).thenReturn(1);
        Annotation queryAnn = mock(Annotation.class);
        ArrayList<Annotation> queryList = new ArrayList<Annotation>();
        queryList.add(queryAnn);
        when(task.getObjectAnnotations()).thenReturn(queryList);
        return new MatchResult(task, candidates, candidates.size(), shepherd);
    }

    @Test void serializedJsonIdenticalBatchVsFallback()
    throws Exception {
        Shepherd s = newShepherd();
        MarkedIndividual indivA = mockIndiv("indiv-A");
        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(mockAnn(0.81, indivA, true));
        cands.add(mockAnn(0.62, indivA, true));
        cands.add(mockAnn(0.55, null, true)); // un-ID'd

        MatchResult mr = buildMatchResult(cands, s);

        // Batch run (map-backed) vs fallback run (empty map -> findEncounter). Same MatchResult.
        JSONObject batchJson = mr.prospectsForApiGet(-1, null, s);
        JSONObject fallbackJson = mr.prospectsForApiGet(-1, null, emptyMapShepherd());

        assertEquals(fallbackJson.toString(), batchJson.toString(),
            "serialized prospects JSON must be identical whether encounters came from the batch " +
            "map or the per-annotation findEncounter fallback");
    }

    @Test void noFindEncounterFanoutWhenFullyMapped()
    throws Exception {
        Shepherd s = newShepherd();
        MarkedIndividual indivA = mockIndiv("indiv-A");
        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(mockAnn(0.81, indivA, true));
        cands.add(mockAnn(0.62, indivA, true));
        cands.add(mockAnn(0.9, null, true));

        MatchResult mr = buildMatchResult(cands, s);
        // The constructor already called getEncountersByAnnotationIds once (Task A indiv grouping);
        // clear so the assertions below count only the serialization pass.
        clearInvocations(s);
        for (Annotation a : allAnns) clearInvocations(a);

        mr.prospectsForApiGet(-1, null, s);

        // One batch loader call for the whole serialization (= ceil(uniqueIds/1000) physical queries;
        // here 1), and ZERO per-prospect findEncounter.
        verify(s, times(1)).getEncountersByAnnotationIds(any());
        for (Annotation a : allAnns) verify(a, never()).findEncounter(any());
    }

    @Test void missingIdFallsBackToFindEncounter()
    throws Exception {
        Shepherd s = newShepherd();
        MarkedIndividual indivA = mockIndiv("indiv-A");
        Annotation mapped = mockAnn(0.8, indivA, true);
        Annotation missing = mockAnn(0.6, indivA, false); // NOT in the batch map
        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(mapped); cands.add(missing);

        MatchResult mr = buildMatchResult(cands, s);
        clearInvocations(s);
        for (Annotation a : allAnns) clearInvocations(a);

        JSONObject json = mr.prospectsForApiGet(-1, null, s);

        // Mapped annotation: served from the map (no findEncounter). Missing: exactly one fallback.
        verify(mapped, never()).findEncounter(any());
        verify(missing, times(1)).findEncounter(any());
        assertTrue(json.toString().contains("enc-" + missing.getId()),
            "missing-id prospect's encounter must still serialize via the fallback");
    }

    @Test void emptyProspects_noBatchCall_noNpe()
    throws Exception {
        Shepherd s = newShepherd();
        MatchResult mr = buildMatchResult(new ArrayList<Annotation>(), s);
        clearInvocations(s);

        JSONObject json = mr.prospectsForApiGet(-1, null, s);
        assertFalse(json == null, "empty prospects must still return a JSON object, not null");
        verify(s, never()).getEncountersByAnnotationIds(any());
    }
}
