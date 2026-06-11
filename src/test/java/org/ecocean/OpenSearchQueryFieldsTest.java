package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OpenSearchQueryFieldsTest {
    private static final Set<String> ALLOW = new HashSet<>(Arrays.asList(
        "id","version","indexTimestamp","displayName","names","nameMap","sex","taxonomy",
        "timeOfBirth","timeOfDeath"));

    @Test void matchAll_isAllowed() {
        assertTrue(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), ALLOW));
    }
    @Test void termOnAllowedField_isAllowed() {
        assertTrue(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"term\":{\"sex\":\"female\"}}}"), ALLOW));
    }
    @Test void boolMatchOnNames_isAllowed() {
        assertTrue(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"names\":\"Fluke\"}}]}}}"), ALLOW));
    }
    @Test void rangeOnNumberEncounters_isRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"range\":{\"numberEncounters\":{\"gte\":100}}}}"), ALLOW));
    }
    @Test void termOnUsers_isRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"term\":{\"users\":\"bob\"}}}"), ALLOW));
    }
    @Test void existsOnEncounterIds_isRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"exists\":{\"field\":\"encounterIds\"}}}"), ALLOW));
    }
    @Test void sortOnLocationGeoPoints_isRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}},\"sort\":[{\"locationGeoPoints\":\"asc\"}]}"), ALLOW));
    }
    @Test void aggsAreRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}},\"aggs\":{\"x\":{\"terms\":{\"field\":\"numberEncounters\"}}}}"), ALLOW));
    }
    @Test void nestedBoolWithDisallowed_isRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"bool\":{\"should\":[{\"term\":{\"sex\":\"f\"}},{\"range\":{\"numberEncounters\":{\"gt\":1}}}]}}}"), ALLOW));
    }
    @Test void scriptIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"script\":{\"script\":\"doc['numberEncounters'].value\"}}}"), ALLOW));
    }
    @Test void queryStringIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"query_string\":{\"query\":\"numberEncounters:>100\"}}}"), ALLOW));
    }
    @Test void simpleQueryStringIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"simple_query_string\":{\"query\":\"users:bob\"}}}"), ALLOW));
    }
    @Test void multiMatchIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"multi_match\":{\"query\":\"x\",\"fields\":[\"users\"]}}}"), ALLOW));
    }
    @Test void postFilterIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}},\"post_filter\":{\"range\":{\"numberEncounters\":{\"gte\":1}}}}"), ALLOW));
    }
    @Test void collapseIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}},\"collapse\":{\"field\":\"users\"}}"), ALLOW));
    }
    @Test void rescoreIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}},\"rescore\":{}}"), ALLOW));
    }
    @Test void aggsTopLevelRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}},\"aggs\":{\"a\":{\"max\":{\"field\":\"numberEncounters\"}}}}"), ALLOW));
    }
    @Test void sortInBodyRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}},\"sort\":[{\"numberEncounters\":\"asc\"}]}"), ALLOW));
    }
    @Test void nestedIsRejected() {
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"nested\":{\"path\":\"socialUnits\",\"query\":{\"match_all\":{}}}}}"), ALLOW));
    }
    @Test void fromSizeAreAllowed() {
        assertTrue(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"term\":{\"sex\":\"f\"}},\"from\":0,\"size\":10}"), ALLOW));
    }
    @Test void matchAllAlone_stillAllowed() {
        assertTrue(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), ALLOW));
    }
    @Test void termsLookupForm_isRejected() {
        // terms lookup: field key "id" is allowlisted but the lookup object references hidden "path"
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(new JSONObject(
            "{\"query\":{\"terms\":{\"id\":{\"index\":\"individual\",\"id\":\"x\",\"path\":\"cooccurrenceIndividualIds\"}}}}"),
            ALLOW));
    }
    @Test void termsPlainArray_onAllowedField_isAllowed() {
        assertTrue(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"terms\":{\"sex\":[\"f\",\"m\"]}}}"), ALLOW));
    }
    @Test void termsLookup_evenOnAllowedFieldName_isRejected() {
        // any object-valued terms entry is the lookup form -> reject regardless of field name
        assertFalse(OpenSearch.queryReferencesOnlyAllowedFields(
            new JSONObject("{\"query\":{\"terms\":{\"names\":{\"index\":\"x\",\"path\":\"y\"}}}}"), ALLOW));
    }
}
