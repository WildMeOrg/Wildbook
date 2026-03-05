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
}
