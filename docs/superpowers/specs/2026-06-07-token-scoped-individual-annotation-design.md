# Token-Scoped Individual + Annotation Reads (Spec A) — Design

**Date:** 2026-06-07
**Status:** Draft — pending Codex design review + user review
**Repo/branch:** Wildbook; new branch off `token-auth-scoped-search` (Artifact D) — or off `main` once A/B/D have landed. Reuses D's `WildbookTokenAuthenticationFilter` + `SearchApi` token path and A's permissions-indexing pattern.
**Depends on:** Artifact A (merged — `viewUsers` hardening / encounter ACL denormalization), Artifact B (`JwtService`/`AuthToken`), Artifact D (token-scoped encounter search — live-verified on flakebook.wildme.org).
**Sibling (deferred):** Spec B — detection-via-service-account-token + nested-kNN ACL injection (separate design).

## Purpose

Extend the token-scoped read boundary (Artifact D) from the `encounter` index to the **`individual`** and **`annotation`** indices, so an agent acting on a user's behalf (via a bearer token) can read individuals and annotations — seeing **exactly** what that user would see on Wildbook's own individual page, and never more. The encounter remains the unit of ACL truth; individual and annotation visibility are **projections gated by encounter access**. An admin token is the "universal access agent" (no filter), matching a Wildbook admin.

This is the user-facing read extension (Consumer 2). It also lays the groundwork for detection (Spec B) by making the annotation index reachable via token.

## Background — the authoritative Wildbook logic (verified in source)

- **Individual view gate:** `Collaboration.canUserAccessMarkedIndividual(mi, request)` (`Collaboration.java:555`) returns true iff the user can access **≥1 of the individual's member encounters** (an individual with zero encounters is visible to anyone). Per-encounter access is the collaboration predicate `canUserAccessEncounter` → `canUserAccessOwnedObject` → `canCollaborate` (`Collaboration.java:452,477,309`): admin, or security-disabled, or **anonymous/null owner** (`enc.getSubmitterID()==null` → true), or same user, or an **approved/edit** collaboration between viewer and owner.
- **Individual page content:** `individuals.jsp:196–216` recomputes all aggregates (sighting count, locations, dates) from the **security-scrubbed** encounter list (`HiddenEncReporter.securityScrubbedResults`) — i.e. only the encounters that viewer can access. The page is **precise per-viewer**, never leaking aggregates from hidden encounters.
- **API redaction when no access:** `MarkedIndividual.sanitizeJson` (`:2123`) returns only `displayName` + `_sanitized:true`, stripping `sex`, `numberEncounters`, `numberLocations`, `timeOfBirth/Death`, `maxYearsBetweenResightings`, `nickName/nickNamer`.
- **Encounter ACL denormalization (Artifact A):** `Encounter.opensearchAccess` (`Encounter.java:4753`) = `publiclyReadable` OR `submitterUserId==user` OR admin OR `viewUsers` contains user. A built `viewUsers` to be the **materialized** collaboration result, and cross-tested that the indexed decision matches the live `Collaboration.canUserAccessEncounter`. So the indexed encounter predicate is the denormalized form of the live collaboration gate.
- **Today's child indices have no ACL:** the `annotation` and `individual` OpenSearch docs carry no `publiclyReadable`/`submitterUserId`/`viewUsers`; the individual doc's aggregates are computed over **all** encounters (`MarkedIndividual.opensearchDocumentSerializer`, ~`:2723`). Annotation `_source` includes `embeddings`.

## Architecture & access model

**The encounter index is the gatekeeper.** We extend A's denormalization onto the two child indices so the *same* token pre-filter (D) enforces all three.

Per-index ACL fields (written at index time):

| Index | Fields | Source |
|---|---|---|
| `encounter` (exists) | `publiclyReadable`, `submitterUserId`, `viewUsers` | A |
| `annotation` (new) | `publiclyReadable`, `submitterUserId`, `viewUsers` | copied verbatim from the one parent encounter |
| `individual` (new) | `publiclyReadable`, `submitterUserIds`, `viewUsers` | **unions** over member encounters |

Individual unions (chosen so the indexed gate is provably equal to `canUserAccessMarkedIndividual`):
- `publiclyReadable` = `OR` over member encounters' `publiclyReadable` (i.e. *any* member encounter is world-readable).
- `submitterUserIds` = the **set** of member encounter submitter UUIDs.
- `viewUsers` = the **union** of member encounters' `viewUsers`.

Then: *user can access individual* ⟺ `publiclyReadable` OR `user ∈ submitterUserIds` OR `user ∈ viewUsers` ⟺ ∃ member encounter the user can access. ✔ matches the live gate.

**Token-path enforcement** (in `SearchApi`, building on D): for a non-admin token request, inject the ACL pre-filter using *that index's* field names, before execution. **Admin token → no filter** (universal access). Token-exposed indices become **`encounter`, `annotation`, `individual`**; `occurrence` and `media_asset` remain **403**. The non-token/session path is **unchanged**.

