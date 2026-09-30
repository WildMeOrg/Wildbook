I found no Critical issues and one new Major one. The client's check that the server still holds its saved rows gives false conflicts when a numeric field is a whole-number decimal like `2026.0`, and that blocks the runbook's normal validate-then-commit flow. The seven round-1 Majors are resolved. A few smaller contract and evidence gaps remain.

I ran nothing and edited nothing. Everything below comes from reading the code.

## Critical
None.

## Major

**N1. Client and server normalize numbers differently, so the saved-rows check fails.**
- The client compares a hash of Python's `json.dumps(..., sort_keys=True)` output (`client.py:124-125`), and it hashes the server's stored rows the same way (`client.py:188-189`).
- The server stores rows through `SubmissionJson.canonical` → `JSONObject.valueToString` (`SubmissionJson.java:63`). That formatting drops trailing decimal zeros, so `2026.0` is stored and returned as `2026`, and `-8.0` as `-8`.
- Python reads the server's copy back as the integer `2026`. That hashes differently from the local `2026.0`.
- **Where it breaks:**
  - **Rerun with `--commit`:** the first run's PUT succeeds and saves the local hash (`:203`). On the rerun, the server's rows match neither the current input nor the saved hash, so `:190` stops with "Draft rows were changed by another client". This is the flow `pilot-runbook.md:67-70` tells users to follow.
  - **Row correction:** the same false conflict occurs.
  - **Lost PUT response:** reconciliation at `:197` and `:201` never matches, so it raises or reports "unresolved".
- This is likely in practice. Pandas and spreadsheet exports turn integer columns with gaps into floats (`2026.0`), and coordinates like `12.0` are common.
- Nothing is lost or imported twice, but the client can't finish, and the runbook gives no workaround.
- The design spec already says clients should reconcile "by GET rows and JSON value equality, not by reproducing this digest" (`openapi.yaml:1189-1190`).
- **Fix:**
  - After a PUT succeeds or is reconciled, save the hash of the rows the server returns, not the local hash.
  - Compare local rows to server rows after normalizing numbers: integral floats become ints, and ideally use `parse_float=Decimal`.
  - Plain Python `==` won't do, because `True == 1`.
  - Add a `main()` test with `"Encounter.year": 2026.0`. The current tests use only integers.

## Minor

**Contract and runtime mismatches:**
- **Commit after an invalid validation:** both specs say it returns 422 `VALIDATION_INVALID` (design `openapi.yaml:794`, published `openapi.yaml:2810-2811`). The runtime returns 409 `VALIDATION_STALE`, because a `valid=false` result leaves the state as `draft` (`Submission.java:48`, `SubmissionJobs.java:34-36`). The reference client never commits an invalid draft, but other clients will get a different error than documented.
- **Undocumented statuses:** the runtime returns 405 and 408 with code `BAD_REQUEST` (`SubmissionAuthenticationFilter.java:40`, `SubmissionFiles.java:112`). 408 isn't declared anywhere. `SubmissionJson.java:95` pairs 422 with `CAPABILITY_UNAVAILABLE`, while the error description implies 503.
- **Carried over from round 1, unchanged:**
  - An expired commit returns 409 `INVALID_STATE`, not 410.
  - Failures with a known outcome (for example "Required media unavailable" at `SubmissionImporter.java:48`) still become `needs_reconciliation`.
  - When derivatives end up `unknown`, indexing stays `pending`. This one is now documented (`pilot-runbook.md:121-122`).

**What `check_contract.py --runtime` does and doesn't show:**
- It checks only two responses (`:95`): capabilities and accepted. Submission, Results, Validation, Manifest, Error and StoredRows are never checked against runtime output. That's how the 422/409 mismatch got through.
- The accepted file is written from `SubmissionJobs.enqueue` in `SubmissionStoreDbTest:177`, not from the servlet, so the context-path rewrite, `Location` and `ETag` aren't covered.
- The capabilities file comes from a servlet with no `ServletContext`, so it only exercises `stagingAvailable=false` (`SubmissionsTest:50-59`).
- It doesn't check that the files in `target/` are fresh.
- **Evidence status:** the README records no `--runtime` run. Its combined Maven command (`README:78`) doesn't include `SubmissionsTest`, which writes the capabilities file, or `SubmissionPolicyTest`. So runtime conformance and the policy test are claimed but not recorded as passing. Treat them as unverified.

