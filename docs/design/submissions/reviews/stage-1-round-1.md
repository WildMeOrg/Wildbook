I found no Critical findings and 6 Major ones. The biggest gaps are in the retry and revision rules, and in the error shape a client needs to decide what to do after a failure. I didn't run anything, including `check_contract.py` and the new test, as you asked.

## Major

**M1. Whether validate changes the revision is undefined.** (`openapi.yaml:604-618`, `1097-1103`; `examples.json:62,71`)
- The example validation is at revision 2, but the accepted commit shows `acceptedRevision: 3`. That suggests the draft → validated change bumps the revision.
- The validate 200 response returns no `ETag`, though, so the client doesn't know which `If-Match` value to send on commit.
- It's also unclear what happens if the `If-Match` header and the body `revision` disagree.
- There's no way to fetch a validation by ID, so a client that loses the validate response can't recover it except by validating again.
- **Fix:** Pick one of two rules.
  - Validate never bumps the revision, and "validated" is derived from `validationId` matching the current revision. Then re-validating the same revision is safe.
  - Or validate returns an `ETag` plus a `resultingRevision`, and `GET /{id}/validations/{validationId}` is added.

  Either way, drop the body `revision` or state that it must equal `If-Match` (otherwise 400).

**M2. Cancellation, 410 and the terminal states contradict each other.** (`openapi.yaml:231-236`, `239-241`, `289-294`, `1025-1034`)
- DELETE promises 204 on repeated cancellation but also lists 410 for cancelled drafts.
- GET returns 410 for cancelled/expired drafts, so the `cancelled` and `expired` states in `Submission` can never be returned.
- "Replay of the cancellation revision" doesn't say which `If-Match` value a retry must carry: the revision before cancellation, or the one after.
- **Fix:** For the owner, a repeated DELETE returns 204 whatever `If-Match` says (skip the precondition check once the draft is already cancelled). Remove 410 from DELETE. Then either remove `cancelled`/`expired` from the `Submission` enum, or have GET return 200 with that state and keep 410 only for results/files. Also say whether a failed or `needs_reconciliation` submission can be cancelled.

**M3. The error body can't be acted on by a program.** (`openapi.yaml:916-926`)
- The plan (line 75) requires optional row/field/limit details, but `Error` has only `code`, `message` and `requestId`, and it forbids extra properties.
- One 409 covers "State, key or content conflict", and no codes are listed. A client can't tell a reused idempotency key from "already committed" or "commit blocked because validation is invalid".
- 413 and 429 can't report which limit was hit.
- **Fix:** Add `issues: Issue[]` (optional) to `Error` and a published enum or table of stable `code` values. At minimum: `PRECONDITION_REQUIRED`, `REVISION_STALE`, `IDEMPOTENCY_KEY_REUSED`, `ALREADY_COMMITTED`, `VALIDATION_STALE`, `VALIDATION_INVALID`, `INVALID_STATE`, `LIMIT_EXCEEDED`, `ADMISSION_DISABLED`, `DUPLICATE_CLIENT_ROW_ID`. Also say which status commit returns when `valid=false`.

**M4. A lost response to a rows PUT can't be recovered.** (`openapi.yaml:325-329`, `1002-1048`)
- After a lost response, the retry gets 412 because the revision has moved on.
- `Submission` exposes neither the rows nor a digest of them, so the client can't tell whether its write was applied. Uploads have a reconcile path; rows don't.
- **Fix:** Add `rowsDigest` (SHA-256 of the canonical rows) and `rowCount` to `Submission`, or add `GET /{id}/rows`. Then document the recovery rule: if the digest matches, treat the write as successful.

**M5. `occurrenceIds` is declared as UUIDs, but real occurrence IDs aren't always UUIDs.** (`openapi.yaml:1194-1196`)
- `BulkImporter.getOrCreateOccurrence` (`BulkImporter.java:1036-1042`) uses whatever string the user put in `Sighting.sightingID`/`Encounter.sightingID`.
- Existing occurrences can have non-UUID IDs, so a valid response would fail the contract. `individualIds` carries a similar risk for legacy data.
- **Fix:** Make `occurrenceIds` (and probably `individualIds`) plain `type: string, minLength: 1`.

