OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3cda-e470-7fe2-a009-98f10aeb3ce3
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing a design doc on the `migrate-ml-service-v2` branch of
the Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you
the project conventions, repo gotchas, and current architecture that
the design assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (now 3.1 on this deployment), React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has 20 commits, all Codex-reviewed at design + code. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md`.

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

`rollbackAndClose` is idempotent — safe after commit (rollback on
inactive tx is a no-op) and safe after early return.

Critical gotcha: never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the
HTTP work without Shepherd in Phase B, persist outcome in a fresh
Shepherd in Phase C. This pattern is the reference for any code
that mixes DB + HTTP.

## JDO column naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes — e.g.
`ENCOUNTER_ANNOTATIONS.CATALOGNUMBER_OID` references
`ENCOUNTER.CATALOGNUMBER`; `ENCOUNTER_ANNOTATIONS.ID_EID` references
`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
`_OID` suffix — it's a direct FK, not a JDO-generated join).

## ANNOTATION FK constraint cleanup

Direct SQL DELETE on `ANNOTATION` is dangerous. Several tables hold
FKs with `allows-null=false`:

- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
  nulled.
- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
  `dependent-element=false`, so JDO does not cascade. Row must be
  deleted explicitly.
- `TASK_OBJECTANNOTATIONS` join — clean up via the join row.
- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
- `ENCOUNTER_ANNOTATIONS` — caller's responsibility (`enc.removeAnnotation(ann)`).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh boundary;
**does not** drain the Wildbook IndexingManager queue. If you need
"after this write the doc must be searchable" semantics, use
`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
combines `_refresh` with a `_count` poll on an `ids` query.

`waitForVisibility` was added in commit `f429c5bf8` (c7 of v2) as
a counter-measure for race conditions between JDO write,
IndexingManager queue, and OS refresh cadence.

`skipAutoIndexing` (controlled by `/tmp/skipAutoIndexing`) makes
every `waitForVisibility` poll return zero — there's a diagnostic
WARN at entry to alert anyone running with the flag set.

## `/tmp/skipAutoIndexing` and test mode

If `/tmp/skipAutoIndexing` exists, IndexingManager skips OS indexing
entirely. This is a developer convenience for fast local iteration
but breaks any code that depends on `_id` being findable in OS. The
v2 work checks for this file and warns.

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",      // "vector" = ml-service v2
        "method": "miewid-msv4.1",      // embedding model id
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
`isVectorConfig = method != null || api_endpoint != null` (the
either-or check covers the v2-vs-legacy contract).

## v1 antipatterns to avoid (carry-over from earlier review rounds)

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.
5. **Don't trust the cache without a strict variant.** Lenient
   `iaImageIds` swallows errors; strict variant raises so calls
   that need accuracy can opt in.

## File path conventions

- Java: `src/main/java/org/ecocean/...`
- Tests: `src/test/java/org/ecocean/...`
- Design docs: `docs/plans/YYYY-MM-DD-<topic>.md`
- React: `frontend/src/...`

## What we want from this review

The doc below proposes fixing two linked bugs causing empty match
prospects on bulk imports. Both are in scope. Your job: independent
read on whether the design is right, where it's wrong, what it
missed, and any sequencing/test gaps.

**Specifically requested feedback in the doc's "Open questions"
section.** Address those, plus anything else you'd raise.

**Do not write to any file.** Review-only.

---

# Codex code-review: Track 1 C2 — WildbookIAM.mediaAssetToUri public + null-safe

Empty-match-prospects design Track 1 C2 (per locked design's "Codex
follow-ups to address before implementation" item 1): promote the
private `mediaAssetToUri(MediaAsset)` to `public static String` and
tighten the `ma.webURL().toString()` NPE.

## Diff

diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index 1ba8750f4..5178735a9 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -1,851 +1,857 @@
-package org.ecocean.ia.plugin;
-
-import java.io.IOException;
-import java.net.MalformedURLException;
-import java.net.URL;
-import java.security.InvalidKeyException;
-import java.security.NoSuchAlgorithmException;
-import java.util.ArrayList;
-import java.util.HashMap;
-import java.util.List;
-import javax.servlet.ServletContextEvent;
-import org.apache.commons.lang3.builder.ToStringBuilder;
-import org.ecocean.acm.AcmUtil;
-import org.ecocean.Annotation;
-import org.ecocean.cache.CachedQuery;
-import org.ecocean.cache.QueryCache;
-import org.ecocean.cache.QueryCacheFactory;
-import org.ecocean.ia.IA;
-import org.ecocean.ia.Task;
-import org.ecocean.media.*;
-import org.ecocean.RestClient;
-import org.ecocean.shepherd.core.Shepherd;
-import org.ecocean.Util;
-import org.joda.time.DateTime;
-import org.json.JSONArray;
-import org.json.JSONObject;
-
-// NOTE!  this steals **a lot** from IBEISIA right now. eventually lets move it all here and kill that off!
-import org.ecocean.identity.IBEISIA;
-
-/*
-    Wildbook Image Analysis Module (IAM)
-    Initial stab at "plugin architecture" for "Image Analysis"
- */
-public class WildbookIAM extends IAPlugin {
-    private String context = null;
-
-    public WildbookIAM() {
-        super();
-    }
-    public WildbookIAM(String context) {
-        super(context);
-        this.context = context;
-    }
-
-    @Override public boolean isEnabled() {
-        return true; // FIXME
-    }
-
-    @Override public boolean init(String context) {
-        this.context = context;
-        IA.log("WildbookIAM init() called on context " + context);
-        return true;
-    }
-
-    @Override public void startup(ServletContextEvent sce) {
-        // if we dont need identificaiton, no need to prime
-        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context,
-            "IBEISIADisableIdentification"));
-
-        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
-    }
-
-    @Override public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
-        final Task parentTask) {
-        return null;
-    }
-
-    @Override public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
-        final Task parentTask) {
-        return null;
-    }
-
-    // for now "primed" is stored in IBEISIA still.  <scratches head>
-    public boolean isPrimed() {
-        return IBEISIA.isIAPrimed();
-    }
-
-    public void prime() {
-        IA.log("INFO: WildbookIAM.prime(" + this.context +
-            ") called - NOTE this is deprecated and does nothing now.");
-        IBEISIA.setIAPrimed(true);
-    }
-
-/*
-    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
-    this is because we **must** get the return value from the POST, in order that we can map the corresponding (returned) acmId values.  if we
- * timeout* in the POST, this *will not happen*.  and it is a lengthy process on the IA side: as IA must grab the image over the network and
-       generate the acmId from it!  hence, batchSize... which we kind of guestimate and cross our fingers.
- */
-    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
-    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
-        InvalidKeyException {
-        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
-
-        if (u == null)
-            throw new MalformedURLException(
-                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
-        URL url = new URL(u);
-        int batchSize = 30;
-        int numBatches = Math.round(mas.size() / batchSize + 1);
-
-        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
-        List<String> iaImageIds = new ArrayList<String>();
-        if (checkFirst) iaImageIds = iaImageIds();
-        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
-        map.put("image_uri_list", new ArrayList<JSONObject>());
-        map.put("image_uuid_list", new ArrayList<JSONObject>());
-        map.put("image_unixtime_list", new ArrayList<Integer>());
-        map.put("image_gps_lat_list", new ArrayList<Double>());
-        map.put("image_gps_lon_list", new ArrayList<Double>());
-        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
-        int batchCt = 1;
-        JSONObject allRtn = new JSONObject();
-        allRtn.put("_batchSize", batchSize);
-        allRtn.put("_totalSize", mas.size());
-        JSONArray bres = new JSONArray();
-        for (int i = 0; i < mas.size(); i++) {
-            MediaAsset ma = mas.get(i);
-            if (iaImageIds.contains(ma.getAcmId())) continue;
-            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
-                IA.log(
-                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
-                    + ma.getId());
-                continue;
-            }
-            if (!validMediaAsset(ma)) {
-                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
-                continue;
-            }
-            acmList.add(ma);
-            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
-            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
-            map.get("image_uri_list").add(mediaAssetToUri(ma));
-            map.get("image_gps_lat_list").add(ma.getLatitude());
-            map.get("image_gps_lon_list").add(ma.getLongitude());
-            DateTime t = ma.getDateTime();
-            if (t == null) {
-                map.get("image_unixtime_list").add(null);
-            } else {
-                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
-            }
-            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
-                if (acmList.size() > 0) {
-                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
-                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
-                        " batches)");
-                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
-                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
-                    List<String> acmIds = acmIdsFromResponse(rtn);
-                    if (acmIds == null) {
-                        IA.log(
-                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
-                            + rtn);
-                    } else {
-                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
-                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
-                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
-                    }
-                    bres.put(rtn);
-                    // initialize for next batch (if any)
-                    map.put("image_uri_list", new ArrayList<JSONObject>());
-                    map.put("image_uuid_list", new ArrayList<JSONObject>());
-                    map.put("image_unixtime_list", new ArrayList<Integer>());
-                    map.put("image_gps_lat_list", new ArrayList<Double>());
-                    map.put("image_gps_lon_list", new ArrayList<Double>());
-                    acmList = new ArrayList<MediaAsset>();
-                } else {
-                    bres.put("EMPTY BATCH");
-                }
-                batchCt++;
-            }
-        }
-        allRtn.put("batchResults", bres);
-        return allRtn;
-    }
-
-    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
-    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
-        InvalidKeyException {
-        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
-
-        if (u == null)
-            throw new MalformedURLException(
-                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
-        URL url = new URL(u);
-        int batchSize = 30;
-        int numBatches = Math.round(mas.size() / batchSize + 1);
-
-        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
-        List<String> iaImageIds = new ArrayList<String>();
-        if (checkFirst) iaImageIds = iaImageIds();
-        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
-        map.put("image_uri_list", new ArrayList<JSONObject>());
-        map.put("image_uuid_list", new ArrayList<JSONObject>());
-        map.put("image_unixtime_list", new ArrayList<Integer>());
-        map.put("image_gps_lat_list", new ArrayList<Double>());
-        map.put("image_gps_lon_list", new ArrayList<Double>());
-        int batchCt = 1;
-        JSONObject allRtn = new JSONObject();
-        allRtn.put("_batchSize", batchSize);
-        allRtn.put("_totalSize", mas.size());
-        JSONArray bres = new JSONArray();
-        for (int i = 0; i < mas.size(); i++) {
-            MediaAsset ma = mas.get(i);
-            if (iaImageIds.contains(ma.getAcmId())) continue;
-            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
-                IA.log(
-                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
-                    + ma.getId());
-                continue;
-            }
-            if (!validMediaAsset(ma)) {
-                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
-                continue;
-            }
-            map.get("image_uri_list").add(mediaAssetToUri(ma));
-            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
-            map.get("image_gps_lat_list").add(ma.getLatitude());
-            map.get("image_gps_lon_list").add(ma.getLongitude());
-            DateTime t = ma.getDateTime();
-            if (t == null) {
-                map.get("image_unixtime_list").add(null);
-            } else {
-                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
-            }
-            int sendSize = map.get("image_uri_list").size();
-            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
-                if (sendSize > 0) {
-                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
-                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
-                        " batches)");
-                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
-                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
-/*
-                    if (acmIds == null) {
-                        IA.log(
-                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
- + rtn);
-                    } else {
-                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
-                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
-                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
-                    }
- */
-                    bres.put(rtn);
-                    // initialize for next batch (if any)
-                    map.put("image_uri_list", new ArrayList<JSONObject>());
-                    map.put("image_uuid_list", new ArrayList<JSONObject>());
-                    map.put("image_unixtime_list", new ArrayList<Integer>());
-                    map.put("image_gps_lat_list", new ArrayList<Double>());
-                    map.put("image_gps_lon_list", new ArrayList<Double>());
-                    // acmList = new ArrayList<MediaAsset>();
-                } else {
-                    bres.put("EMPTY BATCH");
-                }
-                batchCt++;
-            }
-        }
-        allRtn.put("batchResults", bres);
-        return allRtn;
-    }
-
-    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
-        Shepherd myShepherd)
-    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
-        InvalidKeyException {
-        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
-
-        if (u == null)
-            throw new MalformedURLException(
-                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
-        URL url = new URL(u);
-        int ct = 0;
-        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
-        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
-        List<String> iaAnnotIds = new ArrayList<String>();
-        if (checkFirst) iaAnnotIds = iaAnnotationIds();
-        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
-        map.put("image_uuid_list", new ArrayList<String>());
-        map.put("annot_species_list", new ArrayList<String>());
-        map.put("annot_bbox_list", new ArrayList<int[]>());
-        map.put("annot_name_list", new ArrayList<String>());
-        map.put("annot_theta_list", new ArrayList<Double>());
-
-        List<Annotation> acmList = new ArrayList<Annotation>(); // for rectifyAnnotationIds below
-        for (Annotation ann : anns) {
-            if (iaAnnotIds.contains(ann.getAcmId())) continue;
-            if (iaAnnotIds.contains(ann.getId())) continue;
-            if (ann.getMediaAsset() == null) {
-                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
-                    "; skipping!");
-                continue;
-            }
-            if (ann.getMediaAsset().getAcmId() == null) {
-                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
-                    " (MediaAsset id=" + ann.getMediaAsset().getId() +
-                    " not added to IA?); skipping!");
-                continue;
-            }
-            if (!IBEISIA.validForIdentification(ann)) {
-                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
-                continue;
-            }
-            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
-            if (iid == null) {
-                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
-                    ann.getMediaAsset() + " on " + ann + "; skipping!");
-                continue;
-            }
-            acmList.add(ann);
-            map.get("image_uuid_list").add(iid);
-            int[] bbox = ann.getBbox();
-            map.get("annot_bbox_list").add(bbox);
-            // yuck - IA class is not species
-            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
-            // better
-            map.get("annot_species_list").add(ann.getIAClass());
-
-            map.get("annot_theta_list").add(ann.getTheta());
-            String name = ann.findIndividualId(myShepherd);
-            map.get("annot_name_list").add((name == null) ? "____" : name);
-            ct++;
-        }
-        // myShepherd.rollbackDBTransaction();
-
-        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
-        if (ct < 1) return null; // null for "none to send" ?  is this cool?
-        System.out.println("sendAnnotations(): data -->\n" + map);
-        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
-        System.out.println("sendAnnotations() -> " + rtn);
-        List<String> acmIds = acmIdsFromResponse(rtn);
-        if (acmIds == null) {
-            IA.log(
-                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
-                + rtn);
-        } else {
-            int numChanged = AcmUtil.rectifyAnnotationIds(acmList, acmIds, myShepherd);
-            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
-                " Annotation(s) acmId(s) via rectifyAnnotationIds()");
-        }
-        return rtn;
-    }
-
-    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
-        Shepherd myShepherd)
-    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
-        InvalidKeyException {
-        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
-
-        if (u == null)
-            throw new MalformedURLException(
-                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
-        URL url = new URL(u);
-        int ct = 0;
-        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
-        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
-        List<String> iaAnnotIds = new ArrayList<String>();
-        if (checkFirst) iaAnnotIds = iaAnnotationIds();
-        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
-        map.put("image_uuid_list", new ArrayList<String>());
-        map.put("annot_uuid_list", new ArrayList<String>());
-        map.put("annot_species_list", new ArrayList<String>());
-        map.put("annot_bbox_list", new ArrayList<int[]>());
-        map.put("annot_name_list", new ArrayList<String>());
-        map.put("annot_theta_list", new ArrayList<Double>());
-        for (Annotation ann : anns) {
-            if (iaAnnotIds.contains(ann.getAcmId())) continue;
-            if (iaAnnotIds.contains(ann.getId())) continue;
-            if (ann.getMediaAsset() == null) {
-                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
-                    ann + "; skipping!");
-                continue;
-            }
-            if (!IBEISIA.validForIdentification(ann)) {
-                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
-                continue;
-            }
-            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
-            if (iid == null) {
-                IA.log(
-                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
-                    + ann.getMediaAsset() + " on " + ann + "; skipping!");
-                continue;
-            }
-            map.get("image_uuid_list").add(iid);
-            JSONObject aid = toFancyUUID(ann.getId());
-            map.get("annot_uuid_list").add(aid);
-            int[] bbox = ann.getBbox();
-            map.get("annot_bbox_list").add(bbox);
-            // yuck - IA class is not species
-            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
-            // better
-            map.get("annot_species_list").add(ann.getIAClass());
-
-            map.get("annot_theta_list").add(ann.getTheta());
-            String name = ann.findIndividualId(myShepherd);
-            map.get("annot_name_list").add((name == null) ? "____" : name);
-            ct++;
-        }
-        // myShepherd.rollbackDBTransaction();
-
-        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
-        if (ct < 1) return null; // null for "none to send" ?  is this cool?
-        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
-        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
-        System.out.println("sendAnnotationsForceId() -> " + rtn);
-        checkForcedIds(map.get("annot_uuid_list"), rtn.optJSONArray("response"));
-        return rtn;
-    }
-
-    // ------------------------------------------------------------------
-    // ml-service migration v2: no-Shepherd WBIA registration helpers.
-    //
-    // The polling thread in StartupWildbook splits the work into:
-    //   Phase A (write tx) - load DTO + close.
-    //   Phase B (no DB)    - call into the helpers below.
-    //   Phase C (write tx) - persist result.
-    // Phase B must not hold a Shepherd transaction across the WBIA call.
-    // ------------------------------------------------------------------
-
-    /**
-     * Outcome of a Phase-B WBIA registration attempt.
-     * REGISTERED_OK              - POST succeeded, ids match.
-     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
-     * NETWORK_FAIL               - GET or POST threw / non-2xx.
-     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
-     *                              (id mismatch, length mismatch, missing field).
-     */
-    public enum WbiaRegisterOutcome {
-        REGISTERED_OK,
-        REGISTERED_ALREADY_PRESENT,
-        NETWORK_FAIL,
-        RESPONSE_BAD,
-    }
-
-    /**
-     * Plain-data DTO that holds everything Phase B needs about one
-     * Annotation. Built under a Shepherd transaction in Phase A, then
-     * passed across the close/open boundary into Phase B.
-     *
-     * <p>Phase A is responsible for pre-validating that all required
-     * fields are populated; Phase B treats the DTO as opaque and does
-     * not re-touch any JDO-managed state.</p>
-     */
-    public static final class WbiaRegisterRequest {
-        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
-        public final String annotationAcmId;    // Annotation.acmId, may differ from id on legacy rows
-        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
-        public final int[]  bbox;               // x,y,w,h
-        public final double theta;
-        public final String iaClass;            // species/class string
-        public final String individualName;     // "____" if absent
-
-        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
-            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
-            String individualName) {
-            this.annotationId    = annotationId;
-            this.annotationAcmId = annotationAcmId;
-            this.mediaAssetAcmId = mediaAssetAcmId;
-            this.bbox            = bbox;
-            this.theta           = theta;
-            this.iaClass         = iaClass;
-            this.individualName  = individualName;
-        }
-    }
-
-    /**
-     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
-     * fetch failure rather than returning an empty list. Phase B needs
-     * this so a network failure during the already-present check is
-     * not silently treated as "go ahead and POST".
-     *
-     * <p>Honors the 15-minute QueryCache the same way the lenient
-     * variant does, so a cache hit avoids the network entirely.</p>
-     */
-    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
-        String cacheName = "iaAnnotationIds";
-        // QueryCacheFactory.getQueryCache(context) can return null on a
-        // context that has never been initialized; treat that as "no cache"
-        // rather than NPE-ing out and aborting the poll cycle.
-        QueryCache qc = null;
-        try {
-            qc = QueryCacheFactory.getQueryCache(context);
-        } catch (Exception ex) {
-            // Defensive: cache factory init can fail; degrade to no-cache.
-        }
-        if (qc != null && qc.getQueryByName(cacheName) != null &&
-            System.currentTimeMillis() <
-            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
-            try {
-                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
-                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
-                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
-                return parseAnnotationIdsArrayStrict(cached);
-            } catch (Exception ex) {
-                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
-                    + ex.getMessage());
-            }
-        }
-        JSONArray jids;
-        try {
-            jids = apiGetJSONArray("/api/annot/json/", context);
-        } catch (Exception ex) {
-            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
-        }
-        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
-        if (qc != null) {
-            try {
-                org.datanucleus.api.rest.orgjson.JSONObject jobj =
-                    new org.datanucleus.api.rest.orgjson.JSONObject();
-                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
-                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
-                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
-                qc.addCachedQuery(cq);
-            } catch (Exception cacheEx) {
-                // Cache store failure is non-fatal; we still have the ids.
-            }
-        }
-        return parseAnnotationIdsArrayStrict(jids);
-    }
-
-    /**
-     * Strict element parser: throws IOException if any element is not a
-     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
-     * skips/null-pads malformed entries, which is fine for legacy paths but
-     * would let a corrupt response masquerade as "annotation not yet
-     * registered" in the polling thread's already-present check.
-     */
-    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
-        List<String> ids = new ArrayList<String>();
-        if (jids == null) return ids;
-        for (int i = 0; i < jids.length(); i++) {
-            JSONObject jo = jids.optJSONObject(i);
-            if (jo == null)
-                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
-            String decoded = fromFancyUUID(jo);
-            if (decoded == null)
-                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
-            ids.add(decoded);
-        }
-        return ids;
-    }
-
-    static List<String> parseAnnotationIdsArray(JSONArray jids) {
-        List<String> ids = new ArrayList<String>();
-        if (jids == null) return ids;
-        for (int i = 0; i < jids.length(); i++) {
-            JSONObject jo = jids.optJSONObject(i);
-            if (jo != null) ids.add(fromFancyUUID(jo));
-        }
-        return ids;
-    }
-
-    /**
-     * Build the forced-id POST body for a single DTO. Pure function;
-     * factored out so unit tests can verify the request shape without
-     * a network round trip.
-     */
-    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
-        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
-        map.put("image_uuid_list", new ArrayList<JSONObject>());
-        map.put("annot_uuid_list", new ArrayList<JSONObject>());
-        map.put("annot_species_list", new ArrayList<String>());
-        map.put("annot_bbox_list", new ArrayList<int[]>());
-        map.put("annot_name_list", new ArrayList<String>());
-        map.put("annot_theta_list", new ArrayList<Double>());
-        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
-        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
-        map.get("annot_species_list").add(dto.iaClass);
-        map.get("annot_bbox_list").add(dto.bbox);
-        map.get("annot_name_list").add(
-            (dto.individualName == null) ? "____" : dto.individualName);
-        map.get("annot_theta_list").add(dto.theta);
-        return map;
-    }
-
-    /**
-     * Validate a forced-id response. Throws on any contract violation
-     * (length mismatch, missing entry, id mismatch). Pure function.
-     */
-    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
-        if (resp == null) throw new IOException("null forced-id response");
-        if (resp.has("status")) {
-            JSONObject status = resp.optJSONObject("status");
-            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
-                throw new IOException("forced-id response status.success=false: " + resp);
-            }
-        }
-        JSONArray respArr = resp.optJSONArray("response");
-        if (respArr == null) throw new IOException("no response array: " + resp);
-        if (respArr.length() != 1)
-            throw new IOException("expected response array length 1, got " + respArr.length());
-        JSONObject jid = respArr.optJSONObject(0);
-        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
-        String respId = fromFancyUUID(jid);
-        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
-        if (!respId.equals(sentAnnotId))
-            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
-    }
-
-    /**
-     * Phase B entry point. Does the already-present check, builds the
-     * forced-id POST, fires it, and classifies the outcome. Does NOT
-     * touch any Shepherd or JDO state; callers must hand it a DTO that
-     * was pre-validated and detached in Phase A.
-     */
-    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
-        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
-        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
-        if (u == null) {
-            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
-            return WbiaRegisterOutcome.NETWORK_FAIL;
-        }
-        List<String> known;
-        try {
-            known = iaAnnotationIdsStrict(context);
-        } catch (IOException ex) {
-            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
-                ex.getMessage());
-            return WbiaRegisterOutcome.NETWORK_FAIL;
-        }
-        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
-        // only check the annotation's id and acmId here. Comparing against
-        // the media-asset's acmId is wrong - that would compare an image
-        // identifier against a list of annotation identifiers.
-        if (known.contains(dto.annotationId) ||
-            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
-            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
-        }
-        URL url;
-        try {
-            url = new URL(u);
-        } catch (MalformedURLException ex) {
-            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
-            return WbiaRegisterOutcome.NETWORK_FAIL;
-        }
-        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
-        JSONObject rtn;
-        try {
-            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
-        } catch (Exception ex) {
-            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
-            return WbiaRegisterOutcome.NETWORK_FAIL;
-        }
-        try {
-            validateForcedResponse(dto.annotationId, rtn);
-        } catch (IOException ex) {
-            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
-            return WbiaRegisterOutcome.RESPONSE_BAD;
-        }
-        return WbiaRegisterOutcome.REGISTERED_OK;
-    }
-
-    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
-    throws IOException {
-        if ((sentIds == null) || (respArr == null))
-            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
-        if (sentIds.size() != respArr.length())
-            throw new IOException("args diff length: " + sentIds.size() + " != " +
-                    respArr.length());
-        for (int i = 0; i < sentIds.size(); i++) {
-            String sentId = fromFancyUUID(sentIds.get(i));
-            if (sentId == null)
-                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
-            JSONObject jid = respArr.optJSONObject(i);
-            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
-            String respId = fromFancyUUID(jid);
-            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
-            if (!respId.equals(sentId))
-                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
-                        "; respId=" + respId);
-        }
-    }
-
-    public static List<String> acmIdsFromResponse(JSONObject rtn) {
-        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
-        List<String> ids = new ArrayList<String>();
-        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
-            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
-                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
-                ids.add(null);
-            } else {
-                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
-            }
-        }
-        System.out.println("fromResponse ---> " + ids);
-        return ids;
-    }
-
-    // instance version of below (since context is known)
-    public List<String> iaAnnotationIds() {
-        return iaAnnotationIds(this.context);
-    }
-
-    // this fails "gracefully" with empty list if network fubar.  bad decision?
-    public static List<String> iaAnnotationIds(String context) {
-        List<String> ids = new ArrayList<String>();
-        JSONArray jids = null;
-        String cacheName = "iaAnnotationIds";
-
-        try {
-            QueryCache qc = QueryCacheFactory.getQueryCache(context);
-            if (qc.getQueryByName(cacheName) != null &&
-                System.currentTimeMillis() <
-                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
-                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
-                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
-                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
-            } else {
-                jids = apiGetJSONArray("/api/annot/json/", context);
-                if (jids != null) {
-                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
-                        new org.datanucleus.api.rest.orgjson.JSONObject();
-                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
-                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
-                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
-                    qc.addCachedQuery(cq);
-                }
-            }
-        } catch (Exception ex) {
-            ex.printStackTrace();
-            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
-                ex.toString());
-        }
-        if (jids != null) {
-            try {
-                for (int i = 0; i < jids.length(); i++) {
-                    if (jids.optJSONObject(i) != null)
-                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
-                }
-            } catch (Exception ex) {
-                ex.printStackTrace();
-                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
-            }
-        }
-        return ids;
-    }
-
-    // as above, but images
-    public List<String> iaImageIds() {
-        return iaImageIds(this.context);
-    }
-
-    public static List<String> iaImageIds(String context) {
-        List<String> ids = new ArrayList<String>();
-        JSONArray jids = null;
-
-        try {
-            jids = apiGetJSONArray("/api/image/json/", context);
-        } catch (Exception ex) {
-            ex.printStackTrace();
-            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
-                ex.toString());
-        }
-        if (jids != null) {
-            try {
-                for (int i = 0; i < jids.length(); i++) {
-                    if (jids.optJSONObject(i) != null)
-                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
-                }
-            } catch (Exception ex) {
-                ex.printStackTrace();
-                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
-            }
-        }
-        return ids;
-    }
-
-    public JSONArray apiGetJSONArray(String urlSuffix)
-    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
-        InvalidKeyException {
-        return apiGetJSONArray(urlSuffix, this.context);
-    }
-
-    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
-    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
-        InvalidKeyException {
-        URL u = IBEISIA.iaURL(context, urlSuffix);
-        JSONObject rtn = RestClient.get(u);
-
-        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
-            (rtn.optJSONArray("response") == null) ||
-            !rtn.getJSONObject("status").optBoolean("success", false)) {
-            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
-                rtn);
-            return null;
-        }
-        return rtn.getJSONArray("response");
-    }
-
-    public static String fromFancyUUID(JSONObject u) {
-        if (u == null) return null;
-        return u.optString("__UUID__", null);
-    }
-
-    public static JSONObject toFancyUUID(String u) {
-        JSONObject j = new JSONObject();
-
-        j.put("__UUID__", u);
-        return j;
-    }
-
-    private static Object mediaAssetToUri(MediaAsset ma) {
-        URL curl = ma.webURL();
-        String urlStr = curl.toString();
-
-        // THIS WILL BREAK if you need to append a query to the filename...
-        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
-        if (urlStr != null) {
-            urlStr = urlStr.replaceAll("\\?", "%3F");
-            if (ma.getStore() instanceof LocalAssetStore) {
-                return urlStr;
-            } else {
-                return urlStr;
-            }
-        }
-        return null;
-    }
-
-    // basically "should we send to IA?"
-    public static boolean validMediaAsset(MediaAsset ma) {
-        if (ma == null) return false;
-        if (!ma.isMimeTypeMajor("image")) return false;
-        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
-        if (mediaAssetToUri(ma) == null) {
-            System.out.println(
-                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
-                ma);
-            return false;
-        }
-        return true;
-    }
-
-    // this is used to give a string to IA for annot_species_list specifially
-    // hence the term "IASpecies"
-    public static String getIASpecies(Annotation ann, Shepherd myShepherd) {
-        // NOTE: returning null here is probably "bad" btw....
-        org.ecocean.Encounter enc = ann.findEncounter(myShepherd);
-        if (enc == null) return null;
-        String ts = enc.getTaxonomyString();
-        if (ts == null) return null;
-        return ts.replaceAll(" ", "_");
-    }
-
-    public String toString() {
-        return new ToStringBuilder(this)
-                   .append("WildbookIAM IA Plugin")
-                   .toString();
-    }
-}
+package org.ecocean.ia.plugin;
+
+import java.io.IOException;
+import java.net.MalformedURLException;
+import java.net.URL;
+import java.security.InvalidKeyException;
+import java.security.NoSuchAlgorithmException;
+import java.util.ArrayList;
+import java.util.HashMap;
+import java.util.List;
+import javax.servlet.ServletContextEvent;
+import org.apache.commons.lang3.builder.ToStringBuilder;
+import org.ecocean.acm.AcmUtil;
+import org.ecocean.Annotation;
+import org.ecocean.cache.CachedQuery;
+import org.ecocean.cache.QueryCache;
+import org.ecocean.cache.QueryCacheFactory;
+import org.ecocean.ia.IA;
+import org.ecocean.ia.Task;
+import org.ecocean.media.*;
+import org.ecocean.RestClient;
+import org.ecocean.shepherd.core.Shepherd;
+import org.ecocean.Util;
+import org.joda.time.DateTime;
+import org.json.JSONArray;
+import org.json.JSONObject;
+
+// NOTE!  this steals **a lot** from IBEISIA right now. eventually lets move it all here and kill that off!
+import org.ecocean.identity.IBEISIA;
+
+/*
+    Wildbook Image Analysis Module (IAM)
+    Initial stab at "plugin architecture" for "Image Analysis"
+ */
+public class WildbookIAM extends IAPlugin {
+    private String context = null;
+
+    public WildbookIAM() {
+        super();
+    }
+    public WildbookIAM(String context) {
+        super(context);
+        this.context = context;
+    }
+
+    @Override public boolean isEnabled() {
+        return true; // FIXME
+    }
+
+    @Override public boolean init(String context) {
+        this.context = context;
+        IA.log("WildbookIAM init() called on context " + context);
+        return true;
+    }
+
+    @Override public void startup(ServletContextEvent sce) {
+        // if we dont need identificaiton, no need to prime
+        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context,
+            "IBEISIADisableIdentification"));
+
+        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
+    }
+
+    @Override public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
+        final Task parentTask) {
+        return null;
+    }
+
+    @Override public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
+        final Task parentTask) {
+        return null;
+    }
+
+    // for now "primed" is stored in IBEISIA still.  <scratches head>
+    public boolean isPrimed() {
+        return IBEISIA.isIAPrimed();
+    }
+
+    public void prime() {
+        IA.log("INFO: WildbookIAM.prime(" + this.context +
+            ") called - NOTE this is deprecated and does nothing now.");
+        IBEISIA.setIAPrimed(true);
+    }
+
+/*
+    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
+    this is because we **must** get the return value from the POST, in order that we can map the corresponding (returned) acmId values.  if we
+ * timeout* in the POST, this *will not happen*.  and it is a lengthy process on the IA side: as IA must grab the image over the network and
+       generate the acmId from it!  hence, batchSize... which we kind of guestimate and cross our fingers.
+ */
+    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
+        InvalidKeyException {
+        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
+
+        if (u == null)
+            throw new MalformedURLException(
+                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
+        URL url = new URL(u);
+        int batchSize = 30;
+        int numBatches = Math.round(mas.size() / batchSize + 1);
+
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
+        List<String> iaImageIds = new ArrayList<String>();
+        if (checkFirst) iaImageIds = iaImageIds();
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
+        map.put("image_uri_list", new ArrayList<JSONObject>());
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
+        map.put("image_unixtime_list", new ArrayList<Integer>());
+        map.put("image_gps_lat_list", new ArrayList<Double>());
+        map.put("image_gps_lon_list", new ArrayList<Double>());
+        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
+        int batchCt = 1;
+        JSONObject allRtn = new JSONObject();
+        allRtn.put("_batchSize", batchSize);
+        allRtn.put("_totalSize", mas.size());
+        JSONArray bres = new JSONArray();
+        for (int i = 0; i < mas.size(); i++) {
+            MediaAsset ma = mas.get(i);
+            if (iaImageIds.contains(ma.getAcmId())) continue;
+            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
+                IA.log(
+                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
+                    + ma.getId());
+                continue;
+            }
+            if (!validMediaAsset(ma)) {
+                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
+                continue;
+            }
+            acmList.add(ma);
+            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
+            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
+            map.get("image_uri_list").add(mediaAssetToUri(ma));
+            map.get("image_gps_lat_list").add(ma.getLatitude());
+            map.get("image_gps_lon_list").add(ma.getLongitude());
+            DateTime t = ma.getDateTime();
+            if (t == null) {
+                map.get("image_unixtime_list").add(null);
+            } else {
+                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
+            }
+            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
+                if (acmList.size() > 0) {
+                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
+                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
+                        " batches)");
+                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
+                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
+                    List<String> acmIds = acmIdsFromResponse(rtn);
+                    if (acmIds == null) {
+                        IA.log(
+                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
+                            + rtn);
+                    } else {
+                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
+                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
+                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
+                    }
+                    bres.put(rtn);
+                    // initialize for next batch (if any)
+                    map.put("image_uri_list", new ArrayList<JSONObject>());
+                    map.put("image_uuid_list", new ArrayList<JSONObject>());
+                    map.put("image_unixtime_list", new ArrayList<Integer>());
+                    map.put("image_gps_lat_list", new ArrayList<Double>());
+                    map.put("image_gps_lon_list", new ArrayList<Double>());
+                    acmList = new ArrayList<MediaAsset>();
+                } else {
+                    bres.put("EMPTY BATCH");
+                }
+                batchCt++;
+            }
+        }
+        allRtn.put("batchResults", bres);
+        return allRtn;
+    }
+
+    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
+        InvalidKeyException {
+        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
+
+        if (u == null)
+            throw new MalformedURLException(
+                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
+        URL url = new URL(u);
+        int batchSize = 30;
+        int numBatches = Math.round(mas.size() / batchSize + 1);
+
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
+        List<String> iaImageIds = new ArrayList<String>();
+        if (checkFirst) iaImageIds = iaImageIds();
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
+        map.put("image_uri_list", new ArrayList<JSONObject>());
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
+        map.put("image_unixtime_list", new ArrayList<Integer>());
+        map.put("image_gps_lat_list", new ArrayList<Double>());
+        map.put("image_gps_lon_list", new ArrayList<Double>());
+        int batchCt = 1;
+        JSONObject allRtn = new JSONObject();
+        allRtn.put("_batchSize", batchSize);
+        allRtn.put("_totalSize", mas.size());
+        JSONArray bres = new JSONArray();
+        for (int i = 0; i < mas.size(); i++) {
+            MediaAsset ma = mas.get(i);
+            if (iaImageIds.contains(ma.getAcmId())) continue;
+            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
+                IA.log(
+                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
+                    + ma.getId());
+                continue;
+            }
+            if (!validMediaAsset(ma)) {
+                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
+                continue;
+            }
+            map.get("image_uri_list").add(mediaAssetToUri(ma));
+            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
+            map.get("image_gps_lat_list").add(ma.getLatitude());
+            map.get("image_gps_lon_list").add(ma.getLongitude());
+            DateTime t = ma.getDateTime();
+            if (t == null) {
+                map.get("image_unixtime_list").add(null);
+            } else {
+                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
+            }
+            int sendSize = map.get("image_uri_list").size();
+            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
+                if (sendSize > 0) {
+                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
+                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
+                        " batches)");
+                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
+                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
+/*
+                    if (acmIds == null) {
+                        IA.log(
+                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
+ + rtn);
+                    } else {
+                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
+                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
+                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
+                    }
+ */
+                    bres.put(rtn);
+                    // initialize for next batch (if any)
+                    map.put("image_uri_list", new ArrayList<JSONObject>());
+                    map.put("image_uuid_list", new ArrayList<JSONObject>());
+                    map.put("image_unixtime_list", new ArrayList<Integer>());
+                    map.put("image_gps_lat_list", new ArrayList<Double>());
+                    map.put("image_gps_lon_list", new ArrayList<Double>());
+                    // acmList = new ArrayList<MediaAsset>();
+                } else {
+                    bres.put("EMPTY BATCH");
+                }
+                batchCt++;
+            }
+        }
+        allRtn.put("batchResults", bres);
+        return allRtn;
+    }
+
+    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
+        Shepherd myShepherd)
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
+        InvalidKeyException {
+        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
+
+        if (u == null)
+            throw new MalformedURLException(
+                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
+        URL url = new URL(u);
+        int ct = 0;
+        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
+        List<String> iaAnnotIds = new ArrayList<String>();
+        if (checkFirst) iaAnnotIds = iaAnnotationIds();
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
+        map.put("image_uuid_list", new ArrayList<String>());
+        map.put("annot_species_list", new ArrayList<String>());
+        map.put("annot_bbox_list", new ArrayList<int[]>());
+        map.put("annot_name_list", new ArrayList<String>());
+        map.put("annot_theta_list", new ArrayList<Double>());
+
+        List<Annotation> acmList = new ArrayList<Annotation>(); // for rectifyAnnotationIds below
+        for (Annotation ann : anns) {
+            if (iaAnnotIds.contains(ann.getAcmId())) continue;
+            if (iaAnnotIds.contains(ann.getId())) continue;
+            if (ann.getMediaAsset() == null) {
+                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
+                    "; skipping!");
+                continue;
+            }
+            if (ann.getMediaAsset().getAcmId() == null) {
+                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
+                    " (MediaAsset id=" + ann.getMediaAsset().getId() +
+                    " not added to IA?); skipping!");
+                continue;
+            }
+            if (!IBEISIA.validForIdentification(ann)) {
+                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
+                continue;
+            }
+            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
+            if (iid == null) {
+                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
+                    ann.getMediaAsset() + " on " + ann + "; skipping!");
+                continue;
+            }
+            acmList.add(ann);
+            map.get("image_uuid_list").add(iid);
+            int[] bbox = ann.getBbox();
+            map.get("annot_bbox_list").add(bbox);
+            // yuck - IA class is not species
+            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
+            // better
+            map.get("annot_species_list").add(ann.getIAClass());
+
+            map.get("annot_theta_list").add(ann.getTheta());
+            String name = ann.findIndividualId(myShepherd);
+            map.get("annot_name_list").add((name == null) ? "____" : name);
+            ct++;
+        }
+        // myShepherd.rollbackDBTransaction();
+
+        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
+        if (ct < 1) return null; // null for "none to send" ?  is this cool?
+        System.out.println("sendAnnotations(): data -->\n" + map);
+        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
+        System.out.println("sendAnnotations() -> " + rtn);
+        List<String> acmIds = acmIdsFromResponse(rtn);
+        if (acmIds == null) {
+            IA.log(
+                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
+                + rtn);
+        } else {
+            int numChanged = AcmUtil.rectifyAnnotationIds(acmList, acmIds, myShepherd);
+            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
+                " Annotation(s) acmId(s) via rectifyAnnotationIds()");
+        }
+        return rtn;
+    }
+
+    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
+        Shepherd myShepherd)
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
+        InvalidKeyException {
+        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
+
+        if (u == null)
+            throw new MalformedURLException(
+                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
+        URL url = new URL(u);
+        int ct = 0;
+        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
+        List<String> iaAnnotIds = new ArrayList<String>();
+        if (checkFirst) iaAnnotIds = iaAnnotationIds();
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
+        map.put("image_uuid_list", new ArrayList<String>());
+        map.put("annot_uuid_list", new ArrayList<String>());
+        map.put("annot_species_list", new ArrayList<String>());
+        map.put("annot_bbox_list", new ArrayList<int[]>());
+        map.put("annot_name_list", new ArrayList<String>());
+        map.put("annot_theta_list", new ArrayList<Double>());
+        for (Annotation ann : anns) {
+            if (iaAnnotIds.contains(ann.getAcmId())) continue;
+            if (iaAnnotIds.contains(ann.getId())) continue;
+            if (ann.getMediaAsset() == null) {
+                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
+                    ann + "; skipping!");
+                continue;
+            }
+            if (!IBEISIA.validForIdentification(ann)) {
+                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
+                continue;
+            }
+            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
+            if (iid == null) {
+                IA.log(
+                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
+                    + ann.getMediaAsset() + " on " + ann + "; skipping!");
+                continue;
+            }
+            map.get("image_uuid_list").add(iid);
+            JSONObject aid = toFancyUUID(ann.getId());
+            map.get("annot_uuid_list").add(aid);
+            int[] bbox = ann.getBbox();
+            map.get("annot_bbox_list").add(bbox);
+            // yuck - IA class is not species
+            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
+            // better
+            map.get("annot_species_list").add(ann.getIAClass());
+
+            map.get("annot_theta_list").add(ann.getTheta());
+            String name = ann.findIndividualId(myShepherd);
+            map.get("annot_name_list").add((name == null) ? "____" : name);
+            ct++;
+        }
+        // myShepherd.rollbackDBTransaction();
+
+        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
+        if (ct < 1) return null; // null for "none to send" ?  is this cool?
+        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
+        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
+        System.out.println("sendAnnotationsForceId() -> " + rtn);
+        checkForcedIds(map.get("annot_uuid_list"), rtn.optJSONArray("response"));
+        return rtn;
+    }
+
+    // ------------------------------------------------------------------
+    // ml-service migration v2: no-Shepherd WBIA registration helpers.
+    //
+    // The polling thread in StartupWildbook splits the work into:
+    //   Phase A (write tx) - load DTO + close.
+    //   Phase B (no DB)    - call into the helpers below.
+    //   Phase C (write tx) - persist result.
+    // Phase B must not hold a Shepherd transaction across the WBIA call.
+    // ------------------------------------------------------------------
+
+    /**
+     * Outcome of a Phase-B WBIA registration attempt.
+     * REGISTERED_OK              - POST succeeded, ids match.
+     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
+     * NETWORK_FAIL               - GET or POST threw / non-2xx.
+     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
+     *                              (id mismatch, length mismatch, missing field).
+     */
+    public enum WbiaRegisterOutcome {
+        REGISTERED_OK,
+        REGISTERED_ALREADY_PRESENT,
+        NETWORK_FAIL,
+        RESPONSE_BAD,
+    }
+
+    /**
+     * Plain-data DTO that holds everything Phase B needs about one
+     * Annotation. Built under a Shepherd transaction in Phase A, then
+     * passed across the close/open boundary into Phase B.
+     *
+     * <p>Phase A is responsible for pre-validating that all required
+     * fields are populated; Phase B treats the DTO as opaque and does
+     * not re-touch any JDO-managed state.</p>
+     */
+    public static final class WbiaRegisterRequest {
+        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
+        public final String annotationAcmId;    // Annotation.acmId, may differ from id on legacy rows
+        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
+        public final int[]  bbox;               // x,y,w,h
+        public final double theta;
+        public final String iaClass;            // species/class string
+        public final String individualName;     // "____" if absent
+
+        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
+            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
+            String individualName) {
+            this.annotationId    = annotationId;
+            this.annotationAcmId = annotationAcmId;
+            this.mediaAssetAcmId = mediaAssetAcmId;
+            this.bbox            = bbox;
+            this.theta           = theta;
+            this.iaClass         = iaClass;
+            this.individualName  = individualName;
+        }
+    }
+
+    /**
+     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
+     * fetch failure rather than returning an empty list. Phase B needs
+     * this so a network failure during the already-present check is
+     * not silently treated as "go ahead and POST".
+     *
+     * <p>Honors the 15-minute QueryCache the same way the lenient
+     * variant does, so a cache hit avoids the network entirely.</p>
+     */
+    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
+        String cacheName = "iaAnnotationIds";
+        // QueryCacheFactory.getQueryCache(context) can return null on a
+        // context that has never been initialized; treat that as "no cache"
+        // rather than NPE-ing out and aborting the poll cycle.
+        QueryCache qc = null;
+        try {
+            qc = QueryCacheFactory.getQueryCache(context);
+        } catch (Exception ex) {
+            // Defensive: cache factory init can fail; degrade to no-cache.
+        }
+        if (qc != null && qc.getQueryByName(cacheName) != null &&
+            System.currentTimeMillis() <
+            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
+            try {
+                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
+                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
+                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
+                return parseAnnotationIdsArrayStrict(cached);
+            } catch (Exception ex) {
+                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
+                    + ex.getMessage());
+            }
+        }
+        JSONArray jids;
+        try {
+            jids = apiGetJSONArray("/api/annot/json/", context);
+        } catch (Exception ex) {
+            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
+        }
+        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
+        if (qc != null) {
+            try {
+                org.datanucleus.api.rest.orgjson.JSONObject jobj =
+                    new org.datanucleus.api.rest.orgjson.JSONObject();
+                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
+                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
+                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
+                qc.addCachedQuery(cq);
+            } catch (Exception cacheEx) {
+                // Cache store failure is non-fatal; we still have the ids.
+            }
+        }
+        return parseAnnotationIdsArrayStrict(jids);
+    }
+
+    /**
+     * Strict element parser: throws IOException if any element is not a
+     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
+     * skips/null-pads malformed entries, which is fine for legacy paths but
+     * would let a corrupt response masquerade as "annotation not yet
+     * registered" in the polling thread's already-present check.
+     */
+    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
+        List<String> ids = new ArrayList<String>();
+        if (jids == null) return ids;
+        for (int i = 0; i < jids.length(); i++) {
+            JSONObject jo = jids.optJSONObject(i);
+            if (jo == null)
+                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
+            String decoded = fromFancyUUID(jo);
+            if (decoded == null)
+                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
+            ids.add(decoded);
+        }
+        return ids;
+    }
+
+    static List<String> parseAnnotationIdsArray(JSONArray jids) {
+        List<String> ids = new ArrayList<String>();
+        if (jids == null) return ids;
+        for (int i = 0; i < jids.length(); i++) {
+            JSONObject jo = jids.optJSONObject(i);
+            if (jo != null) ids.add(fromFancyUUID(jo));
+        }
+        return ids;
+    }
+
+    /**
+     * Build the forced-id POST body for a single DTO. Pure function;
+     * factored out so unit tests can verify the request shape without
+     * a network round trip.
+     */
+    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
+        map.put("annot_uuid_list", new ArrayList<JSONObject>());
+        map.put("annot_species_list", new ArrayList<String>());
+        map.put("annot_bbox_list", new ArrayList<int[]>());
+        map.put("annot_name_list", new ArrayList<String>());
+        map.put("annot_theta_list", new ArrayList<Double>());
+        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
+        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
+        map.get("annot_species_list").add(dto.iaClass);
+        map.get("annot_bbox_list").add(dto.bbox);
+        map.get("annot_name_list").add(
+            (dto.individualName == null) ? "____" : dto.individualName);
+        map.get("annot_theta_list").add(dto.theta);
+        return map;
+    }
+
+    /**
+     * Validate a forced-id response. Throws on any contract violation
+     * (length mismatch, missing entry, id mismatch). Pure function.
+     */
+    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
+        if (resp == null) throw new IOException("null forced-id response");
+        if (resp.has("status")) {
+            JSONObject status = resp.optJSONObject("status");
+            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
+                throw new IOException("forced-id response status.success=false: " + resp);
+            }
+        }
+        JSONArray respArr = resp.optJSONArray("response");
+        if (respArr == null) throw new IOException("no response array: " + resp);
+        if (respArr.length() != 1)
+            throw new IOException("expected response array length 1, got " + respArr.length());
+        JSONObject jid = respArr.optJSONObject(0);
+        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
+        String respId = fromFancyUUID(jid);
+        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
+        if (!respId.equals(sentAnnotId))
+            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
+    }
+
+    /**
+     * Phase B entry point. Does the already-present check, builds the
+     * forced-id POST, fires it, and classifies the outcome. Does NOT
+     * touch any Shepherd or JDO state; callers must hand it a DTO that
+     * was pre-validated and detached in Phase A.
+     */
+    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
+        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
+        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
+        if (u == null) {
+            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        List<String> known;
+        try {
+            known = iaAnnotationIdsStrict(context);
+        } catch (IOException ex) {
+            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
+                ex.getMessage());
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
+        // only check the annotation's id and acmId here. Comparing against
+        // the media-asset's acmId is wrong - that would compare an image
+        // identifier against a list of annotation identifiers.
+        if (known.contains(dto.annotationId) ||
+            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
+            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
+        }
+        URL url;
+        try {
+            url = new URL(u);
+        } catch (MalformedURLException ex) {
+            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
+        JSONObject rtn;
+        try {
+            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
+        } catch (Exception ex) {
+            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        try {
+            validateForcedResponse(dto.annotationId, rtn);
+        } catch (IOException ex) {
+            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
+            return WbiaRegisterOutcome.RESPONSE_BAD;
+        }
+        return WbiaRegisterOutcome.REGISTERED_OK;
+    }
+
+    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
+    throws IOException {
+        if ((sentIds == null) || (respArr == null))
+            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
+        if (sentIds.size() != respArr.length())
+            throw new IOException("args diff length: " + sentIds.size() + " != " +
+                    respArr.length());
+        for (int i = 0; i < sentIds.size(); i++) {
+            String sentId = fromFancyUUID(sentIds.get(i));
+            if (sentId == null)
+                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
+            JSONObject jid = respArr.optJSONObject(i);
+            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
+            String respId = fromFancyUUID(jid);
+            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
+            if (!respId.equals(sentId))
+                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
+                        "; respId=" + respId);
+        }
+    }
+
+    public static List<String> acmIdsFromResponse(JSONObject rtn) {
+        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
+        List<String> ids = new ArrayList<String>();
+        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
+            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
+                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
+                ids.add(null);
+            } else {
+                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
+            }
+        }
+        System.out.println("fromResponse ---> " + ids);
+        return ids;
+    }
+
+    // instance version of below (since context is known)
+    public List<String> iaAnnotationIds() {
+        return iaAnnotationIds(this.context);
+    }
+
+    // this fails "gracefully" with empty list if network fubar.  bad decision?
+    public static List<String> iaAnnotationIds(String context) {
+        List<String> ids = new ArrayList<String>();
+        JSONArray jids = null;
+        String cacheName = "iaAnnotationIds";
+
+        try {
+            QueryCache qc = QueryCacheFactory.getQueryCache(context);
+            if (qc.getQueryByName(cacheName) != null &&
+                System.currentTimeMillis() <
+                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
+                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
+                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
+                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
+            } else {
+                jids = apiGetJSONArray("/api/annot/json/", context);
+                if (jids != null) {
+                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
+                        new org.datanucleus.api.rest.orgjson.JSONObject();
+                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
+                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
+                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
+                    qc.addCachedQuery(cq);
+                }
+            }
+        } catch (Exception ex) {
+            ex.printStackTrace();
+            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
+                ex.toString());
+        }
+        if (jids != null) {
+            try {
+                for (int i = 0; i < jids.length(); i++) {
+                    if (jids.optJSONObject(i) != null)
+                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
+                }
+            } catch (Exception ex) {
+                ex.printStackTrace();
+                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
+            }
+        }
+        return ids;
+    }
+
+    // as above, but images
+    public List<String> iaImageIds() {
+        return iaImageIds(this.context);
+    }
+
+    public static List<String> iaImageIds(String context) {
+        List<String> ids = new ArrayList<String>();
+        JSONArray jids = null;
+
+        try {
+            jids = apiGetJSONArray("/api/image/json/", context);
+        } catch (Exception ex) {
+            ex.printStackTrace();
+            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
+                ex.toString());
+        }
+        if (jids != null) {
+            try {
+                for (int i = 0; i < jids.length(); i++) {
+                    if (jids.optJSONObject(i) != null)
+                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
+                }
+            } catch (Exception ex) {
+                ex.printStackTrace();
+                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
+            }
+        }
+        return ids;
+    }
+
+    public JSONArray apiGetJSONArray(String urlSuffix)
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
+        InvalidKeyException {
+        return apiGetJSONArray(urlSuffix, this.context);
+    }
+
+    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
+        InvalidKeyException {
+        URL u = IBEISIA.iaURL(context, urlSuffix);
+        JSONObject rtn = RestClient.get(u);
+
+        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
+            (rtn.optJSONArray("response") == null) ||
+            !rtn.getJSONObject("status").optBoolean("success", false)) {
+            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
+                rtn);
+            return null;
+        }
+        return rtn.getJSONArray("response");
+    }
+
+    public static String fromFancyUUID(JSONObject u) {
+        if (u == null) return null;
+        return u.optString("__UUID__", null);
+    }
+
+    public static JSONObject toFancyUUID(String u) {
+        JSONObject j = new JSONObject();
+
+        j.put("__UUID__", u);
+        return j;
+    }
+
+    /**
+     * Build the URL string WBIA expects in {@code image_uri_list}. The
+     * double-encoded "?" pattern preserves filenames that contain "?" so
+     * WBIA's HTTP layer doesn't truncate them at the query boundary.
+     *
+     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
+     * {@code null}. Promoted from {@code private Object} to
+     * {@code public String} (and the leading-NPE on {@code curl.toString()}
+     * tightened) so the ml-service v2 WBIA registration polling thread
+     * can call it from Phase A while building the {@link WbiaRegisterRequest}
+     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
+     */
+    public static String mediaAssetToUri(MediaAsset ma) {
+        if (ma == null) return null;
+        URL curl = ma.webURL();
+        if (curl == null) return null;
+        String urlStr = curl.toString();
+        if (urlStr == null) return null;
+        // THIS WILL BREAK if you need to append a query to the filename...
+        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
+        return urlStr.replaceAll("\\?", "%3F");
+    }
+
+    // basically "should we send to IA?"
+    public static boolean validMediaAsset(MediaAsset ma) {
+        if (ma == null) return false;
+        if (!ma.isMimeTypeMajor("image")) return false;
+        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
+        if (mediaAssetToUri(ma) == null) {
+            System.out.println(
+                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
+                ma);
+            return false;
+        }
+        return true;
+    }
+
+    // this is used to give a string to IA for annot_species_list specifially
+    // hence the term "IASpecies"
+    public static String getIASpecies(Annotation ann, Shepherd myShepherd) {
+        // NOTE: returning null here is probably "bad" btw....
+        org.ecocean.Encounter enc = ann.findEncounter(myShepherd);
+        if (enc == null) return null;
+        String ts = enc.getTaxonomyString();
+        if (ts == null) return null;
+        return ts.replaceAll(" ", "_");
+    }
+
+    public String toString() {
+        return new ToStringBuilder(this)
+                   .append("WildbookIAM IA Plugin")
+                   .toString();
+    }
+}


## New test:

```java
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;

