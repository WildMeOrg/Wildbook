package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OpenSearchAclFilterTest {

    @Test void wrapsInnerQueryWithAclFilter() throws Exception {
        JSONObject original = new JSONObject("{\"query\":{\"match\":{\"name\":\"x\"}}}");
        JSONObject out = OpenSearch.applyEncounterAclFilter(original, "user-uuid-1");

        JSONObject bool = out.getJSONObject("query").getJSONObject("bool");
        // original inner query preserved as a must clause
        JSONArray must = bool.getJSONArray("must");
        assertEquals(1, must.length(), "one must clause (the original query)");
        assertTrue(must.getJSONObject(0).has("match"), "original match preserved");

        // ACL is a filter clause: should[publiclyReadable, submitterUserId, viewUsers], msm=1
        JSONArray filter = bool.getJSONArray("filter");
        JSONObject aclBool = filter.getJSONObject(0).getJSONObject("bool");
        assertEquals(1, aclBool.getInt("minimum_should_match"), "minimum_should_match=1");
        JSONArray should = aclBool.getJSONArray("should");
        assertEquals(3, should.length(), "three should clauses");
        assertTrue(should.getJSONObject(0).getJSONObject("term").getBoolean("publiclyReadable"),
            "publiclyReadable term");
        assertEquals("user-uuid-1",
            should.getJSONObject(1).getJSONObject("term").getString("submitterUserId"),
            "submitterUserId term = uuid");
        assertEquals("user-uuid-1",
            should.getJSONObject(2).getJSONObject("term").getString("viewUsers"),
            "viewUsers term = uuid");
    }

    @Test void filterOnlyWhenNoInnerQuery() throws Exception {
        JSONObject original = new JSONObject("{}"); // no "query" field
        JSONObject out = OpenSearch.applyEncounterAclFilter(original, "user-uuid-1");
        JSONObject bool = out.getJSONObject("query").getJSONObject("bool");
        assertFalse(bool.has("must"), "no must clause when there was no inner query");
        assertTrue(bool.has("filter"), "ACL filter still applied (fail-closed scope)");
    }

    @Test void rejectsNullUser() {
        JSONObject original = new JSONObject("{\"query\":{\"match_all\":{}}}");
        assertThrows(java.io.IOException.class,
            () -> OpenSearch.applyEncounterAclFilter(original, null),
            "null/empty userId must throw (fail closed)");
    }
}
