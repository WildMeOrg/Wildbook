# Token-Scoped Individual + Annotation Reads (Spec A) — Design

**Date:** 2026-06-07
**Status:** Draft — incorporated Codex design review (2026-06-07, verified against real source; FIX-FIRST findings folded). Pending user review.
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
| `annotation` (new) | `publiclyReadable`, `submitterUserIds`, `viewUsers` | **union over its parent encounter(s)** |
| `individual` (new) | `publiclyReadable`, `submitterUserIds`, `viewUsers` | **union over member encounters** |

**Both child indices use union-over-related-encounters** (Codex High — an annotation is *not* provably 1:1 to one encounter: `Annotation.findByAnnotation`/`findByMediaAsset` can return zero or multiple parent encounters, `Annotation.java:218,1330`; `Encounter.java:3650`). So annotation ACL is the union over **all** its parent encounters, exactly like individual over member encounters — usually a single parent, but correct for 0/many:
- `publiclyReadable` = `OR` over related encounters' `publiclyReadable` (any related encounter world-readable). **A related encounter set of size 0 → fail closed (admin-only)** for annotations; for **individuals, 0 member encounters → world-readable** (matches `canUserAccessMarkedIndividual` returning true for an encounterless individual — Codex Medium).
- `submitterUserIds` = the **set** of related encounter submitter UUIDs.
- `viewUsers` = the **union** of related encounters' `viewUsers`.

Then: *user can access* ⟺ `publiclyReadable` OR `user ∈ submitterUserIds` OR `user ∈ viewUsers` ⟺ ∃ related encounter the user can access. ✔ matches the live gate (`canUserAccessMarkedIndividual` / per-encounter `canUserAccessEncounter`).