**Fail-closed during reindex:** until the corrective reindex populates the new fields, they are simply absent on child docs → the pre-filter matches nothing → a non-admin sees *nothing* on those indices. A partial/in-progress reindex degrades to "too restrictive," never "leaky."

## Read surfaces

### Annotation (`POST /api/v3/search/annotation`, token path)
Annotation is 1:1 with an encounter, so each annotation doc carries the parent encounter's three ACL fields. The token path injects the **same** pre-filter as encounter (identical field names): a non-admin sees only annotations whose encounter they can access; admin bypasses. **Embeddings are returned as-is** (deemed not sensitive). The annotation doc is otherwise gated **wholesale** by the encounter ACL — no content redaction — **except** the internal denormalized ACL fields (`viewUsers`, and the copied `submitterUserId`/`publiclyReadable` if not otherwise meaningful) are removed from the returned doc, consistent with how Artifact D strips `viewUsers` from encounter responses (`sanitizeDoc` → `clean.remove("viewUsers")`). This also gives detection (Spec B) a real annotation read surface.

Note: the existing **session** path keeps annotation admin-only (`SearchApi`'s `"annotation".equals(indexName) && !isAdmin → 403`). That gate must NOT block the token path — the token path is governed by the ACL pre-filter instead. The implementation must ensure the annotation-admin-only branch applies only to non-token requests.

### Individual (`POST /api/v3/search/individual`, token path)
Two parts:

1. **Gate** — inject the pre-filter against the individual union fields:
   ```json
   { "bool": { "should": [
       {"term": {"publiclyReadable": true}},
       {"term": {"submitterUserIds": "<uuid>"}},
       {"term": {"viewUsers": "<uuid>"}}
     ], "minimum_should_match": 1 } }
   ```
   The user's own query (name/taxonomy/sex/etc.) runs normally, `must`-combined with this gate. (`term` on the keyword-array fields `submitterUserIds`/`viewUsers` matches if any element equals the uuid.)

2. **Strip leaky aggregates** — a token-path sanitizer removes fields computed across *all* member encounters (which would reveal hidden ones), keeping individual-identity attributes:

   | Keep (identity attributes) | Strip (cross-encounter aggregates → leak) |
   |---|---|
   | `id`, `displayName`, `names`/`nameMap`, `sex`, `taxonomy`, `timeOfBirth`, `timeOfDeath` | `encounterIds`, `numberEncounters`, `users`, `locationGeoPoints`, `numberLocations`, `maxYearsBetweenResightings`, `socialUnits`, `relationships`, `cooccurrenceIndividualMap`, occurrence-derived counts |
   | (plus the new ACL fields `publiclyReadable`/`submitterUserIds`/`viewUsers` are stripped from the response) | |

   The final keep/strip list is pinned against `MarkedIndividual.opensearchDocumentSerializer` during implementation; any field derived from the full member-encounter set is stripped. `sex`/`timeOfBirth`/`timeOfDeath` are individual-level attributes Wildbook shows to anyone who can access the individual, so they are kept.

**Two-hop richness:** the agent obtains the *per-viewer* sighting list, locations, dates, co-occurrences for an individual by querying `POST /api/v3/search/encounter` with `{"query":{"term":{"individualId":"<id>"}}}` — already ACL-scoped. The rich record is assembled across two indices, each enforcing the ACL independently; nothing leaks and individual search keeps full query expressiveness.

## Indexing & sync

**Serializer changes:**
- **Annotation serializer** — emit the parent encounter's `publiclyReadable`, `submitterUserId`, `viewUsers`.
- **Individual serializer** (`MarkedIndividual.opensearchDocumentSerializer`) — in the existing all-member-encounters pass, also compute and emit `publiclyReadable` (OR), `submitterUserIds` (set), `viewUsers` (union).
- **Mappings** are additive: add the new fields (boolean / keyword) to the existing annotation + individual index mappings (OpenSearch allows adding fields to a live mapping); no drop/recreate required.

**Sync triggers (correctness-critical).** Artifact A reindexes an encounter when its ACL changes (collaboration approve/reject, orgAdmin role change, org membership, user delete/rename/consolidation). Spec A **extends those same trigger points** so that when an encounter's `viewUsers`/`publiclyReadable`/`submitterUserId` changes, we also enqueue reindex of (a) its annotations and (b) its individual. Without this, a revoked viewer could linger on the child indices after the encounter is corrected — the bug class A hardened, now for two more indices. Use the existing bulk/queue path; document any best-effort-vs-guaranteed nuance as A did.

**Parity edge — anonymous / null-submitter encounters.** Wildbook's live gate grants these to everyone (`Collaboration.java:477`). The pre-filter only grants via `publiclyReadable`/submitter/`viewUsers`, so indexing must mark anonymous/null-owner encounters as `publiclyReadable=true` (or an equivalent world-readable flag the filter treats as public) — on the **encounter** index too, since today it may under-grant those. This is a small correction that also tightens encounter parity. (Confirm against A's existing handling of invalid/missing owners, which currently writes `[]`/admin-only — reconcile: anonymous-owned ≠ invalid-owner; anonymous-owned is world-readable, invalid/deleted-owner is admin-only/fail-closed.)

