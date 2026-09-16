package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the C19 changes to
 * {@link MatchResult#_populateProspectsByIndividual} (exercised
 * indirectly through the vector constructor + {@code prospectsSorted("indiv")}).
 *
 * Three behaviors are pinned here:
 * <ol>
 * <li>Multi-annotation individuals get one prospect at the best-score
 *     score within their candidate group, not a count-based score.</li>
 * <li>Annotations whose encounter has no MarkedIndividual are emitted
 *     as singleton prospects in the indiv tab (legacy WBIA parity —
 *     un-ID'd encounters used the placeholder name {@code "____"}).</li>
 * <li>The indiv and annot tabs use the same scoring scale (OpenSearch
 *     Lucene knn cosinesimil score {@code (1+cos)/2} in [0, 1], which
 *     also happens to be WBIA-MiewID's stored score scale, giving
 *     cross-pipeline parity) so users can compare across tabs.</li>
 * </ol>
 */
class MatchResultIndivProspectsTest {

    private static final double EPS = 1e-9;

    // Per-test registry mirroring the DB: annotation id -> its parent Encounter. mockAnn populates
    // it; newShepherd() stubs Shepherd.getEncountersByAnnotationIds(...) to read it (the batch seam
    // that replaced the per-prospect Annotation.findEncounter).
    private final Map<String, Encounter> encByAnnId = new HashMap<String, Encounter>();
    private int annCounter = 0;

    @BeforeEach void reset() {
        encByAnnId.clear();
        annCounter = 0;
    }

    // A Shepherd mock whose getEncountersByAnnotationIds reflects the per-test registry, exactly as
    // the real batch loader would (returns only ids that have a parent encounter).
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

    private Annotation mockAnn(double score, MarkedIndividual indiv, Shepherd shepherd) {
        Annotation a = mock(Annotation.class);
        String id = "ann-" + (++annCounter);
        lenient().when(a.getId()).thenReturn(id);
        lenient().when(a.getOpensearchScore()).thenReturn(score);
        Encounter enc = mock(Encounter.class);
        lenient().when(enc.getIndividual()).thenReturn(indiv);
        encByAnnId.put(id, enc);
        return a;
    }

    private MarkedIndividual mockIndiv(String id) {
        MarkedIndividual indiv = mock(MarkedIndividual.class);
        lenient().when(indiv.getId()).thenReturn(id);
        return indiv;
    }

    private Annotation mockAnnNoEncounter(double score, Shepherd shepherd) {
        Annotation a = mock(Annotation.class);
        // A real id, but no registry entry -> the batch loader returns no encounter for it, so it is
        // skipped in the indiv tab (same as the old findEncounter == null).
        lenient().when(a.getId()).thenReturn("ann-" + (++annCounter));
        lenient().when(a.getOpensearchScore()).thenReturn(score);
        return a;
    }

    private MatchResult buildMatchResult(List<Annotation> candidates, Shepherd shepherd)
    throws Exception {
        Task task = mock(Task.class);
        when(task.countObjectAnnotations()).thenReturn(1);
        // Use a single distinct annotation as the query to avoid mixing
        // it into the candidate pool — the constructor calls
        // setQueryAnnotationFromTask() then populateProspects(annots).
        Annotation queryAnn = mock(Annotation.class);
        ArrayList<Annotation> queryList = new ArrayList<Annotation>();
        queryList.add(queryAnn);
        when(task.getObjectAnnotations()).thenReturn(queryList);
        return new MatchResult(task, candidates, candidates.size(), shepherd);
    }

    // ---- Behavior 1: best-score wins within a multi-annotation indiv -----

    @Test void multiAnnIndividual_scoredByBestCosine()
    throws Exception {
        Shepherd s = newShepherd();
        MarkedIndividual indivA = mockIndiv("indiv-A");

        // Three candidate annotations belong to the same individual.
        // Best cosine in the group is 0.81; the indiv prospect should
        // carry that score (not a count-weighted aggregate).
        Annotation a1 = mockAnn(0.45, indivA, s);
        Annotation a2 = mockAnn(0.81, indivA, s);
        Annotation a3 = mockAnn(0.62, indivA, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1); cands.add(a2); cands.add(a3);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertEquals(1, indivPros.size(), "expected exactly 1 indiv prospect for one ID'd indiv");
        assertEquals(0.81, indivPros.get(0).getScore(), EPS,
            "indiv prospect should use best-score score, not count");
    }

    // ---- Behavior 2: un-ID'd singletons appear in the indiv tab ----------

    @Test void unIdentifiedAnnotation_emittedAsSingletonInIndivTab()
    throws Exception {
        Shepherd s = newShepherd();
        // No individual on this encounter.
        Annotation a1 = mockAnn(0.7, null, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertEquals(1, indivPros.size(),
            "un-ID'd annotation should still appear in indiv tab as a singleton");
        assertEquals(0.7, indivPros.get(0).getScore(), EPS,
            "singleton score should equal annotation's own OS knn score");
    }

    @Test void mixedIdentifiedAndUnidentified_bothPresent()
    throws Exception {
        Shepherd s = newShepherd();
        MarkedIndividual indivA = mockIndiv("indiv-A");
        MarkedIndividual indivB = mockIndiv("indiv-B");

        // indivA: two annots, best 0.9
        Annotation a1 = mockAnn(0.4, indivA, s);
        Annotation a2 = mockAnn(0.9, indivA, s);
        // indivB: one annot, 0.55
        Annotation a3 = mockAnn(0.55, indivB, s);
        // un-ID'd: 0.72
        Annotation a4 = mockAnn(0.72, null, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1); cands.add(a2); cands.add(a3); cands.add(a4);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertEquals(3, indivPros.size(),
            "expected 3 indiv prospects: indivA (best 0.9), indivB (0.55), 1 singleton (0.72)");
        // Sorted descending by score.
        assertEquals(0.9, indivPros.get(0).getScore(), EPS);
        assertEquals(0.72, indivPros.get(1).getScore(), EPS);
        assertEquals(0.55, indivPros.get(2).getScore(), EPS);
    }

    @Test void multipleUnidentified_allEmittedAsSeparateSingletons()
    throws Exception {
        Shepherd s = newShepherd();
        Annotation a1 = mockAnn(0.3, null, s);
        Annotation a2 = mockAnn(0.8, null, s);
        Annotation a3 = mockAnn(0.55, null, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1); cands.add(a2); cands.add(a3);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertEquals(3, indivPros.size(),
            "each un-ID'd annotation should become its own singleton prospect");
        assertEquals(0.8, indivPros.get(0).getScore(), EPS);
        assertEquals(0.55, indivPros.get(1).getScore(), EPS);
        assertEquals(0.3, indivPros.get(2).getScore(), EPS);
    }

    // ---- Behavior 3: indiv & annot tabs use the same scoring scale -------

    @Test void indivAndAnnotTabs_useSameCosineScale()
    throws Exception {
        Shepherd s = newShepherd();
        MarkedIndividual indivA = mockIndiv("indiv-A");
        Annotation a1 = mockAnn(0.65, indivA, s);
        Annotation a2 = mockAnn(0.42, null, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1); cands.add(a2);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> annotPros = mr.prospectsSorted("annot", -1, null, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);

        // Both tabs should have the same annotations with the same cosine
        // scores — no count-based offset on the indiv side any more.
        assertEquals(2, annotPros.size());
        assertEquals(2, indivPros.size());
        assertEquals(0.65, annotPros.get(0).getScore(), EPS);
        assertEquals(0.65, indivPros.get(0).getScore(), EPS);
        assertEquals(0.42, annotPros.get(1).getScore(), EPS);
        assertEquals(0.42, indivPros.get(1).getScore(), EPS);
    }

    // ---- Edge cases -----------------------------------------------------

    @Test void annotationWithNoEncounter_skippedInIndivTab()
    throws Exception {
        Shepherd s = newShepherd();
        // a1: belongs to indivA — should emit a prospect.
        MarkedIndividual indivA = mockIndiv("indiv-A");
        Annotation a1 = mockAnn(0.5, indivA, s);
        // a2: no encounter at all — should be skipped (no individual axis).
        Annotation a2 = mockAnnNoEncounter(0.9, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1); cands.add(a2);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertEquals(1, indivPros.size(),
            "annotation with no encounter should not appear in indiv tab");
        assertEquals(0.5, indivPros.get(0).getScore(), EPS);
    }

    @Test void emptyCandidates_noIndivProspects()
    throws Exception {
        Shepherd s = newShepherd();
        Task task = mock(Task.class);
        when(task.countObjectAnnotations()).thenReturn(1);
        Annotation queryAnn = mock(Annotation.class);
        ArrayList<Annotation> queryList = new ArrayList<Annotation>();
        queryList.add(queryAnn);
        when(task.getObjectAnnotations()).thenReturn(queryList);

        // populateProspects returns early when annots is empty.
        MatchResult mr = new MatchResult(task, new ArrayList<Annotation>(), 0, s);
        assertNotNull(mr);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertTrue(indivPros.isEmpty(),
            "empty candidate list should produce no indiv prospects");
    }

    // ---- Regression: tally must group by individual ID, not object ------
    // MarkedIndividual.equals() compares by id, but the Base class
    // does not override hashCode(), so a HashMap keyed by the object
    // would put two distinct instances with the same id in different
    // buckets. The C19 implementation keys by indiv.getId() to avoid
    // this — without that fix, this test would see 2 prospects.
    // (Codex C19 review Medium.)

    @Test void multipleMarkedIndividualInstancesWithSameId_groupedAsOne()
    throws Exception {
        Shepherd s = newShepherd();
        // Two distinct MarkedIndividual objects, same id "shark-42".
        MarkedIndividual indivA1 = mock(MarkedIndividual.class);
        MarkedIndividual indivA2 = mock(MarkedIndividual.class);
        lenient().when(indivA1.getId()).thenReturn("shark-42");
        lenient().when(indivA2.getId()).thenReturn("shark-42");
        // Each instance is attached to a different annotation; in real
        // code this can happen across multiple Encounter loads via
        // separate JDO fetch groups.
        Annotation a1 = mockAnn(0.5, indivA1, s);
        Annotation a2 = mockAnn(0.8, indivA2, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1); cands.add(a2);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertEquals(1, indivPros.size(),
            "two equal-by-id MarkedIndividual instances should group into 1 indiv prospect");
        assertEquals(0.8, indivPros.get(0).getScore(), EPS,
            "grouped prospect should carry the highest score across both candidate annotations");
    }

    @Test void markedIndividualWithNullId_treatedAsSingleton()
    throws Exception {
        Shepherd s = newShepherd();
        MarkedIndividual indivWithNullId = mock(MarkedIndividual.class);
        lenient().when(indivWithNullId.getId()).thenReturn(null);
        Annotation a1 = mockAnn(0.6, indivWithNullId, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertEquals(1, indivPros.size(),
            "indiv with null id should emit a singleton prospect (defensive against bad data)");
        assertEquals(0.6, indivPros.get(0).getScore(), EPS);
    }

    @Test void allAnnotationsHaveNoEncounter_noProspectsEmitted()
    throws Exception {
        Shepherd s = newShepherd();
        Annotation a1 = mockAnnNoEncounter(0.5, s);
        Annotation a2 = mockAnnNoEncounter(0.7, s);

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(a1); cands.add(a2);

        MatchResult mr = buildMatchResult(cands, s);
        List<MatchResultProspect> indivPros = mr.prospectsSorted("indiv", -1, null, s);
        assertTrue(indivPros.isEmpty(),
            "if no candidate has an encounter, the indiv tab is empty");
    }
}
