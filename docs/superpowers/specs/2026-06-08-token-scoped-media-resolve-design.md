# Token-Scoped Media Resolve — Design

**Date:** 2026-06-08
**Status:** Draft — incorporated Codex design review (2026-06-08, verified against real source; all High/Medium/Low findings folded). Pending user review.
**Builds on:** Spec A — token-scoped individual + annotation reads
(`2026-06-07-token-scoped-individual-annotation-design.md`) and the token-auth filter / search API
already shipped in PR #1613.

---

## Problem

The AI co-scientist's embedding sweeps surface candidate "missed matches" (two annotations whose
MiewID embeddings are suspiciously similar across different individuals). To make a sweep result
*verifiable*, a human or agent needs to **see the two annotation regions side by side** — the exact
bbox + viewpoint the embedding was computed from, not the whole photo.

Today the token API exposes `encounter`, `annotation`, and `individual`, but:

- the `annotation` index returns only a bare integer `mediaAssetId` — **no fetchable image URL**;
- the `media_asset` index is `403` (deferred);
- so a token-only agent (no web session) **cannot resolve an annotation to an image** it can display.

This design closes that gap with the minimum surface needed for visual verification.

## Goal

Let a token-bearing agent resolve a set of annotation IDs it is **already allowed to see** into the
data needed to render a side-by-side crop comparison: an access-controlled image URL plus the
annotation's bounding box and orientation metadata. The agent fetches the image and crops it itself
(no server-side cropping, no new Wildbook UI).

## Non-Goals

- Server-side crop rendering or stored crop derivatives (consumer/agent renders).
- A hosted comparison-view UI page (consumer/agent renders).
- Exposing the full `media_asset` index or its store internals.
- Privilege-tiered derivatives (master vs mid by caller) — noted as future, not built (YAGNI).
- Signed/expiring image URLs — noted as future hardening, not built (YAGNI).

---

## Security model (explicit)

Empirically verified on flakebook: Wildbook's derivative image URLs
(`/wildbook_data_dir/.../<uuid>-mid.jpg`) are **open-by-URL** — the same bytes return with no auth at
all (HTTP 200) as with credentials. They are UUID-path files served directly by the webserver; there
is no ACL check at fetch time. (`ImgFilter` covers only `/di/ImgFilter/*`, a dynamic-resize path, not
derivative serving.)

Therefore the access-control model for this feature is:

> **ACL is enforced at URL-*discovery* time, not at fetch time.** The endpoint reveals an image URL
> only for annotations the token is permitted to see. Image bytes themselves are protected solely by
> unguessable-UUID paths (a capability-URL model). You cannot fetch what you cannot discover.

**Accepted tradeoff:** a URL handed to an agent is bearer-less and never expires; if logged, cached,
or copied, anyone with the string can fetch the image indefinitely. This is *not a new* weakness — it
is exactly how Wildbook already serves every image — but this endpoint makes such URLs easier to
harvest. For the intended use (admin-token co-scientist over a research catalog) this is acceptable.
The future hardening, if sensitive imagery becomes a concern, is signed/expiring URLs.

---

## Architecture & boundary

A single new token-gated endpoint:

```
POST /api/v3/media/resolve
Authorization: Bearer <token>
Content-Type: application/json

{ "annotationIds": ["<uuid>", "<uuid>", ...] }   // 1..100 ids
```

Response — `200`:

```json
[
  {
    "id": "<annotationId>",
    "imageUrl": "https://host/wildbook_data_dir/.../<uuid>-master.jpg",
    "imageWidth": 1024,
    "imageHeight": 768,
    "bbox": [x, y, width, height],
    "theta": 0.0,
    "viewpoint": "up",
    "encounterId": "<encId>",
    "individualId": "<indivId or null>",
    "methodVersion": ["msv4.1"]
  }
]
```

**`bbox` is expressed in the pixel space of the returned `imageUrl`** (not the source asset). The
agent crops `imageUrl` directly to `bbox` with no further scaling; `imageWidth`/`imageHeight` are the
returned image's dimensions so the agent can sanity-check. `theta` is the annotation's rotation (in
the same convention Wildbook stores it); the axis-aligned `bbox` bounds the region and the agent may
rotate the crop by `theta` about the bbox center for an upright view.

The endpoint:

1. runs **Spec A's token-ACL-filtered annotation query** over exactly the requested IDs (with query
   `size` = the de-duplicated ID count — **not** SearchApi's default 10) to determine the visible
   subset (single source of ACL truth — Approach A);
2. for each visible ID, loads the `Annotation` via `Shepherd`, **explicitly selects a safe derivative
   asset** (try `_master`, then `_mid`; reject `_original` and any other/unknown label — see
   *Derivative selection* below), reads the source-asset and derivative dimensions, and **scales +
   clamps** `getBbox()` into the derivative's pixel space;
3. returns the array. Non-visible / unknown / unresolvable IDs (no safe derivative, missing
   dimensions, null bbox) are simply **absent** (fail-closed; no existence oracle).

Admin tokens bypass the filter (consistent with Spec A). No new index fields, no image proxy, no UI.

