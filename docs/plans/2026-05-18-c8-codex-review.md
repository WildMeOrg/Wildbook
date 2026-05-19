OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3e68-175a-7810-9d2a-f82089f84a88
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

# Codex code-review: Track 2 C8 — OpenSearch.waitForAnnotationMatchableIds

Empty-match-prospects design Track 2 C8: new visibility helper
that polls a stricter predicate than waitForVisibility. The
existing helper only confirms `_id` is present; this one also
confirms matchAgainst=true, acmId exists, and a nested embedding
of the configured method/methodVersion is indexed. Scope is
intentionally narrower than getMatchingSetQuery — it answers
"doc has fresh embedding metadata", which is the visibility race
the Track 2 batch gate cares about.

Conditional method/methodVersion handling per Codex round-5 Major:
either being null/blank omits its predicate (matches
Annotation.getMatchQuery at Annotation.java:1205-1209). Both blank
falls back to match_all on the nested embeddings path (legacy
api_endpoint-only configs).

`_count` body shape uses `ids` query (matches existing
queryCount precedent at OpenSearch.java:541-552). No `size`, no
`track_total_hits` (Codex round-3 Medium).

## Diff

diff --git a/src/main/java/org/ecocean/OpenSearch.java b/src/main/java/org/ecocean/OpenSearch.java
index 55208421d..1f6d35966 100644
--- a/src/main/java/org/ecocean/OpenSearch.java
+++ b/src/main/java/org/ecocean/OpenSearch.java
@@ -548,6 +548,114 @@ public class OpenSearch {
         return query;
     }
 
