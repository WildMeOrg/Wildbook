# Design: image-then-annotation WBIA registration in polling thread

## Context

The v2 WBIA registration polling thread (commit `c6ffe5d20` and follow-ups)
calls `/api/annot/json/` to register each ml-service-created annotation
with WBIA. The thread is meant to keep WBIA in sync so HotSpotter remains
available as a fallback identifier.

What works today:
- The polling JDOQL picks pending annotations (`wbiaRegistered == false
  && wbiaRegisterAttempts < 10`).
- Phase A loads a detached DTO under a short Shepherd transaction.
- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
- Phase C persists the outcome in a fresh Shepherd.

What's broken in production: the legacy v2 routing path
(`IA.intakeMediaAssetsOneSpeciesMlService`) **never tells WBIA about the
image** — it skips the legacy `WBIA.sendMediaAssets()` call because
ml-service does its own detection. By the time the annotation-registration
polling thread fires `/api/annot/json/`, WBIA has no record of the image
uuid the annotation references. WBIA returns HTTP 500 with `ValueError:
The input list image_uuid_list has invalid values (index, value): [(0,
None)]`. Annotations get marked failed and retry until `MAX_ATTEMPTS=10`,
then park.

## Goal

Make the polling thread register the image first when needed, then the
annotation. Keep it non-blocking (no foreground caller is gated on WBIA),
keep one retry counter, keep one polling thread.

## Non-goals

- Modifying `IA.intakeMediaAssetsOneSpeciesMlService` to do image
  registration at intake time. The polling thread can handle it
  retroactively, which keeps the intake fast path simple.
- Adding a parallel image-registration polling thread. Doubles the
  background-thread inventory for no benefit; the single annotation
  thread already iterates pending work at a sensible cadence.
- Adding a new JDO column for image-registration state. The existing
  `wbiaRegistered` / `wbiaRegisterAttempts` columns govern both phases
  atomically.

## Audit: what already exists

| Helper | Status | Reused as-is |
|---|---|---|
| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list, 15-min QueryCache) | Yes, indirectly — we add a strict variant alongside it |
| `WildbookIAM.sendMediaAssetsForceId(ArrayList<MediaAsset>, boolean)` | exists, batch POST to `/api/image/json/`, sends our acmId via `image_uuid_list` | The POST body shape and `toFancyUUID` wrapping logic lift line-for-line into the new DTO-based variant |
| `WildbookIAM.mediaAssetToUri(MediaAsset)` | exists, private; returns the double-encoded web URL string | Called in Phase A to capture `imageUri` into the DTO |
| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
| `AcmUtil` | exists | Not relevant — its `rectify*` utilities are for syncing acmId values, but v2 owns the acmId so no rectification needed |

## Design

### DTO extension

Extend `WildbookIAM.WbiaRegisterRequest` with four image-side fields,
populated in Phase A:

```java
public final String imageUri;             // mediaAssetToUri(ma) result
public final Double imageLatitude;        // ma.getLatitude(), nullable
public final Double imageLongitude;       // ma.getLongitude(), nullable
public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
```

`mediaAssetUuid` is not needed as a separate field because v2's
convention is `MediaAsset.acmId == MediaAsset.uuid` (commit `2a3eab63a`);
`dto.mediaAssetAcmId` already carries the value `sendMediaAssetsForceId`
puts in `image_uuid_list`.

### Phase A additions

`StartupWildbook.loadWbiaRegisterDto` (added in commit `c6ffe5d20`) already
captures the annotation-side eligibility (`mediaAsset != null &&
acmId != null && validForIdentification`). Add the image-side eligibility
in the same block:

```java
if (!WildbookIAM.validMediaAsset(ma)) {
    reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
}
```

If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
behavior — keeps the ineligible-park path consistent).

Then capture the image fields into the DTO:

```java
String imageUri = (String) WildbookIAM.mediaAssetToUri(ma);  // method returns Object; cast
DateTime dt = ma.getDateTime();
Long dateTimeMillis = (dt == null) ? null : dt.getMillis();
WbiaRegisterRequest dto = new WbiaRegisterRequest(
    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy, ann.getTheta(),
    ann.getIAClass(), name,
    imageUri, ma.getLatitude(), ma.getLongitude(), dateTimeMillis  // NEW
);
```

The c11 fix-pass refactor of Phase A is preserved (still short DB tx, no
network).

