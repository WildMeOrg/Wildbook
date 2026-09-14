package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * v2 commit #7: shape-of-query tests for OpenSearch.buildIdEligibilityQuery.
 * The full poll-and-wait behavior of waitForVisibility requires a real
 * OpenSearch (or a heavily mocked RestClient); the eligibility-query JSON
 * shape is the one purely-mechanical part that benefits from a unit test.
 *
 * Uses the OpenSearch idiomatic `ids` query rather than the generic `terms`
 * query on `_id`, per OpenSearch docs.
 */
class OpenSearchVisibilityTest {

    @Test void buildIdEligibilityQuery_shapeForMultipleIds() {
        Set<String> ids = new LinkedHashSet<String>(
            Arrays.asList("ann-1", "ann-2", "ann-3"));
        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
        assertNotNull(q);
        JSONObject inner = q.optJSONObject("query");
        assertNotNull(inner);
        JSONObject idsClause = inner.optJSONObject("ids");
        assertNotNull(idsClause);
        JSONArray values = idsClause.optJSONArray("values");
        assertNotNull(values);
        assertEquals(3, values.length());
        assertEquals("ann-1", values.getString(0));
        assertEquals("ann-2", values.getString(1));
        assertEquals("ann-3", values.getString(2));
    }

    @Test void buildIdEligibilityQuery_emptySet() {
        Set<String> ids = new LinkedHashSet<String>();
        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
        JSONArray values = q.getJSONObject("query")
            .getJSONObject("ids")
            .getJSONArray("values");
        assertEquals(0, values.length());
    }

    @Test void buildIdEligibilityQuery_preservesInsertionOrder() {
        // LinkedHashSet preserves insertion order, which is useful for stable
        // OpenSearch query-cache keys.
        Set<String> ids = new LinkedHashSet<String>();
        ids.add("z");
        ids.add("a");
        ids.add("m");
        JSONArray values = OpenSearch.buildIdEligibilityQuery(ids)
            .getJSONObject("query")
            .getJSONObject("ids")
            .getJSONArray("values");
        assertEquals("z", values.getString(0));
        assertEquals("a", values.getString(1));
        assertEquals("m", values.getString(2));
    }

    @Test void buildIdEligibilityQuery_singletonShape() {
        // Sanity check: the JSON shape with one element matches the OpenSearch
        // docs example for the `ids` query.
        Set<String> ids = new LinkedHashSet<String>();
        ids.add("only-one");
        String expected = "{\"query\":{\"ids\":{\"values\":[\"only-one\"]}}}";
        JSONObject actual = OpenSearch.buildIdEligibilityQuery(ids);
        assertEquals(expected, actual.toString());
    }
}
