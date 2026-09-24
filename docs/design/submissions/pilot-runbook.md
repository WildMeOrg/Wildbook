# Submissions pilot: operator and integration handoff

Implementation is local and disabled by default. No QA or production deployment,
partner enrollment, or live import has been performed. Verification is recorded in README.md; outstanding deployment checks below are release gates.

## Installation controls

In the installation's private `apiAccessKeys.properties` override (read fresh on each
policy check, independent of browser/user configuration caches):

```properties
submissions.enabled=false
submissions.commitEnabled=false
submissions.workerEnabled=false
submissions.stagingDirectory=/srv/wildbook-private/submissions
submissions.allowedUserIds=<first-partner-user-UUID>,<second-partner-user-UUID>
```

Use existing trusted integration users, with real usernames, and the installation's
existing RSA JWT configuration. Start with one or two partners. Context0 only.
Setting `enabled` permits new mutations; `commitEnabled` independently permits
queue acceptance. `workerEnabled` starts the lifecycle-managed worker at application
startup and controls processing at runtime. Restart after enabling workers for the
first time. Admission can be disabled while accepted jobs drain. Removing a partner
blocks new writes and causes unstarted imports for that owner to fail eligibility.
Owners can continue status/results reads with a submissions:read token.

Create the staging directory outside the webapps tree, legacy upload directory,
import directory, and every local asset-store root. Give only the service account access (0700 where supported).
Use a filesystem shared by all application instances, with capacity monitoring.
Do not share this directory across contexts or separate installations. This pilot
only supports context0. The new table has not been deployed; do not reuse an
experimental SUBMISSION table with an older schema without an explicit migration.
The default media-size configuration still bounds each file. Configure Tomcat's
upload/read timeout (for example `disableUploadTimeout="false"` and an appropriate
`connectionUploadTimeout`) and reverse-proxy request deadlines: a blocked idle
socket cannot be interrupted by the application's per-read two-minute deadline.
The application admits two expensive intake operations per JVM, at most one per
owner. A worker uses one slot, leaving a slot for uploads/validation. Contention returns
429; this conservative setting intentionally limits pilot throughput.

Build with DataNucleus enhancement. Apply the additive SUBMISSION table metadata
from `src/main/resources/org/ecocean/submission/package.jdo` using the installation's
normal schema rollout process, including the create-key uniqueness constraint and
LOCK_VERSION. When upgrading a development schema from an earlier increment, backfill new
primitive timestamp columns with zero before enforcing NOT NULL. Job/report/result
strings may be null on older drafts. Test schema
creation and upgrades in isolated PostgreSQL before deployment. Do not point a
local build or test run at a production database. The existing biological tables
and bulk-import defaults do not need a data migration.

The existing Maven WAR configuration excludes `WEB-INF/web.xml`. Deploy the new
servlet and Shiro filter mappings from `src/main/webapp/WEB-INF/web.xml` through
the installation's descriptor rollout process as well as deploying the WAR.
Verify the running descriptor contains both submissions route patterns and their
Bearer filter before enabling admission; a WAR-only update is insufficient.

## Agent skill

After deployment, the public toolbox at `/api/v3/agent-skill` links to
`/api/v3/agent-skill/submit-sightings`. Give that skill URL to a coding agent along
with the installation URL and its separately supplied scoped token. The skill
contains the exact supported row fields, settings discovery, request formats,
field-specific validation failures and safe recovery instructions. Public skill
availability does not enable intake or enroll an account. Include fetching the
base toolbox and new skill in QA deployment checks.

## Authentication and reference client

Mint a bearer token using fresh HTTP Basic credentials at
`POST /api/v3/auth/token?scope=submissions:write`. Do not put credentials in a URL.
The response contains `token`, `tokenType`, `expiresInSeconds`, and `scope`.
Renewal requires fresh credentials. A read token uses `scope=submissions:read`.
Omitting scope retains legacy identity-only issuance. Scoped submission tokens have
a separate JWT audience and cannot be used with legacy search routes. Browser
cookie authentication is not accepted on the submissions resource.

Store a token in `WILDBOOK_SUBMISSIONS_TOKEN`, then use the POSIX Python 3 client:

```bash
python3 scripts/submissions/client.py \
  --base-url https://your-qa-installation.example \
  --rows rows.json --media-dir ./photos --state ./submission-state.json
```

This creates/uploads/validates without committing. Inspect the reported validation
errors, correct rows.json and rerun with the same state file to update the same
draft. The client checks that the server still holds its previously saved rows
before replacing them. Repeat with `--commit` when ready. Keep the same state file for all
retries and restarts. It contains IDs and operation keys, not credentials. A file
lock prevents concurrent clients using that state on a local POSIX filesystem;
network filesystem and cross-host locking are not supported. Use one state file per batch.
The client refuses redirects and remote cleartext HTTP, disables environment
proxies, and honors Retry-After with up to five minutes of contention retries. Renew expired tokens and
resume with the same state. Never create a replacement batch merely because a
commit timed out. Use `--cancel --base-url ... --state ...` to cancel an editable
draft without supplying rows/media. If the create response was lost before its ID
was saved, first rerun the normal command to recover the ID using its saved key.
A frozen commit that was never accepted can
be cleared using `--reset-commit`: the client first requires server state draft
or validated with no operation ID. Then correct/revalidate and commit. Accepted,
failed or uncertain executions cannot be reset or cancelled through this client.
Changed image content needs a new filename, or cancel the draft and start a new
state file; completed file contents are immutable.

