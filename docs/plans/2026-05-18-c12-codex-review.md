OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e409f-91f6-7663-8d20-f1952aff9641
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

# Codex code-review: Track 2 C12 — PairX bbox clamp + shared-array fix

Follow-up to the Track 1+2 work. Live integration on amphibian-reptile
revealed the empty-results symptom was caused by PairX `/explain/`
HTTP 400 rejections leaving `ASSET_ID_OID=NULL` on every prospect,
which the React UI hides. Diagnosis confirmed by SQL:

- 4 MatchResult rows landed for the test import (1-3 prospects each,
  scores 2.76-3.00, cross-references symmetric — Track 1+2 working).
- All 9 prospects have `ASSET_ID_OID=NULL`.
- UI for a subTask with prospects renders empty.

Two bugs found in `MatchResult.createInspectionPairxAsset`:

**Bug 1 (load-bearing):** the bbox payload reused a single JSONArray
for both `bb1` and `bb2`, so `tmpArr.put(0, ann2.getBbox())` after
`payload.put("bb1", tmpArr)` mutated the shared array and made
`bb2 == bb1`. The log payloads confirmed identical `bb1`/`bb2` in
every POST — PairX was comparing image1[bbox=ann2's] vs
image2[bbox=ann2's]. Fix: build two separate outer JSONArrays.

**Bug 2 (the actual 400 cause):** ml-service detections sometimes
produce bboxes that extend past the image edge (negative x/y values
like `[-80, 42, 1786, 2228]`). `/explain/` rejects with
`400: Bounding box values should be positive`. Fix: `clampBbox`
helper transforms negative x to 0 and shrinks w by the same amount
(and same for y/h), preserving the in-image portion of the box.

## Diff

diff --git a/src/main/java/org/ecocean/ia/MatchResult.java b/src/main/java/org/ecocean/ia/MatchResult.java
index 79a85e296..5592cc87f 100644
--- a/src/main/java/org/ecocean/ia/MatchResult.java
+++ b/src/main/java/org/ecocean/ia/MatchResult.java
@@ -330,12 +330,31 @@ public class MatchResult implements java.io.Serializable {
         payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
         payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
         payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
-        // this needs an array of array(s)
-        JSONArray tmpArr = new JSONArray();
-        tmpArr.put(0, ann1.getBbox());
-        payload.put("bb1", tmpArr);
-        tmpArr.put(0, ann2.getBbox());
-        payload.put("bb2", tmpArr);
+        // bb1 / bb2 each need an outer array of [x, y, w, h] arrays.
+        //
+        // Two bugs were producing empty match-result UI before this fix
+        // (empty-match-prospects design Track 2 C12):
+        //
+        //   1. The previous implementation reused a single JSONArray for
+        //      both bb1 and bb2, so both keys ended up referencing
+        //      ann2's bbox — PairX was comparing image1[bbox=ann2's]
+        //      vs image2[bbox=ann2's]. Now we build separate outer
+        //      arrays.
+        //
+        //   2. ml-service detections sometimes produce bboxes that
+        //      extend past the image edge (negative x or y values
+        //      observed on amphibian-reptile.wildbook.org). The
+        //      /explain/ endpoint rejects those with HTTP 400
+        //      "Bounding box values should be positive", which caused
+        //      ASSET_ID_OID=NULL on every prospect and the UI hid the
+        //      whole MatchResult. clampBbox clamps to the in-image
+        //      portion: x=max(0,x), w=w+min(0,x_orig) so the box still
+        //      covers the same pixels that the embedding model
+        //      actually saw after image-edge cropping.
+        payload.put("bb1", new JSONArray().put(bboxToJsonArray(
+            clampBbox(ann1.getBbox()))));
+        payload.put("bb2", new JSONArray().put(bboxToJsonArray(
+            clampBbox(ann2.getBbox()))));
 
         // get the image data from pairx endpoint
         JSONObject res = null;
@@ -375,6 +394,45 @@ public class MatchResult implements java.io.Serializable {
         return null;
     }
 
+    /**
+     * Clamp negative bbox values to the in-image portion. ml-service
+     * detections sometimes produce bboxes whose top-left extends past
+     * the image edge (e.g., {@code [-80, 42, 1786, 2228]}); the PairX
+     * {@code /explain/} endpoint rejects those with HTTP 400. Shifting
+     * x or y to 0 alone would translate the box; we also shrink the
+     * dimension by the same amount so the result covers the same in-
+     * image pixels the embedding model actually consumed after
+     * edge-cropping.
+     *
+     * <p>Package-visible for unit testing. (Empty-match-prospects
+     * design Track 2 C12.)</p>
+     */
+    static int[] clampBbox(int[] bbox) {
+        if (bbox == null || bbox.length < 4) return bbox;
+        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];
+        if (x < 0) {
+            w = Math.max(0, w + x);
+            x = 0;
+        }
+        if (y < 0) {
+            h = Math.max(0, h + y);
+            y = 0;
+        }
+        return new int[] { x, y, w, h };
+    }
+
+    /**
+     * Convert an int[] bbox to a JSONArray of ints. {@code JSONArray.put(Object)}
+     * doesn't auto-convert int[] reliably across org.json versions, so we
+     * box explicitly.
+     */
+    static JSONArray bboxToJsonArray(int[] bbox) {
+        JSONArray arr = new JSONArray();
+        if (bbox == null) return arr;
+        for (int v : bbox) arr.put(v);
+        return arr;
+    }
+
     public static URL _getPairxUrl(String txStr)
     throws IOException {
         if (txStr == null) throw new IOException("passed null taxonomy");


## New test file:

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the bbox-clamp + JSON-conversion helpers added to
 * {@link MatchResult} to fix the PairX {@code /explain/} 400
 * rejections and the shared-tmpArr bug. (Empty-match-prospects
 * design Track 2 C12.)
 */
class MatchResultClampBboxTest {

    // --- clampBbox -------------------------------------------------------

    @Test void clampBbox_passesThroughPositiveValues() {
        int[] in = { 10, 20, 100, 200 };
        assertArrayEquals(new int[] { 10, 20, 100, 200 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsNegativeX_shrinksWidth() {
        // x=-80 means box starts 80px to the left of the image; the
        // in-image portion is x=0, w=1786-80=1706.
        int[] in = { -80, 42, 1786, 2228 };
        assertArrayEquals(new int[] { 0, 42, 1706, 2228 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsNegativeY_shrinksHeight() {
        int[] in = { 10, -50, 100, 300 };
        assertArrayEquals(new int[] { 10, 0, 100, 250 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsBothXAndY_independently() {
        int[] in = { -44, -27, 2072, 2406 };
        assertArrayEquals(new int[] { 0, 0, 2028, 2379 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsWidthFloorToZero_whenAbsXExceedsW() {
        // Pathological: x=-200, w=100 means the box is entirely off-image.
        // Clamp to a zero-width box at the origin rather than a negative
        // width that would also be rejected.
        int[] in = { -200, 50, 100, 80 };
        assertArrayEquals(new int[] { 0, 50, 0, 80 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsHeightFloorToZero_whenAbsYExceedsH() {
        int[] in = { 10, -300, 100, 100 };
        assertArrayEquals(new int[] { 10, 0, 100, 0 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_returnsInput_whenNull() {
        assertNull(MatchResult.clampBbox(null));
    }

    @Test void clampBbox_returnsInput_whenShorterThanFour() {
        int[] in = { 1, 2, 3 };
        assertArrayEquals(in, MatchResult.clampBbox(in));
    }

    @Test void clampBbox_doesNotMutateInput() {
        int[] in = { -80, 42, 1786, 2228 };
        MatchResult.clampBbox(in);
        // Caller's array must be untouched; clamp returns a fresh copy.
        assertArrayEquals(new int[] { -80, 42, 1786, 2228 }, in);
    }

    // --- bboxToJsonArray -------------------------------------------------

    @Test void bboxToJsonArray_buildsArrayFromInts() {
        JSONArray out = MatchResult.bboxToJsonArray(new int[] { 1, 2, 3, 4 });
        assertEquals(4, out.length());
        assertEquals(1, out.getInt(0));
        assertEquals(2, out.getInt(1));
        assertEquals(3, out.getInt(2));
        assertEquals(4, out.getInt(3));
    }

    @Test void bboxToJsonArray_emptyForNullInput() {
        JSONArray out = MatchResult.bboxToJsonArray(null);
        assertEquals(0, out.length());
    }

    @Test void bboxToJsonArray_handlesEmptyArray() {
        JSONArray out = MatchResult.bboxToJsonArray(new int[0]);
        assertEquals(0, out.length());
    }
}
```


## Validation

- MatchResultClampBboxTest: 12/12 covering clampBbox and bboxToJsonArray.
- MatchResultTest: 3/3 (existing, no regression).
- LF-normalized.

## Review scope

- Is "shrink width/height by the overflow amount" the right clamp
  semantics for PairX? Alternative: just clamp position to 0 and
  leave w/h, which translates the box rather than shrinking.
  Reasoning for shrink: the embedding model would have cropped at
  image edges, so the in-image portion is what was actually scored.
- Is `bboxToJsonArray` strictly needed? `JSONArray.put(Object)`
  auto-converts arrays in some org.json versions. Building explicitly
  is defensive — but could be over-engineering.
- The bug-1 fix (separate JSONArrays) preserves the original
  outer-array shape PairX expects (`[[x, y, w, h]]`).
- I left the existing try/catch on the POST untouched — PairX failure
  is still non-blocking; this fix just stops the failure from
  happening in the first place.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll verify the actual files and surrounding code read-only, then give review findings with line references. I’m not going to touch the worktree.
exec
/bin/bash -lc 'git diff -- src/main/java/org/ecocean/ia/MatchResult.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg --files | rg '("'^|/)MatchResult.*Test'"\\.java"'$|(''^|/)MatchResult'"\\.java"'$'"'" in /mnt/c/Wildbook-clean2
 succeeded in 817ms:
src/main/java/org/ecocean/ia/MatchResult.java
src/test/java/org/ecocean/MatchResultTest.java
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java

 succeeded in 885ms:
diff --git a/src/main/java/org/ecocean/ia/MatchResult.java b/src/main/java/org/ecocean/ia/MatchResult.java
index 79a85e296..5592cc87f 100644
--- a/src/main/java/org/ecocean/ia/MatchResult.java
+++ b/src/main/java/org/ecocean/ia/MatchResult.java
@@ -330,12 +330,31 @@ public class MatchResult implements java.io.Serializable {
         payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
         payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
         payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
-        // this needs an array of array(s)
-        JSONArray tmpArr = new JSONArray();
-        tmpArr.put(0, ann1.getBbox());
-        payload.put("bb1", tmpArr);
-        tmpArr.put(0, ann2.getBbox());
-        payload.put("bb2", tmpArr);
+        // bb1 / bb2 each need an outer array of [x, y, w, h] arrays.
+        //
+        // Two bugs were producing empty match-result UI before this fix
+        // (empty-match-prospects design Track 2 C12):
+        //
+        //   1. The previous implementation reused a single JSONArray for
+        //      both bb1 and bb2, so both keys ended up referencing
+        //      ann2's bbox — PairX was comparing image1[bbox=ann2's]
+        //      vs image2[bbox=ann2's]. Now we build separate outer
+        //      arrays.
+        //
+        //   2. ml-service detections sometimes produce bboxes that
+        //      extend past the image edge (negative x or y values
+        //      observed on amphibian-reptile.wildbook.org). The
+        //      /explain/ endpoint rejects those with HTTP 400
+        //      "Bounding box values should be positive", which caused
+        //      ASSET_ID_OID=NULL on every prospect and the UI hid the
+        //      whole MatchResult. clampBbox clamps to the in-image
+        //      portion: x=max(0,x), w=w+min(0,x_orig) so the box still
+        //      covers the same pixels that the embedding model
+        //      actually saw after image-edge cropping.
+        payload.put("bb1", new JSONArray().put(bboxToJsonArray(
+            clampBbox(ann1.getBbox()))));
+        payload.put("bb2", new JSONArray().put(bboxToJsonArray(
+            clampBbox(ann2.getBbox()))));
 
         // get the image data from pairx endpoint
         JSONObject res = null;
@@ -375,6 +394,45 @@ public class MatchResult implements java.io.Serializable {
         return null;
     }
 
+    /**
+     * Clamp negative bbox values to the in-image portion. ml-service
+     * detections sometimes produce bboxes whose top-left extends past
+     * the image edge (e.g., {@code [-80, 42, 1786, 2228]}); the PairX
+     * {@code /explain/} endpoint rejects those with HTTP 400. Shifting
+     * x or y to 0 alone would translate the box; we also shrink the
+     * dimension by the same amount so the result covers the same in-
+     * image pixels the embedding model actually consumed after
+     * edge-cropping.
+     *
+     * <p>Package-visible for unit testing. (Empty-match-prospects
+     * design Track 2 C12.)</p>
+     */
+    static int[] clampBbox(int[] bbox) {
+        if (bbox == null || bbox.length < 4) return bbox;
+        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];
+        if (x < 0) {
+            w = Math.max(0, w + x);
+            x = 0;
+        }
+        if (y < 0) {
+            h = Math.max(0, h + y);
+            y = 0;
+        }
+        return new int[] { x, y, w, h };
+    }
+
+    /**
+     * Convert an int[] bbox to a JSONArray of ints. {@code JSONArray.put(Object)}
+     * doesn't auto-convert int[] reliably across org.json versions, so we
+     * box explicitly.
+     */
+    static JSONArray bboxToJsonArray(int[] bbox) {
+        JSONArray arr = new JSONArray();
+        if (bbox == null) return arr;
+        for (int v : bbox) arr.put(v);
+        return arr;
+    }
+
     public static URL _getPairxUrl(String txStr)
     throws IOException {
         if (txStr == null) throw new IOException("passed null taxonomy");

codex
The repo diff matches the bundle for `MatchResult.java`. I’m checking the test placement, package conventions, and nearby PairX request handling next because those are the most likely review-sensitive spots here.
exec
/bin/bash -lc 'git diff -- src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java src/test/java/org/ecocean/MatchResultTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '280,430p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/ia/MatchResult.java src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 210ms:
     1	package org.ecocean.ia;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertArrayEquals;
     4	import static org.junit.jupiter.api.Assertions.assertEquals;
     5	import static org.junit.jupiter.api.Assertions.assertNull;
     6	
     7	import org.json.JSONArray;
     8	import org.junit.jupiter.api.Test;
     9	
    10	/**
    11	 * Unit tests for the bbox-clamp + JSON-conversion helpers added to
    12	 * {@link MatchResult} to fix the PairX {@code /explain/} 400
    13	 * rejections and the shared-tmpArr bug. (Empty-match-prospects
    14	 * design Track 2 C12.)
    15	 */
    16	class MatchResultClampBboxTest {
    17	
    18	    // --- clampBbox -------------------------------------------------------
    19	
    20	    @Test void clampBbox_passesThroughPositiveValues() {
    21	        int[] in = { 10, 20, 100, 200 };
    22	        assertArrayEquals(new int[] { 10, 20, 100, 200 },
    23	            MatchResult.clampBbox(in));
    24	    }
    25	
    26	    @Test void clampBbox_clampsNegativeX_shrinksWidth() {
    27	        // x=-80 means box starts 80px to the left of the image; the
    28	        // in-image portion is x=0, w=1786-80=1706.
    29	        int[] in = { -80, 42, 1786, 2228 };
    30	        assertArrayEquals(new int[] { 0, 42, 1706, 2228 },
    31	            MatchResult.clampBbox(in));
    32	    }
    33	
    34	    @Test void clampBbox_clampsNegativeY_shrinksHeight() {
    35	        int[] in = { 10, -50, 100, 300 };
    36	        assertArrayEquals(new int[] { 10, 0, 100, 250 },
    37	            MatchResult.clampBbox(in));
    38	    }
    39	
    40	    @Test void clampBbox_clampsBothXAndY_independently() {
    41	        int[] in = { -44, -27, 2072, 2406 };
    42	        assertArrayEquals(new int[] { 0, 0, 2028, 2379 },
    43	            MatchResult.clampBbox(in));
    44	    }
    45	
    46	    @Test void clampBbox_clampsWidthFloorToZero_whenAbsXExceedsW() {
    47	        // Pathological: x=-200, w=100 means the box is entirely off-image.
    48	        // Clamp to a zero-width box at the origin rather than a negative
    49	        // width that would also be rejected.
    50	        int[] in = { -200, 50, 100, 80 };
    51	        assertArrayEquals(new int[] { 0, 50, 0, 80 },
    52	            MatchResult.clampBbox(in));
    53	    }
    54	
    55	    @Test void clampBbox_clampsHeightFloorToZero_whenAbsYExceedsH() {
    56	        int[] in = { 10, -300, 100, 100 };
    57	        assertArrayEquals(new int[] { 10, 0, 100, 0 },
    58	            MatchResult.clampBbox(in));
    59	    }
    60	
    61	    @Test void clampBbox_returnsInput_whenNull() {
    62	        assertNull(MatchResult.clampBbox(null));
    63	    }
    64	
    65	    @Test void clampBbox_returnsInput_whenShorterThanFour() {
    66	        int[] in = { 1, 2, 3 };
    67	        assertArrayEquals(in, MatchResult.clampBbox(in));
    68	    }
    69	
    70	    @Test void clampBbox_doesNotMutateInput() {
    71	        int[] in = { -80, 42, 1786, 2228 };
    72	        MatchResult.clampBbox(in);
    73	        // Caller's array must be untouched; clamp returns a fresh copy.
    74	        assertArrayEquals(new int[] { -80, 42, 1786, 2228 }, in);
    75	    }
    76	
    77	    // --- bboxToJsonArray -------------------------------------------------
    78	
    79	    @Test void bboxToJsonArray_buildsArrayFromInts() {
    80	        JSONArray out = MatchResult.bboxToJsonArray(new int[] { 1, 2, 3, 4 });
    81	        assertEquals(4, out.length());
    82	        assertEquals(1, out.getInt(0));
    83	        assertEquals(2, out.getInt(1));
    84	        assertEquals(3, out.getInt(2));
    85	        assertEquals(4, out.getInt(3));
    86	    }
    87	
    88	    @Test void bboxToJsonArray_emptyForNullInput() {
    89	        JSONArray out = MatchResult.bboxToJsonArray(null);
    90	        assertEquals(0, out.length());
    91	    }
    92	
    93	    @Test void bboxToJsonArray_handlesEmptyArray() {
    94	        JSONArray out = MatchResult.bboxToJsonArray(new int[0]);
    95	        assertEquals(0, out.length());
    96	    }
    97	}

 succeeded in 248ms:
   280	        url += "&version=heatmask";
   281	        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
   282	        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
   283	            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
   284	        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
   285	        MediaAsset ma = null;
   286	        try {
   287	            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
   288	            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
   289	            ma.addLabel("matchInspectionHeatmap");
   290	            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
   291	                " and created " + ma);
   292	            tmpFile.delete();
   293	        } catch (Exception ex) {
   294	            System.out.println(
   295	                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
   296	                " => " + tmpFile + ": " + ex);
   297	            ex.printStackTrace();
   298	        }
   299	        return ma;
   300	    }
   301	
   302	/*
   303	   notes on pairx payload:
   304	   - image1_uris / image2_uris accept URLs or local file paths (as seen by the server)
   305	   - If you provide 1 image1 and N image2s, it compares that single image1 against each image2 (1-to-many)
   306	   - If you provide N of each, they're compared pairwise (N-to-N, max 16 pairs)
   307	   - bb1/bb2 are bounding boxes as [x, y, width, height]
   308	   - visualization_type options: "lines_and_colors", "only_lines", "only_colors"
   309	   - layer_key controls feature depth — earlier layers (e.g. backbone.blocks.1) give point-specific matches, later layers
   310	    (e.g. backbone.blocks.5) give broader region matches
   311	 */
   312	    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
   313	        Shepherd myShepherd) {
   314	        if ((ann1 == null) || (ann2 == null)) return null;
   315	        MediaAsset ma1 = ann1.getMediaAsset();
   316	        MediaAsset ma2 = ann2.getMediaAsset();
   317	        if ((ma1 == null) || (ma2 == null)) return null;
   318	        // we need this to find MLService endpoint
   319	        Encounter enc = ann1.findEncounter(myShepherd);
   320	        if (enc == null) return null;
   321	        JSONObject payload = new JSONObject();
   322	        payload.put("algorithm", "pairx");
   323	        payload.put("visualization_type", "only_colors");
   324	        payload.put("k_colors", 5);
   325	        // payload.put("k_lines", 20);
   326	        payload.put("model_id", "miewid-msv4.1");
   327	        payload.put("crop_bbox", false);
   328	        payload.put("layer_key", "backbone.blocks.3");
   329	        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
   330	        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
   331	        payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
   332	        payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
   333	        // bb1 / bb2 each need an outer array of [x, y, w, h] arrays.
   334	        //
   335	        // Two bugs were producing empty match-result UI before this fix
   336	        // (empty-match-prospects design Track 2 C12):
   337	        //
   338	        //   1. The previous implementation reused a single JSONArray for
   339	        //      both bb1 and bb2, so both keys ended up referencing
   340	        //      ann2's bbox — PairX was comparing image1[bbox=ann2's]
   341	        //      vs image2[bbox=ann2's]. Now we build separate outer
   342	        //      arrays.
   343	        //
   344	        //   2. ml-service detections sometimes produce bboxes that
   345	        //      extend past the image edge (negative x or y values
   346	        //      observed on amphibian-reptile.wildbook.org). The
   347	        //      /explain/ endpoint rejects those with HTTP 400
   348	        //      "Bounding box values should be positive", which caused
   349	        //      ASSET_ID_OID=NULL on every prospect and the UI hid the
   350	        //      whole MatchResult. clampBbox clamps to the in-image
   351	        //      portion: x=max(0,x), w=w+min(0,x_orig) so the box still
   352	        //      covers the same pixels that the embedding model
   353	        //      actually saw after image-edge cropping.
   354	        payload.put("bb1", new JSONArray().put(bboxToJsonArray(
   355	            clampBbox(ann1.getBbox()))));
   356	        payload.put("bb2", new JSONArray().put(bboxToJsonArray(
   357	            clampBbox(ann2.getBbox()))));
   358	
   359	        // get the image data from pairx endpoint
   360	        JSONObject res = null;
   361	        URL pairxUrl = null;
   362	        try {
   363	            pairxUrl = _getPairxUrl(enc.getTaxonomyString());
   364	            if (pairxUrl == null) return null;
   365	            res = RestClient.postJSON(pairxUrl, payload, null);
   366	        } catch (Exception ex) {
   367	            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
   368	                " failed: " + ex + "; payload=" + payload);
   369	            ex.printStackTrace();
   370	        }
   371	        if (res == null) return null;
   372	        JSONArray imgs = res.optJSONArray("images");
   373	        if ((imgs == null) || (imgs.length() < 1)) return null;
   374	        String b64 = imgs.optString(0, null);
   375	        if (b64 == null) return null;
   376	        // create the asset from base64 data
   377	        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
   378	            " got image data length=" + b64.length());
   379	        try {
   380	            AssetStore store = AssetStore.getDefault(myShepherd);
   381	            JSONObject params = store.createParameters(new File(Util.hashDirectories(this.id) +
   382	                "/pairx-" + this.id + "-" + ann1.getId() + "-" + ann2.getId() + ".png"));
   383	            MediaAsset ma = store.create(params);
   384	            ma.copyInBase64(b64);
   385	            ma.addLabel("matchInspectionPairx");
   386	            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
   387	            myShepherd.getPM().makePersistent(ma);
   388	            return ma;
   389	        } catch (Exception ex) {
   390	            System.out.println(
   391	                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
   392	            ex.printStackTrace();
   393	        }
   394	        return null;
   395	    }
   396	
   397	    /**
   398	     * Clamp negative bbox values to the in-image portion. ml-service
   399	     * detections sometimes produce bboxes whose top-left extends past
   400	     * the image edge (e.g., {@code [-80, 42, 1786, 2228]}); the PairX
   401	     * {@code /explain/} endpoint rejects those with HTTP 400. Shifting
   402	     * x or y to 0 alone would translate the box; we also shrink the
   403	     * dimension by the same amount so the result covers the same in-
   404	     * image pixels the embedding model actually consumed after
   405	     * edge-cropping.
   406	     *
   407	     * <p>Package-visible for unit testing. (Empty-match-prospects
   408	     * design Track 2 C12.)</p>
   409	     */
   410	    static int[] clampBbox(int[] bbox) {
   411	        if (bbox == null || bbox.length < 4) return bbox;
   412	        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];
   413	        if (x < 0) {
   414	            w = Math.max(0, w + x);
   415	            x = 0;
   416	        }
   417	        if (y < 0) {
   418	            h = Math.max(0, h + y);
   419	            y = 0;
   420	        }
   421	        return new int[] { x, y, w, h };
   422	    }
   423	
   424	    /**
   425	     * Convert an int[] bbox to a JSONArray of ints. {@code JSONArray.put(Object)}
   426	     * doesn't auto-convert int[] reliably across org.json versions, so we
   427	     * box explicitly.
   428	     */
   429	    static JSONArray bboxToJsonArray(int[] bbox) {
   430	        JSONArray arr = new JSONArray();

 succeeded in 401ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MatchResult.java

 succeeded in 476ms:
diff --git a/src/test/java/org/ecocean/MatchResultTest.java b/src/test/java/org/ecocean/MatchResultTest.java
index ff1153e1a..d24b17c0c 100644
--- a/src/test/java/org/ecocean/MatchResultTest.java
+++ b/src/test/java/org/ecocean/MatchResultTest.java
@@ -1,112 +1,112 @@
-package org.ecocean;
-
-import java.io.IOException;
-import java.util.ArrayList;
-import java.util.List;
-import java.util.Map;
-import org.ecocean.Annotation;
-import org.ecocean.ia.MatchResult;
-import org.ecocean.ia.MatchResultProspect;
-import org.ecocean.ia.Task;
-import org.ecocean.media.MediaAsset;
-import org.ecocean.shepherd.core.Shepherd;
-import org.ecocean.shepherd.core.ShepherdPMF;
-import org.json.JSONArray;
-import org.json.JSONObject;
-import org.junit.jupiter.api.Test;
-
-import static org.junit.Assert.*;
-import static org.junit.jupiter.api.Assertions.assertThrows;
-
-import javax.jdo.PersistenceManager;
-import javax.jdo.PersistenceManagerFactory;
-
-import org.mockito.MockedConstruction;
-import org.mockito.MockedStatic;
-import static org.mockito.Mockito.any;
-import static org.mockito.Mockito.mock;
-import static org.mockito.Mockito.mockConstruction;
-import static org.mockito.Mockito.mockStatic;
-import static org.mockito.Mockito.when;
-
-class MatchResultTest {
-    @Test void testMatchResultClassic()
-    throws IOException {
-        Task task = mock(Task.class);
-        MatchResult mr = new MatchResult(task);
-
-        assertTrue(mr.getNumberCandidates() == 0);
-
-        Annotation ann = mock(Annotation.class);
-        ArrayList<Annotation> annList = new ArrayList<Annotation>();
-        annList.add(ann);
-
-        Shepherd myShepherd = mock(Shepherd.class);
-        when(myShepherd.getAnnotationsWithACMId(any(String.class),
-            any(Boolean.class))).thenReturn(annList);
-
-        // gotta build whole IA json structure here :(
-        JSONObject res = new JSONObject();
-        res.put("query_annot_uuid_list", new JSONArray("[{\"__UUID__\": \"query-annot-id\"}]"));
-        res.put("database_annot_uuid_list",
-            new JSONArray(
-            "[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}, {\"__UUID__\": \"id2\"}]"));
-        JSONObject cm_dict = new JSONObject();
-        JSONObject lists = new JSONObject();
-        lists.put("dannot_uuid_list",
-            new JSONArray("[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}]"));
-        lists.put("annot_score_list", new JSONArray("[0.1, 0.2]"));
-        lists.put("score_list", new JSONArray("[0.3, 0.4]"));
-        cm_dict.put("query-annot-id", lists);
-        res.put("cm_dict", cm_dict);
-        mr.createFromJsonResult(res, myShepherd);
-        assertTrue(mr.getNumberCandidates() == 3);
-        assertTrue(mr.numberProspects() == 4);
-        assertTrue(mr.prospectScoreTypes().contains("indiv"));
-        assertTrue(mr.prospectScoreTypes().contains("annot"));
-        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
-        // verify ordering is correct
-        assertTrue(pj.getJSONArray("indiv").getJSONObject(0).getDouble("score") == 0.4);
-        assertTrue(pj.getJSONArray("indiv").getJSONObject(1).getDouble("score") == 0.3);
-        assertTrue(pj.getJSONArray("annot").getJSONObject(0).getDouble("score") == 0.2);
-        assertTrue(pj.getJSONArray("annot").getJSONObject(1).getDouble("score") == 0.1);
-        JSONObject full = mr.jsonForApiGet(-1, null, myShepherd);
-        assertTrue(full.getInt("numberTotalProspects") == 4);
-        assertTrue(full.getInt("numberCandidates") == 3);
-    }
-
-    // annotation-list style creation
-    @Test void testMatchResultVector()
-    throws IOException {
-        Task task = mock(Task.class);
-
-        when(task.countObjectAnnotations()).thenReturn(1);
-        int numCand = 99;
-        Annotation ann = mock(Annotation.class);
-        ArrayList<Annotation> annList = new ArrayList<Annotation>();
-
-        annList.add(ann);
-        when(task.getObjectAnnotations()).thenReturn(annList);
-
-        MatchResult mr = new MatchResult(task, annList, numCand, null);
-        assertTrue(mr.getNumberCandidates() == numCand);
-        assertTrue(mr.numberProspects() == 1);
-        // FIXME someday we need to figure out indiv-vector-search
-        // assertTrue(mr.prospectScoreTypes().contains("indiv"));
-        assertTrue(mr.prospectScoreTypes().contains("annot"));
-    }
-
-    @Test void basicMatchResultProspect() {
-        MatchResultProspect mrp = new MatchResultProspect(null, 1.0, "test", null);
-
-        assertNotNull(mrp);
-        assertTrue(mrp.getScore() == 1.0);
-        assertEquals(mrp.getType(), "test");
-        assertTrue(mrp.isType("test"));
-        assertFalse(mrp.isType(null));
-        // null annotation allows us to get away with null shepherd passed here
-        // as annotationDetails() will simply return empty json for no annot
-        JSONObject json = mrp.jsonForApiGet(null);
-        assertTrue(json.getDouble("score") == 1.0);
-    }
-}
+package org.ecocean;
+
+import java.io.IOException;
+import java.util.ArrayList;
+import java.util.List;
+import java.util.Map;
+import org.ecocean.Annotation;
+import org.ecocean.ia.MatchResult;
+import org.ecocean.ia.MatchResultProspect;
+import org.ecocean.ia.Task;
+import org.ecocean.media.MediaAsset;
+import org.ecocean.shepherd.core.Shepherd;
+import org.ecocean.shepherd.core.ShepherdPMF;
+import org.json.JSONArray;
+import org.json.JSONObject;
+import org.junit.jupiter.api.Test;
+
+import static org.junit.Assert.*;
+import static org.junit.jupiter.api.Assertions.assertThrows;
+
+import javax.jdo.PersistenceManager;
+import javax.jdo.PersistenceManagerFactory;
+
+import org.mockito.MockedConstruction;
+import org.mockito.MockedStatic;
+import static org.mockito.Mockito.any;
+import static org.mockito.Mockito.mock;
+import static org.mockito.Mockito.mockConstruction;
+import static org.mockito.Mockito.mockStatic;
+import static org.mockito.Mockito.when;
+
+class MatchResultTest {
+    @Test void testMatchResultClassic()
+    throws IOException {
+        Task task = mock(Task.class);
+        MatchResult mr = new MatchResult(task);
+
+        assertTrue(mr.getNumberCandidates() == 0);
+
+        Annotation ann = mock(Annotation.class);
+        ArrayList<Annotation> annList = new ArrayList<Annotation>();
+        annList.add(ann);
+
+        Shepherd myShepherd = mock(Shepherd.class);
+        when(myShepherd.getAnnotationsWithACMId(any(String.class),
+            any(Boolean.class))).thenReturn(annList);
+
+        // gotta build whole IA json structure here :(
+        JSONObject res = new JSONObject();
+        res.put("query_annot_uuid_list", new JSONArray("[{\"__UUID__\": \"query-annot-id\"}]"));
+        res.put("database_annot_uuid_list",
+            new JSONArray(
+            "[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}, {\"__UUID__\": \"id2\"}]"));
+        JSONObject cm_dict = new JSONObject();
+        JSONObject lists = new JSONObject();
+        lists.put("dannot_uuid_list",
+            new JSONArray("[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}]"));
+        lists.put("annot_score_list", new JSONArray("[0.1, 0.2]"));
+        lists.put("score_list", new JSONArray("[0.3, 0.4]"));
+        cm_dict.put("query-annot-id", lists);
+        res.put("cm_dict", cm_dict);
+        mr.createFromJsonResult(res, myShepherd);
+        assertTrue(mr.getNumberCandidates() == 3);
+        assertTrue(mr.numberProspects() == 4);
+        assertTrue(mr.prospectScoreTypes().contains("indiv"));
+        assertTrue(mr.prospectScoreTypes().contains("annot"));
+        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
+        // verify ordering is correct
+        assertTrue(pj.getJSONArray("indiv").getJSONObject(0).getDouble("score") == 0.4);
+        assertTrue(pj.getJSONArray("indiv").getJSONObject(1).getDouble("score") == 0.3);
+        assertTrue(pj.getJSONArray("annot").getJSONObject(0).getDouble("score") == 0.2);
+        assertTrue(pj.getJSONArray("annot").getJSONObject(1).getDouble("score") == 0.1);
+        JSONObject full = mr.jsonForApiGet(-1, null, myShepherd);
+        assertTrue(full.getInt("numberTotalProspects") == 4);
+        assertTrue(full.getInt("numberCandidates") == 3);
+    }
+
+    // annotation-list style creation
+    @Test void testMatchResultVector()
+    throws IOException {
+        Task task = mock(Task.class);
+
+        when(task.countObjectAnnotations()).thenReturn(1);
+        int numCand = 99;
+        Annotation ann = mock(Annotation.class);
+        ArrayList<Annotation> annList = new ArrayList<Annotation>();
+
+        annList.add(ann);
+        when(task.getObjectAnnotations()).thenReturn(annList);
+
+        MatchResult mr = new MatchResult(task, annList, numCand, null);
+        assertTrue(mr.getNumberCandidates() == numCand);
+        assertTrue(mr.numberProspects() == 1);
+        // FIXME someday we need to figure out indiv-vector-search
+        // assertTrue(mr.prospectScoreTypes().contains("indiv"));
+        assertTrue(mr.prospectScoreTypes().contains("annot"));
+    }
+
+    @Test void basicMatchResultProspect() {
+        MatchResultProspect mrp = new MatchResultProspect(null, 1.0, "test", null);
+
+        assertNotNull(mrp);
+        assertTrue(mrp.getScore() == 1.0);
+        assertEquals(mrp.getType(), "test");
+        assertTrue(mrp.isType("test"));
+        assertFalse(mrp.isType(null));
+        // null annotation allows us to get away with null shepherd passed here
+        // as annotationDetails() will simply return empty json for no annot
+        JSONObject json = mrp.jsonForApiGet(null);
+        assertTrue(json.getDouble("score") == 1.0);
+    }
+}

 succeeded in 11841ms:
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
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
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
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
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
 M src/main/java/org/ecocean/ia/MatchResult.java
 M src/main/java/org/ecocean/ia/MatchResultProspect.java
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
 M src/main/webapp/WEB-INF/web.xml
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
?? src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java

exec
/bin/bash -lc 'git diff --numstat -- src/test/java/org/ecocean/MatchResultTest.java src/main/java/org/ecocean/ia/MatchResult.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol src/test/java/org/ecocean/MatchResultTest.java src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java src/main/java/org/ecocean/ia/MatchResult.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff --check -- src/main/java/org/ecocean/ia/MatchResult.java src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java src/test/java/org/ecocean/MatchResultTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 449ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MatchResult.java
i/lf    w/crlf  attr/                 	src/test/java/org/ecocean/MatchResultTest.java

 succeeded in 589ms:
64	6	src/main/java/org/ecocean/ia/MatchResult.java
112	112	src/test/java/org/ecocean/MatchResultTest.java

 exited 2 in 655ms:
src/test/java/org/ecocean/MatchResultTest.java:1: trailing whitespace.
+package org.ecocean;
src/test/java/org/ecocean/MatchResultTest.java:2: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:3: trailing whitespace.
+import java.io.IOException;
src/test/java/org/ecocean/MatchResultTest.java:4: trailing whitespace.
+import java.util.ArrayList;
src/test/java/org/ecocean/MatchResultTest.java:5: trailing whitespace.
+import java.util.List;
src/test/java/org/ecocean/MatchResultTest.java:6: trailing whitespace.
+import java.util.Map;
src/test/java/org/ecocean/MatchResultTest.java:7: trailing whitespace.
+import org.ecocean.Annotation;
src/test/java/org/ecocean/MatchResultTest.java:8: trailing whitespace.
+import org.ecocean.ia.MatchResult;
src/test/java/org/ecocean/MatchResultTest.java:9: trailing whitespace.
+import org.ecocean.ia.MatchResultProspect;
src/test/java/org/ecocean/MatchResultTest.java:10: trailing whitespace.
+import org.ecocean.ia.Task;
src/test/java/org/ecocean/MatchResultTest.java:11: trailing whitespace.
+import org.ecocean.media.MediaAsset;
src/test/java/org/ecocean/MatchResultTest.java:12: trailing whitespace.
+import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/MatchResultTest.java:13: trailing whitespace.
+import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/MatchResultTest.java:14: trailing whitespace.
+import org.json.JSONArray;
src/test/java/org/ecocean/MatchResultTest.java:15: trailing whitespace.
+import org.json.JSONObject;
src/test/java/org/ecocean/MatchResultTest.java:16: trailing whitespace.
+import org.junit.jupiter.api.Test;
src/test/java/org/ecocean/MatchResultTest.java:17: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:18: trailing whitespace.
+import static org.junit.Assert.*;
src/test/java/org/ecocean/MatchResultTest.java:19: trailing whitespace.
+import static org.junit.jupiter.api.Assertions.assertThrows;
src/test/java/org/ecocean/MatchResultTest.java:20: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:21: trailing whitespace.
+import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/MatchResultTest.java:22: trailing whitespace.
+import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/MatchResultTest.java:23: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:24: trailing whitespace.
+import org.mockito.MockedConstruction;
src/test/java/org/ecocean/MatchResultTest.java:25: trailing whitespace.
+import org.mockito.MockedStatic;
src/test/java/org/ecocean/MatchResultTest.java:26: trailing whitespace.
+import static org.mockito.Mockito.any;
src/test/java/org/ecocean/MatchResultTest.java:27: trailing whitespace.
+import static org.mockito.Mockito.mock;
src/test/java/org/ecocean/MatchResultTest.java:28: trailing whitespace.
+import static org.mockito.Mockito.mockConstruction;
src/test/java/org/ecocean/MatchResultTest.java:29: trailing whitespace.
+import static org.mockito.Mockito.mockStatic;
src/test/java/org/ecocean/MatchResultTest.java:30: trailing whitespace.
+import static org.mockito.Mockito.when;
src/test/java/org/ecocean/MatchResultTest.java:31: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:32: trailing whitespace.
+class MatchResultTest {
src/test/java/org/ecocean/MatchResultTest.java:33: trailing whitespace.
+    @Test void testMatchResultClassic()
src/test/java/org/ecocean/MatchResultTest.java:34: trailing whitespace.
+    throws IOException {
src/test/java/org/ecocean/MatchResultTest.java:35: trailing whitespace.
+        Task task = mock(Task.class);
src/test/java/org/ecocean/MatchResultTest.java:36: trailing whitespace.
+        MatchResult mr = new MatchResult(task);
src/test/java/org/ecocean/MatchResultTest.java:37: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:38: trailing whitespace.
+        assertTrue(mr.getNumberCandidates() == 0);
src/test/java/org/ecocean/MatchResultTest.java:39: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:40: trailing whitespace.
+        Annotation ann = mock(Annotation.class);
src/test/java/org/ecocean/MatchResultTest.java:41: trailing whitespace.
+        ArrayList<Annotation> annList = new ArrayList<Annotation>();
src/test/java/org/ecocean/MatchResultTest.java:42: trailing whitespace.
+        annList.add(ann);
src/test/java/org/ecocean/MatchResultTest.java:43: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:44: trailing whitespace.
+        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/MatchResultTest.java:45: trailing whitespace.
+        when(myShepherd.getAnnotationsWithACMId(any(String.class),
src/test/java/org/ecocean/MatchResultTest.java:46: trailing whitespace.
+            any(Boolean.class))).thenReturn(annList);
src/test/java/org/ecocean/MatchResultTest.java:47: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:48: trailing whitespace.
+        // gotta build whole IA json structure here :(
src/test/java/org/ecocean/MatchResultTest.java:49: trailing whitespace.
+        JSONObject res = new JSONObject();
src/test/java/org/ecocean/MatchResultTest.java:50: trailing whitespace.
+        res.put("query_annot_uuid_list", new JSONArray("[{\"__UUID__\": \"query-annot-id\"}]"));
src/test/java/org/ecocean/MatchResultTest.java:51: trailing whitespace.
+        res.put("database_annot_uuid_list",
src/test/java/org/ecocean/MatchResultTest.java:52: trailing whitespace.
+            new JSONArray(
src/test/java/org/ecocean/MatchResultTest.java:53: trailing whitespace.
+            "[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}, {\"__UUID__\": \"id2\"}]"));
src/test/java/org/ecocean/MatchResultTest.java:54: trailing whitespace.
+        JSONObject cm_dict = new JSONObject();
src/test/java/org/ecocean/MatchResultTest.java:55: trailing whitespace.
+        JSONObject lists = new JSONObject();
src/test/java/org/ecocean/MatchResultTest.java:56: trailing whitespace.
+        lists.put("dannot_uuid_list",
src/test/java/org/ecocean/MatchResultTest.java:57: trailing whitespace.
+            new JSONArray("[{\"__UUID__\": \"id0\"}, {\"__UUID__\": \"id1\"}]"));
src/test/java/org/ecocean/MatchResultTest.java:58: trailing whitespace.
+        lists.put("annot_score_list", new JSONArray("[0.1, 0.2]"));
src/test/java/org/ecocean/MatchResultTest.java:59: trailing whitespace.
+        lists.put("score_list", new JSONArray("[0.3, 0.4]"));
src/test/java/org/ecocean/MatchResultTest.java:60: trailing whitespace.
+        cm_dict.put("query-annot-id", lists);
src/test/java/org/ecocean/MatchResultTest.java:61: trailing whitespace.
+        res.put("cm_dict", cm_dict);
src/test/java/org/ecocean/MatchResultTest.java:62: trailing whitespace.
+        mr.createFromJsonResult(res, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:63: trailing whitespace.
+        assertTrue(mr.getNumberCandidates() == 3);
src/test/java/org/ecocean/MatchResultTest.java:64: trailing whitespace.
+        assertTrue(mr.numberProspects() == 4);
src/test/java/org/ecocean/MatchResultTest.java:65: trailing whitespace.
+        assertTrue(mr.prospectScoreTypes().contains("indiv"));
src/test/java/org/ecocean/MatchResultTest.java:66: trailing whitespace.
+        assertTrue(mr.prospectScoreTypes().contains("annot"));
src/test/java/org/ecocean/MatchResultTest.java:67: trailing whitespace.
+        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:68: trailing whitespace.
+        // verify ordering is correct
src/test/java/org/ecocean/MatchResultTest.java:69: trailing whitespace.
+        assertTrue(pj.getJSONArray("indiv").getJSONObject(0).getDouble("score") == 0.4);
src/test/java/org/ecocean/MatchResultTest.java:70: trailing whitespace.
+        assertTrue(pj.getJSONArray("indiv").getJSONObject(1).getDouble("score") == 0.3);
src/test/java/org/ecocean/MatchResultTest.java:71: trailing whitespace.
+        assertTrue(pj.getJSONArray("annot").getJSONObject(0).getDouble("score") == 0.2);
src/test/java/org/ecocean/MatchResultTest.java:72: trailing whitespace.
+        assertTrue(pj.getJSONArray("annot").getJSONObject(1).getDouble("score") == 0.1);
src/test/java/org/ecocean/MatchResultTest.java:73: trailing whitespace.
+        JSONObject full = mr.jsonForApiGet(-1, null, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:74: trailing whitespace.
+        assertTrue(full.getInt("numberTotalProspects") == 4);
src/test/java/org/ecocean/MatchResultTest.java:75: trailing whitespace.
+        assertTrue(full.getInt("numberCandidates") == 3);
src/test/java/org/ecocean/MatchResultTest.java:76: trailing whitespace.
+    }
src/test/java/org/ecocean/MatchResultTest.java:77: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:78: trailing whitespace.
+    // annotation-list style creation
src/test/java/org/ecocean/MatchResultTest.java:79: trailing whitespace.
+    @Test void testMatchResultVector()
src/test/java/org/ecocean/MatchResultTest.java:80: trailing whitespace.
+    throws IOException {
src/test/java/org/ecocean/MatchResultTest.java:81: trailing whitespace.
+        Task task = mock(Task.class);
src/test/java/org/ecocean/MatchResultTest.java:82: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:83: trailing whitespace.
+        when(task.countObjectAnnotations()).thenReturn(1);
src/test/java/org/ecocean/MatchResultTest.java:84: trailing whitespace.
+        int numCand = 99;
src/test/java/org/ecocean/MatchResultTest.java:85: trailing whitespace.
+        Annotation ann = mock(Annotation.class);
src/test/java/org/ecocean/MatchResultTest.java:86: trailing whitespace.
+        ArrayList<Annotation> annList = new ArrayList<Annotation>();
src/test/java/org/ecocean/MatchResultTest.java:87: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:88: trailing whitespace.
+        annList.add(ann);
src/test/java/org/ecocean/MatchResultTest.java:89: trailing whitespace.
+        when(task.getObjectAnnotations()).thenReturn(annList);
src/test/java/org/ecocean/MatchResultTest.java:90: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:91: trailing whitespace.
+        MatchResult mr = new MatchResult(task, annList, numCand, null);
src/test/java/org/ecocean/MatchResultTest.java:92: trailing whitespace.
+        assertTrue(mr.getNumberCandidates() == numCand);
src/test/java/org/ecocean/MatchResultTest.java:93: trailing whitespace.
+        assertTrue(mr.numberProspects() == 1);
src/test/java/org/ecocean/MatchResultTest.java:94: trailing whitespace.
+        // FIXME someday we need to figure out indiv-vector-search
src/test/java/org/ecocean/MatchResultTest.java:95: trailing whitespace.
+        // assertTrue(mr.prospectScoreTypes().contains("indiv"));
src/test/java/org/ecocean/MatchResultTest.java:96: trailing whitespace.
+        assertTrue(mr.prospectScoreTypes().contains("annot"));
src/test/java/org/ecocean/MatchResultTest.java:97: trailing whitespace.
+    }
src/test/java/org/ecocean/MatchResultTest.java:98: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:99: trailing whitespace.
+    @Test void basicMatchResultProspect() {
src/test/java/org/ecocean/MatchResultTest.java:100: trailing whitespace.
+        MatchResultProspect mrp = new MatchResultProspect(null, 1.0, "test", null);
src/test/java/org/ecocean/MatchResultTest.java:101: trailing whitespace.
+
src/test/java/org/ecocean/MatchResultTest.java:102: trailing whitespace.
+        assertNotNull(mrp);
src/test/java/org/ecocean/MatchResultTest.java:103: trailing whitespace.
+        assertTrue(mrp.getScore() == 1.0);
src/test/java/org/ecocean/MatchResultTest.java:104: trailing whitespace.
+        assertEquals(mrp.getType(), "test");
src/test/java/org/ecocean/MatchResultTest.java:105: trailing whitespace.
+        assertTrue(mrp.isType("test"));
src/test/java/org/ecocean/MatchResultTest.java:106: trailing whitespace.
+        assertFalse(mrp.isType(null));
src/test/java/org/ecocean/MatchResultTest.java:107: trailing whitespace.
+        // null annotation allows us to get away with null shepherd passed here
src/test/java/org/ecocean/MatchResultTest.java:108: trailing whitespace.
+        // as annotationDetails() will simply return empty json for no annot
src/test/java/org/ecocean/MatchResultTest.java:109: trailing whitespace.
+        JSONObject json = mrp.jsonForApiGet(null);
src/test/java/org/ecocean/MatchResultTest.java:110: trailing whitespace.
+        assertTrue(json.getDouble("score") == 1.0);
src/test/java/org/ecocean/MatchResultTest.java:111: trailing whitespace.
+    }
src/test/java/org/ecocean/MatchResultTest.java:112: trailing whitespace.
+}

exec
/bin/bash -lc "rg \"int\\[\\] getBbox|getBbox\\(\\)\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean -n" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg "createInspectionPairxAsset|matchInspectionPairx|ASSET_ID" -n src/main/java src/test/java | head -80' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg "jsonForApiGet|asset" -n src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/java/org/ecocean/ia/MatchResult.java | head -120' in /mnt/c/Wildbook-clean2
 succeeded in 366ms:
src/main/java/org/ecocean/ia/MatchResultProspect.java:19:    private MediaAsset asset;
src/main/java/org/ecocean/ia/MatchResultProspect.java:29:    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:34:        this.asset = asset;
src/main/java/org/ecocean/ia/MatchResultProspect.java:63:    public JSONObject jsonForApiGet(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:69:        if (asset != null) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:70:            JSONObject aj = asset.toSimpleJSONObject();
src/main/java/org/ecocean/ia/MatchResultProspect.java:71:            aj.put("url", asset.webURL()); // we have no "safe" url
src/main/java/org/ecocean/ia/MatchResultProspect.java:72:            rtn.put("asset", aj);
src/main/java/org/ecocean/ia/MatchResult.java:295:                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
src/main/java/org/ecocean/ia/MatchResult.java:376:        // create the asset from base64 data
src/main/java/org/ecocean/ia/MatchResult.java:504:                jarr.put(mrp.jsonForApiGet(myShepherd));
src/main/java/org/ecocean/ia/MatchResult.java:511:    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:550:            aj.put("asset", mj);

 succeeded in 1049ms:
src/main/java/org/ecocean/Annotation.java:769:    public int[] getBbox() {
src/main/java/org/ecocean/Annotation.java:796:            // note: do NOT use toString() in here!  it references .getBbox() !!  see: recursion
src/main/java/org/ecocean/Annotation.java:797:            System.out.println("WARNING: Annotation.getBbox() found invalid width/height for id=" +
src/main/java/org/ecocean/Annotation.java:807:        int[] mine = this.getBbox();
src/main/java/org/ecocean/Annotation.java:809:        int[] otherBbox = other.getBbox();
src/main/java/org/ecocean/Annotation.java:844:        return Arrays.toString(this.getBbox());
src/main/java/org/ecocean/Annotation.java:852:                   .append("bbox", getBbox())
src/main/java/org/ecocean/Annotation.java:1990:            if (ann.getBbox() == null) return null;
src/main/java/org/ecocean/Annotation.java:1991:            int[] bBox = ann.getBbox();
src/main/java/org/ecocean/Encounter.java:4323:                    Feature ft = ann.getFeature(); // attempt force loading features for getBbox()
src/main/java/org/ecocean/Encounter.java:4324:                    int[] bbox = ann.getBbox();
src/main/java/org/ecocean/export/EncounterImageExportFile.java:68:                    int[] bbox = a.getBbox();
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:330:        int[] bbox = ann.getBbox();
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:419:        int[] bbox = ann.getBbox();
src/main/java/org/ecocean/identity/IBEISIA.java:204:            int[] bbox = ann.getBbox();
src/main/java/org/ecocean/identity/IBEISIA.java:3603:        int[] bbox = ann.getBbox();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:313:            int[] bbox = ann.getBbox();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:389:            int[] bbox = ann.getBbox();
src/main/java/org/ecocean/StartupWildbook.java:798:            int[] bb = ann.getBbox();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:264:            int[] bbox = ann.getBbox();
src/main/java/org/ecocean/Annotation.java:769:    public int[] getBbox() {
src/main/java/org/ecocean/Annotation.java:796:            // note: do NOT use toString() in here!  it references .getBbox() !!  see: recursion
src/main/java/org/ecocean/Annotation.java:797:            System.out.println("WARNING: Annotation.getBbox() found invalid width/height for id=" +
src/main/java/org/ecocean/Annotation.java:807:        int[] mine = this.getBbox();
src/main/java/org/ecocean/Annotation.java:809:        int[] otherBbox = other.getBbox();
src/main/java/org/ecocean/Annotation.java:844:        return Arrays.toString(this.getBbox());
src/main/java/org/ecocean/Annotation.java:852:                   .append("bbox", getBbox())
src/main/java/org/ecocean/Annotation.java:1990:            if (ann.getBbox() == null) return null;
src/main/java/org/ecocean/Annotation.java:1991:            int[] bBox = ann.getBbox();
src/main/java/org/ecocean/ia/MLService.java:420:        payload.put("bbox", ann.getBbox());
src/main/java/org/ecocean/ia/MatchResult.java:355:            clampBbox(ann1.getBbox()))));
src/main/java/org/ecocean/ia/MatchResult.java:357:            clampBbox(ann2.getBbox()))));

 succeeded in 1136ms:
src/main/java/org/ecocean/ia/MatchResult.java:208:                MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ann, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:247:            MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ent.getValue().get(0),
src/main/java/org/ecocean/ia/MatchResult.java:312:    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
src/main/java/org/ecocean/ia/MatchResult.java:349:        //      ASSET_ID_OID=NULL on every prospect and the UI hid the
src/main/java/org/ecocean/ia/MatchResult.java:367:            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
src/main/java/org/ecocean/ia/MatchResult.java:377:        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
src/main/java/org/ecocean/ia/MatchResult.java:385:            ma.addLabel("matchInspectionPairx");
src/main/java/org/ecocean/ia/MatchResult.java:386:            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
src/main/java/org/ecocean/ia/MatchResult.java:391:                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
src/main/java/org/ecocean/Annotation.java:601:     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2

codex
One incidental issue showed up: `src/test/java/org/ecocean/MatchResultTest.java` is marked `i/lf w/crlf` and diffs as a full-file line-ending change. I’ll keep that separate from the bbox logic review unless it turns out to affect this patch.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '160,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '740,820p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '390,435p'" in /mnt/c/Wildbook-clean2
 succeeded in 204ms:
   160	    throws IOException {
   161	        if ((annotIds == null) || (scores == null))
   162	            throw new IOException("null annotIds or scores");
   163	        if (annotIds.length() != scores.length())
   164	            throw new IOException("mismatch in size of annotIds/scores");
   165	        if (this.prospects == null)
   166	            this.prospects = new HashSet<MatchResultProspect>();
   167	        int num = 0;
   168	        this.numberProspects += annotIds.length(); // true number of prospects
   169	        for (int i = 0; i < annotIds.length(); i++) {
   170	            double score = scores.optDouble(i, -Double.MAX_VALUE);
   171	            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
   172	            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
   173	            if (ann == null) {
   174	                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
   175	                    "; skipping; score=" + score);
   176	                continue;
   177	            }
   178	            MediaAsset ma = null;
   179	            // we only try if we have a true value in externs[i]
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
   196	    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
   197	        Shepherd myShepherd)
   198	    throws IOException {
   199	        if (Util.collectionIsEmptyOrNull(annots)) return 0;
   200	        if (this.prospects == null)
   201	            this.prospects = new HashSet<MatchResultProspect>();
   202	        if (scoreByIndividual) {
   203	            // the scores for these are calculated weighted by indiv count
   204	            _populateProspectsByIndividual(annots, myShepherd);
   205	        } else {
   206	            // these scores are direct from opensearch
   207	            for (Annotation ann : annots) {
   208	                MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ann, myShepherd);
   209	                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
   210	                    ma));
   211	            }
   212	        }
   213	        this.numberProspects = this.prospects.size();
   214	        return this.numberProspects;
   215	    }
   216	
   217	    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
   218	        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
   219	            List<Annotation> >();
   220	
   221	        for (Annotation ann : annots) {
   222	            Encounter enc = ann.findEncounter(myShepherd);
   223	            // i think we just ignore if no enc/indiv
   224	            if (enc == null) continue;
   225	            MarkedIndividual indiv = enc.getIndividual();
   226	            if (indiv == null) continue;
   227	            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
   228	            tally.get(indiv).add(ann);
   229	        }
   230	        if (tally.size() < 1) return; // no individuals i guess?
   231	
   232	        // this sorts by most annots (per indiv) highest to lowest
   233	        List<Map.Entry<MarkedIndividual,
   234	            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
   235	        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
   236	        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
   237	            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
   238	            Map.Entry<MarkedIndividual, List<Annotation> > two) {
   239	                // we reverse order here so we get largest first
   240	                return Integer.compare(two.getValue().size(), one.getValue().size());
   241	            }
   242	        });
   243	        int most = sorted.get(0).getValue().size(); // top num of annots
   244	        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
   245	            double score = new Double(ent.getValue().size()) / new Double(most);
   246	            // the ent value (annot List) should always have at least one annot, so we use first one
   247	            MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ent.getValue().get(0),
   248	                myShepherd);
   249	            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv", ma));
   250	        }
   251	    }
   252	
   253	    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
   254	        if (acmId == null) return null;
   255	        Annotation found = findAcmIdInTaskAnnotations(acmId);
   256	        if (found != null) return found;
   257	        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
   258	        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
   259	            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
   260	        if ((anns == null) || (anns.size() < 1)) return null;

 succeeded in 212ms:
   740	    }
   741	
   742	    public Boolean getIsOfInterest() {
   743	        return isOfInterest;
   744	    }
   745	
   746	    public void setIsOfInterest(Boolean b) {
   747	        isOfInterest = b;
   748	    }
   749	
   750	    public boolean getMatchAgainst() {
   751	        return matchAgainst;
   752	    }
   753	
   754	    public void setMatchAgainst(boolean b) {
   755	        matchAgainst = b;
   756	        this.setVersion();
   757	    }
   758	
   759	    public String getIdentificationStatus() {
   760	        return this.identificationStatus;
   761	    }
   762	
   763	    public void setIdentificationStatus(String status) {
   764	        this.identificationStatus = status;
   765	        this.setVersion();
   766	    }
   767	
   768	    // if this cannot determine a bounding box, then we return null
   769	    public int[] getBbox() {
   770	        MediaAsset ma = getMediaAsset();
   771	
   772	        if (ma == null) return null;
   773	        Feature found = null;
   774	        for (Feature ft : getFeatures()) {
   775	            if (ft.isUnity() || ft.isType("org.ecocean.boundingBox")) {
   776	                found = ft;
   777	                break;
   778	            }
   779	        }
   780	        if (found == null) return null;
   781	        int[] bbox = new int[4];
   782	        if (found.isUnity()) {
   783	            bbox[0] = 0;
   784	            bbox[1] = 0;
   785	            bbox[2] = (int)ma.getWidth();
   786	            bbox[3] = (int)ma.getHeight();
   787	        } else {
   788	            // guess we derive from feature!
   789	            if (found.getParameters() == null) return null;
   790	            bbox[0] = found.getParameters().optInt("x", 0);
   791	            bbox[1] = found.getParameters().optInt("y", 0);
   792	            bbox[2] = found.getParameters().optInt("width", 0);
   793	            bbox[3] = found.getParameters().optInt("height", 0);
   794	        }
   795	        if ((bbox[2] < 1) || (bbox[3] < 1)) {
   796	            // note: do NOT use toString() in here!  it references .getBbox() !!  see: recursion
   797	            System.out.println("WARNING: Annotation.getBbox() found invalid width/height for id=" +
   798	                this.getId());
   799	            return null;
   800	        }
   801	        // System.out.println("Set new Bounding box.");
   802	        return bbox;
   803	    }
   804	
   805	    public boolean equalsBbox(Annotation other) {
   806	        if (other == null) return false;
   807	        int[] mine = this.getBbox();
   808	        if (mine == null) return false;
   809	        int[] otherBbox = other.getBbox();
   810	        if (otherBbox == null) return false;
   811	        if (mine.length != otherBbox.length) return false;
   812	        for (int i = 0; i < mine.length; i++) {
   813	            if (mine[i] != otherBbox[i]) return false;
   814	        }
   815	        return true;
   816	    }
   817	
   818	    public boolean equalsTheta(Annotation other) {
   819	        if (other == null) return false;
   820	        return (this.getTheta() == other.getTheta());

 succeeded in 234ms:
   390	            throw new IAException("unhandled exception [will requeue, incremented] on POST: " + ex,
   391	                    true, true);
   392	        }
   393	    }
   394	
   395	    // this is to request detection find an annotation and (optionally) return embedding as well
   396	    public JSONObject createPayload(MediaAsset ma, JSONObject config)
   397	    throws IAException {
   398	        if ((config == null) || (ma == null))
   399	            throw new IAException("MLService.createPayload() configuration problem with ma=" + ma +
   400	                    "; config=" + config);
   401	        JSONObject payload = new JSONObject(config.toString());
   402	        payload.remove("api_endpoint");
   403	        payload.put("image_uri", ma.webURL());
   404	        // FIXME add embedding boolean/args
   405	        return payload;
   406	    }
   407	
   408	    // this only gets the embedding, from a given (manual or pre-existing) Annotation
   409	    public JSONObject createPayload(Annotation ann, JSONObject config)
   410	    throws IAException {
   411	        if ((config == null) || (ann == null))
   412	            throw new IAException("MLService.createPayload() configuration problem with ann=" +
   413	                    ann + "; config=" + config);
   414	        MediaAsset ma = ann.getMediaAsset();
   415	        if (ma == null)
   416	            throw new IAException("MLService.createPayload() no MediaAsset for ann=" + ann);
   417	        JSONObject payload = new JSONObject(config.toString());
   418	        payload.remove("api_endpoint");
   419	        payload.put("image_uri", ma.webURL());
   420	        payload.put("bbox", ann.getBbox());
   421	        payload.put("theta", ann.getTheta());
   422	        return payload;
   423	    }
   424	}

exec
/bin/bash -lc 'file src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java src/test/java/org/ecocean/MatchResultTest.java src/main/java/org/ecocean/ia/MatchResult.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff --name-status -- src/main/java/org/ecocean/ia/MatchResult.java src/test/java/org/ecocean/MatchResultTest.java src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "git diff -- src/test/java/org/ecocean/MatchResultTest.java | sed -n '1,40p'" in /mnt/c/Wildbook-clean2
 succeeded in 349ms:
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java: C++ source, ASCII text
src/test/java/org/ecocean/MatchResultTest.java:             ASCII text, with CRLF line terminators
src/main/java/org/ecocean/ia/MatchResult.java:              Java source, Unicode text, UTF-8 text

 succeeded in 439ms:
M	src/main/java/org/ecocean/ia/MatchResult.java
M	src/test/java/org/ecocean/MatchResultTest.java

 succeeded in 457ms:
diff --git a/src/test/java/org/ecocean/MatchResultTest.java b/src/test/java/org/ecocean/MatchResultTest.java
index ff1153e1a..d24b17c0c 100644
--- a/src/test/java/org/ecocean/MatchResultTest.java
+++ b/src/test/java/org/ecocean/MatchResultTest.java
@@ -1,112 +1,112 @@
-package org.ecocean;
-
-import java.io.IOException;
-import java.util.ArrayList;
-import java.util.List;
-import java.util.Map;
-import org.ecocean.Annotation;
-import org.ecocean.ia.MatchResult;
-import org.ecocean.ia.MatchResultProspect;
-import org.ecocean.ia.Task;
-import org.ecocean.media.MediaAsset;
-import org.ecocean.shepherd.core.Shepherd;
-import org.ecocean.shepherd.core.ShepherdPMF;
-import org.json.JSONArray;
-import org.json.JSONObject;
-import org.junit.jupiter.api.Test;
-
-import static org.junit.Assert.*;
-import static org.junit.jupiter.api.Assertions.assertThrows;
-
-import javax.jdo.PersistenceManager;
-import javax.jdo.PersistenceManagerFactory;
-
-import org.mockito.MockedConstruction;
-import org.mockito.MockedStatic;
-import static org.mockito.Mockito.any;
-import static org.mockito.Mockito.mock;
-import static org.mockito.Mockito.mockConstruction;
-import static org.mockito.Mockito.mockStatic;
-import static org.mockito.Mockito.when;
-
-class MatchResultTest {
-    @Test void testMatchResultClassic()
-    throws IOException {
-        Task task = mock(Task.class);

exec
/bin/bash -lc 'rg "sourceEncoding|maven.compiler|encoding" pom.xml' in /mnt/c/Wildbook-clean2
 succeeded in 263ms:
    <maven.compiler.release>${java.version}</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <artifactId>maven-compiler-plugin</artifactId>
          <encoding>UTF-8</encoding>

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '430,570p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg "junit-jupiter|junit-vintage|maven-surefire|include" -n pom.xml | head -80' in /mnt/c/Wildbook-clean2
 succeeded in 206ms:
   430	        JSONArray arr = new JSONArray();
   431	        if (bbox == null) return arr;
   432	        for (int v : bbox) arr.put(v);
   433	        return arr;
   434	    }
   435	
   436	    public static URL _getPairxUrl(String txStr)
   437	    throws IOException {
   438	        if (txStr == null) throw new IOException("passed null taxonomy");
   439	        String urlStr = null;
   440	        try {
   441	            MLService mls = new MLService();
   442	            List<JSONObject> confs = mls.getConfigs(txStr);
   443	            if (confs.size() < 1) throw new IOException("empty MLService configs for tx=" + txStr);
   444	            urlStr = confs.get(0).optString("api_endpoint", null);
   445	        } catch (IAException ex) {
   446	            throw new IOException(ex);
   447	        }
   448	        if (urlStr == null) return null;
   449	        return new URL(urlStr + "/explain/");
   450	    }
   451	
   452	    public JSONObject getTaskParameters() {
   453	        if (task == null) return null;
   454	        return task.getParameters();
   455	    }
   456	
   457	    public JSONObject getTaskMatchingSetFilter() {
   458	        if (task == null) return null;
   459	        JSONObject params = task.getParameters();
   460	        if (params == null) return null;
   461	        return params.optJSONObject("matchingSetFilter");
   462	    }
   463	
   464	/*
   465	    see note at top about candidates vs numberCandidates
   466	    public int numberCandidates() {
   467	        return Util.collectionSize(candidates);
   468	    }
   469	 */
   470	    public int numberProspects() {
   471	        return this.numberProspects;
   472	    }
   473	
   474	    public Set<String> prospectScoreTypes() {
   475	        Set<String> types = new HashSet<String>();
   476	
   477	        if (numberProspects() == 0) return types;
   478	        for (MatchResultProspect mrp : prospects) {
   479	            types.add(mrp.getType());
   480	        }
   481	        return types;
   482	    }
   483	
   484	    // if cutoff < 0 then it will not be truncated at all
   485	    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
   486	        Set<String> projectIds, Shepherd myShepherd) {
   487	        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
   488	
   489	        if (numberProspects() == 0) return pros;
   490	        for (MatchResultProspect mrp : prospects) {
   491	            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
   492	        }
   493	        Collections.sort(pros);
   494	        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
   495	        return pros;
   496	    }
   497	
   498	    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   499	        JSONObject sj = new JSONObject();
   500	
   501	        for (String type : prospectScoreTypes()) {
   502	            JSONArray jarr = new JSONArray();
   503	            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
   504	                jarr.put(mrp.jsonForApiGet(myShepherd));
   505	            }
   506	            sj.put(type, jarr);
   507	        }
   508	        return sj;
   509	    }
   510	
   511	    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   512	        JSONObject rtn = new JSONObject();
   513	
   514	        rtn.put("id", id);
   515	        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
   516	        rtn.put("numberTotalProspects", numberProspects());
   517	        rtn.put("numberCandidates", getNumberCandidates());
   518	        rtn.put("created", Util.millisToISO8601String(created));
   519	        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
   520	        rtn.put("projectIds", projectIds);
   521	        return rtn;
   522	    }
   523	
   524	    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
   525	        JSONObject aj = new JSONObject();
   526	
   527	        if (ann == null) return aj;
   528	        MediaAsset ma = ann.getMediaAsset();
   529	        // populate bounding box stuff (note: it may reset aj so must be done first)
   530	        if (ann.getFeatures() != null) {
   531	            for (Feature ft : ann.getFeatures()) {
   532	                if (ft.isUnity()) {
   533	                    aj.put("trivial", true);
   534	                    aj.put("x", 0);
   535	                    aj.put("y", 0);
   536	                    // would be weird to be null, but.....
   537	                    if (ma != null) {
   538	                        aj.put("width", (int)ma.getWidth());
   539	                        aj.put("height", (int)ma.getHeight());
   540	                    }
   541	                } else {
   542	                    // basically if we have more than one feature, only one wins
   543	                    if (ft.getParameters() != null) aj = ft.getParameters();
   544	                }
   545	            }
   546	        }
   547	        if (ma != null) {
   548	            JSONObject mj = ma.toSimpleJSONObject();
   549	            mj.put("rotationInfo", ma.getRotationInfo());
   550	            aj.put("asset", mj);
   551	        }
   552	        Encounter enc = ann.findEncounter(myShepherd);
   553	        if (enc != null) {
   554	            JSONObject ej = new JSONObject();
   555	            // TODO add "access" permission value if needed?
   556	            ej.put("id", enc.getId());
   557	            ej.put("taxonomy", enc.getTaxonomyString());
   558	            ej.put("locationId", enc.getLocationID());
   559	            aj.put("encounter", ej);
   560	            MarkedIndividual indiv = enc.getIndividual();
   561	            if (indiv != null) {
   562	                JSONObject ij = new JSONObject();
   563	                ij.put("id", indiv.getId());
   564	                ij.put("taxonomy", indiv.getTaxonomyString());
   565	                ij.put("displayName", indiv.getDisplayName());
   566	                ij.put("nickname", indiv.getNickName());
   567	                ij.put("sex", indiv.getSex());
   568	                ij.put("numberEncounters", indiv.getNumEncounters());
   569	                aj.put("individual", ij);
   570	            }

 succeeded in 205ms:
395:      <artifactId>junit-jupiter-api</artifactId>
401:      <artifactId>junit-jupiter-engine</artifactId>
439:        <artifactId>testcontainers-junit-jupiter</artifactId>
660:        <artifactId>maven-surefire-plugin</artifactId>
671:          <includes>
672:            <include>**/*Test.java</include>
673:            <include>**/*Tests.java</include>
674:          </includes>
843:          <includes>
844:            <include>manta.less</include>
845:          </includes>

exec
/bin/bash -lc "rg \"new MatchResult\\(|createFromJsonResult|populateProspects\\(\" -n src/main/java/org/ecocean src/test/java/org/ecocean | head -120" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '1,159p'" in /mnt/c/Wildbook-clean2
 succeeded in 229ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.File;
     4	import java.io.IOException;
     5	import java.net.URL;
     6	import java.util.ArrayList;
     7	import java.util.Collections;
     8	import java.util.Comparator;
     9	import java.util.HashMap;
    10	import java.util.HashSet;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	
    15	import org.json.JSONArray;
    16	import org.json.JSONObject;
    17	
    18	import org.ecocean.api.UploadedFiles;
    19	import org.ecocean.Annotation;
    20	import org.ecocean.Encounter;
    21	import org.ecocean.ia.MLService;
    22	import org.ecocean.ia.Task;
    23	import org.ecocean.identity.IBEISIA;
    24	import org.ecocean.identity.IdentityServiceLog;
    25	import org.ecocean.media.AssetStore;
    26	import org.ecocean.media.Feature;
    27	import org.ecocean.media.MediaAsset;
    28	import org.ecocean.media.URLAssetStore;
    29	import org.ecocean.MarkedIndividual;
    30	import org.ecocean.RestClient;
    31	import org.ecocean.shepherd.core.Shepherd;
    32	import org.ecocean.Util;
    33	
    34	public class MatchResult implements java.io.Serializable {
    35	    private String id;
    36	    private long created;
    37	    private Task task;
    38	    private Set<MatchResultProspect> prospects;
    39	    private Annotation queryAnnotation;
    40	    private int numberCandidates = 0;
    41	    // we store *actual* count here, but they may not all exist
    42	    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
    43	    private int numberProspects = 0;
    44	    // not sure we really *need* true fk link to these annots
    45	    // they might be gone now and will we ever use this?
    46	    // so for now we just populate numberCandidates
    47	    private Set<Annotation> candidates;
    48	    // fallback number to cutoff number of prospects to return
    49	    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
    50	    // number of MatchResultProspects [per type] to actually store (hotspotter
    51	    // results can produce thousands, but storing them all is excessive)
    52	    public static final int MAXIMUM_PROSPECTS_STORED = 500;
    53	
    54	    public MatchResult() {
    55	        id = Util.generateUUID();
    56	        created = System.currentTimeMillis();
    57	    }
    58	
    59	    public MatchResult(Task task) {
    60	        this();
    61	        this.task = task;
    62	    }
    63	
    64	    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
    65	    throws IOException {
    66	        this();
    67	        this.createFromIdentityServiceLog(isLog, myShepherd);
    68	    }
    69	
    70	    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
    71	    throws IOException {
    72	        this();
    73	        this.task = task;
    74	        this.createFromJsonResult(jsonResult, myShepherd);
    75	    }
    76	
    77	    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
    78	        Shepherd myShepherd)
    79	    throws IOException {
    80	        this();
    81	        this.task = task;
    82	        this.numberCandidates = numberCandidates;
    83	        this.setQueryAnnotationFromTask();
    84	        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
    85	        this.populateProspects(annots, false, myShepherd);
    86	        this.populateProspects(annots, true, myShepherd);
    87	    }
    88	
    89	    public int getNumberCandidates() {
    90	        return numberCandidates;
    91	    }
    92	
    93	    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
    94	    throws IOException {
    95	        if (isLog == null) throw new IOException("log passed is null");
    96	        String taskId = isLog.getTaskID();
    97	        this.task = myShepherd.getTask(taskId);
    98	        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
    99	        JSONObject res = isLog.getJsonResult();
   100	        if (res == null) {
   101	            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
   102	                isLog.getStatusJson());
   103	            throw new IOException("could not get json result");
   104	        }
   105	        createFromJsonResult(res, myShepherd);
   106	    }
   107	
   108	    public Annotation setQueryAnnotationFromTask()
   109	    throws IOException {
   110	        if (this.task == null)
   111	            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
   112	        int numAnns = this.task.countObjectAnnotations();
   113	        if (numAnns < 1)
   114	            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
   115	        if (numAnns > 1)
   116	            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
   117	                " annotations; using first");
   118	        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
   119	        return this.queryAnnotation;
   120	    }
   121	
   122	    // json_result section should be passed here
   123	    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
   124	    throws IOException {
   125	        if (res == null) throw new IOException("null json_result passed");
   126	        if (res.optJSONArray("query_annot_uuid_list") == null)
   127	            throw new IOException("no query annot list");
   128	        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
   129	            throw new IOException("empty query annot list");
   130	        // for now we are assuming a single query annot. sorrynotsorry.
   131	        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
   132	            "query_annot_uuid_list").optJSONObject(0));
   133	        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
   134	        if (this.queryAnnotation == null)
   135	            throw new IOException("failed to load query annot from id=" + queryAnnotId);
   136	        if (res.optJSONObject("cm_dict") == null)
   137	            throw new IOException("no cm_dict found in " + res);
   138	        // results is the real scores (etc) we are looking for.... finally!
   139	        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
   140	        if (results == null) throw new IOException("no actual results found");
   141	        // see note at top about true annot list of candidates vs number
   142	        if (res.optJSONArray("database_annot_uuid_list") != null)
   143	            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
   144	/*
   145	        annot_score_list <=> dannot_uuid_list
   146	        score_list is for indiv scores but on dannot_uuid_list (same length)
   147	        name_score_list <=> unique_name_uuid_list ???
   148	 */
   149	        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
   150	            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
   151	            results.optString("dannot_extern_reference", null), myShepherd);
   152	        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
   153	            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
   154	            results.optString("dannot_extern_reference", null), myShepherd);
   155	        System.out.println("[DEBUG] createFromJsonResult() created " + this);
   156	    }
   157	
   158	    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
   159	        JSONArray externs, String externRef, Shepherd myShepherd)

 succeeded in 849ms:
src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:74:        this.createFromJsonResult(jsonResult, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:85:        this.populateProspects(annots, false, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:86:        this.populateProspects(annots, true, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:105:        createFromJsonResult(res, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:123:    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:149:        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
src/main/java/org/ecocean/ia/MatchResult.java:152:        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
src/main/java/org/ecocean/ia/MatchResult.java:155:        System.out.println("[DEBUG] createFromJsonResult() created " + this);
src/main/java/org/ecocean/ia/MatchResult.java:158:    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
src/main/java/org/ecocean/ia/MatchResult.java:196:    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
src/main/java/org/ecocean/ia/Task.java:800:                    MatchResult mr = new MatchResult(log, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1884:                MatchResult mr = new MatchResult(task, j, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:36:        MatchResult mr = new MatchResult(task);
src/test/java/org/ecocean/MatchResultTest.java:62:        mr.createFromJsonResult(res, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:91:        MatchResult mr = new MatchResult(task, annList, numCand, null);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '330,395p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '770,820p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '1860,1905p'" in /mnt/c/Wildbook-clean2
 succeeded in 223ms:
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
   391	                subTask.setStatusDetailsAddError("UNKNOWN",
   392	                    "MatchResult persistence failed");
   393	                subtasksFailed++;
   394	            }
   395	            subTask.setCompletionDateInMilliseconds();

 succeeded in 220ms:
   770	        rtn.put("description",
   771	            getParameters().getJSONObject("ibeis.identification").optString("description",
   772	            "unknown algorithm/method"));
   773	        return rtn;
   774	    }
   775	
   776	    // convenience
   777	    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
   778	        return myShepherd.getMatchResults(this);
   779	    }
   780	
   781	    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
   782	        List<MatchResult> all = myShepherd.getMatchResults(this);
   783	
   784	        if (Util.collectionIsEmptyOrNull(all)) return null;
   785	        return all.get(0);
   786	    }
   787	
   788	    // logs are returned in chronological order here, so if the latest is desired, take the LAST one
   789	    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
   790	        List<MatchResult> mrs = new ArrayList<MatchResult>();
   791	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(this.id, "IBEISIA",
   792	            myShepherd);
   793	
   794	        if (logs == null) return mrs;
   795	        for (IdentityServiceLog log : logs) {
   796	            JSONObject res = log.getJsonResult();
   797	            // in theory this is how we can tell if it is an ident result log versus detection
   798	            if ((res != null) && (res.optJSONObject("cm_dict") != null)) {
   799	                try {
   800	                    MatchResult mr = new MatchResult(log, myShepherd);
   801	                    System.out.println("[INFO] generateMatchResults() [log t=" +
   802	                        log.getTimestamp() + "] on Task " + this.getId() + " generated: " + mr);
   803	                    myShepherd.getPM().makePersistent(mr);
   804	                    mrs.add(mr);
   805	                    setStatusDetailsAddLog("Created " + mr + " from IdentityServiceLog " +
   806	                        log.getTimestamp());
   807	                } catch (java.io.IOException ex) {
   808	                    System.out.println("[ERROR] generateMatchResults() [log t=" +
   809	                        log.getTimestamp() + "] on Task " + this.getId() + " failed: " + ex);
   810	                    ex.printStackTrace();
   811	                    setStatusDetailsAddError("UNKNOWN",
   812	                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
   813	                        " failed due to: " + ex);
   814	                }
   815	            }
   816	        }
   817	        return mrs;
   818	    }
   819	
   820	    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {

 succeeded in 279ms:
  1860	                if (!needReviewMap.keySet().contains(aid)) {
  1861	                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
  1862	                    needNameResolution.add(anns.get(aid));
  1863	                }
  1864	            }
  1865	        } else {
  1866	            for (String aid : anns.keySet()) {
  1867	                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
  1868	                needNameResolution.add(anns.get(aid));
  1869	            }
  1870	            jlog.put("loopComplete", true);
  1871	            rtn.put("loopComplete", true);
  1872	            jlog.put("_infDict", infDict);
  1873	            exitIdentificationLoop(infDict, myShepherd);
  1874	        }
  1875	        resolveNames(needNameResolution, j.optJSONObject("cm_dict"), myShepherd);
  1876	        log(taskID, null, jlog, myShepherd.getContext());
  1877	
  1878	        // set "completed" on Task
  1879	        Task task = myShepherd.getTask(taskID);
  1880	        if (task != null) {
  1881	            task.setStatus("completed");
  1882	            task.setCompletionDateInMilliseconds();
  1883	            try {
  1884	                MatchResult mr = new MatchResult(task, j, myShepherd);
  1885	                System.out.println("processCallbackIdentify() created " + mr + " on " + task);
  1886	                myShepherd.getPM().makePersistent(mr);
  1887	                task.setStatusDetailsAddLog("Created " + mr + " upon task completion");
  1888	            } catch (IOException ex) {
  1889	                System.out.println("processCallbackIdentify() failed to create MatchResult on " +
  1890	                    task + ": " + ex);
  1891	                ex.printStackTrace();
  1892	                task.setStatusDetailsAddError("UNKNOWN",
  1893	                    "Creation of MatchResult upon task completion failed due to: " + ex);
  1894	            }
  1895	        }
  1896	        myShepherd.commitDBTransaction();
  1897	        myShepherd.closeDBTransaction();
  1898	        return rtn;
  1899	    }
  1900	
  1901	    private static void exitIdentificationLoop(JSONObject infDict, Shepherd myShepherd) {
  1902	        System.out.println(
  1903	            "*****************\nhey i think we are happy with these annotations!\n*********************\n");
  1904	        System.out.println("I am not printing infDict. Sorry.");
  1905	        // here we can use cluster_dict to find out what to create/persist on our side

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,410p'" in /mnt/c/Wildbook-clean2
 succeeded in 248ms:
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
   391	                subTask.setStatusDetailsAddError("UNKNOWN",
   392	                    "MatchResult persistence failed");
   393	                subtasksFailed++;
   394	            }
   395	            subTask.setCompletionDateInMilliseconds();
   396	            myShepherd.getPM().makePersistent(subTask);
   397	        }
   398	        // Reflect subtask outcomes on the parent. Any failure marks the
   399	        // parent "error" with a PARTIAL detail; otherwise "completed".
   400	        // No "partial" terminal status is introduced — Task.statusInEndState
   401	        // only recognizes completed/error, so adding one would ripple into
   402	        // polling/UI semantics.
   403	        if (subtasksFailed == 0) {
   404	            task.setStatus("completed");
   405	        } else {
   406	            task.setStatus("error");
   407	            task.setStatusDetailsAddError("PARTIAL",
   408	                subtasksFailed + " of " + (subtasksOk + subtasksFailed) + " subtasks failed");
   409	        }
   410	        task.setCompletionDateInMilliseconds();

exec
/bin/bash -lc "rg \"findMatchProspects\\(\" -n src/main/java | grep -v \"public static\"" in /mnt/c/Wildbook-clean2
 succeeded in 750ms:
src/main/java/org/ecocean/Embedding.java:281:        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
src/main/java/org/ecocean/Embedding.java:302:                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:326:                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
src/main/java/org/ecocean/Embedding.java:345:                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:371:            System.out.println("findMatchProspects() on " + ann + " found " +
src/main/java/org/ecocean/Embedding.java:378:                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
src/main/java/org/ecocean/Embedding.java:381:                System.out.println("findMatchProspects() MatchResult creation failed on " +
src/main/java/org/ecocean/ia/MlServiceProcessor.java:526:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
src/main/java/org/ecocean/ia/IA.java:470:                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '480,555p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '440,485p'" in /mnt/c/Wildbook-clean2
 succeeded in 229ms:
   440	            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
   441	                newTaskParams.put("ibeis.identification",
   442	                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
   443	                topTask.setParameters(newTaskParams);
   444	                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
   445	            } else {
   446	                for (int i = 0; i < opts.size(); i++) {
   447	                    Task t = new Task();
   448	                    t.setObjectAnnotations(annsOneIAClass);
   449	                    newTaskParams.put("ibeis.identification",
   450	                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
   451	                    t.setParameters(newTaskParams);
   452	                    topTask.addChild(t);
   453	                    tasks.add(t);
   454	                }
   455	            }
   456	            newTaskParams.put("fastlane", fastlane);
   457	            if (fastlane) newTaskParams.put("lane", "fast");
   458	            myShepherd.storeNewTask(topTask);
   459	
   460	            // these are re-used in every task
   461	            JSONArray annArr = new JSONArray();
   462	            for (Annotation ann : annsOneIAClass) {
   463	                annArr.put(ann.getId());
   464	            }
   465	            JSONObject aj = new JSONObject();
   466	            aj.put("annotationIds", annArr);
   467	            String baseUrl = getBaseURL(context);
   468	            for (int i = 0; i < opts.size(); i++) {
   469	                // if this is a vector-based matching option, this will just do the job and be done
   470	                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
   471	                JSONObject qjob = new JSONObject();
   472	                qjob.put("identify", aj);
   473	                qjob.put("taskId", tasks.get(i).getId());
   474	                qjob.put("__context", context);
   475	                qjob.put("__baseUrl", baseUrl);
   476	                if (opts.get(i) != null) qjob.put("opt", opts.get(i));
   477	                boolean sent = false;
   478	                try {
   479	                    if (fastlane) {
   480	                        // if fastlane and a smaller, bespoke request, get this into the faster queue
   481	                        qjob.put("fastlane", fastlane);
   482	                        qjob.put("lane", "fast");
   483	                        tasks.get(i).setQueueResumeMessage(qjob.toString());
   484	                        sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
   485	                            qjob.toString());

 succeeded in 231ms:
   480	        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
   481	        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
   482	        // Carry forward attempt + firstDeferredAt so age-out is
   483	        // measured by elapsed wall-clock from the original DEFER, not
   484	        // by attempt count (Codex round-4 OQ #1).
   485	        int attempt = jobData.optInt("attempt", 2);
   486	        Long firstDeferredAt = jobData.has("firstDeferredAt")
   487	            ? Long.valueOf(jobData.optLong("firstDeferredAt")) : null;
   488	        // Re-gate; deferred match earns the same protection as the
   489	        // initial call (Codex round-2 Major: don't degrade back to
   490	        // today's bug on the first deferral).
   491	        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
   492	            attempt, firstDeferredAt);
   493	    }
   494	
   495	    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
   496	        JSONObject matchConfig) {
   497	        if (annotationIds == null || annotationIds.isEmpty()) {
   498	            markTaskCompleted(taskId);
   499	            return MlServiceJobOutcome.ok(new ArrayList<String>());
   500	        }
   501	
   502	        Shepherd shep = new Shepherd(context);
   503	        shep.setAction(ACTION_PREFIX + "runMatchProspects");
   504	        try {
   505	            shep.beginDBTransaction();
   506	            List<Annotation> anns = new ArrayList<Annotation>();
   507	            for (String annId : annotationIds) {
   508	                Annotation ann = shep.getAnnotation(annId);
   509	                if (ann != null) anns.add(ann);
   510	            }
   511	            if (anns.isEmpty()) {
   512	                Task task = Task.load(taskId, shep);
   513	                markTaskDroppedStale(shep, task, "annotations missing");
   514	                shep.commitDBTransaction();
   515	                return MlServiceJobOutcome.stale("annotations missing");
   516	            }
   517	
   518	            Task parent = Task.load(taskId, shep);
   519	            Task matchTask = (parent == null) ? new Task() : new Task(parent);
   520	            matchTask.setObjectAnnotations(anns);
   521	            matchTask.addParameter("mlServiceV2Match", true);
   522	            shep.getPM().makePersistent(matchTask);
   523	            // findMatchProspects returns false when the match config is not
   524	            // a vector config or matchConfig is null. Don't leave the match
   525	            // task without a terminal status — mark the parent task error.
   526	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
   527	            if (!ran) {
   528	                matchTask.setStatus("error");
   529	                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
   530	                    "findMatchProspects rejected match config: " +
   531	                    (matchConfig == null ? "null" : matchConfig.toString()));
   532	                matchTask.setCompletionDateInMilliseconds();
   533	                // Update the parent task in this same transaction (parent is
   534	                // already loaded above) so the two updates commit atomically.
   535	                // Splitting across transactions risks leaving the parent
   536	                // "completed" if the second commit fails or the JVM dies.
   537	                if (parent != null) {
   538	                    markTaskError(parent, "INVALID_MATCH_CONFIG",
   539	                        "no usable vector match config");
   540	                }
   541	                shep.commitDBTransaction();
   542	                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
   543	                    "no usable vector match config");
   544	            }
   545	            shep.commitDBTransaction();
   546	            return MlServiceJobOutcome.ok(annotationIds);
   547	        } catch (Exception ex) {
   548	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
   549	            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
   550	        } finally {
   551	            shep.rollbackAndClose();
   552	        }
   553	    }
   554	
   555	    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {

exec
/bin/bash -lc 'rg "beginDBTransaction|commitDBTransaction" -n src/main/java/org/ecocean/ia/IA.java | head -40' in /mnt/c/Wildbook-clean2
 succeeded in 299ms:
532:        myShepherd.beginDBTransaction();
591:            myShepherd.commitDBTransaction();
609:        myShepherd.beginDBTransaction();

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '400,540p'" in /mnt/c/Wildbook-clean2
 succeeded in 308ms:
   400	            System.out.println("IA.java is sending a Tursiops truncatus job");
   401	            opts = IBEISIA.identOpts(context, bottlenose);
   402	        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes opts = IBEISIA.identOpts(context);
   403	        }
   404	 */
   405	        // List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
   406	        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
   407	        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
   408	        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
   409	            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
   410	            // now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
   411	            if (opts != null) {
   412	                Iterator<JSONObject> itr = opts.iterator();
   413	                while (itr.hasNext()) {
   414	                    if (!itr.next().optBoolean("default", true)) itr.remove();
   415	                }
   416	            }
   417	            System.out.println("identOpts: " + opts);
   418	            List<Task> tasks = new ArrayList<Task>();
   419	            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
   420	            if (parentTask != null && parentTask.getParameters() != null) {
   421	                newTaskParams = parentTask.getParameters();
   422	                System.out.println("newTaskParams: " + newTaskParams.toString());
   423	                if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
   424	                    JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
   425	                    System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
   426	                    ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
   427	                    int maLength = matchingAlgorithms.length();
   428	                    for (int y = 0; y < maLength; y++) {
   429	                        newOpts.add(matchingAlgorithms.getJSONObject(y));
   430	                    }
   431	                    System.out.println("matchingAlgorithms2: " + newOpts.toString());
   432	                    if (newOpts.size() > 0) {
   433	                        opts = newOpts;
   434	                        System.out.println("Swapping opts for newOpts!!");
   435	                    }
   436	                }
   437	            }
   438	            if ((opts == null) || (opts.size() < 1)) continue; // no ID for this iaClass.
   439	            // just one IA class, one algorithm case
   440	            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
   441	                newTaskParams.put("ibeis.identification",
   442	                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
   443	                topTask.setParameters(newTaskParams);
   444	                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
   445	            } else {
   446	                for (int i = 0; i < opts.size(); i++) {
   447	                    Task t = new Task();
   448	                    t.setObjectAnnotations(annsOneIAClass);
   449	                    newTaskParams.put("ibeis.identification",
   450	                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
   451	                    t.setParameters(newTaskParams);
   452	                    topTask.addChild(t);
   453	                    tasks.add(t);
   454	                }
   455	            }
   456	            newTaskParams.put("fastlane", fastlane);
   457	            if (fastlane) newTaskParams.put("lane", "fast");
   458	            myShepherd.storeNewTask(topTask);
   459	
   460	            // these are re-used in every task
   461	            JSONArray annArr = new JSONArray();
   462	            for (Annotation ann : annsOneIAClass) {
   463	                annArr.put(ann.getId());
   464	            }
   465	            JSONObject aj = new JSONObject();
   466	            aj.put("annotationIds", annArr);
   467	            String baseUrl = getBaseURL(context);
   468	            for (int i = 0; i < opts.size(); i++) {
   469	                // if this is a vector-based matching option, this will just do the job and be done
   470	                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
   471	                JSONObject qjob = new JSONObject();
   472	                qjob.put("identify", aj);
   473	                qjob.put("taskId", tasks.get(i).getId());
   474	                qjob.put("__context", context);
   475	                qjob.put("__baseUrl", baseUrl);
   476	                if (opts.get(i) != null) qjob.put("opt", opts.get(i));
   477	                boolean sent = false;
   478	                try {
   479	                    if (fastlane) {
   480	                        // if fastlane and a smaller, bespoke request, get this into the faster queue
   481	                        qjob.put("fastlane", fastlane);
   482	                        qjob.put("lane", "fast");
   483	                        tasks.get(i).setQueueResumeMessage(qjob.toString());
   484	                        sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
   485	                            qjob.toString());
   486	                    } else {
   487	                        tasks.get(i).setQueueResumeMessage(qjob.toString());
   488	                        sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
   489	                    }
   490	                } catch (java.io.IOException iox) {
   491	                    System.out.println("ERROR[" + i +
   492	                        "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
   493	                }
   494	                System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " +
   495	                    annsOneIAClass.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
   496	            }
   497	        } // end for each iaClass
   498	        System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
   499	        return topTask;
   500	    }
   501	
   502	    public static List<List<Annotation> > binAnnotsByIaClass(List<Annotation> anns) {
   503	        System.out.println("binAnnotsByIaClass called on " + anns.size() + " annots");
   504	        Map<String, List<Annotation> > iaClassToAnns = new HashMap<String, List<Annotation> >();
   505	        for (Annotation ann : anns) {
   506	            String iaClass = ann.getIAClass();
   507	            if (iaClass == null) continue;
   508	            List<Annotation> iaClassList = iaClassToAnns.getOrDefault(iaClass,
   509	                new ArrayList<Annotation>());
   510	            iaClassList.add(ann);
   511	            iaClassToAnns.put(iaClass, iaClassList);
   512	        }
   513	        System.out.println("binAnnotsByIaClass binned them into " + iaClassToAnns.keySet().size() +
   514	            " bins: " + iaClassToAnns.keySet());
   515	        return new ArrayList<List<Annotation> >(iaClassToAnns.values());
   516	    }
   517	
   518	    // possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
   519	    // note: this is processed *from the queue* and as such does not have "output"
   520	    public static void handleRest(JSONObject jin) {
   521	        System.out.println("JIN JIN JIN: " + jin);
   522	        if (jin == null) return;
   523	        String context = jin.optString("__context", null);
   524	        if (context == null)
   525	            throw new RuntimeException("IA.handleRest(): passed data has no __context");
   526	        Shepherd myShepherd = new Shepherd(context);
   527	
   528	        // check if these should be directed through the fastlane
   529	        boolean fastlane = false;
   530	        if (jin.optBoolean("fastlane", false)) { fastlane = true; }
   531	        myShepherd.setAction("IA.handleRest");
   532	        myShepherd.beginDBTransaction();
   533	        try {
   534	            String taskId = jin.optString("taskId", Util.generateUUID());
   535	            Task topTask = Task.load(taskId, myShepherd);
   536	            if (topTask == null) topTask = new Task(taskId);
   537	            if (fastlane) topTask.addParameter("fastlane", true);
   538	            myShepherd.storeNewTask(topTask);
   539	            JSONObject opt = jin.optJSONObject("opt"); // should use this to decide how to branch differently than "default"
   540	            JSONArray mlist = jin.optJSONArray("mediaAssetIds");

codex
**Findings**

- **High:** `createInspectionPairxAsset` still does PairX HTTP while a Shepherd transaction is open. `MlServiceProcessor.runMatchProspects` begins the DB transaction at [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:505), calls `Embedding.findMatchProspects` at [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:526), which builds `MatchResult` at [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:377), which reaches `RestClient.postJSON` at [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:365). That violates the locked Phase A/B/C convention. It is pre-existing in shape, but this patch touches the PairX path and makes it more likely to succeed and spend time there.

- **Medium:** `clampBbox` can produce zero-width or zero-height boxes, and the tests codify that behavior. See [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:414) and [MatchResultClampBboxTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:46). If PairX rejects “positive” bbox values, zero dimensions are still invalid. I would skip/log these before POST rather than submit `w=0` or `h=0`.

- **Medium:** The tests cover the helpers, but not the load-bearing caller behavior that caused bug 1. A regression could reintroduce shared-array payload mutation or wrong `[[x,y,w,h]]` shape at [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:354) while all helper tests still pass. A focused payload-construction test, probably via extracted package-visible builder, would close that gap.

- **Low:** `src/test/java/org/ecocean/MatchResultTest.java` has CRLF churn: `git ls-files --eol` reports `i/lf w/crlf`, and the file diffs as `112/112` changed from line endings starting at [MatchResultTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/MatchResultTest.java:1). The new clamp test file is LF, but this existing test file is not normalized.

**Answers To Review Questions**

Shrink-on-negative-x/y is the right geometry for preserving the in-image intersection. Just moving `x` or `y` to `0` while keeping `w/h` would expand/translate the region.

`bboxToJsonArray` is reasonable defensive code, not over-engineering. The part I would tighten is invalid input handling: `null`, short arrays, and zero-sized clamped boxes should not become `bb: [[]]` or invalid PairX requests.

Open question: if PairX also requires boxes not to exceed the right/bottom image bounds, this helper needs media dimensions and should clamp `x + w` / `y + h` too. I did not run tests because this was review-only and Maven would write build output.
tokens used
146,400
**Findings**

- **High:** `createInspectionPairxAsset` still does PairX HTTP while a Shepherd transaction is open. `MlServiceProcessor.runMatchProspects` begins the DB transaction at [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:505), calls `Embedding.findMatchProspects` at [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:526), which builds `MatchResult` at [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:377), which reaches `RestClient.postJSON` at [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:365). That violates the locked Phase A/B/C convention. It is pre-existing in shape, but this patch touches the PairX path and makes it more likely to succeed and spend time there.

- **Medium:** `clampBbox` can produce zero-width or zero-height boxes, and the tests codify that behavior. See [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:414) and [MatchResultClampBboxTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:46). If PairX rejects “positive” bbox values, zero dimensions are still invalid. I would skip/log these before POST rather than submit `w=0` or `h=0`.

- **Medium:** The tests cover the helpers, but not the load-bearing caller behavior that caused bug 1. A regression could reintroduce shared-array payload mutation or wrong `[[x,y,w,h]]` shape at [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:354) while all helper tests still pass. A focused payload-construction test, probably via extracted package-visible builder, would close that gap.

- **Low:** `src/test/java/org/ecocean/MatchResultTest.java` has CRLF churn: `git ls-files --eol` reports `i/lf w/crlf`, and the file diffs as `112/112` changed from line endings starting at [MatchResultTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/MatchResultTest.java:1). The new clamp test file is LF, but this existing test file is not normalized.

**Answers To Review Questions**

Shrink-on-negative-x/y is the right geometry for preserving the in-image intersection. Just moving `x` or `y` to `0` while keeping `w/h` would expand/translate the region.

`bboxToJsonArray` is reasonable defensive code, not over-engineering. The part I would tighten is invalid input handling: `null`, short arrays, and zero-sized clamped boxes should not become `bb: [[]]` or invalid PairX requests.

Open question: if PairX also requires boxes not to exceed the right/bottom image bounds, this helper needs media dimensions and should clamp `x + w` / `y + h` too. I did not run tests because this was review-only and Maven would write build output.
