I reviewed only the code you pasted. I didn't have access to the server-side create handler or the worker, so two points below depend on code I couldn't read.

## Verdict
**Fix 2 is approved. Fix 1 is approved with one required change. The old-state upgrade is approved, provided the idempotency check below holds.**

## Fix 1: import failure vs. AI handoff phase
The message change is correct. For an import that failed or is uncertain, the `code` is `IMPORT_FAILED` or `IMPORT_OUTCOME_UNCERTAIN`, and the message no longer claims records exist. The new test checks this.

**Remaining blocker: `detection.state` / `identification.state` still reuses the AI-handoff values.** When `state == "failed"` and `aiState == null`, `getAiState()` returns `"failed"`. That is the same `state` a real `AI_HANDOFF_FAILED` reports, and `"unknown"` likewise matches `AI_HANDOFF_UNKNOWN`. So the two cases differ only in `code`. A client that reads `detection.state` will conclude that the AI handoff failed, when detection was never started. The message itself says "were not started," so `state` contradicts it. Suggested fix: in the import-failure branch, return a distinct state such as `"not_started"` for both failed and needs_reconciliation, and add it to the enum in both `openapi.yaml` files. Then assert `state` in `recordImportFailuresAreNotMisreportedAsAiHandoffFailures`, not just `code`.

**Needs checking, not a blocker as far as I can see:** the branch decides by `state`, not by whether records were actually committed. It's only correct if `fail()` is never called after `imported()`. For example, a later indexing, derivatives or AI failure must not move `state` to `failed`. If anything does that, this branch would wrongly say records don't exist when they do. The test creates exactly that sequence (`imported()`, then `aiState(null)`, then `fail()`), which the real code shouldn't be able to reach. Please confirm that `SubmissionWorker`/`SubmissionProcessing` only ever call `aiState("failed"|"unknown")` after import, never `fail()`. If you want it to hold regardless, you could also require `resultJson == null` in that branch.

## Fix 2: `unittest.main` guard
Approved. The guard is now the last statement in the file, so all three test classes are defined before it runs.

## Old state with no id
The client logic is sound:
- On a fresh run, nothing is saved before `createRequest` is set, so `args.state.exists()` really does mean the state came from an older client. The `.lock` file is separate and doesn't affect that check.
- `createRequest` is saved before the first POST, and replays send the saved body with the saved key.
- A state with an id but no `createRequest` gets `import-only` recorded but never sent, which is harmless.

**One condition I couldn't check:** an older client may have sent `{contractVersion, source}` and lost the response. The resume now sends the same `Idempotency-Key` with an added `processing: {mode: "import-only"}`. That only replays cleanly if the server fills in the default mode *before* computing `createHash`. If it hashes the raw body, the resume gets an idempotency-mismatch error instead of the original draft. You describe it as matching the previous normalized semantics. If a test already covers "missing processing and explicit import-only give the same create hash," this is fine. If not, that's the one test worth adding.

**Minor, not blocking:** if an old state already has a `createRequest` without `processing` and the user passes `--processing-mode import-only`, the client raises "fixed", even though the mode is effectively import-only. You could treat a missing `processing` as `import-only` in that comparison.

## Summary
The one change I'd require is the separate `not_started` state (Fix 1), plus confirmation of the two things I couldn't check: nothing calls `fail()` after `imported()`, and the create hash is computed after the default mode is filled in.
