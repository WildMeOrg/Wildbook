# Wildbook ACL Prerequisites — Design Spec

**Date:** 2026-05-29
**Status:** Draft — incorporated Codex design review (2026-05-29); pending user approval
**Repo:** Wildbook (this repo)
**Related specs:** [AI Co-Scientist + Scoped Query Kernel](2026-05-29-ai-coscientist-design.md)
**Related memory:** `project_viewusers_acl_bug.md`, `project_ai_coscientist.md`

## Purpose

The AI Co-Scientist project (separate spec) needs a *sound* authorization boundary so an external read-only service can scope OpenSearch queries to exactly the data a given Wildbook user may see. A code review (Codex, 2026-05-29, verified in source) found that Wildbook's existing `viewUsers[]` OpenSearch ACL field — already relied on by Wildbook's *own* search via `Encounter.opensearchAccess` and `EncounterQueryProcessor` — is **not trustworthy as written**. This spec covers the Wildbook-side prerequisites:

- **Artifact A — `viewUsers` ACL hardening.** A standalone security bugfix. Valuable independent of the co-scientist: it closes a live data-exposure path in Wildbook's existing search.
- **Artifact B — Integration endpoints.** Net-new API the external kernel needs: short-lived token issuance and a live "can user access encounter" check used as a defense-in-depth backstop.

These are **two separate branches/PRs** (A is a pure security bugfix; B is feature additions) so A's security diff stays clean and independently reviewable.

## Background: the two confirmed defects

Both verified in current source on 2026-05-29.

### Defect 1 — revocation can leak indefinitely
`Encounter.opensearchIndexPermissions()` (the correct background ACL writer) only pushes an update when the computed set is non-empty:

```java
// Encounter.java ~4224
if (viewUsers.length() > 0) {
    updateData.put("viewUsers", viewUsers);
    os.indexUpdate("encounter", id, updateData);
}
```

When the **last** collaborator/orgAdmin loses access, the computed set is empty, so **nothing is written** — the previously-indexed `viewUsers` array persists in OpenSearch until some unrelated full reindex happens to overwrite the document. A revoked user retains read access via the field for an unbounded period. This is not the ~10–45 min background-pass latency; it is potentially permanent.

### Defect 2 — a second, contradictory `viewUsers` writer
`Encounter.userIdsWithViewAccess()` is a *different* ACL computation used by the full-document serializer:

```java
// Encounter.java ~3351 — NO state filter, BACKWARDS direction
List<Collaboration> collabs =
    Collaboration.collaborationsForUser(myShepherd, this.getSubmitterID());
for (Collaboration collab : collabs) {
    User user = myShepherd.getUser(collab.getOtherUsername(this.getSubmitterID()));
    if (user != null) ids.add(user.getId());
}
```

It is invoked whenever `opensearchProcessPermissions == true` (`Encounter.java` ~4605–4615), which ordinary single-encounter edit servlets set (e.g. `EncounterSetLocationID`). Versus the correct background writer it:

