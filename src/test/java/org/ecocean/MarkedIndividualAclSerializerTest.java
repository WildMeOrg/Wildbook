package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class MarkedIndividualAclSerializerTest {

    private JSONObject serialize(MarkedIndividual mi, Shepherd sh) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        mi.writeAclFields(jg, sh);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    @Test void unionOverMembers() throws Exception {
        MarkedIndividual mi = spy(new MarkedIndividual());
        Shepherd sh = mock(Shepherd.class);
        Encounter e1 = mock(Encounter.class);
        Encounter e2 = mock(Encounter.class);
        when(e1.opensearchAclFields(sh)).thenReturn(new JSONObject(
            "{\"publiclyReadable\":false,\"submitterUserId\":\"u1\",\"viewUsers\":[\"v1\"]}"));
        when(e2.opensearchAclFields(sh)).thenReturn(new JSONObject(
            "{\"publiclyReadable\":false,\"submitterUserId\":\"u2\",\"viewUsers\":[\"v2\"]}"));
        // getEncounters() returns Vector<Encounter>, not List — stub with a Vector
        doReturn(new java.util.Vector<Encounter>(java.util.Arrays.asList(e1, e2))).when(mi).getEncounters();

        JSONObject out = serialize(mi, sh);
        assertFalse(out.getBoolean("publiclyReadable"));
        assertTrue(out.getJSONArray("submitterUserIds").toList().containsAll(java.util.Arrays.asList("u1","u2")));
        assertTrue(out.getJSONArray("viewUsers").toList().containsAll(java.util.Arrays.asList("v1","v2")));
    }

    @Test void zeroEncounters_worldReadable() throws Exception {
        MarkedIndividual mi = spy(new MarkedIndividual());
        Shepherd sh = mock(Shepherd.class);
        doReturn(new java.util.Vector<Encounter>()).when(mi).getEncounters();
        JSONObject out = serialize(mi, sh);
        assertTrue(out.getBoolean("publiclyReadable"),
            "encounterless individual -> visible to anyone (matches canUserAccessMarkedIndividual)");
    }
}
