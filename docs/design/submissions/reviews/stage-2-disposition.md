# Stage 2 review disposition

Round 1: one Major, no Critical findings.

- Major: submission capabilities now use the configured JWT audience plus
  `/submissions`; identity verification keeps its original audience. The legacy
  token filter also rejects submission-scope claims. Added a real-signature test
  proving the legacy search filter returns 401 for a submission token.
- Added maxDraftsPerUser/maxFieldsPerRow to discovery and published schemas.
- Noneditable PUT rows consistently returns 409; If-Match length matches code.
- Draft-capacity 429 no longer suggests a five-second retry.
- Authentication completes before chain.doFilter, preserving downstream errors.
- Added bounded strict JSON parsing, explicit UTF-8 validation, duplicate-key and
  trailing-input rejection, and parser tests. Jackson core 2.17.0 was already a
  transitive runtime dependency; it is now an explicit pinned compile dependency.
- Added real PostgreSQL quota-race, expiry and admin-access tests.
- Kept admission checks ahead of routing for writes: disabled or unenrolled callers
  do not reach the new mutating resource dispatch. This cosmetic error precedence
  is intentional; capabilities remains readable.

Local database testing also caught external writes to public persistent fields
not marking objects dirty. Fields are now private and mutations are performed
inside the enhanced entity's methods. Durability, competing revision and confirmed
rollback tests passed after that correction. A mock header-count assertion was
fixed to allow setting the same no-store header more than once.

Round 2: Claude found no Critical or Major issues. The expanded test run passed
38 tests with zero failures, errors or skips. Remaining Unicode input handling
and published schema bounds were corrected; unsupported-method 405 documentation
is tracked for the final contract publication. No upload,
validation, commit or worker implementation is claimed in this stage.
