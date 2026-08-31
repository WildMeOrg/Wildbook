2026-05-19T15:18:19.293023Z ERROR codex_models_manager::manager: failed to refresh available models: timeout waiting for child process to exit
OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e40d0-ff9d-75f1-8400-7978a8cc0f74
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

# Codex code-review: Track 2 C13 — PairX as Phase A/B/C

Follow-up to C12 (which fixed the bbox/shared-array bugs but left
the Shepherd-across-PairX-HTTP antipattern flagged High). C13 moves
PairX out of MatchResult construction entirely. Phase A loads DTOs
under Shepherd and closes; Phase B does HTTP without Shepherd;
Phase C reopens a fresh Shepherd per prospect to attach the
inspection MediaAsset.

Per-prospect failure is non-blocking: an HTTP timeout or persist
error on one prospect logs and continues; other prospects in the
same MatchResult still get processed. MatchResult itself is
persisted with prospects.asset=null at the MatchResult-creation
seam; the enricher fills them in afterwards.

## Diff

diff --git a/src/main/java/org/ecocean/ia/MatchResult.java b/src/main/java/org/ecocean/ia/MatchResult.java
index 2eee14ef8..0fbee7c04 100644
--- a/src/main/java/org/ecocean/ia/MatchResult.java
+++ b/src/main/java/org/ecocean/ia/MatchResult.java
@@ -193,6 +193,13 @@ public class MatchResult implements java.io.Serializable {
     // we just have a list of annots which matched (e.g. via vectors in opensearch)
     // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
     // tends to return relatively few prospects. TODO adjust later if this proves untrue.
+    //
+    // Empty-match-prospects design Track 2 C13: prospects are created with
+    // {@code asset=null}. The PairX inspection image is populated later by
+    // {@link MatchInspectionPairxEnricher} in a Phase A/B/C flow so the
+    // outer Shepherd is never held across the PairX HTTP call. Holding a
+    // Shepherd across that ~10-30s POST would risk connection-pool
+    // exhaustion under load (Codex C12 review High).
     private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
         Shepherd myShepherd)
     throws IOException {
@@ -205,9 +212,8 @@ public class MatchResult implements java.io.Serializable {
         } else {
             // these scores are direct from opensearch
             for (Annotation ann : annots) {
-                MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ann, myShepherd);
                 this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
-                    ma));
+                    null));
             }
         }
         this.numberProspects = this.prospects.size();
@@ -243,13 +249,39 @@ public class MatchResult implements java.io.Serializable {
         int most = sorted.get(0).getValue().size(); // top num of annots
         for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
             double score = new Double(ent.getValue().size()) / new Double(most);
-            // the ent value (annot List) should always have at least one annot, so we use first one
-            MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ent.getValue().get(0),
-                myShepherd);
-            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv", ma));
+            // the ent value (annot List) should always have at least one annot, so we use first one.
+            // Inspection MediaAsset attached later by MatchInspectionPairxEnricher
+            // (empty-match-prospects design Track 2 C13).
+            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
+                null));
         }
     }
 
+    /**
+     * Public read-only view of the prospects collection so the
+     * {@link MatchInspectionPairxEnricher} can iterate them in Phase A
+     * and Phase C without reaching into private state. Returns the
+     * underlying Set; callers must not mutate.
+     */
+    public Set<MatchResultProspect> getProspects() {
+        return this.prospects;
+    }
+
+    /**
+     * Public accessor for the queryAnnotation field. Returns whatever
+     * value was set by {@link #setQueryAnnotationFromTask()} or
+     * {@link #createFromJsonResult(JSONObject, Shepherd)} — may be null
+     * if neither has run.
+     */
+    public Annotation getQueryAnnotation() {
+        return this.queryAnnotation;
+    }
+
+    /** Public accessor for the JDO primary key. */
+    public String getId() {
+        return this.id;
+    }
+
     private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
         if (acmId == null) return null;
         Annotation found = findAcmIdInTaskAnnotations(acmId);
diff --git a/src/main/java/org/ecocean/ia/MatchResultProspect.java b/src/main/java/org/ecocean/ia/MatchResultProspect.java
index 32f6b1b71..85581ceee 100644
--- a/src/main/java/org/ecocean/ia/MatchResultProspect.java
+++ b/src/main/java/org/ecocean/ia/MatchResultProspect.java
@@ -1,88 +1,107 @@
-package org.ecocean.ia;
-
-import java.util.HashSet;
-import java.util.Set;
-
-import org.json.JSONArray;
-import org.json.JSONObject;
-
-import org.ecocean.Annotation;
-import org.ecocean.Encounter;
-import org.ecocean.media.MediaAsset;
-import org.ecocean.shepherd.core.Shepherd;
-import org.ecocean.Util;
-
-public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
-    private Annotation annotation;
-    private double score = 0.0d;
-    private String scoreType;
-    private MediaAsset asset;
-    private MatchResult matchResult;
-
-    public MatchResultProspect() {}
-
-    public MatchResultProspect(Annotation ann) {
-        this();
-        this.annotation = ann;
-    }
-
-    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
-        this();
-        this.annotation = ann;
-        this.score = score;
-        this.scoreType = type;
-        this.asset = asset;
-    }
-
-    public double getScore() {
-        return score;
-    }
-
-    public String getType() {
-        return scoreType;
-    }
-
-    public boolean isType(String type) {
-        if (type == null) return (this.scoreType == null);
-        return type.equals(this.scoreType);
-    }
-
-    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
-        // if we have no projects to filter on, we consider this to be in it
-        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
-        if (annotation == null) return false;
-        Encounter enc = annotation.findEncounter(myShepherd);
-        if (enc == null) return false;
-        return enc.isInProjects(projectIds, myShepherd);
-    }
-
-    public String toString() {
-        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
-    }
-
-    public JSONObject jsonForApiGet(Shepherd myShepherd) {
-        JSONObject rtn = new JSONObject();
-
-        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
-        rtn.put("score", score);
-        // skipping scoreType since this is currently only used filtered by scoreType already
-        if (asset != null) {
-            JSONObject aj = asset.toSimpleJSONObject();
-            aj.put("url", asset.webURL()); // we have no "safe" url
-            rtn.put("asset", aj);
-        }
-        return rtn;
-    }
-
-    // used in sorting
-    @Override public int compareTo(MatchResultProspect other) {
-        // we invert this so higher score is first
-        int comp = Double.compare(other.score, this.score);
-        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
-        // ordering (otherwise tied scores come back random order), so we use annot id
-        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
-            return this.annotation.getId().compareTo(other.annotation.getId());
-        // scores are *not* equal, so we just let comparison stand as-is
-        return comp;
-    }
-}
+package org.ecocean.ia;
+
+import java.util.HashSet;
+import java.util.Set;
+
+import org.json.JSONArray;
+import org.json.JSONObject;
+
+import org.ecocean.Annotation;
+import org.ecocean.Encounter;
+import org.ecocean.media.MediaAsset;
+import org.ecocean.shepherd.core.Shepherd;
+import org.ecocean.Util;
+
+public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
+    private Annotation annotation;
+    private double score = 0.0d;
+    private String scoreType;
+    private MediaAsset asset;
+    private MatchResult matchResult;
+
+    public MatchResultProspect() {}
+
+    public MatchResultProspect(Annotation ann) {
+        this();
+        this.annotation = ann;
+    }
+
+    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
+        this();
+        this.annotation = ann;
+        this.score = score;
+        this.scoreType = type;
+        this.asset = asset;
+    }
+
+    /**
+     * Attach a PairX inspection MediaAsset to this prospect. Used by
+     * {@link MatchInspectionPairxEnricher} in Phase C to enrich
+     * prospects after the MatchResult has been persisted (empty-match-
+     * prospects design Track 2 C13: PairX is now non-blocking and
+     * runs without holding the outer Shepherd across HTTP).
+     */
+    public void setAsset(MediaAsset asset) {
+        this.asset = asset;
+    }
+
+    public MediaAsset getAsset() {
+        return asset;
+    }
+
+    public Annotation getAnnotation() {
+        return annotation;
+    }
+
+    public double getScore() {
+        return score;
+    }
+
+    public String getType() {
+        return scoreType;
+    }
+
+    public boolean isType(String type) {
+        if (type == null) return (this.scoreType == null);
+        return type.equals(this.scoreType);
+    }
+
+    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
+        // if we have no projects to filter on, we consider this to be in it
+        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
+        if (annotation == null) return false;
+        Encounter enc = annotation.findEncounter(myShepherd);
+        if (enc == null) return false;
+        return enc.isInProjects(projectIds, myShepherd);
+    }
+
+    public String toString() {
+        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
+    }
+
+    public JSONObject jsonForApiGet(Shepherd myShepherd) {
+        JSONObject rtn = new JSONObject();
+
+        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
+        rtn.put("score", score);
+        // skipping scoreType since this is currently only used filtered by scoreType already
+        if (asset != null) {
+            JSONObject aj = asset.toSimpleJSONObject();
+            aj.put("url", asset.webURL()); // we have no "safe" url
+            rtn.put("asset", aj);
+        }
+        return rtn;
+    }
+
+    // used in sorting
+    @Override public int compareTo(MatchResultProspect other) {
+        // we invert this so higher score is first
+        int comp = Double.compare(other.score, this.score);
+        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
+        // ordering (otherwise tied scores come back random order), so we use annot id
+        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
+            return this.annotation.getId().compareTo(other.annotation.getId());
+        // scores are *not* equal, so we just let comparison stand as-is
+        return comp;
+    }
+}
diff --git a/src/main/java/org/ecocean/ia/MlServiceProcessor.java b/src/main/java/org/ecocean/ia/MlServiceProcessor.java
index e053fa7e8..363fbf467 100644
--- a/src/main/java/org/ecocean/ia/MlServiceProcessor.java
+++ b/src/main/java/org/ecocean/ia/MlServiceProcessor.java
@@ -542,7 +542,16 @@ public class MlServiceProcessor {
                 return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
                     "no usable vector match config");
             }
+            String matchTaskId = matchTask.getId();
             shep.commitDBTransaction();
+            shep.rollbackAndClose();  // close BEFORE PairX enrichment (Track 2 C13)
+            // Phase 4 (C13): PairX inspection-image enrichment. The
+            // MatchResult + prospects are already persisted with
+            // null inspection MediaAssets; the enricher fills them in
+            // out-of-transaction via a Phase A/B/C flow per prospect.
+            // Per-prospect failure is non-blocking — UI render works
+            // either way, just without the inspection image.
+            enrichPairxAssetsForMatchTask(matchTaskId);
             return MlServiceJobOutcome.ok(annotationIds);
         } catch (Exception ex) {
             markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
@@ -552,6 +561,71 @@ public class MlServiceProcessor {
         }
     }
 
+    /**
+     * Phase 4: drive {@link MatchInspectionPairxEnricher} for every
+     * MatchResult attached to a child of {@code matchTaskId}. Runs
+     * after the main runMatchProspects transaction has closed, so the
+     * PairX HTTP work doesn't hold a Shepherd. (Empty-match-prospects
+     * design Track 2 C13.)
+     */
+    void enrichPairxAssetsForMatchTask(String matchTaskId) {
+        if (matchTaskId == null) return;
+        List<String> mrIds = collectMatchResultIds(matchTaskId);
+        if (mrIds.isEmpty()) return;
+        MatchInspectionPairxEnricher enricher =
+            new MatchInspectionPairxEnricher(context);
+        for (String mrId : mrIds) {
+            try {
+                enricher.enrichMatchResult(mrId);
+            } catch (Exception ex) {
+                System.out.println(
+                    "[WARN] MlServiceProcessor.enrichPairxAssetsForMatchTask " +
+                    "mr=" + mrId + " failed (non-blocking): " + ex);
+            }
+        }
+    }
+
+    /**
+     * Open a short Shepherd, list MatchResult IDs attached to children
+     * of {@code matchTaskId}, close. Returns scalar IDs only so
+     * subsequent enrichment runs without DB state.
+     */
+    private List<String> collectMatchResultIds(String matchTaskId) {
+        List<String> out = new ArrayList<String>();
+        Shepherd shep = new Shepherd(context);
+        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
+        try {
+            shep.beginDBTransaction();
+            Task matchTask = Task.load(matchTaskId, shep);
+            if (matchTask == null) {
+                shep.commitDBTransaction();
+                return out;
+            }
+            List<Task> children = matchTask.getChildren();
+            if (children != null) {
+                for (Task child : children) {
+                    if (child == null) continue;
+                    List<MatchResult> mrs = shep.getMatchResults(child);
+                    if (mrs == null) continue;
+                    for (MatchResult mr : mrs) {
+                        if (mr != null && mr.getId() != null) {
+                            out.add(mr.getId());
+                        }
+                    }
+                }
+            }
+            shep.commitDBTransaction();
+        } catch (Exception ex) {
+            shep.rollbackDBTransaction();
+            System.out.println(
+                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
+                matchTaskId + ": " + ex);
+        } finally {
+            shep.closeDBTransaction();
+        }
+        return out;
+    }
+
     static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
         String code = ex == null ? null : ex.getCode();
         String message = ex == null ? null : ex.getMessage();


## New files:


### `MatchInspectionPairxEnricher.java`

```java
package org.ecocean.ia;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.RestClient;
import org.ecocean.Util;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Phase A/B/C orchestrator that enriches a persisted
 * {@link MatchResult}'s prospects with PairX inspection MediaAssets.
 * Replaces the previous in-{@link MatchResult}-constructor PairX calls
 * that violated the "never hold a Shepherd across HTTP" convention.
 *
 * <p>Per-prospect flow:</p>
 * <ol>
 *   <li><b>Phase A (Shepherd open, short tx):</b>
 *       {@link #loadDtos(String)} loads the MatchResult, walks its
 *       prospects, and builds a list of {@link PairxDto} carrying every
 *       scalar Phase B and Phase C need. Shepherd closes before any HTTP.</li>
 *   <li><b>Phase B (no Shepherd):</b>
 *       {@link #postPairxAndExtractBase64(PairxDto)} runs the
 *       {@code /explain/} HTTP POST. No JDO state held.</li>
 *   <li><b>Phase C (fresh Shepherd, per prospect):</b>
 *       {@link #persistInspectionAsset(PairxDto, String)} opens a fresh
 *       short-lived Shepherd, creates a MediaAsset from the base64
 *       payload, attaches it to the prospect, commits.</li>
 * </ol>
 *
 * <p>Per-prospect failure is non-blocking: an HTTP timeout or persist
 * error on one prospect logs and continues; other prospects in the
 * same MatchResult still get processed.</p>
 *
 * <p>(Empty-match-prospects design Track 2 C13.)</p>
 */
public final class MatchInspectionPairxEnricher {

    private final String context;

    public MatchInspectionPairxEnricher(String context) {
        this.context = context;
    }

    /**
     * For each prospect of the named MatchResult that lacks an
     * inspection MediaAsset, run PairX out-of-transaction and attach
     * the resulting image.
     *
     * <p>Returns the number of prospects that received a new inspection
     * MediaAsset.</p>
     */
    public int enrichMatchResult(String matchResultId) {
        if (matchResultId == null) return 0;
        List<PairxDto> dtos;
        try {
            dtos = loadDtos(matchResultId);
        } catch (Exception ex) {
            System.out.println(
                "[WARN] MatchInspectionPairxEnricher.loadDtos failed for mr=" +
                matchResultId + ": " + ex);
            return 0;
        }
        int enriched = 0;
        for (PairxDto dto : dtos) {
            String b64;
            try {
                b64 = postPairxAndExtractBase64(dto);
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MatchInspectionPairxEnricher Phase B HTTP failed for ann=" +
                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
                continue;
            }
            if (b64 == null) continue;
            try {
                if (persistInspectionAsset(dto, b64)) enriched++;
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MatchInspectionPairxEnricher Phase C persist failed for ann=" +
                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
            }
        }
        System.out.println("[INFO] MatchInspectionPairxEnricher enriched " + enriched +
            "/" + dtos.size() + " prospects on mr=" + matchResultId);
        return enriched;
    }

    /**
     * Phase A: load all PairxDtos for the given MatchResult under one
     * short Shepherd transaction. Returns detached, scalar-only DTOs.
     */
    List<PairxDto> loadDtos(String matchResultId) {
        List<PairxDto> out = new ArrayList<PairxDto>();
        Shepherd shep = new Shepherd(context);
        shep.setAction("PairxEnricher.loadDtos." + matchResultId);
        try {
            shep.beginDBTransaction();
            MatchResult mr = shep.getMatchResult(matchResultId);
            if (mr == null) {
                shep.commitDBTransaction();
                return out;
            }
            Annotation queryAnn = mr.getQueryAnnotation();
            if (queryAnn == null) {
                shep.commitDBTransaction();
                return out;
            }
            String taxonomy = null;
            Encounter qEnc = queryAnn.findEncounter(shep);
            if (qEnc != null) taxonomy = qEnc.getTaxonomyString();
            String queryImageUri = imageUriOf(queryAnn);
            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
            double queryTheta = queryAnn.getTheta();
            if (mr.getProspects() != null) {
                for (MatchResultProspect prospect : mr.getProspects()) {
                    // Skip prospects that already have an inspection image
                    // (idempotent retry — Phase C may run twice on the
                    // same MatchResult under operator-driven re-fire).
                    if (prospect.getAsset() != null) continue;
                    Annotation pAnn = prospect.getAnnotation();
                    if (pAnn == null) continue;
                    String prospectImageUri = imageUriOf(pAnn);
                    int[] prospectBbox = MatchResult.clampBbox(pAnn.getBbox());
                    double prospectTheta = pAnn.getTheta();
                    out.add(new PairxDto(
                        matchResultId, pAnn.getId(), prospect.getType(),
                        taxonomy, queryImageUri, prospectImageUri,
                        queryBbox, prospectBbox, queryTheta, prospectTheta));
                }
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            throw new RuntimeException(ex);
        } finally {
            shep.closeDBTransaction();
        }
        return out;
    }

    private static String imageUriOf(Annotation ann) {
        if (ann == null) return null;
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null) return null;
        URL url = ma.webURL();
        return (url == null) ? null : url.toString();
    }

    /**
     * Phase B: POST to {@code /explain/} and extract the base64 image.
     * No Shepherd held. Returns null on any non-fatal condition (degenerate
     * bbox, missing URL, empty response). Throws on HTTP failure so the
     * caller can log per-prospect.
     */
    String postPairxAndExtractBase64(PairxDto dto) throws IOException {
        if (dto == null) return null;
        if (!Util.stringExists(dto.queryImageUri) ||
            !Util.stringExists(dto.prospectImageUri)) return null;
        if (MatchResult.isDegenerateBbox(dto.queryBbox) ||
            MatchResult.isDegenerateBbox(dto.prospectBbox)) {
            System.out.println(
                "[INFO] PairxEnricher skipping degenerate bbox for ann=" +
                dto.prospectAnnotationId);
            return null;
        }
        if (!Util.stringExists(dto.taxonomyString)) return null;
        URL pairxUrl = MatchResult._getPairxUrl(dto.taxonomyString);
        if (pairxUrl == null) return null;
        JSONObject payload = buildPayload(dto);
        JSONObject res = RestClient.postJSON(pairxUrl, payload, null);
        if (res == null) return null;
        JSONArray imgs = res.optJSONArray("images");
        if ((imgs == null) || (imgs.length() < 1)) return null;
        String b64 = imgs.optString(0, null);
        if (!Util.stringExists(b64)) return null;
        return b64;
    }

    /**
     * Build the {@code /explain/} POST body. Pure function; package-
     * visible for unit-testing. Mirrors the body the legacy
     * {@code MatchResult.createInspectionPairxAsset} sent, with the
     * C12 clampBbox and addBboxPayload fixes baked in.
     */
    static JSONObject buildPayload(PairxDto dto) {
        JSONObject payload = new JSONObject();
        payload.put("algorithm", "pairx");
        payload.put("visualization_type", "only_colors");
        payload.put("k_colors", 5);
        payload.put("model_id", "miewid-msv4.1");
        payload.put("crop_bbox", false);
        payload.put("layer_key", "backbone.blocks.3");
        payload.put("image1_uris", new JSONArray().put(dto.queryImageUri));
        payload.put("image2_uris", new JSONArray().put(dto.prospectImageUri));
        payload.put("theta1", new JSONArray().put(dto.queryTheta));
        payload.put("theta2", new JSONArray().put(dto.prospectTheta));
        MatchResult.addBboxPayload(payload, dto.queryBbox, dto.prospectBbox);
        return payload;
    }

    /**
     * Phase C: persist a new MediaAsset under a fresh Shepherd and
     * attach it to the prospect identified by (annotationId, scoreType)
     * within the MatchResult. Returns true on successful attach, false
     * if the prospect couldn't be located.
     */
    boolean persistInspectionAsset(PairxDto dto, String b64) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("PairxEnricher.persist." + dto.matchResultId + "." +
            dto.prospectAnnotationId);
        try {
            shep.beginDBTransaction();
            MatchResult mr = shep.getMatchResult(dto.matchResultId);
            if (mr == null) {
                shep.rollbackDBTransaction();
                return false;
            }
            MatchResultProspect target = findProspect(mr, dto.prospectAnnotationId,
                dto.scoreType);
            if (target == null) {
                shep.rollbackDBTransaction();
                return false;
            }
            // Idempotency guard: if a parallel Phase C already attached an
            // asset to this prospect, don't double-attach.
            if (target.getAsset() != null) {
                shep.rollbackDBTransaction();
                return false;
            }
            AssetStore store = AssetStore.getDefault(shep);
            JSONObject params = store.createParameters(new File(
                Util.hashDirectories(dto.matchResultId) +
                "/pairx-" + dto.matchResultId + "-" +
                dto.prospectAnnotationId + "-" + dto.scoreType + ".png"));
            MediaAsset ma = store.create(params);
            ma.copyInBase64(b64);
            ma.addLabel("matchInspectionPairx");
            shep.getPM().makePersistent(ma);
            target.setAsset(ma);
            shep.commitDBTransaction();
            return true;
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            throw new RuntimeException(ex);
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Find a prospect in the given MatchResult by (annotationId, scoreType).
     * Package-visible for tests.
     */
    static MatchResultProspect findProspect(MatchResult mr, String annotationId,
        String scoreType) {
        if (mr == null || mr.getProspects() == null) return null;
        for (MatchResultProspect p : mr.getProspects()) {
            Annotation a = p.getAnnotation();
            if (a == null) continue;
            if (!annotationId.equals(a.getId())) continue;
            String t = p.getType();
            if (scoreType == null) {
                if (t == null) return p;
            } else if (scoreType.equals(t)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Plain-data carrier for one prospect's PairX inputs. Captured under
     * Shepherd in Phase A, immutable through Phase B + C.
     */
    static final class PairxDto {
        final String matchResultId;
        final String prospectAnnotationId;
        final String scoreType;             // "annot" or "indiv"
        final String taxonomyString;
        final String queryImageUri;
        final String prospectImageUri;
        final int[] queryBbox;              // pre-clamped
        final int[] prospectBbox;           // pre-clamped
        final double queryTheta;
        final double prospectTheta;

        PairxDto(String matchResultId, String prospectAnnotationId, String scoreType,
            String taxonomyString, String queryImageUri, String prospectImageUri,
            int[] queryBbox, int[] prospectBbox,
            double queryTheta, double prospectTheta) {
            this.matchResultId = matchResultId;
            this.prospectAnnotationId = prospectAnnotationId;
            this.scoreType = scoreType;
            this.taxonomyString = taxonomyString;
            this.queryImageUri = queryImageUri;
            this.prospectImageUri = prospectImageUri;
            this.queryBbox = queryBbox;
            this.prospectBbox = prospectBbox;
            this.queryTheta = queryTheta;
            this.prospectTheta = prospectTheta;
        }
    }
}
```