+    // ml-service migration v2 / empty-match-prospects design Track 2 C8.
+    //
+    // Stronger visibility predicate than waitForVisibility for the annotation
+    // index. A doc that exists by _id but is missing nested
+    // embeddings.method/methodVersion would pass _id-only and then knn-fail
+    // at match time. This helper polls a predicate that mirrors the matching
+    // constraints in Annotation.getMatchQuery: id ∈ ids AND matchAgainst=true
+    // AND acmId exists AND a nested embedding for this method/version is
+    // indexed. Scope is intentionally narrower than getMatchingSetQuery
+    // (no taxonomy/viewpoint/encounter/dead-animal filters) — this helper
+    // answers "doc has fresh embedding metadata", which is the visibility
+    // race the Track 2 batch gate cares about.
+    //
+    // method/methodVersion follow the strict-when-present convention of
+    // Annotation.getMatchQuery at Annotation.java:1205-1209: if either is
+    // null/blank, the corresponding nested predicate is omitted.
+    //
+    // Like waitForVisibility: _refresh on entry, then exponential-backoff
+    // poll of _count until count >= |normalized ids| OR timeout. Empty
+    // wait set short-circuits to true.
+    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
+        String method, String methodVersion, long timeoutMs)
+    throws IOException {
+        if (ids == null || ids.isEmpty()) return true;
+
+        Set<String> targetIds = new LinkedHashSet<String>();
+        for (String id : ids) {
+            if (id != null) targetIds.add(id);
+        }
+        if (targetIds.isEmpty()) return true;
+
+        if (skipAutoIndexing()) {
+            System.out.println(
+                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
+                "/tmp/skipAutoIndexing set — every poll will return zero hits " +
+                "regardless of wait time.");
+        }
+
+        indexRefresh("annotation");
+
+        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
+            methodVersion);
+        long deadline = System.currentTimeMillis() + timeoutMs;
+        long sleepMs = 100;
+        while (true) {
+            int seen = queryCount("annotation", query);
+            if (seen >= targetIds.size()) return true;
+            long remaining = deadline - System.currentTimeMillis();
+            if (remaining <= 0) return false;
+            try {
+                Thread.sleep(Math.min(sleepMs, remaining));
+            } catch (InterruptedException ex) {
+                Thread.currentThread().interrupt();
+                return false;
+            }
+            sleepMs = Math.min(sleepMs * 2, 1000);
+        }
+    }
+
+    // Package-visible for testing. Returns the _count-shaped query body
+    // matching the annotation-matchable predicate documented on
+    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as
+    // buildIdEligibilityQuery for consistency with queryCount's
+    // expectations (no `size`, no `track_total_hits`).
+    static JSONObject buildAnnotationMatchableQuery(Set<String> ids,
+        String method, String methodVersion) {
+        JSONArray idArr = new JSONArray();
+        for (String id : ids) idArr.put(id);
+
+        JSONArray filterArr = new JSONArray();
+        filterArr.put(new JSONObject().put("ids",
+            new JSONObject().put("values", idArr)));
+        filterArr.put(new JSONObject().put("term",
+            new JSONObject().put("matchAgainst", true)));
+        filterArr.put(new JSONObject().put("exists",
+            new JSONObject().put("field", "acmId")));
+
+        // Nested embedding clause. Method + methodVersion are added
+        // conditionally to match Annotation.getMatchQuery semantics: a
+        // blank method or methodVersion means "any" for that field.
+        JSONArray nestedMust = new JSONArray();
+        if (method != null && !method.isEmpty()) {
+            nestedMust.put(new JSONObject().put("term",
+                new JSONObject().put("embeddings.method", method)));
+        }
+        if (methodVersion != null && !methodVersion.isEmpty()) {
+            nestedMust.put(new JSONObject().put("term",
+                new JSONObject().put("embeddings.methodVersion", methodVersion)));
+        }
+        JSONObject nestedQuery;
+        if (nestedMust.length() == 0) {
+            // No method/version filter — wait only on the existence of a
+            // nested embedding entry.
+            nestedQuery = new JSONObject().put("match_all", new JSONObject());
+        } else {
+            nestedQuery = new JSONObject().put("bool",
+                new JSONObject().put("must", nestedMust));
+        }
+        filterArr.put(new JSONObject().put("nested",
+            new JSONObject().put("path", "embeddings").put("query", nestedQuery)));
+
+        JSONObject query = new JSONObject();
+        query.put("query",
+            new JSONObject().put("bool",
+                new JSONObject().put("filter", filterArr)));
+        return query;
+    }
+
     // when you only care about how many this would return
     public int queryCount(String indexName, final JSONObject query)
     throws IOException {


## New test file:

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Shape-of-query tests for {@link OpenSearch#buildAnnotationMatchableQuery}.
 * Full poll-and-wait behavior of {@link OpenSearch#waitForAnnotationMatchableIds}
 * is exercised by integration in dev deployments — the matchable-predicate
 * JSON shape is the mechanical part that benefits from unit-testing.
 *
 * <p>(Empty-match-prospects design Track 2 C8.)</p>
 */
class OpenSearchAnnotationMatchableQueryTest {

    private static Set<String> ids(String... s) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        for (String x : s) set.add(x);
        return set;
    }

    private static JSONArray filterArr(JSONObject q) {
        return q.getJSONObject("query").getJSONObject("bool").getJSONArray("filter");
    }

    private static JSONObject findFilterClause(JSONArray filters, String kind) {
        for (int i = 0; i < filters.length(); i++) {
            JSONObject jo = filters.optJSONObject(i);
            if (jo != null && jo.has(kind)) return jo;
        }
        return null;
    }

    @Test void includesIdsTermMatchAgainstAcmIdExistsAndNested() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1", "ann-2"), "miewid-msv4.1", "4.1");
        JSONArray filters = filterArr(q);
        // Order doesn't matter for OpenSearch, but each named clause
        // must be present in the filter array.
        assertNotNull(findFilterClause(filters, "ids"),
            "missing ids clause: " + q);
        assertNotNull(findFilterClause(filters, "term"),
            "missing term clause (matchAgainst): " + q);
        assertNotNull(findFilterClause(filters, "exists"),
            "missing exists clause (acmId): " + q);
        assertNotNull(findFilterClause(filters, "nested"),
            "missing nested clause (embeddings): " + q);
    }

    @Test void idsClauseListsExactValues() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1", "ann-2", "ann-3"), "miewid-msv4.1", "4.1");
        JSONArray values = findFilterClause(filterArr(q), "ids")
            .getJSONObject("ids").getJSONArray("values");
        assertEquals(3, values.length());
        assertEquals("ann-1", values.getString(0));
        assertEquals("ann-3", values.getString(2));
    }

    @Test void termClauseAssertsMatchAgainstTrue() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        JSONObject term = findFilterClause(filterArr(q), "term")
            .getJSONObject("term");
        assertTrue(term.has("matchAgainst"),
            "term clause should target matchAgainst: " + term);
        assertEquals(true, term.getBoolean("matchAgainst"));
    }

    @Test void existsClauseTargetsAcmId() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        JSONObject exists = findFilterClause(filterArr(q), "exists")
            .getJSONObject("exists");
        assertEquals("acmId", exists.getString("field"));
    }

    @Test void nestedClauseTargetsEmbeddingsPath_withMethodAndVersion() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        assertEquals("embeddings", nested.getString("path"));
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        assertEquals(2, must.length());
        // Method term
        JSONObject methodTerm = null;
        JSONObject versionTerm = null;
        for (int i = 0; i < must.length(); i++) {
            JSONObject term = must.getJSONObject(i).getJSONObject("term");
            if (term.has("embeddings.method")) methodTerm = term;
            if (term.has("embeddings.methodVersion")) versionTerm = term;
        }
        assertNotNull(methodTerm);
        assertNotNull(versionTerm);
        assertEquals("miewid-msv4.1", methodTerm.getString("embeddings.method"));
        assertEquals("4.1", versionTerm.getString("embeddings.methodVersion"));
    }

    @Test void nestedClauseOmitsVersion_whenMethodVersionNull() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", null);
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        // Only method term should be present.
        assertEquals(1, must.length());
        JSONObject term = must.getJSONObject(0).getJSONObject("term");
        assertTrue(term.has("embeddings.method"));
        assertFalse(term.has("embeddings.methodVersion"));
    }

    @Test void nestedClauseOmitsMethod_whenMethodNull() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), null, "4.1");
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        JSONArray must = nested.getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must");
        assertEquals(1, must.length());
        JSONObject term = must.getJSONObject(0).getJSONObject("term");
        assertTrue(term.has("embeddings.methodVersion"));
        assertFalse(term.has("embeddings.method"));
    }

    @Test void nestedClauseFallsBackToMatchAll_whenBothNullOrBlank() {
        // Legacy api_endpoint-only config: neither method nor version is
        // available, but the gate still wants to confirm SOME nested
        // embedding exists.
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), null, "");
        JSONObject nested = findFilterClause(filterArr(q), "nested")
            .getJSONObject("nested");
        JSONObject inner = nested.getJSONObject("query");
        assertTrue(inner.has("match_all"),
            "expected match_all when method+version both blank: " + nested);
    }

    @Test void noTopLevelSizeOrTrackTotalHits() {
        // queryCount() strips only `_source`; size and track_total_hits in
        // the body would be invalid for _count. Codex round-3 Medium.
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids("ann-1"), "miewid-msv4.1", "4.1");
        assertFalse(q.has("size"),
            "size must not be present in _count body: " + q);
        assertFalse(q.has("track_total_hits"),
            "track_total_hits must not be present in _count body: " + q);
    }

    @Test void emptyIdSetProducesEmptyIdsValues() {
        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
            ids(), "miewid-msv4.1", "4.1");
        JSONArray values = findFilterClause(filterArr(q), "ids")
            .getJSONObject("ids").getJSONArray("values");
        assertEquals(0, values.length());
    }
}
```


## Validation

- OpenSearchAnnotationMatchableQueryTest: 10/10 (new) covering the
  pure-function query-builder.
- LF normalized.
- Full project still compiles.
- The poll-and-wait loop mirrors waitForVisibility exactly (refresh
  on entry, _count poll with exponential backoff to 1s cap,
  honors timeoutMs); same Thread.interrupt handling.

## Review scope

- Predicate composition: id ∈ ids AND matchAgainst=true AND acmId
  exists AND nested embeddings.method/methodVersion. Mirrors
  Annotation.getMatchQuery's strict-when-present semantics.
- match_all fallback when both method+version blank — is that the
  right semantics ("any nested embedding exists") for legacy
  api_endpoint-only configs?
- API surface: public method on OpenSearch.java alongside
  waitForVisibility (per Codex round-3 OQ #1 — annotation-schema-
  specific naming makes a public method acceptable).
- Anything else (especially: nested-query semantics inside _count;
  empty input handling).

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll treat this as review-only and won’t edit anything. I’m going to verify the diff against the surrounding `OpenSearch` and `Annotation` code so the findings are grounded in the repo’s actual query shapes and indexing conventions.
exec
/bin/bash -lc 'rg -n "waitForVisibility|buildIdEligibilityQuery|queryCount|buildAnnotationMatchableQuery|waitForAnnotationMatchableIds" src/main/java/org/ecocean/OpenSearch.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short && git branch --show-current' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getMatchQuery|embeddings\\.method|methodVersion|matchAgainst|acmId\" src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
 succeeded in 224ms:
467:    // Callers (typically waitForVisibility) follow with a visibility poll.
498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
521:        JSONObject query = buildIdEligibilityQuery(targetIds);
525:            int seen = queryCount(indexName, query);
541:    static JSONObject buildIdEligibilityQuery(Set<String> ids) {
553:    // Stronger visibility predicate than waitForVisibility for the annotation
568:    // Like waitForVisibility: _refresh on entry, then exponential-backoff
571:    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
584:                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
591:        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
596:            int seen = queryCount("annotation", query);
612:    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as
613:    // buildIdEligibilityQuery for consistency with queryCount's
615:    static JSONObject buildAnnotationMatchableQuery(Set<String> ids,
660:    public int queryCount(String indexName, final JSONObject query)
671:            System.out.println("queryCount() on index " + indexName + " using query=" + query +
674:            throw new IOException("queryCount() failed");

 succeeded in 256ms:
53:    protected String acmId;
72:    //           acmId IS NOT NULL ("already registered via the historical
88:    private boolean matchAgainst = false;
178:        map.put("acmId", keywordType);
196:        embProps.put("methodVersion", keywordType);
213:        jgen.writeStringField("acmId", this.getAcmId());
216:        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
250:                jgen.writeStringField("methodVersion", emb.getMethodVersion());
268:    // TODO should this also be limited by matchAgainst and acmId?
313:        this.acmId = id;
318:        return this.acmId;
322:        return (this.acmId != null);
573:        if (acmId == null)
574:            throw new IOException(this + " does not have acmId set; cannot get viewpoint from IA");
576:            JSONObject resp = IBEISIA.iaViewpointFromAnnotUUID(acmId, context);
751:        return matchAgainst;
755:        matchAgainst = b;
956:        // returned every annotation with matchAgainst=T and an acmId
1001:        // matchAgainst true
1003:        arg.put("matchAgainst", true);
1008:        // must have acmId
1010:        arg.put("field", "acmId");
1178:    // method and methodVersion are used to determine *which* embedding to use; if null it will use 1st embedding
1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
1183:        String method, String methodVersion) {
1184:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
1187:        return getMatchQuery(method, methodVersion,
1192:    public JSONObject getMatchQuery(String method, String methodVersion,
1194:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
1207:        if (methodVersion != null)
1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
1223:        boolean useClauses, String method, String methodVersion) {
1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
1890:            "\" && annotations.contains(annot1) && annot1.acmId == \"" + annot.getAcmId() +
1905:        if (this.acmId == null) return;
1906:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
1912:        Util.mark("Annotation.refreshLiteTaxonomy() refreshing " + this.acmId);
1913:        AnnotationLite.setCache(this.acmId, annl);
1917:        if (this.acmId == null) return;
1918:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
1924:        Util.mark("Annotation.refreshLiteIndividual() refreshing " + this.acmId);
1925:        AnnotationLite.setCache(this.acmId, annl);
1929:        if (this.acmId == null) return;
1930:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
1936:        Util.mark("Annotation.refreshLiteValid() refreshing " + this.acmId);
1937:        AnnotationLite.setCache(this.acmId, annl);
2047:    public Embedding getEmbeddingByMethod(String method, String methodVersion) {
2054:            if ((methodVersion == null) || (methodVersion.equals(emb.getMethodVersion())))

 succeeded in 10279ms:
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
?? src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java
migrate-ml-service-v2

codex
The helper is present in the working tree. I’m checking the adjacent existing `waitForVisibility` and `Annotation.getMatchQuery` implementation now, because the important review question is whether the new predicate really matches the established match query semantics.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '470,690p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '940,1030p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1170,1225p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '2035,2065p'" in /mnt/c/Wildbook-clean2
 succeeded in 197ms:
   470	        if (!isValidIndexName(indexName))
   471	            throw new IOException("invalid index name: " + indexName);
   472	        Request req = new Request("POST", indexName + "/_refresh");
   473	        getRestResponse(req);   // discard body; non-2xx surfaces as IOException
   474	    }
   475	
   476	    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
   477	    // can see every id in `ids` in `indexName`. Used by MlServiceProcessor
   478	    // (commit #9) post-persist to avoid running findMatchProspects against an
   479	    // index that doesn't yet contain the freshly-written annotations.
   480	    //
   481	    // On entry:
   482	    //   - normalizes `ids` to a Set (drops nulls and duplicates so they can't
   483	    //     prevent the count check from ever succeeding);
   484	    //   - calls _refresh once (synchronous; pushes pending writes through
   485	    //     Lucene's refresh boundary);
   486	    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
   487	    //     every poll return zero hits regardless of how long we wait.
   488	    //
   489	    // Then polls a _count eligibility query with exponential backoff (start
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
   628	        // Nested embedding clause. Method + methodVersion are added
   629	        // conditionally to match Annotation.getMatchQuery semantics: a
   630	        // blank method or methodVersion means "any" for that field.
   631	        JSONArray nestedMust = new JSONArray();
   632	        if (method != null && !method.isEmpty()) {
   633	            nestedMust.put(new JSONObject().put("term",
   634	                new JSONObject().put("embeddings.method", method)));
   635	        }
   636	        if (methodVersion != null && !methodVersion.isEmpty()) {
   637	            nestedMust.put(new JSONObject().put("term",
   638	                new JSONObject().put("embeddings.methodVersion", methodVersion)));
   639	        }
   640	        JSONObject nestedQuery;
   641	        if (nestedMust.length() == 0) {
   642	            // No method/version filter — wait only on the existence of a
   643	            // nested embedding entry.
   644	            nestedQuery = new JSONObject().put("match_all", new JSONObject());
   645	        } else {
   646	            nestedQuery = new JSONObject().put("bool",
   647	                new JSONObject().put("must", nestedMust));
   648	        }
   649	        filterArr.put(new JSONObject().put("nested",
   650	            new JSONObject().put("path", "embeddings").put("query", nestedQuery)));
   651	
   652	        JSONObject query = new JSONObject();
   653	        query.put("query",
   654	            new JSONObject().put("bool",
   655	                new JSONObject().put("filter", filterArr)));
   656	        return query;
   657	    }
   658	
   659	    // when you only care about how many this would return
   660	    public int queryCount(String indexName, final JSONObject query)
   661	    throws IOException {
   662	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   663	        Request searchRequest = new Request("POST", indexName + "/_count");
   664	        JSONObject cleanedQuery = new JSONObject(query.toString());
   665	        cleanedQuery.remove("_source"); // invalid for a _count query
   666	        searchRequest.setJsonEntity(cleanedQuery.toString());
   667	        JSONObject res = new JSONObject();
   668	        try {
   669	            res = new JSONObject(getRestResponse(searchRequest));
   670	        } catch (Exception ex) {
   671	            System.out.println("queryCount() on index " + indexName + " using query=" + query +
   672	                " failed with: " + ex);
   673	            ex.printStackTrace();
   674	            throw new IOException("queryCount() failed");
   675	        }
   676	        return res.optInt("count", -1);
   677	    }
   678	
   679	    public Map<String, Long> getAllVersions(String indexName)
   680	    throws IOException {
   681	        Map<String, Long> versions = new HashMap<String, Long>();
   682	        boolean reachedEnd = false;
   683	        JSONObject query = new JSONObject("{\"sort\":[{\"version\": \"asc\"}]}");
   684	        JSONObject res = queryRawScroll(indexName, query, 2000);
   685	
   686	        while (!reachedEnd) {
   687	            JSONObject outerHits = res.optJSONObject("hits");
   688	            if (outerHits == null) throw new IOException("outer hits failed");
   689	            JSONArray hits = outerHits.optJSONArray("hits");
   690	            if (hits == null) throw new IOException("hits failed");

 succeeded in 201ms:
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

 succeeded in 204ms:
  2035	
  2036	    // since embeddings is a set, there isnt really an order so...
  2037	    // pretty much random; null if we have none
  2038	    public Embedding getAnEmbedding() {
  2039	        return getEmbeddingByMethod(null, null);
  2040	    }
  2041	
  2042	    public Embedding getEmbeddingByMethod(String method) {
  2043	        return getEmbeddingByMethod(method, null);
  2044	    }
  2045	
  2046	    // suppose we could order by created?
  2047	    public Embedding getEmbeddingByMethod(String method, String methodVersion) {
  2048	        if (numberEmbeddings() < 1) return null;
  2049	        Iterator it = embeddings.iterator();
  2050	        if (method == null) return (Embedding)it.next();
  2051	        while (it.hasNext()) {
  2052	            Embedding emb = (Embedding)it.next();
  2053	            if (!method.equals(emb.getMethod())) continue;
  2054	            if ((methodVersion == null) || (methodVersion.equals(emb.getMethodVersion())))
  2055	                return emb;
  2056	        }
  2057	        return null;
  2058	    }
  2059	
  2060	    // this will match only vector (not other properties)
  2061	    public Embedding findEmbeddingByVector(Embedding find) {
  2062	        if (find == null) return null;
  2063	        if (numberEmbeddings() < 1) return null;
  2064	        Iterator it = embeddings.iterator();
  2065	        while (it.hasNext()) {

 succeeded in 209ms:
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
  1199	        JSONArray must = new JSONArray();
  1200	        JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
  1201	        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
  1202	            new JSONArray(emb.vectorToFloatArray()));
  1203	        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
  1204	        must.put(knn);
  1205	        if (method != null)
  1206	            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
  1207	        if (methodVersion != null)
  1208	            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
  1209	                "\"}}"));
  1210	        nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
  1211	            must);
  1212	
  1213	        // we put nested under its own top-level must, that way its score counts (whereas filter does not)
  1214	        JSONArray nestedMust = new JSONArray();
  1215	        nestedMust.put(nested);
  1216	        matchingSetQuery.getJSONObject("query").getJSONObject("bool").put("must", nestedMust);
  1217	        return matchingSetQuery;
  1218	    }
  1219	
  1220	    // finds annotations based on embedding vector matches
  1221	    // null means we didnt have an embedding to query with
  1222	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
  1223	        boolean useClauses, String method, String methodVersion) {
  1224	        return getMatches(myShepherd,
  1225	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '150,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"waitForAnnotationMatchableIds|buildAnnotationMatchableQuery|waitForVisibility\\(\" -S src test WEB-INF . | head -200" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getMatchQuery\\(|getMatches\\(|findMatchProspects|methodVersion|method\" src/main/java/org/ecocean | head -200" in /mnt/c/Wildbook-clean2
 succeeded in 241ms:
   150	        this.species = species;
   151	        this.features = f;
   152	        this.iaClass = iaClass;
   153	    }
   154	
   155	    @Override public String opensearchIndexName() { return "annotation"; }
   156	
   157	    @Override public long getVersion() {
   158	        return version;
   159	    }
   160	
   161	    public long setVersion() {
   162	        version = System.currentTimeMillis();
   163	        return version;
   164	    }
   165	
   166	    public JSONObject opensearchMapping() {
   167	        JSONObject map = super.opensearchMapping();
   168	        JSONObject keywordType = new JSONObject("{\"type\": \"keyword\"}");
   169	
   170	/*
   171	        JSONObject keywordNormalType = new org.json.JSONObject(
   172	            "{\"type\": \"keyword\", \"normalizer\": \"wildbook_keyword_normalizer\"}");
   173	 */
   174	
   175	        // "id" is done in Base
   176	        map.put("viewpoint", keywordType);
   177	        map.put("iaClass", keywordType);
   178	        map.put("acmId", keywordType);
   179	        map.put("encounterId", keywordType);
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
   216	        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
   217	        MediaAsset ma = this.getMediaAsset();
   218	        if (ma != null) {
   219	            jgen.writeNumberField("mediaAssetId", ma.getIdInt());
   220	        }
   221	        Encounter enc = this.findEncounter(myShepherd);
   222	        if (enc != null) {
   223	            jgen.writeStringField("encounterId", enc.getId());
   224	            jgen.writeStringField("encounterSubmitterId", enc.getSubmitterID());
   225	            jgen.writeStringField("encounterLocationId", enc.getLocationID());
   226	            jgen.writeStringField("encounterTaxonomy", enc.getTaxonomyString());
   227	            // per discussion on issue 874, including this in indexing, but not (yet) using in matchingSet
   228	            jgen.writeStringField("encounterLivingStatus", enc.getLivingStatus());
   229	            User owner = enc.getSubmitterUser(myShepherd);
   230	            if (owner != null) jgen.writeStringField("encounterUserUuid", owner.getId());
   231	            List<Project> projects = enc.getProjects(myShepherd);
   232	            if (!Util.collectionIsEmptyOrNull(projects)) {
   233	                jgen.writeArrayFieldStart("encounterProjectIds");
   234	                for (Project proj : projects) {
   235	                    jgen.writeString(proj.getId());
   236	                }
   237	                jgen.writeEndArray();
   238	            }
   239	            if (enc.getIndividual() != null) {
   240	                long tod = enc.getIndividual().getTimeOfDeath();
   241	                if (tod > 0) jgen.writeNumberField("encounterIndividualTimeOfDeath", tod);
   242	            }
   243	        }
   244	        jgen.writeArrayFieldStart("embeddings");
   245	        if (this.embeddings != null)
   246	            for (Embedding emb : this.embeddings) {
   247	                jgen.writeStartObject();
   248	                jgen.writeStringField("id", emb.getId());
   249	                jgen.writeStringField("method", emb.getMethod());
   250	                jgen.writeStringField("methodVersion", emb.getMethodVersion());
   251	                jgen.writeNumberField("created", emb.getCreated());
   252	
   253	                float[] vecFloat = emb.vectorToFloatArray();
   254	                // System.out.println("[INFO] indexing emb " + emb.getId() + " vector length " + ((vecFloat == null) ? "null" : vecFloat.length));
   255	                if ((vecFloat != null) && (vecFloat.length > 0)) {
   256	                    jgen.writeFieldName("vector");
   257	                    jgen.writeStartArray();
   258	                    for (int i = 0; i < vecFloat.length; i++) {
   259	                        jgen.writeNumber(vecFloat[i]);
   260	                    }

 succeeded in 938ms:
src/main/java/org/ecocean/CommonConfiguration.java:127:    // start getter methods
src/main/java/org/ecocean/CommonConfiguration.java:136:     * Utility method to return a {@code URI} instance for the specified context path of the server relating to the servlet request. This method
src/main/java/org/ecocean/CommonConfiguration.java:156:     * Utility method to return a URL string for the specified context path of the server relating to the servlet request. This method ensures all
src/main/java/org/ecocean/CommonConfiguration.java:572:     * This method is used to determined the show/hide condition of an element of the UI. It simply looks to see if a property is defined AND if the
src/main/java/org/ecocean/CommonConfiguration.java:573:     * property is false. For any other value or if the value is absent, the method returns true. Thsi means that conditional elements are shown by
src/main/java/org/ecocean/CommonConfiguration.java:633:     * Helper method to parse boolean from string.
src/main/java/org/ecocean/MarkedIndividual.java:861:    // TODO: evaluate and remove if deprecated:  ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/MarkedIndividual.java:898:    // TODO: evaluate and remove if deprecated:  ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/MarkedIndividual.java:2532:    // convenience method which uses the above prefix stuff
src/main/java/org/ecocean/LocationID.java:230:    public static String getPrefixForLocationID(String locationID, String qualifier) { // now a wrapper method
src/main/java/org/ecocean/LocationID.java:243:    public static int getPrefixDigitPaddingForLocationID(String locationID, String qualifier) { // now a wrapper method
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
src/main/java/org/ecocean/media/YouTubeAssetStore.java:198:    // note that uses same _processing derivation method as .grabAndParse() above.  this is cuz these should not be run simultaneous anyway
src/main/java/org/ecocean/genetics/TissueSample.java:102:            paramValues += "     Preservation method: " + this.getPreservationMethod() + "<br />";
src/main/java/org/ecocean/media/MediaAssetMetadata.java:52:    // a convenience method which accesses the data.attributes JSONObject
src/main/java/org/ecocean/Keyword.java:49:    // This method is overwritten by inheriting classes like LabeledKeyword
src/main/java/org/ecocean/Keyword.java:80:    // convenience method for removing duplicate keywords
src/main/java/org/ecocean/media/MediaAsset.java:97:     * To be called by AssetStore factory method.
src/main/java/org/ecocean/media/Feature.java:97:     * {@code Feature.asset}; this method is the symmetric setter for the
src/main/java/org/ecocean/Util.java:917:    // convenience method for comparing string values
src/main/java/org/ecocean/Util.java:1083:    // NOTE: a leading '/' is stripped off to be safe.  this puts the onus on the user of this method
src/main/java/org/ecocean/media/LocalAssetStore.java:119:    // convenience method to create directly from single File arguement (LocalAssetStore only)
src/main/java/org/ecocean/media/AssetStore.java:46: * AssetStore describes a location and methods for access to a set of MediaAssets.  Concrete subtypes fill in the "hows".
src/main/java/org/ecocean/media/AssetStore.java:275:        // right now we strictly bail on non-images. TODO: let various methods try to do whatever this means for their type
src/main/java/org/ecocean/IndexingManager.java:56:    		//IMPORTANT - no persistent objects, such as the passed in Base can be referenced inside this method
src/main/java/org/ecocean/User.java:432:    // Use this method to find out what organization-wide nameKey a user would want, to use to generate new individual names.
src/main/java/org/ecocean/User.java:497:    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
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
src/main/java/org/ecocean/Spot.java:6: * "setter" methods are allowed in order to preserve data integrity.
src/main/java/org/ecocean/ia/Task.java:408:    // internal utility method for above
src/main/java/org/ecocean/ia/Task.java:436:    // convenience method to construct the JSONObject from key/value
src/main/java/org/ecocean/ia/Task.java:657:    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
src/main/java/org/ecocean/ia/Task.java:772:            "unknown algorithm/method"));
src/main/java/org/ecocean/ia/Task.java:837:        JSONObject methodInfo = getIdentificationMethodInfo();
src/main/java/org/ecocean/ia/Task.java:840:        if (methodInfo != null) {
src/main/java/org/ecocean/ia/Task.java:841:            rtn.put("method", methodInfo);
src/main/java/org/ecocean/ia/Task.java:845:               (as there may be non-leaf nodes with methodInfo)
src/main/java/org/ecocean/api/SiteSettings.java:178:            // this seems like less desirable method: getAllKeywordsNoLabeledKeywords()
src/main/java/org/ecocean/servlet/WriteOutScanTask.java:320:    } // end writeResult method
src/main/java/org/ecocean/Occurrence.java:106:    // while convenience string-only methods with noun "Species"
src/main/java/org/ecocean/Occurrence.java:420:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:425:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:438:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:453:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:611:    // while convenience string-only methods use noun "Species"
src/main/java/org/ecocean/Occurrence.java:620:    // convenience method for e.g. web display
src/main/java/org/ecocean/Occurrence.java:728:    // This method simply iterates through the encounters for the occurrence and returns the first Encounter.locationID that it finds or returns null.
src/main/java/org/ecocean/TemplateFiller.java:197:     * @return Current text content of the template, or {@code null} if not loaded. This is the method to be used to retrieve the template contents
src/main/java/org/ecocean/api/SearchApi.java:62:                    res.put("error", "method not allowed");
src/main/java/org/ecocean/identity/IBEISIA.java:321:                // argh we need to standardize this and/or have a method. :/
src/main/java/org/ecocean/identity/IBEISIA.java:1117:    this is the re-tooling of this method which does nothing with encounter(s)
src/main/java/org/ecocean/identity/IBEISIA.java:1573:    1. how do we determine where the cutoff is for auto-creating the annotation?-- made some methods for this
src/main/java/org/ecocean/identity/IBEISIA.java:3182:     * TODO: evaluate as deprecated and remove. See primeIA() instead This static method sends all annotations and media assets for a species in Wildbook to Image Analysis in
src/main/java/org/ecocean/identity/IBEISIA.java:3538:        // this whole method is just navigating the pyramid of doom
src/main/java/org/ecocean/Organization.java:403:    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
src/main/java/org/ecocean/importutils/RowFeedback.java:47:            if (value == null) { // a tad experimental here. this means we don't have to check the parseSuccess in each getWhatever method
src/main/java/org/ecocean/social/SocialUnit.java:40:    // preserve calls to old method that required shepherd
src/main/java/org/ecocean/Taxonomy.java:24:    // while convenience string-only methods will use noun "Species" (and might require Shepherds to see which Taxonomy objects exist in the DB, for
src/main/java/org/ecocean/Taxonomy.java:98:    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
src/main/java/org/ecocean/OpenSearch.java:478:    // (commit #9) post-persist to avoid running findMatchProspects against an
src/main/java/org/ecocean/OpenSearch.java:555:    // embeddings.method/methodVersion would pass _id-only and then knn-fail
src/main/java/org/ecocean/OpenSearch.java:558:    // AND acmId exists AND a nested embedding for this method/version is
src/main/java/org/ecocean/OpenSearch.java:564:    // method/methodVersion follow the strict-when-present convention of
src/main/java/org/ecocean/OpenSearch.java:572:        String method, String methodVersion, long timeoutMs)
src/main/java/org/ecocean/OpenSearch.java:591:        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
src/main/java/org/ecocean/OpenSearch.java:592:            methodVersion);
src/main/java/org/ecocean/OpenSearch.java:616:        String method, String methodVersion) {
src/main/java/org/ecocean/OpenSearch.java:628:        // Nested embedding clause. Method + methodVersion are added
src/main/java/org/ecocean/OpenSearch.java:630:        // blank method or methodVersion means "any" for that field.
src/main/java/org/ecocean/OpenSearch.java:632:        if (method != null && !method.isEmpty()) {
src/main/java/org/ecocean/OpenSearch.java:634:                new JSONObject().put("embeddings.method", method)));
src/main/java/org/ecocean/OpenSearch.java:636:        if (methodVersion != null && !methodVersion.isEmpty()) {
src/main/java/org/ecocean/OpenSearch.java:638:                new JSONObject().put("embeddings.methodVersion", methodVersion)));
src/main/java/org/ecocean/OpenSearch.java:642:            // No method/version filter — wait only on the existence of a
src/main/java/org/ecocean/queue/QueueUtil.java:24:    // helper method for backgrounding queue consumers who dont background themselves
src/main/java/org/ecocean/NotificationMailer.java:52: * {@link #run()} method is invoked, usually via a wrapping
src/main/java/org/ecocean/NotificationMailer.java:104: * methods.</p>
src/main/java/org/ecocean/NotificationMailer.java:356:     * Loads an email template from the specified email types. This method traverses the list of types until one is found that provides a valid email
src/main/java/org/ecocean/NotificationMailer.java:436:     * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text and HTML messages respectively. This method doesn't attempt to load the files, just
src/main/java/org/ecocean/NotificationMailer.java:464:     * Performs a string search/replace on the subject and body of the template. This method is a convenience to perform all replacements throughout.
src/main/java/org/ecocean/NotificationMailer.java:474:     * Searches and replaces one or all occurrences of the specified regular expression search term with the specified replacement. This method is a
src/main/java/org/ecocean/IAJsonProperties.java:53:    // naming convention: not using 'get' on static methods
src/main/java/org/ecocean/IAJsonProperties.java:70:    // Detection methods
src/main/java/org/ecocean/IAJsonProperties.java:101:    // all detection methods take an int i, bc there may be multiple detection algos for a taxonomy
src/main/java/org/ecocean/IAJsonProperties.java:118:        // we can't just mess with config bc changes aren't scoped to this method
src/main/java/org/ecocean/IAJsonProperties.java:238:    // Identification methods
src/main/java/org/ecocean/IAJsonProperties.java:273:        // into ident configs returned by this method.
src/main/java/org/ecocean/IAJsonProperties.java:379:    // mimics an old IBEISIA method for easy migration
src/main/java/org/ecocean/Base.java:317:    // these two methods are kinda hacky needs for opensearchSyncIndex (e.g. the fact
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:25:    A Queue is not strictly necessary.  The intake methods could have simply done whatever processing necessary (e.g. send the objects to a blackbox
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:26:       IA service) in real-time.  The Queue just demostrates how to allow intake methods to asynchronously stack up jobs in the background.
src/main/java/org/ecocean/genetics/FStatistics.java:12:    1. Instantiate a new FStatistics object for each allele 2. Use the loadIndividual(...) method to load the individuals and alleles from each of the
src/main/java/org/ecocean/opendata/Share.java:88:        if (orgUsers == null) orgUsers = new ArrayList<User>(); // means we at least tried once!  (see methods below)
src/main/java/org/ecocean/SinglePhotoVideo.java:80:     * Returns the photo or video represented by this object as a java.io.File This is a convenience method.
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:37:    // having the code in these methods set the child on the parent  (hence the 'final')
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
src/main/java/org/ecocean/ia/MlServiceProcessor.java:472:            // findMatchProspects returns false when the match config is not
src/main/java/org/ecocean/ia/MlServiceProcessor.java:475:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:479:                    "findMatchProspects rejected match config: " +
src/main/java/org/ecocean/ia/MlServiceProcessor.java:497:            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:588:                    config.put("method", emb.getMethod());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:605:            if (!Util.stringExists(config.optString("method", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:607:                config.put("method", embeddingSource.optString("embedding_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:        String method = matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:742:        if (!Util.stringExists(method)) return ann.numberEmbeddings() > 0;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:743:        return ann.getEmbeddingByMethod(method, version) != null;
src/main/java/org/ecocean/QueryProcessor.java:13:    // a bunch of util methods
src/main/java/org/ecocean/QueryProcessor.java:92:    // This is probably not the ideal place for a method with this narrow of usage, but I've put it here in the interest
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
src/main/java/org/ecocean/Embedding.java:265:    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
src/main/java/org/ecocean/Embedding.java:267:        // contract (entries with `method`/`version`/`pipeline_root` and no
src/main/java/org/ecocean/Embedding.java:270:        boolean isVectorConfig = Util.stringExists(iaConfig.optString("method", null))
src/main/java/org/ecocean/Embedding.java:281:        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
src/main/java/org/ecocean/Embedding.java:302:                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:326:                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
src/main/java/org/ecocean/Embedding.java:345:                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
src/main/java/org/ecocean/Embedding.java:347:            // Read method/version directly from the new _id_conf contract;
src/main/java/org/ecocean/Embedding.java:349:            String method = iaConfig.optString("method", null);
src/main/java/org/ecocean/Embedding.java:351:            if (!Util.stringExists(method)) {
src/main/java/org/ecocean/Embedding.java:353:                method = mv[0];
src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:370:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
src/main/java/org/ecocean/Embedding.java:371:            System.out.println("findMatchProspects() on " + ann + " found " +
src/main/java/org/ecocean/Embedding.java:378:                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
src/main/java/org/ecocean/Embedding.java:381:                System.out.println("findMatchProspects() MatchResult creation failed on " +
src/main/java/org/ecocean/EmailTemplate.java:185:     * Performs a string search/replace on the subject and body of the template. This method is a convenience to perform all replacements throughout.
src/main/java/org/ecocean/EmailTemplate.java:234:     * Searches and replaces one or all occurrences of the specified regular expression search term with the specified replacement. This method is a
src/main/java/org/ecocean/EmailTemplate.java:329:     * {@link #reset()} method is called.
src/main/java/org/ecocean/EmailTemplate.java:677:     * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text and HTML messages respectively. This method doesn't attempt to load the files, just
src/main/java/org/ecocean/EmailTemplate.java:698:     * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text and HTML messages respectively. This method doesn't attempt to load the files, just
src/main/java/org/ecocean/api/Logout.java:41:        ThreadContext.put("http_method", request.getMethod());
src/main/java/org/ecocean/mmutil/MediaUtilities.java:76:     * Checks filename extension for supported image type. This method had been recreated here to allow static and centralized access;
src/main/java/org/ecocean/mmutil/MediaUtilities.java:87:     * Checks filename extension for supported video type. This method had been recreated here to allow static and centralized access;

2026-05-19T04:05:28.576643Z ERROR codex_core::tools::router: error=write_stdin failed: stdin is closed for this session; rerun exec_command with tty=true to keep stdin open
 succeeded in 4536ms:
rg: test: No such file or directory (os error 2)
rg: WEB-INF: No such file or directory (os error 2)
./docs/plans/2026-05-18-empty-match-prospects-design.md:50:  Phase 2 SQL and in `waitForAnnotationMatchableIds`, matching
./docs/plans/2026-05-18-empty-match-prospects-design.md:72:  `waitForAnnotationMatchableIds` (new matchable predicate).
./docs/plans/2026-05-18-empty-match-prospects-design.md:119:  Major). Renamed to `waitForAnnotationMatchableIds` (per round-3
./docs/plans/2026-05-18-empty-match-prospects-design.md:195:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-design.md:479:`waitForAnnotationMatchableIds` does the same for the nested OS
./docs/plans/2026-05-18-empty-match-prospects-design.md:538:public boolean waitForAnnotationMatchableIds(
./docs/plans/2026-05-18-empty-match-prospects-design.md:554:If `waitForAnnotationMatchableIds` returns false (timeout),
./docs/plans/2026-05-18-empty-match-prospects-design.md:568:if (!os.waitForVisibility("annotation", callerAnnotationIds,
./docs/plans/2026-05-18-empty-match-prospects-design.md:578:    !os.waitForAnnotationMatchableIds(siblingsOnly,
./docs/plans/2026-05-18-empty-match-prospects-design.md:968:- `waitForAnnotationMatchableIds_usesIdsQuery_notTermsOnUnderscoreId` —
./docs/plans/2026-05-18-empty-match-prospects-design.md:970:- `waitForAnnotationMatchableIds_bodyHasNoSizeOrTrackTotal` —
./docs/plans/2026-05-18-empty-match-prospects-design.md:1018:- Helper named `waitForAnnotationMatchableIds`, public on
./docs/plans/2026-05-18-c6-codex-review.md:76:use `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` (added
./docs/plans/2026-05-18-c6-codex-review.md:2455:docs/plans/2026-05-18-empty-match-prospects-design.md:195:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:2470:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:241:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-c6-codex-review.md:2477:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:573:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-18-c6-codex-review.md:2479:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:582:docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-c6-codex-review.md:2550:docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:350:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:2771:docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:312:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:2905:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:273:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:2913:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:947:docs/plans/2026-05-18-empty-match-prospects-design.md:98:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:2916:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1056:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:262:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:2920:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1321:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8941:    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:2924:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1398:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:241:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-c6-codex-review.md:2925:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1449:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:573:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-18-c6-codex-review.md:2927:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1458:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:582:docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-c6-codex-review.md:2933:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1700:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-18-c6-codex-review.md:3032:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:262:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:3099:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8941:    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-c6-codex-review.md:3260:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:205:   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:241:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:314:    waitForVisibility("annotation", [A], TIMEOUT)
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:349:        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:571:docs/plans/2026-05-09-ml-service-migration-v2.md:77:  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:573:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:574:docs/plans/2026-05-09-ml-service-migration-v2.md:245:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:580:docs/plans/2026-05-18-empty-match-prospects-design.md:30:   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:582:docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:589:docs/plans/2026-05-18-empty-match-prospects-design.md:139:    waitForVisibility("annotation", [A], TIMEOUT)
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:597:docs/plans/2026-05-18-empty-match-prospects-design.md:174:        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:644:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:655:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1369:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1868:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3305:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-c4-codex-review.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-09-ml-service-migration-v2.md:77:  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
./docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-09-ml-service-migration-v2.md:245:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:227:  `waitForAnnotationMatchableIds` (new matchable predicate).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:274:  Major). Renamed to `waitForAnnotationMatchableIds` (per round-3
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:350:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:683:public boolean waitForAnnotationMatchableIds(
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:699:If `waitForAnnotationMatchableIds` returns false (timeout),
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:713:if (!os.waitForVisibility("annotation", callerAnnotationIds,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:723:    !os.waitForAnnotationMatchableIds(siblingsOnly,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1100:- `waitForAnnotationMatchableIds_usesIdsQuery_notTermsOnUnderscoreId` —
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1102:- `waitForAnnotationMatchableIds_bodyHasNoSizeOrTrackTotal` —
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1150:- Helper named `waitForAnnotationMatchableIds`, public on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1230:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1246:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1259:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1938:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2139:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-c3-codex-review.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-c2-codex-review.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:236:  Major). Renamed to `waitForAnnotationMatchableIds` (per round-3
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:312:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:595:public boolean waitForAnnotationMatchableIds(
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:611:If `waitForAnnotationMatchableIds` returns false (timeout),
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:956:- `waitForAnnotationMatchableIds_usesIdsQuery_notTermsOnUnderscoreId` —
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:958:- `waitForAnnotationMatchableIds_bodyHasNoSizeOrTrackTotal` —
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:983:- Helper named `waitForAnnotationMatchableIds`, public on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1060:/bin/bash -lc 'rg -n "waitForVisibility|queryCount|indexRefresh|skipAutoIndexing|waitForAnnotationMatchableIds" src/main/java/org/ecocean/OpenSearch.java' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1096:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1195:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1302:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:3573:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7880:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8530:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8620:- **Major:** The “caller IDs never block on their own visibility” claim is not true if caller IDs are passed through `waitForAnnotationMatchableIds`. That helper filters `matchAgainst`, `acmId`, and embedding metadata. A caller annotation that is visible by `_id` but not matchable will defer until age-out. Either split the wait into `_id` visibility for caller IDs plus matchable visibility for sibling eligible IDs, or explicitly classify non-matchable caller annotations as a validation/rejected outcome.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8647:- **Major:** The “caller IDs never block on their own visibility” claim is not true if caller IDs are passed through `waitForAnnotationMatchableIds`. That helper filters `matchAgainst`, `acmId`, and embedding metadata. A caller annotation that is visible by `_id` but not matchable will defer until age-out. Either split the wait into `_id` visibility for caller IDs plus matchable visibility for sibling eligible IDs, or explicitly classify non-matchable caller annotations as a validation/rejected outcome.
./docs/plans/2026-05-18-c7-codex-review.md:76:use `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` (added
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:262:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:409:`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:834:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1203:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5952:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8941:    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9088:   234	`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:273:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:947:docs/plans/2026-05-18-empty-match-prospects-design.md:98:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1041:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1056:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:262:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1077:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:409:`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1110:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:834:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1128:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1203:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1226:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5952:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1321:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8941:    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1342:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9088:   234	`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1387:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1396:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:205:   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1398:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:241:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1412:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:314:    waitForVisibility("annotation", [A], TIMEOUT)
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1419:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:349:        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1447:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:571:docs/plans/2026-05-09-ml-service-migration-v2.md:77:  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1449:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:573:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1450:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:574:docs/plans/2026-05-09-ml-service-migration-v2.md:245:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1456:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:580:docs/plans/2026-05-18-empty-match-prospects-design.md:30:   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1458:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:582:docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1464:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:589:docs/plans/2026-05-18-empty-match-prospects-design.md:139:    waitForVisibility("annotation", [A], TIMEOUT)
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1470:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:597:docs/plans/2026-05-18-empty-match-prospects-design.md:174:        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1496:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:644:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1503:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:655:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1552:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1369:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1570:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1868:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1581:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3305:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1697:docs/plans/2026-05-09-ml-service-migration-v2.md:77:  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1700:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1703:docs/plans/2026-05-09-ml-service-migration-v2.md:245:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1728:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1797:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2665:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:3024:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:10405:1. Put `waitForMatchable` on `OpenSearch` only if it is explicitly annotation-schema-specific, e.g. `waitForAnnotationMatchableIds`. Otherwise keep it private to the gate.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:10407:3. Prefer `waitForAnnotationMatchableIds`; `waitForIndexed` is too broad.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:10433:1. Put `waitForMatchable` on `OpenSearch` only if it is explicitly annotation-schema-specific, e.g. `waitForAnnotationMatchableIds`. Otherwise keep it private to the gate.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:10435:3. Prefer `waitForAnnotationMatchableIds`; `waitForIndexed` is too broad.
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:16: * Shape-of-query tests for {@link OpenSearch#buildAnnotationMatchableQuery}.
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:17: * Full poll-and-wait behavior of {@link OpenSearch#waitForAnnotationMatchableIds}
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:44:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:60:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:70:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:80:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:88:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:111:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:125:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:141:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:153:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:162:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:16: * Shape-of-query tests for {@link OpenSearch#buildAnnotationMatchableQuery}.
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:17: * Full poll-and-wait behavior of {@link OpenSearch#waitForAnnotationMatchableIds}
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:44:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:60:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:70:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:80:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:88:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:111:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:125:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:141:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:153:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
./src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:162:        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:571:    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:584:                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
src/main/java/org/ecocean/OpenSearch.java:591:        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
src/main/java/org/ecocean/OpenSearch.java:612:    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as
src/main/java/org/ecocean/OpenSearch.java:615:    static JSONObject buildAnnotationMatchableQuery(Set<String> ids,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
./src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
./src/main/java/org/ecocean/OpenSearch.java:571:    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
./src/main/java/org/ecocean/OpenSearch.java:584:                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
./src/main/java/org/ecocean/OpenSearch.java:591:        JSONObject query = buildAnnotationMatchableQuery(targetIds, method,
./src/main/java/org/ecocean/OpenSearch.java:612:    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as
./src/main/java/org/ecocean/OpenSearch.java:615:    static JSONObject buildAnnotationMatchableQuery(Set<String> ids,
./src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {

2026-05-19T04:05:31.597582Z ERROR codex_core::tools::router: error=write_stdin failed: stdin is closed for this session; rerun exec_command with tty=true to keep stdin open
codex
One discrepancy is emerging around “blank means omitted”: the new helper treats blank strings as absent, but the current `Annotation.getMatchQuery` code only omits when the value is actually `null`. I’m tracing the ml-service config path to see whether callers normalize blanks before reaching matching.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '560,755p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,380p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "stringExists|stringIsEmptyOrNull" src/main/java/org/ecocean/Util.java' in /mnt/c/Wildbook-clean2
 succeeded in 213ms:
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

 succeeded in 220ms:
   560	                if (entry == null) continue;
   561	                if (entry.optBoolean("default", false)
   562	                    && "vector".equals(entry.optString("pipeline_root", null))) {
   563	                    matchConfig = new JSONObject(entry.toString());
   564	                    break;
   565	                }
   566	            }
   567	        }
   568	        if (matchConfig == null) matchConfig = new JSONObject();
   569	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
   570	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   571	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
   572	        }
   573	        return matchConfig;
   574	    }
   575	
   576	    private JSONObject inferMatchConfig(List<String> annotationIds) {
   577	        if (annotationIds == null || annotationIds.isEmpty()) return null;
   578	        Shepherd shep = new Shepherd(context);
   579	        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
   580	        try {
   581	            shep.beginDBTransaction();
   582	            for (String annId : annotationIds) {
   583	                Annotation ann = shep.getAnnotation(annId);
   584	                if (ann == null) continue;
   585	                Embedding emb = ann.getAnEmbedding();
   586	                if (emb != null) {
   587	                    JSONObject config = new JSONObject();
   588	                    config.put("method", emb.getMethod());
   589	                    config.put("version", emb.getMethodVersion());
   590	                    config.put("pipeline_root", "vector");
   591	                    return config;
   592	                }
   593	            }
   594	            return null;
   595	        } finally {
   596	            shep.rollbackAndClose();
   597	        }
   598	    }
   599	
   600	    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
   601	        JSONObject mlConfig) {
   602	        JSONObject config = (matchConfig == null) ? new JSONObject()
   603	            : new JSONObject(matchConfig.toString());
   604	        if (embeddingSource != null) {
   605	            if (!Util.stringExists(config.optString("method", null))
   606	                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
   607	                config.put("method", embeddingSource.optString("embedding_model_id"));
   608	            }
   609	            if (!Util.stringExists(config.optString("version", null))
   610	                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
   611	                config.put("version", embeddingSource.optString("embedding_model_version"));
   612	            }
   613	        }
   614	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
   615	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   616	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
   617	        }
   618	        return config;
   619	    }
   620	
   621	    private String detectionStaleReason(MediaAsset ma, Encounter enc, String encounterId) {
   622	        if (ma == null) return "media asset missing";
   623	        if (Util.stringExists(encounterId) && enc == null) return "encounter missing";
   624	        String status = ma.getDetectionStatus();
   625	        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
   626	        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
   627	        return null;
   628	    }
   629	
   630	    private String effectiveTaxonomyString(String taxonomyString, Encounter enc) {
   631	        if (Util.stringExists(taxonomyString)) return taxonomyString;
   632	        if (enc != null) return enc.getTaxonomyString();
   633	        return null;
   634	    }
   635	
   636	    private void markTaskError(String taskId, String code, String message) {
   637	        Shepherd shep = new Shepherd(context);
   638	        shep.setAction(ACTION_PREFIX + "markTaskError");
   639	        try {
   640	            shep.beginDBTransaction();
   641	            Task task = Task.load(taskId, shep);
   642	            markTaskError(task, code, message);
   643	            shep.commitDBTransaction();
   644	        } finally {
   645	            shep.rollbackAndClose();
   646	        }
   647	    }
   648	
   649	    private void markTaskCompleted(String taskId) {
   650	        Shepherd shep = new Shepherd(context);
   651	        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
   652	        try {
   653	            shep.beginDBTransaction();
   654	            markTaskCompleted(Task.load(taskId, shep));
   655	            shep.commitDBTransaction();
   656	        } finally {
   657	            shep.rollbackAndClose();
   658	        }
   659	    }
   660	
   661	    private void markTaskError(Task task, String code, String message) {
   662	        if (task == null) return;
   663	        task.setStatus(IBEISIA.STATUS_ERROR);
   664	        task.setStatusDetailsAddError(code == null ? "UNKNOWN" : code, message);
   665	        task.setCompletionDateInMilliseconds();
   666	    }
   667	
   668	    private void markTaskCompleted(Task task) {
   669	        if (task == null) return;
   670	        task.setStatus("completed");
   671	        task.setCompletionDateInMilliseconds();
   672	    }
   673	
   674	    private void markTaskDroppedStale(Shepherd shep, Task task, String reason) {
   675	        if (task == null) return;
   676	        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
   677	        task.setStatusDetailsAddLog(reason);
   678	        task.setCompletionDateInMilliseconds();
   679	    }
   680	
   681	    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
   682	        JSONObject payload = new JSONObject();
   683	        payload.put("mlServiceV2", true);
   684	        payload.put("deferredMatch", true);
   685	        payload.put("annotationIds", new JSONArray(annotationIds));
   686	        if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
   687	        // Carry __context in the payload so the dispatcher's
   688	        // jobj.optString("__context", "context0") fallback at IAGateway.java
   689	        // doesn't silently route the deferred-match into context0 when this
   690	        // processor is running in a non-default context.
   691	        payload.put("__context", context);
   692	        try {
   693	            IAGateway.addToDetectionQueue(context, payload.toString());
   694	        } catch (IOException ex) {
   695	            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
   696	        }
   697	    }
   698	
   699	    private static JSONObject featureParams(double[] bbox, double theta, String viewpoint) {
   700	        JSONObject params = new JSONObject();
   701	        params.put("x", bbox[0]);
   702	        params.put("y", bbox[1]);
   703	        params.put("width", bbox[2]);
   704	        params.put("height", bbox[3]);
   705	        params.put("theta", theta);
   706	        params.put("viewpoint", viewpoint);
   707	        return params;
   708	    }
   709	
   710	    private static double[] parseBbox(JSONArray bbox) {
   711	        return new double[] {
   712	            bbox.getDouble(0), bbox.getDouble(1), bbox.getDouble(2), bbox.getDouble(3)
   713	        };
   714	    }
   715	
   716	    private static double[] toDoubleArray(int[] bbox) {
   717	        return new double[] { bbox[0], bbox[1], bbox[2], bbox[3] };
   718	    }
   719	
   720	    private static List<String> jsonArrayToStringList(JSONArray array) {
   721	        List<String> values = new ArrayList<String>();
   722	        if (array == null) return values;
   723	        for (int i = 0; i < array.length(); i++) {
   724	            String value = array.optString(i, null);
   725	            if (Util.stringExists(value)) values.add(value);
   726	        }
   727	        return values;
   728	    }
   729	
   730	    private static Double optionalFiniteDouble(JSONObject obj, String key, Double fallback) {
   731	        if (obj == null || !obj.has(key)) return fallback;
   732	        double value = obj.optDouble(key, Double.NaN);
   733	        if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
   734	        return value;
   735	    }
   736	
   737	    private static boolean hasEmbeddingForMatchConfig(Annotation ann, JSONObject matchConfig) {
   738	        if (ann == null || ann.numberEmbeddings() < 1) return false;
   739	        if (matchConfig == null) return ann.numberEmbeddings() > 0;
   740	        String method = matchConfig.optString("method", null);
   741	        String version = matchConfig.optString("version", null);
   742	        if (!Util.stringExists(method)) return ann.numberEmbeddings() > 0;
   743	        return ann.getEmbeddingByMethod(method, version) != null;
   744	    }
   745	
   746	    private static boolean sameString(String a, String b) {
   747	        if (a == null) return b == null;
   748	        return a.equals(b);
   749	    }
   750	
   751	    private static final class ConfigPair {
   752	        final JSONObject mlConfig;
   753	        final JSONObject matchConfig;
   754	
   755	        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {

 succeeded in 290ms:
403:        if (stringExists(genus) && stringExists(species)) return genus + " " + species;
404:        if (stringExists(genus)) return genus;
405:        if (stringExists(species)) return species;
808:        if (Util.stringExists(request.getParameter(paramName))) {
870:    public static boolean stringExists(String str) {
875:    public static boolean stringIsEmptyOrNull(String str) {
880:        if (!stringExists(s1)) {
881:            if (!stringExists(s2)) return true;
884:        if (stringExists(s2)) {
919:        return (stringExists(val1) && !stringExists(val2));
929:        if (!stringExists(val2)) return val1;
930:        if (!stringExists(val1)) return val2;
1245:        if (!stringExists(modified)) return 0;
1272:            if (user.getReceiveEmails() && !stringIsEmptyOrNull(user.getEmailAddress()))

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Util.java | sed -n '860,880p'" in /mnt/c/Wildbook-clean2
 succeeded in 236ms:
   860	        }
   861	        result.append("]");
   862	        return result.toString();
   863	    }
   864	
   865	    public static String toString(Object obj) {
   866	        if (obj == null) return null;
   867	        return obj.toString();
   868	    }
   869	
   870	    public static boolean stringExists(String str) {
   871	        return (str != null && !str.trim().equals("") && !str.toLowerCase().equals("none") &&
   872	                   !str.toLowerCase().equals("unknown"));
   873	    }
   874	
   875	    public static boolean stringIsEmptyOrNull(String str) {
   876	        return ((str == null) || str.equals(""));
   877	    }
   878	
   879	    public static boolean stringsEqualish(String s1, String s2) {
   880	        if (!stringExists(s1)) {

exec
/bin/bash -lc "rg -n \"getMethodValues\" src/main/java/org/ecocean -S && nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '1,140p'" in /mnt/c/Wildbook-clean2
 succeeded in 769ms:
src/main/java/org/ecocean/Embedding.java:352:                String[] mv = MLService.getMethodValues(iaConfig);
src/main/java/org/ecocean/ia/MLService.java:333:        String[] methodValues = getMethodValues(res);
src/main/java/org/ecocean/ia/MLService.java:348:    public static String[] getMethodValues(JSONObject conf) {
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '300,370p'" in /mnt/c/Wildbook-clean2
 succeeded in 247ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '1,130p'" in /mnt/c/Wildbook-clean2
 succeeded in 241ms:
     1	package org.ecocean;
     2	
     3	import com.pgvector.PGvector;
     4	import java.io.IOException;
     5	import java.util.ArrayList;
     6	import java.util.Arrays;
     7	import java.util.Collection;
     8	import java.util.Iterator;
     9	import java.util.List;
    10	import javax.jdo.Query;
    11	import org.json.JSONObject;
    12	import org.postgresql.jdbc.PgArray;
    13	
    14	import org.json.JSONArray;
    15	import org.json.JSONException;
    16	
    17	import org.ecocean.ia.IAException;
    18	import org.ecocean.ia.MatchResult;
    19	import org.ecocean.ia.MLService;
    20	import org.ecocean.ia.Task;
    21	import org.ecocean.shepherd.core.Shepherd;
    22	
    23	// https://github.com/pgvector/pgvector
    24	// https://github.com/pgvector/pgvector-java
    25	// https://www.thenile.dev/blog/pgvector_myth_debunking
    26	// CREATE EXTENSION IF NOT EXISTS vector
    27	
    28	public class Embedding implements java.io.Serializable {
    29	    private String id;
    30	    private Annotation annotation;
    31	    private PGvector vector;
    32	    private float[] vectorFloatArray;
    33	    private String method;
    34	    private String methodVersion;
    35	    private long created;
    36	
    37	    // for trying to query vectors of annots without embeddings
    38	    public static int BACKGROUND_BATCH_SIZE = 50;
    39	    public static int BACKGROUND_MINUTES = 30;
    40	
    41	    public Embedding() {}
    42	
    43	    public Embedding(Annotation ann, String method, String methodVersion, PGvector vec) {
    44	        this.id = Util.generateUUID();
    45	        this.annotation = ann;
    46	        this.setVector(vec);
    47	        this.method = method;
    48	        this.methodVersion = methodVersion;
    49	        this.created = System.currentTimeMillis();
    50	        this.getVector();
    51	        if (ann != null) ann.addEmbedding(this);
    52	    }
    53	
    54	    public Embedding(Annotation ann, String method, String methodVersion, JSONArray vecArr) {
    55	        this(ann, method, methodVersion, (PGvector)null);
    56	        this.setVector(vecArr);
    57	        this.getVector();
    58	    }
    59	
    60	    public String getId() {
    61	        return id;
    62	    }
    63	
    64	    public Annotation getAnnotation() {
    65	        return annotation;
    66	    }
    67	
    68	    public void setAnnotation(Annotation ann) {
    69	        this.annotation = ann;
    70	        if (!ann.hasEmbedding(this)) ann.addEmbedding(this);
    71	    }
    72	
    73	    public PGvector getVector() {
    74	        if ((vector == null) && (vectorFloatArray != null))
    75	            vector = new PGvector(vectorFloatArray);
    76	        return vector;
    77	    }
    78	
    79	    public void setVector(PGvector vec) {
    80	        if (vec != null) {
    81	            vectorFloatArray = vec.toArray();
    82	        } else {
    83	            vectorFloatArray = null;
    84	        }
    85	        this.vector = vec;
    86	    }
    87	
    88	    public void setVector(JSONArray varr) {
    89	        this.setVector(vectorFromJSONArray(varr));
    90	    }
    91	
    92	    public String getMethod() {
    93	        return method;
    94	    }
    95	
    96	    public void setMethod(String method) {
    97	        this.method = method;
    98	    }
    99	
   100	    public String getMethodVersion() {
   101	        return methodVersion;
   102	    }
   103	
   104	    public void setMethodVersion(String methodVersion) {
   105	        this.methodVersion = methodVersion;
   106	    }
   107	
   108	    public String getMethodDescription() {
   109	        return ((method == null) ? "(unknown)" : method) + "-" + ((methodVersion ==
   110	                   null) ? "(unknown version)" : methodVersion);
   111	    }
   112	
   113	    public long getCreated() {
   114	        return created;
   115	    }
   116	
   117	    public float[] vectorToFloatArray() {
   118	        getVector();
   119	        if (vector == null) return null;
   120	        return vector.toArray();
   121	    }
   122	
   123	    public int vectorLength() {
   124	        getVector();
   125	        if (vector == null) return 0;
   126	        return vector.toArray().length;
   127	    }
   128	
   129	    public static PGvector vectorFromJSONArray(JSONArray varr) {
   130	        if (varr == null) return null;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '129,190p'" in /mnt/c/Wildbook-clean2
 succeeded in 217ms:
   129	    public static PGvector vectorFromJSONArray(JSONArray varr) {
   130	        if (varr == null) return null;
   131	        float[] vecVals = new float[varr.length()];
   132	        for (int i = 0; i < varr.length(); i++) {
   133	            try {
   134	                vecVals[i] = varr.getFloat(i);
   135	            } catch (JSONException ex) {
   136	                System.out.println("[WARNING] Embedding.setVector() could not getFloat at i=" + i +
   137	                    " of " + varr);
   138	                vecVals[i] = varr.getFloat(i);
   139	            }
   140	        }
   141	        return new PGvector(vecVals);
   142	    }
   143	
   144	/* note: these have been deprecated but just kept for reference
   145	
   146	    // these shenanigans could be avoided if datanucleus supported vectors, but alas
   147	    public PGvector loadVector(Shepherd myShepherd) {
   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
   149	            this.id + "'";
   150	        Query q = null;
   151	
   152	        try {
   153	            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
   154	            List results = (List)q.execute();
   155	            Iterator it = results.iterator();
   156	            if (it.hasNext()) {
   157	                PgArray parr = (PgArray)it.next();
   158	                Float[] vf = (Float[])parr.getArray();
   159	                // we need little-f so we gotta skip nulls, which we should never get from a db vector!
   160	                float[] vecVals = new float[vf.length];
   161	                for (int i = 0; i < vf.length; i++) {
   162	                    vecVals[i] = ((vf[i] == null) ? 0f : vf[i]);
   163	                }
   164	                this.vector = new PGvector(vecVals);
   165	            }
   166	        } catch (Exception ex) {
   167	            System.out.println("[ERROR] could not loadVector() on " + this.toString());
   168	            ex.printStackTrace();
   169	        } finally {
   170	            if (q != null) q.closeAll();
   171	        }
   172	        return this.vector;
   173	    }
   174	
   175	    public void storeVector(Shepherd myShepherd) {
   176	        if (vector == null) return;
   177	        List<String> vals = new java.util.ArrayList<String>();
   178	        for (float f : vector.toArray()) {
   179	            vals.add(Float.toString(f));
   180	        }
   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
   182	            vals) + "]' WHERE \"ID\"='" + this.id + "'";
   183	        Query q = null;
   184	
   185	        try {
   186	            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
   187	            q.execute();
   188	        } catch (Exception ex) {
   189	            System.out.println("[ERROR] could not storeVector() on " + this.toString());
   190	            ex.printStackTrace();

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '520,595p'" in /mnt/c/Wildbook-clean2
 succeeded in 256ms:
   520	            "query": { "bool": { "filter": [
   521	              { "term": { "embeddings.method":        "<method>" } },
   522	              { "term": { "embeddings.methodVersion": "<version>" } }
   523	            ] } }
   524	        } }
   525	      ]
   526	    }
   527	  }
   528	}
   529	```
   530	
   531	If `methodVersion` is null (legacy config without version), omit
   532	the version predicate, matching `getMatchQuery`'s behavior
   533	(strict-when-present, confirmed at `Annotation.java:1205-1209`).
   534	
   535	New method:
   536	
   537	```java
   538	public boolean waitForAnnotationMatchableIds(
   539	    Collection<String> ids,
   540	    String method,
   541	    String methodVersion,
   542	    long timeoutMs)
   543	throws IOException;
   544	```
   545	
   546	Same `_refresh`-on-entry + exponential-backoff polling pattern as
   547	`waitForVisibility`. Lives on `OpenSearch.java` (per Codex round-3
   548	open-question #1: annotation-schema-specific naming makes it
   549	acceptable as a public method). **Empty wait set short-circuits
   550	to `true`** (per round-3 open-question #4) — but normalize after
   551	unioning caller IDs, so the gate always waits on at least the
   552	caller's annotations.
   553	
   554	If `waitForAnnotationMatchableIds` returns false (timeout),
   555	**defer**.
   556	
   557	**Two-wait split** (Codex round-4 Major): passing caller IDs
   558	through the matchable predicate means a caller annotation that's
   559	visible by `_id` but not-yet-matchable would defer until age-out.
   560	Avoid that by running two waits:
   561	
   562	```java
   563	// Caller IDs: weaker predicate (just _id visibility). A caller
   564	// annotation should not block its own match on its own matchable-
   565	// metadata visibility; if it's missing matchAgainst/acmId/
   566	// embedding metadata, the match will just return zero candidates,
   567	// not hang the gate.
   568	if (!os.waitForVisibility("annotation", callerAnnotationIds,
   569	        VISIBILITY_TIMEOUT_MS)) {
   570	    return GateOutcome.defer(...);
   571	}
   572	
   573	// Sibling eligible IDs: full matchable predicate (id +
   574	// matchAgainst + acmId + embeddings.method/methodVersion).
   575	Set<String> siblingsOnly = new LinkedHashSet<>(eligibleIds);
   576	siblingsOnly.removeAll(callerAnnotationIds);
   577	if (!siblingsOnly.isEmpty() &&
   578	    !os.waitForAnnotationMatchableIds(siblingsOnly,
   579	        method, methodVersion, VISIBILITY_TIMEOUT_MS)) {
   580	    return GateOutcome.defer(...);
   581	}
   582	```
   583	
   584	This makes the caller's own match resilient: if the caller's
   585	embedding metadata is genuinely missing, that's a different
   586	problem (no candidates returned), not a stuck wait.
   587	
   588	#### Phase 4: run match
   589	
   590	`runMatchProspects(callerAnnotationIds, taskId, matchConfig)` —
   591	**unchanged**. The caller's own annotation IDs drive the match;
   592	the gate only ensures the OS corpus is populated.
   593	
   594	#### Pseudocode
   595	

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '590,640p'" in /mnt/c/Wildbook-clean2
 succeeded in 228ms:
   590	`runMatchProspects(callerAnnotationIds, taskId, matchConfig)` —
   591	**unchanged**. The caller's own annotation IDs drive the match;
   592	the gate only ensures the OS corpus is populated.
   593	
   594	#### Pseudocode
   595	
   596	```java
   597	private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
   598	    String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
   599	
   600	    GateOutcome g = visibilityGate.gateForBatch(
   601	        annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
   602	    switch (g.kind) {
   603	      case READY:
   604	        return runMatchProspects(annotationIds, taskId, matchConfig);
   605	      case DEFER:
   606	        enqueueDeferredMatch(annotationIds, taskId, matchConfig, g);
   607	        return MlServiceJobOutcome.ok(annotationIds);
   608	      case GIVE_UP:
   609	        // Bounded age-out: run match against whatever is visible
   610	        // rather than block forever. Log WARN + mark task with status
   611	        // detail indicating partial-batch match.
   612	        System.out.println("WARN: MatchVisibilityGate aged out for task "
   613	            + taskId + " after " + (g.attempt) + " attempts ("
   614	            + g.elapsedMillis + "ms): " + g.reason);
   615	        return runMatchProspects(annotationIds, taskId, matchConfig);
   616	    }
   617	}
   618	```
   619	
   620	`MatchVisibilityGate.gateForBatch(callerAnnotationIds, childTaskId,
   621	matchConfig, attempt, firstDeferredAt)` returns `READY` if Phase 1
   622	+ 2 + 3 all succeed, `DEFER` if any phase says "wait", `GIVE_UP`
   623	if `(System.currentTimeMillis() - firstDeferredAt) > MAX_AGE_MS`.
   624	
   625	`callerAnnotationIds` is passed in so the gate can:
   626	- Union it into the visibility set even if the eligibility filter
   627	  would otherwise exclude it (caller's match must not block on its
   628	  own visibility).
   629	- Degrade cleanly when the child task has no parent topTask
   630	  (orphan child case): wait only on callerAnnotationIds.
   631	
   632	#### Deferred-match path (`runDeferredMatch`) and backoff
   633	
   634	Today at `MlServiceProcessor.java:433-442`, `runDeferredMatch`
   635	reads `annotationIds` + `taskId` + `matchConfig` from the job
   636	payload and calls `runMatchProspects` directly — no re-gate.
   637	
   638	The existing requeue mechanism is `IAGateway.requeueJob` at
   639	`IAGateway.java:751-810`. Verified behavior:
   640	

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '950,980p'" in /mnt/c/Wildbook-clean2
 succeeded in 240ms:
   950	
   951	- `processor_callsRunMatchProspects_onlyAfterGateReady`
   952	- `processor_callsEnqueueDeferredMatch_onGateDefer`
   953	- `processor_runsMatchAnyway_onGateGiveUp_withWarnLog`
   954	- `runDeferredMatch_reGatesBeforeRunning`
   955	- `runDeferredMatch_agesOutAtMaxAge`
   956	
   957	**Round-3 follow-up tests:**
   958	
   959	- `gate_picksDirectChildTask_notTopTaskOrUnrelated` — set up
   960	  `Task.getTasksFor(ma)` to return [topTask, oldUnrelatedTask,
   961	  correctChild]; prove gate selects correctChild.
   962	- `gate_usesRawStatusAccessor_notGetStatusShepherd` — verify
   963	  `Task.getStatus(Shepherd)` is NOT called on siblings (mutation
   964	  side-effect would corrupt the gate's read).
   965	- `gate_treatsChildTaskCompleted_asTerminal` — child Task with
   966	  status `"completed"` (Task value) while MA still has status
   967	  `processing-mlservice`; sibling is terminal.
   968	- `waitForAnnotationMatchableIds_usesIdsQuery_notTermsOnUnderscoreId` —
   969	  observed body matches `OpenSearch.java:541` shape.
   970	- `waitForAnnotationMatchableIds_bodyHasNoSizeOrTrackTotal` —
   971	  `_count` doesn't accept those fields cleanly.
   972	- `deferred_agesOutAt12min_withinRequeueCap` — verify GIVE_UP
   973	  fires before requeueJob's 30-retry cap.
   974	- `safeInvalidate_swallowsNull_fromQueryCacheFactory`
   975	- `safeInvalidate_swallowsException_fromInvalidateByName`
   976	
   977	**End-to-end on live deployment:** import 4+ same-species images,
   978	observe that each annotation's `findMatchProspects` result
   979	includes the other 3 as candidates. Same fire-salamander test
   980	case that surfaced this bug.

exec
/bin/bash -lc "ps -eo pid,ppid,stat,cmd | rg \"git status|rg -n \\\"waitForAnnotationMatchableIds|buildAnnotationMatchableQuery|waitForVisibility|sed -n|nl -ba\"" in /mnt/c/Wildbook-clean2
 succeeded in 235ms:
      1       0 Ss   bwrap --new-session --die-with-parent --ro-bind / / --dev /dev --bind /tmp /tmp --perms 555 --tmpfs /tmp/.git --remount-ro /tmp/.git --perms 555 --tmpfs /tmp/.agents --remount-ro /tmp/.agents --perms 555 --tmpfs /tmp/.codex --remount-ro /tmp/.codex --bind /mnt/c/Wildbook-clean2 /mnt/c/Wildbook-clean2 --ro-bind /mnt/c/Wildbook-clean2/.git /mnt/c/Wildbook-clean2/.git --perms 555 --tmpfs /mnt/c/Wildbook-clean2/.agents --remount-ro /mnt/c/Wildbook-clean2/.agents --ro-bind-data 3 /mnt/c/Wildbook-clean2/.codex --bind /home/jason/.codex/memories /home/jason/.codex/memories --perms 555 --tmpfs /home/jason/.codex/memories/.git --remount-ro /home/jason/.codex/memories/.git --perms 555 --tmpfs /home/jason/.codex/memories/.agents --remount-ro /home/jason/.codex/memories/.agents --perms 555 --tmpfs /home/jason/.codex/memories/.codex --remount-ro /home/jason/.codex/memories/.codex --unshare-user --unshare-pid --unshare-net --proc /proc --argv0 codex-linux-sandbox -- /usr/lib/node_modules/@openai/codex/node_modules/@openai/codex-linux-x64/vendor/x86_64-unknown-linux-musl/codex/codex --sandbox-policy-cwd /mnt/c/Wildbook-clean2 --command-cwd /mnt/c/Wildbook-clean2 --permission-profile {"type":"managed","file_system":{"type":"restricted","entries":[{"path":{"type":"special","value":{"kind":"root"}},"access":"read"},{"path":{"type":"special","value":{"kind":"project_roots"}},"access":"write"},{"path":{"type":"special","value":{"kind":"slash_tmp"}},"access":"write"},{"path":{"type":"special","value":{"kind":"tmpdir"}},"access":"write"},{"path":{"type":"special","value":{"kind":"project_roots","subpath":".git"}},"access":"read"},{"path":{"type":"special","value":{"kind":"project_roots","subpath":".agents"}},"access":"read"},{"path":{"type":"special","value":{"kind":"project_roots","subpath":".codex"}},"access":"read"},{"path":{"type":"path","path":"/home/jason/.codex/memories"},"access":"write"}]},"network":"restricted"} --apply-seccomp-then-exec -- /bin/bash -c __CODEX_SNAPSHOT_OVERRIDE_SET_0="${CODEX_THREAD_ID+x}" __CODEX_SNAPSHOT_OVERRIDE_0="${CODEX_THREAD_ID-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_0="${ALL_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_0="${ALL_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_1="${BUNDLE_HTTPS_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_1="${BUNDLE_HTTPS_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_2="${BUNDLE_HTTP_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_2="${BUNDLE_HTTP_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_3="${BUNDLE_NO_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_3="${BUNDLE_NO_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_4="${CODEX_NETWORK_ALLOW_LOCAL_BINDING+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_4="${CODEX_NETWORK_ALLOW_LOCAL_BINDING-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_5="${CODEX_NETWORK_PROXY_ACTIVE+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_5="${CODEX_NETWORK_PROXY_ACTIVE-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_6="${DOCKER_HTTPS_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_6="${DOCKER_HTTPS_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_7="${DOCKER_HTTP_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_7="${DOCKER_HTTP_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_8="${ELECTRON_GET_USE_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_8="${ELECTRON_GET_USE_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_9="${FTP_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_9="${FTP_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_10="${HTTPS_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_10="${HTTPS_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_11="${HTTP_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_11="${HTTP_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_12="${NO_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_12="${NO_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_13="${NPM_CONFIG_HTTPS_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_13="${NPM_CONFIG_HTTPS_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_14="${NPM_CONFIG_HTTP_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_14="${NPM_CONFIG_HTTP_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_15="${NPM_CONFIG_NOPROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_15="${NPM_CONFIG_NOPROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_16="${NPM_CONFIG_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_16="${NPM_CONFIG_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_17="${PIP_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_17="${PIP_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_18="${WSS_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_18="${WSS_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_19="${WS_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_19="${WS_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_20="${YARN_HTTPS_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_20="${YARN_HTTPS_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_21="${YARN_HTTP_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_21="${YARN_HTTP_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_22="${YARN_NO_PROXY+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_22="${YARN_NO_PROXY-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_23="${all_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_23="${all_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_24="${ftp_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_24="${ftp_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_25="${http_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_25="${http_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_26="${https_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_26="${https_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_27="${no_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_27="${no_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_28="${npm_config_http_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_28="${npm_config_http_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_29="${npm_config_https_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_29="${npm_config_https_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_30="${npm_config_noproxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_30="${npm_config_noproxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_31="${npm_config_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_31="${npm_config_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_32="${ws_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_32="${ws_proxy-}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_33="${wss_proxy+x}" __CODEX_SNAPSHOT_PROXY_OVERRIDE_33="${wss_proxy-}" __CODEX_SNAPSHOT_PROXY_ENV_SET="${CODEX_NETWORK_PROXY_ACTIVE+x}"  if . '/home/jason/.codex/shell_snapshots/019e3e68-175a-7810-9d2a-f82089f84a88.1779163469660892321.sh' >/dev/null 2>&1; then :; fi  if [ -n "${__CODEX_SNAPSHOT_OVERRIDE_SET_0}" ]; then export CODEX_THREAD_ID="${__CODEX_SNAPSHOT_OVERRIDE_0}"; else unset CODEX_THREAD_ID; fi if [ -n "$__CODEX_SNAPSHOT_PROXY_ENV_SET" ] || [ -n "${CODEX_NETWORK_PROXY_ACTIVE+x}" ]; then if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_0}" ]; then export ALL_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_0}"; else unset ALL_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_1}" ]; then export BUNDLE_HTTPS_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_1}"; else unset BUNDLE_HTTPS_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_2}" ]; then export BUNDLE_HTTP_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_2}"; else unset BUNDLE_HTTP_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_3}" ]; then export BUNDLE_NO_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_3}"; else unset BUNDLE_NO_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_4}" ]; then export CODEX_NETWORK_ALLOW_LOCAL_BINDING="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_4}"; else unset CODEX_NETWORK_ALLOW_LOCAL_BINDING; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_5}" ]; then export CODEX_NETWORK_PROXY_ACTIVE="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_5}"; else unset CODEX_NETWORK_PROXY_ACTIVE; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_6}" ]; then export DOCKER_HTTPS_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_6}"; else unset DOCKER_HTTPS_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_7}" ]; then export DOCKER_HTTP_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_7}"; else unset DOCKER_HTTP_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_8}" ]; then export ELECTRON_GET_USE_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_8}"; else unset ELECTRON_GET_USE_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_9}" ]; then export FTP_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_9}"; else unset FTP_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_10}" ]; then export HTTPS_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_10}"; else unset HTTPS_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_11}" ]; then export HTTP_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_11}"; else unset HTTP_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_12}" ]; then export NO_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_12}"; else unset NO_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_13}" ]; then export NPM_CONFIG_HTTPS_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_13}"; else unset NPM_CONFIG_HTTPS_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_14}" ]; then export NPM_CONFIG_HTTP_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_14}"; else unset NPM_CONFIG_HTTP_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_15}" ]; then export NPM_CONFIG_NOPROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_15}"; else unset NPM_CONFIG_NOPROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_16}" ]; then export NPM_CONFIG_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_16}"; else unset NPM_CONFIG_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_17}" ]; then export PIP_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_17}"; else unset PIP_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_18}" ]; then export WSS_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_18}"; else unset WSS_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_19}" ]; then export WS_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_19}"; else unset WS_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_20}" ]; then export YARN_HTTPS_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_20}"; else unset YARN_HTTPS_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_21}" ]; then export YARN_HTTP_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_21}"; else unset YARN_HTTP_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_22}" ]; then export YARN_NO_PROXY="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_22}"; else unset YARN_NO_PROXY; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_23}" ]; then export all_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_23}"; else unset all_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_24}" ]; then export ftp_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_24}"; else unset ftp_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_25}" ]; then export http_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_25}"; else unset http_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_26}" ]; then export https_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_26}"; else unset https_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_27}" ]; then export no_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_27}"; else unset no_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_28}" ]; then export npm_config_http_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_28}"; else unset npm_config_http_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_29}" ]; then export npm_config_https_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_29}"; else unset npm_config_https_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_30}" ]; then export npm_config_noproxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_30}"; else unset npm_config_noproxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_31}" ]; then export npm_config_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_31}"; else unset npm_config_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_32}" ]; then export ws_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_32}"; else unset ws_proxy; fi if [ -n "${__CODEX_SNAPSHOT_PROXY_OVERRIDE_SET_33}" ]; then export wss_proxy="${__CODEX_SNAPSHOT_PROXY_OVERRIDE_33}"; else unset wss_proxy; fi fi  exec '/bin/bash' -c 'ps -eo pid,ppid,stat,cmd | rg "git status|rg -n \"waitForAnnotationMatchableIds|buildAnnotationMatchableQuery|waitForVisibility|sed -n|nl -ba"'
      2       1 S    /bin/bash -c ps -eo pid,ppid,stat,cmd | rg "git status|rg -n \"waitForAnnotationMatchableIds|buildAnnotationMatchableQuery|waitForVisibility|sed -n|nl -ba"
      4       2 S    rg git status|rg -n "waitForAnnotationMatchableIds|buildAnnotationMatchableQuery|waitForVisibility|sed -n|nl -ba