**M6. The characterization tests don't pin the behaviour the new API depends on.** (`BulkSubmissionCompatibilityTest.java`)
- **Unknown-field default (`:52-59`):** the test only exercises `BulkValidatorException.treatAsWarning(true/false)`. It doesn't pin the actual legacy default, `badFieldnamesAreWarnings=true` (`BulkImport.java:183-184`), which is the behaviour the new API deliberately differs from. Either extract that default into a constant or helper and assert it, or cover it in a `BulkApiPostTest`-style servlet test.
- **Year precision (`:29-30`):** the checks that `Encounter.month` and `Encounter.day` are absent pass trivially because neither was in the input. That proves nothing about precision. Assert at importer level that a year-only row produces an encounter with no month/day, or rename the test.
- **Feb 30 (`:64`):** `anyMatch` would pass on any unrelated error. Assert `result.get("Encounter.day") instanceof BulkValidatorException`, plus the message or code.
- **Object vs array rows (`:35-50`):** this skips the one real difference, which is that a short array pads with nulls (`BulkImportUtil.java:38-42`). Add a short-array case.
- **Coverage vs the exit gate:** the plan (lines 87-90) lists synonyms, row grouping and media count, missing/corrupt images, and status response shapes. None are covered here or cited from the existing suites. Grouping matters most, because `maxMediaPerEncounter` depends on it (plan line 174). Add them, or state which existing test covers each one.

## Minor

1. **Default processing mode (`openapi.yaml:944-953` vs `74-75`):** `Processing.required: [mode]` means `default: import-only` never applies. The "omitted means import-only" rule exists only in prose, and `check_contract.py:57` asserts a default that can't take effect. Either make `mode` optional, or document that `processing` is omitted as a whole.
2. **Irrelevant error statuses (`openapi.yaml:141-164`, and 404 on create/capabilities):** create has no `If-Match`, so 412/428 can't happen, and 404 doesn't apply there. Listing them misleads generated clients. Only list 412/428 where `If-Match` is required.
3. **Upload name mapping (`openapi.yaml:493-498`, `1059`):** it isn't stated that `File.name` is exactly the multipart filename that rows reference in `Encounter.mediaAssetN`. Nor is it stated that names are rejected rather than silently cleaned (plan lines 164-165). Also say whether a same-content retry bumps the revision.
4. **Create replay (`openapi.yaml:74`):** a replay returns the original 201 body and ETag, which may be out of date. Tell clients to GET before mutating.
5. **Session auth (`openapi.yaml:882-888` vs `1262-1268`):** capabilities can advertise `session`, but no cookie security scheme or CSRF header is declared.
6. **Strict responses:** `additionalProperties: false` on response schemas blocks adding fields later. Keep it on request schemas and loosen it on responses.
7. **YAML anchors:** schemas are shared via anchors (`&id001` is defined in a query parameter at `:817` and reused throughout components). This is fragile to edit. Replace with named `$ref` schemas (`NonEmptyString`, `Uuid`, `Revision`).
8. **`check_contract.py` checks less than it claims:**
   - It validates without a format checker (`:48,53`), so `uuid` and `date-time` are never enforced. Use `format_checker=Draft4Validator.FORMAT_CHECKER` or an equivalent.
   - There are no negative examples, such as a null field or an unknown property being rejected.
   - It doesn't assert 412/428 wherever `If-Match` is required, `Retry-After` on 429, `ETag` on GET/mutation 2xx responses, or `Idempotency-Key` on create.
9. **Examples are incomplete (`examples.json`):** the plan (lines 54-55) calls for examples across the full create → upload → rows → validate → commit → results sequence, including errors. Missing are `Manifest`, `Capabilities`, a valid `Validation`, and `Error` bodies for 409, 412, 428 and 429.
10. **Results links:** the plan (line 242) asks for links to the existing task and record pages, but `Results` has no field for them.
