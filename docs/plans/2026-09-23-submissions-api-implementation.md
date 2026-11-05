# Submissions API implementation plan

Status: all six implementation stages are local, 2026-09-23. Claude reviews have
converged at every stage with no remaining Critical or Major findings. Final
verification is recorded in the [workbench](../design/submissions/README.md).
The API is disabled by default; QA pilot/browser gates and deployment remain outstanding.

Based on the [accepted direction](../design/2026-09-23-submissions-engineer-brief.md)
and [supporting design](../design/2026-09-23-submissions-api.md), checked against
Wildbook `24cc99aede`.

## Outcome and first milestone

Deliver `/api/v3/submissions` alongside the existing bulk-import API. Reuse its
field names, validators, media creation and importer. Require configured location
and strict validation, default to import-only, and initially enable a few approved
partner accounts. Existing browser imports retain their behavior.

**First runnable milestone:** an enrolled partner can authenticate, discover
supported input, create a private draft, upload images, submit JSON rows, and
receive a validation report with stable source-row references. Drafts survive a
restart. This milestone creates no biological records and starts no IA jobs.

**Pilot release milestone:** the partner can commit that draft once, survive a
lost HTTP response without duplicate records, and retrieve record IDs and status.
Uncertain execution outcomes are held for reconciliation instead of retried.

The first milestone is an internal increment, not completion of the intake project.

## Delivery sequence

| Change | Deliverable | Dependency | Exit gate |
| --- | --- | --- | --- |
| 1 | Contract and legacy compatibility fixtures | None | Request/response examples and baseline test results recorded |
| 2 | Pilot access, owned drafts and revision control | 1 | Authorized draft lifecycle survives restart; concurrent creation deduplicates |
| 3 | Simple uploads and strict preview validation | 2 | First runnable milestone; no domain writes or IA |
| 4 | Narrow importer adapter and result mapping | 1, 3 | Existing behavior retained; new execution has explicit transaction/side-effect boundary |
| 5 | Durable commit, worker and results | 2–4 | Concurrent retries and crash scenarios pass against PostgreSQL |
| 6 | Reference client, operator runbook and pilot | 5 | End-to-end QA plus legacy browser smoke test passes |

Keep each change separately reviewable. A change can span multiple small PRs;
do not combine mechanical extraction with new behavior in one opaque diff.
No effort estimate is committed until change 1 establishes the test/build baseline
and change 4's lifecycle seam is understood.

## 1. Define the contract and establish compatibility

### Tasks

- Create a draft OpenAPI 3.0.3 document under `docs/design/` for the proposed
  routes; merge implemented operations into `src/main/resources/openapi.yaml`
  as they become available. `ApiDocsServlet` serves that resource today. Avoid
  advertising unimplemented routes as working production endpoints.
- Specify owner/context, UUIDs, revision/ETag, expiry, source metadata,
  `clientRowId`, row fields, manifest entries, validation reports, errors, and
  separate import/indexing/detection/identification states.
- Use examples for create → upload → rows → validate → commit → results.
  Include error examples, not just the successful sequence.
- Describe both existing legacy semantics and intentionally stricter new-API
  semantics in executable fixtures. Snapshot stable fields, not generated IDs,
  timestamps, log strings or incidental JSON ordering.
- Run the existing targeted suites before source changes; record any baseline
  failures without weakening assertions to obtain a green run.

### Contract choices to implement

| Concern | Concrete rule |
| --- | --- |
| Version | Envelope `contractVersion: "1"`; reject unsupported versions |
| Rows | Nonempty array of `{clientRowId, fields}`; unique row IDs per draft |
| Processing | `import-only` default; advertise other modes only when implemented and enabled |
| Strictness | Unknown/unsupported fields and invalid rows block commit; no legacy tolerance parameters exposed |
| Revisions | GET/mutations return quoted ETag; row/file mutations require `If-Match`; missing precondition `428`, stale `412` |
| Validation | POST targets a revision and returns `200` with `valid`; a check running successfully is not a valid submission |
| Commit | Requires validation ID, `If-Match` and `Idempotency-Key`; accepted work returns `202` and `Location` |
| Idempotency | Same key/input returns original resource/operation; different input `409`; replay recognized before stale-revision rejection |
| Visibility | Draft owner and explicit administrator access; record visibility remains existing Wildbook policy |
| Errors | Stable code, readable message, request ID and optional row/field/limit; no internal exception dump |
| Cancellation | Draft DELETE is retry-safe; queued/importing/imported submissions return `409`; no biological record deletion |
| Expiry | Retain a tombstone for advertised key retention; owner sees `410` for expired drafts during that period |

