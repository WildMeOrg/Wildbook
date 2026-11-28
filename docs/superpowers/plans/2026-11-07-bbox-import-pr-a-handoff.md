# Bulk-import bounding boxes, PR A: IA hand-off (behavior-neutral) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Plan rev 4 (2026-11-07): Codex plan review rounds 1 to 3 applied (see the review log, "Plan A"); round 3 accepted the parity contract below. Tasks 1-9 implemented 2026-11-07 in nine commits; Task 10 (full suite, Codex branch review, PR) in progress. Execution notes: Task 6's two pure tests were written before, but run only after, their implementation (their red state is not captured); `ImportedBoxes.settle` flushes before `Task.getTasksFor` because that ignoreCache query refreshed an unflushed status write away (found by `enqueueFailureOnEveryBoxSettlesTheAsset`); the Task 8 regression fixture sets the detected asset to complete-mlservice, as detection leaves it.

**Goal:** Teach the IA pipeline to take an import's user-supplied box annotations through embedding extraction and identification without detection, with a durable per-box task lifecycle, while every existing import's successful path produces the same messages, task topology and statuses as today (no import carries boxes until PR B).

**Architecture:** `IAGateway.handleBulkImport` (and later `SubmissionProcessing.prepare`) build the queue envelope through one helper that, for an import stamped `annotationsImported`, sends the import's imported-box annotation ids with `annotationMode: "extract-then-identify"` instead of media asset ids. `IA.handleRest` validates the envelope shape first, then routes that mode to a new intake that creates one species task and one per-box child task plus a `{mlServiceV2, annotationId}` job on the detection queue (or, for species without an ml-service extractor, the existing `MlServiceProcessor.runMatchProspects` identification path). `MlServiceProcessor` keeps an imported-box child non-terminal until its match run exists and settles the asset's `detectionStatus` to `complete` once every extraction-tracked box on it is extracted or errored, so the existing `MatchVisibilityGate` waits for sibling boxes unchanged. A startup replay republishes stale imported-box children from `queueResumeMessage`.

**Tech Stack:** Java 17, DataNucleus JDO (`Shepherd`), JUnit 5 + Mockito, Testcontainers PostgreSQL (`IdentificationDbTestSupport`), org.json.

**Spec:** `docs/superpowers/specs/2026-11-06-bulk-import-annotation-bboxes-design.md` (rev 8), sections 4.4, 4.5 and the PR A row of section 5. Companion: `docs/superpowers/specs/2026-11-06-bulk-import-annotation-bboxes-codex-review-log.md`.

## Global Constraints