1. **Applies no state filter** — `collaborationsForUser(..., state=null)` returns `initialized`/`rejected`/`pending` collaborations, granting view access to users who never had it (or were rejected).
2. **Uses the backwards direction** the background pass explicitly warns against (`Encounter.java` 4189–4193): it asks "who is the submitter collaborating with" rather than "who can see this submitter," which also **inverts orgAdmin one-way visibility** (org members would gain sight of the orgAdmin's own encounters).

So in production `viewUsers` is a race between a correct writer (background pass) and a buggy writer (edit-triggered), and the buggy one runs on routine edits.

### Impact beyond the co-scientist
`EncounterQueryProcessor` post-filters search hits with `Encounter.opensearchAccess(doc, user, shepherd)`, which reads these exact fields. Both defects therefore affect **Wildbook's existing search authorization today**, independent of any new service.

## Artifact A — `viewUsers` ACL hardening

### Goals
1. Exactly one ACL computation, used by every writer.
2. `viewUsers` is always written for non-public encounters, including the empty set `[]` (revocation propagates).
3. Revocation/rejection/removal of a collaboration or orgAdmin triggers reindex of affected encounters.
4. Full-document reindex includes `viewUsers` (or never drops it).
5. A one-time corrective full reindex repairs already-stale docs in existing installs.

### Design

**Single ACL writer.** Extract the correct logic (currently inline in `opensearchIndexPermissions()`, `Encounter.java` 4208–4222) into one method — e.g. `Encounter.computeViewUsers(Shepherd)` — that:
- returns the set of user UUIDs who can see this encounter via **approved/edit** collaborations, in the correct "who can see the submitter" direction, including correctly-directed assumed orgAdmin collaborations;
- returns `[]` for a non-public encounter with no viewers;
- is the **only** producer of `viewUsers`.

Replace `userIdsWithViewAccess()`'s body with a call to this method (or delete it and repoint callers). Delete the divergent state-less/backwards logic.

**Always-write semantics.** Remove the `if (viewUsers.length() > 0)` guard at `Encounter.java` ~4224; always write the field (including `[]`) for non-public encounters. Public encounters set `publiclyReadable=true` and need no `viewUsers`.

**Full-reindex includes the field — via two explicit code paths (resolves prior open Q1).** A full reindex must never emit a `viewUsers` it did not compute, and must never re-emit the stale indexed value (that would preserve exactly the bug A repairs, and cannot serve the corrective reindex). But naively computing the ACL on *every* full index is a real scale risk — generic per-doc `viewUsers` computation was previously removed from the base serializer as too expensive (`Base.java:214–216`); the background pass is only affordable because it builds the user/collab maps **once** and SQL-scans encounters (`Encounter.java:4143–4167`). Therefore define two paths, both sourced from the single `computeViewUsers` logic:
- **Single-encounter recompute** — for edit servlets and lifecycle-triggered reindex of one encounter (live JDO collaboration scan; acceptable for one doc).
- **Bulk ACL-computation service** — for full reindex and the background/corrective passes: build the maps once, pass a precomputed ACL snapshot into the reindex job. The full-document serializer takes `viewUsers` from this snapshot, never from the stale index and never dropping the field.

Add timing metrics before adopting any always-compute path.

**Revocation triggers reindex — enumerate every trigger; `IndexingManager` alone is insufficient.** The JDO lifecycle listener only special-cases `Base` and `Collaboration` objects (`WildbookLifecycleListener.java:53–68`), but several revocation paths mutate *other* objects and would silently fail to reindex:
- Collaboration → `rejected`/deleted (already covered by the listener).
- **orgAdmin role removal** — roles are plain `Role` objects (`UserCreate.java:194–200`), not `Base`/`Collaboration`.
- **Org membership / hierarchy edits** — mutate `User`/`Organization` (`User.java:452–458`, `Organization.java:119–124`).
- **User deletion / consolidation / rename** — affects both the old and new owner's encounters.

Each must explicitly enqueue permissions-recompute for the affected encounters (submitter's encounters; for orgAdmin/org changes, the org members' encounters) via the bulk path.

**Invalid/deleted/renamed submitters must fail closed.** Current permissions indexing *skips* an encounter when `submitterID` doesn't map to a user (`Encounter.java:4178–4184`), and full indexing omits `submitterUserId` when the user is missing (`Encounter.java:4344–4345`) — leaving any prior `viewUsers` orphaned. `computeViewUsers` must instead return and write `[]` for non-public encounters with invalid/missing owners (and log/audit them), so such encounters become admin-only, not stale-open. User rename/consolidation must enqueue both the old and new owner's encounters.

**One-time corrective reindex.** Document an operator step (and/or a startup migration flag) to run a full permissions reindex once after deploy to repair already-stale `viewUsers` in existing installs.

### Tests (required, security-sensitive)
- Sole collaborator rejected → `viewUsers` becomes `[]`; previously-permitted user no longer matches.
- orgAdmin role removed → org members' encounters drop that admin from `viewUsers`.
- Edit servlet (e.g. set location) on an encounter does **not** introduce rejected/pending users and does **not** invert orgAdmin direction (regression test for Defect 2).
- Full reindex of an encounter preserves correct `viewUsers`.
- Cross-check: for a sample of encounters/users, the indexed `viewUsers`-based decision matches a live `Collaboration.canUserAccessEncounter` computation.

## Artifact B — Integration endpoints

Net-new, additive. No token/JWT/API-key infrastructure exists today (only Shiro HTTP Basic). Keep this off Artifact A's clean security diff.

### B1 — `POST /api/auth/token` (short-lived token issuance)
- Gated by the **existing** Shiro authentication (an already-authenticated user calls it).
- Mints a short-lived **signed** token. Claims: subject = user UUID, `context`, `iss`, `aud`, `jti`, `exp` (~30–60 min), `iat`. **No** admin/orgAdmin/role claims (privileges are resolved fresh by the kernel per request).
- Signing: **asymmetric** (decided — the service runs remote). Wildbook holds the private signing key; the remote kernel validates with the public key only, so a kernel compromise cannot mint tokens. Key id in header + rotation support.
- Must have an **explicit** Shiro filter rule in `web.xml` (existing config covers only selected `/api/v3/...` paths; do not assume coverage). CSRF protection if the caller is cookie-authenticated.
- No server-side session state required beyond signing keys; refresh = re-call with Basic auth.

### B2 — Live `canUserAccess` check (defense-in-depth backstop)
- An authenticated endpoint (admin/service-scoped) that, given a user UUID and a set of encounter IDs, returns the subset that user may access — computed from **live** `Collaboration`/role objects, **not** from the indexed `viewUsers`.
- **Use the correct authorization entry point.** `Collaboration.canUserAccessEncounter(enc, context, username)` does **not** check admin and only does username-based collaboration (`Collaboration.java:465–470`); admin handling lives in `Encounter.canUserView` (`Encounter.java:3316–3318`). B2 must resolve the UUID to a `User` **in the requested context** and call `Encounter.canUserView(user, shepherd)` (or an audited equivalent), never the bare string overload — otherwise admins are wrongly denied and the result is unsound.
- Batched (accept up to a page of IDs per call) so the kernel can re-validate a result page cheaply.
- Purpose: closes the residual `viewUsers` staleness window for the user-facing API even after Artifact A; the kernel pre-filters on `viewUsers` for speed, then confirms the returned page against this live check.

### Tests
- Token: valid/expired/tampered signature; wrong `aud`/`iss`; context isolation (a token issued in `context0` cannot be used to read another context's data); admin claim in token is ignored (privileges resolved fresh).
- canUserAccess: matches `Encounter.canUserView` for owner / approved-collab / orgAdmin / rejected-collab / public / admin cases; correctly excludes a just-rejected collaborator even when `viewUsers` is still stale.

## Out of scope / follow-ups
- Denormalizing ACL fields onto annotation/individual/occurrence indices (the external API is encounter-index-only for v1; see co-scientist spec).
- Write/agentic endpoints (v1 is read-only).
- Multi-context productionization beyond a mandatory indexed `context` field + per-request context scoping (see co-scientist spec § ACL boundary).

## Open questions for review
1. ~~Cost of computing the ACL on every full index~~ — **resolved** (Codex design review): two paths, single-encounter recompute + bulk ACL snapshot; never re-emit stale index value.
2. ~~Symmetric vs. asymmetric token signing~~ — **resolved: asymmetric.** The service runs remote, so the signing (minting) key must never leave Wildbook; the remote kernel holds only the public verify key. Support key id + rotation.
3. Should revocation-triggered reindex be synchronous-ish (security) or rely on the existing async queue (simplicity)? What revocation latency is acceptable given B2 backstops the user-facing path?
4. Context handling (see co-scientist spec): v1 targets a single Wildbook context (typically `context0`); true multi-context scoping requires a mandatory indexed `context` field on **both** the encounter and annotation indices (none exists today) — confirm v1 may assume single-context.
