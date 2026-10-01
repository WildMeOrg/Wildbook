I found no remaining Critical or Major issues in SubmissionJobs, SubmissionStore, SubmissionWorker, SubmissionFiles, SubmissionImporter, the `Submission` entity, `package.jdo` or `SubmissionStoreDbTest`. The round-3 cleanup fix works as described. This was read-only: I ran no commands or builds, so none of the tests have been run.

## Confirmed fixed

- **Cleanup no longer gets permanently stuck.** `SubmissionJobs.java:237-261` pages through rows with non-empty `filesJson` in `id` order, 100 at a time, with no row cap. Each release takes its own short lock and commits (`:263-276`). A locked row is skipped and its files kept. Released rows drop out of later passes.
- **Deleting files on a partial inventory is safe.** When the deadline hits, the pass stops before deleting anything. Released references stay saved, so the next pass has less to scan.
  - Files added after a page is read are new, so they're protected by the 7-day age check (`SubmissionFiles.java:185,189`).
  - Files for active drafts are always under 7 days old, because a draft expires 7 days after creation.
  - Queued, importing and needs-reconciliation rows keep their files through the retained set.
- **The inventory can't grow without limit.** Rows that can't be released are capped per owner: up to 20 active drafts, about 140 imported or failed in the last 7 days, and 1 in needs-reconciliation (the one-active-job rule covers that state). With a few partners, the pass fits well within 10s.
- **Releasing files after import is safe.** `UploadedFiles.makeMediaAsset` copies each file into the asset store (`copyIn`). Media assets and later derivative generation never read from staging.
- **Release rule is correct.** Certain failures and imports set `completedAt` (`Submission.java:66,69`). Uncertain failures and stale-claim holds don't set it, and the `completed > 0` check keeps their files.
- **Daily quota is correct.** It runs under the per-owner lock, after the idempotent-replay check, and counts cancelled drafts (`SubmissionStore.java:43-48`). The test covers both churn rejection and replay after the quota is reached.
- **Replay batching** filters on `id > :after` in `id` order. Removing a failed row no longer causes other rows to be skipped.
- **Sweeper budget:** 5000 directories an hour is far more than admission allows (20 drafts × 200 files a day per owner). Failures are isolated per directory.

## Minor issues (none block release)

1. **Operator reconciliation and `completedAt`:** If an operator moves a `needs_reconciliation` row to `imported` or `failed` directly, without setting `completedAt`, its files are kept forever. I couldn't find this covered in the design or plan docs. If the runbook doesn't already say so, add "set `completed_at`" to the manual reconciliation steps.
2. **Manifest after release:** For an imported or failed draft older than 7 days, `manifest()` now returns an empty file list with no error. It returns 410 for expired or cancelled drafts, so consider doing the same here, or marking the manifest as released.
3. **One bad row stops the whole pass:** An exception inside `cleanupReference` (for example a commit returning 503) ends that hour's inventory, while the directory sweeper isolates failures per directory. This fails safe (nothing is deleted) and a persistently failing single row is unlikely, but a per-row try/catch would match the sweeper.
4. **Test gaps:** There's no DB test for the certain-`failed` release path, for more than 100 rows across pages or the deadline pause, or for replay advancing past its first batch. `replayIsBounded` only checks the first batch of 5.
5. **Shared staging root:** Cleanup builds its retained set from one context only. That's fine while only `context0` exists, but the runbook should say the staging directory must not be shared across contexts.
6. **Schema:** `completedAt` is a non-null primitive column. That's safe only because the table isn't deployed yet; any pre-existing pilot `SUBMISSION` table should be dropped rather than upgraded in place.

These findings rest on reading the code. They still need your clean build and Java test results to confirm them.
