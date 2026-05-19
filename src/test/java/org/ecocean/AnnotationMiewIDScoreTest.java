package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Annotation#openSearchScoreToCosine} helper
 * introduced in C17 to translate OpenSearch Lucene knn cosinesimil
 * scores (range [0, 1], formula {@code (1 + cos) / 2}) back to raw
 * cosine similarity in [-1, 1] — the native MiewID pipeline output.
 * (Empty-match-prospects design C17.)
 */
class AnnotationMiewIDScoreTest {

    private static final double EPS = 1e-9;

    @Test void identicalVectors_scoresExactlyOne() {
        // cos(a, a) = 1 → OS score = (1 + 1) / 2 = 1.0 → raw = 1.0
        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.0), EPS);
    }

    @Test void perpendicularVectors_scoresExactlyZero() {
        // cos(a, b) = 0 → OS score = 0.5 → raw = 0.0
        assertEquals(0.0, Annotation.openSearchScoreToCosine(0.5), EPS);
    }

    @Test void oppositeVectors_scoresExactlyNegativeOne() {
        // cos(a, -a) = -1 → OS score = 0 → raw = -1.0
        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
    }

    @Test void midRangeMatchesObservedDeploymentValues() {
        // The live test showed OS scores 1.0, 0.86, 0.78, 0.76 (after
        // the +2.0 offset is removed). Convert each to raw cosine and
        // verify the transform is correct.
        assertEquals(1.0,  Annotation.openSearchScoreToCosine(1.0),  EPS);
        assertEquals(0.72, Annotation.openSearchScoreToCosine(0.86), EPS);
        assertEquals(0.56, Annotation.openSearchScoreToCosine(0.78), EPS);
        assertEquals(0.52, Annotation.openSearchScoreToCosine(0.76), EPS);
    }

    @Test void clampsAboveOne_defensiveAgainstScoringDrift() {
        // If OS returns >1.0 for any reason (different engine, scoring
        // bug, etc.) the transform should clamp to 1.0 rather than
        // produce raw cosines outside the valid range.
        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.5), EPS);
        assertEquals(1.0, Annotation.openSearchScoreToCosine(100.0), EPS);
    }

    @Test void clampsBelowMinusOne_defensiveAgainstScoringDrift() {
        // OS shouldn't return negative scores from cosinesimil but
        // defense-in-depth: clamp to -1.0 rather than overflow.
        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-0.5), EPS);
        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-100.0), EPS);
    }

    @Test void zeroScoreStaysAtMinusOne_notNaN() {
        // Edge case: OS returns exactly 0.0 (perpendicular or
        // missing-score-default-to-0). Transform: 2*0 - 1 = -1.
        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
    }

    @Test void invertibleAcrossKnownPoints() {
        // For OS scores 0.0 through 1.0 in 0.1 steps, the raw cosine
        // should be monotonically increasing and span [-1, 1].
        double prev = -2.0;
        for (int i = 0; i <= 10; i++) {
            double osScore = i / 10.0;
            double cos = Annotation.openSearchScoreToCosine(osScore);
            assertTrue(cos > prev, "expected monotonic increase at i=" + i + " (" + cos + ")");
            assertTrue(cos >= -1.0 && cos <= 1.0,
                "expected cos in [-1, 1] at i=" + i + " (" + cos + ")");
            prev = cos;
        }
    }

    // --- getMatchQuery shape regression guard (Codex C17 Medium) --------
    // If a future refactor moves embeddings.method/methodVersion back
    // into the nested `must` list, the score-helper tests still pass
    // but the +2.0 offset returns. This test pins the JSON shape so
    // that drift is caught.

    @Test void getMatchQuery_putsKnnInMust_termsInFilter() {
        org.ecocean.Annotation ann = new org.ecocean.Annotation();
        // Embedding constructor auto-attaches to the annotation.
        org.json.JSONArray vec = new org.json.JSONArray();
        for (int i = 0; i < 8; i++) vec.put(0.1d * i);
        new org.ecocean.Embedding(ann, "miewid-msv4.1", "4.1", vec);

        org.json.JSONObject matchingSet = new org.json.JSONObject();
        matchingSet.put("query", new org.json.JSONObject().put("bool",
            new org.json.JSONObject().put("filter", new org.json.JSONArray())));

        org.json.JSONObject q = ann.getMatchQuery("miewid-msv4.1", "4.1", matchingSet);
        assertNotNull(q, "getMatchQuery returned null");

        // Top-level bool.must should contain a single nested clause.
        org.json.JSONArray topMust = q.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        assertEquals(1, topMust.length(), "expected 1 top-level must clause");
        org.json.JSONObject nested = topMust.getJSONObject(0)
            .getJSONObject("nested");

        // Inside the nested bool: must has ONLY knn, filter has the
        // method/methodVersion terms. This is the C17 contract — any
        // movement of the terms back into must would re-introduce the
        // +1.0-per-term-clause score offset that turned [0, 1] into
        // [2, 3] on the live deployment.
        org.json.JSONObject nestedBool = nested.getJSONObject("query")
            .getJSONObject("bool");

        org.json.JSONArray nestedMust = nestedBool.getJSONArray("must");
        assertEquals(1, nestedMust.length(),
            "nested.must should contain only the knn clause; found: " + nestedMust);
        assertTrue(nestedMust.getJSONObject(0).has("knn"),
            "nested.must[0] should be a knn clause: " + nestedMust.getJSONObject(0));

        org.json.JSONArray nestedFilter = nestedBool.getJSONArray("filter");
        assertEquals(2, nestedFilter.length(),
            "nested.filter should contain method + methodVersion terms; found: " + nestedFilter);
        // Both filter entries must be term clauses.
        for (int i = 0; i < nestedFilter.length(); i++) {
            assertTrue(nestedFilter.getJSONObject(i).has("term"),
                "nested.filter[" + i + "] should be a term clause: " +
                nestedFilter.getJSONObject(i));
        }
    }

    @Test void getMatchQuery_omitsNestedFilter_whenMethodAndVersionNull() {
        // Legacy api_endpoint-only configs: no method/methodVersion to
        // filter on. The nested.filter array should be absent (or empty)
        // rather than carrying empty term clauses.
        org.ecocean.Annotation ann = new org.ecocean.Annotation();
        org.json.JSONArray vec = new org.json.JSONArray();
        for (int i = 0; i < 8; i++) vec.put(0.1d * i);
        new org.ecocean.Embedding(ann, null, null, vec);

        org.json.JSONObject matchingSet = new org.json.JSONObject();
        matchingSet.put("query", new org.json.JSONObject().put("bool",
            new org.json.JSONObject().put("filter", new org.json.JSONArray())));

        org.json.JSONObject q = ann.getMatchQuery(null, null, matchingSet);
        assertNotNull(q);
        org.json.JSONObject nestedBool = q.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must")
            .getJSONObject(0).getJSONObject("nested")
            .getJSONObject("query").getJSONObject("bool");
        // nested.filter should either be absent or empty
        org.json.JSONArray nestedFilter = nestedBool.optJSONArray("filter");
        if (nestedFilter != null) {
            assertEquals(0, nestedFilter.length(),
                "nested.filter should be absent or empty when method+version both null");
        }
    }
}