Canonical request hashes must include effective options, schema version and
ordered rows, with deterministic object-key ordering. Specify treatment of omitted
versus explicit defaults. Commit hashes reference immutable validation and manifest
content. File digests are server-computed. Publish idempotency retention before
partners depend on it; never imply perpetual deduplication after records expire.

### Legacy fixtures

Cover object rows versus `fieldNames`/array rows, synonyms, date precision,
submitter defaults, unknown-field tolerance, missing/corrupt images, grouping
multiple rows into one encounter, existing individual/occurrence links, foreground
and background status shapes, skip flags, and indexing/IA handoffs.

Existing starting points are `BulkApiPostTest`, `BulkApiOtherTest`,
`BulkGeneralTest`, `BulkImagesTest`, and `BulkImporterMissingAssetTest` under
`src/test/java/org/ecocean/api/bulk/`. Some declared Java packages differ from
their directories; inspect declarations when adding tests. These are useful unit
fixtures, not proof of durable transactions or concurrency.

## 2. Add pilot access and owned drafts

### Code boundaries

- New `api/Submissions.java`: HTTP routing, parsing, headers and error conversion.
- New `api/submission/` services: authorization policy, draft persistence and
  transitions. Names are proposed; keep HTTP logic separate from transactions.
- New persistent classes under `org.ecocean.submission`, with matching
  `src/main/resources/org/ecocean/submission/package.jdo` metadata.
- Add only the new route/filter mappings to `src/main/webapp/WEB-INF/web.xml`.
- Reuse `api/auth/JwtService.java` and the identity-resolution pattern in
  `security/WildbookTokenAuthenticationFilter.java`; keep read-path policy intact.

### Tasks

- Add independent feature controls for new admission and commit, both off by
  default. Disabling admission must leave authorized status/results available
  for accepted work. Check the enabled partner accounts on each new mutation.
- Identify pilot users by stable user UUID and installation context. Removing
  enrollment prevents new mutations; owners can still inspect existing work.
- Extend token issuance with an explicit, optional import capability request.
  Existing issuance without that request keeps its current behavior. Verify
  credentials and enrollment before signing the capability; a scope claim is
  never accepted merely because it appeared in a request body.
- Test token subject/context, expiry, account eligibility, signed capability,
  no cookie fallback for a bad Bearer token, and mixed-identity requests.
- Support session authentication only with a tested CSRF check for the new
  writes. If no suitable existing mechanism is available, implement a scoped
  one before enabling that path; a token-only internal milestone is acceptable
  if discovery/docs accurately advertise it.
- Implement draft creation, GET, rows replacement, cancellation and capabilities.
  Resolve owner on the server. Hide resource existence from unauthorized callers.

### Persistence requirements

Persist submission identity, owner/context, source, revision, state, payload and
manifest references/hashes, validation reference, reserved ImportTask ID, execution
metadata, errors and timestamps. Store large content privately and immutably;
publish a new database reference only after the file is fully written.

Use a separate operation/idempotency record where useful, with a database unique
constraint on context, principal, operation and key (or a collision-safe bounded
key digest). Insert the create operation and draft in one transaction. Use database
optimistic versioning or locking for state transitions; JVM synchronization alone
does not protect a multi-process deployment.

Verify new metadata is discovered by Maven enhancement and deployed JDO setup.
Test schema constraints in PostgreSQL and write a rollout/rollback note for added
tables. Do not assume automatic schema updates prove uniqueness enforcement.

## 3. Add uploads and validation

### Tasks

- Add streaming, single-file multipart upload and manifest GET. Reuse
  `UploadPaths` containment/name validation. New drafts use private owned staging;
  old upload routes and staging conventions stay untouched.
- Reserve quotas atomically before writing, enforce actual-byte limits during
  streaming, and release reservations on failure. Include multipart overhead in
  the request bound. Bound decode dimensions/resource use as well as file bytes.
- Write a temporary file, verify it, then finalize its manifest entry atomically
  with revision advancement. Clean up a finalized-but-unreferenced file after a
  failed database update; never make it visible as a completed upload prematurely.
