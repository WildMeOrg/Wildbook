OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3c8e-52a1-7312-ba19-0afff939d3f3
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

# Design: fix empty match prospects on bulk imports (revision 4, post-Codex round 3)

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
            siblingMa.getId() == mas.get(0).getId()) {
            return child;
        }
    }
    return null;
}
```

**Use raw persisted status** (Codex round-3 Major): call a raw
accessor on the Task field, not `Task.getStatus(Shepherd)` —
`Task.getStatus(Shepherd)` is derived and can mutate timed-out
tasks as a side effect (`Task.java:597`), which we don't want
inside the gate. The right field is whatever `statusInEndState()`
at `Task.java:85` reads (`Task.status` getter without the
Shepherd-driven mutation). Either-being-terminal is sufficient.

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

## Open questions for Codex (round 4)

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

Remaining open questions (likely minor at this point):

1. **Naming the safe-invalidate helper.** Inline in `WildbookIAM`
   or extract to `QueryCacheFactory.safeInvalidate(context, name)`?
   The latter is reusable for any future caller; the former
   keeps WBIA changes local. My instinct: extract — null-safe
   invalidation is a generally-useful primitive.
2. **Direct SQL placement.** The eligibility query joins
   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
   is fine. Where does it live? Options:
   (a) Static method on a new `MatchEligibilityQuery` utility class.
   (b) Static method on `Annotation` next to `getMatchingSetQuery`.
   (c) Private helper inside `MatchVisibilityGateImpl`.
   My instinct: (c) — keep it co-located with its sole caller.
3. **Failure mode for `topTask == null` after child resolution.**
   The child Task's parent should always be a topTask in v2, but
   if for some reason it's null (corrupted state, race during
   topTask deletion), the gate falls through to waiting only on
   `callerAnnotationIds`. Confirm this degrades cleanly enough.
4. **Anything else** — design smells, sequencing bugs, test
   gaps, missing failure cases. If round 4 finds only minor
   items, the design is locked.

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
I’ll treat this as a review, so I’m going to verify the design against the current branch rather than relying on the line references in the doc. I’ll only read files and command output.
  → Verify current branch and locate referenced code paths
  • Check Track 1 assumptions against WildbookIAM/WBIA registration code
  • Check Track 2 assumptions against task, ml-service, OpenSearch, and embedding code
  • Identify blockers, majors, minors, and answer open questions
exec
/bin/bash -lc 'git status --short --branch' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "waitAndRunMatch|runDeferredMatch|enqueueDeferredMatch|runMatchProspects|markTaskCompleted|mlServiceV2DeferredMatch" src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "waitForVisibility|queryCount|indexRefresh|skipAutoIndexing|waitForAnnotationMatchableIds" src/main/java/org/ecocean/OpenSearch.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"statusInEndState|getStatus\\(|setStatus\\(|children|parent|objectMediaAssets\" src/main/java/org/ecocean/identity/Task.java src/main/java/org/ecocean/ia/IA.java" in /mnt/c/Wildbook-clean2
 succeeded in 240ms:
56:            return runDeferredMatch(jobData);
109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
172:                markTaskCompleted(task);
229:                markTaskCompleted(task);
234:                markTaskCompleted(task);
279:            markTaskCompleted(task);
366:            markTaskCompleted(task);
395:                markTaskCompleted(task);
405:            markTaskCompleted(task);
418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
423:                enqueueDeferredMatch(annotationIds, taskId);
427:            enqueueDeferredMatch(annotationIds, taskId);
430:        return runMatchProspects(annotationIds, taskId, matchConfig);
433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
441:        return runMatchProspects(annotationIds, taskId, matchConfig);
444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
447:            markTaskCompleted(taskId);
452:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
649:    private void markTaskCompleted(String taskId) {
651:        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
654:            markTaskCompleted(Task.load(taskId, shep));
668:    private void markTaskCompleted(Task task) {
681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);

 succeeded in 238ms:
139:    public static boolean skipAutoIndexing() {
140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
467:    // Callers (typically waitForVisibility) follow with a visibility poll.
468:    public void indexRefresh(final String indexName)
486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
513:        if (skipAutoIndexing()) {
515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
519:        indexRefresh(indexName);
525:            int seen = queryCount(indexName, query);
552:    public int queryCount(String indexName, final JSONObject query)
563:            System.out.println("queryCount() on index " + indexName + " using query=" + query +
566:            throw new IOException("queryCount() failed");

 exited 2 in 286ms:
rg: src/main/java/org/ecocean/identity/Task.java: No such file or directory (os error 2)
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
src/main/java/org/ecocean/ia/IA.java:149:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:169:        // summary code that reads topTask.objectMediaAssets keeps working).
src/main/java/org/ecocean/ia/IA.java:271:     * the task references the MediaAsset through objectMediaAssets),
src/main/java/org/ecocean/ia/IA.java:363:    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
src/main/java/org/ecocean/ia/IA.java:369:        final Task parentTask, boolean fastlane) {
src/main/java/org/ecocean/ia/IA.java:372:        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
src/main/java/org/ecocean/ia/IA.java:374:        // Task topTask = (parentTask==null) ? new Task() : parentTask;
src/main/java/org/ecocean/ia/IA.java:376:        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
src/main/java/org/ecocean/ia/IA.java:380:        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
src/main/java/org/ecocean/ia/IA.java:384:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:419:            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
src/main/java/org/ecocean/ia/IA.java:420:            if (parentTask != null && parentTask.getParameters() != null) {
src/main/java/org/ecocean/ia/IA.java:421:                newTaskParams = parentTask.getParameters();
src/main/java/org/ecocean/ia/IA.java:444:                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
src/main/java/org/ecocean/ia/IA.java:554:                    " for (parent) " + topTask);

codex
The first pass found one path mismatch in the bundle: `Task.java` is not under `org/ecocean/identity` in this repo. I’m widening the reads now to the actual task model and the surrounding IA/OpenSearch code.
exec
/bin/bash -lc "rg --files src/main/java | rg '/Task\\.java"'$'"'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '450,580p' src/main/java/org/ecocean/OpenSearch.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '120,320p' src/main/java/org/ecocean/ia/IA.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '400,720p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
 succeeded in 193ms:
    throws IOException {
        if (scrollData == null) throw new IOException("null data passed");
        String scrollId = scrollData.optString("_scroll_id", null);
        if (scrollData == null) throw new IOException("no _scroll_id");
        JSONObject data = new JSONObject();
        data.put("scroll", SEARCH_SCROLL_TIME);
        data.put("scroll_id", scrollId);
        Request searchRequest = new Request("POST", "_search/scroll");
        searchRequest.setJsonEntity(data.toString());
        String rtn = getRestResponse(searchRequest);
        return new JSONObject(rtn);
    }

    // ml-service migration v2 (commit #7): force pending writes in `indexName`
    // through Lucene's refresh boundary so they are searchable. Synchronous;
    // returns after targeted shards have completed the refresh. NOT a Wildbook
    // queue drain — IndexingManager may still have unindexed entities queued.
    // Callers (typically waitForVisibility) follow with a visibility poll.
    public void indexRefresh(final String indexName)
    throws IOException {
        if (!isValidIndexName(indexName))
            throw new IOException("invalid index name: " + indexName);
        Request req = new Request("POST", indexName + "/_refresh");
        getRestResponse(req);   // discard body; non-2xx surfaces as IOException
    }

    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
    // can see every id in `ids` in `indexName`. Used by MlServiceProcessor
    // (commit #9) post-persist to avoid running findMatchProspects against an
    // index that doesn't yet contain the freshly-written annotations.
    //
    // On entry:
    //   - normalizes `ids` to a Set (drops nulls and duplicates so they can't
    //     prevent the count check from ever succeeding);
    //   - calls _refresh once (synchronous; pushes pending writes through
    //     Lucene's refresh boundary);
    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
    //     every poll return zero hits regardless of how long we wait.
    //
    // Then polls a _count eligibility query with exponential backoff (start
    // 100ms, double, cap 1s) until count >= |normalized ids| OR the total
    // wait reaches timeoutMs. Returns true on visible-success, false on
    // timeout. Caller decides what to do on false (e.g. enqueue a deferred-
    // match job rather than match against a partial index).
    //
    // Does NOT try to drain the Wildbook IndexingManager queue. That queue
    // may contain unrelated entities; queue-depth zero doesn't imply the
    // specific IDs are queryable. Polling visibility IS the correctness gate.
    public boolean waitForVisibility(String indexName, Collection<String> ids,
        long timeoutMs)
    throws IOException {
        if (!isValidIndexName(indexName))
            throw new IOException("invalid index name: " + indexName);
        if (ids == null || ids.isEmpty()) return true;

        // Normalize: drop nulls + duplicates so the count comparison is
        // against the true number of distinct documents we expect to see.
        Set<String> targetIds = new LinkedHashSet<String>();
        for (String id : ids) {
            if (id != null) targetIds.add(id);
        }
        if (targetIds.isEmpty()) return true;

        if (skipAutoIndexing()) {
            System.out.println(
                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
                "— every poll will return zero hits regardless of wait time.");
        }

        indexRefresh(indexName);

        JSONObject query = buildIdEligibilityQuery(targetIds);
        long deadline = System.currentTimeMillis() + timeoutMs;
        long sleepMs = 100;
        while (true) {
            int seen = queryCount(indexName, query);
            if (seen >= targetIds.size()) return true;
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;
            try {
                Thread.sleep(Math.min(sleepMs, remaining));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
            sleepMs = Math.min(sleepMs * 2, 1000);
        }
    }

    // Package-visible for testing. Returns the _count-shaped query body that
    // filters on _id ∈ ids, using OpenSearch's idiomatic `ids` query.
    static JSONObject buildIdEligibilityQuery(Set<String> ids) {
        JSONArray idArr = new JSONArray();
        for (String id : ids) idArr.put(id);
        JSONObject query = new JSONObject();
        query.put("query",
            new JSONObject().put("ids",
                new JSONObject().put("values", idArr)));
        return query;
    }

    // when you only care about how many this would return
    public int queryCount(String indexName, final JSONObject query)
    throws IOException {
        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
        Request searchRequest = new Request("POST", indexName + "/_count");
        JSONObject cleanedQuery = new JSONObject(query.toString());
        cleanedQuery.remove("_source"); // invalid for a _count query
        searchRequest.setJsonEntity(cleanedQuery.toString());
        JSONObject res = new JSONObject();
        try {
            res = new JSONObject(getRestResponse(searchRequest));
        } catch (Exception ex) {
            System.out.println("queryCount() on index " + indexName + " using query=" + query +
                " failed with: " + ex);
            ex.printStackTrace();
            throw new IOException("queryCount() failed");
        }
        return res.optInt("count", -1);
    }

    public Map<String, Long> getAllVersions(String indexName)
    throws IOException {
        Map<String, Long> versions = new HashMap<String, Long>();
        boolean reachedEnd = false;
        JSONObject query = new JSONObject("{\"sort\":[{\"version\": \"asc\"}]}");
        JSONObject res = queryRawScroll(indexName, query, 2000);

        while (!reachedEnd) {
            JSONObject outerHits = res.optJSONObject("hits");
            if (outerHits == null) throw new IOException("outer hits failed");

 succeeded in 192ms:

            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
            shep.getPM().makePersistent(emb);
            markTaskCompleted(task);
            annotationIds.add(ann.getId());
            shep.commitDBTransaction();
            return PersistResult.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
                ex.getMessage()));
        } finally {
            shep.rollbackAndClose();
        }
    }

    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        try {
            OpenSearch os = new OpenSearch();
            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
                enqueueDeferredMatch(annotationIds, taskId);
                return MlServiceJobOutcome.ok(annotationIds);
            }
        } catch (IOException ex) {
            enqueueDeferredMatch(annotationIds, taskId);
            return MlServiceJobOutcome.ok(annotationIds);
        }
        return runMatchProspects(annotationIds, taskId, matchConfig);
    }

    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
        if (jobData == null) {
            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
        }
        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
        String taskId = jobData.optString("taskId", null);
        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
        return runMatchProspects(annotationIds, taskId, matchConfig);
    }

    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            markTaskCompleted(taskId);
            return MlServiceJobOutcome.ok(new ArrayList<String>());
        }

        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "runMatchProspects");
        try {
            shep.beginDBTransaction();
            List<Annotation> anns = new ArrayList<Annotation>();
            for (String annId : annotationIds) {
                Annotation ann = shep.getAnnotation(annId);
                if (ann != null) anns.add(ann);
            }
            if (anns.isEmpty()) {
                Task task = Task.load(taskId, shep);
                markTaskDroppedStale(shep, task, "annotations missing");
                shep.commitDBTransaction();
                return MlServiceJobOutcome.stale("annotations missing");
            }

            Task parent = Task.load(taskId, shep);
            Task matchTask = (parent == null) ? new Task() : new Task(parent);
            matchTask.setObjectAnnotations(anns);
            matchTask.addParameter("mlServiceV2Match", true);
            shep.getPM().makePersistent(matchTask);
            // findMatchProspects returns false when the match config is not
            // a vector config or matchConfig is null. Don't leave the match
            // task without a terminal status — mark the parent task error.
            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
            if (!ran) {
                matchTask.setStatus("error");
                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
                    "findMatchProspects rejected match config: " +
                    (matchConfig == null ? "null" : matchConfig.toString()));
                matchTask.setCompletionDateInMilliseconds();
                // Update the parent task in this same transaction (parent is
                // already loaded above) so the two updates commit atomically.
                // Splitting across transactions risks leaving the parent
                // "completed" if the second commit fails or the JVM dies.
                if (parent != null) {
                    markTaskError(parent, "INVALID_MATCH_CONFIG",
                        "no usable vector match config");
                }
                shep.commitDBTransaction();
                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
                    "no usable vector match config");
            }
            shep.commitDBTransaction();
            return MlServiceJobOutcome.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
        } finally {
            shep.rollbackAndClose();
        }
    }

    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
        String code = ex == null ? null : ex.getCode();
        String message = ex == null ? null : ex.getMessage();
        if ("INVALID".equals(code) || "SUCCESS_FALSE".equals(code)) {
            return MlServiceJobOutcome.validationError(code, message);
        }
        if ("TIMEOUT".equals(code) || "NETWORK".equals(code) || "RATE_LIMITED".equals(code)
            || "SERVER_ERROR".equals(code) || "CLIENT_ERROR".equals(code)) {
            return MlServiceJobOutcome.networkError(code, message);
        }
        return MlServiceJobOutcome.networkError("UNKNOWN", message);
    }

    static String bboxKey(double[] bbox) {
        if (bbox == null || bbox.length != 4) return null;
        return Math.round(bbox[0]) + ":" + Math.round(bbox[1]) + ":" + Math.round(bbox[2]) +
            ":" + Math.round(bbox[3]);
    }

    static String thetaKey(double theta) {
        return String.format(Locale.US, "%.4f", theta);
    }

    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
        String bboxKey, String thetaKey) {
        if (ma == null) return null;
        for (Annotation ann : ma.getAnnotations()) {
            if (ann == null) continue;
            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
            if (!sameString(bboxKey, ann.getBboxKey())) continue;
            if (!sameString(thetaKey, ann.getThetaKey())) continue;
            return ann;
        }
        return null;
    }

    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
        if (!Util.stringExists(taxonomyString)) return null;
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        if (iac == null) return null;
        Taxonomy taxy = shep.getOrCreateTaxonomy(taxonomyString, false);
        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
        if (configs == null || configs.length() == 0) return null;
        JSONObject mlConfig = configs.optJSONObject(0);
        if (mlConfig == null) return null;
        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
        return new ConfigPair(mlConfig, matchConfig);
    }

    private JSONObject defaultMatchConfig(IAJsonProperties iac, Taxonomy taxy,
        JSONObject mlConfig) {
        JSONObject matchConfig = null;
        JSONArray identConfigs = iac.getIdentConfig(taxy);
        if (identConfigs != null) {
            for (int i = 0; i < identConfigs.length(); i++) {
                JSONObject entry = identConfigs.optJSONObject(i);
                if (entry == null) continue;
                if (entry.optBoolean("default", false)
                    && "vector".equals(entry.optString("pipeline_root", null))) {
                    matchConfig = new JSONObject(entry.toString());
                    break;
                }
            }
        }
        if (matchConfig == null) matchConfig = new JSONObject();
        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
        }
        return matchConfig;
    }

    private JSONObject inferMatchConfig(List<String> annotationIds) {
        if (annotationIds == null || annotationIds.isEmpty()) return null;
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
        try {
            shep.beginDBTransaction();
            for (String annId : annotationIds) {
                Annotation ann = shep.getAnnotation(annId);
                if (ann == null) continue;
                Embedding emb = ann.getAnEmbedding();
                if (emb != null) {
                    JSONObject config = new JSONObject();
                    config.put("method", emb.getMethod());
                    config.put("version", emb.getMethodVersion());
                    config.put("pipeline_root", "vector");
                    return config;
                }
            }
            return null;
        } finally {
            shep.rollbackAndClose();
        }
    }

    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
        JSONObject mlConfig) {
        JSONObject config = (matchConfig == null) ? new JSONObject()
            : new JSONObject(matchConfig.toString());
        if (embeddingSource != null) {
            if (!Util.stringExists(config.optString("method", null))
                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
                config.put("method", embeddingSource.optString("embedding_model_id"));
            }
            if (!Util.stringExists(config.optString("version", null))
                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
                config.put("version", embeddingSource.optString("embedding_model_version"));
            }
        }
        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
        }
        return config;
    }

    private String detectionStaleReason(MediaAsset ma, Encounter enc, String encounterId) {
        if (ma == null) return "media asset missing";
        if (Util.stringExists(encounterId) && enc == null) return "encounter missing";
        String status = ma.getDetectionStatus();
        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
        return null;
    }

    private String effectiveTaxonomyString(String taxonomyString, Encounter enc) {
        if (Util.stringExists(taxonomyString)) return taxonomyString;
        if (enc != null) return enc.getTaxonomyString();
        return null;
    }

    private void markTaskError(String taskId, String code, String message) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "markTaskError");
        try {
            shep.beginDBTransaction();
            Task task = Task.load(taskId, shep);
            markTaskError(task, code, message);
            shep.commitDBTransaction();
        } finally {
            shep.rollbackAndClose();
        }
    }

    private void markTaskCompleted(String taskId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
        try {
            shep.beginDBTransaction();
            markTaskCompleted(Task.load(taskId, shep));
            shep.commitDBTransaction();
        } finally {
            shep.rollbackAndClose();
        }
    }

    private void markTaskError(Task task, String code, String message) {
        if (task == null) return;
        task.setStatus(IBEISIA.STATUS_ERROR);
        task.setStatusDetailsAddError(code == null ? "UNKNOWN" : code, message);
        task.setCompletionDateInMilliseconds();
    }

    private void markTaskCompleted(Task task) {
        if (task == null) return;
        task.setStatus("completed");
        task.setCompletionDateInMilliseconds();
    }

    private void markTaskDroppedStale(Shepherd shep, Task task, String reason) {
        if (task == null) return;
        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
        task.setStatusDetailsAddLog(reason);
        task.setCompletionDateInMilliseconds();
    }

    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
        JSONObject payload = new JSONObject();
        payload.put("mlServiceV2", true);
        payload.put("deferredMatch", true);
        payload.put("annotationIds", new JSONArray(annotationIds));
        if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
        // Carry __context in the payload so the dispatcher's
        // jobj.optString("__context", "context0") fallback at IAGateway.java
        // doesn't silently route the deferred-match into context0 when this
        // processor is running in a non-default context.
        payload.put("__context", context);
        try {
            IAGateway.addToDetectionQueue(context, payload.toString());
        } catch (IOException ex) {
            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
        }
    }

    private static JSONObject featureParams(double[] bbox, double theta, String viewpoint) {
        JSONObject params = new JSONObject();
        params.put("x", bbox[0]);
        params.put("y", bbox[1]);
        params.put("width", bbox[2]);
        params.put("height", bbox[3]);
        params.put("theta", theta);
        params.put("viewpoint", viewpoint);
        return params;
    }

    private static double[] parseBbox(JSONArray bbox) {
        return new double[] {
            bbox.getDouble(0), bbox.getDouble(1), bbox.getDouble(2), bbox.getDouble(3)
        };
    }

    private static double[] toDoubleArray(int[] bbox) {
        return new double[] { bbox[0], bbox[1], bbox[2], bbox[3] };
    }

    private static List<String> jsonArrayToStringList(JSONArray array) {

 succeeded in 225ms:

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
        final Task parentTask) {
        if ((mas == null) || (mas.size() < 1)) return null;
        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
        Taxonomy taxy, final Task parentTask) {
        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
            " media assets:");
        handleMissingAcmids(mas, myShepherd);
        for (MediaAsset ma : mas) {
            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
            System.out.println("acmid is: " + ma.getAcmId());
        }
        JSONArray maArr = new JSONArray();
        for (MediaAsset ma : mas) {
            maArr.put(ma.getId());
        }
        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());

        Task topTask = new Task();
        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
        topTask.setObjectMediaAssets(mas);
        myShepherd.storeNewTask(topTask);

        String context = myShepherd.getContext();
        String baseUrl = getBaseURL(context);

        // Ia configs are keyed off taxonomies
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();

        // Migration plan v2 §commit #10b: routing reroute.
        // If the species' _id_conf.default.pipeline_root is "vector" AND
        // _mlservice_conf is configured, route per-asset through the
        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
        // WBIA path below — production deployments without _mlservice_conf
        // see no behavior change at all.
        //
        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
        // child finalization is local; no first-finisher-wins. The topTask
        // remains as the aggregator for the caller contract (and so legacy
        // summary code that reads topTask.objectMediaAssets keeps working).
        if (iaConfig != null && taxy != null &&
            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
                context, baseUrl);
        }

        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
        JSONObject dj = new JSONObject();
        dj.put("mediaAssetIds", maArr);
        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
        Boolean[] sent = new Boolean[numDetectAlgos];
        for (int i = 0; i < numDetectAlgos; i++) {
            // task for this job (only create new (child) tasks if multiple detect algos)
            Task task = (numDetectAlgos == 1) ? topTask : new Task();
            task.setObjectMediaAssets(mas);
            task.setParameters(topTask.getParameters());

            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
            task.addParameter("detectArgs", detectArgs);

            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
            task.addParameter("__detect_url", detectionUrl);

            JSONObject qjob = new JSONObject();
            qjob.put("detect", dj);
            qjob.put("__detect_args", detectArgs);
            qjob.put("__detect_url", detectionUrl);
            // task is queued here
            qjob.put("taskId", topTask.getId());
            qjob.put("__context", context);
            qjob.put("__baseUrl", baseUrl);
            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
            if (tweetAssetId != -1) {
                qjob.put("tweetAssetId", tweetAssetId);
            }
            System.out.println(
                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
            sent[i] = false;
            try {
                // job is queued here
                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
                    qjob.toString());
            } catch (java.io.IOException iox) {
                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
                    iox.toString());
            }
        }
        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
            " assets; queued? = " + sent + "; " + topTask);
        return topTask;
    }

    /**
     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
     * vector pipeline. Each MediaAsset gets its own child Task under
     * topTask; each emits a {@code mlServiceV2:true} payload to the
     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
     * picks them up via the IAGateway dispatcher (commit #10a).
     *
     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
     * topTask. The topTask itself remains as the aggregator that holds the
     * full MediaAsset list for caller-side summary code.</p>
     *
     * <p>encounterId is derived best-effort from the MediaAsset's existing
     * trivial annotation (every Encounter.addMediaAsset call creates one).
     * If null, MlServiceProcessor persists annotations without explicit
     * Encounter linkage and downstream MediaAsset.assignEncounters handles
     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
     */
    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
        int queued = 0;
        for (MediaAsset ma : mas) {
            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
                queued++;
            }
        }
        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
        return topTask;
    }

    /**
     * Build and enqueue one v2 ml-service job for a single MediaAsset.
     * Returns {@code true} iff the FileQueue write succeeded.
     *
     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
     * normal intake path) and the startup stale-mlservice reconciler in
     * {@code StartupWildbook}. The reconciler relies on the boolean
     * return to decide whether to commit accompanying state changes; the
     * normal intake path tolerates the swallowed-failure behavior.</p>
     *
     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
     * internally commits/reopens the transaction, so the child Task row
     * is persisted before this method enqueues. On enqueue failure the
     * child Task remains in the DB as an orphan — there is no queued
     * job that will ever drive it. The orphan IS still discoverable
     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
     * the task references the MediaAsset through objectMediaAssets),
     * so it may surface in operator-facing task listings until cleaned
     * up by an out-of-band path. Callers that need cleanup should
     * delete the orphan explicitly; the default posture here is to
     * accept it since FileQueue write failures are rare.</p>
     *
     * <p>If {@code topTask} is null a fresh root task is created inside
     * this method. This matches the reconciler's use case where there is
     * no caller-side aggregator umbrella.</p>
     */
    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
        Taxonomy taxy, Task topTask, String context, String baseUrl) {
        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
        singleton.add(ma);
        childTask.setObjectMediaAssets(singleton);
        myShepherd.storeNewTask(childTask);

        // Best-effort encounterId via existing annotations on the MA.
        String encounterId = null;
        ArrayList<Annotation> existing = ma.getAnnotations();
        if (existing != null) {
            for (Annotation a : existing) {
                Encounter enc = a.findEncounter(myShepherd);
                if (enc != null) {
                    encounterId = enc.getId();
                    break;
                }
            }
        }

        JSONObject qjob = new JSONObject();
        qjob.put("mlServiceV2", true);
        qjob.put("mediaAssetId", ma.getId());
        qjob.put("taxonomyString", taxy.getScientificName());
        qjob.put("taskId", childTask.getId());
        qjob.put("__context", context);
        qjob.put("__baseUrl", baseUrl);
        if (Util.stringExists(encounterId)) {
            qjob.put("encounterId", encounterId);
        }

        try {
            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
        } catch (java.io.IOException iox) {
            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
            return false;
        }
    }

 succeeded in 341ms:
src/main/java/org/ecocean/ia/Task.java

 succeeded in 11508ms:
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
?? docs/plans/2026-05-18-empty-match-prospects-codex-review.md
?? docs/plans/2026-05-18-empty-match-prospects-design.md
?? logs/

exec
/bin/bash -lc "sed -n '1,140p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '730,820p' src/main/java/org/ecocean/servlet/IAGateway.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '560,640p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '900,1240p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
 succeeded in 301ms:
/*
    an instance of an ia.Task can be persisted and represents the state of that task
    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
 */
package org.ecocean.ia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jdo.Query;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.identity.IdentityServiceLog;

public class Task implements java.io.Serializable {
    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    private String id = null;
    private long created = -1;
    private long modified = -1;
    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    private List<MediaAsset> objectMediaAssets = null;
    private List<Annotation> objectAnnotations = null;
    private Task parent = null;
    private List<Task> children = null;
    private String parameters = null;
    private String status;
    // general use, but notably will contain error details when status=error
    private String statusDetails = null;
    private Long completionDateInMilliseconds;
    private String queueResumeMessage;

    public Task() {
        this(Util.generateUUID());
    }
    public Task(String id) {
        this.id = id;
        created = System.currentTimeMillis();
        modified = System.currentTimeMillis();
    }
    // makes a child of the passed Task (and inherits the parameters!!)
    public Task(Task p) {
        this();
        this.setParameters(p.getParameters());
        this.setParent(p);
    }

    public String getId() {
        return id;
    }

    public long getCreatedLong() {
        return created;
    }

    public long getModifiedLong() {
        return modified;
    }

    public long timeInactive() {
        long now = System.currentTimeMillis();

        if (modified > 0) return (now - modified);
        if (created > 0) return (now - created);
        // weird or inconclusive:
        return -1l;
    }

    public boolean timedOutDueToInactivity() {
        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    }

    public boolean statusInEndState() {
        if ("completed".equals(status)) return true;
        if ("error".equals(status)) return true;
        // ml-service migration v2: "dropped-stale" is terminal — the task's
        // target was deleted before the queued job ran. Neither success nor
        // error; the inactivity-timeout watchdog must not flip it to error.
        if ("dropped-stale".equals(status)) return true;
        return false;
    }

    public void setModified() {
        modified = System.currentTimeMillis();
    }

    public boolean canUserAccess(User user, Shepherd myShepherd) {
        if (user == null) return false;
        if (user.isAdmin(myShepherd)) return true;
        Encounter enc = null;
        // if we have annotations, use first to determine encounter
        if (this.countObjectAnnotations() > 0) {
            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
            MediaAsset ma = this.getObjectMediaAssets().get(0);
            // we iterate over all annots on this asset til we find an encounter.
            // it might be better to find *all* encounters and return access based on each;
            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
            // so i think this means asset-based access of tasks will be rare or unused anyway
            for (Annotation ann : ma.getAnnotations()) {
                if (ann != null) enc = ann.findEncounter(myShepherd);
                if (enc != null) break;
            }
        }
        if (enc == null) return false;
        if (enc.isPubliclyReadable()) return true;
        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
        return enc.canUserAccess(user, myShepherd.getContext());
    }

/*
    // not really convinced these are accurate enough to use
    //   actual computation of these things is complicated
    //   leaving these for future potential exploration, if needed.

    public boolean isTypeDetection() {
        if (this.hasObjectMediaAssets()) return true;
        if (this.hasObjectAnnotations()) return false;
        if (this.parameters == null) return false;
        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
        return false;
    }
    public boolean isTypeIdentification() {
        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
        if (this.parameters == null) return false;
        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
        return false;

 succeeded in 303ms:
            } catch (Exception ex) {
                System.out.println(
                    "ERROR: IAGateway.processQueueMessage() 'identify' from threw exception: " +
                    ex.toString());
                if (ex.toString().contains("HTTP error code : 500")) {
                    requeueIncrement = true;
                    requeue = true;
                } else if (ex.toString().contains("Empty target annotation list")) {
                    requeue = false;
                } else { requeue = true; }
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
        } else {
            System.out.println(
                "WARNING: IAGateway.processQueueMessage() unable to use json data in '" + message +
                "'; ignoring");
        }
        if (requeue) requeueJob(jobj, requeueIncrement);
    }

    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
        int MAX_RETRIES = 30;
        long MAX_TIME_MILLIS = 2 * 24 * 60 * 60 * 1000;
        String context = jobj.optString("__context", "context0");
        String taskId = jobj.optString("taskId", "UNKNOWN_TASKID");
        long queueStart = jobj.optLong("__queueStart", System.currentTimeMillis());
        int actualRetries = jobj.optInt("__queueActualRetries", 0);
        int retries = jobj.optInt("__queueRetries", 0);

        if (retries < 0) retries = 0;
        long elapsed = System.currentTimeMillis() - queueStart;
        if (elapsed > MAX_TIME_MILLIS) retries = MAX_RETRIES + 1; // waiting around too long
        if (retries > MAX_RETRIES) {
            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
                MAX_RETRIES + " retries (or max time) in queue; giving up");
            return false;
        }
        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
            retries + " out of " + MAX_RETRIES + " (actualRetries=" + actualRetries + "; start=" +
            queueStart + "; elapsed=" + elapsed + "; increment=" + increment + ")");
        final long sleepMillis = 1000;
        if (increment) retries++;
        actualRetries++;
        jobj.put("__queueStart", queueStart);
        jobj.put("__queueRetries", retries);
        jobj.put("__queueActualRetries", actualRetries);

        // now we fork background thread to *wait* and then add this to queue
        Runnable r = new Runnable() {
            public void run() {
                boolean requeueSuccess = false;
                long whileSleepMillis = 1000;
                while (!requeueSuccess) {
                    try {
                        if (increment) whileSleepMillis = 30000;
                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
                        try {
                            Thread.sleep(whileSleepMillis);
                        } catch (java.lang.InterruptedException ex) {}
                        if (jobj.optJSONObject("detect") != null || jobj.optBoolean("fastlane",
                            false) || jobj.optBoolean("MLService", false) ||
                            jobj.optBoolean("mlServiceV2", false)) {
                            // mlServiceV2 retries must land on the detection
                            // queue, not the generic IA queue. Without this,
                            // a retryable ml-service failure would never be
                            // re-dispatched to MlServiceProcessor.
                            addToDetectionQueue(context, jobj.toString());
                        } else {
                            addToQueue(context, jobj.toString());
                        }
                        requeueSuccess = true;
                    } catch (Exception ex) {
                        whileSleepMillis = 30000;
                        System.out.println(
                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
                            taskId + " due to " + ex.toString());
                        ex.printStackTrace();
                    }
                } // end while
            } // end run
        }; // end Runnable
        new Thread(r).start();

        return true;
    }

    public static void processCallbackQueueMessage(String message) {
        JSONObject jmsg = Util.stringToJSONObject(message);

        if (jmsg == null) {

 succeeded in 345ms:
        query.setOrdering("created");
        Collection c = (Collection)query.execute();
        List<Task> listy = new ArrayList<Task>(c);
        query.closeAll();
        return listy;
    }

    public static List<Task> getRootTasksFor(MediaAsset ma, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ma, myShepherd));
    }

    // takes a bunch of tasks and returns only roots (without duplication)
    public static List<Task> onlyRoots(List<Task> all) {
        List<Task> roots = new ArrayList<Task>();

        for (Task t : all) {
            Task r = t.getRootTask();
            if (!roots.contains(r)) roots.add(r);
        }
        return roots;
    }

    public boolean areSelfAndOrAllChildrenComplete() {
        boolean complete = false;

        if (!hasChildren() && completionDateInMilliseconds != null) {
            complete = true;
        } else if (hasChildren()) {
            List<Task> children = getChildren();
            complete = true;
            for (Task t : children) {
                if (!t.areSelfAndOrAllChildrenComplete()) complete = false;
            }
        }
        return complete;
    }

    public String getStatus(Shepherd myShepherd) {
        // see if we might be dead in the water
        // TODO skipping status==null cuz i cant figure out what this means and there are so many of them
        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
            this.status = "error";
            long ti = timeInactive();
            setStatusDetailsAddError("TIMEOUT",
                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
            return this.status;
        }
        // if status is not null, just send it
        if (status != null) return status;
        // otherwise
        // note: this is LOCAL status :(  so it is not changing this.status, only returning the value
        String status = "waiting to queue";
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
            myShepherd);
        if (logs != null && logs.size() > 0) {
            Collections.reverse(logs); // so it has newest first like mostRecent above
            IdentityServiceLog l = logs.get(0);
            JSONObject islObj = l.toJSONObject();
            if (islObj.optString("status") != null &&
                islObj.optString("status").equals("completed")) {
                status = islObj.optString("status");
            } else if (islObj.optJSONObject("status") != null &&
                (islObj.optJSONObject("status").optJSONObject("needReview") != null)) {
                status = "completed";
            } else if (logs.toString().indexOf("score") > -1) {
                status = "completed";
            } else if (islObj.toString().indexOf("HTTP error code") > -1) {
                status = "error";
            } else if (!islObj.optString("queueStatus").equals("")) {
                status = islObj.optString("queueStatus");
            } else if (islObj.opt("status") != null &&
                islObj.opt("status").toString().indexOf("initIdentify") > -1) {
                status = "queuing";
            }
            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
            // if(status.equals("waiting to queue"))System.out.println("islObj: "+islObj.toString());
        }
        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
            this.getId());
        return status;
    }

 succeeded in 355ms:

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        return this.sanitizeMedia(request, false);
    }

    public boolean isPart() {
        return ((this.iaClass != null) && this.iaClass.contains("+"));
    }

    public String getPartIfPresent() {
        String thisPart = "";

        if (this.iaClass != null && this.iaClass.contains("+")) {
            String[] arr = this.iaClass.split("\\+");
            thisPart = arr[arr.length - 1];
        }
        return thisPart;
    }

/*
   both must be arrays which contain objects.
   these will be "mixed into" the built default query. TODO this might cause some conflict or
   overwriting that needs to be addressed in the future
 */
    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
        boolean useClauses) {
        Encounter enc = this.findEncounter(myShepherd);

        if (enc == null) {
            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
                this);
            return null;
        }
        JSONObject query = new JSONObject(
            "{\"query\": {\"bool\": {\"filter\": [], \"must_not\": []} } }");
        JSONObject wrapper = new JSONObject();
        JSONObject arg = new JSONObject();
        String txStr = enc.getTaxonomyString();
        if (txStr != null) {
            useClauses = true;
            if (txStr.endsWith(" sp")) {
                arg.put("encounterTaxonomy", txStr.substring(0, txStr.length() - 2) + "*");
                wrapper.put("wildcard", arg);
            } else {
                arg.put("encounterTaxonomy", txStr);
                wrapper.put("match", arg);
            }
            query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
        } else if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
            "allowIdentificationWithoutTaxonomy"))) {
            System.out.println(
                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
            return null;
        }
        // it seems like useClauses=false only ever was used when no taxonomy was present and basically
        // returned every annotation with matchAgainst=T and an acmId
        if (useClauses) {
            if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
                "ignoreViewpointMatching", this.getTaxonomy(myShepherd)))) {
                String[] viewpoints = this.getViewpointAndNeighbors();
                if (viewpoints != null) {
                    arg = new JSONObject();
                    arg.put("viewpoint", new JSONArray(viewpoints));
                    wrapper = new JSONObject();
                    wrapper.put("terms", arg);
                    // query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
                    // to handle allowing null viewpoint, opensearch query gets messy!
                    JSONArray should = new JSONArray(
                        "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"viewpoint\"}}}}]");
                    should.put(wrapper);
                    JSONObject bool = new JSONObject("{\"bool\": {}}");
                    bool.getJSONObject("bool").put("should", should);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        bool);
                }
            }
            // this does either/or part/iaClass - unsure if this is correct
            boolean usedPart = false;
            if (Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
                "usePartsForIdentification"))) {
                String part = this.getPartIfPresent();
                if (!Util.stringIsEmptyOrNull(part)) {
                    arg = new JSONObject();
                    arg.put("iaClass", "*" + part);
                    wrapper = new JSONObject();
                    wrapper.put("wildcard", arg);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        wrapper);
                    usedPart = true;
                }
            }
            if (!usedPart && (this.getIAClass() != null)) {
                arg = new JSONObject();
                arg.put("iaClass", this.getIAClass());
                wrapper = new JSONObject();
                wrapper.put("match", arg);
                query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                    wrapper);
            }
        }
        // matchAgainst true
        arg = new JSONObject();
        arg.put("matchAgainst", true);
        wrapper = new JSONObject();
        wrapper.put("term", arg);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);

        // must have acmId
        arg = new JSONObject();
        arg.put("field", "acmId");
        wrapper = new JSONObject();
        wrapper.put("exists", arg);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);

        // exclude our encounter
        arg = new JSONObject();
        arg.put("encounterId", enc.getId());
        wrapper = new JSONObject();
        wrapper.put("match", arg);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(wrapper);

        // skip dead animals
        Long dateMS = enc.getDateInMillisecondsFallback();
        if (dateMS != null) {
            wrapper = new JSONObject(
                "{\"range\": {\"encounterIndividualTimeOfDeath\": { \"lte\": " + dateMS + " } } }");
            query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(
                wrapper);
        }
        // now process taskParams
        if (taskParams != null) {
            String userId = taskParams.optString("userId", null);
            JSONObject filt = taskParams.optJSONObject("matchingSetFilter");
            if (filt != null) {
                // locationId=FOO and locationIds=[FOO,BAR]
                boolean useNullLocation = false;
                List<String> rawLocationIds = new ArrayList<String>();
                String tmp = Util.basicSanitize(filt.optString("locationId", null));
                if (Util.stringExists(tmp)) rawLocationIds.add(tmp);
                JSONArray larr = filt.optJSONArray("locationIds");
                if (larr != null) {
                    for (int i = 0; i < larr.length(); i++) {
                        tmp = Util.basicSanitize(larr.optString(i));
                        if ("__NULL__".equals(tmp)) {
                            useNullLocation = true;
                        } else if (Util.stringExists(tmp) && !rawLocationIds.contains(tmp)) {
                            rawLocationIds.add(tmp);
                        }
                    }
                }
                List<String> expandedLocationIds = LocationID.expandIDs(rawLocationIds);
                if (expandedLocationIds.size() > 0) {
                    arg = new JSONObject();
                    arg.put("encounterLocationId", new JSONArray(expandedLocationIds));
                    wrapper = new JSONObject();
                    wrapper.put("terms", arg);
                    if (useNullLocation) {
                        JSONArray should = new JSONArray(
                            "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"encounterLocationId\"}}}}]");
                        should.put(wrapper);
                        JSONObject bool = new JSONObject("{\"bool\": {}}");
                        bool.getJSONObject("bool").put("should", should);
                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                            "filter").put(bool);
                    } else {
                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                            "filter").put(wrapper);
                    }
                }
                // owner ... which requires we have userId in the taskParams
                JSONArray owner = filt.optJSONArray("owner");
                JSONArray uids = new JSONArray();
                if ((owner != null) && (userId != null)) {
                    for (int i = 0; i < owner.length(); i++) {
                        String opt = owner.optString(i, null);
                        if (!Util.stringExists(opt)) continue;
                        if (opt.equals("me")) {
                            uids.put(userId);
                        } else {
                            uids.put(opt);
                        }
                    }
                }
                if (uids.length() > 0) {
                    arg = new JSONObject();
                    arg.put("encounterUserUuid", uids);
                    wrapper = new JSONObject();
                    wrapper.put("terms", arg);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        wrapper);
                }
                // projectId
                String projectId = filt.optString("projectId", null);
                if (Util.stringExists(projectId)) {
                    arg = new JSONObject();
                    arg.put("encounterProjectIds", projectId);
                    wrapper = new JSONObject();
                    wrapper.put("match", arg);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        wrapper);
                }
            }
        }
        /* saving this for possible future passing raw queries
           JSONArray arr = additionalQuery.optJSONArray("filter");
           if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject clause = arr.optJSONObject(i);
                if (clause != null)
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                        "filter").put(clause);
            }
           }
           arr = additionalQuery.optJSONArray("must_not");
           if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject clause = arr.optJSONObject(i);
                if (clause != null)
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                        "must_not").put(clause);
            }
           }
         */

        // this exludes the very noisy embeddings from opensearch results since we dont need it
        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
        return query;
    }

    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd) {
        return getMatchingSet(myShepherd, null, true);
    }

    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams) {
        return getMatchingSet(myShepherd, taskParams, true);
    }

    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams,
        boolean useClauses) {
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
        OpenSearch os = new OpenSearch();
        long startTime = System.currentTimeMillis();

        if (query == null) return anns;
        JSONObject queryRes = null;
        int hitSize = -1;
        try {
            int pageSize = 10000;
            try {
                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
            } catch (Exception ex) {}
            os.deletePit("annotation");
            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
            hitSize = queryRes.optJSONObject("hits").optJSONObject("total").optInt("value");
        } catch (Exception ex) {
            System.out.println("getMatchingSet() exception: " + ex);
            ex.printStackTrace();
        }
        JSONArray hits = OpenSearch.getHits(queryRes);
        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.optJSONObject(i);
            if (hit == null) continue;
            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
            if (ann != null) {
                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
                anns.add(ann);
            }
        }
        System.out.println("getMatchingSet() results: hitSize=" + hitSize + "; hits length=" +
            hits.length() + "; anns size=" + anns.size() + "; " +
            (System.currentTimeMillis() - startTime) + "ms");
        return anns;
    }

    // a variation of matchingSet query, but includes the vector stuff - thus returns actual matches(!)
    // method and methodVersion are used to determine *which* embedding to use; if null it will use 1st embedding
    // return null when this annot has no embeddings to match, sorry!

    // this version will construct matchingSetQuery
    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
        String method, String methodVersion) {
        Embedding emb = getEmbeddingByMethod(method, methodVersion);

        if (emb == null) return null;
        return getMatchQuery(method, methodVersion,
                getMatchingSetQuery(myShepherd, taskParams, useClauses));
    }

    // this version if you already have matchingSetQuery
    public JSONObject getMatchQuery(String method, String methodVersion,
        JSONObject matchingSetQuery) {
        Embedding emb = getEmbeddingByMethod(method, methodVersion);

        if (emb == null) return null;
        JSONObject nested = new JSONObject(
            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
        JSONArray must = new JSONArray();
        JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
            new JSONArray(emb.vectorToFloatArray()));
        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
        must.put(knn);
        if (method != null)
            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
        if (methodVersion != null)
            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
                "\"}}"));
        nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
            must);

        // we put nested under its own top-level must, that way its score counts (whereas filter does not)
        JSONArray nestedMust = new JSONArray();
        nestedMust.put(nested);
        matchingSetQuery.getJSONObject("query").getJSONObject("bool").put("must", nestedMust);
        return matchingSetQuery;
    }

    // finds annotations based on embedding vector matches
    // null means we didnt have an embedding to query with
    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
        boolean useClauses, String method, String methodVersion) {
        return getMatches(myShepherd,
                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
    }

    // where we already have the query
    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
        if (matchQuery == null) return null;
        List<Annotation> anns = new ArrayList<Annotation>();
        OpenSearch os = new OpenSearch();
        long startTime = System.currentTimeMillis();
        JSONObject queryRes = null;
        int hitSize = -1;
        try {
            int pageSize = 10000;
            try {
                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
            } catch (Exception ex) {}

exec
/bin/bash -lc 'rg -n "STATUS_COMPLETE|STATUS_COMPLETE_MLSERVICE|STATUS_PROCESSING_MLSERVICE|STATUS_PENDING_SPECIES|STATUS_DROPPED_STALE|STATUS_INITIATED|STATUS_PENDING" src/main/java/org/ecocean/identity/IBEISIA.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getParent|getChildren|addChild|setParent|setStatus\\(|getStatus\\(|setObjectMediaAssets|getObjectMediaAssets|hasChildren|countObjectMediaAssets\" src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "getAcmId|getMatchAgainst|isMatchAgainst|setMatchAgainst|addAnnotation|embeddings|index" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/Embedding.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '250,430p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
 succeeded in 233ms:
src/main/java/org/ecocean/Embedding.java:37:    // for trying to query vectors of annots without embeddings
src/main/java/org/ecocean/Embedding.java:215:        // TODO prevent duplicate runs by perhaps locking wity SystemValue like indexing
src/main/java/org/ecocean/Embedding.java:248:                /// maybe set on embData when we have *no embeddings* but did not have exception??
src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
src/main/java/org/ecocean/Embedding.java:308:                        " due to no suitable embeddings for " + iaConfig);
src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:361:                    " due to no suitable embeddings for " + iaConfig);
src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/media/MediaAsset.java:136:    public String getAcmId() {
src/main/java/org/ecocean/media/MediaAsset.java:220:        // OpenSearch reindexer picks up detection-status changes and so the
src/main/java/org/ecocean/media/MediaAsset.java:701:                null) ? -1 : store.getUsage().indexOf("PLACEHOLDERHACK:"));
src/main/java/org/ecocean/media/MediaAsset.java:861:        jobj.put("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:895:                    jf.put("annotationAcmId", ann.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:957:                kj.put("indexname", kw.getIndexname());
src/main/java/org/ecocean/media/MediaAsset.java:1579:            } else if (ann.getIAClass().indexOf("+") > -1) { // we are a part, i guess?
src/main/java/org/ecocean/media/MediaAsset.java:1629:                    tann.setMatchAgainst(false);
src/main/java/org/ecocean/media/MediaAsset.java:1640:                        newEnc.addAnnotation(ann);
src/main/java/org/ecocean/media/MediaAsset.java:1663:                    tann.setMatchAgainst(false);
src/main/java/org/ecocean/media/MediaAsset.java:1674:                    whichever.addAnnotation(ann);
src/main/java/org/ecocean/media/MediaAsset.java:1726:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:1745:            jgen.writeStringField("acmId", ann.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:1762:    // we override Base version, as we want to (dont we?) always skip auto-indexing children assets
src/main/java/org/ecocean/media/MediaAsset.java:1766:        // TODO making the bold decision to *always* let MediaAsset index in background
src/main/java/org/ecocean/media/MediaAsset.java:1767:        // as we currently arent even using this index. this is especially helpful as
src/main/java/org/ecocean/Annotation.java:52:    private Set<Embedding> embeddings;
src/main/java/org/ecocean/Annotation.java:59:    // identifies an ml-service-created detection. The partial unique index
src/main/java/org/ecocean/Annotation.java:189:        // embeddings have some metadata (algorithm etc)
src/main/java/org/ecocean/Annotation.java:198:        // https://docs.opensearch.org/docs/latest/vector-search/creating-vector-index/
src/main/java/org/ecocean/Annotation.java:204:        map.put("embeddings", embMap);
src/main/java/org/ecocean/Annotation.java:213:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/Annotation.java:216:        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
src/main/java/org/ecocean/Annotation.java:227:            // per discussion on issue 874, including this in indexing, but not (yet) using in matchingSet
src/main/java/org/ecocean/Annotation.java:244:        jgen.writeArrayFieldStart("embeddings");
src/main/java/org/ecocean/Annotation.java:245:        if (this.embeddings != null)
src/main/java/org/ecocean/Annotation.java:246:            for (Embedding emb : this.embeddings) {
src/main/java/org/ecocean/Annotation.java:254:                // System.out.println("[INFO] indexing emb " + emb.getId() + " vector length " + ((vecFloat == null) ? "null" : vecFloat.length));
src/main/java/org/ecocean/Annotation.java:317:    public String getAcmId() {
src/main/java/org/ecocean/Annotation.java:541:                                // gettin trixy.. wrap indexes around
src/main/java/org/ecocean/Annotation.java:602:     * partial unique index on (mediaAsset, predictModelId, bboxKey, thetaKey)
src/main/java/org/ecocean/Annotation.java:604:     * Bumps version for OpenSearch reindex pickup. Mirrors the
src/main/java/org/ecocean/Annotation.java:666:        return anns.indexOf(this);
src/main/java/org/ecocean/Annotation.java:750:    public boolean getMatchAgainst() {
src/main/java/org/ecocean/Annotation.java:754:    public void setMatchAgainst(boolean b) {
src/main/java/org/ecocean/Annotation.java:1125:        // this exludes the very noisy embeddings from opensearch results since we dont need it
src/main/java/org/ecocean/Annotation.java:1126:        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
src/main/java/org/ecocean/Annotation.java:1179:    // return null when this annot has no embeddings to match, sorry!
src/main/java/org/ecocean/Annotation.java:1198:            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
src/main/java/org/ecocean/Annotation.java:1200:        JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
src/main/java/org/ecocean/Annotation.java:1201:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
src/main/java/org/ecocean/Annotation.java:1203:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
src/main/java/org/ecocean/Annotation.java:1206:            must.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
src/main/java/org/ecocean/Annotation.java:1208:            must.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
src/main/java/org/ecocean/Annotation.java:1364:                ann.setMatchAgainst(false);
src/main/java/org/ecocean/Annotation.java:1383:                    someEnc.addAnnotation(this);
src/main/java/org/ecocean/Annotation.java:1396:                newEnc.addAnnotation(this);
src/main/java/org/ecocean/Annotation.java:1447:        this.setMatchAgainst(false);
src/main/java/org/ecocean/Annotation.java:1449:        enc.addAnnotation(triv);
src/main/java/org/ecocean/Annotation.java:1546:                ann.setMatchAgainst(true);
src/main/java/org/ecocean/Annotation.java:1552:            ann.setMatchAgainst(true);
src/main/java/org/ecocean/Annotation.java:1565:                (iaClass.indexOf("+") == -1)) {
src/main/java/org/ecocean/Annotation.java:1570:                if ((annot1.getIAClass() != null) && (annot1.getIAClass().indexOf("+") != -1)) {
src/main/java/org/ecocean/Annotation.java:1576:                (iaClass.indexOf("+") > -1)) {
src/main/java/org/ecocean/Annotation.java:1579:                if ((annot1.getIAClass() != null) && (annot1.getIAClass().indexOf("+") != -1)) {
src/main/java/org/ecocean/Annotation.java:1626:                    clone.addAnnotation(ann);
src/main/java/org/ecocean/Annotation.java:1649:                enc.addAnnotation(ann);
src/main/java/org/ecocean/Annotation.java:1687:    // note: this will throw an IAException if the txStr does not support embeddings
src/main/java/org/ecocean/Annotation.java:1837:        for (Embedding emb : embeddings) {
src/main/java/org/ecocean/Annotation.java:1890:            "\" && annotations.contains(annot1) && annot1.acmId == \"" + annot.getAcmId() +
src/main/java/org/ecocean/Annotation.java:1980:                if (iaClass0.indexOf("+") > -1 && iaClass1.indexOf("+") == -1) return true;
src/main/java/org/ecocean/Annotation.java:1981:                if (iaClass1.indexOf("+") > -1 && iaClass0.indexOf("+") == -1) return true;
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

 succeeded in 254ms:
src/main/java/org/ecocean/ia/MlServiceProcessor.java:171:                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:255:            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:278:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:333:                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:365:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:401:            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:625:        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:626:        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:676:        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
src/main/java/org/ecocean/identity/IBEISIA.java:73:    public static String STATUS_PENDING = "pending"; // pending review (needs action by user)
src/main/java/org/ecocean/identity/IBEISIA.java:74:    public static String STATUS_COMPLETE = "complete"; // process is done
src/main/java/org/ecocean/identity/IBEISIA.java:75:    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
src/main/java/org/ecocean/identity/IBEISIA.java:76:    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
src/main/java/org/ecocean/identity/IBEISIA.java:77:    public static String STATUS_DROPPED_STALE = "dropped-stale"; // queued ml-service job's target deleted before run; terminal-drop without error
src/main/java/org/ecocean/identity/IBEISIA.java:79:    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
src/main/java/org/ecocean/identity/IBEISIA.java:80:    public static String STATUS_INITIATED = "initiated"; // initiated on our side but may or may not be processing on IA side
src/main/java/org/ecocean/identity/IBEISIA.java:1666:                        asset.setDetectionStatus(STATUS_PENDING);
src/main/java/org/ecocean/identity/IBEISIA.java:1668:                        asset.setDetectionStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:1711:                    ann.setIdentificationStatus(STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/identity/IBEISIA.java:1856:                    anns.get(id).setIdentificationStatus(STATUS_PENDING);
src/main/java/org/ecocean/identity/IBEISIA.java:1861:                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:1867:                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:2221:        ma.setDetectionStatus(STATUS_COMPLETE); // kosher?

 succeeded in 286ms:
57:        this.setParent(p);
106:        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
107:            MediaAsset ma = this.getObjectMediaAssets().get(0);
151:    public int countObjectMediaAssets() {
160:        return countObjectMediaAssets() + countObjectAnnotations();
165:        return (countObjectMediaAssets() > 0);
176:    public void setObjectMediaAssets(List<MediaAsset> mas) {
184:    public List<MediaAsset> getObjectMediaAssets() {
247:    public List<Task> getChildren() {
258:            this.addChild(kid); // let this do the work
262:    public List<Task> addChild(Task kid) {
270:    public void setParent(Task t) {
272:        t.addChild(this);
275:    public Task getParent() {
279:    public String getParentId() {
288:    public boolean hasChildren() {
296:        if (!this.hasChildren()) {
313:        if (!this.hasChildren()) return null;
323:        if (!this.hasChildren()) return null;
335:        if (this.hasChildren())
346:        if (this.hasChildren())
357:        for (MediaAsset ma : this.getObjectMediaAssets()) {
480:        if (includeChildren && this.hasChildren()) {
509:                   .append(countObjectMediaAssets() + "MA")
585:        if (!hasChildren() && completionDateInMilliseconds != null) {
587:        } else if (hasChildren()) {
588:            List<Task> children = getChildren();
597:    public String getStatus(Shepherd myShepherd) {
637:        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
648:        if (this.hasChildren()) {
652:            for (Task childTask : this.getChildren()) {
653:                if (childTask.hasChildren()) {
654:                    for (Task childTask2 : childTask.getChildren()) {
659:                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
667:                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
683:            status = this.getStatus(myShepherd);
699:    public void setStatus(String newStatus) {
810:        rtn.put("parentTaskId", getParentId());
837:            if ((mr == null) && !hasChildren()) {
853:        if (hasChildren()) {
867:            rtn.put("status", getStatus(myShepherd));

 succeeded in 265ms:

    public void setChildren(List<Task> kids) {
        if (kids == null) {
            children = null;
            return;
        }
        children = new ArrayList<Task>();
        for (Task kid : kids) {
            this.addChild(kid); // let this do the work
        }
    }

    public List<Task> addChild(Task kid) {
        if (children == null) children = new ArrayList<Task>();
        if (kid == null) return children;
        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
        if (!children.contains(kid)) children.add(kid);
        return children;
    }

    public void setParent(Task t) {
        parent = t;
        t.addChild(this);
    }

    public Task getParent() {
        return parent;
    }

    public String getParentId() {
        if (parent == null) return null;
        return parent.getId();
    }

    public int numChildren() {
        return (children == null) ? 0 : children.size();
    }

    public boolean hasChildren() {
        return (this.numChildren() > 0);
    }

    // omg i am going to assume no looping
    public List<Task> getLeafTasks() {
        List<Task> leaves = new ArrayList<Task>();

        if (!this.hasChildren()) {
            leaves.add(this);
            return leaves;
        }
        for (Task kid : children) {
            leaves.addAll(kid.getLeafTasks());
        }
        return leaves;
    }

    public Task getRootTask() {
        if (parent == null) return this;
        return parent.getRootTask();
    }

    public Task deepContains(Annotation ann) {
        if (this.contains(ann)) return this;
        if (!this.hasChildren()) return null;
        for (Task kid : children) {
            Task found = kid.deepContains(ann);
            if (found != null) return found;
        }
        return null;
    }

    public Task deepContains(MediaAsset ma) {
        if (this.contains(ma)) return this;
        if (!this.hasChildren()) return null;
        for (Task kid : children) {
            Task found = kid.deepContains(ma);
            if (found != null) return found;
        }
        return null;
    }

    public List<Task> findNodesWithMediaAssets() {
        List<Task> found = new ArrayList<Task>();

        if (this.hasObjectMediaAssets()) found.add(this);
        if (this.hasChildren())
            for (Task kid : this.children) {
                found.addAll(kid.findNodesWithMediaAssets());
            }
        return found;
    }

    public List<Task> findNodesWithAnnotations() {
        List<Task> found = new ArrayList<Task>();

        if (this.hasObjectAnnotations()) found.add(this);
        if (this.hasChildren())
            for (Task kid : this.children) {
                found.addAll(kid.findNodesWithAnnotations());
            }
        return found;
    }

    public Map<String, Integer> detectionStatusSummary() {
        Map<String, Integer> cts = new HashMap<String, Integer>();

        if (!this.hasObjectMediaAssets()) return cts;
        for (MediaAsset ma : this.getObjectMediaAssets()) {
            String status = ma.getDetectionStatus();
            if (status == null) status = "";
            cts.put(status, cts.getOrDefault(status, 0) + 1);
        }
        return cts;
    }

    public Map<String, Integer> identificationStatusSummary() {
        Map<String, Integer> cts = new HashMap<String, Integer>();

        if (!this.hasObjectAnnotations()) return cts;
        for (Annotation ann : this.getObjectAnnotations()) {
            String status = ann.getIdentificationStatus();
            if (status == null) status = "";
            cts.put(status, cts.getOrDefault(status, 0) + 1);
        }
        return cts;
    }

    public JSONObject getStatusDetails() {
        return Util.stringToJSONObject(statusDetails);
    }

    public void setStatusDetails(String s) {
        statusDetails = s;
    }

    public void setStatusDetails(JSONObject j) {
        if (j == null) {
            statusDetails = null;
        } else {
            statusDetails = j.toString();
        }
    }

    public void setStatusDetailsAddError(String code, String message) {
        JSONObject add = new JSONObject();

        add.put("code", code);
        add.put("message", message);
        setStatusDetailsAddToSection("errors", add);
    }

    public void setStatusDetailsAddLog(String message) {
        JSONObject add = new JSONObject();

        add.put("message", message);
        setStatusDetailsAddToSection("log", add);
    }

    // internal utility method for above
    private void setStatusDetailsAddToSection(String section, JSONObject add) {
        if (add == null) return;
        add.put("timestamp", System.currentTimeMillis());
        JSONObject sd = getStatusDetails();
        if (sd == null) sd = new JSONObject();
        if (sd.optJSONArray(section) == null) sd.put(section, new JSONArray());
        sd.getJSONArray(section).put(add);
        setStatusDetails(sd);
    }

    public JSONObject getParameters() { // only return as JSONObject!
        return Util.stringToJSONObject(parameters);
    }

    // see comment above: should this even be public?  (or exist)
    public void setParameters(String s) { // best be json, yo
        parameters = s;
    }

    public void setParameters(JSONObject j) {
        if (j == null) {
            parameters = null;

codex
The current code supports the design’s main diagnosis: `waitAndRunMatch` only waits on the caller’s IDs today, and `runDeferredMatch` bypasses the wait entirely. I’m checking the proposed gate details now, especially task status access and the SQL/JDO join assumptions.
exec
/bin/bash -lc "sed -n '180,260p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '140,210p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '690,715p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
 succeeded in 206ms:
        return false;
    }

    public boolean initiatedWithDetection() {
        if (this.parameters == null) return false;
        return this.getParameters().optBoolean("ibeis.detection", false);
    }
    public boolean initiatedWithIdentification() {
        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
    }
 */
    public int countObjectMediaAssets() {
        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
    }

    public int countObjectAnnotations() {
        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
    }

    public int countObjects() {
        return countObjectMediaAssets() + countObjectAnnotations();
    }

    // not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
    public boolean hasObjectMediaAssets() {
        return (countObjectMediaAssets() > 0);
    }

    public boolean hasObjectAnnotations() {
        return (countObjectAnnotations() > 0);
    }

    public boolean hasObjects() {
        return (countObjects() > 0);
    }

    public void setObjectMediaAssets(List<MediaAsset> mas) {
        objectMediaAssets = mas;
    }

    public void setObjectAnnotations(List<Annotation> anns) {
        objectAnnotations = anns;
    }

    public List<MediaAsset> getObjectMediaAssets() {
        return objectMediaAssets;
    }

    public List<Annotation> getObjectAnnotations() {
        return objectAnnotations;
    }

    public int numberMediaAssets() {
        return Util.collectionSize(objectMediaAssets);
    }

    public int numberAnnotations() {
        return Util.collectionSize(objectAnnotations);
    }

    // kinda for convenience?
    public boolean addObject(MediaAsset ma) {
        if (ma == null) return false;
        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
        if (!objectMediaAssets.contains(ma)) {
            objectMediaAssets.add(ma);
            return true;
        }
        return false;
    }


 succeeded in 214ms:
        map.put("encounterSubmitterId", keywordType);
        map.put("encounterUserUuid", keywordType);
        map.put("encounterLocationId", keywordType);
        map.put("encounterTaxonomy", keywordType);
        map.put("encounterProjectIds", keywordType);

        // all case-insensitive keyword-ish types
        // map.put("fubar", keywordNormalType);

        // embeddings have some metadata (algorithm etc)
        // and then the vector that is the embedding
        JSONObject embMap = new JSONObject();
        embMap.put("type", "nested");
        embMap.put("dynamic", false);
        JSONObject embProps = new JSONObject();
        embProps.put("method", keywordType);
        embProps.put("methodVersion", keywordType);
        JSONObject embVect = new JSONObject();
        // https://docs.opensearch.org/docs/latest/vector-search/creating-vector-index/
        embVect.put("type", "knn_vector");
        embVect.put("dimension", Embedding.getVectorDimension());
        embVect.put("space_type", "cosinesimil");
        embProps.put("vector", embVect);
        embMap.put("properties", embProps);
        map.put("embeddings", embMap);

        return map;
    }

    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
    throws IOException, JsonProcessingException {
        super.opensearchDocumentSerializer(jgen, myShepherd);

        jgen.writeStringField("acmId", this.getAcmId());
        jgen.writeStringField("viewpoint", this.getViewpoint());
        jgen.writeStringField("iaClass", this.getIAClass());
        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
        MediaAsset ma = this.getMediaAsset();
        if (ma != null) {
            jgen.writeNumberField("mediaAssetId", ma.getIdInt());
        }
        Encounter enc = this.findEncounter(myShepherd);
        if (enc != null) {
            jgen.writeStringField("encounterId", enc.getId());
            jgen.writeStringField("encounterSubmitterId", enc.getSubmitterID());
            jgen.writeStringField("encounterLocationId", enc.getLocationID());
            jgen.writeStringField("encounterTaxonomy", enc.getTaxonomyString());
            // per discussion on issue 874, including this in indexing, but not (yet) using in matchingSet
            jgen.writeStringField("encounterLivingStatus", enc.getLivingStatus());
            User owner = enc.getSubmitterUser(myShepherd);
            if (owner != null) jgen.writeStringField("encounterUserUuid", owner.getId());
            List<Project> projects = enc.getProjects(myShepherd);
            if (!Util.collectionIsEmptyOrNull(projects)) {
                jgen.writeArrayFieldStart("encounterProjectIds");
                for (Project proj : projects) {
                    jgen.writeString(proj.getId());
                }
                jgen.writeEndArray();
            }
            if (enc.getIndividual() != null) {
                long tod = enc.getIndividual().getTimeOfDeath();
                if (tod > 0) jgen.writeNumberField("encounterIndividualTimeOfDeath", tod);
            }
        }
        jgen.writeArrayFieldStart("embeddings");
        if (this.embeddings != null)
            for (Embedding emb : this.embeddings) {
                jgen.writeStartObject();
                jgen.writeStringField("id", emb.getId());
                jgen.writeStringField("method", emb.getMethod());
                jgen.writeStringField("methodVersion", emb.getMethodVersion());
                jgen.writeNumberField("created", emb.getCreated());

                float[] vecFloat = emb.vectorToFloatArray();
                // System.out.println("[INFO] indexing emb " + emb.getId() + " vector length " + ((vecFloat == null) ? "null" : vecFloat.length));
                if ((vecFloat != null) && (vecFloat.length > 0)) {
                    jgen.writeFieldName("vector");
                    jgen.writeStartArray();
                    for (int i = 0; i < vecFloat.length; i++) {
                        jgen.writeNumber(vecFloat[i]);
                    }

 succeeded in 234ms:
package org.ecocean;

import com.pgvector.PGvector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.jdo.Query;
import org.json.JSONObject;
import org.postgresql.jdbc.PgArray;

import org.json.JSONArray;
import org.json.JSONException;

import org.ecocean.ia.IAException;
import org.ecocean.ia.MatchResult;
import org.ecocean.ia.MLService;
import org.ecocean.ia.Task;
import org.ecocean.shepherd.core.Shepherd;

// https://github.com/pgvector/pgvector
// https://github.com/pgvector/pgvector-java
// https://www.thenile.dev/blog/pgvector_myth_debunking
// CREATE EXTENSION IF NOT EXISTS vector

public class Embedding implements java.io.Serializable {
    private String id;
    private Annotation annotation;
    private PGvector vector;
    private float[] vectorFloatArray;
    private String method;
    private String methodVersion;
    private long created;

    // for trying to query vectors of annots without embeddings
    public static int BACKGROUND_BATCH_SIZE = 50;
    public static int BACKGROUND_MINUTES = 30;

    public Embedding() {}

    public Embedding(Annotation ann, String method, String methodVersion, PGvector vec) {
        this.id = Util.generateUUID();
        this.annotation = ann;
        this.setVector(vec);
        this.method = method;
        this.methodVersion = methodVersion;
        this.created = System.currentTimeMillis();
        this.getVector();
        if (ann != null) ann.addEmbedding(this);
    }

    public Embedding(Annotation ann, String method, String methodVersion, JSONArray vecArr) {
        this(ann, method, methodVersion, (PGvector)null);
        this.setVector(vecArr);
        this.getVector();
    }

    public String getId() {
        return id;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Annotation ann) {
        this.annotation = ann;
        if (!ann.hasEmbedding(this)) ann.addEmbedding(this);
    }

    public PGvector getVector() {
        if ((vector == null) && (vectorFloatArray != null))
            vector = new PGvector(vectorFloatArray);
        return vector;
    }

    public void setVector(PGvector vec) {
        if (vec != null) {
            vectorFloatArray = vec.toArray();
        } else {
            vectorFloatArray = null;
        }
        this.vector = vec;
    }

    public void setVector(JSONArray varr) {
        this.setVector(vectorFromJSONArray(varr));
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethodVersion() {
        return methodVersion;
    }

    public void setMethodVersion(String methodVersion) {
        this.methodVersion = methodVersion;
    }

    public String getMethodDescription() {
        return ((method == null) ? "(unknown)" : method) + "-" + ((methodVersion ==
                   null) ? "(unknown version)" : methodVersion);
    }

    public long getCreated() {
        return created;
    }

    public float[] vectorToFloatArray() {
        getVector();
        if (vector == null) return null;
        return vector.toArray();

 succeeded in 262ms:
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
            myShepherd);

        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
            return true;
        }
        return false;
    }

    public void setStatus(String newStatus) {
        setModified();
        if (newStatus == null) status = null;
        else { status = newStatus; }
    }

    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }

    // this will set all date stuff based on ms since epoch
    public void setCompletionDateInMilliseconds(Long ms) {
        this.completionDateInMilliseconds = ms;
    }

    // no arg = set to now
    public void setCompletionDateInMilliseconds() {
        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
    }

exec
/bin/bash -lc "rg -n \"javax\\.jdo\\.query\\.SQL|newQuery\\(\\\"SQL\\\"|newQuery\\(\" src/main/java/org/ecocean src/test/java/org/ecocean | head -n 80" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "class QueryCacheFactory|class QueryCache|interface QueryCache|invalidateByName|getQueryCache" src/main/java/org/ecocean/cache -S' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "class QueryCacheFactory|getQueryCache|invalidateByName|iaAnnotationIdsStrict|iaImageIds|sendMediaAssetsForceId|mediaAssetToUri|validMediaAsset|parseAnnotationIdsArrayStrict" src/main/java/org/ecocean/cache src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "annotationIds|deferredMatch|mlServiceV2|runDeferredMatch|processQueueJob|requeueJob" src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/servlet/IAGateway.java' in /mnt/c/Wildbook-clean2
 succeeded in 349ms:
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {

 succeeded in 321ms:
src/main/java/org/ecocean/servlet/IAGateway.java:133:                if (j.optJSONArray("annotationIds") != null) {
src/main/java/org/ecocean/servlet/IAGateway.java:294:        JSONArray alist = j.optJSONArray("annotationIds");
src/main/java/org/ecocean/servlet/IAGateway.java:375:                jobj.getJSONObject("identify").put("annotationIds", new JSONArray());
src/main/java/org/ecocean/servlet/IAGateway.java:376:                jobj.getJSONObject("identify").getJSONArray("annotationIds").put(ann.getId());
src/main/java/org/ecocean/servlet/IAGateway.java:385:                requeueJob(jobj, true);
src/main/java/org/ecocean/servlet/IAGateway.java:410:        taskRes.put("annotationIds", jids);
src/main/java/org/ecocean/servlet/IAGateway.java:628:            mlserv.processQueueJob(jobj);
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
src/main/java/org/ecocean/ia/MlServiceProcessor.java:30: * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:        if (jobData.optBoolean("deferredMatch", false)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:56:            return runDeferredMatch(jobData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:112:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:133:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:147:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:294:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:320:                    annotationIds.add(existing.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:362:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:368:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:382:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:397:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:398:                return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:406:            annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:408:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:424:                return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:428:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:437:        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:446:        if (annotationIds == null || annotationIds.isEmpty()) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:495:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:576:    private JSONObject inferMatchConfig(List<String> annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:577:        if (annotationIds == null || annotationIds.isEmpty()) return null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:582:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:683:        payload.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:684:        payload.put("deferredMatch", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:685:        payload.put("annotationIds", new JSONArray(annotationIds));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:826:        final List<String> annotationIds;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:829:        private PersistResult(List<String> annotationIds, MlServiceJobOutcome outcome) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:830:            this.annotationIds = annotationIds;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:834:        static PersistResult ok(List<String> annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:835:            return new PersistResult(annotationIds, null);

 succeeded in 420ms:
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
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
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
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {

 succeeded in 513ms:
src/main/java/org/ecocean/MarkedIndividual.java:428:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/MarkedIndividual.java:2461:        Query query = myShepherd.getPM().newQuery(jdoql);
src/main/java/org/ecocean/MarkedIndividual.java:2564:        Query query = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.MarkedIndividual");
src/main/java/org/ecocean/MarkedIndividual.java:2645:        Query q = myShepherd.getPM().newQuery(filter);
src/main/java/org/ecocean/OccurrenceQueryProcessor.java:155:        Query query = myShepherd.getPM().newQuery(filter);
src/main/java/org/ecocean/StartupWildbook.java:492:            javax.jdo.Query q = shep.getPM().newQuery(
src/main/java/org/ecocean/StartupWildbook.java:589:            javax.jdo.Query q = shep.getPM().newQuery(
src/main/java/org/ecocean/MultiValue.java:362:        Query query = myShepherd.getPM().newQuery(filter);
src/main/java/org/ecocean/IndividualQueryProcessor.java:1218:        Query query = myShepherd.getPM().newQuery(filter);
src/main/java/org/ecocean/WBQuery.java:126:            query = myShepherd.getPM().newQuery(qString);
src/main/java/org/ecocean/Util.java:327:                Query query = myShepherd.getPM().newQuery(
src/main/java/org/ecocean/Util.java:699:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Util.java:717:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/media/FeatureType.java:69:        Query q = myShepherd.getPM().newQuery(ext);
src/main/java/org/ecocean/OpenSearch.java:970:        Query query = myShepherd.getPM().newQuery(obj.getClass(), clause);
src/main/java/org/ecocean/media/AssetStoreFactory.java:24:        Query all = myShepherd.getPM().newQuery(ext);
src/main/java/org/ecocean/media/AssetStoreFactory.java:46:        // Query acceptedAssetStores = pm.newQuery(assClass, filter);
src/main/java/org/ecocean/media/AssetStoreFactory.java:47:        Query acceptedAssetStores = pm.newQuery(assClass);
src/main/java/org/ecocean/media/MediaAssetFactory.java:36:        Query query = myShepherd.getPM().newQuery(MediaAsset.class);
src/main/java/org/ecocean/media/MediaAssetFactory.java:53:        Query query = myShepherd.getPM().newQuery(MediaAsset.class);
src/main/java/org/ecocean/Taxonomy.java:176:        Query query = myShepherd.getPM().newQuery(jdoql);
src/main/java/org/ecocean/media/AssetStore.java:196:        Query matches = myShepherd.getPM().newQuery(mac,
src/main/java/org/ecocean/media/AssetStore.java:227:        Query matches = myShepherd.getPM().newQuery(mac, "parentId == " + parent.getId());
src/main/java/org/ecocean/MetricsBot.java:116:            q = myShepherd.getPM().newQuery(filter);
src/main/java/org/ecocean/MetricsBot.java:465:            Query q = myShepherd.getPM().newQuery("SELECT count(this) FROM " + className);
src/main/java/org/ecocean/MetricsBot.java:578:                qD = myShepherd.getPM().newQuery(detectionsCompleteFilter);
src/main/java/org/ecocean/MetricsBot.java:581:                qID = myShepherd.getPM().newQuery(idCompleteFilter);
src/main/java/org/ecocean/MetricsBot.java:640:                filterTasksUsersQuery = myShepherd.getPM().newQuery(filterTasksUsers);
src/main/java/org/ecocean/identity/IdentityServiceLog.java:116:        Query qry = myShepherd.getPM().newQuery(cls,
src/main/java/org/ecocean/identity/IdentityServiceLog.java:139:        Query qry = myShepherd.getPM().newQuery(cls,
src/main/java/org/ecocean/identity/IdentityServiceLog.java:161:        Query qry = myShepherd.getPM().newQuery(cls,
src/main/java/org/ecocean/identity/IdentityServiceLog.java:182:        // Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"" + serviceName + "\" && this.objectIDs.contains(\"" + objectID +
src/main/java/org/ecocean/identity/IdentityServiceLog.java:184:        Query qry = myShepherd.getPM().newQuery(cls,
src/main/java/org/ecocean/identity/IdentityServiceLog.java:209:        Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"" + serviceName + "\"");
src/main/java/org/ecocean/identity/IdentityServiceLog.java:249:        Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"IBEISIA\"");
src/main/java/org/ecocean/opendata/OBISSeamap.java:50:        Query query = myShepherd.getPM().newQuery(jdoql);
src/main/java/org/ecocean/opendata/OBISSeamap.java:62:        query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/SurveyQueryProcessor.java:76:        Query query = myShepherd.getPM().newQuery(filter);
src/main/java/org/ecocean/identity/IBEISIAIdentificationMatchingState.java:43:        Query query = myShepherd.getPM().newQuery(
src/main/java/org/ecocean/identity/IBEISIAIdentificationMatchingState.java:55:        Query q = myShepherd.getPM().newQuery(all);
src/main/java/org/ecocean/identity/IBEISIA.java:3745:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/security/Collaboration.java:184:        Query query = myShepherd.getPM().newQuery(queryString);
src/main/java/org/ecocean/security/Collaboration.java:213:            Query query = myShepherd.getPM().newQuery(queryString);
src/main/java/org/ecocean/security/Collaboration.java:246:        Query query = myShepherd.getPM().newQuery(queryString);
src/main/java/org/ecocean/AcmIdBot.java:145:            query2 = myShepherd.getPM().newQuery(filter2);
src/main/java/org/ecocean/AcmIdBot.java:164:            query3 = myShepherd.getPM().newQuery(filter3);
src/main/java/org/ecocean/cache/CachedQuery.java:262:        Query query = myShepherd.getPM().newQuery(queryString);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:416:            Query acceptedCollabs = pm.newQuery(allCollabs);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:529:        Query qry = pm.newQuery("SELECT FROM org.ecocean.Setting WHERE group=='" + group +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:642:        Query workspaceQuery = pm.newQuery(allWorkspaces, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:660:        Query workspaceQuery = pm.newQuery(allWorkspaces, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:682:        Query mediaAssetQuery = pm.newQuery(allMediaAssets, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:705:        Query mediaAssetQuery = pm.newQuery(allMediaAssets, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:742:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:769:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:797:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:832:        Query query = pm.newQuery(filter2use);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:843:        Query query = pm.newQuery(
src/main/java/org/ecocean/shepherd/core/Shepherd.java:890:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:901:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:917:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:928:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:940:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:958:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:972:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:986:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1035:            query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1063:            query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1089:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1110:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1123:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1141:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1176:        Query query = getPM().newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1219:        Query acceptedEncounters = pm.newQuery(encClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1379:            Query acceptedKeywords = pm.newQuery(keyClass, filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1391:        Query query = pm.newQuery("SELECT org.ecocean.Taxonomy WHERE itisTsn == " + tsn);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1446:            Query query = pm.newQuery(filter);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1501:        Query itq = pm.newQuery(
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1535:            query = pm.newQuery(
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1563:            query = pm.newQuery(queryString);

exec
/bin/bash -lc "sed -n '450,540p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '680,835p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,80p' src/main/java/org/ecocean/cache/QueryCache.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,90p' src/main/java/org/ecocean/cache/QueryCacheFactory.java" in /mnt/c/Wildbook-clean2
 succeeded in 198ms:
        public final int[]  bbox;               // x,y,w,h
        public final double theta;
        public final String iaClass;            // species/class string
        public final String individualName;     // "____" if absent

        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
            String individualName) {
            this.annotationId    = annotationId;
            this.annotationAcmId = annotationAcmId;
            this.mediaAssetAcmId = mediaAssetAcmId;
            this.bbox            = bbox;
            this.theta           = theta;
            this.iaClass         = iaClass;
            this.individualName  = individualName;
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
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {
            JSONObject jo = jids.optJSONObject(i);
            if (jo == null)
                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
            String decoded = fromFancyUUID(jo);
            if (decoded == null)
                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
            ids.add(decoded);

 succeeded in 194ms:
            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
                ids.add(null);
            } else {
                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
            }
        }
        System.out.println("fromResponse ---> " + ids);
        return ids;
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

        try {
            QueryCache qc = QueryCacheFactory.getQueryCache(context);
            if (qc.getQueryByName(cacheName) != null &&
                System.currentTimeMillis() <
                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
            } else {
                jids = apiGetJSONArray("/api/annot/json/", context);
                if (jids != null) {
                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
                        new org.datanucleus.api.rest.orgjson.JSONObject();
                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                    qc.addCachedQuery(cq);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
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
        InvalidKeyException {
        return apiGetJSONArray(urlSuffix, this.context);
    }

    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        URL u = IBEISIA.iaURL(context, urlSuffix);
        JSONObject rtn = RestClient.get(u);

        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
            (rtn.optJSONArray("response") == null) ||
            !rtn.getJSONObject("status").optBoolean("success", false)) {
            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
                rtn);
            return null;
        }
        return rtn.getJSONArray("response");
    }

    public static String fromFancyUUID(JSONObject u) {
        if (u == null) return null;
        return u.optString("__UUID__", null);
    }

    public static JSONObject toFancyUUID(String u) {
        JSONObject j = new JSONObject();

        j.put("__UUID__", u);
        return j;
    }

    private static Object mediaAssetToUri(MediaAsset ma) {
        URL curl = ma.webURL();
        String urlStr = curl.toString();

        // THIS WILL BREAK if you need to append a query to the filename...
        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
        if (urlStr != null) {
            urlStr = urlStr.replaceAll("\\?", "%3F");
            if (ma.getStore() instanceof LocalAssetStore) {
                return urlStr;
            } else {
                return urlStr;
            }
        }
        return null;
    }

    // basically "should we send to IA?"
    public static boolean validMediaAsset(MediaAsset ma) {
        if (ma == null) return false;
        if (!ma.isMimeTypeMajor("image")) return false;
        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
        if (mediaAssetToUri(ma) == null) {
            System.out.println(
                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
                ma);
            return false;
        }
        return true;
    }

    // this is used to give a string to IA for annot_species_list specifially

 succeeded in 197ms:
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
}

 succeeded in 202ms:
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

exec
/bin/bash -lc "sed -n '90,150p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '178,250p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '120,180p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
 succeeded in 348ms:
    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");

        if (u == null)
            throw new MalformedURLException(
                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        int batchSize = 30;
        int numBatches = Math.round(mas.size() / batchSize + 1);

        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaImageIds = new ArrayList<String>();
        if (checkFirst) iaImageIds = iaImageIds();
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uri_list", new ArrayList<JSONObject>());
        map.put("image_uuid_list", new ArrayList<JSONObject>());
        map.put("image_unixtime_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());
        int batchCt = 1;
        JSONObject allRtn = new JSONObject();
        allRtn.put("_batchSize", batchSize);
        allRtn.put("_totalSize", mas.size());
        JSONArray bres = new JSONArray();
        for (int i = 0; i < mas.size(); i++) {
            MediaAsset ma = mas.get(i);
            if (iaImageIds.contains(ma.getAcmId())) continue;
            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
                IA.log(
                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
                    + ma.getId());
                continue;
            }
            if (!validMediaAsset(ma)) {
                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
                continue;
            }
            map.get("image_uri_list").add(mediaAssetToUri(ma));
            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());
            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_unixtime_list").add(null);
            } else {
                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
            }
            int sendSize = map.get("image_uri_list").size();
            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
                if (sendSize > 0) {
                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
                        " batches)");
                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
/*
                    if (acmIds == null) {
                        IA.log(
                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
 + rtn);
                    } else {
                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
                    }
 */
                    bres.put(rtn);
                    // initialize for next batch (if any)
                    map.put("image_uri_list", new ArrayList<JSONObject>());
                    map.put("image_uuid_list", new ArrayList<JSONObject>());
                    map.put("image_unixtime_list", new ArrayList<Integer>());

 succeeded in 350ms:
package org.ecocean.ia;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.OpenSearch;
import org.ecocean.Taxonomy;
import org.ecocean.Util;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Single-job orchestrator for ml-service v2 queue payloads.
 *
 * <p>Owns Shepherd transaction boundaries: load/revalidate, release DB while
 * the network call runs, then reopen for idempotent persistence and matching.
 * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
 * a later commit.</p>
 */
public class MlServiceProcessor {
    private static final String ACTION_PREFIX = "MlServiceProcessor.";
    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";
    private static final long VISIBILITY_TIMEOUT_MS = 5000L;

    private final String context;
    private final MlServiceClient client;

    public MlServiceProcessor(String context) {
        this(context, new MlServiceClient());
    }

    public MlServiceProcessor(String context, MlServiceClient client) {
        this.context = context;
        this.client = client;
    }

    /** Process one ml-service queue job. Returns the outcome. */
    public MlServiceJobOutcome process(JSONObject jobData) {
        if (jobData == null) {
            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
        }
        if (jobData.optBoolean("deferredMatch", false)) {
            return runDeferredMatch(jobData);
        }

        String taxonomyString = jobData.optString("taxonomyString", null);
        String taskId = jobData.optString("taskId", null);
        String encounterId = jobData.optString("encounterId", null);

        if (jobData.has("mediaAssetId")) {
            String maId = String.valueOf(jobData.opt("mediaAssetId"));
            return processDetection(jobData, taxonomyString, taskId, encounterId, maId);
        }
        if (jobData.has("annotationId")) {
            String annId = jobData.optString("annotationId", null);
            return processExtraction(jobData, taxonomyString, taskId, annId);
        }
        return MlServiceJobOutcome.validationError("INVALID_PAYLOAD",
            "neither mediaAssetId nor annotationId in payload");
    }

    private MlServiceJobOutcome processDetection(JSONObject jobData, String taxonomyString,
        String taskId, String encounterId, String maId) {
        DetectionContext det = null;

        try {
            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        }
        if (det.outcome != null) return det.outcome;

        JSONObject response;
        try {
            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
        } catch (IAException ex) {
            if (ex.shouldRequeue()) {
                IAGateway.requeueJob(jobData, ex.shouldIncrement());
                return MlServiceJobOutcome.requeue();
            }
            markTaskError(taskId, ex.getCode(), ex.getMessage());
            return mapNonRetryableError(ex);
        }

        JSONArray results = response.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return finalizeZeroDetections(maId, taskId);
        }

        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results);
        if (persisted.outcome != null) return persisted.outcome;

        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
            det.mlConfig);
        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
            matchConfig);
        if (matchOutcome != null) return matchOutcome;
        return MlServiceJobOutcome.ok(persisted.annotationIds);
    }

    private MlServiceJobOutcome processExtraction(JSONObject jobData, String taxonomyString,
        String taskId, String annId) {
        ExtractionContext ext = null;

        try {
            ext = loadExtractionContext(taxonomyString, taskId, annId);

 succeeded in 355ms:
 */
    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");

        if (u == null)
            throw new MalformedURLException(
                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        int batchSize = 30;
        int numBatches = Math.round(mas.size() / batchSize + 1);

        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaImageIds = new ArrayList<String>();
        if (checkFirst) iaImageIds = iaImageIds();
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uri_list", new ArrayList<JSONObject>());
        map.put("image_uuid_list", new ArrayList<JSONObject>());
        map.put("image_unixtime_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());
        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
        int batchCt = 1;
        JSONObject allRtn = new JSONObject();
        allRtn.put("_batchSize", batchSize);
        allRtn.put("_totalSize", mas.size());
        JSONArray bres = new JSONArray();
        for (int i = 0; i < mas.size(); i++) {
            MediaAsset ma = mas.get(i);
            if (iaImageIds.contains(ma.getAcmId())) continue;
            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
                IA.log(
                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
                    + ma.getId());
                continue;
            }
            if (!validMediaAsset(ma)) {
                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
                continue;
            }
            acmList.add(ma);
            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
            map.get("image_uri_list").add(mediaAssetToUri(ma));
            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());
            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_unixtime_list").add(null);
            } else {
                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
            }
            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
                if (acmList.size() > 0) {
                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
                        " batches)");
                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
                    List<String> acmIds = acmIdsFromResponse(rtn);

 succeeded in 349ms:
            ext = loadExtractionContext(taxonomyString, taskId, annId);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        }
        if (ext.outcome != null) return ext.outcome;

        JSONObject response;
        try {
            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
                ext.mlConfig);
        } catch (IAException ex) {
            if (ex.shouldRequeue()) {
                IAGateway.requeueJob(jobData, ex.shouldIncrement());
                return MlServiceJobOutcome.requeue();
            }
            markTaskError(taskId, ex.getCode(), ex.getMessage());
            return mapNonRetryableError(ex);
        }

        PersistResult persisted = persistExtraction(annId, taskId, ext, response);
        if (persisted.outcome != null) return persisted.outcome;

        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
            matchConfig);
        if (matchOutcome != null) return matchOutcome;
        return MlServiceJobOutcome.ok(persisted.annotationIds);
    }

    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
        String encounterId, String maId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
        try {
            FeatureType.initAll(shep);
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
            Task task = Task.load(taskId, shep);

            String staleReason = detectionStaleReason(ma, enc, encounterId);
            if (staleReason != null) {
                markTaskDroppedStale(shep, task, staleReason);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
            }

            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
            if (configs == null) {
                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
                markTaskCompleted(task);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
            }

            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
                markTaskError(task, "INVALID",
                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
                shep.commitDBTransaction();

exec
/bin/bash -lc "sed -n '610,650p' src/main/java/org/ecocean/servlet/IAGateway.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '250,375p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '375,415p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '360,450p' src/test/java/org/ecocean/ia/MlServiceProcessorTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 233ms:
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.validationError("INVALID_BBOX",
                    "Annotation " + annId + " has no bbox"));
            }

            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
            shep.commitDBTransaction();
            return new ExtractionContext(webUrl.toString(),
                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
        } finally {
            shep.rollbackAndClose();
        }
    }

    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
        try {
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Task task = Task.load(taskId, shep);
            String staleReason = detectionStaleReason(ma, null, null);
            if (staleReason != null) {
                markTaskDroppedStale(shep, task, staleReason);
                shep.commitDBTransaction();
                return MlServiceJobOutcome.stale(staleReason);
            }
            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            markTaskCompleted(task);
            shep.commitDBTransaction();
            return MlServiceJobOutcome.okZeroDetections();
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "zero-detection finalize failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        } finally {
            shep.rollbackAndClose();
        }
    }

    private PersistResult persistDetections(String maId, String encounterId, String taskId,
        DetectionContext det, JSONArray results) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "persistDetections");
        List<String> annotationIds = new ArrayList<String>();

        try {
            FeatureType.initAll(shep);
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
            Task task = Task.load(taskId, shep);
            String staleReason = detectionStaleReason(ma, enc, encounterId);
            if (staleReason != null) {
                markTaskDroppedStale(shep, task, staleReason);
                shep.commitDBTransaction();
                return PersistResult.done(MlServiceJobOutcome.stale(staleReason));
            }

            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                double[] bbox = parseBbox(result.getJSONArray("bbox"));
                double theta = result.getDouble("theta");
                String bboxKey = bboxKey(bbox);
                String thetaKey = thetaKey(theta);
                String predictModelId = result.optString("predict_model_id",
                    det.mlConfig.optString("predict_model_id", null));
                Annotation existing = findExistingAnnotation(ma, predictModelId, bboxKey,
                    thetaKey);
                if (existing != null) {
                    annotationIds.add(existing.getId());
                    continue;
                }

                JSONObject featureParams = featureParams(bbox, theta,
                    result.optString("viewpoint", null));
                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
                String iaClass = result.optString("iaClass",
                    result.optString("class_name", result.optString("class", null)));
                Annotation ann = new Annotation(null, feature, iaClass);
                ann.__setMediaAsset(ma);
                ann.setAcmId(ann.getId());
                ann.setMatchAgainst(true);
                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
                ann.setPredictModelId(predictModelId);
                ann.setBboxKey(bboxKey);
                ann.setThetaKey(thetaKey);
                ann.setWbiaRegistered(Boolean.FALSE);
                ann.setWbiaRegisterAttempts(0);
                ann.setViewpoint(result.optString("viewpoint", null));
                ann.setQuality(optionalFiniteDouble(result, "score",
                    optionalFiniteDouble(result, "confidence", null)));

                // Bidirectional linkage:
                //   MediaAsset.addFeature sets Feature.asset
                //   Feature.setAnnotation sets the OWNING side of the
                //     Annotation.features collection (which is mapped-by
                //     "annotation"). Without explicitly setting this, the
                //     ANNOTATION_FEATURES join row depends on DataNucleus
                //     relationship management — fragile.
                //   Annotation.addFeature puts the feature in the in-memory
                //     list so reload returns it as expected.
                ma.addFeature(feature);
                feature.setAnnotation(ann);
                ann.addFeature(feature);
                if (enc != null) enc.addAnnotation(ann);
                shep.getPM().makePersistent(feature);
                shep.getPM().makePersistent(ann);

                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
                shep.getPM().makePersistent(emb);
                annotationIds.add(ann.getId());
            }

            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            markTaskCompleted(task);
            shep.commitDBTransaction();
            return PersistResult.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "detection persist failed: " + ex.getMessage());
            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
                ex.getMessage()));
        } finally {
            shep.rollbackAndClose();
        }

 succeeded in 235ms:
        JSONObject jobj = null;
        try {
            jobj = new JSONObject(message);
        } catch (org.json.JSONException jex) {
            System.out.println(
                "WARNING: IAGateway.processQueueMessage() failed to parse json from '" + message +
                "' - " + jex.toString());
            return;
        }
        if (jobj == null) return; // would this ever happen? #bsts
        // this must have a taskId coming in, cuz otherwise how would (detached, async) caller know what it is!
        // __context and __baseUrl should be set -- this is done automatically in IAGateway, but if getting here by some other method, do the work!
        if (jobj.optBoolean("v2", false)) { // lets "new world" ia package do its thing
            IA.handleRest(jobj);
            return;
        }
        if (jobj.optBoolean("MLService", false)) {
            MLService mlserv = new MLService();
            mlserv.processQueueJob(jobj);
            return;
        }
        // Migration plan v2 §commit #10a: ml-service v2 dispatcher branch.
        // The new processor takes context in its constructor (no hardcoded
        // "context0") and returns a typed outcome. Both the detection +
        // extraction lifecycle (Phases 1-5) and the deferred-match path
        // route here; MlServiceProcessor.process(jobj) handles routing
        // internally based on the payload's flags.
        if (jobj.optBoolean("mlServiceV2", false)) {
            String mlContext = jobj.optString("__context", "context0");
            org.ecocean.ia.MlServiceProcessor processor =
                new org.ecocean.ia.MlServiceProcessor(mlContext);
            org.ecocean.ia.MlServiceJobOutcome outcome = processor.process(jobj);
            System.out.println("IAGateway: mlServiceV2 job " +
                jobj.optString("taskId", "?") + " → " + outcome.getKind() +
                (outcome.getCode() == null ? "" : " [" + outcome.getCode() + "]"));
            return;
        }
        boolean requeue = false;
        boolean requeueIncrement = false;
        if ((jobj.optJSONObject("detect") != null) && (jobj.optString("taskId", null) != null)) {
            JSONObject res = new JSONObject("{\"success\": false}");

 succeeded in 239ms:
 succeeded in 243ms:
        }
    }

    private PersistResult persistExtraction(String annId, String taskId, ExtractionContext ext,
        JSONObject response) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "persistExtraction");
        List<String> annotationIds = new ArrayList<String>();

        try {
            shep.beginDBTransaction();
            Annotation ann = shep.getAnnotation(annId);
            Task task = Task.load(taskId, shep);
            if (ann == null || ann.getMediaAsset() == null) {
                markTaskDroppedStale(shep, task, "annotation missing");
                shep.commitDBTransaction();
                return PersistResult.done(MlServiceJobOutcome.stale("annotation missing"));
            }
            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
                markTaskCompleted(task);
                shep.commitDBTransaction();
                annotationIds.add(ann.getId());
                return PersistResult.ok(annotationIds);
            }

            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
            shep.getPM().makePersistent(emb);
            markTaskCompleted(task);
            annotationIds.add(ann.getId());
            shep.commitDBTransaction();
            return PersistResult.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
                ex.getMessage()));
        } finally {
            shep.rollbackAndClose();
        }

exec
/bin/bash -lc "sed -n '1,220p' src/test/java/org/ecocean/OpenSearchVisibilityTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,220p' src/test/java/org/ecocean/ia/MlServiceProcessorTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,220p' src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '520,725p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 245ms:
package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * v2 commit #7: shape-of-query tests for OpenSearch.buildIdEligibilityQuery.
 * The full poll-and-wait behavior of waitForVisibility requires a real
 * OpenSearch (or a heavily mocked RestClient); the eligibility-query JSON
 * shape is the one purely-mechanical part that benefits from a unit test.
 *
 * Uses the OpenSearch idiomatic `ids` query rather than the generic `terms`
 * query on `_id`, per OpenSearch docs.
 */
class OpenSearchVisibilityTest {

    @Test void buildIdEligibilityQuery_shapeForMultipleIds() {
        Set<String> ids = new LinkedHashSet<String>(
            Arrays.asList("ann-1", "ann-2", "ann-3"));
        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
        assertNotNull(q);
        JSONObject inner = q.optJSONObject("query");
        assertNotNull(inner);
        JSONObject idsClause = inner.optJSONObject("ids");
        assertNotNull(idsClause);
        JSONArray values = idsClause.optJSONArray("values");
        assertNotNull(values);
        assertEquals(3, values.length());
        assertEquals("ann-1", values.getString(0));
        assertEquals("ann-2", values.getString(1));
        assertEquals("ann-3", values.getString(2));
    }

    @Test void buildIdEligibilityQuery_emptySet() {
        Set<String> ids = new LinkedHashSet<String>();
        JSONObject q = OpenSearch.buildIdEligibilityQuery(ids);
        JSONArray values = q.getJSONObject("query")
            .getJSONObject("ids")
            .getJSONArray("values");
        assertEquals(0, values.length());
    }

    @Test void buildIdEligibilityQuery_preservesInsertionOrder() {
        // LinkedHashSet preserves insertion order, which is useful for stable
        // OpenSearch query-cache keys.
        Set<String> ids = new LinkedHashSet<String>();
        ids.add("z");
        ids.add("a");
        ids.add("m");
        JSONArray values = OpenSearch.buildIdEligibilityQuery(ids)
            .getJSONObject("query")
            .getJSONObject("ids")
            .getJSONArray("values");
        assertEquals("z", values.getString(0));
        assertEquals("a", values.getString(1));
        assertEquals("m", values.getString(2));
    }

    @Test void buildIdEligibilityQuery_singletonShape() {
        // Sanity check: the JSON shape with one element matches the OpenSearch
        // docs example for the `ids` query.
        Set<String> ids = new LinkedHashSet<String>();
        ids.add("only-one");
        String expected = "{\"query\":{\"ids\":{\"values\":[\"only-one\"]}}}";
        JSONObject actual = OpenSearch.buildIdEligibilityQuery(ids);
        assertEquals(expected, actual.toString());
    }
}

 succeeded in 240ms:
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * v2 commit #9: pure-logic tests for MlServiceProcessor.
 *
 * <p>The Phase 1-5 lifecycle methods require real Shepherd transactions,
 * JDO mutations, and live OpenSearch — those are reviewable by diff and
 * exercised by hand-test per the v2 plan's test-strategy decision
 * (WireMock unit tests only). Here we cover:</p>
 *
 * <ul>
 *   <li>Top-level {@code process()} payload routing (validation errors,
 *       missing-payload-fields branches).</li>
 *   <li>{@code mapNonRetryableError(IAException)} maps each typed code
 *       to the right outcome Kind.</li>
 *   <li>{@code bboxKey}/{@code thetaKey} formatting (rounding and
 *       string-format invariants).</li>
 *   <li>{@code findExistingAnnotation} dedupe matching.</li>
 * </ul>
 */
class MlServiceProcessorTest {

    // --- process() payload routing -------------------------------------

    @Test void processRejectsNullPayload() {
        MlServiceProcessor p = new MlServiceProcessor("context0");
        MlServiceJobOutcome out = p.process(null);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("INVALID_PAYLOAD", out.getCode());
    }

    @Test void processRejectsPayloadWithoutMediaAssetOrAnnotationId() {
        MlServiceProcessor p = new MlServiceProcessor("context0");
        JSONObject payload = new JSONObject()
            .put("mlServiceV2", true)
            .put("taxonomyString", "Rhincodon typus");
        MlServiceJobOutcome out = p.process(payload);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("INVALID_PAYLOAD", out.getCode());
        assertNotNull(out.getMessage());
    }

    // --- mapNonRetryableError ------------------------------------------

    @Test void mapNonRetryableInvalidIsValidationError() {
        IAException ex = new IAException("INVALID", "bad bbox", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("INVALID", out.getCode());
    }

    @Test void mapNonRetryableSuccessFalseIsValidationError() {
        IAException ex = new IAException("SUCCESS_FALSE",
            "ml-service success=false", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("SUCCESS_FALSE", out.getCode());
    }

    @Test void mapNonRetryableNetworkIsNetworkError() {
        IAException ex = new IAException("NETWORK",
            "ml-service 502", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
        assertEquals("NETWORK", out.getCode());
    }

    @Test void mapNonRetryableTimeoutIsNetworkError() {
        IAException ex = new IAException("TIMEOUT",
            "ml-service read timed out", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    }

    @Test void mapNonRetryableClientErrorIsNetworkError() {
        // 4xx surfaces as CLIENT_ERROR from the client. The processor's
        // contract: anything not VALIDATION* maps to NETWORK (with code preserved).
        IAException ex = new IAException("CLIENT_ERROR",
            "ml-service 400", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
        assertEquals("CLIENT_ERROR", out.getCode());
    }

    @Test void mapNonRetryableUnknownCodeIsNetworkError() {
        IAException ex = new IAException("WEIRD_CODE",
            "something happened", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        // Defensive default: unknown codes route to NETWORK rather than
        // crashing the processor.
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    }

    @Test void mapNonRetryableNullCode() {
        // IAException constructed with the legacy 1/2/3-arg constructors
        // leaves code null. Treat as ERROR_NETWORK.
        IAException ex = new IAException("legacy message");
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    }

    // --- bboxKey / thetaKey -------------------------------------------

    @Test void bboxKeyRoundsToInts() {
        // The composite-unique-index columns are literal strings so we get
        // debugability over hash opacity. Rounded ints from a 4-element double[].
        assertEquals("10:20:30:40",
            MlServiceProcessor.bboxKey(new double[] { 10.0, 20.0, 30.0, 40.0 }));
        assertEquals("10:20:30:40",
            MlServiceProcessor.bboxKey(new double[] { 10.4, 20.4, 30.4, 40.4 }));
        assertEquals("11:21:31:41",
            MlServiceProcessor.bboxKey(new double[] { 10.5, 20.5, 30.5, 40.5 }));
    }

    @Test void thetaKeyRoundsToFourDecimals() {
        assertEquals("0.0000", MlServiceProcessor.thetaKey(0.0));
        assertEquals("3.1416", MlServiceProcessor.thetaKey(3.1415926));
        // Negative angles round symmetrically.
        assertEquals("-1.5708", MlServiceProcessor.thetaKey(-1.5707963));
    }

    @Test void thetaKeyHandlesNegativeZero() {
        // Negative zero formats the same as positive zero, matching the
        // expected key for "theta is zero".
        String k = MlServiceProcessor.thetaKey(-0.0);
        assertTrue(k.equals("0.0000") || k.equals("-0.0000"),
            "unexpected thetaKey for -0.0: " + k);
    }
}

 succeeded in 257ms:
package org.ecocean.ia;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * v2 commit #5: Task.statusInEndState recognizes "dropped-stale" as terminal.
 * The inactivity-timeout watchdog must not flip a deliberately-dropped task
 * (target deleted before the queued ml-service job ran) to "error".
 */
class TaskStatusInEndStateTest {

    @Test void completedIsTerminal() {
        Task t = new Task();
        t.setStatus("completed");
        assertTrue(t.statusInEndState());
    }

    @Test void errorIsTerminal() {
        Task t = new Task();
        t.setStatus("error");
        assertTrue(t.statusInEndState());
    }

    @Test void droppedStaleIsTerminal() {
        Task t = new Task();
        t.setStatus("dropped-stale");
        assertTrue(t.statusInEndState());
    }

    @Test void initiatedIsNotTerminal() {
        Task t = new Task();
        t.setStatus("initiated");
        assertFalse(t.statusInEndState());
    }

    @Test void typoIsNotTerminal() {
        // "completed-foo" must not accidentally pass the terminal check.
        Task t = new Task();
        t.setStatus("completed-foo");
        assertFalse(t.statusInEndState());
    }
}

 succeeded in 258ms:
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
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {
            JSONObject jo = jids.optJSONObject(i);
            if (jo == null)
                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
            String decoded = fromFancyUUID(jo);
            if (decoded == null)
                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
            ids.add(decoded);
        }
        return ids;
    }

    static List<String> parseAnnotationIdsArray(JSONArray jids) {
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {
            JSONObject jo = jids.optJSONObject(i);
            if (jo != null) ids.add(fromFancyUUID(jo));
        }
        return ids;
    }

    /**
     * Build the forced-id POST body for a single DTO. Pure function;
     * factored out so unit tests can verify the request shape without
     * a network round trip.
     */
    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uuid_list", new ArrayList<JSONObject>());
        map.put("annot_uuid_list", new ArrayList<JSONObject>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());
        map.put("annot_theta_list", new ArrayList<Double>());
        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
        map.get("annot_species_list").add(dto.iaClass);
        map.get("annot_bbox_list").add(dto.bbox);
        map.get("annot_name_list").add(
            (dto.individualName == null) ? "____" : dto.individualName);
        map.get("annot_theta_list").add(dto.theta);
        return map;
    }

    /**
     * Validate a forced-id response. Throws on any contract violation
     * (length mismatch, missing entry, id mismatch). Pure function.
     */
    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
        if (resp == null) throw new IOException("null forced-id response");
        if (resp.has("status")) {
            JSONObject status = resp.optJSONObject("status");
            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
                throw new IOException("forced-id response status.success=false: " + resp);
            }
        }
        JSONArray respArr = resp.optJSONArray("response");
        if (respArr == null) throw new IOException("no response array: " + resp);
        if (respArr.length() != 1)
            throw new IOException("expected response array length 1, got " + respArr.length());
        JSONObject jid = respArr.optJSONObject(0);
        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
        String respId = fromFancyUUID(jid);
        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
        if (!respId.equals(sentAnnotId))
            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
    }

    /**
     * Phase B entry point. Does the already-present check, builds the
     * forced-id POST, fires it, and classifies the outcome. Does NOT
     * touch any Shepherd or JDO state; callers must hand it a DTO that
     * was pre-validated and detached in Phase A.
     */
    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
        if (u == null) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        List<String> known;
        try {
            known = iaAnnotationIdsStrict(context);
        } catch (IOException ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
                ex.getMessage());
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
        // only check the annotation's id and acmId here. Comparing against
        // the media-asset's acmId is wrong - that would compare an image
        // identifier against a list of annotation identifiers.
        if (known.contains(dto.annotationId) ||
            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
        }
        URL url;
        try {
            url = new URL(u);
        } catch (MalformedURLException ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
        JSONObject rtn;
        try {
            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
        } catch (Exception ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        try {
            validateForcedResponse(dto.annotationId, rtn);
        } catch (IOException ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
            return WbiaRegisterOutcome.RESPONSE_BAD;
        }
        return WbiaRegisterOutcome.REGISTERED_OK;
    }

    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
    throws IOException {
        if ((sentIds == null) || (respArr == null))
            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
        if (sentIds.size() != respArr.length())
            throw new IOException("args diff length: " + sentIds.size() + " != " +
                    respArr.length());
        for (int i = 0; i < sentIds.size(); i++) {
            String sentId = fromFancyUUID(sentIds.get(i));
            if (sentId == null)
                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
            JSONObject jid = respArr.optJSONObject(i);
            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
            String respId = fromFancyUUID(jid);
            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
            if (!respId.equals(sentId))
                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
                        "; respId=" + respId);
        }
    }

    public static List<String> acmIdsFromResponse(JSONObject rtn) {
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
                ids.add(null);
            } else {
                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
            }
        }
        System.out.println("fromResponse ---> " + ids);
        return ids;
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

        try {
            QueryCache qc = QueryCacheFactory.getQueryCache(context);
            if (qc.getQueryByName(cacheName) != null &&
                System.currentTimeMillis() <
                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
            } else {
                jids = apiGetJSONArray("/api/annot/json/", context);
                if (jids != null) {
                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
                        new org.datanucleus.api.rest.orgjson.JSONObject();
                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                    qc.addCachedQuery(cq);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
                ex.toString());
        }

exec
/bin/bash -lc 'rg -n "WbiaRegister|registerOne|wbia|WbiaRegisterOutcome|Park|park|wbiaRegister" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "wbiaRegistered|WbiaRegister|register.*WBIA|Phase A|Phase B|Phase C|Annotation.*registered" src/main/java src/test/java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,220p' src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '220,420p' src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 221ms:
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * ml-service migration v2 §commit #11 fix-pass. Pure-function coverage of
 * the no-Shepherd WBIA registration helpers introduced in
 * {@link org.ecocean.ia.plugin.WildbookIAM}. Network-bound behavior of
 * {@code registerOneByDto} itself is exercised end-to-end by the
 * polling thread integration in a dev deployment; here we cover the
 * pieces that can be tested without WireMock/Tomcat.
 */
class WildbookIAMRegisterTest {

    private static WbiaRegisterRequest sampleDto() {
        return new WbiaRegisterRequest(
            "ann-uuid-1", "ann-acm-1", "ma-acm-1",
            new int[] { 10, 20, 100, 200 },
            0.0d, "right_dorsalfin", "indiv-1");
    }

    // --- buildForcedRequestMap -------------------------------------------

    @Test void buildForcedRequestMapPopulatesAllLists() {
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
        assertEquals(1, map.get("image_uuid_list").size());
        assertEquals(1, map.get("annot_uuid_list").size());
        assertEquals(1, map.get("annot_species_list").size());
        assertEquals(1, map.get("annot_bbox_list").size());
        assertEquals(1, map.get("annot_name_list").size());
        assertEquals(1, map.get("annot_theta_list").size());
        assertEquals("right_dorsalfin", map.get("annot_species_list").get(0));
        assertEquals("indiv-1", map.get("annot_name_list").get(0));
    }

    @Test void buildForcedRequestMapWrapsUuidsInFancyForm() {
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
        JSONObject annUuid = (JSONObject) map.get("annot_uuid_list").get(0);
        JSONObject imgUuid = (JSONObject) map.get("image_uuid_list").get(0);
        assertEquals("ann-uuid-1", WildbookIAM.fromFancyUUID(annUuid));
        assertEquals("ma-acm-1",   WildbookIAM.fromFancyUUID(imgUuid));
    }

    @Test void buildForcedRequestMapNullIndividualSerializesUnderscores() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-2", "ann-acm-2", "ma-2", new int[] { 0, 0, 1, 1 },
            1.5d, "iaClass", null);
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(dto);
        assertEquals("____", map.get("annot_name_list").get(0));
    }

    // --- validateForcedResponse ------------------------------------------

    @Test void validateForcedResponseAcceptsMatchingId() throws IOException {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("ann-uuid-1")));
        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
    }

    @Test void validateForcedResponseRejectsNull() {
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("x", null));
    }

    @Test void validateForcedResponseRejectsMissingArray() {
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("x", new JSONObject()));
    }

    @Test void validateForcedResponseRejectsWrongLength() {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("a")).put(makeFancy("b")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("a", resp));
    }

    @Test void validateForcedResponseRejectsIdMismatch() {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("other-id")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
    }

    @Test void validateForcedResponseRejectsStatusSuccessFalse() {
        JSONObject resp = new JSONObject()
            .put("status", new JSONObject().put("success", false))
            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
    }

    @Test void validateForcedResponseTreatsStatusSuccessTrueAsOK() throws IOException {
        JSONObject resp = new JSONObject()
            .put("status", new JSONObject().put("success", true))
            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
    }

    // --- parseAnnotationIdsArray -----------------------------------------

    @Test void parseAnnotationIdsArrayReturnsEmptyOnNull() {
        assertTrue(WildbookIAM.parseAnnotationIdsArray(null).isEmpty());
    }

    @Test void parseAnnotationIdsArrayExtractsUuids() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(makeFancy("u2"));
        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("u1"));
        assertTrue(ids.contains("u2"));
    }

    @Test void parseAnnotationIdsArraySkipsNonObjectEntries() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put("not-an-object");
        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
        assertEquals(1, ids.size());
        assertEquals("u1", ids.get(0));
    }

    // --- parseAnnotationIdsArrayStrict -----------------------------------

    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
    }

    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(makeFancy("u2"));
        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("u1"));
        assertTrue(ids.contains("u2"));
    }

    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put("not-an-object");
        assertThrows(IOException.class,
            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
    }

    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(new JSONObject().put("not_uuid_key", "x"));
        assertThrows(IOException.class,
            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
    }

    // --- helpers ---------------------------------------------------------

    /**
     * Builds a "fancy UUID" wrapper that {@link WildbookIAM#fromFancyUUID}
     * decodes back to the raw string. The wire format ({@code __UUID__})
     * is opaque to this test, so go through the public factory.
     */
    private static JSONObject makeFancy(String raw) {
        JSONObject jo = WildbookIAM.toFancyUUID(raw);
        assertNotNull(jo, "toFancyUUID returned null for " + raw);
        return jo;
    }
}

 succeeded in 312ms:
 succeeded in 1070ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
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
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
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
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:16:    private static volatile JSONObject wbiaQueue = new JSONObject();
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:17:    private static String cacheName = "wbiaQueue";
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:50:                wbiaQueue = Util.toggleJSONObject(existingCache.getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:72:                wbiaQueue = Util.toggleJSONObject(existingCache.getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:88:                URL wbiaQueueUrl = IBEISIA.iaURL(context, "api/engine/job/status/");
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:89:                wbiaQueue = Util.toggleJSONObject(RestClient.get(wbiaQueueUrl, 90000));
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:90:                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(wbiaQueue));
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:105:                JSONObject inspectMe = wbiaQueue.getJSONObject("response").getJSONObject(
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:190:            JSONObject inspectMe = wbiaQueue.getJSONObject("response").getJSONObject("json_result");
src/main/java/org/ecocean/ia/Task.java:833:            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
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
src/main/java/org/ecocean/ia/MlServiceProcessor.java:337:                ann.setWbiaRegistered(Boolean.FALSE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:338:                ann.setWbiaRegisterAttempts(0);
src/main/java/org/ecocean/ia/MatchResult.java:521:        Boolean wbiaReg = ann.getWbiaRegistered();
src/main/java/org/ecocean/ia/MatchResult.java:522:        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/main/java/org/ecocean/MetricsBot.java:353:                String wbiaMetrics = httpGetRemoteText(metricsURL);
src/main/java/org/ecocean/MetricsBot.java:356:                String regexTT = "wbia_turnaround_seconds\\{endpoint=\"\\*\".*\\} \\d*\\.\\d*";
src/main/java/org/ecocean/MetricsBot.java:357:                String promValueTT = getWBIAPrometheusClientValue(wbiaMetrics, regexTT);
src/main/java/org/ecocean/MetricsBot.java:359:                    csvLines.add("wildbook_wbia_turnaroundtime" + "," + promValueTT + "," +
src/main/java/org/ecocean/MetricsBot.java:364:                    "wbia_turnaround_seconds\\{endpoint=\"/api/engine/detect/cnn/lightnet/\".*\\} \\d*\\.\\d*";
src/main/java/org/ecocean/MetricsBot.java:365:                String promValueDetect = getWBIAPrometheusClientValue(wbiaMetrics, regexTTdetect);
src/main/java/org/ecocean/MetricsBot.java:367:                    csvLines.add("wildbook_wbia_turnaroundtime_detection" + "," + promValueDetect +
src/main/java/org/ecocean/MetricsBot.java:372:                    "wbia_turnaround_seconds\\{endpoint=\"/api/engine/query/graph/\".*\\} \\d*\\.\\d*";
src/main/java/org/ecocean/MetricsBot.java:373:                String promValueID = getWBIAPrometheusClientValue(wbiaMetrics, regexTTgraph);
src/main/java/org/ecocean/MetricsBot.java:375:                    csvLines.add("wildbook_wbia_turnaroundtime_id" + "," + promValueID + "," +
src/main/java/org/ecocean/StartupWildbook.java:49:    private static volatile ScheduledExecutorService wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:439:     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:453:        if (wbiaRegisterExecutor != null) {
src/main/java/org/ecocean/StartupWildbook.java:481:        wbiaRegisterExecutor = ses;
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:496:            q.setOrdering("wbiaRegisterAttempts ascending");
src/main/java/org/ecocean/StartupWildbook.java:523:                wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:527:            registerOneAnnotationWithWbia(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:683:            System.out.println("WARN: StaleMlServiceReconciliation registerOne failed for " +
src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
src/main/java/org/ecocean/StartupWildbook.java:703:    private static void registerOneAnnotationWithWbia(String context, String annId) {
src/main/java/org/ecocean/StartupWildbook.java:705:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:706:            loadWbiaRegisterDto(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:707:        if (dto == null) return;  // ineligible / already registered / parked
src/main/java/org/ecocean/StartupWildbook.java:713:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);
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

 succeeded in 1072ms:
src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
src/main/java/org/ecocean/Annotation.java:80:    protected Boolean wbiaRegistered;
src/main/java/org/ecocean/Annotation.java:336:    public Boolean getWbiaRegistered() { return wbiaRegistered; }
src/main/java/org/ecocean/Annotation.java:337:    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:341:    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
src/main/java/org/ecocean/Annotation.java:343:    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
src/main/java/org/ecocean/Annotation.java:344:    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:345:    public void incrementWbiaRegisterAttempts() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:416:    //   Phase A (write tx) - load DTO + close.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:417:    //   Phase B (no DB)    - call into the helpers below.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:418:    //   Phase C (write tx) - persist result.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:419:    // Phase B must not hold a Shepherd transaction across the WBIA call.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:438:     * Plain-data DTO that holds everything Phase B needs about one
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:439:     * Annotation. Built under a Shepherd transaction in Phase A, then
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:440:     * passed across the close/open boundary into Phase B.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:442:     * <p>Phase A is responsible for pre-validating that all required
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:443:     * fields are populated; Phase B treats the DTO as opaque and does
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:470:     * fetch failure rather than returning an empty list. Phase B needs
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:560:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:603:     * Phase B entry point. Does the already-present check, builds the
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:606:     * was pre-validated and detached in Phase A.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:337:                ann.setWbiaRegistered(Boolean.FALSE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:338:                ann.setWbiaRegisterAttempts(0);
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:427:     * registers ml-service-created annotations with WBIA so HotSpotter is
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:519:        // the per-tick work even if the in-flight Phase B HTTP call ran
src/main/java/org/ecocean/StartupWildbook.java:692:     * Phase A/B/C split per Codex c11 fix-review.
src/main/java/org/ecocean/StartupWildbook.java:694:     *   <li>Phase A: Shepherd open, re-check state, build DTO, close.
src/main/java/org/ecocean/StartupWildbook.java:695:     *   <li>Phase B: no Shepherd held; WBIA HTTP via
src/main/java/org/ecocean/StartupWildbook.java:697:     *   <li>Phase C: Shepherd open, re-load, persist outcome, close.
src/main/java/org/ecocean/StartupWildbook.java:704:        // ---- Phase A: load DTO under a short transaction. ----
src/main/java/org/ecocean/StartupWildbook.java:705:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:706:            loadWbiaRegisterDto(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:710:        // shutdown was requested while Phase A was running. Otherwise we
src/main/java/org/ecocean/StartupWildbook.java:714:            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
src/main/java/org/ecocean/StartupWildbook.java:719:        // ---- Phase B: no Shepherd held; call WBIA. ----
src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
src/main/java/org/ecocean/StartupWildbook.java:725:        // Skip Phase C if shutdown has been requested while Phase B ran.
src/main/java/org/ecocean/StartupWildbook.java:726:        // RestClient is not interruptible mid-IO, so Phase B can outlive
src/main/java/org/ecocean/StartupWildbook.java:727:        // awaitTermination; this prevents Phase C from racing the rest of
src/main/java/org/ecocean/StartupWildbook.java:731:            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
src/main/java/org/ecocean/StartupWildbook.java:736:        // ---- Phase C: persist outcome under a short transaction. ----
src/main/java/org/ecocean/StartupWildbook.java:737:        persistWbiaRegisterResult(context, annId, outcome);
src/main/java/org/ecocean/StartupWildbook.java:741:     * Phase A. Returns a detached DTO ready for Phase B, or null if the
src/main/java/org/ecocean/StartupWildbook.java:747:    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
src/main/java/org/ecocean/StartupWildbook.java:748:        loadWbiaRegisterDto(String context, String annId) {
src/main/java/org/ecocean/StartupWildbook.java:758:            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
src/main/java/org/ecocean/StartupWildbook.java:762:            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:778:                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:783:            // Phase B has no DB access.
src/main/java/org/ecocean/StartupWildbook.java:788:            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:789:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
src/main/java/org/ecocean/StartupWildbook.java:795:            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
src/main/java/org/ecocean/StartupWildbook.java:805:     * Phase C. Re-loads the annotation and writes the outcome of the
src/main/java/org/ecocean/StartupWildbook.java:810:    private static void persistWbiaRegisterResult(String context, String annId,
src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
src/main/java/org/ecocean/StartupWildbook.java:821:            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
src/main/java/org/ecocean/StartupWildbook.java:822:                // Some other path flipped it while Phase B ran; respect that.
src/main/java/org/ecocean/StartupWildbook.java:832:                    ann.setWbiaRegistered(Boolean.TRUE);
src/main/java/org/ecocean/StartupWildbook.java:837:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:841:                    ann.incrementWbiaRegisterAttempts();
src/main/java/org/ecocean/StartupWildbook.java:842:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:851:            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
src/main/java/org/ecocean/StartupWildbook.java:871:        // flight. shutdownWbiaRegisterExecutor signals shutdown by
src/main/java/org/ecocean/StartupWildbook.java:875:        shutdownWbiaRegisterExecutor();
src/main/java/org/ecocean/StartupWildbook.java:886:    private static void shutdownWbiaRegisterExecutor() {
src/main/java/org/ecocean/StartupWildbook.java:893:            // 15s gives a healthy Phase B WBIA call time to finish so we
src/main/java/org/ecocean/StartupWildbook.java:894:            // do not skip its Phase C unnecessarily. RestClient HTTP isn't
src/test/java/org/ecocean/AnnotationTest.java:72:        assertNull(ann.getWbiaRegistered());
src/test/java/org/ecocean/AnnotationTest.java:73:        assertEquals(0, ann.getWbiaRegisterAttempts());
src/test/java/org/ecocean/AnnotationTest.java:74:        assertFalse(ann.isWbiaRegistered());
src/test/java/org/ecocean/AnnotationTest.java:87:    @Test void isWbiaRegisteredOnlyTrueWhenExplicitlyTrue() {
src/test/java/org/ecocean/AnnotationTest.java:89:        assertFalse(ann.isWbiaRegistered());                  // null
src/test/java/org/ecocean/AnnotationTest.java:90:        ann.setWbiaRegistered(Boolean.FALSE);
src/test/java/org/ecocean/AnnotationTest.java:91:        assertFalse(ann.isWbiaRegistered());                  // pending
src/test/java/org/ecocean/AnnotationTest.java:92:        ann.setWbiaRegistered(Boolean.TRUE);
src/test/java/org/ecocean/AnnotationTest.java:93:        assertTrue(ann.isWbiaRegistered());                   // acknowledged
src/test/java/org/ecocean/AnnotationTest.java:94:        ann.setWbiaRegistered(null);
src/test/java/org/ecocean/AnnotationTest.java:95:        assertFalse(ann.isWbiaRegistered());                  // back to null
src/test/java/org/ecocean/AnnotationTest.java:98:    @Test void incrementWbiaRegisterAttempts() {
src/test/java/org/ecocean/AnnotationTest.java:100:        assertEquals(0, ann.getWbiaRegisterAttempts());
src/test/java/org/ecocean/AnnotationTest.java:101:        ann.incrementWbiaRegisterAttempts();
src/test/java/org/ecocean/AnnotationTest.java:102:        assertEquals(1, ann.getWbiaRegisterAttempts());
src/test/java/org/ecocean/AnnotationTest.java:103:        ann.incrementWbiaRegisterAttempts();
src/test/java/org/ecocean/AnnotationTest.java:104:        ann.incrementWbiaRegisterAttempts();
src/test/java/org/ecocean/AnnotationTest.java:105:        assertEquals(3, ann.getWbiaRegisterAttempts());
src/test/java/org/ecocean/AnnotationTest.java:119:        ann.setWbiaRegistered(Boolean.TRUE);
src/test/java/org/ecocean/AnnotationTest.java:121:        assertTrue("setWbiaRegistered should bump version", v2 > v1);
src/test/java/org/ecocean/AnnotationTest.java:123:        ann.incrementWbiaRegisterAttempts();
src/test/java/org/ecocean/AnnotationTest.java:125:        assertTrue("incrementWbiaRegisterAttempts should bump version", v3 > v2);
src/main/java/org/ecocean/ia/MatchResult.java:521:        Boolean wbiaReg = ann.getWbiaRegistered();
src/main/java/org/ecocean/ia/MatchResult.java:522:        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(

exec
/bin/bash -lc "sed -n '420,540p' src/main/java/org/ecocean/StartupWildbook.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '680,855p' src/main/java/org/ecocean/StartupWildbook.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '40,95p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '330,350p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
 succeeded in 217ms:
                myShepherd.closeDBTransaction();
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    /**
     * ml-service migration v2 §commit #11. Background polling thread that
     * registers ml-service-created annotations with WBIA so HotSpotter is
     * available on demand for them. State is on the Annotation row itself
     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
     * queue or reconcile servlet is needed.
     *
     * <p>Per cycle (~30s): query annotations with
     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
     * up to a small batch limit. For each, call
     * {@link org.ecocean.ia.plugin.WildbookIAM#sendAnnotationsForceId} in a
     * per-annotation Shepherd transaction (so one slow WBIA call blocks
     * only one slot, not the entire batch). On success: set
     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
     *
     * <p>Legacy annotations are excluded from the query because the DDL
     * migration in {@code archive/sql/ml_service_idempotency.sql} backfills
     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
     */
    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
    private static final int WBIA_REGISTER_BATCH_LIMIT = 50;
    private static final long WBIA_REGISTER_POLL_SECONDS = 30L;

    private static void startWbiaRegistrationPollingThread(final String context) {
        // Refuse to start a second poller if one is already running; this
        // also matters when contextInitialized fires more than once for
        // the same JVM (e.g., context reload).
        if (wbiaRegisterExecutor != null) {
            System.out.println(
                "WARN: startWbiaRegistrationPollingThread() called with existing executor; skipping");
            return;
        }
        System.out.println("STARTING: StartupWildbook.startWbiaRegistrationPollingThread()");
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(
            new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "WbiaRegistrationPoll");
                    t.setDaemon(true);
                    return t;
                }
            });
        ses.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                try {
                    runWbiaRegistrationPoll(context);
                } catch (Throwable t) {
                    // Catch Throwable here: ScheduledExecutorService silently
                    // stops re-firing the task on any uncaught exception.
                    // We want the thread to keep ticking through transient
                    // failures.
                    System.out.println("WARN: WbiaRegistrationPoll uncaught: " + t);
                    t.printStackTrace();
                }
            }
        }, WBIA_REGISTER_POLL_SECONDS, WBIA_REGISTER_POLL_SECONDS, TimeUnit.SECONDS);
        wbiaRegisterExecutor = ses;
    }

    private static void runWbiaRegistrationPoll(String context) {
        // Phase 1: query the pending list (Shepherd open, no network). Capture
        // annotation IDs and release before any WBIA calls.
        java.util.List<String> pendingIds = new ArrayList<String>();
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.fetch");
        shep.beginDBTransaction();
        try {
            javax.jdo.Query q = shep.getPM().newQuery(
                org.ecocean.Annotation.class,
                "wbiaRegistered == false && wbiaRegisterAttempts < "
                + WBIA_REGISTER_MAX_ATTEMPTS);
            q.setOrdering("wbiaRegisterAttempts ascending");
            q.setRange(0, WBIA_REGISTER_BATCH_LIMIT);
            @SuppressWarnings("unchecked")
            java.util.List<org.ecocean.Annotation> pending =
                (java.util.List<org.ecocean.Annotation>) q.execute();
            if (pending != null) {
                for (org.ecocean.Annotation a : pending) pendingIds.add(a.getId());
            }
            q.closeAll();
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll fetch failed: " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }

        if (pendingIds.isEmpty()) return;
        System.out.println("[INFO] WbiaRegistrationPoll: " + pendingIds.size() + " pending");

        // Phase 2: per-annotation registration. Each runs in its own short
        // Shepherd tx so a slow WBIA call blocks only that one slot.
        // The interrupted-check makes shutdownNow() effective at bounding
        // the per-tick work even if the in-flight Phase B HTTP call ran
        // past awaitTermination.
        for (String annId : pendingIds) {
            if (Thread.currentThread().isInterrupted() ||
                wbiaRegisterExecutor == null) {
                System.out.println("[INFO] WbiaRegistrationPoll: stopping mid-batch (interrupted)");
                return;
            }
            registerOneAnnotationWithWbia(context, annId);
        }
    }

    /**
     * ml-service migration v2 §commit #12. Once-at-startup pass that
     * detects MediaAssets stuck in {@code processing-mlservice} past a
     * threshold (worker presumably died mid-detection due to the
     * at-most-once FileQueue semantics) and re-enqueues them through
     * the normal routing layer.
     *
     * <p>Safe under any active worker because:</p>
     * <ul>
     *   <li>The re-check inside reconcileOneStaleAsset uses the fresh

 succeeded in 216ms:
            shep.commitDBTransaction();
            System.out.println("[INFO] StaleMlServiceReconciliation: re-enqueued " + maId);
        } catch (Exception ex) {
            System.out.println("WARN: StaleMlServiceReconciliation registerOne failed for " +
                maId + ": " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Phase A/B/C split per Codex c11 fix-review.
     * <ul>
     *   <li>Phase A: Shepherd open, re-check state, build DTO, close.
     *   <li>Phase B: no Shepherd held; WBIA HTTP via
     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
     *   <li>Phase C: Shepherd open, re-load, persist outcome, close.
     * </ul>
     * Ineligible annotations (missing media asset, missing acmId, fails
     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
     * fall out of the polling query.
     */
    private static void registerOneAnnotationWithWbia(String context, String annId) {
        // ---- Phase A: load DTO under a short transaction. ----
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
            loadWbiaRegisterDto(context, annId);
        if (dto == null) return;  // ineligible / already registered / parked

        // Bail out before starting the non-interruptible HTTP call if
        // shutdown was requested while Phase A was running. Otherwise we
        // would start a 300s WBIA POST that contextDestroyed can't cancel.
        if (Thread.currentThread().isInterrupted() ||
            wbiaRegisterExecutor == null) {
            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
                " (shutdown requested)");
            return;
        }

        // ---- Phase B: no Shepherd held; call WBIA. ----
        org.ecocean.ia.plugin.WildbookIAM iam =
            new org.ecocean.ia.plugin.WildbookIAM(context);
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
            iam.registerOneByDto(dto);

        // Skip Phase C if shutdown has been requested while Phase B ran.
        // RestClient is not interruptible mid-IO, so Phase B can outlive
        // awaitTermination; this prevents Phase C from racing the rest of
        // contextDestroyed's cleanup (Shepherd / IndexingManager / etc.).
        if (Thread.currentThread().isInterrupted() ||
            wbiaRegisterExecutor == null) {
            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
                " (shutdown requested)");
            return;
        }

        // ---- Phase C: persist outcome under a short transaction. ----
        persistWbiaRegisterResult(context, annId, outcome);
    }

    /**
     * Phase A. Returns a detached DTO ready for Phase B, or null if the
     * annotation does not need (or cannot get) a Phase-B network call.
     * Null cases: missing annotation, already registered, parked at max
     * attempts, or ineligible (missing media asset / acmId / bbox / etc.).
     * Ineligible annotations are parked here so they stop being polled.
     */
    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
        loadWbiaRegisterDto(String context, String annId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
        shep.beginDBTransaction();
        try {
            org.ecocean.Annotation ann = shep.getAnnotation(annId);
            if (ann == null) {
                shep.commitDBTransaction();
                return null;
            }
            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
                shep.commitDBTransaction();
                return null;
            }
            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                shep.commitDBTransaction();
                return null;
            }
            // Eligibility checks. Any failure here is permanent for this
            // annotation under its current state, so park it.
            org.ecocean.media.MediaAsset ma = ann.getMediaAsset();
            String reason = null;
            if (ma == null) reason = "missing media asset";
            else if (!Util.stringExists(ma.getAcmId())) reason = "media asset has no acmId";
            else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
            else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
                reason = "validForIdentification returned false (bbox/iaClass/etc.)";
            if (reason != null) {
                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
                    " (ineligible: " + reason + ")");
                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
                shep.commitDBTransaction();
                return null;
            }
            // Resolve the individual name now while the Shepherd is open;
            // Phase B has no DB access.
            String name = ann.findIndividualId(shep);
            // Copy bbox into a fresh array so the DTO is fully detached.
            int[] bb = ann.getBbox();
            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
                    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
                    ann.getTheta(), ann.getIAClass(), name);
            shep.commitDBTransaction();
            return dto;
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
                annId + ": " + ex);
            shep.rollbackDBTransaction();
            return null;
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Phase C. Re-loads the annotation and writes the outcome of the
     * Phase-B network call. On terminal-success outcomes the annotation
     * is marked registered; on retryable outcomes the attempts counter
     * is bumped and we WARN-log when we hit the abandonment threshold.
     */
    private static void persistWbiaRegisterResult(String context, String annId,
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
        shep.beginDBTransaction();
        try {
            org.ecocean.Annotation ann = shep.getAnnotation(annId);
            if (ann == null) {
                shep.commitDBTransaction();
                return;
            }
            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
                // Some other path flipped it while Phase B ran; respect that.
                shep.commitDBTransaction();
                return;
            }
            switch (outcome) {
                case REGISTERED_OK:
                case REGISTERED_ALREADY_PRESENT:
                    // Always honor a success outcome even if the row was
                    // parked by a racing poller: stuck-at-attempts==MAX
                    // would otherwise become permanent.
                    ann.setWbiaRegistered(Boolean.TRUE);
                    break;
                case NETWORK_FAIL:
                case RESPONSE_BAD:
                default:
                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                        // Already parked by another path; do not increment past MAX.
                        break;
                    }
                    ann.incrementWbiaRegisterAttempts();
                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
                            " attempts (last outcome=" + outcome + "); will not retry");
                    }
                    break;
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
                annId + ": " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();

 succeeded in 216ms:
    public String getBboxKey() { return bboxKey; }
    public void setBboxKey(String s) { this.bboxKey = s; this.setVersion(); }

    public String getThetaKey() { return thetaKey; }
    public void setThetaKey(String s) { this.thetaKey = s; this.setVersion(); }

    public Boolean getWbiaRegistered() { return wbiaRegistered; }
    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }

    // Convenience: hides the tri-state from frontend JSON. Returns true only
    // when the column is explicitly TRUE.
    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }

    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
    public void incrementWbiaRegisterAttempts() {
        this.wbiaRegisterAttempts++;
        this.setVersion();
    }

    public ArrayList<Feature> getFeatures() {

 succeeded in 221ms:
                "downleft", "downfrontleft"
        }, { "down", "down", "down", "down", "down", "down", "down", "down" }
    };
    private String species;

    private String iaClass;

    private String name;
    private boolean isExemplar = false;
    private Boolean isOfInterest = null; // aka AoI (Annotation of Interest)
    protected String identificationStatus;
    private ArrayList<Feature> features;
    private Set<Embedding> embeddings;
    protected String acmId;

    // ----- ml-service migration v2: idempotency + WBIA registration -----
    // (commit #4 of the v2 plan)
    //
    // predictModelId / bboxKey / thetaKey form the composite that uniquely
    // identifies an ml-service-created detection. The partial unique index
    // in archive/sql/ml_service_idempotency.sql guards against concurrent
    // retry creating duplicates. Null on legacy WBIA-era rows and on
    // manually-drawn annotations.
    protected String predictModelId;
    protected String bboxKey;   // literal "x:y:w:h" of rounded ints
    protected String thetaKey;  // theta rounded to 4 decimals, as String

    // wbiaRegistered drives the DB-backed background poller that tells WBIA
    // about ml-service-created annotations so HotSpotter remains available.
    //
    //   null  — legacy annotation (column is new; starts null on existing
    //           rows). The DDL migration sets nulls to TRUE wherever
    //           acmId IS NOT NULL ("already registered via the historical
    //           IBEISIA flow"). Excluded from polling.
    //   false — new ml-service annotation awaiting WBIA registration.
    //           Polling thread picks these up.
    //   true  — WBIA acknowledged. Terminal success.
    //
    // Contract: MlServiceProcessor MUST set this to false (not null) on
    // new ml-service annotations.
    protected Boolean wbiaRegistered;

    // Failed-attempt counter. Polling filters wbiaRegisterAttempts < MAX so
    // chronically-failing rows park rather than spin forever.
    protected int wbiaRegisterAttempts = 0;

    // this is used to decide "should we match against this"  problem is: that is not very (IA-)algorithm agnostic
    // TODO: was this made obsolete by ACM and friends?
    private boolean matchAgainst = false;

    // TODO: can these (thru mediaAsset) be removed now that there Features?
    private int x;
    private int y;
    private int width;
    private int height;
    private float[] transformMatrix;

exec
/bin/bash -lc "rg -n \"class name=\\\"Annotation\\\"|field name=\\\"features\\\"|field name=\\\"embeddings\\\"|class name=\\\"Embedding\\\"|annotation|method|methodVersion|class name=\\\"Task\\\"|objectMediaAssets|children|parent|MediaAsset|features\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/ia/package.jdo src/main/resources/org/ecocean/media/package.jdo" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,170p' src/main/resources/org/ecocean/package.jdo" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,130p' src/main/resources/org/ecocean/ia/package.jdo" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,160p' src/main/resources/org/ecocean/media/package.jdo" in /mnt/c/Wildbook-clean2
 succeeded in 263ms:
<!DOCTYPE jdo PUBLIC "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN" "http://java.sun.com/dtd/jdo_2_0.dtd">

<jdo>
  <package name="org.ecocean">


    <class name="Survey" identity-type="application" table="SURVEY">
      <field name="surveyID" primary-key="true">
        <column length="100" allows-null="true"/>
      </field>

      <field name="surveyTracks" persistence-modifier="persistent">
        <collection element-type="org.ecocean.movement.SurveyTrack" allow-null="true"/>
        <join/>
      </field>

      <field name="project" persistence-modifier="persistent">
        <column length="256" allows-null="true" />
      </field>

      <field name="organization" persistence-modifier="persistent">
        <column length="256" allows-null="true"/>
      </field>

      <field name="comments" persistence-modifier="persistent">
        <column length="2048" allows-null="true"/>
      </field>

      <field name="type" persistence-modifier="persistent">
        <column length="100" allows-null="true"/>
      </field>

      <field name="startTime" persistence-modifier="persistent">
        <column jdbc-type="BIGINT" allows-null="true"/>
      </field>

      <field name="endTime" persistence-modifier="persistent">
        <column jdbc-type="BIGINT" allows-null="true"/>
      </field>

      <field name="effort" persistence-modifier="persistent" element-type="org.ecocean.Measurement" allows-null="true"/>

      <field name="dateTimeCreated" persistence-modifier="persistent">
        <column jdbc-type="VARCHAR" allows-null="true"/>
      </field>

      <field name="dateTimeModified" persistence-modifier="persistent">
        <column jdbc-type="VARCHAR" allows-null="true"/>
      </field>

      <field name="date" persistence-modifier="persistent">
        <column jdbc-type="VARCHAR" allows-null="true"/>
      </field>

      <field name="observations" persistence-modifier="persistent" default-fetch-group="true">
        <collection element-type="org.ecocean.Observation" dependent-element="true" />
        <join/>
      </field>

      <fetch-group name="count"/>
    </class>

    <class name="PointLocation" identity-type="application" table="POINTLOCATION">
      <field name="pointLocationID" primary-key="true">
        <column length="100"/>
      </field>

 	  <field name="elevation" persistence-modifier="persistent" element-type="org.ecocean.Measurement" allows-null="true"/>

      <field name="correspondingPathID" persistence-modifier="persistent">
        <column length="100" allows-null="true" />
      </field>

      <field name="correspondingEncounterID" persistence-modifier="persistent">
        <column length="100" allows-null="true"/>
      </field>

      <field name="correspondingOccurrenceID" persistence-modifier="persistent">
        <column length="100" allows-null="true"/>
      </field>

      <field name="dateTime" persistence-modifier="persistent">
        <column jdbc-type="BIGINT" allows-null="true"/>
      </field>

      <field name="latitude" persistence-modifier="persistent">
        <column jdbc-type="DOUBLE" allows-null="true"/>
      </field>

      <field name="longitude" persistence-modifier="persistent">
        <column jdbc-type="DOUBLE" allows-null="true"/>
      </field>

      <field name="bearing" persistence-modifier="persistent">
        <column jdbc-type="DOUBLE" allows-null="true"/>
      </field>

      <fetch-group name="count"/>
    </class>

    <class name="MarkedIndividual" identity-type="application" table="MARKEDINDIVIDUAL">
      <field name="individualID" primary-key="true" />

		<field jdbc-type="BIGINT" name="version" allows-null="false" >
			<column default-value="-1" />
			<index name="MARKEDINDIVIDUAL_VERSION_IDX" />
		</field>

      	<field name="names" persistence-modifier="persistent" element-type="org.ecocean.MultiValue" default-fetch-group="true" />

      <field name="comments" persistence-modifier="persistent" default-fetch-group="false">
        <column jdbc-type="LONGVARCHAR"/>
      </field>
      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true" mapped-by="individual" >
        <collection element-type="org.ecocean.Encounter" />
        <join/>
      </field>

      <field name="dataFiles" persistence-modifier="persistent">
        <collection element-type="java.lang.String"/>
      </field>
      <field name="interestedResearchers" persistence-modifier="persistent">
        <collection element-type="java.lang.String"/>
      </field>

      <field name="dateTimeCreated" persistence-modifier="persistent">
        <column jdbc-type="LONGVARCHAR"/>
      </field>
      <field name="alternateid" persistence-modifier="persistent">
        <column length="512"/>
      </field>

      <field name="maxYearsBetweenResightings" persistence-modifier="persistent">
        <column jdbc-type="INTEGER" allows-null="true"/>
      </field>

      <field name="timeOfDeath" persistence-modifier="persistent">
        <column jdbc-type="BIGINT" allows-null="true"/>
      </field>
      <field name="timeOfBirth" persistence-modifier="persistent">
        <column jdbc-type="BIGINT" allows-null="true"/>
      </field>

      <field name="numberLocations" persistence-modifier="persistent">
        <column jdbc-type="INTEGER" allows-null="true"/>
      </field>
      <field name="thumbnailUrl" persistence-modifier="persistent">
        <column allows-null="true"/>
      </field>

      <fetch-group name="allSharks_min">
        <field name="encounters"/>
      </fetch-group>


	      <fetch-group name="indymin">
        <field name="individualID"/>
      </fetch-group>

      <fetch-group name="count"/>

    </class>

    <class name="Occurrence" identity-type="application" table="OCCURRENCE">
      <inheritance strategy="new-table"/>
      <field name="occurrenceID" primary-key="true">
        <column length="100"/>
      </field>

      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true">

 succeeded in 231ms:
<!DOCTYPE jdo PUBLIC
    "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN"
    "http://java.sun.com/dtd/jdo_2_0.dtd">

<jdo>
    <package name="org.ecocean.media">

	<class name="MediaAsset" identity-type="application">
		<field name="id" primary-key="true" value-strategy="identity"/>
		<field name="acmId">
			<column length="100"/>
			<index name="MEDIAASSET_ACMID_INDEX" />
		</field>
		<field name="uuid">
			<index name="MEDIAASSET_UUID_INDEX" unique="true" />
		</field>
		<field name="store" persistence-modifier="persistent" element-type="org.ecocean.media.AssetStore" />
		<field name="parentId" persistence-modifier="persistent">
			<column jdbc-type="INTEGER" allows-null="true"/>
		</field>
		<field name="validImageForIA" persistence-modifier="persistent">
			<column jdbc-type="BIT" allows-null="true"/>
		</field>
            	<field name="revision" persistence-modifier="persistent">
                	<column jdbc-type="BIGINT" allows-null="false"/>
            	</field>
		<field name="metadata" persistence-modifier="persistent" cacheable="false" element-type="org.ecocean.media.MediaAssetMetadata" default-fetch-group="true" />
		<field name="parametersAsString" persistence-modifier="persistent">
                	<column jdbc-type="LONGVARCHAR" name="parameters" />
		</field>
		<property name="derivationMethodAsString" persistence-modifier="persistent">
                	<column jdbc-type="LONGVARCHAR" name="derivationMethod" />
		</property>
		<field name="contentHash">
			<column length="30"/>
			<index name="MEDIAASSET_CONTENTHASH_INDEX" />
		</field>

    <field name="occurrence"/>

<!--
		<field name="annotations" mapped-by="mediaAsset">
			<collection element-type="org.ecocean.Annotation"/>
			<join />
		</field>
-->

		<field name="hashCode">
			<column length="75" />
		</field>

		<field name="labels" serialized="true" column="LABELS" />

            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" mapped-by="asset" >
                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
                	<join />
            	</field>

            	<field name="keywords" persistence-modifier="persistent" default-fetch-group="true">
                	<collection element-type="org.ecocean.Keyword" dependent-element="false" />
                	<join />
            	</field>
	</class>


	<class name="MediaAssetMetadata">
		<property name="dataAsString" persistence-modifier="persistent" default-fetch-group="true">
                	<column jdbc-type="LONGVARCHAR" name="data" />
		</property>
	</class>

	<class name="AssetStore" identity-type="application">
		<field name="id" primary-key="true" value-strategy="identity"/>
            	<field name="config" persistence-modifier="persistent" serialized="true" />
    		<inheritance>
        		<discriminator strategy="value-map">
				<column name="TYPE"/>
			</discriminator>
    		</inheritance>
		<field name="type" persistence-modifier="none" />
	</class>

	<class name="LocalAssetStore">
		<inheritance strategy="superclass-table">
			<discriminator value="LOCAL"/>
		</inheritance>
	</class>

 	<class name="URLAssetStore">
 		<inheritance strategy="superclass-table">
 			<discriminator value="URL"/>
 		</inheritance>
 	</class>

 	<class name="YouTubeAssetStore">
 		<inheritance strategy="superclass-table">
 			<discriminator value="YouTube"/>
 		</inheritance>
 	</class>

	<class name="MediaAssetSet">
		<field name="id" primary-key="true">
			<column length="36" />
		</field>
		<field name="assets" persistence-modifier="persistent" default-fetch-group="false">
			<collection element-type="org.ecocean.media.MediaAsset" dependent-element="true" />
			<join />
		</field>
	</class>

	<class name="Feature" identity-type="application">
		<field name="id" primary-key="true">
			<column length="36" />
		</field>
		<field name="type" persistence-modifier="persistent" element-type="org.ecocean.media.FeatureType" allows-null="true" />
            	<field name="revision" persistence-modifier="persistent">
                	<column jdbc-type="BIGINT" allows-null="false"/>
            	</field>
		<field name="parametersAsString" persistence-modifier="persistent">
                	<column jdbc-type="LONGVARCHAR" name="parameters" />
		</field>

		<field name="annotation" />
		<field name="asset" />
	</class>

	<class name="FeatureType" identity-type="application">
		<field name="id" primary-key="true">
			<column length="255" />
		</field>
		<field name="description" persistence-modifier="persistent" />
	</class>



    </package>
</jdo>

 succeeded in 235ms:
<!DOCTYPE jdo PUBLIC
    "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN"
    "http://java.sun.com/dtd/jdo_2_0.dtd">

<jdo>
    <package name="org.ecocean.ia">

	<class name="Task" identity-type="application">
		<field name="id" primary-key="true">
        		<column length="36"/>
		</field>
		<field name="created">
			<index name="TASK_CREATED_idx" />
		</field>

		<!-- this points up to the parent -->
      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" recursion-depth="3"/>

		<!-- this uses parent to find child tasks -->
            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
                	<collection element-type="org.ecocean.ia.Task" dependent-element="true" />
                	<join />
            	</field>

<!--  to update your db table:
alter table "TASK" alter column "PARAMETERS" type text;
-->
      		<field name="parameters" persistence-modifier="persistent">
        		<column jdbc-type="LONGVARCHAR"/>
      		</field>
			
			<field name="status" persistence-modifier="persistent">
        		<column jdbc-type="LONGVARCHAR"/>
      		</field>

      		<field name="statusDetails" persistence-modifier="persistent">
        		<column jdbc-type="LONGVARCHAR"/>
      		</field>
			
			<field name="queueResumeMessage" persistence-modifier="persistent">
        		<column jdbc-type="LONGVARCHAR"/>
      		</field>
			
			<field name="completionDateInMilliseconds" persistence-modifier="persistent">
				<column jdbc-type="BIGINT" allows-null="true"/>
			</field>

<!--
		<field name="id" field-type="java.util.UUID" primary-key="true" value-strategy="uuid">
                        <column sql-type="uuid" />
                </field>
-->
<!--
		<field name="parentId" persistence-modifier="persistent">
			<column jdbc-type="INT" allows-null="true"/>
		</field>
            	<field name="revision" persistence-modifier="persistent">
                	<column jdbc-type="BIGINT" allows-null="false"/>
            	</field>
-->

		<field name="objectMediaAssets">
			<collection element-type="org.ecocean.media.MediaAsset"/>
			<join />
		</field>
<!--
		<field name="objectAnnotations" mapped-by="mediaAsset"???   >
-->
		<field name="objectAnnotations">
			<collection element-type="org.ecocean.Annotation"/>
			<join />
		</field>
	</class>

	<class name="MatchResult" identity-type="application">
		<field name="id" primary-key="true">
        		<column length="36"/>
		</field>
		<field name="created">
                	<column jdbc-type="BIGINT" allows-null="false"/>
			<index name="MATCHRESULT_CREATED_idx" />
		</field>

      		<field name="task" persistence-modifier="persistent" element-type="org.ecocean.ia.Task">
			<column allows-null="false" />
		</field>

		<field name="prospects" default-fetch-group="false" mapped-by="matchResult">
			<collection element-type="org.ecocean.ia.MatchResultProspect" dependent-element="true" />
		</field>

      		<field name="queryAnnotation" persistence-modifier="persistent" element-type="org.ecocean.Annotation">
			<column allows-null="false" />
		</field>

		<field name="candidates">
			<collection element-type="org.ecocean.Annotation"/>
			<join />
		</field>
	</class>

	<class name="MatchResultProspect">
      		<field name="annotation" persistence-modifier="persistent" element-type="org.ecocean.Annotation">
			<column allows-null="false" />
		</field>
      		<field name="asset" persistence-modifier="persistent" element-type="org.ecocean.media.MediaAsset" />
		<field name="matchResult" dependent-element="false" >
			<column name="MATCHRESULT_ID" />
		</field>
	</class>

    </package>
</jdo>

 succeeded in 308ms:
src/main/resources/org/ecocean/package.jdo:225:          <collection element-type="org.ecocean.media.MediaAsset" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:422:      <field name="annotations" persistence-modifier="persistent" default-fetch-group="true">
src/main/resources/org/ecocean/package.jdo:538:    <class name="Annotation" identity-type="application">
src/main/resources/org/ecocean/package.jdo:593:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
src/main/resources/org/ecocean/package.jdo:598:      <field name="mediaAsset" persistence-modifier="persistent" element-type="org.ecocean.media.MediaAsset" recursion-depth="3"/>
src/main/resources/org/ecocean/package.jdo:617:	<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
src/main/resources/org/ecocean/package.jdo:720:      <field name="parentObjectClass">
src/main/resources/org/ecocean/package.jdo:724:      <field name="parentObjectID">
src/main/resources/org/ecocean/package.jdo:990:		<field name="children" mapped-by="parent">
src/main/resources/org/ecocean/package.jdo:993:		<field name="parent">
src/main/resources/org/ecocean/package.jdo:1026:		<!-- this points up to the parent -->
src/main/resources/org/ecocean/package.jdo:1027:      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.Organization" recursion-depth="3"/>
src/main/resources/org/ecocean/package.jdo:1029:		<!-- this uses parent to find child orgs -->
src/main/resources/org/ecocean/package.jdo:1030:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/package.jdo:1041:	<class name="Embedding" identity-type="application">
src/main/resources/org/ecocean/package.jdo:1043:		     to support idempotency lookup of (annotation, method, methodVersion).
src/main/resources/org/ecocean/package.jdo:1056:		<field name="annotation" dependent-element="false" >
src/main/resources/org/ecocean/package.jdo:1067:		<field name="method">
src/main/resources/org/ecocean/package.jdo:1072:		<field name="methodVersion">
src/main/resources/org/ecocean/media/package.jdo:8:	<class name="MediaAsset" identity-type="application">
src/main/resources/org/ecocean/media/package.jdo:18:		<field name="parentId" persistence-modifier="persistent">
src/main/resources/org/ecocean/media/package.jdo:27:		<field name="metadata" persistence-modifier="persistent" cacheable="false" element-type="org.ecocean.media.MediaAssetMetadata" default-fetch-group="true" />
src/main/resources/org/ecocean/media/package.jdo:42:		<field name="annotations" mapped-by="mediaAsset">
src/main/resources/org/ecocean/media/package.jdo:54:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" mapped-by="asset" >
src/main/resources/org/ecocean/media/package.jdo:66:	<class name="MediaAssetMetadata">
src/main/resources/org/ecocean/media/package.jdo:101:	<class name="MediaAssetSet">
src/main/resources/org/ecocean/media/package.jdo:106:			<collection element-type="org.ecocean.media.MediaAsset" dependent-element="true" />
src/main/resources/org/ecocean/media/package.jdo:123:		<field name="annotation" />
src/main/resources/org/ecocean/ia/package.jdo:8:	<class name="Task" identity-type="application">
src/main/resources/org/ecocean/ia/package.jdo:16:		<!-- this points up to the parent -->
src/main/resources/org/ecocean/ia/package.jdo:17:      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" recursion-depth="3"/>
src/main/resources/org/ecocean/ia/package.jdo:19:		<!-- this uses parent to find child tasks -->
src/main/resources/org/ecocean/ia/package.jdo:20:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/ia/package.jdo:54:		<field name="parentId" persistence-modifier="persistent">
src/main/resources/org/ecocean/ia/package.jdo:62:		<field name="objectMediaAssets">
src/main/resources/org/ecocean/ia/package.jdo:63:			<collection element-type="org.ecocean.media.MediaAsset"/>
src/main/resources/org/ecocean/ia/package.jdo:103:      		<field name="annotation" persistence-modifier="persistent" element-type="org.ecocean.Annotation">
src/main/resources/org/ecocean/ia/package.jdo:106:      		<field name="asset" persistence-modifier="persistent" element-type="org.ecocean.media.MediaAsset" />

exec
/bin/bash -lc "sed -n '538,630p' src/main/resources/org/ecocean/package.jdo" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1040,1080p' src/main/resources/org/ecocean/package.jdo" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,90p' archive/sql/ml_service_idempotency.sql" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' archive/sql/ml_service_embedding_audit.sql" in /mnt/c/Wildbook-clean2
 succeeded in 222ms:
    <class name="Annotation" identity-type="application">
      <field name="id" primary-key="true">
        <column length="36"/>
      </field>

      <field name="iaClass">
        <column name="IACLASS"/>
      </field>

	<field name="acmId">
		<column length="100"/>
	</field>

	<!-- ml-service migration v2 (commit #4): idempotency + WBIA registration.
	     The partial unique index on (mediaAsset, predictModelId, bboxKey,
	     thetaKey) WHERE predictModelId IS NOT NULL is applied by
	     archive/sql/ml_service_idempotency.sql. -->
	<field name="predictModelId">
		<column length="100" allows-null="true"/>
		<index name="ANNOTATION_PREDICTMODELID_IDX"/>
	</field>
	<field name="bboxKey">
		<column length="64" allows-null="true"/>
	</field>
	<field name="thetaKey">
		<column length="20" allows-null="true"/>
	</field>
	<field name="wbiaRegistered">
		<column allows-null="true"/>
	</field>
	<field name="wbiaRegisterAttempts">
		<column allows-null="false" default-value="0"/>
	</field>

		<field name="isExemplar">
<!--  note, if you are using mysql, which maybe you shouldnt be?, you want this here: -->
<!--			<column default-value="0" /> -->
<!-- this is for postgresql-->
			<column default-value="false" />

		</field>

		<field name="matchAgainst">
			<column default-value="false" />
			<index name="ANNOTATION_MATCHAGAINST_idx" />
		</field>
		<field name="viewpoint">
			<index name="ANNOTATION_VIEWPOINT_IDX" />
		</field>

		<field jdbc-type="BIGINT" name="version" allows-null="false" >
			<column default-value="-1" />
			<index name="ANNOTATION_VERSION_IDX" />
		</field>

            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
                	<join />
            	</field>

      <field name="mediaAsset" persistence-modifier="persistent" element-type="org.ecocean.media.MediaAsset" recursion-depth="3"/>

      <field name="quality" persistence-modifier="persistent">
        <column jdbc-type="DOUBLE" allows-null="true"/>
      </field>

      <field name="distinctiveness" persistence-modifier="persistent">
        <column jdbc-type="DOUBLE" allows-null="true"/>
      </field>

      <!--
		<field name="annot_xtl" />
		<field name="annot_ytl" />
		<field name="annot_width" />
		<field name="annot_height" />
		<field name="annot_theta" />
		<field name="species_text" />
-->

	<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
		<collection element-type="org.ecocean.Embedding" dependent-element="false" />
	</field>

    </class>

    <class name="DataCollectionEvent" identity-type="application">
      <inheritance strategy="new-table"/>
      <field name="correspondingEncounterNumber">
        <column name="CORRESPONDINGENCOUNTERNUMBER"/>
      </field>

      <field name="dataCollectionEventID" primary-key="true" value-strategy="native">
        <column name="DATACOLLECTIONEVENTID"/>

 succeeded in 226ms:

	<class name="Embedding" identity-type="application">
		<!-- ml-service migration v2 (commit #4): non-unique composite index
		     to support idempotency lookup of (annotation, method, methodVersion).
		     Promotion to UNIQUE is deferred to a follow-up branch after a
		     per-deployment audit confirms no existing duplicates (see
		     archive/sql/ml_service_embedding_audit.sql). -->
		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
			<column name="ANNOTATION_ID"/>
			<column name="METHOD"/>
			<column name="METHODVERSION"/>
		</index>
		<field name="id" primary-key="true">
			<column length="36"/>
		</field>

		<field name="annotation" dependent-element="false" >
			<column name="ANNOTATION_ID" />
		</field>

		<field name="vectorFloatArray" persistence-modifier="persistent" />

		<field name="created">
			<column jdbc-type="BIGINT" />
			<index name="EMBEDDING_CREATED_IDX" />
		</field>

		<field name="method">
			<column length="64" />
			<index name="EMBEDDING_METHOD_IDX" />
		</field>

		<field name="methodVersion">
			<column length="32" />
			<index name="EMBEDDING_METHODVERSION_IDX" />
		</field>
	</class>

  </package>
</jdo>

 succeeded in 217ms:
-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
--
-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
-- (ANNOTATION_ID, METHOD, METHODVERSION) is created by JDO in
-- src/main/resources/org/ecocean/package.jdo. The v2 plan defers promoting
-- it to UNIQUE until a per-deployment audit confirms no existing duplicates.
--
-- Run this query before promoting. Must return zero rows. If it returns
-- rows, dedupe those first.

SELECT "ANNOTATION_ID", "METHOD", "METHODVERSION", COUNT(*) AS dup_count
FROM "EMBEDDING"
GROUP BY "ANNOTATION_ID", "METHOD", "METHODVERSION"
HAVING COUNT(*) > 1
ORDER BY dup_count DESC, "ANNOTATION_ID";

 succeeded in 219ms:
-- ml-service migration v2 (commit #4): annotation idempotency + WBIA-registration backfill
--
-- This script complements the JDO mapping in src/main/resources/org/ecocean/package.jdo.
-- DataNucleus auto-creates the new ANNOTATION columns (PREDICTMODELID, BBOXKEY, THETAKEY,
-- WBIAREGISTERED, WBIAREGISTERATTEMPTS) on next startup. This file adds the parts
-- DataNucleus cannot auto-create: a Postgres partial unique index for ml-service
-- idempotency, a CHECK constraint that the composite columns are populated together,
-- and a one-time backfill marking legacy annotations as already-registered with WBIA.
--
-- Safe to re-run. Each statement is either idempotent (CREATE INDEX IF NOT
-- EXISTS, ALTER COLUMN, ADD CONSTRAINT guarded by pg_constraint lookup) or
-- filters on the pre-backfill state (UPDATEs touching only NULL rows).

BEGIN;

-- (1) Partial unique index: idempotency for ml-service-created annotations.
--     Filters on PREDICTMODELID IS NOT NULL so legacy WBIA-era rows are unaffected.
--     NOTE: Postgres unique indexes treat NULL as distinct, so any of the four
--     composite columns being NULL would defeat the constraint. The CHECK
--     constraint below guarantees the other three are also non-null when
--     PREDICTMODELID is non-null.
CREATE UNIQUE INDEX IF NOT EXISTS "ANNOTATION_MLSERVICE_IDEM_idx"
ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
WHERE "PREDICTMODELID" IS NOT NULL;

-- (2) CHECK constraint: when PREDICTMODELID is non-null, the other composite
--     columns must also be non-null. Defense in depth against partial fills
--     that would silently bypass the partial unique index.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'annotation_mlservice_composite_check'
          AND conrelid = '"ANNOTATION"'::regclass
    ) THEN
        ALTER TABLE "ANNOTATION" ADD CONSTRAINT annotation_mlservice_composite_check
            CHECK (
                "PREDICTMODELID" IS NULL OR (
                    "MEDIAASSET_ID_OID" IS NOT NULL AND
                    "BBOXKEY" IS NOT NULL AND
                    "THETAKEY" IS NOT NULL
                )
            );
    END IF;
END $$;

-- (3) Harden WBIAREGISTERATTEMPTS at the SQL level. DataNucleus creates the
--     column from package.jdo with allows-null=false + default 0, but if an
--     older DataNucleus run on this deployment already created it without
--     those properties (rare but possible), this block repairs it idempotently.
--     SET DEFAULT first so new rows get 0; backfill NULLs to 0 (with VERSION
--     bump for OpenSearch reindex); then SET NOT NULL.
ALTER TABLE "ANNOTATION"
    ALTER COLUMN "WBIAREGISTERATTEMPTS" SET DEFAULT 0;

UPDATE "ANNOTATION"
SET "WBIAREGISTERATTEMPTS" = 0,
    "VERSION" = (EXTRACT(EPOCH FROM now()) * 1000)::bigint
WHERE "WBIAREGISTERATTEMPTS" IS NULL;

ALTER TABLE "ANNOTATION"
    ALTER COLUMN "WBIAREGISTERATTEMPTS" SET NOT NULL;

-- (4) One-time WBIA-registration backfill: legacy annotations that already
--     have an acmId were registered with WBIA via the historical IBEISIA
--     flow. Mark them as registered so the new background-polling thread
--     does NOT re-register them.
--
--     Bumps VERSION in the same statement per the repo rule for direct SQL
--     writes to indexed ANNOTATION rows (OpenSearch reindexer reads VERSION).
--     Filters on WBIAREGISTERED IS NULL so re-running this script only
--     touches rows the previous run missed.
UPDATE "ANNOTATION"
SET "WBIAREGISTERED" = TRUE,
    "VERSION" = (EXTRACT(EPOCH FROM now()) * 1000)::bigint
WHERE "ACMID" IS NOT NULL AND "WBIAREGISTERED" IS NULL;

-- (5) Partial index for the WBIA-registration polling thread (commit #11
--     fix-pass). The poller's JDOQL filter is
--         wbiaRegistered == false AND wbiaRegisterAttempts < 10
--     ordered by wbiaRegisterAttempts ASC. Partial-on-FALSE keeps the
--     index tiny: legacy rows are TRUE post-backfill, registered rows are
--     TRUE, and only the small still-pending set lives in the index.
--     The predicate matches the poller's filter exactly (also excluding
--     parked rows at attempts == MAX_ATTEMPTS, so abandoned rows never
--     hit the index).
CREATE INDEX IF NOT EXISTS "ANNOTATION_WBIAREGISTER_PENDING_IDX"
ON "ANNOTATION" ("WBIAREGISTERATTEMPTS")
WHERE "WBIAREGISTERED" = FALSE AND "WBIAREGISTERATTEMPTS" < 10;


exec
/bin/bash -lc "rg -n \"getMediaAsset\\(|__setMediaAsset|mediaAsset\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "MEDIAASSET_FEATURES|ANNOTATION_FEATURES|MEDIAASSET_ID_OID|FEATURE|FEATURES|EMBEDDING" archive src/main/resources src/main/java docs/plans | head -n 120' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '300,330p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,180p' src/main/java/org/ecocean/media/Feature.java" in /mnt/c/Wildbook-clean2
 succeeded in 259ms:
src/main/java/org/ecocean/Annotation.java:90:    // TODO: can these (thru mediaAsset) be removed now that there Features?
src/main/java/org/ecocean/Annotation.java:117:    private MediaAsset mediaAsset = null;
src/main/java/org/ecocean/Annotation.java:217:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:219:            jgen.writeNumberField("mediaAssetId", ma.getIdInt());
src/main/java/org/ecocean/Annotation.java:307:        __getMediaAsset().addFeature(f);
src/main/java/org/ecocean/Annotation.java:456:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:484:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:595:    public MediaAsset __getMediaAsset() {
src/main/java/org/ecocean/Annotation.java:596:        return mediaAsset;
src/main/java/org/ecocean/Annotation.java:600:     * Direct setter for the {@code mediaAsset} field — populates the
src/main/java/org/ecocean/Annotation.java:602:     * partial unique index on (mediaAsset, predictModelId, bboxKey, thetaKey)
src/main/java/org/ecocean/Annotation.java:606:     * code (use {@link #getMediaAsset()} when you want the proper
src/main/java/org/ecocean/Annotation.java:609:    public void __setMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/Annotation.java:610:        this.mediaAsset = ma;
src/main/java/org/ecocean/Annotation.java:614:    public MediaAsset getMediaAsset() {
src/main/java/org/ecocean/Annotation.java:619:                " is featureless, falling back to deprecated __getMediaAsset().  please fix!");
src/main/java/org/ecocean/Annotation.java:620:            return __getMediaAsset();
src/main/java/org/ecocean/Annotation.java:622:        return fts.get(0).getMediaAsset();
src/main/java/org/ecocean/Annotation.java:626:        return (getMediaAsset() != null);
src/main/java/org/ecocean/Annotation.java:639:        MediaAsset ma = fts.get(0).getMediaAsset();
src/main/java/org/ecocean/Annotation.java:647:        if (this.getMediaAsset() == null) return null;
src/main/java/org/ecocean/Annotation.java:649:        for (Annotation ann : this.getMediaAsset().getAnnotations()) { // fyi .getAnnotations() doesnt return null
src/main/java/org/ecocean/Annotation.java:662:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:722:        this.getMediaAsset().addKeyword(kw);
src/main/java/org/ecocean/Annotation.java:770:        MediaAsset ma = getMediaAsset();
src/main/java/org/ecocean/Annotation.java:892:        if (this.getMediaAsset() != null) {
src/main/java/org/ecocean/Annotation.java:893:            jobj = this.getMediaAsset().sanitizeJson(request,
src/main/java/org/ecocean/Annotation.java:1439:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:1471:        int maId = (Integer)validateFieldValue("mediaAssetId", payload);
src/main/java/org/ecocean/Annotation.java:1487:            error.put("fieldName", "mediaAssetId");
src/main/java/org/ecocean/Annotation.java:1726:        case "mediaAssetId":
src/main/java/org/ecocean/media/Feature.java:104:    public MediaAsset getMediaAsset() {
src/main/java/org/ecocean/media/Feature.java:164:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/media/Feature.java:182:        if (this.getMediaAsset() != null)
src/main/java/org/ecocean/media/Feature.java:183:            jobj.put("mediaAsset",
src/main/java/org/ecocean/media/Feature.java:184:                this.getMediaAsset().sanitizeJson(request,
src/main/java/org/ecocean/media/MediaAsset.java:1361:        return store.mediaAssetToHtmlElement(this, request, myShepherd, ann);
src/main/java/org/ecocean/media/MediaAsset.java:1759:        return myShepherd.getMediaAsset(id);

 succeeded in 217ms:
package org.ecocean.media;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A Feature is a unique set of (arbitrary) data that contributes to helping with detection and identification of images (or other data, e.g. audio).
 * It is metadata that can be generated either manually or automated (or both).
 */
public class Feature implements java.io.Serializable {
    static final long serialVersionUID = 8844223450443974780L;
    protected String id = null;

    // NOTE: a null for feature type we call the "unity" case -- meaning it serves only as a direct map from Annotation to MediaAsset.
    // this can be thought of as "no" feature, or the "entire asset" feature, etc. (ostensibly parameters would be ignored/useless, but that may
    // change in the future)
    protected FeatureType type;

    protected JSONObject parameters;
    protected String parametersAsString;

    // this link back to the objs with .features that include us
    protected Annotation annotation;
    protected MediaAsset asset;

    protected long revision;

    // effectively creates a "unity" feature
    public Feature() {
        this(Util.generateUUID(), null, null);
    }
/*
    public Feature(final String typeAsString, final JSONObject params) {
        this(new FeatureType(typeAsString), params);
    }
 */
    public Feature(final FeatureType type, final JSONObject params) {
        this(Util.generateUUID(), type, params);
    }

    public Feature(final String featureTypeId, final JSONObject params) {
        this(Util.generateUUID(), FeatureType.load(featureTypeId), params);
    }

    public Feature(final String id, final FeatureType type, final JSONObject params) {
        if (id == null) throw new IllegalArgumentException("id is null");
        this.id = id;
        this.type = type;
        this.parameters = params;
        if (params != null) this.parametersAsString = params.toString();
        this.setRevision();
    }

    public String getId() {
        return id;
    }

    public void setId(String i) {
        id = i;
    }

    public FeatureType getType() {
        return type;
    }

    public void setType(FeatureType t) {
        type = t;
    }

    public boolean isType(String tid) { // pass in string version of FeatureType.id (e.g. "org.ecocean.fubar")
        if ((type == null) && (tid == null)) return true; // who would really do this?
        if (type == null) return false;
        return type.getId().equals(tid);
    }

    public boolean isUnity() {
        return (type == null);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    /**
     * Explicitly sets the owning Annotation. The {@code Annotation.features}
     * collection is {@code mapped-by="annotation"} in the JDO mapping, so the
     * Annotation side is the inverse and this side is the owner; the owner
     * must be set for the {@code ANNOTATION_FEATURES} relationship to
     * populate reliably without depending on DataNucleus relationship
     * management. {@code MediaAsset.addFeature(f)} already sets
     * {@code Feature.asset}; this method is the symmetric setter for the
     * Annotation side.
     */
    public void setAnnotation(Annotation ann) {
        this.annotation = ann;
    }

    public MediaAsset getMediaAsset() {
        return asset;
    }

    public JSONObject getParameters() {
        if (parameters != null) return parameters;
        if (parametersAsString == null) return null;
        try {
            parameters = new JSONObject(parametersAsString);
        } catch (JSONException je) {
            System.out.println(this + " -- error parsing parameters json string (" +
                parametersAsString + "): " + je.toString());
            return null;
        }
        return parameters;
    }

    public void setParameters(JSONObject p) {
        parameters = p;
        parametersAsString = (p == null) ? null : p.toString();
    }

    // only DataNucleus should be calling get/setParametersAsString. always use get/setParameters() instead.
    public String getParametersAsString() {
        if (parametersAsString != null) return parametersAsString;
        if (parameters == null) return null;
        parametersAsString = parameters.toString();
        return parametersAsString;
    }

    public void setParametersAsString(String p) {
        parametersAsString = p;
        parameters = null; // force lazy re-parse on next getParameters()
    }

    public long getRevision() {
        return revision;
    }

    public long setRevision() {
        this.revision = System.currentTimeMillis();
        return this.revision;
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("id", id)
                   .append("rev", revision)
                   .append("type", type)
                   .append("asset", asset)
                   .toString();
    }

    /*
        this is how we derive a MediaAsset from the source MediaAsset this Feature references.
        for images, this might do some kind of clipping or rotation etc.  perhaps in the future this may need to return N MediaAssets?
        ideally we would break this out into neater classes, perhaps per MediaAsset type (image, video, sound)
     */
    public MediaAsset createMediaAsset()
    throws IOException {
        MediaAsset ma = this.getMediaAsset();

        if (ma == null) return null;
        if (this.isUnity()) return null; // we shouldnt make a new MA that is identical, right?
        HashMap<String, Object> hmap = new HashMap<String, Object>();
        hmap.put("feature", this);
        return ma.updateChild("feature", hmap);
    }

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        boolean fullAccess)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONObject jobj =
            new org.datanucleus.api.rest.orgjson.JSONObject();
        jobj.put("id", id);
        if (this.getType() != null) jobj.put("type", this.getType().getId());
        if (this.getParameters() != null)

 succeeded in 246ms:
                params.put("transformMatrix", getTransformMatrix());
            } else {
                params.put("x", getX());
                params.put("y", getY());
            }
            f = new Feature("org.ecocean.boundingBox", params);
        }
        __getMediaAsset().addFeature(f);
        addFeature(f);
        return f;
    }

    public void setAcmId(String id) {
        this.acmId = id;
        this.setVersion();
    }

    public String getAcmId() {
        return this.acmId;
    }

    public boolean hasAcmId() {
        return (this.acmId != null);
    }

    // ----- ml-service migration v2 idempotency / WBIA-registration accessors -----

    public String getPredictModelId() { return predictModelId; }
    public void setPredictModelId(String s) { this.predictModelId = s; this.setVersion(); }

    public String getBboxKey() { return bboxKey; }

 succeeded in 972ms:
docs/plans/2026-05-18-empty-match-prospects-design.md:345:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-design.md:346:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
docs/plans/2026-05-18-empty-match-prospects-design.md:348:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-design.md:356:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
docs/plans/2026-05-18-empty-match-prospects-design.md:364:  (`predictModelId` set but `EMBEDDING` row never created).
docs/plans/2026-05-18-empty-match-prospects-design.md:757:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
docs/plans/2026-05-18-empty-match-prospects-design.md:824:   ANNOTATION + ANNOTATION_FEATURES + MEDIAASSET_FEATURES +
docs/plans/2026-05-18-empty-match-prospects-design.md:825:   EMBEDDING (4-table join). Per round-2 answer #5, direct SQL
archive/sql/feature_types.sql:3:-- Data for Name: FEATURETYPE; Type: TABLE DATA; Schema: public; Owner: flukebook
archive/sql/feature_types.sql:6:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.flukeEdge.referenceSpots', NULL);
archive/sql/feature_types.sql:7:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.flukeEdge.edgeSpots', NULL);
archive/sql/feature_types.sql:8:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.dorsalEdge.referenceSpots', NULL);
archive/sql/feature_types.sql:9:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.dorsalEdge.edgeSpots', NULL);
archive/sql/feature_types.sql:10:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.boundingBox', NULL);
archive/sql/feature_types.sql:11:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.whaleshark.referenceSpots', NULL);
archive/sql/feature_types.sql:12:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.whaleshark.spots', NULL);
archive/sql/feature_types.sql:13:INSERT INTO "FEATURETYPE" VALUES ('org.ecocean.MediaAssetPlaceholder', NULL);
archive/sql/ml_service_idempotency.sql:23:ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
archive/sql/ml_service_idempotency.sql:39:                    "MEDIAASSET_ID_OID" IS NOT NULL AND
src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
src/main/resources/org/ecocean/package.jdo:1064:			<index name="EMBEDDING_CREATED_IDX" />
src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
archive/sql/ml_service_embedding_audit.sql:1:-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
archive/sql/ml_service_embedding_audit.sql:3:-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
archive/sql/ml_service_embedding_audit.sql:12:FROM "EMBEDDING"
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1199:    35	    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2955:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:2956:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4392:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4874:   326	                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4896:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:426:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:427:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:429:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:437:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:445:  (`predictModelId` set but `EMBEDDING` row never created).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:812:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:859:   My draft uses `EMBEDDING.METHODVERSION = ?` strictly — Codex
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1007:docs/plans/2026-05-18-empty-match-prospects-design.md:637:  (`skipAutoIndexing=true`, no EMBEDDING row); prove it's
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2272:    35	    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2566:   326	                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2588:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5541:/bin/bash -lc "rg -n \"ANNOTATION_FEATURES|MEDIAASSET_FEATURES|name=\\\"features\\\"|mapped-by=\\\"annotation\\\"|mapped-by=\\\"asset\\\"|class name=\\\"Annotation\\\"|class name=\\\"MediaAsset\\\"|class name=\\\"Embedding\\\"|ANNOTATION_ID|dependent-element\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/media/package.jdo src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:5642:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6125:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6127:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6242:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6260:/bin/bash -lc 'rg -n "ANNOTATION_FEATURES|MEDIAASSET_FEATURES|MEDIAASSET_ID|ASSET_ID|FEATURES|ANNOTATION_ID" archive src/test src/main/java | head -200' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6266:archive/sql/ml_service_idempotency.sql:23:ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6267:archive/sql/ml_service_idempotency.sql:39:                    "MEDIAASSET_ID_OID" IS NOT NULL AND
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6268:src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6269:src/main/java/org/ecocean/Annotation.java:601:     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6270:src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6271:src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6272:src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6273:src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6306:    23	ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6322:    39	                    "MEDIAASSET_ID_OID" IS NOT NULL AND
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6377:   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6378:   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6397:   601	     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9059:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
docs/plans/2026-05-09-ml-service-migration-v2.md:69:        │       mediaAsset set explicitly (so MEDIAASSET_ID_OID populated
docs/plans/2026-05-09-ml-service-migration-v2.md:224:For the partial unique index to actually constrain duplicates, `Annotation.MEDIAASSET_ID_OID` must be populated. The Wildbook context note that `MEDIAASSET_ID_OID` is "not populated in practice" applies to *legacy* annotations created before the Wildbook-owns-acmId era; v2 explicitly sets `ann.setMediaAsset(ma)` in MlServiceProcessor's persistence. For commit #4 to function correctly, every ml-service-created annotation has both `predictModelId` AND `MEDIAASSET_ID_OID` populated; legacy annotations remain unaffected by the partial unique constraint (filtered out by the `WHERE PREDICTMODELID IS NOT NULL` clause).
docs/plans/2026-05-09-ml-service-migration-v2.md:230:ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
docs/plans/2026-05-09-ml-service-migration-v2.md:333:    SELECT "ANNOTATION_ID" FROM "EMBEDDING"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:68:`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:79:- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:83:- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:383:JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:384:JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:386:JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:394:The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:402:  (`predictModelId` set but `EMBEDDING` row never created).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:603:  set up an annotation with no EMBEDDING row, prove it's filtered
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:652:   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2491:   326	                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2513:   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:2582:    35	    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3378:   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3647:   148	        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3762:src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3766:src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3767:src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3797:  1047			<index name="EMBEDDING_ANN_METHOD_VER_IDX">
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3814:  1064				<index name="EMBEDDING_CREATED_IDX" />
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3819:  1069				<index name="EMBEDDING_METHOD_IDX" />
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3824:  1074				<index name="EMBEDDING_METHODVERSION_IDX" />
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4003:    94	     * must be set for the {@code ANNOTATION_FEATURES} relationship to
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:4180:   601	     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5498:src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5574:   181	        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5604:   211	        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5614:   221	            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9062:   208	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9063:   209	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9065:   211	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9073:   219	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9081:   227	  (`predictModelId` set but `EMBEDDING` row never created).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9302:   428	  set up an annotation with no EMBEDDING row, prove it's filtered
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9356:   477	   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
src/main/java/org/ecocean/Annotation.java:601:     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2
src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
src/main/java/org/ecocean/Embedding.java:181:        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
src/main/java/org/ecocean/Embedding.java:211:        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
src/main/java/org/ecocean/Embedding.java:221:            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
src/main/java/org/ecocean/Embedding.java:261:        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:35:    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:326:                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:348:                //     ANNOTATION_FEATURES join row depends on DataNucleus
src/main/java/org/ecocean/media/Feature.java:94:     * must be set for the {@code ANNOTATION_FEATURES} relationship to
src/main/java/org/ecocean/servlet/importer/ImportTask.java:781:            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
src/main/java/org/ecocean/servlet/importer/ImportTask.java:782:            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +

exec
/bin/bash -lc "sed -n '580,625p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '760,795p' src/main/java/org/ecocean/servlet/importer/ImportTask.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '675,725p' src/main/java/org/ecocean/api/bulk/BulkImporter.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "skipAutoIndexing|new Annotation|setMatchAgainst|placeholder|addAnnotation" src/main/java/org/ecocean/api/bulk/BulkImporter.java' in /mnt/c/Wildbook-clean2
 succeeded in 222ms:
                System.out.println("[INFO] processRow() ignored a field [" + fieldName +
                    "] that was flagged valid");
            }
        }
        // fields done
        System.out.println("+ populated data on " + enc);
        // now attach annotations
        String tx = enc.getTaxonomyString();
        List<Annotation> annots = new ArrayList<Annotation>();
        int offset = 0;
        for (String maKey : maFields) {
            if (maKey == null) continue; // data skipped an index
            BulkValidator bv = fmap.get(maKey);
            if (bv == null) throw new RuntimeException("could not find fmap for key=" + maKey);
            if (bv.valueIsNull()) continue;
            MediaAsset ma = this.mediaAssetMap.get(bv.getValueString());
            if (ma == null)
                throw new RuntimeException("could not find MediaAsset for maKey=" + maKey +
                        ", bv=" + bv.getValueString() + " in " + this.mediaAssetMap);
            Set<String> kws = new HashSet<String>();
            if ((offset < kwFields.size()) && (kwFields.get(offset) != null))
                kws.add(fmap.get(kwFields.get(offset)).getValueString());
            // StandardImport claims multivalue keywordS is delimited by underscore :/ is this for real?
            if ((offset < multiKwFields.size()) && (multiKwFields.get(offset) != null)) {
                String multi = fmap.get(multiKwFields.get(offset)).getValueString();
                if (multi != null) kws.addAll(Arrays.asList(multi.split("_")));
            }
            handleKeywords(ma, kws);
            Annotation ann = new Annotation(tx, ma);
            ann.setIsExemplar(true);
            ann.setSkipAutoIndexing(true);
            if ((offset < maQuality.size()) && (maQuality.get(offset) != null))
                ann.setQuality(fmap.get(maQuality.get(offset)).getValueDouble());
            annots.add(ann);
            offset++;
        }
        if (annots.size() > 0) enc.addAnnotations(annots);
        System.out.println("+ populated " + annots.size() + " MediaAssets on " + enc);
    }

    public void markProgress(int ticks, int total, double base, double weight) {
        if (this.importTaskId == null) return;
        // we want our own shepherd here so we can persist this task independent of our main shepherd
        Shepherd taskShepherd = new Shepherd(this.myShepherd.getContext());
        taskShepherd.setAction("BulkImporter.markProgress");
        taskShepherd.beginDBTransaction();
        try {
            ImportTask itask = taskShepherd.getImportTask(this.importTaskId);
            if (itask == null) return;
            Double progress = base + (weight * new Double(ticks) / new Double(total));
            itask.setProcessingProgress(progress);

 succeeded in 241ms:
                resp.optDouble("score", -1.0) + ") for " + this);
            return viewpoint;
        } catch (RuntimeException | IOException | java.security.NoSuchAlgorithmException |
            java.security.InvalidKeyException ex) {
            throw new IOException("setViewpointFromIA() on " + this + " failed: " + ex.toString());
        }
    }