**Single ACL source — no divergence (Codex Medium).** All three indices must derive these fields from **one** centralized computation over **live** encounter state — the same logic Artifact A established (`computeViewUsers` + the public/submitter determination). The annotation/individual serializers must call that same method on their related encounter(s) rather than reading a possibly-stale denormalized value, so the encounter, annotation, and individual ACLs cannot drift. (A's `viewUsers` is written by a bulk permissions pass; the child serializers must invoke the identical computation, not assume the parent doc is already current.)

**Token-path enforcement** (in `SearchApi`, building on D): for a non-admin token request, inject the ACL pre-filter using *that index's* field names, before execution. **Admin token → no filter** (universal access). Token-exposed indices become **`encounter`, `annotation`, `individual`**; `occurrence` and `media_asset` remain **403**. The non-token/session path is **unchanged**.

**Fail-closed during reindex:** until the corrective reindex populates the new fields, they are simply absent on child docs → the pre-filter matches nothing → a non-admin sees *nothing* on those indices. A partial/in-progress reindex degrades to "too restrictive," never "leaky."

**Universal ACL-field stripping from responses (Codex High).** Denormalizing the ACL onto annotation/individual means those internal fields (`publiclyReadable`, `submitterUserId`, `submitterUserIds`, `viewUsers`, and `editUsers` if present) would otherwise be returned in `_source` — and the **session** path returns raw `_source` for annotation/individual today (`OpenSearch.sanitizeDoc:963` returns the doc unchanged for those indices). So `sanitizeDoc` must strip these ACL fields from **every** returned doc, for **all three indices, on both the token and session paths** — independent of, and in addition to, the token-only aggregate-strip for individuals. (D already removes `viewUsers` from encounter responses; generalize that to a universal ACL-field scrub.)

## Read surfaces

### Annotation (`POST /api/v3/search/annotation`, token path)
Each annotation doc carries the **union** ACL over its parent encounter(s) (`publiclyReadable`/`submitterUserIds`/`viewUsers`; 0 parents → admin-only/fail-closed). The token path injects the **same** `should`-pre-filter (a non-admin sees only annotations whose parent encounter they can access; admin bypasses). **Embeddings are returned as-is** (deemed not sensitive). The annotation doc is otherwise gated **wholesale** by the encounter ACL — no content redaction — except the internal ACL fields are removed by the universal ACL-field scrub (above). This also gives detection (Spec B) a real annotation read surface.

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

2. **Allowlist sanitizer (Codex High — not a denylist).** A token-path sanitizer keeps **only** an explicit allowlist of individual-identity fields and **drops everything else** — so any present-or-future serializer field that's derived from the full member-encounter set is hidden by default (a denylist would silently miss fields like `cooccurrenceIndividualIds`, `numberMediaAssets`, `numberOccurrences`, `MarkedIndividual.java:2813,2839,2858`).

   | Keep (allowlist — identity attributes) | Everything else → dropped |
   |---|---|
   | `id`, `displayName`, `names`/`nameMap`, `sex`, `taxonomy`, `timeOfBirth`, `timeOfDeath` | all cross-encounter aggregates: `encounterIds`, `numberEncounters`, `users`, `locationGeoPoints`, `numberLocations`, `maxYearsBetweenResightings`, `socialUnits`, `relationships`, `cooccurrenceIndividualMap`/`cooccurrenceIndividualIds`, `numberMediaAssets`, `numberOccurrences`, occurrence-derived counts, **and any field not in the keep-list** |

   The allowlist is pinned against the current `MarkedIndividual.opensearchDocumentSerializer` output during implementation, and a test asserts **every** emitted serializer field is either in the keep-list or dropped (so adding a serializer field later can't silently leak). `sex`/`timeOfBirth`/`timeOfDeath` are individual-level attributes Wildbook shows to anyone who can access the individual (page parity), so they are kept — see Open Question 1 for the `names`/`sex`/`taxonomy` "contributed by a hidden encounter" residual.

**Two-hop richness:** the agent obtains the *per-viewer* sighting list, locations, dates, co-occurrences for an individual by querying `POST /api/v3/search/encounter` with `{"query":{"term":{"individualId":"<id>"}}}` — already ACL-scoped. The rich record is assembled across two indices, each enforcing the ACL independently; nothing leaks and individual search keeps full query expressiveness.

## Indexing & sync

**Serializer changes:**
- **Annotation serializer** — emit the parent encounter's `publiclyReadable`, `submitterUserId`, `viewUsers`.
- **Individual serializer** (`MarkedIndividual.opensearchDocumentSerializer`) — in the existing all-member-encounters pass, also compute and emit `publiclyReadable` (OR), `submitterUserIds` (set), `viewUsers` (union).
- **Mappings** are additive: add the new fields (boolean / keyword) to the existing annotation + individual index mappings (OpenSearch allows adding fields to a live mapping); no drop/recreate required.

**Sync triggers (correctness-critical) — two distinct trigger classes:**

*(i) ACL-change triggers.* Artifact A reindexes an encounter when its ACL changes (collaboration approve/reject, orgAdmin role change, org membership, user delete/rename/consolidation). Spec A **extends those same trigger points** so the encounter's annotations and its individual are also enqueued. Without this, a revoked viewer lingers on the child indices — the bug class A hardened, now for two more indices.

*(ii) Membership-change triggers (Codex High — easy to miss).* The union fields also change when an encounter's *relationship* changes, even if no ACL changed: encounter↔individual reassignment, encounter removal from an individual, and individual **merge/split**. On any such event we must reindex **both the old and the new individual** (capture both IDs *before* the mutation — e.g. `IndividualRemoveEncounter.java:60`, `Encounter.setIndividual`/`MarkedIndividual.removeEncounter`/merge paths), and the affected annotations. Reindexing only "its individual" after the fact misses the stale **old** individual whose union must shrink. Enumerate and cover: encounter added to / removed from / reassigned between individuals, individual merge, individual split, encounter delete/unindex.

Use the existing bulk/queue path; document any best-effort-vs-guaranteed nuance as A did.

**Parity edge — anonymous-owned vs invalid-owner (Codex Medium — use the right predicate).** Wildbook's live gate grants **anonymous-owned** encounters to everyone, where "anonymous" is precisely `User.isUsernameAnonymous(submitterID)` — `null`, blank, `"N/A"`, or `"public"` (`User.java:392`, `Collaboration.java:452-458`, `Encounter.isPubliclyReadable` `Encounter.java:4107`). The indexer must mark those `publiclyReadable=true`. It must **NOT** treat a non-anonymous owner whose `User` lookup happens to be `null` (deleted/invalid) as public — that stays **admin-only/fail-closed** (A's existing invalid-owner handling, `Encounter.java:4398`). So: use `User.isUsernameAnonymous(submitterID)` / `Encounter.isPubliclyReadable()`, never `getSubmitterUser()==null`, to avoid accidentally world-readabling a genuinely-private encounter. This correction belongs in the single ACL source so encounter, annotation, and individual all inherit it.

**Equivalence test (extends A's cross-check).** Add the analogue of A's indexed-vs-live cross-check: for a sample of individuals/annotations, the indexed gate decision matches live `Collaboration.canUserAccessMarkedIndividual` / `canUserAccessEncounter`.

**One-time corrective reindex.** A runbook step (analogue of A's `viewUsers` corrective pass), run once at deploy, in order: (1) encounter anon-owner correction (or full encounter pass), (2) full annotation reindex (**the long pole** — several annotations per encounter; can be 100K+ on large installs), (3) full individual reindex. After this, the extended triggers keep fields current incrementally. Until it runs, child-index token reads fail closed (non-admin sees nothing there).

## `SearchApi` changes

Small, building on D's structure:
- Index gate flips from "encounter-only" to a token **allowlist `{encounter, annotation, individual}`**; `occurrence`/`media_asset` still 403.
- Generalize `OpenSearch.applyEncounterAclFilter(query, uuid)` → `applyAclFilter(query, uuid, indexName)` that selects field names per index (`submitterUserId` for encounter/annotation; `submitterUserIds` for individual; `publiclyReadable`/`viewUsers` common). Same `bool{must:[orig], filter:[should…]}` shape, injected before `queryPit`, admin-bypassed.
- **Universal ACL-field scrub** (all indices, all paths): `sanitizeDoc` removes `publiclyReadable`/`submitterUserId`/`submitterUserIds`/`viewUsers`/`editUsers` from every returned doc (generalizes D's encounter `viewUsers` removal).
- **Individual allowlist sanitizer** (token, non-admin): keep only the allowlisted identity fields, drop everything else (see Read surfaces). Annotation hits pass through whole (embeddings included, ACL fields scrubbed). Encounter unchanged beyond the universal scrub.
- **Resolve the effective index BEFORE the annotation-admin-only gate (Codex Medium).** Today the non-token `"annotation" → admin-only` check runs before a stored query's real `indexName` is resolved, so a stored query over the annotation index could slip past it. Compute the effective index first (direct path index, or the loaded stored query's `indexName`), then apply: on the **non-token/session** path the annotation-admin-only gate uses the effective index; on the **token** path the ACL filter governs instead.
- Stored-query owner check, method allowlist, fail-closed-unmarked, verified-context binding — **unchanged** from D; already per-index.

## Testing

- **Unit:** `applyAclFilter` builds the correct clause per index (field names: `submitterUserId` for encounter, `submitterUserIds` for annotation/individual); index allowlist (occurrence/media_asset → 403; annotation/individual allowed on token path); the **universal ACL-field scrub** removes the ACL fields from encounter, annotation, and individual responses on **both** token and session paths; the effective-index-first ordering blocks a session annotation stored-query from bypassing the admin gate.
- **Allowlist-completeness test (Codex High):** assert that for the current `MarkedIndividual.opensearchDocumentSerializer` output, **every** emitted field is either in the keep-allowlist or dropped by the sanitizer — guards against a future serializer field silently leaking.
- **Serializer unit:** annotation emits the **union over parent encounter(s)** (and **0 parents → admin-only**); individual emits unions (and **0 member encounters → `publiclyReadable=true`/world-readable**); anonymous-owned (`isUsernameAnonymous`) → `publiclyReadable=true` while invalid/deleted non-anonymous owner → fail-closed; indexed gate matches live `canUserAccessMarkedIndividual` / `canUserAccessEncounter` for owner/approved-collab/orgAdmin/rejected-collab/public/anonymous/zero-encounter cases (the single-ACL-source guarantees encounter==annotation==individual agreement).
- **Sync-trigger tests (Codex High):** encounter reassigned between individuals → **both** old and new individual reindexed (old union shrinks); individual merge/split → affected individuals reindexed; encounter ACL change → its annotations + individual reindexed.
- **Integration (real OpenSearch — the decisive proof):** seed encounters with mixed ACLs + their annotations + a shared individual spanning multiple owners; with a non-admin token assert: (a) annotation hits + totals gated by visible encounters, no ACL fields in `_source`; (b) individual gate = ≥1 visible encounter, hits scoped, only allowlisted identity fields present (no aggregates, no ACL fields); (c) an individual the user can't see → absent; (d) a zero-encounter individual → visible to anyone; (e) admin token → unscoped. Mirrors the live `testy1` proof, extended to the two indices.

## Scope

**In:** token reads for `individual` + `annotation`; denormalized indexing (serializers + mappings) + extended sync triggers; anonymous-owner `publiclyReadable` correction; `SearchApi` index-aware ACL filter + individual aggregate-strip; corrective-reindex runbook; tests.

**Out (Spec B / later):** detection-via-service-account-token; nested-kNN ACL injection on the annotation embeddings; `occurrence` and `media_asset` token exposure; any write/agentic actions.

**Carried-forward follow-ups (unchanged):** the global session-path metadata leak (separate security PR); orgAdmin `viewUsers` coverage verification — now also load-bearing for the individual `viewUsers` union.

## Open questions for review

1. **Identity allowlist + the "hidden-encounter-contributed attribute" residual (Codex Medium/Low).** Confirm the allowlist against the current serializer. Note: `names`/`nameMap`, `sex`, and `taxonomy` are individual-level fields that can be *contributed by a member encounter the viewer can't see*, yet Wildbook's individual page shows them wholesale once the individual is visible (`MarkedIndividual.java:2738`, `individuals.jsp:557`). Default = **keep** (exact page parity). Decision for the reviewer: accept this page-parity residual (a name/sex/taxonomy sourced from a hidden encounter is visible), or add a stricter mode that filters name entries to those from visible encounters (more faithful to confidentiality, less faithful to the page, materially more work). Recommend: keep (page parity), document the residual.
2. **Anonymous-owner vs invalid-owner reconciliation** — confirm A's current handling so anonymous-owned encounters become `publiclyReadable=true` (world-readable) while invalid/deleted-owner stays admin-only/fail-closed; ensure the two cases aren't conflated in the serializer.
3. **Sync-trigger guarantee level** — reuse A's queue/bulk path; confirm acceptable latency for child-index ACL propagation (the live `canUserAccess`/B2 backstop is encounter-only; individuals/annotations rely on the indexed gate, so propagation latency = visibility latency).
4. **Branch/PR strategy** — stack Spec A on the D branch, or wait until A/B/D land on `main` and branch fresh?
