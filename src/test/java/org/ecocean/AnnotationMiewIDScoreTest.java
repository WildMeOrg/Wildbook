package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests pinning the {@code getMatchQuery} JSON shape that the C17
 * commit established: knn clause in the nested {@code must} array,
 * method/methodVersion term clauses in the nested {@code filter}
 * array. This eliminates the spurious +2.0 score offset that the
 * original (pre-C17) layout introduced when method/methodVersion
 * terms were in {@code must}.
 *
 * <p>The earlier "openSearchScoreToCosine" tests have been removed
 * along with the back-transform itself (parity-fix). OpenSearch
 * Lucene knn returns {@code (1+cos)/2}; the stored prospect score
 * is now that value directly, which happens to match WBIA-MiewID's
 * {@code distance_to_score = (2 - distance) / 2} formula and gives
 * cross-pipeline score-scale parity.</p>
 */
class AnnotationMiewIDScoreTest {

    private static final double EPS = 1e-9;

    // --- score-storage contract: OS score persisted unchanged ----------
    // Pins Annotation.osHitScore as identity-mapping so an accidental
    // re-introduction of the C17 back-transform (2*x - 1) trips a unit
    // failure. This is what gives vector prospects the same [0, 1]
    // score scale as WBIA-MiewID's distance_to_score formula. (Codex
    // Stage 2 Medium finding.)

    @Test void osHitScore_isIdentity_noBackTransform() {
        // Half-way OS score → 0.5. If the back-transform crept back,
        // this would return 0.0 (= 2*0.5 - 1) and fail.
        org.json.JSONObject hit = new org.json.JSONObject().put("_score", 0.5d);
        assertEquals(0.5d, Annotation.osHitScore(hit), EPS,
            "OS hit _score must be persisted unchanged for vector ↔ "
            + "WBIA-MiewID parity; back-transforming to 2*x-1 reintroduces "
            + "the C17 regression.");
    }

    @Test void osHitScore_passesThroughKnownDeploymentValues() {
        // Sample values observed on the amphibian-reptile deployment
        // post-must→filter. All should round-trip exactly.
        for (double s : new double[] { 0.0, 0.5, 0.736172, 0.7365, 0.86, 1.0 }) {
            org.json.JSONObject hit = new org.json.JSONObject().put("_score", s);
            assertEquals(s, Annotation.osHitScore(hit), EPS,
                "score=" + s + " must pass through unchanged");
        }
    }

    @Test void osHitScore_missingScore_defaultsToZero() {
        // Defensive: malformed/empty hit shouldn't NPE. opt-with-default
        // returns 0.0 (treated as perpendicular vectors).
        org.json.JSONObject hit = new org.json.JSONObject();
        assertEquals(0.0d, Annotation.osHitScore(hit), EPS,
            "missing _score should default to 0.0, not crash");
    }

    // --- getMatchQuery shape regression guard (Codex C17 Medium) --------
    // If a future refactor moves embeddings.method/methodVersion back
    // into the nested `must` list, callers will start seeing scores
    // offset by +2.0 again. This test pins the JSON shape so that
    // drift is caught.

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
