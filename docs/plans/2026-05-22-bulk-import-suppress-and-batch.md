# Bulk-import suppress-and-batch match — design + Codex findings

**Status:** Locked design after 2 rounds of Codex review. NOT
implemented; this doc captures the design so a focused session can
ship it. Stopgap mitigation (commit `75c1452b`) reduced gate-defer
cadence from 30s to 5s on first attempt for `deferredMatch:true`
payloads — buys ~6x faster convergence on the existing gate path
but does not eliminate the 12-minute ceiling for very large imports.

## Problem

`MatchVisibilityGate` defers per-encounter match attempts during a
bulk import until all sibling MAs are terminal AND visible in
OpenSearch. For N-encounter imports the result is N separate gate
cycles, each requeueing on `IAGateway.requeueJob` (30s sleep before
the stopgap; 5s on first attempt after it). For ~1000-encounter
imports the total intake time exceeds the gate's
`MAX_DEFER_AGE_MILLIS = 12 * 60 * 1000` (`MatchVisibilityGate.java:54`)
and early-finishing encounters `GIVE_UP` with partial galleries —
silent incomplete matches.

The gate's "wait for siblings" correctness work IS load-bearing for
single-encounter live submissions. For bulk imports we want a
different shape: detection + extraction for all N encounters with no
match attempts during the import, then ONE batch match pass after
intake completes. Gallery is known-stable by then; matching is ms
per query; no defer cycles; no 12-minute ceiling clipping
early-finishers.

## Locked design decisions (per user, 2026-05-22)

1. **Scope:** explicit `bulkImportBatchMode:true` flag, bulk imports
   only. Single-encounter live path keeps the gate.
2. **Trigger:** last-finisher with atomic DB conditional UPDATE on
   `Task.matchEnqueued`. Race-safe across concurrent finalizers.
3. **Failures:** best-effort per annotation. Skip + log on no
   embedding; per-annotation try/catch on match failures; batch keeps
   going.
4. **Safety-net:** 12-min ceiling via a durable, self-requeueing
   deadline job. Survives server restart (lives on FileQueue).

## Actual code path (verified during design review)

```
BulkImport.initiateIA() (api/BulkImport.java:796)
└── IAGateway.handleBulkImport(jin, ...) (servlet/IAGateway.java:871)
    Creates parentTask + a child `task`; enqueues one IA job with
    {mediaAssetIds[], v2:true, taskId=task.id} on the generic IA
    queue.
└── IAGateway dispatcher → IA.handleRest(jobj) (ia/IA.java:520)
    Calls IA.intakeMediaAssets() per species (line 552). Note: the
    `bulkImport` flag from the original jin is REMOVED before this
    enqueue (IAGateway.java:908) -- a distinct key name is required.
└── IA.intakeMediaAssets → intakeMediaAssetsOneSpeciesMlService (line 242)
    Creates species-level topTask (line 148). For vector-pipeline
    species, calls enqueueOneAssetForMlService per MA.
└── IA.enqueueOneAssetForMlService (line 281)
    Creates per-MA child Task UNDER the species-level topTask; queues
    a job with {mediaAssetId, mlServiceV2:true, taskId=childTask.id}
    on the detection queue.
└── MlServiceProcessor.process() (ia/MlServiceProcessor.java:80)
    Dispatches by payload shape. mediaAssetId → detection branch
    (line 121). After persistDetections (which writes Embedding for
    the new annotation, lines 376-378), calls waitAndRunMatch
    (line 124-127).
```

Task topology for a single-species bulk import:

```
parentTask (per bulk import, handleBulkImport:886)
└── task (handleBulkImport:900)
    └── topTask (species-level, IA.intakeMediaAssets:148)
        ├── child task per MA (enqueueOneAssetForMlService:283)
        ├── child task per MA
        ├── ...
```

"All siblings" for the suppress-and-batch boundary = all children
under the species-level topTask. Matches what
`MatchVisibilityGateImpl.gateForBatch` already enumerates
(`MatchVisibilityGateImpl.java:239-267`).

For mixed-species bulk imports each species' topTask drives its own
batch — correct because matchConfig is per-species.

## Files to touch

