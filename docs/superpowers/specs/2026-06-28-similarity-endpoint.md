# Token Similarity (k-NN) Endpoint — Design

**Date:** 2026-06-28
**Branch:** `feature/similarity-endpoint`
**Driver:** Live API feedback (MMF/Sharkbook) — the **critical** scaling bottleneck. To find matches an
agent must download every catalog annotation's 2,152-float vector (≈0.5–0.9 s/annotation; a 200-record
page ≈176 s; >10k unwalkable). Wildbook already does OpenSearch kNN internally for matching
(`Annotation.getMatchQuery`, nested `knn` on `embeddings.vector` filtered by method/version). Surface
that as a read-only token endpoint so the agent gets nearest neighbors **without downloading vectors**.

## Endpoint
`POST /api/v3/similarity` — token-auth (same `tokenAuthSearch` Shiro filter as search), read-only. A new
path (not under `/api/v3/search/*`, which the SearchApi servlet owns).

### Request
```json
{ "annotationId": "<uuid>", "k": 50, "methodVersion": "msv4.1",
  "taxonomy": "Equus quagga", "locationId": "Mbuluzi Game Reserve", "excludeSameEncounter": true }
```
- `annotationId` (**required**) — the query annotation. The server reads ITS stored embedding; the
  caller never sends a vector (v1 has no raw-vector input — avoids untrusted vectors and is the whole
  point: no vector download).
- `k` (optional, default 50, **max 100**) — number of neighbors.
- `method` (optional, default `miewid`) / `methodVersion` (optional) — which embedding to compare. If
  omitted and the annotation has exactly one (method,version) embedding, use it; if it has several and
  `methodVersion` is omitted → 400 (ambiguous).
- `taxonomy` (optional) / `locationId` (optional) — explicit scope filters, applied as exact term
  filters on `encounterTaxonomy` / `encounterLocationId` inside the candidate filter. **v1 uses these
  explicit, safe scope params rather than an arbitrary caller `query`** — this avoids needing to
  validate a free-form ranking-oracle filter (deferred to a future version with a strict allow-list).