### Why Approach A (reuse the Spec A gate)

The ACL decision reuses the already-reviewed, already-deployed token-filtered annotation query rather
than introducing a second permission check. This avoids the dual-writer/dual-evaluator drift that has
repeatedly bitten the `viewUsers` ACL work. The resolve endpoint is a thin layer: *"given IDs the
token already passes the search gate for, hand back URL + bbox."*

---

## Data flow

1. **Token-path enforcement (Codex Low).** Require `request.getAttribute(TOKEN_AUTH_ATTR) == true`;
   otherwise `401` — no session fallback. Resolve `context` from the filter-**verified**
   `TOKEN_CONTEXT_ATTR` (never request-derived), exactly as `SearchApi` does (`SearchApi.java:25-32`),
   so a caller can't steer context via `?context=`/cookie/host.
2. Validate body: `annotationIds` present, is an array, non-empty, size ≤ 100 → else `400`.
   De-duplicate IDs.
3. **Visibility gate:** build the Spec A token-ACL annotation query (`applyAclFilter`, which wraps the
   query in `bool.must` + ACL in `bool.filter` — composes correctly), with the user query being a
   `terms` filter on `_id ∈ annotationIds`. **Set the query `size` to the de-duplicated ID count
   (Codex Medium)** — SearchApi's default is 10, which would silently drop visible IDs beyond the
   first 10. Run it; returned hit IDs = the visible subset. Admin token → skip the ACL filter, treat
   all requested IDs as candidates. No DB work for IDs that fail the gate.
4. For each visible ID, via a read-only `Shepherd`:
   - load `Annotation`; get its source `MediaAsset` (`getMediaAsset()` — the asset the bbox feature
     lives on) and read source dimensions `(srcW, srcH)`;
   - **select the safe derivative** (see *Derivative selection*) → the served `MediaAsset`, its
     `webURL`, and its dimensions `(dstW, dstH)`;
   - `getBbox()` → `[x, y, w, h]` in source space; **scale into derivative space**:
     `bbox' = [round(x·dstW/srcW), round(y·dstH/srcH), round(w·dstW/srcW), round(h·dstH/srcH)]`, then
     **clamp** to `[0,dstW]×[0,dstH]`;
   - read `theta`, `viewpoint`, parent `encounterId`/`individualId` (first parent — see *Multi-parent*),
     `methodVersion`(s);
   - emit `{id, imageUrl, imageWidth:dstW, imageHeight:dstH, bbox:bbox', theta, viewpoint, ...}`.
5. Return the array; rollback + close the `Shepherd` transaction (read-only).

### Derivative selection (Codex High + Medium)

Do **not** trust `safeURL`/`bestSafeAsset` to avoid originals or to fall back: `bestSafeAsset` forces
`bestType="original"` for `URLAssetStore` (`MediaAsset.java:770`), returns `this` when it already
carries the requested label (`:777`), and only checks the *exact* requested type (the `gotBest` reset
at `:790` means a missing `master` does **not** fall through to `mid`). Instead, select explicitly:

- look for a child labeled `_master`; if absent, one labeled `_mid`;
- the selected asset **must** be `_master` or `_mid` — reject `_original` and any other/unknown label,
  and reject `URLAssetStore`-backed assets (treated as public originals);
- if no `_master`/`_mid` derivative exists, **omit the entry** (fail-closed for that ID).

Both `master` (≤4096²) and `mid` (≤1024×768) are aspect-preserving resizes, so the scale factors
`dstW/srcW` and `dstH/srcH` are equal up to rounding; the spec scales each axis independently and
clamps, which is robust to either. Because the bytes are open-by-URL regardless, v1 does **not** tier
the derivative by caller privilege — every token caller that passes the discovery gate gets the same
safe (non-original) derivative.

### Null-safety / fail-closed omission

An annotation is **omitted** from the response (not returned blank) when any of: no source MediaAsset;
null/invalid bbox; no `_master`/`_mid` derivative; missing source or derivative dimensions (`≤0`,
which would make scaling undefined). Same treatment as a gate miss — indistinguishable from "not
visible." Omissions are logged server-side at debug so a catalog data problem is diagnosable without
leaking to the caller.

---

## Error model

| Status | When |
|--------|------|
| `200` | Well-formed request + valid token. Body = resolved array; **may be empty** (none visible/exist). Never distinguishes "not visible" from "doesn't exist" — no existence oracle. |
| `400` | Malformed body: missing/empty `annotationIds`, not an array, or size > 100. |
| `401` | Missing/invalid/expired token (upstream filter, same as rest of token API). |
| `500` | Genuine server fault (DB/OpenSearch unreachable) only — never per-ID resolution failure. |

**No `403` in v1.** Resolving an annotation's image is conceptually an annotation read, and Spec A
already permits *any* valid token to query the annotation index (gating happens per-document). This
endpoint follows the same rule: every valid token may call it, and per-ID visibility governs what
comes back. A `403` (endpoint-level scope denial) is reserved for a future per-token-scope model and
is not emitted in v1.

### Multi-parent annotations (Codex Medium)