Example `rows.json` for one new encounter with two photographs:

```json
{"rows":[{"clientRowId":"camera-observation-001","fields":{
  "Encounter.genus":"Manta",
  "Encounter.specificEpithet":"birostris",
  "Encounter.year":2026,
  "Encounter.locationID":"REPLACE_WITH_CONFIGURED_LOCATION",
  "Encounter.mediaAsset0":"photo-1.jpg",
  "Encounter.mediaAsset1":"photo-2.jpg"
}}]}
```

Use the installation's configured taxonomy and location. Discover exact supported
fields, modes and limits at GET `/api/v3/submissions/capabilities`. The pilot creates
one encounter per row; explicit encounter, individual, sighting, project and owner
fields are unavailable. It accepts JPEG and PNG with matching filename extensions.
Each photograph belongs to one row; duplicate references are rejected. Optional
date parts remain optional; a year-only observation stays year-only. Limits are
200 rows, 256 fields per row, 200 files and 200 MiB completed bytes per draft, 20
live drafts, 20 new drafts per rolling 24 hours, and one active job per owner.
Cancellation does not refund the daily creation budget. File-count and byte overages return 413.

Use GET rows/files to reconcile lost edit/upload responses. PUT rows replaces the
whole row list. Edits require the current quoted If-Match revision and invalidate
validation. Validate returns 200 with `valid=false` for data errors; it does not
advance the revision. Commit requires a current validation ID, revision and saved
Idempotency-Key. Same-key/same-input retries recover the accepted operation;
different keys cannot launch a second execution for the same submission.

## Status, recovery and retention

Poll the submission and its paginated results. Imported means domain records,
source-row mappings and the post-import intent committed together. Detection and
identification are skipped in this pilot. Derivative state is reported separately.
Indexing `unknown` means submitted to the existing asynchronous indexing queue;
this implementation does not assert search completion. Failed index dispatch
is held as failed for operator inspection; derivative unknown leaves indexing
pending until an operator reconciles the derivative work. Pending post-import work is scanned separately from history. Index intent is
replayed idempotently after worker restart in five-item batches, using a startup
watermark and bounded pagination. Check record pages/search during QA acceptance.

One installation-wide import writer is claimed through PostgreSQL. A crash before
claim leaves a queued job discoverable. A stale importing claim is moved to
`needs_reconciliation` after an hour only when its transaction lock can be acquired.
It is never automatically rerun. A delayed worker must recheck state under that
same lock. Imported state wins when reconciling a lost database-commit acknowledgment.
Interrupted derivative generation uses its own claim timestamp and is held as
`unknown`, preserving imported records. A crashed import can pause the installation
queue for up to one hour before the conservative reconciliation check.

For uncertain work, disable commit admission and inspect the submission, reserved
ImportTask ID, row mappings and database records using a fresh connection. Stop all
workers before an operator repairs state. Establish whether the transaction committed
before considering a retry. There is deliberately no public retry/requeue endpoint.
A reconciliation patch must preserve the original operation IDs and audit the
operator's evidence; do not change needs_reconciliation back to queued blindly.
When resolving to imported or certainly failed, record the completion time through
the entity transition (including `completedAt`) so staging retention can finish.
Uncertain owners remain at their one-job limit until reconciliation.

The hourly private-staging sweeper releases manifest references for expired and
cancelled drafts under short locks. It also releases staging for imported or
certainly failed submissions seven days after completion; imported asset-store
originals and row mappings remain intact. Active and uncertain work is retained.
Released manifests become empty without changing the frozen execution revision.
Subsequent scans skip them, using keyset pages so reference removal cannot skip
other drafts. A ten-second inventory deadline aborts physical deletion safely but
keeps completed reference-release progress for the next pass. At most 5,000 old
unreferenced blob directories are removed per pass. Errors are isolated from intake.

Tombstones and operation keys remain in the database for retry safety; no automatic
database purge is implemented. Monitor retained bytes and database growth before
broader enrollment. Asset-store copies from rolled-back imports remain under the
reserved task namespace for operator inspection. The staging sweeper never deletes
shared or pre-existing media assets. Keep staging configuration stable while work
is active; drain before relocating it.

Monitor worker attempt/error logs by submission ID, queue age via createdAt/state,
phase failures, 429s and staging bytes. The new deferred importer suppresses its
raw row diagnostics. Do not log tokens or submission bodies. Application metrics
export/dashboard integration remains an operational follow-up.

## QA release gate and rollback

Before enabling a partner, deploy to QA and verify:

- Two-image valid import produces the expected encounter/media/task and source-row
  mapping; partial dates retain precision and configured ownership is correct.
- Bad location, unknown field, absent/corrupt image, stale revision and cross-owner
  access produce the documented errors without biological records or IA work.
- Restart after draft creation, after queue acceptance and after domain commit;
  reconcile saved IDs and verify no duplicate execution.
- Compare the existing browser upload/review/import/task flow against its baseline,
  including a legacy import with grouped rows. Check derivatives and search.
- Validate schema constraints, private storage permissions, request deadlines and
  shared staging across instances. Toggle admission, commit and worker flags on
  an API-only instance and verify they are read fresh. Start with one selected partner.

Rollback: disable admission and commit, confirm no importing or derivative-running rows
remain (or reconcile them), then stop
workers before reverting application code. Retain the added table and private files
while work or retention obligations remain. Turning off a flag alone does not undo
an import or prove an in-flight transaction stopped.
