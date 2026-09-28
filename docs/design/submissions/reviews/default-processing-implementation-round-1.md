# Review: default detect-and-identify for submissions

I had no way to read files or run commands in this session, so everything below comes from the code and diff you pasted. Where a finding depends on code I couldn't see (`IAGateway`, `FileQueue.getNext`, `SubmissionJobs.postprocess`, `SubmissionImporter`), I've marked it **verify**. I made no code changes.

## High

**1. Nothing tests that IAGateway accepts the message. (verify)**
No test sends a message from `prepare` to the real consumer. `realAiTasksAndResumeMessageAreCommittedBeforePublishing` only checks the Task rows. Three things to check against the legacy producer:
- **`__handleBulkImport` type:** it's set to `System.currentTimeMillis()`. If the consumer routes with `optBoolean("__handleBulkImport")`, org.json returns `false` for a number, so the message would never reach the bulk-import branch.
- **Mixed message shape:** the message combines `__handleBulkImport` with v2 fields (`v2`, `mediaAssetIds`, `taskId`). If `handleBulkImport` expects its own payload (for example, a map of encounter to media), the two formats don't fit together.
- **Missing media on the Task:** legacy detection tasks usually call `setObjectMediaAssets(...)`. The new parent and child tasks carry only parameters. Callbacks and the bulk-import progress page may depend on that link.

Suggested test: send a `prepare` output message through `IAGateway.processQueueMessage` with WBIA mocked, and assert which handler branch it takes.

**2. `records.mediaAssets` doesn't come from real importer output. (verify)**
Both the unit test and `importedForAi()` build `resultJson` by hand. The importer diff shows a per-row `mediaAssetIds`, but not a top-level `records.mediaAssets` array of IDs. If that key is missing, or holds objects instead of IDs, every real submission would go `failed` on a `JSONException`, or publish objects as `mediaAssetIds`. Asset IDs shared between rows would also be sent twice. Add a test that feeds real `SubmissionImporter.execute` output into `prepare`.

**3. Detection is on by default with no gate and no off switch.**
- **Capabilities:** `processingModes` advertises `detect-and-identify` unconditionally, and omitting the mode now selects it. If a deployment has no IA configured, every default submission ends up `failed` after import.
- **Non-FileQueue deployments:** the check that the detection queue is a `FileQueue` runs inside the `publisher` lambda. That is after phase 1 has already committed the Tasks and the `dispatching` claim, and after `publishing = true`. So a publish that certainly never happened gets recorded as `unknown`, with orphan Tasks, on every submission. Resolve the queue and check its type before claiming, and treat that failure as `failed`.
- **No off switch:** there's no policy flag to pause AI handoff while leaving the worker running. If IA is down, every default submission fails for good.

**4. `publishChecked` writes its temp file inside the directory the consumer reads. (verify)**
`Files.createTempFile(queueDir, "addToQueue-", ".tmp")` creates the file in the live queue directory. If `getNext` lists every file and claims by renaming, it can pick up a half-written `.tmp`. Our `ATOMIC_MOVE` then fails with `NoSuchFileException`: we record `unknown` while the consumer processes a truncated message. Confirm that `getNext` skips `addToQueue-*.tmp` (or use a sibling staging directory), and add a test with a consumer running at the same time. Smaller points:
- **`ATOMIC_MOVE` support:** on a filesystem without it, every publish lands in `unknown`. Check this at deploy time.
- **Permissions:** temp files are created `0600`, which matters if another uid consumes the queue.
- **Crash durability:** there's no fsync on the directory after the rename, so a message already marked `dispatched` could be lost in a crash.

**5. The ImportTask is stranded when AI handoff doesn't complete.**
The importer sets `pending-detection` and `prepare` sets `processing-detection`. Nothing updates the ImportTask when `aiState` becomes `failed` or `unknown`, including the reconcile path for `derivatives == unknown`. The legacy re-ID button requires `complete`, so these tasks have no way forward in the UI at all, not just a manual operator step.

