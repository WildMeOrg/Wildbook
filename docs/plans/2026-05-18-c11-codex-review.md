OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3ec0-4d45-7531-b61f-31891388cf9c
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

# Codex code-review: Track 2 C11 — MlServiceProcessor rewire

Final Track 2 commit. Wires MatchVisibilityGate + DeferredMatchPublisher
into MlServiceProcessor:
- Adds visibilityGate + deferredPublisher fields with a
  package-visible test-friendly constructor (Codex round-4 Medium
  testability seam).
- Replaces waitAndRunMatch with a thin wrapper calling
  waitAndRunMatchInternal (attempt=1, firstDeferredAt=null).
- waitAndRunMatchInternal: switch on GateOutcome.kind. READY →
  runMatchProspects. DEFER → enqueueDeferredMatch + ok outcome.
  GIVE_UP → log WARN + runMatchProspects (Codex round-2 #2:
  partial results > silently nothing).
- runDeferredMatch reads attempt + firstDeferredAt from jobData
  and re-gates (Codex round-2 Major: deferred match earns same
  protection).
- enqueueDeferredMatch publishes through the injected publisher
  with mlServiceV2 + deferredMatch routing flags (Codex round-5
  Blocker), increments attempt, preserves firstDeferredAt, writes
  lastGateReason.
- IAGatewayDeferredMatchPublisher: production publisher wraps
  IAGateway.requeueJob(payload, true) for the 30s delay (Codex
  round-4 Blocker: setting __queueRetries alone doesn't apply
  the delay).

## Diff

diff --git a/src/main/java/org/ecocean/ia/IA.java b/src/main/java/org/ecocean/ia/IA.java
index 56d8c05fb..c19e56e05 100644
--- a/src/main/java/org/ecocean/ia/IA.java
+++ b/src/main/java/org/ecocean/ia/IA.java
@@ -1,690 +1,690 @@
-/*
-    idea here is "simple": a gateway to all IA calls, mostly (now) cleaned up from identity/IBEISIA.java (guessing)
-
-    THIS IS A WORK-IN-PROGRESS
-
-    proposed key concepts:
- * can handle multiple IA frameworks (not just historic-IBEIS)
-      - likely a base abstract class with a "isEnabled() / init()" concept
-      - classes would allow for instances of each IA framework?
-
- * no idea how to handle crazy (and configurable!?) workflow!
-
- * probably should "leverage" Queue stuff where applicable?
-      - possibly there is a NEED for both variations (as suggested by drew): an asynchronous (queued) and synchronous (not)
-
- * simply entry point for: MediaAsset and Annotation???
- */
-package org.ecocean.ia;
-
-import java.io.PrintWriter;
-import java.util.ArrayList;
-import java.util.Arrays;
-import java.util.HashMap;
-import java.util.Iterator;
-import java.util.List;
-import java.util.Map;
-import java.util.Properties;
-import javax.servlet.http.HttpServletRequest;
-import javax.servlet.http.HttpServletResponse;
-import org.ecocean.Annotation;
-import org.ecocean.CommonConfiguration;
-import org.ecocean.Embedding;
-import org.ecocean.Encounter;
-import org.ecocean.identity.IBEISIA;
-import org.ecocean.IAJsonProperties;
-import org.ecocean.media.MediaAsset;
-import org.ecocean.media.MediaAssetFactory;
-import org.ecocean.servlet.ServletUtilities;
-import org.ecocean.shepherd.core.Shepherd;
-import org.ecocean.shepherd.core.ShepherdProperties;
-import org.ecocean.Taxonomy;
-import org.ecocean.Util;
-import org.json.JSONArray;
-import org.json.JSONObject;
-
-public class IA {
-    private static final String PROP_FILE = "IA.properties";
-
-    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
-        - these should be treated as different in that an IA framework might batch together the list in some way (i.e. difference between sending as
-           list vs iterating over list with intake(each element)
-        - you only get one task ID for the list/group, is this a bad idea?
-     */
-    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
-        return intakeMediaAssets(myShepherd, new ArrayList<MediaAsset>(Arrays.asList(ma)));
-    }
-
-    // Annotations *may or may not* already be on an Encounter  #neverforget
-    public static Task intake(Shepherd myShepherd, Annotation ann) {
-        return intakeAnnotations(myShepherd, new ArrayList<Annotation>(Arrays.asList(ann)));
-    }
-
-/*  these have same erasure types so cant co-exist. :( another reason for a common baseclass.. sigh?
-    hence the overly-inclusive Object version below!
-    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
-        if ((mas == null) || (mas.size() < 1)) return null;
-        Task task = new Task();
-        return task;
-    }
-    public static Task intake(Shepherd myShepherd, List<Annotation> anns) {
-        if ((anns == null) || (anns.size() < 1)) return null;
-        Task task = new Task();
-        return task;
-    }
- */
-
-    // i think objects ingested here must(?) be persisted (and committed), as we have to assume (or we know)
-    // that these processes will use queues which operate in different (Shepherd) threads and will thus try
-    // to find the objects via the db.  :/
-    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
-    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
-        return intakeMediaAssets(myShepherd, mas, null);
-    }
-
-    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
-        Task parentTask) {
-        List<List<MediaAsset> > assetsBySpecies = binAssetsBySpecies(mas, myShepherd);
-        int numSpecies = assetsBySpecies.size();
-
-        // System.out.println("IA.java.numSpecies="+numSpecies);
-        // in one-species case we don't need to create an extra layer of tasks
-        if (numSpecies == 1)
-            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
-        // in multi-species case we make sure we have a parent task and add each species task as a child
-        if (parentTask == null) parentTask = new Task();
-        for (List<MediaAsset> masOneSpecies : assetsBySpecies) {
-            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
-            parentTask.addChild(thisTask);
-        }
-        return parentTask;
-    }
-
-    public static List<List<MediaAsset> > binAssetsBySpecies(List<MediaAsset> mas,
-        Shepherd myShepherd) {
-        Map<String, List<MediaAsset> > assetsBySpecies = new HashMap<String, List<MediaAsset> >();
-
-        for (MediaAsset ma : mas) {
-            Taxonomy taxy = ma.getTaxonomy(myShepherd);
-            String scientificName = "null";
-            if (taxy != null && taxy.getScientificName() != null)
-                scientificName = taxy.getScientificName();
-            // System.out.println("     MA ID "+ma.getId()+" has taxy "+scientificName);
-            if (!assetsBySpecies.containsKey(scientificName))
-                assetsBySpecies.put(scientificName, new ArrayList<MediaAsset>());
-            assetsBySpecies.get(scientificName).add(ma);
-            // System.out.println("       Taxy size: "+assetsBySpecies.get(scientificName).size());
-        }
-        return new ArrayList<List<MediaAsset> >(assetsBySpecies.values());
-    }
-
-    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
-        final Task parentTask) {
-        if ((mas == null) || (mas.size() < 1)) return null;
-        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
-        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
-    }
-
-    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
-        Taxonomy taxy, final Task parentTask) {
-        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
-    }
-
-    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
-        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
-        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
-            " media assets:");
-        handleMissingAcmids(mas, myShepherd);
-        for (MediaAsset ma : mas) {
-            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
-            System.out.println("acmid is: " + ma.getAcmId());
-        }
-        JSONArray maArr = new JSONArray();
-        for (MediaAsset ma : mas) {
-            maArr.put(ma.getId());
-        }
-        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());
-
-        Task topTask = new Task();
-        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
-        topTask.setObjectMediaAssets(mas);
-        myShepherd.storeNewTask(topTask);
-
-        String context = myShepherd.getContext();
-        String baseUrl = getBaseURL(context);
-
-        // Ia configs are keyed off taxonomies
-        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
-
-        // Migration plan v2 §commit #10b: routing reroute.
-        // If the species' _id_conf.default.pipeline_root is "vector" AND
-        // _mlservice_conf is configured, route per-asset through the
-        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
-        // WBIA path below — production deployments without _mlservice_conf
-        // see no behavior change at all.
-        //
-        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
-        // child finalization is local; no first-finisher-wins. The topTask
-        // remains as the aggregator for the caller contract (and so legacy
-        // summary code that reads topTask.objectMediaAssets keeps working).
-        if (iaConfig != null && taxy != null &&
-            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
-            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
-                context, baseUrl);
-        }
-
-        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
-        JSONObject dj = new JSONObject();
-        dj.put("mediaAssetIds", maArr);
-        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
-        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
-        Boolean[] sent = new Boolean[numDetectAlgos];
-        for (int i = 0; i < numDetectAlgos; i++) {
-            // task for this job (only create new (child) tasks if multiple detect algos)
-            Task task = (numDetectAlgos == 1) ? topTask : new Task();
-            task.setObjectMediaAssets(mas);
-            task.setParameters(topTask.getParameters());
-
-            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
-            task.addParameter("detectArgs", detectArgs);
-
-            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
-            task.addParameter("__detect_url", detectionUrl);
-
-            JSONObject qjob = new JSONObject();
-            qjob.put("detect", dj);
-            qjob.put("__detect_args", detectArgs);
-            qjob.put("__detect_url", detectionUrl);
-            // task is queued here
-            qjob.put("taskId", topTask.getId());
-            qjob.put("__context", context);
-            qjob.put("__baseUrl", baseUrl);
-            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
-            if (tweetAssetId != -1) {
-                qjob.put("tweetAssetId", tweetAssetId);
-            }
-            System.out.println(
-                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
-            sent[i] = false;
-            try {
-                // job is queued here
-                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
-                    qjob.toString());
-            } catch (java.io.IOException iox) {
-                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
-                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
-                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
-                    iox.toString());
-            }
-        }
-        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
-            " assets; queued? = " + sent + "; " + topTask);
-        return topTask;
-    }
-
-    /**
-     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
-     * vector pipeline. Each MediaAsset gets its own child Task under
-     * topTask; each emits a {@code mlServiceV2:true} payload to the
-     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
-     * picks them up via the IAGateway dispatcher (commit #10a).
-     *
-     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
-     * topTask. The topTask itself remains as the aggregator that holds the
-     * full MediaAsset list for caller-side summary code.</p>
-     *
-     * <p>encounterId is derived best-effort from the MediaAsset's existing
-     * trivial annotation (every Encounter.addMediaAsset call creates one).
-     * If null, MlServiceProcessor persists annotations without explicit
-     * Encounter linkage and downstream MediaAsset.assignEncounters handles
-     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
-     */
-    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
-        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
-        int queued = 0;
-        for (MediaAsset ma : mas) {
-            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
-                queued++;
-            }
-        }
-        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
-            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
-        return topTask;
-    }
-
-    /**
-     * Build and enqueue one v2 ml-service job for a single MediaAsset.
-     * Returns {@code true} iff the FileQueue write succeeded.
-     *
-     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
-     * normal intake path) and the startup stale-mlservice reconciler in
-     * {@code StartupWildbook}. The reconciler relies on the boolean
-     * return to decide whether to commit accompanying state changes; the
-     * normal intake path tolerates the swallowed-failure behavior.</p>
-     *
-     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
-     * internally commits/reopens the transaction, so the child Task row
-     * is persisted before this method enqueues. On enqueue failure the
-     * child Task remains in the DB as an orphan — there is no queued
-     * job that will ever drive it. The orphan IS still discoverable
-     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
-     * the task references the MediaAsset through objectMediaAssets),
-     * so it may surface in operator-facing task listings until cleaned
-     * up by an out-of-band path. Callers that need cleanup should
-     * delete the orphan explicitly; the default posture here is to
-     * accept it since FileQueue write failures are rare.</p>
-     *
-     * <p>If {@code topTask} is null a fresh root task is created inside
-     * this method. This matches the reconciler's use case where there is
-     * no caller-side aggregator umbrella.</p>
-     */
-    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
-        Taxonomy taxy, Task topTask, String context, String baseUrl) {
-        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
-        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
-        singleton.add(ma);
-        childTask.setObjectMediaAssets(singleton);
-        myShepherd.storeNewTask(childTask);
-
-        // Best-effort encounterId via existing annotations on the MA.
-        String encounterId = null;
-        ArrayList<Annotation> existing = ma.getAnnotations();
-        if (existing != null) {
-            for (Annotation a : existing) {
-                Encounter enc = a.findEncounter(myShepherd);
-                if (enc != null) {
-                    encounterId = enc.getId();
-                    break;
-                }
-            }
-        }
-
-        JSONObject qjob = new JSONObject();
-        qjob.put("mlServiceV2", true);
-        qjob.put("mediaAssetId", ma.getId());
-        qjob.put("taxonomyString", taxy.getScientificName());
-        qjob.put("taskId", childTask.getId());
-        qjob.put("__context", context);
-        qjob.put("__baseUrl", baseUrl);
-        if (Util.stringExists(encounterId)) {
-            qjob.put("encounterId", encounterId);
-        }
-
-        try {
-            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
-        } catch (java.io.IOException iox) {
-            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
-                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
-            return false;
-        }
-    }
-
-    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
-        int count = 0;
-        int stopAfter = 200000;
-        int batchThreshold = 50;
-        int batchesSoFar = 0;
-        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();
-
-        try {
-            for (MediaAsset ma : mediaAssets) {
-                count++;
-                if (count > stopAfter) {
-                    break;
-                }
-                if (ma != null && !ma.hasAcmId()) {
-                    assetsWithMissingAcmids.add(ma);
-                }
-                if ((assetsWithMissingAcmids.size() >= batchThreshold) ||
-                    count == mediaAssets.size()) {
-                    if (assetsWithMissingAcmids.size() > 0) { // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this
-                        try {
-                            IBEISIA.sendMediaAssetsNew(assetsWithMissingAcmids,
-                                myShepherd.getContext());
-                        } catch (Exception e) {
-                            System.out.println(
-                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
-                            e.printStackTrace();
-                        }
-                    }
-                    batchesSoFar++;
-                    assetsWithMissingAcmids = new ArrayList<MediaAsset>();
-                    myShepherd.updateDBTransaction();
-                }
-            }
-        } catch (Exception e) {
-            System.out.println("Error in handleMissingAcmids in IA.java");
-            e.printStackTrace();
-            myShepherd.rollbackDBTransaction();
-        }
-    }
-
-    // similar behavior to above: basically fake /ia api call, but via queue
-    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
-    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
-        return intakeAnnotations(myShepherd, anns, null, false);
-    }
-
-    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
-        final Task parentTask, boolean fastlane) {
-        // List<List<Annotation>> annses = binAnnotsByIaClass(anns);
-        //// slightly complicated bc we need to create child tasks only if there are multiple iaClasses
-        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
-        //// here we make child tasks
-        // Task topTask = (parentTask==null) ? new Task() : parentTask;
-        // for (List<Annotation> annsOneIaClass: annses) {
-        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
-        // }
-        // return topTask;
-        // }
-        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
-        // System.out.println("Starting intakeAnnotations");
-        if ((anns == null) || (anns.size() < 1)) return null;
-        Task topTask = new Task();
-        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
-        topTask.setObjectAnnotations(anns);
-        String context = myShepherd.getContext();
-
-        /*
-            what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed for this we use
-               IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than one we need to make a set of subtasks
-         */
-
-/*
-        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
-           List<JSONObject> opts = null;
-        // below gets it working for dolphins but can be generalized easily from IA.properties String inferredIaClass =
-           IBEISIA.inferIaClass(anns.get(0), myShepherd);
-        String bottlenose = "dolphin_bottlenose_fin";
-        if (bottlenose.equals(iaClass) || bottlenose.equals(inferredIaClass)) {
-            System.out.println("IA.java is sending a Tursiops truncatus job");
-            opts = IBEISIA.identOpts(context, bottlenose);
-        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes opts = IBEISIA.identOpts(context);
-        }
- */
-        // List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
-        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
-        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
-        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
-            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
-            // now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
-            if (opts != null) {
-                Iterator<JSONObject> itr = opts.iterator();
-                while (itr.hasNext()) {
-                    if (!itr.next().optBoolean("default", true)) itr.remove();
-                }
-            }
-            System.out.println("identOpts: " + opts);
-            List<Task> tasks = new ArrayList<Task>();
-            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
-            if (parentTask != null && parentTask.getParameters() != null) {
-                newTaskParams = parentTask.getParameters();
-                System.out.println("newTaskParams: " + newTaskParams.toString());
-                if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
-                    JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
-                    System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
-                    ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
-                    int maLength = matchingAlgorithms.length();
-                    for (int y = 0; y < maLength; y++) {
-                        newOpts.add(matchingAlgorithms.getJSONObject(y));
-                    }
-                    System.out.println("matchingAlgorithms2: " + newOpts.toString());
-                    if (newOpts.size() > 0) {
-                        opts = newOpts;
-                        System.out.println("Swapping opts for newOpts!!");
-                    }
-                }
-            }
-            if ((opts == null) || (opts.size() < 1)) continue; // no ID for this iaClass.
-            // just one IA class, one algorithm case
-            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
-                newTaskParams.put("ibeis.identification",
-                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
-                topTask.setParameters(newTaskParams);
-                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
-            } else {
-                for (int i = 0; i < opts.size(); i++) {
-                    Task t = new Task();
-                    t.setObjectAnnotations(annsOneIAClass);
-                    newTaskParams.put("ibeis.identification",
-                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
-                    t.setParameters(newTaskParams);
-                    topTask.addChild(t);
-                    tasks.add(t);
-                }
-            }
-            newTaskParams.put("fastlane", fastlane);
-            if (fastlane) newTaskParams.put("lane", "fast");
-            myShepherd.storeNewTask(topTask);
-
-            // these are re-used in every task
-            JSONArray annArr = new JSONArray();
-            for (Annotation ann : annsOneIAClass) {
-                annArr.put(ann.getId());
-            }
-            JSONObject aj = new JSONObject();
-            aj.put("annotationIds", annArr);
-            String baseUrl = getBaseURL(context);
-            for (int i = 0; i < opts.size(); i++) {
-                // if this is a vector-based matching option, this will just do the job and be done
-                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
-                JSONObject qjob = new JSONObject();
-                qjob.put("identify", aj);
-                qjob.put("taskId", tasks.get(i).getId());
-                qjob.put("__context", context);
-                qjob.put("__baseUrl", baseUrl);
-                if (opts.get(i) != null) qjob.put("opt", opts.get(i));
-                boolean sent = false;
-                try {
-                    if (fastlane) {
-                        // if fastlane and a smaller, bespoke request, get this into the faster queue
-                        qjob.put("fastlane", fastlane);
-                        qjob.put("lane", "fast");
-                        tasks.get(i).setQueueResumeMessage(qjob.toString());
-                        sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
-                            qjob.toString());
-                    } else {
-                        tasks.get(i).setQueueResumeMessage(qjob.toString());
-                        sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
-                    }
-                } catch (java.io.IOException iox) {
-                    System.out.println("ERROR[" + i +
-                        "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
-                }
-                System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " +
-                    annsOneIAClass.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
-            }
-        } // end for each iaClass
-        System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
-        return topTask;
-    }
-
-    public static List<List<Annotation> > binAnnotsByIaClass(List<Annotation> anns) {
-        System.out.println("binAnnotsByIaClass called on " + anns.size() + " annots");
-        Map<String, List<Annotation> > iaClassToAnns = new HashMap<String, List<Annotation> >();
-        for (Annotation ann : anns) {
-            String iaClass = ann.getIAClass();
-            if (iaClass == null) continue;
-            List<Annotation> iaClassList = iaClassToAnns.getOrDefault(iaClass,
-                new ArrayList<Annotation>());
-            iaClassList.add(ann);
-            iaClassToAnns.put(iaClass, iaClassList);
-        }
-        System.out.println("binAnnotsByIaClass binned them into " + iaClassToAnns.keySet().size() +
-            " bins: " + iaClassToAnns.keySet());
-        return new ArrayList<List<Annotation> >(iaClassToAnns.values());
-    }
-
-    // possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
-    // note: this is processed *from the queue* and as such does not have "output"
-    public static void handleRest(JSONObject jin) {
-        System.out.println("JIN JIN JIN: " + jin);
-        if (jin == null) return;
-        String context = jin.optString("__context", null);
-        if (context == null)
-            throw new RuntimeException("IA.handleRest(): passed data has no __context");
-        Shepherd myShepherd = new Shepherd(context);
-
-        // check if these should be directed through the fastlane
-        boolean fastlane = false;
-        if (jin.optBoolean("fastlane", false)) { fastlane = true; }
-        myShepherd.setAction("IA.handleRest");
-        myShepherd.beginDBTransaction();
-        try {
-            String taskId = jin.optString("taskId", Util.generateUUID());
-            Task topTask = Task.load(taskId, myShepherd);
-            if (topTask == null) topTask = new Task(taskId);
-            if (fastlane) topTask.addParameter("fastlane", true);
-            myShepherd.storeNewTask(topTask);
-            JSONObject opt = jin.optJSONObject("opt"); // should use this to decide how to branch differently than "default"
-            JSONArray mlist = jin.optJSONArray("mediaAssetIds");
-            if ((mlist != null) && (mlist.length() > 0)) {
-                System.out.println("MLIST: " + mlist);
-                List<MediaAsset> mas = new ArrayList<MediaAsset>();
-                for (int i = 0; i < mlist.length(); i++) {
-                    int mid = mlist.optInt(i, -1);
-                    if (mid < 1) continue;
-                    MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
-                    System.out.println(i + " -> " + ma);
-                    if (ma == null) continue;
-                    mas.add(ma);
-                }
-                Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
-                System.out.println("INFO: IA.handleRest() just intook MediaAssets as " + mtask +
-                    " for (parent) " + topTask);
-                topTask.addChild(mtask);
-            }
-            JSONArray alist = jin.optJSONArray("annotationIds");
-            if ((alist != null) && (alist.length() > 0)) {
-                List<Annotation> anns = new ArrayList<Annotation>();
-                for (int i = 0; i < alist.length(); i++) {
-                    String aid = alist.optString(i, null);
-                    if (aid == null) continue;
-                    Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
-                        myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
-                    if (ann == null) continue;
-                    anns.add(ann);
-                }
-                // okay, if we are sending another ID job from the hburger menu, the media asset needs to be added to your top level 'root' task,
-                // or else you will link to the original root task
-                List<MediaAsset> masForNewRoot = new ArrayList<>();
-                for (Annotation ann : anns) {
-                    MediaAsset ma = ann.getMediaAsset();
-                    if (ma != null && !masForNewRoot.contains(ma)) {
-                        masForNewRoot.add(ma);
-                    }
-                }
-                // i cant think of a scenario where we would get here and accidently double-add mas... but jic
-                for (MediaAsset ma : masForNewRoot) {
-                    if (!topTask.getObjectMediaAssets().contains(ma)) {
-                        topTask.addObject(ma);
-                    }
-                }
-                Task atask = intakeAnnotations(myShepherd, anns, topTask, fastlane);
-                System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask +
-                    " for " + topTask);
-                myShepherd.getPM().refresh(topTask);
-                topTask.addChild(atask);
-                topTask.setModified();
-                myShepherd.getPM().makePersistent(atask);
-            }
-            myShepherd.commitDBTransaction();
-        } catch (Exception e) {
-            e.printStackTrace();
-            myShepherd.rollbackDBTransaction();
-        } finally {
-            myShepherd.closeDBTransaction();
-        }
-    }
-
-    // via IAGateway servlet, we handle the work
-    public static void handleGet(HttpServletRequest request, HttpServletResponse response)
-    throws java.io.IOException {
-        // JSONObject rtn = queueCallback(request);
-        JSONObject rtn = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
-        String context = ServletUtilities.getContext(request);
-        Shepherd myShepherd = new Shepherd(context);
-
-        myShepherd.setAction("IA.handleGet");
-        myShepherd.beginDBTransaction();
-        String taskId = request.getParameter("taskId");
-        if (taskId != null) {
-            Task task = Task.load(taskId, myShepherd);
-            if (task == null) {
-                response.sendError(404, "Not found: taskId=" + taskId);
-                myShepherd.rollbackDBTransaction();
-                myShepherd.closeDBTransaction();
-                return;
-            }
-            rtn.put("success", true);
-            rtn.remove("error");
-            rtn.put("task",
-                task.toJSONObject(Util.requestParameterSet(request.getParameter(
-                    "includeChildren"))));
-        }
-        response.setContentType("text/plain");
-        PrintWriter out = response.getWriter();
-        out.println(rtn.toString());
-        out.close();
-        myShepherd.rollbackDBTransaction();
-        myShepherd.closeDBTransaction();
-        return;
-    }
-
-    public static String getBaseURL(String context) {
-        String url = CommonConfiguration.getServerURL(context);
-        String containerName = CommonConfiguration.getProperty("containerName", context);
-
-        if (containerName != null && !"".equals(containerName)) {
-            containerName = containerName.trim();
-            System.out.println("INFO: Wildbook is containerized: Server getBaseURL is returning: " +
-                containerName + "");
-            url = url.replace("localhost", containerName);
-        }
-        System.out.println("INFO: Server getBaseURL is returning " + url);
-        return url;
-    }
-
-    // (optional!) Taxonomy will append "_Scientific_name" to label and try that.  if not available, then try just label.
-    public static String getProperty(String context, String label, Taxonomy tax, String def) {
-        if ((tax != null) && (tax.getScientificName() != null)) {
-            String propKey = label + "_".concat(tax.getScientificName()).replaceAll(" ", "_");
-            System.out.println("[INFO] IA.getProperty() using propKey=" + propKey + " based on " +
-                tax);
-            String val = getProperty(context, propKey, (String)null);
-            if (val != null) return val;
-        }
-        return IA.getProperty(context, label, def);
-    }
-
-    public static String getProperty(String context, String label, Taxonomy tax) { // no-default version
-        return getProperty(context, label, tax, null);
-    }
-
-    public static String getProperty(String context, String label) { // no-default, no-taxonomy
-        return getProperty(context, label, (String)null);
-    }
-
-    public static String getProperty(String context, String label, String def) {
-        Properties p = getProperties(context);
-
-        if (p == null) {
-            System.out.println("IA.getProperty(" + label +
-                ") has no properties; IA.properties unavailable?");
-            return null;
-        }
-        return p.getProperty(label, def);
-    }
-
-    private static Properties getProperties(String context) {
-        try {
-            return ShepherdProperties.getProperties(PROP_FILE, "", context);
-        } catch (Exception ex) {
-            return null;
-        }
-    }
-
-    public static void log(String msg) {
-        System.out.println(new org.joda.time.DateTime() + " " + msg);
-    }
-}
+/*
+    idea here is "simple": a gateway to all IA calls, mostly (now) cleaned up from identity/IBEISIA.java (guessing)
+
+    THIS IS A WORK-IN-PROGRESS
+
+    proposed key concepts:
+ * can handle multiple IA frameworks (not just historic-IBEIS)
+      - likely a base abstract class with a "isEnabled() / init()" concept
+      - classes would allow for instances of each IA framework?
+
+ * no idea how to handle crazy (and configurable!?) workflow!
+
+ * probably should "leverage" Queue stuff where applicable?
+      - possibly there is a NEED for both variations (as suggested by drew): an asynchronous (queued) and synchronous (not)
+
+ * simply entry point for: MediaAsset and Annotation???
+ */
+package org.ecocean.ia;
+
+import java.io.PrintWriter;
+import java.util.ArrayList;
+import java.util.Arrays;
+import java.util.HashMap;
+import java.util.Iterator;
+import java.util.List;
+import java.util.Map;
+import java.util.Properties;
+import javax.servlet.http.HttpServletRequest;
+import javax.servlet.http.HttpServletResponse;
+import org.ecocean.Annotation;
+import org.ecocean.CommonConfiguration;
+import org.ecocean.Embedding;
+import org.ecocean.Encounter;
+import org.ecocean.identity.IBEISIA;
+import org.ecocean.IAJsonProperties;
+import org.ecocean.media.MediaAsset;
+import org.ecocean.media.MediaAssetFactory;
+import org.ecocean.servlet.ServletUtilities;
+import org.ecocean.shepherd.core.Shepherd;
+import org.ecocean.shepherd.core.ShepherdProperties;
+import org.ecocean.Taxonomy;
+import org.ecocean.Util;
+import org.json.JSONArray;
+import org.json.JSONObject;
+
+public class IA {
+    private static final String PROP_FILE = "IA.properties";
+
+    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
+        - these should be treated as different in that an IA framework might batch together the list in some way (i.e. difference between sending as
+           list vs iterating over list with intake(each element)
+        - you only get one task ID for the list/group, is this a bad idea?
+     */
+    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
+        return intakeMediaAssets(myShepherd, new ArrayList<MediaAsset>(Arrays.asList(ma)));
+    }
+
+    // Annotations *may or may not* already be on an Encounter  #neverforget
+    public static Task intake(Shepherd myShepherd, Annotation ann) {
+        return intakeAnnotations(myShepherd, new ArrayList<Annotation>(Arrays.asList(ann)));
+    }
+
+/*  these have same erasure types so cant co-exist. :( another reason for a common baseclass.. sigh?
+    hence the overly-inclusive Object version below!
+    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
+        if ((mas == null) || (mas.size() < 1)) return null;
+        Task task = new Task();
+        return task;
+    }
+    public static Task intake(Shepherd myShepherd, List<Annotation> anns) {
+        if ((anns == null) || (anns.size() < 1)) return null;
+        Task task = new Task();
+        return task;
+    }
+ */
+
+    // i think objects ingested here must(?) be persisted (and committed), as we have to assume (or we know)
+    // that these processes will use queues which operate in different (Shepherd) threads and will thus try
+    // to find the objects via the db.  :/
+    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
+    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
+        return intakeMediaAssets(myShepherd, mas, null);
+    }
+
+    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
+        Task parentTask) {
+        List<List<MediaAsset> > assetsBySpecies = binAssetsBySpecies(mas, myShepherd);
+        int numSpecies = assetsBySpecies.size();
+
+        // System.out.println("IA.java.numSpecies="+numSpecies);
+        // in one-species case we don't need to create an extra layer of tasks
+        if (numSpecies == 1)
+            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
+        // in multi-species case we make sure we have a parent task and add each species task as a child
+        if (parentTask == null) parentTask = new Task();
+        for (List<MediaAsset> masOneSpecies : assetsBySpecies) {
+            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
+            parentTask.addChild(thisTask);
+        }
+        return parentTask;
+    }
+
+    public static List<List<MediaAsset> > binAssetsBySpecies(List<MediaAsset> mas,
+        Shepherd myShepherd) {
+        Map<String, List<MediaAsset> > assetsBySpecies = new HashMap<String, List<MediaAsset> >();
+
+        for (MediaAsset ma : mas) {
+            Taxonomy taxy = ma.getTaxonomy(myShepherd);
+            String scientificName = "null";
+            if (taxy != null && taxy.getScientificName() != null)
+                scientificName = taxy.getScientificName();
+            // System.out.println("     MA ID "+ma.getId()+" has taxy "+scientificName);
+            if (!assetsBySpecies.containsKey(scientificName))
+                assetsBySpecies.put(scientificName, new ArrayList<MediaAsset>());
+            assetsBySpecies.get(scientificName).add(ma);
+            // System.out.println("       Taxy size: "+assetsBySpecies.get(scientificName).size());
+        }
+        return new ArrayList<List<MediaAsset> >(assetsBySpecies.values());
+    }
+
+    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
+        final Task parentTask) {
+        if ((mas == null) || (mas.size() < 1)) return null;
+        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
+        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
+    }
+
+    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
+        Taxonomy taxy, final Task parentTask) {
+        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
+    }
+
+    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
+        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
+        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
+            " media assets:");
+        handleMissingAcmids(mas, myShepherd);
+        for (MediaAsset ma : mas) {
+            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
+            System.out.println("acmid is: " + ma.getAcmId());
+        }
+        JSONArray maArr = new JSONArray();
+        for (MediaAsset ma : mas) {
+            maArr.put(ma.getId());
+        }
+        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());
+
+        Task topTask = new Task();
+        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
+        topTask.setObjectMediaAssets(mas);
+        myShepherd.storeNewTask(topTask);
+
+        String context = myShepherd.getContext();
+        String baseUrl = getBaseURL(context);
+
+        // Ia configs are keyed off taxonomies
+        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
+
+        // Migration plan v2 §commit #10b: routing reroute.
+        // If the species' _id_conf.default.pipeline_root is "vector" AND
+        // _mlservice_conf is configured, route per-asset through the
+        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
+        // WBIA path below — production deployments without _mlservice_conf
+        // see no behavior change at all.
+        //
+        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
+        // child finalization is local; no first-finisher-wins. The topTask
+        // remains as the aggregator for the caller contract (and so legacy
+        // summary code that reads topTask.objectMediaAssets keeps working).
+        if (iaConfig != null && taxy != null &&
+            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
+            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
+                context, baseUrl);
+        }
+
+        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
+        JSONObject dj = new JSONObject();
+        dj.put("mediaAssetIds", maArr);
+        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
+        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
+        Boolean[] sent = new Boolean[numDetectAlgos];
+        for (int i = 0; i < numDetectAlgos; i++) {
+            // task for this job (only create new (child) tasks if multiple detect algos)
+            Task task = (numDetectAlgos == 1) ? topTask : new Task();
+            task.setObjectMediaAssets(mas);
+            task.setParameters(topTask.getParameters());
+
+            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
+            task.addParameter("detectArgs", detectArgs);
+
+            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
+            task.addParameter("__detect_url", detectionUrl);
+
+            JSONObject qjob = new JSONObject();
+            qjob.put("detect", dj);
+            qjob.put("__detect_args", detectArgs);
+            qjob.put("__detect_url", detectionUrl);
+            // task is queued here
+            qjob.put("taskId", topTask.getId());
+            qjob.put("__context", context);
+            qjob.put("__baseUrl", baseUrl);
+            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
+            if (tweetAssetId != -1) {
+                qjob.put("tweetAssetId", tweetAssetId);
+            }
+            System.out.println(
+                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
+            sent[i] = false;
+            try {
+                // job is queued here
+                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
+                    qjob.toString());
+            } catch (java.io.IOException iox) {
+                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
+                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
+                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
+                    iox.toString());
+            }
+        }
+        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
+            " assets; queued? = " + sent + "; " + topTask);
+        return topTask;
+    }
+
+    /**
+     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
+     * vector pipeline. Each MediaAsset gets its own child Task under
+     * topTask; each emits a {@code mlServiceV2:true} payload to the
+     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
+     * picks them up via the IAGateway dispatcher (commit #10a).
+     *
+     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
+     * topTask. The topTask itself remains as the aggregator that holds the
+     * full MediaAsset list for caller-side summary code.</p>
+     *
+     * <p>encounterId is derived best-effort from the MediaAsset's existing
+     * trivial annotation (every Encounter.addMediaAsset call creates one).
+     * If null, MlServiceProcessor persists annotations without explicit
+     * Encounter linkage and downstream MediaAsset.assignEncounters handles
+     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
+     */
+    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
+        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
+        int queued = 0;
+        for (MediaAsset ma : mas) {
+            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
+                queued++;
+            }
+        }
+        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
+            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
+        return topTask;
+    }
+
+    /**
+     * Build and enqueue one v2 ml-service job for a single MediaAsset.
+     * Returns {@code true} iff the FileQueue write succeeded.
+     *
+     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
+     * normal intake path) and the startup stale-mlservice reconciler in
+     * {@code StartupWildbook}. The reconciler relies on the boolean
+     * return to decide whether to commit accompanying state changes; the
+     * normal intake path tolerates the swallowed-failure behavior.</p>
+     *
+     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
+     * internally commits/reopens the transaction, so the child Task row
+     * is persisted before this method enqueues. On enqueue failure the
+     * child Task remains in the DB as an orphan — there is no queued
+     * job that will ever drive it. The orphan IS still discoverable
+     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
+     * the task references the MediaAsset through objectMediaAssets),
+     * so it may surface in operator-facing task listings until cleaned
+     * up by an out-of-band path. Callers that need cleanup should
+     * delete the orphan explicitly; the default posture here is to
+     * accept it since FileQueue write failures are rare.</p>
+     *
+     * <p>If {@code topTask} is null a fresh root task is created inside
+     * this method. This matches the reconciler's use case where there is
+     * no caller-side aggregator umbrella.</p>
+     */
+    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
+        Taxonomy taxy, Task topTask, String context, String baseUrl) {
+        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
+        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
+        singleton.add(ma);
+        childTask.setObjectMediaAssets(singleton);
+        myShepherd.storeNewTask(childTask);
+
+        // Best-effort encounterId via existing annotations on the MA.
+        String encounterId = null;
+        ArrayList<Annotation> existing = ma.getAnnotations();
+        if (existing != null) {
+            for (Annotation a : existing) {
+                Encounter enc = a.findEncounter(myShepherd);
+                if (enc != null) {
+                    encounterId = enc.getId();
+                    break;
+                }
+            }
+        }
+
+        JSONObject qjob = new JSONObject();
+        qjob.put("mlServiceV2", true);
+        qjob.put("mediaAssetId", ma.getId());
+        qjob.put("taxonomyString", taxy.getScientificName());
+        qjob.put("taskId", childTask.getId());
+        qjob.put("__context", context);
+        qjob.put("__baseUrl", baseUrl);
+        if (Util.stringExists(encounterId)) {
+            qjob.put("encounterId", encounterId);
+        }
+
+        try {
+            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
+        } catch (java.io.IOException iox) {
+            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
+                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
+            return false;
+        }
+    }
+
+    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
+        int count = 0;
+        int stopAfter = 200000;
+        int batchThreshold = 50;
+        int batchesSoFar = 0;
+        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();
+
+        try {
+            for (MediaAsset ma : mediaAssets) {
+                count++;
+                if (count > stopAfter) {
+                    break;
+                }
+                if (ma != null && !ma.hasAcmId()) {
+                    assetsWithMissingAcmids.add(ma);
+                }
+                if ((assetsWithMissingAcmids.size() >= batchThreshold) ||
+                    count == mediaAssets.size()) {
+                    if (assetsWithMissingAcmids.size() > 0) { // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this
+                        try {
+                            IBEISIA.sendMediaAssetsNew(assetsWithMissingAcmids,
+                                myShepherd.getContext());
+                        } catch (Exception e) {
+                            System.out.println(
+                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
+                            e.printStackTrace();
+                        }
+                    }
+                    batchesSoFar++;
+                    assetsWithMissingAcmids = new ArrayList<MediaAsset>();
+                    myShepherd.updateDBTransaction();
+                }
+            }
+        } catch (Exception e) {
+            System.out.println("Error in handleMissingAcmids in IA.java");
+            e.printStackTrace();
+            myShepherd.rollbackDBTransaction();
+        }
+    }
+
+    // similar behavior to above: basically fake /ia api call, but via queue
+    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
+    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
+        return intakeAnnotations(myShepherd, anns, null, false);
+    }
+
+    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
+        final Task parentTask, boolean fastlane) {
+        // List<List<Annotation>> annses = binAnnotsByIaClass(anns);
+        //// slightly complicated bc we need to create child tasks only if there are multiple iaClasses
+        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
+        //// here we make child tasks
+        // Task topTask = (parentTask==null) ? new Task() : parentTask;
+        // for (List<Annotation> annsOneIaClass: annses) {
+        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
+        // }
+        // return topTask;
+        // }
+        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
+        // System.out.println("Starting intakeAnnotations");
+        if ((anns == null) || (anns.size() < 1)) return null;
+        Task topTask = new Task();
+        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
+        topTask.setObjectAnnotations(anns);
+        String context = myShepherd.getContext();
+
+        /*
+            what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed for this we use
+               IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than one we need to make a set of subtasks
+         */
+
+/*
+        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
+           List<JSONObject> opts = null;
+        // below gets it working for dolphins but can be generalized easily from IA.properties String inferredIaClass =
+           IBEISIA.inferIaClass(anns.get(0), myShepherd);
+        String bottlenose = "dolphin_bottlenose_fin";
+        if (bottlenose.equals(iaClass) || bottlenose.equals(inferredIaClass)) {
+            System.out.println("IA.java is sending a Tursiops truncatus job");
+            opts = IBEISIA.identOpts(context, bottlenose);
+        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes opts = IBEISIA.identOpts(context);
+        }
+ */
+        // List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
+        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
+        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
+        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
+            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
+            // now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
+            if (opts != null) {
+                Iterator<JSONObject> itr = opts.iterator();
+                while (itr.hasNext()) {
+                    if (!itr.next().optBoolean("default", true)) itr.remove();
+                }
+            }
+            System.out.println("identOpts: " + opts);
+            List<Task> tasks = new ArrayList<Task>();
+            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
+            if (parentTask != null && parentTask.getParameters() != null) {
+                newTaskParams = parentTask.getParameters();
+                System.out.println("newTaskParams: " + newTaskParams.toString());
+                if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
+                    JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
+                    System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
+                    ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
+                    int maLength = matchingAlgorithms.length();
+                    for (int y = 0; y < maLength; y++) {
+                        newOpts.add(matchingAlgorithms.getJSONObject(y));
+                    }
+                    System.out.println("matchingAlgorithms2: " + newOpts.toString());
+                    if (newOpts.size() > 0) {
+                        opts = newOpts;
+                        System.out.println("Swapping opts for newOpts!!");
+                    }
+                }
+            }
+            if ((opts == null) || (opts.size() < 1)) continue; // no ID for this iaClass.
+            // just one IA class, one algorithm case
+            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
+                newTaskParams.put("ibeis.identification",
+                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
+                topTask.setParameters(newTaskParams);
+                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
+            } else {
+                for (int i = 0; i < opts.size(); i++) {
+                    Task t = new Task();
+                    t.setObjectAnnotations(annsOneIAClass);
+                    newTaskParams.put("ibeis.identification",
+                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
+                    t.setParameters(newTaskParams);
+                    topTask.addChild(t);
+                    tasks.add(t);
+                }
+            }
+            newTaskParams.put("fastlane", fastlane);
+            if (fastlane) newTaskParams.put("lane", "fast");
+            myShepherd.storeNewTask(topTask);
+
+            // these are re-used in every task
+            JSONArray annArr = new JSONArray();
+            for (Annotation ann : annsOneIAClass) {
+                annArr.put(ann.getId());
+            }
+            JSONObject aj = new JSONObject();
+            aj.put("annotationIds", annArr);
+            String baseUrl = getBaseURL(context);
+            for (int i = 0; i < opts.size(); i++) {
+                // if this is a vector-based matching option, this will just do the job and be done
+                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
+                JSONObject qjob = new JSONObject();
+                qjob.put("identify", aj);
+                qjob.put("taskId", tasks.get(i).getId());
+                qjob.put("__context", context);
+                qjob.put("__baseUrl", baseUrl);
+                if (opts.get(i) != null) qjob.put("opt", opts.get(i));
+                boolean sent = false;
+                try {
+                    if (fastlane) {
+                        // if fastlane and a smaller, bespoke request, get this into the faster queue
+                        qjob.put("fastlane", fastlane);
+                        qjob.put("lane", "fast");
+                        tasks.get(i).setQueueResumeMessage(qjob.toString());
+                        sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
+                            qjob.toString());
+                    } else {
+                        tasks.get(i).setQueueResumeMessage(qjob.toString());
+                        sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
+                    }
+                } catch (java.io.IOException iox) {
+                    System.out.println("ERROR[" + i +
+                        "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
+                }
+                System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " +
+                    annsOneIAClass.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
+            }
+        } // end for each iaClass
+        System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
+        return topTask;
+    }
+
+    public static List<List<Annotation> > binAnnotsByIaClass(List<Annotation> anns) {
+        System.out.println("binAnnotsByIaClass called on " + anns.size() + " annots");
+        Map<String, List<Annotation> > iaClassToAnns = new HashMap<String, List<Annotation> >();
+        for (Annotation ann : anns) {
+            String iaClass = ann.getIAClass();
+            if (iaClass == null) continue;
+            List<Annotation> iaClassList = iaClassToAnns.getOrDefault(iaClass,
+                new ArrayList<Annotation>());
+            iaClassList.add(ann);
+            iaClassToAnns.put(iaClass, iaClassList);
+        }
+        System.out.println("binAnnotsByIaClass binned them into " + iaClassToAnns.keySet().size() +
+            " bins: " + iaClassToAnns.keySet());
+        return new ArrayList<List<Annotation> >(iaClassToAnns.values());
+    }
+
+    // possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
+    // note: this is processed *from the queue* and as such does not have "output"
+    public static void handleRest(JSONObject jin) {
+        System.out.println("JIN JIN JIN: " + jin);
+        if (jin == null) return;
+        String context = jin.optString("__context", null);
+        if (context == null)
+            throw new RuntimeException("IA.handleRest(): passed data has no __context");
+        Shepherd myShepherd = new Shepherd(context);
+
+        // check if these should be directed through the fastlane
+        boolean fastlane = false;
+        if (jin.optBoolean("fastlane", false)) { fastlane = true; }
+        myShepherd.setAction("IA.handleRest");
+        myShepherd.beginDBTransaction();
+        try {
+            String taskId = jin.optString("taskId", Util.generateUUID());
+            Task topTask = Task.load(taskId, myShepherd);
+            if (topTask == null) topTask = new Task(taskId);
+            if (fastlane) topTask.addParameter("fastlane", true);
+            myShepherd.storeNewTask(topTask);
+            JSONObject opt = jin.optJSONObject("opt"); // should use this to decide how to branch differently than "default"
+            JSONArray mlist = jin.optJSONArray("mediaAssetIds");
+            if ((mlist != null) && (mlist.length() > 0)) {
+                System.out.println("MLIST: " + mlist);
+                List<MediaAsset> mas = new ArrayList<MediaAsset>();
+                for (int i = 0; i < mlist.length(); i++) {
+                    int mid = mlist.optInt(i, -1);
+                    if (mid < 1) continue;
+                    MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
+                    System.out.println(i + " -> " + ma);
+                    if (ma == null) continue;
+                    mas.add(ma);
+                }
+                Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
+                System.out.println("INFO: IA.handleRest() just intook MediaAssets as " + mtask +
+                    " for (parent) " + topTask);
+                topTask.addChild(mtask);
+            }
+            JSONArray alist = jin.optJSONArray("annotationIds");
+            if ((alist != null) && (alist.length() > 0)) {
+                List<Annotation> anns = new ArrayList<Annotation>();
+                for (int i = 0; i < alist.length(); i++) {
+                    String aid = alist.optString(i, null);
+                    if (aid == null) continue;
+                    Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
+                        myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
+                    if (ann == null) continue;
+                    anns.add(ann);
+                }
+                // okay, if we are sending another ID job from the hburger menu, the media asset needs to be added to your top level 'root' task,
+                // or else you will link to the original root task
+                List<MediaAsset> masForNewRoot = new ArrayList<>();
+                for (Annotation ann : anns) {
+                    MediaAsset ma = ann.getMediaAsset();
+                    if (ma != null && !masForNewRoot.contains(ma)) {
+                        masForNewRoot.add(ma);
+                    }
+                }
+                // i cant think of a scenario where we would get here and accidently double-add mas... but jic
+                for (MediaAsset ma : masForNewRoot) {
+                    if (!topTask.getObjectMediaAssets().contains(ma)) {
+                        topTask.addObject(ma);
+                    }
+                }
+                Task atask = intakeAnnotations(myShepherd, anns, topTask, fastlane);
+                System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask +
+                    " for " + topTask);
+                myShepherd.getPM().refresh(topTask);
+                topTask.addChild(atask);
+                topTask.setModified();
+                myShepherd.getPM().makePersistent(atask);
+            }
+            myShepherd.commitDBTransaction();
+        } catch (Exception e) {
+            e.printStackTrace();
+            myShepherd.rollbackDBTransaction();
+        } finally {
+            myShepherd.closeDBTransaction();
+        }
+    }
+
+    // via IAGateway servlet, we handle the work
+    public static void handleGet(HttpServletRequest request, HttpServletResponse response)
+    throws java.io.IOException {
+        // JSONObject rtn = queueCallback(request);
+        JSONObject rtn = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
+        String context = ServletUtilities.getContext(request);
+        Shepherd myShepherd = new Shepherd(context);
+
+        myShepherd.setAction("IA.handleGet");
+        myShepherd.beginDBTransaction();
+        String taskId = request.getParameter("taskId");
+        if (taskId != null) {
+            Task task = Task.load(taskId, myShepherd);
+            if (task == null) {
+                response.sendError(404, "Not found: taskId=" + taskId);
+                myShepherd.rollbackDBTransaction();
+                myShepherd.closeDBTransaction();
+                return;
+            }
+            rtn.put("success", true);
+            rtn.remove("error");
+            rtn.put("task",
+                task.toJSONObject(Util.requestParameterSet(request.getParameter(
+                    "includeChildren"))));
+        }
+        response.setContentType("text/plain");
+        PrintWriter out = response.getWriter();
+        out.println(rtn.toString());
+        out.close();
+        myShepherd.rollbackDBTransaction();
+        myShepherd.closeDBTransaction();
+        return;
+    }
+
+    public static String getBaseURL(String context) {
+        String url = CommonConfiguration.getServerURL(context);
+        String containerName = CommonConfiguration.getProperty("containerName", context);
+
+        if (containerName != null && !"".equals(containerName)) {
+            containerName = containerName.trim();
+            System.out.println("INFO: Wildbook is containerized: Server getBaseURL is returning: " +
+                containerName + "");
+            url = url.replace("localhost", containerName);
+        }
+        System.out.println("INFO: Server getBaseURL is returning " + url);
+        return url;
+    }
+
+    // (optional!) Taxonomy will append "_Scientific_name" to label and try that.  if not available, then try just label.
+    public static String getProperty(String context, String label, Taxonomy tax, String def) {
+        if ((tax != null) && (tax.getScientificName() != null)) {
+            String propKey = label + "_".concat(tax.getScientificName()).replaceAll(" ", "_");
+            System.out.println("[INFO] IA.getProperty() using propKey=" + propKey + " based on " +
+                tax);
+            String val = getProperty(context, propKey, (String)null);
+            if (val != null) return val;
+        }
+        return IA.getProperty(context, label, def);
+    }
+
+    public static String getProperty(String context, String label, Taxonomy tax) { // no-default version
+        return getProperty(context, label, tax, null);
+    }
+
+    public static String getProperty(String context, String label) { // no-default, no-taxonomy
+        return getProperty(context, label, (String)null);
+    }
+
+    public static String getProperty(String context, String label, String def) {
+        Properties p = getProperties(context);
+
+        if (p == null) {
+            System.out.println("IA.getProperty(" + label +
+                ") has no properties; IA.properties unavailable?");
+            return null;
+        }
+        return p.getProperty(label, def);
+    }
+
+    private static Properties getProperties(String context) {
+        try {
+            return ShepherdProperties.getProperties(PROP_FILE, "", context);
+        } catch (Exception ex) {
+            return null;
+        }
+    }
+
+    public static void log(String msg) {
+        System.out.println(new org.joda.time.DateTime() + " " + msg);
+    }
+}
diff --git a/src/main/java/org/ecocean/ia/IAException.java b/src/main/java/org/ecocean/ia/IAException.java
index 3a35a7792..afbf24a95 100644
--- a/src/main/java/org/ecocean/ia/IAException.java
+++ b/src/main/java/org/ecocean/ia/IAException.java
@@ -1,60 +1,60 @@
-package org.ecocean.ia;
-
-import org.json.JSONArray;
-import org.json.JSONObject;
-
-public class IAException extends Exception {
-    protected boolean requeue = false;
-    protected boolean requeueIncrement = false;
-    // protected JSONArray errors = null;
-
-    // ml-service migration v2 (commit #8): optional typed code so callers
-    // (e.g. MlServiceProcessor) can classify failures without parsing message
-    // strings. Backward-compatible — existing constructors leave code null.
-    protected String code;
-
-    public IAException(String message) {
-        super(message);
-    }
-
-    public IAException(String message, boolean requeue) {
-        super(message);
-        this.requeue = requeue;
-    }
-
-    public IAException(String message, boolean requeue, boolean requeueIncrement) {
-        super(message);
-        this.requeue = requeue;
-        this.requeueIncrement = requeueIncrement;
-    }
-
-    public IAException(String code, String message, boolean requeue, boolean requeueIncrement) {
-        super(message);
-        this.code = code;
-        this.requeue = requeue;
-        this.requeueIncrement = requeueIncrement;
-    }
-
-    public String getCode() {
-        return code;
-    }
-
-/*
-    public IAException(String message, JSONArray errors) {
-        super(message);
-        this.errors = errors;
-    }
-
-    public IAException(String message, JSONObject error) {
-        super(message);
-        addError(error);
-    }
- */
-    public boolean shouldRequeue() {
-        return requeue;
-    }
-
-    public boolean shouldIncrement() {
-        return requeueIncrement;
-    }
-}
+package org.ecocean.ia;
+
+import org.json.JSONArray;
+import org.json.JSONObject;
+
+public class IAException extends Exception {
+    protected boolean requeue = false;
+    protected boolean requeueIncrement = false;
+    // protected JSONArray errors = null;
+
+    // ml-service migration v2 (commit #8): optional typed code so callers
+    // (e.g. MlServiceProcessor) can classify failures without parsing message
+    // strings. Backward-compatible — existing constructors leave code null.
+    protected String code;
+
+    public IAException(String message) {
+        super(message);
+    }
+
+    public IAException(String message, boolean requeue) {
+        super(message);
+        this.requeue = requeue;
+    }
+
+    public IAException(String message, boolean requeue, boolean requeueIncrement) {
+        super(message);
+        this.requeue = requeue;
+        this.requeueIncrement = requeueIncrement;
+    }
+
+    public IAException(String code, String message, boolean requeue, boolean requeueIncrement) {
+        super(message);
+        this.code = code;
+        this.requeue = requeue;
+        this.requeueIncrement = requeueIncrement;
+    }
+
+    public String getCode() {
+        return code;
+    }
+
+/*
+    public IAException(String message, JSONArray errors) {
+        super(message);
+        this.errors = errors;
+    }
+
+    public IAException(String message, JSONObject error) {
+        super(message);
+        addError(error);
+    }
+ */
+    public boolean shouldRequeue() {
+        return requeue;
+    }
+
+    public boolean shouldIncrement() {
+        return requeueIncrement;
+    }
+}
diff --git a/src/main/java/org/ecocean/ia/MLService.java b/src/main/java/org/ecocean/ia/MLService.java
index 795b7f928..a52930e7f 100644
--- a/src/main/java/org/ecocean/ia/MLService.java
+++ b/src/main/java/org/ecocean/ia/MLService.java
@@ -1,424 +1,424 @@
-package org.ecocean.ia;
-
-import org.json.JSONArray;
-import org.json.JSONObject;
-
-import java.net.MalformedURLException;
-import java.net.URL;
-import java.util.ArrayList;
-import java.util.List;
-
-import org.ecocean.Annotation;
-import org.ecocean.Embedding;
-import org.ecocean.ia.Task;
-import org.ecocean.identity.IBEISIA;
-import org.ecocean.IAJsonProperties;
-import org.ecocean.media.Feature;
-import org.ecocean.media.FeatureType;
-import org.ecocean.media.MediaAsset;
-import org.ecocean.RestClient;
-import org.ecocean.servlet.IAGateway;
-import org.ecocean.shepherd.core.Shepherd;
-import org.ecocean.Util;
-
-import java.io.IOException;
-
-// https://github.com/WildMeOrg/ml-service
-
-public class MLService {
-    private IAJsonProperties iaConfig = null;
-
-    public MLService() {
-        iaConfig = IAJsonProperties.iaConfig();
-    }
-
-    public JSONObject initiateRequest(MediaAsset ma, String taxonomyString)
-    throws IOException {
-        addToQueue(createJobData(ma, taxonomyString), null);
-        return null;
-    }
-
-    public JSONObject initiateRequest(Annotation ann, String taxonomyString)
-    throws IOException {
-        return initiateRequest(ann, taxonomyString, null);
-    }
-
-    public JSONObject initiateRequest(Annotation ann, String taxonomyString, Task task)
-    throws IOException {
-        addToQueue(createJobData(ann, taxonomyString), task);
-        return null;
-    }
-
-    public IAJsonProperties getIAConfig() {
-        return iaConfig;
-    }
-
-    // there can be multiple configs (differing model_id)
-    public List<JSONObject> getConfigs(String passedTxStr)
-    throws IAException {
-        IAJsonProperties iac = getIAConfig();
-
-        if (iac == null) throw new IAException("MLService.getConfigs() iac configuration problem");
-        if (passedTxStr == null)
-            throw new IAException("MLService.getConfigs() null passed taxonomy");
-        String taxonomyString = passedTxStr.replaceAll(" ", "."); // need dots, not spaces
-        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
-        if (mlc == null)
-            throw new IAException(
-                      "MLService.getConfigs() configuration problem with taxonomyString=" +
-                      taxonomyString);
-        JSONArray confs = null;
-        try {
-            confs = (JSONArray)mlc;
-        } catch (Exception ex) {
-            ex.printStackTrace();
-        }
-        if (confs == null)
-            throw new IAException(
-                      "MLService.getConfigs() configuration problem with taxonomyString=" +
-                      taxonomyString + "; mlc=" + mlc);
-        List<JSONObject> configs = new ArrayList<JSONObject>();
-        for (int i = 0; i < confs.length(); i++) {
-            JSONObject jc = confs.optJSONObject(i);
-            if (jc != null) configs.add(jc);
-        }
-        return configs;
-    }
-
-    public void addToQueue(JSONObject jobData, Task task)
-    throws IOException {
-        if (jobData == null) return;
-        if (task != null) jobData.put("taskId", task.getId());
-        IAGateway.addToDetectionQueue("context0", jobData.toString());
-    }
-
-    // i think we *must* pass taxonomyString here
-    public JSONObject createJobData(MediaAsset ma, String taxonomyString) {
-        JSONObject data = new JSONObject();
-
-        data.put("MLService", true);
-        data.put("taxonomyString", taxonomyString);
-
-        JSONArray maIds = new JSONArray();
-        maIds.put(ma.getIdInt());
-        data.put("mediaAssetIds", maIds);
-        return data;
-    }
-
-    public JSONObject createJobData(Annotation ann, String taxonomyString) {
-        JSONObject data = new JSONObject();
-
-        data.put("MLService", true);
-        data.put("taxonomyString", taxonomyString);
-
-        JSONArray annIds = new JSONArray();
-        annIds.put(ann.getId());
-        data.put("annotationIds", annIds);
-        return data;
-    }
-
-    public void processQueueJob(JSONObject jobData) {
-        System.out.println("#################################################### processing: " +
-            jobData.toString(8));
-        Shepherd myShepherd = new Shepherd("context0");
-        myShepherd.setAction("MLService.processQueueJob");
-        myShepherd.beginDBTransaction();
-        FeatureType.initAll(myShepherd);
-        Task task = myShepherd.getTask(jobData.optString("taskId", null));
-        JSONArray ids = jobData.optJSONArray("mediaAssetIds");
-        // skipEmbedding will set true if there was a non-requeuable config problem
-        // (probably not configured for _mlservice in IA.json) so we just give up and
-        // let ident do its thing
-        boolean skipEmbedding = false;
-        try {
-            // got some asset ids
-            if (ids != null) {
-                for (String maId : Util.jsonArrayToStringList(ids)) {
-                    System.out.println("[DEBUG] MLService.processQueueJob() maId=" + maId + " [" +
-                        task + "]");
-                    send(myShepherd.getMediaAsset(maId), jobData.optString("taxonomyString", null),
-                        myShepherd);
-                }
-                // maybe annot ids?
-            } else {
-                ids = jobData.optJSONArray("annotationIds");
-                if (ids != null) {
-                    for (String annId : Util.jsonArrayToStringList(ids)) {
-                        System.out.println("[DEBUG] MLService.processQueueJob() annId=" + annId +
-                            " [" + task + "]");
-                        send(myShepherd.getAnnotation(annId),
-                            jobData.optString("taxonomyString", null), myShepherd);
-                    }
-                }
-            }
-            if (task != null) task.setStatus("completed");
-        } catch (IAException iaex) {
-            System.out.println("MLService.processQueueJob() threw " + iaex + " with jobData=" +
-                jobData);
-            iaex.printStackTrace();
-            if (task != null) {
-                task.setStatus("error");
-                task.setStatusDetailsAddError("UNKNOWN", "MLService job: " + iaex);
-            }
-            if (iaex.shouldRequeue()) {
-                requeueJob(jobData, iaex.shouldIncrement());
-            } else {
-                // we might want more complex logic to determine if we really should give up
-                skipEmbedding = true;
-            }
-        } finally {
-            // we end up here after *each* annotation, so we are "done" when all annotations have been processed
-            boolean taskComplete = skipEmbedding || areAllEmbeddingsExtracted(task);
-            if (taskComplete && (task != null)) task.setCompletionDateInMilliseconds();
-            myShepherd.commitDBTransaction();
-            if (taskComplete) {
-                // now we are done we can fake a callback to initiate identification
-                JSONObject fakeResp = new JSONObject();
-                fakeResp.put("embeddingExtraction", true);
-                // taskComplete is only true if we have *some* annots
-                JSONObject annMap = new JSONObject();
-                if (task != null)
-                    for (Annotation ann : task.getObjectAnnotations()) {
-                        MediaAsset ma = ann.getMediaAsset();
-                        if (ma == null) continue; // snh
-                        if (!annMap.has(ma.getId())) annMap.put(ma.getId(), new JSONArray());
-                        annMap.getJSONArray(ma.getId()).put(ann.getId());
-                    }
-                fakeResp.put("annotationMap", annMap);
-                JSONObject cbRes = IBEISIA.processCallback((task == null) ? null : task.getId(),
-                    fakeResp, myShepherd.getContext(), null);
-                System.out.println("[DEBUG] MLService.processQueueJob() [" + task +
-                    " complete] cbRes=" + cbRes);
-            }
-            myShepherd.closeDBTransaction();
-        }
-    }
-
-    // true if all annotations "are done" from (trying to) extract embeddings
-    private boolean areAllEmbeddingsExtracted(Task task) {
-        if (task == null) return false;
-        List<Annotation> anns = task.getObjectAnnotations();
-        // we return false here because there is no reason to send to ident in this case
-        if (Util.collectionIsEmptyOrNull(anns)) return false;
-        // we iterate over annotations and only return false if we find one explicitly still
-        // in processing state. this means *any* other (complete, error, etc) get counted as "done"
-        for (Annotation ann : anns) {
-            if (IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(ann.getIdentificationStatus()))
-                return false;
-        }
-        System.out.println(
-            "[DEBUG] MLService.areAllEmbeddingsExtracted() fell thru (aka true) on " + anns.size() +
-            " annots for " + task);
-        return true;
-    }
-
-    public void requeueJob(JSONObject jobData, boolean increment) {
-        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
-            jobData);
-        // this handles a bunch of messiness, including max retries etc
-        IAGateway.requeueJob(jobData, increment);
-    }
-
-    public void send(MediaAsset ma, String taxonomyString, Shepherd myShepherd)
-    throws IAException {
-        if (ma == null) throw new IAException("null MediaAsset passed");
-        for (JSONObject conf : getConfigs(taxonomyString)) {
-            JSONObject payload = createPayload(ma, conf);
-            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
-                payload);
-            // got results, now we try to use them
-            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
-                "; RESPONSE => " + res);
-            List<Annotation> anns = processMediaAssetResults(ma, res);
-            System.out.println("MLService.send() created " + anns.size() + " anns on " + ma + ": " +
-                anns);
-            // FIXME persist anns using myShepherd
-            // FIXME send along to ident????? (but using vectors!!!????!)
-        }
-    }
-
-    public List<Annotation> processMediaAssetResults(MediaAsset ma, JSONObject res)
-    throws IAException {
-        if (res == null) throw new IAException("empty results");
-        if (!res.optBoolean("success", false))
-            throw new IAException("results success=false: " + res);
-        JSONArray bboxes = res.optJSONArray("bboxes");
-        if (bboxes == null) throw new IAException("null bboxes in results: " + res);
-        List<Annotation> anns = new ArrayList<Annotation>();
-        if (bboxes.length() < 1) return anns;
-        // TODO do we ever care about scores?
-        List<Double> scores = Util.jsonArrayToDoubleList(res.optJSONArray("scores"));
-        if ((scores == null) || (scores.size() != bboxes.length()))
-            throw new IAException("scores size does not match bboxes: " + res);
-        List<Double> thetas = Util.jsonArrayToDoubleList(res.optJSONArray("thetas"));
-        if ((thetas == null) || (thetas.size() != bboxes.length()))
-            throw new IAException("thetas size does not match bboxes: " + res);
-        List<String> classNames = Util.jsonArrayToStringList(res.optJSONArray("class_names"));
-        if ((classNames == null) || (classNames.size() != bboxes.length()))
-            throw new IAException("class_names size does not match bboxes: " + res);
-        // FIXME wtf happened to viewpoint??? :)
-        // iterate over bboxes and make annots
-        for (int i = 0; i < bboxes.length(); i++) {
-            List<Double> xywh = Util.jsonArrayToDoubleList(bboxes.optJSONArray(i));
-            if (xywh == null) throw new IAException("error parsing bbox[" + i + "] (null): " + res);
-            if (xywh.size() != 4)
-                throw new IAException("error parsing bbox[" + i + "] (size): " + res);
-            Annotation ann = createAnnotation(xywh, thetas.get(i), classNames.get(i), null);
-            Annotation exists = ma.findAnnotation(ann, true);
-            if (exists != null) { // i guess we just skip this and do not create???
-                System.out.println("[WARNING] MLService.processMediaAssetResults() skipping i=" +
-                    i + " (res=" + res + ") due to existing matching " + exists);
-                continue;
-            }
-            ma.addFeature(ann.getFeature());
-            anns.add(ann);
-        }
-        ma.setDetectionStatus("complete");
-        return anns;
-    }
-
-    private Annotation createAnnotation(List<Double> bbox, Double theta, String iaClass,
-        String viewpoint)
-    throws IAException {
-        if ((bbox == null) || (bbox.size() != 4))
-            throw new IAException("createAnnotation() bad bbox");
-        if ((bbox.get(2) < 1.0d) || (bbox.get(3) < 1.0d))
-            throw new IAException("createAnnotation() bad bbox width/height");
-        JSONObject fparams = new JSONObject();
-        fparams.put("x", bbox.get(0));
-        fparams.put("y", bbox.get(1));
-        fparams.put("width", bbox.get(2));
-        fparams.put("height", bbox.get(3));
-        fparams.put("theta", ((theta == null) ? 0.0d : theta));
-        fparams.put("viewpoint", viewpoint);
-        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
-        Annotation ann = new Annotation(null, ft, iaClass);
-        ann.setViewpoint(viewpoint);
-        return ann;
-    }
-
-    public void send(Annotation ann, String taxonomyString, Shepherd myShepherd)
-    throws IAException {
-        if (ann == null) throw new IAException("null Annotation passed");
-        for (JSONObject conf : getConfigs(taxonomyString)) {
-            JSONObject payload = createPayload(ann, conf);
-            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
-                payload);
-            // got results, now we try to use them
-            JSONObject logRes = new JSONObject(res.toString());
-            if (logRes.optJSONArray("embeddings") != null)
-                logRes.put("embeddings",
-                    "TRUNCATED [length=" + logRes.getJSONArray("embeddings").toString().length() +
-                    "]");
-            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
-                "; RESPONSE => " + logRes);
-            processAnnotationResults(ann, res, myShepherd);
-            System.out.println("MLService.send() process results on " + ann);
-        }
-    }
-
-    // not sure what (if anything) we need to return here
-    public void processAnnotationResults(Annotation ann, JSONObject res, Shepherd myShepherd)
-    throws IAException {
-        if (res == null) throw new IAException("empty results");
-        if (ann == null) throw new IAException("null Annotation");
-        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
-        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
-        JSONArray embs = res.optJSONArray("embeddings");
-        if (embs == null) throw new IAException("results has no embeddings array: " + res);
-        // in our case we should have one embedding in there
-        if ((embs.length() < 1) || (embs.optJSONArray(0) == null))
-            throw new IAException("results has no embeddings array[0]: " + res);
-        JSONArray vecArr = embs.getJSONArray(0);
-        String[] methodValues = getMethodValues(res);
-        Embedding emb = new Embedding(ann, methodValues[0], methodValues[1], vecArr);
-        // maybe this is unwise? could 2 embeddings *from different methods* have same vectors? TODO
-        Embedding exists = ann.findEmbeddingByVector(emb);
-        if (exists != null) {
-            System.out.println("[WARNING] MLService.processAnnotationResults(): skipping; " + ann +
-                " already has: " + exists);
-            return;
-        }
-        ann.addEmbedding(emb);
-        // FIXME persist or whatever????
-        System.out.println("[DEBUG] MLService.processAnnotationResults(): added " + emb + " to " +
-            ann);
-    }
-
-    public static String[] getMethodValues(JSONObject conf) {
-        String[] mv = { null, null };
-
-        if (conf == null) return mv;
-        mv[0] = conf.optString("model_id", null);
-        // kinda hack version splitting here but... and i think some might not have dash, like "msv3"  :(
-        if ((mv[0] != null) && mv[0].contains("-")) {
-            String[] parts = mv[0].split("\\-");
-            mv[0] = parts[0];
-            mv[1] = parts[1];
-        }
-        return mv;
-    }
-
-    private JSONObject sendPayload(String endpoint, JSONObject payload)
-    throws IAException {
-        if (endpoint == null) throw new IAException("null api_endpoint");
-        URL url = null;
-        try {
-            url = new URL(endpoint);
-        } catch (MalformedURLException urlEx) {
-            throw new IAException("api_endpoint url error: " + urlEx);
-        }
-        try {
-            // throws IOException, java.net.ProtocolException
-            JSONObject res = RestClient.postJSON(url, payload, null);
-            return res;
-        } catch (Exception ex) {
-            System.out.println("sendPayload(" + url + ") threw " + ex);
-            ex.printStackTrace();
-            String msg = ex.getMessage();
-            if (msg == null) msg = ""; // safety against NPE
-            if (msg.contains("Connection refused")) {
-                throw new IAException("Connection refused", true, true);
-            } else if (msg.contains("Read timed out")) {
-                throw new IAException("time out", true); // no increment
-            } else if (msg.contains("HTTP error code : 500")) {
-                throw new IAException("500 error", true, true);
-            } else if (msg.contains("HTTP error code : 502")) {
-                throw new IAException("502 error", true); // we requeue, but dont increment this?
-            }
-            // default behavior is to retry, but with increment
-            throw new IAException("unhandled exception [will requeue, incremented] on POST: " + ex,
-                    true, true);
-        }
-    }
-
-    // this is to request detection find an annotation and (optionally) return embedding as well
-    public JSONObject createPayload(MediaAsset ma, JSONObject config)
-    throws IAException {
-        if ((config == null) || (ma == null))
-            throw new IAException("MLService.createPayload() configuration problem with ma=" + ma +
-                    "; config=" + config);
-        JSONObject payload = new JSONObject(config.toString());
-        payload.remove("api_endpoint");
-        payload.put("image_uri", ma.webURL());
-        // FIXME add embedding boolean/args
-        return payload;
-    }
-
-    // this only gets the embedding, from a given (manual or pre-existing) Annotation
-    public JSONObject createPayload(Annotation ann, JSONObject config)
-    throws IAException {
-        if ((config == null) || (ann == null))
-            throw new IAException("MLService.createPayload() configuration problem with ann=" +
-                    ann + "; config=" + config);
-        MediaAsset ma = ann.getMediaAsset();
-        if (ma == null)
-            throw new IAException("MLService.createPayload() no MediaAsset for ann=" + ann);
-        JSONObject payload = new JSONObject(config.toString());
-        payload.remove("api_endpoint");
-        payload.put("image_uri", ma.webURL());
-        payload.put("bbox", ann.getBbox());
-        payload.put("theta", ann.getTheta());
-        return payload;
-    }
-}
+package org.ecocean.ia;
+
+import org.json.JSONArray;
+import org.json.JSONObject;
+
+import java.net.MalformedURLException;
+import java.net.URL;
+import java.util.ArrayList;
+import java.util.List;
+
+import org.ecocean.Annotation;
+import org.ecocean.Embedding;
+import org.ecocean.ia.Task;
+import org.ecocean.identity.IBEISIA;
+import org.ecocean.IAJsonProperties;
+import org.ecocean.media.Feature;
+import org.ecocean.media.FeatureType;
+import org.ecocean.media.MediaAsset;
+import org.ecocean.RestClient;
+import org.ecocean.servlet.IAGateway;
+import org.ecocean.shepherd.core.Shepherd;
+import org.ecocean.Util;
+
+import java.io.IOException;
+
+// https://github.com/WildMeOrg/ml-service
+
+public class MLService {
+    private IAJsonProperties iaConfig = null;
+
+    public MLService() {
+        iaConfig = IAJsonProperties.iaConfig();
+    }
+
+    public JSONObject initiateRequest(MediaAsset ma, String taxonomyString)
+    throws IOException {
+        addToQueue(createJobData(ma, taxonomyString), null);
+        return null;
+    }
+
+    public JSONObject initiateRequest(Annotation ann, String taxonomyString)
+    throws IOException {
+        return initiateRequest(ann, taxonomyString, null);
+    }
+
+    public JSONObject initiateRequest(Annotation ann, String taxonomyString, Task task)
+    throws IOException {
+        addToQueue(createJobData(ann, taxonomyString), task);
+        return null;
+    }
+
+    public IAJsonProperties getIAConfig() {
+        return iaConfig;
+    }
+
+    // there can be multiple configs (differing model_id)
+    public List<JSONObject> getConfigs(String passedTxStr)
+    throws IAException {
+        IAJsonProperties iac = getIAConfig();
+
+        if (iac == null) throw new IAException("MLService.getConfigs() iac configuration problem");
+        if (passedTxStr == null)
+            throw new IAException("MLService.getConfigs() null passed taxonomy");
+        String taxonomyString = passedTxStr.replaceAll(" ", "."); // need dots, not spaces
+        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
+        if (mlc == null)
+            throw new IAException(
+                      "MLService.getConfigs() configuration problem with taxonomyString=" +
+                      taxonomyString);
+        JSONArray confs = null;
+        try {
+            confs = (JSONArray)mlc;
+        } catch (Exception ex) {
+            ex.printStackTrace();
+        }
+        if (confs == null)
+            throw new IAException(
+                      "MLService.getConfigs() configuration problem with taxonomyString=" +
+                      taxonomyString + "; mlc=" + mlc);
+        List<JSONObject> configs = new ArrayList<JSONObject>();
+        for (int i = 0; i < confs.length(); i++) {
+            JSONObject jc = confs.optJSONObject(i);
+            if (jc != null) configs.add(jc);
+        }
+        return configs;
+    }
+
+    public void addToQueue(JSONObject jobData, Task task)
+    throws IOException {
+        if (jobData == null) return;
+        if (task != null) jobData.put("taskId", task.getId());
+        IAGateway.addToDetectionQueue("context0", jobData.toString());
+    }
+
+    // i think we *must* pass taxonomyString here
+    public JSONObject createJobData(MediaAsset ma, String taxonomyString) {
+        JSONObject data = new JSONObject();
+
+        data.put("MLService", true);
+        data.put("taxonomyString", taxonomyString);
+
+        JSONArray maIds = new JSONArray();
+        maIds.put(ma.getIdInt());
+        data.put("mediaAssetIds", maIds);
+        return data;
+    }
+
+    public JSONObject createJobData(Annotation ann, String taxonomyString) {
+        JSONObject data = new JSONObject();
+
+        data.put("MLService", true);
+        data.put("taxonomyString", taxonomyString);
+
+        JSONArray annIds = new JSONArray();
+        annIds.put(ann.getId());
+        data.put("annotationIds", annIds);
+        return data;
+    }
+
+    public void processQueueJob(JSONObject jobData) {
+        System.out.println("#################################################### processing: " +
+            jobData.toString(8));
+        Shepherd myShepherd = new Shepherd("context0");
+        myShepherd.setAction("MLService.processQueueJob");
+        myShepherd.beginDBTransaction();
+        FeatureType.initAll(myShepherd);
+        Task task = myShepherd.getTask(jobData.optString("taskId", null));
+        JSONArray ids = jobData.optJSONArray("mediaAssetIds");
+        // skipEmbedding will set true if there was a non-requeuable config problem
+        // (probably not configured for _mlservice in IA.json) so we just give up and
+        // let ident do its thing
+        boolean skipEmbedding = false;
+        try {
+            // got some asset ids
+            if (ids != null) {
+                for (String maId : Util.jsonArrayToStringList(ids)) {
+                    System.out.println("[DEBUG] MLService.processQueueJob() maId=" + maId + " [" +
+                        task + "]");
+                    send(myShepherd.getMediaAsset(maId), jobData.optString("taxonomyString", null),
+                        myShepherd);
+                }
+                // maybe annot ids?
+            } else {
+                ids = jobData.optJSONArray("annotationIds");
+                if (ids != null) {
+                    for (String annId : Util.jsonArrayToStringList(ids)) {
+                        System.out.println("[DEBUG] MLService.processQueueJob() annId=" + annId +
+                            " [" + task + "]");
+                        send(myShepherd.getAnnotation(annId),
+                            jobData.optString("taxonomyString", null), myShepherd);
+                    }
+                }
+            }
+            if (task != null) task.setStatus("completed");
+        } catch (IAException iaex) {
+            System.out.println("MLService.processQueueJob() threw " + iaex + " with jobData=" +
+                jobData);
+            iaex.printStackTrace();
+            if (task != null) {
+                task.setStatus("error");
+                task.setStatusDetailsAddError("UNKNOWN", "MLService job: " + iaex);
+            }
+            if (iaex.shouldRequeue()) {
+                requeueJob(jobData, iaex.shouldIncrement());
+            } else {
+                // we might want more complex logic to determine if we really should give up
+                skipEmbedding = true;
+            }
+        } finally {
+            // we end up here after *each* annotation, so we are "done" when all annotations have been processed
+            boolean taskComplete = skipEmbedding || areAllEmbeddingsExtracted(task);
+            if (taskComplete && (task != null)) task.setCompletionDateInMilliseconds();
+            myShepherd.commitDBTransaction();
+            if (taskComplete) {
+                // now we are done we can fake a callback to initiate identification
+                JSONObject fakeResp = new JSONObject();
+                fakeResp.put("embeddingExtraction", true);
+                // taskComplete is only true if we have *some* annots
+                JSONObject annMap = new JSONObject();
+                if (task != null)
+                    for (Annotation ann : task.getObjectAnnotations()) {
+                        MediaAsset ma = ann.getMediaAsset();
+                        if (ma == null) continue; // snh
+                        if (!annMap.has(ma.getId())) annMap.put(ma.getId(), new JSONArray());
+                        annMap.getJSONArray(ma.getId()).put(ann.getId());
+                    }
+                fakeResp.put("annotationMap", annMap);
+                JSONObject cbRes = IBEISIA.processCallback((task == null) ? null : task.getId(),
+                    fakeResp, myShepherd.getContext(), null);
+                System.out.println("[DEBUG] MLService.processQueueJob() [" + task +
+                    " complete] cbRes=" + cbRes);
+            }
+            myShepherd.closeDBTransaction();
+        }
+    }
+
+    // true if all annotations "are done" from (trying to) extract embeddings
+    private boolean areAllEmbeddingsExtracted(Task task) {
+        if (task == null) return false;
+        List<Annotation> anns = task.getObjectAnnotations();
+        // we return false here because there is no reason to send to ident in this case
+        if (Util.collectionIsEmptyOrNull(anns)) return false;
+        // we iterate over annotations and only return false if we find one explicitly still
+        // in processing state. this means *any* other (complete, error, etc) get counted as "done"
+        for (Annotation ann : anns) {
+            if (IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(ann.getIdentificationStatus()))
+                return false;
+        }
+        System.out.println(
+            "[DEBUG] MLService.areAllEmbeddingsExtracted() fell thru (aka true) on " + anns.size() +
+            " annots for " + task);
+        return true;
+    }
+
+    public void requeueJob(JSONObject jobData, boolean increment) {
+        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
+            jobData);
+        // this handles a bunch of messiness, including max retries etc
+        IAGateway.requeueJob(jobData, increment);
+    }
+
+    public void send(MediaAsset ma, String taxonomyString, Shepherd myShepherd)
+    throws IAException {
+        if (ma == null) throw new IAException("null MediaAsset passed");
+        for (JSONObject conf : getConfigs(taxonomyString)) {
+            JSONObject payload = createPayload(ma, conf);
+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
+                payload);
+            // got results, now we try to use them
+            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
+                "; RESPONSE => " + res);
+            List<Annotation> anns = processMediaAssetResults(ma, res);
+            System.out.println("MLService.send() created " + anns.size() + " anns on " + ma + ": " +
+                anns);
+            // FIXME persist anns using myShepherd
+            // FIXME send along to ident????? (but using vectors!!!????!)
+        }
+    }
+
+    public List<Annotation> processMediaAssetResults(MediaAsset ma, JSONObject res)
+    throws IAException {
+        if (res == null) throw new IAException("empty results");
+        if (!res.optBoolean("success", false))
+            throw new IAException("results success=false: " + res);
+        JSONArray bboxes = res.optJSONArray("bboxes");
+        if (bboxes == null) throw new IAException("null bboxes in results: " + res);
+        List<Annotation> anns = new ArrayList<Annotation>();
+        if (bboxes.length() < 1) return anns;
+        // TODO do we ever care about scores?
+        List<Double> scores = Util.jsonArrayToDoubleList(res.optJSONArray("scores"));
+        if ((scores == null) || (scores.size() != bboxes.length()))
+            throw new IAException("scores size does not match bboxes: " + res);
+        List<Double> thetas = Util.jsonArrayToDoubleList(res.optJSONArray("thetas"));
+        if ((thetas == null) || (thetas.size() != bboxes.length()))
+            throw new IAException("thetas size does not match bboxes: " + res);
+        List<String> classNames = Util.jsonArrayToStringList(res.optJSONArray("class_names"));
+        if ((classNames == null) || (classNames.size() != bboxes.length()))
+            throw new IAException("class_names size does not match bboxes: " + res);
+        // FIXME wtf happened to viewpoint??? :)
+        // iterate over bboxes and make annots
+        for (int i = 0; i < bboxes.length(); i++) {
+            List<Double> xywh = Util.jsonArrayToDoubleList(bboxes.optJSONArray(i));
+            if (xywh == null) throw new IAException("error parsing bbox[" + i + "] (null): " + res);
+            if (xywh.size() != 4)
+                throw new IAException("error parsing bbox[" + i + "] (size): " + res);
+            Annotation ann = createAnnotation(xywh, thetas.get(i), classNames.get(i), null);
+            Annotation exists = ma.findAnnotation(ann, true);
+            if (exists != null) { // i guess we just skip this and do not create???
+                System.out.println("[WARNING] MLService.processMediaAssetResults() skipping i=" +
+                    i + " (res=" + res + ") due to existing matching " + exists);
+                continue;
+            }
+            ma.addFeature(ann.getFeature());
+            anns.add(ann);
+        }
+        ma.setDetectionStatus("complete");
+        return anns;
+    }
+
+    private Annotation createAnnotation(List<Double> bbox, Double theta, String iaClass,
+        String viewpoint)
+    throws IAException {
+        if ((bbox == null) || (bbox.size() != 4))
+            throw new IAException("createAnnotation() bad bbox");
+        if ((bbox.get(2) < 1.0d) || (bbox.get(3) < 1.0d))
+            throw new IAException("createAnnotation() bad bbox width/height");
+        JSONObject fparams = new JSONObject();
+        fparams.put("x", bbox.get(0));
+        fparams.put("y", bbox.get(1));
+        fparams.put("width", bbox.get(2));
+        fparams.put("height", bbox.get(3));
+        fparams.put("theta", ((theta == null) ? 0.0d : theta));
+        fparams.put("viewpoint", viewpoint);
+        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
+        Annotation ann = new Annotation(null, ft, iaClass);
+        ann.setViewpoint(viewpoint);
+        return ann;
+    }
+
+    public void send(Annotation ann, String taxonomyString, Shepherd myShepherd)
+    throws IAException {
+        if (ann == null) throw new IAException("null Annotation passed");
+        for (JSONObject conf : getConfigs(taxonomyString)) {
+            JSONObject payload = createPayload(ann, conf);
+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
+                payload);
+            // got results, now we try to use them
+            JSONObject logRes = new JSONObject(res.toString());
+            if (logRes.optJSONArray("embeddings") != null)
+                logRes.put("embeddings",
+                    "TRUNCATED [length=" + logRes.getJSONArray("embeddings").toString().length() +
+                    "]");
+            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
+                "; RESPONSE => " + logRes);
+            processAnnotationResults(ann, res, myShepherd);
+            System.out.println("MLService.send() process results on " + ann);
+        }
+    }
+
+    // not sure what (if anything) we need to return here
+    public void processAnnotationResults(Annotation ann, JSONObject res, Shepherd myShepherd)
+    throws IAException {
+        if (res == null) throw new IAException("empty results");
+        if (ann == null) throw new IAException("null Annotation");
+        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
+        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
+        JSONArray embs = res.optJSONArray("embeddings");
+        if (embs == null) throw new IAException("results has no embeddings array: " + res);
+        // in our case we should have one embedding in there
+        if ((embs.length() < 1) || (embs.optJSONArray(0) == null))
+            throw new IAException("results has no embeddings array[0]: " + res);
+        JSONArray vecArr = embs.getJSONArray(0);
+        String[] methodValues = getMethodValues(res);
+        Embedding emb = new Embedding(ann, methodValues[0], methodValues[1], vecArr);
+        // maybe this is unwise? could 2 embeddings *from different methods* have same vectors? TODO
+        Embedding exists = ann.findEmbeddingByVector(emb);
+        if (exists != null) {
+            System.out.println("[WARNING] MLService.processAnnotationResults(): skipping; " + ann +
+                " already has: " + exists);
+            return;
+        }
+        ann.addEmbedding(emb);
+        // FIXME persist or whatever????
+        System.out.println("[DEBUG] MLService.processAnnotationResults(): added " + emb + " to " +
+            ann);
+    }
+
+    public static String[] getMethodValues(JSONObject conf) {
+        String[] mv = { null, null };
+
+        if (conf == null) return mv;
+        mv[0] = conf.optString("model_id", null);
+        // kinda hack version splitting here but... and i think some might not have dash, like "msv3"  :(
+        if ((mv[0] != null) && mv[0].contains("-")) {
+            String[] parts = mv[0].split("\\-");
+            mv[0] = parts[0];
+            mv[1] = parts[1];
+        }
+        return mv;
+    }
+
+    private JSONObject sendPayload(String endpoint, JSONObject payload)
+    throws IAException {
+        if (endpoint == null) throw new IAException("null api_endpoint");
+        URL url = null;
+        try {
+            url = new URL(endpoint);
+        } catch (MalformedURLException urlEx) {
+            throw new IAException("api_endpoint url error: " + urlEx);
+        }
+        try {
+            // throws IOException, java.net.ProtocolException
+            JSONObject res = RestClient.postJSON(url, payload, null);
+            return res;
+        } catch (Exception ex) {
+            System.out.println("sendPayload(" + url + ") threw " + ex);
+            ex.printStackTrace();
+            String msg = ex.getMessage();
+            if (msg == null) msg = ""; // safety against NPE
+            if (msg.contains("Connection refused")) {
+                throw new IAException("Connection refused", true, true);
+            } else if (msg.contains("Read timed out")) {
+                throw new IAException("time out", true); // no increment
+            } else if (msg.contains("HTTP error code : 500")) {
+                throw new IAException("500 error", true, true);
+            } else if (msg.contains("HTTP error code : 502")) {
+                throw new IAException("502 error", true); // we requeue, but dont increment this?
+            }
+            // default behavior is to retry, but with increment
+            throw new IAException("unhandled exception [will requeue, incremented] on POST: " + ex,
+                    true, true);
+        }
+    }
+
+    // this is to request detection find an annotation and (optionally) return embedding as well
+    public JSONObject createPayload(MediaAsset ma, JSONObject config)
+    throws IAException {
+        if ((config == null) || (ma == null))
+            throw new IAException("MLService.createPayload() configuration problem with ma=" + ma +
+                    "; config=" + config);
+        JSONObject payload = new JSONObject(config.toString());
+        payload.remove("api_endpoint");
+        payload.put("image_uri", ma.webURL());
+        // FIXME add embedding boolean/args
+        return payload;
+    }
+
+    // this only gets the embedding, from a given (manual or pre-existing) Annotation
+    public JSONObject createPayload(Annotation ann, JSONObject config)
+    throws IAException {
+        if ((config == null) || (ann == null))
+            throw new IAException("MLService.createPayload() configuration problem with ann=" +
+                    ann + "; config=" + config);
+        MediaAsset ma = ann.getMediaAsset();
+        if (ma == null)
+            throw new IAException("MLService.createPayload() no MediaAsset for ann=" + ann);
+        JSONObject payload = new JSONObject(config.toString());
+        payload.remove("api_endpoint");
+        payload.put("image_uri", ma.webURL());
+        payload.put("bbox", ann.getBbox());
+        payload.put("theta", ann.getTheta());
+        return payload;
+    }
+}
diff --git a/src/main/java/org/ecocean/ia/MatchResult.java b/src/main/java/org/ecocean/ia/MatchResult.java
index 79a85e296..b21dbccf7 100644
--- a/src/main/java/org/ecocean/ia/MatchResult.java
+++ b/src/main/java/org/ecocean/ia/MatchResult.java
@@ -1,536 +1,536 @@
-package org.ecocean.ia;
-
-import java.io.File;
-import java.io.IOException;
-import java.net.URL;
-import java.util.ArrayList;
-import java.util.Collections;
-import java.util.Comparator;
-import java.util.HashMap;
-import java.util.HashSet;
-import java.util.List;
-import java.util.Map;
-import java.util.Set;
-
-import org.json.JSONArray;
-import org.json.JSONObject;
-
-import org.ecocean.api.UploadedFiles;
-import org.ecocean.Annotation;
-import org.ecocean.Encounter;
-import org.ecocean.ia.MLService;
-import org.ecocean.ia.Task;
-import org.ecocean.identity.IBEISIA;
-import org.ecocean.identity.IdentityServiceLog;
-import org.ecocean.media.AssetStore;
-import org.ecocean.media.Feature;
-import org.ecocean.media.MediaAsset;
-import org.ecocean.media.URLAssetStore;
-import org.ecocean.MarkedIndividual;
-import org.ecocean.RestClient;
-import org.ecocean.shepherd.core.Shepherd;
-import org.ecocean.Util;
-
-public class MatchResult implements java.io.Serializable {
-    private String id;
-    private long created;
-    private Task task;
-    private Set<MatchResultProspect> prospects;
-    private Annotation queryAnnotation;
-    private int numberCandidates = 0;
-    // we store *actual* count here, but they may not all exist
-    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
-    private int numberProspects = 0;
-    // not sure we really *need* true fk link to these annots
-    // they might be gone now and will we ever use this?
-    // so for now we just populate numberCandidates
-    private Set<Annotation> candidates;
-    // fallback number to cutoff number of prospects to return
-    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
-    // number of MatchResultProspects [per type] to actually store (hotspotter
-    // results can produce thousands, but storing them all is excessive)
-    public static final int MAXIMUM_PROSPECTS_STORED = 500;
-
-    public MatchResult() {
-        id = Util.generateUUID();
-        created = System.currentTimeMillis();
-    }
-
-    public MatchResult(Task task) {
-        this();
-        this.task = task;
-    }
-
-    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
-    throws IOException {
-        this();
-        this.createFromIdentityServiceLog(isLog, myShepherd);
-    }
-
-    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
-    throws IOException {
-        this();
-        this.task = task;
-        this.createFromJsonResult(jsonResult, myShepherd);
-    }
-
-    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
-        Shepherd myShepherd)
-    throws IOException {
-        this();
-        this.task = task;
-        this.numberCandidates = numberCandidates;
-        this.setQueryAnnotationFromTask();
-        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
-        this.populateProspects(annots, false, myShepherd);
-        this.populateProspects(annots, true, myShepherd);
-    }
-
-    public int getNumberCandidates() {
-        return numberCandidates;
-    }
-
-    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
-    throws IOException {
-        if (isLog == null) throw new IOException("log passed is null");
-        String taskId = isLog.getTaskID();
-        this.task = myShepherd.getTask(taskId);
-        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
-        JSONObject res = isLog.getJsonResult();
-        if (res == null) {
-            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
-                isLog.getStatusJson());
-            throw new IOException("could not get json result");
-        }
-        createFromJsonResult(res, myShepherd);
-    }
-
-    public Annotation setQueryAnnotationFromTask()
-    throws IOException {
-        if (this.task == null)
-            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
-        int numAnns = this.task.countObjectAnnotations();
-        if (numAnns < 1)
-            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
-        if (numAnns > 1)
-            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
-                " annotations; using first");
-        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
-        return this.queryAnnotation;
-    }
-
-    // json_result section should be passed here
-    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
-    throws IOException {
-        if (res == null) throw new IOException("null json_result passed");
-        if (res.optJSONArray("query_annot_uuid_list") == null)
-            throw new IOException("no query annot list");
-        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
-            throw new IOException("empty query annot list");
-        // for now we are assuming a single query annot. sorrynotsorry.
-        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
-            "query_annot_uuid_list").optJSONObject(0));
-        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
-        if (this.queryAnnotation == null)
-            throw new IOException("failed to load query annot from id=" + queryAnnotId);
-        if (res.optJSONObject("cm_dict") == null)
-            throw new IOException("no cm_dict found in " + res);
-        // results is the real scores (etc) we are looking for.... finally!
-        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
-        if (results == null) throw new IOException("no actual results found");
-        // see note at top about true annot list of candidates vs number
-        if (res.optJSONArray("database_annot_uuid_list") != null)
-            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
-/*
-        annot_score_list <=> dannot_uuid_list
-        score_list is for indiv scores but on dannot_uuid_list (same length)
-        name_score_list <=> unique_name_uuid_list ???
- */
-        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
-            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
-            results.optString("dannot_extern_reference", null), myShepherd);
-        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
-            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
-            results.optString("dannot_extern_reference", null), myShepherd);
-        System.out.println("[DEBUG] createFromJsonResult() created " + this);
-    }
-
-    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
-        JSONArray externs, String externRef, Shepherd myShepherd)
-    throws IOException {
-        if ((annotIds == null) || (scores == null))
-            throw new IOException("null annotIds or scores");
-        if (annotIds.length() != scores.length())
-            throw new IOException("mismatch in size of annotIds/scores");
-        if (this.prospects == null)
-            this.prospects = new HashSet<MatchResultProspect>();
-        int num = 0;
-        this.numberProspects += annotIds.length(); // true number of prospects
-        for (int i = 0; i < annotIds.length(); i++) {
-            double score = scores.optDouble(i, -Double.MAX_VALUE);
-            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
-            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
-            if (ann == null) {
-                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
-                    "; skipping; score=" + score);
-                continue;
-            }
-            MediaAsset ma = null;
-            // we only try if we have a true value in externs[i]
-            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
-                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
-            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
-            num++;
-            if (num >= MAXIMUM_PROSPECTS_STORED) {
-                System.out.println("[DEBUG] hit max (" + MAXIMUM_PROSPECTS_STORED +
-                    ") number storable prospects on " + this);
-                break;
-            }
-        }
-        return num;
-    }
-
-    // we just have a list of annots which matched (e.g. via vectors in opensearch)
-    // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
-    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
-    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
-        Shepherd myShepherd)
-    throws IOException {
-        if (Util.collectionIsEmptyOrNull(annots)) return 0;
-        if (this.prospects == null)
-            this.prospects = new HashSet<MatchResultProspect>();
-        if (scoreByIndividual) {
-            // the scores for these are calculated weighted by indiv count
-            _populateProspectsByIndividual(annots, myShepherd);
-        } else {
-            // these scores are direct from opensearch
-            for (Annotation ann : annots) {
-                MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ann, myShepherd);
-                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
-                    ma));
-            }
-        }
-        this.numberProspects = this.prospects.size();
-        return this.numberProspects;
-    }
-
-    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
-        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
-            List<Annotation> >();
-
-        for (Annotation ann : annots) {
-            Encounter enc = ann.findEncounter(myShepherd);
-            // i think we just ignore if no enc/indiv
-            if (enc == null) continue;
-            MarkedIndividual indiv = enc.getIndividual();
-            if (indiv == null) continue;
-            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
-            tally.get(indiv).add(ann);
-        }
-        if (tally.size() < 1) return; // no individuals i guess?
-
-        // this sorts by most annots (per indiv) highest to lowest
-        List<Map.Entry<MarkedIndividual,
-            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
-        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
-        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
-            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
-            Map.Entry<MarkedIndividual, List<Annotation> > two) {
-                // we reverse order here so we get largest first
-                return Integer.compare(two.getValue().size(), one.getValue().size());
-            }
-        });
-        int most = sorted.get(0).getValue().size(); // top num of annots
-        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
-            double score = new Double(ent.getValue().size()) / new Double(most);
-            // the ent value (annot List) should always have at least one annot, so we use first one
-            MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ent.getValue().get(0),
-                myShepherd);
-            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv", ma));
-        }
-    }
-
-    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
-        if (acmId == null) return null;
-        Annotation found = findAcmIdInTaskAnnotations(acmId);
-        if (found != null) return found;
-        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
-        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
-            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
-        if ((anns == null) || (anns.size() < 1)) return null;
-        return anns.get(0);
-    }
-
-    private Annotation findAcmIdInTaskAnnotations(String acmId) {
-        if ((this.task == null) || (acmId == null)) return null;
-        if (!this.task.hasObjectAnnotations()) return null;
-        for (Annotation ann : this.task.getObjectAnnotations()) {
-            if (acmId.equals(ann.getAcmId())) return ann;
-        }
-        return null;
-    }
-
-    // if it exists, we just return the thing, other wise we attempt to create it
-    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
-        Shepherd myShepherd) {
-        if (externRef == null) return null;
-        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
-        url += "&query_annot_uuid=" + this.queryAnnotation.getAcmId();
-        url += "&database_annot_uuid=" + annotId;
-        url += "&version=heatmask";
-        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
-        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
-            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
-        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
-        MediaAsset ma = null;
-        try {
-            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
-            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
-            ma.addLabel("matchInspectionHeatmap");
-            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
-                " and created " + ma);
-            tmpFile.delete();
-        } catch (Exception ex) {
-            System.out.println(
-                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
-                " => " + tmpFile + ": " + ex);
-            ex.printStackTrace();
-        }
-        return ma;
-    }
-
-/*
-   notes on pairx payload:
-   - image1_uris / image2_uris accept URLs or local file paths (as seen by the server)
-   - If you provide 1 image1 and N image2s, it compares that single image1 against each image2 (1-to-many)
-   - If you provide N of each, they're compared pairwise (N-to-N, max 16 pairs)
-   - bb1/bb2 are bounding boxes as [x, y, width, height]
-   - visualization_type options: "lines_and_colors", "only_lines", "only_colors"
-   - layer_key controls feature depth — earlier layers (e.g. backbone.blocks.1) give point-specific matches, later layers
-    (e.g. backbone.blocks.5) give broader region matches
- */
-    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
-        Shepherd myShepherd) {
-        if ((ann1 == null) || (ann2 == null)) return null;
-        MediaAsset ma1 = ann1.getMediaAsset();
-        MediaAsset ma2 = ann2.getMediaAsset();
-        if ((ma1 == null) || (ma2 == null)) return null;
-        // we need this to find MLService endpoint
-        Encounter enc = ann1.findEncounter(myShepherd);
-        if (enc == null) return null;
-        JSONObject payload = new JSONObject();
-        payload.put("algorithm", "pairx");
-        payload.put("visualization_type", "only_colors");
-        payload.put("k_colors", 5);
-        // payload.put("k_lines", 20);
-        payload.put("model_id", "miewid-msv4.1");
-        payload.put("crop_bbox", false);
-        payload.put("layer_key", "backbone.blocks.3");
-        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
-        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
-        payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
-        payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
-        // this needs an array of array(s)
-        JSONArray tmpArr = new JSONArray();
-        tmpArr.put(0, ann1.getBbox());
-        payload.put("bb1", tmpArr);
-        tmpArr.put(0, ann2.getBbox());
-        payload.put("bb2", tmpArr);
-
-        // get the image data from pairx endpoint
-        JSONObject res = null;
-        URL pairxUrl = null;
-        try {
-            pairxUrl = _getPairxUrl(enc.getTaxonomyString());
-            if (pairxUrl == null) return null;
-            res = RestClient.postJSON(pairxUrl, payload, null);
-        } catch (Exception ex) {
-            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
-                " failed: " + ex + "; payload=" + payload);
-            ex.printStackTrace();
-        }
-        if (res == null) return null;
-        JSONArray imgs = res.optJSONArray("images");
-        if ((imgs == null) || (imgs.length() < 1)) return null;
-        String b64 = imgs.optString(0, null);
-        if (b64 == null) return null;
-        // create the asset from base64 data
-        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
-            " got image data length=" + b64.length());
-        try {
-            AssetStore store = AssetStore.getDefault(myShepherd);
-            JSONObject params = store.createParameters(new File(Util.hashDirectories(this.id) +
-                "/pairx-" + this.id + "-" + ann1.getId() + "-" + ann2.getId() + ".png"));
-            MediaAsset ma = store.create(params);
-            ma.copyInBase64(b64);
-            ma.addLabel("matchInspectionPairx");
-            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
-            myShepherd.getPM().makePersistent(ma);
-            return ma;
-        } catch (Exception ex) {
-            System.out.println(
-                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
-            ex.printStackTrace();
-        }
-        return null;
-    }
-
-    public static URL _getPairxUrl(String txStr)
-    throws IOException {
-        if (txStr == null) throw new IOException("passed null taxonomy");
-        String urlStr = null;
-        try {
-            MLService mls = new MLService();
-            List<JSONObject> confs = mls.getConfigs(txStr);
-            if (confs.size() < 1) throw new IOException("empty MLService configs for tx=" + txStr);
-            urlStr = confs.get(0).optString("api_endpoint", null);
-        } catch (IAException ex) {
-            throw new IOException(ex);
-        }
-        if (urlStr == null) return null;
-        return new URL(urlStr + "/explain/");
-    }
-
-    public JSONObject getTaskParameters() {
-        if (task == null) return null;
-        return task.getParameters();
-    }
-
-    public JSONObject getTaskMatchingSetFilter() {
-        if (task == null) return null;
-        JSONObject params = task.getParameters();
-        if (params == null) return null;
-        return params.optJSONObject("matchingSetFilter");
-    }
-
-/*
-    see note at top about candidates vs numberCandidates
-    public int numberCandidates() {
-        return Util.collectionSize(candidates);
-    }
- */
-    public int numberProspects() {
-        return this.numberProspects;
-    }
-
-    public Set<String> prospectScoreTypes() {
-        Set<String> types = new HashSet<String>();
-
-        if (numberProspects() == 0) return types;
-        for (MatchResultProspect mrp : prospects) {
-            types.add(mrp.getType());
-        }
-        return types;
-    }
-
-    // if cutoff < 0 then it will not be truncated at all
-    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
-        Set<String> projectIds, Shepherd myShepherd) {
-        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
-
-        if (numberProspects() == 0) return pros;
-        for (MatchResultProspect mrp : prospects) {
-            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
-        }
-        Collections.sort(pros);
-        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
-        return pros;
-    }
-
-    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
-        JSONObject sj = new JSONObject();
-
-        for (String type : prospectScoreTypes()) {
-            JSONArray jarr = new JSONArray();
-            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
-                jarr.put(mrp.jsonForApiGet(myShepherd));
-            }
-            sj.put(type, jarr);
-        }
-        return sj;
-    }
-
-    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
-        JSONObject rtn = new JSONObject();
-
-        rtn.put("id", id);
-        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
-        rtn.put("numberTotalProspects", numberProspects());
-        rtn.put("numberCandidates", getNumberCandidates());
-        rtn.put("created", Util.millisToISO8601String(created));
-        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
-        rtn.put("projectIds", projectIds);
-        return rtn;
-    }
-
-    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
-        JSONObject aj = new JSONObject();
-
-        if (ann == null) return aj;
-        MediaAsset ma = ann.getMediaAsset();
-        // populate bounding box stuff (note: it may reset aj so must be done first)
-        if (ann.getFeatures() != null) {
-            for (Feature ft : ann.getFeatures()) {
-                if (ft.isUnity()) {
-                    aj.put("trivial", true);
-                    aj.put("x", 0);
-                    aj.put("y", 0);
-                    // would be weird to be null, but.....
-                    if (ma != null) {
-                        aj.put("width", (int)ma.getWidth());
-                        aj.put("height", (int)ma.getHeight());
-                    }
-                } else {
-                    // basically if we have more than one feature, only one wins
-                    if (ft.getParameters() != null) aj = ft.getParameters();
-                }
-            }
-        }
-        if (ma != null) {
-            JSONObject mj = ma.toSimpleJSONObject();
-            mj.put("rotationInfo", ma.getRotationInfo());
-            aj.put("asset", mj);
-        }
-        Encounter enc = ann.findEncounter(myShepherd);
-        if (enc != null) {
-            JSONObject ej = new JSONObject();
-            // TODO add "access" permission value if needed?
-            ej.put("id", enc.getId());
-            ej.put("taxonomy", enc.getTaxonomyString());
-            ej.put("locationId", enc.getLocationID());
-            aj.put("encounter", ej);
-            MarkedIndividual indiv = enc.getIndividual();
-            if (indiv != null) {
-                JSONObject ij = new JSONObject();
-                ij.put("id", indiv.getId());
-                ij.put("taxonomy", indiv.getTaxonomyString());
-                ij.put("displayName", indiv.getDisplayName());
-                ij.put("nickname", indiv.getNickName());
-                ij.put("sex", indiv.getSex());
-                ij.put("numberEncounters", indiv.getNumEncounters());
-                aj.put("individual", ij);
-            }
-        }
-        aj.put("id", ann.getId());
-        // ml-service migration v2 §commit #11: surface WBIA registration
-        // state so the frontend can disable the "Match with HotSpotter"
-        // button until WBIA has acknowledged the annotation. tri-state:
-        // null = legacy or not-yet-pending; false = pending registration;
-        // true = WBIA acknowledged. Frontend treats anything non-true as
-        // "HotSpotter not available yet" with a tooltip.
-        Boolean wbiaReg = ann.getWbiaRegistered();
-        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
-        return aj;
-    }
-
-    public String toString() {
-        String s = "MatchResult " + id;
-
-        s += " [" + Util.millisToISO8601String(created) + "]";
-        s += " query " + queryAnnotation;
-        s += "; numCandidates=" + this.getNumberCandidates();
-        s += "; numProspects=" + this.numberProspects();
-        s += "; task=" + (task == null ? "null" : task.getId());
-        return s;
-    }
-}
+package org.ecocean.ia;
+
+import java.io.File;
+import java.io.IOException;
+import java.net.URL;
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.Comparator;
+import java.util.HashMap;
+import java.util.HashSet;
+import java.util.List;
+import java.util.Map;
+import java.util.Set;
+
+import org.json.JSONArray;
+import org.json.JSONObject;
+
+import org.ecocean.api.UploadedFiles;
+import org.ecocean.Annotation;
+import org.ecocean.Encounter;
+import org.ecocean.ia.MLService;
+import org.ecocean.ia.Task;
+import org.ecocean.identity.IBEISIA;
+import org.ecocean.identity.IdentityServiceLog;
+import org.ecocean.media.AssetStore;
+import org.ecocean.media.Feature;
+import org.ecocean.media.MediaAsset;
+import org.ecocean.media.URLAssetStore;
+import org.ecocean.MarkedIndividual;
+import org.ecocean.RestClient;
+import org.ecocean.shepherd.core.Shepherd;
+import org.ecocean.Util;
+
+public class MatchResult implements java.io.Serializable {
+    private String id;
+    private long created;
+    private Task task;
+    private Set<MatchResultProspect> prospects;
+    private Annotation queryAnnotation;
+    private int numberCandidates = 0;
+    // we store *actual* count here, but they may not all exist
+    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
+    private int numberProspects = 0;
+    // not sure we really *need* true fk link to these annots
+    // they might be gone now and will we ever use this?
+    // so for now we just populate numberCandidates
+    private Set<Annotation> candidates;
+    // fallback number to cutoff number of prospects to return
+    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
+    // number of MatchResultProspects [per type] to actually store (hotspotter
+    // results can produce thousands, but storing them all is excessive)
+    public static final int MAXIMUM_PROSPECTS_STORED = 500;
+
+    public MatchResult() {
+        id = Util.generateUUID();
+        created = System.currentTimeMillis();
+    }
+
+    public MatchResult(Task task) {
+        this();
+        this.task = task;
+    }
+
+    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
+    throws IOException {
+        this();
+        this.createFromIdentityServiceLog(isLog, myShepherd);
+    }
+
+    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
+    throws IOException {
+        this();
+        this.task = task;
+        this.createFromJsonResult(jsonResult, myShepherd);
+    }
+
+    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
+        Shepherd myShepherd)
+    throws IOException {
+        this();
+        this.task = task;
+        this.numberCandidates = numberCandidates;
+        this.setQueryAnnotationFromTask();
+        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
+        this.populateProspects(annots, false, myShepherd);
+        this.populateProspects(annots, true, myShepherd);
+    }
+
+    public int getNumberCandidates() {
+        return numberCandidates;
+    }
+
+    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
+    throws IOException {
+        if (isLog == null) throw new IOException("log passed is null");
+        String taskId = isLog.getTaskID();
+        this.task = myShepherd.getTask(taskId);
+        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
+        JSONObject res = isLog.getJsonResult();
+        if (res == null) {
+            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
+                isLog.getStatusJson());
+            throw new IOException("could not get json result");
+        }
+        createFromJsonResult(res, myShepherd);
+    }
+
+    public Annotation setQueryAnnotationFromTask()
+    throws IOException {
+        if (this.task == null)
+            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
+        int numAnns = this.task.countObjectAnnotations();
+        if (numAnns < 1)
+            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
+        if (numAnns > 1)
+            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
+                " annotations; using first");
+        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
+        return this.queryAnnotation;
+    }
+
+    // json_result section should be passed here
+    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
+    throws IOException {
+        if (res == null) throw new IOException("null json_result passed");
+        if (res.optJSONArray("query_annot_uuid_list") == null)
+            throw new IOException("no query annot list");
+        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
+            throw new IOException("empty query annot list");
+        // for now we are assuming a single query annot. sorrynotsorry.
+        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
+            "query_annot_uuid_list").optJSONObject(0));
+        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
+        if (this.queryAnnotation == null)
+            throw new IOException("failed to load query annot from id=" + queryAnnotId);
+        if (res.optJSONObject("cm_dict") == null)
+            throw new IOException("no cm_dict found in " + res);
+        // results is the real scores (etc) we are looking for.... finally!
+        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
+        if (results == null) throw new IOException("no actual results found");
+        // see note at top about true annot list of candidates vs number
+        if (res.optJSONArray("database_annot_uuid_list") != null)
+            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
+/*
+        annot_score_list <=> dannot_uuid_list
+        score_list is for indiv scores but on dannot_uuid_list (same length)
+        name_score_list <=> unique_name_uuid_list ???
+ */
+        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
+            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
+            results.optString("dannot_extern_reference", null), myShepherd);
+        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
+            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
+            results.optString("dannot_extern_reference", null), myShepherd);
+        System.out.println("[DEBUG] createFromJsonResult() created " + this);
+    }
+
+    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
+        JSONArray externs, String externRef, Shepherd myShepherd)
+    throws IOException {
+        if ((annotIds == null) || (scores == null))
+            throw new IOException("null annotIds or scores");
+        if (annotIds.length() != scores.length())
+            throw new IOException("mismatch in size of annotIds/scores");
+        if (this.prospects == null)
+            this.prospects = new HashSet<MatchResultProspect>();
+        int num = 0;
+        this.numberProspects += annotIds.length(); // true number of prospects
+        for (int i = 0; i < annotIds.length(); i++) {
+            double score = scores.optDouble(i, -Double.MAX_VALUE);
+            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
+            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
+            if (ann == null) {
+                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
+                    "; skipping; score=" + score);
+                continue;
+            }
+            MediaAsset ma = null;
+            // we only try if we have a true value in externs[i]
+            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
+                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
+            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
+            num++;
+            if (num >= MAXIMUM_PROSPECTS_STORED) {
+                System.out.println("[DEBUG] hit max (" + MAXIMUM_PROSPECTS_STORED +
+                    ") number storable prospects on " + this);
+                break;
+            }
+        }
+        return num;
+    }
+
+    // we just have a list of annots which matched (e.g. via vectors in opensearch)
+    // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
+    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
+    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
+        Shepherd myShepherd)
+    throws IOException {
+        if (Util.collectionIsEmptyOrNull(annots)) return 0;
+        if (this.prospects == null)
+            this.prospects = new HashSet<MatchResultProspect>();
+        if (scoreByIndividual) {
+            // the scores for these are calculated weighted by indiv count
+            _populateProspectsByIndividual(annots, myShepherd);
+        } else {
+            // these scores are direct from opensearch
+            for (Annotation ann : annots) {
+                MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ann, myShepherd);
+                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
+                    ma));
+            }
+        }
+        this.numberProspects = this.prospects.size();
+        return this.numberProspects;
+    }
+
+    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
+        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
+            List<Annotation> >();
+
+        for (Annotation ann : annots) {
+            Encounter enc = ann.findEncounter(myShepherd);
+            // i think we just ignore if no enc/indiv
+            if (enc == null) continue;
+            MarkedIndividual indiv = enc.getIndividual();
+            if (indiv == null) continue;
+            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
+            tally.get(indiv).add(ann);
+        }
+        if (tally.size() < 1) return; // no individuals i guess?
+
+        // this sorts by most annots (per indiv) highest to lowest
+        List<Map.Entry<MarkedIndividual,
+            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
+        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
+        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
+            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
+            Map.Entry<MarkedIndividual, List<Annotation> > two) {
+                // we reverse order here so we get largest first
+                return Integer.compare(two.getValue().size(), one.getValue().size());
+            }
+        });
+        int most = sorted.get(0).getValue().size(); // top num of annots
+        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
+            double score = new Double(ent.getValue().size()) / new Double(most);
+            // the ent value (annot List) should always have at least one annot, so we use first one
+            MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ent.getValue().get(0),
+                myShepherd);
+            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv", ma));
+        }
+    }
+
+    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
+        if (acmId == null) return null;
+        Annotation found = findAcmIdInTaskAnnotations(acmId);
+        if (found != null) return found;
+        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
+        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
+            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
+        if ((anns == null) || (anns.size() < 1)) return null;
+        return anns.get(0);
+    }
+
+    private Annotation findAcmIdInTaskAnnotations(String acmId) {
+        if ((this.task == null) || (acmId == null)) return null;
+        if (!this.task.hasObjectAnnotations()) return null;
+        for (Annotation ann : this.task.getObjectAnnotations()) {
+            if (acmId.equals(ann.getAcmId())) return ann;
+        }
+        return null;
+    }
+
+    // if it exists, we just return the thing, other wise we attempt to create it
+    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
+        Shepherd myShepherd) {
+        if (externRef == null) return null;
+        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
+        url += "&query_annot_uuid=" + this.queryAnnotation.getAcmId();
+        url += "&database_annot_uuid=" + annotId;
+        url += "&version=heatmask";
+        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
+        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
+            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
+        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
+        MediaAsset ma = null;
+        try {
+            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
+            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
+            ma.addLabel("matchInspectionHeatmap");
+            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
+                " and created " + ma);
+            tmpFile.delete();
+        } catch (Exception ex) {
+            System.out.println(
+                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
+                " => " + tmpFile + ": " + ex);
+            ex.printStackTrace();
+        }
+        return ma;
+    }
+
+/*
+   notes on pairx payload:
+   - image1_uris / image2_uris accept URLs or local file paths (as seen by the server)
+   - If you provide 1 image1 and N image2s, it compares that single image1 against each image2 (1-to-many)
+   - If you provide N of each, they're compared pairwise (N-to-N, max 16 pairs)
+   - bb1/bb2 are bounding boxes as [x, y, width, height]
+   - visualization_type options: "lines_and_colors", "only_lines", "only_colors"
+   - layer_key controls feature depth — earlier layers (e.g. backbone.blocks.1) give point-specific matches, later layers
+    (e.g. backbone.blocks.5) give broader region matches
+ */
+    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
+        Shepherd myShepherd) {
+        if ((ann1 == null) || (ann2 == null)) return null;
+        MediaAsset ma1 = ann1.getMediaAsset();
+        MediaAsset ma2 = ann2.getMediaAsset();
+        if ((ma1 == null) || (ma2 == null)) return null;
+        // we need this to find MLService endpoint
+        Encounter enc = ann1.findEncounter(myShepherd);
+        if (enc == null) return null;
+        JSONObject payload = new JSONObject();
+        payload.put("algorithm", "pairx");
+        payload.put("visualization_type", "only_colors");
+        payload.put("k_colors", 5);
+        // payload.put("k_lines", 20);
+        payload.put("model_id", "miewid-msv4.1");
+        payload.put("crop_bbox", false);
+        payload.put("layer_key", "backbone.blocks.3");
+        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
+        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
+        payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
+        payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
+        // this needs an array of array(s)
+        JSONArray tmpArr = new JSONArray();
+        tmpArr.put(0, ann1.getBbox());
+        payload.put("bb1", tmpArr);
+        tmpArr.put(0, ann2.getBbox());
+        payload.put("bb2", tmpArr);
+
+        // get the image data from pairx endpoint
+        JSONObject res = null;
+        URL pairxUrl = null;
+        try {
+            pairxUrl = _getPairxUrl(enc.getTaxonomyString());
+            if (pairxUrl == null) return null;
+            res = RestClient.postJSON(pairxUrl, payload, null);
+        } catch (Exception ex) {
+            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
+                " failed: " + ex + "; payload=" + payload);
+            ex.printStackTrace();
+        }
+        if (res == null) return null;
+        JSONArray imgs = res.optJSONArray("images");
+        if ((imgs == null) || (imgs.length() < 1)) return null;
+        String b64 = imgs.optString(0, null);
+        if (b64 == null) return null;
+        // create the asset from base64 data
+        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
+            " got image data length=" + b64.length());
+        try {
+            AssetStore store = AssetStore.getDefault(myShepherd);
+            JSONObject params = store.createParameters(new File(Util.hashDirectories(this.id) +
+                "/pairx-" + this.id + "-" + ann1.getId() + "-" + ann2.getId() + ".png"));
+            MediaAsset ma = store.create(params);
+            ma.copyInBase64(b64);
+            ma.addLabel("matchInspectionPairx");
+            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
+            myShepherd.getPM().makePersistent(ma);
+            return ma;
+        } catch (Exception ex) {
+            System.out.println(
+                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
+            ex.printStackTrace();
+        }
+        return null;
+    }
+
+    public static URL _getPairxUrl(String txStr)
+    throws IOException {
+        if (txStr == null) throw new IOException("passed null taxonomy");
+        String urlStr = null;
+        try {
+            MLService mls = new MLService();
+            List<JSONObject> confs = mls.getConfigs(txStr);
+            if (confs.size() < 1) throw new IOException("empty MLService configs for tx=" + txStr);
+            urlStr = confs.get(0).optString("api_endpoint", null);
+        } catch (IAException ex) {
+            throw new IOException(ex);
+        }
+        if (urlStr == null) return null;
+        return new URL(urlStr + "/explain/");
+    }
+
+    public JSONObject getTaskParameters() {
+        if (task == null) return null;
+        return task.getParameters();
+    }
+
+    public JSONObject getTaskMatchingSetFilter() {
+        if (task == null) return null;
+        JSONObject params = task.getParameters();
+        if (params == null) return null;
+        return params.optJSONObject("matchingSetFilter");
+    }
+
+/*
+    see note at top about candidates vs numberCandidates
+    public int numberCandidates() {
+        return Util.collectionSize(candidates);
+    }
+ */
+    public int numberProspects() {
+        return this.numberProspects;
+    }
+
+    public Set<String> prospectScoreTypes() {
+        Set<String> types = new HashSet<String>();
+
+        if (numberProspects() == 0) return types;
+        for (MatchResultProspect mrp : prospects) {
+            types.add(mrp.getType());
+        }
+        return types;
+    }
+
+    // if cutoff < 0 then it will not be truncated at all
+    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
+        Set<String> projectIds, Shepherd myShepherd) {
+        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
+
+        if (numberProspects() == 0) return pros;
+        for (MatchResultProspect mrp : prospects) {
+            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
+        }
+        Collections.sort(pros);
+        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
+        return pros;
+    }
+
+    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
+        JSONObject sj = new JSONObject();
+
+        for (String type : prospectScoreTypes()) {
+            JSONArray jarr = new JSONArray();
+            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
+                jarr.put(mrp.jsonForApiGet(myShepherd));
+            }
+            sj.put(type, jarr);
+        }
+        return sj;
+    }
+
+    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
+        JSONObject rtn = new JSONObject();
+
+        rtn.put("id", id);
+        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
+        rtn.put("numberTotalProspects", numberProspects());
+        rtn.put("numberCandidates", getNumberCandidates());
+        rtn.put("created", Util.millisToISO8601String(created));
+        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
+        rtn.put("projectIds", projectIds);
+        return rtn;
+    }
+
+    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
+        JSONObject aj = new JSONObject();
+
+        if (ann == null) return aj;
+        MediaAsset ma = ann.getMediaAsset();
+        // populate bounding box stuff (note: it may reset aj so must be done first)
+        if (ann.getFeatures() != null) {
+            for (Feature ft : ann.getFeatures()) {
+                if (ft.isUnity()) {
+                    aj.put("trivial", true);
+                    aj.put("x", 0);
+                    aj.put("y", 0);
+                    // would be weird to be null, but.....
+                    if (ma != null) {
+                        aj.put("width", (int)ma.getWidth());
+                        aj.put("height", (int)ma.getHeight());
+                    }
+                } else {
+                    // basically if we have more than one feature, only one wins
+                    if (ft.getParameters() != null) aj = ft.getParameters();
+                }
+            }
+        }
+        if (ma != null) {
+            JSONObject mj = ma.toSimpleJSONObject();
+            mj.put("rotationInfo", ma.getRotationInfo());
+            aj.put("asset", mj);
+        }
+        Encounter enc = ann.findEncounter(myShepherd);
+        if (enc != null) {
+            JSONObject ej = new JSONObject();
+            // TODO add "access" permission value if needed?
+            ej.put("id", enc.getId());
+            ej.put("taxonomy", enc.getTaxonomyString());
+            ej.put("locationId", enc.getLocationID());
+            aj.put("encounter", ej);
+            MarkedIndividual indiv = enc.getIndividual();
+            if (indiv != null) {
+                JSONObject ij = new JSONObject();
+                ij.put("id", indiv.getId());
+                ij.put("taxonomy", indiv.getTaxonomyString());
+                ij.put("displayName", indiv.getDisplayName());
+                ij.put("nickname", indiv.getNickName());
+                ij.put("sex", indiv.getSex());
+                ij.put("numberEncounters", indiv.getNumEncounters());
+                aj.put("individual", ij);
+            }
+        }
+        aj.put("id", ann.getId());
+        // ml-service migration v2 §commit #11: surface WBIA registration
+        // state so the frontend can disable the "Match with HotSpotter"
+        // button until WBIA has acknowledged the annotation. tri-state:
+        // null = legacy or not-yet-pending; false = pending registration;
+        // true = WBIA acknowledged. Frontend treats anything non-true as
+        // "HotSpotter not available yet" with a tooltip.
+        Boolean wbiaReg = ann.getWbiaRegistered();
+        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
+        return aj;
+    }
+
+    public String toString() {
+        String s = "MatchResult " + id;
+
+        s += " [" + Util.millisToISO8601String(created) + "]";
+        s += " query " + queryAnnotation;
+        s += "; numCandidates=" + this.getNumberCandidates();
+        s += "; numProspects=" + this.numberProspects();
+        s += "; task=" + (task == null ? "null" : task.getId());
+        return s;
+    }
+}
diff --git a/src/main/java/org/ecocean/ia/MatchResultProspect.java b/src/main/java/org/ecocean/ia/MatchResultProspect.java
index 32f6b1b71..0e94bb637 100644
--- a/src/main/java/org/ecocean/ia/MatchResultProspect.java
+++ b/src/main/java/org/ecocean/ia/MatchResultProspect.java
@@ -1,88 +1,88 @@
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
diff --git a/src/main/java/org/ecocean/ia/MlServiceClient.java b/src/main/java/org/ecocean/ia/MlServiceClient.java
index b7337a0a7..add09eef3 100644
--- a/src/main/java/org/ecocean/ia/MlServiceClient.java
+++ b/src/main/java/org/ecocean/ia/MlServiceClient.java
@@ -1,320 +1,320 @@
-package org.ecocean.ia;
-
-import java.io.IOException;
-import java.net.MalformedURLException;
-import java.net.SocketTimeoutException;
-import java.net.URL;
-import java.util.regex.Matcher;
-import java.util.regex.Pattern;
-
-import org.json.JSONArray;
-import org.json.JSONObject;
-
-import org.ecocean.RestClient;
-import org.ecocean.Util;
-
-/**
- * HTTP-only wrapper around ml-service ({@code /pipeline/} and {@code /extract/}
- * endpoints). Validates the response shape against the v2 contract. No
- * Shepherd, no DB; just HTTP + JSON validation.
- *
- * <p>Migration plan v2 §commit #8. Used by {@link
- * org.ecocean.ia.MlServiceProcessor} (commit #9). Tests directly via
- * {@code MlServiceClientTest}.</p>
- *
- * <h3>Retry classification (matches v2 plan §Failure ladder):</h3>
- * <ul>
- *   <li>{@link SocketTimeoutException} or message contains "timed out" →
- *       IAException retryable=true, increment=false (timeout doesn't imply
- *       overload).</li>
- *   <li>Connection refused / 502 / 503 / 504 / 5xx → retryable=true,
- *       increment=true.</li>
- *   <li>429 (rate-limited) → retryable=true, increment=true so the client
- *       backs off.</li>
- *   <li>Other 4xx, parse failure, {@code success=false} response → retryable
- *       =false; mark task error.</li>
- * </ul>
- *
- * <p>RestClient throws {@code "HTTP error code = NNN"} (literally with {@code
- * =}). The classifier accepts both {@code "= NNN"} and {@code ": NNN"}
- * spellings to be defensive against any future RestClient refactor.</p>
- */
-public class MlServiceClient {
-
-    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;
-    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
-
-    // Matches "HTTP error code = 502" or "HTTP error code : 502", capturing
-    // the status code as group 1.
-    private static final Pattern HTTP_CODE_PATTERN =
-        Pattern.compile("HTTP error code\\s*[=:]\\s*(\\d{3})");
-
-    private final int connectTimeoutMs;
-    private final int readTimeoutMs;
-
-    public MlServiceClient() {
-        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
-    }
-
-    public MlServiceClient(int connectTimeoutMs, int readTimeoutMs) {
-        this.connectTimeoutMs = connectTimeoutMs;
-        this.readTimeoutMs = readTimeoutMs;
-    }
-
-    // ---------------------------------------------------------------------
-    // Public API
-    // ---------------------------------------------------------------------
-
-    /**
-     * POSTs to {@code apiEndpoint/pipeline/} with the predict/classify/extract/
-     * orientation model IDs from {@code config}. Returns the validated response.
-     *
-     * @param apiEndpoint base URL of ml-service (no trailing slash required)
-     * @param imageUri    URL or local path of the image to process
-     * @param config      a single {@code _mlservice_conf} entry from IA.json
-     * @return validated response JSON ({@code success:true, results:[...]})
-     * @throws IAException on network failure or response-validation failure;
-     *         {@code shouldRequeue()} and {@code getCode()} carry the
-     *         classification. Codes: {@code TIMEOUT}, {@code NETWORK},
-     *         {@code SERVER_ERROR}, {@code RATE_LIMITED},
-     *         {@code CLIENT_ERROR}, {@code SUCCESS_FALSE}, {@code INVALID}.
-     */
-    public JSONObject pipeline(String apiEndpoint, String imageUri, JSONObject config)
-    throws IAException {
-        JSONObject payload = buildPipelinePayload(imageUri, config);
-        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/pipeline/"),
-            payload);
-        validatePipelineResponse(response, config.optInt("embedding_dimension", 0));
-        return response;
-    }
-
-    /**
-     * POSTs to {@code apiEndpoint/extract/}. Used for manual annotations
-     * (user-drawn bbox; no detection step needed).
-     *
-     * @throws IAException same contract as {@link #pipeline}.
-     */
-    public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
-        double theta, JSONObject config)
-    throws IAException {
-        JSONObject payload = buildExtractPayload(imageUri, bbox, theta, config);
-        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/extract/"),
-            payload);
-        validateExtractResponse(response, config.optInt("embedding_dimension", 0));
-        return response;
-    }
-
-    // ---------------------------------------------------------------------
-    // Internal helpers (package-visible for unit tests)
-    // ---------------------------------------------------------------------
-
-    static String joinEndpoint(String base, String path) {
-        if (base == null) return path;
-        String trimmed = base.replaceAll("/+$", "");
-        return trimmed + path;
-    }
-
-    static JSONObject buildPipelinePayload(String imageUri, JSONObject config) {
-        JSONObject p = new JSONObject();
-        p.put("image_uri", imageUri);
-        if (config != null) {
-            if (config.has("predict_model_id"))
-                p.put("predict_model_id", config.opt("predict_model_id"));
-            if (config.has("classify_model_id"))
-                p.put("classify_model_id", config.opt("classify_model_id"));
-            if (config.has("extract_model_id"))
-                p.put("extract_model_id", config.opt("extract_model_id"));
-            if (config.has("orientation_model_id"))
-                p.put("orientation_model_id", config.opt("orientation_model_id"));
-        }
-        return p;
-    }
-
-    static JSONObject buildExtractPayload(String imageUri, double[] bbox, double theta,
-        JSONObject config) {
-        JSONObject p = new JSONObject();
-        p.put("image_uri", imageUri);
-        if (config != null && config.has("extract_model_id")) {
-            p.put("extract_model_id", config.opt("extract_model_id"));
-        }
-        if (bbox != null) {
-            JSONArray b = new JSONArray();
-            for (double v : bbox) b.put(v);
-            p.put("bbox", b);
-        }
-        p.put("theta", theta);
-        return p;
-    }
-
-    static void validatePipelineResponse(JSONObject response, int expectedDim)
-    throws IAException {
-        if (response == null)
-            throw new IAException("INVALID", "/pipeline/ returned null", false, false);
-        if (!response.optBoolean("success", false))
-            throw new IAException("SUCCESS_FALSE",
-                "/pipeline/ returned success=false: " + response, false, false);
-        JSONArray results = response.optJSONArray("results");
-        if (results == null)
-            throw new IAException("INVALID",
-                "/pipeline/ response missing 'results' array: " + response, false, false);
-        // Zero detections is a valid response. Each present result must be
-        // structurally complete; we reject the whole response on any partial
-        // result rather than persist a subset.
-        for (int i = 0; i < results.length(); i++) {
-            JSONObject r = results.optJSONObject(i);
-            if (r == null)
-                throw new IAException("INVALID",
-                    "/pipeline/ results[" + i + "] is not an object", false, false);
-            validateBbox(r.optJSONArray("bbox"), i);
-            // theta must be present AND finite. Default-on-missing (e.g.
-            // optDouble("theta", 0.0)) would accept a malformed result and
-            // persist a fabricated orientation. Require presence.
-            if (!r.has("theta"))
-                throw new IAException("INVALID",
-                    "/pipeline/ results[" + i + "] missing theta", false, false);
-            double theta = r.optDouble("theta", Double.NaN);
-            if (!isFiniteDouble(theta))
-                throw new IAException("INVALID",
-                    "/pipeline/ results[" + i + "] theta non-finite", false, false);
-            validateEmbeddingField(r, "embedding", expectedDim, "results[" + i + "]");
-        }
-    }
-
-    static void validateExtractResponse(JSONObject response, int expectedDim)
-    throws IAException {
-        if (response == null)
-            throw new IAException("INVALID", "/extract/ returned null", false, false);
-        if (!response.optBoolean("success", false))
-            throw new IAException("SUCCESS_FALSE",
-                "/extract/ returned success=false: " + response, false, false);
-        validateEmbeddingField(response, "embedding", expectedDim, "response");
-    }
-
-    private static void validateBbox(JSONArray bbox, int idx)
-    throws IAException {
-        if (bbox == null || bbox.length() != 4)
-            throw new IAException("INVALID",
-                "/pipeline/ results[" + idx + "] bbox must be a 4-element array", false, false);
-        for (int j = 0; j < 4; j++) {
-            double v = bbox.optDouble(j, Double.NaN);
-            if (!isFiniteDouble(v))
-                throw new IAException("INVALID",
-                    "/pipeline/ results[" + idx + "] bbox[" + j + "] non-finite", false, false);
-        }
-        if (bbox.optDouble(2) < 1.0 || bbox.optDouble(3) < 1.0)
-            throw new IAException("INVALID",
-                "/pipeline/ results[" + idx + "] bbox width/height must be >= 1", false, false);
-    }
-
-    private static void validateEmbeddingField(JSONObject parent, String fieldName,
-        int expectedDim, String context)
-    throws IAException {
-        JSONArray emb = parent.optJSONArray(fieldName);
-        if (emb == null)
-            throw new IAException("INVALID",
-                context + " missing '" + fieldName + "' array", false, false);
-        if (expectedDim > 0 && emb.length() != expectedDim)
-            throw new IAException("INVALID",
-                context + " embedding length " + emb.length() + " != expected " + expectedDim,
-                false, false);
-        if (emb.length() == 0)
-            throw new IAException("INVALID",
-                context + " embedding array is empty", false, false);
-        for (int j = 0; j < emb.length(); j++) {
-            double v = emb.optDouble(j, Double.NaN);
-            if (!isFiniteDouble(v))
-                throw new IAException("INVALID",
-                    context + " embedding[" + j + "] non-finite", false, false);
-        }
-        String modelId = parent.optString("embedding_model_id", null);
-        String modelVer = parent.optString("embedding_model_version", null);
-        if (!Util.stringExists(modelId) || !Util.stringExists(modelVer))
-            throw new IAException("INVALID",
-                context + " missing embedding_model_id or embedding_model_version",
-                false, false);
-    }
-
-    private static boolean isFiniteDouble(double v) {
-        return !Double.isNaN(v) && !Double.isInfinite(v);
-    }
-
-    private JSONObject postWithClassification(String url, JSONObject payload)
-    throws IAException {
-        URL u;
-        try {
-            u = new URL(url);
-        } catch (MalformedURLException ex) {
-            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
-        }
-        try {
-            return RestClient.postJSON(u, payload, null, connectTimeoutMs, readTimeoutMs);
-        } catch (Exception ex) {
-            throw classifyHttpFailure(ex, url);
-        }
-    }
-
-    /** Classify a RestClient throw into the v2 failure-ladder buckets. */
-    static IAException classifyHttpFailure(Exception ex, String url) {
-        // Detect timeout primarily by exception type; fall back to message
-        // sniffing for environments where the cause chain is flattened.
-        for (Throwable t = ex; t != null; t = t.getCause()) {
-            if (t instanceof SocketTimeoutException) {
-                return new IAException("TIMEOUT",
-                    "ml-service timeout on " + url + ": " + ex.getMessage(), true, false);
-            }
-        }
-        String msg = ex.getMessage() == null ? "" : ex.getMessage();
-        if (msg.contains("timed out")) {
-            return new IAException("TIMEOUT",
-                "ml-service timeout on " + url + ": " + msg, true, false);
-        }
-        // Connection refused and Connection reset are both transient peer-side
-        // conditions; retry with increment so the back-off counter advances.
-        if (msg.contains("Connection refused") || msg.contains("Connection reset")) {
-            return new IAException("NETWORK",
-                "ml-service connection error on " + url + ": " + msg, true, true);
-        }
-        // Parse failures from RestClient.postJSON: the response was a 200 OK
-        // but the body wasn't valid JSON. That's a contract violation by
-        // ml-service, not a network issue. Classify as INVALID, non-retryable.
-        if (msg.contains("could not convert postRaw()")) {
-            return new IAException("INVALID",
-                "ml-service returned non-JSON 200 on " + url + ": " + msg, false, false);
-        }
-        Matcher m = HTTP_CODE_PATTERN.matcher(msg);
-        if (m.find()) {
-            int statusCode;
-            try {
-                statusCode = Integer.parseInt(m.group(1));
-            } catch (NumberFormatException nfe) {
-                statusCode = 0;
-            }
-            // 408 (Request Timeout) — typically emitted by a proxy/LB in front
-            // of ml-service; treat like a normal timeout (retry, no increment).
-            if (statusCode == 408) {
-                return new IAException("TIMEOUT",
-                    "ml-service 408 on " + url, true, false);
-            }
-            if (statusCode == 429) {
-                return new IAException("RATE_LIMITED",
-                    "ml-service rate-limited (429) on " + url, true, true);
-            }
-            if (statusCode == 502 || statusCode == 503 || statusCode == 504) {
-                return new IAException("NETWORK",
-                    "ml-service " + statusCode + " on " + url, true, true);
-            }
-            if (statusCode >= 500 && statusCode < 600) {
-                return new IAException("SERVER_ERROR",
-                    "ml-service " + statusCode + " on " + url, true, true);
-            }
-            if (statusCode >= 400 && statusCode < 500) {
-                return new IAException("CLIENT_ERROR",
-                    "ml-service " + statusCode + " on " + url + " (non-retryable)",
-                    false, false);
-            }
-        }
-        // Unrecognized; treat as non-retryable to avoid spinning.
-        return new IAException("NETWORK",
-            "ml-service request failed on " + url + ": " + msg, false, false);
-    }
-}
+package org.ecocean.ia;
+
+import java.io.IOException;
+import java.net.MalformedURLException;
+import java.net.SocketTimeoutException;
+import java.net.URL;
+import java.util.regex.Matcher;
+import java.util.regex.Pattern;
+
+import org.json.JSONArray;
+import org.json.JSONObject;
+
+import org.ecocean.RestClient;
+import org.ecocean.Util;
+
+/**
+ * HTTP-only wrapper around ml-service ({@code /pipeline/} and {@code /extract/}
+ * endpoints). Validates the response shape against the v2 contract. No
+ * Shepherd, no DB; just HTTP + JSON validation.
+ *
+ * <p>Migration plan v2 §commit #8. Used by {@link
+ * org.ecocean.ia.MlServiceProcessor} (commit #9). Tests directly via
+ * {@code MlServiceClientTest}.</p>
+ *
+ * <h3>Retry classification (matches v2 plan §Failure ladder):</h3>
+ * <ul>
+ *   <li>{@link SocketTimeoutException} or message contains "timed out" →
+ *       IAException retryable=true, increment=false (timeout doesn't imply
+ *       overload).</li>
+ *   <li>Connection refused / 502 / 503 / 504 / 5xx → retryable=true,
+ *       increment=true.</li>
+ *   <li>429 (rate-limited) → retryable=true, increment=true so the client
+ *       backs off.</li>
+ *   <li>Other 4xx, parse failure, {@code success=false} response → retryable
+ *       =false; mark task error.</li>
+ * </ul>
+ *
+ * <p>RestClient throws {@code "HTTP error code = NNN"} (literally with {@code
+ * =}). The classifier accepts both {@code "= NNN"} and {@code ": NNN"}
+ * spellings to be defensive against any future RestClient refactor.</p>
+ */
+public class MlServiceClient {
+
+    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;
+    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
+
+    // Matches "HTTP error code = 502" or "HTTP error code : 502", capturing
+    // the status code as group 1.
+    private static final Pattern HTTP_CODE_PATTERN =
+        Pattern.compile("HTTP error code\\s*[=:]\\s*(\\d{3})");
+
+    private final int connectTimeoutMs;
+    private final int readTimeoutMs;
+
+    public MlServiceClient() {
+        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
+    }
+
+    public MlServiceClient(int connectTimeoutMs, int readTimeoutMs) {
+        this.connectTimeoutMs = connectTimeoutMs;
+        this.readTimeoutMs = readTimeoutMs;
+    }
+
+    // ---------------------------------------------------------------------
+    // Public API
+    // ---------------------------------------------------------------------
+
+    /**
+     * POSTs to {@code apiEndpoint/pipeline/} with the predict/classify/extract/
+     * orientation model IDs from {@code config}. Returns the validated response.
+     *
+     * @param apiEndpoint base URL of ml-service (no trailing slash required)
+     * @param imageUri    URL or local path of the image to process
+     * @param config      a single {@code _mlservice_conf} entry from IA.json
+     * @return validated response JSON ({@code success:true, results:[...]})
+     * @throws IAException on network failure or response-validation failure;
+     *         {@code shouldRequeue()} and {@code getCode()} carry the
+     *         classification. Codes: {@code TIMEOUT}, {@code NETWORK},
+     *         {@code SERVER_ERROR}, {@code RATE_LIMITED},
+     *         {@code CLIENT_ERROR}, {@code SUCCESS_FALSE}, {@code INVALID}.
+     */
+    public JSONObject pipeline(String apiEndpoint, String imageUri, JSONObject config)
+    throws IAException {
+        JSONObject payload = buildPipelinePayload(imageUri, config);
+        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/pipeline/"),
+            payload);
+        validatePipelineResponse(response, config.optInt("embedding_dimension", 0));
+        return response;
+    }
+
+    /**
+     * POSTs to {@code apiEndpoint/extract/}. Used for manual annotations
+     * (user-drawn bbox; no detection step needed).
+     *
+     * @throws IAException same contract as {@link #pipeline}.
+     */
+    public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
+        double theta, JSONObject config)
+    throws IAException {
+        JSONObject payload = buildExtractPayload(imageUri, bbox, theta, config);
+        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/extract/"),
+            payload);
+        validateExtractResponse(response, config.optInt("embedding_dimension", 0));
+        return response;
+    }
+
+    // ---------------------------------------------------------------------
+    // Internal helpers (package-visible for unit tests)
+    // ---------------------------------------------------------------------
+
+    static String joinEndpoint(String base, String path) {
+        if (base == null) return path;
+        String trimmed = base.replaceAll("/+$", "");
+        return trimmed + path;
+    }
+
+    static JSONObject buildPipelinePayload(String imageUri, JSONObject config) {
+        JSONObject p = new JSONObject();
+        p.put("image_uri", imageUri);
+        if (config != null) {
+            if (config.has("predict_model_id"))
+                p.put("predict_model_id", config.opt("predict_model_id"));
+            if (config.has("classify_model_id"))
+                p.put("classify_model_id", config.opt("classify_model_id"));
+            if (config.has("extract_model_id"))
+                p.put("extract_model_id", config.opt("extract_model_id"));
+            if (config.has("orientation_model_id"))
+                p.put("orientation_model_id", config.opt("orientation_model_id"));
+        }
+        return p;
+    }
+
+    static JSONObject buildExtractPayload(String imageUri, double[] bbox, double theta,
+        JSONObject config) {
+        JSONObject p = new JSONObject();
+        p.put("image_uri", imageUri);
+        if (config != null && config.has("extract_model_id")) {
+            p.put("extract_model_id", config.opt("extract_model_id"));
+        }
+        if (bbox != null) {
+            JSONArray b = new JSONArray();
+            for (double v : bbox) b.put(v);
+            p.put("bbox", b);
+        }
+        p.put("theta", theta);
+        return p;
+    }
+
+    static void validatePipelineResponse(JSONObject response, int expectedDim)
+    throws IAException {
+        if (response == null)
+            throw new IAException("INVALID", "/pipeline/ returned null", false, false);
+        if (!response.optBoolean("success", false))
+            throw new IAException("SUCCESS_FALSE",
+                "/pipeline/ returned success=false: " + response, false, false);
+        JSONArray results = response.optJSONArray("results");
+        if (results == null)
+            throw new IAException("INVALID",
+                "/pipeline/ response missing 'results' array: " + response, false, false);
+        // Zero detections is a valid response. Each present result must be
+        // structurally complete; we reject the whole response on any partial
+        // result rather than persist a subset.
+        for (int i = 0; i < results.length(); i++) {
+            JSONObject r = results.optJSONObject(i);
+            if (r == null)
+                throw new IAException("INVALID",
+                    "/pipeline/ results[" + i + "] is not an object", false, false);
+            validateBbox(r.optJSONArray("bbox"), i);
+            // theta must be present AND finite. Default-on-missing (e.g.
+            // optDouble("theta", 0.0)) would accept a malformed result and
+            // persist a fabricated orientation. Require presence.
+            if (!r.has("theta"))
+                throw new IAException("INVALID",
+                    "/pipeline/ results[" + i + "] missing theta", false, false);
+            double theta = r.optDouble("theta", Double.NaN);
+            if (!isFiniteDouble(theta))
+                throw new IAException("INVALID",
+                    "/pipeline/ results[" + i + "] theta non-finite", false, false);
+            validateEmbeddingField(r, "embedding", expectedDim, "results[" + i + "]");
+        }
+    }
+
+    static void validateExtractResponse(JSONObject response, int expectedDim)
+    throws IAException {
+        if (response == null)
+            throw new IAException("INVALID", "/extract/ returned null", false, false);
+        if (!response.optBoolean("success", false))
+            throw new IAException("SUCCESS_FALSE",
+                "/extract/ returned success=false: " + response, false, false);
+        validateEmbeddingField(response, "embedding", expectedDim, "response");
+    }
+
+    private static void validateBbox(JSONArray bbox, int idx)
+    throws IAException {
+        if (bbox == null || bbox.length() != 4)
+            throw new IAException("INVALID",
+                "/pipeline/ results[" + idx + "] bbox must be a 4-element array", false, false);
+        for (int j = 0; j < 4; j++) {
+            double v = bbox.optDouble(j, Double.NaN);
+            if (!isFiniteDouble(v))
+                throw new IAException("INVALID",
+                    "/pipeline/ results[" + idx + "] bbox[" + j + "] non-finite", false, false);
+        }
+        if (bbox.optDouble(2) < 1.0 || bbox.optDouble(3) < 1.0)
+            throw new IAException("INVALID",
+                "/pipeline/ results[" + idx + "] bbox width/height must be >= 1", false, false);
+    }
+
+    private static void validateEmbeddingField(JSONObject parent, String fieldName,
+        int expectedDim, String context)
+    throws IAException {
+        JSONArray emb = parent.optJSONArray(fieldName);
+        if (emb == null)
+            throw new IAException("INVALID",
+                context + " missing '" + fieldName + "' array", false, false);
+        if (expectedDim > 0 && emb.length() != expectedDim)
+            throw new IAException("INVALID",
+                context + " embedding length " + emb.length() + " != expected " + expectedDim,
+                false, false);
+        if (emb.length() == 0)
+            throw new IAException("INVALID",
+                context + " embedding array is empty", false, false);
+        for (int j = 0; j < emb.length(); j++) {
+            double v = emb.optDouble(j, Double.NaN);
+            if (!isFiniteDouble(v))
+                throw new IAException("INVALID",
+                    context + " embedding[" + j + "] non-finite", false, false);
+        }
+        String modelId = parent.optString("embedding_model_id", null);
+        String modelVer = parent.optString("embedding_model_version", null);
+        if (!Util.stringExists(modelId) || !Util.stringExists(modelVer))
+            throw new IAException("INVALID",
+                context + " missing embedding_model_id or embedding_model_version",
+                false, false);
+    }
+
+    private static boolean isFiniteDouble(double v) {
+        return !Double.isNaN(v) && !Double.isInfinite(v);
+    }
+
+    private JSONObject postWithClassification(String url, JSONObject payload)
+    throws IAException {
+        URL u;
+        try {
+            u = new URL(url);
+        } catch (MalformedURLException ex) {
+            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
+        }
+        try {
+            return RestClient.postJSON(u, payload, null, connectTimeoutMs, readTimeoutMs);
+        } catch (Exception ex) {
+            throw classifyHttpFailure(ex, url);
+        }
+    }
+
+    /** Classify a RestClient throw into the v2 failure-ladder buckets. */
+    static IAException classifyHttpFailure(Exception ex, String url) {
+        // Detect timeout primarily by exception type; fall back to message
+        // sniffing for environments where the cause chain is flattened.
+        for (Throwable t = ex; t != null; t = t.getCause()) {
+            if (t instanceof SocketTimeoutException) {
+                return new IAException("TIMEOUT",
+                    "ml-service timeout on " + url + ": " + ex.getMessage(), true, false);
+            }
+        }
+        String msg = ex.getMessage() == null ? "" : ex.getMessage();
+        if (msg.contains("timed out")) {
+            return new IAException("TIMEOUT",
+                "ml-service timeout on " + url + ": " + msg, true, false);
+        }
+        // Connection refused and Connection reset are both transient peer-side
+        // conditions; retry with increment so the back-off counter advances.
+        if (msg.contains("Connection refused") || msg.contains("Connection reset")) {
+            return new IAException("NETWORK",
+                "ml-service connection error on " + url + ": " + msg, true, true);
+        }
+        // Parse failures from RestClient.postJSON: the response was a 200 OK
+        // but the body wasn't valid JSON. That's a contract violation by
+        // ml-service, not a network issue. Classify as INVALID, non-retryable.
+        if (msg.contains("could not convert postRaw()")) {
+            return new IAException("INVALID",
+                "ml-service returned non-JSON 200 on " + url + ": " + msg, false, false);
+        }
+        Matcher m = HTTP_CODE_PATTERN.matcher(msg);
+        if (m.find()) {
+            int statusCode;
+            try {
+                statusCode = Integer.parseInt(m.group(1));
+            } catch (NumberFormatException nfe) {
+                statusCode = 0;
+            }
+            // 408 (Request Timeout) — typically emitted by a proxy/LB in front
+            // of ml-service; treat like a normal timeout (retry, no increment).
+            if (statusCode == 408) {
+                return new IAException("TIMEOUT",
+                    "ml-service 408 on " + url, true, false);
+            }
+            if (statusCode == 429) {
+                return new IAException("RATE_LIMITED",
+                    "ml-service rate-limited (429) on " + url, true, true);
+            }
+            if (statusCode == 502 || statusCode == 503 || statusCode == 504) {
+                return new IAException("NETWORK",
+                    "ml-service " + statusCode + " on " + url, true, true);
+            }
+            if (statusCode >= 500 && statusCode < 600) {
+                return new IAException("SERVER_ERROR",
+                    "ml-service " + statusCode + " on " + url, true, true);
+            }
+            if (statusCode >= 400 && statusCode < 500) {
+                return new IAException("CLIENT_ERROR",
+                    "ml-service " + statusCode + " on " + url + " (non-retryable)",
+                    false, false);
+            }
+        }
+        // Unrecognized; treat as non-retryable to avoid spinning.
+        return new IAException("NETWORK",
+            "ml-service request failed on " + url + ": " + msg, false, false);
+    }
+}
diff --git a/src/main/java/org/ecocean/ia/MlServiceJobOutcome.java b/src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
index 50e8bd778..2194cab81 100644
--- a/src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
+++ b/src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
@@ -1,134 +1,134 @@
-package org.ecocean.ia;
-
-import java.util.ArrayList;
-import java.util.Collections;
-import java.util.List;
-
-/**
- * Typed outcome of a single ml-service queue job, returned by
- * {@code MlServiceProcessor.process(...)}. The seven {@link Kind} values
- * distinguish operationally-different terminal states so the caller can
- * record clear status/statusDetails on the parent Task and react
- * appropriately (e.g. enqueue a deferred match when {@code OK}).
- *
- * <p>Migration plan v2 §commit #8.</p>
- */
-public final class MlServiceJobOutcome {
-
-    public enum Kind {
-        /** Job persisted at least one annotation/embedding; matching can proceed. */
-        OK,
-        /**
-         * Job completed but ml-service returned zero detections. Not an error
-         * — the asset is genuinely empty / has no detectable subject — but no
-         * downstream match work is needed.
-         */
-        OK_ZERO_DETECTIONS,
-        /**
-         * The target (Encounter / MediaAsset) was deleted before/during the
-         * job. Terminal-drop with no error; the inactivity-timeout watchdog
-         * must not flip it to "error".
-         */
-        STALE,
-        /**
-         * ml-service returned a response that failed structural validation
-         * (malformed embedding length, non-finite floats, missing fields).
-         * Non-retryable; mark task error.
-         */
-        ERROR_VALIDATION,
-        /**
-         * Network failure that exceeded retry budget or was non-retryable
-         * from the start (4xx). Mark task error.
-         */
-        ERROR_NETWORK,
-        /**
-         * Database write failed at the persistence step (e.g. FK violation,
-         * idempotency-index conflict that wasn't a no-op).
-         */
-        ERROR_PERSIST,
-        /**
-         * Transient network error; the queue framework has been told to
-         * requeue this job. Caller should not finalize the task — the next
-         * worker pass will pick it up.
-         */
-        REQUEUE
-    }
-
-    private final Kind kind;
-    private final String code;
-    private final String message;
-    private final List<String> persistedAnnotationIds;
-
-    private MlServiceJobOutcome(Kind kind, String code, String message,
-        List<String> persistedAnnotationIds) {
-        this.kind = kind;
-        this.code = code;
-        this.message = message;
-        // Defensive copy + unmodifiable wrap so the caller can't mutate our
-        // state after construction (Codex code-review guidance).
-        if (persistedAnnotationIds == null || persistedAnnotationIds.isEmpty()) {
-            this.persistedAnnotationIds = Collections.emptyList();
-        } else {
-            this.persistedAnnotationIds = Collections.unmodifiableList(
-                new ArrayList<String>(persistedAnnotationIds));
-        }
-    }
-
-    // --- Factories ---------------------------------------------------------
-
-    public static MlServiceJobOutcome ok(List<String> persistedAnnotationIds) {
-        return new MlServiceJobOutcome(Kind.OK, null, null, persistedAnnotationIds);
-    }
-
-    public static MlServiceJobOutcome okZeroDetections() {
-        return new MlServiceJobOutcome(Kind.OK_ZERO_DETECTIONS, null, null, null);
-    }
-
-    public static MlServiceJobOutcome stale(String reason) {
-        return new MlServiceJobOutcome(Kind.STALE, "STALE", reason, null);
-    }
-
-    public static MlServiceJobOutcome validationError(String code, String message) {
-        return new MlServiceJobOutcome(Kind.ERROR_VALIDATION,
-            code == null ? "INVALID" : code, message, null);
-    }
-
-    public static MlServiceJobOutcome networkError(String code, String message) {
-        return new MlServiceJobOutcome(Kind.ERROR_NETWORK,
-            code == null ? "NETWORK" : code, message, null);
-    }
-
-    public static MlServiceJobOutcome persistError(String code, String message) {
-        return new MlServiceJobOutcome(Kind.ERROR_PERSIST,
-            code == null ? "PERSIST" : code, message, null);
-    }
-
-    public static MlServiceJobOutcome requeue() {
-        return new MlServiceJobOutcome(Kind.REQUEUE, null, null, null);
-    }
-
-    // --- Accessors ---------------------------------------------------------
-
-    public Kind getKind() {
-        return kind;
-    }
-
-    public String getCode() {
-        return code;
-    }
-
-    public String getMessage() {
-        return message;
-    }
-
-    public List<String> getPersistedAnnotationIds() {
-        return persistedAnnotationIds;
-    }
-
-    /** True iff this outcome represents a terminal error (not OK*, STALE, or REQUEUE). */
-    public boolean isError() {
-        return kind == Kind.ERROR_VALIDATION
-            || kind == Kind.ERROR_NETWORK
-            || kind == Kind.ERROR_PERSIST;
-    }
-}
+package org.ecocean.ia;
+
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.List;
+
+/**
+ * Typed outcome of a single ml-service queue job, returned by
+ * {@code MlServiceProcessor.process(...)}. The seven {@link Kind} values
+ * distinguish operationally-different terminal states so the caller can
+ * record clear status/statusDetails on the parent Task and react
+ * appropriately (e.g. enqueue a deferred match when {@code OK}).
+ *
+ * <p>Migration plan v2 §commit #8.</p>
+ */
+public final class MlServiceJobOutcome {
+
+    public enum Kind {
+        /** Job persisted at least one annotation/embedding; matching can proceed. */
+        OK,
+        /**
+         * Job completed but ml-service returned zero detections. Not an error
+         * — the asset is genuinely empty / has no detectable subject — but no
+         * downstream match work is needed.
+         */
+        OK_ZERO_DETECTIONS,
+        /**
+         * The target (Encounter / MediaAsset) was deleted before/during the
+         * job. Terminal-drop with no error; the inactivity-timeout watchdog
+         * must not flip it to "error".
+         */
+        STALE,
+        /**
+         * ml-service returned a response that failed structural validation
+         * (malformed embedding length, non-finite floats, missing fields).
+         * Non-retryable; mark task error.
+         */
+        ERROR_VALIDATION,
+        /**
+         * Network failure that exceeded retry budget or was non-retryable
+         * from the start (4xx). Mark task error.
+         */
+        ERROR_NETWORK,
+        /**
+         * Database write failed at the persistence step (e.g. FK violation,
+         * idempotency-index conflict that wasn't a no-op).
+         */
+        ERROR_PERSIST,
+        /**
+         * Transient network error; the queue framework has been told to
+         * requeue this job. Caller should not finalize the task — the next
+         * worker pass will pick it up.
+         */
+        REQUEUE
+    }
+
+    private final Kind kind;
+    private final String code;
+    private final String message;
+    private final List<String> persistedAnnotationIds;
+
+    private MlServiceJobOutcome(Kind kind, String code, String message,
+        List<String> persistedAnnotationIds) {
+        this.kind = kind;
+        this.code = code;
+        this.message = message;
+        // Defensive copy + unmodifiable wrap so the caller can't mutate our
+        // state after construction (Codex code-review guidance).
+        if (persistedAnnotationIds == null || persistedAnnotationIds.isEmpty()) {
+            this.persistedAnnotationIds = Collections.emptyList();
+        } else {
+            this.persistedAnnotationIds = Collections.unmodifiableList(
+                new ArrayList<String>(persistedAnnotationIds));
+        }
+    }
+
+    // --- Factories ---------------------------------------------------------
+
+    public static MlServiceJobOutcome ok(List<String> persistedAnnotationIds) {
+        return new MlServiceJobOutcome(Kind.OK, null, null, persistedAnnotationIds);
+    }
+
+    public static MlServiceJobOutcome okZeroDetections() {
+        return new MlServiceJobOutcome(Kind.OK_ZERO_DETECTIONS, null, null, null);
+    }
+
+    public static MlServiceJobOutcome stale(String reason) {
+        return new MlServiceJobOutcome(Kind.STALE, "STALE", reason, null);
+    }
+
+    public static MlServiceJobOutcome validationError(String code, String message) {
+        return new MlServiceJobOutcome(Kind.ERROR_VALIDATION,
+            code == null ? "INVALID" : code, message, null);
+    }
+
+    public static MlServiceJobOutcome networkError(String code, String message) {
+        return new MlServiceJobOutcome(Kind.ERROR_NETWORK,
+            code == null ? "NETWORK" : code, message, null);
+    }
+
+    public static MlServiceJobOutcome persistError(String code, String message) {
+        return new MlServiceJobOutcome(Kind.ERROR_PERSIST,
+            code == null ? "PERSIST" : code, message, null);
+    }
+
+    public static MlServiceJobOutcome requeue() {
+        return new MlServiceJobOutcome(Kind.REQUEUE, null, null, null);
+    }
+
+    // --- Accessors ---------------------------------------------------------
+
+    public Kind getKind() {
+        return kind;
+    }
+
+    public String getCode() {
+        return code;
+    }
+
+    public String getMessage() {
+        return message;
+    }
+
+    public List<String> getPersistedAnnotationIds() {
+        return persistedAnnotationIds;
+    }
+
+    /** True iff this outcome represents a terminal error (not OK*, STALE, or REQUEUE). */
+    public boolean isError() {
+        return kind == Kind.ERROR_VALIDATION
+            || kind == Kind.ERROR_NETWORK
+            || kind == Kind.ERROR_PERSIST;
+    }
+}
diff --git a/src/main/java/org/ecocean/ia/MlServiceProcessor.java b/src/main/java/org/ecocean/ia/MlServiceProcessor.java
index 04b14d625..45c639df8 100644
--- a/src/main/java/org/ecocean/ia/MlServiceProcessor.java
+++ b/src/main/java/org/ecocean/ia/MlServiceProcessor.java
@@ -37,14 +37,34 @@ public class MlServiceProcessor {
 
     private final String context;
     private final MlServiceClient client;
+    private final MatchVisibilityGate visibilityGate;
+    private final DeferredMatchPublisher deferredPublisher;
 
     public MlServiceProcessor(String context) {
-        this(context, new MlServiceClient());
+        this(context, new MlServiceClient(),
+            new MatchVisibilityGateImpl(context),
+            new IAGatewayDeferredMatchPublisher());
     }
 
     public MlServiceProcessor(String context, MlServiceClient client) {
+        this(context, client, new MatchVisibilityGateImpl(context),
+            new IAGatewayDeferredMatchPublisher());
+    }
+
+    /**
+     * Test-friendly constructor that accepts injected
+     * {@link MatchVisibilityGate} and {@link DeferredMatchPublisher}.
+     * Production code should use the no-arg or single-arg constructor
+     * above. (Empty-match-prospects design Track 2 C11 testability
+     * seam — Codex round-4 Medium.)
+     */
+    MlServiceProcessor(String context, MlServiceClient client,
+        MatchVisibilityGate visibilityGate,
+        DeferredMatchPublisher deferredPublisher) {
         this.context = context;
         this.client = client;
+        this.visibilityGate = visibilityGate;
+        this.deferredPublisher = deferredPublisher;
     }
 
     /** Process one ml-service queue job. Returns the outcome. */
@@ -417,17 +437,41 @@ public class MlServiceProcessor {
 
     private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
         JSONObject matchConfig) {
-        try {
-            OpenSearch os = new OpenSearch();
-            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
-                enqueueDeferredMatch(annotationIds, taskId);
-                return MlServiceJobOutcome.ok(annotationIds);
-            }
-        } catch (IOException ex) {
-            enqueueDeferredMatch(annotationIds, taskId);
+        // Initial invocation: attempt=1, firstDeferredAt=null (the
+        // gate stamps `now` so age-out is measured from this first
+        // call, not from later re-fires).
+        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
+    }
+
+    /**
+     * Shared body for the initial {@link #waitAndRunMatch} call and
+     * the re-gated {@link #runDeferredMatch} path. Drives the
+     * {@link MatchVisibilityGate}: READY → run match; DEFER → publish
+     * a deferred-match job through the publisher; GIVE_UP → log WARN
+     * and run match against whatever is visible (partial results are
+     * better than silently no match task; Codex round-2 #2).
+     *
+     * <p>(Empty-match-prospects design Track 2 C11.)</p>
+     */
+    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
+        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
+        MatchVisibilityGate.GateOutcome gate = visibilityGate.gateForBatch(
+            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
+        switch (gate.kind) {
+          case READY:
+            return runMatchProspects(annotationIds, taskId, matchConfig);
+          case DEFER:
+            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
             return MlServiceJobOutcome.ok(annotationIds);
+          case GIVE_UP:
+          default:
+            System.out.println(
+                "WARN: MatchVisibilityGate aged out for task " + taskId +
+                " after attempt=" + gate.attempt + " elapsed=" +
+                gate.elapsedMillis + "ms reason=" + gate.reason +
+                "; running match against current visible corpus");
+            return runMatchProspects(annotationIds, taskId, matchConfig);
         }
-        return runMatchProspects(annotationIds, taskId, matchConfig);
     }
 
     public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
@@ -438,7 +482,17 @@ public class MlServiceProcessor {
         String taskId = jobData.optString("taskId", null);
         JSONObject matchConfig = jobData.optJSONObject("matchConfig");
         if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
-        return runMatchProspects(annotationIds, taskId, matchConfig);
+        // Carry forward attempt + firstDeferredAt so age-out is
+        // measured by elapsed wall-clock from the original DEFER, not
+        // by attempt count (Codex round-4 OQ #1).
+        int attempt = jobData.optInt("attempt", 2);
+        Long firstDeferredAt = jobData.has("firstDeferredAt")
+            ? Long.valueOf(jobData.optLong("firstDeferredAt")) : null;
+        // Re-gate; deferred match earns the same protection as the
+        // initial call (Codex round-2 Major: don't degrade back to
+        // today's bug on the first deferral).
+        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
+            attempt, firstDeferredAt);
     }
 
     public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
@@ -678,20 +732,53 @@ public class MlServiceProcessor {
         task.setCompletionDateInMilliseconds();
     }
 
-    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
+    /**
+     * Build and publish a deferred-match payload via the injected
+     * {@link DeferredMatchPublisher}. The real publisher wraps
+     * {@link IAGateway#requeueJob} with {@code increment=true} so the
+     * 30s fixed delay applies (Codex round-4 Blocker: setting
+     * {@code __queueRetries} alone does not create the delay).
+     *
+     * <p>Routing flags: {@code mlServiceV2: true} (IAGateway v2
+     * dispatch) AND {@code deferredMatch: true} (MlServiceProcessor
+     * deferred branch). Both required — Codex round-5 Blocker
+     * documented the dispatch contract.</p>
+     *
+     * <p>Gate metadata on the payload: {@code attempt} (incremented
+     * per DEFER), {@code firstDeferredAt} (epoch-ms of the first
+     * DEFER, preserved across re-fires for elapsed-time age-out),
+     * {@code lastGateReason} (Codex round-2 #6 diagnostic).</p>
+     */
+    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId,
+        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate) {
         JSONObject payload = new JSONObject();
+        // Routing flags — both required for the dispatcher to land
+        // the requeue back on MlServiceProcessor's deferred entry
+        // point (Codex round-5 Blocker).
         payload.put("mlServiceV2", true);
         payload.put("deferredMatch", true);
+        // Diagnostic marker — not the routing contract.
+        payload.put("mlServiceV2DeferredMatch", true);
         payload.put("annotationIds", new JSONArray(annotationIds));
         if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
+        if (matchConfig != null) payload.put("matchConfig", matchConfig);
         // Carry __context in the payload so the dispatcher's
-        // jobj.optString("__context", "context0") fallback at IAGateway.java
-        // doesn't silently route the deferred-match into context0 when this
-        // processor is running in a non-default context.
+        // jobj.optString("__context", "context0") fallback at
+        // IAGateway.java doesn't silently route the deferred-match
+        // into context0 when this processor is running in a non-default
+        // context.
         payload.put("__context", context);
+        // Gate metadata — incremented for next attempt; firstDeferredAt
+        // preserved across re-fires (Codex round-4 OQ #1).
+        payload.put("attempt", gate.attempt + 1);
+        payload.put("firstDeferredAt", gate.firstDeferredAt);
+        if (gate.reason != null) payload.put("lastGateReason", gate.reason);
         try {
-            IAGateway.addToDetectionQueue(context, payload.toString());
-        } catch (IOException ex) {
+            deferredPublisher.publish(payload);
+        } catch (Exception ex) {
+            // requeueJob doesn't throw declared exceptions, but a future
+            // publisher impl might. Don't let publish-failure leak past
+            // the orchestrator.
             System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
         }
     }


## New files:


### IAGatewayDeferredMatchPublisher.java

```java
package org.ecocean.ia;

import org.ecocean.servlet.IAGateway;
import org.json.JSONObject;

/**
 * Production {@link DeferredMatchPublisher} that re-queues
 * deferred-match payloads through
 * {@link IAGateway#requeueJob(JSONObject, boolean)} with
 * {@code increment=true} so the 30s fixed delay applies
 * (IAGateway.java:785). Calling {@code addToDetectionQueue}
 * directly would publish immediately and hot-loop.
 *
 * <p>(Empty-match-prospects design Track 2 C11 — Codex round-4
 * Blocker: the deferred enqueue must explicitly use
 * {@code requeueJob(payload, true)}, not just stamp
 * {@code __queueRetries} into the JSON.)</p>
 */
public final class IAGatewayDeferredMatchPublisher implements DeferredMatchPublisher {
    @Override
    public void publish(JSONObject payload) {
        IAGateway.requeueJob(payload, true);
    }
}
```

### MlServiceProcessorGateTest.java

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the gate/publisher wiring in
 * {@link MlServiceProcessor}'s deferred-match path. Uses the
 * package-visible test constructor to inject a
 * {@link MatchVisibilityGate} stub and a recording
 * {@link DeferredMatchPublisher}. Sidesteps Shepherd/OpenSearch:
 * a DEFER outcome short-circuits before runMatchProspects, so the
 * tests assert on the published payload without needing a DB.
 *
 * <p>(Empty-match-prospects design Track 2 C11.)</p>
 */
class MlServiceProcessorGateTest {

    /** Records the payload(s) published by the processor under test. */
    private static final class RecordingPublisher implements DeferredMatchPublisher {
        final List<JSONObject> published = new ArrayList<JSONObject>();
        @Override public void publish(JSONObject payload) {
            published.add(payload);
        }
    }

    /** Always returns a fixed gate outcome. */
    private static final class StubGate implements MatchVisibilityGate {
        final GateOutcome fixed;
        StubGate(GateOutcome fixed) { this.fixed = fixed; }
        @Override public GateOutcome gateForBatch(
            Collection<String> callerAnnotationIds, String childTaskId,
            JSONObject matchConfig, int attempt, Long firstDeferredAt) {
            return fixed;
        }
    }

    private static MlServiceProcessor processorWith(MatchVisibilityGate gate,
        DeferredMatchPublisher publisher) {
        return new MlServiceProcessor("context0", new MlServiceClient(),
            gate, publisher);
    }

    private static JSONObject deferredJobPayload(int attempt,
        Long firstDeferredAt) {
        JSONObject jo = new JSONObject();
        jo.put("mlServiceV2", true);
        jo.put("deferredMatch", true);
        jo.put("annotationIds", new JSONArray().put("ann-1").put("ann-2"));
        jo.put("taskId", "task-1");
        jo.put("matchConfig", new JSONObject()
            .put("method", "miewid-msv4.1").put("version", "4.1"));
        jo.put("attempt", attempt);
        if (firstDeferredAt != null) {
            jo.put("firstDeferredAt", firstDeferredAt.longValue());
        }
        return jo;
    }

    // --- DEFER path: publisher receives a payload -----------------------

    @Test void runDeferredMatch_publishesPayload_onGateDefer() {
        long firstDeferred = System.currentTimeMillis();
        MatchVisibilityGate.GateOutcome defer =
            MatchVisibilityGate.GateOutcome.defer(2, firstDeferred,
                "sibling MA 42 non-terminal");
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(new StubGate(defer), publisher);
        MlServiceJobOutcome out = p.runDeferredMatch(
            deferredJobPayload(2, firstDeferred));
        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertEquals(1, publisher.published.size(),
            "expected exactly one re-published payload");
    }

    @Test void publishedPayloadCarriesBothRoutingFlags() {
        // Codex round-5 Blocker: IAGateway dispatches v2 jobs only when
        // mlServiceV2==true; MlServiceProcessor branches deferred only
        // when deferredMatch==true. Both required.
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "non-terminal")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        JSONObject payload = publisher.published.get(0);
        assertTrue(payload.optBoolean("mlServiceV2", false),
            "missing mlServiceV2: " + payload);
        assertTrue(payload.optBoolean("deferredMatch", false),
            "missing deferredMatch: " + payload);
    }

    @Test void publishedPayloadIncrementsAttempt() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                3, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(3, firstDeferred));
        JSONObject payload = publisher.published.get(0);
        assertEquals(4, payload.optInt("attempt", -1));
    }

    @Test void publishedPayloadPreservesFirstDeferredAt() {
        // Age-out is measured by elapsed wall-clock from the original
        // DEFER, so firstDeferredAt must be carried forward unchanged
        // across re-fires (Codex round-4 OQ #1).
        long firstDeferred = 1700000000000L;
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        assertEquals(firstDeferred,
            publisher.published.get(0).optLong("firstDeferredAt"));
    }

    @Test void publishedPayloadCarriesLastGateReason() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "sibling MA 42 processing-mlservice")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        assertEquals("sibling MA 42 processing-mlservice",
            publisher.published.get(0).optString("lastGateReason"));
    }

    @Test void publishedPayloadCarriesAnnotationIdsAndTaskId() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        JSONObject payload = publisher.published.get(0);
        JSONArray ids = payload.optJSONArray("annotationIds");
        assertNotNull(ids);
        assertEquals(2, ids.length());
        assertEquals("ann-1", ids.optString(0));
        assertEquals("ann-2", ids.optString(1));
        assertEquals("task-1", payload.optString("taskId"));
    }

    @Test void publishedPayloadCarriesContext() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        assertEquals("context0",
            publisher.published.get(0).optString("__context"));
    }

    // --- runDeferredMatch input validation ------------------------------

    @Test void runDeferredMatch_returnsValidationError_onNullPayload() {
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.ready(
                1, System.currentTimeMillis())),
            publisher);
        MlServiceJobOutcome out = p.runDeferredMatch(null);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals(0, publisher.published.size(),
            "publisher should not fire on validation error");
    }
}
```


## Validation

- MlServiceProcessorGateTest: 8/8 (new) — uses injected gate +
  publisher to cover the DEFER path's payload contract end-to-end:
  both routing flags present, attempt incremented, firstDeferredAt
  preserved, lastGateReason written, annotationIds + taskId carried,
  __context carried, null-payload validation error.
- MlServiceProcessorTest: 12/12 (existing) — confirms no regression.
- Full project test suite: 377 tests, 0 failures, 7 pre-existing
  skips.
- LF-normalized on all touched files.

## Review scope

- Constructor chain: no-arg / single-arg (legacy) constructors
  delegate to the test-friendly 4-arg constructor with real impls.
  Existing tests still work via the public 1- and 2-arg paths.
- Gate switch: READY/DEFER/GIVE_UP all reachable; default branch
  matches GIVE_UP behavior for forward-compat.
- runDeferredMatch payload propagation: attempt defaults to 2 on
  first defer (since the initial call was attempt=1). Should the
  initial-call default be 2 or 1? My read: when a deferred-match
  job lands here, it was published by enqueueDeferredMatch which
  stamped attempt+1; an absent attempt key means an externally-
  crafted payload, so default-2 is conservative.
- enqueueDeferredMatch routing flags: mlServiceV2 + deferredMatch
  + mlServiceV2DeferredMatch (diagnostic marker only). Codex
  round-5 Blocker confirmed.
- IAGatewayDeferredMatchPublisher: trivial wrapper, no defensive
  null handling on payload (caller responsibility).
- Anything else (especially: are there other call sites of the
  old enqueueDeferredMatch signature I missed? Was the
  Phase-1/Phase-3 wiring inside waitAndRunMatch fully replaced?).

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll review this as a code review against the live branch, using the provided design constraints and checking the surrounding dispatcher/gate code for contract mismatches. I’ll avoid file writes and stick to read-only inspection.
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/ia/IAException.java src/main/java/org/ecocean/ia/MLService.java src/main/java/org/ecocean/ia/MatchResult.java src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/java/org/ecocean/ia/MlServiceClient.java src/main/java/org/ecocean/ia/MlServiceJobOutcome.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short && git rev-parse --abbrev-ref HEAD' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "class MlServiceProcessor|waitAndRunMatch|runDeferredMatch|enqueueDeferredMatch|class MatchVisibilityGate|interface MatchVisibilityGate|interface DeferredMatchPublisher|class IAGatewayDeferredMatchPublisher|mlServiceV2|deferredMatch|requeueJob" src/main/java src/test/java' in /mnt/c/Wildbook-clean2
 succeeded in 906ms:
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/IA.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/IAException.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/MLService.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/MatchResult.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/MatchResultProspect.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/MlServiceClient.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MlServiceProcessor.java

 succeeded in 957ms:
src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java:9: * {@link IAGateway#requeueJob(JSONObject, boolean)} with
src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java:16: * {@code requeueJob(payload, true)}, not just stamp
src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java:19:public final class IAGatewayDeferredMatchPublisher implements DeferredMatchPublisher {
src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java:22:        IAGateway.requeueJob(payload, true);
src/main/java/org/ecocean/ia/IA.java:228:     * topTask; each emits a {@code mlServiceV2:true} payload to the
src/main/java/org/ecocean/ia/IA.java:303:        qjob.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/MLService.java:164:                requeueJob(jobData, iaex.shouldIncrement());
src/main/java/org/ecocean/ia/MLService.java:215:    public void requeueJob(JSONObject jobData, boolean increment) {
src/main/java/org/ecocean/ia/MLService.java:216:        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
src/main/java/org/ecocean/ia/MLService.java:219:        IAGateway.requeueJob(jobData, increment);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:30: * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
src/main/java/org/ecocean/ia/MlServiceProcessor.java:33:public class MlServiceProcessor {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:75:        if (jobData.optBoolean("deferredMatch", false)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:76:            return runDeferredMatch(jobData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:112:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:129:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:153:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:164:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:438:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:443:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:447:     * Shared body for the initial {@link #waitAndRunMatch} call and
src/main/java/org/ecocean/ia/MlServiceProcessor.java:448:     * the re-gated {@link #runDeferredMatch} path. Drives the
src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:464:            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:477:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:494:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:524:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:738:     * {@link IAGateway#requeueJob} with {@code increment=true} so the
src/main/java/org/ecocean/ia/MlServiceProcessor.java:742:     * <p>Routing flags: {@code mlServiceV2: true} (IAGateway v2
src/main/java/org/ecocean/ia/MlServiceProcessor.java:743:     * dispatch) AND {@code deferredMatch: true} (MlServiceProcessor
src/main/java/org/ecocean/ia/MlServiceProcessor.java:752:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:758:        payload.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:759:        payload.put("deferredMatch", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:761:        payload.put("mlServiceV2DeferredMatch", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:779:            // requeueJob doesn't throw declared exceptions, but a future
src/main/java/org/ecocean/ia/MlServiceProcessor.java:782:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
src/main/java/org/ecocean/ia/DeferredMatchPublisher.java:9: * {@link org.ecocean.servlet.IAGateway#requeueJob} static call. The
src/main/java/org/ecocean/ia/DeferredMatchPublisher.java:10: * real implementation wraps {@code requeueJob(payload, true)},
src/main/java/org/ecocean/ia/DeferredMatchPublisher.java:18:public interface DeferredMatchPublisher {
src/main/java/org/ecocean/ia/DeferredMatchPublisher.java:21:     * for stamping routing flags (e.g. {@code mlServiceV2: true},
src/main/java/org/ecocean/ia/DeferredMatchPublisher.java:22:     * {@code deferredMatch: true}) and gate metadata (
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:45:public interface MatchVisibilityGate {
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:51:     * {@code IAGateway.requeueJob} 30-retry/2-day cap with comfortable
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:26:public final class MatchVisibilityGateImpl implements MatchVisibilityGate {
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:28:class MlServiceProcessorGateTest {
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:58:        jo.put("mlServiceV2", true);
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:59:        jo.put("deferredMatch", true);
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:73:    @Test void runDeferredMatch_publishesPayload_onGateDefer() {
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:80:        MlServiceJobOutcome out = p.runDeferredMatch(
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:89:        // mlServiceV2==true; MlServiceProcessor branches deferred only
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:90:        // when deferredMatch==true. Both required.
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:97:        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:99:        assertTrue(payload.optBoolean("mlServiceV2", false),
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:100:            "missing mlServiceV2: " + payload);
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:101:        assertTrue(payload.optBoolean("deferredMatch", false),
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:102:            "missing deferredMatch: " + payload);
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:112:        p.runDeferredMatch(deferredJobPayload(3, firstDeferred));
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:127:        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:139:        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:151:        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:168:        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:173:    // --- runDeferredMatch input validation ------------------------------
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:175:    @Test void runDeferredMatch_returnsValidationError_onNullPayload() {
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:181:        MlServiceJobOutcome out = p.runDeferredMatch(null);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:25:class MatchVisibilityGateImplTest {
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:234:        // IAGateway.requeueJob 30-retry / 30s-per-attempt window
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:30:class MlServiceProcessorTest {
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:44:            .put("mlServiceV2", true)
src/main/java/org/ecocean/servlet/IAGateway.java:385:                requeueJob(jobj, true);
src/main/java/org/ecocean/servlet/IAGateway.java:637:        if (jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:642:            System.out.println("IAGateway: mlServiceV2 job " +
src/main/java/org/ecocean/servlet/IAGateway.java:748:        if (requeue) requeueJob(jobj, requeueIncrement);
src/main/java/org/ecocean/servlet/IAGateway.java:751:    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
src/main/java/org/ecocean/servlet/IAGateway.java:764:            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
src/main/java/org/ecocean/servlet/IAGateway.java:768:        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
src/main/java/org/ecocean/servlet/IAGateway.java:786:                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:792:                            jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:793:                            // mlServiceV2 retries must land on the detection
src/main/java/org/ecocean/servlet/IAGateway.java:805:                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +

codex
I’ve confirmed the diff is present locally and started with the dispatcher and gate contracts. One immediate thing stands out: several touched legacy files are currently `i/lf w/crlf`, matching the Windows-mounted repo gotcha, so I’ll include that if it remains relevant to this commit.
 succeeded in 10886ms:
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
?? src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java
?? src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java
migrate-ml-service-v2

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '400,810p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchVisibilityGate.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java | sed -n '1,280p'" in /mnt/c/Wildbook-clean2
 succeeded in 225ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.IOException;
     4	import java.net.URL;
     5	import java.util.ArrayList;
     6	import java.util.List;
     7	import java.util.Locale;
     8	
     9	import org.ecocean.Annotation;
    10	import org.ecocean.Embedding;
    11	import org.ecocean.Encounter;
    12	import org.ecocean.IAJsonProperties;
    13	import org.ecocean.OpenSearch;
    14	import org.ecocean.Taxonomy;
    15	import org.ecocean.Util;
    16	import org.ecocean.identity.IBEISIA;
    17	import org.ecocean.media.Feature;
    18	import org.ecocean.media.FeatureType;
    19	import org.ecocean.media.MediaAsset;
    20	import org.ecocean.servlet.IAGateway;
    21	import org.ecocean.shepherd.core.Shepherd;
    22	import org.json.JSONArray;
    23	import org.json.JSONObject;
    24	
    25	/**
    26	 * Single-job orchestrator for ml-service v2 queue payloads.
    27	 *
    28	 * <p>Owns Shepherd transaction boundaries: load/revalidate, release DB while
    29	 * the network call runs, then reopen for idempotent persistence and matching.
    30	 * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
    31	 * a later commit.</p>
    32	 */
    33	public class MlServiceProcessor {
    34	    private static final String ACTION_PREFIX = "MlServiceProcessor.";
    35	    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";
    36	    private static final long VISIBILITY_TIMEOUT_MS = 5000L;
    37	
    38	    private final String context;
    39	    private final MlServiceClient client;
    40	    private final MatchVisibilityGate visibilityGate;
    41	    private final DeferredMatchPublisher deferredPublisher;
    42	
    43	    public MlServiceProcessor(String context) {
    44	        this(context, new MlServiceClient(),
    45	            new MatchVisibilityGateImpl(context),
    46	            new IAGatewayDeferredMatchPublisher());
    47	    }
    48	
    49	    public MlServiceProcessor(String context, MlServiceClient client) {
    50	        this(context, client, new MatchVisibilityGateImpl(context),
    51	            new IAGatewayDeferredMatchPublisher());
    52	    }
    53	
    54	    /**
    55	     * Test-friendly constructor that accepts injected
    56	     * {@link MatchVisibilityGate} and {@link DeferredMatchPublisher}.
    57	     * Production code should use the no-arg or single-arg constructor
    58	     * above. (Empty-match-prospects design Track 2 C11 testability
    59	     * seam — Codex round-4 Medium.)
    60	     */
    61	    MlServiceProcessor(String context, MlServiceClient client,
    62	        MatchVisibilityGate visibilityGate,
    63	        DeferredMatchPublisher deferredPublisher) {
    64	        this.context = context;
    65	        this.client = client;
    66	        this.visibilityGate = visibilityGate;
    67	        this.deferredPublisher = deferredPublisher;
    68	    }
    69	
    70	    /** Process one ml-service queue job. Returns the outcome. */
    71	    public MlServiceJobOutcome process(JSONObject jobData) {
    72	        if (jobData == null) {
    73	            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
    74	        }
    75	        if (jobData.optBoolean("deferredMatch", false)) {
    76	            return runDeferredMatch(jobData);
    77	        }
    78	
    79	        String taxonomyString = jobData.optString("taxonomyString", null);
    80	        String taskId = jobData.optString("taskId", null);
    81	        String encounterId = jobData.optString("encounterId", null);
    82	
    83	        if (jobData.has("mediaAssetId")) {
    84	            String maId = String.valueOf(jobData.opt("mediaAssetId"));
    85	            return processDetection(jobData, taxonomyString, taskId, encounterId, maId);
    86	        }
    87	        if (jobData.has("annotationId")) {
    88	            String annId = jobData.optString("annotationId", null);
    89	            return processExtraction(jobData, taxonomyString, taskId, annId);
    90	        }
    91	        return MlServiceJobOutcome.validationError("INVALID_PAYLOAD",
    92	            "neither mediaAssetId nor annotationId in payload");
    93	    }
    94	
    95	    private MlServiceJobOutcome processDetection(JSONObject jobData, String taxonomyString,
    96	        String taskId, String encounterId, String maId) {
    97	        DetectionContext det = null;
    98	
    99	        try {
   100	            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
   101	        } catch (Exception ex) {
   102	            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
   103	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
   104	        }
   105	        if (det.outcome != null) return det.outcome;
   106	
   107	        JSONObject response;
   108	        try {
   109	            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
   110	        } catch (IAException ex) {
   111	            if (ex.shouldRequeue()) {
   112	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
   113	                return MlServiceJobOutcome.requeue();
   114	            }
   115	            markTaskError(taskId, ex.getCode(), ex.getMessage());
   116	            return mapNonRetryableError(ex);
   117	        }
   118	
   119	        JSONArray results = response.optJSONArray("results");
   120	        if (results == null || results.length() == 0) {
   121	            return finalizeZeroDetections(maId, taskId);
   122	        }
   123	
   124	        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results);
   125	        if (persisted.outcome != null) return persisted.outcome;
   126	
   127	        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
   128	            det.mlConfig);
   129	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   130	            matchConfig);
   131	        if (matchOutcome != null) return matchOutcome;
   132	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   133	    }
   134	
   135	    private MlServiceJobOutcome processExtraction(JSONObject jobData, String taxonomyString,
   136	        String taskId, String annId) {
   137	        ExtractionContext ext = null;
   138	
   139	        try {
   140	            ext = loadExtractionContext(taxonomyString, taskId, annId);
   141	        } catch (Exception ex) {
   142	            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
   143	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
   144	        }
   145	        if (ext.outcome != null) return ext.outcome;
   146	
   147	        JSONObject response;
   148	        try {
   149	            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
   150	                ext.mlConfig);
   151	        } catch (IAException ex) {
   152	            if (ex.shouldRequeue()) {
   153	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
   154	                return MlServiceJobOutcome.requeue();
   155	            }
   156	            markTaskError(taskId, ex.getCode(), ex.getMessage());
   157	            return mapNonRetryableError(ex);
   158	        }
   159	
   160	        PersistResult persisted = persistExtraction(annId, taskId, ext, response);
   161	        if (persisted.outcome != null) return persisted.outcome;
   162	
   163	        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
   164	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   165	            matchConfig);
   166	        if (matchOutcome != null) return matchOutcome;
   167	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   168	    }
   169	
   170	    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
   171	        String encounterId, String maId) {
   172	        Shepherd shep = new Shepherd(context);
   173	        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
   174	        try {
   175	            FeatureType.initAll(shep);
   176	            shep.beginDBTransaction();
   177	            MediaAsset ma = shep.getMediaAsset(maId);
   178	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   179	            Task task = Task.load(taskId, shep);
   180	
   181	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   182	            if (staleReason != null) {
   183	                markTaskDroppedStale(shep, task, staleReason);
   184	                shep.commitDBTransaction();
   185	                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
   186	            }
   187	
   188	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
   189	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   190	            if (configs == null) {
   191	                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
   192	                markTaskCompleted(task);
   193	                shep.commitDBTransaction();
   194	                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
   195	            }
   196	
   197	            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
   198	                markTaskError(task, "INVALID",
   199	                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
   200	                shep.commitDBTransaction();
   201	                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
   202	                    "_mlservice_conf missing predict_model_id"));
   203	            }
   204	
   205	            URL webUrl = ma.webURL();
   206	            if (webUrl == null) {
   207	                markTaskError(task, "INVALID_IMAGE_URI",
   208	                    "MediaAsset " + maId + " has no webURL");
   209	                shep.commitDBTransaction();
   210	                return DetectionContext.done(MlServiceJobOutcome.validationError(
   211	                    "INVALID_IMAGE_URI", "MediaAsset " + maId + " has no webURL"));
   212	            }
   213	
   214	            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   215	            shep.commitDBTransaction();
   216	            return new DetectionContext(webUrl.toString(),
   217	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   218	                configs.matchConfig);
   219	        } finally {
   220	            shep.rollbackAndClose();

 succeeded in 226ms:
   400	        Shepherd shep = new Shepherd(context);
   401	        shep.setAction(ACTION_PREFIX + "persistExtraction");
   402	        List<String> annotationIds = new ArrayList<String>();
   403	
   404	        try {
   405	            shep.beginDBTransaction();
   406	            Annotation ann = shep.getAnnotation(annId);
   407	            Task task = Task.load(taskId, shep);
   408	            if (ann == null || ann.getMediaAsset() == null) {
   409	                markTaskDroppedStale(shep, task, "annotation missing");
   410	                shep.commitDBTransaction();
   411	                return PersistResult.done(MlServiceJobOutcome.stale("annotation missing"));
   412	            }
   413	            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
   414	            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
   415	                markTaskCompleted(task);
   416	                shep.commitDBTransaction();
   417	                annotationIds.add(ann.getId());
   418	                return PersistResult.ok(annotationIds);
   419	            }
   420	
   421	            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   422	            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
   423	                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
   424	            shep.getPM().makePersistent(emb);
   425	            markTaskCompleted(task);
   426	            annotationIds.add(ann.getId());
   427	            shep.commitDBTransaction();
   428	            return PersistResult.ok(annotationIds);
   429	        } catch (Exception ex) {
   430	            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
   431	            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
   432	                ex.getMessage()));
   433	        } finally {
   434	            shep.rollbackAndClose();
   435	        }
   436	    }
   437	
   438	    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
   439	        JSONObject matchConfig) {
   440	        // Initial invocation: attempt=1, firstDeferredAt=null (the
   441	        // gate stamps `now` so age-out is measured from this first
   442	        // call, not from later re-fires).
   443	        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
   444	    }
   445	
   446	    /**
   447	     * Shared body for the initial {@link #waitAndRunMatch} call and
   448	     * the re-gated {@link #runDeferredMatch} path. Drives the
   449	     * {@link MatchVisibilityGate}: READY → run match; DEFER → publish
   450	     * a deferred-match job through the publisher; GIVE_UP → log WARN
   451	     * and run match against whatever is visible (partial results are
   452	     * better than silently no match task; Codex round-2 #2).
   453	     *
   454	     * <p>(Empty-match-prospects design Track 2 C11.)</p>
   455	     */
   456	    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
   457	        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
   458	        MatchVisibilityGate.GateOutcome gate = visibilityGate.gateForBatch(
   459	            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
   460	        switch (gate.kind) {
   461	          case READY:
   462	            return runMatchProspects(annotationIds, taskId, matchConfig);
   463	          case DEFER:
   464	            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
   465	            return MlServiceJobOutcome.ok(annotationIds);
   466	          case GIVE_UP:
   467	          default:
   468	            System.out.println(
   469	                "WARN: MatchVisibilityGate aged out for task " + taskId +
   470	                " after attempt=" + gate.attempt + " elapsed=" +
   471	                gate.elapsedMillis + "ms reason=" + gate.reason +
   472	                "; running match against current visible corpus");
   473	            return runMatchProspects(annotationIds, taskId, matchConfig);
   474	        }
   475	    }
   476	
   477	    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
   478	        if (jobData == null) {
   479	            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
   480	        }
   481	        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
   482	        String taskId = jobData.optString("taskId", null);
   483	        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
   484	        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
   485	        // Carry forward attempt + firstDeferredAt so age-out is
   486	        // measured by elapsed wall-clock from the original DEFER, not
   487	        // by attempt count (Codex round-4 OQ #1).
   488	        int attempt = jobData.optInt("attempt", 2);
   489	        Long firstDeferredAt = jobData.has("firstDeferredAt")
   490	            ? Long.valueOf(jobData.optLong("firstDeferredAt")) : null;
   491	        // Re-gate; deferred match earns the same protection as the
   492	        // initial call (Codex round-2 Major: don't degrade back to
   493	        // today's bug on the first deferral).
   494	        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
   495	            attempt, firstDeferredAt);
   496	    }
   497	
   498	    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
   499	        JSONObject matchConfig) {
   500	        if (annotationIds == null || annotationIds.isEmpty()) {
   501	            markTaskCompleted(taskId);
   502	            return MlServiceJobOutcome.ok(new ArrayList<String>());
   503	        }
   504	
   505	        Shepherd shep = new Shepherd(context);
   506	        shep.setAction(ACTION_PREFIX + "runMatchProspects");
   507	        try {
   508	            shep.beginDBTransaction();
   509	            List<Annotation> anns = new ArrayList<Annotation>();
   510	            for (String annId : annotationIds) {
   511	                Annotation ann = shep.getAnnotation(annId);
   512	                if (ann != null) anns.add(ann);
   513	            }
   514	            if (anns.isEmpty()) {
   515	                Task task = Task.load(taskId, shep);
   516	                markTaskDroppedStale(shep, task, "annotations missing");
   517	                shep.commitDBTransaction();
   518	                return MlServiceJobOutcome.stale("annotations missing");
   519	            }
   520	
   521	            Task parent = Task.load(taskId, shep);
   522	            Task matchTask = (parent == null) ? new Task() : new Task(parent);
   523	            matchTask.setObjectAnnotations(anns);
   524	            matchTask.addParameter("mlServiceV2Match", true);
   525	            shep.getPM().makePersistent(matchTask);
   526	            // findMatchProspects returns false when the match config is not
   527	            // a vector config or matchConfig is null. Don't leave the match
   528	            // task without a terminal status — mark the parent task error.
   529	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
   530	            if (!ran) {
   531	                matchTask.setStatus("error");
   532	                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
   533	                    "findMatchProspects rejected match config: " +
   534	                    (matchConfig == null ? "null" : matchConfig.toString()));
   535	                matchTask.setCompletionDateInMilliseconds();
   536	                // Update the parent task in this same transaction (parent is
   537	                // already loaded above) so the two updates commit atomically.
   538	                // Splitting across transactions risks leaving the parent
   539	                // "completed" if the second commit fails or the JVM dies.
   540	                if (parent != null) {
   541	                    markTaskError(parent, "INVALID_MATCH_CONFIG",
   542	                        "no usable vector match config");
   543	                }
   544	                shep.commitDBTransaction();
   545	                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
   546	                    "no usable vector match config");
   547	            }
   548	            shep.commitDBTransaction();
   549	            return MlServiceJobOutcome.ok(annotationIds);
   550	        } catch (Exception ex) {
   551	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
   552	            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
   553	        } finally {
   554	            shep.rollbackAndClose();
   555	        }
   556	    }
   557	
   558	    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
   559	        String code = ex == null ? null : ex.getCode();
   560	        String message = ex == null ? null : ex.getMessage();
   561	        if ("INVALID".equals(code) || "SUCCESS_FALSE".equals(code)) {
   562	            return MlServiceJobOutcome.validationError(code, message);
   563	        }
   564	        if ("TIMEOUT".equals(code) || "NETWORK".equals(code) || "RATE_LIMITED".equals(code)
   565	            || "SERVER_ERROR".equals(code) || "CLIENT_ERROR".equals(code)) {
   566	            return MlServiceJobOutcome.networkError(code, message);
   567	        }
   568	        return MlServiceJobOutcome.networkError("UNKNOWN", message);
   569	    }
   570	
   571	    static String bboxKey(double[] bbox) {
   572	        if (bbox == null || bbox.length != 4) return null;
   573	        return Math.round(bbox[0]) + ":" + Math.round(bbox[1]) + ":" + Math.round(bbox[2]) +
   574	            ":" + Math.round(bbox[3]);
   575	    }
   576	
   577	    static String thetaKey(double theta) {
   578	        return String.format(Locale.US, "%.4f", theta);
   579	    }
   580	
   581	    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
   582	        String bboxKey, String thetaKey) {
   583	        if (ma == null) return null;
   584	        for (Annotation ann : ma.getAnnotations()) {
   585	            if (ann == null) continue;
   586	            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
   587	            if (!sameString(bboxKey, ann.getBboxKey())) continue;
   588	            if (!sameString(thetaKey, ann.getThetaKey())) continue;
   589	            return ann;
   590	        }
   591	        return null;
   592	    }
   593	
   594	    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
   595	        if (!Util.stringExists(taxonomyString)) return null;
   596	        IAJsonProperties iac = IAJsonProperties.iaConfig();
   597	        if (iac == null) return null;
   598	        Taxonomy taxy = shep.getOrCreateTaxonomy(taxonomyString, false);
   599	        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
   600	        if (configs == null || configs.length() == 0) return null;
   601	        JSONObject mlConfig = configs.optJSONObject(0);
   602	        if (mlConfig == null) return null;
   603	        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
   604	        return new ConfigPair(mlConfig, matchConfig);
   605	    }
   606	
   607	    private JSONObject defaultMatchConfig(IAJsonProperties iac, Taxonomy taxy,
   608	        JSONObject mlConfig) {
   609	        JSONObject matchConfig = null;
   610	        JSONArray identConfigs = iac.getIdentConfig(taxy);
   611	        if (identConfigs != null) {
   612	            for (int i = 0; i < identConfigs.length(); i++) {
   613	                JSONObject entry = identConfigs.optJSONObject(i);
   614	                if (entry == null) continue;
   615	                if (entry.optBoolean("default", false)
   616	                    && "vector".equals(entry.optString("pipeline_root", null))) {
   617	                    matchConfig = new JSONObject(entry.toString());
   618	                    break;
   619	                }
   620	            }
   621	        }
   622	        if (matchConfig == null) matchConfig = new JSONObject();
   623	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
   624	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   625	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
   626	        }
   627	        return matchConfig;
   628	    }
   629	
   630	    private JSONObject inferMatchConfig(List<String> annotationIds) {
   631	        if (annotationIds == null || annotationIds.isEmpty()) return null;
   632	        Shepherd shep = new Shepherd(context);
   633	        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
   634	        try {
   635	            shep.beginDBTransaction();
   636	            for (String annId : annotationIds) {
   637	                Annotation ann = shep.getAnnotation(annId);
   638	                if (ann == null) continue;
   639	                Embedding emb = ann.getAnEmbedding();
   640	                if (emb != null) {
   641	                    JSONObject config = new JSONObject();
   642	                    config.put("method", emb.getMethod());
   643	                    config.put("version", emb.getMethodVersion());
   644	                    config.put("pipeline_root", "vector");
   645	                    return config;
   646	                }
   647	            }
   648	            return null;
   649	        } finally {
   650	            shep.rollbackAndClose();
   651	        }
   652	    }
   653	
   654	    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
   655	        JSONObject mlConfig) {
   656	        JSONObject config = (matchConfig == null) ? new JSONObject()
   657	            : new JSONObject(matchConfig.toString());
   658	        if (embeddingSource != null) {
   659	            if (!Util.stringExists(config.optString("method", null))
   660	                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
   661	                config.put("method", embeddingSource.optString("embedding_model_id"));
   662	            }
   663	            if (!Util.stringExists(config.optString("version", null))
   664	                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
   665	                config.put("version", embeddingSource.optString("embedding_model_version"));
   666	            }
   667	        }
   668	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
   669	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   670	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
   671	        }
   672	        return config;
   673	    }
   674	
   675	    private String detectionStaleReason(MediaAsset ma, Encounter enc, String encounterId) {
   676	        if (ma == null) return "media asset missing";
   677	        if (Util.stringExists(encounterId) && enc == null) return "encounter missing";
   678	        String status = ma.getDetectionStatus();
   679	        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
   680	        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
   681	        return null;
   682	    }
   683	
   684	    private String effectiveTaxonomyString(String taxonomyString, Encounter enc) {
   685	        if (Util.stringExists(taxonomyString)) return taxonomyString;
   686	        if (enc != null) return enc.getTaxonomyString();
   687	        return null;
   688	    }
   689	
   690	    private void markTaskError(String taskId, String code, String message) {
   691	        Shepherd shep = new Shepherd(context);
   692	        shep.setAction(ACTION_PREFIX + "markTaskError");
   693	        try {
   694	            shep.beginDBTransaction();
   695	            Task task = Task.load(taskId, shep);
   696	            markTaskError(task, code, message);
   697	            shep.commitDBTransaction();
   698	        } finally {
   699	            shep.rollbackAndClose();
   700	        }
   701	    }
   702	
   703	    private void markTaskCompleted(String taskId) {
   704	        Shepherd shep = new Shepherd(context);
   705	        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
   706	        try {
   707	            shep.beginDBTransaction();
   708	            markTaskCompleted(Task.load(taskId, shep));
   709	            shep.commitDBTransaction();
   710	        } finally {
   711	            shep.rollbackAndClose();
   712	        }
   713	    }
   714	
   715	    private void markTaskError(Task task, String code, String message) {
   716	        if (task == null) return;
   717	        task.setStatus(IBEISIA.STATUS_ERROR);
   718	        task.setStatusDetailsAddError(code == null ? "UNKNOWN" : code, message);
   719	        task.setCompletionDateInMilliseconds();
   720	    }
   721	
   722	    private void markTaskCompleted(Task task) {
   723	        if (task == null) return;
   724	        task.setStatus("completed");
   725	        task.setCompletionDateInMilliseconds();
   726	    }
   727	
   728	    private void markTaskDroppedStale(Shepherd shep, Task task, String reason) {
   729	        if (task == null) return;
   730	        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
   731	        task.setStatusDetailsAddLog(reason);
   732	        task.setCompletionDateInMilliseconds();
   733	    }
   734	
   735	    /**
   736	     * Build and publish a deferred-match payload via the injected
   737	     * {@link DeferredMatchPublisher}. The real publisher wraps
   738	     * {@link IAGateway#requeueJob} with {@code increment=true} so the
   739	     * 30s fixed delay applies (Codex round-4 Blocker: setting
   740	     * {@code __queueRetries} alone does not create the delay).
   741	     *
   742	     * <p>Routing flags: {@code mlServiceV2: true} (IAGateway v2
   743	     * dispatch) AND {@code deferredMatch: true} (MlServiceProcessor
   744	     * deferred branch). Both required — Codex round-5 Blocker
   745	     * documented the dispatch contract.</p>
   746	     *
   747	     * <p>Gate metadata on the payload: {@code attempt} (incremented
   748	     * per DEFER), {@code firstDeferredAt} (epoch-ms of the first
   749	     * DEFER, preserved across re-fires for elapsed-time age-out),
   750	     * {@code lastGateReason} (Codex round-2 #6 diagnostic).</p>
   751	     */
   752	    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId,
   753	        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate) {
   754	        JSONObject payload = new JSONObject();
   755	        // Routing flags — both required for the dispatcher to land
   756	        // the requeue back on MlServiceProcessor's deferred entry
   757	        // point (Codex round-5 Blocker).
   758	        payload.put("mlServiceV2", true);
   759	        payload.put("deferredMatch", true);
   760	        // Diagnostic marker — not the routing contract.
   761	        payload.put("mlServiceV2DeferredMatch", true);
   762	        payload.put("annotationIds", new JSONArray(annotationIds));
   763	        if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
   764	        if (matchConfig != null) payload.put("matchConfig", matchConfig);
   765	        // Carry __context in the payload so the dispatcher's
   766	        // jobj.optString("__context", "context0") fallback at
   767	        // IAGateway.java doesn't silently route the deferred-match
   768	        // into context0 when this processor is running in a non-default
   769	        // context.
   770	        payload.put("__context", context);
   771	        // Gate metadata — incremented for next attempt; firstDeferredAt
   772	        // preserved across re-fires (Codex round-4 OQ #1).
   773	        payload.put("attempt", gate.attempt + 1);
   774	        payload.put("firstDeferredAt", gate.firstDeferredAt);
   775	        if (gate.reason != null) payload.put("lastGateReason", gate.reason);
   776	        try {
   777	            deferredPublisher.publish(payload);
   778	        } catch (Exception ex) {
   779	            // requeueJob doesn't throw declared exceptions, but a future
   780	            // publisher impl might. Don't let publish-failure leak past
   781	            // the orchestrator.
   782	            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
   783	        }
   784	    }
   785	
   786	    private static JSONObject featureParams(double[] bbox, double theta, String viewpoint) {
   787	        JSONObject params = new JSONObject();
   788	        params.put("x", bbox[0]);
   789	        params.put("y", bbox[1]);
   790	        params.put("width", bbox[2]);
   791	        params.put("height", bbox[3]);
   792	        params.put("theta", theta);
   793	        params.put("viewpoint", viewpoint);
   794	        return params;
   795	    }
   796	
   797	    private static double[] parseBbox(JSONArray bbox) {
   798	        return new double[] {
   799	            bbox.getDouble(0), bbox.getDouble(1), bbox.getDouble(2), bbox.getDouble(3)
   800	        };
   801	    }
   802	
   803	    private static double[] toDoubleArray(int[] bbox) {
   804	        return new double[] { bbox[0], bbox[1], bbox[2], bbox[3] };
   805	    }
   806	
   807	    private static List<String> jsonArrayToStringList(JSONArray array) {
   808	        List<String> values = new ArrayList<String>();
   809	        if (array == null) return values;
   810	        for (int i = 0; i < array.length(); i++) {

 succeeded in 223ms:
     1	package org.ecocean.ia;
     2	
     3	import java.util.Collection;
     4	
     5	import org.json.JSONObject;
     6	
     7	/**
     8	 * Gate that decides whether an ml-service-driven match invocation
     9	 * is safe to fire NOW or should be deferred. Solves the
    10	 * empty-match-prospects bug where per-image ml-service jobs fired
    11	 * their match step before sibling jobs in the same import had
    12	 * persisted their annotations into JDO and OpenSearch.
    13	 *
    14	 * <p>Three-phase gate:</p>
    15	 * <ol>
    16	 *   <li><b>Phase 1 (sibling terminal-state):</b> for each sibling
    17	 *       MediaAsset under the per-asset task's topTask, check both
    18	 *       {@code ma.detectionStatus} and the per-MA child Task's raw
    19	 *       persisted status (via
    20	 *       {@link Task#getStoredStatus()}). Either being terminal is
    21	 *       sufficient. Non-terminal siblings cause DEFER.</li>
    22	 *   <li><b>Phase 2 (eligible annotations):</b> resolve annotation
    23	 *       IDs reachable from the sibling MAs via
    24	 *       {@link MatchEligibilityQuery#findEligibleAnnotationIds},
    25	 *       filtered to those with matchAgainst, acmId, and the right
    26	 *       embedding metadata. Phase 1+2 run under a single Shepherd
    27	 *       scope that closes BEFORE Phase 3 (per c11 Phase A/B/C
    28	 *       pattern — no Shepherd held across network).</li>
    29	 *   <li><b>Phase 3 (visibility wait):</b> two-wait split per Codex
    30	 *       round-4 Major: caller IDs through the existing
    31	 *       {@link org.ecocean.OpenSearch#waitForVisibility} (just
    32	 *       {@code _id} visibility), sibling eligible IDs through
    33	 *       {@link org.ecocean.OpenSearch#waitForAnnotationMatchableIds}
    34	 *       (full matchable predicate).</li>
    35	 * </ol>
    36	 *
    37	 * <p>If any phase reports a wait, the gate returns
    38	 * {@link Kind#DEFER}. Once the elapsed time since
    39	 * {@code firstDeferredAt} exceeds {@code MAX_DEFER_AGE_MILLIS}
    40	 * the gate returns {@link Kind#GIVE_UP} so the caller can run
    41	 * match against whatever is visible rather than block forever.</p>
    42	 *
    43	 * <p>(Empty-match-prospects design Track 2 C10.)</p>
    44	 */
    45	public interface MatchVisibilityGate {
    46	    enum Kind { READY, DEFER, GIVE_UP }
    47	
    48	    /**
    49	     * Maximum elapsed wall-clock time from first DEFER to
    50	     * GIVE_UP. Chosen to stay within the existing
    51	     * {@code IAGateway.requeueJob} 30-retry/2-day cap with comfortable
    52	     * margin (Codex round-3 Blocker, refined in round-4 Blocker).
    53	     */
    54	    long MAX_DEFER_AGE_MILLIS = 12L * 60L * 1000L;
    55	
    56	    /**
    57	     * Plain-data carrier for the gate decision. Caller-facing
    58	     * fields are all {@code final}.
    59	     */
    60	    final class GateOutcome {
    61	        public final Kind kind;
    62	        public final int attempt;
    63	        public final long firstDeferredAt;
    64	        public final long elapsedMillis;
    65	        public final String reason;
    66	
    67	        private GateOutcome(Kind kind, int attempt, long firstDeferredAt,
    68	            long elapsedMillis, String reason) {
    69	            this.kind = kind;
    70	            this.attempt = attempt;
    71	            this.firstDeferredAt = firstDeferredAt;
    72	            this.elapsedMillis = elapsedMillis;
    73	            this.reason = reason;
    74	        }
    75	
    76	        public static GateOutcome ready(int attempt, long firstDeferredAt) {
    77	            return new GateOutcome(Kind.READY, attempt, firstDeferredAt,
    78	                System.currentTimeMillis() - firstDeferredAt, null);
    79	        }
    80	
    81	        public static GateOutcome defer(int attempt, long firstDeferredAt,
    82	            String reason) {
    83	            return new GateOutcome(Kind.DEFER, attempt, firstDeferredAt,
    84	                System.currentTimeMillis() - firstDeferredAt, reason);
    85	        }
    86	
    87	        public static GateOutcome giveUp(int attempt, long firstDeferredAt,
    88	            String reason) {
    89	            return new GateOutcome(Kind.GIVE_UP, attempt, firstDeferredAt,
    90	                System.currentTimeMillis() - firstDeferredAt, reason);
    91	        }
    92	    }
    93	
    94	    /**
    95	     * Gate the match for the given child task.
    96	     *
    97	     * @param callerAnnotationIds  the annotations this job's match
    98	     *                              will actually score (always
    99	     *                              included in the visibility wait,
   100	     *                              even if the eligibility filter
   101	     *                              would otherwise exclude them).
   102	     * @param childTaskId          the per-asset child task ID whose
   103	     *                              parent is the topTask carrying
   104	     *                              the sibling MA group.
   105	     * @param matchConfig          the ml-service _id_conf entry,
   106	     *                              source of {@code method} and
   107	     *                              {@code version}.
   108	     * @param attempt              1 for the initial call; the
   109	     *                              deferred-match path increments
   110	     *                              this on each re-fire.
   111	     * @param firstDeferredAt      epoch-ms when the deferral chain
   112	     *                              started, or {@code null} on the
   113	     *                              initial call (the gate stamps
   114	     *                              {@code now} in that case).
   115	     */
   116	    GateOutcome gateForBatch(
   117	        Collection<String> callerAnnotationIds,
   118	        String childTaskId,
   119	        JSONObject matchConfig,
   120	        int attempt,
   121	        Long firstDeferredAt);
   122	}

 succeeded in 223ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.IOException;
     4	import java.util.ArrayList;
     5	import java.util.Arrays;
     6	import java.util.Collection;
     7	import java.util.HashSet;
     8	import java.util.LinkedHashSet;
     9	import java.util.List;
    10	import java.util.Set;
    11	
    12	import org.ecocean.OpenSearch;
    13	import org.ecocean.Util;
    14	import org.ecocean.identity.IBEISIA;
    15	import org.ecocean.media.MediaAsset;
    16	import org.ecocean.shepherd.core.Shepherd;
    17	import org.json.JSONObject;
    18	
    19	/**
    20	 * Production {@link MatchVisibilityGate}. Phase 1 + 2 run under
    21	 * Shepherd; Shepherd closes before Phase 3 (per c11 Phase A/B/C
    22	 * pattern). All state needed by Phase 3 is captured as scalars.
    23	 *
    24	 * <p>(Empty-match-prospects design Track 2 C10.)</p>
    25	 */
    26	public final class MatchVisibilityGateImpl implements MatchVisibilityGate {
    27	
    28	    /**
    29	     * Visibility-poll timeout per phase (caller-id wait + sibling-id
    30	     * wait each get this budget). Matches the visibility-wait
    31	     * budget used by the c7 deferred-match path elsewhere.
    32	     */
    33	    static final long VISIBILITY_TIMEOUT_MS = 15L * 1000L;
    34	
    35	    // MediaAsset.detectionStatus values considered terminal — sibling
    36	    // is done contributing (success or otherwise), don't wait. The set
    37	    // mirrors IBEISIA constants at IBEISIA.java:73-82 (Codex round-3 OQ #3).
    38	    private static final Set<String> TERMINAL_MA_STATUSES =
    39	        new HashSet<String>(Arrays.asList(
    40	            IBEISIA.STATUS_COMPLETE,
    41	            IBEISIA.STATUS_COMPLETE_MLSERVICE,
    42	            IBEISIA.STATUS_PENDING,
    43	            IBEISIA.STATUS_PENDING_SPECIES,
    44	            IBEISIA.STATUS_ERROR,
    45	            IBEISIA.STATUS_DROPPED_STALE));
    46	
    47	    // Task.status values considered terminal — child task is done
    48	    // contributing. Note "completed" (Task) vs "complete" (MA);
    49	    // see Task.statusInEndState() at Task.java:85 (Codex round-4 Major).
    50	    private static final Set<String> TERMINAL_TASK_STATUSES =
    51	        new HashSet<String>(Arrays.asList(
    52	            "completed",
    53	            "error",
    54	            "dropped-stale"));
    55	
    56	    // MA detection statuses considered "produced annotations" — only
    57	    // these contribute to Phase 2's eligibility set. Other terminal
    58	    // statuses (ERROR, DROPPED_STALE, PENDING_SPECIES) contributed
    59	    // nothing.
    60	    private static final Set<String> CONTRIBUTING_MA_STATUSES =
    61	        new HashSet<String>(Arrays.asList(
    62	            IBEISIA.STATUS_COMPLETE,
    63	            IBEISIA.STATUS_COMPLETE_MLSERVICE));
    64	
    65	    private final String context;
    66	    private final OpenSearch openSearch;
    67	
    68	    public MatchVisibilityGateImpl(String context) {
    69	        this(context, new OpenSearch());
    70	    }
    71	
    72	    /** Package-visible: tests inject a mocked OpenSearch. */
    73	    MatchVisibilityGateImpl(String context, OpenSearch openSearch) {
    74	        this.context = context;
    75	        this.openSearch = openSearch;
    76	    }
    77	
    78	    @Override
    79	    public GateOutcome gateForBatch(
    80	        Collection<String> callerAnnotationIds,
    81	        String childTaskId,
    82	        JSONObject matchConfig,
    83	        int attempt,
    84	        Long firstDeferredAt) {
    85	
    86	        long deferStart = (firstDeferredAt == null)
    87	            ? System.currentTimeMillis()
    88	            : firstDeferredAt.longValue();
    89	        if (System.currentTimeMillis() - deferStart > MAX_DEFER_AGE_MILLIS) {
    90	            return GateOutcome.giveUp(attempt, deferStart,
    91	                "exceeded MAX_DEFER_AGE_MILLIS=" + MAX_DEFER_AGE_MILLIS);
    92	        }
    93	
    94	        // Normalize caller ids: drop nulls + dedupe.
    95	        Set<String> normalizedCaller = new LinkedHashSet<String>();
    96	        if (callerAnnotationIds != null) {
    97	            for (String id : callerAnnotationIds) {
    98	                if (id != null) normalizedCaller.add(id);
    99	            }
   100	        }
   101	
   102	        // Derive method/methodVersion from matchConfig using the same
   103	        // fallback chain as Embedding.findMatchProspects
   104	        // (Embedding.java:349-355): _id_conf.method/version first,
   105	        // then MLService.getMethodValues for legacy api_endpoint
   106	        // configs.
   107	        //
   108	        // Whatever falls out of the chain is what we use, including
   109	        // blank strings. The downstream helpers (C8
   110	        // waitForAnnotationMatchableIds, C9 MatchEligibilityQuery, and
   111	        // Annotation.getMatchQuery itself at Annotation.java:1205-1209)
   112	        // are strict-when-non-null; normalizing blank to null here
   113	        // would silently broaden the gate's wait predicate vs the
   114	        // matcher's strict match, causing the gate to declare READY
   115	        // for docs the matcher then rejects (Codex round-1 C10
   116	        // Major).
   117	        String method = (matchConfig == null) ? null
   118	            : matchConfig.optString("method", null);
   119	        String methodVersion = (matchConfig == null) ? null
   120	            : matchConfig.optString("version", null);
   121	        if (!Util.stringExists(method) && matchConfig != null) {
   122	            String[] mv = MLService.getMethodValues(matchConfig);
   123	            method = (mv == null) ? null : mv[0];
   124	            methodVersion = (mv == null) ? null : mv[1];
   125	        }
   126	
   127	        // ---- Phase 1 + 2: under Shepherd ---------------------------
   128	        Phase12Result phase12;
   129	        try {
   130	            phase12 = loadPhase12(childTaskId, method, methodVersion,
   131	                normalizedCaller);
   132	        } catch (IOException ex) {
   133	            // SQL failure during eligibility resolution — DEFER with
   134	            // reason rather than silently proceed against an unknown
   135	            // sibling set (Codex C9 Major).
   136	            return GateOutcome.defer(attempt, deferStart,
   137	                "Phase2 SQL failed: " + ex.getMessage());
   138	        }
   139	        if (phase12.deferReason != null) {
   140	            return GateOutcome.defer(attempt, deferStart, phase12.deferReason);
   141	        }
   142	
   143	        // ---- Phase 3: visibility, no Shepherd held ----------------
   144	        // Wait for caller IDs first using the weaker _id predicate.
   145	        // If a caller annotation is visible by _id but lacks
   146	        // matchAgainst/acmId/embedding metadata, that's a different
   147	        // problem (no candidates returned); we don't want to block
   148	        // the gate on it.
   149	        try {
   150	            if (!normalizedCaller.isEmpty() &&
   151	                !openSearch.waitForVisibility("annotation", normalizedCaller,
   152	                    VISIBILITY_TIMEOUT_MS)) {
   153	                return GateOutcome.defer(attempt, deferStart,
   154	                    "caller IDs not yet visible in OS");
   155	            }
   156	        } catch (IOException ex) {
   157	            return GateOutcome.defer(attempt, deferStart,
   158	                "caller visibility poll IOException: " + ex.getMessage());
   159	        }
   160	
   161	        // Sibling-only set: drop caller IDs from the eligibility set
   162	        // since they're already waited for above with the weaker
   163	        // predicate. Then wait on the remainder with the full
   164	        // matchable predicate.
   165	        Set<String> siblingsOnly = new LinkedHashSet<String>(phase12.eligibleIds);
   166	        siblingsOnly.removeAll(normalizedCaller);
   167	        if (!siblingsOnly.isEmpty()) {
   168	            try {
   169	                if (!openSearch.waitForAnnotationMatchableIds(siblingsOnly,
   170	                    method, methodVersion, VISIBILITY_TIMEOUT_MS)) {
   171	                    return GateOutcome.defer(attempt, deferStart,
   172	                        "sibling IDs not yet matchable in OS");
   173	                }
   174	            } catch (IOException ex) {
   175	                return GateOutcome.defer(attempt, deferStart,
   176	                    "sibling matchable-visibility poll IOException: " +
   177	                    ex.getMessage());
   178	            }
   179	        }
   180	
   181	        return GateOutcome.ready(attempt, deferStart);
   182	    }
   183	
   184	    /**
   185	     * Phase 1 + 2 result carrier. Either {@code deferReason} is
   186	     * non-null (Phase 1 said wait) or {@code eligibleIds} is the
   187	     * Phase 2 eligibility set.
   188	     */
   189	    static final class Phase12Result {
   190	        final String deferReason;
   191	        final Set<String> eligibleIds;
   192	
   193	        private Phase12Result(String deferReason, Set<String> eligibleIds) {
   194	            this.deferReason = deferReason;
   195	            this.eligibleIds = (eligibleIds == null)
   196	                ? new LinkedHashSet<String>() : eligibleIds;
   197	        }
   198	
   199	        static Phase12Result defer(String reason) {
   200	            return new Phase12Result(reason, null);
   201	        }
   202	
   203	        static Phase12Result ready(Set<String> eligibleIds) {
   204	            return new Phase12Result(null, eligibleIds);
   205	        }
   206	    }
   207	
   208	    /**
   209	     * Run Phase 1 + 2 under Shepherd. The transaction closes before
   210	     * we return; the result holds only detached scalars so callers
   211	     * can do network IO without holding the connection.
   212	     */
   213	    Phase12Result loadPhase12(String childTaskId, String method,
   214	        String methodVersion, Set<String> normalizedCaller)
   215	    throws IOException {
   216	        Shepherd shep = new Shepherd(context);
   217	        shep.setAction("MatchVisibilityGate.gateForBatch.phase12." + childTaskId);
   218	        try {
   219	            shep.beginDBTransaction();
   220	            Task child = (childTaskId == null) ? null
   221	                : Task.load(childTaskId, shep);
   222	            if (child == null) {
   223	                // No child task to walk up from. Degrade to caller-only
   224	                // visibility (Codex round-4 OQ #3: log WARN, not silent).
   225	                System.out.println(
   226	                    "WARN: MatchVisibilityGate gating with no child task; childTaskId=" +
   227	                    childTaskId);
   228	                shep.commitDBTransaction();
   229	                return Phase12Result.ready(new LinkedHashSet<String>(normalizedCaller));
   230	            }
   231	            Task topTask = child.getParent();
   232	            if (topTask == null) {
   233	                System.out.println(
   234	                    "WARN: MatchVisibilityGate gating with no parent topTask; childTaskId=" +
   235	                    childTaskId);
   236	                shep.commitDBTransaction();
   237	                return Phase12Result.ready(new LinkedHashSet<String>(normalizedCaller));
   238	            }
   239	            List<MediaAsset> siblingMas = topTask.getObjectMediaAssets();
   240	            if (siblingMas == null || siblingMas.isEmpty()) {
   241	                shep.commitDBTransaction();
   242	                return Phase12Result.ready(new LinkedHashSet<String>(normalizedCaller));
   243	            }
   244	            List<Task> children = topTask.getChildren();
   245	            // Phase 1: sibling terminal-state check.
   246	            for (MediaAsset siblingMa : siblingMas) {
   247	                if (siblingMa == null) continue;
   248	                String maStatus = siblingMa.getDetectionStatus();
   249	                Task siblingChild = findChildTaskForSibling(children, siblingMa);
   250	                String childStatus = (siblingChild == null) ? null
   251	                    : siblingChild.getStoredStatus();
   252	                if (!isSiblingTerminal(maStatus, childStatus)) {
   253	                    shep.commitDBTransaction();
   254	                    return Phase12Result.defer(
   255	                        "sibling MA " + siblingMa.getId() +
   256	                        " non-terminal (ma=" + maStatus + ", task=" +
   257	                        childStatus + ")");
   258	                }
   259	            }
   260	            // Phase 2: eligibility resolution. Only siblings whose MAs
   261	            // actually contributed annotations (complete/complete-mlservice)
   262	            // need to be in the eligibility set.
   263	            List<Integer> contributingMaIds = new ArrayList<Integer>();
   264	            for (MediaAsset siblingMa : siblingMas) {
   265	                if (siblingMa == null) continue;
   266	                if (CONTRIBUTING_MA_STATUSES.contains(siblingMa.getDetectionStatus())) {
   267	                    contributingMaIds.add(Integer.valueOf(siblingMa.getIdInt()));
   268	                }
   269	            }
   270	            Set<String> eligibleIds = MatchEligibilityQuery
   271	                .findEligibleAnnotationIds(shep, contributingMaIds, method,
   272	                    methodVersion);
   273	            // Always include caller IDs in the wait set; the
   274	            // eligibility filter may have excluded them (e.g.,
   275	            // matchAgainst=false), but we must not block the caller's
   276	            // own match on its own visibility (Codex round-4 Major).
   277	            eligibleIds.addAll(normalizedCaller);
   278	            shep.commitDBTransaction();
   279	            return Phase12Result.ready(eligibleIds);
   280	        } catch (IOException ex) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java | sed -n '280,420p'" in /mnt/c/Wildbook-clean2
 succeeded in 234ms:
   280	        } catch (IOException ex) {
   281	            shep.rollbackDBTransaction();
   282	            throw ex;
   283	        } catch (Exception ex) {
   284	            shep.rollbackDBTransaction();
   285	            throw new IOException(
   286	                "MatchVisibilityGate Phase 1+2 failed: " + ex.getMessage(), ex);
   287	        } finally {
   288	            shep.closeDBTransaction();
   289	        }
   290	    }
   291	
   292	    /**
   293	     * Find the per-MA child task by iterating
   294	     * {@code topTask.getChildren()} directly (Codex round-3 Major:
   295	     * NOT {@code Task.getTasksFor(ma)}, which returns any task
   296	     * containing the MA, including the topTask itself or old
   297	     * unrelated tasks).
   298	     *
   299	     * <p>Match by {@code .equals()} (Codex round-4 Major:
   300	     * {@code MediaAsset.getId()} returns String, not int).</p>
   301	     */
   302	    static Task findChildTaskForSibling(List<Task> children, MediaAsset siblingMa) {
   303	        if (children == null || siblingMa == null) return null;
   304	        String maId = siblingMa.getId();
   305	        if (maId == null) return null;
   306	        for (Task child : children) {
   307	            List<MediaAsset> mas = child.getObjectMediaAssets();
   308	            if (mas != null && mas.size() == 1 &&
   309	                maId.equals(mas.get(0).getId())) {
   310	                return child;
   311	            }
   312	        }
   313	        return null;
   314	    }
   315	
   316	    /**
   317	     * A sibling is treated as terminal when EITHER its MediaAsset
   318	     * detectionStatus OR its child Task's stored status is in the
   319	     * terminal set. Several failure paths in MlServiceProcessor
   320	     * mark the child Task error WITHOUT advancing
   321	     * MediaAsset.detectionStatus, so MA-only evaluation misses
   322	     * real terminal states (Codex round-2 Blocker/Major).
   323	     */
   324	    static boolean isSiblingTerminal(String maStatus, String childTaskStatus) {
   325	        if (maStatus != null && TERMINAL_MA_STATUSES.contains(maStatus)) return true;
   326	        if (childTaskStatus != null &&
   327	            TERMINAL_TASK_STATUSES.contains(childTaskStatus)) return true;
   328	        return false;
   329	    }
   330	}

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '620,825p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/DeferredMatchPublisher.java | sed -n '1,80p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java | sed -n '1,80p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java | sed -n '1,240p'" in /mnt/c/Wildbook-clean2
 succeeded in 208ms:
     1	package org.ecocean.ia;
     2	
     3	import org.ecocean.servlet.IAGateway;
     4	import org.json.JSONObject;
     5	
     6	/**
     7	 * Production {@link DeferredMatchPublisher} that re-queues
     8	 * deferred-match payloads through
     9	 * {@link IAGateway#requeueJob(JSONObject, boolean)} with
    10	 * {@code increment=true} so the 30s fixed delay applies
    11	 * (IAGateway.java:785). Calling {@code addToDetectionQueue}
    12	 * directly would publish immediately and hot-loop.
    13	 *
    14	 * <p>(Empty-match-prospects design Track 2 C11 — Codex round-4
    15	 * Blocker: the deferred enqueue must explicitly use
    16	 * {@code requeueJob(payload, true)}, not just stamp
    17	 * {@code __queueRetries} into the JSON.)</p>
    18	 */
    19	public final class IAGatewayDeferredMatchPublisher implements DeferredMatchPublisher {
    20	    @Override
    21	    public void publish(JSONObject payload) {
    22	        IAGateway.requeueJob(payload, true);
    23	    }
    24	}

 succeeded in 208ms:
     1	package org.ecocean.ia;
     2	
     3	import org.json.JSONObject;
     4	
     5	/**
     6	 * Abstraction over the deferred-match enqueue path so
     7	 * {@link org.ecocean.ia.MlServiceProcessor} can be unit-tested
     8	 * without going through the production
     9	 * {@link org.ecocean.servlet.IAGateway#requeueJob} static call. The
    10	 * real implementation wraps {@code requeueJob(payload, true)},
    11	 * which applies the 30s fixed delay (see
    12	 * {@code IAGateway.java:785}); a test double simply captures the
    13	 * published payload for assertions.
    14	 *
    15	 * <p>(Empty-match-prospects design Track 2 C10: testability seam
    16	 * surfaced in Codex round-4 Medium.)</p>
    17	 */
    18	public interface DeferredMatchPublisher {
    19	    /**
    20	     * Publish a deferred-match job payload. Callers are responsible
    21	     * for stamping routing flags (e.g. {@code mlServiceV2: true},
    22	     * {@code deferredMatch: true}) and gate metadata (
    23	     * {@code attempt}, {@code firstDeferredAt},
    24	     * {@code lastGateReason}) into the payload before calling.
    25	     */
    26	    void publish(JSONObject payload);
    27	}

 succeeded in 237ms:
   620	        // this must have a taskId coming in, cuz otherwise how would (detached, async) caller know what it is!
   621	        // __context and __baseUrl should be set -- this is done automatically in IAGateway, but if getting here by some other method, do the work!
   622	        if (jobj.optBoolean("v2", false)) { // lets "new world" ia package do its thing
   623	            IA.handleRest(jobj);
   624	            return;
   625	        }
   626	        if (jobj.optBoolean("MLService", false)) {
   627	            MLService mlserv = new MLService();
   628	            mlserv.processQueueJob(jobj);
   629	            return;
   630	        }
   631	        // Migration plan v2 §commit #10a: ml-service v2 dispatcher branch.
   632	        // The new processor takes context in its constructor (no hardcoded
   633	        // "context0") and returns a typed outcome. Both the detection +
   634	        // extraction lifecycle (Phases 1-5) and the deferred-match path
   635	        // route here; MlServiceProcessor.process(jobj) handles routing
   636	        // internally based on the payload's flags.
   637	        if (jobj.optBoolean("mlServiceV2", false)) {
   638	            String mlContext = jobj.optString("__context", "context0");
   639	            org.ecocean.ia.MlServiceProcessor processor =
   640	                new org.ecocean.ia.MlServiceProcessor(mlContext);
   641	            org.ecocean.ia.MlServiceJobOutcome outcome = processor.process(jobj);
   642	            System.out.println("IAGateway: mlServiceV2 job " +
   643	                jobj.optString("taskId", "?") + " → " + outcome.getKind() +
   644	                (outcome.getCode() == null ? "" : " [" + outcome.getCode() + "]"));
   645	            return;
   646	        }
   647	        boolean requeue = false;
   648	        boolean requeueIncrement = false;
   649	        if ((jobj.optJSONObject("detect") != null) && (jobj.optString("taskId", null) != null)) {
   650	            JSONObject res = new JSONObject("{\"success\": false}");
   651	            res.put("taskId", jobj.getString("taskId"));
   652	            String context = jobj.optString("__context", "context0");
   653	            Shepherd myShepherd = new Shepherd(context);
   654	            myShepherd.setAction("IAGateway.processQueueMessage.detect");
   655	            myShepherd.beginDBTransaction();
   656	            String baseUrl = jobj.optString("__baseUrl", null);
   657	            try {
   658	                JSONObject rtn = _doDetect(jobj, res, myShepherd, baseUrl);
   659	                System.out.println(
   660	                    "INFO: IAGateway.processQueueMessage() 'detect' successful --> " +
   661	                    rtn.toString());
   662	                if (!rtn.optBoolean("success", false)) {
   663	                    requeueIncrement = true;
   664	                    requeue = true;
   665	                    myShepherd.rollbackDBTransaction();
   666	                } else {
   667	                    myShepherd.commitDBTransaction();
   668	                }
   669	            } catch (Exception ex) {
   670	                System.out.println(
   671	                    "ERROR: IAGateway.processQueueMessage() 'detect' threw exception: " +
   672	                    ex.toString());
   673	                if (ex.toString().contains("HTTP error code : 500")) {
   674	                    requeueIncrement = true;
   675	                    requeue = true;
   676	                }
   677	                // error - don't requeue
   678	                else if (ex.toString().contains("HTTP error code : 502")) {
   679	                    requeueIncrement = true;
   680	                    requeue = true;
   681	                }
   682	                // error - don't requeue
   683	                else if (ex.toString().contains("HTTP error code : 608")) {
   684	                    requeue = false;
   685	                } else {
   686	                    requeueIncrement = true;
   687	                    requeue = true;
   688	                }
   689	                myShepherd.rollbackDBTransaction();
   690	            }
   691	            myShepherd.closeDBTransaction();
   692	        } else if ((jobj.optJSONObject("identify") != null) && (jobj.optString("taskId",
   693	            null) != null)) {                                                                               // ditto about taskId
   694	            System.out.println("identify TOP!");
   695	            JSONObject res = new JSONObject("{\"success\": false}");
   696	            res.put("taskId", jobj.getString("taskId"));
   697	            String context = jobj.optString("__context", "context0");
   698	            boolean fastlane = false;
   699	            if (jobj.optBoolean("fastlane", false)) {
   700	                fastlane = true;
   701	            }
   702	            System.out.println(" > context = " + context);
   703	            System.out.println(" > taskId = " + jobj.getString("taskId"));
   704	            System.out.println(" > fastlane = " + jobj.optBoolean("fastlane", false));
   705	            Shepherd myShepherd = new Shepherd(context);
   706	            myShepherd.setAction("IAGateway.processQueueMessage.identify");
   707	            myShepherd.beginDBTransaction();
   708	            String baseUrl = jobj.optString("__baseUrl", null);
   709	            System.out.println("--- BEFORE _doIdentify() ---");
   710	            try {
   711	                // here jobj contains queryconfigdict somehow
   712	                JSONObject rtn = _doIdentify(jobj, res, myShepherd, context, baseUrl, fastlane);
   713	                System.out.println(
   714	                    "INFO: IAGateway.processQueueMessage() 'identify' from successful --> " +
   715	                    rtn.toString());
   716	                if (!rtn.optBoolean("success", false)) {
   717	                    requeueIncrement = true;
   718	                    requeue = true;
   719	                    myShepherd.rollbackDBTransaction();
   720	                } else {
   721	                    myShepherd.commitDBTransaction();
   722	                }
   723	            } catch (javax.jdo.JDOObjectNotFoundException ex) {
   724	                System.out.println(
   725	                    "ERROR: IAGateway.processQueueMessage() 'identify' from threw exception: " +
   726	                    ex.toString());
   727	                if (ex.toString().contains("HTTP error code : 500")) requeueIncrement = false;
   728	                myShepherd.rollbackDBTransaction();
   729	                requeue = false;
   730	            } catch (Exception ex) {
   731	                System.out.println(
   732	                    "ERROR: IAGateway.processQueueMessage() 'identify' from threw exception: " +
   733	                    ex.toString());
   734	                if (ex.toString().contains("HTTP error code : 500")) {
   735	                    requeueIncrement = true;
   736	                    requeue = true;
   737	                } else if (ex.toString().contains("Empty target annotation list")) {
   738	                    requeue = false;
   739	                } else { requeue = true; }
   740	                myShepherd.rollbackDBTransaction();
   741	            }
   742	            myShepherd.closeDBTransaction();
   743	        } else {
   744	            System.out.println(
   745	                "WARNING: IAGateway.processQueueMessage() unable to use json data in '" + message +
   746	                "'; ignoring");
   747	        }
   748	        if (requeue) requeueJob(jobj, requeueIncrement);
   749	    }
   750	
   751	    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
   752	        int MAX_RETRIES = 30;
   753	        long MAX_TIME_MILLIS = 2 * 24 * 60 * 60 * 1000;
   754	        String context = jobj.optString("__context", "context0");
   755	        String taskId = jobj.optString("taskId", "UNKNOWN_TASKID");
   756	        long queueStart = jobj.optLong("__queueStart", System.currentTimeMillis());
   757	        int actualRetries = jobj.optInt("__queueActualRetries", 0);
   758	        int retries = jobj.optInt("__queueRetries", 0);
   759	
   760	        if (retries < 0) retries = 0;
   761	        long elapsed = System.currentTimeMillis() - queueStart;
   762	        if (elapsed > MAX_TIME_MILLIS) retries = MAX_RETRIES + 1; // waiting around too long
   763	        if (retries > MAX_RETRIES) {
   764	            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
   765	                MAX_RETRIES + " retries (or max time) in queue; giving up");
   766	            return false;
   767	        }
   768	        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
   769	            retries + " out of " + MAX_RETRIES + " (actualRetries=" + actualRetries + "; start=" +
   770	            queueStart + "; elapsed=" + elapsed + "; increment=" + increment + ")");
   771	        final long sleepMillis = 1000;
   772	        if (increment) retries++;
   773	        actualRetries++;
   774	        jobj.put("__queueStart", queueStart);
   775	        jobj.put("__queueRetries", retries);
   776	        jobj.put("__queueActualRetries", actualRetries);
   777	
   778	        // now we fork background thread to *wait* and then add this to queue
   779	        Runnable r = new Runnable() {
   780	            public void run() {
   781	                boolean requeueSuccess = false;
   782	                long whileSleepMillis = 1000;
   783	                while (!requeueSuccess) {
   784	                    try {
   785	                        if (increment) whileSleepMillis = 30000;
   786	                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
   787	                        try {
   788	                            Thread.sleep(whileSleepMillis);
   789	                        } catch (java.lang.InterruptedException ex) {}
   790	                        if (jobj.optJSONObject("detect") != null || jobj.optBoolean("fastlane",
   791	                            false) || jobj.optBoolean("MLService", false) ||
   792	                            jobj.optBoolean("mlServiceV2", false)) {
   793	                            // mlServiceV2 retries must land on the detection
   794	                            // queue, not the generic IA queue. Without this,
   795	                            // a retryable ml-service failure would never be
   796	                            // re-dispatched to MlServiceProcessor.
   797	                            addToDetectionQueue(context, jobj.toString());
   798	                        } else {
   799	                            addToQueue(context, jobj.toString());
   800	                        }
   801	                        requeueSuccess = true;
   802	                    } catch (Exception ex) {
   803	                        whileSleepMillis = 30000;
   804	                        System.out.println(
   805	                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
   806	                            taskId + " due to " + ex.toString());
   807	                        ex.printStackTrace();
   808	                    }
   809	                } // end while
   810	            } // end run
   811	        }; // end Runnable
   812	        new Thread(r).start();
   813	
   814	        return true;
   815	    }
   816	
   817	    public static void processCallbackQueueMessage(String message) {
   818	        JSONObject jmsg = Util.stringToJSONObject(message);
   819	
   820	        if (jmsg == null) {
   821	            System.out.println("ERROR: processCallbackQueueMessage() failed to parse JSON from " +
   822	                message);
   823	            return;
   824	        }
   825	        System.out.println("processCallbackQueueMessage got: " + message);

 succeeded in 251ms:
     1	package org.ecocean.ia;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertFalse;
     5	import static org.junit.jupiter.api.Assertions.assertNotNull;
     6	import static org.junit.jupiter.api.Assertions.assertNull;
     7	import static org.junit.jupiter.api.Assertions.assertTrue;
     8	
     9	import java.util.ArrayList;
    10	import java.util.Collection;
    11	import java.util.List;
    12	
    13	import org.json.JSONArray;
    14	import org.json.JSONObject;
    15	import org.junit.jupiter.api.Test;
    16	
    17	/**
    18	 * Coverage of the gate/publisher wiring in
    19	 * {@link MlServiceProcessor}'s deferred-match path. Uses the
    20	 * package-visible test constructor to inject a
    21	 * {@link MatchVisibilityGate} stub and a recording
    22	 * {@link DeferredMatchPublisher}. Sidesteps Shepherd/OpenSearch:
    23	 * a DEFER outcome short-circuits before runMatchProspects, so the
    24	 * tests assert on the published payload without needing a DB.
    25	 *
    26	 * <p>(Empty-match-prospects design Track 2 C11.)</p>
    27	 */
    28	class MlServiceProcessorGateTest {
    29	
    30	    /** Records the payload(s) published by the processor under test. */
    31	    private static final class RecordingPublisher implements DeferredMatchPublisher {
    32	        final List<JSONObject> published = new ArrayList<JSONObject>();
    33	        @Override public void publish(JSONObject payload) {
    34	            published.add(payload);
    35	        }
    36	    }
    37	
    38	    /** Always returns a fixed gate outcome. */
    39	    private static final class StubGate implements MatchVisibilityGate {
    40	        final GateOutcome fixed;
    41	        StubGate(GateOutcome fixed) { this.fixed = fixed; }
    42	        @Override public GateOutcome gateForBatch(
    43	            Collection<String> callerAnnotationIds, String childTaskId,
    44	            JSONObject matchConfig, int attempt, Long firstDeferredAt) {
    45	            return fixed;
    46	        }
    47	    }
    48	
    49	    private static MlServiceProcessor processorWith(MatchVisibilityGate gate,
    50	        DeferredMatchPublisher publisher) {
    51	        return new MlServiceProcessor("context0", new MlServiceClient(),
    52	            gate, publisher);
    53	    }
    54	
    55	    private static JSONObject deferredJobPayload(int attempt,
    56	        Long firstDeferredAt) {
    57	        JSONObject jo = new JSONObject();
    58	        jo.put("mlServiceV2", true);
    59	        jo.put("deferredMatch", true);
    60	        jo.put("annotationIds", new JSONArray().put("ann-1").put("ann-2"));
    61	        jo.put("taskId", "task-1");
    62	        jo.put("matchConfig", new JSONObject()
    63	            .put("method", "miewid-msv4.1").put("version", "4.1"));
    64	        jo.put("attempt", attempt);
    65	        if (firstDeferredAt != null) {
    66	            jo.put("firstDeferredAt", firstDeferredAt.longValue());
    67	        }
    68	        return jo;
    69	    }
    70	
    71	    // --- DEFER path: publisher receives a payload -----------------------
    72	
    73	    @Test void runDeferredMatch_publishesPayload_onGateDefer() {
    74	        long firstDeferred = System.currentTimeMillis();
    75	        MatchVisibilityGate.GateOutcome defer =
    76	            MatchVisibilityGate.GateOutcome.defer(2, firstDeferred,
    77	                "sibling MA 42 non-terminal");
    78	        RecordingPublisher publisher = new RecordingPublisher();
    79	        MlServiceProcessor p = processorWith(new StubGate(defer), publisher);
    80	        MlServiceJobOutcome out = p.runDeferredMatch(
    81	            deferredJobPayload(2, firstDeferred));
    82	        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
    83	        assertEquals(1, publisher.published.size(),
    84	            "expected exactly one re-published payload");
    85	    }
    86	
    87	    @Test void publishedPayloadCarriesBothRoutingFlags() {
    88	        // Codex round-5 Blocker: IAGateway dispatches v2 jobs only when
    89	        // mlServiceV2==true; MlServiceProcessor branches deferred only
    90	        // when deferredMatch==true. Both required.
    91	        long firstDeferred = System.currentTimeMillis();
    92	        RecordingPublisher publisher = new RecordingPublisher();
    93	        MlServiceProcessor p = processorWith(
    94	            new StubGate(MatchVisibilityGate.GateOutcome.defer(
    95	                2, firstDeferred, "non-terminal")),
    96	            publisher);
    97	        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
    98	        JSONObject payload = publisher.published.get(0);
    99	        assertTrue(payload.optBoolean("mlServiceV2", false),
   100	            "missing mlServiceV2: " + payload);
   101	        assertTrue(payload.optBoolean("deferredMatch", false),
   102	            "missing deferredMatch: " + payload);
   103	    }
   104	
   105	    @Test void publishedPayloadIncrementsAttempt() {
   106	        long firstDeferred = System.currentTimeMillis();
   107	        RecordingPublisher publisher = new RecordingPublisher();
   108	        MlServiceProcessor p = processorWith(
   109	            new StubGate(MatchVisibilityGate.GateOutcome.defer(
   110	                3, firstDeferred, "still waiting")),
   111	            publisher);
   112	        p.runDeferredMatch(deferredJobPayload(3, firstDeferred));
   113	        JSONObject payload = publisher.published.get(0);
   114	        assertEquals(4, payload.optInt("attempt", -1));
   115	    }
   116	
   117	    @Test void publishedPayloadPreservesFirstDeferredAt() {
   118	        // Age-out is measured by elapsed wall-clock from the original
   119	        // DEFER, so firstDeferredAt must be carried forward unchanged
   120	        // across re-fires (Codex round-4 OQ #1).
   121	        long firstDeferred = 1700000000000L;
   122	        RecordingPublisher publisher = new RecordingPublisher();
   123	        MlServiceProcessor p = processorWith(
   124	            new StubGate(MatchVisibilityGate.GateOutcome.defer(
   125	                2, firstDeferred, "still waiting")),
   126	            publisher);
   127	        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
   128	        assertEquals(firstDeferred,
   129	            publisher.published.get(0).optLong("firstDeferredAt"));
   130	    }
   131	
   132	    @Test void publishedPayloadCarriesLastGateReason() {
   133	        long firstDeferred = System.currentTimeMillis();
   134	        RecordingPublisher publisher = new RecordingPublisher();
   135	        MlServiceProcessor p = processorWith(
   136	            new StubGate(MatchVisibilityGate.GateOutcome.defer(
   137	                2, firstDeferred, "sibling MA 42 processing-mlservice")),
   138	            publisher);
   139	        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
   140	        assertEquals("sibling MA 42 processing-mlservice",
   141	            publisher.published.get(0).optString("lastGateReason"));
   142	    }
   143	
   144	    @Test void publishedPayloadCarriesAnnotationIdsAndTaskId() {
   145	        long firstDeferred = System.currentTimeMillis();
   146	        RecordingPublisher publisher = new RecordingPublisher();
   147	        MlServiceProcessor p = processorWith(
   148	            new StubGate(MatchVisibilityGate.GateOutcome.defer(
   149	                2, firstDeferred, "still waiting")),
   150	            publisher);
   151	        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
   152	        JSONObject payload = publisher.published.get(0);
   153	        JSONArray ids = payload.optJSONArray("annotationIds");
   154	        assertNotNull(ids);
   155	        assertEquals(2, ids.length());
   156	        assertEquals("ann-1", ids.optString(0));
   157	        assertEquals("ann-2", ids.optString(1));
   158	        assertEquals("task-1", payload.optString("taskId"));
   159	    }
   160	
   161	    @Test void publishedPayloadCarriesContext() {
   162	        long firstDeferred = System.currentTimeMillis();
   163	        RecordingPublisher publisher = new RecordingPublisher();
   164	        MlServiceProcessor p = processorWith(
   165	            new StubGate(MatchVisibilityGate.GateOutcome.defer(
   166	                2, firstDeferred, "still waiting")),
   167	            publisher);
   168	        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
   169	        assertEquals("context0",
   170	            publisher.published.get(0).optString("__context"));
   171	    }
   172	
   173	    // --- runDeferredMatch input validation ------------------------------
   174	
   175	    @Test void runDeferredMatch_returnsValidationError_onNullPayload() {
   176	        RecordingPublisher publisher = new RecordingPublisher();
   177	        MlServiceProcessor p = processorWith(
   178	            new StubGate(MatchVisibilityGate.GateOutcome.ready(
   179	                1, System.currentTimeMillis())),
   180	            publisher);
   181	        MlServiceJobOutcome out = p.runDeferredMatch(null);
   182	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
   183	        assertEquals(0, publisher.published.size(),
   184	            "publisher should not fire on validation error");
   185	    }
   186	}

codex
I’m checking whether the new deferral path preserves the queue routing and age-out semantics all the way through `IAGateway.requeueJob`. The production publisher does hit the detection queue after the 30s delayed requeue, so the basic dispatch route is consistent with the stated contract.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '220,410p'" in /mnt/c/Wildbook-clean2
 succeeded in 258ms:
   220	            shep.rollbackAndClose();
   221	        }
   222	    }
   223	
   224	    private ExtractionContext loadExtractionContext(String taxonomyString, String taskId,
   225	        String annId) {
   226	        Shepherd shep = new Shepherd(context);
   227	        shep.setAction(ACTION_PREFIX + "loadExtractionContext");
   228	        try {
   229	            shep.beginDBTransaction();
   230	            Annotation ann = shep.getAnnotation(annId);
   231	            Task task = Task.load(taskId, shep);
   232	            if (ann == null) {
   233	                markTaskDroppedStale(shep, task, "annotation missing");
   234	                shep.commitDBTransaction();
   235	                return ExtractionContext.done(MlServiceJobOutcome.stale("annotation missing"));
   236	            }
   237	            MediaAsset ma = ann.getMediaAsset();
   238	            if (ma == null) {
   239	                markTaskDroppedStale(shep, task, "annotation media asset missing");
   240	                shep.commitDBTransaction();
   241	                return ExtractionContext.done(
   242	                    MlServiceJobOutcome.stale("annotation media asset missing"));
   243	            }
   244	
   245	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString,
   246	                ann.findEncounter(shep));
   247	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   248	            if (configs == null) {
   249	                markTaskCompleted(task);
   250	                shep.commitDBTransaction();
   251	                return ExtractionContext.done(MlServiceJobOutcome.stale("pending-species"));
   252	            }
   253	            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
   254	                markTaskCompleted(task);
   255	                shep.commitDBTransaction();
   256	                return ExtractionContext.done(MlServiceJobOutcome.stale("embedding-exists"));
   257	            }
   258	
   259	            URL webUrl = ma.webURL();
   260	            if (webUrl == null) {
   261	                markTaskError(task, "INVALID_IMAGE_URI",
   262	                    "Annotation " + annId + " media asset has no webURL");
   263	                shep.commitDBTransaction();
   264	                return ExtractionContext.done(MlServiceJobOutcome.validationError(
   265	                    "INVALID_IMAGE_URI", "Annotation " + annId + " media asset has no webURL"));
   266	            }
   267	            int[] bbox = ann.getBbox();
   268	            if (bbox == null || bbox.length != 4) {
   269	                markTaskError(task, "INVALID_BBOX", "Annotation " + annId + " has no bbox");
   270	                shep.commitDBTransaction();
   271	                return ExtractionContext.done(MlServiceJobOutcome.validationError("INVALID_BBOX",
   272	                    "Annotation " + annId + " has no bbox"));
   273	            }
   274	
   275	            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   276	            shep.commitDBTransaction();
   277	            return new ExtractionContext(webUrl.toString(),
   278	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   279	                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
   280	        } finally {
   281	            shep.rollbackAndClose();
   282	        }
   283	    }
   284	
   285	    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
   286	        Shepherd shep = new Shepherd(context);
   287	        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
   288	        try {
   289	            shep.beginDBTransaction();
   290	            MediaAsset ma = shep.getMediaAsset(maId);
   291	            Task task = Task.load(taskId, shep);
   292	            String staleReason = detectionStaleReason(ma, null, null);
   293	            if (staleReason != null) {
   294	                markTaskDroppedStale(shep, task, staleReason);
   295	                shep.commitDBTransaction();
   296	                return MlServiceJobOutcome.stale(staleReason);
   297	            }
   298	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   299	            markTaskCompleted(task);
   300	            shep.commitDBTransaction();
   301	            return MlServiceJobOutcome.okZeroDetections();
   302	        } catch (Exception ex) {
   303	            markTaskError(taskId, "PERSIST", "zero-detection finalize failed: " + ex.getMessage());
   304	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
   305	        } finally {
   306	            shep.rollbackAndClose();
   307	        }
   308	    }
   309	
   310	    private PersistResult persistDetections(String maId, String encounterId, String taskId,
   311	        DetectionContext det, JSONArray results) {
   312	        Shepherd shep = new Shepherd(context);
   313	        shep.setAction(ACTION_PREFIX + "persistDetections");
   314	        List<String> annotationIds = new ArrayList<String>();
   315	
   316	        try {
   317	            FeatureType.initAll(shep);
   318	            shep.beginDBTransaction();
   319	            MediaAsset ma = shep.getMediaAsset(maId);
   320	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   321	            Task task = Task.load(taskId, shep);
   322	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   323	            if (staleReason != null) {
   324	                markTaskDroppedStale(shep, task, staleReason);
   325	                shep.commitDBTransaction();
   326	                return PersistResult.done(MlServiceJobOutcome.stale(staleReason));
   327	            }
   328	
   329	            for (int i = 0; i < results.length(); i++) {
   330	                JSONObject result = results.getJSONObject(i);
   331	                double[] bbox = parseBbox(result.getJSONArray("bbox"));
   332	                double theta = result.getDouble("theta");
   333	                String bboxKey = bboxKey(bbox);
   334	                String thetaKey = thetaKey(theta);
   335	                String predictModelId = result.optString("predict_model_id",
   336	                    det.mlConfig.optString("predict_model_id", null));
   337	                Annotation existing = findExistingAnnotation(ma, predictModelId, bboxKey,
   338	                    thetaKey);
   339	                if (existing != null) {
   340	                    annotationIds.add(existing.getId());
   341	                    continue;
   342	                }
   343	
   344	                JSONObject featureParams = featureParams(bbox, theta,
   345	                    result.optString("viewpoint", null));
   346	                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
   347	                String iaClass = result.optString("iaClass",
   348	                    result.optString("class_name", result.optString("class", null)));
   349	                Annotation ann = new Annotation(null, feature, iaClass);
   350	                ann.__setMediaAsset(ma);
   351	                ann.setAcmId(ann.getId());
   352	                ann.setMatchAgainst(true);
   353	                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   354	                ann.setPredictModelId(predictModelId);
   355	                ann.setBboxKey(bboxKey);
   356	                ann.setThetaKey(thetaKey);
   357	                ann.setWbiaRegistered(Boolean.FALSE);
   358	                ann.setWbiaRegisterAttempts(0);
   359	                ann.setViewpoint(result.optString("viewpoint", null));
   360	                ann.setQuality(optionalFiniteDouble(result, "score",
   361	                    optionalFiniteDouble(result, "confidence", null)));
   362	
   363	                // Bidirectional linkage:
   364	                //   MediaAsset.addFeature sets Feature.asset
   365	                //   Feature.setAnnotation sets the OWNING side of the
   366	                //     Annotation.features collection (which is mapped-by
   367	                //     "annotation"). Without explicitly setting this, the
   368	                //     ANNOTATION_FEATURES join row depends on DataNucleus
   369	                //     relationship management — fragile.
   370	                //   Annotation.addFeature puts the feature in the in-memory
   371	                //     list so reload returns it as expected.
   372	                ma.addFeature(feature);
   373	                feature.setAnnotation(ann);
   374	                ann.addFeature(feature);
   375	                if (enc != null) enc.addAnnotation(ann);
   376	                shep.getPM().makePersistent(feature);
   377	                shep.getPM().makePersistent(ann);
   378	
   379	                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
   380	                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
   381	                shep.getPM().makePersistent(emb);
   382	                annotationIds.add(ann.getId());
   383	            }
   384	
   385	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   386	            markTaskCompleted(task);
   387	            shep.commitDBTransaction();
   388	            return PersistResult.ok(annotationIds);
   389	        } catch (Exception ex) {
   390	            markTaskError(taskId, "PERSIST", "detection persist failed: " + ex.getMessage());
   391	            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
   392	                ex.getMessage()));
   393	        } finally {
   394	            shep.rollbackAndClose();
   395	        }
   396	    }
   397	
   398	    private PersistResult persistExtraction(String annId, String taskId, ExtractionContext ext,
   399	        JSONObject response) {
   400	        Shepherd shep = new Shepherd(context);
   401	        shep.setAction(ACTION_PREFIX + "persistExtraction");
   402	        List<String> annotationIds = new ArrayList<String>();
   403	
   404	        try {
   405	            shep.beginDBTransaction();
   406	            Annotation ann = shep.getAnnotation(annId);
   407	            Task task = Task.load(taskId, shep);
   408	            if (ann == null || ann.getMediaAsset() == null) {
   409	                markTaskDroppedStale(shep, task, "annotation missing");
   410	                shep.commitDBTransaction();

exec
/bin/bash -lc "rg -n \"class Task|new Task\\(|addChild|getParent|getChildren|storeNewTask|setCompletionDate|statusInEndState|getStoredStatus|setStatus\\(\" src/main/java/org/ecocean/ia/Task.java src/main/java/org/ecocean/shepherd/core/Shepherd.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"void storeNewTask|storeNewTask\\(\" src/main/java/org/ecocean/shepherd/core/Shepherd.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '220,520p'" in /mnt/c/Wildbook-clean2
 succeeded in 218ms:
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

 succeeded in 236ms:
src/main/java/org/ecocean/ia/Task.java:28:public class Task implements java.io.Serializable {
src/main/java/org/ecocean/ia/Task.java:85:    public boolean statusInEndState() {
src/main/java/org/ecocean/ia/Task.java:247:    public List<Task> getChildren() {
src/main/java/org/ecocean/ia/Task.java:258:            this.addChild(kid); // let this do the work
src/main/java/org/ecocean/ia/Task.java:262:    public List<Task> addChild(Task kid) {
src/main/java/org/ecocean/ia/Task.java:272:        t.addChild(this);
src/main/java/org/ecocean/ia/Task.java:275:    public Task getParent() {
src/main/java/org/ecocean/ia/Task.java:279:    public String getParentId() {
src/main/java/org/ecocean/ia/Task.java:588:            List<Task> children = getChildren();
src/main/java/org/ecocean/ia/Task.java:607:    public String getStoredStatus() {
src/main/java/org/ecocean/ia/Task.java:614:        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
src/main/java/org/ecocean/ia/Task.java:666:            for (Task childTask : this.getChildren()) {
src/main/java/org/ecocean/ia/Task.java:668:                    for (Task childTask2 : childTask.getChildren()) {
src/main/java/org/ecocean/ia/Task.java:713:    public void setStatus(String newStatus) {
src/main/java/org/ecocean/ia/Task.java:722:    public void setCompletionDateInMilliseconds(Long ms) {
src/main/java/org/ecocean/ia/Task.java:727:    public void setCompletionDateInMilliseconds() {
src/main/java/org/ecocean/ia/Task.java:824:        rtn.put("parentTaskId", getParentId());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:308:    public boolean storeNewTask(Task task) {

 succeeded in 204ms:
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
   316	            if (found != null) return found;
   317	        }
   318	        return null;
   319	    }
   320	
   321	    public Task deepContains(MediaAsset ma) {
   322	        if (this.contains(ma)) return this;
   323	        if (!this.hasChildren()) return null;
   324	        for (Task kid : children) {
   325	            Task found = kid.deepContains(ma);
   326	            if (found != null) return found;
   327	        }
   328	        return null;
   329	    }
   330	
   331	    public List<Task> findNodesWithMediaAssets() {
   332	        List<Task> found = new ArrayList<Task>();
   333	
   334	        if (this.hasObjectMediaAssets()) found.add(this);
   335	        if (this.hasChildren())
   336	            for (Task kid : this.children) {
   337	                found.addAll(kid.findNodesWithMediaAssets());
   338	            }
   339	        return found;
   340	    }
   341	
   342	    public List<Task> findNodesWithAnnotations() {
   343	        List<Task> found = new ArrayList<Task>();
   344	
   345	        if (this.hasObjectAnnotations()) found.add(this);
   346	        if (this.hasChildren())
   347	            for (Task kid : this.children) {
   348	                found.addAll(kid.findNodesWithAnnotations());
   349	            }
   350	        return found;
   351	    }
   352	
   353	    public Map<String, Integer> detectionStatusSummary() {
   354	        Map<String, Integer> cts = new HashMap<String, Integer>();
   355	
   356	        if (!this.hasObjectMediaAssets()) return cts;
   357	        for (MediaAsset ma : this.getObjectMediaAssets()) {
   358	            String status = ma.getDetectionStatus();
   359	            if (status == null) status = "";
   360	            cts.put(status, cts.getOrDefault(status, 0) + 1);
   361	        }
   362	        return cts;
   363	    }
   364	
   365	    public Map<String, Integer> identificationStatusSummary() {
   366	        Map<String, Integer> cts = new HashMap<String, Integer>();
   367	
   368	        if (!this.hasObjectAnnotations()) return cts;
   369	        for (Annotation ann : this.getObjectAnnotations()) {
   370	            String status = ann.getIdentificationStatus();
   371	            if (status == null) status = "";
   372	            cts.put(status, cts.getOrDefault(status, 0) + 1);
   373	        }
   374	        return cts;
   375	    }
   376	
   377	    public JSONObject getStatusDetails() {
   378	        return Util.stringToJSONObject(statusDetails);
   379	    }
   380	
   381	    public void setStatusDetails(String s) {
   382	        statusDetails = s;
   383	    }
   384	
   385	    public void setStatusDetails(JSONObject j) {
   386	        if (j == null) {
   387	            statusDetails = null;
   388	        } else {
   389	            statusDetails = j.toString();
   390	        }
   391	    }
   392	
   393	    public void setStatusDetailsAddError(String code, String message) {
   394	        JSONObject add = new JSONObject();
   395	
   396	        add.put("code", code);
   397	        add.put("message", message);
   398	        setStatusDetailsAddToSection("errors", add);
   399	    }
   400	
   401	    public void setStatusDetailsAddLog(String message) {
   402	        JSONObject add = new JSONObject();
   403	
   404	        add.put("message", message);
   405	        setStatusDetailsAddToSection("log", add);
   406	    }
   407	
   408	    // internal utility method for above
   409	    private void setStatusDetailsAddToSection(String section, JSONObject add) {
   410	        if (add == null) return;
   411	        add.put("timestamp", System.currentTimeMillis());
   412	        JSONObject sd = getStatusDetails();
   413	        if (sd == null) sd = new JSONObject();
   414	        if (sd.optJSONArray(section) == null) sd.put(section, new JSONArray());
   415	        sd.getJSONArray(section).put(add);
   416	        setStatusDetails(sd);
   417	    }
   418	
   419	    public JSONObject getParameters() { // only return as JSONObject!
   420	        return Util.stringToJSONObject(parameters);
   421	    }
   422	
   423	    // see comment above: should this even be public?  (or exist)
   424	    public void setParameters(String s) { // best be json, yo
   425	        parameters = s;
   426	    }
   427	
   428	    public void setParameters(JSONObject j) {
   429	        if (j == null) {
   430	            parameters = null;
   431	        } else {
   432	            parameters = j.toString();
   433	        }
   434	    }
   435	
   436	    // convenience method to construct the JSONObject from key/value
   437	    public void setParameters(String key, Object value) {
   438	        if (key == null) return; // nope
   439	        JSONObject j = new JSONObject();
   440	        j.put(key, value); // value object type better be kosher for JSONObject.  :/
   441	        parameters = j.toString();
   442	    }
   443	
   444	    // like above, but doesnt (re)set .parameters, will only append/alter the key'ed one
   445	    public void addParameter(String key, Object value) {
   446	        if (key == null) return;
   447	        JSONObject j = this.getParameters();
   448	        if (j == null) j = new JSONObject();
   449	        j.put(key, value);
   450	        parameters = j.toString();
   451	    }
   452	
   453	    public JSONObject toJSONObject() {
   454	        return this.toJSONObject(false);
   455	    }
   456	
   457	    public JSONObject toJSONObject(boolean includeChildren) {
   458	        JSONObject j = new JSONObject();
   459	
   460	        j.put("id", id);
   461	        j.put("parameters", this.getParameters());
   462	        j.put("created", created);
   463	        j.put("modified", modified);
   464	        j.put("createdDate", new DateTime(created));
   465	        j.put("modifiedDate", new DateTime(modified));
   466	        if ((objectMediaAssets != null) && (objectMediaAssets.size() > 0)) {
   467	            JSONArray jo = new JSONArray();
   468	            for (MediaAsset ma : this.objectMediaAssets) {
   469	                jo.put(ma.getId());
   470	            }
   471	            j.put("mediaAssetIds", jo);
   472	        }
   473	        if ((objectAnnotations != null) && (objectAnnotations.size() > 0)) {
   474	            JSONArray jo = new JSONArray();
   475	            for (Annotation ann : this.objectAnnotations) {
   476	                jo.put(ann.getId());
   477	            }
   478	            j.put("annotationIds", jo);
   479	        }
   480	        if (includeChildren && this.hasChildren()) {
   481	            JSONArray jc = new JSONArray();
   482	            for (Task kid : this.children) {
   483	                jc.put(kid.toJSONObject(true)); // we once again assume no looping!  bon chance.
   484	            }
   485	            j.put("children", jc);
   486	        }
   487	        return j;
   488	    }
   489	
   490	    // need these two so we can use things like List.contains() on tasks
   491	    public boolean equals(final Object t2) {
   492	        if (t2 == null) return false;
   493	        if (!(t2 instanceof Task)) return false;
   494	        Task two = (Task)t2;
   495	        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
   496	        return this.id.equals(two.getId());
   497	    }
   498	
   499	    public int hashCode() {
   500	        if (id == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two users with no uuid equals! :/
   501	        return id.hashCode();
   502	    }
   503	
   504	    public String toString() {
   505	        return new ToStringBuilder(this)
   506	                   .append(id)
   507	                   .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
   508	                   .append(numChildren() + "Kids")
   509	                   .append(countObjectMediaAssets() + "MA")
   510	                   .append(countObjectAnnotations() + "Ann")
   511	                   .append("params=" + ((this.getParameters() ==
   512	                null) ? "(none)" : this.getParameters().toString()))
   513	                   .toString();
   514	    }
   515	
   516	    public static Task load(String taskId, Shepherd myShepherd) {
   517	        Task t = null;
   518	
   519	        try {
   520	            t = ((Task)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(

 succeeded in 228ms:
308:    public boolean storeNewTask(Task task) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '580,740p'" in /mnt/c/Wildbook-clean2
 succeeded in 239ms:
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
   631	            JSONObject islObj = l.toJSONObject();
   632	            if (islObj.optString("status") != null &&
   633	                islObj.optString("status").equals("completed")) {
   634	                status = islObj.optString("status");
   635	            } else if (islObj.optJSONObject("status") != null &&
   636	                (islObj.optJSONObject("status").optJSONObject("needReview") != null)) {
   637	                status = "completed";
   638	            } else if (logs.toString().indexOf("score") > -1) {
   639	                status = "completed";
   640	            } else if (islObj.toString().indexOf("HTTP error code") > -1) {
   641	                status = "error";
   642	            } else if (!islObj.optString("queueStatus").equals("")) {
   643	                status = islObj.optString("queueStatus");
   644	            } else if (islObj.opt("status") != null &&
   645	                islObj.opt("status").toString().indexOf("initIdentify") > -1) {
   646	                status = "queuing";
   647	            }
   648	            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
   649	            // if(status.equals("waiting to queue"))System.out.println("islObj: "+islObj.toString());
   650	        }
   651	        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
   652	            this.getId());
   653	        return status;
   654	    }
   655	
   656	    // this is stitched together from import.jsp. godspeed.
   657	    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
   658	    // also the original building/modification of (passed-in) idStatusMap is dropped
   659	    public String getOverallStatus(Shepherd myShepherd) {
   660	        String status = "unknown";
   661	
   662	        if (this.hasChildren()) {
   663	            // accumulate status across children
   664	            HashMap<String, String> map = new HashMap<String, String>();
   665	            // this should only ever be two layers deep
   666	            for (Task childTask : this.getChildren()) {
   667	                if (childTask.hasChildren()) {
   668	                    for (Task childTask2 : childTask.getChildren()) {
   669	                        if ((childTask2.getObjectAnnotations() != null) &&
   670	                            (childTask2.getObjectAnnotations().size() > 0) &&
   671	                            childTask2.getObjectAnnotations().get(0).getMatchAgainst() &&
   672	                            (childTask2.getObjectAnnotations().get(0).getIAClass() != null)) {
   673	                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
   674	                        }
   675	                    }
   676	                } else {
   677	                    if ((childTask.getObjectAnnotations() != null) &&
   678	                        (childTask.getObjectAnnotations().size() > 0) &&
   679	                        childTask.getObjectAnnotations().get(0).getMatchAgainst() &&
   680	                        (childTask.getObjectAnnotations().get(0).getIAClass() != null)) {
   681	                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
   682	                    }
   683	                }
   684	            }
   685	            // now, how do we report these?
   686	            HashMap<String, Integer> resultsMap = new HashMap<String, Integer>();
   687	            for (String key : map.values()) {
   688	                // task results
   689	                if (!resultsMap.containsKey(key)) {
   690	                    resultsMap.put(key, new Integer(1));
   691	                } else {
   692	                    resultsMap.put(key, new Integer(resultsMap.get(key) + 1));
   693	                }
   694	            }
   695	            status = resultsMap.toString();
   696	        } else { // childless
   697	            status = this.getStatus(myShepherd);
   698	        }
   699	        return status;
   700	    }
   701	
   702	    public boolean isFastlane(Shepherd myShepherd) {
   703	        String status = "waiting to queue";
   704	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   705	            myShepherd);
   706	
   707	        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
   708	            return true;
   709	        }
   710	        return false;
   711	    }
   712	
   713	    public void setStatus(String newStatus) {
   714	        setModified();
   715	        if (newStatus == null) status = null;
   716	        else { status = newStatus; }
   717	    }
   718	
   719	    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }
   720	
   721	    // this will set all date stuff based on ms since epoch
   722	    public void setCompletionDateInMilliseconds(Long ms) {
   723	        this.completionDateInMilliseconds = ms;
   724	    }
   725	
   726	    // no arg = set to now
   727	    public void setCompletionDateInMilliseconds() {
   728	        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
   729	    }
   730	
   731	    // capture original queue message to make this Task more easily resumeable
   732	    public String getQueueResumeMessage() { return queueResumeMessage; }
   733	    public void setQueueResumeMessage(String message) {
   734	        if (message == null) { queueResumeMessage = null; } else {
   735	            queueResumeMessage = message;
   736	        }
   737	    }
   738	
   739	    public JSONObject getMatchingSetFilter() {
   740	        if (getParameters() == null) return null;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/shepherd/core/Shepherd.java | sed -n '292,330p'" in /mnt/c/Wildbook-clean2
 succeeded in 272ms:
   292	
   293	    public boolean storeNewScanTask(ScanTask scanTask) {
   294	        beginDBTransaction();
   295	        try {
   296	            pm.makePersistent(scanTask);
   297	            commitDBTransaction();
   298	            return true;
   299	        } catch (Exception e) {
   300	            rollbackDBTransaction();
   301	            e.printStackTrace();
   302	            System.out.println("I failed to store the new ScanTask number: " +
   303	                scanTask.getUniqueNumber());
   304	            return false;
   305	        }
   306	    }
   307	
   308	    public boolean storeNewTask(Task task) {
   309	        beginDBTransaction();
   310	        try {
   311	            pm.makePersistent(task);
   312	            updateDBTransaction();
   313	            return true;
   314	        } catch (Exception e) {
   315	            rollbackDBTransaction();
   316	            e.printStackTrace();
   317	            System.out.println("I failed to store the new IA Task with ID: " + task.getId());
   318	            return false;
   319	        }
   320	    }
   321	
   322	    public boolean storeNewCollaboration(Collaboration collab) {
   323	        beginDBTransaction();
   324	        try {
   325	            pm.makePersistent(collab);
   326	            commitDBTransaction();
   327	            return true;
   328	        } catch (Exception e) {
   329	            rollbackDBTransaction();
   330	            System.out.println(

exec
/bin/bash -lc 'git diff --check -- src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/ia/IAException.java src/main/java/org/ecocean/ia/MLService.java src/main/java/org/ecocean/ia/MatchResult.java src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/java/org/ecocean/ia/MlServiceClient.java src/main/java/org/ecocean/ia/MlServiceJobOutcome.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff --ignore-space-at-eol --stat -- src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/ia/IAException.java src/main/java/org/ecocean/ia/MLService.java src/main/java/org/ecocean/ia/MatchResult.java src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/java/org/ecocean/ia/MlServiceClient.java src/main/java/org/ecocean/ia/MlServiceJobOutcome.java src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff --ignore-space-at-eol -- src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
 succeeded in 422ms:
diff --git a/src/main/java/org/ecocean/ia/MlServiceProcessor.java b/src/main/java/org/ecocean/ia/MlServiceProcessor.java
index 04b14d625..45c639df8 100644
--- a/src/main/java/org/ecocean/ia/MlServiceProcessor.java
+++ b/src/main/java/org/ecocean/ia/MlServiceProcessor.java
@@ -37,14 +37,34 @@ public class MlServiceProcessor {
 
     private final String context;
     private final MlServiceClient client;
+    private final MatchVisibilityGate visibilityGate;
+    private final DeferredMatchPublisher deferredPublisher;
 
     public MlServiceProcessor(String context) {
-        this(context, new MlServiceClient());
+        this(context, new MlServiceClient(),
+            new MatchVisibilityGateImpl(context),
+            new IAGatewayDeferredMatchPublisher());
     }
 
     public MlServiceProcessor(String context, MlServiceClient client) {
+        this(context, client, new MatchVisibilityGateImpl(context),
+            new IAGatewayDeferredMatchPublisher());
+    }
+
+    /**
+     * Test-friendly constructor that accepts injected
+     * {@link MatchVisibilityGate} and {@link DeferredMatchPublisher}.
+     * Production code should use the no-arg or single-arg constructor
+     * above. (Empty-match-prospects design Track 2 C11 testability
+     * seam — Codex round-4 Medium.)
+     */
+    MlServiceProcessor(String context, MlServiceClient client,
+        MatchVisibilityGate visibilityGate,
+        DeferredMatchPublisher deferredPublisher) {
         this.context = context;
         this.client = client;
+        this.visibilityGate = visibilityGate;
+        this.deferredPublisher = deferredPublisher;
     }
 
     /** Process one ml-service queue job. Returns the outcome. */
@@ -417,17 +437,41 @@ public class MlServiceProcessor {
 
     private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
         JSONObject matchConfig) {
-        try {
-            OpenSearch os = new OpenSearch();
-            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
-                enqueueDeferredMatch(annotationIds, taskId);
-                return MlServiceJobOutcome.ok(annotationIds);
-            }
-        } catch (IOException ex) {
-            enqueueDeferredMatch(annotationIds, taskId);
+        // Initial invocation: attempt=1, firstDeferredAt=null (the
+        // gate stamps `now` so age-out is measured from this first
+        // call, not from later re-fires).
+        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
+    }
+
+    /**
+     * Shared body for the initial {@link #waitAndRunMatch} call and
+     * the re-gated {@link #runDeferredMatch} path. Drives the
+     * {@link MatchVisibilityGate}: READY → run match; DEFER → publish
+     * a deferred-match job through the publisher; GIVE_UP → log WARN
+     * and run match against whatever is visible (partial results are
+     * better than silently no match task; Codex round-2 #2).
+     *
+     * <p>(Empty-match-prospects design Track 2 C11.)</p>
+     */
+    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
+        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
+        MatchVisibilityGate.GateOutcome gate = visibilityGate.gateForBatch(
+            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
+        switch (gate.kind) {
+          case READY:
+            return runMatchProspects(annotationIds, taskId, matchConfig);
+          case DEFER:
+            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
             return MlServiceJobOutcome.ok(annotationIds);
+          case GIVE_UP:
+          default:
+            System.out.println(
+                "WARN: MatchVisibilityGate aged out for task " + taskId +
+                " after attempt=" + gate.attempt + " elapsed=" +
+                gate.elapsedMillis + "ms reason=" + gate.reason +
+                "; running match against current visible corpus");
+            return runMatchProspects(annotationIds, taskId, matchConfig);
         }
-        return runMatchProspects(annotationIds, taskId, matchConfig);
     }
 
     public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
@@ -438,7 +482,17 @@ public class MlServiceProcessor {
         String taskId = jobData.optString("taskId", null);
         JSONObject matchConfig = jobData.optJSONObject("matchConfig");
         if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
-        return runMatchProspects(annotationIds, taskId, matchConfig);
+        // Carry forward attempt + firstDeferredAt so age-out is
+        // measured by elapsed wall-clock from the original DEFER, not
+        // by attempt count (Codex round-4 OQ #1).
+        int attempt = jobData.optInt("attempt", 2);
+        Long firstDeferredAt = jobData.has("firstDeferredAt")
+            ? Long.valueOf(jobData.optLong("firstDeferredAt")) : null;
+        // Re-gate; deferred match earns the same protection as the
+        // initial call (Codex round-2 Major: don't degrade back to
+        // today's bug on the first deferral).
+        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
+            attempt, firstDeferredAt);
     }
 
     public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
@@ -678,20 +732,53 @@ public class MlServiceProcessor {
         task.setCompletionDateInMilliseconds();
     }
 
-    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
+    /**
+     * Build and publish a deferred-match payload via the injected
+     * {@link DeferredMatchPublisher}. The real publisher wraps
+     * {@link IAGateway#requeueJob} with {@code increment=true} so the
+     * 30s fixed delay applies (Codex round-4 Blocker: setting
+     * {@code __queueRetries} alone does not create the delay).
+     *
+     * <p>Routing flags: {@code mlServiceV2: true} (IAGateway v2
+     * dispatch) AND {@code deferredMatch: true} (MlServiceProcessor
+     * deferred branch). Both required — Codex round-5 Blocker
+     * documented the dispatch contract.</p>
+     *
+     * <p>Gate metadata on the payload: {@code attempt} (incremented
+     * per DEFER), {@code firstDeferredAt} (epoch-ms of the first
+     * DEFER, preserved across re-fires for elapsed-time age-out),
+     * {@code lastGateReason} (Codex round-2 #6 diagnostic).</p>
+     */
+    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId,
+        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate) {
         JSONObject payload = new JSONObject();
+        // Routing flags — both required for the dispatcher to land
+        // the requeue back on MlServiceProcessor's deferred entry
+        // point (Codex round-5 Blocker).
         payload.put("mlServiceV2", true);
         payload.put("deferredMatch", true);
+        // Diagnostic marker — not the routing contract.
+        payload.put("mlServiceV2DeferredMatch", true);
         payload.put("annotationIds", new JSONArray(annotationIds));
         if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
+        if (matchConfig != null) payload.put("matchConfig", matchConfig);
         // Carry __context in the payload so the dispatcher's
-        // jobj.optString("__context", "context0") fallback at IAGateway.java
-        // doesn't silently route the deferred-match into context0 when this
-        // processor is running in a non-default context.
+        // jobj.optString("__context", "context0") fallback at
+        // IAGateway.java doesn't silently route the deferred-match
+        // into context0 when this processor is running in a non-default
+        // context.
         payload.put("__context", context);
+        // Gate metadata — incremented for next attempt; firstDeferredAt
+        // preserved across re-fires (Codex round-4 OQ #1).
+        payload.put("attempt", gate.attempt + 1);
+        payload.put("firstDeferredAt", gate.firstDeferredAt);
+        if (gate.reason != null) payload.put("lastGateReason", gate.reason);
         try {
-            IAGateway.addToDetectionQueue(context, payload.toString());
-        } catch (IOException ex) {
+            deferredPublisher.publish(payload);
+        } catch (Exception ex) {
+            // requeueJob doesn't throw declared exceptions, but a future
+            // publisher impl might. Don't let publish-failure leak past
+            // the orchestrator.
             System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
         }
     }

 succeeded in 521ms:
 .../java/org/ecocean/ia/MlServiceProcessor.java    | 121 ++++++++++++++++++---
 1 file changed, 104 insertions(+), 17 deletions(-)

 exited 2 in 579ms:
src/main/java/org/ecocean/ia/IA.java:1: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/IA.java:2: trailing whitespace.
+    idea here is "simple": a gateway to all IA calls, mostly (now) cleaned up from identity/IBEISIA.java (guessing)
src/main/java/org/ecocean/ia/IA.java:3: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:4: trailing whitespace.
+    THIS IS A WORK-IN-PROGRESS
src/main/java/org/ecocean/ia/IA.java:5: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:6: trailing whitespace.
+    proposed key concepts:
src/main/java/org/ecocean/ia/IA.java:7: trailing whitespace.
+ * can handle multiple IA frameworks (not just historic-IBEIS)
src/main/java/org/ecocean/ia/IA.java:8: trailing whitespace.
+      - likely a base abstract class with a "isEnabled() / init()" concept
src/main/java/org/ecocean/ia/IA.java:9: trailing whitespace.
+      - classes would allow for instances of each IA framework?
src/main/java/org/ecocean/ia/IA.java:10: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:11: trailing whitespace.
+ * no idea how to handle crazy (and configurable!?) workflow!
src/main/java/org/ecocean/ia/IA.java:12: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:13: trailing whitespace.
+ * probably should "leverage" Queue stuff where applicable?
src/main/java/org/ecocean/ia/IA.java:14: trailing whitespace.
+      - possibly there is a NEED for both variations (as suggested by drew): an asynchronous (queued) and synchronous (not)
src/main/java/org/ecocean/ia/IA.java:15: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:16: trailing whitespace.
+ * simply entry point for: MediaAsset and Annotation???
src/main/java/org/ecocean/ia/IA.java:17: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/IA.java:18: trailing whitespace.
+package org.ecocean.ia;
src/main/java/org/ecocean/ia/IA.java:19: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:20: trailing whitespace.
+import java.io.PrintWriter;
src/main/java/org/ecocean/ia/IA.java:21: trailing whitespace.
+import java.util.ArrayList;
src/main/java/org/ecocean/ia/IA.java:22: trailing whitespace.
+import java.util.Arrays;
src/main/java/org/ecocean/ia/IA.java:23: trailing whitespace.
+import java.util.HashMap;
src/main/java/org/ecocean/ia/IA.java:24: trailing whitespace.
+import java.util.Iterator;
src/main/java/org/ecocean/ia/IA.java:25: trailing whitespace.
+import java.util.List;
src/main/java/org/ecocean/ia/IA.java:26: trailing whitespace.
+import java.util.Map;
src/main/java/org/ecocean/ia/IA.java:27: trailing whitespace.
+import java.util.Properties;
src/main/java/org/ecocean/ia/IA.java:28: trailing whitespace.
+import javax.servlet.http.HttpServletRequest;
src/main/java/org/ecocean/ia/IA.java:29: trailing whitespace.
+import javax.servlet.http.HttpServletResponse;
src/main/java/org/ecocean/ia/IA.java:30: trailing whitespace.
+import org.ecocean.Annotation;
src/main/java/org/ecocean/ia/IA.java:31: trailing whitespace.
+import org.ecocean.CommonConfiguration;
src/main/java/org/ecocean/ia/IA.java:32: trailing whitespace.
+import org.ecocean.Embedding;
src/main/java/org/ecocean/ia/IA.java:33: trailing whitespace.
+import org.ecocean.Encounter;
src/main/java/org/ecocean/ia/IA.java:34: trailing whitespace.
+import org.ecocean.identity.IBEISIA;
src/main/java/org/ecocean/ia/IA.java:35: trailing whitespace.
+import org.ecocean.IAJsonProperties;
src/main/java/org/ecocean/ia/IA.java:36: trailing whitespace.
+import org.ecocean.media.MediaAsset;
src/main/java/org/ecocean/ia/IA.java:37: trailing whitespace.
+import org.ecocean.media.MediaAssetFactory;
src/main/java/org/ecocean/ia/IA.java:38: trailing whitespace.
+import org.ecocean.servlet.ServletUtilities;
src/main/java/org/ecocean/ia/IA.java:39: trailing whitespace.
+import org.ecocean.shepherd.core.Shepherd;
src/main/java/org/ecocean/ia/IA.java:40: trailing whitespace.
+import org.ecocean.shepherd.core.ShepherdProperties;
src/main/java/org/ecocean/ia/IA.java:41: trailing whitespace.
+import org.ecocean.Taxonomy;
src/main/java/org/ecocean/ia/IA.java:42: trailing whitespace.
+import org.ecocean.Util;
src/main/java/org/ecocean/ia/IA.java:43: trailing whitespace.
+import org.json.JSONArray;
src/main/java/org/ecocean/ia/IA.java:44: trailing whitespace.
+import org.json.JSONObject;
src/main/java/org/ecocean/ia/IA.java:45: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:46: trailing whitespace.
+public class IA {
src/main/java/org/ecocean/ia/IA.java:47: trailing whitespace.
+    private static final String PROP_FILE = "IA.properties";
src/main/java/org/ecocean/ia/IA.java:48: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:49: trailing whitespace.
+    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
src/main/java/org/ecocean/ia/IA.java:50: trailing whitespace.
+        - these should be treated as different in that an IA framework might batch together the list in some way (i.e. difference between sending as
src/main/java/org/ecocean/ia/IA.java:51: trailing whitespace.
+           list vs iterating over list with intake(each element)
src/main/java/org/ecocean/ia/IA.java:52: trailing whitespace.
+        - you only get one task ID for the list/group, is this a bad idea?
src/main/java/org/ecocean/ia/IA.java:53: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/IA.java:54: trailing whitespace.
+    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
src/main/java/org/ecocean/ia/IA.java:55: trailing whitespace.
+        return intakeMediaAssets(myShepherd, new ArrayList<MediaAsset>(Arrays.asList(ma)));
src/main/java/org/ecocean/ia/IA.java:56: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:57: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:58: trailing whitespace.
+    // Annotations *may or may not* already be on an Encounter  #neverforget
src/main/java/org/ecocean/ia/IA.java:59: trailing whitespace.
+    public static Task intake(Shepherd myShepherd, Annotation ann) {
src/main/java/org/ecocean/ia/IA.java:60: trailing whitespace.
+        return intakeAnnotations(myShepherd, new ArrayList<Annotation>(Arrays.asList(ann)));
src/main/java/org/ecocean/ia/IA.java:61: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:62: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:63: trailing whitespace.
+/*  these have same erasure types so cant co-exist. :( another reason for a common baseclass.. sigh?
src/main/java/org/ecocean/ia/IA.java:64: trailing whitespace.
+    hence the overly-inclusive Object version below!
src/main/java/org/ecocean/ia/IA.java:65: trailing whitespace.
+    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/IA.java:66: trailing whitespace.
+        if ((mas == null) || (mas.size() < 1)) return null;
src/main/java/org/ecocean/ia/IA.java:67: trailing whitespace.
+        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:68: trailing whitespace.
+        return task;
src/main/java/org/ecocean/ia/IA.java:69: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:70: trailing whitespace.
+    public static Task intake(Shepherd myShepherd, List<Annotation> anns) {
src/main/java/org/ecocean/ia/IA.java:71: trailing whitespace.
+        if ((anns == null) || (anns.size() < 1)) return null;
src/main/java/org/ecocean/ia/IA.java:72: trailing whitespace.
+        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:73: trailing whitespace.
+        return task;
src/main/java/org/ecocean/ia/IA.java:74: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:75: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/IA.java:76: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:77: trailing whitespace.
+    // i think objects ingested here must(?) be persisted (and committed), as we have to assume (or we know)
src/main/java/org/ecocean/ia/IA.java:78: trailing whitespace.
+    // that these processes will use queues which operate in different (Shepherd) threads and will thus try
src/main/java/org/ecocean/ia/IA.java:79: trailing whitespace.
+    // to find the objects via the db.  :/
src/main/java/org/ecocean/ia/IA.java:80: trailing whitespace.
+    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
src/main/java/org/ecocean/ia/IA.java:81: trailing whitespace.
+    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/IA.java:82: trailing whitespace.
+        return intakeMediaAssets(myShepherd, mas, null);
src/main/java/org/ecocean/ia/IA.java:83: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:84: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:85: trailing whitespace.
+    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:86: trailing whitespace.
+        Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:87: trailing whitespace.
+        List<List<MediaAsset> > assetsBySpecies = binAssetsBySpecies(mas, myShepherd);
src/main/java/org/ecocean/ia/IA.java:88: trailing whitespace.
+        int numSpecies = assetsBySpecies.size();
src/main/java/org/ecocean/ia/IA.java:89: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:90: trailing whitespace.
+        // System.out.println("IA.java.numSpecies="+numSpecies);
src/main/java/org/ecocean/ia/IA.java:91: trailing whitespace.
+        // in one-species case we don't need to create an extra layer of tasks
src/main/java/org/ecocean/ia/IA.java:92: trailing whitespace.
+        if (numSpecies == 1)
src/main/java/org/ecocean/ia/IA.java:93: trailing whitespace.
+            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
src/main/java/org/ecocean/ia/IA.java:94: trailing whitespace.
+        // in multi-species case we make sure we have a parent task and add each species task as a child
src/main/java/org/ecocean/ia/IA.java:95: trailing whitespace.
+        if (parentTask == null) parentTask = new Task();
src/main/java/org/ecocean/ia/IA.java:96: trailing whitespace.
+        for (List<MediaAsset> masOneSpecies : assetsBySpecies) {
src/main/java/org/ecocean/ia/IA.java:97: trailing whitespace.
+            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
src/main/java/org/ecocean/ia/IA.java:98: trailing whitespace.
+            parentTask.addChild(thisTask);
src/main/java/org/ecocean/ia/IA.java:99: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:100: trailing whitespace.
+        return parentTask;
src/main/java/org/ecocean/ia/IA.java:101: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:102: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:103: trailing whitespace.
+    public static List<List<MediaAsset> > binAssetsBySpecies(List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:104: trailing whitespace.
+        Shepherd myShepherd) {
src/main/java/org/ecocean/ia/IA.java:105: trailing whitespace.
+        Map<String, List<MediaAsset> > assetsBySpecies = new HashMap<String, List<MediaAsset> >();
src/main/java/org/ecocean/ia/IA.java:106: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:107: trailing whitespace.
+        for (MediaAsset ma : mas) {
src/main/java/org/ecocean/ia/IA.java:108: trailing whitespace.
+            Taxonomy taxy = ma.getTaxonomy(myShepherd);
src/main/java/org/ecocean/ia/IA.java:109: trailing whitespace.
+            String scientificName = "null";
src/main/java/org/ecocean/ia/IA.java:110: trailing whitespace.
+            if (taxy != null && taxy.getScientificName() != null)
src/main/java/org/ecocean/ia/IA.java:111: trailing whitespace.
+                scientificName = taxy.getScientificName();
src/main/java/org/ecocean/ia/IA.java:112: trailing whitespace.
+            // System.out.println("     MA ID "+ma.getId()+" has taxy "+scientificName);
src/main/java/org/ecocean/ia/IA.java:113: trailing whitespace.
+            if (!assetsBySpecies.containsKey(scientificName))
src/main/java/org/ecocean/ia/IA.java:114: trailing whitespace.
+                assetsBySpecies.put(scientificName, new ArrayList<MediaAsset>());
src/main/java/org/ecocean/ia/IA.java:115: trailing whitespace.
+            assetsBySpecies.get(scientificName).add(ma);
src/main/java/org/ecocean/ia/IA.java:116: trailing whitespace.
+            // System.out.println("       Taxy size: "+assetsBySpecies.get(scientificName).size());
src/main/java/org/ecocean/ia/IA.java:117: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:118: trailing whitespace.
+        return new ArrayList<List<MediaAsset> >(assetsBySpecies.values());
src/main/java/org/ecocean/ia/IA.java:119: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:120: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:121: trailing whitespace.
+    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:122: trailing whitespace.
+        final Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:123: trailing whitespace.
+        if ((mas == null) || (mas.size() < 1)) return null;
src/main/java/org/ecocean/ia/IA.java:124: trailing whitespace.
+        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
src/main/java/org/ecocean/ia/IA.java:125: trailing whitespace.
+        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
src/main/java/org/ecocean/ia/IA.java:126: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:127: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:128: trailing whitespace.
+    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:129: trailing whitespace.
+        Taxonomy taxy, final Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:130: trailing whitespace.
+        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
src/main/java/org/ecocean/ia/IA.java:131: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:132: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:133: trailing whitespace.
+    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:134: trailing whitespace.
+        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
src/main/java/org/ecocean/ia/IA.java:135: trailing whitespace.
+        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
src/main/java/org/ecocean/ia/IA.java:136: trailing whitespace.
+            " media assets:");
src/main/java/org/ecocean/ia/IA.java:137: trailing whitespace.
+        handleMissingAcmids(mas, myShepherd);
src/main/java/org/ecocean/ia/IA.java:138: trailing whitespace.
+        for (MediaAsset ma : mas) {
src/main/java/org/ecocean/ia/IA.java:139: trailing whitespace.
+            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
src/main/java/org/ecocean/ia/IA.java:140: trailing whitespace.
+            System.out.println("acmid is: " + ma.getAcmId());
src/main/java/org/ecocean/ia/IA.java:141: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:142: trailing whitespace.
+        JSONArray maArr = new JSONArray();
src/main/java/org/ecocean/ia/IA.java:143: trailing whitespace.
+        for (MediaAsset ma : mas) {
src/main/java/org/ecocean/ia/IA.java:144: trailing whitespace.
+            maArr.put(ma.getId());
src/main/java/org/ecocean/ia/IA.java:145: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:146: trailing whitespace.
+        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());
src/main/java/org/ecocean/ia/IA.java:147: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:148: trailing whitespace.
+        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:149: trailing whitespace.
+        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:150: trailing whitespace.
+        topTask.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:151: trailing whitespace.
+        myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:152: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:153: trailing whitespace.
+        String context = myShepherd.getContext();
src/main/java/org/ecocean/ia/IA.java:154: trailing whitespace.
+        String baseUrl = getBaseURL(context);
src/main/java/org/ecocean/ia/IA.java:155: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:156: trailing whitespace.
+        // Ia configs are keyed off taxonomies
src/main/java/org/ecocean/ia/IA.java:157: trailing whitespace.
+        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
src/main/java/org/ecocean/ia/IA.java:158: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:159: trailing whitespace.
+        // Migration plan v2 §commit #10b: routing reroute.
src/main/java/org/ecocean/ia/IA.java:160: trailing whitespace.
+        // If the species' _id_conf.default.pipeline_root is "vector" AND
src/main/java/org/ecocean/ia/IA.java:161: trailing whitespace.
+        // _mlservice_conf is configured, route per-asset through the
src/main/java/org/ecocean/ia/IA.java:162: trailing whitespace.
+        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
src/main/java/org/ecocean/ia/IA.java:163: trailing whitespace.
+        // WBIA path below — production deployments without _mlservice_conf
src/main/java/org/ecocean/ia/IA.java:164: trailing whitespace.
+        // see no behavior change at all.
src/main/java/org/ecocean/ia/IA.java:165: trailing whitespace.
+        //
src/main/java/org/ecocean/ia/IA.java:166: trailing whitespace.
+        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
src/main/java/org/ecocean/ia/IA.java:167: trailing whitespace.
+        // child finalization is local; no first-finisher-wins. The topTask
src/main/java/org/ecocean/ia/IA.java:168: trailing whitespace.
+        // remains as the aggregator for the caller contract (and so legacy
src/main/java/org/ecocean/ia/IA.java:169: trailing whitespace.
+        // summary code that reads topTask.objectMediaAssets keeps working).
src/main/java/org/ecocean/ia/IA.java:170: trailing whitespace.
+        if (iaConfig != null && taxy != null &&
src/main/java/org/ecocean/ia/IA.java:171: trailing whitespace.
+            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
src/main/java/org/ecocean/ia/IA.java:172: trailing whitespace.
+            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
src/main/java/org/ecocean/ia/IA.java:173: trailing whitespace.
+                context, baseUrl);
src/main/java/org/ecocean/ia/IA.java:174: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:175: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:176: trailing whitespace.
+        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
src/main/java/org/ecocean/ia/IA.java:177: trailing whitespace.
+        JSONObject dj = new JSONObject();
src/main/java/org/ecocean/ia/IA.java:178: trailing whitespace.
+        dj.put("mediaAssetIds", maArr);
src/main/java/org/ecocean/ia/IA.java:179: trailing whitespace.
+        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
src/main/java/org/ecocean/ia/IA.java:180: trailing whitespace.
+        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
src/main/java/org/ecocean/ia/IA.java:181: trailing whitespace.
+        Boolean[] sent = new Boolean[numDetectAlgos];
src/main/java/org/ecocean/ia/IA.java:182: trailing whitespace.
+        for (int i = 0; i < numDetectAlgos; i++) {
src/main/java/org/ecocean/ia/IA.java:183: trailing whitespace.
+            // task for this job (only create new (child) tasks if multiple detect algos)
src/main/java/org/ecocean/ia/IA.java:184: trailing whitespace.
+            Task task = (numDetectAlgos == 1) ? topTask : new Task();
src/main/java/org/ecocean/ia/IA.java:185: trailing whitespace.
+            task.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:186: trailing whitespace.
+            task.setParameters(topTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:187: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:188: trailing whitespace.
+            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
src/main/java/org/ecocean/ia/IA.java:189: trailing whitespace.
+            task.addParameter("detectArgs", detectArgs);
src/main/java/org/ecocean/ia/IA.java:190: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:191: trailing whitespace.
+            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
src/main/java/org/ecocean/ia/IA.java:192: trailing whitespace.
+            task.addParameter("__detect_url", detectionUrl);
src/main/java/org/ecocean/ia/IA.java:193: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:194: trailing whitespace.
+            JSONObject qjob = new JSONObject();
src/main/java/org/ecocean/ia/IA.java:195: trailing whitespace.
+            qjob.put("detect", dj);
src/main/java/org/ecocean/ia/IA.java:196: trailing whitespace.
+            qjob.put("__detect_args", detectArgs);
src/main/java/org/ecocean/ia/IA.java:197: trailing whitespace.
+            qjob.put("__detect_url", detectionUrl);
src/main/java/org/ecocean/ia/IA.java:198: trailing whitespace.
+            // task is queued here
src/main/java/org/ecocean/ia/IA.java:199: trailing whitespace.
+            qjob.put("taskId", topTask.getId());
src/main/java/org/ecocean/ia/IA.java:200: trailing whitespace.
+            qjob.put("__context", context);
src/main/java/org/ecocean/ia/IA.java:201: trailing whitespace.
+            qjob.put("__baseUrl", baseUrl);
src/main/java/org/ecocean/ia/IA.java:202: trailing whitespace.
+            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
src/main/java/org/ecocean/ia/IA.java:203: trailing whitespace.
+            if (tweetAssetId != -1) {
src/main/java/org/ecocean/ia/IA.java:204: trailing whitespace.
+                qjob.put("tweetAssetId", tweetAssetId);
src/main/java/org/ecocean/ia/IA.java:205: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:206: trailing whitespace.
+            System.out.println(
src/main/java/org/ecocean/ia/IA.java:207: trailing whitespace.
+                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
src/main/java/org/ecocean/ia/IA.java:208: trailing whitespace.
+            sent[i] = false;
src/main/java/org/ecocean/ia/IA.java:209: trailing whitespace.
+            try {
src/main/java/org/ecocean/ia/IA.java:210: trailing whitespace.
+                // job is queued here
src/main/java/org/ecocean/ia/IA.java:211: trailing whitespace.
+                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
src/main/java/org/ecocean/ia/IA.java:212: trailing whitespace.
+                    qjob.toString());
src/main/java/org/ecocean/ia/IA.java:213: trailing whitespace.
+            } catch (java.io.IOException iox) {
src/main/java/org/ecocean/ia/IA.java:214: trailing whitespace.
+                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
src/main/java/org/ecocean/ia/IA.java:215: trailing whitespace.
+                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
src/main/java/org/ecocean/ia/IA.java:216: trailing whitespace.
+                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
src/main/java/org/ecocean/ia/IA.java:217: trailing whitespace.
+                    iox.toString());
src/main/java/org/ecocean/ia/IA.java:218: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:219: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:220: trailing whitespace.
+        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
src/main/java/org/ecocean/ia/IA.java:221: trailing whitespace.
+            " assets; queued? = " + sent + "; " + topTask);
src/main/java/org/ecocean/ia/IA.java:222: trailing whitespace.
+        return topTask;
src/main/java/org/ecocean/ia/IA.java:223: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:224: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:225: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/IA.java:226: trailing whitespace.
+     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
src/main/java/org/ecocean/ia/IA.java:227: trailing whitespace.
+     * vector pipeline. Each MediaAsset gets its own child Task under
src/main/java/org/ecocean/ia/IA.java:228: trailing whitespace.
+     * topTask; each emits a {@code mlServiceV2:true} payload to the
src/main/java/org/ecocean/ia/IA.java:229: trailing whitespace.
+     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
src/main/java/org/ecocean/ia/IA.java:230: trailing whitespace.
+     * picks them up via the IAGateway dispatcher (commit #10a).
src/main/java/org/ecocean/ia/IA.java:231: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/IA.java:232: trailing whitespace.
+     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
src/main/java/org/ecocean/ia/IA.java:233: trailing whitespace.
+     * topTask. The topTask itself remains as the aggregator that holds the
src/main/java/org/ecocean/ia/IA.java:234: trailing whitespace.
+     * full MediaAsset list for caller-side summary code.</p>
src/main/java/org/ecocean/ia/IA.java:235: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/IA.java:236: trailing whitespace.
+     * <p>encounterId is derived best-effort from the MediaAsset's existing
src/main/java/org/ecocean/ia/IA.java:237: trailing whitespace.
+     * trivial annotation (every Encounter.addMediaAsset call creates one).
src/main/java/org/ecocean/ia/IA.java:238: trailing whitespace.
+     * If null, MlServiceProcessor persists annotations without explicit
src/main/java/org/ecocean/ia/IA.java:239: trailing whitespace.
+     * Encounter linkage and downstream MediaAsset.assignEncounters handles
src/main/java/org/ecocean/ia/IA.java:240: trailing whitespace.
+     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
src/main/java/org/ecocean/ia/IA.java:241: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/IA.java:242: trailing whitespace.
+    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
src/main/java/org/ecocean/ia/IA.java:243: trailing whitespace.
+        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
src/main/java/org/ecocean/ia/IA.java:244: trailing whitespace.
+        int queued = 0;
src/main/java/org/ecocean/ia/IA.java:245: trailing whitespace.
+        for (MediaAsset ma : mas) {
src/main/java/org/ecocean/ia/IA.java:246: trailing whitespace.
+            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
src/main/java/org/ecocean/ia/IA.java:247: trailing whitespace.
+                queued++;
src/main/java/org/ecocean/ia/IA.java:248: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:249: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:250: trailing whitespace.
+        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
src/main/java/org/ecocean/ia/IA.java:251: trailing whitespace.
+            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
src/main/java/org/ecocean/ia/IA.java:252: trailing whitespace.
+        return topTask;
src/main/java/org/ecocean/ia/IA.java:253: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:254: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:255: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/IA.java:256: trailing whitespace.
+     * Build and enqueue one v2 ml-service job for a single MediaAsset.
src/main/java/org/ecocean/ia/IA.java:257: trailing whitespace.
+     * Returns {@code true} iff the FileQueue write succeeded.
src/main/java/org/ecocean/ia/IA.java:258: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/IA.java:259: trailing whitespace.
+     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
src/main/java/org/ecocean/ia/IA.java:260: trailing whitespace.
+     * normal intake path) and the startup stale-mlservice reconciler in
src/main/java/org/ecocean/ia/IA.java:261: trailing whitespace.
+     * {@code StartupWildbook}. The reconciler relies on the boolean
src/main/java/org/ecocean/ia/IA.java:262: trailing whitespace.
+     * return to decide whether to commit accompanying state changes; the
src/main/java/org/ecocean/ia/IA.java:263: trailing whitespace.
+     * normal intake path tolerates the swallowed-failure behavior.</p>
src/main/java/org/ecocean/ia/IA.java:264: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/IA.java:265: trailing whitespace.
+     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
src/main/java/org/ecocean/ia/IA.java:266: trailing whitespace.
+     * internally commits/reopens the transaction, so the child Task row
src/main/java/org/ecocean/ia/IA.java:267: trailing whitespace.
+     * is persisted before this method enqueues. On enqueue failure the
src/main/java/org/ecocean/ia/IA.java:268: trailing whitespace.
+     * child Task remains in the DB as an orphan — there is no queued
src/main/java/org/ecocean/ia/IA.java:269: trailing whitespace.
+     * job that will ever drive it. The orphan IS still discoverable
src/main/java/org/ecocean/ia/IA.java:270: trailing whitespace.
+     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
src/main/java/org/ecocean/ia/IA.java:271: trailing whitespace.
+     * the task references the MediaAsset through objectMediaAssets),
src/main/java/org/ecocean/ia/IA.java:272: trailing whitespace.
+     * so it may surface in operator-facing task listings until cleaned
src/main/java/org/ecocean/ia/IA.java:273: trailing whitespace.
+     * up by an out-of-band path. Callers that need cleanup should
src/main/java/org/ecocean/ia/IA.java:274: trailing whitespace.
+     * delete the orphan explicitly; the default posture here is to
src/main/java/org/ecocean/ia/IA.java:275: trailing whitespace.
+     * accept it since FileQueue write failures are rare.</p>
src/main/java/org/ecocean/ia/IA.java:276: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/IA.java:277: trailing whitespace.
+     * <p>If {@code topTask} is null a fresh root task is created inside
src/main/java/org/ecocean/ia/IA.java:278: trailing whitespace.
+     * this method. This matches the reconciler's use case where there is
src/main/java/org/ecocean/ia/IA.java:279: trailing whitespace.
+     * no caller-side aggregator umbrella.</p>
src/main/java/org/ecocean/ia/IA.java:280: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/IA.java:281: trailing whitespace.
+    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
src/main/java/org/ecocean/ia/IA.java:282: trailing whitespace.
+        Taxonomy taxy, Task topTask, String context, String baseUrl) {
src/main/java/org/ecocean/ia/IA.java:283: trailing whitespace.
+        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
src/main/java/org/ecocean/ia/IA.java:284: trailing whitespace.
+        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/IA.java:285: trailing whitespace.
+        singleton.add(ma);
src/main/java/org/ecocean/ia/IA.java:286: trailing whitespace.
+        childTask.setObjectMediaAssets(singleton);
src/main/java/org/ecocean/ia/IA.java:287: trailing whitespace.
+        myShepherd.storeNewTask(childTask);
src/main/java/org/ecocean/ia/IA.java:288: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:289: trailing whitespace.
+        // Best-effort encounterId via existing annotations on the MA.
src/main/java/org/ecocean/ia/IA.java:290: trailing whitespace.
+        String encounterId = null;
src/main/java/org/ecocean/ia/IA.java:291: trailing whitespace.
+        ArrayList<Annotation> existing = ma.getAnnotations();
src/main/java/org/ecocean/ia/IA.java:292: trailing whitespace.
+        if (existing != null) {
src/main/java/org/ecocean/ia/IA.java:293: trailing whitespace.
+            for (Annotation a : existing) {
src/main/java/org/ecocean/ia/IA.java:294: trailing whitespace.
+                Encounter enc = a.findEncounter(myShepherd);
src/main/java/org/ecocean/ia/IA.java:295: trailing whitespace.
+                if (enc != null) {
src/main/java/org/ecocean/ia/IA.java:296: trailing whitespace.
+                    encounterId = enc.getId();
src/main/java/org/ecocean/ia/IA.java:297: trailing whitespace.
+                    break;
src/main/java/org/ecocean/ia/IA.java:298: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:299: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:300: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:301: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:302: trailing whitespace.
+        JSONObject qjob = new JSONObject();
src/main/java/org/ecocean/ia/IA.java:303: trailing whitespace.
+        qjob.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/IA.java:304: trailing whitespace.
+        qjob.put("mediaAssetId", ma.getId());
src/main/java/org/ecocean/ia/IA.java:305: trailing whitespace.
+        qjob.put("taxonomyString", taxy.getScientificName());
src/main/java/org/ecocean/ia/IA.java:306: trailing whitespace.
+        qjob.put("taskId", childTask.getId());
src/main/java/org/ecocean/ia/IA.java:307: trailing whitespace.
+        qjob.put("__context", context);
src/main/java/org/ecocean/ia/IA.java:308: trailing whitespace.
+        qjob.put("__baseUrl", baseUrl);
src/main/java/org/ecocean/ia/IA.java:309: trailing whitespace.
+        if (Util.stringExists(encounterId)) {
src/main/java/org/ecocean/ia/IA.java:310: trailing whitespace.
+            qjob.put("encounterId", encounterId);
src/main/java/org/ecocean/ia/IA.java:311: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:312: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:313: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/IA.java:314: trailing whitespace.
+            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
src/main/java/org/ecocean/ia/IA.java:315: trailing whitespace.
+        } catch (java.io.IOException iox) {
src/main/java/org/ecocean/ia/IA.java:316: trailing whitespace.
+            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
src/main/java/org/ecocean/ia/IA.java:317: trailing whitespace.
+                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
src/main/java/org/ecocean/ia/IA.java:318: trailing whitespace.
+            return false;
src/main/java/org/ecocean/ia/IA.java:319: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:320: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:321: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:322: trailing whitespace.
+    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/IA.java:323: trailing whitespace.
+        int count = 0;
src/main/java/org/ecocean/ia/IA.java:324: trailing whitespace.
+        int stopAfter = 200000;
src/main/java/org/ecocean/ia/IA.java:325: trailing whitespace.
+        int batchThreshold = 50;
src/main/java/org/ecocean/ia/IA.java:326: trailing whitespace.
+        int batchesSoFar = 0;
src/main/java/org/ecocean/ia/IA.java:327: trailing whitespace.
+        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/IA.java:328: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:329: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/IA.java:330: trailing whitespace.
+            for (MediaAsset ma : mediaAssets) {
src/main/java/org/ecocean/ia/IA.java:331: trailing whitespace.
+                count++;
src/main/java/org/ecocean/ia/IA.java:332: trailing whitespace.
+                if (count > stopAfter) {
src/main/java/org/ecocean/ia/IA.java:333: trailing whitespace.
+                    break;
src/main/java/org/ecocean/ia/IA.java:334: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:335: trailing whitespace.
+                if (ma != null && !ma.hasAcmId()) {
src/main/java/org/ecocean/ia/IA.java:336: trailing whitespace.
+                    assetsWithMissingAcmids.add(ma);
src/main/java/org/ecocean/ia/IA.java:337: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:338: trailing whitespace.
+                if ((assetsWithMissingAcmids.size() >= batchThreshold) ||
src/main/java/org/ecocean/ia/IA.java:339: trailing whitespace.
+                    count == mediaAssets.size()) {
src/main/java/org/ecocean/ia/IA.java:340: trailing whitespace.
+                    if (assetsWithMissingAcmids.size() > 0) { // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this
src/main/java/org/ecocean/ia/IA.java:341: trailing whitespace.
+                        try {
src/main/java/org/ecocean/ia/IA.java:342: trailing whitespace.
+                            IBEISIA.sendMediaAssetsNew(assetsWithMissingAcmids,
src/main/java/org/ecocean/ia/IA.java:343: trailing whitespace.
+                                myShepherd.getContext());
src/main/java/org/ecocean/ia/IA.java:344: trailing whitespace.
+                        } catch (Exception e) {
src/main/java/org/ecocean/ia/IA.java:345: trailing whitespace.
+                            System.out.println(
src/main/java/org/ecocean/ia/IA.java:346: trailing whitespace.
+                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
src/main/java/org/ecocean/ia/IA.java:347: trailing whitespace.
+                            e.printStackTrace();
src/main/java/org/ecocean/ia/IA.java:348: trailing whitespace.
+                        }
src/main/java/org/ecocean/ia/IA.java:349: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/IA.java:350: trailing whitespace.
+                    batchesSoFar++;
src/main/java/org/ecocean/ia/IA.java:351: trailing whitespace.
+                    assetsWithMissingAcmids = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/IA.java:352: trailing whitespace.
+                    myShepherd.updateDBTransaction();
src/main/java/org/ecocean/ia/IA.java:353: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:354: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:355: trailing whitespace.
+        } catch (Exception e) {
src/main/java/org/ecocean/ia/IA.java:356: trailing whitespace.
+            System.out.println("Error in handleMissingAcmids in IA.java");
src/main/java/org/ecocean/ia/IA.java:357: trailing whitespace.
+            e.printStackTrace();
src/main/java/org/ecocean/ia/IA.java:358: trailing whitespace.
+            myShepherd.rollbackDBTransaction();
src/main/java/org/ecocean/ia/IA.java:359: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:360: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:361: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:362: trailing whitespace.
+    // similar behavior to above: basically fake /ia api call, but via queue
src/main/java/org/ecocean/ia/IA.java:363: trailing whitespace.
+    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
src/main/java/org/ecocean/ia/IA.java:364: trailing whitespace.
+    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
src/main/java/org/ecocean/ia/IA.java:365: trailing whitespace.
+        return intakeAnnotations(myShepherd, anns, null, false);
src/main/java/org/ecocean/ia/IA.java:366: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:367: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:368: trailing whitespace.
+    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
src/main/java/org/ecocean/ia/IA.java:369: trailing whitespace.
+        final Task parentTask, boolean fastlane) {
src/main/java/org/ecocean/ia/IA.java:370: trailing whitespace.
+        // List<List<Annotation>> annses = binAnnotsByIaClass(anns);
src/main/java/org/ecocean/ia/IA.java:371: trailing whitespace.
+        //// slightly complicated bc we need to create child tasks only if there are multiple iaClasses
src/main/java/org/ecocean/ia/IA.java:372: trailing whitespace.
+        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
src/main/java/org/ecocean/ia/IA.java:373: trailing whitespace.
+        //// here we make child tasks
src/main/java/org/ecocean/ia/IA.java:374: trailing whitespace.
+        // Task topTask = (parentTask==null) ? new Task() : parentTask;
src/main/java/org/ecocean/ia/IA.java:375: trailing whitespace.
+        // for (List<Annotation> annsOneIaClass: annses) {
src/main/java/org/ecocean/ia/IA.java:376: trailing whitespace.
+        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
src/main/java/org/ecocean/ia/IA.java:377: trailing whitespace.
+        // }
src/main/java/org/ecocean/ia/IA.java:378: trailing whitespace.
+        // return topTask;
src/main/java/org/ecocean/ia/IA.java:379: trailing whitespace.
+        // }
src/main/java/org/ecocean/ia/IA.java:380: trailing whitespace.
+        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:381: trailing whitespace.
+        // System.out.println("Starting intakeAnnotations");
src/main/java/org/ecocean/ia/IA.java:382: trailing whitespace.
+        if ((anns == null) || (anns.size() < 1)) return null;
src/main/java/org/ecocean/ia/IA.java:383: trailing whitespace.
+        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:384: trailing whitespace.
+        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:385: trailing whitespace.
+        topTask.setObjectAnnotations(anns);
src/main/java/org/ecocean/ia/IA.java:386: trailing whitespace.
+        String context = myShepherd.getContext();
src/main/java/org/ecocean/ia/IA.java:387: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:388: trailing whitespace.
+        /*
src/main/java/org/ecocean/ia/IA.java:389: trailing whitespace.
+            what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed for this we use
src/main/java/org/ecocean/ia/IA.java:390: trailing whitespace.
+               IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than one we need to make a set of subtasks
src/main/java/org/ecocean/ia/IA.java:391: trailing whitespace.
+         */
src/main/java/org/ecocean/ia/IA.java:392: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:393: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/IA.java:394: trailing whitespace.
+        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
src/main/java/org/ecocean/ia/IA.java:395: trailing whitespace.
+           List<JSONObject> opts = null;
src/main/java/org/ecocean/ia/IA.java:396: trailing whitespace.
+        // below gets it working for dolphins but can be generalized easily from IA.properties String inferredIaClass =
src/main/java/org/ecocean/ia/IA.java:397: trailing whitespace.
+           IBEISIA.inferIaClass(anns.get(0), myShepherd);
src/main/java/org/ecocean/ia/IA.java:398: trailing whitespace.
+        String bottlenose = "dolphin_bottlenose_fin";
src/main/java/org/ecocean/ia/IA.java:399: trailing whitespace.
+        if (bottlenose.equals(iaClass) || bottlenose.equals(inferredIaClass)) {
src/main/java/org/ecocean/ia/IA.java:400: trailing whitespace.
+            System.out.println("IA.java is sending a Tursiops truncatus job");
src/main/java/org/ecocean/ia/IA.java:401: trailing whitespace.
+            opts = IBEISIA.identOpts(context, bottlenose);
src/main/java/org/ecocean/ia/IA.java:402: trailing whitespace.
+        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes opts = IBEISIA.identOpts(context);
src/main/java/org/ecocean/ia/IA.java:403: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:404: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/IA.java:405: trailing whitespace.
+        // List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
src/main/java/org/ecocean/ia/IA.java:406: trailing whitespace.
+        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
src/main/java/org/ecocean/ia/IA.java:407: trailing whitespace.
+        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
src/main/java/org/ecocean/ia/IA.java:408: trailing whitespace.
+        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
src/main/java/org/ecocean/ia/IA.java:409: trailing whitespace.
+            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
src/main/java/org/ecocean/ia/IA.java:410: trailing whitespace.
+            // now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
src/main/java/org/ecocean/ia/IA.java:411: trailing whitespace.
+            if (opts != null) {
src/main/java/org/ecocean/ia/IA.java:412: trailing whitespace.
+                Iterator<JSONObject> itr = opts.iterator();
src/main/java/org/ecocean/ia/IA.java:413: trailing whitespace.
+                while (itr.hasNext()) {
src/main/java/org/ecocean/ia/IA.java:414: trailing whitespace.
+                    if (!itr.next().optBoolean("default", true)) itr.remove();
src/main/java/org/ecocean/ia/IA.java:415: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:416: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:417: trailing whitespace.
+            System.out.println("identOpts: " + opts);
src/main/java/org/ecocean/ia/IA.java:418: trailing whitespace.
+            List<Task> tasks = new ArrayList<Task>();
src/main/java/org/ecocean/ia/IA.java:419: trailing whitespace.
+            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
src/main/java/org/ecocean/ia/IA.java:420: trailing whitespace.
+            if (parentTask != null && parentTask.getParameters() != null) {
src/main/java/org/ecocean/ia/IA.java:421: trailing whitespace.
+                newTaskParams = parentTask.getParameters();
src/main/java/org/ecocean/ia/IA.java:422: trailing whitespace.
+                System.out.println("newTaskParams: " + newTaskParams.toString());
src/main/java/org/ecocean/ia/IA.java:423: trailing whitespace.
+                if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
src/main/java/org/ecocean/ia/IA.java:424: trailing whitespace.
+                    JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
src/main/java/org/ecocean/ia/IA.java:425: trailing whitespace.
+                    System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
src/main/java/org/ecocean/ia/IA.java:426: trailing whitespace.
+                    ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
src/main/java/org/ecocean/ia/IA.java:427: trailing whitespace.
+                    int maLength = matchingAlgorithms.length();
src/main/java/org/ecocean/ia/IA.java:428: trailing whitespace.
+                    for (int y = 0; y < maLength; y++) {
src/main/java/org/ecocean/ia/IA.java:429: trailing whitespace.
+                        newOpts.add(matchingAlgorithms.getJSONObject(y));
src/main/java/org/ecocean/ia/IA.java:430: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/IA.java:431: trailing whitespace.
+                    System.out.println("matchingAlgorithms2: " + newOpts.toString());
src/main/java/org/ecocean/ia/IA.java:432: trailing whitespace.
+                    if (newOpts.size() > 0) {
src/main/java/org/ecocean/ia/IA.java:433: trailing whitespace.
+                        opts = newOpts;
src/main/java/org/ecocean/ia/IA.java:434: trailing whitespace.
+                        System.out.println("Swapping opts for newOpts!!");
src/main/java/org/ecocean/ia/IA.java:435: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/IA.java:436: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:437: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:438: trailing whitespace.
+            if ((opts == null) || (opts.size() < 1)) continue; // no ID for this iaClass.
src/main/java/org/ecocean/ia/IA.java:439: trailing whitespace.
+            // just one IA class, one algorithm case
src/main/java/org/ecocean/ia/IA.java:440: trailing whitespace.
+            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
src/main/java/org/ecocean/ia/IA.java:441: trailing whitespace.
+                newTaskParams.put("ibeis.identification",
src/main/java/org/ecocean/ia/IA.java:442: trailing whitespace.
+                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
src/main/java/org/ecocean/ia/IA.java:443: trailing whitespace.
+                topTask.setParameters(newTaskParams);
src/main/java/org/ecocean/ia/IA.java:444: trailing whitespace.
+                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
src/main/java/org/ecocean/ia/IA.java:445: trailing whitespace.
+            } else {
src/main/java/org/ecocean/ia/IA.java:446: trailing whitespace.
+                for (int i = 0; i < opts.size(); i++) {
src/main/java/org/ecocean/ia/IA.java:447: trailing whitespace.
+                    Task t = new Task();
src/main/java/org/ecocean/ia/IA.java:448: trailing whitespace.
+                    t.setObjectAnnotations(annsOneIAClass);
src/main/java/org/ecocean/ia/IA.java:449: trailing whitespace.
+                    newTaskParams.put("ibeis.identification",
src/main/java/org/ecocean/ia/IA.java:450: trailing whitespace.
+                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
src/main/java/org/ecocean/ia/IA.java:451: trailing whitespace.
+                    t.setParameters(newTaskParams);
src/main/java/org/ecocean/ia/IA.java:452: trailing whitespace.
+                    topTask.addChild(t);
src/main/java/org/ecocean/ia/IA.java:453: trailing whitespace.
+                    tasks.add(t);
src/main/java/org/ecocean/ia/IA.java:454: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:455: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:456: trailing whitespace.
+            newTaskParams.put("fastlane", fastlane);
src/main/java/org/ecocean/ia/IA.java:457: trailing whitespace.
+            if (fastlane) newTaskParams.put("lane", "fast");
src/main/java/org/ecocean/ia/IA.java:458: trailing whitespace.
+            myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:459: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:460: trailing whitespace.
+            // these are re-used in every task
src/main/java/org/ecocean/ia/IA.java:461: trailing whitespace.
+            JSONArray annArr = new JSONArray();
src/main/java/org/ecocean/ia/IA.java:462: trailing whitespace.
+            for (Annotation ann : annsOneIAClass) {
src/main/java/org/ecocean/ia/IA.java:463: trailing whitespace.
+                annArr.put(ann.getId());
src/main/java/org/ecocean/ia/IA.java:464: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:465: trailing whitespace.
+            JSONObject aj = new JSONObject();
src/main/java/org/ecocean/ia/IA.java:466: trailing whitespace.
+            aj.put("annotationIds", annArr);
src/main/java/org/ecocean/ia/IA.java:467: trailing whitespace.
+            String baseUrl = getBaseURL(context);
src/main/java/org/ecocean/ia/IA.java:468: trailing whitespace.
+            for (int i = 0; i < opts.size(); i++) {
src/main/java/org/ecocean/ia/IA.java:469: trailing whitespace.
+                // if this is a vector-based matching option, this will just do the job and be done
src/main/java/org/ecocean/ia/IA.java:470: trailing whitespace.
+                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
src/main/java/org/ecocean/ia/IA.java:471: trailing whitespace.
+                JSONObject qjob = new JSONObject();
src/main/java/org/ecocean/ia/IA.java:472: trailing whitespace.
+                qjob.put("identify", aj);
src/main/java/org/ecocean/ia/IA.java:473: trailing whitespace.
+                qjob.put("taskId", tasks.get(i).getId());
src/main/java/org/ecocean/ia/IA.java:474: trailing whitespace.
+                qjob.put("__context", context);
src/main/java/org/ecocean/ia/IA.java:475: trailing whitespace.
+                qjob.put("__baseUrl", baseUrl);
src/main/java/org/ecocean/ia/IA.java:476: trailing whitespace.
+                if (opts.get(i) != null) qjob.put("opt", opts.get(i));
src/main/java/org/ecocean/ia/IA.java:477: trailing whitespace.
+                boolean sent = false;
src/main/java/org/ecocean/ia/IA.java:478: trailing whitespace.
+                try {
src/main/java/org/ecocean/ia/IA.java:479: trailing whitespace.
+                    if (fastlane) {
src/main/java/org/ecocean/ia/IA.java:480: trailing whitespace.
+                        // if fastlane and a smaller, bespoke request, get this into the faster queue
src/main/java/org/ecocean/ia/IA.java:481: trailing whitespace.
+                        qjob.put("fastlane", fastlane);
src/main/java/org/ecocean/ia/IA.java:482: trailing whitespace.
+                        qjob.put("lane", "fast");
src/main/java/org/ecocean/ia/IA.java:483: trailing whitespace.
+                        tasks.get(i).setQueueResumeMessage(qjob.toString());
src/main/java/org/ecocean/ia/IA.java:484: trailing whitespace.
+                        sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
src/main/java/org/ecocean/ia/IA.java:485: trailing whitespace.
+                            qjob.toString());
src/main/java/org/ecocean/ia/IA.java:486: trailing whitespace.
+                    } else {
src/main/java/org/ecocean/ia/IA.java:487: trailing whitespace.
+                        tasks.get(i).setQueueResumeMessage(qjob.toString());
src/main/java/org/ecocean/ia/IA.java:488: trailing whitespace.
+                        sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
src/main/java/org/ecocean/ia/IA.java:489: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/IA.java:490: trailing whitespace.
+                } catch (java.io.IOException iox) {
src/main/java/org/ecocean/ia/IA.java:491: trailing whitespace.
+                    System.out.println("ERROR[" + i +
src/main/java/org/ecocean/ia/IA.java:492: trailing whitespace.
+                        "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
src/main/java/org/ecocean/ia/IA.java:493: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:494: trailing whitespace.
+                System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " +
src/main/java/org/ecocean/ia/IA.java:495: trailing whitespace.
+                    annsOneIAClass.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
src/main/java/org/ecocean/ia/IA.java:496: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:497: trailing whitespace.
+        } // end for each iaClass
src/main/java/org/ecocean/ia/IA.java:498: trailing whitespace.
+        System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
src/main/java/org/ecocean/ia/IA.java:499: trailing whitespace.
+        return topTask;
src/main/java/org/ecocean/ia/IA.java:500: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:501: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:502: trailing whitespace.
+    public static List<List<Annotation> > binAnnotsByIaClass(List<Annotation> anns) {
src/main/java/org/ecocean/ia/IA.java:503: trailing whitespace.
+        System.out.println("binAnnotsByIaClass called on " + anns.size() + " annots");
src/main/java/org/ecocean/ia/IA.java:504: trailing whitespace.
+        Map<String, List<Annotation> > iaClassToAnns = new HashMap<String, List<Annotation> >();
src/main/java/org/ecocean/ia/IA.java:505: trailing whitespace.
+        for (Annotation ann : anns) {
src/main/java/org/ecocean/ia/IA.java:506: trailing whitespace.
+            String iaClass = ann.getIAClass();
src/main/java/org/ecocean/ia/IA.java:507: trailing whitespace.
+            if (iaClass == null) continue;
src/main/java/org/ecocean/ia/IA.java:508: trailing whitespace.
+            List<Annotation> iaClassList = iaClassToAnns.getOrDefault(iaClass,
src/main/java/org/ecocean/ia/IA.java:509: trailing whitespace.
+                new ArrayList<Annotation>());
src/main/java/org/ecocean/ia/IA.java:510: trailing whitespace.
+            iaClassList.add(ann);
src/main/java/org/ecocean/ia/IA.java:511: trailing whitespace.
+            iaClassToAnns.put(iaClass, iaClassList);
src/main/java/org/ecocean/ia/IA.java:512: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:513: trailing whitespace.
+        System.out.println("binAnnotsByIaClass binned them into " + iaClassToAnns.keySet().size() +
src/main/java/org/ecocean/ia/IA.java:514: trailing whitespace.
+            " bins: " + iaClassToAnns.keySet());
src/main/java/org/ecocean/ia/IA.java:515: trailing whitespace.
+        return new ArrayList<List<Annotation> >(iaClassToAnns.values());
src/main/java/org/ecocean/ia/IA.java:516: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:517: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:518: trailing whitespace.
+    // possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
src/main/java/org/ecocean/ia/IA.java:519: trailing whitespace.
+    // note: this is processed *from the queue* and as such does not have "output"
src/main/java/org/ecocean/ia/IA.java:520: trailing whitespace.
+    public static void handleRest(JSONObject jin) {
src/main/java/org/ecocean/ia/IA.java:521: trailing whitespace.
+        System.out.println("JIN JIN JIN: " + jin);
src/main/java/org/ecocean/ia/IA.java:522: trailing whitespace.
+        if (jin == null) return;
src/main/java/org/ecocean/ia/IA.java:523: trailing whitespace.
+        String context = jin.optString("__context", null);
src/main/java/org/ecocean/ia/IA.java:524: trailing whitespace.
+        if (context == null)
src/main/java/org/ecocean/ia/IA.java:525: trailing whitespace.
+            throw new RuntimeException("IA.handleRest(): passed data has no __context");
src/main/java/org/ecocean/ia/IA.java:526: trailing whitespace.
+        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/ia/IA.java:527: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:528: trailing whitespace.
+        // check if these should be directed through the fastlane
src/main/java/org/ecocean/ia/IA.java:529: trailing whitespace.
+        boolean fastlane = false;
src/main/java/org/ecocean/ia/IA.java:530: trailing whitespace.
+        if (jin.optBoolean("fastlane", false)) { fastlane = true; }
src/main/java/org/ecocean/ia/IA.java:531: trailing whitespace.
+        myShepherd.setAction("IA.handleRest");
src/main/java/org/ecocean/ia/IA.java:532: trailing whitespace.
+        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/ia/IA.java:533: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/IA.java:534: trailing whitespace.
+            String taskId = jin.optString("taskId", Util.generateUUID());
src/main/java/org/ecocean/ia/IA.java:535: trailing whitespace.
+            Task topTask = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/ia/IA.java:536: trailing whitespace.
+            if (topTask == null) topTask = new Task(taskId);
src/main/java/org/ecocean/ia/IA.java:537: trailing whitespace.
+            if (fastlane) topTask.addParameter("fastlane", true);
src/main/java/org/ecocean/ia/IA.java:538: trailing whitespace.
+            myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:539: trailing whitespace.
+            JSONObject opt = jin.optJSONObject("opt"); // should use this to decide how to branch differently than "default"
src/main/java/org/ecocean/ia/IA.java:540: trailing whitespace.
+            JSONArray mlist = jin.optJSONArray("mediaAssetIds");
src/main/java/org/ecocean/ia/IA.java:541: trailing whitespace.
+            if ((mlist != null) && (mlist.length() > 0)) {
src/main/java/org/ecocean/ia/IA.java:542: trailing whitespace.
+                System.out.println("MLIST: " + mlist);
src/main/java/org/ecocean/ia/IA.java:543: trailing whitespace.
+                List<MediaAsset> mas = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/IA.java:544: trailing whitespace.
+                for (int i = 0; i < mlist.length(); i++) {
src/main/java/org/ecocean/ia/IA.java:545: trailing whitespace.
+                    int mid = mlist.optInt(i, -1);
src/main/java/org/ecocean/ia/IA.java:546: trailing whitespace.
+                    if (mid < 1) continue;
src/main/java/org/ecocean/ia/IA.java:547: trailing whitespace.
+                    MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
src/main/java/org/ecocean/ia/IA.java:548: trailing whitespace.
+                    System.out.println(i + " -> " + ma);
src/main/java/org/ecocean/ia/IA.java:549: trailing whitespace.
+                    if (ma == null) continue;
src/main/java/org/ecocean/ia/IA.java:550: trailing whitespace.
+                    mas.add(ma);
src/main/java/org/ecocean/ia/IA.java:551: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:552: trailing whitespace.
+                Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
src/main/java/org/ecocean/ia/IA.java:553: trailing whitespace.
+                System.out.println("INFO: IA.handleRest() just intook MediaAssets as " + mtask +
src/main/java/org/ecocean/ia/IA.java:554: trailing whitespace.
+                    " for (parent) " + topTask);
src/main/java/org/ecocean/ia/IA.java:555: trailing whitespace.
+                topTask.addChild(mtask);
src/main/java/org/ecocean/ia/IA.java:556: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:557: trailing whitespace.
+            JSONArray alist = jin.optJSONArray("annotationIds");
src/main/java/org/ecocean/ia/IA.java:558: trailing whitespace.
+            if ((alist != null) && (alist.length() > 0)) {
src/main/java/org/ecocean/ia/IA.java:559: trailing whitespace.
+                List<Annotation> anns = new ArrayList<Annotation>();
src/main/java/org/ecocean/ia/IA.java:560: trailing whitespace.
+                for (int i = 0; i < alist.length(); i++) {
src/main/java/org/ecocean/ia/IA.java:561: trailing whitespace.
+                    String aid = alist.optString(i, null);
src/main/java/org/ecocean/ia/IA.java:562: trailing whitespace.
+                    if (aid == null) continue;
src/main/java/org/ecocean/ia/IA.java:563: trailing whitespace.
+                    Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
src/main/java/org/ecocean/ia/IA.java:564: trailing whitespace.
+                        myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
src/main/java/org/ecocean/ia/IA.java:565: trailing whitespace.
+                    if (ann == null) continue;
src/main/java/org/ecocean/ia/IA.java:566: trailing whitespace.
+                    anns.add(ann);
src/main/java/org/ecocean/ia/IA.java:567: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:568: trailing whitespace.
+                // okay, if we are sending another ID job from the hburger menu, the media asset needs to be added to your top level 'root' task,
src/main/java/org/ecocean/ia/IA.java:569: trailing whitespace.
+                // or else you will link to the original root task
src/main/java/org/ecocean/ia/IA.java:570: trailing whitespace.
+                List<MediaAsset> masForNewRoot = new ArrayList<>();
src/main/java/org/ecocean/ia/IA.java:571: trailing whitespace.
+                for (Annotation ann : anns) {
src/main/java/org/ecocean/ia/IA.java:572: trailing whitespace.
+                    MediaAsset ma = ann.getMediaAsset();
src/main/java/org/ecocean/ia/IA.java:573: trailing whitespace.
+                    if (ma != null && !masForNewRoot.contains(ma)) {
src/main/java/org/ecocean/ia/IA.java:574: trailing whitespace.
+                        masForNewRoot.add(ma);
src/main/java/org/ecocean/ia/IA.java:575: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/IA.java:576: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:577: trailing whitespace.
+                // i cant think of a scenario where we would get here and accidently double-add mas... but jic
src/main/java/org/ecocean/ia/IA.java:578: trailing whitespace.
+                for (MediaAsset ma : masForNewRoot) {
src/main/java/org/ecocean/ia/IA.java:579: trailing whitespace.
+                    if (!topTask.getObjectMediaAssets().contains(ma)) {
src/main/java/org/ecocean/ia/IA.java:580: trailing whitespace.
+                        topTask.addObject(ma);
src/main/java/org/ecocean/ia/IA.java:581: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/IA.java:582: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/IA.java:583: trailing whitespace.
+                Task atask = intakeAnnotations(myShepherd, anns, topTask, fastlane);
src/main/java/org/ecocean/ia/IA.java:584: trailing whitespace.
+                System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask +
src/main/java/org/ecocean/ia/IA.java:585: trailing whitespace.
+                    " for " + topTask);
src/main/java/org/ecocean/ia/IA.java:586: trailing whitespace.
+                myShepherd.getPM().refresh(topTask);
src/main/java/org/ecocean/ia/IA.java:587: trailing whitespace.
+                topTask.addChild(atask);
src/main/java/org/ecocean/ia/IA.java:588: trailing whitespace.
+                topTask.setModified();
src/main/java/org/ecocean/ia/IA.java:589: trailing whitespace.
+                myShepherd.getPM().makePersistent(atask);
src/main/java/org/ecocean/ia/IA.java:590: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:591: trailing whitespace.
+            myShepherd.commitDBTransaction();
src/main/java/org/ecocean/ia/IA.java:592: trailing whitespace.
+        } catch (Exception e) {
src/main/java/org/ecocean/ia/IA.java:593: trailing whitespace.
+            e.printStackTrace();
src/main/java/org/ecocean/ia/IA.java:594: trailing whitespace.
+            myShepherd.rollbackDBTransaction();
src/main/java/org/ecocean/ia/IA.java:595: trailing whitespace.
+        } finally {
src/main/java/org/ecocean/ia/IA.java:596: trailing whitespace.
+            myShepherd.closeDBTransaction();
src/main/java/org/ecocean/ia/IA.java:597: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:598: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:599: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:600: trailing whitespace.
+    // via IAGateway servlet, we handle the work
src/main/java/org/ecocean/ia/IA.java:601: trailing whitespace.
+    public static void handleGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/ia/IA.java:602: trailing whitespace.
+    throws java.io.IOException {
src/main/java/org/ecocean/ia/IA.java:603: trailing whitespace.
+        // JSONObject rtn = queueCallback(request);
src/main/java/org/ecocean/ia/IA.java:604: trailing whitespace.
+        JSONObject rtn = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
src/main/java/org/ecocean/ia/IA.java:605: trailing whitespace.
+        String context = ServletUtilities.getContext(request);
src/main/java/org/ecocean/ia/IA.java:606: trailing whitespace.
+        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/ia/IA.java:607: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:608: trailing whitespace.
+        myShepherd.setAction("IA.handleGet");
src/main/java/org/ecocean/ia/IA.java:609: trailing whitespace.
+        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/ia/IA.java:610: trailing whitespace.
+        String taskId = request.getParameter("taskId");
src/main/java/org/ecocean/ia/IA.java:611: trailing whitespace.
+        if (taskId != null) {
src/main/java/org/ecocean/ia/IA.java:612: trailing whitespace.
+            Task task = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/ia/IA.java:613: trailing whitespace.
+            if (task == null) {
src/main/java/org/ecocean/ia/IA.java:614: trailing whitespace.
+                response.sendError(404, "Not found: taskId=" + taskId);
src/main/java/org/ecocean/ia/IA.java:615: trailing whitespace.
+                myShepherd.rollbackDBTransaction();
src/main/java/org/ecocean/ia/IA.java:616: trailing whitespace.
+                myShepherd.closeDBTransaction();
src/main/java/org/ecocean/ia/IA.java:617: trailing whitespace.
+                return;
src/main/java/org/ecocean/ia/IA.java:618: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/IA.java:619: trailing whitespace.
+            rtn.put("success", true);
src/main/java/org/ecocean/ia/IA.java:620: trailing whitespace.
+            rtn.remove("error");
src/main/java/org/ecocean/ia/IA.java:621: trailing whitespace.
+            rtn.put("task",
src/main/java/org/ecocean/ia/IA.java:622: trailing whitespace.
+                task.toJSONObject(Util.requestParameterSet(request.getParameter(
src/main/java/org/ecocean/ia/IA.java:623: trailing whitespace.
+                    "includeChildren"))));
src/main/java/org/ecocean/ia/IA.java:624: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:625: trailing whitespace.
+        response.setContentType("text/plain");
src/main/java/org/ecocean/ia/IA.java:626: trailing whitespace.
+        PrintWriter out = response.getWriter();
src/main/java/org/ecocean/ia/IA.java:627: trailing whitespace.
+        out.println(rtn.toString());
src/main/java/org/ecocean/ia/IA.java:628: trailing whitespace.
+        out.close();
src/main/java/org/ecocean/ia/IA.java:629: trailing whitespace.
+        myShepherd.rollbackDBTransaction();
src/main/java/org/ecocean/ia/IA.java:630: trailing whitespace.
+        myShepherd.closeDBTransaction();
src/main/java/org/ecocean/ia/IA.java:631: trailing whitespace.
+        return;
src/main/java/org/ecocean/ia/IA.java:632: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:633: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:634: trailing whitespace.
+    public static String getBaseURL(String context) {
src/main/java/org/ecocean/ia/IA.java:635: trailing whitespace.
+        String url = CommonConfiguration.getServerURL(context);
src/main/java/org/ecocean/ia/IA.java:636: trailing whitespace.
+        String containerName = CommonConfiguration.getProperty("containerName", context);
src/main/java/org/ecocean/ia/IA.java:637: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:638: trailing whitespace.
+        if (containerName != null && !"".equals(containerName)) {
src/main/java/org/ecocean/ia/IA.java:639: trailing whitespace.
+            containerName = containerName.trim();
src/main/java/org/ecocean/ia/IA.java:640: trailing whitespace.
+            System.out.println("INFO: Wildbook is containerized: Server getBaseURL is returning: " +
src/main/java/org/ecocean/ia/IA.java:641: trailing whitespace.
+                containerName + "");
src/main/java/org/ecocean/ia/IA.java:642: trailing whitespace.
+            url = url.replace("localhost", containerName);
src/main/java/org/ecocean/ia/IA.java:643: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:644: trailing whitespace.
+        System.out.println("INFO: Server getBaseURL is returning " + url);
src/main/java/org/ecocean/ia/IA.java:645: trailing whitespace.
+        return url;
src/main/java/org/ecocean/ia/IA.java:646: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:647: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:648: trailing whitespace.
+    // (optional!) Taxonomy will append "_Scientific_name" to label and try that.  if not available, then try just label.
src/main/java/org/ecocean/ia/IA.java:649: trailing whitespace.
+    public static String getProperty(String context, String label, Taxonomy tax, String def) {
src/main/java/org/ecocean/ia/IA.java:650: trailing whitespace.
+        if ((tax != null) && (tax.getScientificName() != null)) {
src/main/java/org/ecocean/ia/IA.java:651: trailing whitespace.
+            String propKey = label + "_".concat(tax.getScientificName()).replaceAll(" ", "_");
src/main/java/org/ecocean/ia/IA.java:652: trailing whitespace.
+            System.out.println("[INFO] IA.getProperty() using propKey=" + propKey + " based on " +
src/main/java/org/ecocean/ia/IA.java:653: trailing whitespace.
+                tax);
src/main/java/org/ecocean/ia/IA.java:654: trailing whitespace.
+            String val = getProperty(context, propKey, (String)null);
src/main/java/org/ecocean/ia/IA.java:655: trailing whitespace.
+            if (val != null) return val;
src/main/java/org/ecocean/ia/IA.java:656: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:657: trailing whitespace.
+        return IA.getProperty(context, label, def);
src/main/java/org/ecocean/ia/IA.java:658: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:659: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:660: trailing whitespace.
+    public static String getProperty(String context, String label, Taxonomy tax) { // no-default version
src/main/java/org/ecocean/ia/IA.java:661: trailing whitespace.
+        return getProperty(context, label, tax, null);
src/main/java/org/ecocean/ia/IA.java:662: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:663: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:664: trailing whitespace.
+    public static String getProperty(String context, String label) { // no-default, no-taxonomy
src/main/java/org/ecocean/ia/IA.java:665: trailing whitespace.
+        return getProperty(context, label, (String)null);
src/main/java/org/ecocean/ia/IA.java:666: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:667: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:668: trailing whitespace.
+    public static String getProperty(String context, String label, String def) {
src/main/java/org/ecocean/ia/IA.java:669: trailing whitespace.
+        Properties p = getProperties(context);
src/main/java/org/ecocean/ia/IA.java:670: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:671: trailing whitespace.
+        if (p == null) {
src/main/java/org/ecocean/ia/IA.java:672: trailing whitespace.
+            System.out.println("IA.getProperty(" + label +
src/main/java/org/ecocean/ia/IA.java:673: trailing whitespace.
+                ") has no properties; IA.properties unavailable?");
src/main/java/org/ecocean/ia/IA.java:674: trailing whitespace.
+            return null;
src/main/java/org/ecocean/ia/IA.java:675: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:676: trailing whitespace.
+        return p.getProperty(label, def);
src/main/java/org/ecocean/ia/IA.java:677: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:678: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:679: trailing whitespace.
+    private static Properties getProperties(String context) {
src/main/java/org/ecocean/ia/IA.java:680: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/IA.java:681: trailing whitespace.
+            return ShepherdProperties.getProperties(PROP_FILE, "", context);
src/main/java/org/ecocean/ia/IA.java:682: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/IA.java:683: trailing whitespace.
+            return null;
src/main/java/org/ecocean/ia/IA.java:684: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/IA.java:685: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:686: trailing whitespace.
+
src/main/java/org/ecocean/ia/IA.java:687: trailing whitespace.
+    public static void log(String msg) {
src/main/java/org/ecocean/ia/IA.java:688: trailing whitespace.
+        System.out.println(new org.joda.time.DateTime() + " " + msg);
src/main/java/org/ecocean/ia/IA.java:689: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IA.java:690: trailing whitespace.
+}
src/main/java/org/ecocean/ia/IAException.java:1: trailing whitespace.
+package org.ecocean.ia;
src/main/java/org/ecocean/ia/IAException.java:2: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:3: trailing whitespace.
+import org.json.JSONArray;
src/main/java/org/ecocean/ia/IAException.java:4: trailing whitespace.
+import org.json.JSONObject;
src/main/java/org/ecocean/ia/IAException.java:5: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:6: trailing whitespace.
+public class IAException extends Exception {
src/main/java/org/ecocean/ia/IAException.java:7: trailing whitespace.
+    protected boolean requeue = false;
src/main/java/org/ecocean/ia/IAException.java:8: trailing whitespace.
+    protected boolean requeueIncrement = false;
src/main/java/org/ecocean/ia/IAException.java:9: trailing whitespace.
+    // protected JSONArray errors = null;
src/main/java/org/ecocean/ia/IAException.java:10: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:11: trailing whitespace.
+    // ml-service migration v2 (commit #8): optional typed code so callers
src/main/java/org/ecocean/ia/IAException.java:12: trailing whitespace.
+    // (e.g. MlServiceProcessor) can classify failures without parsing message
src/main/java/org/ecocean/ia/IAException.java:13: trailing whitespace.
+    // strings. Backward-compatible — existing constructors leave code null.
src/main/java/org/ecocean/ia/IAException.java:14: trailing whitespace.
+    protected String code;
src/main/java/org/ecocean/ia/IAException.java:15: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:16: trailing whitespace.
+    public IAException(String message) {
src/main/java/org/ecocean/ia/IAException.java:17: trailing whitespace.
+        super(message);
src/main/java/org/ecocean/ia/IAException.java:18: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:19: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:20: trailing whitespace.
+    public IAException(String message, boolean requeue) {
src/main/java/org/ecocean/ia/IAException.java:21: trailing whitespace.
+        super(message);
src/main/java/org/ecocean/ia/IAException.java:22: trailing whitespace.
+        this.requeue = requeue;
src/main/java/org/ecocean/ia/IAException.java:23: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:24: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:25: trailing whitespace.
+    public IAException(String message, boolean requeue, boolean requeueIncrement) {
src/main/java/org/ecocean/ia/IAException.java:26: trailing whitespace.
+        super(message);
src/main/java/org/ecocean/ia/IAException.java:27: trailing whitespace.
+        this.requeue = requeue;
src/main/java/org/ecocean/ia/IAException.java:28: trailing whitespace.
+        this.requeueIncrement = requeueIncrement;
src/main/java/org/ecocean/ia/IAException.java:29: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:30: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:31: trailing whitespace.
+    public IAException(String code, String message, boolean requeue, boolean requeueIncrement) {
src/main/java/org/ecocean/ia/IAException.java:32: trailing whitespace.
+        super(message);
src/main/java/org/ecocean/ia/IAException.java:33: trailing whitespace.
+        this.code = code;
src/main/java/org/ecocean/ia/IAException.java:34: trailing whitespace.
+        this.requeue = requeue;
src/main/java/org/ecocean/ia/IAException.java:35: trailing whitespace.
+        this.requeueIncrement = requeueIncrement;
src/main/java/org/ecocean/ia/IAException.java:36: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:37: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:38: trailing whitespace.
+    public String getCode() {
src/main/java/org/ecocean/ia/IAException.java:39: trailing whitespace.
+        return code;
src/main/java/org/ecocean/ia/IAException.java:40: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:41: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:42: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/IAException.java:43: trailing whitespace.
+    public IAException(String message, JSONArray errors) {
src/main/java/org/ecocean/ia/IAException.java:44: trailing whitespace.
+        super(message);
src/main/java/org/ecocean/ia/IAException.java:45: trailing whitespace.
+        this.errors = errors;
src/main/java/org/ecocean/ia/IAException.java:46: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:47: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:48: trailing whitespace.
+    public IAException(String message, JSONObject error) {
src/main/java/org/ecocean/ia/IAException.java:49: trailing whitespace.
+        super(message);
src/main/java/org/ecocean/ia/IAException.java:50: trailing whitespace.
+        addError(error);
src/main/java/org/ecocean/ia/IAException.java:51: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:52: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/IAException.java:53: trailing whitespace.
+    public boolean shouldRequeue() {
src/main/java/org/ecocean/ia/IAException.java:54: trailing whitespace.
+        return requeue;
src/main/java/org/ecocean/ia/IAException.java:55: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:56: trailing whitespace.
+
src/main/java/org/ecocean/ia/IAException.java:57: trailing whitespace.
+    public boolean shouldIncrement() {
src/main/java/org/ecocean/ia/IAException.java:58: trailing whitespace.
+        return requeueIncrement;
src/main/java/org/ecocean/ia/IAException.java:59: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/IAException.java:60: trailing whitespace.
+}
src/main/java/org/ecocean/ia/MLService.java:1: trailing whitespace.
+package org.ecocean.ia;
src/main/java/org/ecocean/ia/MLService.java:2: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:3: trailing whitespace.
+import org.json.JSONArray;
src/main/java/org/ecocean/ia/MLService.java:4: trailing whitespace.
+import org.json.JSONObject;
src/main/java/org/ecocean/ia/MLService.java:5: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:6: trailing whitespace.
+import java.net.MalformedURLException;
src/main/java/org/ecocean/ia/MLService.java:7: trailing whitespace.
+import java.net.URL;
src/main/java/org/ecocean/ia/MLService.java:8: trailing whitespace.
+import java.util.ArrayList;
src/main/java/org/ecocean/ia/MLService.java:9: trailing whitespace.
+import java.util.List;
src/main/java/org/ecocean/ia/MLService.java:10: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:11: trailing whitespace.
+import org.ecocean.Annotation;
src/main/java/org/ecocean/ia/MLService.java:12: trailing whitespace.
+import org.ecocean.Embedding;
src/main/java/org/ecocean/ia/MLService.java:13: trailing whitespace.
+import org.ecocean.ia.Task;
src/main/java/org/ecocean/ia/MLService.java:14: trailing whitespace.
+import org.ecocean.identity.IBEISIA;
src/main/java/org/ecocean/ia/MLService.java:15: trailing whitespace.
+import org.ecocean.IAJsonProperties;
src/main/java/org/ecocean/ia/MLService.java:16: trailing whitespace.
+import org.ecocean.media.Feature;
src/main/java/org/ecocean/ia/MLService.java:17: trailing whitespace.
+import org.ecocean.media.FeatureType;
src/main/java/org/ecocean/ia/MLService.java:18: trailing whitespace.
+import org.ecocean.media.MediaAsset;
src/main/java/org/ecocean/ia/MLService.java:19: trailing whitespace.
+import org.ecocean.RestClient;
src/main/java/org/ecocean/ia/MLService.java:20: trailing whitespace.
+import org.ecocean.servlet.IAGateway;
src/main/java/org/ecocean/ia/MLService.java:21: trailing whitespace.
+import org.ecocean.shepherd.core.Shepherd;
src/main/java/org/ecocean/ia/MLService.java:22: trailing whitespace.
+import org.ecocean.Util;
src/main/java/org/ecocean/ia/MLService.java:23: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:24: trailing whitespace.
+import java.io.IOException;
src/main/java/org/ecocean/ia/MLService.java:25: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:26: trailing whitespace.
+// https://github.com/WildMeOrg/ml-service
src/main/java/org/ecocean/ia/MLService.java:27: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:28: trailing whitespace.
+public class MLService {
src/main/java/org/ecocean/ia/MLService.java:29: trailing whitespace.
+    private IAJsonProperties iaConfig = null;
src/main/java/org/ecocean/ia/MLService.java:30: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:31: trailing whitespace.
+    public MLService() {
src/main/java/org/ecocean/ia/MLService.java:32: trailing whitespace.
+        iaConfig = IAJsonProperties.iaConfig();
src/main/java/org/ecocean/ia/MLService.java:33: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:34: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:35: trailing whitespace.
+    public JSONObject initiateRequest(MediaAsset ma, String taxonomyString)
src/main/java/org/ecocean/ia/MLService.java:36: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MLService.java:37: trailing whitespace.
+        addToQueue(createJobData(ma, taxonomyString), null);
src/main/java/org/ecocean/ia/MLService.java:38: trailing whitespace.
+        return null;
src/main/java/org/ecocean/ia/MLService.java:39: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:40: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:41: trailing whitespace.
+    public JSONObject initiateRequest(Annotation ann, String taxonomyString)
src/main/java/org/ecocean/ia/MLService.java:42: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MLService.java:43: trailing whitespace.
+        return initiateRequest(ann, taxonomyString, null);
src/main/java/org/ecocean/ia/MLService.java:44: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:45: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:46: trailing whitespace.
+    public JSONObject initiateRequest(Annotation ann, String taxonomyString, Task task)
src/main/java/org/ecocean/ia/MLService.java:47: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MLService.java:48: trailing whitespace.
+        addToQueue(createJobData(ann, taxonomyString), task);
src/main/java/org/ecocean/ia/MLService.java:49: trailing whitespace.
+        return null;
src/main/java/org/ecocean/ia/MLService.java:50: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:51: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:52: trailing whitespace.
+    public IAJsonProperties getIAConfig() {
src/main/java/org/ecocean/ia/MLService.java:53: trailing whitespace.
+        return iaConfig;
src/main/java/org/ecocean/ia/MLService.java:54: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:55: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:56: trailing whitespace.
+    // there can be multiple configs (differing model_id)
src/main/java/org/ecocean/ia/MLService.java:57: trailing whitespace.
+    public List<JSONObject> getConfigs(String passedTxStr)
src/main/java/org/ecocean/ia/MLService.java:58: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:59: trailing whitespace.
+        IAJsonProperties iac = getIAConfig();
src/main/java/org/ecocean/ia/MLService.java:60: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:61: trailing whitespace.
+        if (iac == null) throw new IAException("MLService.getConfigs() iac configuration problem");
src/main/java/org/ecocean/ia/MLService.java:62: trailing whitespace.
+        if (passedTxStr == null)
src/main/java/org/ecocean/ia/MLService.java:63: trailing whitespace.
+            throw new IAException("MLService.getConfigs() null passed taxonomy");
src/main/java/org/ecocean/ia/MLService.java:64: trailing whitespace.
+        String taxonomyString = passedTxStr.replaceAll(" ", "."); // need dots, not spaces
src/main/java/org/ecocean/ia/MLService.java:65: trailing whitespace.
+        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
src/main/java/org/ecocean/ia/MLService.java:66: trailing whitespace.
+        if (mlc == null)
src/main/java/org/ecocean/ia/MLService.java:67: trailing whitespace.
+            throw new IAException(
src/main/java/org/ecocean/ia/MLService.java:68: trailing whitespace.
+                      "MLService.getConfigs() configuration problem with taxonomyString=" +
src/main/java/org/ecocean/ia/MLService.java:69: trailing whitespace.
+                      taxonomyString);
src/main/java/org/ecocean/ia/MLService.java:70: trailing whitespace.
+        JSONArray confs = null;
src/main/java/org/ecocean/ia/MLService.java:71: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MLService.java:72: trailing whitespace.
+            confs = (JSONArray)mlc;
src/main/java/org/ecocean/ia/MLService.java:73: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/MLService.java:74: trailing whitespace.
+            ex.printStackTrace();
src/main/java/org/ecocean/ia/MLService.java:75: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:76: trailing whitespace.
+        if (confs == null)
src/main/java/org/ecocean/ia/MLService.java:77: trailing whitespace.
+            throw new IAException(
src/main/java/org/ecocean/ia/MLService.java:78: trailing whitespace.
+                      "MLService.getConfigs() configuration problem with taxonomyString=" +
src/main/java/org/ecocean/ia/MLService.java:79: trailing whitespace.
+                      taxonomyString + "; mlc=" + mlc);
src/main/java/org/ecocean/ia/MLService.java:80: trailing whitespace.
+        List<JSONObject> configs = new ArrayList<JSONObject>();
src/main/java/org/ecocean/ia/MLService.java:81: trailing whitespace.
+        for (int i = 0; i < confs.length(); i++) {
src/main/java/org/ecocean/ia/MLService.java:82: trailing whitespace.
+            JSONObject jc = confs.optJSONObject(i);
src/main/java/org/ecocean/ia/MLService.java:83: trailing whitespace.
+            if (jc != null) configs.add(jc);
src/main/java/org/ecocean/ia/MLService.java:84: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:85: trailing whitespace.
+        return configs;
src/main/java/org/ecocean/ia/MLService.java:86: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:87: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:88: trailing whitespace.
+    public void addToQueue(JSONObject jobData, Task task)
src/main/java/org/ecocean/ia/MLService.java:89: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MLService.java:90: trailing whitespace.
+        if (jobData == null) return;
src/main/java/org/ecocean/ia/MLService.java:91: trailing whitespace.
+        if (task != null) jobData.put("taskId", task.getId());
src/main/java/org/ecocean/ia/MLService.java:92: trailing whitespace.
+        IAGateway.addToDetectionQueue("context0", jobData.toString());
src/main/java/org/ecocean/ia/MLService.java:93: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:94: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:95: trailing whitespace.
+    // i think we *must* pass taxonomyString here
src/main/java/org/ecocean/ia/MLService.java:96: trailing whitespace.
+    public JSONObject createJobData(MediaAsset ma, String taxonomyString) {
src/main/java/org/ecocean/ia/MLService.java:97: trailing whitespace.
+        JSONObject data = new JSONObject();
src/main/java/org/ecocean/ia/MLService.java:98: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:99: trailing whitespace.
+        data.put("MLService", true);
src/main/java/org/ecocean/ia/MLService.java:100: trailing whitespace.
+        data.put("taxonomyString", taxonomyString);
src/main/java/org/ecocean/ia/MLService.java:101: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:102: trailing whitespace.
+        JSONArray maIds = new JSONArray();
src/main/java/org/ecocean/ia/MLService.java:103: trailing whitespace.
+        maIds.put(ma.getIdInt());
src/main/java/org/ecocean/ia/MLService.java:104: trailing whitespace.
+        data.put("mediaAssetIds", maIds);
src/main/java/org/ecocean/ia/MLService.java:105: trailing whitespace.
+        return data;
src/main/java/org/ecocean/ia/MLService.java:106: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:107: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:108: trailing whitespace.
+    public JSONObject createJobData(Annotation ann, String taxonomyString) {
src/main/java/org/ecocean/ia/MLService.java:109: trailing whitespace.
+        JSONObject data = new JSONObject();
src/main/java/org/ecocean/ia/MLService.java:110: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:111: trailing whitespace.
+        data.put("MLService", true);
src/main/java/org/ecocean/ia/MLService.java:112: trailing whitespace.
+        data.put("taxonomyString", taxonomyString);
src/main/java/org/ecocean/ia/MLService.java:113: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:114: trailing whitespace.
+        JSONArray annIds = new JSONArray();
src/main/java/org/ecocean/ia/MLService.java:115: trailing whitespace.
+        annIds.put(ann.getId());
src/main/java/org/ecocean/ia/MLService.java:116: trailing whitespace.
+        data.put("annotationIds", annIds);
src/main/java/org/ecocean/ia/MLService.java:117: trailing whitespace.
+        return data;
src/main/java/org/ecocean/ia/MLService.java:118: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:119: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:120: trailing whitespace.
+    public void processQueueJob(JSONObject jobData) {
src/main/java/org/ecocean/ia/MLService.java:121: trailing whitespace.
+        System.out.println("#################################################### processing: " +
src/main/java/org/ecocean/ia/MLService.java:122: trailing whitespace.
+            jobData.toString(8));
src/main/java/org/ecocean/ia/MLService.java:123: trailing whitespace.
+        Shepherd myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/ia/MLService.java:124: trailing whitespace.
+        myShepherd.setAction("MLService.processQueueJob");
src/main/java/org/ecocean/ia/MLService.java:125: trailing whitespace.
+        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/ia/MLService.java:126: trailing whitespace.
+        FeatureType.initAll(myShepherd);
src/main/java/org/ecocean/ia/MLService.java:127: trailing whitespace.
+        Task task = myShepherd.getTask(jobData.optString("taskId", null));
src/main/java/org/ecocean/ia/MLService.java:128: trailing whitespace.
+        JSONArray ids = jobData.optJSONArray("mediaAssetIds");
src/main/java/org/ecocean/ia/MLService.java:129: trailing whitespace.
+        // skipEmbedding will set true if there was a non-requeuable config problem
src/main/java/org/ecocean/ia/MLService.java:130: trailing whitespace.
+        // (probably not configured for _mlservice in IA.json) so we just give up and
src/main/java/org/ecocean/ia/MLService.java:131: trailing whitespace.
+        // let ident do its thing
src/main/java/org/ecocean/ia/MLService.java:132: trailing whitespace.
+        boolean skipEmbedding = false;
src/main/java/org/ecocean/ia/MLService.java:133: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MLService.java:134: trailing whitespace.
+            // got some asset ids
src/main/java/org/ecocean/ia/MLService.java:135: trailing whitespace.
+            if (ids != null) {
src/main/java/org/ecocean/ia/MLService.java:136: trailing whitespace.
+                for (String maId : Util.jsonArrayToStringList(ids)) {
src/main/java/org/ecocean/ia/MLService.java:137: trailing whitespace.
+                    System.out.println("[DEBUG] MLService.processQueueJob() maId=" + maId + " [" +
src/main/java/org/ecocean/ia/MLService.java:138: trailing whitespace.
+                        task + "]");
src/main/java/org/ecocean/ia/MLService.java:139: trailing whitespace.
+                    send(myShepherd.getMediaAsset(maId), jobData.optString("taxonomyString", null),
src/main/java/org/ecocean/ia/MLService.java:140: trailing whitespace.
+                        myShepherd);
src/main/java/org/ecocean/ia/MLService.java:141: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/MLService.java:142: trailing whitespace.
+                // maybe annot ids?
src/main/java/org/ecocean/ia/MLService.java:143: trailing whitespace.
+            } else {
src/main/java/org/ecocean/ia/MLService.java:144: trailing whitespace.
+                ids = jobData.optJSONArray("annotationIds");
src/main/java/org/ecocean/ia/MLService.java:145: trailing whitespace.
+                if (ids != null) {
src/main/java/org/ecocean/ia/MLService.java:146: trailing whitespace.
+                    for (String annId : Util.jsonArrayToStringList(ids)) {
src/main/java/org/ecocean/ia/MLService.java:147: trailing whitespace.
+                        System.out.println("[DEBUG] MLService.processQueueJob() annId=" + annId +
src/main/java/org/ecocean/ia/MLService.java:148: trailing whitespace.
+                            " [" + task + "]");
src/main/java/org/ecocean/ia/MLService.java:149: trailing whitespace.
+                        send(myShepherd.getAnnotation(annId),
src/main/java/org/ecocean/ia/MLService.java:150: trailing whitespace.
+                            jobData.optString("taxonomyString", null), myShepherd);
src/main/java/org/ecocean/ia/MLService.java:151: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/MLService.java:152: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/MLService.java:153: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MLService.java:154: trailing whitespace.
+            if (task != null) task.setStatus("completed");
src/main/java/org/ecocean/ia/MLService.java:155: trailing whitespace.
+        } catch (IAException iaex) {
src/main/java/org/ecocean/ia/MLService.java:156: trailing whitespace.
+            System.out.println("MLService.processQueueJob() threw " + iaex + " with jobData=" +
src/main/java/org/ecocean/ia/MLService.java:157: trailing whitespace.
+                jobData);
src/main/java/org/ecocean/ia/MLService.java:158: trailing whitespace.
+            iaex.printStackTrace();
src/main/java/org/ecocean/ia/MLService.java:159: trailing whitespace.
+            if (task != null) {
src/main/java/org/ecocean/ia/MLService.java:160: trailing whitespace.
+                task.setStatus("error");
src/main/java/org/ecocean/ia/MLService.java:161: trailing whitespace.
+                task.setStatusDetailsAddError("UNKNOWN", "MLService job: " + iaex);
src/main/java/org/ecocean/ia/MLService.java:162: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MLService.java:163: trailing whitespace.
+            if (iaex.shouldRequeue()) {
src/main/java/org/ecocean/ia/MLService.java:164: trailing whitespace.
+                requeueJob(jobData, iaex.shouldIncrement());
src/main/java/org/ecocean/ia/MLService.java:165: trailing whitespace.
+            } else {
src/main/java/org/ecocean/ia/MLService.java:166: trailing whitespace.
+                // we might want more complex logic to determine if we really should give up
src/main/java/org/ecocean/ia/MLService.java:167: trailing whitespace.
+                skipEmbedding = true;
src/main/java/org/ecocean/ia/MLService.java:168: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MLService.java:169: trailing whitespace.
+        } finally {
src/main/java/org/ecocean/ia/MLService.java:170: trailing whitespace.
+            // we end up here after *each* annotation, so we are "done" when all annotations have been processed
src/main/java/org/ecocean/ia/MLService.java:171: trailing whitespace.
+            boolean taskComplete = skipEmbedding || areAllEmbeddingsExtracted(task);
src/main/java/org/ecocean/ia/MLService.java:172: trailing whitespace.
+            if (taskComplete && (task != null)) task.setCompletionDateInMilliseconds();
src/main/java/org/ecocean/ia/MLService.java:173: trailing whitespace.
+            myShepherd.commitDBTransaction();
src/main/java/org/ecocean/ia/MLService.java:174: trailing whitespace.
+            if (taskComplete) {
src/main/java/org/ecocean/ia/MLService.java:175: trailing whitespace.
+                // now we are done we can fake a callback to initiate identification
src/main/java/org/ecocean/ia/MLService.java:176: trailing whitespace.
+                JSONObject fakeResp = new JSONObject();
src/main/java/org/ecocean/ia/MLService.java:177: trailing whitespace.
+                fakeResp.put("embeddingExtraction", true);
src/main/java/org/ecocean/ia/MLService.java:178: trailing whitespace.
+                // taskComplete is only true if we have *some* annots
src/main/java/org/ecocean/ia/MLService.java:179: trailing whitespace.
+                JSONObject annMap = new JSONObject();
src/main/java/org/ecocean/ia/MLService.java:180: trailing whitespace.
+                if (task != null)
src/main/java/org/ecocean/ia/MLService.java:181: trailing whitespace.
+                    for (Annotation ann : task.getObjectAnnotations()) {
src/main/java/org/ecocean/ia/MLService.java:182: trailing whitespace.
+                        MediaAsset ma = ann.getMediaAsset();
src/main/java/org/ecocean/ia/MLService.java:183: trailing whitespace.
+                        if (ma == null) continue; // snh
src/main/java/org/ecocean/ia/MLService.java:184: trailing whitespace.
+                        if (!annMap.has(ma.getId())) annMap.put(ma.getId(), new JSONArray());
src/main/java/org/ecocean/ia/MLService.java:185: trailing whitespace.
+                        annMap.getJSONArray(ma.getId()).put(ann.getId());
src/main/java/org/ecocean/ia/MLService.java:186: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/MLService.java:187: trailing whitespace.
+                fakeResp.put("annotationMap", annMap);
src/main/java/org/ecocean/ia/MLService.java:188: trailing whitespace.
+                JSONObject cbRes = IBEISIA.processCallback((task == null) ? null : task.getId(),
src/main/java/org/ecocean/ia/MLService.java:189: trailing whitespace.
+                    fakeResp, myShepherd.getContext(), null);
src/main/java/org/ecocean/ia/MLService.java:190: trailing whitespace.
+                System.out.println("[DEBUG] MLService.processQueueJob() [" + task +
src/main/java/org/ecocean/ia/MLService.java:191: trailing whitespace.
+                    " complete] cbRes=" + cbRes);
src/main/java/org/ecocean/ia/MLService.java:192: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MLService.java:193: trailing whitespace.
+            myShepherd.closeDBTransaction();
src/main/java/org/ecocean/ia/MLService.java:194: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:195: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:196: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:197: trailing whitespace.
+    // true if all annotations "are done" from (trying to) extract embeddings
src/main/java/org/ecocean/ia/MLService.java:198: trailing whitespace.
+    private boolean areAllEmbeddingsExtracted(Task task) {
src/main/java/org/ecocean/ia/MLService.java:199: trailing whitespace.
+        if (task == null) return false;
src/main/java/org/ecocean/ia/MLService.java:200: trailing whitespace.
+        List<Annotation> anns = task.getObjectAnnotations();
src/main/java/org/ecocean/ia/MLService.java:201: trailing whitespace.
+        // we return false here because there is no reason to send to ident in this case
src/main/java/org/ecocean/ia/MLService.java:202: trailing whitespace.
+        if (Util.collectionIsEmptyOrNull(anns)) return false;
src/main/java/org/ecocean/ia/MLService.java:203: trailing whitespace.
+        // we iterate over annotations and only return false if we find one explicitly still
src/main/java/org/ecocean/ia/MLService.java:204: trailing whitespace.
+        // in processing state. this means *any* other (complete, error, etc) get counted as "done"
src/main/java/org/ecocean/ia/MLService.java:205: trailing whitespace.
+        for (Annotation ann : anns) {
src/main/java/org/ecocean/ia/MLService.java:206: trailing whitespace.
+            if (IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(ann.getIdentificationStatus()))
src/main/java/org/ecocean/ia/MLService.java:207: trailing whitespace.
+                return false;
src/main/java/org/ecocean/ia/MLService.java:208: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:209: trailing whitespace.
+        System.out.println(
src/main/java/org/ecocean/ia/MLService.java:210: trailing whitespace.
+            "[DEBUG] MLService.areAllEmbeddingsExtracted() fell thru (aka true) on " + anns.size() +
src/main/java/org/ecocean/ia/MLService.java:211: trailing whitespace.
+            " annots for " + task);
src/main/java/org/ecocean/ia/MLService.java:212: trailing whitespace.
+        return true;
src/main/java/org/ecocean/ia/MLService.java:213: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:214: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:215: trailing whitespace.
+    public void requeueJob(JSONObject jobData, boolean increment) {
src/main/java/org/ecocean/ia/MLService.java:216: trailing whitespace.
+        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
src/main/java/org/ecocean/ia/MLService.java:217: trailing whitespace.
+            jobData);
src/main/java/org/ecocean/ia/MLService.java:218: trailing whitespace.
+        // this handles a bunch of messiness, including max retries etc
src/main/java/org/ecocean/ia/MLService.java:219: trailing whitespace.
+        IAGateway.requeueJob(jobData, increment);
src/main/java/org/ecocean/ia/MLService.java:220: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:221: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:222: trailing whitespace.
+    public void send(MediaAsset ma, String taxonomyString, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MLService.java:223: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:224: trailing whitespace.
+        if (ma == null) throw new IAException("null MediaAsset passed");
src/main/java/org/ecocean/ia/MLService.java:225: trailing whitespace.
+        for (JSONObject conf : getConfigs(taxonomyString)) {
src/main/java/org/ecocean/ia/MLService.java:226: trailing whitespace.
+            JSONObject payload = createPayload(ma, conf);
src/main/java/org/ecocean/ia/MLService.java:227: trailing whitespace.
+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
src/main/java/org/ecocean/ia/MLService.java:228: trailing whitespace.
+                payload);
src/main/java/org/ecocean/ia/MLService.java:229: trailing whitespace.
+            // got results, now we try to use them
src/main/java/org/ecocean/ia/MLService.java:230: trailing whitespace.
+            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
src/main/java/org/ecocean/ia/MLService.java:231: trailing whitespace.
+                "; RESPONSE => " + res);
src/main/java/org/ecocean/ia/MLService.java:232: trailing whitespace.
+            List<Annotation> anns = processMediaAssetResults(ma, res);
src/main/java/org/ecocean/ia/MLService.java:233: trailing whitespace.
+            System.out.println("MLService.send() created " + anns.size() + " anns on " + ma + ": " +
src/main/java/org/ecocean/ia/MLService.java:234: trailing whitespace.
+                anns);
src/main/java/org/ecocean/ia/MLService.java:235: trailing whitespace.
+            // FIXME persist anns using myShepherd
src/main/java/org/ecocean/ia/MLService.java:236: trailing whitespace.
+            // FIXME send along to ident????? (but using vectors!!!????!)
src/main/java/org/ecocean/ia/MLService.java:237: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:238: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:239: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:240: trailing whitespace.
+    public List<Annotation> processMediaAssetResults(MediaAsset ma, JSONObject res)
src/main/java/org/ecocean/ia/MLService.java:241: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:242: trailing whitespace.
+        if (res == null) throw new IAException("empty results");
src/main/java/org/ecocean/ia/MLService.java:243: trailing whitespace.
+        if (!res.optBoolean("success", false))
src/main/java/org/ecocean/ia/MLService.java:244: trailing whitespace.
+            throw new IAException("results success=false: " + res);
src/main/java/org/ecocean/ia/MLService.java:245: trailing whitespace.
+        JSONArray bboxes = res.optJSONArray("bboxes");
src/main/java/org/ecocean/ia/MLService.java:246: trailing whitespace.
+        if (bboxes == null) throw new IAException("null bboxes in results: " + res);
src/main/java/org/ecocean/ia/MLService.java:247: trailing whitespace.
+        List<Annotation> anns = new ArrayList<Annotation>();
src/main/java/org/ecocean/ia/MLService.java:248: trailing whitespace.
+        if (bboxes.length() < 1) return anns;
src/main/java/org/ecocean/ia/MLService.java:249: trailing whitespace.
+        // TODO do we ever care about scores?
src/main/java/org/ecocean/ia/MLService.java:250: trailing whitespace.
+        List<Double> scores = Util.jsonArrayToDoubleList(res.optJSONArray("scores"));
src/main/java/org/ecocean/ia/MLService.java:251: trailing whitespace.
+        if ((scores == null) || (scores.size() != bboxes.length()))
src/main/java/org/ecocean/ia/MLService.java:252: trailing whitespace.
+            throw new IAException("scores size does not match bboxes: " + res);
src/main/java/org/ecocean/ia/MLService.java:253: trailing whitespace.
+        List<Double> thetas = Util.jsonArrayToDoubleList(res.optJSONArray("thetas"));
src/main/java/org/ecocean/ia/MLService.java:254: trailing whitespace.
+        if ((thetas == null) || (thetas.size() != bboxes.length()))
src/main/java/org/ecocean/ia/MLService.java:255: trailing whitespace.
+            throw new IAException("thetas size does not match bboxes: " + res);
src/main/java/org/ecocean/ia/MLService.java:256: trailing whitespace.
+        List<String> classNames = Util.jsonArrayToStringList(res.optJSONArray("class_names"));
src/main/java/org/ecocean/ia/MLService.java:257: trailing whitespace.
+        if ((classNames == null) || (classNames.size() != bboxes.length()))
src/main/java/org/ecocean/ia/MLService.java:258: trailing whitespace.
+            throw new IAException("class_names size does not match bboxes: " + res);
src/main/java/org/ecocean/ia/MLService.java:259: trailing whitespace.
+        // FIXME wtf happened to viewpoint??? :)
src/main/java/org/ecocean/ia/MLService.java:260: trailing whitespace.
+        // iterate over bboxes and make annots
src/main/java/org/ecocean/ia/MLService.java:261: trailing whitespace.
+        for (int i = 0; i < bboxes.length(); i++) {
src/main/java/org/ecocean/ia/MLService.java:262: trailing whitespace.
+            List<Double> xywh = Util.jsonArrayToDoubleList(bboxes.optJSONArray(i));
src/main/java/org/ecocean/ia/MLService.java:263: trailing whitespace.
+            if (xywh == null) throw new IAException("error parsing bbox[" + i + "] (null): " + res);
src/main/java/org/ecocean/ia/MLService.java:264: trailing whitespace.
+            if (xywh.size() != 4)
src/main/java/org/ecocean/ia/MLService.java:265: trailing whitespace.
+                throw new IAException("error parsing bbox[" + i + "] (size): " + res);
src/main/java/org/ecocean/ia/MLService.java:266: trailing whitespace.
+            Annotation ann = createAnnotation(xywh, thetas.get(i), classNames.get(i), null);
src/main/java/org/ecocean/ia/MLService.java:267: trailing whitespace.
+            Annotation exists = ma.findAnnotation(ann, true);
src/main/java/org/ecocean/ia/MLService.java:268: trailing whitespace.
+            if (exists != null) { // i guess we just skip this and do not create???
src/main/java/org/ecocean/ia/MLService.java:269: trailing whitespace.
+                System.out.println("[WARNING] MLService.processMediaAssetResults() skipping i=" +
src/main/java/org/ecocean/ia/MLService.java:270: trailing whitespace.
+                    i + " (res=" + res + ") due to existing matching " + exists);
src/main/java/org/ecocean/ia/MLService.java:271: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/MLService.java:272: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MLService.java:273: trailing whitespace.
+            ma.addFeature(ann.getFeature());
src/main/java/org/ecocean/ia/MLService.java:274: trailing whitespace.
+            anns.add(ann);
src/main/java/org/ecocean/ia/MLService.java:275: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:276: trailing whitespace.
+        ma.setDetectionStatus("complete");
src/main/java/org/ecocean/ia/MLService.java:277: trailing whitespace.
+        return anns;
src/main/java/org/ecocean/ia/MLService.java:278: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:279: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:280: trailing whitespace.
+    private Annotation createAnnotation(List<Double> bbox, Double theta, String iaClass,
src/main/java/org/ecocean/ia/MLService.java:281: trailing whitespace.
+        String viewpoint)
src/main/java/org/ecocean/ia/MLService.java:282: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:283: trailing whitespace.
+        if ((bbox == null) || (bbox.size() != 4))
src/main/java/org/ecocean/ia/MLService.java:284: trailing whitespace.
+            throw new IAException("createAnnotation() bad bbox");
src/main/java/org/ecocean/ia/MLService.java:285: trailing whitespace.
+        if ((bbox.get(2) < 1.0d) || (bbox.get(3) < 1.0d))
src/main/java/org/ecocean/ia/MLService.java:286: trailing whitespace.
+            throw new IAException("createAnnotation() bad bbox width/height");
src/main/java/org/ecocean/ia/MLService.java:287: trailing whitespace.
+        JSONObject fparams = new JSONObject();
src/main/java/org/ecocean/ia/MLService.java:288: trailing whitespace.
+        fparams.put("x", bbox.get(0));
src/main/java/org/ecocean/ia/MLService.java:289: trailing whitespace.
+        fparams.put("y", bbox.get(1));
src/main/java/org/ecocean/ia/MLService.java:290: trailing whitespace.
+        fparams.put("width", bbox.get(2));
src/main/java/org/ecocean/ia/MLService.java:291: trailing whitespace.
+        fparams.put("height", bbox.get(3));
src/main/java/org/ecocean/ia/MLService.java:292: trailing whitespace.
+        fparams.put("theta", ((theta == null) ? 0.0d : theta));
src/main/java/org/ecocean/ia/MLService.java:293: trailing whitespace.
+        fparams.put("viewpoint", viewpoint);
src/main/java/org/ecocean/ia/MLService.java:294: trailing whitespace.
+        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
src/main/java/org/ecocean/ia/MLService.java:295: trailing whitespace.
+        Annotation ann = new Annotation(null, ft, iaClass);
src/main/java/org/ecocean/ia/MLService.java:296: trailing whitespace.
+        ann.setViewpoint(viewpoint);
src/main/java/org/ecocean/ia/MLService.java:297: trailing whitespace.
+        return ann;
src/main/java/org/ecocean/ia/MLService.java:298: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:299: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:300: trailing whitespace.
+    public void send(Annotation ann, String taxonomyString, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MLService.java:301: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:302: trailing whitespace.
+        if (ann == null) throw new IAException("null Annotation passed");
src/main/java/org/ecocean/ia/MLService.java:303: trailing whitespace.
+        for (JSONObject conf : getConfigs(taxonomyString)) {
src/main/java/org/ecocean/ia/MLService.java:304: trailing whitespace.
+            JSONObject payload = createPayload(ann, conf);
src/main/java/org/ecocean/ia/MLService.java:305: trailing whitespace.
+            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
src/main/java/org/ecocean/ia/MLService.java:306: trailing whitespace.
+                payload);
src/main/java/org/ecocean/ia/MLService.java:307: trailing whitespace.
+            // got results, now we try to use them
src/main/java/org/ecocean/ia/MLService.java:308: trailing whitespace.
+            JSONObject logRes = new JSONObject(res.toString());
src/main/java/org/ecocean/ia/MLService.java:309: trailing whitespace.
+            if (logRes.optJSONArray("embeddings") != null)
src/main/java/org/ecocean/ia/MLService.java:310: trailing whitespace.
+                logRes.put("embeddings",
src/main/java/org/ecocean/ia/MLService.java:311: trailing whitespace.
+                    "TRUNCATED [length=" + logRes.getJSONArray("embeddings").toString().length() +
src/main/java/org/ecocean/ia/MLService.java:312: trailing whitespace.
+                    "]");
src/main/java/org/ecocean/ia/MLService.java:313: trailing whitespace.
+            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
src/main/java/org/ecocean/ia/MLService.java:314: trailing whitespace.
+                "; RESPONSE => " + logRes);
src/main/java/org/ecocean/ia/MLService.java:315: trailing whitespace.
+            processAnnotationResults(ann, res, myShepherd);
src/main/java/org/ecocean/ia/MLService.java:316: trailing whitespace.
+            System.out.println("MLService.send() process results on " + ann);
src/main/java/org/ecocean/ia/MLService.java:317: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:318: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:319: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:320: trailing whitespace.
+    // not sure what (if anything) we need to return here
src/main/java/org/ecocean/ia/MLService.java:321: trailing whitespace.
+    public void processAnnotationResults(Annotation ann, JSONObject res, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MLService.java:322: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:323: trailing whitespace.
+        if (res == null) throw new IAException("empty results");
src/main/java/org/ecocean/ia/MLService.java:324: trailing whitespace.
+        if (ann == null) throw new IAException("null Annotation");
src/main/java/org/ecocean/ia/MLService.java:325: trailing whitespace.
+        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MLService.java:326: trailing whitespace.
+        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
src/main/java/org/ecocean/ia/MLService.java:327: trailing whitespace.
+        JSONArray embs = res.optJSONArray("embeddings");
src/main/java/org/ecocean/ia/MLService.java:328: trailing whitespace.
+        if (embs == null) throw new IAException("results has no embeddings array: " + res);
src/main/java/org/ecocean/ia/MLService.java:329: trailing whitespace.
+        // in our case we should have one embedding in there
src/main/java/org/ecocean/ia/MLService.java:330: trailing whitespace.
+        if ((embs.length() < 1) || (embs.optJSONArray(0) == null))
src/main/java/org/ecocean/ia/MLService.java:331: trailing whitespace.
+            throw new IAException("results has no embeddings array[0]: " + res);
src/main/java/org/ecocean/ia/MLService.java:332: trailing whitespace.
+        JSONArray vecArr = embs.getJSONArray(0);
src/main/java/org/ecocean/ia/MLService.java:333: trailing whitespace.
+        String[] methodValues = getMethodValues(res);
src/main/java/org/ecocean/ia/MLService.java:334: trailing whitespace.
+        Embedding emb = new Embedding(ann, methodValues[0], methodValues[1], vecArr);
src/main/java/org/ecocean/ia/MLService.java:335: trailing whitespace.
+        // maybe this is unwise? could 2 embeddings *from different methods* have same vectors? TODO
src/main/java/org/ecocean/ia/MLService.java:336: trailing whitespace.
+        Embedding exists = ann.findEmbeddingByVector(emb);
src/main/java/org/ecocean/ia/MLService.java:337: trailing whitespace.
+        if (exists != null) {
src/main/java/org/ecocean/ia/MLService.java:338: trailing whitespace.
+            System.out.println("[WARNING] MLService.processAnnotationResults(): skipping; " + ann +
src/main/java/org/ecocean/ia/MLService.java:339: trailing whitespace.
+                " already has: " + exists);
src/main/java/org/ecocean/ia/MLService.java:340: trailing whitespace.
+            return;
src/main/java/org/ecocean/ia/MLService.java:341: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:342: trailing whitespace.
+        ann.addEmbedding(emb);
src/main/java/org/ecocean/ia/MLService.java:343: trailing whitespace.
+        // FIXME persist or whatever????
src/main/java/org/ecocean/ia/MLService.java:344: trailing whitespace.
+        System.out.println("[DEBUG] MLService.processAnnotationResults(): added " + emb + " to " +
src/main/java/org/ecocean/ia/MLService.java:345: trailing whitespace.
+            ann);
src/main/java/org/ecocean/ia/MLService.java:346: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:347: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:348: trailing whitespace.
+    public static String[] getMethodValues(JSONObject conf) {
src/main/java/org/ecocean/ia/MLService.java:349: trailing whitespace.
+        String[] mv = { null, null };
src/main/java/org/ecocean/ia/MLService.java:350: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:351: trailing whitespace.
+        if (conf == null) return mv;
src/main/java/org/ecocean/ia/MLService.java:352: trailing whitespace.
+        mv[0] = conf.optString("model_id", null);
src/main/java/org/ecocean/ia/MLService.java:353: trailing whitespace.
+        // kinda hack version splitting here but... and i think some might not have dash, like "msv3"  :(
src/main/java/org/ecocean/ia/MLService.java:354: trailing whitespace.
+        if ((mv[0] != null) && mv[0].contains("-")) {
src/main/java/org/ecocean/ia/MLService.java:355: trailing whitespace.
+            String[] parts = mv[0].split("\\-");
src/main/java/org/ecocean/ia/MLService.java:356: trailing whitespace.
+            mv[0] = parts[0];
src/main/java/org/ecocean/ia/MLService.java:357: trailing whitespace.
+            mv[1] = parts[1];
src/main/java/org/ecocean/ia/MLService.java:358: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:359: trailing whitespace.
+        return mv;
src/main/java/org/ecocean/ia/MLService.java:360: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:361: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:362: trailing whitespace.
+    private JSONObject sendPayload(String endpoint, JSONObject payload)
src/main/java/org/ecocean/ia/MLService.java:363: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:364: trailing whitespace.
+        if (endpoint == null) throw new IAException("null api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:365: trailing whitespace.
+        URL url = null;
src/main/java/org/ecocean/ia/MLService.java:366: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MLService.java:367: trailing whitespace.
+            url = new URL(endpoint);
src/main/java/org/ecocean/ia/MLService.java:368: trailing whitespace.
+        } catch (MalformedURLException urlEx) {
src/main/java/org/ecocean/ia/MLService.java:369: trailing whitespace.
+            throw new IAException("api_endpoint url error: " + urlEx);
src/main/java/org/ecocean/ia/MLService.java:370: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:371: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MLService.java:372: trailing whitespace.
+            // throws IOException, java.net.ProtocolException
src/main/java/org/ecocean/ia/MLService.java:373: trailing whitespace.
+            JSONObject res = RestClient.postJSON(url, payload, null);
src/main/java/org/ecocean/ia/MLService.java:374: trailing whitespace.
+            return res;
src/main/java/org/ecocean/ia/MLService.java:375: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/MLService.java:376: trailing whitespace.
+            System.out.println("sendPayload(" + url + ") threw " + ex);
src/main/java/org/ecocean/ia/MLService.java:377: trailing whitespace.
+            ex.printStackTrace();
src/main/java/org/ecocean/ia/MLService.java:378: trailing whitespace.
+            String msg = ex.getMessage();
src/main/java/org/ecocean/ia/MLService.java:379: trailing whitespace.
+            if (msg == null) msg = ""; // safety against NPE
src/main/java/org/ecocean/ia/MLService.java:380: trailing whitespace.
+            if (msg.contains("Connection refused")) {
src/main/java/org/ecocean/ia/MLService.java:381: trailing whitespace.
+                throw new IAException("Connection refused", true, true);
src/main/java/org/ecocean/ia/MLService.java:382: trailing whitespace.
+            } else if (msg.contains("Read timed out")) {
src/main/java/org/ecocean/ia/MLService.java:383: trailing whitespace.
+                throw new IAException("time out", true); // no increment
src/main/java/org/ecocean/ia/MLService.java:384: trailing whitespace.
+            } else if (msg.contains("HTTP error code : 500")) {
src/main/java/org/ecocean/ia/MLService.java:385: trailing whitespace.
+                throw new IAException("500 error", true, true);
src/main/java/org/ecocean/ia/MLService.java:386: trailing whitespace.
+            } else if (msg.contains("HTTP error code : 502")) {
src/main/java/org/ecocean/ia/MLService.java:387: trailing whitespace.
+                throw new IAException("502 error", true); // we requeue, but dont increment this?
src/main/java/org/ecocean/ia/MLService.java:388: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MLService.java:389: trailing whitespace.
+            // default behavior is to retry, but with increment
src/main/java/org/ecocean/ia/MLService.java:390: trailing whitespace.
+            throw new IAException("unhandled exception [will requeue, incremented] on POST: " + ex,
src/main/java/org/ecocean/ia/MLService.java:391: trailing whitespace.
+                    true, true);
src/main/java/org/ecocean/ia/MLService.java:392: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MLService.java:393: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:394: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:395: trailing whitespace.
+    // this is to request detection find an annotation and (optionally) return embedding as well
src/main/java/org/ecocean/ia/MLService.java:396: trailing whitespace.
+    public JSONObject createPayload(MediaAsset ma, JSONObject config)
src/main/java/org/ecocean/ia/MLService.java:397: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:398: trailing whitespace.
+        if ((config == null) || (ma == null))
src/main/java/org/ecocean/ia/MLService.java:399: trailing whitespace.
+            throw new IAException("MLService.createPayload() configuration problem with ma=" + ma +
src/main/java/org/ecocean/ia/MLService.java:400: trailing whitespace.
+                    "; config=" + config);
src/main/java/org/ecocean/ia/MLService.java:401: trailing whitespace.
+        JSONObject payload = new JSONObject(config.toString());
src/main/java/org/ecocean/ia/MLService.java:402: trailing whitespace.
+        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:403: trailing whitespace.
+        payload.put("image_uri", ma.webURL());
src/main/java/org/ecocean/ia/MLService.java:404: trailing whitespace.
+        // FIXME add embedding boolean/args
src/main/java/org/ecocean/ia/MLService.java:405: trailing whitespace.
+        return payload;
src/main/java/org/ecocean/ia/MLService.java:406: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:407: trailing whitespace.
+
src/main/java/org/ecocean/ia/MLService.java:408: trailing whitespace.
+    // this only gets the embedding, from a given (manual or pre-existing) Annotation
src/main/java/org/ecocean/ia/MLService.java:409: trailing whitespace.
+    public JSONObject createPayload(Annotation ann, JSONObject config)
src/main/java/org/ecocean/ia/MLService.java:410: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MLService.java:411: trailing whitespace.
+        if ((config == null) || (ann == null))
src/main/java/org/ecocean/ia/MLService.java:412: trailing whitespace.
+            throw new IAException("MLService.createPayload() configuration problem with ann=" +
src/main/java/org/ecocean/ia/MLService.java:413: trailing whitespace.
+                    ann + "; config=" + config);
src/main/java/org/ecocean/ia/MLService.java:414: trailing whitespace.
+        MediaAsset ma = ann.getMediaAsset();
src/main/java/org/ecocean/ia/MLService.java:415: trailing whitespace.
+        if (ma == null)
src/main/java/org/ecocean/ia/MLService.java:416: trailing whitespace.
+            throw new IAException("MLService.createPayload() no MediaAsset for ann=" + ann);
src/main/java/org/ecocean/ia/MLService.java:417: trailing whitespace.
+        JSONObject payload = new JSONObject(config.toString());
src/main/java/org/ecocean/ia/MLService.java:418: trailing whitespace.
+        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:419: trailing whitespace.
+        payload.put("image_uri", ma.webURL());
src/main/java/org/ecocean/ia/MLService.java:420: trailing whitespace.
+        payload.put("bbox", ann.getBbox());
src/main/java/org/ecocean/ia/MLService.java:421: trailing whitespace.
+        payload.put("theta", ann.getTheta());
src/main/java/org/ecocean/ia/MLService.java:422: trailing whitespace.
+        return payload;
src/main/java/org/ecocean/ia/MLService.java:423: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MLService.java:424: trailing whitespace.
+}
src/main/java/org/ecocean/ia/MatchResult.java:1: trailing whitespace.
+package org.ecocean.ia;
src/main/java/org/ecocean/ia/MatchResult.java:2: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:3: trailing whitespace.
+import java.io.File;
src/main/java/org/ecocean/ia/MatchResult.java:4: trailing whitespace.
+import java.io.IOException;
src/main/java/org/ecocean/ia/MatchResult.java:5: trailing whitespace.
+import java.net.URL;
src/main/java/org/ecocean/ia/MatchResult.java:6: trailing whitespace.
+import java.util.ArrayList;
src/main/java/org/ecocean/ia/MatchResult.java:7: trailing whitespace.
+import java.util.Collections;
src/main/java/org/ecocean/ia/MatchResult.java:8: trailing whitespace.
+import java.util.Comparator;
src/main/java/org/ecocean/ia/MatchResult.java:9: trailing whitespace.
+import java.util.HashMap;
src/main/java/org/ecocean/ia/MatchResult.java:10: trailing whitespace.
+import java.util.HashSet;
src/main/java/org/ecocean/ia/MatchResult.java:11: trailing whitespace.
+import java.util.List;
src/main/java/org/ecocean/ia/MatchResult.java:12: trailing whitespace.
+import java.util.Map;
src/main/java/org/ecocean/ia/MatchResult.java:13: trailing whitespace.
+import java.util.Set;
src/main/java/org/ecocean/ia/MatchResult.java:14: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:15: trailing whitespace.
+import org.json.JSONArray;
src/main/java/org/ecocean/ia/MatchResult.java:16: trailing whitespace.
+import org.json.JSONObject;
src/main/java/org/ecocean/ia/MatchResult.java:17: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:18: trailing whitespace.
+import org.ecocean.api.UploadedFiles;
src/main/java/org/ecocean/ia/MatchResult.java:19: trailing whitespace.
+import org.ecocean.Annotation;
src/main/java/org/ecocean/ia/MatchResult.java:20: trailing whitespace.
+import org.ecocean.Encounter;
src/main/java/org/ecocean/ia/MatchResult.java:21: trailing whitespace.
+import org.ecocean.ia.MLService;
src/main/java/org/ecocean/ia/MatchResult.java:22: trailing whitespace.
+import org.ecocean.ia.Task;
src/main/java/org/ecocean/ia/MatchResult.java:23: trailing whitespace.
+import org.ecocean.identity.IBEISIA;
src/main/java/org/ecocean/ia/MatchResult.java:24: trailing whitespace.
+import org.ecocean.identity.IdentityServiceLog;
src/main/java/org/ecocean/ia/MatchResult.java:25: trailing whitespace.
+import org.ecocean.media.AssetStore;
src/main/java/org/ecocean/ia/MatchResult.java:26: trailing whitespace.
+import org.ecocean.media.Feature;
src/main/java/org/ecocean/ia/MatchResult.java:27: trailing whitespace.
+import org.ecocean.media.MediaAsset;
src/main/java/org/ecocean/ia/MatchResult.java:28: trailing whitespace.
+import org.ecocean.media.URLAssetStore;
src/main/java/org/ecocean/ia/MatchResult.java:29: trailing whitespace.
+import org.ecocean.MarkedIndividual;
src/main/java/org/ecocean/ia/MatchResult.java:30: trailing whitespace.
+import org.ecocean.RestClient;
src/main/java/org/ecocean/ia/MatchResult.java:31: trailing whitespace.
+import org.ecocean.shepherd.core.Shepherd;
src/main/java/org/ecocean/ia/MatchResult.java:32: trailing whitespace.
+import org.ecocean.Util;
src/main/java/org/ecocean/ia/MatchResult.java:33: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:34: trailing whitespace.
+public class MatchResult implements java.io.Serializable {
src/main/java/org/ecocean/ia/MatchResult.java:35: trailing whitespace.
+    private String id;
src/main/java/org/ecocean/ia/MatchResult.java:36: trailing whitespace.
+    private long created;
src/main/java/org/ecocean/ia/MatchResult.java:37: trailing whitespace.
+    private Task task;
src/main/java/org/ecocean/ia/MatchResult.java:38: trailing whitespace.
+    private Set<MatchResultProspect> prospects;
src/main/java/org/ecocean/ia/MatchResult.java:39: trailing whitespace.
+    private Annotation queryAnnotation;
src/main/java/org/ecocean/ia/MatchResult.java:40: trailing whitespace.
+    private int numberCandidates = 0;
src/main/java/org/ecocean/ia/MatchResult.java:41: trailing whitespace.
+    // we store *actual* count here, but they may not all exist
src/main/java/org/ecocean/ia/MatchResult.java:42: trailing whitespace.
+    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
src/main/java/org/ecocean/ia/MatchResult.java:43: trailing whitespace.
+    private int numberProspects = 0;
src/main/java/org/ecocean/ia/MatchResult.java:44: trailing whitespace.
+    // not sure we really *need* true fk link to these annots
src/main/java/org/ecocean/ia/MatchResult.java:45: trailing whitespace.
+    // they might be gone now and will we ever use this?
src/main/java/org/ecocean/ia/MatchResult.java:46: trailing whitespace.
+    // so for now we just populate numberCandidates
src/main/java/org/ecocean/ia/MatchResult.java:47: trailing whitespace.
+    private Set<Annotation> candidates;
src/main/java/org/ecocean/ia/MatchResult.java:48: trailing whitespace.
+    // fallback number to cutoff number of prospects to return
src/main/java/org/ecocean/ia/MatchResult.java:49: trailing whitespace.
+    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
src/main/java/org/ecocean/ia/MatchResult.java:50: trailing whitespace.
+    // number of MatchResultProspects [per type] to actually store (hotspotter
src/main/java/org/ecocean/ia/MatchResult.java:51: trailing whitespace.
+    // results can produce thousands, but storing them all is excessive)
src/main/java/org/ecocean/ia/MatchResult.java:52: trailing whitespace.
+    public static final int MAXIMUM_PROSPECTS_STORED = 500;
src/main/java/org/ecocean/ia/MatchResult.java:53: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:54: trailing whitespace.
+    public MatchResult() {
src/main/java/org/ecocean/ia/MatchResult.java:55: trailing whitespace.
+        id = Util.generateUUID();
src/main/java/org/ecocean/ia/MatchResult.java:56: trailing whitespace.
+        created = System.currentTimeMillis();
src/main/java/org/ecocean/ia/MatchResult.java:57: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:58: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:59: trailing whitespace.
+    public MatchResult(Task task) {
src/main/java/org/ecocean/ia/MatchResult.java:60: trailing whitespace.
+        this();
src/main/java/org/ecocean/ia/MatchResult.java:61: trailing whitespace.
+        this.task = task;
src/main/java/org/ecocean/ia/MatchResult.java:62: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:63: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:64: trailing whitespace.
+    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:65: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:66: trailing whitespace.
+        this();
src/main/java/org/ecocean/ia/MatchResult.java:67: trailing whitespace.
+        this.createFromIdentityServiceLog(isLog, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:68: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:69: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:70: trailing whitespace.
+    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:71: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:72: trailing whitespace.
+        this();
src/main/java/org/ecocean/ia/MatchResult.java:73: trailing whitespace.
+        this.task = task;
src/main/java/org/ecocean/ia/MatchResult.java:74: trailing whitespace.
+        this.createFromJsonResult(jsonResult, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:75: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:76: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:77: trailing whitespace.
+    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
src/main/java/org/ecocean/ia/MatchResult.java:78: trailing whitespace.
+        Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:79: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:80: trailing whitespace.
+        this();
src/main/java/org/ecocean/ia/MatchResult.java:81: trailing whitespace.
+        this.task = task;
src/main/java/org/ecocean/ia/MatchResult.java:82: trailing whitespace.
+        this.numberCandidates = numberCandidates;
src/main/java/org/ecocean/ia/MatchResult.java:83: trailing whitespace.
+        this.setQueryAnnotationFromTask();
src/main/java/org/ecocean/ia/MatchResult.java:84: trailing whitespace.
+        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
src/main/java/org/ecocean/ia/MatchResult.java:85: trailing whitespace.
+        this.populateProspects(annots, false, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:86: trailing whitespace.
+        this.populateProspects(annots, true, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:87: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:88: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:89: trailing whitespace.
+    public int getNumberCandidates() {
src/main/java/org/ecocean/ia/MatchResult.java:90: trailing whitespace.
+        return numberCandidates;
src/main/java/org/ecocean/ia/MatchResult.java:91: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:92: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:93: trailing whitespace.
+    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:94: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:95: trailing whitespace.
+        if (isLog == null) throw new IOException("log passed is null");
src/main/java/org/ecocean/ia/MatchResult.java:96: trailing whitespace.
+        String taskId = isLog.getTaskID();
src/main/java/org/ecocean/ia/MatchResult.java:97: trailing whitespace.
+        this.task = myShepherd.getTask(taskId);
src/main/java/org/ecocean/ia/MatchResult.java:98: trailing whitespace.
+        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
src/main/java/org/ecocean/ia/MatchResult.java:99: trailing whitespace.
+        JSONObject res = isLog.getJsonResult();
src/main/java/org/ecocean/ia/MatchResult.java:100: trailing whitespace.
+        if (res == null) {
src/main/java/org/ecocean/ia/MatchResult.java:101: trailing whitespace.
+            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
src/main/java/org/ecocean/ia/MatchResult.java:102: trailing whitespace.
+                isLog.getStatusJson());
src/main/java/org/ecocean/ia/MatchResult.java:103: trailing whitespace.
+            throw new IOException("could not get json result");
src/main/java/org/ecocean/ia/MatchResult.java:104: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:105: trailing whitespace.
+        createFromJsonResult(res, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:106: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:107: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:108: trailing whitespace.
+    public Annotation setQueryAnnotationFromTask()
src/main/java/org/ecocean/ia/MatchResult.java:109: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:110: trailing whitespace.
+        if (this.task == null)
src/main/java/org/ecocean/ia/MatchResult.java:111: trailing whitespace.
+            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
src/main/java/org/ecocean/ia/MatchResult.java:112: trailing whitespace.
+        int numAnns = this.task.countObjectAnnotations();
src/main/java/org/ecocean/ia/MatchResult.java:113: trailing whitespace.
+        if (numAnns < 1)
src/main/java/org/ecocean/ia/MatchResult.java:114: trailing whitespace.
+            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
src/main/java/org/ecocean/ia/MatchResult.java:115: trailing whitespace.
+        if (numAnns > 1)
src/main/java/org/ecocean/ia/MatchResult.java:116: trailing whitespace.
+            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
src/main/java/org/ecocean/ia/MatchResult.java:117: trailing whitespace.
+                " annotations; using first");
src/main/java/org/ecocean/ia/MatchResult.java:118: trailing whitespace.
+        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
src/main/java/org/ecocean/ia/MatchResult.java:119: trailing whitespace.
+        return this.queryAnnotation;
src/main/java/org/ecocean/ia/MatchResult.java:120: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:121: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:122: trailing whitespace.
+    // json_result section should be passed here
src/main/java/org/ecocean/ia/MatchResult.java:123: trailing whitespace.
+    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:124: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:125: trailing whitespace.
+        if (res == null) throw new IOException("null json_result passed");
src/main/java/org/ecocean/ia/MatchResult.java:126: trailing whitespace.
+        if (res.optJSONArray("query_annot_uuid_list") == null)
src/main/java/org/ecocean/ia/MatchResult.java:127: trailing whitespace.
+            throw new IOException("no query annot list");
src/main/java/org/ecocean/ia/MatchResult.java:128: trailing whitespace.
+        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
src/main/java/org/ecocean/ia/MatchResult.java:129: trailing whitespace.
+            throw new IOException("empty query annot list");
src/main/java/org/ecocean/ia/MatchResult.java:130: trailing whitespace.
+        // for now we are assuming a single query annot. sorrynotsorry.
src/main/java/org/ecocean/ia/MatchResult.java:131: trailing whitespace.
+        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
src/main/java/org/ecocean/ia/MatchResult.java:132: trailing whitespace.
+            "query_annot_uuid_list").optJSONObject(0));
src/main/java/org/ecocean/ia/MatchResult.java:133: trailing whitespace.
+        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:134: trailing whitespace.
+        if (this.queryAnnotation == null)
src/main/java/org/ecocean/ia/MatchResult.java:135: trailing whitespace.
+            throw new IOException("failed to load query annot from id=" + queryAnnotId);
src/main/java/org/ecocean/ia/MatchResult.java:136: trailing whitespace.
+        if (res.optJSONObject("cm_dict") == null)
src/main/java/org/ecocean/ia/MatchResult.java:137: trailing whitespace.
+            throw new IOException("no cm_dict found in " + res);
src/main/java/org/ecocean/ia/MatchResult.java:138: trailing whitespace.
+        // results is the real scores (etc) we are looking for.... finally!
src/main/java/org/ecocean/ia/MatchResult.java:139: trailing whitespace.
+        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
src/main/java/org/ecocean/ia/MatchResult.java:140: trailing whitespace.
+        if (results == null) throw new IOException("no actual results found");
src/main/java/org/ecocean/ia/MatchResult.java:141: trailing whitespace.
+        // see note at top about true annot list of candidates vs number
src/main/java/org/ecocean/ia/MatchResult.java:142: trailing whitespace.
+        if (res.optJSONArray("database_annot_uuid_list") != null)
src/main/java/org/ecocean/ia/MatchResult.java:143: trailing whitespace.
+            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
src/main/java/org/ecocean/ia/MatchResult.java:144: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/MatchResult.java:145: trailing whitespace.
+        annot_score_list <=> dannot_uuid_list
src/main/java/org/ecocean/ia/MatchResult.java:146: trailing whitespace.
+        score_list is for indiv scores but on dannot_uuid_list (same length)
src/main/java/org/ecocean/ia/MatchResult.java:147: trailing whitespace.
+        name_score_list <=> unique_name_uuid_list ???
src/main/java/org/ecocean/ia/MatchResult.java:148: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/MatchResult.java:149: trailing whitespace.
+        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
src/main/java/org/ecocean/ia/MatchResult.java:150: trailing whitespace.
+            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
src/main/java/org/ecocean/ia/MatchResult.java:151: trailing whitespace.
+            results.optString("dannot_extern_reference", null), myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:152: trailing whitespace.
+        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
src/main/java/org/ecocean/ia/MatchResult.java:153: trailing whitespace.
+            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
src/main/java/org/ecocean/ia/MatchResult.java:154: trailing whitespace.
+            results.optString("dannot_extern_reference", null), myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:155: trailing whitespace.
+        System.out.println("[DEBUG] createFromJsonResult() created " + this);
src/main/java/org/ecocean/ia/MatchResult.java:156: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:157: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:158: trailing whitespace.
+    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
src/main/java/org/ecocean/ia/MatchResult.java:159: trailing whitespace.
+        JSONArray externs, String externRef, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:160: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:161: trailing whitespace.
+        if ((annotIds == null) || (scores == null))
src/main/java/org/ecocean/ia/MatchResult.java:162: trailing whitespace.
+            throw new IOException("null annotIds or scores");
src/main/java/org/ecocean/ia/MatchResult.java:163: trailing whitespace.
+        if (annotIds.length() != scores.length())
src/main/java/org/ecocean/ia/MatchResult.java:164: trailing whitespace.
+            throw new IOException("mismatch in size of annotIds/scores");
src/main/java/org/ecocean/ia/MatchResult.java:165: trailing whitespace.
+        if (this.prospects == null)
src/main/java/org/ecocean/ia/MatchResult.java:166: trailing whitespace.
+            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:167: trailing whitespace.
+        int num = 0;
src/main/java/org/ecocean/ia/MatchResult.java:168: trailing whitespace.
+        this.numberProspects += annotIds.length(); // true number of prospects
src/main/java/org/ecocean/ia/MatchResult.java:169: trailing whitespace.
+        for (int i = 0; i < annotIds.length(); i++) {
src/main/java/org/ecocean/ia/MatchResult.java:170: trailing whitespace.
+            double score = scores.optDouble(i, -Double.MAX_VALUE);
src/main/java/org/ecocean/ia/MatchResult.java:171: trailing whitespace.
+            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
src/main/java/org/ecocean/ia/MatchResult.java:172: trailing whitespace.
+            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:173: trailing whitespace.
+            if (ann == null) {
src/main/java/org/ecocean/ia/MatchResult.java:174: trailing whitespace.
+                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
src/main/java/org/ecocean/ia/MatchResult.java:175: trailing whitespace.
+                    "; skipping; score=" + score);
src/main/java/org/ecocean/ia/MatchResult.java:176: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/MatchResult.java:177: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MatchResult.java:178: trailing whitespace.
+            MediaAsset ma = null;
src/main/java/org/ecocean/ia/MatchResult.java:179: trailing whitespace.
+            // we only try if we have a true value in externs[i]
src/main/java/org/ecocean/ia/MatchResult.java:180: trailing whitespace.
+            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
src/main/java/org/ecocean/ia/MatchResult.java:181: trailing whitespace.
+                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:182: trailing whitespace.
+            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
src/main/java/org/ecocean/ia/MatchResult.java:183: trailing whitespace.
+            num++;
src/main/java/org/ecocean/ia/MatchResult.java:184: trailing whitespace.
+            if (num >= MAXIMUM_PROSPECTS_STORED) {
src/main/java/org/ecocean/ia/MatchResult.java:185: trailing whitespace.
+                System.out.println("[DEBUG] hit max (" + MAXIMUM_PROSPECTS_STORED +
src/main/java/org/ecocean/ia/MatchResult.java:186: trailing whitespace.
+                    ") number storable prospects on " + this);
src/main/java/org/ecocean/ia/MatchResult.java:187: trailing whitespace.
+                break;
src/main/java/org/ecocean/ia/MatchResult.java:188: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MatchResult.java:189: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:190: trailing whitespace.
+        return num;
src/main/java/org/ecocean/ia/MatchResult.java:191: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:192: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:193: trailing whitespace.
+    // we just have a list of annots which matched (e.g. via vectors in opensearch)
src/main/java/org/ecocean/ia/MatchResult.java:194: trailing whitespace.
+    // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
src/main/java/org/ecocean/ia/MatchResult.java:195: trailing whitespace.
+    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
src/main/java/org/ecocean/ia/MatchResult.java:196: trailing whitespace.
+    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
src/main/java/org/ecocean/ia/MatchResult.java:197: trailing whitespace.
+        Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:198: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:199: trailing whitespace.
+        if (Util.collectionIsEmptyOrNull(annots)) return 0;
src/main/java/org/ecocean/ia/MatchResult.java:200: trailing whitespace.
+        if (this.prospects == null)
src/main/java/org/ecocean/ia/MatchResult.java:201: trailing whitespace.
+            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:202: trailing whitespace.
+        if (scoreByIndividual) {
src/main/java/org/ecocean/ia/MatchResult.java:203: trailing whitespace.
+            // the scores for these are calculated weighted by indiv count
src/main/java/org/ecocean/ia/MatchResult.java:204: trailing whitespace.
+            _populateProspectsByIndividual(annots, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:205: trailing whitespace.
+        } else {
src/main/java/org/ecocean/ia/MatchResult.java:206: trailing whitespace.
+            // these scores are direct from opensearch
src/main/java/org/ecocean/ia/MatchResult.java:207: trailing whitespace.
+            for (Annotation ann : annots) {
src/main/java/org/ecocean/ia/MatchResult.java:208: trailing whitespace.
+                MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ann, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:209: trailing whitespace.
+                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
src/main/java/org/ecocean/ia/MatchResult.java:210: trailing whitespace.
+                    ma));
src/main/java/org/ecocean/ia/MatchResult.java:211: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MatchResult.java:212: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:213: trailing whitespace.
+        this.numberProspects = this.prospects.size();
src/main/java/org/ecocean/ia/MatchResult.java:214: trailing whitespace.
+        return this.numberProspects;
src/main/java/org/ecocean/ia/MatchResult.java:215: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:216: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:217: trailing whitespace.
+    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:218: trailing whitespace.
+        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
src/main/java/org/ecocean/ia/MatchResult.java:219: trailing whitespace.
+            List<Annotation> >();
src/main/java/org/ecocean/ia/MatchResult.java:220: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:221: trailing whitespace.
+        for (Annotation ann : annots) {
src/main/java/org/ecocean/ia/MatchResult.java:222: trailing whitespace.
+            Encounter enc = ann.findEncounter(myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:223: trailing whitespace.
+            // i think we just ignore if no enc/indiv
src/main/java/org/ecocean/ia/MatchResult.java:224: trailing whitespace.
+            if (enc == null) continue;
src/main/java/org/ecocean/ia/MatchResult.java:225: trailing whitespace.
+            MarkedIndividual indiv = enc.getIndividual();
src/main/java/org/ecocean/ia/MatchResult.java:226: trailing whitespace.
+            if (indiv == null) continue;
src/main/java/org/ecocean/ia/MatchResult.java:227: trailing whitespace.
+            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
src/main/java/org/ecocean/ia/MatchResult.java:228: trailing whitespace.
+            tally.get(indiv).add(ann);
src/main/java/org/ecocean/ia/MatchResult.java:229: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:230: trailing whitespace.
+        if (tally.size() < 1) return; // no individuals i guess?
src/main/java/org/ecocean/ia/MatchResult.java:231: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:232: trailing whitespace.
+        // this sorts by most annots (per indiv) highest to lowest
src/main/java/org/ecocean/ia/MatchResult.java:233: trailing whitespace.
+        List<Map.Entry<MarkedIndividual,
src/main/java/org/ecocean/ia/MatchResult.java:234: trailing whitespace.
+            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
src/main/java/org/ecocean/ia/MatchResult.java:235: trailing whitespace.
+        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
src/main/java/org/ecocean/ia/MatchResult.java:236: trailing whitespace.
+        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
src/main/java/org/ecocean/ia/MatchResult.java:237: trailing whitespace.
+            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
src/main/java/org/ecocean/ia/MatchResult.java:238: trailing whitespace.
+            Map.Entry<MarkedIndividual, List<Annotation> > two) {
src/main/java/org/ecocean/ia/MatchResult.java:239: trailing whitespace.
+                // we reverse order here so we get largest first
src/main/java/org/ecocean/ia/MatchResult.java:240: trailing whitespace.
+                return Integer.compare(two.getValue().size(), one.getValue().size());
src/main/java/org/ecocean/ia/MatchResult.java:241: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MatchResult.java:242: trailing whitespace.
+        });
src/main/java/org/ecocean/ia/MatchResult.java:243: trailing whitespace.
+        int most = sorted.get(0).getValue().size(); // top num of annots
src/main/java/org/ecocean/ia/MatchResult.java:244: trailing whitespace.
+        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
src/main/java/org/ecocean/ia/MatchResult.java:245: trailing whitespace.
+            double score = new Double(ent.getValue().size()) / new Double(most);
src/main/java/org/ecocean/ia/MatchResult.java:246: trailing whitespace.
+            // the ent value (annot List) should always have at least one annot, so we use first one
src/main/java/org/ecocean/ia/MatchResult.java:247: trailing whitespace.
+            MediaAsset ma = createInspectionPairxAsset(this.queryAnnotation, ent.getValue().get(0),
src/main/java/org/ecocean/ia/MatchResult.java:248: trailing whitespace.
+                myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:249: trailing whitespace.
+            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv", ma));
src/main/java/org/ecocean/ia/MatchResult.java:250: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:251: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:252: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:253: trailing whitespace.
+    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:254: trailing whitespace.
+        if (acmId == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:255: trailing whitespace.
+        Annotation found = findAcmIdInTaskAnnotations(acmId);
src/main/java/org/ecocean/ia/MatchResult.java:256: trailing whitespace.
+        if (found != null) return found;
src/main/java/org/ecocean/ia/MatchResult.java:257: trailing whitespace.
+        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
src/main/java/org/ecocean/ia/MatchResult.java:258: trailing whitespace.
+        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
src/main/java/org/ecocean/ia/MatchResult.java:259: trailing whitespace.
+            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
src/main/java/org/ecocean/ia/MatchResult.java:260: trailing whitespace.
+        if ((anns == null) || (anns.size() < 1)) return null;
src/main/java/org/ecocean/ia/MatchResult.java:261: trailing whitespace.
+        return anns.get(0);
src/main/java/org/ecocean/ia/MatchResult.java:262: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:263: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:264: trailing whitespace.
+    private Annotation findAcmIdInTaskAnnotations(String acmId) {
src/main/java/org/ecocean/ia/MatchResult.java:265: trailing whitespace.
+        if ((this.task == null) || (acmId == null)) return null;
src/main/java/org/ecocean/ia/MatchResult.java:266: trailing whitespace.
+        if (!this.task.hasObjectAnnotations()) return null;
src/main/java/org/ecocean/ia/MatchResult.java:267: trailing whitespace.
+        for (Annotation ann : this.task.getObjectAnnotations()) {
src/main/java/org/ecocean/ia/MatchResult.java:268: trailing whitespace.
+            if (acmId.equals(ann.getAcmId())) return ann;
src/main/java/org/ecocean/ia/MatchResult.java:269: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:270: trailing whitespace.
+        return null;
src/main/java/org/ecocean/ia/MatchResult.java:271: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:272: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:273: trailing whitespace.
+    // if it exists, we just return the thing, other wise we attempt to create it
src/main/java/org/ecocean/ia/MatchResult.java:274: trailing whitespace.
+    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
src/main/java/org/ecocean/ia/MatchResult.java:275: trailing whitespace.
+        Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:276: trailing whitespace.
+        if (externRef == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:277: trailing whitespace.
+        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
src/main/java/org/ecocean/ia/MatchResult.java:278: trailing whitespace.
+        url += "&query_annot_uuid=" + this.queryAnnotation.getAcmId();
src/main/java/org/ecocean/ia/MatchResult.java:279: trailing whitespace.
+        url += "&database_annot_uuid=" + annotId;
src/main/java/org/ecocean/ia/MatchResult.java:280: trailing whitespace.
+        url += "&version=heatmask";
src/main/java/org/ecocean/ia/MatchResult.java:281: trailing whitespace.
+        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
src/main/java/org/ecocean/ia/MatchResult.java:282: trailing whitespace.
+        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
src/main/java/org/ecocean/ia/MatchResult.java:283: trailing whitespace.
+            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
src/main/java/org/ecocean/ia/MatchResult.java:284: trailing whitespace.
+        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
src/main/java/org/ecocean/ia/MatchResult.java:285: trailing whitespace.
+        MediaAsset ma = null;
src/main/java/org/ecocean/ia/MatchResult.java:286: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MatchResult.java:287: trailing whitespace.
+            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
src/main/java/org/ecocean/ia/MatchResult.java:288: trailing whitespace.
+            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:289: trailing whitespace.
+            ma.addLabel("matchInspectionHeatmap");
src/main/java/org/ecocean/ia/MatchResult.java:290: trailing whitespace.
+            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
src/main/java/org/ecocean/ia/MatchResult.java:291: trailing whitespace.
+                " and created " + ma);
src/main/java/org/ecocean/ia/MatchResult.java:292: trailing whitespace.
+            tmpFile.delete();
src/main/java/org/ecocean/ia/MatchResult.java:293: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/MatchResult.java:294: trailing whitespace.
+            System.out.println(
src/main/java/org/ecocean/ia/MatchResult.java:295: trailing whitespace.
+                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
src/main/java/org/ecocean/ia/MatchResult.java:296: trailing whitespace.
+                " => " + tmpFile + ": " + ex);
src/main/java/org/ecocean/ia/MatchResult.java:297: trailing whitespace.
+            ex.printStackTrace();
src/main/java/org/ecocean/ia/MatchResult.java:298: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:299: trailing whitespace.
+        return ma;
src/main/java/org/ecocean/ia/MatchResult.java:300: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:301: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:302: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/MatchResult.java:303: trailing whitespace.
+   notes on pairx payload:
src/main/java/org/ecocean/ia/MatchResult.java:304: trailing whitespace.
+   - image1_uris / image2_uris accept URLs or local file paths (as seen by the server)
src/main/java/org/ecocean/ia/MatchResult.java:305: trailing whitespace.
+   - If you provide 1 image1 and N image2s, it compares that single image1 against each image2 (1-to-many)
src/main/java/org/ecocean/ia/MatchResult.java:306: trailing whitespace.
+   - If you provide N of each, they're compared pairwise (N-to-N, max 16 pairs)
src/main/java/org/ecocean/ia/MatchResult.java:307: trailing whitespace.
+   - bb1/bb2 are bounding boxes as [x, y, width, height]
src/main/java/org/ecocean/ia/MatchResult.java:308: trailing whitespace.
+   - visualization_type options: "lines_and_colors", "only_lines", "only_colors"
src/main/java/org/ecocean/ia/MatchResult.java:309: trailing whitespace.
+   - layer_key controls feature depth — earlier layers (e.g. backbone.blocks.1) give point-specific matches, later layers
src/main/java/org/ecocean/ia/MatchResult.java:310: trailing whitespace.
+    (e.g. backbone.blocks.5) give broader region matches
src/main/java/org/ecocean/ia/MatchResult.java:311: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/MatchResult.java:312: trailing whitespace.
+    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
src/main/java/org/ecocean/ia/MatchResult.java:313: trailing whitespace.
+        Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:314: trailing whitespace.
+        if ((ann1 == null) || (ann2 == null)) return null;
src/main/java/org/ecocean/ia/MatchResult.java:315: trailing whitespace.
+        MediaAsset ma1 = ann1.getMediaAsset();
src/main/java/org/ecocean/ia/MatchResult.java:316: trailing whitespace.
+        MediaAsset ma2 = ann2.getMediaAsset();
src/main/java/org/ecocean/ia/MatchResult.java:317: trailing whitespace.
+        if ((ma1 == null) || (ma2 == null)) return null;
src/main/java/org/ecocean/ia/MatchResult.java:318: trailing whitespace.
+        // we need this to find MLService endpoint
src/main/java/org/ecocean/ia/MatchResult.java:319: trailing whitespace.
+        Encounter enc = ann1.findEncounter(myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:320: trailing whitespace.
+        if (enc == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:321: trailing whitespace.
+        JSONObject payload = new JSONObject();
src/main/java/org/ecocean/ia/MatchResult.java:322: trailing whitespace.
+        payload.put("algorithm", "pairx");
src/main/java/org/ecocean/ia/MatchResult.java:323: trailing whitespace.
+        payload.put("visualization_type", "only_colors");
src/main/java/org/ecocean/ia/MatchResult.java:324: trailing whitespace.
+        payload.put("k_colors", 5);
src/main/java/org/ecocean/ia/MatchResult.java:325: trailing whitespace.
+        // payload.put("k_lines", 20);
src/main/java/org/ecocean/ia/MatchResult.java:326: trailing whitespace.
+        payload.put("model_id", "miewid-msv4.1");
src/main/java/org/ecocean/ia/MatchResult.java:327: trailing whitespace.
+        payload.put("crop_bbox", false);
src/main/java/org/ecocean/ia/MatchResult.java:328: trailing whitespace.
+        payload.put("layer_key", "backbone.blocks.3");
src/main/java/org/ecocean/ia/MatchResult.java:329: trailing whitespace.
+        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
src/main/java/org/ecocean/ia/MatchResult.java:330: trailing whitespace.
+        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
src/main/java/org/ecocean/ia/MatchResult.java:331: trailing whitespace.
+        payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
src/main/java/org/ecocean/ia/MatchResult.java:332: trailing whitespace.
+        payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
src/main/java/org/ecocean/ia/MatchResult.java:333: trailing whitespace.
+        // this needs an array of array(s)
src/main/java/org/ecocean/ia/MatchResult.java:334: trailing whitespace.
+        JSONArray tmpArr = new JSONArray();
src/main/java/org/ecocean/ia/MatchResult.java:335: trailing whitespace.
+        tmpArr.put(0, ann1.getBbox());
src/main/java/org/ecocean/ia/MatchResult.java:336: trailing whitespace.
+        payload.put("bb1", tmpArr);
src/main/java/org/ecocean/ia/MatchResult.java:337: trailing whitespace.
+        tmpArr.put(0, ann2.getBbox());
src/main/java/org/ecocean/ia/MatchResult.java:338: trailing whitespace.
+        payload.put("bb2", tmpArr);
src/main/java/org/ecocean/ia/MatchResult.java:339: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:340: trailing whitespace.
+        // get the image data from pairx endpoint
src/main/java/org/ecocean/ia/MatchResult.java:341: trailing whitespace.
+        JSONObject res = null;
src/main/java/org/ecocean/ia/MatchResult.java:342: trailing whitespace.
+        URL pairxUrl = null;
src/main/java/org/ecocean/ia/MatchResult.java:343: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MatchResult.java:344: trailing whitespace.
+            pairxUrl = _getPairxUrl(enc.getTaxonomyString());
src/main/java/org/ecocean/ia/MatchResult.java:345: trailing whitespace.
+            if (pairxUrl == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:346: trailing whitespace.
+            res = RestClient.postJSON(pairxUrl, payload, null);
src/main/java/org/ecocean/ia/MatchResult.java:347: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/MatchResult.java:348: trailing whitespace.
+            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
src/main/java/org/ecocean/ia/MatchResult.java:349: trailing whitespace.
+                " failed: " + ex + "; payload=" + payload);
src/main/java/org/ecocean/ia/MatchResult.java:350: trailing whitespace.
+            ex.printStackTrace();
src/main/java/org/ecocean/ia/MatchResult.java:351: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:352: trailing whitespace.
+        if (res == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:353: trailing whitespace.
+        JSONArray imgs = res.optJSONArray("images");
src/main/java/org/ecocean/ia/MatchResult.java:354: trailing whitespace.
+        if ((imgs == null) || (imgs.length() < 1)) return null;
src/main/java/org/ecocean/ia/MatchResult.java:355: trailing whitespace.
+        String b64 = imgs.optString(0, null);
src/main/java/org/ecocean/ia/MatchResult.java:356: trailing whitespace.
+        if (b64 == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:357: trailing whitespace.
+        // create the asset from base64 data
src/main/java/org/ecocean/ia/MatchResult.java:358: trailing whitespace.
+        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
src/main/java/org/ecocean/ia/MatchResult.java:359: trailing whitespace.
+            " got image data length=" + b64.length());
src/main/java/org/ecocean/ia/MatchResult.java:360: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MatchResult.java:361: trailing whitespace.
+            AssetStore store = AssetStore.getDefault(myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:362: trailing whitespace.
+            JSONObject params = store.createParameters(new File(Util.hashDirectories(this.id) +
src/main/java/org/ecocean/ia/MatchResult.java:363: trailing whitespace.
+                "/pairx-" + this.id + "-" + ann1.getId() + "-" + ann2.getId() + ".png"));
src/main/java/org/ecocean/ia/MatchResult.java:364: trailing whitespace.
+            MediaAsset ma = store.create(params);
src/main/java/org/ecocean/ia/MatchResult.java:365: trailing whitespace.
+            ma.copyInBase64(b64);
src/main/java/org/ecocean/ia/MatchResult.java:366: trailing whitespace.
+            ma.addLabel("matchInspectionPairx");
src/main/java/org/ecocean/ia/MatchResult.java:367: trailing whitespace.
+            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
src/main/java/org/ecocean/ia/MatchResult.java:368: trailing whitespace.
+            myShepherd.getPM().makePersistent(ma);
src/main/java/org/ecocean/ia/MatchResult.java:369: trailing whitespace.
+            return ma;
src/main/java/org/ecocean/ia/MatchResult.java:370: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/MatchResult.java:371: trailing whitespace.
+            System.out.println(
src/main/java/org/ecocean/ia/MatchResult.java:372: trailing whitespace.
+                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
src/main/java/org/ecocean/ia/MatchResult.java:373: trailing whitespace.
+            ex.printStackTrace();
src/main/java/org/ecocean/ia/MatchResult.java:374: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:375: trailing whitespace.
+        return null;
src/main/java/org/ecocean/ia/MatchResult.java:376: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:377: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:378: trailing whitespace.
+    public static URL _getPairxUrl(String txStr)
src/main/java/org/ecocean/ia/MatchResult.java:379: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/MatchResult.java:380: trailing whitespace.
+        if (txStr == null) throw new IOException("passed null taxonomy");
src/main/java/org/ecocean/ia/MatchResult.java:381: trailing whitespace.
+        String urlStr = null;
src/main/java/org/ecocean/ia/MatchResult.java:382: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MatchResult.java:383: trailing whitespace.
+            MLService mls = new MLService();
src/main/java/org/ecocean/ia/MatchResult.java:384: trailing whitespace.
+            List<JSONObject> confs = mls.getConfigs(txStr);
src/main/java/org/ecocean/ia/MatchResult.java:385: trailing whitespace.
+            if (confs.size() < 1) throw new IOException("empty MLService configs for tx=" + txStr);
src/main/java/org/ecocean/ia/MatchResult.java:386: trailing whitespace.
+            urlStr = confs.get(0).optString("api_endpoint", null);
src/main/java/org/ecocean/ia/MatchResult.java:387: trailing whitespace.
+        } catch (IAException ex) {
src/main/java/org/ecocean/ia/MatchResult.java:388: trailing whitespace.
+            throw new IOException(ex);
src/main/java/org/ecocean/ia/MatchResult.java:389: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:390: trailing whitespace.
+        if (urlStr == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:391: trailing whitespace.
+        return new URL(urlStr + "/explain/");
src/main/java/org/ecocean/ia/MatchResult.java:392: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:393: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:394: trailing whitespace.
+    public JSONObject getTaskParameters() {
src/main/java/org/ecocean/ia/MatchResult.java:395: trailing whitespace.
+        if (task == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:396: trailing whitespace.
+        return task.getParameters();
src/main/java/org/ecocean/ia/MatchResult.java:397: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:398: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:399: trailing whitespace.
+    public JSONObject getTaskMatchingSetFilter() {
src/main/java/org/ecocean/ia/MatchResult.java:400: trailing whitespace.
+        if (task == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:401: trailing whitespace.
+        JSONObject params = task.getParameters();
src/main/java/org/ecocean/ia/MatchResult.java:402: trailing whitespace.
+        if (params == null) return null;
src/main/java/org/ecocean/ia/MatchResult.java:403: trailing whitespace.
+        return params.optJSONObject("matchingSetFilter");
src/main/java/org/ecocean/ia/MatchResult.java:404: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:405: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:406: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/MatchResult.java:407: trailing whitespace.
+    see note at top about candidates vs numberCandidates
src/main/java/org/ecocean/ia/MatchResult.java:408: trailing whitespace.
+    public int numberCandidates() {
src/main/java/org/ecocean/ia/MatchResult.java:409: trailing whitespace.
+        return Util.collectionSize(candidates);
src/main/java/org/ecocean/ia/MatchResult.java:410: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:411: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/MatchResult.java:412: trailing whitespace.
+    public int numberProspects() {
src/main/java/org/ecocean/ia/MatchResult.java:413: trailing whitespace.
+        return this.numberProspects;
src/main/java/org/ecocean/ia/MatchResult.java:414: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:415: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:416: trailing whitespace.
+    public Set<String> prospectScoreTypes() {
src/main/java/org/ecocean/ia/MatchResult.java:417: trailing whitespace.
+        Set<String> types = new HashSet<String>();
src/main/java/org/ecocean/ia/MatchResult.java:418: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:419: trailing whitespace.
+        if (numberProspects() == 0) return types;
src/main/java/org/ecocean/ia/MatchResult.java:420: trailing whitespace.
+        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:421: trailing whitespace.
+            types.add(mrp.getType());
src/main/java/org/ecocean/ia/MatchResult.java:422: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:423: trailing whitespace.
+        return types;
src/main/java/org/ecocean/ia/MatchResult.java:424: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:425: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:426: trailing whitespace.
+    // if cutoff < 0 then it will not be truncated at all
src/main/java/org/ecocean/ia/MatchResult.java:427: trailing whitespace.
+    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
src/main/java/org/ecocean/ia/MatchResult.java:428: trailing whitespace.
+        Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:429: trailing whitespace.
+        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:430: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:431: trailing whitespace.
+        if (numberProspects() == 0) return pros;
src/main/java/org/ecocean/ia/MatchResult.java:432: trailing whitespace.
+        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:433: trailing whitespace.
+            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
src/main/java/org/ecocean/ia/MatchResult.java:434: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:435: trailing whitespace.
+        Collections.sort(pros);
src/main/java/org/ecocean/ia/MatchResult.java:436: trailing whitespace.
+        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
src/main/java/org/ecocean/ia/MatchResult.java:437: trailing whitespace.
+        return pros;
src/main/java/org/ecocean/ia/MatchResult.java:438: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:439: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:440: trailing whitespace.
+    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:441: trailing whitespace.
+        JSONObject sj = new JSONObject();
src/main/java/org/ecocean/ia/MatchResult.java:442: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:443: trailing whitespace.
+        for (String type : prospectScoreTypes()) {
src/main/java/org/ecocean/ia/MatchResult.java:444: trailing whitespace.
+            JSONArray jarr = new JSONArray();
src/main/java/org/ecocean/ia/MatchResult.java:445: trailing whitespace.
+            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
src/main/java/org/ecocean/ia/MatchResult.java:446: trailing whitespace.
+                jarr.put(mrp.jsonForApiGet(myShepherd));
src/main/java/org/ecocean/ia/MatchResult.java:447: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MatchResult.java:448: trailing whitespace.
+            sj.put(type, jarr);
src/main/java/org/ecocean/ia/MatchResult.java:449: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:450: trailing whitespace.
+        return sj;
src/main/java/org/ecocean/ia/MatchResult.java:451: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:452: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:453: trailing whitespace.
+    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:454: trailing whitespace.
+        JSONObject rtn = new JSONObject();
src/main/java/org/ecocean/ia/MatchResult.java:455: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:456: trailing whitespace.
+        rtn.put("id", id);
src/main/java/org/ecocean/ia/MatchResult.java:457: trailing whitespace.
+        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
src/main/java/org/ecocean/ia/MatchResult.java:458: trailing whitespace.
+        rtn.put("numberTotalProspects", numberProspects());
src/main/java/org/ecocean/ia/MatchResult.java:459: trailing whitespace.
+        rtn.put("numberCandidates", getNumberCandidates());
src/main/java/org/ecocean/ia/MatchResult.java:460: trailing whitespace.
+        rtn.put("created", Util.millisToISO8601String(created));
src/main/java/org/ecocean/ia/MatchResult.java:461: trailing whitespace.
+        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
src/main/java/org/ecocean/ia/MatchResult.java:462: trailing whitespace.
+        rtn.put("projectIds", projectIds);
src/main/java/org/ecocean/ia/MatchResult.java:463: trailing whitespace.
+        return rtn;
src/main/java/org/ecocean/ia/MatchResult.java:464: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:465: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:466: trailing whitespace.
+    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:467: trailing whitespace.
+        JSONObject aj = new JSONObject();
src/main/java/org/ecocean/ia/MatchResult.java:468: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:469: trailing whitespace.
+        if (ann == null) return aj;
src/main/java/org/ecocean/ia/MatchResult.java:470: trailing whitespace.
+        MediaAsset ma = ann.getMediaAsset();
src/main/java/org/ecocean/ia/MatchResult.java:471: trailing whitespace.
+        // populate bounding box stuff (note: it may reset aj so must be done first)
src/main/java/org/ecocean/ia/MatchResult.java:472: trailing whitespace.
+        if (ann.getFeatures() != null) {
src/main/java/org/ecocean/ia/MatchResult.java:473: trailing whitespace.
+            for (Feature ft : ann.getFeatures()) {
src/main/java/org/ecocean/ia/MatchResult.java:474: trailing whitespace.
+                if (ft.isUnity()) {
src/main/java/org/ecocean/ia/MatchResult.java:475: trailing whitespace.
+                    aj.put("trivial", true);
src/main/java/org/ecocean/ia/MatchResult.java:476: trailing whitespace.
+                    aj.put("x", 0);
src/main/java/org/ecocean/ia/MatchResult.java:477: trailing whitespace.
+                    aj.put("y", 0);
src/main/java/org/ecocean/ia/MatchResult.java:478: trailing whitespace.
+                    // would be weird to be null, but.....
src/main/java/org/ecocean/ia/MatchResult.java:479: trailing whitespace.
+                    if (ma != null) {
src/main/java/org/ecocean/ia/MatchResult.java:480: trailing whitespace.
+                        aj.put("width", (int)ma.getWidth());
src/main/java/org/ecocean/ia/MatchResult.java:481: trailing whitespace.
+                        aj.put("height", (int)ma.getHeight());
src/main/java/org/ecocean/ia/MatchResult.java:482: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/MatchResult.java:483: trailing whitespace.
+                } else {
src/main/java/org/ecocean/ia/MatchResult.java:484: trailing whitespace.
+                    // basically if we have more than one feature, only one wins
src/main/java/org/ecocean/ia/MatchResult.java:485: trailing whitespace.
+                    if (ft.getParameters() != null) aj = ft.getParameters();
src/main/java/org/ecocean/ia/MatchResult.java:486: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/MatchResult.java:487: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MatchResult.java:488: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:489: trailing whitespace.
+        if (ma != null) {
src/main/java/org/ecocean/ia/MatchResult.java:490: trailing whitespace.
+            JSONObject mj = ma.toSimpleJSONObject();
src/main/java/org/ecocean/ia/MatchResult.java:491: trailing whitespace.
+            mj.put("rotationInfo", ma.getRotationInfo());
src/main/java/org/ecocean/ia/MatchResult.java:492: trailing whitespace.
+            aj.put("asset", mj);
src/main/java/org/ecocean/ia/MatchResult.java:493: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:494: trailing whitespace.
+        Encounter enc = ann.findEncounter(myShepherd);
src/main/java/org/ecocean/ia/MatchResult.java:495: trailing whitespace.
+        if (enc != null) {
src/main/java/org/ecocean/ia/MatchResult.java:496: trailing whitespace.
+            JSONObject ej = new JSONObject();
src/main/java/org/ecocean/ia/MatchResult.java:497: trailing whitespace.
+            // TODO add "access" permission value if needed?
src/main/java/org/ecocean/ia/MatchResult.java:498: trailing whitespace.
+            ej.put("id", enc.getId());
src/main/java/org/ecocean/ia/MatchResult.java:499: trailing whitespace.
+            ej.put("taxonomy", enc.getTaxonomyString());
src/main/java/org/ecocean/ia/MatchResult.java:500: trailing whitespace.
+            ej.put("locationId", enc.getLocationID());
src/main/java/org/ecocean/ia/MatchResult.java:501: trailing whitespace.
+            aj.put("encounter", ej);
src/main/java/org/ecocean/ia/MatchResult.java:502: trailing whitespace.
+            MarkedIndividual indiv = enc.getIndividual();
src/main/java/org/ecocean/ia/MatchResult.java:503: trailing whitespace.
+            if (indiv != null) {
src/main/java/org/ecocean/ia/MatchResult.java:504: trailing whitespace.
+                JSONObject ij = new JSONObject();
src/main/java/org/ecocean/ia/MatchResult.java:505: trailing whitespace.
+                ij.put("id", indiv.getId());
src/main/java/org/ecocean/ia/MatchResult.java:506: trailing whitespace.
+                ij.put("taxonomy", indiv.getTaxonomyString());
src/main/java/org/ecocean/ia/MatchResult.java:507: trailing whitespace.
+                ij.put("displayName", indiv.getDisplayName());
src/main/java/org/ecocean/ia/MatchResult.java:508: trailing whitespace.
+                ij.put("nickname", indiv.getNickName());
src/main/java/org/ecocean/ia/MatchResult.java:509: trailing whitespace.
+                ij.put("sex", indiv.getSex());
src/main/java/org/ecocean/ia/MatchResult.java:510: trailing whitespace.
+                ij.put("numberEncounters", indiv.getNumEncounters());
src/main/java/org/ecocean/ia/MatchResult.java:511: trailing whitespace.
+                aj.put("individual", ij);
src/main/java/org/ecocean/ia/MatchResult.java:512: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MatchResult.java:513: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResult.java:514: trailing whitespace.
+        aj.put("id", ann.getId());
src/main/java/org/ecocean/ia/MatchResult.java:515: trailing whitespace.
+        // ml-service migration v2 §commit #11: surface WBIA registration
src/main/java/org/ecocean/ia/MatchResult.java:516: trailing whitespace.
+        // state so the frontend can disable the "Match with HotSpotter"
src/main/java/org/ecocean/ia/MatchResult.java:517: trailing whitespace.
+        // button until WBIA has acknowledged the annotation. tri-state:
src/main/java/org/ecocean/ia/MatchResult.java:518: trailing whitespace.
+        // null = legacy or not-yet-pending; false = pending registration;
src/main/java/org/ecocean/ia/MatchResult.java:519: trailing whitespace.
+        // true = WBIA acknowledged. Frontend treats anything non-true as
src/main/java/org/ecocean/ia/MatchResult.java:520: trailing whitespace.
+        // "HotSpotter not available yet" with a tooltip.
src/main/java/org/ecocean/ia/MatchResult.java:521: trailing whitespace.
+        Boolean wbiaReg = ann.getWbiaRegistered();
src/main/java/org/ecocean/ia/MatchResult.java:522: trailing whitespace.
+        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/main/java/org/ecocean/ia/MatchResult.java:523: trailing whitespace.
+        return aj;
src/main/java/org/ecocean/ia/MatchResult.java:524: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:525: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:526: trailing whitespace.
+    public String toString() {
src/main/java/org/ecocean/ia/MatchResult.java:527: trailing whitespace.
+        String s = "MatchResult " + id;
src/main/java/org/ecocean/ia/MatchResult.java:528: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResult.java:529: trailing whitespace.
+        s += " [" + Util.millisToISO8601String(created) + "]";
src/main/java/org/ecocean/ia/MatchResult.java:530: trailing whitespace.
+        s += " query " + queryAnnotation;
src/main/java/org/ecocean/ia/MatchResult.java:531: trailing whitespace.
+        s += "; numCandidates=" + this.getNumberCandidates();
src/main/java/org/ecocean/ia/MatchResult.java:532: trailing whitespace.
+        s += "; numProspects=" + this.numberProspects();
src/main/java/org/ecocean/ia/MatchResult.java:533: trailing whitespace.
+        s += "; task=" + (task == null ? "null" : task.getId());
src/main/java/org/ecocean/ia/MatchResult.java:534: trailing whitespace.
+        return s;
src/main/java/org/ecocean/ia/MatchResult.java:535: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResult.java:536: trailing whitespace.
+}
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
+    public double getScore() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:38: trailing whitespace.
+        return score;
src/main/java/org/ecocean/ia/MatchResultProspect.java:39: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:40: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:41: trailing whitespace.
+    public String getType() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:42: trailing whitespace.
+        return scoreType;
src/main/java/org/ecocean/ia/MatchResultProspect.java:43: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:44: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:45: trailing whitespace.
+    public boolean isType(String type) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:46: trailing whitespace.
+        if (type == null) return (this.scoreType == null);
src/main/java/org/ecocean/ia/MatchResultProspect.java:47: trailing whitespace.
+        return type.equals(this.scoreType);
src/main/java/org/ecocean/ia/MatchResultProspect.java:48: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:49: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:50: trailing whitespace.
+    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:51: trailing whitespace.
+        // if we have no projects to filter on, we consider this to be in it
src/main/java/org/ecocean/ia/MatchResultProspect.java:52: trailing whitespace.
+        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
src/main/java/org/ecocean/ia/MatchResultProspect.java:53: trailing whitespace.
+        if (annotation == null) return false;
src/main/java/org/ecocean/ia/MatchResultProspect.java:54: trailing whitespace.
+        Encounter enc = annotation.findEncounter(myShepherd);
src/main/java/org/ecocean/ia/MatchResultProspect.java:55: trailing whitespace.
+        if (enc == null) return false;
src/main/java/org/ecocean/ia/MatchResultProspect.java:56: trailing whitespace.
+        return enc.isInProjects(projectIds, myShepherd);
src/main/java/org/ecocean/ia/MatchResultProspect.java:57: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:58: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:59: trailing whitespace.
+    public String toString() {
src/main/java/org/ecocean/ia/MatchResultProspect.java:60: trailing whitespace.
+        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
src/main/java/org/ecocean/ia/MatchResultProspect.java:61: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:62: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:63: trailing whitespace.
+    public JSONObject jsonForApiGet(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:64: trailing whitespace.
+        JSONObject rtn = new JSONObject();
src/main/java/org/ecocean/ia/MatchResultProspect.java:65: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:66: trailing whitespace.
+        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
src/main/java/org/ecocean/ia/MatchResultProspect.java:67: trailing whitespace.
+        rtn.put("score", score);
src/main/java/org/ecocean/ia/MatchResultProspect.java:68: trailing whitespace.
+        // skipping scoreType since this is currently only used filtered by scoreType already
src/main/java/org/ecocean/ia/MatchResultProspect.java:69: trailing whitespace.
+        if (asset != null) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:70: trailing whitespace.
+            JSONObject aj = asset.toSimpleJSONObject();
src/main/java/org/ecocean/ia/MatchResultProspect.java:71: trailing whitespace.
+            aj.put("url", asset.webURL()); // we have no "safe" url
src/main/java/org/ecocean/ia/MatchResultProspect.java:72: trailing whitespace.
+            rtn.put("asset", aj);
src/main/java/org/ecocean/ia/MatchResultProspect.java:73: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MatchResultProspect.java:74: trailing whitespace.
+        return rtn;
src/main/java/org/ecocean/ia/MatchResultProspect.java:75: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:76: trailing whitespace.
+
src/main/java/org/ecocean/ia/MatchResultProspect.java:77: trailing whitespace.
+    // used in sorting
src/main/java/org/ecocean/ia/MatchResultProspect.java:78: trailing whitespace.
+    @Override public int compareTo(MatchResultProspect other) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:79: trailing whitespace.
+        // we invert this so higher score is first
src/main/java/org/ecocean/ia/MatchResultProspect.java:80: trailing whitespace.
+        int comp = Double.compare(other.score, this.score);
src/main/java/org/ecocean/ia/MatchResultProspect.java:81: trailing whitespace.
+        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
src/main/java/org/ecocean/ia/MatchResultProspect.java:82: trailing whitespace.
+        // ordering (otherwise tied scores come back random order), so we use annot id
src/main/java/org/ecocean/ia/MatchResultProspect.java:83: trailing whitespace.
+        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
src/main/java/org/ecocean/ia/MatchResultProspect.java:84: trailing whitespace.
+            return this.annotation.getId().compareTo(other.annotation.getId());
src/main/java/org/ecocean/ia/MatchResultProspect.java:85: trailing whitespace.
+        // scores are *not* equal, so we just let comparison stand as-is
src/main/java/org/ecocean/ia/MatchResultProspect.java:86: trailing whitespace.
+        return comp;
src/main/java/org/ecocean/ia/MatchResultProspect.java:87: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MatchResultProspect.java:88: trailing whitespace.
+}
src/main/java/org/ecocean/ia/MlServiceClient.java:1: trailing whitespace.
+package org.ecocean.ia;
src/main/java/org/ecocean/ia/MlServiceClient.java:2: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:3: trailing whitespace.
+import java.io.IOException;
src/main/java/org/ecocean/ia/MlServiceClient.java:4: trailing whitespace.
+import java.net.MalformedURLException;
src/main/java/org/ecocean/ia/MlServiceClient.java:5: trailing whitespace.
+import java.net.SocketTimeoutException;
src/main/java/org/ecocean/ia/MlServiceClient.java:6: trailing whitespace.
+import java.net.URL;
src/main/java/org/ecocean/ia/MlServiceClient.java:7: trailing whitespace.
+import java.util.regex.Matcher;
src/main/java/org/ecocean/ia/MlServiceClient.java:8: trailing whitespace.
+import java.util.regex.Pattern;
src/main/java/org/ecocean/ia/MlServiceClient.java:9: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:10: trailing whitespace.
+import org.json.JSONArray;
src/main/java/org/ecocean/ia/MlServiceClient.java:11: trailing whitespace.
+import org.json.JSONObject;
src/main/java/org/ecocean/ia/MlServiceClient.java:12: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:13: trailing whitespace.
+import org.ecocean.RestClient;
src/main/java/org/ecocean/ia/MlServiceClient.java:14: trailing whitespace.
+import org.ecocean.Util;
src/main/java/org/ecocean/ia/MlServiceClient.java:15: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:16: trailing whitespace.
+/**
src/main/java/org/ecocean/ia/MlServiceClient.java:17: trailing whitespace.
+ * HTTP-only wrapper around ml-service ({@code /pipeline/} and {@code /extract/}
src/main/java/org/ecocean/ia/MlServiceClient.java:18: trailing whitespace.
+ * endpoints). Validates the response shape against the v2 contract. No
src/main/java/org/ecocean/ia/MlServiceClient.java:19: trailing whitespace.
+ * Shepherd, no DB; just HTTP + JSON validation.
src/main/java/org/ecocean/ia/MlServiceClient.java:20: trailing whitespace.
+ *
src/main/java/org/ecocean/ia/MlServiceClient.java:21: trailing whitespace.
+ * <p>Migration plan v2 §commit #8. Used by {@link
src/main/java/org/ecocean/ia/MlServiceClient.java:22: trailing whitespace.
+ * org.ecocean.ia.MlServiceProcessor} (commit #9). Tests directly via
src/main/java/org/ecocean/ia/MlServiceClient.java:23: trailing whitespace.
+ * {@code MlServiceClientTest}.</p>
src/main/java/org/ecocean/ia/MlServiceClient.java:24: trailing whitespace.
+ *
src/main/java/org/ecocean/ia/MlServiceClient.java:25: trailing whitespace.
+ * <h3>Retry classification (matches v2 plan §Failure ladder):</h3>
src/main/java/org/ecocean/ia/MlServiceClient.java:26: trailing whitespace.
+ * <ul>
src/main/java/org/ecocean/ia/MlServiceClient.java:27: trailing whitespace.
+ *   <li>{@link SocketTimeoutException} or message contains "timed out" →
src/main/java/org/ecocean/ia/MlServiceClient.java:28: trailing whitespace.
+ *       IAException retryable=true, increment=false (timeout doesn't imply
src/main/java/org/ecocean/ia/MlServiceClient.java:29: trailing whitespace.
+ *       overload).</li>
src/main/java/org/ecocean/ia/MlServiceClient.java:30: trailing whitespace.
+ *   <li>Connection refused / 502 / 503 / 504 / 5xx → retryable=true,
src/main/java/org/ecocean/ia/MlServiceClient.java:31: trailing whitespace.
+ *       increment=true.</li>
src/main/java/org/ecocean/ia/MlServiceClient.java:32: trailing whitespace.
+ *   <li>429 (rate-limited) → retryable=true, increment=true so the client
src/main/java/org/ecocean/ia/MlServiceClient.java:33: trailing whitespace.
+ *       backs off.</li>
src/main/java/org/ecocean/ia/MlServiceClient.java:34: trailing whitespace.
+ *   <li>Other 4xx, parse failure, {@code success=false} response → retryable
src/main/java/org/ecocean/ia/MlServiceClient.java:35: trailing whitespace.
+ *       =false; mark task error.</li>
src/main/java/org/ecocean/ia/MlServiceClient.java:36: trailing whitespace.
+ * </ul>
src/main/java/org/ecocean/ia/MlServiceClient.java:37: trailing whitespace.
+ *
src/main/java/org/ecocean/ia/MlServiceClient.java:38: trailing whitespace.
+ * <p>RestClient throws {@code "HTTP error code = NNN"} (literally with {@code
src/main/java/org/ecocean/ia/MlServiceClient.java:39: trailing whitespace.
+ * =}). The classifier accepts both {@code "= NNN"} and {@code ": NNN"}
src/main/java/org/ecocean/ia/MlServiceClient.java:40: trailing whitespace.
+ * spellings to be defensive against any future RestClient refactor.</p>
src/main/java/org/ecocean/ia/MlServiceClient.java:41: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/MlServiceClient.java:42: trailing whitespace.
+public class MlServiceClient {
src/main/java/org/ecocean/ia/MlServiceClient.java:43: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:44: trailing whitespace.
+    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;
src/main/java/org/ecocean/ia/MlServiceClient.java:45: trailing whitespace.
+    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;
src/main/java/org/ecocean/ia/MlServiceClient.java:46: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:47: trailing whitespace.
+    // Matches "HTTP error code = 502" or "HTTP error code : 502", capturing
src/main/java/org/ecocean/ia/MlServiceClient.java:48: trailing whitespace.
+    // the status code as group 1.
src/main/java/org/ecocean/ia/MlServiceClient.java:49: trailing whitespace.
+    private static final Pattern HTTP_CODE_PATTERN =
src/main/java/org/ecocean/ia/MlServiceClient.java:50: trailing whitespace.
+        Pattern.compile("HTTP error code\\s*[=:]\\s*(\\d{3})");
src/main/java/org/ecocean/ia/MlServiceClient.java:51: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:52: trailing whitespace.
+    private final int connectTimeoutMs;
src/main/java/org/ecocean/ia/MlServiceClient.java:53: trailing whitespace.
+    private final int readTimeoutMs;
src/main/java/org/ecocean/ia/MlServiceClient.java:54: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:55: trailing whitespace.
+    public MlServiceClient() {
src/main/java/org/ecocean/ia/MlServiceClient.java:56: trailing whitespace.
+        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
src/main/java/org/ecocean/ia/MlServiceClient.java:57: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:58: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:59: trailing whitespace.
+    public MlServiceClient(int connectTimeoutMs, int readTimeoutMs) {
src/main/java/org/ecocean/ia/MlServiceClient.java:60: trailing whitespace.
+        this.connectTimeoutMs = connectTimeoutMs;
src/main/java/org/ecocean/ia/MlServiceClient.java:61: trailing whitespace.
+        this.readTimeoutMs = readTimeoutMs;
src/main/java/org/ecocean/ia/MlServiceClient.java:62: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:63: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:64: trailing whitespace.
+    // ---------------------------------------------------------------------
src/main/java/org/ecocean/ia/MlServiceClient.java:65: trailing whitespace.
+    // Public API
src/main/java/org/ecocean/ia/MlServiceClient.java:66: trailing whitespace.
+    // ---------------------------------------------------------------------
src/main/java/org/ecocean/ia/MlServiceClient.java:67: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:68: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/MlServiceClient.java:69: trailing whitespace.
+     * POSTs to {@code apiEndpoint/pipeline/} with the predict/classify/extract/
src/main/java/org/ecocean/ia/MlServiceClient.java:70: trailing whitespace.
+     * orientation model IDs from {@code config}. Returns the validated response.
src/main/java/org/ecocean/ia/MlServiceClient.java:71: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/MlServiceClient.java:72: trailing whitespace.
+     * @param apiEndpoint base URL of ml-service (no trailing slash required)
src/main/java/org/ecocean/ia/MlServiceClient.java:73: trailing whitespace.
+     * @param imageUri    URL or local path of the image to process
src/main/java/org/ecocean/ia/MlServiceClient.java:74: trailing whitespace.
+     * @param config      a single {@code _mlservice_conf} entry from IA.json
src/main/java/org/ecocean/ia/MlServiceClient.java:75: trailing whitespace.
+     * @return validated response JSON ({@code success:true, results:[...]})
src/main/java/org/ecocean/ia/MlServiceClient.java:76: trailing whitespace.
+     * @throws IAException on network failure or response-validation failure;
src/main/java/org/ecocean/ia/MlServiceClient.java:77: trailing whitespace.
+     *         {@code shouldRequeue()} and {@code getCode()} carry the
src/main/java/org/ecocean/ia/MlServiceClient.java:78: trailing whitespace.
+     *         classification. Codes: {@code TIMEOUT}, {@code NETWORK},
src/main/java/org/ecocean/ia/MlServiceClient.java:79: trailing whitespace.
+     *         {@code SERVER_ERROR}, {@code RATE_LIMITED},
src/main/java/org/ecocean/ia/MlServiceClient.java:80: trailing whitespace.
+     *         {@code CLIENT_ERROR}, {@code SUCCESS_FALSE}, {@code INVALID}.
src/main/java/org/ecocean/ia/MlServiceClient.java:81: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/MlServiceClient.java:82: trailing whitespace.
+    public JSONObject pipeline(String apiEndpoint, String imageUri, JSONObject config)
src/main/java/org/ecocean/ia/MlServiceClient.java:83: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MlServiceClient.java:84: trailing whitespace.
+        JSONObject payload = buildPipelinePayload(imageUri, config);
src/main/java/org/ecocean/ia/MlServiceClient.java:85: trailing whitespace.
+        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/pipeline/"),
src/main/java/org/ecocean/ia/MlServiceClient.java:86: trailing whitespace.
+            payload);
src/main/java/org/ecocean/ia/MlServiceClient.java:87: trailing whitespace.
+        validatePipelineResponse(response, config.optInt("embedding_dimension", 0));
src/main/java/org/ecocean/ia/MlServiceClient.java:88: trailing whitespace.
+        return response;
src/main/java/org/ecocean/ia/MlServiceClient.java:89: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:90: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:91: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/MlServiceClient.java:92: trailing whitespace.
+     * POSTs to {@code apiEndpoint/extract/}. Used for manual annotations
src/main/java/org/ecocean/ia/MlServiceClient.java:93: trailing whitespace.
+     * (user-drawn bbox; no detection step needed).
src/main/java/org/ecocean/ia/MlServiceClient.java:94: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/MlServiceClient.java:95: trailing whitespace.
+     * @throws IAException same contract as {@link #pipeline}.
src/main/java/org/ecocean/ia/MlServiceClient.java:96: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/MlServiceClient.java:97: trailing whitespace.
+    public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
src/main/java/org/ecocean/ia/MlServiceClient.java:98: trailing whitespace.
+        double theta, JSONObject config)
src/main/java/org/ecocean/ia/MlServiceClient.java:99: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MlServiceClient.java:100: trailing whitespace.
+        JSONObject payload = buildExtractPayload(imageUri, bbox, theta, config);
src/main/java/org/ecocean/ia/MlServiceClient.java:101: trailing whitespace.
+        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/extract/"),
src/main/java/org/ecocean/ia/MlServiceClient.java:102: trailing whitespace.
+            payload);
src/main/java/org/ecocean/ia/MlServiceClient.java:103: trailing whitespace.
+        validateExtractResponse(response, config.optInt("embedding_dimension", 0));
src/main/java/org/ecocean/ia/MlServiceClient.java:104: trailing whitespace.
+        return response;
src/main/java/org/ecocean/ia/MlServiceClient.java:105: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:106: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:107: trailing whitespace.
+    // ---------------------------------------------------------------------
src/main/java/org/ecocean/ia/MlServiceClient.java:108: trailing whitespace.
+    // Internal helpers (package-visible for unit tests)
src/main/java/org/ecocean/ia/MlServiceClient.java:109: trailing whitespace.
+    // ---------------------------------------------------------------------
src/main/java/org/ecocean/ia/MlServiceClient.java:110: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:111: trailing whitespace.
+    static String joinEndpoint(String base, String path) {
src/main/java/org/ecocean/ia/MlServiceClient.java:112: trailing whitespace.
+        if (base == null) return path;
src/main/java/org/ecocean/ia/MlServiceClient.java:113: trailing whitespace.
+        String trimmed = base.replaceAll("/+$", "");
src/main/java/org/ecocean/ia/MlServiceClient.java:114: trailing whitespace.
+        return trimmed + path;
src/main/java/org/ecocean/ia/MlServiceClient.java:115: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:116: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:117: trailing whitespace.
+    static JSONObject buildPipelinePayload(String imageUri, JSONObject config) {
src/main/java/org/ecocean/ia/MlServiceClient.java:118: trailing whitespace.
+        JSONObject p = new JSONObject();
src/main/java/org/ecocean/ia/MlServiceClient.java:119: trailing whitespace.
+        p.put("image_uri", imageUri);
src/main/java/org/ecocean/ia/MlServiceClient.java:120: trailing whitespace.
+        if (config != null) {
src/main/java/org/ecocean/ia/MlServiceClient.java:121: trailing whitespace.
+            if (config.has("predict_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:122: trailing whitespace.
+                p.put("predict_model_id", config.opt("predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:123: trailing whitespace.
+            if (config.has("classify_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:124: trailing whitespace.
+                p.put("classify_model_id", config.opt("classify_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:125: trailing whitespace.
+            if (config.has("extract_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:126: trailing whitespace.
+                p.put("extract_model_id", config.opt("extract_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:127: trailing whitespace.
+            if (config.has("orientation_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:128: trailing whitespace.
+                p.put("orientation_model_id", config.opt("orientation_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:129: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:130: trailing whitespace.
+        return p;
src/main/java/org/ecocean/ia/MlServiceClient.java:131: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:132: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:133: trailing whitespace.
+    static JSONObject buildExtractPayload(String imageUri, double[] bbox, double theta,
src/main/java/org/ecocean/ia/MlServiceClient.java:134: trailing whitespace.
+        JSONObject config) {
src/main/java/org/ecocean/ia/MlServiceClient.java:135: trailing whitespace.
+        JSONObject p = new JSONObject();
src/main/java/org/ecocean/ia/MlServiceClient.java:136: trailing whitespace.
+        p.put("image_uri", imageUri);
src/main/java/org/ecocean/ia/MlServiceClient.java:137: trailing whitespace.
+        if (config != null && config.has("extract_model_id")) {
src/main/java/org/ecocean/ia/MlServiceClient.java:138: trailing whitespace.
+            p.put("extract_model_id", config.opt("extract_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:139: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:140: trailing whitespace.
+        if (bbox != null) {
src/main/java/org/ecocean/ia/MlServiceClient.java:141: trailing whitespace.
+            JSONArray b = new JSONArray();
src/main/java/org/ecocean/ia/MlServiceClient.java:142: trailing whitespace.
+            for (double v : bbox) b.put(v);
src/main/java/org/ecocean/ia/MlServiceClient.java:143: trailing whitespace.
+            p.put("bbox", b);
src/main/java/org/ecocean/ia/MlServiceClient.java:144: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:145: trailing whitespace.
+        p.put("theta", theta);
src/main/java/org/ecocean/ia/MlServiceClient.java:146: trailing whitespace.
+        return p;
src/main/java/org/ecocean/ia/MlServiceClient.java:147: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:148: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:149: trailing whitespace.
+    static void validatePipelineResponse(JSONObject response, int expectedDim)
src/main/java/org/ecocean/ia/MlServiceClient.java:150: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MlServiceClient.java:151: trailing whitespace.
+        if (response == null)
src/main/java/org/ecocean/ia/MlServiceClient.java:152: trailing whitespace.
+            throw new IAException("INVALID", "/pipeline/ returned null", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:153: trailing whitespace.
+        if (!response.optBoolean("success", false))
src/main/java/org/ecocean/ia/MlServiceClient.java:154: trailing whitespace.
+            throw new IAException("SUCCESS_FALSE",
src/main/java/org/ecocean/ia/MlServiceClient.java:155: trailing whitespace.
+                "/pipeline/ returned success=false: " + response, false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:156: trailing whitespace.
+        JSONArray results = response.optJSONArray("results");
src/main/java/org/ecocean/ia/MlServiceClient.java:157: trailing whitespace.
+        if (results == null)
src/main/java/org/ecocean/ia/MlServiceClient.java:158: trailing whitespace.
+            throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:159: trailing whitespace.
+                "/pipeline/ response missing 'results' array: " + response, false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:160: trailing whitespace.
+        // Zero detections is a valid response. Each present result must be
src/main/java/org/ecocean/ia/MlServiceClient.java:161: trailing whitespace.
+        // structurally complete; we reject the whole response on any partial
src/main/java/org/ecocean/ia/MlServiceClient.java:162: trailing whitespace.
+        // result rather than persist a subset.
src/main/java/org/ecocean/ia/MlServiceClient.java:163: trailing whitespace.
+        for (int i = 0; i < results.length(); i++) {
src/main/java/org/ecocean/ia/MlServiceClient.java:164: trailing whitespace.
+            JSONObject r = results.optJSONObject(i);
src/main/java/org/ecocean/ia/MlServiceClient.java:165: trailing whitespace.
+            if (r == null)
src/main/java/org/ecocean/ia/MlServiceClient.java:166: trailing whitespace.
+                throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:167: trailing whitespace.
+                    "/pipeline/ results[" + i + "] is not an object", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:168: trailing whitespace.
+            validateBbox(r.optJSONArray("bbox"), i);
src/main/java/org/ecocean/ia/MlServiceClient.java:169: trailing whitespace.
+            // theta must be present AND finite. Default-on-missing (e.g.
src/main/java/org/ecocean/ia/MlServiceClient.java:170: trailing whitespace.
+            // optDouble("theta", 0.0)) would accept a malformed result and
src/main/java/org/ecocean/ia/MlServiceClient.java:171: trailing whitespace.
+            // persist a fabricated orientation. Require presence.
src/main/java/org/ecocean/ia/MlServiceClient.java:172: trailing whitespace.
+            if (!r.has("theta"))
src/main/java/org/ecocean/ia/MlServiceClient.java:173: trailing whitespace.
+                throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:174: trailing whitespace.
+                    "/pipeline/ results[" + i + "] missing theta", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:175: trailing whitespace.
+            double theta = r.optDouble("theta", Double.NaN);
src/main/java/org/ecocean/ia/MlServiceClient.java:176: trailing whitespace.
+            if (!isFiniteDouble(theta))
src/main/java/org/ecocean/ia/MlServiceClient.java:177: trailing whitespace.
+                throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:178: trailing whitespace.
+                    "/pipeline/ results[" + i + "] theta non-finite", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:179: trailing whitespace.
+            validateEmbeddingField(r, "embedding", expectedDim, "results[" + i + "]");
src/main/java/org/ecocean/ia/MlServiceClient.java:180: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:181: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:182: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:183: trailing whitespace.
+    static void validateExtractResponse(JSONObject response, int expectedDim)
src/main/java/org/ecocean/ia/MlServiceClient.java:184: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MlServiceClient.java:185: trailing whitespace.
+        if (response == null)
src/main/java/org/ecocean/ia/MlServiceClient.java:186: trailing whitespace.
+            throw new IAException("INVALID", "/extract/ returned null", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:187: trailing whitespace.
+        if (!response.optBoolean("success", false))
src/main/java/org/ecocean/ia/MlServiceClient.java:188: trailing whitespace.
+            throw new IAException("SUCCESS_FALSE",
src/main/java/org/ecocean/ia/MlServiceClient.java:189: trailing whitespace.
+                "/extract/ returned success=false: " + response, false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:190: trailing whitespace.
+        validateEmbeddingField(response, "embedding", expectedDim, "response");
src/main/java/org/ecocean/ia/MlServiceClient.java:191: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:192: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:193: trailing whitespace.
+    private static void validateBbox(JSONArray bbox, int idx)
src/main/java/org/ecocean/ia/MlServiceClient.java:194: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MlServiceClient.java:195: trailing whitespace.
+        if (bbox == null || bbox.length() != 4)
src/main/java/org/ecocean/ia/MlServiceClient.java:196: trailing whitespace.
+            throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:197: trailing whitespace.
+                "/pipeline/ results[" + idx + "] bbox must be a 4-element array", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:198: trailing whitespace.
+        for (int j = 0; j < 4; j++) {
src/main/java/org/ecocean/ia/MlServiceClient.java:199: trailing whitespace.
+            double v = bbox.optDouble(j, Double.NaN);
src/main/java/org/ecocean/ia/MlServiceClient.java:200: trailing whitespace.
+            if (!isFiniteDouble(v))
src/main/java/org/ecocean/ia/MlServiceClient.java:201: trailing whitespace.
+                throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:202: trailing whitespace.
+                    "/pipeline/ results[" + idx + "] bbox[" + j + "] non-finite", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:203: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:204: trailing whitespace.
+        if (bbox.optDouble(2) < 1.0 || bbox.optDouble(3) < 1.0)
src/main/java/org/ecocean/ia/MlServiceClient.java:205: trailing whitespace.
+            throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:206: trailing whitespace.
+                "/pipeline/ results[" + idx + "] bbox width/height must be >= 1", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:207: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:208: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:209: trailing whitespace.
+    private static void validateEmbeddingField(JSONObject parent, String fieldName,
src/main/java/org/ecocean/ia/MlServiceClient.java:210: trailing whitespace.
+        int expectedDim, String context)
src/main/java/org/ecocean/ia/MlServiceClient.java:211: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MlServiceClient.java:212: trailing whitespace.
+        JSONArray emb = parent.optJSONArray(fieldName);
src/main/java/org/ecocean/ia/MlServiceClient.java:213: trailing whitespace.
+        if (emb == null)
src/main/java/org/ecocean/ia/MlServiceClient.java:214: trailing whitespace.
+            throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:215: trailing whitespace.
+                context + " missing '" + fieldName + "' array", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:216: trailing whitespace.
+        if (expectedDim > 0 && emb.length() != expectedDim)
src/main/java/org/ecocean/ia/MlServiceClient.java:217: trailing whitespace.
+            throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:218: trailing whitespace.
+                context + " embedding length " + emb.length() + " != expected " + expectedDim,
src/main/java/org/ecocean/ia/MlServiceClient.java:219: trailing whitespace.
+                false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:220: trailing whitespace.
+        if (emb.length() == 0)
src/main/java/org/ecocean/ia/MlServiceClient.java:221: trailing whitespace.
+            throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:222: trailing whitespace.
+                context + " embedding array is empty", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:223: trailing whitespace.
+        for (int j = 0; j < emb.length(); j++) {
src/main/java/org/ecocean/ia/MlServiceClient.java:224: trailing whitespace.
+            double v = emb.optDouble(j, Double.NaN);
src/main/java/org/ecocean/ia/MlServiceClient.java:225: trailing whitespace.
+            if (!isFiniteDouble(v))
src/main/java/org/ecocean/ia/MlServiceClient.java:226: trailing whitespace.
+                throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:227: trailing whitespace.
+                    context + " embedding[" + j + "] non-finite", false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:228: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:229: trailing whitespace.
+        String modelId = parent.optString("embedding_model_id", null);
src/main/java/org/ecocean/ia/MlServiceClient.java:230: trailing whitespace.
+        String modelVer = parent.optString("embedding_model_version", null);
src/main/java/org/ecocean/ia/MlServiceClient.java:231: trailing whitespace.
+        if (!Util.stringExists(modelId) || !Util.stringExists(modelVer))
src/main/java/org/ecocean/ia/MlServiceClient.java:232: trailing whitespace.
+            throw new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:233: trailing whitespace.
+                context + " missing embedding_model_id or embedding_model_version",
src/main/java/org/ecocean/ia/MlServiceClient.java:234: trailing whitespace.
+                false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:235: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:236: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:237: trailing whitespace.
+    private static boolean isFiniteDouble(double v) {
src/main/java/org/ecocean/ia/MlServiceClient.java:238: trailing whitespace.
+        return !Double.isNaN(v) && !Double.isInfinite(v);
src/main/java/org/ecocean/ia/MlServiceClient.java:239: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:240: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:241: trailing whitespace.
+    private JSONObject postWithClassification(String url, JSONObject payload)
src/main/java/org/ecocean/ia/MlServiceClient.java:242: trailing whitespace.
+    throws IAException {
src/main/java/org/ecocean/ia/MlServiceClient.java:243: trailing whitespace.
+        URL u;
src/main/java/org/ecocean/ia/MlServiceClient.java:244: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MlServiceClient.java:245: trailing whitespace.
+            u = new URL(url);
src/main/java/org/ecocean/ia/MlServiceClient.java:246: trailing whitespace.
+        } catch (MalformedURLException ex) {
src/main/java/org/ecocean/ia/MlServiceClient.java:247: trailing whitespace.
+            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:248: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:249: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/MlServiceClient.java:250: trailing whitespace.
+            return RestClient.postJSON(u, payload, null, connectTimeoutMs, readTimeoutMs);
src/main/java/org/ecocean/ia/MlServiceClient.java:251: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/MlServiceClient.java:252: trailing whitespace.
+            throw classifyHttpFailure(ex, url);
src/main/java/org/ecocean/ia/MlServiceClient.java:253: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:254: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:255: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceClient.java:256: trailing whitespace.
+    /** Classify a RestClient throw into the v2 failure-ladder buckets. */
src/main/java/org/ecocean/ia/MlServiceClient.java:257: trailing whitespace.
+    static IAException classifyHttpFailure(Exception ex, String url) {
src/main/java/org/ecocean/ia/MlServiceClient.java:258: trailing whitespace.
+        // Detect timeout primarily by exception type; fall back to message
src/main/java/org/ecocean/ia/MlServiceClient.java:259: trailing whitespace.
+        // sniffing for environments where the cause chain is flattened.
src/main/java/org/ecocean/ia/MlServiceClient.java:260: trailing whitespace.
+        for (Throwable t = ex; t != null; t = t.getCause()) {
src/main/java/org/ecocean/ia/MlServiceClient.java:261: trailing whitespace.
+            if (t instanceof SocketTimeoutException) {
src/main/java/org/ecocean/ia/MlServiceClient.java:262: trailing whitespace.
+                return new IAException("TIMEOUT",
src/main/java/org/ecocean/ia/MlServiceClient.java:263: trailing whitespace.
+                    "ml-service timeout on " + url + ": " + ex.getMessage(), true, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:264: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MlServiceClient.java:265: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:266: trailing whitespace.
+        String msg = ex.getMessage() == null ? "" : ex.getMessage();
src/main/java/org/ecocean/ia/MlServiceClient.java:267: trailing whitespace.
+        if (msg.contains("timed out")) {
src/main/java/org/ecocean/ia/MlServiceClient.java:268: trailing whitespace.
+            return new IAException("TIMEOUT",
src/main/java/org/ecocean/ia/MlServiceClient.java:269: trailing whitespace.
+                "ml-service timeout on " + url + ": " + msg, true, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:270: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:271: trailing whitespace.
+        // Connection refused and Connection reset are both transient peer-side
src/main/java/org/ecocean/ia/MlServiceClient.java:272: trailing whitespace.
+        // conditions; retry with increment so the back-off counter advances.
src/main/java/org/ecocean/ia/MlServiceClient.java:273: trailing whitespace.
+        if (msg.contains("Connection refused") || msg.contains("Connection reset")) {
src/main/java/org/ecocean/ia/MlServiceClient.java:274: trailing whitespace.
+            return new IAException("NETWORK",
src/main/java/org/ecocean/ia/MlServiceClient.java:275: trailing whitespace.
+                "ml-service connection error on " + url + ": " + msg, true, true);
src/main/java/org/ecocean/ia/MlServiceClient.java:276: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:277: trailing whitespace.
+        // Parse failures from RestClient.postJSON: the response was a 200 OK
src/main/java/org/ecocean/ia/MlServiceClient.java:278: trailing whitespace.
+        // but the body wasn't valid JSON. That's a contract violation by
src/main/java/org/ecocean/ia/MlServiceClient.java:279: trailing whitespace.
+        // ml-service, not a network issue. Classify as INVALID, non-retryable.
src/main/java/org/ecocean/ia/MlServiceClient.java:280: trailing whitespace.
+        if (msg.contains("could not convert postRaw()")) {
src/main/java/org/ecocean/ia/MlServiceClient.java:281: trailing whitespace.
+            return new IAException("INVALID",
src/main/java/org/ecocean/ia/MlServiceClient.java:282: trailing whitespace.
+                "ml-service returned non-JSON 200 on " + url + ": " + msg, false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:283: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:284: trailing whitespace.
+        Matcher m = HTTP_CODE_PATTERN.matcher(msg);
src/main/java/org/ecocean/ia/MlServiceClient.java:285: trailing whitespace.
+        if (m.find()) {
src/main/java/org/ecocean/ia/MlServiceClient.java:286: trailing whitespace.
+            int statusCode;
src/main/java/org/ecocean/ia/MlServiceClient.java:287: trailing whitespace.
+            try {
src/main/java/org/ecocean/ia/MlServiceClient.java:288: trailing whitespace.
+                statusCode = Integer.parseInt(m.group(1));
src/main/java/org/ecocean/ia/MlServiceClient.java:289: trailing whitespace.
+            } catch (NumberFormatException nfe) {
src/main/java/org/ecocean/ia/MlServiceClient.java:290: trailing whitespace.
+                statusCode = 0;
src/main/java/org/ecocean/ia/MlServiceClient.java:291: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MlServiceClient.java:292: trailing whitespace.
+            // 408 (Request Timeout) — typically emitted by a proxy/LB in front
src/main/java/org/ecocean/ia/MlServiceClient.java:293: trailing whitespace.
+            // of ml-service; treat like a normal timeout (retry, no increment).
src/main/java/org/ecocean/ia/MlServiceClient.java:294: trailing whitespace.
+            if (statusCode == 408) {
src/main/java/org/ecocean/ia/MlServiceClient.java:295: trailing whitespace.
+                return new IAException("TIMEOUT",
src/main/java/org/ecocean/ia/MlServiceClient.java:296: trailing whitespace.
+                    "ml-service 408 on " + url, true, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:297: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MlServiceClient.java:298: trailing whitespace.
+            if (statusCode == 429) {
src/main/java/org/ecocean/ia/MlServiceClient.java:299: trailing whitespace.
+                return new IAException("RATE_LIMITED",
src/main/java/org/ecocean/ia/MlServiceClient.java:300: trailing whitespace.
+                    "ml-service rate-limited (429) on " + url, true, true);
src/main/java/org/ecocean/ia/MlServiceClient.java:301: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MlServiceClient.java:302: trailing whitespace.
+            if (statusCode == 502 || statusCode == 503 || statusCode == 504) {
src/main/java/org/ecocean/ia/MlServiceClient.java:303: trailing whitespace.
+                return new IAException("NETWORK",
src/main/java/org/ecocean/ia/MlServiceClient.java:304: trailing whitespace.
+                    "ml-service " + statusCode + " on " + url, true, true);
src/main/java/org/ecocean/ia/MlServiceClient.java:305: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MlServiceClient.java:306: trailing whitespace.
+            if (statusCode >= 500 && statusCode < 600) {
src/main/java/org/ecocean/ia/MlServiceClient.java:307: trailing whitespace.
+                return new IAException("SERVER_ERROR",
src/main/java/org/ecocean/ia/MlServiceClient.java:308: trailing whitespace.
+                    "ml-service " + statusCode + " on " + url, true, true);
src/main/java/org/ecocean/ia/MlServiceClient.java:309: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MlServiceClient.java:310: trailing whitespace.
+            if (statusCode >= 400 && statusCode < 500) {
src/main/java/org/ecocean/ia/MlServiceClient.java:311: trailing whitespace.
+                return new IAException("CLIENT_ERROR",
src/main/java/org/ecocean/ia/MlServiceClient.java:312: trailing whitespace.
+                    "ml-service " + statusCode + " on " + url + " (non-retryable)",
src/main/java/org/ecocean/ia/MlServiceClient.java:313: trailing whitespace.
+                    false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:314: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/MlServiceClient.java:315: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceClient.java:316: trailing whitespace.
+        // Unrecognized; treat as non-retryable to avoid spinning.
src/main/java/org/ecocean/ia/MlServiceClient.java:317: trailing whitespace.
+        return new IAException("NETWORK",
src/main/java/org/ecocean/ia/MlServiceClient.java:318: trailing whitespace.
+            "ml-service request failed on " + url + ": " + msg, false, false);
src/main/java/org/ecocean/ia/MlServiceClient.java:319: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceClient.java:320: trailing whitespace.
+}
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:1: trailing whitespace.
+package org.ecocean.ia;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:2: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:3: trailing whitespace.
+import java.util.ArrayList;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:4: trailing whitespace.
+import java.util.Collections;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:5: trailing whitespace.
+import java.util.List;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:6: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:7: trailing whitespace.
+/**
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:8: trailing whitespace.
+ * Typed outcome of a single ml-service queue job, returned by
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:9: trailing whitespace.
+ * {@code MlServiceProcessor.process(...)}. The seven {@link Kind} values
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:10: trailing whitespace.
+ * distinguish operationally-different terminal states so the caller can
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:11: trailing whitespace.
+ * record clear status/statusDetails on the parent Task and react
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:12: trailing whitespace.
+ * appropriately (e.g. enqueue a deferred match when {@code OK}).
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:13: trailing whitespace.
+ *
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:14: trailing whitespace.
+ * <p>Migration plan v2 §commit #8.</p>
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:15: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:16: trailing whitespace.
+public final class MlServiceJobOutcome {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:17: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:18: trailing whitespace.
+    public enum Kind {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:19: trailing whitespace.
+        /** Job persisted at least one annotation/embedding; matching can proceed. */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:20: trailing whitespace.
+        OK,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:21: trailing whitespace.
+        /**
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:22: trailing whitespace.
+         * Job completed but ml-service returned zero detections. Not an error
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:23: trailing whitespace.
+         * — the asset is genuinely empty / has no detectable subject — but no
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:24: trailing whitespace.
+         * downstream match work is needed.
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:25: trailing whitespace.
+         */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:26: trailing whitespace.
+        OK_ZERO_DETECTIONS,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:27: trailing whitespace.
+        /**
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:28: trailing whitespace.
+         * The target (Encounter / MediaAsset) was deleted before/during the
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:29: trailing whitespace.
+         * job. Terminal-drop with no error; the inactivity-timeout watchdog
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:30: trailing whitespace.
+         * must not flip it to "error".
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:31: trailing whitespace.
+         */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:32: trailing whitespace.
+        STALE,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:33: trailing whitespace.
+        /**
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:34: trailing whitespace.
+         * ml-service returned a response that failed structural validation
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:35: trailing whitespace.
+         * (malformed embedding length, non-finite floats, missing fields).
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:36: trailing whitespace.
+         * Non-retryable; mark task error.
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:37: trailing whitespace.
+         */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:38: trailing whitespace.
+        ERROR_VALIDATION,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:39: trailing whitespace.
+        /**
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:40: trailing whitespace.
+         * Network failure that exceeded retry budget or was non-retryable
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:41: trailing whitespace.
+         * from the start (4xx). Mark task error.
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:42: trailing whitespace.
+         */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:43: trailing whitespace.
+        ERROR_NETWORK,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:44: trailing whitespace.
+        /**
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:45: trailing whitespace.
+         * Database write failed at the persistence step (e.g. FK violation,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:46: trailing whitespace.
+         * idempotency-index conflict that wasn't a no-op).
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:47: trailing whitespace.
+         */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:48: trailing whitespace.
+        ERROR_PERSIST,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:49: trailing whitespace.
+        /**
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:50: trailing whitespace.
+         * Transient network error; the queue framework has been told to
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:51: trailing whitespace.
+         * requeue this job. Caller should not finalize the task — the next
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:52: trailing whitespace.
+         * worker pass will pick it up.
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:53: trailing whitespace.
+         */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:54: trailing whitespace.
+        REQUEUE
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:55: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:56: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:57: trailing whitespace.
+    private final Kind kind;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:58: trailing whitespace.
+    private final String code;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:59: trailing whitespace.
+    private final String message;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:60: trailing whitespace.
+    private final List<String> persistedAnnotationIds;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:61: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:62: trailing whitespace.
+    private MlServiceJobOutcome(Kind kind, String code, String message,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:63: trailing whitespace.
+        List<String> persistedAnnotationIds) {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:64: trailing whitespace.
+        this.kind = kind;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:65: trailing whitespace.
+        this.code = code;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:66: trailing whitespace.
+        this.message = message;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:67: trailing whitespace.
+        // Defensive copy + unmodifiable wrap so the caller can't mutate our
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:68: trailing whitespace.
+        // state after construction (Codex code-review guidance).
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:69: trailing whitespace.
+        if (persistedAnnotationIds == null || persistedAnnotationIds.isEmpty()) {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:70: trailing whitespace.
+            this.persistedAnnotationIds = Collections.emptyList();
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:71: trailing whitespace.
+        } else {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:72: trailing whitespace.
+            this.persistedAnnotationIds = Collections.unmodifiableList(
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:73: trailing whitespace.
+                new ArrayList<String>(persistedAnnotationIds));
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:74: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:75: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:76: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:77: trailing whitespace.
+    // --- Factories ---------------------------------------------------------
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:78: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:79: trailing whitespace.
+    public static MlServiceJobOutcome ok(List<String> persistedAnnotationIds) {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:80: trailing whitespace.
+        return new MlServiceJobOutcome(Kind.OK, null, null, persistedAnnotationIds);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:81: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:82: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:83: trailing whitespace.
+    public static MlServiceJobOutcome okZeroDetections() {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:84: trailing whitespace.
+        return new MlServiceJobOutcome(Kind.OK_ZERO_DETECTIONS, null, null, null);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:85: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:86: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:87: trailing whitespace.
+    public static MlServiceJobOutcome stale(String reason) {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:88: trailing whitespace.
+        return new MlServiceJobOutcome(Kind.STALE, "STALE", reason, null);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:89: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:90: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:91: trailing whitespace.
+    public static MlServiceJobOutcome validationError(String code, String message) {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:92: trailing whitespace.
+        return new MlServiceJobOutcome(Kind.ERROR_VALIDATION,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:93: trailing whitespace.
+            code == null ? "INVALID" : code, message, null);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:94: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:95: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:96: trailing whitespace.
+    public static MlServiceJobOutcome networkError(String code, String message) {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:97: trailing whitespace.
+        return new MlServiceJobOutcome(Kind.ERROR_NETWORK,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:98: trailing whitespace.
+            code == null ? "NETWORK" : code, message, null);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:99: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:100: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:101: trailing whitespace.
+    public static MlServiceJobOutcome persistError(String code, String message) {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:102: trailing whitespace.
+        return new MlServiceJobOutcome(Kind.ERROR_PERSIST,
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:103: trailing whitespace.
+            code == null ? "PERSIST" : code, message, null);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:104: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:105: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:106: trailing whitespace.
+    public static MlServiceJobOutcome requeue() {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:107: trailing whitespace.
+        return new MlServiceJobOutcome(Kind.REQUEUE, null, null, null);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:108: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:109: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:110: trailing whitespace.
+    // --- Accessors ---------------------------------------------------------
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:111: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:112: trailing whitespace.
+    public Kind getKind() {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:113: trailing whitespace.
+        return kind;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:114: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:115: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:116: trailing whitespace.
+    public String getCode() {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:117: trailing whitespace.
+        return code;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:118: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:119: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:120: trailing whitespace.
+    public String getMessage() {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:121: trailing whitespace.
+        return message;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:122: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:123: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:124: trailing whitespace.
+    public List<String> getPersistedAnnotationIds() {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:125: trailing whitespace.
+        return persistedAnnotationIds;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:126: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:127: trailing whitespace.
+
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:128: trailing whitespace.
+    /** True iff this outcome represents a terminal error (not OK*, STALE, or REQUEUE). */
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:129: trailing whitespace.
+    public boolean isError() {
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:130: trailing whitespace.
+        return kind == Kind.ERROR_VALIDATION
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:131: trailing whitespace.
+            || kind == Kind.ERROR_NETWORK
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:132: trailing whitespace.
+            || kind == Kind.ERROR_PERSIST;
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:133: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:134: trailing whitespace.
+}

exec
/bin/bash -lc 'rg -n "waitAndRunMatch|runDeferredMatch|runMatchProspects|GIVE_UP|defer|MatchVisibilityGate" src/test/java/org/ecocean/ia/MlServiceProcessorTest.java src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 306ms:
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:16: * Pure-logic coverage of {@link MatchVisibilityGateImpl}'s static
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:17: * helpers and {@link MatchVisibilityGate.GateOutcome} factory
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:18: * methods. The full {@link MatchVisibilityGateImpl#gateForBatch}
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:25:class MatchVisibilityGateImplTest {
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:31:        MatchVisibilityGate.GateOutcome g =
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:32:            MatchVisibilityGate.GateOutcome.ready(3, firstDeferred);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:33:        assertEquals(MatchVisibilityGate.Kind.READY, g.kind);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:40:    @Test void deferOutcome_carriesReason() {
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:42:        MatchVisibilityGate.GateOutcome g =
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:43:            MatchVisibilityGate.GateOutcome.defer(2, firstDeferred,
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:45:        assertEquals(MatchVisibilityGate.Kind.DEFER, g.kind);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:51:        MatchVisibilityGate.GateOutcome g =
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:52:            MatchVisibilityGate.GateOutcome.giveUp(7, firstDeferred,
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:54:        assertEquals(MatchVisibilityGate.Kind.GIVE_UP, g.kind);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:62:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:67:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:72:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:77:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:82:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:87:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:92:        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:97:        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:102:        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:107:        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:112:        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(null, null));
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:122:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:129:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:134:        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:140:        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:147:        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:175:        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:192:        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:201:        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:208:        assertNull(MatchVisibilityGateImpl.findChildTaskForSibling(null, ma));
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:213:        assertNull(MatchVisibilityGateImpl.findChildTaskForSibling(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:225:        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:233:        // Bound is 12 minutes — keeps GIVE_UP reachable inside the
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:237:            MatchVisibilityGate.MAX_DEFER_AGE_MILLIS);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:251:        MatchVisibilityGateImpl gate = new MatchVisibilityGateImpl("ctx0");
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:253:            - (MatchVisibilityGate.MAX_DEFER_AGE_MILLIS + 1000L);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:254:        MatchVisibilityGate.GateOutcome g = gate.gateForBatch(
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:260:        assertEquals(MatchVisibilityGate.Kind.GIVE_UP, g.kind);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:264:            "GIVE_UP reason should mention MAX_DEFER_AGE_MILLIS: " + g.reason);

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java | sed -n '230,270p'" in /mnt/c/Wildbook-clean2
 succeeded in 224ms:
   230	    // --- MAX_DEFER_AGE_MILLIS interface constant -------------------------
   231	
   232	    @Test void maxDeferAgeMatchesTwelveMinutes() {
   233	        // Bound is 12 minutes — keeps GIVE_UP reachable inside the
   234	        // IAGateway.requeueJob 30-retry / 30s-per-attempt window
   235	        // (Codex round-3 Blocker).
   236	        assertEquals(12L * 60L * 1000L,
   237	            MatchVisibilityGate.MAX_DEFER_AGE_MILLIS);
   238	    }
   239	
   240	    // --- gateForBatch public-method coverage -----------------------------
   241	    //
   242	    // Covers the load-bearing path that runs before Shepherd is opened
   243	    // (the age-out check at the top of gateForBatch). Tests requiring
   244	    // Shepherd or OpenSearch interaction are covered by the live
   245	    // integration harness on dev deployments — same precedent as the
   246	    // C6/C9 paths.
   247	
   248	    @Test void gateForBatch_returnsGiveUp_whenAgeExceedsMaxDeferAge() {
   249	        // No Shepherd/OS is invoked because the age-out check is the
   250	        // first thing gateForBatch does.
   251	        MatchVisibilityGateImpl gate = new MatchVisibilityGateImpl("ctx0");
   252	        long longAgo = System.currentTimeMillis()
   253	            - (MatchVisibilityGate.MAX_DEFER_AGE_MILLIS + 1000L);
   254	        MatchVisibilityGate.GateOutcome g = gate.gateForBatch(
   255	            java.util.Arrays.asList("ann-1"),
   256	            null,                  // childTaskId — ignored because age fires first
   257	            new org.json.JSONObject().put("method", "miewid-msv4.1").put("version", "4.1"),
   258	            5,                     // attempt
   259	            longAgo);
   260	        assertEquals(MatchVisibilityGate.Kind.GIVE_UP, g.kind);
   261	        assertEquals(5, g.attempt);
   262	        assertEquals(longAgo, g.firstDeferredAt);
   263	        assertTrue(g.reason != null && g.reason.contains("MAX_DEFER_AGE_MILLIS"),
   264	            "GIVE_UP reason should mention MAX_DEFER_AGE_MILLIS: " + g.reason);
   265	    }
   266	}

exec
/bin/bash -lc 'rg -n "IOException|OpenSearch|VISIBILITY_TIMEOUT_MS" src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
 succeeded in 212ms:
3:import java.io.IOException;
13:import org.ecocean.OpenSearch;
36:    private static final long VISIBILITY_TIMEOUT_MS = 5000L;

exec
/bin/bash -lc 'rg -n "waitForVisibility|waitForAnnotationMatchableIds" src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchVisibilityTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 196ms:
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:553:    // Stronger visibility predicate than waitForVisibility for the annotation
src/main/java/org/ecocean/OpenSearch.java:568:    // Like waitForVisibility: _refresh on entry, then exponential-backoff
src/main/java/org/ecocean/OpenSearch.java:571:    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:584:                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
src/main/java/org/ecocean/OpenSearch.java:612:    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '490,635p'" in /mnt/c/Wildbook-clean2
 succeeded in 219ms:
   490	    // 100ms, double, cap 1s) until count >= |normalized ids| OR the total
   491	    // wait reaches timeoutMs. Returns true on visible-success, false on
   492	    // timeout. Caller decides what to do on false (e.g. enqueue a deferred-
   493	    // match job rather than match against a partial index).
   494	    //
   495	    // Does NOT try to drain the Wildbook IndexingManager queue. That queue
   496	    // may contain unrelated entities; queue-depth zero doesn't imply the
   497	    // specific IDs are queryable. Polling visibility IS the correctness gate.
   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
   499	        long timeoutMs)
   500	    throws IOException {
   501	        if (!isValidIndexName(indexName))
   502	            throw new IOException("invalid index name: " + indexName);
   503	        if (ids == null || ids.isEmpty()) return true;
   504	
   505	        // Normalize: drop nulls + duplicates so the count comparison is
   506	        // against the true number of distinct documents we expect to see.
   507	        Set<String> targetIds = new LinkedHashSet<String>();
   508	        for (String id : ids) {
   509	            if (id != null) targetIds.add(id);
   510	        }
   511	        if (targetIds.isEmpty()) return true;
   512	
   513	        if (skipAutoIndexing()) {
   514	            System.out.println(
   515	                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
   516	                "— every poll will return zero hits regardless of wait time.");
   517	        }
   518	
   519	        indexRefresh(indexName);
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '635,710p'" in /mnt/c/Wildbook-clean2
 succeeded in 217ms:
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
   671	        searchRequest.setJsonEntity(cleanedQuery.toString());
   672	        JSONObject res = new JSONObject();
   673	        try {
   674	            res = new JSONObject(getRestResponse(searchRequest));
   675	        } catch (Exception ex) {
   676	            System.out.println("queryCount() on index " + indexName + " using query=" + query +
   677	                " failed with: " + ex);
   678	            ex.printStackTrace();
   679	            throw new IOException("queryCount() failed");
   680	        }
   681	        return res.optInt("count", -1);
   682	    }
   683	
   684	    public Map<String, Long> getAllVersions(String indexName)
   685	    throws IOException {
   686	        Map<String, Long> versions = new HashMap<String, Long>();
   687	        boolean reachedEnd = false;
   688	        JSONObject query = new JSONObject("{\"sort\":[{\"version\": \"asc\"}]}");
   689	        JSONObject res = queryRawScroll(indexName, query, 2000);
   690	
   691	        while (!reachedEnd) {
   692	            JSONObject outerHits = res.optJSONObject("hits");
   693	            if (outerHits == null) throw new IOException("outer hits failed");
   694	            JSONArray hits = outerHits.optJSONArray("hits");
   695	            if (hits == null) throw new IOException("hits failed");
   696	            if (hits.length() < 1) {
   697	                reachedEnd = true;
   698	            } else {
   699	                for (int i = 0; i < hits.length(); i++) {
   700	                    String id = hits.optJSONObject(i).optString("_id", "__FAIL__");
   701	                    Long version = hits.optJSONObject(i).optJSONObject("_source").optLong("version",
   702	                        -999L);
   703	                    versions.put(id, version);
   704	                }
   705	                // continue with next scroll...
   706	                query = new JSONObject();
   707	                query.put("_scroll_id", res.optString("_scroll_id", "__FAIL__"));
   708	                res = queryRawScroll(query);
   709	            }
   710	        }

exec
/bin/bash -lc 'rg -n "annotationId|mlServiceV2" src/main/java src/test/java | head -n 80' in /mnt/c/Wildbook-clean2
 succeeded in 406ms:
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:58:        jo.put("mlServiceV2", true);
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:60:        jo.put("annotationIds", new JSONArray().put("ann-1").put("ann-2"));
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:89:        // mlServiceV2==true; MlServiceProcessor branches deferred only
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:99:        assertTrue(payload.optBoolean("mlServiceV2", false),
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:100:            "missing mlServiceV2: " + payload);
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:153:        JSONArray ids = payload.optJSONArray("annotationIds");
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:44:            .put("mlServiceV2", true)
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:24:        assertEquals("ann-1", dto.annotationId);
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:60:        assertEquals("ann-3", dto.annotationId);
src/main/java/org/ecocean/identity/IBEISIA.java:2576:                "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
src/main/java/org/ecocean/identity/IBEISIA.java:2580:        JSONArray annIds = arg.optJSONArray("annotationIds");
src/main/java/org/ecocean/identity/IBEISIA.java:2583:                "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
src/main/java/org/ecocean/identity/IBEISIA.java:2659:                j.put("annotationIds", new JSONArray());
src/main/java/org/ecocean/identity/IBEISIA.java:2662:            map.get(name).getJSONArray("annotationIds").put(aid);
src/main/java/org/ecocean/export/EncounterImageExportFile.java:49:                // if numAnnotationsPerId is 1, then the annotationIdx will be 1 as we write the second image
src/main/java/org/ecocean/ia/Task.java:478:            j.put("annotationIds", jo);
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:64:        int annotationId = 1;
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:71:                annotationsArray.put(buildAnnotationObject(ann, annotationId++, imgId,
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:408:    private JSONObject buildAnnotationObject(Annotation ann, int annotationId, int imageId,
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:413:        annJson.put("id", annotationId);
src/main/java/org/ecocean/ia/MLService.java:116:        data.put("annotationIds", annIds);
src/main/java/org/ecocean/ia/MLService.java:144:                ids = jobData.optJSONArray("annotationIds");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:447:        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:464:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:468:            this.annotationId        = annotationId;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:491:            this(annotationId, annotationAcmId, mediaAssetAcmId, bbox, theta,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:683:        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:752:                dto.annotationId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:760:                dto.annotationId + ": " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:767:        if (known.contains(dto.annotationId) ||
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:776:                " for ann=" + dto.annotationId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:785:                dto.annotationId + ": " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:795:            validateForcedResponse(dto.annotationId, rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:798:                dto.annotationId + ": " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:833:                dto.annotationId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:838:                dto.annotationId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844:                dto.annotationId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:852:                dto.annotationId + ": " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:863:                " for ann=" + dto.annotationId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:872:                dto.annotationId + " ma=" + dto.mediaAssetAcmId + ": " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:884:                dto.annotationId + " ma=" + dto.mediaAssetAcmId + ": " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:30: * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
src/main/java/org/ecocean/ia/MlServiceProcessor.java:87:        if (jobData.has("annotationId")) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:88:            String annId = jobData.optString("annotationId", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:            "neither mediaAssetId nor annotationId in payload");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:129:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:132:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:164:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:167:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:314:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:340:                    annotationIds.add(existing.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:382:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:388:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:402:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:417:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:                return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:426:            annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:428:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:438:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:443:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:459:            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:462:            return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:464:            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:465:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:473:            return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:481:        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:484:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:494:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:498:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:500:        if (annotationIds == null || annotationIds.isEmpty()) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:510:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:524:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:549:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:630:    private JSONObject inferMatchConfig(List<String> annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:631:        if (annotationIds == null || annotationIds.isEmpty()) return null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:636:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:742:     * <p>Routing flags: {@code mlServiceV2: true} (IAGateway v2
src/main/java/org/ecocean/ia/MlServiceProcessor.java:752:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId,

exec
/bin/bash -lc 'rg -n "findMatchProspects|OpenSearch|query" src/main/java/org/ecocean/Embedding.java | head -n 80' in /mnt/c/Wildbook-clean2
 succeeded in 200ms:
37:    // for trying to query vectors of annots without embeddings
153:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
186:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
224:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
265:    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
281:        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
302:                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
324:            // NPE'ing on the queryCount/getMatchQuery below.
326:                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
340:            OpenSearch os = new OpenSearch();
343:                numberCandidates = os.queryCount("annotation", matchingSetQuery);
345:                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
371:            System.out.println("findMatchProspects() on " + ann + " found " +
378:                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
381:                System.out.println("findMatchProspects() MatchResult creation failed on " +

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,390p'" in /mnt/c/Wildbook-clean2
 succeeded in 166ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '390,430p'" in /mnt/c/Wildbook-clean2
 succeeded in 157ms:
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
   411	        return true;
   412	    }
   413	
   414	    public String toString() {
   415	        String st = "Embedding " + id;
   416	
   417	        st += " (vec len " + this.vectorLength() + ")";
   418	        if (annotation != null) st += " [Annotation " + annotation.getId() + "]";
   419	        st += " " + this.getMethodDescription();
   420	        st += " " + Util.prettyPrintDateTime(this.created);
   421	        return st;
   422	    }
   423	
   424	    public boolean equals(final Object o2) {
   425	        if (o2 == null) return false;
   426	        if (!(o2 instanceof Embedding)) return false;
   427	        Embedding two = (Embedding)o2;
   428	        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
   429	        return this.id.equals(two.getId());
   430	    }

codex
**Findings**

- **P2:** Line endings flipped on several touched files. `git ls-files --eol` reports `i/lf w/crlf` for [IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:1), `IAException.java`, `MLService.java`, `MatchResult.java`, `MatchResultProspect.java`, `MlServiceClient.java`, and `MlServiceJobOutcome.java`. `git diff --check` also fails with CRLF showing as trailing whitespace. These appear line-ending-only: `git diff --ignore-space-at-eol --stat` leaves only `MlServiceProcessor.java`. This should be normalized out before commit.

- **P3:** [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:3) now has stale `IOException`, `OpenSearch`, and `VISIBILITY_TIMEOUT_MS` after the gate refactor. Not behavioral, but it leaves dead imports/constants in the final Track 2 commit.

I did not find a blocker in the C11 wiring itself. The dispatcher flags are present, deferred jobs re-enter through `mlServiceV2 + deferredMatch`, the publisher uses `IAGateway.requeueJob(payload, true)` for the delayed detection-queue path, and deferred jobs preserve `attempt`, `firstDeferredAt`, `lastGateReason`, `matchConfig`, `annotationIds`, `taskId`, and `__context`.

Test coverage is good for the DEFER payload contract. Remaining gap: the processor-level READY/GIVE_UP branches are not directly unit-tested through the injected gate, though `MatchVisibilityGateImpl` has age-out coverage and existing tests cover `runMatchProspects` separately.
tokens used
153,288
**Findings**

- **P2:** Line endings flipped on several touched files. `git ls-files --eol` reports `i/lf w/crlf` for [IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:1), `IAException.java`, `MLService.java`, `MatchResult.java`, `MatchResultProspect.java`, `MlServiceClient.java`, and `MlServiceJobOutcome.java`. `git diff --check` also fails with CRLF showing as trailing whitespace. These appear line-ending-only: `git diff --ignore-space-at-eol --stat` leaves only `MlServiceProcessor.java`. This should be normalized out before commit.

- **P3:** [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:3) now has stale `IOException`, `OpenSearch`, and `VISIBILITY_TIMEOUT_MS` after the gate refactor. Not behavioral, but it leaves dead imports/constants in the final Track 2 commit.

I did not find a blocker in the C11 wiring itself. The dispatcher flags are present, deferred jobs re-enter through `mlServiceV2 + deferredMatch`, the publisher uses `IAGateway.requeueJob(payload, true)` for the delayed detection-queue path, and deferred jobs preserve `attempt`, `firstDeferredAt`, `lastGateReason`, `matchConfig`, `annotationIds`, `taskId`, and `__context`.

Test coverage is good for the DEFER payload contract. Remaining gap: the processor-level READY/GIVE_UP branches are not directly unit-tested through the injected gate, though `MatchVisibilityGateImpl` has age-out coverage and existing tests cover `runMatchProspects` separately.