**Equivalence test (extends A's cross-check).** Add the analogue of A's indexed-vs-live cross-check: for a sample of individuals/annotations, the indexed gate decision matches live `Collaboration.canUserAccessMarkedIndividual` / `canUserAccessEncounter`.

**One-time corrective reindex.** A runbook step (analogue of A's `viewUsers` corrective pass), run once at deploy, in order: (1) encounter anon-owner correction (or full encounter pass), (2) full annotation reindex (**the long pole** — several annotations per encounter; can be 100K+ on large installs), (3) full individual reindex. After this, the extended triggers keep fields current incrementally. Until it runs, child-index token reads fail closed (non-admin sees nothing there).

## `SearchApi` changes

Small, building on D's structure:
- Index gate flips from "encounter-only" to a token **allowlist `{encounter, annotation, individual}`**; `occurrence`/`media_asset` still 403.
- Generalize `OpenSearch.applyEncounterAclFilter(query, uuid)` → `applyAclFilter(query, uuid, indexName)` that selects field names per index (`submitterUserId` for encounter/annotation; `submitterUserIds` for individual; `publiclyReadable`/`viewUsers` common). Same `bool{must:[orig], filter:[should…]}` shape, injected before `queryPit`, admin-bypassed.
- **Individual result sanitizer**: a token-path branch (in `sanitizeDoc` or a sibling) that strips the cross-encounter aggregate fields (the keep/strip table) from individual hits. Annotation hits pass through whole (embeddings included). Encounter unchanged.
- Ensure the existing **annotation-admin-only** branch applies only to **non-token** requests (token path uses the ACL filter instead).
- Stored-query owner check, method allowlist, fail-closed-unmarked, verified-context binding — **unchanged** from D; already per-index.

## Testing

- **Unit:** `applyAclFilter` builds the correct clause per index (field names); the individual sanitizer strips exactly the leak set and keeps identity; index allowlist (occurrence/media_asset → 403, annotation/individual allowed on token path).
- **Serializer unit:** annotation emits parent ACL; individual emits unions (incl. anon-owner → `publiclyReadable=true`); indexed gate matches live `canUserAccessMarkedIndividual` for owner/approved-collab/orgAdmin/rejected-collab/public/anonymous cases.
- **Integration (real OpenSearch — the decisive proof):** seed encounters with mixed ACLs + their annotations + a shared individual spanning multiple owners; with a non-admin token assert: (a) annotation hits + totals are gated by visible encounters; (b) individual gate = ≥1 visible encounter, hits scoped, leaky aggregates absent from the response, identity present; (c) an individual the user can't see → absent (or stub); (d) admin token → unscoped. Mirrors the live `testy1` proof, extended to the two indices.

## Scope

**In:** token reads for `individual` + `annotation`; denormalized indexing (serializers + mappings) + extended sync triggers; anonymous-owner `publiclyReadable` correction; `SearchApi` index-aware ACL filter + individual aggregate-strip; corrective-reindex runbook; tests.

**Out (Spec B / later):** detection-via-service-account-token; nested-kNN ACL injection on the annotation embeddings; `occurrence` and `media_asset` token exposure; any write/agentic actions.

**Carried-forward follow-ups (unchanged):** the global session-path metadata leak (separate security PR); orgAdmin `viewUsers` coverage verification — now also load-bearing for the individual `viewUsers` union.

## Open questions for review

1. **Keep/strip field list for individuals** — confirm the exact split against the current `MarkedIndividual.opensearchDocumentSerializer` output; decide whether `sex`/`timeOfBirth`/`timeOfDeath` are "identity" (kept) or sensitive (stripped). Default: kept (matches the page showing them to anyone who can access the individual).
2. **Anonymous-owner vs invalid-owner reconciliation** — confirm A's current handling so anonymous-owned encounters become `publiclyReadable=true` (world-readable) while invalid/deleted-owner stays admin-only/fail-closed; ensure the two cases aren't conflated in the serializer.
3. **Sync-trigger guarantee level** — reuse A's queue/bulk path; confirm acceptable latency for child-index ACL propagation (the live `canUserAccess`/B2 backstop is encounter-only; individuals/annotations rely on the indexed gate, so propagation latency = visibility latency).
4. **Branch/PR strategy** — stack Spec A on the D branch, or wait until A/B/D land on `main` and branch fresh?
