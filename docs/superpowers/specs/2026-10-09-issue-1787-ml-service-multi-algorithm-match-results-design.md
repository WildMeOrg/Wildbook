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
