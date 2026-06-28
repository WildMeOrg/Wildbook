# Token-API Concurrency Guard — Design

**Date:** 2026-06-28
**Branch:** `feature/similarity-endpoint` (ships under PR5, ahead of/with the kNN endpoint)
**Driver:** Operator requirement — a remote agent may make a *massive volume* of token-API calls (that's
fine), but it **must not interfere with interactive Wildbook use**. Token search/kNN hit the SAME
OpenSearch the live UI and the live re-ID matching pipeline use; one sequential agent already triggered
intermittent `500`s under sustained paging. kNN (PR5) is the heaviest op. We need **resource isolation**,
not a volume quota.

## Approach: bound *concurrency*, not volume
A per-instance (per-JVM) **semaphore** caps how many token-API requests execute *simultaneously*. The
agent may send unlimited requests; only N run at once; the rest get **`429 Too Many Requests` +
`Retry-After`** and back off. This bounds the agent's instantaneous footprint on OpenSearch/Tomcat,
leaving headroom for live users, without limiting total work (the agent self-paces). Volume is fine;
crowding-out is prevented.

### Two pools
- **GENERAL** — every *token* search / `media/resolve` / aggregation request acquires one permit.
- **SIMILARITY** — the kNN `/api/v3/similarity` endpoint acquires from a **separate, smaller** pool
  (kNN is CPU-heaviest). Independent pools (one acquire per request) — no double-acquire, no deadlock.
Defaults (per instance, tunable): `GENERAL = 16`, `SIMILARITY = 4`, `Retry-After = 5s`. Read via the
existing `getConfigurationValue(key, default)` mechanism (e.g. `tokenApiMaxConcurrent`,
`tokenApiMaxConcurrentSimilarity`, `tokenApiRetryAfterSeconds`); fall back to defaults.

