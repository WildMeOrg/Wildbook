package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.fasterxml.jackson.core.JsonGenerator;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Base.jsonForApiGet hands the OpenSearch document to API clients (individuals, annotations,
 * occurrences, media assets use it). The index-internal viewUsers array must never be part of
 * that document.
 */
class BaseApiDocumentTest {

    @Test void apiDocumentNeverCarriesViewUsers() throws Exception {
        Base base = mock(Base.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        Shepherd sh = mock(Shepherd.class);
        User viewer = mock(User.class);
        doReturn(true).when(base).canUserView(any(), any());
        doAnswer(inv -> {
            JsonGenerator jgen = inv.getArgument(0);
            jgen.writeStringField("id", "obj-1");
            jgen.writeArrayFieldStart("viewUsers");
            jgen.writeString("viewer-uuid");
            jgen.writeEndArray();
            jgen.writeStringField("displayName", "Ruby");
            return null;
        }).when(base).opensearchDocumentSerializer(any(JsonGenerator.class), any(Shepherd.class));

        JSONObject doc = base.jsonForApiGet(sh, viewer);
        assertTrue(doc.getBoolean("success"));
        assertEquals("obj-1", doc.getString("id"));
        assertEquals("Ruby", doc.getString("displayName"), "ordinary fields pass through");
        assertFalse(doc.has("viewUsers"), "the viewer list stays inside the index");
    }
}
