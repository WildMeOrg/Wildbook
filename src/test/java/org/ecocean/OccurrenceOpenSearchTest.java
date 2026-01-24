package testing_opensearch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

import java.io.StringWriter;
import java.io.Writer;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import org.ecocean.Occurrence;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.sql.SQLException;

public class OccurrenceOpenSearchTest {
    @Test void mappingTest() {
        Occurrence occ = new Occurrence();
        JSONObject map = occ.opensearchMapping();

        assertNotNull(map);
        assertTrue(map.has("id"));
        assertTrue(map.has("version"));
        assertEquals(18, map.keySet().size());
    }

    @Test void documentTest()
    throws IOException, JsonProcessingException {
        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
        Occurrence occ = new Occurrence();
        String testId = "TEST-ID";

        occ.setId(testId);
        occ.setDWCDateLastModified();
        Writer writer = new StringWriter();
        JsonGenerator jgen = new JsonFactory().createGenerator(writer);
        jgen.writeStartObject();

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                Shepherd myShepherd = new Shepherd("fake");
                occ.opensearchDocumentSerializer(jgen, myShepherd);
                jgen.writeEndObject();
                jgen.flush();
                // JSONObject just more familiar territory
                JSONObject doc = new JSONObject(writer.toString());
                assertEquals(17, doc.keySet().size());
                assertTrue(doc.has("encounters"));
                assertTrue(doc.getLong("version") > 0L);
                assertTrue(doc.getLong("indexTimestamp") > 0L);
                assertEquals(testId, doc.getString("id"));
            }
        }
    }
}
