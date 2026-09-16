OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3c99-3fb4-7790-bc3b-a9714d4de63f
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

# Design: fix empty match prospects on bulk imports (revision 5, LOCKED post-Codex round 4)

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

**v4 → v5** (post-Codex round 4; 1 blocker + 1 blocker/major + 3
majors + 2 mediums; Codex signaled lock after these):

- **Deferred enqueue explicitly calls `IAGateway.requeueJob(payload, true)`**
  (Codex round-4 Blocker). Pseudocode rewritten to call the
  function by name; `__queueRetries` is set by `requeueJob`
  itself, not by us.
- **`MediaAsset.getId()` comparison uses `.equals()`** (Codex
  round-4 Blocker/Major). `getId()` returns `String`, not `int`;
  `==` is reference equality.
- **Wait set is split into two predicates** (Codex round-4
  Major). Caller IDs wait on `_id` visibility (existing
  `waitForVisibility`); sibling eligible IDs wait on
  `waitForAnnotationMatchableIds` (new matchable predicate).
  Prevents a caller annotation that's not-yet-matchable from
  blocking its own match until age-out.
- **Phase 2 SQL skips `methodVersion` predicate when null**
  (Codex round-4 Major), matching `Annotation.getMatchQuery` at
  `Annotation.java:1207`. Method/version derived using the same
  fallback chain as `Embedding.findMatchProspects` —
  `iaConfig.method`/`version` then `MLService.getMethodValues`
  for legacy configs.
- **`Task.getStoredStatus()` added** to `Task.java` (Codex round-4
  Major). Returns the raw persisted status field without the
  timed-out-task mutation side-effect of `getStatus(Shepherd)`.
