# Restore matchability of spot-crop (unity) annotations on the React Encounter page

**Date:** 2026-07-01
**Target:** `main` (GitHub Milestone 10.12)
**Type:** Bug fix (React regression introduced April 2026)

## Problem

On the React Encounter page, a spot-mapping crop annotation displays with no
annotation boundary and a grayed-out "New Match" button, even though the
annotation is genuinely matchable. The same annotation shows as matchable in
`obrowse.jsp` and satisfies the backend match-eligibility rule.

### Root cause (confirmed end-to-end)

Spot-mapping crops are created by `SubmitSpotsAndImage.java`:

```java
MediaAsset crMa = store.create(params);              // :98  the crop = its own MediaAsset
Annotation ann = new Annotation(speciesString, crMa); // :122 whole-image "unity" annotation
ann.setMatchAgainst(true);                            // :123 deliberately matchable
```

`new Annotation(String, MediaAsset)` (`Annotation.java:117-120`) is explicitly the
"trivial" constructor — it builds a single **unity feature** (`FeatureType == null`)
covering the whole cropped image. So these annotations are, by design, both
"trivial" (whole-image) **and** matchable (`matchAgainst=true`, `acmId` set).

The React Encounter page determines matchability from a **geometry proxy** computed
live in `Encounter.opensearchDocumentSerializer()` — `isTrivial` and the bounding
box — and never consults the real fields (`matchAgainst`, `acmId`), which are not
even serialized in the encounter API response. `Annotation.isTrivial()`
(`Annotation.java:457-468`) returns `true` for any unity feature, so the
April-2026 filter `!isTrivial && bbox>0` (commits `eb795ae45d`, `33b08f860a`)
drops the annotation.

The DB row for the reported annotation confirms this exactly: unity feature
(`FEATURE.TYPE_ID_OID` NULL), `ANNOTATION.WIDTH/HEIGHT = 0`, `matchAgainst=true`,
`acmId` set, MediaAsset = the crop.

### Scope narrowing

- The **downstream match already works**: `NewMatchStore.annotationIds`
  (`NewMatchStore.js:106-116`) filters only by `encounterId` — not `isTrivial`/bbox —
  so once the button is clickable, the match payload already includes this annotation.
- Therefore the **only load-bearing bug** is the button gate
  (`hasNonTrivialAnnotations`), which reads the filtered `encounterAnnotations`
  getter that drops `isTrivial`.
- The **missing boundary rectangle is cosmetic and out of scope**: a whole-image
  crop has no sub-region to outline.

## Approach

Stop using the geometry proxy as the matchability gate. Serialize the real
signal (`matchAgainst`, `acmId`) into the encounter API, and gate the New Match
button on it — mirroring the backend's exact eligibility rule
(`matchAgainst=true AND acmId IS NOT NULL`).

This is strictly more correct than the current logic: genuine placeholder
annotations have `matchAgainst=false` and stay hidden, while spot-crop
annotations (and any other legitimately matchable whole-image annotation)
reappear.

## Backend change

`Encounter.opensearchDocumentSerializer()` — annotation loop (~`Encounter.java:4464-4484`).
Add two fields inside the per-annotation object:

```java
jgen.writeBooleanField("matchAgainst", ann.getMatchAgainst());
jgen.writeStringField("acmId", ann.getAcmId());
```

Notes:
- Served **live** via `jsonForApiGet` → `opensearchDocumentAsJSONObject`
  (`Base.java:302-315` builds the JSON fresh from the object each call).
  **No reindex and no OpenSearch mapping change required** — the fields appear on
  the next API call.
- Additive and backward-compatible. The same serializer also builds the indexed
  encounter document, so the fields will additionally land under the nested
  `annotations` array on the next reindex via dynamic mapping — additive, not
  queried on. (Codex to sanity-check the nested `annotations` mapping.)

## Frontend changes

1. **New getter** `EncounterStore.hasMatchableAnnotations`
   (`frontend/src/pages/Encounter/stores/EncounterStore.js`). Reads the **raw**
   `encounterData.mediaAssets[selectedImageIndex].annotations` (NOT the filtered
   `encounterAnnotations` getter), filters `encounterId === encounterData.id`, and
   returns `.some(a => a.matchAgainst && !!a.acmId)`.

2. **Delegation** — `ImageModalStore` (Encounter,
   `frontend/src/pages/Encounter/stores/ImageModalStore.js`) delegates
   `hasMatchableAnnotations` to `encounterStore` (mirrors the existing
   `encounterAnnotations` delegation). The SearchPages `ImageModalStore`
   (`frontend/src/pages/SearchPages/stores/ImageModalStore.js`) gets an equivalent
   getter (computed from its own `encounterData`, or a safe `false` default) so the
   shared `ImageModal` never reads `undefined`.

3. **`ImageCard.jsx:107`** — replace the local `hasNonTrivialAnnotations`
   (filtered getter + redundant re-filter) with `store.hasMatchableAnnotations`.
   Update its three usages (~793, 804, 806).

4. **`ImageModal.jsx:216`** — replace with `imageStore.hasMatchableAnnotations`.
   Update its two usages (~1109, 1111).

### Left untouched (deliberately)

- The `encounterAnnotations` getter (`EncounterStore.js:601-613`) — feeds
  edit/select/task-id/`currentAnnotation` flows; changing its filter would ripple
  broadly.
- The boundary-drawing `rects` filter (`ImageCard.jsx:124-128`) — cosmetic.
- `NewMatchStore.annotationIds` — already includes the annotation.

### Naming

Rename `hasNonTrivialAnnotations` → `hasMatchableAnnotations` in both components;
the old name is now misleading.

## Data flow (after fix)

encounter API (`matchAgainst`+`acmId` per annotation) →
`EncounterStore.hasMatchableAnnotations` → New Match button enabled →
`MatchCriteriaModal` → `NewMatchStore.annotationIds` (already includes the
annotation) → match runs.

## Testing

- **Backend:** assert `opensearchDocumentSerializer` emits `matchAgainst` and
  `acmId` for each serialized annotation (extend the existing Encounter
  serialization test if present; otherwise add a focused one).
- **Frontend (jest):**
  - `EncounterStore.test.js`: `hasMatchableAnnotations` is `true` for a
    `matchAgainst + acmId` annotation **even when `isTrivial` and bbox is empty**;
    `false` when `matchAgainst` is false or `acmId` is missing; `false` for
    annotations belonging to a different `encounterId`.
  - `ImageCard.test.js`: New Match enabled/disabled tracks
    `hasMatchableAnnotations`.
  - `ImageModal.test.js`: same for the modal button.
- **CI caveat:** jest failures are invisible in CI (continue-on-error). Run jest
  locally to verify.

## Risks

- Additive API/index fields — backward compatible.
- Rename touches a handful of lines across two components.
- SearchPages `ImageModalStore` must expose the new getter (or a safe default) to
  avoid `undefined` in the shared `ImageModal`.

## Out of scope

- Drawing a boundary for whole-image crops (cosmetic).
- `SubmitSpotsAndTransformImage.java` (per user instruction — it builds a real
  bounding-box feature and does not set `matchAgainst`; not the path that created
  the reported annotation).