exec
/bin/bash -lc 'git rev-parse --abbrev-ref HEAD' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 301ms:
migrate-ml-service-v2

 succeeded in 414ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/OpenSearch.java

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
 succeeded in 235ms:
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
   137	    @Test void nestedClauseFallsBackToMatchAll_whenBothNullOrBlank() {
   138	        // Legacy api_endpoint-only config: neither method nor version is
   139	        // available, but the gate still wants to confirm SOME nested
   140	        // embedding exists.
   141	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   142	            ids("ann-1"), null, "");
   143	        JSONObject nested = findFilterClause(filterArr(q), "nested")
   144	            .getJSONObject("nested");
   145	        JSONObject inner = nested.getJSONObject("query");
   146	        assertTrue(inner.has("match_all"),
   147	            "expected match_all when method+version both blank: " + nested);
   148	    }
   149	
   150	    @Test void noTopLevelSizeOrTrackTotalHits() {
   151	        // queryCount() strips only `_source`; size and track_total_hits in
   152	        // the body would be invalid for _count. Codex round-3 Medium.
   153	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   154	            ids("ann-1"), "miewid-msv4.1", "4.1");
   155	        assertFalse(q.has("size"),
   156	            "size must not be present in _count body: " + q);
   157	        assertFalse(q.has("track_total_hits"),
   158	            "track_total_hits must not be present in _count body: " + q);
   159	    }
   160	
   161	    @Test void emptyIdSetProducesEmptyIdsValues() {
   162	        JSONObject q = OpenSearch.buildAnnotationMatchableQuery(
   163	            ids(), "miewid-msv4.1", "4.1");
   164	        JSONArray values = findFilterClause(filterArr(q), "ids")
   165	            .getJSONObject("ids").getJSONArray("values");
   166	        assertEquals(0, values.length());
   167	    }
   168	}

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '440,475p'" in /mnt/c/Wildbook-clean2
 succeeded in 255ms:
   440	            indexName + "/_search?scroll=" + SEARCH_SCROLL_TIME);
   441	
   442	        query.put("size", pageSize);
   443	        searchRequest.setJsonEntity(query.toString());
   444	        String rtn = getRestResponse(searchRequest);
   445	        return new JSONObject(rtn);
   446	    }
   447	
   448	    // this expects only json passed in, which is to continue paging on results from above
   449	    public JSONObject queryRawScroll(JSONObject scrollData)
   450	    throws IOException {
   451	        if (scrollData == null) throw new IOException("null data passed");
   452	        String scrollId = scrollData.optString("_scroll_id", null);
   453	        if (scrollData == null) throw new IOException("no _scroll_id");
   454	        JSONObject data = new JSONObject();
   455	        data.put("scroll", SEARCH_SCROLL_TIME);
   456	        data.put("scroll_id", scrollId);
   457	        Request searchRequest = new Request("POST", "_search/scroll");
   458	        searchRequest.setJsonEntity(data.toString());
   459	        String rtn = getRestResponse(searchRequest);
   460	        return new JSONObject(rtn);
   461	    }
   462	
   463	    // ml-service migration v2 (commit #7): force pending writes in `indexName`
   464	    // through Lucene's refresh boundary so they are searchable. Synchronous;
   465	    // returns after targeted shards have completed the refresh. NOT a Wildbook
   466	    // queue drain — IndexingManager may still have unindexed entities queued.
   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
   468	    public void indexRefresh(final String indexName)
   469	    throws IOException {
   470	        if (!isValidIndexName(indexName))
   471	            throw new IOException("invalid index name: " + indexName);
   472	        Request req = new Request("POST", indexName + "/_refresh");
   473	        getRestResponse(req);   // discard body; non-2xx surfaces as IOException
   474	    }
   475	

