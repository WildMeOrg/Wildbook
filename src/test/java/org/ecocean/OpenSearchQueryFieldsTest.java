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
}
