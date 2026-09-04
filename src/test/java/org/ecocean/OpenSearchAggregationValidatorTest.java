package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Deny-by-default validator for token-search aggregations (OpenSearch.aggregationError). null == accept
 * (no agg, or a valid bounded terms agg on an allow-listed field); non-null == reject (400). Guards the
 * security boundary: only a direct terms aggregation, exact param/field allow-list, no wrappers /
 * sub-aggs / scripts / runtime_mappings / extra top-level keys.
 */
class OpenSearchAggregationValidatorTest {

    private JSONObject terms(String field, Integer size) {
        JSONObject t = new JSONObject().put("field", field);
        if (size != null) t.put("size", size.intValue());
        return t;
    }

    private JSONObject aggBody(String aggsKey, String name, JSONObject termsBody) {
        return new JSONObject().put(aggsKey,
            new JSONObject().put(name, new JSONObject().put("terms", termsBody)));
    }

    // ---- accept cases (null) ----

    @Test void noAggregation_isAccepted() {
        assertNull(OpenSearch.aggregationError(new JSONObject().put("query",
            new JSONObject().put("match_all", new JSONObject())), "encounter"));
        assertNull(OpenSearch.aggregationError(null, "encounter"));
    }

    @Test void validTermsOnAllowedField_isAccepted() {
        assertNull(OpenSearch.aggregationError(aggBody("aggs", "byLoc", terms("locationId", 50)),
            "encounter"));
        assertNull(OpenSearch.aggregationError(aggBody("aggregations", "byVp", terms("viewpoint", null)),
            "annotation"), "the 'aggregations' alias is accepted too");
        assertNull(OpenSearch.aggregationError(aggBody("aggs", "byTax", terms("taxonomy", 1000)),
            "individual"), "size at the max is accepted");
        // an agg alongside a query is fine (pagination is via URL params, not the body)
        JSONObject withQuery = aggBody("aggs", "byLoc", terms("encounterLocationId", 10))
            .put("query", new JSONObject().put("term", new JSONObject().put("iaClass", "zebra")));
        assertNull(OpenSearch.aggregationError(withQuery, "annotation"));
    }

    @Test void sizeAsLongWithinRange_isAccepted() {
        JSONObject t = new JSONObject().put("field", "locationId").put("size", 5L);
        assertNull(OpenSearch.aggregationError(
            new JSONObject().put("aggs", new JSONObject().put("a", new JSONObject().put("terms", t))),
            "encounter"));
    }

    // ---- reject cases (non-null) ----

    @Test void bothAggsAndAggregations_rejected() {
        JSONObject body = aggBody("aggs", "a", terms("locationId", 5))
            .put("aggregations", new JSONObject());
        assertNotNull(OpenSearch.aggregationError(body, "encounter"));
    }

    @Test void extraTopLevelKey_rejected() {
        for (String bad : new String[] { "runtime_mappings", "post_filter", "sort", "_source",
                "script_fields", "fields", "collapse", "pit", "rescore", "suggest", "whatever",
                "from", "size" }) { // body from/size rejected with aggs (pagination is via URL params)
            JSONObject body = aggBody("aggs", "a", terms("locationId", 5)).put(bad, new JSONObject());
            assertNotNull(OpenSearch.aggregationError(body, "encounter"),
                "top-level '" + bad + "' must be rejected alongside an aggregation");
        }
    }

    @Test void subAggregations_rejected() {
        JSONObject named = new JSONObject()
            .put("terms", terms("locationId", 5))
            .put("aggs", new JSONObject().put("sub", new JSONObject().put("terms", terms("taxonomy", 5))));
        JSONObject body = new JSONObject().put("aggs", new JSONObject().put("a", named));
        assertNotNull(OpenSearch.aggregationError(body, "encounter"), "sub-aggregations are rejected");
    }

