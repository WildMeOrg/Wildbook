package org.ecocean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.ecocean.Annotation;
import org.ecocean.ia.MatchResult;
import org.ecocean.ia.MatchResultProspect;
import org.ecocean.ia.Task;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MatchResultTest {
    @Test void testMatchResultClassic()
    throws IOException {
        Task task = mock(Task.class);
        MatchResult mr = new MatchResult(task);

        assertTrue(mr.getNumberCandidates() == 0);

        Annotation ann = mock(Annotation.class);
        ArrayList<Annotation> annList = new ArrayList<Annotation>();
        annList.add(ann);

        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getAnnotationsWithACMId(any(String.class),
            any(Boolean.class))).thenReturn(annList);

        // gotta build whole IA json structure here :(
        JSONObject res = new JSONObject();
        res.put("query_annot_uuid_list", new JSONArray("[{\"__UUID__\": \"query-annot-id\"}]"));
        res.put("database_annot_uuid_list",
            new JSONArray(
            "[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}, {\"__UUID__\": \"id2\"}]"));
        JSONObject cm_dict = new JSONObject();
        JSONObject lists = new JSONObject();
        lists.put("dannot_uuid_list",
            new JSONArray("[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}]"));
        lists.put("annot_score_list", new JSONArray("[0.1, 0.2]"));
        lists.put("score_list", new JSONArray("[0.3, 0.4]"));
        cm_dict.put("query-annot-id", lists);
        res.put("cm_dict", cm_dict);
        mr.createFromJsonResult(res, myShepherd);
        assertTrue(mr.getNumberCandidates() == 3);
        assertTrue(mr.numberProspects() == 4);
        assertTrue(mr.prospectScoreTypes().contains("indiv"));
        assertTrue(mr.prospectScoreTypes().contains("annot"));
        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
        // verify ordering is correct
        assertTrue(pj.getJSONArray("indiv").getJSONObject(0).getDouble("score") == 0.4);
        assertTrue(pj.getJSONArray("indiv").getJSONObject(1).getDouble("score") == 0.3);
        assertTrue(pj.getJSONArray("annot").getJSONObject(0).getDouble("score") == 0.2);
        assertTrue(pj.getJSONArray("annot").getJSONObject(1).getDouble("score") == 0.1);
        JSONObject full = mr.jsonForApiGet(-1, null, myShepherd);
        assertTrue(full.getInt("numberTotalProspects") == 4);
        assertTrue(full.getInt("numberCandidates") == 3);
    }

    // annotation-list style creation
    @Test void testMatchResultVector()
    throws IOException {
        Task task = mock(Task.class);

        when(task.countObjectAnnotations()).thenReturn(1);
        int numCand = 99;
        Annotation ann = mock(Annotation.class);
        ArrayList<Annotation> annList = new ArrayList<Annotation>();

        annList.add(ann);
        when(task.getObjectAnnotations()).thenReturn(annList);

        MatchResult mr = new MatchResult(task, annList, numCand, null);
        assertTrue(mr.getNumberCandidates() == numCand);
        assertTrue(mr.numberProspects() == 1);
        // FIXME someday we need to figure out indiv-vector-search
        // assertTrue(mr.prospectScoreTypes().contains("indiv"));
        assertTrue(mr.prospectScoreTypes().contains("annot"));
    }

    @Test void basicMatchResultProspect() {
        MatchResultProspect mrp = new MatchResultProspect(null, 1.0, "test", null);

        assertNotNull(mrp);
        assertTrue(mrp.getScore() == 1.0);
        assertEquals(mrp.getType(), "test");
        assertTrue(mrp.isType("test"));
        assertFalse(mrp.isType(null));
        // null annotation allows us to get away with null shepherd passed here
        // as annotationDetails() will simply return empty json for no annot
        JSONObject json = mrp.jsonForApiGet(null);
        assertTrue(json.getDouble("score") == 1.0);
    }

    // parseScore must handle the literal string "-Infinity" that WBIA emits
    // for score_list entries it scored negatively (Python's
    // json.dumps(float('-inf')) — not strict JSON but real in the wild).
    // Missing, null, or non-numeric strings collapse to NEGATIVE_INFINITY
    // so they sort to the bottom of the prospect list.
    @Test void parseScoreHandlesInfinityAndNullSentinels() {
        assertEquals(0.0, MatchResult.parseScore(Double.valueOf(0.0)), 0.0);
        assertEquals(0.95, MatchResult.parseScore(Double.valueOf(0.95)), 1e-12);
        assertEquals(Double.NEGATIVE_INFINITY, MatchResult.parseScore("-Infinity"), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, MatchResult.parseScore("Infinity"), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, MatchResult.parseScore(null), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, MatchResult.parseScore(JSONObject.NULL), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, MatchResult.parseScore("not-a-number"), 0.0);
        assertEquals(0.5, MatchResult.parseScore("0.5"), 1e-12);
        // NaN must coerce to NEGATIVE_INFINITY — Double.compare sorts NaN
        // above finite values which would push invalid scores to the top
        // of the DESC-sorted prospect list.
        assertEquals(Double.NEGATIVE_INFINITY, MatchResult.parseScore(Double.NaN), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, MatchResult.parseScore("NaN"), 0.0);
    }

    // Regression for the production bug: WBIA's dannot_uuid_list is NOT
    // ordered by score. If we iterate WBIA order and break at
    // MAXIMUM_PROSPECTS_STORED=500, strong matches sitting at high
    // indices get dropped. Build 600 candidates where the BEST score
    // sits at index 599, intermediate scores are scattered, and a
    // string "-Infinity" sits within the first 500. After the fix:
    // - the best score is retained (sorted to position 0)
    // - the worst 100 (including the early "-Infinity") are dropped
    // - the 500 highest finite scores are kept
    @Test void populateProspectsKeepsTopByScoreRegardlessOfOrder()
    throws IOException {
        Task task = mock(Task.class);
        MatchResult mr = new MatchResult(task);

        Shepherd myShepherd = mock(Shepherd.class);
        // 600 candidates. Score = index/600 (so scores 0..0.998 ascending
        // with index). Best score is at index 599. Plant a literal
        // "-Infinity" string score at index 250 (well within the old
        // first-500 cap window) so we can verify it's parsed and sorted
        // to the bottom.
        int n = 600;
        StringBuilder dannot = new StringBuilder("[");
        StringBuilder annotScore = new StringBuilder("[");
        StringBuilder scoreList = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                dannot.append(",");
                annotScore.append(",");
                scoreList.append(",");
            }
            String aid = "id" + i;
            dannot.append("{\"__UUID__\": \"").append(aid).append("\"}");
            double s = (double) i / (double) n;
            annotScore.append(s);
            scoreList.append(i == 250 ? "\"-Infinity\"" : Double.toString(s));
            Annotation aMock = mock(Annotation.class);
            ArrayList<Annotation> list = new ArrayList<Annotation>();
            list.add(aMock);
            when(myShepherd.getAnnotationsWithACMId(aid, true)).thenReturn(list);
        }
        dannot.append("]");
        annotScore.append("]");
        scoreList.append("]");
        // Stub the query-annot lookup too — createFromJsonResult resolves the
        // query annotation by acmId before it reads any candidates.
        ArrayList<Annotation> queryList = new ArrayList<Annotation>();
        queryList.add(mock(Annotation.class));
        when(myShepherd.getAnnotationsWithACMId("query-annot-id", true)).thenReturn(queryList);

        JSONObject res = new JSONObject();
        res.put("query_annot_uuid_list",
            new JSONArray("[{\"__UUID__\": \"query-annot-id\"}]"));
        res.put("database_annot_uuid_list", new JSONArray(dannot.toString()));
        JSONObject cm_dict = new JSONObject();
        JSONObject lists = new JSONObject();
        lists.put("dannot_uuid_list", new JSONArray(dannot.toString()));
        lists.put("annot_score_list", new JSONArray(annotScore.toString()));
        lists.put("score_list", new JSONArray(scoreList.toString()));
        cm_dict.put("query-annot-id", lists);
        res.put("cm_dict", cm_dict);
        mr.createFromJsonResult(res, myShepherd);

        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
        JSONArray annot = pj.getJSONArray("annot");
        JSONArray indiv = pj.getJSONArray("indiv");

        // 500-cap should hold on each tab.
        assertEquals(MatchResult.MAXIMUM_PROSPECTS_STORED, annot.length());
        assertEquals(MatchResult.MAXIMUM_PROSPECTS_STORED, indiv.length());
        // Top of annot tab is the best score (599/600 ≈ 0.9983).
        assertEquals((double) (n - 1) / (double) n,
            annot.getJSONObject(0).getDouble("score"), 1e-9);
        // Top of indiv tab is also the best numeric score. The
        // "-Infinity" at index 250 must be sorted to the bottom; with
        // 600 candidates and a 500-cap it should be DROPPED.
        assertEquals((double) (n - 1) / (double) n,
            indiv.getJSONObject(0).getDouble("score"), 1e-9);
        // Last kept prospect on the indiv tab has a finite score — not
        // -Infinity (which was dropped past the 500-cap).
        assertTrue(Double.isFinite(
            indiv.getJSONObject(MatchResult.MAXIMUM_PROSPECTS_STORED - 1).getDouble("score")));
        // Confirm numberProspects retains the "true total" of 1200
        // (600 annot + 600 indiv) even though stored is capped at 1000.
        // (Both tabs contribute via the two populateProspects calls.)
        assertEquals(2 * n, mr.numberProspects());
    }

    // Small-response variant: when WBIA returns fewer than the 500 cap,
    // non-finite scores ("-Infinity" / "NaN") MUST be skipped — otherwise
    // they survive into MatchResultProspect and org.json throws when the
    // API later serializes them. Codex C14 round-2 Major.
    @Test void populateProspectsSkipsNonFiniteScoresInSmallResults()
    throws IOException {
        Task task = mock(Task.class);
        MatchResult mr = new MatchResult(task);

        Annotation a0 = mock(Annotation.class);
        Annotation a1 = mock(Annotation.class);
        Annotation a2 = mock(Annotation.class);
        Shepherd myShepherd = mock(Shepherd.class);
        ArrayList<Annotation> list0 = new ArrayList<Annotation>();
        list0.add(a0);
        ArrayList<Annotation> list1 = new ArrayList<Annotation>();
        list1.add(a1);
        ArrayList<Annotation> list2 = new ArrayList<Annotation>();
        list2.add(a2);
        when(myShepherd.getAnnotationsWithACMId("id0", true)).thenReturn(list0);
        when(myShepherd.getAnnotationsWithACMId("id1", true)).thenReturn(list1);
        when(myShepherd.getAnnotationsWithACMId("id2", true)).thenReturn(list2);
        // Stub the query-annot lookup too — createFromJsonResult resolves the
        // query annotation by acmId before it reads any candidates.
        ArrayList<Annotation> queryList = new ArrayList<Annotation>();
        queryList.add(mock(Annotation.class));
        when(myShepherd.getAnnotationsWithACMId("query-annot-id", true)).thenReturn(queryList);

        JSONObject res = new JSONObject();
        res.put("query_annot_uuid_list",
            new JSONArray("[{\"__UUID__\": \"query-annot-id\"}]"));
        res.put("database_annot_uuid_list",
            new JSONArray(
            "[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}, {\"__UUID__\": \"id2\"}]"));
        JSONObject cm_dict = new JSONObject();
        JSONObject lists = new JSONObject();
        lists.put("dannot_uuid_list",
            new JSONArray(
            "[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}, {\"__UUID__\": \"id2\"}]"));
        // annot tab: one valid, one -Infinity, one NaN. Only the valid
        // one should be stored — the other two must be dropped, not
        // emitted with non-finite scores that org.json can't serialize.
        lists.put("annot_score_list",
            new JSONArray("[0.7, \"-Infinity\", \"NaN\"]"));
        // indiv tab: similar — one valid, two non-finite.
        lists.put("score_list",
            new JSONArray("[\"Infinity\", 0.5, \"-Infinity\"]"));
        cm_dict.put("query-annot-id", lists);
        res.put("cm_dict", cm_dict);
        mr.createFromJsonResult(res, myShepherd);

        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
        // Each tab should have exactly the one finite-score entry.
        assertEquals(1, pj.getJSONArray("annot").length());
        assertEquals(0.7, pj.getJSONArray("annot").getJSONObject(0).getDouble("score"), 1e-9);
        assertEquals(1, pj.getJSONArray("indiv").length());
        assertEquals(0.5, pj.getJSONArray("indiv").getJSONObject(0).getDouble("score"), 1e-9);
    }
}
