# WBIA Annotation Reconciliation Sweep — Design

**Date:** 2026-08-03 (rev 3, post-Codex round 2 on the implementation)
**Status:** Approved
**Branch:** `feature/wbia-annotation-reconciliation-sweep` (off `main`)

## Problem

`AcmIdBot.sweepMatchableAssets()` continuously reconciles **MediaAssets** against WBIA:
it probes `/api/image/rowid/uuid/` for every asset backing a `matchAgainst` annotation
and re-registers the ones WBIA does not know. There is **no annotation equivalent**.

Annotation registration relies on `StartupWildbook.startWbiaRegistrationPollingThread`
(30s), which is push-once-and-trust-a-flag:

1. **No probe.** It only queries `wbiaRegistered == false && wbiaRegisterAttempts < 10`.
   Nothing ever asks WBIA whether an annotation is actually present, so drift (WBIA DB
   rebuilt/restored, annotations deleted WBIA-side, a POST that "succeeded" but didn't
   persist) is invisible and permanent.
2. **Optimistic mass backfill.** `archive/sql/ml_service_idempotency.sql:65` sets
   `WBIAREGISTERED = TRUE WHERE "ACMID" IS NOT NULL AND "WBIAREGISTERED" IS NULL` — an
   assumption, never verified against WBIA. Every annotation it touched is excluded
   from the poller forever.
3. **No un-park path.** Only `MlServiceProcessor` (at creation) ever sets the flag FALSE.
   Annotations parked at `wbiaRegisterAttempts = 10` — including ones parked for a
   transient WBIA outage — never retry. No admin JSP exists.

Live symptoms, both observed on the affected install:

- identify fails permanently with WBIA
  `code 600 "Missing image and/or annotation UUIDs (0, 32)"`, requeued 30× to no effect;
- a manual annotation POST fails with WBIA `code 500`
  `ValueError: The input list image_uuid_list has invalid values (index, value): [(0, None)]`
  — i.e. **WBIA does not have the backing image**, even though the MediaAsset carries a
  well-formed `acmId`. This is the decisive evidence for §4 below: a non-null `acmId` is
  not evidence of WBIA image presence.

## Verified facts (code archaeology, against `main` @ 61122d6f5a)

- **`Annotation.id` is a String PK**, `<column length="36"/>` (UUID). The sweep cursor is
  therefore a **String** with lexicographic ordering, not the int cursor the asset sweep
  uses. Lexicographic order over a fixed-width UUID column is a stable total order.
- **`matchAgainst` is a field on `Annotation`** (`Annotation.java:85`), not on Encounter.
- **What identify asks WBIA about is the annotation's `acmId`, not its `id`:**
  `IBEISIA.sendIdentify` builds `query_annot_uuid_list` / `database_annot_uuid_list`
  from `toFancyUUID(ann.getAcmId())` (`IBEISIA.java:289`, `:326`). **The probe and the
  heal must both key on `acmId`.**
- **`acmId != id` is real for legacy rows.** ml-service and manual paths set
  `acmId = getId()` (`MlServiceProcessor.java:478`, `Annotation.java:1656`,
  `SubmitSpotsAndImage.java:145`), but WBIA-era import paths set acmId from WBIA's own
  UUID (`IBEISIA.java:1274`, `:2172`) and `AcmUtil.rectifyAnnotationIds` rewrites it to
  whatever WBIA returned (`AcmUtil.java:56,61`). So a force-by-`id` heal would register
  a UUID identify never asks about.
- **`sendAnnotationsForceId` forces the wrong identifier** for this purpose:
  `annot_uuid_list = toFancyUUID(ann.getId())` (`WildbookIAM.java:387-388`). A new
  force-by-acmId send is required — verified necessary, not a preference.
- **Non-forced `sendAnnotations` mutates `acmId`** via `AcmUtil.rectifyAnnotationIds`,
  which is why it is rejected as the heal: a reconciler must not rewrite the identifier
  it reconciles.
- **WBIA's annotation probe endpoint exists and has the semantics we need.** Verified in
  the wildbook-ia source, not assumed:
  `@register_api('/api/annot/rowid/uuid/', methods=['GET'])` on
  `get_annot_aids_from_uuid` (`wbia/control/manual_annot_funcs.py:1126`), decorated
  `@accessor_decors.getter_1to1` so the response is a parallel list. Its companion
  `get_annot_missing_uuid` defines missing as exactly `aid is None`, matching what
  `parseRowidProbeResponse` already implements. (As on the image side, the tidier
  `/api/annot/uuid/missing/` route is commented out and unusable over REST.)
- **Existing plumbing reused verbatim:** `WildbookIAM.chunkList`,
  `PROBE_CHUNK_SIZE = 50`, `parseRowidProbeResponse`, `validateForcedResponse`,
  `AcmIdBot.sendConfirmedAcmId`, `shouldSkipPoisonedPage`, `PAGE_FAIL_LIMIT = 3`, and the
  read/probe/heal phase separation with explicit `updateDBTransaction()` commits.