### Only token requests are throttled
The guard applies **only when the request is token-authenticated** (`TOKEN_AUTH_ATTR`). Interactive
session/UI traffic (the live users we're protecting) is never throttled. Non-token requests don't
consume a permit.

### Mechanism: in-servlet, not a filter
Each token-serving servlet acquires/releases around its work (robust, testable, no Shiro-ordering
dependence):
```
if (tokenAuth) {
    if (!TokenApiConcurrency.tryAcquire(kind)) { write 429 + Retry-After; return; }
    try { ...handle... } finally { TokenApiConcurrency.release(kind); }
}
```
- `tryAcquire` is **non-blocking** (immediate) — never hold a Tomcat thread waiting (waiting threads are
  themselves a way to starve the pool). Acquire as late as possible but before the expensive
  OpenSearch/DB work (`SearchApi`: before `queryPit`; `MediaResolveApi`: before the resolve loop;
  `SimilarityApi`: at entry, before the kNN). Release in `finally` on every path (success, 4xx, 5xx,
  exception) so permits never leak.
- Wiring: `SearchApi` + `MediaResolveApi` → `Kind.GENERAL`; `SimilarityApi` → `Kind.SIMILARITY`.

### 429 response
`HTTP 429`, header `Retry-After: <seconds>`, body `{"error":"token API busy, retry after N seconds"}`.
No `_source`/data. Distinct from `403`/`401`/`400`.

### Metrics (ties into the re-ID metrics work)
On the Prometheus `defaultRegistry`:
- `wildbook_token_api_inflight{pool=}` — gauge of permits currently held (inc on acquire, dec on
  release).
- `wildbook_token_api_rejected_total{pool=}` — counter of `429`s.
This makes saturation observable (ops can see when agents are hitting the cap and tune the budget).

## Security / correctness
- Permits released in `finally` (no leak on exceptions/early returns).
- Per-JVM semaphore: in a fleet, each instance self-protects (load-balancer spreads load; per-instance
  caps still bound each node's contribution to the shared OpenSearch). A distributed limiter is overkill.
- Fail-open vs fail-closed on guard error: the guard itself must never 500 a request; if anything in the
  guard throws, proceed (fail-open) — the guard is a safety valve, not an auth gate.
- Does not change auth/ACL; orthogonal to the per-request cost ceilings (10k window, k≤100, etc.) which
  remain and bound each permit's cost.

## Out of scope (documented)
- Distributed/cluster-wide limiting; a dedicated OpenSearch read replica / lower-priority search pool
  (the stronger infra isolation) — recommended as a platform follow-up, not in this PR.
- Per-token / per-IP fairness (one noisy token vs many) — v1 is a global per-instance cap; per-token
  fairness can come later if needed.
- Short-wait acquire (vs instant 429) — start instant; revisit if 429 churn is high.

## Testing
- `TokenApiConcurrency`: acquire up to N succeeds; the N+1th fails; release restores a permit; pools are
  independent (exhausting SIMILARITY doesn't block GENERAL). Config override changes sizes; bad config →
  defaults. Metrics inflight inc/dec and rejected increments.
- Servlet-level: with the pool exhausted (acquire all permits in the test), a token request returns
  `429` + `Retry-After` and does NOT call `queryPit`/resolve; a session (non-token) request is NOT
  throttled even when the pool is exhausted; permit released after a normal request (next succeeds);
  permit released after an exception path.

## Design review — incorporated (BINDING)

- **Scope honesty:** this is *bounded concurrency* for OpenSearch isolation, not absolute isolation.
  It does not protect Tomcat threads / Postgres / body-parsing from a hostile flood (rejected token
  requests still parse + do user lookup before the 429). Document a WAF/edge rate-limit + an OpenSearch
  `thread_pool`/search-backpressure layer as the complementary platform follow-ups.
- **AutoCloseable permit handle (no leak / no over-release):** `tryAcquire(kind)` returns a `Permit`
  (`AutoCloseable`, or null if not acquired). Use `try (Permit p = …) { … }`; never call bare
  `Semaphore.release()` (over-releases if acquire failed). Handles `MediaResolveApi`'s IOException
  rethrow safely.
- **Acquire placement (cover ALL OpenSearch work, skip 4xx gates):**
  - `SearchApi`: acquire AFTER the individual-token 400 gate, BEFORE `new OpenSearch()`/`deletePit`/
    `queryPit` (so 401/403/404/405/400/agg-400 never consume a permit, but the whole OpenSearch path is
    covered).
  - `MediaResolveApi`: acquire AFTER token/context/body/`currentUser`/`isAdmin`, BEFORE computing
    `visible` (which itself calls `gatedVisibleIds` → `deletePit`+`queryPit`).
  - `SimilarityApi`: at entry to the kNN execution.
- **Lower defaults** (one sequential agent already caused 500s): `GENERAL = 8`, `SIMILARITY = 2`
  (configurable). Note the budgets are **additive** (≤10 concurrent token OpenSearch ops/instance).
  Clamp invalid config to ≥1; **restart required** to resize; these are node/webapp-wide
  `OpenSearch.properties` settings read once via `getConfigurationValue`.
- **Instant 429** (no wait); GENERAL pool also covers aggregation requests in v1 (their per-request cost
  is already capped: terms-only, size≤1000).
- **Idempotent metrics registration:** register the gauge/counter once (guard against duplicate
  registration across redeploys/multiple webapp contexts — e.g. catch the already-registered case or use
  a singleton holder). Update the inflight gauge only through the permit lifecycle. Low-cardinality
  labels (`pool` only).

## Related interference vector — shared PIT cache (IMPORTANT, partly addressed here)
`OpenSearch.PIT_CACHE` is a **static `HashMap` keyed by index**, shared by token AND interactive/UI
search. Each token request calls `deletePit(indexName)`, which deletes the cached point-in-time the UI
may be mid-search on — so a single token call can disrupt a live search, independent of concurrency. The
unsynchronized HashMap is also a cross-thread hazard.
- **In this PR:** the new `SimilarityApi` uses a **request-scoped PIT** (create → use → delete its own
  id; never touches `PIT_CACHE`), so the heaviest new endpoint adds no shared-PIT contention.
- **Follow-up PR (recommended, possibly urgent — affects the already-built token search/`media/resolve`):**
  move those token paths to request-scoped PITs too (and/or make `PIT_CACHE` concurrency-safe). Done as a
  focused PR because it touches shared search infra used by the live UI. The concurrency guard only
  *bounds* how many token requests churn the shared PIT at once; it does not stop a single one from
  deleting a UI PIT.

## Open questions (for review)
1. Defaults: `GENERAL=16`, `SIMILARITY=4` per instance — reasonable starting headroom, or derive from
   CPU count? (Lean: fixed configurable defaults; ops tune per instance size.)
2. Instant `429` vs a short bounded wait (e.g. 100ms) before rejecting — which better avoids 429 churn
   without risking thread pile-up? (Lean: instant.)
3. Should aggregation requests share the GENERAL pool or get their own (they can be heavier than a plain
   term search)? (Lean: GENERAL for v1; split if needed.)
4. Per-JVM semaphore acceptable given the fleet, or is per-token/per-IP fairness needed in v1?
