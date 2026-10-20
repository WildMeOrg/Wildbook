**Stage 2 has converged: I found no Critical or Major issues.** This was a read-only review. I didn't run anything, so the expanded PostgreSQL test run you have going is still the evidence the tests actually pass.

## The round 1 Major is fixed
- **Tokens are now separated by audience.** Submission tokens are minted with the configured audience plus `/submissions` (`api/auth/JwtService.java:117`). Identity checks require the original audience (`JwtService.java:128-130`) and submission checks require the suffixed one (`:132-134`). The token library matches the audience exactly, so each kind of token fails the other's check. The same applies to the external service, provided it also checks the audience.
- **Both filters use the right check.** `security/SubmissionAuthenticationFilter.java:50` uses `verifySubmission`. `security/WildbookTokenAuthenticationFilter.java:75-78` also rejects any token carrying `submissionScope`, as a backstop.
- **Only explicit scoped requests get a submission token.** `api/AuthToken.java:88-89` mints one only when a valid scope is requested. A write token also requires the caller to be enrolled while admission is enabled.
- **Tests cover both directions.** `SubmissionAuthenticationFilterTest.java:81-97` uses a real RSA key pair. It shows that `verify()` throws on a submission token and that the legacy search filter returns 401 without continuing the chain. `:50-54` shows an identity-only token gets 401 from the submissions filter.

## The other round 1 items are resolved
- **Limits:** the capabilities response and the schema now both include `maxDraftsPerUser` and `maxFieldsPerRow` (`Submissions.java:75-77`, `openapi.yaml:407-412`).
- **Status codes:** PUT rows no longer lists 410, so non-editable drafts return 409 as documented. The If-Match pattern is now `{1,18}` in both the code and the spec.
- **Retry-After:** `error()` no longer sends it, so the 20-draft quota 429 has none.
- **Filter structure:** `chain.doFilter` now runs outside the authentication try/catch (`SubmissionAuthenticationFilter.java:79`).
- **JSON parsing:** bodies are decoded as strict UTF-8, and Jackson is limited to 32 nesting levels. Duplicate keys, non-object roots and trailing content are rejected before org.json sees the text. The parser tests cover unquoted keys, single quotes, trailing values, duplicates, depth and invalid UTF-8.
- **Entity:** the fields are private and the changes happen inside the entity's own methods (`submission/Submission.java:35-36`).
- **Database tests** (`SubmissionStoreDbTest`):
  - Rows survive a persistence restart.
  - Competing edits produce exactly one 200 and one 412.
  - An unconfirmed commit is reported as 503 and the rows are rolled back.
  - An expired draft stays readable, rejects edits with 409, and an admin can read it.
  - The quota race gives exactly one 201 and one 429. Because an expired draft for the same owner is already present, this also proves expired drafts don't count toward the quota.
- **Error order:** admission is still checked before routing. That was a cosmetic item and you kept it deliberately, which is reasonable.

## Remaining Minor issues
1. **Strings containing U+0000 or unpaired surrogates pass validation but can't be stored cleanly.** `SubmissionJson` accepts escapes like `"\u0000"` and `"\ud800"` anywhere: source name, batchId, clientRowId, field keys and field values. PostgreSQL rejects NUL in text columns. That surfaces as a 500 from `makePersistent`, or as the 503 "Commit outcome unavailable; retry using the original operation key". A client that follows that advice retries the same bad input forever. An unpaired surrogate is instead silently replaced with `?` when written to the database. **Fix:** reject U+0000 and unpaired surrogates with 400 during parsing or in `requiredString` and the field checks, and add a test.
2. **The published rows schema is looser than the code.** `SubmissionApiRows.rows` has no `maxItems: 200`, and `fields` has no `maxProperties: 256`. The code enforces both, returning 413 for too many rows and 400 for too many fields. Field-name length also has no limit beyond the 2 MB body cap. **Fix:** add the two bounds to the spec, and optionally a `propertyNames` max length.
3. **The filter's 405 isn't documented.** `SubmissionAuthenticationFilter` returns 405 with `Allow` for methods like HEAD, OPTIONS and PATCH (using code `BAD_REQUEST`), but no operation in the spec lists 405. This is spec completeness only.

None of these affect the stage 2 security boundary (ownership, scope, enrollment, keeping scoped tokens out of other endpoints) or the draft state rules. They can go into this change or be tracked separately. Uploads, validation, commit and workers remain gated and weren't reviewed.
