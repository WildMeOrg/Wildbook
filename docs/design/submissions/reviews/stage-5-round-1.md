# Stage 5 review: submission queue, worker, importer and lifecycle

This was a read-only review. I didn't run any builds or tests, and I edited nothing.

**Verdict:** no Critical findings. There are 2 Major findings and 11 Minor ones. Most of the properties you listed hold:

- **Acceptance before 202:** the ImportTask and the queued draft commit in one transaction before the 202, and a lost response replays.
- **One execution regardless of keys:** the per-submission advisory lock plus the `jobId` check allow only one accepted execution.
- **Claims across instances:** a worker-wide lock plus the "any `importing` row" guard enforce one active import across the installation.
- **Revalidation before import:** owner, enrollment, config digest and manifest are rechecked before anything is copied.
- **Caller-owned transaction:** in deferred mode the bulk importer only calls `makePersistent`, with no hidden commits or progress writes.
- **Atomic import result:** domain objects, `imported(result)` and ImportTask completion commit together.
- **Uncertain-commit reread is fenced:** `fail()` blocks on the old session's advisory lock, so a commit that actually landed shows as `imported`.
- **Derivatives only after domain commit.**
- **Lifecycle:** the worker is off by default and stops on shutdown.
- **Admission off still drains accepted work:** the worker only checks `workerEnabled`.
- **Unenrolled owners** fail with the terminal `failed` state before any copy.
- **Strict field subset:** unsupported fields are rejected, and `Encounter.id`, sighting and individual keys can't get in.
- **No automatic AI dispatch.**
- **Owned reads** skip the admission check in both the filter and the servlet.

## Major

**M1. The postprocess scan can permanently skip new imports, and restart replays all history** (`SubmissionJobs.java:129-131`, `SubmissionWorker.java:34-37`)
- **Problem:** the scan query matches `derivatives == 'complete'` with no filter on `phase`. It takes the oldest 10,000 rows first, and done items are only skipped via the in-memory `dispatched` set.
  - Once about 10,000 finished submissions exist, rows that still need derivatives fall outside the window and never get derivatives or indexing. Nothing reports it.
  - After every restart, and in every JVM, each tick replays indexing for every finished submission. The whole loop runs while holding the JVM's single resource slot, so uploads and validation get 429 until it finishes.
  - `dispatched` grows without limit.
- **Fix:**
  - Split it into two queries. Do the real work first: `derivatives == 'pending' || (derivatives == 'complete' && phase == 'pending')`.
  - Do replay separately and bounded: `phase == 'unknown'` limited by a recent import-time window or a per-JVM start marker, processed in batches (for example 50 per tick).
  - Replace the unbounded `dispatched` set with a replay marker, or cap it.

**M2. A second JVM can wrongly mark derivatives `unknown` because staleness uses the import-claim time** (`SubmissionJobs.java:142-149`, `190`, `199`)
- **Problem:** `postprocess` commits `derivatives = running`, releases the lock, then takes the lock again in a new transaction. `reconcileStaleClaims` judges `running` rows by `workStartedAt`, which is the time of the import claim.
  - If postprocessing starts more than an hour after the claim (worker toggled off, restart, or backlog), another JVM's reconcile can grab the lock in that gap and mark the derivatives `unknown`.
  - The derivatives are then held for manual reconciliation even though nothing was interrupted.
- **Fix:** add a `derivativesStartedAt` field, set it in `derivatives("running")`, and use it in the reconcile filter. Alternatively, use a claim token that the second transaction checks.

## Minor

1. **One bad item stops the rest of the tick** (`SubmissionWorker.java:34-38`). An exception in one `postprocess` aborts the whole loop, and the same oldest item fails again every tick. Wrap each item in its own try/catch and log it with the item's id.
2. **The ImportTask is checked too late** (`SubmissionImporter.java:57-58`). A missing ImportTask (for example, deleted from the legacy task UI) is only detected after the asset copies. That leaves copied files behind and marks the job uncertain when it could have simply failed. Move the check before `makeMediaAsset`.
3. **Error classification relies on exception type** (`SubmissionJobs.java:84`). Anything thrown as a `SubmissionException` counts as "no side effects". That's true today, but only by convention.
   - A 503 lock timeout in `execute` before `run` becomes a terminal `failed`, and the draft can't be committed again.
   - A validation JSON missing a digest key throws a `JSONException`, which becomes `needs_reconciliation`.
   - Fix: add a dedicated pre-import rejection type thrown only before the first copy. If the lock or open fails before the attempt starts, leave the row `importing` rather than failing it.