- Neutrality, stated exactly. With no `annotationsImported` ImportTask and no `_bulkImportBbox` annotation in the database: every queue message published on a successful path, every task topology and every task or asset status is what main produces today, with ONE successful-path metadata change: the per-asset ml-service detection child now stores its job as `queueResumeMessage` (today it stores none). Failure-path changes, deliberate: (1) `handleBulkImport` reports `success:false` and marks the root `error` when its commit or publish fails or throws (today: silent `success:true`); (2) a per-asset detection child whose persist or commit fails is not published (today: `storeNewTask` returns false, the caller ignores it and publishes anyway); (3) a `v2` envelope carrying the newly reserved key `annotationMode` is validated and may be rejected (today the key is ignored); (4) a skipped-identification job of an ordinary detection child returns a `persistError` outcome instead of `ok` when the skip-completion helper's strict task lookup hits a datastore failure (today: `ok` without any lookup). Edge cases found by the branch review, also deliberate: a `handleBulkImport` call naming a missing ImportTask returns `success:false` with an error instead of throwing; an ImportTask whose media list is null publishes an empty `mediaAssetIds` envelope instead of throwing; `matchIntakeParams` strips `embeddingExtraction` and `importedBox` from every caller's inherited parameters. `SubmissionProcessingTest.messageMatchesLegacyBulkImportPipelineContract` is the executable statement for the bulk-import message; these exceptions go into the PR description verbatim.
- Cloned envelopes keep every request field today's code keeps, except on a boxed import, where the routing keys are replaced explicitly (`mediaAssetIds` removed, `annotationIds` and `annotationMode` set). Exclusivity is enforced again by `handleRest`'s envelope validation.
- Provenance marker: feature parameter key `_bulkImportBbox` (object with `importTaskId`); every "imported box" rule keys on it, never on geometry.
- Task parameter keys: `importedBox` (boolean) and `embeddingExtraction` (boolean) on extraction children; `annotationsImported` (boolean) on ImportTask passed parameters; envelope key `annotationMode` with the single value `extract-then-identify`.
- Terminal task statuses are exactly `completed`, `error`, `dropped-stale` (`Task.statusInEndState`); the import summary counts only `completed` and `error` as terminal, so an imported-box child never ends in `dropped-stale`: every stale exit on such a child writes `error` with code `STALE`.
- Boxed assets reach `detectionStatus = "complete"` (`IBEISIA.STATUS_COMPLETE`) only through `ImportedBoxes.settle`; never `processing-mlservice` (the startup reconciler would re-detect them). Settle runs on every terminal transition of an imported-box child that this PR writes (embedding stored, embedding exists, error of any code, ENQUEUE failure at intake).
- The `MatchVisibilityGate` and its SQL are not modified.
- Commits introduced by this PR are checked (`commitDBTransactionWithStatus`), a false return is handled and logged with the task id; a thrown exception on those paths is routed to the same recovery as a false return. Existing commits inside `MlServiceProcessor` (`loadExtractionContext`, `persistExtraction`, the String-id `markTaskError`) stay as they are, unchecked, exactly as on the detection path: a swallowed commit failure there leaves the child non-terminal with its job consumed, and the startup replay (one hour) or the seven-day watchdog is the recovery, as for detection today. The plan states this parity explicitly; it does not upgrade those commits.
- Uncaught exceptions inside the processor (extraction, configuration, gate, deferred inference) leave an imported-box child non-terminal with its job consumed; the startup replay re-runs the job (duplicate-tolerant), as the detection path relies on today. No new catch-all is added.
- Recovery is duplicate-tolerant (spec 4.4): a replay or re-send may publish a job whose work is already queued or done; `embedding-exists` then produces a second run and run-aware status selection picks the newest. No pending-job check is attempted.
- Stale-replay threshold reuses `StartupWildbook.STALE_MLSERVICE_THRESHOLD_MS` (1 hour, stays private; the cutoff is passed as an argument); replay runs once per startup, bounded to 500 tasks per run.
- Every edited repo file is LF-only before commit (`grep -cP '\r$' <file>` prints 0).
- Branch `feat/bbox-import-a-handoff` from `main` in the sibling worktree `/mnt/c/Wildbook-bbox`; one `mvn` per worktree at a time; `mvn -q test -Dtest=<classes>` uses the pom's Surefire `argLine` (never override it).
- Every commit message ends with these two trailer lines, in this order: `Co-Authored-By: Codex <noreply@openai.com>` and `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. The PR body ends with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- Tests marked **(regression)** pass before the change and guard existing behavior; they are not evidence the step is implemented. Every other test fails before its step.

## Review Focus

1. **A photo with two imported boxes where one box's job fails to publish after the other's extraction already ran.** The ENQUEUE error must settle the asset, so the extracted box's deferred match stops deferring. Test in Task 6: `enqueueFailureSettlesTheAsset`, and in Task 8: `deferredReFireProceedsAfterSiblingEnqueueFailure`.
2. **An imported-box job replayed after its embedding already exists, on an `_id_conf` without explicit model identity.** `embedding-exists` must gate and match with the same defaults a fresh extraction would use. Test in Task 8: `embeddingExistsStillMatchesWithDefaultedConfig`.
3. **A crash between embedding commit and match dispatch, then a restart.** The replayed job finds the embedding, gates and creates the run; the child ends `completed`. Test in Task 9: `replayAfterCrashProducesTheRun`.
4. **An envelope that mixes media ids with a moded annotation list, or names an unknown mode.** `handleRest` marks the root `error` before any intake; no detection job is published. Test in Task 6: `mixedEnvelopeIsRejectedBeforeAnyIntake`.
5. **A photo shared by an ml-service species box and a WBIA-only species box.** The asset must settle on the ml-service box alone (the WBIA box has no extraction task). Test in Task 7: `boxesWithoutAnExtractionTaskDoNotBlockSettle`.

---

### Task 1: Annotation marker helpers (done, commit 5e189cf8e)

**Files:** `src/main/java/org/ecocean/Annotation.java`, `src/test/java/org/ecocean/AnnotationImportedBoxTest.java`.

**Interfaces:** `Annotation.FEATURE_PARAM_IMPORTED_BOX = "_bulkImportBbox"`; `boolean isImportedBox()`; `String importedBoxImportTaskId()`.

- [x] Steps 1-5 done; 4 tests green.

### Task 2: Task and ImportTask flags (done, commit 45ab677546)

**Files:**
- Modify: `src/main/java/org/ecocean/ia/Task.java:34-41` (constants) and near line 842 (option helpers)
- Modify: `src/main/java/org/ecocean/servlet/importer/ImportTask.java:346-356`
- Test: `src/test/java/org/ecocean/ImportTaskTest.java` (extended), `src/test/java/org/ecocean/ia/TaskImportedBoxTest.java` (created)

**Interfaces:**
- Produces: `Task.PARAM_IMPORTED_BOX = "importedBox"`, `Task.PARAM_EMBEDDING_EXTRACTION = "embeddingExtraction"`, `public static boolean Task.isImportedBoxTask(Task t)` (null-safe `optBoolean`); `ImportTask.PARAM_ANNOTATIONS_IMPORTED = "annotationsImported"`, `public boolean ImportTask.annotationsImported()` (passed parameters `optBoolean`, false when parameters are null).
- Changes: `ImportTask.skippedDetection()` is true when `skipDetection` OR `annotationsImported()`; `skippedIdentification()` returns `!iaTaskRequestedIdentification()` when `annotationsImported()`, else today's rule (`skippedDetection()` implies true).

- [x] **Step 1: tests written** — `TaskImportedBoxTest` (2 tests) and four `ImportTaskTest` additions (`annotationsImportedSkipsDetectionButNotIdentification`, `nullPassedParametersMeanNothingImported`; **(regression)**: `annotationsImportedWithSkipIdentSkipsIdentification`, `plainSkipDetectionStillImpliesSkippedIdentification`).
- [x] **Step 2: Run to verify they fail** — `mvn -q test -Dtest='ImportTaskTest,TaskImportedBoxTest'`; expected: compilation failure on the new symbols.
- [x] **Step 3: Implement** per the Interfaces block. `iaSummaryJson` needs no change (lines 926-927 already read these two methods).
- [x] **Step 4: Run to verify they pass**, including the pre-existing `ImportTaskTest` cases.
- [x] **Step 5: Commit** — `git commit -m "feat(import-task): annotationsImported flag and imported-box task parameter"`

### Task 3: Lane rule for the extraction mode (done, commit 4f6774eb43)

**Files:**
- Modify: `src/main/java/org/ecocean/servlet/IAGateway.java:119-138` (`laneFor`)
- Test: `src/test/java/org/ecocean/servlet/IAGatewayLaneForTest.java` (create)

**Interfaces:**
- Produces: `IAGateway.ANNOTATION_MODE_EXTRACT = "extract-then-identify"`. Rule: inside the `v2` block, before the `annotationIds` rule, an envelope whose `annotationMode` equals that value classifies `LANE_DETECTION` regardless of `fastlane`. Extend the method's javadoc paragraph on annotations dominating: extraction-mode envelopes enqueue per-box jobs and intake nothing synchronously, so they belong on the detection lane.

- [x] **Step 1: Write the failing test**

```java
@Test void extractionModeGoesToTheDetectionLaneEvenWithFastlane() {
    JSONObject j = new JSONObject().put("v2", true).put("fastlane", true)
        .put("annotationIds", new JSONArray().put("a1")).put("annotationMode", IAGateway.ANNOTATION_MODE_EXTRACT);
    assertEquals(IAGateway.LANE_DETECTION, IAGateway.laneFor(j));
}
@Test void plainAnnotationIdsKeepTheIaLane() { // (regression)
    JSONObject j = new JSONObject().put("v2", true).put("annotationIds", new JSONArray().put("a1"));
    assertEquals(IAGateway.LANE_IA, IAGateway.laneFor(j));
    assertEquals(IAGateway.LANE_FASTLANE, IAGateway.laneFor(j.put("fastlane", true)));
}
@Test void mediaAssetIdsKeepTheDetectionLane() { // (regression)
    assertEquals(IAGateway.LANE_DETECTION, IAGateway.laneFor(new JSONObject().put("v2", true).put("mediaAssetIds", new JSONArray().put("7"))));
}
```

- [x] **Step 2: Run to verify it fails** — `mvn -q test -Dtest=IAGatewayLaneForTest`; expected: compilation failure on the constant.
- [x] **Step 3: Implement** the constant and the rule.
- [x] **Step 4: Run to verify it passes**; `mvn -q test -Dtest=IAIntakeCoreTest` stays green (it asserts `laneFor` on its own jobs).
- [x] **Step 5: Commit** — `git commit -m "feat(ia-gateway): extraction-mode envelopes classify to the detection lane"`

### Task 4: Shared envelope builder and the checked hand-off (done, commit 7021720295)

**Files:**
- Modify: `src/main/java/org/ecocean/servlet/IAGateway.java:1306-1363` (`handleBulkImport`)
- Modify: `src/main/java/org/ecocean/ia/IA.java:575` (`markTaskError` becomes `public static`; it is called from `org.ecocean.servlet`)
- Modify: `src/main/java/org/ecocean/api/BulkImport.java:855-860` (`initiateIA` logs a false `success`)
- Modify: `src/test/java/org/ecocean/api/submission/SubmissionProcessingTest.java:64-75` (stub the checked commit)
- Test: `src/test/java/org/ecocean/servlet/IAGatewayImportEnvelopeTest.java` (create)

**Interfaces:**
- Produces: `public static JSONObject IAGateway.buildImportEnvelope(ImportTask itask)` returning either `{"mediaAssetIds": [String...]}` (ids from `itask.getMediaAssets()`, as today, in that order) or, when `itask.annotationsImported()`, `{"annotationIds": [String...], "annotationMode": "extract-then-identify"}` where the ids are every annotation of `itask.getEncounters()` with `isImportedBox()` and `importedBoxImportTaskId()` equal to `itask.getId()` (null-safe over encounters, annotation lists and features; duplicates removed, first-seen order).
- `handleBulkImport` after this task, in order: (1) create `parentTask` (root) and `task` (child) exactly as today (same parameters, same parent/child link, `itask.setIATask(parentTask)`, the same `addLog` line); (2) build `qjob` as today (clone of `jin`, `remove("bulkImport")`, put `taskId`, `v2`, `__context`, `__baseUrl`, `__handleBulkImport`); `JSONObject env = buildImportEnvelope(itask)`; for an un-boxed import put `mediaAssetIds` from `env`; for a boxed import `qjob.remove("mediaAssetIds")` then put `annotationIds` and `annotationMode` from `env`; (3) `task.setQueueResumeMessage(qjob.toString())`; (4) a boxed import with zero annotation ids: `parentTask.setStatus("completed")`, `itask.addLog("no imported boxes survived import; nothing to hand off")`, remember `publish = false`; (5) ONE persist boundary: `makePersistent(parentTask)`, `makePersistent(task)`, `makePersistent(itask)`, `commitDBTransactionWithStatus()`, then `beginDBTransaction()` (replaces the two `storeNewTask` calls and the unchecked commit; the legacy message is unchanged); (6) when `publish`: `publishToLane`; (7) a false commit at (5), a false publish at (6), or ANY exception thrown by (5) or (6) (`makePersistent` runtime exceptions, `publishToLane`'s `IOException`): `recoverHandoffFailure(...)` with code `HANDOFF_COMMIT` or `HANDOFF_PUBLISH` (exception message appended), `res.put("success", false)`, `res.put("error", code)`; the zero-box case returns `success:true`, `queuedCount:0`.
- `static boolean IAGateway.recoverHandoffFailure(Shepherd myShepherd, ImportTask itask, Task root, List<String> boxIds, String code, String message)`: inside its own `try/catch`: `rollbackDBTransaction()`, `beginDBTransaction()`, reload the root by id (`Task.load`), or when it does not load (commit failure) re-create a `Task` with the root's id and parameters and `itask.setIATask(root)`; `IA.markTaskError(root, code, message)`; when `boxIds` is non-empty, one child `new Task(root)` holding each box annotation loaded by id (`myShepherd.getAnnotation`, skipping ids that do not load) with status `error` and the same code; `itask.addLog("IA hand-off failed (" + code + "): " + message)`; `makePersistent` the root, the child and `itask`; `commitDBTransactionWithStatus()`; `beginDBTransaction()`; returns the recovery commit's result. A false recovery commit, or any exception inside recovery, is logged as `ERROR: IAGateway.recoverHandoffFailure() could not persist the failure for import <id>: <detail>` and the method returns false; the caller still returns `success:false`.
- `BulkImport.initiateIA`: after `handleBulkImport`, `if (!rtn.optBoolean("success", false)) itask.addLog("IA hand-off failed: " + rtn.optString("error", "unknown"))`.

- [x] **Step 1: Write the failing tests** (Mockito; `IAGateway` static methods through `mockStatic(IAGateway.class, CALLS_REAL_METHODS)` stubbing only `addToDetectionQueue`, as `SubmissionProcessingTest` does; `Shepherd` and `PersistenceManager` mocked; task statuses read with `Task.getStoredStatus()`; an "itask" mock with `getId()`, `annotationsImported()`, `getMediaAssets()`, `getEncounters()`)

```java
@Test void unboxedImportProducesMediaAssetIdsOnly() { ... "[\"42\",\"43\"]" ... no annotationIds, no annotationMode }
@Test void boxedImportProducesOnlyItsOwnImportedBoxes() { /* ann A marker imp-1, B marker imp-9, C unmarked, A again on a second encounter; itask id imp-1 -> ["A"], mode set, no mediaAssetIds */ }
@Test void successfulUnboxedHandoffPublishesTodaysMessage() { // (regression) commit true, publish true; captured message: mediaAssetIds, v2, taskId == child id, no annotationMode; child.getQueueResumeMessage() equals it; res success true, queuedCount 1 }
@Test void unboxedInputKeepsLegacyAnnotationFields() { // (regression) jin carries "annotationIds":["z"] and "fastlane":true; the captured message still carries both, plus mediaAssetIds }
@Test void boxedInputDropsMediaAssetIdsFromTheClone() { /* jin carries "mediaAssetIds":["9"]; boxed itask: captured message has no mediaAssetIds, has annotationIds ["A"] and the mode; laneFor(captured) == LANE_DETECTION */ }
@Test void failedCommitReturnsFalseAndPublishesNothing() { /* commitDBTransactionWithStatus false then true: success false, error HANDOFF_COMMIT, publish never, two commit calls */ }
@Test void throwingPersistIsRecovered() { /* pm.makePersistent throws RuntimeException once: success false, error starts with HANDOFF_COMMIT, publish never */ }
@Test void throwingPublishIsRecovered() { /* addToDetectionQueue throws IOException: success false, error starts with HANDOFF_PUBLISH, root error, error child holds A */ }
@Test void failedPublishMarksRootErrorWithAnErrorChildHoldingTheBoxes() { /* addToDetectionQueue false: root error; last child status error holding A; itask.addLog contains HANDOFF_PUBLISH */ }
@Test void failedRecoveryCommitStillReportsFailure() { /* commit false, recovery commit false: success false, no exception */ }
@Test void throwingRecoveryStillReportsFailure() { /* commit false; pm.makePersistent throws inside recovery: success false, no exception */ }
@Test void boxedImportWithNoSurvivingBoxesCompletesTheRoot() { /* annotationsImported true, no marked annotations: success true, queuedCount 0, root completed before the commit (verify commit called once), no publish */ }
```
Also edit `SubmissionProcessingTest.messageMatchesLegacyBulkImportPipelineContract`: add `when(sh.commitDBTransactionWithStatus()).thenReturn(true);` before calling `handleBulkImport` (its intent, an identical legacy message, is unchanged).

- [x] **Step 2: Run to verify they fail** — `mvn -q test -Dtest='IAGatewayImportEnvelopeTest,SubmissionProcessingTest'`; expected: compilation failure on `buildImportEnvelope`.
- [x] **Step 3: Implement** per the Interfaces block.
- [x] **Step 4: Run to verify they pass**; plus `mvn -q test -Dtest='IAGatewayRequestRootTest,IAGatewayDoIdentifyTest,IAGatewayHttpFailureTest'` stays green.
- [x] **Step 5: Commit** — `git commit -m "feat(ia-gateway): shared import envelope with checked commit and publish"`

### Task 5: Checked ml-service enqueue helpers (done, commit b316ea272a)

**Files:**
- Modify: `src/main/java/org/ecocean/ia/IA.java:284-333` (`enqueueOneAssetForMlService`)
- Test: `src/test/java/org/ecocean/ia/IAEnqueueMlServiceJobTest.java` (create)

**Interfaces:**
- Produces: `public static boolean IA.publishMlServiceJob(String context, JSONObject qjob)` (`IAGateway.addToDetectionQueue(context, qjob.toString())`; an `IOException` logs `ERROR: IA.publishMlServiceJob() addToDetectionQueue threw for task <taskId>: <ex>` and returns false) and `public static boolean IA.enqueueMlServiceJob(Shepherd myShepherd, Task child, JSONObject qjob, String context)`: `child.setQueueResumeMessage(qjob.toString())`; `myShepherd.getPM().makePersistent(child)` (an exception logs, `rollbackDBTransaction()`, `beginDBTransaction()`, returns false); `commitDBTransactionWithStatus()` false -> `beginDBTransaction()`, return false, no publish; true -> `beginDBTransaction()` then `return publishMlServiceJob(context, qjob)`.
- Changes: `enqueueOneAssetForMlService` builds the child and the job exactly as today (same parameters, same payload keys, same `encounterId` lookup) and ends with `return enqueueMlServiceJob(myShepherd, childTask, qjob, context)` instead of `storeNewTask` + `addToDetectionQueue`. Its boolean contract (true iff the queue write succeeded) is unchanged; the javadoc's "Task persistence note" is rewritten to describe the checked commit (a child whose commit fails is not persisted and not published).

- [x] **Step 1: Write the failing test** (mocked `Shepherd`/`PersistenceManager`; `mockStatic(IAGateway.class)` on `addToDetectionQueue`):
```java
@Test void failedCommitDoesNotPublish() { ... }
@Test void successfulCommitPublishesTheJobAndStoresTheResumeMessage() { ... }
@Test void persistExceptionReturnsFalseWithoutPublishing() { ... verify(sh).rollbackDBTransaction(); }
@Test void queueIoExceptionReturnsFalse() { ... }
@Test void enqueueOneAssetForMlServiceStillEmitsTheSamePayload() { // (regression) same keys as today: mlServiceV2, mediaAssetId, taxonomyString, taskId, __context, __baseUrl; child parameter ibeis.detection true; child.getQueueResumeMessage() equals the published string (the one new metadata write) }
```
- [x] **Step 2: Run to verify it fails** — `mvn -q test -Dtest=IAEnqueueMlServiceJobTest`; expected: compilation failure.
- [x] **Step 3: Implement** per the Interfaces block.
- [x] **Step 4: Run to verify it passes**; `mvn -q test -Dtest='MlServiceProcessorRetryExhaustionTest,IAIntakeCoreTest'` stays green.
- [x] **Step 5: Commit** — `git commit -m "refactor(ia): checked persist-then-publish helpers for ml-service jobs"`

### Task 6: `handleRest` envelope validation and the extraction intake (done, commit 29c1b15092)

**Files:**
- Create: `src/main/java/org/ecocean/ia/ImportedBoxes.java` (static helpers shared by `IA` and `MlServiceProcessor`)
- Modify: `src/main/java/org/ecocean/ia/IA.java:916-991` (`handleRest`) plus new package-private `envelopeRejection` and `intakeImportedBoxes`
- Test: `src/test/java/org/ecocean/ia/IAEnvelopeRejectionTest.java` (create, pure), `src/test/java/org/ecocean/ia/ImportedBoxesSettleTest.java` (create, pure), `src/test/java/org/ecocean/ia/ImportedBoxIntakeDbTest.java` (create; `extends IdentificationDbTestSupport`)

**Interfaces:**
- Produces `ImportedBoxes` (final class, static): `static boolean allSettled(List<Annotation> annsOnAsset, String doneAnnId, java.util.function.Function<Annotation, List<Task>> tasksFor)` (pure): considers only annotations with `isImportedBox()` that have at least one task with `Task.isImportedBoxTask` (boxes with no extraction task, such as WBIA-only species boxes, are ignored); such a box is done when its id equals `doneAnnId`, or `numberEmbeddings() > 0`, or any of its imported-box tasks `statusInEndState()`; true when nothing is considered. `static void settle(Shepherd shep, MediaAsset ma, String doneAnnId)`: when `allSettled(ma.getAnnotations(), doneAnnId, ann -> Task.getTasksFor(ann, shep))`, `ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE)`; idempotent; no-op for a null asset. `static void failChild(Shepherd shep, Task child, String code, String message)`: `IA.markTaskError(child, code, message)` then `settle(shep, first asset of child.getObjectMediaAssets(), null)` when it holds one.
- Produces: `static String IA.envelopeRejection(JSONObject jin)`: null for the three permitted shapes (non-empty `mediaAssetIds` with no `annotationMode`; non-empty `annotationIds` with no `annotationMode`; non-empty `annotationIds` with `annotationMode` equal to `IAGateway.ANNOTATION_MODE_EXTRACT` and empty or absent `mediaAssetIds`; an envelope with neither list is also null, today's no-intake case); otherwise `"unknown annotationMode: <value>"`, `"extraction mode with mediaAssetIds"`, or `"annotationMode without annotationIds"`.
- `handleRest` change: after `resolveRequestRoot` and `storeNewTask(topTask)`, `String rejection = envelopeRejection(jin)`; when non-null: `markTaskError(topTask, "INVALID_ENVELOPE", rejection)`, `System.out.println("ERROR: IA.handleRest() rejected envelope for task " + taskId + ": " + rejection)`, commit, return before either intake branch. Then: the media branch as today; the annotation branch as today when `annotationMode` is absent; when it is the extraction mode, do NOT use today's throwing `getObjectById(..., true)` loop: pass the id list straight to `intakeImportedBoxes(myShepherd, annotationIds, topTask, context, baseUrl)`, which loads them itself (below) and adds their assets to `topTask`.
- `static int IA.intakeImportedBoxes(Shepherd myShepherd, List<String> inputIds, Task root, String context, String baseUrl)`, returning the number of per-box children persisted plus WBIA groups handed to `runMatchProspects`. `inputIds` is the snapshot. Loading happens inside the staging `try`: `myShepherd.getAnnotation(id)` per id (null-tolerant); a null (deleted annotation) is skipped with a log line and never blocks the others; loaded annotations' assets are added to `root` (`root.addObject(ma)`) as the identification branch does. Then:
  1. `skipIdent = IBEISIA.taskParametersSkipIdent(root)`; `baseUrl` falls back to `getBaseURL(context)` when null.
  2. Stage, inside `try`: bin by `ann.getTaxonomy(myShepherd)` (Annotation.java:476, the encounter's taxonomy); an annotation with a null taxonomy or a null media asset goes into one child `new Task(root)` holding those annotations, status `error`, code `NO_TAXONOMY`. Per species `Task speciesTask = new Task(); speciesTask.setParameters(scrubRunParams(root.getParameters())); speciesTask.setObjectMediaAssets(distinct assets of that species' boxes, first-seen order); linkUnderRoot(speciesTask, root)`. For a species with `IAJsonProperties.iaConfig().getActiveMlServiceConfigs(taxy) != null`: per box `Task child = new Task(speciesTask); child.addObject(ann); child.addObject(ann.getMediaAsset()); child.addParameter(Task.PARAM_EMBEDDING_EXTRACTION, true); child.addParameter(Task.PARAM_IMPORTED_BOX, true); child.setStatus("initiated"); ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE); child.setQueueResumeMessage(job)` with job `{mlServiceV2:true, annotationId, taxonomyString: taxy.getScientificName(), taskId: child.getId(), __context, __baseUrl}`. Extraction is staged even with `skipIdent`. `makePersistent` every staged task, then `commitDBTransactionWithStatus()`, `beginDBTransaction()`.
  3. Staging failure (a false commit, or any exception inside the `try`, including loading): `rollbackDBTransaction`, `beginDBTransaction`, one child `new Task(root)` holding every annotation of `inputIds` that reloads by id (`getAnnotation`, nulls skipped) with status `error`, code `EXTRACTION_PERSIST` (message = the exception or "commit failed"), `markTaskError(root, "EXTRACTION_PERSIST", ...)`, checked commit (a false result or an exception is logged with the root id); return 0.
  4. Publish, each item in its own `try/catch`: for each ml-service child, `publishMlServiceJob(context, job)`; false or exception -> `ImportedBoxes.failChild(myShepherd, child, "ENQUEUE", ...)` (marks error and settles the child's asset). For each WBIA-only species with `skipIdent` false: `matchProspectsRunner.run(context, annotationIds, speciesTask.getId())` where `IA.matchProspectsRunner` is a package-private static seam defaulting to `new MlServiceProcessor(context).runMatchProspects(ids, taskId, null)` (it groups by encounter, inherits parameters through `matchIntakeParams`, and owns its failure recovery, marking a publication failure `QUEUE_FAILED` on the option; a WBIA-only species with `skipIdent` gets nothing); an exception escaping the seam -> one child `new Task(speciesTask)` holding that species' annotations, status `error`, code `MATCH_DISPATCH`. `static void setMatchProspectsRunnerForTests(...)`, null restores the default. After the loop, one checked commit persists every mark written in step 4 (false or exception: logged with the root id).

- [x] **Step 1: Write the failing tests**

`ImportedBoxesSettleTest` (pure; annotations built as in `AnnotationImportedBoxTest`, embeddings through `new Embedding(ann, "m", "1", new JSONArray().put(0.1))`):
```java
@Test void settledWhenEveryTrackedBoxHasAnEmbeddingOrATerminalTask() { ... }
@Test void notSettledWhileATrackedBoxIsPending() { ... }
@Test void boxesWithoutAnExtractionTaskDoNotBlockSettle() { ... }
@Test void unmarkedAnnotationsAreIgnored() { ... }
```
`IAEnvelopeRejectionTest` (pure): permitted shapes; unknown mode; extraction mode with media; mode without annotations.
`ImportedBoxIntakeDbTest` (fixtures: add to `IdentificationDbTestSupport` a helper `static Encounter boxedEncounter(Shepherd sh, String genus, String species, String importId, int boxes)` creating ONE media asset with `boxes` marked annotations on it, each `_bulkImportBbox:{importTaskId: importId}`, `matchAgainst` true, `acmId` set; IA.json installed with `IAJsonProperties.iaConfig().setJson(...)` as `MultiAlgorithmIntakeDbTest.iaJson()` does, one variant with `_mlservice_conf` and a vector `_id_conf` for the fixture species, one without; jobs captured with `mockStatic(IAGateway.class, CALLS_REAL_METHODS)` on `addToDetectionQueue`; WBIA intake observed with `IA.setDispatcherForTests(new IAIntakeCoreTest.RecordingDispatcher())`):
```java
@Test void mlServiceSpeciesGetsOneChildAndOneJobPerBoxAfterASingleCommit() { /* 2 boxes on 1 photo + 1 box on another: 1 species task under root holding 2 assets; 3 children each holding its ann + 1 asset, params importedBox + embeddingExtraction + importTaskId, status initiated, resume message == its job; 3 jobs; annotations processing-mlservice; assets detectionStatus null; every child id named by a job already loads from the DB inside the capture answer */ }
@Test void skipIdentStillEnqueuesExtraction() { ... }
@Test void wbiaOnlySpeciesRunsIdentificationThroughRunMatchProspects() { ... }
@Test void wbiaOnlySpeciesWithSkipIdentDoesNothing() { ... }
@Test void twoSpeciesOnOnePhotoAreRoutedByTheirOwnTaxonomy() { ... }
@Test void mixedEnvelopeIsRejectedBeforeAnyIntake() { /* annotationIds + extract mode + mediaAssetIds of a real asset: root error INVALID_ENVELOPE, no jobs, no children */ }
@Test void unknownModeIsRejectedBeforeAnyIntake() { ... }
@Test void plainAnnotationIdsStillTakeTheIdentificationBranch() { // (regression) }
@Test void enqueueFailureSettlesTheAsset() { /* 2 boxes on 1 photo; addToDetectionQueue false for the second job: child 2 error ENQUEUE; child 1 initiated; the asset's detectionStatus is still null (child 1 pending) BUT after a stubbed embedding is attached to box 1 and ImportedBoxes.settle is invoked for box 1, the asset completes; and a variant where box 1's job also fails: both error, asset complete immediately */ }
@Test void stagingExceptionYieldsOneErrorChildHoldingEveryBox() { /* a PersistenceManager that throws on the second makePersistent (wrap the test Shepherd's PM through a Mockito spy): root error EXTRACTION_PERSIST, one error child holding all input annotations, no jobs */ }
@Test void wbiaPublicationFailureIsHandledByTheExistingIntakeRecovery() { /* two WBIA-only species; the dispatcher's publish throws for the first species: its option is marked QUEUE_FAILED by the existing intake code, the second species' run is dispatched, no MATCH_DISPATCH child */ }
@Test void wbiaRunnerExceptionIsIsolated() { /* two WBIA-only species; the matchProspectsRunner seam throws for the first species: first species gets an error child MATCH_DISPATCH holding its boxes, second species' run is dispatched */ }
@Test void deletedAnnotationInTheEnvelopeDoesNotBlockTheOthers() { /* envelope [A, B]; delete A before handleRest: B gets its child and job; root not error */ }
@Test void annotationWithoutTaxonomyGetsAnErrorChild() { ... }
```

- [x] **Step 2: Run to verify they fail** — `mvn -q test -Dtest='ImportedBoxesSettleTest,IAEnvelopeRejectionTest,ImportedBoxIntakeDbTest'` (Docker running); expected: compilation failure.
- [x] **Step 3: Implement** per the Interfaces block.
- [x] **Step 4: Run to verify they pass**; `mvn -q test -Dtest='IAHandleRestRootTest,MultiAlgorithmIntakeDbTest,IdentificationIntakeDbTest'` stays green.
- [x] **Step 5: Commit** — `git commit -m "feat(ia): extract-then-identify intake for imported boxes"`

### Task 7: Processor lifecycle, pure parts and the finalization points (done, commit 4194d41b6f)

**Files:**
- Modify: `src/main/java/org/ecocean/ia/MlServiceProcessor.java:1032-1040` (`matchIntakeParams`), `:954-990` (`waitAndRunMatchInternal`), `:991-1020` (`runDeferredMatch`), `:1082-1215` (`runMatchProspects`), `:1118` (missing-annotation exit), `:1718` (seams)
- Test: `src/test/java/org/ecocean/ia/MlServiceProcessorMatchIntakeTest.java` (extend), `src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java` (extend; its `processorWithSkipIdent` factory (line 241) also overrides the new seam to return null, so the existing skip tests do no database access; the new completion tests configure the seam's result explicitly)

**Interfaces:**
- Produces: `MlServiceJobOutcome completeImportedChildIfSkipped(String taskId)` (package-private seam): one Shepherd transaction; strict load through `shep.getPM().getObjectById(Task.class, taskId)`: a `JDOObjectNotFoundException` means "nothing needed" (returns null); any other exception logs and returns `MlServiceJobOutcome.persistError("PERSIST", "could not complete skipped imported-box task <id>: <ex>")`; a loaded task that is not `isImportedBoxTask` returns null untouched; an imported one: `markTaskCompleted`, `commitDBTransactionWithStatus()`, null on success else the same `persistError`.
- Changes: `matchIntakeParams` = `IA.scrubRunParams(parentParams)` minus `ibeis.detection`, `Task.PARAM_EMBEDDING_EXTRACTION`, `Task.PARAM_IMPORTED_BOX`. The skip guards in `waitAndRunMatchInternal` and `runDeferredMatch`: `MlServiceJobOutcome done = completeImportedChildIfSkipped(taskId); return (done != null) ? done : MlServiceJobOutcome.ok(annotationIds)`. In `runMatchProspects`: (a) the missing-annotations exit (`anns.isEmpty()`, not `matchGroup`) writes `ImportedBoxes.failChild(shep, task, "STALE", "annotations missing")` when `Task.isImportedBoxTask(task)` (it settles the held asset), else today's `markTaskDroppedStale`; its commit becomes `commitDBTransactionWithStatus()` and a false result is logged with the task id (the outcome stays `stale`); (b) the empty-input exit keeps `markTaskCompleted(taskId)` (an imported extraction job never reaches it; an empty deferred payload could, and then completes without a run, which is today's behavior and acceptable); (c) the success branch (`failureCode == null`, not all-noWork): when `Task.isImportedBoxTask(parent)`, `markTaskCompleted(parent)` then `shep.commitDBTransactionWithStatus()`; false -> `failureCode = "MATCH_PERSIST"`, `failureMessage = "could not commit completion of imported-box task " + parentId` (existing recovery then marks the parent `error`). The DEFER arm is untouched: `ok` from a successful deferred publication completes nothing.

- [x] **Step 1: Write the failing tests**

`MlServiceProcessorMatchIntakeTest`:
```java
@Test void matchIntakeParamsStripsTheExtractionMarkers() { /* importTaskId, skipIdent, matchingSetFilter kept; importedBox, embeddingExtraction, ibeis.detection, PARAM_RUN_ID removed */ }
```
`MlServiceProcessorGateTest` (the processor subclass records `completeImportedChildIfSkipped` calls and returns a configured value):
```java
@Test void deferDoesNotCompleteImportedChild() { // (regression for the DEFER arm) }
@Test void skipIdentCompletesImportedChild() { ... }
@Test void skipIdentReportsAFailedCompletion() { ... }
```

- [x] **Step 2: Run to verify they fail** — `mvn -q test -Dtest='MlServiceProcessorMatchIntakeTest,MlServiceProcessorGateTest'`; expected: compilation failures.
- [x] **Step 3: Implement** per the Interfaces block.
- [x] **Step 4: Run to verify they pass**; the whole `MlServiceProcessor*Test` set stays green.
- [x] **Step 5: Commit** — `git commit -m "feat(ml-service): imported-box task finalization at the match hand-off"`

### Task 8: Processor lifecycle, extraction path (done, commit 72bb2a9c1f)

**Files:**
- Modify: `src/main/java/org/ecocean/ia/MlServiceProcessor.java:351-393` (`processExtraction`), `:467-548` (`loadExtractionContext`), `:855-905` (`persistExtraction`), `:1568` (`ensureMatchConfig` overload), `:1629` (`markTaskError(String,...)`), `:1968-2010` (`ExtractionContext`)
- Test: `src/test/java/org/ecocean/ia/MlServiceProcessorImportedBoxDbTest.java` (create; `extends IdentificationDbTestSupport`)

**Interfaces:**
- Produces: `ExtractionContext` gains `final boolean embeddingExists` and `final String[] existingMv`, with `static ExtractionContext existing(JSONObject mlConfig, JSONObject matchConfig, boolean multiPipeline, String taxonomyString, String iaClass, String[] mv)`. `JSONObject ensureMatchConfig(JSONObject matchConfig, String[] mv, JSONObject mlConfig)`: the existing defaulting (`:1568`) with the method/version taken from `mv` instead of a response; the response-taking overload delegates to it.
- Changes, only when `Task.isImportedBoxTask(task)` (non-imported tasks: unchanged; existing commits stay unchecked per Global Constraints):
  - `loadExtractionContext`: (a) annotation missing -> `ImportedBoxes.failChild(shep, task, "STALE", "annotation missing")`; media asset missing -> the same with reason "annotation media asset missing"; same stale outcomes returned. (b) `configs == null` -> `failChild(shep, task, "PENDING_SPECIES", "no active ml-service configuration for " + effectiveTaxonomy)`, commit, return the `pending-species` stale outcome. (c) embedding already stored (both the multi-pipeline and the single-config checks) -> `task.setStatus("processing")`, `ImportedBoxes.settle(shep, ma, annId)`, commit, return `ExtractionContext.existing(...)` with the stored embedding's method/version (multi-pipeline: `extractMv`; single: the embedding `ann.getEmbeddingByMethod(method, version)` of the match config's values, else the annotation's first embedding). (d) the INVALID, INVALID_IMAGE_URI and INVALID_BBOX exits use `failChild`.
  - `processExtraction`: when `ext.embeddingExists`, skip `client.extract` and `persistExtraction`; `matchConfig` = multi-pipeline ? `resolveMultiPipelineMatchConfig(IAJsonProperties.iaConfig(), new Taxonomy(ext.taxonomyString), ext.iaClass, ext.existingMv, ext.mlConfig)` : `ensureMatchConfig(ext.matchConfig, ext.existingMv, ext.mlConfig)`; then `waitAndRunMatch(List.of(annId), taskId, matchConfig, false, ext.multiPipeline)`.
  - `persistExtraction`: the missing-annotation exit (`:865`) uses `failChild(shep, task, "STALE", "annotation missing")`; in both the `exists` branch and the store branch, `task.setStatus("processing")` instead of `markTaskCompleted(task)`, then `ImportedBoxes.settle(shep, ann.getMediaAsset(), ann.getId())` before the commit.
  - The String-id `markTaskError(String taskId, ...)` (`:1629`): after marking, when the loaded task `isImportedBoxTask`, `ImportedBoxes.settle` on its held asset (`doneAnnId` null) in the same transaction.

- [x] **Step 1: Write the failing DB tests** (processor built with the package-private constructor: a `MlServiceClient` subclass whose `extract(...)` returns `{"embedding":[0.1,0.2,0.3], "embedding_model_id": "<method>-<version>"}` in the shape `parseEmbeddingMethodVersion` (`:1512`) accepts, counting calls, optionally deleting the annotation inside the call, or throwing a non-retryable `IAException`; the REAL gate `new MatchVisibilityGateImpl(context, openSearchStub)` through its package-visible two-argument constructor, the stub answering true to `waitForVisibility` and `waitForAnnotationMatchableIds`; a `RecordingPublisher`; `IA.setDispatcherForTests(new IAIntakeCoreTest.RecordingDispatcher() { runVector records Task.load(childId).getStoredStatus() at dispatch })`; IA.json with `_mlservice_conf` + vector `_id_conf` for the fixture species; fixtures from `boxedEncounter(...)`: one photo with two marked boxes A and B, each with its own child task `importedBox` + `embeddingExtraction` holding ann + asset, status `initiated`, under a species task holding the asset, under a root with `importTaskId`; a second fixture with one box)
```java
@Test void firstBoxDefersUntilItsSiblingExtracts() { /* process A: A has 1 embedding; A's child "processing"; asset null; publisher got 1 deferred payload (the real gate deferred on B) */ }
@Test void lastBoxSettlesTheAssetAndBothRunsAreCreated() { /* then B: asset complete; B's run created (dispatcher saw "processing" at dispatch) and B's child completed; then runDeferredMatch(A's payload): A's run created, A's child completed; umbrella params carry importTaskId and no importedBox */ }
@Test void settleAfterSiblingError() { /* client throws non-retryable for B: B's child error, asset complete */ }
@Test void deferredReFireProceedsAfterSiblingEnqueueFailure() { /* A extracted and deferred; then ImportedBoxes.failChild(B's child, "ENQUEUE"): asset complete; runDeferredMatch(A's payload) creates A's run */ }
@Test void embeddingExistsStillMatchesWithDefaultedConfig() { /* single-box fixture whose _id_conf omits method/version; process twice: second run makes no client call, creates a second run whose vector option carries the same method/version as the first */ }
@Test void pendingSpeciesIsErrorForImportedBox() { /* single-box fixture, IA.json without _mlservice_conf: child error PENDING_SPECIES, asset complete */ }
@Test void annotationDeletedDuringExtractionIsErrorStale() { /* single box; the client stub deletes the annotation before returning: persistExtraction's exit: child error STALE, asset complete */ }
@Test void annotationDeletedBeforeDeferredReFireIsErrorStale() { /* A deferred; ImportedBoxes.failChild(B's child, "ENQUEUE") so the asset settles and the gate will pass; delete A; runDeferredMatch(A's payload): runMatchProspects' missing-annotation exit: A's child error STALE, asset still complete */ }
@Test void skipIdentCompletesTheChildWithoutARun() { /* root skipIdent true: embedding stored, child completed, no run, asset complete */ }
@Test void detectionChildLifecycleUnchanged() { // (regression) }
```

- [x] **Step 2: Run to verify they fail** — `mvn -q test -Dtest=MlServiceProcessorImportedBoxDbTest`; expected: compilation failure / assertion failures on the new statuses.
- [x] **Step 3: Implement** per the Interfaces block.
- [x] **Step 4: Run to verify they pass**; `mvn -q test -Dtest='MlServiceProcessor*Test,MatchVisibilityGateImplTest,MultiAlgorithmIntakeDbTest'` stays green.
- [x] **Step 5: Commit** — `git commit -m "feat(ml-service): imported-box extraction lifecycle and asset settle"`

### Task 9: Startup replay of stale imported-box children (done, commit 824f93140f)

**Files:**
- Create: `src/main/java/org/ecocean/ia/ImportedBoxReplay.java`
- Modify: `src/main/java/org/ecocean/StartupWildbook.java:213-217` (call after `runStaleMlServiceReconciliation`), `src/main/java/org/ecocean/ia/Task.java` (a package-private `void setModifiedForTests(long)` next to `setCreated`)
- Test: `src/test/java/org/ecocean/ia/ImportedBoxReplayDbTest.java` (create; `extends IdentificationDbTestSupport`)

**Interfaces:**
- Produces: `public final class ImportedBoxReplay` with `static List<String> fetchStaleResumeMessages(Shepherd shep, long modifiedCutoff, int max)` (JDOQL on `Task`: `status != 'completed' && status != 'error' && status != 'dropped-stale' && modified < :cutoff && parameters.indexOf(:marker) >= 0`, `marker = "\"importedBox\":true"`, ordered `modified ascending`, range `0..max`; returns the non-null `queueResumeMessage` values; DataNucleus 5.2 supports `String.indexOf` in JDOQL, see `MetricsBot.java:508`) and `public static int replay(String context, long modifiedCutoff)` (max 500; publishes each message with `IAGateway.publishToLane(context, message)`; logs `[INFO] ImportedBoxReplay: republished N stale imported-box jobs`; returns the count; an exception is logged and the count so far returned).
- `StartupWildbook`: `ImportedBoxReplay.replay(context, System.currentTimeMillis() - STALE_MLSERVICE_THRESHOLD_MS)` inside its own `try/catch`, directly after the stale-mlservice reconciliation. The constant stays private in `StartupWildbook`.

- [x] **Step 1: Write the failing DB tests**
```java
@Test void onlyStaleNonTerminalImportedBoxChildrenAreReplayed() { /* (1) importedBox child initiated, modified now-2h, message M1; (2) importedBox completed, old; (3) importedBox initiated, modified now-5min; (4) detection child (ibeis.detection) initiated, old; (5) importedBox old, null message -> [M1] */ }
@Test void replayPublishesThroughTheLaneClassifier() { /* mockStatic publishToLane capturing: replay() == 1 and the message equals M1 */ }
@Test void replayIsDuplicateTolerant() { /* an initiated child whose job is still queued (just published, modified set old): replay publishes it again; documented as accepted */ }
@Test void replayAfterCrashProducesTheRun() { /* single-box fixture with the Task 8 harness; a gate stub that throws RuntimeException on its first call simulates the crash after persistExtraction committed: the exception escapes process(), the child is "processing" with the embedding stored and the asset complete; age the child; replay() republishes its extraction job; process the captured job with a gate that answers READY: embedding-exists path, run created, child completed */ }
```
- [x] **Step 2: Run to verify it fails** — `mvn -q test -Dtest=ImportedBoxReplayDbTest`; expected: compilation failure.
- [x] **Step 3: Implement** per the Interfaces block.
- [x] **Step 4: Run to verify it passes.**
- [x] **Step 5: Commit** — `git commit -m "feat(startup): replay stale imported-box extraction jobs"`

### Task 10: Full verification, Codex review, pull request

**Files:**
- Modify: `docs/superpowers/specs/2026-11-06-bulk-import-annotation-bboxes-design.md` only if implementation forced a design change (record it in section 7 "Settled calls"); `docs/superpowers/specs/2026-11-06-bulk-import-annotation-bboxes-codex-review-log.md` (append the branch review rounds).

- [x] **Step 1: Full backend test suite in the worktree** (BUILD SUCCESS: 219 classes, 1897 tests, 0 failures, 0 errors, 7 pre-existing skips) — `mvn -q clean install` (the frontend `npm ci` runs; allow it). Expected: BUILD SUCCESS, zero failures. Record the counts.
- [x] **Step 2: LF check** — `git diff --name-only main... | xargs grep -lP '\r$'` prints nothing.
- [x] **Step 3: Codex review of the branch** (three rounds; converged on round 3) (codex-review skill): prompt lists every file touched, asks for correctness of the lifecycle against spec 4.4 (including the exit audit from the plan reviews), the neutrality statement in Global Constraints, simplicity and reuse; iterate until "converged / no Major findings". Append each round to the review log.
- [x] **Step 4: Open the PR** — `gh pr create --base main --title "Bulk import boxes A: IA hand-off for imported annotations (behavior-neutral)" --body-file <body>` with: scope (spec 4.4), the neutrality statement with its metadata change and three failure-path exceptions verbatim, the lifecycle summary, test commands run with counts, "Reviewed with Codex (N rounds)", the generated-with line; then `gh api -X PATCH repos/WildMeOrg/Wildbook/issues/<n> -f milestone=29` (milestone 11.1).
- [x] **Step 5: Commit the plan checkboxes and the spec touch-ups** on the branch; push.

## Self-review notes

- Spec 4.4 coverage: envelope and partition (Task 4), durability (Tasks 4, 5, 6), lane (Task 3), envelope validation and both intake branches (Task 6), lifecycle single finalization point with the full exit audit (Tasks 7, 8), recovery parity (Task 9 plus the unchanged re-send page; its `validForIdentification` check accepts a feature-based box, verified by reading `IBEISIA.java:3660-3700`, no code change), marker hygiene (Task 7), asset settle instead of a gate change (Tasks 6, 8), status flags (Task 2). Section 4.5 needs nothing beyond Task 2.
- Not in PR A by design: the importer side (`annotationsImported` is only read here; PR B writes it), `SubmissionProcessing.prepare` using the helper (PR E), the page (PR C), the skill (PR D).