- `excludeSameEncounter` (optional, default false) — also drop candidates sharing the query's
  `encounterId` (the skills' anti-inflation rule).

### Behavior
1. Resolve the query annotation. If the caller can't see it (ACL) or it doesn't exist → **404**, the
   two indistinguishable (no existence oracle, mirroring media/resolve).
2. Get its embedding for (method, methodVersion). None → **400** `"no embedding for that
   method/version"`.
3. Build the nested `knn` query (vector + `k`) filtered by `embeddings.method`/`methodVersion`
   (reuse the `getMatchQuery` construction).
4. **Enforce correctness server-side:** candidates are constrained to the **same `viewpoint`** as the
   query annotation (skip this constraint only if the query's viewpoint is null), and the same
   method/version (already in the knn filter). Always **exclude the query annotation itself**; if
   `excludeSameEncounter`, also exclude its `encounterId`.
5. **ACL-scope candidates:** wrap the candidate query with `applyAclFilter` for non-admin (admins
   unfiltered), so neighbors are only annotations the caller may see.
6. Merge the optional caller `query` as an additional `filter`.
7. Execute via `queryPit` with size = k; return the top-k.

### Response
```json
{ "query": { "annotationId": "...", "viewpoint": "left", "method": "miewid", "methodVersion": "msv4.1" },
  "neighbors": [ { "id": "...", "score": 0.87, "encounterId": "...", "individualId": "...",
                   "viewpoint": "left", "iaClass": "zebra", "methodVersion": ["msv4.1"] }, ... ] }
```
- Each neighbor is the **sanitized** annotation doc (ACL-scrubbed, like search hits) **with the
  `embeddings` field stripped** + a `score` (the OpenSearch kNN cosine similarity). Stripping
  `embeddings` is essential: it keeps the payload tiny (the whole point) and never returns vectors.
- `score` lets the agent threshold/rank (calibrate per the skill guidance).

## Security
- Token-auth, read-only; candidates ACL-scoped via `applyAclFilter`; query annotation must be visible
  (else 404, no oracle).
- **Never returns vectors** (embeddings stripped from neighbor `_source`).
- `k` bounded (≤100); optional `query` validated (no scripts/aggs/runtime_mappings); same-viewpoint +
  same-method/version enforced (prevents cross-latent-space comparison the skills warn against).
- Self (and optionally same-encounter) excluded so the top hit isn't the query itself.

## Metrics (REQUIRED — this is user-run re-ID compute on our servers)
A similarity call is a re-identification query executed on our OpenSearch. It creates **no `ia.Task`**,
so the existing identification gauges (`MetricsBot` JDOQL counts of `Task` rows with
`ibeis.identification`/`pipeline_root`/`graph`) will NOT see it. Add a dedicated metric so this re-ID
channel is visible in `/metrics` and impact reporting:
- A Prometheus **Counter** on `io.prometheus.client.CollectorRegistry.defaultRegistry` (the same
  registry `MetricsResource`/`WildbookMetrics` expose), e.g. `wildbook_token_reid_queries_total`
  (label: `context`), incremented once per successfully-served similarity query. Optionally also a
  neighbors-returned counter and a latency histogram.
- Keep it **distinct** from the pipeline `wildbook_identification_tasks*` gauges — token-API re-ID is a
  separate, synchronous, agent-driven modality; conflating the two corrupts identification counts (cf.
  the v2-detection-gauge pollution incident). Increment only on a real query that ran the kNN (not on
  400/404/validation rejections).
- Counter resets on restart (standard Prometheus semantics; the scrape/TSDB accumulates). If a durable
  all-time total is also wanted, additionally persist a running total (e.g. a `SystemValue`) — optional.

## Reuse / files
- New servlet `org.ecocean.api.SimilarityApi extends ApiBase` (`doPost`); web.xml servlet + url-pattern
  `/api/v3/similarity`; Shiro `[urls]` `/api/v3/similarity = tokenAuthSearch`.
- New `OpenSearch.knnQuery(...)` (or reuse `Annotation.getMatchQuery` building blocks) to assemble the
  nested-knn + filters; reuse `applyAclFilter`, `queryPit`, `sanitizeDoc`.
- Strip `embeddings` from each returned `_source` before `sanitizeDoc`/output.

## Out of scope (future)
- Raw-vector input; cross-viewpoint search; paging beyond top-k; multi-annotation batch; returning the
  query vector. A `/api/v3/agent-skill/find-missed-matches` rewrite to use this endpoint (huge speedup)
  is a follow-up doc PR once this lands.

## Design review (BINDING — incorporated)

**PREREQUISITE — RESOLVED (verified empirically on zebra.wildme.org 2026-06-28).** Through the existing
`/api/v3/search/annotation` (which forwards the body to OpenSearch), a nested `knn` with an inner
`filter` is accepted (HTTP 200) and the filter is **honored**: filtering to the opposite `viewpoint`
returned 10 `left` neighbors instead of the natural `right` ones, and the filter worked on BOTH a nested
subfield (`embeddings.methodVersion`) and a PARENT field (`viewpoint`). So the deployed engine supports
efficient filtered nested kNN; build on native `knn.filter` (parent + nested filters), no mapping
migration needed. **Heavy-compute note:** kNN is the most expensive token operation — it must be behind
the token-API concurrency guard (see the separate request-isolation workstream) so it cannot starve
interactive Wildbook search/matching.

(Original prerequisite text, now satisfied:) verify the deployed engine supports efficient *filtered
nested* kNN. The
`embeddings.vector` mapping is `knn_vector` with **no `method.engine`** specified, so filtered-kNN
support is engine/version-dependent and unconfirmed. The endpoint MUST NOT ship on a global-ANN +
outer-`bool.filter` (post-filter) basis — that returns fewer than `k`, lets hidden/out-of-scope vectors
suppress visible neighbors, and is a recall/leak hazard. Resolve one of:
  (a) confirm the live OpenSearch honors `knn.filter` on the nested `embeddings.vector` (empirically:
      POST a `knn`+`filter` body through the existing `/api/v3/search/annotation` and check it filters);
  (b) migrate the mapping to a filter-capable engine (Lucene) + reindex;
  (c) fall back to exact filtered scoring or a flattened embedding-row index.
Do not implement until (a) or (b) holds.

**Corrected query shape:** put ACL, viewpoint, method/version, caller scope, and self/same-encounter
exclusion **inside the native `knn.filter`** (the efficient filtered-ANN path), NOT only an outer bool.
Keep an outer ACL `bool.filter` as defense-in-depth only.

**Vector non-leak (defense in depth):** set `_source.excludes:["embeddings"]` on the request, reject any
caller `_source`/`fields`/`docvalue_fields`, AND `remove("embeddings")` from each doc before output.

**Query-annotation visibility gating (no-oracle):** mirror `MediaResolveApi` exactly — token context
only; parse first; for a non-admin run the singleton id-eligibility ACL query (fresh PIT) to confirm
visibility BEFORE any DB/vector load; not-visible and not-found return an identical **404** (never a
distinct "no embedding"/timing signal for hidden ids).

**Caller `query` is now a ranking oracle → strict validation** (stricter than annotation search): allow
filter-only DSL on a **field allow-list**; reject scripts, aggs, runtime_mappings, `_source`, `fields`,
sort, post_filter, rescore, `nested`/`embeddings`/`vector`, ACL fields, terms-lookup, and overly
large/deep clauses (extend the existing `DENY_FEATURES`/`nodeAllowed` deny-list).

**Deterministic method resolution:** do NOT reuse `getEmbeddingByMethod(null,null)` (returns first of a
set). Require exactly one eligible `(method, methodVersion)` after defaults (method=miewid); else 400.

**Viewpoint-null → 400** (don't silently broaden latent space). **k:** default 50, range 1–100, else 400.

**Cost:** fresh PIT; reject huge/deep caller filters; note token/IP rate-limiting as a platform follow-up.

## Open questions (RESOLVED by review)
- Path `/api/v3/similarity` (not under `/api/v3/search/*`). • 404 for not-visible/not-found query
  annotation. • Always the query annotation's viewpoint (no override) in v1; null → 400. • k default 50,
  max 100, out-of-range → 400. • Do NOT rely on outer `bool.filter`; use native `knn.filter`.

## Original open questions (superseded by the resolutions above)
1. Endpoint path/name: `/api/v3/similarity` vs `/api/v3/search/similar` (the latter collides with the
   SearchApi `/api/v3/search/*` mapping → avoid). Confirm `/api/v3/similarity`.
2. Not-visible/not-found query annotation: 404 (chosen) vs 200-empty — which better preserves no-oracle
   while being ergonomic?
3. Should same-viewpoint enforcement be overridable via an explicit `viewpoint` param, or always the
   query's viewpoint? (Lean: always the query's in v1; overridable later.)
4. `k` max (100 chosen) and default (50) — reasonable for re-ID shortlists?
5. Does the OpenSearch nested-knn query honor an outer `bool.filter` (ACL + viewpoint + scope) so the
   ANN search is restricted to the filtered candidate set (efficient + correct), or does kNN run global
   then post-filter? (Implementation must confirm the filter applies to the ANN candidate set.)
