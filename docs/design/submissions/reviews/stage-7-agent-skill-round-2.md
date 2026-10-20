I found no Critical or Major issues in this re-review. Both Majors from round 1 are fixed, and none of the new or changed instructions contradict the runtime code. I think the skill is ready to publish. This was a source-only review with Read/Grep/Glob. I didn't run any tests or builds, so the test and build results you gave are yours, not mine.

## Both Majors are fixed

- **M1 (401 vs 403):** The 401 row (`submit-sightings.md:372`) now says an ordinary token gets 401, even on the first request. That matches the code: `verifySubmission` fails and returns 401 "Invalid token" (`SubmissionAuthenticationFilter.java:50-54`). The 403 row (`:373`) now covers only a read token used for a write (`:63`), an account that isn't enrolled (`SubmissionPolicy.java:29`), or an owner who is no longer eligible at commit (`SubmissionJobs.java:54`). Those are the only 403s an agent can actually get.
- **M2 (whose account mints the token):** `:29-35` now says to mint with the intended owner's account, recommends a non-admin account, and says to compare `effectiveOwnerId` with the expected user ID. The ID being compared is the right one: it is `user.getId()` throughout, from issuance (`AuthToken.java:81,88-89`) through the allowlist (`SubmissionPolicy.java:23`) and the actor (`SubmissionAuthenticationFilter.java:89`) to the report (`SubmissionValidator.java:79`). The 404 row's "for a non-admin account" qualifier matches `SubmissionStore.java:176`.

## The new instructions match the runtime

- **Returned URLs:** `statusUrl` and `links.importTask` get the application prefix added (`Submissions.java:62-66`), so resolving them against the origin is correct.
- **Concurrency (`:189-191`):** one expensive operation per owner, two slots per server process, and the worker takes a slot (`SubmissionResources.java:5-11`, `SubmissionWorker.java:25`).
- **429 "busy" retries:** Both busy responses happen before anything is written and both send `Retry-After: 5` (`SubmissionResources.java:14`, `SubmissionStore.java:208`, `SubmissionAuthenticationFilter.java:94`).
- **New error rows:**
  - 400 for a badly formatted `If-Match` matches the pattern check at `Submissions.java:93`.
  - 409 `INVALID_STATE` (`SubmissionStore.java:193`) and 410 `GONE` (`:107-108`) are accurate.
  - The 503 codes match: `ADMISSION_DISABLED` (`SubmissionPolicy.java:28`), and "Commit is disabled", which is checked after the idempotent replay (`SubmissionJobs.java:27-32`). So calling these definite rejections is correct.
- **Enrollment:** `admissionEnabled` is the global flag. Enrollment is checked when a write token is minted and on every write (`AuthToken.java:79-81`, `SubmissionAuthenticationFilter.java:62-65`).
- **Lost validation response:** Validation doesn't change the revision (`Submission.java:49`), each run creates a new report `id` (`SubmissionValidator.java:74`), and commit accepts only the stored latest report (`SubmissionJobs.java:39-41`).
- **Indexing and derivative states:** the "unknown" wording matches `SubmissionJobs.java:126`. `failed` is set at `:233`, and `running`/`unknown` at `:162,222`.
- **Config digest:** It covers only locations, the media-per-encounter limit, the file-size limit and the pixel limit (`SubmissionValidator.java:20-24`). So "execution failure rather than commit-time conflict" is now stated correctly.
- **Integers and taxonomy:** The integer-format advice matches `BulkValidator.java:494-503`. Putting everything after the genus in `specificEpithet` matches how the server joins the name.
- **Response shapes:** The rows and file-manifest shapes match `SubmissionStore.java:63,103-104`. An identical same-name upload returns the manifest unchanged (`:131-132`).
- **OpenAPI:** `/api/v3/docs/openapi.yaml` is mapped (`web.xml:628`), and the spec includes all the submissions paths (`openapi.yaml:2004-2927`).
- **Curl example:** `-H @-` reads the headers from stdin, and `printf` is a Bash built-in, so the token never appears in the command's arguments. `--fail-with-body` needs curl 7.76 or later, which is newer than when curl added `@file`/`@-` headers, so the stated requirement is enough.

## Optional nits (none block publishing)

1. `indexing.state` can also be `pending` after import (`Submission.java:66`). Before import the `indexing` object is empty, because a null value is dropped. The skill doesn't list `pending`. It's harmless, since agents are already told not to treat anything but a confirmed state as proof.
2. A lost-upload retry that sends the old ETag gets 412 before the server checks for an identical upload (`SubmissionStore.java:122` runs before `:131`). The skill already says to check `/files` first and then use the current ETag, so the instructions are fine. The sentence at `:234-235` could just say the retry needs the current ETag.

The review has converged. Nothing Critical or Major remains, and none of the amended instructions is inaccurate.