import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of {@link WildbookIAM#mediaAssetToUri(MediaAsset)}
 * after the C2 promotion to {@code public static String} and
 * leading-NPE tightening. (Empty-match-prospects design Track 1 C2.)
 */
class WildbookIAMMediaAssetToUriTest {

    @Test void returnsNull_whenMediaAssetIsNull() {
        assertNull(WildbookIAM.mediaAssetToUri(null));
    }

    @Test void returnsNull_whenWebUrlIsNull() {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(null);
        assertNull(WildbookIAM.mediaAssetToUri(ma));
    }

    @Test void returnsUrl_unchanged_whenNoQuestionMark()
    throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(new URL("https://example.com/images/abc.jpg"));
        assertEquals("https://example.com/images/abc.jpg",
            WildbookIAM.mediaAssetToUri(ma));
    }

    @Test void escapesSingleQuestionMark_to_percent_3F()
    throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(new URL("https://example.com/a?b.jpg"));
        assertEquals("https://example.com/a%3Fb.jpg",
            WildbookIAM.mediaAssetToUri(ma));
    }

    @Test void escapesEveryQuestionMark_whenMultiplePresent()
    throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(new URL("https://example.com/a?b?c.jpg"));
        assertEquals("https://example.com/a%3Fb%3Fc.jpg",
            WildbookIAM.mediaAssetToUri(ma));
    }
}
```


## Notes

- Two existing call sites at `WildbookIAM.java:134` and `:217` pass
  the result into `List.add()` — no casts to update since the
  previous `Object` return was implicitly compatible.
- `validMediaAsset` at line 822 calls `mediaAssetToUri(ma) == null`
  — still correct with the new String return.
- The previous `if (ma.getStore() instanceof LocalAssetStore)` two-arm
  return was a no-op (both arms returned `urlStr`); removed.
- 5 new tests pass; existing `WildbookIAMRegisterTest` still passes.

## Review scope

Per locked design, this is the smallest needed change to enable the
Phase A DTO image-fields capture (C5 later in the plan). Review for:
- Whether the null-safety covers all cases (`ma==null`, `webURL==null`,
  `toString` returning null is implausible but covered).
- Whether the removed `LocalAssetStore` branch had any historical
  significance (commit archaeology suggests it was always a no-op).
- Whether the JavaDoc references the right design artifact.
- Anything else.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll keep this review-only and inspect the local branch for context around the helper, call sites, and the proposed test shape before giving findings.
exec
/bin/bash -lc 'git status --short --branch' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"mediaAssetToUri|validMediaAsset|sendMediaAssetsForceId|sendMediaAssets\\(\" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin 2>/dev/null" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'file src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
 succeeded in 327ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java: ASCII text, with CRLF line terminators

 succeeded in 518ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:86:    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:91:    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:123:                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:127:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:128:                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:145:                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:149:                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:153:                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:157:                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:178:    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:209:                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:213:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:214:                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:230:                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:234:                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:238:                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:242:                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:816:    public static String mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:    public static boolean validMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:832:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:834:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:14: * Pure-function coverage of {@link WildbookIAM#mediaAssetToUri(MediaAsset)}
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:21:        assertNull(WildbookIAM.mediaAssetToUri(null));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:27:        assertNull(WildbookIAM.mediaAssetToUri(ma));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:35:            WildbookIAM.mediaAssetToUri(ma));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:43:            WildbookIAM.mediaAssetToUri(ma));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:51:            WildbookIAM.mediaAssetToUri(ma));

codex
I found one repo-level issue already: `WildbookIAM.java` is now CRLF, which explains why the pasted diff shows the whole file replaced. I’m checking whether that is intentional and whether there are behavioral issues beyond whitespace.
 succeeded in 11272ms:
## migrate-ml-service-v2...origin/migrate-ml-service-v2 [ahead 3]
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
 M src/main/java/org/ecocean/OpenSearch.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/StartupWildbook.java
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
 M src/main/java/org/ecocean/ia/Task.java
 M src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
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
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/
?? src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '800,840p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff --numstat -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 273ms:
   800	        j.put("__UUID__", u);
   801	        return j;
   802	    }
   803	
   804	    /**
   805	     * Build the URL string WBIA expects in {@code image_uri_list}. The
   806	     * double-encoded "?" pattern preserves filenames that contain "?" so
   807	     * WBIA's HTTP layer doesn't truncate them at the query boundary.
   808	     *
   809	     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
   810	     * {@code null}. Promoted from {@code private Object} to
   811	     * {@code public String} (and the leading-NPE on {@code curl.toString()}
   812	     * tightened) so the ml-service v2 WBIA registration polling thread
   813	     * can call it from Phase A while building the {@link WbiaRegisterRequest}
   814	     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
   815	     */
   816	    public static String mediaAssetToUri(MediaAsset ma) {
   817	        if (ma == null) return null;
   818	        URL curl = ma.webURL();
   819	        if (curl == null) return null;
   820	        String urlStr = curl.toString();
   821	        if (urlStr == null) return null;
   822	        // THIS WILL BREAK if you need to append a query to the filename...
   823	        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
   824	        return urlStr.replaceAll("\\?", "%3F");
   825	    }
   826	
   827	    // basically "should we send to IA?"
   828	    public static boolean validMediaAsset(MediaAsset ma) {
   829	        if (ma == null) return false;
   830	        if (!ma.isMimeTypeMajor("image")) return false;
   831	        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
   832	        if (mediaAssetToUri(ma) == null) {
   833	            System.out.println(
   834	                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
   835	                ma);
   836	            return false;
   837	        }
   838	        return true;
   839	    }
   840	

 succeeded in 470ms:
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/ia/plugin/WildbookIAM.java

 succeeded in 621ms:
857	851	src/main/java/org/ecocean/ia/plugin/WildbookIAM.java

exec
/bin/bash -lc 'file src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "git diff --ignore-space-at-eol -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git log --oneline --follow -n 8 -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
 succeeded in 227ms:
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java: C++ source, ASCII text

 succeeded in 235ms:
     1	package org.ecocean.ia.plugin;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertNull;
     5	import static org.mockito.Mockito.mock;
     6	import static org.mockito.Mockito.when;
     7	
     8	import java.net.URL;
     9	
    10	import org.ecocean.media.MediaAsset;
    11	import org.junit.jupiter.api.Test;
    12	
    13	/**
    14	 * Pure-function coverage of {@link WildbookIAM#mediaAssetToUri(MediaAsset)}
    15	 * after the C2 promotion to {@code public static String} and
    16	 * leading-NPE tightening. (Empty-match-prospects design Track 1 C2.)
    17	 */
    18	class WildbookIAMMediaAssetToUriTest {
    19	
    20	    @Test void returnsNull_whenMediaAssetIsNull() {
    21	        assertNull(WildbookIAM.mediaAssetToUri(null));
    22	    }
    23	
    24	    @Test void returnsNull_whenWebUrlIsNull() {
    25	        MediaAsset ma = mock(MediaAsset.class);
    26	        when(ma.webURL()).thenReturn(null);
    27	        assertNull(WildbookIAM.mediaAssetToUri(ma));
    28	    }
    29	
    30	    @Test void returnsUrl_unchanged_whenNoQuestionMark()
    31	    throws Exception {
    32	        MediaAsset ma = mock(MediaAsset.class);
    33	        when(ma.webURL()).thenReturn(new URL("https://example.com/images/abc.jpg"));
    34	        assertEquals("https://example.com/images/abc.jpg",
    35	            WildbookIAM.mediaAssetToUri(ma));
    36	    }
    37	
    38	    @Test void escapesSingleQuestionMark_to_percent_3F()
    39	    throws Exception {
    40	        MediaAsset ma = mock(MediaAsset.class);
    41	        when(ma.webURL()).thenReturn(new URL("https://example.com/a?b.jpg"));
    42	        assertEquals("https://example.com/a%3Fb.jpg",
    43	            WildbookIAM.mediaAssetToUri(ma));
    44	    }
    45	
    46	    @Test void escapesEveryQuestionMark_whenMultiplePresent()
    47	    throws Exception {
    48	        MediaAsset ma = mock(MediaAsset.class);
    49	        when(ma.webURL()).thenReturn(new URL("https://example.com/a?b?c.jpg"));
    50	        assertEquals("https://example.com/a%3Fb%3Fc.jpg",
    51	            WildbookIAM.mediaAssetToUri(ma));
    52	    }
    53	}

 succeeded in 529ms:
diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index 1ba8750f4..5178735a9 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -801,21 +801,27 @@ public class WildbookIAM extends IAPlugin {
         return j;
     }
 
-    private static Object mediaAssetToUri(MediaAsset ma) {
+    /**
+     * Build the URL string WBIA expects in {@code image_uri_list}. The
+     * double-encoded "?" pattern preserves filenames that contain "?" so
+     * WBIA's HTTP layer doesn't truncate them at the query boundary.
+     *
+     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
+     * {@code null}. Promoted from {@code private Object} to
+     * {@code public String} (and the leading-NPE on {@code curl.toString()}
+     * tightened) so the ml-service v2 WBIA registration polling thread
+     * can call it from Phase A while building the {@link WbiaRegisterRequest}
+     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
+     */
+    public static String mediaAssetToUri(MediaAsset ma) {
+        if (ma == null) return null;
         URL curl = ma.webURL();
+        if (curl == null) return null;
         String urlStr = curl.toString();
-
+        if (urlStr == null) return null;
         // THIS WILL BREAK if you need to append a query to the filename...
         // we are double encoding the '?' in order to allow filenames that contain it to go to IA
-        if (urlStr != null) {
-            urlStr = urlStr.replaceAll("\\?", "%3F");
-            if (ma.getStore() instanceof LocalAssetStore) {
-                return urlStr;
-            } else {
-                return urlStr;
-            }
-        }
-        return null;
+        return urlStr.replaceAll("\\?", "%3F");
     }
 
     // basically "should we send to IA?"

 succeeded in 3030ms:
69d6bb401 fix(ia): c11 fix-pass second-round review fixes
4febfee91 fix(ia): c11 fix-pass post-review fixes
c6ffe5d20 fix(ia): WBIA registration poller c11 review follow-ups
358438e44 Normalize line endings to LF
2a3eab63a Let Wildbook own ACMID generation instead of relying on WBIA
c90777431 removal of S3AssetStore and related S3 stuff; removal of twitter lib from pom.xml
1bdacba20 test of force-id for images
fa9255450 bugfix

exec
/bin/bash -lc "rg -n \"URL webURL|webURL\\(\" src/main/java/org/ecocean/media src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 917ms:
src/main/java/org/ecocean/media/YouTubeAssetStore.java:79:    @Override public URL webURL(final MediaAsset ma) {
src/main/java/org/ecocean/media/URLAssetStore.java:105:    @Override public URL webURL(final MediaAsset ma) {
src/main/java/org/ecocean/media/URLAssetStore.java:132:        URL u = this.webURL(ma);
src/main/java/org/ecocean/export/EncounterImageExportFile.java:45:                    ma.webURL() == null) {
src/main/java/org/ecocean/export/EncounterImageExportFile.java:60:                    URI imageUrl = ma.webURL().toURI();
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:133:        URL url = ma.webURL();
src/main/java/org/ecocean/media/MediaAsset.java:694:    public URL webURL() {
src/main/java/org/ecocean/media/MediaAsset.java:707:        return store.webURL(this);
src/main/java/org/ecocean/media/MediaAsset.java:716:        return ma.webURL();
src/main/java/org/ecocean/media/MediaAsset.java:755:                return new URL(store.webURL(this).getProtocol(), containerName, 80,
src/main/java/org/ecocean/media/MediaAsset.java:756:                        store.webURL(this).getFile());
src/main/java/org/ecocean/opendata/OBISSeamap.java:149:                URL u = kids.get(0).webURL();
src/main/java/org/ecocean/opendata/OBISSeamap.java:206:                URL u = kids.get(0).webURL();
src/main/java/org/ecocean/media/LocalAssetStore.java:243:    @Override public URL webURL(final MediaAsset ma) {
src/main/java/org/ecocean/Encounter.java:3539:        if (ma.webURL() == null) {
src/main/java/org/ecocean/Encounter.java:3544:        url = ma.webURL().toString();
src/main/java/org/ecocean/media/AssetStore.java:131:    public abstract URL webURL(MediaAsset ma);
src/main/java/org/ecocean/media/AssetStore.java:542:        URL url = ma.webURL();
src/main/java/org/ecocean/media/AssetStore.java:555:        if ((kids != null) && (kids.size() > 0) && (kids.get(0).webURL() != null))
src/main/java/org/ecocean/media/AssetStore.java:556:            smallUrl = kids.get(0).webURL().toString();
src/main/java/org/ecocean/identity/IBEISIA.java:838:        if (curl == null) curl = ma.webURL();
src/main/java/org/ecocean/api/BaseObject.java:167:                    maj.put("url", ma.webURL());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:809:     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:818:        URL curl = ma.webURL();
src/main/java/org/ecocean/ia/MatchResultProspect.java:71:            aj.put("url", asset.webURL()); // we have no "safe" url
src/main/java/org/ecocean/ia/MlServiceProcessor.java:185:            URL webUrl = ma.webURL();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:239:            URL webUrl = ma.webURL();
src/main/java/org/ecocean/ia/MatchResult.java:329:        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
src/main/java/org/ecocean/ia/MatchResult.java:330:        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
src/main/java/org/ecocean/ia/MLService.java:403:        payload.put("image_uri", ma.webURL());
src/main/java/org/ecocean/ia/MLService.java:419:        payload.put("image_uri", ma.webURL());
src/main/java/org/ecocean/ai/ocr/azure/AzureOcr.java:40:            String responseString = postSingleAsset(ma.webURL().toString(), language);
src/main/java/org/ecocean/MarkedIndividual.java:2243:                if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
src/main/java/org/ecocean/MarkedIndividual.java:2288:                    if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
src/main/java/org/ecocean/MarkedIndividual.java:2342:            if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
src/main/java/org/ecocean/servlet/EncounterVMData.java:225:            URL safe = ma.webURL();
src/main/java/org/ecocean/media/YouTubeAssetStore.java:79:    @Override public URL webURL(final MediaAsset ma) {
src/main/java/org/ecocean/media/URLAssetStore.java:105:    @Override public URL webURL(final MediaAsset ma) {
src/main/java/org/ecocean/media/URLAssetStore.java:132:        URL u = this.webURL(ma);
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:114:            annIA.put("assetWebURL", Util.scrubURL(ma.webURL()));
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:177:	        rtn.put("spotAssetUrl", crMa.webURL());
src/main/java/org/ecocean/media/MediaAsset.java:694:    public URL webURL() {
src/main/java/org/ecocean/media/MediaAsset.java:707:        return store.webURL(this);
src/main/java/org/ecocean/media/MediaAsset.java:716:        return ma.webURL();
src/main/java/org/ecocean/media/MediaAsset.java:755:                return new URL(store.webURL(this).getProtocol(), containerName, 80,
src/main/java/org/ecocean/media/MediaAsset.java:756:                        store.webURL(this).getFile());
src/main/java/org/ecocean/media/LocalAssetStore.java:243:    @Override public URL webURL(final MediaAsset ma) {
src/main/java/org/ecocean/media/AssetStore.java:131:    public abstract URL webURL(MediaAsset ma);
src/main/java/org/ecocean/media/AssetStore.java:542:        URL url = ma.webURL();
src/main/java/org/ecocean/media/AssetStore.java:555:        if ((kids != null) && (kids.size() > 0) && (kids.get(0).webURL() != null))
src/main/java/org/ecocean/media/AssetStore.java:556:            smallUrl = kids.get(0).webURL().toString();

exec
/bin/bash -lc 'git diff --check -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"LocalAssetStore|mediaAssetToUri|image_uri_list|webURL\\(\\)\" docs/plans/2026-05-18-wbia-image-registration-design.md docs/plans/2026-05-09-ml-service-migration-v2.md src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 742ms:
docs/plans/2026-05-18-wbia-image-registration-design.md:51:| `WildbookIAM.mediaAssetToUri(MediaAsset)` | exists but is **`private`** at line 804. Returns the double-encoded web URL string. Note: `ma.webURL()` can return null and the existing helper dereferences without checking — a long-standing latent NPE. | Promote to `public static String` (signature change from `Object` return; tighten the null-check). Phase A then calls it directly to capture `imageUri` into the DTO. |
docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-wbia-image-registration-design.md:63:public final String imageUri;             // mediaAssetToUri(ma) result
docs/plans/2026-05-18-wbia-image-registration-design.md:96:String imageUri = (String) WildbookIAM.mediaAssetToUri(ma);  // method returns Object; cast
docs/plans/2026-05-18-wbia-image-registration-design.md:150:    map.put("image_uri_list", new ArrayList<String>());
docs/plans/2026-05-18-wbia-image-registration-design.md:155:    map.get("image_uri_list").add(dto.imageUri);
src/main/java/org/ecocean/MarkedIndividual.java:2243:                if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
src/main/java/org/ecocean/MarkedIndividual.java:2288:                    if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
src/main/java/org/ecocean/MarkedIndividual.java:2342:            if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
src/main/java/org/ecocean/export/EncounterImageExportFile.java:45:                    ma.webURL() == null) {
src/main/java/org/ecocean/export/EncounterImageExportFile.java:60:                    URI imageUrl = ma.webURL().toURI();
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:133:        URL url = ma.webURL();
src/main/java/org/ecocean/identity/IBEISIA.java:121:        map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/identity/IBEISIA.java:148:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/identity/IBEISIA.java:834:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/identity/IBEISIA.java:835:        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
src/main/java/org/ecocean/identity/IBEISIA.java:838:        if (curl == null) curl = ma.webURL();
src/main/java/org/ecocean/identity/IBEISIA.java:839:        if (ma.getStore() instanceof LocalAssetStore) {
src/main/java/org/ecocean/opendata/OBISSeamap.java:149:                URL u = kids.get(0).webURL();
src/main/java/org/ecocean/opendata/OBISSeamap.java:206:                URL u = kids.get(0).webURL();
src/main/java/org/ecocean/StartupWildbook.java:20:import org.ecocean.media.LocalAssetStore;
src/main/java/org/ecocean/StartupWildbook.java:151:        LocalAssetStore as = new LocalAssetStore("Default Local AssetStore",
src/main/java/org/ecocean/Encounter.java:3539:        if (ma.webURL() == null) {
src/main/java/org/ecocean/Encounter.java:3544:        url = ma.webURL().toString();
src/main/java/org/ecocean/media/MediaAsset.java:694:    public URL webURL() {
src/main/java/org/ecocean/media/MediaAsset.java:716:        return ma.webURL();
src/main/java/org/ecocean/media/LocalAssetStore.java:19: * LocalAssetStore references MediaAssets on the current host's filesystem.
src/main/java/org/ecocean/media/LocalAssetStore.java:30:public class LocalAssetStore extends AssetStore {
src/main/java/org/ecocean/media/LocalAssetStore.java:34:        org.ecocean.media.LocalAssetStore.class);
src/main/java/org/ecocean/media/LocalAssetStore.java:49:    public LocalAssetStore(final String name, final Path root, final String webRoot,
src/main/java/org/ecocean/media/LocalAssetStore.java:61:    LocalAssetStore(final Integer id, final String name, final AssetStoreConfig config,
src/main/java/org/ecocean/media/LocalAssetStore.java:119:    // convenience method to create directly from single File arguement (LocalAssetStore only)
src/main/java/org/ecocean/media/LocalAssetStore.java:181:        if (!(fromMA.getStore() instanceof LocalAssetStore) ||
src/main/java/org/ecocean/media/LocalAssetStore.java:182:            !(toMA.getStore() instanceof LocalAssetStore))
src/main/java/org/ecocean/media/LocalAssetStore.java:185:        throw new IOException("oops, LocalAssetStore.copyAsset() still not implemented. :/"); 
src/main/java/org/ecocean/media/LocalAssetStore.java:192:        System.out.println("LocalAssetStore attempting to delete file=" + file);
src/main/java/org/ecocean/media/LocalAssetStore.java:244:        if (ma == null) System.out.println("MediaAsset is null in LocalAssetStore.webURL");
src/main/java/org/ecocean/media/LocalAssetStore.java:294:        return abs.substring(0, 10) + LocalAssetStore.hexStringSHA256(abs);
src/main/java/org/ecocean/media/LocalAssetStore.java:309:// System.out.println("NOTE: LocalAssetStore.createParameters(" + file + ") -> " + p.toString());
src/main/java/org/ecocean/ia/MatchResult.java:329:        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
src/main/java/org/ecocean/ia/MatchResult.java:330:        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
src/main/java/org/ecocean/media/AssetStore.java:48: * @see LocalAssetStore
src/main/java/org/ecocean/media/AssetStore.java:542:        URL url = ma.webURL();
src/main/java/org/ecocean/media/AssetStore.java:555:        if ((kids != null) && (kids.size() > 0) && (kids.get(0).webURL() != null))
src/main/java/org/ecocean/media/AssetStore.java:556:            smallUrl = kids.get(0).webURL().toString();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:107:        map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:162:                    map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:194:        map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:227:            int sendSize = map.get("image_uri_list").size();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:248:                    map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:805:     * Build the URL string WBIA expects in {@code image_uri_list}. The
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:809:     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:816:    public static String mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:818:        URL curl = ma.webURL();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:832:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:834:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/ia/MlServiceProcessor.java:185:            URL webUrl = ma.webURL();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:239:            URL webUrl = ma.webURL();
src/main/java/org/ecocean/api/BaseObject.java:167:                    maj.put("url", ma.webURL());
src/main/java/org/ecocean/ia/MLService.java:403:        payload.put("image_uri", ma.webURL());
src/main/java/org/ecocean/ia/MLService.java:419:        payload.put("image_uri", ma.webURL());
src/main/java/org/ecocean/ia/MatchResultProspect.java:71:            aj.put("url", asset.webURL()); // we have no "safe" url
src/main/java/org/ecocean/ai/ocr/azure/AzureOcr.java:40:            String responseString = postSingleAsset(ma.webURL().toString(), language);
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:1147:    private LocalAssetStore initFeatureTypeAndAssetStore(Shepherd myShepherd) {
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:1156:        LocalAssetStore as = new LocalAssetStore("Oman-Asset-Store",
src/main/java/org/ecocean/servlet/EncounterVMData.java:225:            URL safe = ma.webURL();
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:114:            annIA.put("assetWebURL", Util.scrubURL(ma.webURL()));
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:177:	        rtn.put("spotAssetUrl", crMa.webURL());

 exited 2 in 834ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1: trailing whitespace.
+package org.ecocean.ia.plugin;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:2: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:3: trailing whitespace.
+import java.io.IOException;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:4: trailing whitespace.
+import java.net.MalformedURLException;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:5: trailing whitespace.
+import java.net.URL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:6: trailing whitespace.
+import java.security.InvalidKeyException;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:7: trailing whitespace.
+import java.security.NoSuchAlgorithmException;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:8: trailing whitespace.
+import java.util.ArrayList;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:9: trailing whitespace.
+import java.util.HashMap;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:10: trailing whitespace.
+import java.util.List;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:11: trailing whitespace.
+import javax.servlet.ServletContextEvent;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:12: trailing whitespace.
+import org.apache.commons.lang3.builder.ToStringBuilder;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:13: trailing whitespace.
+import org.ecocean.acm.AcmUtil;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:14: trailing whitespace.
+import org.ecocean.Annotation;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:15: trailing whitespace.
+import org.ecocean.cache.CachedQuery;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:16: trailing whitespace.
+import org.ecocean.cache.QueryCache;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:17: trailing whitespace.
+import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:18: trailing whitespace.
+import org.ecocean.ia.IA;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:19: trailing whitespace.
+import org.ecocean.ia.Task;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:20: trailing whitespace.
+import org.ecocean.media.*;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:21: trailing whitespace.
+import org.ecocean.RestClient;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:22: trailing whitespace.
+import org.ecocean.shepherd.core.Shepherd;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:23: trailing whitespace.
+import org.ecocean.Util;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:24: trailing whitespace.
+import org.joda.time.DateTime;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:25: trailing whitespace.
+import org.json.JSONArray;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:26: trailing whitespace.
+import org.json.JSONObject;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:27: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:28: trailing whitespace.
+// NOTE!  this steals **a lot** from IBEISIA right now. eventually lets move it all here and kill that off!
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:29: trailing whitespace.
+import org.ecocean.identity.IBEISIA;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:30: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:31: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:32: trailing whitespace.
+    Wildbook Image Analysis Module (IAM)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:33: trailing whitespace.
+    Initial stab at "plugin architecture" for "Image Analysis"
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:34: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:35: trailing whitespace.
+public class WildbookIAM extends IAPlugin {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:36: trailing whitespace.
+    private String context = null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:37: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:38: trailing whitespace.
+    public WildbookIAM() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:39: trailing whitespace.
+        super();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:40: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:41: trailing whitespace.
+    public WildbookIAM(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:42: trailing whitespace.
+        super(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:43: trailing whitespace.
+        this.context = context;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:44: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:45: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:46: trailing whitespace.
+    @Override public boolean isEnabled() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:47: trailing whitespace.
+        return true; // FIXME
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:48: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:49: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:50: trailing whitespace.
+    @Override public boolean init(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:51: trailing whitespace.
+        this.context = context;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:52: trailing whitespace.
+        IA.log("WildbookIAM init() called on context " + context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:53: trailing whitespace.
+        return true;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:54: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:55: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:56: trailing whitespace.
+    @Override public void startup(ServletContextEvent sce) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:57: trailing whitespace.
+        // if we dont need identificaiton, no need to prime
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:58: trailing whitespace.
+        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:59: trailing whitespace.
+            "IBEISIADisableIdentification"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:60: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:61: trailing whitespace.
+        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:62: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:63: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:64: trailing whitespace.
+    @Override public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:65: trailing whitespace.
+        final Task parentTask) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:66: trailing whitespace.
+        return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:67: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:68: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:69: trailing whitespace.
+    @Override public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:70: trailing whitespace.
+        final Task parentTask) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:71: trailing whitespace.
+        return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:72: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:73: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:74: trailing whitespace.
+    // for now "primed" is stored in IBEISIA still.  <scratches head>
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:75: trailing whitespace.
+    public boolean isPrimed() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:76: trailing whitespace.
+        return IBEISIA.isIAPrimed();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:77: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:78: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:79: trailing whitespace.
+    public void prime() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:80: trailing whitespace.
+        IA.log("INFO: WildbookIAM.prime(" + this.context +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:81: trailing whitespace.
+            ") called - NOTE this is deprecated and does nothing now.");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:82: trailing whitespace.
+        IBEISIA.setIAPrimed(true);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:83: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:84: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:85: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:86: trailing whitespace.
+    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:87: trailing whitespace.
+    this is because we **must** get the return value from the POST, in order that we can map the corresponding (returned) acmId values.  if we
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:88: trailing whitespace.
+ * timeout* in the POST, this *will not happen*.  and it is a lengthy process on the IA side: as IA must grab the image over the network and
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:89: trailing whitespace.
+       generate the acmId from it!  hence, batchSize... which we kind of guestimate and cross our fingers.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:90: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:91: trailing whitespace.
+    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:92: trailing whitespace.
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:93: trailing whitespace.
+        InvalidKeyException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:94: trailing whitespace.
+        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:95: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:96: trailing whitespace.
+        if (u == null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:97: trailing whitespace.
+            throw new MalformedURLException(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:98: trailing whitespace.
+                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:99: trailing whitespace.
+        URL url = new URL(u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:100: trailing whitespace.
+        int batchSize = 30;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:101: trailing whitespace.
+        int numBatches = Math.round(mas.size() / batchSize + 1);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:102: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:103: trailing whitespace.
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104: trailing whitespace.
+        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105: trailing whitespace.
+        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:106: trailing whitespace.
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:107: trailing whitespace.
+        map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:108: trailing whitespace.
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:109: trailing whitespace.
+        map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:110: trailing whitespace.
+        map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:111: trailing whitespace.
+        map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:112: trailing whitespace.
+        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:113: trailing whitespace.
+        int batchCt = 1;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:114: trailing whitespace.
+        JSONObject allRtn = new JSONObject();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:115: trailing whitespace.
+        allRtn.put("_batchSize", batchSize);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:116: trailing whitespace.
+        allRtn.put("_totalSize", mas.size());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:117: trailing whitespace.
+        JSONArray bres = new JSONArray();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:118: trailing whitespace.
+        for (int i = 0; i < mas.size(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:119: trailing whitespace.
+            MediaAsset ma = mas.get(i);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120: trailing whitespace.
+            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:121: trailing whitespace.
+            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:122: trailing whitespace.
+                IA.log(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:123: trailing whitespace.
+                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:124: trailing whitespace.
+                    + ma.getId());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:125: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:126: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:127: trailing whitespace.
+            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:128: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:129: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:130: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:131: trailing whitespace.
+            acmList.add(ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:132: trailing whitespace.
+            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:133: trailing whitespace.
+            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134: trailing whitespace.
+            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:135: trailing whitespace.
+            map.get("image_gps_lat_list").add(ma.getLatitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:136: trailing whitespace.
+            map.get("image_gps_lon_list").add(ma.getLongitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:137: trailing whitespace.
+            DateTime t = ma.getDateTime();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:138: trailing whitespace.
+            if (t == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:139: trailing whitespace.
+                map.get("image_unixtime_list").add(null);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:140: trailing whitespace.
+            } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:141: trailing whitespace.
+                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:142: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:143: trailing whitespace.
+            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:144: trailing whitespace.
+                if (acmList.size() > 0) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:145: trailing whitespace.
+                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:146: trailing whitespace.
+                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:147: trailing whitespace.
+                        " batches)");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:148: trailing whitespace.
+                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:149: trailing whitespace.
+                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:150: trailing whitespace.
+                    List<String> acmIds = acmIdsFromResponse(rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:151: trailing whitespace.
+                    if (acmIds == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:152: trailing whitespace.
+                        IA.log(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:153: trailing whitespace.
+                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:154: trailing whitespace.
+                            + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:155: trailing whitespace.
+                    } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:156: trailing whitespace.
+                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:157: trailing whitespace.
+                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:158: trailing whitespace.
+                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:159: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:160: trailing whitespace.
+                    bres.put(rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:161: trailing whitespace.
+                    // initialize for next batch (if any)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:162: trailing whitespace.
+                    map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:163: trailing whitespace.
+                    map.put("image_uuid_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:164: trailing whitespace.
+                    map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:165: trailing whitespace.
+                    map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:166: trailing whitespace.
+                    map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:167: trailing whitespace.
+                    acmList = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:168: trailing whitespace.
+                } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:169: trailing whitespace.
+                    bres.put("EMPTY BATCH");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:170: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:171: trailing whitespace.
+                batchCt++;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:172: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:173: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:174: trailing whitespace.
+        allRtn.put("batchResults", bres);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:175: trailing whitespace.
+        return allRtn;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:176: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:177: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:178: trailing whitespace.
+    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:179: trailing whitespace.
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:180: trailing whitespace.
+        InvalidKeyException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:181: trailing whitespace.
+        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:182: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:183: trailing whitespace.
+        if (u == null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:184: trailing whitespace.
+            throw new MalformedURLException(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:185: trailing whitespace.
+                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:186: trailing whitespace.
+        URL url = new URL(u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:187: trailing whitespace.
+        int batchSize = 30;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:188: trailing whitespace.
+        int numBatches = Math.round(mas.size() / batchSize + 1);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:189: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:190: trailing whitespace.
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191: trailing whitespace.
+        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192: trailing whitespace.
+        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:193: trailing whitespace.
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:194: trailing whitespace.
+        map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:195: trailing whitespace.
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:196: trailing whitespace.
+        map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:197: trailing whitespace.
+        map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:198: trailing whitespace.
+        map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:199: trailing whitespace.
+        int batchCt = 1;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:200: trailing whitespace.
+        JSONObject allRtn = new JSONObject();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:201: trailing whitespace.
+        allRtn.put("_batchSize", batchSize);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:202: trailing whitespace.
+        allRtn.put("_totalSize", mas.size());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:203: trailing whitespace.
+        JSONArray bres = new JSONArray();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:204: trailing whitespace.
+        for (int i = 0; i < mas.size(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:205: trailing whitespace.
+            MediaAsset ma = mas.get(i);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206: trailing whitespace.
+            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:207: trailing whitespace.
+            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:208: trailing whitespace.
+                IA.log(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:209: trailing whitespace.
+                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:210: trailing whitespace.
+                    + ma.getId());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:211: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:212: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:213: trailing whitespace.
+            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:214: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:215: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:216: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217: trailing whitespace.
+            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:218: trailing whitespace.
+            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:219: trailing whitespace.
+            map.get("image_gps_lat_list").add(ma.getLatitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:220: trailing whitespace.
+            map.get("image_gps_lon_list").add(ma.getLongitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:221: trailing whitespace.
+            DateTime t = ma.getDateTime();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:222: trailing whitespace.
+            if (t == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:223: trailing whitespace.
+                map.get("image_unixtime_list").add(null);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:224: trailing whitespace.
+            } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:225: trailing whitespace.
+                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:226: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:227: trailing whitespace.
+            int sendSize = map.get("image_uri_list").size();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:228: trailing whitespace.
+            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:229: trailing whitespace.
+                if (sendSize > 0) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:230: trailing whitespace.
+                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:231: trailing whitespace.
+                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:232: trailing whitespace.
+                        " batches)");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:233: trailing whitespace.
+                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:234: trailing whitespace.
+                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:235: trailing whitespace.
+/*
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:236: trailing whitespace.
+                    if (acmIds == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:237: trailing whitespace.
+                        IA.log(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:238: trailing whitespace.
+                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:239: trailing whitespace.
+ + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:240: trailing whitespace.
+                    } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:241: trailing whitespace.
+                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:242: trailing whitespace.
+                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:243: trailing whitespace.
+                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:244: trailing whitespace.
+                    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:245: trailing whitespace.
+ */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:246: trailing whitespace.
+                    bres.put(rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:247: trailing whitespace.
+                    // initialize for next batch (if any)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:248: trailing whitespace.
+                    map.put("image_uri_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:249: trailing whitespace.
+                    map.put("image_uuid_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:250: trailing whitespace.
+                    map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:251: trailing whitespace.
+                    map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:252: trailing whitespace.
+                    map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:253: trailing whitespace.
+                    // acmList = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:254: trailing whitespace.
+                } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:255: trailing whitespace.
+                    bres.put("EMPTY BATCH");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:256: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:257: trailing whitespace.
+                batchCt++;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:258: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:259: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:260: trailing whitespace.
+        allRtn.put("batchResults", bres);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:261: trailing whitespace.
+        return allRtn;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:262: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:263: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:264: trailing whitespace.
+    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:265: trailing whitespace.
+        Shepherd myShepherd)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:266: trailing whitespace.
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:267: trailing whitespace.
+        InvalidKeyException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:268: trailing whitespace.
+        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:269: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:270: trailing whitespace.
+        if (u == null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:271: trailing whitespace.
+            throw new MalformedURLException(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:272: trailing whitespace.
+                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:273: trailing whitespace.
+        URL url = new URL(u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:274: trailing whitespace.
+        int ct = 0;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:275: trailing whitespace.
+        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:276: trailing whitespace.
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:277: trailing whitespace.
+        List<String> iaAnnotIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:278: trailing whitespace.
+        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:279: trailing whitespace.
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:280: trailing whitespace.
+        map.put("image_uuid_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:281: trailing whitespace.
+        map.put("annot_species_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:282: trailing whitespace.
+        map.put("annot_bbox_list", new ArrayList<int[]>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:283: trailing whitespace.
+        map.put("annot_name_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:284: trailing whitespace.
+        map.put("annot_theta_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:285: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:286: trailing whitespace.
+        List<Annotation> acmList = new ArrayList<Annotation>(); // for rectifyAnnotationIds below
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:287: trailing whitespace.
+        for (Annotation ann : anns) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:288: trailing whitespace.
+            if (iaAnnotIds.contains(ann.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:289: trailing whitespace.
+            if (iaAnnotIds.contains(ann.getId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:290: trailing whitespace.
+            if (ann.getMediaAsset() == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:291: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:292: trailing whitespace.
+                    "; skipping!");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:293: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:294: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:295: trailing whitespace.
+            if (ann.getMediaAsset().getAcmId() == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:296: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:297: trailing whitespace.
+                    " (MediaAsset id=" + ann.getMediaAsset().getId() +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:298: trailing whitespace.
+                    " not added to IA?); skipping!");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:299: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:300: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:301: trailing whitespace.
+            if (!IBEISIA.validForIdentification(ann)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:302: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:303: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:304: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:305: trailing whitespace.
+            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:306: trailing whitespace.
+            if (iid == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:307: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:308: trailing whitespace.
+                    ann.getMediaAsset() + " on " + ann + "; skipping!");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:309: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:310: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:311: trailing whitespace.
+            acmList.add(ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:312: trailing whitespace.
+            map.get("image_uuid_list").add(iid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:313: trailing whitespace.
+            int[] bbox = ann.getBbox();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:314: trailing whitespace.
+            map.get("annot_bbox_list").add(bbox);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:315: trailing whitespace.
+            // yuck - IA class is not species
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:316: trailing whitespace.
+            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:317: trailing whitespace.
+            // better
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:318: trailing whitespace.
+            map.get("annot_species_list").add(ann.getIAClass());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:319: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:320: trailing whitespace.
+            map.get("annot_theta_list").add(ann.getTheta());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:321: trailing whitespace.
+            String name = ann.findIndividualId(myShepherd);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:322: trailing whitespace.
+            map.get("annot_name_list").add((name == null) ? "____" : name);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:323: trailing whitespace.
+            ct++;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:324: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:325: trailing whitespace.
+        // myShepherd.rollbackDBTransaction();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:326: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:327: trailing whitespace.
+        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:328: trailing whitespace.
+        if (ct < 1) return null; // null for "none to send" ?  is this cool?
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:329: trailing whitespace.
+        System.out.println("sendAnnotations(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:330: trailing whitespace.
+        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:331: trailing whitespace.
+        System.out.println("sendAnnotations() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:332: trailing whitespace.
+        List<String> acmIds = acmIdsFromResponse(rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:333: trailing whitespace.
+        if (acmIds == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:334: trailing whitespace.
+            IA.log(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:335: trailing whitespace.
+                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:336: trailing whitespace.
+                + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:337: trailing whitespace.
+        } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:338: trailing whitespace.
+            int numChanged = AcmUtil.rectifyAnnotationIds(acmList, acmIds, myShepherd);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:339: trailing whitespace.
+            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:340: trailing whitespace.
+                " Annotation(s) acmId(s) via rectifyAnnotationIds()");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:341: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:342: trailing whitespace.
+        return rtn;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:343: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:344: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:345: trailing whitespace.
+    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:346: trailing whitespace.
+        Shepherd myShepherd)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:347: trailing whitespace.
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:348: trailing whitespace.
+        InvalidKeyException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:349: trailing whitespace.
+        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:350: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:351: trailing whitespace.
+        if (u == null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:352: trailing whitespace.
+            throw new MalformedURLException(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:353: trailing whitespace.
+                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:354: trailing whitespace.
+        URL url = new URL(u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:355: trailing whitespace.
+        int ct = 0;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:356: trailing whitespace.
+        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:357: trailing whitespace.
+        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:358: trailing whitespace.
+        List<String> iaAnnotIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:359: trailing whitespace.
+        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:360: trailing whitespace.
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:361: trailing whitespace.
+        map.put("image_uuid_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:362: trailing whitespace.
+        map.put("annot_uuid_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:363: trailing whitespace.
+        map.put("annot_species_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:364: trailing whitespace.
+        map.put("annot_bbox_list", new ArrayList<int[]>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:365: trailing whitespace.
+        map.put("annot_name_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:366: trailing whitespace.
+        map.put("annot_theta_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:367: trailing whitespace.
+        for (Annotation ann : anns) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:368: trailing whitespace.
+            if (iaAnnotIds.contains(ann.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:369: trailing whitespace.
+            if (iaAnnotIds.contains(ann.getId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:370: trailing whitespace.
+            if (ann.getMediaAsset() == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:371: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:372: trailing whitespace.
+                    ann + "; skipping!");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:373: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:374: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:375: trailing whitespace.
+            if (!IBEISIA.validForIdentification(ann)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:376: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:377: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:378: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:379: trailing whitespace.
+            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:380: trailing whitespace.
+            if (iid == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:381: trailing whitespace.
+                IA.log(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:382: trailing whitespace.
+                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:383: trailing whitespace.
+                    + ann.getMediaAsset() + " on " + ann + "; skipping!");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:384: trailing whitespace.
+                continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:385: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:386: trailing whitespace.
+            map.get("image_uuid_list").add(iid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:387: trailing whitespace.
+            JSONObject aid = toFancyUUID(ann.getId());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:388: trailing whitespace.
+            map.get("annot_uuid_list").add(aid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:389: trailing whitespace.
+            int[] bbox = ann.getBbox();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:390: trailing whitespace.
+            map.get("annot_bbox_list").add(bbox);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:391: trailing whitespace.
+            // yuck - IA class is not species
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:392: trailing whitespace.
+            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:393: trailing whitespace.
+            // better
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:394: trailing whitespace.
+            map.get("annot_species_list").add(ann.getIAClass());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:395: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:396: trailing whitespace.
+            map.get("annot_theta_list").add(ann.getTheta());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:397: trailing whitespace.
+            String name = ann.findIndividualId(myShepherd);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:398: trailing whitespace.
+            map.get("annot_name_list").add((name == null) ? "____" : name);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:399: trailing whitespace.
+            ct++;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:400: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:401: trailing whitespace.
+        // myShepherd.rollbackDBTransaction();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:402: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:403: trailing whitespace.
+        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:404: trailing whitespace.
+        if (ct < 1) return null; // null for "none to send" ?  is this cool?
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:405: trailing whitespace.
+        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:406: trailing whitespace.
+        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:407: trailing whitespace.
+        System.out.println("sendAnnotationsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:408: trailing whitespace.
+        checkForcedIds(map.get("annot_uuid_list"), rtn.optJSONArray("response"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:409: trailing whitespace.
+        return rtn;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:410: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:411: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:412: trailing whitespace.
+    // ------------------------------------------------------------------
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:413: trailing whitespace.
+    // ml-service migration v2: no-Shepherd WBIA registration helpers.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:414: trailing whitespace.
+    //
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:415: trailing whitespace.
+    // The polling thread in StartupWildbook splits the work into:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:416: trailing whitespace.
+    //   Phase A (write tx) - load DTO + close.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:417: trailing whitespace.
+    //   Phase B (no DB)    - call into the helpers below.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:418: trailing whitespace.
+    //   Phase C (write tx) - persist result.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:419: trailing whitespace.
+    // Phase B must not hold a Shepherd transaction across the WBIA call.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:420: trailing whitespace.
+    // ------------------------------------------------------------------
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:421: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:422: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:423: trailing whitespace.
+     * Outcome of a Phase-B WBIA registration attempt.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:424: trailing whitespace.
+     * REGISTERED_OK              - POST succeeded, ids match.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425: trailing whitespace.
+     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426: trailing whitespace.
+     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427: trailing whitespace.
+     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:428: trailing whitespace.
+     *                              (id mismatch, length mismatch, missing field).
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:429: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430: trailing whitespace.
+    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:431: trailing whitespace.
+        REGISTERED_OK,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432: trailing whitespace.
+        REGISTERED_ALREADY_PRESENT,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433: trailing whitespace.
+        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434: trailing whitespace.
+        RESPONSE_BAD,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:435: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:436: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:437: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:438: trailing whitespace.
+     * Plain-data DTO that holds everything Phase B needs about one
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:439: trailing whitespace.
+     * Annotation. Built under a Shepherd transaction in Phase A, then
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:440: trailing whitespace.
+     * passed across the close/open boundary into Phase B.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:441: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:442: trailing whitespace.
+     * <p>Phase A is responsible for pre-validating that all required
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:443: trailing whitespace.
+     * fields are populated; Phase B treats the DTO as opaque and does
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:444: trailing whitespace.
+     * not re-touch any JDO-managed state.</p>
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:445: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446: trailing whitespace.
+    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:447: trailing whitespace.
+        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:448: trailing whitespace.
+        public final String annotationAcmId;    // Annotation.acmId, may differ from id on legacy rows
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:449: trailing whitespace.
+        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:450: trailing whitespace.
+        public final int[]  bbox;               // x,y,w,h
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:451: trailing whitespace.
+        public final double theta;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:452: trailing whitespace.
+        public final String iaClass;            // species/class string
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:453: trailing whitespace.
+        public final String individualName;     // "____" if absent
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:454: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455: trailing whitespace.
+        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:456: trailing whitespace.
+            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:457: trailing whitespace.
+            String individualName) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:458: trailing whitespace.
+            this.annotationId    = annotationId;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:459: trailing whitespace.
+            this.annotationAcmId = annotationAcmId;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:460: trailing whitespace.
+            this.mediaAssetAcmId = mediaAssetAcmId;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:461: trailing whitespace.
+            this.bbox            = bbox;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:462: trailing whitespace.
+            this.theta           = theta;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:463: trailing whitespace.
+            this.iaClass         = iaClass;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:464: trailing whitespace.
+            this.individualName  = individualName;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:465: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:466: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:467: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:468: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:469: trailing whitespace.
+     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:470: trailing whitespace.
+     * fetch failure rather than returning an empty list. Phase B needs
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:471: trailing whitespace.
+     * this so a network failure during the already-present check is
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:472: trailing whitespace.
+     * not silently treated as "go ahead and POST".
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:473: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:474: trailing whitespace.
+     * <p>Honors the 15-minute QueryCache the same way the lenient
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:475: trailing whitespace.
+     * variant does, so a cache hit avoids the network entirely.</p>
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:476: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477: trailing whitespace.
+    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:478: trailing whitespace.
+        String cacheName = "iaAnnotationIds";
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479: trailing whitespace.
+        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:480: trailing whitespace.
+        // context that has never been initialized; treat that as "no cache"
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:481: trailing whitespace.
+        // rather than NPE-ing out and aborting the poll cycle.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:482: trailing whitespace.
+        QueryCache qc = null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:483: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484: trailing whitespace.
+            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:485: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:486: trailing whitespace.
+            // Defensive: cache factory init can fail; degrade to no-cache.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:487: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488: trailing whitespace.
+        if (qc != null && qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:489: trailing whitespace.
+            System.currentTimeMillis() <
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:490: trailing whitespace.
+            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:491: trailing whitespace.
+            try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:492: trailing whitespace.
+                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:493: trailing whitespace.
+                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:494: trailing whitespace.
+                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:495: trailing whitespace.
+                return parseAnnotationIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:496: trailing whitespace.
+            } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497: trailing whitespace.
+                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:498: trailing whitespace.
+                    + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:499: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:500: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:501: trailing whitespace.
+        JSONArray jids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:502: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:503: trailing whitespace.
+            jids = apiGetJSONArray("/api/annot/json/", context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:504: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:505: trailing whitespace.
+            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:506: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:507: trailing whitespace.
+        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:508: trailing whitespace.
+        if (qc != null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:509: trailing whitespace.
+            try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:510: trailing whitespace.
+                org.datanucleus.api.rest.orgjson.JSONObject jobj =
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:511: trailing whitespace.
+                    new org.datanucleus.api.rest.orgjson.JSONObject();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:512: trailing whitespace.
+                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:513: trailing whitespace.
+                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:514: trailing whitespace.
+                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:515: trailing whitespace.
+                qc.addCachedQuery(cq);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:516: trailing whitespace.
+            } catch (Exception cacheEx) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:517: trailing whitespace.
+                // Cache store failure is non-fatal; we still have the ids.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:518: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:519: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:520: trailing whitespace.
+        return parseAnnotationIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:521: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:522: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:523: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:524: trailing whitespace.
+     * Strict element parser: throws IOException if any element is not a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:525: trailing whitespace.
+     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:526: trailing whitespace.
+     * skips/null-pads malformed entries, which is fine for legacy paths but
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:527: trailing whitespace.
+     * would let a corrupt response masquerade as "annotation not yet
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:528: trailing whitespace.
+     * registered" in the polling thread's already-present check.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:529: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530: trailing whitespace.
+    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:531: trailing whitespace.
+        List<String> ids = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:532: trailing whitespace.
+        if (jids == null) return ids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:533: trailing whitespace.
+        for (int i = 0; i < jids.length(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:534: trailing whitespace.
+            JSONObject jo = jids.optJSONObject(i);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:535: trailing whitespace.
+            if (jo == null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:536: trailing whitespace.
+                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:537: trailing whitespace.
+            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:538: trailing whitespace.
+            if (decoded == null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:539: trailing whitespace.
+                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:540: trailing whitespace.
+            ids.add(decoded);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:541: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:542: trailing whitespace.
+        return ids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:543: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:544: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:545: trailing whitespace.
+    static List<String> parseAnnotationIdsArray(JSONArray jids) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:546: trailing whitespace.
+        List<String> ids = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:547: trailing whitespace.
+        if (jids == null) return ids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:548: trailing whitespace.
+        for (int i = 0; i < jids.length(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:549: trailing whitespace.
+            JSONObject jo = jids.optJSONObject(i);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:550: trailing whitespace.
+            if (jo != null) ids.add(fromFancyUUID(jo));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:551: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:552: trailing whitespace.
+        return ids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:553: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:554: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:555: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:556: trailing whitespace.
+     * Build the forced-id POST body for a single DTO. Pure function;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:557: trailing whitespace.
+     * factored out so unit tests can verify the request shape without
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:558: trailing whitespace.
+     * a network round trip.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:559: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:560: trailing whitespace.
+    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:561: trailing whitespace.
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:562: trailing whitespace.
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:563: trailing whitespace.
+        map.put("annot_uuid_list", new ArrayList<JSONObject>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:564: trailing whitespace.
+        map.put("annot_species_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:565: trailing whitespace.
+        map.put("annot_bbox_list", new ArrayList<int[]>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:566: trailing whitespace.
+        map.put("annot_name_list", new ArrayList<String>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:567: trailing whitespace.
+        map.put("annot_theta_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:568: trailing whitespace.
+        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:569: trailing whitespace.
+        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:570: trailing whitespace.
+        map.get("annot_species_list").add(dto.iaClass);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:571: trailing whitespace.
+        map.get("annot_bbox_list").add(dto.bbox);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:572: trailing whitespace.
+        map.get("annot_name_list").add(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:573: trailing whitespace.
+            (dto.individualName == null) ? "____" : dto.individualName);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:574: trailing whitespace.
+        map.get("annot_theta_list").add(dto.theta);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:575: trailing whitespace.
+        return map;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:576: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:577: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:578: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:579: trailing whitespace.
+     * Validate a forced-id response. Throws on any contract violation
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:580: trailing whitespace.
+     * (length mismatch, missing entry, id mismatch). Pure function.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:581: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:582: trailing whitespace.
+    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:583: trailing whitespace.
+        if (resp == null) throw new IOException("null forced-id response");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:584: trailing whitespace.
+        if (resp.has("status")) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:585: trailing whitespace.
+            JSONObject status = resp.optJSONObject("status");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:586: trailing whitespace.
+            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:587: trailing whitespace.
+                throw new IOException("forced-id response status.success=false: " + resp);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:588: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:589: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:590: trailing whitespace.
+        JSONArray respArr = resp.optJSONArray("response");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:591: trailing whitespace.
+        if (respArr == null) throw new IOException("no response array: " + resp);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:592: trailing whitespace.
+        if (respArr.length() != 1)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:593: trailing whitespace.
+            throw new IOException("expected response array length 1, got " + respArr.length());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:594: trailing whitespace.
+        JSONObject jid = respArr.optJSONObject(0);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:595: trailing whitespace.
+        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:596: trailing whitespace.
+        String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:597: trailing whitespace.
+        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:598: trailing whitespace.
+        if (!respId.equals(sentAnnotId))
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:599: trailing whitespace.
+            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:600: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:601: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:602: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:603: trailing whitespace.
+     * Phase B entry point. Does the already-present check, builds the
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:604: trailing whitespace.
+     * forced-id POST, fires it, and classifies the outcome. Does NOT
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:605: trailing whitespace.
+     * touch any Shepherd or JDO state; callers must hand it a DTO that
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:606: trailing whitespace.
+     * was pre-validated and detached in Phase A.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:607: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608: trailing whitespace.
+    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609: trailing whitespace.
+        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:610: trailing whitespace.
+        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:611: trailing whitespace.
+        if (u == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:612: trailing whitespace.
+            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613: trailing whitespace.
+            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:614: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:615: trailing whitespace.
+        List<String> known;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:616: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617: trailing whitespace.
+            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:618: trailing whitespace.
+        } catch (IOException ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:619: trailing whitespace.
+            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:620: trailing whitespace.
+                ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621: trailing whitespace.
+            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:622: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:623: trailing whitespace.
+        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:624: trailing whitespace.
+        // only check the annotation's id and acmId here. Comparing against
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:625: trailing whitespace.
+        // the media-asset's acmId is wrong - that would compare an image
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:626: trailing whitespace.
+        // identifier against a list of annotation identifiers.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:627: trailing whitespace.
+        if (known.contains(dto.annotationId) ||
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:628: trailing whitespace.
+            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629: trailing whitespace.
+            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:630: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:631: trailing whitespace.
+        URL url;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:632: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:633: trailing whitespace.
+            url = new URL(u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:634: trailing whitespace.
+        } catch (MalformedURLException ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:635: trailing whitespace.
+            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636: trailing whitespace.
+            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:637: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:638: trailing whitespace.
+        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:639: trailing whitespace.
+        JSONObject rtn;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:640: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:641: trailing whitespace.
+            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:642: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:643: trailing whitespace.
+            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644: trailing whitespace.
+            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:645: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:646: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:647: trailing whitespace.
+            validateForcedResponse(dto.annotationId, rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:648: trailing whitespace.
+        } catch (IOException ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:649: trailing whitespace.
+            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650: trailing whitespace.
+            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:651: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652: trailing whitespace.
+        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:653: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:654: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:655: trailing whitespace.
+    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:656: trailing whitespace.
+    throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:657: trailing whitespace.
+        if ((sentIds == null) || (respArr == null))
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:658: trailing whitespace.
+            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:659: trailing whitespace.
+        if (sentIds.size() != respArr.length())
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:660: trailing whitespace.
+            throw new IOException("args diff length: " + sentIds.size() + " != " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:661: trailing whitespace.
+                    respArr.length());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:662: trailing whitespace.
+        for (int i = 0; i < sentIds.size(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:663: trailing whitespace.
+            String sentId = fromFancyUUID(sentIds.get(i));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:664: trailing whitespace.
+            if (sentId == null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:665: trailing whitespace.
+                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:666: trailing whitespace.
+            JSONObject jid = respArr.optJSONObject(i);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:667: trailing whitespace.
+            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:668: trailing whitespace.
+            String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:669: trailing whitespace.
+            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:670: trailing whitespace.
+            if (!respId.equals(sentId))
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:671: trailing whitespace.
+                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:672: trailing whitespace.
+                        "; respId=" + respId);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:673: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:674: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:675: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:676: trailing whitespace.
+    public static List<String> acmIdsFromResponse(JSONObject rtn) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:677: trailing whitespace.
+        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:678: trailing whitespace.
+        List<String> ids = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:679: trailing whitespace.
+        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:680: trailing whitespace.
+            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:681: trailing whitespace.
+                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:682: trailing whitespace.
+                ids.add(null);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:683: trailing whitespace.
+            } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:684: trailing whitespace.
+                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:685: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:686: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:687: trailing whitespace.
+        System.out.println("fromResponse ---> " + ids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688: trailing whitespace.
+        return ids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:690: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:691: trailing whitespace.
+    // instance version of below (since context is known)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692: trailing whitespace.
+    public List<String> iaAnnotationIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693: trailing whitespace.
+        return iaAnnotationIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:694: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:695: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:696: trailing whitespace.
+    // this fails "gracefully" with empty list if network fubar.  bad decision?
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:697: trailing whitespace.
+    public static List<String> iaAnnotationIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:698: trailing whitespace.
+        List<String> ids = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:699: trailing whitespace.
+        JSONArray jids = null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:700: trailing whitespace.
+        String cacheName = "iaAnnotationIds";
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:702: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:703: trailing whitespace.
+            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:704: trailing whitespace.
+            if (qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:705: trailing whitespace.
+                System.currentTimeMillis() <
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:706: trailing whitespace.
+                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:707: trailing whitespace.
+                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:708: trailing whitespace.
+                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709: trailing whitespace.
+                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:710: trailing whitespace.
+            } else {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:711: trailing whitespace.
+                jids = apiGetJSONArray("/api/annot/json/", context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:712: trailing whitespace.
+                if (jids != null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:713: trailing whitespace.
+                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:714: trailing whitespace.
+                        new org.datanucleus.api.rest.orgjson.JSONObject();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715: trailing whitespace.
+                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716: trailing whitespace.
+                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:717: trailing whitespace.
+                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:718: trailing whitespace.
+                    qc.addCachedQuery(cq);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:719: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:720: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:721: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:722: trailing whitespace.
+            ex.printStackTrace();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723: trailing whitespace.
+            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724: trailing whitespace.
+                ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:725: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:726: trailing whitespace.
+        if (jids != null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:727: trailing whitespace.
+            try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:728: trailing whitespace.
+                for (int i = 0; i < jids.length(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:729: trailing whitespace.
+                    if (jids.optJSONObject(i) != null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730: trailing whitespace.
+                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:731: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732: trailing whitespace.
+            } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:733: trailing whitespace.
+                ex.printStackTrace();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:734: trailing whitespace.
+                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:735: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:736: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:737: trailing whitespace.
+        return ids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:738: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:739: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:740: trailing whitespace.
+    // as above, but images
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741: trailing whitespace.
+    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742: trailing whitespace.
+        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:743: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:744: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745: trailing whitespace.
+    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:746: trailing whitespace.
+        List<String> ids = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:747: trailing whitespace.
+        JSONArray jids = null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:748: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:749: trailing whitespace.
+        try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:750: trailing whitespace.
+            jids = apiGetJSONArray("/api/image/json/", context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:751: trailing whitespace.
+        } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:752: trailing whitespace.
+            ex.printStackTrace();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753: trailing whitespace.
+            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:754: trailing whitespace.
+                ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:755: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:756: trailing whitespace.
+        if (jids != null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:757: trailing whitespace.
+            try {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:758: trailing whitespace.
+                for (int i = 0; i < jids.length(); i++) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:759: trailing whitespace.
+                    if (jids.optJSONObject(i) != null)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:760: trailing whitespace.
+                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:761: trailing whitespace.
+                }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:762: trailing whitespace.
+            } catch (Exception ex) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:763: trailing whitespace.
+                ex.printStackTrace();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764: trailing whitespace.
+                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:765: trailing whitespace.
+            }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:766: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:767: trailing whitespace.
+        return ids;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:768: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:769: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:770: trailing whitespace.
+    public JSONArray apiGetJSONArray(String urlSuffix)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:771: trailing whitespace.
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:772: trailing whitespace.
+        InvalidKeyException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:773: trailing whitespace.
+        return apiGetJSONArray(urlSuffix, this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:774: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:775: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:776: trailing whitespace.
+    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:777: trailing whitespace.
+    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:778: trailing whitespace.
+        InvalidKeyException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:779: trailing whitespace.
+        URL u = IBEISIA.iaURL(context, urlSuffix);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:780: trailing whitespace.
+        JSONObject rtn = RestClient.get(u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:781: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:782: trailing whitespace.
+        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:783: trailing whitespace.
+            (rtn.optJSONArray("response") == null) ||
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:784: trailing whitespace.
+            !rtn.getJSONObject("status").optBoolean("success", false)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:785: trailing whitespace.
+            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:786: trailing whitespace.
+                rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:787: trailing whitespace.
+            return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:788: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:789: trailing whitespace.
+        return rtn.getJSONArray("response");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:790: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:791: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:792: trailing whitespace.
+    public static String fromFancyUUID(JSONObject u) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:793: trailing whitespace.
+        if (u == null) return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:794: trailing whitespace.
+        return u.optString("__UUID__", null);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:795: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:796: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:797: trailing whitespace.
+    public static JSONObject toFancyUUID(String u) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:798: trailing whitespace.
+        JSONObject j = new JSONObject();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:799: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:800: trailing whitespace.
+        j.put("__UUID__", u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:801: trailing whitespace.
+        return j;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:802: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:803: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804: trailing whitespace.
+    /**
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:805: trailing whitespace.
+     * Build the URL string WBIA expects in {@code image_uri_list}. The
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:806: trailing whitespace.
+     * double-encoded "?" pattern preserves filenames that contain "?" so
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:807: trailing whitespace.
+     * WBIA's HTTP layer doesn't truncate them at the query boundary.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:808: trailing whitespace.
+     *
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:809: trailing whitespace.
+     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:810: trailing whitespace.
+     * {@code null}. Promoted from {@code private Object} to
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:811: trailing whitespace.
+     * {@code public String} (and the leading-NPE on {@code curl.toString()}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:812: trailing whitespace.
+     * tightened) so the ml-service v2 WBIA registration polling thread
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:813: trailing whitespace.
+     * can call it from Phase A while building the {@link WbiaRegisterRequest}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:814: trailing whitespace.
+     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:815: trailing whitespace.
+     */
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:816: trailing whitespace.
+    public static String mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:817: trailing whitespace.
+        if (ma == null) return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:818: trailing whitespace.
+        URL curl = ma.webURL();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:819: trailing whitespace.
+        if (curl == null) return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:820: trailing whitespace.
+        String urlStr = curl.toString();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:821: trailing whitespace.
+        if (urlStr == null) return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822: trailing whitespace.
+        // THIS WILL BREAK if you need to append a query to the filename...
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:823: trailing whitespace.
+        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:824: trailing whitespace.
+        return urlStr.replaceAll("\\?", "%3F");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:825: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:827: trailing whitespace.
+    // basically "should we send to IA?"
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828: trailing whitespace.
+    public static boolean validMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:829: trailing whitespace.
+        if (ma == null) return false;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:830: trailing whitespace.
+        if (!ma.isMimeTypeMajor("image")) return false;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:831: trailing whitespace.
+        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:832: trailing whitespace.
+        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:833: trailing whitespace.
+            System.out.println(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:834: trailing whitespace.
+                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:835: trailing whitespace.
+                ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:836: trailing whitespace.
+            return false;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:837: trailing whitespace.
+        }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:838: trailing whitespace.
+        return true;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:839: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:840: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:841: trailing whitespace.
+    // this is used to give a string to IA for annot_species_list specifially
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:842: trailing whitespace.
+    // hence the term "IASpecies"
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:843: trailing whitespace.
+    public static String getIASpecies(Annotation ann, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844: trailing whitespace.
+        // NOTE: returning null here is probably "bad" btw....
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:845: trailing whitespace.
+        org.ecocean.Encounter enc = ann.findEncounter(myShepherd);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:846: trailing whitespace.
+        if (enc == null) return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:847: trailing whitespace.
+        String ts = enc.getTaxonomyString();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:848: trailing whitespace.
+        if (ts == null) return null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:849: trailing whitespace.
+        return ts.replaceAll(" ", "_");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:850: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:851: trailing whitespace.
+
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:852: trailing whitespace.
+    public String toString() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:853: trailing whitespace.
+        return new ToStringBuilder(this)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:854: trailing whitespace.
+                   .append("WildbookIAM IA Plugin")
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:855: trailing whitespace.
+                   .toString();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:856: trailing whitespace.
+    }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:857: trailing whitespace.
+}

codex
The behavior change itself is small; the bigger review finding is mechanical: the tracked file has LF in index and CRLF in the worktree, so `git diff --check` reports every line as trailing whitespace. I’m also comparing the older IBEISIA helper to confirm the LocalAssetStore branch history.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '680,715p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '820,870p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-wbia-image-registration-design.md | sed -n '40,70p;88,105p;140,160p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/LocalAssetStore.java | sed -n '235,265p'" in /mnt/c/Wildbook-clean2
 succeeded in 197ms:
   680	                break;
   681	            }
   682	        }
   683	        if (shapeOnly || (found == null)) return found;
   684	        // TODO what else do we want to compare here?
   685	        if (!found.equalsIAClass(match)) return null;
   686	        if (!found.equalsViewpoint(match)) return null;
   687	        return found;
   688	    }
   689	
   690	    /**
   691	     * Return a full web-accessible url to the asset, or null if the asset is not web-accessible. NOTE: now you should *almost always* use .safeURL()
   692	     * to return something to a user -- this will hide original files when necessary
   693	     */
   694	    public URL webURL() {
   695	        if (store == null) {
   696	            System.out.println("MediaAsset " + this.getUUID() + " has no store!");
   697	            return null;
   698	        }
   699	        try {
   700	            int i = ((store.getUsage() ==
   701	                null) ? -1 : store.getUsage().indexOf("PLACEHOLDERHACK:"));
   702	            if (i == 0) {
   703	                String localURL = store.getUsage().substring(16);
   704	                return new URL(localURL);
   705	            }
   706	        } catch (java.net.MalformedURLException ex) {}
   707	        return store.webURL(this);
   708	    }
   709	
   710	    // the primary purpose here is to mask (i.e. never send) the original (uploaded) image file.
   711	    // right now "master" labelled image is used, if available, otherwise children are chosen by allChildTypes() order....
   712	    public URL safeURL(Shepherd myShepherd, HttpServletRequest request, String bestType) {
   713	        MediaAsset ma = bestSafeAsset(myShepherd, request, bestType);
   714	
   715	        if (ma == null) return null;

 succeeded in 208ms:
   820	                    anns.toString());
   821	                JSONObject srtn = null;
   822	                try {
   823	                    __sendAnnotations(anns, context, myShepherd);
   824	                } catch (Exception ex) {}
   825	                System.out.println(" returned --> " + srtn);
   826	                if ((srtn != null) && (srtn.getJSONObject("status") != null) &&
   827	                    srtn.getJSONObject("status").getBoolean("success")) tryAgain = true;    // it "worked"?
   828	            }
   829	        }
   830	        System.out.println("iaCheckMissing -> " + tryAgain);
   831	        return tryAgain;
   832	    }
   833	
   834	    private static Object mediaAssetToUri(MediaAsset ma) {
   835	        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
   836	        URL curl = ma.containerURLIfPresent();
   837	
   838	        if (curl == null) curl = ma.webURL();
   839	        if (ma.getStore() instanceof LocalAssetStore) {
   840	            // return ma.localPath().toString(); //nah, lets skip local and go for "url" flavor?
   841	            if (curl == null) return null;
   842	            return curl.toString();
   843	        } else {
   844	            if (curl == null) return null;
   845	            return curl.toString();
   846	        }
   847	    }
   848	
   849	    // If you realllllly want to send species I'll just swallow it.
   850	    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
   851	        ArrayList<Annotation> tanns, JSONObject queryConfigDict, JSONObject userConfidence,
   852	        Shepherd myShepherd, String species, Task task, String baseUrl, boolean fastlane) {
   853	        System.out.println(
   854	            "INFO: You no longer need to send species with call to beginIdentifyAnnotations. It is derived from the Annotation's Encounters.");
   855	        return beginIdentifyAnnotations(qanns, tanns, queryConfigDict, userConfidence, myShepherd,
   856	                task, baseUrl, fastlane);
   857	    }
   858	
   859	    // trying to optimize the original beginIdentifyAnnotations()  [above]
   860	    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
   861	        ArrayList<Annotation> tanns, JSONObject queryConfigDict, JSONObject userConfidence,
   862	        Shepherd myShepherd, Task task, String baseUrl, boolean fastlane) {
   863	        long tt = System.currentTimeMillis();
   864	
   865	        if (!isIAPrimed())
   866	            System.out.println("WARNING: beginIdentifyAnnotations() called without IA primed");
   867	        // possibly could exclude qencs from tencs?
   868	        String jobID = "-1";
   869	        JSONObject results = new JSONObject();
   870	        results.put("success", false); // pessimism!

 succeeded in 210ms:
    40	  thread already iterates pending work at a sensible cadence.
    41	- Adding a new JDO column for image-registration state. The existing
    42	  `wbiaRegistered` / `wbiaRegisterAttempts` columns govern both phases
    43	  atomically.
    44	
    45	## Audit: what already exists
    46	
    47	| Helper | Status | Reused as-is |
    48	|---|---|---|
    49	| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
    50	| `WildbookIAM.sendMediaAssetsForceId(ArrayList<MediaAsset>, boolean)` | exists, batch POST to `/api/image/json/`, sends our acmId via `image_uuid_list` | The POST body shape and `toFancyUUID` wrapping logic lift line-for-line into the new DTO-based variant |
    51	| `WildbookIAM.mediaAssetToUri(MediaAsset)` | exists but is **`private`** at line 804. Returns the double-encoded web URL string. Note: `ma.webURL()` can return null and the existing helper dereferences without checking — a long-standing latent NPE. | Promote to `public static String` (signature change from `Object` return; tighten the null-check). Phase A then calls it directly to capture `imageUri` into the DTO. |
    52	| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
    53	| `AcmUtil` | exists | Not relevant — its `rectify*` utilities are for syncing acmId values, but v2 owns the acmId so no rectification needed |
    54	
    55	## Design
    56	
    57	### DTO extension
    58	
    59	Extend `WildbookIAM.WbiaRegisterRequest` with four image-side fields,
    60	populated in Phase A:
    61	
    62	```java
    63	public final String imageUri;             // mediaAssetToUri(ma) result
    64	public final Double imageLatitude;        // ma.getLatitude(), nullable
    65	public final Double imageLongitude;       // ma.getLongitude(), nullable
    66	public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
    67	```
    68	
    69	`mediaAssetUuid` is not needed as a separate field because v2's
    70	convention is `MediaAsset.acmId == MediaAsset.uuid` (commit `2a3eab63a`);
    88	```
    89	
    90	If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
    91	behavior — keeps the ineligible-park path consistent).
    92	
    93	Then capture the image fields into the DTO:
    94	
    95	```java
    96	String imageUri = (String) WildbookIAM.mediaAssetToUri(ma);  // method returns Object; cast
    97	DateTime dt = ma.getDateTime();
    98	Long dateTimeMillis = (dt == null) ? null : dt.getMillis();
    99	WbiaRegisterRequest dto = new WbiaRegisterRequest(
   100	    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy, ann.getTheta(),
   101	    ann.getIAClass(), name,
   102	    imageUri, ma.getLatitude(), ma.getLongitude(), dateTimeMillis  // NEW
   103	);
   104	```
   105	
   140	        if (!isStatusSuccess(rtn)) return false;
   141	        return true;
   142	    } catch (Exception ex) {
   143	        IA.log("WARNING: sendMediaAssetByDto() POST failed: " + ex.getMessage());
   144	        return false;
   145	    }
   146	}
   147	
   148	static HashMap<String, ArrayList> buildSingleImageRequestMap(WbiaRegisterRequest dto) {
   149	    HashMap<String, ArrayList> map = new HashMap<>();
   150	    map.put("image_uri_list", new ArrayList<String>());
   151	    map.put("image_uuid_list", new ArrayList<JSONObject>());
   152	    map.put("image_unixtime_list", new ArrayList<Integer>());
   153	    map.put("image_gps_lat_list", new ArrayList<Double>());
   154	    map.put("image_gps_lon_list", new ArrayList<Double>());
   155	    map.get("image_uri_list").add(dto.imageUri);
   156	    map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
   157	    map.get("image_unixtime_list").add(
   158	        dto.imageDateTimeMillis == null ? null
   159	                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
   160	    map.get("image_gps_lat_list").add(dto.imageLatitude);

 succeeded in 215ms:
   235	        if (!full.toFile().exists())
   236	            throw new IllegalArgumentException(full + " does not exist");
   237	        return result;
   238	    }
   239	
   240	    /**
   241	     * Return a full URL to the given MediaAsset, or null if the asset is not web-accessible.
   242	     */
   243	    @Override public URL webURL(final MediaAsset ma) {
   244	        if (ma == null) System.out.println("MediaAsset is null in LocalAssetStore.webURL");
   245	        if ((webRoot() == null) || (ma == null)) return null;
   246	        Path path = pathFromParameters(ma.getParameters());
   247	        // System.out.println("making webURL with pathFromParameters(mediaAsset) = "+path);
   248	        if (path == null) return null;
   249	        try {
   250	            URL url;
   251	            if (!path.startsWith("/")) {
   252	                url = new URL(webRoot() + "/" + path.toString());
   253	            } else {
   254	                url = new URL(webRoot() + path.toString());
   255	            }
   256	            log.debug("url: " + url.toString());
   257	            return url;
   258	        } catch (MalformedURLException e) {
   259	            log.warn("Can't construct web path", e);
   260	            return null;
   261	        }
   262	    }
   263	
   264	    @Override
   265	/*

exec
/bin/bash -lc 'rg -n "Open questions|question|C2|Track 1|mediaAssetToUri|LocalAssetStore|JavaDoc|Javadoc" docs/plans/2026-05-18-wbia-image-registration-design.md docs/plans/2026-05-09-ml-service-migration-v2.md' in /mnt/c/Wildbook-clean2
 succeeded in 288ms:
docs/plans/2026-05-18-wbia-image-registration-design.md:51:| `WildbookIAM.mediaAssetToUri(MediaAsset)` | exists but is **`private`** at line 804. Returns the double-encoded web URL string. Note: `ma.webURL()` can return null and the existing helper dereferences without checking — a long-standing latent NPE. | Promote to `public static String` (signature change from `Object` return; tighten the null-check). Phase A then calls it directly to capture `imageUri` into the DTO. |
docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-wbia-image-registration-design.md:63:public final String imageUri;             // mediaAssetToUri(ma) result
docs/plans/2026-05-18-wbia-image-registration-design.md:96:String imageUri = (String) WildbookIAM.mediaAssetToUri(ma);  // method returns Object; cast
docs/plans/2026-05-18-wbia-image-registration-design.md:295:## Open questions for Codex

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-wbia-image-registration-design.md | sed -n '260,320p'" in /mnt/c/Wildbook-clean2
 succeeded in 255ms:
   260	  image-ids list; verify sendMediaAssetByDto called; verify
   261	  iaAnnotationIdsStrict called after (Phase 1 reached).
   262	- `registerOneByDto_phase0_get_fails_returns_network_fail` — mock GET to
   263	  throw IOException; verify NETWORK_FAIL returned without any POST.
   264	- `registerOneByDto_phase0_post_fails_returns_network_fail` — mock POST
   265	  to throw; verify NETWORK_FAIL; verify no annotation POST attempted.
   266	- `registerOneByDto_phase0_then_phase1_success_full_sequence` — both
   267	  succeed → REGISTERED_OK.
   268	
   269	Layer 3 — Phase A + outcome integration (new test class
   270	`WbiaRegistrationPhaseAImageFieldsTest` or appended to existing): verify
   271	the DTO captures `imageUri`, lat, lon, datetimeMillis correctly from a
   272	mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
   273	
   274	## Failure modes
   275	
   276	| Scenario | Outcome | Why |
   277	|---|---|---|
   278	| MediaAsset has null acmId at Phase A | Park (MAX_ATTEMPTS) | Already handled by existing eligibility check |
   279	| MediaAsset fails validMediaAsset at Phase A | Park (MAX_ATTEMPTS) | New eligibility check added |
   280	| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
   281	| Image POST fails | NETWORK_FAIL | Retry next tick |
   282	| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
   283	| Image POST succeeds + cache invalidation not supported | Functional but mildly wasteful | Per-annotation re-check until cache TTL expires |
   284	
   285	## Codex review gates
   286	
   287	Per the locked-in workflow:
   288	
   289	1. Design review: this document. Codex green-lights before any code.
   290	2. Code review: single implementation commit (DTO additions + new
   291	   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
   292	   before merge.
   293	3. Post-commit verify: if Codex finds issues, fix-pass commit + re-review.
   294	
   295	## Open questions for Codex
   296	
   297	1. **QueryCache invalidation API**: does `QueryCacheFactory.getQueryCache`
   298	   expose a way to remove a single named cached query? If not, the
   299	   "minor staleness" trade-off above is acceptable. Worth verifying
   300	   before implementation.
   301	2. **`isStatusSuccess` placement**: this is a generic WBIA-response
   302	   helper. Should it live in `WildbookIAM` (alongside the other request/
   303	   response helpers) or in `IBEISIA` (which has historical knowledge of
   304	   the WBIA wrapper shape)? Current draft says `WildbookIAM` for locality;
   305	   open to moving it.
   306	3. **Strict-parser duplication**: `parseAnnotationIdsArrayStrict` already
   307	   exists from c11 fix-pass. The new `parseImageIdsArrayStrict` would be
   308	   almost identical. Worth factoring into a shared private helper? Or
   309	   accept duplication for grep-readability (image-ids and annotation-ids
   310	   conceptually different, even if mechanically identical today).
   311	4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
   312	   Phase A's eligibility check rather than Phase B. Acceptable, or
   313	   should Phase B also defensively check (in case state changed between
   314	   Phase A and Phase B)? The window is short (Phase A commits, Phase B
   315	   immediately calls HTTP), but the strict no-trust-managed-state c12
   316	   pattern suggests Phase B could re-check via DTO field. My judgment is
   317	   it's not worth the field — the MA's validity is set at construction
   318	   time and rarely changes.

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCache.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCacheFactory.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"remove.*Query|delete.*Query|addCachedQuery|getQueryByName|clear|invalidate|QueryCache\" src/main/java/org/ecocean/cache src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 222ms:
     1	package org.ecocean.cache;
     2	
     3	import java.io.IOException;
     4	import java.util.HashMap;
     5	import java.util.List;
     6	import java.util.Map;
     7	
     8	import org.ecocean.shepherd.core.Shepherd;
     9	import org.json.JSONObject;
    10	
    11	// A singleton responsible for storing, retrieving, and eventually executing queries that are desired for caching. It will have all necessary getters
    12	// and setters for dealing with the cache.
    13	public class QueryCache {
    14	    private Map<String, CachedQuery> cachedQueries;
    15	    private String context = null;
    16	
    17	    public QueryCache() {}
    18	    public QueryCache(String context) {
    19	        this.context = context;
    20	    }
    21	
    22	    public CachedQuery getQueryByName(String name) {
    23	        if (cachedQueries == null) loadQueries();
    24	        return cachedQueries.get(name);
    25	    }
    26	
    27	    public Map<String, CachedQuery> cachedQueries() { return cachedQueries; }
    28	
    29	    public void loadQueries() {
    30	        if (context == null)
    31	            throw new RuntimeException("QueryCache.loadQueries() called with context null");
    32	        cachedQueries = new HashMap<String, CachedQuery>();
    33	        Shepherd myShepherd = new Shepherd(context);
    34	        myShepherd.beginDBTransaction();
    35	
    36	        List<StoredQuery> queries = myShepherd.getAllStoredQueries();
    37	        int numQueries = queries.size();
    38	        for (int i = 0; i < numQueries; i++) {
    39	            StoredQuery sq = queries.get(i);
    40	            cachedQueries.put(sq.getName(), sq.getCachedQueryCopy());
    41	        }
    42	        myShepherd.rollbackDBTransaction();
    43	        myShepherd.closeDBTransaction();
    44	    }
    45	
    46	    public void invalidateByName(String name)
    47	    throws IOException {
    48	        if (cachedQueries.containsKey(name)) {
    49	            cachedQueries.get(name).invalidate();
    50	        }
    51	    }
    52	
    53	    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
    54	        Shepherd myShepherd) {
    55	        // create CachedQuery object
    56	        CachedQuery cq = new CachedQuery(name, jsonobj, persistAsStoredQuery, myShepherd);
    57	
    58	        // put on HashMap
    59	        cachedQueries.put(name, cq);
    60	    }
    61	
    62	    public void addCachedQuery(CachedQuery cq) {
    63	        cachedQueries.put(cq.getName(), cq);
    64	    }
    65	}

 succeeded in 224ms:
     1	package org.ecocean.cache;
     2	
     3	public class QueryCacheFactory {
     4	    private static QueryCache qc;
     5	
     6	    public synchronized static QueryCache getQueryCache(String context) {
     7	        try {
     8	            if (qc == null) {
     9	                qc = new QueryCache(context);
    10	                qc.loadQueries();
    11	            }
    12	            return qc;
    13	        } catch (Exception jdo) {
    14	            jdo.printStackTrace();
    15	            System.out.println("I couldn't instantiate a QueryCache.");
    16	            return null;
    17	        }
    18	    }
    19	
    20	    /**
    21	     * Invalidate a named cached query without throwing. Both
    22	     * {@link #getQueryCache(String)} (which can return null on
    23	     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
    24	     * (which declares {@code throws IOException}) are wrapped so callers
    25	     * doing best-effort cache busting don't have to repeat the same
    26	     * defensive plumbing.
    27	     */
    28	    public static void safeInvalidate(String context, String cacheName) {
    29	        try {
    30	            QueryCache cache = getQueryCache(context);
    31	            if (cache != null) cache.invalidateByName(cacheName);
    32	        } catch (Exception ex) {
    33	            System.out.println(
    34	                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
    35	        }
    36	    }
    37	}

 succeeded in 742ms:
src/main/java/org/ecocean/cache/StoredQuery.java:32:    // FUTURE: invalidateAfter - number of milliseconds after the lastExecuted time after which the stored results are invalidated and the query must
src/main/java/org/ecocean/cache/StoredQuery.java:103:       FUTURE: setCachedResult FUTURE: getCachedResult FUTURE: invalidateCachedResult - immediately invalidates cachedResult FUTURE:
src/main/java/org/ecocean/cache/StoredQuery.java:104:          setInvalidateAfter Value of -1 never invalidates cachedResult if not null FUTURE: executeQuery
src/main/java/org/ecocean/cache/StoredQuery.java:32:    // FUTURE: invalidateAfter - number of milliseconds after the lastExecuted time after which the stored results are invalidated and the query must
src/main/java/org/ecocean/cache/StoredQuery.java:103:       FUTURE: setCachedResult FUTURE: getCachedResult FUTURE: invalidateCachedResult - immediately invalidates cachedResult FUTURE:
src/main/java/org/ecocean/cache/StoredQuery.java:104:          setInvalidateAfter Value of -1 never invalidates cachedResult if not null FUTURE: executeQuery
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:4:    private static QueryCache qc;
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:9:                qc = new QueryCache(context);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:15:            System.out.println("I couldn't instantiate a QueryCache.");
src/main/java/org/ecocean/cache/QueryCacheFactory.java:22:     * {@link #getQueryCache(String)} (which can return null on
src/main/java/org/ecocean/cache/QueryCacheFactory.java:23:     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
src/main/java/org/ecocean/cache/QueryCacheFactory.java:30:            QueryCache cache = getQueryCache(context);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:31:            if (cache != null) cache.invalidateByName(cacheName);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:34:                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:4:    private static QueryCache qc;
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:9:                qc = new QueryCache(context);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:15:            System.out.println("I couldn't instantiate a QueryCache.");
src/main/java/org/ecocean/cache/QueryCacheFactory.java:22:     * {@link #getQueryCache(String)} (which can return null on
src/main/java/org/ecocean/cache/QueryCacheFactory.java:23:     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
src/main/java/org/ecocean/cache/QueryCacheFactory.java:30:            QueryCache cache = getQueryCache(context);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:31:            if (cache != null) cache.invalidateByName(cacheName);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:34:                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:17:    public QueryCache() {}
src/main/java/org/ecocean/cache/QueryCache.java:18:    public QueryCache(String context) {
src/main/java/org/ecocean/cache/QueryCache.java:22:    public CachedQuery getQueryByName(String name) {
src/main/java/org/ecocean/cache/QueryCache.java:31:            throw new RuntimeException("QueryCache.loadQueries() called with context null");
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCache.java:49:            cachedQueries.get(name).invalidate();
src/main/java/org/ecocean/cache/QueryCache.java:53:    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
src/main/java/org/ecocean/cache/QueryCache.java:62:    public void addCachedQuery(CachedQuery cq) {
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:17:    public QueryCache() {}
src/main/java/org/ecocean/cache/QueryCache.java:18:    public QueryCache(String context) {
src/main/java/org/ecocean/cache/QueryCache.java:22:    public CachedQuery getQueryByName(String name) {
src/main/java/org/ecocean/cache/QueryCache.java:31:            throw new RuntimeException("QueryCache.loadQueries() called with context null");
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCache.java:49:            cachedQueries.get(name).invalidate();
src/main/java/org/ecocean/cache/QueryCache.java:53:    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
src/main/java/org/ecocean/cache/QueryCache.java:62:    public void addCachedQuery(CachedQuery cq) {
src/main/java/org/ecocean/cache/CachedQuery.java:206:    public synchronized void invalidate()
src/main/java/org/ecocean/Encounter.java:2695:    // this will clear out ALL tags with this location, but i think we
src/main/java/org/ecocean/cache/CachedQuery.java:206:    public synchronized void invalidate()
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:8:import org.ecocean.cache.QueryCache;
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:9:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:44:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:45:            CachedQuery existingCache = qc.getQueryByName(cacheName);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:68:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:69:            CachedQuery existingCache = qc.getQueryByName(cacheName);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:92:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/genetics/HFStatistics.java:52:    private void d_clear() {
src/main/java/org/ecocean/genetics/HFStatistics.java:70:    private void s_clear() {
src/main/java/org/ecocean/genetics/HFStatistics.java:95:    private void w_clear() {
src/main/java/org/ecocean/genetics/HFStatistics.java:134:        // clears statistics if already computed
src/main/java/org/ecocean/genetics/HFStatistics.java:135:        if (s_flag) s_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:168:            s_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:194:                    s_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:199:                    s_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:242:                     s_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:252:                          s_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:312:            w_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:327:            w_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:346:            w_clear();
src/main/java/org/ecocean/genetics/HFStatistics.java:360:            w_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:76:    void s_clear() {
src/main/java/org/ecocean/genetics/FStatistics.java:127:    void w_clear() {
src/main/java/org/ecocean/genetics/FStatistics.java:168:        // clears statistics if already computed
src/main/java/org/ecocean/genetics/FStatistics.java:169:        if (s_flag) s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:203:        // s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:211:        // s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:238:                    s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:243:                    s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:289:                        s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:298:                            s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:310:                        s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:320:                            s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:337:                              s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:348:                                s_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:414:            w_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:429:            w_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:448:            w_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:471:            w_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:486:            w_clear();
src/main/java/org/ecocean/genetics/FStatistics.java:502:            w_clear();
src/main/java/org/ecocean/metrics/Prometheus.java:220:            // Gauge already registered - this shouldn't happen now that we clear the registry first,
src/main/java/org/ecocean/shepherd/core/Shepherd.java:46:import org.ecocean.cache.QueryCache;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:47:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4390:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4394:                if (qc.getQueryByName(("numRecentEncounters_" + thisUser)) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4395:                    CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + thisUser));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4404:                    qc.addCachedQuery(cq);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4426:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4431:            if (qc.getQueryByName(("numRecentEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4432:                CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + user.getUUID()));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4443:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4467:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4472:            if (qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4473:                CachedQuery cq = qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID()));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4484:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:16:import org.ecocean.cache.QueryCache;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:17:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:474:     * <p>Honors the 15-minute QueryCache the same way the lenient
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:482:        QueryCache qc = null;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        if (qc != null && qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:490:            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:493:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:515:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:703:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:704:            if (qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:706:                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:708:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:718:                    qc.addCachedQuery(cq);
src/main/java/org/ecocean/ContextConfiguration.java:28:        props.clear();
src/main/java/org/ecocean/identity/IBEISIA.java:1575:    3. do we first clear out existing annotations?
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:11: * record clear status/statusDetails on the parent Task and react
src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java:190:            // if we get through to here, value should be cleared to do actual patch
src/main/java/org/ecocean/identity/IAQueryCache.java:7:import org.ecocean.cache.QueryCache;
src/main/java/org/ecocean/identity/IAQueryCache.java:8:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/identity/IAQueryCache.java:14:public class IAQueryCache {
src/main/java/org/ecocean/identity/IAQueryCache.java:58:        QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/identity/IAQueryCache.java:59:        CachedQuery q = qc.getQueryByName(qname);
src/main/java/org/ecocean/identity/IAQueryCache.java:62:            qc.addCachedQuery(q);
src/main/java/org/ecocean/identity/IAQueryCache.java:82:        QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/identity/IAQueryCache.java:83:        CachedQuery q = qc.getQueryByName(qname);
src/main/java/org/ecocean/identity/IAQueryCache.java:177:        System.out.println("IAQueryCache " + msg);
src/main/java/org/ecocean/MetricsBot.java:144:        // NOTE: We no longer clear the registry at the start.
src/main/java/org/ecocean/MetricsBot.java:146:        // swap by clearing and reloading only after successful CSV write.
src/main/java/org/ecocean/MetricsBot.java:406:            // Now that CSV is safely written, clear and reload the Prometheus registry
src/main/java/org/ecocean/MetricsBot.java:407:            // This is the only place where we clear the registry, ensuring /metrics
src/main/java/org/ecocean/MetricsBot.java:410:                CollectorRegistry.defaultRegistry.clear();
src/main/java/org/ecocean/MetricsBot.java:416:            // On any exception, we do NOT clear the registry, so old metrics remain available
src/main/java/org/ecocean/servlet/WorkspaceDelete.java:82:            out.println("{status: deleted, originalWorkspaceQuery: " + originalWorkspaceQuery +
src/main/java/org/ecocean/api/Logout.java:82:                httpSession.invalidate();
src/main/java/org/ecocean/api/Logout.java:83:                ThreadContext.put("action", "logout_http_session_invalidated");
src/main/java/org/ecocean/api/Logout.java:84:                logger.debug("Logout attempt with http session invalidated");
src/main/java/org/ecocean/api/Logout.java:116:            ThreadContext.clearAll();
src/main/java/org/ecocean/api/bulk/BulkImporter.java:172:        // clears shepherd/pmf cache, which we seem to do when we create encounters (?)
src/main/java/org/ecocean/genetics/distance/Distances.java:38:  //  same location, we need to have a way of clearing the cache.
src/main/java/org/ecocean/genetics/distance/Distances.java:40:  public static final void clearCache ()
src/main/java/org/ecocean/genetics/distance/Distances.java:49:      clearCache ();
src/main/java/org/ecocean/api/Login.java:146:                token.clear();
src/main/java/org/ecocean/api/Login.java:217:            ThreadContext.clearAll();
src/main/java/org/ecocean/Project.java:252:    public void clearAllEncounters() {
src/main/java/org/ecocean/OpenSearch.java:359:        PIT_CACHE.clear();
src/main/java/org/ecocean/servlet/LogoutUser.java:55:            session.invalidate();
src/main/java/org/ecocean/servlet/LoginUser.java:94:            // clear the information stored in the token
src/main/java/org/ecocean/servlet/LoginUser.java:95:            token.clear();
src/main/java/org/ecocean/grid/EncounterLite.java:1323:        // clearly, newEncounterSpots and newOrigEncounterSpots must be of
src/main/java/org/ecocean/grid/GridManager.java:661:    public void clearDoneItems() { done = new ArrayList<ScanWorkItemResult>(); }
src/main/java/org/ecocean/servlet/IAGateway.java:412:        JSONObject shortCut = IAQueryCache.tryTargetAnnotationsCache(context, ann, taskRes,
src/main/java/org/ecocean/servlet/ServletUtilities.java:752:    public static String hashAndSaltPassword(String clearTextPassword, String salt) {
src/main/java/org/ecocean/servlet/ServletUtilities.java:753:        return new Sha512Hash(clearTextPassword, salt, 200000).toHex();
src/main/java/org/ecocean/servlet/ProjectDelete.java:80:                project.clearAllEncounters();
src/main/java/org/ecocean/servlet/export/ExportExcelIndividualReport.java:84:                encCal.clear();

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/CachedQuery.java | sed -n '190,215p'" in /mnt/c/Wildbook-clean2
 succeeded in 255ms:
   190	       }
   191	     */
   192	    public Integer executeCountQuery(Shepherd myShepherd) {
   193	        if ((collectionQueryCount == null) ||
   194	            ((expirationTimeoutDuration > -1) &&
   195	            (System.currentTimeMillis() > nextExpirationTimeout))) {
   196	            try {
   197	                // System.out.println("Executing executeCountQuery");
   198	                List<Object> c = executeQuery(myShepherd);
   199	                collectionQueryCount = new Integer(c.size());
   200	                nextExpirationTimeout = System.currentTimeMillis() + expirationTimeoutDuration;
   201	            } catch (Exception e) { e.printStackTrace(); }
   202	        }
   203	        return collectionQueryCount;
   204	    }
   205	
   206	    public synchronized void invalidate()
   207	    throws IOException {
   208	        collectionQueryCount = null;
   209	        jsonSerializedQueryResult = null;
   210	
   211	        // delete the serialized JSON
   212	        getCacheFile().delete();
   213	    }
   214	
   215	    public synchronized JSONObject getJSONSerializedQueryResult() {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/WbiaQueueUtil.java | sed -n '1,125p'" in /mnt/c/Wildbook-clean2
 succeeded in 251ms:
     1	package org.ecocean.ia;
     2	
     3	import java.net.URL;
     4	import java.util.Iterator;
     5	import java.util.concurrent.atomic.AtomicBoolean;
     6	import org.datanucleus.api.rest.orgjson.JSONObject;
     7	import org.ecocean.cache.CachedQuery;
     8	import org.ecocean.cache.QueryCache;
     9	import org.ecocean.cache.QueryCacheFactory;
    10	import org.ecocean.identity.IBEISIA;
    11	import org.ecocean.RestClient;
    12	import org.ecocean.Util;
    13	
    14	public class WbiaQueueUtil {
    15	    // Measurement static values
    16	    private static volatile JSONObject wbiaQueue = new JSONObject();
    17	    private static String cacheName = "wbiaQueue";
    18	
    19	    // Flag to prevent cache stampede - only one thread can refresh at a time
    20	    private static final AtomicBoolean isReloading = new AtomicBoolean(false);
    21	
    22	    // Use volatile for thread-safe reads without synchronization
    23	    private static volatile int numJobs = 0;
    24	    private static volatile int numCompletedJobs = 0;
    25	    private static volatile int numWorkingJobs = 0;
    26	    private static volatile int numQueuedJobs = 0;
    27	    private static volatile int numErrorJobs = 0;
    28	    private static volatile int numDetectionJobs = 0;
    29	    private static volatile int numIDJobs = 0;
    30	    private static volatile int sizeIDJobQueue = 0;
    31	    private static volatile int sizeDetectionJobQueue = 0;
    32	
    33	    /**
    34	     * Checks if cache needs refresh and reloads if necessary.
    35	     * Uses AtomicBoolean to prevent cache stampede - only one thread will
    36	     * actually perform the refresh, others will return immediately with
    37	     * the last known (stale but valid) values.
    38	     */
    39	    private static void reloadIfNeeded(boolean forceRefresh) {
    40	        String context = "context0";
    41	
    42	        // Quick check if cache is still valid (without acquiring lock)
    43	        try {
    44	            QueryCache qc = QueryCacheFactory.getQueryCache(context);
    45	            CachedQuery existingCache = qc.getQueryByName(cacheName);
    46	
    47	            // If cache is valid and not forcing refresh, use cached value
    48	            if (!forceRefresh && existingCache != null &&
    49	                System.currentTimeMillis() < existingCache.getNextExpirationTimeout()) {
    50	                wbiaQueue = Util.toggleJSONObject(existingCache.getJSONSerializedQueryResult());
    51	                return;
    52	            }
    53	        } catch (Exception e) {
    54	            // If we can't check cache, proceed to try refresh
    55	        }
    56	
    57	        // Cache expired or doesn't exist - try to acquire the reload lock
    58	        // compareAndSet returns true only if the value was false and we set it to true
    59	        if (!isReloading.compareAndSet(false, true)) {
    60	            // Another thread is already reloading - return immediately with stale data
    61	            // The stale data is still valid (just expired), so it's safe to return
    62	            return;
    63	        }
    64	
    65	        // We acquired the lock - we're responsible for refreshing
    66	        try {
    67	            // Double-check cache validity (another thread may have just refreshed)
    68	            QueryCache qc = QueryCacheFactory.getQueryCache(context);
    69	            CachedQuery existingCache = qc.getQueryByName(cacheName);
    70	            if (!forceRefresh && existingCache != null &&
    71	                System.currentTimeMillis() < existingCache.getNextExpirationTimeout()) {
    72	                wbiaQueue = Util.toggleJSONObject(existingCache.getJSONSerializedQueryResult());
    73	                return;
    74	            }
    75	
    76	            // Save current values in case of error
    77	            int e_numJobs = numJobs;
    78	            int e_numCompletedJobs = numCompletedJobs;
    79	            int e_numWorkingJobs = numWorkingJobs;
    80	            int e_numQueuedJobs = numQueuedJobs;
    81	            int e_numErrorJobs = numErrorJobs;
    82	            int e_numDetectionJobs = numDetectionJobs;
    83	            int e_numIDJobs = numIDJobs;
    84	            int e_sizeIDJobQueue = sizeIDJobQueue;
    85	            int e_sizeDetectionJobQueue = sizeDetectionJobQueue;
    86	
    87	            try {
    88	                URL wbiaQueueUrl = IBEISIA.iaURL(context, "api/engine/job/status/");
    89	                wbiaQueue = Util.toggleJSONObject(RestClient.get(wbiaQueueUrl, 90000));
    90	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(wbiaQueue));
    91	                cq.nextExpirationTimeout = System.currentTimeMillis() + 120000;
    92	                qc.addCachedQuery(cq);
    93	
    94	                // Temporary variables for atomic update
    95	                int t_numJobs = 0;
    96	                int t_numCompletedJobs = 0;
    97	                int t_numWorkingJobs = 0;
    98	                int t_numQueuedJobs = 0;
    99	                int t_numErrorJobs = 0;
   100	                int t_numDetectionJobs = 0;
   101	                int t_numIDJobs = 0;
   102	                int t_sizeIDJobQueue = 0;
   103	                int t_sizeDetectionJobQueue = 0;
   104	
   105	                JSONObject inspectMe = wbiaQueue.getJSONObject("response").getJSONObject(
   106	                    "json_result");
   107	                Iterator<String> keys = inspectMe.keys();
   108	                while (keys.hasNext()) {
   109	                    String jobID = keys.next();
   110	                    t_numJobs++;
   111	                    JSONObject job = inspectMe.getJSONObject(jobID);
   112	                    boolean working = false;
   113	                    boolean queued = false;
   114	                    if (job.getString("status").equals("completed")) t_numCompletedJobs++;
   115	                    if (job.getString("status").equals("working")) {
   116	                        t_numWorkingJobs++;
   117	                        working = true;
   118	                    }
   119	                    if (job.getString("status").equals("queued")) {
   120	                        t_numQueuedJobs++;
   121	                        queued = true;
   122	                    }
   123	                    if (job.getString("status").equals("error")) t_numErrorJobs++;
   124	                    if (job.getString("function").startsWith("start_detect")) {
   125	                        t_numDetectionJobs++;

exec
/bin/bash -lc "rg -n \"iaImageIdsStrict|parseImageIdsArrayStrict|sendMediaAssetByDto|buildSingleImageRequestMap|safeInvalidate\\(\" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 799ms:
src/main/java/org/ecocean/cache/QueryCacheFactory.java:28:    public static void safeInvalidate(String context, String cacheName) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:34:                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);

codex
**Findings**

- `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1` - The file was converted from LF to CRLF. `git ls-files --eol` shows `i/lf w/crlf`, and `git diff --check` reports trailing whitespace on every line. That is the main blocker: it pollutes the commit and may fail whitespace checks. Re-normalize this file to LF and keep only the semantic method diff.

- `src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:1` - Local workspace note: the new test file is currently untracked. If this is meant to be the C2 commit, make sure it is included.

**C2 Review**

The helper change itself looks right. `ma == null` and `ma.webURL() == null` are covered, and `URL.toString()` returning null is effectively impossible but harmless to guard. Returning `String` instead of `Object` is a strict improvement and the existing raw `ArrayList` call sites remain source-compatible.

Removing the `LocalAssetStore` branch is behavior-preserving for `WildbookIAM`: both old branches returned the same `urlStr`. The historical `IBEISIA` helper has a similar no-op branch, with an old commented-out local-path alternative, but nothing in the current `WildbookIAM` implementation depends on that distinction.

The JavaDoc references the correct C2 purpose. I would keep it shorter in production code, but it is not wrong.

**Design Open Questions**

- QueryCache invalidation: yes, this branch has `QueryCache.invalidateByName` and `QueryCacheFactory.safeInvalidate`. Use `safeInvalidate(context, "iaImageIds")` after successful image registration. The strict cache readers should remain robust because invalidation leaves a cache object but clears serialized JSON.

- `isStatusSuccess`: keep it in `WildbookIAM` for locality with the new DTO/WBIA request helpers. Move later only if multiple classes start needing it.

- Strict parser duplication: small duplication is acceptable, but my preference is a private `parseFancyUuidArrayStrict(JSONArray, label)` with annotation/image wrappers for grep-friendly names.

- Phase A validity: putting `validMediaAsset` in Phase A is correct. Phase B should only validate DTO fields it already has, such as non-null `imageUri` and IDs, not re-touch `MediaAsset` state.
tokens used
63,793
**Findings**

- `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1` - The file was converted from LF to CRLF. `git ls-files --eol` shows `i/lf w/crlf`, and `git diff --check` reports trailing whitespace on every line. That is the main blocker: it pollutes the commit and may fail whitespace checks. Re-normalize this file to LF and keep only the semantic method diff.

- `src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:1` - Local workspace note: the new test file is currently untracked. If this is meant to be the C2 commit, make sure it is included.

**C2 Review**

The helper change itself looks right. `ma == null` and `ma.webURL() == null` are covered, and `URL.toString()` returning null is effectively impossible but harmless to guard. Returning `String` instead of `Object` is a strict improvement and the existing raw `ArrayList` call sites remain source-compatible.

Removing the `LocalAssetStore` branch is behavior-preserving for `WildbookIAM`: both old branches returned the same `urlStr`. The historical `IBEISIA` helper has a similar no-op branch, with an old commented-out local-path alternative, but nothing in the current `WildbookIAM` implementation depends on that distinction.

The JavaDoc references the correct C2 purpose. I would keep it shorter in production code, but it is not wrong.

**Design Open Questions**

- QueryCache invalidation: yes, this branch has `QueryCache.invalidateByName` and `QueryCacheFactory.safeInvalidate`. Use `safeInvalidate(context, "iaImageIds")` after successful image registration. The strict cache readers should remain robust because invalidation leaves a cache object but clears serialized JSON.

- `isStatusSuccess`: keep it in `WildbookIAM` for locality with the new DTO/WBIA request helpers. Move later only if multiple classes start needing it.

- Strict parser duplication: small duplication is acceptable, but my preference is a private `parseFancyUuidArrayStrict(JSONArray, label)` with annotation/image wrappers for grep-friendly names.

- Phase A validity: putting `validMediaAsset` in Phase A is correct. Phase B should only validate DTO fields it already has, such as non-null `imageUri` and IDs, not re-touch `MediaAsset` state.
