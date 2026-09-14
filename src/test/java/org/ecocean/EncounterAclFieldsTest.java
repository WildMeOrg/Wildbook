package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class EncounterAclFieldsTest {

    @Test void privateEncounter_emitsSubmitterAndViewUsers() {
        Encounter enc = spy(new Encounter());
        Shepherd sh = mock(Shepherd.class);
        User submitter = mock(User.class);
        when(submitter.getId()).thenReturn("owner-uuid");
        doReturn(false).when(enc).isPubliclyReadable();
        doReturn(submitter).when(enc).getSubmitterUser(sh);
        doReturn(Arrays.asList("collab-uuid")).when(enc).computeViewUsers(sh);

        JSONObject acl = enc.opensearchAclFields(sh);
        assertFalse(acl.getBoolean("publiclyReadable"), "private -> not public");
        assertEquals("owner-uuid", acl.getString("submitterUserId"), "submitter uuid");
        assertEquals(1, acl.getJSONArray("viewUsers").length(), "one viewUser");
        assertEquals("collab-uuid", acl.getJSONArray("viewUsers").getString(0));
    }

    @Test void publicEncounter_isWorldReadable() {
        Encounter enc = spy(new Encounter());
        Shepherd sh = mock(Shepherd.class);
        doReturn(true).when(enc).isPubliclyReadable();
        JSONObject acl = enc.opensearchAclFields(sh);
        assertTrue(acl.getBoolean("publiclyReadable"), "anonymous -> world readable");
        assertFalse(acl.has("submitterUserId"), "no submitter on public");
        assertEquals(0, acl.getJSONArray("viewUsers").length(), "no viewUsers on public");
    }

    @Test void invalidOwner_failsClosed() {
        Encounter enc = spy(new Encounter());
        Shepherd sh = mock(Shepherd.class);
        doReturn(false).when(enc).isPubliclyReadable();
        doReturn(null).when(enc).getSubmitterUser(sh);
        doReturn(java.util.Collections.emptyList()).when(enc).computeViewUsers(sh);
        JSONObject acl = enc.opensearchAclFields(sh);
        assertFalse(acl.getBoolean("publiclyReadable"), "not public");
        assertFalse(acl.has("submitterUserId"), "no resolvable submitter -> admin-only");
        assertEquals(0, acl.getJSONArray("viewUsers").length(), "no viewUsers -> admin-only");
    }
}
