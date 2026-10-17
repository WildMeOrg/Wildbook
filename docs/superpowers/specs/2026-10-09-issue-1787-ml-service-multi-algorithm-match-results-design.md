# Identification reliability and multi-algorithm imports (#1787)

This design replaces the large planning/review history in #1788. The work lands as
three stacked PRs: WBIA registration, intake/retry reliability, then multi-algorithm
imports and result/status selection. Merge in that order. Deploy the first two
together so a failed registration remains pending while its send is retried.

## 4.2.5 WBIA registration

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

## 4.2 Shared intake persistence and dispatch

The shared intake builds its entire option tree and links an optional parent before
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

## 4.4 WBIA send retries

A failed send stores nonterminal `requeued` before deciding whether to retry.
`SEND_FAILED` becomes terminal only when the retry budget refuses another attempt.
Existing import summaries count only completed/error tasks as finished, so this
state remains "sent" while work is queued. The existing seven-day watchdog still
reports work stuck beyond its retry window.