/*
    //  response comes from ia thus: "response": [{"score": 0.9783339699109396, "species": "giraffe_reticulated", "viewpoint": "right"}] public static
       JSONObject iaViewpointFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException,
       NoSuchAlgorithmException, InvalidKeyException {
 */

// TODO: Deprecate "all of this" now that deployed sites are migrated
    public MediaAsset __getMediaAsset() {
        return mediaAsset;
    }

    /**
     * Direct setter for the {@code mediaAsset} field — populates the
     * underlying {@code ANNOTATION.MEDIAASSET_ID_OID} column. The plan v2
     * partial unique index on (mediaAsset, predictModelId, bboxKey, thetaKey)
     * needs this column to be populated on ml-service-created annotations.
     * Bumps version for OpenSearch reindex pickup. Mirrors the
     * {@link #__getMediaAsset} naming convention used by infrastructure
     * code (use {@link #getMediaAsset()} when you want the proper
     * Feature-based linkage).
     */
    public void __setMediaAsset(MediaAsset ma) {
        this.mediaAsset = ma;
        this.setVersion();
    }

    public MediaAsset getMediaAsset() {
        ArrayList<Feature> fts = getFeatures();

        if ((fts == null) || (fts.size() < 1) || (fts.get(0) == null)) {
            System.out.println("WARNING: annotation " + this.getId() +
                " is featureless, falling back to deprecated __getMediaAsset().  please fix!");
            return __getMediaAsset();
        }
        return fts.get(0).getMediaAsset();
    }

    public boolean hasMediaAsset() {

 succeeded in 241ms:
    public static Map<String, Integer> getAllEncounterCounts(Shepherd myShepherd) {
        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT \"ID_OID\", count(*) FROM \"IMPORTTASK_ENCOUNTERS\" GROUP BY \"ID_OID\"");

        return parseSqlCountResults(query);
    }

    public static Map<String, Integer> getAllIndividualCounts(Shepherd myShepherd) {
        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT ie.\"ID_OID\", count(distinct me.\"INDIVIDUALID_OID\") " +
            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" me " +
            "ON ie.\"CATALOGNUMBER_EID\" = me.\"CATALOGNUMBER_EID\" " + "GROUP BY ie.\"ID_OID\"");

        return parseSqlCountResults(query);
    }

    public static Map<String, Integer> getAllMediaAssetCounts(Shepherd myShepherd) {
        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT ie.\"ID_OID\", count(distinct mf.\"ID_OID\") " +
            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"ENCOUNTER_ANNOTATIONS\" ea " +
            "ON ie.\"CATALOGNUMBER_EID\" = ea.\"CATALOGNUMBER_OID\" " +
            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
            "GROUP BY ie.\"ID_OID\"");

        return parseSqlCountResults(query);
    }
}

 succeeded in 284ms:
703:            Annotation ann = new Annotation(tx, ma);
711:        if (annots.size() > 0) enc.addAnnotations(annots);

exec
/bin/bash -lc "rg -n \"public Annotation\\(\" src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '120,180p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1360,1395p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '180,230p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
 succeeded in 233ms:
30:    public Annotation() {}
121:    public Annotation(String species, MediaAsset ma) {
126:    public Annotation(String species, Feature f) {
133:    public Annotation(String species, ArrayList<Feature> f) {
140:    public Annotation(String species, Feature f, String iaClass) {
148:    public Annotation(String species, ArrayList<Feature> f, String iaClass) {

 succeeded in 210ms:
        Encounter someEnc = null; // this is in case we fall thru (no trivial annot), we can clone some of this for new one
        for (Annotation ann : sibs) {
            Encounter enc = ann.findEncounter(myShepherd);
            if (ann.isTrivial()) {
                ann.setMatchAgainst(false);
                if (enc == null) { // weird case, but yneverknow (trivial annot with no encounter?)
                    ann.detachFromMediaAsset(); // but this.annot is now on asset, so we are good: kill ann!
                } else {
                    // this also does the detachFromMediaAsset() for us
                    enc.replaceAnnotation(ann, this);
                    return enc; // our work is done here
                }
                break;
            }
            if (someEnc == null) someEnc = enc; // use the first one we find to base new one (below) off of, if necessary
        }
        // do we have an an encounter from the sibling?
        if (someEnc != null) {
            for (Annotation ann : sibs) {
                if ((ann.getIAClass() == null || this.getIAClass() == null) ||
                    ann.getIAClass().equals(this.getIAClass())) { break; }
                // if these two intersect and have a different detected class they are allowed to reside on the same encounter
                if (this.intersects(ann)) {
                    someEnc.addAnnotation(this);
                    someEnc.setDWCDateLastModified();
                    return someEnc;
                }
            }
        }
        // if we fall thru, we have no trivial annot, so just get a new Encounter for this Annotation
        Encounter newEnc = null;
        if (someEnc == null) {
            newEnc = new Encounter(this);
        } else { // copy some stuff from sibling
            try {
                newEnc = someEnc.cloneWithoutAnnotations(myShepherd);

 succeeded in 210ms:
        map.put("encounterSubmitterId", keywordType);
        map.put("encounterUserUuid", keywordType);
        map.put("encounterLocationId", keywordType);
        map.put("encounterTaxonomy", keywordType);
        map.put("encounterProjectIds", keywordType);

        // all case-insensitive keyword-ish types
        // map.put("fubar", keywordNormalType);

        // embeddings have some metadata (algorithm etc)
        // and then the vector that is the embedding
        JSONObject embMap = new JSONObject();
        embMap.put("type", "nested");
        embMap.put("dynamic", false);
        JSONObject embProps = new JSONObject();
        embProps.put("method", keywordType);
        embProps.put("methodVersion", keywordType);
        JSONObject embVect = new JSONObject();
        // https://docs.opensearch.org/docs/latest/vector-search/creating-vector-index/
        embVect.put("type", "knn_vector");
        embVect.put("dimension", Embedding.getVectorDimension());
        embVect.put("space_type", "cosinesimil");
        embProps.put("vector", embVect);
        embMap.put("properties", embProps);
        map.put("embeddings", embMap);

        return map;
    }

    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
    throws IOException, JsonProcessingException {
        super.opensearchDocumentSerializer(jgen, myShepherd);

        jgen.writeStringField("acmId", this.getAcmId());
        jgen.writeStringField("viewpoint", this.getViewpoint());
        jgen.writeStringField("iaClass", this.getIAClass());
        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
        MediaAsset ma = this.getMediaAsset();
        if (ma != null) {
            jgen.writeNumberField("mediaAssetId", ma.getIdInt());
        }
        Encounter enc = this.findEncounter(myShepherd);
        if (enc != null) {
            jgen.writeStringField("encounterId", enc.getId());
            jgen.writeStringField("encounterSubmitterId", enc.getSubmitterID());
            jgen.writeStringField("encounterLocationId", enc.getLocationID());
            jgen.writeStringField("encounterTaxonomy", enc.getTaxonomyString());
            // per discussion on issue 874, including this in indexing, but not (yet) using in matchingSet
            jgen.writeStringField("encounterLivingStatus", enc.getLivingStatus());
            User owner = enc.getSubmitterUser(myShepherd);
            if (owner != null) jgen.writeStringField("encounterUserUuid", owner.getId());

 succeeded in 222ms:
    // the "trivial" Annotation - will have a single feature which references the total MediaAsset
    public Annotation(String species, MediaAsset ma) {
        this(species, ma.generateUnityFeature());
    }

    // single feature convenience constructor
    public Annotation(String species, Feature f) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = new ArrayList<Feature>();
        this.features.add(f);
    }

    public Annotation(String species, ArrayList<Feature> f) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = f;
    }

    // For setting the iaClass returned from detection... No more mangled species names sent to identification
    public Annotation(String species, Feature f, String iaClass) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = new ArrayList<Feature>();
        this.features.add(f);
        this.iaClass = iaClass;
    }

    public Annotation(String species, ArrayList<Feature> f, String iaClass) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = f;
        this.iaClass = iaClass;
    }

    @Override public String opensearchIndexName() { return "annotation"; }

    @Override public long getVersion() {
        return version;
    }

    public long setVersion() {
        version = System.currentTimeMillis();
        return version;
    }

    public JSONObject opensearchMapping() {
        JSONObject map = super.opensearchMapping();
        JSONObject keywordType = new JSONObject("{\"type\": \"keyword\"}");

/*
        JSONObject keywordNormalType = new org.json.JSONObject(
            "{\"type\": \"keyword\", \"normalizer\": \"wildbook_keyword_normalizer\"}");
 */

        // "id" is done in Base
        map.put("viewpoint", keywordType);
        map.put("iaClass", keywordType);
        map.put("acmId", keywordType);
        map.put("encounterId", keywordType);
        map.put("encounterSubmitterId", keywordType);

exec
/bin/bash -lc "rg -n \"generateUnityFeature|addFeature\\(\" src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1080,1120p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '850,890p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '300,315p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
 succeeded in 225ms:
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        org.datanucleus.api.rest.orgjson.JSONObject jobj, Shepherd myShepherd)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        return sanitizeJson(request, jobj, true, myShepherd);
    }

    // fullAccess just gets cascaded down from Encounter -> Annotation -> us... not sure if it should win vs security(request)
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        org.datanucleus.api.rest.orgjson.JSONObject jobj, boolean fullAccess, Shepherd myShepherd)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        jobj.put("id", this.getId());
        jobj.put("acmId", this.getAcmId());
        jobj.put("detectionStatus", this.getDetectionStatus());
        jobj.remove("parametersAsString");
        // jobj.put("guid", "http://" + CommonConfiguration.getURLLocation(request) + "/api/org.ecocean.media.MediaAsset/" + id);

        HashMap<String, String> s = new HashMap<String, String>();
        s.put("type", store.getType().toString());
        jobj.put("store", s);

        String context = ServletUtilities.getContext(request);
        // Shepherd myShepherd = new Shepherd(context);
        // myShepherd.setAction("MediaAsset.class_1");
        // myShepherd.beginDBTransaction();
        ArrayList<Feature> fts = getFeatures();
        if ((fts != null) && (fts.size() > 0)) {
            org.datanucleus.api.rest.orgjson.JSONArray jarr =
                new org.datanucleus.api.rest.orgjson.JSONArray();
            for (int i = 0; i < fts.size(); i++) {
                org.datanucleus.api.rest.orgjson.JSONObject jf =
                    new org.datanucleus.api.rest.orgjson.JSONObject();
                Feature ft = fts.get(i);
                jf.put("id", ft.getId());
                try { // for some reason(?) this will get a jdo error for "row not found".  why???  anyhow, we catch it
                    jf.put("type", ft.getType());
                } catch (Exception ex) {
                    jf.put("type", "unknown");
                    System.out.println("ERROR: MediaAsset.sanitizeJson() on " + this.toString() +
                        " threw " + ex.toString());
                }
                JSONObject p = ft.getParameters();

 succeeded in 236ms:
src/main/java/org/ecocean/media/MediaAsset.java:443:    public void addFeature(Feature f) {
src/main/java/org/ecocean/media/MediaAsset.java:596:    public Feature generateUnityFeature() {
src/main/java/org/ecocean/media/MediaAsset.java:597:        return generateUnityFeature(true);
src/main/java/org/ecocean/media/MediaAsset.java:600:    public Feature generateUnityFeature(boolean addToMediaAsset) {
src/main/java/org/ecocean/media/MediaAsset.java:603:        if (addToMediaAsset) this.addFeature(f);
src/main/java/org/ecocean/media/MediaAsset.java:617:        this.addFeature(f);
src/main/java/org/ecocean/Annotation.java:122:        this(species, ma.generateUnityFeature());
src/main/java/org/ecocean/Annotation.java:307:        __getMediaAsset().addFeature(f);
src/main/java/org/ecocean/Annotation.java:308:        addFeature(f);
src/main/java/org/ecocean/Annotation.java:364:    public void addFeature(Feature f) {
src/main/java/org/ecocean/Annotation.java:1538:        ma.addFeature(ft);

 succeeded in 231ms:
        List<MediaAsset> children = findChildren(myShepherd);
        return (children != null && children.size() > 0);
    }

    public ArrayList<MediaAsset> findChildren(Shepherd myShepherd) {
        if (store == null) return null;
        ArrayList<MediaAsset> all = store.findAllChildren(this, myShepherd);
        return all;
    }

    public ArrayList<MediaAsset> findChildrenByLabel(Shepherd myShepherd, String label) {
        ArrayList<MediaAsset> all = this.findChildren(myShepherd);

        if ((all == null) || (all.size() < 1)) return null;
        ArrayList<MediaAsset> matches = new ArrayList<MediaAsset>();
        for (MediaAsset ma : all) {
            if ((ma.getLabels() != null) && ma.getLabels().contains(label)) matches.add(ma);
        }
        return matches;
    }

    // NOTE: these currrently do not recurse.  this makes a big assumption that one only wants children of _original
    // (e.g. on an encounter) and will *probably* need to change in the future.
    public static MediaAsset findOneByLabel(ArrayList<MediaAsset> mas, Shepherd myShepherd,
        String label) {
        ArrayList<MediaAsset> all = findAllByLabel(mas, myShepherd, label, true);

        if ((all == null) || (all.size() < 1)) return null;
        return all.get(0);
    }

    public static ArrayList<MediaAsset> findAllByLabel(ArrayList<MediaAsset> mas,
        Shepherd myShepherd, String label) {
        return findAllByLabel(mas, myShepherd, label, false);
    }

    private static ArrayList<MediaAsset> findAllByLabel(ArrayList<MediaAsset> mas,
        Shepherd myShepherd, String label, boolean onlyOne) {
        if ((mas == null) || (mas.size() < 1)) return null;
        ArrayList<MediaAsset> found = new ArrayList<MediaAsset>();
        for (MediaAsset ma : mas) {

 succeeded in 240ms:
                params.put("transformMatrix", getTransformMatrix());
            } else {
                params.put("x", getX());
                params.put("y", getY());
            }
            f = new Feature("org.ecocean.boundingBox", params);
        }
        __getMediaAsset().addFeature(f);
        addFeature(f);
        return f;
    }

    public void setAcmId(String id) {
        this.acmId = id;
        this.setVersion();
    }

exec
/bin/bash -lc "sed -n '430,455p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 246ms:
    public boolean hasLabel(String s) {
        if (labels == null) return false;
        return labels.contains(s);
    }

    public ArrayList<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(ArrayList<Feature> f) {
        features = f;
    }

    public void addFeature(Feature f) {
        if (features == null) features = new ArrayList<Feature>();
        if (!features.contains(f)) {
            features.add(f);
            f.asset = this;
        }
    }

    // note: this will outright deletes feature (from db, blame datanucleus), and thus will
    // break the reference from Annotation-Feature that (likely) existed ... oops?
    public void removeFeature(Feature f) {
        if (features == null) return;
        System.out.println("INFO: removeFeature() killing off " + f + " from asset id=" + this.id);

exec
/bin/bash -lc "sed -n '590,607p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 243ms:

    public void setUserLongitude(Double lon) {
        this.userLongitude = lon;
    }

    // note: default behavior will add this to the features on this MediaAsset -- can pass false to disable
    public Feature generateUnityFeature() {
        return generateUnityFeature(true);
    }

    public Feature generateUnityFeature(boolean addToMediaAsset) {
        Feature f = new Feature();

        if (addToMediaAsset) this.addFeature(f);
        return f;
    }

    // if unity feature is appropriate, generates that; otherwise does a boundingBox one

exec
/bin/bash -lc "sed -n '355,375p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
 succeeded in 249ms:
        if (Util.collectionSize(features) < 1) return null;
        return features.get(0);
    }

    public void setFeatures(ArrayList<Feature> f) {
        features = f;
        this.setVersion();
    }

    public void addFeature(Feature f) {
        if (features == null) features = new ArrayList<Feature>();
        if (!features.contains(f)) features.add(f);
        this.setVersion();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;

exec
/bin/bash -lc "sed -n '265,380p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
 succeeded in 239ms:
    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
        // Migration plan v2 §commit #3: gate accepts the new _id_conf
        // contract (entries with `method`/`version`/`pipeline_root` and no
        // api_endpoint) as well as legacy entries (with `api_endpoint`).
        if (iaConfig == null) return false;
        boolean isVectorConfig = Util.stringExists(iaConfig.optString("method", null))
            || Util.stringExists(iaConfig.optString("api_endpoint", null));
        if (!isVectorConfig) return false;
        // from here on out we should return true since this is a vector match, even when something goes wrong
        // and we should also set status on the task (and subtasks)
        if (task == null) return true; // cant really set status on this :(
        if (task.numberAnnotations() < 1) {
            task.setStatus("completed");
            task.setCompletionDateInMilliseconds();
            return true;
        }
        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
            task.numberAnnotations() + " annots) has embedding match: " + iaConfig);
        // Track per-subtask outcomes so the parent task's terminal state
        // reflects reality (plan v2 §commit #3: previous code unconditionally
        // marked the parent "completed" even if every subtask failed).
        int subtasksOk = 0;
        int subtasksFailed = 0;
        for (Annotation ann : task.getObjectAnnotations()) {
            // every ann gets a subTask
            Task subTask = new Task(task);
            subTask.addObject(ann);
            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
            // TODO not sure if this is wise, or it would be better to just fail outright and let some background process do this
            if (ann.numberEmbeddings() < 1) {
                System.out.println(
                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
                    ann);
                try {
                    ann.extractEmbeddings(myShepherd);
                } catch (IAException ex) {
                    System.out.println(
                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
                        " due to: " + ex);
                }
                // if none now, we just fail and continue onto next annot
                if (ann.numberEmbeddings() < 1) {
                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
                        " due to no suitable embeddings for " + iaConfig);
                    subTask.setStatus("error");
                    subTask.setStatusDetailsAddError("REQUIRED",
                        "no suitable embeddings for getMatches()");
                    subTask.setCompletionDateInMilliseconds();
                    myShepherd.getPM().makePersistent(subTask);
                    subtasksFailed++;
                    continue;
                }
            }
            // Build matchingSetQuery for the candidate count.
            boolean useClauses = false; // TODO how??
            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
                useClauses);
            // getMatchingSetQuery can return null (e.g. encounter missing,
            // taxonomy filtered out). Skip this subtask cleanly rather than
            // NPE'ing on the queryCount/getMatchQuery below.
            if (matchingSetQuery == null) {
                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("REQUIRED", "null matchingSetQuery");
                subTask.setCompletionDateInMilliseconds();
                myShepherd.getPM().makePersistent(subTask);
                subtasksFailed++;
                continue;
            }
            // Count eligible candidates BEFORE getMatchQuery, which mutates
            // matchingSetQuery in place (Annotation.java:1203). The previous
            // order returned the knn result count instead of the candidate
            // count. Defense in depth: also pass a deep clone of
            // matchingSetQuery to getMatchQuery so the eligible-set object
            // never silently changes shape if a future caller relies on it.
            OpenSearch os = new OpenSearch();
            int numberCandidates = -2;
            try {
                numberCandidates = os.queryCount("annotation", matchingSetQuery);
            } catch (IOException ex) {
                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
            }
            // Read method/version directly from the new _id_conf contract;
            // fall back to splitting model_id for legacy configs.
            String method = iaConfig.optString("method", null);
            String version = iaConfig.optString("version", null);
            if (!Util.stringExists(method)) {
                String[] mv = MLService.getMethodValues(iaConfig);
                method = mv[0];
                version = mv[1];
            }
            JSONObject matchQuery = ann.getMatchQuery(method, version,
                new JSONObject(matchingSetQuery.toString()));
            // i think this will never happen now, due to on-the-fly fix above; but leaving to be safe
            if (matchQuery == null) {
                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
                    " due to no suitable embeddings for " + iaConfig);
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("REQUIRED",
                    "no suitable embeddings for getMatches()");
                subTask.setCompletionDateInMilliseconds();
                myShepherd.getPM().makePersistent(subTask);
                subtasksFailed++;
                continue; // on to next ann
            }
            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
            System.out.println("findMatchProspects() on " + ann + " found " +
                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
                " candidates) for subTask " + subTask.getId());
            boolean mrOk = true;
            try {
                // we build this even if empty, cuz that means we got results; just not nice ones
                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
                myShepherd.getPM().makePersistent(mr);
            } catch (IOException ex) {

exec
/bin/bash -lc "sed -n '380,455p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
 succeeded in 203ms:
            } catch (IOException ex) {
                System.out.println("findMatchProspects() MatchResult creation failed on " +
                    subTask + ": " + ex);
                ex.printStackTrace();
                mrOk = false;
            }
            if (mrOk) {
                subTask.setStatus("completed");
                subtasksOk++;
            } else {
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("UNKNOWN",
                    "MatchResult persistence failed");
                subtasksFailed++;
            }
            subTask.setCompletionDateInMilliseconds();
            myShepherd.getPM().makePersistent(subTask);
        }
        // Reflect subtask outcomes on the parent. Any failure marks the
        // parent "error" with a PARTIAL detail; otherwise "completed".
        // No "partial" terminal status is introduced — Task.statusInEndState
        // only recognizes completed/error, so adding one would ripple into
        // polling/UI semantics.
        if (subtasksFailed == 0) {
            task.setStatus("completed");
        } else {
            task.setStatus("error");
            task.setStatusDetailsAddError("PARTIAL",
                subtasksFailed + " of " + (subtasksOk + subtasksFailed) + " subtasks failed");
        }
        task.setCompletionDateInMilliseconds();
        return true;
    }

    public String toString() {
        String st = "Embedding " + id;

        st += " (vec len " + this.vectorLength() + ")";
        if (annotation != null) st += " [Annotation " + annotation.getId() + "]";
        st += " " + this.getMethodDescription();
        st += " " + Util.prettyPrintDateTime(this.created);
        return st;
    }

    public boolean equals(final Object o2) {
        if (o2 == null) return false;
        if (!(o2 instanceof Embedding)) return false;
        Embedding two = (Embedding)o2;
        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
        return this.id.equals(two.getId());
    }

    public int hashCode() {
        if (id == null) return Util.generateUUID().hashCode();
        return id.hashCode();
    }
}

exec
/bin/bash -lc "rg -n \"annotationId|processExtraction|extract\\(|MlServiceProcessor|mlServiceV2.*annotation|addToDetectionQueue\\(.*annotation\" src/main/java/org/ecocean src/test/java/org/ecocean" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1528,1558p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '740,765p' src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "matchAgainst" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/identity/IBEISIA.java' in /mnt/c/Wildbook-clean2
 succeeded in 197ms:
    }

    public Boolean getIsOfInterest() {
        return isOfInterest;
    }

    public void setIsOfInterest(Boolean b) {
        isOfInterest = b;
    }

    public boolean getMatchAgainst() {
        return matchAgainst;
    }

    public void setMatchAgainst(boolean b) {
        matchAgainst = b;
        this.setVersion();
    }

    public String getIdentificationStatus() {
        return this.identificationStatus;
    }

    public void setIdentificationStatus(String status) {
        this.identificationStatus = status;
        this.setVersion();

 succeeded in 201ms:
        fparams.put("x", x);
        fparams.put("y", y);
        fparams.put("width", width);
        fparams.put("height", height);
        fparams.put("theta", theta);
        fparams.put("viewpoint", viewpoint); // not sure when/how this is used, but seems here historically
        fparams.put("_manualAnnotationViaApiV3", System.currentTimeMillis());
        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
        Annotation ann = new Annotation(null, ft, iaClass);
        ann.setViewpoint(viewpoint);
        ma.addFeature(ft);
        ma.setDetectionStatus("complete");
        myShepherd.getPM().makePersistent(ft);
        myShepherd.getPM().makePersistent(ann);
/*
        believe this is overly complicated, but saving it from manualAnnotation.jsp logic
        if (enc != null) {
            if (IBEISIA.validForIdentification(ann, context) && iaConf.isValidIAClass(enc.getTaxonomy(myShepherd), iaClass)) {
                ann.setMatchAgainst(true);
            }
        }
 */
        // NOTE: manualAnnotation.jsp once allowed featureId to be passed; that functionality is not handled here
        if (enc != null) { // note: we currently *require* enc, so this should always be true
            ann.setMatchAgainst(true);
            // !NOTE! this first set of logic to set cloneEncounter is copied from manualAnnotation.jsp
            // i believe this logic is flawed! it is left for reference/research/consideration
            // please see instead the block following this where new logic is applied  -jon 2025-10-17
            boolean cloneEncounter = false;
            // we would expect at least a trivial annotation, so if annots>=2, we know we need to clone
            if ((annots.size() > 1) && (iaClass != null)) {

 succeeded in 304ms:
src/main/java/org/ecocean/Annotation.java:88:    private boolean matchAgainst = false;
src/main/java/org/ecocean/Annotation.java:216:        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
src/main/java/org/ecocean/Annotation.java:268:    // TODO should this also be limited by matchAgainst and acmId?
src/main/java/org/ecocean/Annotation.java:751:        return matchAgainst;
src/main/java/org/ecocean/Annotation.java:755:        matchAgainst = b;
src/main/java/org/ecocean/Annotation.java:956:        // returned every annotation with matchAgainst=T and an acmId
src/main/java/org/ecocean/Annotation.java:1001:        // matchAgainst true
src/main/java/org/ecocean/Annotation.java:1003:        arg.put("matchAgainst", true);
src/main/java/org/ecocean/identity/IBEISIA.java:1613:                        // that there is an animal in the image and set trivial annot to matchAgainst=true

 succeeded in 758ms:
src/main/java/org/ecocean/export/EncounterImageExportFile.java:49:                // if numAnnotationsPerId is 1, then the annotationIdx will be 1 as we write the second image
src/main/java/org/ecocean/StartupWildbook.java:544:     *   <li>MlServiceProcessor's Phase 4 idempotency check (composite of
src/main/java/org/ecocean/StartupWildbook.java:552:     *       advances naturally when MlServiceProcessor's Phase 1 calls
src/main/java/org/ecocean/StartupWildbook.java:670:            // MlServiceProcessor job will set processing-mlservice itself
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:64:        int annotationId = 1;
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:71:                annotationsArray.put(buildAnnotationObject(ann, annotationId++, imgId,
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:408:    private JSONObject buildAnnotationObject(Annotation ann, int annotationId, int imageId,
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:413:        annJson.put("id", annotationId);
src/main/java/org/ecocean/ia/Task.java:478:            j.put("annotationIds", jo);
src/main/java/org/ecocean/OpenSearch.java:477:    // can see every id in `ids` in `indexName`. Used by MlServiceProcessor
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:447:        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:458:            this.annotationId    = annotationId;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:569:        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:627:        if (known.contains(dto.annotationId) ||
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:647:            validateForcedResponse(dto.annotationId, rtn);
src/main/java/org/ecocean/identity/IBEISIA.java:2576:                "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
src/main/java/org/ecocean/identity/IBEISIA.java:2580:        JSONArray annIds = arg.optJSONArray("annotationIds");
src/main/java/org/ecocean/identity/IBEISIA.java:2583:                "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
src/main/java/org/ecocean/identity/IBEISIA.java:2659:                j.put("annotationIds", new JSONArray());
src/main/java/org/ecocean/identity/IBEISIA.java:2662:            map.get(name).getJSONArray("annotationIds").put(aid);
src/main/java/org/ecocean/ia/IAException.java:12:    // (e.g. MlServiceProcessor) can classify failures without parsing message
src/main/java/org/ecocean/ia/IA.java:162:        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
src/main/java/org/ecocean/ia/IA.java:229:     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
src/main/java/org/ecocean/ia/IA.java:238:     * If null, MlServiceProcessor persists annotations without explicit
src/main/java/org/ecocean/ia/IA.java:466:            aj.put("annotationIds", annArr);
src/main/java/org/ecocean/ia/IA.java:557:            JSONArray alist = jin.optJSONArray("annotationIds");
src/main/java/org/ecocean/ia/MLService.java:116:        data.put("annotationIds", annIds);
src/main/java/org/ecocean/ia/MLService.java:144:                ids = jobData.optJSONArray("annotationIds");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:33:public class MlServiceProcessor {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:34:    private static final String ACTION_PREFIX = "MlServiceProcessor.";
src/main/java/org/ecocean/ia/MlServiceProcessor.java:41:    public MlServiceProcessor(String context) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:45:    public MlServiceProcessor(String context, MlServiceClient client) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:67:        if (jobData.has("annotationId")) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:68:            String annId = jobData.optString("annotationId", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:69:            return processExtraction(jobData, taxonomyString, taskId, annId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:72:            "neither mediaAssetId nor annotationId in payload");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:112:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:115:    private MlServiceJobOutcome processExtraction(JSONObject jobData, String taxonomyString,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:129:            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:147:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:294:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:320:                    annotationIds.add(existing.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:362:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:368:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:382:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:397:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:398:                return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:406:            annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:408:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:424:                return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:428:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:437:        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:446:        if (annotationIds == null || annotationIds.isEmpty()) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:495:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:576:    private JSONObject inferMatchConfig(List<String> annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:577:        if (annotationIds == null || annotationIds.isEmpty()) return null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:582:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:685:        payload.put("annotationIds", new JSONArray(annotationIds));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:826:        final List<String> annotationIds;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:829:        private PersistResult(List<String> annotationIds, MlServiceJobOutcome outcome) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:830:            this.annotationIds = annotationIds;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:834:        static PersistResult ok(List<String> annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:835:            return new PersistResult(annotationIds, null);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:9: * {@code MlServiceProcessor.process(...)}. The seven {@link Kind} values
src/main/java/org/ecocean/ia/MlServiceClient.java:22: * org.ecocean.ia.MlServiceProcessor} (commit #9). Tests directly via
src/main/java/org/ecocean/ia/MlServiceClient.java:97:    public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
src/main/java/org/ecocean/media/MediaAsset.java:897:                    jf.put("annotationId", ann.getId());
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:261:                .extract()
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:532:                .extract()
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:699:                .extract()
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:986:                    .extract()
src/main/java/org/ecocean/Annotation.java:78:    // Contract: MlServiceProcessor MUST set this to false (not null) on
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:13: * v2 commit #9: pure-logic tests for MlServiceProcessor.
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:30:class MlServiceProcessorTest {
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:35:        MlServiceProcessor p = new MlServiceProcessor("context0");
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:42:        MlServiceProcessor p = new MlServiceProcessor("context0");
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
src/test/java/org/ecocean/ia/MlServiceClientTest.java:17: * Network round-trips are exercised end-to-end via MlServiceProcessor's
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:228:            response = client.extract(baseUrl(), image, bbox, theta, zebraConfig());
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:260:        JSONObject r1 = client.extract(baseUrl(), image, bbox, 0.0, zebraConfig());
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:261:        JSONObject r2 = client.extract(baseUrl(), image, bbox, 0.0, zebraConfig());
src/main/java/org/ecocean/servlet/IAGateway.java:133:                if (j.optJSONArray("annotationIds") != null) {
src/main/java/org/ecocean/servlet/IAGateway.java:294:        JSONArray alist = j.optJSONArray("annotationIds");
src/main/java/org/ecocean/servlet/IAGateway.java:375:                jobj.getJSONObject("identify").put("annotationIds", new JSONArray());
src/main/java/org/ecocean/servlet/IAGateway.java:376:                jobj.getJSONObject("identify").getJSONArray("annotationIds").put(ann.getId());
src/main/java/org/ecocean/servlet/IAGateway.java:410:        taskRes.put("annotationIds", jids);
src/main/java/org/ecocean/servlet/IAGateway.java:635:        // route here; MlServiceProcessor.process(jobj) handles routing
src/main/java/org/ecocean/servlet/IAGateway.java:639:            org.ecocean.ia.MlServiceProcessor processor =
src/main/java/org/ecocean/servlet/IAGateway.java:640:                new org.ecocean.ia.MlServiceProcessor(mlContext);
src/main/java/org/ecocean/servlet/IAGateway.java:796:                            // re-dispatched to MlServiceProcessor.
src/main/java/org/ecocean/servlet/importer/ImportTask.java:387:        Map<String, String> annIdToEncId = annotationIdsToEncounterIds(atm.keySet(),
src/main/java/org/ecocean/servlet/importer/ImportTask.java:478:    // Returns {annotationId -> encounter catalogNumber} via one SQL pass through
src/main/java/org/ecocean/servlet/importer/ImportTask.java:482:    private static Map<String, String> annotationIdsToEncounterIds(
src/main/java/org/ecocean/servlet/importer/ImportTask.java:511:                "[ERROR] ImportTask.annotationIdsToEncounterIds() failed: " + ex);

exec
/bin/bash -lc "rg -n \"getId\\(|getIdInt\\(\" src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 291ms:
158:    public int getIdInt() {
162:    public String getId() {
860:        jobj.put("id", this.getId());
882:                jf.put("id", ft.getId());
897:                    jf.put("annotationId", ann.getId());
997:        j.put("id", getIdInt());
1071:                ma.addDerivationMethod("detachedFrom", this.getId());
1219:            maIds.add(ma.getIdInt());
1230:        updateStandardChildrenBackground(context, this.getIdInt());
1450:        String s = IBEISIA.parseDetectionStatus(Integer.toString(this.getIdInt()), myShepherd);
1455:            s = IBEISIA.parseIdentificationStatus(ann.getId(), myShepherd);
1540:                    + this.getId());
1736:            jgen.writeStringField("id", enc.getId());
1744:            jgen.writeStringField("id", ann.getId());

exec
/bin/bash -lc "sed -n '150,165p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 252ms:

    private String getUrlString(final URL url) {
        if (url == null) {
            return null;
        }
        return url.toExternalForm();
    }

    public int getIdInt() {
        return id;
    }

    public String getId() {
        return String.valueOf(id);
    }


exec
/bin/bash -lc "rg -n \"static Task load|getTasksFor\\(\" src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
 succeeded in 251ms:
516:    public static Task load(String taskId, Shepherd myShepherd) {
526:    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
527:        return getTasksFor(ann, myShepherd, null);
531:    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd, String ordering) {
550:        return onlyRoots(getTasksFor(ann, myShepherd));
553:    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
568:        return onlyRoots(getTasksFor(ma, myShepherd));

exec
/bin/bash -lc "sed -n '516,566p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
 succeeded in 249ms:
    public static Task load(String taskId, Shepherd myShepherd) {
        Task t = null;

        try {
            t = ((Task)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(
                Task.class, taskId), true)));
        } catch (Exception ex) {}; // swallow jdo not found noise
        return t;
    }

    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
        return getTasksFor(ann, myShepherd, null);
    }

    // TODO: evaluate if we should support versions for multiple objects (when needed)
    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd, String ordering) {
        String qstr =
            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
            + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
        Query query = myShepherd.getPM().newQuery(qstr);

        query.setIgnoreCache(true);
        if (ordering == null) {
            query.setOrdering("created");
        } else {
            query.setOrdering(ordering);
        }
        Collection c = (Collection)query.execute();
        List<Task> listy = new ArrayList<Task>(c);
        query.closeAll();
        return listy;
    }

    public static List<Task> getRootTasksFor(Annotation ann, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ann, myShepherd));
    }

    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
        String qstr =
            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
            ma.getId() + " VARIABLES org.ecocean.media.MediaAsset obj";
        Query query = myShepherd.getPM().newQuery(qstr);

        query.setIgnoreCache(true);
        query.setOrdering("created");
        Collection c = (Collection)query.execute();
        List<Task> listy = new ArrayList<Task>(c);
        query.closeAll();
        return listy;
    }


exec
/bin/bash -lc "sed -n '145,190p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '410,440p' src/main/java/org/ecocean/MarkedIndividual.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '210,230p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '690,725p' src/main/java/org/ecocean/Util.java" in /mnt/c/Wildbook-clean2
 succeeded in 240ms:
        if (batchSize < 1) batchSize = BACKGROUND_BATCH_SIZE;
        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
        if (embData == null) embData = new JSONObject();
        // this will pick up where last left off, effectively
        // note: passing zero-uuid will effectively override to start over
        // TODO prevent duplicate runs by perhaps locking wity SystemValue like indexing
        if (startId == null) startId = embData.optString("_lastId", null);
        System.out.println("catchUpEmbeddings: beginning at " + startId + "; batch size=" +
            batchSize);

        String sql =
            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
        if (Util.isUUID(startId)) sql += " AND \"ANNOTATION\".\"ID\" > '" + startId + "'";
        sql += " order by \"ANNOTATION\".\"ID\" limit " + batchSize;
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        q.setClass(Annotation.class);
        Collection c = (Collection)q.execute();
        List<Annotation> anns = new ArrayList(c);
        q.closeAll();
        MLService mls = new MLService();
        String lastId = null;

 succeeded in 255ms:
        return names;
    }

    // NOTE:  this is a little wonky in that it incorporates SQL.  deal with it.
    // you can pass null for keyHint to get default only
    public static List<String> allNamesValues(Shepherd myShepherd, Object keyHint) {
        Set<String> keys = MultiValue.generateKeys(keyHint);
        List<String> rtn = new ArrayList<String>();

        if (keys.size() < 1) return rtn;
        List<String> keysList = new ArrayList<String>(keys); // we want a list to use .replaceAll
        keysList.replaceAll(s -> s.replaceAll("'", "''"));
        keysList.replaceAll(s -> s.replaceAll("_", "\\_"));
        keysList.replaceAll(s -> s.replaceAll(":", "_"));
        String sql =
            "SELECT DISTINCT(\"ID_OID\") AS \"ID\" FROM \"MULTIVALUE_VALUES\" JOIN \"MARKEDINDIVIDUAL\" ON (\"NAMES_ID_OID\" = \"ID_OID\") WHERE \"KEY\" LIKE '"
            + StringUtils.join(keysList, "' OR \"KEY\" LIKE '") + "'";
        System.out.println("MarkedIndividual.allNamesValues() sql->[" + sql + "]");
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        q.setClass(MultiValue.class);
        List<MultiValue> mvs = (List<MultiValue>)q.execute();
        for (MultiValue mv : mvs) {
            List<String> vals = mv.getValuesByKeys(keys);
            vals.removeAll(rtn); // weed out duplicates
            rtn.addAll(vals);
        }
        q.closeAll();
        return rtn;
    }

    public void addIncrementalProjectId(Project project) {

 succeeded in 283ms:

    // these shenanigans could be avoided if datanucleus supported vectors, but alas
    public PGvector loadVector(Shepherd myShepherd) {
        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
            this.id + "'";
        Query q = null;

        try {
            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
            List results = (List)q.execute();
            Iterator it = results.iterator();
            if (it.hasNext()) {
                PgArray parr = (PgArray)it.next();
                Float[] vf = (Float[])parr.getArray();
                // we need little-f so we gotta skip nulls, which we should never get from a db vector!
                float[] vecVals = new float[vf.length];
                for (int i = 0; i < vf.length; i++) {
                    vecVals[i] = ((vf[i] == null) ? 0f : vf[i]);
                }
                this.vector = new PGvector(vecVals);
            }
        } catch (Exception ex) {
            System.out.println("[ERROR] could not loadVector() on " + this.toString());
            ex.printStackTrace();
        } finally {
            if (q != null) q.closeAll();
        }
        return this.vector;
    }

    public void storeVector(Shepherd myShepherd) {
        if (vector == null) return;
        List<String> vals = new java.util.ArrayList<String>();
        for (float f : vector.toArray()) {
            vals.add(Float.toString(f));
        }
        String sql = "UPDATE \"EMBEDDING\" SET \"VECTOR\"='[" + String.join(",",
            vals) + "]' WHERE \"ID\"='" + this.id + "'";
        Query q = null;

        try {
            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
            q.execute();
        } catch (Exception ex) {
            System.out.println("[ERROR] could not storeVector() on " + this.toString());
            ex.printStackTrace();

 succeeded in 278ms:

    // see postgis/README.md for full details on these!  (including setup)
    public static JSONArray overlappingWaterGeometries(Shepherd myShepherd, Double lat, Double lon,
        Double radius) {
        if (!Util.isValidDecimalLatitude(lat) || !Util.isValidDecimalLongitude(lon)) return null;
        if ((radius == null) || (radius < 0)) radius = 200.0D; // this seems "close enough"... might be in meters?
        String sql =
            "SELECT ST_AsGeoJSON(ST_Transform(geom, 4326)) FROM overlappingWaterGeometries(" +
            lat.toString() + ", " + lon.toString() + ", " + radius.toString() + ")";
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        JSONArray rtn = new JSONArray();
        List results = (List)q.execute();
        Iterator it = results.iterator();
        while (it.hasNext()) {
            String js = (String)it.next();
            JSONObject geom = Util.stringToJSONObject(js);
            if (geom != null) rtn.put(geom);
        }
        q.closeAll();
        return rtn;
    }

    public static boolean nearWater(Shepherd myShepherd, Double lat, Double lon, Double radius) {
        if (!Util.isValidDecimalLatitude(lat) || !Util.isValidDecimalLongitude(lon)) return false;
        if ((radius == null) || (radius < 0)) radius = 200.0D;
        String sql = "SELECT nearWater(" + lat.toString() + ", " + lon.toString() + ", " +
            radius.toString() + ")";
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        List results = (List)q.execute();
        Iterator it = results.iterator();
        if (!it.hasNext()) return false;
        Boolean rtn = (Boolean)it.next();
        q.closeAll();
        return rtn;
    }


exec
/bin/bash -lc 'rg -n "getMethodValues" src/main/java/org/ecocean/ia/MLService.java src/main/java/org/ecocean/Embedding.java src/test/java/org/ecocean' in /mnt/c/Wildbook-clean2
 succeeded in 426ms:
src/main/java/org/ecocean/Embedding.java:352:                String[] mv = MLService.getMethodValues(iaConfig);
src/main/java/org/ecocean/ia/MLService.java:333:        String[] methodValues = getMethodValues(res);
src/main/java/org/ecocean/ia/MLService.java:348:    public static String[] getMethodValues(JSONObject conf) {
src/test/java/org/ecocean/ia/IdentificationTest.java:73:        String[] mv = MLService.getMethodValues(null);
src/test/java/org/ecocean/ia/IdentificationTest.java:80:        mv = MLService.getMethodValues(conf);
src/test/java/org/ecocean/ia/IdentificationTest.java:86:        mv = MLService.getMethodValues(conf);

exec
/bin/bash -lc "sed -n '340,370p' src/main/java/org/ecocean/ia/MLService.java" in /mnt/c/Wildbook-clean2
 succeeded in 260ms:
            return;
        }
        ann.addEmbedding(emb);
        // FIXME persist or whatever????
        System.out.println("[DEBUG] MLService.processAnnotationResults(): added " + emb + " to " +
            ann);
    }

    public static String[] getMethodValues(JSONObject conf) {
        String[] mv = { null, null };

        if (conf == null) return mv;
        mv[0] = conf.optString("model_id", null);
        // kinda hack version splitting here but... and i think some might not have dash, like "msv3"  :(
        if ((mv[0] != null) && mv[0].contains("-")) {
            String[] parts = mv[0].split("\\-");
            mv[0] = parts[0];
            mv[1] = parts[1];
        }
        return mv;
    }

    private JSONObject sendPayload(String endpoint, JSONObject payload)
    throws IAException {
        if (endpoint == null) throw new IAException("null api_endpoint");
        URL url = null;
        try {
            url = new URL(endpoint);
        } catch (MalformedURLException urlEx) {
            throw new IAException("api_endpoint url error: " + urlEx);
        }

exec
/bin/bash -lc "rg -n \"setAcmId\\(|getUUID\\(|uuid|acmId\" src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/acm/AcmUtil.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/ia/IA.java docs/plans/2026-05-09-ml-service-migration-v2.md" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '120,150p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,80p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '130,155p' src/main/java/org/ecocean/acm/AcmUtil.java" in /mnt/c/Wildbook-clean2
 succeeded in 212ms:
package org.ecocean.media;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.activation.MimeType;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.AccessControl;
import org.ecocean.Annotation;
import org.ecocean.Base;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.IAJsonProperties;
import org.ecocean.ImageAttributes;
import org.ecocean.Keyword;
import org.ecocean.LabeledKeyword;
import org.ecocean.Occurrence;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Taxonomy;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * MediaAsset describes a photo or video that can be displayed or used for processing and analysis.
 */
public class MediaAsset extends Base implements java.io.Serializable {
    static final long serialVersionUID = 8844223450447974780L;
    @Override public String opensearchIndexName() { return "media_asset"; }
    protected int id = MediaAssetFactory.NOT_SAVED;

    protected String uuid = null;

    protected AssetStore store;
    protected String parametersAsString;
    protected JSONObject parameters;

    protected Occurrence occurrence;

    protected Integer parentId;

    protected long revision;

    protected AccessControl accessControl = null;

    protected JSONObject derivationMethod = null;

    protected MediaAssetMetadata metadata = null;

    protected ArrayList<String> labels;

    protected ArrayList<Feature> features;

    protected ArrayList<Keyword> keywords;

    protected String hashCode;
    protected String contentHash; // see Util.fileContentHash()

    protected String detectionStatus;

 succeeded in 215ms:
    public AccessControl getAccessControl() {
        return accessControl;
    }

    public void setAccessControl(AccessControl ac) {
        accessControl = ac;
    }

    public void setAccessControl(HttpServletRequest request) {
        this.setAccessControl(new AccessControl(request));
    }

    public void setAcmId(String id) {
        this.acmId = id;
    }

    public String getAcmId() {
        return this.acmId;
    }

    public boolean hasAcmId() {
        return (null != this.acmId);
    }

    private URL getUrl(final AssetStore store, final Path path) {
        if (store == null) {
            return null;
        }
        return null; // store.webPath(path);
    }


 succeeded in 306ms:
docs/plans/2026-05-09-ml-service-migration-v2.md:53:  Encounter + MediaAsset persisted (MediaAsset.acmId = uuid; existing)
docs/plans/2026-05-09-ml-service-migration-v2.md:68:        │     - Annotation: acmId = id; matchAgainst = true;
docs/plans/2026-05-09-ml-service-migration-v2.md:224:For the partial unique index to actually constrain duplicates, `Annotation.MEDIAASSET_ID_OID` must be populated. The Wildbook context note that `MEDIAASSET_ID_OID` is "not populated in practice" applies to *legacy* annotations created before the Wildbook-owns-acmId era; v2 explicitly sets `ann.setMediaAsset(ma)` in MlServiceProcessor's persistence. For commit #4 to function correctly, every ml-service-created annotation has both `predictModelId` AND `MEDIAASSET_ID_OID` populated; legacy annotations remain unaffected by the partial unique constraint (filtered out by the `WHERE PREDICTMODELID IS NOT NULL` clause).
docs/plans/2026-05-09-ml-service-migration-v2.md:233:-- One-time backfill: legacy annotations with an acmId have been registered
src/main/java/org/ecocean/ia/MlServiceProcessor.java:331:                ann.setAcmId(ann.getId());
src/main/java/org/ecocean/media/MediaAsset.java:53:    protected String uuid = null;
src/main/java/org/ecocean/media/MediaAsset.java:92:    private String acmId;
src/main/java/org/ecocean/media/MediaAsset.java:117:        if (this.acmId == null) this.acmId = this.getUUID();
src/main/java/org/ecocean/media/MediaAsset.java:132:    public void setAcmId(String id) {
src/main/java/org/ecocean/media/MediaAsset.java:133:        this.acmId = id;
src/main/java/org/ecocean/media/MediaAsset.java:137:        return this.acmId;
src/main/java/org/ecocean/media/MediaAsset.java:141:        return (null != this.acmId);
src/main/java/org/ecocean/media/MediaAsset.java:234:    // this is for Annotation mostly?  provides are reproducible uuid based on the MediaAsset id
src/main/java/org/ecocean/media/MediaAsset.java:235:    public String getUUID() {
src/main/java/org/ecocean/media/MediaAsset.java:236:        if (uuid != null) return uuid;
src/main/java/org/ecocean/media/MediaAsset.java:242:        uuid = u;
src/main/java/org/ecocean/media/MediaAsset.java:247:       thus this essentially will almost always use a v4 uuid (random).  so be it! */
src/main/java/org/ecocean/media/MediaAsset.java:249:        uuid = this.generateUUIDFromId();
src/main/java/org/ecocean/media/MediaAsset.java:250:        if (uuid == null) uuid = Util.generateUUID();
src/main/java/org/ecocean/media/MediaAsset.java:696:            System.out.println("MediaAsset " + this.getUUID() + " has no store!");
src/main/java/org/ecocean/media/MediaAsset.java:861:        jobj.put("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:998:        j.put("uuid", getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1694:        if ((this.uuid == null) || (two == null) || (two.getUUID() == null)) return false;
src/main/java/org/ecocean/media/MediaAsset.java:1695:        return this.uuid.equals(two.getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1699:        if (uuid == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two users with no uuid equals! :/
src/main/java/org/ecocean/media/MediaAsset.java:1700:        return uuid.hashCode();
src/main/java/org/ecocean/media/MediaAsset.java:1712:        map.put("acmId", keywordType);
src/main/java/org/ecocean/media/MediaAsset.java:1713:        map.put("uuid", keywordType);
src/main/java/org/ecocean/media/MediaAsset.java:1726:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:1727:        jgen.writeStringField("uuid", this.getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1745:            jgen.writeStringField("acmId", ann.getAcmId());
src/main/java/org/ecocean/acm/AcmUtil.java:10:    // these take a list of objects and a parallel list of acmIds to assign
src/main/java/org/ecocean/acm/AcmUtil.java:13:    public static int rectifyMediaAssetIds(List<MediaAsset> mas, List<String> acmIds) {
src/main/java/org/ecocean/acm/AcmUtil.java:14:        if ((mas == null) || (acmIds == null) || (mas.size() != acmIds.size())) {
src/main/java/org/ecocean/acm/AcmUtil.java:24:            } else if (acmIds.get(i) == null) {
src/main/java/org/ecocean/acm/AcmUtil.java:25:                IA.log("INFO: AcmUtil.rectifyMediaAssetIds() has null acmId response for " +
src/main/java/org/ecocean/acm/AcmUtil.java:28:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:30:            } else if (!mas.get(i).getAcmId().equals(acmIds.get(i))) {
src/main/java/org/ecocean/acm/AcmUtil.java:31:                IA.log("ERROR: AcmUtil.rectifyMediaAssetIds() changing acmId from " +
src/main/java/org/ecocean/acm/AcmUtil.java:32:                    mas.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + mas.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:33:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:40:    public static int rectifyAnnotationIds(List<Annotation> anns, List<String> acmIds,
src/main/java/org/ecocean/acm/AcmUtil.java:42:        if ((anns == null) || (acmIds == null) || (anns.size() != acmIds.size())) {
src/main/java/org/ecocean/acm/AcmUtil.java:52:            } else if (acmIds.get(i) == null) {
src/main/java/org/ecocean/acm/AcmUtil.java:53:                IA.log("INFO: AcmUtil.rectifyAnnotationIds() has null acmId response for " +
src/main/java/org/ecocean/acm/AcmUtil.java:56:                anns.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:58:            } else if (!anns.get(i).getAcmId().equals(acmIds.get(i))) {
src/main/java/org/ecocean/acm/AcmUtil.java:59:                IA.log("WARNING: AcmUtil.rectifyAnnotationIds() changing acmId from " +
src/main/java/org/ecocean/acm/AcmUtil.java:60:                    anns.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + anns.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:61:                anns.get(i).setAcmId(acmIds.get(i));

 succeeded in 245ms:
exec
/bin/bash -lc "sed -n '108,122p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 275ms:

    public MediaAsset(final int id, final AssetStore store, final JSONObject params) {
        this.id = id;
        this.setUUID();
        this.store = store;
        this.parameters = params;
        if (params != null) this.parametersAsString = params.toString();
        this.setRevision();
        this.setHashCode();
        if (this.acmId == null) this.acmId = this.getUUID();
    }

    public AccessControl getAccessControl() {
        return accessControl;
    }

  ✓ Verify current branch and locate referenced code paths
  ✓ Check Track 1 assumptions against WildbookIAM/WBIA registration code
  → Check Track 2 assumptions against task, ml-service, OpenSearch, and embedding code
  • Identify blockers, majors, minors, and answer open questions
codex
A few implementation-sensitive issues are emerging rather than a rejection of the design: the gate idea is right, but the doc has a couple of places where following the pseudocode literally would either miss siblings or fail to delay deferred jobs. I’m doing one last pass on the concrete line references before summarizing.
exec
/bin/bash -lc "rg -n \"public int getIdInt|public String getId\\(|public String getStatus\\(|public void setStatus\\(|statusInEndState\" src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"waitAndRunMatch|runDeferredMatch|runMatchProspects|enqueueDeferredMatch|new OpenSearch\\(|matchConfig|IAGateway.addToDetectionQueue|IAGateway.requeueJob\" src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/servlet/IAGateway.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"if \\(method "'!= null'"\\)|if \\(methodVersion "'!= null'"\\)|getMethodValues\\(|waitForVisibility|buildIdEligibilityQuery|queryCount\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/OpenSearch.java src/main/java/org/ecocean/ia/MLService.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"mediaAssetToUri|iaImageIds\\(|iaAnnotationIdsStrict|invalidateByName|getQueryCache\" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/cache/QueryCache.java src/main/java/org/ecocean/cache/QueryCacheFactory.java" in /mnt/c/Wildbook-clean2
 succeeded in 233ms:
src/main/java/org/ecocean/ia/Task.java:60:    public String getId() {
src/main/java/org/ecocean/ia/Task.java:85:    public boolean statusInEndState() {
src/main/java/org/ecocean/ia/Task.java:597:    public String getStatus(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:600:        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
src/main/java/org/ecocean/ia/Task.java:699:    public void setStatus(String newStatus) {
src/main/java/org/ecocean/media/MediaAsset.java:158:    public int getIdInt() {
src/main/java/org/ecocean/media/MediaAsset.java:162:    public String getId() {

 succeeded in 237ms:
src/main/java/org/ecocean/servlet/IAGateway.java:570:        System.out.println("IAGateway.addToDetectionQueue() publishing: " + content);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:56:            return runDeferredMatch(jobData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:107:        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:110:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:133:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:143:        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:145:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:198:                configs.matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:233:            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:259:                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:393:            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:394:            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:419:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:421:            OpenSearch os = new OpenSearch();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:439:        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:445:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:452:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:473:            // a vector config or matchConfig is null. Don't leave the match
src/main/java/org/ecocean/ia/MlServiceProcessor.java:475:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:480:                    (matchConfig == null ? "null" : matchConfig.toString()));
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
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:693:            IAGateway.addToDetectionQueue(context, payload.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
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

 succeeded in 233ms:
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:521:        JSONObject query = buildIdEligibilityQuery(targetIds);
src/main/java/org/ecocean/OpenSearch.java:525:            int seen = queryCount(indexName, query);
src/main/java/org/ecocean/OpenSearch.java:541:    static JSONObject buildIdEligibilityQuery(Set<String> ids) {
src/main/java/org/ecocean/OpenSearch.java:552:    public int queryCount(String indexName, final JSONObject query)
src/main/java/org/ecocean/OpenSearch.java:563:            System.out.println("queryCount() on index " + indexName + " using query=" + query +
src/main/java/org/ecocean/OpenSearch.java:566:            throw new IOException("queryCount() failed");
src/main/java/org/ecocean/Embedding.java:324:            // NPE'ing on the queryCount/getMatchQuery below.
src/main/java/org/ecocean/Embedding.java:343:                numberCandidates = os.queryCount("annotation", matchingSetQuery);
src/main/java/org/ecocean/Embedding.java:352:                String[] mv = MLService.getMethodValues(iaConfig);
src/main/java/org/ecocean/ia/MLService.java:333:        String[] methodValues = getMethodValues(res);
src/main/java/org/ecocean/ia/MLService.java:348:    public static String[] getMethodValues(JSONObject conf) {
src/main/java/org/ecocean/Annotation.java:1205:        if (method != null)
src/main/java/org/ecocean/Annotation.java:1207:        if (methodVersion != null)

 succeeded in 238ms:
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:703:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +

exec
/bin/bash -lc "rg -n \"MAX_RETRIES|whileSleepMillis|Thread.sleep|__queueRetries|addToDetectionQueue\\(context\" src/main/java/org/ecocean/servlet/IAGateway.java" in /mnt/c/Wildbook-clean2
 succeeded in 296ms:
137:                        ok = addToDetectionQueue(context, j.toString());
144:                    ok = addToDetectionQueue(context, j.toString());
381:                jobj.put("__queueRetries", jin.optInt("__queueRetries", 0));
752:        int MAX_RETRIES = 30;
758:        int retries = jobj.optInt("__queueRetries", 0);
762:        if (elapsed > MAX_TIME_MILLIS) retries = MAX_RETRIES + 1; // waiting around too long
763:        if (retries > MAX_RETRIES) {
765:                MAX_RETRIES + " retries (or max time) in queue; giving up");
769:            retries + " out of " + MAX_RETRIES + " (actualRetries=" + actualRetries + "; start=" +
775:        jobj.put("__queueRetries", retries);
782:                long whileSleepMillis = 1000;
785:                        if (increment) whileSleepMillis = 30000;
788:                            Thread.sleep(whileSleepMillis);
797:                            addToDetectionQueue(context, jobj.toString());
803:                        whileSleepMillis = 30000;
916:        boolean ok = addToDetectionQueue(context, qjob.toString());

exec
/bin/bash -lc "sed -n '690,710p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 246ms:

    // instance version of below (since context is known)
    public List<String> iaAnnotationIds() {
        return iaAnnotationIds(this.context);
    }

    // this fails "gracefully" with empty list if network fubar.  bad decision?
    public static List<String> iaAnnotationIds(String context) {
        List<String> ids = new ArrayList<String>();
        JSONArray jids = null;
        String cacheName = "iaAnnotationIds";

        try {
            QueryCache qc = QueryCacheFactory.getQueryCache(context);
            if (qc.getQueryByName(cacheName) != null &&
                System.currentTimeMillis() <
                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
            } else {

codex
**Findings**

- **Blocker:** The deferred requeue design must explicitly call `IAGateway.requeueJob(payload, true)`. Setting `__queueRetries` alone does not create the 30s delay; `requeueJob` only switches to 30s sleep when `increment` is true ([IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785)). The current deferred enqueue path publishes immediately via `addToDetectionQueue` ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:681)). If implemented ambiguously, this can hot-loop and bypass the intended queue cap.

- **Blocker/Major:** The child-task lookup pseudocode compares `MediaAsset.getId()` with `==`. `getId()` returns `String` ([MediaAsset.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:162)), so this is reference equality. Use `getIdInt()` ([MediaAsset.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:158)) or `.equals()`. Otherwise the gate may fail to find the actual child task and wait until age-out.

- **Major:** The “caller IDs never block on their own visibility” claim is not true if caller IDs are passed through `waitForAnnotationMatchableIds`. That helper filters `matchAgainst`, `acmId`, and embedding metadata. A caller annotation that is visible by `_id` but not matchable will defer until age-out. Either split the wait into `_id` visibility for caller IDs plus matchable visibility for sibling eligible IDs, or explicitly classify non-matchable caller annotations as a validation/rejected outcome.

- **Major:** Phase 2 SQL must mirror `getMatchQuery` version handling. `Annotation.getMatchQuery` only adds `methodVersion` when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1207)); the SQL sketch’s unconditional `e."METHODVERSION" = ?` will drop null-version legacy embeddings. Also derive method/version the same way matching does, including the `MLService.getMethodValues` fallback ([Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:352)).

- **Major:** A raw `Task.status` accessor does not currently exist. The only public getter, `getStatus(Shepherd)`, can mutate timed-out tasks ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:597)). Add an explicit `getStoredStatus()`/`getRawStatus()` and make the gate use that.

- **Medium:** The testability seam is still incomplete. Injecting `MatchVisibilityGate` is useful, but `enqueueDeferredMatch` is private and directly calls queue/static infrastructure ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:681)). To assert DEFER behavior cleanly, inject a small deferred-match publisher or make a package-visible hook.

- **Medium:** The edge-case section still mentions `MAX_DEFER_ATTEMPTS` and ~50 minutes. That conflicts with the revised 12-minute elapsed cap. Clean this before locking the doc.

**Open Questions**

1. Put `safeInvalidate(context, name)` on `QueryCacheFactory`. The null-return behavior originates there ([QueryCacheFactory.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCacheFactory.java:6)), and it is generally reusable.

2. I would not keep the direct SQL private inside `MatchVisibilityGateImpl`. Use a small package-visible `MatchEligibilityQuery` in `org.ecocean.ia`, or at least a package-visible query builder plus executor. This query is schema-sensitive enough to deserve focused tests.

3. `topTask == null` degrading to caller-only visibility is fine, but log a WARN with `childTaskId`. It should be an abnormal degraded path, not silent normal behavior.

Overall, the two-track diagnosis and the topTask batch gate are right. I would lock the design after fixing the requeue wording, the child ID comparison, the caller-ID wait semantics, and the SQL methodVersion/null handling.
tokens used
161,865
**Findings**

- **Blocker:** The deferred requeue design must explicitly call `IAGateway.requeueJob(payload, true)`. Setting `__queueRetries` alone does not create the 30s delay; `requeueJob` only switches to 30s sleep when `increment` is true ([IAGateway.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785)). The current deferred enqueue path publishes immediately via `addToDetectionQueue` ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:681)). If implemented ambiguously, this can hot-loop and bypass the intended queue cap.

- **Blocker/Major:** The child-task lookup pseudocode compares `MediaAsset.getId()` with `==`. `getId()` returns `String` ([MediaAsset.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:162)), so this is reference equality. Use `getIdInt()` ([MediaAsset.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:158)) or `.equals()`. Otherwise the gate may fail to find the actual child task and wait until age-out.

- **Major:** The “caller IDs never block on their own visibility” claim is not true if caller IDs are passed through `waitForAnnotationMatchableIds`. That helper filters `matchAgainst`, `acmId`, and embedding metadata. A caller annotation that is visible by `_id` but not matchable will defer until age-out. Either split the wait into `_id` visibility for caller IDs plus matchable visibility for sibling eligible IDs, or explicitly classify non-matchable caller annotations as a validation/rejected outcome.

- **Major:** Phase 2 SQL must mirror `getMatchQuery` version handling. `Annotation.getMatchQuery` only adds `methodVersion` when non-null ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1207)); the SQL sketch’s unconditional `e."METHODVERSION" = ?` will drop null-version legacy embeddings. Also derive method/version the same way matching does, including the `MLService.getMethodValues` fallback ([Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:352)).

- **Major:** A raw `Task.status` accessor does not currently exist. The only public getter, `getStatus(Shepherd)`, can mutate timed-out tasks ([Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:597)). Add an explicit `getStoredStatus()`/`getRawStatus()` and make the gate use that.

- **Medium:** The testability seam is still incomplete. Injecting `MatchVisibilityGate` is useful, but `enqueueDeferredMatch` is private and directly calls queue/static infrastructure ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:681)). To assert DEFER behavior cleanly, inject a small deferred-match publisher or make a package-visible hook.

- **Medium:** The edge-case section still mentions `MAX_DEFER_ATTEMPTS` and ~50 minutes. That conflicts with the revised 12-minute elapsed cap. Clean this before locking the doc.

**Open Questions**

1. Put `safeInvalidate(context, name)` on `QueryCacheFactory`. The null-return behavior originates there ([QueryCacheFactory.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCacheFactory.java:6)), and it is generally reusable.

2. I would not keep the direct SQL private inside `MatchVisibilityGateImpl`. Use a small package-visible `MatchEligibilityQuery` in `org.ecocean.ia`, or at least a package-visible query builder plus executor. This query is schema-sensitive enough to deserve focused tests.

3. `topTask == null` degrading to caller-only visibility is fine, but log a WARN with `childTaskId`. It should be an abnormal degraded path, not silent normal behavior.

Overall, the two-track diagnosis and the topTask batch gate are right. I would lock the design after fixing the requeue wording, the child ID comparison, the caller-ID wait semantics, and the SQL methodVersion/null handling.
