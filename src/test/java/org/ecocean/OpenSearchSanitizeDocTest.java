package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class OpenSearchSanitizeDocTest {

    private User user(boolean admin) {
        User u = mock(User.class);
        when(u.getId()).thenReturn("u1");
        return u;
    }

    @Test void annotation_stripsAclFields_keepsContent() throws Exception {
        JSONObject doc = new JSONObject("{\"id\":\"a1\",\"viewpoint\":\"left\",\"embeddings\":[{}],"
            + "\"publiclyReadable\":true,\"submitterUserIds\":[\"x\"],\"viewUsers\":[\"y\"]}");
        Shepherd sh = mock(Shepherd.class);
        JSONObject out = OpenSearch.sanitizeDoc(doc, "annotation", sh, user(false), true);
        assertEquals("left", out.getString("viewpoint"), "content kept");
        assertTrue(out.has("embeddings"), "embeddings kept (not precious)");
        assertFalse(out.has("viewUsers"), "ACL fields scrubbed");
        assertFalse(out.has("submitterUserIds"), "ACL fields scrubbed");
        assertFalse(out.has("publiclyReadable"), "ACL fields scrubbed");
    }

    @Test void individualToken_allowlistOnly() throws Exception {
        JSONObject doc = new JSONObject("{\"id\":\"i1\",\"displayName\":\"Fluke\",\"sex\":\"female\","
            + "\"taxonomy\":\"x\",\"numberEncounters\":42,\"users\":[\"owner\"],"
            + "\"encounterIds\":[\"e1\"],\"locationGeoPoints\":[{}],\"viewUsers\":[\"y\"]}");
        Shepherd sh = mock(Shepherd.class);
        User u = user(false);
        when(u.isAdmin(sh)).thenReturn(false);
        JSONObject out = OpenSearch.sanitizeDoc(doc, "individual", sh, u, true);
        assertEquals("Fluke", out.getString("displayName"), "identity kept");
        assertEquals("female", out.getString("sex"), "identity kept");
        assertFalse(out.has("numberEncounters"), "aggregate dropped");
        assertFalse(out.has("users"), "aggregate dropped (would leak hidden submitters)");
        assertFalse(out.has("encounterIds"), "aggregate dropped");
        assertFalse(out.has("locationGeoPoints"), "aggregate dropped");
        assertFalse(out.has("viewUsers"), "ACL field scrubbed");
    }

    @Test void individualAdminToken_fullDocMinusAcl() throws Exception {
        JSONObject doc = new JSONObject("{\"id\":\"i1\",\"numberEncounters\":42,\"viewUsers\":[\"y\"]}");
        Shepherd sh = mock(Shepherd.class);
        User u = user(true);
        when(u.isAdmin(sh)).thenReturn(true);
        JSONObject out = OpenSearch.sanitizeDoc(doc, "individual", sh, u, true);
        assertEquals(42, out.getInt("numberEncounters"), "admin keeps aggregates");
        assertFalse(out.has("viewUsers"), "ACL still scrubbed even for admin");
    }

    // Allowlist completeness: a doc with EVERY field the individual serializer emits must, for a
    // non-admin token, retain ONLY the keep-list and drop everything else.
    @Test void individualToken_allowlistDropsAllSerializerAggregates() throws Exception {
        JSONObject doc = new JSONObject("{"
            + "\"id\":\"i1\",\"version\":1,\"indexTimestamp\":1,\"displayName\":\"F\",\"names\":[\"F\"],"
            + "\"nameMap\":{},\"sex\":\"female\",\"taxonomy\":\"t\",\"timeOfBirth\":\"x\",\"timeOfDeath\":\"y\","
            + "\"numberEncounters\":5,\"encounterIds\":[\"e\"],\"users\":[\"u\"],\"numberOccurrences\":2,"
            + "\"cooccurrenceIndividualIds\":[\"c\"],\"cooccurrenceIndividualMap\":{},\"locationGeoPoints\":[{}],"
            + "\"numberMediaAssets\":3,\"socialUnits\":[{}],\"relationships\":[{}],"
            + "\"publiclyReadable\":false,\"submitterUserIds\":[\"s\"],\"viewUsers\":[\"v\"]}");
        Shepherd sh = mock(Shepherd.class);
        User u = user(false);
        when(u.isAdmin(sh)).thenReturn(false);
        JSONObject out = OpenSearch.sanitizeDoc(doc, "individual", sh, u, true);
        java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(
            "id","version","indexTimestamp","displayName","names","nameMap","sex","taxonomy",
            "timeOfBirth","timeOfDeath"));
        for (String k : out.keySet()) assertTrue(keep.contains(k), "leaked non-allowlisted field: " + k);
        for (String dropped : new String[]{"numberEncounters","encounterIds","users","numberOccurrences",
            "cooccurrenceIndividualIds","cooccurrenceIndividualMap","locationGeoPoints","numberMediaAssets",
            "socialUnits","relationships","publiclyReadable","submitterUserIds","viewUsers"}) {
            assertFalse(out.has(dropped), "must drop " + dropped);
        }
    }
}
