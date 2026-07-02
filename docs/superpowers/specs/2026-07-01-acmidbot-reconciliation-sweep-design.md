# AcmIdBot Reconciliation Sweep — Design

**Date:** 2026-07-01
**Status:** Approved (pending spec review)
**Branch:** `feature/acmidbot-reconciliation-sweep`

## Problem

`AcmIdBot.fixAcmIds()` heals MediaAssets that never got registered with WBIA. Its
second query only looks at Encounters submitted in the last 24 hours with
`asset.acmId == null`. This misses two things:

1. **Scope.** The set we actually care about is every MediaAsset that could be used
   in matching — i.e. assets backing an Annotation with `matchAgainst == true` — not
   just yesterday's submissions.
2. **Desync detection.** Wildbook now owns acmId assignment (the MediaAsset
   constructor defaults `acmId = getUUID()`; the v2 detection path sets it) and tells
   WBIA which UUID to use. An asset can therefore hold a plausible acmId that WBIA
   never actually registered — invisible to any `acmId == null` query. Only asking
   WBIA "do you know this UUID?" can find these.

## Verified facts (code archaeology)

- **Matchable-candidate definition** (`Annotation.java:1600–1602`): the OpenSearch
  indexer and `Annotation.getMatchingSetQuery` both require
  `matchAgainst == true && acmId != null`.
- **WBIA existence probe exists:** `GET /api/image/rowid/uuid/?uuid_list=[...]`
  (`wildbook-ia/wbia/control/manual_image_funcs.py:1691`,
  `get_image_gids_from_uuid`) — batch of image UUIDs in, parallel list of rowids
  out, `null` where the UUID is unknown. (The tidier `/api/image/uuid/missing/`
  exists but its `@register_api` is commented out — not usable over REST.)
- **Wildbook already has the calling pattern:** `IBEISIA.iaImageSetIdFromUUID()`
  (`IBEISIA.java:2859`) hits the sibling `/api/imageset/rowid/uuid/` with
  `RestClient.get` + `iaURL` + `toFancyUUID`, treating `optInt(0, -1) == -1` as
  not-found.
- **Wildbook-assigns-UUID registration is supported end to end:**
  `POST /api/image/json/` (`add_images_json`, `apis_json.py:59`) accepts an optional
  `image_uuid_list` which flows into `add_images(image_uuid_list=...)`
  (`manual_image_funcs.py:464–481`), overriding the hash-computed UUID with the
  caller's.
- **Gap:** `WildbookIAM.sendMediaAssets()` today does NOT send `image_uuid_list`; it
  lets WBIA hash-derive the UUID and rectifies acmId afterward
  (`AcmUtil.rectifyMediaAssetIds`). Under Wildbook-owned UUIDs, a heal that omits
  `image_uuid_list` could register the image under a hash UUID different from the
  probed acmId — the sweep would then flag that asset missing forever.

## Design

### 1. Query changes in `AcmIdBot.fixAcmIds()`

- **Query 1 (unchanged):** completed-ImportTask healing stays as the recency
  fast-path for fresh bulk imports.
- **Query 2 (replaced):** the 24-hour Encounter query is removed. In its place, a
  paged sweep over the matchable set:

  ```
  Feature where annot.matchAgainst == true && asset.id > <cursor>
  VARIABLES org.ecocean.Annotation annot
  ordering: asset.id ascending
  ```

  Each run takes a page of **10,000 distinct MediaAssets** (deduped in Java — one
  asset can carry several Features). No `acmId == null` filter: the sweep considers
  the entire matchable set, because desync is not detectable from the DB alone.
  Assets with `isValidImageForIA == false` are excluded up front (unhealable; don't
  burn probe/heal slots every sweep).

### 2. Sweep cursor

- In-memory `static long` on `AcmIdBot`: the highest asset id processed.
- A page returning fewer than pageSize assets ⇒ sweep complete ⇒ cursor resets to 0
  (wrap-around; continuous background reconciliation).
- Tomcat restart resets the cursor. Accepted: probes are cheap idempotent GETs, so a
  restarted sweep merely re-verifies healthy assets.
- Full-sweep duration at Sharkbook scale (~1M matchable): ~100 runs ≈ ~1 day.
  Small installs sweep in one or two runs.

### 3. Existence probe

New method in `WildbookIAM` (alongside `iaImageIds()`):

```java
public static List<String> iaMissingImageIds(List<String> acmIds, String context)
```

- Chunks acmIds into groups of **100** (bounds URL length).
- Per chunk: `GET /api/image/rowid/uuid/?uuid_list=[<fancy UUIDs>]` via the existing
  `iaURL`/`RestClient.get`/`toFancyUUID` idiom.
- A `null` / `-1` rowid in the response marks that acmId as unknown to WBIA.
- Returns the list of missing acmIds.

Heal candidates for a page = probe-missing assets **plus** assets with
`acmId == null` (legacy rows; assign `acmId = getUUID()` before healing, matching
the constructor convention).

### 4. Heal path (Wildbook-assigns-UUID)

Extend `WildbookIAM.sendMediaAssets` with an overload:

```java
public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst,
                                  boolean sendImageUuids)
```

- When `sendImageUuids` is true, the POST body includes
  `image_uuid_list = [asset.getAcmId() ...]` parallel to `image_uri_list` — the
  verified `add_images_json` signature where the caller dictates the UUID.
- The sweep calls it with `checkFirst=false` (we just probed; skip the redundant
  full-list `iaImageIds()` fetch) and `sendImageUuids=true`.
- Existing behavior of the two-arg call is untouched (other callers unaffected).
- `AcmUtil.rectifyMediaAssetIds` still runs on the response; with caller-supplied
  UUIDs it should be a no-op confirmation.
- Heals per run remain capped by the existing `maxFixes = 500`.

### 5. Error handling

- WBIA unreachable / a probe chunk throws ⇒ log and end the run **without advancing
  the cursor** (same page retried next run). Prevents silently skipping a page.
- Individual heal failures: log, continue with the rest; the next full sweep retries
  naturally.
- Existing summary-logging conventions kept (candidate counts, probes performed,
  missing found, heals sent/succeeded).

### 6. Constants

| Constant | Value | Meaning |
|---|---|---|
| `SWEEP_PAGE_SIZE` | 10,000 | assets examined per 15-minute run |
| `PROBE_CHUNK_SIZE` | 100 | UUIDs per `/api/image/rowid/uuid/` GET |
| `maxFixes` | 500 (existing) | max successful heals per run |

Plain constants for now; promote to config properties only if a deployment needs
tuning.

## Out of scope (follow-ups)

- Making **all** `sendMediaAssets` callers pass `image_uuid_list` (the global
  ownership-model fix). This design only adds the opt-in overload used by the sweep.
- Exposing WBIA's `/api/image/uuid/missing/` endpoint (would simplify the probe to
  one purpose-built call).
- Durable (DB/file) cursor persistence — revisit only if restart-induced re-sweeps
  prove costly in practice.

## Testing

- Unit tests for the probe-response parsing (missing-UUID extraction, null/`-1`
  handling, chunking boundaries).
- Unit test that the `sendImageUuids` overload builds `image_uuid_list` parallel to
  `image_uri_list` and that the two-arg path is byte-identical to before.
- Sweep-logic tests (cursor advance, wrap-around, dedup, cap interaction) — extract
  the page-processing step into a testable method rather than testing the scheduler.
