OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e41b0-9631-75f2-90a1-ea74c4d02f0b
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing code on the `migrate-ml-service-v2` branch of the
Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you the
project conventions, repo gotchas, and current architecture that the
code under review assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (3.1 on the live amphibian-reptile deployment),
  React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has the 20 v2 commits plus the Track 1 empty-match-prospects work
  in progress. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md` and
  `docs/plans/2026-05-18-empty-match-prospects-design.md`.

## Shepherd pattern

```java
Shepherd shep = new Shepherd(context);
shep.setAction(ACTION_PREFIX + "methodName");
try {
    shep.beginDBTransaction();
    // ... JDO operations ...
    shep.commitDBTransaction();
} catch (Exception ex) {
    // log
} finally {
    shep.rollbackAndClose();
}
```

`rollbackAndClose` is idempotent — safe after commit and safe after
early return.

**Critical gotcha:** never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the HTTP
work without Shepherd in Phase B, persist outcome in a fresh Shepherd
in Phase C.

## JDO naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes. The
`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
direct FK, not a JDO-generated join).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh
boundary; **does not** drain the Wildbook IndexingManager queue. If
you need "after this write the doc must be searchable" semantics,
use `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` (added
in c7, commit `f429c5bf8`).

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",   // "vector" = ml-service v2
        "method": "miewid-msv4.1",   // embedding model id
        "version": "4.1",
        "embedding_dimension": 2152,
        // legacy entries have api_endpoint instead of method/version
      }
    },
    "_mlservice_conf": {
      "default": {
        "base_url": "https://ml-service.example.com:8008",
        "detection_endpoint": "/pipeline/",
        "extraction_endpoint": "/extract/",
        "model_id": "...",
        "match_against_species": [...]
      }
    }
  }
}
```

`Embedding.findMatchProspects` gates entry on
`isVectorConfig = method != null || api_endpoint != null` — both
nullable independently.

## v1 antipatterns to avoid

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.

## CRLF/LF gotcha on this Windows-mounted repo

The Edit tool sometimes flips LF files to CRLF when editing on
`/mnt/c/Wildbook-clean2`. Reviewers should call this out if they
see `git ls-files --eol` reporting `i/lf w/crlf`.

## What we want from this review

Code-review the diff below. Focus on:
- Correctness given the Wildbook conventions above.
- Whether the implementation matches the locked design.
- Test coverage and gaps.
- Anything else.

**Do not write to any file.** Review-only.

---

# Codex code-review: C17 — MiewID-parity scoring

User reported match-result scores look weird: top scores 3.0,
descending to 2.76 — not the [0, 1] range MiewID natively
produces. Two contributors to the score drift, both in
`Annotation.getMatchQuery`:

1. Inside the nested embeddings bool, method/methodVersion terms
   were in `must`, adding +1.0 each to the score (the +2.0 offset
   that turned [0, 1] into [2, 3]).
2. Even after removing that offset, OS Lucene knn with
   `cosinesimil` returns `(1 + cos) / 2` — a [0, 1] transform of
   cosine, not the raw cosine MiewID emits.

## Fix

- Move method/methodVersion from `must` to `filter` inside the
  nested bool. They still constrain results (filter clauses don't
  affect knn candidate selection or filtering); they just don't
  contribute to score.
- Add `Annotation.openSearchScoreToCosine(osScore)` static helper
  that computes `2 * osScore - 1` (with [-1, 1] clamp for
  defense-in-depth), and apply it in `getMatches()` before
  `setOpensearchScore`.

Result: a hit with perfect cos similarity reads 1.0; a weaker
match reads e.g. 0.56 instead of 2.78. Same ordering, same
candidates, range matches MiewID's native output.

## Diff

diff --git a/src/main/java/org/ecocean/Annotation.java b/src/main/java/org/ecocean/Annotation.java
index 104069857..846d89acf 100644
--- a/src/main/java/org/ecocean/Annotation.java
+++ b/src/main/java/org/ecocean/Annotation.java
@@ -1196,19 +1196,29 @@ public class Annotation extends Base implements java.io.Serializable {
         if (emb == null) return null;
         JSONObject nested = new JSONObject(
             "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
+        // Inside the nested bool, keep ONLY the knn clause in `must` so the
+        // per-hit score is exactly the OS knn similarity (no spurious
+        // +1.0-per-term-clause offset). method/methodVersion become
+        // `filter` clauses — they still constrain results but contribute
+        // 0 to score. (Empty-match-prospects C17: MiewID score parity.)
         JSONArray must = new JSONArray();
         JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
         knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
             new JSONArray(emb.vectorToFloatArray()));
         knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
         must.put(knn);
+        JSONArray filter = new JSONArray();
         if (method != null)
-            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
+            filter.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
         if (methodVersion != null)
-            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
+            filter.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
                 "\"}}"));
         nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
             must);
+        if (filter.length() > 0) {
+            nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put(
+                "filter", filter);
+        }
 
         // we put nested under its own top-level must, that way its score counts (whereas filter does not)
         JSONArray nestedMust = new JSONArray();
@@ -1217,6 +1227,21 @@ public class Annotation extends Base implements java.io.Serializable {
         return matchingSetQuery;
     }
 
+    /**
+     * Transform an OpenSearch knn score (Lucene's
+     * {@code (1 + cos) / 2}, range [0, 1]) to raw cosine similarity in
+     * [-1, 1] — the value the MiewID pipeline itself reports. Public so
+     * tests and any future scoring consumers use the same conversion.
+     *
+     * <p>(Empty-match-prospects C17.)</p>
+     */
+    public static double openSearchScoreToCosine(double osScore) {
+        double cos = 2.0 * osScore - 1.0;
+        if (cos > 1.0) return 1.0;
+        if (cos < -1.0) return -1.0;
+        return cos;
+    }
+
     // finds annotations based on embedding vector matches
     // null means we didnt have an embedding to query with
     public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
@@ -1251,7 +1276,12 @@ public class Annotation extends Base implements java.io.Serializable {
             if (hit == null) continue;
             Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
             if (ann != null) {
-                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
+                // OS Lucene knn with cosinesimil returns (1 + cos) / 2 in
+                // [0, 1]. Convert to raw cosine in [-1, 1] so the score
+                // matches the MiewID pipeline's native output.
+                // (Empty-match-prospects C17.)
+                double osScore = hit.optDouble("_score", 0.0d);
+                ann.setOpensearchScore(openSearchScoreToCosine(osScore));
                 anns.add(ann);
             }
         }


## New test file:

```java
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
}
```


## Validation

- AnnotationMiewIDScoreTest: 8/8 (new)
- AnnotationTest: 7/7 (unchanged)
- MatchResultTest: 3/3 (unchanged)
- Compiles cleanly
- LF-normalized

## Review scope

- Is the `(1 + cos) / 2` assumption correct for OS 3.1 with
  Lucene-backed knn + `space_type=cosinesimil`? User's live data
  is consistent: 3.0 max (with old +2.0 offset) → 1.0 after
  offset removal → 1.0 raw cosine. The 2.76 min → 0.76 after
  offset → 0.52 raw. The transform 2*x - 1 maps both correctly
  but assumes OS is using `(1+cos)/2`.
- If the deployment ever switches to nmslib engine (returns
  `1 + cos` in [0, 2]), my transform would produce raw cosines
  in [-1, 3]; the clamp would cap at [-1, 1] but the relative
  ordering would distort. Defense-in-depth note worth knowing
  about — happy to add an env-var guard or just document the
  assumption.
- Filter-not-must placement: any downstream caller that depended
  on the method/methodVersion clauses being in `must` (e.g., a
  log analyzer parsing the query JSON)?
- Anything else.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll verify the relevant `Annotation` code and nearby tests so the review can cite real line numbers and catch interactions outside the pasted diff. No file writes.
exec
/bin/bash -lc 'git diff -- src/main/java/org/ecocean/Annotation.java src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short && git branch --show-current' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"openSearchScoreToCosine|getMatchQuery|getMatches|setOpensearchScore|cosinesimil|space_type|embeddings\\.method\" src/main/java src/test/java docs/plans" in /mnt/c/Wildbook-clean2
 succeeded in 943ms:
diff --git a/src/main/java/org/ecocean/Annotation.java b/src/main/java/org/ecocean/Annotation.java
index 104069857..846d89acf 100644
--- a/src/main/java/org/ecocean/Annotation.java
+++ b/src/main/java/org/ecocean/Annotation.java
@@ -1196,19 +1196,29 @@ public class Annotation extends Base implements java.io.Serializable {
         if (emb == null) return null;
         JSONObject nested = new JSONObject(
             "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
+        // Inside the nested bool, keep ONLY the knn clause in `must` so the
+        // per-hit score is exactly the OS knn similarity (no spurious
+        // +1.0-per-term-clause offset). method/methodVersion become
+        // `filter` clauses — they still constrain results but contribute
+        // 0 to score. (Empty-match-prospects C17: MiewID score parity.)
         JSONArray must = new JSONArray();
         JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
         knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
             new JSONArray(emb.vectorToFloatArray()));
         knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
         must.put(knn);
+        JSONArray filter = new JSONArray();
         if (method != null)
-            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
+            filter.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
         if (methodVersion != null)
-            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
+            filter.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
                 "\"}}"));
         nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
             must);
+        if (filter.length() > 0) {
+            nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put(
+                "filter", filter);
+        }
 
         // we put nested under its own top-level must, that way its score counts (whereas filter does not)
         JSONArray nestedMust = new JSONArray();
@@ -1217,6 +1227,21 @@ public class Annotation extends Base implements java.io.Serializable {
         return matchingSetQuery;
     }
 
+    /**
+     * Transform an OpenSearch knn score (Lucene's
+     * {@code (1 + cos) / 2}, range [0, 1]) to raw cosine similarity in
+     * [-1, 1] — the value the MiewID pipeline itself reports. Public so
+     * tests and any future scoring consumers use the same conversion.
+     *
+     * <p>(Empty-match-prospects C17.)</p>
+     */
+    public static double openSearchScoreToCosine(double osScore) {
+        double cos = 2.0 * osScore - 1.0;
+        if (cos > 1.0) return 1.0;
+        if (cos < -1.0) return -1.0;
+        return cos;
+    }
+
     // finds annotations based on embedding vector matches
     // null means we didnt have an embedding to query with
     public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
@@ -1251,7 +1276,12 @@ public class Annotation extends Base implements java.io.Serializable {
             if (hit == null) continue;
             Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
             if (ann != null) {
-                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
+                // OS Lucene knn with cosinesimil returns (1 + cos) / 2 in
+                // [0, 1]. Convert to raw cosine in [-1, 1] so the score
+                // matches the MiewID pipeline's native output.
+                // (Empty-match-prospects C17.)
+                double osScore = hit.optDouble("_score", 0.0d);
+                ann.setOpensearchScore(openSearchScoreToCosine(osScore));
                 anns.add(ann);
             }
         }

 succeeded in 1319ms:
src/main/java/org/ecocean/Base.java:113:    public void setOpensearchScore(double s) {
src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
src/main/java/org/ecocean/Embedding.java:334:            // Count eligible candidates BEFORE getMatchQuery, which mutates
src/main/java/org/ecocean/Embedding.java:338:            // matchingSetQuery to getMatchQuery so the eligible-set object
src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:370:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-19-empty-results-rca.md:7967:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-19-empty-results-rca.md:7971:                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-19-empty-results-rca.md:7977:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-19-empty-results-rca.md:8949:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-19-empty-results-rca.md:8959:            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-19-empty-results-rca.md:8963:            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-19-empty-results-rca.md:8981:            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-19-empty-results-rca.md:8985:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-19-empty-results-rca.md:8989:                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-19-empty-results-rca.md:8995:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-19-empty-results-rca.md:9208:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-19-empty-results-rca.md:9212:                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-19-empty-results-rca.md:9225:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-19-c15-codex-review.md:1401:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-19-c15-codex-review.md:1405:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-19-c15-codex-review.md:1409:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-19-c15-codex-review.md:1415:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
src/main/java/org/ecocean/OpenSearch.java:555:    // embeddings.method/methodVersion would pass _id-only and then knn-fail
src/main/java/org/ecocean/OpenSearch.java:557:    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
src/main/java/org/ecocean/OpenSearch.java:565:    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
src/main/java/org/ecocean/OpenSearch.java:628:        // Nested embedding clause. Match Annotation.getMatchQuery at
src/main/java/org/ecocean/OpenSearch.java:638:                new JSONObject().put("embeddings.method", method)));
src/main/java/org/ecocean/OpenSearch.java:642:                new JSONObject().put("embeddings.methodVersion", methodVersion)));
docs/plans/2026-05-18-empty-match-prospects-design.md:51:  `Annotation.getMatchQuery`'s independent strict-when-present
docs/plans/2026-05-18-empty-match-prospects-design.md:76:  (Codex round-4 Major), matching `Annotation.getMatchQuery` at
docs/plans/2026-05-18-empty-match-prospects-design.md:122:  embeddings.method/methodVersion). NOT a full matchingSetQuery
docs/plans/2026-05-18-empty-match-prospects-design.md:151:  by id but missing nested `embeddings.method`/`methodVersion`
docs/plans/2026-05-18-empty-match-prospects-design.md:470:(`MLService.java:348`). `Annotation.getMatchQuery` omits each
docs/plans/2026-05-18-empty-match-prospects-design.md:491:NOT confirm the doc has its nested `embeddings.method` /
docs/plans/2026-05-18-empty-match-prospects-design.md:521:              { "term": { "embeddings.method":        "<method>" } },
docs/plans/2026-05-18-empty-match-prospects-design.md:522:              { "term": { "embeddings.methodVersion": "<version>" } }
docs/plans/2026-05-18-empty-match-prospects-design.md:532:the version predicate, matching `getMatchQuery`'s behavior
docs/plans/2026-05-18-empty-match-prospects-design.md:574:// matchAgainst + acmId + embeddings.method/methodVersion).
docs/plans/2026-05-18-empty-match-prospects-design.md:909:  `embeddings.method`; prove it's filtered out.
docs/plans/2026-05-18-empty-match-prospects-design.md:914:  matches `getMatchQuery` behavior.
docs/plans/2026-05-18-empty-match-prospects-design.md:1020:- `methodVersion` strict-when-present, matching `getMatchQuery`
src/main/java/org/ecocean/Annotation.java:201:        embVect.put("space_type", "cosinesimil");
src/main/java/org/ecocean/Annotation.java:1167:                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
src/main/java/org/ecocean/Annotation.java:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
src/main/java/org/ecocean/Annotation.java:1212:            filter.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
src/main/java/org/ecocean/Annotation.java:1214:            filter.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
src/main/java/org/ecocean/Annotation.java:1238:    public static double openSearchScoreToCosine(double osScore) {
src/main/java/org/ecocean/Annotation.java:1247:    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
src/main/java/org/ecocean/Annotation.java:1249:        return getMatches(myShepherd,
src/main/java/org/ecocean/Annotation.java:1250:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
src/main/java/org/ecocean/Annotation.java:1254:    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
src/main/java/org/ecocean/Annotation.java:1270:            System.out.println("getMatches() exception: " + ex);
src/main/java/org/ecocean/Annotation.java:1279:                // OS Lucene knn with cosinesimil returns (1 + cos) / 2 in
src/main/java/org/ecocean/Annotation.java:1284:                ann.setOpensearchScore(openSearchScoreToCosine(osScore));
src/main/java/org/ecocean/Annotation.java:1288:        System.out.println("getMatches() results: hitSize=" + hitSize + "; hits length=" +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4438:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4442:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4455:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4465:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4469:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4487:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4491:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:231:  (Codex round-4 Major), matching `Annotation.getMatchQuery` at
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:277:  embeddings.method/methodVersion). NOT a full matchingSetQuery
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:306:  by id but missing nested `embeddings.method`/`methodVersion`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:625:`Annotation.getMatchQuery`'s strict-when-present behavior at
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:636:NOT confirm the doc has its nested `embeddings.method` /
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:666:              { "term": { "embeddings.method":        "<method>" } },
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:667:              { "term": { "embeddings.methodVersion": "<version>" } }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:677:the version predicate, matching `getMatchQuery`'s behavior
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:719:// matchAgainst + acmId + embeddings.method/methodVersion).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1047:  `embeddings.method`; prove it's filtered out.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1052:  matches `getMatchQuery` behavior.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1152:- `methodVersion` strict-when-present, matching `getMatchQuery`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1257:src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2262:  1187	        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2267:  1192	    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2281:  1206	            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2283:  1208	            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2355:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2359:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2372:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2382:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2386:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2404:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2408:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2412:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2418:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2429:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2890:src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2892:src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2941:src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2942:src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5896:   113	    public void setOpensearchScore(double s) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8328:   201	        embVect.put("space_type", "cosinesimil");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8457:The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8458:Fix: either conditionally omit `e."METHOD" = ?` and the OpenSearch `embeddings.method` term when method is blank, or explicitly reject api-endpoint configs that cannot derive a method before the gate. Add tests for `method == null`, not only `methodVersion == null`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8481:The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8482:Fix: either conditionally omit `e."METHOD" = ?` and the OpenSearch `embeddings.method` term when method is blank, or explicitly reject api-endpoint configs that cannot derive a method before the gate. Add tests for `method == null`, not only `methodVersion == null`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:239:  embeddings.method/methodVersion). NOT a full matchingSetQuery
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:268:  by id but missing nested `embeddings.method`/`methodVersion`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:548:NOT confirm the doc has its nested `embeddings.method` /
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:578:              { "term": { "embeddings.method":        "<method>" } },
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:579:              { "term": { "embeddings.methodVersion": "<version>" } }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:589:the version predicate, matching `getMatchQuery`'s behavior
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:903:  `embeddings.method`; prove it's filtered out.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:908:  matches `getMatchQuery` behavior.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:985:- `methodVersion` strict-when-present, matching `getMatchQuery`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2784:                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2799:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2804:        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2809:    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2823:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2825:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2839:    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2841:        return getMatches(myShepherd,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2842:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2846:    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2873:src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2875:src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2918:src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:2919:src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:3311:        embVect.put("space_type", "cosinesimil");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7269:        embVect.put("space_type", "cosinesimil");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7610:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7614:                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7627:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7637:            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7641:            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7659:            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7663:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7667:                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7673:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8538:src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8622:- **Major:** Phase 2 SQL must mirror `getMatchQuery` version handling. `Annotation.getMatchQuery` only adds `methodVersion` when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1207)); the SQL sketch’s unconditional `e."METHODVERSION" = ?` will drop null-version legacy embeddings. Also derive method/version the same way matching does, including the `MLService.getMethodValues` fallback ([Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:352)).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8649:- **Major:** Phase 2 SQL must mirror `getMatchQuery` version handling. `Annotation.getMatchQuery` only adds `methodVersion` when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1207)); the SQL sketch’s unconditional `e."METHODVERSION" = ?` will drop null-version legacy embeddings. Also derive method/version the same way matching does, including the `MLService.getMethodValues` fallback ([Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:352)).
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:31: * <p>method/methodVersion match Annotation.getMatchQuery semantics
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:111:        // Annotation.getMatchQuery itself at Annotation.java:1205-1209)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:229:  by id but missing nested `embeddings.method`/`methodVersion`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:454:NOT confirm the doc has its nested `embeddings.method` /
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:460:(`Annotation.getMatchQuery` at `Annotation.java:1194`). Conceptual
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:476:              { "term": { "embeddings.method":        "<method>" } },
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:477:              { "term": { "embeddings.methodVersion": "<version>" } }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:487:the version predicate, matching `getMatchQuery`'s behavior.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:783:  `embeddings.method`; prove it's filtered out.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:788:  matches `getMatchQuery` behavior.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:856:   omitting the predicate.** Same as `getMatchQuery`. But: if the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:860:   answer to round-2 #5 says match `getMatchQuery` behavior. I
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:861:   read `getMatchQuery` at `Annotation.java:1205-1209` as
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1031:src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1036:src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1208:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3424:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1213:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3477:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1378:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9470:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1382:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9504:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1595:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4438:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1600:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4491:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:4984:/bin/bash -lc "rg -n \"getMatchQuery|embeddings\\.method|methodVersion|matchingSetQuery|matchAgainst|acmId|nested\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Embedding.java src/test/java/org/ecocean/AnnotationTest.java src/test/java/org/ecocean/EmbeddingTest.java src/test/java/org/ecocean/OpenSearchVisibilityTest.java" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5259:  1167	                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5274:  1182	    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5279:  1187	        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5284:  1192	    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5298:  1206	            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5300:  1208	            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5314:  1222	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5316:  1224	        return getMatches(myShepherd,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5317:  1225	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5321:  1229	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5342:src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5346:src/main/java/org/ecocean/Embedding.java:334:            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5348:src/main/java/org/ecocean/Embedding.java:338:            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5350:src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5377:src/main/java/org/ecocean/Annotation.java:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5380:src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5382:src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5387:src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5389:src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5397:src/main/java/org/ecocean/Annotation.java:1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5448:   201	        embVect.put("space_type", "cosinesimil");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9105:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9109:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9122:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9132:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9136:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9154:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9158:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9162:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9168:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:10406:2. Yes, your methodVersion read is right: `getMatchQuery` applies version strictly when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205)).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:10434:2. Yes, your methodVersion read is right: `getMatchQuery` applies version strictly when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205)).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3424:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3428:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3441:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3451:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3455:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3473:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3477:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3481:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3487:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3752:src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3755:src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4157:/bin/bash -lc 'rg -n "getMatchQuery|getAnEmbedding|numberEmbeddings|addEmbedding|hasEmbedding" src/main/java/org/ecocean/Annotation.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4442:  1182	    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4447:  1187	        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4452:  1192	    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4466:  1206	            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4468:  1208	            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4482:  1222	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4484:  1224	        return getMatches(myShepherd,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4485:  1225	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4489:  1229	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4505:  1245	            System.out.println("getMatches() exception: " + ex);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4514:  1254	                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4518:  1258	        System.out.println("getMatches() results: hitSize=" + hitSize + "; hits length=" +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4529:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4530:1187:        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4531:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4532:1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6115:   113	    public void setOpensearchScore(double s) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6319:   201	        embVect.put("space_type", "cosinesimil");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9470:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9487:5. JDOQL vs direct SQL: use direct SQL here. There is plenty of SQL precedent in this repo, and this join is clearer as SQL. Parameterize/validate inputs and handle null `methodVersion` by omitting that predicate or using `IS NULL`, matching `getMatchQuery` behavior.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9504:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9521:5. JDOQL vs direct SQL: use direct SQL here. There is plenty of SQL precedent in this repo, and this join is clearer as SQL. Parameterize/validate inputs and handle null `methodVersion` by omitting that predicate or using `IS NULL`, matching `getMatchQuery` behavior.
docs/plans/2026-05-18-c9-codex-review.md:149:Method/methodVersion follow Annotation.getMatchQuery's strict-
docs/plans/2026-05-18-c9-codex-review.md:249:        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
docs/plans/2026-05-18-c9-codex-review.md:1489:/bin/bash -lc 'rg -n "getMatchQuery|METHODVERSION|METHOD" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Embedding.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c9-codex-review.md:1520:    30	 * <p>method/methodVersion match Annotation.getMatchQuery semantics
docs/plans/2026-05-18-c9-codex-review.md:1779:    89	        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
docs/plans/2026-05-18-c9-codex-review.md:1869:src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c9-codex-review.md:1870:src/main/java/org/ecocean/Embedding.java:334:            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c9-codex-review.md:1871:src/main/java/org/ecocean/Embedding.java:338:            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c9-codex-review.md:1872:src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c9-codex-review.md:1873:src/main/java/org/ecocean/Annotation.java:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-c9-codex-review.md:1874:src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-c9-codex-review.md:1875:src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-c9-codex-review.md:1876:src/main/java/org/ecocean/Annotation.java:1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-c9-codex-review.md:1896:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c9-codex-review.md:1900:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c9-codex-review.md:1913:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c9-codex-review.md:1923:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c9-codex-review.md:1927:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c9-codex-review.md:1945:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c9-codex-review.md:1949:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c9-codex-review.md:1953:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c9-codex-review.md:1959:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c9-codex-review.md:1984:  1182	    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-c9-codex-review.md:1989:  1187	        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-c9-codex-review.md:1994:  1192	    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-c9-codex-review.md:2008:  1206	            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-c9-codex-review.md:2010:  1208	            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-c9-codex-review.md:2024:  1222	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-c9-codex-review.md:2026:  1224	        return getMatches(myShepherd,
docs/plans/2026-05-18-c9-codex-review.md:2027:  1225	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-c9-codex-review.md:2031:  1229	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
docs/plans/2026-05-18-c9-codex-review.md:2308:   470	(`MLService.java:348`). `Annotation.getMatchQuery` omits each
docs/plans/2026-05-18-c9-codex-review.md:2329:   491	NOT confirm the doc has its nested `embeddings.method` /
docs/plans/2026-05-18-c9-codex-review.md:2359:   521	              { "term": { "embeddings.method":        "<method>" } },
docs/plans/2026-05-18-c9-codex-review.md:2360:   522	              { "term": { "embeddings.methodVersion": "<version>" } }
docs/plans/2026-05-18-c9-codex-review.md:2370:   532	the version predicate, matching `getMatchQuery`'s behavior
docs/plans/2026-05-18-c9-codex-review.md:2771:  1167	                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
docs/plans/2026-05-18-c9-codex-review.md:2793:src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-c9-codex-review.md:2794:src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-c9-codex-review.md:2881:   555	    // embeddings.method/methodVersion would pass _id-only and then knn-fail
docs/plans/2026-05-18-c9-codex-review.md:2883:   557	    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
docs/plans/2026-05-18-c9-codex-review.md:2891:   565	    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
docs/plans/2026-05-18-c9-codex-review.md:2954:   628	        // Nested embedding clause. Match Annotation.getMatchQuery at
docs/plans/2026-05-18-c9-codex-review.md:2964:   638	                new JSONObject().put("embeddings.method", method)));
docs/plans/2026-05-18-c9-codex-review.md:2968:   642	                new JSONObject().put("embeddings.methodVersion", methodVersion)));
docs/plans/2026-05-18-c9-codex-review.md:3066:   574	// matchAgainst + acmId + embeddings.method/methodVersion).
docs/plans/2026-05-18-c8-codex-review.md:153:Annotation.getMatchQuery at Annotation.java:1205-1209). Both blank
docs/plans/2026-05-18-c8-codex-review.md:175:+    // embeddings.method/methodVersion would pass _id-only and then knn-fail
docs/plans/2026-05-18-c8-codex-review.md:177:+    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
docs/plans/2026-05-18-c8-codex-review.md:185:+    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
docs/plans/2026-05-18-c8-codex-review.md:249:+        // conditionally to match Annotation.getMatchQuery semantics: a
docs/plans/2026-05-18-c8-codex-review.md:254:+                new JSONObject().put("embeddings.method", method)));
docs/plans/2026-05-18-c8-codex-review.md:258:+                new JSONObject().put("embeddings.methodVersion", methodVersion)));
docs/plans/2026-05-18-c8-codex-review.md:387:            if (term.has("embeddings.method")) methodTerm = term;
docs/plans/2026-05-18-c8-codex-review.md:388:            if (term.has("embeddings.methodVersion")) versionTerm = term;
docs/plans/2026-05-18-c8-codex-review.md:392:        assertEquals("miewid-msv4.1", methodTerm.getString("embeddings.method"));
docs/plans/2026-05-18-c8-codex-review.md:393:        assertEquals("4.1", versionTerm.getString("embeddings.methodVersion"));
docs/plans/2026-05-18-c8-codex-review.md:406:        assertTrue(term.has("embeddings.method"));
docs/plans/2026-05-18-c8-codex-review.md:407:        assertFalse(term.has("embeddings.methodVersion"));
docs/plans/2026-05-18-c8-codex-review.md:419:        assertTrue(term.has("embeddings.methodVersion"));
docs/plans/2026-05-18-c8-codex-review.md:420:        assertFalse(term.has("embeddings.method"));
docs/plans/2026-05-18-c8-codex-review.md:471:  exists AND nested embeddings.method/methodVersion. Mirrors
docs/plans/2026-05-18-c8-codex-review.md:472:  Annotation.getMatchQuery's strict-when-present semantics.
docs/plans/2026-05-18-c8-codex-review.md:492:/bin/bash -lc "rg -n \"getMatchQuery|embeddings\\.method|methodVersion|matchAgainst|acmId\" src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c8-codex-review.md:537:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-c8-codex-review.md:540:1187:        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-c8-codex-review.md:541:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-c8-codex-review.md:543:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-c8-codex-review.md:545:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-c8-codex-review.md:547:1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-c8-codex-review.md:940:The helper is present in the working tree. I’m checking the adjacent existing `waitForVisibility` and `Annotation.getMatchQuery` implementation now, because the important review question is whether the new predicate really matches the established match query semantics.
docs/plans/2026-05-18-c8-codex-review.md:1035:   555	    // embeddings.method/methodVersion would pass _id-only and then knn-fail
docs/plans/2026-05-18-c8-codex-review.md:1037:   557	    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
docs/plans/2026-05-18-c8-codex-review.md:1045:   565	    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
docs/plans/2026-05-18-c8-codex-review.md:1109:   629	        // conditionally to match Annotation.getMatchQuery semantics: a
docs/plans/2026-05-18-c8-codex-review.md:1114:   634	                new JSONObject().put("embeddings.method", method)));
docs/plans/2026-05-18-c8-codex-review.md:1118:   638	                new JSONObject().put("embeddings.methodVersion", methodVersion)));
docs/plans/2026-05-18-c8-codex-review.md:1311:  1182	    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-c8-codex-review.md:1316:  1187	        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-c8-codex-review.md:1321:  1192	    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-c8-codex-review.md:1335:  1206	            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-c8-codex-review.md:1337:  1208	            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-c8-codex-review.md:1351:  1222	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-c8-codex-review.md:1353:  1224	        return getMatches(myShepherd,
docs/plans/2026-05-18-c8-codex-review.md:1354:  1225	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-c8-codex-review.md:1361:/bin/bash -lc "rg -n \"getMatchQuery\\(|getMatches\\(|findMatchProspects|methodVersion|method\" src/main/java/org/ecocean | head -200" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c8-codex-review.md:1414:   201	        embVect.put("space_type", "cosinesimil");
docs/plans/2026-05-18-c8-codex-review.md:1571:src/main/java/org/ecocean/OpenSearch.java:555:    // embeddings.method/methodVersion would pass _id-only and then knn-fail
docs/plans/2026-05-18-c8-codex-review.md:1581:src/main/java/org/ecocean/OpenSearch.java:634:                new JSONObject().put("embeddings.method", method)));
docs/plans/2026-05-18-c8-codex-review.md:1583:src/main/java/org/ecocean/OpenSearch.java:638:                new JSONObject().put("embeddings.methodVersion", methodVersion)));
docs/plans/2026-05-18-c8-codex-review.md:1653:src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c8-codex-review.md:1654:src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c8-codex-review.md:1661:src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c8-codex-review.md:1662:src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c8-codex-review.md:1663:src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c8-codex-review.md:1664:src/main/java/org/ecocean/Embedding.java:370:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c8-codex-review.md:1856:One discrepancy is emerging around “blank means omitted”: the new helper treats blank strings as absent, but the current `Annotation.getMatchQuery` code only omits when the value is actually `null`. I’m tracing the ml-service config path to see whether callers normalize blanks before reaching matching.
docs/plans/2026-05-18-c8-codex-review.md:1921:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c8-codex-review.md:1925:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c8-codex-review.md:1938:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c8-codex-review.md:1948:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c8-codex-review.md:1952:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c8-codex-review.md:1970:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c8-codex-review.md:1974:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c8-codex-review.md:1978:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c8-codex-review.md:1984:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c8-codex-review.md:2661:   521	              { "term": { "embeddings.method":        "<method>" } },
docs/plans/2026-05-18-c8-codex-review.md:2662:   522	              { "term": { "embeddings.methodVersion": "<version>" } }
docs/plans/2026-05-18-c8-codex-review.md:2672:   532	the version predicate, matching `getMatchQuery`'s behavior
docs/plans/2026-05-18-c8-codex-review.md:2714:   574	// matchAgainst + acmId + embeddings.method/methodVersion).
docs/plans/2026-05-18-c8-codex-review.md:2947:   101	            if (term.has("embeddings.method")) methodTerm = term;
docs/plans/2026-05-18-c8-codex-review.md:2948:   102	            if (term.has("embeddings.methodVersion")) versionTerm = term;
docs/plans/2026-05-18-c8-codex-review.md:2952:   106	        assertEquals("miewid-msv4.1", methodTerm.getString("embeddings.method"));
docs/plans/2026-05-18-c8-codex-review.md:2953:   107	        assertEquals("4.1", versionTerm.getString("embeddings.methodVersion"));
docs/plans/2026-05-18-c8-codex-review.md:2966:   120	        assertTrue(term.has("embeddings.method"));
docs/plans/2026-05-18-c8-codex-review.md:2967:   121	        assertFalse(term.has("embeddings.methodVersion"));
docs/plans/2026-05-18-c8-codex-review.md:2979:   133	        assertTrue(term.has("embeddings.methodVersion"));
docs/plans/2026-05-18-c8-codex-review.md:2980:   134	        assertFalse(term.has("embeddings.method"));
docs/plans/2026-05-18-c8-codex-review.md:3074:- **Major:** Blank handling does not actually match the current match path. [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:632) and [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:636) omit predicates for `""`, but [Annotation.getMatchQuery](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205) only omits when values are `null`, and [getEmbeddingByMethod](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:2047) treats `methodVersion == ""` as strict. So `waitForAnnotationMatchableIds(ids, "miewid-msv4.1", "")` can return ready for docs with any `miewid-msv4.1` embedding, while matching may return `null` or query `embeddings.methodVersion:""`. Either normalize blank to `null` before both waiting and matching, or make the helper strict on non-null to match existing behavior. The current test at [OpenSearchAnnotationMatchableQueryTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:137) locks in behavior the matcher does not yet share.
docs/plans/2026-05-18-c8-codex-review.md:3083:- **Major:** Blank handling does not actually match the current match path. [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:632) and [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:636) omit predicates for `""`, but [Annotation.getMatchQuery](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205) only omits when values are `null`, and [getEmbeddingByMethod](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:2047) treats `methodVersion == ""` as strict. So `waitForAnnotationMatchableIds(ids, "miewid-msv4.1", "")` can return ready for docs with any `miewid-msv4.1` embedding, while matching may return `null` or query `embeddings.methodVersion:""`. Either normalize blank to `null` before both waiting and matching, or make the helper strict on non-null to match existing behavior. The current test at [OpenSearchAnnotationMatchableQueryTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:137) locks in behavior the matcher does not yet share.
docs/plans/2026-05-18-c13-codex-review.md:2658:src/main/java/org/ecocean/Embedding.java:370:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c13-codex-review.md:3015:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c13-codex-review.md:3019:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c13-codex-review.md:3023:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c13-codex-review.md:3029:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c13-codex-review.md:3241:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c13-codex-review.md:3245:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c13-codex-review.md:3258:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c13-codex-review.md:3268:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c13-codex-review.md:3272:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c13-codex-review.md:6518:docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8457:The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
docs/plans/2026-05-18-c13-codex-review.md:6519:docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8481:The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
docs/plans/2026-05-18-c12-codex-review.md:2316:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c12-codex-review.md:2320:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c12-codex-review.md:2338:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c12-codex-review.md:2342:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c12-codex-review.md:2346:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c12-codex-review.md:2352:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c12-codex-review.md:2540:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c12-codex-review.md:2544:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c12-codex-review.md:2557:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c12-codex-review.md:2567:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c12-codex-review.md:2571:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c12-codex-review.md:2589:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c12-codex-review.md:2593:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c12-codex-review.md:2597:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c12-codex-review.md:2603:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c12-codex-review.md:2651:src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c12-codex-review.md:2654:src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c6-codex-review.md:2921:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1378:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9470:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-c6-codex-review.md:2922:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1382:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9504:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-c6-codex-review.md:3118:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9470:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-c6-codex-review.md:3120:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9504:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-c11-codex-review.md:6493:   111	        // Annotation.getMatchQuery itself at Annotation.java:1205-1209)
docs/plans/2026-05-18-c11-codex-review.md:13003:   555	    // embeddings.method/methodVersion would pass _id-only and then knn-fail
docs/plans/2026-05-18-c11-codex-review.md:13005:   557	    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
docs/plans/2026-05-18-c11-codex-review.md:13013:   565	    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
docs/plans/2026-05-18-c11-codex-review.md:13076:   628	        // Nested embedding clause. Match Annotation.getMatchQuery at
docs/plans/2026-05-18-c11-codex-review.md:13091:   638	                new JSONObject().put("embeddings.method", method)));
docs/plans/2026-05-18-c11-codex-review.md:13095:   642	                new JSONObject().put("embeddings.methodVersion", methodVersion)));
docs/plans/2026-05-18-c11-codex-review.md:13260:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c11-codex-review.md:13261:324:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c11-codex-review.md:13266:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c11-codex-review.md:13331:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c11-codex-review.md:13335:   311	                        "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c11-codex-review.md:13348:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c11-codex-review.md:13358:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c11-codex-review.md:13362:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c11-codex-review.md:13380:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c11-codex-review.md:13384:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c11-codex-review.md:13388:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c11-codex-review.md:13394:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c10-codex-review.md:8727:    31	 * <p>method/methodVersion match Annotation.getMatchQuery semantics
docs/plans/2026-05-18-c10-codex-review.md:9007:   555	    // embeddings.method/methodVersion would pass _id-only and then knn-fail
docs/plans/2026-05-18-c10-codex-review.md:9009:   557	    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
docs/plans/2026-05-18-c10-codex-review.md:9017:   565	    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
docs/plans/2026-05-18-c10-codex-review.md:9149:   628	        // Nested embedding clause. Match Annotation.getMatchQuery at
docs/plans/2026-05-18-c10-codex-review.md:9159:   638	                new JSONObject().put("embeddings.method", method)));
docs/plans/2026-05-18-c10-codex-review.md:9163:   642	                new JSONObject().put("embeddings.methodVersion", methodVersion)));
docs/plans/2026-05-18-c10-codex-review.md:9351:/bin/bash -lc "rg -n \"findMatchProspects|getMatchQuery|getEmbeddingByMethod|getMethodValues|optString\\(\\\"method\\\"|optString\\(\\\"version\\\"\" src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/ia/MLService.java src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c10-codex-review.md:9361:   324	            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c10-codex-review.md:9371:   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c10-codex-review.md:9375:   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c10-codex-review.md:9393:   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c10-codex-review.md:9397:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c10-codex-review.md:9401:   364	                    "no suitable embeddings for getMatches()");
docs/plans/2026-05-18-c10-codex-review.md:9407:   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
docs/plans/2026-05-18-c10-codex-review.md:9455:src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c10-codex-review.md:9456:src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
docs/plans/2026-05-18-c10-codex-review.md:9458:src/main/java/org/ecocean/Embedding.java:334:            // Count eligible candidates BEFORE getMatchQuery, which mutates
docs/plans/2026-05-18-c10-codex-review.md:9459:src/main/java/org/ecocean/Embedding.java:338:            // matchingSetQuery to getMatchQuery so the eligible-set object
docs/plans/2026-05-18-c10-codex-review.md:9464:src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
docs/plans/2026-05-18-c10-codex-review.md:9465:src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-c10-codex-review.md:9469:src/main/java/org/ecocean/Annotation.java:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-c10-codex-review.md:9471:src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-c10-codex-review.md:9472:src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-c10-codex-review.md:9474:src/main/java/org/ecocean/Annotation.java:1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-c10-codex-review.md:9479:src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:141:        // helper matches Annotation.getMatchQuery's strict-when-non-null
docs/plans/2026-05-18-c10-codex-review.md:9480:src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:154:        // same as null, but Annotation.getMatchQuery only omits on null.
docs/plans/2026-05-18-c10-codex-review.md:9483:src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:89:        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
docs/plans/2026-05-18-c10-codex-review.md:9531:  1182	    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
docs/plans/2026-05-18-c10-codex-review.md:9536:  1187	        return getMatchQuery(method, methodVersion,
docs/plans/2026-05-18-c10-codex-review.md:9541:  1192	    public JSONObject getMatchQuery(String method, String methodVersion,
docs/plans/2026-05-18-c10-codex-review.md:9555:  1206	            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
docs/plans/2026-05-18-c10-codex-review.md:9557:  1208	            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
docs/plans/2026-05-18-c10-codex-review.md:9571:  1222	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-c10-codex-review.md:9573:  1224	        return getMatches(myShepherd,
docs/plans/2026-05-18-c10-codex-review.md:9574:  1225	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
docs/plans/2026-05-18-c10-codex-review.md:9578:  1229	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
docs/plans/2026-05-18-c10-codex-review.md:9797:    51	  `Annotation.getMatchQuery`'s independent strict-when-present
docs/plans/2026-05-18-c10-codex-review.md:9822:    76	  (Codex round-4 Major), matching `Annotation.getMatchQuery` at
docs/plans/2026-05-18-c10-codex-review.md:9868:   122	  embeddings.method/methodVersion). NOT a full matchingSetQuery
docs/plans/2026-05-18-c10-codex-review.md:9897:   151	  by id but missing nested `embeddings.method`/`methodVersion`
docs/plans/2026-05-18-c10-codex-review.md:10167:   470	(`MLService.java:348`). `Annotation.getMatchQuery` omits each
docs/plans/2026-05-18-c10-codex-review.md:10188:   491	NOT confirm the doc has its nested `embeddings.method` /
docs/plans/2026-05-18-c10-codex-review.md:10499:   521	              { "term": { "embeddings.method":        "<method>" } },
docs/plans/2026-05-18-c10-codex-review.md:10500:   522	              { "term": { "embeddings.methodVersion": "<version>" } }
docs/plans/2026-05-18-c10-codex-review.md:10510:   532	the version predicate, matching `getMatchQuery`'s behavior
docs/plans/2026-05-18-c10-codex-review.md:10552:   574	// matchAgainst + acmId + embeddings.method/methodVersion).
docs/plans/2026-05-18-c10-codex-review.md:18991:    89	        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
docs/plans/2026-05-18-c10-codex-review.md:19036:   133	        assertTrue(term.has("embeddings.methodVersion"));
docs/plans/2026-05-18-c10-codex-review.md:19037:   134	        assertFalse(term.has("embeddings.method"));
docs/plans/2026-05-18-c10-codex-review.md:19044:   141	        // helper matches Annotation.getMatchQuery's strict-when-non-null
docs/plans/2026-05-18-c10-codex-review.md:19057:   154	        // same as null, but Annotation.getMatchQuery only omits on null.
docs/plans/2026-05-18-c10-codex-review.md:19071:   168	            if (term.has("embeddings.methodVersion")) versionTerm = term;
docs/plans/2026-05-18-c10-codex-review.md:19080:- **Major: blank `methodVersion` is incorrectly normalized to null in the gate.** [MatchVisibilityGateImpl.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:111) converts non-empty checks into null at lines 116-117. That violates the existing strict-when-non-null behavior in `Annotation.getMatchQuery` and the C8/C9 tests, where `""` must remain a strict term, not be omitted. With `method` set and `version: ""`, the gate can wait on a broader sibling set than the actual matcher uses, and can declare READY for docs that `findMatchProspects` will not match.
docs/plans/2026-05-18-c10-codex-review.md:19093:- **Major: blank `methodVersion` is incorrectly normalized to null in the gate.** [MatchVisibilityGateImpl.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:111) converts non-empty checks into null at lines 116-117. That violates the existing strict-when-non-null behavior in `Annotation.getMatchQuery` and the C8/C9 tests, where `""` must remain a strict term, not be omitted. With `method` set and `version: ""`, the gate can wait on a broader sibling set than the actual matcher uses, and can declare READY for docs that `findMatchProspects` will not match.
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:101:            if (term.has("embeddings.method")) methodTerm = term;
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:102:            if (term.has("embeddings.methodVersion")) versionTerm = term;
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:106:        assertEquals("miewid-msv4.1", methodTerm.getString("embeddings.method"));
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:107:        assertEquals("4.1", versionTerm.getString("embeddings.methodVersion"));
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:120:        assertTrue(term.has("embeddings.method"));
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:121:        assertFalse(term.has("embeddings.methodVersion"));
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:133:        assertTrue(term.has("embeddings.methodVersion"));
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:134:        assertFalse(term.has("embeddings.method"));
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:141:        // helper matches Annotation.getMatchQuery's strict-when-non-null
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:154:        // same as null, but Annotation.getMatchQuery only omits on null.
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:168:            if (term.has("embeddings.methodVersion")) versionTerm = term;
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:171:        assertEquals("", versionTerm.getString("embeddings.methodVersion"));
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:89:        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:10: * Tests for the {@link Annotation#openSearchScoreToCosine} helper
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:11: * introduced in C17 to translate OpenSearch Lucene knn cosinesimil
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:22:        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:27:        assertEquals(0.0, Annotation.openSearchScoreToCosine(0.5), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:32:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:39:        assertEquals(1.0,  Annotation.openSearchScoreToCosine(1.0),  EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:40:        assertEquals(0.72, Annotation.openSearchScoreToCosine(0.86), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:41:        assertEquals(0.56, Annotation.openSearchScoreToCosine(0.78), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:42:        assertEquals(0.52, Annotation.openSearchScoreToCosine(0.76), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:49:        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.5), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:50:        assertEquals(1.0, Annotation.openSearchScoreToCosine(100.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:54:        // OS shouldn't return negative scores from cosinesimil but
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:56:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-0.5), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:57:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-100.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:63:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:72:            double cos = Annotation.openSearchScoreToCosine(osScore);

codex
The local diff matches the pasted change. I’m checking the index mapping and the sibling visibility-query code because scoring semantics can also be affected by engine/mapping and by duplicate query builders that may now drift.
 succeeded in 12280ms:
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/
?? src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java
migrate-ml-service-v2

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '180,215p;1148,1295p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "new AnnotationMiewIDScoreTest|class AnnotationMiewIDScoreTest|openSearchScoreToCosine|ScoreTest|getOpensearchScore|setOpensearchScore|opensearchScore" src/test/java src/main/java | head -n 200' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '520,670p'" in /mnt/c/Wildbook-clean2
 succeeded in 229ms:
   180	        map.put("encounterSubmitterId", keywordType);
   181	        map.put("encounterUserUuid", keywordType);
   182	        map.put("encounterLocationId", keywordType);
   183	        map.put("encounterTaxonomy", keywordType);
   184	        map.put("encounterProjectIds", keywordType);
   185	
   186	        // all case-insensitive keyword-ish types
   187	        // map.put("fubar", keywordNormalType);
   188	
   189	        // embeddings have some metadata (algorithm etc)
   190	        // and then the vector that is the embedding
   191	        JSONObject embMap = new JSONObject();
   192	        embMap.put("type", "nested");
   193	        embMap.put("dynamic", false);
   194	        JSONObject embProps = new JSONObject();
   195	        embProps.put("method", keywordType);
   196	        embProps.put("methodVersion", keywordType);
   197	        JSONObject embVect = new JSONObject();
   198	        // https://docs.opensearch.org/docs/latest/vector-search/creating-vector-index/
   199	        embVect.put("type", "knn_vector");
   200	        embVect.put("dimension", Embedding.getVectorDimension());
   201	        embVect.put("space_type", "cosinesimil");
   202	        embProps.put("vector", embVect);
   203	        embMap.put("properties", embProps);
   204	        map.put("embeddings", embMap);
   205	
   206	        return map;
   207	    }
   208	
   209	    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
   210	    throws IOException, JsonProcessingException {
   211	        super.opensearchDocumentSerializer(jgen, myShepherd);
   212	
   213	        jgen.writeStringField("acmId", this.getAcmId());
   214	        jgen.writeStringField("viewpoint", this.getViewpoint());
   215	        jgen.writeStringField("iaClass", this.getIAClass());
  1148	        int hitSize = -1;
  1149	        try {
  1150	            int pageSize = 10000;
  1151	            try {
  1152	                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
  1153	            } catch (Exception ex) {}
  1154	            os.deletePit("annotation");
  1155	            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
  1156	            hitSize = queryRes.optJSONObject("hits").optJSONObject("total").optInt("value");
  1157	        } catch (Exception ex) {
  1158	            System.out.println("getMatchingSet() exception: " + ex);
  1159	            ex.printStackTrace();
  1160	        }
  1161	        JSONArray hits = OpenSearch.getHits(queryRes);
  1162	        for (int i = 0; i < hits.length(); i++) {
  1163	            JSONObject hit = hits.optJSONObject(i);
  1164	            if (hit == null) continue;
  1165	            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
  1166	            if (ann != null) {
  1167	                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
  1168	                anns.add(ann);
  1169	            }
  1170	        }
  1171	        System.out.println("getMatchingSet() results: hitSize=" + hitSize + "; hits length=" +
  1172	            hits.length() + "; anns size=" + anns.size() + "; " +
  1173	            (System.currentTimeMillis() - startTime) + "ms");
  1174	        return anns;
  1175	    }
  1176	
  1177	    // a variation of matchingSet query, but includes the vector stuff - thus returns actual matches(!)
  1178	    // method and methodVersion are used to determine *which* embedding to use; if null it will use 1st embedding
  1179	    // return null when this annot has no embeddings to match, sorry!
  1180	
  1181	    // this version will construct matchingSetQuery
  1182	    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
  1183	        String method, String methodVersion) {
  1184	        Embedding emb = getEmbeddingByMethod(method, methodVersion);
  1185	
  1186	        if (emb == null) return null;
  1187	        return getMatchQuery(method, methodVersion,
  1188	                getMatchingSetQuery(myShepherd, taskParams, useClauses));
  1189	    }
  1190	
  1191	    // this version if you already have matchingSetQuery
  1192	    public JSONObject getMatchQuery(String method, String methodVersion,
  1193	        JSONObject matchingSetQuery) {
  1194	        Embedding emb = getEmbeddingByMethod(method, methodVersion);
  1195	
  1196	        if (emb == null) return null;
  1197	        JSONObject nested = new JSONObject(
  1198	            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
  1199	        // Inside the nested bool, keep ONLY the knn clause in `must` so the
  1200	        // per-hit score is exactly the OS knn similarity (no spurious
  1201	        // +1.0-per-term-clause offset). method/methodVersion become
  1202	        // `filter` clauses — they still constrain results but contribute
  1203	        // 0 to score. (Empty-match-prospects C17: MiewID score parity.)
  1204	        JSONArray must = new JSONArray();
  1205	        JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
  1206	        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
  1207	            new JSONArray(emb.vectorToFloatArray()));
  1208	        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
  1209	        must.put(knn);
  1210	        JSONArray filter = new JSONArray();
  1211	        if (method != null)
  1212	            filter.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
  1213	        if (methodVersion != null)
  1214	            filter.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
  1215	                "\"}}"));
  1216	        nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
  1217	            must);
  1218	        if (filter.length() > 0) {
  1219	            nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put(
  1220	                "filter", filter);
  1221	        }
  1222	
  1223	        // we put nested under its own top-level must, that way its score counts (whereas filter does not)
  1224	        JSONArray nestedMust = new JSONArray();
  1225	        nestedMust.put(nested);
  1226	        matchingSetQuery.getJSONObject("query").getJSONObject("bool").put("must", nestedMust);
  1227	        return matchingSetQuery;
  1228	    }
  1229	
  1230	    /**
  1231	     * Transform an OpenSearch knn score (Lucene's
  1232	     * {@code (1 + cos) / 2}, range [0, 1]) to raw cosine similarity in
  1233	     * [-1, 1] — the value the MiewID pipeline itself reports. Public so
  1234	     * tests and any future scoring consumers use the same conversion.
  1235	     *
  1236	     * <p>(Empty-match-prospects C17.)</p>
  1237	     */
  1238	    public static double openSearchScoreToCosine(double osScore) {
  1239	        double cos = 2.0 * osScore - 1.0;
  1240	        if (cos > 1.0) return 1.0;
  1241	        if (cos < -1.0) return -1.0;
  1242	        return cos;
  1243	    }
  1244	
  1245	    // finds annotations based on embedding vector matches
  1246	    // null means we didnt have an embedding to query with
  1247	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
  1248	        boolean useClauses, String method, String methodVersion) {
  1249	        return getMatches(myShepherd,
  1250	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
  1251	    }
  1252	
  1253	    // where we already have the query
  1254	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
  1255	        if (matchQuery == null) return null;
  1256	        List<Annotation> anns = new ArrayList<Annotation>();
  1257	        OpenSearch os = new OpenSearch();
  1258	        long startTime = System.currentTimeMillis();
  1259	        JSONObject queryRes = null;
  1260	        int hitSize = -1;
  1261	        try {
  1262	            int pageSize = 10000;
  1263	            try {
  1264	                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
  1265	            } catch (Exception ex) {}
  1266	            os.deletePit("annotation");
  1267	            queryRes = os.queryPit("annotation", matchQuery, 0, pageSize, null, null);
  1268	            hitSize = queryRes.optJSONObject("hits").optJSONObject("total").optInt("value");
  1269	        } catch (Exception ex) {
  1270	            System.out.println("getMatches() exception: " + ex);
  1271	            ex.printStackTrace();
  1272	        }
  1273	        JSONArray hits = OpenSearch.getHits(queryRes);
  1274	        for (int i = 0; i < hits.length(); i++) {
  1275	            JSONObject hit = hits.optJSONObject(i);
  1276	            if (hit == null) continue;
  1277	            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
  1278	            if (ann != null) {
  1279	                // OS Lucene knn with cosinesimil returns (1 + cos) / 2 in
  1280	                // [0, 1]. Convert to raw cosine in [-1, 1] so the score
  1281	                // matches the MiewID pipeline's native output.
  1282	                // (Empty-match-prospects C17.)
  1283	                double osScore = hit.optDouble("_score", 0.0d);
  1284	                ann.setOpensearchScore(openSearchScoreToCosine(osScore));
  1285	                anns.add(ann);
  1286	            }
  1287	        }
  1288	        System.out.println("getMatches() results: hitSize=" + hitSize + "; hits length=" +
  1289	            hits.length() + "; anns size=" + anns.size() + "; " +
  1290	            (System.currentTimeMillis() - startTime) + "ms");
  1291	        return anns;
  1292	    }
  1293	
  1294	    /*
  1295	        sorta weird to have this in here, but it is inherently linked with getMatchingSetXXX() above ...

 succeeded in 319ms:
   520	
   521	        JSONObject query = buildIdEligibilityQuery(targetIds);
   522	        long deadline = System.currentTimeMillis() + timeoutMs;
   523	        long sleepMs = 100;
   524	        while (true) {
   525	            int seen = queryCount(indexName, query);
   526	            if (seen >= targetIds.size()) return true;
   527	            long remaining = deadline - System.currentTimeMillis();
   528	            if (remaining <= 0) return false;
   529	            try {
   530	                Thread.sleep(Math.min(sleepMs, remaining));
   531	            } catch (InterruptedException ex) {
   532	                Thread.currentThread().interrupt();
   533	                return false;
   534	            }
   535	            sleepMs = Math.min(sleepMs * 2, 1000);
   536	        }
   537	    }
   538	
   539	    // Package-visible for testing. Returns the _count-shaped query body that
   540	    // filters on _id ∈ ids, using OpenSearch's idiomatic `ids` query.
   541	    static JSONObject buildIdEligibilityQuery(Set<String> ids) {
   542	        JSONArray idArr = new JSONArray();
   543	        for (String id : ids) idArr.put(id);
   544	        JSONObject query = new JSONObject();
   545	        query.put("query",
   546	            new JSONObject().put("ids",
   547	                new JSONObject().put("values", idArr)));
   548	        return query;
   549	    }
   550	
   551	    // ml-service migration v2 / empty-match-prospects design Track 2 C8.
   552	    //
   553	    // Stronger visibility predicate than waitForVisibility for the annotation
   554	    // index. A doc that exists by _id but is missing nested
   555	    // embeddings.method/methodVersion would pass _id-only and then knn-fail
   556	    // at match time. This helper polls a predicate that mirrors the matching
   557	    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
   558	    // AND acmId exists AND a nested embedding for this method/version is
   559	    // indexed. Scope is intentionally narrower than getMatchingSetQuery
   560	    // (no taxonomy/viewpoint/encounter/dead-animal filters) — this helper
   561	    // answers "doc has fresh embedding metadata", which is the visibility
   562	    // race the Track 2 batch gate cares about.
   563	    //
   564	    // method/methodVersion follow the strict-when-present convention of
   565	    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
   566	    // null/blank, the corresponding nested predicate is omitted.
   567	    //
   568	    // Like waitForVisibility: _refresh on entry, then exponential-backoff
   569	    // poll of _count until count >= |normalized ids| OR timeout. Empty
   570	    // wait set short-circuits to true.
   571	    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
   572	        String method, String methodVersion, long timeoutMs)
   573	    throws IOException {
   574	        if (ids == null || ids.isEmpty()) return true;
   575	
   576	        Set<String> targetIds = new LinkedHashSet<String>();
   577	        for (String id : ids) {
   578	            if (id != null) targetIds.add(id);
   579	        }
   580	        if (targetIds.isEmpty()) return true;
   581	
   582	        if (skipAutoIndexing()) {
   583	            System.out.println(
   584	                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
   585	                "/tmp/skipAutoIndexing set — every poll will return zero hits " +
   586	                "regardless of wait time.");
   587	        }
   588	
   589	        indexRefresh("annotation");
   590	
   591	        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
   592	            methodVersion);
   593	        long deadline = System.currentTimeMillis() + timeoutMs;
   594	        long sleepMs = 100;
   595	        while (true) {
   596	            int seen = queryCount("annotation", query);
   597	            if (seen >= targetIds.size()) return true;
   598	            long remaining = deadline - System.currentTimeMillis();
   599	            if (remaining <= 0) return false;
   600	            try {
   601	                Thread.sleep(Math.min(sleepMs, remaining));
   602	            } catch (InterruptedException ex) {
   603	                Thread.currentThread().interrupt();
   604	                return false;
   605	            }
   606	            sleepMs = Math.min(sleepMs * 2, 1000);
   607	        }
   608	    }
   609	
   610	    // Package-visible for testing. Returns the _count-shaped query body
   611	    // matching the annotation-matchable predicate documented on
   612	    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as
   613	    // buildIdEligibilityQuery for consistency with queryCount's
   614	    // expectations (no `size`, no `track_total_hits`).
   615	    static JSONObject buildAnnotationMatchableQuery(Set<String> ids,
   616	        String method, String methodVersion) {
   617	        JSONArray idArr = new JSONArray();
   618	        for (String id : ids) idArr.put(id);
   619	
   620	        JSONArray filterArr = new JSONArray();
   621	        filterArr.put(new JSONObject().put("ids",
   622	            new JSONObject().put("values", idArr)));
   623	        filterArr.put(new JSONObject().put("term",
   624	            new JSONObject().put("matchAgainst", true)));
   625	        filterArr.put(new JSONObject().put("exists",
   626	            new JSONObject().put("field", "acmId")));
   627	
   628	        // Nested embedding clause. Match Annotation.getMatchQuery at
   629	        // Annotation.java:1205-1209 exactly: omit a predicate only when
   630	        // the value is `null`. A non-null blank string would be a strict
   631	        // term on "" (matching no docs), preserving consistency with the
   632	        // matcher rather than silently broadening the wait predicate.
   633	        // Codex round-1 C8 review surfaced this — empty vs null asymmetry
   634	        // would let the gate green-light docs the matcher then rejects.
   635	        JSONArray nestedMust = new JSONArray();
   636	        if (method != null) {
   637	            nestedMust.put(new JSONObject().put("term",
   638	                new JSONObject().put("embeddings.method", method)));
   639	        }
   640	        if (methodVersion != null) {
   641	            nestedMust.put(new JSONObject().put("term",
   642	                new JSONObject().put("embeddings.methodVersion", methodVersion)));
   643	        }
   644	        JSONObject nestedQuery;
   645	        if (nestedMust.length() == 0) {
   646	            // Both null — wait only on the existence of any nested
   647	            // embedding entry. (Legacy api_endpoint-only configs that
   648	            // can't derive a method.)
   649	            nestedQuery = new JSONObject().put("match_all", new JSONObject());
   650	        } else {
   651	            nestedQuery = new JSONObject().put("bool",
   652	                new JSONObject().put("must", nestedMust));
   653	        }
   654	        filterArr.put(new JSONObject().put("nested",
   655	            new JSONObject().put("path", "embeddings").put("query", nestedQuery)));
   656	
   657	        JSONObject query = new JSONObject();
   658	        query.put("query",
   659	            new JSONObject().put("bool",
   660	                new JSONObject().put("filter", filterArr)));
   661	        return query;
   662	    }
   663	
   664	    // when you only care about how many this would return
   665	    public int queryCount(String indexName, final JSONObject query)
   666	    throws IOException {
   667	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   668	        Request searchRequest = new Request("POST", indexName + "/_count");
   669	        JSONObject cleanedQuery = new JSONObject(query.toString());
   670	        cleanedQuery.remove("_source"); // invalid for a _count query

 succeeded in 780ms:
src/main/java/org/ecocean/Annotation.java:1167:                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
src/main/java/org/ecocean/Annotation.java:1238:    public static double openSearchScoreToCosine(double osScore) {
src/main/java/org/ecocean/Annotation.java:1284:                ann.setOpensearchScore(openSearchScoreToCosine(osScore));
src/main/java/org/ecocean/Base.java:111:    public double opensearchScore = 0.0d;
src/main/java/org/ecocean/Base.java:113:    public void setOpensearchScore(double s) {
src/main/java/org/ecocean/Base.java:114:        opensearchScore = s;
src/main/java/org/ecocean/Base.java:117:    public double getOpensearchScore() {
src/main/java/org/ecocean/Base.java:118:        return opensearchScore;
src/main/java/org/ecocean/ia/MatchResult.java:215:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:10: * Tests for the {@link Annotation#openSearchScoreToCosine} helper
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:16:class AnnotationMiewIDScoreTest {
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:22:        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:27:        assertEquals(0.0, Annotation.openSearchScoreToCosine(0.5), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:32:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:39:        assertEquals(1.0,  Annotation.openSearchScoreToCosine(1.0),  EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:40:        assertEquals(0.72, Annotation.openSearchScoreToCosine(0.86), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:41:        assertEquals(0.56, Annotation.openSearchScoreToCosine(0.78), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:42:        assertEquals(0.52, Annotation.openSearchScoreToCosine(0.76), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:49:        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.5), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:50:        assertEquals(1.0, Annotation.openSearchScoreToCosine(100.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:56:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-0.5), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:57:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-100.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:63:        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:72:            double cos = Annotation.openSearchScoreToCosine(osScore);

web search: 
web search: OpenSearch k-NN score calculation cosinesimil Lucene engine (1 + cosine) / 2
web search: 
web search: https://docs.opensearch.org/latest/mappings/supported-field-types/knn-spaces/
web search: 
web search: 'nmslib' in https://docs.opensearch.org/latest/mappings/supported-field-types/knn-spaces/
web search: 
web search: https://docs.opensearch.org/3.1/vector-search/vector-search-techniques/approximate-knn/
web search: 
web search: https://docs.opensearch.org/3.1/field-types/supported-field-types/knn-spaces/
web search: 
web search: 'Distance calculation' in https://docs.opensearch.org/3.1/field-types/supported-field-types/knn-spaces/
codex
The OpenSearch 3.1 docs do back the score conversion for `cosinesimil`: `d = 1 - cos`, score `(2 - d) / 2`, which algebraically is `(1 + cos) / 2`. I’m doing one more pass for behavioral edge cases in query mutation and score consumers.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '180,245p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Base.java | sed -n '100,122p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getMatchQuery\\(|getMatches\\(|queryPit\\(|nested.*embeddings|OpenSearchAnnotationMatchableQueryTest|AnnotationTest|MatchResultTest\" src/test/java/org/ecocean src/main/java/org/ecocean | head -n 300" in /mnt/c/Wildbook-clean2
 succeeded in 247ms:
     1	package org.ecocean;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertNotNull;
     5	import static org.junit.jupiter.api.Assertions.assertTrue;
     6	
     7	import org.junit.jupiter.api.Test;
     8	
     9	/**
    10	 * Tests for the {@link Annotation#openSearchScoreToCosine} helper
    11	 * introduced in C17 to translate OpenSearch Lucene knn cosinesimil
    12	 * scores (range [0, 1], formula {@code (1 + cos) / 2}) back to raw
    13	 * cosine similarity in [-1, 1] — the native MiewID pipeline output.
    14	 * (Empty-match-prospects design C17.)
    15	 */
    16	class AnnotationMiewIDScoreTest {
    17	
    18	    private static final double EPS = 1e-9;
    19	
    20	    @Test void identicalVectors_scoresExactlyOne() {
    21	        // cos(a, a) = 1 → OS score = (1 + 1) / 2 = 1.0 → raw = 1.0
    22	        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.0), EPS);
    23	    }
    24	
    25	    @Test void perpendicularVectors_scoresExactlyZero() {
    26	        // cos(a, b) = 0 → OS score = 0.5 → raw = 0.0
    27	        assertEquals(0.0, Annotation.openSearchScoreToCosine(0.5), EPS);
    28	    }
    29	
    30	    @Test void oppositeVectors_scoresExactlyNegativeOne() {
    31	        // cos(a, -a) = -1 → OS score = 0 → raw = -1.0
    32	        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
    33	    }
    34	
    35	    @Test void midRangeMatchesObservedDeploymentValues() {
    36	        // The live test showed OS scores 1.0, 0.86, 0.78, 0.76 (after
    37	        // the +2.0 offset is removed). Convert each to raw cosine and
    38	        // verify the transform is correct.
    39	        assertEquals(1.0,  Annotation.openSearchScoreToCosine(1.0),  EPS);
    40	        assertEquals(0.72, Annotation.openSearchScoreToCosine(0.86), EPS);
    41	        assertEquals(0.56, Annotation.openSearchScoreToCosine(0.78), EPS);
    42	        assertEquals(0.52, Annotation.openSearchScoreToCosine(0.76), EPS);
    43	    }
    44	
    45	    @Test void clampsAboveOne_defensiveAgainstScoringDrift() {
    46	        // If OS returns >1.0 for any reason (different engine, scoring
    47	        // bug, etc.) the transform should clamp to 1.0 rather than
    48	        // produce raw cosines outside the valid range.
    49	        assertEquals(1.0, Annotation.openSearchScoreToCosine(1.5), EPS);
    50	        assertEquals(1.0, Annotation.openSearchScoreToCosine(100.0), EPS);
    51	    }
    52	
    53	    @Test void clampsBelowMinusOne_defensiveAgainstScoringDrift() {
    54	        // OS shouldn't return negative scores from cosinesimil but
    55	        // defense-in-depth: clamp to -1.0 rather than overflow.
    56	        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-0.5), EPS);
    57	        assertEquals(-1.0, Annotation.openSearchScoreToCosine(-100.0), EPS);
    58	    }
    59	
    60	    @Test void zeroScoreStaysAtMinusOne_notNaN() {
    61	        // Edge case: OS returns exactly 0.0 (perpendicular or
    62	        // missing-score-default-to-0). Transform: 2*0 - 1 = -1.
    63	        assertEquals(-1.0, Annotation.openSearchScoreToCosine(0.0), EPS);
    64	    }
    65	
    66	    @Test void invertibleAcrossKnownPoints() {
    67	        // For OS scores 0.0 through 1.0 in 0.1 steps, the raw cosine
    68	        // should be monotonically increasing and span [-1, 1].
    69	        double prev = -2.0;
    70	        for (int i = 0; i <= 10; i++) {
    71	            double osScore = i / 10.0;
    72	            double cos = Annotation.openSearchScoreToCosine(osScore);
    73	            assertTrue(cos > prev, "expected monotonic increase at i=" + i + " (" + cos + ")");
    74	            assertTrue(cos >= -1.0 && cos <= 1.0,
    75	                "expected cos in [-1, 1] at i=" + i + " (" + cos + ")");
    76	            prev = cos;
    77	        }
    78	    }
    79	}

 succeeded in 254ms:
   180	            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
   181	                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
   182	            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
   183	            num++;
   184	            if (num >= MAXIMUM_PROSPECTS_STORED) {
   185	                System.out.println("[DEBUG] hit max (" + MAXIMUM_PROSPECTS_STORED +
   186	                    ") number storable prospects on " + this);
   187	                break;
   188	            }
   189	        }
   190	        return num;
   191	    }
   192	
   193	    // we just have a list of annots which matched (e.g. via vectors in opensearch)
   194	    // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
   195	    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
   196	    //
   197	    // Empty-match-prospects design Track 2 C13: prospects are created with
   198	    // {@code asset=null}. The PairX inspection image is populated later by
   199	    // {@link MatchInspectionPairxEnricher} in a Phase A/B/C flow so the
   200	    // outer Shepherd is never held across the PairX HTTP call. Holding a
   201	    // Shepherd across that ~10-30s POST would risk connection-pool
   202	    // exhaustion under load (Codex C12 review High).
   203	    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
   204	        Shepherd myShepherd)
   205	    throws IOException {
   206	        if (Util.collectionIsEmptyOrNull(annots)) return 0;
   207	        if (this.prospects == null)
   208	            this.prospects = new HashSet<MatchResultProspect>();
   209	        if (scoreByIndividual) {
   210	            // the scores for these are calculated weighted by indiv count
   211	            _populateProspectsByIndividual(annots, myShepherd);
   212	        } else {
   213	            // these scores are direct from opensearch
   214	            for (Annotation ann : annots) {
   215	                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
   216	                    null));
   217	            }
   218	        }
   219	        this.numberProspects = this.prospects.size();
   220	        return this.numberProspects;
   221	    }
   222	
   223	    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
   224	        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
   225	            List<Annotation> >();
   226	
   227	        for (Annotation ann : annots) {
   228	            Encounter enc = ann.findEncounter(myShepherd);
   229	            // i think we just ignore if no enc/indiv
   230	            if (enc == null) continue;
   231	            MarkedIndividual indiv = enc.getIndividual();
   232	            if (indiv == null) continue;
   233	            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
   234	            tally.get(indiv).add(ann);
   235	        }
   236	        if (tally.size() < 1) return; // no individuals i guess?
   237	
   238	        // this sorts by most annots (per indiv) highest to lowest
   239	        List<Map.Entry<MarkedIndividual,
   240	            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
   241	        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
   242	        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
   243	            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
   244	            Map.Entry<MarkedIndividual, List<Annotation> > two) {
   245	                // we reverse order here so we get largest first

 succeeded in 262ms:
   100	    public boolean skipAutoIndexing = false;
   101	
   102	    public void setSkipAutoIndexing(boolean skip) {
   103	        skipAutoIndexing = skip;
   104	    }
   105	
   106	    public boolean getSkipAutoIndexing() {
   107	        return skipAutoIndexing;
   108	    }
   109	
   110	    // this is not persisted, but is a place to keep score when from search query results
   111	    public double opensearchScore = 0.0d;
   112	
   113	    public void setOpensearchScore(double s) {
   114	        opensearchScore = s;
   115	    }
   116	
   117	    public double getOpensearchScore() {
   118	        return opensearchScore;
   119	    }
   120	
   121	    public abstract String opensearchIndexName();
   122	

 succeeded in 889ms:
src/test/java/org/ecocean/MatchResultTest.java:32:class MatchResultTest {
src/test/java/org/ecocean/AnnotationTest.java:9:class AnnotationTest {
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:23:class OpenSearchAnnotationMatchableQueryTest {
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:56:            "missing nested clause (embeddings): " + q);
src/main/java/org/ecocean/Base.java:251:        JSONObject res = opensearch.queryPit(indexname, query, numFrom, pageSize, sort, sortOrder);
src/main/java/org/ecocean/Annotation.java:1155:            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
src/main/java/org/ecocean/Annotation.java:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
src/main/java/org/ecocean/Annotation.java:1198:            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
src/main/java/org/ecocean/Annotation.java:1247:    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
src/main/java/org/ecocean/Annotation.java:1249:        return getMatches(myShepherd,
src/main/java/org/ecocean/Annotation.java:1250:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
src/main/java/org/ecocean/Annotation.java:1254:    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
src/main/java/org/ecocean/Annotation.java:1267:            queryRes = os.queryPit("annotation", matchQuery, 0, pageSize, null, null);
src/main/java/org/ecocean/Annotation.java:1270:            System.out.println("getMatches() exception: " + ex);
src/main/java/org/ecocean/Annotation.java:1288:        System.out.println("getMatches() results: hitSize=" + hitSize + "; hits length=" +
src/main/java/org/ecocean/EncounterQueryProcessor.java:88:                    JSONObject queryRes = os.queryPit(indexName, sanitized, numFrom, pageSize, sort,
src/main/java/org/ecocean/EncounterQueryProcessor.java:1609:                JSONObject queryRes = os.queryPit(indexName, sanitized, numFrom, pageSize, sort,
src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:370:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
src/main/java/org/ecocean/api/SearchApi.java:91:                        JSONObject queryRes = os.queryPit(indexName, query, numFrom, pageSize, sort,
src/main/java/org/ecocean/OpenSearch.java:377:    public JSONObject queryPit(String indexName, final JSONObject query, int numFrom, int pageSize,
src/main/java/org/ecocean/OpenSearch.java:402:            System.out.println("queryPit() using pitId=" + pitId + " failed[" + pitRetry +
src/main/java/org/ecocean/OpenSearch.java:407:                throw new IOException("queryPit() failed to POST query");
src/main/java/org/ecocean/OpenSearch.java:411:            return queryPit(indexName, query, numFrom, pageSize, sort, sortOrder);
src/main/java/org/ecocean/MetricsBot.java:441:            JSONObject result = os.queryPit(indexName, query, 0, 0, null, null);

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java | sed -n '1,210p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,390p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/AnnotationTest.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
 succeeded in 254ms:
     1	package org.ecocean;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertFalse;
     5	import static org.junit.jupiter.api.Assertions.assertNotNull;
     6	import static org.junit.jupiter.api.Assertions.assertTrue;
     7	
     8	import java.util.LinkedHashSet;
     9	import java.util.Set;
    10	
    11	import org.json.JSONArray;
    12	import org.json.JSONObject;
    13	import org.junit.jupiter.api.Test;
    14	
    15	/**
    16	 * Shape-of-query tests for {@link OpenSearch#buildAnnotationMatchableQuery}.
    17	 * Full poll-and-wait behavior of {@link OpenSearch#waitForAnnotationMatchableIds}
    18	 * is exercised by integration in dev deployments — the matchable-predicate
    19	 * JSON shape is the mechanical part that benefits from unit-testing.
    20	 *
    21	 * <p>(Empty-match-prospects design Track 2 C8.)</p>
    22	 */
    23	class OpenSearchAnnotationMatchableQueryTest {
    24	
    25	    private static Set<String> ids(String... s) {
    26	        LinkedHashSet<String> set = new LinkedHashSet<String>();
    27	        for (String x : s) set.add(x);
    28	        return set;
    29	    }
    30	
    31	    private static JSONArray filterArr(JSONObject q) {
    32	        return q.getJSONObject("query").getJSONObject("bool").getJSONArray("filter");
    33	    }
    34	
    35	    private static JSONObject findFilterClause(JSONArray filters, String kind) {
    36	        for (int i = 0; i < filters.length(); i++) {
    37	            JSONObject jo = filters.optJSONObject(i);
    38	            if (jo != null && jo.has(kind)) return jo;
    39	        }
    40	        return null;
    41	    }
    42	
    43	    @Test void includesIdsTermMatchAgainstAcmIdExistsAndNested() {
    44	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
    45	            ids("ann-1", "ann-2"), "miewid-msv4.1", "4.1");
    46	        JSONArray filters = filterArr(q);
    47	        // Order doesn't matter for OpenSearch, but each named clause
    48	        // must be present in the filter array.
    49	        assertNotNull(findFilterClause(filters, "ids"),
    50	            "missing ids clause: " + q);
    51	        assertNotNull(findFilterClause(filters, "term"),
    52	            "missing term clause (matchAgainst): " + q);
    53	        assertNotNull(findFilterClause(filters, "exists"),
    54	            "missing exists clause (acmId): " + q);
    55	        assertNotNull(findFilterClause(filters, "nested"),
    56	            "missing nested clause (embeddings): " + q);
    57	    }
    58	
    59	    @Test void idsClauseListsExactValues() {
    60	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
    61	            ids("ann-1", "ann-2", "ann-3"), "miewid-msv4.1", "4.1");
    62	        JSONArray values = findFilterClause(filterArr(q), "ids")
    63	            .getJSONObject("ids").getJSONArray("values");
    64	        assertEquals(3, values.length());
    65	        assertEquals("ann-1", values.getString(0));
    66	        assertEquals("ann-3", values.getString(2));
    67	    }
    68	
    69	    @Test void termClauseAssertsMatchAgainstTrue() {
    70	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
    71	            ids("ann-1"), "miewid-msv4.1", "4.1");
    72	        JSONObject term = findFilterClause(filterArr(q), "term")
    73	            .getJSONObject("term");
    74	        assertTrue(term.has("matchAgainst"),
    75	            "term clause should target matchAgainst: " + term);
    76	        assertEquals(true, term.getBoolean("matchAgainst"));
    77	    }
    78	
    79	    @Test void existsClauseTargetsAcmId() {
    80	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
    81	            ids("ann-1"), "miewid-msv4.1", "4.1");
    82	        JSONObject exists = findFilterClause(filterArr(q), "exists")
    83	            .getJSONObject("exists");
    84	        assertEquals("acmId", exists.getString("field"));
    85	    }
    86	
    87	    @Test void nestedClauseTargetsEmbeddingsPath_withMethodAndVersion() {
    88	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
    89	            ids("ann-1"), "miewid-msv4.1", "4.1");
    90	        JSONObject nested = findFilterClause(filterArr(q), "nested")
    91	            .getJSONObject("nested");
    92	        assertEquals("embeddings", nested.getString("path"));
    93	        JSONArray must = nested.getJSONObject("query")
    94	            .getJSONObject("bool").getJSONArray("must");
    95	        assertEquals(2, must.length());
    96	        // Method term
    97	        JSONObject methodTerm = null;
    98	        JSONObject versionTerm = null;
    99	        for (int i = 0; i < must.length(); i++) {
   100	            JSONObject term = must.getJSONObject(i).getJSONObject("term");
   101	            if (term.has("embeddings.method")) methodTerm = term;
   102	            if (term.has("embeddings.methodVersion")) versionTerm = term;
   103	        }
   104	        assertNotNull(methodTerm);
   105	        assertNotNull(versionTerm);
   106	        assertEquals("miewid-msv4.1", methodTerm.getString("embeddings.method"));
   107	        assertEquals("4.1", versionTerm.getString("embeddings.methodVersion"));
   108	    }
   109	
   110	    @Test void nestedClauseOmitsVersion_whenMethodVersionNull() {
   111	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   112	            ids("ann-1"), "miewid-msv4.1", null);
   113	        JSONObject nested = findFilterClause(filterArr(q), "nested")
   114	            .getJSONObject("nested");
   115	        JSONArray must = nested.getJSONObject("query")
   116	            .getJSONObject("bool").getJSONArray("must");
   117	        // Only method term should be present.
   118	        assertEquals(1, must.length());
   119	        JSONObject term = must.getJSONObject(0).getJSONObject("term");
   120	        assertTrue(term.has("embeddings.method"));
   121	        assertFalse(term.has("embeddings.methodVersion"));
   122	    }
   123	
   124	    @Test void nestedClauseOmitsMethod_whenMethodNull() {
   125	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   126	            ids("ann-1"), null, "4.1");
   127	        JSONObject nested = findFilterClause(filterArr(q), "nested")
   128	            .getJSONObject("nested");
   129	        JSONArray must = nested.getJSONObject("query")
   130	            .getJSONObject("bool").getJSONArray("must");
   131	        assertEquals(1, must.length());
   132	        JSONObject term = must.getJSONObject(0).getJSONObject("term");
   133	        assertTrue(term.has("embeddings.methodVersion"));
   134	        assertFalse(term.has("embeddings.method"));
   135	    }
   136	
   137	    @Test void nestedClauseFallsBackToMatchAll_whenBothNull() {
   138	        // Legacy api_endpoint-only config: neither method nor version is
   139	        // available, but the gate still wants to confirm SOME nested
   140	        // embedding exists. Both must be `null` (not blank string) — the
   141	        // helper matches Annotation.getMatchQuery's strict-when-non-null
   142	        // semantics.
   143	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   144	            ids("ann-1"), null, null);
   145	        JSONObject nested = findFilterClause(filterArr(q), "nested")
   146	            .getJSONObject("nested");
   147	        JSONObject inner = nested.getJSONObject("query");
   148	        assertTrue(inner.has("match_all"),
   149	            "expected match_all when method+version both null: " + nested);
   150	    }
   151	
   152	    @Test void nestedClauseDoesStrictMatchOnBlankString_notOmit() {
   153	        // Codex C8 review Major: previous draft treated blank strings the
   154	        // same as null, but Annotation.getMatchQuery only omits on null.
   155	        // A non-null blank means a strict term match on "" (matches no
   156	        // docs), preserving consistency with the matcher.
   157	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   158	            ids("ann-1"), "miewid-msv4.1", "");
   159	        JSONObject nested = findFilterClause(filterArr(q), "nested")
   160	            .getJSONObject("nested");
   161	        JSONArray must = nested.getJSONObject("query")
   162	            .getJSONObject("bool").getJSONArray("must");
   163	        // Both terms present — version term is the blank-string strict.
   164	        assertEquals(2, must.length());
   165	        JSONObject versionTerm = null;
   166	        for (int i = 0; i < must.length(); i++) {
   167	            JSONObject term = must.getJSONObject(i).getJSONObject("term");
   168	            if (term.has("embeddings.methodVersion")) versionTerm = term;
   169	        }
   170	        assertNotNull(versionTerm);
   171	        assertEquals("", versionTerm.getString("embeddings.methodVersion"));
   172	    }
   173	
   174	    @Test void noTopLevelSizeOrTrackTotalHits() {
   175	        // queryCount() strips only `_source`; size and track_total_hits in
   176	        // the body would be invalid for _count. Codex round-3 Medium.
   177	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   178	            ids("ann-1"), "miewid-msv4.1", "4.1");
   179	        assertFalse(q.has("size"),
   180	            "size must not be present in _count body: " + q);
   181	        assertFalse(q.has("track_total_hits"),
   182	            "track_total_hits must not be present in _count body: " + q);
   183	    }
   184	
   185	    @Test void emptyIdSetProducesEmptyIdsValues() {
   186	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   187	            ids(), "miewid-msv4.1", "4.1");
   188	        JSONArray values = findFilterClause(filterArr(q), "ids")
   189	            .getJSONObject("ids").getJSONArray("values");
   190	        assertEquals(0, values.length());
   191	    }
   192	}

 succeeded in 254ms:
   250	                // certain cases we store in embData, so they *will not be retried later*
   251	                // TODO decide actual cases!!
   252	                embData.put(ann.getId(), ex.toString());
   253	                System.out.println("catchUpEmbeddings: exception " + ann + " -> " + ex);
   254	            }
   255	        }
   256	        System.out.println("catchUpEmbeddings: finished with lastId=" + lastId);
   257	        embData.put("_runCount", ct);
   258	        embData.put("_runOk", ok);
   259	        embData.put("_runIds", runIds);
   260	        embData.put("_lastId", lastId);
   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
   262	        return embData;
   263	    }
   264	
   265	    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
   266	        // Migration plan v2 §commit #3: gate accepts the new _id_conf
   267	        // contract (entries with `method`/`version`/`pipeline_root` and no
   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
   269	        if (iaConfig == null) return false;
   270	        boolean isVectorConfig = Util.stringExists(iaConfig.optString("method", null))
   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
   272	        if (!isVectorConfig) return false;
   273	        // from here on out we should return true since this is a vector match, even when something goes wrong
   274	        // and we should also set status on the task (and subtasks)
   275	        if (task == null) return true; // cant really set status on this :(
   276	        if (task.numberAnnotations() < 1) {
   277	            task.setStatus("completed");
   278	            task.setCompletionDateInMilliseconds();
   279	            return true;
   280	        }
   281	        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
   282	            task.numberAnnotations() + " annots) has embedding match: " + iaConfig);
   283	        // Track per-subtask outcomes so the parent task's terminal state
   284	        // reflects reality (plan v2 §commit #3: previous code unconditionally
   285	        // marked the parent "completed" even if every subtask failed).
   286	        int subtasksOk = 0;
   287	        int subtasksFailed = 0;
   288	        for (Annotation ann : task.getObjectAnnotations()) {
   289	            // every ann gets a subTask
   290	            Task subTask = new Task(task);
   291	            subTask.addObject(ann);
   292	            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
   293	            // TODO not sure if this is wise, or it would be better to just fail outright and let some background process do this
   294	            if (ann.numberEmbeddings() < 1) {
   295	                System.out.println(
   296	                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
   297	                    ann);
   298	                try {
   299	                    ann.extractEmbeddings(myShepherd);
   300	                } catch (IAException ex) {
   301	                    System.out.println(
   302	                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
   303	                        " due to: " + ex);
   304	                }
   305	                // if none now, we just fail and continue onto next annot
   306	                if (ann.numberEmbeddings() < 1) {
   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
   308	                        " due to no suitable embeddings for " + iaConfig);
   309	                    subTask.setStatus("error");
   310	                    subTask.setStatusDetailsAddError("REQUIRED",
   311	                        "no suitable embeddings for getMatches()");
   312	                    subTask.setCompletionDateInMilliseconds();
   313	                    myShepherd.getPM().makePersistent(subTask);
   314	                    subtasksFailed++;
   315	                    continue;
   316	                }
   317	            }
   318	            // Build matchingSetQuery for the candidate count.
   319	            boolean useClauses = false; // TODO how??
   320	            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
   321	                useClauses);
   322	            // getMatchingSetQuery can return null (e.g. encounter missing,
   323	            // taxonomy filtered out). Skip this subtask cleanly rather than
   324	            // NPE'ing on the queryCount/getMatchQuery below.
   325	            if (matchingSetQuery == null) {
   326	                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
   327	                subTask.setStatus("error");
   328	                subTask.setStatusDetailsAddError("REQUIRED", "null matchingSetQuery");
   329	                subTask.setCompletionDateInMilliseconds();
   330	                myShepherd.getPM().makePersistent(subTask);
   331	                subtasksFailed++;
   332	                continue;
   333	            }
   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
   335	            // matchingSetQuery in place (Annotation.java:1203). The previous
   336	            // order returned the knn result count instead of the candidate
   337	            // count. Defense in depth: also pass a deep clone of
   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
   339	            // never silently changes shape if a future caller relies on it.
   340	            OpenSearch os = new OpenSearch();
   341	            int numberCandidates = -2;
   342	            try {
   343	                numberCandidates = os.queryCount("annotation", matchingSetQuery);
   344	            } catch (IOException ex) {
   345	                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
   346	            }
   347	            // Read method/version directly from the new _id_conf contract;
   348	            // fall back to splitting model_id for legacy configs.
   349	            String method = iaConfig.optString("method", null);
   350	            String version = iaConfig.optString("version", null);
   351	            if (!Util.stringExists(method)) {
   352	                String[] mv = MLService.getMethodValues(iaConfig);
   353	                method = mv[0];
   354	                version = mv[1];
   355	            }
   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
   357	                new JSONObject(matchingSetQuery.toString()));
   358	            // i think this will never happen now, due to on-the-fly fix above; but leaving to be safe
   359	            if (matchQuery == null) {
   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
   361	                    " due to no suitable embeddings for " + iaConfig);
   362	                subTask.setStatus("error");
   363	                subTask.setStatusDetailsAddError("REQUIRED",
   364	                    "no suitable embeddings for getMatches()");
   365	                subTask.setCompletionDateInMilliseconds();
   366	                myShepherd.getPM().makePersistent(subTask);
   367	                subtasksFailed++;
   368	                continue; // on to next ann
   369	            }
   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
   371	            System.out.println("findMatchProspects() on " + ann + " found " +
   372	                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
   373	                " candidates) for subTask " + subTask.getId());
   374	            boolean mrOk = true;
   375	            try {
   376	                // we build this even if empty, cuz that means we got results; just not nice ones
   377	                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
   378	                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
   379	                myShepherd.getPM().makePersistent(mr);
   380	            } catch (IOException ex) {
   381	                System.out.println("findMatchProspects() MatchResult creation failed on " +
   382	                    subTask + ": " + ex);
   383	                ex.printStackTrace();
   384	                mrOk = false;
   385	            }
   386	            if (mrOk) {
   387	                subTask.setStatus("completed");
   388	                subtasksOk++;
   389	            } else {
   390	                subTask.setStatus("error");

 succeeded in 315ms:
     1	package org.ecocean;
     2	
     3	import org.ecocean.Annotation;
     4	import org.ecocean.media.*;
     5	import org.json.JSONObject;
     6	import org.junit.jupiter.api.Test;
     7	import static org.junit.Assert.*;
     8	
     9	class AnnotationTest {
    10	/*
    11	    to create a (real) MediaAsset or a Feature we need to have a Shepherd!
    12	    TODO: extend to this with mockito/Shepherd
    13	    https://semaphoreci.com/community/tutorials/stubbing-and-mocking-with-mockito-2-and-junit
    14	 */
    15	    @Test void createAnnotation() {
    16	        AssetStore store = null; // see note above
    17	        MediaAsset ma = new MediaAsset(store, null);
    18	        Annotation ann = new Annotation("species", ma);
    19	
    20	        assertNotNull(ann);
    21	        assertTrue(ann.isTrivial());
    22	        assertEquals(ann.getMediaAsset(), ma);
    23	    }
    24	
    25	    @Test void comparisons() {
    26	        Annotation a1 = new Annotation();
    27	        Annotation a2 = new Annotation();
    28	        a1.setIAClass("foo");
    29	        a2.setIAClass("bar");
    30	        assertFalse(a1.equalsIAClass(a2));
    31	        a2.setIAClass("foo");
    32	        assertTrue(a1.equalsIAClass(a2));
    33	
    34	        AssetStore store = null;
    35	        MediaAsset ma = new MediaAsset(store, null);
    36	
    37	        FeatureType ftype = new FeatureType("org.ecocean.boundingBox");
    38	        JSONObject p1 = new JSONObject();
    39	        p1.put("x", 1);
    40	        p1.put("y", 2);
    41	        p1.put("width", 3);
    42	        p1.put("height", 4);
    43	        p1.put("theta", 5);
    44	        Feature ft1 = new Feature(ftype, p1);
    45	        JSONObject p2 = new JSONObject(p1.toString());
    46	        Feature ft2 = new Feature(ftype, p2);
    47	        ma.addFeature(ft1);
    48	        ma.addFeature(ft2);
    49	
    50	        Annotation a3 = new Annotation("species", ft1);
    51	        Annotation a4 = new Annotation("species", ft2);
    52	        assertTrue(a3.equalsBbox(a4));
    53	        assertTrue(a3.equalsTheta(a4));
    54	        assertTrue(a3.equalsShape(a4));
    55	
    56	        p2.put("height", 100);
    57	        ft2.setParameters(p2);
    58	        assertFalse(a3.equalsShape(a4));
    59	
    60	        assertTrue(a3.equalsViewpoint(a4));
    61	        a3.setViewpoint("foo");
    62	        assertFalse(a3.equalsViewpoint(a4));
    63	    }
    64	
    65	    // ml-service migration v2 (commit #4): idempotency + WBIA registration fields
    66	
    67	    @Test void mlServiceFieldsDefaultToNull() {
    68	        Annotation ann = new Annotation();
    69	        assertNull(ann.getPredictModelId());
    70	        assertNull(ann.getBboxKey());
    71	        assertNull(ann.getThetaKey());
    72	        assertNull(ann.getWbiaRegistered());
    73	        assertEquals(0, ann.getWbiaRegisterAttempts());
    74	        assertFalse(ann.isWbiaRegistered());
    75	    }
    76	
    77	    @Test void mlServiceFieldSettersRoundTrip() {
    78	        Annotation ann = new Annotation();
    79	        ann.setPredictModelId("msv3");
    80	        ann.setBboxKey("10:20:30:40");
    81	        ann.setThetaKey("0.0000");
    82	        assertEquals("msv3", ann.getPredictModelId());
    83	        assertEquals("10:20:30:40", ann.getBboxKey());
    84	        assertEquals("0.0000", ann.getThetaKey());
    85	    }
    86	
    87	    @Test void isWbiaRegisteredOnlyTrueWhenExplicitlyTrue() {
    88	        Annotation ann = new Annotation();
    89	        assertFalse(ann.isWbiaRegistered());                  // null
    90	        ann.setWbiaRegistered(Boolean.FALSE);
    91	        assertFalse(ann.isWbiaRegistered());                  // pending
    92	        ann.setWbiaRegistered(Boolean.TRUE);
    93	        assertTrue(ann.isWbiaRegistered());                   // acknowledged
    94	        ann.setWbiaRegistered(null);
    95	        assertFalse(ann.isWbiaRegistered());                  // back to null
    96	    }
    97	
    98	    @Test void incrementWbiaRegisterAttempts() {
    99	        Annotation ann = new Annotation();
   100	        assertEquals(0, ann.getWbiaRegisterAttempts());
   101	        ann.incrementWbiaRegisterAttempts();
   102	        assertEquals(1, ann.getWbiaRegisterAttempts());
   103	        ann.incrementWbiaRegisterAttempts();
   104	        ann.incrementWbiaRegisterAttempts();
   105	        assertEquals(3, ann.getWbiaRegisterAttempts());
   106	    }
   107	
   108	    // Codex review caveat: setVersion() uses System.currentTimeMillis(), so
   109	    // two setter calls in the same millisecond produce the same version.
   110	    // Sleep between to make the test reliable.
   111	    @Test void mlServiceSettersBumpVersion() throws InterruptedException {
   112	        Annotation ann = new Annotation();
   113	        long v0 = ann.getVersion();
   114	        Thread.sleep(2);
   115	        ann.setPredictModelId("msv3");
   116	        long v1 = ann.getVersion();
   117	        assertTrue("setPredictModelId should bump version", v1 > v0);
   118	        Thread.sleep(2);
   119	        ann.setWbiaRegistered(Boolean.TRUE);
   120	        long v2 = ann.getVersion();
   121	        assertTrue("setWbiaRegistered should bump version", v2 > v1);
   122	        Thread.sleep(2);
   123	        ann.incrementWbiaRegisterAttempts();
   124	        long v3 = ann.getVersion();
   125	        assertTrue("incrementWbiaRegisterAttempts should bump version", v3 > v2);
   126	    }
   127	}

web search: 
web search: OpenSearch nested k-NN query score_mode max nested field search k-NN
web search: 
web search: https://docs.opensearch.org/3.1/vector-search/specialized-operations/nested-search-knn/
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1010,1148p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"put\\(\\\"must\\\"|put\\(\\\"filter\\\"|getMatchingSetQuery|matchingSetQuery\" src/main/java/org/ecocean/Annotation.java src/test/java/org/ecocean -C 2" in /mnt/c/Wildbook-clean2
 succeeded in 220ms:
  1010	        arg.put("field", "acmId");
  1011	        wrapper = new JSONObject();
  1012	        wrapper.put("exists", arg);
  1013	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1014	
  1015	        // exclude our encounter
  1016	        arg = new JSONObject();
  1017	        arg.put("encounterId", enc.getId());
  1018	        wrapper = new JSONObject();
  1019	        wrapper.put("match", arg);
  1020	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(wrapper);
  1021	
  1022	        // skip dead animals
  1023	        Long dateMS = enc.getDateInMillisecondsFallback();
  1024	        if (dateMS != null) {
  1025	            wrapper = new JSONObject(
  1026	                "{\"range\": {\"encounterIndividualTimeOfDeath\": { \"lte\": " + dateMS + " } } }");
  1027	            query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(
  1028	                wrapper);
  1029	        }
  1030	        // now process taskParams
  1031	        if (taskParams != null) {
  1032	            String userId = taskParams.optString("userId", null);
  1033	            JSONObject filt = taskParams.optJSONObject("matchingSetFilter");
  1034	            if (filt != null) {
  1035	                // locationId=FOO and locationIds=[FOO,BAR]
  1036	                boolean useNullLocation = false;
  1037	                List<String> rawLocationIds = new ArrayList<String>();
  1038	                String tmp = Util.basicSanitize(filt.optString("locationId", null));
  1039	                if (Util.stringExists(tmp)) rawLocationIds.add(tmp);
  1040	                JSONArray larr = filt.optJSONArray("locationIds");
  1041	                if (larr != null) {
  1042	                    for (int i = 0; i < larr.length(); i++) {
  1043	                        tmp = Util.basicSanitize(larr.optString(i));
  1044	                        if ("__NULL__".equals(tmp)) {
  1045	                            useNullLocation = true;
  1046	                        } else if (Util.stringExists(tmp) && !rawLocationIds.contains(tmp)) {
  1047	                            rawLocationIds.add(tmp);
  1048	                        }
  1049	                    }
  1050	                }
  1051	                List<String> expandedLocationIds = LocationID.expandIDs(rawLocationIds);
  1052	                if (expandedLocationIds.size() > 0) {
  1053	                    arg = new JSONObject();
  1054	                    arg.put("encounterLocationId", new JSONArray(expandedLocationIds));
  1055	                    wrapper = new JSONObject();
  1056	                    wrapper.put("terms", arg);
  1057	                    if (useNullLocation) {
  1058	                        JSONArray should = new JSONArray(
  1059	                            "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"encounterLocationId\"}}}}]");
  1060	                        should.put(wrapper);
  1061	                        JSONObject bool = new JSONObject("{\"bool\": {}}");
  1062	                        bool.getJSONObject("bool").put("should", should);
  1063	                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1064	                            "filter").put(bool);
  1065	                    } else {
  1066	                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1067	                            "filter").put(wrapper);
  1068	                    }
  1069	                }
  1070	                // owner ... which requires we have userId in the taskParams
  1071	                JSONArray owner = filt.optJSONArray("owner");
  1072	                JSONArray uids = new JSONArray();
  1073	                if ((owner != null) && (userId != null)) {
  1074	                    for (int i = 0; i < owner.length(); i++) {
  1075	                        String opt = owner.optString(i, null);
  1076	                        if (!Util.stringExists(opt)) continue;
  1077	                        if (opt.equals("me")) {
  1078	                            uids.put(userId);
  1079	                        } else {
  1080	                            uids.put(opt);
  1081	                        }
  1082	                    }
  1083	                }
  1084	                if (uids.length() > 0) {
  1085	                    arg = new JSONObject();
  1086	                    arg.put("encounterUserUuid", uids);
  1087	                    wrapper = new JSONObject();
  1088	                    wrapper.put("terms", arg);
  1089	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
  1090	                        wrapper);
  1091	                }
  1092	                // projectId
  1093	                String projectId = filt.optString("projectId", null);
  1094	                if (Util.stringExists(projectId)) {
  1095	                    arg = new JSONObject();
  1096	                    arg.put("encounterProjectIds", projectId);
  1097	                    wrapper = new JSONObject();
  1098	                    wrapper.put("match", arg);
  1099	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
  1100	                        wrapper);
  1101	                }
  1102	            }
  1103	        }
  1104	        /* saving this for possible future passing raw queries
  1105	           JSONArray arr = additionalQuery.optJSONArray("filter");
  1106	           if (arr != null) {
  1107	            for (int i = 0; i < arr.length(); i++) {
  1108	                JSONObject clause = arr.optJSONObject(i);
  1109	                if (clause != null)
  1110	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1111	                        "filter").put(clause);
  1112	            }
  1113	           }
  1114	           arr = additionalQuery.optJSONArray("must_not");
  1115	           if (arr != null) {
  1116	            for (int i = 0; i < arr.length(); i++) {
  1117	                JSONObject clause = arr.optJSONObject(i);
  1118	                if (clause != null)
  1119	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1120	                        "must_not").put(clause);
  1121	            }
  1122	           }
  1123	         */
  1124	
  1125	        // this exludes the very noisy embeddings from opensearch results since we dont need it
  1126	        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
  1127	        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
  1128	        return query;
  1129	    }
  1130	
  1131	    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd) {
  1132	        return getMatchingSet(myShepherd, null, true);
  1133	    }
  1134	
  1135	    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams) {
  1136	        return getMatchingSet(myShepherd, taskParams, true);
  1137	    }
  1138	
  1139	    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams,
  1140	        boolean useClauses) {
  1141	        ArrayList<Annotation> anns = new ArrayList<Annotation>();
  1142	        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
  1143	        OpenSearch os = new OpenSearch();
  1144	        long startTime = System.currentTimeMillis();
  1145	
  1146	        if (query == null) return anns;
  1147	        JSONObject queryRes = null;
  1148	        int hitSize = -1;

 succeeded in 397ms:
src/main/java/org/ecocean/Annotation.java-923-   overwriting that needs to be addressed in the future
src/main/java/org/ecocean/Annotation.java-924- */
src/main/java/org/ecocean/Annotation.java:925:    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
src/main/java/org/ecocean/Annotation.java-926-        boolean useClauses) {
src/main/java/org/ecocean/Annotation.java-927-        Encounter enc = this.findEncounter(myShepherd);
src/main/java/org/ecocean/Annotation.java-928-
src/main/java/org/ecocean/Annotation.java-929-        if (enc == null) {
src/main/java/org/ecocean/Annotation.java:930:            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
src/main/java/org/ecocean/Annotation.java-931-                this);
src/main/java/org/ecocean/Annotation.java-932-            return null;
--
src/main/java/org/ecocean/Annotation.java-950-            "allowIdentificationWithoutTaxonomy"))) {
src/main/java/org/ecocean/Annotation.java-951-            System.out.println(
src/main/java/org/ecocean/Annotation.java:952:                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
src/main/java/org/ecocean/Annotation.java-953-            return null;
src/main/java/org/ecocean/Annotation.java-954-        }
--
src/main/java/org/ecocean/Annotation.java-1125-        // this exludes the very noisy embeddings from opensearch results since we dont need it
src/main/java/org/ecocean/Annotation.java-1126-        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
src/main/java/org/ecocean/Annotation.java:1127:        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
src/main/java/org/ecocean/Annotation.java-1128-        return query;
src/main/java/org/ecocean/Annotation.java-1129-    }
--
src/main/java/org/ecocean/Annotation.java-1140-        boolean useClauses) {
src/main/java/org/ecocean/Annotation.java-1141-        ArrayList<Annotation> anns = new ArrayList<Annotation>();
src/main/java/org/ecocean/Annotation.java:1142:        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
src/main/java/org/ecocean/Annotation.java-1143-        OpenSearch os = new OpenSearch();
src/main/java/org/ecocean/Annotation.java-1144-        long startTime = System.currentTimeMillis();
--
src/main/java/org/ecocean/Annotation.java-1179-    // return null when this annot has no embeddings to match, sorry!
src/main/java/org/ecocean/Annotation.java-1180-
src/main/java/org/ecocean/Annotation.java:1181:    // this version will construct matchingSetQuery
src/main/java/org/ecocean/Annotation.java-1182-    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
src/main/java/org/ecocean/Annotation.java-1183-        String method, String methodVersion) {
--
src/main/java/org/ecocean/Annotation.java-1186-        if (emb == null) return null;
src/main/java/org/ecocean/Annotation.java-1187-        return getMatchQuery(method, methodVersion,
src/main/java/org/ecocean/Annotation.java:1188:                getMatchingSetQuery(myShepherd, taskParams, useClauses));
src/main/java/org/ecocean/Annotation.java-1189-    }
src/main/java/org/ecocean/Annotation.java-1190-
src/main/java/org/ecocean/Annotation.java:1191:    // this version if you already have matchingSetQuery
src/main/java/org/ecocean/Annotation.java-1192-    public JSONObject getMatchQuery(String method, String methodVersion,
src/main/java/org/ecocean/Annotation.java:1193:        JSONObject matchingSetQuery) {
src/main/java/org/ecocean/Annotation.java-1194-        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java-1195-
--
src/main/java/org/ecocean/Annotation.java-1214-            filter.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
src/main/java/org/ecocean/Annotation.java-1215-                "\"}}"));
src/main/java/org/ecocean/Annotation.java:1216:        nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
src/main/java/org/ecocean/Annotation.java-1217-            must);
src/main/java/org/ecocean/Annotation.java-1218-        if (filter.length() > 0) {
--
src/main/java/org/ecocean/Annotation.java-1224-        JSONArray nestedMust = new JSONArray();
src/main/java/org/ecocean/Annotation.java-1225-        nestedMust.put(nested);
src/main/java/org/ecocean/Annotation.java:1226:        matchingSetQuery.getJSONObject("query").getJSONObject("bool").put("must", nestedMust);
src/main/java/org/ecocean/Annotation.java:1227:        return matchingSetQuery;
src/main/java/org/ecocean/Annotation.java-1228-    }
src/main/java/org/ecocean/Annotation.java-1229-

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '925,1015p'" in /mnt/c/Wildbook-clean2
 succeeded in 274ms:
   925	    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
   926	        boolean useClauses) {
   927	        Encounter enc = this.findEncounter(myShepherd);
   928	
   929	        if (enc == null) {
   930	            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
   931	                this);
   932	            return null;
   933	        }
   934	        JSONObject query = new JSONObject(
   935	            "{\"query\": {\"bool\": {\"filter\": [], \"must_not\": []} } }");
   936	        JSONObject wrapper = new JSONObject();
   937	        JSONObject arg = new JSONObject();
   938	        String txStr = enc.getTaxonomyString();
   939	        if (txStr != null) {
   940	            useClauses = true;
   941	            if (txStr.endsWith(" sp")) {
   942	                arg.put("encounterTaxonomy", txStr.substring(0, txStr.length() - 2) + "*");
   943	                wrapper.put("wildcard", arg);
   944	            } else {
   945	                arg.put("encounterTaxonomy", txStr);
   946	                wrapper.put("match", arg);
   947	            }
   948	            query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
   949	        } else if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   950	            "allowIdentificationWithoutTaxonomy"))) {
   951	            System.out.println(
   952	                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
   953	            return null;
   954	        }
   955	        // it seems like useClauses=false only ever was used when no taxonomy was present and basically
   956	        // returned every annotation with matchAgainst=T and an acmId
   957	        if (useClauses) {
   958	            if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   959	                "ignoreViewpointMatching", this.getTaxonomy(myShepherd)))) {
   960	                String[] viewpoints = this.getViewpointAndNeighbors();
   961	                if (viewpoints != null) {
   962	                    arg = new JSONObject();
   963	                    arg.put("viewpoint", new JSONArray(viewpoints));
   964	                    wrapper = new JSONObject();
   965	                    wrapper.put("terms", arg);
   966	                    // query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
   967	                    // to handle allowing null viewpoint, opensearch query gets messy!
   968	                    JSONArray should = new JSONArray(
   969	                        "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"viewpoint\"}}}}]");
   970	                    should.put(wrapper);
   971	                    JSONObject bool = new JSONObject("{\"bool\": {}}");
   972	                    bool.getJSONObject("bool").put("should", should);
   973	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   974	                        bool);
   975	                }
   976	            }
   977	            // this does either/or part/iaClass - unsure if this is correct
   978	            boolean usedPart = false;
   979	            if (Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   980	                "usePartsForIdentification"))) {
   981	                String part = this.getPartIfPresent();
   982	                if (!Util.stringIsEmptyOrNull(part)) {
   983	                    arg = new JSONObject();
   984	                    arg.put("iaClass", "*" + part);
   985	                    wrapper = new JSONObject();
   986	                    wrapper.put("wildcard", arg);
   987	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   988	                        wrapper);
   989	                    usedPart = true;
   990	                }
   991	            }
   992	            if (!usedPart && (this.getIAClass() != null)) {
   993	                arg = new JSONObject();
   994	                arg.put("iaClass", this.getIAClass());
   995	                wrapper = new JSONObject();
   996	                wrapper.put("match", arg);
   997	                query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   998	                    wrapper);
   999	            }
  1000	        }
  1001	        // matchAgainst true
  1002	        arg = new JSONObject();
  1003	        arg.put("matchAgainst", true);
  1004	        wrapper = new JSONObject();
  1005	        wrapper.put("term", arg);
  1006	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1007	
  1008	        // must have acmId
  1009	        arg = new JSONObject();
  1010	        arg.put("field", "acmId");
  1011	        wrapper = new JSONObject();
  1012	        wrapper.put("exists", arg);
  1013	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1014	
  1015	        // exclude our encounter

exec
/bin/bash -lc 'git diff --check -- src/main/java/org/ecocean/Annotation.java src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/Annotation.java src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"engine|method|space_type|knn_vector|index\\.knn\" src/main/java src/main/resources devops -g '"'!docs/plans/**'"'" in /mnt/c/Wildbook-clean2
 succeeded in 774ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/Annotation.java

 succeeded in 857ms:
 succeeded in 1147ms:
src/main/resources/openapi.yaml:125:      description: HTTP method not allowed for this endpoint
src/main/resources/openapi.yaml:136:                example: "method not allowed"
src/main/resources/openapi.yaml:501:      summary: Process login (alternate method)
src/main/resources/openapi.yaml:535:      summary: User logout (alternate method)
src/main/resources/openapi.yaml:536:      description: GET method also supported for logout
src/main/java/org/ecocean/FormUtilities.java:20:        // note how fieldName is variously manipulated in this method to make element ids and contents
src/main/java/org/ecocean/FormUtilities.java:33:        // note how fieldName is variously manipulated in this method to make element ids and contents
src/main/java/org/ecocean/FormUtilities.java:53:        // note how fieldName is variously manipulated in this method to make element ids and contents
src/main/java/org/ecocean/IndexingManager.java:56:    		//IMPORTANT - no persistent objects, such as the passed in Base can be referenced inside this method
src/main/java/org/ecocean/queue/QueueUtil.java:24:    // helper method for backgrounding queue consumers who dont background themselves
src/main/java/org/ecocean/Util.java:917:    // convenience method for comparing string values
src/main/java/org/ecocean/Util.java:1083:    // NOTE: a leading '/' is stripped off to be safe.  this puts the onus on the user of this method
src/main/java/com/reijns/I3S/Point2D.java:3:// In the C++ source, protected vars and methods could be accessible from a
src/main/java/com/reijns/I3S/Point2D.java:14:     * setter methods).  Beware of bending OO concepts by maintaining this system.  The DBL_INIT constant is initiated here and referenced in the
src/main/java/com/reijns/I3S/Point2D.java:23:     * Comments: Second constructor method added.
src/main/java/com/reijns/I3S/Point2D.java:43:     * Comments: Normally getter methods bend Encapsulation, but since the Point2D class can be considered as only a Value Object access to the data
src/main/java/com/reijns/I3S/Point2D.java:46:     * package.  Thus the getter methods are set for package level access (i.e. the default for undeclared method types) rather than public.
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:203:     * This is the most memory-efficient method for JPEG images.
src/main/java/org/ecocean/importutils/RowFeedback.java:47:            if (value == null) { // a tad experimental here. this means we don't have to check the parseSuccess in each getWhatever method
src/main/java/org/ecocean/User.java:432:    // Use this method to find out what organization-wide nameKey a user would want, to use to generate new individual names.
src/main/java/org/ecocean/User.java:497:    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
src/main/java/com/reijns/I3S/Pair.java:11:    // protected vars and methods could be accessible from the
src/main/java/org/ecocean/export/EncounterAnnotationExportFile.java:108:        // method is null as we current approach does not support parameters
src/main/java/org/ecocean/identity/IBEISIA.java:321:                // argh we need to standardize this and/or have a method. :/
src/main/java/org/ecocean/identity/IBEISIA.java:1117:    this is the re-tooling of this method which does nothing with encounter(s)
src/main/java/org/ecocean/identity/IBEISIA.java:1573:    1. how do we determine where the cutoff is for auto-creating the annotation?-- made some methods for this
src/main/java/org/ecocean/identity/IBEISIA.java:3182:     * TODO: evaluate as deprecated and remove. See primeIA() instead This static method sends all annotations and media assets for a species in Wildbook to Image Analysis in
src/main/java/org/ecocean/identity/IBEISIA.java:3538:        // this whole method is just navigating the pyramid of doom
src/main/java/com/reijns/I3S/LinSys.java:297:    // This is a private inner class.  So only the methods
src/main/java/org/ecocean/QueryProcessor.java:13:    // a bunch of util methods
src/main/java/org/ecocean/QueryProcessor.java:92:    // This is probably not the ideal place for a method with this narrow of usage, but I've put it here in the interest
src/main/java/org/ecocean/TemplateFiller.java:197:     * @return Current text content of the template, or {@code null} if not loaded. This is the method to be used to retrieve the template contents
src/main/java/com/reijns/I3S/FingerPrint.java:52:// Old : deleted System.arraycopy code and replaced it with setSpots method
src/main/java/com/reijns/I3S/FingerPrint.java:78:// added private method setSpots
src/main/java/com/reijns/I3S/FingerPrint.java:116:// Old : deleted System.arraycopy code and replaced it with setSpots method
src/main/java/com/reijns/I3S/FingerPrint.java:136:        // System.out.println("Starting the distance method...");
src/main/java/com/reijns/I3S/FingerPrint.java:269:     * Sets the total distance score for all the points in this class compared to another FingerPrint class passed in via the distance method.
src/main/java/org/ecocean/Taxonomy.java:24:    // while convenience string-only methods will use noun "Species" (and might require Shepherds to see which Taxonomy objects exist in the DB, for
src/main/java/org/ecocean/Taxonomy.java:98:    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
src/main/java/org/ecocean/Base.java:317:    // these two methods are kinda hacky needs for opensearchSyncIndex (e.g. the fact
src/main/java/org/ecocean/IAJsonProperties.java:53:    // naming convention: not using 'get' on static methods
src/main/java/org/ecocean/IAJsonProperties.java:70:    // Detection methods
src/main/java/org/ecocean/IAJsonProperties.java:101:    // all detection methods take an int i, bc there may be multiple detection algos for a taxonomy
src/main/java/org/ecocean/IAJsonProperties.java:118:        // we can't just mess with config bc changes aren't scoped to this method
src/main/java/org/ecocean/IAJsonProperties.java:238:    // Identification methods
src/main/java/org/ecocean/IAJsonProperties.java:273:        // into ident configs returned by this method.
src/main/java/org/ecocean/IAJsonProperties.java:379:    // mimics an old IBEISIA method for easy migration
src/main/java/org/ecocean/Encounter.java:1015:    // like above, but class method so you pass the encID
src/main/java/org/ecocean/Encounter.java:1186:    // TODO: Verify and remove if this is deprecated: A legacy method replaced by setLocationID(...).
src/main/java/org/ecocean/Encounter.java:1375:    // utility method for created MediaAssets
src/main/java/org/ecocean/Encounter.java:1458:    // old method. use getAssignedUser() instead
src/main/java/org/ecocean/Encounter.java:1918:    // ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/Encounter.java:1924:    // ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Encounter.java:1930:    // ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/Encounter.java:1936:    // ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Encounter.java:2193:    // we need these methods because our side-effected setGenus will silently break an import (!!!!!) in an edge case I cannot identify
src/main/java/org/ecocean/Encounter.java:2756:    // A convenience method that returns the first haplotype found in the TissueSamples for this Encounter.
src/main/java/org/ecocean/Encounter.java:2780:    // A convenience method that returns the first genetic sex found in the TissueSamples for this Encounter.
src/main/java/org/ecocean/Encounter.java:2987:    // this is really only for above method
src/main/java/org/ecocean/Encounter.java:3050:    // convenience method
src/main/java/org/ecocean/Encounter.java:4064:    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
src/main/java/com/reijns/I3S/Compare.java:58:    * In method parameter : Map<Integer,Pair>
src/main/java/com/reijns/I3S/Compare.java:119:    //System.out.println("entering the Compare.find method, pairs size is: "+pairs.size()+" and cnt is "+cnt );
src/main/java/com/reijns/I3S/Compare.java:253:   * The Comparator method class for sorting in the find method.
src/main/java/org/ecocean/Embedding.java:33:    private String method;
src/main/java/org/ecocean/Embedding.java:34:    private String methodVersion;
src/main/java/org/ecocean/Embedding.java:43:    public Embedding(Annotation ann, String method, String methodVersion, PGvector vec) {
src/main/java/org/ecocean/Embedding.java:47:        this.method = method;
src/main/java/org/ecocean/Embedding.java:48:        this.methodVersion = methodVersion;
src/main/java/org/ecocean/Embedding.java:54:    public Embedding(Annotation ann, String method, String methodVersion, JSONArray vecArr) {
src/main/java/org/ecocean/Embedding.java:55:        this(ann, method, methodVersion, (PGvector)null);
src/main/java/org/ecocean/Embedding.java:93:        return method;
src/main/java/org/ecocean/Embedding.java:96:    public void setMethod(String method) {
src/main/java/org/ecocean/Embedding.java:97:        this.method = method;
src/main/java/org/ecocean/Embedding.java:101:        return methodVersion;
src/main/java/org/ecocean/Embedding.java:104:    public void setMethodVersion(String methodVersion) {
src/main/java/org/ecocean/Embedding.java:105:        this.methodVersion = methodVersion;
src/main/java/org/ecocean/Embedding.java:109:        return ((method == null) ? "(unknown)" : method) + "-" + ((methodVersion ==
src/main/java/org/ecocean/Embedding.java:110:                   null) ? "(unknown version)" : methodVersion);
src/main/java/org/ecocean/Embedding.java:267:        // contract (entries with `method`/`version`/`pipeline_root` and no
src/main/java/org/ecocean/Embedding.java:270:        boolean isVectorConfig = Util.stringExists(iaConfig.optString("method", null))
src/main/java/org/ecocean/Embedding.java:347:            // Read method/version directly from the new _id_conf contract;
src/main/java/org/ecocean/Embedding.java:349:            String method = iaConfig.optString("method", null);
src/main/java/org/ecocean/Embedding.java:351:            if (!Util.stringExists(method)) {
src/main/java/org/ecocean/Embedding.java:353:                method = mv[0];
src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
src/main/java/org/ecocean/mmutil/MediaUtilities.java:76:     * Checks filename extension for supported image type. This method had been recreated here to allow static and centralized access;
src/main/java/org/ecocean/mmutil/MediaUtilities.java:87:     * Checks filename extension for supported video type. This method had been recreated here to allow static and centralized access;
src/main/java/org/ecocean/mmutil/MediaUtilities.java:402:     * Convenience method to convert a {@code BufferedImage} to the sRGB color space if not already compatible.
src/main/java/org/ecocean/mmutil/MediaUtilities.java:422:     * Convenience method that returns a scaled instance of the provided {@code BufferedImage}. Any transparency/alpha of the original image is not
src/main/java/org/ecocean/mmutil/MediaUtilities.java:450:     * Convenience method that returns a scaled instance of the provided {@code BufferedImage} with a text overlay. Any transparency/alpha of the
src/main/java/org/ecocean/mmutil/MediaUtilities.java:496:     * Extracts all the EXIF tags from the specified Metadata instance. This method ignores all GPS-related tags for data security reasons
src/main/java/org/ecocean/MarkedIndividual.java:861:    // TODO: evaluate and remove if deprecated:  ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/MarkedIndividual.java:898:    // TODO: evaluate and remove if deprecated:  ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/MarkedIndividual.java:2532:    // convenience method which uses the above prefix stuff
src/main/java/org/ecocean/EmailTemplate.java:185:     * Performs a string search/replace on the subject and body of the template. This method is a convenience to perform all replacements throughout.
src/main/java/org/ecocean/EmailTemplate.java:234:     * Searches and replaces one or all occurrences of the specified regular expression search term with the specified replacement. This method is a
src/main/java/org/ecocean/EmailTemplate.java:329:     * {@link #reset()} method is called.
src/main/java/org/ecocean/EmailTemplate.java:677:     * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text and HTML messages respectively. This method doesn't attempt to load the files, just
src/main/java/org/ecocean/EmailTemplate.java:698:     * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text and HTML messages respectively. This method doesn't attempt to load the files, just
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:88:                URL wbiaQueueUrl = IBEISIA.iaURL(context, "api/engine/job/status/");
src/main/java/org/ecocean/mmutil/DataUtilities.java:7: * Class providing useful generic data-oriented utility methods.
src/main/java/org/ecocean/Organization.java:403:    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
src/main/java/org/ecocean/MetricsBot.java:27:import org.apache.http.client.methods.HttpGet;
src/main/java/org/ecocean/MetricsBot.java:135:    // Helper method to safely parse label from buildGauge result
src/main/java/org/ecocean/MetricsBot.java:364:                    "wbia_turnaround_seconds\\{endpoint=\"/api/engine/detect/cnn/lightnet/\".*\\} \\d*\\.\\d*";
src/main/java/org/ecocean/MetricsBot.java:372:                    "wbia_turnaround_seconds\\{endpoint=\"/api/engine/query/graph/\".*\\} \\d*\\.\\d*";
src/main/java/org/ecocean/MetricsBot.java:427:    // Helper method to add a line to csvLines only if it's not null
src/main/java/org/ecocean/MetricsBot.java:434:    // Helper method to get total document count from an OpenSearch index
src/main/java/org/ecocean/MetricsBot.java:459:    // Helper method to get database count for a class
src/main/java/org/ecocean/MetricsBot.java:480:     * Helper method for adding machine learning tasks related metrics
src/main/java/org/ecocean/MetricsBot.java:653:    // Helper method for normalizing characters
src/main/java/org/ecocean/ia/Task.java:408:    // internal utility method for above
src/main/java/org/ecocean/ia/Task.java:436:    // convenience method to construct the JSONObject from key/value
src/main/java/org/ecocean/ia/Task.java:657:    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
src/main/java/org/ecocean/ia/Task.java:772:            "unknown algorithm/method"));
src/main/java/org/ecocean/ia/Task.java:837:        JSONObject methodInfo = getIdentificationMethodInfo();
src/main/java/org/ecocean/ia/Task.java:838:        // methodInfo gates the "identification-like" extras (method label,
src/main/java/org/ecocean/ia/Task.java:841:        // match tasks, so methodInfo is null even though the MatchResult
src/main/java/org/ecocean/ia/Task.java:846:        if (methodInfo != null) {
src/main/java/org/ecocean/ia/Task.java:847:            rtn.put("method", methodInfo);
src/main/java/org/ecocean/ia/Task.java:850:        // Always serialize an existing MatchResult regardless of methodInfo.
src/main/java/org/ecocean/ia/Task.java:855:        // and that path stays gated by methodInfo since it interprets
src/main/java/org/ecocean/ia/Task.java:856:        // identification-method-specific log JSON.
src/main/java/org/ecocean/ia/Task.java:858:        if ((mr == null) && (methodInfo != null) && !hasChildren()) {
src/main/java/org/ecocean/Keyword.java:49:    // This method is overwritten by inheriting classes like LabeledKeyword
src/main/java/org/ecocean/Keyword.java:80:    // convenience method for removing duplicate keywords
src/main/java/org/ecocean/LocationID.java:230:    public static String getPrefixForLocationID(String locationID, String qualifier) { // now a wrapper method
src/main/java/org/ecocean/LocationID.java:243:    public static int getPrefixDigitPaddingForLocationID(String locationID, String qualifier) { // now a wrapper method
src/main/java/org/ecocean/Occurrence.java:106:    // while convenience string-only methods with noun "Species"
src/main/java/org/ecocean/Occurrence.java:420:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:425:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:438:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:453:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:611:    // while convenience string-only methods use noun "Species"
src/main/java/org/ecocean/Occurrence.java:620:    // convenience method for e.g. web display
src/main/java/org/ecocean/Occurrence.java:728:    // This method simply iterates through the encounters for the occurrence and returns the first Encounter.locationID that it finds or returns null.
src/main/java/org/ecocean/OpenSearch.java:555:    // embeddings.method/methodVersion would pass _id-only and then knn-fail
src/main/java/org/ecocean/OpenSearch.java:558:    // AND acmId exists AND a nested embedding for this method/version is
src/main/java/org/ecocean/OpenSearch.java:564:    // method/methodVersion follow the strict-when-present convention of
src/main/java/org/ecocean/OpenSearch.java:572:        String method, String methodVersion, long timeoutMs)
src/main/java/org/ecocean/OpenSearch.java:591:        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
src/main/java/org/ecocean/OpenSearch.java:592:            methodVersion);
src/main/java/org/ecocean/OpenSearch.java:616:        String method, String methodVersion) {
src/main/java/org/ecocean/OpenSearch.java:636:        if (method != null) {
src/main/java/org/ecocean/OpenSearch.java:638:                new JSONObject().put("embeddings.method", method)));
src/main/java/org/ecocean/OpenSearch.java:640:        if (methodVersion != null) {
src/main/java/org/ecocean/OpenSearch.java:642:                new JSONObject().put("embeddings.methodVersion", methodVersion)));
src/main/java/org/ecocean/OpenSearch.java:648:            // can't derive a method.)
src/main/java/org/ecocean/NotificationMailer.java:52: * {@link #run()} method is invoked, usually via a wrapping
src/main/java/org/ecocean/NotificationMailer.java:104: * methods.</p>
src/main/java/org/ecocean/NotificationMailer.java:356:     * Loads an email template from the specified email types. This method traverses the list of types until one is found that provides a valid email
src/main/java/org/ecocean/NotificationMailer.java:436:     * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text and HTML messages respectively. This method doesn't attempt to load the files, just
src/main/java/org/ecocean/NotificationMailer.java:464:     * Performs a string search/replace on the subject and body of the template. This method is a convenience to perform all replacements throughout.
src/main/java/org/ecocean/NotificationMailer.java:474:     * Searches and replaces one or all occurrences of the specified regular expression search term with the specified replacement. This method is a
src/main/java/org/ecocean/opendata/Share.java:88:        if (orgUsers == null) orgUsers = new ArrayList<User>(); // means we at least tried once!  (see methods below)
src/main/java/org/ecocean/SinglePhotoVideo.java:80:     * Returns the photo or video represented by this object as a java.io.File This is a convenience method.
src/main/java/org/ecocean/api/SiteSettings.java:178:            // this seems like less desirable method: getAllKeywordsNoLabeledKeywords()
src/main/java/org/ecocean/media/YouTubeAssetStore.java:198:    // note that uses same _processing derivation method as .grabAndParse() above.  this is cuz these should not be run simultaneous anyway
src/main/java/org/ecocean/metrics/Prometheus.java:60:    // Helper method for metrics() also borrowed from MetricsServlet.java
src/main/java/org/ecocean/api/SearchApi.java:62:                    res.put("error", "method not allowed");
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:25:    A Queue is not strictly necessary.  The intake methods could have simply done whatever processing necessary (e.g. send the objects to a blackbox
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:26:       IA service) in real-time.  The Queue just demostrates how to allow intake methods to asynchronously stack up jobs in the background.
src/main/java/org/ecocean/StartupWildbook.java:929:        System.out.println("Entering createMatchGraph StartupWildbook method.");
src/main/java/org/ecocean/RestClient.java:2:/*  NOTE: due to the authentication header stuff, this is effectively IBEIS-specific but starting to break this out into generic. Still a work-in-progress.... see methods at the end of the file which will hopefully allow control over auth stuff better
src/main/java/org/ecocean/RestClient.java:67:    private static JSONObject anyMethod(String method, URL url, JSONObject data)
src/main/java/org/ecocean/RestClient.java:70:        return anyMethod(method, url, data, CONNECTION_TIMEOUT);
src/main/java/org/ecocean/RestClient.java:73:    private static JSONObject anyMethod(String method, URL url, JSONObject data, int ctimeout)
src/main/java/org/ecocean/RestClient.java:76:        System.out.println("TRYING anyMethod(" + method + ") url -> " + url);
src/main/java/org/ecocean/RestClient.java:83:        conn.setRequestMethod(method);
src/main/java/org/ecocean/RestClient.java:266:    private static String anyMethodGeneric(String method, URL url, String authUsername,
src/main/java/org/ecocean/RestClient.java:270:        return anyMethodGeneric(method, url, authUsername, authPassword, data, CONNECTION_TIMEOUT);
src/main/java/org/ecocean/RestClient.java:273:    private static String anyMethodGeneric(String method, URL url, String authUsername,
src/main/java/org/ecocean/RestClient.java:277:        System.out.println("TRYING anyMethodGeneric(" + method + ") url -> " + url);
src/main/java/org/ecocean/RestClient.java:283:        conn.setRequestMethod(method);
src/main/java/org/ecocean/RestClient.java:335:    // values or 0 — this method does not clamp.
src/main/java/org/ecocean/SpotterConserveIO.java:848:    public static Path trackToPath(JSONObject track) { // for now (see comments above) this is just convenience method
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:37:    // having the code in these methods set the child on the parent  (hence the 'final')
src/main/resources/org/ecocean/package.jdo:1043:		     to support idempotency lookup of (annotation, method, methodVersion).
src/main/resources/org/ecocean/package.jdo:1067:		<field name="method">
src/main/resources/org/ecocean/package.jdo:1072:		<field name="methodVersion">
src/main/java/org/ecocean/SurveyQueryProcessor.java:51:        // Filter method takes a relative package argument as a means of making is adaptable for other classes.
src/main/java/org/ecocean/Spot.java:6: * "setter" methods are allowed in order to preserve data integrity.
src/main/java/org/ecocean/Survey.java:157:    // TODO: evaluate this method in relation to getSurveyTracks(); remove if duplicate
src/main/java/org/ecocean/ia/MlServiceProcessor.java:713:                    config.put("method", emb.getMethod());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:730:            if (!Util.stringExists(config.optString("method", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:732:                config.put("method", embeddingSource.optString("embedding_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:898:        String method = matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:900:        if (!Util.stringExists(method)) return ann.numberEmbeddings() > 0;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:901:        return ann.getEmbeddingByMethod(method, version) != null;
src/main/java/org/ecocean/shepherd/utils/ShepherdState.java:6: * ShepherdState provides static utility methods to track the state and use of shepherd instances (database access).
src/main/java/org/ecocean/media/MediaAssetMetadata.java:52:    // a convenience method which accesses the data.attributes JSONObject
src/main/java/org/ecocean/Setting.java:162:    class. not ideal. some casting convenience methods are below, but may not act
src/main/java/org/ecocean/Setting.java:164:    convenience all-in-one method: Shepherd.getSettingValue()
src/main/java/org/ecocean/social/SocialUnit.java:40:    // preserve calls to old method that required shepherd
src/main/java/org/ecocean/servlet/WriteOutScanTask.java:320:    } // end writeResult method
src/main/java/org/ecocean/api/Logout.java:41:        ThreadContext.put("http_method", request.getMethod());
src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java:38:    // This method works just like getProperties, except it gives priority to your organization-specific .properties overwrite file
src/main/java/org/ecocean/ia/MLService.java:333:        String[] methodValues = getMethodValues(res);
src/main/java/org/ecocean/ia/MLService.java:334:        Embedding emb = new Embedding(ann, methodValues[0], methodValues[1], vecArr);
src/main/java/org/ecocean/ia/MLService.java:335:        // maybe this is unwise? could 2 embeddings *from different methods* have same vectors? TODO
src/main/java/org/ecocean/media/MediaAsset.java:97:     * To be called by AssetStore factory method.
src/main/java/org/ecocean/shepherd/core/Shepherd.java:94:    // static method so the programmer knows this is an *active* Shepherd
src/main/java/org/ecocean/shepherd/core/Shepherd.java:127:     * also its unique retrievable ID in the database. This method will be the primary method used for future web submissions to shepherd from
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1174:        // bc of how sql's startsWith method works we need the null check below
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2636:    // this method returns the MediaAsset on an Indy with the given keyword, with preference
src/main/java/org/ecocean/shepherd/core/Shepherd.java:3566:            // prevents npe's on search pages, counting methods
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4061:        // bc of how sql's startsWith method works we need the null check below
src/main/java/org/ecocean/api/Login.java:55:        ThreadContext.put("http_method", request.getMethod());
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:102:        // Derive method/methodVersion from matchConfig using the same
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:104:        // (Embedding.java:349-355): _id_conf.method/version first,
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:117:        String method = (matchConfig == null) ? null
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:118:            : matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:119:        String methodVersion = (matchConfig == null) ? null
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:121:        if (!Util.stringExists(method) && matchConfig != null) {
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:123:            method = (mv == null) ? null : mv[0];
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:124:            methodVersion = (mv == null) ? null : mv[1];
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:130:            phase12 = loadPhase12(childTaskId, method, methodVersion,
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:170:                    method, methodVersion, VISIBILITY_TIMEOUT_MS)) {
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:213:    Phase12Result loadPhase12(String childTaskId, String method,
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:214:        String methodVersion, Set<String> normalizedCaller)
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:271:                .findEligibleAnnotationIds(shep, contributingMaIds, method,
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:272:                    methodVersion);
src/main/java/org/ecocean/media/LocalAssetStore.java:119:    // convenience method to create directly from single File arguement (LocalAssetStore only)
src/main/java/org/ecocean/security/HiddenOccReporter.java:21:    // atomic methods that HiddenDataReporter methods call
src/main/java/org/ecocean/Annotation.java:195:        embProps.put("method", keywordType);
src/main/java/org/ecocean/Annotation.java:196:        embProps.put("methodVersion", keywordType);
src/main/java/org/ecocean/Annotation.java:199:        embVect.put("type", "knn_vector");
src/main/java/org/ecocean/Annotation.java:201:        embVect.put("space_type", "cosinesimil");
src/main/java/org/ecocean/Annotation.java:249:                jgen.writeStringField("method", emb.getMethod());
src/main/java/org/ecocean/Annotation.java:250:                jgen.writeStringField("methodVersion", emb.getMethodVersion());
src/main/java/org/ecocean/Annotation.java:1178:    // method and methodVersion are used to determine *which* embedding to use; if null it will use 1st embedding
src/main/java/org/ecocean/Annotation.java:1183:        String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:1184:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
src/main/java/org/ecocean/Annotation.java:1194:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1201:        // +1.0-per-term-clause offset). method/methodVersion become
src/main/java/org/ecocean/Annotation.java:1211:        if (method != null)
src/main/java/org/ecocean/Annotation.java:1212:            filter.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
src/main/java/org/ecocean/Annotation.java:1213:        if (methodVersion != null)
src/main/java/org/ecocean/Annotation.java:1214:            filter.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
src/main/java/org/ecocean/Annotation.java:1248:        boolean useClauses, String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:1250:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
src/main/java/org/ecocean/Annotation.java:1378:               methods, and we are "zooming in" on the actual animal.  or *one of* the actual animals -- if there are others, they should get added in
src/main/java/org/ecocean/Annotation.java:1875:    // a convenient method which does a typical set of steps to ready Annotation for deletion from db
src/main/java/org/ecocean/Annotation.java:2072:    public Embedding getEmbeddingByMethod(String method) {
src/main/java/org/ecocean/Annotation.java:2073:        return getEmbeddingByMethod(method, null);
src/main/java/org/ecocean/Annotation.java:2077:    public Embedding getEmbeddingByMethod(String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:2080:        if (method == null) return (Embedding)it.next();
src/main/java/org/ecocean/Annotation.java:2083:            if (!method.equals(emb.getMethod())) continue;
src/main/java/org/ecocean/Annotation.java:2084:            if ((methodVersion == null) || (methodVersion.equals(emb.getMethodVersion())))
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:106:     *                              source of {@code method} and
src/main/java/org/ecocean/api/BulkImport.java:375:                    // ... but this new method is less than 1 sec :)
src/main/java/org/ecocean/security/HiddenIndividualReporter.java:22:    // atomic methods that HiddenDataReporter methods call
src/main/java/org/ecocean/media/Feature.java:97:     * {@code Feature.asset}; this method is the symmetric setter for the
src/main/java/org/ecocean/CommonConfiguration.java:127:    // start getter methods
src/main/java/org/ecocean/CommonConfiguration.java:136:     * Utility method to return a {@code URI} instance for the specified context path of the server relating to the servlet request. This method
src/main/java/org/ecocean/CommonConfiguration.java:156:     * Utility method to return a URL string for the specified context path of the server relating to the servlet request. This method ensures all
src/main/java/org/ecocean/CommonConfiguration.java:572:     * This method is used to determined the show/hide condition of an element of the UI. It simply looks to see if a property is defined AND if the
src/main/java/org/ecocean/CommonConfiguration.java:573:     * property is false. For any other value or if the value is absent, the method returns true. Thsi means that conditional elements are shown by
src/main/java/org/ecocean/CommonConfiguration.java:633:     * Helper method to parse boolean from string.
src/main/java/org/ecocean/ClassEditTemplate.java:25:    public static void invokeObjectMethod(Object obj, String methodName, String valueAsString)
src/main/java/org/ecocean/ClassEditTemplate.java:28:            Class c = findTypeOfField(obj.getClass(), methodName);
src/main/java/org/ecocean/ClassEditTemplate.java:31:                Method setter = obj.getClass().getMethod(methodName, Double.class);
src/main/java/org/ecocean/ClassEditTemplate.java:33:                System.out.println("invokeObjectMethod: just invoked " + methodName +
src/main/java/org/ecocean/ClassEditTemplate.java:38:                Method setter = obj.getClass().getMethod(methodName, Integer.class);
src/main/java/org/ecocean/ClassEditTemplate.java:40:                System.out.println("invokeObjectMethod: just invoked " + methodName +
src/main/java/org/ecocean/ClassEditTemplate.java:45:                Method setter = obj.getClass().getMethod(methodName, Boolean.class);
src/main/java/org/ecocean/ClassEditTemplate.java:47:                System.out.println("invokeObjectMethod: just invoked " + methodName +
src/main/java/org/ecocean/ClassEditTemplate.java:51:                Method setter = obj.getClass().getMethod(methodName, String.class);
src/main/java/org/ecocean/ClassEditTemplate.java:53:                System.out.println("invokeObjectMethod: just invoked " + methodName +
src/main/java/org/ecocean/ClassEditTemplate.java:58:                Method setter = obj.getClass().getMethod(methodName, DateTime.class);
src/main/java/org/ecocean/ClassEditTemplate.java:60:                System.out.println("invokeObjectMethod: just invoked " + methodName +
src/main/java/org/ecocean/ClassEditTemplate.java:64:            System.out.println("invokeObjectMethod: was not able to invoke " + methodName +
src/main/java/org/ecocean/ClassEditTemplate.java:113:        // note how fieldName is variously manipulated in this method to make element ids and contents
src/main/java/org/ecocean/ClassEditTemplate.java:125:        // note how fieldName is variously manipulated in this method to make element ids and contents
src/main/java/org/ecocean/ClassEditTemplate.java:136:        // note how fieldName is variously manipulated in this method to make element ids and contents
src/main/java/org/ecocean/ClassEditTemplate.java:146:        // note how fieldName is variously manipulated in this method to make element ids and contents
src/main/java/org/ecocean/ClassEditTemplate.java:200:    public static boolean isDisplayableGetter(Method method) {
src/main/java/org/ecocean/ClassEditTemplate.java:202:            String methName = method.getName();
src/main/java/org/ecocean/ClassEditTemplate.java:205:            Class fieldType = method.getReturnType();
src/main/java/org/ecocean/ClassEditTemplate.java:206:            Method setter = method.getDeclaringClass().getMethod("set" + fieldName, fieldType);
src/main/java/org/ecocean/ClassEditTemplate.java:339:    // custom method to replicate a very specific table row format on this page
src/main/java/org/ecocean/ClassEditTemplate.java:359:    // custom method to replicate a very specific table row format on this page
src/main/java/org/ecocean/ClassEditTemplate.java:414:                System.out.println("  (successfully invoked object method)");
src/main/java/org/ecocean/security/HiddenEncReporter.java:17:    // atomic methods that HiddenDataReporter methods call
src/main/java/org/ecocean/ia/MatchResult.java:484:     *       before this method) prevents negative x/y from being
src/main/java/org/ecocean/security/HiddenDataReporter.java:33: * > filter your export with the contains() method
src/main/java/org/ecocean/security/HiddenDataReporter.java:109:    // these basic pieces. You can make a HDR for anything with these methods.
src/main/java/org/ecocean/security/Collaboration.java:256:            // this is consistent with the current method calling this function
src/main/java/org/ecocean/security/Collaboration.java:426:            viewer.getUUID().equals(owner.getUUID())) return true; // should really be user .equals() method
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:20: * configured method/methodVersion.
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:31: * <p>method/methodVersion match Annotation.getMatchQuery semantics
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:35: * method).</p>
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:47:     * {@code SELECT DISTINCT}, so ordering is engine-dependent but
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:51:     *                       transaction lifecycle; this method
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:57:     * @param method         embedding method (e.g. "miewid-msv4.1");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:59:     * @param methodVersion  embedding method version (e.g. "4.1");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:71:        String method,
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:72:        String methodVersion)
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:86:        String sql = buildSql(normalized, method, methodVersion);
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:134:     * and methodVersion come from admin-controlled IA.json, so SQL
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:150:    static String buildSql(Collection<Integer> siblingMaIds, String method,
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:151:        String methodVersion) {
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:166:        boolean joinEmbedding = method != null || methodVersion != null;
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:173:        if (method != null) {
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:174:            sql.append("AND e.\"METHOD\" = '").append(sqlEscape(method)).append("' ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:176:        if (methodVersion != null) {
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:178:                .append(sqlEscape(methodVersion)).append("' ");
src/main/java/org/ecocean/media/AssetStore.java:46: * AssetStore describes a location and methods for access to a set of MediaAssets.  Concrete subtypes fill in the "hows".
src/main/java/org/ecocean/media/AssetStore.java:275:        // right now we strictly bail on non-images. TODO: let various methods try to do whatever this means for their type
src/main/resources/bundles/it/encounter.properties:28:method = Metodo
src/main/resources/bundles/IA.properties:6:#IBEISIARestUrlStartIdentifyAnnotations = http://localhost:5000/api/engine/query/graph/
src/main/resources/bundles/IA.properties:8:#IBEISIARestUrlStartDetectImages = http://localhost:5000/api/engine/detect/cnn/yolo/
src/main/resources/bundles/IA.properties:11:#IBEISIARestUrlGetJobStatus = http://localhost:5000/api/engine/job/status/
src/main/resources/bundles/IA.properties:12:#IBEISIARestUrlGetJobResult = http://localhost:5000/api/engine/job/result/
src/main/resources/bundles/IA.properties:64:#IBEISIARestUrlStartDetectImages.modelTag_goes_here = http://localhost:5000/api/engine/detect/cnn/lightnet/
src/main/resources/bundles/IA.properties:83:#IBEISIARestUrlStartDetectImages.modelTag_goes_here = http://localhost:5000/api/engine/detect/cnn/lightnet/
src/main/java/org/ecocean/api/BaseObject.java:86:            throw new ServletException("Invalid method");
src/main/java/org/ecocean/ai/ocr/azure/AzureOcr.java:9:import org.apache.http.client.methods.HttpPost;
src/main/java/org/ecocean/ia/IA.java:49:    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
src/main/java/org/ecocean/ia/IA.java:267:     * is persisted before this method enqueues. On enqueue failure the
src/main/java/org/ecocean/ia/IA.java:278:     * this method. This matches the reconciler's use case where there is
src/main/java/org/ecocean/ia/IA.java:346:                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
src/main/java/org/ecocean/servlet/EncounterSetField.java:62:            throw new IOException("EncounterSetField: there is no Encounter method called " +
src/main/java/org/ecocean/servlet/importer/WebImport.java:311:            // overwrite=false on following fromEncs methods
src/main/java/org/ecocean/servlet/importer/WebImport.java:979:    // Below methods are *not* static and work from column names rather than column numbers
src/main/java/org/ecocean/servlet/importer/WebImport.java:1136:        // all UI methods must begin with print (convention)
src/main/java/org/ecocean/servlet/importer/WebImport.java:1217:            if (value == null) { // a tad experimental here. this means we don't have to check the parseSuccess in each getWhatever method
src/main/java/org/ecocean/servlet/importer/WebImport.java:1236:    // cannot put this inside CellFeedback bc java inner classes are not allowed static methods or vars (this is stupid).
src/main/java/org/ecocean/servlet/UserConsolidate.java:859:        PersistenceManager persistenceManager) {                                                                         // now a wrapper method
src/main/java/org/ecocean/servlet/DispatchServlet.java:20: * Servlet base class which dispatches calls to delegate methods based on the value of the extra path info in the servlet URL. For example, if the
src/main/java/org/ecocean/servlet/DispatchServlet.java:23: * then the method
src/main/java/org/ecocean/servlet/DispatchServlet.java:25: * will be called within the servlet. Within the context of this class, the method {@code foo} is considered the delegate method.
src/main/java/org/ecocean/servlet/DispatchServlet.java:27: * Sub-classes should register which methods they use with the various
src/main/java/org/ecocean/servlet/DispatchServlet.java:28: * {@code registerXXX()} methods, otherwise they will not be accessible. This is best done in the {@code init()} method.
src/main/java/org/ecocean/servlet/DispatchServlet.java:37:    /** List of method names supporting GET. */
src/main/java/org/ecocean/servlet/DispatchServlet.java:38:    private final Set<String> methodsGET = new HashSet<String>();
src/main/java/org/ecocean/servlet/DispatchServlet.java:39:    /** List of method names supporting POST. */
src/main/java/org/ecocean/servlet/DispatchServlet.java:40:    private final Set<String> methodsPOST = new HashSet<String>();
src/main/java/org/ecocean/servlet/DispatchServlet.java:59:     * Registers the named methods as valid for GET requests.
src/main/java/org/ecocean/servlet/DispatchServlet.java:60:     * @param methodName name of method to register
src/main/java/org/ecocean/servlet/DispatchServlet.java:61:     * @throws DelegateNotFoundException if the named method is not found
src/main/java/org/ecocean/servlet/DispatchServlet.java:63:    protected final void registerMethodGET(String... methodName)
src/main/java/org/ecocean/servlet/DispatchServlet.java:65:        for (String s : methodName) {
src/main/java/org/ecocean/servlet/DispatchServlet.java:67:                methodsGET.add(s);
src/main/java/org/ecocean/servlet/DispatchServlet.java:72:     * Unregisters the named methods as valid for GET requests.
src/main/java/org/ecocean/servlet/DispatchServlet.java:73:     * @param methodName name of method to register
src/main/java/org/ecocean/servlet/DispatchServlet.java:74:     * @throws DelegateNotFoundException if the named method is not found
src/main/java/org/ecocean/servlet/DispatchServlet.java:76:    protected final void unregisterMethodGET(String... methodName)
src/main/java/org/ecocean/servlet/DispatchServlet.java:78:        for (String s : methodName)
src/main/java/org/ecocean/servlet/DispatchServlet.java:79:            methodsGET.remove(s);
src/main/java/org/ecocean/servlet/DispatchServlet.java:83:     * Registers the named methods as valid for POST requests.
src/main/java/org/ecocean/servlet/DispatchServlet.java:84:     * @param methodName name of method to register
src/main/java/org/ecocean/servlet/DispatchServlet.java:85:     * @throws DelegateNotFoundException if the named method is not found
src/main/java/org/ecocean/servlet/DispatchServlet.java:87:    protected final void registerMethodPOST(String... methodName)
src/main/java/org/ecocean/servlet/DispatchServlet.java:89:        for (String s : methodName) {
src/main/java/org/ecocean/servlet/DispatchServlet.java:91:                methodsPOST.add(s);
src/main/java/org/ecocean/servlet/DispatchServlet.java:96:     * Unregisters the named methods as valid for POST requests.
src/main/java/org/ecocean/servlet/DispatchServlet.java:97:     * @param methodName name of method to register
src/main/java/org/ecocean/servlet/DispatchServlet.java:98:     * @throws DelegateNotFoundException if the named method is not found
src/main/java/org/ecocean/servlet/DispatchServlet.java:100:    protected final void unregisterMethodPOST(String... methodName)
src/main/java/org/ecocean/servlet/DispatchServlet.java:102:        for (String s : methodName)
src/main/java/org/ecocean/servlet/DispatchServlet.java:103:            methodsPOST.remove(s);
src/main/java/org/ecocean/servlet/DispatchServlet.java:107:     * Processes GET request, which is delegated to another method based on path.
src/main/java/org/ecocean/servlet/DispatchServlet.java:113:            if (mn == null || !methodsGET.contains(mn)) {
src/main/java/org/ecocean/servlet/DispatchServlet.java:124:     * Processes POST request, which is delegated to another method based on path.
src/main/java/org/ecocean/servlet/DispatchServlet.java:130:            if (mn == null || !methodsPOST.contains(mn)) {
src/main/java/org/ecocean/servlet/DispatchServlet.java:141:     * Dispatches control to the supplied method.
src/main/java/org/ecocean/servlet/DispatchServlet.java:143:     * @param target servlet in which to look for target method
src/main/java/org/ecocean/servlet/DispatchServlet.java:144:     * @param method delegate method to which to dispatch control
src/main/java/org/ecocean/servlet/DispatchServlet.java:148:    protected void dispatchToDelegate(HttpServlet target, Method method, HttpServletRequest req,
src/main/java/org/ecocean/servlet/DispatchServlet.java:152:            method.invoke(target, new Object[] { req, res });
src/main/java/org/ecocean/servlet/DispatchServlet.java:154:            throw new DelegateNotFoundException("Couldn't access method", iax);
src/main/java/org/ecocean/servlet/DispatchServlet.java:166:     * Returns the delegate {@code Method} of the class instance with the specified method name. The delegate method must have the standard
src/main/java/org/ecocean/servlet/DispatchServlet.java:169:     * @param methodName name of delegate method
src/main/java/org/ecocean/servlet/DispatchServlet.java:171:    protected Method getDelegateMethod(String methodName)
src/main/java/org/ecocean/servlet/DispatchServlet.java:174:            return getClass().getMethod(methodName, SERVLET_ARGS);
src/main/java/org/ecocean/servlet/DispatchServlet.java:176:            throw new DelegateNotFoundException("Couldn't locate method: " + methodName, ex);
src/main/java/org/ecocean/servlet/DispatchServlet.java:178:            throw new DelegateNotFoundException("Couldn't locate method: " + methodName, ex);
src/main/java/org/ecocean/servlet/DispatchServlet.java:183:     * Method called when a delegate method cannot be found.
src/main/java/org/ecocean/servlet/DispatchServlet.java:193:     * Gets the name of the delegate dispatch method from the servlet request, or null.
src/main/java/org/ecocean/servlet/DispatchServlet.java:204: * Exception class thrown when the the servlet cannot find a delegate method.
src/main/java/org/ecocean/genetics/TissueSample.java:102:            paramValues += "     Preservation method: " + this.getPreservationMethod() + "<br />";
src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:385:            // overwrite=false on following fromEncs methods
src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:1196:    // Below methods are *not* static and work from column names rather than column numbers
src/main/java/org/ecocean/servlet/importer/StandardImport.java:764:            // overwrite=false on following fromEncs methods
src/main/java/org/ecocean/servlet/importer/StandardImport.java:2229:    // Below methods are *not* static and work from column names rather than column numbers
src/main/java/org/ecocean/servlet/importer/StandardImport.java:2560:    // cannot put this inside CellFeedback bc java inner classes are not allowed static methods or vars (this is stupid).
src/main/java/org/ecocean/servlet/importer/ImportTask.java:330:    // some (potentially) useful methods about our IA Task
src/main/java/org/ecocean/servlet/EncounterResetDate.java:66:                     * Old method of parsing fixMe.setDay(Integer.parseInt(request.getParameter("day")));
src/main/java/org/ecocean/servlet/EncounterResetDate.java:73:                    // new method using a datepicker
src/main/java/org/ecocean/servlet/importer/ImportSRGD.java:443:                                // OK to generically add it as the addEncounter() method will ignore it if already added to marked individual
src/main/resources/bundles/fr/encounter.properties:28:method = M\u00e9thode
src/main/java/org/ecocean/servlet/AddLabeledKeyword.java:77:        // if the method above succeeded, then it ended on a commit in a store method, so now need to beginDBTRansaction again
src/main/java/org/ecocean/servlet/EncounterSetString.java:81:        // grab the setter method
src/main/java/org/ecocean/servlet/EncounterSetString.java:101:                "EncounterSetString NoSuchMethodException: could not find setter method for fieldName: "
src/main/java/org/ecocean/servlet/importer/ImportExcelMetadata.java:455:                                // OK to generically add it as the addEncounter() method will ignore it if already added to marked individual
src/main/java/org/ecocean/genetics/FStatistics.java:12:    1. Instantiate a new FStatistics object for each allele 2. Use the loadIndividual(...) method to load the individuals and alleles from each of the
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:52:    // so we can bounce objects around between helper methods w/o worrying about shepherd permanence so much
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:954:        System.out.println("    SHROW-Proc done with method. Why you not printing?");
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:962:        System.out.println("    SHROW-Proc REALLY done with method. Why you not printing?");
src/main/java/org/ecocean/servlet/EncounterSetPatterningPassport.java:192:         * To convert the InputStream to String we use the Reader.read(char[] buffer) method. We iterate until the Reader return -1 which means
src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:596:    // Below methods are *not* static and work from column names rather than column numbers
src/main/java/org/ecocean/grid/ScanWorkItemCreationThread.java:43:     * main method of the shepherd thread
src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java:160:        // System.out.println("numSessions in method is: "+numSessions);
src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java:167:                // remember that the folowing methods use GregorianCalendar internally, so month needs to be -1 as Gregorian counts January as month 0
src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:86:             * See also the createFeatureType method below for another,
src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:229:                 * SimpleFeatureStore has a method to add features from a SimpleFeatureCollection object, so we use the ListFeatureCollection class to
src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:332:     * This method is an improvement on the code used in the main method above (where we used DataUtilities.createFeatureType) because we can set a
src/main/java/org/ecocean/grid/ScanTaskCleanupThread.java:22:     * main method of the shepherd thread
src/main/java/org/ecocean/servlet/IAGateway.java:621:        // __context and __baseUrl should be set -- this is done automatically in IAGateway, but if getting here by some other method, do the work!
src/main/java/org/ecocean/servlet/IAGateway.java:829:    // weirdly (via StartupWildbook) stuff put in the queue is processed by.... the method right above us!  :)  :(
src/main/java/org/ecocean/grid/ScanTask.java:171:    // use this method for returning groupings of workItems for more efficient processing
src/main/java/org/ecocean/servlet/LightRestServlet.java:92:     * Convenience method to get the next token after a "/".
src/main/java/org/ecocean/servlet/LightRestServlet.java:289:                            // errors from the google appengine may be raised when running queries
src/main/java/org/ecocean/servlet/LightRestServlet.java:563:            System.out.println("no such method??????????");
src/main/java/org/ecocean/servlet/LightRestServlet.java:566:        if (restAccess == null) return true; // if method doesnt exist, counts as good
src/main/java/org/ecocean/servlet/LightRestServlet.java:628:                " does not have decorateJson() method");
src/main/java/org/ecocean/servlet/LightRestServlet.java:646:// System.out.println("i guess " + obj.getClass() + " does not have sanitizeJson() method");
src/main/java/org/ecocean/servlet/ReactAppServlet.java:23: *   <li>Non-GET request method (POST, PUT, DELETE, etc.): preserve
src/main/java/org/ecocean/servlet/ReactAppServlet.java:75:     * methods reaching this servlet via the error-page dispatch are
src/main/java/org/ecocean/servlet/ReactAppServlet.java:87:            // React shell). Other methods get the standard 405.
src/main/java/org/ecocean/servlet/ReactAppServlet.java:95:        // Error-page dispatch. Decide based on path and method.
src/main/java/org/ecocean/servlet/ReactAppServlet.java:108:            // static assets, and non-GET methods. Write a minimal body
src/main/java/org/ecocean/servlet/export/ExportExcelFile.java:103:                    System.out.println("no such method for column " + mname + " (" + columns[i] +
src/main/java/org/ecocean/servlet/ScanAppletSupport.java:67:            // following two methods used for the old individual scan applet
src/main/java/org/ecocean/servlet/ScanAppletSupport.java:79:    } // end doPost method
src/main/java/org/ecocean/servlet/ScanAppletSupport.java:239:                "Caught an error when attempting to return data via the sendEncounterList method of scanAppletSupport servlet");
src/main/java/org/ecocean/servlet/ScanAppletSupport.java:321:        // end method
src/main/java/org/ecocean/grid/EncounterLite.java:255:            // start triangle creation in Groth method
src/main/java/org/ecocean/grid/EncounterLite.java:356:            // VmatchesA are the matched triangles of the new encounter whose spots were passed into this method
src/main/java/org/ecocean/grid/EncounterLite.java:467:                // method to compute logM using only the standard deviations of same sense triangles
src/main/java/org/ecocean/grid/EncounterLite.java:479:                // weighted method
src/main/java/org/ecocean/grid/EncounterLite.java:809:            // if newspots and spots were swapped at the beginning of this method to decrease processing time, we need to correct this for eventual
src/main/java/org/ecocean/grid/EncounterLite.java:844:            // start triangle creation in Groth method
src/main/java/org/ecocean/grid/EncounterLite.java:954:            // VmatchesA are the matched triangles of the new encounter whose spots were passed into this method
src/main/java/org/ecocean/grid/EncounterLite.java:1058:                // method to compute logM using only the standard deviations of same sense triangles
src/main/java/org/ecocean/grid/EncounterLite.java:1276:     * This method allows us to use the I3S match algorithm as well.
src/main/java/org/ecocean/grid/MatchGraphCreationThread.java:37:     * main method of the shepherd thread
src/main/java/org/ecocean/grid/GridManager.java:627:     * Convenience method to speed ScanWorkItemCreationThread by always maintaining and recalculating accurate counts of potential patterns to compare
src/main/java/org/ecocean/servlet/export/ExportColumn.java:101:     * This method uses colNum to place the value at the correct index,
src/main/java/org/ecocean/servlet/export/ExportColumn.java:111:    // this would be a static method of above subclass if java allowed that
src/main/java/org/ecocean/servlet/export/ExportColumn.java:135:            System.out.println("[ERROR]: newEasyColumn failed to find the method specified by " +
src/main/java/org/ecocean/grid/AppletWorkItemThread.java:28:        System.out.println("...in the run method of AppletWorkItemThread...");
src/main/java/org/ecocean/servlet/InterconnectSubmitSpots.java:194:                            "<form action=\"EncounterAddSpotFile\" method=\"post\" enctype=\"multipart/form-data\" name=\"addSpotsFile\">");
src/main/java/org/ecocean/servlet/InterconnectSubmitSpots.java:227:                            "<form action=\"EncounterAddSpotFile\" method=\"post\" enctype=\"multipart/form-data\" name=\"addSpotsFile\">");
src/main/java/org/ecocean/servlet/RestServlet.java:96:     * Convenience method to get the next token after a "/".
src/main/java/org/ecocean/servlet/RestServlet.java:337:                            // errors from the google appengine may be raised when running queries
src/main/java/org/ecocean/servlet/RestServlet.java:693:            System.out.println("no such method??????????");
src/main/java/org/ecocean/servlet/RestServlet.java:696:        if (restAccess == null) return true; // if method doesnt exist, counts as good
src/main/java/org/ecocean/servlet/RestServlet.java:748:                // System.out.println("i guess " + obj.getClass() + " does not have decorateJson() method");
src/main/java/org/ecocean/servlet/RestServlet.java:770:            // System.out.println("i guess " + obj.getClass() + " does not have sanitizeJson() method");
src/main/java/org/ecocean/grid/GridCleanupThread.java:24:     * main method of the shepherd thread
src/main/java/org/ecocean/grid/AppletHeartbeatThread.java:40:     * main method of the heartbeat thread
src/main/java/org/ecocean/grid/Geometry.java:16: * Collection of geometry utility methods. All methods are static.
src/main/java/org/ecocean/grid/Geometry.java:165:     * Find the point on the line defined by x0,y0,x1,y1 a given fraction from x0,y0. 2D version of method above..
src/main/java/org/ecocean/grid/Geometry.java:769:     * NOTE: This method is only guaranteed to work if the polygon is first preprocessed so that "unneccesary" vertices are removed (i.e vertices on
src/main/java/org/ecocean/grid/Geometry.java:836:     * Return the geometry of an ellipse based on its four top points. Integer domain. The method use the generic createEllipse() method for the main
src/main/java/org/ecocean/grid/Geometry.java:1186:     * Create geometry for an arrow along the specified line and with tip at x1,y1. See general method above.
src/main/resources/bundles/commonConfiguration.properties:335:#Sample preservation methods
src/main/resources/bundles/commonConfiguration.properties:336:#preservationMethod0=method0
src/main/resources/bundles/docker-config/server.xml:65:         This connector uses the NIO implementation with the JSSE engine. When
src/main/resources/bundles/docker-config/server.xml:66:         using the JSSE engine, the JSSE configuration attributes must be used.
src/main/resources/bundles/docker-config/server.xml:79:         APR/native implementation or the OpenSSL engine with NIO or NIO2 then
src/main/resources/bundles/docker-config/server.xml:103:         Documentation at /docs/config/engine.html -->
src/main/resources/bundles/es/encounter.properties:28:method = M\u00e9todo
src/main/resources/bundles/en/encounter.properties:28:method = Method
src/main/resources/bundles/en/encounter.properties:185:preservationMethod = Preservation method
src/main/resources/bundles/de/encounter.properties:28:method = Methode
src/main/resources/bundles/de/encounter.properties:184:preservationMethod = Konservierungsmethode

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '1,130p;240,330p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"index\\.knn|knn_vector|opensearch.*mapping|createIndex|ensureIndex|annotation\" src/main/java/org/ecocean/OpenSearch.java src/main/java/org/ecocean/Annotation.java devops/deploy devops/development src/main/resources -g '"'!**/*.md'"'" in /mnt/c/Wildbook-clean2
 succeeded in 216ms:
     1	package org.ecocean;
     2	
     3	import java.io.BufferedReader;
     4	import java.io.InputStreamReader;
     5	import java.io.IOException;
     6	import java.util.ArrayList;
     7	import java.util.Arrays;
     8	import java.util.Collection;
     9	import java.util.HashMap;
    10	import java.util.LinkedHashSet;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Properties;
    14	import java.util.Set;
    15	import javax.jdo.Query;
    16	import javax.net.ssl.SSLContext;
    17	import javax.net.ssl.SSLEngine;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.SystemValue;
    20	
    21	import org.ecocean.shepherd.core.Shepherd;
    22	import org.ecocean.shepherd.core.ShepherdProperties;
    23	
    24	import org.json.JSONArray;
    25	import org.json.JSONObject;
    26	
    27	import org.apache.http.HttpHost;
    28	import org.opensearch.client.json.jackson.JacksonJsonpMapper;
    29	import org.opensearch.client.Request;
    30	import org.opensearch.client.Response;
    31	import org.opensearch.client.ResponseException;
    32	import org.opensearch.client.RestClient;
    33	import org.opensearch.client.transport.rest_client.RestClientTransport;
    34	
    35	import org.opensearch.client.opensearch.core.IndexRequest;
    36	import org.opensearch.client.opensearch.indices.CreateIndexRequest;
    37	import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
    38	import org.opensearch.client.opensearch.indices.IndexSettings;
    39	import org.opensearch.client.opensearch.OpenSearchClient;
    40	import org.opensearch.client.transport.OpenSearchTransport;
    41	
    42	import java.lang.Runnable;
    43	import java.util.concurrent.Executors;
    44	import java.util.concurrent.ScheduledExecutorService;
    45	import java.util.concurrent.ScheduledFuture;
    46	import java.util.concurrent.TimeUnit;
    47	
    48	// https://opensearch.org/docs/latest/clients/java/
    49	// https://github.com/opensearch-project/opensearch-java/blob/main/USER_GUIDE.md
    50	
    51	public class OpenSearch {
    52	    public static OpenSearchClient client = null;
    53	    public static RestClient restClient = null;
    54	    public static Map<String, Boolean> INDEX_EXISTS_CACHE = new HashMap<String, Boolean>();
    55	    public static Map<String, String> PIT_CACHE = new HashMap<String, String>();
    56	    public static String SEARCH_SCROLL_TIME = (String)getConfigurationValue("searchScrollTime",
    57	        "10m");
    58	    public static String SEARCH_PIT_TIME = (String)getConfigurationValue("searchPitTime", "10m");
    59	    public static String INDEX_TIMESTAMP_PREFIX = "OpenSearch_index_timestamp_";
    60	    public static String[] VALID_INDICES = {
    61	        "encounter", "individual", "occurrence", "annotation", "media_asset"
    62	    };
    63	    public static int BACKGROUND_DELAY_MINUTES = (Integer)getConfigurationValue(
    64	        "backgroundDelayMinutes", 20);
    65	    public static int BACKGROUND_SLICE_SIZE = (Integer)getConfigurationValue("backgroundSliceSize",
    66	        2500);
    67	    public static int BACKGROUND_PERMISSIONS_MINUTES = (Integer)getConfigurationValue(
    68	        "backgroundPermissionsMinutes", 10);
    69	    public static int BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES = (Integer)getConfigurationValue(
    70	        "backgroundPermissionsMaxForceMinutes", 45);
    71	    public static String PERMISSIONS_LAST_RUN_KEY = "OpenSearch_permissions_last_run_timestamp";
    72	    public static String PERMISSIONS_NEEDED_KEY = "OpenSearch_permissions_needed";
    73	    public static String QUERY_STORAGE_DIR = "/tmp"; // FIXME
    74	    static String ACTIVE_TYPE_FOREGROUND = "opensearch_indexing_foreground";
    75	    static String ACTIVE_TYPE_BACKGROUND = "opensearch_indexing_background";
    76	
    77	    private int pitRetry = 0;
    78	
    79	    public OpenSearch() {
    80	        if (client != null) return;
    81	        // System.setProperty("javax.net.ssl.trustStore", "/full/path/to/keystore");
    82	        // System.setProperty("javax.net.ssl.trustStorePassword", "password-to-keystore");
    83	
    84	        // final HttpHost host = new HttpHost("http", "opensearch", 9200);
    85	        final HttpHost host = new HttpHost("opensearch", 9200, "http");
    86	/*
    87	    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    88	    // Only for demo purposes. Don't specify your credentials in code.
    89	    credentialsProvider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials("admin", "admin".toCharArray()));
    90	
    91	    final SSLContext sslcontext = SSLContextBuilder
    92	      .create()
    93	      .loadTrustMaterial(null, (chains, authType) -> true)
    94	      .build();
    95	 */
    96	
    97	        //////final ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(host);
    98	/*
    99	    builder.setHttpClientConfigCallback(httpClientBuilder -> {
   100	      final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
   101	        .setSslContext(sslcontext)
   102	        // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
   103	        .setTlsDetailsFactory(new Factory<SSLEngine, TlsDetails>() {
   104	          @Override
   105	          public TlsDetails create(final SSLEngine sslEngine) {
   106	            return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
   107	          }
   108	        })
   109	        .build();
   110	
   111	      final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
   112	        .create()
   113	        .setTlsStrategy(tlsStrategy)
   114	        .build();
   115	
   116	      return httpClientBuilder
   117	        .setDefaultCredentialsProvider(credentialsProvider)
   118	        .setConnectionManager(connectionManager);
   119	    });
   120	 */
   121	
   122	        /////final OpenSearchTransport transport = builder.build();
   123	        ///final RestClient restClient = RestClient.builder(host).build();
   124	        initializeClient(host);
   125	    }
   126	
   127	    public static void initializeClient(HttpHost host) {
   128	        restClient = RestClient.builder(host).build();
   129	        final OpenSearchTransport transport = new RestClientTransport(restClient,
   130	            new JacksonJsonpMapper());
   240	    }
   241	
   242	    public void deleteIndex(String indexName)
   243	    throws IOException {
   244	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   245	        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index(
   246	            indexName).build();
   247	
   248	        // DeleteIndexResponse deleteIndexResponse = client.indices().delete(deleteIndexRequest);
   249	        client.indices().delete(deleteIndexRequest);
   250	        INDEX_EXISTS_CACHE.remove(indexName);
   251	        System.out.println(indexName + " OpenSearch index deleted");
   252	    }
   253	
   254	    public boolean existsIndex(String indexName) {
   255	        if (!isValidIndexName(indexName)) return false;
   256	        if (INDEX_EXISTS_CACHE.get(indexName) != null) return true;
   257	        try {
   258	            client.indices().get(i -> i.index(indexName));
   259	            INDEX_EXISTS_CACHE.put(indexName, true);
   260	            return true;
   261	        } catch (Exception ex) {
   262	            System.out.println("existsIndex(" + indexName + "): " + ex.toString());
   263	        }
   264	        return false;
   265	    }
   266	
   267	    public void index(String indexName, Base obj)
   268	    throws IOException {
   269	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   270	        String id = obj.getId();
   271	        if (!Util.stringExists(id))
   272	            throw new RuntimeException("must have id property to index: " + obj);
   273	        ensureIndex(indexName, obj.opensearchMapping());
   274	        IndexRequest<Base> indexRequest = new IndexRequest.Builder<Base>()
   275	                .index(indexName)
   276	                .id(id)
   277	                .document(obj)
   278	                .build();
   279	        client.index(indexRequest);
   280	/*
   281	        IndexResponse indexResponse = client.index(indexRequest);
   282	        System.out.println(id + ": " + String.format("Document %s.",
   283	            indexResponse.result().toString().toLowerCase()));
   284	 */
   285	    }
   286	
   287	/*
   288	    a mapping cannot be changed after data has been indexed, so we allow mapping to be made
   289	    only right after index is created. any properties we do not define will be autoset upon first document creation.
   290	    https://opensearch.org/docs/latest/api-reference/index-apis/put-mapping/
   291	 */
   292	    private JSONObject createMapping(String indexName, final JSONObject mapProperties)
   293	    throws IOException {
   294	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   295	        if (!existsIndex(indexName)) throw new IOException("non-existent index: " + indexName);
   296	        if (mapProperties == null) return null;
   297	        JSONObject set = new JSONObject();
   298	        set.put("properties", mapProperties);
   299	        Request req = new Request("PUT", indexName + "/_mapping");
   300	        req.setJsonEntity(set.toString());
   301	        String rtn = getRestResponse(req);
   302	        System.out.println("createMapping(" + indexName + "): " + set + " => " + rtn);
   303	        return set;
   304	    }
   305	
   306	/*
   307	    // https://github.com/opensearch-project/opensearch-java/issues/824
   308	    // https://forum.opensearch.org/t/how-can-i-create-a-simple-match-query-using-java-client/7748/2
   309	    // https://forum.opensearch.org/t/java-client-searchrequest-query-building-for-neural-plugin/15895/4
   310	    public List<Base> queryx(String indexName, String query)
   311	    throws IOException {
   312	        List<Base> results = new ArrayList<Base>();
   313	        final SearchRequest request = new SearchRequest.Builder()
   314	                .index(indexName)
   315	                .from(0)
   316	                .size(200)
   317	            // .sort(sortOptions)
   318	                .trackScores(true)
   319	            // .query(q -> q.queryString("{}"))
   320	                .build();
   321	
   322	   // Unnecessary casting/deserialisation imo
   323	   // final var response = openSearchClient.search(request, ObjectNode.class);
   324	
   325	   // Unnecessary conversion
   326	   // final var str = objectMapper.writeValueAsString(response);
   327	
   328	        // SearchResponse<Base> searchResponse = client.search(request, Base.class);
   329	        SearchResponse<Base> searchResponse = client.search(s -> s.index(indexName), Base.class);
   330	

 succeeded in 557ms:
src/main/java/org/ecocean/OpenSearch.java:61:        "encounter", "individual", "occurrence", "annotation", "media_asset"
src/main/java/org/ecocean/OpenSearch.java:211:    public void createIndex(String indexName, JSONObject mapping)
src/main/java/org/ecocean/OpenSearch.java:216:        if (indexName.equals("annotation")) {
src/main/java/org/ecocean/OpenSearch.java:220:        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
src/main/java/org/ecocean/OpenSearch.java:223:        client.indices().create(createIndexRequest);
src/main/java/org/ecocean/OpenSearch.java:235:    public void ensureIndex(String indexName, JSONObject mapping)
src/main/java/org/ecocean/OpenSearch.java:239:        createIndex(indexName, mapping);
src/main/java/org/ecocean/OpenSearch.java:273:        ensureIndex(indexName, obj.opensearchMapping());
src/main/java/org/ecocean/OpenSearch.java:290:    https://opensearch.org/docs/latest/api-reference/index-apis/put-mapping/
src/main/java/org/ecocean/OpenSearch.java:479:    // index that doesn't yet contain the freshly-written annotations.
src/main/java/org/ecocean/OpenSearch.java:553:    // Stronger visibility predicate than waitForVisibility for the annotation
src/main/java/org/ecocean/OpenSearch.java:589:        indexRefresh("annotation");
src/main/java/org/ecocean/OpenSearch.java:596:            int seen = queryCount("annotation", query);
src/main/java/org/ecocean/OpenSearch.java:611:    // matching the annotation-matchable predicate documented on
src/main/java/org/ecocean/OpenSearch.java:931:        if ("annotation".equals(indexName) || "individual".equals(indexName)) return sourceDoc;
src/main/java/org/ecocean/Annotation.java:62:    // manually-drawn annotations.
src/main/java/org/ecocean/Annotation.java:68:    // about ml-service-created annotations so HotSpotter remains available.
src/main/java/org/ecocean/Annotation.java:70:    //   null  — legacy annotation (column is new; starts null on existing
src/main/java/org/ecocean/Annotation.java:74:    //   false — new ml-service annotation awaiting WBIA registration.
src/main/java/org/ecocean/Annotation.java:79:    // new ml-service annotations.
src/main/java/org/ecocean/Annotation.java:99:    // quality indicates the fidelity of the annotation, e.g. the overall image quality of a picture.
src/main/java/org/ecocean/Annotation.java:155:    @Override public String opensearchIndexName() { return "annotation"; }
src/main/java/org/ecocean/Annotation.java:199:        embVect.put("type", "knn_vector");
src/main/java/org/ecocean/Annotation.java:603:     * needs this column to be populated on ml-service-created annotations.
src/main/java/org/ecocean/Annotation.java:618:            System.out.println("WARNING: annotation " + this.getId() +
src/main/java/org/ecocean/Annotation.java:867:        jobj.put("annotationIsOfInterest", this.getIsOfInterest());
src/main/java/org/ecocean/Annotation.java:956:        // returned every annotation with matchAgainst=T and an acmId
src/main/java/org/ecocean/Annotation.java:1152:                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
src/main/java/org/ecocean/Annotation.java:1154:            os.deletePit("annotation");
src/main/java/org/ecocean/Annotation.java:1155:            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
src/main/java/org/ecocean/Annotation.java:1245:    // finds annotations based on embedding vector matches
src/main/java/org/ecocean/Annotation.java:1264:                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
src/main/java/org/ecocean/Annotation.java:1266:            os.deletePit("annotation");
src/main/java/org/ecocean/Annotation.java:1267:            queryRes = os.queryPit("annotation", matchQuery, 0, pageSize, null, null);
src/main/java/org/ecocean/Annotation.java:1356:    // additionally, if one is a trivial annotation, we drop it after.  if no siblings are found, we create
src/main/java/org/ecocean/Annotation.java:1387:        // we currently now allow (via import!) there to be *more than one trivial annotation* on a media asset!
src/main/java/org/ecocean/Annotation.java:1532:            // should this be locked down tighter as to who can add an annotation to an encounter?
src/main/java/org/ecocean/Annotation.java:1587:            // we would expect at least a trivial annotation, so if annots>=2, we know we need to clone
src/main/java/org/ecocean/Annotation.java:1598:                // exception case - if there is only one annotation and it is a part
src/main/java/org/ecocean/Annotation.java:1604:                // exception case - if there is only one annotation and it is a part
src/main/java/org/ecocean/Annotation.java:1692:                "Annotation.createFromApi(): no trivial annotation found to remove from " + ma);
src/main/java/org/ecocean/Annotation.java:1920:            "\" && annotations.contains(annot1) && annot1.acmId == \"" + annot.getAcmId() +
src/main/resources/openapi.yaml:39:    description: Image annotation management
src/main/resources/openapi.yaml:323:          enum: [encounters, individuals, occurrences, annotations]
src/main/resources/openapi.yaml:376:        annotations:
src/main/resources/openapi.yaml:437:            measurements, annotations, and standard encounter fields (genus, specificEpithet,
src/main/resources/openapi.yaml:912:  /api/v3/annotations:
src/main/resources/openapi.yaml:916:      summary: Create new annotation
src/main/resources/openapi.yaml:917:      description: Create a new annotation (requires authentication)
src/main/resources/openapi.yaml:939:  /api/v3/annotations/{annotationId}:
src/main/resources/openapi.yaml:943:      summary: Get annotation details
src/main/resources/openapi.yaml:944:      description: Retrieve details of a specific annotation
src/main/resources/openapi.yaml:949:        - name: annotationId
src/main/resources/openapi.yaml:974:      description: Get information about a specific media asset including annotations
src/main/resources/openapi.yaml:1057:        Note: annotation index requires admin privileges.
src/main/resources/openapi.yaml:1067:            enum: [encounter, individual, occurrence, annotation, sighting, user]
src/main/resources/openapi.yaml:1118:          description: Forbidden (admin required for annotation index)
src/main/resources/org/ecocean/media/package.jdo:42:		<field name="annotations" mapped-by="mediaAsset">
src/main/resources/org/ecocean/media/package.jdo:123:		<field name="annotation" />
src/main/resources/bundles/en/individualSearch.properties:149:iaClassFilter = Has at least one annotation of class (a logical OR for multi-select):
src/main/resources/bundles/en/individualSearch.properties:150:viewpointFilter = Has at least one annotation with viewpoint (a logical OR for multi-select):
src/main/resources/org/ecocean/ia/package.jdo:103:      		<field name="annotation" persistence-modifier="persistent" element-type="org.ecocean.Annotation">
src/main/resources/bundles/en/searchResultsAnalysis.properties:64:numAnnotations = Number annotations from machine learning:
src/main/resources/bundles/en/encounterSearch.properties:137:iaClassFilter = Has at least one annotation of class (a logical OR for multi-select):
src/main/resources/bundles/en/encounterSearch.properties:138:viewpointFilter = Has at least one annotation with viewpoint (a logical OR for multi-select):
src/main/resources/org/ecocean/package.jdo:422:      <field name="annotations" persistence-modifier="persistent" default-fetch-group="true">
src/main/resources/org/ecocean/package.jdo:593:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
src/main/resources/org/ecocean/package.jdo:617:	<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
src/main/resources/org/ecocean/package.jdo:1043:		     to support idempotency lookup of (annotation, method, methodVersion).
src/main/resources/org/ecocean/package.jdo:1056:		<field name="annotation" dependent-element="false" >
src/main/resources/bundles/en/encounter.properties:341:annotationID = Annotation ID: 
src/main/resources/bundles/fr/individualSearch.properties:149:iaClassFilter = Poss\u00e8de au moins une annotation de classe (un OU logique pour la s\u00e9lection multiple):
src/main/resources/bundles/fr/individualSearch.properties:150:viewpointFilter = Poss\u00e8de au moins une annotation avec point de vue (un OU logique pour la s\u00e9lection multiple):
src/main/resources/bundles/fr/dataIntegrity.properties:3:check4annots = Rechercher des annotations avec plusieurs ID individuels
src/main/resources/bundles/fr/dataIntegrity.properties:4:description0 = Les annotations attribu\u00e9es \u00e0 deux ou plusieurs personnes diff\u00e9rentes repr\u00e9sentent des erreurs de donn\u00e9es qui peuvent entra\u00eener l'\u00e9chec de la correspondance ou entra\u00eener des individus faussement fusionn\u00e9s.
src/main/resources/bundles/fr/searchResultsAnalysis.properties:64:numAnnotations = Nombre d'annotations issues du machine learning:
src/main/resources/bundles/fr/encounterSearch.properties:137:iaClassFilter = Poss\u00e8de au moins une annotation de classe (un OU logique pour la s\u00e9lection multiple):
src/main/resources/bundles/fr/encounterSearch.properties:138:viewpointFilter = Poss\u00e8de au moins une annotation avec point de vue (un OU logique pour la s\u00e9lection multiple):
src/main/resources/bundles/fr/encounter.properties:341:annotationID = Annotation ID: 
src/main/resources/bundles/it/encounter.properties:341:annotationID = ID annotazione: 
src/main/resources/bundles/fr/projectList.properties:2:PercentAnnotations = Pourcentage d'annotations identifi\u00E9es
src/main/resources/bundles/de/encounter.properties:341:annotationID = Anmerkungs-ID: 
src/main/resources/bundles/es/encounter.properties:341:annotationID = Annotation ID: 

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '205,238p'" in /mnt/c/Wildbook-clean2
 succeeded in 301ms:
   205	        } finally {
   206	            myShepherd.rollbackAndClose();
   207	            unsetActiveIndexingBackground();
   208	        }
   209	    }
   210	
   211	    public void createIndex(String indexName, JSONObject mapping)
   212	    throws IOException {
   213	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   214	        IndexSettings indexSettings = null;
   215	        // a little hacky but meh
   216	        if (indexName.equals("annotation")) {
   217	            // also? "knn.algo_param.ef_search": 100
   218	            indexSettings = IndexSettings.of(is -> is.knn(true));
   219	        }
   220	        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
   221	            indexName).settings(indexSettings).build();
   222	
   223	        client.indices().create(createIndexRequest);
   224	        // TODO fold in this settings-change into indexSettings above
   225	        indexClose(indexName);
   226	        JSONObject analysis = new JSONObject(
   227	            "{\"analysis\": {\"normalizer\": {\"wildbook_keyword_normalizer\": {\"type\": \"custom\", \"char_filter\": [], \"filter\": [\"lowercase\", \"asciifolding\"]} } } }");
   228	        putSettings(indexName, analysis);
   229	        createMapping(indexName, mapping);
   230	        indexOpen(indexName);
   231	        INDEX_EXISTS_CACHE.put(indexName, true);
   232	        System.out.println(indexName + " OpenSearch index created");
   233	    }
   234	
   235	    public void ensureIndex(String indexName, JSONObject mapping)
   236	    throws IOException {
   237	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   238	        if (existsIndex(indexName)) return;

web search: 
web search: 'Specifying the space type'
web search: 
web search: https://docs.opensearch.org/3.1/field-types/supported-field-types/knn-methods-engines/
web search: 
web search: 'Default' in https://docs.opensearch.org/3.1/field-types/supported-field-types/knn-methods-engines/
web search: 
web search: https://docs.opensearch.org/3.1/field-types/supported-field-types/knn-vector/
web search: 
web search: 'method' in https://docs.opensearch.org/3.1/field-types/supported-field-types/knn-vector/
web search: 
web search: https://docs.opensearch.org/2.15/search-plugins/knn/approximate-knn/
web search: 
web search: 'cosinesimil' in https://docs.opensearch.org/2.15/search-plugins/knn/approximate-knn/
web search: 
web search: https://docs.opensearch.org/2.15/search-plugins/knn/knn-index/
web search: 
web search: 'engine' in https://docs.opensearch.org/2.15/search-plugins/knn/knn-index/
codex
**Findings**

- **Major:** The score conversion is correct for OpenSearch 3.1/Lucene-style `cosinesimil`, but the code applies it unconditionally in [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1283). Wildbook’s stated stack still includes OpenSearch 2.15, and the local annotation mapping only sets `type`, `dimension`, and `space_type` without pinning a Lucene `method.engine` in [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:197). On OpenSearch 2.15, the docs distinguish engines: NMSLIB/Faiss use `score = 1 / (1 + d)`, while Lucene uses `score = (2 - d) / 2`. Since `d = 1 - cos`, `2*x - 1` is only the Lucene/3.1 formula. This will report wrong MiewID-parity scores on any 2.15 NMSLIB/Faiss-backed annotation index. Fix by either explicitly pinning/requiring Lucene for the annotation vector mapping and documenting/reindexing that contract, or make the score conversion engine/version-aware.

- **Medium:** The new tests cover only [openSearchScoreToCosine](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1238), not the query-shape half of the fix. A regression that moves `embeddings.method` / `methodVersion` back into nested `must` at [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1211) would pass [AnnotationMiewIDScoreTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:20) while reintroducing the `+2.0` score offset. Add a focused `getMatchQuery` shape test asserting nested `must` contains only `knn` and nested `filter` contains method/version terms.

**Notes**

For OpenSearch 3.1, your `(1 + cos) / 2` assumption checks out: the 3.1 docs define `cosinesimil` distance as `1 - cos` and score as `(2 - d) / 2`, which simplifies to `(1 + cos) / 2`. OpenSearch 2.15 docs are engine-dependent, which is the main risk above.

I did not write files or run the test suite. I did run read-only inspection commands, and `git diff --check` was clean for the two reviewed files; tracked [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1) is `i/lf w/lf`.

Sources: OpenSearch 3.1 Spaces docs, OpenSearch 2.15 k-NN docs, OpenSearch 2.15 method defaults docs.
tokens used
206,516
**Findings**

- **Major:** The score conversion is correct for OpenSearch 3.1/Lucene-style `cosinesimil`, but the code applies it unconditionally in [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1283). Wildbook’s stated stack still includes OpenSearch 2.15, and the local annotation mapping only sets `type`, `dimension`, and `space_type` without pinning a Lucene `method.engine` in [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:197). On OpenSearch 2.15, the docs distinguish engines: NMSLIB/Faiss use `score = 1 / (1 + d)`, while Lucene uses `score = (2 - d) / 2`. Since `d = 1 - cos`, `2*x - 1` is only the Lucene/3.1 formula. This will report wrong MiewID-parity scores on any 2.15 NMSLIB/Faiss-backed annotation index. Fix by either explicitly pinning/requiring Lucene for the annotation vector mapping and documenting/reindexing that contract, or make the score conversion engine/version-aware.

- **Medium:** The new tests cover only [openSearchScoreToCosine](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1238), not the query-shape half of the fix. A regression that moves `embeddings.method` / `methodVersion` back into nested `must` at [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1211) would pass [AnnotationMiewIDScoreTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java:20) while reintroducing the `+2.0` score offset. Add a focused `getMatchQuery` shape test asserting nested `must` contains only `knn` and nested `filter` contains method/version terms.

**Notes**

For OpenSearch 3.1, your `(1 + cos) / 2` assumption checks out: the 3.1 docs define `cosinesimil` distance as `1 - cos` and score as `(2 - d) / 2`, which simplifies to `(1 + cos) / 2`. OpenSearch 2.15 docs are engine-dependent, which is the main risk above.

I did not write files or run the test suite. I did run read-only inspection commands, and `git diff --check` was clean for the two reviewed files; tracked [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1) is `i/lf w/lf`.

Sources: OpenSearch 3.1 Spaces docs, OpenSearch 2.15 k-NN docs, OpenSearch 2.15 method defaults docs.
