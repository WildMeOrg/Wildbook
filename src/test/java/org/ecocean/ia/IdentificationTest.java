package org.ecocean;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.*;
import org.ecocean.IAJsonProperties;
import org.ecocean.identity.IBEISIA;
import org.ecocean.shepherd.core.Shepherd;

import java.util.ArrayList;
import java.util.List;
import javax.jdo.PersistenceManager;
import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

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

    @Test void isHotspotterQueryConfigTest() {
        // NOTE: this file imports org.junit.Assert (JUnit 4) — message is the FIRST arg.
        assertTrue("sv_on:true must be HotSpotter",
            IBEISIA.isHotspotterQueryConfig(new JSONObject("{\"sv_on\": true}")));
        assertFalse("sv_on:false must NOT be HotSpotter",
            IBEISIA.isHotspotterQueryConfig(new JSONObject("{\"sv_on\": false}")));
        assertFalse("absent sv_on must NOT be HotSpotter",
            IBEISIA.isHotspotterQueryConfig(new JSONObject("{\"pipeline_root\": \"MiewId\"}")));
        assertFalse("null must NOT be HotSpotter", IBEISIA.isHotspotterQueryConfig(null));
    }

    @Test void annotationHasHotspotterOptTest() {
        Annotation ann = new Annotation();
        ann.setIAClass("giraffe_whole");
        ann.setId("ann-hs");

        List<JSONObject> optsWithHs = new ArrayList<JSONObject>();
        optsWithHs.add(new JSONObject("{\"query_config_dict\": {\"pipeline_root\": \"MiewId\"}, \"default\": true}"));
        optsWithHs.add(new JSONObject("{\"query_config_dict\": {\"sv_on\": true}, \"description\": \"HotSpotter\"}"));

        List<JSONObject> optsNoHs = new ArrayList<JSONObject>();
        optsNoHs.add(new JSONObject("{\"query_config_dict\": {\"pipeline_root\": \"MiewId\"}, \"default\": true}"));

        Shepherd myShepherd = mock(Shepherd.class);

        IAJsonProperties mockHas = mock(IAJsonProperties.class);
        when(mockHas.identOpts(any(Shepherd.class), any(Annotation.class))).thenReturn(optsWithHs);
        IAJsonProperties mockNo = mock(IAJsonProperties.class);
        when(mockNo.identOpts(any(Shepherd.class), any(Annotation.class))).thenReturn(optsNoHs);
        IAJsonProperties mockNull = mock(IAJsonProperties.class);
        when(mockNull.identOpts(any(Shepherd.class), any(Annotation.class))).thenReturn(null);

        try (MockedStatic<IAJsonProperties> mockJP = mockStatic(IAJsonProperties.class)) {
            mockJP.when(() -> IAJsonProperties.iaConfig()).thenReturn(mockHas);
            assertTrue("class with a HotSpotter opt must be applicable",
                IA.annotationHasHotspotterOpt(myShepherd, ann));

            mockJP.when(() -> IAJsonProperties.iaConfig()).thenReturn(mockNo);
            assertFalse("class with only MiewID must NOT be applicable",
                IA.annotationHasHotspotterOpt(myShepherd, ann));

            mockJP.when(() -> IAJsonProperties.iaConfig()).thenReturn(mockNull);
            assertFalse("null identOpts (no config) must NOT be applicable and must not throw",
                IA.annotationHasHotspotterOpt(myShepherd, ann));
        }
    }

    private static List<JSONObject> giraffeOptsFixture() {
        List<JSONObject> opts = new ArrayList<JSONObject>();
        opts.add(new JSONObject("{\"query_config_dict\": {\"pipeline_root\": \"MiewId\"}, \"default\": true}"));
        opts.add(new JSONObject("{\"query_config_dict\": {\"sv_on\": true}, \"default\": false, \"description\": \"HotSpotter\"}"));
        return opts;
    }

    private static Task runIntake(String filterOrNull) {
        Annotation ann = new Annotation();
        ann.setIAClass("giraffe_whole");
        ann.setId("ann-1");
        List<Annotation> anns = new ArrayList<Annotation>();
        anns.add(ann);

        PersistenceManager mockPM = mock(PersistenceManager.class);
        when(mockPM.makePersistent(any(Object.class))).thenReturn(null);
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getPM()).thenReturn(mockPM);

        Encounter enc = new Encounter();
        enc.setTaxonomyFromString("Giraffa giraffa");

        IAJsonProperties mockIAConfig = mock(IAJsonProperties.class);
        when(mockIAConfig.identOpts(any(Shepherd.class), any(Annotation.class)))
            .thenAnswer(inv -> giraffeOptsFixture());

        Task parentTask = new Task();
        if (filterOrNull != null) {
            parentTask.setParameters(new JSONObject().put("matchingAlgorithmFilter", filterOrNull));
        }

        try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(CommonConfiguration.class)) {
            mockConfig.when(() -> CommonConfiguration.getServerURL(any(String.class))).thenReturn("/fake/url");
            try (MockedStatic<IAJsonProperties> mockJP = mockStatic(IAJsonProperties.class)) {
                mockJP.when(() -> IAJsonProperties.iaConfig()).thenReturn(mockIAConfig);
                try (MockedStatic<Encounter> mockEnc = mockStatic(Encounter.class,
                        org.mockito.Answers.CALLS_REAL_METHODS)) {
                    mockEnc.when(() -> Encounter.findByAnnotation(any(Annotation.class),
                        any(Shepherd.class))).thenReturn(enc);
                    return IA.intakeAnnotations(myShepherd, anns, parentTask, false);
                }
            }
        }
    }

    private static JSONObject taskIdentOpt(Task t) {
        // the chosen algorithm is recorded under "ibeis.identification" on the task's params
        JSONObject params = t.getParameters();
        return (params == null) ? null : params.optJSONObject("ibeis.identification");
    }

    @Test void hotspotterFilterSelectsHotspotterOpt() {
        Task t = runIntake("hotspotter");
        JSONObject chosen = taskIdentOpt(t);
        assertNotNull("hotspotter filter must schedule an identification opt", chosen);
        assertTrue("hotspotter filter must keep the HotSpotter opt even though it is default:false",
            IBEISIA.isHotspotterQueryConfig(chosen.optJSONObject("query_config_dict")));
    }

    @Test void noFilterReproducesTodayBehavior() {
        Task t = runIntake(null);
        JSONObject chosen = taskIdentOpt(t);
        assertNotNull("no filter must still schedule the default MiewID opt", chosen);
        assertFalse("no filter must drop the default:false HotSpotter opt, leaving MiewID",
            IBEISIA.isHotspotterQueryConfig(chosen.optJSONObject("query_config_dict")));
    }

    @Test void unknownFilterBehavesLikeNoFilter() {
        Task t = runIntake("bogusvalue");
        JSONObject chosen = taskIdentOpt(t);
        assertNotNull("unknown filter must behave exactly like no filter", chosen);
        assertFalse("unknown filter must not re-enable default:false algorithms",
            IBEISIA.isHotspotterQueryConfig(chosen.optJSONObject("query_config_dict")));
    }

    @Test void hasHotspotterIdentOptTest() {
        IAJsonProperties cfg = IAJsonProperties.iaConfig();
        org.junit.Assume.assumeTrue(cfg != null);   // needs IA.json on test classpath

        cfg.setJson(new JSONObject(
            "{\"Giraffa\":{\"giraffe_whole\":{\"_id_conf\":["
            + "{\"query_config_dict\":{\"pipeline_root\":\"MiewId\"},\"default\":true},"
            + "{\"query_config_dict\":{\"sv_on\":true},\"description\":\"HotSpotter\"}"
            + "]}}}"));
        assertTrue("config with a HotSpotter opt must report available", cfg.hasHotspotterIdentOpt());

        cfg.setJson(new JSONObject(
            "{\"Giraffa\":{\"giraffe_whole\":{\"_id_conf\":["
            + "{\"query_config_dict\":{\"pipeline_root\":\"MiewId\"},\"default\":true}"
            + "]}}}"));
        assertFalse("config without HotSpotter must report unavailable", cfg.hasHotspotterIdentOpt());

        cfg.setJson(new JSONObject("{}"));
        assertFalse("empty config must report unavailable", cfg.hasHotspotterIdentOpt());
    }
}
