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

**`bbox` is expressed in the coordinate space given by `imageWidth`×`imageHeight`** — the annotation
asset's own (reliably-stored) pixel frame, the space `Annotation.getBbox()` uses. The served
`imageUrl` is an access-controlled derivative whose *actual* pixel size may differ from
`imageWidth`/`imageHeight` (e.g. a downscaled `_master`). **The consumer fetches `imageUrl`, reads its
real dimensions, and scales `bbox` by `realW/imageWidth`, `realH/imageHeight` before cropping** —
typically a no-op (factor 1.0) since `_master` usually equals the source size. `theta` is the
annotation's rotation; the axis-aligned `bbox` bounds the region and the agent may rotate the crop by
`theta` about the bbox center for an upright view.

> **Why the consumer scales, not the server (design decision, validated live):** Wildbook extracts
> image metadata (dimensions) on the **annotation asset**, but does **not** reliably populate it on
> the generated `_master`/`_mid` **derivative** children — so `derivative.getWidth()` is often `0`. A
> server that scaled the bbox into the *derivative's* stored dims would therefore omit nearly every
> annotation (confirmed on flakebook: 0/60 resolved). The server instead uses only data it can trust —
> the annotation asset's dimensions and Wildbook's own `safeURL` (which already walks to the
> `_original` and masks the upload) — and the consumer, which holds the fetched image's true pixels,
> does the final scale.

The endpoint:

1. runs **Spec A's token-ACL-filtered annotation query** over exactly the requested IDs (with query
   `size` = the de-duplicated ID count — **not** SearchApi's default 10) to determine the visible
   subset (single source of ACL truth — Approach A);
2. for each visible ID, loads the `Annotation` via `Shepherd`, requires its asset `LocalAssetStore`-backed,
   reads the annotation asset's own dimensions, clamps `getBbox()` to them (no scaling), and obtains
   the servable URL (serve the annotation asset's own `webURL` when it is not the raw `_original`,
   else a `_master`/`_mid` child of it) — see *Servable URL selection*;
3. returns the array. Non-visible / unknown / unresolvable IDs (source not local, dimensions ≤0, null
   bbox, no servable URL) are simply **absent** (fail-closed; no existence oracle).

Admin tokens bypass the filter (consistent with Spec A). No new index fields, no image proxy, no UI.

### Why Approach A (reuse the Spec A gate)

The ACL decision reuses the already-reviewed, already-deployed token-filtered annotation query rather
than introducing a second permission check. This avoids the dual-writer/dual-evaluator drift that has
repeatedly bitten the `viewUsers` ACL work. The resolve endpoint is a thin layer: *"given IDs the
token already passes the search gate for, hand back URL + bbox."*

---

## Data flow

1. **Token-path enforcement.** Require `request.getAttribute(TOKEN_AUTH_ATTR) == true`; otherwise
   `401` — no session fallback. Take `context` **only** from the filter-verified `TOKEN_CONTEXT_ATTR`;
   if it is null/blank → `401`. Unlike `SearchApi` (which still serves session traffic and so falls
   back to `ServletUtilities.getContext`), this endpoint **never** calls `getContext` — a caller must
   not be able to steer context via `?context=`/cookie/host.
2. Validate body **before constructing a `Shepherd`**: parse JSON (unparseable → `400`);
   `annotationIds` present, is an array, non-empty, size ≤ 100 → else `400`. De-duplicate IDs. No DB
   or OpenSearch work happens until the body is valid.
3. **Visibility gate:** build the Spec A token-ACL annotation query (`applyAclFilter`, which wraps the
   query in `bool.must` + ACL in `bool.filter` — composes correctly), with the user query being a
   `terms` filter on `_id ∈ annotationIds`. **Set the query `size` to the de-duplicated ID count
   (Codex Medium)** — SearchApi's default is 10, which would silently drop visible IDs beyond the
   first 10. Run it; returned hit IDs = the visible subset. Admin token → skip the ACL filter, treat
   all requested IDs as candidates. No DB work for IDs that fail the gate.
4. For each visible ID, via a read-only `Shepherd`:
   - load `Annotation`; get its source `MediaAsset` (`getMediaAsset()` — the asset the bbox feature
     lives on); require it `LocalAssetStore`-backed; read its dimensions `(W, H)` (reliably stored on
     the annotation asset — see the metadata note above);
   - `clampBbox(getBbox(), W, H)` → `[x, y, w, h]` clamped to `[0,W]×[0,H]` (**no scaling**; bbox stays
     in the source frame);
   - obtain the servable URL (see *Servable URL selection*);
   - read `theta`, `viewpoint`, parent `encounterId`/`individualId` (first parent — see *Multi-parent*),
     `methodVersion`(s);
   - emit `{id, imageUrl, imageWidth:W, imageHeight:H, bbox, theta, viewpoint, ...}`.
5. Return the array; rollback + close the `Shepherd` transaction (read-only).

### Servable URL selection