- **`Shepherd.updateDBTransaction()` = commit + begin**, so confirmed writes survive the
  trailing `rollbackAndClose()`.
- **Featureless legacy annotations bypass the asset sweep entirely.**
  `Annotation.getMediaAsset()` falls back to the deprecated direct `__getMediaAsset()`
  association when the annotation has no Features (`Annotation.java:618-627`), while the
  asset sweep's scope query traverses `Feature` rows (`AcmIdBot.java:308`). Those assets
  are invisible to it. The annotation sweep therefore cannot delegate image healing.

## Design

### 1. Scope

Annotations with `matchAgainst == true` that are attached to an Encounter. Orphans are
excluded: identification cannot reach them, and an orphan's `findIndividualId()` yields
no name anyway. Scope deliberately **ignores `wbiaRegistered`** — that flag is the thing
this sweep exists to stop trusting.

```java
select id, acmId from org.ecocean.Annotation
where matchAgainst == true && enc.annotations.contains(this) && id > cursor
VARIABLES org.ecocean.Encounter enc
PARAMETERS String cursor
result:   distinct id, acmId
ordering: id ascending
```

plus an anti-race clause taking the exact complement of the 30s poller's queue:

```java
&& (wbiaRegistered == null || wbiaRegistered == true
    || wbiaRegisterAttempts >= StartupWildbook.WBIA_REGISTER_MAX_ATTEMPTS)
```

The poller claims precisely `wbiaRegistered == false && attempts < MAX`. Without this
clause both workers could POST the same annotation concurrently — and since the poller
forces `id` while the sweep forces `acmId`, an `id != acmId` row would land in WBIA as
**two annotations on one box**. Partitioning by the flag beats a lease here because the
sweep's whole purpose is the rows the poller ignores: those whose flag is lying
(`TRUE`, or `NULL` for rows the backfill never touched) and those it permanently parked.
Note `wbiaRegistered` is a nullable `Boolean`, so the null case is spelled out rather
than relying on `!= false` — SQL three-valued logic would drop null rows.

Three further deliberate choices:

- **Bound parameter, not concatenation** — a String cursor concatenated into JDOQL is
  invalid as an unquoted literal and brittle if quoted.
- **Projection (`id, acmId`), not entity materialization** — the read phase must not
  materialize 10,000 managed `Annotation` graphs to extract two fields.
- **`distinct`** — `enc.annotations.contains(this)` yields one row per parent encounter,
  so an annotation attached to several appears several times. Dedup is pushed into SQL;
  the in-memory distinct-id collection remains as a backstop.

### 2. Paging & cursor

- `ANNOT_SWEEP_PAGE_SIZE = 10_000` distinct annotations per 15-minute run.
- `static String annotSweepCursor = ""` (empty string sorts below every UUID);
  `static int annotSweepFailCount = 0`.
- Wrap-around on **raw result exhaustion only**, never a post-dedup count. Reset to `""`.
- `maxFixes` clamp: cap hit mid-page ⇒ cursor advances only to the last annotation
  actually processed.
- **Partial-read progress (fixes the livelock).** The page object is allocated *before*
  iteration and populated in place, so if iteration throws partway the caller still holds
  the last safely-read id. A read-phase failure can then do a **targeted** skip to that
  id rather than the blind `cursor += PAGE_SIZE` the int cursor uses and a String cursor
  cannot express. Only when a page yields no ids at all is there nothing to skip to; that
  case wraps to `""` with a loud `ANNOT SWEEP STUCK` log rather than retrying the same
  page forever or silently dropping a range.

### 3. Probe

`WildbookIAM.iaMissingAnnotationIds(List<String> acmIds, String context)`, delegating to
a new shared private `iaMissingIds(acmIds, context, endpointPath)` that also backs
`iaMissingImageIds` — the two endpoints are both `getter_1to1` rowid lookups with
identical response semantics, so the body is shared rather than copied. Null / non-UUID
acmIds are filtered out before sending (they would make WBIA reject the whole chunk);
any chunk failure throws `IOException` so a failed probe is never read as "all present".

### 4. Heal — image presence first

**The annotation heal must confirm the backing image is present in WBIA, not merely that
`MediaAsset.acmId != null`.** This is the correction the live 500 error forces, and it
also covers the featureless-legacy-annotation hole where the asset sweep never sees the
asset at all. Per candidate, in order:

1. Load the annotation; skip if gone.
2. Resolve the MediaAsset; skip (count `skippedNoAsset`) if absent.
3. **Context-aware** eligibility: `IBEISIA.validForIdentification(ann, context)`. The
   no-context overload skips the `validIAClassForIdentification` check, so using it would
   let the sweep register annotations identify then rejects.
