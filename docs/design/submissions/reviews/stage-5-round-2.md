I found 1 new Major issue and no Critical ones. Both earlier Majors (M1 and M2) are fixed. This was read-only: I ran nothing, edited nothing, and haven't seen any build or test results.

## Your corrections, checked against the code

| Item | Status |
|---|---|
| M1: split pending and replay queries | ✅ `SubmissionJobs.java:130`: the pending query only matches rows with derivatives `pending`, or `complete` with indexing `pending`. The limit is 10. `replayBatch` (`:137-147`) only matches `complete` with indexing `unknown`, with `createdAt <= startup`, a stable `createdAt, id` order and pages of 5. The unbounded `dispatched` set is gone. |
| Worker: pending first, per-item catch, one replay pass | ✅ `SubmissionWorker.java:37-50` |
| M2: separate derivative timestamp | ✅ `Submission.java:55` sets `derivativesStartedAt` when derivatives go `running`, and the reconcile filter uses it (`SubmissionJobs.java:202`). |
| ImportTask checked before copying files | ✅ `SubmissionImporter.java:29-30`, thrown as `PreImportRejection` |
| Reconcile updates the ImportTask | ✅ `SubmissionJobs.java:212` |
| Worker starts at the end of startup | ✅ `StartupWildbook.java:227` |
| Busy (429) skips are logged | ✅ at most once a minute (`SubmissionWorker.java:52`) |
| Cleanup: try-lock, refresh, keep busy drafts | ✅ This closes the old expiry race (Minor 8). It also causes the new Major below. |
| Refusal above the cap is logged | ✅ |
| Results link | ✅ It now points to `/react/bulk-import-task?id=`. I didn't recheck whether that page offers detection or identification actions. |
| Indexes | ✅ `package.jdo:6-8` |

## Major

**N1. A cleanup failure stops all imports, and the new cleanup locks make that failure likely** (`SubmissionJobs.java:220-241`, `SubmissionWorker.java:29-32`)
- **Lock build-up:** each expired or cancelled draft gets its own advisory lock, all in one transaction, and none is released until the whole inventory has been read.
  - Expired and cancelled rows are never deleted, so their number only grows.
  - PostgreSQL keeps advisory locks in a shared table sized by `max_locks_per_transaction × (max_connections + max_prepared_transactions)`. With default settings that's about 6,400 locks for the whole server. Below the 10,000-row cap, cleanup can hit `out of shared memory`, and while it holds all those locks, other sessions' lock requests can fail too.
- **What happens after a failure:**
  - The SQL error becomes a 503 `SubmissionException`. Any `IOException` from `storage.cleanup` (for example, permission denied on one blob) has the same effect.
  - Either one ends the tick before `claimNext`, and `lastCleanup` is never updated. So cleanup runs and fails again on every 10-second tick.
  - Result: work that already got a 202 never runs, and postprocessing and replay stop too. The only symptom is a repeating "requires inspection" / "unavailable" log line.
- **Fix:**
  - Set `lastCleanup` before calling cleanup, and wrap cleanup in its own try/catch so it can never block claiming.
  - Delete blobs one directory at a time and catch errors per directory.
  - For each expired or cancelled candidate, either use a short transaction per draft, or take a session lock with `pg_try_advisory_lock` and release it with `pg_advisory_unlock`, so locks don't pile up.

## Minor

1. **Replay uses the draft's creation time and re-queues all history on every start.**
   - `createdAt` is when the draft was created, not when it was imported or dispatched. So submissions imported after startup can still be replayed (harmless duplicates).
   - More importantly, each JVM start re-queues indexing for every imported submission ever, 5 every 10 s, and every JVM does it.
   - The indexing queue is in memory (`IndexingManager.java:29`), so only rows dispatched shortly before the previous shutdown can have lost entries.
   - Fix: add a `dispatchedAt` field, set it with `phase("unknown")`, and only replay rows where `dispatchedAt` falls in a window before startup.
2. **Bad rows can fill the pending window.** A row whose derivatives are `complete` but whose indexing step keeps throwing stays `complete`/`pending` and is picked up every tick. Ten such rows fill `setRange(0, 10)` and hide newer work. A persistently failing derivative step doesn't have this problem, because it stays `running` and reconcile later marks it `unknown`. Fix: add an attempt count or a last-attempt time and skip rows that keep failing.
3. **The worker still holds the JVM's single intake slot during derivative work** (from the old Minor 9). One tick can generate derivatives for 10 submissions × up to 200 images while holding the slot and a stage-2 transaction. Uploads and validation get 429 for the whole time. Consider not taking the slot for postprocessing and replay, or processing one submission per tick.
4. **Cleanup inventory.** It still loads full `Submission` rows, including `rowsJson`, which can be up to 2 MB each, for up to 10,001 rows in one persistence manager. The cap counts terminal rows, which are never deleted, so it will eventually refuse permanently. Blobs for `imported` and `failed` drafts are still kept forever. Fix: fetch only the fields cleanup needs, use pages, and decide whether rows expire and how long blobs are kept.
5. **Lock timeout before an import starts** (`SubmissionJobs.java:83`). The row stays `importing`, which blocks the installation-wide queue for an hour. Reconcile then marks it `needs_reconciliation` even though nothing ran. That's safe and matches the old Minors 5 and 6, but it should be documented as a known limitation.
6. **Schema:** `derivativesStartedAt` is a primitive `long` added to an existing table. On any dev or staging database that already has `SUBMISSION` rows, check how DataNucleus schema auto-update adds the column (NOT NULL without a default).

## Test gaps in the new DB tests
- `staleClaimsAreHeld…` (`SubmissionStoreDbTest.java:251`) only checks the negative case: a fresh derivative claim isn't held. Nothing checks that an old `derivativesStartedAt` becomes `unknown`, or that a locked row is skipped.
- `pendingWork…IsBounded` (`:263`):
  - It never tests the startup watermark (it passes `now`) or that paging advances with the offset.
  - The `work.contains(pending)` check depends on fewer than 10 older pending rows existing in the shared container. Other tests, such as `lostCommitAcknowledgment…` and the real-adapter test, leave imported/pending rows behind. That makes it order-dependent. Filter by the test's own IDs or use a fresh context.
- `lostCommitAcknowledgment…` runs the steps one after another, so it proves that "imported wins" but not the lock fence during an in-flight commit. That's acceptable, but name it accordingly.
- Still no cleanup tests (refusal at the cap, keeping blobs, busy drafts retained, lock build-up with many expired rows).

The strict field subset is unchanged, as you said. The asset-root and atomic-permission Minor is left for you as agreed.