**Worker:**
- **Replay cost grows with history.** `replayBatch` re-sends indexing for every imported submission with `phase == 'unknown'` on each worker restart, per JVM, 5 per 10-second tick (`SubmissionJobs.java:140-150`). Because `phase` stays `unknown`, this set never shrinks. It terminates and is harmless, but it is O(history) on every restart.
- **Replay can skip items.** Offset paging skips one item whenever a replayed item switches to `failed` during the scan.
- **Slot contention.** The worker keeps its slot for the whole tick: the import plus up to 10 derivative generations (`SubmissionWorker.java:25-43`). Concurrent partners on that JVM share the one remaining slot, and a long derivative pass can outlast the client's 5-minute upload budget. A rerun converges, and the runbook (`:35-37`) warns about 429s.

**Client and tests:**
- **Only 4 tests run as a script.** `test_client.py:60-61` calls `unittest.main()` before `MainFlowTests` is defined, so `python3 test_client.py` runs just 4 tests. `unittest discover`, as in the README, runs all 7. Move the call to the end of the file.
- **Leftover circular test.** `test_safe_retries_keep_the_original_commit_intent` can't fail. The `main()` tests now cover that path, so delete it.
- **Create key covered in-process only.** The create-key test covers a retry within one process (the saved key is asserted before each POST). It doesn't cover a process exit after a lost create. The logic is the same, so this is low risk.
- **Cancel after a lost create.** If the create response is lost, `--cancel` reports "No saved submission to cancel" (`client.py:134-135`) and the server-side draft lingers for 7 days. A normal rerun replays the create key and recovers the ID, after which cancel works. The runbook should say so.
- **Policy test scope.** `SubmissionPolicyTest` mocks `getApiAccessProperty`. It proves the flags are re-read on each check, not that the file read itself is uncached. The code does confirm that `apiAccessPropsCache` is only filled by tests (`CommonConfiguration.java:39-40, 615-625`). The runbook's QA gate, toggling the flags on an API-only instance (`:172-173`), is still the real proof.

## Round-1 Majors resolved
- **M1 (flags cached):** the flags and staging path now use `getApiAccessProperty`, which reads the file fresh in production (`SubmissionPolicy.java:13-21`, `SubmissionFiles.java:30`). The runbook is updated and has a QA gate.
- **M2 (contract vs runtime):** the runtime sends `acceptedRevision` (`SubmissionJobs.java:52`). Both specs list `maxFieldsPerRow` and `maxDraftsPerUser`, and the two specs' `Capabilities` and `Accepted` schemas match. See N1 and the checker notes above for what is still uncovered.
- **M3 (imports block intake):** there are now two slots, a 429 carries `Retry-After: 5`, and the client honors it within a 5-minute budget.
- **M4 (post-processing repeats forever):** pending work is selected by `derivatives == 'pending' || (complete && phase == 'pending')`, 10 at a time. Failures are held as `failed`, and the derivative timestamp is separate.
- **M5 (orphan drafts, hidden errors):** rows can be corrected in the same draft, 4xx details are kept, and `--cancel` exists. `--reset-commit` is allowed only when the server shows `draft` or `validated` with no `operationId`, which `Submission.json` always includes once accepted. Cancel is idempotent (`SubmissionStore.java:84`), and validate doesn't bump the revision, so retrying either is safe.
- **M6 (tests skip recovery):** the `main()` tests cover a lost create, a lost commit response recovered by GET, resume without a second commit, pagination, row correction, an actionable 422 and the file lock.
- **M7 (runbook overclaims):** the "final verification record" wording is gone. The README says the full build and frontend regression are underway and that the stage 5/6 reviews are open. It claims no deployment.

## Convergence
Before stage-6 sign-off, fix N1 and add the float test. Also either align commit-after-invalid with the spec or change the spec. Record the `--runtime` run and the full test command, including `SubmissionsTest` and `SubmissionPolicyTest`, in the README when they actually pass. The full build, frontend regression and the QA gates are still unfinished, so none of them is a pass yet.