- Serialize manifest mutation for the first version. For a lost upload response,
  clients GET the manifest and reconcile digest/name before retrying at the new
  revision. Parallel uploads with one stale ETag are not silently accepted.
- Implement same logical filename/digest retry success; reject different content
  and collisions introduced by filename cleaning or filesystem case behavior.
- Add a `SubmissionValidator` that copies JSON input, performs new contract and
  permission checks, and calls `BulkImportUtil.validateRow`/`BulkValidator`.
- Use the configured location hierarchy (`LocationID.getLocationIDStructure`,
  also used by `SiteSettings`) to derive valid IDs. Do not treat a format check
  alone as configured membership, and do not change shared required fields.
- Validate actual staged media without creating domain objects. Persist the report
  against its input revision and relevant config digest. A concurrent edit makes
  the report stale and prevents a transition to `validated`.
- Count media after the importer's grouping semantics are applied. Reuse/extract
  a small grouping helper if necessary; do not implement conflicting grouping
  rules. If this requires change 4 first, keep validation unavailable until then.
- Restrict the initial supported field set explicitly. Authorize fields that can
  link or affect existing individuals, occurrences, projects or owners. Discovery
  lists the supported subset rather than implying every validator field is enabled.

### First milestone demonstration

Using an enrolled test account and two photographs, create a draft, upload both,
submit valid rows, and see their source IDs and normalized preview. Then show a
bad location, unknown field, missing image, corrupt image, stale revision and
cross-owner request produce actionable errors. Restart the application and show
the draft survives. Verify domain entity counts and IA dispatch counts unchanged.

## 4. Introduce the importer adapter without changing legacy behavior

Read the full lifecycle before extracting: `BulkImport.doPost`, its task/media/IA
helpers, `BulkImporter.createImport`, `UploadedFiles.makeMediaAsset`, and ImportTask
status serialization. Include cache handling, post-commit deep individual reindex,
media derivatives and matching options in the compatibility checklist.

### Tasks

- Create an execution adapter taking explicit context, user identity, reserved
  ImportTask ID, immutable input, staged files and processing options.
- Reuse field validation and `BulkImporter` conversion. Do not call a servlet
  through fake HTTP requests or make authenticated loopback requests to reuse it.
- Extract only helpers required by both callers. Avoid moving the entire servlet
  into a new abstraction or altering `processRow` business semantics.
- Add an optional result collector mapping each client row to actual encounter,
  occurrence, individual and media IDs. Capture this when rows resolve entities;
  do not rely on cache iteration order or zip aggregate arrays to input rows.
- Add an opt-in way to defer derivative/indexing work until after commit, retaining
  the legacy default. The current importer invokes
  `MediaAsset.updateStandardChildrenBackground` before its caller commits.
- Return durable result metadata and post-commit work intent. Use the worker's
  own Shepherd and reload entities there; never transfer request-scoped JDO
  objects into a background thread.

Gate this change on the legacy fixtures plus integration assertions that imported
records and row mappings match the legacy equivalent, shared entities stay
consistent, and no new-path side effect runs before a successful commit.

## 5. Add durable commit and results

### Tasks

- Under a database lock/version check, verify authorization, revision, completed
  files and validation; freeze input, reserve an ImportTask ID, persist `queued`
  and the operation response, then return `202`. One submission has at most one
  accepted execution even when callers use different idempotency keys.
- Recheck current policy/configuration at execution. If input interpretation or
  authorization changed, fail before domain creation with an explicit reason;
  never silently apply new defaults to a previously reviewed draft.
- Use bounded workers with durable claims and per-installation/user concurrency
  limits. Integrate worker startup/shutdown into existing application lifecycle
  after locating the appropriate hook; do not add an untracked servlet thread.
- Persist domain objects, row mappings, imported state and post-commit intent in
  one transaction where supported. Separate progress transactions are advisory.
- Handle database commit errors as potentially uncertain until reconciled. A
  worker lease expiring does not establish rollback; use a fencing mechanism
  before automatic takeover. Conservative manual reconciliation is sufficient
  for the first pilot when the outcome cannot be established safely.
- Execute/reconcile derivatives, indexing and requested IA from persisted intent.
  Preserve imported state if downstream work fails. Advertise only processing
  modes whose dispatch/recovery behavior is covered; IA uncertainty is explicit.
