package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OpenSearchAclFilterIndexAwareTest {

    private JSONArray shoulds(JSONObject out) {
        return out.getJSONObject("query").getJSONObject("bool")
            .getJSONArray("filter").getJSONObject(0).getJSONObject("bool").getJSONArray("should");
    }

    @Test void encounterUsesSubmitterUserId() throws Exception {
        JSONObject out = OpenSearch.applyAclFilter(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), "u1", "encounter");
        assertEquals("u1", shoulds(out).getJSONObject(1).getJSONObject("term").getString("submitterUserId"));
    }

    @Test void individualUsesSubmitterUserIds() throws Exception {
        JSONObject out = OpenSearch.applyAclFilter(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), "u1", "individual");
        assertEquals("u1", shoulds(out).getJSONObject(1).getJSONObject("term").getString("submitterUserIds"));
    }

    @Test void annotationUsesSubmitterUserIds() throws Exception {
        JSONObject out = OpenSearch.applyAclFilter(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), "u1", "annotation");
        assertEquals("u1", shoulds(out).getJSONObject(1).getJSONObject("term").getString("submitterUserIds"));
    }

    @Test void rejectsNullUser() {
        assertThrows(java.io.IOException.class,
            () -> OpenSearch.applyAclFilter(new JSONObject("{\"query\":{\"match_all\":{}}}"), null, "encounter"));
    }
}