The served image must be in the **same coordinate frame as the bbox** (the annotation asset's frame),
so the consumer's scale-only transform is correct (a scale cannot recover a crop offset). Therefore:

- If the annotation asset is **not** the raw upload (`!hasLabel("_original")`) it *is* the bbox frame —
  serve its own `webURL()` directly. The served image equals the source asset, so `imageWidth/Height`
  are its real pixels and the consumer scale is exactly 1.0.
- If the annotation asset **is** the `_original` (raw upload), we must not serve it directly — serve a
  `_master` (else `_mid`) **child of it** via `bestSafeAsset(myShepherd, null, type)`. That child is an
  aspect-preserving scale of the *same* frame, so the consumer scales by `realPixels/originalDims`.
- The chosen asset must be `LocalAssetStore`-backed and not `_original`; any lookup error or a null URL
  → **omit** (fail-soft, never 500 the batch). We never serve a *different-frame* asset (e.g. a
  parent's derivative for a crop child), which would make the bbox unrecoverable by scaling.

Do **not** trust `safeURL`/`bestSafeAsset` blindly: `bestSafeAsset` forces `bestType="original"` for
`URLAssetStore` and `"master"` alone does not fall back to `"mid"` — hence the explicit
`LocalAssetStore`/non-`_original` guards and the master→mid retry, and the source must be
`LocalAssetStore` (rejecting `URLAssetStore` externals and `YouTubeAssetStore` watch pages).

### Null-safety / fail-closed omission

An annotation is **omitted** from the response (not returned blank) when any of: no source MediaAsset;
source not `LocalAssetStore`; source dimensions `≤0`; null/invalid/empty bbox; no servable URL. Same
treatment as a gate miss — indistinguishable from "not visible."

---

## Error model

| Status | When |
|--------|------|
| `200` | Well-formed request + valid token. Body = resolved array; **may be empty** (none visible/exist). Never distinguishes "not visible" from "doesn't exist" — no existence oracle. |
| `400` | Malformed body: unparseable JSON, missing/empty `annotationIds`, not an array, or size > 100. Validated **before** any DB/OpenSearch work (no `Shepherd` constructed on a validation failure). |
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
- **Annotation visible but asset/bbox/dimensions/URL missing** → omitted (fail-closed).
- **Batch all-invisible** → `200` with `[]`.
- **`viewpoint` absent** → returned as `null`; **`theta` absent** → returned as `0.0` (the no-rotation
  default); `imageUrl` + source-frame `bbox` + `imageWidth`/`imageHeight` still provided.

---

## Testing

**Unit / integration (JUnit 5, message-last assertions, mirroring Spec A test style):**

- **Visibility gate honored:** non-admin token requesting {own-encounter, other-user-private,
  publicly-readable} annotations gets back only own + public; the private one is absent.
- **Admin bypass:** admin token gets all requested resolvable IDs regardless of ownership.
- **No existence oracle:** a real-but-invisible ID and a fabricated/garbage ID return the same shape
  (both absent) — assert indistinguishable.
- **Resolution payload correctness:** for a visible annotation, `viewpoint`/`encounterId`/
  `individualId`/`methodVersion` match the object.
- **Source-frame bbox (Codex High):** the returned `bbox` equals `getBbox()` clamped to the annotation
  asset's dimensions (**not** scaled), and `imageWidth`/`imageHeight` equal the annotation asset's
  dimensions — so the consumer's scale to the fetched image is correct even for a crop source.
- **Servable URL selection:** non-`_original` source asset → served via its own `webURL`; `_original`
  source → served via a `_master`/`_mid` child of it; non-`LocalAssetStore` source → omitted; null URL
  → omitted; a lookup error → omitted (fail-soft, batch still 200).
- **Clamp (Codex High):** negative-origin bbox clamps both corners (width shrinks, not preserved);
  fully-out-of-bounds or inverted bbox → omitted.
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
- Two small static helpers in `MediaResolveApi`: `safeServableUrl(src, shepherd)` (serve the
  annotation asset's own `webURL` when non-`_original`, else a `_master`/`_mid` child via
  `bestSafeAsset`; `LocalAssetStore`/non-`_original` guards; fail-soft) and `clampBbox(bbox, W, H)`
  (corner-clamp to the source frame, no scaling).
- Reuse: `OpenSearch.applyAclFilter` (annotation gate), `WildbookTokenAuthenticationFilter`,
  `Annotation.getBbox()`/`getTheta()`/`getViewpoint()`/`findEncounter()`/`getEmbeddings()`,
  `MediaAsset.getWidth()`/`getHeight()`/`getStore()`/`hasLabel()`/`bestSafeAsset()`/`webURL()`,
  `Encounter.getId()`/`hasMarkedIndividual()`/`getIndividualID()`, `Shepherd`.
- `web.xml` — register the new endpoint mapping (if not covered by an existing `/api/v3/*` dispatch).
- Tests: `MediaResolveApiTest.java` (new).
- Runbook: smoke-test addendum to `childindex-acl-reindex.md`.

This is a self-contained increment off the Spec A branch; it adds one endpoint plus the
`safeServableUrl`/`clampBbox` helpers and one test class, and changes no existing ACL or index code paths.
