# Token Search Aggregations — Design

**Date:** 2026-06-28
**Branch:** `feature/search-aggregations`
**Driver:** Live API feedback (MMF/Sharkbook) — per-location counts required ~190 separate queries; "one
server-side terms aggregation on `encounterLocationId` would replace the entire loop." Also: aggregations
currently **return empty rather than erroring**, which silently misleads callers.

## Problem

`POST /api/v3/search/{index}` forwards the full query body (including `aggs`) to OpenSearch via
`queryPit`, so OpenSearch *computes* aggregations — but `SearchApi` only reads `queryRes.hits` and never
surfaces `queryRes.aggregations`. Result: aggregations silently come back empty.

Naively surfacing `aggregations` is unsafe. The token API is a security boundary. Risks:
1. **Value leak** — a `terms` aggregation's bucket keys expose field *values*. A terms agg on a hidden
   field (e.g. `viewUsers`, `submitterUserIds`, `gpsLatitude`) would leak data the caller can't see in
   `_source`.
2. **Count leak** — bucket `doc_count`s could reveal counts of records the caller can't access.
3. **Cost** — unbounded bucket counts / `cardinality` / scripted aggs are expensive and a DoS vector.

## Goal

Enable **bounded, ACL-safe `terms` aggregations on an allow-list of non-sensitive fields**, and make any
other aggregation request an **explicit 400** (never silent empty).

## Design

### Accepted shape (everything else rejected)
A request may include a top-level `aggs` (or `aggregations`) object. It is accepted only if **every**
named aggregation is:
- type **`terms`** only (no `cardinality`, `date_histogram`, `script`, metric aggs, etc.);
- on a `field` in the per-index **agg allow-list** (below);
- with optional integer `size` in `[1, AGG_MAX_BUCKETS=1000]` (reject `>max`; if omitted, OpenSearch's
  own terms default applies — pass an explicit `size` to widen);
- with **no sub-aggregations** (no nested `aggs` inside a bucket) and **no `script`**.

Any aggregation that is present but violates the above → **HTTP 400** with a specific message
(e.g. `"aggregation not allowed: only terms on {allowed fields}, size<=1000, no sub-aggs/scripts"`).
This replaces today's silent-empty behavior.

### Per-index agg allow-list (only fields CONFIRMED explicitly keyword-mapped → terms-aggregatable)
Verified against `opensearchMapping()`; a dynamically-mapped string becomes `text` (with a `.keyword`
sub-field) and is NOT directly terms-aggregatable, so such fields are excluded.
- **encounter**: `locationId`, `taxonomy`, `country`, `lifeStage` (all explicit keyword). *Excluded:*
  `sex`, `livingStatus` (not explicitly mapped → dynamic `text`).
- **annotation**: `viewpoint`, `iaClass`, `encounterLocationId`, `encounterTaxonomy` (all explicit
  keyword). *Excluded:* `matchAgainst` (relies on dynamic boolean mapping; not requested — drop to stay
  strictly within confirmed-keyword fields).
- **individual**: `taxonomy`, `sex` (both explicit keyword). For non-admin, individual aggregations are
  additionally blocked by the existing identity-field gate. Never include identity/PII or ACL fields.

The allow-list is a hard deny-by-default; no field outside it is ever aggregatable. ACL field names
(`viewUsers`, `editUsers`, `publiclyReadable`, `submitterUserId(s)`) are never on it.

### ACL scoping (the safety hinge)
For a non-admin token, `applyAclFilter` already wraps the request's `query` clause in a bool whose filter
enforces the encounter ACL. OpenSearch aggregations run over the **query-matched** documents, so the
aggregation is automatically scoped to ACL-visible records — **provided** `applyAclFilter` continues to
wrap only the `query` (leaving `aggs` top-level) and runs BEFORE execution. Validation of the agg shape
runs first (reject early), then `applyAclFilter`, then execute. Admins are unfiltered (as today).

### Response
When a valid aggregation ran, surface it: `res.put("aggregations", queryRes.optJSONObject("aggregations"))`.
Hits are still returned per the caller's `?from=/?size=` URL params (for counts only, pass `?size=0`).
`X-Wildbook-Total-Hits` continues to reflect the (ACL-scoped) match count.

### Order of operations in SearchApi (token path)
1. Resolve index (existing allow-list).
2. Parse body.
3. **Validate aggregations** (new): if `aggs`/`aggregations` present, enforce the accepted shape → 400 on violation.
4. Existing non-admin individual identity-field gate (already 400s any `aggs` on individual for non-admin).
5. `applyAclFilter` for non-admin.
6. Execute via `queryPit`; surface `hits` + (new) `aggregations`.

