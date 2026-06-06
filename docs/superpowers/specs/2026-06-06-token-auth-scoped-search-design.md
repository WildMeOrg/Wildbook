# Artifact D — Token-Authenticated Scoped Search (Wildbook, Java)

**Date:** 2026-06-06
**Status:** Draft for Codex design review
**Repo/branch:** Wildbook, `token-auth-scoped-search` (off the A+B integration base; reuses B's `JwtService`).
**Depends on:** Artifact A (merged, PR #1592 — hardened `viewUsers`) and Artifact B (PR #1594 — `JwtService` + `AuthToken`). D reuses `JwtService` for verification and Wildbook's existing enforced search for filtering.

## Purpose

Make **Java the hard permission boundary** for user-facing scoped queries. An external client (Claude / an MCP shim) presents a B-issued bearer JWT to Wildbook's **existing enforced read/search endpoints**; Wildbook verifies the token, resolves the user, and runs its *own* permission-filtered search. The external side never reads OpenSearch directly, so a buggy/compromised external client cannot exceed what the user's roles/collaborations/org-memberships allow.

This supersedes the Python Scoped-Query Kernel's user-facing scoped-read (which enforced in Python). See the kernel-retirement note below.

## Scope

**In:** read-only. The token filter is wired ONLY onto Wildbook's enforced **read/search** endpoints (primarily `SearchApi` at `/api/v3/search/*`, plus encounter/individual read endpoints we choose to expose). A valid token authenticates the user for reads only — it cannot reach write/edit/merge endpoints.

**Out (deferred):** write/agentic actions over token auth; a long-lived revocable PAT scheme (v1 accepts B's short-lived JWT — a PAT, if later desired, either mints/exchanges into the same JWT the filter verifies, or adds a second verification path); non-search read surfaces beyond what's listed.

## Components

1. **Token verification — reuse B's `JwtService`.** Wildbook holds the keypair, so `JwtService.fromConfig(context).verify(token)` checks RS256 (pinned), `iss`, `aud`, and `exp`. **D adds a context check**: the token's `context` claim must equal the server context (`context0`); reject otherwise. (B's `JwtService.verify` enforces iss/aud/alg/exp but not context — the consumer owns the context check; D is that consumer on the Wildbook side.)

2. **`WildbookTokenAuthenticationFilter`** (new — the only substantive new code). A Shiro filter on the read/search paths that:
   - Reads `Authorization: Bearer <jwt>`.
   - **No Bearer** → do nothing, let the chain proceed (existing session/behaviour unchanged — the endpoints still work for browser sessions).
   - **Bearer present** → verify via `JwtService`; on failure (bad sig / expired / wrong iss-aud / wrong context) → **401**.
   - On success → resolve the user via `Shepherd.getUserByUUID(sub)`; if absent → 401. Establish that user as the authenticated subject so downstream `myShepherd.getUser(request)` / `request.getUserPrincipal()` return the token's user.

3. **Establishing the Shiro principal from a pre-verified JWT.** `ShepherdRealm` today only authenticates a `UsernamePasswordToken` (compares a hashed password) — a JWT login has no password. Two candidate mechanisms (recommend the first; Codex to confirm against the live Shiro config):
   - **(Recommended) Shiro-native pre-authenticated realm.** Add a `JwtAuthenticationToken(username)` (no credentials) and a small `JwtRealm` that `supports(JwtAuthenticationToken)`, loads the user, and returns auth info that matches (the token was already cryptographically verified by the filter, so no password check). The filter does `subject.login(new JwtAuthenticationToken(username))`. Register `JwtRealm` alongside `ShepherdRealm` in the Shiro `securityManager`. Result: both `request.getUserPrincipal()` AND `SecurityUtils.getSubject()` reflect the user — fully consistent with how Wildbook auth works.
   - **(Alternative, simpler) request-principal wrapper.** The filter wraps the request in an `HttpServletRequestWrapper` overriding `getUserPrincipal()`/`getRemoteUser()` to return the token username, and forwards. `SearchApi.getUser(request)` resolves the user via `getUserPrincipal()`, so this suffices for the read path — but `SecurityUtils.getSubject()` would NOT reflect the user (only the servlet principal). Acceptable only if every read endpoint in scope uses `getUser(request)`, not the Shiro subject.

4. **`web.xml` Shiro `[urls]` wiring.** Apply the token filter to the read/search paths only (e.g. `/api/v3/search/** = authcTokenOptional`), composed so a Bearer authenticates and absence falls through to the existing rule. The search path currently has no explicit `[urls]` auth rule, so the filter both authenticates token requests and leaves existing behaviour intact for non-token requests.

5. **No new search/enforcement code.** `SearchApi` → `EncounterQueryProcessor` → `Encounter.opensearchAccess` already filter every hit by the live user's roles/collaborations/org-membership/admin (and A made `viewUsers` trustworthy). The token only supplies the principal.

## Data flow

```
Claude/MCP --Bearer JWT--> /api/v3/search/* (Wildbook)
  WildbookTokenAuthenticationFilter: verify JWT (RS256/iss/aud/exp/context) -> getUserByUUID(sub) -> establish subject
  SearchApi.doPost: getUser(request) == token user
  EncounterQueryProcessor + opensearchAccess: filter every hit by THAT user's roles/collabs/orgs/admin
  -> returns only what the user may see (sanitizeDoc redacts the rest)
```

## Security

- **Read-only blast radius:** filter on read/search paths only; a leaked token can read (as that user, expiring within the token TTL ≤ 30 min default) but never write.
- **Java is the enforcer:** the external side never touches OpenSearch; per-hit filtering is Wildbook's existing `opensearchAccess` (live permissions), not a re-implementation.
- **Context isolation:** reject tokens whose `context` claim ≠ server context.
- **No credential in the token:** identity-only (`sub`=uuid); the filter trusts it ONLY after full RS256 verification. The `JwtRealm` must not be reachable except via the verified-token filter (it accepts pre-verified principals).
- **No secret logging** (token strings, keys).

## Testing

- **Filter unit tests:** valid Bearer → subject/principal established + chain proceeds; expired/tampered/wrong-iss-aud/wrong-context Bearer → 401; **no Bearer → chain proceeds unchanged** (session path intact); unknown `sub` → 401.
- **Enforcement integration test:** a token-authenticated `/api/v3/search/encounter` returns only the token user's permitted encounters (reuse the `EncounterExportImagesTest`/embedded-Jetty + Shiro test harness; seed a user with a collaboration and assert scoping). Confirms Java filters, not the token.
- **Realm test:** `JwtRealm` authenticates a `JwtAuthenticationToken` to the right principal and is not usable with other token types.

## Kernel retirement (separate, tracked)

This makes the Python kernel's user-facing scoped-read redundant. Retirement (separate task in the `wildbook-coscientist` repo): remove `tokens.py`, `permission_filter.py`, `opensearch_client.py` (user-scoped), `redaction.py`, `operations.py`, `app.py`, `main.py`, and the kernel `models`/`config` fields specific to user-facing reads; **keep** the `coscientist/` operator-scope detection package and the `can-user-access` client (reused by the co-scientist delivery gate); trim `wbk.config.Settings` to the OpenSearch connection + B2 settings the co-scientist still needs.

## Open questions for review

1. **Principal mechanism:** Shiro-native `JwtRealm` (recommended) vs request-principal wrapper — which composes cleanly with the existing `IniShiroFilter`/`ShepherdRealm` config and the way `SearchApi` resolves the user? (Codex to verify against the live Shiro setup + `securityManager` realms list.)
2. **Endpoint set:** just `SearchApi` (`/api/v3/search/*`) for v1, or also specific encounter/individual read endpoints? Which read endpoints does the Claude/MCP use-case actually need first?
3. **Filter behaviour on invalid Bearer:** hard 401 (chosen) vs. fall through to anon — 401 is safer (an explicitly-presented bad token shouldn't silently downgrade to anonymous), confirm.
4. **PR strategy:** D depends on B (`JwtService`). Stack D on B's PR, or hold D until B merges then branch off `main`?
