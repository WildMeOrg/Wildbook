I found no Critical issues. N1's specific problems are fixed, but one Major issue remains: nothing ever deletes submission rows, so the new cleanup bound will eventually stop cleanup for good.

## N1 status: fixed as described

What I checked, in `SubmissionJobs.java:232-265` and `SubmissionWorker.java:29-33`:

- **No long-held transaction or lock pile-up.** Each page of 100 rows is read in its own transaction, which is closed before any row is processed. Each candidate row (cancelled, or an expired draft) gets its own short transaction that tries the row's lock without waiting, re-reads the row, and releases. The worker's lock is never held across the whole pass.
- **Rows that are busy or not yet final keep their files.** If the lock is taken, the file list read earlier is kept. The re-read also handles a race with commit: an `enqueue` that commits after expiry turns the row into `queued`, and its files are kept.
- **Paging is safe.** Nothing deletes `Submission` rows, and new rows only push existing rows later in the order. The worst case is reading a row twice, which is safe; no row can be skipped.
- **Stopping early deletes nothing.** Hitting the 10,000-row bound or the 10-second deadline returns before `storage.cleanup`. The file-cleanup step only removes directories that are old, not symlinks, contain only regular files, and whose files are also old. An error on one directory doesn't stop the others.
- **The worker changes are in place.** It records the cleanup time before running cleanup, catches cleanup failures separately, and still claims work afterwards. Failed indexing moves to `phase=failed` and leaves the pending list. Reruns are limited to rows with `phase=unknown`.
- **The DB test covers the main behaviours.** Blobs of draft, imported and `needs_reconciliation` rows are kept. A cancelled row's blob is kept while its lock is held and removed after release. An old orphan is removed on the first pass.

## Major: once row count passes the bound, cleanup stops for good

The count only ever grows:

1. **The row count only grows.** Cancelled, expired, imported and failed rows are never deleted. The paging query (`SubmissionJobs.java:237`) counts every row in the context, so normal use eventually passes 10,000 rows. After that, cleanup logs "paused" every hour and never deletes anything again.
2. **Every pass re-checks every old cancelled or expired row.** Nothing records that a row's files were already released, so each of these rows costs a lock transaction on every pass (`:247-248`), and every pass starts again at offset 0. As they pile up, the 10-second deadline (`:245`) starts firing before the end is reached, well before 10,000 rows.
3. **One user can cause this on purpose.** Only *active* drafts count toward the 20-per-account limit (`SubmissionStore.java:36-41`), and cancelling frees the slot at once. Repeating create → upload up to 200 MB → cancel builds up rows until cleanup stops. After that, every blob uploaded and cancelled stays on disk forever. "Fail closed" then means the staging disk slowly fills up.

A related gap: blobs of `imported` and `failed` rows are kept forever by design (`:249-250`, and the test asserts it). So even below the bound, staging only grows with each import, and there is no way to release them.

**Suggested fix, a small change:**
- Once a cancelled or expired row has been checked under its lock, write `filesJson = "[]"` in that same short transaction and commit it.
- Filter the paging query to rows that still hold files (`filesJson != '[]'`).

The bound and deadline then apply only to rows that actually hold blobs, and each old row costs one transaction in total. Separately, decide on a release rule for `imported` and `failed` blobs (for example, some time after `imported` with derivatives complete), or document that operators must clear staging by hand. Rate-limiting create+cancel per owner would close the deliberate route.

## Minor
- **`SubmissionFiles.cleanup` (`:179-197`) has no time limit.** The 50-directory cap counts only deletions, so the whole directory listing is still scanned and checked. That listing grows without limit because of the kept imported blobs.
- **`reconcileStaleClaims` (`SubmissionJobs.java:209-219`) still collects up to 20 row locks in one transaction.** It's limited and brief, but it's the same pattern N1 removed from cleanup. If it throws, it also aborts the whole tick, including cleanup, because it runs first.
- **Certain failures keep their files.** A `failed` row that came from a `PreImportRejection` could release its blobs instead of keeping them like the uncertain states.
- **The test runs against the shared `context0` database with rows from other tests.** It's fine now, but the assertion that the orphan is removed would silently depend on the total row count staying under the bound.

This was a read-only review: I ran no commands and didn't run the tests or build, so I haven't seen the full build's results.
