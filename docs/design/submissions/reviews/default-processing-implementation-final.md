**Approved. I found no concrete blockers.**

I only reviewed the code you pasted here. I haven't read the OpenAPI specs, the agent skill or `SubmissionJobs` myself, and I didn't run anything. Where I rely on your word that `fail` only runs while the stored state is `importing`, I say so.

**What looks right:**
- **The check order in `aiPhase()` is correct.** A failed or uncertain record import now reports `not_started` with `IMPORT_FAILED` or `IMPORT_OUTCOME_UNCERTAIN`. It no longer uses the `AI_HANDOFF_*` codes, and its message no longer says records "remain available". This check comes before the `aiState` checks. Since you confirmed `fail` never runs after `imported`, a real AI handoff failure can't be hidden by it.
- **Import-only mode is unaffected.** `requestsIdentification()` limits the new branch to detect-and-identify, so import-only submissions still report `skipped` even when the import failed.
- **The test setup now matches how things actually happen.** It fails a submission that was queued but never imported.
- **The tests for building the processing message are good.** One compares the message field-for-field with the old bulk-import path (`IAGateway.handleBulkImport`). Another proves nothing gets committed. A third proves no work is started when the callback config is missing or a task already exists.

**Worth doing later (not blockers):**
1. **`getAiState()` can disagree with `aiPhase()`.** For a failed import in detect-and-identify mode, `getAiState()` still returns `"failed"` or `"unknown"`, while `aiPhase()` says `not_started`. The stored `aiState` column is still null, so database queries aren't affected. But any Java code that calls `getAiState()` directly (sweepers, metrics, the worker) would see a false AI failure. Either make it return `"not_started"` for those states, or check that nothing outside `aiPhase()` reads it for failed or uncertain submissions.
2. **The tests only check `aiPhase()` directly.** One extra check through `json(false)` that both `detection` and `identification` show `not_started` would lock in what callers actually see. That output only appears when `jobId != null`, which your test setup already ensures.
3. **The spec change reaches further than the AI fields.** If `Phase` is one enum shared with `indexing` and `derivatives`, adding `not_started` also allows it there. That's harmless, but you could note it in the spec. And if the error `code` is an enum anywhere in the specs, make sure `IMPORT_FAILED` and `IMPORT_OUTCOME_UNCERTAIN` are listed.
4. **A minor wording issue:** old submissions with no mode set still show the message "Explicit import-only mode", even though nobody chose that mode explicitly.

Once the final full test run passes, this is good to go from my side.
