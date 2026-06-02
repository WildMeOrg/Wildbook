package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Shape-of-SQL tests for {@link MatchEligibilityQuery#buildSql}.
 * Full execution against a real DataNucleus PMF is exercised by
 * the live integration harness on dev deployments; the SQL
 * builder is the mechanical part that benefits from unit-testing.
 * (Empty-match-prospects design Track 2 C9.)
 */
class MatchEligibilityQueryTest {

    private static Set<Integer> ids(Integer... s) {
        return new LinkedHashSet<Integer>(Arrays.asList(s));
    }

    @Test void selectsDistinctAnnotationId() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.startsWith("SELECT DISTINCT a.\"ID\""),
            "SQL should start with SELECT DISTINCT a.\"ID\": " + sql);
    }

    @Test void joinsAnnotationFeaturesAndMediaAssetFeatures() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("JOIN \"ANNOTATION_FEATURES\" af"),
            "missing ANNOTATION_FEATURES join: " + sql);
        assertTrue(sql.contains("JOIN \"MEDIAASSET_FEATURES\" mf"),
            "missing MEDIAASSET_FEATURES join: " + sql);
        assertTrue(sql.contains("af.\"ID_OID\" = a.\"ID\""),
            "wrong ANNOTATION_FEATURES join condition: " + sql);
        assertTrue(sql.contains("mf.\"ID_EID\" = af.\"ID_EID\""),
            "wrong MEDIAASSET_FEATURES join condition: " + sql);
    }

    @Test void joinsEmbeddingWhenMethodOrVersionProvided() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
            "EMBEDDING join missing when method+version supplied: " + sql);
        assertTrue(sql.contains("e.\"ANNOTATION_ID\" = a.\"ID\""));
    }

    @Test void joinsEmbeddingWhenOnlyMethodProvided() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", null);
        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
            "EMBEDDING join missing when only method supplied: " + sql);
    }

    @Test void joinsEmbeddingWhenOnlyVersionProvided() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), null, "4.1");
        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
            "EMBEDDING join missing when only version supplied: " + sql);
    }

    @Test void omitsEmbeddingJoinWhenBothNull() {
        // Legacy api_endpoint-only config: gate just wants matchAgainst +
        // acmId reachable from the sibling MAs; no embedding-method filter.
        String sql = MatchEligibilityQuery.buildSql(ids(42), null, null);
        assertFalse(sql.contains("JOIN \"EMBEDDING\""),
            "EMBEDDING join should be omitted when both null: " + sql);
        assertFalse(sql.contains("e.\"METHOD\""));
        assertFalse(sql.contains("e.\"METHODVERSION\""));
    }

    @Test void emitsMatchAgainstAndAcmIdFilters() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("a.\"MATCHAGAINST\" = true"),
            "missing matchAgainst filter: " + sql);
        assertTrue(sql.contains("a.\"ACMID\" IS NOT NULL"),
            "missing acmId IS NOT NULL filter: " + sql);
    }

    @Test void emitsMethodFilterStrictWhenNonNull_includingBlank() {
        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
        // A blank string is a strict equality on "" (matches no docs),
        // preserving consistency with the matcher.
        String sql = MatchEligibilityQuery.buildSql(ids(42), "", "4.1");
        assertTrue(sql.contains("AND e.\"METHOD\" = '' "),
            "expected strict '' equality for blank method: " + sql);
    }

    @Test void emitsVersionFilterStrictWhenNonNull_includingBlank() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "");
        assertTrue(sql.contains("AND e.\"METHODVERSION\" = '' "),
            "expected strict '' equality for blank version: " + sql);
    }

    @Test void inListUsesNumericValuesWithoutQuotes() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(1, 42, 1000), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("IN (1,42,1000)"),
            "expected unquoted comma-separated IN list: " + sql);
    }

    @Test void skipsNullIdsInList() {
        // LinkedHashSet allows null. Builder must drop them silently.
        LinkedHashSet<Integer> ids = new LinkedHashSet<Integer>();
        ids.add(1);
        ids.add(null);
        ids.add(42);
        String sql = MatchEligibilityQuery.buildSql(ids, null, null);
        assertTrue(sql.contains("IN (1,42)"),
            "expected null to be skipped from IN list: " + sql);
    }

    @Test void escapesSingleQuotesInMethodAndVersion() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "ev'il", "v'1");
        // Single quote inside the value must be doubled per SQL escaping.
        assertTrue(sql.contains("e.\"METHOD\" = 'ev''il'"),
            "method not escaped: " + sql);
        assertTrue(sql.contains("e.\"METHODVERSION\" = 'v''1'"),
            "version not escaped: " + sql);
    }

    @Test void emptyMaSetReturnsEmpty_inFindEligibleAnnotationIds()
    throws java.io.IOException {
        // The execute path bails out before any DB call when the input
        // is empty. We can't easily test execute() without a Shepherd
        // mock, but the early-return path is covered.
        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
            null, Collections.<Integer>emptySet(), "miewid-msv4.1", "4.1");
        assertEquals(0, out.size());
    }

    @Test void nullMaSetReturnsEmpty_inFindEligibleAnnotationIds()
    throws java.io.IOException {
        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
            null, null, "miewid-msv4.1", "4.1");
        assertEquals(0, out.size());
    }
}