4. **Image presence**, delegated wholly to `ensureImageRegistered`, which owns the asset's
   `acmId` so the heal loop never has to reason about reverting it. **Step order inside it
   is load-bearing**, because `updateDBTransaction()` is commit-plus-begin and so commits
   *every* dirty object in the transaction, not just the field the caller had in mind:
   1. resolve and commit image validity **first**, while nothing provisional is pending;
   2. if the asset already carries a well-formed acmId, probe
      `iaMissingImageIds([acmId])` — present ⇒ done, no writes at all;
   3. only then adopt `acmId = ma.getUUID()` if needed, POST via
      `IBEISIA.sendMediaAssetsNew([ma], context, false)`, require
      `sendConfirmedAcmId(rtn, ma.getAcmId())`, and commit **only after** confirmation.

   Adopting before the validity commit would let an unconfirmed identifier be committed by
   that flush, after which the in-memory revert is silently discarded by the enclosing
   `rollbackAndClose()` — leaving Wildbook holding an acmId WBIA never acknowledged.
   Unconfirmed ⇒ count `blockedOnAsset` and continue; the annotation is retried on a later
   pass, never parked.
5. Adopt the annotation's `acmId = getId()` if null/malformed.
6. `WildbookIAM.sendAnnotationForcedByAcmId(ann, shepherd)` — forces
   `annot_uuid_list = acmId`, performs **no already-present check** (the probe already
   established absence, and the full-list `iaAnnotationIds()` fetch would be ~1M UUIDs).
   Critically it must never treat presence of `ann.id` at WBIA as evidence that
   `ann.acmId` is registered — that is precisely the failure under repair.
7. Confirmed (WBIA echoed our acmId) ⇒ set `wbiaRegistered = TRUE` and
   `wbiaRegisterAttempts = 0`, **each only if its value actually changes**, since both
   setters bump `Annotation.version` and would otherwise cause needless OpenSearch
   reindex churn across a systemic WBIA loss. Then `updateDBTransaction()`.
   Not confirmed ⇒ revert the annotation's acmId, on **every** non-confirmed outcome and
   not only a thrown send — but only when an adoption actually happened, since a
   no-op `setAcmId` still bumps `version`. The asset's acmId is deliberately **not**
   reverted here: its registration was already confirmed in step 4, and undoing it would
   make Wildbook forget an image WBIA really holds.

Setting `wbiaRegistered = TRUE` also un-parks annotations the 30s poller abandoned at
`attempts >= MAX`, which nothing else in the codebase does — closing problem #3.

### 5. Transactions

Phase separation identical to `sweepMatchableAssets`: read (short tx, primitives out) →
probe (no tx open) → heal (fresh tx, commit per confirmed heal). Wired into
`AcmIdBot.fixAcmIds()` after `sweepMatchableAssets`, under the existing `botRunning`
guard. The heal phase does hold one DB connection across its HTTP calls — the same
shape the asset sweep already has, one connection at a time.

### 6. Constants & expected duration

| Constant | Value | Meaning |
|---|---|---|
| `ANNOT_SWEEP_PAGE_SIZE` | 10,000 | distinct annotations examined per 15-min run |
| `PROBE_CHUNK_SIZE` | 50 (existing) | UUIDs per probe GET |
| `PAGE_FAIL_LIMIT` | 3 (existing) | failed runs on one page before skipping |
| `maxFixes` | 500 (existing) | max successful heals per run |

**Duration, stated plainly** (the rev-1 spec understated this): at 1M matchable
annotations, a *healthy* full pass is 100 runs ≈ **25 hours**. If every annotation needs
repair, `maxFixes = 500` makes it 2,000 runs ≈ **21 days**, and each resumed run still
probes up to 10,000 rows. That is acceptable for continuous background reconciliation and
unacceptable as an incident response — a bulk repair for a known-bad install wants a
dedicated admin JSP (deferred, see below). The summary log reports page counts, heals
sent/confirmed, and both skip reasons so the rate is observable.

## Explicitly out of scope

Real defects found in the same investigation; each is separately PR-worthy and bundling
them would make this diff unreviewable.

- **`IBEISIA.iaCheckMissing` is broken.** Its image branch is a no-op
  (`// TODO: actually send the mediaasset duh`, prints "FAKE ATTEMPT"); its annotation
  branch calls `__sendAnnotations(...)` but never assigns the result to `srtn`, so
  `tryAgain` is always false and identify is never retried; `catch (Exception ex) {}`
  swallows failures; and `anns.add(annsTemp.get(0))` inside one shared try means a single
  unresolvable acmId aborts collection of the rest of the missing list.