### `MatchInspectionPairxEnricherTest.java`

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of
 * {@link MatchInspectionPairxEnricher#buildPayload} and
 * {@link MatchInspectionPairxEnricher#findProspect}. Phase A/C paths
 * that require Shepherd are exercised by integration on the live
 * deployment — same precedent as the C6/C9 paths. (Empty-match-
 * prospects design Track 2 C13.)
 */
class MatchInspectionPairxEnricherTest {

    private static MatchInspectionPairxEnricher.PairxDto sampleDto() {
        return new MatchInspectionPairxEnricher.PairxDto(
            "mr-1", "ann-2", "annot",
            "Salamandra salamandra",
            "https://example.com/img1.jpg",
            "https://example.com/img2.jpg",
            new int[] { 10, 20, 100, 200 },
            new int[] { 30, 40, 150, 300 },
            0.0d, 0.5d);
    }

    // --- buildPayload ---------------------------------------------------

    @Test void buildPayload_setsAllFixedKeys() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        assertEquals("pairx", p.getString("algorithm"));
        assertEquals("only_colors", p.getString("visualization_type"));
        assertEquals(5, p.getInt("k_colors"));
        assertEquals("miewid-msv4.1", p.getString("model_id"));
        assertEquals(false, p.getBoolean("crop_bbox"));
        assertEquals("backbone.blocks.3", p.getString("layer_key"));
    }

    @Test void buildPayload_setsImageUrisAsSeparateArrays() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        JSONArray image1 = p.getJSONArray("image1_uris");
        JSONArray image2 = p.getJSONArray("image2_uris");
        assertEquals("https://example.com/img1.jpg", image1.getString(0));
        assertEquals("https://example.com/img2.jpg", image2.getString(0));
    }

    @Test void buildPayload_setsThetaArrays() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        assertEquals(0.0d, p.getJSONArray("theta1").getDouble(0));
        assertEquals(0.5d, p.getJSONArray("theta2").getDouble(0));
    }

    @Test void buildPayload_bb1AndBb2AreDistinctReferences() {
        // C12 regression guard: previous implementation reused one
        // JSONArray for both keys; this test fails if the enricher
        // reintroduces that bug.
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        JSONArray bb1 = p.getJSONArray("bb1");
        JSONArray bb2 = p.getJSONArray("bb2");
        assertNotSame(bb1, bb2);
        // Confirm bb1 and bb2 contain the DTO's distinct bboxes.
        assertEquals(10, bb1.getJSONArray(0).getInt(0));
        assertEquals(30, bb2.getJSONArray(0).getInt(0));
    }

    @Test void buildPayload_doubleArrayShape() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        // PairX expects [[x, y, w, h]] for each bbox key.
        JSONArray bb1Outer = p.getJSONArray("bb1");
        assertEquals(1, bb1Outer.length());
        JSONArray inner = bb1Outer.getJSONArray(0);
        assertEquals(4, inner.length());
    }

    // --- findProspect ---------------------------------------------------

    @Test void findProspect_returnsNullForNullMatchResult() {
        assertNull(MatchInspectionPairxEnricher.findProspect(null, "x", "annot"));
    }

    @Test void findProspect_returnsNullWhenProspectsNull() {
        MatchResult mr = new MatchResult();
        // prospects field is null by default
        assertNull(MatchInspectionPairxEnricher.findProspect(mr, "x", "annot"));
    }

    @Test void findProspect_matchesByAnnotationIdAndScoreType() {
        MatchResult mr = new MatchResult();
        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
        a1.setId("ann-1");
        org.ecocean.Annotation a2 = new org.ecocean.Annotation();
        a2.setId("ann-2");
        MatchResultProspect p1 = new MatchResultProspect(a1, 1.0d, "annot", null);
        MatchResultProspect p2 = new MatchResultProspect(a2, 0.5d, "annot", null);
        // Inject via the populateProspects loop is private; we exercise
        // findProspect by directly constructing a MatchResult and
        // adding via reflection on the prospects field.
        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
        set.add(p1);
        set.add(p2);
        injectProspects(mr, set);
        MatchResultProspect found = MatchInspectionPairxEnricher.findProspect(
            mr, "ann-2", "annot");
        assertNotNull(found);
        assertEquals(a2, found.getAnnotation());
    }

    @Test void findProspect_distinguishesByScoreType() {
        MatchResult mr = new MatchResult();
        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
        a1.setId("ann-1");
        MatchResultProspect annotP = new MatchResultProspect(a1, 1.0d, "annot", null);
        MatchResultProspect indivP = new MatchResultProspect(a1, 0.8d, "indiv", null);
        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
        set.add(annotP);
        set.add(indivP);
        injectProspects(mr, set);
        MatchResultProspect foundAnnot = MatchInspectionPairxEnricher.findProspect(
            mr, "ann-1", "annot");
        MatchResultProspect foundIndiv = MatchInspectionPairxEnricher.findProspect(
            mr, "ann-1", "indiv");
        assertNotNull(foundAnnot);
        assertNotNull(foundIndiv);
        assertTrue(foundAnnot.isType("annot"));
        assertTrue(foundIndiv.isType("indiv"));
        assertNotSame(foundAnnot, foundIndiv);
    }

    @Test void findProspect_returnsNullWhenAnnotationIdMissing() {
        MatchResult mr = new MatchResult();
        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
        a1.setId("ann-1");
        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
        set.add(new MatchResultProspect(a1, 1.0d, "annot", null));
        injectProspects(mr, set);
        assertNull(MatchInspectionPairxEnricher.findProspect(mr, "ann-MISSING", "annot"));
    }

    @SuppressWarnings("unchecked")
    private static void injectProspects(MatchResult mr,
        java.util.HashSet<MatchResultProspect> prospects) {
        try {
            java.lang.reflect.Field f = MatchResult.class.getDeclaredField("prospects");
            f.setAccessible(true);
            f.set(mr, prospects);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
```


## Validation

- MatchInspectionPairxEnricherTest: 10/10
- MatchResultClampBboxTest (C12): 19/19
- MatchResultTest, MlServiceProcessorTest, full project suite:
  406 tests, 0 failures, 7 pre-existing skips
- Compiles cleanly
- LF-normalized

## Review scope

- Phase A/B/C boundaries: Shepherd is opened-and-closed three
  times (Phase A loadDtos = once; Phase C persistInspectionAsset
  = once per DTO). No Shepherd ever crosses the
  RestClient.postJSON in Phase B.
- Idempotency: Phase A skips prospects that already have an asset;
  Phase C double-checks at persist time before attaching. Safe
  for retry/re-fire.
- Per-prospect failure isolation: any exception in Phase B or C
  is caught and logged; other prospects in the same MatchResult
  proceed. The MatchResult itself is unaffected.
- Identifier lookup: prospects are identified by
  (annotationId, scoreType) within a MatchResult — that tuple is
  unique because populateProspects creates at most one entry per
  (ann, type) combination.
- Wiring: MlServiceProcessor.runMatchProspects commits + closes
  Shepherd, THEN calls enrichPairxAssetsForMatchTask. The
  enricher's failure can't roll back the already-persisted
  MatchResult.
- collectMatchResultIds in MlServiceProcessor uses
  Shepherd.getMatchResults(Task) which exists at Shepherd.java:2820.

Anything I missed? Especially:
- Race conditions if the same MatchResult is processed twice
  (idempotency seems solid but worth a sanity check).
- AssetStore default behavior for the chosen file path.
- Whether the enrichment should be queued (true async) rather
  than synchronous-after-close. Current is sync-after-close:
  the job thread waits, but no Shepherd is held.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll review this against the live workspace rather than only the pasted diff, focusing on transaction boundaries, JDO persistence shape, and whether the new identifiers are actually stable for Phase C.
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/ia/MatchResult.java src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class MatchInspectionPairxEnricher|enrichPairxAssetsForMatchTask|collectMatchResultIds|populateProspects|getProspects\\(|getQueryAnnotation\\(|createInspectionPairxAsset|_getPairxUrl|addBboxPayload|clampBbox|isDegenerateBbox\" src/main/java/org/ecocean/ia src/test/java/org/ecocean/ia" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short && git branch --show-current' in /mnt/c/Wildbook-clean2
 succeeded in 559ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MatchResult.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/MatchResultProspect.java
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MlServiceProcessor.java

 succeeded in 577ms:
src/main/java/org/ecocean/ia/MatchResult.java:85:        this.populateProspects(annots, false, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:86:        this.populateProspects(annots, true, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:149:        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
src/main/java/org/ecocean/ia/MatchResult.java:152:        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
src/main/java/org/ecocean/ia/MatchResult.java:158:    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
src/main/java/org/ecocean/ia/MatchResult.java:203:    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
src/main/java/org/ecocean/ia/MatchResult.java:211:            _populateProspectsByIndividual(annots, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:223:    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:266:    public Set<MatchResultProspect> getProspects() {
src/main/java/org/ecocean/ia/MatchResult.java:276:    public Annotation getQueryAnnotation() {
src/main/java/org/ecocean/ia/MatchResult.java:344:    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
src/main/java/org/ecocean/ia/MatchResult.java:365:        // bb1 / bb2 payload construction. See addBboxPayload Javadoc for
src/main/java/org/ecocean/ia/MatchResult.java:370:        int[] clamped1 = clampBbox(ann1.getBbox());
src/main/java/org/ecocean/ia/MatchResult.java:371:        int[] clamped2 = clampBbox(ann2.getBbox());
src/main/java/org/ecocean/ia/MatchResult.java:372:        if (isDegenerateBbox(clamped1) || isDegenerateBbox(clamped2)) {
src/main/java/org/ecocean/ia/MatchResult.java:374:                "[INFO] createInspectionPairxAsset() skipping PairX for ann1=" +
src/main/java/org/ecocean/ia/MatchResult.java:381:        addBboxPayload(payload, clamped1, clamped2);
src/main/java/org/ecocean/ia/MatchResult.java:387:            pairxUrl = _getPairxUrl(enc.getTaxonomyString());
src/main/java/org/ecocean/ia/MatchResult.java:391:            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
src/main/java/org/ecocean/ia/MatchResult.java:401:        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
src/main/java/org/ecocean/ia/MatchResult.java:410:            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
src/main/java/org/ecocean/ia/MatchResult.java:415:                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
src/main/java/org/ecocean/ia/MatchResult.java:434:    static int[] clampBbox(int[] bbox) {
src/main/java/org/ecocean/ia/MatchResult.java:462:     * shape after {@link #clampBbox} on a box that lies entirely off-
src/main/java/org/ecocean/ia/MatchResult.java:466:    static boolean isDegenerateBbox(int[] bbox) {
src/main/java/org/ecocean/ia/MatchResult.java:483:     *   <li>{@link #clampBbox} (called by the production entry point
src/main/java/org/ecocean/ia/MatchResult.java:492:    static void addBboxPayload(JSONObject payload, int[] bbox1, int[] bbox2) {
src/main/java/org/ecocean/ia/MatchResult.java:497:    public static URL _getPairxUrl(String txStr)
src/main/java/org/ecocean/ia/MlServiceProcessor.java:554:            enrichPairxAssetsForMatchTask(matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:571:    void enrichPairxAssetsForMatchTask(String matchTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:573:        List<String> mrIds = collectMatchResultIds(matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:582:                    "[WARN] MlServiceProcessor.enrichPairxAssetsForMatchTask " +
src/main/java/org/ecocean/ia/MlServiceProcessor.java:593:    private List<String> collectMatchResultIds(String matchTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:596:        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:621:                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:46:public final class MatchInspectionPairxEnricher {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:113:            Annotation queryAnn = mr.getQueryAnnotation();
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:122:            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:124:            if (mr.getProspects() != null) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:125:                for (MatchResultProspect prospect : mr.getProspects()) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:133:                    int[] prospectBbox = MatchResult.clampBbox(pAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:169:        if (MatchResult.isDegenerateBbox(dto.queryBbox) ||
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:170:            MatchResult.isDegenerateBbox(dto.prospectBbox)) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:177:        URL pairxUrl = MatchResult._getPairxUrl(dto.taxonomyString);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:192:     * {@code MatchResult.createInspectionPairxAsset} sent, with the
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:193:     * C12 clampBbox and addBboxPayload fixes baked in.
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:207:        MatchResult.addBboxPayload(payload, dto.queryBbox, dto.prospectBbox);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:266:        if (mr == null || mr.getProspects() == null) return null;
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:267:        for (MatchResultProspect p : mr.getProspects()) {
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:21:class MatchInspectionPairxEnricherTest {
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:102:        // Inject via the populateProspects loop is private; we exercise
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:18:    // --- clampBbox -------------------------------------------------------
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:20:    @Test void clampBbox_passesThroughPositiveValues() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:23:            MatchResult.clampBbox(in));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:26:    @Test void clampBbox_clampsNegativeX_shrinksWidth() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:31:            MatchResult.clampBbox(in));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:34:    @Test void clampBbox_clampsNegativeY_shrinksHeight() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:37:            MatchResult.clampBbox(in));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:40:    @Test void clampBbox_clampsBothXAndY_independently() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:43:            MatchResult.clampBbox(in));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:46:    @Test void clampBbox_clampsWidthFloorToZero_whenAbsXExceedsW() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:52:            MatchResult.clampBbox(in));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:55:    @Test void clampBbox_clampsHeightFloorToZero_whenAbsYExceedsH() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:58:            MatchResult.clampBbox(in));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:61:    @Test void clampBbox_returnsInput_whenNull() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:62:        assertNull(MatchResult.clampBbox(null));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:65:    @Test void clampBbox_returnsInput_whenShorterThanFour() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:67:        assertArrayEquals(in, MatchResult.clampBbox(in));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:70:    @Test void clampBbox_doesNotMutateInput() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:72:        MatchResult.clampBbox(in);
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:98:    // --- isDegenerateBbox ------------------------------------------------
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:100:    @Test void isDegenerateBbox_falseForPositiveDims() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:102:            MatchResult.isDegenerateBbox(new int[] { 0, 0, 100, 200 }));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:105:    @Test void isDegenerateBbox_trueForZeroWidth() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:107:            MatchResult.isDegenerateBbox(new int[] { 0, 0, 0, 200 }));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:110:    @Test void isDegenerateBbox_trueForZeroHeight() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:112:            MatchResult.isDegenerateBbox(new int[] { 0, 0, 100, 0 }));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:115:    @Test void isDegenerateBbox_trueForNull() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:116:        org.junit.jupiter.api.Assertions.assertTrue(MatchResult.isDegenerateBbox(null));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:119:    @Test void isDegenerateBbox_trueForShortArray() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:121:            MatchResult.isDegenerateBbox(new int[] { 1, 2, 3 }));
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:124:    // --- addBboxPayload (bug-1 regression guard) -------------------------
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:126:    @Test void addBboxPayload_bb1AndBb2AreDistinctReferences() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:133:        MatchResult.addBboxPayload(payload, bbox1, bbox2);
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:143:    @Test void addBboxPayload_buildsDoubleArrayShape() {
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:146:        MatchResult.addBboxPayload(payload,

codex
The first pass already surfaced one concrete repo hygiene issue: `MatchResultProspect.java` is checked in as LF but the worktree copy is CRLF (`i/lf w/crlf`). I’m going deeper on the runtime behavior now, especially whether Phase C can reliably find and update the exact persisted prospect.
 succeeded in 11291ms:
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
 M src/main/java/org/ecocean/ia/MlServiceProcessor.java
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
?? src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java
?? src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java
migrate-ml-service-v2

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java | sed -n '1,340p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '1,540p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResultProspect.java | sed -n '1,140p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '500,640p'" in /mnt/c/Wildbook-clean2
 succeeded in 204ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.File;
     4	import java.io.IOException;
     5	import java.net.URL;
     6	import java.util.ArrayList;
     7	import java.util.List;
     8	
     9	import org.ecocean.Annotation;
    10	import org.ecocean.Encounter;
    11	import org.ecocean.RestClient;
    12	import org.ecocean.Util;
    13	import org.ecocean.media.AssetStore;
    14	import org.ecocean.media.MediaAsset;
    15	import org.ecocean.shepherd.core.Shepherd;
    16	import org.json.JSONArray;
    17	import org.json.JSONObject;
    18	
    19	/**
    20	 * Phase A/B/C orchestrator that enriches a persisted
    21	 * {@link MatchResult}'s prospects with PairX inspection MediaAssets.
    22	 * Replaces the previous in-{@link MatchResult}-constructor PairX calls
    23	 * that violated the "never hold a Shepherd across HTTP" convention.
    24	 *
    25	 * <p>Per-prospect flow:</p>
    26	 * <ol>
    27	 *   <li><b>Phase A (Shepherd open, short tx):</b>
    28	 *       {@link #loadDtos(String)} loads the MatchResult, walks its
    29	 *       prospects, and builds a list of {@link PairxDto} carrying every
    30	 *       scalar Phase B and Phase C need. Shepherd closes before any HTTP.</li>
    31	 *   <li><b>Phase B (no Shepherd):</b>
    32	 *       {@link #postPairxAndExtractBase64(PairxDto)} runs the
    33	 *       {@code /explain/} HTTP POST. No JDO state held.</li>
    34	 *   <li><b>Phase C (fresh Shepherd, per prospect):</b>
    35	 *       {@link #persistInspectionAsset(PairxDto, String)} opens a fresh
    36	 *       short-lived Shepherd, creates a MediaAsset from the base64
    37	 *       payload, attaches it to the prospect, commits.</li>
    38	 * </ol>
    39	 *
    40	 * <p>Per-prospect failure is non-blocking: an HTTP timeout or persist
    41	 * error on one prospect logs and continues; other prospects in the
    42	 * same MatchResult still get processed.</p>
    43	 *
    44	 * <p>(Empty-match-prospects design Track 2 C13.)</p>
    45	 */
    46	public final class MatchInspectionPairxEnricher {
    47	
    48	    private final String context;
    49	
    50	    public MatchInspectionPairxEnricher(String context) {
    51	        this.context = context;
    52	    }
    53	
    54	    /**
    55	     * For each prospect of the named MatchResult that lacks an
    56	     * inspection MediaAsset, run PairX out-of-transaction and attach
    57	     * the resulting image.
    58	     *
    59	     * <p>Returns the number of prospects that received a new inspection
    60	     * MediaAsset.</p>
    61	     */
    62	    public int enrichMatchResult(String matchResultId) {
    63	        if (matchResultId == null) return 0;
    64	        List<PairxDto> dtos;
    65	        try {
    66	            dtos = loadDtos(matchResultId);
    67	        } catch (Exception ex) {
    68	            System.out.println(
    69	                "[WARN] MatchInspectionPairxEnricher.loadDtos failed for mr=" +
    70	                matchResultId + ": " + ex);
    71	            return 0;
    72	        }
    73	        int enriched = 0;
    74	        for (PairxDto dto : dtos) {
    75	            String b64;
    76	            try {
    77	                b64 = postPairxAndExtractBase64(dto);
    78	            } catch (Exception ex) {
    79	                System.out.println(
    80	                    "[WARN] MatchInspectionPairxEnricher Phase B HTTP failed for ann=" +
    81	                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
    82	                continue;
    83	            }
    84	            if (b64 == null) continue;
    85	            try {
    86	                if (persistInspectionAsset(dto, b64)) enriched++;
    87	            } catch (Exception ex) {
    88	                System.out.println(
    89	                    "[WARN] MatchInspectionPairxEnricher Phase C persist failed for ann=" +
    90	                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
    91	            }
    92	        }
    93	        System.out.println("[INFO] MatchInspectionPairxEnricher enriched " + enriched +
    94	            "/" + dtos.size() + " prospects on mr=" + matchResultId);
    95	        return enriched;
    96	    }
    97	
    98	    /**
    99	     * Phase A: load all PairxDtos for the given MatchResult under one
   100	     * short Shepherd transaction. Returns detached, scalar-only DTOs.
   101	     */
   102	    List<PairxDto> loadDtos(String matchResultId) {
   103	        List<PairxDto> out = new ArrayList<PairxDto>();
   104	        Shepherd shep = new Shepherd(context);
   105	        shep.setAction("PairxEnricher.loadDtos." + matchResultId);
   106	        try {
   107	            shep.beginDBTransaction();
   108	            MatchResult mr = shep.getMatchResult(matchResultId);
   109	            if (mr == null) {
   110	                shep.commitDBTransaction();
   111	                return out;
   112	            }
   113	            Annotation queryAnn = mr.getQueryAnnotation();
   114	            if (queryAnn == null) {
   115	                shep.commitDBTransaction();
   116	                return out;
   117	            }
   118	            String taxonomy = null;
   119	            Encounter qEnc = queryAnn.findEncounter(shep);
   120	            if (qEnc != null) taxonomy = qEnc.getTaxonomyString();
   121	            String queryImageUri = imageUriOf(queryAnn);
   122	            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
   123	            double queryTheta = queryAnn.getTheta();
   124	            if (mr.getProspects() != null) {
   125	                for (MatchResultProspect prospect : mr.getProspects()) {
   126	                    // Skip prospects that already have an inspection image
   127	                    // (idempotent retry — Phase C may run twice on the
   128	                    // same MatchResult under operator-driven re-fire).
   129	                    if (prospect.getAsset() != null) continue;
   130	                    Annotation pAnn = prospect.getAnnotation();
   131	                    if (pAnn == null) continue;
   132	                    String prospectImageUri = imageUriOf(pAnn);
   133	                    int[] prospectBbox = MatchResult.clampBbox(pAnn.getBbox());
   134	                    double prospectTheta = pAnn.getTheta();
   135	                    out.add(new PairxDto(
   136	                        matchResultId, pAnn.getId(), prospect.getType(),
   137	                        taxonomy, queryImageUri, prospectImageUri,
   138	                        queryBbox, prospectBbox, queryTheta, prospectTheta));
   139	                }
   140	            }
   141	            shep.commitDBTransaction();
   142	        } catch (Exception ex) {
   143	            shep.rollbackDBTransaction();
   144	            throw new RuntimeException(ex);
   145	        } finally {
   146	            shep.closeDBTransaction();
   147	        }
   148	        return out;
   149	    }
   150	
   151	    private static String imageUriOf(Annotation ann) {
   152	        if (ann == null) return null;
   153	        MediaAsset ma = ann.getMediaAsset();
   154	        if (ma == null) return null;
   155	        URL url = ma.webURL();
   156	        return (url == null) ? null : url.toString();
   157	    }
   158	
   159	    /**
   160	     * Phase B: POST to {@code /explain/} and extract the base64 image.
   161	     * No Shepherd held. Returns null on any non-fatal condition (degenerate
   162	     * bbox, missing URL, empty response). Throws on HTTP failure so the
   163	     * caller can log per-prospect.
   164	     */
   165	    String postPairxAndExtractBase64(PairxDto dto) throws IOException {
   166	        if (dto == null) return null;
   167	        if (!Util.stringExists(dto.queryImageUri) ||
   168	            !Util.stringExists(dto.prospectImageUri)) return null;
   169	        if (MatchResult.isDegenerateBbox(dto.queryBbox) ||
   170	            MatchResult.isDegenerateBbox(dto.prospectBbox)) {
   171	            System.out.println(
   172	                "[INFO] PairxEnricher skipping degenerate bbox for ann=" +
   173	                dto.prospectAnnotationId);
   174	            return null;
   175	        }
   176	        if (!Util.stringExists(dto.taxonomyString)) return null;
   177	        URL pairxUrl = MatchResult._getPairxUrl(dto.taxonomyString);
   178	        if (pairxUrl == null) return null;
   179	        JSONObject payload = buildPayload(dto);
   180	        JSONObject res = RestClient.postJSON(pairxUrl, payload, null);
   181	        if (res == null) return null;
   182	        JSONArray imgs = res.optJSONArray("images");
   183	        if ((imgs == null) || (imgs.length() < 1)) return null;
   184	        String b64 = imgs.optString(0, null);
   185	        if (!Util.stringExists(b64)) return null;
   186	        return b64;
   187	    }
   188	
   189	    /**
   190	     * Build the {@code /explain/} POST body. Pure function; package-
   191	     * visible for unit-testing. Mirrors the body the legacy
   192	     * {@code MatchResult.createInspectionPairxAsset} sent, with the
   193	     * C12 clampBbox and addBboxPayload fixes baked in.
   194	     */
   195	    static JSONObject buildPayload(PairxDto dto) {
   196	        JSONObject payload = new JSONObject();
   197	        payload.put("algorithm", "pairx");
   198	        payload.put("visualization_type", "only_colors");
   199	        payload.put("k_colors", 5);
   200	        payload.put("model_id", "miewid-msv4.1");
   201	        payload.put("crop_bbox", false);
   202	        payload.put("layer_key", "backbone.blocks.3");
   203	        payload.put("image1_uris", new JSONArray().put(dto.queryImageUri));
   204	        payload.put("image2_uris", new JSONArray().put(dto.prospectImageUri));
   205	        payload.put("theta1", new JSONArray().put(dto.queryTheta));
   206	        payload.put("theta2", new JSONArray().put(dto.prospectTheta));
   207	        MatchResult.addBboxPayload(payload, dto.queryBbox, dto.prospectBbox);
   208	        return payload;
   209	    }
   210	
   211	    /**
   212	     * Phase C: persist a new MediaAsset under a fresh Shepherd and
   213	     * attach it to the prospect identified by (annotationId, scoreType)
   214	     * within the MatchResult. Returns true on successful attach, false
   215	     * if the prospect couldn't be located.
   216	     */
   217	    boolean persistInspectionAsset(PairxDto dto, String b64) {
   218	        Shepherd shep = new Shepherd(context);
   219	        shep.setAction("PairxEnricher.persist." + dto.matchResultId + "." +
   220	            dto.prospectAnnotationId);
   221	        try {
   222	            shep.beginDBTransaction();
   223	            MatchResult mr = shep.getMatchResult(dto.matchResultId);
   224	            if (mr == null) {
   225	                shep.rollbackDBTransaction();
   226	                return false;
   227	            }
   228	            MatchResultProspect target = findProspect(mr, dto.prospectAnnotationId,
   229	                dto.scoreType);
   230	            if (target == null) {
   231	                shep.rollbackDBTransaction();
   232	                return false;
   233	            }
   234	            // Idempotency guard: if a parallel Phase C already attached an
   235	            // asset to this prospect, don't double-attach.
   236	            if (target.getAsset() != null) {
   237	                shep.rollbackDBTransaction();
   238	                return false;
   239	            }
   240	            AssetStore store = AssetStore.getDefault(shep);
   241	            JSONObject params = store.createParameters(new File(
   242	                Util.hashDirectories(dto.matchResultId) +
   243	                "/pairx-" + dto.matchResultId + "-" +
   244	                dto.prospectAnnotationId + "-" + dto.scoreType + ".png"));
   245	            MediaAsset ma = store.create(params);
   246	            ma.copyInBase64(b64);
   247	            ma.addLabel("matchInspectionPairx");
   248	            shep.getPM().makePersistent(ma);
   249	            target.setAsset(ma);
   250	            shep.commitDBTransaction();
   251	            return true;
   252	        } catch (Exception ex) {
   253	            shep.rollbackDBTransaction();
   254	            throw new RuntimeException(ex);
   255	        } finally {
   256	            shep.closeDBTransaction();
   257	        }
   258	    }
   259	
   260	    /**
   261	     * Find a prospect in the given MatchResult by (annotationId, scoreType).
   262	     * Package-visible for tests.
   263	     */
   264	    static MatchResultProspect findProspect(MatchResult mr, String annotationId,
   265	        String scoreType) {
   266	        if (mr == null || mr.getProspects() == null) return null;
   267	        for (MatchResultProspect p : mr.getProspects()) {
   268	            Annotation a = p.getAnnotation();
   269	            if (a == null) continue;
   270	            if (!annotationId.equals(a.getId())) continue;
   271	            String t = p.getType();
   272	            if (scoreType == null) {
   273	                if (t == null) return p;
   274	            } else if (scoreType.equals(t)) {
   275	                return p;
   276	            }
   277	        }
   278	        return null;
   279	    }
   280	
   281	    /**
   282	     * Plain-data carrier for one prospect's PairX inputs. Captured under
   283	     * Shepherd in Phase A, immutable through Phase B + C.
   284	     */
   285	    static final class PairxDto {
   286	        final String matchResultId;
   287	        final String prospectAnnotationId;
   288	        final String scoreType;             // "annot" or "indiv"
   289	        final String taxonomyString;
   290	        final String queryImageUri;
   291	        final String prospectImageUri;
   292	        final int[] queryBbox;              // pre-clamped
   293	        final int[] prospectBbox;           // pre-clamped
   294	        final double queryTheta;
   295	        final double prospectTheta;
   296	
   297	        PairxDto(String matchResultId, String prospectAnnotationId, String scoreType,
   298	            String taxonomyString, String queryImageUri, String prospectImageUri,
   299	            int[] queryBbox, int[] prospectBbox,
   300	            double queryTheta, double prospectTheta) {
   301	            this.matchResultId = matchResultId;
   302	            this.prospectAnnotationId = prospectAnnotationId;
   303	            this.scoreType = scoreType;
   304	            this.taxonomyString = taxonomyString;
   305	            this.queryImageUri = queryImageUri;
   306	            this.prospectImageUri = prospectImageUri;
   307	            this.queryBbox = queryBbox;
   308	            this.prospectBbox = prospectBbox;
   309	            this.queryTheta = queryTheta;
   310	            this.prospectTheta = prospectTheta;
   311	        }
   312	    }
   313	}

 succeeded in 204ms:
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
   246	                return Integer.compare(two.getValue().size(), one.getValue().size());
   247	            }
   248	        });
   249	        int most = sorted.get(0).getValue().size(); // top num of annots
   250	        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
   251	            double score = new Double(ent.getValue().size()) / new Double(most);
   252	            // the ent value (annot List) should always have at least one annot, so we use first one.
   253	            // Inspection MediaAsset attached later by MatchInspectionPairxEnricher
   254	            // (empty-match-prospects design Track 2 C13).
   255	            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
   256	                null));
   257	        }
   258	    }
   259	
   260	    /**
   261	     * Public read-only view of the prospects collection so the
   262	     * {@link MatchInspectionPairxEnricher} can iterate them in Phase A
   263	     * and Phase C without reaching into private state. Returns the
   264	     * underlying Set; callers must not mutate.
   265	     */
   266	    public Set<MatchResultProspect> getProspects() {
   267	        return this.prospects;
   268	    }
   269	
   270	    /**
   271	     * Public accessor for the queryAnnotation field. Returns whatever
   272	     * value was set by {@link #setQueryAnnotationFromTask()} or
   273	     * {@link #createFromJsonResult(JSONObject, Shepherd)} — may be null
   274	     * if neither has run.
   275	     */
   276	    public Annotation getQueryAnnotation() {
   277	        return this.queryAnnotation;
   278	    }
   279	
   280	    /** Public accessor for the JDO primary key. */
   281	    public String getId() {
   282	        return this.id;
   283	    }
   284	
   285	    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
   286	        if (acmId == null) return null;
   287	        Annotation found = findAcmIdInTaskAnnotations(acmId);
   288	        if (found != null) return found;
   289	        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
   290	        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
   291	            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
   292	        if ((anns == null) || (anns.size() < 1)) return null;
   293	        return anns.get(0);
   294	    }
   295	
   296	    private Annotation findAcmIdInTaskAnnotations(String acmId) {
   297	        if ((this.task == null) || (acmId == null)) return null;
   298	        if (!this.task.hasObjectAnnotations()) return null;
   299	        for (Annotation ann : this.task.getObjectAnnotations()) {
   300	            if (acmId.equals(ann.getAcmId())) return ann;
   301	        }
   302	        return null;
   303	    }
   304	
   305	    // if it exists, we just return the thing, other wise we attempt to create it
   306	    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
   307	        Shepherd myShepherd) {
   308	        if (externRef == null) return null;
   309	        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
   310	        url += "&query_annot_uuid=" + this.queryAnnotation.getAcmId();
   311	        url += "&database_annot_uuid=" + annotId;
   312	        url += "&version=heatmask";
   313	        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
   314	        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
   315	            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
   316	        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
   317	        MediaAsset ma = null;
   318	        try {
   319	            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
   320	            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
   321	            ma.addLabel("matchInspectionHeatmap");
   322	            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
   323	                " and created " + ma);
   324	            tmpFile.delete();
   325	        } catch (Exception ex) {
   326	            System.out.println(
   327	                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
   328	                " => " + tmpFile + ": " + ex);
   329	            ex.printStackTrace();
   330	        }
   331	        return ma;
   332	    }
   333	
   334	/*
   335	   notes on pairx payload:
   336	   - image1_uris / image2_uris accept URLs or local file paths (as seen by the server)
   337	   - If you provide 1 image1 and N image2s, it compares that single image1 against each image2 (1-to-many)
   338	   - If you provide N of each, they're compared pairwise (N-to-N, max 16 pairs)
   339	   - bb1/bb2 are bounding boxes as [x, y, width, height]
   340	   - visualization_type options: "lines_and_colors", "only_lines", "only_colors"
   341	   - layer_key controls feature depth — earlier layers (e.g. backbone.blocks.1) give point-specific matches, later layers
   342	    (e.g. backbone.blocks.5) give broader region matches
   343	 */
   344	    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
   345	        Shepherd myShepherd) {
   346	        if ((ann1 == null) || (ann2 == null)) return null;
   347	        MediaAsset ma1 = ann1.getMediaAsset();
   348	        MediaAsset ma2 = ann2.getMediaAsset();
   349	        if ((ma1 == null) || (ma2 == null)) return null;
   350	        // we need this to find MLService endpoint
   351	        Encounter enc = ann1.findEncounter(myShepherd);
   352	        if (enc == null) return null;
   353	        JSONObject payload = new JSONObject();
   354	        payload.put("algorithm", "pairx");
   355	        payload.put("visualization_type", "only_colors");
   356	        payload.put("k_colors", 5);
   357	        // payload.put("k_lines", 20);
   358	        payload.put("model_id", "miewid-msv4.1");
   359	        payload.put("crop_bbox", false);
   360	        payload.put("layer_key", "backbone.blocks.3");
   361	        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
   362	        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
   363	        payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
   364	        payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
   365	        // bb1 / bb2 payload construction. See addBboxPayload Javadoc for
   366	        // the two bugs this fixes (shared-array + negative-bbox-rejection,
   367	        // empty-match-prospects design Track 2 C12). If either clamped
   368	        // bbox has zero width or height, skip the POST entirely — PairX
   369	        // also rejects degenerate boxes.
   370	        int[] clamped1 = clampBbox(ann1.getBbox());
   371	        int[] clamped2 = clampBbox(ann2.getBbox());
   372	        if (isDegenerateBbox(clamped1) || isDegenerateBbox(clamped2)) {
   373	            System.out.println(
   374	                "[INFO] createInspectionPairxAsset() skipping PairX for ann1=" +
   375	                ann1.getId() + " ann2=" + ann2.getId() +
   376	                ": degenerate clamped bbox " +
   377	                java.util.Arrays.toString(clamped1) + " / " +
   378	                java.util.Arrays.toString(clamped2));
   379	            return null;
   380	        }
   381	        addBboxPayload(payload, clamped1, clamped2);
   382	
   383	        // get the image data from pairx endpoint
   384	        JSONObject res = null;
   385	        URL pairxUrl = null;
   386	        try {
   387	            pairxUrl = _getPairxUrl(enc.getTaxonomyString());
   388	            if (pairxUrl == null) return null;
   389	            res = RestClient.postJSON(pairxUrl, payload, null);
   390	        } catch (Exception ex) {
   391	            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
   392	                " failed: " + ex + "; payload=" + payload);
   393	            ex.printStackTrace();
   394	        }
   395	        if (res == null) return null;
   396	        JSONArray imgs = res.optJSONArray("images");
   397	        if ((imgs == null) || (imgs.length() < 1)) return null;
   398	        String b64 = imgs.optString(0, null);
   399	        if (b64 == null) return null;
   400	        // create the asset from base64 data
   401	        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
   402	            " got image data length=" + b64.length());
   403	        try {
   404	            AssetStore store = AssetStore.getDefault(myShepherd);
   405	            JSONObject params = store.createParameters(new File(Util.hashDirectories(this.id) +
   406	                "/pairx-" + this.id + "-" + ann1.getId() + "-" + ann2.getId() + ".png"));
   407	            MediaAsset ma = store.create(params);
   408	            ma.copyInBase64(b64);
   409	            ma.addLabel("matchInspectionPairx");
   410	            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
   411	            myShepherd.getPM().makePersistent(ma);
   412	            return ma;
   413	        } catch (Exception ex) {
   414	            System.out.println(
   415	                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
   416	            ex.printStackTrace();
   417	        }
   418	        return null;
   419	    }
   420	
   421	    /**
   422	     * Clamp negative bbox values to the in-image portion. ml-service
   423	     * detections sometimes produce bboxes whose top-left extends past
   424	     * the image edge (e.g., {@code [-80, 42, 1786, 2228]}); the PairX
   425	     * {@code /explain/} endpoint rejects those with HTTP 400. Shifting
   426	     * x or y to 0 alone would translate the box; we also shrink the
   427	     * dimension by the same amount so the result covers the same in-
   428	     * image pixels the embedding model actually consumed after
   429	     * edge-cropping.
   430	     *
   431	     * <p>Package-visible for unit testing. (Empty-match-prospects
   432	     * design Track 2 C12.)</p>
   433	     */
   434	    static int[] clampBbox(int[] bbox) {
   435	        if (bbox == null || bbox.length < 4) return bbox;
   436	        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];
   437	        if (x < 0) {
   438	            w = Math.max(0, w + x);
   439	            x = 0;
   440	        }
   441	        if (y < 0) {
   442	            h = Math.max(0, h + y);
   443	            y = 0;
   444	        }
   445	        return new int[] { x, y, w, h };
   446	    }
   447	
   448	    /**
   449	     * Convert an int[] bbox to a JSONArray of ints. {@code JSONArray.put(Object)}
   450	     * doesn't auto-convert int[] reliably across org.json versions, so we
   451	     * box explicitly.
   452	     */
   453	    static JSONArray bboxToJsonArray(int[] bbox) {
   454	        JSONArray arr = new JSONArray();
   455	        if (bbox == null) return arr;
   456	        for (int v : bbox) arr.put(v);
   457	        return arr;
   458	    }
   459	
   460	    /**
   461	     * True when a bbox has zero or negative width/height (the typical
   462	     * shape after {@link #clampBbox} on a box that lies entirely off-
   463	     * image). PairX rejects such boxes the same way it rejects negative
   464	     * x/y, so callers should skip the POST entirely.
   465	     */
   466	    static boolean isDegenerateBbox(int[] bbox) {
   467	        if (bbox == null || bbox.length < 4) return true;
   468	        return bbox[2] <= 0 || bbox[3] <= 0;
   469	    }
   470	
   471	    /**
   472	     * Build the bb1/bb2 payload for {@code /explain/}: each key gets
   473	     * its own outer JSONArray of one [x, y, w, h] inner array.
   474	     *
   475	     * <p>Two bugs in the previous implementation are addressed
   476	     * together (empty-match-prospects design Track 2 C12):</p>
   477	     * <ol>
   478	     *   <li>The previous code reused one tmpArr for both keys, so
   479	     *       {@code tmpArr.put(0, ann2)} after {@code payload.put("bb1", tmpArr)}
   480	     *       mutated the shared array and made {@code bb2 == bb1}.
   481	     *       Building two outer arrays here keeps the references
   482	     *       distinct.</li>
   483	     *   <li>{@link #clampBbox} (called by the production entry point
   484	     *       before this method) prevents negative x/y from being
   485	     *       sent to PairX, which would return HTTP 400
   486	     *       "Bounding box values should be positive".</li>
   487	     * </ol>
   488	     *
   489	     * <p>Package-visible so {@code MatchResultPairxPayloadTest} can
   490	     * assert the JSON shape without spinning up a real Annotation.</p>
   491	     */
   492	    static void addBboxPayload(JSONObject payload, int[] bbox1, int[] bbox2) {
   493	        payload.put("bb1", new JSONArray().put(bboxToJsonArray(bbox1)));
   494	        payload.put("bb2", new JSONArray().put(bboxToJsonArray(bbox2)));
   495	    }
   496	
   497	    public static URL _getPairxUrl(String txStr)
   498	    throws IOException {
   499	        if (txStr == null) throw new IOException("passed null taxonomy");
   500	        String urlStr = null;
   501	        try {
   502	            MLService mls = new MLService();
   503	            List<JSONObject> confs = mls.getConfigs(txStr);
   504	            if (confs.size() < 1) throw new IOException("empty MLService configs for tx=" + txStr);
   505	            urlStr = confs.get(0).optString("api_endpoint", null);
   506	        } catch (IAException ex) {
   507	            throw new IOException(ex);
   508	        }
   509	        if (urlStr == null) return null;
   510	        return new URL(urlStr + "/explain/");
   511	    }
   512	
   513	    public JSONObject getTaskParameters() {
   514	        if (task == null) return null;
   515	        return task.getParameters();
   516	    }
   517	
   518	    public JSONObject getTaskMatchingSetFilter() {
   519	        if (task == null) return null;
   520	        JSONObject params = task.getParameters();
   521	        if (params == null) return null;
   522	        return params.optJSONObject("matchingSetFilter");
   523	    }
   524	
   525	/*
   526	    see note at top about candidates vs numberCandidates
   527	    public int numberCandidates() {
   528	        return Util.collectionSize(candidates);
   529	    }
   530	 */
   531	    public int numberProspects() {
   532	        return this.numberProspects;
   533	    }
   534	
   535	    public Set<String> prospectScoreTypes() {
   536	        Set<String> types = new HashSet<String>();
   537	
   538	        if (numberProspects() == 0) return types;
   539	        for (MatchResultProspect mrp : prospects) {
   540	            types.add(mrp.getType());

 succeeded in 206ms:
     1	package org.ecocean.ia;
     2	
     3	import java.util.HashSet;
     4	import java.util.Set;
     5	
     6	import org.json.JSONArray;
     7	import org.json.JSONObject;
     8	
     9	import org.ecocean.Annotation;
    10	import org.ecocean.Encounter;
    11	import org.ecocean.media.MediaAsset;
    12	import org.ecocean.shepherd.core.Shepherd;
    13	import org.ecocean.Util;
    14	
    15	public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
    16	    private Annotation annotation;
    17	    private double score = 0.0d;
    18	    private String scoreType;
    19	    private MediaAsset asset;
    20	    private MatchResult matchResult;
    21	
    22	    public MatchResultProspect() {}
    23	
    24	    public MatchResultProspect(Annotation ann) {
    25	        this();
    26	        this.annotation = ann;
    27	    }
    28	
    29	    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
    30	        this();
    31	        this.annotation = ann;
    32	        this.score = score;
    33	        this.scoreType = type;
    34	        this.asset = asset;
    35	    }
    36	
    37	    /**
    38	     * Attach a PairX inspection MediaAsset to this prospect. Used by
    39	     * {@link MatchInspectionPairxEnricher} in Phase C to enrich
    40	     * prospects after the MatchResult has been persisted (empty-match-
    41	     * prospects design Track 2 C13: PairX is now non-blocking and
    42	     * runs without holding the outer Shepherd across HTTP).
    43	     */
    44	    public void setAsset(MediaAsset asset) {
    45	        this.asset = asset;
    46	    }
    47	
    48	    public MediaAsset getAsset() {
    49	        return asset;
    50	    }
    51	
    52	    public Annotation getAnnotation() {
    53	        return annotation;
    54	    }
    55	
    56	    public double getScore() {
    57	        return score;
    58	    }
    59	
    60	    public String getType() {
    61	        return scoreType;
    62	    }
    63	
    64	    public boolean isType(String type) {
    65	        if (type == null) return (this.scoreType == null);
    66	        return type.equals(this.scoreType);
    67	    }
    68	
    69	    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
    70	        // if we have no projects to filter on, we consider this to be in it
    71	        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
    72	        if (annotation == null) return false;
    73	        Encounter enc = annotation.findEncounter(myShepherd);
    74	        if (enc == null) return false;
    75	        return enc.isInProjects(projectIds, myShepherd);
    76	    }
    77	
    78	    public String toString() {
    79	        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
    80	    }
    81	
    82	    public JSONObject jsonForApiGet(Shepherd myShepherd) {
    83	        JSONObject rtn = new JSONObject();
    84	
    85	        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
    86	        rtn.put("score", score);
    87	        // skipping scoreType since this is currently only used filtered by scoreType already
    88	        if (asset != null) {
    89	            JSONObject aj = asset.toSimpleJSONObject();
    90	            aj.put("url", asset.webURL()); // we have no "safe" url
    91	            rtn.put("asset", aj);
    92	        }
    93	        return rtn;
    94	    }
    95	
    96	    // used in sorting
    97	    @Override public int compareTo(MatchResultProspect other) {
    98	        // we invert this so higher score is first
    99	        int comp = Double.compare(other.score, this.score);
   100	        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
   101	        // ordering (otherwise tied scores come back random order), so we use annot id
   102	        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
   103	            return this.annotation.getId().compareTo(other.annotation.getId());
   104	        // scores are *not* equal, so we just let comparison stand as-is
   105	        return comp;
   106	    }
   107	}

 succeeded in 213ms:
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
   545	            String matchTaskId = matchTask.getId();
   546	            shep.commitDBTransaction();
   547	            shep.rollbackAndClose();  // close BEFORE PairX enrichment (Track 2 C13)
   548	            // Phase 4 (C13): PairX inspection-image enrichment. The
   549	            // MatchResult + prospects are already persisted with
   550	            // null inspection MediaAssets; the enricher fills them in
   551	            // out-of-transaction via a Phase A/B/C flow per prospect.
   552	            // Per-prospect failure is non-blocking — UI render works
   553	            // either way, just without the inspection image.
   554	            enrichPairxAssetsForMatchTask(matchTaskId);
   555	            return MlServiceJobOutcome.ok(annotationIds);
   556	        } catch (Exception ex) {
   557	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
   558	            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
   559	        } finally {
   560	            shep.rollbackAndClose();
   561	        }
   562	    }
   563	
   564	    /**
   565	     * Phase 4: drive {@link MatchInspectionPairxEnricher} for every
   566	     * MatchResult attached to a child of {@code matchTaskId}. Runs
   567	     * after the main runMatchProspects transaction has closed, so the
   568	     * PairX HTTP work doesn't hold a Shepherd. (Empty-match-prospects
   569	     * design Track 2 C13.)
   570	     */
   571	    void enrichPairxAssetsForMatchTask(String matchTaskId) {
   572	        if (matchTaskId == null) return;
   573	        List<String> mrIds = collectMatchResultIds(matchTaskId);
   574	        if (mrIds.isEmpty()) return;
   575	        MatchInspectionPairxEnricher enricher =
   576	            new MatchInspectionPairxEnricher(context);
   577	        for (String mrId : mrIds) {
   578	            try {
   579	                enricher.enrichMatchResult(mrId);
   580	            } catch (Exception ex) {
   581	                System.out.println(
   582	                    "[WARN] MlServiceProcessor.enrichPairxAssetsForMatchTask " +
   583	                    "mr=" + mrId + " failed (non-blocking): " + ex);
   584	            }
   585	        }
   586	    }
   587	
   588	    /**
   589	     * Open a short Shepherd, list MatchResult IDs attached to children
   590	     * of {@code matchTaskId}, close. Returns scalar IDs only so
   591	     * subsequent enrichment runs without DB state.
   592	     */
   593	    private List<String> collectMatchResultIds(String matchTaskId) {
   594	        List<String> out = new ArrayList<String>();
   595	        Shepherd shep = new Shepherd(context);
   596	        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
   597	        try {
   598	            shep.beginDBTransaction();
   599	            Task matchTask = Task.load(matchTaskId, shep);
   600	            if (matchTask == null) {
   601	                shep.commitDBTransaction();
   602	                return out;
   603	            }
   604	            List<Task> children = matchTask.getChildren();
   605	            if (children != null) {
   606	                for (Task child : children) {
   607	                    if (child == null) continue;
   608	                    List<MatchResult> mrs = shep.getMatchResults(child);
   609	                    if (mrs == null) continue;
   610	                    for (MatchResult mr : mrs) {
   611	                        if (mr != null && mr.getId() != null) {
   612	                            out.add(mr.getId());
   613	                        }
   614	                    }
   615	                }
   616	            }
   617	            shep.commitDBTransaction();
   618	        } catch (Exception ex) {
   619	            shep.rollbackDBTransaction();
   620	            System.out.println(
   621	                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
   622	                matchTaskId + ": " + ex);
   623	        } finally {
   624	            shep.closeDBTransaction();
   625	        }
   626	        return out;
   627	    }
   628	
   629	    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
   630	        String code = ex == null ? null : ex.getCode();
   631	        String message = ex == null ? null : ex.getMessage();
   632	        if ("INVALID".equals(code) || "SUCCESS_FALSE".equals(code)) {
   633	            return MlServiceJobOutcome.validationError(code, message);
   634	        }
   635	        if ("TIMEOUT".equals(code) || "NETWORK".equals(code) || "RATE_LIMITED".equals(code)
   636	            || "SERVER_ERROR".equals(code) || "CLIENT_ERROR".equals(code)) {
   637	            return MlServiceJobOutcome.networkError(code, message);
   638	        }
   639	        return MlServiceJobOutcome.networkError("UNKNOWN", message);
   640	    }

exec
/bin/bash -lc "rg -n \"MatchResult|MatchResultProspect|prospects|matchResult\" src/main/resources src/main/java -g '*.xml' -g '*.jdo' -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getMatchResults\\(|getMatchResult\\(|class Shepherd|makePersistent\\(.*MatchResult|new MatchResult\\(\" src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class AssetStore|createParameters\\(|copyInBase64|hashDirectories\\(\" src/main/java/org/ecocean src/test/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 1375ms:
src/main/java/org/ecocean/Encounter.java:2526:        // NOTE: opposite logic in MatchResultProspect.isInProject()
src/main/java/org/ecocean/Embedding.java:18:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/Embedding.java:292:            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
src/main/java/org/ecocean/Embedding.java:370:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
src/main/java/org/ecocean/Embedding.java:372:                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
src/main/java/org/ecocean/Embedding.java:381:                System.out.println("findMatchProspects() MatchResult creation failed on " +
src/main/java/org/ecocean/Embedding.java:392:                    "MatchResult persistence failed");
src/main/java/org/ecocean/StartupWildbook.java:781:            // closes. (Empty-match-prospects design Track 1 C5: WBIA
src/main/java/org/ecocean/OpenSearch.java:551:    // ml-service migration v2 / empty-match-prospects design Track 2 C8.
src/main/java/org/ecocean/identity/IBEISIA.java:1884:                MatchResult mr = new MatchResult(task, j, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1889:                System.out.println("processCallbackIdentify() failed to create MatchResult on " +
src/main/java/org/ecocean/identity/IBEISIA.java:1893:                    "Creation of MatchResult upon task completion failed due to: " + ex);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:21: * {@link MatchResult}'s prospects with PairX inspection MediaAssets.
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:22: * Replaces the previous in-{@link MatchResult}-constructor PairX calls
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:28: *       {@link #loadDtos(String)} loads the MatchResult, walks its
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:29: *       prospects, and builds a list of {@link PairxDto} carrying every
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:41: * error on one prospect logs and continues; other prospects in the
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:42: * same MatchResult still get processed.</p>
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:44: * <p>(Empty-match-prospects design Track 2 C13.)</p>
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:55:     * For each prospect of the named MatchResult that lacks an
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:59:     * <p>Returns the number of prospects that received a new inspection
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:62:    public int enrichMatchResult(String matchResultId) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:63:        if (matchResultId == null) return 0;
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:66:            dtos = loadDtos(matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:70:                matchResultId + ": " + ex);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:81:                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:90:                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:94:            "/" + dtos.size() + " prospects on mr=" + matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:99:     * Phase A: load all PairxDtos for the given MatchResult under one
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:102:    List<PairxDto> loadDtos(String matchResultId) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:105:        shep.setAction("PairxEnricher.loadDtos." + matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:108:            MatchResult mr = shep.getMatchResult(matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:122:            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:125:                for (MatchResultProspect prospect : mr.getProspects()) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:126:                    // Skip prospects that already have an inspection image
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:128:                    // same MatchResult under operator-driven re-fire).
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:133:                    int[] prospectBbox = MatchResult.clampBbox(pAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:136:                        matchResultId, pAnn.getId(), prospect.getType(),
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:169:        if (MatchResult.isDegenerateBbox(dto.queryBbox) ||
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:170:            MatchResult.isDegenerateBbox(dto.prospectBbox)) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:177:        URL pairxUrl = MatchResult._getPairxUrl(dto.taxonomyString);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:192:     * {@code MatchResult.createInspectionPairxAsset} sent, with the
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:207:        MatchResult.addBboxPayload(payload, dto.queryBbox, dto.prospectBbox);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:214:     * within the MatchResult. Returns true on successful attach, false
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:219:        shep.setAction("PairxEnricher.persist." + dto.matchResultId + "." +
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:223:            MatchResult mr = shep.getMatchResult(dto.matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:228:            MatchResultProspect target = findProspect(mr, dto.prospectAnnotationId,
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:242:                Util.hashDirectories(dto.matchResultId) +
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:243:                "/pairx-" + dto.matchResultId + "-" +
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:261:     * Find a prospect in the given MatchResult by (annotationId, scoreType).
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:264:    static MatchResultProspect findProspect(MatchResult mr, String annotationId,
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:267:        for (MatchResultProspect p : mr.getProspects()) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:286:        final String matchResultId;
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:297:        PairxDto(String matchResultId, String prospectAnnotationId, String scoreType,
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:301:            this.matchResultId = matchResultId;
src/main/java/org/ecocean/ia/Task.java:601:     * empty-match-prospects Track 2 batch gate) need this so a read of
src/main/java/org/ecocean/ia/Task.java:605:     * <p>(Empty-match-prospects design Track 2 C7.)</p>
src/main/java/org/ecocean/ia/Task.java:777:    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:778:        return myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:781:    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:782:        List<MatchResult> all = myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:789:    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:790:        List<MatchResult> mrs = new ArrayList<MatchResult>();
src/main/java/org/ecocean/ia/Task.java:800:                    MatchResult mr = new MatchResult(log, myShepherd);
src/main/java/org/ecocean/ia/Task.java:801:                    System.out.println("[INFO] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:808:                    System.out.println("[ERROR] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:812:                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
src/main/java/org/ecocean/ia/Task.java:820:    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:828:        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
src/main/java/org/ecocean/ia/Task.java:844:            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
src/main/java/org/ecocean/ia/Task.java:847:            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
src/main/java/org/ecocean/ia/Task.java:848:               as vector results should have generated their MatchResult upon completion
src/main/java/org/ecocean/ia/Task.java:850:            MatchResult mr = getLatestMatchResult(myShepherd);
src/main/java/org/ecocean/ia/Task.java:853:                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
src/main/java/org/ecocean/ia/Task.java:855:                List<MatchResult> mrs = generateMatchResults(myShepherd);
src/main/java/org/ecocean/ia/Task.java:856:                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
src/main/java/org/ecocean/ia/Task.java:864:                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
src/main/java/org/ecocean/ia/Task.java:871:                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:15: * Direct-SQL utility for the empty-match-prospects batch gate.
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:37: * <p>(Empty-match-prospects design Track 2 C9.)</p>
src/main/java/org/ecocean/ia/DeferredMatchPublisher.java:15: * <p>(Empty-match-prospects design Track 2 C10: testability seam
src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java:14: * <p>(Empty-match-prospects design Track 2 C11 — Codex round-4
src/main/java/org/ecocean/api/MarkedIndividualInfo.java:123:/* from matchResults.jsp ...
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:458:        // no JDO touchpoints. (Empty-match-prospects design Track 1 C5.)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:582:     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:648:     * <p>(Empty-match-prospects design Track 1 C4: extracted from
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:733:     * (Empty-match-prospects design Track 1 C6.)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1105:     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
src/main/java/org/ecocean/ia/MatchResultProspect.java:15:public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
src/main/java/org/ecocean/ia/MatchResultProspect.java:20:    private MatchResult matchResult;
src/main/java/org/ecocean/ia/MatchResultProspect.java:22:    public MatchResultProspect() {}
src/main/java/org/ecocean/ia/MatchResultProspect.java:24:    public MatchResultProspect(Annotation ann) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:29:    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:40:     * prospects after the MatchResult has been persisted (empty-match-
src/main/java/org/ecocean/ia/MatchResultProspect.java:41:     * prospects design Track 2 C13: PairX is now non-blocking and
src/main/java/org/ecocean/ia/MatchResultProspect.java:79:        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
src/main/java/org/ecocean/ia/MatchResultProspect.java:85:        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
src/main/java/org/ecocean/ia/MatchResultProspect.java:97:    @Override public int compareTo(MatchResultProspect other) {
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:24: * <p>(Empty-match-prospects design Track 2 C10.)</p>
src/main/java/org/ecocean/ia/MatchResult.java:34:public class MatchResult implements java.io.Serializable {
src/main/java/org/ecocean/ia/MatchResult.java:38:    private Set<MatchResultProspect> prospects;
src/main/java/org/ecocean/ia/MatchResult.java:42:    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
src/main/java/org/ecocean/ia/MatchResult.java:48:    // fallback number to cutoff number of prospects to return
src/main/java/org/ecocean/ia/MatchResult.java:50:    // number of MatchResultProspects [per type] to actually store (hotspotter
src/main/java/org/ecocean/ia/MatchResult.java:54:    public MatchResult() {
src/main/java/org/ecocean/ia/MatchResult.java:59:    public MatchResult(Task task) {
src/main/java/org/ecocean/ia/MatchResult.java:64:    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:70:    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:77:    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
src/main/java/org/ecocean/ia/MatchResult.java:84:        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
src/main/java/org/ecocean/ia/MatchResult.java:165:        if (this.prospects == null)
src/main/java/org/ecocean/ia/MatchResult.java:166:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:168:        this.numberProspects += annotIds.length(); // true number of prospects
src/main/java/org/ecocean/ia/MatchResult.java:182:            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
src/main/java/org/ecocean/ia/MatchResult.java:186:                    ") number storable prospects on " + this);
src/main/java/org/ecocean/ia/MatchResult.java:195:    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
src/main/java/org/ecocean/ia/MatchResult.java:197:    // Empty-match-prospects design Track 2 C13: prospects are created with
src/main/java/org/ecocean/ia/MatchResult.java:207:        if (this.prospects == null)
src/main/java/org/ecocean/ia/MatchResult.java:208:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:215:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
src/main/java/org/ecocean/ia/MatchResult.java:219:        this.numberProspects = this.prospects.size();
src/main/java/org/ecocean/ia/MatchResult.java:254:            // (empty-match-prospects design Track 2 C13).
src/main/java/org/ecocean/ia/MatchResult.java:255:            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
src/main/java/org/ecocean/ia/MatchResult.java:261:     * Public read-only view of the prospects collection so the
src/main/java/org/ecocean/ia/MatchResult.java:266:    public Set<MatchResultProspect> getProspects() {
src/main/java/org/ecocean/ia/MatchResult.java:267:        return this.prospects;
src/main/java/org/ecocean/ia/MatchResult.java:367:        // empty-match-prospects design Track 2 C12). If either clamped
src/main/java/org/ecocean/ia/MatchResult.java:431:     * <p>Package-visible for unit testing. (Empty-match-prospects
src/main/java/org/ecocean/ia/MatchResult.java:476:     * together (empty-match-prospects design Track 2 C12):</p>
src/main/java/org/ecocean/ia/MatchResult.java:489:     * <p>Package-visible so {@code MatchResultPairxPayloadTest} can
src/main/java/org/ecocean/ia/MatchResult.java:539:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:546:    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
src/main/java/org/ecocean/ia/MatchResult.java:548:        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:551:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:559:    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:564:            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
src/main/java/org/ecocean/ia/MatchResult.java:580:        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
src/main/java/org/ecocean/ia/MatchResult.java:646:        String s = "MatchResult " + id;
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:10: * empty-match-prospects bug where per-image ml-service jobs fired
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:43: * <p>(Empty-match-prospects design Track 2 C10.)</p>
src/main/java/org/ecocean/api/GenericObject.java:112:                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
src/main/java/org/ecocean/api/GenericObject.java:120:                                prospectsSize = Integer.parseInt(request.getParameter(
src/main/java/org/ecocean/api/GenericObject.java:121:                                    "prospectsSize"));
src/main/java/org/ecocean/api/GenericObject.java:123:                            rtn.put("prospectsSize", prospectsSize);
src/main/java/org/ecocean/api/GenericObject.java:124:                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
src/main/java/org/ecocean/api/GenericObject.java:127:                            rtn.put("matchResultsRoot", mrJson);
src/main/java/org/ecocean/api/GenericObject.java:130:                            // this means we created on-the-fly some MatchResult(s) that need persisting
src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:     * above. (Empty-match-prospects design Track 2 C11 testability
src/main/java/org/ecocean/ia/MlServiceProcessor.java:451:     * <p>(Empty-match-prospects design Track 2 C11.)</p>
src/main/java/org/ecocean/ia/MlServiceProcessor.java:549:            // MatchResult + prospects are already persisted with
src/main/java/org/ecocean/ia/MlServiceProcessor.java:566:     * MatchResult attached to a child of {@code matchTaskId}. Runs
src/main/java/org/ecocean/ia/MlServiceProcessor.java:568:     * PairX HTTP work doesn't hold a Shepherd. (Empty-match-prospects
src/main/java/org/ecocean/ia/MlServiceProcessor.java:573:        List<String> mrIds = collectMatchResultIds(matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:579:                enricher.enrichMatchResult(mrId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:589:     * Open a short Shepherd, list MatchResult IDs attached to children
src/main/java/org/ecocean/ia/MlServiceProcessor.java:593:    private List<String> collectMatchResultIds(String matchTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:596:        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:608:                    List<MatchResult> mrs = shep.getMatchResults(child);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:610:                    for (MatchResult mr : mrs) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:621:                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:19:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:20:import org.ecocean.ia.MatchResultProspect;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2808:    public MatchResult getMatchResult(String id) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2809:        MatchResult mr = null;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2812:            mr = (MatchResult)(pm.getObjectById(pm.newObjectIdInstance(MatchResult.class, id),
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820:    public List<MatchResult> getMatchResults(Task task) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2821:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2824:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE task.id == '" + task.getId() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2829:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2834:    public List<MatchResult> getMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2835:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2838:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2843:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2848:    // faster deletion of all MatchResults associated with Annotation
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2849:    public long deleteMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2852:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2857:        System.out.println("[DEBUG] deleteMatchResults() deleted " + ct + " [" + (System.currentTimeMillis() - t) + "ms] on " + ann);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2861:    public List<MatchResultProspect> getMatchResultProspects(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2862:        List<MatchResultProspect> all = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2865:        String filter = "SELECT FROM org.ecocean.ia.MatchResultProspect WHERE annotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2869:        if (c != null) all = new ArrayList<MatchResultProspect>(c);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:107:                        ann.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:108:                        ann.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:127:                        ann.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:128:                        ann.deleteMatchResultProspects(myShepherd);
src/main/resources/org/ecocean/ia/package.jdo:75:	<class name="MatchResult" identity-type="application">
src/main/resources/org/ecocean/ia/package.jdo:88:		<field name="prospects" default-fetch-group="false" mapped-by="matchResult">
src/main/resources/org/ecocean/ia/package.jdo:89:			<collection element-type="org.ecocean.ia.MatchResultProspect" dependent-element="true" />
src/main/resources/org/ecocean/ia/package.jdo:102:	<class name="MatchResultProspect">
src/main/resources/org/ecocean/ia/package.jdo:107:		<field name="matchResult" dependent-element="false" >
src/main/java/org/ecocean/Annotation.java:16:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/Annotation.java:17:import org.ecocean.ia.MatchResultProspect;
src/main/java/org/ecocean/Annotation.java:1814:    public long deleteMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/Annotation.java:1815:        return myShepherd.deleteMatchResults(this);
src/main/java/org/ecocean/Annotation.java:1818:    // similar as above for MatchResultProspects
src/main/java/org/ecocean/Annotation.java:1819:    public int deleteMatchResultProspects(Shepherd myShepherd) {
src/main/java/org/ecocean/Annotation.java:1820:        List<MatchResultProspect> mrps = myShepherd.getMatchResultProspects(this);
src/main/java/org/ecocean/Annotation.java:1823:        for (MatchResultProspect mrp : mrps) {
src/main/java/org/ecocean/Annotation.java:1825:            System.out.println("[DEBUG] (" + ct + ") ann.deleteMatchResultProspects() on id=" +
src/main/java/org/ecocean/Annotation.java:1853:        long nm = this.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/Annotation.java:1854:        int np = this.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/Annotation.java:1857:            " MatchResults, " + np + " MatchResultProspects, " + ne + " Embeddings on " + this);
src/main/java/org/ecocean/servlet/AnnotationEdit.java:165:                        annot.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/AnnotationEdit.java:166:                        annot.deleteMatchResultProspects(myShepherd);

 succeeded in 1435ms:
src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1884:                MatchResult mr = new MatchResult(task, j, myShepherd);
src/main/java/org/ecocean/ia/Task.java:777:    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:778:        return myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:782:        List<MatchResult> all = myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:800:                    MatchResult mr = new MatchResult(log, myShepherd);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:108:            MatchResult mr = shep.getMatchResult(matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:223:            MatchResult mr = shep.getMatchResult(dto.matchResultId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:608:                    List<MatchResult> mrs = shep.getMatchResults(child);
src/main/java/org/ecocean/shepherd/utils/ShepherdState.java:15:public final class ShepherdState {
src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java:20:public class ShepherdProperties {
src/main/java/org/ecocean/shepherd/core/ShepherdPMF.java:22:public class ShepherdPMF {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:63:public class Shepherd {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2808:    public MatchResult getMatchResult(String id) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820:    public List<MatchResult> getMatchResults(Task task) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2834:    public List<MatchResult> getMatchResults(Annotation ann) {
src/main/java/org/ecocean/security/ShepherdRealm.java:21:public class ShepherdRealm extends AuthorizingRealm {

 succeeded in 1575ms:
src/main/java/org/ecocean/Util.java:427:    public static String hashDirectories(String in, String separator) {
src/main/java/org/ecocean/Util.java:432:    public static String hashDirectories(String in) {
src/main/java/org/ecocean/Util.java:433:        return hashDirectories(in, File.separator);
src/main/java/org/ecocean/identity/IBEISIA.java:2199:        JSONObject params = astore.createParameters(file);
src/main/java/org/ecocean/api/UploadedFiles.java:117:            JSONObject sp = astore.createParameters(new File(Encounter.subdir(encounterId) +
src/main/java/org/ecocean/api/UploadedFiles.java:155:        JSONObject sp = astore.createParameters(new File(Encounter.subdir(dirId) + File.separator +
src/main/java/org/ecocean/api/BaseObject.java:359:            JSONObject sp = astore.createParameters(new File(Encounter.subdir(encounterId) +
src/main/java/org/ecocean/Encounter.java:1381:        org.json.JSONObject sp = astore.createParameters(mpath);
src/main/java/org/ecocean/Encounter.java:1382:        if (key != null) sp.put("key", key); // will use default from createParameters() (if there was one even)
src/main/java/org/ecocean/Encounter.java:1431:        org.json.JSONObject sp = astore.createParameters(fullPath);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:329:        when(astore.createParameters(any(File.class))).thenReturn(new JSONObject());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:241:            JSONObject params = store.createParameters(new File(
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:242:                Util.hashDirectories(dto.matchResultId) +
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:246:            ma.copyInBase64(b64);
src/main/java/org/ecocean/ia/MatchResult.java:405:            JSONObject params = store.createParameters(new File(Util.hashDirectories(this.id) +
src/main/java/org/ecocean/ia/MatchResult.java:408:            ma.copyInBase64(b64);
src/main/java/org/ecocean/SinglePhotoVideo.java:253:        org.json.JSONObject sp = astore.createParameters(new File(fullFileSystemPath));
src/main/java/org/ecocean/servlet/EncounterForm.java:1048:        JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator +
src/main/java/org/ecocean/servlet/EncounterForm.java:1052:        sp.put("key", Util.hashDirectories(encID) + "/" + sanitizedItemName);
src/main/java/org/ecocean/servlet/EncounterForm.java:1098:        JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator +
src/main/java/org/ecocean/servlet/EncounterForm.java:1101:        sp.put("key", Util.hashDirectories(encID) + "/" + item.getName());
src/main/java/org/ecocean/media/YouTubeAssetStore.java:108:    @Override public JSONObject createParameters(File file, String grouping) {
src/main/java/org/ecocean/media/YouTubeAssetStore.java:254:        JSONObject sp = astore.createParameters(f);
src/main/java/org/ecocean/media/YouTubeAssetStore.java:279:        JSONObject sp = astore.createParameters(f);
src/main/java/org/ecocean/media/YouTubeAssetStore.java:315:        JSONObject sp = astore.createParameters(f);
src/main/java/org/ecocean/media/LocalAssetStore.java:297:    @Override public JSONObject createParameters(File file, String grouping) {
src/main/java/org/ecocean/media/LocalAssetStore.java:305:                grouping = Util.hashDirectories(Util.generateUUID(), File.separator);
src/main/java/org/ecocean/media/LocalAssetStore.java:309:// System.out.println("NOTE: LocalAssetStore.createParameters(" + file + ") -> " + p.toString());
src/main/java/org/ecocean/media/URLAssetStore.java:125:    @Override public JSONObject createParameters(File file, String grouping) {
src/main/java/org/ecocean/media/AssetStoreFactory.java:12:public class AssetStoreFactory {
src/main/java/org/ecocean/servlet/importer/WebImport.java:554:                JSONObject assetParams = astore.createParameters(f);
src/main/java/org/ecocean/servlet/importer/WebImport.java:638:        JSONObject assetParams = astore.createParameters(f);
src/main/java/org/ecocean/media/AssetStoreConfig.java:20:public class AssetStoreConfig implements java.io.Serializable {
src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:719:                JSONObject assetParams = astore.createParameters(f);
src/main/java/org/ecocean/media/AssetStore.java:50:public abstract class AssetStore implements java.io.Serializable {
src/main/java/org/ecocean/media/AssetStore.java:299:        JSONObject sp = this.createParameters(targetFile);
src/main/java/org/ecocean/media/AssetStore.java:476:    public abstract JSONObject createParameters(final File file, final String grouping);
src/main/java/org/ecocean/media/AssetStore.java:478:    public JSONObject createParameters(final File file) {
src/main/java/org/ecocean/media/AssetStore.java:479:        return createParameters(file, null);
src/main/java/org/ecocean/media/MediaAsset.java:1475:    public void copyInBase64(String b64)
src/main/java/org/ecocean/media/MediaAsset.java:1477:        if (b64 == null) throw new IOException("copyInBase64() null string");
src/main/java/org/ecocean/media/MediaAsset.java:1482:            throw new IOException("copyInBase64() could not parse: " + ex.toString());
src/main/java/org/ecocean/media/MediaAsset.java:1501:            throw new IOException("copyInBase64() could not write " + file);
src/main/java/org/ecocean/servlet/importer/StandardImport.java:1543:        JSONObject assetParams = astore.createParameters(f);
src/main/java/org/ecocean/servlet/MediaAssetCreate.java:210:                    params = targetStore.createParameters(inFile);
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:95:	        JSONObject params = store.createParameters(new File("encounters/" +
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:99:	        crMa.copyInBase64(json.optString("imageData", null));
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:723:            JSONObject assetParams = astore.createParameters(f);
src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:286:                JSONObject assetParams = astore.createParameters(f);
src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:376:        JSONObject assetParams = astore.createParameters(f);

codex
The JDO metadata matters here because the code relies on mutating an element inside `MatchResult.prospects` and having DataNucleus persist that relation update. I’m checking the mapping and the existing helper methods before deciding whether that is safe.
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '60,120p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '340,405p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/shepherd/core/Shepherd.java | sed -n '2790,2880p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Util.java | sed -n '420,438p'" in /mnt/c/Wildbook-clean2
 succeeded in 265ms:
    60	-->
    61	
    62			<field name="objectMediaAssets">
    63				<collection element-type="org.ecocean.media.MediaAsset"/>
    64				<join />
    65			</field>
    66	<!--
    67			<field name="objectAnnotations" mapped-by="mediaAsset"???   >
    68	-->
    69			<field name="objectAnnotations">
    70				<collection element-type="org.ecocean.Annotation"/>
    71				<join />
    72			</field>
    73		</class>
    74	
    75		<class name="MatchResult" identity-type="application">
    76			<field name="id" primary-key="true">
    77	        		<column length="36"/>
    78			</field>
    79			<field name="created">
    80	                	<column jdbc-type="BIGINT" allows-null="false"/>
    81				<index name="MATCHRESULT_CREATED_idx" />
    82			</field>
    83	
    84	      		<field name="task" persistence-modifier="persistent" element-type="org.ecocean.ia.Task">
    85				<column allows-null="false" />
    86			</field>
    87	
    88			<field name="prospects" default-fetch-group="false" mapped-by="matchResult">
    89				<collection element-type="org.ecocean.ia.MatchResultProspect" dependent-element="true" />
    90			</field>
    91	
    92	      		<field name="queryAnnotation" persistence-modifier="persistent" element-type="org.ecocean.Annotation">
    93				<column allows-null="false" />
    94			</field>
    95	
    96			<field name="candidates">
    97				<collection element-type="org.ecocean.Annotation"/>
    98				<join />
    99			</field>
   100		</class>
   101	
   102		<class name="MatchResultProspect">
   103	      		<field name="annotation" persistence-modifier="persistent" element-type="org.ecocean.Annotation">
   104				<column allows-null="false" />
   105			</field>
   106	      		<field name="asset" persistence-modifier="persistent" element-type="org.ecocean.media.MediaAsset" />
   107			<field name="matchResult" dependent-element="false" >
   108				<column name="MATCHRESULT_ID" />
   109			</field>
   110		</class>
   111	
   112	    </package>
   113	</jdo>

 succeeded in 228ms:
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

 succeeded in 230ms:
   420	            gs[1] = s.substring(i + 1);
   421	        }
   422	        return gs;
   423	    }
   424	
   425	    // a generic version of our uuid-dir-structure-creating algorithm -- adjust as needed!?
   426	    // TODO: check for incoming slashes and similar weirdness
   427	    public static String hashDirectories(String in, String separator) {
   428	        if ((in == null) || (in.length() < 4)) return in;
   429	        return in.charAt(0) + separator + in.charAt(1) + separator + in;
   430	    }
   431	
   432	    public static String hashDirectories(String in) {
   433	        return hashDirectories(in, File.separator);
   434	    }
   435	
   436	    public static boolean isIdentityMatrix(float[] m) {
   437	        if (m == null) return false;
   438	        if (m.length != 6) return false;

 succeeded in 300ms:
  2790	        if ((user == null) || (user.getUsername() == null)) return null;
  2791	        // String sql = "SELECT \"TASK\".\"ID\" FROM \"TASK\" "
  2792	        String sql = "SELECT \"ID\" FROM \"TASK\" " +
  2793	            "JOIN \"TASK_OBJECTANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_OID\" = \"TASK\".\"ID\") "
  2794	            +
  2795	            "JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_EID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") "
  2796	            +
  2797	            "JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") "
  2798	            + "WHERE \"ENCOUNTER\".\"SUBMITTERID\" = '" + user.getUsername() +
  2799	            "' ORDER BY \"TASK\".\"CREATED\" desc";
  2800	        Query q = getPM().newQuery("javax.jdo.query.SQL", sql);
  2801	        q.setClass(Task.class);
  2802	        Collection c = (Collection)q.execute();
  2803	        List<Task> all = new ArrayList(c);
  2804	        q.closeAll();
  2805	        return all;
  2806	    }
  2807	
  2808	    public MatchResult getMatchResult(String id) {
  2809	        MatchResult mr = null;
  2810	
  2811	        try {
  2812	            mr = (MatchResult)(pm.getObjectById(pm.newObjectIdInstance(MatchResult.class, id),
  2813	                true));
  2814	        } catch (Exception ex) {
  2815	            ex.printStackTrace();
  2816	        }
  2817	        return mr;
  2818	    }
  2819	
  2820	    public List<MatchResult> getMatchResults(Task task) {
  2821	        List<MatchResult> all = new ArrayList<MatchResult>();
  2822	
  2823	        if (task == null) return all;
  2824	        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE task.id == '" + task.getId() +
  2825	            "'";
  2826	        Query query = pm.newQuery(filter);
  2827	        query.setOrdering("created DESC");
  2828	        Collection c = (Collection)query.execute();
  2829	        if (c != null) all = new ArrayList<MatchResult>(c);
  2830	        query.closeAll();
  2831	        return all;
  2832	    }
  2833	
  2834	    public List<MatchResult> getMatchResults(Annotation ann) {
  2835	        List<MatchResult> all = new ArrayList<MatchResult>();
  2836	
  2837	        if (ann == null) return all;
  2838	        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
  2839	            ann.getId() + "'";
  2840	        Query query = pm.newQuery(filter);
  2841	        query.setOrdering("created DESC");
  2842	        Collection c = (Collection)query.execute();
  2843	        if (c != null) all = new ArrayList<MatchResult>(c);
  2844	        query.closeAll();
  2845	        return all;
  2846	    }
  2847	
  2848	    // faster deletion of all MatchResults associated with Annotation
  2849	    public long deleteMatchResults(Annotation ann) {
  2850	        if (ann == null) return 0l;
  2851	        long t = System.currentTimeMillis();
  2852	        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
  2853	            ann.getId() + "'";
  2854	        Query query = pm.newQuery(filter);
  2855	        long ct = query.deletePersistentAll(); 
  2856	        query.closeAll();
  2857	        System.out.println("[DEBUG] deleteMatchResults() deleted " + ct + " [" + (System.currentTimeMillis() - t) + "ms] on " + ann);
  2858	        return ct;
  2859	    }
  2860	
  2861	    public List<MatchResultProspect> getMatchResultProspects(Annotation ann) {
  2862	        List<MatchResultProspect> all = new ArrayList<MatchResultProspect>();
  2863	
  2864	        if (ann == null) return all;
  2865	        String filter = "SELECT FROM org.ecocean.ia.MatchResultProspect WHERE annotation.id == '" +
  2866	            ann.getId() + "'";
  2867	        Query query = pm.newQuery(filter);
  2868	        Collection c = (Collection)query.execute();
  2869	        if (c != null) all = new ArrayList<MatchResultProspect>(c);
  2870	        query.closeAll();
  2871	        return all;
  2872	    }
  2873	
  2874	    public MarkedIndividual getMarkedIndividualQuiet(String name) {
  2875	        MarkedIndividual indiv = null;
  2876	
  2877	        try {
  2878	            indiv = ((org.ecocean.MarkedIndividual)(pm.getObjectById(pm.newObjectIdInstance(
  2879	                MarkedIndividual.class, name.trim()), true)));
  2880	        } catch (Exception nsoe) {

2026-05-19T15:19:12.869104Z ERROR codex_core::tools::router: error=write_stdin failed: stdin is closed for this session; rerun exec_command with tty=true to keep stdin open
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,340p'" in /mnt/c/Wildbook-clean2
 succeeded in 247ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '520,630p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,170p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '1,60p'" in /mnt/c/Wildbook-clean2
 succeeded in 214ms:
   520	            t = ((Task)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(
   521	                Task.class, taskId), true)));
   522	        } catch (Exception ex) {}; // swallow jdo not found noise
   523	        return t;
   524	    }
   525	
   526	    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
   527	        return getTasksFor(ann, myShepherd, null);
   528	    }
   529	
   530	    // TODO: evaluate if we should support versions for multiple objects (when needed)
   531	    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd, String ordering) {
   532	        String qstr =
   533	            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
   534	            + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
   535	        Query query = myShepherd.getPM().newQuery(qstr);
   536	
   537	        query.setIgnoreCache(true);
   538	        if (ordering == null) {
   539	            query.setOrdering("created");
   540	        } else {
   541	            query.setOrdering(ordering);
   542	        }
   543	        Collection c = (Collection)query.execute();
   544	        List<Task> listy = new ArrayList<Task>(c);
   545	        query.closeAll();
   546	        return listy;
   547	    }
   548	
   549	    public static List<Task> getRootTasksFor(Annotation ann, Shepherd myShepherd) {
   550	        return onlyRoots(getTasksFor(ann, myShepherd));
   551	    }
   552	
   553	    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
   554	        String qstr =
   555	            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
   556	            ma.getId() + " VARIABLES org.ecocean.media.MediaAsset obj";
   557	        Query query = myShepherd.getPM().newQuery(qstr);
   558	
   559	        query.setIgnoreCache(true);
   560	        query.setOrdering("created");
   561	        Collection c = (Collection)query.execute();
   562	        List<Task> listy = new ArrayList<Task>(c);
   563	        query.closeAll();
   564	        return listy;
   565	    }
   566	
   567	    public static List<Task> getRootTasksFor(MediaAsset ma, Shepherd myShepherd) {
   568	        return onlyRoots(getTasksFor(ma, myShepherd));
   569	    }
   570	
   571	    // takes a bunch of tasks and returns only roots (without duplication)
   572	    public static List<Task> onlyRoots(List<Task> all) {
   573	        List<Task> roots = new ArrayList<Task>();
   574	
   575	        for (Task t : all) {
   576	            Task r = t.getRootTask();
   577	            if (!roots.contains(r)) roots.add(r);
   578	        }
   579	        return roots;
   580	    }
   581	
   582	    public boolean areSelfAndOrAllChildrenComplete() {
   583	        boolean complete = false;
   584	
   585	        if (!hasChildren() && completionDateInMilliseconds != null) {
   586	            complete = true;
   587	        } else if (hasChildren()) {
   588	            List<Task> children = getChildren();
   589	            complete = true;
   590	            for (Task t : children) {
   591	                if (!t.areSelfAndOrAllChildrenComplete()) complete = false;
   592	            }
   593	        }
   594	        return complete;
   595	    }
   596	
   597	    /**
   598	     * Read the raw persisted status field without the timed-out-task
   599	     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
   600	     * Callers gating read-only decisions on status (e.g., the
   601	     * empty-match-prospects Track 2 batch gate) need this so a read of
   602	     * a sibling task's status doesn't mutate that task as a side
   603	     * effect.
   604	     *
   605	     * <p>(Empty-match-prospects design Track 2 C7.)</p>
   606	     */
   607	    public String getStoredStatus() {
   608	        return this.status;
   609	    }
   610	
   611	    public String getStatus(Shepherd myShepherd) {
   612	        // see if we might be dead in the water
   613	        // TODO skipping status==null cuz i cant figure out what this means and there are so many of them
   614	        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
   615	            this.status = "error";
   616	            long ti = timeInactive();
   617	            setStatusDetailsAddError("TIMEOUT",
   618	                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
   619	            return this.status;
   620	        }
   621	        // if status is not null, just send it
   622	        if (status != null) return status;
   623	        // otherwise
   624	        // note: this is LOCAL status :(  so it is not changing this.status, only returning the value
   625	        String status = "waiting to queue";
   626	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   627	            myShepherd);
   628	        if (logs != null && logs.size() > 0) {
   629	            Collections.reverse(logs); // so it has newest first like mostRecent above
   630	            IdentityServiceLog l = logs.get(0);

 succeeded in 217ms:
     1	/*
     2	    an instance of an ia.Task can be persisted and represents the state of that task
     3	    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
     4	 */
     5	package org.ecocean.ia;
     6	
     7	import java.util.ArrayList;
     8	import java.util.Collection;
     9	import java.util.Collections;
    10	import java.util.HashMap;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	import javax.jdo.Query;
    15	import org.apache.commons.lang3.builder.ToStringBuilder;
    16	import org.ecocean.Annotation;
    17	import org.ecocean.Encounter;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.shepherd.core.Shepherd;
    20	import org.ecocean.User;
    21	import org.ecocean.Util;
    22	import org.joda.time.DateTime;
    23	import org.json.JSONArray;
    24	import org.json.JSONObject;
    25	
    26	import org.ecocean.identity.IdentityServiceLog;
    27	
    28	public class Task implements java.io.Serializable {
    29	    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    30	    private String id = null;
    31	    private long created = -1;
    32	    private long modified = -1;
    33	    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    34	    private List<MediaAsset> objectMediaAssets = null;
    35	    private List<Annotation> objectAnnotations = null;
    36	    private Task parent = null;
    37	    private List<Task> children = null;
    38	    private String parameters = null;
    39	    private String status;
    40	    // general use, but notably will contain error details when status=error
    41	    private String statusDetails = null;
    42	    private Long completionDateInMilliseconds;
    43	    private String queueResumeMessage;
    44	
    45	    public Task() {
    46	        this(Util.generateUUID());
    47	    }
    48	    public Task(String id) {
    49	        this.id = id;
    50	        created = System.currentTimeMillis();
    51	        modified = System.currentTimeMillis();
    52	    }
    53	    // makes a child of the passed Task (and inherits the parameters!!)
    54	    public Task(Task p) {
    55	        this();
    56	        this.setParameters(p.getParameters());
    57	        this.setParent(p);
    58	    }
    59	
    60	    public String getId() {
    61	        return id;
    62	    }
    63	
    64	    public long getCreatedLong() {
    65	        return created;
    66	    }
    67	
    68	    public long getModifiedLong() {
    69	        return modified;
    70	    }
    71	
    72	    public long timeInactive() {
    73	        long now = System.currentTimeMillis();
    74	
    75	        if (modified > 0) return (now - modified);
    76	        if (created > 0) return (now - created);
    77	        // weird or inconclusive:
    78	        return -1l;
    79	    }
    80	
    81	    public boolean timedOutDueToInactivity() {
    82	        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    83	    }
    84	
    85	    public boolean statusInEndState() {
    86	        if ("completed".equals(status)) return true;
    87	        if ("error".equals(status)) return true;
    88	        // ml-service migration v2: "dropped-stale" is terminal — the task's
    89	        // target was deleted before the queued job ran. Neither success nor
    90	        // error; the inactivity-timeout watchdog must not flip it to error.
    91	        if ("dropped-stale".equals(status)) return true;
    92	        return false;
    93	    }
    94	
    95	    public void setModified() {
    96	        modified = System.currentTimeMillis();
    97	    }
    98	
    99	    public boolean canUserAccess(User user, Shepherd myShepherd) {
   100	        if (user == null) return false;
   101	        if (user.isAdmin(myShepherd)) return true;
   102	        Encounter enc = null;
   103	        // if we have annotations, use first to determine encounter
   104	        if (this.countObjectAnnotations() > 0) {
   105	            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
   106	        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
   107	            MediaAsset ma = this.getObjectMediaAssets().get(0);
   108	            // we iterate over all annots on this asset til we find an encounter.
   109	            // it might be better to find *all* encounters and return access based on each;
   110	            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
   111	            // so i think this means asset-based access of tasks will be rare or unused anyway
   112	            for (Annotation ann : ma.getAnnotations()) {
   113	                if (ann != null) enc = ann.findEncounter(myShepherd);
   114	                if (enc != null) break;
   115	            }
   116	        }
   117	        if (enc == null) return false;
   118	        if (enc.isPubliclyReadable()) return true;
   119	        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
   120	        return enc.canUserAccess(user, myShepherd.getContext());
   121	    }
   122	
   123	/*
   124	    // not really convinced these are accurate enough to use
   125	    //   actual computation of these things is complicated
   126	    //   leaving these for future potential exploration, if needed.
   127	
   128	    public boolean isTypeDetection() {
   129	        if (this.hasObjectMediaAssets()) return true;
   130	        if (this.hasObjectAnnotations()) return false;
   131	        if (this.parameters == null) return false;
   132	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
   133	        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
   134	        return false;
   135	    }
   136	    public boolean isTypeIdentification() {
   137	        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
   138	        if (this.parameters == null) return false;
   139	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
   140	        return false;
   141	    }
   142	
   143	    public boolean initiatedWithDetection() {
   144	        if (this.parameters == null) return false;
   145	        return this.getParameters().optBoolean("ibeis.detection", false);
   146	    }
   147	    public boolean initiatedWithIdentification() {
   148	        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
   149	    }
   150	 */
   151	    public int countObjectMediaAssets() {
   152	        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
   153	    }
   154	
   155	    public int countObjectAnnotations() {
   156	        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
   157	    }
   158	
   159	    public int countObjects() {
   160	        return countObjectMediaAssets() + countObjectAnnotations();
   161	    }
   162	
   163	    // not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
   164	    public boolean hasObjectMediaAssets() {
   165	        return (countObjectMediaAssets() > 0);
   166	    }
   167	
   168	    public boolean hasObjectAnnotations() {
   169	        return (countObjectAnnotations() > 0);
   170	    }

 succeeded in 259ms:
     1	<!DOCTYPE jdo PUBLIC
     2	    "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN"
     3	    "http://java.sun.com/dtd/jdo_2_0.dtd">
     4	
     5	<jdo>
     6	    <package name="org.ecocean.ia">
     7	
     8		<class name="Task" identity-type="application">
     9			<field name="id" primary-key="true">
    10	        		<column length="36"/>
    11			</field>
    12			<field name="created">
    13				<index name="TASK_CREATED_idx" />
    14			</field>
    15	
    16			<!-- this points up to the parent -->
    17	      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" recursion-depth="3"/>
    18	
    19			<!-- this uses parent to find child tasks -->
    20	            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
    21	                	<collection element-type="org.ecocean.ia.Task" dependent-element="true" />
    22	                	<join />
    23	            	</field>
    24	
    25	<!--  to update your db table:
    26	alter table "TASK" alter column "PARAMETERS" type text;
    27	-->
    28	      		<field name="parameters" persistence-modifier="persistent">
    29	        		<column jdbc-type="LONGVARCHAR"/>
    30	      		</field>
    31				
    32				<field name="status" persistence-modifier="persistent">
    33	        		<column jdbc-type="LONGVARCHAR"/>
    34	      		</field>
    35	
    36	      		<field name="statusDetails" persistence-modifier="persistent">
    37	        		<column jdbc-type="LONGVARCHAR"/>
    38	      		</field>
    39				
    40				<field name="queueResumeMessage" persistence-modifier="persistent">
    41	        		<column jdbc-type="LONGVARCHAR"/>
    42	      		</field>
    43				
    44				<field name="completionDateInMilliseconds" persistence-modifier="persistent">
    45					<column jdbc-type="BIGINT" allows-null="true"/>
    46				</field>
    47	
    48	<!--
    49			<field name="id" field-type="java.util.UUID" primary-key="true" value-strategy="uuid">
    50	                        <column sql-type="uuid" />
    51	                </field>
    52	-->
    53	<!--
    54			<field name="parentId" persistence-modifier="persistent">
    55				<column jdbc-type="INT" allows-null="true"/>
    56			</field>
    57	            	<field name="revision" persistence-modifier="persistent">
    58	                	<column jdbc-type="BIGINT" allows-null="false"/>
    59	            	</field>
    60	-->

exec
/bin/bash -lc "rg -n \"void setParent|setParent\\(|getChildren\\(|hasChildren\\(\" src/main/java/org/ecocean/ia/Task.java && nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '170,260p'" in /mnt/c/Wildbook-clean2
 succeeded in 306ms:
57:        this.setParent(p);
247:    public List<Task> getChildren() {
270:    public void setParent(Task t) {
288:    public boolean hasChildren() {
296:        if (!this.hasChildren()) {
313:        if (!this.hasChildren()) return null;
323:        if (!this.hasChildren()) return null;
335:        if (this.hasChildren())
346:        if (this.hasChildren())
480:        if (includeChildren && this.hasChildren()) {
585:        if (!hasChildren() && completionDateInMilliseconds != null) {
587:        } else if (hasChildren()) {
588:            List<Task> children = getChildren();
662:        if (this.hasChildren()) {
666:            for (Task childTask : this.getChildren()) {
667:                if (childTask.hasChildren()) {
668:                    for (Task childTask2 : childTask.getChildren()) {
851:            if ((mr == null) && !hasChildren()) {
867:        if (hasChildren()) {
   170	    }
   171	
   172	    public boolean hasObjects() {
   173	        return (countObjects() > 0);
   174	    }
   175	
   176	    public void setObjectMediaAssets(List<MediaAsset> mas) {
   177	        objectMediaAssets = mas;
   178	    }
   179	
   180	    public void setObjectAnnotations(List<Annotation> anns) {
   181	        objectAnnotations = anns;
   182	    }
   183	
   184	    public List<MediaAsset> getObjectMediaAssets() {
   185	        return objectMediaAssets;
   186	    }
   187	
   188	    public List<Annotation> getObjectAnnotations() {
   189	        return objectAnnotations;
   190	    }
   191	
   192	    public int numberMediaAssets() {
   193	        return Util.collectionSize(objectMediaAssets);
   194	    }
   195	
   196	    public int numberAnnotations() {
   197	        return Util.collectionSize(objectAnnotations);
   198	    }
   199	
   200	    // kinda for convenience?
   201	    public boolean addObject(MediaAsset ma) {
   202	        if (ma == null) return false;
   203	        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
   204	        if (!objectMediaAssets.contains(ma)) {
   205	            objectMediaAssets.add(ma);
   206	            return true;
   207	        }
   208	        return false;
   209	    }
   210	
   211	    public boolean addObject(Annotation ann) {
   212	        if (ann == null) return false;
   213	        if (objectAnnotations == null) objectAnnotations = new ArrayList<Annotation>();
   214	        if (!objectAnnotations.contains(ann)) {
   215	            objectAnnotations.add(ann);
   216	            return true;
   217	        }
   218	        return false;
   219	    }
   220	
   221	    public boolean removeObject(Annotation ann) {
   222	        if (ann != null && objectAnnotations != null && objectAnnotations.contains(ann)) {
   223	            objectAnnotations.remove(ann);
   224	            return true;
   225	        }
   226	        return false;
   227	    }
   228	
   229	    public boolean removeObject(MediaAsset ma) {
   230	        if (ma != null && objectMediaAssets != null && objectMediaAssets.contains(ma)) {
   231	            objectMediaAssets.remove(ma);
   232	            return true;
   233	        }
   234	        return false;
   235	    }
   236	
   237	    public boolean contains(Annotation ann) {
   238	        if (objectAnnotations == null) return false;
   239	        return objectAnnotations.contains(ann);
   240	    }
   241	
   242	    public boolean contains(MediaAsset ma) {
   243	        if (objectMediaAssets == null) return false;
   244	        return objectMediaAssets.contains(ma);
   245	    }
   246	
   247	    public List<Task> getChildren() {
   248	        return children;
   249	    }
   250	
   251	    public void setChildren(List<Task> kids) {
   252	        if (kids == null) {
   253	            children = null;
   254	            return;
   255	        }
   256	        children = new ArrayList<Task>();
   257	        for (Task kid : kids) {
   258	            this.addChild(kid); // let this do the work
   259	        }
   260	    }

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '260,315p'" in /mnt/c/Wildbook-clean2
 succeeded in 280ms:
   260	    }
   261	
   262	    public List<Task> addChild(Task kid) {
   263	        if (children == null) children = new ArrayList<Task>();
   264	        if (kid == null) return children;
   265	        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
   266	        if (!children.contains(kid)) children.add(kid);
   267	        return children;
   268	    }
   269	
   270	    public void setParent(Task t) {
   271	        parent = t;
   272	        t.addChild(this);
   273	    }
   274	
   275	    public Task getParent() {
   276	        return parent;
   277	    }
   278	
   279	    public String getParentId() {
   280	        if (parent == null) return null;
   281	        return parent.getId();
   282	    }
   283	
   284	    public int numChildren() {
   285	        return (children == null) ? 0 : children.size();
   286	    }
   287	
   288	    public boolean hasChildren() {
   289	        return (this.numChildren() > 0);
   290	    }
   291	
   292	    // omg i am going to assume no looping
   293	    public List<Task> getLeafTasks() {
   294	        List<Task> leaves = new ArrayList<Task>();
   295	
   296	        if (!this.hasChildren()) {
   297	            leaves.add(this);
   298	            return leaves;
   299	        }
   300	        for (Task kid : children) {
   301	            leaves.addAll(kid.getLeafTasks());
   302	        }
   303	        return leaves;
   304	    }
   305	
   306	    public Task getRootTask() {
   307	        if (parent == null) return this;
   308	        return parent.getRootTask();
   309	    }
   310	
   311	    public Task deepContains(Annotation ann) {
   312	        if (this.contains(ann)) return this;
   313	        if (!this.hasChildren()) return null;
   314	        for (Task kid : children) {
   315	            Task found = kid.deepContains(ann);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class MLService|getConfigs\\(|_mlservice_conf|api_endpoint|base_url|explain\" src/main/java/org/ecocean/ia src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '260,560p'" in /mnt/c/Wildbook-clean2
 succeeded in 239ms:
     1	package org.ecocean.ia;
     2	
     3	import org.json.JSONArray;
     4	import org.json.JSONObject;
     5	
     6	import java.net.MalformedURLException;
     7	import java.net.URL;
     8	import java.util.ArrayList;
     9	import java.util.List;
    10	
    11	import org.ecocean.Annotation;
    12	import org.ecocean.Embedding;
    13	import org.ecocean.ia.Task;
    14	import org.ecocean.identity.IBEISIA;
    15	import org.ecocean.IAJsonProperties;
    16	import org.ecocean.media.Feature;
    17	import org.ecocean.media.FeatureType;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.RestClient;
    20	import org.ecocean.servlet.IAGateway;
    21	import org.ecocean.shepherd.core.Shepherd;
    22	import org.ecocean.Util;
    23	
    24	import java.io.IOException;
    25	
    26	// https://github.com/WildMeOrg/ml-service
    27	
    28	public class MLService {
    29	    private IAJsonProperties iaConfig = null;
    30	
    31	    public MLService() {
    32	        iaConfig = IAJsonProperties.iaConfig();
    33	    }
    34	
    35	    public JSONObject initiateRequest(MediaAsset ma, String taxonomyString)
    36	    throws IOException {
    37	        addToQueue(createJobData(ma, taxonomyString), null);
    38	        return null;
    39	    }
    40	
    41	    public JSONObject initiateRequest(Annotation ann, String taxonomyString)
    42	    throws IOException {
    43	        return initiateRequest(ann, taxonomyString, null);
    44	    }
    45	
    46	    public JSONObject initiateRequest(Annotation ann, String taxonomyString, Task task)
    47	    throws IOException {
    48	        addToQueue(createJobData(ann, taxonomyString), task);
    49	        return null;
    50	    }
    51	
    52	    public IAJsonProperties getIAConfig() {
    53	        return iaConfig;
    54	    }
    55	
    56	    // there can be multiple configs (differing model_id)
    57	    public List<JSONObject> getConfigs(String passedTxStr)
    58	    throws IAException {
    59	        IAJsonProperties iac = getIAConfig();
    60	
    61	        if (iac == null) throw new IAException("MLService.getConfigs() iac configuration problem");
    62	        if (passedTxStr == null)
    63	            throw new IAException("MLService.getConfigs() null passed taxonomy");
    64	        String taxonomyString = passedTxStr.replaceAll(" ", "."); // need dots, not spaces
    65	        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
    66	        if (mlc == null)
    67	            throw new IAException(
    68	                      "MLService.getConfigs() configuration problem with taxonomyString=" +
    69	                      taxonomyString);
    70	        JSONArray confs = null;
    71	        try {
    72	            confs = (JSONArray)mlc;
    73	        } catch (Exception ex) {
    74	            ex.printStackTrace();
    75	        }
    76	        if (confs == null)
    77	            throw new IAException(
    78	                      "MLService.getConfigs() configuration problem with taxonomyString=" +
    79	                      taxonomyString + "; mlc=" + mlc);
    80	        List<JSONObject> configs = new ArrayList<JSONObject>();
    81	        for (int i = 0; i < confs.length(); i++) {
    82	            JSONObject jc = confs.optJSONObject(i);
    83	            if (jc != null) configs.add(jc);
    84	        }
    85	        return configs;
    86	    }
    87	
    88	    public void addToQueue(JSONObject jobData, Task task)
    89	    throws IOException {
    90	        if (jobData == null) return;
    91	        if (task != null) jobData.put("taskId", task.getId());
    92	        IAGateway.addToDetectionQueue("context0", jobData.toString());
    93	    }
    94	
    95	    // i think we *must* pass taxonomyString here
    96	    public JSONObject createJobData(MediaAsset ma, String taxonomyString) {
    97	        JSONObject data = new JSONObject();
    98	
    99	        data.put("MLService", true);
   100	        data.put("taxonomyString", taxonomyString);
   101	
   102	        JSONArray maIds = new JSONArray();
   103	        maIds.put(ma.getIdInt());
   104	        data.put("mediaAssetIds", maIds);
   105	        return data;
   106	    }
   107	
   108	    public JSONObject createJobData(Annotation ann, String taxonomyString) {
   109	        JSONObject data = new JSONObject();
   110	
   111	        data.put("MLService", true);
   112	        data.put("taxonomyString", taxonomyString);
   113	
   114	        JSONArray annIds = new JSONArray();
   115	        annIds.put(ann.getId());
   116	        data.put("annotationIds", annIds);
   117	        return data;
   118	    }
   119	
   120	    public void processQueueJob(JSONObject jobData) {
   121	        System.out.println("#################################################### processing: " +
   122	            jobData.toString(8));
   123	        Shepherd myShepherd = new Shepherd("context0");
   124	        myShepherd.setAction("MLService.processQueueJob");
   125	        myShepherd.beginDBTransaction();
   126	        FeatureType.initAll(myShepherd);
   127	        Task task = myShepherd.getTask(jobData.optString("taskId", null));
   128	        JSONArray ids = jobData.optJSONArray("mediaAssetIds");
   129	        // skipEmbedding will set true if there was a non-requeuable config problem
   130	        // (probably not configured for _mlservice in IA.json) so we just give up and
   131	        // let ident do its thing
   132	        boolean skipEmbedding = false;
   133	        try {
   134	            // got some asset ids
   135	            if (ids != null) {
   136	                for (String maId : Util.jsonArrayToStringList(ids)) {
   137	                    System.out.println("[DEBUG] MLService.processQueueJob() maId=" + maId + " [" +
   138	                        task + "]");
   139	                    send(myShepherd.getMediaAsset(maId), jobData.optString("taxonomyString", null),
   140	                        myShepherd);
   141	                }
   142	                // maybe annot ids?
   143	            } else {
   144	                ids = jobData.optJSONArray("annotationIds");
   145	                if (ids != null) {
   146	                    for (String annId : Util.jsonArrayToStringList(ids)) {
   147	                        System.out.println("[DEBUG] MLService.processQueueJob() annId=" + annId +
   148	                            " [" + task + "]");
   149	                        send(myShepherd.getAnnotation(annId),
   150	                            jobData.optString("taxonomyString", null), myShepherd);
   151	                    }
   152	                }
   153	            }
   154	            if (task != null) task.setStatus("completed");
   155	        } catch (IAException iaex) {
   156	            System.out.println("MLService.processQueueJob() threw " + iaex + " with jobData=" +
   157	                jobData);
   158	            iaex.printStackTrace();
   159	            if (task != null) {
   160	                task.setStatus("error");
   161	                task.setStatusDetailsAddError("UNKNOWN", "MLService job: " + iaex);
   162	            }
   163	            if (iaex.shouldRequeue()) {
   164	                requeueJob(jobData, iaex.shouldIncrement());
   165	            } else {
   166	                // we might want more complex logic to determine if we really should give up
   167	                skipEmbedding = true;
   168	            }
   169	        } finally {
   170	            // we end up here after *each* annotation, so we are "done" when all annotations have been processed
   171	            boolean taskComplete = skipEmbedding || areAllEmbeddingsExtracted(task);
   172	            if (taskComplete && (task != null)) task.setCompletionDateInMilliseconds();
   173	            myShepherd.commitDBTransaction();
   174	            if (taskComplete) {
   175	                // now we are done we can fake a callback to initiate identification
   176	                JSONObject fakeResp = new JSONObject();
   177	                fakeResp.put("embeddingExtraction", true);
   178	                // taskComplete is only true if we have *some* annots
   179	                JSONObject annMap = new JSONObject();
   180	                if (task != null)
   181	                    for (Annotation ann : task.getObjectAnnotations()) {
   182	                        MediaAsset ma = ann.getMediaAsset();
   183	                        if (ma == null) continue; // snh
   184	                        if (!annMap.has(ma.getId())) annMap.put(ma.getId(), new JSONArray());
   185	                        annMap.getJSONArray(ma.getId()).put(ann.getId());
   186	                    }
   187	                fakeResp.put("annotationMap", annMap);
   188	                JSONObject cbRes = IBEISIA.processCallback((task == null) ? null : task.getId(),
   189	                    fakeResp, myShepherd.getContext(), null);
   190	                System.out.println("[DEBUG] MLService.processQueueJob() [" + task +
   191	                    " complete] cbRes=" + cbRes);
   192	            }
   193	            myShepherd.closeDBTransaction();
   194	        }
   195	    }
   196	
   197	    // true if all annotations "are done" from (trying to) extract embeddings
   198	    private boolean areAllEmbeddingsExtracted(Task task) {
   199	        if (task == null) return false;
   200	        List<Annotation> anns = task.getObjectAnnotations();
   201	        // we return false here because there is no reason to send to ident in this case
   202	        if (Util.collectionIsEmptyOrNull(anns)) return false;
   203	        // we iterate over annotations and only return false if we find one explicitly still
   204	        // in processing state. this means *any* other (complete, error, etc) get counted as "done"
   205	        for (Annotation ann : anns) {
   206	            if (IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(ann.getIdentificationStatus()))
   207	                return false;
   208	        }
   209	        System.out.println(
   210	            "[DEBUG] MLService.areAllEmbeddingsExtracted() fell thru (aka true) on " + anns.size() +
   211	            " annots for " + task);
   212	        return true;
   213	    }
   214	
   215	    public void requeueJob(JSONObject jobData, boolean increment) {
   216	        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
   217	            jobData);
   218	        // this handles a bunch of messiness, including max retries etc
   219	        IAGateway.requeueJob(jobData, increment);
   220	    }
   221	
   222	    public void send(MediaAsset ma, String taxonomyString, Shepherd myShepherd)
   223	    throws IAException {
   224	        if (ma == null) throw new IAException("null MediaAsset passed");
   225	        for (JSONObject conf : getConfigs(taxonomyString)) {
   226	            JSONObject payload = createPayload(ma, conf);
   227	            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
   228	                payload);
   229	            // got results, now we try to use them
   230	            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
   231	                "; RESPONSE => " + res);
   232	            List<Annotation> anns = processMediaAssetResults(ma, res);
   233	            System.out.println("MLService.send() created " + anns.size() + " anns on " + ma + ": " +
   234	                anns);
   235	            // FIXME persist anns using myShepherd
   236	            // FIXME send along to ident????? (but using vectors!!!????!)
   237	        }
   238	    }
   239	
   240	    public List<Annotation> processMediaAssetResults(MediaAsset ma, JSONObject res)
   241	    throws IAException {
   242	        if (res == null) throw new IAException("empty results");
   243	        if (!res.optBoolean("success", false))
   244	            throw new IAException("results success=false: " + res);
   245	        JSONArray bboxes = res.optJSONArray("bboxes");
   246	        if (bboxes == null) throw new IAException("null bboxes in results: " + res);
   247	        List<Annotation> anns = new ArrayList<Annotation>();
   248	        if (bboxes.length() < 1) return anns;
   249	        // TODO do we ever care about scores?
   250	        List<Double> scores = Util.jsonArrayToDoubleList(res.optJSONArray("scores"));
   251	        if ((scores == null) || (scores.size() != bboxes.length()))
   252	            throw new IAException("scores size does not match bboxes: " + res);
   253	        List<Double> thetas = Util.jsonArrayToDoubleList(res.optJSONArray("thetas"));
   254	        if ((thetas == null) || (thetas.size() != bboxes.length()))
   255	            throw new IAException("thetas size does not match bboxes: " + res);
   256	        List<String> classNames = Util.jsonArrayToStringList(res.optJSONArray("class_names"));
   257	        if ((classNames == null) || (classNames.size() != bboxes.length()))
   258	            throw new IAException("class_names size does not match bboxes: " + res);
   259	        // FIXME wtf happened to viewpoint??? :)
   260	        // iterate over bboxes and make annots

 succeeded in 318ms:
   260	        // iterate over bboxes and make annots
   261	        for (int i = 0; i < bboxes.length(); i++) {
   262	            List<Double> xywh = Util.jsonArrayToDoubleList(bboxes.optJSONArray(i));
   263	            if (xywh == null) throw new IAException("error parsing bbox[" + i + "] (null): " + res);
   264	            if (xywh.size() != 4)
   265	                throw new IAException("error parsing bbox[" + i + "] (size): " + res);
   266	            Annotation ann = createAnnotation(xywh, thetas.get(i), classNames.get(i), null);
   267	            Annotation exists = ma.findAnnotation(ann, true);
   268	            if (exists != null) { // i guess we just skip this and do not create???
   269	                System.out.println("[WARNING] MLService.processMediaAssetResults() skipping i=" +
   270	                    i + " (res=" + res + ") due to existing matching " + exists);
   271	                continue;
   272	            }
   273	            ma.addFeature(ann.getFeature());
   274	            anns.add(ann);
   275	        }
   276	        ma.setDetectionStatus("complete");
   277	        return anns;
   278	    }
   279	
   280	    private Annotation createAnnotation(List<Double> bbox, Double theta, String iaClass,
   281	        String viewpoint)
   282	    throws IAException {
   283	        if ((bbox == null) || (bbox.size() != 4))
   284	            throw new IAException("createAnnotation() bad bbox");
   285	        if ((bbox.get(2) < 1.0d) || (bbox.get(3) < 1.0d))
   286	            throw new IAException("createAnnotation() bad bbox width/height");
   287	        JSONObject fparams = new JSONObject();
   288	        fparams.put("x", bbox.get(0));
   289	        fparams.put("y", bbox.get(1));
   290	        fparams.put("width", bbox.get(2));
   291	        fparams.put("height", bbox.get(3));
   292	        fparams.put("theta", ((theta == null) ? 0.0d : theta));
   293	        fparams.put("viewpoint", viewpoint);
   294	        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
   295	        Annotation ann = new Annotation(null, ft, iaClass);
   296	        ann.setViewpoint(viewpoint);
   297	        return ann;
   298	    }
   299	
   300	    public void send(Annotation ann, String taxonomyString, Shepherd myShepherd)
   301	    throws IAException {
   302	        if (ann == null) throw new IAException("null Annotation passed");
   303	        for (JSONObject conf : getConfigs(taxonomyString)) {
   304	            JSONObject payload = createPayload(ann, conf);
   305	            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
   306	                payload);
   307	            // got results, now we try to use them
   308	            JSONObject logRes = new JSONObject(res.toString());
   309	            if (logRes.optJSONArray("embeddings") != null)
   310	                logRes.put("embeddings",
   311	                    "TRUNCATED [length=" + logRes.getJSONArray("embeddings").toString().length() +
   312	                    "]");
   313	            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
   314	                "; RESPONSE => " + logRes);
   315	            processAnnotationResults(ann, res, myShepherd);
   316	            System.out.println("MLService.send() process results on " + ann);
   317	        }
   318	    }
   319	
   320	    // not sure what (if anything) we need to return here
   321	    public void processAnnotationResults(Annotation ann, JSONObject res, Shepherd myShepherd)
   322	    throws IAException {
   323	        if (res == null) throw new IAException("empty results");
   324	        if (ann == null) throw new IAException("null Annotation");
   325	        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   326	        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
   327	        JSONArray embs = res.optJSONArray("embeddings");
   328	        if (embs == null) throw new IAException("results has no embeddings array: " + res);
   329	        // in our case we should have one embedding in there
   330	        if ((embs.length() < 1) || (embs.optJSONArray(0) == null))
   331	            throw new IAException("results has no embeddings array[0]: " + res);
   332	        JSONArray vecArr = embs.getJSONArray(0);
   333	        String[] methodValues = getMethodValues(res);
   334	        Embedding emb = new Embedding(ann, methodValues[0], methodValues[1], vecArr);
   335	        // maybe this is unwise? could 2 embeddings *from different methods* have same vectors? TODO
   336	        Embedding exists = ann.findEmbeddingByVector(emb);
   337	        if (exists != null) {
   338	            System.out.println("[WARNING] MLService.processAnnotationResults(): skipping; " + ann +
   339	                " already has: " + exists);
   340	            return;
   341	        }
   342	        ann.addEmbedding(emb);
   343	        // FIXME persist or whatever????
   344	        System.out.println("[DEBUG] MLService.processAnnotationResults(): added " + emb + " to " +
   345	            ann);
   346	    }
   347	
   348	    public static String[] getMethodValues(JSONObject conf) {
   349	        String[] mv = { null, null };
   350	
   351	        if (conf == null) return mv;
   352	        mv[0] = conf.optString("model_id", null);
   353	        // kinda hack version splitting here but... and i think some might not have dash, like "msv3"  :(
   354	        if ((mv[0] != null) && mv[0].contains("-")) {
   355	            String[] parts = mv[0].split("\\-");
   356	            mv[0] = parts[0];
   357	            mv[1] = parts[1];
   358	        }
   359	        return mv;
   360	    }
   361	
   362	    private JSONObject sendPayload(String endpoint, JSONObject payload)
   363	    throws IAException {
   364	        if (endpoint == null) throw new IAException("null api_endpoint");
   365	        URL url = null;
   366	        try {
   367	            url = new URL(endpoint);
   368	        } catch (MalformedURLException urlEx) {
   369	            throw new IAException("api_endpoint url error: " + urlEx);
   370	        }
   371	        try {
   372	            // throws IOException, java.net.ProtocolException
   373	            JSONObject res = RestClient.postJSON(url, payload, null);
   374	            return res;
   375	        } catch (Exception ex) {
   376	            System.out.println("sendPayload(" + url + ") threw " + ex);
   377	            ex.printStackTrace();
   378	            String msg = ex.getMessage();
   379	            if (msg == null) msg = ""; // safety against NPE
   380	            if (msg.contains("Connection refused")) {
   381	                throw new IAException("Connection refused", true, true);
   382	            } else if (msg.contains("Read timed out")) {
   383	                throw new IAException("time out", true); // no increment
   384	            } else if (msg.contains("HTTP error code : 500")) {
   385	                throw new IAException("500 error", true, true);
   386	            } else if (msg.contains("HTTP error code : 502")) {
   387	                throw new IAException("502 error", true); // we requeue, but dont increment this?
   388	            }
   389	            // default behavior is to retry, but with increment
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

 succeeded in 755ms:
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:105:        // then MLService.getMethodValues for legacy api_endpoint
src/main/java/org/ecocean/ia/IA.java:161:        // _mlservice_conf is configured, route per-asset through the
src/main/java/org/ecocean/ia/IA.java:163:        // WBIA path below — production deployments without _mlservice_conf
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:33: *       {@code /explain/} HTTP POST. No JDO state held.</li>
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:160:     * Phase B: POST to {@code /explain/} and extract the base64 image.
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:190:     * Build the {@code /explain/} POST body. Pure function; package-
src/main/java/org/ecocean/ia/MatchResult.java:425:     * {@code /explain/} endpoint rejects those with HTTP 400. Shifting
src/main/java/org/ecocean/ia/MatchResult.java:472:     * Build the bb1/bb2 payload for {@code /explain/}: each key gets
src/main/java/org/ecocean/ia/MatchResult.java:503:            List<JSONObject> confs = mls.getConfigs(txStr);
src/main/java/org/ecocean/ia/MatchResult.java:505:            urlStr = confs.get(0).optString("api_endpoint", null);
src/main/java/org/ecocean/ia/MatchResult.java:510:        return new URL(urlStr + "/explain/");
src/main/java/org/ecocean/ia/MLService.java:28:public class MLService {
src/main/java/org/ecocean/ia/MLService.java:57:    public List<JSONObject> getConfigs(String passedTxStr)
src/main/java/org/ecocean/ia/MLService.java:61:        if (iac == null) throw new IAException("MLService.getConfigs() iac configuration problem");
src/main/java/org/ecocean/ia/MLService.java:63:            throw new IAException("MLService.getConfigs() null passed taxonomy");
src/main/java/org/ecocean/ia/MLService.java:65:        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
src/main/java/org/ecocean/ia/MLService.java:68:                      "MLService.getConfigs() configuration problem with taxonomyString=" +
src/main/java/org/ecocean/ia/MLService.java:78:                      "MLService.getConfigs() configuration problem with taxonomyString=" +
src/main/java/org/ecocean/ia/MLService.java:225:        for (JSONObject conf : getConfigs(taxonomyString)) {
src/main/java/org/ecocean/ia/MLService.java:227:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
src/main/java/org/ecocean/ia/MLService.java:303:        for (JSONObject conf : getConfigs(taxonomyString)) {
src/main/java/org/ecocean/ia/MLService.java:305:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
src/main/java/org/ecocean/ia/MLService.java:364:        if (endpoint == null) throw new IAException("null api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:369:            throw new IAException("api_endpoint url error: " + urlEx);
src/main/java/org/ecocean/ia/MLService.java:402:        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:418:        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MlServiceClient.java:74:     * @param config      a single {@code _mlservice_conf} entry from IA.json
src/main/java/org/ecocean/ia/MlServiceClient.java:247:            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:34: * entirely (legacy api_endpoint-only configs that can't derive a
src/main/java/org/ecocean/ia/Task.java:749:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
src/main/java/org/ecocean/Embedding.java:268:        // api_endpoint) as well as legacy entries (with `api_endpoint`).
src/main/java/org/ecocean/Embedding.java:271:            || Util.stringExists(iaConfig.optString("api_endpoint", null));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:196:                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:199:                    "_mlservice_conf missing predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:214:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:275:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:739:        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/api/SiteSettings.java:137:                            // idOptCopy.remove("api_endpoint");
src/main/java/org/ecocean/IAJsonProperties.java:268:        // The implicit `_mlservice_conf` appending that used to live here was
src/main/java/org/ecocean/IAJsonProperties.java:285:    // `_mlservice_conf` array is populated. Both conditions are enforced
src/main/java/org/ecocean/IAJsonProperties.java:286:    // by `getActiveMlServiceConfigs`. A species with `_mlservice_conf` but
src/main/java/org/ecocean/IAJsonProperties.java:291:        return taxonomyKey(taxy) + "._mlservice_conf";
src/main/java/org/ecocean/IAJsonProperties.java:295:     * Returns the per-taxonomy `_mlservice_conf` JSONArray iff the species'
src/main/java/org/ecocean/ia/Task.java:749:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:33: *       {@code /explain/} HTTP POST. No JDO state held.</li>
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:160:     * Phase B: POST to {@code /explain/} and extract the base64 image.
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:190:     * Build the {@code /explain/} POST body. Pure function; package-
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:34: * entirely (legacy api_endpoint-only configs that can't derive a
src/main/java/org/ecocean/ia/IA.java:161:        // _mlservice_conf is configured, route per-asset through the
src/main/java/org/ecocean/ia/IA.java:163:        // WBIA path below — production deployments without _mlservice_conf
src/main/java/org/ecocean/ia/MLService.java:28:public class MLService {
src/main/java/org/ecocean/ia/MLService.java:57:    public List<JSONObject> getConfigs(String passedTxStr)
src/main/java/org/ecocean/ia/MLService.java:61:        if (iac == null) throw new IAException("MLService.getConfigs() iac configuration problem");
src/main/java/org/ecocean/ia/MLService.java:63:            throw new IAException("MLService.getConfigs() null passed taxonomy");
src/main/java/org/ecocean/ia/MLService.java:65:        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
src/main/java/org/ecocean/ia/MLService.java:68:                      "MLService.getConfigs() configuration problem with taxonomyString=" +
src/main/java/org/ecocean/ia/MLService.java:78:                      "MLService.getConfigs() configuration problem with taxonomyString=" +
src/main/java/org/ecocean/ia/MLService.java:225:        for (JSONObject conf : getConfigs(taxonomyString)) {
src/main/java/org/ecocean/ia/MLService.java:227:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
src/main/java/org/ecocean/ia/MLService.java:303:        for (JSONObject conf : getConfigs(taxonomyString)) {
src/main/java/org/ecocean/ia/MLService.java:305:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
src/main/java/org/ecocean/ia/MLService.java:364:        if (endpoint == null) throw new IAException("null api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:369:            throw new IAException("api_endpoint url error: " + urlEx);
src/main/java/org/ecocean/ia/MLService.java:402:        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:418:        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:105:        // then MLService.getMethodValues for legacy api_endpoint
src/main/java/org/ecocean/ia/MlServiceClient.java:74:     * @param config      a single {@code _mlservice_conf} entry from IA.json
src/main/java/org/ecocean/ia/MlServiceClient.java:247:            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
src/main/java/org/ecocean/ia/MatchResult.java:425:     * {@code /explain/} endpoint rejects those with HTTP 400. Shifting
src/main/java/org/ecocean/ia/MatchResult.java:472:     * Build the bb1/bb2 payload for {@code /explain/}: each key gets
src/main/java/org/ecocean/ia/MatchResult.java:503:            List<JSONObject> confs = mls.getConfigs(txStr);
src/main/java/org/ecocean/ia/MatchResult.java:505:            urlStr = confs.get(0).optString("api_endpoint", null);
src/main/java/org/ecocean/ia/MatchResult.java:510:        return new URL(urlStr + "/explain/");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:196:                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:199:                    "_mlservice_conf missing predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:214:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:275:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:739:        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/OpenSearch.java:647:            // embedding entry. (Legacy api_endpoint-only configs that
src/main/java/org/ecocean/servlet/MediaAssetAttach.java:60:           ok, leaving this for prosperity. this FAILS on the .getString() for reasons i cant explain when passed an int (non-string) however, the

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '650,760p'" in /mnt/c/Wildbook-clean2
 succeeded in 261ms:
   650	    }
   651	
   652	    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
   653	        String bboxKey, String thetaKey) {
   654	        if (ma == null) return null;
   655	        for (Annotation ann : ma.getAnnotations()) {
   656	            if (ann == null) continue;
   657	            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
   658	            if (!sameString(bboxKey, ann.getBboxKey())) continue;
   659	            if (!sameString(thetaKey, ann.getThetaKey())) continue;
   660	            return ann;
   661	        }
   662	        return null;
   663	    }
   664	
   665	    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
   666	        if (!Util.stringExists(taxonomyString)) return null;
   667	        IAJsonProperties iac = IAJsonProperties.iaConfig();
   668	        if (iac == null) return null;
   669	        Taxonomy taxy = shep.getOrCreateTaxonomy(taxonomyString, false);
   670	        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
   671	        if (configs == null || configs.length() == 0) return null;
   672	        JSONObject mlConfig = configs.optJSONObject(0);
   673	        if (mlConfig == null) return null;
   674	        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
   675	        return new ConfigPair(mlConfig, matchConfig);
   676	    }
   677	
   678	    private JSONObject defaultMatchConfig(IAJsonProperties iac, Taxonomy taxy,
   679	        JSONObject mlConfig) {
   680	        JSONObject matchConfig = null;
   681	        JSONArray identConfigs = iac.getIdentConfig(taxy);
   682	        if (identConfigs != null) {
   683	            for (int i = 0; i < identConfigs.length(); i++) {
   684	                JSONObject entry = identConfigs.optJSONObject(i);
   685	                if (entry == null) continue;
   686	                if (entry.optBoolean("default", false)
   687	                    && "vector".equals(entry.optString("pipeline_root", null))) {
   688	                    matchConfig = new JSONObject(entry.toString());
   689	                    break;
   690	                }
   691	            }
   692	        }
   693	        if (matchConfig == null) matchConfig = new JSONObject();
   694	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
   695	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   696	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
   697	        }
   698	        return matchConfig;
   699	    }
   700	
   701	    private JSONObject inferMatchConfig(List<String> annotationIds) {
   702	        if (annotationIds == null || annotationIds.isEmpty()) return null;
   703	        Shepherd shep = new Shepherd(context);
   704	        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
   705	        try {
   706	            shep.beginDBTransaction();
   707	            for (String annId : annotationIds) {
   708	                Annotation ann = shep.getAnnotation(annId);
   709	                if (ann == null) continue;
   710	                Embedding emb = ann.getAnEmbedding();
   711	                if (emb != null) {
   712	                    JSONObject config = new JSONObject();
   713	                    config.put("method", emb.getMethod());
   714	                    config.put("version", emb.getMethodVersion());
   715	                    config.put("pipeline_root", "vector");
   716	                    return config;
   717	                }
   718	            }
   719	            return null;
   720	        } finally {
   721	            shep.rollbackAndClose();
   722	        }
   723	    }
   724	
   725	    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
   726	        JSONObject mlConfig) {
   727	        JSONObject config = (matchConfig == null) ? new JSONObject()
   728	            : new JSONObject(matchConfig.toString());
   729	        if (embeddingSource != null) {
   730	            if (!Util.stringExists(config.optString("method", null))
   731	                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
   732	                config.put("method", embeddingSource.optString("embedding_model_id"));
   733	            }
   734	            if (!Util.stringExists(config.optString("version", null))
   735	                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
   736	                config.put("version", embeddingSource.optString("embedding_model_version"));
   737	            }
   738	        }
   739	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
   740	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   741	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
   742	        }
   743	        return config;
   744	    }
   745	
   746	    private String detectionStaleReason(MediaAsset ma, Encounter enc, String encounterId) {
   747	        if (ma == null) return "media asset missing";
   748	        if (Util.stringExists(encounterId) && enc == null) return "encounter missing";
   749	        String status = ma.getDetectionStatus();
   750	        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
   751	        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
   752	        return null;
   753	    }
   754	
   755	    private String effectiveTaxonomyString(String taxonomyString, Encounter enc) {
   756	        if (Util.stringExists(taxonomyString)) return taxonomyString;
   757	        if (enc != null) return enc.getTaxonomyString();
   758	        return null;
   759	    }
   760	

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/IAJsonProperties.java | sed -n '240,330p'" in /mnt/c/Wildbook-clean2
 succeeded in 248ms:
   240	        return identKey(taxy, "_default");
   241	    }
   242	
   243	    public static String identKey(Taxonomy taxy, String iaClass) {
   244	        return taxonomyKey(taxy) + "." + iaClass;
   245	    }
   246	
   247	    public static String identConfigKey(Taxonomy taxy) {
   248	        return identConfigKey(taxy, "_default");
   249	    }
   250	
   251	    public static String identConfigKey(Taxonomy taxy, String iaClass) {
   252	        return identKey(taxy, iaClass) + "._id_conf";
   253	    }
   254	
   255	    public JSONArray getIdentConfig(Taxonomy taxy) {
   256	        return getIdentConfig(taxy, "_default");
   257	    }
   258	
   259	    public JSONArray getIdentConfig(Taxonomy taxy, String iaClass) {
   260	        String configKey = identConfigKey(taxy, iaClass);
   261	        JSONArray config = (JSONArray)this.get(configKey);
   262	
   263	        if (config == null) {
   264	            System.out.println("IAJsonProperties: could not find ident config for taxonomy " +
   265	                taxy.toString() + " and iaClass " + iaClass + ". Trying _default iaClass instead.");
   266	            config = (JSONArray)this.get(identConfigKey(taxy, "_default"));
   267	        }
   268	        // The implicit `_mlservice_conf` appending that used to live here was
   269	        // removed in commit #2 of the ml-service migration v2 plan. The new
   270	        // contract: `_id_conf.default.pipeline_root` is the single decision
   271	        // point for vector vs HotSpotter, and ml-service config is reached
   272	        // via `getActiveMlServiceConfigs(...)` only — never implicitly mixed
   273	        // into ident configs returned by this method.
   274	        if (config == null)
   275	            System.out.println(
   276	                "IAJsonProperties WARNING: could not find any identConfig for taxonomy " +
   277	                taxy.getScientificName() + ". Tried configKey=" + configKey + " Returning null.");
   278	        return config;
   279	    }
   280	
   281	    // ------------------------------------------------------------------
   282	    // ml-service migration v2: routing-aware accessors. The strict
   283	    // invariant: a species routes to ml-service iff its
   284	    // `_id_conf.default.pipeline_root == "vector"` AND its
   285	    // `_mlservice_conf` array is populated. Both conditions are enforced
   286	    // by `getActiveMlServiceConfigs`. A species with `_mlservice_conf` but
   287	    // pipeline_root="HotSpotter" does NOT route to ml-service.
   288	    // ------------------------------------------------------------------
   289	
   290	    public static String mlServiceConfigKey(Taxonomy taxy) {
   291	        return taxonomyKey(taxy) + "._mlservice_conf";
   292	    }
   293	
   294	    /**
   295	     * Returns the per-taxonomy `_mlservice_conf` JSONArray iff the species'
   296	     * `_id_conf.default.pipeline_root == "vector"` AND the array is
   297	     * populated. Returns null otherwise. Callers don't need to second-guess
   298	     * the routing decision — null means "do not route to ml-service".
   299	     */
   300	    public JSONArray getActiveMlServiceConfigs(Taxonomy taxy) {
   301	        if (taxy == null) return null;
   302	        String pipelineRoot = getPipelineRoot(taxy);
   303	        if (!"vector".equals(pipelineRoot)) return null;
   304	        Object raw = this.get(mlServiceConfigKey(taxy));
   305	        if (!(raw instanceof JSONArray)) return null;
   306	        JSONArray arr = (JSONArray) raw;
   307	        if (arr.length() == 0) return null;
   308	        return arr;
   309	    }
   310	
   311	    /**
   312	     * Returns the `pipeline_root` string of the `_id_conf` entry marked
   313	     * `default: true` for this taxonomy (under `_default` iaClass). Returns
   314	     * null if no entry is marked default, no `pipeline_root` is set on the
   315	     * default entry, or no `_id_conf` exists at all.
   316	     */
   317	    public String getPipelineRoot(Taxonomy taxy) {
   318	        return getPipelineRoot(taxy, "_default");
   319	    }
   320	
   321	    public String getPipelineRoot(Taxonomy taxy, String iaClass) {
   322	        if (taxy == null) return null;
   323	        JSONArray idConf = getIdentConfig(taxy, iaClass);
   324	        if (idConf == null) return null;
   325	        for (int i = 0; i < idConf.length(); i++) {
   326	            JSONObject entry = idConf.optJSONObject(i);
   327	            if (entry == null) continue;
   328	            if (entry.optBoolean("default", false)) {
   329	                return entry.optString("pipeline_root", null);
   330	            }

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceClient.java | sed -n '1,120p' && nl -ba src/main/java/org/ecocean/ia/MlServiceClient.java | sed -n '220,270p'" in /mnt/c/Wildbook-clean2
 succeeded in 295ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.IOException;
     4	import java.net.MalformedURLException;
     5	import java.net.SocketTimeoutException;
     6	import java.net.URL;
     7	import java.util.regex.Matcher;
     8	import java.util.regex.Pattern;
     9	
    10	import org.json.JSONArray;
    11	import org.json.JSONObject;
    12	
    13	import org.ecocean.RestClient;
    14	import org.ecocean.Util;
    15	
    16	/**
    17	 * HTTP-only wrapper around ml-service ({@code /pipeline/} and {@code /extract/}
    18	 * endpoints). Validates the response shape against the v2 contract. No
    19	 * Shepherd, no DB; just HTTP + JSON validation.
    20	 *
    21	 * <p>Migration plan v2 §commit #8. Used by {@link
    22	 * org.ecocean.ia.MlServiceProcessor} (commit #9). Tests directly via
    23	 * {@code MlServiceClientTest}.</p>
    24	 *
    25	 * <h3>Retry classification (matches v2 plan §Failure ladder):</h3>
    26	 * <ul>
    27	 *   <li>{@link SocketTimeoutException} or message contains "timed out" →
    28	 *       IAException retryable=true, increment=false (timeout doesn't imply
    29	 *       overload).</li>
    30	 *   <li>Connection refused / 502 / 503 / 504 / 5xx → retryable=true,
    31	 *       increment=true.</li>
    32	 *   <li>429 (rate-limited) → retryable=true, increment=true so the client
    33	 *       backs off.</li>
    34	 *   <li>Other 4xx, parse failure, {@code success=false} response → retryable
    35	 *       =false; mark task error.</li>
    36	 * </ul>
    37	 *
    38	 * <p>RestClient throws {@code "HTTP error code = NNN"} (literally with {@code
    39	 * =}). The classifier accepts both {@code "= NNN"} and {@code ": NNN"}
    40	 * spellings to be defensive against any future RestClient refactor.</p>
    41	 */
    42	public class MlServiceClient {
    43	
    44	    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;
    45	    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
    46	
    47	    // Matches "HTTP error code = 502" or "HTTP error code : 502", capturing
    48	    // the status code as group 1.
    49	    private static final Pattern HTTP_CODE_PATTERN =
    50	        Pattern.compile("HTTP error code\\s*[=:]\\s*(\\d{3})");
    51	
    52	    private final int connectTimeoutMs;
    53	    private final int readTimeoutMs;
    54	
    55	    public MlServiceClient() {
    56	        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    57	    }
    58	
    59	    public MlServiceClient(int connectTimeoutMs, int readTimeoutMs) {
    60	        this.connectTimeoutMs = connectTimeoutMs;
    61	        this.readTimeoutMs = readTimeoutMs;
    62	    }
    63	
    64	    // ---------------------------------------------------------------------
    65	    // Public API
    66	    // ---------------------------------------------------------------------
    67	
    68	    /**
    69	     * POSTs to {@code apiEndpoint/pipeline/} with the predict/classify/extract/
    70	     * orientation model IDs from {@code config}. Returns the validated response.
    71	     *
    72	     * @param apiEndpoint base URL of ml-service (no trailing slash required)
    73	     * @param imageUri    URL or local path of the image to process
    74	     * @param config      a single {@code _mlservice_conf} entry from IA.json
    75	     * @return validated response JSON ({@code success:true, results:[...]})
    76	     * @throws IAException on network failure or response-validation failure;
    77	     *         {@code shouldRequeue()} and {@code getCode()} carry the
    78	     *         classification. Codes: {@code TIMEOUT}, {@code NETWORK},
    79	     *         {@code SERVER_ERROR}, {@code RATE_LIMITED},
    80	     *         {@code CLIENT_ERROR}, {@code SUCCESS_FALSE}, {@code INVALID}.
    81	     */
    82	    public JSONObject pipeline(String apiEndpoint, String imageUri, JSONObject config)
    83	    throws IAException {
    84	        JSONObject payload = buildPipelinePayload(imageUri, config);
    85	        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/pipeline/"),
    86	            payload);
    87	        validatePipelineResponse(response, config.optInt("embedding_dimension", 0));
    88	        return response;
    89	    }
    90	
    91	    /**
    92	     * POSTs to {@code apiEndpoint/extract/}. Used for manual annotations
    93	     * (user-drawn bbox; no detection step needed).
    94	     *
    95	     * @throws IAException same contract as {@link #pipeline}.
    96	     */
    97	    public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
    98	        double theta, JSONObject config)
    99	    throws IAException {
   100	        JSONObject payload = buildExtractPayload(imageUri, bbox, theta, config);
   101	        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/extract/"),
   102	            payload);
   103	        validateExtractResponse(response, config.optInt("embedding_dimension", 0));
   104	        return response;
   105	    }
   106	
   107	    // ---------------------------------------------------------------------
   108	    // Internal helpers (package-visible for unit tests)
   109	    // ---------------------------------------------------------------------
   110	
   111	    static String joinEndpoint(String base, String path) {
   112	        if (base == null) return path;
   113	        String trimmed = base.replaceAll("/+$", "");
   114	        return trimmed + path;
   115	    }
   116	
   117	    static JSONObject buildPipelinePayload(String imageUri, JSONObject config) {
   118	        JSONObject p = new JSONObject();
   119	        p.put("image_uri", imageUri);
   120	        if (config != null) {
   220	        if (emb.length() == 0)
   221	            throw new IAException("INVALID",
   222	                context + " embedding array is empty", false, false);
   223	        for (int j = 0; j < emb.length(); j++) {
   224	            double v = emb.optDouble(j, Double.NaN);
   225	            if (!isFiniteDouble(v))
   226	                throw new IAException("INVALID",
   227	                    context + " embedding[" + j + "] non-finite", false, false);
   228	        }
   229	        String modelId = parent.optString("embedding_model_id", null);
   230	        String modelVer = parent.optString("embedding_model_version", null);
   231	        if (!Util.stringExists(modelId) || !Util.stringExists(modelVer))
   232	            throw new IAException("INVALID",
   233	                context + " missing embedding_model_id or embedding_model_version",
   234	                false, false);
   235	    }
   236	
   237	    private static boolean isFiniteDouble(double v) {
   238	        return !Double.isNaN(v) && !Double.isInfinite(v);
   239	    }
   240	
   241	    private JSONObject postWithClassification(String url, JSONObject payload)
   242	    throws IAException {
   243	        URL u;
   244	        try {
   245	            u = new URL(url);
   246	        } catch (MalformedURLException ex) {
   247	            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
   248	        }
   249	        try {
   250	            return RestClient.postJSON(u, payload, null, connectTimeoutMs, readTimeoutMs);
   251	        } catch (Exception ex) {
   252	            throw classifyHttpFailure(ex, url);
   253	        }
   254	    }
   255	
   256	    /** Classify a RestClient throw into the v2 failure-ladder buckets. */
   257	    static IAException classifyHttpFailure(Exception ex, String url) {
   258	        // Detect timeout primarily by exception type; fall back to message
   259	        // sniffing for environments where the cause chain is flattened.
   260	        for (Throwable t = ex; t != null; t = t.getCause()) {
   261	            if (t instanceof SocketTimeoutException) {
   262	                return new IAException("TIMEOUT",
   263	                    "ml-service timeout on " + url + ": " + ex.getMessage(), true, false);
   264	            }
   265	        }
   266	        String msg = ex.getMessage() == null ? "" : ex.getMessage();
   267	        if (msg.contains("timed out")) {
   268	            return new IAException("TIMEOUT",
   269	                "ml-service timeout on " + url + ": " + msg, true, false);
   270	        }

exec
/bin/bash -lc "rg -n \"postJSON\\(\" src/main/java/org/ecocean/RestClient.java && nl -ba src/main/java/org/ecocean/RestClient.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
 succeeded in 310ms:
383:    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers)
385:        return postJSON(url, data, headers, CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
389:    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers,
     1	package org.ecocean;
     2	/*  NOTE: due to the authentication header stuff, this is effectively IBEIS-specific but starting to break this out into generic. Still a work-in-progress.... see methods at the end of the file which will hopefully allow control over auth stuff better
     3	 */
     4	
     5	import java.io.BufferedReader;
     6	import java.io.File;
     7	import java.io.FileOutputStream;
     8	import java.io.InputStream;
     9	import java.io.InputStreamReader;
    10	import java.io.IOException;
    11	import java.io.OutputStream;
    12	import java.io.UnsupportedEncodingException;
    13	import java.net.HttpURLConnection;
    14	import java.net.MalformedURLException;
    15	import java.net.URL;
    16	import java.net.URLEncoder;
    17	import java.util.HashMap;
    18	import java.util.Iterator;
    19	import java.util.Map;
    20	import org.json.JSONObject;
    21	
    22	import java.security.InvalidKeyException;
    23	import java.security.NoSuchAlgorithmException;
    24	import javax.crypto.Mac;
    25	import javax.crypto.spec.SecretKeySpec;
    26	import org.apache.commons.codec.binary.Base64;
    27	
    28	/*
    29	   javax.ws.rs.core.UriBuilder https://stackoverflow.com/a/29053050/1525311
    30	 */
    31	
    32	public class RestClient {
    33	    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    34	    private static final int CONNECTION_TIMEOUT = 300000; // maybe this should be service-specific?
    35	
    36	    public static JSONObject post(URL url, JSONObject data)
    37	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    38	        InvalidKeyException {
    39	        return anyMethod("POST", url, data);
    40	    }
    41	
    42	    public static JSONObject put(URL url, JSONObject data)
    43	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    44	        InvalidKeyException {
    45	        return anyMethod("PUT", url, data);
    46	    }
    47	
    48	    public static JSONObject get(URL url)
    49	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    50	        InvalidKeyException {
    51	        return anyMethod("GET", url, null);
    52	    }
    53	
    54	    public static JSONObject get(URL url, int timeout)
    55	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    56	        InvalidKeyException {
    57	        return anyMethod("GET", url, null, timeout);
    58	    }
    59	
    60	    public static JSONObject get(URL url, JSONObject data)
    61	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    62	        InvalidKeyException {
    63	        return anyMethod("GET", url, data);
    64	    }
    65	
    66	    // IBEIS-specifically, data gets posted as name-value pairs where name comes from the keys
    67	    private static JSONObject anyMethod(String method, URL url, JSONObject data)
    68	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    69	        InvalidKeyException {
    70	        return anyMethod(method, url, data, CONNECTION_TIMEOUT);
    71	    }
    72	
    73	    private static JSONObject anyMethod(String method, URL url, JSONObject data, int ctimeout)
    74	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    75	        InvalidKeyException {
    76	        System.out.println("TRYING anyMethod(" + method + ") url -> " + url);
    77	        // System.setProperty("http.keepAlive", "false");
    78	        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    79	        conn.setReadTimeout(ctimeout);
    80	        conn.setConnectTimeout(ctimeout);
    81	        conn.setDoOutput((data != null));
    82	        conn.setDoInput(true);
    83	        conn.setRequestMethod(method);
    84	        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    85	        conn.setRequestProperty("Authorization", getAuthorizationHeader(url.toString()));
    86	        if (data != null) {
    87	            OutputStream os = conn.getOutputStream();
    88	            os.write(getPostDataString(data).getBytes());
    89	            os.flush();
    90	            os.close();
    91	        }
    92	        conn.connect();
    93	
    94	        boolean success = true;
    95	        // the 600 response here is IBEIS-specific, so we need to genericize this
    96	        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK) &&
    97	            (conn.getResponseCode() != 600)) {
    98	            // conn.disconnnect();
    99	            System.out.println("!!!!!!!!!!!!!!!!!!! [url = " + url.toString() +
   100	                "] bad response code = " + conn.getResponseCode());
   101	            success = false;
   102	        }
   103	        BufferedReader br = null;
   104	
   105	        try {
   106	            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
   107	        } catch (IOException ioe) {
   108	            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
   109	        }
   110	        String output;
   111	        String jtext = "";
   112	        while ((output = br.readLine()) != null) {
   113	            jtext += output;
   114	        }
   115	        br.close();
   116	        // conn.disconnect();
   117	        if (!success) {
   118	            System.out.println("========= anyMethod failed with code=" + conn.getResponseCode() +
   119	                "\n" + jtext + "\n============");
   120	            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
   121	        }
   122	        if (jtext.equals("")) return null;
   123	        return new JSONObject(jtext);
   124	
   125	    }
   126	
   127	    public static JSONObject postStream(URL url, InputStream in)
   128	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   129	        InvalidKeyException {
   130	        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
   131	
   132	        conn.setReadTimeout(CONNECTION_TIMEOUT);
   133	        conn.setConnectTimeout(CONNECTION_TIMEOUT);
   134	        conn.setDoOutput(true);
   135	        conn.setDoInput(true);
   136	        conn.setRequestMethod("POST");
   137	        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
   138	        OutputStream os = conn.getOutputStream();
   139	        byte[] buffer = new byte[10240];
   140	        int len;
   141	// System.out.println("OK, begin<");
   142	        while ((len = in.read(buffer)) != -1) {
   143	            os.write(buffer, 0, len);
   144	// System.out.write(buffer, 0, len);
   145	        }
   146	        in.close();
   147	        os.flush();
   148	        os.close();
   149	        conn.connect();
   150	
   151	        boolean success = true;
   152	        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK)) {
   153	            System.out.println("!!!!!!!!!!!!!!!!!!! bad response code = " + conn.getResponseCode());
   154	            success = false;
   155	        }
   156	        if (!success) {
   157	            JSONObject rtn = new JSONObject();
   158	            rtn.put("error", conn.getResponseCode());
   159	            return rtn;
   160	        }
   161	
   162	        BufferedReader br = null;
   163	
   164	        try {
   165	            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
   166	        } catch (IOException ioe) {
   167	            success = false;
   168	            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
   169	        }
   170	        String output;
   171	        String jtext = "";
   172	        while ((output = br.readLine()) != null) {
   173	            jtext += output;
   174	        }
   175	        br.close();
   176	        
   177	        if (jtext.equals("")) return null;
   178	        System.out.println("======================== postStream -> " + jtext);
   179	        return new JSONObject(jtext);
   180	    }
   181	
   182	    // this chunk below is IBEIS-specific -- need to generalize for RestClient to be universal
   183	
   184	    private static String getSignature(String key, byte[] messageToSendBytes)
   185	    throws NoSuchAlgorithmException, InvalidKeyException {
   186	        SecretKeySpec keyHmac = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
   187	        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
   188	
   189	        mac.init(keyHmac);
   190	        return new String(Base64.encodeBase64(mac.doFinal(messageToSendBytes)));
   191	    }
   192	
   193	    public static String getAuthorizationHeader(String url)
   194	    throws NoSuchAlgorithmException, InvalidKeyException {
   195	        String appName = "IBEIS";
   196	        String appSecret = "CB73808F-A6F6-094B-5FCD-385EBAFF8FC0";
   197	
   198	        return appName + ":" + getSignature(appSecret, url.getBytes());
   199	    }
   200	
   201	    // end IBEIS-specific
   202	
   203	    private static String getPostDataString(JSONObject obj) {
   204	        StringBuilder result = new StringBuilder();
   205	        boolean first = true;
   206	        Iterator<?> keys = obj.keys();
   207	
   208	        while (keys.hasNext()) {
   209	            String key = (String)keys.next();
   210	            if (first) {
   211	                first = false;
   212	            } else {
   213	                result.append("&");
   214	            }
   215	            try {
   216	                result.append(URLEncoder.encode(key, "UTF-8"));
   217	                result.append("=");
   218	                result.append(URLEncoder.encode(obj.get(key).toString(), "UTF-8"));
   219	            } catch (UnsupportedEncodingException uee) {
   220	                System.out.println("caught exception on key " + key + ": " + uee.toString());

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/RestClient.java | sed -n '360,430p'" in /mnt/c/Wildbook-clean2
 succeeded in 283ms:
   360	        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) success = false;
   361	        BufferedReader br = null;
   362	        try {
   363	            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
   364	        } catch (IOException ioe) {
   365	            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
   366	        }
   367	        String output;
   368	        String jtext = "";
   369	        while ((output = br.readLine()) != null) {
   370	            jtext += output;
   371	        }
   372	        br.close();
   373	        if (!success) {
   374	            System.out.println("WARNING: postRaw() on " + url + " failed with code=" +
   375	                conn.getResponseCode() + "\n" + jtext + "\n============");
   376	            throw new IOException("HTTP error code = " + conn.getResponseCode());
   377	        }
   378	        if (jtext.equals("")) return null;
   379	        return jtext;
   380	    }
   381	
   382	    // JSON-friendly generic  (can pass null for headers and it will get set)
   383	    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers)
   384	    throws IOException, java.net.ProtocolException {
   385	        return postJSON(url, data, headers, CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
   386	    }
   387	
   388	    // ml-service migration v2 (commit #6): timeout-aware overload.
   389	    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers,
   390	        int connectTimeoutMs, int readTimeoutMs)
   391	    throws IOException, java.net.ProtocolException {
   392	        if (headers == null) headers = new HashMap<String, String>();
   393	        if (headers.get("Content-type") == null) headers.put("Content-type", "application/json");
   394	        String rtn = postRaw(url, (data == null) ? (String)null : data.toString(), headers,
   395	            connectTimeoutMs, readTimeoutMs);
   396	        JSONObject jrtn = Util.stringToJSONObject(rtn);
   397	        if (jrtn == null)
   398	            throw new IOException("could not convert postRaw() to JSONObject: " + rtn);
   399	        return jrtn;
   400	    }
   401	}

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/AssetStore.java | sed -n '450,490p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/LocalAssetStore.java | sed -n '260,330p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '1460,1510p'" in /mnt/c/Wildbook-clean2
 succeeded in 245ms:
   260	            return null;
   261	        }
   262	    }
   263	
   264	    @Override
   265	/*
   266	    NOTE: the iaSourcePath is a bit of a mungy hack specific for Lewa -- going forward the filename should be *created* based upon the IA original
   267	       filename (thus .getFilename would just work as expected) ... but for now we rely on favoring this extra logic.  thus, this *probably*
   268	    should not go outside of the lewa branch.  :(
   269	 */
   270	    public String getFilename(MediaAsset ma) {
   271	        if ((ma.getParameters() != null) && (ma.getParameters().optString("iaSourcePath",
   272	            null) != null)) {
   273	            File tmp = new File(ma.getParameters().getString("iaSourcePath"));
   274	            return tmp.getName();
   275	        }
   276	        Path path = pathFromParameters(ma.getParameters());
   277	        if (path == null) return null;
   278	        Path fn = path.getFileName();
   279	        if (fn == null) return null; // will this ever happen?
   280	        return fn.toString();
   281	    }
   282	
   283	    @Override public String getUserFilename(MediaAsset ma) {
   284	        if ((ma.getParameters() != null) && (ma.getParameters().optString("userFilename",
   285	            null) != null)) return ma.getParameters().getString("userFilename");
   286	        return this.getFilename(ma);
   287	    }
   288	
   289	    @Override public String hashCode(JSONObject params) {
   290	        if (params == null) return null;
   291	        Path path = pathFromParameters(params);
   292	        if (path == null) return null;
   293	        String abs = path.toAbsolutePath().toString();
   294	        return abs.substring(0, 10) + LocalAssetStore.hexStringSHA256(abs);
   295	    }
   296	
   297	    @Override public JSONObject createParameters(File file, String grouping) {
   298	        JSONObject p = new JSONObject();
   299	
   300	        if (file == null) return p;
   301	        if (underRoot(file.toPath())) { // note: we ignore grouping here!
   302	            p.put("path", file.toPath());
   303	        } else { // must be just some file "elsewhere", so we store it in some unique dir
   304	            if (grouping == null)
   305	                grouping = Util.hashDirectories(Util.generateUUID(), File.separator);
   306	            p.put("path",
   307	                root().toString() + File.separator + grouping + File.separator + file.getName());
   308	        }
   309	// System.out.println("NOTE: LocalAssetStore.createParameters(" + file + ") -> " + p.toString());
   310	        return p;
   311	    }
   312	}

 succeeded in 249ms:
   450	        if (toMA == null) throw new IOException("copyAssetAny(): toMA is null");
   451	        if (fromMA.getStore() == null)
   452	            throw new IOException("copyAssetAny(): fromMA store is null");
   453	        if (toMA.getStore() == null) throw new IOException("copyAssetAny(): toMA store is null");
   454	        if (fromMA.getStore().typeEquals(toMA.getStore())) {
   455	            fromMA.getStore().copyAsset(fromMA, toMA);
   456	        } else {
   457	            copyAssetAcross(fromMA, toMA);
   458	        }
   459	    }
   460	
   461	    // this is within the same flavor of AssetStore, so is handled by the subclass
   462	    public abstract void copyAsset(final MediaAsset fromMA, final MediaAsset toMA)
   463	    throws IOException;
   464	
   465	    // to copy across flavors of AssetStore
   466	    private void copyAssetAcross(final MediaAsset fromMA, final MediaAsset toMA)
   467	    throws IOException {
   468	        throw new IOException("copyAssetAcross() not yet implemented!  :/");
   469	    }
   470	
   471	    /**
   472	     *  should create the ("base") set of parameters for the specific store-type based on file.
   473	     *  note this can take into account store-specific config settings (optional) "grouping" acts sort of like a common subdir to
   474	     * put it under (**if** available for that store!) can (should?) just return null for read-only stores?
   475	     */
   476	    public abstract JSONObject createParameters(final File file, final String grouping);
   477	
   478	    public JSONObject createParameters(final File file) {
   479	        return createParameters(file, null);
   480	    }
   481	
   482	    public abstract void deleteFrom(final MediaAsset ma);
   483	
   484	    // right now default is determined by (a) a .usage value of "default"; or (b) if that does not exist, the first AssetStore by order
   485	    public static AssetStore getDefault(Shepherd myShepherd) {
   486	        init(AssetStoreFactory.getStores(myShepherd));
   487	        if ((stores == null) || (stores.size() < 1)) {
   488	            System.out.println(
   489	                "WARNING: AssetStore.getDefault() can find no AssetStores. This is likely bad! Please create one.");
   490	            return null; // i have a good mind to throw an exception here...

 succeeded in 285ms:
  1460	
  1461	    public JSONObject getIAStatus() {
  1462	        JSONObject rtn = new JSONObject();
  1463	        JSONObject j = new JSONObject();
  1464	
  1465	        j.put("status", getDetectionStatus());
  1466	        rtn.put("detection", j);
  1467	
  1468	        j = new JSONObject();
  1469	        j.put("status", getIdentificationStatus());
  1470	        rtn.put("identification", j);
  1471	        return rtn;
  1472	    }
  1473	
  1474	    // takes base64 string and turns to binary content and copies that in as normal
  1475	    public void copyInBase64(String b64)
  1476	    throws IOException {
  1477	        if (b64 == null) throw new IOException("copyInBase64() null string");
  1478	        byte[] imgBytes = new byte[100];
  1479	        try {
  1480	            imgBytes = Base64.getDecoder().decode(b64);
  1481	        } catch (IllegalArgumentException ex) {
  1482	            throw new IOException("copyInBase64() could not parse: " + ex.toString());
  1483	        }
  1484	        File file = (this.localPath() !=
  1485	            null) ? this.localPath().toFile() : File.createTempFile("b64-" + Util.generateUUID(),
  1486	            ".tmp");
  1487	        File parentDir = file.getParentFile();
  1488	        if (!parentDir.exists()) parentDir.mkdirs();
  1489	        FileOutputStream stream = new FileOutputStream(file);
  1490	        try {
  1491	            stream.write(imgBytes);
  1492	        } catch (Exception e) {
  1493	            System.out.println("Exception from Writing FileOutputStream with imgBytes");
  1494	            e.printStackTrace();
  1495	        } finally {
  1496	            stream.close();
  1497	        }
  1498	        if (file.exists()) {
  1499	            this.copyIn(file);
  1500	        } else {
  1501	            throw new IOException("copyInBase64() could not write " + file);
  1502	        }
  1503	    }
  1504	
  1505	    public boolean isValidChildType(String type) {
  1506	        if (store == null) return false;
  1507	        return store.isValidChildType(type);
  1508	    }
  1509	
  1510	    public List<Task> getRootIATasks(Shepherd myShepherd) { // convenience

exec
/bin/bash -lc "rg -n \"void copyIn\\(|copyIn\\(\" src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/AssetStore.java src/main/java/org/ecocean/media/LocalAssetStore.java" in /mnt/c/Wildbook-clean2
 succeeded in 316ms:
src/main/java/org/ecocean/media/LocalAssetStore.java:107:        Path subpath = pathFromParameters(params, false); // file can not exist (for sake of subsequent copyIn() being called) ... sorry?
src/main/java/org/ecocean/media/LocalAssetStore.java:161:    @Override public MediaAsset copyIn(final File file, final JSONObject params,
src/main/java/org/ecocean/media/AssetStore.java:300:        MediaAsset ma = this.copyIn(targetFile, sp);
src/main/java/org/ecocean/media/AssetStore.java:402:            t.join(); // we have to wait for it to finish, so we can do the copyIn() below
src/main/java/org/ecocean/media/AssetStore.java:420:    public MediaAsset copyIn(final File file, final JSONObject params)
src/main/java/org/ecocean/media/AssetStore.java:422:        return copyIn(file, params, true);
src/main/java/org/ecocean/media/AssetStore.java:432:    public void copyIn(final File file, final MediaAsset ma)
src/main/java/org/ecocean/media/AssetStore.java:434:        copyIn(file, ma.getParameters(), false);
src/main/java/org/ecocean/media/AssetStore.java:437:    protected abstract MediaAsset copyIn(final File file, final JSONObject params,
src/main/java/org/ecocean/media/MediaAsset.java:1020:    public void copyIn(File file)
src/main/java/org/ecocean/media/MediaAsset.java:1022:        if (store == null) throw new IOException("copyIn(): store is null on " + this);
src/main/java/org/ecocean/media/MediaAsset.java:1023:        store.copyIn(file, getParameters(), false);
src/main/java/org/ecocean/media/MediaAsset.java:1499:            this.copyIn(file);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/AssetStore.java | sed -n '286,442p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/LocalAssetStore.java | sed -n '90,190p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '1000,1030p'" in /mnt/c/Wildbook-clean2
 succeeded in 209ms:
   286	        try {
   287	            parent.cacheLocal();
   288	        } catch (Exception ex) {
   289	            throw new IOException("updateChild() error caching local file: " + ex.toString());
   290	        }
   291	        if (parent.localPath() == null)
   292	            throw new IOException("updateChild() found null localPath() on parent");
   293	        File sourceFile = parent.localPath().toFile();
   294	        File targetFile = new File(sourceFile.getParent().toString() + File.separator +
   295	            Util.generateUUID() + "-" + type + ".jpg");
   296	        boolean allowed = _updateChildLocalWork(parent, type, opts, sourceFile, targetFile,
   297	            skipCropping);                                                                                  // does the heavy lifting
   298	        if (!allowed) return null; // usually means read-only (big trouble throws exception, including targetFile not existing)
   299	        JSONObject sp = this.createParameters(targetFile);
   300	        MediaAsset ma = this.copyIn(targetFile, sp);
   301	        if (ma == null) return null; // not sure how this would happen *without* an exception, but meh.
   302	        ma.addLabel("_" + type);
   303	        ma.setParentId(parent.getIdInt());
   304	        if ((opts != null) && (opts.get("feature") != null)) {
   305	            Feature ft = (Feature)opts.get("feature");
   306	            ma.addDerivationMethod("feature", ft.getId());
   307	        }
   308	        return ma;
   309	    }
   310	
   311	    protected boolean _updateChildLocalWork(MediaAsset parentMA, String type,
   312	        HashMap<String, Object> opts, File sourceFile, File targetFile)
   313	    throws IOException {
   314	        return _updateChildLocalWork(parentMA, type, opts, sourceFile, targetFile, false); // by default do not skip cropping
   315	    }
   316	
   317	    // a helper/utility app for the above (if applicable) that works on localfiles (since many flavors will want that)
   318	    protected boolean _updateChildLocalWork(MediaAsset parentMA, String type,
   319	        HashMap<String, Object> opts, File sourceFile, File targetFile, boolean skipCropping)
   320	    throws IOException {
   321	        if (!this.writable) return false; // should we silently fail or throw exception??
   322	        if (!sourceFile.exists())
   323	            throw new IOException("updateChild() " + sourceFile.toString() + " does not exist");
   324	        String action = "resize";
   325	        int width = 0;
   326	        int height = 0;
   327	        float[] transformArray = new float[0];
   328	        boolean needsTransform = false;
   329	        String args = null; // i think the only real arg would be watermark text (which is largely unused)
   330	        switch (type) {
   331	        case "master":
   332	            action = "maintainAspectRatio";
   333	            width = 4096;
   334	            height = 4096;
   335	            break;
   336	        case "thumb":
   337	            if (!skipCropping) action = "maintainAspectRatio";
   338	            width = 100;
   339	            height = 75;
   340	            break;
   341	        case "mid":
   342	            if (!skipCropping) action = "maintainAspectRatio";
   343	            width = 1024;
   344	            height = 768;
   345	            break;
   346	        case "watermark":
   347	            if (!skipCropping) action = "maintainAspectRatio";
   348	            action = "watermark";
   349	            width = 250;
   350	            height = 200;
   351	            break;
   352	        case "feature":
   353	            needsTransform = true;
   354	            Feature ft = (Feature)opts.get("feature");
   355	            if (ft == null)
   356	                throw new IOException(
   357	                          "updateChild() has 'feature' type without a Feature passed in via opts");
   358	            // right now we only handle bbox (xywh) and transforms
   359	            JSONObject params = ft.getParameters();
   360	            System.out.println("updateChild() is trying feature! --> params = " + params);
   361	            width = (int)Math.round(params.optDouble("width", -1));
   362	            height = (int)Math.round(params.optDouble("height", -1));
   363	            if ((width < 0) || (height < 0))
   364	                throw new IOException("updateChild() could not get w/h for feature " + ft +
   365	                        " parameters " + params);
   366	            transformArray = new float[] { 1, 0, 0, 1, 0, 0 };
   367	            if (params.optJSONArray("transformMatrix") != null) {
   368	                JSONArray tarr = params.optJSONArray("transformMatrix");
   369	                for (int i = 0; i < tarr.length(); i++) {
   370	                    if (i > 5) break; // fail!
   371	                    transformArray[i] = (float)tarr.optDouble(i, 0);
   372	                }
   373	            }
   374	            if (Util.isIdentityMatrix(transformArray)) {
   375	                // lets set offsets only (ImageMagick shell script will basically ignore most of matrix)
   376	                transformArray[4] = (float)params.optDouble("x", 0);
   377	                transformArray[5] = (float)params.optDouble("y", 0);
   378	            }
   379	            System.out.println("got transformArray -> " + transformArray);
   380	            break;
   381	        default:
   382	            throw new IOException("updateChild() type " + type + " unknown");
   383	        }
   384	        System.out.println("AssetStore.updateChild(): " + sourceFile + " --> " + targetFile);
   385	
   386	        /* generally agreed that a *new* MediaAsset should be created for each change in the contents of the source file.
   387	        as such, finding an existing child MediaAsset of the type desired probably means it should either be deleted or orphaned ... or maybe simply marked older?
   388	        in short: "revisioning".  further, if the *parent has changed* should it also then not be a NEW MediaAsset itself anyway!? as such, we "should
   389	        never" be altering an existing child type on an existing parent. */
   390	
   391	        ImageProcessor iproc = null;
   392	        if (needsTransform) {
   393	            iproc = new ImageProcessor("context0", sourceFile.toString(), targetFile.toString(),
   394	                width, height, transformArray, parentMA);
   395	        } else {
   396	            iproc = new ImageProcessor("context0", action, width, height, sourceFile.toString(),
   397	                targetFile.toString(), args, parentMA);
   398	        }
   399	        Thread t = new Thread(iproc);
   400	        t.start();
   401	        try {
   402	            t.join(); // we have to wait for it to finish, so we can do the copyIn() below
   403	        } catch (InterruptedException ex) {
   404	            throw new IOException("updateChild() ImageProcessor failed due to interruption: " +
   405	                    ex.toString());
   406	        }
   407	        if (!targetFile.exists())
   408	            throw new IOException("updateChild() failed to create " + targetFile.toString());
   409	        return true;
   410	    }
   411	
   412	    /**
   413	     * Create a new asset from a File. The file is copied in to the store as part of this process.
   414	     *
   415	     * @param file File to copy in.
   416	     *
   417	     * @param params The (store-type-specific) JSONObject with settings on how to store the incoming file.
   418	     *
   419	     */
   420	    public MediaAsset copyIn(final File file, final JSONObject params)
   421	    throws IOException {
   422	        return copyIn(file, params, true);
   423	    }
   424	
   425	    /**
   426	     * Like above, but pass in MediaAsset and copy file to AssetStore accordingly (does the real dirty work for above).
   427	     *
   428	     * @param file File to copy in
   429	     *
   430	     * @param ma The MediaAsset
   431	     */
   432	    public void copyIn(final File file, final MediaAsset ma)
   433	    throws IOException {
   434	        copyIn(file, ma.getParameters(), false);
   435	    }
   436	
   437	    protected abstract MediaAsset copyIn(final File file, final JSONObject params,
   438	        final boolean createMediaAsset)
   439	    throws IOException;
   440	
   441	    /**
   442	     * does a store-specific copy of asset (contents) from one MediaAsset (location) to another note: these may be on different stores, so we handle

 succeeded in 217ms:
  1000	        if ((getMetadata() != null) && (getMetadata().getData() != null) &&
  1001	            (getMetadata().getData().opt("attributes") != null)) {
  1002	            j.put("attributes", getMetadata().getData().opt("attributes"));
  1003	        }
  1004	        return j;
  1005	    }
  1006	
  1007	    public String toString() {
  1008	        List<String> kwNames = getKeywordNames();
  1009	        String kwString = (kwNames == null) ? "None" : Util.joinStrings(kwNames);
  1010	
  1011	        return new ToStringBuilder(this)
  1012	                   .append("id", id)
  1013	                   .append("parent", parentId)
  1014	                   .append("labels", ((labels == null) ? "" : labels.toString()))
  1015	                   .append("store", store.toString())
  1016	                   .append("keywords", kwString)
  1017	                   .toString();
  1018	    }
  1019	
  1020	    public void copyIn(File file)
  1021	    throws IOException {
  1022	        if (store == null) throw new IOException("copyIn(): store is null on " + this);
  1023	        store.copyIn(file, getParameters(), false);
  1024	    }
  1025	
  1026	    public MediaAsset updateChild(String type, HashMap<String, Object> opts)
  1027	    throws IOException {
  1028	        if (store == null) throw new IOException("store is null on " + this);
  1029	        return store.updateChild(this, type, opts);
  1030	    }

 succeeded in 277ms:
    90	    public String webRoot() {
    91	        if (webRoot == null) {
    92	            webRoot = config.getString(KEY_WEB_ROOT);
    93	            log.debug("Asset Store [" + name + "] using web root [" + webRoot + "]");
    94	        }
    95	        return webRoot;
    96	    }
    97	
    98	    /**
    99	     * Create a new MediaAsset that points to an existing file under our root.
   100	     *
   101	     * @param path Relative or absolute path to a file.  Must be under the asset store root.
   102	     *
   103	     * @return The MediaAsset, or null if the path is invalid (not under the asset root or nonexistent).
   104	     */
   105	    @Override public MediaAsset create(final JSONObject params)
   106	    throws IllegalArgumentException {
   107	        Path subpath = pathFromParameters(params, false); // file can not exist (for sake of subsequent copyIn() being called) ... sorry?
   108	
   109	        if (subpath == null) return null;
   110	        params.put("path", subpath.toString()); // always store it relative, not absolute
   111	        try {
   112	            return new MediaAsset(this, params);
   113	        } catch (IllegalArgumentException e) {
   114	            log.warn("Bad path", e);
   115	            return null;
   116	        }
   117	    }
   118	
   119	    // convenience method to create directly from single File arguement (LocalAssetStore only)
   120	    public MediaAsset create(File file)
   121	    throws IllegalArgumentException {
   122	        JSONObject params = new JSONObject();
   123	
   124	        params.put("path", file.getAbsolutePath().toString());
   125	        return create(params);
   126	    }
   127	
   128	    public boolean cacheLocal(MediaAsset ma, boolean force) {
   129	        return true; // easy!
   130	    }
   131	
   132	    // note this does not check for existence; useful to know where to write such a file
   133	    public Path localPath(MediaAsset ma) {
   134	        if (ma == null) throw new IllegalArgumentException("MediaAsset is null");
   135	        Path subpath = pathFromParameters(ma.getParameters(), false);
   136	        return root().resolve(subpath);
   137	    }
   138	
   139	    // this returns the subpath relative to root
   140	    public Path pathFromParameters(JSONObject params) {
   141	        return pathFromParameters(params, false); // default behavior will be not to check
   142	    }
   143	
   144	    public Path pathFromParameters(JSONObject params, boolean checkExists) {
   145	        if (params == null) throw new IllegalArgumentException("null path");
   146	        Object p = getParameter(params, "path");
   147	        if (p == null) {
   148	            log.warn("pathFromParameters(): Invalid parameters");
   149	            throw new IllegalArgumentException("null path");
   150	        }
   151	        Path passed = Paths.get(p.toString());
   152	        Path path = null;
   153	        if (checkExists) {
   154	            path = ensurePath(root(), passed);
   155	        } else {
   156	            path = checkPath(root(), passed);
   157	        }
   158	        return path;
   159	    }
   160	
   161	    @Override public MediaAsset copyIn(final File file, final JSONObject params,
   162	        final boolean createMediaAsset)
   163	    throws IOException {
   164	        if (!this.writable) throw new IOException(this.name + " is a read-only AssetStore");
   165	        Path subpath = pathFromParameters(params);
   166	        if (subpath == null) throw new IOException("no path passed in parameters");
   167	        Path fullpath = root().resolve(subpath);
   168	        fullpath.getParent().toFile().mkdirs();
   169	        log.debug("copying from " + file + " to " + fullpath);
   170	        Files.copy(file.toPath(), fullpath, REPLACE_EXISTING);
   171	        params.put("path", subpath.toString()); // always store it relative, not absolute (in case it was passed in as such)
   172	        if (!createMediaAsset) return null;
   173	        return new MediaAsset(this, params);
   174	    }
   175	
   176	    @Override public void copyAsset(final MediaAsset fromMA, final MediaAsset toMA)
   177	    throws IOException {
   178	        // i guess we could pass this case along to AssetStore.copyAssetAny() ??
   179	        if ((fromMA == null) || (toMA == null) || (fromMA.getStore() == null) ||
   180	            (toMA.getStore() == null)) throw new IOException("null value(s) in copyAsset()");
   181	        if (!(fromMA.getStore() instanceof LocalAssetStore) ||
   182	            !(toMA.getStore() instanceof LocalAssetStore))
   183	            throw new IOException("invalid AssetStore type(s)");
   184	        if (!this.writable) throw new IOException(this.name + " is a read-only AssetStore");
   185	        throw new IOException("oops, LocalAssetStore.copyAsset() still not implemented. :/"); 
   186	    }
   187	
   188	    @Override public void deleteFrom(final MediaAsset ma) {
   189	        if (!this.contains(ma)) return; 
   190	        if (!this.writable) return;

exec
/bin/bash -lc "rg -n \"class .*AssetStore|@Override public MediaAsset create|create\\(final JSONObject\" src/main/java/org/ecocean/media -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 479ms:
src/main/java/org/ecocean/media/AssetStoreFactory.java:12:public class AssetStoreFactory {
src/main/java/org/ecocean/media/LocalAssetStore.java:30:public class LocalAssetStore extends AssetStore {
src/main/java/org/ecocean/media/LocalAssetStore.java:105:    @Override public MediaAsset create(final JSONObject params)
src/main/java/org/ecocean/media/AssetStore.java:50:public abstract class AssetStore implements java.io.Serializable {
src/main/java/org/ecocean/media/AssetStore.java:270:    // subclass can override, but this should work for AssetStores which can handle making a local cached copy of file
src/main/java/org/ecocean/media/YouTubeAssetStore.java:28:public class YouTubeAssetStore extends AssetStore {
src/main/java/org/ecocean/media/YouTubeAssetStore.java:42:    @Override public MediaAsset create(final JSONObject params)
src/main/java/org/ecocean/media/URLAssetStore.java:21:public class URLAssetStore extends AssetStore {
src/main/java/org/ecocean/media/URLAssetStore.java:42:    @Override public MediaAsset create(final JSONObject params)
src/main/java/org/ecocean/media/AssetStoreConfig.java:20:public class AssetStoreConfig implements java.io.Serializable {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/URLAssetStore.java | sed -n '1,150p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/YouTubeAssetStore.java | sed -n '1,130p'" in /mnt/c/Wildbook-clean2
 succeeded in 240ms:
     1	package org.ecocean.media;
     2	
     3	import java.io.File;
     4	import java.io.FileOutputStream;
     5	import java.io.InputStream;
     6	import java.io.IOException;
     7	import java.io.OutputStream;
     8	
     9	import java.net.MalformedURLException;
    10	import java.net.URL;
    11	import java.nio.file.Files;
    12	import java.nio.file.Path;
    13	import java.nio.file.Paths;
    14	import org.ecocean.shepherd.core.Shepherd;
    15	import org.json.JSONObject;
    16	
    17	/**
    18	 * URLAssetStore references MediaAssets that reside at arbitrary URLs.
    19	 *
    20	 */
    21	public class URLAssetStore extends AssetStore {
    22	    // private static final String KEY_ROOT = "root";
    23	
    24	    /**
    25	     * Create a new local filesystem asset store.
    26	     *
    27	     * @param name Friendly name for the store.
    28	     */
    29	    public URLAssetStore(final String name) {
    30	        this(null, name, null, false);
    31	    }
    32	
    33	    URLAssetStore(final Integer id, final String name, final AssetStoreConfig config,
    34	        final boolean writable) {
    35	        super(id, name, AssetStoreType.URL, config, false); // note: made this always non-writable... yes?
    36	    }
    37	
    38	    public AssetStoreType getType() {
    39	        return AssetStoreType.URL;
    40	    }
    41	
    42	    @Override public MediaAsset create(final JSONObject params)
    43	    throws IllegalArgumentException {
    44	        if (urlFromParameters(params) == null)
    45	            throw new IllegalArgumentException("no url parameter");
    46	        return new MediaAsset(this, params);
    47	    }
    48	
    49	    // convenience
    50	    public MediaAsset create(final URL url) {
    51	        return create(url.toString());
    52	    }
    53	
    54	    public MediaAsset create(final String url) {
    55	        JSONObject p = new JSONObject();
    56	
    57	        p.put("url", url);
    58	        return create(p);
    59	    }
    60	
    61	    @Override public boolean cacheLocal(MediaAsset ma, boolean force) {
    62	        Path lpath = localPath(ma);
    63	
    64	        if (lpath == null) return false; 
    65	        if (!force && Files.exists(lpath)) return true; // we assume if we have it, then we should be cool
    66	        System.out.println("URL.cacheLocal() trying to write to " + lpath);
    67	        String url = urlFromParameters(ma.getParameters());
    68	        try {
    69	            fetchFileFromURL(new URL(url), lpath.toFile());
    70	        } catch (Exception ioe) {
    71	            System.out.println("WARN: cacheLocal got " + ioe.toString() + " while fetching " + url +
    72	                " via " + ma.toString());
    73	            return false;
    74	        }
    75	        return true;
    76	    }
    77	
    78	    @Override public Path localPath(MediaAsset ma) {
    79	        JSONObject params = ma.getParameters();
    80	        String url = urlFromParameters(params);
    81	
    82	        if (url == null) return null;
    83	        String suffix = "";
    84	        int i = url.lastIndexOf("/");
    85	        if (i > -1) suffix = url.substring(i + 1);
    86	        return Paths.get("/tmp", hashCode(params) + suffix);
    87	    }
    88	
    89	    @Override public MediaAsset copyIn(final File file, final JSONObject params,
    90	        final boolean createMediaAsset)
    91	    throws IOException {
    92	        throw new IOException(this.name + " is a read-only AssetStore");
    93	        // if (!this.writable) throw new IOException(this.name + " is a read-only AssetStore");
    94	    }
    95	
    96	    @Override public void copyAsset(final MediaAsset fromMA, final MediaAsset toMA)
    97	    throws IOException {
    98	        throw new IOException("oops, URL.copyAsset() still not implemented. :/"); 
    99	    }
   100	
   101	    @Override public void deleteFrom(final MediaAsset ma) {
   102	        return; 
   103	    }
   104	
   105	    @Override public URL webURL(final MediaAsset ma) {
   106	        if (ma == null) return null;
   107	        String url = urlFromParameters(ma.getParameters());
   108	        if (url == null) return null;
   109	        try {
   110	            URL u = new URL(url);
   111	            return u;
   112	        } catch (MalformedURLException e) {
   113	            System.out.println("WARNING: " + url + " generated " + e.toString());
   114	            return null;
   115	        }
   116	    }
   117	
   118	    @Override public String hashCode(JSONObject params) {
   119	        String url = urlFromParameters(params);
   120	
   121	        if (url == null) return null;
   122	        return URLAssetStore.hexStringSHA256(url);
   123	    }
   124	
   125	    @Override public JSONObject createParameters(File file, String grouping) {
   126	        JSONObject p = new JSONObject();
   127	
   128	        return p;
   129	    }
   130	
   131	    @Override public String getFilename(MediaAsset ma) {
   132	        URL u = this.webURL(ma);
   133	
   134	        if (u == null) return null;
   135	        return u.getPath(); // note this will return "/foo/bar/whatever.jpg" for example, but meh good enough?
   136	    }
   137	
   138	    private String urlFromParameters(JSONObject params) {
   139	        if (params == null) return null;
   140	        return params.optString("url");
   141	    }
   142	
   143	    public static void fetchFileFromURL(URL srcUrl, File targetFile)
   144	    throws IOException {
   145	        InputStream is = srcUrl.openStream();
   146	        OutputStream os = new FileOutputStream(targetFile);
   147	        byte[] b = new byte[2048];
   148	        int length;
   149	
   150	        while ((length = is.read(b)) != -1) {

 succeeded in 241ms:
     1	package org.ecocean.media;
     2	
     3	import java.io.File;
     4	import java.io.IOException;
     5	import java.util.ArrayList;
     6	import java.util.List;
     7	
     8	import java.net.URL;
     9	import java.nio.charset.Charset;
    10	import java.nio.file.Files;
    11	import java.nio.file.Path;
    12	import javax.servlet.http.HttpServletRequest;
    13	import org.apache.commons.lang3.StringUtils;
    14	import org.ecocean.servlet.ServletUtilities;
    15	import org.ecocean.shepherd.core.Shepherd;
    16	import org.joda.time.DateTime;
    17	import org.json.JSONException;
    18	import org.json.JSONObject;
    19	
    20	
    21	// TODO: soon to be deprecated: https://github.com/WildMeOrg/Wildbook/issues/694
    22	
    23	/**
    24	 * YouTubeAssetStore references MediaAssets that reside on YouTube.
    25	 * currently this is read-only but later could be writable with an API key if needed?
    26	 *
    27	 */
    28	public class YouTubeAssetStore extends AssetStore {
    29	    public YouTubeAssetStore(final String name) {
    30	        this(null, name, null, false);
    31	    }
    32	
    33	    YouTubeAssetStore(final Integer id, final String name, final AssetStoreConfig config,
    34	        final boolean writable) {
    35	        super(id, name, AssetStoreType.YouTube, config, false);
    36	    }
    37	
    38	    public AssetStoreType getType() {
    39	        return AssetStoreType.YouTube;
    40	    }
    41	
    42	    @Override public MediaAsset create(final JSONObject params)
    43	    throws IllegalArgumentException {
    44	        if (idFromParameters(params) == null) throw new IllegalArgumentException("no id parameter");
    45	        return new MediaAsset(this, params);
    46	    }
    47	
    48	    // convenience
    49	    public MediaAsset create(final String ytid) {
    50	        JSONObject p = new JSONObject();
    51	
    52	        p.put("id", ytid);
    53	        return create(p);
    54	    }
    55	
    56	    @Override public boolean cacheLocal(MediaAsset ma, boolean force) {
    57	        return false;
    58	    }
    59	
    60	    @Override public Path localPath(MediaAsset ma) {
    61	        return null;
    62	    }
    63	
    64	    @Override public MediaAsset copyIn(final File file, final JSONObject params,
    65	        final boolean createMediaAsset)
    66	    throws IOException {
    67	        throw new IOException(this.name + " is a read-only AssetStore");
    68	    }
    69	
    70	    @Override public void copyAsset(final MediaAsset fromMA, final MediaAsset toMA)
    71	    throws IOException {
    72	        throw new IOException("copyAsset() not available for YouTubeAssetStore");
    73	    }
    74	
    75	    @Override public void deleteFrom(final MediaAsset ma) {
    76	        return; 
    77	    }
    78	
    79	    @Override public URL webURL(final MediaAsset ma) {
    80	        String id = idFromParameters(ma.getParameters());
    81	
    82	        if (id == null) return null;
    83	        try {
    84	            return new URL("https://www.youtube.com/watch?v=" + id);
    85	        } catch (java.net.MalformedURLException ex) { // "should never happen"
    86	            return null;
    87	        }
    88	    }
    89	
    90	    @Override public String hashCode(JSONObject params) {
    91	        return "YouTube" + idFromParameters(params);
    92	    }
    93	
    94	    @Override public DateTime getDateTime(MediaAsset ma) {
    95	        if ((ma == null) || (ma.getMetadata() == null) ||
    96	            (ma.getMetadata().getData().optJSONObject("detailed") == null)) return null;
    97	        String upd = ma.getMetadata().getData().getJSONObject("detailed").optString("upload_date",
    98	            null);
    99	        if ((upd == null) || (upd.length() != 8)) return null; // is like YYYYMMDD
   100	        try {
   101	            return new DateTime(Integer.parseInt(upd.substring(0, 4)),
   102	                    Integer.parseInt(upd.substring(4, 6)), Integer.parseInt(upd.substring(6)), 0,
   103	                    1);
   104	        } catch (Exception ex) {}
   105	        return null;
   106	    }
   107	
   108	    @Override public JSONObject createParameters(File file, String grouping) {
   109	        JSONObject p = new JSONObject();
   110	
   111	        // will we even ever need this for a read-only system???  
   112	        return p;
   113	    }
   114	
   115	    @Override public String getFilename(MediaAsset ma) {
   116	        return idFromParameters(ma.getParameters()); // meh?
   117	    }
   118	
   119	    private static String idFromParameters(JSONObject params) {
   120	        if (params == null) return null;
   121	        return params.optString("id", null);
   122	    }
   123	
   124	    // most likely you want grabAndParse() really.  this is just a wrapper to YouTube.grab() basically
   125	    // how should we thread in bkgd??? ... probably this should by synchrous, but stuff can bg when needed (e.g. extractMetadata)
   126	    public static List<File> grab(MediaAsset ma)
   127	    throws IOException {
   128	        throw new IOException("deprecated");
   129	    }
   130	

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "PairX|Pairx|inspection|C13|Phase A|Phase B|Phase C|sync|queue" docs/plans/2026-05-18-empty-match-prospects-design.md docs/plans/2026-05-09-ml-service-migration-v2.md' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MlServiceProcessorTest.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
 succeeded in 213ms:
     1	package org.ecocean.ia;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertFalse;
     5	import static org.junit.jupiter.api.Assertions.assertNotNull;
     6	import static org.junit.jupiter.api.Assertions.assertNull;
     7	import static org.junit.jupiter.api.Assertions.assertTrue;
     8	
     9	import org.json.JSONObject;
    10	import org.junit.jupiter.api.Test;
    11	
    12	/**
    13	 * v2 commit #9: pure-logic tests for MlServiceProcessor.
    14	 *
    15	 * <p>The Phase 1-5 lifecycle methods require real Shepherd transactions,
    16	 * JDO mutations, and live OpenSearch — those are reviewable by diff and
    17	 * exercised by hand-test per the v2 plan's test-strategy decision
    18	 * (WireMock unit tests only). Here we cover:</p>
    19	 *
    20	 * <ul>
    21	 *   <li>Top-level {@code process()} payload routing (validation errors,
    22	 *       missing-payload-fields branches).</li>
    23	 *   <li>{@code mapNonRetryableError(IAException)} maps each typed code
    24	 *       to the right outcome Kind.</li>
    25	 *   <li>{@code bboxKey}/{@code thetaKey} formatting (rounding and
    26	 *       string-format invariants).</li>
    27	 *   <li>{@code findExistingAnnotation} dedupe matching.</li>
    28	 * </ul>
    29	 */
    30	class MlServiceProcessorTest {
    31	
    32	    // --- process() payload routing -------------------------------------
    33	
    34	    @Test void processRejectsNullPayload() {
    35	        MlServiceProcessor p = new MlServiceProcessor("context0");
    36	        MlServiceJobOutcome out = p.process(null);
    37	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    38	        assertEquals("INVALID_PAYLOAD", out.getCode());
    39	    }
    40	
    41	    @Test void processRejectsPayloadWithoutMediaAssetOrAnnotationId() {
    42	        MlServiceProcessor p = new MlServiceProcessor("context0");
    43	        JSONObject payload = new JSONObject()
    44	            .put("mlServiceV2", true)
    45	            .put("taxonomyString", "Rhincodon typus");
    46	        MlServiceJobOutcome out = p.process(payload);
    47	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    48	        assertEquals("INVALID_PAYLOAD", out.getCode());
    49	        assertNotNull(out.getMessage());
    50	    }
    51	
    52	    // --- mapNonRetryableError ------------------------------------------
    53	
    54	    @Test void mapNonRetryableInvalidIsValidationError() {
    55	        IAException ex = new IAException("INVALID", "bad bbox", false, false);
    56	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    57	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    58	        assertEquals("INVALID", out.getCode());
    59	    }
    60	
    61	    @Test void mapNonRetryableSuccessFalseIsValidationError() {
    62	        IAException ex = new IAException("SUCCESS_FALSE",
    63	            "ml-service success=false", false, false);
    64	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    65	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    66	        assertEquals("SUCCESS_FALSE", out.getCode());
    67	    }
    68	
    69	    @Test void mapNonRetryableNetworkIsNetworkError() {
    70	        IAException ex = new IAException("NETWORK",
    71	            "ml-service 502", false, false);
    72	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    73	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    74	        assertEquals("NETWORK", out.getCode());
    75	    }
    76	
    77	    @Test void mapNonRetryableTimeoutIsNetworkError() {
    78	        IAException ex = new IAException("TIMEOUT",
    79	            "ml-service read timed out", false, false);
    80	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    81	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    82	    }
    83	
    84	    @Test void mapNonRetryableClientErrorIsNetworkError() {
    85	        // 4xx surfaces as CLIENT_ERROR from the client. The processor's
    86	        // contract: anything not VALIDATION* maps to NETWORK (with code preserved).
    87	        IAException ex = new IAException("CLIENT_ERROR",
    88	            "ml-service 400", false, false);
    89	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    90	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    91	        assertEquals("CLIENT_ERROR", out.getCode());
    92	    }
    93	
    94	    @Test void mapNonRetryableUnknownCodeIsNetworkError() {
    95	        IAException ex = new IAException("WEIRD_CODE",
    96	            "something happened", false, false);
    97	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    98	        // Defensive default: unknown codes route to NETWORK rather than
    99	        // crashing the processor.
   100	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
   101	    }
   102	
   103	    @Test void mapNonRetryableNullCode() {
   104	        // IAException constructed with the legacy 1/2/3-arg constructors
   105	        // leaves code null. Treat as ERROR_NETWORK.
   106	        IAException ex = new IAException("legacy message");
   107	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
   108	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
   109	    }
   110	
   111	    // --- bboxKey / thetaKey -------------------------------------------
   112	
   113	    @Test void bboxKeyRoundsToInts() {
   114	        // The composite-unique-index columns are literal strings so we get
   115	        // debugability over hash opacity. Rounded ints from a 4-element double[].
   116	        assertEquals("10:20:30:40",
   117	            MlServiceProcessor.bboxKey(new double[] { 10.0, 20.0, 30.0, 40.0 }));
   118	        assertEquals("10:20:30:40",
   119	            MlServiceProcessor.bboxKey(new double[] { 10.4, 20.4, 30.4, 40.4 }));
   120	        assertEquals("11:21:31:41",
   121	            MlServiceProcessor.bboxKey(new double[] { 10.5, 20.5, 30.5, 40.5 }));
   122	    }
   123	
   124	    @Test void thetaKeyRoundsToFourDecimals() {
   125	        assertEquals("0.0000", MlServiceProcessor.thetaKey(0.0));
   126	        assertEquals("3.1416", MlServiceProcessor.thetaKey(3.1415926));
   127	        // Negative angles round symmetrically.
   128	        assertEquals("-1.5708", MlServiceProcessor.thetaKey(-1.5707963));
   129	    }
   130	
   131	    @Test void thetaKeyHandlesNegativeZero() {
   132	        // Negative zero formats the same as positive zero, matching the
   133	        // expected key for "theta is zero".
   134	        String k = MlServiceProcessor.thetaKey(-0.0);
   135	        assertTrue(k.equals("0.0000") || k.equals("-0.0000"),
   136	            "unexpected thetaKey for -0.0: " + k);
   137	    }
   138	}

 succeeded in 226ms:
docs/plans/2026-05-09-ml-service-migration-v2.md:33:| Shared top-level Task across per-asset queue jobs; first-finisher marked the whole batch terminal | Per-asset child Tasks under a retained topTask via real `setParent`/`addChild`; child finalization is local; topTask still holds all MAs for summary code |
docs/plans/2026-05-09-ml-service-migration-v2.md:41:| `wbiaRegister` queue with FileQueue at-most-once + manual reconcile servlet | DB-backed via `Annotation.wbiaRegistered`; periodic background thread does the polling and registration; no separate FileQueue |
docs/plans/2026-05-09-ml-service-migration-v2.md:62:        ▼ (ml-service path; per-asset child Task on detection queue)
docs/plans/2026-05-09-ml-service-migration-v2.md:80:        │   IndexingManager queue (which may contain unrelated entities).
docs/plans/2026-05-09-ml-service-migration-v2.md:83:  if timed out: enqueue {deferredMatch:true, ids} on existing IA queue;
docs/plans/2026-05-09-ml-service-migration-v2.md:101:  → No queue infrastructure; the columns ARE the durable queue. Restart-safe.
docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-09-ml-service-migration-v2.md:138:- A separate `wbiaRegister` queue. The DB column IS the durable queue.
docs/plans/2026-05-09-ml-service-migration-v2.md:148:| `processing-mlservice` | new (commit #5): set on enqueue success | no |
docs/plans/2026-05-09-ml-service-migration-v2.md:254:`IndexingManager` queue: that queue may contain unrelated entities and queue-depth
docs/plans/2026-05-09-ml-service-migration-v2.md:266:2. Per MediaAsset: create `childTask`. Use real Task hierarchy: `topTask.addChild(childTask)` and `childTask.setParent(topTask)`. Each child holds *only this MA* via `setObjectMediaAssets(List.of(thisMa))`. Persist both. Enqueue per-asset job with `taskId = childTask.getId()`.
docs/plans/2026-05-09-ml-service-migration-v2.md:278:startup pass that walks media assets stuck in that state and re-enqueues a
docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
docs/plans/2026-05-18-empty-match-prospects-design.md:27:   pre-persist. Even after they persist, OS indexing is async with
docs/plans/2026-05-18-empty-match-prospects-design.md:62:- **Deferred enqueue explicitly calls `IAGateway.requeueJob(payload, true)`**
docs/plans/2026-05-18-empty-match-prospects-design.md:64:  function by name; `__queueRetries` is set by `requeueJob`
docs/plans/2026-05-18-empty-match-prospects-design.md:100:  Blocker). `IAGateway.requeueJob` caps at `MAX_RETRIES=30` with
docs/plans/2026-05-18-empty-match-prospects-design.md:101:  30s sleep (`IAGateway.java:751-810`) — ~15 min before the queue
docs/plans/2026-05-18-empty-match-prospects-design.md:158:  reuse existing `IAGateway.requeueJob` 30s fixed delay (verified
docs/plans/2026-05-18-empty-match-prospects-design.md:176:stays in sync as a HotSpotter fallback.
docs/plans/2026-05-18-empty-match-prospects-design.md:197:| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Per-job wrapper; current implementation widens nothing. Falls back to `enqueueDeferredMatch` on timeout. |
docs/plans/2026-05-18-empty-match-prospects-design.md:200:| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues via IAGateway with `mlServiceV2DeferredMatch: true`. Today: no attempt counter. |
docs/plans/2026-05-18-empty-match-prospects-design.md:201:| `IA.enqueueOneAssetForMlService` creates per-asset `Task childTask = new Task(topTask)` | `IA.java:281-287` | Per-asset child has parent=topTask; topTask owns `objectMediaAssets` (the same-species batch). |
docs/plans/2026-05-18-empty-match-prospects-design.md:225:3. Phase A eligibility check gains `ma.isValidImageForIA() != null
docs/plans/2026-05-18-empty-match-prospects-design.md:230:4. Phase B does NOT re-validate MA eligibility. Documented as an
docs/plans/2026-05-18-empty-match-prospects-design.md:231:   explicit decision: MA validity does not change between Phase A
docs/plans/2026-05-18-empty-match-prospects-design.md:232:   commit and Phase B HTTP. One-line comment in code.
docs/plans/2026-05-18-empty-match-prospects-design.md:238:   the existing `NETWORK_FAIL` outcome; Phase C log line
docs/plans/2026-05-18-empty-match-prospects-design.md:241:   Phase A:
docs/plans/2026-05-18-empty-match-prospects-design.md:289:  stale-cache duplicate POSTs if Phase C races or fails between
docs/plans/2026-05-18-empty-match-prospects-design.md:300:`IA.enqueueOneAssetForMlService` at `IA.java:281-287` creates each
docs/plans/2026-05-18-empty-match-prospects-design.md:606:        enqueueDeferredMatch(annotationIds, taskId, matchConfig, g);
docs/plans/2026-05-18-empty-match-prospects-design.md:638:The existing requeue mechanism is `IAGateway.requeueJob` at
docs/plans/2026-05-18-empty-match-prospects-design.md:641:- 30s fixed delay between requeues (line 785: `whileSleepMillis = 30000`).
docs/plans/2026-05-18-empty-match-prospects-design.md:642:- Tracks `__queueRetries`, `__queueActualRetries`, `__queueStart`
docs/plans/2026-05-18-empty-match-prospects-design.md:646:- mlServiceV2 jobs land back on the detection queue (line 792-797).
docs/plans/2026-05-18-empty-match-prospects-design.md:663:// 12 minutes from first deferral. Aligns with IAGateway.requeueJob
docs/plans/2026-05-18-empty-match-prospects-design.md:664:// real cap (MAX_RETRIES=30 × 30s = ~15min before the queue gives
docs/plans/2026-05-18-empty-match-prospects-design.md:666:// fires inside the queue's window with margin. Most v2 imports
docs/plans/2026-05-18-empty-match-prospects-design.md:685:`enqueueDeferredMatch` is extended to take the gate outcome so
docs/plans/2026-05-18-empty-match-prospects-design.md:690:**Use `requeueJob` explicitly** (Codex round-4 Blocker). Setting
docs/plans/2026-05-18-empty-match-prospects-design.md:691:`__queueRetries` on the JSON is NOT enough — `IAGateway.requeueJob`
docs/plans/2026-05-18-empty-match-prospects-design.md:696:// In MlServiceProcessor.enqueueDeferredMatch (or its replacement).
docs/plans/2026-05-18-empty-match-prospects-design.md:697:private void enqueueDeferredMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-design.md:715:    // IAGateway.requeueJob(payload, true) is what creates the 30s
docs/plans/2026-05-18-empty-match-prospects-design.md:718:    IAGateway.requeueJob(payload, /* increment= */ true);
docs/plans/2026-05-18-empty-match-prospects-design.md:729:directly, and `enqueueDeferredMatch` is private. Codex review item
docs/plans/2026-05-18-empty-match-prospects-design.md:790:Phase A/B/C pattern from the WBIA register thread (commit `c6ffe5d20`).
docs/plans/2026-05-18-empty-match-prospects-design.md:803:- Stub `gateForBatch` to return DEFER → assert `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-design.md:823:  `MAX_DEFER_AGE_MILLIS = 12 min` (well inside `requeueJob`'s
docs/plans/2026-05-18-empty-match-prospects-design.md:824:  ~15min queue cap). User's match runs against whatever's
docs/plans/2026-05-18-empty-match-prospects-design.md:844:sync — annotations no longer linger with placeholder acmIds in OS.
docs/plans/2026-05-18-empty-match-prospects-design.md:933:- `enqueueDeferredMatch_publishesViaRequeueJob_with30sDelay` —
docs/plans/2026-05-18-empty-match-prospects-design.md:934:  observe `__queueRetries` set, sleep mechanism invoked.
docs/plans/2026-05-18-empty-match-prospects-design.md:935:- `enqueueDeferredMatch_includesRoutingFlags` — captured payload
docs/plans/2026-05-18-empty-match-prospects-design.md:937:  round-5 Blocker). Without both, the requeued job is dropped on
docs/plans/2026-05-18-empty-match-prospects-design.md:952:- `processor_callsEnqueueDeferredMatch_onGateDefer`
docs/plans/2026-05-18-empty-match-prospects-design.md:972:- `deferred_agesOutAt12min_withinRequeueCap` — verify GIVE_UP
docs/plans/2026-05-18-empty-match-prospects-design.md:973:  fires before requeueJob's 30-retry cap.
docs/plans/2026-05-18-empty-match-prospects-design.md:985:small publisher interface for the deferred-match enqueue path so
docs/plans/2026-05-18-empty-match-prospects-design.md:987:internals or going through `IAGateway.requeueJob` for real:
docs/plans/2026-05-18-empty-match-prospects-design.md:996:// Real impl wraps IAGateway.requeueJob(payload, true).
docs/plans/2026-05-18-empty-match-prospects-design.md:1002:`IAGateway.requeueJob` directly. Default constructor wires the
docs/plans/2026-05-18-empty-match-prospects-design.md:1023:- `MAX_DEFER_AGE_MILLIS = 12min` (within `requeueJob` cap)
docs/plans/2026-05-18-empty-match-prospects-design.md:1059:  - `IA.enqueueOneAssetForMlService` (topTask boundary) —

 succeeded in 252ms:
     1	package org.ecocean.ia;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertNotNull;
     5	import static org.junit.jupiter.api.Assertions.assertNotSame;
     6	import static org.junit.jupiter.api.Assertions.assertNull;
     7	import static org.junit.jupiter.api.Assertions.assertTrue;
     8	
     9	import org.json.JSONArray;
    10	import org.json.JSONObject;
    11	import org.junit.jupiter.api.Test;
    12	
    13	/**
    14	 * Pure-function coverage of
    15	 * {@link MatchInspectionPairxEnricher#buildPayload} and
    16	 * {@link MatchInspectionPairxEnricher#findProspect}. Phase A/C paths
    17	 * that require Shepherd are exercised by integration on the live
    18	 * deployment — same precedent as the C6/C9 paths. (Empty-match-
    19	 * prospects design Track 2 C13.)
    20	 */
    21	class MatchInspectionPairxEnricherTest {
    22	
    23	    private static MatchInspectionPairxEnricher.PairxDto sampleDto() {
    24	        return new MatchInspectionPairxEnricher.PairxDto(
    25	            "mr-1", "ann-2", "annot",
    26	            "Salamandra salamandra",
    27	            "https://example.com/img1.jpg",
    28	            "https://example.com/img2.jpg",
    29	            new int[] { 10, 20, 100, 200 },
    30	            new int[] { 30, 40, 150, 300 },
    31	            0.0d, 0.5d);
    32	    }
    33	
    34	    // --- buildPayload ---------------------------------------------------
    35	
    36	    @Test void buildPayload_setsAllFixedKeys() {
    37	        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
    38	        assertEquals("pairx", p.getString("algorithm"));
    39	        assertEquals("only_colors", p.getString("visualization_type"));
    40	        assertEquals(5, p.getInt("k_colors"));
    41	        assertEquals("miewid-msv4.1", p.getString("model_id"));
    42	        assertEquals(false, p.getBoolean("crop_bbox"));
    43	        assertEquals("backbone.blocks.3", p.getString("layer_key"));
    44	    }
    45	
    46	    @Test void buildPayload_setsImageUrisAsSeparateArrays() {
    47	        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
    48	        JSONArray image1 = p.getJSONArray("image1_uris");
    49	        JSONArray image2 = p.getJSONArray("image2_uris");
    50	        assertEquals("https://example.com/img1.jpg", image1.getString(0));
    51	        assertEquals("https://example.com/img2.jpg", image2.getString(0));
    52	    }
    53	
    54	    @Test void buildPayload_setsThetaArrays() {
    55	        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
    56	        assertEquals(0.0d, p.getJSONArray("theta1").getDouble(0));
    57	        assertEquals(0.5d, p.getJSONArray("theta2").getDouble(0));
    58	    }
    59	
    60	    @Test void buildPayload_bb1AndBb2AreDistinctReferences() {
    61	        // C12 regression guard: previous implementation reused one
    62	        // JSONArray for both keys; this test fails if the enricher
    63	        // reintroduces that bug.
    64	        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
    65	        JSONArray bb1 = p.getJSONArray("bb1");
    66	        JSONArray bb2 = p.getJSONArray("bb2");
    67	        assertNotSame(bb1, bb2);
    68	        // Confirm bb1 and bb2 contain the DTO's distinct bboxes.
    69	        assertEquals(10, bb1.getJSONArray(0).getInt(0));
    70	        assertEquals(30, bb2.getJSONArray(0).getInt(0));
    71	    }
    72	
    73	    @Test void buildPayload_doubleArrayShape() {
    74	        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
    75	        // PairX expects [[x, y, w, h]] for each bbox key.
    76	        JSONArray bb1Outer = p.getJSONArray("bb1");
    77	        assertEquals(1, bb1Outer.length());
    78	        JSONArray inner = bb1Outer.getJSONArray(0);
    79	        assertEquals(4, inner.length());
    80	    }
    81	
    82	    // --- findProspect ---------------------------------------------------
    83	
    84	    @Test void findProspect_returnsNullForNullMatchResult() {
    85	        assertNull(MatchInspectionPairxEnricher.findProspect(null, "x", "annot"));
    86	    }
    87	
    88	    @Test void findProspect_returnsNullWhenProspectsNull() {
    89	        MatchResult mr = new MatchResult();
    90	        // prospects field is null by default
    91	        assertNull(MatchInspectionPairxEnricher.findProspect(mr, "x", "annot"));
    92	    }
    93	
    94	    @Test void findProspect_matchesByAnnotationIdAndScoreType() {
    95	        MatchResult mr = new MatchResult();
    96	        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
    97	        a1.setId("ann-1");
    98	        org.ecocean.Annotation a2 = new org.ecocean.Annotation();
    99	        a2.setId("ann-2");
   100	        MatchResultProspect p1 = new MatchResultProspect(a1, 1.0d, "annot", null);
   101	        MatchResultProspect p2 = new MatchResultProspect(a2, 0.5d, "annot", null);
   102	        // Inject via the populateProspects loop is private; we exercise
   103	        // findProspect by directly constructing a MatchResult and
   104	        // adding via reflection on the prospects field.
   105	        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
   106	        set.add(p1);
   107	        set.add(p2);
   108	        injectProspects(mr, set);
   109	        MatchResultProspect found = MatchInspectionPairxEnricher.findProspect(
   110	            mr, "ann-2", "annot");
   111	        assertNotNull(found);
   112	        assertEquals(a2, found.getAnnotation());
   113	    }
   114	
   115	    @Test void findProspect_distinguishesByScoreType() {
   116	        MatchResult mr = new MatchResult();
   117	        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
   118	        a1.setId("ann-1");
   119	        MatchResultProspect annotP = new MatchResultProspect(a1, 1.0d, "annot", null);
   120	        MatchResultProspect indivP = new MatchResultProspect(a1, 0.8d, "indiv", null);
   121	        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
   122	        set.add(annotP);
   123	        set.add(indivP);
   124	        injectProspects(mr, set);
   125	        MatchResultProspect foundAnnot = MatchInspectionPairxEnricher.findProspect(
   126	            mr, "ann-1", "annot");
   127	        MatchResultProspect foundIndiv = MatchInspectionPairxEnricher.findProspect(
   128	            mr, "ann-1", "indiv");
   129	        assertNotNull(foundAnnot);
   130	        assertNotNull(foundIndiv);
   131	        assertTrue(foundAnnot.isType("annot"));
   132	        assertTrue(foundIndiv.isType("indiv"));
   133	        assertNotSame(foundAnnot, foundIndiv);
   134	    }
   135	
   136	    @Test void findProspect_returnsNullWhenAnnotationIdMissing() {
   137	        MatchResult mr = new MatchResult();
   138	        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
   139	        a1.setId("ann-1");
   140	        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
   141	        set.add(new MatchResultProspect(a1, 1.0d, "annot", null));
   142	        injectProspects(mr, set);
   143	        assertNull(MatchInspectionPairxEnricher.findProspect(mr, "ann-MISSING", "annot"));
   144	    }
   145	
   146	    @SuppressWarnings("unchecked")
   147	    private static void injectProspects(MatchResult mr,
   148	        java.util.HashSet<MatchResultProspect> prospects) {
   149	        try {
   150	            java.lang.reflect.Field f = MatchResult.class.getDeclaredField("prospects");
   151	            f.setAccessible(true);
   152	            f.set(mr, prospects);
   153	        } catch (Exception ex) {
   154	            throw new RuntimeException(ex);
   155	        }
   156	    }
   157	}

codex
One configuration assumption looks suspicious: the new enricher still resolves PairX through `MatchResult._getPairxUrl`, which reads `api_endpoint` from `_mlservice_conf`. The v2 contract in your bundle names `base_url` plus endpoint paths, so I’m checking whether the branch has a normalizer or whether this path silently disables PairX on true v2 config.
exec
/bin/bash -lc "rg -n \"getActiveMlServiceConfigs|mlConfig|api_endpoint|base_url|joinEndpoint|pipeline\\(|extract\\(\" src/main/java/org/ecocean/ia/MlServiceProcessor.java src/test/java/org/ecocean/ia/MlServiceProcessorTest.java src/test/java/org/ecocean/ia/MlServiceClientTest.java src/test/java/org/ecocean/IAJsonPropertiesTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"base_url|detection_endpoint|extraction_endpoint|api_endpoint\" IA.json src/test src/main/resources docs -g '*.json' -g '*.java' -g '*.md'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '160,285p'" in /mnt/c/Wildbook-clean2
 succeeded in 297ms:
src/test/java/org/ecocean/IAJsonPropertiesTest.java:40:                            .put("api_endpoint", "https://ml-service.example.org")))
src/test/java/org/ecocean/IAJsonPropertiesTest.java:99:    @Test void getActiveMlServiceConfigs_returnsConfigsWhenDefaultIsVector() {
src/test/java/org/ecocean/IAJsonPropertiesTest.java:101:        JSONArray confs = iac.getActiveMlServiceConfigs(TAXY);
src/test/java/org/ecocean/IAJsonPropertiesTest.java:107:    @Test void getActiveMlServiceConfigs_returnsNullWhenDefaultIsHotspotter() {
src/test/java/org/ecocean/IAJsonPropertiesTest.java:111:        assertNull(iac.getActiveMlServiceConfigs(TAXY));
src/test/java/org/ecocean/IAJsonPropertiesTest.java:114:    @Test void getActiveMlServiceConfigs_returnsNullWhenMlServiceConfMissing() {
src/test/java/org/ecocean/IAJsonPropertiesTest.java:124:        assertNull(iac.getActiveMlServiceConfigs(TAXY));
src/test/java/org/ecocean/IAJsonPropertiesTest.java:127:    @Test void getActiveMlServiceConfigs_returnsNullForEmptyArray() {
src/test/java/org/ecocean/IAJsonPropertiesTest.java:138:        assertNull(iac.getActiveMlServiceConfigs(TAXY));
src/test/java/org/ecocean/IAJsonPropertiesTest.java:141:    @Test void getActiveMlServiceConfigs_returnsNullForNullTaxonomy() {
src/test/java/org/ecocean/IAJsonPropertiesTest.java:143:        assertNull(iac.getActiveMlServiceConfigs(null));
src/test/java/org/ecocean/IAJsonPropertiesTest.java:162:    @Test void getActiveMlServiceConfigs_returnsNullWhenMlServiceConfNotAnArray() {
src/test/java/org/ecocean/IAJsonPropertiesTest.java:175:        assertNull(iac.getActiveMlServiceConfigs(TAXY));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:22:    // --- joinEndpoint -----------------------------------------------------
src/test/java/org/ecocean/ia/MlServiceClientTest.java:24:    @Test void joinEndpointHandlesTrailingSlashes() {
src/test/java/org/ecocean/ia/MlServiceClientTest.java:26:            MlServiceClient.joinEndpoint("https://ml/", "/pipeline/"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:28:            MlServiceClient.joinEndpoint("https://ml///", "/pipeline/"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:30:            MlServiceClient.joinEndpoint("https://ml", "/pipeline/"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:106:            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:125:            det.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:146:            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:147:                ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:160:        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:214:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:275:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:333:                    det.mlConfig.optString("predict_model_id", null));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:410:            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:670:        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:672:        JSONObject mlConfig = configs.optJSONObject(0);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:673:        if (mlConfig == null) return null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:674:        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:675:        return new ConfigPair(mlConfig, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:679:        JSONObject mlConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:726:        JSONObject mlConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:739:        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:910:        final JSONObject mlConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:913:        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:914:            this.mlConfig = mlConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:922:        final JSONObject mlConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:926:        DetectionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:930:            this.mlConfig = mlConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:938:            this.mlConfig = null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:951:        final JSONObject mlConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:957:        ExtractionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:961:            this.mlConfig = mlConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:971:            this.mlConfig = null;

 succeeded in 316ms:
   160	        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
   161	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   162	            matchConfig);
   163	        if (matchOutcome != null) return matchOutcome;
   164	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   165	    }
   166	
   167	    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
   168	        String encounterId, String maId) {
   169	        Shepherd shep = new Shepherd(context);
   170	        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
   171	        try {
   172	            FeatureType.initAll(shep);
   173	            shep.beginDBTransaction();
   174	            MediaAsset ma = shep.getMediaAsset(maId);
   175	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   176	            Task task = Task.load(taskId, shep);
   177	
   178	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   179	            if (staleReason != null) {
   180	                markTaskDroppedStale(shep, task, staleReason);
   181	                shep.commitDBTransaction();
   182	                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
   183	            }
   184	
   185	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
   186	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   187	            if (configs == null) {
   188	                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
   189	                markTaskCompleted(task);
   190	                shep.commitDBTransaction();
   191	                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
   192	            }
   193	
   194	            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
   195	                markTaskError(task, "INVALID",
   196	                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
   197	                shep.commitDBTransaction();
   198	                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
   199	                    "_mlservice_conf missing predict_model_id"));
   200	            }
   201	
   202	            URL webUrl = ma.webURL();
   203	            if (webUrl == null) {
   204	                markTaskError(task, "INVALID_IMAGE_URI",
   205	                    "MediaAsset " + maId + " has no webURL");
   206	                shep.commitDBTransaction();
   207	                return DetectionContext.done(MlServiceJobOutcome.validationError(
   208	                    "INVALID_IMAGE_URI", "MediaAsset " + maId + " has no webURL"));
   209	            }
   210	
   211	            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   212	            shep.commitDBTransaction();
   213	            return new DetectionContext(webUrl.toString(),
   214	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   215	                configs.matchConfig);
   216	        } finally {
   217	            shep.rollbackAndClose();
   218	        }
   219	    }
   220	
   221	    private ExtractionContext loadExtractionContext(String taxonomyString, String taskId,
   222	        String annId) {
   223	        Shepherd shep = new Shepherd(context);
   224	        shep.setAction(ACTION_PREFIX + "loadExtractionContext");
   225	        try {
   226	            shep.beginDBTransaction();
   227	            Annotation ann = shep.getAnnotation(annId);
   228	            Task task = Task.load(taskId, shep);
   229	            if (ann == null) {
   230	                markTaskDroppedStale(shep, task, "annotation missing");
   231	                shep.commitDBTransaction();
   232	                return ExtractionContext.done(MlServiceJobOutcome.stale("annotation missing"));
   233	            }
   234	            MediaAsset ma = ann.getMediaAsset();
   235	            if (ma == null) {
   236	                markTaskDroppedStale(shep, task, "annotation media asset missing");
   237	                shep.commitDBTransaction();
   238	                return ExtractionContext.done(
   239	                    MlServiceJobOutcome.stale("annotation media asset missing"));
   240	            }
   241	
   242	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString,
   243	                ann.findEncounter(shep));
   244	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   245	            if (configs == null) {
   246	                markTaskCompleted(task);
   247	                shep.commitDBTransaction();
   248	                return ExtractionContext.done(MlServiceJobOutcome.stale("pending-species"));
   249	            }
   250	            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
   251	                markTaskCompleted(task);
   252	                shep.commitDBTransaction();
   253	                return ExtractionContext.done(MlServiceJobOutcome.stale("embedding-exists"));
   254	            }
   255	
   256	            URL webUrl = ma.webURL();
   257	            if (webUrl == null) {
   258	                markTaskError(task, "INVALID_IMAGE_URI",
   259	                    "Annotation " + annId + " media asset has no webURL");
   260	                shep.commitDBTransaction();
   261	                return ExtractionContext.done(MlServiceJobOutcome.validationError(
   262	                    "INVALID_IMAGE_URI", "Annotation " + annId + " media asset has no webURL"));
   263	            }
   264	            int[] bbox = ann.getBbox();
   265	            if (bbox == null || bbox.length != 4) {
   266	                markTaskError(task, "INVALID_BBOX", "Annotation " + annId + " has no bbox");
   267	                shep.commitDBTransaction();
   268	                return ExtractionContext.done(MlServiceJobOutcome.validationError("INVALID_BBOX",
   269	                    "Annotation " + annId + " has no bbox"));
   270	            }
   271	
   272	            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   273	            shep.commitDBTransaction();
   274	            return new ExtractionContext(webUrl.toString(),
   275	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   276	                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
   277	        } finally {
   278	            shep.rollbackAndClose();
   279	        }
   280	    }
   281	
   282	    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
   283	        Shepherd shep = new Shepherd(context);
   284	        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
   285	        try {

 succeeded in 409ms:
IA.json:19:      "api_endpoint": "https://ml-service.rodan.scribble.com:8008",
IA.json:52:            "api_endpoint": "https://ml-service.rodan.scribble.com:8008",
IA.json:497:          "api_endpoint": "https://ml-service.rodan.scribble.com:8008",
docs/plans/2026-05-09-ml-service-migration-v2.md:116:| `org.ecocean.Embedding.findMatchProspects` (modified) | Drop the `api_endpoint` requirement; read method/version from the `_id_conf` entry directly; subtask-failure does not prematurely complete parent; clone-not-mutate `matchingSetQuery`. | +40 |
docs/plans/2026-05-09-ml-service-migration-v2.md:188:          "api_endpoint": "https://ml-service.example.org"
docs/plans/2026-05-09-ml-service-migration-v2.md:307:| 3 | `Embedding.findMatchProspects` bug fixes (api_endpoint, premature complete, mutation) | ~40 | #2 |
docs/plans/2026-05-18-c4-codex-review.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c4-codex-review.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c4-codex-review.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c4-codex-review.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c4-codex-review.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-c3-codex-review.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c3-codex-review.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c3-codex-review.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c3-codex-review.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c3-codex-review.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-c12-codex-review.md:90:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c12-codex-review.md:95:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c12-codex-review.md:96:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c12-codex-review.md:97:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c12-codex-review.md:107:`isVectorConfig = method != null || api_endpoint != null` — both
docs/plans/2026-05-18-c12-codex-review.md:1873:   402	        payload.remove("api_endpoint");
docs/plans/2026-05-18-c12-codex-review.md:1889:   418	        payload.remove("api_endpoint");
docs/plans/2026-05-18-c12-codex-review.md:1981:   444	            urlStr = confs.get(0).optString("api_endpoint", null);
docs/plans/2026-05-18-c12-codex-review.md:2501:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-c12-codex-review.md:2504:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-c2-codex-review.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c2-codex-review.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c2-codex-review.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c2-codex-review.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c2-codex-review.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-empty-match-prospects-design.md:467:`api_endpoint`-only configs that `Embedding.findMatchProspects`
docs/plans/2026-05-18-empty-match-prospects-design.md:916:  legacy `api_endpoint`-only path (Codex round-5 Major).
docs/plans/2026-05-18-c6-codex-review.md:90:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c6-codex-review.md:95:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c6-codex-review.md:96:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c6-codex-review.md:97:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c6-codex-review.md:107:`isVectorConfig = method != null || api_endpoint != null` — both
docs/plans/2026-05-18-c11-codex-review.md:90:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c11-codex-review.md:95:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c11-codex-review.md:96:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c11-codex-review.md:97:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c11-codex-review.md:107:`isVectorConfig = method != null || api_endpoint != null` — both
docs/plans/2026-05-18-c11-codex-review.md:1908:-            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
docs/plans/2026-05-18-c11-codex-review.md:1986:-            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
docs/plans/2026-05-18-c11-codex-review.md:2045:-        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:2050:-            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c11-codex-review.md:2083:-        payload.remove("api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:2099:-        payload.remove("api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:2332:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
docs/plans/2026-05-18-c11-codex-review.md:2410:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
docs/plans/2026-05-18-c11-codex-review.md:2469:+        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:2474:+            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c11-codex-review.md:2507:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:2523:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:2920:-            urlStr = confs.get(0).optString("api_endpoint", null);
docs/plans/2026-05-18-c11-codex-review.md:3456:+            urlStr = confs.get(0).optString("api_endpoint", null);
docs/plans/2026-05-18-c11-codex-review.md:4039:-            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
docs/plans/2026-05-18-c11-codex-review.md:4359:+            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
docs/plans/2026-05-18-c11-codex-review.md:5840:   217	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c11-codex-review.md:6069:   623	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-c11-codex-review.md:6070:   624	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c11-codex-review.md:6071:   625	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c11-codex-review.md:6114:   668	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-c11-codex-review.md:6115:   669	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c11-codex-review.md:6116:   670	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c11-codex-review.md:6487:   105	        // then MLService.getMethodValues for legacy api_endpoint
docs/plans/2026-05-18-c11-codex-review.md:7241:   278	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c11-codex-review.md:10275:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
docs/plans/2026-05-18-c11-codex-review.md:10431:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
docs/plans/2026-05-18-c11-codex-review.md:10549:+        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:10559:+            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c11-codex-review.md:10625:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:10657:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c11-codex-review.md:11441:+            urlStr = confs.get(0).optString("api_endpoint", null);
docs/plans/2026-05-18-c11-codex-review.md:12411:+            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
docs/plans/2026-05-18-c11-codex-review.md:13100:   647	            // embedding entry. (Legacy api_endpoint-only configs that
docs/plans/2026-05-18-c11-codex-review.md:13292:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-c11-codex-review.md:13295:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-c7-codex-review.md:90:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c7-codex-review.md:95:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c7-codex-review.md:96:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c7-codex-review.md:97:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c7-codex-review.md:107:`isVectorConfig = method != null || api_endpoint != null` — both
docs/plans/2026-05-18-c7-codex-review.md:579:   749	        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
docs/plans/2026-05-18-c7-codex-review.md:1311:    75	        // Legacy: api_endpoint present, no method. Must keep working.
docs/plans/2026-05-18-c7-codex-review.md:1312:    76	        JSONObject conf = new JSONObject().put("api_endpoint", "http://legacy");
docs/plans/2026-05-18-c8-codex-review.md:90:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c8-codex-review.md:95:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c8-codex-review.md:96:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c8-codex-review.md:97:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c8-codex-review.md:107:`isVectorConfig = method != null || api_endpoint != null` — both
docs/plans/2026-05-18-c8-codex-review.md:155:api_endpoint-only configs).
docs/plans/2026-05-18-c8-codex-review.md:424:        // Legacy api_endpoint-only config: neither method nor version is
docs/plans/2026-05-18-c8-codex-review.md:475:  api_endpoint-only configs?
docs/plans/2026-05-18-c8-codex-review.md:1882:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-c8-codex-review.md:1885:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-c8-codex-review.md:2006:   569	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-c8-codex-review.md:2007:   570	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c8-codex-review.md:2008:   571	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c8-codex-review.md:2051:   614	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-c8-codex-review.md:2052:   615	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c8-codex-review.md:2053:   616	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c8-codex-review.md:2390:   305	            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
docs/plans/2026-05-18-c8-codex-review.md:2449:   364	        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c8-codex-review.md:2454:   369	            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c8-codex-review.md:2984:   138	        // Legacy api_endpoint-only config: neither method nor version is
docs/plans/2026-05-18-c9-codex-review.md:90:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c9-codex-review.md:95:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c9-codex-review.md:96:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c9-codex-review.md:97:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c9-codex-review.md:107:`isVectorConfig = method != null || api_endpoint != null` — both
docs/plans/2026-05-18-c9-codex-review.md:152:EMBEDDING join entirely (legacy api_endpoint-only configs).
docs/plans/2026-05-18-c9-codex-review.md:230:        // Legacy api_endpoint-only config: gate just wants matchAgainst +
docs/plans/2026-05-18-c9-codex-review.md:1523:    33	 * entirely (legacy api_endpoint-only configs that can't derive a
docs/plans/2026-05-18-c9-codex-review.md:1760:    70	        // Legacy api_endpoint-only config: gate just wants matchAgainst +
docs/plans/2026-05-18-c9-codex-review.md:2305:   467	`api_endpoint`-only configs that `Embedding.findMatchProspects`
docs/plans/2026-05-18-c9-codex-review.md:2973:   647	            // embedding entry. (Legacy api_endpoint-only configs that
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:981:   569	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:982:   570	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:983:   571	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1026:   614	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1027:   615	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1028:   616	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2423:   258	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2744:   197	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3385:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3388:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4638:/bin/bash -lc 'rg -n "method|version|api_endpoint|pipeline_root|model_id|predict_model_id" src/main/resources/bundles/IA.json | head -n 120' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5058:   364	        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1449:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1450:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1451:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1494:        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1495:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1496:            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4418:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7571:        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7574:            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8240:        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8245:            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8503:src/main/java/org/ecocean/ia/MlServiceProcessor.java:569:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8504:src/main/java/org/ecocean/ia/MlServiceProcessor.java:571:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/test/java/org/ecocean/EmbeddingTest.java:60:        // Neither method nor api_endpoint set → not a vector config.
src/test/java/org/ecocean/EmbeddingTest.java:66:        // New _id_conf contract: method/version present, no api_endpoint.
src/test/java/org/ecocean/EmbeddingTest.java:75:        // Legacy: api_endpoint present, no method. Must keep working.
src/test/java/org/ecocean/EmbeddingTest.java:76:        JSONObject conf = new JSONObject().put("api_endpoint", "http://legacy");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1516:   569	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1517:   570	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1518:   571	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1561:   614	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1562:   615	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1563:   616	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2564:src/main/java/org/ecocean/ia/Task.java:735:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4399:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4402:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:622:provided `api_endpoint`, where `MLService.getMethodValues`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2316:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2319:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2694:   735	        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5183:src/main/java/org/ecocean/ia/MlServiceProcessor.java:569:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5184:src/main/java/org/ecocean/ia/MlServiceProcessor.java:571:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5254:src/main/java/org/ecocean/ia/Task.java:735:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5310:src/main/java/org/ecocean/ia/MlServiceProcessor.java:569:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5311:src/main/java/org/ecocean/ia/MlServiceProcessor.java:571:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5530:   569	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5531:   570	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5532:   571	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5575:   614	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5576:   615	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8457:The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8481:The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:138:        // Legacy api_endpoint-only config: neither method nor version is
src/test/java/org/ecocean/IAJsonPropertiesTest.java:40:                            .put("api_endpoint", "https://ml-service.example.org")))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:120:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:125:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:126:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:127:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:137:`isVectorConfig = method != null || api_endpoint != null` (the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1701:docs/plans/2026-05-09-ml-service-migration-v2.md:116:| `org.ecocean.Embedding.findMatchProspects` (modified) | Drop the `api_endpoint` requirement; read method/version from the `_id_conf` entry directly; subtask-failure does not prematurely complete parent; clone-not-mutate `matchingSetQuery`. | +40 |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1706:docs/plans/2026-05-09-ml-service-migration-v2.md:307:| 3 | `Embedding.findMatchProspects` bug fixes (api_endpoint, premature complete, mutation) | ~40 | #2 |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2437:   197	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2498:   258	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2812:   569	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2813:   570	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2814:   571	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2857:   614	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2858:   615	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2859:   616	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9066:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9069:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-c10-codex-review.md:90:        // legacy entries have api_endpoint instead of method/version
docs/plans/2026-05-18-c10-codex-review.md:95:        "base_url": "https://ml-service.example.com:8008",
docs/plans/2026-05-18-c10-codex-review.md:96:        "detection_endpoint": "/pipeline/",
docs/plans/2026-05-18-c10-codex-review.md:97:        "extraction_endpoint": "/extract/",
docs/plans/2026-05-18-c10-codex-review.md:107:`isVectorConfig = method != null || api_endpoint != null` — both
docs/plans/2026-05-18-c10-codex-review.md:158:fallback for legacy api_endpoint configs.
docs/plans/2026-05-18-c10-codex-review.md:1903:-            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
docs/plans/2026-05-18-c10-codex-review.md:1981:-            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
docs/plans/2026-05-18-c10-codex-review.md:2040:-        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:2045:-            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c10-codex-review.md:2078:-        payload.remove("api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:2094:-        payload.remove("api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:2327:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
docs/plans/2026-05-18-c10-codex-review.md:2405:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
docs/plans/2026-05-18-c10-codex-review.md:2464:+        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:2469:+            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c10-codex-review.md:2502:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:2518:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:2915:-            urlStr = confs.get(0).optString("api_endpoint", null);
docs/plans/2026-05-18-c10-codex-review.md:3451:+            urlStr = confs.get(0).optString("api_endpoint", null);
docs/plans/2026-05-18-c10-codex-review.md:4034:-            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
docs/plans/2026-05-18-c10-codex-review.md:4354:+            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
docs/plans/2026-05-18-c10-codex-review.md:4902:-                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c10-codex-review.md:4963:-                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c10-codex-review.md:5274:-        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-c10-codex-review.md:5275:-            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c10-codex-review.md:5276:-            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c10-codex-review.md:5319:-        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-c10-codex-review.md:5320:-            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c10-codex-review.md:5321:-            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c10-codex-review.md:5744:+                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c10-codex-review.md:5805:+                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c10-codex-review.md:6116:+        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-c10-codex-review.md:6117:+            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c10-codex-review.md:6118:+            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c10-codex-review.md:6161:+        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-c10-codex-review.md:6162:+            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c10-codex-review.md:6163:+            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c10-codex-review.md:6663:        // then MLService.getMethodValues for legacy api_endpoint
docs/plans/2026-05-18-c10-codex-review.md:8194:   105	        // then MLService.getMethodValues for legacy api_endpoint
docs/plans/2026-05-18-c10-codex-review.md:8730:    34	 * entirely (legacy api_endpoint-only configs that can't derive a
docs/plans/2026-05-18-c10-codex-review.md:9168:   647	            // embedding entry. (Legacy api_endpoint-only configs that
docs/plans/2026-05-18-c10-codex-review.md:9500:   364	        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:9505:   369	            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c10-codex-review.md:9607:   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
docs/plans/2026-05-18-c10-codex-review.md:9610:   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
docs/plans/2026-05-18-c10-codex-review.md:10164:   467	`api_endpoint`-only configs that `Embedding.findMatchProspects`
docs/plans/2026-05-18-c10-codex-review.md:12542:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
docs/plans/2026-05-18-c10-codex-review.md:12698:+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
docs/plans/2026-05-18-c10-codex-review.md:12816:+        if (endpoint == null) throw new IAException("null api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:12826:+            throw new IAException("api_endpoint url error: " + urlEx);
docs/plans/2026-05-18-c10-codex-review.md:12892:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:12924:+        payload.remove("api_endpoint");
docs/plans/2026-05-18-c10-codex-review.md:13708:+            urlStr = confs.get(0).optString("api_endpoint", null);
docs/plans/2026-05-18-c10-codex-review.md:14678:+            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
docs/plans/2026-05-18-c10-codex-review.md:15486:+                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c10-codex-review.md:15608:+                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
docs/plans/2026-05-18-c10-codex-review.md:16230:+        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
docs/plans/2026-05-18-c10-codex-review.md:16232:+            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c10-codex-review.md:16234:+            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c10-codex-review.md:16320:+        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
docs/plans/2026-05-18-c10-codex-review.md:16322:+            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
docs/plans/2026-05-18-c10-codex-review.md:16324:+            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
docs/plans/2026-05-18-c10-codex-review.md:17740:docs/plans/2026-05-18-c9-codex-review.md:152:EMBEDDING join entirely (legacy api_endpoint-only configs).
docs/plans/2026-05-18-c10-codex-review.md:18972:    70	        // Legacy api_endpoint-only config: gate just wants matchAgainst +
docs/plans/2026-05-18-c10-codex-review.md:19041:   138	        // Legacy api_endpoint-only config: neither method nor version is
src/test/java/org/ecocean/ia/IdentificationTest.java:51:            "{\"api_endpoint\": \"fake-mlservice-endpoint\", \"model_id\": \"method0-version0\" }"));
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:70:        // Legacy api_endpoint-only config: gate just wants matchAgainst +
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:154:            .put("api_endpoint", baseUrl())
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:189:        System.out.println("  base_url      : " + baseUrl());
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:243:        System.out.println("  base_url      : " + baseUrl());

exec
/bin/bash -lc 'git diff --check -- src/main/java/org/ecocean/ia/MatchResult.java src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'file src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java src/main/java/org/ecocean/ia/MatchResultProspect.java' in /mnt/c/Wildbook-clean2
 succeeded in 261ms:
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:     Java source, Unicode text, UTF-8 text
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java: C++ source, Unicode text, UTF-8 text
src/main/java/org/ecocean/ia/MatchResultProspect.java:              ASCII text, with CRLF line terminators

 exited 2 in 622ms:
src/main/java/org/ecocean/ia/MatchResultProspect.java:1: trailing whitespace.
+package org.ecocean.ia;
src/main/java/org/ecocean/ia/MatchResultProspect.java:2: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:3: trailing whitespace.
+import java.util.HashSet;
src/main/java/org/ecocean/ia/MatchResultProspect.java:4: trailing whitespace.
+import java.util.Set;
src/main/java/org/ecocean/ia/MatchResultProspect.java:5: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:6: trailing whitespace.
+import org.json.JSONArray;
src/main/java/org/ecocean/ia/MatchResultProspect.java:7: trailing whitespace.
+import org.json.JSONObject;
src/main/java/org/ecocean/ia/MatchResultProspect.java:8: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:9: trailing whitespace.
+import org.ecocean.Annotation;
src/main/java/org/ecocean/ia/MatchResultProspect.java:10: trailing whitespace.
+import org.ecocean.Encounter;
src/main/java/org/ecocean/ia/MatchResultProspect.java:11: trailing whitespace.
+import org.ecocean.media.MediaAsset;
src/main/java/org/ecocean/ia/MatchResultProspect.java:12: trailing whitespace.
+import org.ecocean.shepherd.core.Shepherd;
src/main/java/org/ecocean/ia/MatchResultProspect.java:13: trailing whitespace.
+import org.ecocean.Util;
src/main/java/org/ecocean/ia/MatchResultProspect.java:14: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:15: trailing whitespace.
+public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
src/main/java/org/ecocean/ia/MatchResultProspect.java:16: trailing whitespace.
+    private Annotation annotation;
src/main/java/org/ecocean/ia/MatchResultProspect.java:17: trailing whitespace.
+    private double score = 0.0d;
src/main/java/org/ecocean/ia/MatchResultProspect.java:18: trailing whitespace.
+    private String scoreType;
src/main/java/org/ecocean/ia/MatchResultProspect.java:19: trailing whitespace.
+    private MediaAsset asset;
src/main/java/org/ecocean/ia/MatchResultProspect.java:20: trailing whitespace.
+    private MatchResult matchResult;
src/main/java/org/ecocean/ia/MatchResultProspect.java:21: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:22: trailing whitespace.
+    public MatchResultProspect() {}
src/main/java/org/ecocean/ia/MatchResultProspect.java:23: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:24: trailing whitespace.
+    public MatchResultProspect(Annotation ann) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:25: trailing whitespace.
+        this();
src/main/java/org/ecocean/ia/MatchResultProspect.java:26: trailing whitespace.
+        this.annotation = ann;
src/main/java/org/ecocean/ia/MatchResultProspect.java:27: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:28: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:29: trailing whitespace.
+    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:30: trailing whitespace.
+        this();
src/main/java/org/ecocean/ia/MatchResultProspect.java:31: trailing whitespace.
+        this.annotation = ann;
src/main/java/org/ecocean/ia/MatchResultProspect.java:32: trailing whitespace.
+        this.score = score;
src/main/java/org/ecocean/ia/MatchResultProspect.java:33: trailing whitespace.
+        this.scoreType = type;
src/main/java/org/ecocean/ia/MatchResultProspect.java:34: trailing whitespace.
+        this.asset = asset;
src/main/java/org/ecocean/ia/MatchResultProspect.java:35: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:36: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:37: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/MatchResultProspect.java:38: trailing whitespace.
+     * Attach a PairX inspection MediaAsset to this prospect. Used by
src/main/java/org/ecocean/ia/MatchResultProspect.java:39: trailing whitespace.
+     * {@link MatchInspectionPairxEnricher} in Phase C to enrich
src/main/java/org/ecocean/ia/MatchResultProspect.java:40: trailing whitespace.
+     * prospects after the MatchResult has been persisted (empty-match-
src/main/java/org/ecocean/ia/MatchResultProspect.java:41: trailing whitespace.
+     * prospects design Track 2 C13: PairX is now non-blocking and
src/main/java/org/ecocean/ia/MatchResultProspect.java:42: trailing whitespace.
+     * runs without holding the outer Shepherd across HTTP).
src/main/java/org/ecocean/ia/MatchResultProspect.java:43: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/MatchResultProspect.java:44: trailing whitespace.
+    public void setAsset(MediaAsset asset) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:45: trailing whitespace.
+        this.asset = asset;
src/main/java/org/ecocean/ia/MatchResultProspect.java:46: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:47: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:48: trailing whitespace.
+    public MediaAsset getAsset() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:49: trailing whitespace.
+        return asset;
src/main/java/org/ecocean/ia/MatchResultProspect.java:50: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:51: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:52: trailing whitespace.
+    public Annotation getAnnotation() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:53: trailing whitespace.
+        return annotation;
src/main/java/org/ecocean/ia/MatchResultProspect.java:54: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:55: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:56: trailing whitespace.
+    public double getScore() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:57: trailing whitespace.
+        return score;
src/main/java/org/ecocean/ia/MatchResultProspect.java:58: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:59: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:60: trailing whitespace.
+    public String getType() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:61: trailing whitespace.
+        return scoreType;
src/main/java/org/ecocean/ia/MatchResultProspect.java:62: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:63: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:64: trailing whitespace.
+    public boolean isType(String type) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:65: trailing whitespace.
+        if (type == null) return (this.scoreType == null);
src/main/java/org/ecocean/ia/MatchResultProspect.java:66: trailing whitespace.
+        return type.equals(this.scoreType);
src/main/java/org/ecocean/ia/MatchResultProspect.java:67: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:68: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:69: trailing whitespace.
+    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:70: trailing whitespace.
+        // if we have no projects to filter on, we consider this to be in it
src/main/java/org/ecocean/ia/MatchResultProspect.java:71: trailing whitespace.
+        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
src/main/java/org/ecocean/ia/MatchResultProspect.java:72: trailing whitespace.
+        if (annotation == null) return false;
src/main/java/org/ecocean/ia/MatchResultProspect.java:73: trailing whitespace.
+        Encounter enc = annotation.findEncounter(myShepherd);
src/main/java/org/ecocean/ia/MatchResultProspect.java:74: trailing whitespace.
+        if (enc == null) return false;
src/main/java/org/ecocean/ia/MatchResultProspect.java:75: trailing whitespace.
+        return enc.isInProjects(projectIds, myShepherd);
src/main/java/org/ecocean/ia/MatchResultProspect.java:76: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:77: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:78: trailing whitespace.
+    public String toString() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:79: trailing whitespace.
+        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
src/main/java/org/ecocean/ia/MatchResultProspect.java:80: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:81: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:82: trailing whitespace.
+    public JSONObject jsonForApiGet(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:83: trailing whitespace.
+        JSONObject rtn = new JSONObject();
src/main/java/org/ecocean/ia/MatchResultProspect.java:84: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:85: trailing whitespace.
+        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
src/main/java/org/ecocean/ia/MatchResultProspect.java:86: trailing whitespace.
+        rtn.put("score", score);
src/main/java/org/ecocean/ia/MatchResultProspect.java:87: trailing whitespace.
+        // skipping scoreType since this is currently only used filtered by scoreType already
src/main/java/org/ecocean/ia/MatchResultProspect.java:88: trailing whitespace.
+        if (asset != null) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:89: trailing whitespace.
+            JSONObject aj = asset.toSimpleJSONObject();
src/main/java/org/ecocean/ia/MatchResultProspect.java:90: trailing whitespace.
+            aj.put("url", asset.webURL()); // we have no "safe" url
src/main/java/org/ecocean/ia/MatchResultProspect.java:91: trailing whitespace.
+            rtn.put("asset", aj);
src/main/java/org/ecocean/ia/MatchResultProspect.java:92: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResultProspect.java:93: trailing whitespace.
+        return rtn;
src/main/java/org/ecocean/ia/MatchResultProspect.java:94: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:95: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:96: trailing whitespace.
+    // used in sorting
src/main/java/org/ecocean/ia/MatchResultProspect.java:97: trailing whitespace.
+    @Override public int compareTo(MatchResultProspect other) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:98: trailing whitespace.
+        // we invert this so higher score is first
src/main/java/org/ecocean/ia/MatchResultProspect.java:99: trailing whitespace.
+        int comp = Double.compare(other.score, this.score);
src/main/java/org/ecocean/ia/MatchResultProspect.java:100: trailing whitespace.
+        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
src/main/java/org/ecocean/ia/MatchResultProspect.java:101: trailing whitespace.
+        // ordering (otherwise tied scores come back random order), so we use annot id
src/main/java/org/ecocean/ia/MatchResultProspect.java:102: trailing whitespace.
+        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
src/main/java/org/ecocean/ia/MatchResultProspect.java:103: trailing whitespace.
+            return this.annotation.getId().compareTo(other.annotation.getId());
src/main/java/org/ecocean/ia/MatchResultProspect.java:104: trailing whitespace.
+        // scores are *not* equal, so we just let comparison stand as-is
src/main/java/org/ecocean/ia/MatchResultProspect.java:105: trailing whitespace.
+        return comp;
src/main/java/org/ecocean/ia/MatchResultProspect.java:106: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:107: trailing whitespace.
+}

exec
/bin/bash -lc "rg -n \"boolean underRoot|underRoot\\(|checkPath\\(|ensurePath\\(\" src/main/java/org/ecocean/media/LocalAssetStore.java src/main/java/org/ecocean/media/AssetStore.java" in /mnt/c/Wildbook-clean2
 succeeded in 259ms:
src/main/java/org/ecocean/media/LocalAssetStore.java:154:            path = ensurePath(root(), passed);
src/main/java/org/ecocean/media/LocalAssetStore.java:156:            path = checkPath(root(), passed);
src/main/java/org/ecocean/media/LocalAssetStore.java:204:    public static Path checkPath(final Path root, final Path path) {
src/main/java/org/ecocean/media/LocalAssetStore.java:216:    public boolean underRoot(final Path path) {
src/main/java/org/ecocean/media/LocalAssetStore.java:218:            Path ok = checkPath(root(), path);
src/main/java/org/ecocean/media/LocalAssetStore.java:227:     * Like checkPath(), but throws an IllegalArgumentException if the resulting file doesn't exist.
src/main/java/org/ecocean/media/LocalAssetStore.java:231:    public static Path ensurePath(final Path root, final Path path) {
src/main/java/org/ecocean/media/LocalAssetStore.java:232:        Path result = checkPath(root, path);
src/main/java/org/ecocean/media/LocalAssetStore.java:301:        if (underRoot(file.toPath())) { // note: we ignore grouping here!

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/LocalAssetStore.java | sed -n '198,224p'" in /mnt/c/Wildbook-clean2
 succeeded in 249ms:
   198	
   199	    /**
   200	     * Make sure path is under the root, either passed in as a relative path or as an absolute path under the root.
   201	     *
   202	     * @return Subpath to the file relative to the root.
   203	     */
   204	    public static Path checkPath(final Path root, final Path path) {
   205	        if (path == null) throw new IllegalArgumentException("null path");
   206	        Path result = root.resolve(path);
   207	        result = root.relativize(result.normalize());
   208	        if (result.startsWith("..")) {
   209	            throw new IllegalArgumentException("Path not under given root (root=" +
   210	                    root.toString() + "; path=" + path.toString() + ")");
   211	        }
   212	        return result;
   213	    }
   214	
   215	    // a test for a Path to see if it exists under the Store's root or not
   216	    public boolean underRoot(final Path path) {
   217	        try {
   218	            Path ok = checkPath(root(), path);
   219	            if (ok != null) return true;
   220	        } catch (Exception ex) {
   221	            return false;
   222	        }
   223	        return false;
   224	    }

codex
**Findings**

- **Major, config compatibility:** PairX URL resolution still only reads `api_endpoint` from `_mlservice_conf`: [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:505). The new enricher then silently returns `null` when that resolver returns no URL: [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:177). If the locked v2 config is really `base_url` + endpoint fields, this enriches `0/N` prospects forever. The branch’s local `IA.json` still has `api_endpoint`, so this may be masked locally, but it does not match the bundle’s v2 shape.

- **Medium, race/idempotency:** The Phase C guard is serial-idempotent but not concurrent-idempotent. Two enrichers can both observe `target.getAsset() == null`, both write the same deterministic file path, both persist `MediaAsset`s, and the last commit wins the prospect reference: [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:236), [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:241), [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:249). This needs a lock/version/unique constraint or single queued worker if parallel re-fire is expected.

- **Low, repo hygiene:** `MatchResultProspect.java` is CRLF in the worktree despite being tracked LF. `git ls-files --eol` reports `i/lf w/crlf`, and `git diff --check` flags every line as trailing whitespace: [MatchResultProspect.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:1).

**Notes**

The core Shepherd boundary looks correct: Phase A/C open short transactions, and `RestClient.postJSON` is only called in Phase B without a Shepherd. For `LocalAssetStore`, the chosen relative path is compatible with `createParameters`; the main AssetStore concern is the deterministic path collision under concurrent enrichment.

Synchronous-after-close solves the DB-connection problem but still ties up the job thread. With `RestClient`’s default 300s timeout, a bad PairX endpoint can hold the worker for a long time per prospect. I’d queue it if match throughput matters, but it is not the same severity as holding Shepherd across HTTP.
tokens used
247,599
**Findings**

- **Major, config compatibility:** PairX URL resolution still only reads `api_endpoint` from `_mlservice_conf`: [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:505). The new enricher then silently returns `null` when that resolver returns no URL: [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:177). If the locked v2 config is really `base_url` + endpoint fields, this enriches `0/N` prospects forever. The branch’s local `IA.json` still has `api_endpoint`, so this may be masked locally, but it does not match the bundle’s v2 shape.

- **Medium, race/idempotency:** The Phase C guard is serial-idempotent but not concurrent-idempotent. Two enrichers can both observe `target.getAsset() == null`, both write the same deterministic file path, both persist `MediaAsset`s, and the last commit wins the prospect reference: [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:236), [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:241), [MatchInspectionPairxEnricher.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:249). This needs a lock/version/unique constraint or single queued worker if parallel re-fire is expected.

- **Low, repo hygiene:** `MatchResultProspect.java` is CRLF in the worktree despite being tracked LF. `git ls-files --eol` reports `i/lf w/crlf`, and `git diff --check` flags every line as trailing whitespace: [MatchResultProspect.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:1).

**Notes**

The core Shepherd boundary looks correct: Phase A/C open short transactions, and `RestClient.postJSON` is only called in Phase B without a Shepherd. For `LocalAssetStore`, the chosen relative path is compatible with `createParameters`; the main AssetStore concern is the deterministic path collision under concurrent enrichment.

Synchronous-after-close solves the DB-connection problem but still ties up the job thread. With `RestClient`’s default 300s timeout, a bad PairX endpoint can hold the worker for a long time per prospect. I’d queue it if match throughput matters, but it is not the same severity as holding Shepherd across HTTP.
