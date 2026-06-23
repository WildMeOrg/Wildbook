# Artifact D — Token-Authenticated Scoped Search (Wildbook, Java)

**Date:** 2026-06-06
**Status:** Draft — incorporated **two** Codex design-review rounds (2026-06-06, verified against real `SearchApi`/`Shiro`/`ShepherdRealm`/`OpenSearch`/`Encounter`). Round 2 verdict was FIX-FIRST on one blocking item (encounter-only index gating for direct + stored-query token paths) — now folded into components 4/7. Pending user review.
**Repo/branch:** Wildbook, `token-auth-scoped-search` (off the A+B integration base; reuses B's `JwtService`).
**Depends on:** Artifact A (merged, PR #1592 — hardened `viewUsers`) and Artifact B (PR #1594 — `JwtService` + `AuthToken`). D reuses `JwtService` for verification and Wildbook's existing enforced search for filtering.

## Purpose

Make **Java the hard permission boundary** for user-facing scoped queries. An external client (Claude / an MCP shim) presents a B-issued bearer JWT to Wildbook's encounter search; Wildbook verifies the token, resolves the user, and runs a **permission-filtered** search. The external side never reads OpenSearch directly, so a buggy/compromised external client cannot exceed what the user's roles/collaborations/org-memberships allow.

> **Codex Critical — the premise corrected.** Wildbook's `SearchApi` does NOT pre-filter: it runs the caller's query, reads the **raw `total`**, and only *post-sanitizes* hits — inaccessible encounters are still returned as `access:"none"` **carrying ids, dates, location, taxonomy, assigned username**, and are counted in totals/pagination (`OpenSearch.querySanitize` is a no-op). So D cannot just "reuse the enforced search" — on the token path it must **inject a real ACL pre-filter into the query before execution**. (That pre-existing session-search metadata leak is real but out of D's scope — flagged as a separate follow-up; we chose to fix only the token path here.)

This supersedes the Python Scoped-Query Kernel's user-facing scoped-read; the kernel's pre-filter logic now lives authoritatively in Java. See the kernel-retirement note below.

## Scope

**In:** read-only, **encounter search only**. The token filter is wired ONLY onto `SearchApi` at `/api/v3/search/encounter` (and the `encounter` index). A valid token authenticates the user for that read only.

**Out (deferred):** any write/edit/merge (the filter is not on the `BaseObject` paths); `individual`/`occurrence`/`annotation` search via token (Codex: `individual` is returned unsanitized, `occurrence` access is weaker/custom, `annotation` is admin-only — each needs its own review before token exposure); write/agentic actions; a long-lived revocable PAT (v1 accepts B's short-lived JWT — a PAT later either mints/exchanges into the same JWT or adds a verification path).

**Flagged follow-ups (NOT in D):**
- **Global SearchApi metadata leak** — the pre-existing session-path behavior (inaccessible encounters returned as `access:"none"` with ids/dates/location/taxonomy/username, plus unscoped totals) is a real data-exposure bug for *all* search, not just token. Its own security issue/PR (sibling to the `viewUsers` bug), with React-search-UI review — out of D's contained scope.
- **Verify orgAdmin coverage in `viewUsers`** — Codex flags the background permissions writer may not include orgAdmins consistently with `computeViewUsers` (A). D's pre-filter trusts `viewUsers`; confirm the background pass delegates to / matches `computeViewUsers` so orgAdmin-scoped search is correct.

## Components

1. **Token verification — reuse B's `JwtService`.** Wildbook holds the keypair, so `JwtService.fromConfig(context).verify(token)` checks RS256 (pinned), `iss`, `aud`, and `exp`. **D adds a context check**: the token's `context` claim must equal the server context (`context0`); reject otherwise. (B's `JwtService.verify` enforces iss/aud/alg/exp but not context — the consumer owns the context check; D is that consumer on the Wildbook side.)

2. **`WildbookTokenAuthenticationFilter`** (new — the only substantive new code). A Shiro filter on the encounter-search path that:
   - Reads `Authorization: Bearer <jwt>`.
   - **No Bearer** → do nothing, let the chain proceed (existing session/behaviour unchanged — the endpoints still work for browser sessions).
   - **Bearer present** → verify via `JwtService`; on failure (bad sig / expired / wrong iss-aud / wrong context) → **401**.
   - On success → resolve the user via `Shepherd.getUserByUUID(sub)`; if absent → 401. Wrap the request (component 3) so downstream `myShepherd.getUser(request)` / `request.getUserPrincipal()` return the token's user, mark the request token-authenticated (uuid + isAdmin), then forward.

3. **Establishing the principal — request wrapper, NOT `subject.login` (Codex Critical).** Do **not** call `subject.login(...)`: Wildbook is session-based (`JSESSIONID`), and a Shiro login on a bearer request can mint/persist a session whose principal could later satisfy *write* endpoints — defeating the read-only containment. Instead the filter wraps the request in an `HttpServletRequestWrapper` overriding `getUserPrincipal()`/`getRemoteUser()` to return the token user's **username**, and forwards. `SearchApi` resolves identity via `myShepherd.getUser(request)` → `request.getUserPrincipal().toString()` (`Shepherd.java:1199`), so the wrapper suffices and is **stateless** (no session, no Set-Cookie, no write reuse). Ensure the principal's `toString()` yields the username. (A Shiro-native `JwtRealm` is feasible but riskier here for exactly the session-persistence reason; deferred.)

4. **`web.xml` Shiro `[urls]` wiring + scope.** Apply the token filter to **`/api/v3/search/*`** (mapped to `SearchApi`, read-only), with v1 enabling/testing the **`encounter`** index only (index gating enforced in `SearchApi` — see component 7). Do **NOT** put it on the `BaseObject` `/api/v3/encounters/*` / `/api/v3/individuals/*` paths — those servlets handle `GET`+`POST`+`PATCH` on one mapping, so token auth there would expose writes (Codex High). Wire the search path to the **token filter only** — do NOT chain Shiro `authc`/`roles` after it (Codex: that would re-introduce a subject/session). The token filter is **optional**: a valid Bearer authenticates (request wrapped); an invalid/expired/bad-context Bearer → **401**; no Bearer → chain proceeds unchanged (session path intact; `SearchApi` already self-401s unauthenticated requests).
   - **Method allowlist, not "safe methods" (Codex Medium).** Encounter search is a `POST`, so a literal GET-only "safe method" gate would break it. The gate is an explicit allowlist: `POST /api/v3/search/encounter` and `GET /api/v3/search/{uuid}` (stored query); reject `PUT`/`PATCH`/`DELETE`.

5. **Context-drift rejection (Codex High).** The filter requires the token's `context` claim == `context0` AND rejects the request if the **resolved** request context is not `context0`. `ServletUtilities.getContext` resolves context from several sources — `?context=` query param, the `wildbookContext` cookie, host→context mapping, and the configured default (`ServletUtilities.java:764–807`) — so reject on *any* resolved non-`context0`, not just the query param, before user resolution. Tests must cover query-param, cookie, and host/default drift.

6. **Token-path ACL pre-filter (the real enforcement — Codex Critical).** `SearchApi` must, **when the request is token-authenticated and the user is non-admin**, inject an ACL clause into the OpenSearch query *before execution* so totals + pagination + hits are all scoped:
   ```json
   { "bool": { "should": [
       {"term": {"publiclyReadable": true}},
       {"term": {"submitterUserId": "<uuid>"}},
       {"term": {"viewUsers": "<uuid>"}}
     ], "minimum_should_match": 1 } }
   ```
   (Same predicate as `Encounter.opensearchAccess` — verified by Codex against `Encounter.java:4753–4764`: public OR submitter-UUID OR admin-bypass OR `viewUsers`; `editUsers` is **not** consulted and orgAdmin access is carried *through* `viewUsers`, so the three-term clause is complete. Same predicate as the now-retired Python kernel's `permission_filter` — moved authoritatively into Java.) **Admin** token users get no pre-filter (full access, matching `opensearchAccess`' admin bypass). The token filter marks the request (e.g. a `wildbook.tokenAuth` request attribute + resolved uuid/admin) so `SearchApi` knows to apply this on the token path only; the existing session path is untouched (its pre-existing post-sanitize leak is a separate follow-up).
   - **Exact injection point (Codex Medium).** The clause must wrap the **top-level OpenSearch `query`** in a `bool` (`must`: original query; `filter`: the ACL clause) **before `queryPit`/execution** (`OpenSearch.java:~918`, `SearchApi.java:~91`), so totals + pagination + hits are all scoped. Adding a sibling field, or filtering after results, would NOT scope totals. **Fail closed** on a malformed/unparseable query rather than executing it unscoped.
7. **Stored-query + index gating in `SearchApi` (Codex High + Medium).** On the token path, after the query is loaded (direct body or `/api/v3/search/{uuid}`) and before execution:
   - **Index gate:** require the resolved/target `indexName == "encounter"`, else 403. The Shiro filter cannot see a stored query's index from the `{uuid}` URL alone, so this MUST be enforced inside `SearchApi` for **both** the direct and stored-query paths — otherwise a stored query over `individual` (returned unsanitized) or `occurrence` (weakly sanitized) bypasses the encounter-only scope.
   - **Owner check:** for a stored-query UUID, require `creator == user.uuid` (or admin), else 403 — `SearchApi` otherwise replays any saved query.
   - Apply the component-6 ACL pre-filter after these gates pass.
8. **Fail-closed if a Bearer reaches `SearchApi` unmarked (Codex Medium).** Defence against filter misordering/misconfig: if an `Authorization: Bearer` header is present but the `wildbook.tokenAuth` request attribute is absent, `SearchApi` returns 401 (misconfiguration) rather than silently falling through to the session path.

## Data flow

```
Claude/MCP --Bearer JWT--> /api/v3/search/encounter (Wildbook)
  WildbookTokenAuthenticationFilter: verify JWT (RS256/iss/aud/exp/context) + reject ?context drift
    -> getUserByUUID(sub) -> wrap request (getUserPrincipal()=username) + mark tokenAuth(uuid,isAdmin) -> forward (NO subject.login, NO session)
  SearchApi.doPost: getUser(request) == token user; sees tokenAuth marker
    -> gate: index == "encounter" (403 else); stored-query owner == uuid (403 else)
    -> non-admin: wrap top-level query in bool{must:orig, filter:ACL} (publiclyReadable OR submitterUserId==uuid OR viewUsers==uuid) BEFORE queryPit/execution; fail closed on malformed
    -> admin: no pre-filter
  OpenSearch executes the scoped query -> totals + pagination + hits all reflect only permitted encounters
```

## Security

- **Read-only blast radius:** filter on `/api/v3/search/encounter` only (not the read+write `BaseObject` paths); a leaked token can read (as that user, expiring within the token TTL ≤ 30 min default) but never write.
- **Stateless / no session reuse (Codex Critical):** the filter does NOT call `subject.login` — it wraps the request and forwards. No `JSESSIONID` is minted, no `Set-Cookie`, so a token can never be parlayed into a session that satisfies a write endpoint.
- **Java is the enforcer:** the external side never touches OpenSearch; the ACL pre-filter is injected into the query *before execution* (matching `Encounter.opensearchAccess`), so totals/pagination/hits are all scoped — not a post-sanitize that leaks metadata.
- **Context isolation:** reject tokens whose `context` claim ≠ `context0` AND reject a `?context=` that drifts from `context0`.
- **No credential in the token:** identity-only (`sub`=uuid); the filter trusts it ONLY after full RS256 verification (alg pinned).
- **No secret logging** (token strings, keys).

## Testing

- **Filter unit tests:** valid Bearer → request wrapped so `getUserPrincipal().toString()` == username + tokenAuth marker set + chain proceeds; expired/tampered/wrong-iss-aud/wrong-context Bearer → 401; context drift via **query param, cookie, and host/default** → reject; method outside the allowlist (`PUT`/`PATCH`/`DELETE`) → reject; **no Bearer → chain proceeds unchanged** (session path intact); unknown `sub` → 401.
- **No-session assertion (Codex Critical):** a token request produces no `Set-Cookie`/`JSESSIONID` and leaves no authenticated Shiro subject behind for a subsequent request on the same connection.
- **Index + stored-query gating (Codex High):** token `POST /api/v3/search/individual` (or `occurrence`) → 403; stored-query `{uuid}` owned by another user → 403; stored-query whose `indexName != encounter` → 403; valid own encounter stored-query → scoped results.
- **Fail-closed:** malformed/unparseable query on the token path → not executed (no unscoped results); `Authorization: Bearer` present at `SearchApi` without the tokenAuth marker → 401.
- **Enforcement integration test:** a token-authenticated `/api/v3/search/encounter` returns only the token user's permitted encounters AND the reported `total`/pagination reflect the scoped set (assert an inaccessible encounter is absent from hits AND not counted — guards against the post-sanitize leak). Reuse the `EncounterExportImagesTest`/embedded-Jetty + Shiro harness; seed a user with one approved collaboration + one unrelated private encounter, plus an orgAdmin-visible encounter (asserts `viewUsers` carries orgAdmin). Admin token → unscoped. Confirms Java filters, not the token.

## Kernel retirement (separate, tracked)

This makes the Python kernel's user-facing scoped-read redundant. Retirement (separate task in the `wildbook-coscientist` repo): remove `tokens.py`, `permission_filter.py`, `opensearch_client.py` (user-scoped), `redaction.py`, `operations.py`, `app.py`, `main.py`, and the kernel `models`/`config` fields specific to user-facing reads; **keep** the `coscientist/` operator-scope detection package and the `can-user-access` client (reused by the co-scientist delivery gate); trim `wbk.config.Settings` to the OpenSearch connection + B2 settings the co-scientist still needs.

## Open questions for review

1. ~~Principal mechanism~~ — **resolved (Codex Critical): request-principal wrapper, NOT `subject.login`** (avoids session minting / write-endpoint reuse). `SearchApi` resolves identity via `getUser(request)`→`getUserPrincipal()`, so the wrapper suffices and stays stateless. A Shiro-native `JwtRealm` is deferred for the same session-persistence reason.
2. ~~Endpoint set~~ — **resolved (Codex High): `/api/v3/search/encounter` only.** The `BaseObject` `/api/v3/encounters|individuals/*` paths serve GET+POST+PATCH on one mapping, so token auth there would expose writes; `individual`/`occurrence`/`annotation` search each have their own gaps and are deferred.
3. ~~Filter behaviour on invalid Bearer~~ — **resolved: hard 401.** An explicitly-presented bad token must not silently downgrade to anonymous; absence of a Bearer still falls through to the session path.
4. **PR strategy:** D depends on B (`JwtService`). Stack D on B's PR, or hold D until B merges then branch off `main`? (Still open — pending B #1594 merge decision.)
5. **Still to confirm with reviewer:** the two flagged follow-ups in Scope (global SearchApi metadata leak as its own security PR; orgAdmin coverage in the background `viewUsers` writer) — agree they are out of D's contained scope?
