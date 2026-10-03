Stage 3 now converges: M1–M4 are fixed and I found no Critical or Major issues. This was a read-only review. I didn't run anything, so compile and test results are whatever your current run reports.

## Verification of M1–M4

**M1: fixed.**
- `receive()` at `SubmissionFiles.java:58-66` unwraps `FileUploadIOException`. A size-limit cause returns 413 and anything else returns 400. `FileUploadException` and `InvalidFileNameException` return 400, which covers errors raised by `hasNext()`.
- `inspect()` at `:154` turns any `IOException` from reading or decoding into 422 `VALIDATION_INVALID`. It doesn't swallow the dimension 413, because `SubmissionException` is a `RuntimeException`.
- `SubmissionFilesTest.java:39-49` exercises an oversize file and a truncated PNG with unknown length through the real parser, and checks that no staging files are left behind.

**M2: fixed in code.** `FILE_CONTENT_CONFLICT` (`SubmissionStore.java:127`), `VALIDATION_INVALID` (`SubmissionFiles.java:108,139,154`) and 410 `GONE` (`SubmissionStore.java:102`) are all in the `Error.code` enum at `openapi.yaml:409-427`. The file-count limit returns 413.

**M3: fixed.**
- `configuredRoot` (`SubmissionFiles.java:29-45`) takes the real path of the staging directory and rejects overlap in either direction with:
  - the legacy upload directory
  - the parent of the webapp's real path, which covers the data directory
  - `importDir`
- It fails closed if `getRealPath` returns null or the parent is null. The only public constructor that reads configuration requires a `ServletContext`.
- Blob directories are created with mode 700 where the filesystem supports POSIX permissions.
- Tests cover both overlap directions.

**M4: fixed.**
- Upload and validate take the `SubmissionResources` slot (JVM-wide plus one per owner) before `open()`, and return 429 with `Retry-After: 5` when it's taken.
- The draft lock uses `pg_try_advisory_xact_lock` and returns 429.
- The write loop has a 2-minute wall-clock limit.
- Decoding uses 4×4 subsampling.
- The nested `finally` at `SubmissionStore.java:133-140` always runs blob removal and logs removal failures instead of throwing them.

**Also verified:**
- Duplicate media is rejected within a row and across rows (`SubmissionValidator.java:54-55`).
- The media check only runs on supported fields, so the earlier double-report is gone.
- The file extension must match the content (`SubmissionFiles.java:106-108`).

## Critical
None.

## Major
None.

## Minor
1. **Truncated multipart body still returns 500.** If the body ends mid-part, the part stream throws `MultipartStream.MalformedStreamException`. That's a plain `IOException`, not a `FileUploadIOException`. It escapes `receive()` and hits the generic 500 handler at `Submissions.java:60`, which logs a stack trace. Retrying is the right client behaviour anyway, and most cases are client disconnects, so this isn't Major. Fix: catch `MultipartStream.MalformedStreamException` in `receive()` and return 400. The "truncated" test covers a truncated PNG, not a truncated multipart body.
2. **The JVM-wide slot count is 1** (`SubmissionResources.java:5`). One admitted user uploading slowly for 2 minutes, or validating a 200-file draft, which has no time limit, gets every other user a 429 for that whole time. That's acceptable for a gated pilot, but make it configurable and consider a separate, smaller limit around `inspect()` only, before wider rollout.
3. **Unsupported formats return 422 with `CAPABILITY_UNAVAILABLE`** (`SubmissionFiles.java:143`). A GIF, BMP or TIFF has an ImageIO reader, so it reaches this line. The spec implies `CAPABILITY_UNAVAILABLE` means 503. Use `VALIDATION_INVALID`.
4. **Limits are only checked after the whole stream is received.** When a draft already has 200 files, or no byte budget left, a new file is streamed in full before the 413 at `SubmissionStore.java:129`. Check the count and remaining bytes before `receive()` (a retry of an existing name is the exception) and pass `min(maxFileBytes, remaining)`.
5. **`Retry-After` depends on the message text.** It's only set when the message contains "five seconds" (`SubmissionAuthenticationFilter.java:94`), so the 20-active-drafts 429 has no `Retry-After`. Put the header value on the exception instead.
6. **Cleanup can hide the original error.** `receiveMultipart`'s cleanup (`:82`) and `write()`'s `finally` (`:113`) can throw `IOException` and hide the original exception. Log it instead.
7. **Test gaps:**
   - `DUPLICATE_MEDIA` (within a row and across rows) is not asserted in `SubmissionValidatorTest`.
   - No extension-mismatch test.
   - No test for the `tryLock` 429, the file-count limit or the draft-byte limit.
   - No truncated-multipart-body test.
   - `SubmissionFilesTest:72` holds the static global slot, so running JUnit tests in parallel would make the DB upload test fail intermittently.
8. **Deployment note, as you said:** a connection that goes idle mid-read is only bounded by the container's read timeout. The deployment docs must require one.

## Spec lag (minor schema issues, separate from the above)
- **408 not documented:** the upload wall-clock limit returns 408 `BAD_REQUEST`, but 408 isn't listed for `uploadSubmissionFile`.
- **405 not documented:** the filter's 405 isn't documented anywhere.
- **410 never returned:** upload and validate list 410, but `editable()` returns 409 `INVALID_STATE` for cancelled or expired drafts. Either document 409, or run the 410 check before `editable()`.
- **Validate 422 doesn't match:** the validate description says 422 means no rows. The code returns 200 with `valid=false` and `REQUIRED_VALUE`.
- **`rowFields` shape:** the schema requires `additionalProperties: {type: object}`, but the code emits `supported` (array), `indexedMedia` (string) and `required` (array).
- **Undocumented capabilities fields:** `maxFiles`, `maxImagePixels`, `uploadMediaTypes` and `operations` values aren't in the schema. They're allowed by `additionalProperties: true` but not described.
- **200-file limit wording:** I couldn't find the 413 for this limit stated explicitly. Only the generic "413 for input size" line at `openapi.yaml:440` covers it. Add it to the upload description or add a `maxFiles` limit.
- **Undocumented issue codes:** `DUPLICATE_MEDIA`, `MISSING_MEDIA` and `INVALID_MEDIA` aren't listed. `Issue.code` is free-form, so nothing breaks.

As you asked, I didn't review the Stage 4/5 worker and commit code (`SubmissionJobs`, `SubmissionImporter`) or their tests.
