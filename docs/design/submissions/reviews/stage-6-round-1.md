I found no Critical issues: nothing lets an import run twice, loses a commit, or leaks the token. There are 7 Major and about 12 Minor issues. The most important is that the on/off flags the runbook relies on for rollback may not take effect at runtime.

One caution: `SubmissionJobs.java` changed while I was reviewing (`postprocess` went from `void` to `boolean`, which fixed a compile error in `SubmissionWorker.java:37`). Findings are against the version I read last. I ran nothing and edited nothing.

## Critical
None.

## Major

**M1. The on/off flags are cached, so runtime toggling is unreliable.**
- `submissions.enabled`, `commitEnabled` and `workerEnabled` are read through `CommonConfiguration.getProperty(name, context)`. That keeps a per-JVM cache (`CommonConfiguration.java:46-52`).
- The cache is only refreshed when some request-scoped config call happens (`:57-62`). Those calls also load per-user/org override files into the same shared entry.
- So an API-only or worker-only instance may keep old flag values until restart. The runbook says `workerEnabled` "controls processing at runtime", "Admission can be disabled while accepted jobs drain", and its rollback steps depend on these flags.
- Enrollment is not affected: `getApiAccessProperty` goes through `ShepherdProperties`, which has no cache, so removing a partner takes effect immediately.
- Fix: read the three flags without the cache, or document that a restart is required. Add a QA gate that toggles each flag on a non-browser instance.

**M2. Contract and runtime disagree, and the contract check can't see it.**
- The commit response: both specs require `acceptedRevision`, but `SubmissionJobs.java:48` sends `revision`. `examples.json:63` uses `acceptedRevision`, so the check passes anyway.
- Capabilities `limits`: the design `openapi.yaml` sets `additionalProperties: false` and omits `maxFieldsPerRow` and `maxDraftsPerUser`, which the runtime always sends (`Submissions.java:102-103`). So every real capabilities response fails the design schema. The main `src/main/resources/openapi.yaml:257-264` does include them, so the two specs have diverged.
- `check_contract.py` only checks the design spec against hand-written examples. It never looks at runtime output or the main spec, so "11 operations/18 examples passed" says nothing about runtime conformance.

**M3. Imports block all uploads and validation on that JVM, and the client gives up after about 7 seconds.**
- `SubmissionWorker.tick` holds the single intake slot for the whole tick, including the import and generating image derivatives (`SubmissionWorker.java:22`, `SubmissionResources.java:5`).
- Every upload and validate on that JVM gets 429 meanwhile. The client retries 4 times over about 7 seconds and ignores `Retry-After`.
- The runbook mentions "one expensive intake operation per JVM" but not that worker imports use that slot. Rerunning does converge, but pilot users will hit this.

**M4. Post-import processing picks the same submissions forever.**
- `pendingPostprocessing` selects `derivatives == 'complete'` with no check on the indexing phase (`SubmissionJobs.java:129`).
- So every imported submission is re-sent for indexing on every restart, by every worker JVM.
- The list is capped at 10,000 sorted oldest first, and nothing is ever deleted. After 10,000 imports, newer submissions never get derivatives or indexing.
- If indexing is unavailable, the exception ends the loop every tick and blocks everything behind it. The runbook's "replayed idempotently" is true but leaves all this out.

**M5. Fixing rows or a stuck commit leaves orphan drafts and hides the error.**
- The runbook says to fix validation errors and repeat. But any change to `rows.json` fails the state digest check, so a new state file is needed, which creates a new draft.
- Old drafts stay live for 7 days and count toward the 20-draft limit. The client has no cancel, and photos are re-uploaded each time.
- A failed PUT replaces the real 400/413/422 reason with "Rows update unresolved or conflicted" (`client.py:157-160`, `from None`).
- A saved commit intent that can never succeed (412, `VALIDATION_STALE`, expired) has no documented way out. Starting a new draft is safe while the state is still `draft`/`validated`, but the runbook should say so and cover cancelling the old one.

