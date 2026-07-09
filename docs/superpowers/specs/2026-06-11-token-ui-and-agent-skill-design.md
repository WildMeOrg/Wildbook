# Token-Issuance UI + Served Agent Skill — Design

**Date:** 2026-06-11
**Status:** Draft — incorporated Codex design review (2026-06-11, verified against source; all
High/Medium/Low findings folded). Pending user review.
**Builds on:** the deployed token-scoped read API — Artifact B (`JwtService`/`AuthToken`,
`POST /api/v3/auth/token`), Artifact D + Spec A (token-scoped `encounter`/`individual`/`annotation`
search), and the media-resolve endpoint (`POST /api/v3/media/resolve`). All on branch
`token-auth-scoped-search` / PR #1613.

---

## Problem

A logged-in Wildbook user has no way to obtain a bearer token from the UI — tokens are only mintable
by calling `POST /api/v3/auth/token` with HTTP Basic auth, which a browser session can't do on its
own. And even with a token, a user's AI agent has no machine-readable description of *how* to use
Wildbook's token-scoped API (endpoints, the OpenSearch schema/fields, how to authenticate, what not to
do). This design adds the two missing pieces so a user can (1) generate a token from the UI and
(2) point their agent at a Wildbook-served skill that teaches it the API safely.

## Goals

1. **Token UI:** a logged-in user can mint a short-lived bearer token from a UI page, with a
   step-up password confirmation, and copy it once.
2. **Agent skill:** Wildbook serves an agent-loadable markdown document describing the token-scoped
   API, the OpenSearch schema (indices + fields + descriptions), how to get a token, and security
   guidance that explicitly discourages giving an agent the user's username/password.

## Non-Goals

- Managed/persisted personal API keys (naming, listing, revocation) — the token stays the existing
  **stateless** 30-min RS256 JWT. A managed-key store is a documented future phase.
- Any change to token TTL, scoping, or the JWT shape (reuses the deployed signing path). **Exception
  (required by Codex review):** the mint endpoint gains server-side enforcement that a *fresh* Basic
  credential was supplied — a session cookie alone must NOT be sufficient to mint (see Mint auth).
- Dynamic/live-introspected skill generation — the skill is a curated static doc (future: hybrid).
- Support for password-less/SSO accounts in the step-up flow (noted limitation).

---

## Token model (decided)

Ephemeral **generate-and-copy**: the existing stateless RS256 JWT (default 30-min TTL, server-clamped
1 min–24 h). No storage, no revocation, no list. Matches exactly what is already deployed.

## Mint auth (decided) — step-up password, **enforced server-side**

**Step-up password re-prompt.** The UI collects the user's password; the server verifies it **fresh**
on every mint and never accepts a session cookie as sufficient.

**Why this needs a backend change (Codex High).** `/api/v3/auth/token` is Shiro-gated by
`authcBasicWildbook`, but Shiro's basic-auth filter lets an *already-authenticated session* pass
**without parsing/verifying the Basic header** (`AuthToken` mints for `myShepherd.getUser(request)`).
So as originally drafted, a logged-in browser could mint via its cookie — even with a *wrong*
password — and the "no CSRF / step-up" guarantee would be false. The fix:

- **Backend (`AuthToken`):** require an `Authorization: Basic` header and **verify the supplied
  username/password against the user's stored credential using Wildbook's existing password check**
  (the same credential path login uses), independent of any session. Mint for *that* verified user.
  A request with no Basic header (session-only) → `401`; a Basic header with a wrong password → `401`,
  regardless of an active session. This also closes the same-origin-XSS vector (a malicious in-page
  script can't mint without the password). Add `Cache-Control: no-store` to the token response.
- **Frontend (defense in depth):** send the mint with `fetch(..., { credentials: "omit" })` (a raw
  fetch, not the shared cookie-bearing axios client) so no session cookie accompanies the Basic
  request.

CSRF: Basic-auth credentials are not auto-sent cross-site and the token is returned only in the
response body (unreadable cross-origin); with the server-side Basic requirement, a session alone can
never mint.

---

## Component A — Token-issuance UI (+ a small backend enforcement)

`POST /api/v3/auth/token` returns `{token, tokenType, expiresInSeconds}`. The UI authenticates the
mint with `Authorization: Basic base64(<loggedInUsername>:<password>)` (raw `fetch`, `credentials:
"omit"`) and displays the result; the backend verifies the Basic credential fresh per the Mint-auth
section above.

### Backend unit
- `AuthToken` (modify) — require + verify a fresh `Authorization: Basic` credential server-side
  (reuse Wildbook's existing password check), reject session-only (`401`), add `Cache-Control:
  no-store`. **Rate-limit / audit (Codex Medium, adjusted to reality):** verified against source —
  Wildbook has **no** app-level login lockout/throttle today (`/api/v3/login`, `/rest/**` Basic auth,
  etc. are equally unthrottled), so there is no existing mechanism to "reuse," and adding a bespoke
  per-username lockout to *only* the mint would be an inconsistent one-off that also enables targeted
  account-DoS. Decision: the mint is no more of a password oracle than the existing auth surfaces;
  v1 adds **audit logging** of every mint attempt (username + client IP + success/failure, **never**
  the password or token) so abuse is detectable, plus `Cache-Control: no-store`. A platform-wide auth
  throttle (covering login + Basic + mint uniformly) is the correct home and is recorded as a
  separate follow-up — out of scope here.

### Units (all under `frontend/src`)
- **Route:** new authenticated route `/api-access` in `AuthenticatedSwitch.jsx` → `ApiAccessPage`.
- **Menu:** a new "API Access" item in `components/header/AvatarAndUserProfile.jsx` linking to it.
- **Hook:** `useMintToken` (a model hook) — builds the Basic header from the `useGetMe`
  (`/api/v3/user`) username + the entered password and POSTs to `/api/v3/auth/token`. The password is
  passed in at call time, used for the single request, and never stored.
- **Components:** a password-confirm modal + a token-display box (copy button, expiry countdown,
  "shown once" warning).

### UX flow
1. Avatar dropdown → **API Access** → `/api-access`.
2. Page explains the feature, warns *"Do NOT give your agent your username/password — use a token,"*
   and links the agent skill URL (`/api/v3/agent-skill`) with a copy button.
3. **Generate API token** → password-confirm modal (username shown read-only from `useGetMe`;
   password field; "Confirm your password to mint a token").
4. Submit → `POST /api/v3/auth/token` with the Basic header.
5. **Success** → modal closes; the token is shown once in a copy box with an expiry countdown
   ("expires in ~30 min") and "copy it now — it won't be shown again." Token lives only in component
   state; navigating away clears it.

### Errors
- `401` → inline "Incorrect password" in the modal (do not close it).
- `503` → "Token issuance isn't enabled on this server."
- network / `500` → generic "Couldn't generate a token, try again."
- **Password-less / SSO accounts:** `/api/v3/user` exposes `username` but **not** whether the account
  has a local password (confirmed: `UserInfo`/`User.infoJSONObject`), so there is no cheap, reliable
  pre-check. v1 does **not** add a `hasLocalPassword` signal — such users simply get the normal `401`
  ("Incorrect password") path. The `401` copy may add a hint ("if your account uses single sign-on,
  API tokens aren't available yet"), but no tailored detection is built. (A `hasLocalPassword` field
  is a documented future option.)

### Security
- Password sent only in the single mint request over HTTPS, then discarded; never logged/stored.
- Token held only in component state, cleared on navigation; never persisted to localStorage.
- No CSRF surface (Basic-auth path). The `/api-access` route is authenticated (logged-in only);
  minting still requires the step-up password.

---

## Component B — Served agent skill (small backend + curated doc)

### Serving
- New servlet `AgentSkill` (`org.ecocean.api`) at **`GET /api/v3/agent-skill`**, returning
  `text/markdown; charset=UTF-8`.
- **Anonymous** Shiro rule (`/api/v3/agent-skill = anon`) — a how-to-authenticate doc cannot itself
  sit behind auth, and it contains no secrets (only API docs + schema field metadata + guidance).
- Content lives in a versioned resource `src/main/resources/agent-skill.md`, loaded from the
  classpath and streamed; the servlet adds no dynamic data. Deploy-versioned, easy to edit.

### Skill content (agent-agnostic, self-contained markdown)
1. **Preamble** — "You are an agent operating Wildbook's read API on behalf of a user." Scope:
   read-only; the user's own ACL-scoped view.
2. **Security first (the requirement that motivated this):** *Never ask for or accept the user's
   Wildbook username/password.* The human mints a short-lived token in the API Access UI and pastes
   **only the token**; treat it as a secret; it expires (~30 min) and is re-minted as needed; never
   log or persist it.
3. **Auth mechanics** — link to the API Access UI; `Authorization: Bearer <token>`;
   `expiresInSeconds`; admin-vs-non-admin scoping (everything is ACL-filtered to what the user sees).
4. **Endpoints** — `POST /api/v3/search/{encounter|individual|annotation}` (allowed query-DSL subset,
   pagination headers, what is returned/scrubbed); `POST /api/v3/media/resolve` (annotation IDs →
   `imageUrl` + source-frame `bbox` + `imageWidth/imageHeight`, the **consumer-scales** contract,
   ≤100 IDs); what is **not** allowed (`occurrence`/`media_asset` → 403; restricted aggregate/script
   queries for non-admin individual search).
5. **OpenSearch schema** — the `encounter`/`individual`/`annotation` indices with field descriptions
   (sourced from `docs/opensearch-indices-and-fields.md`), limited to **token-exposed, returned**
   fields; `embeddings` (nested vector, `method`/`methodVersion`). **Do not** document internal ACL
   field names (`publiclyReadable`/`submitterUserId(s)`/`viewUsers`/`editUsers`) or deployment
   details (Codex Low) — state only that ACL fields exist server-side and are never returned.
6. **Worked examples** — search by taxonomy; resolve annotation IDs and crop with the bbox; the
   missed-match calibration caveat (compare within one `viewpoint` + `methodVersion`).

### Security
- Anon GET; no secrets, no user data — only documentation. Its content actively discourages
  credential sharing and promotes short-lived tokens.

---

## Testing

### Frontend (Jest / React Testing Library)
- `useMintToken` builds the correct `Authorization: Basic` header from the `useGetMe` username + the
  entered password and POSTs to `/api/v3/auth/token`.
- `ApiAccessPage`: **Generate** opens the modal; a successful mint renders the token + expiry + copy
  control; `401` renders the inline "Incorrect password" error (modal stays open); `503` renders the
  "not enabled" message.
- `AvatarAndUserProfile` includes the new "API Access" item linking to `/api-access`.
- (Repo note: frontend jest is continue-on-error in CI — tests are still written and run locally.)

### Backend (JUnit)
- **`AuthToken` step-up enforcement (Codex High):** a session-only request (no Basic header) → `401`;
  a Basic header with a *wrong* password → `401` *even with an active session*; a correct Basic
  credential → `200` + token + `Cache-Control: no-store`. (Mock the session subject + the credential
  check.)
- `AgentSkill` returns `200` with `Content-Type: text/markdown; charset=UTF-8`, a non-empty body
  containing the key anchors: `Authorization: Bearer`, `/api/v3/media/resolve`, the three index names,
  and the "never share credentials" guidance.
- **Skill drift-guard (Codex Medium):** a test asserts the skill markdown's API claims stay in sync —
  it mentions exactly the token-allowed indices (`encounter`/`individual`/`annotation`, and that
  `occurrence`/`media_asset` are 403) consistent with `SearchApi`'s allowlist and `MediaResolveApi`,
  and references no field the skill claims is returned that isn't. (Pragmatic form: assert the
  allowed/denied index sets in the markdown match the `SearchApi` token allowlist constants; flag on
  mismatch so the doc can't silently drift.)
- `EndpointAuthWiringTest`: the `AgentSkill` servlet + `/api/v3/agent-skill` `<url-pattern>` are
  registered (an exact mapping that wins over the `/api/*` → `WildbookApi` mapping), and the Shiro
  `[urls]` rule for `/api/v3/agent-skill` is exactly `anon`.

### Live smoke
- `GET /api/v3/agent-skill` on flakebook returns the markdown anonymously.
- Run the UI mint → copy flow as a logged-in user; confirm `401` on wrong password.
- **Step-up enforcement:** while logged in (session cookie present), `POST /api/v3/auth/token` with a
  *wrong* Basic password returns `401` (the session does not let it through); with no Basic header at
  all returns `401`.
- Paste the minted token into a `POST /api/v3/search/...` call and confirm it works.

---

## Components / file boundary

**Component A — backend (step-up enforcement):**
- `src/main/java/org/ecocean/api/AuthToken.java` (modify) — require + verify a fresh Basic credential
  server-side (reuse Wildbook's password check), reject session-only, reuse login lockout/throttle,
  `Cache-Control: no-store`, audit-log without secrets.
- `src/test/java/org/ecocean/api/AuthTokenTest.java` (modify/extend) — the step-up enforcement cases.

**Component A — frontend:**
- `frontend/src/pages/ApiAccess/ApiAccessPage.jsx` (new) — page + modal + token display.
- `frontend/src/models/auth/useMintToken.js` (new) — the mint hook (raw `fetch`, `credentials:
  "omit"`, Basic header).
- `frontend/src/AuthenticatedSwitch.jsx` (modify) — add the `/api-access` route.
- `frontend/src/components/header/AvatarAndUserProfile.jsx` (modify) — add the menu item.
- Frontend tests under `frontend/src/__tests__/`.

**Backend (Component B):**
- `src/main/java/org/ecocean/api/AgentSkill.java` (new) — the servlet.
- `src/main/resources/agent-skill.md` (new) — the curated skill content.
- `src/main/webapp/WEB-INF/web.xml` (modify) — servlet + mapping + anon Shiro rule.
- `src/test/java/org/ecocean/api/AgentSkillTest.java` (new) + `EndpointAuthWiringTest.java` (modify).

This is a self-contained increment on the `token-auth-scoped-search` branch. Component A is mostly
frontend plus a focused security hardening of the existing `AuthToken` mint (fresh Basic
verification); Component B adds one servlet + one resource + a web.xml mapping. Neither changes the
token JWT shape, TTL, scoping, or the search/index code paths.
