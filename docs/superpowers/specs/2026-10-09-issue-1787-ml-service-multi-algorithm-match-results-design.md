# Identification reliability and multi-algorithm imports (#1787)

This design replaces the large planning/review history in #1788. The work lands as
three stacked PRs: WBIA registration, intake/retry reliability, then multi-algorithm
imports and result/status selection. Merge in that order. Deploy the first two
together so a failed registration remains pending while its send is retried.

## 4.0 Identification run identity

Each invocation has one umbrella whose id is `identificationInvocationId` on every
member task. An `identificationOption` marker identifies option tasks; fan-out leaves
retain the run id but drop that marker. Parameter copies across request/detection
boundaries strip old run metadata. Request roots belonging to a prior run are replaced
before their parameters are overwritten, and `/ia` returns the replacement id. Legacy
identify requests and media-only roots keep their existing reuse behavior.

## 4.1 ml-service bulk-import matching

After extraction/gating, annotations are grouped by encounter and sent through the
shared intake, which runs every default option for each class. Annotations without
an encounter share an image-level group. Vector options fill missing method/version/
endpoint from the extraction match config, without changing configured values.
An explicitly vector option still lacking a usable method/endpoint becomes a terminal
`INVALID_MATCH_CONFIG` option; it is never published as a WBIA job.

The intake commits its tree before dispatch; the processor then checks the group
commit before proceeding. Recovery leaves successfully published WBIA options alone,
marks unpublished work on durable failed groups, and creates terminal error umbrellas
for groups that never acquired durable tasks. Scalar annotation ids and inherited
parameters are retained before dispatch. If loading/grouping itself fails, an error
umbrella covers the input annotations. This lets the import summary finish with
errors instead of polling annotations with no task forever. Recovery itself requires
a successful database commit; failures log the affected task ids.

## 4.2 Shared intake persistence and dispatch

**4.2.1 Entry points.** `intakeAnnotationsUnder` links the parent before persistence
and returns an outcome; its optional vector defaults apply only to ml-service calls.
Existing `intakeAnnotations` callers receive the umbrella, or null on persistence failure.

**4.2.2 Markers.** Run and option markers follow §4.0. Vector option tasks additionally
carry `mlServiceV2Match`; their leaves inherit it for metrics.

**4.2.3 Build, persist, dispatch.** The shared intake builds its entire option tree and links an optional parent before
a checked database commit. A failed commit publishes nothing and returns a failure
outcome; existing callers guard its null task result. After persistence, the intake
checks that the transaction restarted and remains active before each dispatch.
Vector dispatch runs inline; WBIA publication records whether the queue accepted
its job. Queue failures and unfinished vector leaves receive terminal errors.

An encounter/class with no default options receives a persisted
`INVALID_MATCH_CONFIG` task rather than an indefinitely pending task. Explicit
algorithm overrides still replace defaults. Fastlane parameters are stored on the
option tasks and routed by the existing lane classifier.

This layer leaves ml-service bulk matching, run identifiers, root replacement,
result selection, and import-status aggregation for the feature layer. Database
tests verify that both dispatch targets can read the committed tree from a second
manager and that a failed checked commit leaves no tree and publishes nothing.

### 4.2.4 WBIA send retries

A failed send stores nonterminal `requeued` before deciding whether to retry.
`SEND_FAILED` becomes terminal only when the retry budget refuses another attempt.
Existing import summaries count only completed/error tasks as finished, so this
state remains "sent" while work is queued. The existing seven-day watchdog still
reports work stuck beyond its retry window.

### 4.2.5 WBIA registration

Pending ml-service annotations (`wbiaRegistered == false`) register under their
existing `acmId`, falling back to the annotation id only when blank. Presence checks,
forced requests, and acknowledgement validation use that same identifier. Images
register under the media asset's `acmId` first. The background registrar and identify
path share eligibility checks. Legacy null/true flags retain their current paths.

A failed query registration fails the identify attempt. An ineligible target is
removed from that attempt; if every original target is removed, the attempt fails.
Successful flags and identifiers are persisted by the caller's transaction.
Missing pending annotations are forced under the same id before a bounded retry.

Validation includes unit tests and PostgreSQL tests with actual attached objects,
commits, reloads, real registration request construction, and simulated WBIA replies.

## 4.3 Match Results selection

New trees select the newest eligible run umbrella for the encounter, using umbrella
creation time rather than late leaf times. Existing trees use bounded structural
climbing with cycle checks, encounter boundaries, and import-stamp isolation. A task
from a different import never wins. Existing single-option links can change task id
while showing the same results. No data migration or resend is required.

## 4.4 Import identification status

The aggregate uses one representative for each option of the selected run that holds
the annotation. Pending work wins over error, then completed. Childless failed runs
use their own error status. The aggregate reports seven-day-old stuck work as error
without rewriting its status; existing per-task tallies retain their watchdog.
Metrics recognize vector leaves from New Match and resend as well as bulk imports.

## 4.5 Call sites

Encounter and annotation selectors supply the encounter's full annotation-id set.
Import summaries resolve those sets in bulk and use the same selector; a failed lookup
falls back to the existing unscoped behavior. See §4.3 for import-stamp boundaries.

## 5.3 Staging checks still required

- Import a species with default MiewID and HotSpotter options on two annotation classes.
- Resend and New Match from both the encounter page and menu; inspect both algorithms.
- Use a two-encounter image and confirm result links stay within encounter boundaries.
- Exercise a legacy `/ia` identify request and confirm the returned task id is usable.
- Simulate WBIA being down, queue publication failure, and a group commit failure.
  Imports must remain pending only while actual work is queued, then finish with errors.
- Inspect a childless error run's Match Results page.
- Register pending ml-service annotations concurrently and confirm no duplicate WBIA uuids.
- Restart Tomcat with queued work and inspect retry recovery and the `requeued` display.
- Check the `/metrics` vector gauge after New Match and resend.
- Audit deployed IA.json defaults: class-specific options now govern bulk matching;
  an install without WBIA must not retain an unintended default HotSpotter option.

## 5.4 Mantamatcher check still required

Import `26e2c50c` should show both algorithms without a resend. These live checks are
not claimed by the unit/PostgreSQL test suite and must be completed before deployment.
