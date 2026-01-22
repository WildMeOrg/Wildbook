package org.ecocean;

// import com.pgvector.PGvector;
import org.ecocean.Annotation;
// import org.ecocean.Embedding;
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

        List<JSONObject> fakeOpts = new ArrayList<JSONObject>();
        fakeOpts.add(new JSONObject(
            "{\"query_config_dict\": {\"sv_on\": true}, \"default\": true}"));
        fakeOpts.add(new JSONObject("{\"api_endpoint\": \"fake-mlservice-endpoint\"}"));
        IAJsonProperties mockIAConfig = mock(IAJsonProperties.class);
        when(mockIAConfig.identOpts(any(Shepherd.class),
            any(Annotation.class))).thenReturn(fakeOpts);

        Task parentTask = new Task();
        try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(CommonConfiguration.class)) {
            mockConfig.when(() -> CommonConfiguration.getServerURL(any(String.class))).thenReturn(
                "/fake/url");
            try (MockedStatic<IAJsonProperties> mockJP = mockStatic(IAJsonProperties.class)) {
                mockJP.when(() -> IAJsonProperties.iaConfig()).thenReturn(mockIAConfig);
                Task resTask = IA.intakeAnnotations(myShepherd, anns, parentTask, false);
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
}
