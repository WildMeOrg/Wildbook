# Review: submissions API default detect-and-identify mode

**I couldn't check any of this against the code.** This session has no file or shell tools, only Docs, Drive and Runpod. Everything below is based on your description. The items marked "verify" are claims about `IAGateway`, `Task` and `ImportTask` that someone needs to check in the code before implementation starts.

## Verdict: approve the architecture once the blockers below are fixed

The overall shape is right: a sibling adapter, the task and message saved before publishing, publish only after commit, a claim guarded by a state transition, and an unclear publish outcome treated as final. The blockers are mostly places where existing Wildbook behaviour could quietly break the "publish once, never retry automatically" promise.

## Blockers

1. **Something may already re-send `queueResumeMessage` (verify).** Saving `queueResumeMessage` before publishing is only safe if nothing re-sends it automatically. If a startup or sweep routine republishes tasks that still have one:
   - a restart after an unclear publish sends the message again, which breaks "never automatic retry";
   - "restart no duplicate enqueue" fails.

   First find every place that reads `queueResumeMessage`. If a re-sender exists, either the `unknown` guarantee has to be dropped, or the adapter needs a separate field for the message.

2. **Records with no saved mode must be read as import-only.** The new default (detect-and-identify) should only apply when a *new* create request is normalized. Anywhere a saved record is read (idempotent replay, validation, task flags, results, capabilities), a missing or null mode must mean import-only. Otherwise one fallback default in a helper would start AI processing on old drafts and imports. Add a test using a saved `createJson` that has no mode key at all.

3. **Two routes can start detection for the same import.**
   - The legacy import-task UI (or the BulkImport servlet) can start detection on the same `ImportTask` while the submission is `pending`. That route doesn't take your advisory lock.
   - Minimum fix: inside the lock, the worker checks whether the `ImportTask` already has a root IA task. If it does, it doesn't create tasks and records a distinct outcome, such as `dispatched` with the note "started outside the submission worker", or `unknown`.
   - Also hide or disable the manual start action for submission-owned imports while their AI state is `pending`, `dispatching` or `unknown`. That's a UI change only; BulkImport and IAGateway stay unchanged.
   - A small race remains if nothing else changes; write that down as an accepted risk.

4. **`pending` can get stuck.** The worker only picks up submissions once derivatives are complete. If derivative generation fails for good, or an import partly fails, the submission stays `pending` forever. Define when that becomes `failed`, with a reason, so it never silently hangs.

5. **The background worker has no HTTP request.** `handleBulkImport` probably takes `__baseUrl` and `__context` from the servlet request (verify). The worker must get them from configuration instead. If they can't be resolved, that counts as a failure before dispatch (`failed`). Test this specifically: a missing base URL means the message goes out but fails later, downstream, where you can't see it.

## Must-verify details (not architecture changes)

- **Advisory lock on the right connection.** Use `pg_try_advisory_xact_lock` run through the same persistence manager's native SQL inside the claim transaction, and confirm DataNucleus keeps one connection for the whole transaction. If transactions are optimistic, the lock could end up on a different connection, or be released before commit.
  - Use the two-key form with a fixed namespace constant, so it can't collide with other advisory-lock users.
  - The lock only orders the claim. The real guard is the conditional `pending → dispatching` update. Keep both.
- **Conditional updates after publishing.** Moving to `dispatched`, and the one-hour sweep that moves stale records to `unknown`, should both only apply if the state is still `dispatching`. Timestamp the claim with `aiStartedAt`.
- **What counts as "acknowledged".** Check what `addToDetectionQueue` actually returns or throws for each queue backend (RabbitMQ or file queue). Write down exactly which outcomes mean `dispatched` and which mean `unknown`.
- **Message matches the legacy one.** You're copying `handleBulkImport`'s logic without changing it, so the two can drift apart, especially in how media assets are chosen (acmId, taxonomy or other filters). Add a golden test: for the same fixture, compare the adapter's tasks and message with `handleBulkImport`'s, ignoring IDs and timestamps.

## Gaps to settle before coding

- **Who resolves `unknown` and `failed`?** Automatic retry is off, so decide whether there's an admin re-dispatch path (behind the lock and an existing-root-task check), or whether these are final for good. Either way, write it down in the status explanation.
- **Can the mode change after create?** Decide whether a draft can switch between import-only and detect-and-identify before it is committed, or whether the mode is fixed at create. Fixed is simpler and fits "persisted mode drives everything".
- **The identification status copies the detection handoff.** Say plainly that `skipIdent=false` means identification runs downstream in the IA pipeline, and this API never reports whether it finished.
- **Existing clients.** Clients that currently leave the mode out will start getting detection and identification on new creates. That's the requirement, but it needs a changelog or release note, and the public agent skill update should include it.

## Tests to add to your list

- A saved `createJson` with no mode key replays and processes as import-only.
- Derivatives fail → `failed`, not stuck in `pending`.
- An `ImportTask` that already has a root IA task → the worker creates no tasks.
- The background worker's message has the configured base URL and context.
- The golden test matching the legacy message.
- After a restart, nothing re-sends `queueResumeMessage` for a submission in `unknown`.

Once blockers 1 and 3 are checked in the code and 2, 4 and 5 are added to the design, the architecture is approved and implementation can start.
