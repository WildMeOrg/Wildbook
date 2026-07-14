# Design: fix empty match prospects on bulk imports (revision 6, LOCKED post-Codex round 5)

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

**v5 → v6 (LOCKED)** (post-Codex round 5; 1 blocker + 1 major
only, with Codex confirming all v4→v5 decisions and the core
Track 2 shape):

- **Deferred-match routing flags fixed** (Codex round-5 Blocker).
  Payload now includes `mlServiceV2: true` AND `deferredMatch:
  true` (the consumers used by `IAGateway.java:637` and
  `MlServiceProcessor.java:55`). `mlServiceV2DeferredMatch` stays
  as an extra diagnostic marker, not the routing contract.
- **`method == null` handled alongside `methodVersion == null`**
  (Codex round-5 Major). Both predicates conditionally omitted in
  Phase 2 SQL and in `waitForAnnotationMatchableIds`, matching
  `Annotation.getMatchQuery`'s independent strict-when-present
  behavior at `Annotation.java:1205-1209`.
- Codex round-5 explicitly confirmed: `safeInvalidate` placement
  on `QueryCacheFactory`, `MatchEligibilityQuery` extraction,
  topTask-null WARN, the two-wait split, the raw status accessor,
  and that the SQL join shape matches existing repo precedent at
  `ImportTask.java:781`. **Design locked.**

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

**`method`/`methodVersion` null handling** (Codex round-4 Major +
round-5 Major). Both fields are independently nullable for legacy
`api_endpoint`-only configs that `Embedding.findMatchProspects`
accepts as vector configs (`Embedding.java:269`), where
`MLService.getMethodValues` can return null in either slot
(`MLService.java:348`). `Annotation.getMatchQuery` omits each
predicate independently when its value is blank
(`Annotation.java:1205-1209`).

Phase 2's SQL builds predicates conditionally to match:

- If `method` is blank, OMIT `AND e."METHOD" = ?`.
- If `methodVersion` is blank, OMIT `AND e."METHODVERSION" = ?`.

`waitForAnnotationMatchableIds` does the same for the nested OS
clause — omit the corresponding `term` filter when the
respective value is blank. The implementation builds the SQL /
JSON conditionally rather than passing null parameters.

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
    // Routing flags: both are required to reach MlServiceProcessor's
    // deferred entry point. IAGateway dispatches v2 jobs only on
    // `mlServiceV2`; MlServiceProcessor branches into deferred mode
    // on `deferredMatch`. (`mlServiceV2DeferredMatch` is an
    // additional diagnostic marker, not the routing contract.)
    payload.put("mlServiceV2", true);
    payload.put("deferredMatch", true);
    payload.put("mlServiceV2DeferredMatch", true); // diagnostic marker
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
- `waitForMatchable_omitsMethodPredicate_whenMethodNull` —
  legacy `api_endpoint`-only path (Codex round-5 Major).
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
- `enqueueDeferredMatch_includesRoutingFlags` — captured payload
  has `mlServiceV2: true` AND `deferredMatch: true` (Codex
  round-5 Blocker). Without both, the requeued job is dropped on
  the floor.

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