### New `iaImageIdsStrict(context)` in `WildbookIAM`

Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
Same shape:

- Honors the existing 15-min `QueryCache`.
- Throws `IOException` on fetch failure (vs. lenient variant which
  swallows and returns empty list).
- Uses a strict element parser (no silently-skipped malformed entries).

Roughly 40 lines, parallel to the annotation version. Reuses the same
`apiGetJSONArray` and `parseAnnotationIdsArrayStrict`-style helpers; the
parser helper is duplicated rather than generalized because the cache key
and endpoint path differ.

### New `sendMediaAssetByDto(WbiaRegisterRequest dto)` in `WildbookIAM`

Single-image variant of `sendMediaAssetsForceId`, DTO-only (no Shepherd
parameter, no MediaAsset entity):

```java
public boolean sendMediaAssetByDto(WbiaRegisterRequest dto) {
    String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
    if (u == null) return false;
    URL url;
    try { url = new URL(u); } catch (MalformedURLException ex) { return false; }
    HashMap<String, ArrayList> map = buildSingleImageRequestMap(dto);
    try {
        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
        // Validate status.success; the legacy WBIA wrapper returns
        // {"status": {"success": true/false, ...}, "response": ...}
        if (!isStatusSuccess(rtn)) return false;
        return true;
    } catch (Exception ex) {
        IA.log("WARNING: sendMediaAssetByDto() POST failed: " + ex.getMessage());
        return false;
    }
}

static HashMap<String, ArrayList> buildSingleImageRequestMap(WbiaRegisterRequest dto) {
    HashMap<String, ArrayList> map = new HashMap<>();
    map.put("image_uri_list", new ArrayList<String>());
    map.put("image_uuid_list", new ArrayList<JSONObject>());
    map.put("image_unixtime_list", new ArrayList<Integer>());
    map.put("image_gps_lat_list", new ArrayList<Double>());
    map.put("image_gps_lon_list", new ArrayList<Double>());
    map.get("image_uri_list").add(dto.imageUri);
    map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
    map.get("image_unixtime_list").add(
        dto.imageDateTimeMillis == null ? null
                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
    map.get("image_gps_lat_list").add(dto.imageLatitude);
    map.get("image_gps_lon_list").add(dto.imageLongitude);
    return map;
}
```

The pure helpers (`buildSingleImageRequestMap`, `isStatusSuccess`) are
extracted so unit tests can verify the request shape without a network
round-trip.

### Phase B `registerOneByDto` modification

Add Phase 0 at the entry; existing Phase 1 (annotation registration)
moves down unchanged:

```java
public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;

    // ---- Phase 0: ensure WBIA knows about the image ----
    List<String> knownImages;
    try { knownImages = iaImageIdsStrict(context); }
    catch (IOException ex) {
        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
        return WbiaRegisterOutcome.NETWORK_FAIL;
    }
    if (!knownImages.contains(dto.mediaAssetAcmId)) {
        if (!sendMediaAssetByDto(dto)) {
            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
        }
        // Invalidate the image-ids cache so the next annotation on this
        // image sees the updated list — without this, the 15-min cache
        // would still report the image as absent and we'd re-POST it
        // every poll cycle.
        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
    }

    // ---- Phase 1: existing annotation registration logic ----
    List<String> knownAnnots;
    try { knownAnnots = iaAnnotationIdsStrict(context); }
    catch (IOException ex) { return NETWORK_FAIL; }
    // ... rest of existing logic unchanged ...
}
```

### Outcome semantics

No new `WbiaRegisterOutcome` value. The 4 existing values handle every
case cleanly:

- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
- Image was already in WBIA + Phase 1 succeeds → `REGISTERED_OK` or
  `REGISTERED_ALREADY_PRESENT` (depending on the annotation side).

Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
counter increments on each NETWORK_FAIL. If WBIA can't be reached at all,
the annotation parks after 10 attempts — same as today, just with image
registration also having been attempted along the way.

### Cache invalidation note