exec
/bin/bash -lc 'git ls-files --others --exclude-standard src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 358ms:
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java

exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/OpenSearch.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'file src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 207ms:
src/main/java/org/ecocean/OpenSearch.java:                             Java source, Unicode text, UTF-8 text
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java: C++ source, Unicode text, UTF-8 text

 succeeded in 312ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/OpenSearch.java

codex
**Findings**
- **Major:** Blank handling does not actually match the current match path. [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:632) and [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:636) omit predicates for `""`, but [Annotation.getMatchQuery](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205) only omits when values are `null`, and [getEmbeddingByMethod](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:2047) treats `methodVersion == ""` as strict. So `waitForAnnotationMatchableIds(ids, "miewid-msv4.1", "")` can return ready for docs with any `miewid-msv4.1` embedding, while matching may return `null` or query `embeddings.methodVersion:""`. Either normalize blank to `null` before both waiting and matching, or make the helper strict on non-null to match existing behavior. The current test at [OpenSearchAnnotationMatchableQueryTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:137) locks in behavior the matcher does not yet share.

**Notes**
- The core `_count` shape looks correct: `ids` under `bool.filter`, top-level `matchAgainst`, `acmId` exists, nested `embeddings` predicate, no `size` or `track_total_hits`.
- `match_all` under a nested query is the right OpenSearch shape for “at least one nested embedding exists,” assuming the blank/null normalization issue above is fixed consistently.
- I did not run tests, to keep this review-only and avoid build output. I did verify `OpenSearch.java` is `i/lf w/lf`; the new test file is present but untracked in this checkout, so make sure it is added when committing.
tokens used
94,519
**Findings**
- **Major:** Blank handling does not actually match the current match path. [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:632) and [OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:636) omit predicates for `""`, but [Annotation.getMatchQuery](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205) only omits when values are `null`, and [getEmbeddingByMethod](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:2047) treats `methodVersion == ""` as strict. So `waitForAnnotationMatchableIds(ids, "miewid-msv4.1", "")` can return ready for docs with any `miewid-msv4.1` embedding, while matching may return `null` or query `embeddings.methodVersion:""`. Either normalize blank to `null` before both waiting and matching, or make the helper strict on non-null to match existing behavior. The current test at [OpenSearchAnnotationMatchableQueryTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:137) locks in behavior the matcher does not yet share.

**Notes**
- The core `_count` shape looks correct: `ids` under `bool.filter`, top-level `matchAgainst`, `acmId` exists, nested `embeddings` predicate, no `size` or `track_total_hits`.
- `match_all` under a nested query is the right OpenSearch shape for “at least one nested embedding exists,” assuming the blank/null normalization issue above is fixed consistently.
- I did not run tests, to keep this review-only and avoid build output. I did verify `OpenSearch.java` is `i/lf w/lf`; the new test file is present but untracked in this checkout, so make sure it is added when committing.
