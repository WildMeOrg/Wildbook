package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import java.util.Arrays;
import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class AnnotationAclSerializerTest {

    private JSONObject serialize(Annotation ann, Shepherd sh) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        ann.writeAclFields(jg, sh);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    @Test void singleParent_usesItsAcl() throws Exception {
        Annotation ann = spy(new Annotation());
        Shepherd sh = mock(Shepherd.class);
        Encounter parent = mock(Encounter.class);
        when(parent.opensearchAclFields(sh)).thenReturn(new JSONObject(
            "{\"publiclyReadable\":false,\"submitterUserId\":\"u1\",\"viewUsers\":[\"v1\"]}"));
        doReturn(Arrays.asList(parent)).when(ann).parentEncounters(sh);

        JSONObject out = serialize(ann, sh);
        assertFalse(out.getBoolean("publiclyReadable"));
        assertTrue(out.getJSONArray("submitterUserIds").toList().contains("u1"));
        assertTrue(out.getJSONArray("viewUsers").toList().contains("v1"));
    }

    @Test void zeroParents_failsClosed() throws Exception {
        Annotation ann = spy(new Annotation());
        Shepherd sh = mock(Shepherd.class);
        doReturn(java.util.Collections.emptyList()).when(ann).parentEncounters(sh);
        JSONObject out = serialize(ann, sh);
        assertFalse(out.getBoolean("publiclyReadable"), "no parent -> not public");
        assertEquals(0, out.getJSONArray("submitterUserIds").length(), "no parent -> admin-only");
        assertEquals(0, out.getJSONArray("viewUsers").length(), "no parent -> admin-only");
    }

    @Test void multipleParents_failsClosed() throws Exception {
        Annotation ann = spy(new Annotation());
        Shepherd sh = mock(Shepherd.class);
        Encounter a = mock(Encounter.class);
        Encounter b = mock(Encounter.class);
        doReturn(Arrays.asList(a, b)).when(ann).parentEncounters(sh);
        JSONObject out = serialize(ann, sh);
        assertFalse(out.getBoolean("publiclyReadable"), ">1 parent -> not public");
        assertEquals(0, out.getJSONArray("submitterUserIds").length(), ">1 parent -> admin-only");
        assertEquals(0, out.getJSONArray("viewUsers").length(), ">1 parent -> admin-only");
    }
}