An annotation can have 0 or >1 parent encounters. Spec A's `Annotation.writeAclFields`
(`Annotation.java:1344`) indexes any 0-or-many-parent annotation as **admin-only** (fail-closed), so a
non-admin token **never** resolves such an annotation — the visibility gate already excludes it. For
the admin path (which bypasses the gate) and for the returned `encounterId`/`individualId`, resolve
uses `findEncounter()` (`Encounter.java:3695`), which returns the **first** parent. This is documented
behavior: `encounterId`/`individualId` denote *a* parent of a multi-parent annotation, not the sole
one. No new ACL logic is introduced; resolve inherits Spec A's fail-closed multi-parent treatment.

### Edge cases

- **Duplicate IDs** → de-duplicated; each appears at most once.
- **Mixed visible/invisible/nonexistent** → only visible-and-resolvable subset returned; response
  order not guaranteed, so each entry carries its `id`.
- **Annotation visible but asset/bbox/derivative/dimensions missing** → omitted (fail-closed), debug-logged.
- **Batch all-invisible** → `200` with `[]`.
- **`viewpoint` absent** → returned as `null`; **`theta` absent** → returned as `0.0` (the no-rotation
  default); `imageUrl` + scaled `bbox` + `imageWidth`/`imageHeight` still provided.

---

## Testing

**Unit / integration (JUnit 5, message-last assertions, mirroring Spec A test style):**

- **Visibility gate honored:** non-admin token requesting {own-encounter, other-user-private,
  publicly-readable} annotations gets back only own + public; the private one is absent.
- **Admin bypass:** admin token gets all requested resolvable IDs regardless of ownership.
- **No existence oracle:** a real-but-invisible ID and a fabricated/garbage ID return the same shape
  (both absent) — assert indistinguishable.
- **Resolution payload correctness:** for a visible annotation, `imageUrl` is a `_master`/`_mid`
  derivative URL (never `original`); `viewpoint`/`encounterId`/`individualId`/`methodVersion` match the
  object.
- **BBox scaled into derivative space (Codex High):** for an annotation whose source asset is larger
  than its `_master`/`_mid` derivative, assert the returned `bbox` equals `getBbox()` scaled by
  `dst/src` and clamped — **not** the raw source-space bbox — and that `imageWidth`/`imageHeight` equal
  the derivative's dimensions.
- **Derivative selection (Codex High/Medium):** `_original`-only asset → omitted; `URLAssetStore`-backed
  asset → omitted; `_master` absent but `_mid` present → resolves via `_mid` (asserts the no-fallback
  `bestSafeAsset` bug is bypassed); unknown-label-only → omitted.
- **Size guard (Codex Medium):** with > 10 visible IDs in one request, **all** of them resolve (proves
  query `size` = ID count, not the default 10).
- **Null-safety omission:** annotation with no MediaAsset, null bbox, or missing dimensions is omitted, not blank.
- **Validation:** empty list, non-array, and >100 IDs each → `400`; missing/expired token or
  non-`TOKEN_AUTH_ATTR` request → `401`.
- **De-dup:** duplicate IDs collapse to one entry.
- **Multi-parent:** a >1-parent annotation is admin-only — non-admin token omits it; admin path returns
  first-parent `encounterId`/`individualId`. Malformed/garbage IDs on the admin path are omitted (not 500).

**Live smoke test** (append to `docs/superpowers/runbooks/childindex-acl-reindex.md`):
with claudio's admin token on flakebook, resolve the two salamander candidate annotations
(BGBI_22-168 ↔ BGBI_23-2716); confirm `200` with both `imageUrl`s fetchable + bboxes present. With a
non-admin token, confirm a private annotation resolves empty.

**No index/mapping changes → no reindex required** for this feature (unlike Spec A).

---

## Components / file boundary (anticipated)

- `MediaResolveApi.java` (new) — the servlet/endpoint: token-path enforcement (`TOKEN_AUTH_ATTR` +
  verified context), validation, visibility gate (sized to ID count), per-ID resolution, response.
- A small **derivative-selection + bbox-scaling helper** (in `MediaResolveApi`, or a static helper on
  `Annotation`/`MediaAsset` if cleaner): selects `_master`→`_mid`, rejects `_original`/`URLAssetStore`/
  unknown, reads source + derivative dimensions, scales + clamps the bbox. This is the one piece of
  genuinely new logic; **do not** route it through `safeURL`/`bestSafeAsset` (which the review showed
  can return originals and won't fall back).
- Reuse: `OpenSearch.applyAclFilter` (annotation gate), `WildbookTokenAuthenticationFilter`,
  `Annotation.getBbox()`/`getTheta()`/`findEncounter()`, `MediaAsset.findChildrenByLabel`/`webURL`/
  dimensions, `Shepherd`.
- `web.xml` — register the new endpoint mapping (if not covered by an existing `/api/v3/*` dispatch).
- Tests: `MediaResolveApiTest.java` (new).
- Runbook: smoke-test addendum to `childindex-acl-reindex.md`.

This is a self-contained increment off the Spec A branch; it adds one endpoint plus a derivative/bbox
helper and one test class, and changes no existing ACL or index code paths.