After a successful Phase 0 POST, we must invalidate the `iaImageIds`
QueryCache entry. Otherwise the next annotation on the same image (within
15 minutes) would still see the cached image-ids list (which didn't
include this image), trigger Phase 0 again, attempt to register the
already-registered image (skip-if-present inside `sendMediaAssetsForceId`
would no-op, but it's wasted work), and worse — *if a different
annotation needs the same image*, we'd repeat the dance per annotation.

Verify QueryCache has an invalidation API. If not, fall back to direct
removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
Worst case: skip invalidation and accept that the next ~30 polling ticks
per image will see stale cache. Trade-off is minor since the cache TTL is
only 15 minutes and `sendMediaAssetByDto` is a single fast POST.

## Tests

Layer 1 — pure-function (`WildbookIAMRegisterTest` additions):

- `buildSingleImageRequestMapPopulatesAllLists` — verifies the 5 list
  shapes (uri, uuid, unixtime, gps_lat, gps_lon).
- `buildSingleImageRequestMapHandlesNullDatetime` — `null` datetime → 
  `null` in unixtime list (not 0 or omitted).
- `buildSingleImageRequestMapWrapsUuidInFancyForm` — verifies `toFancyUUID`
  is called on `mediaAssetAcmId`.
- `parseImageIdsArrayStrict` — paralleling
  `parseAnnotationIdsArrayStrict`: null array returns empty; well-formed
  returns list; non-object entry raises IOException; undecodable raises.
- `isStatusSuccess` — accepts `status.success=true`, rejects `false`,
  rejects missing `status`.

Layer 2 — flow (mocked HTTP):

- `registerOneByDto_image_already_present_skips_phase0_post` — mock
  iaImageIdsStrict to return list including dto's acmId; verify no
  sendMediaAssetByDto call.
- `registerOneByDto_image_absent_triggers_phase0_post` — mock empty
  image-ids list; verify sendMediaAssetByDto called; verify
  iaAnnotationIdsStrict called after (Phase 1 reached).
- `registerOneByDto_phase0_get_fails_returns_network_fail` — mock GET to
  throw IOException; verify NETWORK_FAIL returned without any POST.
- `registerOneByDto_phase0_post_fails_returns_network_fail` — mock POST
  to throw; verify NETWORK_FAIL; verify no annotation POST attempted.
- `registerOneByDto_phase0_then_phase1_success_full_sequence` — both
  succeed → REGISTERED_OK.

Layer 3 — Phase A + outcome integration (new test class
`WbiaRegistrationPhaseAImageFieldsTest` or appended to existing): verify
the DTO captures `imageUri`, lat, lon, datetimeMillis correctly from a
mock MediaAsset; verify `validMediaAsset` failure parks the annotation.

## Failure modes

| Scenario | Outcome | Why |
|---|---|---|
| MediaAsset has null acmId at Phase A | Park (MAX_ATTEMPTS) | Already handled by existing eligibility check |
| MediaAsset fails validMediaAsset at Phase A | Park (MAX_ATTEMPTS) | New eligibility check added |
| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
| Image POST fails | NETWORK_FAIL | Retry next tick |
| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
| Image POST succeeds + cache invalidation not supported | Functional but mildly wasteful | Per-annotation re-check until cache TTL expires |

## Codex review gates

Per the locked-in workflow:

1. Design review: this document. Codex green-lights before any code.
2. Code review: single implementation commit (DTO additions + new
   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
   before merge.
3. Post-commit verify: if Codex finds issues, fix-pass commit + re-review.

## Open questions for Codex

1. **QueryCache invalidation API**: does `QueryCacheFactory.getQueryCache`
   expose a way to remove a single named cached query? If not, the
   "minor staleness" trade-off above is acceptable. Worth verifying
   before implementation.
2. **`isStatusSuccess` placement**: this is a generic WBIA-response
   helper. Should it live in `WildbookIAM` (alongside the other request/
   response helpers) or in `IBEISIA` (which has historical knowledge of
   the WBIA wrapper shape)? Current draft says `WildbookIAM` for locality;
   open to moving it.
3. **Strict-parser duplication**: `parseAnnotationIdsArrayStrict` already
   exists from c11 fix-pass. The new `parseImageIdsArrayStrict` would be
   almost identical. Worth factoring into a shared private helper? Or
   accept duplication for grep-readability (image-ids and annotation-ids
   conceptually different, even if mechanically identical today).
4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
   Phase A's eligibility check rather than Phase B. Acceptable, or
   should Phase B also defensively check (in case state changed between
   Phase A and Phase B)? The window is short (Phase A commits, Phase B
   immediately calls HTTP), but the strict no-trust-managed-state c12
   pattern suggests Phase B could re-check via DTO field. My judgment is
   it's not worth the field — the MA's validity is set at construction
   time and rarely changes.
