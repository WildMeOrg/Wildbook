**Stage 6, round 3:** I found no Critical or Major issues, so stage 6 has converged. What remains are small gaps in tests and records, plus build evidence you've already said is pending. I didn't count that pending evidence as a false claim.

I only read files. I ran nothing and edited nothing, so I haven't reproduced the seven passing tests or the frontend counts myself; those numbers are as you reported them.

## N1 (rows falsely reported as changed) is fixed
- **Client side:** `normalize_numbers` (`scripts/submissions/client.py:124-133`) turns whole-number floats into ints, rejects non-finite numbers and keeps booleans separate from ints. It runs before hashing, so the local rows, the saved hash and the server's rows are all hashed the same way.
- **After an edit:** the client re-reads the rows from the server, checks them against the local rows, and saves the server's hash (`:215-220`). A lost PUT response is also handled correctly (`:208-214`).
- **Checked against the server's JSON library (org.json 20240303, from `pom.xml`):**
  - Decimals are stored as `BigDecimal`, and `valueToString` drops trailing zeros. `2026.0` comes back as `2026`, which matches the client.
  - Exponent forms like `1E+20` come back as a float, which the client then turns into the same int.
  - `-0.0` comes back as `-0`, which Python reads as `0`, the same as the client's value.
  - Booleans are stored as `true`/`false` and never turn into `1`/`0`.
  - The server only prevalidates text and doesn't otherwise change rows (`SubmissionJson.java:16-40, 99-119`).

  I found no remaining number format that would cause a false conflict.
- **Test:** `test_client.py:104-118` covers the runbook flow end to end:
  - validate `2026.0` while the fake server stores `2026`;
  - rerun with `--commit`, without a false conflict and without a second PUT;
  - recover a lost commit response;
  - resume without a second commit.

  All seven tests run as a script now that `unittest.main` is at the end of the file (`:177-178`). The old circular test has been replaced by the number/boolean test (`:33-36`).

## Other round-2 items that are now resolved
- **Commit after an invalid validation:** it returns 422 `VALIDATION_INVALID` when the `validationId` matches the saved `valid=false` report (`SubmissionJobs.java:34-38`). This happens after the revision check and before the stale-validation check, which matches `openapi.yaml:2816-2817`.
- **Replay skipping items:** `replayBatch` now pages by ID instead of by offset (`SubmissionJobs.java:145-155`, `SubmissionWorker.java:44-53`). Items that switch to `failed` can no longer shift the next page. An interrupted batch is simply repeated, which does no harm.
- **408:** it is declared for uploads in both specs.
- **410:** the one remaining 410 (GET files) is correct. The runtime does return 410 `GONE` there (`SubmissionStore.java:106-108`), and `GONE` is in the error-code list.

## Remaining Minor issues (none block sign-off)
1. **No Java test for the new 422 path.** No test commits against a `valid=false` validation (a search for `VALIDATION_INVALID` in `src/test` finds only the file-upload test). I suggest adding one to `SubmissionStoreDbTest`, and one checking that a stale ID for an invalid validation still returns 409.
2. **The paged replay is only half tested.** `pendingWorkDoesNotCompeteWithCompletedHistoryAndReplayIsBounded` sets up 7 items but only checks the first page of 5 (`SubmissionStoreDbTest.java:285-286`). Checking that the second page, `replayBatch(..., lastIdOfPage1)`, returns exactly the other 2 would prove the paging works. Replay still re-scans all past submissions on every restart; that's known and harmless.
3. **The float test only checks the client against itself.** The fake server copies the Java behaviour by assumption (`test_client.py:85`). A Java test that sends `2026.0` and `-0.0` through `replaceRows` and reads the rows back would pin the server side. So would adding stored rows to the `--runtime` fixtures. `check_contract.py --runtime` still checks only capabilities and accepted (`:95`).
4. **Status and code details:**
   - 405 and 408 return code `BAD_REQUEST` (`SubmissionAuthenticationFilter.java:40`, `SubmissionFiles.java:112`).
   - 408 and 405 declare no error body in the specs (`openapi.yaml:2685-2688`), though the runtime sends one.
   - `SubmissionJson.java:95` still pairs 422 with `CAPABILITY_UNAVAILABLE`.
5. **README is behind the evidence.**
   - `README.md:85` still says the frontend regression is "underway". It should record the 21 bulk-import suites passing, and the whole-frontend result (130 passed, 16 failed) with a note that the failing suites are in sources this branch doesn't touch. Calling them "pre-existing" would ideally need a run on the base commit `24cc99aede`.
   - The combined Maven command (`:78`) still leaves out `SubmissionsTest` and `SubmissionPolicyTest`. That's fine while the full build is pending, but add them when it's recorded.
6. **Carried over from round 2, still open:**
   - The runbook doesn't say that `--cancel` after a lost create needs a normal rerun first to recover the submission ID (`pilot-runbook.md:77-78`, `client.py:146-147`).
   - Failures with a known outcome still become `needs_reconciliation`.

## Remaining gates
The full Java build that writes the runtime fixtures, the `check_contract.py --runtime` run, the runbook's QA gates (toggling the flags on an API-only instance, and the browser tests) and deployment are all still unfinished. The README correctly doesn't claim them. I'd treat stage 6 as signed off pending those gates. Items 1–2 are cheap and worth doing before the full build, so their results are included in it.

I couldn't save this review because the session is read-only. If you want it on file, it would go in `docs/design/submissions/reviews/stage-6-round-3.md`.
