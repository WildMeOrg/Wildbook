package org.ecocean;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.*;
import org.ecocean.IAJsonProperties;
import org.ecocean.shepherd.core.Shepherd;

import java.util.ArrayList;
import java.util.List;
import javax.jdo.PersistenceManager;
import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentificationTest {
    @Test void basicAddToQueue() {
        Annotation ann = new Annotation();

        ann.setIAClass("fake-iaClass");
        ann.setId("fake-ann-id");
        List<Annotation> anns = new ArrayList<Annotation>();
        anns.add(ann);
        PersistenceManager mockPM = mock(PersistenceManager.class);
        when(mockPM.makePersistent(any(Object.class))).thenReturn(null);
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getPM()).thenReturn(mockPM);

        Encounter enc = new Encounter();
        enc.setTaxonomyFromString("Genus specific");

        List<JSONObject> fakeOpts = new ArrayList<JSONObject>();
        fakeOpts.add(new JSONObject(
            "{\"query_config_dict\": {\"sv_on\": true}, \"default\": true}"));
        fakeOpts.add(new JSONObject(
            "{\"api_endpoint\": \"fake-mlservice-endpoint\", \"model_id\": \"method0-version0\" }"));
        IAJsonProperties mockIAConfig = mock(IAJsonProperties.class);
        when(mockIAConfig.identOpts(any(Shepherd.class),
            any(Annotation.class))).thenReturn(fakeOpts);

        Task parentTask = new Task();
        try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(CommonConfiguration.class)) {
            mockConfig.when(() -> CommonConfiguration.getServerURL(any(String.class))).thenReturn(
                "/fake/url");
            try (MockedStatic<IAJsonProperties> mockJP = mockStatic(IAJsonProperties.class)) {
                mockJP.when(() -> IAJsonProperties.iaConfig()).thenReturn(mockIAConfig);
                try (MockedStatic<Encounter> mockEnc = mockStatic(Encounter.class,
                        org.mockito.Answers.CALLS_REAL_METHODS)) {
                    mockEnc.when(() -> Encounter.findByAnnotation(any(Annotation.class),
                        any(Shepherd.class))).thenReturn(enc);
                    Task resTask = IA.intakeAnnotations(myShepherd, anns, parentTask, false);
                }
            }
        }
    }

    // An iaClass configured with `"_id_conf": []` has opted out of identification.
    // A user-selected algorithm (taskParameters.matchingAlgorithms) must not
    // manufacture an option for it -- otherwise part classes like
    // `wild_dog+tail_*` get a doomed identification task whenever someone picks
    // an algorithm on a sibling annotation of the same image.
    private static Shepherd runIntakeWithOpts(List<JSONObject> identOpts,
        JSONArray matchingAlgorithms) {
        Annotation ann = new Annotation();

        ann.setIAClass("wild_dog+tail_general");
        ann.setId("tail-ann-1");
        List<Annotation> anns = new ArrayList<Annotation>();
        anns.add(ann);

        PersistenceManager mockPM = mock(PersistenceManager.class);
        when(mockPM.makePersistent(any(Object.class))).thenReturn(null);
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getPM()).thenReturn(mockPM);

        Encounter enc = new Encounter();
        enc.setTaxonomyFromString("Lycaon pictus");

        IAJsonProperties mockIAConfig = mock(IAJsonProperties.class);
        when(mockIAConfig.identOpts(any(Shepherd.class),
            any(Annotation.class))).thenReturn(identOpts);

        Task parentTask = new Task();
        if (matchingAlgorithms != null) {
            JSONObject params = new JSONObject();
            params.put("matchingAlgorithms", matchingAlgorithms);
            parentTask.setParameters(params);
        }
        try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(CommonConfiguration.class)) {
            mockConfig.when(() -> CommonConfiguration.getServerURL(any(String.class))).thenReturn(
                "/fake/url");
            try (MockedStatic<IAJsonProperties> mockJP = mockStatic(IAJsonProperties.class)) {
                mockJP.when(() -> IAJsonProperties.iaConfig()).thenReturn(mockIAConfig);
                try (MockedStatic<Encounter> mockEnc = mockStatic(Encounter.class,
                        org.mockito.Answers.CALLS_REAL_METHODS)) {
                    mockEnc.when(() -> Encounter.findByAnnotation(any(Annotation.class),
                        any(Shepherd.class))).thenReturn(enc);
                    IA.intakeAnnotations(myShepherd, anns, parentTask, false);
                }
            }
        }
        return myShepherd;
    }

    private static JSONArray hotspotterAlgorithmChoice() {
        JSONArray algos = new JSONArray();

        algos.put(new JSONObject(
            "{\"query_config_dict\": {\"sv_on\": false}, \"description\": \"HotSpotter pattern-matcher\", \"default\": false}"));
        return algos;
    }

    @Test void emptyIdentConfigIsNotOverriddenByMatchingAlgorithms() {
        Shepherd myShepherd = runIntakeWithOpts(new ArrayList<JSONObject>(),
            hotspotterAlgorithmChoice());

        verify(myShepherd, never()).storeNewTask(any(Task.class));
    }

    @Test void nullIdentConfigIsNotOverriddenByMatchingAlgorithms() {
        Shepherd myShepherd = runIntakeWithOpts(null, hotspotterAlgorithmChoice());

        verify(myShepherd, never()).storeNewTask(any(Task.class));
    }

    @Test void configuredIaClassStillHonorsMatchingAlgorithms() {
        List<JSONObject> configured = new ArrayList<JSONObject>();

        // deliberately has no "description", so the assertion below can only pass if the
        // user-selected option really did replace it
        configured.add(new JSONObject(
            "{\"pipeline_root\": \"vector\", \"method\": \"miewid\", \"default\": true}"));
        Shepherd myShepherd = runIntakeWithOpts(configured, hotspotterAlgorithmChoice());

        ArgumentCaptor<Task> stored = ArgumentCaptor.forClass(Task.class);
        verify(myShepherd).storeNewTask(stored.capture());
        JSONObject chosen = stored.getValue().getParameters().optJSONObject(
            "ibeis.identification");
        assertNotNull("the stored task must record the option it will run", chosen);
        assertEquals("the user-selected option must override the configured default",
            "HotSpotter pattern-matcher", chosen.optString("description", null));
    }

    // Characterization test for the IA.json contract the guard above relies on. This locks in
    // existing IAJsonProperties behavior (it passes without the guard change): an explicit
    // `"_id_conf": []` is an opt-out and must NOT inherit `_default`, while a class that is not
    // declared at all must inherit it. If that ever flipped, the guard would start skipping
    // classes that should be matched.
    @Test void emptyIdentConfigOptsOutWhileUndeclaredClassInheritsDefault()
    throws Exception {
        IAJsonProperties conf = new IAJsonProperties();

        conf.setJson(new JSONObject("{\"Lycaon\": {\"pictus\": {"
            + "\"_default\": {\"_id_conf\": [{\"description\": \"MiewID\", \"default\": true}]},"
            + "\"wild_dog\": {\"_id_conf\": [{\"description\": \"MiewID\", \"default\": true}]},"
            + "\"wild_dog+tail_general\": {\"_id_conf\": [], \"_save_keyword\": \"TailGeneral\"}"
            + "}}}"));
        Taxonomy taxy = new Taxonomy("Lycaon pictus");

        assertEquals("explicit [] must stay empty, not fall back to _default",
            0, conf.identOpts(taxy, "wild_dog+tail_general").size());
        assertEquals("a declared class keeps its own options",
            1, conf.identOpts(taxy, "wild_dog").size());
        assertEquals("an undeclared class must inherit _default and stay matchable",
            1, conf.identOpts(taxy, "wild_dog+scar").size());
    }

    @Test void miscMethodTest() {
        String[] mv = MLService.getMethodValues(null);

        assertTrue(mv.length == 2);
        assertNull(mv[0]);
        assertNull(mv[1]);

        JSONObject conf = new JSONObject();
        mv = MLService.getMethodValues(conf);
        assertTrue(mv.length == 2);
        assertNull(mv[0]);
        assertNull(mv[1]);

        conf = new JSONObject("{\"model_id\": \"abc-123\"}");
        mv = MLService.getMethodValues(conf);
        assertTrue(mv.length == 2);
        assertEquals(mv[0], "abc");
        assertEquals(mv[1], "123");
    }
}
