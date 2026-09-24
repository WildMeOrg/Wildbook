# Stage 2: private draft API

Historical stage-two increment (see pilot-runbook.md for current configuration).
This increment implements drafts only: create, inspect, replace/read rows and
cancel. Upload, validation and commit remain unavailable until their review gates
pass. Existing bulk import and browser uploads retain their routes and policies.
Do not enroll production partners until the complete pilot milestone passes.

Configuration (server-side, never supplied by callers):

- Private `apiAccessKeys.properties`: `submissions.enabled=true` enables new writes;
  omission defaults to disabled.
- Private `apiAccessKeys.properties`: `submissions.allowedUserIds` is a comma-separated
  list of enrolled Wildbook user UUIDs. The existing JWT keys and issuer are reused. Submission tokens use the
  configured audience with `/submissions` appended, distinct from identity tokens. This pilot supports `context0` only.

Fresh HTTP Basic credentials at `POST /api/v3/auth/token?scope=submissions:write`
issue the explicitly requested write capability only to enrolled users while
admission is enabled. `scope=submissions:read` lets authenticated owners renew a
read credential after unenrollment or shutdown. Omitting scope preserves existing
identity-only tokens, which are rejected by the submissions API.

The new endpoints accept Bearer authentication only. They do not fall back to a
browser session, inherit its roles or mint a session. Administrators are checked
against the token's user, not a cookie. Writes also recheck current enrollment.

Create requires an Idempotency-Key; GET returns ETag; PUT rows and DELETE require
If-Match. A repeated create returns its original body/ETag: GET the resource before
editing. A cancelled draft stays readable, including its original rows; repeat
DELETE with the original header is successful. Expired drafts remain readable but
cannot be edited. No endpoint deletes imported records.

Current bounds: 200 rows, 2 MiB JSON request, 256 fields per row, 20 active drafts
per account, seven-day draft lifetime. These are published in capabilities.
Tombstones and operation keys are retained for at least seven days; this increment
has no physical cleanup job. Later cleanup must preserve that guarantee.

The new `SUBMISSION` table has a UUID primary key, unique scoped create-key hash,
and a JDO version column. Payloads are bounded JSON text. No legacy table is
changed. PostgreSQL transaction advisory locks serialize creation per owner and
mutation per submission across application processes; JDO versioning provides an
additional check. Every read uses a fresh transaction and refreshes cached values.
Only a confirmed commit is reported as successful. An uncertain commit returns
503; clients reconcile or retry their original create key rather than invent one.

Deployment must include DataNucleus enhancement and creation of the new table and
unique/version constraints. Test against an isolated PostgreSQL before enabling.
Disable admission to roll back functionality; retain the table and status access.

Stage-two review and test evidence are recorded in `reviews/` and the workbench
README. This document does not claim later stages are implemented.

Submission tokens are rejected by legacy search/media token verification. Draft
capacity errors return 429 without suggesting a five-second retry; cancel a draft
or wait for expiry. JSON parsing rejects invalid UTF-8, duplicate keys, trailing
content and nesting beyond 32 levels.
