OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3e92-a1dc-7423-b667-ce7efeb5334e
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

# Codex code-review: Track 2 C9 — MatchEligibilityQuery

Empty-match-prospects design Track 2 C9: package-visible
direct-SQL utility that resolves match-eligible annotation IDs
reachable from a set of MediaAsset IDs. The 4-table join
(ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
EMBEDDING) follows the repo precedent at ImportTask.java:781
(Codex round-2 #5: direct SQL preferred over JDOQL here).

Method/methodVersion follow Annotation.getMatchQuery's strict-
when-non-null semantics (Codex round-5 Major): each predicate
present only when value is non-null; both null skips the
EMBEDDING join entirely (legacy api_endpoint-only configs).

## Diff



## New test file:

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Shape-of-SQL tests for {@link MatchEligibilityQuery#buildSql}.
 * Full execution against a real DataNucleus PMF is exercised by
 * the live integration harness on dev deployments; the SQL
 * builder is the mechanical part that benefits from unit-testing.
 * (Empty-match-prospects design Track 2 C9.)
 */
class MatchEligibilityQueryTest {

    private static Set<Integer> ids(Integer... s) {
        return new LinkedHashSet<Integer>(Arrays.asList(s));
    }

    @Test void selectsDistinctAnnotationId() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.startsWith("SELECT DISTINCT a.\"ID\""),
            "SQL should start with SELECT DISTINCT a.\"ID\": " + sql);
    }

    @Test void joinsAnnotationFeaturesAndMediaAssetFeatures() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("JOIN \"ANNOTATION_FEATURES\" af"),
            "missing ANNOTATION_FEATURES join: " + sql);
        assertTrue(sql.contains("JOIN \"MEDIAASSET_FEATURES\" mf"),
            "missing MEDIAASSET_FEATURES join: " + sql);
        assertTrue(sql.contains("af.\"ID_OID\" = a.\"ID\""),
            "wrong ANNOTATION_FEATURES join condition: " + sql);
        assertTrue(sql.contains("mf.\"ID_EID\" = af.\"ID_EID\""),
            "wrong MEDIAASSET_FEATURES join condition: " + sql);
    }

    @Test void joinsEmbeddingWhenMethodOrVersionProvided() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
            "EMBEDDING join missing when method+version supplied: " + sql);
        assertTrue(sql.contains("e.\"ANNOTATION_ID\" = a.\"ID\""));
    }

    @Test void joinsEmbeddingWhenOnlyMethodProvided() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", null);
        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
            "EMBEDDING join missing when only method supplied: " + sql);
    }

    @Test void joinsEmbeddingWhenOnlyVersionProvided() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), null, "4.1");
        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
            "EMBEDDING join missing when only version supplied: " + sql);
    }

    @Test void omitsEmbeddingJoinWhenBothNull() {
        // Legacy api_endpoint-only config: gate just wants matchAgainst +
        // acmId reachable from the sibling MAs; no embedding-method filter.
        String sql = MatchEligibilityQuery.buildSql(ids(42), null, null);
        assertFalse(sql.contains("JOIN \"EMBEDDING\""),
            "EMBEDDING join should be omitted when both null: " + sql);
        assertFalse(sql.contains("e.\"METHOD\""));
        assertFalse(sql.contains("e.\"METHODVERSION\""));
    }

    @Test void emitsMatchAgainstAndAcmIdFilters() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("a.\"MATCHAGAINST\" = true"),
            "missing matchAgainst filter: " + sql);
        assertTrue(sql.contains("a.\"ACMID\" IS NOT NULL"),
            "missing acmId IS NOT NULL filter: " + sql);
    }

    @Test void emitsMethodFilterStrictWhenNonNull_includingBlank() {
        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
        // A blank string is a strict equality on "" (matches no docs),
        // preserving consistency with the matcher.
        String sql = MatchEligibilityQuery.buildSql(ids(42), "", "4.1");
        assertTrue(sql.contains("AND e.\"METHOD\" = '' "),
            "expected strict '' equality for blank method: " + sql);
    }

    @Test void emitsVersionFilterStrictWhenNonNull_includingBlank() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "miewid-msv4.1", "");
        assertTrue(sql.contains("AND e.\"METHODVERSION\" = '' "),
            "expected strict '' equality for blank version: " + sql);
    }

    @Test void inListUsesNumericValuesWithoutQuotes() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(1, 42, 1000), "miewid-msv4.1", "4.1");
        assertTrue(sql.contains("IN (1,42,1000)"),
            "expected unquoted comma-separated IN list: " + sql);
    }

    @Test void skipsNullIdsInList() {
        // LinkedHashSet allows null. Builder must drop them silently.
        LinkedHashSet<Integer> ids = new LinkedHashSet<Integer>();
        ids.add(1);
        ids.add(null);
        ids.add(42);
        String sql = MatchEligibilityQuery.buildSql(ids, null, null);
        assertTrue(sql.contains("IN (1,42)"),
            "expected null to be skipped from IN list: " + sql);
    }

    @Test void escapesSingleQuotesInMethodAndVersion() {
        String sql = MatchEligibilityQuery.buildSql(
            ids(42), "ev'il", "v'1");
        // Single quote inside the value must be doubled per SQL escaping.
        assertTrue(sql.contains("e.\"METHOD\" = 'ev''il'"),
            "method not escaped: " + sql);
        assertTrue(sql.contains("e.\"METHODVERSION\" = 'v''1'"),
            "version not escaped: " + sql);
    }

    @Test void emptyMaSetReturnsEmpty_inFindEligibleAnnotationIds() {
        // The execute path bails out before any DB call when the input
        // is empty. We can't easily test execute() without a Shepherd
        // mock, but the early-return path is covered.
        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
            null, Collections.<Integer>emptySet(), "miewid-msv4.1", "4.1");
        assertEquals(0, out.size());
    }

    @Test void nullMaSetReturnsEmpty_inFindEligibleAnnotationIds() {
        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
            null, null, "miewid-msv4.1", "4.1");
        assertEquals(0, out.size());
    }
}
```


## Validation

- MatchEligibilityQueryTest: 14/14 (new) covering SQL builder shape.
- Compiles cleanly into target/classes.
- LF-normalized.

## Review scope

- Join correctness: `ANNOTATION ↔ ANNOTATION_FEATURES.ID_OID` is
  the annotation→feature link; `MEDIAASSET_FEATURES.ID_EID` ties
  feature to media-asset via the same Feature ID. `EMBEDDING.ANNOTATION_ID`
  is the direct FK to ANNOTATION.ID (no _OID suffix per the JDO
  schema-skill note).
- The SELECT DISTINCT is needed because an annotation with
  multiple embeddings of the same method/version (rare but
  possible) would duplicate-row otherwise.
- IN-list interpolation uses primitive int from Integer.intValue();
  no quoting/escape needed since the type is enforced numeric.
- Method/methodVersion strings interpolated with single-quote
  doubling. Source is admin-controlled IA.json; the escape is
  defense-in-depth.
- Test surface covers the builder (`buildSql`) plus the
  empty-input + null-input paths through `findEligibleAnnotationIds`.
  Execution against a real DataNucleus PMF would require a
  Shepherd mock or integration setup; this matches the project's
  established WildbookIAMRegisterTest pattern of pure-function
  tests + live integration for non-pure paths.

Anything else (especially: SQL correctness, performance for large
sibling sets, missing-cleanup of the JDO Query).

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll review this as read-only. I’m going to inspect the implementation, the new tests, and the nearby schema precedents so the findings are tied to actual file/line references.
exec
/bin/bash -lc 'rg -n "class MatchEligibilityQuery|MatchEligibilityQuery|buildSql|findEligibleAnnotationIds" .' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|EMBEDDING|ANNOTATION_ID|MATCHAGAINST|ACMID" src main test .' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'GIT_OPTIONAL_LOCKS=0 git diff --name-only --' in /mnt/c/Wildbook-clean2
 succeeded in 4968ms:
./docs/plans/2026-05-18-empty-match-prospects-design.md:54:  on `QueryCacheFactory`, `MatchEligibilityQuery` extraction,
./docs/plans/2026-05-18-empty-match-prospects-design.md:84:- **`MatchEligibilityQuery` extracted as package-visible class
./docs/plans/2026-05-18-empty-match-prospects-design.md:423:package-visible `MatchEligibilityQuery` class under
./docs/plans/2026-05-18-empty-match-prospects-design.md:430:public final class MatchEligibilityQuery {
./docs/plans/2026-05-18-empty-match-prospects-design.md:431:    public static Set<String> findEligibleAnnotationIds(
./docs/plans/2026-05-18-empty-match-prospects-design.md:812:`MatchEligibilityQuery` utility, so it can be unit-tested with a
./docs/plans/2026-05-18-empty-match-prospects-design.md:1030:- `MatchEligibilityQuery` extracted as package-visible class in
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:529:`MatchEligibilityQuery` utility, so it can be unit-tested with a
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:649:5. **JDOQL vs. direct SQL** for `findEligibleAnnotationIdsForBatch`:
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9228:   354	`MatchEligibilityQuery` utility, so it can be unit-tested with a
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9353:   474	5. **JDOQL vs. direct SQL** for `findEligibleAnnotationIdsForBatch`:
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:239:- **`MatchEligibilityQuery` extracted as package-visible class
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:578:package-visible `MatchEligibilityQuery` class under
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:585:public final class MatchEligibilityQuery {
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:586:    public static Set<String> findEligibleAnnotationIds(
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:950:`MatchEligibilityQuery` utility, so it can be unit-tested with a
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1162:- `MatchEligibilityQuery` extracted as package-visible class in
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4625:  1002	   (a) Static method on a new `MatchEligibilityQuery` utility class.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8465:- `MatchEligibilityQuery` as package-visible in `org.ecocean.ia` is better than hiding schema-sensitive SQL inside the gate.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8489:- `MatchEligibilityQuery` as package-visible in `org.ecocean.ia` is better than hiding schema-sensitive SQL inside the gate.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:808:`MatchEligibilityQuery` utility, so it can be unit-tested with a
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1002:   (a) Static method on a new `MatchEligibilityQuery` utility class.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8634:2. I would not keep the direct SQL private inside `MatchVisibilityGateImpl`. Use a small package-visible `MatchEligibilityQuery` in `org.ecocean.ia`, or at least a package-visible query builder plus executor. This query is schema-sensitive enough to deserve focused tests.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8661:2. I would not keep the direct SQL private inside `MatchVisibilityGateImpl`. Use a small package-visible `MatchEligibilityQuery` in `org.ecocean.ia`, or at least a package-visible query builder plus executor. This query is schema-sensitive enough to deserve focused tests.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:688:`MatchEligibilityQuery` utility, so it can be unit-tested with a
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:15: * Shape-of-SQL tests for {@link MatchEligibilityQuery#buildSql}.
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:21:class MatchEligibilityQueryTest {
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:28:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:35:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:48:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:56:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:63:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:72:        String sql = MatchEligibilityQuery.buildSql(ids(42), null, null);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:80:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:92:        String sql = MatchEligibilityQuery.buildSql(ids(42), "", "4.1");
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:98:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:105:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:117:        String sql = MatchEligibilityQuery.buildSql(ids, null, null);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:123:        String sql = MatchEligibilityQuery.buildSql(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:136:        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:142:        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:38:public final class MatchEligibilityQuery {
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:40:    private MatchEligibilityQuery() { }
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:62:    public static Set<String> findEligibleAnnotationIds(
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:128:                "[ERROR] MatchEligibilityQuery.findEligibleAnnotationIds() failed: " + ex);
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:166:    static String buildSql(Collection<Integer> siblingMaIds, String method,

 exited 2 in 7077ms:
rg: main: No such file or directory (os error 2)
rg: test: No such file or directory (os error 2)
./archive/sql/ml_service_idempotency.sql:76:WHERE "ACMID" IS NOT NULL AND "WBIAREGISTERED" IS NULL;
./archive/sql/ml_service_embedding_audit.sql:1:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./archive/sql/ml_service_embedding_audit.sql:3:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./archive/sql/ml_service_embedding_audit.sql:4:-- (ANNOTATION_ID, METHOD, METHODVERSION) is created by JDO in
./archive/sql/ml_service_embedding_audit.sql:11:SELECT "ANNOTATION_ID", "METHOD", "METHODVERSION", COUNT(*) AS dup_count
./archive/sql/ml_service_embedding_audit.sql:12:FROM "EMBEDDING"
./archive/sql/ml_service_embedding_audit.sql:13:GROUP BY "ANNOTATION_ID", "METHOD", "METHODVERSION"
./archive/sql/ml_service_embedding_audit.sql:15:ORDER BY dup_count DESC, "ANNOTATION_ID";
./src/main/webapp/iaResults.jsp:828:		var matchAgainstACMIDs="";
./src/main/webapp/iaResults.jsp:833:			if(i==0){matchAgainstACMIDs=acmId;}
./src/main/webapp/iaResults.jsp:834:			else{matchAgainstACMIDs=matchAgainstACMIDs+","+acmId;}
./src/main/webapp/iaResults.jsp:837:		matchAgainstACMIDs=matchAgainstACMIDs+","+qannotId;
./src/main/webapp/iaResults.jsp:839:		let paramString = 'iaResultsAnnotFeed.jsp?acmId=' + matchAgainstACMIDs;
./src/main/webapp/import.jsp:452:	    	ArrayList<MediaAsset> fixACMIDAssets=new ArrayList<MediaAsset>();
./src/main/webapp/import.jsp:505:	                    	if(!fixACMIDAssets.contains(ma))fixACMIDAssets.add(ma);
./src/main/webapp/import.jsp:513:                if(fixACMIDAssets.size()>0 && "complete".equals(itask.getStatus())){
./src/main/webapp/import.jsp:517:                	//IBEISIA.sendMediaAssetsNew(fixACMIDAssets, context);
./src/main/webapp/import.jsp:621:		int numWithACMID=0;
./src/main/webapp/import.jsp:626:			if(asset.getAcmId()!=null)numWithACMID++;
./src/main/webapp/import.jsp:645:			<li>Number with acmIDs: <%=numWithACMID %></li>
./src/main/webapp/encounters/encounterMediaGallery.jsp:303:        String sql = "select \"MEDIAASSET\".\"ID\" as assetId, \"MEDIAASSET\".\"ACMID\" as assetAcmId, \"ENCOUNTER\".\"CATALOGNUMBER\" as encId, \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"INDIVIDUALID_OID\" as indivId from \"MEDIAASSET\" join \"MEDIAASSET_FEATURES\" on (\"ID\" = \"ID_OID\") join \"ANNOTATION_FEATURES\" using (\"ID_EID\") join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") join \"ENCOUNTER\" on (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") left join \"MARKEDINDIVIDUAL_ENCOUNTERS\" on (\"ENCOUNTER\".\"CATALOGNUMBER\" = \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"CATALOGNUMBER_EID\") where \"MEDIAASSET\".\"ACMID\" in ('" + String.join("', '", maAcms) + "') AND \"MEDIAASSET\".\"ID\" not in (" + String.join(", ", maIds) + ");";
./archive/sql/indices.sql:28:CREATE INDEX IF NOT EXISTS "ANNOTATION_MATCHAGAINST_IDX" ON "ANNOTATION" ("MATCHAGAINST");
./archive/sql/indices.sql:29:CREATE INDEX IF NOT EXISTS "ANNOTATION_ACMID_IDX" ON "ANNOTATION" ("ACMID");
./docs/plans/2026-05-18-empty-match-prospects-design.md:444:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-design.md:445:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-design.md:447:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-design.md:449:  AND a."MATCHAGAINST" = true
./docs/plans/2026-05-18-empty-match-prospects-design.md:450:  AND a."ACMID" IS NOT NULL
./docs/plans/2026-05-18-empty-match-prospects-design.md:455:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-design.md:463:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-design.md:944:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2955:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2956:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4392:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4896:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5539:   582				<index name="ANNOTATION_MATCHAGAINST_idx" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:599:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:600:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:602:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:604:  AND a."MATCHAGAINST" = true
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:605:  AND a."ACMID" IS NOT NULL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:610:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:618:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1076:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2309:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2870:/bin/bash -lc "rg -n \"class Annotation|class Feature|class MediaAsset|class Embedding|matchAgainst|acmId|List<Feature>|annotations|embeddings|METHOD|METHODVERSION|ANNOTATION_ID\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/media/MediaAsset.java src/main/resources -g '*.{java,xml,jdo}'" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2886:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2999:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3000:src/main/resources/org/ecocean/package.jdo:1048:			<column name="ANNOTATION_ID"/>
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3003:src/main/resources/org/ecocean/package.jdo:1057:			<column name="ANNOTATION_ID" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3004:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3005:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3188:    12				<index name="MEDIAASSET_ACMID_INDEX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3317:   582				<index name="ANNOTATION_MATCHAGAINST_idx" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3370:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3371:  1048				<column name="ANNOTATION_ID"/>
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3380:  1057				<column name="ANNOTATION_ID" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3387:  1064				<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3392:  1069				<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3397:  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3827:/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|TASK_OBJECTMEDIAASSETS|TASK_OBJECTANNOTATIONS|TASK_CHILDREN|EMBEDDING\" . -g '*.{sql,md,java,xml,jdo}'" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3926:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4316:./docs/plans/2026-05-18-empty-match-prospects-design.md:424:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4317:./docs/plans/2026-05-18-empty-match-prospects-design.md:425:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4318:./docs/plans/2026-05-18-empty-match-prospects-design.md:427:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4319:./docs/plans/2026-05-18-empty-match-prospects-design.md:435:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4320:./docs/plans/2026-05-18-empty-match-prospects-design.md:443:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4321:./docs/plans/2026-05-18-empty-match-prospects-design.md:901:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4322:./archive/sql/ml_service_embedding_audit.sql:1:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4323:./archive/sql/ml_service_embedding_audit.sql:3:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4324:./archive/sql/ml_service_embedding_audit.sql:12:FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4325:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4326:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4328:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4329:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2955:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4330:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2956:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4331:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4392:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4332:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4896:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4333:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4334:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4336:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4337:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:383:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4338:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:384:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4339:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:386:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4340:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:394:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4341:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:402:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4342:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:603:  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4343:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:652:   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4344:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2513:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4345:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3378:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4346:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3647:   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4347:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3762:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4348:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3766:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4349:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3767:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4350:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3797:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4351:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3814:  1064				<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4352:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3819:  1069				<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4353:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3824:  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4354:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4003:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4355:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5498:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4356:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5574:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4357:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5604:   211	        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4358:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5614:   221	            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4361:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9062:   208	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4362:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9063:   209	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4363:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9065:   211	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4364:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9073:   219	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4365:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9081:   227	  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4366:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9302:   428	  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4367:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9356:   477	   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4368:./docs/plans/2026-05-09-ml-service-migration-v2.md:333:    SELECT "ANNOTATION_ID" FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4370:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4371:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4373:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4374:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:520:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4375:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:521:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4376:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:523:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4377:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:531:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4378:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:539:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4379:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:932:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4380:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:999:   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4381:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1000:   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4382:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4508:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4383:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6522:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4384:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6539:			<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4385:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6544:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4386:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6549:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4387:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6557:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4388:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6559:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4389:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6568:FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4390:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6668:/bin/bash -lc 'rg -n "MEDIAASSET_FEATURES|ANNOTATION_FEATURES|MEDIAASSET_ID_OID|FEATURE|FEATURES|EMBEDDING" archive src/main/resources src/main/java docs/plans | head -n 120' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4391:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6807:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4392:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6929:docs/plans/2026-05-18-empty-match-prospects-design.md:345:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4393:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6930:docs/plans/2026-05-18-empty-match-prospects-design.md:346:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4394:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6931:docs/plans/2026-05-18-empty-match-prospects-design.md:348:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4395:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6932:docs/plans/2026-05-18-empty-match-prospects-design.md:356:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4396:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6933:docs/plans/2026-05-18-empty-match-prospects-design.md:364:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4397:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6934:docs/plans/2026-05-18-empty-match-prospects-design.md:757:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4398:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6935:docs/plans/2026-05-18-empty-match-prospects-design.md:824:   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4399:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6936:docs/plans/2026-05-18-empty-match-prospects-design.md:825:   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4400:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6948:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4401:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6949:src/main/resources/org/ecocean/package.jdo:1064:			<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4402:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6950:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4403:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6951:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4404:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6952:archive/sql/ml_service_embedding_audit.sql:1:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4405:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6953:archive/sql/ml_service_embedding_audit.sql:3:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4406:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6954:archive/sql/ml_service_embedding_audit.sql:12:FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4407:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6955:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4408:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6956:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4409:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6957:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4410:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6959:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2955:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4411:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6960:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2956:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4412:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6961:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4392:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4413:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6963:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4896:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4414:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6964:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4415:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6965:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4416:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6966:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4417:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6967:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:426:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4418:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6968:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:427:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4419:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6969:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:429:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4420:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6970:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:437:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4421:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6971:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:445:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4422:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6972:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:812:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4423:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6973:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:859:   My draft uses `EMBEDDING.METHODVERSION = ?` strictly — Codex
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4424:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6974:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1007:docs/plans/2026-05-18-empty-match-prospects-design.md:637:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4425:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6977:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2588:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4426:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6978:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5541:/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|name=\\\"features\\\"|mapped-by=\\\"annotation\\\"|mapped-by=\\\"asset\\\"|class name=\\\"Annotation\\\"|class name=\\\"MediaAsset\\\"|class name=\\\"Embedding\\\"|ANNOTATION_ID|dependent-element\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/media/package.jdo src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4427:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6979:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5642:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4428:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6980:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6125:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4429:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6981:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6127:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4430:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6982:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6242:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4431:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6983:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6260:/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|MEDIAASSET_ID|ASSET_ID|FEATURES|ANNOTATION_ID" archive src/test src/main/java | head -200' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4432:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6986:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6268:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4433:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6988:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6270:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4434:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6989:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6271:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4435:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6990:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6272:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4436:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6991:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6273:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4437:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6994:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6377:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4438:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6995:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6378:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4439:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6997:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9059:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4440:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7001:docs/plans/2026-05-09-ml-service-migration-v2.md:333:    SELECT "ANNOTATION_ID" FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4441:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7002:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4442:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7003:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4443:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7004:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4444:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7005:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:383:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4445:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7006:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:384:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4446:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7007:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:386:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4447:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7008:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:394:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4448:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7009:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:402:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4449:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7010:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:603:  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4450:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7011:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:652:   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4451:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7013:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2513:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4452:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7015:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3378:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4453:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7016:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3647:   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4454:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7017:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3762:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4455:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7018:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3766:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4456:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7019:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3767:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4457:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7020:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3797:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4458:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7021:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3814:  1064				<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4459:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7022:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3819:  1069				<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4460:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7023:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3824:  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4461:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7024:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4003:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4462:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7026:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5498:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4463:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7027:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5574:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4464:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7028:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5604:   211	        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4465:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7029:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5614:   221	            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4466:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7030:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9062:   208	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4467:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7031:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9063:   209	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4468:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7032:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9065:   211	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4469:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7033:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9073:   219	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4470:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7034:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9081:   227	  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4471:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7035:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9302:   428	  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4472:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7036:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9356:   477	   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4473:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7038:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4474:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7039:src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4475:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7040:src/main/java/org/ecocean/Embedding.java:211:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4476:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7041:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4477:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7042:src/main/java/org/ecocean/Embedding.java:261:        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4478:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7045:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4479:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7046:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4480:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7047:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4481:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7048:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4482:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7181:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4483:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7182:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4484:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8063:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4485:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8073:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4486:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8121:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4487:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8154:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4488:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4489:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4491:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4492:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:426:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4493:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:427:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4494:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:429:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4495:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:437:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4496:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:445:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4497:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:812:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4498:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:859:   My draft uses `EMBEDDING.METHODVERSION = ?` strictly — Codex
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4499:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1007:docs/plans/2026-05-18-empty-match-prospects-design.md:637:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4500:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2588:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4501:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5541:/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|name=\\\"features\\\"|mapped-by=\\\"annotation\\\"|mapped-by=\\\"asset\\\"|class name=\\\"Annotation\\\"|class name=\\\"MediaAsset\\\"|class name=\\\"Embedding\\\"|ANNOTATION_ID|dependent-element\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/media/package.jdo src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4502:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5642:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4503:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6125:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4504:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6127:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4505:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6242:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4506:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6260:/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|MEDIAASSET_ID|ASSET_ID|FEATURES|ANNOTATION_ID" archive src/test src/main/java | head -200' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4507:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6268:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4508:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6270:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4509:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6271:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4510:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6272:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4511:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6273:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4512:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6377:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4513:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6378:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4514:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9059:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4515:./src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4516:./src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4517:./src/main/java/org/ecocean/Embedding.java:211:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4518:./src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4519:./src/main/java/org/ecocean/Embedding.java:261:        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4520:./src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4521:./src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4524:./src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4525:./src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4526:./src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4527:./src/main/resources/org/ecocean/package.jdo:1064:			<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4528:./src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4529:./src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4568:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4604:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4605:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4622:   999	   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4623:  1000	   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4639:  7181	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4640:  7182	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5403:/bin/bash -lc "rg -n \"skipAutoIndexing|new Annotation|setMatchAgainst|setAcmId|predictModelId|EMBEDDING\" src/main/java/org/ecocean/api/bulk/BulkImporter.java src/main/java/org/ecocean/api/BulkImport.java src/main/java/org/ecocean/servlet/importer -g '*.java'" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:5771:   271	                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6614:   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6647:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6664:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6665:src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6667:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6848:The schema-sensitive part now looks consistent with existing precedent: `ImportTask` already joins `ENCOUNTER_ANNOTATIONS → ANNOTATION_FEATURES → MEDIAASSET_FEATURES` using the same `ID_OID`/`ID_EID` shape, so the proposed eligibility join is not inventing a new mapping. I’m checking the WBIA side and the deferred queue edge cases before writing up findings.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8416:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:520:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:521:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:523:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:525:  AND a."MATCHAGAINST" = true
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:526:  AND a."ACMID" IS NOT NULL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:531:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:539:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:932:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:999:   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1000:   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4508:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6129:			<index name="MEDIAASSET_ACMID_INDEX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6464:			<index name="ANNOTATION_MATCHAGAINST_idx" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6522:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6523:			<column name="ANNOTATION_ID"/>
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6532:			<column name="ANNOTATION_ID" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6539:			<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6544:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6549:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6557:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6559:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6560:-- (ANNOTATION_ID, METHOD, METHODVERSION) is created by JDO in
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6567:SELECT "ANNOTATION_ID", "METHOD", "METHODVERSION", COUNT(*) AS dup_count
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6568:FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6569:GROUP BY "ANNOTATION_ID", "METHOD", "METHODVERSION"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6571:ORDER BY dup_count DESC, "ANNOTATION_ID";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6649:WHERE "ACMID" IS NOT NULL AND "WBIAREGISTERED" IS NULL;
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6668:/bin/bash -lc 'rg -n "MEDIAASSET_FEATURES|ANNOTATION_FEATURES|MEDIAASSET_ID_OID|FEATURE|FEATURES|EMBEDDING" archive src/main/resources src/main/java docs/plans | head -n 120' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6807:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6929:docs/plans/2026-05-18-empty-match-prospects-design.md:345:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6930:docs/plans/2026-05-18-empty-match-prospects-design.md:346:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6931:docs/plans/2026-05-18-empty-match-prospects-design.md:348:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6932:docs/plans/2026-05-18-empty-match-prospects-design.md:356:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6933:docs/plans/2026-05-18-empty-match-prospects-design.md:364:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6934:docs/plans/2026-05-18-empty-match-prospects-design.md:757:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6935:docs/plans/2026-05-18-empty-match-prospects-design.md:824:   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6936:docs/plans/2026-05-18-empty-match-prospects-design.md:825:   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6948:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6949:src/main/resources/org/ecocean/package.jdo:1064:			<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6950:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6951:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6952:archive/sql/ml_service_embedding_audit.sql:1:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6953:archive/sql/ml_service_embedding_audit.sql:3:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6954:archive/sql/ml_service_embedding_audit.sql:12:FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6955:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6956:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6957:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6959:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2955:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6960:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2956:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6961:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4392:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6963:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4896:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6964:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6965:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6966:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6967:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:426:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6968:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:427:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6969:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:429:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6970:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:437:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6971:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:445:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6972:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:812:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6973:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:859:   My draft uses `EMBEDDING.METHODVERSION = ?` strictly — Codex
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6974:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1007:docs/plans/2026-05-18-empty-match-prospects-design.md:637:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6977:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2588:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6978:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5541:/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|name=\\\"features\\\"|mapped-by=\\\"annotation\\\"|mapped-by=\\\"asset\\\"|class name=\\\"Annotation\\\"|class name=\\\"MediaAsset\\\"|class name=\\\"Embedding\\\"|ANNOTATION_ID|dependent-element\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/media/package.jdo src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6979:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5642:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6980:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6125:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6981:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6127:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6982:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6242:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6983:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6260:/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|MEDIAASSET_ID|ASSET_ID|FEATURES|ANNOTATION_ID" archive src/test src/main/java | head -200' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6986:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6268:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6988:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6270:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6989:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6271:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6990:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6272:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6991:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6273:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6994:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6377:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6995:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6378:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6997:docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9059:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7001:docs/plans/2026-05-09-ml-service-migration-v2.md:333:    SELECT "ANNOTATION_ID" FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7002:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7003:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7004:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7005:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:383:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7006:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:384:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7007:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:386:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7008:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:394:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7009:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:402:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7010:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:603:  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7011:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:652:   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7013:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2513:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7015:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3378:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7016:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3647:   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7017:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3762:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7018:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3766:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7019:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3767:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7020:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3797:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7021:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3814:  1064				<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7022:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3819:  1069				<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7023:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3824:  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7024:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4003:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7026:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5498:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7027:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5574:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7028:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5604:   211	        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7029:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5614:   221	            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7030:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9062:   208	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7031:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9063:   209	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7032:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9065:   211	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7033:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9073:   219	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7034:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9081:   227	  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7035:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9302:   428	  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7036:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9356:   477	   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7038:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7039:src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7040:src/main/java/org/ecocean/Embedding.java:211:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7041:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7042:src/main/java/org/ecocean/Embedding.java:261:        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7045:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7046:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7047:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7048:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7181:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7182:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8063:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8073:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8121:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8154:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:426:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:427:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:429:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:431:  AND a."MATCHAGAINST" = true
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:432:  AND a."ACMID" IS NOT NULL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:437:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:445:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:812:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:859:   My draft uses `EMBEDDING.METHODVERSION = ?` strictly — Codex
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1007:docs/plans/2026-05-18-empty-match-prospects-design.md:637:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2588:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5541:/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|name=\\\"features\\\"|mapped-by=\\\"annotation\\\"|mapped-by=\\\"asset\\\"|class name=\\\"Annotation\\\"|class name=\\\"MediaAsset\\\"|class name=\\\"Embedding\\\"|ANNOTATION_ID|dependent-element\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/media/package.jdo src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5642:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5964:    12				<index name="MEDIAASSET_ACMID_INDEX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6122:src/main/resources/org/ecocean/package.jdo:1048:			<column name="ANNOTATION_ID"/>
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6124:src/main/resources/org/ecocean/package.jdo:1057:			<column name="ANNOTATION_ID" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6125:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6127:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6184:   582				<index name="ANNOTATION_MATCHAGAINST_idx" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6242:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6243:  1048				<column name="ANNOTATION_ID"/>
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6252:  1057				<column name="ANNOTATION_ID" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6260:/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|MEDIAASSET_ID|ASSET_ID|FEATURES|ANNOTATION_ID" archive src/test src/main/java | head -200' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6262:archive/sql/ml_service_embedding_audit.sql:4:-- (ANNOTATION_ID, METHOD, METHODVERSION) is created by JDO in
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6263:archive/sql/ml_service_embedding_audit.sql:11:SELECT "ANNOTATION_ID", "METHOD", "METHODVERSION", COUNT(*) AS dup_count
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6264:archive/sql/ml_service_embedding_audit.sql:13:GROUP BY "ANNOTATION_ID", "METHOD", "METHODVERSION"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6265:archive/sql/ml_service_embedding_audit.sql:15:ORDER BY dup_count DESC, "ANNOTATION_ID";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6268:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6270:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6271:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6272:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6273:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6377:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6378:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6762:   271	                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9059:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:383:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:384:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:386:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:388:  AND a."MATCHAGAINST" = true
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:389:  AND a."ACMID" IS NOT NULL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:394:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:402:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:603:  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:652:   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2513:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3378:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3647:   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3762:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3766:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3767:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3797:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3798:  1048				<column name="ANNOTATION_ID"/>
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3807:  1057				<column name="ANNOTATION_ID" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3814:  1064				<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3819:  1069				<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3824:  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3854:   582				<index name="ANNOTATION_MATCHAGAINST_idx" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4003:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5490:src/main/java/org/ecocean/Annotation.java:271:                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5494:src/main/java/org/ecocean/identity/IBEISIA.java:3744:            "SELECT \"ANNOTATION\".\"ACMID\" as acmId, \"ENCOUNTER\".\"GENUS\" as genus, \"ENCOUNTER\".\"SPECIFICEPITHET\" as specificEpithet FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION\".\"ID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"ANNOTATION\".\"ACMID\" IS NOT NULL;";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5498:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5574:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5604:   211	        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5614:   221	            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7919:    12				<index name="MEDIAASSET_ACMID_INDEX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9062:   208	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9063:   209	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9065:   211	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9067:   213	  AND a."MATCHAGAINST" = true
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9068:   214	  AND a."ACMID" IS NOT NULL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9073:   219	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9081:   227	  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9302:   428	  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9356:   477	   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-18-c8-codex-review.md:68:`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
./docs/plans/2026-05-18-c8-codex-review.md:1875:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-c8-codex-review.md:2613:   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-c8-codex-review.md:2646:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-c7-codex-review.md:68:`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
./docs/plans/2026-05-18-c6-codex-review.md:68:`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
./docs/plans/2026-05-09-ml-service-migration-v2.md:238:WHERE "ACMID" IS NOT NULL AND "WBIAREGISTERED" IS NULL;
./docs/plans/2026-05-09-ml-service-migration-v2.md:330:WHERE a."MATCHAGAINST" = TRUE
./docs/plans/2026-05-09-ml-service-migration-v2.md:333:    SELECT "ANNOTATION_ID" FROM "EMBEDDING"
./docs/plans/2026-05-09-ml-service-migration-v2.md:346:JOIN "ANNOTATION" qa ON qa."ID" = mr."QUERYANNOTATION_ID_OID"
./docs/plans/2026-05-09-ml-service-migration-v2.md:355:WHERE mr."QUERYANNOTATION_ID_OID" = qa."ID"
./docs/plans/2026-05-18-c4-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-c4-codex-review.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-c4-codex-review.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-c4-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-c4-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-c3-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-c3-codex-review.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-c3-codex-review.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-c3-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-c3-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-c2-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-c2-codex-review.md:76:- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
./docs/plans/2026-05-18-c2-codex-review.md:78:- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
./docs/plans/2026-05-18-c2-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-c2-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-c2-codex-review.md:2578:2a3eab63a Let Wildbook own ACMID generation instead of relying on WBIA
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:37:        assertTrue(sql.contains("JOIN \"ANNOTATION_FEATURES\" af"),
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:38:            "missing ANNOTATION_FEATURES join: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:39:        assertTrue(sql.contains("JOIN \"MEDIAASSET_FEATURES\" mf"),
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:40:            "missing MEDIAASSET_FEATURES join: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:42:            "wrong ANNOTATION_FEATURES join condition: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:44:            "wrong MEDIAASSET_FEATURES join condition: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:50:        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:51:            "EMBEDDING join missing when method+version supplied: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:52:        assertTrue(sql.contains("e.\"ANNOTATION_ID\" = a.\"ID\""));
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:58:        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:59:            "EMBEDDING join missing when only method supplied: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:65:        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:66:            "EMBEDDING join missing when only version supplied: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:73:        assertFalse(sql.contains("JOIN \"EMBEDDING\""),
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:74:            "EMBEDDING join should be omitted when both null: " + sql);
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:82:        assertTrue(sql.contains("a.\"MATCHAGAINST\" = true"),
./src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:84:        assertTrue(sql.contains("a.\"ACMID\" IS NOT NULL"),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:37:        assertTrue(sql.contains("JOIN \"ANNOTATION_FEATURES\" af"),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:38:            "missing ANNOTATION_FEATURES join: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:39:        assertTrue(sql.contains("JOIN \"MEDIAASSET_FEATURES\" mf"),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:40:            "missing MEDIAASSET_FEATURES join: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:42:            "wrong ANNOTATION_FEATURES join condition: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:44:            "wrong MEDIAASSET_FEATURES join condition: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:50:        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:51:            "EMBEDDING join missing when method+version supplied: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:52:        assertTrue(sql.contains("e.\"ANNOTATION_ID\" = a.\"ID\""));
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:58:        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:59:            "EMBEDDING join missing when only method supplied: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:65:        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:66:            "EMBEDDING join missing when only version supplied: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:73:        assertFalse(sql.contains("JOIN \"EMBEDDING\""),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:74:            "EMBEDDING join should be omitted when both null: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:82:        assertTrue(sql.contains("a.\"MATCHAGAINST\" = true"),
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:84:        assertTrue(sql.contains("a.\"ACMID\" IS NOT NULL"),
./frontend/src/locale/it.json:376:  "ANNOTATION_ID": "ID Annotazione",
./frontend/src/locale/fr.json:376:  "ANNOTATION_ID": "ID d'annotation",
./frontend/src/locale/es.json:376:  "ANNOTATION_ID": "ID de anotación",
./frontend/src/locale/en.json:374:    "ANNOTATION_ID": "Annotation ID",
./frontend/src/locale/de.json:376:  "ANNOTATION_ID": "Annotation ID",
src/main/webapp/import.jsp:452:	    	ArrayList<MediaAsset> fixACMIDAssets=new ArrayList<MediaAsset>();
src/main/webapp/import.jsp:505:	                    	if(!fixACMIDAssets.contains(ma))fixACMIDAssets.add(ma);
src/main/webapp/import.jsp:513:                if(fixACMIDAssets.size()>0 && "complete".equals(itask.getStatus())){
src/main/webapp/import.jsp:517:                	//IBEISIA.sendMediaAssetsNew(fixACMIDAssets, context);
src/main/webapp/import.jsp:621:		int numWithACMID=0;
src/main/webapp/import.jsp:626:			if(asset.getAcmId()!=null)numWithACMID++;
src/main/webapp/import.jsp:645:			<li>Number with acmIDs: <%=numWithACMID %></li>
src/main/webapp/appadmin/testEmbeddingSearch.jsp:37:    out.println("<h2>unknown id <br /> pass ?id=ANNOTATION_ID on url</h2>");
src/main/webapp/appadmin/sharedAnnotations.jsp:210:	String sql = "SELECT \"ID\",\"ACMID\" FROM \"ANNOTATION\" WHERE \"ACMID\" IN (SELECT acmId FROM (SELECT \"ACMID\" AS acmId, COUNT(DISTINCT(\"INDIVIDUALID_OID\")) AS ct FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ANNOTATION\".\"ID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"CATALOGNUMBER_EID\") WHERE \"ACMID\" IS NOT NULL GROUP BY acmId) AS counts WHERE ct > 1) ORDER BY \"ACMID\", \"ID\";";
src/main/webapp/acmIdSync.jsp:24:	    String sql = "SELECT DISTINCT(\"ACMID\") AS acmId FROM \"MEDIAASSET\" WHERE \"ACMID\" IS NOT NULL ORDER BY acmId; ";
src/main/webapp/acmIdSync.jsp:45:			"SELECT \"ANNOTATION\".\"ACMID\" as annotAcmId, " +
src/main/webapp/acmIdSync.jsp:49:	        "\"ANNOTATION\".\"MATCHAGAINST\" as annotMatchAgainst, " +
src/main/webapp/acmIdSync.jsp:52:	        "join \"ANNOTATION_FEATURES\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ANNOTATION\".\"ID\") " + 
src/main/webapp/acmIdSync.jsp:53:	        "join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") " +
src/main/webapp/acmIdSync.jsp:55:	        " where \"ANNOTATION\".\"ACMID\" is not null " +
src/main/webapp/acmIdSync.jsp:56:	        " order by \"ACMID\" ;";
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:54:public Long countACMIDIAClassInstances(String genus, String specificEpithet, String iaClass, Shepherd myShepherd){
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:110:public HashMap<String, Long> getMediaAssetDetectionStatusesForSpeciesByACMID(String genus, String specificEpithet, Shepherd myShepherd){
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:192:						<li>Have acmID: <%=countACMIDIAClassInstances(genus, specificEpithet, iaClass, myShepherd) %></li>
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:204:		HashMap<String, Long> mapACMID=getMediaAssetDetectionStatusesForSpeciesByACMID(genus, specificEpithet, myShepherd);
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:213:				<ul><li>Have acmID: <%=mapACMID.get(key) %></li></ul>
src/main/webapp/encounters/encounterMediaGallery.jsp:303:        String sql = "select \"MEDIAASSET\".\"ID\" as assetId, \"MEDIAASSET\".\"ACMID\" as assetAcmId, \"ENCOUNTER\".\"CATALOGNUMBER\" as encId, \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"INDIVIDUALID_OID\" as indivId from \"MEDIAASSET\" join \"MEDIAASSET_FEATURES\" on (\"ID\" = \"ID_OID\") join \"ANNOTATION_FEATURES\" using (\"ID_EID\") join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") join \"ENCOUNTER\" on (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") left join \"MARKEDINDIVIDUAL_ENCOUNTERS\" on (\"ENCOUNTER\".\"CATALOGNUMBER\" = \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"CATALOGNUMBER_EID\") where \"MEDIAASSET\".\"ACMID\" in ('" + String.join("', '", maAcms) + "') AND \"MEDIAASSET\".\"ID\" not in (" + String.join(", ", maIds) + ");";
src/main/webapp/iaResults.jsp:828:		var matchAgainstACMIDs="";
src/main/webapp/iaResults.jsp:833:			if(i==0){matchAgainstACMIDs=acmId;}
src/main/webapp/iaResults.jsp:834:			else{matchAgainstACMIDs=matchAgainstACMIDs+","+acmId;}
src/main/webapp/iaResults.jsp:837:		matchAgainstACMIDs=matchAgainstACMIDs+","+qannotId;
src/main/webapp/iaResults.jsp:839:		let paramString = 'iaResultsAnnotFeed.jsp?acmId=' + matchAgainstACMIDs;
./src/main/webapp/appadmin/testEmbeddingSearch.jsp:37:    out.println("<h2>unknown id <br /> pass ?id=ANNOTATION_ID on url</h2>");
./src/main/webapp/appadmin/sharedAnnotations.jsp:210:	String sql = "SELECT \"ID\",\"ACMID\" FROM \"ANNOTATION\" WHERE \"ACMID\" IN (SELECT acmId FROM (SELECT \"ACMID\" AS acmId, COUNT(DISTINCT(\"INDIVIDUALID_OID\")) AS ct FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ANNOTATION\".\"ID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"CATALOGNUMBER_EID\") WHERE \"ACMID\" IS NOT NULL GROUP BY acmId) AS counts WHERE ct > 1) ORDER BY \"ACMID\", \"ID\";";
src/main/resources/org/ecocean/package.jdo:582:			<index name="ANNOTATION_MATCHAGAINST_idx" />
src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
src/main/resources/org/ecocean/package.jdo:1048:			<column name="ANNOTATION_ID"/>
src/main/resources/org/ecocean/package.jdo:1057:			<column name="ANNOTATION_ID" />
src/main/resources/org/ecocean/package.jdo:1064:			<index name="EMBEDDING_CREATED_IDX" />
src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
src/main/resources/org/ecocean/media/package.jdo:12:			<index name="MEDIAASSET_ACMID_INDEX" />
./src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:54:public Long countACMIDIAClassInstances(String genus, String specificEpithet, String iaClass, Shepherd myShepherd){
./src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:110:public HashMap<String, Long> getMediaAssetDetectionStatusesForSpeciesByACMID(String genus, String specificEpithet, Shepherd myShepherd){
./src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:192:						<li>Have acmID: <%=countACMIDIAClassInstances(genus, specificEpithet, iaClass, myShepherd) %></li>
./src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:204:		HashMap<String, Long> mapACMID=getMediaAssetDetectionStatusesForSpeciesByACMID(genus, specificEpithet, myShepherd);
./src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:213:				<ul><li>Have acmID: <%=mapACMID.get(key) %></li></ul>
src/main/java/org/ecocean/identity/IBEISIA.java:3744:            "SELECT \"ANNOTATION\".\"ACMID\" as acmId, \"ENCOUNTER\".\"GENUS\" as genus, \"ENCOUNTER\".\"SPECIFICEPITHET\" as specificEpithet FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION\".\"ID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"ANNOTATION\".\"ACMID\" IS NOT NULL;";
./src/main/webapp/acmIdSync.jsp:24:	    String sql = "SELECT DISTINCT(\"ACMID\") AS acmId FROM \"MEDIAASSET\" WHERE \"ACMID\" IS NOT NULL ORDER BY acmId; ";
./src/main/webapp/acmIdSync.jsp:45:			"SELECT \"ANNOTATION\".\"ACMID\" as annotAcmId, " +
./src/main/webapp/acmIdSync.jsp:49:	        "\"ANNOTATION\".\"MATCHAGAINST\" as annotMatchAgainst, " +
./src/main/webapp/acmIdSync.jsp:52:	        "join \"ANNOTATION_FEATURES\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ANNOTATION\".\"ID\") " + 
./src/main/webapp/acmIdSync.jsp:53:	        "join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") " +
./src/main/webapp/acmIdSync.jsp:55:	        " where \"ANNOTATION\".\"ACMID\" is not null " +
./src/main/webapp/acmIdSync.jsp:56:	        " order by \"ACMID\" ;";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:18: * the matchConfig provides them) have an EMBEDDING row for the
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:24: * The 4-table join (ANNOTATION + ANNOTATION_FEATURES +
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:25: * MEDIAASSET_FEATURES + EMBEDDING) matches the same shape used at
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:32: * the value is non-null. Both null skips the EMBEDDING join
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:92:        sql.append("JOIN \"ANNOTATION_FEATURES\" af ON af.\"ID_OID\" = a.\"ID\" ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:93:        sql.append("JOIN \"MEDIAASSET_FEATURES\" mf ON mf.\"ID_EID\" = af.\"ID_EID\" ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:94:        // EMBEDDING join: required only when at least one of
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:100:            sql.append("JOIN \"EMBEDDING\" e ON e.\"ANNOTATION_ID\" = a.\"ID\" ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:103:        sql.append("AND a.\"MATCHAGAINST\" = true ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:104:        sql.append("AND a.\"ACMID\" IS NOT NULL ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:180:        sql.append("JOIN \"ANNOTATION_FEATURES\" af ON af.\"ID_OID\" = a.\"ID\" ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:181:        sql.append("JOIN \"MEDIAASSET_FEATURES\" mf ON mf.\"ID_EID\" = af.\"ID_EID\" ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:184:            sql.append("JOIN \"EMBEDDING\" e ON e.\"ANNOTATION_ID\" = a.\"ID\" ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:187:        sql.append("AND a.\"MATCHAGAINST\" = true ");
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:188:        sql.append("AND a.\"ACMID\" IS NOT NULL ");
./src/main/resources/org/ecocean/package.jdo:582:			<index name="ANNOTATION_MATCHAGAINST_idx" />
./src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./src/main/resources/org/ecocean/package.jdo:1048:			<column name="ANNOTATION_ID"/>
./src/main/resources/org/ecocean/package.jdo:1057:			<column name="ANNOTATION_ID" />
./src/main/resources/org/ecocean/package.jdo:1064:			<index name="EMBEDDING_CREATED_IDX" />
./src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
./src/main/resources/org/ecocean/media/package.jdo:12:			<index name="MEDIAASSET_ACMID_INDEX" />
./src/main/java/org/ecocean/identity/IBEISIA.java:3744:            "SELECT \"ANNOTATION\".\"ACMID\" as acmId, \"ENCOUNTER\".\"GENUS\" as genus, \"ENCOUNTER\".\"SPECIFICEPITHET\" as specificEpithet FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION\".\"ID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"ANNOTATION\".\"ACMID\" IS NOT NULL;";
./src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:18: * the matchConfig provides them) have an EMBEDDING row for the
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:24: * The 4-table join (ANNOTATION + ANNOTATION_FEATURES +
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:25: * MEDIAASSET_FEATURES + EMBEDDING) matches the same shape used at
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:32: * the value is non-null. Both null skips the EMBEDDING join
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:92:        sql.append("JOIN \"ANNOTATION_FEATURES\" af ON af.\"ID_OID\" = a.\"ID\" ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:93:        sql.append("JOIN \"MEDIAASSET_FEATURES\" mf ON mf.\"ID_EID\" = af.\"ID_EID\" ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:94:        // EMBEDDING join: required only when at least one of
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:100:            sql.append("JOIN \"EMBEDDING\" e ON e.\"ANNOTATION_ID\" = a.\"ID\" ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:103:        sql.append("AND a.\"MATCHAGAINST\" = true ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:104:        sql.append("AND a.\"ACMID\" IS NOT NULL ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:180:        sql.append("JOIN \"ANNOTATION_FEATURES\" af ON af.\"ID_OID\" = a.\"ID\" ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:181:        sql.append("JOIN \"MEDIAASSET_FEATURES\" mf ON mf.\"ID_EID\" = af.\"ID_EID\" ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:184:            sql.append("JOIN \"EMBEDDING\" e ON e.\"ANNOTATION_ID\" = a.\"ID\" ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:187:        sql.append("AND a.\"MATCHAGAINST\" = true ");
./src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:188:        sql.append("AND a.\"ACMID\" IS NOT NULL ");
src/main/java/org/ecocean/Annotation.java:271:                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
src/main/java/org/ecocean/Embedding.java:211:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
src/main/java/org/ecocean/Embedding.java:261:        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
src/main/java/org/ecocean/servlet/importer/StandardImport.java:443:                if (itask != null) sendforACMID(itask, myShepherd, context);
src/main/java/org/ecocean/servlet/importer/StandardImport.java:2650:    private void sendforACMID(ImportTask itask, Shepherd myShepherd, String context) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./src/main/java/org/ecocean/Annotation.java:271:                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
./src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./src/main/java/org/ecocean/Embedding.java:211:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./src/main/java/org/ecocean/Embedding.java:261:        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
src/main/java/org/ecocean/Project.java:375:        List<String> allACMIDsStr = new ArrayList<String>();
src/main/java/org/ecocean/Project.java:382:                        !allACMIDsStr.contains(ann.getAcmId())) {
src/main/java/org/ecocean/Project.java:383:                        allACMIDsStr.add(ann.getAcmId());
./src/main/java/org/ecocean/servlet/importer/StandardImport.java:443:                if (itask != null) sendforACMID(itask, myShepherd, context);
./src/main/java/org/ecocean/servlet/importer/StandardImport.java:2650:    private void sendforACMID(ImportTask itask, Shepherd myShepherd, String context) {
./src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./src/main/java/org/ecocean/Project.java:375:        List<String> allACMIDsStr = new ArrayList<String>();
./src/main/java/org/ecocean/Project.java:382:                        !allACMIDsStr.contains(ann.getAcmId())) {
./src/main/java/org/ecocean/Project.java:383:                        allACMIDsStr.add(ann.getAcmId());

codex
The first pass mostly found the design docs and schema references, so I’m narrowing to the actual Java files now. I’m also checking the committed diff names without allowing Git’s optional index locking.
exec
/bin/bash -lc "rg --files src/main/java src/test/java | rg 'MatchEligibilityQuery|Annotation.java|Embedding.java|ImportTask.java|package.jdo|MediaAsset.java|Feature.java'" in /mnt/c/Wildbook-clean2
 succeeded in 319ms:
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java
src/main/java/org/ecocean/Embedding.java
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
src/main/java/org/ecocean/Annotation.java
src/main/java/org/ecocean/media/Feature.java
src/main/java/org/ecocean/media/MediaAsset.java
src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
src/main/java/org/ecocean/servlet/importer/ImportTask.java

 succeeded in 14451ms:
.github/workflows/claude-code-review.yml
.github/workflows/claude.yml
.gitignore
archive/copyBuild.sh
archive/imageTransformWrapper.sh
archive/sql/ml_service_embedding_audit.sql
archive/sql/ml_service_idempotency.sql
config/git-json-info.ps1
config/git-json-info.sh
devops/deploy/.dockerfiles/alloy/config.alloy
devops/deploy/.dockerfiles/alloy/config.tempo.alloy
devops/deploy/.dockerfiles/db/postgresql.conf
devops/deploy/.dockerfiles/nginx/nginx-https.conf
devops/deploy/.dockerfiles/nginx/nginx.conf
devops/deploy/.dockerfiles/opensearch/log4j2.properties
devops/deploy/.dockerfiles/tomcat/server.xml
devops/deploy/_env.template
devops/deploy/docker-compose.yml
devops/development/.dockerfiles/tomcat/server.xml
devops/development/docker-compose.yml
docs/plans/2026-05-09-ml-service-migration-v2.md
docs/plans/2026-05-18-wbia-image-registration-design.md
frontend/maven-build.sh
frontend/package-lock.json
frontend/package.json
frontend/src/App.jsx
frontend/src/AuthenticatedSwitch.jsx
frontend/src/FrontDesk.jsx
frontend/src/SiteSettingsContext.jsx
frontend/src/UnAuthenticatedSwitch.jsx
frontend/src/__tests__/FrontDesk.test.js
frontend/src/__tests__/components/AddAdditionalModal.test.js
frontend/src/__tests__/components/AuthenticatedSwitch.test.js
frontend/src/__tests__/components/Map.test.js
frontend/src/__tests__/components/SearchAndSelectInput.test.js
frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
frontend/src/__tests__/pages/Encounter/Encounter.test.js
frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
frontend/src/__tests__/pages/Encounter/ImageCard.test.js
frontend/src/__tests__/pages/Encounter/ImageModal.test.js
frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
frontend/src/__tests__/pages/PoliciesAndData.test.js
frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
frontend/src/__tests__/pages/login/LoginPageError.test.js
frontend/src/__tests__/pages/login/LoginPageInput.test.js
frontend/src/__tests__/pages/login/LoginPageLinks.test.js
frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
frontend/src/__tests__/pages/login/LoginPageRender.test.js
frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
frontend/src/components/AnnotationOverlay.jsx
frontend/src/components/AuthenticatedAppHeader.jsx
frontend/src/components/Chip.jsx
frontend/src/components/ContainerWithSpinner.jsx
frontend/src/components/DataTable.jsx
frontend/src/components/FilterPanel.jsx
frontend/src/components/Footer.jsx
frontend/src/components/Form/FormGroupMultiSelect.jsx
frontend/src/components/ImageModal.jsx
frontend/src/components/LoadingScreen.jsx
frontend/src/components/Map.jsx
frontend/src/components/MultiSelectWithCheckbox.jsx
frontend/src/components/SimpleDataTable.jsx
frontend/src/components/SmallSpinner.jsx
frontend/src/components/UnAuthenticatedAppHeader.jsx
frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
frontend/src/components/filterFields/DateFilter.jsx
frontend/src/components/filterFields/ImageLabelFilter.jsx
frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
frontend/src/components/filterFields/LocationFilterMap.jsx
frontend/src/components/filterFields/LocationFilterText.jsx
frontend/src/components/filterFields/MetadataFilter.jsx
frontend/src/components/filterFields/ObservationAttributeFilter.jsx
frontend/src/components/filterFields/SocialFilter.jsx
frontend/src/components/generalInputs/CoordinatesInput.jsx
frontend/src/components/header/HeaderDropdownItems.jsx
frontend/src/components/header/Menu.jsx
frontend/src/components/home/PickUpWhereYouLeft.jsx
frontend/src/components/icons/EditIcon.jsx
frontend/src/components/icons/EncounterIcon.jsx
frontend/src/components/icons/ExitIcon.jsx
frontend/src/components/icons/FullscreenIcon.jsx
frontend/src/components/icons/SpotMappingIcon.jsx
frontend/src/components/icons/SpotMappingIcon2.jsx
frontend/src/constants/navMenu.js
frontend/src/hooks/useDocumentTitle.js
frontend/src/locale/de.json
frontend/src/locale/en.json
frontend/src/locale/es.json
frontend/src/locale/fr.json
frontend/src/locale/it.json
frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
frontend/src/pages/AboutUs.jsx
frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
frontend/src/pages/BulkImport/BulkImportStore.js
frontend/src/pages/BulkImport/BulkImportTask.jsx
frontend/src/pages/BulkImport/EditableDataTable.jsx
frontend/src/pages/Citation.jsx
frontend/src/pages/EditAnnotation.jsx
frontend/src/pages/Encounter/AddPeople.jsx
frontend/src/pages/Encounter/AttributesSectionEdit.jsx
frontend/src/pages/Encounter/ContactInfoCard.jsx
frontend/src/pages/Encounter/ContactInfoModal.jsx
frontend/src/pages/Encounter/Encounter.jsx
frontend/src/pages/Encounter/IdentifySectionEdit.jsx
frontend/src/pages/Encounter/IdentifySectionReview.jsx
frontend/src/pages/Encounter/ImageCard.jsx
frontend/src/pages/Encounter/LocationSectionEdit.jsx
frontend/src/pages/Encounter/MapDisplay.jsx
frontend/src/pages/Encounter/MatchCriteria.jsx
frontend/src/pages/Encounter/MetadataSectionEdit.jsx
frontend/src/pages/Encounter/MoreDetails.jsx
frontend/src/pages/Encounter/ProjectsCard.jsx
frontend/src/pages/Encounter/SpotMappingCard.jsx
frontend/src/pages/Encounter/constants.js
frontend/src/pages/Encounter/pollingHelpers.js
frontend/src/pages/Encounter/stores/EncounterStore.js
frontend/src/pages/Encounter/stores/NewMatchStore.js
frontend/src/pages/Encounter/stores/helperFunctions.js
frontend/src/pages/HowToPhotograph.jsx
frontend/src/pages/Login.jsx
frontend/src/pages/ManualAnnotation.jsx
frontend/src/pages/MatchResultsPage/MatchResults.jsx
frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
frontend/src/pages/MatchResultsPage/constants.js
frontend/src/pages/MatchResultsPage/helperFunctions.js
frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
frontend/src/pages/SearchPages/EncounterSearch.jsx
frontend/src/pages/SearchPages/components/ExportModal.jsx
frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
frontend/src/pages/SearchPages/stores/EncounterFormStore.js
frontend/src/pages/SearchPages/stores/ImageModalStore.js
frontend/src/service-worker.js
pom.xml
src/main/java/org/ecocean/Annotation.java
src/main/java/org/ecocean/AnnotationLite.java
src/main/java/org/ecocean/Base.java
src/main/java/org/ecocean/CommonConfiguration.java
src/main/java/org/ecocean/EmailTemplate.java
src/main/java/org/ecocean/Embedding.java
src/main/java/org/ecocean/Encounter.java
src/main/java/org/ecocean/IAJsonProperties.java
src/main/java/org/ecocean/ImageProcessor.java
src/main/java/org/ecocean/LocationID.java
src/main/java/org/ecocean/MarkedIndividual.java
src/main/java/org/ecocean/NotificationMailer.java
src/main/java/org/ecocean/Occurrence.java
src/main/java/org/ecocean/RestClient.java
src/main/java/org/ecocean/SpotterConserveIO.java
src/main/java/org/ecocean/Survey.java
src/main/java/org/ecocean/User.java
src/main/java/org/ecocean/Util.java
src/main/java/org/ecocean/acm/AcmUtil.java
src/main/java/org/ecocean/api/GenericObject.java
src/main/java/org/ecocean/api/Login.java
src/main/java/org/ecocean/api/Logout.java
src/main/java/org/ecocean/api/MarkedIndividualInfo.java
src/main/java/org/ecocean/api/SiteSettings.java
src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
src/main/java/org/ecocean/grid/EncounterLite.java
src/main/java/org/ecocean/grid/GridManager.java
src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
src/main/java/org/ecocean/grid/MatchedPoints.java
src/main/java/org/ecocean/grid/SpotTriangle.java
src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
src/main/java/org/ecocean/ia/IA.java
src/main/java/org/ecocean/ia/IAException.java
src/main/java/org/ecocean/ia/MLService.java
src/main/java/org/ecocean/ia/MatchResult.java
src/main/java/org/ecocean/ia/MatchResultProspect.java
src/main/java/org/ecocean/ia/MlServiceClient.java
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
src/main/java/org/ecocean/ia/MlServiceProcessor.java
src/main/java/org/ecocean/identity/IBEISIA.java
src/main/java/org/ecocean/identity/IdentityServiceLog.java
src/main/java/org/ecocean/media/AssetStore.java
src/main/java/org/ecocean/media/AssetStoreConfig.java
src/main/java/org/ecocean/media/AssetStoreFactory.java
src/main/java/org/ecocean/media/Feature.java
src/main/java/org/ecocean/media/LocalAssetStore.java
src/main/java/org/ecocean/media/MediaAsset.java
src/main/java/org/ecocean/mmutil/FileUtilities.java
src/main/java/org/ecocean/mmutil/MediaUtilities.java
src/main/java/org/ecocean/movement/Path.java
src/main/java/org/ecocean/opendata/OBISSeamap.java
src/main/java/org/ecocean/opendata/Share.java
src/main/java/org/ecocean/resumableupload/UploadServlet.java
src/main/java/org/ecocean/security/ShepherdRealm.java
src/main/java/org/ecocean/servlet/AnnotationEdit.java
src/main/java/org/ecocean/servlet/EncounterDelete.java
src/main/java/org/ecocean/servlet/EncounterForm.java
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
src/main/java/org/ecocean/servlet/GrothMatchServlet.java
src/main/java/org/ecocean/servlet/IAGateway.java
src/main/java/org/ecocean/servlet/IAGlobals.java
src/main/java/org/ecocean/servlet/MediaAssetModify.java
src/main/java/org/ecocean/servlet/SiteSearch.java
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
src/main/java/org/ecocean/servlet/UserCreate.java
src/main/java/org/ecocean/servlet/UserResetPassword.java
src/main/java/org/ecocean/servlet/WorkspaceDelete.java
src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
src/main/java/org/ecocean/servlet/importer/ImportTask.java
src/main/java/org/ecocean/servlet/importer/StandardImport.java
src/main/java/org/ecocean/shepherd/core/Shepherd.java
src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
src/main/resources/bundles/IA.json
src/main/resources/bundles/IA.properties
src/main/resources/bundles/commonConfiguration.properties
src/main/resources/bundles/de/commonConfigurationLabels.properties
src/main/resources/bundles/de/header.properties
src/main/resources/bundles/en/commonConfigurationLabels.properties
src/main/resources/bundles/en/header.properties
src/main/resources/bundles/en/index.properties
src/main/resources/bundles/en/occurrence.properties
src/main/resources/bundles/en/survey.properties
src/main/resources/bundles/es/commonConfigurationLabels.properties
src/main/resources/bundles/es/header.properties
src/main/resources/bundles/es/index.properties
src/main/resources/bundles/fr/commonConfigurationLabels.properties
src/main/resources/bundles/fr/encounter.properties
src/main/resources/bundles/fr/encounterSearch.properties
src/main/resources/bundles/fr/header.properties
src/main/resources/bundles/fr/index.properties
src/main/resources/bundles/haplotypeColorCodes.properties
src/main/resources/bundles/it/commonConfigurationLabels.properties
src/main/resources/bundles/it/header.properties
src/main/resources/bundles/it/index.properties
src/main/resources/bundles/locationID.json
src/main/resources/bundles/locationIDGPS.properties
src/main/resources/bundles/locationID_indocet.json
src/main/resources/bundles/newIndividualNumbers.properties
src/main/resources/bundles/opendata.properties
src/main/resources/log4j2.xml
src/main/resources/org/ecocean/ia/package.jdo
src/main/resources/org/ecocean/media/package.jdo
src/main/resources/org/ecocean/package.jdo
src/main/resources/servletResponseTemplate.htm
src/main/webapp/WEB-INF/web.xml
src/main/webapp/appadmin/catchUpEmbeddings.jsp
src/main/webapp/appadmin/importEmbeddings.jsp
src/main/webapp/appadmin/testEmbeddingSearch.jsp
src/main/webapp/contactus.jsp
src/main/webapp/css/imageEnhancer.css
src/main/webapp/cust/mantamatcher/styles/_wildbook.less
src/main/webapp/datacleaning/individuals.jsp
src/main/webapp/encounters/biologicalSamples.jsp
src/main/webapp/encounters/encounterCropTool.jsp
src/main/webapp/encounters/encounterSpotTool.jsp
src/main/webapp/encounters/manualAnnotation.jsp
src/main/webapp/encounters/scanEndApplet.jsp
src/main/webapp/encounters/spotMatchingAlgorithm.jsp
src/main/webapp/header.jsp
src/main/webapp/iaResults.jsp
src/main/webapp/import.jsp
src/main/webapp/import/photos.jsp
src/main/webapp/import/reviewDirectory.jsp
src/main/webapp/import/spreadsheet.jsp
src/main/webapp/index.jsp
src/main/webapp/javascript/ia.IBEIS.js
src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
src/main/webapp/listImages.jsp
src/main/webapp/logout.jsp
src/main/webapp/obrowse.jsp
src/main/webapp/occurrence.jsp
src/main/webapp/pictureBook.jsp
src/main/webapp/projects/project.jsp
src/main/webapp/submit.jsp
src/main/webapp/surveys/survey.jsp
src/main/webapp/surveys/surveyMapEmbed.jsp
src/main/webapp/welcome.jsp
src/test/java/org/ecocean/AnnotationTest.java
src/test/java/org/ecocean/EmbeddingTest.java
src/test/java/org/ecocean/IAJsonPropertiesTest.java
src/test/java/org/ecocean/MatchResultTest.java
src/test/java/org/ecocean/OpenSearchVisibilityTest.java
src/test/java/org/ecocean/UtilTest.java
src/test/java/org/ecocean/api/EncounterApiTest.java
src/test/java/org/ecocean/api/EncounterExportImagesTest.java
src/test/java/org/ecocean/api/SettingApiTest.java
src/test/java/org/ecocean/api/TaskMatchResults.java
src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
src/test/java/org/ecocean/grid/GrothOptimizationTest.java
src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
src/test/java/org/ecocean/ia/IdentificationTest.java
src/test/java/org/ecocean/ia/MlServiceClientTest.java
src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
src/test/resources/log4j2-test.xml

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchEligibilityQuery.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '760,805p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "getMatchQuery|METHODVERSION|METHOD" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Embedding.java' in /mnt/c/Wildbook-clean2
 succeeded in 220ms:
     1	package org.ecocean.ia;
     2	
     3	import java.util.ArrayList;
     4	import java.util.Collection;
     5	import java.util.LinkedHashSet;
     6	import java.util.List;
     7	import java.util.Set;
     8	
     9	import javax.jdo.Query;
    10	
    11	import org.ecocean.shepherd.core.Shepherd;
    12	
    13	/**
    14	 * Direct-SQL utility for the empty-match-prospects batch gate.
    15	 * Returns the set of annotation IDs reachable from a collection of
    16	 * media-asset IDs that pass ml-service match eligibility:
    17	 * {@code matchAgainst=true}, {@code acmId IS NOT NULL}, and (when
    18	 * the matchConfig provides them) have an EMBEDDING row for the
    19	 * configured method/methodVersion.
    20	 *
    21	 * <p>Used by {@link MatchVisibilityGate} (Track 2 C10) to compute
    22	 * the wait set Phase 2 hands to
    23	 * {@link org.ecocean.OpenSearch#waitForAnnotationMatchableIds}.
    24	 * The 4-table join (ANNOTATION + ANNOTATION_FEATURES +
    25	 * MEDIAASSET_FEATURES + EMBEDDING) matches the same shape used at
    26	 * {@code ImportTask.java:781} for repo precedent; direct SQL is
    27	 * preferred over JDOQL here because the join goes through two
    28	 * JDO-generated join tables plus a custom FK table.</p>
    29	 *
    30	 * <p>method/methodVersion match Annotation.getMatchQuery semantics
    31	 * at Annotation.java:1205-1209: each predicate is added only when
    32	 * the value is non-null. Both null skips the EMBEDDING join
    33	 * entirely (legacy api_endpoint-only configs that can't derive a
    34	 * method).</p>
    35	 *
    36	 * <p>(Empty-match-prospects design Track 2 C9.)</p>
    37	 */
    38	public final class MatchEligibilityQuery {
    39	
    40	    private MatchEligibilityQuery() { }
    41	
    42	    /**
    43	     * Resolve the set of match-eligible annotation IDs whose media
    44	     * asset is in {@code siblingMaIds}. The returned set preserves
    45	     * insertion order from the underlying query (which uses
    46	     * {@code SELECT DISTINCT}, so ordering is engine-dependent but
    47	     * stable within a call).
    48	     *
    49	     * @param shep           open Shepherd. Caller manages the
    50	     *                       transaction lifecycle; this method
    51	     *                       neither begins nor commits.
    52	     * @param siblingMaIds   integer MediaAsset IDs from
    53	     *                       {@code topTask.getObjectMediaAssets()}.
    54	     *                       An empty or null collection yields an
    55	     *                       empty result without hitting the DB.
    56	     * @param method         embedding method (e.g. "miewid-msv4.1");
    57	     *                       null skips the predicate.
    58	     * @param methodVersion  embedding method version (e.g. "4.1");
    59	     *                       null skips the predicate.
    60	     * @return distinct annotation IDs, never null.
    61	     */
    62	    public static Set<String> findEligibleAnnotationIds(
    63	        Shepherd shep,
    64	        Collection<Integer> siblingMaIds,
    65	        String method,
    66	        String methodVersion) {
    67	        Set<String> ids = new LinkedHashSet<String>();
    68	        if (siblingMaIds == null || siblingMaIds.isEmpty()) return ids;
    69	
    70	        // Build the IN-list inline — JDO SQL parameterization for IN-lists
    71	        // is uneven across DataNucleus versions; the existing repo pattern
    72	        // at ImportTask.java:496 quotes string IDs inline. MediaAsset IDs
    73	        // are integers so no quoting is needed, but we do a defensive
    74	        // numeric-only check below so a malformed call site can't sneak
    75	        // SQL through.
    76	        StringBuilder inList = new StringBuilder();
    77	        int n = 0;
    78	        for (Integer id : siblingMaIds) {
    79	            if (id == null) continue;
    80	            // Defense-in-depth: Integer.toString of a primitive int is
    81	            // always numeric, but the explicit intValue avoids any
    82	            // String-based subclass shenanigans.
    83	            if (n > 0) inList.append(",");
    84	            inList.append(id.intValue());
    85	            n++;
    86	        }
    87	        if (n == 0) return ids;
    88	
    89	        StringBuilder sql = new StringBuilder();
    90	        sql.append("SELECT DISTINCT a.\"ID\" ");
    91	        sql.append("FROM \"ANNOTATION\" a ");
    92	        sql.append("JOIN \"ANNOTATION_FEATURES\" af ON af.\"ID_OID\" = a.\"ID\" ");
    93	        sql.append("JOIN \"MEDIAASSET_FEATURES\" mf ON mf.\"ID_EID\" = af.\"ID_EID\" ");
    94	        // EMBEDDING join: required only when at least one of
    95	        // method/methodVersion is supplied. Otherwise the gate just
    96	        // needs annotations reachable from the sibling MAs that pass
    97	        // the matchAgainst + acmId filters.
    98	        boolean joinEmbedding = method != null || methodVersion != null;
    99	        if (joinEmbedding) {
   100	            sql.append("JOIN \"EMBEDDING\" e ON e.\"ANNOTATION_ID\" = a.\"ID\" ");
   101	        }
   102	        sql.append("WHERE mf.\"ID_OID\" IN (").append(inList).append(") ");
   103	        sql.append("AND a.\"MATCHAGAINST\" = true ");
   104	        sql.append("AND a.\"ACMID\" IS NOT NULL ");
   105	        if (method != null) {
   106	            sql.append("AND e.\"METHOD\" = '").append(sqlEscape(method)).append("' ");
   107	        }
   108	        if (methodVersion != null) {
   109	            sql.append("AND e.\"METHODVERSION\" = '")
   110	                .append(sqlEscape(methodVersion)).append("' ");
   111	        }
   112	
   113	        Query q = null;
   114	        try {
   115	            q = shep.getPM().newQuery("javax.jdo.query.SQL", sql.toString());
   116	            @SuppressWarnings("rawtypes")
   117	            List rows = (List) q.execute();
   118	            if (rows != null) {
   119	                for (Object row : rows) {
   120	                    // SELECT-of-one returns the scalar directly, not an
   121	                    // Object[]. Defensive cast on both shapes.
   122	                    String annId = scalarOrFirstColumn(row);
   123	                    if (annId != null) ids.add(annId);
   124	                }
   125	            }
   126	        } catch (Exception ex) {
   127	            System.out.println(
   128	                "[ERROR] MatchEligibilityQuery.findEligibleAnnotationIds() failed: " + ex);
   129	            ex.printStackTrace();
   130	        } finally {
   131	            if (q != null) q.closeAll();
   132	        }
   133	        return ids;
   134	    }
   135	
   136	    /**
   137	     * Some JDO query backends return single-column rows as raw
   138	     * scalars, others wrap them in a 1-element {@code Object[]}.
   139	     * Handle both shapes defensively.
   140	     */
   141	    private static String scalarOrFirstColumn(Object row) {
   142	        if (row == null) return null;
   143	        if (row instanceof String) return (String) row;
   144	        if (row instanceof Object[]) {
   145	            Object[] arr = (Object[]) row;
   146	            if (arr.length == 0) return null;
   147	            Object v = arr[0];
   148	            return (v == null) ? null : v.toString();
   149	        }
   150	        return row.toString();
   151	    }
   152	
   153	    /**
   154	     * Escape a SQL string literal for inline interpolation. Method
   155	     * and methodVersion come from admin-controlled IA.json, so SQL
   156	     * injection isn't a typical risk, but this lets the helper hold
   157	     * up if a future call site sources these from less-trusted input.
   158	     */
   159	    private static String sqlEscape(String s) {
   160	        return s.replace("'", "''");
   161	    }
   162	
   163	    // Test-only entry point for the SQL builder. Returns the
   164	    // generated SQL as a String without executing it, so unit tests
   165	    // can assert structure without a real database. Package-visible.
   166	    static String buildSql(Collection<Integer> siblingMaIds, String method,
   167	        String methodVersion) {
   168	        if (siblingMaIds == null) siblingMaIds = new ArrayList<Integer>();
   169	        StringBuilder inList = new StringBuilder();
   170	        int n = 0;
   171	        for (Integer id : siblingMaIds) {
   172	            if (id == null) continue;
   173	            if (n > 0) inList.append(",");
   174	            inList.append(id.intValue());
   175	            n++;
   176	        }
   177	        StringBuilder sql = new StringBuilder();
   178	        sql.append("SELECT DISTINCT a.\"ID\" ");
   179	        sql.append("FROM \"ANNOTATION\" a ");
   180	        sql.append("JOIN \"ANNOTATION_FEATURES\" af ON af.\"ID_OID\" = a.\"ID\" ");
   181	        sql.append("JOIN \"MEDIAASSET_FEATURES\" mf ON mf.\"ID_EID\" = af.\"ID_EID\" ");
   182	        boolean joinEmbedding = method != null || methodVersion != null;
   183	        if (joinEmbedding) {
   184	            sql.append("JOIN \"EMBEDDING\" e ON e.\"ANNOTATION_ID\" = a.\"ID\" ");
   185	        }
   186	        sql.append("WHERE mf.\"ID_OID\" IN (").append(inList).append(") ");
   187	        sql.append("AND a.\"MATCHAGAINST\" = true ");
   188	        sql.append("AND a.\"ACMID\" IS NOT NULL ");
   189	        if (method != null) {
   190	            sql.append("AND e.\"METHOD\" = '").append(sqlEscape(method)).append("' ");
   191	        }
   192	        if (methodVersion != null) {
   193	            sql.append("AND e.\"METHODVERSION\" = '")
   194	                .append(sqlEscape(methodVersion)).append("' ");
   195	        }
   196	        return sql.toString();
   197	    }
   198	}

 succeeded in 219ms:
     1	package org.ecocean.ia;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertFalse;
     5	import static org.junit.jupiter.api.Assertions.assertTrue;
     6	
     7	import java.util.Arrays;
     8	import java.util.Collections;
     9	import java.util.LinkedHashSet;
    10	import java.util.Set;
    11	
    12	import org.junit.jupiter.api.Test;
    13	
    14	/**
    15	 * Shape-of-SQL tests for {@link MatchEligibilityQuery#buildSql}.
    16	 * Full execution against a real DataNucleus PMF is exercised by
    17	 * the live integration harness on dev deployments; the SQL
    18	 * builder is the mechanical part that benefits from unit-testing.
    19	 * (Empty-match-prospects design Track 2 C9.)
    20	 */
    21	class MatchEligibilityQueryTest {
    22	
    23	    private static Set<Integer> ids(Integer... s) {
    24	        return new LinkedHashSet<Integer>(Arrays.asList(s));
    25	    }
    26	
    27	    @Test void selectsDistinctAnnotationId() {
    28	        String sql = MatchEligibilityQuery.buildSql(
    29	            ids(42), "miewid-msv4.1", "4.1");
    30	        assertTrue(sql.startsWith("SELECT DISTINCT a.\"ID\""),
    31	            "SQL should start with SELECT DISTINCT a.\"ID\": " + sql);
    32	    }
    33	
    34	    @Test void joinsAnnotationFeaturesAndMediaAssetFeatures() {
    35	        String sql = MatchEligibilityQuery.buildSql(
    36	            ids(42), "miewid-msv4.1", "4.1");
    37	        assertTrue(sql.contains("JOIN \"ANNOTATION_FEATURES\" af"),
    38	            "missing ANNOTATION_FEATURES join: " + sql);
    39	        assertTrue(sql.contains("JOIN \"MEDIAASSET_FEATURES\" mf"),
    40	            "missing MEDIAASSET_FEATURES join: " + sql);
    41	        assertTrue(sql.contains("af.\"ID_OID\" = a.\"ID\""),
    42	            "wrong ANNOTATION_FEATURES join condition: " + sql);
    43	        assertTrue(sql.contains("mf.\"ID_EID\" = af.\"ID_EID\""),
    44	            "wrong MEDIAASSET_FEATURES join condition: " + sql);
    45	    }
    46	
    47	    @Test void joinsEmbeddingWhenMethodOrVersionProvided() {
    48	        String sql = MatchEligibilityQuery.buildSql(
    49	            ids(42), "miewid-msv4.1", "4.1");
    50	        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
    51	            "EMBEDDING join missing when method+version supplied: " + sql);
    52	        assertTrue(sql.contains("e.\"ANNOTATION_ID\" = a.\"ID\""));
    53	    }
    54	
    55	    @Test void joinsEmbeddingWhenOnlyMethodProvided() {
    56	        String sql = MatchEligibilityQuery.buildSql(
    57	            ids(42), "miewid-msv4.1", null);
    58	        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
    59	            "EMBEDDING join missing when only method supplied: " + sql);
    60	    }
    61	
    62	    @Test void joinsEmbeddingWhenOnlyVersionProvided() {
    63	        String sql = MatchEligibilityQuery.buildSql(
    64	            ids(42), null, "4.1");
    65	        assertTrue(sql.contains("JOIN \"EMBEDDING\" e"),
    66	            "EMBEDDING join missing when only version supplied: " + sql);
    67	    }
    68	
    69	    @Test void omitsEmbeddingJoinWhenBothNull() {
    70	        // Legacy api_endpoint-only config: gate just wants matchAgainst +
    71	        // acmId reachable from the sibling MAs; no embedding-method filter.
    72	        String sql = MatchEligibilityQuery.buildSql(ids(42), null, null);
    73	        assertFalse(sql.contains("JOIN \"EMBEDDING\""),
    74	            "EMBEDDING join should be omitted when both null: " + sql);
    75	        assertFalse(sql.contains("e.\"METHOD\""));
    76	        assertFalse(sql.contains("e.\"METHODVERSION\""));
    77	    }
    78	
    79	    @Test void emitsMatchAgainstAndAcmIdFilters() {
    80	        String sql = MatchEligibilityQuery.buildSql(
    81	            ids(42), "miewid-msv4.1", "4.1");
    82	        assertTrue(sql.contains("a.\"MATCHAGAINST\" = true"),
    83	            "missing matchAgainst filter: " + sql);
    84	        assertTrue(sql.contains("a.\"ACMID\" IS NOT NULL"),
    85	            "missing acmId IS NOT NULL filter: " + sql);
    86	    }
    87	
    88	    @Test void emitsMethodFilterStrictWhenNonNull_includingBlank() {
    89	        // Mirrors Annotation.getMatchQuery: strict-when-non-null.
    90	        // A blank string is a strict equality on "" (matches no docs),
    91	        // preserving consistency with the matcher.
    92	        String sql = MatchEligibilityQuery.buildSql(ids(42), "", "4.1");
    93	        assertTrue(sql.contains("AND e.\"METHOD\" = '' "),
    94	            "expected strict '' equality for blank method: " + sql);
    95	    }
    96	
    97	    @Test void emitsVersionFilterStrictWhenNonNull_includingBlank() {
    98	        String sql = MatchEligibilityQuery.buildSql(
    99	            ids(42), "miewid-msv4.1", "");
   100	        assertTrue(sql.contains("AND e.\"METHODVERSION\" = '' "),
   101	            "expected strict '' equality for blank version: " + sql);
   102	    }
   103	
   104	    @Test void inListUsesNumericValuesWithoutQuotes() {
   105	        String sql = MatchEligibilityQuery.buildSql(
   106	            ids(1, 42, 1000), "miewid-msv4.1", "4.1");
   107	        assertTrue(sql.contains("IN (1,42,1000)"),
   108	            "expected unquoted comma-separated IN list: " + sql);
   109	    }
   110	
   111	    @Test void skipsNullIdsInList() {
   112	        // LinkedHashSet allows null. Builder must drop them silently.
   113	        LinkedHashSet<Integer> ids = new LinkedHashSet<Integer>();
   114	        ids.add(1);
   115	        ids.add(null);
   116	        ids.add(42);
   117	        String sql = MatchEligibilityQuery.buildSql(ids, null, null);
   118	        assertTrue(sql.contains("IN (1,42)"),
   119	            "expected null to be skipped from IN list: " + sql);
   120	    }
   121	
   122	    @Test void escapesSingleQuotesInMethodAndVersion() {
   123	        String sql = MatchEligibilityQuery.buildSql(
   124	            ids(42), "ev'il", "v'1");
   125	        // Single quote inside the value must be doubled per SQL escaping.
   126	        assertTrue(sql.contains("e.\"METHOD\" = 'ev''il'"),
   127	            "method not escaped: " + sql);
   128	        assertTrue(sql.contains("e.\"METHODVERSION\" = 'v''1'"),
   129	            "version not escaped: " + sql);
   130	    }
   131	
   132	    @Test void emptyMaSetReturnsEmpty_inFindEligibleAnnotationIds() {
   133	        // The execute path bails out before any DB call when the input
   134	        // is empty. We can't easily test execute() without a Shepherd
   135	        // mock, but the early-return path is covered.
   136	        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
   137	            null, Collections.<Integer>emptySet(), "miewid-msv4.1", "4.1");
   138	        assertEquals(0, out.size());
   139	    }
   140	
   141	    @Test void nullMaSetReturnsEmpty_inFindEligibleAnnotationIds() {
   142	        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
   143	            null, null, "miewid-msv4.1", "4.1");
   144	        assertEquals(0, out.size());
   145	    }
   146	}

 succeeded in 218ms:
   760	    public static Map<String, Integer> getAllEncounterCounts(Shepherd myShepherd) {
   761	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
   762	            "SELECT \"ID_OID\", count(*) FROM \"IMPORTTASK_ENCOUNTERS\" GROUP BY \"ID_OID\"");
   763	
   764	        return parseSqlCountResults(query);
   765	    }
   766	
   767	    public static Map<String, Integer> getAllIndividualCounts(Shepherd myShepherd) {
   768	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
   769	            "SELECT ie.\"ID_OID\", count(distinct me.\"INDIVIDUALID_OID\") " +
   770	            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" me " +
   771	            "ON ie.\"CATALOGNUMBER_EID\" = me.\"CATALOGNUMBER_EID\" " + "GROUP BY ie.\"ID_OID\"");
   772	
   773	        return parseSqlCountResults(query);
   774	    }
   775	
   776	    public static Map<String, Integer> getAllMediaAssetCounts(Shepherd myShepherd) {
   777	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
   778	            "SELECT ie.\"ID_OID\", count(distinct mf.\"ID_OID\") " +
   779	            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"ENCOUNTER_ANNOTATIONS\" ea " +
   780	            "ON ie.\"CATALOGNUMBER_EID\" = ea.\"CATALOGNUMBER_OID\" " +
   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
   783	            "GROUP BY ie.\"ID_OID\"");
   784	
   785	        return parseSqlCountResults(query);
   786	    }
   787	}

 succeeded in 221ms:
src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
src/main/java/org/ecocean/Embedding.java:334:            // Count eligible candidates BEFORE getMatchQuery, which mutates
src/main/java/org/ecocean/Embedding.java:338:            // matchingSetQuery to getMatchQuery so the eligible-set object
src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
src/main/java/org/ecocean/Annotation.java:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
src/main/java/org/ecocean/Annotation.java:1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1170,1235p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '300,380p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '420,465p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/media/package.jdo | sed -n '1,90p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '540,610p;1038,1080p'" in /mnt/c/Wildbook-clean2
 succeeded in 235ms:
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

 succeeded in 245ms:
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
  1226	    }
  1227	
  1228	    // where we already have the query
  1229	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
  1230	        if (matchQuery == null) return null;
  1231	        List<Annotation> anns = new ArrayList<Annotation>();
  1232	        OpenSearch os = new OpenSearch();
  1233	        long startTime = System.currentTimeMillis();
  1234	        JSONObject queryRes = null;
  1235	        int hitSize = -1;

 succeeded in 238ms:
   420	values into Phase 2's SQL.
   421	
   422	**Encapsulation** (Codex round-4 OQ #2): the SQL lives in a new
   423	package-visible `MatchEligibilityQuery` class under
   424	`org.ecocean.ia` so it has its own test surface, not buried
   425	inside `MatchVisibilityGateImpl`:
   426	
   427	```java
   428	package org.ecocean.ia;
   429	
   430	public final class MatchEligibilityQuery {
   431	    public static Set<String> findEligibleAnnotationIds(
   432	        Shepherd shep,
   433	        Collection<String> siblingMaIds,
   434	        String method,
   435	        String methodVersion)
   436	    throws SQLException;
   437	}
   438	```
   439	
   440	```sql
   441	-- Sketch; JDOQL equivalent in code.
   442	SELECT DISTINCT a."ID"
   443	FROM "ANNOTATION" a
   444	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
   445	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
   446	JOIN "MEDIAASSET" ma ON ma."ID" = mf."ID_OID"
   447	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
   448	WHERE ma."ID" IN (<topTask sibling MA ids>)
   449	  AND a."MATCHAGAINST" = true
   450	  AND a."ACMID" IS NOT NULL
   451	  AND e."METHOD" = ?
   452	  AND e."METHODVERSION" = ?
   453	```
   454	
   455	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
   456	ensures we only wait for annotations that actually have an
   457	embedding of the model we're matching against. This excludes:
   458	- Bulk-import placeholder annotations created by `BulkImporter`
   459	  (no embedding at all, never indexed in OS due to
   460	  `skipAutoIndexing`).
   461	- Legacy annotations from a different embedding method.
   462	- Annotations whose ml-service extraction failed
   463	  (`predictModelId` set but `EMBEDDING` row never created).
   464	
   465	**`method`/`methodVersion` null handling** (Codex round-4 Major +

 succeeded in 237ms:
     1	<!DOCTYPE jdo PUBLIC
     2	    "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN"
     3	    "http://java.sun.com/dtd/jdo_2_0.dtd">
     4	
     5	<jdo>
     6	    <package name="org.ecocean.media">
     7	
     8		<class name="MediaAsset" identity-type="application">
     9			<field name="id" primary-key="true" value-strategy="identity"/>
    10			<field name="acmId">
    11				<column length="100"/>
    12				<index name="MEDIAASSET_ACMID_INDEX" />
    13			</field>
    14			<field name="uuid">
    15				<index name="MEDIAASSET_UUID_INDEX" unique="true" />
    16			</field>
    17			<field name="store" persistence-modifier="persistent" element-type="org.ecocean.media.AssetStore" />
    18			<field name="parentId" persistence-modifier="persistent">
    19				<column jdbc-type="INTEGER" allows-null="true"/>
    20			</field>
    21			<field name="validImageForIA" persistence-modifier="persistent">
    22				<column jdbc-type="BIT" allows-null="true"/>
    23			</field>
    24	            	<field name="revision" persistence-modifier="persistent">
    25	                	<column jdbc-type="BIGINT" allows-null="false"/>
    26	            	</field>
    27			<field name="metadata" persistence-modifier="persistent" cacheable="false" element-type="org.ecocean.media.MediaAssetMetadata" default-fetch-group="true" />
    28			<field name="parametersAsString" persistence-modifier="persistent">
    29	                	<column jdbc-type="LONGVARCHAR" name="parameters" />
    30			</field>
    31			<property name="derivationMethodAsString" persistence-modifier="persistent">
    32	                	<column jdbc-type="LONGVARCHAR" name="derivationMethod" />
    33			</property>
    34			<field name="contentHash">
    35				<column length="30"/>
    36				<index name="MEDIAASSET_CONTENTHASH_INDEX" />
    37			</field>
    38	
    39	    <field name="occurrence"/>
    40	
    41	<!--
    42			<field name="annotations" mapped-by="mediaAsset">
    43				<collection element-type="org.ecocean.Annotation"/>
    44				<join />
    45			</field>
    46	-->
    47	
    48			<field name="hashCode">
    49				<column length="75" />
    50			</field>
    51	
    52			<field name="labels" serialized="true" column="LABELS" />
    53	
    54	            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" mapped-by="asset" >
    55	                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
    56	                	<join />
    57	            	</field>
    58	
    59	            	<field name="keywords" persistence-modifier="persistent" default-fetch-group="true">
    60	                	<collection element-type="org.ecocean.Keyword" dependent-element="false" />
    61	                	<join />
    62	            	</field>
    63		</class>
    64	
    65	
    66		<class name="MediaAssetMetadata">
    67			<property name="dataAsString" persistence-modifier="persistent" default-fetch-group="true">
    68	                	<column jdbc-type="LONGVARCHAR" name="data" />
    69			</property>
    70		</class>
    71	
    72		<class name="AssetStore" identity-type="application">
    73			<field name="id" primary-key="true" value-strategy="identity"/>
    74	            	<field name="config" persistence-modifier="persistent" serialized="true" />
    75	    		<inheritance>
    76	        		<discriminator strategy="value-map">
    77					<column name="TYPE"/>
    78				</discriminator>
    79	    		</inheritance>
    80			<field name="type" persistence-modifier="none" />
    81		</class>
    82	
    83		<class name="LocalAssetStore">
    84			<inheritance strategy="superclass-table">
    85				<discriminator value="LOCAL"/>
    86			</inheritance>
    87		</class>
    88	
    89	 	<class name="URLAssetStore">
    90	 		<inheritance strategy="superclass-table">

 succeeded in 286ms:
   540	        <column length="36"/>
   541	      </field>
   542	
   543	      <field name="iaClass">
   544	        <column name="IACLASS"/>
   545	      </field>
   546	
   547		<field name="acmId">
   548			<column length="100"/>
   549		</field>
   550	
   551		<!-- ml-service migration v2 (commit #4): idempotency + WBIA registration.
   552		     The partial unique index on (mediaAsset, predictModelId, bboxKey,
   553		     thetaKey) WHERE predictModelId IS NOT NULL is applied by
   554		     archive/sql/ml_service_idempotency.sql. -->
   555		<field name="predictModelId">
   556			<column length="100" allows-null="true"/>
   557			<index name="ANNOTATION_PREDICTMODELID_IDX"/>
   558		</field>
   559		<field name="bboxKey">
   560			<column length="64" allows-null="true"/>
   561		</field>
   562		<field name="thetaKey">
   563			<column length="20" allows-null="true"/>
   564		</field>
   565		<field name="wbiaRegistered">
   566			<column allows-null="true"/>
   567		</field>
   568		<field name="wbiaRegisterAttempts">
   569			<column allows-null="false" default-value="0"/>
   570		</field>
   571	
   572			<field name="isExemplar">
   573	<!--  note, if you are using mysql, which maybe you shouldnt be?, you want this here: -->
   574	<!--			<column default-value="0" /> -->
   575	<!-- this is for postgresql-->
   576				<column default-value="false" />
   577	
   578			</field>
   579	
   580			<field name="matchAgainst">
   581				<column default-value="false" />
   582				<index name="ANNOTATION_MATCHAGAINST_idx" />
   583			</field>
   584			<field name="viewpoint">
   585				<index name="ANNOTATION_VIEWPOINT_IDX" />
   586			</field>
   587	
   588			<field jdbc-type="BIGINT" name="version" allows-null="false" >
   589				<column default-value="-1" />
   590				<index name="ANNOTATION_VERSION_IDX" />
   591			</field>
   592	
   593	            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
   594	                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
   595	                	<join />
   596	            	</field>
   597	
   598	      <field name="mediaAsset" persistence-modifier="persistent" element-type="org.ecocean.media.MediaAsset" recursion-depth="3"/>
   599	
   600	      <field name="quality" persistence-modifier="persistent">
   601	        <column jdbc-type="DOUBLE" allows-null="true"/>
   602	      </field>
   603	
   604	      <field name="distinctiveness" persistence-modifier="persistent">
   605	        <column jdbc-type="DOUBLE" allows-null="true"/>
   606	      </field>
   607	
   608	      <!--
   609			<field name="annot_xtl" />
   610			<field name="annot_ytl" />
  1038			</field>
  1039		</class>
  1040	
  1041		<class name="Embedding" identity-type="application">
  1042			<!-- ml-service migration v2 (commit #4): non-unique composite index
  1043			     to support idempotency lookup of (annotation, method, methodVersion).
  1044			     Promotion to UNIQUE is deferred to a follow-up branch after a
  1045			     per-deployment audit confirms no existing duplicates (see
  1046			     archive/sql/ml_service_embedding_audit.sql). -->
  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
  1048				<column name="ANNOTATION_ID"/>
  1049				<column name="METHOD"/>
  1050				<column name="METHODVERSION"/>
  1051			</index>
  1052			<field name="id" primary-key="true">
  1053				<column length="36"/>
  1054			</field>
  1055	
  1056			<field name="annotation" dependent-element="false" >
  1057				<column name="ANNOTATION_ID" />
  1058			</field>
  1059	
  1060			<field name="vectorFloatArray" persistence-modifier="persistent" />
  1061	
  1062			<field name="created">
  1063				<column jdbc-type="BIGINT" />
  1064				<index name="EMBEDDING_CREATED_IDX" />
  1065			</field>
  1066	
  1067			<field name="method">
  1068				<column length="64" />
  1069				<index name="EMBEDDING_METHOD_IDX" />
  1070			</field>
  1071	
  1072			<field name="methodVersion">
  1073				<column length="32" />
  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
  1075			</field>
  1076		</class>
  1077	
  1078	  </package>
  1079	</jdo>

codex
I found the implementation. The SQL shape mostly matches the schema, but I’m checking how callers will interpret failures because the current execution path catches all exceptions and returns an empty set.
exec
/bin/bash -lc "rg -n \"query.closeAll|closeAll\\(|newQuery\\(\\\"javax.jdo.query.SQL\\\"\" src/main/java/org/ecocean src/main/webapp | head -120" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '465,540p;800,845p;930,955p;1025,1045p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "MatchVisibilityGate|findEligibleAnnotationIds|empty-match|waitForAnnotationMatchableIds|waitForVisibility|MatchEligibilityQuery" src/main/java src/test/java docs/plans/2026-05-18-empty-match-prospects-design.md' in /mnt/c/Wildbook-clean2
 succeeded in 189ms:
   465	**`method`/`methodVersion` null handling** (Codex round-4 Major +
   466	round-5 Major). Both fields are independently nullable for legacy
   467	`api_endpoint`-only configs that `Embedding.findMatchProspects`
   468	accepts as vector configs (`Embedding.java:269`), where
   469	`MLService.getMethodValues` can return null in either slot
   470	(`MLService.java:348`). `Annotation.getMatchQuery` omits each
   471	predicate independently when its value is blank
   472	(`Annotation.java:1205-1209`).
   473	
   474	Phase 2's SQL builds predicates conditionally to match:
   475	
   476	- If `method` is blank, OMIT `AND e."METHOD" = ?`.
   477	- If `methodVersion` is blank, OMIT `AND e."METHODVERSION" = ?`.
   478	
   479	`waitForAnnotationMatchableIds` does the same for the nested OS
   480	clause — omit the corresponding `term` filter when the
   481	respective value is blank. The implementation builds the SQL /
   482	JSON conditionally rather than passing null parameters.
   483	
   484	This is a single `SELECT DISTINCT` query, no materialization of
   485	Annotation objects. Returns a `Set<String>` of annotation IDs.
   486	
   487	#### Phase 3: matchable-visibility wait on the eligible set
   488	
   489	`waitForVisibility` today polls `_count` with an `ids` query
   490	(`OpenSearch.java:521`). That confirms `_id` is in OS but does
   491	NOT confirm the doc has its nested `embeddings.method` /
   492	`methodVersion` indexed — a doc that's been written with the
   493	right `_id` but stale nested fields would pass `_id`-only and
   494	then knn-fail at match time.
   495	
   496	**Scope** (Codex round-3 Major): the helper introduced here is
   497	NOT a full replication of `getMatchingSetQuery` (which adds
   498	taxonomy, viewpoint, iaClass, encounter exclusion, dead-animal
   499	range, task params). It is narrower: "doc has fresh embedding
   500	metadata." That's sufficient for the visibility race the gate
   501	solves — the index lag is between persist and OS write of
   502	embedding metadata, not between persist and the encounter/
   503	taxonomy fields (which are written at the same time as id and
   504	acmId).
   505	
   506	The conceptual `_count` body (matching the shape `queryCount`
   507	expects at `OpenSearch.java:541-552` — uses `ids` query, no
   508	`size`, no `track_total_hits`):
   509	
   510	```json
   511	{
   512	  "query": {
   513	    "bool": {
   514	      "filter": [
   515	        { "ids": { "values": [ ...waitSetIds ] } },
   516	        { "term":  { "matchAgainst": true } },
   517	        { "exists": { "field": "acmId" } },
   518	        { "nested": {
   519	            "path": "embeddings",
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
   800	Unit tests can now:
   801	- Stub `gateForBatch` to return READY → assert `runMatchProspects` is
   802	  called with caller's IDs.
   803	- Stub `gateForBatch` to return DEFER → assert `enqueueDeferredMatch`
   804	  is called with attempt counter.
   805	- Stub `gateForBatch` to return GIVE_UP → assert match runs anyway
   806	  (age-out behavior).
   807	- Test the gate implementation separately with a mock OpenSearch
   808	  + Shepherd-test-double.
   809	
   810	Also: extract the JDOQL "find eligible annotations for a topTask"
   811	into a package-visible static method on `Annotation` or a new
   812	`MatchEligibilityQuery` utility, so it can be unit-tested with a
   813	DataNucleus in-memory store (the v2 pattern from c8/c9 tests).
   814	
   815	#### Behavior in edge cases
   816	
   817	- **Child Task has no parent (orphaned single-asset job from
   818	  reconciler)**: gate degrades to "no batch to wait for; just OS
   819	  visibility on caller's IDs". Logs WARN with `childTaskId` (Codex
   820	  round-4 OQ #3): this is an abnormal degraded path, not silent
   821	  normal behavior.
   822	- **Sibling MA permanently stuck in `processing`**: bounded by
   823	  `MAX_DEFER_AGE_MILLIS = 12 min` (well inside `requeueJob`'s
   824	  ~15min queue cap). User's match runs against whatever's
   825	  visible at age-out.
   826	- **All siblings are `error` / `zero_detections`**: Phase 2's
   827	  eligibility filter returns the empty set or only the caller's
   828	  own annotations. `waitForVisibility` on empty set should be
   829	  short-circuit (or no-op); confirm behavior in tests.
   830	- **Two parallel users with overlapping imports**: topTask is
   831	  per-routing-call, so each user's batch is its own topTask.
   832	  Boundary is clean.
   833	- **Caller's annotation isn't in the eligible set** (somehow):
   834	  union it in. The gate should never block the caller's match on
   835	  its own visibility — the caller's annotation went through
   836	  `waitForVisibility` in the old single-annotation path
   837	  effectively; we maintain that property by always including
   838	  caller's IDs in the visibility set, eligibility filter or not.
   839	
   840	## Interaction between Track 1 and Track 2
   841	
   842	The two tracks are independent in code (different files, different
   843	review chains) but linked in symptom. Track 1 fixes acmId/HotSpotter
   844	sync — annotations no longer linger with placeholder acmIds in OS.
   845	Track 2 fixes the timing where annotations are visible in DB but
   930	- `deferred_incrementsAttempt_perDeferral` — diagnostic only;
   931	  doesn't drive age-out.
   932	- `deferred_writesLastGateReason_intoJobData`
   933	- `enqueueDeferredMatch_publishesViaRequeueJob_with30sDelay` —
   934	  observe `__queueRetries` set, sleep mechanism invoked.
   935	- `enqueueDeferredMatch_includesRoutingFlags` — captured payload
   936	  has `mlServiceV2: true` AND `deferredMatch: true` (Codex
   937	  round-5 Blocker). Without both, the requeued job is dropped on
   938	  the floor.
   939	
   940	**Bulk-import placeholder exclusion (Codex round-1 Blocker 2):**
   941	
   942	- `gate_excludesPlaceholderAnnotationsWithSkipAutoIndexing` — set
   943	  up an annotation in `BulkImporter`'s placeholder state
   944	  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
   945	  filtered out by the embedding-method predicate.
   946	- `gate_excludesAnnotationsWithoutAcmId`
   947	- `gate_excludesAnnotationsWithMatchAgainstFalse`
   948	
   949	**Processor wiring:**
   950	
   951	- `processor_callsRunMatchProspects_onlyAfterGateReady`
   952	- `processor_callsEnqueueDeferredMatch_onGateDefer`
   953	- `processor_runsMatchAnyway_onGateGiveUp_withWarnLog`
   954	- `runDeferredMatch_reGatesBeforeRunning`
   955	- `runDeferredMatch_agesOutAtMaxAge`
  1025	
  1026	All round-4 open questions have been incorporated:
  1027	
  1028	- `safeInvalidate(context, name)` lives on `QueryCacheFactory`
  1029	  (round-4 OQ #1).
  1030	- `MatchEligibilityQuery` extracted as package-visible class in
  1031	  `org.ecocean.ia` (round-4 OQ #2).
  1032	- `topTask == null` logs WARN with childTaskId (round-4 OQ #3).
  1033	
  1034	This pass is confirmatory only. The expected outcome is "no
  1035	material findings". If Codex round 5 surfaces anything not
  1036	already in this revision history, address it inline; otherwise
  1037	the design is locked and ready for implementation in the order
  1038	specified.
  1039	
  1040	## Cross-references
  1041	
  1042	- v1 of this design (superseded): see git history for
  1043	  `2026-05-18-empty-match-prospects-design.md`.
  1044	- Codex review of v1:
  1045	  `docs/plans/2026-05-18-empty-match-prospects-codex-review.md`.

 succeeded in 1058ms:
src/main/webapp/individualSearchResultsAnalysis.jsp:138: 	try{numYearsCoverage=1+((Integer)yearsCoverageQuery.execute()).intValue();yearsCoverageQuery.closeAll();}
src/main/webapp/individualSearchResultsAnalysis.jsp:141: 		yearsCoverageQuery.closeAll();
src/main/webapp/individualSearch.jsp:888:						Query q32 = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "select distinct(\"VIEWPOINT\") as v from \"ANNOTATION\" order by v");
src/main/webapp/individualSearch.jsp:898:						q32.closeAll();
src/main/webapp/individualSearch.jsp:910:						Query q33 = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "select distinct(\"IACLASS\") as v from \"ANNOTATION\" order by v");
src/main/webapp/individualSearch.jsp:920:						q33.closeAll();
src/main/webapp/appadmin/wildbookIAQueueStats.jsp:54:	q.closeAll();
src/main/webapp/appadmin/wildbookIAQueueStats.jsp:183:        qD.closeAll();
src/main/webapp/appadmin/wildbookIAQueueStats.jsp:188:        qID.closeAll();
src/main/webapp/individuals.jsp:84:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "SELECT \"INDIVIDUALID\" FROM \"MARKEDINDIVIDUAL\" WHERE \"LEGACYINDIVIDUALID\" = ? OR \"ALTERNATEID\" LIKE ? OR \"INDIVIDUALID\" = ?");
src/main/webapp/individuals.jsp:89:        q.closeAll();
src/main/webapp/imports.jsp:103:    query.closeAll();
src/main/webapp/appadmin/sharedAnnotations.jsp:213:    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/webapp/appadmin/sharedAnnotations.jsp:269:	        q2.closeAll();
src/main/webapp/appadmin/sharedAnnotations.jsp:272:    q.closeAll();
src/main/java/org/ecocean/WBQuery.java:113:        query.closeAll();
src/main/java/org/ecocean/Util.java:350:                query.closeAll();
src/main/java/org/ecocean/Util.java:699:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Util.java:708:        q.closeAll();
src/main/java/org/ecocean/Util.java:717:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Util.java:722:        q.closeAll();
src/main/webapp/appadmin/resendWaitingTasks.jsp:112:	q.closeAll();
src/main/webapp/surveys/surveySearch.jsp:349:  kwQuery.closeAll();
src/main/java/org/ecocean/Taxonomy.java:179:        query.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:18:	q.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:27:		q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:32:		q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:43:		q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:48:		q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:59:		q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:64:		q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:78:		q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:89:	q.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:96:			q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:102:			q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:115:	q.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:122:			q2.closeAll();
src/main/webapp/appadmin/iaBreakdownBySpecies.jsp:128:			q2.closeAll();
src/main/java/org/ecocean/identity/IdentityServiceLog.java:129:        qry.closeAll();
src/main/java/org/ecocean/identity/IdentityServiceLog.java:151:        qry.closeAll();
src/main/java/org/ecocean/identity/IdentityServiceLog.java:174:        qry.closeAll();
src/main/java/org/ecocean/identity/IdentityServiceLog.java:200:        qry.closeAll();
src/main/java/org/ecocean/identity/IdentityServiceLog.java:232:        qry.closeAll();
src/main/java/org/ecocean/identity/IdentityServiceLog.java:267:        qry.closeAll();
src/main/webapp/appadmin/duplicateAnnotations.jsp:269:	    q.closeAll();
src/main/webapp/appadmin/duplicateAnnotations.jsp:312:				        q2.closeAll();
src/main/java/org/ecocean/identity/IBEISIAIdentificationMatchingState.java:49:        query.closeAll();
src/main/java/org/ecocean/identity/IBEISIAIdentificationMatchingState.java:63:        q.closeAll();
src/main/webapp/encounters/socialJson.jsp:191:	if(query!=null)query.closeAll();
src/main/webapp/iaResultsSetID.jsp:27:	    query.closeAll();
src/main/java/org/ecocean/identity/IBEISIA.java:3745:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/identity/IBEISIA.java:3757:        q.closeAll();
src/main/webapp/appadmin/dataValuesCheck.jsp:103:		q.closeAll();
src/main/java/org/ecocean/SurveyQueryProcessor.java:95:        query.closeAll();
src/main/webapp/encounters/relationshipJSON.jsp:164:	if(query!=null)query.closeAll();
src/main/webapp/encounters/relationshipJSON.jsp:165:	if(query2!=null)query2.closeAll();
src/main/webapp/appadmin/consolidateDuplicateUsersSandbox.jsp:51:        // query.closeAll();
src/main/java/org/ecocean/StartupWildbook.java:504:            q.closeAll();
src/main/java/org/ecocean/StartupWildbook.java:604:            q.closeAll();
src/main/java/org/ecocean/ia/Task.java:545:        query.closeAll();
src/main/java/org/ecocean/ia/Task.java:563:        query.closeAll();
src/main/webapp/encounters/occurrenceGraphJson.jsp:175:	if(query!=null)query.closeAll();
src/main/webapp/appadmin/automatedUserReconciliation.jsp:66:        query.closeAll();
src/main/webapp/appadmin/automatedUserReconciliation.jsp:75:        query.closeAll();
src/main/webapp/acmIdSync.jsp:25:	    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/webapp/acmIdSync.jsp:38:	    q.closeAll();
src/main/webapp/acmIdSync.jsp:57:	    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/webapp/acmIdSync.jsp:105:	    q.closeAll();
src/main/webapp/encounters/manualAnnotation.jsp:228:	//Query q2 = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "select distinct(\"IACLASS\") as v from \"ANNOTATION\" order by v");
src/main/webapp/encounters/manualAnnotation.jsp:248:	//q2.closeAll();
src/main/webapp/encounters/encounterVM.jsp:125:				kwQuery.closeAll();
src/main/webapp/encounters/encounterVM.jsp:154:	kwQuery.closeAll();
src/main/webapp/encounters/encounterSearchResultsAnalysisEmbed.jsp:89:    acceptedEncounters.closeAll();
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:115:            q = shep.getPM().newQuery("javax.jdo.query.SQL", sql.toString());
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:131:            if (q != null) q.closeAll();
src/main/webapp/encounters/encounterSearch.jsp:980:            //kwQuery.closeAll();
src/main/webapp/encounters/encounterSearch.jsp:1031:						Query q32 = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "select distinct(\"VIEWPOINT\") as v from \"ANNOTATION\" order by v");
src/main/webapp/encounters/encounterSearch.jsp:1041:						q32.closeAll();
src/main/webapp/encounters/encounterSearch.jsp:1053:						Query q33 = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "select distinct(\"IACLASS\") as v from \"ANNOTATION\" order by v");
src/main/webapp/encounters/encounterSearch.jsp:1063:						q33.closeAll();
src/main/webapp/encounters/encounterMediaGallery.jsp:105:	query.closeAll();
src/main/webapp/encounters/encounterMediaGallery.jsp:305:        Query q = imageShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/webapp/encounters/encounterMediaGallery.jsp:321:        q.closeAll();
src/main/webapp/encounters/encounterMediaGallery.jsp:331:	query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:421:            acceptedCollabs.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:534:        qry.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:648:        workspaceQuery.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:669:            workspaceQuery.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:690:            mediaAssetQuery.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:713:            mediaAssetQuery.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:717:            mediaAssetQuery.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:749:                acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:754:            acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:757:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:776:                acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:781:            acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:784:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:804:                acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:809:            acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:812:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:837:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:894:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:905:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:920:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:932:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:949:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:964:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:979:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:993:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1049:            query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1076:            query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1096:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1116:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1130:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1146:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1180:        query.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1226:                acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1231:            acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1234:        acceptedEncounters.closeAll();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1383:                acceptedKeywords.closeAll();

 succeeded in 1141ms:
docs/plans/2026-05-18-empty-match-prospects-design.md:50:  Phase 2 SQL and in `waitForAnnotationMatchableIds`, matching
docs/plans/2026-05-18-empty-match-prospects-design.md:54:  on `QueryCacheFactory`, `MatchEligibilityQuery` extraction,
docs/plans/2026-05-18-empty-match-prospects-design.md:71:  `waitForVisibility`); sibling eligible IDs wait on
docs/plans/2026-05-18-empty-match-prospects-design.md:72:  `waitForAnnotationMatchableIds` (new matchable predicate).
docs/plans/2026-05-18-empty-match-prospects-design.md:84:- **`MatchEligibilityQuery` extracted as package-visible class
docs/plans/2026-05-18-empty-match-prospects-design.md:119:  Major). Renamed to `waitForAnnotationMatchableIds` (per round-3
docs/plans/2026-05-18-empty-match-prospects-design.md:153:- **Shepherd boundary spelled out** in `MatchVisibilityGateImpl`
docs/plans/2026-05-18-empty-match-prospects-design.md:186:- Moving away from `waitForVisibility`. It works correctly — its
docs/plans/2026-05-18-empty-match-prospects-design.md:195:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-design.md:423:package-visible `MatchEligibilityQuery` class under
docs/plans/2026-05-18-empty-match-prospects-design.md:425:inside `MatchVisibilityGateImpl`:
docs/plans/2026-05-18-empty-match-prospects-design.md:430:public final class MatchEligibilityQuery {
docs/plans/2026-05-18-empty-match-prospects-design.md:431:    public static Set<String> findEligibleAnnotationIds(
docs/plans/2026-05-18-empty-match-prospects-design.md:479:`waitForAnnotationMatchableIds` does the same for the nested OS
docs/plans/2026-05-18-empty-match-prospects-design.md:489:`waitForVisibility` today polls `_count` with an `ids` query
docs/plans/2026-05-18-empty-match-prospects-design.md:538:public boolean waitForAnnotationMatchableIds(
docs/plans/2026-05-18-empty-match-prospects-design.md:547:`waitForVisibility`. Lives on `OpenSearch.java` (per Codex round-3
docs/plans/2026-05-18-empty-match-prospects-design.md:554:If `waitForAnnotationMatchableIds` returns false (timeout),
docs/plans/2026-05-18-empty-match-prospects-design.md:568:if (!os.waitForVisibility("annotation", callerAnnotationIds,
docs/plans/2026-05-18-empty-match-prospects-design.md:578:    !os.waitForAnnotationMatchableIds(siblingsOnly,
docs/plans/2026-05-18-empty-match-prospects-design.md:612:        System.out.println("WARN: MatchVisibilityGate aged out for task "
docs/plans/2026-05-18-empty-match-prospects-design.md:620:`MatchVisibilityGate.gateForBatch(callerAnnotationIds, childTaskId,
docs/plans/2026-05-18-empty-match-prospects-design.md:726:#### Testability seam — `MatchVisibilityGate`
docs/plans/2026-05-18-empty-match-prospects-design.md:738:public interface MatchVisibilityGate {
docs/plans/2026-05-18-empty-match-prospects-design.md:761:#### Shepherd boundary in `MatchVisibilityGateImpl`
docs/plans/2026-05-18-empty-match-prospects-design.md:792:Real implementation `MatchVisibilityGateImpl` constructs Shepherd +
docs/plans/2026-05-18-empty-match-prospects-design.md:795:`MlServiceProcessor` holds `private final MatchVisibilityGate
docs/plans/2026-05-18-empty-match-prospects-design.md:797:`MatchVisibilityGateImpl`; a package-visible constructor accepts
docs/plans/2026-05-18-empty-match-prospects-design.md:812:`MatchEligibilityQuery` utility, so it can be unit-tested with a
docs/plans/2026-05-18-empty-match-prospects-design.md:828:  own annotations. `waitForVisibility` on empty set should be
docs/plans/2026-05-18-empty-match-prospects-design.md:836:  `waitForVisibility` in the old single-annotation path
docs/plans/2026-05-18-empty-match-prospects-design.md:880:New unit tests in `MatchVisibilityGateImplTest` and updates to
docs/plans/2026-05-18-empty-match-prospects-design.md:968:- `waitForAnnotationMatchableIds_usesIdsQuery_notTermsOnUnderscoreId` —
docs/plans/2026-05-18-empty-match-prospects-design.md:970:- `waitForAnnotationMatchableIds_bodyHasNoSizeOrTrackTotal` —
docs/plans/2026-05-18-empty-match-prospects-design.md:984:In addition to the `MatchVisibilityGate` interface, extract a
docs/plans/2026-05-18-empty-match-prospects-design.md:1018:- Helper named `waitForAnnotationMatchableIds`, public on
docs/plans/2026-05-18-empty-match-prospects-design.md:1030:- `MatchEligibilityQuery` extracted as package-visible class in
docs/plans/2026-05-18-empty-match-prospects-design.md:1043:  `2026-05-18-empty-match-prospects-design.md`.
docs/plans/2026-05-18-empty-match-prospects-design.md:1045:  `docs/plans/2026-05-18-empty-match-prospects-codex-review.md`.
docs/plans/2026-05-18-empty-match-prospects-design.md:1050:- waitForVisibility introduction commit: `f429c5bf8` (c7).
docs/plans/2026-05-18-empty-match-prospects-design.md:1057:  - `OpenSearch.waitForVisibility` —
src/main/java/org/ecocean/ia/Task.java:601:     * empty-match-prospects Track 2 batch gate) need this so a read of
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:10: * accessor needed by the empty-match-prospects Track 2 batch gate so a
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:551:    // ml-service migration v2 / empty-match-prospects design Track 2 C8.
src/main/java/org/ecocean/OpenSearch.java:553:    // Stronger visibility predicate than waitForVisibility for the annotation
src/main/java/org/ecocean/OpenSearch.java:568:    // Like waitForVisibility: _refresh on entry, then exponential-backoff
src/main/java/org/ecocean/OpenSearch.java:571:    public boolean waitForAnnotationMatchableIds(Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:584:                "WARN: OpenSearch.waitForAnnotationMatchableIds called with " +
src/main/java/org/ecocean/OpenSearch.java:612:    // waitForAnnotationMatchableIds. Uses the same `ids` query shape as
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:15: * Shape-of-SQL tests for {@link MatchEligibilityQuery#buildSql}.
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:21:class MatchEligibilityQueryTest {
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:28:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:35:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:48:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:56:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:63:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:72:        String sql = MatchEligibilityQuery.buildSql(ids(42), null, null);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:80:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:92:        String sql = MatchEligibilityQuery.buildSql(ids(42), "", "4.1");
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:98:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:105:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:117:        String sql = MatchEligibilityQuery.buildSql(ids, null, null);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:123:        String sql = MatchEligibilityQuery.buildSql(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:136:        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:142:        Set<String> out = MatchEligibilityQuery.findEligibleAnnotationIds(
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:17: * Full poll-and-wait behavior of {@link OpenSearch#waitForAnnotationMatchableIds}
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:14: * Direct-SQL utility for the empty-match-prospects batch gate.
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:21: * <p>Used by {@link MatchVisibilityGate} (Track 2 C10) to compute
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:23: * {@link org.ecocean.OpenSearch#waitForAnnotationMatchableIds}.
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:38:public final class MatchEligibilityQuery {
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:40:    private MatchEligibilityQuery() { }
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:62:    public static Set<String> findEligibleAnnotationIds(
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:128:                "[ERROR] MatchEligibilityQuery.findEligibleAnnotationIds() failed: " + ex);

exec
/bin/bash -lc 'rg -n "getEmbeddingByMethod|numberEmbeddings|embeddings" src/main/java/org/ecocean/Annotation.java src/main/resources/org/ecocean/package.jdo | head -80' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1080,1170p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '620,670p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '550,660p'" in /mnt/c/Wildbook-clean2
 succeeded in 197ms:
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

 succeeded in 236ms:
src/main/java/org/ecocean/Annotation.java:52:    private Set<Embedding> embeddings;
src/main/java/org/ecocean/Annotation.java:189:        // embeddings have some metadata (algorithm etc)
src/main/java/org/ecocean/Annotation.java:204:        map.put("embeddings", embMap);
src/main/java/org/ecocean/Annotation.java:244:        jgen.writeArrayFieldStart("embeddings");
src/main/java/org/ecocean/Annotation.java:245:        if (this.embeddings != null)
src/main/java/org/ecocean/Annotation.java:246:            for (Embedding emb : this.embeddings) {
src/main/java/org/ecocean/Annotation.java:853:                   .append("numEmbed", numberEmbeddings())
src/main/java/org/ecocean/Annotation.java:1125:        // this exludes the very noisy embeddings from opensearch results since we dont need it
src/main/java/org/ecocean/Annotation.java:1126:        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
src/main/java/org/ecocean/Annotation.java:1179:    // return null when this annot has no embeddings to match, sorry!
src/main/java/org/ecocean/Annotation.java:1184:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1194:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1198:            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
src/main/java/org/ecocean/Annotation.java:1200:        JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
src/main/java/org/ecocean/Annotation.java:1201:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
src/main/java/org/ecocean/Annotation.java:1203:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
src/main/java/org/ecocean/Annotation.java:1687:    // note: this will throw an IAException if the txStr does not support embeddings
src/main/java/org/ecocean/Annotation.java:1834:        int rtn = numberEmbeddings();
src/main/java/org/ecocean/Annotation.java:1837:        for (Embedding emb : embeddings) {
src/main/java/org/ecocean/Annotation.java:2004:        return embeddings;
src/main/java/org/ecocean/Annotation.java:2007:    public int numberEmbeddings() {
src/main/java/org/ecocean/Annotation.java:2008:        return Util.collectionSize(embeddings);
src/main/java/org/ecocean/Annotation.java:2014:        if (Util.collectionIsEmptyOrNull(embeddings)) return cts;
src/main/java/org/ecocean/Annotation.java:2015:        for (Embedding emb : embeddings) {
src/main/java/org/ecocean/Annotation.java:2024:        if (embeddings == null) embeddings = new HashSet<Embedding>();
src/main/java/org/ecocean/Annotation.java:2025:        if (emb == null) return embeddings;
src/main/java/org/ecocean/Annotation.java:2026:        embeddings.add(emb);
src/main/java/org/ecocean/Annotation.java:2028:        return embeddings;
src/main/java/org/ecocean/Annotation.java:2032:        if (embeddings == null) return false;
src/main/java/org/ecocean/Annotation.java:2033:        return embeddings.contains(emb);
src/main/java/org/ecocean/Annotation.java:2036:    // since embeddings is a set, there isnt really an order so...
src/main/java/org/ecocean/Annotation.java:2039:        return getEmbeddingByMethod(null, null);
src/main/java/org/ecocean/Annotation.java:2042:    public Embedding getEmbeddingByMethod(String method) {
src/main/java/org/ecocean/Annotation.java:2043:        return getEmbeddingByMethod(method, null);
src/main/java/org/ecocean/Annotation.java:2047:    public Embedding getEmbeddingByMethod(String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:2048:        if (numberEmbeddings() < 1) return null;
src/main/java/org/ecocean/Annotation.java:2049:        Iterator it = embeddings.iterator();
src/main/java/org/ecocean/Annotation.java:2063:        if (numberEmbeddings() < 1) return null;
src/main/java/org/ecocean/Annotation.java:2064:        Iterator it = embeddings.iterator();
src/main/java/org/ecocean/Annotation.java:2074:        if (embeddings == null) return;
src/main/java/org/ecocean/Annotation.java:2075:        for (Embedding emb : this.embeddings) {
src/main/resources/org/ecocean/package.jdo:617:	<field name="embeddings" default-fetch-group="false" mapped-by="annotation">

 succeeded in 208ms:
   620	
   621	    </class>
   622	
   623	    <class name="DataCollectionEvent" identity-type="application">
   624	      <inheritance strategy="new-table"/>
   625	      <field name="correspondingEncounterNumber">
   626	        <column name="CORRESPONDINGENCOUNTERNUMBER"/>
   627	      </field>
   628	
   629	      <field name="dataCollectionEventID" primary-key="true" value-strategy="native">
   630	        <column name="DATACOLLECTIONEVENTID"/>
   631	      </field>
   632	      <field name="samplingProtocol">
   633	        <column name="SAMPLINGPROTOCOL"/>
   634	      </field>
   635	      <field name="samplingEffort">
   636	        <column name="SAMPLINGEFFORT"/>
   637	      </field>
   638	      <field name="eventStartDate">
   639	        <column name="EVENTSTARTDATE"/>
   640	      </field>
   641	      <field name="eventEndDate">
   642	        <column name="EVENTENDDATE"/>
   643	      </field>
   644	      <field name="fieldNumber">
   645	        <column name="FIELDNUMBER"/>
   646	      </field>
   647	      <field name="fieldNotes">
   648	        <column name="FIELDNOTES"/>
   649	      </field>
   650	
   651	      <field name="eventRemarks">
   652	        <column name="EVENTREMARKS"/>
   653	      </field>
   654	      <field name="institutionID">
   655	        <column name="INSTITUTIONID"/>
   656	      </field>
   657	      <field name="collectionID">
   658	        <column name="COLLECTIONID"/>
   659	      </field>
   660	      <field name="datasetID">
   661	        <column name="DATASETID"/>
   662	      </field>
   663	      <field name="institutionCode">
   664	        <column name="INSTITUTIONCODE"/>
   665	      </field>
   666	      <field name="collectionCode">
   667	        <column name="COLLECTIONCODE"/>
   668	      </field>
   669	      <field name="datasetName">
   670	        <column name="DATASETNAME"/>

 succeeded in 211ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '2036,2068p'" in /mnt/c/Wildbook-clean2
 succeeded in 245ms:
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
  2066	            Embedding emb = (Embedding)it.next();
  2067	            if (emb.hasEqualVector(find)) return emb;
  2068	        }

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '540,620p;730,795p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '388,438p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "throws SQLException|SQLException|MatchEligibilityQuery" docs/plans/2026-05-18-empty-match-prospects-design.md docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md' in /mnt/c/Wildbook-clean2
 succeeded in 224ms:
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
   730	5 calls this out: mock/wrap unit tests are not feasible cleanly
   731	without changing the seam.
   732	
   733	Extract:
   734	
   735	```java
   736	package org.ecocean.ia;
   737	
   738	public interface MatchVisibilityGate {
   739	    enum Kind { READY, DEFER, GIVE_UP }
   740	
   741	    static class GateOutcome {
   742	        public final Kind kind;
   743	        public final int attempt;            // current attempt number (1-based)
   744	        public final long firstDeferredAt;   // epoch-ms of first DEFER (or now for first call)
   745	        public final long elapsedMillis;     // System.currentTimeMillis() - firstDeferredAt
   746	        public final String reason;          // human-readable, written to jobData.lastGateReason
   747	        // factory methods: ready(attempt, firstDeferredAt),
   748	        //                  defer(attempt, firstDeferredAt, reason),
   749	        //                  giveUp(attempt, firstDeferredAt, reason)
   750	    }
   751	
   752	    GateOutcome gateForBatch(
   753	        Collection<String> callerAnnotationIds,
   754	        String childTaskId,
   755	        JSONObject matchConfig,
   756	        int attempt,
   757	        Long firstDeferredAt);
   758	}
   759	```
   760	
   761	#### Shepherd boundary in `MatchVisibilityGateImpl`
   762	
   763	Explicit phase-locality, per Codex round-2 Major:
   764	
   765	```
   766	Phase 1 + 2 (read DB):
   767	    Shepherd shep = new Shepherd(context);
   768	    shep.setAction(ACTION_PREFIX + "gateForBatch");
   769	    try {
   770	        shep.beginDBTransaction();
   771	        // Phase 1: walk childTaskId → parent topTask → siblings,
   772	        //         read MA.detectionStatus + child Task.status.
   773	        // Phase 2: SELECT DISTINCT eligible annotation IDs (one SQL query).
   774	        // Collect: (allTerminal: boolean, eligibleIds: Set<String>).
   775	        shep.commitDBTransaction();
   776	    } finally {
   777	        shep.rollbackAndClose();
   778	    }
   779	    // Shepherd is now CLOSED.
   780	
   781	Phase 3 (network):
   782	    OpenSearch os = new OpenSearch();
   783	    // Use callerAnnotationIds + eligibleIds for the wait set.
   784	    boolean ok = os.waitForMatchable("annotation",
   785	        union(callerAnnotationIds, eligibleIds),
   786	        method, methodVersion, VISIBILITY_TIMEOUT_MS);
   787	```
   788	
   789	No DB transaction is held during the OS poll. Matches the
   790	Phase A/B/C pattern from the WBIA register thread (commit `c6ffe5d20`).
   791	
   792	Real implementation `MatchVisibilityGateImpl` constructs Shepherd +
   793	OpenSearch as needed; takes `context` in constructor.
   794	
   795	`MlServiceProcessor` holds `private final MatchVisibilityGate

 succeeded in 202ms:
docs/plans/2026-05-18-empty-match-prospects-design.md:54:  on `QueryCacheFactory`, `MatchEligibilityQuery` extraction,
docs/plans/2026-05-18-empty-match-prospects-design.md:84:- **`MatchEligibilityQuery` extracted as package-visible class
docs/plans/2026-05-18-empty-match-prospects-design.md:423:package-visible `MatchEligibilityQuery` class under
docs/plans/2026-05-18-empty-match-prospects-design.md:430:public final class MatchEligibilityQuery {
docs/plans/2026-05-18-empty-match-prospects-design.md:436:    throws SQLException;
docs/plans/2026-05-18-empty-match-prospects-design.md:812:`MatchEligibilityQuery` utility, so it can be unit-tested with a
docs/plans/2026-05-18-empty-match-prospects-design.md:1030:- `MatchEligibilityQuery` extracted as package-visible class in
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:239:- **`MatchEligibilityQuery` extracted as package-visible class
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:578:package-visible `MatchEligibilityQuery` class under
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:585:public final class MatchEligibilityQuery {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:591:    throws SQLException;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:950:`MatchEligibilityQuery` utility, so it can be unit-tested with a
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1162:- `MatchEligibilityQuery` extracted as package-visible class in
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4625:  1002	   (a) Static method on a new `MatchEligibilityQuery` utility class.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8465:- `MatchEligibilityQuery` as package-visible in `org.ecocean.ia` is better than hiding schema-sensitive SQL inside the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:8489:- `MatchEligibilityQuery` as package-visible in `org.ecocean.ia` is better than hiding schema-sensitive SQL inside the gate.

 succeeded in 233ms:
   388	            if (ann == null || ann.getMediaAsset() == null) {
   389	                markTaskDroppedStale(shep, task, "annotation missing");
   390	                shep.commitDBTransaction();
   391	                return PersistResult.done(MlServiceJobOutcome.stale("annotation missing"));
   392	            }
   393	            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
   394	            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
   395	                markTaskCompleted(task);
   396	                shep.commitDBTransaction();
   397	                annotationIds.add(ann.getId());
   398	                return PersistResult.ok(annotationIds);
   399	            }
   400	
   401	            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   402	            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
   403	                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
   404	            shep.getPM().makePersistent(emb);
   405	            markTaskCompleted(task);
   406	            annotationIds.add(ann.getId());
   407	            shep.commitDBTransaction();
   408	            return PersistResult.ok(annotationIds);
   409	        } catch (Exception ex) {
   410	            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
   411	            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
   412	                ex.getMessage()));
   413	        } finally {
   414	            shep.rollbackAndClose();
   415	        }
   416	    }
   417	
   418	    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
   419	        JSONObject matchConfig) {
   420	        try {
   421	            OpenSearch os = new OpenSearch();
   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
   423	                enqueueDeferredMatch(annotationIds, taskId);
   424	                return MlServiceJobOutcome.ok(annotationIds);
   425	            }
   426	        } catch (IOException ex) {
   427	            enqueueDeferredMatch(annotationIds, taskId);
   428	            return MlServiceJobOutcome.ok(annotationIds);
   429	        }
   430	        return runMatchProspects(annotationIds, taskId, matchConfig);
   431	    }
   432	
   433	    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
   434	        if (jobData == null) {
   435	            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
   436	        }
   437	        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
   438	        String taskId = jobData.optString("taskId", null);

codex
**Findings**

- **Major:** [MatchEligibilityQuery.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:126) catches all execution failures, logs them, and returns the partially built/empty `Set`. For the gate, an empty set means “no eligible siblings,” so a SQL/JDO/schema failure would silently green-light matching against an incomplete corpus. That repeats the v1 “couldn’t tell vs no work” ambiguity. This should propagate or return an explicit failure outcome so C10 can defer or age out with a reason.

- **Minor/Test gap:** [MatchEligibilityQuery.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:89) builds the production SQL separately from test-only `buildSql` at [line 166](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:166). The new tests verify the duplicate builder, not necessarily the SQL that executes. Today they match, but future drift would keep tests green. Production should call the shared builder after normalizing the input.

No SQL-shape issue found in the reviewed join itself: `ANNOTATION_FEATURES.ID_OID`, `MEDIAASSET_FEATURES.ID_EID`, `MEDIAASSET_FEATURES.ID_OID`, and `EMBEDDING.ANNOTATION_ID` match the schema/precedent. Query cleanup is present via `closeAll()`.

I did not run tests or compile because this was requested as review-only/no file writes.
tokens used
81,677
**Findings**

- **Major:** [MatchEligibilityQuery.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:126) catches all execution failures, logs them, and returns the partially built/empty `Set`. For the gate, an empty set means “no eligible siblings,” so a SQL/JDO/schema failure would silently green-light matching against an incomplete corpus. That repeats the v1 “couldn’t tell vs no work” ambiguity. This should propagate or return an explicit failure outcome so C10 can defer or age out with a reason.

- **Minor/Test gap:** [MatchEligibilityQuery.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:89) builds the production SQL separately from test-only `buildSql` at [line 166](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:166). The new tests verify the duplicate builder, not necessarily the SQL that executes. Today they match, but future drift would keep tests green. Production should call the shared builder after normalizing the input.

No SQL-shape issue found in the reviewed join itself: `ANNOTATION_FEATURES.ID_OID`, `MEDIAASSET_FEATURES.ID_EID`, `MEDIAASSET_FEATURES.ID_OID`, and `EMBEDDING.ANNOTATION_ID` match the schema/precedent. Query cleanup is present via `closeAll()`.

I did not run tests or compile because this was requested as review-only/no file writes.
