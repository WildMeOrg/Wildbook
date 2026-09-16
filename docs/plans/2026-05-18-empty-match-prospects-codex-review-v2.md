OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3c76-af98-76f0-8a86-41ef7b13c198
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

# Design: fix empty match prospects on bulk imports (revision 2, post-Codex)

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

## Revision 2 — what changed from v1

Codex review of v1 returned 2 blockers, 3 majors, 1 medium. Summary
of changes:

- **Track 2 reshaped into a two-phase gate** (was: single-phase
  visibility expansion). Phase 1 waits for sibling media-asset
  detection terminal states. Phase 2 collects eligible annotations
  and waits for OS visibility. v1 conflated the two and would have
  expanded to annotations that don't exist yet.
- **Batch boundary changed from ImportTask to the per-asset Task's
  parent** (`topTask` from `IA.enqueueOneAssetForMlService`,
  `IA.java:281-287`). ImportTask is mixed-species and the
  `ImportTask → Encounter → Annotation` join leaks placeholder
  annotations with `skipAutoIndexing=true`. topTask is one
  same-species MA group from one routing call — tighter and
  semantically correct.
- **Eligibility filter added to the annotation set** (was: no
  filter). Without `matchAgainst=true AND acmId IS NOT NULL AND
  has-embedding-for-method`, the expansion picks up bulk-import
  placeholder annotations that never reach OS.
- **`runDeferredMatch` reuses the same gate** with an attempt
  counter and max-age (was: bypassed visibility wait entirely).
- **Testability seam added**: extract a
  `MatchVisibilityGate` collaborator so unit tests can mock without
  live JDO/OpenSearch.
- **Track 1 cache-naming cleanup**: pick `iaImageIdsStrict`
  consistently across both docs; also invalidate `iaAnnotationIds`
  after successful annotation POST.

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

#### Cache-name fix (Codex Medium 6)

- The combined doc and WBIA doc both standardize on
  **`iaImageIdsStrict`** as the strict cache key.
- After successful image POST in Phase 0, call
  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaImageIdsStrict")`.
  This is the correct API at `QueryCache.java:46` (NOT `invalidate(name)`).
- Also invalidate `iaAnnotationIdsStrict` after successful
  annotation POST in Phase 1, to prevent stale-cache duplicate
  POSTs if Phase C races or fails between attempts.

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

#### Phase 1: MA terminal-state gate

For each sibling MA in `topTask.getObjectMediaAssets()`, check
`ma.getDetectionStatus()`:

| Status | Meaning | Terminal? |
|---|---|---|
| `_new` | created, not yet picked up | no |
| `_post_new` | post-new transition | no |
| `processing` | ml-service in flight | no |
| `complete` | detections persisted | **yes** |
| `error` | ml-service failed for this MA | **yes (no annotations to wait for)** |
| `pending` | needs human review | **yes (no annotations to wait for)** |

If any sibling MA is non-terminal, **defer**: re-queue via
`enqueueDeferredMatch` with an incremented attempt counter (see
deferred-path section below). Caller's per-annotation match does
NOT run yet.

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

#### Phase 3: OS visibility wait on the eligible set

`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.

If returns false (timeout), **defer**: re-queue with attempt
counter increment.

#### Phase 4: run match

`runMatchProspects(callerAnnotationIds, taskId, matchConfig)` —
**unchanged**. The caller's own annotation IDs drive the match;
the gate only ensures the OS corpus is populated.

#### Pseudocode

```java
private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
    String taskId, JSONObject matchConfig) {

    GateOutcome g = visibilityGate.gateForBatch(taskId, matchConfig);
    switch (g.kind) {
      case READY:
        return runMatchProspects(annotationIds, taskId, matchConfig);
      case DEFER:
        enqueueDeferredMatch(annotationIds, taskId, g.attempt);
        return MlServiceJobOutcome.ok(annotationIds);
      case GIVE_UP:
        // Max attempts reached; run match against whatever is visible
        // rather than block forever. This is a degraded outcome but
        // not a regression vs. today's behavior.
        return runMatchProspects(annotationIds, taskId, matchConfig);
    }
}
```

`MatchVisibilityGate.gateForBatch(taskId, matchConfig)` returns
`READY` if Phase 1 + 2 + 3 all succeed, `DEFER` if any phase says
"wait", `GIVE_UP` if attempt counter exceeds max.

#### Deferred-match path (`runDeferredMatch`)

Today at `MlServiceProcessor.java:433-442`,
`runDeferredMatch` reads `annotationIds` + `taskId` + `matchConfig`
from the job payload and calls `runMatchProspects` directly. We
change it to call the same gate:

```java
public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
    if (jobData == null) { ... }
    List<String> annotationIds = ...;
    String taskId = ...;
    JSONObject matchConfig = ...;
    int attempt = jobData.optInt("attempt", 1);
    if (attempt > MAX_DEFER_ATTEMPTS) {
        // age-out: run against whatever is visible
        return runMatchProspects(annotationIds, taskId, matchConfig);
    }
    // re-gate; deferred match earns the same protection as initial.
    return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, attempt);
}
```

`enqueueDeferredMatch` is extended to take an `attempt` parameter,
written into the job payload. New constants:

```java
private static final int MAX_DEFER_ATTEMPTS = 10;
// Deferred re-fire delay scales with attempt; first deferral ~30s,
// last ~5min, matching the WBIA register polling thread's cadence.
```

The age-out at `attempt > MAX_DEFER_ATTEMPTS` is a degraded outcome
(matches against whatever is visible) but bounded so a stuck
sibling cannot block the user's match indefinitely.

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
        public final int attempt;        // current attempt number
        public final String reason;      // for logs
        // factory methods: ready(), defer(attempt, reason), giveUp(reason)
    }

    GateOutcome gateForBatch(String childTaskId, JSONObject matchConfig);
    GateOutcome gateForBatch(String childTaskId, JSONObject matchConfig, int attempt);
}
```

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
`MlServiceProcessorTest`:

- `gate_returnsReady_whenAllSiblingMasAreTerminalAndOsVisible`
- `gate_returnsDefer_whenSiblingMaIsStillProcessing`
- `gate_returnsDefer_whenEligibleIdsNotYetInOs`
- `gate_returnsGiveUp_whenAttemptExceedsMax`
- `gate_excludesPlaceholderAnnotations_filtersByEmbeddingMethod` —
  set up an annotation with no EMBEDDING row, prove it's filtered
  out.
- `gate_excludesAnnotationsWithSkipAutoIndexing` — set up an
  annotation in `BulkImporter`'s placeholder state, prove it's
  filtered out (transitively via the embedding filter; this is a
  double-check that placeholders never have embeddings of our
  method).
- `gate_handlesOrphanChildTaskWithoutParent` — child task with no
  parent; gate degrades cleanly.
- `gate_unionsCallersIdsIntoVisibilitySet` — caller's annotation
  always present in the wait set even if eligibility filter
  excluded it.
- `processor_callsRunMatchProspects_onlyAfterGateReady`
- `processor_callsEnqueueDeferredMatch_onGateDefer_withAttemptCounter`
- `processor_runsMatchAnyway_onGateGiveUp`
- `runDeferredMatch_reGatesBeforeRunning`
- `runDeferredMatch_agesOutAtMaxAttempts`
- `enqueueDeferredMatch_writesAttemptCounterToJobPayload`

Plus one end-to-end harness against the live deployment:
import 4 same-species images, observe that each annotation's
`findMatchProspects` result includes the other 3 as candidates.

## Open questions for Codex (round 2)

1. **`MAX_DEFER_ATTEMPTS=10` reasonable**, given the WBIA register
   thread also uses 10? Should it scale with batch size (larger
   batches deserve more retries)?
