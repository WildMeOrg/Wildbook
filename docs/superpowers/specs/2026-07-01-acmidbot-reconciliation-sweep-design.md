# AcmIdBot Reconciliation Sweep — Design

**Date:** 2026-07-01 (rev 2, post-Codex review)
**Status:** Approved (pending spec review)
**Branch:** `feature/acmidbot-reconciliation-sweep`

## Problem

`AcmIdBot.fixAcmIds()` heals MediaAssets that never got registered with WBIA. Its
second query only looks at Encounters submitted in the last 24 hours with
`asset.acmId == null`. This misses two things:

1. **Scope.** The set we actually care about is every MediaAsset that could be used
   in matching — i.e. assets backing an Annotation with `matchAgainst == true` — not
   just yesterday's submissions.
2. **Desync detection.** Wildbook owns acmId assignment and tells WBIA which image
   UUID to use (`sendMediaAssets` sends `image_uuid_list` = acmId, falling back to
   the asset UUID; `add_images_json` honors it). An asset can therefore hold a
   plausible acmId that WBIA never actually registered — invisible to any
   `acmId == null` query. Only asking WBIA "do you know this UUID?" finds these.

## Verified facts (code archaeology, against main)

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
  `RestClient.get` + `iaURL` + `toFancyUUID`.
- **Registration already sends Wildbook-assigned UUIDs:**
  `WildbookIAM.sendMediaAssets()` (main, lines 132–133) sends
  `image_uuid_list = (acmId != null ? acmId : UUID)` per asset;
  `add_images_json` → `add_images(image_uuid_list=...)`
  (`manual_image_funcs.py:464–481`) overrides the hash-computed UUID with the
  caller's. No new send path is needed — the heal is a plain
  `sendMediaAssets(candidates, /* checkFirst */ false)`.
- **Transaction semantics:** `Shepherd.updateDBTransaction()` = commit + begin
  (`Shepherd.java:3388`). `AcmUtil.rectifyMediaAssetIds()` mutates JDO-attached
  assets and relies on the caller to commit. `fixAcmIds()` currently ends with
  `rollbackAndClose()` and only commits inside the validate branch of `fixFeats` —
  so acmId writes from a heal can be silently rolled back today. The sweep must
  commit explicitly.

## Design

### 1. Query changes in `AcmIdBot.fixAcmIds()`

- **Query 1 (unchanged):** completed-ImportTask healing stays as the recency
  fast-path for fresh bulk imports.
- **Query 2 (replaced):** the 24-hour Encounter query is removed. In its place, a
  paged sweep over the matchable set:

  ```
  select from org.ecocean.media.Feature
  where annot.matchAgainst == true
    && annot.features.contains(this)
    && asset.id > <cursor>
  VARIABLES org.ecocean.Annotation annot
  ordering: asset.id ascending
  ```

  (Note the explicit `annot.features.contains(this)` binding — without it the
  variable is unbound; matches the existing Query 1/2 idiom.)

  Each run processes a page of up to **10,000 distinct MediaAssets**. Because
  Feature rows ≠ asset rows, paging is by distinct asset: iterate the ordered
  result, collecting distinct assets until 10,000 are gathered or the raw result is
  exhausted. **Wrap-around triggers only on raw-result exhaustion**, never on a
  post-dedup count (which would falsely end the sweep). Assets with
  `isValidImageForIA == false` are excluded (unhealable; don't burn probe/heal
  slots every sweep).

### 2. Sweep cursor

- In-memory `static long` on `AcmIdBot`: the highest asset id **processed**.
- Raw result exhausted ⇒ sweep complete ⇒ cursor resets to 0 (wrap-around;
  continuous background reconciliation).
- **maxFixes interplay:** if the heal cap (existing `maxFixes = 500`) is hit
  mid-page, the cursor advances only to the last asset actually processed, so the
  rest of the page is not deferred until the next full sweep.
- Tomcat restart resets the cursor. Accepted: probes are cheap idempotent GETs, so
  a restarted sweep merely re-verifies healthy assets.
- Full-sweep duration at Sharkbook scale (~1M matchable): ~100 runs ≈ ~1 day.
  Small installs sweep in one or two runs.

### 3. Existence probe

New method in `WildbookIAM` (alongside `iaImageIds()`):

```java
public static List<String> iaMissingImageIds(List<String> acmIds, String context)
```

- Skips/never sends null acmIds (`toFancyUUID(null)` must not happen; null-acmId
  assets are heal candidates without probing).
- Chunks acmIds into groups of **50** (~3.5 KB URL — safely under common proxy
  limits; Codex flagged 100 as borderline).
- Per chunk: `GET /api/image/rowid/uuid/?uuid_list=[<fancy UUIDs>]` via the
  existing `iaURL`/`RestClient.get`/`toFancyUUID` idiom.