- **`MatchEligibilityQuery` extracted as package-visible class
  in `org.ecocean.ia`** (Codex round-4 OQ #2). Schema-sensitive
  SQL deserves focused tests; not buried inside the gate impl.
- **`safeInvalidate(context, name)` lives on `QueryCacheFactory`**
  (Codex round-4 OQ #1).
- **`topTask == null` logs WARN with childTaskId** (Codex round-4
  OQ #3). Abnormal degraded path, not silent.
- **Stale `MAX_DEFER_ATTEMPTS` / 50min references removed**
  (Codex round-4 Medium).
- **`MlServiceProcessor` gets a package-visible
  `DeferredMatchPublisher` seam** (Codex round-4 Medium) so DEFER
  assertions are clean without exposing private internals.

**v3 → v4** (post-Codex round 3; 1 blocker + 3 majors + 3 mediums):

- **`MAX_DEFER_AGE_MILLIS` reduced to 12 minutes** (Codex round-3
  Blocker). `IAGateway.requeueJob` caps at `MAX_RETRIES=30` with
  30s sleep (`IAGateway.java:751-810`) — ~15 min before the queue
  itself gives up. 12 min keeps `GIVE_UP` reachable inside the
  real cap with a small margin. Design no longer assumes any new
  scheduler.
- **Sibling child-task lookup uses `topTask.getChildren()` directly**
  (Codex round-3 Major). `Task.getTasksFor(ma)` returns any task
  containing the MA (includes topTask itself and old unrelated
  tasks) — wrong API. The gate iterates `topTask.getChildren()`
  and picks the child whose singleton `objectMediaAssets` contains
  the sibling MA.
- **Child Task terminal-status set corrected** (Codex round-3
  Major). `Task.statusInEndState()` at `Task.java:85` recognizes
  `"completed"`, `"error"`, `"dropped-stale"` for child tasks
  (note the distinction: `"completed"` for Task vs `"complete"`
  for MA detectionStatus). Use a raw persisted-status accessor,
  NOT `Task.getStatus(Shepherd)` which can mutate timed-out
  tasks (`Task.java:597`).
- **`waitForMatchable` renamed and rescoped** (Codex round-3
  Major). Renamed to `waitForAnnotationMatchableIds` (per round-3
  open-question answer #3); scope explicitly limited to "doc has
  fresh embedding metadata" (id + matchAgainst + acmId + nested
  embeddings.method/methodVersion). NOT a full matchingSetQuery
  replication — the narrower semantics are sufficient for the
  index-visibility race we're solving.
- **`_count` body shape** uses `ids` query (matching existing
  `OpenSearch.queryCount` at `OpenSearch.java:541`), no `size`,
  no `track_total_hits` (Codex round-3 Medium).
- **Phase 2 prose** updated to reference both `STATUS_COMPLETE`
  and `STATUS_COMPLETE_MLSERVICE` where it previously said only
  `complete` (Codex round-3 Medium).
- **Cache invalidation null-safety** (Codex round-3 Medium): wrap
  `QueryCacheFactory.getQueryCache(context).invalidateByName(name)`
  in a small `safeInvalidate(context, name)` helper because the
  factory can return null on uninitialized contexts.

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

Invalidations target the shared keys. `QueryCacheFactory.getQueryCache(context)`
can return null on uninitialized contexts (Codex round-3 Medium),
so wrap in a null-safe helper:

```java
// Either inline in WildbookIAM or as a static helper on QueryCacheFactory.
private static void safeInvalidate(String context, String cacheName) {
    try {
        QueryCache qc = QueryCacheFactory.getQueryCache(context);
        if (qc != null) qc.invalidateByName(cacheName);
    } catch (Exception ex) {
        // Cache invalidation must not abort the polling cycle; just log.
        System.out.println(
            "WARN: QueryCache invalidate " + cacheName + " failed: " + ex);
    }
}
```

Then:

- After successful image POST in Phase 0:
  `safeInvalidate(context, "iaImageIds")`.
- After successful annotation POST in Phase 1:
  `safeInvalidate(context, "iaAnnotationIds")` (prevents
  stale-cache duplicate POSTs if Phase C races or fails between
  attempts).

The lenient `iaImageIds` getter remains uncached; only the new
strict variant uses the `"iaImageIds"` cache key, mirroring the
existing `iaAnnotationIdsStrict` pattern.

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
**raw persisted status**.

**Finding the per-MA child Task** (Codex round-3 Major): do NOT
use `Task.getTasksFor(ma)` — it returns ANY task containing that
MA, which includes the topTask itself (whose
`objectMediaAssets` contains all siblings) and possibly older
unrelated tasks. Instead iterate `topTask.getChildren()` and
select the child whose singleton `objectMediaAssets` contains
the sibling MA:

```java
private Task findChildTaskForSibling(Task topTask, MediaAsset siblingMa) {
    List<Task> children = topTask.getChildren();
    if (children == null) return null;
    for (Task child : children) {
        List<MediaAsset> mas = child.getObjectMediaAssets();
        if (mas != null && mas.size() == 1 &&
            siblingMa.getId().equals(mas.get(0).getId())) {
            return child;
        }
    }
    return null;
}
```

**Use raw persisted status** (Codex round-3 Major + round-4
Major). The current public getter `Task.getStatus(Shepherd)` is
derived and can mutate timed-out tasks (`Task.java:597`), which we
don't want inside the gate. A raw accessor does NOT exist yet, so
this design adds one:

```java
// In Task.java, alongside getStatus():
/**
 * Returns the persisted status field WITHOUT the timed-out-task
 * mutation side-effect that getStatus(Shepherd) performs. Use
 * this when reading status for read-only decisions (e.g., gating)
 * where mutating timed-out tasks as a side-effect would corrupt
 * the caller's logic.
 */
public String getStoredStatus() {
    return this.status;
}
```

The gate reads `siblingChildTask.getStoredStatus()` and tests
against the terminal set listed below. Either-being-terminal is
sufficient.

Terminal vs non-terminal status sets:

**MediaAsset.detectionStatus** (constants from `IBEISIA.java:73-82`).
Terminal:
- `STATUS_COMPLETE` = `"complete"` (legacy WBIA path success)
- `STATUS_COMPLETE_MLSERVICE` = `"complete-mlservice"` (v2 success)
- `STATUS_PENDING` = `"pending"` (human review; won't auto-progress)
- `STATUS_PENDING_SPECIES` = `"pending-species"` (no taxonomy)
- `STATUS_ERROR` = `"error"`
- `STATUS_DROPPED_STALE` = `"dropped-stale"`

Non-terminal:
- `STATUS_INITIATED` = `"initiated"`
- `STATUS_PROCESSING` = `"processing"`
- `STATUS_PROCESSING_MLSERVICE` = `"processing-mlservice"`
- `"_new"` / `"_post_new"` (MA lifecycle pre-processing)
- `null` (uninitialized)

**Task.status** (per `Task.statusInEndState()` at `Task.java:85`,
which is the source of truth for the per-MA child Task; note the
"completed" / "complete" distinction between Task and MA).
Terminal:
- `"completed"` (child Task success; set by `markTaskCompleted`
  at `MlServiceProcessor.java:668`)
- `"error"`
- `"dropped-stale"`

Non-terminal:
- `null`, `""`, `"queuing"`, `"working"`, or any other value.

A sibling is treated as **terminal** for the gate when
`isMaTerminal(ma.detectionStatus) OR isTaskTerminal(childTask.rawStatus)`.

If any sibling is non-terminal, **defer** (see deferred-path
section). Caller's match does NOT run yet.

If all siblings are terminal, proceed to Phase 2.

#### Phase 2: collect eligible annotation IDs

For each sibling MA in topTask whose detectionStatus is in
`{STATUS_COMPLETE, STATUS_COMPLETE_MLSERVICE}` (i.e., contributed
annotations successfully), enumerate annotations through
`MediaAsset → Feature → Annotation`, filtered for ml-service match
eligibility. Siblings with terminal-error states
(`STATUS_ERROR`, `STATUS_DROPPED_STALE`) contributed nothing — they
are skipped from the eligibility set, not waited for.

**Method/version derivation** (Codex round-4 Major): use the same
fallback chain as `Embedding.findMatchProspects`
(`Embedding.java:349-355`). Read `iaConfig.method` and
`iaConfig.version` first; if `method` is blank, fall back to
`MLService.getMethodValues(iaConfig)[0]` and `[1]`. Carry both
values into Phase 2's SQL.

**Encapsulation** (Codex round-4 OQ #2): the SQL lives in a new
package-visible `MatchEligibilityQuery` class under
`org.ecocean.ia` so it has its own test surface, not buried
inside `MatchVisibilityGateImpl`:

```java
package org.ecocean.ia;

public final class MatchEligibilityQuery {
    public static Set<String> findEligibleAnnotationIds(
        Shepherd shep,
        Collection<String> siblingMaIds,
        String method,
        String methodVersion)
    throws SQLException;
}
```

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

**`methodVersion=null` handling** (Codex round-4 Major): if
`methodVersion` is null (legacy `_id_conf` entries that only
provided `api_endpoint`, where `MLService.getMethodValues`
returned a null version), OMIT the
`AND e."METHODVERSION" = ?` predicate entirely. This matches
`Annotation.getMatchQuery`'s strict-when-present behavior at
`Annotation.java:1205-1209`. The implementation builds the SQL
string conditionally rather than passing a null parameter.

This is a single `SELECT DISTINCT` query, no materialization of
Annotation objects. Returns a `Set<String>` of annotation IDs.

#### Phase 3: matchable-visibility wait on the eligible set

`waitForVisibility` today polls `_count` with an `ids` query
(`OpenSearch.java:521`). That confirms `_id` is in OS but does
NOT confirm the doc has its nested `embeddings.method` /
`methodVersion` indexed — a doc that's been written with the
right `_id` but stale nested fields would pass `_id`-only and
then knn-fail at match time.

**Scope** (Codex round-3 Major): the helper introduced here is
NOT a full replication of `getMatchingSetQuery` (which adds
taxonomy, viewpoint, iaClass, encounter exclusion, dead-animal
range, task params). It is narrower: "doc has fresh embedding
metadata." That's sufficient for the visibility race the gate
solves — the index lag is between persist and OS write of
embedding metadata, not between persist and the encounter/
taxonomy fields (which are written at the same time as id and
acmId).

The conceptual `_count` body (matching the shape `queryCount`
expects at `OpenSearch.java:541-552` — uses `ids` query, no
`size`, no `track_total_hits`):

```json
{
  "query": {
    "bool": {
      "filter": [
        { "ids": { "values": [ ...waitSetIds ] } },
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
the version predicate, matching `getMatchQuery`'s behavior
(strict-when-present, confirmed at `Annotation.java:1205-1209`).

New method:

```java
public boolean waitForAnnotationMatchableIds(
    Collection<String> ids,
    String method,
    String methodVersion,
    long timeoutMs)
throws IOException;
```

Same `_refresh`-on-entry + exponential-backoff polling pattern as
`waitForVisibility`. Lives on `OpenSearch.java` (per Codex round-3
open-question #1: annotation-schema-specific naming makes it
acceptable as a public method). **Empty wait set short-circuits
to `true`** (per round-3 open-question #4) — but normalize after
unioning caller IDs, so the gate always waits on at least the
caller's annotations.

If `waitForAnnotationMatchableIds` returns false (timeout),
**defer**.

**Two-wait split** (Codex round-4 Major): passing caller IDs
through the matchable predicate means a caller annotation that's
visible by `_id` but not-yet-matchable would defer until age-out.
Avoid that by running two waits:

```java
// Caller IDs: weaker predicate (just _id visibility). A caller
// annotation should not block its own match on its own matchable-
// metadata visibility; if it's missing matchAgainst/acmId/
// embedding metadata, the match will just return zero candidates,
// not hang the gate.
if (!os.waitForVisibility("annotation", callerAnnotationIds,
        VISIBILITY_TIMEOUT_MS)) {
    return GateOutcome.defer(...);
}

// Sibling eligible IDs: full matchable predicate (id +
// matchAgainst + acmId + embeddings.method/methodVersion).
Set<String> siblingsOnly = new LinkedHashSet<>(eligibleIds);
siblingsOnly.removeAll(callerAnnotationIds);
if (!siblingsOnly.isEmpty() &&
    !os.waitForAnnotationMatchableIds(siblingsOnly,
        method, methodVersion, VISIBILITY_TIMEOUT_MS)) {
    return GateOutcome.defer(...);
}
```

This makes the caller's own match resilient: if the caller's
embedding metadata is genuinely missing, that's a different
problem (no candidates returned), not a stuck wait.

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
private static final long MAX_DEFER_AGE_MILLIS = 12L * 60L * 1000L;
// 12 minutes from first deferral. Aligns with IAGateway.requeueJob
// real cap (MAX_RETRIES=30 × 30s = ~15min before the queue gives
// up on its own at IAGateway.java:751-810). With 12min, GIVE_UP
// fires inside the queue's window with margin. Most v2 imports
// complete sibling detection in <2min; 12min is 6× normal.

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
jobData.

**Use `requeueJob` explicitly** (Codex round-4 Blocker). Setting
`__queueRetries` on the JSON is NOT enough — `IAGateway.requeueJob`
only applies its 30s sleep when called with `increment=true`
(`IAGateway.java:785`). So:

```java
// In MlServiceProcessor.enqueueDeferredMatch (or its replacement).
private void enqueueDeferredMatch(List<String> annotationIds,
    String taskId, JSONObject matchConfig, GateOutcome lastGate) {
    JSONObject payload = new JSONObject();
    payload.put("mlServiceV2DeferredMatch", true);
    payload.put("__context", context);
    payload.put("taskId", taskId);
    payload.put("annotationIds", new JSONArray(annotationIds));
    if (matchConfig != null) payload.put("matchConfig", matchConfig);
    payload.put("attempt", lastGate.attempt + 1);
    payload.put("firstDeferredAt", lastGate.firstDeferredAt);
    payload.put("lastGateReason", lastGate.reason);
    // IAGateway.requeueJob(payload, true) is what creates the 30s
    // delay. Calling addToDetectionQueue directly would fire
    // immediately and hot-loop.
    IAGateway.requeueJob(payload, /* increment= */ true);
}
```

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
  visibility on caller's IDs". Logs WARN with `childTaskId` (Codex
  round-4 OQ #3): this is an abnormal degraded path, not silent
  normal behavior.
- **Sibling MA permanently stuck in `processing`**: bounded by
  `MAX_DEFER_AGE_MILLIS = 12 min` (well inside `requeueJob`'s
  ~15min queue cap). User's match runs against whatever's
  visible at age-out.
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

**Round-3 follow-up tests:**

- `gate_picksDirectChildTask_notTopTaskOrUnrelated` — set up
  `Task.getTasksFor(ma)` to return [topTask, oldUnrelatedTask,
  correctChild]; prove gate selects correctChild.
- `gate_usesRawStatusAccessor_notGetStatusShepherd` — verify
  `Task.getStatus(Shepherd)` is NOT called on siblings (mutation
  side-effect would corrupt the gate's read).
- `gate_treatsChildTaskCompleted_asTerminal` — child Task with
  status `"completed"` (Task value) while MA still has status
  `processing-mlservice`; sibling is terminal.
- `waitForAnnotationMatchableIds_usesIdsQuery_notTermsOnUnderscoreId` —
  observed body matches `OpenSearch.java:541` shape.
- `waitForAnnotationMatchableIds_bodyHasNoSizeOrTrackTotal` —
  `_count` doesn't accept those fields cleanly.
- `deferred_agesOutAt12min_withinRequeueCap` — verify GIVE_UP
  fires before requeueJob's 30-retry cap.
- `safeInvalidate_swallowsNull_fromQueryCacheFactory`
- `safeInvalidate_swallowsException_fromInvalidateByName`

**End-to-end on live deployment:** import 4+ same-species images,
observe that each annotation's `findMatchProspects` result
includes the other 3 as candidates. Same fire-salamander test
case that surfaced this bug.

## Testability seam (Codex round-4 Medium)

In addition to the `MatchVisibilityGate` interface, extract a
small publisher interface for the deferred-match enqueue path so
unit tests can assert DEFER cleanly without exposing private
internals or going through `IAGateway.requeueJob` for real:

```java
package org.ecocean.ia;

public interface DeferredMatchPublisher {
    void publish(JSONObject payload);
}

// Real impl wraps IAGateway.requeueJob(payload, true).
// Test impl captures payloads into a list for assertions.
```

`MlServiceProcessor` holds `private final DeferredMatchPublisher
deferredPublisher` and uses it instead of calling
`IAGateway.requeueJob` directly. Default constructor wires the
real impl; package-visible constructor accepts a test double.

## Open questions for Codex (round 5 — confirmatory)

All round-2 + round-3 open questions have been resolved and
incorporated into the design above:

- Cap by elapsed time, not attempt count (round-2 #1).
- Age-out posture: run match anyway with WARN (round-2 #2).
- Full v2 terminal-status set, with child Task status checked
  separately from MA status (round-2 #3, round-3 #3).
- `skipAutoIndexing`: no special handling (round-2 #4).
- Direct SQL for eligibility query (round-2 #5).
- Job payload for attempt + `firstDeferredAt` +
  `lastGateReason` (round-2 #6).
- Helper named `waitForAnnotationMatchableIds`, public on
  `OpenSearch.java` (round-3 #1, #3).
- `methodVersion` strict-when-present, matching `getMatchQuery`
  (round-3 #2).
- Empty wait set returns true after normalization (round-3 #4).
- `MAX_DEFER_AGE_MILLIS = 12min` (within `requeueJob` cap)
  (round-3 #5).

All round-4 open questions have been incorporated:

- `safeInvalidate(context, name)` lives on `QueryCacheFactory`
  (round-4 OQ #1).
- `MatchEligibilityQuery` extracted as package-visible class in
  `org.ecocean.ia` (round-4 OQ #2).
- `topTask == null` logs WARN with childTaskId (round-4 OQ #3).

This pass is confirmatory only. The expected outcome is "no
material findings". If Codex round 5 surfaces anything not
already in this revision history, address it inline; otherwise
the design is locked and ready for implementation in the order
specified.

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
I’ll review this against the actual branch rather than treating the bundle as authoritative. I’m going to verify the cited seams first: task status behavior, OpenSearch helpers, ml-service enqueue/deferred paths, and the eligibility query shape.
  → Verify cited code paths and constants
  • Check proposed SQL/schema assumptions
  • Review deferred-match sequencing and test gaps
  • Summarize findings with severity
exec
/bin/bash -lc 'git rev-parse --abbrev-ref HEAD' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short --branch' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"waitForVisibility|indexRefresh|queryCount|waitForAnnotation|skipAutoIndexing|class OpenSearch\" src/main/java/org/ecocean/OpenSearch.java src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"waitAndRunMatch|runDeferredMatch|enqueueDeferredMatch|runMatchProspects|markTaskCompleted|requeueJob|MAX_RETRIES|whileSleepMillis\" src/main/java/org/ecocean/ia src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 367ms:
migrate-ml-service-v2

 succeeded in 1064ms:
src/main/java/org/ecocean/OpenSearch.java:51:public class OpenSearch {
src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:468:    public void indexRefresh(final String indexName)
src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:519:        indexRefresh(indexName);
src/main/java/org/ecocean/OpenSearch.java:525:            int seen = queryCount(indexName, query);
src/main/java/org/ecocean/OpenSearch.java:552:    public int queryCount(String indexName, final JSONObject query)
src/main/java/org/ecocean/OpenSearch.java:563:            System.out.println("queryCount() on index " + indexName + " using query=" + query +
src/main/java/org/ecocean/OpenSearch.java:566:            throw new IOException("queryCount() failed");
src/main/java/org/ecocean/WildbookLifecycleListener.java:43:        if (OpenSearch.skipAutoIndexing()) {
src/main/java/org/ecocean/WildbookLifecycleListener.java:44:            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
src/main/java/org/ecocean/OpenSearch.java:51:public class OpenSearch {
src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:468:    public void indexRefresh(final String indexName)
src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:519:        indexRefresh(indexName);
src/main/java/org/ecocean/OpenSearch.java:525:            int seen = queryCount(indexName, query);
src/main/java/org/ecocean/OpenSearch.java:552:    public int queryCount(String indexName, final JSONObject query)
src/main/java/org/ecocean/OpenSearch.java:563:            System.out.println("queryCount() on index " + indexName + " using query=" + query +
src/main/java/org/ecocean/OpenSearch.java:566:            throw new IOException("queryCount() failed");
src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
src/main/java/org/ecocean/Embedding.java:343:                numberCandidates = os.queryCount("annotation", matchingSetQuery);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;

 succeeded in 1077ms:
src/main/java/org/ecocean/ia/MlServiceProcessor.java:56:            return runDeferredMatch(jobData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:133:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:172:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:229:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:234:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:279:            markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:366:            markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:395:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:405:            markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:447:            markTaskCompleted(taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:452:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:649:    private void markTaskCompleted(String taskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:651:        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:654:            markTaskCompleted(Task.load(taskId, shep));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:668:    private void markTaskCompleted(Task task) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
src/main/java/org/ecocean/ia/MLService.java:164:                requeueJob(jobData, iaex.shouldIncrement());
src/main/java/org/ecocean/ia/MLService.java:215:    public void requeueJob(JSONObject jobData, boolean increment) {
src/main/java/org/ecocean/ia/MLService.java:216:        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
src/main/java/org/ecocean/ia/MLService.java:219:        IAGateway.requeueJob(jobData, increment);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:56:            return runDeferredMatch(jobData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:133:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:172:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:229:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:234:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:279:            markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:366:            markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:395:                markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:405:            markTaskCompleted(task);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:447:            markTaskCompleted(taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:452:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:649:    private void markTaskCompleted(String taskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:651:        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:654:            markTaskCompleted(Task.load(taskId, shep));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:668:    private void markTaskCompleted(Task task) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
src/main/java/org/ecocean/ia/MLService.java:164:                requeueJob(jobData, iaex.shouldIncrement());
src/main/java/org/ecocean/ia/MLService.java:215:    public void requeueJob(JSONObject jobData, boolean increment) {
src/main/java/org/ecocean/ia/MLService.java:216:        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
src/main/java/org/ecocean/ia/MLService.java:219:        IAGateway.requeueJob(jobData, increment);
src/main/java/org/ecocean/servlet/IAGateway.java:385:                requeueJob(jobj, true);
src/main/java/org/ecocean/servlet/IAGateway.java:748:        if (requeue) requeueJob(jobj, requeueIncrement);
src/main/java/org/ecocean/servlet/IAGateway.java:751:    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
src/main/java/org/ecocean/servlet/IAGateway.java:752:        int MAX_RETRIES = 30;
src/main/java/org/ecocean/servlet/IAGateway.java:762:        if (elapsed > MAX_TIME_MILLIS) retries = MAX_RETRIES + 1; // waiting around too long
src/main/java/org/ecocean/servlet/IAGateway.java:763:        if (retries > MAX_RETRIES) {
src/main/java/org/ecocean/servlet/IAGateway.java:764:            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
src/main/java/org/ecocean/servlet/IAGateway.java:765:                MAX_RETRIES + " retries (or max time) in queue; giving up");
src/main/java/org/ecocean/servlet/IAGateway.java:768:        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
src/main/java/org/ecocean/servlet/IAGateway.java:769:            retries + " out of " + MAX_RETRIES + " (actualRetries=" + actualRetries + "; start=" +
src/main/java/org/ecocean/servlet/IAGateway.java:782:                long whileSleepMillis = 1000;
src/main/java/org/ecocean/servlet/IAGateway.java:785:                        if (increment) whileSleepMillis = 30000;
src/main/java/org/ecocean/servlet/IAGateway.java:786:                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:788:                            Thread.sleep(whileSleepMillis);
src/main/java/org/ecocean/servlet/IAGateway.java:803:                        whileSleepMillis = 30000;
src/main/java/org/ecocean/servlet/IAGateway.java:805:                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +

 succeeded in 11657ms:
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
?? docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md
?? docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md
?? docs/plans/2026-05-18-empty-match-prospects-codex-review.md
?? docs/plans/2026-05-18-empty-match-prospects-design.md
?? logs/

codex
The branch is `migrate-ml-service-v2`, and the current code matches the doc’s broad baseline: deferred matches currently bypass the visibility wait, while initial matches only wait for caller `_id` visibility. I’m drilling into the schema and query details now, since that’s where this design is most likely to fail late.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '1,180p;400,470p;640,710p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '900,940p;1185,1220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '120,160p;460,575p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,370p'" in /mnt/c/Wildbook-clean2
 succeeded in 268ms:
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

 succeeded in 208ms:
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

 succeeded in 222ms:
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

 succeeded in 273ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,120p;560,620p;720,770p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '250,310p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '370,455p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '740,812p'" in /mnt/c/Wildbook-clean2
 succeeded in 224ms:
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
   431	
   432	    public int hashCode() {
   433	        if (id == null) return Util.generateUUID().hashCode();
   434	        return id.hashCode();
   435	    }
   436	}

 succeeded in 228ms:
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
   731	        if (getParameters() == null) return null;
   732	        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
   733	        JSONObject rtn = new JSONObject();
   734	        // vector/embed flavor
   735	        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
   736	            null) != null) {
   737	            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
   738	                "model_id", null);
   739	            if (modelId == null) {
   740	                rtn.put("description", "Vector embedding match");
   741	            } else {
   742	                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
   743	                rtn.put("modelId", modelId);
   744	            }
   745	            return rtn;
   746	        }
   747	        // it seems both of these are in most logs (and are identical), but being safe in case there are
   748	        // examples in the wild with only one
   749	        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   750	            "query_config_dict");
   751	        if (conf == null)
   752	            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   753	                "queryConfigDict");
   754	        // we set HotSpotter if pipeline_root is not set here
   755	        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
   756	        rtn.put("description",
   757	            getParameters().getJSONObject("ibeis.identification").optString("description",
   758	            "unknown algorithm/method"));
   759	        return rtn;
   760	    }
   761	
   762	    // convenience
   763	    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
   764	        return myShepherd.getMatchResults(this);
   765	    }
   766	
   767	    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
   768	        List<MatchResult> all = myShepherd.getMatchResults(this);
   769	
   770	        if (Util.collectionIsEmptyOrNull(all)) return null;

 succeeded in 225ms:
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

 succeeded in 251ms:
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

exec
/bin/bash -lc "rg -n \"class Annotation|class Feature|class MediaAsset|class Embedding|matchAgainst|acmId|List<Feature>|annotations|embeddings|METHOD|METHODVERSION|ANNOTATION_ID\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/media/MediaAsset.java src/main/resources -g '*.{java,xml,jdo}'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"sendMediaAssetsForceId|mediaAssetToUri|iaImageIds|iaAnnotationIdsStrict|parseAnnotationIdsArrayStrict|fromFancyUUID|validMediaAsset|safeInvalidate|QueryCacheFactory|getQueryCache|invalidateByName\" src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"STATUS_COMPLETE|STATUS_COMPLETE_MLSERVICE|STATUS_PENDING_SPECIES|STATUS_DROPPED_STALE|STATUS_PROCESSING_MLSERVICE|STATUS_INITIATED|STATUS_PENDING|STATUS_ERROR|STATUS_PROCESSING\" src/main/java/org/ecocean/identity/IBEISIA.java src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class QueryCacheFactory|class QueryCache|invalidateByName|getQueryCache\" src/main/java/org/ecocean/cache -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 306ms:
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {

 succeeded in 629ms:
src/main/java/org/ecocean/Embedding.java:28:public class Embedding implements java.io.Serializable {
src/main/java/org/ecocean/Embedding.java:37:    // for trying to query vectors of annots without embeddings
src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
src/main/java/org/ecocean/Embedding.java:248:                /// maybe set on embData when we have *no embeddings* but did not have exception??
src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
src/main/java/org/ecocean/Embedding.java:308:                        " due to no suitable embeddings for " + iaConfig);
src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:361:                    " due to no suitable embeddings for " + iaConfig);
src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Annotation.java:29:public class Annotation extends Base implements java.io.Serializable {
src/main/java/org/ecocean/Annotation.java:51:    private ArrayList<Feature> features;
src/main/java/org/ecocean/Annotation.java:52:    private Set<Embedding> embeddings;
src/main/java/org/ecocean/Annotation.java:53:    protected String acmId;
src/main/java/org/ecocean/Annotation.java:62:    // manually-drawn annotations.
src/main/java/org/ecocean/Annotation.java:68:    // about ml-service-created annotations so HotSpotter remains available.
src/main/java/org/ecocean/Annotation.java:72:    //           acmId IS NOT NULL ("already registered via the historical
src/main/java/org/ecocean/Annotation.java:79:    // new ml-service annotations.
src/main/java/org/ecocean/Annotation.java:88:    private boolean matchAgainst = false;
src/main/java/org/ecocean/Annotation.java:129:        this.features = new ArrayList<Feature>();
src/main/java/org/ecocean/Annotation.java:133:    public Annotation(String species, ArrayList<Feature> f) {
src/main/java/org/ecocean/Annotation.java:143:        this.features = new ArrayList<Feature>();
src/main/java/org/ecocean/Annotation.java:148:    public Annotation(String species, ArrayList<Feature> f, String iaClass) {
src/main/java/org/ecocean/Annotation.java:178:        map.put("acmId", keywordType);
src/main/java/org/ecocean/Annotation.java:189:        // embeddings have some metadata (algorithm etc)
src/main/java/org/ecocean/Annotation.java:204:        map.put("embeddings", embMap);
src/main/java/org/ecocean/Annotation.java:213:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/Annotation.java:216:        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
src/main/java/org/ecocean/Annotation.java:244:        jgen.writeArrayFieldStart("embeddings");
src/main/java/org/ecocean/Annotation.java:245:        if (this.embeddings != null)
src/main/java/org/ecocean/Annotation.java:246:            for (Embedding emb : this.embeddings) {
src/main/java/org/ecocean/Annotation.java:268:    // TODO should this also be limited by matchAgainst and acmId?
src/main/java/org/ecocean/Annotation.java:313:        this.acmId = id;
src/main/java/org/ecocean/Annotation.java:318:        return this.acmId;
src/main/java/org/ecocean/Annotation.java:322:        return (this.acmId != null);
src/main/java/org/ecocean/Annotation.java:350:    public ArrayList<Feature> getFeatures() {
src/main/java/org/ecocean/Annotation.java:359:    public void setFeatures(ArrayList<Feature> f) {
src/main/java/org/ecocean/Annotation.java:365:        if (features == null) features = new ArrayList<Feature>();
src/main/java/org/ecocean/Annotation.java:573:        if (acmId == null)
src/main/java/org/ecocean/Annotation.java:574:            throw new IOException(this + " does not have acmId set; cannot get viewpoint from IA");
src/main/java/org/ecocean/Annotation.java:576:            JSONObject resp = IBEISIA.iaViewpointFromAnnotUUID(acmId, context);
src/main/java/org/ecocean/Annotation.java:603:     * needs this column to be populated on ml-service-created annotations.
src/main/java/org/ecocean/Annotation.java:615:        ArrayList<Feature> fts = getFeatures();
src/main/java/org/ecocean/Annotation.java:636:        ArrayList<Feature> fts = getFeatures();
src/main/java/org/ecocean/Annotation.java:751:        return matchAgainst;
src/main/java/org/ecocean/Annotation.java:755:        matchAgainst = b;
src/main/java/org/ecocean/Annotation.java:956:        // returned every annotation with matchAgainst=T and an acmId
src/main/java/org/ecocean/Annotation.java:1001:        // matchAgainst true
src/main/java/org/ecocean/Annotation.java:1003:        arg.put("matchAgainst", true);
src/main/java/org/ecocean/Annotation.java:1008:        // must have acmId
src/main/java/org/ecocean/Annotation.java:1010:        arg.put("field", "acmId");
src/main/java/org/ecocean/Annotation.java:1125:        // this exludes the very noisy embeddings from opensearch results since we dont need it
src/main/java/org/ecocean/Annotation.java:1126:        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
src/main/java/org/ecocean/Annotation.java:1179:    // return null when this annot has no embeddings to match, sorry!
src/main/java/org/ecocean/Annotation.java:1198:            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
src/main/java/org/ecocean/Annotation.java:1200:        JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
src/main/java/org/ecocean/Annotation.java:1201:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
src/main/java/org/ecocean/Annotation.java:1203:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
src/main/java/org/ecocean/Annotation.java:1220:    // finds annotations based on embedding vector matches
src/main/java/org/ecocean/Annotation.java:1687:    // note: this will throw an IAException if the txStr does not support embeddings
src/main/java/org/ecocean/Annotation.java:1837:        for (Embedding emb : embeddings) {
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
src/main/java/org/ecocean/Annotation.java:2004:        return embeddings;
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
src/main/java/org/ecocean/Annotation.java:2049:        Iterator it = embeddings.iterator();
src/main/java/org/ecocean/Annotation.java:2064:        Iterator it = embeddings.iterator();
src/main/java/org/ecocean/Annotation.java:2074:        if (embeddings == null) return;
src/main/java/org/ecocean/Annotation.java:2075:        for (Embedding emb : this.embeddings) {
src/main/java/org/ecocean/media/Feature.java:16:public class Feature implements java.io.Serializable {
src/main/java/org/ecocean/media/MediaAsset.java:48:public class MediaAsset extends Base implements java.io.Serializable {
src/main/java/org/ecocean/media/MediaAsset.java:73:    protected ArrayList<Feature> features;
src/main/java/org/ecocean/media/MediaAsset.java:92:    private String acmId;
src/main/java/org/ecocean/media/MediaAsset.java:117:        if (this.acmId == null) this.acmId = this.getUUID();
src/main/java/org/ecocean/media/MediaAsset.java:133:        this.acmId = id;
src/main/java/org/ecocean/media/MediaAsset.java:137:        return this.acmId;
src/main/java/org/ecocean/media/MediaAsset.java:141:        return (null != this.acmId);
src/main/java/org/ecocean/media/MediaAsset.java:435:    public ArrayList<Feature> getFeatures() {
src/main/java/org/ecocean/media/MediaAsset.java:439:    public void setFeatures(ArrayList<Feature> f) {
src/main/java/org/ecocean/media/MediaAsset.java:444:        if (features == null) features = new ArrayList<Feature>();
src/main/java/org/ecocean/media/MediaAsset.java:665:    class AnnotationPositionalComparator implements Comparator<Annotation> {
src/main/java/org/ecocean/media/MediaAsset.java:861:        jobj.put("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:874:        ArrayList<Feature> fts = getFeatures();
src/main/java/org/ecocean/media/MediaAsset.java:1712:        map.put("acmId", keywordType);
src/main/java/org/ecocean/media/MediaAsset.java:1717:        map.put("annotations", new org.json.JSONObject("{\"type\": \"nested\"}"));
src/main/java/org/ecocean/media/MediaAsset.java:1726:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:1741:        jgen.writeArrayFieldStart("annotations");
src/main/java/org/ecocean/media/MediaAsset.java:1745:            jgen.writeStringField("acmId", ann.getAcmId());
src/main/resources/org/ecocean/media/package.jdo:10:		<field name="acmId">
src/main/resources/org/ecocean/media/package.jdo:42:		<field name="annotations" mapped-by="mediaAsset">
src/main/resources/org/ecocean/package.jdo:422:      <field name="annotations" persistence-modifier="persistent" default-fetch-group="true">
src/main/resources/org/ecocean/package.jdo:547:	<field name="acmId">
src/main/resources/org/ecocean/package.jdo:580:		<field name="matchAgainst">
src/main/resources/org/ecocean/package.jdo:617:	<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
src/main/resources/org/ecocean/package.jdo:1048:			<column name="ANNOTATION_ID"/>
src/main/resources/org/ecocean/package.jdo:1049:			<column name="METHOD"/>
src/main/resources/org/ecocean/package.jdo:1050:			<column name="METHODVERSION"/>
src/main/resources/org/ecocean/package.jdo:1057:			<column name="ANNOTATION_ID" />
src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />

 succeeded in 1071ms:
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:9:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:44:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:68:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:47:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4390:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4426:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4467:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
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
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:495:                return parseAnnotationIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:520:        return parseAnnotationIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530:    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:537:            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:550:            if (jo != null) ids.add(fromFancyUUID(jo));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:596:        String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:663:            String sentId = fromFancyUUID(sentIds.get(i));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:668:            String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:684:                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:703:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:760:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:792:    public static String fromFancyUUID(JSONObject u) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:    public static boolean validMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/identity/IBEISIA.java:148:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/identity/IBEISIA.java:800:                    String uuid = fromFancyUUID(list.getJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:813:                        String acmId = fromFancyUUID(list.getJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:834:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/identity/IBEISIA.java:835:        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
src/main/java/org/ecocean/identity/IBEISIA.java:1086:    public static String fromFancyUUID(JSONObject u) {
src/main/java/org/ecocean/identity/IBEISIA.java:1271:        ann.setAcmId(fromFancyUUID(iaResult.optJSONObject("uuid")));
src/main/java/org/ecocean/identity/IBEISIA.java:1594:                    String iuuid = fromFancyUUID(jiuuid);
src/main/java/org/ecocean/identity/IBEISIA.java:1833:                String acmId = fromFancyUUID(rlist.getJSONObject(i).getJSONObject("annot_uuid_1")); // gets not opts here... so ungraceful fail possible
src/main/java/org/ecocean/identity/IBEISIA.java:1946:        String ms = getIdentificationMatchingState(fromFancyUUID(rlist.getJSONObject(
src/main/java/org/ecocean/identity/IBEISIA.java:1948:            fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")), context);
src/main/java/org/ecocean/identity/IBEISIA.java:2122:            String imageUUID = fromFancyUUID(rtn.getJSONArray("response").getJSONObject(0));
src/main/java/org/ecocean/identity/IBEISIA.java:2282:            origAnnUUIDs.add(fromFancyUUID(auuids.optJSONObject(j)));
src/main/java/org/ecocean/identity/IBEISIA.java:2291:            String n = fromFancyUUID(nameUUIDs.optJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:2320:                String u = fromFancyUUID(auuidSet.optJSONObject(j));
src/main/java/org/ecocean/identity/IBEISIA.java:2841:            map.put(fromFancyUUID(uuids.optJSONObject(i)), names.optString(i, null));
src/main/java/org/ecocean/identity/IBEISIA.java:2855:        return fromFancyUUID(rtn.getJSONArray("response").optJSONObject(0));
src/main/java/org/ecocean/identity/IBEISIA.java:3242:            qid = fromFancyUUID(rlist.getJSONObject(0).optJSONObject("annot_uuid_key"));
src/main/java/org/ecocean/identity/IBEISIA.java:3247:            if (!qid.equals(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_key"))))
src/main/java/org/ecocean/identity/IBEISIA.java:3250:            s.put(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")));
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
src/main/java/org/ecocean/ia/MatchResult.java:131:        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
src/main/java/org/ecocean/ia/MatchResult.java:171:            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
src/main/java/org/ecocean/servlet/importer/ImportIA.java:121:            String occID = IBEISIA.fromFancyUUID(fancyID);
src/main/java/org/ecocean/servlet/importer/ImportIA.java:126:            List<String> annotUUIDs = fromFancyUUIDList(annotFancyUUIDs);
src/main/java/org/ecocean/servlet/importer/ImportIA.java:369:    private List<String> fromFancyUUIDList(JSONArray fancyUUIDs) {
src/main/java/org/ecocean/servlet/importer/ImportIA.java:373:            ids.add(IBEISIA.fromFancyUUID(fancyUUIDs.getJSONObject(j)));
src/main/java/org/ecocean/servlet/importer/ImportTask.java:659:                    invalidMediaAssets.add(asset);

 succeeded in 1094ms:
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
src/main/java/org/ecocean/Encounter.java:5493:                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/Encounter.java:5534:                    ma.setDetectionStatus(hasConfig ? IBEISIA.STATUS_INITIATED : IBEISIA.STATUS_COMPLETE);
src/main/java/org/ecocean/cache/CachedQuery.java:28:    public static final String STATUS_PENDING = "pending"; // pending review (needs action by user)
src/main/java/org/ecocean/ia/IAUtils.java:20:                ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
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
src/main/java/org/ecocean/ia/MlServiceProcessor.java:171:                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:255:            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:278:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:333:                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:365:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:401:            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:625:        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:626:        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:663:        task.setStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:676:        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
src/main/java/org/ecocean/ia/MLService.java:206:            if (IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(ann.getIdentificationStatus()))
src/main/java/org/ecocean/ia/MLService.java:325:        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/Resolver.java:17:    public static String STATUS_PENDING = "pending"; // needs review
src/main/java/org/ecocean/Resolver.java:20:    public static String STATUS_COMPLETE = "complete";
src/main/java/org/ecocean/Resolver.java:21:    public static String STATUS_ERROR = "error";
src/main/java/org/ecocean/Resolver.java:231:            res.setStatus(STATUS_ERROR);
src/main/java/org/ecocean/Resolver.java:238:            res.setStatus(STATUS_PENDING);
src/main/java/org/ecocean/Resolver.java:252:        res.setStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/StartupWildbook.java:627:            if (!org.ecocean.identity.IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(
src/main/java/org/ecocean/StartupWildbook.java:643:                ma.setDetectionStatus(org.ecocean.identity.IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/servlet/IAGateway.java:216:                    ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
src/main/java/org/ecocean/servlet/IAGateway.java:260:                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/servlet/IAGateway.java:458:            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING);
src/main/java/org/ecocean/servlet/IAGateway.java:474:                ann.setIdentificationStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/servlet/EncounterForm.java:891:                            ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:111:	        crMa.setDetectionStatus(IBEISIA.STATUS_COMPLETE);

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '400,440p;540,625p;1038,1080p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/media/package.jdo | sed -n '1,55p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '90,150p;178,246p;470,525p;530,555p;695,770p;792,835p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCacheFactory.java src/main/java/org/ecocean/cache/QueryCache.java | sed -n '1,90p'" in /mnt/c/Wildbook-clean2
 succeeded in 225ms:
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

 succeeded in 228ms:
   400	        <collection element-type="SuperSpot"/>
   401	        <join/>
   402	      </field>
   403	      <field name="leftReferenceSpots" persistence-modifier="persistent">
   404	        <collection element-type="SuperSpot"/>
   405	        <join/>
   406	      </field>
   407	      <field name="rightReferenceSpots" persistence-modifier="persistent">
   408	        <collection element-type="SuperSpot"/>
   409	        <join/>
   410	      </field>
   411	
   412	      <field name="tissueSamples" persistence-modifier="persistent" default-fetch-group="true">
   413	        <collection element-type="org.ecocean.genetics.TissueSample" dependent-element="false"/>
   414	        <join/>
   415	      </field>
   416	
   417	      <field name="images" persistence-modifier="persistent" default-fetch-group="true">
   418	        <collection element-type="org.ecocean.SinglePhotoVideo" dependent-element="true"/>
   419	        <join/>
   420	      </field>
   421	
   422	      <field name="annotations" persistence-modifier="persistent" default-fetch-group="true">
   423	        <collection element-type="org.ecocean.Annotation" dependent-element="false"/>
   424	        <join/>
   425	      </field>
   426	
   427	      <field name="measurements" persistence-modifier="persistent" default-fetch-group="true">
   428	        <collection element-type="org.ecocean.Measurement" dependent-element="false"/>
   429	        <join/>
   430	      </field>
   431	
   432	      <field name="metalTags" persistence-modifier="persistent" default-fetch-group="true">
   433	        <collection element-type="org.ecocean.tag.MetalTag" dependent-element="false"/>
   434	        <join/>
   435	      </field>
   436	
   437	      <field name="observations" persistence-modifier="persistent" default-fetch-group="true">
   438	        <collection element-type="org.ecocean.Observation" dependent-element="false" />
   439	        <join/>
   440	      </field>
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

 succeeded in 226ms:
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
    20	package org.ecocean.cache;
    21	
    22	import java.io.IOException;
    23	import java.util.HashMap;
    24	import java.util.List;
    25	import java.util.Map;
    26	
    27	import org.ecocean.shepherd.core.Shepherd;
    28	import org.json.JSONObject;
    29	
    30	// A singleton responsible for storing, retrieving, and eventually executing queries that are desired for caching. It will have all necessary getters
    31	// and setters for dealing with the cache.
    32	public class QueryCache {
    33	    private Map<String, CachedQuery> cachedQueries;
    34	    private String context = null;
    35	
    36	    public QueryCache() {}
    37	    public QueryCache(String context) {
    38	        this.context = context;
    39	    }
    40	
    41	    public CachedQuery getQueryByName(String name) {
    42	        if (cachedQueries == null) loadQueries();
    43	        return cachedQueries.get(name);
    44	    }
    45	
    46	    public Map<String, CachedQuery> cachedQueries() { return cachedQueries; }
    47	
    48	    public void loadQueries() {
    49	        if (context == null)
    50	            throw new RuntimeException("QueryCache.loadQueries() called with context null");
    51	        cachedQueries = new HashMap<String, CachedQuery>();
    52	        Shepherd myShepherd = new Shepherd(context);
    53	        myShepherd.beginDBTransaction();
    54	
    55	        List<StoredQuery> queries = myShepherd.getAllStoredQueries();
    56	        int numQueries = queries.size();
    57	        for (int i = 0; i < numQueries; i++) {
    58	            StoredQuery sq = queries.get(i);
    59	            cachedQueries.put(sq.getName(), sq.getCachedQueryCopy());
    60	        }
    61	        myShepherd.rollbackDBTransaction();
    62	        myShepherd.closeDBTransaction();
    63	    }
    64	
    65	    public void invalidateByName(String name)
    66	    throws IOException {
    67	        if (cachedQueries.containsKey(name)) {
    68	            cachedQueries.get(name).invalidate();
    69	        }
    70	    }
    71	
    72	    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
    73	        Shepherd myShepherd) {
    74	        // create CachedQuery object
    75	        CachedQuery cq = new CachedQuery(name, jsonobj, persistAsStoredQuery, myShepherd);
    76	
    77	        // put on HashMap
    78	        cachedQueries.put(name, cq);
    79	    }
    80	
    81	    public void addCachedQuery(CachedQuery cq) {
    82	        cachedQueries.put(cq.getName(), cq);
    83	    }
    84	}

 succeeded in 231ms:
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
   141	                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
   142	            }
   143	            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
   144	                if (acmList.size() > 0) {
   145	                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
   146	                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
   147	                        " batches)");
   148	                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   149	                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
   150	                    List<String> acmIds = acmIdsFromResponse(rtn);
   178	    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
   179	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   180	        InvalidKeyException {
   181	        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
   182	
   183	        if (u == null)
   184	            throw new MalformedURLException(
   185	                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
   186	        URL url = new URL(u);
   187	        int batchSize = 30;
   188	        int numBatches = Math.round(mas.size() / batchSize + 1);
   189	
   190	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   191	        List<String> iaImageIds = new ArrayList<String>();
   192	        if (checkFirst) iaImageIds = iaImageIds();
   193	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   194	        map.put("image_uri_list", new ArrayList<JSONObject>());
   195	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   196	        map.put("image_unixtime_list", new ArrayList<Integer>());
   197	        map.put("image_gps_lat_list", new ArrayList<Double>());
   198	        map.put("image_gps_lon_list", new ArrayList<Double>());
   199	        int batchCt = 1;
   200	        JSONObject allRtn = new JSONObject();
   201	        allRtn.put("_batchSize", batchSize);
   202	        allRtn.put("_totalSize", mas.size());
   203	        JSONArray bres = new JSONArray();
   204	        for (int i = 0; i < mas.size(); i++) {
   205	            MediaAsset ma = mas.get(i);
   206	            if (iaImageIds.contains(ma.getAcmId())) continue;
   207	            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
   208	                IA.log(
   209	                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
   210	                    + ma.getId());
   211	                continue;
   212	            }
   213	            if (!validMediaAsset(ma)) {
   214	                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
   215	                continue;
   216	            }
   217	            map.get("image_uri_list").add(mediaAssetToUri(ma));
   218	            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
   219	            map.get("image_gps_lat_list").add(ma.getLatitude());
   220	            map.get("image_gps_lon_list").add(ma.getLongitude());
   221	            DateTime t = ma.getDateTime();
   222	            if (t == null) {
   223	                map.get("image_unixtime_list").add(null);
   224	            } else {
   225	                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
   226	            }
   227	            int sendSize = map.get("image_uri_list").size();
   228	            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
   229	                if (sendSize > 0) {
   230	                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
   231	                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
   232	                        " batches)");
   233	                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   234	                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
   235	/*
   236	                    if (acmIds == null) {
   237	                        IA.log(
   238	                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
   239	 + rtn);
   240	                    } else {
   241	                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
   242	                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
   243	                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
   244	                    }
   245	 */
   246	                    bres.put(rtn);
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
   521	    }
   522	
   523	    /**
   524	     * Strict element parser: throws IOException if any element is not a
   525	     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
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
   711	                jids = apiGetJSONArray("/api/annot/json/", context);
   712	                if (jids != null) {
   713	                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
   714	                        new org.datanucleus.api.rest.orgjson.JSONObject();
   715	                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
   716	                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   717	                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   718	                    qc.addCachedQuery(cq);
   719	                }
   720	            }
   721	        } catch (Exception ex) {
   722	            ex.printStackTrace();
   723	            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
   724	                ex.toString());
   725	        }
   726	        if (jids != null) {
   727	            try {
   728	                for (int i = 0; i < jids.length(); i++) {
   729	                    if (jids.optJSONObject(i) != null)
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
   816	            }
   817	        }
   818	        return null;
   819	    }
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/Feature.java | sed -n '1,160p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|TASK_OBJECTMEDIAASSETS|TASK_OBJECTANNOTATIONS|TASK_CHILDREN|EMBEDDING\" . -g '*.{sql,md,java,xml,jdo}'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '29,100p;300,370p;740,760p;2000,2030p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '48,100p;428,455p;650,690p'" in /mnt/c/Wildbook-clean2
 succeeded in 209ms:
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

 succeeded in 257ms:
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
  2000	        return null;
  2001	    }
  2002	
  2003	    public Set<Embedding> getEmbeddings() {
  2004	        return embeddings;
  2005	    }
  2006	
  2007	    public int numberEmbeddings() {
  2008	        return Util.collectionSize(embeddings);
  2009	    }
  2010	
  2011	    public Map<String, Integer> getEmbeddingCounts() {
  2012	        Map<String, Integer> cts = new HashMap<String, Integer>();
  2013	
  2014	        if (Util.collectionIsEmptyOrNull(embeddings)) return cts;
  2015	        for (Embedding emb : embeddings) {
  2016	            String md = emb.getMethodDescription();
  2017	            if (!cts.containsKey(md)) cts.put(md, 0);
  2018	            cts.put(md, cts.get(md) + 1);
  2019	        }
  2020	        return cts;
  2021	    }
  2022	
  2023	    public Set<Embedding> addEmbedding(Embedding emb) {
  2024	        if (embeddings == null) embeddings = new HashSet<Embedding>();
  2025	        if (emb == null) return embeddings;
  2026	        embeddings.add(emb);
  2027	        if (!this.equals(emb.getAnnotation())) emb.setAnnotation(this);
  2028	        return embeddings;
  2029	    }
  2030	

 succeeded in 259ms:
    48	public class MediaAsset extends Base implements java.io.Serializable {
    49	    static final long serialVersionUID = 8844223450447974780L;
    50	    @Override public String opensearchIndexName() { return "media_asset"; }
    51	    protected int id = MediaAssetFactory.NOT_SAVED;
    52	
    53	    protected String uuid = null;
    54	
    55	    protected AssetStore store;
    56	    protected String parametersAsString;
    57	    protected JSONObject parameters;
    58	
    59	    protected Occurrence occurrence;
    60	
    61	    protected Integer parentId;
    62	
    63	    protected long revision;
    64	
    65	    protected AccessControl accessControl = null;
    66	
    67	    protected JSONObject derivationMethod = null;
    68	
    69	    protected MediaAssetMetadata metadata = null;
    70	
    71	    protected ArrayList<String> labels;
    72	
    73	    protected ArrayList<Feature> features;
    74	
    75	    protected ArrayList<Keyword> keywords;
    76	
    77	    protected String hashCode;
    78	    protected String contentHash; // see Util.fileContentHash()
    79	
    80	    protected String detectionStatus;
    81	    protected String identificationStatus;
    82	
    83	    protected Double userLatitude;
    84	    protected Double userLongitude;
    85	
    86	    protected DateTime userDateTime;
    87	
    88	    // Variables used in the Survey, SurveyTrack, Path, Location model
    89	
    90	    private String correspondingSurveyTrackID;
    91	    private String correspondingSurveyID;
    92	    private String acmId;
    93	
    94	    private Boolean validImageForIA;
    95	
    96	    /**
    97	     * To be called by AssetStore factory method.
    98	     */
    99	
   100	    public MediaAsset() {
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
   676	        for (Annotation ann : getAnnotations()) {
   677	            if (ann.equals(match)) continue;
   678	            if (ann.equalsShape(match)) {
   679	                found = ann;
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

 succeeded in 1050ms:
./docs/plans/2026-05-18-empty-match-prospects-design.md:424:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-design.md:425:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-design.md:427:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-design.md:435:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-design.md:443:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-design.md:901:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./archive/sql/ml_service_embedding_audit.sql:1:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./archive/sql/ml_service_embedding_audit.sql:3:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./archive/sql/ml_service_embedding_audit.sql:12:FROM "EMBEDDING"
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:82:- `TASK_OBJECTANNOTATIONS` join — clean up via the join row.
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2955:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2956:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4392:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4896:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:82:- `TASK_OBJECTANNOTATIONS` join — clean up via the join row.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:383:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:384:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:386:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
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
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3814:  1064				<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3819:  1069				<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3824:  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4003:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5498:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5574:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5604:   211	        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5614:   221	            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8120:  2793	            "JOIN \"TASK_OBJECTANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_OID\" = \"TASK\".\"ID\") "
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8122:  2795	            "JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_EID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") "
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9062:   208	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9063:   209	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9065:   211	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9073:   219	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9081:   227	  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9302:   428	  set up an annotation with no EMBEDDING row, prove it's filtered
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9356:   477	   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
./docs/plans/2026-05-09-ml-service-migration-v2.md:333:    SELECT "ANNOTATION_ID" FROM "EMBEDDING"
./archive/sql/drop_task_constraint.sql:1:alter table "TASK_OBJECTANNOTATIONS" drop constraint "TASK_OBJECTANNOTATIONS_FK2", add constraint "TASK_OBJECTANNOTATIONS_FK2" FOREIGN KEY ("ID_EID") REFERENCES "ANNOTATION"("ID") ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED ;
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:82:- `TASK_OBJECTANNOTATIONS` join — clean up via the join row.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:520:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:521:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:523:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:531:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:539:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:932:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:999:   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1000:   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4508:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6522:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6539:			<index name="EMBEDDING_CREATED_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6544:			<index name="EMBEDDING_METHOD_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6549:			<index name="EMBEDDING_METHODVERSION_IDX" />
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6557:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6559:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6568:FROM "EMBEDDING"
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
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:82:- `TASK_OBJECTANNOTATIONS` join — clean up via the join row.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:426:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:427:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:429:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:437:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:445:  (`predictModelId` set but `EMBEDDING` row never created).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:812:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:859:   My draft uses `EMBEDDING.METHODVERSION = ?` strictly — Codex
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1007:docs/plans/2026-05-18-empty-match-prospects-design.md:637:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2588:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5541:/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|name=\\\"features\\\"|mapped-by=\\\"annotation\\\"|mapped-by=\\\"asset\\\"|class name=\\\"Annotation\\\"|class name=\\\"MediaAsset\\\"|class name=\\\"Embedding\\\"|ANNOTATION_ID|dependent-element\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/media/package.jdo src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5642:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6125:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6127:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6242:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6260:/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|MEDIAASSET_ID|ASSET_ID|FEATURES|ANNOTATION_ID" archive src/test src/main/java | head -200' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6268:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6270:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6271:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6272:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6273:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6377:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6378:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9059:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
./src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
./src/main/java/org/ecocean/Embedding.java:211:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
./src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
./src/main/java/org/ecocean/Embedding.java:261:        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
./src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
./src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
./src/main/java/org/ecocean/shepherd/core/Shepherd.java:2793:            "JOIN \"TASK_OBJECTANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_OID\" = \"TASK\".\"ID\") "
./src/main/java/org/ecocean/shepherd/core/Shepherd.java:2795:            "JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_EID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") "
./src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
./src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
./src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
./src/main/resources/org/ecocean/package.jdo:1064:			<index name="EMBEDDING_CREATED_IDX" />
./src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
./src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '760,792p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '320,360p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getChildren\\(|setParent\\(|getParent\\(|children\" src/main/java/org/ecocean/ia/Task.java src/main/resources/org/ecocean/ia/package.jdo src/main/resources/org/ecocean/package.jdo src/main/resources -g '*.{java,jdo,xml}'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md | sed -n '990,1010p;7176,7185p'" in /mnt/c/Wildbook-clean2
 succeeded in 230ms:
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

 succeeded in 234ms:
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

 succeeded in 247ms:
   990	
   991	Remaining open questions (likely minor at this point):
   992	
   993	1. **Naming the safe-invalidate helper.** Inline in `WildbookIAM`
   994	   or extract to `QueryCacheFactory.safeInvalidate(context, name)`?
   995	   The latter is reusable for any future caller; the former
   996	   keeps WBIA changes local. My instinct: extract — null-safe
   997	   invalidation is a generally-useful primitive.
   998	2. **Direct SQL placement.** The eligibility query joins
   999	   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
  1000	   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
  1001	   is fine. Where does it live? Options:
  1002	   (a) Static method on a new `MatchEligibilityQuery` utility class.
  1003	   (b) Static method on `Annotation` next to `getMatchingSetQuery`.
  1004	   (c) Private helper inside `MatchVisibilityGateImpl`.
  1005	   My instinct: (c) — keep it co-located with its sole caller.
  1006	3. **Failure mode for `topTask == null` after child resolution.**
  1007	   The child Task's parent should always be a topTask in v2, but
  1008	   if for some reason it's null (corrupted state, race during
  1009	   topTask deletion), the gate falls through to waiting only on
  1010	   `callerAnnotationIds`. Confirm this degrades cleanly enough.
  7176	    public static Map<String, Integer> getAllMediaAssetCounts(Shepherd myShepherd) {
  7177	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
  7178	            "SELECT ie.\"ID_OID\", count(distinct mf.\"ID_OID\") " +
  7179	            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"ENCOUNTER_ANNOTATIONS\" ea " +
  7180	            "ON ie.\"CATALOGNUMBER_EID\" = ea.\"CATALOGNUMBER_OID\" " +
  7181	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
  7182	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
  7183	            "GROUP BY ie.\"ID_OID\"");
  7184	
  7185	        return parseSqlCountResults(query);

 succeeded in 365ms:
src/main/resources/org/ecocean/package.jdo:990:		<field name="children" mapped-by="parent">
src/main/resources/org/ecocean/package.jdo:1030:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/java/org/ecocean/ia/Task.java:37:    private List<Task> children = null;
src/main/java/org/ecocean/ia/Task.java:57:        this.setParent(p);
src/main/java/org/ecocean/ia/Task.java:247:    public List<Task> getChildren() {
src/main/java/org/ecocean/ia/Task.java:248:        return children;
src/main/java/org/ecocean/ia/Task.java:253:            children = null;
src/main/java/org/ecocean/ia/Task.java:256:        children = new ArrayList<Task>();
src/main/java/org/ecocean/ia/Task.java:263:        if (children == null) children = new ArrayList<Task>();
src/main/java/org/ecocean/ia/Task.java:264:        if (kid == null) return children;
src/main/java/org/ecocean/ia/Task.java:265:        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
src/main/java/org/ecocean/ia/Task.java:266:        if (!children.contains(kid)) children.add(kid);
src/main/java/org/ecocean/ia/Task.java:267:        return children;
src/main/java/org/ecocean/ia/Task.java:270:    public void setParent(Task t) {
src/main/java/org/ecocean/ia/Task.java:275:    public Task getParent() {
src/main/java/org/ecocean/ia/Task.java:285:        return (children == null) ? 0 : children.size();
src/main/java/org/ecocean/ia/Task.java:300:        for (Task kid : children) {
src/main/java/org/ecocean/ia/Task.java:314:        for (Task kid : children) {
src/main/java/org/ecocean/ia/Task.java:324:        for (Task kid : children) {
src/main/java/org/ecocean/ia/Task.java:336:            for (Task kid : this.children) {
src/main/java/org/ecocean/ia/Task.java:347:            for (Task kid : this.children) {
src/main/java/org/ecocean/ia/Task.java:482:            for (Task kid : this.children) {
src/main/java/org/ecocean/ia/Task.java:485:            j.put("children", jc);
src/main/java/org/ecocean/ia/Task.java:588:            List<Task> children = getChildren();
src/main/java/org/ecocean/ia/Task.java:590:            for (Task t : children) {
src/main/java/org/ecocean/ia/Task.java:649:            // accumulate status across children
src/main/java/org/ecocean/ia/Task.java:652:            for (Task childTask : this.getChildren()) {
src/main/java/org/ecocean/ia/Task.java:654:                    for (Task childTask2 : childTask.getChildren()) {
src/main/java/org/ecocean/ia/Task.java:830:            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
src/main/java/org/ecocean/ia/Task.java:832: * note: we try getting it regardless of children ("just in case"); but only try to generate if none
src/main/java/org/ecocean/ia/Task.java:852:        // now we recurse thru children if applicable
src/main/java/org/ecocean/ia/Task.java:855:            for (Task child : children) {
src/main/java/org/ecocean/ia/Task.java:863:            rtn.put("children", charr);
src/main/java/org/ecocean/ia/Task.java:864:            // if we dont have children (leaf nodes) we get the status
src/main/resources/org/ecocean/ia/package.jdo:20:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/ia/package.jdo:20:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/package.jdo:990:		<field name="children" mapped-by="parent">
src/main/resources/org/ecocean/package.jdo:1030:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >

exec
/bin/bash -lc "rg -n \"storeNewTask|makePersistent\\(.*Task|setObjectMediaAssets|objectMediaAssets|objectAnnotations|parent\" src/main/java/org/ecocean/shepherd/core/Shepherd.java src/main/java/org/ecocean/ia/Task.java src/main/resources/org/ecocean/ia/package.jdo" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '1,45p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '1010,1038p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '220,290p;630,670p'" in /mnt/c/Wildbook-clean2
 succeeded in 335ms:
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

 succeeded in 330ms:
  1010		<class name="MultiValue" identity-type="application">
  1011			<field name="id" primary-key="true" value-strategy="identity" persistence-modifier="persistent" />
  1012			<field name="valuesAsString" persistence-modifier="persistent">
  1013	                	<column jdbc-type="LONGVARCHAR" name="values" />
  1014			</field>
  1015		</class>
  1016	
  1017	
  1018		<class name="Organization" identity-type="application">
  1019			<field name="id" primary-key="true">
  1020	        		<column length="36"/>
  1021			</field>
  1022			<field name="created">
  1023				<index name="ORGANIZATION_CREATED_idx" />
  1024			</field>
  1025	
  1026			<!-- this points up to the parent -->
  1027	      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.Organization" recursion-depth="3"/>
  1028	
  1029			<!-- this uses parent to find child orgs -->
  1030	            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
  1031	                	<collection element-type="org.ecocean.Organization" dependent-element="false" />
  1032	                	<join />
  1033	            	</field>
  1034	
  1035			<field name="members" persistent-modifier="persistent" mapped-by="organizations" default-fetch-group="true" >
  1036				<collection element-type="org.ecocean.User"/>
  1037				<join />
  1038			</field>

 succeeded in 375ms:
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

 succeeded in 426ms:
src/main/java/org/ecocean/ia/Task.java:34:    private List<MediaAsset> objectMediaAssets = null;
src/main/java/org/ecocean/ia/Task.java:35:    private List<Annotation> objectAnnotations = null;
src/main/java/org/ecocean/ia/Task.java:36:    private Task parent = null;
src/main/java/org/ecocean/ia/Task.java:152:        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
src/main/java/org/ecocean/ia/Task.java:156:        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
src/main/java/org/ecocean/ia/Task.java:176:    public void setObjectMediaAssets(List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/Task.java:177:        objectMediaAssets = mas;
src/main/java/org/ecocean/ia/Task.java:181:        objectAnnotations = anns;
src/main/java/org/ecocean/ia/Task.java:185:        return objectMediaAssets;
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
src/main/java/org/ecocean/ia/Task.java:271:        parent = t;
src/main/java/org/ecocean/ia/Task.java:276:        return parent;
src/main/java/org/ecocean/ia/Task.java:280:        if (parent == null) return null;
src/main/java/org/ecocean/ia/Task.java:281:        return parent.getId();
src/main/java/org/ecocean/ia/Task.java:307:        if (parent == null) return this;
src/main/java/org/ecocean/ia/Task.java:308:        return parent.getRootTask();
src/main/java/org/ecocean/ia/Task.java:466:        if ((objectMediaAssets != null) && (objectMediaAssets.size() > 0)) {
src/main/java/org/ecocean/ia/Task.java:468:            for (MediaAsset ma : this.objectMediaAssets) {
src/main/java/org/ecocean/ia/Task.java:473:        if ((objectAnnotations != null) && (objectAnnotations.size() > 0)) {
src/main/java/org/ecocean/ia/Task.java:475:            for (Annotation ann : this.objectAnnotations) {
src/main/java/org/ecocean/ia/Task.java:533:            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
src/main/java/org/ecocean/ia/Task.java:555:            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
src/main/java/org/ecocean/ia/Task.java:810:        rtn.put("parentTaskId", getParentId());
src/main/resources/org/ecocean/ia/package.jdo:16:		<!-- this points up to the parent -->
src/main/resources/org/ecocean/ia/package.jdo:17:      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" recursion-depth="3"/>
src/main/resources/org/ecocean/ia/package.jdo:19:		<!-- this uses parent to find child tasks -->
src/main/resources/org/ecocean/ia/package.jdo:20:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/ia/package.jdo:54:		<field name="parentId" persistence-modifier="persistent">
src/main/resources/org/ecocean/ia/package.jdo:62:		<field name="objectMediaAssets">
src/main/resources/org/ecocean/ia/package.jdo:67:		<field name="objectAnnotations" mapped-by="mediaAsset"???   >
src/main/resources/org/ecocean/ia/package.jdo:69:		<field name="objectAnnotations">
src/main/java/org/ecocean/shepherd/core/Shepherd.java:296:            pm.makePersistent(scanTask);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:308:    public boolean storeNewTask(Task task) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '140,215p;520,560p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '45,82p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/shepherd/core/Shepherd.java | sed -n '300,322p;2786,2800p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"matchConfig|ensureMatchConfig|activeConfigs|identification|ibeis.identification|model_id|embedding_model\" src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/ia/MLService.java src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 311ms:
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

 succeeded in 319ms:
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

 succeeded in 375ms:
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
  2786	        return theTask;
  2787	    }
  2788	
  2789	    public List<Task> getIdentificationTasksForUser(User user) {
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

 succeeded in 986ms:
src/main/java/org/ecocean/ia/MLService.java:56:    // there can be multiple configs (differing model_id)
src/main/java/org/ecocean/ia/MLService.java:175:                // now we are done we can fake a callback to initiate identification
src/main/java/org/ecocean/ia/MLService.java:326:        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
src/main/java/org/ecocean/ia/MLService.java:352:        mv[0] = conf.optString("model_id", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:107:        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:110:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:143:        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:145:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:169:            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:177:            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:179:                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:182:                    "_mlservice_conf missing predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:198:                configs.matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:227:            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:233:            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:259:                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:315:                String predictModelId = result.optString("predict_model_id",
src/main/java/org/ecocean/ia/MlServiceProcessor.java:316:                    det.mlConfig.optString("predict_model_id", null));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:359:                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:360:                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:393:            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:394:            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:402:            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:403:                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:419:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:439:        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:445:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:473:            // a vector config or matchConfig is null. Don't leave the match
src/main/java/org/ecocean/ia/MlServiceProcessor.java:475:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:480:                    (matchConfig == null ? "null" : matchConfig.toString()));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:540:    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:549:        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:550:        return new ConfigPair(mlConfig, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:555:        JSONObject matchConfig = null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:563:                    matchConfig = new JSONObject(entry.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:568:        if (matchConfig == null) matchConfig = new JSONObject();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:569:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:571:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:573:        return matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:600:    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:602:        JSONObject config = (matchConfig == null) ? new JSONObject()
src/main/java/org/ecocean/ia/MlServiceProcessor.java:603:            : new JSONObject(matchConfig.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:606:                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:607:                config.put("method", embeddingSource.optString("embedding_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:610:                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:611:                config.put("version", embeddingSource.optString("embedding_model_version"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:737:    private static boolean hasEmbeddingForMatchConfig(Annotation ann, JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:739:        if (matchConfig == null) return ann.numberEmbeddings() > 0;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:        String method = matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:        String version = matchConfig.optString("version", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:753:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:755:        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:757:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:765:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:769:            JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:773:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:781:            this.matchConfig = null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:794:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:800:            JSONObject matchConfig, double[] bbox, double theta) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:804:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:814:            this.matchConfig = null;
src/main/java/org/ecocean/Embedding.java:348:            // fall back to splitting model_id for legacy configs.
src/main/java/org/ecocean/IndividualQueryProcessor.java:989:        // filter for identificationRemarks------------------------------------------
src/main/java/org/ecocean/IndividualQueryProcessor.java:990:        if ((request.getParameter("identificationRemarksField") != null) &&
src/main/java/org/ecocean/IndividualQueryProcessor.java:991:            (!request.getParameter("identificationRemarksField").equals(""))) {
src/main/java/org/ecocean/IndividualQueryProcessor.java:992:            String idRemarks = request.getParameter("identificationRemarksField").trim();
src/main/java/org/ecocean/IndividualQueryProcessor.java:994:                "encounters.contains(enc98) && enc98.identificationRemarks.startsWith('" +
src/main/java/org/ecocean/IndividualQueryProcessor.java:999:            prettyPrint.append("identificationRemarks starts with \"" + idRemarks + "\".<br />");
src/main/java/org/ecocean/EncounterQueryProcessor.java:1357:            // filter for identificationRemarks------------------------------------------
src/main/java/org/ecocean/EncounterQueryProcessor.java:1358:            if ((request.getParameter("identificationRemarksField") != null) &&
src/main/java/org/ecocean/EncounterQueryProcessor.java:1359:                (!request.getParameter("identificationRemarksField").equals(""))) {
src/main/java/org/ecocean/EncounterQueryProcessor.java:1360:                String idRemarks = request.getParameter("identificationRemarksField").trim();
src/main/java/org/ecocean/EncounterQueryProcessor.java:1362:                    filter += "identificationRemarks.startsWith('" + idRemarks + "')";
src/main/java/org/ecocean/EncounterQueryProcessor.java:1363:                } else { filter += " && identificationRemarks.startsWith('" + idRemarks + "')"; }
src/main/java/org/ecocean/EncounterQueryProcessor.java:1364:                prettyPrint.append("identificationRemarks starts with \"" + idRemarks +
src/main/java/org/ecocean/EncounterQueryProcessor.java:1367:            // end identification remarks filter
src/main/java/org/ecocean/Encounter.java:96:    public String identificationRemarks = "";
src/main/java/org/ecocean/Encounter.java:1207:        if ((identificationRemarks == null) || (identificationRemarks.equals(""))) {
src/main/java/org/ecocean/Encounter.java:1210:        return identificationRemarks;
src/main/java/org/ecocean/Encounter.java:1214:        identificationRemarks = matchType;
src/main/java/org/ecocean/Encounter.java:1218:        identificationRemarks = matchType;
src/main/java/org/ecocean/Encounter.java:2151:        return identificationRemarks;
src/main/java/org/ecocean/Encounter.java:4832:        rtn.put("identificationRemarks", getIdentificationRemarks());
src/main/java/org/ecocean/Encounter.java:4905:                                    "annotations").getJSONObject(j).put("identificationStatus",
src/main/java/org/ecocean/Encounter.java:5182:        case "identificationRemarks":
src/main/java/org/ecocean/identity/IBEISIA.java:90:    private static HashMap<String, String> identificationUserActiveTaskId = new HashMap<String,
src/main/java/org/ecocean/identity/IBEISIA.java:201:                    "===> CRITICAL ERROR: Annotation did not have a useable class candidate to send to identification for iaClass. ");
src/main/java/org/ecocean/identity/IBEISIA.java:296:            noQueryAnn.put("error", "No query annotation was valid for identification. ");
src/main/java/org/ecocean/identity/IBEISIA.java:629:    // this is "new" identification results
src/main/java/org/ecocean/identity/IBEISIA.java:1417:                for detection, we have to check if we have generated any Annotations, which we then pass on to IA.intake() for identification ... BUT
src/main/java/org/ecocean/identity/IBEISIA.java:1421:                ">>>>>>>>>>>>>>>>>>>>>>>>>> SHORT-CIRCUIT of detection-to-identification <<<<<<<<<<<<<<<<<<<<<<<<");
src/main/java/org/ecocean/identity/IBEISIA.java:1428:            does nothing, yet continues on with pipeline to identification. this can be seen when testing that the `if (fromEmbeddingExtraction)` code
src/main/java/org/ecocean/identity/IBEISIA.java:1461:                    " skipped identification");
src/main/java/org/ecocean/identity/IBEISIA.java:1528:                    "[INFO]: No annotations were suitable for identification. Check resulting identification class(es).");
src/main/java/org/ecocean/identity/IBEISIA.java:1578:    6. how do (when do) we kick off *identification* on an annotation? and what are the target annotations?
src/main/java/org/ecocean/identity/IBEISIA.java:1854:                        " to set identificationStatus");
src/main/java/org/ecocean/identity/IBEISIA.java:2014:        System.out.println("identification most recent action found is " + action);
src/main/java/org/ecocean/identity/IBEISIA.java:2046:        return identificationUserActiveTaskId.get(uname);
src/main/java/org/ecocean/identity/IBEISIA.java:2054:            identificationUserActiveTaskId.remove(uname);
src/main/java/org/ecocean/identity/IBEISIA.java:2056:            identificationUserActiveTaskId.put(uname, taskId);
src/main/java/org/ecocean/identity/IBEISIA.java:3362:        // how do we know when IA has auto-started identification when detection found an annotation???
src/main/java/org/ecocean/identity/IBEISIA.java:3648:            className = IA.getProperty(context, "identificationClass" + i);
src/main/java/org/ecocean/identity/IBEISIA.java:3656:    // does this task want us to skip identification?
src/main/java/org/ecocean/ia/Task.java:132:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
src/main/java/org/ecocean/ia/Task.java:139:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
src/main/java/org/ecocean/ia/Task.java:365:    public Map<String, Integer> identificationStatusSummary() {
src/main/java/org/ecocean/ia/Task.java:732:        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
src/main/java/org/ecocean/ia/Task.java:735:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
src/main/java/org/ecocean/ia/Task.java:737:            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
src/main/java/org/ecocean/ia/Task.java:738:                "model_id", null);
src/main/java/org/ecocean/ia/Task.java:749:        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
src/main/java/org/ecocean/ia/Task.java:752:            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
src/main/java/org/ecocean/ia/Task.java:757:            getParameters().getJSONObject("ibeis.identification").optString("description",
src/main/java/org/ecocean/ia/Task.java:824:        // we basically use this to determine if we are "identification-like" enough
src/main/java/org/ecocean/MetricsBot.java:498:        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 ||
src/main/java/org/ecocean/MetricsBot.java:499:        // parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1)" , "wildbook_identification_tasks","Number of identification
src/main/java/org/ecocean/MetricsBot.java:507:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && created > "
src/main/java/org/ecocean/MetricsBot.java:509:            "wildbook_identification_tasks_added_last24",
src/main/java/org/ecocean/MetricsBot.java:510:            "Number of child identification tasks added last 24 hours", context));
src/main/java/org/ecocean/MetricsBot.java:519:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && created > "
src/main/java/org/ecocean/MetricsBot.java:521:            "wildbook_fastlane_identification_tasks_added_last24",
src/main/java/org/ecocean/MetricsBot.java:522:            "Number of fastlane child identification tasks added last 24 hours", context));
src/main/java/org/ecocean/MetricsBot.java:525:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && completionDateInMilliseconds > "
src/main/java/org/ecocean/MetricsBot.java:527:            "wildbook_fastlane_identification_tasks_completed_last24",
src/main/java/org/ecocean/MetricsBot.java:528:            "Number of fastlane child identification tasks completed last 24 hours", context));
src/main/java/org/ecocean/MetricsBot.java:585:                csvLines.add("wildbook_identification_tasks_completed_last24, " +
src/main/java/org/ecocean/MetricsBot.java:587:                    "Number of child identification tasks completed last 24 hours");
src/main/java/org/ecocean/MetricsBot.java:603:                "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) ";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:107:        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:110:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:143:        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:145:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:169:            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:177:            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:179:                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:182:                    "_mlservice_conf missing predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:198:                configs.matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:227:            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:233:            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:259:                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:315:                String predictModelId = result.optString("predict_model_id",
src/main/java/org/ecocean/ia/MlServiceProcessor.java:316:                    det.mlConfig.optString("predict_model_id", null));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:359:                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:360:                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:393:            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:394:            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:402:            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:403:                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:419:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:439:        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:445:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:473:            // a vector config or matchConfig is null. Don't leave the match
src/main/java/org/ecocean/ia/MlServiceProcessor.java:475:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:480:                    (matchConfig == null ? "null" : matchConfig.toString()));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:540:    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:549:        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:550:        return new ConfigPair(mlConfig, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:555:        JSONObject matchConfig = null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:563:                    matchConfig = new JSONObject(entry.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:568:        if (matchConfig == null) matchConfig = new JSONObject();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:569:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:571:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:573:        return matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:600:    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:602:        JSONObject config = (matchConfig == null) ? new JSONObject()
src/main/java/org/ecocean/ia/MlServiceProcessor.java:603:            : new JSONObject(matchConfig.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:606:                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:607:                config.put("method", embeddingSource.optString("embedding_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:610:                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:611:                config.put("version", embeddingSource.optString("embedding_model_version"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:737:    private static boolean hasEmbeddingForMatchConfig(Annotation ann, JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:739:        if (matchConfig == null) return ann.numberEmbeddings() > 0;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:        String method = matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:        String version = matchConfig.optString("version", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:753:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:755:        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:757:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:765:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:769:            JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:773:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:781:            this.matchConfig = null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:794:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:800:            JSONObject matchConfig, double[] bbox, double theta) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:804:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:814:            this.matchConfig = null;
src/main/java/org/ecocean/media/Feature.java:13: * A Feature is a unique set of (arbitrary) data that contributes to helping with detection and identification of images (or other data, e.g. audio).
src/main/java/org/ecocean/api/SiteSettings.java:99:            settings.put("identificationRemarks", Util.getIdentificationRemarksValues());
src/main/java/org/ecocean/ia/MlServiceClient.java:121:            if (config.has("predict_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:122:                p.put("predict_model_id", config.opt("predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:123:            if (config.has("classify_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:124:                p.put("classify_model_id", config.opt("classify_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:125:            if (config.has("extract_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:126:                p.put("extract_model_id", config.opt("extract_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:127:            if (config.has("orientation_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:128:                p.put("orientation_model_id", config.opt("orientation_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:137:        if (config != null && config.has("extract_model_id")) {
src/main/java/org/ecocean/ia/MlServiceClient.java:138:            p.put("extract_model_id", config.opt("extract_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:229:        String modelId = parent.optString("embedding_model_id", null);
src/main/java/org/ecocean/ia/MlServiceClient.java:230:        String modelVer = parent.optString("embedding_model_version", null);
src/main/java/org/ecocean/ia/MlServiceClient.java:233:                context + " missing embedding_model_id or embedding_model_version",
src/main/java/org/ecocean/ia/MLService.java:56:    // there can be multiple configs (differing model_id)
src/main/java/org/ecocean/ia/MLService.java:175:                // now we are done we can fake a callback to initiate identification
src/main/java/org/ecocean/ia/MLService.java:326:        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
src/main/java/org/ecocean/ia/MLService.java:352:        mv[0] = conf.optString("model_id", null);
src/main/java/org/ecocean/media/MediaAsset.java:81:    protected String identificationStatus;
src/main/java/org/ecocean/media/MediaAsset.java:227:        return this.identificationStatus;
src/main/java/org/ecocean/media/MediaAsset.java:231:        this.identificationStatus = status;
src/main/java/org/ecocean/media/MediaAsset.java:1470:        rtn.put("identification", j);
src/main/java/org/ecocean/ia/MatchResult.java:326:        payload.put("model_id", "miewid-msv4.1");
src/main/java/org/ecocean/ia/IA.java:390:               IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than one we need to make a set of subtasks
src/main/java/org/ecocean/ia/IA.java:394:        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
src/main/java/org/ecocean/ia/IA.java:441:                newTaskParams.put("ibeis.identification",
src/main/java/org/ecocean/ia/IA.java:449:                    newTaskParams.put("ibeis.identification",
src/main/java/org/ecocean/Annotation.java:50:    protected String identificationStatus;
src/main/java/org/ecocean/Annotation.java:139:    // For setting the iaClass returned from detection... No more mangled species names sent to identification
src/main/java/org/ecocean/Annotation.java:760:        return this.identificationStatus;
src/main/java/org/ecocean/Annotation.java:764:        this.identificationStatus = status;
src/main/java/org/ecocean/Annotation.java:877:        jobj.put("identificationStatus", this.getIdentificationStatus());
src/main/java/org/ecocean/Annotation.java:1463:        ann.identificationStatus = this.identificationStatus;
src/main/java/org/ecocean/api/BulkImport.java:242:            // if you skipDetection, you cant do identification, so:
src/main/java/org/ecocean/api/bulk/BulkValidator.java:28:        "Encounter.groupRole", "Encounter.hour", "Encounter.id", "Encounter.identificationRemarks",
src/main/java/org/ecocean/api/bulk/BulkValidator.java:73:        "Encounter.identificationRemarks", "Encounter.individualID", "Encounter.sightingID",
src/main/java/org/ecocean/api/bulk/BulkValidator.java:401:        case "Encounter.identificationRemarks":
src/main/java/org/ecocean/api/bulk/BulkValidator.java:403:                throw new BulkValidatorException("invalid identificationRemarks value: " + value,
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:109:            annIA.put("identificationStatus", ann.getIdentificationStatus());
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:129:                        task.getParameters().has("ibeis.identification")) {
src/main/java/org/ecocean/api/bulk/BulkImporter.java:477:            case "Encounter.identificationRemarks":
src/main/java/org/ecocean/servlet/importer/StandardImport.java:957:        String identificationRemarks = getString(row, "Encounter.identificationRemarks",
src/main/java/org/ecocean/servlet/importer/StandardImport.java:959:        if (identificationRemarks != null) enc.setIdentificationRemarks(identificationRemarks);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:432:                        atask.getParameters().has("ibeis.identification")) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:529:            Map<String, Integer> tsum = task.identificationStatusSummary();
src/main/java/org/ecocean/servlet/importer/ImportTask.java:702:                pj.put("identificationNumberComplete", numIdentificationComplete);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:703:                pj.put("identificationNumTotal", numIdentificationTotal);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:705:                    pj.put("identificationStatus", "identification not started");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:706:                    pj.put("identificationPercent", 0.0);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:708:                    pj.put("identificationStatus", "complete");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:709:                    pj.put("identificationPercent", 1.0);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:711:                    pj.put("identificationStatus", "sent");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:712:                    pj.put("identificationPercent",
src/main/java/org/ecocean/servlet/importer/ImportTask.java:728:            pj.put("identificationStatus", "unknown");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:731:        if (this.skippedIdentification()) pj.put("identificationStatus", "skipped");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:733:        String is = pj.optString("identificationStatus");
src/main/java/org/ecocean/servlet/IAGateway.java:431:                        "WARNING: limited identification matchingSet list size from " +
src/main/java/org/ecocean/servlet/IAGateway.java:472:                System.out.println("_sendIdentificationTask() unable to initiate identification: " +
src/main/java/org/ecocean/servlet/importer/ImportExcelMetadata.java:640:                            enc.getLivingStatus() + ", identification notes " +

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '467,615p;730,747p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '340,360p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceClient.java | sed -n '220,240p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"skipAutoIndexing|new Annotation|setMatchAgainst|setAcmId|predictModelId|EMBEDDING\" src/main/java/org/ecocean/api/bulk/BulkImporter.java src/main/java/org/ecocean/api/BulkImport.java src/main/java/org/ecocean/servlet/importer -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 238ms:
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

 succeeded in 241ms:
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

 succeeded in 233ms:
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

 succeeded in 446ms:
src/main/java/org/ecocean/api/bulk/BulkImporter.java:703:            Annotation ann = new Annotation(tx, ma);
src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:238:            Annotation ann = new Annotation(species, ma);
src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:299:            Annotation ann = new Annotation(species, ma);
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:644:        Annotation ann = new Annotation("Megaptera novaeangliae", ma);
src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:732:            Annotation ann = new Annotation(species, ma);
src/main/java/org/ecocean/servlet/importer/WebImport.java:503:            Annotation ann = new Annotation(species, ma);
src/main/java/org/ecocean/servlet/importer/WebImport.java:567:            Annotation ann = new Annotation(species, ma);
src/main/java/org/ecocean/servlet/importer/StandardImport.java:1407:            Annotation ann = new Annotation(species, ma);
src/main/java/org/ecocean/servlet/importer/StandardImport.java:1413:            // ann.setMatchAgainst(true);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/bulk/BulkImporter.java | sed -n '680,715p'" in /mnt/c/Wildbook-clean2
 succeeded in 260ms:
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

exec
/bin/bash -lc 'rg -n "Base|skipAutoIndexing|setSkipAutoIndexing|getSkipAutoIndexing" src/main/java/org/ecocean/Base.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '120,175p;235,310p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Base.java | sed -n '80,115p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"objectMediaAssets|setObjectMediaAssets|new Task\\(|topTask|intakeMediaAssetsOneSpeciesMlService\" src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/servlet -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 237ms:
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

 succeeded in 240ms:
src/main/java/org/ecocean/Annotation.java:29:public class Annotation extends Base implements java.io.Serializable {
src/main/java/org/ecocean/Annotation.java:175:        // "id" is done in Base
src/main/java/org/ecocean/Annotation.java:274:    @Override public Base getById(Shepherd myShepherd, String id) {
src/main/java/org/ecocean/Annotation.java:278:    // comment cruft only needed for Base class
src/main/java/org/ecocean/Annotation.java:1467:    public static Base createFromApi(JSONObject payload, List<File> files, Shepherd myShepherd)
src/main/java/org/ecocean/Base.java:26: * Base class for other classes such as Encounter.java, Occurrence.java, and MarkedIndividual.java
src/main/java/org/ecocean/Base.java:30:@JsonSerialize(using = BaseSerializer.class) @JsonDeserialize(using =
src/main/java/org/ecocean/Base.java:31:    BaseDeserializer.class) public abstract class Base {
src/main/java/org/ecocean/Base.java:89:    // these should/must be overridden. they are used for generic access control, like in api/ApiBase.java
src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
src/main/java/org/ecocean/Base.java:102:    public void setSkipAutoIndexing(boolean skip) {
src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
src/main/java/org/ecocean/Base.java:106:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
src/main/java/org/ecocean/Base.java:238:        myShepherd.setAction("BaseSerializer");
src/main/java/org/ecocean/Base.java:256:    // this is so we can call it on Base obj, but really is only needed by [overridden by] Encounter (currently)
src/main/java/org/ecocean/Base.java:319:    public abstract Base getById(Shepherd myShepherd, String id);
src/main/java/org/ecocean/Base.java:324:    public static Base getByClassnameAndId(Shepherd myShepherd, String className, String id) {
src/main/java/org/ecocean/Base.java:326:        Base tmp = null;
src/main/java/org/ecocean/Base.java:351:        Base baseObj = (Base)tmpObj;
src/main/java/org/ecocean/Base.java:354:            System.out.println("Base.opensearchSyncIndex(" + indexName +
src/main/java/org/ecocean/Base.java:369:        System.out.println("Base.opensearchSyncIndex(" + indexName + "): stopAfter=" + stopAfter +
src/main/java/org/ecocean/Base.java:373:            Base obj = baseObj.getById(myShepherd, id);
src/main/java/org/ecocean/Base.java:377:                System.out.println("Base.opensearchSyncIndex(" + indexName + "): index failed " +
src/main/java/org/ecocean/Base.java:382:                System.out.println("Base.opensearchSyncIndex(" + indexName + ") needIndexing: " +
src/main/java/org/ecocean/Base.java:386:                System.out.println("Base.opensearchSyncIndex(" + indexName +
src/main/java/org/ecocean/Base.java:391:        System.out.println("Base.opensearchSyncIndex(" + indexName + ") finished needIndexing");
src/main/java/org/ecocean/Base.java:396:                System.out.println("Base.opensearchSyncIndex(" + indexName + ") needRemoval: " +
src/main/java/org/ecocean/Base.java:400:        System.out.println("Base.opensearchSyncIndex(" + indexName + ") finished needRemoval");
src/main/java/org/ecocean/Base.java:405:    public static Base createFromApi(JSONObject payload, List<File> files, Shepherd myShepherd)
src/main/java/org/ecocean/Base.java:442:        and Base.createFromApi() .... this is really ugly and unfortunate. in some wonderful future
src/main/java/org/ecocean/Base.java:467:        if (!(u2 instanceof Base)) return false;
src/main/java/org/ecocean/Base.java:468:        Base two = (Base)u2;
src/main/java/org/ecocean/media/MediaAsset.java:12:import java.util.Base64;
src/main/java/org/ecocean/media/MediaAsset.java:26:import org.ecocean.Base;
src/main/java/org/ecocean/media/MediaAsset.java:48:public class MediaAsset extends Base implements java.io.Serializable {
src/main/java/org/ecocean/media/MediaAsset.java:1199:                    ma.setSkipAutoIndexing(true);
src/main/java/org/ecocean/media/MediaAsset.java:1475:    public void copyInBase64(String b64)
src/main/java/org/ecocean/media/MediaAsset.java:1477:        if (b64 == null) throw new IOException("copyInBase64() null string");
src/main/java/org/ecocean/media/MediaAsset.java:1480:            imgBytes = Base64.getDecoder().decode(b64);
src/main/java/org/ecocean/media/MediaAsset.java:1482:            throw new IOException("copyInBase64() could not parse: " + ex.toString());
src/main/java/org/ecocean/media/MediaAsset.java:1501:            throw new IOException("copyInBase64() could not write " + file);
src/main/java/org/ecocean/media/MediaAsset.java:1711:        // "id" is done in Base
src/main/java/org/ecocean/media/MediaAsset.java:1758:    @Override public Base getById(Shepherd myShepherd, String id) {
src/main/java/org/ecocean/media/MediaAsset.java:1762:    // we override Base version, as we want to (dont we?) always skip auto-indexing children assets
src/main/java/org/ecocean/media/MediaAsset.java:1763:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
src/main/java/org/ecocean/media/MediaAsset.java:1773:    // comment cruft only needed for Base class

 succeeded in 237ms:
    80	     * @param newComments to add
    81	     */
    82	    public abstract void addComments(final String newComments);
    83	
    84	    // issue 785 makes this no longer necessary; the overrides are left on Occurrence and MarkedIndividual
    85	    // for now as reference -- but are not called. they will need to be addressed when these classes are searchable
    86	    // public abstract List<String> userIdsWithViewAccess(Shepherd myShepherd);
    87	    // public abstract List<String> userIdsWithEditAccess(Shepherd myShepherd);
    88	
    89	    // these should/must be overridden. they are used for generic access control, like in api/ApiBase.java
    90	    public boolean canUserView(User user, Shepherd myShepherd) {
    91	        return false;
    92	    }
    93	
    94	    public boolean canUserEdit(User user, Shepherd myShepherd) {
    95	        return false;
    96	    }
    97	
    98	    // this allows us to delay indexing during heavy activity which triggers auto-indexing
    99	    // via lifecycle persisting triggers e.g. during bulk import
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

 succeeded in 519ms:
src/main/java/org/ecocean/ia/IA.java:67:        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:72:        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:95:        if (parentTask == null) parentTask = new Task();
src/main/java/org/ecocean/ia/IA.java:148:        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:149:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:150:        topTask.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:151:        myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:166:        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
src/main/java/org/ecocean/ia/IA.java:167:        // child finalization is local; no first-finisher-wins. The topTask
src/main/java/org/ecocean/ia/IA.java:169:        // summary code that reads topTask.objectMediaAssets keeps working).
src/main/java/org/ecocean/ia/IA.java:172:            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
src/main/java/org/ecocean/ia/IA.java:184:            Task task = (numDetectAlgos == 1) ? topTask : new Task();
src/main/java/org/ecocean/ia/IA.java:185:            task.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:186:            task.setParameters(topTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:199:            qjob.put("taskId", topTask.getId());
src/main/java/org/ecocean/ia/IA.java:221:            " assets; queued? = " + sent + "; " + topTask);
src/main/java/org/ecocean/ia/IA.java:222:        return topTask;
src/main/java/org/ecocean/ia/IA.java:228:     * topTask; each emits a {@code mlServiceV2:true} payload to the
src/main/java/org/ecocean/ia/IA.java:233:     * topTask. The topTask itself remains as the aggregator that holds the
src/main/java/org/ecocean/ia/IA.java:242:    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
src/main/java/org/ecocean/ia/IA.java:243:        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
src/main/java/org/ecocean/ia/IA.java:246:            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
src/main/java/org/ecocean/ia/IA.java:250:        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
src/main/java/org/ecocean/ia/IA.java:251:            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
src/main/java/org/ecocean/ia/IA.java:252:        return topTask;
src/main/java/org/ecocean/ia/IA.java:259:     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
src/main/java/org/ecocean/ia/IA.java:271:     * the task references the MediaAsset through objectMediaAssets),
src/main/java/org/ecocean/ia/IA.java:277:     * <p>If {@code topTask} is null a fresh root task is created inside
src/main/java/org/ecocean/ia/IA.java:282:        Taxonomy taxy, Task topTask, String context, String baseUrl) {
src/main/java/org/ecocean/ia/IA.java:283:        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
src/main/java/org/ecocean/ia/IA.java:286:        childTask.setObjectMediaAssets(singleton);
src/main/java/org/ecocean/ia/IA.java:374:        // Task topTask = (parentTask==null) ? new Task() : parentTask;
src/main/java/org/ecocean/ia/IA.java:376:        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
src/main/java/org/ecocean/ia/IA.java:378:        // return topTask;
src/main/java/org/ecocean/ia/IA.java:383:        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:384:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:385:        topTask.setObjectAnnotations(anns);
src/main/java/org/ecocean/ia/IA.java:443:                topTask.setParameters(newTaskParams);
src/main/java/org/ecocean/ia/IA.java:444:                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
src/main/java/org/ecocean/ia/IA.java:447:                    Task t = new Task();
src/main/java/org/ecocean/ia/IA.java:452:                    topTask.addChild(t);
src/main/java/org/ecocean/ia/IA.java:458:            myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:498:        System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
src/main/java/org/ecocean/ia/IA.java:499:        return topTask;
src/main/java/org/ecocean/ia/IA.java:535:            Task topTask = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/ia/IA.java:536:            if (topTask == null) topTask = new Task(taskId);
src/main/java/org/ecocean/ia/IA.java:537:            if (fastlane) topTask.addParameter("fastlane", true);
src/main/java/org/ecocean/ia/IA.java:538:            myShepherd.storeNewTask(topTask);
src/main/java/org/ecocean/ia/IA.java:552:                Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
src/main/java/org/ecocean/ia/IA.java:554:                    " for (parent) " + topTask);
src/main/java/org/ecocean/ia/IA.java:555:                topTask.addChild(mtask);
src/main/java/org/ecocean/ia/IA.java:579:                    if (!topTask.getObjectMediaAssets().contains(ma)) {
src/main/java/org/ecocean/ia/IA.java:580:                        topTask.addObject(ma);
src/main/java/org/ecocean/ia/IA.java:583:                Task atask = intakeAnnotations(myShepherd, anns, topTask, fastlane);
src/main/java/org/ecocean/ia/IA.java:585:                    " for " + topTask);
src/main/java/org/ecocean/ia/IA.java:586:                myShepherd.getPM().refresh(topTask);
src/main/java/org/ecocean/ia/IA.java:587:                topTask.addChild(atask);
src/main/java/org/ecocean/ia/IA.java:588:                topTask.setModified();
src/main/java/org/ecocean/Encounter.java:5497:                    parentTask = new Task();
src/main/java/org/ecocean/Encounter.java:5543:                    parentTask = new Task();
src/main/java/org/ecocean/servlet/EncounterForm.java:895:                            parentTask = new Task();
src/main/java/org/ecocean/servlet/MediaAssetCreate.java:114:                    final Task parentTask = new Task();
src/main/java/org/ecocean/servlet/ProjectIA.java:82:                            Task parentTask = new Task();
src/main/java/org/ecocean/servlet/ProjectIA.java:93:                            jobJSON.put("topTaskId", parentTask.getId());
src/main/java/org/ecocean/servlet/IAGateway.java:116:                if (task == null) task = new Task(taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:226:                task.setObjectMediaAssets(mas);
src/main/java/org/ecocean/servlet/IAGateway.java:337:            parentTask = new Task(taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:347:                Task newTask = new Task(parentTask);
src/main/java/org/ecocean/servlet/IAGateway.java:886:            parentTask = new Task(); // root task to hold all others, to connect to ImportTask
src/main/java/org/ecocean/servlet/IAGateway.java:900:        Task task = new Task();

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '130,180p;242,252p'" in /mnt/c/Wildbook-clean2
 succeeded in 276ms:
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

exec
/bin/bash -lc 'rg -n "finalizeZeroDetections|zero" src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '260,285p;620,630p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/IAJsonProperties.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getActiveMlServiceConfigs|getIdentConfig|_id_conf|_mlservice_conf|pipeline_root|method|version\" src/main/java/org/ecocean/IAJsonProperties.java src/main/java/org/ecocean/ia -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 217ms:
101:            return finalizeZeroDetections(maId, taskId);
265:    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
267:        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
283:            markTaskError(taskId, "PERSIST", "zero-detection finalize failed: " + ex.getMessage());

 succeeded in 220ms:
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

 succeeded in 228ms:
     1	package org.ecocean;
     2	
     3	import java.io.FileNotFoundException;
     4	import java.util.ArrayList;
     5	import java.util.Collections;
     6	import java.util.HashMap;
     7	import java.util.HashSet;
     8	import java.util.Iterator;
     9	import java.util.List;
    10	import java.util.Map;
    11	import java.util.Set;
    12	
    13	import org.ecocean.shepherd.core.Shepherd;
    14	import org.json.JSONArray;
    15	import org.json.JSONObject;
    16	
    17	import org.ecocean.identity.IBEISIA;
    18	import org.ecocean.media.MediaAsset;
    19	
    20	public class IAJsonProperties extends JsonProperties {
    21	    // maps IA.properties keys to IA.json keys that should return the same value
    22	    private static final Map<String, String> globalBackCompatibleKeyMap;
    23	    static {
    24	        Map<String, String> m = new HashMap<String, String>();
    25	        m.put("IBEISIARestUrlAddImages", "_global.add_images");
    26	        m.put("IBEISIARestUrlAddAnnotations", "_global.add_annotations");
    27	        m.put("IBEISIARestUrlStartIdentifyAnnotations", "_global.start_identify");
    28	        m.put("IBEISIARestUrlStartDetectImages", "_global.start_detect");
    29	        m.put("IBEISIARestUrlDetectReview", "_global.detect_review");
    30	        m.put("IBEISIARestUrlGetJobStatus", "_global.get_job_status");
    31	        m.put("IBEISIARestUrlGetJobResult", "_global.get_job_result");
    32	        globalBackCompatibleKeyMap = Collections.unmodifiableMap(m);
    33	    }
    34	
    35	    public static final String DETECT_URL_KEY = "start_detect";
    36	
    37	    public IAJsonProperties()
    38	    throws FileNotFoundException {
    39	        super("IA.json");
    40	    }
    41	
    42	    // hackey constructor so we don't need to catch an exception every time we use these. Is there a better way?
    43	    public static IAJsonProperties iaConfig() {
    44	        try {
    45	            return new IAJsonProperties();
    46	        } catch (Exception e) {
    47	            System.out.println(
    48	                "IAJsonProperties ERROR: Could not find IA.json file! returning null and therefore about to hit an NPE elsewhere");
    49	            return null;
    50	        }
    51	    }
    52	
    53	    // naming convention: not using 'get' on static methods
    54	    public static String taxonomyKey(Taxonomy taxy) {
    55	        return taxy.getScientificName().replace(' ', '.');
    56	    }
    57	
    58	    public boolean hasIA(Taxonomy taxy) {
    59	        Object conf = this.get(taxonomyKey(taxy));
    60	
    61	        return (conf != null);
    62	    }
    63	
    64	    public boolean hasIA(Encounter enc, Shepherd myShepherd) {
    65	        Taxonomy taxy = enc.getTaxonomy(myShepherd);
    66	
    67	        return hasIA(taxy);
    68	    }
    69	
    70	    // Detection methods
    71	    public static String detectionKey(Taxonomy taxy) {
    72	        return taxonomyKey(taxy) + "._detect_conf";
    73	    }
    74	
    75	    public JSONArray getDetectionConfigs(Taxonomy taxy) {
    76	        return (JSONArray)this.get(detectionKey(taxy));
    77	    }
    78	
    79	    public JSONArray getDetectionConfigs(MediaAsset ma, Shepherd myShepherd) {
    80	        JSONArray allConfs = new JSONArray();
    81	
    82	        for (Taxonomy taxy : ma.getTaxonomies(myShepherd)) {
    83	            JSONArray theseConfs = getDetectionConfigs(taxy);
    84	            for (int i = 0; i < theseConfs.length(); i++) {
    85	                allConfs.put(theseConfs.get(i));
    86	            }
    87	        }
    88	        return allConfs;
    89	    }
    90	
    91	    public int numDetectionAlgos(Taxonomy taxy) {
    92	        JSONArray detectConfs = getDetectionConfigs(taxy);
    93	
    94	        return detectConfs.length();
    95	    }
    96	
    97	    public JSONObject getDetectionConfig(Taxonomy taxy) {
    98	        return getDetectionConfig(taxy, 0);
    99	    }
   100	
   101	    // all detection methods take an int i, bc there may be multiple detection algos for a taxonomy
   102	    // DetectionArgs are what we pass to IA. DetectionConfig is all of those, plus the target url, minus the callback (which is generated by each
   103	    // wildbook vis a vis its baseUrl, and not a config value)
   104	    public JSONObject getDetectionConfig(Taxonomy taxy, int i) {
   105	        JSONArray confs = getDetectionConfigs(taxy);
   106	
   107	        System.out.println("IAJsonProperties.getDetectionConfigs returned " + confs.toString());
   108	        return confs.getJSONObject(i);
   109	    }
   110	
   111	    // DetectionArgs are what we pass to IA.
   112	    public JSONObject getDetectionArgs(Taxonomy taxy, String baseUrl) {
   113	        return getDetectionArgs(taxy, baseUrl, 0);
   114	    }
   115	
   116	    public JSONObject getDetectionArgs(Taxonomy taxy, String baseUrl, int i) {
   117	        JSONObject config = getDetectionConfig(taxy, i);
   118	        // we can't just mess with config bc changes aren't scoped to this method
   119	        // and this is the stupid-ass way to clone JSONObjects
   120	        JSONObject args = new JSONObject(config, JSONObject.getNames(config));
   121	
   122	        args.remove(DETECT_URL_KEY);
   123	        String callbackUrl = IBEISIA.callbackUrl(baseUrl);
   124	        args.put("callback_url", callbackUrl);
   125	        return args;
   126	    }
   127	
   128	    public String getDetectionUrl(Taxonomy taxy) {
   129	        return getDetectionUrl(taxy, 0);
   130	    }
   131	
   132	    public String getDetectionUrl(Taxonomy taxy, int i) {
   133	        JSONObject conf = getDetectionConfig(taxy, i);
   134	
   135	        System.out.println("getDetectionUrl looking for " + DETECT_URL_KEY + " in conf " +
   136	            conf.toString());
   137	        return conf.getString(DETECT_URL_KEY);
   138	    }
   139	
   140	    // e.g. if a humpback whale detection returns ia class sperm_whale_fluke this will return humpback_fluke or whatever
   141	    public String convertIAClassForTaxonomy(String returnedIAClass, Taxonomy taxy) {
   142	        String taxKey = taxonomyKey(taxy);
   143	        String lookupKey = taxKey + "." + returnedIAClass + "._save_as";
   144	        String ans = (String)get(lookupKey);
   145	
   146	        System.out.println("IAJsonProperties.convertIAClassForTaxonomy called on " +
   147	            returnedIAClass + " for taxonomy " + taxy.toString());
   148	        System.out.println(".................convertIAClassForTaxonomy made lookupKey " +
   149	            lookupKey + " and found " + ans);
   150	        if (!Util.stringExists(ans)) {
   151	            String defaultLookupKey = taxKey + "._default._save_as";
   152	            ans = (String)get(defaultLookupKey);
   153	            System.out.println("........fallback convertIAClassForTaxonomy made defaulLookupKey " +
   154	                defaultLookupKey + " and found " + ans);
   155	        }
   156	        if (!Util.stringExists(ans)) ans = returnedIAClass;
   157	        return ans;
   158	    }
   159	
   160	    // just the stings, not Taxonomys
   161	    public List<String> getAllTaxonomyStrings() {
   162	        List<String> taxs = new ArrayList<String>();
   163	        Iterator<String> it1 = this.getJson().keys();
   164	
   165	        while (it1.hasNext()) {
   166	            String genus = it1.next();
   167	            if (genus.startsWith("_")) continue;
   168	            JSONObject second = this.getJson().optJSONObject(genus);
   169	            if (second == null) continue;
   170	            Iterator<String> it2 = second.keys();
   171	            while (it2.hasNext()) {
   172	                String species = it2.next();
   173	                if (species.startsWith("_")) continue;
   174	                taxs.add(genus + " " + species);
   175	            }
   176	        }
   177	        return taxs;
   178	    }
   179	
   180	    public Set<String> getAllIAClasses() {
   181	        return getAllIAClasses(this.getJson());
   182	    }
   183	
   184	    public Set<String> getAllIAClasses(JSONObject jobj) {
   185	        Set<String> rtn = new HashSet<String>();
   186	
   187	        if (jobj == null) return rtn;
   188	        JSONArray detectConf = jobj.optJSONArray("_detect_conf");
   189	        boolean correctLevel = (detectConf != null);
   190	        for (String key : (Set<String>)jobj.keySet()) {
   191	            if (key.startsWith("_")) continue;
   192	            String parts[] = key.split("\\+");
   193	            if (correctLevel) rtn.add(parts[0]);
   194	            JSONObject child = jobj.optJSONObject(key);
   195	            rtn.addAll(getAllIAClasses(child));
   196	        }
   197	        return rtn;
   198	    }
   199	
   200	    public Set<String> getAllIAClassesWithParts() {
   201	        return getAllIAClassesWithParts(this.getJson());
   202	    }
   203	
   204	    public Set<String> getAllIAClassesWithParts(JSONObject jobj) {
   205	        Set<String> rtn = new HashSet<String>();
   206	
   207	        if (jobj == null) return rtn;
   208	        JSONArray detectConf = jobj.optJSONArray("_detect_conf");
   209	        boolean correctLevel = (detectConf != null);
   210	        for (String key : (Set<String>)jobj.keySet()) {
   211	            if (key.startsWith("_")) continue;
   212	            if (correctLevel) rtn.add(key);
   213	            JSONObject child = jobj.optJSONObject(key);
   214	            rtn.addAll(getAllIAClassesWithParts(child));
   215	        }
   216	        return rtn;
   217	    }
   218	
   219	    public List<Taxonomy> getAllTaxonomies(Shepherd myShepherd) {
   220	        List<Taxonomy> taxs = new ArrayList<Taxonomy>();

 succeeded in 382ms:
src/main/java/org/ecocean/IAJsonProperties.java:53:    // naming convention: not using 'get' on static methods
src/main/java/org/ecocean/IAJsonProperties.java:70:    // Detection methods
src/main/java/org/ecocean/IAJsonProperties.java:101:    // all detection methods take an int i, bc there may be multiple detection algos for a taxonomy
src/main/java/org/ecocean/IAJsonProperties.java:118:        // we can't just mess with config bc changes aren't scoped to this method
src/main/java/org/ecocean/IAJsonProperties.java:238:    // Identification methods
src/main/java/org/ecocean/IAJsonProperties.java:252:        return identKey(taxy, iaClass) + "._id_conf";
src/main/java/org/ecocean/IAJsonProperties.java:255:    public JSONArray getIdentConfig(Taxonomy taxy) {
src/main/java/org/ecocean/IAJsonProperties.java:256:        return getIdentConfig(taxy, "_default");
src/main/java/org/ecocean/IAJsonProperties.java:259:    public JSONArray getIdentConfig(Taxonomy taxy, String iaClass) {
src/main/java/org/ecocean/IAJsonProperties.java:268:        // The implicit `_mlservice_conf` appending that used to live here was
src/main/java/org/ecocean/IAJsonProperties.java:270:        // contract: `_id_conf.default.pipeline_root` is the single decision
src/main/java/org/ecocean/IAJsonProperties.java:272:        // via `getActiveMlServiceConfigs(...)` only — never implicitly mixed
src/main/java/org/ecocean/IAJsonProperties.java:273:        // into ident configs returned by this method.
src/main/java/org/ecocean/IAJsonProperties.java:284:    // `_id_conf.default.pipeline_root == "vector"` AND its
src/main/java/org/ecocean/IAJsonProperties.java:285:    // `_mlservice_conf` array is populated. Both conditions are enforced
src/main/java/org/ecocean/IAJsonProperties.java:286:    // by `getActiveMlServiceConfigs`. A species with `_mlservice_conf` but
src/main/java/org/ecocean/IAJsonProperties.java:287:    // pipeline_root="HotSpotter" does NOT route to ml-service.
src/main/java/org/ecocean/IAJsonProperties.java:291:        return taxonomyKey(taxy) + "._mlservice_conf";
src/main/java/org/ecocean/IAJsonProperties.java:295:     * Returns the per-taxonomy `_mlservice_conf` JSONArray iff the species'
src/main/java/org/ecocean/IAJsonProperties.java:296:     * `_id_conf.default.pipeline_root == "vector"` AND the array is
src/main/java/org/ecocean/IAJsonProperties.java:300:    public JSONArray getActiveMlServiceConfigs(Taxonomy taxy) {
src/main/java/org/ecocean/IAJsonProperties.java:312:     * Returns the `pipeline_root` string of the `_id_conf` entry marked
src/main/java/org/ecocean/IAJsonProperties.java:314:     * null if no entry is marked default, no `pipeline_root` is set on the
src/main/java/org/ecocean/IAJsonProperties.java:315:     * default entry, or no `_id_conf` exists at all.
src/main/java/org/ecocean/IAJsonProperties.java:323:        JSONArray idConf = getIdentConfig(taxy, iaClass);
src/main/java/org/ecocean/IAJsonProperties.java:329:                return entry.optString("pipeline_root", null);
src/main/java/org/ecocean/IAJsonProperties.java:341:            JSONArray idConfigs = getIdentConfig(taxy, iaClass);
src/main/java/org/ecocean/IAJsonProperties.java:379:    // mimics an old IBEISIA method for easy migration
src/main/java/org/ecocean/IAJsonProperties.java:389:        JSONArray identConfig = getIdentConfig(taxy, iaClass);
src/main/java/org/ecocean/ia/MLService.java:65:        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
src/main/java/org/ecocean/ia/MLService.java:333:        String[] methodValues = getMethodValues(res);
src/main/java/org/ecocean/ia/MLService.java:334:        Embedding emb = new Embedding(ann, methodValues[0], methodValues[1], vecArr);
src/main/java/org/ecocean/ia/MLService.java:335:        // maybe this is unwise? could 2 embeddings *from different methods* have same vectors? TODO
src/main/java/org/ecocean/ia/MLService.java:353:        // kinda hack version splitting here but... and i think some might not have dash, like "msv3"  :(
src/main/java/org/ecocean/ia/MatchResult.java:280:        url += "&version=heatmask";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:179:                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:182:                    "_mlservice_conf missing predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:360:                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:403:                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:545:        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:556:        JSONArray identConfigs = iac.getIdentConfig(taxy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:562:                    && "vector".equals(entry.optString("pipeline_root", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:588:                    config.put("method", emb.getMethod());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:589:                    config.put("version", emb.getMethodVersion());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:590:                    config.put("pipeline_root", "vector");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:605:            if (!Util.stringExists(config.optString("method", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:607:                config.put("method", embeddingSource.optString("embedding_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:609:            if (!Util.stringExists(config.optString("version", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:610:                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:611:                config.put("version", embeddingSource.optString("embedding_model_version"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:        String method = matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:        String version = matchConfig.optString("version", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:742:        if (!Util.stringExists(method)) return ann.numberEmbeddings() > 0;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:743:        return ann.getEmbeddingByMethod(method, version) != null;
src/main/java/org/ecocean/ia/Task.java:408:    // internal utility method for above
src/main/java/org/ecocean/ia/Task.java:436:    // convenience method to construct the JSONObject from key/value
src/main/java/org/ecocean/ia/Task.java:530:    // TODO: evaluate if we should support versions for multiple objects (when needed)
src/main/java/org/ecocean/ia/Task.java:643:    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
src/main/java/org/ecocean/ia/Task.java:754:        // we set HotSpotter if pipeline_root is not set here
src/main/java/org/ecocean/ia/Task.java:755:        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
src/main/java/org/ecocean/ia/Task.java:758:            "unknown algorithm/method"));
src/main/java/org/ecocean/ia/Task.java:823:        JSONObject methodInfo = getIdentificationMethodInfo();
src/main/java/org/ecocean/ia/Task.java:826:        if (methodInfo != null) {
src/main/java/org/ecocean/ia/Task.java:827:            rtn.put("method", methodInfo);
src/main/java/org/ecocean/ia/Task.java:831:               (as there may be non-leaf nodes with methodInfo)
src/main/java/org/ecocean/ia/IA.java:49:    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
src/main/java/org/ecocean/ia/IA.java:64:    hence the overly-inclusive Object version below!
src/main/java/org/ecocean/ia/IA.java:160:        // If the species' _id_conf.default.pipeline_root is "vector" AND
src/main/java/org/ecocean/ia/IA.java:161:        // _mlservice_conf is configured, route per-asset through the
src/main/java/org/ecocean/ia/IA.java:163:        // WBIA path below — production deployments without _mlservice_conf
src/main/java/org/ecocean/ia/IA.java:171:            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
src/main/java/org/ecocean/ia/IA.java:267:     * is persisted before this method enqueues. On enqueue failure the
src/main/java/org/ecocean/ia/IA.java:278:     * this method. This matches the reconciler's use case where there is
src/main/java/org/ecocean/ia/IA.java:346:                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
src/main/java/org/ecocean/ia/IA.java:660:    public static String getProperty(String context, String label, Taxonomy tax) { // no-default version
src/main/java/org/ecocean/ia/MlServiceClient.java:74:     * @param config      a single {@code _mlservice_conf} entry from IA.json
src/main/java/org/ecocean/ia/MlServiceClient.java:230:        String modelVer = parent.optString("embedding_model_version", null);
src/main/java/org/ecocean/ia/MlServiceClient.java:233:                context + " missing embedding_model_id or embedding_model_version",
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:25:    A Queue is not strictly necessary.  The intake methods could have simply done whatever processing necessary (e.g. send the objects to a blackbox
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:26:       IA service) in real-time.  The Queue just demostrates how to allow intake methods to asynchronously stack up jobs in the background.
src/main/java/org/ecocean/ia/plugin/IAPlugin.java:37:    // having the code in these methods set the child on the parent  (hence the 'final')
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:691:    // instance version of below (since context is known)

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/IAJsonProperties.java | sed -n '238,335p'" in /mnt/c/Wildbook-clean2
 succeeded in 239ms:
   238	    // Identification methods
   239	    public static String identKey(Taxonomy taxy) {
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
   331	        }
   332	        return null;
   333	    }
   334	
   335	    public JSONArray getAllIdentConfigs(Taxonomy taxy) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '28,115p;130,190p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "VECTORFLOATARRAY|vectorFloatArray|VECTOR|COLUMN|@Persistent|fetchGroup" src/main/resources/org/ecocean/package.jdo src/main/java/org/ecocean/Embedding.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '2040,2078p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "getAnEmbedding|getEmbeddingByMethod|vectorToFloatArray|setAnnotation|getAnnotation|getMethod|getMethodVersion" src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/Annotation.java' in /mnt/c/Wildbook-clean2
 succeeded in 220ms:
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

 succeeded in 240ms:
src/main/java/org/ecocean/Embedding.java:32:    private float[] vectorFloatArray;
src/main/java/org/ecocean/Embedding.java:74:        if ((vector == null) && (vectorFloatArray != null))
src/main/java/org/ecocean/Embedding.java:75:            vector = new PGvector(vectorFloatArray);
src/main/java/org/ecocean/Embedding.java:81:            vectorFloatArray = vec.toArray();
src/main/java/org/ecocean/Embedding.java:83:            vectorFloatArray = null;
src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
src/main/java/org/ecocean/Embedding.java:205:        return Arrays.equals(this.vectorFloatArray, emb.vectorFloatArray);
src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
src/main/resources/org/ecocean/package.jdo:1060:		<field name="vectorFloatArray" persistence-modifier="persistent" />

 succeeded in 221ms:
src/main/java/org/ecocean/Annotation.java:249:                jgen.writeStringField("method", emb.getMethod());
src/main/java/org/ecocean/Annotation.java:250:                jgen.writeStringField("methodVersion", emb.getMethodVersion());
src/main/java/org/ecocean/Annotation.java:253:                float[] vecFloat = emb.vectorToFloatArray();
src/main/java/org/ecocean/Annotation.java:275:        return myShepherd.getAnnotation(id);
src/main/java/org/ecocean/Annotation.java:649:        for (Annotation ann : this.getMediaAsset().getAnnotations()) { // fyi .getAnnotations() doesnt return null
src/main/java/org/ecocean/Annotation.java:665:        List<Annotation> anns = ma.getAnnotationsSortedPositionally();
src/main/java/org/ecocean/Annotation.java:1165:            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
src/main/java/org/ecocean/Annotation.java:1184:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1194:        Embedding emb = getEmbeddingByMethod(method, methodVersion);
src/main/java/org/ecocean/Annotation.java:1202:            new JSONArray(emb.vectorToFloatArray()));
src/main/java/org/ecocean/Annotation.java:1252:            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
src/main/java/org/ecocean/Annotation.java:1525:        List<Annotation> annots = ma.getAnnotations(); // get before we add ours
src/main/java/org/ecocean/Annotation.java:1590:                List<Annotation> encAnnots = enc.getAnnotations(ma);
src/main/java/org/ecocean/Annotation.java:1608:                List<Annotation> encAnnots = enc.getAnnotations(ma);
src/main/java/org/ecocean/Annotation.java:1657:        for (Annotation a : ma.getAnnotations()) {
src/main/java/org/ecocean/Annotation.java:2016:            String md = emb.getMethodDescription();
src/main/java/org/ecocean/Annotation.java:2027:        if (!this.equals(emb.getAnnotation())) emb.setAnnotation(this);
src/main/java/org/ecocean/Annotation.java:2038:    public Embedding getAnEmbedding() {
src/main/java/org/ecocean/Annotation.java:2039:        return getEmbeddingByMethod(null, null);
src/main/java/org/ecocean/Annotation.java:2042:    public Embedding getEmbeddingByMethod(String method) {
src/main/java/org/ecocean/Annotation.java:2043:        return getEmbeddingByMethod(method, null);
src/main/java/org/ecocean/Annotation.java:2047:    public Embedding getEmbeddingByMethod(String method, String methodVersion) {
src/main/java/org/ecocean/Annotation.java:2053:            if (!method.equals(emb.getMethod())) continue;
src/main/java/org/ecocean/Annotation.java:2054:            if ((methodVersion == null) || (methodVersion.equals(emb.getMethodVersion())))
src/main/java/org/ecocean/Embedding.java:64:    public Annotation getAnnotation() {
src/main/java/org/ecocean/Embedding.java:68:    public void setAnnotation(Annotation ann) {
src/main/java/org/ecocean/Embedding.java:92:    public String getMethod() {
src/main/java/org/ecocean/Embedding.java:100:    public String getMethodVersion() {
src/main/java/org/ecocean/Embedding.java:108:    public String getMethodDescription() {
src/main/java/org/ecocean/Embedding.java:117:    public float[] vectorToFloatArray() {
src/main/java/org/ecocean/Embedding.java:352:                String[] mv = MLService.getMethodValues(iaConfig);
src/main/java/org/ecocean/Embedding.java:419:        st += " " + this.getMethodDescription();

 succeeded in 226ms:
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
  2069	        return null;
  2070	    }
  2071	
  2072	/*
  2073	    public void loadEmbeddingVectors(Shepherd myShepherd) {
  2074	        if (embeddings == null) return;
  2075	        for (Embedding emb : this.embeddings) {
  2076	            emb.loadVector(myShepherd);
  2077	        }
  2078	    }

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '116,129p;198,207p'" in /mnt/c/Wildbook-clean2
 succeeded in 283ms:
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
   198	    public static int getVectorDimension() {
   199	        return 2152;
   200	    }
   201	
   202	    // compare vectors of two Embeddings
   203	    public boolean hasEqualVector(Embedding emb) {
   204	        if (emb == null) return false;
   205	        return Arrays.equals(this.vectorFloatArray, emb.vectorFloatArray);
   206	    }
   207	

exec
/bin/bash -lc "rg -n \"getId\\(\\)\" src/main/java/org/ecocean/media/MediaAsset.java | head -20 && nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '110,130p;318,340p'" in /mnt/c/Wildbook-clean2
 succeeded in 279ms:
162:    public String getId() {
860:        jobj.put("id", this.getId());
882:                jf.put("id", ft.getId());
897:                    jf.put("annotationId", ann.getId());
1071:                ma.addDerivationMethod("detachedFrom", this.getId());
1455:            s = IBEISIA.parseIdentificationStatus(ann.getId(), myShepherd);
1540:                    + this.getId());
1736:            jgen.writeStringField("id", enc.getId());
1744:            jgen.writeStringField("id", ann.getId());
   110	        this.id = id;
   111	        this.setUUID();
   112	        this.store = store;
   113	        this.parameters = params;
   114	        if (params != null) this.parametersAsString = params.toString();
   115	        this.setRevision();
   116	        this.setHashCode();
   117	        if (this.acmId == null) this.acmId = this.getUUID();
   118	    }
   119	
   120	    public AccessControl getAccessControl() {
   121	        return accessControl;
   122	    }
   123	
   124	    public void setAccessControl(AccessControl ac) {
   125	        accessControl = ac;
   126	    }
   127	
   128	    public void setAccessControl(HttpServletRequest request) {
   129	        this.setAccessControl(new AccessControl(request));
   130	    }
   318	            return;
   319	        }
   320	        parameters = p;
   321	        parametersAsString = p.toString();
   322	    }
   323	
   324	    ///note: really the only place that should call getParametersAsString or setParametersAsString is datanucleus...
   325	    ///  always use getParameters() and setParameters() instead!
   326	    public String getParametersAsString() {
   327	        if (parametersAsString != null) return parametersAsString;
   328	        if (parameters == null) return null;
   329	        parametersAsString = parameters.toString();
   330	        return parametersAsString;
   331	    }
   332	
   333	    public void setParametersAsString(String p) {
   334	        if (p == null) {
   335	            System.out.println("WARNING: attempted to set null parametersAsString on " + this +
   336	                "; ignoring");
   337	            return;
   338	        }
   339	        parametersAsString = p;
   340	        // now we also set parameters as the JSONObject (or try)

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '156,168p'" in /mnt/c/Wildbook-clean2
 succeeded in 241ms:
   156	    }
   157	
   158	    public int getIdInt() {
   159	        return id;
   160	    }
   161	
   162	    public String getId() {
   163	        return String.valueOf(id);
   164	    }
   165	
   166	    public void setId(String s) {
   167	        id = Integer.parseInt(s);
   168	    }

codex
The schema-sensitive part now looks consistent with existing precedent: `ImportTask` already joins `ENCOUNTER_ANNOTATIONS → ANNOTATION_FEATURES → MEDIAASSET_FEATURES` using the same `ID_OID`/`ID_EID` shape, so the proposed eligibility join is not inventing a new mapping. I’m checking the WBIA side and the deferred queue edge cases before writing up findings.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/WbiaQueueUtil.java | sed -n '1,110p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"WbiaRegister|wbiaRegister|iaAnnotationIdsStrict|sendAnnotations|park|NETWORK_FAIL|Phase 0|Phase 1|Phase 2|mediaAssetToUri|parseImage\" src/main/java/org/ecocean/ia src/main/java/org/ecocean/ia/plugin src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class Wbia|WbiaRegister|RegisterRequest|register\" src/main/java/org/ecocean/ia src/test/java/org/ecocean/ia -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-wbia-image-registration-design.md | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
 succeeded in 225ms:
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

 succeeded in 221ms:
     1	# Design: image-then-annotation WBIA registration in polling thread
     2	
     3	## Context
     4	
     5	The v2 WBIA registration polling thread (commit `c6ffe5d20` and follow-ups)
     6	calls `/api/annot/json/` to register each ml-service-created annotation
     7	with WBIA. The thread is meant to keep WBIA in sync so HotSpotter remains
     8	available as a fallback identifier.
     9	
    10	What works today:
    11	- The polling JDOQL picks pending annotations (`wbiaRegistered == false
    12	  && wbiaRegisterAttempts < 10`).
    13	- Phase A loads a detached DTO under a short Shepherd transaction.
    14	- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
    15	- Phase C persists the outcome in a fresh Shepherd.
    16	
    17	What's broken in production: the legacy v2 routing path
    18	(`IA.intakeMediaAssetsOneSpeciesMlService`) **never tells WBIA about the
    19	image** — it skips the legacy `WBIA.sendMediaAssets()` call because
    20	ml-service does its own detection. By the time the annotation-registration
    21	polling thread fires `/api/annot/json/`, WBIA has no record of the image
    22	uuid the annotation references. WBIA returns HTTP 500 with `ValueError:
    23	The input list image_uuid_list has invalid values (index, value): [(0,
    24	None)]`. Annotations get marked failed and retry until `MAX_ATTEMPTS=10`,
    25	then park.
    26	
    27	## Goal
    28	
    29	Make the polling thread register the image first when needed, then the
    30	annotation. Keep it non-blocking (no foreground caller is gated on WBIA),
    31	keep one retry counter, keep one polling thread.
    32	
    33	## Non-goals
    34	
    35	- Modifying `IA.intakeMediaAssetsOneSpeciesMlService` to do image
    36	  registration at intake time. The polling thread can handle it
    37	  retroactively, which keeps the intake fast path simple.
    38	- Adding a parallel image-registration polling thread. Doubles the
    39	  background-thread inventory for no benefit; the single annotation
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
    71	`dto.mediaAssetAcmId` already carries the value `sendMediaAssetsForceId`
    72	puts in `image_uuid_list`.
    73	
    74	### Phase A additions
    75	
    76	`StartupWildbook.loadWbiaRegisterDto` (added in commit `c6ffe5d20`) already
    77	captures the annotation-side eligibility (`mediaAsset != null &&
    78	acmId != null && validForIdentification`). Add TWO image-side eligibility
    79	checks in the same block (mirroring what `sendMediaAssetsForceId` does
    80	at line 121-130, in the same order):
    81	
    82	```java
    83	if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
    84	    reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
    85	} else if (!WildbookIAM.validMediaAsset(ma)) {
    86	    reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
    87	}
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
   106	The c11 fix-pass refactor of Phase A is preserved (still short DB tx, no
   107	network).
   108	
   109	### New `iaImageIdsStrict(context)` in `WildbookIAM`
   110	
   111	Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
   112	Same shape:
   113	
   114	- Honors the existing 15-min `QueryCache`.
   115	- Throws `IOException` on fetch failure (vs. lenient variant which
   116	  swallows and returns empty list).
   117	- Uses a strict element parser (no silently-skipped malformed entries).
   118	
   119	Roughly 40 lines, parallel to the annotation version. Reuses the same
   120	`apiGetJSONArray` and `parseAnnotationIdsArrayStrict`-style helpers; the
   121	parser helper is duplicated rather than generalized because the cache key
   122	and endpoint path differ.
   123	
   124	### New `sendMediaAssetByDto(WbiaRegisterRequest dto)` in `WildbookIAM`
   125	
   126	Single-image variant of `sendMediaAssetsForceId`, DTO-only (no Shepherd
   127	parameter, no MediaAsset entity):
   128	
   129	```java
   130	public boolean sendMediaAssetByDto(WbiaRegisterRequest dto) {
   131	    String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
   132	    if (u == null) return false;
   133	    URL url;
   134	    try { url = new URL(u); } catch (MalformedURLException ex) { return false; }
   135	    HashMap<String, ArrayList> map = buildSingleImageRequestMap(dto);
   136	    try {
   137	        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   138	        // Validate status.success; the legacy WBIA wrapper returns
   139	        // {"status": {"success": true/false, ...}, "response": ...}
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
   161	    map.get("image_gps_lon_list").add(dto.imageLongitude);
   162	    return map;
   163	}
   164	```
   165	
   166	The pure helpers (`buildSingleImageRequestMap`, `isStatusSuccess`) are
   167	extracted so unit tests can verify the request shape without a network
   168	round-trip.
   169	
   170	### Phase B `registerOneByDto` modification
   171	
   172	Add Phase 0 at the entry; existing Phase 1 (annotation registration)
   173	moves down unchanged:
   174	
   175	```java
   176	public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
   177	    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
   178	
   179	    // ---- Phase 0: ensure WBIA knows about the image ----
   180	    List<String> knownImages;
   181	    try { knownImages = iaImageIdsStrict(context); }
   182	    catch (IOException ex) {
   183	        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
   184	        return WbiaRegisterOutcome.NETWORK_FAIL;
   185	    }
   186	    if (!knownImages.contains(dto.mediaAssetAcmId)) {
   187	        if (!sendMediaAssetByDto(dto)) {
   188	            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
   189	        }
   190	        // Invalidate the image-ids cache so the next annotation on this
   191	        // image sees the updated list — without this, the 15-min cache
   192	        // would still report the image as absent and we'd re-POST it
   193	        // every poll cycle.
   194	        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
   195	    }
   196	
   197	    // ---- Phase 1: existing annotation registration logic ----
   198	    List<String> knownAnnots;
   199	    try { knownAnnots = iaAnnotationIdsStrict(context); }
   200	    catch (IOException ex) { return NETWORK_FAIL; }
   201	    // ... rest of existing logic unchanged ...
   202	}
   203	```
   204	
   205	### Outcome semantics
   206	
   207	No new `WbiaRegisterOutcome` value. The 4 existing values handle every
   208	case cleanly:
   209	
   210	- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
   211	- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
   212	- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
   213	- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
   214	- Image was already in WBIA + Phase 1 succeeds → `REGISTERED_OK` or
   215	  `REGISTERED_ALREADY_PRESENT` (depending on the annotation side).
   216	
   217	Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
   218	counter increments on each NETWORK_FAIL. If WBIA can't be reached at all,
   219	the annotation parks after 10 attempts — same as today, just with image
   220	registration also having been attempted along the way.

 succeeded in 663ms:
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/main/java/org/ecocean/ia/MlServiceProcessor.java:337:                ann.setWbiaRegistered(Boolean.FALSE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:338:                ann.setWbiaRegisterAttempts(0);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:14:public class WbiaQueueUtil {
src/main/java/org/ecocean/ia/MatchResult.java:521:        Boolean wbiaReg = ann.getWbiaRegistered();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:528:     * registered" in the polling thread's already-present check.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:560:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:612:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:619:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:635:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:643:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:649:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;

 succeeded in 780ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:86:    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:264:    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:291:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:296:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:302:                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:307:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:327:        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:329:        System.out.println("sendAnnotations(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:331:        System.out.println("sendAnnotations() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:335:                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:339:            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:345:    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:371:                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:376:                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:382:                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:403:        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:405:        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:407:        System.out.println("sendAnnotationsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
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
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:86:    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:264:    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:291:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:296:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:302:                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:307:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:327:        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:329:        System.out.println("sendAnnotations(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:331:        System.out.println("sendAnnotations() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:335:                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:339:            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:345:    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:371:                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:376:                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:382:                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:403:        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:405:        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:407:        System.out.println("sendAnnotationsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
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
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:86:    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:264:    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:291:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:296:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:302:                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:307:                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:327:        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:329:        System.out.println("sendAnnotations(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:331:        System.out.println("sendAnnotations() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:335:                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:339:            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:345:    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:371:                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:376:                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:382:                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:403:        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:405:        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:407:        System.out.println("sendAnnotationsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
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
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/ia/MatchResult.java:521:        Boolean wbiaReg = ann.getWbiaRegistered();
src/main/java/org/ecocean/ia/MatchResult.java:522:        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:337:                ann.setWbiaRegistered(Boolean.FALSE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:338:                ann.setWbiaRegisterAttempts(0);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:337:                ann.setWbiaRegistered(Boolean.FALSE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:338:                ann.setWbiaRegisterAttempts(0);
src/main/java/org/ecocean/ia/MatchResult.java:521:        Boolean wbiaReg = ann.getWbiaRegistered();
src/main/java/org/ecocean/ia/MatchResult.java:522:        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java:112:                // ---- Phase 1: Get work items from server ----
src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java:171:                // ---- Phase 2: Execute comparisons ----
src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
src/main/java/org/ecocean/Annotation.java:80:    protected Boolean wbiaRegistered;
src/main/java/org/ecocean/Annotation.java:82:    // Failed-attempt counter. Polling filters wbiaRegisterAttempts < MAX so
src/main/java/org/ecocean/Annotation.java:83:    // chronically-failing rows park rather than spin forever.
src/main/java/org/ecocean/Annotation.java:84:    protected int wbiaRegisterAttempts = 0;
src/main/java/org/ecocean/Annotation.java:336:    public Boolean getWbiaRegistered() { return wbiaRegistered; }
src/main/java/org/ecocean/Annotation.java:337:    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:341:    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
src/main/java/org/ecocean/Annotation.java:343:    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
src/main/java/org/ecocean/Annotation.java:344:    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:345:    public void incrementWbiaRegisterAttempts() {
src/main/java/org/ecocean/Annotation.java:346:        this.wbiaRegisterAttempts++;
src/main/java/org/ecocean/identity/IBEISIA.java:148:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/identity/IBEISIA.java:167:    public static JSONObject __sendAnnotations(ArrayList<Annotation> anns, String context,
src/main/java/org/ecocean/identity/IBEISIA.java:172:            System.out.println("WARNING: sendAnnotations() called without IA primed");
src/main/java/org/ecocean/identity/IBEISIA.java:188:                System.out.println("WARNING: IBEISIA.sendAnnotations() skipping invalid " + ann);
src/main/java/org/ecocean/identity/IBEISIA.java:214:        System.out.println("sendAnnotations(): sending " + ct);
src/main/java/org/ecocean/identity/IBEISIA.java:818:                // would this ever recurse? seems like a 600 would only happen inside sendAnnotations for missing MediaAssets.
src/main/java/org/ecocean/identity/IBEISIA.java:823:                    __sendAnnotations(anns, context, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:834:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/identity/IBEISIA.java:835:        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
src/main/java/org/ecocean/identity/IBEISIA.java:917:            results.put("sendAnnotationsAsNeeded", sendAnnotationsAsNeeded(allAnns, myShepherd));
src/main/java/org/ecocean/identity/IBEISIA.java:3213:            results.put("sendAnnotations",
src/main/java/org/ecocean/identity/IBEISIA.java:3214:                sendAnnotationsNew(allAnns, myShepherd.getContext(), myShepherd));
src/main/java/org/ecocean/identity/IBEISIA.java:3685:    public static JSONObject sendAnnotationsNew(ArrayList<Annotation> anns, String context,
src/main/java/org/ecocean/identity/IBEISIA.java:3691:        return plugin.sendAnnotations(anns, true, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:3694:    public static JSONObject sendAnnotationsAsNeeded(ArrayList<Annotation> anns,
src/main/java/org/ecocean/identity/IBEISIA.java:3698:        Util.mark("sendAnnotationsAsNeeded -in- ", tt);
src/main/java/org/ecocean/identity/IBEISIA.java:3708:        Util.mark("sendAnnotationsAsNeeded 1 ", tt);
src/main/java/org/ecocean/identity/IBEISIA.java:3712:        Util.mark("sendAnnotationsAsNeeded 2-hs ", tt);
src/main/java/org/ecocean/identity/IBEISIA.java:3725:        Util.mark("sendAnnotationsAsNeeded 3-hs ", tt);
src/main/java/org/ecocean/identity/IBEISIA.java:3731:            Util.mark("sendAnnotationsAsNeeded 4 ", tt);
src/main/java/org/ecocean/identity/IBEISIA.java:3733:                rtn.put("sendAnnotations", plugin.sendAnnotations(annsToSend, false, myShepherd));
src/main/java/org/ecocean/identity/IBEISIA.java:3737:        Util.mark("sendAnnotationsAsNeeded -out- ", tt);
src/main/java/org/ecocean/StartupWildbook.java:49:    private static volatile ScheduledExecutorService wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:435:     * {@link org.ecocean.ia.plugin.WildbookIAM#sendAnnotationsForceId} in a
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:439:     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:453:        if (wbiaRegisterExecutor != null) {
src/main/java/org/ecocean/StartupWildbook.java:481:        wbiaRegisterExecutor = ses;
src/main/java/org/ecocean/StartupWildbook.java:485:        // Phase 1: query the pending list (Shepherd open, no network). Capture
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:496:            q.setOrdering("wbiaRegisterAttempts ascending");
src/main/java/org/ecocean/StartupWildbook.java:516:        // Phase 2: per-annotation registration. Each runs in its own short
src/main/java/org/ecocean/StartupWildbook.java:523:                wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:552:     *       advances naturally when MlServiceProcessor's Phase 1 calls
src/main/java/org/ecocean/StartupWildbook.java:671:            // (bumping REVISION) when its Phase 1 picks the work up. We
src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
src/main/java/org/ecocean/StartupWildbook.java:705:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:706:            loadWbiaRegisterDto(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:707:        if (dto == null) return;  // ineligible / already registered / parked
src/main/java/org/ecocean/StartupWildbook.java:713:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
src/main/java/org/ecocean/StartupWildbook.java:730:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:737:        persistWbiaRegisterResult(context, annId, outcome);
src/main/java/org/ecocean/StartupWildbook.java:743:     * Null cases: missing annotation, already registered, parked at max
src/main/java/org/ecocean/StartupWildbook.java:745:     * Ineligible annotations are parked here so they stop being polled.
src/main/java/org/ecocean/StartupWildbook.java:747:    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
src/main/java/org/ecocean/StartupWildbook.java:748:        loadWbiaRegisterDto(String context, String annId) {
src/main/java/org/ecocean/StartupWildbook.java:758:            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
src/main/java/org/ecocean/StartupWildbook.java:762:            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:767:            // annotation under its current state, so park it.
src/main/java/org/ecocean/StartupWildbook.java:776:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
src/main/java/org/ecocean/StartupWildbook.java:778:                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:788:            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:789:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
src/main/java/org/ecocean/StartupWildbook.java:795:            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
src/main/java/org/ecocean/StartupWildbook.java:810:    private static void persistWbiaRegisterResult(String context, String annId,
src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
src/main/java/org/ecocean/StartupWildbook.java:821:            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
src/main/java/org/ecocean/StartupWildbook.java:830:                    // parked by a racing poller: stuck-at-attempts==MAX
src/main/java/org/ecocean/StartupWildbook.java:832:                    ann.setWbiaRegistered(Boolean.TRUE);
src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
src/main/java/org/ecocean/StartupWildbook.java:837:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:838:                        // Already parked by another path; do not increment past MAX.
src/main/java/org/ecocean/StartupWildbook.java:841:                    ann.incrementWbiaRegisterAttempts();
src/main/java/org/ecocean/StartupWildbook.java:842:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:851:            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
src/main/java/org/ecocean/StartupWildbook.java:871:        // flight. shutdownWbiaRegisterExecutor signals shutdown by
src/main/java/org/ecocean/StartupWildbook.java:875:        shutdownWbiaRegisterExecutor();
src/main/java/org/ecocean/StartupWildbook.java:886:    private static void shutdownWbiaRegisterExecutor() {
src/main/java/org/ecocean/StartupWildbook.java:887:        ScheduledExecutorService ses = wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:889:        wbiaRegisterExecutor = null;
src/main/java/org/ecocean/StartupWildbook.java:890:        System.out.println("STOPPING: StartupWildbook.wbiaRegisterExecutor");
src/main/java/org/ecocean/servlet/GrothMatchServlet.java:81:        // Phase 1: Short DB transaction to load query encounter spots
src/main/java/org/ecocean/servlet/GrothMatchServlet.java:119:        // Phase 2: CPU-heavy Groth matching — no DB transaction needed
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:149:	                IBEISIA.sendAnnotationsNew(annList, iaContext, iaShepherd);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '420,466p;555,655p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '429,505p;516,565p;690,852p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"MlServiceProcessorTest|waitAndRunMatch|deferredMatch|OpenSearch\" src/test/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java | sed -n '1,140p'" in /mnt/c/Wildbook-clean2
 succeeded in 234ms:
   429	     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
   430	     * queue or reconcile servlet is needed.
   431	     *
   432	     * <p>Per cycle (~30s): query annotations with
   433	     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
   434	     * up to a small batch limit. For each, call
   435	     * {@link org.ecocean.ia.plugin.WildbookIAM#sendAnnotationsForceId} in a
   436	     * per-annotation Shepherd transaction (so one slow WBIA call blocks
   437	     * only one slot, not the entire batch). On success: set
   438	     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
   439	     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
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
   526	            }
   527	            registerOneAnnotationWithWbia(context, annId);
   528	        }
   529	    }
   530	
   531	    /**
   532	     * ml-service migration v2 §commit #12. Once-at-startup pass that
   533	     * detects MediaAssets stuck in {@code processing-mlservice} past a
   534	     * threshold (worker presumably died mid-detection due to the
   535	     * at-most-once FileQueue semantics) and re-enqueues them through
   536	     * the normal routing layer.
   537	     *
   538	     * <p>Safe under any active worker because:</p>
   539	     * <ul>
   540	     *   <li>The re-check inside reconcileOneStaleAsset uses the fresh
   541	     *       Shepherd's current state; if another worker has already
   542	     *       progressed the asset, the status will no longer be
   543	     *       {@code processing-mlservice} and the reconciler skips.</li>
   544	     *   <li>MlServiceProcessor's Phase 4 idempotency check (composite of
   545	     *       mediaAsset + predictModelId + bboxKey + thetaKey) prevents
   546	     *       duplicate annotation creation if the dead worker had already
   547	     *       persisted some results.</li>
   548	     *   <li>The reconciler intentionally does NOT bump REVISION after a
   549	     *       successful re-enqueue, because doing so from the stale
   550	     *       managed MediaAsset instance could overwrite progress made by
   551	     *       a fast queue consumer between enqueue and commit. REVISION
   552	     *       advances naturally when MlServiceProcessor's Phase 1 calls
   553	     *       setDetectionStatus on the picked-up job. A restart that
   554	     *       happens between enqueue and consumer pickup can re-enqueue
   555	     *       a duplicate job; Phase 4 idempotency (see previous bullet)
   556	     *       bounds the impact to wasted work, not data corruption.</li>
   557	     * </ul>
   558	     *
   559	     * <p>Threshold default: 1 hour. Longer than any healthy detection
   560	     * job's worst-case duration; short enough that operators don't wait
   561	     * days for recovery.</p>
   562	     */
   563	    private static final long STALE_MLSERVICE_THRESHOLD_MS = 60L * 60L * 1000L;
   564	
   565	    private static void runStaleMlServiceReconciliation(String context) {
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

 succeeded in 237ms:
   420	    // ------------------------------------------------------------------
   421	
   422	    /**
   423	     * Outcome of a Phase-B WBIA registration attempt.
   424	     * REGISTERED_OK              - POST succeeded, ids match.
   425	     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
   426	     * NETWORK_FAIL               - GET or POST threw / non-2xx.
   427	     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
   428	     *                              (id mismatch, length mismatch, missing field).
   429	     */
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
   466	    }
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
   651	        }
   652	        return WbiaRegisterOutcome.REGISTERED_OK;
   653	    }
   654	
   655	    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)

 succeeded in 228ms:
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

 succeeded in 378ms:
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:40:public class MediaAssetOpenSearchTest {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:16: * v2 commit #7: shape-of-query tests for OpenSearch.buildIdEligibilityQuery.
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:18: * OpenSearch (or a heavily mocked RestClient); the eligibility-query JSON
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:21: * Uses the OpenSearch idiomatic `ids` query rather than the generic `terms`
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:22: * query on `_id`, per OpenSearch docs.
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:24:class OpenSearchVisibilityTest {
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:29:        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:45:        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:54:        // OpenSearch query-cache keys.
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:59:        JSONArray values = OpenSearch.buildIdEligibilityQuery(ids)
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:69:        // Sanity check: the JSON shape with one element matches the OpenSearch
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:74:        JSONObject actual = OpenSearch.buildIdEligibilityQuery(ids);
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:40:public class OccurrenceOpenSearchTest {
src/test/java/org/ecocean/OpenSearchTest.java:15:import org.opensearch.client.transport.OpenSearchTransport;
src/test/java/org/ecocean/OpenSearchTest.java:22:import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
src/test/java/org/ecocean/OpenSearchTest.java:23:import org.opensearch.client.opensearch.OpenSearchClient;
src/test/java/org/ecocean/OpenSearchTest.java:30:import org.ecocean.OpenSearch;
src/test/java/org/ecocean/OpenSearchTest.java:58:public class OpenSearchTest {
src/test/java/org/ecocean/OpenSearchTest.java:61:    OpenSearchClient osClient;
src/test/java/org/ecocean/OpenSearchTest.java:62:    OpenSearch os;
src/test/java/org/ecocean/OpenSearchTest.java:66:        osClient = mock(OpenSearchClient.class);
src/test/java/org/ecocean/OpenSearchTest.java:67:        OpenSearch.client = osClient;
src/test/java/org/ecocean/OpenSearchTest.java:69:        OpenSearch.restClient = restClient;
src/test/java/org/ecocean/OpenSearchTest.java:70:        os = new OpenSearch();
src/test/java/org/ecocean/OpenSearchTest.java:74:    void verifyOpenSearch() {
src/test/java/org/ecocean/OpenSearchTest.java:86:        OpenSearchIndicesClient mockedIndicesClient = mock(OpenSearchIndicesClient.class);
src/test/java/org/ecocean/OpenSearchTest.java:98:        // now unset cache so it has to ask OpenSearch about index
src/test/java/org/ecocean/OpenSearchTest.java:99:        OpenSearch.INDEX_EXISTS_CACHE.remove("encounter");
src/test/java/org/ecocean/OpenSearchTest.java:101:        assertTrue(OpenSearch.INDEX_EXISTS_CACHE.get("encounter"));
src/test/java/org/ecocean/OpenSearchTest.java:124:        assertFalse(OpenSearch.INDEX_EXISTS_CACHE.containsKey("encounter"));
src/test/java/org/ecocean/OpenSearchTest.java:128:        OpenSearch.INDEX_EXISTS_CACHE.remove("encounter");  // force OpenSearch check (which will throw above)
src/test/java/org/ecocean/EmbeddingTest.java:52:    // subtask outcomes) need a real Annotation/Shepherd/OpenSearch to
src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java:9: * v2 commit #5: MediaAsset.setDetectionStatus bumps REVISION so the OpenSearch
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:34:import org.ecocean.OpenSearch;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:66: * Uses Testcontainers for PostgreSQL and OpenSearch, WireMock for external image mocking,
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:108:        // Log OpenSearch connection details
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:109:        System.out.println("OpenSearch started at: " + getOpenSearchUrl());
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:147:        OpenSearch.initializeClient(new HttpHost(opensearch.getHost(),
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:164:        // Manually trigger OpenSearch indexing (don't wait for background task which has 2 min delay)
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:165:        OpenSearch.updateEncounterIndexes("context0");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:175:        System.out.println("OpenSearch container will be stopped automatically");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:201:        System.out.println("\n--- Test: Search API with OpenSearch ---");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:203:        // Wait for OpenSearch to be ready and index to be created
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:732:     * Get the OpenSearch base URL for the running container.
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:734:     * @return OpenSearch base URL (e.g., "http://localhost:12345")
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:736:    private static String getOpenSearchUrl() {
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1011:     * OpenSearch connection info for debugging.
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1013:    @Test @Order(0) void testOpenSearchConnection() {
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1014:        System.out.println("\n--- Test: OpenSearch Connection ---");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1015:        System.out.println("OpenSearch URL: " + getOpenSearchUrl());
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1016:        System.out.println("OpenSearch Port 9200: " + opensearch.getMappedPort(9200));
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1017:        System.out.println("OpenSearch Port 9300: " + opensearch.getMappedPort(9300));
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1019:        assertTrue(opensearch.isRunning(), "OpenSearch container should be running");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1021:        // Verify we can connect to OpenSearch cluster health endpoint
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1024:            .get(getOpenSearchUrl() + "/_cluster/health")
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1029:        System.out.println("OpenSearch connection test passed");
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:51: *   <li>JDO/OpenSearch/FileQueue handoff (those still need a Tomcat
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:16: * JDO mutations, and live OpenSearch — those are reviewable by diff and
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:30:class MlServiceProcessorTest {

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MlServiceProcessorTest.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
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

exec
/bin/bash -lc "rg -n \"mlServiceV2DeferredMatch|deferredMatch|mlServiceV2\" src/main/java/org/ecocean/servlet/IAGateway.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 802ms:
src/main/java/org/ecocean/ia/MlServiceProcessor.java:30: * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:        if (jobData.optBoolean("deferredMatch", false)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:683:        payload.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:684:        payload.put("deferredMatch", true);
src/main/java/org/ecocean/servlet/IAGateway.java:637:        if (jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:642:            System.out.println("IAGateway: mlServiceV2 job " +
src/main/java/org/ecocean/servlet/IAGateway.java:792:                            jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:793:                            // mlServiceV2 retries must land on the detection
src/main/java/org/ecocean/ia/MlServiceProcessor.java:30: * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:        if (jobData.optBoolean("deferredMatch", false)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:683:        payload.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:684:        payload.put("deferredMatch", true);
src/main/java/org/ecocean/servlet/IAGateway.java:637:        if (jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:642:            System.out.println("IAGateway: mlServiceV2 job " +
src/main/java/org/ecocean/servlet/IAGateway.java:792:                            jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:793:                            // mlServiceV2 retries must land on the detection
src/main/java/org/ecocean/ia/IA.java:228:     * topTask; each emits a {@code mlServiceV2:true} payload to the
src/main/java/org/ecocean/ia/IA.java:303:        qjob.put("mlServiceV2", true);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '628,646p'" in /mnt/c/Wildbook-clean2
 succeeded in 235ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '166,207p;990,1013p'" in /mnt/c/Wildbook-clean2
 succeeded in 272ms:
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

2026-05-18T19:43:42.386489Z ERROR codex_core::tools::router: error=write_stdin failed: stdin is closed for this session; rerun exec_command with tty=true to keep stdin open
  ✓ Verify cited code paths and constants
  ✓ Check proposed SQL/schema assumptions
  → Review deferred-match sequencing and test gaps
  • Summarize findings with severity
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '300,370p'" in /mnt/c/Wildbook-clean2
 succeeded in 252ms:
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

  ✓ Verify cited code paths and constants
  ✓ Check proposed SQL/schema assumptions
  ✓ Review deferred-match sequencing and test gaps
  → Summarize findings with severity
  ✓ Verify cited code paths and constants
  ✓ Check proposed SQL/schema assumptions
  ✓ Review deferred-match sequencing and test gaps
  ✓ Summarize findings with severity
codex
**Findings**

1. **Blocker: deferred-match payload flag is wrong in the design.**  
The pseudocode enqueues only `mlServiceV2DeferredMatch: true`, but nothing currently consumes that flag. `IAGateway` dispatches v2 jobs only when `mlServiceV2` is true, and `MlServiceProcessor` enters deferred mode only when `deferredMatch` is true: [IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:637), [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:55). `requeueJob` also sends jobs back to the detection queue only for `mlServiceV2`: [IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:790).  
Fix: keep the existing flags in the deferred payload:
`mlServiceV2: true` and `deferredMatch: true`. `mlServiceV2DeferredMatch` can be an extra diagnostic marker, but not the routing contract. Add a test that captured deferred payloads contain both routing flags and are published via `IAGateway.requeueJob(payload, true)`.

2. **Major: method-null legacy vector configs are still not aligned.**  
The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
Fix: either conditionally omit `e."METHOD" = ?` and the OpenSearch `embeddings.method` term when method is blank, or explicitly reject api-endpoint configs that cannot derive a method before the gate. Add tests for `method == null`, not only `methodVersion == null`.

**Confirmations**

The round-4 open-question answers look right:

- `QueryCacheFactory.safeInvalidate(context, name)` is the right placement.
- `MatchEligibilityQuery` as package-visible in `org.ecocean.ia` is better than hiding schema-sensitive SQL inside the gate.
- `topTask == null` should WARN and degrade to caller-only visibility.

The core Track 2 shape is otherwise sound. The SQL join shape matches existing repo precedent in [ImportTask.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:781), the raw `Task` status accessor is necessary because `getStatus(Shepherd)` mutates timed-out tasks, and the two-wait split is the right correction.

Review-only pass; I did not write files or run tests.
tokens used
214,215
**Findings**

1. **Blocker: deferred-match payload flag is wrong in the design.**  
The pseudocode enqueues only `mlServiceV2DeferredMatch: true`, but nothing currently consumes that flag. `IAGateway` dispatches v2 jobs only when `mlServiceV2` is true, and `MlServiceProcessor` enters deferred mode only when `deferredMatch` is true: [IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:637), [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:55). `requeueJob` also sends jobs back to the detection queue only for `mlServiceV2`: [IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:790).  
Fix: keep the existing flags in the deferred payload:
`mlServiceV2: true` and `deferredMatch: true`. `mlServiceV2DeferredMatch` can be an extra diagnostic marker, but not the routing contract. Add a test that captured deferred payloads contain both routing flags and are published via `IAGateway.requeueJob(payload, true)`.

2. **Major: method-null legacy vector configs are still not aligned.**  
The design handles `methodVersion == null`, but not `method == null`. Existing matching treats `api_endpoint`-only configs as vector configs: [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:269). The legacy fallback can still leave `method` null: [MLService.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:348). `Annotation.getMatchQuery` omits the method predicate when method is null: [Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1205).  
Fix: either conditionally omit `e."METHOD" = ?` and the OpenSearch `embeddings.method` term when method is blank, or explicitly reject api-endpoint configs that cannot derive a method before the gate. Add tests for `method == null`, not only `methodVersion == null`.

**Confirmations**

The round-4 open-question answers look right:

- `QueryCacheFactory.safeInvalidate(context, name)` is the right placement.
- `MatchEligibilityQuery` as package-visible in `org.ecocean.ia` is better than hiding schema-sensitive SQL inside the gate.
- `topTask == null` should WARN and degrade to caller-only visibility.

The core Track 2 shape is otherwise sound. The SQL join shape matches existing repo precedent in [ImportTask.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:781), the raw `Task` status accessor is necessary because `getStatus(Shepherd)` mutates timed-out tasks, and the two-wait split is the right correction.

Review-only pass; I did not write files or run tests.
