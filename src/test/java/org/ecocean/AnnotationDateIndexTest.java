package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import java.util.Collections;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * The annotation OpenSearch document denormalizes its parent encounter's sighting date as
 * encounterDateMillis, so temporal re-ID analyses (e.g. reliability by time-between-sightings)
 * do not need a second round-trip to the encounter index. Guards Annotation.opensearchMapping()
 * and opensearchDocumentSerializer().
 */
class AnnotationDateIndexTest {

    private JSONObject serialize(Annotation ann, Shepherd sh) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        ann.opensearchDocumentSerializer(jg, sh);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    private Annotation annotationWithEncounter(Shepherd sh, Encounter enc) {
        Annotation ann = spy(new Annotation());
        ann.setId("ann-1");
        doReturn(enc).when(ann).findEncounter(sh);
        doReturn(Collections.emptyList()).when(ann).parentEncounters(sh); // writeAclFields: fail-closed
        return ann;
    }

    @Test void mapping_declares_encounterDateMillis_as_date() {
        JSONObject map = new Annotation().opensearchMapping();
        assertTrue(map.has("encounterDateMillis"), "mapping must declare encounterDateMillis");
        assertEquals("date", map.getJSONObject("encounterDateMillis").getString("type"),
            "encounterDateMillis must be indexed as a date");
        assertEquals("epoch_millis", map.getJSONObject("encounterDateMillis").getString("format"),
            "encounterDateMillis must accept epoch-millis values");
    }

    @Test void serializer_writes_encounter_date_millis() throws Exception {
        long when = 1609459200000L; // 2021-01-01T00:00:00Z
        Shepherd sh = mock(Shepherd.class);
        Encounter enc = mock(Encounter.class);
        when(enc.getId()).thenReturn("enc-1");
        when(enc.getDateInMillisecondsFallback()).thenReturn(when);

        JSONObject out = serialize(annotationWithEncounter(sh, enc), sh);

        assertEquals(when, out.getLong("encounterDateMillis"),
            "serialized annotation must carry the parent encounter's dateMillis");
        assertEquals("enc-1", out.getString("encounterId"));
    }

    @Test void serializer_omits_date_when_encounter_has_none() throws Exception {
        Shepherd sh = mock(Shepherd.class);
        Encounter enc = mock(Encounter.class);
        when(enc.getId()).thenReturn("enc-2");
        when(enc.getDateInMillisecondsFallback()).thenReturn(null);

        JSONObject out = serialize(annotationWithEncounter(sh, enc), sh);

        assertFalse(out.has("encounterDateMillis"), "no encounter date -> field omitted");
        assertEquals("enc-2", out.getString("encounterId"));
    }
}
