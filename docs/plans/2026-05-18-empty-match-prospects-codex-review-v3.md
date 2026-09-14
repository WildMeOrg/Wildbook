OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3c83-0d01-7ab2-9604-8b27fe8988d4
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

# Design: fix empty match prospects on bulk imports (revision 3, post-Codex round 2)

## Context

On amphibian-reptile.wildbook.org (ml-service v2 live test deployment),
bulk-imported fire salamanders return 0–5 match prospects per
annotation even when the import contains 7+ same-species annotations
that should match each other. Manual OpenSearch queries a few minutes
later return all the expected hits, so the data lands correctly — but
at match-task time the candidate pool is incomplete.

This design fixes two linked bugs that together produce the empty
results. Either fix on its own leaves a residual.

The two bugs:

1. **WBIA image-then-annotation registration** — the v2 routing path
   skips legacy `WBIA.sendMediaAssets()`, so the annotation-registration
   polling thread fires `/api/annot/json/` against WBIA without WBIA
   knowing about the image. Returns HTTP 500
   `image_uuid_list has invalid values [(0, None)]`. (See existing
   paused design at `2026-05-18-wbia-image-registration-design.md`.)
2. **Match runs before sibling ml-service jobs complete and before
   their annotations are visible in OpenSearch.** Each per-image
   ml-service job calls `MlServiceProcessor.waitAndRunMatch` as soon
   as *it* persists detections; siblings may still be mid-HTTP or
   pre-persist. Even after they persist, OS indexing is async with
   its own refresh interval. The match for any one annotation runs
   against a candidate pool that doesn't yet include the rest of
   the batch.

## Revision history

**v1 → v2** (post-Codex round 1; 2 blockers, 3 majors, 1 medium):
two-phase gate (MA terminal-state + OS visibility), topTask
boundary, eligibility filter, testability seam, deferred-match
re-gating with attempt counter.

**v2 → v3** (post-Codex round 2; 1 blocker + 1 blocker/major + 3
majors + 1 medium):

- **Gate API takes `callerAnnotationIds`** (Codex round-2 Blocker).
  Pseudocode and behavioral spec aligned.
- **Terminal-status table replaced with the actual v2 constants**
  from `IBEISIA.java:73-80` (Codex round-2 Blocker/Major). Includes
  `STATUS_COMPLETE_MLSERVICE`, `STATUS_PROCESSING_MLSERVICE`,
  `STATUS_PENDING_SPECIES`, `STATUS_DROPPED_STALE` that v2 missed.
  Phase 1 now considers **both** `MediaAsset.detectionStatus` AND
  the per-MA child Task status; either being terminal-error short-
  circuits to "no annotations expected from this sibling".
- **Matchable-visibility predicate**, not just `_id` count (Codex
  round-2 Major). Phase 3 polls with the matchingSetQuery's
  eligibility predicate plus `_id` filter, so a stale doc indexed
  by id but missing nested `embeddings.method`/`methodVersion`
  cannot pass the gate.
- **Shepherd boundary spelled out** in `MatchVisibilityGateImpl`
  (Codex round-2 Major): Phase 1 + 2 run under a single Shepherd
  scope which **closes** before Phase 3's OpenSearch poll. No
  transaction held across the visibility wait.
- **Deferred backoff defined explicitly** (Codex round-2 Major):
  reuse existing `IAGateway.requeueJob` 30s fixed delay (verified
  at `IAGateway.java:785`); add `firstDeferredAt` epoch-ms in
  jobData for elapsed-time age-out; cap by elapsed time (60min)
  rather than attempt count alone.
- **Cache names corrected** (Codex round-2 Medium): existing
  `iaAnnotationIdsStrict` reuses cache key `"iaAnnotationIds"`
  (`WildbookIAM.java:478`); new strict-image variant follows the
  same pattern and shares key `"iaImageIds"`. Invalidations target
  the shared key. The WBIA doc's stale `invalidate("iaImageIds")`
  text was correct; the v2 revision's `iaImageIdsStrict` cache
  key was wrong.

## Goal

Restore the user-expected behavior: when I upload N same-species
photos and the import completes, the per-annotation match results
include the other N-1 annotations from my upload (when they match
visually) plus any pre-existing candidates from the corpus. WBIA
stays in sync as a HotSpotter fallback.

## Non-goals

- Detection-time WBIA image registration. Same rationale as the
  paused WBIA design — handling retroactively in the polling
  thread keeps the intake fast path simple.
- Restructuring ml-service to batch detection. Per-image jobs are
  the natural granularity for v2; batching belongs at the match
  invocation seam, not detection.
- Moving away from `waitForVisibility`. It works correctly — its
  call-site scope and pre-conditions are the bug, not the helper.
- A barrier task type / new dispatcher branch. We solve this at the
  existing `waitAndRunMatch` seam plus a small collaborator.

## Audit: what already exists

| Helper | File:line | Status |
|---|---|---|
| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
| `OpenSearch.indexRefresh(indexName)` | `OpenSearch.java:468` | Synchronous refresh helper. |
| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Per-job wrapper; current implementation widens nothing. Falls back to `enqueueDeferredMatch` on timeout. |
| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
| `MlServiceProcessor.runDeferredMatch(jobData)` | `MlServiceProcessor.java:433` | Today: direct `runMatchProspects`. Bug: no re-gate. |
| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues via IAGateway with `mlServiceV2DeferredMatch: true`. Today: no attempt counter. |
| `IA.enqueueOneAssetForMlService` creates per-asset `Task childTask = new Task(topTask)` | `IA.java:281-287` | Per-asset child has parent=topTask; topTask owns `objectMediaAssets` (the same-species batch). |
| `MediaAsset.getDetectionStatus()` | per JDO | Lifecycle: `_new` → `_post_new` → `processing` → `complete` \| `error` \| `pending`. |
| `Embedding.findMatchProspects(iaConfig, task, shepherd)` | `Embedding.java:265` | Per-annotation knn + matchingSetQuery on `task.getObjectAnnotations()`. |
| `QueryCache.invalidateByName(name)` | `QueryCache.java:46` | Right API for cache invalidation (not "invalidate"). |
| `WildbookIAM` helpers — `iaImageIds`, `sendMediaAssetsForceId`, `mediaAssetToUri`, `validMediaAsset` | `WildbookIAM.java` | See existing WBIA design doc. |

Note: `findMatchProspects` already loops `task.getObjectAnnotations()`
correctly — the matching itself is fine. The bug is the **gate** in
front of it.

## Design

### Track 1 — WBIA image-then-annotation registration

No structural change from
`docs/plans/2026-05-18-wbia-image-registration-design.md`. Codex
follow-ups to address before implementation:

1. `mediaAssetToUri(MediaAsset)` is `private`. Promote to
   `public static String` (returning `String`, tightening
   `ma.webURL()` null-check).
2. New strict variant `iaImageIdsStrict` with 15-min QueryCache
   (matching `iaAnnotationIdsStrict` from c11 fix-pass). Lenient
   `iaImageIds` stays cache-free for backward compatibility.
3. Phase A eligibility check gains `ma.isValidImageForIA() != null
   && !ma.isValidImageForIA()` AND `WildbookIAM.validMediaAsset(ma)`
   (in the order `sendMediaAssetsForceId` uses them at
   `WildbookIAM.java:121-130`). If either fails, park at
   `MAX_ATTEMPTS`.
4. Phase B does NOT re-validate MA eligibility. Documented as an
   explicit decision: MA validity does not change between Phase A
   commit and Phase B HTTP. One-line comment in code.
5. `parseImageIdsArrayStrict` is added alongside
   `parseAnnotationIdsArrayStrict`. A shared
   `parseFancyUuidListStrict(jids, ctxLabel)` helper extracts the
   commonality. Both go through `fromFancyUUID`.
6. No new outcome enum value. Image-registration failures reuse
   the existing `NETWORK_FAIL` outcome; Phase C log line
   distinguishes phase via existing log string (Phase 0 / 1 / 2).
7. `WbiaRegisterRequest` gains four image-side fields, captured in
   Phase A:

```java
public final String imageUri;
public final Double imageLatitude;
public final Double imageLongitude;
public final Long imageDateTimeMillis;
```

`mediaAssetUuid` is not a separate field —
`MediaAsset.acmId == MediaAsset.uuid` (commit `2a3eab63a`), so
`dto.mediaAssetAcmId` already carries the value
`sendMediaAssetsForceId` puts in `image_uuid_list`.

#### Cache-name fix (Codex round-2 Medium)

The existing `iaAnnotationIdsStrict` method reuses the lenient
variant's cache key `"iaAnnotationIds"` (see `WildbookIAM.java:478`,
verified). Strict and lenient share a single cache; the strict
variant just adds error-raising semantics on top.

The new strict-image variant follows the **same pattern**: shared
cache key `"iaImageIds"`, no separate `"iaImageIdsStrict"` key.

Invalidations target the shared keys:

- After successful image POST in Phase 0, call
  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaImageIds")`.
  `invalidateByName` is the correct API at `QueryCache.java:46`
  (NOT `invalidate(name)`).
- After successful annotation POST in Phase 1, call
  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaAnnotationIds")`,
  matching what Codex round 1 suggested — prevents stale-cache
  duplicate POSTs if Phase C races or fails between attempts.

### Track 2 — two-phase match gate

#### Boundary: per-asset Task's parent (topTask)

`IA.enqueueOneAssetForMlService` at `IA.java:281-287` creates each
per-asset job as `Task childTask = new Task(topTask)`. The
**topTask** owns `objectMediaAssets` — the same-species batch from
one routing call. This is the right boundary because:

- It's same-species (ImportTask is mixed-species).
- It's set-membership-explicit via `objectMediaAssets` (no fuzzy
  join through ENCOUNTER that catches placeholders).
- It's the batch the user submitted together; intra-batch match
  expectations align with topTask membership.

In `waitAndRunMatch`, given the child Task's id, load the child,
walk up to its parent (topTask), and read `topTask.getObjectMediaAssets()`.

#### Phase 1: sibling terminal-state gate

For each sibling MA in `topTask.getObjectMediaAssets()`, evaluate
**both** `ma.getDetectionStatus()` AND the per-MA child Task's
status (`Task.getStatus()` on the Task whose `objectMediaAssets`
contains this MA). Either being terminal is sufficient — several
failure paths in `MlServiceProcessor` set the child Task to `error`
WITHOUT advancing `MediaAsset.detectionStatus`, so MA-only
evaluation misses real terminal states.

Terminal vs non-terminal status sets, using the actual v2
constants from `IBEISIA.java:73-82`:

**Terminal** (sibling is done contributing, whether successfully or
not — proceed without waiting):

- `STATUS_COMPLETE` = `"complete"` (legacy WBIA path success)
- `STATUS_COMPLETE_MLSERVICE` = `"complete-mlservice"` (v2 success)
- `STATUS_PENDING` = `"pending"` (awaiting human review; will not
  produce more annotations without explicit action)
- `STATUS_PENDING_SPECIES` = `"pending-species"` (upload without
  taxonomy; will not auto-progress)
- `STATUS_ERROR` = `"error"`
- `STATUS_DROPPED_STALE` = `"dropped-stale"` (target deleted before
  run; terminal-drop)

**Non-terminal** (sibling is still working, wait for it):

- `STATUS_INITIATED` = `"initiated"`
- `STATUS_PROCESSING` = `"processing"`
- `STATUS_PROCESSING_MLSERVICE` = `"processing-mlservice"`
- `_new` / `_post_new` (MA lifecycle pre-processing)
- `null` (uninitialized; treat as non-terminal unless child task
  itself is terminal)

A sibling is treated as **terminal** for the gate when
`isTerminal(ma.detectionStatus) OR isTerminal(childTask.status)`.

If any sibling is non-terminal, **defer** (see deferred-path
section). Caller's match does NOT run yet.

If all siblings are terminal, proceed to Phase 2.

#### Phase 2: collect eligible annotation IDs

For each sibling MA in topTask whose detectionStatus is `complete`
(i.e., contributed annotations), enumerate annotations through
`MediaAsset → Feature → Annotation`, filtered for ml-service match
eligibility:

```sql
-- Sketch; JDOQL equivalent in code.
SELECT DISTINCT a."ID"
FROM "ANNOTATION" a
JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
JOIN "MEDIAASSET" ma ON ma."ID" = mf."ID_OID"
JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
WHERE ma."ID" IN (<topTask sibling MA ids>)
  AND a."MATCHAGAINST" = true
  AND a."ACMID" IS NOT NULL
  AND e."METHOD" = ?
  AND e."METHODVERSION" = ?
```

The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
ensures we only wait for annotations that actually have an
embedding of the model we're matching against. This excludes:
- Bulk-import placeholder annotations created by `BulkImporter`
  (no embedding at all, never indexed in OS due to
  `skipAutoIndexing`).
- Legacy annotations from a different embedding method.
- Annotations whose ml-service extraction failed
  (`predictModelId` set but `EMBEDDING` row never created).

This is a single `SELECT DISTINCT` query, no materialization of
Annotation objects. Returns a `Set<String>` of annotation IDs.

#### Phase 3: matchable-visibility wait on the eligible set

`waitForVisibility` today polls `_count` with an `ids` query
(`OpenSearch.java:521`). That confirms `_id` is in OS but does
NOT confirm the doc has its nested `embeddings.method` /
`methodVersion` indexed — a doc that's been written with the
right `_id` but stale nested fields would pass `_id`-only and
then knn-fail at match time.

Use a stricter predicate that mirrors the matching constraints
(`Annotation.getMatchQuery` at `Annotation.java:1194`). Conceptual
shape:

```json
{
  "size": 0,
  "track_total_hits": true,
  "query": {
    "bool": {
      "filter": [
        { "terms": { "_id": [ ...eligibleIds ] } },
        { "term":  { "matchAgainst": true } },
        { "exists": { "field": "acmId" } },
        { "nested": {
            "path": "embeddings",
            "query": { "bool": { "filter": [
              { "term": { "embeddings.method":        "<method>" } },
              { "term": { "embeddings.methodVersion": "<version>" } }
            ] } }
        } }
      ]
    }
  }
}
```

If `methodVersion` is null (legacy config without version), omit
the version predicate, matching `getMatchQuery`'s behavior.

Add a new method `OpenSearch.waitForMatchable(indexName, ids,
method, methodVersion, timeoutMs)` that takes the same shape as
`waitForVisibility` but uses this stricter `_count` body. Same
`_refresh`-on-entry + exponential-backoff polling pattern.

If `waitForMatchable` returns false (timeout), **defer**.

#### Phase 4: run match

`runMatchProspects(callerAnnotationIds, taskId, matchConfig)` —
**unchanged**. The caller's own annotation IDs drive the match;
the gate only ensures the OS corpus is populated.

#### Pseudocode

```java
private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
    String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {

    GateOutcome g = visibilityGate.gateForBatch(
        annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
    switch (g.kind) {
      case READY:
        return runMatchProspects(annotationIds, taskId, matchConfig);
      case DEFER:
        enqueueDeferredMatch(annotationIds, taskId, matchConfig, g);
        return MlServiceJobOutcome.ok(annotationIds);
      case GIVE_UP:
        // Bounded age-out: run match against whatever is visible
        // rather than block forever. Log WARN + mark task with status
        // detail indicating partial-batch match.
        System.out.println("WARN: MatchVisibilityGate aged out for task "
            + taskId + " after " + (g.attempt) + " attempts ("
            + g.elapsedMillis + "ms): " + g.reason);
        return runMatchProspects(annotationIds, taskId, matchConfig);
    }
}
```

`MatchVisibilityGate.gateForBatch(callerAnnotationIds, childTaskId,
matchConfig, attempt, firstDeferredAt)` returns `READY` if Phase 1
+ 2 + 3 all succeed, `DEFER` if any phase says "wait", `GIVE_UP`
if `(System.currentTimeMillis() - firstDeferredAt) > MAX_AGE_MS`.

`callerAnnotationIds` is passed in so the gate can:
- Union it into the visibility set even if the eligibility filter
  would otherwise exclude it (caller's match must not block on its
  own visibility).
- Degrade cleanly when the child task has no parent topTask
  (orphan child case): wait only on callerAnnotationIds.

#### Deferred-match path (`runDeferredMatch`) and backoff

Today at `MlServiceProcessor.java:433-442`, `runDeferredMatch`
reads `annotationIds` + `taskId` + `matchConfig` from the job
payload and calls `runMatchProspects` directly — no re-gate.

The existing requeue mechanism is `IAGateway.requeueJob` at
`IAGateway.java:751-810`. Verified behavior:

- 30s fixed delay between requeues (line 785: `whileSleepMillis = 30000`).
- Tracks `__queueRetries`, `__queueActualRetries`, `__queueStart`
  on the job JSON.
- Hard caps at `MAX_RETRIES=30` retries OR `MAX_TIME_MILLIS = 2
  days` elapsed.
- mlServiceV2 jobs land back on the detection queue (line 792-797).

For the deferred-match path we reuse this 30s fixed delay (no
custom backoff). What we add:

- **`firstDeferredAt`** epoch-ms in the jobData payload, recorded
  on the first DEFER. Used by `gateForBatch` to compute elapsed
  age; `GIVE_UP` fires when elapsed > `MAX_DEFER_AGE_MILLIS`.
- **`attempt`** counter, incremented per DEFER. Logged for
  diagnostics; not the cap (elapsed-time is the cap, per Codex
  round-2 open-question #1).
- **`lastGateReason`** string in jobData, written by `gateForBatch`
  on DEFER. Useful when ops investigate why a match was deferred
  (e.g., "sibling MA 2/4 still processing-mlservice").

```java
private static final long MAX_DEFER_AGE_MILLIS = 60L * 60L * 1000L;
// One hour from first deferral. Most v2 imports complete sibling
// detection in <2min; 1 hour is conservative slack for a stuck
// sibling without unboundedly waiting.

public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
    if (jobData == null) return MlServiceJobOutcome.validationError(...);
    List<String> annotationIds = jsonArrayToStringList(
        jobData.optJSONArray("annotationIds"));
    String taskId = jobData.optString("taskId", null);
    JSONObject matchConfig = jobData.optJSONObject("matchConfig");
    if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
    int attempt = jobData.optInt("attempt", 1);
    Long firstDeferredAt = jobData.has("firstDeferredAt")
        ? jobData.optLong("firstDeferredAt") : null;
    // re-gate; deferred match earns the same protection as initial.
    return waitAndRunMatch(annotationIds, taskId, matchConfig,
        attempt, firstDeferredAt);
}
```

`enqueueDeferredMatch` is extended to take the gate outcome so
it can carry `attempt + 1`, `firstDeferredAt` (set on first call,
preserved on subsequent), and `lastGateReason` into the new
jobData. The actual fire-after-delay mechanism is the existing
`requeueJob` path — we publish the new jobData with
`__queueRetries` set so requeueJob applies its 30s delay.

The age-out at `elapsed > MAX_DEFER_AGE_MILLIS` is a degraded
outcome (matches against whatever is visible) but bounded so a
stuck sibling cannot block the user's match indefinitely.

#### Testability seam — `MatchVisibilityGate`

Today `waitAndRunMatch` is private and constructs `new OpenSearch()`
directly, and `enqueueDeferredMatch` is private. Codex review item
5 calls this out: mock/wrap unit tests are not feasible cleanly
without changing the seam.

Extract:

```java
package org.ecocean.ia;

public interface MatchVisibilityGate {
    enum Kind { READY, DEFER, GIVE_UP }

    static class GateOutcome {
        public final Kind kind;
        public final int attempt;            // current attempt number (1-based)
        public final long firstDeferredAt;   // epoch-ms of first DEFER (or now for first call)
        public final long elapsedMillis;     // System.currentTimeMillis() - firstDeferredAt
        public final String reason;          // human-readable, written to jobData.lastGateReason
        // factory methods: ready(attempt, firstDeferredAt),
        //                  defer(attempt, firstDeferredAt, reason),
        //                  giveUp(attempt, firstDeferredAt, reason)
    }

    GateOutcome gateForBatch(
        Collection<String> callerAnnotationIds,
        String childTaskId,
        JSONObject matchConfig,
        int attempt,
        Long firstDeferredAt);
}
```

#### Shepherd boundary in `MatchVisibilityGateImpl`

Explicit phase-locality, per Codex round-2 Major:

```
Phase 1 + 2 (read DB):
    Shepherd shep = new Shepherd(context);
    shep.setAction(ACTION_PREFIX + "gateForBatch");
    try {
        shep.beginDBTransaction();
        // Phase 1: walk childTaskId → parent topTask → siblings,
        //         read MA.detectionStatus + child Task.status.
        // Phase 2: SELECT DISTINCT eligible annotation IDs (one SQL query).
        // Collect: (allTerminal: boolean, eligibleIds: Set<String>).
        shep.commitDBTransaction();
    } finally {
        shep.rollbackAndClose();
    }
    // Shepherd is now CLOSED.

Phase 3 (network):
    OpenSearch os = new OpenSearch();
    // Use callerAnnotationIds + eligibleIds for the wait set.
    boolean ok = os.waitForMatchable("annotation",
        union(callerAnnotationIds, eligibleIds),
        method, methodVersion, VISIBILITY_TIMEOUT_MS);
```

No DB transaction is held during the OS poll. Matches the
Phase A/B/C pattern from the WBIA register thread (commit `c6ffe5d20`).

Real implementation `MatchVisibilityGateImpl` constructs Shepherd +
OpenSearch as needed; takes `context` in constructor.

`MlServiceProcessor` holds `private final MatchVisibilityGate
visibilityGate`. Default constructor uses
`MatchVisibilityGateImpl`; a package-visible constructor accepts
an injected gate for tests.

Unit tests can now:
- Stub `gateForBatch` to return READY → assert `runMatchProspects` is
  called with caller's IDs.
- Stub `gateForBatch` to return DEFER → assert `enqueueDeferredMatch`
  is called with attempt counter.
- Stub `gateForBatch` to return GIVE_UP → assert match runs anyway
  (age-out behavior).
- Test the gate implementation separately with a mock OpenSearch
  + Shepherd-test-double.

Also: extract the JDOQL "find eligible annotations for a topTask"
into a package-visible static method on `Annotation` or a new
`MatchEligibilityQuery` utility, so it can be unit-tested with a
DataNucleus in-memory store (the v2 pattern from c8/c9 tests).

#### Behavior in edge cases

- **Child Task has no parent (orphaned single-asset job from
  reconciler)**: gate degrades to "no batch to wait for; just OS
  visibility on caller's IDs". Equivalent to today's behavior for
  this path. No regression.
- **Sibling MA permanently stuck in `processing`**: bounded by
  `MAX_DEFER_ATTEMPTS`. Age-out after ~50min (10 attempts ×
  growing delay). User's match runs against whatever's visible.
- **All siblings are `error` / `zero_detections`**: Phase 2's
  eligibility filter returns the empty set or only the caller's
  own annotations. `waitForVisibility` on empty set should be
  short-circuit (or no-op); confirm behavior in tests.
- **Two parallel users with overlapping imports**: topTask is
  per-routing-call, so each user's batch is its own topTask.
  Boundary is clean.
- **Caller's annotation isn't in the eligible set** (somehow):
  union it in. The gate should never block the caller's match on
  its own visibility — the caller's annotation went through
  `waitForVisibility` in the old single-annotation path
  effectively; we maintain that property by always including
  caller's IDs in the visibility set, eligibility filter or not.

## Interaction between Track 1 and Track 2

The two tracks are independent in code (different files, different
review chains) but linked in symptom. Track 1 fixes acmId/HotSpotter
sync — annotations no longer linger with placeholder acmIds in OS.
Track 2 fixes the timing where annotations are visible in DB but
not yet in OS at match time.

Specifically: today, a newly-imported annotation gets a placeholder
acmId (its own UUID), gets indexed into OS that way, then later
`AcmUtil.rectifyAnnotationIds` mutates it to WBIA's UUID and
re-indexes. Match tasks firing between initial index and rectify
reindex see the placeholder acmId — which still passes the
`exists: {field: acmId}` filter, so it's not a filter regression.
Track 1's effect on the empty-prospects symptom is indirect (via
HotSpotter fallback availability and operational hygiene). Track 2
is the load-bearing fix for the empty-prospects symptom.

## Order of work

1. **Track 1**: resolve the existing WBIA design doc's Codex
   follow-ups (items 1–7 above + cache-name fix), implement, code-review
   pass.
2. **Track 2**: implement after re-reviewing this design with Codex.

Track 2 is larger than v1's estimate (~150 lines instead of ~80
once the gate, helpers, and attempt counter are included). Two
commits within Track 2 is reasonable: one for the gate +
collaborator extraction, one for the deferred-match path + age-out.

## Test strategy

### Track 1

`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
behavior, strict-cache invalidation, the strict parser, DTO image
fields. Per existing WBIA design.

### Track 2

New unit tests in `MatchVisibilityGateImplTest` and updates to
`MlServiceProcessorTest`. Grouped by Codex-round-2 finding to
make traceability explicit.

**Gate API + caller-ids (Codex round-2 Blocker):**

- `gate_acceptsCallerAnnotationIds_inSignature` — compile-time
  contract; gate API takes `Collection<String>`.
- `gate_unionsCallerIds_intoVisibilitySet` — caller's annotation
  always present in the wait set even if eligibility filter
  excluded it.
- `gate_handlesOrphanChildTaskWithoutParent` — child task with no
  parent; gate degrades to waiting on caller IDs only.

**Terminal-status set (Codex round-2 Blocker/Major):**

- `gate_treatsCompleteMlservice_asTerminal`
- `gate_treatsPendingSpecies_asTerminal`
- `gate_treatsDroppedStale_asTerminal`
- `gate_treatsProcessingMlservice_asNonTerminal`
- `gate_treatsInitiated_asNonTerminal`
- `gate_treatsNullMaStatus_butChildTaskErrored_asTerminal` —
  the case where MA status didn't advance but child Task did.
- `gate_treatsBothMaAndChildTaskNonTerminal_asNonTerminal`

**Matchable visibility (Codex round-2 Major):**

- `waitForMatchable_returnsFalse_whenDocsByIdButMissingEmbedding` —
  index a doc with matching `_id` but no nested
  `embeddings.method`; prove it's filtered out.
- `waitForMatchable_returnsFalse_whenAcmIdMissing`
- `waitForMatchable_returnsFalse_whenMatchAgainstFalse`
- `waitForMatchable_returnsTrue_whenFullyMatchable`
- `waitForMatchable_omitsVersionPredicate_whenMethodVersionNull` —
  matches `getMatchQuery` behavior.
- `waitForMatchable_shortCircuits_onEmptyIdSet`

**Shepherd boundary (Codex round-2 Major):**

- `gateImpl_closesShepherdBeforeOsCall` — verify with a stubbed
  OS client that the Shepherd was closed before
  `waitForMatchable` was invoked. (Mock-friendly check.)

**Deferred backoff (Codex round-2 Major):**

- `deferred_recordsFirstDeferredAt_onFirstDefer`
- `deferred_preservesFirstDeferredAt_acrossRedeferral`
- `deferred_agesOut_whenElapsedExceedsMaxAge`
- `deferred_incrementsAttempt_perDeferral` — diagnostic only;
  doesn't drive age-out.
- `deferred_writesLastGateReason_intoJobData`
- `enqueueDeferredMatch_publishesViaRequeueJob_with30sDelay` —
  observe `__queueRetries` set, sleep mechanism invoked.

**Bulk-import placeholder exclusion (Codex round-1 Blocker 2):**

- `gate_excludesPlaceholderAnnotationsWithSkipAutoIndexing` — set
  up an annotation in `BulkImporter`'s placeholder state
  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
  filtered out by the embedding-method predicate.
- `gate_excludesAnnotationsWithoutAcmId`
- `gate_excludesAnnotationsWithMatchAgainstFalse`

**Processor wiring:**

- `processor_callsRunMatchProspects_onlyAfterGateReady`
- `processor_callsEnqueueDeferredMatch_onGateDefer`
- `processor_runsMatchAnyway_onGateGiveUp_withWarnLog`
- `runDeferredMatch_reGatesBeforeRunning`
- `runDeferredMatch_agesOutAtMaxAge`

**End-to-end on live deployment:** import 4+ same-species images,
observe that each annotation's `findMatchProspects` result
includes the other 3 as candidates. Same fire-salamander test
case that surfaced this bug.

## Open questions for Codex (round 3)

All round-2 open questions have been resolved per Codex's
answers and incorporated into the design above:

- Cap by elapsed time (`MAX_DEFER_AGE_MILLIS = 1h`), not attempt
  count (round-2 #1).
- Age-out posture: run match anyway with WARN + status detail
  (round-2 #2).
- Terminal-status set: full v2 constants table above; child task
  status checked alongside MA status (round-2 #3).
- `skipAutoIndexing`: no special handling beyond the existing
  WARN at `waitForVisibility` entry (round-2 #4).
- Direct SQL for eligibility query (round-2 #5).
- Job payload for attempt counter + `firstDeferredAt` +
  `lastGateReason` (round-2 #6).

Remaining open questions:

1. **`waitForMatchable` is a new public method on `OpenSearch`.**
   Does it belong on `OpenSearch.java` alongside
   `waitForVisibility`, or in the gate impl as a private helper?
   I lean toward `OpenSearch.java` — it's a general visibility
   primitive that any future caller wanting "matchable" semantics
   could reuse. Flag if the surface area is wrong.
2. **Phase 2 eligibility SQL handles `methodVersion=null` by
   omitting the predicate.** Same as `getMatchQuery`. But: if the
   matchConfig provides a non-null version, an annotation that
   has the same method at a different version is still ineligible.
   My draft uses `EMBEDDING.METHODVERSION = ?` strictly — Codex
   answer to round-2 #5 says match `getMatchQuery` behavior. I
   read `getMatchQuery` at `Annotation.java:1205-1209` as
   strict-version when version is non-null. Confirm I read it
   right.
3. **`waitForVisibility` vs `waitForMatchable` naming.** Open to
   a clearer name (e.g., `waitForMatchableIds`?
   `waitForIndexed`?). Naming bikeshed — happy with either.
4. **Empty `eligibleIds`** (all siblings errored / zero
   detections): `waitForMatchable` on an empty set should short-
   circuit to true (nothing to wait for). Confirm this is the
   right semantics for the new helper and not a misuse pattern.
5. **`MAX_DEFER_AGE_MILLIS = 1h` reasonable?** For typical 4–20
   image imports, sibling detections complete in ~10–60s. 1h is
   ~60× normal. Codex round-2 didn't specify a number for me;
   open to a different default.
6. **Anything else** — design smells, sequencing bugs, test
   gaps, missing failure cases.

## Cross-references

- v1 of this design (superseded): see git history for
  `2026-05-18-empty-match-prospects-design.md`.
- Codex review of v1:
  `docs/plans/2026-05-18-empty-match-prospects-codex-review.md`.
- Existing WBIA registration design:
  `docs/plans/2026-05-18-wbia-image-registration-design.md`.
- ml-service migration v2 plan:
  `docs/plans/2026-05-09-ml-service-migration-v2.md`.
- waitForVisibility introduction commit: `f429c5bf8` (c7).
- WBIA registration polling thread origin: `c6ffe5d20` (c11).
- Key helpers:
  - `MlServiceProcessor.waitAndRunMatch` —
    `src/main/java/org/ecocean/ia/MlServiceProcessor.java:418`
  - `MlServiceProcessor.runDeferredMatch` —
    `src/main/java/org/ecocean/ia/MlServiceProcessor.java:433`
  - `OpenSearch.waitForVisibility` —
    `src/main/java/org/ecocean/OpenSearch.java:498`
  - `IA.enqueueOneAssetForMlService` (topTask boundary) —
    `src/main/java/org/ecocean/ia/IA.java:281`
  - `QueryCache.invalidateByName` —
    `src/main/java/org/ecocean/cache/QueryCache.java:46`
  - `Annotation.getMatchingSetQuery` (eligibility alignment) —
    `src/main/java/org/ecocean/Annotation.java:925`
  - `Embedding.findMatchProspects` —
    `src/main/java/org/ecocean/Embedding.java:265`
  - `BulkImporter` placeholder annotations
    (`skipAutoIndexing=true`) —
    `src/main/java/org/ecocean/api/bulk/BulkImporter.java:703`

**Do not write to any file when Codex reviews this.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll keep this review read-only and check the design against the current branch code paths it depends on: status constants, task/media relationships, OpenSearch query shape, and deferred queue behavior.
exec
/bin/bash -lc 'git rev-parse --abbrev-ref HEAD && git log --oneline -5' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short --branch' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "STATUS_(COMPLETE|COMPLETE_MLSERVICE|PROCESSING_MLSERVICE|PENDING_SPECIES|DROPPED_STALE|INITIATED|PROCESSING|PENDING|ERROR)|waitAndRunMatch|runDeferredMatch|enqueueDeferredMatch|waitForVisibility|indexRefresh|waitForMatchable|enqueueOneAssetForMlService|iaAnnotationIdsStrict|iaImageIds|invalidateByName|getMatchingSetQuery|findMatchProspects|skipAutoIndexing|requeueJob|MAX_RETRIES|MAX_TIME_MILLIS" src/main/java docs/plans' in /mnt/c/Wildbook-clean2
 succeeded in 1020ms:
src/main/java/org/ecocean/WildbookLifecycleListener.java:43:        if (OpenSearch.skipAutoIndexing()) {
src/main/java/org/ecocean/WildbookLifecycleListener.java:44:            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-wbia-image-registration-design.md:111:Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-wbia-image-registration-design.md:199:    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-wbia-image-registration-design.md:261:  iaAnnotationIdsStrict called after (Phase 1 reached).
docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
src/main/java/org/ecocean/Encounter.java:5493:                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/Encounter.java:5534:                    ma.setDetectionStatus(hasConfig ? IBEISIA.STATUS_INITIATED : IBEISIA.STATUS_COMPLETE);
docs/plans/2026-05-18-empty-match-prospects-design.md:25:   ml-service job calls `MlServiceProcessor.waitAndRunMatch` as soon
docs/plans/2026-05-18-empty-match-prospects-design.md:46:  `STATUS_COMPLETE_MLSERVICE`, `STATUS_PROCESSING_MLSERVICE`,
docs/plans/2026-05-18-empty-match-prospects-design.md:47:  `STATUS_PENDING_SPECIES`, `STATUS_DROPPED_STALE` that v2 missed.
docs/plans/2026-05-18-empty-match-prospects-design.md:61:  reuse existing `IAGateway.requeueJob` 30s fixed delay (verified
docs/plans/2026-05-18-empty-match-prospects-design.md:66:  `iaAnnotationIdsStrict` reuses cache key `"iaAnnotationIds"`
docs/plans/2026-05-18-empty-match-prospects-design.md:68:  same pattern and shares key `"iaImageIds"`. Invalidations target
docs/plans/2026-05-18-empty-match-prospects-design.md:69:  the shared key. The WBIA doc's stale `invalidate("iaImageIds")`
docs/plans/2026-05-18-empty-match-prospects-design.md:70:  text was correct; the v2 revision's `iaImageIdsStrict` cache
docs/plans/2026-05-18-empty-match-prospects-design.md:89:- Moving away from `waitForVisibility`. It works correctly — its
docs/plans/2026-05-18-empty-match-prospects-design.md:92:  existing `waitAndRunMatch` seam plus a small collaborator.
docs/plans/2026-05-18-empty-match-prospects-design.md:98:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-design.md:99:| `OpenSearch.indexRefresh(indexName)` | `OpenSearch.java:468` | Synchronous refresh helper. |
docs/plans/2026-05-18-empty-match-prospects-design.md:100:| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Per-job wrapper; current implementation widens nothing. Falls back to `enqueueDeferredMatch` on timeout. |
docs/plans/2026-05-18-empty-match-prospects-design.md:101:| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
docs/plans/2026-05-18-empty-match-prospects-design.md:102:| `MlServiceProcessor.runDeferredMatch(jobData)` | `MlServiceProcessor.java:433` | Today: direct `runMatchProspects`. Bug: no re-gate. |
docs/plans/2026-05-18-empty-match-prospects-design.md:103:| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues via IAGateway with `mlServiceV2DeferredMatch: true`. Today: no attempt counter. |
docs/plans/2026-05-18-empty-match-prospects-design.md:104:| `IA.enqueueOneAssetForMlService` creates per-asset `Task childTask = new Task(topTask)` | `IA.java:281-287` | Per-asset child has parent=topTask; topTask owns `objectMediaAssets` (the same-species batch). |
docs/plans/2026-05-18-empty-match-prospects-design.md:106:| `Embedding.findMatchProspects(iaConfig, task, shepherd)` | `Embedding.java:265` | Per-annotation knn + matchingSetQuery on `task.getObjectAnnotations()`. |
docs/plans/2026-05-18-empty-match-prospects-design.md:107:| `QueryCache.invalidateByName(name)` | `QueryCache.java:46` | Right API for cache invalidation (not "invalidate"). |
docs/plans/2026-05-18-empty-match-prospects-design.md:108:| `WildbookIAM` helpers — `iaImageIds`, `sendMediaAssetsForceId`, `mediaAssetToUri`, `validMediaAsset` | `WildbookIAM.java` | See existing WBIA design doc. |
docs/plans/2026-05-18-empty-match-prospects-design.md:110:Note: `findMatchProspects` already loops `task.getObjectAnnotations()`
docs/plans/2026-05-18-empty-match-prospects-design.md:125:2. New strict variant `iaImageIdsStrict` with 15-min QueryCache
docs/plans/2026-05-18-empty-match-prospects-design.md:126:   (matching `iaAnnotationIdsStrict` from c11 fix-pass). Lenient
docs/plans/2026-05-18-empty-match-prospects-design.md:127:   `iaImageIds` stays cache-free for backward compatibility.
docs/plans/2026-05-18-empty-match-prospects-design.md:160:The existing `iaAnnotationIdsStrict` method reuses the lenient
docs/plans/2026-05-18-empty-match-prospects-design.md:166:cache key `"iaImageIds"`, no separate `"iaImageIdsStrict"` key.
docs/plans/2026-05-18-empty-match-prospects-design.md:171:  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaImageIds")`.
docs/plans/2026-05-18-empty-match-prospects-design.md:172:  `invalidateByName` is the correct API at `QueryCache.java:46`
docs/plans/2026-05-18-empty-match-prospects-design.md:175:  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaAnnotationIds")`,
docs/plans/2026-05-18-empty-match-prospects-design.md:183:`IA.enqueueOneAssetForMlService` at `IA.java:281-287` creates each
docs/plans/2026-05-18-empty-match-prospects-design.md:194:In `waitAndRunMatch`, given the child Task's id, load the child,
docs/plans/2026-05-18-empty-match-prospects-design.md:213:- `STATUS_COMPLETE` = `"complete"` (legacy WBIA path success)
docs/plans/2026-05-18-empty-match-prospects-design.md:214:- `STATUS_COMPLETE_MLSERVICE` = `"complete-mlservice"` (v2 success)
docs/plans/2026-05-18-empty-match-prospects-design.md:215:- `STATUS_PENDING` = `"pending"` (awaiting human review; will not
docs/plans/2026-05-18-empty-match-prospects-design.md:217:- `STATUS_PENDING_SPECIES` = `"pending-species"` (upload without
docs/plans/2026-05-18-empty-match-prospects-design.md:219:- `STATUS_ERROR` = `"error"`
docs/plans/2026-05-18-empty-match-prospects-design.md:220:- `STATUS_DROPPED_STALE` = `"dropped-stale"` (target deleted before
docs/plans/2026-05-18-empty-match-prospects-design.md:225:- `STATUS_INITIATED` = `"initiated"`
docs/plans/2026-05-18-empty-match-prospects-design.md:226:- `STATUS_PROCESSING` = `"processing"`
docs/plans/2026-05-18-empty-match-prospects-design.md:227:- `STATUS_PROCESSING_MLSERVICE` = `"processing-mlservice"`
docs/plans/2026-05-18-empty-match-prospects-design.md:267:  `skipAutoIndexing`).
docs/plans/2026-05-18-empty-match-prospects-design.md:277:`waitForVisibility` today polls `_count` with an `ids` query
docs/plans/2026-05-18-empty-match-prospects-design.md:314:Add a new method `OpenSearch.waitForMatchable(indexName, ids,
docs/plans/2026-05-18-empty-match-prospects-design.md:316:`waitForVisibility` but uses this stricter `_count` body. Same
docs/plans/2026-05-18-empty-match-prospects-design.md:319:If `waitForMatchable` returns false (timeout), **defer**.
docs/plans/2026-05-18-empty-match-prospects-design.md:330:private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-design.md:339:        enqueueDeferredMatch(annotationIds, taskId, matchConfig, g);
docs/plans/2026-05-18-empty-match-prospects-design.md:365:#### Deferred-match path (`runDeferredMatch`) and backoff
docs/plans/2026-05-18-empty-match-prospects-design.md:367:Today at `MlServiceProcessor.java:433-442`, `runDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-design.md:371:The existing requeue mechanism is `IAGateway.requeueJob` at
docs/plans/2026-05-18-empty-match-prospects-design.md:377:- Hard caps at `MAX_RETRIES=30` retries OR `MAX_TIME_MILLIS = 2
docs/plans/2026-05-18-empty-match-prospects-design.md:400:public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
docs/plans/2026-05-18-empty-match-prospects-design.md:411:    return waitAndRunMatch(annotationIds, taskId, matchConfig,
docs/plans/2026-05-18-empty-match-prospects-design.md:416:`enqueueDeferredMatch` is extended to take the gate outcome so
docs/plans/2026-05-18-empty-match-prospects-design.md:420:`requeueJob` path — we publish the new jobData with
docs/plans/2026-05-18-empty-match-prospects-design.md:421:`__queueRetries` set so requeueJob applies its 30s delay.
docs/plans/2026-05-18-empty-match-prospects-design.md:429:Today `waitAndRunMatch` is private and constructs `new OpenSearch()`
docs/plans/2026-05-18-empty-match-prospects-design.md:430:directly, and `enqueueDeferredMatch` is private. Codex review item
docs/plans/2026-05-18-empty-match-prospects-design.md:485:    boolean ok = os.waitForMatchable("annotation",
docs/plans/2026-05-18-empty-match-prospects-design.md:504:- Stub `gateForBatch` to return DEFER → assert `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-design.md:527:  own annotations. `waitForVisibility` on empty set should be
docs/plans/2026-05-18-empty-match-prospects-design.md:535:  `waitForVisibility` in the old single-annotation path
docs/plans/2026-05-18-empty-match-prospects-design.md:606:- `waitForMatchable_returnsFalse_whenDocsByIdButMissingEmbedding` —
docs/plans/2026-05-18-empty-match-prospects-design.md:609:- `waitForMatchable_returnsFalse_whenAcmIdMissing`
docs/plans/2026-05-18-empty-match-prospects-design.md:610:- `waitForMatchable_returnsFalse_whenMatchAgainstFalse`
docs/plans/2026-05-18-empty-match-prospects-design.md:611:- `waitForMatchable_returnsTrue_whenFullyMatchable`
docs/plans/2026-05-18-empty-match-prospects-design.md:612:- `waitForMatchable_omitsVersionPredicate_whenMethodVersionNull` —
docs/plans/2026-05-18-empty-match-prospects-design.md:614:- `waitForMatchable_shortCircuits_onEmptyIdSet`
docs/plans/2026-05-18-empty-match-prospects-design.md:620:  `waitForMatchable` was invoked. (Mock-friendly check.)
docs/plans/2026-05-18-empty-match-prospects-design.md:630:- `enqueueDeferredMatch_publishesViaRequeueJob_with30sDelay` —
docs/plans/2026-05-18-empty-match-prospects-design.md:637:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
docs/plans/2026-05-18-empty-match-prospects-design.md:647:- `runDeferredMatch_reGatesBeforeRunning`
docs/plans/2026-05-18-empty-match-prospects-design.md:648:- `runDeferredMatch_agesOutAtMaxAge`
docs/plans/2026-05-18-empty-match-prospects-design.md:651:observe that each annotation's `findMatchProspects` result
docs/plans/2026-05-18-empty-match-prospects-design.md:666:- `skipAutoIndexing`: no special handling beyond the existing
docs/plans/2026-05-18-empty-match-prospects-design.md:667:  WARN at `waitForVisibility` entry (round-2 #4).
docs/plans/2026-05-18-empty-match-prospects-design.md:674:1. **`waitForMatchable` is a new public method on `OpenSearch`.**
docs/plans/2026-05-18-empty-match-prospects-design.md:676:   `waitForVisibility`, or in the gate impl as a private helper?
docs/plans/2026-05-18-empty-match-prospects-design.md:689:3. **`waitForVisibility` vs `waitForMatchable` naming.** Open to
docs/plans/2026-05-18-empty-match-prospects-design.md:690:   a clearer name (e.g., `waitForMatchableIds`?
docs/plans/2026-05-18-empty-match-prospects-design.md:693:   detections): `waitForMatchable` on an empty set should short-
docs/plans/2026-05-18-empty-match-prospects-design.md:713:- waitForVisibility introduction commit: `f429c5bf8` (c7).
docs/plans/2026-05-18-empty-match-prospects-design.md:716:  - `MlServiceProcessor.waitAndRunMatch` —
docs/plans/2026-05-18-empty-match-prospects-design.md:718:  - `MlServiceProcessor.runDeferredMatch` —
docs/plans/2026-05-18-empty-match-prospects-design.md:720:  - `OpenSearch.waitForVisibility` —
docs/plans/2026-05-18-empty-match-prospects-design.md:722:  - `IA.enqueueOneAssetForMlService` (topTask boundary) —
docs/plans/2026-05-18-empty-match-prospects-design.md:724:  - `QueryCache.invalidateByName` —
docs/plans/2026-05-18-empty-match-prospects-design.md:726:  - `Annotation.getMatchingSetQuery` (eligibility alignment) —
docs/plans/2026-05-18-empty-match-prospects-design.md:728:  - `Embedding.findMatchProspects` —
docs/plans/2026-05-18-empty-match-prospects-design.md:731:    (`skipAutoIndexing=true`) —
src/main/java/org/ecocean/Embedding.java:265:    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
src/main/java/org/ecocean/Embedding.java:281:        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
src/main/java/org/ecocean/Embedding.java:302:                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:320:            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
src/main/java/org/ecocean/Embedding.java:322:            // getMatchingSetQuery can return null (e.g. encounter missing,
src/main/java/org/ecocean/Embedding.java:326:                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
src/main/java/org/ecocean/Embedding.java:345:                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
src/main/java/org/ecocean/Embedding.java:371:            System.out.println("findMatchProspects() on " + ann + " found " +
src/main/java/org/ecocean/Embedding.java:378:                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
src/main/java/org/ecocean/Embedding.java:381:                System.out.println("findMatchProspects() MatchResult creation failed on " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:88:`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh boundary;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:94:`waitForVisibility` was added in commit `f429c5bf8` (c7 of v2) as
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:98:`skipAutoIndexing` (controlled by `/tmp/skipAutoIndexing`) makes
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:102:## `/tmp/skipAutoIndexing` and test mode
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:104:If `/tmp/skipAutoIndexing` exists, IndexingManager skips OS indexing
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:136:`Embedding.findMatchProspects` gates entry on
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:152:   `iaImageIds` swallows errors; strict variant raises so calls
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:200:   ml-service job calls `MlServiceProcessor.waitAndRunMatch` as soon
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:218:  parent** (`topTask` from `IA.enqueueOneAssetForMlService`,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:221:  annotations with `skipAutoIndexing=true`. topTask is one
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:228:- **`runDeferredMatch` reuses the same gate** with an attempt
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:233:- **Track 1 cache-naming cleanup**: pick `iaImageIdsStrict`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:253:- Moving away from `waitForVisibility`. It works correctly — its
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:256:  existing `waitAndRunMatch` seam plus a small collaborator.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:262:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:263:| `OpenSearch.indexRefresh(indexName)` | `OpenSearch.java:468` | Synchronous refresh helper. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:264:| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Per-job wrapper; current implementation widens nothing. Falls back to `enqueueDeferredMatch` on timeout. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:265:| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:266:| `MlServiceProcessor.runDeferredMatch(jobData)` | `MlServiceProcessor.java:433` | Today: direct `runMatchProspects`. Bug: no re-gate. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:267:| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues via IAGateway with `mlServiceV2DeferredMatch: true`. Today: no attempt counter. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:268:| `IA.enqueueOneAssetForMlService` creates per-asset `Task childTask = new Task(topTask)` | `IA.java:281-287` | Per-asset child has parent=topTask; topTask owns `objectMediaAssets` (the same-species batch). |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:270:| `Embedding.findMatchProspects(iaConfig, task, shepherd)` | `Embedding.java:265` | Per-annotation knn + matchingSetQuery on `task.getObjectAnnotations()`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:271:| `QueryCache.invalidateByName(name)` | `QueryCache.java:46` | Right API for cache invalidation (not "invalidate"). |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:272:| `WildbookIAM` helpers — `iaImageIds`, `sendMediaAssetsForceId`, `mediaAssetToUri`, `validMediaAsset` | `WildbookIAM.java` | See existing WBIA design doc. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:274:Note: `findMatchProspects` already loops `task.getObjectAnnotations()`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:289:2. New strict variant `iaImageIdsStrict` with 15-min QueryCache
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:290:   (matching `iaAnnotationIdsStrict` from c11 fix-pass). Lenient
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:291:   `iaImageIds` stays cache-free for backward compatibility.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:325:  **`iaImageIdsStrict`** as the strict cache key.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:327:  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaImageIdsStrict")`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:329:- Also invalidate `iaAnnotationIdsStrict` after successful
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:337:`IA.enqueueOneAssetForMlService` at `IA.java:281-287` creates each
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:348:In `waitAndRunMatch`, given the child Task's id, load the child,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:366:`enqueueDeferredMatch` with an incremented attempt counter (see
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:399:  `skipAutoIndexing`).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:409:`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:423:private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:431:        enqueueDeferredMatch(annotationIds, taskId, g.attempt);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:446:#### Deferred-match path (`runDeferredMatch`)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:449:`runDeferredMatch` reads `annotationIds` + `taskId` + `matchConfig`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:454:public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:465:    return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, attempt);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:469:`enqueueDeferredMatch` is extended to take an `attempt` parameter,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:484:Today `waitAndRunMatch` is private and constructs `new OpenSearch()`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:485:directly, and `enqueueDeferredMatch` is private. Codex review item
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:520:- Stub `gateForBatch` to return DEFER → assert `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:543:  own annotations. `waitForVisibility` on empty set should be
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:551:  `waitForVisibility` in the old single-annotation path
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:618:- `runDeferredMatch_reGatesBeforeRunning`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:619:- `runDeferredMatch_agesOutAtMaxAttempts`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:620:- `enqueueDeferredMatch_writesAttemptCounterToJobPayload`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:624:`findMatchProspects` result includes the other 3 as candidates.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:643:4. **Should the gate also wait for `skipAutoIndexing` to be off**
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:646:   where the user toggles `skipAutoIndexing` mid-import, we could
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:674:- waitForVisibility introduction commit: `f429c5bf8` (c7).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:677:  - `MlServiceProcessor.waitAndRunMatch` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:679:  - `MlServiceProcessor.runDeferredMatch` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:681:  - `OpenSearch.waitForVisibility` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:683:  - `IA.enqueueOneAssetForMlService` (topTask boundary) —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:685:  - `QueryCache.invalidateByName` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:687:  - `Annotation.getMatchingSetQuery` (eligibility alignment) —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:689:  - `Embedding.findMatchProspects` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:692:    (`skipAutoIndexing=true`) —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:715:   246	            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:750:   281	    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:785:   316	            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:813:   401	            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:830:   418	    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:834:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:835:   423	                enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:839:   427	            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:845:   433	    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:884:   472	            // findMatchProspects returns false when the match config is not
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:887:   475	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:891:   479	                    "findMatchProspects rejected match config: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:909:   497	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1037:   625	        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1038:   626	        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1075:   663	        task.setStatus(IBEISIA.STATUS_ERROR);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1088:   676	        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1093:   681	    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1107:   695	            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1172:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1173:   468	    public void indexRefresh(final String indexName)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1183:   478	    // (commit #9) post-persist to avoid running findMatchProspects against an
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1191:   486	    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1203:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1218:   513	        if (skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1220:   515	                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1224:   519	        indexRefresh(indexName);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1649:I’ve confirmed the branch and current match path: the existing code only waits on the caller annotation IDs, and `runDeferredMatch` bypasses that wait entirely. The worktree is very dirty already, so I’m keeping this strictly to reads and will not touch files.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1657:/bin/bash -lc 'rg -n "STATUS_.*(PENDING|PROCESS|COMPLETE|ERROR|NEW)|STATUS_PENDING|STATUS_PROCESSING|STATUS_COMPLETE" src/main/java/org/ecocean/identity/IBEISIA.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1992:   925	    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1997:   930	            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2019:   952	                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2050:src/main/java/org/ecocean/ia/MlServiceProcessor.java:171:                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2051:src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2052:src/main/java/org/ecocean/ia/MlServiceProcessor.java:255:            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2053:src/main/java/org/ecocean/ia/MlServiceProcessor.java:278:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2054:src/main/java/org/ecocean/ia/MlServiceProcessor.java:333:                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2055:src/main/java/org/ecocean/ia/MlServiceProcessor.java:365:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2056:src/main/java/org/ecocean/ia/MlServiceProcessor.java:401:            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2057:src/main/java/org/ecocean/ia/MlServiceProcessor.java:625:        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2058:src/main/java/org/ecocean/ia/MlServiceProcessor.java:626:        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2059:src/main/java/org/ecocean/ia/MlServiceProcessor.java:663:        task.setStatus(IBEISIA.STATUS_ERROR);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2060:src/main/java/org/ecocean/identity/IBEISIA.java:73:    public static String STATUS_PENDING = "pending"; // pending review (needs action by user)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2061:src/main/java/org/ecocean/identity/IBEISIA.java:74:    public static String STATUS_COMPLETE = "complete"; // process is done
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2062:src/main/java/org/ecocean/identity/IBEISIA.java:75:    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2063:src/main/java/org/ecocean/identity/IBEISIA.java:76:    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2064:src/main/java/org/ecocean/identity/IBEISIA.java:78:    public static String STATUS_PROCESSING = "processing"; // off at IA, awaiting results
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2065:src/main/java/org/ecocean/identity/IBEISIA.java:79:    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2066:src/main/java/org/ecocean/identity/IBEISIA.java:81:    public static String STATUS_ERROR = "error";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2067:src/main/java/org/ecocean/identity/IBEISIA.java:433:                ma.setDetectionStatus(STATUS_ERROR); // is this wise?
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2068:src/main/java/org/ecocean/identity/IBEISIA.java:1666:                        asset.setDetectionStatus(STATUS_PENDING);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2069:src/main/java/org/ecocean/identity/IBEISIA.java:1668:                        asset.setDetectionStatus(STATUS_COMPLETE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2070:src/main/java/org/ecocean/identity/IBEISIA.java:1711:                    ann.setIdentificationStatus(STATUS_PROCESSING_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2071:src/main/java/org/ecocean/identity/IBEISIA.java:1856:                    anns.get(id).setIdentificationStatus(STATUS_PENDING);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2072:src/main/java/org/ecocean/identity/IBEISIA.java:1861:                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2073:src/main/java/org/ecocean/identity/IBEISIA.java:1867:                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2074:src/main/java/org/ecocean/identity/IBEISIA.java:2221:        ma.setDetectionStatus(STATUS_COMPLETE); // kosher?
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2357:    73	    public static String STATUS_PENDING = "pending"; // pending review (needs action by user)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2358:    74	    public static String STATUS_COMPLETE = "complete"; // process is done
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2359:    75	    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2360:    76	    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2361:    77	    public static String STATUS_DROPPED_STALE = "dropped-stale"; // queued ml-service job's target deleted before run; terminal-drop without error
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2362:    78	    public static String STATUS_PROCESSING = "processing"; // off at IA, awaiting results
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2363:    79	    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2364:    80	    public static String STATUS_INITIATED = "initiated"; // initiated on our side but may or may not be processing on IA side
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2365:    81	    public static String STATUS_ERROR = "error";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2420:   255	            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2443:   278	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2498:   333	                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2530:   365	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2603:    56	            return runDeferredMatch(jobData);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2639:    92	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2656:   109	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2680:   133	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2691:   144	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2718:   171	                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2741:   194	            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2790:src/main/java/org/ecocean/StartupWildbook.java:643:                ma.setDetectionStatus(org.ecocean.identity.IBEISIA.STATUS_ERROR);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2795:src/main/java/org/ecocean/identity/IBEISIA.java:75:    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2796:src/main/java/org/ecocean/identity/IBEISIA.java:76:    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2797:src/main/java/org/ecocean/identity/IBEISIA.java:79:    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2798:src/main/java/org/ecocean/identity/IBEISIA.java:433:                ma.setDetectionStatus(STATUS_ERROR); // is this wise?
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2799:src/main/java/org/ecocean/identity/IBEISIA.java:1666:                        asset.setDetectionStatus(STATUS_PENDING);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2800:src/main/java/org/ecocean/identity/IBEISIA.java:1668:                        asset.setDetectionStatus(STATUS_COMPLETE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2801:src/main/java/org/ecocean/identity/IBEISIA.java:2221:        ma.setDetectionStatus(STATUS_COMPLETE); // kosher?
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2810:src/main/java/org/ecocean/Encounter.java:5493:                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2811:src/main/java/org/ecocean/Encounter.java:5534:                    ma.setDetectionStatus(hasConfig ? IBEISIA.STATUS_INITIATED : IBEISIA.STATUS_COMPLETE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2812:src/main/java/org/ecocean/ia/IAUtils.java:20:                ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2813:src/main/java/org/ecocean/ia/MlServiceProcessor.java:171:                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2815:src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2817:src/main/java/org/ecocean/ia/MlServiceProcessor.java:278:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2818:src/main/java/org/ecocean/ia/MlServiceProcessor.java:365:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2832:src/main/java/org/ecocean/servlet/EncounterForm.java:891:                            ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2835:src/main/java/org/ecocean/servlet/IAGateway.java:216:                    ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2836:src/main/java/org/ecocean/servlet/IAGateway.java:260:                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2837:src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:111:	        crMa.setDetectionStatus(IBEISIA.STATUS_COMPLETE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3028:   627	            if (!org.ecocean.identity.IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3044:   643	                ma.setDetectionStatus(org.ecocean.identity.IBEISIA.STATUS_ERROR);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3062:   661	            boolean enqueued = org.ecocean.ia.IA.enqueueOneAssetForMlService(
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3382:   265	    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3398:   281	        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3413:   296	                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3419:   302	                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3424:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3437:   320	            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3439:   322	            // getMatchingSetQuery can return null (e.g. encounter missing,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3443:   326	                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3462:   345	                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3477:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3488:   371	            System.out.println("findMatchProspects() on " + ann + " found " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3495:   378	                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3683:   381	                System.out.println("findMatchProspects() MatchResult creation failed on " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3750:src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4448:  1188	                getMatchingSetQuery(myShepherd, taskParams, useClauses));
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5019:   325	        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5334:    17	 * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5791:/bin/bash -lc 'rg -n "skipAutoIndexing|setSkipAutoIndexing|opensearch|index" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/IndexingManager.java src/main/java/org/ecocean/OpenSearch.java src/main/java/org/ecocean/media/MediaAsset.java | head -n 200' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5860:src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5861:src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5944:src/main/java/org/ecocean/OpenSearch.java:468:    public void indexRefresh(final String indexName)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5950:src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5952:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5955:src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5956:src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5957:src/main/java/org/ecocean/OpenSearch.java:519:        indexRefresh(indexName);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5997:/bin/bash -lc 'rg -n "skipAutoIndexing" src/main/java/org/ecocean/Base.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/IndexingManager.java src/main/java/org/ecocean/shepherd/core/Shepherd.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6102:   100	    public boolean skipAutoIndexing = false;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6105:   103	        skipAutoIndexing = skip;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6109:   107	        return skipAutoIndexing;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6391:src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6392:src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6393:src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6399:src/main/java/org/ecocean/WildbookLifecycleListener.java:43:        if (OpenSearch.skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6400:src/main/java/org/ecocean/WildbookLifecycleListener.java:44:            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6402:src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6403:src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6405:src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6407:src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6408:src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6409:src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6410:src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6411:src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6413:src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6420:/bin/bash -lc "rg -n \"setSkipAutoIndexing\\(false\\)|skipAutoIndexing\" src/main/java/org/ecocean/api/bulk src/main/java/org/ecocean/servlet/importer src/main/java/org/ecocean/media src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6466:    43	        if (OpenSearch.skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6467:    44	            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6519:  1764	        if (this.skipAutoIndexing) return true;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6571:src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6576:/bin/bash -lc 'rg -n "iaImageIds|iaAnnotationIds|parse.*Strict|fromFancyUUID|sendMediaAssetsForceId|mediaAssetToUri|validMediaAsset" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java docs/plans/2026-05-18-wbia-image-registration-design.md' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6627:    46	    public void invalidateByName(String name)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6649:docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6659:docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6660:docs/plans/2026-05-18-wbia-image-registration-design.md:111:Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6663:docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6664:docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6665:docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6666:docs/plans/2026-05-18-wbia-image-registration-design.md:199:    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6667:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6669:docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6672:docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6673:docs/plans/2026-05-18-wbia-image-registration-design.md:261:  iaAnnotationIdsStrict called after (Phase 1 reached).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6676:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6692:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6693:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6694:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6698:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6699:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6700:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6712:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6716:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6725:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6740:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6741:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6742:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6743:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6745:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6910:   104	        List<String> iaImageIds = new ArrayList<String>();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6911:   105	        if (checkFirst) iaImageIds = iaImageIds();
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6926:   120	            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7004:   741	    public List<String> iaImageIds() {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7005:   742	        return iaImageIds(this.context);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7008:   745	    public static List<String> iaImageIds(String context) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7016:   753	            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7027:   764	                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7118:   477	    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7138:   497	                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7669:   617	            known = iaAnnotationIdsStrict(context);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8295:/bin/bash -lc 'rg -n "addToDetectionQueue|requeueJob|queueResumeMessage|deferredMatch|sleep|delay|publish" src/main/java/org/ecocean/servlet/IAGateway.java src/main/java/org/ecocean/queue src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8510:src/main/java/org/ecocean/servlet/IAGateway.java:385:                requeueJob(jobj, true);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8517:src/main/java/org/ecocean/servlet/IAGateway.java:748:        if (requeue) requeueJob(jobj, requeueIncrement);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8518:src/main/java/org/ecocean/servlet/IAGateway.java:751:    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8519:src/main/java/org/ecocean/servlet/IAGateway.java:764:            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8520:src/main/java/org/ecocean/servlet/IAGateway.java:768:        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8522:src/main/java/org/ecocean/servlet/IAGateway.java:786:                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8525:src/main/java/org/ecocean/servlet/IAGateway.java:805:                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8529:src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8530:src/main/java/org/ecocean/ia/MlServiceProcessor.java:133:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8688:   748	        if (requeue) requeueJob(jobj, requeueIncrement);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8691:   751	    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8692:   752	        int MAX_RETRIES = 30;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8693:   753	        long MAX_TIME_MILLIS = 2 * 24 * 60 * 60 * 1000;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8702:   762	        if (elapsed > MAX_TIME_MILLIS) retries = MAX_RETRIES + 1; // waiting around too long
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8703:   763	        if (retries > MAX_RETRIES) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8704:   764	            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8705:   765	                MAX_RETRIES + " retries (or max time) in queue; giving up");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8708:   768	        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8709:   769	            retries + " out of " + MAX_RETRIES + " (actualRetries=" + actualRetries + "; start=" +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8726:   786	                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8745:   805	                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8879:    25	   ml-service job calls `MlServiceProcessor.waitAndRunMatch` as soon
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8897:    43	  parent** (`topTask` from `IA.enqueueOneAssetForMlService`,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8900:    46	  annotations with `skipAutoIndexing=true`. topTask is one
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8907:    53	- **`runDeferredMatch` reuses the same gate** with an attempt
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8912:    58	- **Track 1 cache-naming cleanup**: pick `iaImageIdsStrict`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8932:    78	- Moving away from `waitForVisibility`. It works correctly — its
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8935:    81	  existing `waitAndRunMatch` seam plus a small collaborator.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8941:    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8942:    88	| `OpenSearch.indexRefresh(indexName)` | `OpenSearch.java:468` | Synchronous refresh helper. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8943:    89	| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Per-job wrapper; current implementation widens nothing. Falls back to `enqueueDeferredMatch` on timeout. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8944:    90	| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8945:    91	| `MlServiceProcessor.runDeferredMatch(jobData)` | `MlServiceProcessor.java:433` | Today: direct `runMatchProspects`. Bug: no re-gate. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8946:    92	| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues via IAGateway with `mlServiceV2DeferredMatch: true`. Today: no attempt counter. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8947:    93	| `IA.enqueueOneAssetForMlService` creates per-asset `Task childTask = new Task(topTask)` | `IA.java:281-287` | Per-asset child has parent=topTask; topTask owns `objectMediaAssets` (the same-species batch). |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8949:    95	| `Embedding.findMatchProspects(iaConfig, task, shepherd)` | `Embedding.java:265` | Per-annotation knn + matchingSetQuery on `task.getObjectAnnotations()`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8950:    96	| `QueryCache.invalidateByName(name)` | `QueryCache.java:46` | Right API for cache invalidation (not "invalidate"). |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8951:    97	| `WildbookIAM` helpers — `iaImageIds`, `sendMediaAssetsForceId`, `mediaAssetToUri`, `validMediaAsset` | `WildbookIAM.java` | See existing WBIA design doc. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8953:    99	Note: `findMatchProspects` already loops `task.getObjectAnnotations()`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8968:   114	2. New strict variant `iaImageIdsStrict` with 15-min QueryCache
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8969:   115	   (matching `iaAnnotationIdsStrict` from c11 fix-pass). Lenient
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8970:   116	   `iaImageIds` stays cache-free for backward compatibility.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9004:   150	  **`iaImageIdsStrict`** as the strict cache key.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9006:   152	  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaImageIdsStrict")`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9008:   154	- Also invalidate `iaAnnotationIdsStrict` after successful
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9016:   162	`IA.enqueueOneAssetForMlService` at `IA.java:281-287` creates each
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9027:   173	In `waitAndRunMatch`, given the child Task's id, load the child,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9045:   191	`enqueueDeferredMatch` with an incremented attempt counter (see
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9078:   224	  `skipAutoIndexing`).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9088:   234	`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9102:   248	private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9110:   256	        enqueueDeferredMatch(annotationIds, taskId, g.attempt);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9122:   248	private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9130:   256	        enqueueDeferredMatch(annotationIds, taskId, g.attempt);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9145:   271	#### Deferred-match path (`runDeferredMatch`)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9148:   274	`runDeferredMatch` reads `annotationIds` + `taskId` + `matchConfig`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9153:   279	public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9164:   290	    return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, attempt);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9168:   294	`enqueueDeferredMatch` is extended to take an `attempt` parameter,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9183:   309	Today `waitAndRunMatch` is private and constructs `new OpenSearch()`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9184:   310	directly, and `enqueueDeferredMatch` is private. Codex review item
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9219:   345	- Stub `gateForBatch` to return DEFER → assert `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9242:   368	  own annotations. `waitForVisibility` on empty set should be
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9250:   376	  `waitForVisibility` in the old single-annotation path
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9322:   443	- `runDeferredMatch_reGatesBeforeRunning`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9323:   444	- `runDeferredMatch_agesOutAtMaxAttempts`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9324:   445	- `enqueueDeferredMatch_writesAttemptCounterToJobPayload`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9328:   449	`findMatchProspects` result includes the other 3 as candidates.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9347:   468	4. **Should the gate also wait for `skipAutoIndexing` to be off**
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9350:   471	   where the user toggles `skipAutoIndexing` mid-import, we could
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9378:   499	- waitForVisibility introduction commit: `f429c5bf8` (c7).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9381:   502	  - `MlServiceProcessor.waitAndRunMatch` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9383:   504	  - `MlServiceProcessor.runDeferredMatch` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9385:   506	  - `OpenSearch.waitForVisibility` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9387:   508	  - `IA.enqueueOneAssetForMlService` (topTask boundary) —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9389:   510	  - `QueryCache.invalidateByName` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9391:   512	  - `Annotation.getMatchingSetQuery` (eligibility alignment) —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9393:   514	  - `Embedding.findMatchProspects` —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9396:   517	    (`skipAutoIndexing=true`) —
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9405:   181	    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9407:   183	        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9418:   194	        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9423:   199	    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9448:   224	After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9457:   233	removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9470:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9474:- **Major:** deferred timing is underspecified. `enqueueDeferredMatch` currently publishes immediately through `addToDetectionQueue` ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:568>)); existing `requeueJob` only gives a fixed 30s delay on increment ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785>)). Define the actual backoff mechanism and attempt semantics before coding.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9476:- **Medium:** Track 1 cache naming is still inconsistent with current code. `iaAnnotationIdsStrict` currently uses cache name `"iaAnnotationIds"` ([WildbookIAM.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477>)), while this design says invalidate `"iaAnnotationIdsStrict"` ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:154>)). The WBIA design doc still has stale `invalidate("iaImageIds")` text ([wbia doc](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-wbia-image-registration-design.md:190>)).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9485:4. `skipAutoIndexing`: do not wait for it to turn off. The existing WARN is enough for dev mode; production should not run this path with the flag set.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9504:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9508:- **Major:** deferred timing is underspecified. `enqueueDeferredMatch` currently publishes immediately through `addToDetectionQueue` ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:568>)); existing `requeueJob` only gives a fixed 30s delay on increment ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785>)). Define the actual backoff mechanism and attempt semantics before coding.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9510:- **Medium:** Track 1 cache naming is still inconsistent with current code. `iaAnnotationIdsStrict` currently uses cache name `"iaAnnotationIds"` ([WildbookIAM.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477>)), while this design says invalidate `"iaAnnotationIdsStrict"` ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:154>)). The WBIA design doc still has stale `invalidate("iaImageIds")` text ([wbia doc](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-wbia-image-registration-design.md:190>)).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9519:4. `skipAutoIndexing`: do not wait for it to turn off. The existing WARN is enough for dev mode; production should not run this path with the flag set.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:88:`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh boundary;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:91:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:94:`waitForVisibility` was added in commit `f429c5bf8` (c7 of v2) as
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:98:`skipAutoIndexing` (controlled by `/tmp/skipAutoIndexing`) makes
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:102:## `/tmp/skipAutoIndexing` and test mode
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:104:If `/tmp/skipAutoIndexing` exists, IndexingManager skips OS indexing
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:136:`Embedding.findMatchProspects` gates entry on
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:152:   `iaImageIds` swallows errors; strict variant raises so calls
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:204:   — `MlServiceProcessor.waitAndRunMatch` calls
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:205:   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:232:- Moving away from `waitForVisibility`. It works correctly — its
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:241:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:242:| `OpenSearch.indexRefresh(indexName)` | `OpenSearch.java:468` | Synchronous refresh helper. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:243:| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Current per-job wrapper; falls back to `enqueueDeferredMatch` on timeout. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:244:| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds the match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:245:| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues the match via IAGateway with `mlServiceV2DeferredMatch: true`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:246:| `Embedding.findMatchProspects(iaConfig, task, shepherd)` | `Embedding.java:265` | Per-annotation knn + matchingSetQuery. Already takes a list of annotations on the task; the count just isn't currently the import batch. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:248:| `WildbookIAM.iaImageIds(context)` / `sendMediaAssetsForceId(…)` / `mediaAssetToUri(…)` / `validMediaAsset(…)` | `WildbookIAM.java` | Existing helpers the WBIA-side design reuses. See the paused WBIA design doc for the helper-by-helper audit. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:250:Note: `findMatchProspects` already loops `task.getObjectAnnotations()`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:269:2. The strict `iaImageIdsStrict` variant introduces caching that the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:270:   lenient `iaImageIds` doesn't currently have. The new cache key is
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:271:   `iaImageIdsStrict` (15-min QueryCache, matching the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:272:   `iaAnnotationIdsStrict` pattern from the c11 fix-pass). The
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:310:`waitAndRunMatch` today:
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:313:MlServiceProcessor.waitAndRunMatch(annotationIds=[A], taskId, matchConfig):
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:314:    waitForVisibility("annotation", [A], TIMEOUT)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:317:        -> findMatchProspects loops [A], does knn against OS corpus
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:323:parallel `waitAndRunMatch` invocations each handle their own
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:335:##### Seam A (chosen): in-place expansion at `waitAndRunMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:337:`waitAndRunMatch` already has the annotationIds for the current job
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:341:`waitForVisibility`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:344:private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:349:        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:350:            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:354:        enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:393:seam itself. `findMatchProspects` continues to do per-annotation knn
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:413:- **ImportTask is large** (hundreds of annotations): `waitForVisibility`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:418:  `enqueueDeferredMatch` for this case. We reuse it. Caller's
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:420:  additional waiting (`runDeferredMatch` → `runMatchProspects` at
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:426:  failed**: `waitForVisibility` will time out (failed annotations
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:450:`exists: {field: acmId}` filter in `getMatchingSetQuery`, so it's
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:464:   `waitAndRunMatch`).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:488:- `waitAndRunMatch_expandsToImportBatch_whenParentTaskHasImportTaskId`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:491:- `waitAndRunMatch_passesOnlyCallerIdsToRunMatchProspects` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:493:- `waitAndRunMatch_fallsThroughWhenNoImportTaskParent` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:495:- `waitAndRunMatch_continuesOnExpansionFailure` — JDOQL throws;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:497:- `waitAndRunMatch_defersOnExpandedTimeout` — `waitForVisibility`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:498:  returns false on the wider set; verify `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:506:images, observe that each annotation's `findMatchProspects` result
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:517:   is >5000 and proceed; `waitForVisibility` will handle the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:520:   too** (`runDeferredMatch`)? Right now it skips visibility wait
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:524:   it (out of scope; this is purely a `waitAndRunMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:529:   yes — the visibility wait will time out, `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:547:- waitForVisibility introduction commit: `f429c5bf8` (c7)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:550:  - `MlServiceProcessor.waitAndRunMatch` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:552:  - `OpenSearch.waitForVisibility` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:554:  - `Annotation.getMatchingSetQuery` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:556:  - `Embedding.findMatchProspects` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:567:/bin/bash -lc 'rg -n "waitAndRunMatch|runDeferredMatch|runMatchProspects|enqueueDeferredMatch|waitForVisibility|importTaskId|class MlServiceProcessor" src/main/java src/test/java docs/plans' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:569:/bin/bash -lc 'rg -n "iaImageIds|iaAnnotationIdsStrict|parseAnnotationIdsArrayStrict|mediaAssetToUri|sendMediaAssetsForceId|validMediaAsset|WbiaRegisterRequest" src/main/java src/test/java docs/plans' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:571:docs/plans/2026-05-09-ml-service-migration-v2.md:77:  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:572:docs/plans/2026-05-09-ml-service-migration-v2.md:84:                deferred handler retries waitForVisibility with longer ceiling
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:573:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:574:docs/plans/2026-05-09-ml-service-migration-v2.md:245:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:575:docs/plans/2026-05-09-ml-service-migration-v2.md:258:Diagnostic: at startup and at the entry of `waitForVisibility`, log a WARN if
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:576:docs/plans/2026-05-09-ml-service-migration-v2.md:311:| 7 | `OpenSearch.waitForVisibility` helper + `skipAutoIndexing` diagnostic | ~60 | — |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:577:docs/plans/2026-05-09-ml-service-migration-v2.md:376:- `OpenSearch.waitForVisibility` (#7): visibility eventual-success returns true; timeout returns false; `skipAutoIndexing` flag triggers WARN.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:578:docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:579:docs/plans/2026-05-18-empty-match-prospects-design.md:29:   — `MlServiceProcessor.waitAndRunMatch` calls
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:580:docs/plans/2026-05-18-empty-match-prospects-design.md:30:   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:581:docs/plans/2026-05-18-empty-match-prospects-design.md:57:- Moving away from `waitForVisibility`. It works correctly — its
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:582:docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:583:docs/plans/2026-05-18-empty-match-prospects-design.md:68:| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Current per-job wrapper; falls back to `enqueueDeferredMatch` on timeout. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:584:docs/plans/2026-05-18-empty-match-prospects-design.md:69:| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds the match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:585:docs/plans/2026-05-18-empty-match-prospects-design.md:70:| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues the match via IAGateway with `mlServiceV2DeferredMatch: true`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:587:docs/plans/2026-05-18-empty-match-prospects-design.md:135:`waitAndRunMatch` today:
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:588:docs/plans/2026-05-18-empty-match-prospects-design.md:138:MlServiceProcessor.waitAndRunMatch(annotationIds=[A], taskId, matchConfig):
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:589:docs/plans/2026-05-18-empty-match-prospects-design.md:139:    waitForVisibility("annotation", [A], TIMEOUT)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:591:docs/plans/2026-05-18-empty-match-prospects-design.md:148:parallel `waitAndRunMatch` invocations each handle their own
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:592:docs/plans/2026-05-18-empty-match-prospects-design.md:160:##### Seam A (chosen): in-place expansion at `waitAndRunMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:593:docs/plans/2026-05-18-empty-match-prospects-design.md:162:`waitAndRunMatch` already has the annotationIds for the current job
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:595:docs/plans/2026-05-18-empty-match-prospects-design.md:166:`waitForVisibility`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:596:docs/plans/2026-05-18-empty-match-prospects-design.md:169:private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:597:docs/plans/2026-05-18-empty-match-prospects-design.md:174:        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:598:docs/plans/2026-05-18-empty-match-prospects-design.md:175:            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:599:docs/plans/2026-05-18-empty-match-prospects-design.md:179:        enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:607:docs/plans/2026-05-18-empty-match-prospects-design.md:238:- **ImportTask is large** (hundreds of annotations): `waitForVisibility`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:608:docs/plans/2026-05-18-empty-match-prospects-design.md:243:  `enqueueDeferredMatch` for this case. We reuse it. Caller's
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:609:docs/plans/2026-05-18-empty-match-prospects-design.md:245:  additional waiting (`runDeferredMatch` → `runMatchProspects` at
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:610:docs/plans/2026-05-18-empty-match-prospects-design.md:251:  failed**: `waitForVisibility` will time out (failed annotations
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:612:docs/plans/2026-05-18-empty-match-prospects-design.md:289:   `waitAndRunMatch`).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:613:docs/plans/2026-05-18-empty-match-prospects-design.md:313:- `waitAndRunMatch_expandsToImportBatch_whenParentTaskHasImportTaskId`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:614:docs/plans/2026-05-18-empty-match-prospects-design.md:316:- `waitAndRunMatch_passesOnlyCallerIdsToRunMatchProspects` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:615:docs/plans/2026-05-18-empty-match-prospects-design.md:318:- `waitAndRunMatch_fallsThroughWhenNoImportTaskParent` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:616:docs/plans/2026-05-18-empty-match-prospects-design.md:320:- `waitAndRunMatch_continuesOnExpansionFailure` — JDOQL throws;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:617:docs/plans/2026-05-18-empty-match-prospects-design.md:322:- `waitAndRunMatch_defersOnExpandedTimeout` — `waitForVisibility`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:618:docs/plans/2026-05-18-empty-match-prospects-design.md:323:  returns false on the wider set; verify `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:620:docs/plans/2026-05-18-empty-match-prospects-design.md:342:   is >5000 and proceed; `waitForVisibility` will handle the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:621:docs/plans/2026-05-18-empty-match-prospects-design.md:345:   too** (`runDeferredMatch`)? Right now it skips visibility wait
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:622:docs/plans/2026-05-18-empty-match-prospects-design.md:349:   it (out of scope; this is purely a `waitAndRunMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:624:docs/plans/2026-05-18-empty-match-prospects-design.md:354:   yes — the visibility wait will time out, `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:625:docs/plans/2026-05-18-empty-match-prospects-design.md:372:- waitForVisibility introduction commit: `f429c5bf8` (c7)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:626:docs/plans/2026-05-18-empty-match-prospects-design.md:375:  - `MlServiceProcessor.waitAndRunMatch` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:627:docs/plans/2026-05-18-empty-match-prospects-design.md:377:  - `OpenSearch.waitForVisibility` —
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:628:src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:640:src/main/java/org/ecocean/ia/MlServiceProcessor.java:56:            return runDeferredMatch(jobData);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:641:src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:642:src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:643:src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:644:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:645:src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:646:src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:648:src/main/java/org/ecocean/ia/MlServiceProcessor.java:433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:652:src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:653:src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:654:src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:655:src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:656:src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:662:docs/plans/2026-05-18-empty-match-prospects-design.md:73:| `WildbookIAM.iaImageIds(context)` / `sendMediaAssetsForceId(…)` / `mediaAssetToUri(…)` / `validMediaAsset(…)` | `WildbookIAM.java` | Existing helpers the WBIA-side design reuses. See the paused WBIA design doc for the helper-by-helper audit. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:664:docs/plans/2026-05-18-empty-match-prospects-design.md:94:2. The strict `iaImageIdsStrict` variant introduces caching that the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:665:docs/plans/2026-05-18-empty-match-prospects-design.md:95:   lenient `iaImageIds` doesn't currently have. The new cache key is
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:666:docs/plans/2026-05-18-empty-match-prospects-design.md:96:   `iaImageIdsStrict` (15-min QueryCache, matching the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:667:docs/plans/2026-05-18-empty-match-prospects-design.md:97:   `iaAnnotationIdsStrict` pattern from the c11 fix-pass). The
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:673:docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:685:docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:686:docs/plans/2026-05-18-wbia-image-registration-design.md:111:Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:693:docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:694:docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:695:docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:696:docs/plans/2026-05-18-wbia-image-registration-design.md:199:    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:697:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:699:docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:701:docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:702:docs/plans/2026-05-18-wbia-image-registration-design.md:261:  iaAnnotationIdsStrict called after (Phase 1 reached).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:705:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:724:src/main/java/org/ecocean/identity/IBEISIA.java:3710:        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:725:src/main/java/org/ecocean/identity/IBEISIA.java:3711:        HashSet<String> iaImageIds = null;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:726:src/main/java/org/ecocean/identity/IBEISIA.java:3718:            // get iaImageIds only if we need it
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:727:src/main/java/org/ecocean/identity/IBEISIA.java:3719:            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:728:src/main/java/org/ecocean/identity/IBEISIA.java:3720:            if (iaImageIds.isEmpty())
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:729:src/main/java/org/ecocean/identity/IBEISIA.java:3721:                throw new RuntimeException("iaImageIds is empty; possible IA problems");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:730:src/main/java/org/ecocean/identity/IBEISIA.java:3722:            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:731:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:732:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:733:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:737:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:738:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:739:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:750:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:752:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:757:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:758:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:759:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:760:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:761:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:762:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:774:The branch already contains the combined design doc you pasted. I’ve confirmed the current code still has the per-job `waitForVisibility` call, so the second track is reviewing a real gap, not a stale design assumption.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1220:    56	            return runDeferredMatch(jobData);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1256:    92	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1273:   109	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1297:   133	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1308:   144	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1335:   171	                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1348:   401	            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1365:   418	    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1369:   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1370:   423	                enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1374:   427	            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1380:   433	    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1419:   472	            // findMatchProspects returns false when the match config is not
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1422:   475	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1426:   479	                    "findMatchProspects rejected match config: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1444:   497	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1572:   625	        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1573:   626	        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1610:   663	        task.setStatus(IBEISIA.STATUS_ERROR);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1623:   676	        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1628:   681	    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1642:   695	            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1837:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1838:   468	    public void indexRefresh(final String indexName)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1848:   478	    // (commit #9) post-persist to avoid running findMatchProspects against an
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1856:   486	    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1868:   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1883:   513	        if (skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1885:   515	                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1889:   519	        indexRefresh(indexName);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3250:src/main/java/org/ecocean/servlet/IAGateway.java:764:            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3251:src/main/java/org/ecocean/servlet/IAGateway.java:768:        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3252:src/main/java/org/ecocean/servlet/IAGateway.java:786:                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3255:src/main/java/org/ecocean/servlet/IAGateway.java:805:                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3273:src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3280:src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3304:src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3305:src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3306:src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3308:src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3323:src/main/java/org/ecocean/ia/MlServiceProcessor.java:497:            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3331:src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3428:   385	                requeueJob(jobj, true);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3533:   246	            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3568:   281	    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:3603:   316	            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4365:/bin/bash -lc 'rg -n "getMatchingSetQuery|acmId|exists|ids|must_not|exclude|same" src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/Annotation.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4396:   265	    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4412:   281	        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4427:   296	                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4433:   302	                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4438:   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4451:   320	            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4453:   322	            // getMatchingSetQuery can return null (e.g. encounter missing,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4457:   326	                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4476:   345	                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4491:   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4519:   925	    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4524:   930	            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4546:   952	                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4607:src/main/java/org/ecocean/Embedding.java:320:            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4608:src/main/java/org/ecocean/Embedding.java:322:            // getMatchingSetQuery can return null (e.g. encounter missing,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4621:src/main/java/org/ecocean/Annotation.java:925:    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4622:src/main/java/org/ecocean/Annotation.java:930:            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4624:src/main/java/org/ecocean/Annotation.java:952:                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4642:src/main/java/org/ecocean/Annotation.java:1127:        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4643:src/main/java/org/ecocean/Annotation.java:1142:        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4644:src/main/java/org/ecocean/Annotation.java:1188:                getMatchingSetQuery(myShepherd, taskParams, useClauses));
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4792:  1127	        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4804:/bin/bash -lc 'rg -n "setSkipAutoIndexing|getSkipAutoIndexing|skipAutoIndexing|setMatchAgainst|matchAgainst|setAcmId|predictModelId|bboxKey|thetaKey|new Annotation" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/api/bulk/BulkImporter.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/Encounter.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4826:   278	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4881:   333	                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4913:   365	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5199:src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5209:/bin/bash -lc 'rg -n "class Base|skipAutoIndexing|setSkipAutoIndexing|getSkipAutoIndexing" src/main/java/org/ecocean/Base.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/IndexingManager.java src/main/java/org/ecocean/servlet/importer/ImportTask.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5212:src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5214:src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5216:src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5219:src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5231:/bin/bash -lc 'rg -n "skipAutoIndexing|getSkipAutoIndexing|dirty|enqueue|IndexingManager|makeDirty|dirtyEntities|Base" src/main/java/org/ecocean src/main/java/org/ecocean/shepherd src/main/java/org/ecocean/media | head -200' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5243:   100	    public boolean skipAutoIndexing = false;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5246:   103	        skipAutoIndexing = skip;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5250:   107	        return skipAutoIndexing;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5277:src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5278:src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5280:src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5306:src/main/java/org/ecocean/WildbookLifecycleListener.java:43:        if (OpenSearch.skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5307:src/main/java/org/ecocean/WildbookLifecycleListener.java:44:            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5329:src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5347:src/main/java/org/ecocean/ia/IA.java:246:            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5350:src/main/java/org/ecocean/ia/IA.java:281:    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5351:src/main/java/org/ecocean/ia/IA.java:316:            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5374:src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5375:src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5376:src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5377:src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5387:src/main/java/org/ecocean/StartupWildbook.java:661:            boolean enqueued = org.ecocean.ia.IA.enqueueOneAssetForMlService(
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5395:src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5396:src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5409:src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5412:src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5413:src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5485:    43	        if (OpenSearch.skipAutoIndexing()) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5486:    44	            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5685:   477	    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5705:   497	                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5825:   617	            known = iaAnnotationIdsStrict(context);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5862:   741	    public List<String> iaImageIds() {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5863:   742	        return iaImageIds(this.context);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5866:   745	    public static List<String> iaImageIds(String context) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5874:   753	            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5885:   764	                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6111:   104	        List<String> iaImageIds = new ArrayList<String>();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6112:   105	        if (checkFirst) iaImageIds = iaImageIds();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6127:   120	            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6198:   191	        List<String> iaImageIds = new ArrayList<String>();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6199:   192	        if (checkFirst) iaImageIds = iaImageIds();
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6213:   206	            if (iaImageIds.contains(ma.getAcmId())) continue;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6599:/bin/bash -lc 'rg -n "cache|invalidate|iaImageIds|iaAnnotationIds|Phase 0|Phase B" docs/plans/2026-05-18-wbia-image-registration-design.md docs/plans/2026-05-18-empty-match-prospects-design.md' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6601:docs/plans/2026-05-18-empty-match-prospects-design.md:73:| `WildbookIAM.iaImageIds(context)` / `sendMediaAssetsForceId(…)` / `mediaAssetToUri(…)` / `validMediaAsset(…)` | `WildbookIAM.java` | Existing helpers the WBIA-side design reuses. See the paused WBIA design doc for the helper-by-helper audit. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6602:docs/plans/2026-05-18-empty-match-prospects-design.md:94:2. The strict `iaImageIdsStrict` variant introduces caching that the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6603:docs/plans/2026-05-18-empty-match-prospects-design.md:95:   lenient `iaImageIds` doesn't currently have. The new cache key is
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6604:docs/plans/2026-05-18-empty-match-prospects-design.md:96:   `iaImageIdsStrict` (15-min QueryCache, matching the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6605:docs/plans/2026-05-18-empty-match-prospects-design.md:97:   `iaAnnotationIdsStrict` pattern from the c11 fix-pass). The
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6612:docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6613:docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6614:docs/plans/2026-05-18-wbia-image-registration-design.md:111:Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6619:docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6620:docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6623:docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6624:docs/plans/2026-05-18-wbia-image-registration-design.md:199:    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6630:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6633:docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6635:docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6636:docs/plans/2026-05-18-wbia-image-registration-design.md:261:  iaAnnotationIdsStrict called after (Phase 1 reached).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6637:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6657:src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6668:src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6749:    46	    public void invalidateByName(String name)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6922:   `waitAndRunMatch` runs immediately after one job persists its own detections ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:290)). If sibling ml-service jobs are still in HTTP or have not persisted, their annotation IDs are not in JDO yet, so `ImportTask -> Encounter -> Annotation` expansion cannot include them. This only fixes the narrower case where siblings are already in DB but not yet visible in OpenSearch. The actual “parallel per-image jobs” race still remains. You need an import/species-level barrier, or a “not all sibling media assets are terminal yet, defer and retry later” check before collecting IDs.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6925:   Bulk import creates initial annotations and marks them `skipAutoIndexing` ([BulkImporter.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:703)). Those rows are joined through `ENCOUNTER_ANNOTATIONS`, but they are not the ml-service detections and may never appear in OpenSearch because lifecycle indexing skips `skipAutoIndexing` objects ([WildbookLifecycleListener.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/WildbookLifecycleListener.java:53)). If the visibility set includes them, `waitForVisibility` will time out and fall into the deferred path for normal successful imports. The expansion query must filter to match-eligible ml-service annotations, at minimum `matchAgainst=true`, `acmId is not null`, and preferably “has embedding for this model/method” or `predictModelId is not null`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6928:   The chosen seam widens visibility but does not know when the batch is complete. A practical fix could still live in `waitAndRunMatch`, but it needs two phases: first wait/defer until the sibling media assets for the current task group are in terminal detection states, then collect eligible annotation IDs and call `waitForVisibility`. The current per-asset task has a parent created with `new Task(topTask)` ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:281)), and that parent carries the same-species media asset group; that is probably a tighter boundary than `ImportTask`, which can be mixed-species and encounter-leaky.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6931:   Leaving `runDeferredMatch` as a direct `runMatchProspects` call ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:433)) makes the first timeout degrade back to today’s bug. If the initial timeout was caused by sibling jobs not being terminal or OS not being caught up, the deferred job needs to re-check the batch gate. Add an attempt counter/backoff or max age so failed siblings do not cause infinite deferral.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6934:   `waitAndRunMatch` is private and constructs `new OpenSearch()` directly ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:418)), while `enqueueDeferredMatch` is private. The proposed mock/wrap tests are not feasible cleanly without changing the seam. Add an injectable visibility client/factory and make the expansion/gating helper package-visible, or extract a small collaborator that can be unit-tested without live JDO/OpenSearch.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6937:   The combined doc says the strict image cache key is `iaImageIdsStrict`, but the older WBIA doc still says invalidate `iaImageIds`. The actual cache API is `invalidateByName` ([QueryCache.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCache.java:46)). The implementation should consistently invalidate `iaImageIdsStrict` after successful image POST. I would also consider invalidating `iaAnnotationIds` after successful annotation POST to avoid stale-cache duplicate POSTs if Phase C fails or races.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6949:5. **Other gap:** `Annotation.getMatchingSetQuery` excludes the query annotation’s own encounter and requires `matchAgainst` plus `acmId` ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1001)). The visibility set should align with that eligibility, or it will wait on annotations that could never be candidates anyway.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6959:   `waitAndRunMatch` runs immediately after one job persists its own detections ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:290)). If sibling ml-service jobs are still in HTTP or have not persisted, their annotation IDs are not in JDO yet, so `ImportTask -> Encounter -> Annotation` expansion cannot include them. This only fixes the narrower case where siblings are already in DB but not yet visible in OpenSearch. The actual “parallel per-image jobs” race still remains. You need an import/species-level barrier, or a “not all sibling media assets are terminal yet, defer and retry later” check before collecting IDs.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6962:   Bulk import creates initial annotations and marks them `skipAutoIndexing` ([BulkImporter.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:703)). Those rows are joined through `ENCOUNTER_ANNOTATIONS`, but they are not the ml-service detections and may never appear in OpenSearch because lifecycle indexing skips `skipAutoIndexing` objects ([WildbookLifecycleListener.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/WildbookLifecycleListener.java:53)). If the visibility set includes them, `waitForVisibility` will time out and fall into the deferred path for normal successful imports. The expansion query must filter to match-eligible ml-service annotations, at minimum `matchAgainst=true`, `acmId is not null`, and preferably “has embedding for this model/method” or `predictModelId is not null`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6965:   The chosen seam widens visibility but does not know when the batch is complete. A practical fix could still live in `waitAndRunMatch`, but it needs two phases: first wait/defer until the sibling media assets for the current task group are in terminal detection states, then collect eligible annotation IDs and call `waitForVisibility`. The current per-asset task has a parent created with `new Task(topTask)` ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:281)), and that parent carries the same-species media asset group; that is probably a tighter boundary than `ImportTask`, which can be mixed-species and encounter-leaky.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6968:   Leaving `runDeferredMatch` as a direct `runMatchProspects` call ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:433)) makes the first timeout degrade back to today’s bug. If the initial timeout was caused by sibling jobs not being terminal or OS not being caught up, the deferred job needs to re-check the batch gate. Add an attempt counter/backoff or max age so failed siblings do not cause infinite deferral.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6971:   `waitAndRunMatch` is private and constructs `new OpenSearch()` directly ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:418)), while `enqueueDeferredMatch` is private. The proposed mock/wrap tests are not feasible cleanly without changing the seam. Add an injectable visibility client/factory and make the expansion/gating helper package-visible, or extract a small collaborator that can be unit-tested without live JDO/OpenSearch.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6974:   The combined doc says the strict image cache key is `iaImageIdsStrict`, but the older WBIA doc still says invalidate `iaImageIds`. The actual cache API is `invalidateByName` ([QueryCache.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCache.java:46)). The implementation should consistently invalidate `iaImageIdsStrict` after successful image POST. I would also consider invalidating `iaAnnotationIds` after successful annotation POST to avoid stale-cache duplicate POSTs if Phase C fails or races.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6986:5. **Other gap:** `Annotation.getMatchingSetQuery` excludes the query annotation’s own encounter and requires `matchAgainst` plus `acmId` ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1001)). The visibility set should align with that eligibility, or it will wait on annotations that could never be candidates anyway.
docs/plans/2026-05-09-ml-service-migration-v2.md:77:  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
docs/plans/2026-05-09-ml-service-migration-v2.md:82:  if visible: Embedding.findMatchProspects → MatchResult per annotation
docs/plans/2026-05-09-ml-service-migration-v2.md:84:                deferred handler retries waitForVisibility with longer ceiling
docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-09-ml-service-migration-v2.md:116:| `org.ecocean.Embedding.findMatchProspects` (modified) | Drop the `api_endpoint` requirement; read method/version from the `_id_conf` entry directly; subtask-failure does not prematurely complete parent; clone-not-mutate `matchingSetQuery`. | +40 |
docs/plans/2026-05-09-ml-service-migration-v2.md:157:`STATUS_PROCESSING_MLSERVICE` and `STATUS_COMPLETE_MLSERVICE` constants exist on main; reused.
docs/plans/2026-05-09-ml-service-migration-v2.md:245:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:
docs/plans/2026-05-09-ml-service-migration-v2.md:258:Diagnostic: at startup and at the entry of `waitForVisibility`, log a WARN if
docs/plans/2026-05-09-ml-service-migration-v2.md:259:`/tmp/skipAutoIndexing` exists (the global indexing-skip flag) — that flag
docs/plans/2026-05-09-ml-service-migration-v2.md:307:| 3 | `Embedding.findMatchProspects` bug fixes (api_endpoint, premature complete, mutation) | ~40 | #2 |
docs/plans/2026-05-09-ml-service-migration-v2.md:311:| 7 | `OpenSearch.waitForVisibility` helper + `skipAutoIndexing` diagnostic | ~60 | — |
docs/plans/2026-05-09-ml-service-migration-v2.md:373:- `Embedding.findMatchProspects` (#3): subtask failure → parent `error`; matchingSetQuery clone-not-mutate.
docs/plans/2026-05-09-ml-service-migration-v2.md:376:- `OpenSearch.waitForVisibility` (#7): visibility eventual-success returns true; timeout returns false; `skipAutoIndexing` flag triggers WARN.
docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
src/main/java/org/ecocean/cache/CachedQuery.java:28:    public static final String STATUS_PENDING = "pending"; // pending review (needs action by user)
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/Annotation.java:925:    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
src/main/java/org/ecocean/Annotation.java:930:            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
src/main/java/org/ecocean/Annotation.java:952:                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
src/main/java/org/ecocean/Annotation.java:1127:        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
src/main/java/org/ecocean/Annotation.java:1142:        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
src/main/java/org/ecocean/Annotation.java:1188:                getMatchingSetQuery(myShepherd, taskParams, useClauses));
src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:468:    public void indexRefresh(final String indexName)
src/main/java/org/ecocean/OpenSearch.java:478:    // (commit #9) post-persist to avoid running findMatchProspects against an
src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:519:        indexRefresh(indexName);
src/main/java/org/ecocean/StartupWildbook.java:627:            if (!org.ecocean.identity.IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(
src/main/java/org/ecocean/StartupWildbook.java:643:                ma.setDetectionStatus(org.ecocean.identity.IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/StartupWildbook.java:661:            boolean enqueued = org.ecocean.ia.IA.enqueueOneAssetForMlService(
src/main/java/org/ecocean/ia/IAUtils.java:20:                ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/identity/IBEISIA.java:73:    public static String STATUS_PENDING = "pending"; // pending review (needs action by user)
src/main/java/org/ecocean/identity/IBEISIA.java:74:    public static String STATUS_COMPLETE = "complete"; // process is done
src/main/java/org/ecocean/identity/IBEISIA.java:75:    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
src/main/java/org/ecocean/identity/IBEISIA.java:76:    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
src/main/java/org/ecocean/identity/IBEISIA.java:77:    public static String STATUS_DROPPED_STALE = "dropped-stale"; // queued ml-service job's target deleted before run; terminal-drop without error
src/main/java/org/ecocean/identity/IBEISIA.java:78:    public static String STATUS_PROCESSING = "processing"; // off at IA, awaiting results
src/main/java/org/ecocean/identity/IBEISIA.java:79:    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
src/main/java/org/ecocean/identity/IBEISIA.java:80:    public static String STATUS_INITIATED = "initiated"; // initiated on our side but may or may not be processing on IA side
src/main/java/org/ecocean/identity/IBEISIA.java:81:    public static String STATUS_ERROR = "error";
src/main/java/org/ecocean/identity/IBEISIA.java:433:                ma.setDetectionStatus(STATUS_ERROR); // is this wise?
src/main/java/org/ecocean/identity/IBEISIA.java:1666:                        asset.setDetectionStatus(STATUS_PENDING);
src/main/java/org/ecocean/identity/IBEISIA.java:1668:                        asset.setDetectionStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:1711:                    ann.setIdentificationStatus(STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/identity/IBEISIA.java:1856:                    anns.get(id).setIdentificationStatus(STATUS_PENDING);
src/main/java/org/ecocean/identity/IBEISIA.java:1861:                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:1867:                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:2221:        ma.setDetectionStatus(STATUS_COMPLETE); // kosher?
src/main/java/org/ecocean/identity/IBEISIA.java:3710:        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
src/main/java/org/ecocean/identity/IBEISIA.java:3711:        HashSet<String> iaImageIds = null;
src/main/java/org/ecocean/identity/IBEISIA.java:3718:            // get iaImageIds only if we need it
src/main/java/org/ecocean/identity/IBEISIA.java:3719:            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
src/main/java/org/ecocean/identity/IBEISIA.java:3720:            if (iaImageIds.isEmpty())
src/main/java/org/ecocean/identity/IBEISIA.java:3721:                throw new RuntimeException("iaImageIds is empty; possible IA problems");
src/main/java/org/ecocean/identity/IBEISIA.java:3722:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/IA.java:246:            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
src/main/java/org/ecocean/ia/IA.java:281:    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
src/main/java/org/ecocean/ia/IA.java:316:            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
src/main/java/org/ecocean/ia/IA.java:470:                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
src/main/java/org/ecocean/ia/MLService.java:164:                requeueJob(jobData, iaex.shouldIncrement());
src/main/java/org/ecocean/ia/MLService.java:206:            if (IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(ann.getIdentificationStatus()))
src/main/java/org/ecocean/ia/MLService.java:215:    public void requeueJob(JSONObject jobData, boolean increment) {
src/main/java/org/ecocean/ia/MLService.java:216:        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
src/main/java/org/ecocean/ia/MLService.java:219:        IAGateway.requeueJob(jobData, increment);
src/main/java/org/ecocean/ia/MLService.java:325:        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:56:            return runDeferredMatch(jobData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:133:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:171:                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:255:            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:278:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:333:                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:365:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:401:            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:472:            // findMatchProspects returns false when the match config is not
src/main/java/org/ecocean/ia/MlServiceProcessor.java:475:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:479:                    "findMatchProspects rejected match config: " +
src/main/java/org/ecocean/ia/MlServiceProcessor.java:497:            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:625:        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:626:        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:663:        task.setStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:676:        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
src/main/java/org/ecocean/Resolver.java:17:    public static String STATUS_PENDING = "pending"; // needs review
src/main/java/org/ecocean/Resolver.java:20:    public static String STATUS_COMPLETE = "complete";
src/main/java/org/ecocean/Resolver.java:21:    public static String STATUS_ERROR = "error";
src/main/java/org/ecocean/Resolver.java:231:            res.setStatus(STATUS_ERROR);
src/main/java/org/ecocean/Resolver.java:238:            res.setStatus(STATUS_PENDING);
src/main/java/org/ecocean/Resolver.java:252:        res.setStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/servlet/EncounterForm.java:891:                            ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:111:	        crMa.setDetectionStatus(IBEISIA.STATUS_COMPLETE);
src/main/java/org/ecocean/servlet/IAGateway.java:216:                    ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
src/main/java/org/ecocean/servlet/IAGateway.java:260:                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/servlet/IAGateway.java:385:                requeueJob(jobj, true);
src/main/java/org/ecocean/servlet/IAGateway.java:458:            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING);
src/main/java/org/ecocean/servlet/IAGateway.java:474:                ann.setIdentificationStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/servlet/IAGateway.java:748:        if (requeue) requeueJob(jobj, requeueIncrement);
src/main/java/org/ecocean/servlet/IAGateway.java:751:    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
src/main/java/org/ecocean/servlet/IAGateway.java:752:        int MAX_RETRIES = 30;
src/main/java/org/ecocean/servlet/IAGateway.java:753:        long MAX_TIME_MILLIS = 2 * 24 * 60 * 60 * 1000;
src/main/java/org/ecocean/servlet/IAGateway.java:762:        if (elapsed > MAX_TIME_MILLIS) retries = MAX_RETRIES + 1; // waiting around too long
src/main/java/org/ecocean/servlet/IAGateway.java:763:        if (retries > MAX_RETRIES) {
src/main/java/org/ecocean/servlet/IAGateway.java:764:            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
src/main/java/org/ecocean/servlet/IAGateway.java:765:                MAX_RETRIES + " retries (or max time) in queue; giving up");
src/main/java/org/ecocean/servlet/IAGateway.java:768:        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
src/main/java/org/ecocean/servlet/IAGateway.java:769:            retries + " out of " + MAX_RETRIES + " (actualRetries=" + actualRetries + "; start=" +
src/main/java/org/ecocean/servlet/IAGateway.java:786:                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:805:                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +

 succeeded in 1165ms:
migrate-ml-service-v2
b0f01f9ff docs: design for image-then-annotation WBIA registration
d3fb8b0f7 test(ia): MlServiceLiveIntegrationTest Codex post-scrub follow-ups
c8f122518 Updated integration testing
42606005e test(ia): MlServiceLiveIntegrationTest scrub + Codex review follow-ups
43e50e3e2 Merge branch 'main' into migrate-ml-service-v2

 succeeded in 12709ms:
## migrate-ml-service-v2...origin/migrate-ml-service-v2 [ahead 1]
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
?? docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md
?? docs/plans/2026-05-18-empty-match-prospects-codex-review.md
?? docs/plans/2026-05-18-empty-match-prospects-design.md
?? logs/

codex
The branch has a dirty worktree, so I’m treating the checked-out code as review context only and avoiding any mutation. I’ve confirmed the key design doc is present as an untracked file, and I’m now checking the implementation assumptions it names.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '180,380p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '380,730p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '450,560p'" in /mnt/c/Wildbook-clean2
 succeeded in 323ms:
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
    40	
    41	    public MlServiceProcessor(String context) {
    42	        this(context, new MlServiceClient());
    43	    }
    44	
    45	    public MlServiceProcessor(String context, MlServiceClient client) {
    46	        this.context = context;
    47	        this.client = client;
    48	    }
    49	
    50	    /** Process one ml-service queue job. Returns the outcome. */
    51	    public MlServiceJobOutcome process(JSONObject jobData) {
    52	        if (jobData == null) {
    53	            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
    54	        }
    55	        if (jobData.optBoolean("deferredMatch", false)) {
    56	            return runDeferredMatch(jobData);
    57	        }
    58	
    59	        String taxonomyString = jobData.optString("taxonomyString", null);
    60	        String taskId = jobData.optString("taskId", null);
    61	        String encounterId = jobData.optString("encounterId", null);
    62	
    63	        if (jobData.has("mediaAssetId")) {
    64	            String maId = String.valueOf(jobData.opt("mediaAssetId"));
    65	            return processDetection(jobData, taxonomyString, taskId, encounterId, maId);
    66	        }
    67	        if (jobData.has("annotationId")) {
    68	            String annId = jobData.optString("annotationId", null);
    69	            return processExtraction(jobData, taxonomyString, taskId, annId);
    70	        }
    71	        return MlServiceJobOutcome.validationError("INVALID_PAYLOAD",
    72	            "neither mediaAssetId nor annotationId in payload");
    73	    }
    74	
    75	    private MlServiceJobOutcome processDetection(JSONObject jobData, String taxonomyString,
    76	        String taskId, String encounterId, String maId) {
    77	        DetectionContext det = null;
    78	
    79	        try {
    80	            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
    81	        } catch (Exception ex) {
    82	            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
    83	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
    84	        }
    85	        if (det.outcome != null) return det.outcome;
    86	
    87	        JSONObject response;
    88	        try {
    89	            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
    90	        } catch (IAException ex) {
    91	            if (ex.shouldRequeue()) {
    92	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
    93	                return MlServiceJobOutcome.requeue();
    94	            }
    95	            markTaskError(taskId, ex.getCode(), ex.getMessage());
    96	            return mapNonRetryableError(ex);
    97	        }
    98	
    99	        JSONArray results = response.optJSONArray("results");
   100	        if (results == null || results.length() == 0) {
   101	            return finalizeZeroDetections(maId, taskId);
   102	        }
   103	
   104	        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results);
   105	        if (persisted.outcome != null) return persisted.outcome;
   106	
   107	        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
   108	            det.mlConfig);
   109	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   110	            matchConfig);
   111	        if (matchOutcome != null) return matchOutcome;
   112	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   113	    }
   114	
   115	    private MlServiceJobOutcome processExtraction(JSONObject jobData, String taxonomyString,
   116	        String taskId, String annId) {
   117	        ExtractionContext ext = null;
   118	
   119	        try {
   120	            ext = loadExtractionContext(taxonomyString, taskId, annId);
   121	        } catch (Exception ex) {
   122	            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
   123	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
   124	        }
   125	        if (ext.outcome != null) return ext.outcome;
   126	
   127	        JSONObject response;
   128	        try {
   129	            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
   130	                ext.mlConfig);
   131	        } catch (IAException ex) {
   132	            if (ex.shouldRequeue()) {
   133	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
   134	                return MlServiceJobOutcome.requeue();
   135	            }
   136	            markTaskError(taskId, ex.getCode(), ex.getMessage());
   137	            return mapNonRetryableError(ex);
   138	        }
   139	
   140	        PersistResult persisted = persistExtraction(annId, taskId, ext, response);
   141	        if (persisted.outcome != null) return persisted.outcome;
   142	
   143	        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
   144	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   145	            matchConfig);
   146	        if (matchOutcome != null) return matchOutcome;
   147	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   148	    }
   149	
   150	    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
   151	        String encounterId, String maId) {
   152	        Shepherd shep = new Shepherd(context);
   153	        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
   154	        try {
   155	            FeatureType.initAll(shep);
   156	            shep.beginDBTransaction();
   157	            MediaAsset ma = shep.getMediaAsset(maId);
   158	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   159	            Task task = Task.load(taskId, shep);
   160	
   161	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   162	            if (staleReason != null) {
   163	                markTaskDroppedStale(shep, task, staleReason);
   164	                shep.commitDBTransaction();
   165	                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
   166	            }
   167	
   168	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
   169	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   170	            if (configs == null) {
   171	                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
   172	                markTaskCompleted(task);
   173	                shep.commitDBTransaction();
   174	                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
   175	            }
   176	
   177	            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
   178	                markTaskError(task, "INVALID",
   179	                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
   180	                shep.commitDBTransaction();

 succeeded in 283ms:
   180	                shep.commitDBTransaction();
   181	                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
   182	                    "_mlservice_conf missing predict_model_id"));
   183	            }
   184	
   185	            URL webUrl = ma.webURL();
   186	            if (webUrl == null) {
   187	                markTaskError(task, "INVALID_IMAGE_URI",
   188	                    "MediaAsset " + maId + " has no webURL");
   189	                shep.commitDBTransaction();
   190	                return DetectionContext.done(MlServiceJobOutcome.validationError(
   191	                    "INVALID_IMAGE_URI", "MediaAsset " + maId + " has no webURL"));
   192	            }
   193	
   194	            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   195	            shep.commitDBTransaction();
   196	            return new DetectionContext(webUrl.toString(),
   197	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   198	                configs.matchConfig);
   199	        } finally {
   200	            shep.rollbackAndClose();
   201	        }
   202	    }
   203	
   204	    private ExtractionContext loadExtractionContext(String taxonomyString, String taskId,
   205	        String annId) {
   206	        Shepherd shep = new Shepherd(context);
   207	        shep.setAction(ACTION_PREFIX + "loadExtractionContext");
   208	        try {
   209	            shep.beginDBTransaction();
   210	            Annotation ann = shep.getAnnotation(annId);
   211	            Task task = Task.load(taskId, shep);
   212	            if (ann == null) {
   213	                markTaskDroppedStale(shep, task, "annotation missing");
   214	                shep.commitDBTransaction();
   215	                return ExtractionContext.done(MlServiceJobOutcome.stale("annotation missing"));
   216	            }
   217	            MediaAsset ma = ann.getMediaAsset();
   218	            if (ma == null) {
   219	                markTaskDroppedStale(shep, task, "annotation media asset missing");
   220	                shep.commitDBTransaction();
   221	                return ExtractionContext.done(
   222	                    MlServiceJobOutcome.stale("annotation media asset missing"));
   223	            }
   224	
   225	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString,
   226	                ann.findEncounter(shep));
   227	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   228	            if (configs == null) {
   229	                markTaskCompleted(task);
   230	                shep.commitDBTransaction();
   231	                return ExtractionContext.done(MlServiceJobOutcome.stale("pending-species"));
   232	            }
   233	            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
   234	                markTaskCompleted(task);
   235	                shep.commitDBTransaction();
   236	                return ExtractionContext.done(MlServiceJobOutcome.stale("embedding-exists"));
   237	            }
   238	
   239	            URL webUrl = ma.webURL();
   240	            if (webUrl == null) {
   241	                markTaskError(task, "INVALID_IMAGE_URI",
   242	                    "Annotation " + annId + " media asset has no webURL");
   243	                shep.commitDBTransaction();
   244	                return ExtractionContext.done(MlServiceJobOutcome.validationError(
   245	                    "INVALID_IMAGE_URI", "Annotation " + annId + " media asset has no webURL"));
   246	            }
   247	            int[] bbox = ann.getBbox();
   248	            if (bbox == null || bbox.length != 4) {
   249	                markTaskError(task, "INVALID_BBOX", "Annotation " + annId + " has no bbox");
   250	                shep.commitDBTransaction();
   251	                return ExtractionContext.done(MlServiceJobOutcome.validationError("INVALID_BBOX",
   252	                    "Annotation " + annId + " has no bbox"));
   253	            }
   254	
   255	            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   256	            shep.commitDBTransaction();
   257	            return new ExtractionContext(webUrl.toString(),
   258	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   259	                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
   260	        } finally {
   261	            shep.rollbackAndClose();
   262	        }
   263	    }
   264	
   265	    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
   266	        Shepherd shep = new Shepherd(context);
   267	        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
   268	        try {
   269	            shep.beginDBTransaction();
   270	            MediaAsset ma = shep.getMediaAsset(maId);
   271	            Task task = Task.load(taskId, shep);
   272	            String staleReason = detectionStaleReason(ma, null, null);
   273	            if (staleReason != null) {
   274	                markTaskDroppedStale(shep, task, staleReason);
   275	                shep.commitDBTransaction();
   276	                return MlServiceJobOutcome.stale(staleReason);
   277	            }
   278	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   279	            markTaskCompleted(task);
   280	            shep.commitDBTransaction();
   281	            return MlServiceJobOutcome.okZeroDetections();
   282	        } catch (Exception ex) {
   283	            markTaskError(taskId, "PERSIST", "zero-detection finalize failed: " + ex.getMessage());
   284	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
   285	        } finally {
   286	            shep.rollbackAndClose();
   287	        }
   288	    }
   289	
   290	    private PersistResult persistDetections(String maId, String encounterId, String taskId,
   291	        DetectionContext det, JSONArray results) {
   292	        Shepherd shep = new Shepherd(context);
   293	        shep.setAction(ACTION_PREFIX + "persistDetections");
   294	        List<String> annotationIds = new ArrayList<String>();
   295	
   296	        try {
   297	            FeatureType.initAll(shep);
   298	            shep.beginDBTransaction();
   299	            MediaAsset ma = shep.getMediaAsset(maId);
   300	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   301	            Task task = Task.load(taskId, shep);
   302	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   303	            if (staleReason != null) {
   304	                markTaskDroppedStale(shep, task, staleReason);
   305	                shep.commitDBTransaction();
   306	                return PersistResult.done(MlServiceJobOutcome.stale(staleReason));
   307	            }
   308	
   309	            for (int i = 0; i < results.length(); i++) {
   310	                JSONObject result = results.getJSONObject(i);
   311	                double[] bbox = parseBbox(result.getJSONArray("bbox"));
   312	                double theta = result.getDouble("theta");
   313	                String bboxKey = bboxKey(bbox);
   314	                String thetaKey = thetaKey(theta);
   315	                String predictModelId = result.optString("predict_model_id",
   316	                    det.mlConfig.optString("predict_model_id", null));
   317	                Annotation existing = findExistingAnnotation(ma, predictModelId, bboxKey,
   318	                    thetaKey);
   319	                if (existing != null) {
   320	                    annotationIds.add(existing.getId());
   321	                    continue;
   322	                }
   323	
   324	                JSONObject featureParams = featureParams(bbox, theta,
   325	                    result.optString("viewpoint", null));
   326	                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
   327	                String iaClass = result.optString("iaClass",
   328	                    result.optString("class_name", result.optString("class", null)));
   329	                Annotation ann = new Annotation(null, feature, iaClass);
   330	                ann.__setMediaAsset(ma);
   331	                ann.setAcmId(ann.getId());
   332	                ann.setMatchAgainst(true);
   333	                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   334	                ann.setPredictModelId(predictModelId);
   335	                ann.setBboxKey(bboxKey);
   336	                ann.setThetaKey(thetaKey);
   337	                ann.setWbiaRegistered(Boolean.FALSE);
   338	                ann.setWbiaRegisterAttempts(0);
   339	                ann.setViewpoint(result.optString("viewpoint", null));
   340	                ann.setQuality(optionalFiniteDouble(result, "score",
   341	                    optionalFiniteDouble(result, "confidence", null)));
   342	
   343	                // Bidirectional linkage:
   344	                //   MediaAsset.addFeature sets Feature.asset
   345	                //   Feature.setAnnotation sets the OWNING side of the
   346	                //     Annotation.features collection (which is mapped-by
   347	                //     "annotation"). Without explicitly setting this, the
   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
   349	                //     relationship management — fragile.
   350	                //   Annotation.addFeature puts the feature in the in-memory
   351	                //     list so reload returns it as expected.
   352	                ma.addFeature(feature);
   353	                feature.setAnnotation(ann);
   354	                ann.addFeature(feature);
   355	                if (enc != null) enc.addAnnotation(ann);
   356	                shep.getPM().makePersistent(feature);
   357	                shep.getPM().makePersistent(ann);
   358	
   359	                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
   360	                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
   361	                shep.getPM().makePersistent(emb);
   362	                annotationIds.add(ann.getId());
   363	            }
   364	
   365	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   366	            markTaskCompleted(task);
   367	            shep.commitDBTransaction();
   368	            return PersistResult.ok(annotationIds);
   369	        } catch (Exception ex) {
   370	            markTaskError(taskId, "PERSIST", "detection persist failed: " + ex.getMessage());
   371	            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
   372	                ex.getMessage()));
   373	        } finally {
   374	            shep.rollbackAndClose();
   375	        }
   376	    }
   377	
   378	    private PersistResult persistExtraction(String annId, String taskId, ExtractionContext ext,
   379	        JSONObject response) {
   380	        Shepherd shep = new Shepherd(context);

 succeeded in 281ms:
   380	        Shepherd shep = new Shepherd(context);
   381	        shep.setAction(ACTION_PREFIX + "persistExtraction");
   382	        List<String> annotationIds = new ArrayList<String>();
   383	
   384	        try {
   385	            shep.beginDBTransaction();
   386	            Annotation ann = shep.getAnnotation(annId);
   387	            Task task = Task.load(taskId, shep);
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
   439	        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
   440	        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
   441	        return runMatchProspects(annotationIds, taskId, matchConfig);
   442	    }
   443	
   444	    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
   445	        JSONObject matchConfig) {
   446	        if (annotationIds == null || annotationIds.isEmpty()) {
   447	            markTaskCompleted(taskId);
   448	            return MlServiceJobOutcome.ok(new ArrayList<String>());
   449	        }
   450	
   451	        Shepherd shep = new Shepherd(context);
   452	        shep.setAction(ACTION_PREFIX + "runMatchProspects");
   453	        try {
   454	            shep.beginDBTransaction();
   455	            List<Annotation> anns = new ArrayList<Annotation>();
   456	            for (String annId : annotationIds) {
   457	                Annotation ann = shep.getAnnotation(annId);
   458	                if (ann != null) anns.add(ann);
   459	            }
   460	            if (anns.isEmpty()) {
   461	                Task task = Task.load(taskId, shep);
   462	                markTaskDroppedStale(shep, task, "annotations missing");
   463	                shep.commitDBTransaction();
   464	                return MlServiceJobOutcome.stale("annotations missing");
   465	            }
   466	
   467	            Task parent = Task.load(taskId, shep);
   468	            Task matchTask = (parent == null) ? new Task() : new Task(parent);
   469	            matchTask.setObjectAnnotations(anns);
   470	            matchTask.addParameter("mlServiceV2Match", true);
   471	            shep.getPM().makePersistent(matchTask);
   472	            // findMatchProspects returns false when the match config is not
   473	            // a vector config or matchConfig is null. Don't leave the match
   474	            // task without a terminal status — mark the parent task error.
   475	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
   476	            if (!ran) {
   477	                matchTask.setStatus("error");
   478	                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
   479	                    "findMatchProspects rejected match config: " +
   480	                    (matchConfig == null ? "null" : matchConfig.toString()));
   481	                matchTask.setCompletionDateInMilliseconds();
   482	                // Update the parent task in this same transaction (parent is
   483	                // already loaded above) so the two updates commit atomically.
   484	                // Splitting across transactions risks leaving the parent
   485	                // "completed" if the second commit fails or the JVM dies.
   486	                if (parent != null) {
   487	                    markTaskError(parent, "INVALID_MATCH_CONFIG",
   488	                        "no usable vector match config");
   489	                }
   490	                shep.commitDBTransaction();
   491	                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
   492	                    "no usable vector match config");
   493	            }
   494	            shep.commitDBTransaction();
   495	            return MlServiceJobOutcome.ok(annotationIds);
   496	        } catch (Exception ex) {
   497	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
   498	            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
   499	        } finally {
   500	            shep.rollbackAndClose();
   501	        }
   502	    }
   503	
   504	    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
   505	        String code = ex == null ? null : ex.getCode();
   506	        String message = ex == null ? null : ex.getMessage();
   507	        if ("INVALID".equals(code) || "SUCCESS_FALSE".equals(code)) {
   508	            return MlServiceJobOutcome.validationError(code, message);
   509	        }
   510	        if ("TIMEOUT".equals(code) || "NETWORK".equals(code) || "RATE_LIMITED".equals(code)
   511	            || "SERVER_ERROR".equals(code) || "CLIENT_ERROR".equals(code)) {
   512	            return MlServiceJobOutcome.networkError(code, message);
   513	        }
   514	        return MlServiceJobOutcome.networkError("UNKNOWN", message);
   515	    }
   516	
   517	    static String bboxKey(double[] bbox) {
   518	        if (bbox == null || bbox.length != 4) return null;
   519	        return Math.round(bbox[0]) + ":" + Math.round(bbox[1]) + ":" + Math.round(bbox[2]) +
   520	            ":" + Math.round(bbox[3]);
   521	    }
   522	
   523	    static String thetaKey(double theta) {
   524	        return String.format(Locale.US, "%.4f", theta);
   525	    }
   526	
   527	    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
   528	        String bboxKey, String thetaKey) {
   529	        if (ma == null) return null;
   530	        for (Annotation ann : ma.getAnnotations()) {
   531	            if (ann == null) continue;
   532	            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
   533	            if (!sameString(bboxKey, ann.getBboxKey())) continue;
   534	            if (!sameString(thetaKey, ann.getThetaKey())) continue;
   535	            return ann;
   536	        }
   537	        return null;
   538	    }
   539	
   540	    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
   541	        if (!Util.stringExists(taxonomyString)) return null;
   542	        IAJsonProperties iac = IAJsonProperties.iaConfig();
   543	        if (iac == null) return null;
   544	        Taxonomy taxy = shep.getOrCreateTaxonomy(taxonomyString, false);
   545	        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
   546	        if (configs == null || configs.length() == 0) return null;
   547	        JSONObject mlConfig = configs.optJSONObject(0);
   548	        if (mlConfig == null) return null;
   549	        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
   550	        return new ConfigPair(mlConfig, matchConfig);
   551	    }
   552	
   553	    private JSONObject defaultMatchConfig(IAJsonProperties iac, Taxonomy taxy,
   554	        JSONObject mlConfig) {
   555	        JSONObject matchConfig = null;
   556	        JSONArray identConfigs = iac.getIdentConfig(taxy);
   557	        if (identConfigs != null) {
   558	            for (int i = 0; i < identConfigs.length(); i++) {
   559	                JSONObject entry = identConfigs.optJSONObject(i);
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

 succeeded in 283ms:
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
   551	    // when you only care about how many this would return
   552	    public int queryCount(String indexName, final JSONObject query)
   553	    throws IOException {
   554	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   555	        Request searchRequest = new Request("POST", indexName + "/_count");
   556	        JSONObject cleanedQuery = new JSONObject(query.toString());
   557	        cleanedQuery.remove("_source"); // invalid for a _count query
   558	        searchRequest.setJsonEntity(cleanedQuery.toString());
   559	        JSONObject res = new JSONObject();
   560	        try {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '220,340p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class Task|Task\\(|getParent|setParent|getObjectMediaAssets|setObjectMediaAssets|addObjectMediaAsset|getObjectAnnotations|setObjectAnnotations|getStatus\\(|setStatus\\(|statusDetails|objectMediaAssets|objectAnnotations|parent\" src/main/java/org/ecocean/ia src/main/resources/org/ecocean/ia/package.jdo" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,240p'" in /mnt/c/Wildbook-clean2
 succeeded in 243ms:
   220	        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
   221	            " assets; queued? = " + sent + "; " + topTask);
   222	        return topTask;
   223	    }
   224	
   225	    /**
   226	     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
   227	     * vector pipeline. Each MediaAsset gets its own child Task under
   228	     * topTask; each emits a {@code mlServiceV2:true} payload to the
   229	     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
   230	     * picks them up via the IAGateway dispatcher (commit #10a).
   231	     *
   232	     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
   233	     * topTask. The topTask itself remains as the aggregator that holds the
   234	     * full MediaAsset list for caller-side summary code.</p>
   235	     *
   236	     * <p>encounterId is derived best-effort from the MediaAsset's existing
   237	     * trivial annotation (every Encounter.addMediaAsset call creates one).
   238	     * If null, MlServiceProcessor persists annotations without explicit
   239	     * Encounter linkage and downstream MediaAsset.assignEncounters handles
   240	     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
   241	     */
   242	    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
   243	        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
   244	        int queued = 0;
   245	        for (MediaAsset ma : mas) {
   246	            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
   247	                queued++;
   248	            }
   249	        }
   250	        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
   251	            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
   252	        return topTask;
   253	    }
   254	
   255	    /**
   256	     * Build and enqueue one v2 ml-service job for a single MediaAsset.
   257	     * Returns {@code true} iff the FileQueue write succeeded.
   258	     *
   259	     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
   260	     * normal intake path) and the startup stale-mlservice reconciler in
   261	     * {@code StartupWildbook}. The reconciler relies on the boolean
   262	     * return to decide whether to commit accompanying state changes; the
   263	     * normal intake path tolerates the swallowed-failure behavior.</p>
   264	     *
   265	     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
   266	     * internally commits/reopens the transaction, so the child Task row
   267	     * is persisted before this method enqueues. On enqueue failure the
   268	     * child Task remains in the DB as an orphan — there is no queued
   269	     * job that will ever drive it. The orphan IS still discoverable
   270	     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
   271	     * the task references the MediaAsset through objectMediaAssets),
   272	     * so it may surface in operator-facing task listings until cleaned
   273	     * up by an out-of-band path. Callers that need cleanup should
   274	     * delete the orphan explicitly; the default posture here is to
   275	     * accept it since FileQueue write failures are rare.</p>
   276	     *
   277	     * <p>If {@code topTask} is null a fresh root task is created inside
   278	     * this method. This matches the reconciler's use case where there is
   279	     * no caller-side aggregator umbrella.</p>
   280	     */
   281	    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
   282	        Taxonomy taxy, Task topTask, String context, String baseUrl) {
   283	        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
   284	        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
   285	        singleton.add(ma);
   286	        childTask.setObjectMediaAssets(singleton);
   287	        myShepherd.storeNewTask(childTask);
   288	
   289	        // Best-effort encounterId via existing annotations on the MA.
   290	        String encounterId = null;
   291	        ArrayList<Annotation> existing = ma.getAnnotations();
   292	        if (existing != null) {
   293	            for (Annotation a : existing) {
   294	                Encounter enc = a.findEncounter(myShepherd);
   295	                if (enc != null) {
   296	                    encounterId = enc.getId();
   297	                    break;
   298	                }
   299	            }
   300	        }
   301	
   302	        JSONObject qjob = new JSONObject();
   303	        qjob.put("mlServiceV2", true);
   304	        qjob.put("mediaAssetId", ma.getId());
   305	        qjob.put("taxonomyString", taxy.getScientificName());
   306	        qjob.put("taskId", childTask.getId());
   307	        qjob.put("__context", context);
   308	        qjob.put("__baseUrl", baseUrl);
   309	        if (Util.stringExists(encounterId)) {
   310	            qjob.put("encounterId", encounterId);
   311	        }
   312	
   313	        try {
   314	            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
   315	        } catch (java.io.IOException iox) {
   316	            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
   317	                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
   318	            return false;
   319	        }
   320	    }
   321	
   322	    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
   323	        int count = 0;
   324	        int stopAfter = 200000;
   325	        int batchThreshold = 50;
   326	        int batchesSoFar = 0;
   327	        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();
   328	
   329	        try {
   330	            for (MediaAsset ma : mediaAssets) {
   331	                count++;
   332	                if (count > stopAfter) {
   333	                    break;
   334	                }
   335	                if (ma != null && !ma.hasAcmId()) {
   336	                    assetsWithMissingAcmids.add(ma);
   337	                }
   338	                if ((assetsWithMissingAcmids.size() >= batchThreshold) ||
   339	                    count == mediaAssets.size()) {
   340	                    if (assetsWithMissingAcmids.size() > 0) { // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this

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

 succeeded in 247ms:
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

 succeeded in 387ms:
src/main/resources/org/ecocean/ia/package.jdo:16:		<!-- this points up to the parent -->
src/main/resources/org/ecocean/ia/package.jdo:17:      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" recursion-depth="3"/>
src/main/resources/org/ecocean/ia/package.jdo:19:		<!-- this uses parent to find child tasks -->
src/main/resources/org/ecocean/ia/package.jdo:20:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/ia/package.jdo:36:      		<field name="statusDetails" persistence-modifier="persistent">
src/main/resources/org/ecocean/ia/package.jdo:54:		<field name="parentId" persistence-modifier="persistent">
src/main/resources/org/ecocean/ia/package.jdo:62:		<field name="objectMediaAssets">
src/main/resources/org/ecocean/ia/package.jdo:67:		<field name="objectAnnotations" mapped-by="mediaAsset"???   >
src/main/resources/org/ecocean/ia/package.jdo:69:		<field name="objectAnnotations">
src/main/java/org/ecocean/ia/MlServiceClient.java:209:    private static void validateEmbeddingField(JSONObject parent, String fieldName,
src/main/java/org/ecocean/ia/MlServiceClient.java:212:        JSONArray emb = parent.optJSONArray(fieldName);
src/main/java/org/ecocean/ia/MlServiceClient.java:229:        String modelId = parent.optString("embedding_model_id", null);
src/main/java/org/ecocean/ia/MlServiceClient.java:230:        String modelVer = parent.optString("embedding_model_version", null);
src/main/java/org/ecocean/ia/IA.java:67:        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:72:        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:80:    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
src/main/java/org/ecocean/ia/IA.java:86:        Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:93:            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
src/main/java/org/ecocean/ia/IA.java:94:        // in multi-species case we make sure we have a parent task and add each species task as a child
src/main/java/org/ecocean/ia/IA.java:95:        if (parentTask == null) parentTask = new Task();
src/main/java/org/ecocean/ia/IA.java:97:            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
src/main/java/org/ecocean/ia/IA.java:98:            parentTask.addChild(thisTask);
src/main/java/org/ecocean/ia/IA.java:100:        return parentTask;
src/main/java/org/ecocean/ia/IA.java:122:        final Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:125:        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
src/main/java/org/ecocean/ia/IA.java:129:        Taxonomy taxy, final Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:130:        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
src/main/java/org/ecocean/ia/IA.java:134:        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
src/main/java/org/ecocean/ia/IA.java:148:        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:149:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:150:        topTask.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:151:        myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:169:        // summary code that reads topTask.objectMediaAssets keeps working).
src/main/java/org/ecocean/ia/IA.java:184:            Task task = (numDetectAlgos == 1) ? topTask : new Task();
src/main/java/org/ecocean/ia/IA.java:185:            task.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:271:     * the task references the MediaAsset through objectMediaAssets),
src/main/java/org/ecocean/ia/IA.java:283:        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
src/main/java/org/ecocean/ia/IA.java:286:        childTask.setObjectMediaAssets(singleton);
src/main/java/org/ecocean/ia/IA.java:287:        myShepherd.storeNewTask(childTask);
src/main/java/org/ecocean/ia/IA.java:363:    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
src/main/java/org/ecocean/ia/IA.java:369:        final Task parentTask, boolean fastlane) {
src/main/java/org/ecocean/ia/IA.java:372:        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
src/main/java/org/ecocean/ia/IA.java:374:        // Task topTask = (parentTask==null) ? new Task() : parentTask;
src/main/java/org/ecocean/ia/IA.java:376:        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
src/main/java/org/ecocean/ia/IA.java:380:        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:383:        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:384:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:385:        topTask.setObjectAnnotations(anns);
src/main/java/org/ecocean/ia/IA.java:419:            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
src/main/java/org/ecocean/ia/IA.java:420:            if (parentTask != null && parentTask.getParameters() != null) {
src/main/java/org/ecocean/ia/IA.java:421:                newTaskParams = parentTask.getParameters();
src/main/java/org/ecocean/ia/IA.java:447:                    Task t = new Task();
src/main/java/org/ecocean/ia/IA.java:448:                    t.setObjectAnnotations(annsOneIAClass);
src/main/java/org/ecocean/ia/IA.java:458:            myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:536:            if (topTask == null) topTask = new Task(taskId);
src/main/java/org/ecocean/ia/IA.java:538:            myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:554:                    " for (parent) " + topTask);
src/main/java/org/ecocean/ia/IA.java:579:                    if (!topTask.getObjectMediaAssets().contains(ma)) {
src/main/java/org/ecocean/ia/MatchResult.java:83:        this.setQueryAnnotationFromTask();
src/main/java/org/ecocean/ia/MatchResult.java:97:        this.task = myShepherd.getTask(taskId);
src/main/java/org/ecocean/ia/MatchResult.java:108:    public Annotation setQueryAnnotationFromTask()
src/main/java/org/ecocean/ia/MatchResult.java:111:            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
src/main/java/org/ecocean/ia/MatchResult.java:114:            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
src/main/java/org/ecocean/ia/MatchResult.java:116:            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
src/main/java/org/ecocean/ia/MatchResult.java:118:        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
src/main/java/org/ecocean/ia/MatchResult.java:267:        for (Annotation ann : this.task.getObjectAnnotations()) {
src/main/java/org/ecocean/ia/MLService.java:127:        Task task = myShepherd.getTask(jobData.optString("taskId", null));
src/main/java/org/ecocean/ia/MLService.java:154:            if (task != null) task.setStatus("completed");
src/main/java/org/ecocean/ia/MLService.java:160:                task.setStatus("error");
src/main/java/org/ecocean/ia/MLService.java:181:                    for (Annotation ann : task.getObjectAnnotations()) {
src/main/java/org/ecocean/ia/MLService.java:200:        List<Annotation> anns = task.getObjectAnnotations();
src/main/java/org/ecocean/ia/IAPluginManager.java:76:        Task rootTask = new Task();
src/main/java/org/ecocean/ia/IAPluginManager.java:77:        rootTask.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IAPluginManager.java:98:        Task rootTask = new Task();
src/main/java/org/ecocean/ia/IAPluginManager.java:99:        rootTask.setObjectAnnotations(anns);
src/main/java/org/ecocean/ia/IAUtils.java:22:            Task parentTask = null; // this is *not* persisted, but only used so intakeMediaAssets will inherit its params
src/main/java/org/ecocean/ia/IAUtils.java:24:                parentTask = new Task();
src/main/java/org/ecocean/ia/IAUtils.java:29:                parentTask.setParameters(tp);
src/main/java/org/ecocean/ia/IAUtils.java:31:            Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), parentTask); // are they *really* persisted for another thread (queue)
src/main/java/org/ecocean/ia/IAUtils.java:32:            myShepherd.storeNewTask(task);
src/main/java/org/ecocean/ia/Task.java:28:public class Task implements java.io.Serializable {
src/main/java/org/ecocean/ia/Task.java:34:    private List<MediaAsset> objectMediaAssets = null;
src/main/java/org/ecocean/ia/Task.java:35:    private List<Annotation> objectAnnotations = null;
src/main/java/org/ecocean/ia/Task.java:36:    private Task parent = null;
src/main/java/org/ecocean/ia/Task.java:41:    private String statusDetails = null;
src/main/java/org/ecocean/ia/Task.java:45:    public Task() {
src/main/java/org/ecocean/ia/Task.java:48:    public Task(String id) {
src/main/java/org/ecocean/ia/Task.java:54:    public Task(Task p) {
src/main/java/org/ecocean/ia/Task.java:57:        this.setParent(p);
src/main/java/org/ecocean/ia/Task.java:105:            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
src/main/java/org/ecocean/ia/Task.java:107:            MediaAsset ma = this.getObjectMediaAssets().get(0);
src/main/java/org/ecocean/ia/Task.java:152:        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
src/main/java/org/ecocean/ia/Task.java:156:        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
src/main/java/org/ecocean/ia/Task.java:176:    public void setObjectMediaAssets(List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/Task.java:177:        objectMediaAssets = mas;
src/main/java/org/ecocean/ia/Task.java:180:    public void setObjectAnnotations(List<Annotation> anns) {
src/main/java/org/ecocean/ia/Task.java:181:        objectAnnotations = anns;
src/main/java/org/ecocean/ia/Task.java:184:    public List<MediaAsset> getObjectMediaAssets() {
src/main/java/org/ecocean/ia/Task.java:185:        return objectMediaAssets;
src/main/java/org/ecocean/ia/Task.java:188:    public List<Annotation> getObjectAnnotations() {
src/main/java/org/ecocean/ia/Task.java:189:        return objectAnnotations;
src/main/java/org/ecocean/ia/Task.java:193:        return Util.collectionSize(objectMediaAssets);
src/main/java/org/ecocean/ia/Task.java:197:        return Util.collectionSize(objectAnnotations);
src/main/java/org/ecocean/ia/Task.java:203:        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/Task.java:204:        if (!objectMediaAssets.contains(ma)) {
src/main/java/org/ecocean/ia/Task.java:205:            objectMediaAssets.add(ma);
src/main/java/org/ecocean/ia/Task.java:213:        if (objectAnnotations == null) objectAnnotations = new ArrayList<Annotation>();
src/main/java/org/ecocean/ia/Task.java:214:        if (!objectAnnotations.contains(ann)) {
src/main/java/org/ecocean/ia/Task.java:215:            objectAnnotations.add(ann);
src/main/java/org/ecocean/ia/Task.java:222:        if (ann != null && objectAnnotations != null && objectAnnotations.contains(ann)) {
src/main/java/org/ecocean/ia/Task.java:223:            objectAnnotations.remove(ann);
src/main/java/org/ecocean/ia/Task.java:230:        if (ma != null && objectMediaAssets != null && objectMediaAssets.contains(ma)) {
src/main/java/org/ecocean/ia/Task.java:231:            objectMediaAssets.remove(ma);
src/main/java/org/ecocean/ia/Task.java:238:        if (objectAnnotations == null) return false;
src/main/java/org/ecocean/ia/Task.java:239:        return objectAnnotations.contains(ann);
src/main/java/org/ecocean/ia/Task.java:243:        if (objectMediaAssets == null) return false;
src/main/java/org/ecocean/ia/Task.java:244:        return objectMediaAssets.contains(ma);
src/main/java/org/ecocean/ia/Task.java:270:    public void setParent(Task t) {
src/main/java/org/ecocean/ia/Task.java:271:        parent = t;
src/main/java/org/ecocean/ia/Task.java:275:    public Task getParent() {
src/main/java/org/ecocean/ia/Task.java:276:        return parent;
src/main/java/org/ecocean/ia/Task.java:279:    public String getParentId() {
src/main/java/org/ecocean/ia/Task.java:280:        if (parent == null) return null;
src/main/java/org/ecocean/ia/Task.java:281:        return parent.getId();
src/main/java/org/ecocean/ia/Task.java:306:    public Task getRootTask() {
src/main/java/org/ecocean/ia/Task.java:307:        if (parent == null) return this;
src/main/java/org/ecocean/ia/Task.java:308:        return parent.getRootTask();
src/main/java/org/ecocean/ia/Task.java:357:        for (MediaAsset ma : this.getObjectMediaAssets()) {
src/main/java/org/ecocean/ia/Task.java:369:        for (Annotation ann : this.getObjectAnnotations()) {
src/main/java/org/ecocean/ia/Task.java:378:        return Util.stringToJSONObject(statusDetails);
src/main/java/org/ecocean/ia/Task.java:382:        statusDetails = s;
src/main/java/org/ecocean/ia/Task.java:387:            statusDetails = null;
src/main/java/org/ecocean/ia/Task.java:389:            statusDetails = j.toString();
src/main/java/org/ecocean/ia/Task.java:466:        if ((objectMediaAssets != null) && (objectMediaAssets.size() > 0)) {
src/main/java/org/ecocean/ia/Task.java:468:            for (MediaAsset ma : this.objectMediaAssets) {
src/main/java/org/ecocean/ia/Task.java:473:        if ((objectAnnotations != null) && (objectAnnotations.size() > 0)) {
src/main/java/org/ecocean/ia/Task.java:475:            for (Annotation ann : this.objectAnnotations) {
src/main/java/org/ecocean/ia/Task.java:533:            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
src/main/java/org/ecocean/ia/Task.java:555:            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
src/main/java/org/ecocean/ia/Task.java:576:            Task r = t.getRootTask();
src/main/java/org/ecocean/ia/Task.java:597:    public String getStatus(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:637:        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
src/main/java/org/ecocean/ia/Task.java:655:                        if ((childTask2.getObjectAnnotations() != null) &&
src/main/java/org/ecocean/ia/Task.java:656:                            (childTask2.getObjectAnnotations().size() > 0) &&
src/main/java/org/ecocean/ia/Task.java:657:                            childTask2.getObjectAnnotations().get(0).getMatchAgainst() &&
src/main/java/org/ecocean/ia/Task.java:658:                            (childTask2.getObjectAnnotations().get(0).getIAClass() != null)) {
src/main/java/org/ecocean/ia/Task.java:659:                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
src/main/java/org/ecocean/ia/Task.java:663:                    if ((childTask.getObjectAnnotations() != null) &&
src/main/java/org/ecocean/ia/Task.java:664:                        (childTask.getObjectAnnotations().size() > 0) &&
src/main/java/org/ecocean/ia/Task.java:665:                        childTask.getObjectAnnotations().get(0).getMatchAgainst() &&
src/main/java/org/ecocean/ia/Task.java:666:                        (childTask.getObjectAnnotations().get(0).getIAClass() != null)) {
src/main/java/org/ecocean/ia/Task.java:667:                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
src/main/java/org/ecocean/ia/Task.java:683:            status = this.getStatus(myShepherd);
src/main/java/org/ecocean/ia/Task.java:699:    public void setStatus(String newStatus) {
src/main/java/org/ecocean/ia/Task.java:810:        rtn.put("parentTaskId", getParentId());
src/main/java/org/ecocean/ia/Task.java:818:            for (Annotation ann : getObjectAnnotations()) {
src/main/java/org/ecocean/ia/Task.java:867:            rtn.put("status", getStatus(myShepherd));
src/main/java/org/ecocean/ia/Task.java:869:            rtn.put("statusDetails", getStatusDetails());
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:11: * record clear status/statusDetails on the parent Task and react
src/main/java/org/ecocean/ia/MlServiceProcessor.java:467:            Task parent = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:468:            Task matchTask = (parent == null) ? new Task() : new Task(parent);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:469:            matchTask.setObjectAnnotations(anns);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:474:            // task without a terminal status — mark the parent task error.
src/main/java/org/ecocean/ia/MlServiceProcessor.java:477:                matchTask.setStatus("error");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:482:                // Update the parent task in this same transaction (parent is
src/main/java/org/ecocean/ia/MlServiceProcessor.java:484:                // Splitting across transactions risks leaving the parent
src/main/java/org/ecocean/ia/MlServiceProcessor.java:486:                if (parent != null) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:487:                    markTaskError(parent, "INVALID_MATCH_CONFIG",
src/main/java/org/ecocean/ia/MlServiceProcessor.java:663:        task.setStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:670:        task.setStatus("completed");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:676:        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:686:        if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:35:    // note: parentTask can be null so should be handled as such
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:36:    // (for now???) we let *calling* code set returned (child) task as child on parentTask, rather than
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:37:    // having the code in these methods set the child on the parent  (hence the 'final')
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:39:        final Task parentTask);
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:41:        final Task parentTask);
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:83:        final Task parentTask) {
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:84:        Task t = new Task();
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:86:        t.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:99:        final Task parentTask) {
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:100:        Task t = new Task();
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:102:        t.setObjectAnnotations(anns);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:65:        final Task parentTask) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:70:        final Task parentTask) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '250,330p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '520,620p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '620,730p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "getRootIATasks|objectMediaAssets.contains|objectAnnotations.contains|getTasksForMediaAsset|MediaAsset.*Task|Task.*MediaAsset" src/main/java/org/ecocean src/main/resources/org/ecocean' in /mnt/c/Wildbook-clean2
 succeeded in 304ms:
   620	                status = islObj.optString("status");
   621	            } else if (islObj.optJSONObject("status") != null &&
   622	                (islObj.optJSONObject("status").optJSONObject("needReview") != null)) {
   623	                status = "completed";
   624	            } else if (logs.toString().indexOf("score") > -1) {
   625	                status = "completed";
   626	            } else if (islObj.toString().indexOf("HTTP error code") > -1) {
   627	                status = "error";
   628	            } else if (!islObj.optString("queueStatus").equals("")) {
   629	                status = islObj.optString("queueStatus");
   630	            } else if (islObj.opt("status") != null &&
   631	                islObj.opt("status").toString().indexOf("initIdentify") > -1) {
   632	                status = "queuing";
   633	            }
   634	            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
   635	            // if(status.equals("waiting to queue"))System.out.println("islObj: "+islObj.toString());
   636	        }
   637	        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
   638	            this.getId());
   639	        return status;
   640	    }
   641	
   642	    // this is stitched together from import.jsp. godspeed.
   643	    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
   644	    // also the original building/modification of (passed-in) idStatusMap is dropped
   645	    public String getOverallStatus(Shepherd myShepherd) {
   646	        String status = "unknown";
   647	
   648	        if (this.hasChildren()) {
   649	            // accumulate status across children
   650	            HashMap<String, String> map = new HashMap<String, String>();
   651	            // this should only ever be two layers deep
   652	            for (Task childTask : this.getChildren()) {
   653	                if (childTask.hasChildren()) {
   654	                    for (Task childTask2 : childTask.getChildren()) {
   655	                        if ((childTask2.getObjectAnnotations() != null) &&
   656	                            (childTask2.getObjectAnnotations().size() > 0) &&
   657	                            childTask2.getObjectAnnotations().get(0).getMatchAgainst() &&
   658	                            (childTask2.getObjectAnnotations().get(0).getIAClass() != null)) {
   659	                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
   660	                        }
   661	                    }
   662	                } else {
   663	                    if ((childTask.getObjectAnnotations() != null) &&
   664	                        (childTask.getObjectAnnotations().size() > 0) &&
   665	                        childTask.getObjectAnnotations().get(0).getMatchAgainst() &&
   666	                        (childTask.getObjectAnnotations().get(0).getIAClass() != null)) {
   667	                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
   668	                    }
   669	                }
   670	            }
   671	            // now, how do we report these?
   672	            HashMap<String, Integer> resultsMap = new HashMap<String, Integer>();
   673	            for (String key : map.values()) {
   674	                // task results
   675	                if (!resultsMap.containsKey(key)) {
   676	                    resultsMap.put(key, new Integer(1));
   677	                } else {
   678	                    resultsMap.put(key, new Integer(resultsMap.get(key) + 1));
   679	                }
   680	            }
   681	            status = resultsMap.toString();
   682	        } else { // childless
   683	            status = this.getStatus(myShepherd);
   684	        }
   685	        return status;
   686	    }
   687	
   688	    public boolean isFastlane(Shepherd myShepherd) {
   689	        String status = "waiting to queue";
   690	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   691	            myShepherd);
   692	
   693	        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
   694	            return true;
   695	        }
   696	        return false;
   697	    }
   698	
   699	    public void setStatus(String newStatus) {
   700	        setModified();
   701	        if (newStatus == null) status = null;
   702	        else { status = newStatus; }
   703	    }
   704	
   705	    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }
   706	
   707	    // this will set all date stuff based on ms since epoch
   708	    public void setCompletionDateInMilliseconds(Long ms) {
   709	        this.completionDateInMilliseconds = ms;
   710	    }
   711	
   712	    // no arg = set to now
   713	    public void setCompletionDateInMilliseconds() {
   714	        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
   715	    }
   716	
   717	    // capture original queue message to make this Task more easily resumeable
   718	    public String getQueueResumeMessage() { return queueResumeMessage; }
   719	    public void setQueueResumeMessage(String message) {
   720	        if (message == null) { queueResumeMessage = null; } else {
   721	            queueResumeMessage = message;
   722	        }
   723	    }
   724	
   725	    public JSONObject getMatchingSetFilter() {
   726	        if (getParameters() == null) return null;
   727	        return getParameters().optJSONObject("matchingSetFilter");
   728	    }
   729	
   730	    public JSONObject getIdentificationMethodInfo() {

 succeeded in 308ms:
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
   597	    public String getStatus(Shepherd myShepherd) {
   598	        // see if we might be dead in the water
   599	        // TODO skipping status==null cuz i cant figure out what this means and there are so many of them
   600	        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
   601	            this.status = "error";
   602	            long ti = timeInactive();
   603	            setStatusDetailsAddError("TIMEOUT",
   604	                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
   605	            return this.status;
   606	        }
   607	        // if status is not null, just send it
   608	        if (status != null) return status;
   609	        // otherwise
   610	        // note: this is LOCAL status :(  so it is not changing this.status, only returning the value
   611	        String status = "waiting to queue";
   612	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   613	            myShepherd);
   614	        if (logs != null && logs.size() > 0) {
   615	            Collections.reverse(logs); // so it has newest first like mostRecent above
   616	            IdentityServiceLog l = logs.get(0);
   617	            JSONObject islObj = l.toJSONObject();
   618	            if (islObj.optString("status") != null &&
   619	                islObj.optString("status").equals("completed")) {
   620	                status = islObj.optString("status");

 succeeded in 316ms:
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

 succeeded in 962ms:
src/main/java/org/ecocean/Annotation.java:1798:    public List<Task> getRootIATasks(Shepherd myShepherd) { // convenience
src/main/java/org/ecocean/Encounter.java:4911:                                List<Task> annTasks = ann.getRootIATasks(myShepherd);
src/main/java/org/ecocean/Encounter.java:5495:                Task parentTask = null; // this is *not* persisted, but only used so intakeMediaAssets will inherit its params
src/main/java/org/ecocean/Encounter.java:5504:                task = IA.intakeMediaAssets(myShepherd, this.getMedia(), parentTask);
src/main/java/org/ecocean/Encounter.java:5552:                    task = IA.intakeMediaAssetsOneSpecies(myShepherd, allMAs, taxy, parentTask);
src/main/java/org/ecocean/ia/Task.java:204:        if (!objectMediaAssets.contains(ma)) {
src/main/java/org/ecocean/ia/Task.java:214:        if (!objectAnnotations.contains(ann)) {
src/main/java/org/ecocean/ia/Task.java:222:        if (ann != null && objectAnnotations != null && objectAnnotations.contains(ann)) {
src/main/java/org/ecocean/ia/Task.java:230:        if (ma != null && objectMediaAssets != null && objectMediaAssets.contains(ma)) {
src/main/java/org/ecocean/ia/Task.java:239:        return objectAnnotations.contains(ann);
src/main/java/org/ecocean/ia/Task.java:244:        return objectMediaAssets.contains(ma);
src/main/java/org/ecocean/ia/Task.java:321:    public Task deepContains(MediaAsset ma) {
src/main/java/org/ecocean/ia/Task.java:331:    public List<Task> findNodesWithMediaAssets() {
src/main/java/org/ecocean/ia/Task.java:533:            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
src/main/java/org/ecocean/ia/Task.java:553:    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:555:            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
src/main/java/org/ecocean/ia/Task.java:567:    public static List<Task> getRootTasksFor(MediaAsset ma, Shepherd myShepherd) {
src/main/java/org/ecocean/media/MediaAsset.java:1510:    public List<Task> getRootIATasks(Shepherd myShepherd) { // convenience
src/main/java/org/ecocean/MetricsBot.java:628:                    String filter = filter3 + " && objectAnnotations.contains(annot) " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:64:    @Override public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IAUtils.java:14:    public static List<String> intakeMediaAssets(ImportTask it, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/IAUtils.java:22:            Task parentTask = null; // this is *not* persisted, but only used so intakeMediaAssets will inherit its params
src/main/java/org/ecocean/ia/IAUtils.java:31:            Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), parentTask); // are they *really* persisted for another thread (queue)
src/main/java/org/ecocean/ia/IAPluginManager.java:68:    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/IAPluginManager.java:77:        rootTask.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IAPluginManager.java:79:            Task subTask = p.intakeMediaAssets(myShepherd, mas, rootTask);
src/main/java/org/ecocean/ia/IAPluginManager.java:83:                IA.log("INFO: IAPluginManager.intakeMediaAssets() got " + subTask + " for " + p);
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:82:    @Override public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:38:    public abstract Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:54:    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
src/main/java/org/ecocean/ia/IA.java:65:    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/IA.java:81:    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/IA.java:85:    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:93:            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
src/main/java/org/ecocean/ia/IA.java:97:            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
src/main/java/org/ecocean/ia/IA.java:121:    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:125:        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
src/main/java/org/ecocean/ia/IA.java:128:    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:130:        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
src/main/java/org/ecocean/ia/IA.java:133:    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
src/main/java/org/ecocean/ia/IA.java:150:        topTask.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:169:        // summary code that reads topTask.objectMediaAssets keeps working).
src/main/java/org/ecocean/ia/IA.java:172:            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
src/main/java/org/ecocean/ia/IA.java:227:     * vector pipeline. Each MediaAsset gets its own child Task under
src/main/java/org/ecocean/ia/IA.java:242:    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
src/main/java/org/ecocean/ia/IA.java:243:        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
src/main/java/org/ecocean/ia/IA.java:270:     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
src/main/java/org/ecocean/ia/IA.java:286:        childTask.setObjectMediaAssets(singleton);
src/main/java/org/ecocean/ia/IA.java:552:                Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
src/main/java/org/ecocean/ia/IA.java:579:                    if (!topTask.getObjectMediaAssets().contains(ma)) {
src/main/java/org/ecocean/servlet/EncounterForm.java:893:                        Task parentTask = null; // this is *not* persisted, but only used so intakeMediaAssets will inherit its params
src/main/java/org/ecocean/servlet/EncounterForm.java:902:                        Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(),
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:120:            for (Task t : ma.getRootIATasks(myShepherd)) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:364:        List<Task> tasks = iaTask.findNodesWithMediaAssets();
src/main/java/org/ecocean/servlet/MediaAssetCreate.java:132:                        task = IA.intakeMediaAssetsOneSpecies(myShepherd, allMAs, taxy, parentTask);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '730,900p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '730,820p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '120,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
 succeeded in 254ms:
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
   756	            this.mlConfig = mlConfig;
   757	            this.matchConfig = matchConfig;
   758	        }
   759	    }
   760	
   761	    private static final class DetectionContext {
   762	        final String imageUri;
   763	        final String apiEndpoint;
   764	        final JSONObject mlConfig;
   765	        final JSONObject matchConfig;
   766	        final MlServiceJobOutcome outcome;
   767	
   768	        DetectionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
   769	            JSONObject matchConfig) {
   770	            this.imageUri = imageUri;
   771	            this.apiEndpoint = apiEndpoint;
   772	            this.mlConfig = mlConfig;
   773	            this.matchConfig = matchConfig;
   774	            this.outcome = null;
   775	        }
   776	
   777	        private DetectionContext(MlServiceJobOutcome outcome) {
   778	            this.imageUri = null;
   779	            this.apiEndpoint = null;
   780	            this.mlConfig = null;
   781	            this.matchConfig = null;
   782	            this.outcome = outcome;
   783	        }
   784	
   785	        static DetectionContext done(MlServiceJobOutcome outcome) {
   786	            return new DetectionContext(outcome);
   787	        }
   788	    }
   789	
   790	    private static final class ExtractionContext {
   791	        final String imageUri;
   792	        final String apiEndpoint;
   793	        final JSONObject mlConfig;
   794	        final JSONObject matchConfig;
   795	        final double[] bbox;
   796	        final double theta;
   797	        final MlServiceJobOutcome outcome;
   798	
   799	        ExtractionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
   800	            JSONObject matchConfig, double[] bbox, double theta) {
   801	            this.imageUri = imageUri;
   802	            this.apiEndpoint = apiEndpoint;
   803	            this.mlConfig = mlConfig;
   804	            this.matchConfig = matchConfig;
   805	            this.bbox = bbox;
   806	            this.theta = theta;
   807	            this.outcome = null;
   808	        }
   809	
   810	        private ExtractionContext(MlServiceJobOutcome outcome) {
   811	            this.imageUri = null;
   812	            this.apiEndpoint = null;
   813	            this.mlConfig = null;
   814	            this.matchConfig = null;
   815	            this.bbox = null;
   816	            this.theta = 0.0d;
   817	            this.outcome = outcome;
   818	        }
   819	
   820	        static ExtractionContext done(MlServiceJobOutcome outcome) {
   821	            return new ExtractionContext(outcome);
   822	        }
   823	    }
   824	
   825	    private static final class PersistResult {
   826	        final List<String> annotationIds;
   827	        final MlServiceJobOutcome outcome;
   828	
   829	        private PersistResult(List<String> annotationIds, MlServiceJobOutcome outcome) {
   830	            this.annotationIds = annotationIds;
   831	            this.outcome = outcome;
   832	        }
   833	
   834	        static PersistResult ok(List<String> annotationIds) {
   835	            return new PersistResult(annotationIds, null);
   836	        }
   837	
   838	        static PersistResult done(MlServiceJobOutcome outcome) {
   839	            return new PersistResult(null, outcome);
   840	        }
   841	    }
   842	}

 succeeded in 252ms:
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

 succeeded in 295ms:
   120	
   121	    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
   122	        final Task parentTask) {
   123	        if ((mas == null) || (mas.size() < 1)) return null;
   124	        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
   125	        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
   126	    }
   127	
   128	    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
   129	        Taxonomy taxy, final Task parentTask) {
   130	        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
   131	    }
   132	
   133	    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
   134	        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
   135	        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
   136	            " media assets:");
   137	        handleMissingAcmids(mas, myShepherd);
   138	        for (MediaAsset ma : mas) {
   139	            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
   140	            System.out.println("acmid is: " + ma.getAcmId());
   141	        }
   142	        JSONArray maArr = new JSONArray();
   143	        for (MediaAsset ma : mas) {
   144	            maArr.put(ma.getId());
   145	        }
   146	        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());
   147	
   148	        Task topTask = new Task();
   149	        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
   150	        topTask.setObjectMediaAssets(mas);
   151	        myShepherd.storeNewTask(topTask);
   152	
   153	        String context = myShepherd.getContext();
   154	        String baseUrl = getBaseURL(context);
   155	
   156	        // Ia configs are keyed off taxonomies
   157	        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
   158	
   159	        // Migration plan v2 §commit #10b: routing reroute.
   160	        // If the species' _id_conf.default.pipeline_root is "vector" AND
   161	        // _mlservice_conf is configured, route per-asset through the
   162	        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
   163	        // WBIA path below — production deployments without _mlservice_conf
   164	        // see no behavior change at all.
   165	        //
   166	        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
   167	        // child finalization is local; no first-finisher-wins. The topTask
   168	        // remains as the aggregator for the caller contract (and so legacy
   169	        // summary code that reads topTask.objectMediaAssets keeps working).
   170	        if (iaConfig != null && taxy != null &&
   171	            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
   172	            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
   173	                context, baseUrl);
   174	        }
   175	
   176	        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
   177	        JSONObject dj = new JSONObject();
   178	        dj.put("mediaAssetIds", maArr);
   179	        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
   180	        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);

 succeeded in 298ms:
     1	package org.ecocean.servlet;
     2	
     3	import org.ecocean.AccessControl;
     4	import org.ecocean.Annotation;
     5	import org.ecocean.CommonConfiguration;
     6	import org.ecocean.ia.IA;
     7	import org.ecocean.ia.MLService;
     8	import org.ecocean.ia.Task;
     9	import org.ecocean.identity.*;
    10	import org.ecocean.media.*;
    11	import org.ecocean.Occurrence;
    12	import org.ecocean.queue.*;
    13	import org.ecocean.Resolver;
    14	import org.ecocean.servlet.importer.ImportTask;
    15	import org.ecocean.shepherd.core.Shepherd;
    16	import org.ecocean.User;
    17	import org.ecocean.Util;
    18	
    19	import javax.servlet.http.HttpServlet;
    20	import javax.servlet.http.HttpServletRequest;
    21	import javax.servlet.http.HttpServletResponse;
    22	import javax.servlet.ServletConfig;
    23	import javax.servlet.ServletException;
    24	import org.json.JSONArray;
    25	import org.json.JSONObject;
    26	
    27	import java.io.BufferedReader;
    28	import java.io.InputStreamReader;
    29	import java.io.IOException;
    30	import java.io.PrintWriter;
    31	import java.util.ArrayList;
    32	import java.util.List;
    33	import java.util.UUID;
    34	
    35	public class IAGateway extends HttpServlet {
    36	    private static Queue IAQueue = null;
    37	    private static Queue detectionQueue = null;
    38	    private static Queue acmIdQueue = null;
    39	    private static Queue IACallbackQueue = null;
    40	
    41	    public void init(ServletConfig config)
    42	    throws ServletException {
    43	        super.init(config);
    44	    }
    45	
    46	    public void doOptions(HttpServletRequest request, HttpServletResponse response)
    47	    throws ServletException, IOException {
    48	        ServletUtilities.doOptions(request, response);
    49	    }
    50	
    51	    public void doGet(HttpServletRequest request, HttpServletResponse response)
    52	    throws ServletException, IOException {
    53	        // "v2" is new IA package stuff -- so we just pass-thru and exit
    54	        String qstr = request.getQueryString();
    55	
    56	        // this seems to feed iaResults.jsp and is an unexpected dependency and extra URL call that shouldn't be needed
    57	        if ((qstr != null) && (qstr.matches(".*\\bv2\\b.*"))) {
    58	            response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
    59	            IA.handleGet(request, response);
    60	            return;
    61	        }
    62	        doPost(request, response);
    63	    }
    64	
    65	    public void doPost(HttpServletRequest request, HttpServletResponse response)
    66	    throws ServletException, IOException {
    67	        response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
    68	        String qstr = request.getQueryString();
    69	        // duplicated in both doGet and doPost
    70	        if ((qstr != null) && (qstr.matches(".*\\bcallback\\b.*"))) {
    71	            JSONObject rtn = queueCallback(request);
    72	            response.setContentType("text/plain");
    73	            PrintWriter out = response.getWriter();
    74	            out.println(rtn.toString());
    75	            out.close();
    76	            return;
    77	        }
    78	        String context = ServletUtilities.getContext(request); // note! this *must* be run after postStream stuff above
    79	
    80	        response.setContentType("text/plain");
    81	        PrintWriter out = response.getWriter();
    82	        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
    83	        JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
    84	        String taskId = Util.generateUUID();
    85	        res.put("taskId", taskId);
    86	        String baseUrl = null;
    87	        try {
    88	            String containerName = IA.getProperty("context0", "containerName");
    89	            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
    90	            if (containerName != null && containerName != "") {
    91	                baseUrl = baseUrl.replace("localhost", containerName);
    92	            }
    93	        } catch (Exception e) {
    94	            e.printStackTrace();
    95	        }
    96	        Shepherd myShepherd = new Shepherd(context);
    97	        myShepherd.setAction("IAGateway9");
    98	        myShepherd.beginDBTransaction();
    99	        try {
   100	            // v2 "forces" queueing -- onward to the glorious future!
   101	            // sendtoIAscripts for bulk command line detection use this v2 option
   102	            // uses detection queue
   103	            if (j.optBoolean("enqueue", false) || j.optBoolean("v2", false)) { // short circuits and just blindly writes out to queue and is done!
   104	                // TODO: if queue is not active/okay, fallback to synchronous
   105	                j.put("__context", context);
   106	                j.put("__baseUrl", baseUrl);
   107	                j.put("__enqueuedByIAGateway", System.currentTimeMillis());
   108	                // incoming json *probably* (should have) has taskId set... but if not i guess we use the one we generated???
   109	                if (j.optString("taskId", null) != null) {
   110	                    taskId = j.getString("taskId");
   111	                    res.put("taskId", taskId);
   112	                } else {
   113	                    j.put("taskId", taskId);
   114	                }
   115	                Task task = Task.load(taskId, myShepherd);
   116	                if (task == null) task = new Task(taskId);
   117	                JSONObject tparams = j.optJSONObject("taskParameters"); // optional
   118	                if (tparams == null) tparams = new JSONObject(); // but we want it, to set user:
   119	                User tuser = AccessControl.getUser(request, myShepherd);
   120	                if (tuser == null) { // "anonymous" but we want to make sure we zero these out to prevent them from being passed in

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '120,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '340,420p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MlServiceProcessorTest.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "mlServiceV2DeferredMatch|deferredMatch|MlServiceProcessor|mlServiceV2" src/main/java/org/ecocean/servlet/IAGateway.java src/main/java/org/ecocean/ia src/test/java/org/ecocean/ia/MlServiceProcessorTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 273ms:
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

 succeeded in 278ms:
   340	/* currently we are sending annotations one at a time (one per query list) but later we will have to support clumped sets...
   341	   things to consider for that - we probably have to further subdivide by species ... other considerations?   */
   342	        List<Task> subTasks = new ArrayList<Task>();
   343	        if (anns.size() > 1) { // need to create child Tasks
   344	            JSONObject params = parentTask.getParameters();
   345	            parentTask.setParameters((String)null); // reset this, kids inherit params
   346	            for (int i = 0; i < anns.size(); i++) {
   347	                Task newTask = new Task(parentTask);
   348	                newTask.setParameters(params);
   349	                newTask.addObject(anns.get(i));
   350	                myShepherd.storeNewTask(newTask);
   351	                myShepherd.beginDBTransaction();
   352	                subTasks.add(newTask);
   353	            }
   354	            myShepherd.storeNewTask(parentTask);
   355	            myShepherd.beginDBTransaction();
   356	        } else { // we just use the existing "parent" task
   357	            subTasks.add(parentTask);
   358	        }
   359	        for (int i = 0; i < anns.size(); i++) {
   360	            Annotation ann = anns.get(i);
   361	            JSONObject queryConfigDict = IBEISIA.queryConfigDict(myShepherd, opt);
   362	            JSONObject taskRes = new JSONObject();
   363	            Task subTask = subTasks.get(i);
   364	            try {
   365	                taskRes = _sendIdentificationTask(ann, context, baseUrl, queryConfigDict, null,
   366	                    limitTargetSize, subTask, myShepherd, fastlane);
   367	            } catch (Exception ex) { // unsure if maybe _some_ exceptions should be treated differently here?
   368	                System.out.println("subTask failure on " + subTask + ": " + ex.toString());
   369	                taskRes.put("success", false);
   370	                taskRes.put("error", ex.toString());
   371	                System.out.println(">>>>>>> parentTask: " + parentTask);
   372	                JSONObject jobj = new JSONObject();
   373	                jobj.put("identify", new JSONObject());
   374	                jobj.put("opt", opt);
   375	                jobj.getJSONObject("identify").put("annotationIds", new JSONArray());
   376	                jobj.getJSONObject("identify").getJSONArray("annotationIds").put(ann.getId());
   377	                jobj.put("taskId", subTask.getId());
   378	                jobj.put("__baseUrl", baseUrl);
   379	                jobj.put("__context", context);
   380	                jobj.put("__queueActualRetries", jin.optInt("__queueActualRetries", 0));
   381	                jobj.put("__queueRetries", jin.optInt("__queueRetries", 0));
   382	                jobj.put("__queueStart", jin.optLong("__queueStart", System.currentTimeMillis()));
   383	                System.out.println("_doIdentify() requeueing from jin=" + jin);
   384	                System.out.println("_doIdentify() requeueing as jobj=" + jobj);
   385	                requeueJob(jobj, true);
   386	            }
   387	            taskRes.put("subTaskId", subTask.getId());
   388	            taskRes.put("subTaskIndex", i);
   389	            taskList.put(taskRes);
   390	            System.out.println("subTask[" + i + "] => " + taskRes.toString());
   391	        }
   392	        if (limitTargetSize > -1) res.put("_limitTargetSize", limitTargetSize);
   393	        res.put("tasks", taskList);
   394	        res.put("success", true);
   395	        return res;
   396	    }
   397	
   398	    private static JSONObject _sendIdentificationTask(Annotation ann, String context,
   399	        String baseUrl, JSONObject queryConfigDict, JSONObject userConfidence, int limitTargetSize,
   400	        Task task, Shepherd myShepherd, boolean fastlane)
   401	    throws IOException {
   402	        boolean success = true;
   403	        String annTaskId = "UNKNOWN_" + Util.generateUUID();
   404	
   405	        if (task != null) annTaskId = task.getId();
   406	        JSONObject taskRes = new JSONObject();
   407	        taskRes.put("taskId", annTaskId);
   408	        JSONArray jids = new JSONArray();
   409	        jids.put(ann.getId()); // for now there is only one
   410	        taskRes.put("annotationIds", jids);
   411	        System.out.println("+ starting ident task " + annTaskId);
   412	        JSONObject shortCut = IAQueryCache.tryTargetAnnotationsCache(context, ann, taskRes,
   413	            myShepherd);
   414	        if (shortCut != null) return shortCut;
   415	        try {
   416	            // TODO: cache this examplars list (per species)
   417	            ///note: this can all go away if/when we decide not to need limitTargetSize
   418	            ArrayList<Annotation> matchingSet = null;
   419	            if (limitTargetSize > -1) {
   420	                matchingSet = ann.getMatchingSet(myShepherd);

 succeeded in 310ms:
   120	                if (tuser == null) { // "anonymous" but we want to make sure we zero these out to prevent them from being passed in
   121	                    tparams.remove("userId");
   122	                    tparams.remove("username");
   123	                } else {
   124	                    tparams.put("userId", tuser.getUUID());
   125	                    tparams.put("username", tuser.getUsername());
   126	                }
   127	                task.setParameters(tparams);
   128	                myShepherd.storeNewTask(task);
   129	                myShepherd.updateDBTransaction(); // hack
   130	                // myShepherd.closeDBTransaction();
   131	
   132	                boolean ok = false;
   133	                if (j.optJSONArray("annotationIds") != null) {
   134	                    // if this is just a single Encounter call, put it in the fast/detection lane to unblock small batch users
   135	                    if (j.optBoolean("fastlane", false)) {
   136	                        task.setQueueResumeMessage(j.toString());
   137	                        ok = addToDetectionQueue(context, j.toString());
   138	                    } else {
   139	                        task.setQueueResumeMessage(j.toString());
   140	                        ok = addToQueue(context, j.toString());
   141	                    }
   142	                } else {
   143	                    task.setQueueResumeMessage(j.toString());
   144	                    ok = addToDetectionQueue(context, j.toString());
   145	                }
   146	                if (ok) {
   147	                    System.out.println("INFO: taskId=" + taskId + " enqueued successfully");
   148	                    res.remove("error");
   149	                } else {
   150	                    System.out.println("ERROR: taskId=" + taskId +
   151	                        " was NOT enqueued successfully");
   152	                    res.put("error", "addToQueue() returned false");
   153	                }
   154	                res.put("success", ok);
   155	            } else if (j.optJSONObject("detect") != null) {
   156	                res = _doDetect(j, res, myShepherd, baseUrl);
   157	            } else if (j.optJSONObject("identify") != null) {
   158	                boolean fastlane = false;
   159	                if (j.optBoolean("fastlane", false)) { fastlane = true; }
   160	                res = _doIdentify(j, res, myShepherd, context, baseUrl, fastlane);
   161	            }
   162	            // bulk detection from import.jsp uses this area
   163	            // uses detection queue
   164	            else if (j.optJSONObject("bulkImport") != null) {
   165	                res = handleBulkImport(j, res, myShepherd, context, baseUrl);
   166	            } else if (j.optJSONObject("resolver") != null) {
   167	                res = Resolver.processAPIJSONObject(j.getJSONObject("resolver"), myShepherd);
   168	            } else {
   169	                res.put("error", "unknown POST command");
   170	                res.put("success", false);
   171	            }
   172	        } catch (Exception ex) {
   173	            System.out.println("Hit an exception in IAGateway:IAGateway9.");
   174	            ex.printStackTrace();
   175	            res.put("error", "exception in handling IAGateway input: " + ex.toString());
   176	            res.put("success", false);
   177	        } finally {
   178	            myShepherd.commitDBTransaction();
   179	            myShepherd.closeDBTransaction();
   180	        }
   181	        res.put("_in", j);
   182	
   183	        out.println(res.toString());
   184	        out.close();
   185	    }
   186	
   187	    public static JSONObject _doDetect(JSONObject jin, JSONObject res, Shepherd myShepherd,
   188	        String baseUrl)
   189	    throws ServletException, IOException {
   190	        if (res == null)
   191	            throw new RuntimeException("IAGateway._doDetect() called without res passed in");
   192	        String taskId = res.optString("taskId", null);
   193	        if (taskId == null)
   194	            throw new RuntimeException("IAGateway._doDetect() has no taskId passed in");
   195	        System.out.println("PRELOADED");
   196	        Task task = Task.load(taskId, myShepherd); // might be null in some cases, such as non-queued
   197	        System.out.println("LOADED???? " + taskId + " --> " + task);
   198	        String context = myShepherd.getContext();
   199	        if (baseUrl == null) return res;
   200	        if (jin == null) return res;
   201	        JSONObject j = jin.optJSONObject("detect");
   202	        if (j == null) return res; // "should never happen"
   203	
   204	        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
   205	        List<MediaAsset> needOccurrences = new ArrayList<MediaAsset>();
   206	        ArrayList<String> validIds = new ArrayList<String>();
   207	        if (j.optJSONArray("mediaAssetIds") != null) {
   208	            JSONArray ids = j.getJSONArray("mediaAssetIds");
   209	            for (int i = 0; i < ids.length(); i++) {
   210	                int id = ids.optInt(i, 0);
   211	                if (id < 1) continue;
   212	                myShepherd.beginDBTransaction();
   213	                MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
   214	                myShepherd.getPM().refresh(ma);
   215	                if (ma != null) {
   216	                    ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
   217	                    mas.add(ma);
   218	                }
   219	            }
   220	        } else {

 succeeded in 518ms:
src/main/java/org/ecocean/servlet/IAGateway.java:635:        // route here; MlServiceProcessor.process(jobj) handles routing
src/main/java/org/ecocean/servlet/IAGateway.java:637:        if (jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:639:            org.ecocean.ia.MlServiceProcessor processor =
src/main/java/org/ecocean/servlet/IAGateway.java:640:                new org.ecocean.ia.MlServiceProcessor(mlContext);
src/main/java/org/ecocean/servlet/IAGateway.java:642:            System.out.println("IAGateway: mlServiceV2 job " +
src/main/java/org/ecocean/servlet/IAGateway.java:792:                            jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:793:                            // mlServiceV2 retries must land on the detection
src/main/java/org/ecocean/servlet/IAGateway.java:796:                            // re-dispatched to MlServiceProcessor.
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:13: * v2 commit #9: pure-logic tests for MlServiceProcessor.
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:30:class MlServiceProcessorTest {
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:35:        MlServiceProcessor p = new MlServiceProcessor("context0");
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:42:        MlServiceProcessor p = new MlServiceProcessor("context0");
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:44:            .put("mlServiceV2", true)
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:56:        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:64:        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:72:        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:80:        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:89:        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:97:        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:107:        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:117:            MlServiceProcessor.bboxKey(new double[] { 10.0, 20.0, 30.0, 40.0 }));
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:119:            MlServiceProcessor.bboxKey(new double[] { 10.4, 20.4, 30.4, 40.4 }));
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:121:            MlServiceProcessor.bboxKey(new double[] { 10.5, 20.5, 30.5, 40.5 }));
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:125:        assertEquals("0.0000", MlServiceProcessor.thetaKey(0.0));
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:126:        assertEquals("3.1416", MlServiceProcessor.thetaKey(3.1415926));
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:128:        assertEquals("-1.5708", MlServiceProcessor.thetaKey(-1.5707963));
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:134:        String k = MlServiceProcessor.thetaKey(-0.0);
src/main/java/org/ecocean/ia/IA.java:162:        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
src/main/java/org/ecocean/ia/IA.java:228:     * topTask; each emits a {@code mlServiceV2:true} payload to the
src/main/java/org/ecocean/ia/IA.java:229:     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
src/main/java/org/ecocean/ia/IA.java:238:     * If null, MlServiceProcessor persists annotations without explicit
src/main/java/org/ecocean/ia/IA.java:303:        qjob.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/IAException.java:12:    // (e.g. MlServiceProcessor) can classify failures without parsing message
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:9: * {@code MlServiceProcessor.process(...)}. The seven {@link Kind} values
src/main/java/org/ecocean/ia/MlServiceClient.java:22: * org.ecocean.ia.MlServiceProcessor} (commit #9). Tests directly via
src/main/java/org/ecocean/ia/MlServiceProcessor.java:30: * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
src/main/java/org/ecocean/ia/MlServiceProcessor.java:33:public class MlServiceProcessor {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:34:    private static final String ACTION_PREFIX = "MlServiceProcessor.";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:41:    public MlServiceProcessor(String context) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:45:    public MlServiceProcessor(String context, MlServiceClient client) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:        if (jobData.optBoolean("deferredMatch", false)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:683:        payload.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:684:        payload.put("deferredMatch", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '600,660p'" in /mnt/c/Wildbook-clean2
 succeeded in 336ms:
   600	
   601	    public static Queue getIACallbackQueue(String context)
   602	    throws IOException {
   603	        IACallbackQueue = QueueUtil.getBest(context, "IACallback");
   604	        return IACallbackQueue;
   605	    }
   606	
   607	    public static void processQueueMessage(String message) {
   608	// System.out.println("DEBUG: IAGateway.processQueueMessage -> " + message);
   609	        if (message == null) return;
   610	        JSONObject jobj = null;
   611	        try {
   612	            jobj = new JSONObject(message);
   613	        } catch (org.json.JSONException jex) {
   614	            System.out.println(
   615	                "WARNING: IAGateway.processQueueMessage() failed to parse json from '" + message +
   616	                "' - " + jex.toString());
   617	            return;
   618	        }
   619	        if (jobj == null) return; // would this ever happen? #bsts
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

codex
One important mismatch has already turned up around the deferred path: `IAGateway.requeueJob` only gets the 30s delay when called with `increment=true`; carrying retry fields in JSON alone will not create that delay. I’m checking the match query predicates next because that determines whether the proposed OpenSearch and SQL filters actually line up.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1030,1150p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '900,1030p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1150,1235p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getMatchQuery|embeddings\\.method|methodVersion|matchingSetQuery|matchAgainst|acmId|nested\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Embedding.java src/test/java/org/ecocean/AnnotationTest.java src/test/java/org/ecocean/EmbeddingTest.java src/test/java/org/ecocean/OpenSearchVisibilityTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 289ms:
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
  1149	        try {
  1150	            int pageSize = 10000;

 succeeded in 292ms:
   900	
   901	    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request)
   902	    throws org.datanucleus.api.rest.orgjson.JSONException {
   903	        return this.sanitizeMedia(request, false);
   904	    }
   905	
   906	    public boolean isPart() {
   907	        return ((this.iaClass != null) && this.iaClass.contains("+"));
   908	    }
   909	
   910	    public String getPartIfPresent() {
   911	        String thisPart = "";
   912	
   913	        if (this.iaClass != null && this.iaClass.contains("+")) {
   914	            String[] arr = this.iaClass.split("\\+");
   915	            thisPart = arr[arr.length - 1];
   916	        }
   917	        return thisPart;
   918	    }
   919	
   920	/*
   921	   both must be arrays which contain objects.
   922	   these will be "mixed into" the built default query. TODO this might cause some conflict or
   923	   overwriting that needs to be addressed in the future
   924	 */
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

 succeeded in 298ms:
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

 succeeded in 350ms:
src/main/java/org/ecocean/Embedding.java:34:    private String methodVersion;
src/main/java/org/ecocean/Embedding.java:43:    public Embedding(Annotation ann, String method, String methodVersion, PGvector vec) {
src/main/java/org/ecocean/Embedding.java:48:        this.methodVersion = methodVersion;
src/main/java/org/ecocean/Embedding.java:54:    public Embedding(Annotation ann, String method, String methodVersion, JSONArray vecArr) {
src/main/java/org/ecocean/Embedding.java:55:        this(ann, method, methodVersion, (PGvector)null);
src/main/java/org/ecocean/Embedding.java:101:        return methodVersion;
src/main/java/org/ecocean/Embedding.java:104:    public void setMethodVersion(String methodVersion) {
src/main/java/org/ecocean/Embedding.java:105:        this.methodVersion = methodVersion;
src/main/java/org/ecocean/Embedding.java:109:        return ((method == null) ? "(unknown)" : method) + "-" + ((methodVersion ==
src/main/java/org/ecocean/Embedding.java:110:                   null) ? "(unknown version)" : methodVersion);
src/main/java/org/ecocean/Embedding.java:318:            // Build matchingSetQuery for the candidate count.
src/main/java/org/ecocean/Embedding.java:320:            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
src/main/java/org/ecocean/Embedding.java:325:            if (matchingSetQuery == null) {
src/main/java/org/ecocean/Embedding.java:326:                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
src/main/java/org/ecocean/Embedding.java:328:                subTask.setStatusDetailsAddError("REQUIRED", "null matchingSetQuery");
src/main/java/org/ecocean/Embedding.java:334:            // Count eligible candidates BEFORE getMatchQuery, which mutates
src/main/java/org/ecocean/Embedding.java:335:            // matchingSetQuery in place (Annotation.java:1203). The previous
src/main/java/org/ecocean/Embedding.java:338:            // matchingSetQuery to getMatchQuery so the eligible-set object
src/main/java/org/ecocean/Embedding.java:343:                numberCandidates = os.queryCount("annotation", matchingSetQuery);
src/main/java/org/ecocean/Embedding.java:356:            JSONObject matchQuery = ann.getMatchQuery(method, version,
src/main/java/org/ecocean/Embedding.java:357:                new JSONObject(matchingSetQuery.toString()));
src/main/java/org/ecocean/Annotation.java:53:    protected String acmId;
src/main/java/org/ecocean/Annotation.java:72:    //           acmId IS NOT NULL ("already registered via the historical
src/main/java/org/ecocean/Annotation.java:88:    private boolean matchAgainst = false;
src/main/java/org/ecocean/Annotation.java:178:        map.put("acmId", keywordType);
src/main/java/org/ecocean/Annotation.java:192:        embMap.put("type", "nested");
src/main/java/org/ecocean/Annotation.java:196:        embProps.put("methodVersion", keywordType);
src/main/java/org/ecocean/Annotation.java:213:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/Annotation.java:216:        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
src/main/java/org/ecocean/Annotation.java:250:                jgen.writeStringField("methodVersion", emb.getMethodVersion());
src/main/java/org/ecocean/Annotation.java:268:    // TODO should this also be limited by matchAgainst and acmId?
src/main/java/org/ecocean/Annotation.java:313:        this.acmId = id;
src/main/java/org/ecocean/Annotation.java:318:        return this.acmId;
src/main/java/org/ecocean/Annotation.java:322:        return (this.acmId != null);
src/main/java/org/ecocean/Annotation.java:573:        if (acmId == null)
src/main/java/org/ecocean/Annotation.java:574:            throw new IOException(this + " does not have acmId set; cannot get viewpoint from IA");
src/main/java/org/ecocean/Annotation.java:576:            JSONObject resp = IBEISIA.iaViewpointFromAnnotUUID(acmId, context);
src/main/java/org/ecocean/Annotation.java:751:        return matchAgainst;
src/main/java/org/ecocean/Annotation.java:755:        matchAgainst = b;
src/main/java/org/ecocean/Annotation.java:956:        // returned every annotation with matchAgainst=T and an acmId
src/main/java/org/ecocean/Annotation.java:1001:        // matchAgainst true
src/main/java/org/ecocean/Annotation.java:1003:        arg.put("matchAgainst", true);
src/main/java/org/ecocean/Annotation.java:1008:        // must have acmId
src/main/java/org/ecocean/Annotation.java:1010:        arg.put("field", "acmId");
src/main/java/org/ecocean/Annotation.java:1178:    // method and methodVersion are used to determine *which* embedding to use; if null it will use 1st embedding
src/main/java/org/ecocean/Annotation.java:1181:    // this version will construct matchingSetQuery
src/main/java/org/ecocean/Annotation.java:1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
src/main/java/org/ecocean/Annotation.java:1183:        String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:1184:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1187:        return getMatchQuery(method, methodVersion,
src/main/java/org/ecocean/Annotation.java:1191:    // this version if you already have matchingSetQuery
src/main/java/org/ecocean/Annotation.java:1192:    public JSONObject getMatchQuery(String method, String methodVersion,
src/main/java/org/ecocean/Annotation.java:1193:        JSONObject matchingSetQuery) {
src/main/java/org/ecocean/Annotation.java:1194:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1197:        JSONObject nested = new JSONObject(
src/main/java/org/ecocean/Annotation.java:1198:            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
src/main/java/org/ecocean/Annotation.java:1207:        if (methodVersion != null)
src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
src/main/java/org/ecocean/Annotation.java:1210:        nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
src/main/java/org/ecocean/Annotation.java:1213:        // we put nested under its own top-level must, that way its score counts (whereas filter does not)
src/main/java/org/ecocean/Annotation.java:1214:        JSONArray nestedMust = new JSONArray();
src/main/java/org/ecocean/Annotation.java:1215:        nestedMust.put(nested);
src/main/java/org/ecocean/Annotation.java:1216:        matchingSetQuery.getJSONObject("query").getJSONObject("bool").put("must", nestedMust);
src/main/java/org/ecocean/Annotation.java:1217:        return matchingSetQuery;
src/main/java/org/ecocean/Annotation.java:1223:        boolean useClauses, String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
src/main/java/org/ecocean/Annotation.java:1890:            "\" && annotations.contains(annot1) && annot1.acmId == \"" + annot.getAcmId() +
src/main/java/org/ecocean/Annotation.java:1905:        if (this.acmId == null) return;
src/main/java/org/ecocean/Annotation.java:1906:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
src/main/java/org/ecocean/Annotation.java:1912:        Util.mark("Annotation.refreshLiteTaxonomy() refreshing " + this.acmId);
src/main/java/org/ecocean/Annotation.java:1913:        AnnotationLite.setCache(this.acmId, annl);
src/main/java/org/ecocean/Annotation.java:1917:        if (this.acmId == null) return;
src/main/java/org/ecocean/Annotation.java:1918:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
src/main/java/org/ecocean/Annotation.java:1924:        Util.mark("Annotation.refreshLiteIndividual() refreshing " + this.acmId);
src/main/java/org/ecocean/Annotation.java:1925:        AnnotationLite.setCache(this.acmId, annl);
src/main/java/org/ecocean/Annotation.java:1929:        if (this.acmId == null) return;
src/main/java/org/ecocean/Annotation.java:1930:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
src/main/java/org/ecocean/Annotation.java:1936:        Util.mark("Annotation.refreshLiteValid() refreshing " + this.acmId);
src/main/java/org/ecocean/Annotation.java:1937:        AnnotationLite.setCache(this.acmId, annl);
src/main/java/org/ecocean/Annotation.java:2047:    public Embedding getEmbeddingByMethod(String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:2054:            if ((methodVersion == null) || (methodVersion.equals(emb.getMethodVersion())))

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '170,260p;2035,2065p'" in /mnt/c/Wildbook-clean2
 succeeded in 293ms:
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

exec
/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|name=\\\"features\\\"|mapped-by=\\\"annotation\\\"|mapped-by=\\\"asset\\\"|class name=\\\"Annotation\\\"|class name=\\\"MediaAsset\\\"|class name=\\\"Embedding\\\"|ANNOTATION_ID|dependent-element\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/media/package.jdo src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/media/package.jdo | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/Feature.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
 succeeded in 316ms:
     1	package org.ecocean.media;
     2	
     3	import java.io.IOException;
     4	import java.util.HashMap;
     5	import javax.servlet.http.HttpServletRequest;
     6	import org.apache.commons.lang3.builder.ToStringBuilder;
     7	import org.ecocean.Annotation;
     8	import org.ecocean.Util;
     9	import org.json.JSONException;
    10	import org.json.JSONObject;
    11	
    12	/**
    13	 * A Feature is a unique set of (arbitrary) data that contributes to helping with detection and identification of images (or other data, e.g. audio).
    14	 * It is metadata that can be generated either manually or automated (or both).
    15	 */
    16	public class Feature implements java.io.Serializable {
    17	    static final long serialVersionUID = 8844223450443974780L;
    18	    protected String id = null;
    19	
    20	    // NOTE: a null for feature type we call the "unity" case -- meaning it serves only as a direct map from Annotation to MediaAsset.
    21	    // this can be thought of as "no" feature, or the "entire asset" feature, etc. (ostensibly parameters would be ignored/useless, but that may
    22	    // change in the future)
    23	    protected FeatureType type;
    24	
    25	    protected JSONObject parameters;
    26	    protected String parametersAsString;
    27	
    28	    // this link back to the objs with .features that include us
    29	    protected Annotation annotation;
    30	    protected MediaAsset asset;
    31	
    32	    protected long revision;
    33	
    34	    // effectively creates a "unity" feature
    35	    public Feature() {
    36	        this(Util.generateUUID(), null, null);
    37	    }
    38	/*
    39	    public Feature(final String typeAsString, final JSONObject params) {
    40	        this(new FeatureType(typeAsString), params);
    41	    }
    42	 */
    43	    public Feature(final FeatureType type, final JSONObject params) {
    44	        this(Util.generateUUID(), type, params);
    45	    }
    46	
    47	    public Feature(final String featureTypeId, final JSONObject params) {
    48	        this(Util.generateUUID(), FeatureType.load(featureTypeId), params);
    49	    }
    50	
    51	    public Feature(final String id, final FeatureType type, final JSONObject params) {
    52	        if (id == null) throw new IllegalArgumentException("id is null");
    53	        this.id = id;
    54	        this.type = type;
    55	        this.parameters = params;
    56	        if (params != null) this.parametersAsString = params.toString();
    57	        this.setRevision();
    58	    }
    59	
    60	    public String getId() {
    61	        return id;
    62	    }
    63	
    64	    public void setId(String i) {
    65	        id = i;
    66	    }
    67	
    68	    public FeatureType getType() {
    69	        return type;
    70	    }
    71	
    72	    public void setType(FeatureType t) {
    73	        type = t;
    74	    }
    75	
    76	    public boolean isType(String tid) { // pass in string version of FeatureType.id (e.g. "org.ecocean.fubar")
    77	        if ((type == null) && (tid == null)) return true; // who would really do this?
    78	        if (type == null) return false;
    79	        return type.getId().equals(tid);
    80	    }
    81	
    82	    public boolean isUnity() {
    83	        return (type == null);
    84	    }
    85	
    86	    public Annotation getAnnotation() {
    87	        return annotation;
    88	    }
    89	
    90	    /**
    91	     * Explicitly sets the owning Annotation. The {@code Annotation.features}
    92	     * collection is {@code mapped-by="annotation"} in the JDO mapping, so the
    93	     * Annotation side is the inverse and this side is the owner; the owner
    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
    95	     * populate reliably without depending on DataNucleus relationship
    96	     * management. {@code MediaAsset.addFeature(f)} already sets
    97	     * {@code Feature.asset}; this method is the symmetric setter for the
    98	     * Annotation side.
    99	     */
   100	    public void setAnnotation(Annotation ann) {
   101	        this.annotation = ann;
   102	    }
   103	
   104	    public MediaAsset getMediaAsset() {
   105	        return asset;
   106	    }
   107	
   108	    public JSONObject getParameters() {
   109	        if (parameters != null) return parameters;
   110	        if (parametersAsString == null) return null;
   111	        try {
   112	            parameters = new JSONObject(parametersAsString);
   113	        } catch (JSONException je) {
   114	            System.out.println(this + " -- error parsing parameters json string (" +
   115	                parametersAsString + "): " + je.toString());
   116	            return null;
   117	        }
   118	        return parameters;
   119	    }
   120	
   121	    public void setParameters(JSONObject p) {
   122	        parameters = p;
   123	        parametersAsString = (p == null) ? null : p.toString();
   124	    }
   125	
   126	    // only DataNucleus should be calling get/setParametersAsString. always use get/setParameters() instead.
   127	    public String getParametersAsString() {
   128	        if (parametersAsString != null) return parametersAsString;
   129	        if (parameters == null) return null;
   130	        parametersAsString = parameters.toString();
   131	        return parametersAsString;
   132	    }
   133	
   134	    public void setParametersAsString(String p) {
   135	        parametersAsString = p;
   136	        parameters = null; // force lazy re-parse on next getParameters()
   137	    }
   138	
   139	    public long getRevision() {
   140	        return revision;
   141	    }
   142	
   143	    public long setRevision() {
   144	        this.revision = System.currentTimeMillis();
   145	        return this.revision;
   146	    }
   147	
   148	    public String toString() {
   149	        return new ToStringBuilder(this)
   150	                   .append("id", id)
   151	                   .append("rev", revision)
   152	                   .append("type", type)
   153	                   .append("asset", asset)
   154	                   .toString();
   155	    }
   156	
   157	    /*
   158	        this is how we derive a MediaAsset from the source MediaAsset this Feature references.
   159	        for images, this might do some kind of clipping or rotation etc.  perhaps in the future this may need to return N MediaAssets?
   160	        ideally we would break this out into neater classes, perhaps per MediaAsset type (image, video, sound)
   161	     */
   162	    public MediaAsset createMediaAsset()
   163	    throws IOException {
   164	        MediaAsset ma = this.getMediaAsset();
   165	
   166	        if (ma == null) return null;
   167	        if (this.isUnity()) return null; // we shouldnt make a new MA that is identical, right?
   168	        HashMap<String, Object> hmap = new HashMap<String, Object>();
   169	        hmap.put("feature", this);
   170	        return ma.updateChild("feature", hmap);
   171	    }
   172	
   173	    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
   174	        boolean fullAccess)
   175	    throws org.datanucleus.api.rest.orgjson.JSONException {
   176	        org.datanucleus.api.rest.orgjson.JSONObject jobj =
   177	            new org.datanucleus.api.rest.orgjson.JSONObject();
   178	        jobj.put("id", id);
   179	        if (this.getType() != null) jobj.put("type", this.getType().getId());
   180	        if (this.getParameters() != null)

 succeeded in 330ms:
     1	<!DOCTYPE jdo PUBLIC "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN" "http://java.sun.com/dtd/jdo_2_0.dtd">
     2	
     3	<jdo>
     4	  <package name="org.ecocean">
     5	
     6	
     7	    <class name="Survey" identity-type="application" table="SURVEY">
     8	      <field name="surveyID" primary-key="true">
     9	        <column length="100" allows-null="true"/>
    10	      </field>
    11	
    12	      <field name="surveyTracks" persistence-modifier="persistent">
    13	        <collection element-type="org.ecocean.movement.SurveyTrack" allow-null="true"/>
    14	        <join/>
    15	      </field>
    16	
    17	      <field name="project" persistence-modifier="persistent">
    18	        <column length="256" allows-null="true" />
    19	      </field>
    20	
    21	      <field name="organization" persistence-modifier="persistent">
    22	        <column length="256" allows-null="true"/>
    23	      </field>
    24	
    25	      <field name="comments" persistence-modifier="persistent">
    26	        <column length="2048" allows-null="true"/>
    27	      </field>
    28	
    29	      <field name="type" persistence-modifier="persistent">
    30	        <column length="100" allows-null="true"/>
    31	      </field>
    32	
    33	      <field name="startTime" persistence-modifier="persistent">
    34	        <column jdbc-type="BIGINT" allows-null="true"/>
    35	      </field>
    36	
    37	      <field name="endTime" persistence-modifier="persistent">
    38	        <column jdbc-type="BIGINT" allows-null="true"/>
    39	      </field>
    40	
    41	      <field name="effort" persistence-modifier="persistent" element-type="org.ecocean.Measurement" allows-null="true"/>
    42	
    43	      <field name="dateTimeCreated" persistence-modifier="persistent">
    44	        <column jdbc-type="VARCHAR" allows-null="true"/>
    45	      </field>
    46	
    47	      <field name="dateTimeModified" persistence-modifier="persistent">
    48	        <column jdbc-type="VARCHAR" allows-null="true"/>
    49	      </field>
    50	
    51	      <field name="date" persistence-modifier="persistent">
    52	        <column jdbc-type="VARCHAR" allows-null="true"/>
    53	      </field>
    54	
    55	      <field name="observations" persistence-modifier="persistent" default-fetch-group="true">
    56	        <collection element-type="org.ecocean.Observation" dependent-element="true" />
    57	        <join/>
    58	      </field>
    59	
    60	      <fetch-group name="count"/>
    61	    </class>
    62	
    63	    <class name="PointLocation" identity-type="application" table="POINTLOCATION">
    64	      <field name="pointLocationID" primary-key="true">
    65	        <column length="100"/>
    66	      </field>
    67	
    68	 	  <field name="elevation" persistence-modifier="persistent" element-type="org.ecocean.Measurement" allows-null="true"/>
    69	
    70	      <field name="correspondingPathID" persistence-modifier="persistent">
    71	        <column length="100" allows-null="true" />
    72	      </field>
    73	
    74	      <field name="correspondingEncounterID" persistence-modifier="persistent">
    75	        <column length="100" allows-null="true"/>
    76	      </field>
    77	
    78	      <field name="correspondingOccurrenceID" persistence-modifier="persistent">
    79	        <column length="100" allows-null="true"/>
    80	      </field>
    81	
    82	      <field name="dateTime" persistence-modifier="persistent">
    83	        <column jdbc-type="BIGINT" allows-null="true"/>
    84	      </field>
    85	
    86	      <field name="latitude" persistence-modifier="persistent">
    87	        <column jdbc-type="DOUBLE" allows-null="true"/>
    88	      </field>
    89	
    90	      <field name="longitude" persistence-modifier="persistent">
    91	        <column jdbc-type="DOUBLE" allows-null="true"/>
    92	      </field>
    93	
    94	      <field name="bearing" persistence-modifier="persistent">
    95	        <column jdbc-type="DOUBLE" allows-null="true"/>
    96	      </field>
    97	
    98	      <fetch-group name="count"/>
    99	    </class>
   100	
   101	    <class name="MarkedIndividual" identity-type="application" table="MARKEDINDIVIDUAL">
   102	      <field name="individualID" primary-key="true" />
   103	
   104			<field jdbc-type="BIGINT" name="version" allows-null="false" >
   105				<column default-value="-1" />
   106				<index name="MARKEDINDIVIDUAL_VERSION_IDX" />
   107			</field>
   108	
   109	      	<field name="names" persistence-modifier="persistent" element-type="org.ecocean.MultiValue" default-fetch-group="true" />
   110	
   111	      <field name="comments" persistence-modifier="persistent" default-fetch-group="false">
   112	        <column jdbc-type="LONGVARCHAR"/>
   113	      </field>
   114	      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true" mapped-by="individual" >
   115	        <collection element-type="org.ecocean.Encounter" />
   116	        <join/>
   117	      </field>
   118	
   119	      <field name="dataFiles" persistence-modifier="persistent">
   120	        <collection element-type="java.lang.String"/>
   121	      </field>
   122	      <field name="interestedResearchers" persistence-modifier="persistent">
   123	        <collection element-type="java.lang.String"/>
   124	      </field>
   125	
   126	      <field name="dateTimeCreated" persistence-modifier="persistent">
   127	        <column jdbc-type="LONGVARCHAR"/>
   128	      </field>
   129	      <field name="alternateid" persistence-modifier="persistent">
   130	        <column length="512"/>
   131	      </field>
   132	
   133	      <field name="maxYearsBetweenResightings" persistence-modifier="persistent">
   134	        <column jdbc-type="INTEGER" allows-null="true"/>
   135	      </field>
   136	
   137	      <field name="timeOfDeath" persistence-modifier="persistent">
   138	        <column jdbc-type="BIGINT" allows-null="true"/>
   139	      </field>
   140	      <field name="timeOfBirth" persistence-modifier="persistent">
   141	        <column jdbc-type="BIGINT" allows-null="true"/>
   142	      </field>
   143	
   144	      <field name="numberLocations" persistence-modifier="persistent">
   145	        <column jdbc-type="INTEGER" allows-null="true"/>
   146	      </field>
   147	      <field name="thumbnailUrl" persistence-modifier="persistent">
   148	        <column allows-null="true"/>
   149	      </field>
   150	
   151	      <fetch-group name="allSharks_min">
   152	        <field name="encounters"/>
   153	      </fetch-group>
   154	
   155	
   156		      <fetch-group name="indymin">
   157	        <field name="individualID"/>
   158	      </fetch-group>
   159	
   160	      <fetch-group name="count"/>
   161	
   162	    </class>
   163	
   164	    <class name="Occurrence" identity-type="application" table="OCCURRENCE">
   165	      <inheritance strategy="new-table"/>
   166	      <field name="occurrenceID" primary-key="true">
   167	        <column length="100"/>
   168	      </field>
   169	
   170	      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true">
   171	        <collection element-type="org.ecocean.Encounter"/>
   172	        <join/>
   173	      </field>
   174	
   175	      <field name="behaviors" persistence-modifier="persistent" default-fetch-group="true">
   176	        <collection element-type="org.ecocean.datacollection.Instant"/>
   177	        <join/>
   178	      </field>
   179	
   180	      <field name="taxonomies" persistence-modifier="persistent" default-fetch-group="true">
   181	        <collection element-type="org.ecocean.Taxonomy" dependent-element="false"/>
   182	        <join/><!--
   183	          <join column="OCCURRENCE_ID_OID" default-value="-1"/>
   184	          <element column="TAXONOMY_ID_EID"/>-->
   185	      </field>
   186	
   187	      <field name="comments" persistence-modifier="persistent" default-fetch-group="false">
   188	        <column jdbc-type="LONGVARCHAR"/>
   189	      </field>
   190	
   191		<field name="submitters" persistence-modifier="persistent" default-fetch-group="true">
   192			<collection element-type="org.ecocean.User" dependent-element="false" />
   193			<join/>
   194		</field>
   195	
   196	      <field name="informOthers" persistence-modifier="persistent" default-fetch-group="true">
   197	        <collection element-type="org.ecocean.User" dependent-element="false"/>
   198	        <join/>
   199	      </field>
   200	
   201	      <field name="groupBehavior" persistence-modifier="persistent">
   202	        <column length="1024" allows-null="true"/>
   203	      </field>
   204	
   205	       <field name="socialMediaSourceID" persistence-modifier="persistent">
   206	        <column length="1024" allows-null="true"/>
   207	      </field>
   208		       <field name="socialMediaQueryCommentID" persistence-modifier="persistent">
   209	        <column length="1024" allows-null="true"/>
   210	      </field>
   211		       <field name="socialMediaQueryCommentReplies" persistence-modifier="persistent">
   212	        <column length="1024" allows-null="true"/>
   213	      </field>
   214	
   215	      <field name="individualCount" persistence-modifier="persistent">
   216	        <column jdbc-type="INTEGER" allows-null="true"/>
   217	      </field>
   218	
   219	      <field name="observations" persistence-modifier="persistent" default-fetch-group="true">
   220	        <collection element-type="org.ecocean.Observation" dependent-element="true" />

 succeeded in 359ms:
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
    91	 			<discriminator value="URL"/>
    92	 		</inheritance>
    93	 	</class>
    94	
    95	 	<class name="YouTubeAssetStore">
    96	 		<inheritance strategy="superclass-table">
    97	 			<discriminator value="YouTube"/>
    98	 		</inheritance>
    99	 	</class>
   100	
   101		<class name="MediaAssetSet">
   102			<field name="id" primary-key="true">
   103				<column length="36" />
   104			</field>
   105			<field name="assets" persistence-modifier="persistent" default-fetch-group="false">
   106				<collection element-type="org.ecocean.media.MediaAsset" dependent-element="true" />
   107				<join />
   108			</field>
   109		</class>
   110	
   111		<class name="Feature" identity-type="application">
   112			<field name="id" primary-key="true">
   113				<column length="36" />
   114			</field>
   115			<field name="type" persistence-modifier="persistent" element-type="org.ecocean.media.FeatureType" allows-null="true" />
   116	            	<field name="revision" persistence-modifier="persistent">
   117	                	<column jdbc-type="BIGINT" allows-null="false"/>
   118	            	</field>
   119			<field name="parametersAsString" persistence-modifier="persistent">
   120	                	<column jdbc-type="LONGVARCHAR" name="parameters" />
   121			</field>
   122	
   123			<field name="annotation" />
   124			<field name="asset" />
   125		</class>
   126	
   127		<class name="FeatureType" identity-type="application">
   128			<field name="id" primary-key="true">
   129				<column length="255" />
   130			</field>
   131			<field name="description" persistence-modifier="persistent" />
   132		</class>
   133	
   134	
   135	
   136	    </package>
   137	</jdo>

 succeeded in 428ms:
src/main/resources/org/ecocean/media/package.jdo:8:	<class name="MediaAsset" identity-type="application">
src/main/resources/org/ecocean/media/package.jdo:54:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" mapped-by="asset" >
src/main/resources/org/ecocean/media/package.jdo:55:                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
src/main/resources/org/ecocean/media/package.jdo:60:                	<collection element-type="org.ecocean.Keyword" dependent-element="false" />
src/main/resources/org/ecocean/media/package.jdo:106:			<collection element-type="org.ecocean.media.MediaAsset" dependent-element="true" />
src/main/resources/org/ecocean/package.jdo:56:        <collection element-type="org.ecocean.Observation" dependent-element="true" />
src/main/resources/org/ecocean/package.jdo:181:        <collection element-type="org.ecocean.Taxonomy" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:192:		<collection element-type="org.ecocean.User" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:197:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:220:        <collection element-type="org.ecocean.Observation" dependent-element="true" />
src/main/resources/org/ecocean/package.jdo:225:          <collection element-type="org.ecocean.media.MediaAsset" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:243:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:248:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:253:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:413:        <collection element-type="org.ecocean.genetics.TissueSample" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:418:        <collection element-type="org.ecocean.SinglePhotoVideo" dependent-element="true"/>
src/main/resources/org/ecocean/package.jdo:423:        <collection element-type="org.ecocean.Annotation" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:428:        <collection element-type="org.ecocean.Measurement" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:433:        <collection element-type="org.ecocean.tag.MetalTag" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:438:        <collection element-type="org.ecocean.Observation" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:538:    <class name="Annotation" identity-type="application">
src/main/resources/org/ecocean/package.jdo:593:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
src/main/resources/org/ecocean/package.jdo:594:                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
src/main/resources/org/ecocean/package.jdo:617:	<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
src/main/resources/org/ecocean/package.jdo:618:		<collection element-type="org.ecocean.Embedding" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:746:        <collection element-type="org.ecocean.User" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:751:        <collection element-type="org.ecocean.Encounter" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:879:                	<collection element-type="org.ecocean.Organization" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:1031:                	<collection element-type="org.ecocean.Organization" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:1041:	<class name="Embedding" identity-type="application">
src/main/resources/org/ecocean/package.jdo:1048:			<column name="ANNOTATION_ID"/>
src/main/resources/org/ecocean/package.jdo:1056:		<field name="annotation" dependent-element="false" >
src/main/resources/org/ecocean/package.jdo:1057:			<column name="ANNOTATION_ID" />
src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
src/main/java/org/ecocean/media/Feature.java:92:     * collection is {@code mapped-by="annotation"} in the JDO mapping, so the
src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '530,630p;1038,1062p'" in /mnt/c/Wildbook-clean2
 succeeded in 292ms:
   530			<field name="modified" jdbc-type="BIGINT" />
   531		</class>
   532	
   533	
   534	    <class name="SuperSpot">
   535	      <datastore-identity strategy="increment"/>
   536	    </class>
   537	
   538	    <class name="Annotation" identity-type="application">
   539	      <field name="id" primary-key="true">
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
   611			<field name="annot_width" />
   612			<field name="annot_height" />
   613			<field name="annot_theta" />
   614			<field name="species_text" />
   615	-->
   616	
   617		<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
   618			<collection element-type="org.ecocean.Embedding" dependent-element="false" />
   619		</field>
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

exec
/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|MEDIAASSET_ID|ASSET_ID|FEATURES|ANNOTATION_ID" archive src/test src/main/java | head -200' in /mnt/c/Wildbook-clean2
 succeeded in 858ms:
archive/sql/ml_service_embedding_audit.sql:4:-- (ANNOTATION_ID, METHOD, METHODVERSION) is created by JDO in
archive/sql/ml_service_embedding_audit.sql:11:SELECT "ANNOTATION_ID", "METHOD", "METHODVERSION", COUNT(*) AS dup_count
archive/sql/ml_service_embedding_audit.sql:13:GROUP BY "ANNOTATION_ID", "METHOD", "METHODVERSION"
archive/sql/ml_service_embedding_audit.sql:15:ORDER BY dup_count DESC, "ANNOTATION_ID";
archive/sql/ml_service_idempotency.sql:23:ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
archive/sql/ml_service_idempotency.sql:39:                    "MEDIAASSET_ID_OID" IS NOT NULL AND
src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
src/main/java/org/ecocean/Annotation.java:601:     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2
src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '760,795p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '590,610p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba archive/sql/ml_service_idempotency.sql | sed -n '1,70p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '300,335p'" in /mnt/c/Wildbook-clean2
 succeeded in 268ms:
     1	-- ml-service migration v2 (commit #4): annotation idempotency + WBIA-registration backfill
     2	--
     3	-- This script complements the JDO mapping in src/main/resources/org/ecocean/package.jdo.
     4	-- DataNucleus auto-creates the new ANNOTATION columns (PREDICTMODELID, BBOXKEY, THETAKEY,
     5	-- WBIAREGISTERED, WBIAREGISTERATTEMPTS) on next startup. This file adds the parts
     6	-- DataNucleus cannot auto-create: a Postgres partial unique index for ml-service
     7	-- idempotency, a CHECK constraint that the composite columns are populated together,
     8	-- and a one-time backfill marking legacy annotations as already-registered with WBIA.
     9	--
    10	-- Safe to re-run. Each statement is either idempotent (CREATE INDEX IF NOT
    11	-- EXISTS, ALTER COLUMN, ADD CONSTRAINT guarded by pg_constraint lookup) or
    12	-- filters on the pre-backfill state (UPDATEs touching only NULL rows).
    13	
    14	BEGIN;
    15	
    16	-- (1) Partial unique index: idempotency for ml-service-created annotations.
    17	--     Filters on PREDICTMODELID IS NOT NULL so legacy WBIA-era rows are unaffected.
    18	--     NOTE: Postgres unique indexes treat NULL as distinct, so any of the four
    19	--     composite columns being NULL would defeat the constraint. The CHECK
    20	--     constraint below guarantees the other three are also non-null when
    21	--     PREDICTMODELID is non-null.
    22	CREATE UNIQUE INDEX IF NOT EXISTS "ANNOTATION_MLSERVICE_IDEM_idx"
    23	ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
    24	WHERE "PREDICTMODELID" IS NOT NULL;
    25	
    26	-- (2) CHECK constraint: when PREDICTMODELID is non-null, the other composite
    27	--     columns must also be non-null. Defense in depth against partial fills
    28	--     that would silently bypass the partial unique index.
    29	DO $$
    30	BEGIN
    31	    IF NOT EXISTS (
    32	        SELECT 1 FROM pg_constraint
    33	        WHERE conname = 'annotation_mlservice_composite_check'
    34	          AND conrelid = '"ANNOTATION"'::regclass
    35	    ) THEN
    36	        ALTER TABLE "ANNOTATION" ADD CONSTRAINT annotation_mlservice_composite_check
    37	            CHECK (
    38	                "PREDICTMODELID" IS NULL OR (
    39	                    "MEDIAASSET_ID_OID" IS NOT NULL AND
    40	                    "BBOXKEY" IS NOT NULL AND
    41	                    "THETAKEY" IS NOT NULL
    42	                )
    43	            );
    44	    END IF;
    45	END $$;
    46	
    47	-- (3) Harden WBIAREGISTERATTEMPTS at the SQL level. DataNucleus creates the
    48	--     column from package.jdo with allows-null=false + default 0, but if an
    49	--     older DataNucleus run on this deployment already created it without
    50	--     those properties (rare but possible), this block repairs it idempotently.
    51	--     SET DEFAULT first so new rows get 0; backfill NULLs to 0 (with VERSION
    52	--     bump for OpenSearch reindex); then SET NOT NULL.
    53	ALTER TABLE "ANNOTATION"
    54	    ALTER COLUMN "WBIAREGISTERATTEMPTS" SET DEFAULT 0;
    55	
    56	UPDATE "ANNOTATION"
    57	SET "WBIAREGISTERATTEMPTS" = 0,
    58	    "VERSION" = (EXTRACT(EPOCH FROM now()) * 1000)::bigint
    59	WHERE "WBIAREGISTERATTEMPTS" IS NULL;
    60	
    61	ALTER TABLE "ANNOTATION"
    62	    ALTER COLUMN "WBIAREGISTERATTEMPTS" SET NOT NULL;
    63	
    64	-- (4) One-time WBIA-registration backfill: legacy annotations that already
    65	--     have an acmId were registered with WBIA via the historical IBEISIA
    66	--     flow. Mark them as registered so the new background-polling thread
    67	--     does NOT re-register them.
    68	--
    69	--     Bumps VERSION in the same statement per the repo rule for direct SQL
    70	--     writes to indexed ANNOTATION rows (OpenSearch reindexer reads VERSION).

 succeeded in 286ms:
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

 succeeded in 295ms:
   590	       JSONObject iaViewpointFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException,
   591	       NoSuchAlgorithmException, InvalidKeyException {
   592	 */
   593	
   594	// TODO: Deprecate "all of this" now that deployed sites are migrated
   595	    public MediaAsset __getMediaAsset() {
   596	        return mediaAsset;
   597	    }
   598	
   599	    /**
   600	     * Direct setter for the {@code mediaAsset} field — populates the
   601	     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2
   602	     * partial unique index on (mediaAsset, predictModelId, bboxKey, thetaKey)
   603	     * needs this column to be populated on ml-service-created annotations.
   604	     * Bumps version for OpenSearch reindex pickup. Mirrors the
   605	     * {@link #__getMediaAsset} naming convention used by infrastructure
   606	     * code (use {@link #getMediaAsset()} when you want the proper
   607	     * Feature-based linkage).
   608	     */
   609	    public void __setMediaAsset(MediaAsset ma) {
   610	        this.mediaAsset = ma;

 succeeded in 331ms:
   300	                params.put("transformMatrix", getTransformMatrix());
   301	            } else {
   302	                params.put("x", getX());
   303	                params.put("y", getY());
   304	            }
   305	            f = new Feature("org.ecocean.boundingBox", params);
   306	        }
   307	        __getMediaAsset().addFeature(f);
   308	        addFeature(f);
   309	        return f;
   310	    }
   311	
   312	    public void setAcmId(String id) {
   313	        this.acmId = id;
   314	        this.setVersion();
   315	    }
   316	
   317	    public String getAcmId() {
   318	        return this.acmId;
   319	    }
   320	
   321	    public boolean hasAcmId() {
   322	        return (this.acmId != null);
   323	    }
   324	
   325	    // ----- ml-service migration v2 idempotency / WBIA-registration accessors -----
   326	
   327	    public String getPredictModelId() { return predictModelId; }
   328	    public void setPredictModelId(String s) { this.predictModelId = s; this.setVersion(); }
   329	
   330	    public String getBboxKey() { return bboxKey; }
   331	    public void setBboxKey(String s) { this.bboxKey = s; this.setVersion(); }
   332	
   333	    public String getThetaKey() { return thetaKey; }
   334	    public void setThetaKey(String s) { this.thetaKey = s; this.setVersion(); }
   335	

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/bulk/BulkImporter.java | sed -n '680,730p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "skipAutoIndexing|new Annotation|setMatchAgainst|setAcmId|setSkipAutoIndexing|addAnnotation|addFeature|__setMediaAsset" src/main/java/org/ecocean/api/bulk/BulkImporter.java src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '1488,1520p;1748,1772p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Encounter.java | sed -n '5450,5570p'" in /mnt/c/Wildbook-clean2
 succeeded in 254ms:
   680	        System.out.println("+ populated data on " + enc);
   681	        // now attach annotations
   682	        String tx = enc.getTaxonomyString();
   683	        List<Annotation> annots = new ArrayList<Annotation>();
   684	        int offset = 0;
   685	        for (String maKey : maFields) {
   686	            if (maKey == null) continue; // data skipped an index
   687	            BulkValidator bv = fmap.get(maKey);
   688	            if (bv == null) throw new RuntimeException("could not find fmap for key=" + maKey);
   689	            if (bv.valueIsNull()) continue;
   690	            MediaAsset ma = this.mediaAssetMap.get(bv.getValueString());
   691	            if (ma == null)
   692	                throw new RuntimeException("could not find MediaAsset for maKey=" + maKey +
   693	                        ", bv=" + bv.getValueString() + " in " + this.mediaAssetMap);
   694	            Set<String> kws = new HashSet<String>();
   695	            if ((offset < kwFields.size()) && (kwFields.get(offset) != null))
   696	                kws.add(fmap.get(kwFields.get(offset)).getValueString());
   697	            // StandardImport claims multivalue keywordS is delimited by underscore :/ is this for real?
   698	            if ((offset < multiKwFields.size()) && (multiKwFields.get(offset) != null)) {
   699	                String multi = fmap.get(multiKwFields.get(offset)).getValueString();
   700	                if (multi != null) kws.addAll(Arrays.asList(multi.split("_")));
   701	            }
   702	            handleKeywords(ma, kws);
   703	            Annotation ann = new Annotation(tx, ma);
   704	            ann.setIsExemplar(true);
   705	            ann.setSkipAutoIndexing(true);
   706	            if ((offset < maQuality.size()) && (maQuality.get(offset) != null))
   707	                ann.setQuality(fmap.get(maQuality.get(offset)).getValueDouble());
   708	            annots.add(ann);
   709	            offset++;
   710	        }
   711	        if (annots.size() > 0) enc.addAnnotations(annots);
   712	        System.out.println("+ populated " + annots.size() + " MediaAssets on " + enc);
   713	    }
   714	
   715	    public void markProgress(int ticks, int total, double base, double weight) {
   716	        if (this.importTaskId == null) return;
   717	        // we want our own shepherd here so we can persist this task independent of our main shepherd
   718	        Shepherd taskShepherd = new Shepherd(this.myShepherd.getContext());
   719	        taskShepherd.setAction("BulkImporter.markProgress");
   720	        taskShepherd.beginDBTransaction();
   721	        try {
   722	            ImportTask itask = taskShepherd.getImportTask(this.importTaskId);
   723	            if (itask == null) return;
   724	            Double progress = base + (weight * new Double(ticks) / new Double(total));
   725	            itask.setProcessingProgress(progress);
   726	            taskShepherd.storeNewImportTask(itask);
   727	        } catch (Exception ex) {
   728	            ex.printStackTrace();
   729	        } finally {
   730	            taskShepherd.commitDBTransaction();

 succeeded in 281ms:
src/main/java/org/ecocean/Annotation.java:307:        __getMediaAsset().addFeature(f);
src/main/java/org/ecocean/Annotation.java:308:        addFeature(f);
src/main/java/org/ecocean/Annotation.java:312:    public void setAcmId(String id) {
src/main/java/org/ecocean/Annotation.java:364:    public void addFeature(Feature f) {
src/main/java/org/ecocean/Annotation.java:609:    public void __setMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/Annotation.java:754:    public void setMatchAgainst(boolean b) {
src/main/java/org/ecocean/Annotation.java:1364:                ann.setMatchAgainst(false);
src/main/java/org/ecocean/Annotation.java:1383:                    someEnc.addAnnotation(this);
src/main/java/org/ecocean/Annotation.java:1396:                newEnc.addAnnotation(this);
src/main/java/org/ecocean/Annotation.java:1445:        Annotation triv = new Annotation(this.species, ma); // not going to set IAClass or anything since starting fresh
src/main/java/org/ecocean/Annotation.java:1447:        this.setMatchAgainst(false);
src/main/java/org/ecocean/Annotation.java:1449:        enc.addAnnotation(triv);
src/main/java/org/ecocean/Annotation.java:1455:    // creates a new Annotation with the basic properties duplicated (but no "linked" objects, like Features etc)
src/main/java/org/ecocean/Annotation.java:1457:        Annotation ann = new Annotation();
src/main/java/org/ecocean/Annotation.java:1536:        Annotation ann = new Annotation(null, ft, iaClass);
src/main/java/org/ecocean/Annotation.java:1538:        ma.addFeature(ft);
src/main/java/org/ecocean/Annotation.java:1546:                ann.setMatchAgainst(true);
src/main/java/org/ecocean/Annotation.java:1552:            ann.setMatchAgainst(true);
src/main/java/org/ecocean/Annotation.java:1626:                    clone.addAnnotation(ann);
src/main/java/org/ecocean/Annotation.java:1628:                        "\">Encounter cloned and <i>new Annotation</i> manually added by " +
src/main/java/org/ecocean/Annotation.java:1649:                enc.addAnnotation(ann);
src/main/java/org/ecocean/Annotation.java:1651:                    "\"><i>new Annotation</i> manually added by " + user.getDisplayName() + "</p>");
src/main/java/org/ecocean/Annotation.java:1908:            annl = new AnnotationLite(null, tax); // indiv = null here, but it is new so its what we got. :/
src/main/java/org/ecocean/Annotation.java:1920:            annl = new AnnotationLite(indiv);
src/main/java/org/ecocean/Annotation.java:1932:            annl = new AnnotationLite(validForId);
src/main/java/org/ecocean/Encounter.java:1352:            annotations.add(new Annotation(getTaxonomyString(), ma));
src/main/java/org/ecocean/Encounter.java:2892:    public void addAnnotations(List<Annotation> anns) {
src/main/java/org/ecocean/Encounter.java:2899:    public void addAnnotation(Annotation ann) {
src/main/java/org/ecocean/Encounter.java:2907:                ann.setMatchAgainst(use);
src/main/java/org/ecocean/Encounter.java:3089:        Annotation ann = new Annotation(getTaxonomyString(), ma);
src/main/java/org/ecocean/Encounter.java:3110:        addAnnotation(newAnn);
src/main/java/org/ecocean/Encounter.java:5030:        this.setSkipAutoIndexing(true);
src/main/java/org/ecocean/Encounter.java:5057:                occ.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5062:                indiv.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5070:        this.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5230:                goneAnnot.setSkipAutoIndexing(true);
src/main/java/org/ecocean/Encounter.java:5264:                    current.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5383:        current.setSkipAutoIndexing(true);
src/main/java/org/ecocean/media/MediaAsset.java:132:    public void setAcmId(String id) {
src/main/java/org/ecocean/media/MediaAsset.java:443:    public void addFeature(Feature f) {
src/main/java/org/ecocean/media/MediaAsset.java:603:        if (addToMediaAsset) this.addFeature(f);
src/main/java/org/ecocean/media/MediaAsset.java:617:        this.addFeature(f);
src/main/java/org/ecocean/media/MediaAsset.java:661:        Collections.sort(ord, new AnnotationPositionalComparator());
src/main/java/org/ecocean/media/MediaAsset.java:1199:                    ma.setSkipAutoIndexing(true);
src/main/java/org/ecocean/media/MediaAsset.java:1629:                    tann.setMatchAgainst(false);
src/main/java/org/ecocean/media/MediaAsset.java:1640:                        newEnc.addAnnotation(ann);
src/main/java/org/ecocean/media/MediaAsset.java:1663:                    tann.setMatchAgainst(false);
src/main/java/org/ecocean/media/MediaAsset.java:1674:                    whichever.addAnnotation(ann);
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
src/main/java/org/ecocean/api/bulk/BulkImporter.java:111:            ma.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:703:            Annotation ann = new Annotation(tx, ma);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:705:            ann.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:711:        if (annots.size() > 0) enc.addAnnotations(annots);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:903:        indiv.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:940:        enc.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:1014:        occ.setSkipAutoIndexing(true);

 succeeded in 263ms:
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
  1511	        return Task.getRootTasksFor(this, myShepherd);
  1512	    }
  1513	
  1514	    public Boolean isValidImageForIA() {
  1515	        return validImageForIA;
  1516	    }
  1517	
  1518	    public boolean isValidImageForIAForced() {
  1519	        if (validImageForIA == null) return false;
  1520	        return validImageForIA;
  1748	            jgen.writeEndObject();
  1749	        }
  1750	        jgen.writeEndArray();
  1751	    }
  1752	
  1753	    @Override public String getAllVersionsSql() {
  1754	        return
  1755	                "SELECT CAST(\"ID\" AS text), \"REVISION\" AS version FROM \"MEDIAASSET\" WHERE \"PARENTID\" IS NULL ORDER BY version";
  1756	    }
  1757	
  1758	    @Override public Base getById(Shepherd myShepherd, String id) {
  1759	        return myShepherd.getMediaAsset(id);
  1760	    }
  1761	
  1762	    // we override Base version, as we want to (dont we?) always skip auto-indexing children assets
  1763	    public boolean getSkipAutoIndexing() {
  1764	        if (this.skipAutoIndexing) return true;
  1765	        if (this.parentId != null) return true;
  1766	        // TODO making the bold decision to *always* let MediaAsset index in background
  1767	        // as we currently arent even using this index. this is especially helpful as
  1768	        // assets seem to trigger lifecycle postStore() **a lot**.   -jon
  1769	        // return false;
  1770	        return true;
  1771	    }
  1772	

 succeeded in 274ms:
  5450	                error.put("value", returnValue);
  5451	                throw new ApiException(exMessage, error);
  5452	            }
  5453	            break;
  5454	
  5455	        case "decimalLatitude":
  5456	            returnValue = data.optDouble(fieldName, UNSET_LATLON);
  5457	            if ((double)returnValue == UNSET_LATLON) {
  5458	                returnValue = null;
  5459	            } else if (!Util.isValidDecimalLatitude((double)returnValue)) {
  5460	                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
  5461	                error.put("value", returnValue);
  5462	                throw new ApiException(exMessage, error);
  5463	            }
  5464	            break;
  5465	
  5466	        case "decimalLongitude":
  5467	            returnValue = data.optDouble(fieldName, UNSET_LATLON);
  5468	            if ((double)returnValue == UNSET_LATLON) {
  5469	                returnValue = null;
  5470	            } else if (!Util.isValidDecimalLongitude((double)returnValue)) {
  5471	                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
  5472	                error.put("value", returnValue);
  5473	                throw new ApiException(exMessage, error);
  5474	            }
  5475	            break;
  5476	
  5477	        default:
  5478	            System.out.println("Encounter.validateFieldValue(): WARNING unsupported fieldName=" +
  5479	                fieldName);
  5480	        }
  5481	        // must be okay!
  5482	        return returnValue;
  5483	    }
  5484	
  5485	    // basically ripped from servlet/EncounterForm
  5486	    public Task sendToIA(Shepherd myShepherd) {
  5487	        Task task = null;
  5488	
  5489	        try {
  5490	            IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
  5491	            if (iaConfig.hasIA(this, myShepherd)) {
  5492	                for (MediaAsset ma : this.getMedia()) {
  5493	                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
  5494	                }
  5495	                Task parentTask = null; // this is *not* persisted, but only used so intakeMediaAssets will inherit its params
  5496	                if (this.getLocationID() != null) {
  5497	                    parentTask = new Task();
  5498	                    org.json.JSONObject tp = new org.json.JSONObject();
  5499	                    org.json.JSONObject mf = new org.json.JSONObject();
  5500	                    mf.put("locationId", this.getLocationID());
  5501	                    tp.put("matchingSetFilter", mf);
  5502	                    parentTask.setParameters(tp);
  5503	                }
  5504	                task = IA.intakeMediaAssets(myShepherd, this.getMedia(), parentTask);
  5505	                myShepherd.storeNewTask(task);
  5506	                System.out.println("sendToIA() success on " + this + " => " + task);
  5507	            } else {
  5508	                System.out.println("sendToIA() skipped; no config for " + this);
  5509	            }
  5510	        } catch (Exception ex) {
  5511	            System.out.println("sendToIA() failed on " + this + ": " + ex);
  5512	            ex.printStackTrace();
  5513	        }
  5514	        return task;
  5515	    }
  5516	
  5517	    // this is based on servlet/MediaAssetCreate and differs only slightly from
  5518	    // above - mainly in that it can handle multiple assets and creates own shepherd
  5519	    // (note: all assets assumed to be using *this encounter*
  5520	    public Task sendToIA(List<Integer> ids, String context) {
  5521	        Task task = null;
  5522	        Shepherd myShepherd = new Shepherd(context);
  5523	
  5524	        myShepherd.setAction("Encounter.sendToIA()");
  5525	        myShepherd.beginDBTransaction();
  5526	        try {
  5527	            IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
  5528	            boolean hasConfig = iaConfig.hasIA(this, myShepherd);
  5529	            List<MediaAsset> allMAs = new ArrayList<MediaAsset>();
  5530	            for (Integer id : ids) {
  5531	                if (id < 0) continue;
  5532	                MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
  5533	                if (ma != null) {
  5534	                    ma.setDetectionStatus(hasConfig ? IBEISIA.STATUS_INITIATED : IBEISIA.STATUS_COMPLETE);
  5535	                    allMAs.add(ma);
  5536	                }
  5537	            }
  5538	            if (!hasConfig) {
  5539	                System.out.println("sendToIA() skipped; no config for " + this);
  5540	            } else if (allMAs.size() > 0) {
  5541	                Task parentTask = null; // not persisted
  5542	                if (this.getLocationID() != null) {
  5543	                    parentTask = new Task();
  5544	                    org.json.JSONObject tp = new org.json.JSONObject();
  5545	                    org.json.JSONObject mf = new org.json.JSONObject();
  5546	                    mf.put("locationId", this.getLocationID());
  5547	                    tp.put("matchingSetFilter", mf);
  5548	                    parentTask.setParameters(tp);
  5549	                }
  5550	                Taxonomy taxy = this.getTaxonomy(myShepherd);
  5551	                if (taxy != null) {
  5552	                    task = IA.intakeMediaAssetsOneSpecies(myShepherd, allMAs, taxy, parentTask);
  5553	                } else {
  5554	                    task = IA.intakeMediaAssets(myShepherd, allMAs);
  5555	                }
  5556	                myShepherd.storeNewTask(task);
  5557	                System.out.println("sendToIA() created " + task + " for " + this);
  5558	            }
  5559	            // persist will catch change on asset detectionStatus regardless
  5560	            myShepherd.commitDBTransaction();
  5561	        } catch (Exception ex) {
  5562	            ex.printStackTrace();
  5563	            myShepherd.rollbackDBTransaction();
  5564	        } finally {
  5565	            myShepherd.closeDBTransaction();
  5566	        }
  5567	        return task;
  5568	    }
  5569	
  5570	    public Set<String> getNotificationEmailAddresses() {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '260,390p'" in /mnt/c/Wildbook-clean2
 succeeded in 284ms:
   260	                    }
   261	                    jgen.writeEndArray();
   262	                }
   263	                jgen.writeEndObject();
   264	            }
   265	        jgen.writeEndArray();
   266	    }
   267	
   268	    // TODO should this also be limited by matchAgainst and acmId?
   269	    @Override public String getAllVersionsSql() {
   270	        return
   271	                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
   272	    }
   273	
   274	    @Override public Base getById(Shepherd myShepherd, String id) {
   275	        return myShepherd.getAnnotation(id);
   276	    }
   277	
   278	    // comment cruft only needed for Base class
   279	    @Override public String getComments() {
   280	        return null;
   281	    }
   282	
   283	    @Override public void setComments(final String comments) {
   284	    }
   285	
   286	    @Override public void addComments(final String newComments) {
   287	    }
   288	
   289	    // this is for use *only* to migrate old-world Annotations to new-world
   290	    public Feature migrateToFeatures() {
   291	        Feature f;
   292	
   293	        if (isTrivial()) { // this gets special "unity" feature, which means the whole thing basically
   294	            f = new Feature();
   295	        } else {
   296	            JSONObject params = new JSONObject();
   297	            params.put("width", getWidth());
   298	            params.put("height", getHeight());
   299	            if (needsTransform()) {
   300	                params.put("transformMatrix", getTransformMatrix());
   301	            } else {
   302	                params.put("x", getX());
   303	                params.put("y", getY());
   304	            }
   305	            f = new Feature("org.ecocean.boundingBox", params);
   306	        }
   307	        __getMediaAsset().addFeature(f);
   308	        addFeature(f);
   309	        return f;
   310	    }
   311	
   312	    public void setAcmId(String id) {
   313	        this.acmId = id;
   314	        this.setVersion();
   315	    }
   316	
   317	    public String getAcmId() {
   318	        return this.acmId;
   319	    }
   320	
   321	    public boolean hasAcmId() {
   322	        return (this.acmId != null);
   323	    }
   324	
   325	    // ----- ml-service migration v2 idempotency / WBIA-registration accessors -----
   326	
   327	    public String getPredictModelId() { return predictModelId; }
   328	    public void setPredictModelId(String s) { this.predictModelId = s; this.setVersion(); }
   329	
   330	    public String getBboxKey() { return bboxKey; }
   331	    public void setBboxKey(String s) { this.bboxKey = s; this.setVersion(); }
   332	
   333	    public String getThetaKey() { return thetaKey; }
   334	    public void setThetaKey(String s) { this.thetaKey = s; this.setVersion(); }
   335	
   336	    public Boolean getWbiaRegistered() { return wbiaRegistered; }
   337	    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
   338	
   339	    // Convenience: hides the tri-state from frontend JSON. Returns true only
   340	    // when the column is explicitly TRUE.
   341	    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
   342	
   343	    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
   344	    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
   345	    public void incrementWbiaRegisterAttempts() {
   346	        this.wbiaRegisterAttempts++;
   347	        this.setVersion();
   348	    }
   349	
   350	    public ArrayList<Feature> getFeatures() {
   351	        return features;
   352	    }
   353	
   354	    public Feature getFeature() {
   355	        if (Util.collectionSize(features) < 1) return null;
   356	        return features.get(0);
   357	    }
   358	
   359	    public void setFeatures(ArrayList<Feature> f) {
   360	        features = f;
   361	        this.setVersion();
   362	    }
   363	
   364	    public void addFeature(Feature f) {
   365	        if (features == null) features = new ArrayList<Feature>();
   366	        if (!features.contains(f)) features.add(f);
   367	        this.setVersion();
   368	    }
   369	
   370	    public String getId() {
   371	        return id;
   372	    }
   373	
   374	    public void setId(String id) {
   375	        this.id = id;
   376	        this.setVersion();
   377	    }
   378	
   379	    public Double getQuality() {
   380	        return quality;
   381	    }
   382	
   383	    public void setQuality(Double quality) {
   384	        this.quality = quality;
   385	    }
   386	
   387	    public String getUUID() {
   388	        return id;
   389	    }
   390	

exec
/bin/bash -lc "rg -n \"public Annotation\\(|Annotation\\(\" src/main/java/org/ecocean/Annotation.java | head -40" in /mnt/c/Wildbook-clean2
 succeeded in 295ms:
30:    public Annotation() {}
121:    public Annotation(String species, MediaAsset ma) {
126:    public Annotation(String species, Feature f) {
133:    public Annotation(String species, ArrayList<Feature> f) {
140:    public Annotation(String species, Feature f, String iaClass) {
148:    public Annotation(String species, ArrayList<Feature> f, String iaClass) {
275:        return myShepherd.getAnnotation(id);
1165:            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
1252:            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
1320:        return Encounter.findByAnnotation(this, myShepherd);
1369:                    enc.replaceAnnotation(ann, this);
1383:                    someEnc.addAnnotation(this);
1396:                newEnc.addAnnotation(this);
1445:        Annotation triv = new Annotation(this.species, ma); // not going to set IAClass or anything since starting fresh
1446:        enc.removeAnnotation(this);
1449:        enc.addAnnotation(triv);
1457:        Annotation ann = new Annotation();
1536:        Annotation ann = new Annotation(null, ft, iaClass);
1626:                    clone.addAnnotation(ann);
1649:                enc.addAnnotation(ann);
1669:                enc.removeAnnotation(foundTrivial);
1851:        if (enc != null) enc.removeAnnotation(this);
1884:    public static ArrayList<Encounter> checkForConflictingIDsforAnnotation(Annotation annot,
2027:        if (!this.equals(emb.getAnnotation())) emb.setAnnotation(this);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1,165p'" in /mnt/c/Wildbook-clean2
 succeeded in 281ms:
     1	package org.ecocean;
     2	
     3	import com.fasterxml.jackson.core.JsonGenerator;
     4	import com.fasterxml.jackson.core.JsonProcessingException;
     5	import java.awt.Rectangle;
     6	import java.io.File;
     7	import java.io.IOException;
     8	import java.util.*;
     9	import javax.jdo.Query;
    10	import javax.servlet.http.HttpServletRequest;
    11	import org.apache.commons.codec.digest.DigestUtils;
    12	import org.apache.commons.lang3.builder.ToStringBuilder;
    13	import org.ecocean.api.ApiException;
    14	import org.ecocean.ia.IA;
    15	import org.ecocean.ia.IAException;
    16	import org.ecocean.ia.MatchResult;
    17	import org.ecocean.ia.MatchResultProspect;
    18	import org.ecocean.ia.MLService;
    19	import org.ecocean.ia.Task;
    20	import org.ecocean.identity.IBEISIA;
    21	import org.ecocean.media.Feature;
    22	import org.ecocean.media.FeatureType;
    23	import org.ecocean.media.MediaAsset;
    24	import org.ecocean.media.MediaAssetFactory;
    25	import org.ecocean.shepherd.core.Shepherd;
    26	import org.json.JSONArray;
    27	import org.json.JSONObject;
    28	
    29	public class Annotation extends Base implements java.io.Serializable {
    30	    public Annotation() {}
    31	    private String id;
    32	    public static final int KNN_K_DISTANCE_VALUE = 4;
    33	    private static final String[][] VALID_VIEWPOINTS = new String[][] {
    34	        { "up", "up", "up", "up", "up", "up", "up", "up", }, {
    35	            "upfront", "upfrontright", "upright", "upbackright", "upback", "upbackleft", "upleft",
    36	                "upfrontleft"
    37	        }, { "front", "frontright", "right", "backright", "back", "backleft", "left", "frontleft" },
    38	                {
    39	            "downfront", "downfrontright", "downright", "downbackright", "downback", "downbackleft",
    40	                "downleft", "downfrontleft"
    41	        }, { "down", "down", "down", "down", "down", "down", "down", "down" }
    42	    };
    43	    private String species;
    44	
    45	    private String iaClass;
    46	
    47	    private String name;
    48	    private boolean isExemplar = false;
    49	    private Boolean isOfInterest = null; // aka AoI (Annotation of Interest)
    50	    protected String identificationStatus;
    51	    private ArrayList<Feature> features;
    52	    private Set<Embedding> embeddings;
    53	    protected String acmId;
    54	
    55	    // ----- ml-service migration v2: idempotency + WBIA registration -----
    56	    // (commit #4 of the v2 plan)
    57	    //
    58	    // predictModelId / bboxKey / thetaKey form the composite that uniquely
    59	    // identifies an ml-service-created detection. The partial unique index
    60	    // in archive/sql/ml_service_idempotency.sql guards against concurrent
    61	    // retry creating duplicates. Null on legacy WBIA-era rows and on
    62	    // manually-drawn annotations.
    63	    protected String predictModelId;
    64	    protected String bboxKey;   // literal "x:y:w:h" of rounded ints
    65	    protected String thetaKey;  // theta rounded to 4 decimals, as String
    66	
    67	    // wbiaRegistered drives the DB-backed background poller that tells WBIA
    68	    // about ml-service-created annotations so HotSpotter remains available.
    69	    //
    70	    //   null  — legacy annotation (column is new; starts null on existing
    71	    //           rows). The DDL migration sets nulls to TRUE wherever
    72	    //           acmId IS NOT NULL ("already registered via the historical
    73	    //           IBEISIA flow"). Excluded from polling.
    74	    //   false — new ml-service annotation awaiting WBIA registration.
    75	    //           Polling thread picks these up.
    76	    //   true  — WBIA acknowledged. Terminal success.
    77	    //
    78	    // Contract: MlServiceProcessor MUST set this to false (not null) on
    79	    // new ml-service annotations.
    80	    protected Boolean wbiaRegistered;
    81	
    82	    // Failed-attempt counter. Polling filters wbiaRegisterAttempts < MAX so
    83	    // chronically-failing rows park rather than spin forever.
    84	    protected int wbiaRegisterAttempts = 0;
    85	
    86	    // this is used to decide "should we match against this"  problem is: that is not very (IA-)algorithm agnostic
    87	    // TODO: was this made obsolete by ACM and friends?
    88	    private boolean matchAgainst = false;
    89	
    90	    // TODO: can these (thru mediaAsset) be removed now that there Features?
    91	    private int x;
    92	    private int y;
    93	    private int width;
    94	    private int height;
    95	    private float[] transformMatrix;
    96	    private double theta;
    97	    private long version = System.currentTimeMillis();
    98	
    99	    // quality indicates the fidelity of the annotation, e.g. the overall image quality of a picture.
   100	    // This is useful e.g. for researchers who want to account for a bias where "better" images are
   101	    // more likely to produce matches.
   102	    private Double quality;
   103	    // distinctiveness indicates the real-wold distinctiveness of the feature *being recorded*, independent
   104	    // of the recording medium. Useful e.g. for researchers who want to account for a bias where more distinct
   105	    // animals like one with a large scar are easier to re-sight (match).
   106	    private Double distinctiveness;
   107	    private String viewpoint;
   108	    // *'annot_yaw': 'REAL',
   109	    // ~'annot_detect_confidence': 'REAL',
   110	    // ~'annot_exemplar_flag': 'INTEGER',
   111	    // ~'annot_note': 'TEXT',
   112	    // ~'annot_visual_uuid': 'UUID',
   113	    // ~'annot_semantic_uuid': 'UUID',
   114	    // *'annot_quality': 'INTEGER',
   115	    // ~'annot_tags': 'TEXT',
   116	
   117	    private MediaAsset mediaAsset = null;
   118	    // end of what will go away
   119	
   120	    // the "trivial" Annotation - will have a single feature which references the total MediaAsset
   121	    public Annotation(String species, MediaAsset ma) {
   122	        this(species, ma.generateUnityFeature());
   123	    }
   124	
   125	    // single feature convenience constructor
   126	    public Annotation(String species, Feature f) {
   127	        this.id = Util.generateUUID();
   128	        this.species = species;
   129	        this.features = new ArrayList<Feature>();
   130	        this.features.add(f);
   131	    }
   132	
   133	    public Annotation(String species, ArrayList<Feature> f) {
   134	        this.id = Util.generateUUID();
   135	        this.species = species;
   136	        this.features = f;
   137	    }
   138	
   139	    // For setting the iaClass returned from detection... No more mangled species names sent to identification
   140	    public Annotation(String species, Feature f, String iaClass) {
   141	        this.id = Util.generateUUID();
   142	        this.species = species;
   143	        this.features = new ArrayList<Feature>();
   144	        this.features.add(f);
   145	        this.iaClass = iaClass;
   146	    }
   147	
   148	    public Annotation(String species, ArrayList<Feature> f, String iaClass) {
   149	        this.id = Util.generateUUID();
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '420,470p;580,625p'" in /mnt/c/Wildbook-clean2
 succeeded in 283ms:
   420	        }
   421	    }
   422	
   423	    public void removeLabel(String s) {
   424	        if (labels == null) return;
   425	        ArrayList<String> dup = new ArrayList<String>(labels);
   426	        dup.remove(s);
   427	        labels = dup;
   428	    }
   429	
   430	    public boolean hasLabel(String s) {
   431	        if (labels == null) return false;
   432	        return labels.contains(s);
   433	    }
   434	
   435	    public ArrayList<Feature> getFeatures() {
   436	        return features;
   437	    }
   438	
   439	    public void setFeatures(ArrayList<Feature> f) {
   440	        features = f;
   441	    }
   442	
   443	    public void addFeature(Feature f) {
   444	        if (features == null) features = new ArrayList<Feature>();
   445	        if (!features.contains(f)) {
   446	            features.add(f);
   447	            f.asset = this;
   448	        }
   449	    }
   450	
   451	    // note: this will outright deletes feature (from db, blame datanucleus), and thus will
   452	    // break the reference from Annotation-Feature that (likely) existed ... oops?
   453	    public void removeFeature(Feature f) {
   454	        if (features == null) return;
   455	        System.out.println("INFO: removeFeature() killing off " + f + " from asset id=" + this.id);
   456	        features.remove(f);
   457	    }
   458	
   459	    // kinda sorta really only for Encounter.findAllMediaByFeatureId()
   460	    public boolean hasFeatures(String[] featureIds) {
   461	        if ((features == null) || (features.size() < 1)) return false;
   462	        for (Feature f : features) {
   463	            for (int i = 0; i < featureIds.length; i++) {
   464	                if (f.isType(featureIds[i])) return true; // short-circuit on first match
   465	            }
   466	        }
   467	        return false;
   468	    }
   469	
   470	    public String getRotationInfo() {
   580	        return this.userLongitude;
   581	    }
   582	
   583	    public Double getLongitude() {
   584	        if (this.userLongitude != null) return this.userLongitude;
   585	        if (getMetadata() == null) return null;
   586	        double lon = getMetadata().getAttributes().optDouble("longitude");
   587	        if (!Double.isNaN(lon)) return lon;
   588	        return getMetadata().getLongitude();
   589	    }
   590	
   591	    public void setUserLongitude(Double lon) {
   592	        this.userLongitude = lon;
   593	    }
   594	
   595	    // note: default behavior will add this to the features on this MediaAsset -- can pass false to disable
   596	    public Feature generateUnityFeature() {
   597	        return generateUnityFeature(true);
   598	    }
   599	
   600	    public Feature generateUnityFeature(boolean addToMediaAsset) {
   601	        Feature f = new Feature();
   602	
   603	        if (addToMediaAsset) this.addFeature(f);
   604	        return f;
   605	    }
   606	
   607	    // if unity feature is appropriate, generates that; otherwise does a boundingBox one
   608	    // 'params' is extra params to use, and can be null
   609	    public Feature generateFeatureFromBbox(double w, double h, double x, double y,
   610	        JSONObject params) {
   611	        if (params == null) params = new JSONObject();
   612	        params.put("width", w);
   613	        params.put("height", h);
   614	        params.put("x", x);
   615	        params.put("y", y);
   616	        Feature f = new Feature("org.ecocean.boundingBox", params);
   617	        this.addFeature(f);
   618	        return f;
   619	    }
   620	
   621	    public ArrayList<Annotation> getAnnotations() {
   622	        ArrayList<Annotation> anns = new ArrayList<Annotation>();
   623	
   624	        if ((this.getFeatures() == null) || (this.getFeatures().size() < 1)) return anns;
   625	        for (Feature f : this.getFeatures()) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '621,675p'" in /mnt/c/Wildbook-clean2
 succeeded in 296ms:
   621	    public ArrayList<Annotation> getAnnotations() {
   622	        ArrayList<Annotation> anns = new ArrayList<Annotation>();
   623	
   624	        if ((this.getFeatures() == null) || (this.getFeatures().size() < 1)) return anns;
   625	        for (Feature f : this.getFeatures()) {
   626	            if (f.getAnnotation() != null) anns.add(f.getAnnotation());
   627	        }
   628	        return anns;
   629	    }
   630	
   631	    public boolean hasAnnotations() {
   632	        return (getAnnotations().size() > 0);
   633	    }
   634	
   635	    public int numAnnotations() {
   636	        return getAnnotations().size();
   637	    }
   638	
   639	    public List<Taxonomy> getTaxonomies(Shepherd myShepherd) {
   640	        Set<Taxonomy> taxis = new HashSet<Taxonomy>();
   641	
   642	        for (Annotation ann : getAnnotations()) {
   643	            Taxonomy taxy = ann.getTaxonomy(myShepherd);
   644	            taxis.add(taxy);
   645	        }
   646	        return new ArrayList(taxis);
   647	    }
   648	
   649	    public Taxonomy getTaxonomy(Shepherd myShepherd) {
   650	        for (Annotation ann : getAnnotations()) {
   651	            Taxonomy taxy = ann.getTaxonomy(myShepherd);
   652	            if (taxy != null) return taxy;
   653	        }
   654	        return null;
   655	    }
   656	
   657	    public List<Annotation> getAnnotationsSortedPositionally() {
   658	        List<Annotation> ord = new ArrayList<Annotation>(this.getAnnotations());
   659	
   660	        if (Util.collectionSize(ord) < 2) return ord; // no sorting necessary
   661	        Collections.sort(ord, new AnnotationPositionalComparator());
   662	        return ord;
   663	    }
   664	
   665	    class AnnotationPositionalComparator implements Comparator<Annotation> {
   666	        @Override public int compare(Annotation annA, Annotation annB) {
   667	            return annA.comparePositional(annB);
   668	        }
   669	    }
   670	
   671	    // will find one with same qualities on this asset (will not return self!)
   672	    public Annotation findAnnotation(Annotation match, boolean shapeOnly) {
   673	        if (match == null) return null;
   674	        if (numAnnotations() < 1) return null;
   675	        Annotation found = null;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Encounter.java | sed -n '2875,2920p'" in /mnt/c/Wildbook-clean2
 succeeded in 292ms:
  2875	        return classes;
  2876	    }
  2877	
  2878	    // all an enc's annotations on a given asset (might be multiple if parts are involved)
  2879	    public List<Annotation> getAnnotations(MediaAsset ma) {
  2880	        List<Annotation> anns = new ArrayList<Annotation>();
  2881	
  2882	        for (Annotation ann : getAnnotations()) {
  2883	            if (ann.getMediaAsset() == ma) anns.add(ann);
  2884	        }
  2885	        return anns;
  2886	    }
  2887	
  2888	    public void setAnnotations(ArrayList<Annotation> anns) {
  2889	        annotations = anns;
  2890	    }
  2891	
  2892	    public void addAnnotations(List<Annotation> anns) {
  2893	        if (annotations == null) annotations = new ArrayList<Annotation>();
  2894	        for (Annotation ann : anns) {
  2895	            if (!annotations.contains(ann)) annotations.add(ann);
  2896	        }
  2897	    }
  2898	
  2899	    public void addAnnotation(Annotation ann) {
  2900	        if (annotations == null) annotations = new ArrayList<Annotation>();
  2901	        if (!annotations.contains(ann)) annotations.add(ann);
  2902	    }
  2903	
  2904	    public void useAnnotationsForMatching(boolean use) {
  2905	        if (getAnnotations() != null && getAnnotations().size() >= 1) {
  2906	            for (Annotation ann : getAnnotations()) {
  2907	                ann.setMatchAgainst(use);
  2908	            }
  2909	        }
  2910	    }
  2911	
  2912	    public Annotation getAnnotationWithKeyword(String word) {
  2913	        System.out.println("getAnnotationWithKeyword called for " + word);
  2914	        System.out.println("getAnnotationWithKeyword called, annotations = " + annotations);
  2915	        if (annotations == null) return null;
  2916	        for (Annotation ann : annotations) {
  2917	            if (ann == null) continue;
  2918	            MediaAsset ma = ann.getMediaAsset();
  2919	            if (ma != null && ma.hasKeyword(word)) return ann;
  2920	        }

exec
/bin/bash -lc 'rg -n "WbiaRegister|wbiaRegister|iaAnnotationIdsStrict|parseAnnotationIdsArrayStrict|sendMediaAssetsForceId|mediaAssetToUri|validMediaAsset|iaImageIds|QueryCacheFactory|invalidateByName|NETWORK_FAIL|MAX_ATTEMPTS" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '90,140p;460,520p;600,650p;730,775p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '820,1040p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
 succeeded in 286ms:
    90	 */
    91	    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
    92	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    93	        InvalidKeyException {
    94	        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
    95	
    96	        if (u == null)
    97	            throw new MalformedURLException(
    98	                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
    99	        URL url = new URL(u);
   100	        int batchSize = 30;
   101	        int numBatches = Math.round(mas.size() / batchSize + 1);
   102	
   103	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   104	        List<String> iaImageIds = new ArrayList<String>();
   105	        if (checkFirst) iaImageIds = iaImageIds();
   106	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   107	        map.put("image_uri_list", new ArrayList<JSONObject>());
   108	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   109	        map.put("image_unixtime_list", new ArrayList<Integer>());
   110	        map.put("image_gps_lat_list", new ArrayList<Double>());
   111	        map.put("image_gps_lon_list", new ArrayList<Double>());
   112	        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
   113	        int batchCt = 1;
   114	        JSONObject allRtn = new JSONObject();
   115	        allRtn.put("_batchSize", batchSize);
   116	        allRtn.put("_totalSize", mas.size());
   117	        JSONArray bres = new JSONArray();
   118	        for (int i = 0; i < mas.size(); i++) {
   119	            MediaAsset ma = mas.get(i);
   120	            if (iaImageIds.contains(ma.getAcmId())) continue;
   121	            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
   122	                IA.log(
   123	                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
   124	                    + ma.getId());
   125	                continue;
   126	            }
   127	            if (!validMediaAsset(ma)) {
   128	                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
   129	                continue;
   130	            }
   131	            acmList.add(ma);
   132	            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
   133	            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
   134	            map.get("image_uri_list").add(mediaAssetToUri(ma));
   135	            map.get("image_gps_lat_list").add(ma.getLatitude());
   136	            map.get("image_gps_lon_list").add(ma.getLongitude());
   137	            DateTime t = ma.getDateTime();
   138	            if (t == null) {
   139	                map.get("image_unixtime_list").add(null);
   140	            } else {
   460	            this.mediaAssetAcmId = mediaAssetAcmId;
   461	            this.bbox            = bbox;
   462	            this.theta           = theta;
   463	            this.iaClass         = iaClass;
   464	            this.individualName  = individualName;
   465	        }
   466	    }
   467	
   468	    /**
   469	     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
   470	     * fetch failure rather than returning an empty list. Phase B needs
   471	     * this so a network failure during the already-present check is
   472	     * not silently treated as "go ahead and POST".
   473	     *
   474	     * <p>Honors the 15-minute QueryCache the same way the lenient
   475	     * variant does, so a cache hit avoids the network entirely.</p>
   476	     */
   477	    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
   478	        String cacheName = "iaAnnotationIds";
   479	        // QueryCacheFactory.getQueryCache(context) can return null on a
   480	        // context that has never been initialized; treat that as "no cache"
   481	        // rather than NPE-ing out and aborting the poll cycle.
   482	        QueryCache qc = null;
   483	        try {
   484	            qc = QueryCacheFactory.getQueryCache(context);
   485	        } catch (Exception ex) {
   486	            // Defensive: cache factory init can fail; degrade to no-cache.
   487	        }
   488	        if (qc != null && qc.getQueryByName(cacheName) != null &&
   489	            System.currentTimeMillis() <
   490	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   491	            try {
   492	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   493	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   494	                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
   495	                return parseAnnotationIdsArrayStrict(cached);
   496	            } catch (Exception ex) {
   497	                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
   498	                    + ex.getMessage());
   499	            }
   500	        }
   501	        JSONArray jids;
   502	        try {
   503	            jids = apiGetJSONArray("/api/annot/json/", context);
   504	        } catch (Exception ex) {
   505	            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
   506	        }
   507	        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
   508	        if (qc != null) {
   509	            try {
   510	                org.datanucleus.api.rest.orgjson.JSONObject jobj =
   511	                    new org.datanucleus.api.rest.orgjson.JSONObject();
   512	                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
   513	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   514	                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   515	                qc.addCachedQuery(cq);
   516	            } catch (Exception cacheEx) {
   517	                // Cache store failure is non-fatal; we still have the ids.
   518	            }
   519	        }
   520	        return parseAnnotationIdsArrayStrict(jids);
   600	    }
   601	
   602	    /**
   603	     * Phase B entry point. Does the already-present check, builds the
   604	     * forced-id POST, fires it, and classifies the outcome. Does NOT
   605	     * touch any Shepherd or JDO state; callers must hand it a DTO that
   606	     * was pre-validated and detached in Phase A.
   607	     */
   608	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
   609	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
   610	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   611	        if (u == null) {
   612	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
   613	            return WbiaRegisterOutcome.NETWORK_FAIL;
   614	        }
   615	        List<String> known;
   616	        try {
   617	            known = iaAnnotationIdsStrict(context);
   618	        } catch (IOException ex) {
   619	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
   620	                ex.getMessage());
   621	            return WbiaRegisterOutcome.NETWORK_FAIL;
   622	        }
   623	        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
   624	        // only check the annotation's id and acmId here. Comparing against
   625	        // the media-asset's acmId is wrong - that would compare an image
   626	        // identifier against a list of annotation identifiers.
   627	        if (known.contains(dto.annotationId) ||
   628	            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
   629	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
   630	        }
   631	        URL url;
   632	        try {
   633	            url = new URL(u);
   634	        } catch (MalformedURLException ex) {
   635	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
   636	            return WbiaRegisterOutcome.NETWORK_FAIL;
   637	        }
   638	        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
   639	        JSONObject rtn;
   640	        try {
   641	            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   642	        } catch (Exception ex) {
   643	            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
   644	            return WbiaRegisterOutcome.NETWORK_FAIL;
   645	        }
   646	        try {
   647	            validateForcedResponse(dto.annotationId, rtn);
   648	        } catch (IOException ex) {
   649	            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
   730	                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
   731	                }
   732	            } catch (Exception ex) {
   733	                ex.printStackTrace();
   734	                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
   735	            }
   736	        }
   737	        return ids;
   738	    }
   739	
   740	    // as above, but images
   741	    public List<String> iaImageIds() {
   742	        return iaImageIds(this.context);
   743	    }
   744	
   745	    public static List<String> iaImageIds(String context) {
   746	        List<String> ids = new ArrayList<String>();
   747	        JSONArray jids = null;
   748	
   749	        try {
   750	            jids = apiGetJSONArray("/api/image/json/", context);
   751	        } catch (Exception ex) {
   752	            ex.printStackTrace();
   753	            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
   754	                ex.toString());
   755	        }
   756	        if (jids != null) {
   757	            try {
   758	                for (int i = 0; i < jids.length(); i++) {
   759	                    if (jids.optJSONObject(i) != null)
   760	                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
   761	                }
   762	            } catch (Exception ex) {
   763	                ex.printStackTrace();
   764	                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
   765	            }
   766	        }
   767	        return ids;
   768	    }
   769	
   770	    public JSONArray apiGetJSONArray(String urlSuffix)
   771	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   772	        InvalidKeyException {
   773	        return apiGetJSONArray(urlSuffix, this.context);
   774	    }
   775	

 succeeded in 279ms:
   820	
   821	    // basically "should we send to IA?"
   822	    public static boolean validMediaAsset(MediaAsset ma) {
   823	        if (ma == null) return false;
   824	        if (!ma.isMimeTypeMajor("image")) return false;
   825	        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
   826	        if (mediaAssetToUri(ma) == null) {
   827	            System.out.println(
   828	                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
   829	                ma);
   830	            return false;
   831	        }
   832	        return true;
   833	    }
   834	
   835	    // this is used to give a string to IA for annot_species_list specifially
   836	    // hence the term "IASpecies"
   837	    public static String getIASpecies(Annotation ann, Shepherd myShepherd) {
   838	        // NOTE: returning null here is probably "bad" btw....
   839	        org.ecocean.Encounter enc = ann.findEncounter(myShepherd);
   840	        if (enc == null) return null;
   841	        String ts = enc.getTaxonomyString();
   842	        if (ts == null) return null;
   843	        return ts.replaceAll(" ", "_");
   844	    }
   845	
   846	    public String toString() {
   847	        return new ToStringBuilder(this)
   848	                   .append("WildbookIAM IA Plugin")
   849	                   .toString();
   850	    }
   851	}

 succeeded in 279ms:
     1	package org.ecocean.ia.plugin;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertNotNull;
     5	import static org.junit.jupiter.api.Assertions.assertNull;
     6	import static org.junit.jupiter.api.Assertions.assertThrows;
     7	import static org.junit.jupiter.api.Assertions.assertTrue;
     8	
     9	import java.io.IOException;
    10	import java.util.ArrayList;
    11	import java.util.HashMap;
    12	import java.util.List;
    13	
    14	import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
    15	import org.json.JSONArray;
    16	import org.json.JSONObject;
    17	import org.junit.jupiter.api.Test;
    18	
    19	/**
    20	 * ml-service migration v2 §commit #11 fix-pass. Pure-function coverage of
    21	 * the no-Shepherd WBIA registration helpers introduced in
    22	 * {@link org.ecocean.ia.plugin.WildbookIAM}. Network-bound behavior of
    23	 * {@code registerOneByDto} itself is exercised end-to-end by the
    24	 * polling thread integration in a dev deployment; here we cover the
    25	 * pieces that can be tested without WireMock/Tomcat.
    26	 */
    27	class WildbookIAMRegisterTest {
    28	
    29	    private static WbiaRegisterRequest sampleDto() {
    30	        return new WbiaRegisterRequest(
    31	            "ann-uuid-1", "ann-acm-1", "ma-acm-1",
    32	            new int[] { 10, 20, 100, 200 },
    33	            0.0d, "right_dorsalfin", "indiv-1");
    34	    }
    35	
    36	    // --- buildForcedRequestMap -------------------------------------------
    37	
    38	    @Test void buildForcedRequestMapPopulatesAllLists() {
    39	        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
    40	        assertEquals(1, map.get("image_uuid_list").size());
    41	        assertEquals(1, map.get("annot_uuid_list").size());
    42	        assertEquals(1, map.get("annot_species_list").size());
    43	        assertEquals(1, map.get("annot_bbox_list").size());
    44	        assertEquals(1, map.get("annot_name_list").size());
    45	        assertEquals(1, map.get("annot_theta_list").size());
    46	        assertEquals("right_dorsalfin", map.get("annot_species_list").get(0));
    47	        assertEquals("indiv-1", map.get("annot_name_list").get(0));
    48	    }
    49	
    50	    @Test void buildForcedRequestMapWrapsUuidsInFancyForm() {
    51	        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
    52	        JSONObject annUuid = (JSONObject) map.get("annot_uuid_list").get(0);
    53	        JSONObject imgUuid = (JSONObject) map.get("image_uuid_list").get(0);
    54	        assertEquals("ann-uuid-1", WildbookIAM.fromFancyUUID(annUuid));
    55	        assertEquals("ma-acm-1",   WildbookIAM.fromFancyUUID(imgUuid));
    56	    }
    57	
    58	    @Test void buildForcedRequestMapNullIndividualSerializesUnderscores() {
    59	        WbiaRegisterRequest dto = new WbiaRegisterRequest(
    60	            "ann-2", "ann-acm-2", "ma-2", new int[] { 0, 0, 1, 1 },
    61	            1.5d, "iaClass", null);
    62	        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(dto);
    63	        assertEquals("____", map.get("annot_name_list").get(0));
    64	    }
    65	
    66	    // --- validateForcedResponse ------------------------------------------
    67	
    68	    @Test void validateForcedResponseAcceptsMatchingId() throws IOException {
    69	        JSONObject resp = new JSONObject().put("response",
    70	            new JSONArray().put(makeFancy("ann-uuid-1")));
    71	        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
    72	    }
    73	
    74	    @Test void validateForcedResponseRejectsNull() {
    75	        assertThrows(IOException.class,
    76	            () -> WildbookIAM.validateForcedResponse("x", null));
    77	    }
    78	
    79	    @Test void validateForcedResponseRejectsMissingArray() {
    80	        assertThrows(IOException.class,
    81	            () -> WildbookIAM.validateForcedResponse("x", new JSONObject()));
    82	    }
    83	
    84	    @Test void validateForcedResponseRejectsWrongLength() {
    85	        JSONObject resp = new JSONObject().put("response",
    86	            new JSONArray().put(makeFancy("a")).put(makeFancy("b")));
    87	        assertThrows(IOException.class,
    88	            () -> WildbookIAM.validateForcedResponse("a", resp));
    89	    }
    90	
    91	    @Test void validateForcedResponseRejectsIdMismatch() {
    92	        JSONObject resp = new JSONObject().put("response",
    93	            new JSONArray().put(makeFancy("other-id")));
    94	        assertThrows(IOException.class,
    95	            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
    96	    }
    97	
    98	    @Test void validateForcedResponseRejectsStatusSuccessFalse() {
    99	        JSONObject resp = new JSONObject()
   100	            .put("status", new JSONObject().put("success", false))
   101	            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
   102	        assertThrows(IOException.class,
   103	            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
   104	    }
   105	
   106	    @Test void validateForcedResponseTreatsStatusSuccessTrueAsOK() throws IOException {
   107	        JSONObject resp = new JSONObject()
   108	            .put("status", new JSONObject().put("success", true))
   109	            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
   110	        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
   111	    }
   112	
   113	    // --- parseAnnotationIdsArray -----------------------------------------
   114	
   115	    @Test void parseAnnotationIdsArrayReturnsEmptyOnNull() {
   116	        assertTrue(WildbookIAM.parseAnnotationIdsArray(null).isEmpty());
   117	    }
   118	
   119	    @Test void parseAnnotationIdsArrayExtractsUuids() {
   120	        JSONArray jids = new JSONArray()
   121	            .put(makeFancy("u1"))
   122	            .put(makeFancy("u2"));
   123	        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
   124	        assertEquals(2, ids.size());
   125	        assertTrue(ids.contains("u1"));
   126	        assertTrue(ids.contains("u2"));
   127	    }
   128	
   129	    @Test void parseAnnotationIdsArraySkipsNonObjectEntries() {
   130	        JSONArray jids = new JSONArray()
   131	            .put(makeFancy("u1"))
   132	            .put("not-an-object");
   133	        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
   134	        assertEquals(1, ids.size());
   135	        assertEquals("u1", ids.get(0));
   136	    }
   137	
   138	    // --- parseAnnotationIdsArrayStrict -----------------------------------
   139	
   140	    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
   141	        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
   142	    }
   143	
   144	    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
   145	        JSONArray jids = new JSONArray()
   146	            .put(makeFancy("u1"))
   147	            .put(makeFancy("u2"));
   148	        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
   149	        assertEquals(2, ids.size());
   150	        assertTrue(ids.contains("u1"));
   151	        assertTrue(ids.contains("u2"));
   152	    }
   153	
   154	    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
   155	        JSONArray jids = new JSONArray()
   156	            .put(makeFancy("u1"))
   157	            .put("not-an-object");
   158	        assertThrows(IOException.class,
   159	            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
   160	    }
   161	
   162	    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
   163	        JSONArray jids = new JSONArray()
   164	            .put(makeFancy("u1"))
   165	            .put(new JSONObject().put("not_uuid_key", "x"));
   166	        assertThrows(IOException.class,
   167	            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
   168	    }
   169	
   170	    // --- helpers ---------------------------------------------------------
   171	
   172	    /**
   173	     * Builds a "fancy UUID" wrapper that {@link WildbookIAM#fromFancyUUID}
   174	     * decodes back to the raw string. The wire format ({@code __UUID__})
   175	     * is opaque to this test, so go through the public factory.
   176	     */
   177	    private static JSONObject makeFancy(String raw) {
   178	        JSONObject jo = WildbookIAM.toFancyUUID(raw);
   179	        assertNotNull(jo, "toFancyUUID returned null for " + raw);
   180	        return jo;
   181	    }
   182	}

 succeeded in 854ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:17:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:127:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:178:    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:209:                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:213:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:214:                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:230:                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:234:                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:238:                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:242:                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:495:                return parseAnnotationIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:520:        return parseAnnotationIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530:    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:560:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:703:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:    public static boolean validMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:138:    // --- parseAnnotationIdsArrayStrict -----------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:140:    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:141:        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:144:    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:148:        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:154:    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:159:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:162:    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:167:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/main/java/org/ecocean/StartupWildbook.java:49:    private static volatile ScheduledExecutorService wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:439:     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:445:    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
src/main/java/org/ecocean/StartupWildbook.java:453:        if (wbiaRegisterExecutor != null) {
src/main/java/org/ecocean/StartupWildbook.java:481:        wbiaRegisterExecutor = ses;
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:495:                + WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:496:            q.setOrdering("wbiaRegisterAttempts ascending");
src/main/java/org/ecocean/StartupWildbook.java:523:                wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
src/main/java/org/ecocean/StartupWildbook.java:705:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:706:            loadWbiaRegisterDto(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:713:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
src/main/java/org/ecocean/StartupWildbook.java:730:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:737:        persistWbiaRegisterResult(context, annId, outcome);
src/main/java/org/ecocean/StartupWildbook.java:747:    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
src/main/java/org/ecocean/StartupWildbook.java:748:        loadWbiaRegisterDto(String context, String annId) {
src/main/java/org/ecocean/StartupWildbook.java:758:            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
src/main/java/org/ecocean/StartupWildbook.java:762:            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:778:                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:788:            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:789:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
src/main/java/org/ecocean/StartupWildbook.java:795:            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
src/main/java/org/ecocean/StartupWildbook.java:810:    private static void persistWbiaRegisterResult(String context, String annId,
src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
src/main/java/org/ecocean/StartupWildbook.java:821:            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
src/main/java/org/ecocean/StartupWildbook.java:832:                    ann.setWbiaRegistered(Boolean.TRUE);
src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
src/main/java/org/ecocean/StartupWildbook.java:837:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:841:                    ann.incrementWbiaRegisterAttempts();
src/main/java/org/ecocean/StartupWildbook.java:842:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:844:                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
src/main/java/org/ecocean/StartupWildbook.java:851:            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
src/main/java/org/ecocean/StartupWildbook.java:871:        // flight. shutdownWbiaRegisterExecutor signals shutdown by
src/main/java/org/ecocean/StartupWildbook.java:875:        shutdownWbiaRegisterExecutor();
src/main/java/org/ecocean/StartupWildbook.java:886:    private static void shutdownWbiaRegisterExecutor() {
src/main/java/org/ecocean/StartupWildbook.java:887:        ScheduledExecutorService ses = wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:889:        wbiaRegisterExecutor = null;
src/main/java/org/ecocean/StartupWildbook.java:890:        System.out.println("STOPPING: StartupWildbook.wbiaRegisterExecutor");
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:9:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:44:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:68:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:17:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:127:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:178:    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:209:                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:213:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:214:                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:230:                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:234:                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:238:                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:242:                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:495:                return parseAnnotationIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:520:        return parseAnnotationIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530:    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:560:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:703:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:    public static boolean validMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/ia/MlServiceProcessor.java:337:                ann.setWbiaRegistered(Boolean.FALSE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:338:                ann.setWbiaRegisterAttempts(0);
src/main/java/org/ecocean/identity/IBEISIA.java:148:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/identity/IBEISIA.java:834:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/identity/IBEISIA.java:835:        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
src/main/java/org/ecocean/identity/IBEISIA.java:3710:        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
src/main/java/org/ecocean/identity/IBEISIA.java:3711:        HashSet<String> iaImageIds = null;
src/main/java/org/ecocean/identity/IBEISIA.java:3718:            // get iaImageIds only if we need it
src/main/java/org/ecocean/identity/IBEISIA.java:3719:            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
src/main/java/org/ecocean/identity/IBEISIA.java:3720:            if (iaImageIds.isEmpty())
src/main/java/org/ecocean/identity/IBEISIA.java:3721:                throw new RuntimeException("iaImageIds is empty; possible IA problems");
src/main/java/org/ecocean/identity/IBEISIA.java:3722:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/identity/IAQueryCache.java:8:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/identity/IAQueryCache.java:58:        QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/identity/IAQueryCache.java:82:        QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/MatchResult.java:521:        Boolean wbiaReg = ann.getWbiaRegistered();
src/main/java/org/ecocean/ia/MatchResult.java:522:        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
src/main/java/org/ecocean/Annotation.java:80:    protected Boolean wbiaRegistered;
src/main/java/org/ecocean/Annotation.java:82:    // Failed-attempt counter. Polling filters wbiaRegisterAttempts < MAX so
src/main/java/org/ecocean/Annotation.java:84:    protected int wbiaRegisterAttempts = 0;
src/main/java/org/ecocean/Annotation.java:336:    public Boolean getWbiaRegistered() { return wbiaRegistered; }
src/main/java/org/ecocean/Annotation.java:337:    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:341:    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
src/main/java/org/ecocean/Annotation.java:343:    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
src/main/java/org/ecocean/Annotation.java:344:    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:345:    public void incrementWbiaRegisterAttempts() {
src/main/java/org/ecocean/Annotation.java:346:        this.wbiaRegisterAttempts++;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:47:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4390:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4426:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4467:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/servlet/importer/ImportTask.java:659:                    invalidMediaAssets.add(asset);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '430,465p;520,600p;650,710p;780,815p'" in /mnt/c/Wildbook-clean2
 succeeded in 307ms:
   430	    public enum WbiaRegisterOutcome {
   431	        REGISTERED_OK,
   432	        REGISTERED_ALREADY_PRESENT,
   433	        NETWORK_FAIL,
   434	        RESPONSE_BAD,
   435	    }
   436	
   437	    /**
   438	     * Plain-data DTO that holds everything Phase B needs about one
   439	     * Annotation. Built under a Shepherd transaction in Phase A, then
   440	     * passed across the close/open boundary into Phase B.
   441	     *
   442	     * <p>Phase A is responsible for pre-validating that all required
   443	     * fields are populated; Phase B treats the DTO as opaque and does
   444	     * not re-touch any JDO-managed state.</p>
   445	     */
   446	    public static final class WbiaRegisterRequest {
   447	        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
   448	        public final String annotationAcmId;    // Annotation.acmId, may differ from id on legacy rows
   449	        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
   450	        public final int[]  bbox;               // x,y,w,h
   451	        public final double theta;
   452	        public final String iaClass;            // species/class string
   453	        public final String individualName;     // "____" if absent
   454	
   455	        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
   456	            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
   457	            String individualName) {
   458	            this.annotationId    = annotationId;
   459	            this.annotationAcmId = annotationAcmId;
   460	            this.mediaAssetAcmId = mediaAssetAcmId;
   461	            this.bbox            = bbox;
   462	            this.theta           = theta;
   463	            this.iaClass         = iaClass;
   464	            this.individualName  = individualName;
   465	        }
   520	        return parseAnnotationIdsArrayStrict(jids);
   521	    }
   522	
   523	    /**
   524	     * Strict element parser: throws IOException if any element is not a
   525	     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
   526	     * skips/null-pads malformed entries, which is fine for legacy paths but
   527	     * would let a corrupt response masquerade as "annotation not yet
   528	     * registered" in the polling thread's already-present check.
   529	     */
   530	    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
   531	        List<String> ids = new ArrayList<String>();
   532	        if (jids == null) return ids;
   533	        for (int i = 0; i < jids.length(); i++) {
   534	            JSONObject jo = jids.optJSONObject(i);
   535	            if (jo == null)
   536	                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
   537	            String decoded = fromFancyUUID(jo);
   538	            if (decoded == null)
   539	                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
   540	            ids.add(decoded);
   541	        }
   542	        return ids;
   543	    }
   544	
   545	    static List<String> parseAnnotationIdsArray(JSONArray jids) {
   546	        List<String> ids = new ArrayList<String>();
   547	        if (jids == null) return ids;
   548	        for (int i = 0; i < jids.length(); i++) {
   549	            JSONObject jo = jids.optJSONObject(i);
   550	            if (jo != null) ids.add(fromFancyUUID(jo));
   551	        }
   552	        return ids;
   553	    }
   554	
   555	    /**
   556	     * Build the forced-id POST body for a single DTO. Pure function;
   557	     * factored out so unit tests can verify the request shape without
   558	     * a network round trip.
   559	     */
   560	    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
   561	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   562	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   563	        map.put("annot_uuid_list", new ArrayList<JSONObject>());
   564	        map.put("annot_species_list", new ArrayList<String>());
   565	        map.put("annot_bbox_list", new ArrayList<int[]>());
   566	        map.put("annot_name_list", new ArrayList<String>());
   567	        map.put("annot_theta_list", new ArrayList<Double>());
   568	        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
   569	        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
   570	        map.get("annot_species_list").add(dto.iaClass);
   571	        map.get("annot_bbox_list").add(dto.bbox);
   572	        map.get("annot_name_list").add(
   573	            (dto.individualName == null) ? "____" : dto.individualName);
   574	        map.get("annot_theta_list").add(dto.theta);
   575	        return map;
   576	    }
   577	
   578	    /**
   579	     * Validate a forced-id response. Throws on any contract violation
   580	     * (length mismatch, missing entry, id mismatch). Pure function.
   581	     */
   582	    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
   583	        if (resp == null) throw new IOException("null forced-id response");
   584	        if (resp.has("status")) {
   585	            JSONObject status = resp.optJSONObject("status");
   586	            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
   587	                throw new IOException("forced-id response status.success=false: " + resp);
   588	            }
   589	        }
   590	        JSONArray respArr = resp.optJSONArray("response");
   591	        if (respArr == null) throw new IOException("no response array: " + resp);
   592	        if (respArr.length() != 1)
   593	            throw new IOException("expected response array length 1, got " + respArr.length());
   594	        JSONObject jid = respArr.optJSONObject(0);
   595	        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
   596	        String respId = fromFancyUUID(jid);
   597	        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
   598	        if (!respId.equals(sentAnnotId))
   599	            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
   600	    }
   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
   651	        }
   652	        return WbiaRegisterOutcome.REGISTERED_OK;
   653	    }
   654	
   655	    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
   656	    throws IOException {
   657	        if ((sentIds == null) || (respArr == null))
   658	            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
   659	        if (sentIds.size() != respArr.length())
   660	            throw new IOException("args diff length: " + sentIds.size() + " != " +
   661	                    respArr.length());
   662	        for (int i = 0; i < sentIds.size(); i++) {
   663	            String sentId = fromFancyUUID(sentIds.get(i));
   664	            if (sentId == null)
   665	                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
   666	            JSONObject jid = respArr.optJSONObject(i);
   667	            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
   668	            String respId = fromFancyUUID(jid);
   669	            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
   670	            if (!respId.equals(sentId))
   671	                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
   672	                        "; respId=" + respId);
   673	        }
   674	    }
   675	
   676	    public static List<String> acmIdsFromResponse(JSONObject rtn) {
   677	        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
   678	        List<String> ids = new ArrayList<String>();
   679	        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
   680	            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
   681	                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
   682	                ids.add(null);
   683	            } else {
   684	                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
   685	            }
   686	        }
   687	        System.out.println("fromResponse ---> " + ids);
   688	        return ids;
   689	    }
   690	
   691	    // instance version of below (since context is known)
   692	    public List<String> iaAnnotationIds() {
   693	        return iaAnnotationIds(this.context);
   694	    }
   695	
   696	    // this fails "gracefully" with empty list if network fubar.  bad decision?
   697	    public static List<String> iaAnnotationIds(String context) {
   698	        List<String> ids = new ArrayList<String>();
   699	        JSONArray jids = null;
   700	        String cacheName = "iaAnnotationIds";
   701	
   702	        try {
   703	            QueryCache qc = QueryCacheFactory.getQueryCache(context);
   704	            if (qc.getQueryByName(cacheName) != null &&
   705	                System.currentTimeMillis() <
   706	                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   707	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   708	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   709	                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
   710	            } else {
   780	        JSONObject rtn = RestClient.get(u);
   781	
   782	        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
   783	            (rtn.optJSONArray("response") == null) ||
   784	            !rtn.getJSONObject("status").optBoolean("success", false)) {
   785	            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
   786	                rtn);
   787	            return null;
   788	        }
   789	        return rtn.getJSONArray("response");
   790	    }
   791	
   792	    public static String fromFancyUUID(JSONObject u) {
   793	        if (u == null) return null;
   794	        return u.optString("__UUID__", null);
   795	    }
   796	
   797	    public static JSONObject toFancyUUID(String u) {
   798	        JSONObject j = new JSONObject();
   799	
   800	        j.put("__UUID__", u);
   801	        return j;
   802	    }
   803	
   804	    private static Object mediaAssetToUri(MediaAsset ma) {
   805	        URL curl = ma.webURL();
   806	        String urlStr = curl.toString();
   807	
   808	        // THIS WILL BREAK if you need to append a query to the filename...
   809	        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
   810	        if (urlStr != null) {
   811	            urlStr = urlStr.replaceAll("\\?", "%3F");
   812	            if (ma.getStore() instanceof LocalAssetStore) {
   813	                return urlStr;
   814	            } else {
   815	                return urlStr;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '440,525p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '680,860p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/WbiaQueueUtil.java | sed -n '1,100p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCache.java | sed -n '1,80p'" in /mnt/c/Wildbook-clean2
 succeeded in 323ms:
   680	            shep.commitDBTransaction();
   681	            System.out.println("[INFO] StaleMlServiceReconciliation: re-enqueued " + maId);
   682	        } catch (Exception ex) {
   683	            System.out.println("WARN: StaleMlServiceReconciliation registerOne failed for " +
   684	                maId + ": " + ex);
   685	            shep.rollbackDBTransaction();
   686	        } finally {
   687	            shep.closeDBTransaction();
   688	        }
   689	    }
   690	
   691	    /**
   692	     * Phase A/B/C split per Codex c11 fix-review.
   693	     * <ul>
   694	     *   <li>Phase A: Shepherd open, re-check state, build DTO, close.
   695	     *   <li>Phase B: no Shepherd held; WBIA HTTP via
   696	     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
   697	     *   <li>Phase C: Shepherd open, re-load, persist outcome, close.
   698	     * </ul>
   699	     * Ineligible annotations (missing media asset, missing acmId, fails
   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
   701	     * fall out of the polling query.
   702	     */
   703	    private static void registerOneAnnotationWithWbia(String context, String annId) {
   704	        // ---- Phase A: load DTO under a short transaction. ----
   705	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   706	            loadWbiaRegisterDto(context, annId);
   707	        if (dto == null) return;  // ineligible / already registered / parked
   708	
   709	        // Bail out before starting the non-interruptible HTTP call if
   710	        // shutdown was requested while Phase A was running. Otherwise we
   711	        // would start a 300s WBIA POST that contextDestroyed can't cancel.
   712	        if (Thread.currentThread().isInterrupted() ||
   713	            wbiaRegisterExecutor == null) {
   714	            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
   715	                " (shutdown requested)");
   716	            return;
   717	        }
   718	
   719	        // ---- Phase B: no Shepherd held; call WBIA. ----
   720	        org.ecocean.ia.plugin.WildbookIAM iam =
   721	            new org.ecocean.ia.plugin.WildbookIAM(context);
   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
   723	            iam.registerOneByDto(dto);
   724	
   725	        // Skip Phase C if shutdown has been requested while Phase B ran.
   726	        // RestClient is not interruptible mid-IO, so Phase B can outlive
   727	        // awaitTermination; this prevents Phase C from racing the rest of
   728	        // contextDestroyed's cleanup (Shepherd / IndexingManager / etc.).
   729	        if (Thread.currentThread().isInterrupted() ||
   730	            wbiaRegisterExecutor == null) {
   731	            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
   732	                " (shutdown requested)");
   733	            return;
   734	        }
   735	
   736	        // ---- Phase C: persist outcome under a short transaction. ----
   737	        persistWbiaRegisterResult(context, annId, outcome);
   738	    }
   739	
   740	    /**
   741	     * Phase A. Returns a detached DTO ready for Phase B, or null if the
   742	     * annotation does not need (or cannot get) a Phase-B network call.
   743	     * Null cases: missing annotation, already registered, parked at max
   744	     * attempts, or ineligible (missing media asset / acmId / bbox / etc.).
   745	     * Ineligible annotations are parked here so they stop being polled.
   746	     */
   747	    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
   748	        loadWbiaRegisterDto(String context, String annId) {
   749	        Shepherd shep = new Shepherd(context);
   750	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
   751	        shep.beginDBTransaction();
   752	        try {
   753	            org.ecocean.Annotation ann = shep.getAnnotation(annId);
   754	            if (ann == null) {
   755	                shep.commitDBTransaction();
   756	                return null;
   757	            }
   758	            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
   759	                shep.commitDBTransaction();
   760	                return null;
   761	            }
   762	            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   763	                shep.commitDBTransaction();
   764	                return null;
   765	            }
   766	            // Eligibility checks. Any failure here is permanent for this
   767	            // annotation under its current state, so park it.
   768	            org.ecocean.media.MediaAsset ma = ann.getMediaAsset();
   769	            String reason = null;
   770	            if (ma == null) reason = "missing media asset";
   771	            else if (!Util.stringExists(ma.getAcmId())) reason = "media asset has no acmId";
   772	            else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
   773	            else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
   774	                reason = "validForIdentification returned false (bbox/iaClass/etc.)";
   775	            if (reason != null) {
   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
   777	                    " (ineligible: " + reason + ")");
   778	                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
   779	                shep.commitDBTransaction();
   780	                return null;
   781	            }
   782	            // Resolve the individual name now while the Shepherd is open;
   783	            // Phase B has no DB access.
   784	            String name = ann.findIndividualId(shep);
   785	            // Copy bbox into a fresh array so the DTO is fully detached.
   786	            int[] bb = ann.getBbox();
   787	            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
   788	            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   789	                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
   790	                    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
   791	                    ann.getTheta(), ann.getIAClass(), name);
   792	            shep.commitDBTransaction();
   793	            return dto;
   794	        } catch (Exception ex) {
   795	            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
   796	                annId + ": " + ex);
   797	            shep.rollbackDBTransaction();
   798	            return null;
   799	        } finally {
   800	            shep.closeDBTransaction();
   801	        }
   802	    }
   803	
   804	    /**
   805	     * Phase C. Re-loads the annotation and writes the outcome of the
   806	     * Phase-B network call. On terminal-success outcomes the annotation
   807	     * is marked registered; on retryable outcomes the attempts counter
   808	     * is bumped and we WARN-log when we hit the abandonment threshold.
   809	     */
   810	    private static void persistWbiaRegisterResult(String context, String annId,
   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
   812	        Shepherd shep = new Shepherd(context);
   813	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
   814	        shep.beginDBTransaction();
   815	        try {
   816	            org.ecocean.Annotation ann = shep.getAnnotation(annId);
   817	            if (ann == null) {
   818	                shep.commitDBTransaction();
   819	                return;
   820	            }
   821	            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
   822	                // Some other path flipped it while Phase B ran; respect that.
   823	                shep.commitDBTransaction();
   824	                return;
   825	            }
   826	            switch (outcome) {
   827	                case REGISTERED_OK:
   828	                case REGISTERED_ALREADY_PRESENT:
   829	                    // Always honor a success outcome even if the row was
   830	                    // parked by a racing poller: stuck-at-attempts==MAX
   831	                    // would otherwise become permanent.
   832	                    ann.setWbiaRegistered(Boolean.TRUE);
   833	                    break;
   834	                case NETWORK_FAIL:
   835	                case RESPONSE_BAD:
   836	                default:
   837	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   838	                        // Already parked by another path; do not increment past MAX.
   839	                        break;
   840	                    }
   841	                    ann.incrementWbiaRegisterAttempts();
   842	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   843	                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
   844	                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
   845	                            " attempts (last outcome=" + outcome + "); will not retry");
   846	                    }
   847	                    break;
   848	            }
   849	            shep.commitDBTransaction();
   850	        } catch (Exception ex) {
   851	            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
   852	                annId + ": " + ex);
   853	            shep.rollbackDBTransaction();
   854	        } finally {
   855	            shep.closeDBTransaction();
   856	        }
   857	    }
   858	
   859	    public void contextDestroyed(ServletContextEvent sce) {
   860	        ServletContext sContext = sce.getServletContext();

 succeeded in 325ms:
   440	     *
   441	     * <p>Legacy annotations are excluded from the query because the DDL
   442	     * migration in {@code archive/sql/ml_service_idempotency.sql} backfills
   443	     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
   444	     */
   445	    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
   446	    private static final int WBIA_REGISTER_BATCH_LIMIT = 50;
   447	    private static final long WBIA_REGISTER_POLL_SECONDS = 30L;
   448	
   449	    private static void startWbiaRegistrationPollingThread(final String context) {
   450	        // Refuse to start a second poller if one is already running; this
   451	        // also matters when contextInitialized fires more than once for
   452	        // the same JVM (e.g., context reload).
   453	        if (wbiaRegisterExecutor != null) {
   454	            System.out.println(
   455	                "WARN: startWbiaRegistrationPollingThread() called with existing executor; skipping");
   456	            return;
   457	        }
   458	        System.out.println("STARTING: StartupWildbook.startWbiaRegistrationPollingThread()");
   459	        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(
   460	            new java.util.concurrent.ThreadFactory() {
   461	                @Override public Thread newThread(Runnable r) {
   462	                    Thread t = new Thread(r, "WbiaRegistrationPoll");
   463	                    t.setDaemon(true);
   464	                    return t;
   465	                }
   466	            });
   467	        ses.scheduleAtFixedRate(new Runnable() {
   468	            @Override public void run() {
   469	                try {
   470	                    runWbiaRegistrationPoll(context);
   471	                } catch (Throwable t) {
   472	                    // Catch Throwable here: ScheduledExecutorService silently
   473	                    // stops re-firing the task on any uncaught exception.
   474	                    // We want the thread to keep ticking through transient
   475	                    // failures.
   476	                    System.out.println("WARN: WbiaRegistrationPoll uncaught: " + t);
   477	                    t.printStackTrace();
   478	                }
   479	            }
   480	        }, WBIA_REGISTER_POLL_SECONDS, WBIA_REGISTER_POLL_SECONDS, TimeUnit.SECONDS);
   481	        wbiaRegisterExecutor = ses;
   482	    }
   483	
   484	    private static void runWbiaRegistrationPoll(String context) {
   485	        // Phase 1: query the pending list (Shepherd open, no network). Capture
   486	        // annotation IDs and release before any WBIA calls.
   487	        java.util.List<String> pendingIds = new ArrayList<String>();
   488	        Shepherd shep = new Shepherd(context);
   489	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.fetch");
   490	        shep.beginDBTransaction();
   491	        try {
   492	            javax.jdo.Query q = shep.getPM().newQuery(
   493	                org.ecocean.Annotation.class,
   494	                "wbiaRegistered == false && wbiaRegisterAttempts < "
   495	                + WBIA_REGISTER_MAX_ATTEMPTS);
   496	            q.setOrdering("wbiaRegisterAttempts ascending");
   497	            q.setRange(0, WBIA_REGISTER_BATCH_LIMIT);
   498	            @SuppressWarnings("unchecked")
   499	            java.util.List<org.ecocean.Annotation> pending =
   500	                (java.util.List<org.ecocean.Annotation>) q.execute();
   501	            if (pending != null) {
   502	                for (org.ecocean.Annotation a : pending) pendingIds.add(a.getId());
   503	            }
   504	            q.closeAll();
   505	            shep.commitDBTransaction();
   506	        } catch (Exception ex) {
   507	            System.out.println("WARN: WbiaRegistrationPoll fetch failed: " + ex);
   508	            shep.rollbackDBTransaction();
   509	        } finally {
   510	            shep.closeDBTransaction();
   511	        }
   512	
   513	        if (pendingIds.isEmpty()) return;
   514	        System.out.println("[INFO] WbiaRegistrationPoll: " + pendingIds.size() + " pending");
   515	
   516	        // Phase 2: per-annotation registration. Each runs in its own short
   517	        // Shepherd tx so a slow WBIA call blocks only that one slot.
   518	        // The interrupted-check makes shutdownNow() effective at bounding
   519	        // the per-tick work even if the in-flight Phase B HTTP call ran
   520	        // past awaitTermination.
   521	        for (String annId : pendingIds) {
   522	            if (Thread.currentThread().isInterrupted() ||
   523	                wbiaRegisterExecutor == null) {
   524	                System.out.println("[INFO] WbiaRegistrationPoll: stopping mid-batch (interrupted)");
   525	                return;

 succeeded in 328ms:
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

 succeeded in 338ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCacheFactory.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
 succeeded in 311ms:
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
    19	}

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/OpenSearchVisibilityTest.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class OpenSearch|queryCount\\(|buildIdEligibilityQuery|protected|RestClient|Request|isValidIndexName\" src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchVisibilityTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '1,170p;552,590p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"OpenSearchVisibility|waitForVisibility|build.*Query|queryCount\" pom.xml build.gradle src/test/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 289ms:
     1	package org.ecocean;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertNotNull;
     5	import static org.junit.jupiter.api.Assertions.assertTrue;
     6	
     7	import java.util.Arrays;
     8	import java.util.LinkedHashSet;
     9	import java.util.Set;
    10	
    11	import org.json.JSONArray;
    12	import org.json.JSONObject;
    13	import org.junit.jupiter.api.Test;
    14	
    15	/**
    16	 * v2 commit #7: shape-of-query tests for OpenSearch.buildIdEligibilityQuery.
    17	 * The full poll-and-wait behavior of waitForVisibility requires a real
    18	 * OpenSearch (or a heavily mocked RestClient); the eligibility-query JSON
    19	 * shape is the one purely-mechanical part that benefits from a unit test.
    20	 *
    21	 * Uses the OpenSearch idiomatic `ids` query rather than the generic `terms`
    22	 * query on `_id`, per OpenSearch docs.
    23	 */
    24	class OpenSearchVisibilityTest {
    25	
    26	    @Test void buildIdEligibilityQuery_shapeForMultipleIds() {
    27	        Set<String> ids = new LinkedHashSet<String>(
    28	            Arrays.asList("ann-1", "ann-2", "ann-3"));
    29	        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
    30	        assertNotNull(q);
    31	        JSONObject inner = q.optJSONObject("query");
    32	        assertNotNull(inner);
    33	        JSONObject idsClause = inner.optJSONObject("ids");
    34	        assertNotNull(idsClause);
    35	        JSONArray values = idsClause.optJSONArray("values");
    36	        assertNotNull(values);
    37	        assertEquals(3, values.length());
    38	        assertEquals("ann-1", values.getString(0));
    39	        assertEquals("ann-2", values.getString(1));
    40	        assertEquals("ann-3", values.getString(2));
    41	    }
    42	
    43	    @Test void buildIdEligibilityQuery_emptySet() {
    44	        Set<String> ids = new LinkedHashSet<String>();
    45	        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
    46	        JSONArray values = q.getJSONObject("query")
    47	            .getJSONObject("ids")
    48	            .getJSONArray("values");
    49	        assertEquals(0, values.length());
    50	    }
    51	
    52	    @Test void buildIdEligibilityQuery_preservesInsertionOrder() {
    53	        // LinkedHashSet preserves insertion order, which is useful for stable
    54	        // OpenSearch query-cache keys.
    55	        Set<String> ids = new LinkedHashSet<String>();
    56	        ids.add("z");
    57	        ids.add("a");
    58	        ids.add("m");
    59	        JSONArray values = OpenSearch.buildIdEligibilityQuery(ids)
    60	            .getJSONObject("query")
    61	            .getJSONObject("ids")
    62	            .getJSONArray("values");
    63	        assertEquals("z", values.getString(0));
    64	        assertEquals("a", values.getString(1));
    65	        assertEquals("m", values.getString(2));
    66	    }
    67	
    68	    @Test void buildIdEligibilityQuery_singletonShape() {
    69	        // Sanity check: the JSON shape with one element matches the OpenSearch
    70	        // docs example for the `ids` query.
    71	        Set<String> ids = new LinkedHashSet<String>();
    72	        ids.add("only-one");
    73	        String expected = "{\"query\":{\"ids\":{\"values\":[\"only-one\"]}}}";
    74	        JSONObject actual = OpenSearch.buildIdEligibilityQuery(ids);
    75	        assertEquals(expected, actual.toString());
    76	    }
    77	}

 succeeded in 316ms:
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:16: * v2 commit #7: shape-of-query tests for OpenSearch.buildIdEligibilityQuery.
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:18: * OpenSearch (or a heavily mocked RestClient); the eligibility-query JSON
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:24:class OpenSearchVisibilityTest {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:26:    @Test void buildIdEligibilityQuery_shapeForMultipleIds() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:29:        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:43:    @Test void buildIdEligibilityQuery_emptySet() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:45:        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:52:    @Test void buildIdEligibilityQuery_preservesInsertionOrder() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:59:        JSONArray values = OpenSearch.buildIdEligibilityQuery(ids)
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:68:    @Test void buildIdEligibilityQuery_singletonShape() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:74:        JSONObject actual = OpenSearch.buildIdEligibilityQuery(ids);
src/main/java/org/ecocean/OpenSearch.java:29:import org.opensearch.client.Request;
src/main/java/org/ecocean/OpenSearch.java:32:import org.opensearch.client.RestClient;
src/main/java/org/ecocean/OpenSearch.java:33:import org.opensearch.client.transport.rest_client.RestClientTransport;
src/main/java/org/ecocean/OpenSearch.java:35:import org.opensearch.client.opensearch.core.IndexRequest;
src/main/java/org/ecocean/OpenSearch.java:36:import org.opensearch.client.opensearch.indices.CreateIndexRequest;
src/main/java/org/ecocean/OpenSearch.java:37:import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
src/main/java/org/ecocean/OpenSearch.java:51:public class OpenSearch {
src/main/java/org/ecocean/OpenSearch.java:53:    public static RestClient restClient = null;
src/main/java/org/ecocean/OpenSearch.java:123:        ///final RestClient restClient = RestClient.builder(host).build();
src/main/java/org/ecocean/OpenSearch.java:128:        restClient = RestClient.builder(host).build();
src/main/java/org/ecocean/OpenSearch.java:129:        final OpenSearchTransport transport = new RestClientTransport(restClient,
src/main/java/org/ecocean/OpenSearch.java:135:    public static boolean isValidIndexName(String indexName) {
src/main/java/org/ecocean/OpenSearch.java:213:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:220:        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
src/main/java/org/ecocean/OpenSearch.java:223:        client.indices().create(createIndexRequest);
src/main/java/org/ecocean/OpenSearch.java:237:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:244:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:245:        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index(
src/main/java/org/ecocean/OpenSearch.java:248:        // DeleteIndexResponse deleteIndexResponse = client.indices().delete(deleteIndexRequest);
src/main/java/org/ecocean/OpenSearch.java:249:        client.indices().delete(deleteIndexRequest);
src/main/java/org/ecocean/OpenSearch.java:255:        if (!isValidIndexName(indexName)) return false;
src/main/java/org/ecocean/OpenSearch.java:269:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:274:        IndexRequest<Base> indexRequest = new IndexRequest.Builder<Base>()
src/main/java/org/ecocean/OpenSearch.java:279:        client.index(indexRequest);
src/main/java/org/ecocean/OpenSearch.java:281:        IndexResponse indexResponse = client.index(indexRequest);
src/main/java/org/ecocean/OpenSearch.java:294:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:299:        Request req = new Request("PUT", indexName + "/_mapping");
src/main/java/org/ecocean/OpenSearch.java:313:        final SearchRequest request = new SearchRequest.Builder()
src/main/java/org/ecocean/OpenSearch.java:342:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:344:        Request searchRequest = new Request("POST",
src/main/java/org/ecocean/OpenSearch.java:346:        String rtn = getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:356:        Request searchRequest = new Request("DELETE", "/_search/point_in_time/_all");
src/main/java/org/ecocean/OpenSearch.java:358:        getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:368:        Request req = new Request("DELETE", "/_search/point_in_time");
src/main/java/org/ecocean/OpenSearch.java:380:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:382:        Request searchRequest = new Request("POST", "/_search?track_total_hits=true");
src/main/java/org/ecocean/OpenSearch.java:396:        searchRequest.setJsonEntity(query.toString());
src/main/java/org/ecocean/OpenSearch.java:399:            rtn = getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:438:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:439:        Request searchRequest = new Request("POST",
src/main/java/org/ecocean/OpenSearch.java:443:        searchRequest.setJsonEntity(query.toString());
src/main/java/org/ecocean/OpenSearch.java:444:        String rtn = getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:457:        Request searchRequest = new Request("POST", "_search/scroll");
src/main/java/org/ecocean/OpenSearch.java:458:        searchRequest.setJsonEntity(data.toString());
src/main/java/org/ecocean/OpenSearch.java:459:        String rtn = getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:470:        if (!isValidIndexName(indexName))
src/main/java/org/ecocean/OpenSearch.java:472:        Request req = new Request("POST", indexName + "/_refresh");
src/main/java/org/ecocean/OpenSearch.java:501:        if (!isValidIndexName(indexName))
src/main/java/org/ecocean/OpenSearch.java:521:        JSONObject query = buildIdEligibilityQuery(targetIds);
src/main/java/org/ecocean/OpenSearch.java:525:            int seen = queryCount(indexName, query);
src/main/java/org/ecocean/OpenSearch.java:541:    static JSONObject buildIdEligibilityQuery(Set<String> ids) {
src/main/java/org/ecocean/OpenSearch.java:552:    public int queryCount(String indexName, final JSONObject query)
src/main/java/org/ecocean/OpenSearch.java:554:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:555:        Request searchRequest = new Request("POST", indexName + "/_count");
src/main/java/org/ecocean/OpenSearch.java:558:        searchRequest.setJsonEntity(cleanedQuery.toString());
src/main/java/org/ecocean/OpenSearch.java:561:            res = new JSONObject(getRestResponse(searchRequest));
src/main/java/org/ecocean/OpenSearch.java:563:            System.out.println("queryCount() on index " + indexName + " using query=" + query +
src/main/java/org/ecocean/OpenSearch.java:566:            throw new IOException("queryCount() failed");
src/main/java/org/ecocean/OpenSearch.java:609:        Request settingsRequest = new Request("GET", indexName + "/_settings");
src/main/java/org/ecocean/OpenSearch.java:610:        String rtn = getRestResponse(settingsRequest);
src/main/java/org/ecocean/OpenSearch.java:627:        Request settingsRequest = new Request("PUT",
src/main/java/org/ecocean/OpenSearch.java:629:        settingsRequest.setJsonEntity(settings.toString());
src/main/java/org/ecocean/OpenSearch.java:630:        String rtn = getRestResponse(settingsRequest);
src/main/java/org/ecocean/OpenSearch.java:637:        Request searchRequest = new Request("POST", indexName + "/_open");
src/main/java/org/ecocean/OpenSearch.java:638:        String rtn = getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:645:        Request searchRequest = new Request("POST", indexName + "/_close");
src/main/java/org/ecocean/OpenSearch.java:646:        String rtn = getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:659:        Request updateRequest = new Request("POST", indexName + "/_update/" + id);
src/main/java/org/ecocean/OpenSearch.java:660:        updateRequest.setJsonEntity(doc.toString());
src/main/java/org/ecocean/OpenSearch.java:661:        getRestResponse(updateRequest);
src/main/java/org/ecocean/OpenSearch.java:719:        Request searchRequest = new Request("POST", indexName + "/_search");
src/main/java/org/ecocean/OpenSearch.java:721:        searchRequest.setJsonEntity("{\"query\": { \"match_all\": {} }}");
src/main/java/org/ecocean/OpenSearch.java:722:        String rtn = getRestResponse(searchRequest);
src/main/java/org/ecocean/OpenSearch.java:727:    public String getRestResponse(Request request)
src/main/java/org/ecocean/OpenSearch.java:729:        Response response = restClient.performRequest(request);
src/main/java/org/ecocean/OpenSearch.java:743:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:751:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);

 succeeded in 316ms:
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
   131	
   132	        client = new OpenSearchClient(transport);
   133	    }
   134	
   135	    public static boolean isValidIndexName(String indexName) {
   136	        return Arrays.asList(VALID_INDICES).contains(indexName);
   137	    }
   138	
   139	    public static boolean skipAutoIndexing() {
   140	        return new java.io.File("/tmp/skipAutoIndexing").exists();
   141	    }
   142	
   143	// http://localhost:9200/encounter/_search?pretty=true&q=*:*
   144	// http://localhost:9200/_cat/indices?v
   145	
   146	    public static void backgroundStartup(String context) {
   147	        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(8);
   148	        final ScheduledFuture schedFutureIndexing = schedExec.scheduleWithFixedDelay(
   149	            new Runnable() {
   150	                public void run() {
   151	                    updateEncounterIndexes(context);
   152	                }
   153	            }, 2, // initial delay
   154	            BACKGROUND_DELAY_MINUTES, // period delay *after* execution finishes
   155	            TimeUnit.MINUTES); // unit of delays above
   156	        final ScheduledFuture schedFuturePermissions = schedExec.scheduleWithFixedDelay(
   157	            new Runnable() {
   158	                public void run() {
   159	                    updatePermissionsIndex(context);
   160	                }
   161	            }, 8, // initial delay
   162	            BACKGROUND_PERMISSIONS_MINUTES, TimeUnit.MINUTES); // unit of delays above
   163	
   164	        try {
   165	            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
   166	        } catch (java.lang.InterruptedException ex) {
   167	            System.out.println("WARNING: OpenSearch.backgroundStartup(" + context +
   168	                ") interrupted: " + ex.toString());
   169	        }
   170	        System.out.println("OpenSearch.backgroundStartup(" + context + ") backgrounded");
   552	    public int queryCount(String indexName, final JSONObject query)
   553	    throws IOException {
   554	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   555	        Request searchRequest = new Request("POST", indexName + "/_count");
   556	        JSONObject cleanedQuery = new JSONObject(query.toString());
   557	        cleanedQuery.remove("_source"); // invalid for a _count query
   558	        searchRequest.setJsonEntity(cleanedQuery.toString());
   559	        JSONObject res = new JSONObject();
   560	        try {
   561	            res = new JSONObject(getRestResponse(searchRequest));
   562	        } catch (Exception ex) {
   563	            System.out.println("queryCount() on index " + indexName + " using query=" + query +
   564	                " failed with: " + ex);
   565	            ex.printStackTrace();
   566	            throw new IOException("queryCount() failed");
   567	        }
   568	        return res.optInt("count", -1);
   569	    }
   570	
   571	    public Map<String, Long> getAllVersions(String indexName)
   572	    throws IOException {
   573	        Map<String, Long> versions = new HashMap<String, Long>();
   574	        boolean reachedEnd = false;
   575	        JSONObject query = new JSONObject("{\"sort\":[{\"version\": \"asc\"}]}");
   576	        JSONObject res = queryRawScroll(indexName, query, 2000);
   577	
   578	        while (!reachedEnd) {
   579	            JSONObject outerHits = res.optJSONObject("hits");
   580	            if (outerHits == null) throw new IOException("outer hits failed");
   581	            JSONArray hits = outerHits.optJSONArray("hits");
   582	            if (hits == null) throw new IOException("hits failed");
   583	            if (hits.length() < 1) {
   584	                reachedEnd = true;
   585	            } else {
   586	                for (int i = 0; i < hits.length(); i++) {
   587	                    String id = hits.optJSONObject(i).optString("_id", "__FAIL__");
   588	                    Long version = hits.optJSONObject(i).optJSONObject("_source").optLong("version",
   589	                        -999L);
   590	                    versions.put(id, version);

 exited 2 in 516ms:
rg: build.gradle: No such file or directory (os error 2)
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:16: * v2 commit #7: shape-of-query tests for OpenSearch.buildIdEligibilityQuery.
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:24:class OpenSearchVisibilityTest {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:26:    @Test void buildIdEligibilityQuery_shapeForMultipleIds() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:29:        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:43:    @Test void buildIdEligibilityQuery_emptySet() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:45:        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:52:    @Test void buildIdEligibilityQuery_preservesInsertionOrder() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:59:        JSONArray values = OpenSearch.buildIdEligibilityQuery(ids)
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:68:    @Test void buildIdEligibilityQuery_singletonShape() {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:74:        JSONObject actual = OpenSearch.buildIdEligibilityQuery(ids);

exec
/bin/bash -lc 'rg -n "junit|mockito|wiremock|opensearch" pom.xml' in /mnt/c/Wildbook-clean2
 succeeded in 313ms:
23:    <junit.version>5.11.0</junit.version>
24:    <mockito.version>4.11.0</mockito.version>
27:    <wiremock.version>3.3.1</wiremock.version>
269:      <groupId>org.opensearch.client</groupId>
270:      <artifactId>opensearch-java</artifactId>
274:      <groupId>org.opensearch.client</groupId>
275:      <artifactId>opensearch-rest-client</artifactId>
394:      <groupId>org.junit.jupiter</groupId>
395:      <artifactId>junit-jupiter-api</artifactId>
396:      <version>${junit.version}</version>
400:      <groupId>org.junit.jupiter</groupId>
401:      <artifactId>junit-jupiter-engine</artifactId>
402:      <version>${junit.version}</version>
406:      <groupId>org.junit.platform</groupId>
407:      <artifactId>junit-platform-launcher</artifactId>
412:      <groupId>org.mockito</groupId>
413:      <artifactId>mockito-inline</artifactId>
414:      <version>${mockito.version}</version>
418:      <groupId>org.mockito</groupId>
419:      <artifactId>mockito-core</artifactId>
420:      <version>${mockito.version}</version>
439:        <artifactId>testcontainers-junit-jupiter</artifactId>

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,390p'" in /mnt/c/Wildbook-clean2
 succeeded in 282ms:
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
 succeeded in 277ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '65,105p'" in /mnt/c/Wildbook-clean2
 succeeded in 279ms:
    65	    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
    66	        if ((mas == null) || (mas.size() < 1)) return null;
    67	        Task task = new Task();
    68	        return task;
    69	    }
    70	    public static Task intake(Shepherd myShepherd, List<Annotation> anns) {
    71	        if ((anns == null) || (anns.size() < 1)) return null;
    72	        Task task = new Task();
    73	        return task;
    74	    }
    75	 */
    76	
    77	    // i think objects ingested here must(?) be persisted (and committed), as we have to assume (or we know)
    78	    // that these processes will use queues which operate in different (Shepherd) threads and will thus try
    79	    // to find the objects via the db.  :/
    80	    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
    81	    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
    82	        return intakeMediaAssets(myShepherd, mas, null);
    83	    }
    84	
    85	    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
    86	        Task parentTask) {
    87	        List<List<MediaAsset> > assetsBySpecies = binAssetsBySpecies(mas, myShepherd);
    88	        int numSpecies = assetsBySpecies.size();
    89	
    90	        // System.out.println("IA.java.numSpecies="+numSpecies);
    91	        // in one-species case we don't need to create an extra layer of tasks
    92	        if (numSpecies == 1)
    93	            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
    94	        // in multi-species case we make sure we have a parent task and add each species task as a child
    95	        if (parentTask == null) parentTask = new Task();
    96	        for (List<MediaAsset> masOneSpecies : assetsBySpecies) {
    97	            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
    98	            parentTask.addChild(thisTask);
    99	        }
   100	        return parentTask;
   101	    }
   102	
   103	    public static List<List<MediaAsset> > binAssetsBySpecies(List<MediaAsset> mas,
   104	        Shepherd myShepherd) {
   105	        Map<String, List<MediaAsset> > assetsBySpecies = new HashMap<String, List<MediaAsset> >();

exec
/bin/bash -lc "rg -n \"String getStatus\\(|getStatus\\(Shepherd|statusInEndState|private String status|public String status\" src/main/java/org/ecocean/ia/Task.java src/main/java/org/ecocean/ia" in /mnt/c/Wildbook-clean2
 succeeded in 502ms:
src/main/java/org/ecocean/ia/Task.java:39:    private String status;
src/main/java/org/ecocean/ia/Task.java:41:    private String statusDetails = null;
src/main/java/org/ecocean/ia/Task.java:85:    public boolean statusInEndState() {
src/main/java/org/ecocean/ia/Task.java:597:    public String getStatus(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:600:        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
src/main/java/org/ecocean/ia/Task.java:39:    private String status;
src/main/java/org/ecocean/ia/Task.java:41:    private String statusDetails = null;
src/main/java/org/ecocean/ia/Task.java:85:    public boolean statusInEndState() {
src/main/java/org/ecocean/ia/Task.java:597:    public String getStatus(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:600:        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '370,420p'" in /mnt/c/Wildbook-clean2
 succeeded in 278ms:
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

exec
/bin/bash -lc "rg -n \"DataNucleus|PersistenceManagerFactory|javax.jdo|in-memory|H2|derby|Shepherd\" src/test/java pom.xml src/test/resources -g '*.java' -g '*.xml' -g '*.properties'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "Testcontainers|PostgreSQLContainer|OpenSearchContainer|@Testcontainers|container" src/test/java pom.xml' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "maven-surefire|failsafe|skipTests|argLine|testcontainers" pom.xml' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"new Shepherd\\(|beginDBTransaction\\(|getPM\\(\\)\\.newQuery\\(\\\"javax.jdo.query.SQL\\\"\" src/test/java src/main/java/org/ecocean | head -200" in /mnt/c/Wildbook-clean2
 succeeded in 314ms:
25:    <testcontainers.version>2.0.3</testcontainers.version>
63:        <groupId>org.testcontainers</groupId>
64:        <artifactId>testcontainers-bom</artifactId>
65:        <version>${testcontainers.version}</version>
426:        <groupId>org.testcontainers</groupId>
427:        <artifactId>testcontainers</artifactId>
432:        <groupId>org.testcontainers</groupId>
433:        <artifactId>testcontainers-postgresql</artifactId>
438:        <groupId>org.testcontainers</groupId>
439:        <artifactId>testcontainers-junit-jupiter</artifactId>
660:        <artifactId>maven-surefire-plugin</artifactId>
664:          <argLine>
669:          </argLine>

 succeeded in 388ms:
pom.xml:18:    <javax.jdo.version>3.2.0-release</javax.jdo.version>
pom.xml:292:    JDO and DataNucleus dependencies -->
pom.xml:296:        <artifactId>javax.jdo</artifactId>
pom.xml:297:        <version>${javax.jdo.version}</version>
pom.xml:300:      <!-- Core DataNucleus -->
src/test/java/org/ecocean/MatchResultTest.java:12:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/MatchResultTest.java:13:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/MatchResultTest.java:21:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/MatchResultTest.java:22:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/MatchResultTest.java:44:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/MatchResultTest.java:45:        when(myShepherd.getAnnotationsWithACMId(any(String.class),
src/test/java/org/ecocean/MatchResultTest.java:62:        mr.createFromJsonResult(res, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:67:        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:73:        JSONObject full = mr.jsonForApiGet(-1, null, myShepherd);
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:22:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:23:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:28:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:29:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:54:        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:62:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:66:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:67:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:68:                Shepherd myShepherd = new Shepherd("fake");
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:69:                ma.opensearchDocumentSerializer(jgen, myShepherd);
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:24:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:25:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:28:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:29:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:53:        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:63:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:67:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:68:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:69:                Shepherd myShepherd = new Shepherd("fake");
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:70:                occ.opensearchDocumentSerializer(jgen, myShepherd);
src/test/java/org/ecocean/ImportTaskTest.java:7:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/ImportTaskTest.java:73:        when(itask.iaSummaryJson(any(Shepherd.class))).thenCallRealMethod();
src/test/java/org/ecocean/ImportTaskTest.java:85:        JSONObject sum = itask.iaSummaryJson(mock(Shepherd.class));
src/test/java/org/ecocean/SettingTest.java:5:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/SettingTest.java:6:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/SettingTest.java:17:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/SettingTest.java:18:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/SettingTest.java:110:        // FIXME we must test with a list *of values* but we need Shepherd/db for this :(
src/test/java/org/ecocean/SettingTest.java:139:        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/SettingTest.java:143:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/SettingTest.java:147:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/SettingTest.java:148:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/AnnotationTest.java:11:    to create a (real) MediaAsset or a Feature we need to have a Shepherd!
src/test/java/org/ecocean/AnnotationTest.java:12:    TODO: extend to this with mockito/Shepherd
src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java:8:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java:29:        Shepherd shepherd = mock(Shepherd.class);
src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java:115:        Shepherd shepherd = mock(Shepherd.class);
src/test/java/org/ecocean/BaseTest.java:10:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/BaseTest.java:70:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/BaseTest.java:78:                JSONObject json = enc.opensearchDocumentAsJSONObject(myShepherd);
src/test/java/org/ecocean/EmbeddingTest.java:14:    to create a (real) MediaAsset or a Feature we need to have a Shepherd!
src/test/java/org/ecocean/EmbeddingTest.java:15:    TODO: extend to this with mockito/Shepherd
src/test/java/org/ecocean/EmbeddingTest.java:52:    // subtask outcomes) need a real Annotation/Shepherd/OpenSearch to
src/test/java/org/ecocean/EmbeddingTest.java:85:        // Task.getStatus(Shepherd) is the public accessor and runs an
src/test/java/org/ecocean/EmbeddingTest.java:87:        // unit test on a brand-new in-memory Task, we go through the
src/test/java/org/ecocean/api/TaskMatchResults.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/TaskMatchResults.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/TaskMatchResults.java:13:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/TaskMatchResults.java:14:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/TaskMatchResults.java:49:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/TaskMatchResults.java:59:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/TaskMatchResults.java:67:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/TaskMatchResults.java:81:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/TaskMatchResults.java:85:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/TaskMatchResults.java:86:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/TaskMatchResults.java:103:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/TaskMatchResults.java:105:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/TaskMatchResults.java:110:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/TaskMatchResults.java:111:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/TaskMatchResults.java:128:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/TaskMatchResults.java:130:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/TaskMatchResults.java:135:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/TaskMatchResults.java:136:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/TaskMatchResults.java:156:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/TaskMatchResults.java:158:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/TaskMatchResults.java:163:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/TaskMatchResults.java:164:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:37:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:160:        // Initialize test data via Shepherd
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:161:        // DataNucleus will auto-create tables on first access
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:355:        Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:383:            myShepherd);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:416:            myShepherd.closeDBTransaction();
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:741:     * Initialize test data in the database using Shepherd.
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:742:     * DataNucleus will auto-create schema based on entity annotations.
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:745:        System.out.println("Initializing test data via Shepherd...");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:748:        // ShepherdPMF will read these and connect to our Testcontainers PostgreSQL
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:756:        org.ecocean.shepherd.core.Shepherd myShepherd = new org.ecocean.shepherd.core.Shepherd(
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:760:            FeatureType.initAll(myShepherd);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:762:            myShepherd.beginDBTransaction();
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:771:            myShepherd.getPM().makePersistent(testUser);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:775:            myShepherd.getPM().makePersistent(researcherRole);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:796:            myShepherd.getPM().makePersistent(refKeyword);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:797:            myShepherd.getPM().makePersistent(laboratoryKeyword);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:798:            myShepherd.getPM().makePersistent(fieldKeyword);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:818:            myShepherd.storeNewEncounter(enc1);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:832:            myShepherd.storeNewEncounter(enc2);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:846:            myShepherd.storeNewEncounter(enc3);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:865:            myShepherd.storeNewEncounter(enc4);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:877:            myShepherd.storeNewMarkedIndividual(ind1);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:882:            myShepherd.storeNewMarkedIndividual(ind2);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:894:            myShepherd.storeNewAnnotation(ann1);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:903:            myShepherd.storeNewAnnotation(ann2);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:912:            myShepherd.storeNewAnnotation(ann3);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:922:            myShepherd.storeNewAnnotation(ann4);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:929:            myShepherd.storeNewOccurrence(occ1);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:936:            myShepherd.storeNewOccurrence(occ2);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:943:            myShepherd.storeNewOccurrence(occ3);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:951:            myShepherd.storeNewOccurrence(occ4);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:954:            myShepherd.getPM().makePersistent(asset1);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:955:            myShepherd.getPM().makePersistent(asset2);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:956:            myShepherd.getPM().makePersistent(asset3);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:957:            myShepherd.getPM().makePersistent(asset4);
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:959:            myShepherd.commitDBTransaction();
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:963:            myShepherd.rollbackDBTransaction();
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:966:            myShepherd.closeDBTransaction();
src/test/java/org/ecocean/api/EncounterApiTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/EncounterApiTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/EncounterApiTest.java:18:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/EncounterApiTest.java:19:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/EncounterApiTest.java:48:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/EncounterApiTest.java:59:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/EncounterApiTest.java:68:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/EncounterApiTest.java:81:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/EncounterApiTest.java:85:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/EncounterApiTest.java:86:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/EncounterApiTest.java:96:                Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/api/EncounterApiTest.java:97:                JSONObject json = encSpy.jsonForApiGet(myShepherd, null);
src/test/java/org/ecocean/api/EncounterApiTest.java:179:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/api/EncounterApiTest.java:185:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/EncounterApiTest.java:200:                    JSONObject res = enc.jsonForApiGet(myShepherd, null);
src/test/java/org/ecocean/api/EncounterApiTest.java:210:                            any(Shepherd.class))).thenReturn(mockMA);
src/test/java/org/ecocean/api/EncounterApiTest.java:215:                        when(myShepherd.getAnnotation(any(String.class))).thenReturn(mockAnnot);
src/test/java/org/ecocean/api/EncounterApiTest.java:223:                        res = enc.jsonForApiGet(myShepherd, user);
src/test/java/org/ecocean/security/PermissionsTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/security/PermissionsTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/security/PermissionsTest.java:16:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/security/PermissionsTest.java:17:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/security/PermissionsTest.java:56:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/security/PermissionsTest.java:63:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/security/PermissionsTest.java:76:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/security/PermissionsTest.java:77:        when(myShepherd.getUser(other.getUsername())).thenReturn(other);
src/test/java/org/ecocean/security/PermissionsTest.java:78:        when(myShepherd.getContext()).thenReturn("context0");
src/test/java/org/ecocean/security/PermissionsTest.java:89:            assertTrue(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:90:            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:93:            assertFalse(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:95:            assertFalse(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:101:            assertFalse(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:104:            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:118:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/security/PermissionsTest.java:119:        when(myShepherd.getUser(other.getUsername())).thenReturn(other);
src/test/java/org/ecocean/security/PermissionsTest.java:120:        when(myShepherd.getContext()).thenReturn("context0");
src/test/java/org/ecocean/security/PermissionsTest.java:131:            assertFalse(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:132:            assertFalse(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:135:            assertTrue(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:136:            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:143:            assertTrue(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:144:            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:151:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/security/PermissionsTest.java:153:        when(myShepherd.getContext()).thenReturn("context0");
src/test/java/org/ecocean/security/PermissionsTest.java:154:        assertFalse(enc.canUserEdit(null, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:157:        assertTrue(enc.canUserEdit(user, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:160:        when(adminUser.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/security/PermissionsTest.java:161:        assertTrue(enc.canUserEdit(adminUser, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:169:            assertFalse(enc.canUserEdit(user2, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:176:            assertFalse(enc.canUserEdit(user2, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:180:            assertTrue(enc.canUserEdit(user2, myShepherd));
src/test/java/org/ecocean/security/PermissionsTest.java:189:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/security/PermissionsTest.java:191:        when(myShepherd.getContext()).thenReturn(context);
src/test/java/org/ecocean/security/PermissionsTest.java:193:        when(userAdmin.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/security/PermissionsTest.java:239:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/security/PermissionsTest.java:241:        when(myShepherd.getContext()).thenReturn(context);
src/test/java/org/ecocean/security/PermissionsTest.java:243:        when(userAdmin.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/SettingApiTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/SettingApiTest.java:12:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/SettingApiTest.java:13:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/SettingApiTest.java:49:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/SettingApiTest.java:59:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/SettingApiTest.java:67:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:80:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:84:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:85:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:99:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/SettingApiTest.java:101:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:105:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:106:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:120:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:124:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:128:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:129:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:142:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:146:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:150:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:151:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:166:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:169:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:175:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:176:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:190:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:203:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:213:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:214:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:230:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:254:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:278:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:282:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:283:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:297:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/SettingApiTest.java:299:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:303:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:304:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:318:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:322:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:326:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:327:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:340:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:344:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:348:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:349:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:363:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:366:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:371:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:372:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/SettingApiTest.java:383:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/SettingApiTest.java:386:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/SettingApiTest.java:393:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/SettingApiTest.java:394:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/ia/IdentificationTest.java:7:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/ia/IdentificationTest.java:11:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/ia/IdentificationTest.java:41:        Shepherd myShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/ia/IdentificationTest.java:42:        when(myShepherd.getPM()).thenReturn(mockPM);
src/test/java/org/ecocean/ia/IdentificationTest.java:53:        when(mockIAConfig.identOpts(any(Shepherd.class),
src/test/java/org/ecocean/ia/IdentificationTest.java:65:                        any(Shepherd.class))).thenReturn(enc);
src/test/java/org/ecocean/ia/IdentificationTest.java:66:                    Task resTask = IA.intakeAnnotations(myShepherd, anns, parentTask, false);
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:16:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:17:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:53:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:64:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:74:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:78:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:79:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:97:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:101:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:102:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:119:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:123:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:124:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:294:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:301:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:302:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:327:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:338:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:339:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:368:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:375:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:376:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:404:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:413:                    try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:414:                        mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:439:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:446:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:447:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:473:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:484:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:485:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:509:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:520:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:521:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:548:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:559:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:560:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:585:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:593:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:594:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:622:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:629:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:630:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:672:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:683:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:684:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:19:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:20:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:57:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:72:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:172:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:183:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:184:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:210:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:223:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:224:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:248:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:262:                    any(Shepherd.class))).thenReturn(mockMediaAsset);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:263:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:264:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:290:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:304:                    any(Shepherd.class))).thenThrow(new ApiException(
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:306:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:307:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:326:        Shepherd mockShepherd = mock(Shepherd.class);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:335:            mockAS.when(() -> AssetStore.getDefault(any(Shepherd.class))).thenReturn(astore);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:342:                    testFile, mockShepherd);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:348:            mockAS.when(() -> AssetStore.getDefault(any(Shepherd.class))).thenReturn(astore);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:356:                    "00000000-0000-0000-0000-000000000000", testFile, mockShepherd);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:364:            mockAS.when(() -> AssetStore.getDefault(any(Shepherd.class))).thenReturn(astore);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:372:                    "00000000-0000-0000-0000-000000000000", testFile, mockShepherd);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:380:            mockAS.when(() -> AssetStore.getDefault(any(Shepherd.class))).thenReturn(astore);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:388:                    "00000000-0000-0000-0000-000000000000", testFile, mockShepherd);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:404:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:418:                    any(Shepherd.class))).thenReturn(mockMediaAsset);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:419:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:420:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:15: * <p>The Phase 1-5 lifecycle methods require real Shepherd transactions,
src/test/java/org/ecocean/servlet/importer/ImportTaskCountsTest.java:10:import javax.jdo.Query;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:21: * the no-Shepherd WBIA registration helpers introduced in
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:11:class ShepherdStateTest {
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:15:            new StaticFieldClearExtension(ShepherdState.class, "shepherds");
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:18:    void testSetAndGetShepherdState() {
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:19:        ShepherdState.setShepherdState("shepherd1", "active");
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:20:        assertEquals("active", ShepherdState.getShepherdState("shepherd1"));
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:24:    void testRemoveShepherdState() {
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:25:        ShepherdState.setShepherdState("shepherd2", "inactive");
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:26:        ShepherdState.removeShepherdState("shepherd2");
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:27:        assertNull(ShepherdState.getShepherdState("shepherd2"));
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:31:    void testGetAllShepherdStates() {
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:32:        ShepherdState.setShepherdState("shepherd3", "busy");
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:33:        ConcurrentHashMap<String, String> allStates = ShepherdState.getAllShepherdStates();
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:39:    void testOverwriteShepherdState() {
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:40:        ShepherdState.setShepherdState("shepherd4", "idle");
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:41:        ShepherdState.setShepherdState("shepherd4", "working");
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:42:        assertEquals("working", ShepherdState.getShepherdState("shepherd4"));
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:46:    void testGetStateForUnknownShepherd() {
src/test/java/org/ecocean/shepherd/utils/ShepherdStateTest.java:47:        assertNull(ShepherdState.getShepherdState("unknown"));
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:18:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:19:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:55:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:67:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:79:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:82:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:86:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:87:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:100:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:105:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:106:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:121:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:127:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:128:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:142:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:148:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:149:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:168:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:172:        when(fakeTask.iaSummaryJson(any(Shepherd.class))).thenReturn(new JSONObject());
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:177:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:183:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:184:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:186:                    mockSU.when(() -> ServletUtilities.isUserAuthorizedForImportTask(any(ImportTask.class), any(HttpServletRequest.class), any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:199:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:201:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:207:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:208:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:222:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:228:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:229:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:248:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:253:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:254:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:278:        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:285:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:292:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:293:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:310:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:315:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:316:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:338:        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:345:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:352:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:353:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:3:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:4:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:6:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:7:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:8:import javax.jdo.Transaction;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:22:public class ShepherdTest {
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:23:    private MockedStatic<ShepherdPMF> mockedShepherdPMF;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:24:    private PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:34:        // Create mock PersistenceManagerFactory and PM creation
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:35:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:38:        // Open the static mock for ShepherdPMF
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:39:        mockedShepherdPMF = Mockito.mockStatic(ShepherdPMF.class);
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:41:        mockedShepherdPMF.when(() -> ShepherdPMF.getPMF(anyString(), any()))
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:47:        mockedShepherdPMF.close();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:50:    @Test public void testBasicShepherdInitialization() {
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:51:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:53:        assertEquals("testContext", testShepherd.getContext());
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:54:        assertEquals(mockPM, testShepherd.getPM());
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:59:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:60:        testShepherd.beginDBTransaction();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:61:        // Shepherd should add the WildbookLifecycleListener() once when beginning a transaction
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:68:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:69:        testShepherd.beginDBTransaction();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:70:        // Shepherd should not begin a trans
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:78:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:79:        testShepherd.commitDBTransaction();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:85:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:86:        testShepherd.commitDBTransaction();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:92:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:93:        testShepherd.rollbackDBTransaction();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:99:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:100:        assertTrue(testShepherd.isDBTransactionActive());
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:105:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:106:        assertFalse(testShepherd.isDBTransactionActive());
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:111:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:112:        testShepherd.closeDBTransaction(); // note:  closeDBTransaction actually closes the persistence manager, not the transaction
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:117:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:120:        testShepherd.setAction(action);
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:121:        assertEquals(action, testShepherd.getAction());
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:4:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:5:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:13:import javax.jdo.*;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:23:    private MockedStatic<ShepherdPMF> mockedShepherdPMF;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:24:    private PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:37:        // Create mock PersistenceManagerFactory and PM creation
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:38:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:41:        // Open the static mock for ShepherdPMF
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:42:        mockedShepherdPMF = mockStatic(ShepherdPMF.class);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:44:        mockedShepherdPMF.when(() -> ShepherdPMF.getPMF(anyString(), any()))
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:50:        mockedShepherdPMF.close();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:56:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:59:        boolean returnValue = testShepherd.storeNewMarkedIndividual(markedIndividual);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:61:        verify(testShepherd, times(1)).beginDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:63:        verify(testShepherd, times(1)).commitDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:70:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:73:        boolean returnValue = testShepherd.storeNewMarkedIndividual(markedIndividual);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:75:        verify(testShepherd, times(1)).beginDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:77:        verify(testShepherd, times(1)).commitDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:84:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:86:        boolean returnValue = testShepherd.storeNewMarkedIndividual(markedIndividual);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:90:        verify(testShepherd, times(1)).rollbackDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:96:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:99:        boolean returnValue = testShepherd.storeNewScheduledIndividualMerge(
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:102:        verify(testShepherd, times(1)).beginDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:104:        verify(testShepherd, times(1)).commitDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:111:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:113:        testShepherd.throwAwayMarkedIndividual(markedIndividual);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:121:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:122:        assertTrue(testShepherd.isMarkedIndividual("testIndividual"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:128:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:129:        assertTrue(testShepherd.isMarkedIndividual(""));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:134:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:135:        assertFalse(testShepherd.isMarkedIndividual(""));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:139:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:142:        assertFalse(testShepherd.isMarkedIndividual(markedIndividual));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:148:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:150:            testShepherd.getMarkedIndividualQuiet("some individual"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:160:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:161:        assertEquals(expected, testShepherd.getMarkedIndividualsFromProject(testProject));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:165:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:168:        assertNull(testShepherd.getMarkedIndividual(testEncounter));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:173:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:177:        assertNull(testShepherd.getMarkedIndividual(mockEncounter));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:181:       // todo:  verify these are removed or refactored after Shepherd method cleanup.
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:5:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:6:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:9:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:10:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:40:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:155:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:163:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:164:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:177:        // note, you should mock Shepherd.isValidTaxonomyName() to allow these
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:190:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:198:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:199:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:221:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:230:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:231:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:310:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:321:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:322:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:451:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:462:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:463:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:466:                Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:470:                assertEquals(Util.collectionSize(enc.getProjects(myShepherd)), 0);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:492:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:500:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:501:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:599:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:610:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:611:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:650:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:659:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:660:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:708:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:717:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:718:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:760:        // Shepherd should be handled by caller via MockConstruction etc
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:762:        Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:767:        row = BulkImportUtil.validateRow(new JSONObject(singleRowData), myShepherd);
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:781:        BulkImporter imp = new BulkImporter(impId, allRows, null, null, myShepherd);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:15:import org.ecocean.shepherd.core.Shepherd;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:16:import org.ecocean.shepherd.core.ShepherdPMF;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:53:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:66:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:104:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:109:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:110:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:133:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:137:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:138:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:159:        // when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:169:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:175:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:176:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:205:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:211:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:212:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:242:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:248:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:249:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:282:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:288:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:289:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:321:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:327:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:328:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:359:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:365:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:366:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:418:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:425:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:426:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:474:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:480:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:481:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:519:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:525:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:526:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:557:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:564:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:565:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:596:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:602:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:603:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:636:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:643:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:644:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:675:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:682:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:683:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:715:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:722:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:723:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:758:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:765:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:766:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:799:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:806:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:807:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:840:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:846:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:847:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:869:        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:880:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:887:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:888:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:909:        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:913:        // when(enc.getOccurrence(any(Shepherd.class))).thenReturn(occ);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:921:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:928:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:929:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:959:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:966:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:967:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:990:        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(false);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1001:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1008:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1009:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1031:        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(true);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1042:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1049:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1050:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1084:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1091:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1092:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1124:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1131:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1132:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1163:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1170:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1171:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1214:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1221:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1222:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1267:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1274:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1275:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1324:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1328:            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1329:                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1458:        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1465:                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1466:                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(

 succeeded in 393ms:
pom.xml:25:    <testcontainers.version>2.0.3</testcontainers.version>
pom.xml:63:        <groupId>org.testcontainers</groupId>
pom.xml:64:        <artifactId>testcontainers-bom</artifactId>
pom.xml:65:        <version>${testcontainers.version}</version>
pom.xml:424:    <!-- Testcontainers for integration testing -->
pom.xml:426:        <groupId>org.testcontainers</groupId>
pom.xml:427:        <artifactId>testcontainers</artifactId>
pom.xml:432:        <groupId>org.testcontainers</groupId>
pom.xml:433:        <artifactId>testcontainers-postgresql</artifactId>
pom.xml:438:        <groupId>org.testcontainers</groupId>
pom.xml:439:        <artifactId>testcontainers-junit-jupiter</artifactId>
pom.xml:811:        <container>
pom.xml:812:          <containerId>tomcat9x</containerId>
pom.xml:817:        </container>
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:42:import org.testcontainers.containers.GenericContainer;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:43:import org.testcontainers.containers.PostgreSQLContainer;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:44:import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:45:import org.testcontainers.junit.jupiter.Container;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:46:import org.testcontainers.junit.jupiter.Testcontainers;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:47:import org.testcontainers.utility.DockerImageName;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:66: * Uses Testcontainers for PostgreSQL and OpenSearch, WireMock for external image mocking,
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:69:@Testcontainers @TestMethodOrder(MethodOrderer.OrderAnnotation.class) public class
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:74:    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:174:        System.out.println("PostgreSQL container will be stopped automatically");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:175:        System.out.println("OpenSearch container will be stopped automatically");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:732:     * Get the OpenSearch base URL for the running container.
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:748:        // ShepherdPMF will read these and connect to our Testcontainers PostgreSQL
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1006:        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1019:        assertTrue(opensearch.isRunning(), "OpenSearch container should be running");

 succeeded in 611ms:
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:64:                doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:68:                Shepherd myShepherd = new Shepherd("fake");
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:65:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:69:                Shepherd myShepherd = new Shepherd("fake");
src/test/java/org/ecocean/api/TaskMatchResults.java:83:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/SettingApiTest.java:82:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/SettingApiTest.java:209:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/SettingApiTest.java:280:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/SettingApiTest.java:390:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:56:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:61:        verify(testShepherd, times(1)).beginDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:70:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:75:        verify(testShepherd, times(1)).beginDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:84:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:96:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:102:        verify(testShepherd, times(1)).beginDBTransaction();
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:111:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:121:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:128:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:134:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:139:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:148:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:160:        Shepherd testShepherd = spy(new Shepherd("testContext"));
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:165:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:173:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/api/EncounterApiTest.java:83:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/EncounterApiTest.java:96:                Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:51:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:59:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:60:        testShepherd.beginDBTransaction();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:68:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:69:        testShepherd.beginDBTransaction();
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:78:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:85:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:92:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:99:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:105:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:111:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:117:        Shepherd testShepherd = new Shepherd("testContext");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:355:        Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:762:            myShepherd.beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:76:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:84:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:103:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:125:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:146:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:181:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:205:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:226:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:251:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:290:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:313:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:350:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:466:                Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:762:        Shepherd myShepherd = new Shepherd("context0");
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:107:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:135:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:173:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:209:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:246:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:286:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:325:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:363:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:423:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:478:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:523:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:562:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:600:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:641:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:680:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:720:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:763:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:804:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:844:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:885:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:926:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:964:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1006:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1047:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1089:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1129:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1168:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1219:            doNothing().when(mock).beginDBTransaction();
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:1272:            doNothing().when(mock).beginDBTransaction();
src/main/java/org/ecocean/AcmIdBot.java:130:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/AcmIdBot.java:133:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/IndividualQueryProcessor.java:39:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/IndividualQueryProcessor.java:700:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/IndividualQueryProcessor.java:772:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/LocationID.java:50:        Shepherd myShepherd = new Shepherd(request);
src/main/java/org/ecocean/LocationID.java:53:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/CommonConfiguration.java:188:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/CommonConfiguration.java:191:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/CommonConfiguration.java:235:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Annotation.java:721:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Annotation.java:1407:                myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Embedding.java:153:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Embedding.java:186:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Embedding.java:224:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/EncounterQueryProcessor.java:51:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/EncounterQueryProcessor.java:53:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/EncounterQueryProcessor.java:1139:            // myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:2530:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Encounter.java:2586:            myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:2592:            myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:2600:            myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:3134:        Shepherd myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Encounter.java:3137:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:3529:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/Encounter.java:3531:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:4138:        Shepherd myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Encounter.java:4140:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:4169:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Encounter.java:4254:        Shepherd myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Encounter.java:4257:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:4626:                Shepherd bgShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Encounter.java:4628:                bgShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:5428:                Shepherd myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Encounter.java:5431:                myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:5522:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/Encounter.java:5525:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Encounter.java:5585:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IdentityServiceLog.java:135:        // Shepherd myShepherd=new Shepherd(context);
src/main/java/org/ecocean/identity/IdentityServiceLog.java:137:        // myShepherd.beginDBTransaction();
src/main/java/org/ecocean/LabeledKeyword.java:117:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/IndexingManager.java:59:                    Shepherd bgShepherd = new Shepherd("context0");
src/main/java/org/ecocean/IndexingManager.java:61:                    bgShepherd.beginDBTransaction();
src/main/java/org/ecocean/Util.java:319:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/Util.java:322:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Util.java:699:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Util.java:717:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Util.java:1290:        Shepherd myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Util.java:1291:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/User.java:197:        Shepherd myShepherd = new Shepherd(request);
src/main/java/org/ecocean/User.java:200:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/cache/QueryCache.java:33:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/cache/QueryCache.java:34:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/cache/CachedQuery.java:61:                myShepherd.beginDBTransaction();
src/main/java/org/ecocean/cache/CachedQuery.java:107:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/cache/CachedQuery.java:109:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:242:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:244:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:492:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:494:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:556:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:558:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:650:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:653:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:753:            Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:755:            myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:1006:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:1009:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:1021:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:1024:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:1062:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:1065:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:1401:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:1403:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:1452:            Shepherd myShepherd2 = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:1454:            myShepherd2.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:1776:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:1778:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:2025:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/identity/IBEISIA.java:2028:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:2591:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/identity/IBEISIA.java:3745:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/grid/MatchGraphCreationThread.java:46:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/grid/MatchGraphCreationThread.java:60:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/grid/MatchGraphCreationThread.java:73:            myShepherd.beginDBTransaction();
src/main/java/org/ecocean/grid/MatchGraphCreationThread.java:87:                    myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Base.java:236:        Shepherd myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Base.java:239:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Base.java:262:        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/grid/GridManager.java:239:            Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/grid/GridManager.java:241:            myShepherd.beginDBTransaction();
src/main/java/org/ecocean/OccurrenceQueryProcessor.java:46:        // Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/ApiAccess.java:35:        // Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/ApiAccess.java:87:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/ApiAccess.java:89:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/grid/GridCleanupThread.java:31:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/grid/GridCleanupThread.java:35:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/grid/GridCleanupThread.java:51:                myShepherd.beginDBTransaction();
src/main/java/org/ecocean/grid/GridCleanupThread.java:56:                myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Occurrence.java:690:        if (shepherdWasCommitting) myShepherd.beginDBTransaction();
src/main/java/org/ecocean/Occurrence.java:1501:                Shepherd bgShepherd = new Shepherd("context0");
src/main/java/org/ecocean/Occurrence.java:1503:                bgShepherd.beginDBTransaction();
src/main/java/org/ecocean/MetricsBot.java:109:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/MetricsBot.java:112:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/MetricsBot.java:461:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/MetricsBot.java:463:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/MetricsBot.java:559:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/MetricsBot.java:561:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/api/UserInfo.java:19:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/api/UserInfo.java:22:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/api/UserInfo.java:45:        Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/api/UserInfo.java:48:        myShepherd.beginDBTransaction();
src/main/java/org/ecocean/NotificationMailer.java:171:                Shepherd myShepherd = new Shepherd(context);
src/main/java/org/ecocean/NotificationMailer.java:173:                myShepherd.beginDBTransaction();
src/main/java/org/ecocean/metrics/WildbookMetrics.java:23:        // this.myShepherd = new Shepherd("context0");
src/main/java/org/ecocean/metrics/WildbookMetrics.java:25:        // this.myShepherd.beginDBTransaction();

exec
/bin/bash -lc 'rg -n "rollbackAndClose|rollbackDBTransaction|closeDBTransaction|isActive" src/main/java/org/ecocean/shepherd/core/Shepherd.java' in /mnt/c/Wildbook-clean2
 succeeded in 334ms:
108:    public void rollbackAndClose() {
109:        rollbackDBTransaction();
110:        closeDBTransaction();
141:            rollbackDBTransaction();
163:            rollbackDBTransaction();
180:            rollbackDBTransaction();
196:            rollbackDBTransaction();
209:            rollbackDBTransaction();
221:            rollbackDBTransaction();
234:            rollbackDBTransaction();
246:            rollbackDBTransaction();
259:            rollbackDBTransaction();
272:            rollbackDBTransaction();
287:            rollbackDBTransaction();
300:            rollbackDBTransaction();
315:            rollbackDBTransaction();
329:            rollbackDBTransaction();
344:            rollbackDBTransaction();
359:            rollbackDBTransaction();
374:            rollbackDBTransaction();
388:            rollbackDBTransaction();
403:            rollbackDBTransaction();
1017:            rollbackDBTransaction();
1420:            rollbackDBTransaction();
3296:            } else if (!pm.currentTransaction().isActive()) {
3311:        return (pm != null && !pm.isClosed() && pm.currentTransaction().isActive());
3322:            if ((pm != null) && (pm.currentTransaction().isActive())) {
3372:    public void closeDBTransaction() {
3395:    public void rollbackDBTransaction() {
3397:            if ((pm != null) && (pm.currentTransaction().isActive())) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/shepherd/core/Shepherd.java | sed -n '100,114p;3300,3410p'" in /mnt/c/Wildbook-clean2
 succeeded in 293ms:
   100	        return myShepherd;
   101	    }
   102	
   103	    public static Shepherd newActiveShepherd(HttpServletRequest req, String action) {
   104	        return newActiveShepherd(ServletUtilities.getContext(req), action);
   105	    }
   106	
   107	    // handy with a newActiveShepherd
   108	    public void rollbackAndClose() {
   109	        rollbackDBTransaction();
   110	        closeDBTransaction();
   111	    }
   112	
   113	    public String getContext() {
   114	        return localContext;
  3300	
  3301	            pm.addInstanceLifecycleListener(new WildbookLifecycleListener(), null);
  3302	        } catch (JDOUserException jdoe) {
  3303	            jdoe.printStackTrace();
  3304	        } catch (NullPointerException npe) {
  3305	            npe.printStackTrace();
  3306	        }
  3307	        // pmf=null;
  3308	    }
  3309	
  3310	    public boolean isDBTransactionActive() {
  3311	        return (pm != null && !pm.isClosed() && pm.currentTransaction().isActive());
  3312	    }
  3313	
  3314	    /**
  3315	     * Commits (makes permanent) any changes made to an open database
  3316	     */
  3317	    // TODO: Either (a) throw an exception itself or (b) return boolean of success (the latter was disabled, needs investigation)
  3318	    public void commitDBTransaction() {
  3319	        try {
  3320	            // System.out.println("     shepherd:"+identifyMe+" is trying to commit a transaction");
  3321	            // System.out.println("Is the pm null? " + Boolean.toString(pm == null));
  3322	            if ((pm != null) && (pm.currentTransaction().isActive())) {
  3323	                // System.out.println("     Now commiting a transaction with pm"+(String)pm.getUserObject());
  3324	                pm.currentTransaction().commit();
  3325	
  3326	                // return true;
  3327	                // System.out.println("A transaction has been successfully committed.");
  3328	            } else {
  3329	                System.out.println("You are trying to commit an inactive transaction.");
  3330	                // return false;
  3331	            }
  3332	            ShepherdState.setShepherdState(action + "_" + shepherdID, "commit");
  3333	        } catch (JDOUserException jdoe) {
  3334	            jdoe.printStackTrace();
  3335	            System.out.println("I failed to commit a transaction." + "\n" + jdoe.getStackTrace());
  3336	            // return false;
  3337	        } catch (JDOException jdoe2) {
  3338	            jdoe2.printStackTrace();
  3339	            Throwable[] throwables = jdoe2.getNestedExceptions();
  3340	            int numThrowables = throwables.length;
  3341	            for (int i = 0; i < numThrowables; i++) {
  3342	                Throwable t = throwables[i];
  3343	                if (t instanceof java.sql.SQLException) {
  3344	                    java.sql.SQLException exc = (java.sql.SQLException)t;
  3345	                    java.sql.SQLException g = exc.getNextException();
  3346	                    g.printStackTrace();
  3347	                }
  3348	                t.printStackTrace();
  3349	            }
  3350	            // return false;
  3351	        }
  3352	        // added to prevent conflicting calls jah 1/19/04
  3353	        catch (NullPointerException npe) {
  3354	            System.out.println(
  3355	                "A null pointer exception was thrown while trying to commit a transaction!");
  3356	            npe.printStackTrace();
  3357	            // return false;
  3358	        }
  3359	    }
  3360	
  3361	    /**
  3362	     * Since we call these together all over Wildbook
  3363	     */
  3364	    public void updateDBTransaction() {
  3365	        commitDBTransaction();
  3366	        beginDBTransaction();
  3367	    }
  3368	
  3369	    /**
  3370	     * Closes a PersistenceManager
  3371	     */
  3372	    public void closeDBTransaction() {
  3373	        try {
  3374	            if ((pm != null) && (!pm.isClosed())) {
  3375	                pm.close();
  3376	            }
  3377	            // ShepherdState.setShepherdState(action+"_"+shepherdID, "close");
  3378	            ShepherdState.removeShepherdState(action + "_" + shepherdID);
  3379	
  3380	            // logger.info("A PersistenceManager has been successfully closed.");
  3381	        } catch (JDOUserException jdoe) {
  3382	            System.out.println("I hit an error trying to close a DBTransaction.");
  3383	            jdoe.printStackTrace();
  3384	
  3385	            // logger.error("I failed to close a PersistenceManager."+"\n"+jdoe.getStackTrace());
  3386	        } catch (NullPointerException npe) {
  3387	            System.out.println("I hit a NullPointerException trying to close a DBTransaction.");
  3388	            npe.printStackTrace();
  3389	        }
  3390	    }
  3391	
  3392	    /**
  3393	     * Undoes any changes made to an open database.
  3394	     */
  3395	    public void rollbackDBTransaction() {
  3396	        try {
  3397	            if ((pm != null) && (pm.currentTransaction().isActive())) {
  3398	                // System.out.println("     Now rollingback a transaction with pm"+(String)pm.getUserObject());
  3399	                pm.currentTransaction().rollback();
  3400	                // System.out.println("A transaction has been successfully committed.");
  3401	            } else {
  3402	                // System.out.println("You are trying to rollback an inactive transaction.");
  3403	            }
  3404	            ShepherdState.setShepherdState(action + "_" + shepherdID, "rollback");
  3405	        } catch (JDOUserException jdoe) {
  3406	            jdoe.printStackTrace();
  3407	        } catch (JDOFatalUserException fdoe) {
  3408	            fdoe.printStackTrace();
  3409	        } catch (NullPointerException npe) {
  3410	            npe.printStackTrace();

codex
**Findings**

- **Blocker: deferred age-out cannot reach 1h with `IAGateway.requeueJob` as-is.** `requeueJob` hard-caps at `MAX_RETRIES = 30` and only applies the 30s delay when `increment == true` ([IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:751), [IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785)). That means a deferred match using `requeueJob(payload, true)` disappears after about 15-16 minutes, before `MAX_DEFER_AGE_MILLIS = 1h`, so `GIVE_UP` may never run. Setting `__queueRetries` in JSON alone also does not create the delay. Either add a deferred-specific scheduler/cap, parameterize `requeueJob`, or reduce the design cap to match the real queue cap.

- **Major: sibling child-task lookup needs to explicitly ignore the topTask and old tasks.** The topTask itself contains all sibling media assets ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:148)), while per-asset children contain singleton media assets ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:283)). A naive `Task.getTasksFor(ma)` includes any task containing that MA and orders by creation ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:553)), so it can pick the topTask or an older unrelated task. The design should require iterating direct `topTask.getChildren()` and selecting the child whose singleton `objectMediaAssets` contains the sibling MA.

- **Major: child Task terminal statuses are not the same as MediaAsset statuses.** Successful child tasks are marked `"completed"` ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:668)), and `Task.statusInEndState()` recognizes `"completed"`, `"error"`, and `"dropped-stale"` ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:85)). The design’s terminal table omits `"completed"` for child tasks. Also, `Task.getStatus(Shepherd)` is derived and can mutate timed-out tasks ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:597)); the gate needs a raw persisted-status accessor, not `getStatus()`.

- **Major: the proposed `waitForMatchable` query is not actually equivalent to the matching predicate.** `getMatchingSetQuery` includes taxonomy, viewpoint, iaClass, `matchAgainst`, `acmId`, encounter exclusion, dead-animal exclusion, and task filter params ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:925), [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1031)). The conceptual `waitForMatchable` body only checks id, `matchAgainst`, `acmId`, and embedding method/version. If the intent is full “would this caller’s match query see it,” build from each caller annotation’s actual matching-set query. If the intent is only “doc has fresh embedding metadata,” name it that way.

- **Medium: keep `_count` query shape compatible with existing `queryCount`.** Current code deliberately uses an `ids` query ([OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:541)); the new helper should do the same rather than `terms` on `_id`. Also do not include `size` or `track_total_hits` in the body passed to `_count`, since `queryCount` only strips `_source` ([OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:552)).

- **Medium: Phase 2 wording says MA detectionStatus `complete`, but v2 success is `complete-mlservice`.** `persistDetections` and zero-detection finalization set `STATUS_COMPLETE_MLSERVICE` ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:365)). If implementation adds a MA-status SQL predicate, it must include both `complete` and `complete-mlservice`.

- **Medium: Track 1 cache invalidation needs the same null-safety as the strict getter.** `QueryCacheFactory.getQueryCache` can return null ([QueryCacheFactory.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCacheFactory.java:6)), so direct `getQueryCache(context).invalidateByName(...)` can NPE. Use a small safe invalidation helper. Also clarify that lenient `iaImageIds` remains uncached; only the strict image variant would use the `"iaImageIds"` cache key.

**Open Questions**

1. Put `waitForMatchable` on `OpenSearch` only if it is explicitly annotation-schema-specific, e.g. `waitForAnnotationMatchableIds`. Otherwise keep it private to the gate.
2. Yes, your methodVersion read is right: `getMatchQuery` applies version strictly when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205)).
3. Prefer `waitForAnnotationMatchableIds`; `waitForIndexed` is too broad.
4. Empty normalized wait set should return true, but normalize after unioning caller IDs.
5. One hour is reasonable operationally, but not with the current `requeueJob` cap. Fix the scheduler/cap mismatch first.
6. Add tests for the requeue cap, direct-child selection, raw `"completed"` task status, `_count` query body shape, and the null-safe cache invalidation path.

I did not write to any files or run tests.
tokens used
182,470
**Findings**

- **Blocker: deferred age-out cannot reach 1h with `IAGateway.requeueJob` as-is.** `requeueJob` hard-caps at `MAX_RETRIES = 30` and only applies the 30s delay when `increment == true` ([IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:751), [IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785)). That means a deferred match using `requeueJob(payload, true)` disappears after about 15-16 minutes, before `MAX_DEFER_AGE_MILLIS = 1h`, so `GIVE_UP` may never run. Setting `__queueRetries` in JSON alone also does not create the delay. Either add a deferred-specific scheduler/cap, parameterize `requeueJob`, or reduce the design cap to match the real queue cap.

- **Major: sibling child-task lookup needs to explicitly ignore the topTask and old tasks.** The topTask itself contains all sibling media assets ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:148)), while per-asset children contain singleton media assets ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:283)). A naive `Task.getTasksFor(ma)` includes any task containing that MA and orders by creation ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:553)), so it can pick the topTask or an older unrelated task. The design should require iterating direct `topTask.getChildren()` and selecting the child whose singleton `objectMediaAssets` contains the sibling MA.

- **Major: child Task terminal statuses are not the same as MediaAsset statuses.** Successful child tasks are marked `"completed"` ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:668)), and `Task.statusInEndState()` recognizes `"completed"`, `"error"`, and `"dropped-stale"` ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:85)). The design’s terminal table omits `"completed"` for child tasks. Also, `Task.getStatus(Shepherd)` is derived and can mutate timed-out tasks ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:597)); the gate needs a raw persisted-status accessor, not `getStatus()`.

- **Major: the proposed `waitForMatchable` query is not actually equivalent to the matching predicate.** `getMatchingSetQuery` includes taxonomy, viewpoint, iaClass, `matchAgainst`, `acmId`, encounter exclusion, dead-animal exclusion, and task filter params ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:925), [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1031)). The conceptual `waitForMatchable` body only checks id, `matchAgainst`, `acmId`, and embedding method/version. If the intent is full “would this caller’s match query see it,” build from each caller annotation’s actual matching-set query. If the intent is only “doc has fresh embedding metadata,” name it that way.

- **Medium: keep `_count` query shape compatible with existing `queryCount`.** Current code deliberately uses an `ids` query ([OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:541)); the new helper should do the same rather than `terms` on `_id`. Also do not include `size` or `track_total_hits` in the body passed to `_count`, since `queryCount` only strips `_source` ([OpenSearch.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:552)).

- **Medium: Phase 2 wording says MA detectionStatus `complete`, but v2 success is `complete-mlservice`.** `persistDetections` and zero-detection finalization set `STATUS_COMPLETE_MLSERVICE` ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:365)). If implementation adds a MA-status SQL predicate, it must include both `complete` and `complete-mlservice`.

- **Medium: Track 1 cache invalidation needs the same null-safety as the strict getter.** `QueryCacheFactory.getQueryCache` can return null ([QueryCacheFactory.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCacheFactory.java:6)), so direct `getQueryCache(context).invalidateByName(...)` can NPE. Use a small safe invalidation helper. Also clarify that lenient `iaImageIds` remains uncached; only the strict image variant would use the `"iaImageIds"` cache key.

**Open Questions**

1. Put `waitForMatchable` on `OpenSearch` only if it is explicitly annotation-schema-specific, e.g. `waitForAnnotationMatchableIds`. Otherwise keep it private to the gate.
2. Yes, your methodVersion read is right: `getMatchQuery` applies version strictly when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205)).
3. Prefer `waitForAnnotationMatchableIds`; `waitForIndexed` is too broad.
4. Empty normalized wait set should return true, but normalize after unioning caller IDs.
5. One hour is reasonable operationally, but not with the current `requeueJob` cap. Fix the scheduler/cap mismatch first.
6. Add tests for the requeue cap, direct-child selection, raw `"completed"` task status, `_count` query body shape, and the null-safe cache invalidation path.

I did not write to any files or run tests.
