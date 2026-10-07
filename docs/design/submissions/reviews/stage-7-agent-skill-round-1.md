I found no Critical issues. Two Major issues should be fixed before publishing: the 401/403 guidance for the wrong token type, and not saying whose credentials the token must be minted with. Once those are fixed, the skill is safe and useful for a coding agent to follow. I checked the other claims against the runtime code and found only Minor inaccuracies and gaps. Neither fix requires a runtime change. This was a read-only review; I didn't run any tests or builds.

## Major

**M1. A normal API Access token gets HTTP 401, but the skill says 403.**
- `submit-sightings.md:333` says a wrong scope or a normal search token gives 403, and `:332` says 401 means "obtain a renewed submissions token".
- In the code, `verifySubmission` requires the `<aud>/submissions` audience (`JwtService.java:132-133`). An ordinary token fails that check and gets **401** "Invalid token" (`SubmissionAuthenticationFilter.java:53-54`).
- 403 only happens with a valid submissions token: a read-scope token used for a write (`:63`), or an account that isn't enrolled (`SubmissionPolicy.java:29`).
- **Effect:** an agent given the wrong token will follow the 401 row and ask for a "renewed" token. The person may just create another API Access token, so the agent loops without ever being told the real problem.
- **Fix:** have the 401 row say that a 401 on the very first request usually means it isn't a `submissions:*` token, so ask for one minted with `scope=submissions:write`. Limit the 403 row to read-scope-on-write and enrollment.

**M2. The skill doesn't say whose credentials the token must be minted with (a record-ownership risk).**
- `submit-sightings.md:29-31` says "The operator obtains the scoped token … with fresh HTTP Basic credentials".
- The token's subject is whichever account's Basic credentials were used (`AuthToken.java:52-53,88-89`). Drafts and imported encounters are owned by that account (`SubmissionStore.java:50`, `SubmissionImporter.java:35`).
- An operator following the text literally could mint the token with their own (possibly admin) account. The records would then be attributed to the operator.
- An admin token also sees every draft (`SubmissionStore.java:176`), so the claim at `:334` that another owner's submission is "hidden as not found" is only true for non-admin tokens.
- **Fix:** state that the token must be minted with the credentials of the enrolled account that should own the records. Also say to check `effectiveOwnerId` against that expected account before committing, and to use a non-admin account.

## Minor inaccuracies and gaps

1. **`statusUrl` and `links.importTask` already include the application prefix.** They are site-relative paths like `/wildbook/api/v3/...` (`Submissions.java:62-66`). Adding them to `BASE`, which already ends in `/wildbook`, doubles the prefix. The warning at `:296-297` ("Do not prepend `/api/v3` twice") points at the wrong risk. It should say to resolve these URLs against the server's origin (scheme and host), not against `BASE`.
2. **Uploads and validation are limited per account, not per draft.** `SubmissionResources.java:5-14` allows one upload/validate per owner across all their drafts, and two across the whole installation. The limit is also per server process, so parallel uploads to different drafts return 429 "Intake processing busy". `:172` should say "sequentially per account". The 429 row (`:342`) should add that the "busy" responses (with `Retry-After: 5`) wrote nothing, so the same request can be retried unchanged.
3. **Error codes missing from the recovery table.**
   - 409 `INVALID_STATE`: editing, uploading or validating a frozen or expired draft (`SubmissionStore.java:193`).
   - 410 `GONE`: `GET /files` on a cancelled or expired draft (`:108`). This can happen during the lost-upload recovery at `:329`.
   - 400 for a badly formatted `If-Match`, such as unquoted `3` or `W/"3"` (`Submissions.java:93`).
   - 400 for a rejected filename (`SubmissionFiles.java:64`). The skill states the filename rules but not the error they produce.
   - 503 `ADMISSION_DISABLED` / `CAPABILITY_UNAVAILABLE` "Commit is disabled". These are definite rejections, not unknown outcomes; the table only covers them with the generic "5xx".
