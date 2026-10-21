# Review: submissions detection/identification follow-up

**Verdict: not approved yet.** Two small fixes are left, plus the test runs you already have pending. The core handoff logic looks correct to me: the preparation/claim/publish fencing, the timestamps rechecked under the lock during reconcile, and never republishing an uncertain handoff. I had no file-read access to the worktree in this session, so this review covers only the code and excerpts you pasted.

## Blockers

**1. Detection/identification status is wrong when the import itself fails** (`Submission.getAiState` / `aiPhase`)
- When `state == "failed"` (the record import failed), the API returns `detection`/`identification` as `{state: failed, code: AI_HANDOFF_FAILED, message: "…Imported records remain available."}`. No records were imported, and no handoff was ever attempted.
- When `state == "needs_reconciliation"` (the import outcome is uncertain), it returns `AI_HANDOFF_UNKNOWN`. That sends operators to inspect the IA queue and tasks instead of the import.
- Removing "pending" on these paths was right. But the code and message need to say "not started because the import failed or is uncertain", not "handoff failed". One fix: apply the `AI_HANDOFF_*` code and the "records remain" text only when `state == "imported"`, and give the import-failure paths their own message (with or without a code).

**2. The new Python tests are never run when the file is executed as a script** (`scripts/submissions/test_client.py`)
- `ProcessingModeTests` is defined after `if __name__ == "__main__": unittest.main()`.
- `unittest.main()` exits before the class is defined, so `python3 scripts/submissions/test_client.py` silently skips the client-mode tests. They only run under `python -m unittest` or pytest discovery.
- Fix: move the class above the main guard.

**3. Test runs still pending**
- The golden legacy-contract test hasn't run yet, and the full suite is still running.
- Approval depends on both passing.

## Worth doing, not blocking

- **Old client state without an `id`** (`client.py`):
  - If the original create request never reached the server, resuming now sends a create with no `processing`, and the server now defaults that to detect-and-identify. The old client could only mean import-only.
  - For the same state, passing `--processing-mode import-only` always raises the "fixed" error.
  - Saving an explicit `{"mode":"import-only"}` for pre-mode state fixes both. It still replays correctly, because the old server saved an omitted mode as explicit import-only, so the stored request hash matches.
- **Capabilities always advertise `detect-and-identify`.** `dispatch` fails whenever the detection queue isn't a `FileQueue` or `IA.getBaseURL` is blank. On such a deployment, every submission that uses the new default imports fine and then gets marked AI failed, with its ImportTask marked `failed`. A bulk-UI user may read that as a failed import and re-upload, creating duplicates. Either check those prerequisites before the import runs (and in `capabilities`), or at minimum confirm the pilot host's queue type and base URL before rollout.
- **Every preparation exception becomes a permanent `failed`,** including transient ones such as `getDetectionQueue` I/O errors or DB errors inside `prepare`. That errs on the safe side and matches the "operator repairs" runbook, so it's fine for the pilot.
- **The golden test proves handler equivalence only for the input it builds itself** (`{taskParameters:{importTaskId, skipIdent:false}, bulkImport:{}}`). If the real bulk-UI caller sends extra `taskParameters` (for example matching-scope filters), submissions will use different matching defaults. It's worth a one-line check of the real caller, or a sentence in the runbook.
- **`publishChecked` files are owner-only.** `Files.createTempFile` creates them `rw-------`, and they keep those permissions after the rename, whereas legacy queue files follow the umask. That's fine while the detection consumer runs in the same JVM or as the same user; check it if anything else reads the queue directory.
- **Cosmetic:**
  - `prepare` doesn't write the `handleBulkImport() initiated IA Task …` log line that the legacy path writes. Adding it would help operators reconcile.
  - The message in the `aiDispatched` example in `examples.json` doesn't match the message the server actually returns.

## Things to check, not findings

- **Derivative states:** `reconcile` only rescues `pending` submissions whose derivatives are `unknown`. If derivatives can end in any other state that isn't `complete` (such as `failed`, or `running` left behind by a crash), detection would show `pending` forever. Confirm `unknown` is the only such terminal state.
- **`tryLock` inside reconcile's shared transaction:** if a busy lock surfaces as an SQL error rather than a false/exception with no DB side effect, Postgres aborts the transaction and the whole batch's updates are lost. That's presumably the same helper `reconcileStaleClaims` uses, and the worker isolates the failure either way.
- **`Phase` schema and the new `code` field:** confirm the schema permits it (not `additionalProperties: false` without `code`), and that its `state` enum includes `unknown` in both specs.

Everything else in your list matches the code as pasted.
