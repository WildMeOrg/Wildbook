# Generic-client intake: accepted design direction

Based on [your bulk-import proposal](https://qa.wildme.org/bulk-import-next.html)
and implementation checkout `24cc99aede` (the PR is based on `main` at
`dcf6f460de`; verification provenance is recorded in the workbench). The senior engineer accepted the three design
decisions below on 2026-09-23, as relayed by the user. Detailed implementation
mechanics are documented in the [implementation workbench](submissions/README.md).
Runtime implementation is locally verified and disabled by default; nothing has
been deployed. The workbench records test results and remaining QA gates.

## Implementation for engineering review

The sibling resource, durable drafts/uploads, strict validation, commit-once queue,
worker, result mapping and resumable Python client are implemented locally. All
six stages received Claude reviews; final rounds report no Critical or Major
findings. Review transcripts and executed checks are in the workbench above.

The main shared-code change is an opt-in importer mode: it persists through the
caller's transaction and defers indexing/derivatives. Existing callers keep their
defaults. This boundary matters because several existing Shepherd creation helpers
commit internally. New PostgreSQL tests exercise rollback, concurrent acceptance,
lost commit acknowledgments, recovery holds and actual two-image import mappings.

The pilot intentionally accepts new encounters only, JPEG/PNG uploads and
import-only processing. It requires configured locations and an explicit account
allowlist. Uncertain imports require operator reconciliation; search-index
dispatch is reported without claiming indexing completion. See the
[pilot runbook](submissions/pilot-runbook.md) for configuration and the remaining
QA/browser release gate. The Python client provides a human/integration entry
point; no new browser form is included. The final addition is a public agent skill
at `/api/v3/agent-skill/submit-sightings`, linked from the existing base toolbox,
with complete field formats, validation examples and retry guidance.

## Recommendation

Agree with the central approach: reuse the existing bulk-import JSON rows,
validators, media creation, and importer. Keep the working React workflow and its
API contract stable. No new spreadsheet parser or encounter-creation pipeline.

I recommend a small **submissions API alongside bulk import** to handle the
additional lifecycle that agents and third-party integrations need:

**Create draft → upload images → validate → commit once → poll results.**

The new API owns authorization, staged files, validation revisions, and retries.
The existing importer owns the conversion into Wildbook records. A submission
can contain one encounter or a batch. Humans can use the same contract through
a CLI now and a form later.

## Suggested changes to the original proposal

| Topic | Recommendation |
| --- | --- |
| Authentication | Reuse JWT infrastructure, but explicitly issue an import capability. A new filter name alone does not distinguish an import token from existing read-only tokens. Keep existing token behavior stable. |
| API boundary | Add `/api/v3/submissions` for the new lifecycle. Preserve `/api/v3/bulk-import` and browser upload routing. Reuse Java components behind a small adapter. |
| Retry safety | Persist an owned draft before upload; freeze it at commit; accept one import per submission. Require idempotency keys for creation/commit. A timed-out request must not lead to duplicate encounters. |
| Upload | Start with streamed, one-file multipart uploads. Use owned staging and a size/digest manifest. Same filename/content can be retried; changed content is a conflict. Add resumable upload afterward using existing chunk mechanics. |
| Validation | Reuse `BulkValidator` and `BulkImportUtil`. Apply stricter new-API policy at its boundary rather than changing shared defaults immediately. Require configured location, reject unknown fields, and validate media before commit. |
| Limits | Separate per-file bytes, request bytes, draft storage and row limits. Apply `maximumMediaCountEncounter` to the resulting encounter after row grouping, not to the whole upload batch. |
| Completion | Report data import separately from indexing, detection and identification. A downstream IA failure must not imply that submitted records disappeared. |

The current checkout has evolved since parts of the proposal: upload requests
already have configured byte bounds, chunk state keys include the destination
path, and ImportTask authorization includes collaboration/admin rules. Token TTL
is server-configured. Implementation should start from these current behaviors.

## Smallest useful first release

- Authenticated, explicitly enrolled integrations; existing browser bulk import
  continues unchanged.
- Discovery of supported fields, configured values, processing choices and limits.
- JSON rows using existing `Class.fieldName` names, plus a client row ID for
  diagnostics and mapping results back to source records.
- Simple image uploads; strict validate-and-commit workflow; polling and links to
  created records and the existing ImportTask.
- Explicit processing choice: import only, detect, or detect and identify.
  Recommend import-only as the new API default, preserving old API defaults.
- Durable commit acceptance and conservative recovery. If a worker crashes and
  commit outcome is uncertain, expose a reconciliation state instead of blindly
  retrying record creation.

Defer anonymous intake, general upserts, webhooks, CSV/XLSX parsing, new human UI,
and broad third-party OAuth. Do not give an agent a user's password; use a trusted
client/service to obtain its short-lived credential.

## Engineering boundary and rollout

The importer is reusable, but its servlet orchestration is not already a service
interface. Extract only needed lifecycle helpers, preserving existing defaults
and sequencing with characterization tests. `BulkImporter` also starts some
indexing/media-child work before its caller commits; a new durable worker must
account for that with a small, tested deferred-side-effects seam.

Implement in three reviewable stages:

1. Characterize the existing flow and agree on the new contract.
2. Add gated authentication, owned drafts, simple uploads and validation.
3. Add the execution adapter, commit deduplication and recovery tests; pilot one
   installation/integration before broader enablement.

This is more work than enabling Bearer authentication on the existing POST, but
the additional work addresses unattended-client behavior without a broad importer
rewrite. If delivery must be reduced, cut resumable upload and client conveniences
first; retain ownership and safe commit retry semantics.

## Accepted decisions — 2026-09-23

1. **Use a sibling submissions resource.** Reuse the bulk-import pipeline behind
   the new lifecycle API.
2. **Require configured location and strict validation; default to import-only.**
   Apply these defaults to the new API and preserve legacy behavior. Requiring
   location universally may be a reasonable later correction, but is outside
   this rollout to avoid disrupting working imports.
3. **Pilot with a few approved partners.** Only accounts explicitly enabled for
   the pilot can use the new API initially. This is what the proposed enrollment
   allowlist means; the API is not opened to all users at launch.

The [implementation plan](../plans/2026-09-23-submissions-api-implementation.md)
breaks delivery into six reviewable changes, starting with the OpenAPI contract
and compatibility tests, then gated drafts, uploads and validation. Pilot partners,
installation, resource limits and retention need operational selection before rollout.

The [supporting design](2026-09-23-submissions-api.md) includes proposed endpoints,
payloads, state transitions, recovery behavior and regression acceptance gates.