4. **Reconcile leaves the ImportTask at `queued`** (`SubmissionJobs.java:198`). `fail()` updates the ImportTask status but reconcile doesn't, so legacy task views show a live task. Set the task to `needs_reconciliation` there too.
5. **A crashed import blocks the whole queue for an hour.** One JVM crash mid-import stops all claims installation-wide until reconcile runs 60 minutes later. Document this, or cut the threshold for claims whose lock `tryLock` finds free (the owning session has died).
6. **Uncertain claim commit** (`claimNext`, `SubmissionJobs.java:67-68`). If the claim's commit result is unknown, the row may be `importing` with no JVM running it. It's held rather than rerun, which is safe but blocks the queue until reconcile. A `claimToken` would let the claimer re-read and continue.
7. **Cleanup inventory** (`SubmissionJobs.java:208-211`).
   - It scans every submission in the context and stops for good once there are more than 10,000, with no log message.
   - Blobs for `failed` and `imported` drafts are never deleted, so private staging duplicates the asset store forever.
   - Fix: only fetch rows whose blobs must be kept (not cancelled, not expired) and page through them. Log when cleanup refuses to run. Document the retention policy for `imported` and `failed`.
8. **Expiry race** (`SubmissionJobs.java:214`). A draft committed just before it expires can have its files deleted by a cleanup that read the inventory just before the commit. The importer's revalidation turns this into `failed`, so no data is lost. Excluding drafts that expired less than a day ago would close the window.
9. **Worker startup and resource slot.**
   - The worker is built during startup only when `workerEnabled` is already true, so turning it on at runtime needs a restart.
   - It starts before OpenSearch and the IndexingManager are initialised; the first tick is 10 s later, so this is only a delay risk.
   - The worker shares the single per-JVM slot with uploads, so steady upload traffic can starve it; its 429s are silent.
   - Fix: start the worker at the end of `contextInitialized`, and log repeated 429 skips.
10. **Staging overlap checks and permissions** (`SubmissionFiles.java:29-45`).
    - The overlap checks don't cover LocalAssetStore roots, which may be served outside webapps.
    - Directory permissions are set after creation, and files keep the umask default.
    - Fix: reject overlap with asset store roots, and create directories and files with owner-only permissions atomically.
11. **Missing indexes** (`package.jdo`). There are none on `(context,state)`, `(context,ownerId,state)` or `jobId`, so every tick's queries scan the whole table.

## Tests
Gaps in `SubmissionStoreDbTest`, based on reading the source:
- Two `claimNext` calls running concurrently: the current "one claim" test calls it twice in sequence.
- A commit that lands but reports failure (a subclass that commits, then returns `false`), asserting the row ends up `imported`, not `needs_reconciliation`.
- `reconcileStaleClaims`: an `importing` row older than an hour becomes `needs_reconciliation`, and a locked row is skipped.
- `postprocess`: a failure mid-generation leaves `running` and nothing reruns; a replay after restart re-queues indexing only.
- `cleanup`: it refuses above the cap, and it keeps blobs for active, uncertain and imported drafts.
- Execution with an unenrolled owner ends `failed` with no asset copy.
- `readyJob`'s validation JSON has no `configDigest` or `manifestDigest`, so no DB test drives the real `SubmissionImporter`.

## Contract mismatches (for stage 6)
- `results.links.importTask` points to `/import/importTask.jsp`, which doesn't exist anywhere under `src/main` (probably removed with WebImport). The React route is `/react/bulk-import-task?id=`.
  - That page may offer detection or identification actions. Check it before linking, to keep "no AI dispatch" true.
- Capabilities advertise `idempotencyRetentionSeconds` = 7 days, but create and commit keys are replayed forever.
- A lost-response commit replay after admission is turned off returns 503 `ADMISSION_DISABLED`, not the original 202. Clients should fall back to GET status.
- Enabling the worker at runtime needs a restart; stopping takes effect on the next tick.
- State values to document:
  - `derivatives.state`: `pending | running | complete | unknown`
  - `indexing.state`: `pending | unknown`
  - error codes: `IMPORT_FAILED`, `COMMIT_OUTCOME_UNCERTAIN`, `INTERRUPTED_EXECUTION`
  - GET submission omits `derivatives`; only results includes it.