2. **Age-out posture**: at GIVE_UP, do we run match anyway
   (current draft) or mark the match Task as error? Running-anyway
   matches the user-facing intent ("I'd rather see partial results
   than nothing"); error matches strict semantic correctness ("the
   intra-batch match was impossible to compute"). My instinct:
   run-anyway, log a WARN, but flag for Codex.
3. **MA terminal-state set**: is `pending` (needs review) really
   terminal for the purposes of this gate? An MA in `pending`
   contributed annotations (or didn't) — the visibility filter
   handles that. But should we WAIT for `pending` to be resolved
   by a human? My instinct: no, treat `pending` as terminal for
   the gate; matches today's behavior. Flag for Codex.
4. **Should the gate also wait for `skipAutoIndexing` to be off**
   on the eligible-ann set? The embedding-method filter should
   already exclude placeholders, but if a Phase 4 race exists
   where the user toggles `skipAutoIndexing` mid-import, we could
   end up waiting for nothing. My instinct: don't over-engineer,
   trust the embedding-method filter.
5. **JDOQL vs. direct SQL** for `findEligibleAnnotationIdsForBatch`:
   v2 has used JDOQL for portability. Is there a precedent in
   this codebase for direct SQL when the join goes through
   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
   query? My instinct: JDOQL with `containsValue` / `EXISTS`
   subquery. Flag if this gets unwieldy.
6. **Deferred-match attempt counter location**: write into the
   `jobData` payload, or onto the Task itself? Today the Task
   doesn't have a counter field; adding one is a JDO schema
   change. Job payload is simpler but ephemeral. My instinct:
   job payload (ephemeral is fine — the Task already records
   `mlServiceV2DeferredMatch: true`).
7. **Anything else** — design smells, sequencing bugs, test
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
I’ll treat this as review-only and only read local files. I’m going to verify the assumptions against the current branch first, especially the task boundary, match path, visibility helper, and the JDO relationship shape.
exec
/bin/bash -lc 'git status --short --branch' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '380,740p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '240,320p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '450,560p'" in /mnt/c/Wildbook-clean2
 succeeded in 251ms:
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

 succeeded in 256ms:
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

 succeeded in 318ms:
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

2026-05-18T19:01:38.507721Z ERROR codex_core::tools::router: error=write_stdin failed: stdin is closed for this session; rerun exec_command with tty=true to keep stdin open
 succeeded in 12194ms:
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
?? docs/plans/2026-05-18-empty-match-prospects-codex-review.md
?? docs/plans/2026-05-18-empty-match-prospects-design.md
?? logs/

codex
I’ve confirmed the branch and current match path: the existing code only waits on the caller annotation IDs, and `runDeferredMatch` bypasses that wait entirely. The worktree is very dirty already, so I’m keeping this strictly to reads and will not touch files.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "getParent|parentTask|objectMediaAssets|setObjectMediaAssets|getObjectMediaAssets|class Task" src/main/java/org/ecocean/ia/Task.java src/main/resources/org/ecocean/ia/package.jdo' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '880,980p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "STATUS_.*(PENDING|PROCESS|COMPLETE|ERROR|NEW)|STATUS_PENDING|STATUS_PROCESSING|STATUS_COMPLETE" src/main/java/org/ecocean/identity/IBEISIA.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
 succeeded in 261ms:
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

 succeeded in 284ms:
src/main/resources/org/ecocean/ia/package.jdo:62:		<field name="objectMediaAssets">
src/main/java/org/ecocean/ia/Task.java:28:public class Task implements java.io.Serializable {
src/main/java/org/ecocean/ia/Task.java:34:    private List<MediaAsset> objectMediaAssets = null;
src/main/java/org/ecocean/ia/Task.java:107:            MediaAsset ma = this.getObjectMediaAssets().get(0);
src/main/java/org/ecocean/ia/Task.java:152:        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
src/main/java/org/ecocean/ia/Task.java:176:    public void setObjectMediaAssets(List<MediaAsset> mas) {
src/main/java/org/ecocean/ia/Task.java:177:        objectMediaAssets = mas;
src/main/java/org/ecocean/ia/Task.java:184:    public List<MediaAsset> getObjectMediaAssets() {
src/main/java/org/ecocean/ia/Task.java:185:        return objectMediaAssets;
src/main/java/org/ecocean/ia/Task.java:193:        return Util.collectionSize(objectMediaAssets);
src/main/java/org/ecocean/ia/Task.java:203:        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
src/main/java/org/ecocean/ia/Task.java:204:        if (!objectMediaAssets.contains(ma)) {
src/main/java/org/ecocean/ia/Task.java:205:            objectMediaAssets.add(ma);
src/main/java/org/ecocean/ia/Task.java:230:        if (ma != null && objectMediaAssets != null && objectMediaAssets.contains(ma)) {
src/main/java/org/ecocean/ia/Task.java:231:            objectMediaAssets.remove(ma);
src/main/java/org/ecocean/ia/Task.java:243:        if (objectMediaAssets == null) return false;
src/main/java/org/ecocean/ia/Task.java:244:        return objectMediaAssets.contains(ma);
src/main/java/org/ecocean/ia/Task.java:275:    public Task getParent() {
src/main/java/org/ecocean/ia/Task.java:279:    public String getParentId() {
src/main/java/org/ecocean/ia/Task.java:357:        for (MediaAsset ma : this.getObjectMediaAssets()) {
src/main/java/org/ecocean/ia/Task.java:466:        if ((objectMediaAssets != null) && (objectMediaAssets.size() > 0)) {
src/main/java/org/ecocean/ia/Task.java:468:            for (MediaAsset ma : this.objectMediaAssets) {
src/main/java/org/ecocean/ia/Task.java:555:            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
src/main/java/org/ecocean/ia/Task.java:810:        rtn.put("parentTaskId", getParentId());

 succeeded in 270ms:
   880	
   881	    // default behavior is limited access
   882	    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request)
   883	    throws org.datanucleus.api.rest.orgjson.JSONException {
   884	        return this.sanitizeJson(request, false);
   885	    }
   886	
   887	    // returns only the MediaAsset sanitized JSON, because whenever UI queries our DB (regardless of class query), all they want in return are
   888	    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request,
   889	        boolean fullAccess)
   890	    throws org.datanucleus.api.rest.orgjson.JSONException {
   891	        org.datanucleus.api.rest.orgjson.JSONObject jobj;
   892	        if (this.getMediaAsset() != null) {
   893	            jobj = this.getMediaAsset().sanitizeJson(request,
   894	                new org.datanucleus.api.rest.orgjson.JSONObject(), fullAccess);
   895	        } else {
   896	            jobj = new org.datanucleus.api.rest.orgjson.JSONObject();
   897	        }
   898	        return jobj;
   899	    }
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

 succeeded in 304ms:
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
src/main/java/org/ecocean/identity/IBEISIA.java:73:    public static String STATUS_PENDING = "pending"; // pending review (needs action by user)
src/main/java/org/ecocean/identity/IBEISIA.java:74:    public static String STATUS_COMPLETE = "complete"; // process is done
src/main/java/org/ecocean/identity/IBEISIA.java:75:    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
src/main/java/org/ecocean/identity/IBEISIA.java:76:    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
src/main/java/org/ecocean/identity/IBEISIA.java:78:    public static String STATUS_PROCESSING = "processing"; // off at IA, awaiting results
src/main/java/org/ecocean/identity/IBEISIA.java:79:    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
src/main/java/org/ecocean/identity/IBEISIA.java:81:    public static String STATUS_ERROR = "error";
src/main/java/org/ecocean/identity/IBEISIA.java:433:                ma.setDetectionStatus(STATUS_ERROR); // is this wise?
src/main/java/org/ecocean/identity/IBEISIA.java:1666:                        asset.setDetectionStatus(STATUS_PENDING);
src/main/java/org/ecocean/identity/IBEISIA.java:1668:                        asset.setDetectionStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:1711:                    ann.setIdentificationStatus(STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/identity/IBEISIA.java:1856:                    anns.get(id).setIdentificationStatus(STATUS_PENDING);
src/main/java/org/ecocean/identity/IBEISIA.java:1861:                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:1867:                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:2221:        ma.setDetectionStatus(STATUS_COMPLETE); // kosher?

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '260,380p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '40,95p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '65,90p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '980,1060p'" in /mnt/c/Wildbook-clean2
 succeeded in 238ms:
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

 succeeded in 246ms:
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

 succeeded in 238ms:
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

 succeeded in 254ms:
    65	    private static final Map<String, String[]> speciesMap;
    66	    static {
    67	        speciesMap = new HashMap<String, String[]>();
    68	        speciesMap.put("zebra_plains", new String[] { "Equus", "quagga" });
    69	        speciesMap.put("zebra_grevys", new String[] { "Equus", "grevyi" });
    70	        speciesMap.put("whale shark", new String[] { "Rhincodon", "typus" });
    71	    }
    72	
    73	    public static String STATUS_PENDING = "pending"; // pending review (needs action by user)
    74	    public static String STATUS_COMPLETE = "complete"; // process is done
    75	    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
    76	    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
    77	    public static String STATUS_DROPPED_STALE = "dropped-stale"; // queued ml-service job's target deleted before run; terminal-drop without error
    78	    public static String STATUS_PROCESSING = "processing"; // off at IA, awaiting results
    79	    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
    80	    public static String STATUS_INITIATED = "initiated"; // initiated on our side but may or may not be processing on IA side
    81	    public static String STATUS_ERROR = "error";
    82	    public static final String IA_UNKNOWN_NAME = "____";
    83	
    84	    private static long TIMEOUT_DETECTION = 20 * 60 * 1000; // in milliseconds
    85	    private static String SERVICE_NAME = "IBEISIA";
    86	
    87	    private static AtomicBoolean iaPrimed = new AtomicBoolean(false);
    88	    private static HashMap<Integer, Boolean> alreadySentMA = new HashMap<Integer, Boolean>();
    89	    private static HashMap<String, Boolean> alreadySentAnn = new HashMap<String, Boolean>();
    90	    private static HashMap<String, String> identificationUserActiveTaskId = new HashMap<String,

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '220,380p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"setDetectionStatus\\(|getDetectionStatus\\(|_new|_post_new|pending-species|complete-mlservice|processing-mlservice\" src/main/java src/test/java | head -n 200" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "detectionStatus" src/main/java/org/ecocean/media/MediaAsset.java src/main/resources/org/ecocean/media/package.jdo src/main/resources/org/ecocean/package.jdo' in /mnt/c/Wildbook-clean2
 succeeded in 248ms:
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

 succeeded in 252ms:
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

 succeeded in 364ms:
src/main/java/org/ecocean/media/MediaAsset.java:80:    protected String detectionStatus;
src/main/java/org/ecocean/media/MediaAsset.java:214:        return this.detectionStatus;
src/main/java/org/ecocean/media/MediaAsset.java:218:        this.detectionStatus = status;
src/main/java/org/ecocean/media/MediaAsset.java:222:        // detectionStatus change" timestamp via REVISION.
src/main/java/org/ecocean/media/MediaAsset.java:862:        jobj.put("detectionStatus", this.getDetectionStatus());
src/main/java/org/ecocean/media/MediaAsset.java:1714:        map.put("detectionStatus", keywordType);
src/main/java/org/ecocean/media/MediaAsset.java:1730:        jgen.writeStringField("detectionStatus", this.getDetectionStatus());

 succeeded in 922ms:
src/test/java/org/ecocean/ImportTaskTest.java:77:        when(mockMA.getDetectionStatus()).thenReturn("complete");
src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java:37:        ma.setDetectionStatus("complete-mlservice");
src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java:41:        assertEquals("complete-mlservice", ma.getDetectionStatus());
src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java:48:        ma.setDetectionStatus("processing-mlservice");
src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java:51:        ma.setDetectionStatus("complete-mlservice");
src/test/java/org/ecocean/api/EncounterApiTest.java:204:                    when(mockMA.getDetectionStatus()).thenReturn("complete");
src/main/java/org/ecocean/StartupWildbook.java:213:        // in processing-mlservice forever. Once at startup, walk assets
src/main/java/org/ecocean/StartupWildbook.java:533:     * detects MediaAssets stuck in {@code processing-mlservice} past a
src/main/java/org/ecocean/StartupWildbook.java:543:     *       {@code processing-mlservice} and the reconciler skips.</li>
src/main/java/org/ecocean/StartupWildbook.java:591:                "detectionStatus == 'processing-mlservice' && revision < "
src/main/java/org/ecocean/StartupWildbook.java:628:                    ma.getDetectionStatus())) {
src/main/java/org/ecocean/StartupWildbook.java:643:                ma.setDetectionStatus(org.ecocean.identity.IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/StartupWildbook.java:660:            //     in processing-mlservice for next-startup retry.
src/main/java/org/ecocean/StartupWildbook.java:665:                    maId + "; leaving processing-mlservice intact for next-startup retry");
src/main/java/org/ecocean/StartupWildbook.java:670:            // MlServiceProcessor job will set processing-mlservice itself
src/main/java/org/ecocean/StartupWildbook.java:674:            // complete-mlservice before our commit lands, and writing
src/main/java/org/ecocean/identity/IBEISIA.java:75:    public static String STATUS_COMPLETE_MLSERVICE = "complete-mlservice"; // ml-service is done (e.g. embeddings added)
src/main/java/org/ecocean/identity/IBEISIA.java:76:    public static String STATUS_PENDING_SPECIES = "pending-species"; // upload without taxonomy / unconfigured species; awaits taxonomy PATCH
src/main/java/org/ecocean/identity/IBEISIA.java:79:    public static String STATUS_PROCESSING_MLSERVICE = "processing-mlservice"; // off at ml-service, awaiting results
src/main/java/org/ecocean/identity/IBEISIA.java:433:                ma.setDetectionStatus(STATUS_ERROR); // is this wise?
src/main/java/org/ecocean/identity/IBEISIA.java:1666:                        asset.setDetectionStatus(STATUS_PENDING);
src/main/java/org/ecocean/identity/IBEISIA.java:1668:                        asset.setDetectionStatus(STATUS_COMPLETE);
src/main/java/org/ecocean/identity/IBEISIA.java:2221:        ma.setDetectionStatus(STATUS_COMPLETE); // kosher?
src/main/java/org/ecocean/ia/Task.java:358:            String status = ma.getDetectionStatus();
src/main/java/org/ecocean/Encounter.java:4883:                                ma.getDetectionStatus());
src/main/java/org/ecocean/Encounter.java:5296:                ma.setDetectionStatus("_new");
src/main/java/org/ecocean/Encounter.java:5339:            if ("_new".equals(ma.getDetectionStatus())) {
src/main/java/org/ecocean/Encounter.java:5340:                // _post_new is meant to be temporary, as it
src/main/java/org/ecocean/Encounter.java:5342:                ma.setDetectionStatus("_post_new");
src/main/java/org/ecocean/Encounter.java:5363:        // we use _post_new state to determine what needs to go to IA
src/main/java/org/ecocean/Encounter.java:5366:            if ("_post_new".equals(ma.getDetectionStatus())) {
src/main/java/org/ecocean/Encounter.java:5493:                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/Encounter.java:5534:                    ma.setDetectionStatus(hasConfig ? IBEISIA.STATUS_INITIATED : IBEISIA.STATUS_COMPLETE);
src/main/java/org/ecocean/ia/IAUtils.java:20:                ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:171:                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:174:                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:231:                return ExtractionContext.done(MlServiceJobOutcome.stale("pending-species"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:278:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:365:            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:624:        String status = ma.getDetectionStatus();
src/main/java/org/ecocean/servlet/importer/ImportTask.java:662:            // ml-service migration v2 (commit #5): "complete-mlservice" is
src/main/java/org/ecocean/servlet/importer/ImportTask.java:666:            if ((ma.getDetectionStatus() != null) &&
src/main/java/org/ecocean/servlet/importer/ImportTask.java:667:                (ma.getDetectionStatus().equals("complete") ||
src/main/java/org/ecocean/servlet/importer/ImportTask.java:668:                ma.getDetectionStatus().equals("complete-mlservice") ||
src/main/java/org/ecocean/servlet/importer/ImportTask.java:669:                ma.getDetectionStatus().equals("pending"))) numDetectionComplete++;
src/main/java/org/ecocean/ia/MLService.java:276:        ma.setDetectionStatus("complete");
src/main/java/org/ecocean/media/MediaAsset.java:213:    public String getDetectionStatus() {
src/main/java/org/ecocean/media/MediaAsset.java:217:    public void setDetectionStatus(String status) {
src/main/java/org/ecocean/media/MediaAsset.java:862:        jobj.put("detectionStatus", this.getDetectionStatus());
src/main/java/org/ecocean/media/MediaAsset.java:1452:        if (s != null) this.setDetectionStatus(s);
src/main/java/org/ecocean/media/MediaAsset.java:1465:        j.put("status", getDetectionStatus());
src/main/java/org/ecocean/media/MediaAsset.java:1730:        jgen.writeStringField("detectionStatus", this.getDetectionStatus());
src/main/java/org/ecocean/servlet/EncounterForm.java:891:                            ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
src/main/java/org/ecocean/Annotation.java:1539:        ma.setDetectionStatus("complete");
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:113:            annIA.put("assetDetectionStatus", ma.getDetectionStatus());
src/main/java/org/ecocean/servlet/IAGateway.java:216:                    ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
src/main/java/org/ecocean/servlet/IAGateway.java:260:                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:111:	        crMa.setDetectionStatus(IBEISIA.STATUS_COMPLETE);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '1,240p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '520,690p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '620,690p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"new Task\\(|setObjectMediaAssets\\(|intakeMediaAssetsOneSpeciesMlService|route.*MlService|mlService\" src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/servlet/importer/StandardImport.java src/main/java/org/ecocean/api/bulk/BulkImporter.java | head -n 200" in /mnt/c/Wildbook-clean2
 succeeded in 281ms:
   620	            }
   621	            myShepherd.getPM().deletePersistent(itask);
   622	            // myShepherd.commitDBTransaction();
   623	        } catch (Exception ex) {
   624	            throw new IOException("general exception on ImportTask delete: " + ex);
   625	        }
   626	        Util.mark("ImportTask.deleteWithRelated(" + id + ") completed");
   627	    }
   628	
   629	    // this is hobbled together from some complex code in import.jsp
   630	    // some of this is only necessary to handle legacy (non-api) uploads
   631	    // may the gods have mercy on our soul
   632	    // FIXME this can be OUTRAGEOUSLY slow for tasks with 100s of annotations
   633	    // for the GET api for listing tasks we very likely want to move this
   634	    // to detailed=true so it is not called for every task -- but this currently
   635	    // messes up the status :(
   636	    public JSONObject iaSummaryJson(Shepherd myShepherd) {
   637	        int numDetectionComplete = 0;
   638	        int numAcmId = 0;
   639	        int numAllowedIA = 0;
   640	        int numAssets = 0;
   641	        int numAnnotations = 0;
   642	        boolean pipelineStarted = false;
   643	        Map<String, Integer> statsMA = this.statsMediaAssets();
   644	        JSONObject statsAnn = this.statsAnnotations(myShepherd);
   645	
   646	        if (this.getMediaAssets() != null)
   647	            numAssets = this.getMediaAssets().size();
   648	        for (MediaAsset ma : this.getMediaAssets()) {
   649	            numAnnotations += ma.numAnnotations();
   650	            if (ma.getAcmId() != null) numAcmId++;
   651	            // check if we can get validity off the image before the expensive check of hitting the AssetStore
   652	            if (ma.isValidImageForIA() != null) {
   653	                if (ma.isValidImageForIA().booleanValue()) numAllowedIA++;
   654	            } else if (ma.validateSourceImage()) {
   655	                numAllowedIA++;
   656	            }
   657	/*
   658	                if ((ma.isValidImageForIA() == null) || !ma.isValidImageForIA().booleanValue()) {
   659	                    invalidMediaAssets.add(asset);
   660	                }
   661	 */
   662	            // ml-service migration v2 (commit #5): "complete-mlservice" is
   663	            // terminal alongside "complete" and "pending" for the bulk-import
   664	            // detection-complete tally. Without this, an ml-service-routed
   665	            // bulk import never reports 100% detection complete in the UI.
   666	            if ((ma.getDetectionStatus() != null) &&
   667	                (ma.getDetectionStatus().equals("complete") ||
   668	                ma.getDetectionStatus().equals("complete-mlservice") ||
   669	                ma.getDetectionStatus().equals("pending"))) numDetectionComplete++;
   670	        }
   671	        JSONObject pj = new JSONObject();
   672	        pj.put("statsMediaAssets", statsMA);
   673	        pj.put("statsAnnotations", statsAnn);
   674	        pj.put("numberMediaAssets", numAssets);
   675	        pj.put("numberAnnotations", numAnnotations);
   676	        pj.put("numberMediaAssetACMIds", numAcmId);
   677	        pj.put("numberMediaAssetValidIA", numAllowedIA);
   678	        pj.put("detectionNumberComplete", numDetectionComplete);
   679	        // non-legacy flavor
   680	        if ((this.getIATask() != null) && this.iaTaskStarted()) {
   681	            pipelineStarted = true;
   682	            if (numDetectionComplete == numAllowedIA) {
   683	                pj.put("detectionPercent", 1.0);
   684	                pj.put("detectionStatus", "complete");
   685	            } else {
   686	                if (numAssets > 0)
   687	                    pj.put("detectionPercent",
   688	                        new Double(numDetectionComplete) / new Double(numAssets));
   689	                pj.put("detectionStatus", "sent");
   690	            }

 succeeded in 293ms:
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
   566	        System.out.println(
   567	            "STARTING: StartupWildbook.runStaleMlServiceReconciliation()");
   568	        long revisionCutoff = System.currentTimeMillis() - STALE_MLSERVICE_THRESHOLD_MS;
   569	        java.util.List<String> staleIds = fetchStaleMlServiceAssetIds(context, revisionCutoff);
   570	        if (staleIds.isEmpty()) {
   571	            System.out.println(
   572	                "[INFO] StaleMlServiceReconciliation: no stuck assets older than threshold");
   573	            return;
   574	        }
   575	        System.out.println("[INFO] StaleMlServiceReconciliation: " + staleIds.size() +
   576	            " stuck assets older than " + STALE_MLSERVICE_THRESHOLD_MS + "ms");
   577	        for (String maId : staleIds) {
   578	            reconcileOneStaleAsset(context, maId);
   579	        }
   580	    }
   581	
   582	    private static java.util.List<String> fetchStaleMlServiceAssetIds(String context,
   583	        long revisionCutoff) {
   584	        java.util.List<String> ids = new ArrayList<String>();
   585	        Shepherd shep = new Shepherd(context);
   586	        shep.setAction("StartupWildbook.StaleMlServiceReconciliation.fetch");
   587	        shep.beginDBTransaction();
   588	        try {
   589	            javax.jdo.Query q = shep.getPM().newQuery(
   590	                org.ecocean.media.MediaAsset.class,
   591	                "detectionStatus == 'processing-mlservice' && revision < "
   592	                + revisionCutoff);
   593	            // Oldest-first so the most-stuck assets get recovered before
   594	            // newer ones in the same batch.
   595	            q.setOrdering("revision ascending");
   596	            @SuppressWarnings("unchecked")
   597	            java.util.List<org.ecocean.media.MediaAsset> stale =
   598	                (java.util.List<org.ecocean.media.MediaAsset>) q.execute();
   599	            if (stale != null) {
   600	                for (org.ecocean.media.MediaAsset ma : stale) {
   601	                    ids.add(String.valueOf(ma.getId()));
   602	                }
   603	            }
   604	            q.closeAll();
   605	            shep.commitDBTransaction();
   606	        } catch (Exception ex) {
   607	            System.out.println(
   608	                "WARN: StaleMlServiceReconciliation fetch failed: " + ex);
   609	            shep.rollbackDBTransaction();
   610	        } finally {
   611	            shep.closeDBTransaction();
   612	        }
   613	        return ids;
   614	    }
   615	
   616	    private static void reconcileOneStaleAsset(String context, String maId) {
   617	        Shepherd shep = new Shepherd(context);
   618	        shep.setAction("StartupWildbook.StaleMlServiceReconciliation." + maId);
   619	        shep.beginDBTransaction();
   620	        try {
   621	            org.ecocean.media.MediaAsset ma = shep.getMediaAsset(maId);
   622	            if (ma == null) {
   623	                shep.commitDBTransaction();
   624	                return;
   625	            }
   626	            // Re-check: another worker may have progressed it since fetch.
   627	            if (!org.ecocean.identity.IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(
   628	                    ma.getDetectionStatus())) {
   629	                shep.commitDBTransaction();
   630	                return;
   631	            }
   632	            // Derive taxonomy.
   633	            java.util.List<org.ecocean.Taxonomy> taxies = ma.getTaxonomies(shep);
   634	            org.ecocean.Taxonomy taxy = null;
   635	            if (taxies != null && !taxies.isEmpty()) taxy = taxies.get(0);
   636	
   637	            org.ecocean.IAJsonProperties iac = org.ecocean.IAJsonProperties.iaConfig();
   638	            boolean stillVectorRouted = iac != null && taxy != null
   639	                && iac.getActiveMlServiceConfigs(taxy) != null;
   640	            if (!stillVectorRouted) {
   641	                // Species is no longer configured for ml-service (or no taxy).
   642	                // Flip to error so the operator sees it; don't re-enqueue.
   643	                ma.setDetectionStatus(org.ecocean.identity.IBEISIA.STATUS_ERROR);
   644	                System.out.println("[INFO] StaleMlServiceReconciliation: " + maId +
   645	                    " no longer vector-routed; marking error");
   646	                shep.commitDBTransaction();
   647	                return;
   648	            }
   649	            // Call the per-asset enqueue helper directly. This bypasses
   650	            // handleMissingAcmids (which would otherwise fire WBIA HTTP
   651	            // inside this Shepherd transaction) and passes null for
   652	            // topTask so the helper creates a root task internally. The
   653	            // reconciler doesn't need an aggregator parent.
   654	            //
   655	            // The helper internally calls storeNewTask, which commits the
   656	            // surrounding transaction. So when we get here, either:
   657	            //   - enqueue succeeded: the child Task + queue file are durable;
   658	            //   - enqueue failed: the child Task IS still persisted (orphan,
   659	            //     unreachable without a queued job) but the asset remains
   660	            //     in processing-mlservice for next-startup retry.
   661	            boolean enqueued = org.ecocean.ia.IA.enqueueOneAssetForMlService(
   662	                shep, ma, taxy, /* topTask */ null, context, /* baseUrl */ null);
   663	            if (!enqueued) {
   664	                System.out.println("WARN: StaleMlServiceReconciliation: enqueue FAILED for " +
   665	                    maId + "; leaving processing-mlservice intact for next-startup retry");
   666	                shep.rollbackDBTransaction();
   667	                return;
   668	            }
   669	            // No status update after a successful enqueue. The queued
   670	            // MlServiceProcessor job will set processing-mlservice itself
   671	            // (bumping REVISION) when its Phase 1 picks the work up. We
   672	            // intentionally do NOT mutate ma here: a fast queue consumer
   673	            // could already have advanced detectionStatus to
   674	            // complete-mlservice before our commit lands, and writing
   675	            // back from this stale managed instance would overwrite it.
   676	            //
   677	            // We still need a successful commit so the storeNewTask done
   678	            // inside the helper is finalized (it is already committed via
   679	            // updateDBTransaction, so this commit is essentially a no-op).
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

 succeeded in 384ms:
     1	/*
     2	    idea here is "simple": a gateway to all IA calls, mostly (now) cleaned up from identity/IBEISIA.java (guessing)
     3	
     4	    THIS IS A WORK-IN-PROGRESS
     5	
     6	    proposed key concepts:
     7	 * can handle multiple IA frameworks (not just historic-IBEIS)
     8	      - likely a base abstract class with a "isEnabled() / init()" concept
     9	      - classes would allow for instances of each IA framework?
    10	
    11	 * no idea how to handle crazy (and configurable!?) workflow!
    12	
    13	 * probably should "leverage" Queue stuff where applicable?
    14	      - possibly there is a NEED for both variations (as suggested by drew): an asynchronous (queued) and synchronous (not)
    15	
    16	 * simply entry point for: MediaAsset and Annotation???
    17	 */
    18	package org.ecocean.ia;
    19	
    20	import java.io.PrintWriter;
    21	import java.util.ArrayList;
    22	import java.util.Arrays;
    23	import java.util.HashMap;
    24	import java.util.Iterator;
    25	import java.util.List;
    26	import java.util.Map;
    27	import java.util.Properties;
    28	import javax.servlet.http.HttpServletRequest;
    29	import javax.servlet.http.HttpServletResponse;
    30	import org.ecocean.Annotation;
    31	import org.ecocean.CommonConfiguration;
    32	import org.ecocean.Embedding;
    33	import org.ecocean.Encounter;
    34	import org.ecocean.identity.IBEISIA;
    35	import org.ecocean.IAJsonProperties;
    36	import org.ecocean.media.MediaAsset;
    37	import org.ecocean.media.MediaAssetFactory;
    38	import org.ecocean.servlet.ServletUtilities;
    39	import org.ecocean.shepherd.core.Shepherd;
    40	import org.ecocean.shepherd.core.ShepherdProperties;
    41	import org.ecocean.Taxonomy;
    42	import org.ecocean.Util;
    43	import org.json.JSONArray;
    44	import org.json.JSONObject;
    45	
    46	public class IA {
    47	    private static final String PROP_FILE = "IA.properties";
    48	
    49	    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
    50	        - these should be treated as different in that an IA framework might batch together the list in some way (i.e. difference between sending as
    51	           list vs iterating over list with intake(each element)
    52	        - you only get one task ID for the list/group, is this a bad idea?
    53	     */
    54	    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
    55	        return intakeMediaAssets(myShepherd, new ArrayList<MediaAsset>(Arrays.asList(ma)));
    56	    }
    57	
    58	    // Annotations *may or may not* already be on an Encounter  #neverforget
    59	    public static Task intake(Shepherd myShepherd, Annotation ann) {
    60	        return intakeAnnotations(myShepherd, new ArrayList<Annotation>(Arrays.asList(ann)));
    61	    }
    62	
    63	/*  these have same erasure types so cant co-exist. :( another reason for a common baseclass.. sigh?
    64	    hence the overly-inclusive Object version below!
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
   106	
   107	        for (MediaAsset ma : mas) {
   108	            Taxonomy taxy = ma.getTaxonomy(myShepherd);
   109	            String scientificName = "null";
   110	            if (taxy != null && taxy.getScientificName() != null)
   111	                scientificName = taxy.getScientificName();
   112	            // System.out.println("     MA ID "+ma.getId()+" has taxy "+scientificName);
   113	            if (!assetsBySpecies.containsKey(scientificName))
   114	                assetsBySpecies.put(scientificName, new ArrayList<MediaAsset>());
   115	            assetsBySpecies.get(scientificName).add(ma);
   116	            // System.out.println("       Taxy size: "+assetsBySpecies.get(scientificName).size());
   117	        }
   118	        return new ArrayList<List<MediaAsset> >(assetsBySpecies.values());
   119	    }
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
   181	        Boolean[] sent = new Boolean[numDetectAlgos];
   182	        for (int i = 0; i < numDetectAlgos; i++) {
   183	            // task for this job (only create new (child) tasks if multiple detect algos)
   184	            Task task = (numDetectAlgos == 1) ? topTask : new Task();
   185	            task.setObjectMediaAssets(mas);
   186	            task.setParameters(topTask.getParameters());
   187	
   188	            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
   189	            task.addParameter("detectArgs", detectArgs);
   190	
   191	            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
   192	            task.addParameter("__detect_url", detectionUrl);
   193	
   194	            JSONObject qjob = new JSONObject();
   195	            qjob.put("detect", dj);
   196	            qjob.put("__detect_args", detectArgs);
   197	            qjob.put("__detect_url", detectionUrl);
   198	            // task is queued here
   199	            qjob.put("taskId", topTask.getId());
   200	            qjob.put("__context", context);
   201	            qjob.put("__baseUrl", baseUrl);
   202	            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
   203	            if (tweetAssetId != -1) {
   204	                qjob.put("tweetAssetId", tweetAssetId);
   205	            }
   206	            System.out.println(
   207	                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
   208	            sent[i] = false;
   209	            try {
   210	                // job is queued here
   211	                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
   212	                    qjob.toString());
   213	            } catch (java.io.IOException iox) {
   214	                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
   215	                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
   216	                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
   217	                    iox.toString());
   218	            }
   219	        }
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

 succeeded in 381ms:
src/main/java/org/ecocean/ia/IA.java:67:        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:72:        Task task = new Task();
src/main/java/org/ecocean/ia/IA.java:95:        if (parentTask == null) parentTask = new Task();
src/main/java/org/ecocean/ia/IA.java:148:        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:150:        topTask.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:172:            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
src/main/java/org/ecocean/ia/IA.java:184:            Task task = (numDetectAlgos == 1) ? topTask : new Task();
src/main/java/org/ecocean/ia/IA.java:185:            task.setObjectMediaAssets(mas);
src/main/java/org/ecocean/ia/IA.java:228:     * topTask; each emits a {@code mlServiceV2:true} payload to the
src/main/java/org/ecocean/ia/IA.java:242:    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
src/main/java/org/ecocean/ia/IA.java:250:        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
src/main/java/org/ecocean/ia/IA.java:259:     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
src/main/java/org/ecocean/ia/IA.java:283:        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
src/main/java/org/ecocean/ia/IA.java:286:        childTask.setObjectMediaAssets(singleton);
src/main/java/org/ecocean/ia/IA.java:303:        qjob.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/IA.java:374:        // Task topTask = (parentTask==null) ? new Task() : parentTask;
src/main/java/org/ecocean/ia/IA.java:383:        Task topTask = new Task();
src/main/java/org/ecocean/ia/IA.java:447:                    Task t = new Task();
src/main/java/org/ecocean/ia/IA.java:536:            if (topTask == null) topTask = new Task(taskId);
src/main/java/org/ecocean/Encounter.java:5497:                    parentTask = new Task();
src/main/java/org/ecocean/Encounter.java:5543:                    parentTask = new Task();

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '250,380p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "class Embedding|METHOD|METHODVERSION|embedding|annotation" src/main/resources/org/ecocean/package.jdo src/main/java/org/ecocean/Embedding.java | head -n 120' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '380,540p'" in /mnt/c/Wildbook-clean2
 succeeded in 261ms:
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

 succeeded in 264ms:
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

 succeeded in 269ms:
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

 succeeded in 294ms:
src/main/java/org/ecocean/Embedding.java:28:public class Embedding implements java.io.Serializable {
src/main/java/org/ecocean/Embedding.java:30:    private Annotation annotation;
src/main/java/org/ecocean/Embedding.java:37:    // for trying to query vectors of annots without embeddings
src/main/java/org/ecocean/Embedding.java:45:        this.annotation = ann;
src/main/java/org/ecocean/Embedding.java:65:        return annotation;
src/main/java/org/ecocean/Embedding.java:69:        this.annotation = ann;
src/main/java/org/ecocean/Embedding.java:248:                /// maybe set on embData when we have *no embeddings* but did not have exception??
src/main/java/org/ecocean/Embedding.java:282:            task.numberAnnotations() + " annots) has embedding match: " + iaConfig);
src/main/java/org/ecocean/Embedding.java:292:            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
src/main/java/org/ecocean/Embedding.java:308:                        " due to no suitable embeddings for " + iaConfig);
src/main/java/org/ecocean/Embedding.java:311:                        "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:343:                numberCandidates = os.queryCount("annotation", matchingSetQuery);
src/main/java/org/ecocean/Embedding.java:361:                    " due to no suitable embeddings for " + iaConfig);
src/main/java/org/ecocean/Embedding.java:364:                    "no suitable embeddings for getMatches()");
src/main/java/org/ecocean/Embedding.java:418:        if (annotation != null) st += " [Annotation " + annotation.getId() + "]";
src/main/resources/org/ecocean/package.jdo:422:      <field name="annotations" persistence-modifier="persistent" default-fetch-group="true">
src/main/resources/org/ecocean/package.jdo:593:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
src/main/resources/org/ecocean/package.jdo:617:	<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
src/main/resources/org/ecocean/package.jdo:1043:		     to support idempotency lookup of (annotation, method, methodVersion).
src/main/resources/org/ecocean/package.jdo:1046:		     archive/sql/ml_service_embedding_audit.sql). -->
src/main/resources/org/ecocean/package.jdo:1047:		<index name="EMBEDDING_ANN_METHOD_VER_IDX">
src/main/resources/org/ecocean/package.jdo:1049:			<column name="METHOD"/>
src/main/resources/org/ecocean/package.jdo:1050:			<column name="METHODVERSION"/>
src/main/resources/org/ecocean/package.jdo:1056:		<field name="annotation" dependent-element="false" >
src/main/resources/org/ecocean/package.jdo:1069:			<index name="EMBEDDING_METHOD_IDX" />
src/main/resources/org/ecocean/package.jdo:1074:			<index name="EMBEDDING_METHODVERSION_IDX" />

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '560,635p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '1028,1080p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/Feature.java | sed -n '1,160p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getMediaAsset\\(|__setMediaAsset|features|addFeature\\(|getAnnotations\\(|numAnnotations\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/media/Feature.java | head -n 240" in /mnt/c/Wildbook-clean2
 succeeded in 277ms:
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

 succeeded in 280ms:
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
   631	      </field>
   632	      <field name="samplingProtocol">
   633	        <column name="SAMPLINGPROTOCOL"/>
   634	      </field>
   635	      <field name="samplingEffort">

 succeeded in 297ms:
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

 succeeded in 304ms:
src/main/java/org/ecocean/media/Feature.java:28:    // this link back to the objs with .features that include us
src/main/java/org/ecocean/media/Feature.java:91:     * Explicitly sets the owning Annotation. The {@code Annotation.features}
src/main/java/org/ecocean/media/Feature.java:96:     * management. {@code MediaAsset.addFeature(f)} already sets
src/main/java/org/ecocean/media/Feature.java:104:    public MediaAsset getMediaAsset() {
src/main/java/org/ecocean/media/Feature.java:164:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/media/Feature.java:182:        if (this.getMediaAsset() != null)
src/main/java/org/ecocean/media/Feature.java:184:                this.getMediaAsset().sanitizeJson(request,
src/main/java/org/ecocean/Annotation.java:51:    private ArrayList<Feature> features;
src/main/java/org/ecocean/Annotation.java:129:        this.features = new ArrayList<Feature>();
src/main/java/org/ecocean/Annotation.java:130:        this.features.add(f);
src/main/java/org/ecocean/Annotation.java:136:        this.features = f;
src/main/java/org/ecocean/Annotation.java:143:        this.features = new ArrayList<Feature>();
src/main/java/org/ecocean/Annotation.java:144:        this.features.add(f);
src/main/java/org/ecocean/Annotation.java:151:        this.features = f;
src/main/java/org/ecocean/Annotation.java:217:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:307:        __getMediaAsset().addFeature(f);
src/main/java/org/ecocean/Annotation.java:308:        addFeature(f);
src/main/java/org/ecocean/Annotation.java:351:        return features;
src/main/java/org/ecocean/Annotation.java:355:        if (Util.collectionSize(features) < 1) return null;
src/main/java/org/ecocean/Annotation.java:356:        return features.get(0);
src/main/java/org/ecocean/Annotation.java:360:        features = f;
src/main/java/org/ecocean/Annotation.java:364:    public void addFeature(Feature f) {
src/main/java/org/ecocean/Annotation.java:365:        if (features == null) features = new ArrayList<Feature>();
src/main/java/org/ecocean/Annotation.java:366:        if (!features.contains(f)) features.add(f);
src/main/java/org/ecocean/Annotation.java:456:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:484:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:595:    public MediaAsset __getMediaAsset() {
src/main/java/org/ecocean/Annotation.java:606:     * code (use {@link #getMediaAsset()} when you want the proper
src/main/java/org/ecocean/Annotation.java:609:    public void __setMediaAsset(MediaAsset ma) {
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
src/main/java/org/ecocean/Annotation.java:875:            jobj.put("features", feats);
src/main/java/org/ecocean/Annotation.java:892:        if (this.getMediaAsset() != null) {
src/main/java/org/ecocean/Annotation.java:893:            jobj = this.getMediaAsset().sanitizeJson(request,
src/main/java/org/ecocean/Annotation.java:1439:        MediaAsset ma = this.getMediaAsset();
src/main/java/org/ecocean/Annotation.java:1525:        List<Annotation> annots = ma.getAnnotations(); // get before we add ours
src/main/java/org/ecocean/Annotation.java:1538:        ma.addFeature(ft);
src/main/java/org/ecocean/Annotation.java:1590:                List<Annotation> encAnnots = enc.getAnnotations(ma);
src/main/java/org/ecocean/Annotation.java:1608:                List<Annotation> encAnnots = enc.getAnnotations(ma);
src/main/java/org/ecocean/Annotation.java:1657:        for (Annotation a : ma.getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:73:    protected ArrayList<Feature> features;
src/main/java/org/ecocean/media/MediaAsset.java:436:        return features;
src/main/java/org/ecocean/media/MediaAsset.java:440:        features = f;
src/main/java/org/ecocean/media/MediaAsset.java:443:    public void addFeature(Feature f) {
src/main/java/org/ecocean/media/MediaAsset.java:444:        if (features == null) features = new ArrayList<Feature>();
src/main/java/org/ecocean/media/MediaAsset.java:445:        if (!features.contains(f)) {
src/main/java/org/ecocean/media/MediaAsset.java:446:            features.add(f);
src/main/java/org/ecocean/media/MediaAsset.java:454:        if (features == null) return;
src/main/java/org/ecocean/media/MediaAsset.java:456:        features.remove(f);
src/main/java/org/ecocean/media/MediaAsset.java:461:        if ((features == null) || (features.size() < 1)) return false;
src/main/java/org/ecocean/media/MediaAsset.java:462:        for (Feature f : features) {
src/main/java/org/ecocean/media/MediaAsset.java:595:    // note: default behavior will add this to the features on this MediaAsset -- can pass false to disable
src/main/java/org/ecocean/media/MediaAsset.java:603:        if (addToMediaAsset) this.addFeature(f);
src/main/java/org/ecocean/media/MediaAsset.java:617:        this.addFeature(f);
src/main/java/org/ecocean/media/MediaAsset.java:621:    public ArrayList<Annotation> getAnnotations() {
src/main/java/org/ecocean/media/MediaAsset.java:632:        return (getAnnotations().size() > 0);
src/main/java/org/ecocean/media/MediaAsset.java:635:    public int numAnnotations() {
src/main/java/org/ecocean/media/MediaAsset.java:636:        return getAnnotations().size();
src/main/java/org/ecocean/media/MediaAsset.java:642:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:650:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:658:        List<Annotation> ord = new ArrayList<Annotation>(this.getAnnotations());
src/main/java/org/ecocean/media/MediaAsset.java:674:        if (numAnnotations() < 1) return null;
src/main/java/org/ecocean/media/MediaAsset.java:676:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:918:            jobj.put("features", jarr);
src/main/java/org/ecocean/media/MediaAsset.java:1454:        for (Annotation ann : this.getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:1556:        List<Annotation> annots = this.getAnnotations();
src/main/java/org/ecocean/media/MediaAsset.java:1742:        for (Annotation ann : this.getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:1759:        return myShepherd.getMediaAsset(id);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '420,645p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '580,630p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1180,1265p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "getMatchQuery|getAnEmbedding|numberEmbeddings|addEmbedding|hasEmbedding" src/main/java/org/ecocean/Annotation.java' in /mnt/c/Wildbook-clean2
 succeeded in 279ms:
   580	                resp.optDouble("score", -1.0) + ") for " + this);
   581	            return viewpoint;
   582	        } catch (RuntimeException | IOException | java.security.NoSuchAlgorithmException |
   583	            java.security.InvalidKeyException ex) {
   584	            throw new IOException("setViewpointFromIA() on " + this + " failed: " + ex.toString());
   585	        }
   586	    }
   587	
   588	/*
   589	    //  response comes from ia thus: "response": [{"score": 0.9783339699109396, "species": "giraffe_reticulated", "viewpoint": "right"}] public static
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
   611	        this.setVersion();
   612	    }
   613	
   614	    public MediaAsset getMediaAsset() {
   615	        ArrayList<Feature> fts = getFeatures();
   616	
   617	        if ((fts == null) || (fts.size() < 1) || (fts.get(0) == null)) {
   618	            System.out.println("WARNING: annotation " + this.getId() +
   619	                " is featureless, falling back to deprecated __getMediaAsset().  please fix!");
   620	            return __getMediaAsset();
   621	        }
   622	        return fts.get(0).getMediaAsset();
   623	    }
   624	
   625	    public boolean hasMediaAsset() {
   626	        return (getMediaAsset() != null);
   627	    }
   628	
   629	    public MediaAsset getDerivedMediaAsset() {
   630	        return null;

 succeeded in 280ms:
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
   471	        if (this.getMetadata() == null) return null;
   472	        HashMap<String, String> orient = this.getMetadata().findRecurse(".*orient.*");
   473	        if (orient == null) return null;
   474	        for (String k : orient.keySet()) {
   475	            if (k.toLowerCase().contains("thumb")) continue; // we skip exif sections with "thumb" in them
   476	            if (orient.get(k).matches(".*90.*")) return orient.get(k);
   477	            if (orient.get(k).matches(".*270.*")) return orient.get(k);
   478	        }
   479	        return null;
   480	    }
   481	
   482	    public boolean isRotated90Or270() {
   483	        return (this.getRotationInfo() != null);
   484	    }
   485	
   486	    public Path localPath() {
   487	        if (store == null) return null;
   488	        return store.localPath(this);
   489	    }
   490	
   491	    public boolean cacheLocal()
   492	    throws Exception {
   493	        if (store == null) return false;
   494	        return store.cacheLocal(this, false);
   495	    }
   496	
   497	    public boolean cacheLocal(boolean force)
   498	    throws Exception {
   499	        if (store == null) return false;
   500	        return store.cacheLocal(this, force);
   501	    }
   502	
   503	    // indisputable attributes about the image (e.g. type, dimensions, colorspaces etc)
   504	    // this is (seemingly?) always derived from MediaAssetMetadata, so .. yeah. make sure that is set (see note by getMetadata() )
   505	    public ImageAttributes getImageAttributes() {
   506	        if ((metadata == null) || (metadata.getData() == null)) return null;
   507	        JSONObject attr = metadata.getData().optJSONObject("attributes");
   508	        if (attr == null) return null;
   509	        double w = attr.optDouble("width", -1);
   510	        double h = attr.optDouble("height", -1);
   511	        String type = attr.optString("contentType");
   512	        if ((w < 1) || (h < 1)) return null;
   513	        return new ImageAttributes(w, h, type);
   514	    }
   515	
   516	    public double getWidth() {
   517	        ImageAttributes iattr = getImageAttributes();
   518	
   519	        if (iattr == null) return 0;
   520	        return iattr.getWidth();
   521	    }
   522	
   523	    public double getHeight() {
   524	        ImageAttributes iattr = getImageAttributes();
   525	
   526	        if (iattr == null) return 0;
   527	        return iattr.getHeight();
   528	    }
   529	
   530	    public void addToMetadata(String key, String value) {
   531	        if (metadata == null) metadata = new MediaAssetMetadata();
   532	        metadata.addDatum(key, value);
   533	    }
   534	
   535	    /**
   536	       this function resolves (how???) various difference in "when" this image was taken.  it might use different metadata (in EXIF etc) and/or human-input
   537	       FOR NOW: we rely first on (a) metadata.attributes.dateTime (as iso8601 string), then (b) crawl metadata.exif for something date-y
   538	     */
   539	    public DateTime getDateTime() {
   540	        if (this.userDateTime != null) return this.userDateTime;
   541	        if (this.store != null) {
   542	            DateTime dt = this.store.getDateTime(this);
   543	            if (dt != null) return dt;
   544	        }
   545	        if (getMetadata() == null) return null;
   546	        String adt = getMetadata().getAttributes().optString("dateTime", null);
   547	        if (adt != null) return DateTime.parse(adt); // lets hope it is in iso8601 format like it should be!
   548	        // meh, gotta find it the hard way then...
   549	        return getMetadata().getDateTime();
   550	    }
   551	
   552	    public void setUserDateTime(DateTime dt) {
   553	        this.userDateTime = dt;
   554	    }
   555	
   556	    public DateTime getUserDateTime() {
   557	        return this.userDateTime;
   558	    }
   559	
   560	    /**
   561	       like getDateTime() this is considered "definitive" -- so it must resolve differences in metadata vs other (e.g. encounter etc) values
   562	     */
   563	    public Double getUserLatitude() {
   564	        return this.userLatitude;
   565	    }
   566	
   567	    public Double getLatitude() {
   568	        if (this.userLatitude != null) return this.userLatitude;
   569	        if (getMetadata() == null) return null;
   570	        double lat = getMetadata().getAttributes().optDouble("latitude");
   571	        if (!Double.isNaN(lat)) return lat;
   572	        return getMetadata().getLatitude();
   573	    }
   574	
   575	    public void setUserLatitude(Double lat) {
   576	        this.userLatitude = lat;
   577	    }
   578	
   579	    public Double getUserLongitude() {
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

 succeeded in 290ms:
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
  1236	        try {
  1237	            int pageSize = 10000;
  1238	            try {
  1239	                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
  1240	            } catch (Exception ex) {}
  1241	            os.deletePit("annotation");
  1242	            queryRes = os.queryPit("annotation", matchQuery, 0, pageSize, null, null);
  1243	            hitSize = queryRes.optJSONObject("hits").optJSONObject("total").optInt("value");
  1244	        } catch (Exception ex) {
  1245	            System.out.println("getMatches() exception: " + ex);
  1246	            ex.printStackTrace();
  1247	        }
  1248	        JSONArray hits = OpenSearch.getHits(queryRes);
  1249	        for (int i = 0; i < hits.length(); i++) {
  1250	            JSONObject hit = hits.optJSONObject(i);
  1251	            if (hit == null) continue;
  1252	            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
  1253	            if (ann != null) {
  1254	                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
  1255	                anns.add(ann);
  1256	            }
  1257	        }
  1258	        System.out.println("getMatches() results: hitSize=" + hitSize + "; hits length=" +
  1259	            hits.length() + "; anns size=" + anns.size() + "; " +
  1260	            (System.currentTimeMillis() - startTime) + "ms");
  1261	        return anns;
  1262	    }
  1263	
  1264	    /*
  1265	        sorta weird to have this in here, but it is inherently linked with getMatchingSetXXX() above ...

 succeeded in 355ms:
853:                   .append("numEmbed", numberEmbeddings())
1182:    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
1187:        return getMatchQuery(method, methodVersion,
1192:    public JSONObject getMatchQuery(String method, String methodVersion,
1225:                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
1834:        int rtn = numberEmbeddings();
2007:    public int numberEmbeddings() {
2023:    public Set<Embedding> addEmbedding(Embedding emb) {
2031:    public boolean hasEmbedding(Embedding emb) {
2038:    public Embedding getAnEmbedding() {
2048:        if (numberEmbeddings() < 1) return null;
2063:        if (numberEmbeddings() < 1) return null;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1990,2075p'" in /mnt/c/Wildbook-clean2
 succeeded in 284ms:
  1990	            if (ann.getBbox() == null) return null;
  1991	            int[] bBox = ann.getBbox();
  1992	            int x = bBox[0];
  1993	            int y = bBox[1];
  1994	            int width = bBox[2];
  1995	            int height = bBox[3];
  1996	            return new Rectangle(x, y, width, height);
  1997	        } catch (NumberFormatException nfe) {
  1998	            nfe.printStackTrace();
  1999	        }
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
  2031	    public boolean hasEmbedding(Embedding emb) {
  2032	        if (embeddings == null) return false;
  2033	        return embeddings.contains(emb);
  2034	    }
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

exec
/bin/bash -lc 'rg -n "getMethodValues|model_id|embedding_model" src/main/java/org/ecocean/ia/MLService.java src/main/java/org/ecocean/IAJsonProperties.java src/main/resources/bundles/IA.json' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '1,140p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/IAJsonProperties.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "method|version|api_endpoint|pipeline_root|model_id|predict_model_id" src/main/resources/bundles/IA.json | head -n 120' in /mnt/c/Wildbook-clean2
 succeeded in 304ms:
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

 succeeded in 344ms:
src/main/java/org/ecocean/ia/MLService.java:56:    // there can be multiple configs (differing model_id)
src/main/java/org/ecocean/ia/MLService.java:326:        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
src/main/java/org/ecocean/ia/MLService.java:333:        String[] methodValues = getMethodValues(res);
src/main/java/org/ecocean/ia/MLService.java:348:    public static String[] getMethodValues(JSONObject conf) {
src/main/java/org/ecocean/ia/MLService.java:352:        mv[0] = conf.optString("model_id", null);

 succeeded in 338ms:
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

 succeeded in 371ms:
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MLService.java | sed -n '320,365p'" in /mnt/c/Wildbook-clean2
 succeeded in 283ms:
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

exec
/bin/bash -lc 'rg -n "getActiveMlServiceConfigs|getIdentConfig|_id_conf|_mlservice_conf" src/main/java/org/ecocean/IAJsonProperties.java' in /mnt/c/Wildbook-clean2
 succeeded in 282ms:
252:        return identKey(taxy, iaClass) + "._id_conf";
255:    public JSONArray getIdentConfig(Taxonomy taxy) {
256:        return getIdentConfig(taxy, "_default");
259:    public JSONArray getIdentConfig(Taxonomy taxy, String iaClass) {
268:        // The implicit `_mlservice_conf` appending that used to live here was
270:        // contract: `_id_conf.default.pipeline_root` is the single decision
272:        // via `getActiveMlServiceConfigs(...)` only — never implicitly mixed
284:    // `_id_conf.default.pipeline_root == "vector"` AND its
285:    // `_mlservice_conf` array is populated. Both conditions are enforced
286:    // by `getActiveMlServiceConfigs`. A species with `_mlservice_conf` but
291:        return taxonomyKey(taxy) + "._mlservice_conf";
295:     * Returns the per-taxonomy `_mlservice_conf` JSONArray iff the species'
296:     * `_id_conf.default.pipeline_root == "vector"` AND the array is
300:    public JSONArray getActiveMlServiceConfigs(Taxonomy taxy) {
312:     * Returns the `pipeline_root` string of the `_id_conf` entry marked
315:     * default entry, or no `_id_conf` exists at all.
323:        JSONArray idConf = getIdentConfig(taxy, iaClass);
341:            JSONArray idConfigs = getIdentConfig(taxy, iaClass);
389:        JSONArray identConfig = getIdentConfig(taxy, iaClass);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/IAJsonProperties.java | sed -n '240,320p'" in /mnt/c/Wildbook-clean2
 succeeded in 282ms:
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

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MlServiceProcessorTest.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/OpenSearchVisibilityTest.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "DataNucleus|PersistenceManager|JDOPersistence|in-memory|H2|javax.jdo|newQuery|SQL" src/test/java src/test/resources pom.xml build.gradle* | head -n 200' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"newQuery\\(\\\"javax.jdo.query.SQL\\\"|newQuery\\(.*SQL|SELECT .* FROM\" src/main/java src/test/java | head -n 200" in /mnt/c/Wildbook-clean2
 succeeded in 299ms:
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

 succeeded in 318ms:
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

 succeeded in 443ms:
rg: build.gradle*: No such file or directory (os error 2)
pom.xml:18:    <javax.jdo.version>3.2.0-release</javax.jdo.version>
pom.xml:292:    JDO and DataNucleus dependencies -->
pom.xml:296:        <artifactId>javax.jdo</artifactId>
pom.xml:297:        <version>${javax.jdo.version}</version>
pom.xml:300:      <!-- Core DataNucleus -->
src/test/java/org/ecocean/MatchResultTest.java:21:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/MatchResultTest.java:22:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:22:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:23:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:38:import java.sql.SQLException;
src/test/java/org/ecocean/MediaAssetOpenSearchTest.java:54:        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/SettingTest.java:17:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/SettingTest.java:18:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/SettingTest.java:139:        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/OpenSearchTest.java:56:import java.sql.SQLException;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:24:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:25:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:38:import java.sql.SQLException;
src/test/java/org/ecocean/OccurrenceOpenSearchTest.java:53:        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/TaskMatchResults.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/TaskMatchResults.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/TaskMatchResults.java:49:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/TaskMatchResults.java:59:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/EncounterApiTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/EncounterApiTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/EncounterApiTest.java:48:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/EncounterApiTest.java:59:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/security/PermissionsTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/security/PermissionsTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/security/PermissionsTest.java:56:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/security/PermissionsTest.java:63:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/api/SettingApiTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/SettingApiTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/SettingApiTest.java:49:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/SettingApiTest.java:59:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/EmbeddingTest.java:87:        // unit test on a brand-new in-memory Task, we go through the
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:43:import org.testcontainers.containers.PostgreSQLContainer;
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:66: * Uses Testcontainers for PostgreSQL and OpenSearch, WireMock for external image mocking,
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:74:    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:106:        System.out.println("PostgreSQL started at: " + postgres.getJdbcUrl());
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:161:        // DataNucleus will auto-create tables on first access
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:174:        System.out.println("PostgreSQL container will be stopped automatically");
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:742:     * DataNucleus will auto-create schema based on entity annotations.
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:748:        // ShepherdPMF will read these and connect to our Testcontainers PostgreSQL
src/test/java/org/ecocean/api/EncounterExportImagesTest.java:1006:        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");
src/test/java/org/ecocean/ia/IdentificationTest.java:11:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/ia/IdentificationTest.java:39:        PersistenceManager mockPM = mock(PersistenceManager.class);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:57:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:58:    PersistenceManager mockPM = mock(PersistenceManager.class);
src/test/java/org/ecocean/api/bulk/BulkImagesTest.java:72:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:6:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:7:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:8:import javax.jdo.Transaction;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:24:    private PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:25:    private PersistenceManager mockPM;
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:29:        // Create mock PersistenceManager and stub critical nested methods
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:31:        mockPM = mock(PersistenceManager.class, RETURNS_DEEP_STUBS);
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:34:        // Create mock PersistenceManagerFactory and PM creation
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:35:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/shepherd/core/ShepherdTest.java:36:        when(mockPMF.getPersistenceManager()).thenReturn(mockPM);
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:53:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkApiPostTest.java:64:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:13:import javax.jdo.*;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:24:    private PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:25:    private PersistenceManager mockPM;
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:30:        // Create mock PersistenceManager and stub critical nested methods
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:33:        mockPM = mock(PersistenceManager.class, RETURNS_DEEP_STUBS);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:35:        when(mockPM.newQuery(anyString())).thenReturn(mockQuery);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:37:        // Create mock PersistenceManagerFactory and PM creation
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:38:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/shepherd/entity/IndividualTest.java:39:        when(mockPMF.getPersistenceManager()).thenReturn(mockPM);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:55:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:56:    PersistenceManager mockPM = mock(PersistenceManager.class);
src/test/java/org/ecocean/api/bulk/BulkApiOtherTest.java:67:        mockPMF = mock(PersistenceManagerFactory.class);
src/test/java/org/ecocean/servlet/importer/ImportTaskCountsTest.java:10:import javax.jdo.Query;
src/test/java/org/ecocean/servlet/importer/ImportTaskCountsTest.java:16: * The public methods (getAllEncounterCounts, etc.) compose a SQL query and
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:9:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:10:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:40:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/bulk/BulkGeneralTest.java:41:    PersistenceManager mockPM = mock(PersistenceManager.class);
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:3:import javax.jdo.PersistenceManager;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:4:import javax.jdo.PersistenceManagerFactory;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:53:    PersistenceManagerFactory mockPMF;
src/test/java/org/ecocean/api/patch/ApiPatchTest.java:66:        mockPMF = mock(PersistenceManagerFactory.class);

 succeeded in 924ms:
src/main/java/org/ecocean/Annotation.java:271:                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
src/main/java/org/ecocean/Util.java:697:            "SELECT ST_AsGeoJSON(ST_Transform(geom, 4326)) FROM overlappingWaterGeometries(" +
src/main/java/org/ecocean/Util.java:699:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Util.java:717:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/identity/IBEISIA.java:3744:            "SELECT \"ANNOTATION\".\"ACMID\" as acmId, \"ENCOUNTER\".\"GENUS\" as genus, \"ENCOUNTER\".\"SPECIFICEPITHET\" as specificEpithet FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION\".\"ID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"ANNOTATION\".\"ACMID\" IS NOT NULL;";
src/main/java/org/ecocean/identity/IBEISIA.java:3745:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/opendata/OBISSeamap.java:61:            "SELECT * FROM \"ENCOUNTER\" LEFT JOIN \"OCCURRENCE_ENCOUNTERS\" ON (\"ENCOUNTER\".\"CATALOGNUMBER\" = \"OCCURRENCE_ENCOUNTERS\".\"CATALOGNUMBER_EID\") WHERE \"OCCURRENCE_ENCOUNTERS\".\"OCCURRENCEID_OID\" IS NULL";
src/main/java/org/ecocean/opendata/OBISSeamap.java:62:        query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Embedding.java:148:        String sql = "SELECT CAST(\"VECTOR\" AS float4[]) FROM \"EMBEDDING\" WHERE \"ID\" = '" +
src/main/java/org/ecocean/Embedding.java:153:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Embedding.java:186:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Embedding.java:224:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/media/MediaAsset.java:1755:                "SELECT CAST(\"ID\" AS text), \"REVISION\" AS version FROM \"MEDIAASSET\" WHERE \"PARENTID\" IS NULL ORDER BY version";
src/main/java/org/ecocean/Occurrence.java:1551:                "SELECT \"OCCURRENCEID\", CAST(COALESCE(EXTRACT(EPOCH FROM CAST(\"MODIFIED\" AS TIMESTAMP))*1000,-1) AS BIGINT) AS version FROM \"OCCURRENCE\" ORDER BY version";
src/main/java/org/ecocean/Encounter.java:2530:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Encounter.java:4166:            "SELECT \"CATALOGNUMBER\", \"SUBMITTERID\" FROM \"ENCOUNTER\" WHERE \"SUBMITTERID\" IS NOT NULL AND \"SUBMITTERID\" != '' AND \"SUBMITTERID\" != 'N/A' AND \"SUBMITTERID\" != 'public'";
src/main/java/org/ecocean/Encounter.java:4169:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/Encounter.java:4711:                "SELECT \"CATALOGNUMBER\", CAST(COALESCE(EXTRACT(EPOCH FROM CAST(\"MODIFIED\" AS TIMESTAMP))*1000,-1) AS BIGINT) AS version FROM \"ENCOUNTER\" ORDER BY version";
src/main/java/org/ecocean/Base.java:262:        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:844:            "SELECT role FROM org.ecocean.social.Membership WHERE role != null");
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2791:        // String sql = "SELECT \"TASK\".\"ID\" FROM \"TASK\" "
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2792:        String sql = "SELECT \"ID\" FROM \"TASK\" " +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2800:        Query q = getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:3910:            Query q = pm.newQuery("SELECT min(year) FROM org.ecocean.Encounter where year > 0");
src/main/java/org/ecocean/shepherd/core/Shepherd.java:3920:            Query q = pm.newQuery("SELECT min(dwcDateAddedLong) FROM org.ecocean.Encounter");
src/main/java/org/ecocean/shepherd/core/Shepherd.java:3937:            Query q = pm.newQuery("SELECT max(year) FROM org.ecocean.Encounter");
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4001:                "SELECT distinct catalogNumber FROM org.ecocean.Encounter") +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4033:                "SELECT distinct analysis1.haplotype FROM org.ecocean.Encounter WHERE (" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4035:                "SELECT distinct individualID FROM org.ecocean.MarkedIndividual") +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4274:            "SELECT distinct occurrenceID FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individualID == \""
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4292:            "SELECT distinct enc2.locationID FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && encounters.contains(enc2) && enc.locationID == \""
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4577:        String filter = "SELECT DISTINCT catalogNumber FROM org.ecocean.Encounter";
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4588:        String filter = "SELECT DISTINCT username FROM org.ecocean.Role";
src/main/java/org/ecocean/MarkedIndividual.java:425:            "SELECT DISTINCT(\"ID_OID\") AS \"ID\" FROM \"MULTIVALUE_VALUES\" JOIN \"MARKEDINDIVIDUAL\" ON (\"NAMES_ID_OID\" = \"ID_OID\") WHERE \"KEY\" LIKE '"
src/main/java/org/ecocean/MarkedIndividual.java:428:        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/MarkedIndividual.java:2953:                "SELECT \"INDIVIDUALID\", \"VERSION\" AS version FROM \"MARKEDINDIVIDUAL\" ORDER BY version";
src/main/java/org/ecocean/MetricsBot.java:166:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.media.MediaAsset",
src/main/java/org/ecocean/MetricsBot.java:168:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Occurrence",
src/main/java/org/ecocean/MetricsBot.java:170:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Annotation",
src/main/java/org/ecocean/MetricsBot.java:203:                            "SELECT count(this) FROM org.ecocean.MarkedIndividual where encounters.contains(enc) && enc.specificEpithet == '"
src/main/java/org/ecocean/MetricsBot.java:212:                            "SELECT count(this) FROM org.ecocean.Encounter where specificEpithet == '" +
src/main/java/org/ecocean/MetricsBot.java:222:                "SELECT count(this) FROM org.ecocean.MarkedIndividual", "*",
src/main/java/org/ecocean/MetricsBot.java:227:            String encLabelTemp = buildGauge("SELECT count(this) FROM org.ecocean.Encounter", "*",
src/main/java/org/ecocean/MetricsBot.java:239:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter",
src/main/java/org/ecocean/MetricsBot.java:241:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.MarkedIndividual",
src/main/java/org/ecocean/MetricsBot.java:274:                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
src/main/java/org/ecocean/MetricsBot.java:280:                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
src/main/java/org/ecocean/MetricsBot.java:286:                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
src/main/java/org/ecocean/MetricsBot.java:293:                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
src/main/java/org/ecocean/MetricsBot.java:299:                "SELECT count(this) FROM org.ecocean.User WHERE username != null && lastLogin > 0",
src/main/java/org/ecocean/MetricsBot.java:308:                "SELECT count(this) FROM org.ecocean.User WHERE username != null && lastLogin > 0",
src/main/java/org/ecocean/MetricsBot.java:313:            String allContribLabel = buildGauge("SELECT count(this) FROM org.ecocean.User", "*",
src/main/java/org/ecocean/MetricsBot.java:319:                "SELECT count(this) FROM org.ecocean.User WHERE username == null", "public",
src/main/java/org/ecocean/MetricsBot.java:329:                "SELECT count(this) FROM org.ecocean.User WHERE username == null",
src/main/java/org/ecocean/MetricsBot.java:334:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter where submitterID == 'public'",
src/main/java/org/ecocean/MetricsBot.java:338:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter where submitterID == role.username && role.rolename=='researcher' VARIABLES org.ecocean.Role role",
src/main/java/org/ecocean/MetricsBot.java:342:            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter where submitterID == null || submitterID == 'public' || !(select distinct username from org.ecocean.Role where rolename=='researcher').contains(submitterID)",
src/main/java/org/ecocean/MetricsBot.java:465:            Query q = myShepherd.getPM().newQuery("SELECT count(this) FROM " + className);
src/main/java/org/ecocean/MetricsBot.java:487:        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task", "wildbook_tasks_total", "Number of machine learning tasks",
src/main/java/org/ecocean/MetricsBot.java:492:        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('ibeis.detection') > -1  && (children == null
src/main/java/org/ecocean/MetricsBot.java:498:        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 ||
src/main/java/org/ecocean/MetricsBot.java:507:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && created > "
src/main/java/org/ecocean/MetricsBot.java:513:            "SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('ibeis.detection') > -1  && (children == null || (children.contains(child) && child.parameters.indexOf('ibeis.detection') == -1)) && created > "
src/main/java/org/ecocean/MetricsBot.java:519:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && created > "
src/main/java/org/ecocean/MetricsBot.java:525:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && completionDateInMilliseconds > "
src/main/java/org/ecocean/MetricsBot.java:532:            "SELECT count(this) FROM org.ecocean.ia.Task where children == null && parameters.indexOf('\"sv_on\"')>-1",
src/main/java/org/ecocean/MetricsBot.java:535:            "SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('PieTwo')>-1",
src/main/java/org/ecocean/MetricsBot.java:538:            "SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('MiewId')>-1",
src/main/java/org/ecocean/MetricsBot.java:567:                "SELECT count(this) FROM org.ecocean.ia.Task where completionDateInMilliseconds > "
src/main/java/org/ecocean/MetricsBot.java:571:                "SELECT count(this) FROM org.ecocean.ia.Task where completionDateInMilliseconds > "
src/main/java/org/ecocean/MetricsBot.java:603:                "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) ";
src/main/java/org/ecocean/servlet/UserConsolidate.java:588:        String baseQueryString = "SELECT * FROM \"USERS\" WHERE ";
src/main/java/org/ecocean/servlet/UserConsolidate.java:622:            Query query = persistenceManager.newQuery("javax.jdo.query.SQL", combinedQuery);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:497:            "SELECT \"ID_EID\", \"CATALOGNUMBER_OID\" FROM \"ENCOUNTER_ANNOTATIONS\" WHERE \"ID_EID\" IN ("
src/main/java/org/ecocean/servlet/importer/ImportTask.java:501:            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:761:        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
src/main/java/org/ecocean/servlet/importer/ImportTask.java:762:            "SELECT \"ID_OID\", count(*) FROM \"IMPORTTASK_ENCOUNTERS\" GROUP BY \"ID_OID\"");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:768:        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
src/main/java/org/ecocean/servlet/importer/ImportTask.java:777:        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '180,240p'" in /mnt/c/Wildbook-clean2
 succeeded in 262ms:
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
   191	        } finally {
   192	            if (q != null) q.closeAll();
   193	        }
   194	    }
   195	 */
   196	
   197	    // TODO: (1) configurable?  (2) exceptions when vector length differs?
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
   208	    // returns final annot id
   209	    public static JSONObject catchUpEmbeddings(Shepherd myShepherd, String startId, int batchSize) {
   210	        if (batchSize < 1) batchSize = BACKGROUND_BATCH_SIZE;
   211	        JSONObject embData = SystemValue.getJSONObject(myShepherd, "EMBEDDING_CATCHUP");
   212	        if (embData == null) embData = new JSONObject();
   213	        // this will pick up where last left off, effectively
   214	        // note: passing zero-uuid will effectively override to start over
   215	        // TODO prevent duplicate runs by perhaps locking wity SystemValue like indexing
   216	        if (startId == null) startId = embData.optString("_lastId", null);
   217	        System.out.println("catchUpEmbeddings: beginning at " + startId + "; batch size=" +
   218	            batchSize);
   219	
   220	        String sql =
   221	            "select \"ANNOTATION\".\"ID\" as \"ID\" from \"ANNOTATION\" left join \"EMBEDDING\" on (\"ANNOTATION\".\"ID\" = \"ANNOTATION_ID\") where \"VECTORFLOATARRAY\" is null";
   222	        if (Util.isUUID(startId)) sql += " AND \"ANNOTATION\".\"ID\" > '" + startId + "'";
   223	        sql += " order by \"ANNOTATION\".\"ID\" limit " + batchSize;
   224	        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
   225	        q.setClass(Annotation.class);
   226	        Collection c = (Collection)q.execute();
   227	        List<Annotation> anns = new ArrayList(c);
   228	        q.closeAll();
   229	        MLService mls = new MLService();
   230	        String lastId = null;
   231	        int ct = 0;
   232	        int ok = 0;
   233	        List<String> runIds = new ArrayList<String>();
   234	        for (Annotation ann : anns) {
   235	            ct++;
   236	            System.out.println("catchUpEmbeddings: [" + ct + "]: " + ann);
   237	            lastId = ann.getId();
   238	            runIds.add(lastId);
   239	            String txStr = ann.getTaxonomyString(myShepherd);
   240	            if (txStr == null) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/bulk/BulkImporter.java | sed -n '660,735p'" in /mnt/c/Wildbook-clean2
 succeeded in 286ms:
   660	            it was decided we disallow setting of the Occurrence.taxonomies values via bulk import
   661	            case "Sighting.taxonomy0":
   662	            case "Taxonomy.commonName":
   663	            case "Taxonomy.scientificName":
   664	                System.out.println("[INFO] " + fieldName + " currently not implemented");
   665	                break;
   666	 */
   667	
   668	/*
   669	            unsure where these came from; possibly specific wildbooks?
   670	            //case "Survey.type":
   671	            //case "Sighting.numAdultMales":
   672	            //case "Sighting.numSubFemales":
   673	 */
   674	            default:
   675	                System.out.println("[INFO] processRow() ignored a field [" + fieldName +
   676	                    "] that was flagged valid");
   677	            }
   678	        }
   679	        // fields done
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
   731	            taskShepherd.closeDBTransaction();
   732	        }
   733	    }
   734	
   735	    public List<Encounter> getEncounters() {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '90,160p'" in /mnt/c/Wildbook-clean2
 succeeded in 245ms:
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

exec
/bin/bash -lc 'rg -n "skipAutoIndexing|setSkipAutoIndexing|opensearch|index" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/IndexingManager.java src/main/java/org/ecocean/OpenSearch.java src/main/java/org/ecocean/media/MediaAsset.java | head -n 200' in /mnt/c/Wildbook-clean2
 succeeded in 310ms:
src/main/java/org/ecocean/IndexingManager.java:17:	//The ExecutorService executes indexing jobs
src/main/java/org/ecocean/IndexingManager.java:20:	//The indexingQueue is a List of Strings that represent the UUIDs of Base class-implementing objects 
src/main/java/org/ecocean/IndexingManager.java:21:	//(Encounter, MarkedIndividual, Annotation, etc.) that need to be indexed or unindexed.
src/main/java/org/ecocean/IndexingManager.java:23:	//unnecessary, duplicate indexing jobs. The UUIDs of the objects being ndexed are removed 
src/main/java/org/ecocean/IndexingManager.java:25:    private List<String> indexingQueue = Collections.synchronizedList(new ArrayList<String>());
src/main/java/org/ecocean/IndexingManager.java:32:	    	String indexingNumAllowedThreads = props.getProperty("indexingNumAllowedThreads");
src/main/java/org/ecocean/IndexingManager.java:33:	    	if(indexingNumAllowedThreads!=null) {
src/main/java/org/ecocean/IndexingManager.java:34:	    		Integer allowThreads = Integer.getInteger(indexingNumAllowedThreads);
src/main/java/org/ecocean/IndexingManager.java:42:    //Returns the indexing queue List of Strings
src/main/java/org/ecocean/IndexingManager.java:43:    public List<String> getIndexingQueue() { return indexingQueue; }
src/main/java/org/ecocean/IndexingManager.java:46:     * Adds a Base object to the queue for indexing or unindexing
src/main/java/org/ecocean/IndexingManager.java:47:     * @Base base The Base-class implementing object to be indexed or unindexed
src/main/java/org/ecocean/IndexingManager.java:48:     * @boolean unindex Whether the object is to be indexed or unindexed.
src/main/java/org/ecocean/IndexingManager.java:50:    public void addIndexingQueueEntry(Base base, boolean unindex) {
src/main/java/org/ecocean/IndexingManager.java:53:    	if(!indexingQueue.contains(objectID)) {
src/main/java/org/ecocean/IndexingManager.java:54:    		indexingQueue.add(objectID);
src/main/java/org/ecocean/IndexingManager.java:64:                    	if(unindex) {base.opensearchUnindexDeep();}
src/main/java/org/ecocean/IndexingManager.java:65:                    	else{base.opensearchIndexDeep();}
src/main/java/org/ecocean/IndexingManager.java:75:                    //remove from indexing queue
src/main/java/org/ecocean/IndexingManager.java:76:                    if(indexingQueue.contains(objectID))indexingQueue.remove(objectID);
src/main/java/org/ecocean/IndexingManager.java:88:        if (indexingQueue.contains(objectID)) {
src/main/java/org/ecocean/IndexingManager.java:89:        	indexingQueue.remove(objectID);
src/main/java/org/ecocean/IndexingManager.java:93:    //Resets the indexing queue
src/main/java/org/ecocean/IndexingManager.java:95:    	indexingQueue = null;
src/main/java/org/ecocean/IndexingManager.java:96:    	indexingQueue = Collections.synchronizedList(new ArrayList<String>());
src/main/java/org/ecocean/Annotation.java:59:    // identifies an ml-service-created detection. The partial unique index
src/main/java/org/ecocean/Annotation.java:155:    @Override public String opensearchIndexName() { return "annotation"; }
src/main/java/org/ecocean/Annotation.java:166:    public JSONObject opensearchMapping() {
src/main/java/org/ecocean/Annotation.java:167:        JSONObject map = super.opensearchMapping();
src/main/java/org/ecocean/Annotation.java:198:        // https://docs.opensearch.org/docs/latest/vector-search/creating-vector-index/
src/main/java/org/ecocean/Annotation.java:209:    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
src/main/java/org/ecocean/Annotation.java:211:        super.opensearchDocumentSerializer(jgen, myShepherd);
src/main/java/org/ecocean/Annotation.java:227:            // per discussion on issue 874, including this in indexing, but not (yet) using in matchingSet
src/main/java/org/ecocean/Annotation.java:254:                // System.out.println("[INFO] indexing emb " + emb.getId() + " vector length " + ((vecFloat == null) ? "null" : vecFloat.length));
src/main/java/org/ecocean/Annotation.java:541:                                // gettin trixy.. wrap indexes around
src/main/java/org/ecocean/Annotation.java:602:     * partial unique index on (mediaAsset, predictModelId, bboxKey, thetaKey)
src/main/java/org/ecocean/Annotation.java:604:     * Bumps version for OpenSearch reindex pickup. Mirrors the
src/main/java/org/ecocean/Annotation.java:666:        return anns.indexOf(this);
src/main/java/org/ecocean/Annotation.java:967:                    // to handle allowing null viewpoint, opensearch query gets messy!
src/main/java/org/ecocean/Annotation.java:1125:        // this exludes the very noisy embeddings from opensearch results since we dont need it
src/main/java/org/ecocean/Annotation.java:1565:                (iaClass.indexOf("+") == -1)) {
src/main/java/org/ecocean/Annotation.java:1570:                if ((annot1.getIAClass() != null) && (annot1.getIAClass().indexOf("+") != -1)) {
src/main/java/org/ecocean/Annotation.java:1576:                (iaClass.indexOf("+") > -1)) {
src/main/java/org/ecocean/Annotation.java:1579:                if ((annot1.getIAClass() != null) && (annot1.getIAClass().indexOf("+") != -1)) {
src/main/java/org/ecocean/Annotation.java:1980:                if (iaClass0.indexOf("+") > -1 && iaClass1.indexOf("+") == -1) return true;
src/main/java/org/ecocean/Annotation.java:1981:                if (iaClass1.indexOf("+") > -1 && iaClass0.indexOf("+") == -1) return true;
src/main/java/org/ecocean/OpenSearch.java:28:import org.opensearch.client.json.jackson.JacksonJsonpMapper;
src/main/java/org/ecocean/OpenSearch.java:29:import org.opensearch.client.Request;
src/main/java/org/ecocean/OpenSearch.java:30:import org.opensearch.client.Response;
src/main/java/org/ecocean/OpenSearch.java:31:import org.opensearch.client.ResponseException;
src/main/java/org/ecocean/OpenSearch.java:32:import org.opensearch.client.RestClient;
src/main/java/org/ecocean/OpenSearch.java:33:import org.opensearch.client.transport.rest_client.RestClientTransport;
src/main/java/org/ecocean/OpenSearch.java:35:import org.opensearch.client.opensearch.core.IndexRequest;
src/main/java/org/ecocean/OpenSearch.java:36:import org.opensearch.client.opensearch.indices.CreateIndexRequest;
src/main/java/org/ecocean/OpenSearch.java:37:import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
src/main/java/org/ecocean/OpenSearch.java:38:import org.opensearch.client.opensearch.indices.IndexSettings;
src/main/java/org/ecocean/OpenSearch.java:39:import org.opensearch.client.opensearch.OpenSearchClient;
src/main/java/org/ecocean/OpenSearch.java:40:import org.opensearch.client.transport.OpenSearchTransport;
src/main/java/org/ecocean/OpenSearch.java:48:// https://opensearch.org/docs/latest/clients/java/
src/main/java/org/ecocean/OpenSearch.java:49:// https://github.com/opensearch-project/opensearch-java/blob/main/USER_GUIDE.md
src/main/java/org/ecocean/OpenSearch.java:59:    public static String INDEX_TIMESTAMP_PREFIX = "OpenSearch_index_timestamp_";
src/main/java/org/ecocean/OpenSearch.java:74:    static String ACTIVE_TYPE_FOREGROUND = "opensearch_indexing_foreground";
src/main/java/org/ecocean/OpenSearch.java:75:    static String ACTIVE_TYPE_BACKGROUND = "opensearch_indexing_background";
src/main/java/org/ecocean/OpenSearch.java:84:        // final HttpHost host = new HttpHost("http", "opensearch", 9200);
src/main/java/org/ecocean/OpenSearch.java:85:        final HttpHost host = new HttpHost("opensearch", 9200, "http");
src/main/java/org/ecocean/OpenSearch.java:135:    public static boolean isValidIndexName(String indexName) {
src/main/java/org/ecocean/OpenSearch.java:136:        return Arrays.asList(VALID_INDICES).contains(indexName);
src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
src/main/java/org/ecocean/OpenSearch.java:180:            Encounter.opensearchIndexPermissionsBackground(myShepherd);
src/main/java/org/ecocean/OpenSearch.java:196:            System.out.println("OpenSearch background indexing running...");
src/main/java/org/ecocean/OpenSearch.java:197:            Base.opensearchSyncIndex(myShepherd, Encounter.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:198:            Base.opensearchSyncIndex(myShepherd, Annotation.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:199:            Base.opensearchSyncIndex(myShepherd, MarkedIndividual.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:200:            Base.opensearchSyncIndex(myShepherd, Occurrence.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:201:            Base.opensearchSyncIndex(myShepherd, MediaAsset.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:202:            System.out.println("OpenSearch background indexing finished.");
src/main/java/org/ecocean/OpenSearch.java:211:    public void createIndex(String indexName, JSONObject mapping)
src/main/java/org/ecocean/OpenSearch.java:213:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:214:        IndexSettings indexSettings = null;
src/main/java/org/ecocean/OpenSearch.java:216:        if (indexName.equals("annotation")) {
src/main/java/org/ecocean/OpenSearch.java:218:            indexSettings = IndexSettings.of(is -> is.knn(true));
src/main/java/org/ecocean/OpenSearch.java:220:        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
src/main/java/org/ecocean/OpenSearch.java:221:            indexName).settings(indexSettings).build();
src/main/java/org/ecocean/OpenSearch.java:224:        // TODO fold in this settings-change into indexSettings above
src/main/java/org/ecocean/OpenSearch.java:225:        indexClose(indexName);
src/main/java/org/ecocean/OpenSearch.java:228:        putSettings(indexName, analysis);
src/main/java/org/ecocean/OpenSearch.java:229:        createMapping(indexName, mapping);
src/main/java/org/ecocean/OpenSearch.java:230:        indexOpen(indexName);
src/main/java/org/ecocean/OpenSearch.java:231:        INDEX_EXISTS_CACHE.put(indexName, true);
src/main/java/org/ecocean/OpenSearch.java:232:        System.out.println(indexName + " OpenSearch index created");
src/main/java/org/ecocean/OpenSearch.java:235:    public void ensureIndex(String indexName, JSONObject mapping)
src/main/java/org/ecocean/OpenSearch.java:237:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:238:        if (existsIndex(indexName)) return;
src/main/java/org/ecocean/OpenSearch.java:239:        createIndex(indexName, mapping);
src/main/java/org/ecocean/OpenSearch.java:242:    public void deleteIndex(String indexName)
src/main/java/org/ecocean/OpenSearch.java:244:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:245:        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index(
src/main/java/org/ecocean/OpenSearch.java:246:            indexName).build();
src/main/java/org/ecocean/OpenSearch.java:250:        INDEX_EXISTS_CACHE.remove(indexName);
src/main/java/org/ecocean/OpenSearch.java:251:        System.out.println(indexName + " OpenSearch index deleted");
src/main/java/org/ecocean/OpenSearch.java:254:    public boolean existsIndex(String indexName) {
src/main/java/org/ecocean/OpenSearch.java:255:        if (!isValidIndexName(indexName)) return false;
src/main/java/org/ecocean/OpenSearch.java:256:        if (INDEX_EXISTS_CACHE.get(indexName) != null) return true;
src/main/java/org/ecocean/OpenSearch.java:258:            client.indices().get(i -> i.index(indexName));
src/main/java/org/ecocean/OpenSearch.java:259:            INDEX_EXISTS_CACHE.put(indexName, true);
src/main/java/org/ecocean/OpenSearch.java:262:            System.out.println("existsIndex(" + indexName + "): " + ex.toString());
src/main/java/org/ecocean/OpenSearch.java:267:    public void index(String indexName, Base obj)
src/main/java/org/ecocean/OpenSearch.java:269:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:272:            throw new RuntimeException("must have id property to index: " + obj);
src/main/java/org/ecocean/OpenSearch.java:273:        ensureIndex(indexName, obj.opensearchMapping());
src/main/java/org/ecocean/OpenSearch.java:274:        IndexRequest<Base> indexRequest = new IndexRequest.Builder<Base>()
src/main/java/org/ecocean/OpenSearch.java:275:                .index(indexName)
src/main/java/org/ecocean/OpenSearch.java:279:        client.index(indexRequest);
src/main/java/org/ecocean/OpenSearch.java:281:        IndexResponse indexResponse = client.index(indexRequest);
src/main/java/org/ecocean/OpenSearch.java:283:            indexResponse.result().toString().toLowerCase()));
src/main/java/org/ecocean/OpenSearch.java:288:    a mapping cannot be changed after data has been indexed, so we allow mapping to be made
src/main/java/org/ecocean/OpenSearch.java:289:    only right after index is created. any properties we do not define will be autoset upon first document creation.
src/main/java/org/ecocean/OpenSearch.java:290:    https://opensearch.org/docs/latest/api-reference/index-apis/put-mapping/
src/main/java/org/ecocean/OpenSearch.java:292:    private JSONObject createMapping(String indexName, final JSONObject mapProperties)
src/main/java/org/ecocean/OpenSearch.java:294:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:295:        if (!existsIndex(indexName)) throw new IOException("non-existent index: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:299:        Request req = new Request("PUT", indexName + "/_mapping");
src/main/java/org/ecocean/OpenSearch.java:302:        System.out.println("createMapping(" + indexName + "): " + set + " => " + rtn);
src/main/java/org/ecocean/OpenSearch.java:307:    // https://github.com/opensearch-project/opensearch-java/issues/824
src/main/java/org/ecocean/OpenSearch.java:308:    // https://forum.opensearch.org/t/how-can-i-create-a-simple-match-query-using-java-client/7748/2
src/main/java/org/ecocean/OpenSearch.java:309:    // https://forum.opensearch.org/t/java-client-searchrequest-query-building-for-neural-plugin/15895/4
src/main/java/org/ecocean/OpenSearch.java:310:    public List<Base> queryx(String indexName, String query)
src/main/java/org/ecocean/OpenSearch.java:314:                .index(indexName)
src/main/java/org/ecocean/OpenSearch.java:329:        SearchResponse<Base> searchResponse = client.search(s -> s.index(indexName), Base.class);
src/main/java/org/ecocean/OpenSearch.java:339:    // https://opensearch.org/docs/latest/search-plugins/searching-data/point-in-time-api/
src/main/java/org/ecocean/OpenSearch.java:340:    public String createPit(String indexName)
src/main/java/org/ecocean/OpenSearch.java:342:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:343:        if (PIT_CACHE.containsKey(indexName)) return PIT_CACHE.get(indexName);
src/main/java/org/ecocean/OpenSearch.java:345:            indexName + "/_search/point_in_time?keep_alive=" + SEARCH_PIT_TIME);
src/main/java/org/ecocean/OpenSearch.java:350:        PIT_CACHE.put(indexName, id);
src/main/java/org/ecocean/OpenSearch.java:363:    public void deletePit(String indexName)
src/main/java/org/ecocean/OpenSearch.java:365:        String pitId = PIT_CACHE.get(indexName);
src/main/java/org/ecocean/OpenSearch.java:373:        PIT_CACHE.remove(indexName);
src/main/java/org/ecocean/OpenSearch.java:374:        System.out.println("OpenSearch.deletePit(" + indexName + ") [" + pitId + "] completed");
src/main/java/org/ecocean/OpenSearch.java:377:    public JSONObject queryPit(String indexName, final JSONObject query, int numFrom, int pageSize,
src/main/java/org/ecocean/OpenSearch.java:380:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:381:        String pitId = createPit(indexName);
src/main/java/org/ecocean/OpenSearch.java:410:            PIT_CACHE.remove(indexName);
src/main/java/org/ecocean/OpenSearch.java:411:            return queryPit(indexName, query, numFrom, pageSize, sort, sortOrder);
src/main/java/org/ecocean/OpenSearch.java:435:    // https://opensearch.org/docs/2.3/opensearch/search/paginate/
src/main/java/org/ecocean/OpenSearch.java:436:    public JSONObject queryRawScroll(String indexName, final JSONObject query, int pageSize)
src/main/java/org/ecocean/OpenSearch.java:438:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:440:            indexName + "/_search?scroll=" + SEARCH_SCROLL_TIME);
src/main/java/org/ecocean/OpenSearch.java:463:    // ml-service migration v2 (commit #7): force pending writes in `indexName`
src/main/java/org/ecocean/OpenSearch.java:466:    // queue drain — IndexingManager may still have unindexed entities queued.
src/main/java/org/ecocean/OpenSearch.java:468:    public void indexRefresh(final String indexName)
src/main/java/org/ecocean/OpenSearch.java:470:        if (!isValidIndexName(indexName))
src/main/java/org/ecocean/OpenSearch.java:471:            throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:472:        Request req = new Request("POST", indexName + "/_refresh");
src/main/java/org/ecocean/OpenSearch.java:477:    // can see every id in `ids` in `indexName`. Used by MlServiceProcessor
src/main/java/org/ecocean/OpenSearch.java:479:    // index that doesn't yet contain the freshly-written annotations.
src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
src/main/java/org/ecocean/OpenSearch.java:493:    // match job rather than match against a partial index).
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:501:        if (!isValidIndexName(indexName))
src/main/java/org/ecocean/OpenSearch.java:502:            throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:519:        indexRefresh(indexName);
src/main/java/org/ecocean/OpenSearch.java:525:            int seen = queryCount(indexName, query);
src/main/java/org/ecocean/OpenSearch.java:552:    public int queryCount(String indexName, final JSONObject query)
src/main/java/org/ecocean/OpenSearch.java:554:        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:555:        Request searchRequest = new Request("POST", indexName + "/_count");
src/main/java/org/ecocean/OpenSearch.java:563:            System.out.println("queryCount() on index " + indexName + " using query=" + query +
src/main/java/org/ecocean/OpenSearch.java:571:    public Map<String, Long> getAllVersions(String indexName)
src/main/java/org/ecocean/OpenSearch.java:576:        JSONObject res = queryRawScroll(indexName, query, 2000);
src/main/java/org/ecocean/OpenSearch.java:600:            putSettings(indexName,
src/main/java/org/ecocean/OpenSearch.java:601:                new JSONObject("{\"index.max_result_window\": " +
src/main/java/org/ecocean/OpenSearch.java:607:    public JSONObject getSettings(final String indexName)
src/main/java/org/ecocean/OpenSearch.java:609:        Request settingsRequest = new Request("GET", indexName + "/_settings");
src/main/java/org/ecocean/OpenSearch.java:614:            // since we are asking for a specific index's settings, let go ahead and dig down
src/main/java/org/ecocean/OpenSearch.java:615:            return jrtn.getJSONObject(indexName).getJSONObject("settings").getJSONObject("index");
src/main/java/org/ecocean/OpenSearch.java:624:    public void putSettings(final String indexName, final JSONObject settings)
src/main/java/org/ecocean/OpenSearch.java:628:            indexName + "/_settings?preserve_existing=true");
src/main/java/org/ecocean/OpenSearch.java:631:        System.out.println("OpenSearch.putSettings() on " + indexName + ": " + settings + " => " +
src/main/java/org/ecocean/OpenSearch.java:635:    public void indexOpen(final String indexName)
src/main/java/org/ecocean/OpenSearch.java:637:        Request searchRequest = new Request("POST", indexName + "/_open");
src/main/java/org/ecocean/OpenSearch.java:640:        System.out.println("OpenSearch.indexOpen() on " + indexName + ": " + rtn);
src/main/java/org/ecocean/OpenSearch.java:643:    public void indexClose(final String indexName)
src/main/java/org/ecocean/OpenSearch.java:645:        Request searchRequest = new Request("POST", indexName + "/_close");
src/main/java/org/ecocean/OpenSearch.java:648:        System.out.println("OpenSearch.indexClose() on " + indexName + ": " + rtn);
src/main/java/org/ecocean/OpenSearch.java:652:    public void indexUpdate(final String indexName, String id, JSONObject updateData)
src/main/java/org/ecocean/OpenSearch.java:654:        if (!existsIndex(indexName)) throw new IOException("index does not exist: " + indexName);
src/main/java/org/ecocean/OpenSearch.java:656:        updateData.put("indexTimestamp", System.currentTimeMillis());
src/main/java/org/ecocean/OpenSearch.java:659:        Request updateRequest = new Request("POST", indexName + "/_update/" + id);
src/main/java/org/ecocean/OpenSearch.java:664:    // returns 2 lists: (1) items needing (re-)indexing; (2) items needing removal
src/main/java/org/ecocean/OpenSearch.java:666:        Map<String, Long> indexVersions) {
src/main/java/org/ecocean/OpenSearch.java:672:            Long iVer = indexVersions.get(objId);
src/main/java/org/ecocean/OpenSearch.java:678:        for (String idxId : indexVersions.keySet()) {
src/main/java/org/ecocean/OpenSearch.java:686:    public List<Base> queryResultsToObjects(Shepherd myShepherd, String indexName,
src/main/java/org/ecocean/OpenSearch.java:700:            if ("encounter".equals(indexName)) {
src/main/java/org/ecocean/OpenSearch.java:702:            } else if ("occurrence".equals(indexName)) {
src/main/java/org/ecocean/OpenSearch.java:704:            } else if ("individual".equals(indexName)) {
src/main/java/org/ecocean/OpenSearch.java:708:                System.out.println("failed to load " + indexName + " object: " + jarr.get(i));

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Base.java | sed -n '200,330p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "skipAutoIndexing" src/main/java/org/ecocean/Base.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/IndexingManager.java src/main/java/org/ecocean/shepherd/core/Shepherd.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '160,270p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Base.java | sed -n '1,140p'" in /mnt/c/Wildbook-clean2
 succeeded in 229ms:
     1	package org.ecocean;
     2	
     3	import com.fasterxml.jackson.core.JsonFactory;
     4	import com.fasterxml.jackson.core.JsonGenerator;
     5	import com.fasterxml.jackson.core.JsonProcessingException;
     6	import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
     7	import com.fasterxml.jackson.databind.annotation.JsonSerialize;
     8	import java.io.File;
     9	import java.io.IOException;
    10	import java.io.StringWriter;
    11	import java.util.concurrent.Executors;
    12	import java.util.concurrent.ExecutorService;
    13	import java.util.HashMap;
    14	import java.util.Iterator;
    15	import java.util.List;
    16	import java.util.Map;
    17	
    18	import javax.jdo.Query;
    19	import org.ecocean.api.ApiException;
    20	import org.ecocean.api.bulk.BulkValidatorException;
    21	import org.ecocean.shepherd.core.Shepherd;
    22	import org.json.JSONArray;
    23	import org.json.JSONObject;
    24	
    25	/**
    26	 * Base class for other classes such as Encounter.java, Occurrence.java, and MarkedIndividual.java
    27	 *
    28	 * @author nishanth_nattoji
    29	 */
    30	@JsonSerialize(using = BaseSerializer.class) @JsonDeserialize(using =
    31	    BaseDeserializer.class) public abstract class Base {
    32	    /**
    33	     * Retrieves Id, such as:
    34	     *
    35	     * <li>Catalog Number for an Encounter</li>
    36	     * <li>Occurrence ID for an Occurrence</li>
    37	     * <li>Individual ID for a Marked Individual</li>
    38	     * <br>
    39	     *
    40	     * @return Id String
    41	     */
    42	    public abstract String getId();
    43	
    44	    /**
    45	     * Sets Id, such as:
    46	     *
    47	     * <li>Catalog Number for an Encounter</li>
    48	     * <li>Occurrence ID for an Occurrence</li>
    49	     * <li>Individual ID for a Marked Individual</li>
    50	     * <br>
    51	     *
    52	     * @param id to set
    53	     */
    54	    public abstract void setId(final String id);
    55	
    56	    /**
    57	     * The computed version, such as a value computed using the 'modified' property of Encounter.java
    58	     *
    59	     * @return Version long value
    60	     */
    61	    public abstract long getVersion();
    62	
    63	    /**
    64	     * Retrieves the recorded comments such as for an Occurrence or a Marked Individual and occurrence remarks for an Encounter.
    65	     *
    66	     * @return Comments String
    67	     */
    68	    public abstract String getComments();
    69	
    70	    /**
    71	     * Sets the recorded comments such as for an Occurrence or a Marked Individual and occurrence remarks for an Encounter.
    72	     *
    73	     * @param comments to set
    74	     */
    75	    public abstract void setComments(final String comments);
    76	
    77	    /**
    78	     * Adds to the comments recorded for an Occurrence or a Marked Individual and to researcher comments for an Encounter.
    79	     *
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
   116	
   117	    public double getOpensearchScore() {
   118	        return opensearchScore;
   119	    }
   120	
   121	    public abstract String opensearchIndexName();
   122	
   123	    public void opensearchCreateIndex()
   124	    throws IOException {
   125	        OpenSearch opensearch = new OpenSearch();
   126	
   127	        opensearch.createIndex(opensearchIndexName(), opensearchMapping());
   128	    }
   129	
   130	    // this should be overridden (but use this as starting point) if any properties need special mappings (likely)
   131	    public JSONObject opensearchMapping() {
   132	        JSONObject map = new JSONObject();
   133	
   134	        // we *must have* version as we likely sort on this for sync(), so even an empty index will have it in mapping
   135	        map.put("version", new org.json.JSONObject("{\"type\": \"long\"}"));
   136	        // id should be keyword for the sake of sorting
   137	        map.put("id", new org.json.JSONObject("{\"type\": \"keyword\"}"));
   138	        map.put("viewUsers", new org.json.JSONObject("{\"type\": \"keyword\"}"));
   139	        map.put("editUsers", new org.json.JSONObject("{\"type\": \"keyword\"}"));
   140	        return map;

 succeeded in 238ms:
   200	    throws IOException {
   201	        if (updateData == null) return;
   202	        OpenSearch opensearch = new OpenSearch();
   203	
   204	        opensearch.indexUpdate(this.opensearchIndexName(), this.getId(), updateData);
   205	    }
   206	
   207	    // should be overridden
   208	    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
   209	    throws IOException, JsonProcessingException {
   210	        jgen.writeStringField("id", this.getId());
   211	        jgen.writeNumberField("version", this.getVersion());
   212	        jgen.writeNumberField("indexTimestamp", System.currentTimeMillis());
   213	
   214	/*
   215	        these are no longer computed in the general opensearchIndex() call.
   216	        they are too expensive. see Encounter.opensearchIndexPermission()
   217	
   218	        jgen.writeFieldName("viewUsers");
   219	        jgen.writeStartArray();
   220	        for (String id : this.userIdsWithViewAccess(myShepherd)) {
   221	            jgen.writeString(id);
   222	        }
   223	        jgen.writeEndArray();
   224	
   225	        jgen.writeFieldName("editUsers");
   226	        jgen.writeStartArray();
   227	        for (String id : this.userIdsWithEditAccess(myShepherd)) {
   228	            jgen.writeString(id);
   229	        }
   230	        jgen.writeEndArray();
   231	 */
   232	    }
   233	
   234	    public void opensearchDocumentSerializer(JsonGenerator jgen)
   235	    throws IOException, JsonProcessingException {
   236	        Shepherd myShepherd = new Shepherd("context0");
   237	
   238	        myShepherd.setAction("BaseSerializer");
   239	        myShepherd.beginDBTransaction();
   240	        try {
   241	            opensearchDocumentSerializer(jgen, myShepherd);
   242	        } catch (Exception e) {} finally {
   243	            myShepherd.rollbackAndClose();
   244	        }
   245	    }
   246	
   247	    public static JSONObject opensearchQuery(final String indexname, final JSONObject query,
   248	        int numFrom, int pageSize, String sort, String sortOrder)
   249	    throws IOException {
   250	        OpenSearch opensearch = new OpenSearch();
   251	        JSONObject res = opensearch.queryPit(indexname, query, numFrom, pageSize, sort, sortOrder);
   252	
   253	        return res;
   254	    }
   255	
   256	    // this is so we can call it on Base obj, but really is only needed by [overridden by] Encounter (currently)
   257	    public boolean getOpensearchProcessPermissions() {
   258	        return false;
   259	    }
   260	
   261	    public static Map<String, Long> getAllVersions(Shepherd myShepherd, String sql) {
   262	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
   263	        Map<String, Long> rtn = new HashMap<String, Long>();
   264	        List results = (List)query.execute();
   265	        Iterator it = results.iterator();
   266	
   267	        while (it.hasNext()) {
   268	            Object[] row = (Object[])it.next();
   269	            String id = (String)row[0];
   270	            Long version = (Long)row[1];
   271	            if (Util.stringExists(id)) {
   272	                rtn.put(id, version);
   273	            } else {
   274	                System.out.println("WARNING: getAllVersions() skipping empty id; " + row);
   275	            }
   276	        }
   277	        query.closeAll();
   278	        return rtn;
   279	    }
   280	
   281	    // this is the results used for a single GET of object via api
   282	    // default behavior here is just to use opensearchDocument, but each class can override
   283	    // if desired
   284	    public JSONObject jsonForApiGet(Shepherd myShepherd, User user)
   285	    throws IOException {
   286	        JSONObject rtn = new JSONObject();
   287	
   288	        // default/base behavior uses canUserView(), which can disallow user=null etc
   289	        // override jsonForApiGet() if this is undesirable behavior (e.g. Encounter)
   290	        if (!canUserView(user, myShepherd)) {
   291	            rtn.put("success", false);
   292	            rtn.put("statusCode", 401);
   293	            rtn.put("error", "access denied");
   294	            return rtn;
   295	        }
   296	        rtn = opensearchDocumentAsJSONObject(myShepherd);
   297	        rtn.put("success", true);
   298	        rtn.put("statusCode", 200);
   299	        return rtn;
   300	    }
   301	
   302	    public JSONObject opensearchDocumentAsJSONObject(Shepherd myShepherd)
   303	    throws IOException {
   304	        StringWriter sw = new StringWriter();
   305	        JsonFactory jf = new JsonFactory();
   306	        JsonGenerator jgen = jf.createGenerator(sw);
   307	
   308	        jgen.writeStartObject();
   309	        opensearchDocumentSerializer(jgen, myShepherd);
   310	        jgen.close();
   311	        jgen.getCurrentValue();
   312	        String jsonStr = sw.getBuffer().toString();
   313	        sw.close();
   314	        return new JSONObject(jsonStr);
   315	    }
   316	
   317	    // these two methods are kinda hacky needs for opensearchSyncIndex (e.g. the fact
   318	    // they are not static)
   319	    public abstract Base getById(Shepherd myShepherd, String id);
   320	
   321	    public abstract String getAllVersionsSql();
   322	
   323	    // i guess that makes this extra hacky?
   324	    public static Base getByClassnameAndId(Shepherd myShepherd, String className, String id) {
   325	        if ((myShepherd == null) || (className == null) || (id == null)) return null;
   326	        Base tmp = null;
   327	        switch (className) {
   328	        case "encounters":
   329	            tmp = new Encounter();
   330	            break;

 succeeded in 314ms:
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

 succeeded in 320ms:
src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;

exec
/bin/bash -lc 'rg -n "getSkipAutoIndexing|skipAuto" src/main/java src/main/resources' in /mnt/c/Wildbook-clean2
 succeeded in 1057ms:
src/main/java/org/ecocean/WildbookLifecycleListener.java:30:                if (base.getSkipAutoIndexing()) return;
src/main/java/org/ecocean/WildbookLifecycleListener.java:43:        if (OpenSearch.skipAutoIndexing()) {
src/main/java/org/ecocean/WildbookLifecycleListener.java:44:            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
src/main/java/org/ecocean/WildbookLifecycleListener.java:55:            if (base.getSkipAutoIndexing()) return;
src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
src/main/java/org/ecocean/Base.java:106:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
src/main/java/org/ecocean/api/bulk/BulkImporter.java:116:            // see note on MediaAsset.getSkipAutoIndexing()
src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/media/MediaAsset.java:1763:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/WildbookLifecycleListener.java | sed -n '1,80p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '1748,1775p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"setSkipAutoIndexing\\(false\\)|skipAutoIndexing\" src/main/java/org/ecocean/api/bulk src/main/java/org/ecocean/servlet/importer src/main/java/org/ecocean/media src/main/java/org/ecocean/Annotation.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/bulk/BulkImporter.java | sed -n '90,125p'" in /mnt/c/Wildbook-clean2
 succeeded in 275ms:
     1	package org.ecocean;
     2	
     3	import java.io.IOException;
     4	import javax.jdo.listener.*;
     5	import org.datanucleus.enhancement.Persistable;
     6	import org.ecocean.Base;
     7	import org.ecocean.OpenSearch;
     8	import org.ecocean.security.Collaboration;
     9	
    10	// https://www.datanucleus.org/products/accessplatform_4_1/jdo/lifecycle_callbacks.html#listeners
    11	
    12	public class WildbookLifecycleListener implements StoreLifecycleListener, DeleteLifecycleListener,
    13	    CreateLifecycleListener, LoadLifecycleListener {
    14	    public void preDelete(InstanceLifecycleEvent event) {
    15	        Persistable obj = (Persistable)event.getSource();
    16	    }
    17	
    18	    public void postDelete(InstanceLifecycleEvent event) {
    19	        Persistable obj = (Persistable)event.getDetachedInstance();
    20	
    21	        // cannot actually use obj, as it will throw: javax.jdo.JDOUserException: Cannot read fields from a deleted object
    22	/*
    23	        System.out.println("WildbookLifecycleListener postDelete() event type=" +
    24	            event.getEventType() + "; source id=" + obj.dnGetObjectId());
    25	        // System.out.println("WildbookLifecycleListener postDelete() event type=" + event.getEventType() + "; source=" + obj + "; target=" + event.getTarget() + "; detachedInstance=" + event.getDetachedInstance() + "; persistentInstance=" + event.getPersistentInstance());
    26	 */
    27	        if (Base.class.isInstance(obj)) {
    28	            Base base = (Base)obj;
    29	            try {
    30	                if (base.getSkipAutoIndexing()) return;
    31	                base.opensearchUnindexDeep();
    32	            } catch (Exception ex) {
    33	                ex.printStackTrace();
    34	            }
    35	        }
    36	    }
    37	
    38	    public void preStore(InstanceLifecycleEvent event) {}
    39	
    40	    public void postStore(InstanceLifecycleEvent event) {
    41	        Persistable obj = (Persistable)event.getSource();
    42	
    43	        if (OpenSearch.skipAutoIndexing()) {
    44	            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
    45	            return;
    46	        }
    47	/*
    48	        System.out.println("WildbookLifecycleListener postStore() event type=" +
    49	            event.getEventType() + "; source=" + obj + "; target=" + event.getTarget() +
    50	            "; detachedInstance=" + event.getDetachedInstance() + "; persistentInstance=" +
    51	            event.getPersistentInstance());
    52	 */
    53	        if (Base.class.isInstance(obj)) {
    54	            Base base = (Base)obj;
    55	            if (base.getSkipAutoIndexing()) return;
    56	            System.out.println("WildbookLifecycleListener postStore() event on " + base);
    57	            try {
    58	                // base.opensearchIndexDeep();
    59	                // new way - put indexing in managed queue
    60	                IndexingManager im = IndexingManagerFactory.getIndexingManager();
    61	                im.addIndexingQueueEntry(base, false);
    62	            } catch (Exception ex) {
    63	                ex.printStackTrace();
    64	            }
    65	        } else if (Collaboration.class.isInstance(obj)) {
    66	            System.out.println("WildbookLifecycleListener postStore() event on " + obj +
    67	                " triggering permissionsNeeded=true");
    68	            OpenSearch.setPermissionsNeeded(true);
    69	        }
    70	    }
    71	
    72	    // required but we do not use
    73	
    74	    public void postCreate(InstanceLifecycleEvent event) {}
    75	
    76	    public void postLoad(InstanceLifecycleEvent event) {}
    77	}

 succeeded in 286ms:
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
  1773	    // comment cruft only needed for Base class
  1774	    @Override public String getComments() {
  1775	        return null;

 succeeded in 281ms:
    90	            } catch (Exception ex) {
    91	                // TODO we could allow this some leeway with a tolerance setting
    92	                System.out.println("createImport() row=" + rowNum + " failed with " + ex);
    93	                ex.printStackTrace();
    94	                throw new ServletException("unexpected exception on processRow for row=" + rowNum +
    95	                        ": " + ex);
    96	            }
    97	            // (previous) MediaAsset creation counts as 20%, and this counts as 50%,
    98	            // with persisting making up the remaining 30%  #progressBarKludge
    99	            markProgress(rowNum, dataRows.size(), 0.2d, 0.5d);
   100	        }
   101	        logProgress("end processRows");
   102	        System.out.println(
   103	            "------------ all rows processed; beginning persistence -------------\n");
   104	        int persistenceTicksTotal = mediaAssetMap.values().size() + userCache.values().size() +
   105	            encounterCache.values().size() + occurrenceCache.values().size() +
   106	            individualCache.values().size() + projectCache.values().size();
   107	        int persistenceTicks = 0;
   108	        List<Integer> maIds = new ArrayList<Integer>(); // used later to build child MAs
   109	        JSONArray arr = new JSONArray();
   110	        for (MediaAsset ma : mediaAssetMap.values()) {
   111	            ma.setSkipAutoIndexing(true);
   112	            MediaAssetFactory.save(ma, myShepherd);
   113	            System.out.println("MMMM " + ma);
   114	            arr.put(ma.getIdInt());
   115	            maIds.add(ma.getIdInt());
   116	            // see note on MediaAsset.getSkipAutoIndexing()
   117	            // needIndexing.add(ma);
   118	            persistenceTicks++;
   119	            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
   120	        }
   121	        logProgress("end persist MediaAsset");
   122	        rtn.put("mediaAssets", arr);
   123	        for (User u : userCache.values()) {
   124	            myShepherd.getPM().makePersistent(u);
   125	            persistenceTicks++;

 succeeded in 507ms:
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCache.java | sed -n '1,90p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "iaImageIds|iaAnnotationIds|parse.*Strict|fromFancyUUID|sendMediaAssetsForceId|mediaAssetToUri|validMediaAsset" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java docs/plans/2026-05-18-wbia-image-registration-design.md' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '90,165p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '250,390p'" in /mnt/c/Wildbook-clean2
 succeeded in 248ms:
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

 succeeded in 292ms:
docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-wbia-image-registration-design.md:50:| `WildbookIAM.sendMediaAssetsForceId(ArrayList<MediaAsset>, boolean)` | exists, batch POST to `/api/image/json/`, sends our acmId via `image_uuid_list` | The POST body shape and `toFancyUUID` wrapping logic lift line-for-line into the new DTO-based variant |
docs/plans/2026-05-18-wbia-image-registration-design.md:51:| `WildbookIAM.mediaAssetToUri(MediaAsset)` | exists but is **`private`** at line 804. Returns the double-encoded web URL string. Note: `ma.webURL()` can return null and the existing helper dereferences without checking — a long-standing latent NPE. | Promote to `public static String` (signature change from `Object` return; tighten the null-check). Phase A then calls it directly to capture `imageUri` into the DTO. |
docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-wbia-image-registration-design.md:63:public final String imageUri;             // mediaAssetToUri(ma) result
docs/plans/2026-05-18-wbia-image-registration-design.md:71:`dto.mediaAssetAcmId` already carries the value `sendMediaAssetsForceId`
docs/plans/2026-05-18-wbia-image-registration-design.md:79:checks in the same block (mirroring what `sendMediaAssetsForceId` does
docs/plans/2026-05-18-wbia-image-registration-design.md:85:} else if (!WildbookIAM.validMediaAsset(ma)) {
docs/plans/2026-05-18-wbia-image-registration-design.md:86:    reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
docs/plans/2026-05-18-wbia-image-registration-design.md:96:String imageUri = (String) WildbookIAM.mediaAssetToUri(ma);  // method returns Object; cast
docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-wbia-image-registration-design.md:111:Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
docs/plans/2026-05-18-wbia-image-registration-design.md:120:`apiGetJSONArray` and `parseAnnotationIdsArrayStrict`-style helpers; the
docs/plans/2026-05-18-wbia-image-registration-design.md:126:Single-image variant of `sendMediaAssetsForceId`, DTO-only (no Shepherd
docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-wbia-image-registration-design.md:199:    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-wbia-image-registration-design.md:228:already-registered image (skip-if-present inside `sendMediaAssetsForceId`
docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-wbia-image-registration-design.md:248:- `parseImageIdsArrayStrict` — paralleling
docs/plans/2026-05-18-wbia-image-registration-design.md:249:  `parseAnnotationIdsArrayStrict`: null array returns empty; well-formed
docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-wbia-image-registration-design.md:261:  iaAnnotationIdsStrict called after (Phase 1 reached).
docs/plans/2026-05-18-wbia-image-registration-design.md:272:mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
docs/plans/2026-05-18-wbia-image-registration-design.md:279:| MediaAsset fails validMediaAsset at Phase A | Park (MAX_ATTEMPTS) | New eligibility check added |
docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-wbia-image-registration-design.md:306:3. **Strict-parser duplication**: `parseAnnotationIdsArrayStrict` already
docs/plans/2026-05-18-wbia-image-registration-design.md:307:   exists from c11 fix-pass. The new `parseImageIdsArrayStrict` would be
docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:54:        assertEquals("ann-uuid-1", WildbookIAM.fromFancyUUID(annUuid));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:55:        assertEquals("ma-acm-1",   WildbookIAM.fromFancyUUID(imgUuid));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:138:    // --- parseAnnotationIdsArrayStrict -----------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:140:    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:141:        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:144:    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:148:        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:154:    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:159:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:162:    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:167:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:173:     * Builds a "fancy UUID" wrapper that {@link WildbookIAM#fromFancyUUID}
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
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:278:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:359:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:469:     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:478:        String cacheName = "iaAnnotationIds";
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:494:                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:495:                return parseAnnotationIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:512:                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:520:        return parseAnnotationIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530:    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:536:                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:537:            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:539:                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:550:            if (jo != null) ids.add(fromFancyUUID(jo));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:596:        String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:619:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:623:        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:663:            String sentId = fromFancyUUID(sentIds.get(i));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:668:            String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:684:                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692:    public List<String> iaAnnotationIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:        return iaAnnotationIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:697:    public static List<String> iaAnnotationIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:700:        String cacheName = "iaAnnotationIds";
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715:                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723:            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:734:                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
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

 succeeded in 245ms:
   250	                    map.put("image_unixtime_list", new ArrayList<Integer>());
   251	                    map.put("image_gps_lat_list", new ArrayList<Double>());
   252	                    map.put("image_gps_lon_list", new ArrayList<Double>());
   253	                    // acmList = new ArrayList<MediaAsset>();
   254	                } else {
   255	                    bres.put("EMPTY BATCH");
   256	                }
   257	                batchCt++;
   258	            }
   259	        }
   260	        allRtn.put("batchResults", bres);
   261	        return allRtn;
   262	    }
   263	
   264	    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
   265	        Shepherd myShepherd)
   266	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   267	        InvalidKeyException {
   268	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   269	
   270	        if (u == null)
   271	            throw new MalformedURLException(
   272	                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
   273	        URL url = new URL(u);
   274	        int ct = 0;
   275	        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
   276	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   277	        List<String> iaAnnotIds = new ArrayList<String>();
   278	        if (checkFirst) iaAnnotIds = iaAnnotationIds();
   279	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   280	        map.put("image_uuid_list", new ArrayList<String>());
   281	        map.put("annot_species_list", new ArrayList<String>());
   282	        map.put("annot_bbox_list", new ArrayList<int[]>());
   283	        map.put("annot_name_list", new ArrayList<String>());
   284	        map.put("annot_theta_list", new ArrayList<Double>());
   285	
   286	        List<Annotation> acmList = new ArrayList<Annotation>(); // for rectifyAnnotationIds below
   287	        for (Annotation ann : anns) {
   288	            if (iaAnnotIds.contains(ann.getAcmId())) continue;
   289	            if (iaAnnotIds.contains(ann.getId())) continue;
   290	            if (ann.getMediaAsset() == null) {
   291	                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
   292	                    "; skipping!");
   293	                continue;
   294	            }
   295	            if (ann.getMediaAsset().getAcmId() == null) {
   296	                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
   297	                    " (MediaAsset id=" + ann.getMediaAsset().getId() +
   298	                    " not added to IA?); skipping!");
   299	                continue;
   300	            }
   301	            if (!IBEISIA.validForIdentification(ann)) {
   302	                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
   303	                continue;
   304	            }
   305	            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
   306	            if (iid == null) {
   307	                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
   308	                    ann.getMediaAsset() + " on " + ann + "; skipping!");
   309	                continue;
   310	            }
   311	            acmList.add(ann);
   312	            map.get("image_uuid_list").add(iid);
   313	            int[] bbox = ann.getBbox();
   314	            map.get("annot_bbox_list").add(bbox);
   315	            // yuck - IA class is not species
   316	            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
   317	            // better
   318	            map.get("annot_species_list").add(ann.getIAClass());
   319	
   320	            map.get("annot_theta_list").add(ann.getTheta());
   321	            String name = ann.findIndividualId(myShepherd);
   322	            map.get("annot_name_list").add((name == null) ? "____" : name);
   323	            ct++;
   324	        }
   325	        // myShepherd.rollbackDBTransaction();
   326	
   327	        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
   328	        if (ct < 1) return null; // null for "none to send" ?  is this cool?
   329	        System.out.println("sendAnnotations(): data -->\n" + map);
   330	        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   331	        System.out.println("sendAnnotations() -> " + rtn);
   332	        List<String> acmIds = acmIdsFromResponse(rtn);
   333	        if (acmIds == null) {
   334	            IA.log(
   335	                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
   336	                + rtn);
   337	        } else {
   338	            int numChanged = AcmUtil.rectifyAnnotationIds(acmList, acmIds, myShepherd);
   339	            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
   340	                " Annotation(s) acmId(s) via rectifyAnnotationIds()");
   341	        }
   342	        return rtn;
   343	    }
   344	
   345	    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
   346	        Shepherd myShepherd)
   347	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   348	        InvalidKeyException {
   349	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   350	
   351	        if (u == null)
   352	            throw new MalformedURLException(
   353	                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
   354	        URL url = new URL(u);
   355	        int ct = 0;
   356	        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
   357	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   358	        List<String> iaAnnotIds = new ArrayList<String>();
   359	        if (checkFirst) iaAnnotIds = iaAnnotationIds();
   360	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   361	        map.put("image_uuid_list", new ArrayList<String>());
   362	        map.put("annot_uuid_list", new ArrayList<String>());
   363	        map.put("annot_species_list", new ArrayList<String>());
   364	        map.put("annot_bbox_list", new ArrayList<int[]>());
   365	        map.put("annot_name_list", new ArrayList<String>());
   366	        map.put("annot_theta_list", new ArrayList<Double>());
   367	        for (Annotation ann : anns) {
   368	            if (iaAnnotIds.contains(ann.getAcmId())) continue;
   369	            if (iaAnnotIds.contains(ann.getId())) continue;
   370	            if (ann.getMediaAsset() == null) {
   371	                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
   372	                    ann + "; skipping!");
   373	                continue;
   374	            }
   375	            if (!IBEISIA.validForIdentification(ann)) {
   376	                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
   377	                continue;
   378	            }
   379	            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
   380	            if (iid == null) {
   381	                IA.log(
   382	                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
   383	                    + ann.getMediaAsset() + " on " + ann + "; skipping!");
   384	                continue;
   385	            }
   386	            map.get("image_uuid_list").add(iid);
   387	            JSONObject aid = toFancyUUID(ann.getId());
   388	            map.get("annot_uuid_list").add(aid);
   389	            int[] bbox = ann.getBbox();
   390	            map.get("annot_bbox_list").add(bbox);

 succeeded in 255ms:
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
   151	                    if (acmIds == null) {
   152	                        IA.log(
   153	                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
   154	                            + rtn);
   155	                    } else {
   156	                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
   157	                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
   158	                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
   159	                    }
   160	                    bres.put(rtn);
   161	                    // initialize for next batch (if any)
   162	                    map.put("image_uri_list", new ArrayList<JSONObject>());
   163	                    map.put("image_uuid_list", new ArrayList<JSONObject>());
   164	                    map.put("image_unixtime_list", new ArrayList<Integer>());
   165	                    map.put("image_gps_lat_list", new ArrayList<Double>());

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '740,835p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCacheFactory.java | sed -n '1,80p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "class QueryCacheFactory|getQueryCache|cachedQueries" src/main/java/org/ecocean/cache src/test/java/org/ecocean/cache' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '460,545p'" in /mnt/c/Wildbook-clean2
 succeeded in 293ms:
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

 succeeded in 304ms:
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
   776	    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
   777	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   778	        InvalidKeyException {
   779	        URL u = IBEISIA.iaURL(context, urlSuffix);
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

 succeeded in 325ms:
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

 exited 2 in 491ms:
rg: src/test/java/org/ecocean/cache: No such file or directory (os error 2)
src/main/java/org/ecocean/cache/QueryCache.java:14:    private Map<String, CachedQuery> cachedQueries;
src/main/java/org/ecocean/cache/QueryCache.java:23:        if (cachedQueries == null) loadQueries();
src/main/java/org/ecocean/cache/QueryCache.java:24:        return cachedQueries.get(name);
src/main/java/org/ecocean/cache/QueryCache.java:27:    public Map<String, CachedQuery> cachedQueries() { return cachedQueries; }
src/main/java/org/ecocean/cache/QueryCache.java:32:        cachedQueries = new HashMap<String, CachedQuery>();
src/main/java/org/ecocean/cache/QueryCache.java:40:            cachedQueries.put(sq.getName(), sq.getCachedQueryCopy());
src/main/java/org/ecocean/cache/QueryCache.java:48:        if (cachedQueries.containsKey(name)) {
src/main/java/org/ecocean/cache/QueryCache.java:49:            cachedQueries.get(name).invalidate();
src/main/java/org/ecocean/cache/QueryCache.java:59:        cachedQueries.put(name, cq);
src/main/java/org/ecocean/cache/QueryCache.java:63:        cachedQueries.put(cq.getName(), cq);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:6:    public synchronized static QueryCache getQueryCache(String context) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '250,520p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "WbiaRegisterRequest|registerOneAnnotationWithWbia|WbiaRegistration|wbiaRegister" src/main/java/org/ecocean/StartupWildbook.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '390,470p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '580,675p'" in /mnt/c/Wildbook-clean2
 succeeded in 261ms:
   250	            	Shepherd myShepherd = new Shepherd(context);
   251	            	myShepherd.setAction("AcmIdMessageHandler.handler."+mediaAssetID);
   252	            	myShepherd.beginDBTransaction();
   253	            	try {
   254	            		MediaAsset asset=myShepherd.getMediaAsset(mediaAssetID);
   255	            		if(asset!=null) {
   256			                ArrayList<MediaAsset> fixMe = new ArrayList<MediaAsset>();
   257		            		fixMe.add(asset);
   258			                IBEISIA.sendMediaAssetsNew(fixMe, context);
   259			                myShepherd.updateDBTransaction();
   260	            		}
   261	            	}
   262	            	//RuntimeExceptions include an array of timeout and connectivitivity issues
   263	            	//indicating WBIA may be overloaded or restarting
   264	            	//therefore this exception includes a simple sleep function to pause ACM ID registration
   265	            	//to give WBIA time to restart or be less busy.
   266	            	//This implementation is temporary until ACM ID registration is removed entirely
   267	                catch (java.lang.RuntimeException ex) {
   268	                    System.out.println("\r\n\r\nWARNING: AcmIdMessageHandler processQueueMessage() threw " +
   269	                        ex.toString()+"\r\n\r\n");
   270	                    ex.printStackTrace();
   271	                    
   272	                    long timeoutMilliseconds=60000;
   273	                    Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
   274	                    if(props!=null && props.getProperty("timeoutMilliseconds")!=null) {
   275	                    	String millis = props.getProperty("timeoutMilliseconds");
   276	                    	Long millisAsLong = Long.getLong(millis);
   277	                    	if(millisAsLong!=null)timeoutMilliseconds=millisAsLong.longValue();
   278	                    }
   279	                    
   280	                    try {
   281	                    	Thread.sleep(timeoutMilliseconds);
   282	                    	Queue acmIdQueue=IAGateway.getAcmIdQueue(context);
   283	                    	acmIdQueue.publish(mediaAssetID);
   284	                    }
   285	                    catch(Exception ioe) {
   286	                    	ioe.printStackTrace();
   287	                    }
   288	                    return false;
   289	                }
   290	                catch (Exception ex) {
   291	                    System.out.println("\r\n\r\nWARNING: AcmIdMessageHandler processQueueMessage() threw " +
   292	                        ex.toString()+"\r\n\r\n");
   293	                    ex.printStackTrace();
   294	                    
   295	                    try {
   296	                    	Queue acmIdQueue=IAGateway.getAcmIdQueue(context);
   297	                    	acmIdQueue.publish(mediaAssetID);
   298	                    }
   299	                    catch(Exception ioe) {
   300	                    	ioe.printStackTrace();
   301	                    }
   302	                    return false;
   303	                }
   304	            	finally {
   305	            		myShepherd.rollbackAndClose();
   306	            	}
   307	                return true;
   308	            }
   309	        }
   310	
   311	        class IACallbackMessageHandler extends QueueMessageHandler {
   312	            public boolean handler(String msg) {
   313	                try {
   314	                    org.ecocean.servlet.IAGateway.processCallbackQueueMessage(msg); // yeah we need to move this somewhere else...
   315	                } catch (Exception ex) {
   316	                    System.out.println(
   317	                        "WARNING: IACallbackMessageHandler processCallbackQueueMessage() threw " +
   318	                        ex.toString());
   319	                    ex.printStackTrace();
   320	                }
   321	                return true;
   322	            }
   323	        }
   324	        if (!IBEISIA.iaEnabled()) {
   325	            System.out.println("+ INFO: IA not enabled; IA queue service not started");
   326	            return;
   327	        }
   328	        Queue queue = null;
   329	        try {
   330	            queue = QueueUtil.getBest(context, "IA");
   331	        } catch (IOException ex) {
   332	            System.out.println("+ ERROR: IA queue startup exception: " + ex.toString());
   333	        }
   334	        Queue queueCallback = null;
   335	        try {
   336	            queueCallback = QueueUtil.getBest(context, "IACallback");
   337	        } catch (IOException ex) {
   338	            System.out.println("+ ERROR: IACallback queue startup exception: " + ex.toString());
   339	        }
   340	        Queue detectionQ = null;
   341	        try {
   342	            detectionQ = QueueUtil.getBest(context, "detection");
   343	        } catch (IOException ex) {
   344	            System.out.println("+ ERROR: detection queue startup exception: " + ex.toString());
   345	        }
   346	        //MediaAsset ACM ID registration queue
   347	        Queue acmidQ = null;
   348	        try {
   349	            acmidQ = QueueUtil.getBest(context, "acmid");
   350	        } catch (IOException ex) {
   351	            System.out.println("+ ERROR: acmid queue startup exception: " + ex.toString());
   352	        }
   353	        if ((queue == null) || (queueCallback == null) || (detectionQ == null) || (acmidQ == null)) {
   354	            System.out.println("+ WARNING: IA queue service(s) NOT started");
   355	            return;
   356	        }
   357	        IAMessageHandler qh = new IAMessageHandler();
   358	        try {
   359	            queue.consume(qh);
   360	            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() started on " +
   361	                queue.toString());
   362	        } catch (IOException iox) {
   363	            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() FAILED on " +
   364	                queue.toString() + ": " + iox.toString());
   365	        }
   366	        IACallbackMessageHandler qh2 = new IACallbackMessageHandler();
   367	        try {
   368	            queueCallback.consume(qh2);
   369	            System.out.println(
   370	                "+ StartupWildbook.startIAQueues() queueCallback.consume() started on " +
   371	                queueCallback.toString());
   372	        } catch (IOException iox) {
   373	            System.out.println(
   374	                "+ StartupWildbook.startIAQueues() queueCallback.consume() FAILED on " +
   375	                queueCallback.toString() + ": " + iox.toString());
   376	        }
   377	        IAMessageHandler qh3 = new IAMessageHandler();
   378	        try {
   379	            detectionQ.consume(qh3);
   380	            System.out.println(
   381	                "+ StartupWildbook.startIAQueues() detectionQ.consume() started on " +
   382	                detectionQ.toString());
   383	        } catch (IOException iox) {
   384	            System.out.println("+ StartupWildbook.startIAQueues() detectionQ.consume() FAILED on " +
   385	                detectionQ.toString() + ": " + iox.toString());
   386	        }
   387	        //ACM ID queue handler
   388	        AcmIdMessageHandler qh4 = new AcmIdMessageHandler();
   389	        try {
   390	            acmidQ.consume(qh4);
   391	            System.out.println(
   392	                "+ StartupWildbook.startIAQueues() acmidQ.consume() started on " +
   393	                acmidQ.toString());
   394	        } catch (IOException iox) {
   395	            System.out.println("+ StartupWildbook.startIAQueues() acmidQ.consume() FAILED on " +
   396	                acmidQ.toString() + ": " + iox.toString());
   397	        }
   398	    }
   399	
   400	    private static void startWildbookScheduledTaskThread(String context) {
   401	        System.out.println("STARTING: StartupWildbook.startWildbookScheduledTaskThread()");
   402	        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
   403	        ses.scheduleAtFixedRate(new Runnable() {
   404	            @Override public void run() {
   405	                System.out.println("[INFO]: checking for scheduled tasks to execute...");
   406	                Shepherd myShepherd = new Shepherd(context);
   407	                myShepherd.setAction("WildbookScheduledTaskThread");
   408	                try {
   409	                    ArrayList<WildbookScheduledTask> scheduledTasks =
   410	                    myShepherd.getAllIncompleteWildbookScheduledTasks();
   411	                    for (WildbookScheduledTask scheduledTask : scheduledTasks) {
   412	                        if (scheduledTask.isTaskEligibleForExecution()) {
   413	                            scheduledTask.execute(myShepherd);
   414	                        }
   415	                    }
   416	                } catch (Exception e) {
   417	                    myShepherd.rollbackAndClose();
   418	                    e.printStackTrace();
   419	                }
   420	                myShepherd.closeDBTransaction();
   421	            }
   422	        }, 0, 1, TimeUnit.HOURS);
   423	    }
   424	
   425	    /**
   426	     * ml-service migration v2 §commit #11. Background polling thread that
   427	     * registers ml-service-created annotations with WBIA so HotSpotter is
   428	     * available on demand for them. State is on the Annotation row itself
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

 succeeded in 306ms:
src/main/java/org/ecocean/StartupWildbook.java:49:    private static volatile ScheduledExecutorService wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:207:            startWbiaRegistrationPollingThread(context);
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:439:     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:449:    private static void startWbiaRegistrationPollingThread(final String context) {
src/main/java/org/ecocean/StartupWildbook.java:453:        if (wbiaRegisterExecutor != null) {
src/main/java/org/ecocean/StartupWildbook.java:455:                "WARN: startWbiaRegistrationPollingThread() called with existing executor; skipping");
src/main/java/org/ecocean/StartupWildbook.java:458:        System.out.println("STARTING: StartupWildbook.startWbiaRegistrationPollingThread()");
src/main/java/org/ecocean/StartupWildbook.java:462:                    Thread t = new Thread(r, "WbiaRegistrationPoll");
src/main/java/org/ecocean/StartupWildbook.java:470:                    runWbiaRegistrationPoll(context);
src/main/java/org/ecocean/StartupWildbook.java:476:                    System.out.println("WARN: WbiaRegistrationPoll uncaught: " + t);
src/main/java/org/ecocean/StartupWildbook.java:481:        wbiaRegisterExecutor = ses;
src/main/java/org/ecocean/StartupWildbook.java:484:    private static void runWbiaRegistrationPoll(String context) {
src/main/java/org/ecocean/StartupWildbook.java:489:        shep.setAction("StartupWildbook.WbiaRegistrationPoll.fetch");
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:496:            q.setOrdering("wbiaRegisterAttempts ascending");
src/main/java/org/ecocean/StartupWildbook.java:507:            System.out.println("WARN: WbiaRegistrationPoll fetch failed: " + ex);
src/main/java/org/ecocean/StartupWildbook.java:514:        System.out.println("[INFO] WbiaRegistrationPoll: " + pendingIds.size() + " pending");
src/main/java/org/ecocean/StartupWildbook.java:523:                wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:524:                System.out.println("[INFO] WbiaRegistrationPoll: stopping mid-batch (interrupted)");
src/main/java/org/ecocean/StartupWildbook.java:527:            registerOneAnnotationWithWbia(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:703:    private static void registerOneAnnotationWithWbia(String context, String annId) {
src/main/java/org/ecocean/StartupWildbook.java:705:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:713:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:714:            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
src/main/java/org/ecocean/StartupWildbook.java:730:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:731:            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
src/main/java/org/ecocean/StartupWildbook.java:747:    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
src/main/java/org/ecocean/StartupWildbook.java:750:        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
src/main/java/org/ecocean/StartupWildbook.java:776:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
src/main/java/org/ecocean/StartupWildbook.java:788:            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:789:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
src/main/java/org/ecocean/StartupWildbook.java:795:            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
src/main/java/org/ecocean/StartupWildbook.java:813:        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
src/main/java/org/ecocean/StartupWildbook.java:843:                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
src/main/java/org/ecocean/StartupWildbook.java:851:            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
src/main/java/org/ecocean/StartupWildbook.java:887:        ScheduledExecutorService ses = wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:889:        wbiaRegisterExecutor = null;
src/main/java/org/ecocean/StartupWildbook.java:890:        System.out.println("STOPPING: StartupWildbook.wbiaRegisterExecutor");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:560:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
src/main/java/org/ecocean/Annotation.java:80:    protected Boolean wbiaRegistered;
src/main/java/org/ecocean/Annotation.java:82:    // Failed-attempt counter. Polling filters wbiaRegisterAttempts < MAX so
src/main/java/org/ecocean/Annotation.java:84:    protected int wbiaRegisterAttempts = 0;
src/main/java/org/ecocean/Annotation.java:336:    public Boolean getWbiaRegistered() { return wbiaRegistered; }
src/main/java/org/ecocean/Annotation.java:337:    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:341:    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
src/main/java/org/ecocean/Annotation.java:343:    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
src/main/java/org/ecocean/Annotation.java:344:    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:346:        this.wbiaRegisterAttempts++;

 succeeded in 273ms:
   390	            map.get("annot_bbox_list").add(bbox);
   391	            // yuck - IA class is not species
   392	            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
   393	            // better
   394	            map.get("annot_species_list").add(ann.getIAClass());
   395	
   396	            map.get("annot_theta_list").add(ann.getTheta());
   397	            String name = ann.findIndividualId(myShepherd);
   398	            map.get("annot_name_list").add((name == null) ? "____" : name);
   399	            ct++;
   400	        }
   401	        // myShepherd.rollbackDBTransaction();
   402	
   403	        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
   404	        if (ct < 1) return null; // null for "none to send" ?  is this cool?
   405	        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
   406	        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   407	        System.out.println("sendAnnotationsForceId() -> " + rtn);
   408	        checkForcedIds(map.get("annot_uuid_list"), rtn.optJSONArray("response"));
   409	        return rtn;
   410	    }
   411	
   412	    // ------------------------------------------------------------------
   413	    // ml-service migration v2: no-Shepherd WBIA registration helpers.
   414	    //
   415	    // The polling thread in StartupWildbook splits the work into:
   416	    //   Phase A (write tx) - load DTO + close.
   417	    //   Phase B (no DB)    - call into the helpers below.
   418	    //   Phase C (write tx) - persist result.
   419	    // Phase B must not hold a Shepherd transaction across the WBIA call.
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
   467	
   468	    /**
   469	     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
   470	     * fetch failure rather than returning an empty list. Phase B needs

 succeeded in 275ms:
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

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '700,860p'" in /mnt/c/Wildbook-clean2
 succeeded in 296ms:
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

exec
/bin/bash -lc "rg -n \"class name=\\\"MediaAsset\\\"|field name=\\\"features\\\"|class name=\\\"Feature\\\"|field name=\\\"asset\\\"|field name=\\\"annotation\\\"\" src/main/resources/org/ecocean/media/package.jdo src/main/resources/org/ecocean/package.jdo" in /mnt/c/Wildbook-clean2
 succeeded in 305ms:
src/main/resources/org/ecocean/package.jdo:593:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
src/main/resources/org/ecocean/package.jdo:1056:		<field name="annotation" dependent-element="false" >
src/main/resources/org/ecocean/media/package.jdo:8:	<class name="MediaAsset" identity-type="application">
src/main/resources/org/ecocean/media/package.jdo:54:            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" mapped-by="asset" >
src/main/resources/org/ecocean/media/package.jdo:111:	<class name="Feature" identity-type="application">
src/main/resources/org/ecocean/media/package.jdo:123:		<field name="annotation" />
src/main/resources/org/ecocean/media/package.jdo:124:		<field name="asset" />

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/media/package.jdo | sed -n '1,140p'" in /mnt/c/Wildbook-clean2
 succeeded in 269ms:
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

exec
/bin/bash -lc "rg -n \"static Task load|getTask\\(\" src/main/java/org/ecocean/ia/Task.java src/main/java/org/ecocean/shepherd/core/Shepherd.java | head -n 80" in /mnt/c/Wildbook-clean2
 succeeded in 300ms:
src/main/java/org/ecocean/ia/Task.java:516:    public static Task load(String taskId, Shepherd myShepherd) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2775:    public Task getTask(String id) {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '500,535p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/shepherd/core/Shepherd.java | sed -n '2768,2810p'" in /mnt/c/Wildbook-clean2
 succeeded in 268ms:
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

 succeeded in 283ms:
  2768	        } catch (Exception e) {
  2769	            System.out.println("Exception in getMarkedIndividualHard for enc " + num);
  2770	            e.printStackTrace();
  2771	        }
  2772	        return ((al.size() > 0) ? ((MarkedIndividual)al.get(0)) : null);
  2773	    }
  2774	
  2775	    public Task getTask(String id) {
  2776	        if (id == null) return null; // save us some trouble
  2777	        Task theTask = null;
  2778	
  2779	        try {
  2780	            theTask = ((org.ecocean.ia.Task)(pm.getObjectById(pm.newObjectIdInstance(Task.class,
  2781	                id.trim()), true)));
  2782	        } catch (Exception e) {
  2783	            e.printStackTrace();
  2784	            return null;
  2785	        }
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

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '1,45p'" in /mnt/c/Wildbook-clean2
 succeeded in 281ms:
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

codex
The key shape is clearer now: the topTask boundary is real, and the direct-SQL join sketch matches existing table conventions. One thing I’m checking carefully is whether “visible by `_id`” is strong enough, because the actual match query also requires the nested embedding metadata.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/IndexingManager.java | sed -n '1,100p'" in /mnt/c/Wildbook-clean2
 succeeded in 311ms:
     1	package org.ecocean;
     2	
     3	
     4	import java.util.ArrayList;
     5	import java.util.Collections;
     6	import java.util.List;
     7	import java.util.Properties;
     8	import java.util.concurrent.ExecutorService;
     9	import java.util.concurrent.Executors;
    10	import org.ecocean.shepherd.core.Shepherd;
    11	import org.ecocean.shepherd.core.ShepherdProperties;
    12	
    13	
    14	
    15	public class IndexingManager {
    16		
    17		//The ExecutorService executes indexing jobs
    18		private final ExecutorService executor;
    19			
    20		//The indexingQueue is a List of Strings that represent the UUIDs of Base class-implementing objects 
    21		//(Encounter, MarkedIndividual, Annotation, etc.) that need to be indexed or unindexed.
    22		//The queue ensures that overzealous calls from the WildbookLifecycleListener do not cause
    23		//unnecessary, duplicate indexing jobs. The UUIDs of the objects being ndexed are removed 
    24		//from the queue once completed.
    25	    private List<String> indexingQueue = Collections.synchronizedList(new ArrayList<String>());
    26	  
    27	    public IndexingManager() {
    28	    	
    29	    	int numAllowedThreads = 4;
    30	    	Properties props = ShepherdProperties.getProperties("OpenSearch.properties", "", "context0");
    31	    	if(props!=null) {
    32		    	String indexingNumAllowedThreads = props.getProperty("indexingNumAllowedThreads");
    33		    	if(indexingNumAllowedThreads!=null) {
    34		    		Integer allowThreads = Integer.getInteger(indexingNumAllowedThreads);
    35		    		if(allowThreads!=null)numAllowedThreads = allowThreads.intValue();
    36		    	}
    37	    	}
    38	    	executor = Executors.newFixedThreadPool(numAllowedThreads);
    39	    	
    40	    }
    41	    
    42	    //Returns the indexing queue List of Strings
    43	    public List<String> getIndexingQueue() { return indexingQueue; }
    44	    
    45	    /*
    46	     * Adds a Base object to the queue for indexing or unindexing
    47	     * @Base base The Base-class implementing object to be indexed or unindexed
    48	     * @boolean unindex Whether the object is to be indexed or unindexed.
    49	     */
    50	    public void addIndexingQueueEntry(Base base, boolean unindex) {
    51	    	String objectID = base.getId();
    52	    	Class myClass = base.getClass();
    53	    	if(!indexingQueue.contains(objectID)) {
    54	    		indexingQueue.add(objectID);
    55	    		
    56	    		//IMPORTANT - no persistent objects, such as the passed in Base can be referenced inside this method
    57	            Runnable rn = new Runnable() {
    58	                public void run() {
    59	                    Shepherd bgShepherd = new Shepherd("context0");
    60	                    bgShepherd.setAction("IndexingManager_" + objectID);
    61	                    bgShepherd.beginDBTransaction();
    62	                    try {
    63	                    	Base base = (Base)bgShepherd.getPM().getObjectById(myClass, objectID);
    64	                    	if(unindex) {base.opensearchUnindexDeep();}
    65	                    	else{base.opensearchIndexDeep();}
    66	                    	
    67	                    } 
    68	                    catch (Exception e) {
    69	                        e.printStackTrace();
    70	                    } 
    71	                    finally {
    72	                        bgShepherd.rollbackAndClose();
    73	                    }
    74	                    
    75	                    //remove from indexing queue
    76	                    if(indexingQueue.contains(objectID))indexingQueue.remove(objectID);
    77	                }
    78	            };
    79	
    80	            executor.execute(rn);
    81	    		
    82	    	}
    83	
    84	    }
    85	
    86	    //Removes an oject's UUID from the queue
    87	    public void removeIndexingQueueEntry(String objectID) {
    88	        if (indexingQueue.contains(objectID)) {
    89	        	indexingQueue.remove(objectID);
    90	        }
    91	    }
    92	
    93	    //Resets the indexing queue
    94	    public void resetIndexingQueuehWithInitialCapacity(int initialCapacity) {
    95	    	indexingQueue = null;
    96	    	indexingQueue = Collections.synchronizedList(new ArrayList<String>());
    97	    }
    98	    
    99	    public void shutdown() {
   100	    	if(executor!=null)executor.shutdown();

exec
/bin/bash -lc 'rg -n "addToDetectionQueue|requeueJob|queueResumeMessage|deferredMatch|sleep|delay|publish" src/main/java/org/ecocean/servlet/IAGateway.java src/main/java/org/ecocean/queue src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '1,130p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class FileQueue|implements Queue|publish\\(\" src/main/java/org/ecocean | head -n 120" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '560,630p'" in /mnt/c/Wildbook-clean2
 succeeded in 276ms:
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

 succeeded in 305ms:
   560	    public static boolean addToQueue(String context, String content)
   561	    throws IOException {
   562	        System.out.println("IAGateway.addToQueue() publishing: " + content);
   563	        getIAQueue(context).publish(content);
   564	        return true;
   565	    }
   566	
   567	    // also used by EncounterForm with new Encounter submission
   568	    public static boolean addToDetectionQueue(String context, String content)
   569	    throws IOException {
   570	        System.out.println("IAGateway.addToDetectionQueue() publishing: " + content);
   571	        getDetectionQueue(context).publish(content);
   572	        return true;
   573	    }
   574	
   575	    public static boolean addToAcmIdQueue(String context, String mediaAssetID)
   576	    throws IOException {
   577	        System.out.println("IAGateway.addToAcmIdQueue() trying to regist MediaAsset ID: " +
   578	            mediaAssetID);
   579	        getAcmIdQueue(context).publish(mediaAssetID);
   580	        return true;
   581	    }
   582	
   583	    public static Queue getIAQueue(String context)
   584	    throws IOException {
   585	        IAQueue = QueueUtil.getBest(context, "IA");
   586	        return IAQueue;
   587	    }
   588	
   589	    public static Queue getDetectionQueue(String context)
   590	    throws IOException {
   591	        detectionQueue = QueueUtil.getBest(context, "detection");
   592	        return detectionQueue;
   593	    }
   594	
   595	    public static Queue getAcmIdQueue(String context)
   596	    throws IOException {
   597	        acmIdQueue = QueueUtil.getBest(context, "acmid");
   598	        return acmIdQueue;
   599	    }
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

 succeeded in 396ms:
src/main/java/org/ecocean/servlet/IAGateway.java:137:                        ok = addToDetectionQueue(context, j.toString());
src/main/java/org/ecocean/servlet/IAGateway.java:144:                    ok = addToDetectionQueue(context, j.toString());
src/main/java/org/ecocean/servlet/IAGateway.java:385:                requeueJob(jobj, true);
src/main/java/org/ecocean/servlet/IAGateway.java:562:        System.out.println("IAGateway.addToQueue() publishing: " + content);
src/main/java/org/ecocean/servlet/IAGateway.java:563:        getIAQueue(context).publish(content);
src/main/java/org/ecocean/servlet/IAGateway.java:568:    public static boolean addToDetectionQueue(String context, String content)
src/main/java/org/ecocean/servlet/IAGateway.java:570:        System.out.println("IAGateway.addToDetectionQueue() publishing: " + content);
src/main/java/org/ecocean/servlet/IAGateway.java:571:        getDetectionQueue(context).publish(content);
src/main/java/org/ecocean/servlet/IAGateway.java:579:        getAcmIdQueue(context).publish(mediaAssetID);
src/main/java/org/ecocean/servlet/IAGateway.java:748:        if (requeue) requeueJob(jobj, requeueIncrement);
src/main/java/org/ecocean/servlet/IAGateway.java:751:    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
src/main/java/org/ecocean/servlet/IAGateway.java:764:            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
src/main/java/org/ecocean/servlet/IAGateway.java:768:        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
src/main/java/org/ecocean/servlet/IAGateway.java:771:        final long sleepMillis = 1000;
src/main/java/org/ecocean/servlet/IAGateway.java:786:                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:788:                            Thread.sleep(whileSleepMillis);
src/main/java/org/ecocean/servlet/IAGateway.java:797:                            addToDetectionQueue(context, jobj.toString());
src/main/java/org/ecocean/servlet/IAGateway.java:805:                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
src/main/java/org/ecocean/servlet/IAGateway.java:865:        queue.publish(qjob.toString());
src/main/java/org/ecocean/servlet/IAGateway.java:916:        boolean ok = addToDetectionQueue(context, qjob.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:        if (jobData.optBoolean("deferredMatch", false)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:92:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:133:                IAGateway.requeueJob(jobData, ex.shouldIncrement());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:684:        payload.put("deferredMatch", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:693:            IAGateway.addToDetectionQueue(context, payload.toString());
src/main/java/org/ecocean/queue/Queue.java:17:    public abstract void publish(String msg)
src/main/java/org/ecocean/queue/FileQueue.java:63:    public void publish(String msg)
src/main/java/org/ecocean/queue/FileQueue.java:66:            throw new IOException("FileQueue.publish() failed, queueDir is not set");
src/main/java/org/ecocean/queue/FileQueue.java:73:        System.out.println("INFO: FileQueue.publish() added " + queueDir + " -> " + qid);
src/main/java/org/ecocean/queue/QueueUtil.java:61:        }, 1, // initial delay
src/main/java/org/ecocean/queue/QueueUtil.java:62:            1, // period delay *after* execution finishes

 succeeded in 782ms:
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:136:        getQueue().publish(content);
src/main/java/org/ecocean/StartupWildbook.java:283:                    	acmIdQueue.publish(mediaAssetID);
src/main/java/org/ecocean/StartupWildbook.java:297:                    	acmIdQueue.publish(mediaAssetID);
src/main/java/org/ecocean/queue/Queue.java:17:    public abstract void publish(String msg)
src/main/java/org/ecocean/queue/FileQueue.java:18:public class FileQueue extends Queue {
src/main/java/org/ecocean/queue/FileQueue.java:63:    public void publish(String msg)
src/main/java/org/ecocean/queue/FileQueue.java:66:            throw new IOException("FileQueue.publish() failed, queueDir is not set");
src/main/java/org/ecocean/queue/FileQueue.java:73:        System.out.println("INFO: FileQueue.publish() added " + queueDir + " -> " + qid);
src/main/java/org/ecocean/servlet/IAGateway.java:563:        getIAQueue(context).publish(content);
src/main/java/org/ecocean/servlet/IAGateway.java:571:        getDetectionQueue(context).publish(content);
src/main/java/org/ecocean/servlet/IAGateway.java:579:        getAcmIdQueue(context).publish(mediaAssetID);
src/main/java/org/ecocean/servlet/IAGateway.java:865:        queue.publish(qjob.toString());

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '730,815p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/queue/FileQueue.java | sed -n '1,105p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/queue/QueueUtil.java | sed -n '45,90p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/queue/Queue.java | sed -n '1,45p'" in /mnt/c/Wildbook-clean2
 succeeded in 257ms:
     1	package org.ecocean.queue;
     2	
     3	import java.io.File;
     4	import java.io.IOException;
     5	import java.io.PrintWriter;
     6	import org.ecocean.CommonConfiguration;
     7	import org.ecocean.Util;
     8	
     9	import java.nio.file.attribute.BasicFileAttributes;
    10	import java.nio.file.attribute.FileTime;
    11	import java.nio.file.Files;
    12	import java.util.Arrays;
    13	
    14	import java.util.List;
    15	import java.util.stream.Collectors;
    16	import org.apache.commons.lang3.StringUtils;
    17	
    18	public class FileQueue extends Queue {
    19	    private static String TYPE_NAME = "File";
    20	    private static File queueBaseDir = null;
    21	    private File queueDir = null;
    22	
    23	    public static boolean isAvailable(String context) {
    24	        return true; 
    25	    }
    26	
    27	    public FileQueue(final String name)
    28	    throws IOException {
    29	        super(name);
    30	        if (queueBaseDir == null)
    31	            throw new IOException("FileQueue.init() has not yet been called!");
    32	        this.type = TYPE_NAME;
    33	        queueDir = new File(queueBaseDir, name); 
    34	        if (!queueDir.exists() || !queueDir.isDirectory()) {
    35	            boolean ok = queueDir.mkdirs();
    36	            if (!ok) throw new IOException("FileQueue failed to create " + queueDir.toString());
    37	        }
    38	    }
    39	
    40	    public static synchronized void init(String context)
    41	    throws IOException {
    42	        File qd = setQueueDir(context);
    43	
    44	        if (qd == null) throw new IOException("ERROR: unable to FileQueue.setQueueDir()");
    45	        System.out.println("[INFO] FileQueue.init(" + context + ") complete");
    46	    }
    47	
    48	    public static synchronized File setQueueDir(String context)
    49	    throws IOException {
    50	        if (queueBaseDir != null) return queueBaseDir; // hey we have one already!
    51	        String qd = Queue.getProperty(context, "filequeue_basedir");
    52	        if (qd == null) qd = CommonConfiguration.getProperty("ScheduledQueueDir", "context0"); // legacy
    53	        if (qd == null) { // lets try to make one *somewhere*
    54	            queueBaseDir = new File("/tmp/WildbookFileQueue");
    55	            System.out.println("INFO: default (temporary) queueBaseDir being used: " +
    56	                queueBaseDir);
    57	        } else {
    58	            queueBaseDir = new File(qd);
    59	        }
    60	        return queueBaseDir;
    61	    }
    62	
    63	    public void publish(String msg)
    64	    throws IOException {
    65	        if (queueDir == null)
    66	            throw new IOException("FileQueue.publish() failed, queueDir is not set");
    67	        String qid = Util.generateUUID();
    68	        File tmpFile = new File(queueDir, "addToQueue-" + qid + ".tmp"); // write to tmp file first so it doesnt (yet) get picked up by queue til done
    69	        PrintWriter qout = new PrintWriter(tmpFile);
    70	        qout.print(msg);
    71	        qout.close();
    72	        tmpFile.renameTo(new File(queueDir, qid));
    73	        System.out.println("INFO: FileQueue.publish() added " + queueDir + " -> " + qid);
    74	    }
    75	
    76	    public void consume(final QueueMessageHandler msgHandler)
    77	    throws IOException {
    78	        this.messageHandler = msgHandler;
    79	        QueueUtil.background(this);
    80	    }
    81	
    82	    public String getNext()
    83	    throws IOException {
    84	        if (queueDir == null)
    85	            throw new IOException(this.toString() + " FileQueue.getNext() queueDir is null");
    86	        File nextFile = getNextFile();
    87	        if (nextFile == null) return null; // wait and try again...
    88	
    89	        File activeFile = new File(nextFile.toString() + ".active");
    90	        if (activeFile.exists()) {
    91	            System.out.println("WARNING: " + this.toString() + " wanted to create " +
    92	                activeFile.toString() + " but it exists; skipping");
    93	            return null;
    94	        }
    95	        if (!nextFile.renameTo(activeFile)) {
    96	            System.out.println("WARNING: " + this.toString() + " wanted to create " +
    97	                activeFile.toString() + " but rename failed; skipping");
    98	            return null;
    99	        }
   100	        System.out.println("INFO: " + this.toString() + " successfully engaged file " +
   101	            nextFile.toString() + "; made .active");
   102	
   103	        // for now we assume we *only* support json content... fix if you need to, future
   104	        String fcontents = null;
   105	        try {

 succeeded in 262ms:
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

 succeeded in 266ms:
     1	package org.ecocean.queue;
     2	import java.util.Properties;
     3	import org.ecocean.shepherd.core.ShepherdProperties;
     4	
     5	public abstract class Queue {
     6	    protected String type = null;
     7	    protected String queueName = null;
     8	    protected QueueMessageHandler messageHandler = null;
     9	
    10	    protected Queue(final String name) {
    11	        queueName = name;
    12	    }
    13	
    14	    public static synchronized void init(String context)
    15	    throws java.io.IOException {} // override in base class
    16	
    17	    public abstract void publish(String msg)
    18	    throws java.io.IOException;
    19	
    20	    // assumed to detach into background
    21	    public abstract void consume(QueueMessageHandler msgHandler)
    22	    throws java.io.IOException;
    23	
    24	    // this is "internal" and is mostly used for manually backgrounded needs (like FileQueue)
    25	    // NOTE: when used, return of null means messageHandler will NOT be called!
    26	    public abstract String getNext()
    27	    throws java.io.IOException;
    28	
    29	    // this is static and should be overridden
    30	    public static boolean isAvailable(String context) { return false; }
    31	
    32	    public abstract void shutdown();
    33	
    34	    public boolean isConsumerShutdownMessage(String msg) {
    35	        return "SHUTDOWN".equals(msg);
    36	    }
    37	
    38	    public static String getProperty(String context, String label) { // no-default flavor
    39	        return getProperty(context, label, null);
    40	    }
    41	
    42	    public static String getProperty(String context, String label, String def) {
    43	        Properties qp = getProperties(context);
    44	
    45	        if (qp == null) {

 succeeded in 272ms:
    45	                // note message == null means it was read, but there is nothign to handle
    46	                if (cont && (message != null)) {
    47	                    try {
    48	                        ok = queue.messageHandler.handler(message);
    49	                    } catch (IOException ioex) {
    50	                        System.out.println("WARNING: swallowed IOException from message handler: " +
    51	                        ioex.toString());
    52	                    }
    53	                }
    54	// System.out.println("count=" + count + "; handled-ok=" + ok + "; cont=" + cont + "; msg=" + message);
    55	                if (!cont) {
    56	                    System.out.println(":::: " + queue.toString() +
    57	                    " shutdown via discontinue signal ::::");
    58	                    schedExec.shutdown();
    59	                }
    60	            }
    61	        }, 1, // initial delay
    62	            1, // period delay *after* execution finishes
    63	            TimeUnit.SECONDS);
    64	
    65	        runningSES.add(schedExec);
    66	        runningSF.add(schedFuture);
    67	
    68	        System.out.println("---- about to awaitTermination() ----");
    69	        try {
    70	            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
    71	        } catch (java.lang.InterruptedException ex) {
    72	            System.out.println("WARNING: queue interrupted! " + ex.toString());
    73	        }
    74	        System.out.println("==== schedExec.shutdown() called, apparently");
    75	    }
    76	
    77	    // mostly for ContextDestroyed in StartupWildbook..... i think?
    78	    public static void cleanup() {
    79	        for (ScheduledExecutorService ses : runningSES) {
    80	            ses.shutdown();
    81	            try {
    82	                if (ses.awaitTermination(20, TimeUnit.SECONDS)) {
    83	                    ses.shutdownNow();
    84	                    if (ses.awaitTermination(20, TimeUnit.SECONDS)) {
    85	                        System.out.println(
    86	                            "!!! QueueUtil.cleanup() -- ExecutorService did not terminate");
    87	                    }
    88	                }
    89	            } catch (InterruptedException ie) {
    90	                ses.shutdownNow();

exec
/bin/bash -lc "test -f docs/plans/2026-05-18-empty-match-prospects-design.md && nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
 succeeded in 272ms:
     1	# Design: fix empty match prospects on bulk imports (revision 2, post-Codex)
     2	
     3	## Context
     4	
     5	On amphibian-reptile.wildbook.org (ml-service v2 live test deployment),
     6	bulk-imported fire salamanders return 0–5 match prospects per
     7	annotation even when the import contains 7+ same-species annotations
     8	that should match each other. Manual OpenSearch queries a few minutes
     9	later return all the expected hits, so the data lands correctly — but
    10	at match-task time the candidate pool is incomplete.
    11	
    12	This design fixes two linked bugs that together produce the empty
    13	results. Either fix on its own leaves a residual.
    14	
    15	The two bugs:
    16	
    17	1. **WBIA image-then-annotation registration** — the v2 routing path
    18	   skips legacy `WBIA.sendMediaAssets()`, so the annotation-registration
    19	   polling thread fires `/api/annot/json/` against WBIA without WBIA
    20	   knowing about the image. Returns HTTP 500
    21	   `image_uuid_list has invalid values [(0, None)]`. (See existing
    22	   paused design at `2026-05-18-wbia-image-registration-design.md`.)
    23	2. **Match runs before sibling ml-service jobs complete and before
    24	   their annotations are visible in OpenSearch.** Each per-image
    25	   ml-service job calls `MlServiceProcessor.waitAndRunMatch` as soon
    26	   as *it* persists detections; siblings may still be mid-HTTP or
    27	   pre-persist. Even after they persist, OS indexing is async with
    28	   its own refresh interval. The match for any one annotation runs
    29	   against a candidate pool that doesn't yet include the rest of
    30	   the batch.
    31	
    32	## Revision 2 — what changed from v1
    33	
    34	Codex review of v1 returned 2 blockers, 3 majors, 1 medium. Summary
    35	of changes:
    36	
    37	- **Track 2 reshaped into a two-phase gate** (was: single-phase
    38	  visibility expansion). Phase 1 waits for sibling media-asset
    39	  detection terminal states. Phase 2 collects eligible annotations
    40	  and waits for OS visibility. v1 conflated the two and would have
    41	  expanded to annotations that don't exist yet.
    42	- **Batch boundary changed from ImportTask to the per-asset Task's
    43	  parent** (`topTask` from `IA.enqueueOneAssetForMlService`,
    44	  `IA.java:281-287`). ImportTask is mixed-species and the
    45	  `ImportTask → Encounter → Annotation` join leaks placeholder
    46	  annotations with `skipAutoIndexing=true`. topTask is one
    47	  same-species MA group from one routing call — tighter and
    48	  semantically correct.
    49	- **Eligibility filter added to the annotation set** (was: no
    50	  filter). Without `matchAgainst=true AND acmId IS NOT NULL AND
    51	  has-embedding-for-method`, the expansion picks up bulk-import
    52	  placeholder annotations that never reach OS.
    53	- **`runDeferredMatch` reuses the same gate** with an attempt
    54	  counter and max-age (was: bypassed visibility wait entirely).
    55	- **Testability seam added**: extract a
    56	  `MatchVisibilityGate` collaborator so unit tests can mock without
    57	  live JDO/OpenSearch.
    58	- **Track 1 cache-naming cleanup**: pick `iaImageIdsStrict`
    59	  consistently across both docs; also invalidate `iaAnnotationIds`
    60	  after successful annotation POST.
    61	
    62	## Goal
    63	
    64	Restore the user-expected behavior: when I upload N same-species
    65	photos and the import completes, the per-annotation match results
    66	include the other N-1 annotations from my upload (when they match
    67	visually) plus any pre-existing candidates from the corpus. WBIA
    68	stays in sync as a HotSpotter fallback.
    69	
    70	## Non-goals
    71	
    72	- Detection-time WBIA image registration. Same rationale as the
    73	  paused WBIA design — handling retroactively in the polling
    74	  thread keeps the intake fast path simple.
    75	- Restructuring ml-service to batch detection. Per-image jobs are
    76	  the natural granularity for v2; batching belongs at the match
    77	  invocation seam, not detection.
    78	- Moving away from `waitForVisibility`. It works correctly — its
    79	  call-site scope and pre-conditions are the bug, not the helper.
    80	- A barrier task type / new dispatcher branch. We solve this at the
    81	  existing `waitAndRunMatch` seam plus a small collaborator.
    82	
    83	## Audit: what already exists
    84	
    85	| Helper | File:line | Status |
    86	|---|---|---|
    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
    88	| `OpenSearch.indexRefresh(indexName)` | `OpenSearch.java:468` | Synchronous refresh helper. |
    89	| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Per-job wrapper; current implementation widens nothing. Falls back to `enqueueDeferredMatch` on timeout. |
    90	| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
    91	| `MlServiceProcessor.runDeferredMatch(jobData)` | `MlServiceProcessor.java:433` | Today: direct `runMatchProspects`. Bug: no re-gate. |
    92	| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues via IAGateway with `mlServiceV2DeferredMatch: true`. Today: no attempt counter. |
    93	| `IA.enqueueOneAssetForMlService` creates per-asset `Task childTask = new Task(topTask)` | `IA.java:281-287` | Per-asset child has parent=topTask; topTask owns `objectMediaAssets` (the same-species batch). |
    94	| `MediaAsset.getDetectionStatus()` | per JDO | Lifecycle: `_new` → `_post_new` → `processing` → `complete` \| `error` \| `pending`. |
    95	| `Embedding.findMatchProspects(iaConfig, task, shepherd)` | `Embedding.java:265` | Per-annotation knn + matchingSetQuery on `task.getObjectAnnotations()`. |
    96	| `QueryCache.invalidateByName(name)` | `QueryCache.java:46` | Right API for cache invalidation (not "invalidate"). |
    97	| `WildbookIAM` helpers — `iaImageIds`, `sendMediaAssetsForceId`, `mediaAssetToUri`, `validMediaAsset` | `WildbookIAM.java` | See existing WBIA design doc. |
    98	
    99	Note: `findMatchProspects` already loops `task.getObjectAnnotations()`
   100	correctly — the matching itself is fine. The bug is the **gate** in
   101	front of it.
   102	
   103	## Design
   104	
   105	### Track 1 — WBIA image-then-annotation registration
   106	
   107	No structural change from
   108	`docs/plans/2026-05-18-wbia-image-registration-design.md`. Codex
   109	follow-ups to address before implementation:
   110	
   111	1. `mediaAssetToUri(MediaAsset)` is `private`. Promote to
   112	   `public static String` (returning `String`, tightening
   113	   `ma.webURL()` null-check).
   114	2. New strict variant `iaImageIdsStrict` with 15-min QueryCache
   115	   (matching `iaAnnotationIdsStrict` from c11 fix-pass). Lenient
   116	   `iaImageIds` stays cache-free for backward compatibility.
   117	3. Phase A eligibility check gains `ma.isValidImageForIA() != null
   118	   && !ma.isValidImageForIA()` AND `WildbookIAM.validMediaAsset(ma)`
   119	   (in the order `sendMediaAssetsForceId` uses them at
   120	   `WildbookIAM.java:121-130`). If either fails, park at
   121	   `MAX_ATTEMPTS`.
   122	4. Phase B does NOT re-validate MA eligibility. Documented as an
   123	   explicit decision: MA validity does not change between Phase A
   124	   commit and Phase B HTTP. One-line comment in code.
   125	5. `parseImageIdsArrayStrict` is added alongside
   126	   `parseAnnotationIdsArrayStrict`. A shared
   127	   `parseFancyUuidListStrict(jids, ctxLabel)` helper extracts the
   128	   commonality. Both go through `fromFancyUUID`.
   129	6. No new outcome enum value. Image-registration failures reuse
   130	   the existing `NETWORK_FAIL` outcome; Phase C log line
   131	   distinguishes phase via existing log string (Phase 0 / 1 / 2).
   132	7. `WbiaRegisterRequest` gains four image-side fields, captured in
   133	   Phase A:
   134	
   135	```java
   136	public final String imageUri;
   137	public final Double imageLatitude;
   138	public final Double imageLongitude;
   139	public final Long imageDateTimeMillis;
   140	```
   141	
   142	`mediaAssetUuid` is not a separate field —
   143	`MediaAsset.acmId == MediaAsset.uuid` (commit `2a3eab63a`), so
   144	`dto.mediaAssetAcmId` already carries the value
   145	`sendMediaAssetsForceId` puts in `image_uuid_list`.
   146	
   147	#### Cache-name fix (Codex Medium 6)
   148	
   149	- The combined doc and WBIA doc both standardize on
   150	  **`iaImageIdsStrict`** as the strict cache key.
   151	- After successful image POST in Phase 0, call
   152	  `QueryCacheFactory.getQueryCache(context).invalidateByName("iaImageIdsStrict")`.
   153	  This is the correct API at `QueryCache.java:46` (NOT `invalidate(name)`).
   154	- Also invalidate `iaAnnotationIdsStrict` after successful
   155	  annotation POST in Phase 1, to prevent stale-cache duplicate
   156	  POSTs if Phase C races or fails between attempts.
   157	
   158	### Track 2 — two-phase match gate
   159	
   160	#### Boundary: per-asset Task's parent (topTask)
   161	
   162	`IA.enqueueOneAssetForMlService` at `IA.java:281-287` creates each
   163	per-asset job as `Task childTask = new Task(topTask)`. The
   164	**topTask** owns `objectMediaAssets` — the same-species batch from
   165	one routing call. This is the right boundary because:
   166	
   167	- It's same-species (ImportTask is mixed-species).
   168	- It's set-membership-explicit via `objectMediaAssets` (no fuzzy
   169	  join through ENCOUNTER that catches placeholders).
   170	- It's the batch the user submitted together; intra-batch match
   171	  expectations align with topTask membership.
   172	
   173	In `waitAndRunMatch`, given the child Task's id, load the child,
   174	walk up to its parent (topTask), and read `topTask.getObjectMediaAssets()`.
   175	
   176	#### Phase 1: MA terminal-state gate
   177	
   178	For each sibling MA in `topTask.getObjectMediaAssets()`, check
   179	`ma.getDetectionStatus()`:
   180	
   181	| Status | Meaning | Terminal? |
   182	|---|---|---|
   183	| `_new` | created, not yet picked up | no |
   184	| `_post_new` | post-new transition | no |
   185	| `processing` | ml-service in flight | no |
   186	| `complete` | detections persisted | **yes** |
   187	| `error` | ml-service failed for this MA | **yes (no annotations to wait for)** |
   188	| `pending` | needs human review | **yes (no annotations to wait for)** |
   189	
   190	If any sibling MA is non-terminal, **defer**: re-queue via
   191	`enqueueDeferredMatch` with an incremented attempt counter (see
   192	deferred-path section below). Caller's per-annotation match does
   193	NOT run yet.
   194	
   195	If all siblings are terminal, proceed to Phase 2.
   196	
   197	#### Phase 2: collect eligible annotation IDs
   198	
   199	For each sibling MA in topTask whose detectionStatus is `complete`
   200	(i.e., contributed annotations), enumerate annotations through
   201	`MediaAsset → Feature → Annotation`, filtered for ml-service match
   202	eligibility:
   203	
   204	```sql
   205	-- Sketch; JDOQL equivalent in code.
   206	SELECT DISTINCT a."ID"
   207	FROM "ANNOTATION" a
   208	JOIN "ANNOTATION_FEATURES" af ON af."ID_OID" = a."ID"
   209	JOIN "MEDIAASSET_FEATURES" mf ON mf."ID_EID" = af."ID_EID"
   210	JOIN "MEDIAASSET" ma ON ma."ID" = mf."ID_OID"
   211	JOIN "EMBEDDING" e ON e."ANNOTATION_ID" = a."ID"
   212	WHERE ma."ID" IN (<topTask sibling MA ids>)
   213	  AND a."MATCHAGAINST" = true
   214	  AND a."ACMID" IS NOT NULL
   215	  AND e."METHOD" = ?
   216	  AND e."METHODVERSION" = ?
   217	```
   218	
   219	The `EMBEDDING JOIN ... METHOD = ?` clause is the key filter — it
   220	ensures we only wait for annotations that actually have an
   221	embedding of the model we're matching against. This excludes:
   222	- Bulk-import placeholder annotations created by `BulkImporter`
   223	  (no embedding at all, never indexed in OS due to
   224	  `skipAutoIndexing`).
   225	- Legacy annotations from a different embedding method.
   226	- Annotations whose ml-service extraction failed
   227	  (`predictModelId` set but `EMBEDDING` row never created).
   228	
   229	This is a single `SELECT DISTINCT` query, no materialization of
   230	Annotation objects. Returns a `Set<String>` of annotation IDs.
   231	
   232	#### Phase 3: OS visibility wait on the eligible set
   233	
   234	`os.waitForVisibility("annotation", eligibleIds, VISIBILITY_TIMEOUT_MS)`.
   235	
   236	If returns false (timeout), **defer**: re-queue with attempt
   237	counter increment.
   238	
   239	#### Phase 4: run match
   240	
   241	`runMatchProspects(callerAnnotationIds, taskId, matchConfig)` —
   242	**unchanged**. The caller's own annotation IDs drive the match;
   243	the gate only ensures the OS corpus is populated.
   244	
   245	#### Pseudocode
   246	
   247	```java
   248	private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
   249	    String taskId, JSONObject matchConfig) {
   250	
   251	    GateOutcome g = visibilityGate.gateForBatch(taskId, matchConfig);
   252	    switch (g.kind) {
   253	      case READY:
   254	        return runMatchProspects(annotationIds, taskId, matchConfig);
   255	      case DEFER:
   256	        enqueueDeferredMatch(annotationIds, taskId, g.attempt);
   257	        return MlServiceJobOutcome.ok(annotationIds);
   258	      case GIVE_UP:
   259	        // Max attempts reached; run match against whatever is visible
   260	        // rather than block forever. This is a degraded outcome but

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '245,430p'" in /mnt/c/Wildbook-clean2
 succeeded in 262ms:
   245	#### Pseudocode
   246	
   247	```java
   248	private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
   249	    String taskId, JSONObject matchConfig) {
   250	
   251	    GateOutcome g = visibilityGate.gateForBatch(taskId, matchConfig);
   252	    switch (g.kind) {
   253	      case READY:
   254	        return runMatchProspects(annotationIds, taskId, matchConfig);
   255	      case DEFER:
   256	        enqueueDeferredMatch(annotationIds, taskId, g.attempt);
   257	        return MlServiceJobOutcome.ok(annotationIds);
   258	      case GIVE_UP:
   259	        // Max attempts reached; run match against whatever is visible
   260	        // rather than block forever. This is a degraded outcome but
   261	        // not a regression vs. today's behavior.
   262	        return runMatchProspects(annotationIds, taskId, matchConfig);
   263	    }
   264	}
   265	```
   266	
   267	`MatchVisibilityGate.gateForBatch(taskId, matchConfig)` returns
   268	`READY` if Phase 1 + 2 + 3 all succeed, `DEFER` if any phase says
   269	"wait", `GIVE_UP` if attempt counter exceeds max.
   270	
   271	#### Deferred-match path (`runDeferredMatch`)
   272	
   273	Today at `MlServiceProcessor.java:433-442`,
   274	`runDeferredMatch` reads `annotationIds` + `taskId` + `matchConfig`
   275	from the job payload and calls `runMatchProspects` directly. We
   276	change it to call the same gate:
   277	
   278	```java
   279	public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
   280	    if (jobData == null) { ... }
   281	    List<String> annotationIds = ...;
   282	    String taskId = ...;
   283	    JSONObject matchConfig = ...;
   284	    int attempt = jobData.optInt("attempt", 1);
   285	    if (attempt > MAX_DEFER_ATTEMPTS) {
   286	        // age-out: run against whatever is visible
   287	        return runMatchProspects(annotationIds, taskId, matchConfig);
   288	    }
   289	    // re-gate; deferred match earns the same protection as initial.
   290	    return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, attempt);
   291	}
   292	```
   293	
   294	`enqueueDeferredMatch` is extended to take an `attempt` parameter,
   295	written into the job payload. New constants:
   296	
   297	```java
   298	private static final int MAX_DEFER_ATTEMPTS = 10;
   299	// Deferred re-fire delay scales with attempt; first deferral ~30s,
   300	// last ~5min, matching the WBIA register polling thread's cadence.
   301	```
   302	
   303	The age-out at `attempt > MAX_DEFER_ATTEMPTS` is a degraded outcome
   304	(matches against whatever is visible) but bounded so a stuck
   305	sibling cannot block the user's match indefinitely.
   306	
   307	#### Testability seam — `MatchVisibilityGate`
   308	
   309	Today `waitAndRunMatch` is private and constructs `new OpenSearch()`
   310	directly, and `enqueueDeferredMatch` is private. Codex review item
   311	5 calls this out: mock/wrap unit tests are not feasible cleanly
   312	without changing the seam.
   313	
   314	Extract:
   315	
   316	```java
   317	package org.ecocean.ia;
   318	
   319	public interface MatchVisibilityGate {
   320	    enum Kind { READY, DEFER, GIVE_UP }
   321	
   322	    static class GateOutcome {
   323	        public final Kind kind;
   324	        public final int attempt;        // current attempt number
   325	        public final String reason;      // for logs
   326	        // factory methods: ready(), defer(attempt, reason), giveUp(reason)
   327	    }
   328	
   329	    GateOutcome gateForBatch(String childTaskId, JSONObject matchConfig);
   330	    GateOutcome gateForBatch(String childTaskId, JSONObject matchConfig, int attempt);
   331	}
   332	```
   333	
   334	Real implementation `MatchVisibilityGateImpl` constructs Shepherd +
   335	OpenSearch as needed; takes `context` in constructor.
   336	
   337	`MlServiceProcessor` holds `private final MatchVisibilityGate
   338	visibilityGate`. Default constructor uses
   339	`MatchVisibilityGateImpl`; a package-visible constructor accepts
   340	an injected gate for tests.
   341	
   342	Unit tests can now:
   343	- Stub `gateForBatch` to return READY → assert `runMatchProspects` is
   344	  called with caller's IDs.
   345	- Stub `gateForBatch` to return DEFER → assert `enqueueDeferredMatch`
   346	  is called with attempt counter.
   347	- Stub `gateForBatch` to return GIVE_UP → assert match runs anyway
   348	  (age-out behavior).
   349	- Test the gate implementation separately with a mock OpenSearch
   350	  + Shepherd-test-double.
   351	
   352	Also: extract the JDOQL "find eligible annotations for a topTask"
   353	into a package-visible static method on `Annotation` or a new
   354	`MatchEligibilityQuery` utility, so it can be unit-tested with a
   355	DataNucleus in-memory store (the v2 pattern from c8/c9 tests).
   356	
   357	#### Behavior in edge cases
   358	
   359	- **Child Task has no parent (orphaned single-asset job from
   360	  reconciler)**: gate degrades to "no batch to wait for; just OS
   361	  visibility on caller's IDs". Equivalent to today's behavior for
   362	  this path. No regression.
   363	- **Sibling MA permanently stuck in `processing`**: bounded by
   364	  `MAX_DEFER_ATTEMPTS`. Age-out after ~50min (10 attempts ×
   365	  growing delay). User's match runs against whatever's visible.
   366	- **All siblings are `error` / `zero_detections`**: Phase 2's
   367	  eligibility filter returns the empty set or only the caller's
   368	  own annotations. `waitForVisibility` on empty set should be
   369	  short-circuit (or no-op); confirm behavior in tests.
   370	- **Two parallel users with overlapping imports**: topTask is
   371	  per-routing-call, so each user's batch is its own topTask.
   372	  Boundary is clean.
   373	- **Caller's annotation isn't in the eligible set** (somehow):
   374	  union it in. The gate should never block the caller's match on
   375	  its own visibility — the caller's annotation went through
   376	  `waitForVisibility` in the old single-annotation path
   377	  effectively; we maintain that property by always including
   378	  caller's IDs in the visibility set, eligibility filter or not.
   379	
   380	## Interaction between Track 1 and Track 2
   381	
   382	The two tracks are independent in code (different files, different
   383	review chains) but linked in symptom. Track 1 fixes acmId/HotSpotter
   384	sync — annotations no longer linger with placeholder acmIds in OS.
   385	Track 2 fixes the timing where annotations are visible in DB but
   386	not yet in OS at match time.
   387	
   388	Specifically: today, a newly-imported annotation gets a placeholder
   389	acmId (its own UUID), gets indexed into OS that way, then later
   390	`AcmUtil.rectifyAnnotationIds` mutates it to WBIA's UUID and
   391	re-indexes. Match tasks firing between initial index and rectify
   392	reindex see the placeholder acmId — which still passes the
   393	`exists: {field: acmId}` filter, so it's not a filter regression.
   394	Track 1's effect on the empty-prospects symptom is indirect (via
   395	HotSpotter fallback availability and operational hygiene). Track 2
   396	is the load-bearing fix for the empty-prospects symptom.
   397	
   398	## Order of work
   399	
   400	1. **Track 1**: resolve the existing WBIA design doc's Codex
   401	   follow-ups (items 1–7 above + cache-name fix), implement, code-review
   402	   pass.
   403	2. **Track 2**: implement after re-reviewing this design with Codex.
   404	
   405	Track 2 is larger than v1's estimate (~150 lines instead of ~80
   406	once the gate, helpers, and attempt counter are included). Two
   407	commits within Track 2 is reasonable: one for the gate +
   408	collaborator extraction, one for the deferred-match path + age-out.
   409	
   410	## Test strategy
   411	
   412	### Track 1
   413	
   414	`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
   415	behavior, strict-cache invalidation, the strict parser, DTO image
   416	fields. Per existing WBIA design.
   417	
   418	### Track 2
   419	
   420	New unit tests in `MatchVisibilityGateImplTest` and updates to
   421	`MlServiceProcessorTest`:
   422	
   423	- `gate_returnsReady_whenAllSiblingMasAreTerminalAndOsVisible`
   424	- `gate_returnsDefer_whenSiblingMaIsStillProcessing`
   425	- `gate_returnsDefer_whenEligibleIdsNotYetInOs`
   426	- `gate_returnsGiveUp_whenAttemptExceedsMax`
   427	- `gate_excludesPlaceholderAnnotations_filtersByEmbeddingMethod` —
   428	  set up an annotation with no EMBEDDING row, prove it's filtered
   429	  out.
   430	- `gate_excludesAnnotationsWithSkipAutoIndexing` — set up an

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '430,540p'" in /mnt/c/Wildbook-clean2
 succeeded in 272ms:
   430	- `gate_excludesAnnotationsWithSkipAutoIndexing` — set up an
   431	  annotation in `BulkImporter`'s placeholder state, prove it's
   432	  filtered out (transitively via the embedding filter; this is a
   433	  double-check that placeholders never have embeddings of our
   434	  method).
   435	- `gate_handlesOrphanChildTaskWithoutParent` — child task with no
   436	  parent; gate degrades cleanly.
   437	- `gate_unionsCallersIdsIntoVisibilitySet` — caller's annotation
   438	  always present in the wait set even if eligibility filter
   439	  excluded it.
   440	- `processor_callsRunMatchProspects_onlyAfterGateReady`
   441	- `processor_callsEnqueueDeferredMatch_onGateDefer_withAttemptCounter`
   442	- `processor_runsMatchAnyway_onGateGiveUp`
   443	- `runDeferredMatch_reGatesBeforeRunning`
   444	- `runDeferredMatch_agesOutAtMaxAttempts`
   445	- `enqueueDeferredMatch_writesAttemptCounterToJobPayload`
   446	
   447	Plus one end-to-end harness against the live deployment:
   448	import 4 same-species images, observe that each annotation's
   449	`findMatchProspects` result includes the other 3 as candidates.
   450	
   451	## Open questions for Codex (round 2)
   452	
   453	1. **`MAX_DEFER_ATTEMPTS=10` reasonable**, given the WBIA register
   454	   thread also uses 10? Should it scale with batch size (larger
   455	   batches deserve more retries)?
   456	2. **Age-out posture**: at GIVE_UP, do we run match anyway
   457	   (current draft) or mark the match Task as error? Running-anyway
   458	   matches the user-facing intent ("I'd rather see partial results
   459	   than nothing"); error matches strict semantic correctness ("the
   460	   intra-batch match was impossible to compute"). My instinct:
   461	   run-anyway, log a WARN, but flag for Codex.
   462	3. **MA terminal-state set**: is `pending` (needs review) really
   463	   terminal for the purposes of this gate? An MA in `pending`
   464	   contributed annotations (or didn't) — the visibility filter
   465	   handles that. But should we WAIT for `pending` to be resolved
   466	   by a human? My instinct: no, treat `pending` as terminal for
   467	   the gate; matches today's behavior. Flag for Codex.
   468	4. **Should the gate also wait for `skipAutoIndexing` to be off**
   469	   on the eligible-ann set? The embedding-method filter should
   470	   already exclude placeholders, but if a Phase 4 race exists
   471	   where the user toggles `skipAutoIndexing` mid-import, we could
   472	   end up waiting for nothing. My instinct: don't over-engineer,
   473	   trust the embedding-method filter.
   474	5. **JDOQL vs. direct SQL** for `findEligibleAnnotationIdsForBatch`:
   475	   v2 has used JDOQL for portability. Is there a precedent in
   476	   this codebase for direct SQL when the join goes through
   477	   ANNOTATION_FEATURES + MEDIAASSET_FEATURES + EMBEDDING in one
   478	   query? My instinct: JDOQL with `containsValue` / `EXISTS`
   479	   subquery. Flag if this gets unwieldy.
   480	6. **Deferred-match attempt counter location**: write into the
   481	   `jobData` payload, or onto the Task itself? Today the Task
   482	   doesn't have a counter field; adding one is a JDO schema
   483	   change. Job payload is simpler but ephemeral. My instinct:
   484	   job payload (ephemeral is fine — the Task already records
   485	   `mlServiceV2DeferredMatch: true`).
   486	7. **Anything else** — design smells, sequencing bugs, test
   487	   gaps, missing failure cases.
   488	
   489	## Cross-references
   490	
   491	- v1 of this design (superseded): see git history for
   492	  `2026-05-18-empty-match-prospects-design.md`.
   493	- Codex review of v1:
   494	  `docs/plans/2026-05-18-empty-match-prospects-codex-review.md`.
   495	- Existing WBIA registration design:
   496	  `docs/plans/2026-05-18-wbia-image-registration-design.md`.
   497	- ml-service migration v2 plan:
   498	  `docs/plans/2026-05-09-ml-service-migration-v2.md`.
   499	- waitForVisibility introduction commit: `f429c5bf8` (c7).
   500	- WBIA registration polling thread origin: `c6ffe5d20` (c11).
   501	- Key helpers:
   502	  - `MlServiceProcessor.waitAndRunMatch` —
   503	    `src/main/java/org/ecocean/ia/MlServiceProcessor.java:418`
   504	  - `MlServiceProcessor.runDeferredMatch` —
   505	    `src/main/java/org/ecocean/ia/MlServiceProcessor.java:433`
   506	  - `OpenSearch.waitForVisibility` —
   507	    `src/main/java/org/ecocean/OpenSearch.java:498`
   508	  - `IA.enqueueOneAssetForMlService` (topTask boundary) —
   509	    `src/main/java/org/ecocean/ia/IA.java:281`
   510	  - `QueryCache.invalidateByName` —
   511	    `src/main/java/org/ecocean/cache/QueryCache.java:46`
   512	  - `Annotation.getMatchingSetQuery` (eligibility alignment) —
   513	    `src/main/java/org/ecocean/Annotation.java:925`
   514	  - `Embedding.findMatchProspects` —
   515	    `src/main/java/org/ecocean/Embedding.java:265`
   516	  - `BulkImporter` placeholder annotations
   517	    (`skipAutoIndexing=true`) —
   518	    `src/main/java/org/ecocean/api/bulk/BulkImporter.java:703`
   519	
   520	**Do not write to any file when Codex reviews this.** Review-only.

exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-wbia-image-registration-design.md | sed -n '180,236p'" in /mnt/c/Wildbook-clean2
 succeeded in 267ms:
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
   221	
   222	### Cache invalidation note
   223	
   224	After a successful Phase 0 POST, we must invalidate the `iaImageIds`
   225	QueryCache entry. Otherwise the next annotation on the same image (within
   226	15 minutes) would still see the cached image-ids list (which didn't
   227	include this image), trigger Phase 0 again, attempt to register the
   228	already-registered image (skip-if-present inside `sendMediaAssetsForceId`
   229	would no-op, but it's wasted work), and worse — *if a different
   230	annotation needs the same image*, we'd repeat the dance per annotation.
   231	
   232	Verify QueryCache has an invalidation API. If not, fall back to direct
   233	removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
   234	Worst case: skip invalidation and accept that the next ~30 polling ticks
   235	per image will see stale cache. Trade-off is minor since the cache TTL is
   236	only 15 minutes and `sendMediaAssetByDto` is a single fast POST.

codex
Review-only. I did not write files.

**Findings**
- **Blocker:** the proposed `MatchVisibilityGate` API cannot implement two promised behaviors. The pseudocode calls `gateForBatch(taskId, matchConfig)` without `annotationIds` ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:251>)), but later says the gate unions caller IDs and handles orphan child tasks by waiting on caller IDs ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:373>)). Pass `Collection<String> callerAnnotationIds` into the gate.

- **Blocker/Major:** the terminal status table is incomplete for current v2. Actual statuses include `processing-mlservice`, `complete-mlservice`, and `pending-species` ([IBEISIA.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:73>)). Also, several failure paths mark the child `Task` error without setting `MediaAsset.detectionStatus = error` ([MlServiceProcessor.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:95>)). Phase 1 should consider sibling child task terminal states, not only MA status.

- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.

- **Major:** make the Shepherd boundary explicit in `MatchVisibilityGateImpl`. It should load task/status/eligible IDs under Shepherd, close it, then call OpenSearch. Do not hold a DB transaction through the visibility poll.

- **Major:** deferred timing is underspecified. `enqueueDeferredMatch` currently publishes immediately through `addToDetectionQueue` ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:568>)); existing `requeueJob` only gives a fixed 30s delay on increment ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785>)). Define the actual backoff mechanism and attempt semantics before coding.

- **Medium:** Track 1 cache naming is still inconsistent with current code. `iaAnnotationIdsStrict` currently uses cache name `"iaAnnotationIds"` ([WildbookIAM.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477>)), while this design says invalidate `"iaAnnotationIdsStrict"` ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:154>)). The WBIA design doc still has stale `invalidate("iaImageIds")` text ([wbia doc](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-wbia-image-registration-design.md:190>)).

**Open Questions**
1. `MAX_DEFER_ATTEMPTS=10`: reasonable only if paired with a real max-age/deferred-since timestamp. I would not scale by batch size; scale by elapsed time and maybe log batch size for diagnosis.

2. Age-out: run anyway, with a WARN and task status detail. Partial results are better than silently no match task.

3. `pending`: yes, terminal for this gate. Also treat `pending-species`, `complete`, `complete-mlservice`, `error`, and terminal child task errors as terminal. Treat `_new`, `_post_new`, `initiated`, `processing`, `processing-mlservice`, and null as non-terminal unless the child task is terminal.

4. `skipAutoIndexing`: do not wait for it to turn off. The existing WARN is enough for dev mode; production should not run this path with the flag set.

5. JDOQL vs direct SQL: use direct SQL here. There is plenty of SQL precedent in this repo, and this join is clearer as SQL. Parameterize/validate inputs and handle null `methodVersion` by omitting that predicate or using `IS NULL`, matching `getMatchQuery` behavior.

6. Attempt counter: job payload is right. Add `attempt`, `firstDeferredAt`, and `lastGateReason`; avoid a schema change.

**Test Gaps**
Add tests for: caller IDs reaching the gate API, child task error with MA still `processing-mlservice`, stale OS doc visible by `_id` but missing nested embedding, null/absent methodVersion, and deferred backoff/attempt off-by-one behavior.

Bottom line: the topTask boundary and DB eligibility filter are the right direction, but Track 2 needs the API/status/visibility fixes above before implementation.
tokens used
286,582
Review-only. I did not write files.

**Findings**
- **Blocker:** the proposed `MatchVisibilityGate` API cannot implement two promised behaviors. The pseudocode calls `gateForBatch(taskId, matchConfig)` without `annotationIds` ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:251>)), but later says the gate unions caller IDs and handles orphan child tasks by waiting on caller IDs ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:373>)). Pass `Collection<String> callerAnnotationIds` into the gate.

- **Blocker/Major:** the terminal status table is incomplete for current v2. Actual statuses include `processing-mlservice`, `complete-mlservice`, and `pending-species` ([IBEISIA.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:73>)). Also, several failure paths mark the child `Task` error without setting `MediaAsset.detectionStatus = error` ([MlServiceProcessor.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:95>)). Phase 1 should consider sibling child task terminal states, not only MA status.

- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.

- **Major:** make the Shepherd boundary explicit in `MatchVisibilityGateImpl`. It should load task/status/eligible IDs under Shepherd, close it, then call OpenSearch. Do not hold a DB transaction through the visibility poll.

- **Major:** deferred timing is underspecified. `enqueueDeferredMatch` currently publishes immediately through `addToDetectionQueue` ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:568>)); existing `requeueJob` only gives a fixed 30s delay on increment ([IAGateway.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:785>)). Define the actual backoff mechanism and attempt semantics before coding.

- **Medium:** Track 1 cache naming is still inconsistent with current code. `iaAnnotationIdsStrict` currently uses cache name `"iaAnnotationIds"` ([WildbookIAM.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477>)), while this design says invalidate `"iaAnnotationIdsStrict"` ([design](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-empty-match-prospects-design.md:154>)). The WBIA design doc still has stale `invalidate("iaImageIds")` text ([wbia doc](</mnt/c/Wildbook-clean2/docs/plans/2026-05-18-wbia-image-registration-design.md:190>)).

**Open Questions**
1. `MAX_DEFER_ATTEMPTS=10`: reasonable only if paired with a real max-age/deferred-since timestamp. I would not scale by batch size; scale by elapsed time and maybe log batch size for diagnosis.

2. Age-out: run anyway, with a WARN and task status detail. Partial results are better than silently no match task.

3. `pending`: yes, terminal for this gate. Also treat `pending-species`, `complete`, `complete-mlservice`, `error`, and terminal child task errors as terminal. Treat `_new`, `_post_new`, `initiated`, `processing`, `processing-mlservice`, and null as non-terminal unless the child task is terminal.

4. `skipAutoIndexing`: do not wait for it to turn off. The existing WARN is enough for dev mode; production should not run this path with the flag set.

5. JDOQL vs direct SQL: use direct SQL here. There is plenty of SQL precedent in this repo, and this join is clearer as SQL. Parameterize/validate inputs and handle null `methodVersion` by omitting that predicate or using `IS NULL`, matching `getMatchQuery` behavior.

6. Attempt counter: job payload is right. Add `attempt`, `firstDeferredAt`, and `lastGateReason`; avoid a schema change.

**Test Gaps**
Add tests for: caller IDs reaching the gate API, child task error with MA still `processing-mlservice`, stale OS doc visible by `_id` but missing nested embedding, null/absent methodVersion, and deferred backoff/attempt off-by-one behavior.

Bottom line: the topTask boundary and DB eligibility filter are the right direction, but Track 2 needs the API/status/visibility fixes above before implementation.