| File | Change |
|---|---|
| `src/main/java/org/ecocean/servlet/IAGateway.java` | `handleBulkImport`: ensure `bulkImportBatchMode:true` reaches the queued task's parameters. NOTE: `parentTask.addParameter(...)` does NOT propagate to the queued `task` because `task.setParameters(taskParameters)` reads the original jin field, not parentTask. Must mutate `taskParameters` (the JSONObject) BEFORE both setParameters calls, or set on the queued `task` directly. Dispatcher: add branches for `bulkImportBatchMatch:true` and `bulkImportDeadline:true` payloads (route to MlServiceProcessor). The existing routing condition for "send to detection queue" (line 790-792) does NOT include these markers — add them. |
| `src/main/java/org/ecocean/ia/IA.java` | `enqueueOneAssetForMlService` (line 281): read `topTask.getParameter("bulkImportBatchMode")` (via species topTask's parameter inheritance from parentTask at line 149 — verify this propagation actually happens), stamp on qjob. `intakeMediaAssetsOneSpeciesMlService` (line 242): after per-asset queue loop, enqueue ONE deadline job if bulkImportBatchMode. |
| `src/main/java/org/ecocean/ia/MlServiceProcessor.java` | `process()`: dispatch branches for `bulkImportBatchMatch:true` and `bulkImportDeadline:true`. Detection branch (line 121): IF `bulkImportBatchMode:true` on payload, skip `waitAndRunMatch`; call new `tryFinalizeBulkImportMatch`. Extraction branch (line 157): same conditional. ALSO: `finalizeZeroDetections` (line 295-296) is a third terminal path that returns BEFORE persistDetections / waitAndRunMatch — needs the same finalizer hook. New helpers: `tryFinalizeBulkImportMatch`, `enqueueBulkImportBatchMatch`, `processBulkImportBatchMatch`, `processBulkImportDeadline`. |
| `src/main/java/org/ecocean/ia/Task.java` | Add `matchEnqueued` field + `getMatchEnqueued()` + `isMatchEnqueued()`. No setter — set via raw SQL only. |
| `src/main/resources/org/ecocean/ia/package.jdo` | Add field mapping for `matchEnqueued`. DataNucleus auto-DDLs the column. |
| `src/test/java/org/ecocean/ia/MlServiceProcessorTest.java` | Unit tests for new payload routing + deadline-handler logic. |

## Atomic finalizer SQL (corrected via Codex round 2)

Terminal status values are `completed`, `error`, `failed`,
`dropped-stale`, `skipped` (per `Task.statusInEndState()` at
`Task.java:85-91` — NOT `complete`).

```sql
UPDATE TASK
SET MATCHENQUEUED = TRUE
WHERE ID = :id
  AND (MATCHENQUEUED IS NULL OR MATCHENQUEUED = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM TASK c
    WHERE c.PARENT_ID_OID = :id
      AND (c.STATUS IS NULL OR c.STATUS NOT IN (
        'completed', 'error', 'failed', 'dropped-stale', 'skipped'
      ))
  );
```

Single SQL statement. Either 1 row affected (this caller is the
finalizer) or 0 rows (peer claimed, or sibling still non-terminal).
After UPDATE, close the Shepherd to avoid stale PM cache; open a
fresh one for the batch-match enqueue.

## Deadline handler (corrected via Codex round 2)

Self-requeueing durable job. Sleep cadence: 30 seconds per
`IAGateway.requeueJob` (call with `increment=true` to land on the
30s branch — `increment=false` would sleep 1s per `IAGateway.java:771,
785`). Routing: payload needs one of the existing markers
(`detect/fastlane/MLService/mlServiceV2`) for the queue selector at
`IAGateway.java:790-792` to route to the detection queue; otherwise
it lands on the generic queue. Adding the deadline marker to that
condition is the cleaner fix.

```text
processBulkImportDeadline(payload):
  topTaskId, deadlineAtMillis from payload
  open Shepherd
  topTask = load(topTaskId)
  if topTask is null: log, return  // import deleted
  if topTask.matchEnqueued is TRUE:
    close, return  // finalizer already fired
  if now() < deadlineAtMillis:
    close
    IAGateway.requeueJob(payload, true)  // 30s nap; durable
    return
  // Time's up. Force-claim ignoring the terminal check.
  UPDATE TASK SET MATCHENQUEUED = TRUE
    WHERE ID = :id AND (MATCHENQUEUED IS NULL OR MATCHENQUEUED = FALSE)
  commit
  if updated == 1: enqueueBulkImportBatchMatch(topTaskId, matchConfig)
```

## Batch match handler

```text
processBulkImportBatchMatch(payload):
  topTaskId, matchConfig from payload
  open Shepherd (read-only)
  topTask = load(topTaskId)
  annIds = collect from topTask.getObjectMediaAssets()[*].annotations[*].id
  close Shepherd

  okCount, skippedNoEmbedding, errCount = 0
  for each annId:
    open Shepherd, begin tx
    try:
      ann = load(annId)
      if ann is null: log "ann missing", continue
      if not hasEmbeddingForMatchConfig(ann, matchConfig):
        skippedNoEmbedding++
        log "ann %s: no usable embedding, skipping"
      else:
        Embedding.findMatchProspects(matchConfig, matchTask, shep)
        okCount++
      commit
    catch any Exception ex:
      errCount++
      log "ann %s: match failed: %s" annId, ex
    finally:
      rollbackAndClose

  // Mark topTask matching-complete for iaSummary
  set topTask identification status to completed
  log summary
```

Per-annotation transactions. One bad annotation can't poison the
rest. No gate, no defer.

## Codex findings to address during implementation

These six were caught across two design-review rounds and must be
specifically validated:

1. **Flag-propagation reality.** `Task.addParameter` rebuilds the
   target task's own stored JSON string; it does not mutate the
   shared `taskParameters` JSONObject. The queued `task` at
   `IAGateway.handleBulkImport:900` gets `.setParameters(taskParameters)`
   from the original jin object, so a `parentTask.addParameter` AFTER
   the parentTask's own setParameters does NOT reach the queued task.
   Mutate `taskParameters` itself before line 901, OR call
   `task.addParameter` directly on the queued task, OR pass the flag
   through a different channel (e.g. directly on qjob at line 911+).

2. **Suppression must cover three terminal paths, not two.**
   `persistDetections` AND `persistExtraction` AND
   `finalizeZeroDetections` (line 295-296) can each mark the child
   terminal. The finalizer hook must be installed on all three —
   otherwise a zero-detection asset that happens to be the last
   sibling leaves the batch hanging until the deadline fires.

3. **Terminal-status string list.** It's `completed`, not `complete`.
   Also `error`, `failed`, `dropped-stale`, `skipped`. See
   `Task.statusInEndState` at `Task.java:85-91`.

4. **Deadline requeue mechanics.** `IAGateway.requeueJob(payload,
   false)` sleeps 1s, not 30s. Use `increment=true` for the 30s
   cadence. Also: the payload must carry a marker that satisfies the
   routing condition at `IAGateway.java:790-792` or the deadline lands
   on the generic queue. Add the deadline marker to that condition.

5. **Atomic lock requires raw SQL.** Task has no `@Version` field, so
   JDO object mutation isn't race-safe. The conditional UPDATE must
   be a single SQL statement. Handle existing-row NULL via
   `MATCHENQUEUED IS NULL OR MATCHENQUEUED = FALSE`. Close + reopen
   Shepherd to avoid stale PM cache after the native UPDATE.

6. **Enqueue orphans.** `enqueueOneAssetForMlService` persists the
   child Task BEFORE the queue write (line 287). If
   `addToDetectionQueue` fails (line 314), the child is stuck
   non-terminal forever. The deadline safety-net rescues this — but
   ONLY if the deadline enqueue itself succeeded. Log loudly at
   intake when the deadline enqueue fails; consider a v2 sweeper.

## Risk and known limitations

- **Per-annotation `findMatchProspects` deadlock blocks batch.**
  Batch is single-threaded per annotation. v1 accepts; v2 follow-up
  adds per-annotation timeout.
- **Schema column add.** DataNucleus auto-DDLs. Existing rows get
  NULL; SQL conditions handle that.
- **Mixed-species bulk imports.** Each species topTask drives its
  own batch independently. Correct.
- **Deadline lost on FileQueue write failure at intake.** Log + v2
  sweeper out of scope for v1.
- **Single-encounter flow unchanged.** Gate keeps running for those.

## Out of scope (v2 follow-ups)

- Per-annotation match timeout (v1 stuck `findMatchProspects` blocks
  the batch).
- Stuck-import janitor that unsticks `matchEnqueued=true` topTasks
  with no match-result progress.
- Telemetry on batch vs gate throughput.
- Removing `MatchVisibilityGate` entirely (still load-bearing for
  single-encounter live submissions).

## Testing

- **Unit tests** in `MlServiceProcessorTest.java`:
  - `processBulkImportDeadline` is null-safe on missing topTask
  - No-op when matchEnqueued is already true
  - Requeues when deadlineAtMillis is in the future
  - Forces claim when deadlineAtMillis has elapsed
  - Dispatch branches in `process()` route the new payload types
- **Manual integration tests** (existing convention — no DB
  testcontainer per `MlServiceProcessorTest.java:14-19`):
  - 4-encounter bulk import: all 4 match within seconds, not minutes
  - 200-encounter bulk import: batch fires once at end, no per-encounter defer cycles
  - Bad-image case: 4-encounter import with one corrupted image; verify other 3 match
  - Zero-detections case: 4-encounter import where one MA has no animals; verify others match
  - Stuck case: stop ml-service mid-import; verify deadline fires at T+12min and batch runs for the rest

## Open questions for the implementation session

- Whether to stamp `bulkImportBatchMode` on `taskParameters` (cleanest
  propagation) vs directly on qjob (most explicit) vs both. Investigate
  whether `taskParameters` is reused after the queue enqueue at
  `IAGateway.java:901`.
- Whether to introduce a `Task.setMatchEnqueued` (package-private) or
  enforce "SQL only" via the absence of a setter. v1 prefers no setter.
- Whether the batch-match handler should use a thread pool for
  parallelism. v1 single-threaded; v2 follow-up.

## Stopgap shipped 2026-05-22

Commit `75c1452b` reduces the gate-defer first-attempt cadence in
`IAGateway.requeueJob` from 30s to 5s. ~6x faster convergence for
the existing gate path without architecture change. Does not
eliminate the 12-minute ceiling. Bulk imports of 4-50 encounters
now match within seconds; 200-encounter is borderline; 1000-encounter
still hits the ceiling. This document is the path to the real fix.