Also **verify** two things:
- **Lost updates:** `postprocess`, `replayBatch` and `holdFailedIndexing` must not write `ImportTask.status`. `pending()` doesn't wait for indexing to finish, so postprocess can overlap dispatch. If they write status without holding the same `submission:` lock, the last writer wins.
- **Legacy screens:** check whether legacy list, delete or cleanup screens treat any status other than `complete` as busy or blocked.

## Medium

**6. `reconcile` isn't isolated from the rest of the tick.**
It runs before `claimNext` with no try/catch. Any failure aborts every tick, so imports stall too. Examples: `find` returning null (NPE) if cleanup ever deletes an imported row, or `commit` returning false. Wrap it the way cleanup is wrapped.

**7. `reconcile` doesn't re-check its conditions after taking the lock.**
The ID list is read before `tryLock`. A row picked for `pending && derivatives == unknown` could have moved to `derivatives == complete` and been claimed by a dispatcher in the meantime. `reconcile` would then flip a fresh `dispatching` claim to `unknown`. That's safe (nothing is published twice), but it strands the work. Re-check `aiStartedAt < cutoff` and `derivatives` after locking.

**8. Detection status is wrong for failed imports.**
`getAiState()` returns `pending` when `aiState` is null and the mode is identify. So `failed` and `needs_reconciliation` imports, which have a `jobId`, show `detection: pending` indefinitely. Report `pending` only when `state` is `queued`, `importing` or `imported`.

**9. An AI `failed` state has no reason attached.**
`errorCode` stays null, so clients see `detection: failed` with an empty `errors` array.

## Low

- **Phase-2 fencing:** phase 2 only checks for `dispatching`. Storing `aiStartedAt` and matching it in phase 2 would fence against a hung dispatcher if an operator ever resets `unknown` to `pending`. Rare today, because `prepare` rejects an existing IA task.
- **Masked exception:** in the phase-1 and phase-2 catch blocks, if `settle` throws, it replaces the original exception, so the log loses the root cause.
- **Idempotency edge case:** a create with explicit `import-only`, retried with `processing` omitted, succeeds. That follows from normalizing against the saved mode. It's acceptable, but document it.
- **Contract change:** the default changes while `contractVersion` stays `"1"`. Tell pilot clients that omitting the mode now starts detection and identification.
- **Schema (verify):** DataNucleus must be configured to add columns (`autoCreateColumns` or `autoCreateAll`) for `aiState` and `aiStartedAt` on the existing table. Old rows have NULL there, which `pending()` correctly skips.

## Tests

**Concrete issues:**
- **Flaky shared DB:** `aiFailureAndStaleDispatchAreHeldWithoutRepublishing` calls the global `reconcile("context0")`, which has an unordered 20-row limit, against a database other tests share. If more than 20 matching rows are left over, this test's rows may never be reached.
- **Possible compile break:** in the unit test, `verify(sh, never()).storeNewTask(any())` won't compile if `storeNewTask` is overloaded.
- **Concurrency test:** the two-thread dispatch test doesn't force the threads to overlap. It shows at-most-once, not that the lock is exercised. Adding a latch inside `prepare` would.

**Missing coverage:**
- importer status by mode (`complete` vs `pending-detection`)
- `SubmissionJobs` passed parameters (`processing`, `skipDetection`, `skipIdentification`)
- the detection shape in the results endpoint
- a real DB row with a NULL `aiState` column (a pre-migration row)
- the publisher-type check in the default `dispatch` overload
- the reconcile and cleanup interaction
- the consumer race from item 4

## What checks out

- **Transaction integrity:** phase 1 commits the Tasks, the ImportTask link and the claim in one transaction. A rolled-back commit leaves the row `pending`, and since nothing was persisted, a retry doesn't duplicate work.
- **No retry after an ambiguous publish:** such publishes go to `unknown` and are never retried.
- **`reconcile` locking:** it uses `tryLock`, so it can't deadlock.
- **Old rows:** rows with a missing mode, and explicit `import-only` rows, stay `skipped`.
- **Legacy paths:** `publish()` itself is untouched, and none of the pasted code calls `Shepherd.storeNewTask` or reads `getQueueResumeMessage`.

The items most likely to break real submissions right away are 1–3. Each needs a check against the real IAGateway and importer code, which I couldn't open here.
