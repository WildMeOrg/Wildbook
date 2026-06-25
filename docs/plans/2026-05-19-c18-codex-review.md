OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e41d4-fc53-7e50-a6b3-92a832e1155a
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

# Codex code-review: C18 — lenient iaAnnotationIds null-safe on invalidated cache

Live integration on the user's deployment after deploying C12+C13+
C14+C15+C16+C17: legacy HotSpotter matching broke with
`iaAnnotIds is empty; possible IA problems` at
`IBEISIA.sendAnnotationsAsNeeded:3705`. Stack trace traced back
to NPE at `WildbookIAM.iaAnnotationIds:1000`.

You called this out in your C3 review: "CachedQuery.invalidate()
leaves the named cache entry present with
jsonSerializedQueryResult = null. The next iaAnnotationIdsStrict
call will treat it as a valid unexpired cache entry, log
'cache parse failed; refetching', then recover by refetching."
The strict variant HAS that inner try-catch + refetch fallback.

C6 (committed earlier today) added
`QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds")`
after a successful Phase 1 annotation POST. This is correct
behavior — it prevents the polling thread from re-POSTing an
annotation we just registered. But the LENIENT
`iaAnnotationIds(context)` at WildbookIAM.java:988 (used by the
legacy HotSpotter path) only has an outer try-catch. After the
invalidate it sees an unexpired entry, tries to parse the null
serialized JSON, NPEs, the outer catch swallows it, returns
empty. HotSpotter treats empty as fatal.

I dismissed this as "log noise" when you flagged it. It was a
real regression I introduced.

## Fix

Restructure the lenient variant to match the strict variant's
defensive-cache pattern: cache attempt in its own try-catch, on
null/parse-failure fall through to the network refetch path.

## Diff

diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index 38b6c5832..1a591a625 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -990,17 +990,43 @@ public class WildbookIAM extends IAPlugin {
         JSONArray jids = null;
         String cacheName = "iaAnnotationIds";
 
+        // Cache-hit attempt first. Wrapped in its own try-catch so a
+        // parse failure (e.g., the cache entry exists but its serialized
+        // JSON is null after invalidateByName) falls through to a fresh
+        // network refetch instead of bubbling out as "return empty"
+        // (C18: regression fix for the safeInvalidate call C6 added —
+        // CachedQuery.invalidate() leaves the entry present with null
+        // JSON, and the prior outer-only catch returned empty, breaking
+        // the legacy HotSpotter sendAnnotationsAsNeeded path).
+        QueryCache qc = null;
         try {
-            QueryCache qc = QueryCacheFactory.getQueryCache(context);
-            if (qc.getQueryByName(cacheName) != null &&
-                System.currentTimeMillis() <
-                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
+            qc = QueryCacheFactory.getQueryCache(context);
+        } catch (Exception ex) {
+            // Defensive: cache factory init can fail; degrade to no-cache.
+        }
+        if (qc != null && qc.getQueryByName(cacheName) != null &&
+            System.currentTimeMillis() <
+            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
+            try {
                 org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                     qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
-                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
-            } else {
+                if (jobj != null) {
+                    jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
+                }
+            } catch (Exception ex) {
+                IA.log("WARNING: WildbookIAM.iaAnnotationIds() cache parse failed; refetching: "
+                    + ex.getMessage());
+                // jids remains null → falls through to the network refetch below.
+            }
+        }
+        // Cache miss or parse failure → refetch from WBIA. Wrapped in
+        // its own try-catch so a network error still returns the empty
+        // list per the original lenient contract (callers may treat
+        // empty as "no annotations registered yet").
+        if (jids == null) {
+            try {
                 jids = apiGetJSONArray("/api/annot/json/", context);
-                if (jids != null) {
+                if ((jids != null) && (qc != null)) {
                     org.datanucleus.api.rest.orgjson.JSONObject jobj =
                         new org.datanucleus.api.rest.orgjson.JSONObject();
                     jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
@@ -1008,11 +1034,11 @@ public class WildbookIAM extends IAPlugin {
                     cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                     qc.addCachedQuery(cq);
                 }
+            } catch (Exception ex) {
+                ex.printStackTrace();
+                IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
+                    ex.toString());
             }
-        } catch (Exception ex) {
-            ex.printStackTrace();
-            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
-                ex.toString());
         }
         if (jids != null) {
             try {

## Validation

- All existing WildbookIAM tests pass (46/46 across 5 test
  classes: register, image-ids-strict, fancy-uuid-array-strict,
  image-phase0, mediaAssetToUri).
- LF-normalized.

## Review scope

- Is the new structure semantically equivalent to the strict
  variant's pattern? Cache attempt isolated in inner try-catch,
  network refetch in outer try-catch, both null/empty paths
  return the empty list per the lenient contract.
- I retained the "cache parse failed; refetching" WARN log (was
  silently swallowed before).
- The lenient `iaImageIds` doesn't use the cache at all (line
  1036-1059), so no parallel fix needed.
- Anything else.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll verify the surrounding implementation and the strict variant before judging the patch, keeping this review read-only.
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "iaAnnotationIds|iaAnnotationIdsStrict|iaImageIds" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
 succeeded in 345ms:
104:        List<String> iaImageIds = new ArrayList<String>();
105:        if (checkFirst) iaImageIds = iaImageIds();
120:            if (iaImageIds.contains(ma.getAcmId())) continue;
191:        List<String> iaImageIds = new ArrayList<String>();
192:        if (checkFirst) iaImageIds = iaImageIds();
206:            if (iaImageIds.contains(ma.getAcmId())) continue;
278:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
359:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
497:     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
505:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
506:        String cacheName = "iaAnnotationIds";
522:                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
525:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
540:                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
559:        return parseFancyUuidArrayStrict(jids, "iaAnnotationIds");
573:     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
579:     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
580:     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
581:     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
584:    public static List<String> iaImageIdsStrict(String context) throws IOException {
585:        String cacheName = "iaImageIds";
601:                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
604:                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
619:                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
637:        return parseFancyUuidArrayStrict(jids, "iaImageIds");
643:     * source-array name (e.g. {@code "iaAnnotationIds"},
644:     * {@code "iaImageIds"}); it appears in IOException messages so
722:     *       invalidate the {@code "iaImageIds"} cache on success.
757:            known = iaAnnotationIdsStrict(context);
759:            IA.log("WARNING: WildbookIAM.registerOneByDto() Phase 1 iaAnnotationIds fetch failed for ann=" +
763:        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
793:        QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds");
815:     *       {@code "iaImageIds"} cache so the next caller sees the
849:            knownImages = iaImageIdsStrict(context);
851:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() Phase 0 iaImageIds fetch failed for ann=" +
879:        QueryCacheFactory.safeInvalidate(context, "iaImageIds");
983:    public List<String> iaAnnotationIds() {
984:        return iaAnnotationIds(this.context);
988:    public static List<String> iaAnnotationIds(String context) {
991:        String cacheName = "iaAnnotationIds";
1014:                    jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
1017:                IA.log("WARNING: WildbookIAM.iaAnnotationIds() cache parse failed; refetching: "
1032:                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
1039:                IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
1051:                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
1058:    public List<String> iaImageIds() {
1059:        return iaImageIds(this.context);
1062:    public static List<String> iaImageIds(String context) {
1070:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
1081:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());

 succeeded in 482ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/plugin/WildbookIAM.java

 succeeded in 561ms:
diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index 38b6c5832..1a591a625 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -990,17 +990,43 @@ public class WildbookIAM extends IAPlugin {
         JSONArray jids = null;
         String cacheName = "iaAnnotationIds";
 
+        // Cache-hit attempt first. Wrapped in its own try-catch so a
+        // parse failure (e.g., the cache entry exists but its serialized
+        // JSON is null after invalidateByName) falls through to a fresh
+        // network refetch instead of bubbling out as "return empty"
+        // (C18: regression fix for the safeInvalidate call C6 added —
+        // CachedQuery.invalidate() leaves the entry present with null
+        // JSON, and the prior outer-only catch returned empty, breaking
+        // the legacy HotSpotter sendAnnotationsAsNeeded path).
+        QueryCache qc = null;
         try {
-            QueryCache qc = QueryCacheFactory.getQueryCache(context);
-            if (qc.getQueryByName(cacheName) != null &&
-                System.currentTimeMillis() <
-                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
+            qc = QueryCacheFactory.getQueryCache(context);
+        } catch (Exception ex) {
+            // Defensive: cache factory init can fail; degrade to no-cache.
+        }
+        if (qc != null && qc.getQueryByName(cacheName) != null &&
+            System.currentTimeMillis() <
+            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
+            try {
                 org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                     qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
-                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
-            } else {
+                if (jobj != null) {
+                    jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
+                }
+            } catch (Exception ex) {
+                IA.log("WARNING: WildbookIAM.iaAnnotationIds() cache parse failed; refetching: "
+                    + ex.getMessage());
+                // jids remains null → falls through to the network refetch below.
+            }
+        }
+        // Cache miss or parse failure → refetch from WBIA. Wrapped in
+        // its own try-catch so a network error still returns the empty
+        // list per the original lenient contract (callers may treat
+        // empty as "no annotations registered yet").
+        if (jids == null) {
+            try {
                 jids = apiGetJSONArray("/api/annot/json/", context);
-                if (jids != null) {
+                if ((jids != null) && (qc != null)) {
                     org.datanucleus.api.rest.orgjson.JSONObject jobj =
                         new org.datanucleus.api.rest.orgjson.JSONObject();
                     jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
@@ -1008,11 +1034,11 @@ public class WildbookIAM extends IAPlugin {
                     cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                     qc.addCachedQuery(cq);
                 }
+            } catch (Exception ex) {
+                ex.printStackTrace();
+                IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
+                    ex.toString());
             }
-        } catch (Exception ex) {
-            ex.printStackTrace();
-            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
-                ex.toString());
         }
         if (jids != null) {
             try {

codex
The local diff matches the supplied patch, and the file stayed `i/lf w/lf`. I’m reading the strict and lenient methods now to check whether the fallback behavior is actually aligned.
exec
/bin/bash -lc "sed -n '480,565p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '980,1088p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '584,640p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 226ms:
    }

    // instance version of below (since context is known)
    public List<String> iaAnnotationIds() {
        return iaAnnotationIds(this.context);
    }

    // this fails "gracefully" with empty list if network fubar.  bad decision?
    public static List<String> iaAnnotationIds(String context) {
        List<String> ids = new ArrayList<String>();
        JSONArray jids = null;
        String cacheName = "iaAnnotationIds";

        // Cache-hit attempt first. Wrapped in its own try-catch so a
        // parse failure (e.g., the cache entry exists but its serialized
        // JSON is null after invalidateByName) falls through to a fresh
        // network refetch instead of bubbling out as "return empty"
        // (C18: regression fix for the safeInvalidate call C6 added —
        // CachedQuery.invalidate() leaves the entry present with null
        // JSON, and the prior outer-only catch returned empty, breaking
        // the legacy HotSpotter sendAnnotationsAsNeeded path).
        QueryCache qc = null;
        try {
            qc = QueryCacheFactory.getQueryCache(context);
        } catch (Exception ex) {
            // Defensive: cache factory init can fail; degrade to no-cache.
        }
        if (qc != null && qc.getQueryByName(cacheName) != null &&
            System.currentTimeMillis() <
            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                if (jobj != null) {
                    jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
                }
            } catch (Exception ex) {
                IA.log("WARNING: WildbookIAM.iaAnnotationIds() cache parse failed; refetching: "
                    + ex.getMessage());
                // jids remains null → falls through to the network refetch below.
            }
        }
        // Cache miss or parse failure → refetch from WBIA. Wrapped in
        // its own try-catch so a network error still returns the empty
        // list per the original lenient contract (callers may treat
        // empty as "no annotations registered yet").
        if (jids == null) {
            try {
                jids = apiGetJSONArray("/api/annot/json/", context);
                if ((jids != null) && (qc != null)) {
                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
                        new org.datanucleus.api.rest.orgjson.JSONObject();
                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                    qc.addCachedQuery(cq);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
                    ex.toString());
            }
        }
        if (jids != null) {
            try {
                for (int i = 0; i < jids.length(); i++) {
                    if (jids.optJSONObject(i) != null)
                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
            }
        }
        return ids;
    }

    // as above, but images
    public List<String> iaImageIds() {
        return iaImageIds(this.context);
    }

    public static List<String> iaImageIds(String context) {
        List<String> ids = new ArrayList<String>();
        JSONArray jids = null;

        try {
            jids = apiGetJSONArray("/api/image/json/", context);
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
                ex.toString());
        }
        if (jids != null) {
            try {
                for (int i = 0; i < jids.length(); i++) {
                    if (jids.optJSONObject(i) != null)
                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
            }
        }
        return ids;
    }

    public JSONArray apiGetJSONArray(String urlSuffix)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,

 succeeded in 230ms:

        /**
         * Pre-C5 constructor preserved for backward-compatibility with
         * test fixtures that don't exercise the Phase 0 image-registration
         * path. Equivalent to the 11-arg constructor with all four
         * image fields null. New production callers should use the
         * 11-arg form.
         */
        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
            String individualName) {
            this(annotationId, annotationAcmId, mediaAssetAcmId, bbox, theta,
                iaClass, individualName, null, null, null, null);
        }
    }

    /**
     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
     * fetch failure rather than returning an empty list. Phase B needs
     * this so a network failure during the already-present check is
     * not silently treated as "go ahead and POST".
     *
     * <p>Honors the 15-minute QueryCache the same way the lenient
     * variant does, so a cache hit avoids the network entirely.</p>
     */
    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
        String cacheName = "iaAnnotationIds";
        // QueryCacheFactory.getQueryCache(context) can return null on a
        // context that has never been initialized; treat that as "no cache"
        // rather than NPE-ing out and aborting the poll cycle.
        QueryCache qc = null;
        try {
            qc = QueryCacheFactory.getQueryCache(context);
        } catch (Exception ex) {
            // Defensive: cache factory init can fail; degrade to no-cache.
        }
        if (qc != null && qc.getQueryByName(cacheName) != null &&
            System.currentTimeMillis() <
            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
                return parseAnnotationIdsArrayStrict(cached);
            } catch (Exception ex) {
                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
                    + ex.getMessage());
            }
        }
        JSONArray jids;
        try {
            jids = apiGetJSONArray("/api/annot/json/", context);
        } catch (Exception ex) {
            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
        }
        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
        if (qc != null) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj =
                    new org.datanucleus.api.rest.orgjson.JSONObject();
                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                qc.addCachedQuery(cq);
            } catch (Exception cacheEx) {
                // Cache store failure is non-fatal; we still have the ids.
            }
        }
        return parseAnnotationIdsArrayStrict(jids);
    }

    /**
     * Strict element parser: throws IOException if any element is not a
     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
     * skips/null-pads malformed entries, which is fine for legacy paths but
     * would let a corrupt response masquerade as "annotation not yet
     * registered" in the polling thread's already-present check.
     */
    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
        return parseFancyUuidArrayStrict(jids, "iaAnnotationIds");
    }

    static List<String> parseAnnotationIdsArray(JSONArray jids) {
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {

 succeeded in 272ms:
    public static List<String> iaImageIdsStrict(String context) throws IOException {
        String cacheName = "iaImageIds";
        // QueryCacheFactory.getQueryCache(context) can return null on a
        // context that has never been initialized; treat that as "no cache"
        // rather than NPE-ing out and aborting the poll cycle.
        QueryCache qc = null;
        try {
            qc = QueryCacheFactory.getQueryCache(context);
        } catch (Exception ex) {
            // Defensive: cache factory init can fail; degrade to no-cache.
        }
        if (qc != null && qc.getQueryByName(cacheName) != null &&
            System.currentTimeMillis() <
            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
                return parseImageIdsArrayStrict(cached);
            } catch (Exception ex) {
                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
                    + ex.getMessage());
            }
        }
        JSONArray jids;
        try {
            jids = apiGetJSONArray("/api/image/json/", context);
        } catch (Exception ex) {
            throw new IOException("WBIA /api/image/json/ fetch failed: " + ex.getMessage(), ex);
        }
        if (jids == null) throw new IOException("WBIA /api/image/json/ returned null");
        if (qc != null) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj =
                    new org.datanucleus.api.rest.orgjson.JSONObject();
                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                qc.addCachedQuery(cq);
            } catch (Exception cacheEx) {
                // Cache store failure is non-fatal; we still have the ids.
            }
        }
        return parseImageIdsArrayStrict(jids);
    }

    /**
     * Strict element parser: throws IOException if any element is not a
     * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
     * both delegate to {@link #parseFancyUuidArrayStrict(JSONArray, String)}
     * with the appropriate label for error-message clarity.
     */
    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
        return parseFancyUuidArrayStrict(jids, "iaImageIds");
    }

    /**

exec
/bin/bash -lc 'LC_ALL=C rg -n "[''^'"\\x00-\\x7F]\" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 369ms:
744:            // enum needed — Phase C log line distinguishes phase).
997:        // (C18: regression fix for the safeInvalidate call C6 added —
1019:                // jids remains null → falls through to the network refetch below.
1022:        // Cache miss or parse failure → refetch from WBIA. Wrapped in

codex
The control flow does match the strict variant at a high level: cache lookup is isolated, parse failure falls through, and fetch failure preserves the lenient empty-list contract. I’m checking tests and cache invalidation helpers for any hidden contract mismatch.
exec
/bin/bash -lc "rg -n \"safeInvalidate|invalidateByName|class QueryCache|jsonSerializedQueryResult|iaAnnotationIds\\(\" src/main/java src/test || true" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "WildbookIAM.*iaAnnotationIds|iaAnnotationIdsStrict|iaImageIdsStrict|parseAnnotationIdsArray" src/test || true' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short' in /mnt/c/Wildbook-clean2
 succeeded in 436ms:
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:21: * iaAnnotationIdsStrict path.
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:113:    // --- parseAnnotationIdsArray -----------------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:115:    @Test void parseAnnotationIdsArrayReturnsEmptyOnNull() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:116:        assertTrue(WildbookIAM.parseAnnotationIdsArray(null).isEmpty());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:119:    @Test void parseAnnotationIdsArrayExtractsUuids() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:123:        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:129:    @Test void parseAnnotationIdsArraySkipsNonObjectEntries() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:133:        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:138:    // --- parseAnnotationIdsArrayStrict -----------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:140:    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:141:        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:144:    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:148:        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:154:    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:159:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:166:    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:171:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:177:    // iaAnnotationIdsStrict path, which relies on the polling thread's
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:15: * Network-bound behavior of {@link WildbookIAM#iaImageIdsStrict} itself
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:18: * ({@link WildbookIAM#parseAnnotationIdsArrayStrict} and

 succeeded in 1014ms:
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:13:class QueryCacheFactorySafeInvalidateTest {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:17:    @Test void safeInvalidate_invokesInvalidateByName_whenCacheNotNull()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:20:        doNothing().when(cache).invalidateByName(NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:23:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:24:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:26:        verify(cache).invalidateByName(NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:29:    @Test void safeInvalidate_swallowsNull_whenGetQueryCacheReturnsNull() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:32:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:34:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:38:    @Test void safeInvalidate_swallowsIoException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:42:            .when(cache).invalidateByName(NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:45:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:47:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:49:        verify(cache).invalidateByName(NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:52:    @Test void safeInvalidate_swallowsRuntimeException_fromGetQueryCache() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:56:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:58:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:62:    @Test void safeInvalidate_swallowsRuntimeException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:65:        // returns a cache instance, but invalidateByName throws a runtime
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:69:            .when(cache).invalidateByName(NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:72:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:73:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:75:        verify(cache).invalidateByName(NAME);
src/main/java/org/ecocean/identity/IBEISIA.java:3704:        // List<String> iaAnnotIds = plugin.iaAnnotationIds();
src/main/java/org/ecocean/identity/IBEISIA.java:3705:        HashSet<String> iaAnnotIds = new HashSet(plugin.iaAnnotationIds());
src/main/java/org/ecocean/identity/IBEISIA.java:3766:        List<String> iaIds = org.ecocean.ia.plugin.WildbookIAM.iaAnnotationIds(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:278:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:359:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:793:        QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:879:        QueryCacheFactory.safeInvalidate(context, "iaImageIds");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:983:    public List<String> iaAnnotationIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:984:        return iaAnnotationIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:988:    public static List<String> iaAnnotationIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:995:        // JSON is null after invalidateByName) falls through to a fresh
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:997:        // (C18: regression fix for the safeInvalidate call C6 added —
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1017:                IA.log("WARNING: WildbookIAM.iaAnnotationIds() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1039:                IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1051:                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:23:     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
src/main/java/org/ecocean/cache/QueryCacheFactory.java:28:    public static void safeInvalidate(String context, String cacheName) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:31:            if (cache != null) cache.invalidateByName(cacheName);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:34:                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/CachedQuery.java:48:    public CachedQuery(String name, JSONObject jsonSerializedQueryResult,
src/main/java/org/ecocean/cache/CachedQuery.java:51:        this.jsonSerializedQueryResult = jsonSerializedQueryResult;
src/main/java/org/ecocean/cache/CachedQuery.java:55:                Util.writeToFile(jsonSerializedQueryResult.toString(),
src/main/java/org/ecocean/cache/CachedQuery.java:68:    public CachedQuery(String name, JSONObject jsonSerializedQueryResult) {
src/main/java/org/ecocean/cache/CachedQuery.java:70:        this.jsonSerializedQueryResult = jsonSerializedQueryResult;
src/main/java/org/ecocean/cache/CachedQuery.java:91:    public JSONObject jsonSerializedQueryResult;
src/main/java/org/ecocean/cache/CachedQuery.java:126:            if ((jsonSerializedQueryResult == null) ||
src/main/java/org/ecocean/cache/CachedQuery.java:132:                if ((jsonSerializedQueryResult == null) && getCacheFile().exists()) {
src/main/java/org/ecocean/cache/CachedQuery.java:156:                return jsonSerializedQueryResult;
src/main/java/org/ecocean/cache/CachedQuery.java:209:        jsonSerializedQueryResult = null;
src/main/java/org/ecocean/cache/CachedQuery.java:216:        return jsonSerializedQueryResult;
src/main/java/org/ecocean/cache/CachedQuery.java:219:    public synchronized void setJSONSerializedQueryResult(JSONObject jsonSerializedQueryResult,
src/main/java/org/ecocean/cache/CachedQuery.java:221:        if (jsonSerializedQueryResult == null) {
src/main/java/org/ecocean/cache/CachedQuery.java:222:            this.jsonSerializedQueryResult = null;
src/main/java/org/ecocean/cache/CachedQuery.java:224:            this.jsonSerializedQueryResult = jsonSerializedQueryResult;
src/main/java/org/ecocean/cache/CachedQuery.java:231:                Util.writeToFile(jsonSerializedQueryResult.toString(),
src/main/java/org/ecocean/cache/CachedQuery.java:284:            jsonSerializedQueryResult = jsonobj;
src/main/java/org/ecocean/cache/CachedQuery.java:311:                this.jsonSerializedQueryResult = jsonobj;
src/main/java/org/ecocean/cache/CachedQuery.java:322:        if (jsonSerializedQueryResult != null) return jsonSerializedQueryResult;

 succeeded in 13154ms:
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

exec
/bin/bash -lc "sed -n '1,90p' src/main/java/org/ecocean/cache/QueryCache.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,90p' src/main/java/org/ecocean/cache/QueryCacheFactory.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '120,230p' src/main/java/org/ecocean/cache/CachedQuery.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '300,330p' src/main/java/org/ecocean/cache/CachedQuery.java" in /mnt/c/Wildbook-clean2
 succeeded in 264ms:
package org.ecocean.cache;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

// A singleton responsible for storing, retrieving, and eventually executing queries that are desired for caching. It will have all necessary getters
// and setters for dealing with the cache.
public class QueryCache {
    private Map<String, CachedQuery> cachedQueries;
    private String context = null;

    public QueryCache() {}
    public QueryCache(String context) {
        this.context = context;
    }

    public CachedQuery getQueryByName(String name) {
        if (cachedQueries == null) loadQueries();
        return cachedQueries.get(name);
    }

    public Map<String, CachedQuery> cachedQueries() { return cachedQueries; }

    public void loadQueries() {
        if (context == null)
            throw new RuntimeException("QueryCache.loadQueries() called with context null");
        cachedQueries = new HashMap<String, CachedQuery>();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();

        List<StoredQuery> queries = myShepherd.getAllStoredQueries();
        int numQueries = queries.size();
        for (int i = 0; i < numQueries; i++) {
            StoredQuery sq = queries.get(i);
            cachedQueries.put(sq.getName(), sq.getCachedQueryCopy());
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }

    public void invalidateByName(String name)
    throws IOException {
        if (cachedQueries.containsKey(name)) {
            cachedQueries.get(name).invalidate();
        }
    }

    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
        Shepherd myShepherd) {
        // create CachedQuery object
        CachedQuery cq = new CachedQuery(name, jsonobj, persistAsStoredQuery, myShepherd);

        // put on HashMap
        cachedQueries.put(name, cq);
    }

    public void addCachedQuery(CachedQuery cq) {
        cachedQueries.put(cq.getName(), cq);
    }
}

 succeeded in 265ms:
package org.ecocean.cache;

public class QueryCacheFactory {
    private static QueryCache qc;

    public synchronized static QueryCache getQueryCache(String context) {
        try {
            if (qc == null) {
                qc = new QueryCache(context);
                qc.loadQueries();
            }
            return qc;
        } catch (Exception jdo) {
            jdo.printStackTrace();
            System.out.println("I couldn't instantiate a QueryCache.");
            return null;
        }
    }

    /**
     * Invalidate a named cached query without throwing. Both
     * {@link #getQueryCache(String)} (which can return null on
     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
     * (which declares {@code throws IOException}) are wrapped so callers
     * doing best-effort cache busting don't have to repeat the same
     * defensive plumbing.
     */
    public static void safeInvalidate(String context, String cacheName) {
        try {
            QueryCache cache = getQueryCache(context);
            if (cache != null) cache.invalidateByName(cacheName);
        } catch (Exception ex) {
            System.out.println(
                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
        }
    }
}

 succeeded in 283ms:

    public JSONObject executeCollectionQuery(Shepherd myShepherd, boolean useSerializedJSONCache)
    throws IOException {
        // first, can we use serialized cache and if so, does it exist
        if (useSerializedJSONCache) {
            long time = System.currentTimeMillis();
            if ((jsonSerializedQueryResult == null) ||
                ((expirationTimeoutDuration > -1) && (time > nextExpirationTimeout))) {
                // System.out.println("*****Status 1");
                // check if we have a serialized cache
                // first if the cache is null but not expired, then just load it.
                // ((expirationTimeoutDuration==-1)||(((expirationTimeoutDuration>-1)&&(time<nextExpirationTimeout))))
                if ((jsonSerializedQueryResult == null) && getCacheFile().exists()) {
                    // load the cache file and return the JSONObject
                    // System.out.println("*****Status 1a");
                    nextExpirationTimeout = time + expirationTimeoutDuration;
                    return loadCachedJSON();
                }
                // gotta regen the cache
                else {
                    // System.out.println("cached file does NOT exist or has expired!");
                    // run the query and set the cache
                    List results = executeQuery(myShepherd);

                    // serialize the results
                    JSONObject jsonobj = serializeCollectionToJSON(results, myShepherd);
                    // System.out.println("finished serializing the result: "+jsonobj);

                    nextExpirationTimeout = time + expirationTimeoutDuration;
                    // then return the List<Object>
                    System.out.println("*****Status 1b");
                    return jsonobj;
                }
            } else {
                // data still valid, just send it back quickly!
                System.out.println("*****Status 2");
                return jsonSerializedQueryResult;
            }
        }
        // just run the query since the user has chosen to override the cache
        else {
            List<Object> c = executeQuery(myShepherd);
            JSONObject jsonobj = convertToJson(c,
                ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
            System.out.println("*****Status 3");
            return jsonobj;
        }
    }

    /*
       public List executeCollectionQueryAsObjects(Shepherd myShepherd,boolean useSerializedJSONCache,String className){
       List results=new ArrayList<Object>();
       try{
        JSONObject jsonobj=executeCollectionQuery(myShepherd,useSerializedJSONCache);
        JSONArray arr=jsonobj.getJSONArray("result");
        System.out.println("arr:"+arr+"\n\n");
        int numResults=arr.length();
        for(int i=0;i<numResults;i++){
          JSONObject js=arr.getJSONObject(i);
          Object obj=RESTUtils.getObjectFromJSONObject(Util.toggleJSONObject(jsonobj), className,
             ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
          System.out.println("obj: "+obj);
          results.add(obj);
        }
        return results;
       }
       catch(Exception e){
        e.printStackTrace();
       }
       return null;
       }
     */
    public Integer executeCountQuery(Shepherd myShepherd) {
        if ((collectionQueryCount == null) ||
            ((expirationTimeoutDuration > -1) &&
            (System.currentTimeMillis() > nextExpirationTimeout))) {
            try {
                // System.out.println("Executing executeCountQuery");
                List<Object> c = executeQuery(myShepherd);
                collectionQueryCount = new Integer(c.size());
                nextExpirationTimeout = System.currentTimeMillis() + expirationTimeoutDuration;
            } catch (Exception e) { e.printStackTrace(); }
        }
        return collectionQueryCount;
    }

    public synchronized void invalidate()
    throws IOException {
        collectionQueryCount = null;
        jsonSerializedQueryResult = null;

        // delete the serialized JSON
        getCacheFile().delete();
    }

    public synchronized JSONObject getJSONSerializedQueryResult() {
        return jsonSerializedQueryResult;
    }

    public synchronized void setJSONSerializedQueryResult(JSONObject jsonSerializedQueryResult,
        boolean serialize) {
        if (jsonSerializedQueryResult == null) {
            this.jsonSerializedQueryResult = null;
        } else {
            this.jsonSerializedQueryResult = jsonSerializedQueryResult;
        }
        try {
            // delete old cache
            getCacheFile().delete();
            // if set in the mwthod declaration, serialize the new object cache
            if (serialize)

 succeeded in 286ms:

        // System.out.println("loading cached JSON: ");
        try {
            File sFile = getCacheFile();
            // System.out.println("loading cached JSON: "+sFile.getAbsolutePath());
            if (sFile.exists()) {
                InputStream is = new FileInputStream(sFile);
                String jsonTxt = IOUtils.toString(is, "UTF-8");
                // System.out.println(jsonTxt);
                JSONObject jsonobj = new JSONObject(jsonTxt);
                // RESTUtils.getObjectFromJSONObject(jsonobj, String className, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
                this.jsonSerializedQueryResult = jsonobj;
                return jsonobj;
            } else { return null; }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // this only loads from disk if "it is necessary" (TBD?)
    public synchronized JSONObject loadCachedJSONIfNeeded() {
        if (jsonSerializedQueryResult != null) return jsonSerializedQueryResult;
        return loadCachedJSON();
    }

    public File getCacheFile()
    throws IOException {
        Properties cprops = null;

        try {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '988,1045p'" in /mnt/c/Wildbook-clean2
 succeeded in 305ms:
   988	    public static List<String> iaAnnotationIds(String context) {
   989	        List<String> ids = new ArrayList<String>();
   990	        JSONArray jids = null;
   991	        String cacheName = "iaAnnotationIds";
   992	
   993	        // Cache-hit attempt first. Wrapped in its own try-catch so a
   994	        // parse failure (e.g., the cache entry exists but its serialized
   995	        // JSON is null after invalidateByName) falls through to a fresh
   996	        // network refetch instead of bubbling out as "return empty"
   997	        // (C18: regression fix for the safeInvalidate call C6 added —
   998	        // CachedQuery.invalidate() leaves the entry present with null
   999	        // JSON, and the prior outer-only catch returned empty, breaking
  1000	        // the legacy HotSpotter sendAnnotationsAsNeeded path).
  1001	        QueryCache qc = null;
  1002	        try {
  1003	            qc = QueryCacheFactory.getQueryCache(context);
  1004	        } catch (Exception ex) {
  1005	            // Defensive: cache factory init can fail; degrade to no-cache.
  1006	        }
  1007	        if (qc != null && qc.getQueryByName(cacheName) != null &&
  1008	            System.currentTimeMillis() <
  1009	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
  1010	            try {
  1011	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
  1012	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
  1013	                if (jobj != null) {
  1014	                    jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
  1015	                }
  1016	            } catch (Exception ex) {
  1017	                IA.log("WARNING: WildbookIAM.iaAnnotationIds() cache parse failed; refetching: "
  1018	                    + ex.getMessage());
  1019	                // jids remains null → falls through to the network refetch below.
  1020	            }
  1021	        }
  1022	        // Cache miss or parse failure → refetch from WBIA. Wrapped in
  1023	        // its own try-catch so a network error still returns the empty
  1024	        // list per the original lenient contract (callers may treat
  1025	        // empty as "no annotations registered yet").
  1026	        if (jids == null) {
  1027	            try {
  1028	                jids = apiGetJSONArray("/api/annot/json/", context);
  1029	                if ((jids != null) && (qc != null)) {
  1030	                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
  1031	                        new org.datanucleus.api.rest.orgjson.JSONObject();
  1032	                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
  1033	                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
  1034	                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
  1035	                    qc.addCachedQuery(cq);
  1036	                }
  1037	            } catch (Exception ex) {
  1038	                ex.printStackTrace();
  1039	                IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
  1040	                    ex.toString());
  1041	            }
  1042	        }
  1043	        if (jids != null) {
  1044	            try {
  1045	                for (int i = 0; i < jids.length(); i++) {

exec
/bin/bash -lc 'rg -n "toggleJSONObject|toggleJSONArray" src/main/java/org/ecocean/Util.java' in /mnt/c/Wildbook-clean2
 succeeded in 310ms:
444:    public static org.datanucleus.api.rest.orgjson.JSONObject toggleJSONObject(JSONObject jin) {
449:    public static JSONObject toggleJSONObject(org.datanucleus.api.rest.orgjson.JSONObject jin) {
454:    public static org.datanucleus.api.rest.orgjson.JSONArray toggleJSONArray(
460:    public static org.json.JSONArray toggleJSONArray(

exec
/bin/bash -lc "sed -n '436,466p' src/main/java/org/ecocean/Util.java" in /mnt/c/Wildbook-clean2
 succeeded in 301ms:
    public static boolean isIdentityMatrix(float[] m) {
        if (m == null) return false;
        if (m.length != 6) return false;
        if ((m[0] == 1) && (m[1] == 0) && (m[2] == 0) && (m[3] == 1) && (m[4] == 0) && (m[5] == 0))
            return false;
        return true;
    }

    public static org.datanucleus.api.rest.orgjson.JSONObject toggleJSONObject(JSONObject jin) {
        if (jin == null) return null;
        return stringToDatanucleusJSONObject(jin.toString());
    }

    public static JSONObject toggleJSONObject(org.datanucleus.api.rest.orgjson.JSONObject jin) {
        if (jin == null) return null;
        return stringToJSONObject(jin.toString());
    }

    public static org.datanucleus.api.rest.orgjson.JSONArray toggleJSONArray(
        org.json.JSONArray jin) {
        if (jin == null) return null;
        return stringToDatanucleusJSONArray(jin.toString());
    }

    public static org.json.JSONArray toggleJSONArray(
        org.datanucleus.api.rest.orgjson.JSONArray jin) {
        if (jin == null) return null;
        return stringToJSONArray(jin.toString());
    }

    public static Object jsonNull(Object obj) {

codex
**Findings**

- Low: [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1007) still leaves `qc.getQueryByName(cacheName)` and `getNextExpirationTimeout()` outside the cache-parse `try`. If `QueryCache.getQueryByName()` has to call `loadQueries()` and that fails, the lenient method can still throw instead of falling through to the WBIA refetch path. To fully match the defensive-cache intent, the lookup/expiration check should be inside the cache-attempt `try`, preferably with a local `CachedQuery`.

- Low: [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1011) does not actually log the WARN for the exact invalidated-cache case. `Util.toggleJSONObject(null)` returns `null`, then the `if (jobj != null)` path silently falls through. The refetch behavior is correct, but the “cache parse failed; refetching” log is only retained for malformed non-null cache JSON.

**Review Notes**

The main regression is fixed: an unexpired `iaAnnotationIds` cache entry with null serialized JSON now refetches instead of returning empty. That preserves the lenient contract for network failures while avoiding the HotSpotter false-empty path.

I’d add one focused regression test for lenient `iaAnnotationIds(context)`: unexpired cache entry, `jsonSerializedQueryResult == null`, mocked `/api/annot/json/` returns IDs, method returns those IDs. I did not rerun the test suite; this was review-only as requested.
tokens used
36,288
**Findings**

- Low: [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1007) still leaves `qc.getQueryByName(cacheName)` and `getNextExpirationTimeout()` outside the cache-parse `try`. If `QueryCache.getQueryByName()` has to call `loadQueries()` and that fails, the lenient method can still throw instead of falling through to the WBIA refetch path. To fully match the defensive-cache intent, the lookup/expiration check should be inside the cache-attempt `try`, preferably with a local `CachedQuery`.

- Low: [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1011) does not actually log the WARN for the exact invalidated-cache case. `Util.toggleJSONObject(null)` returns `null`, then the `if (jobj != null)` path silently falls through. The refetch behavior is correct, but the “cache parse failed; refetching” log is only retained for malformed non-null cache JSON.

**Review Notes**

The main regression is fixed: an unexpired `iaAnnotationIds` cache entry with null serialized JSON now refetches instead of returning empty. That preserves the lenient contract for network failures while avoiding the HotSpotter false-empty path.

I’d add one focused regression test for lenient `iaAnnotationIds(context)`: unexpired cache entry, `jsonSerializedQueryResult == null`, mocked `/api/annot/json/` returns IDs, method returns those IDs. I did not rerun the test suite; this was review-only as requested.