**M6. The client tests don't exercise recovery.**
- None of the 4 tests runs `main()`, so they cover none of: create-key persistence, commit intent saved before POST, resume after `queued`, row conflict, pagination, redirect refusal, or the lock.
- `test_safe_retries_keep_the_original_commit_intent` is circular: it retries a closure over a fixed dict, so it can't fail.
- The code for these paths looks right, but there is no test evidence for it.

**M7. The runbook overstates verification.**
- `pilot-runbook.md:4-5` calls README.md "the final verification record".
- README says "Implementation verification in progress" and that the stage 5/6 reviews are open (README:30, :68). README:47 still shows the old "10 operations / seven examples" line.

## Minor
- **Upload race:** if a timed-out upload commits between the manifest GET and the retry, the retry gets 412 and aborts instead of re-reading the manifest. A rerun fixes it.
- **Commit and config staleness:** the contract promises 409 `VALIDATION_STALE` at commit when configuration changed. The runtime only checks this during the import, so the job ends `failed`/`IMPORT_FAILED`.
- **Wrong or missing status codes:** an expired commit returns 409 `INVALID_STATE`, not 410. Results never return 410.
- **Retention and "404 after purge":** the capabilities `idempotencyRetentionSeconds` advertises 7 days, but the runtime keeps records forever, so "404 after final purge" never happens.
- **Schema field name:** the Submission schema says `error`; the runtime sends `errors[]`.
- **Missing context path:** `statusUrl` and `links.importTask` lack the context path that `Location` includes.
- **Commit replay needs admission:** the filter checks admission on every non-GET (`SubmissionAuthenticationFilter.java:62-65`). With admission off, a same-key commit replay gets 503. The reference client is fine because it polls, but the contract should say to recover by GET.
- **Too many jobs marked uncertain:**
  - Certain-outcome failures (`IllegalStateException("Required media unavailable")`, errors from `shutdownNow` interrupting an import) become `needs_reconciliation`, which blocks the owner.
  - The runbook has no "drain before restart" step, e.g. confirm no `importing` or `derivatives=running` rows. M1 undermines this anyway.
- **Indexing never sent after `derivatives=unknown`:** the indexing phase stays `pending` forever. The runbook should say this.
- **Client input errors crash:** `sorted(names)` runs before the type check, so mixed-type media values raise an uncaught `TypeError`. Malformed `rows.json` raises `KeyError` tracebacks.
- **Proxy leak on localhost:** for `http://localhost`, a set `http_proxy` with no `no_proxy` sends the bearer token to the proxy in cleartext. Consider an empty `ProxyHandler`.
- **Client ignores capabilities:** it doesn't check `admissionEnabled`/`commitEnabled`/`stagingAvailable` before uploading.
- **State durability and lock scope:**
  - `save()` fsyncs the file but not the directory after `os.replace`.
  - `flock` may not work across hosts on network filesystems or WSL `/mnt/c`. The runbook should say "local filesystem".

## Checked and correct
- **Create key:** saved before the POST, and the replay is checked before the draft limit.
- **Commit intent:** validation ID, key and revision are fsynced before the POST. A same-key replay is checked before `If-Match`. On restart, the POST is skipped once the state is past `validated`.
- **Upload reconciliation:** by name and SHA-256 from the manifest.
- **Polling and pagination:** fixed-offset pages over results that never change.
- **Transport and credentials:**
  - Redirects are refused; a 3xx surfaces as an `ApiError`.
  - HTTPS is required outside localhost.
  - The token comes only from the environment and is never written to state.
  - State and lock files are 0600, and the lock is taken before state is read.
- **Worker:**
  - Stale claims are safely held off and never rerun.
  - "Imported wins" holds, with a single writer.
  - Removing a partner fails their unstarted imports as `failed`.
  - Indexing `unknown` is described as "dispatched", matching your note.

## Convergence
The client and server converge on resume. The blockers before stage 5/6 sign-off:
- **M1:** needs either a code fix or a restart requirement in the runbook.
- **M2:** fix the runtime or both specs, and extend the checker to cover runtime output.
- **M4:** filter selection on `phase` or add a done marker.
- **M5 and M6:** the runbook workflow and error surfacing, plus tests that run `main()`.

The rest can be tracked as follow-ups.
