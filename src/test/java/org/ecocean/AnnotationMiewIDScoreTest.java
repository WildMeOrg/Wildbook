package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests pinning the {@code getMatchQuery} JSON shape: a TOP-LEVEL knn
 * on the denormalized {@code vector} field, with method/methodVersion
 * as top-level {@code embeddingMethod}/{@code embeddingMethodVersion}
 * term clauses passed inside the knn {@code filter} (Lucene efficient
 * filtering). The match was moved off the nested {@code embeddings.vector}
 * field because a nested knn_vector cannot do filtered ANN (any filter
 * forces an exact brute-force scan).
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

    // --- getMatchQuery shape regression guard ---------------------------
    // The matcher queries the TOP-LEVEL "vector" knn_vector field (NOT
    // nested embeddings.vector, which can't do filtered ANN -- any filter
    // forces an exact brute-force scan that trips the client timeout).
    // method/methodVersion are top-level embeddingMethod/embeddingMethodVersion
    // term filters passed INSIDE the knn `filter` (Lucene efficient
    // filtering keeps the search on the HNSW graph). The knn _score is the
    // cosinesimil (1+cos)/2 value, consumed unchanged by osHitScore().
    // This test pins that shape so a regression back to nested is caught.

    @Test void getMatchQuery_topLevelKnn_methodVersionInEfficientFilter() {
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

        // Top-level knn on the denormalized "vector" field (no nested wrapper).
        org.json.JSONObject knnVector = q.getJSONObject("query")
            .getJSONObject("knn").getJSONObject("vector");
        assertTrue(knnVector.has("vector"), "knn should carry the query vector array");
        assertTrue(knnVector.has("k"), "knn should carry k");

        // method/version are term filters inside the knn efficient-filter bool.
        org.json.JSONArray filter = knnVector.getJSONObject("filter")
            .getJSONObject("bool").getJSONArray("filter");
        org.json.JSONObject methodTerm = null;
        org.json.JSONObject versionTerm = null;
        for (int i = 0; i < filter.length(); i++) {
            org.json.JSONObject term = filter.getJSONObject(i).optJSONObject("term");
            if (term == null) continue;
            if (term.has("embeddingMethod")) methodTerm = term;
            if (term.has("embeddingMethodVersion")) versionTerm = term;
        }
        assertNotNull(methodTerm, "expected embeddingMethod term clause: " + filter);
        assertNotNull(versionTerm, "expected embeddingMethodVersion term clause: " + filter);
        assertEquals("miewid-msv4.1", methodTerm.getString("embeddingMethod"));
        assertEquals("4.1", versionTerm.getString("embeddingMethodVersion"));
    }

    @Test void getMatchQuery_omitsMethodVersionTerms_whenBothNull() {
        // Legacy api_endpoint-only configs: no method/methodVersion to filter
        // on. Neither embeddingMethod nor embeddingMethodVersion term is added.
        org.ecocean.Annotation ann = new org.ecocean.Annotation();
        org.json.JSONArray vec = new org.json.JSONArray();
        for (int i = 0; i < 8; i++) vec.put(0.1d * i);
        new org.ecocean.Embedding(ann, null, null, vec);

        org.json.JSONObject matchingSet = new org.json.JSONObject();
        matchingSet.put("query", new org.json.JSONObject().put("bool",
            new org.json.JSONObject().put("filter", new org.json.JSONArray())));

        org.json.JSONObject q = ann.getMatchQuery(null, null, matchingSet);
        assertNotNull(q);
        org.json.JSONArray filter = q.getJSONObject("query")
            .getJSONObject("knn").getJSONObject("vector")
            .getJSONObject("filter").getJSONObject("bool").getJSONArray("filter");
        for (int i = 0; i < filter.length(); i++) {
            org.json.JSONObject term = filter.getJSONObject(i).optJSONObject("term");
            if (term == null) continue;
            assertFalse(term.has("embeddingMethod"),
                "embeddingMethod term should be absent when method null");
            assertFalse(term.has("embeddingMethodVersion"),
                "embeddingMethodVersion term should be absent when version null");
        }
    }

    @Test void getMatchQuery_preservesMustNot_suppressesSource_noInputMutation() {
        org.ecocean.Annotation ann = new org.ecocean.Annotation();
        org.json.JSONArray vec = new org.json.JSONArray();
        for (int i = 0; i < 8; i++) vec.put(0.1d * i);
        new org.ecocean.Embedding(ann, "miewid-msv4.1", "4.1", vec);

        // matchingSet carrying BOTH a filter clause and a must_not clause
        // (as getMatchingSetQuery produces -- e.g. exclude-own-encounter).
        org.json.JSONArray filter = new org.json.JSONArray();
        filter.put(new org.json.JSONObject().put("term",
            new org.json.JSONObject().put("iaClass", "whaleshark")));
        org.json.JSONArray mustNot = new org.json.JSONArray();
        mustNot.put(new org.json.JSONObject().put("match",
            new org.json.JSONObject().put("encounterId", "enc-self")));
        org.json.JSONObject boolObj = new org.json.JSONObject()
            .put("filter", filter).put("must_not", mustNot);
        org.json.JSONObject matchingSet = new org.json.JSONObject()
            .put("query", new org.json.JSONObject().put("bool", boolObj));
        String before = matchingSet.toString();

        org.json.JSONObject q = ann.getMatchQuery("miewid-msv4.1", "4.1", matchingSet);
        assertNotNull(q);

        // _source suppressed (getMatches only reads _id/_score).
        assertFalse(q.getBoolean("_source"), "_source should be false");

        org.json.JSONObject knnBool = q.getJSONObject("query").getJSONObject("knn")
            .getJSONObject("vector").getJSONObject("filter").getJSONObject("bool");
        // must_not preserved into the knn efficient-filter bool.
        assertTrue(knnBool.has("must_not"), "must_not should be preserved: " + knnBool);
        assertEquals(1, knnBool.getJSONArray("must_not").length());
        // original filter clause + appended method + version terms.
        assertEquals(3, knnBool.getJSONArray("filter").length(),
            "filter should hold original + method + version: " + knnBool.getJSONArray("filter"));

        // getMatchQuery must work on a copy, not mutate its input.
        assertEquals(before, matchingSet.toString(),
            "getMatchQuery must not mutate the matchingSetQuery argument");
    }
}