- **send/identify eligibility mismatch.** `sendAnnotations` skips annotations whose
  MediaAsset lacks an acmId while `sendIdentify` still asks about them. The sweep no
  longer *depends* on this being fixed (it confirms image presence itself and uses the
  context-aware eligibility check), but the live path still needs it.
- **`sendAnnotationsAsNeeded` swallows send exceptions** into
  `rtn.put("sendAnnotMAException", ...)`; identify proceeds regardless.
- Durable cursor/quarantine persistence across restart; an admin JSP for bulk repair and
  for forcing a re-sweep; configurable probe/heal limits with run-duration telemetry.

## Testing

Pure-logic unit tests (no scheduler, no network), mirroring `AcmIdBotSweepTest` and
`WildbookIAMRowidProbeParseTest`:

- Page collection over `(id, acmId)` projection rows: distinct-id dedup (multi-encounter
  duplicates), `rawExhausted` only on input exhaustion, page-size boundary, empty page,
  null-row and null-id tolerance, duplicate-acmId warning.
- Cursor policy: advance past page, wrap to `""` on exhaustion, `maxFixes` clamp resumes
  mid-page, empty page never writes a null cursor.
- Candidate routing: null and malformed acmIds go to the heal-without-probe bucket;
  well-formed ones go to the probe bucket, mapped back to their annotation id.
- **Regression for Codex finding #1:** `id` present at WBIA but `acmId` absent must still
  POST the `acmId`. Covered at the unit level by asserting the forced request map carries
  the acmId (not the id) when the two differ.
- Probe: `iaMissingAnnotationIds` null-input and non-UUID filtering; response parsing is
  already covered by the shared `parseRowidProbeResponse` tests.

## Codex review disposition (rev 1 → rev 2)

| # | Severity | Finding | Disposition |
|---|---|---|---|
| 1 | Critical | A near-copy sender retaining the `iaAnnotIds.contains(ann.getId())` skip would refuse to send exactly the rows under repair | §4.6: new sender does **no** already-present check at all; unit test asserts acmId (not id) is forced |
| 2 | Major | "asset sweep runs earlier in the same tick" does not establish the dependency; featureless legacy annotations are invisible to the asset sweep | §4.4: annotation heal probes and registers the image itself. Confirmed independently by the live 500 error |
| 3 | Major | JDOQL not implementation-ready: String cursor needs a bound parameter | §1: `PARAMETERS String cursor`, plus projection and `distinct` |
| 4 | Major | String-cursor failure policy can livelock at a poison row | §2: page allocated before iteration so partial progress survives; targeted skip, `ANNOT SWEEP STUCK` only when no ids at all |
| 5 | Minor | Two "verified facts" wrong: prospects hold an `Annotation` relationship not an acmId; `findIndividualId` doesn't require an encounter | Both claims removed/corrected above |
| 6 | Major | Deferred eligibility defect partly load-bearing; sweep used the context-free `validForIdentification` | §4.3: context-aware form; image presence in this same PR |
| 7 | Major | Scale understated; entity materialization not proven heap-safe; pool claim wrong | §1 projects `id, acmId`; §6 states 25h healthy / 21d worst case plainly; unverified pool number dropped |
| 8 | Minor | Revert on every non-confirmed outcome; setters always bump version → reindex churn | §4.7: revert on all non-confirmed paths; set each flag only when its value changes |

## Codex round 2 disposition (implementation review; rev 2 → rev 3)

Round 2 confirmed findings 1–7 FIXED in code and 8 partially fixed. Three items remained,
all now closed:

| # | Severity | Finding | Disposition |
|---|---|---|---|
| 8 | Minor | An unconfirmed POST called `setAcmId(prior)` even when no adoption had occurred, bumping `version` for nothing | Track `annAcmIdAdopted`; revert only when true, on both the else-branch and catch paths |
| 9 | Major | A provisional asset acmId could be **committed** by the validity `updateDBTransaction()` before image confirmation; the later revert was then discarded by `rollbackAndClose()`, leaving an unconfirmed acmId persisted | `ensureImageRegistered` reordered to commit validity before adopting, and to commit the adopted acmId only after confirmation; it now owns the asset acmId end-to-end so the heal loop never touches it |
| 10 | Major | The sweep raced the 30s poller; for an `id != acmId` row both could POST, creating two WBIA annotations for one box | §1 anti-race clause: sweep scope is the exact complement of the poller's queue |

Also confirmed correct in round 2, having been fixed between rounds without being asked:
page-exhaustion logic (size-limit return cannot mark exhaustion, including an exactly-full
final page); fail-closed projection-shape handling (an unexpected row shape throws instead
of mass-routing every row into the null-acmId bucket and re-POSTing the corpus);
database-order candidate processing (so the heal-cap resume cursor cannot skip candidates
when Postgres collation disagrees with Java String ordering); and that the `iaMissingIds`
extraction preserved `iaMissingImageIds` behavior for its existing caller.
