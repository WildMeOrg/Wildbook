# Parallelize bulk vector-match at the per-encounter driver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** Run the bulk re-identification driver's **per-encounter** `intakeAnnotations` units across a bounded thread pool instead of serially, gated by `iaMatchThreads` (default **1** = today's exact serial behavior). Measured: ~9→~46 matches/sec (~4M/day) at ~8 threads for a warm 50k-candidate pool.

**Architecture (revised after Codex plan review R1):** The original loop-level plan (parallelize `findMatchProspects`' inner annotation loop) was WRONG — the real driver (`resendBulkImportID.jsp`) loops per encounter with ~1 annotation each, so that loop always ran serially and gained nothing. It also would have broken per-task transaction atomicity. Instead, parallelize at the **driver** level: each encounter's `IA.intakeAnnotations(...)` is ALREADY a self-contained, transactional unit today; run N of them concurrently, each on its OWN Shepherd. **`findMatchProspects` and the match/task-tree internals are untouched.**

**Tech Stack:** Java 17, DataNucleus JDO, JUnit 5, Maven.

## Global Constraints
- **HARD DEPENDENCY / stacking:** off `perf/vector-match-throughput` (**PR #1676**), NOT `main`. Concurrent `getMatches` needs #1676's stateless `querySearch` + `ConcurrentHashMap` PIT_CACHE + synchronized/eager client init. PR base = `perf/vector-match-throughput`.
- **Default `iaMatchThreads = 1` is byte-identical to today:** the helper runs the encounters serially on the caller's Shepherd, i.e. the current loop. Parallelism is an opt-in branch.
- **Each worker: its own `new Shepherd(context)` + its own transaction, committed with `commitDBTransactionWithStatus()`** (plain `commitDBTransaction()` swallows failures — Codex R1 #3). Never share a Shepherd/PM or a loaded persistent object across threads; workers load encounter + root Task by **id**.
- **Per-encounter atomicity is preserved** — one encounter's intake = one worker transaction, exactly as the serial loop does one at a time today. We change concurrency, NOT transaction boundaries. `findMatchProspects` is not modified.
- **DB pool ≥ effective threads + web baseline;** clamp `iaMatchThreads` to `[1, min(16, availableProcessors)]` (throughput regresses past ~cores). Roll out incrementally.
- Commit LF; JUnit-5 message-last; `Query.closeAll()` / `rollbackAndClose()` in `finally`.

## Baseline (verified on the #1676 base)
- Driver `resendBulkImportID.jsp` (~62-104): creates a root `parentTask` (linked to ImportTask); `for each Encounter`: `new subParentTask` (params, stored), collect `matchMeAnns` (valid-for-ID annotations), `childTask = IA.intakeAnnotations(myShepherd, matchMeAnns, subParentTask, false)` (runs the vector match INLINE), `subParentTask.addChild(childTask)`, record a jobJSON. All on one `myShepherd`, serial, in the request thread.
- `Task.children` is `mapped-by="parent"` (ia/package.jdo:20) → child owns the FK; `Task` has no optimistic-version field. So a worker that creates `subParentTask` with `parent = root` (root loaded by id in the worker's PM) writes the child FK independently — no shared parent-row/join-table write. Each worker mutating only ITS OWN PM copy of root's inverse `children` collection is not a cross-thread race (Codex R1 #1 accepted with the per-id-load approach; add the concurrent-children test).
- `IA.intakeAnnotations(Shepherd, List<Annotation>, Task parent, boolean fastlane)` (IA.java:377) is unchanged and self-contained; for a vector config it runs `findMatchProspects` inline and returns.
- Match read path is concurrency-safe on this base (Codex R1 #6): `getMatches`→stateless `querySearch`, batched `getEncountersByAnnotationIds` per-Shepherd, no PIT/`_matchEncounterCache`/`existsIndex` on this path.
- `Shepherd.getEncounter(id)` / `getTask(id)` / `commitDBTransactionWithStatus()` (:3458) exist.
- Encounters in a bulk import are distinct → their annotations are distinct → no duplicate-annotation concurrent embedding extraction (Codex R1 #7); the helper still de-dups encounter ids defensively.

---

## Task 1: Bounded parallel per-encounter identify helper

**Files:**
- Create: `src/main/java/org/ecocean/ia/ParallelIdentify.java`
- Test: `src/test/java/org/ecocean/ia/ParallelIdentifyTest.java` (new)

**Interfaces:**
- `public static JSONArray identifyEncounters(String context, String rootTaskId, java.util.List<String> encounterIds, JSONObject taskParameters, int threads)` — returns one job entry per encounter that produced work: `{encounterId, subParentTaskId, childTaskId, ok}` (and `{encounterId, ok:false, error}` for failures). `threads<=1` runs serially on a single Shepherd (identical to the current loop). `threads>1` runs a bounded pool; each worker: `new Shepherd(context)`, begin tx, load the root Task + encounter by id, build `subParentTask(parent=root, params)`, gather valid annotations, `IA.intakeAnnotations(ws, anns, subParentTask, false)`, link child, `commitDBTransactionWithStatus()`; on false/exception rollback + record failure. Bounded lifecycle: submit all, `shutdown()`, `awaitTermination(deadline)`, on timeout `cancelAll + shutdownNow`, record un-run encounters as failures; restore interrupt status.

- [ ] **Step 1: Write the failing test** — `ParallelIdentifyTest` (Testcontainers PG+OS, reset boilerplate). Fixture: an ImportTask-less root Task + K encounters each with 1+ valid annotation (embedded), candidates indexed. Assert:
  - `identifyEncounters(ctx, rootId, encIds, params, 4)` returns K ok entries; K subParentTasks each `parent==root`; each encounter's childTask + a MatchResult per matched annotation persisted.
  - Result set equals a `threads=1` run over the same fixture (same MatchResults / task tree).
  - A worker whose commit is forced to fail (e.g. inject a duplicate-key or a null) records `ok:false` and does NOT leave the root wrongly finalized; other encounters still succeed.
  - Concurrent-children: after the run, reload the root in a fresh Shepherd and assert ALL K children are present (no lost/overwritten children — Codex R1 #1).

- [ ] **Step 2: Run to verify it fails** — `mvn test -Dtest=ParallelIdentifyTest` → FAIL (class absent).

- [ ] **Step 3: Implement `ParallelIdentify.identifyEncounters`** per the interface. Extract the per-encounter body from the JSP verbatim in logic (subParentTask, matchMeAnns via `IBEISIA.validForIdentification`, intakeAnnotations, link) into a private `processOne(Shepherd ws, Task root, String encId, JSONObject params) -> JSONObject`. Serial branch: one Shepherd, loop `processOne`, commitWithStatus per encounter (mirrors the JSP's per-encounter `updateDBTransaction`). Parallel branch: pool of `clampThreads(threads)`, one `processOne` per encounter on a per-worker Shepherd, futures collected with a deadline; aggregate JSONArray.

- [ ] **Step 4: Run to verify it passes** — `mvn test -Dtest=ParallelIdentifyTest` → PASS; parallel == serial.

- [ ] **Step 5: Commit** — `refactor(match): ParallelIdentify.identifyEncounters (bounded per-encounter pool, default serial)`

## Task 2: Config + wire the bulk-reID driver

**Files:**
- Modify: `src/main/webapp/appadmin/resendBulkImportID.jsp` (replace the serial per-encounter loop with `ParallelIdentify.identifyEncounters`)
- Modify: `src/main/resources/bundles/commonConfiguration.properties` (`iaMatchThreads = 1`)

**Interfaces:** JSP reads `iaMatchThreads` (default 1, clamped in the helper) and passes it; the emitted `initiatedJobs` JSON keeps `topTaskId`/`childTaskId` fields (map from the helper's entries) so the response contract is unchanged.

- [ ] **Step 1:** Add `iaMatchThreads` to `commonConfiguration.properties`:
```properties
# Threads for bulk re-identification (parallel per-encounter matching in resendBulkImportID).
# Default 1 = serial (unchanged). Each worker holds its own DB connection; size the pool >= N + web
# baseline. Requires stateless match search (PR #1676). Clamped to [1, min(16, cpu cores)] —
# throughput regresses past ~cores.
iaMatchThreads = 1
```
- [ ] **Step 2:** In `resendBulkImportID.jsp`, after building `parentTask`/`taskParameters`, replace the `for(Encounter ...)` block with: read `iaMatchThreads`; collect `encounterIds` from `itask.getEncounters()`; `JSONArray jobs = ParallelIdentify.identifyEncounters(context, parentTask.getId(), encounterIds, taskParameters, threads);` map into the existing `initiatedJobs` response shape. Keep the root/ImportTask linkage (`itask.setIATask(parentTask)`) on the request Shepherd as today.
- [ ] **Step 3: Manual/behavioral check** — with `iaMatchThreads=1` the response + persisted tasks match the pre-change JSP; with `>1`, same jobs produced, wall time drops. (JSP has no unit harness; covered by Task 1's tests + a full build.)
- [ ] **Step 4: Full build** — `mvn clean install` → BUILD SUCCESS.
- [ ] **Step 5: Normalize LF + commit** — `feat(match): parallel bulk re-identification via iaMatchThreads (default 1)`

## Scope / non-goals
- **In-request bounded pool** (not a background job queue). The JSP already does the whole batch in-request serially, so in-request-parallel is a strict improvement and minimal change. For very large imports, a durable background job is a separate follow-up (Codex R1 #9 note).
- Only the `resendBulkImportID` driver is wired here. The ml-service detection-callback path (`MlServiceProcessor`) and other intake callers are left serial (separate follow-up); default `iaMatchThreads=1` means no behavior change anywhere until opted in.

## Capacity Caveats (before raising iaMatchThreads > 1)
1. DB pool ≥ N + web baseline (each worker holds a Shepherd tx) — else pool-exhaustion deadlock.
2. RAM/OpenSearch: N concurrent kNN; keep the annotation index in page cache. Warm 50k pool measured fine to ~cores; regresses past.
3. ml-service: un-embedded annotations extract inline (N concurrent calls) — pre-embed or size for it.
4. Roll out incrementally (1→cores/2→cores) watching `dbconnections.jsp` + latency.

## Self-Review (post-Codex R1)
- Altitude fixed: parallelizes per-encounter (the real batch), not the 1-annotation inner loop (Codex R1 #9). ✓
- `findMatchProspects` untouched → per-encounter atomicity preserved (R1 #2/#4/#5). ✓
- `commitDBTransactionWithStatus` for worker success (R1 #3). ✓
- Root linkage via child FK, per-worker Shepherd, load-by-id, concurrent-children test (R1 #1). ✓
- Proper shutdown/awaitTermination/deadline + interrupt handling (R1 #10). ✓
- Default 1 = serial identical. ✓ Read path concurrency-safe on #1676 base (R1 #6). ✓