- Response validation: require `status.success` and response length == request
  length; treat a `null` rowid entry as missing. On malformed response, treat the
  chunk as failed (see §5), never as "all present."
- Returns the list of missing acmIds.

Heal candidates for a page = probe-missing assets **plus** assets with
`acmId == null` (legacy rows; assign `acmId = getUUID()` before healing, matching
the constructor convention).

### 4. Heal path

- Call the **existing** `WildbookIAM.sendMediaAssets(candidates, false)` —
  `checkFirst=false` because we just probed (skips the redundant full-list
  `iaImageIds()` fetch, which is ~1M UUIDs on Sharkbook). It already sends
  `image_uuid_list` (the acmId we probed for), so a healed asset registers under
  exactly the UUID the next sweep will probe. No overload needed.
- `AcmUtil.rectifyMediaAssetIds` runs on the response as today; with
  caller-supplied UUIDs it is a no-op confirmation (and logs loudly if WBIA
  returns a different UUID).
- Heals per run capped by the existing `maxFixes = 500`.

### 5. Transactions & error handling

- **Phase separation (fixes the rollback bug):**
  1. *Read phase* — open transaction, run the sweep query, collect
     `(assetId, acmId, validity)` tuples for the page, close the transaction.
  2. *Probe phase* — HTTP only, **no transaction open**.
  3. *Heal phase* — new transaction; re-fetch the candidate assets; run the heal;
     `updateDBTransaction()` (commit) after each heal batch so rectified acmIds
     and validity flags persist. The final `rollbackAndClose()` then only discards
     the trailing empty transaction.
- **WBIA unreachable / probe chunk fails:** log and end the run without advancing
  the cursor (same page retried next run).
- **Poisoned-page guard:** a static consecutive-failure counter for the current
  cursor position. After **3** failed runs on the same page, advance the cursor
  past the page with a prominent log line, so one bad page cannot stall the sweep
  forever. (Locally-invalid UUIDs are filtered before sending, so this is a
  backstop, not the primary defense.)
- Individual heal failures: log, continue; the next full sweep retries naturally.
- Existing summary-logging conventions kept (candidate counts, probes performed,
  missing found, heals sent/succeeded).

### 6. Constants

| Constant | Value | Meaning |
|---|---|---|
| `SWEEP_PAGE_SIZE` | 10,000 | distinct assets examined per 15-minute run |
| `PROBE_CHUNK_SIZE` | 50 | UUIDs per `/api/image/rowid/uuid/` GET |
| `PAGE_FAIL_LIMIT` | 3 | failed runs on one page before skipping it |
| `maxFixes` | 500 (existing) | max successful heals per run |

Plain constants for now; promote to config properties only if a deployment needs
tuning.

### 7. Opportunistic hardening (small, in-scope)

- Guard `startServices()`/`startCollector()` against double start (static
  already-started flag per context), and have `cleanup()` actually shut down the
  scheduled executor. (Codex Minor; trivial and touches code we're editing.)

## Out of scope (follow-ups)

- Exposing WBIA's `/api/image/uuid/missing/` endpoint (would simplify the probe to
  one purpose-built call).
- Durable (DB/file) cursor persistence — revisit only if restart-induced re-sweeps
  prove costly in practice.
- Auditing other `fixFeats`-style callers for the same commit-after-rectify gap
  outside the sweep path.

## Testing

- Unit tests for probe-response parsing: missing-UUID extraction, null rowid,
  length-mismatch and `status` failure treated as chunk failure, chunking
  boundaries, null-acmId exclusion.
- Sweep-logic tests (extract page processing into a testable method rather than
  testing the scheduler): cursor advance, wrap-around only on raw exhaustion,
  distinct-asset dedup, maxFixes cursor clamp, poisoned-page skip after 3
  failures.
- Transaction test (or focused review): healed acmId survives
  `rollbackAndClose()` — i.e. commit happens in the heal phase.

## Codex review disposition (rev 1 → rev 2)

| # | Severity | Finding | Disposition |
|---|---|---|---|
| 1 | Critical | Heals rolled back by `rollbackAndClose()` | §5 phase separation + explicit commits |
| 2 | Major | JDOQL variable unbound | §1 adds `annot.features.contains(this)` |
| 3 | Major | "sendMediaAssets lacks image_uuid_list" was stale (read on wrong branch) | §4: use existing method, no overload |
| 4 | Major | Dedup vs page-size false wrap-around | §1/§2: wrap only on raw exhaustion |
| 5 | Major | Poisoned page stalls cursor forever | §5: 3-strike page skip |
| 6 | Major | maxFixes defers rest of page a full sweep | §2: cursor clamps to last processed |
| 7 | Minor | Probe response validation | §3: status + length checks, no null UUIDs |
| 8 | Minor | URL length at 100 UUIDs | §3/§6: chunk = 50 |
| 9 | Minor | Executor double-start / no shutdown | §7 opportunistic hardening |
