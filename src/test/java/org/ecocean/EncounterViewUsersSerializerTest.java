package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * A full index of an encounter is a whole-document replace in OpenSearch. The document must
 * therefore carry viewUsers on EVERY full index, with no flag to opt in, or a plain reindex erases
 * the ACL the background permissions pass wrote (issue #1779). The pass and this serializer share
 * computeViewUsers so the two writers can never disagree.
 */
class EncounterViewUsersSerializerTest {

    private static String serializeRaw(Encounter enc, Shepherd sh) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        enc.opensearchDocumentSerializer(jg, sh);
        jg.writeEndObject();
        jg.close();
        return sw.toString();
    }

    private static Encounter privateEncounter(Shepherd sh, java.util.List<String> viewers) {
        Encounter enc = spy(new Encounter());
        enc.setCatalogNumber("enc-1");
        enc.setSubmitterID("owner");
        doReturn(false).when(enc).isPubliclyReadable();
        doReturn(viewers).when(enc).computeViewUsers(sh);
        return enc;
    }

    @Test void fullIndexEmitsViewUsersFromComputeViewUsers_withoutAnyFlag() throws Exception {
        Shepherd sh = mock(Shepherd.class);
        Encounter enc = privateEncounter(sh, Arrays.asList("viewer-1", "viewer-2"));

        JSONObject doc = new JSONObject(serializeRaw(enc, sh));
        assertEquals(Arrays.asList("viewer-1", "viewer-2"), doc.getJSONArray("viewUsers").toList(),
            "a plain full index must carry the same viewUsers the permissions pass computes");
    }

    @Test void publicEncounterEmitsEmptyViewUsers_notAbsent() throws Exception {
        Shepherd sh = mock(Shepherd.class);
        Encounter enc = spy(new Encounter());
        enc.setCatalogNumber("enc-pub");
        doReturn(true).when(enc).isPubliclyReadable();
        doReturn(Collections.emptyList()).when(enc).computeViewUsers(sh);

        JSONObject doc = new JSONObject(serializeRaw(enc, sh));
        assertTrue(doc.has("viewUsers"), "the field is always present so the pass can compare it");
        assertEquals(0, doc.getJSONArray("viewUsers").length());
    }

    @Test void aclTrioIsWrittenBeforeEveryOtherField() throws Exception {
        // the 1-arg serializer swallows exceptions and leaves a partial document behind; the
        // complete ACL (publiclyReadable, submitterUserId, viewUsers) must already be in it
        // before any other section can fail
        Shepherd sh = mock(Shepherd.class);
        User owner = mock(User.class);
        when(owner.getId()).thenReturn("owner-uuid");
        when(sh.getUser("owner")).thenReturn(owner);
        Encounter enc = privateEncounter(sh, Arrays.asList("viewer-1"));

        String raw = serializeRaw(enc, sh);
        int pub = raw.indexOf("\"publiclyReadable\"");
        int owner_ = raw.indexOf("\"submitterUserId\"");
        int acl = raw.indexOf("\"viewUsers\"");
        int first = raw.indexOf("\"locationId\""); // the first non-ACL field of the encounter
        assertTrue(pub >= 0 && owner_ >= 0 && acl >= 0, "all three ACL fields present");
        assertTrue(pub < first && owner_ < first && acl < first,
            "the ACL trio must precede every other encounter field");
        JSONObject doc = new JSONObject(raw);
        assertEquals("owner-uuid", doc.getString("submitterUserId"));
        assertEquals("owner", doc.getString("assignedUsername"), "the later owner block still runs");
    }

    @Test void aclIsComputedBeforeTheDocumentIsStamped() throws Exception {
        // the ACL is computed before super writes id/version/indexTimestamp: a failure inside
        // the computation then leaves a document without a version, which the reconciler
        // retries, instead of a versioned document with an incomplete ACL
        Shepherd sh = mock(Shepherd.class);
        Encounter enc = spy(new Encounter());
        enc.setCatalogNumber("enc-1");
        enc.setSubmitterID("owner");
        doReturn(false).when(enc).isPubliclyReadable();
        final long[] computedAt = new long[1];
        doAnswer(inv -> {
            Thread.sleep(5);
            computedAt[0] = System.currentTimeMillis();
            return Arrays.asList("viewer-1");
        }).when(enc).computeViewUsers(sh);

        JSONObject doc = new JSONObject(serializeRaw(enc, sh));
        assertTrue(doc.getLong("indexTimestamp") >= computedAt[0],
            "the stamp must be taken after computeViewUsers ran");
    }
}