4. **Enrollment can't be discovered in advance.** `admissionEnabled` is a global setting (`Submissions.java:101`). Whether this particular account is enrolled only shows up as a 403 on the first write, or when the token is minted. Worth one sentence at `:51`.
5. **No row for a lost validate response.** The safe action is to POST validate again with the same ETag, since revision doesn't change. Each run creates a new report `id`, and only the newest one is accepted at commit (`SubmissionJobs.java:39-41`).
6. **Indexing-state wording at `:319` is confusing.** The runtime message is "unknown means dispatched; completion is not acknowledged" (`SubmissionJobs.java:126`). The skill says "dispatch was not acknowledged as complete". It also leaves out `indexing.state: "failed"` (`:233`) and `derivatives: "running"/"unknown"` (`:222`).
7. **Validation isn't pinned to the full configuration.** The config digest covers location IDs, the media-per-encounter limit, the file-size limit and the pixel limit. It does not cover taxonomy, lifeStage or livingStatus (`SubmissionValidator.java:20-24`). A taxonomy change after commit is caught by the re-check at execution time and ends in `failed` (`SubmissionImporter.java:24-28`), not a 409 at commit. So `:276` ("applies only to that exact revision and configuration") overstates what commit-time checking guarantees. It's harmless, but slightly off.
8. **Numbers such as `2025.0` fail for integer fields.** The integer parser rejects them (`BulkValidator.java:497-503`). `:330` presents 2025 vs 2025.0 only as a comparison quirk. It should also say to send whole integers for year, month, day, hour and minutes.
9. **Species with three-part names.** `siteTaxonomies` can list names with a subspecies. The check joins `genus + " " + epithet` (`Util.java:403`, `Shepherd.java:2197`), so `specificEpithet` must hold everything after the genus. `:61` and `:132` only describe two-part names.
10. **A wrong base URL can return an HTML page with HTTP 200, not a 404.** Unmapped paths fall through to the React app (`web.xml:74-77`). The 404 row at `:334` should tell agents to check that responses are `application/json`.
11. **Curl example.** `--fail-with-body` needs curl 7.76 or later. The token also ends up in the command's arguments. `-H @headerfile` would avoid that, which is better than only warning about it at `:187-188`.

## Confirmed accurate

I checked these claims against the code and they are correct:
- **Request bodies:** the create body, rows body, field list, envelope 400/422 rules, and strict duplicate-key handling.
- **Revisions:** the create response replaying revision 0 and `ETag: "0"`, If-Match quoting, rows and uploads incrementing revision while validate does not, and the commit replay matching on key, body and revision.
- **Field validation:** every row in the failure table (`:250-264`) matches `SubmissionValidator` + `BulkImportUtil` + `BulkValidator`. That includes the error on month when a day is given without one, the leap-year check, the missing-coordinate error landing on the absent field, `INVALID_LOCATION` plus a second `INVALID_VALUE`, and `MISSING_MEDIA` followed by `REQUIRED_VALUE`.
- **Limits:** 200 rows, 256 fields per row, 2 MiB requests, 200 files, 200 MiB per draft, 24 MP / 16,000 px, 64 KiB multipart overhead, 20 active drafts, 20 new drafts per rolling day, one active job, and 7-day expiry.
- **Installation settings:** `site-settings` keys (`locationData`, `siteTaxonomies`, `sex`, `lifeStage`, `livingStatus`) and the fact that it needs no token.
- **Results:** result row shape, cursor paging, the 1–200 limit, and filtering by current access.
- **Token lifetime:** `expiresInSeconds` and read-scope access surviving removal of write enrollment.

## Registration and tests

- The `AgentSkill.java:36` registration, the `index.md:33-41` entry and the `api-reference.md:11-13` cross-link are correct and consistent. Skills are served without authentication, which is appropriate since they contain no secrets.
- The new test (`AgentSkillContentTest.java:179-195`) usefully parses both example request bodies through the runtime envelope parsers and checks the field list in both directions.
- The test doesn't cover the claims most likely to drift: error codes, numeric limits, and the illustrative commit and report JSON. A cheap improvement would assert that the documented codes appear in the runtime sources, and that the documented limits match `SubmissionPolicy` / `SubmissionFiles`.