- Add paginated results with stable ordering/cursors and source-row mapping.
  Return links to existing task/record pages, filtered by current authorization.
- Add expiry and orphan cleanup that excludes active and uncertain jobs, respects
  key/tombstone retention and never removes shared or pre-existing assets.

### Required failure-injection tests

| Scenario | Expected result |
| --- | --- |
| Two concurrent create requests, same key/input | One draft; both resolve to it |
| Same key, changed input | `409`, no second draft/import |
| Concurrent commit, same or different keys | One accepted execution |
| Response lost after queue transaction commits | Retry returns original operation |
| Crash before worker claims queued job | Job remains durably discoverable |
| Crash during import transaction | Rollback proven before retry, or reconciliation state |
| Crash after domain commit before dispatch | Records retained; persisted intent available |
| Worker lease expires while worker still runs | No second concurrent writer |
| IA accepts work but dispatch acknowledgement is lost | No blind duplicate dispatch; reconciliation if no deduplication proof |
| Disk failure or DB rollback after copying assets | No false success; safe orphan cleanup |
| Feature admission disabled during execution | Accepted work drains/reconciles; status remains readable |

Use real PostgreSQL transactions and independent persistence contexts for
concurrency/recovery tests. Mock-based servlet tests cannot establish these
guarantees. Reset process-wide PMF/configuration state between container tests.

## 6. Deliver a usable pilot

- Publish implemented OpenAPI operations, exact limits and retry rules. Keep
  capabilities consistent with enabled processing and upload modes.
- Add a small reference client using only the documented HTTP contract. It stores
  submission/operation IDs before retrying, reconciles uploads, presents validation
  errors, commits explicit revisions and polls with backoff. Keep credentials out
  of example source and logs.
- Document how operators enroll/remove partners, choose limits, disable admission,
  inspect stuck work, reconcile uncertainty and expire drafts safely.
- Track accepted/failed/reconciled submissions, time spent in each phase, queue
  age, upload bytes/quota use and downstream status. Log correlation IDs and
  counts without raw tokens, full row payloads or sensitive locations.
- Pilot on QA with one selected partner integration. Compare a representative
  legacy import and new API import, including grouped rows and partial dates.
- Confirm no regressions in the browser upload → review → import → task workflow.
  Broader enrollment follows observed results; deployment is a separate action.

## Verification commands and evidence

These are planned checks, **not reported passes** from this documentation change.

```bash
mvn test -Dtest=BulkApiPostTest,BulkApiOtherTest,BulkGeneralTest,BulkImagesTest,BulkImporterMissingAssetTest
mvn test -Dtest=AuthTokenTest,AuthTokenStepUpTest,WildbookTokenAuthenticationFilterTest
mvn test -Dtest='UploadPaths*Test'
```

Run new submissions tests at each stage, then `mvn clean install` for the final
integration gate, including DataNucleus enhancement. Capture command, revision,
result, environment and baseline failures in each PR. Run frontend regression
checks using the repository's CI Jest runner from `frontend/`:

```bash
CI=true npx jest --ci --runInBand --testPathPattern='BulkImport|bulkImport'
```

Existing frontend test failures must be distinguished from introduced failures.
Manual browser smoke testing remains necessary for unchanged-client compatibility.

## Rollback and deferred work

Disable new admission/commit, keep status access and drain/reconcile accepted work.
Retain new tables and private artifacts while work or retention obligations remain.
An older application rollback requires workers stopped and queued work accounted
for; do not assume removing the feature flag makes an in-flight import disappear.

Defer resumable uploads, general upserts, anonymous intake, new UI, spreadsheet
parsing, webhooks and broad delegated OAuth. Universally requiring location in
legacy bulk import is a separate compatibility change. Keep the agreed ownership,
strict validation and commit-retry behavior in the pilot scope.

## Final requested addition: agent skill

After the six-stage API implementation and successful full Java build, added the
public `submit-sightings` skill, registered in the existing AgentSkill catalog and
linked from the base toolbox and API reference. It documents every supported field,
wire formats, configured-value discovery, concrete validation failures and safe
recovery. Fifteen skill tests passed; two Claude review rounds converged with no
Critical/Major findings. See the workbench for transcripts and packaging evidence.
