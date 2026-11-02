package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Shape-of-query tests for {@link OpenSearch#buildAnnotationMatchableQuery}.
 * Full poll-and-wait behavior of {@link OpenSearch#waitForAnnotationMatchableIds}
 * is exercised by integration in dev deployments — the matchable-predicate
 * JSON shape is the mechanical part that benefits from unit-testing.
 *
 * <p>(Empty-match-prospects design Track 2 C8.)</p>
 */
class OpenSearchAnnotationMatchableQueryTest {

    private static Set<String> ids(String... s) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        for (String x : s) set.add(x);
        return set;
    }

    private static JSONArray filterArr(JSONObject q) {
        return q.getJSONObject("query").getJSONObject("bool").getJSONArray("filter");
    }

    private static JSONObject findFilterClause(JSONArray filters, String kind) {
        for (int i = 0; i < filters.length(); i++) {
            JSONObject jo = filters.optJSONObject(i);
            if (jo != null && jo.has(kind)) return jo;
        }
        return null;
    }

    @Test void includesIdsTermMatchAgainstAcmIdExistsAndNested() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1", "ann-2"), "miewid-msv4.1", "4.1");
        JSONArray filters = filterArr(q);
        // Order doesn't matter for OpenSearch, but each named clause
        // must be present in the filter array.
        assertNotNull(findFilterClause(filters, "ids"),
            "missing ids clause: " + q);
        assertNotNull(findFilterClause(filters, "term"),
            "missing term clause (matchAgainst): " + q);
        assertNotNull(findFilterClause(filters, "exists"),
            "missing exists clause (acmId): " + q);
        assertNotNull(findFilterClause(filters, "nested"),
            "missing nested clause (embeddings): " + q);
    }

    @Test void idsClauseListsExactValues() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1", "ann-2", "ann-3"), "miewid-msv4.1", "4.1");
        JSONArray values = findFilterClause(filterArr(q), "ids")
            .getJSONObject("ids").getJSONArray("values");
        assertEquals(3, values.length());
        assertEquals("ann-1", values.getString(0));
        assertEquals("ann-3", values.getString(2));
    }

    @Test void termClauseAssertsMatchAgainstTrue() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        JSONObject term = findFilterClause(filterArr(q), "term")
            .getJSONObject("term");
        assertTrue(term.has("matchAgainst"),
            "term clause should target matchAgainst: " + term);
        assertEquals(true, term.getBoolean("matchAgainst"));
    }

    @Test void existsClauseTargetsAcmId() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        JSONObject exists = findFilterClause(filterArr(q), "exists")
            .getJSONObject("exists");
        assertEquals("acmId", exists.getString("field"));
    }

    // the nested must holds the method/version term clauses plus one bool clause
    // requiring an indexed vector; these split the two apart
    private static java.util.List<JSONObject> terms(JSONArray must) {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        for (int i = 0; i < must.length(); i++) {
            JSONObject term = must.getJSONObject(i).optJSONObject("term");
            if (term != null) out.add(term);
        }
        return out;
    }

    private static void assertRequiresIndexedVector(JSONArray must) {
        JSONObject vectorClause = null;
        for (int i = 0; i < must.length(); i++) {
            if (must.getJSONObject(i).has("bool")) vectorClause = must.getJSONObject(i);
        }
        assertNotNull(vectorClause, "nested must should require an indexed vector: " + must);
        JSONObject bool = vectorClause.getJSONObject("bool");
        assertEquals(1, bool.getInt("minimum_should_match"));
        java.util.Set<String> fields = new java.util.HashSet<>();
        JSONArray should = bool.getJSONArray("should");
        for (int i = 0; i < should.length(); i++) {
            fields.add(should.getJSONObject(i).getJSONObject("exists").getString("field"));
        }
        java.util.Set<String> expected = new java.util.HashSet<>();
        for (String f : Embedding.VECTOR_FIELDS.values()) expected.add("embeddings." + f);
        assertEquals(expected, fields);
    }

    @Test void nestedClauseTargetsEmbeddingsPath_withMethodAndVersion() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        assertEquals("embeddings", nested.getString("path"));
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        assertEquals(3, must.length());
        assertRequiresIndexedVector(must);
        // Method term
        JSONObject methodTerm = null;
        JSONObject versionTerm = null;
        for (JSONObject term : terms(must)) {
            if (term.has("embeddings.method")) methodTerm = term;
            if (term.has("embeddings.methodVersion")) versionTerm = term;
        }
        assertNotNull(methodTerm);
        assertNotNull(versionTerm);
        assertEquals("miewid-msv4.1", methodTerm.getString("embeddings.method"));
        assertEquals("4.1", versionTerm.getString("embeddings.methodVersion"));
    }

    @Test void nestedClauseOmitsVersion_whenMethodVersionNull() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", null);
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        // Only method term should be present.
        assertEquals(1, terms(must).size());
        assertRequiresIndexedVector(must);
        JSONObject term = terms(must).get(0);
        assertTrue(term.has("embeddings.method"));
        assertFalse(term.has("embeddings.methodVersion"));
    }

    @Test void nestedClauseOmitsMethod_whenMethodNull() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), null, "4.1");
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        assertEquals(1, terms(must).size());
        assertRequiresIndexedVector(must);
        JSONObject term = terms(must).get(0);
        assertTrue(term.has("embeddings.methodVersion"));
        assertFalse(term.has("embeddings.method"));
    }

    @Test void nestedClauseRequiresOnlyIndexedVector_whenBothNull() {
        // Legacy api_endpoint-only config: neither method nor version is
        // available, but the gate still wants to confirm SOME nested
        // embedding with an indexed vector exists. Both must be `null` (not
        // blank string) — the helper matches Annotation.getMatchQuery's
        // strict-when-non-null semantics.
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), null, null);
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        assertEquals(0, terms(must).size(), "no metadata terms when both null: " + must);
        assertRequiresIndexedVector(must);
    }

    @Test void nestedClauseDoesStrictMatchOnBlankString_notOmit() {
        // Codex C8 review Major: previous draft treated blank strings the
        // same as null, but Annotation.getMatchQuery only omits on null.
        // A non-null blank means a strict term match on "" (matches no
        // docs), preserving consistency with the matcher.
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "");
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        // Both terms present — version term is the blank-string strict.
        assertEquals(2, terms(must).size());
        JSONObject versionTerm = null;
        for (JSONObject term : terms(must)) {
            if (term.has("embeddings.methodVersion")) versionTerm = term;
        }
        assertNotNull(versionTerm);
        assertEquals("", versionTerm.getString("embeddings.methodVersion"));
    }

    @Test void noTopLevelSizeOrTrackTotalHits() {
        // queryCount() strips only `_source`; size and track_total_hits in
        // the body would be invalid for _count. Codex round-3 Medium.
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        assertFalse(q.has("size"),
            "size must not be present in _count body: " + q);
        assertFalse(q.has("track_total_hits"),
            "track_total_hits must not be present in _count body: " + q);
    }

    @Test void emptyIdSetProducesEmptyIdsValues() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids(), "miewid-msv4.1", "4.1");
        JSONArray values = findFilterClause(filterArr(q), "ids")
            .getJSONObject("ids").getJSONArray("values");
        assertEquals(0, values.length());
    }
}