## Out of scope (future)
- Nested/multi-level aggregations; metric aggs (`avg`/`sum`); `date_histogram` (useful for temporal
  rollups — candidate next); `cardinality` (count-distinct; deferred — leak/cost review needed).
- A dedicated `/aggregate` endpoint; for now it rides on `search`.

## Testing
- Valid `terms` on an allowed field → 200, `aggregations` present with buckets; hits honor `size`.
- `terms` on a **non-allowed** field (e.g. `viewUsers`, `gpsLatitude`) → **400** (value-leak guard).
- Non-`terms` agg (`cardinality`, `date_histogram`, scripted) → 400.
- Sub-aggregation present → 400.
- `size` over max → 400 (or clamp — open question).
- Non-admin: aggregation runs ACL-filtered — buckets/counts reflect only visible docs (mock OpenSearch
  to assert the agg request rode alongside the ACL-filtered query; and that `applyAclFilter` wrapped
  `query` while leaving `aggs` intact).
- Non-admin **individual** with `aggs` → still 400 via the existing identity gate.
- No-agg request → unchanged behavior.

## Resolved open questions (design review)
1. **Size over max → reject with 400, do NOT clamp.** This is a security boundary; silent mutation is
   harder to reason about.
2. **Uniform validator for admin too.** Admin token is still the token boundary; one code path. (A
   broader admin field list can come later.)
3. **`matchAgainst` excluded** — it relies on dynamic boolean mapping (not an explicit mapping);
   dropped to stay strictly within confirmed-keyword fields. Revisit if it is ever explicitly mapped.

## Hardening from design review (BINDING — validator must enforce all)

The basic ACL approach is sound (a direct top-level `terms` runs over the ACL-filtered query). The risk
is validator gaps. The validator is **deny-by-default** and must:

**A. Agg-bearing request → strict whole-body top-level allow-list.** When `aggs`/`aggregations` is
present, the body's top-level keys must be a subset of `{ query, aggs | aggregations }`. Pagination is
via the `?from=/?size=` URL params (execution ignores body `from`/`size`), so body `from`/`size` are
**rejected** on an agg request rather than silently dropped; for counts-only pass `?size=0` on the URL.
Reject (400) any other top-level key — especially **`runtime_mappings`** (can redefine/script an
allow-listed field), and also `post_filter`, `script_fields`, `fields`, `_source`, body `sort`,
`collapse`, `rescore`, `suggest`, caller `pit`, and any unknown key. Reject if BOTH `aggs` and
`aggregations` are present (ambiguous).

**B. Each named aggregation must be EXACTLY one direct `terms`.** The agg object's keys must be exactly
`{"terms"}` — no sibling `aggs`/`aggregations` (sub-aggregations), and no other agg type. Explicitly
reject `global`, `filter`, `filters`, `nested`, `reverse_nested`, `children`, `composite`, pipeline aggs
(`bucket_*`), and any metric/`script`/`date_histogram`/`cardinality`/`multi_terms`. Do not recursively
search for a valid terms agg — validate the exact shape.

**C. `terms` params → exact allow-list `{ field, size }` only.** `field` (required, String) must be an
**exact** member of the per-index allow-list (no `.keyword` suffixes, no root-field/prefix matching).
Optional `size` must be an integer in `[1, AGG_MAX_BUCKETS=1000]` (>max → 400). Reject every other terms
param: `script`, `missing`, `include`, `exclude`, `order`, `shard_size`, `min_doc_count`,
`execution_hint`, `value_type`, `collect_mode`, `meta`, etc.

**D. Validate BEFORE persisting.** Run the validator on the parsed direct-POST body **before**
`OpenSearch.queryStore`, so invalid agg bodies are never stored.

**E. Scope of the feature.** Validation + surfacing apply on the **tokenAuth path only** (admin: full
access; non-admin: `applyAclFilter` already wraps `query`, so aggs are ACL-scoped). Non-token callers
keep today's behavior (aggs not surfaced) — out of scope. Stored-query replay drops aggs in
`queryScrubStored` (keeps only `query`), so replay can't reintroduce an agg post-validation.

**F. Accepted residual (documented).** An agg-bearing query may still filter its `query` clause on
arbitrary fields, turning a hidden-field predicate into grouped counts — but only over **ACL-visible**
docs (the agg never sees docs the filter+ACL exclude), i.e. no wider than what `hits` already expose.
This is pre-existing token-search query freedom; not widened here.

**Per-field mapping note:** every allow-listed field is a keyword/boolean with doc-values in the
index mapping (terms-aggregatable). Confirm against `opensearchMapping()` during implementation.