    @Test void nonTermsAggTypes_rejected() {
        for (String type : new String[] { "global", "filter", "filters", "nested", "reverse_nested",
                "cardinality", "date_histogram", "composite", "multi_terms", "avg", "value_count",
                "significant_terms", "bucket_selector" }) {
            JSONObject named = new JSONObject().put(type, new JSONObject().put("field", "locationId"));
            JSONObject body = new JSONObject().put("aggs", new JSONObject().put("a", named));
            assertNotNull(OpenSearch.aggregationError(body, "encounter"),
                "non-terms aggregation '" + type + "' must be rejected");
        }
    }

    @Test void termsExtraParams_rejected() {
        for (String param : new String[] { "script", "missing", "include", "exclude", "order",
                "shard_size", "min_doc_count", "execution_hint", "value_type", "collect_mode" }) {
            JSONObject t = terms("locationId", 5).put(param, "anything");
            JSONObject body = new JSONObject().put("aggs", new JSONObject().put("a",
                new JSONObject().put("terms", t)));
            assertNotNull(OpenSearch.aggregationError(body, "encounter"),
                "terms parameter '" + param + "' must be rejected");
        }
    }

    @Test void fieldNotOnAllowList_rejected() {
        for (String field : new String[] { "viewUsers", "submitterUserIds", "gpsLatitude", "sex",
                "livingStatus", "behavior", "id", "individualId" }) {
            assertNotNull(OpenSearch.aggregationError(aggBody("aggs", "a", terms(field, 5)), "encounter"),
                "field '" + field + "' is not on the encounter agg allow-list");
        }
    }

    @Test void fieldAllowedOnWrongIndex_rejected() {
        // locationId is allowed for encounter, not for individual
        assertNotNull(OpenSearch.aggregationError(aggBody("aggs", "a", terms("locationId", 5)),
            "individual"));
        // viewpoint is annotation-only
        assertNotNull(OpenSearch.aggregationError(aggBody("aggs", "a", terms("viewpoint", 5)),
            "encounter"));
    }

    @Test void unsupportedIndex_rejected() {
        assertNotNull(OpenSearch.aggregationError(aggBody("aggs", "a", terms("locationId", 5)),
            "occurrence"), "aggregations unsupported for non-allow-listed index");
    }

    @Test void badSize_rejected() {
        assertNotNull(OpenSearch.aggregationError(
            aggBody("aggs", "a", terms("locationId", OpenSearch.AGG_MAX_BUCKETS + 1)), "encounter"),
            "size over max rejected (no clamp)");
        assertNotNull(OpenSearch.aggregationError(aggBody("aggs", "a", terms("locationId", 0)),
            "encounter"), "size 0 rejected");
        // non-integer size (double / string)
        JSONObject tDouble = new JSONObject().put("field", "locationId").put("size", 5.5);
        assertNotNull(OpenSearch.aggregationError(
            new JSONObject().put("aggs", new JSONObject().put("a",
                new JSONObject().put("terms", tDouble))), "encounter"), "non-integer size rejected");
        JSONObject tStr = new JSONObject().put("field", "locationId").put("size", "50");
        assertNotNull(OpenSearch.aggregationError(
            new JSONObject().put("aggs", new JSONObject().put("a",
                new JSONObject().put("terms", tStr))), "encounter"), "string size rejected");
    }

    @Test void nonStringField_rejected() {
        JSONObject t = new JSONObject().put("field", 5).put("size", 5);
        assertNotNull(OpenSearch.aggregationError(
            new JSONObject().put("aggs", new JSONObject().put("a", new JSONObject().put("terms", t))),
            "encounter"), "non-string field rejected");
        JSONObject tMissing = new JSONObject().put("size", 5); // no field
        assertNotNull(OpenSearch.aggregationError(
            new JSONObject().put("aggs", new JSONObject().put("a",
                new JSONObject().put("terms", tMissing))), "encounter"), "missing field rejected");
    }

    @Test void emptyAggObject_rejected() {
        assertNotNull(OpenSearch.aggregationError(new JSONObject().put("aggs", new JSONObject()),
            "encounter"), "empty aggs object rejected");
    }
}
