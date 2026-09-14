OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3c6c-6706-7502-81de-0e02101c421c
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing a design doc on the `migrate-ml-service-v2` branch of
the Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you
the project conventions, repo gotchas, and current architecture that
the design assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (now 3.1 on this deployment), React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has 20 commits, all Codex-reviewed at design + code. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md`.

## Shepherd pattern

```java
Shepherd shep = new Shepherd(context);
shep.setAction(ACTION_PREFIX + "methodName");
try {
    shep.beginDBTransaction();
    // ... JDO operations ...
    shep.commitDBTransaction();
} catch (Exception ex) {
    // log
} finally {
    shep.rollbackAndClose();
}
```

`rollbackAndClose` is idempotent — safe after commit (rollback on
inactive tx is a no-op) and safe after early return.

Critical gotcha: never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the
HTTP work without Shepherd in Phase B, persist outcome in a fresh
Shepherd in Phase C. This pattern is the reference for any code
that mixes DB + HTTP.

## JDO column naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes — e.g.
`ENCOUNTER_ANNOTATIONS.CATALOGNUMBER_OID` references
`ENCOUNTER.CATALOGNUMBER`; `ENCOUNTER_ANNOTATIONS.ID_EID` references
`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
`_OID` suffix — it's a direct FK, not a JDO-generated join).

## ANNOTATION FK constraint cleanup

Direct SQL DELETE on `ANNOTATION` is dangerous. Several tables hold
FKs with `allows-null=false`:

- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
  nulled.
- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
  `dependent-element=false`, so JDO does not cascade. Row must be
  deleted explicitly.
- `TASK_OBJECTANNOTATIONS` join — clean up via the join row.
- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
- `ENCOUNTER_ANNOTATIONS` — caller's responsibility (`enc.removeAnnotation(ann)`).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh boundary;
**does not** drain the Wildbook IndexingManager queue. If you need
"after this write the doc must be searchable" semantics, use
`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
combines `_refresh` with a `_count` poll on an `ids` query.

`waitForVisibility` was added in commit `f429c5bf8` (c7 of v2) as
a counter-measure for race conditions between JDO write,
IndexingManager queue, and OS refresh cadence.

`skipAutoIndexing` (controlled by `/tmp/skipAutoIndexing`) makes
every `waitForVisibility` poll return zero — there's a diagnostic
WARN at entry to alert anyone running with the flag set.

## `/tmp/skipAutoIndexing` and test mode

If `/tmp/skipAutoIndexing` exists, IndexingManager skips OS indexing
entirely. This is a developer convenience for fast local iteration
but breaks any code that depends on `_id` being findable in OS. The
v2 work checks for this file and warns.

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",      // "vector" = ml-service v2
        "method": "miewid-msv4.1",      // embedding model id
        "version": "4.1",
        "embedding_dimension": 2152,
        // legacy entries have api_endpoint instead of method/version
      }
    },
    "_mlservice_conf": {
      "default": {
        "base_url": "https://ml-service.example.com:8008",
        "detection_endpoint": "/pipeline/",
        "extraction_endpoint": "/extract/",
        "model_id": "...",
        "match_against_species": [...]
      }
    }
  }
}
```

`Embedding.findMatchProspects` gates entry on
`isVectorConfig = method != null || api_endpoint != null` (the
either-or check covers the v2-vs-legacy contract).

## v1 antipatterns to avoid (carry-over from earlier review rounds)

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.
5. **Don't trust the cache without a strict variant.** Lenient
   `iaImageIds` swallows errors; strict variant raises so calls
   that need accuracy can opt in.

## File path conventions

- Java: `src/main/java/org/ecocean/...`
- Tests: `src/test/java/org/ecocean/...`
- Design docs: `docs/plans/YYYY-MM-DD-<topic>.md`
- React: `frontend/src/...`

## What we want from this review

The doc below proposes fixing two linked bugs causing empty match
prospects on bulk imports. Both are in scope. Your job: independent
read on whether the design is right, where it's wrong, what it
missed, and any sequencing/test gaps.

**Specifically requested feedback in the doc's "Open questions"
section.** Address those, plus anything else you'd raise.

**Do not write to any file.** Review-only.

---

# Design: fix empty match prospects on bulk imports

## Context

On amphibian-reptile.wildbook.org (ml-service v2 live test deployment),
bulk-imported fire salamanders return 0–5 match prospects per annotation
even when the import itself contains 7+ same-species annotations that
should match each other. Manual OpenSearch queries against the index a
few minutes later return all the expected hits, so the data lands
correctly — but at match-task time the candidate pool is incomplete.

This design fixes two linked bugs that together produce the empty
results. Either fix on its own leaves a residual: WBIA-only fixes the
acmId/HotSpotter-fallback story, batch-visibility-only fixes the
intra-import matching story. Both are needed because both contribute
distinct failure modes to the same observable symptom.

The two bugs:

1. **WBIA image-then-annotation registration** — the v2 routing path
   skips legacy `WBIA.sendMediaAssets()` (ml-service runs detection
   without WBIA's help), so when the annotation-registration polling
   thread fires `/api/annot/json/` against WBIA it errors out with
   `image_uuid_list has invalid values [(0, None)]`. This is the bug
   the existing paused design doc
   (`2026-05-18-wbia-image-registration-design.md`) targets. Its scope
   does not change here; we are folding it into the combined plan.
2. **Match-task visibility wait is per-annotation, not per-import-batch**
   — `MlServiceProcessor.waitAndRunMatch` calls
   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
   …)` on only the annotations from the current ml-service job. With
   one ml-service job per image, an import of N images produces N
   parallel jobs. Each job waits for its own annotation to land in OS,
   then runs match — but the other N-1 annotations from sibling jobs
   may not be in OS yet. Intra-batch matches that the user expects
   (multiple sightings of the same individual within one upload) never
   appear because the candidates aren't searchable when the match runs.

## Goal

Restore the user-expected behavior: when I upload a batch of 4 fire
salamander photos and the import completes, the per-annotation match
results show the other annotations from my upload (when they match
visually) and any pre-existing candidates from the corpus. WBIA stays
in sync as a HotSpotter fallback.

## Non-goals

- Detection-time WBIA image registration. Same rationale as the
  paused WBIA design — handling it retroactively in the polling thread
  keeps the intake fast path simple.
- Restructuring ml-service to do detection in batches instead of per
  image. Per-image jobs are the natural granularity for ml-service v2
  (single image in, single result out, retryable in isolation). The
  batching question is at the match invocation seam, not the
  detection seam.
- Moving away from `waitForVisibility`. It works correctly — its
  call-site scope is the bug, not the helper itself.
- Adding a refresh-on-every-index. We already have the visibility
  primitive; we just need to call it on the right set.

## Audit: what already exists

| Helper | File | Status |
|---|---|---|
| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
| `OpenSearch.indexRefresh(indexName)` | `OpenSearch.java:468` | Synchronous refresh helper. |
| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Current per-job wrapper; falls back to `enqueueDeferredMatch` on timeout. |
| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds the match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues the match via IAGateway with `mlServiceV2DeferredMatch: true`. |
| `Embedding.findMatchProspects(iaConfig, task, shepherd)` | `Embedding.java:265` | Per-annotation knn + matchingSetQuery. Already takes a list of annotations on the task; the count just isn't currently the import batch. |
| `Task` has parent/child relationship | `Task.java` | A child Task can carry an `importTaskId` parameter; we already use this. |
| `WildbookIAM.iaImageIds(context)` / `sendMediaAssetsForceId(…)` / `mediaAssetToUri(…)` / `validMediaAsset(…)` | `WildbookIAM.java` | Existing helpers the WBIA-side design reuses. See the paused WBIA design doc for the helper-by-helper audit. |

Note: `findMatchProspects` already loops `task.getObjectAnnotations()`
and builds a per-annotation knn — there's nothing in the matching
itself that's per-annotation-job-specific. The bug is at the seam
that decides *when* to invoke it for a given annotation set.

## Design

### Track 1 — WBIA image-then-annotation registration

No structural change to the existing design at
`docs/plans/2026-05-18-wbia-image-registration-design.md`. The Codex
review of that doc surfaced the following items, which need addressing
before implementation:

1. `mediaAssetToUri(MediaAsset)` is `private` in `WildbookIAM`.
   Promote to `public static String` (returning `String` rather than
   the current `Object`, while we're touching the signature). Tighten
   the `ma.webURL()` null-check that the existing private version
   doesn't have.
2. The strict `iaImageIdsStrict` variant introduces caching that the
   lenient `iaImageIds` doesn't currently have. The new cache key is
   `iaImageIdsStrict` (15-min QueryCache, matching the
   `iaAnnotationIdsStrict` pattern from the c11 fix-pass). The
   lenient variant stays cache-free for backward compatibility.
3. Phase A's eligibility check gains a `validMediaAsset(ma)` call
   *and* an `ma.isValidImageForIA()` check, in the order
   `sendMediaAssetsForceId` uses them (lines 121–130 of
   `WildbookIAM.java`). If either fails, Phase A parks the
   annotation at `MAX_ATTEMPTS` (existing park behavior).
4. Phase B does NOT re-validate (no DTO trust-boundary check for MA
   eligibility). Rationale: MA validity does not change between
   Phase A commit and Phase B HTTP call. Documented in the design as
   an explicit decision, with a one-line comment in the code.
5. `parseImageIdsArrayStrict` is added alongside
   `parseAnnotationIdsArrayStrict`. A shared
   `parseFancyUuidListStrict(jids, ctxLabel)` helper extracts the
   commonality. Both parsers go through `fromFancyUUID`.
6. No new `outcome` enum value. Image-registration failures use the
   existing `NETWORK_FAIL` outcome, with the Phase C log line
   distinguishing the phase via the existing log string (Phase 0 vs
   Phase 1 vs Phase 2).
7. The `WbiaRegisterRequest` DTO gains four image-side fields,
   captured in Phase A:

```java
public final String imageUri;
public final Double imageLatitude;
public final Double imageLongitude;
public final Long imageDateTimeMillis;
```

`mediaAssetUuid` is not a separate field — v2's convention is
`MediaAsset.acmId == MediaAsset.uuid` (commit `2a3eab63a`), so
`dto.mediaAssetAcmId` already carries the value
`sendMediaAssetsForceId` uses for `image_uuid_list`.

### Track 2 — batch-aware visibility wait

#### Problem shape

`waitAndRunMatch` today:

```
MlServiceProcessor.waitAndRunMatch(annotationIds=[A], taskId, matchConfig):
    waitForVisibility("annotation", [A], TIMEOUT)
        -> A is now in OS
    runMatchProspects([A], taskId, matchConfig)
        -> findMatchProspects loops [A], does knn against OS corpus
        -> at this moment, B/C/D from sibling jobs are still queued
        -> A's knn finds nothing in the batch
```

For an import of {A, B, C, D} with one image per ml-service job, four
parallel `waitAndRunMatch` invocations each handle their own
annotation. Each individually sees its annotation reach OS, then
fires match against an OS state that lacks the others.

#### Approach

Promote the visibility wait from per-annotation to per-import-batch:
before any annotation's match runs, wait for *all annotations from
the same import* to be visible in OS.

Two implementation seams considered, one chosen:

##### Seam A (chosen): in-place expansion at `waitAndRunMatch`

`waitAndRunMatch` already has the annotationIds for the current job
and access to the parent task. From the parent task, walk up to the
ImportTask (via `importTaskId` task parameter) and enumerate all
annotations that share that import. Pass the union to
`waitForVisibility`.

```java
private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
    String taskId, JSONObject matchConfig) {
    Set<String> visibilitySet = expandToImportBatch(annotationIds, taskId);
    try {
        OpenSearch os = new OpenSearch();
        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
            enqueueDeferredMatch(annotationIds, taskId);
            return MlServiceJobOutcome.ok(annotationIds);
        }
    } catch (IOException ex) {
        enqueueDeferredMatch(annotationIds, taskId);
        return MlServiceJobOutcome.ok(annotationIds);
    }
    return runMatchProspects(annotationIds, taskId, matchConfig);
}

private Set<String> expandToImportBatch(List<String> annotationIds, String taskId) {
    Set<String> ids = new LinkedHashSet<String>(annotationIds);
    if (taskId == null) return ids;
    Shepherd shep = new Shepherd(context);
    shep.setAction(ACTION_PREFIX + "expandToImportBatch");
    try {
        shep.beginDBTransaction();
        Task t = Task.load(taskId, shep);
        if (t == null) { shep.rollbackAndClose(); return ids; }
        String importTaskId = t.getParameters() == null ? null
            : t.getParameters().optString("importTaskId", null);
        if (!Util.stringExists(importTaskId)) { shep.rollbackAndClose(); return ids; }
        // Fan out: all annotation IDs whose encounter joins to this importTask.
        for (String batchAnnId : findImportBatchAnnotationIds(shep, importTaskId)) {
            ids.add(batchAnnId);
        }
        shep.rollbackAndClose();
    } catch (Exception ex) {
        System.out.println("expandToImportBatch failed (continuing with caller set): " + ex);
        shep.rollbackAndClose();
    }
    return ids;
}
```

`findImportBatchAnnotationIds(shep, importTaskId)` resolves
`ImportTask → Encounters → Annotations` via the existing JDO joins
(`IMPORTTASK_ENCOUNTERS.CATALOGNUMBER_EID` →
`ENCOUNTER_ANNOTATIONS.CATALOGNUMBER_OID`). One JDOQL query, no
serialized state to worry about.

The runMatchProspects call still passes the *original* (caller's)
annotationIds — we only widen the visibility wait, not the matching
seam itself. `findMatchProspects` continues to do per-annotation knn
on the current job's annotations; what changes is that the OS
corpus is guaranteed to include the sibling annotations as
*candidates* by the time the knn fires.

##### Seam B (rejected): a "batch barrier" task that gates all match invocations

Have the import create one barrier task that all per-annotation
match tasks block on. Cleaner topology in theory; the wait happens
once at the barrier instead of N times across N jobs. But: adds a
new task type, requires a new dispatcher branch, and conflicts with
the "fast lane" goal where each ml-service job can complete
end-to-end without external coordination. Rejected.

#### Behavior in edge cases

- **Annotation has no importTask parent** (single-encounter
  match-by-API call, frontend "match again" button, etc.):
  `expandToImportBatch` falls through; visibility wait reduces to
  the current per-job behavior. No regression for non-import paths.
- **ImportTask is large** (hundreds of annotations): `waitForVisibility`
  is `_count`-based polling, bounded by `VISIBILITY_TIMEOUT_MS`.
  Cost is the same order as today's wait — one `_count` query per
  poll cycle, regardless of how many ids are in the request.
- **Timeout in the wider set**: today's code already has
  `enqueueDeferredMatch` for this case. We reuse it. Caller's
  match is deferred and the deferred path runs match without
  additional waiting (`runDeferredMatch` → `runMatchProspects` at
  `MlServiceProcessor.java:441`). That's still correct: by the
  time the deferred match fires from the queue, normal OS refresh
  cadence has run, and the visibility shortfall is unlikely to
  persist.
- **The widened set includes annotations from sibling jobs that
  failed**: `waitForVisibility` will time out (failed annotations
  never make it to OS), the wider set falls back to deferred. The
  deferred match still runs (without batch awareness) and returns
  whatever candidates are visible. This is a degradation but not a
  regression vs. today.
- **Two imports overlap**: `importTaskId` is the boundary. Each
  import's annotations form a self-contained visibility set, even
  when two imports run concurrently.

## Interaction between Track 1 and Track 2

The two tracks are independent in code (different files, different
review chains) but linked in symptom: Track 1 fixes "acmId is set in
DB but not in WBIA" which affects HotSpotter fallback and which
allowed acmIds to take an awkward placeholder-then-rectified path
through OS reindexing. Track 2 fixes the timing where annotations
are visible in DB but not yet in OS at match time, regardless of
WBIA state.

Specifically: today, the acmId on a newly-imported annotation is its
own UUID (placeholder), gets indexed into OS that way, then later
`AcmUtil.rectifyAnnotationIds` mutates it to WBIA's UUID and
re-indexes. Match tasks firing between the initial index and the
rectify reindex see the placeholder acmId — which still passes the
`exists: {field: acmId}` filter in `getMatchingSetQuery`, so it's
not a filter regression. The relevant effect of Track 1 is
HotSpotter fallback (Match-with-HotSpotter button) and operational
hygiene (no recurring 500s in logs), not directly the
empty-prospects symptom. We're fixing it now because (a) it's
already designed, (b) it's the right thing to do, and (c) the
empty-prospects user-facing issue surfaced via the same import
flow and it would be confusing to fix one and leave the other.

## Order of work

1. Resolve the existing WBIA design doc's Codex follow-ups (Track 1
   items 1–7 above), then implement that design.
2. Implement Track 2 (`expandToImportBatch` + call-site change in
   `waitAndRunMatch`).

Track 2 is smaller (one file, one method body, one helper, ~80
lines including the JDOQL helper and tests) and could land first
if needed — there is no compile-time or runtime dependency on
Track 1. Doing Track 1 first is preferred for review ordering:
its design is older and has already absorbed a Codex pass, so it's
ready to move; Track 2 is brand new and benefits from a Codex
pass before code lands.

## Test strategy

### Track 1

Existing WBIA design doc covers this — `WildbookIAMRegisterTest`
gains coverage for the Phase 0 image POST, the cache invalidation
behavior, the strict parser, and the new DTO fields. Roughly 8–10
new test cases.

### Track 2

New unit tests in `MlServiceProcessorTest` (a wrap-and-mock pattern
since this class is JDBC/Shepherd-heavy):

- `waitAndRunMatch_expandsToImportBatch_whenParentTaskHasImportTaskId`
  — ImportTask with 4 sibling annotations; ensures the visibility
  wait set contains all 4.
- `waitAndRunMatch_passesOnlyCallerIdsToRunMatchProspects` —
  expansion happens at the wait, not the match.
- `waitAndRunMatch_fallsThroughWhenNoImportTaskParent` —
  single-encounter case; visibility wait set equals caller set.
- `waitAndRunMatch_continuesOnExpansionFailure` — JDOQL throws;
  fall back to caller's set, log warning, proceed.
- `waitAndRunMatch_defersOnExpandedTimeout` — `waitForVisibility`
  returns false on the wider set; verify `enqueueDeferredMatch`
  invoked.
- `expandToImportBatch_handlesOverlappingImports` — two imports
  with shared encounter; ensure boundary is by importTaskId, not
  encounter.

Plus one end-to-end harness against `MlServiceLiveIntegrationTest`
(or equivalent) on the live deployment: import 4 same-species
images, observe that each annotation's `findMatchProspects` result
includes the other 3 as candidates.

## Open questions for Codex

1. **`findImportBatchAnnotationIds` performance.** Resolves
   `ImportTask → ENCOUNTERS → ANNOTATIONS` via the JDO join
   tables. For typical bulk imports (50–500 annotations) this is a
   single small query and fine. For unusually large imports
   (10k+), would Codex prefer paginated query or a cap with a
   diagnostic warning? My instinct: just log if the expanded set
   is >5000 and proceed; `waitForVisibility` will handle the
   bound. Open to a stronger preference.
2. **Should we widen visibility for the deferred match path
   too** (`runDeferredMatch`)? Right now it skips visibility wait
   entirely. The argument for skipping is that the deferral itself
   gives OS time to catch up; the argument against is that under
   load the deferral may fire before refresh. I lean: don't change
   it (out of scope; this is purely a `waitAndRunMatch`
   adjustment), but flag for Codex.
3. **Order of expansion vs validation**: if `importTaskId` is set
   but the import is in `failed` state, do we still want to wait
   for siblings that may never land in OS? My current design says
   yes — the visibility wait will time out, `enqueueDeferredMatch`
   takes over, and the deferred path runs match on whatever's
   visible. Alternative: skip expansion if import is failed.
   Cleaner but adds another DB lookup. Open to Codex's view.
4. **Should `expandToImportBatch` deduplicate across encounters?**
   Two encounters with the same annotation attached (rare but
   possible per the schema) would otherwise double-count in the
   visibility set. The `LinkedHashSet<String>` does this for free
   if we pass through string IDs, which is what's drafted.
5. **Anything else I missed** — design smells, sequencing bugs,
   test gaps, missing failure cases.

## Cross-references

- Original WBIA registration design:
  `docs/plans/2026-05-18-wbia-image-registration-design.md`
- ml-service migration v2 plan:
  `docs/plans/2026-05-09-ml-service-migration-v2.md`
- waitForVisibility introduction commit: `f429c5bf8` (c7)
- WBIA registration polling thread origin: `c6ffe5d20` (c11)
- Helper code:
  - `MlServiceProcessor.waitAndRunMatch` —
    `src/main/java/org/ecocean/ia/MlServiceProcessor.java:418`
  - `OpenSearch.waitForVisibility` —
    `src/main/java/org/ecocean/OpenSearch.java:498`
  - `Annotation.getMatchingSetQuery` —
    `src/main/java/org/ecocean/Annotation.java:925`
  - `Embedding.findMatchProspects` —
    `src/main/java/org/ecocean/Embedding.java:265`

**Do not write to any file when Codex reviews this.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll review this against the actual branch code and the referenced docs, staying read-only. I’m going to verify the current call paths, task/import relationships, and existing test seams before giving findings.
exec
/bin/bash -lc 'git status --short --branch' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "waitAndRunMatch|runDeferredMatch|runMatchProspects|enqueueDeferredMatch|waitForVisibility|importTaskId|class MlServiceProcessor" src/main/java src/test/java docs/plans' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "iaImageIds|iaAnnotationIdsStrict|parseAnnotationIdsArrayStrict|mediaAssetToUri|sendMediaAssetsForceId|validMediaAsset|WbiaRegisterRequest" src/main/java src/test/java docs/plans' in /mnt/c/Wildbook-clean2
 succeeded in 1427ms:
docs/plans/2026-05-09-ml-service-migration-v2.md:77:  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
docs/plans/2026-05-09-ml-service-migration-v2.md:84:                deferred handler retries waitForVisibility with longer ceiling
docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-09-ml-service-migration-v2.md:245:`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:
docs/plans/2026-05-09-ml-service-migration-v2.md:258:Diagnostic: at startup and at the entry of `waitForVisibility`, log a WARN if
docs/plans/2026-05-09-ml-service-migration-v2.md:311:| 7 | `OpenSearch.waitForVisibility` helper + `skipAutoIndexing` diagnostic | ~60 | — |
docs/plans/2026-05-09-ml-service-migration-v2.md:376:- `OpenSearch.waitForVisibility` (#7): visibility eventual-success returns true; timeout returns false; `skipAutoIndexing` flag triggers WARN.
docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
docs/plans/2026-05-18-empty-match-prospects-design.md:29:   — `MlServiceProcessor.waitAndRunMatch` calls
docs/plans/2026-05-18-empty-match-prospects-design.md:30:   `OpenSearch.waitForVisibility(annotation, persisted.annotationIds,
docs/plans/2026-05-18-empty-match-prospects-design.md:57:- Moving away from `waitForVisibility`. It works correctly — its
docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
docs/plans/2026-05-18-empty-match-prospects-design.md:68:| `MlServiceProcessor.waitAndRunMatch(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:418` | Current per-job wrapper; falls back to `enqueueDeferredMatch` on timeout. |
docs/plans/2026-05-18-empty-match-prospects-design.md:69:| `MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)` | `MlServiceProcessor.java:444` | Builds the match Task, attaches annotations, calls `Embedding.findMatchProspects`. |
docs/plans/2026-05-18-empty-match-prospects-design.md:70:| `MlServiceProcessor.enqueueDeferredMatch(annotationIds, parentTaskId)` | `MlServiceProcessor.java:681` | Re-queues the match via IAGateway with `mlServiceV2DeferredMatch: true`. |
docs/plans/2026-05-18-empty-match-prospects-design.md:72:| `Task` has parent/child relationship | `Task.java` | A child Task can carry an `importTaskId` parameter; we already use this. |
docs/plans/2026-05-18-empty-match-prospects-design.md:135:`waitAndRunMatch` today:
docs/plans/2026-05-18-empty-match-prospects-design.md:138:MlServiceProcessor.waitAndRunMatch(annotationIds=[A], taskId, matchConfig):
docs/plans/2026-05-18-empty-match-prospects-design.md:139:    waitForVisibility("annotation", [A], TIMEOUT)
docs/plans/2026-05-18-empty-match-prospects-design.md:141:    runMatchProspects([A], taskId, matchConfig)
docs/plans/2026-05-18-empty-match-prospects-design.md:148:parallel `waitAndRunMatch` invocations each handle their own
docs/plans/2026-05-18-empty-match-prospects-design.md:160:##### Seam A (chosen): in-place expansion at `waitAndRunMatch`
docs/plans/2026-05-18-empty-match-prospects-design.md:162:`waitAndRunMatch` already has the annotationIds for the current job
docs/plans/2026-05-18-empty-match-prospects-design.md:164:ImportTask (via `importTaskId` task parameter) and enumerate all
docs/plans/2026-05-18-empty-match-prospects-design.md:166:`waitForVisibility`.
docs/plans/2026-05-18-empty-match-prospects-design.md:169:private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds,
docs/plans/2026-05-18-empty-match-prospects-design.md:174:        if (!os.waitForVisibility("annotation", visibilitySet, VISIBILITY_TIMEOUT_MS)) {
docs/plans/2026-05-18-empty-match-prospects-design.md:175:            enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-design.md:179:        enqueueDeferredMatch(annotationIds, taskId);
docs/plans/2026-05-18-empty-match-prospects-design.md:182:    return runMatchProspects(annotationIds, taskId, matchConfig);
docs/plans/2026-05-18-empty-match-prospects-design.md:194:        String importTaskId = t.getParameters() == null ? null
docs/plans/2026-05-18-empty-match-prospects-design.md:195:            : t.getParameters().optString("importTaskId", null);
docs/plans/2026-05-18-empty-match-prospects-design.md:196:        if (!Util.stringExists(importTaskId)) { shep.rollbackAndClose(); return ids; }
docs/plans/2026-05-18-empty-match-prospects-design.md:198:        for (String batchAnnId : findImportBatchAnnotationIds(shep, importTaskId)) {
docs/plans/2026-05-18-empty-match-prospects-design.md:210:`findImportBatchAnnotationIds(shep, importTaskId)` resolves
docs/plans/2026-05-18-empty-match-prospects-design.md:216:The runMatchProspects call still passes the *original* (caller's)
docs/plans/2026-05-18-empty-match-prospects-design.md:238:- **ImportTask is large** (hundreds of annotations): `waitForVisibility`
docs/plans/2026-05-18-empty-match-prospects-design.md:243:  `enqueueDeferredMatch` for this case. We reuse it. Caller's
docs/plans/2026-05-18-empty-match-prospects-design.md:245:  additional waiting (`runDeferredMatch` → `runMatchProspects` at
docs/plans/2026-05-18-empty-match-prospects-design.md:251:  failed**: `waitForVisibility` will time out (failed annotations
docs/plans/2026-05-18-empty-match-prospects-design.md:256:- **Two imports overlap**: `importTaskId` is the boundary. Each
docs/plans/2026-05-18-empty-match-prospects-design.md:289:   `waitAndRunMatch`).
docs/plans/2026-05-18-empty-match-prospects-design.md:313:- `waitAndRunMatch_expandsToImportBatch_whenParentTaskHasImportTaskId`
docs/plans/2026-05-18-empty-match-prospects-design.md:316:- `waitAndRunMatch_passesOnlyCallerIdsToRunMatchProspects` —
docs/plans/2026-05-18-empty-match-prospects-design.md:318:- `waitAndRunMatch_fallsThroughWhenNoImportTaskParent` —
docs/plans/2026-05-18-empty-match-prospects-design.md:320:- `waitAndRunMatch_continuesOnExpansionFailure` — JDOQL throws;
docs/plans/2026-05-18-empty-match-prospects-design.md:322:- `waitAndRunMatch_defersOnExpandedTimeout` — `waitForVisibility`
docs/plans/2026-05-18-empty-match-prospects-design.md:323:  returns false on the wider set; verify `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-design.md:326:  with shared encounter; ensure boundary is by importTaskId, not
docs/plans/2026-05-18-empty-match-prospects-design.md:342:   is >5000 and proceed; `waitForVisibility` will handle the
docs/plans/2026-05-18-empty-match-prospects-design.md:345:   too** (`runDeferredMatch`)? Right now it skips visibility wait
docs/plans/2026-05-18-empty-match-prospects-design.md:349:   it (out of scope; this is purely a `waitAndRunMatch`
docs/plans/2026-05-18-empty-match-prospects-design.md:351:3. **Order of expansion vs validation**: if `importTaskId` is set
docs/plans/2026-05-18-empty-match-prospects-design.md:354:   yes — the visibility wait will time out, `enqueueDeferredMatch`
docs/plans/2026-05-18-empty-match-prospects-design.md:372:- waitForVisibility introduction commit: `f429c5bf8` (c7)
docs/plans/2026-05-18-empty-match-prospects-design.md:375:  - `MlServiceProcessor.waitAndRunMatch` —
docs/plans/2026-05-18-empty-match-prospects-design.md:377:  - `OpenSearch.waitForVisibility` —
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:30:class MlServiceProcessorTest {
src/main/java/org/ecocean/api/BulkImport.java:812:            taskParams.put("importTaskId", importId);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:42:    private String importTaskId = null;
src/main/java/org/ecocean/api/bulk/BulkImporter.java:65:        this.importTaskId = id;
src/main/java/org/ecocean/api/bulk/BulkImporter.java:716:        if (this.importTaskId == null) return;
src/main/java/org/ecocean/api/bulk/BulkImporter.java:722:            ImportTask itask = taskShepherd.getImportTask(this.importTaskId);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:1024:        logProgress(this.importTaskId, msg, this.startTime);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:1028:        return super.toString() + " [id:" + importTaskId + "] " + (dataRows ==
src/main/java/org/ecocean/Encounter.java:4395:            jgen.writeStringField("importTaskId", itask.getId());
src/main/java/org/ecocean/Encounter.java:4740:        map.put("importTaskId", keywordType);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:33:public class MlServiceProcessor {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:56:            return runDeferredMatch(jobData);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:433:    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:452:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:498:    public boolean waitForVisibility(String indexName, Collection<String> ids,
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/servlet/IAGateway.java:879:        String importTaskId = null;
src/main/java/org/ecocean/servlet/IAGateway.java:880:        if (taskParameters != null) importTaskId = taskParameters.optString("importTaskId", null);
src/main/java/org/ecocean/servlet/IAGateway.java:884:        if (importTaskId != null) itask = myShepherd.getImportTask(importTaskId);

 succeeded in 1408ms:
docs/plans/2026-05-18-empty-match-prospects-design.md:73:| `WildbookIAM.iaImageIds(context)` / `sendMediaAssetsForceId(…)` / `mediaAssetToUri(…)` / `validMediaAsset(…)` | `WildbookIAM.java` | Existing helpers the WBIA-side design reuses. See the paused WBIA design doc for the helper-by-helper audit. |
docs/plans/2026-05-18-empty-match-prospects-design.md:89:1. `mediaAssetToUri(MediaAsset)` is `private` in `WildbookIAM`.
docs/plans/2026-05-18-empty-match-prospects-design.md:94:2. The strict `iaImageIdsStrict` variant introduces caching that the
docs/plans/2026-05-18-empty-match-prospects-design.md:95:   lenient `iaImageIds` doesn't currently have. The new cache key is
docs/plans/2026-05-18-empty-match-prospects-design.md:96:   `iaImageIdsStrict` (15-min QueryCache, matching the
docs/plans/2026-05-18-empty-match-prospects-design.md:97:   `iaAnnotationIdsStrict` pattern from the c11 fix-pass). The
docs/plans/2026-05-18-empty-match-prospects-design.md:99:3. Phase A's eligibility check gains a `validMediaAsset(ma)` call
docs/plans/2026-05-18-empty-match-prospects-design.md:101:   `sendMediaAssetsForceId` uses them (lines 121–130 of
docs/plans/2026-05-18-empty-match-prospects-design.md:109:   `parseAnnotationIdsArrayStrict`. A shared
docs/plans/2026-05-18-empty-match-prospects-design.md:116:7. The `WbiaRegisterRequest` DTO gains four image-side fields,
docs/plans/2026-05-18-empty-match-prospects-design.md:129:`sendMediaAssetsForceId` uses for `image_uuid_list`.
docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-wbia-image-registration-design.md:50:| `WildbookIAM.sendMediaAssetsForceId(ArrayList<MediaAsset>, boolean)` | exists, batch POST to `/api/image/json/`, sends our acmId via `image_uuid_list` | The POST body shape and `toFancyUUID` wrapping logic lift line-for-line into the new DTO-based variant |
docs/plans/2026-05-18-wbia-image-registration-design.md:51:| `WildbookIAM.mediaAssetToUri(MediaAsset)` | exists but is **`private`** at line 804. Returns the double-encoded web URL string. Note: `ma.webURL()` can return null and the existing helper dereferences without checking — a long-standing latent NPE. | Promote to `public static String` (signature change from `Object` return; tighten the null-check). Phase A then calls it directly to capture `imageUri` into the DTO. |
docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-wbia-image-registration-design.md:59:Extend `WildbookIAM.WbiaRegisterRequest` with four image-side fields,
docs/plans/2026-05-18-wbia-image-registration-design.md:63:public final String imageUri;             // mediaAssetToUri(ma) result
docs/plans/2026-05-18-wbia-image-registration-design.md:71:`dto.mediaAssetAcmId` already carries the value `sendMediaAssetsForceId`
docs/plans/2026-05-18-wbia-image-registration-design.md:79:checks in the same block (mirroring what `sendMediaAssetsForceId` does
docs/plans/2026-05-18-wbia-image-registration-design.md:85:} else if (!WildbookIAM.validMediaAsset(ma)) {
docs/plans/2026-05-18-wbia-image-registration-design.md:86:    reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
docs/plans/2026-05-18-wbia-image-registration-design.md:96:String imageUri = (String) WildbookIAM.mediaAssetToUri(ma);  // method returns Object; cast
docs/plans/2026-05-18-wbia-image-registration-design.md:99:WbiaRegisterRequest dto = new WbiaRegisterRequest(
docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-wbia-image-registration-design.md:111:Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
docs/plans/2026-05-18-wbia-image-registration-design.md:120:`apiGetJSONArray` and `parseAnnotationIdsArrayStrict`-style helpers; the
docs/plans/2026-05-18-wbia-image-registration-design.md:124:### New `sendMediaAssetByDto(WbiaRegisterRequest dto)` in `WildbookIAM`
docs/plans/2026-05-18-wbia-image-registration-design.md:126:Single-image variant of `sendMediaAssetsForceId`, DTO-only (no Shepherd
docs/plans/2026-05-18-wbia-image-registration-design.md:130:public boolean sendMediaAssetByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-wbia-image-registration-design.md:148:static HashMap<String, ArrayList> buildSingleImageRequestMap(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-wbia-image-registration-design.md:176:public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-wbia-image-registration-design.md:199:    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-wbia-image-registration-design.md:228:already-registered image (skip-if-present inside `sendMediaAssetsForceId`
docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-wbia-image-registration-design.md:249:  `parseAnnotationIdsArrayStrict`: null array returns empty; well-formed
docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-wbia-image-registration-design.md:261:  iaAnnotationIdsStrict called after (Phase 1 reached).
docs/plans/2026-05-18-wbia-image-registration-design.md:272:mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
docs/plans/2026-05-18-wbia-image-registration-design.md:279:| MediaAsset fails validMediaAsset at Phase A | Park (MAX_ATTEMPTS) | New eligibility check added |
docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-wbia-image-registration-design.md:306:3. **Strict-parser duplication**: `parseAnnotationIdsArrayStrict` already
docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:138:    // --- parseAnnotationIdsArrayStrict -----------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:140:    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:141:        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:144:    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:148:        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:154:    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:159:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:162:    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:167:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/main/java/org/ecocean/identity/IBEISIA.java:148:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/identity/IBEISIA.java:834:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/identity/IBEISIA.java:835:        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
src/main/java/org/ecocean/identity/IBEISIA.java:3710:        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
src/main/java/org/ecocean/identity/IBEISIA.java:3711:        HashSet<String> iaImageIds = null;
src/main/java/org/ecocean/identity/IBEISIA.java:3718:            // get iaImageIds only if we need it
src/main/java/org/ecocean/identity/IBEISIA.java:3719:            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
src/main/java/org/ecocean/identity/IBEISIA.java:3720:            if (iaImageIds.isEmpty())
src/main/java/org/ecocean/identity/IBEISIA.java:3721:                throw new RuntimeException("iaImageIds is empty; possible IA problems");
src/main/java/org/ecocean/identity/IBEISIA.java:3722:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:127:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:178:    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:209:                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:213:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:214:                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:230:                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:234:                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:238:                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:242:                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:495:                return parseAnnotationIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:520:        return parseAnnotationIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530:    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:560:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:617:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:745:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:753:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:804:    private static Object mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:    public static boolean validMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:826:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:828:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/StartupWildbook.java:705:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:747:    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
src/main/java/org/ecocean/StartupWildbook.java:788:            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:789:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
src/main/java/org/ecocean/servlet/importer/ImportTask.java:659:                    invalidMediaAssets.add(asset);

codex
The branch already contains the combined design doc you pasted. I’ve confirmed the current code still has the per-job `waitForVisibility` call, so the second track is reviewing a real gap, not a stale design assumption.
 succeeded in 12965ms:
## migrate-ml-service-v2...origin/migrate-ml-service-v2 [ahead 1]
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/OpenSearch.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/StartupWildbook.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MatchResult.java
 M src/main/java/org/ecocean/ia/MatchResultProspect.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/ia/MlServiceProcessor.java
 M src/main/java/org/ecocean/ia/Task.java
 M src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/WEB-INF/web.xml
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? docs/plans/2026-05-18-empty-match-prospects-design.md
?? logs/

codex
There are many dirty files in the worktree, including the new design doc as untracked. I’ll ignore unrelated churn and keep the review grounded in the files that affect these two tracks.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '400,730p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/OpenSearch.java | sed -n '450,560p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/MlServiceProcessorTest.java | sed -n '1,260p'" in /mnt/c/Wildbook-clean2
 succeeded in 320ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.IOException;
     4	import java.net.URL;
     5	import java.util.ArrayList;
     6	import java.util.List;
     7	import java.util.Locale;
     8	
     9	import org.ecocean.Annotation;
    10	import org.ecocean.Embedding;
    11	import org.ecocean.Encounter;
    12	import org.ecocean.IAJsonProperties;
    13	import org.ecocean.OpenSearch;
    14	import org.ecocean.Taxonomy;
    15	import org.ecocean.Util;
    16	import org.ecocean.identity.IBEISIA;
    17	import org.ecocean.media.Feature;
    18	import org.ecocean.media.FeatureType;
    19	import org.ecocean.media.MediaAsset;
    20	import org.ecocean.servlet.IAGateway;
    21	import org.ecocean.shepherd.core.Shepherd;
    22	import org.json.JSONArray;
    23	import org.json.JSONObject;
    24	
    25	/**
    26	 * Single-job orchestrator for ml-service v2 queue payloads.
    27	 *
    28	 * <p>Owns Shepherd transaction boundaries: load/revalidate, release DB while
    29	 * the network call runs, then reopen for idempotent persistence and matching.
    30	 * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
    31	 * a later commit.</p>
    32	 */
    33	public class MlServiceProcessor {
    34	    private static final String ACTION_PREFIX = "MlServiceProcessor.";
    35	    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";
    36	    private static final long VISIBILITY_TIMEOUT_MS = 5000L;
    37	
    38	    private final String context;
    39	    private final MlServiceClient client;
    40	
    41	    public MlServiceProcessor(String context) {
    42	        this(context, new MlServiceClient());
    43	    }
    44	
    45	    public MlServiceProcessor(String context, MlServiceClient client) {
    46	        this.context = context;
    47	        this.client = client;
    48	    }
    49	
    50	    /** Process one ml-service queue job. Returns the outcome. */
    51	    public MlServiceJobOutcome process(JSONObject jobData) {
    52	        if (jobData == null) {
    53	            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
    54	        }
    55	        if (jobData.optBoolean("deferredMatch", false)) {
    56	            return runDeferredMatch(jobData);
    57	        }
    58	
    59	        String taxonomyString = jobData.optString("taxonomyString", null);
    60	        String taskId = jobData.optString("taskId", null);
    61	        String encounterId = jobData.optString("encounterId", null);
    62	
    63	        if (jobData.has("mediaAssetId")) {
    64	            String maId = String.valueOf(jobData.opt("mediaAssetId"));
    65	            return processDetection(jobData, taxonomyString, taskId, encounterId, maId);
    66	        }
    67	        if (jobData.has("annotationId")) {
    68	            String annId = jobData.optString("annotationId", null);
    69	            return processExtraction(jobData, taxonomyString, taskId, annId);
    70	        }
    71	        return MlServiceJobOutcome.validationError("INVALID_PAYLOAD",
    72	            "neither mediaAssetId nor annotationId in payload");
    73	    }
    74	
    75	    private MlServiceJobOutcome processDetection(JSONObject jobData, String taxonomyString,
    76	        String taskId, String encounterId, String maId) {
    77	        DetectionContext det = null;
    78	
    79	        try {
    80	            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
    81	        } catch (Exception ex) {
    82	            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
    83	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
    84	        }
    85	        if (det.outcome != null) return det.outcome;
    86	
    87	        JSONObject response;
    88	        try {
    89	            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
    90	        } catch (IAException ex) {
    91	            if (ex.shouldRequeue()) {
    92	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
    93	                return MlServiceJobOutcome.requeue();
    94	            }
    95	            markTaskError(taskId, ex.getCode(), ex.getMessage());
    96	            return mapNonRetryableError(ex);
    97	        }
    98	
    99	        JSONArray results = response.optJSONArray("results");
   100	        if (results == null || results.length() == 0) {
   101	            return finalizeZeroDetections(maId, taskId);
   102	        }
   103	
   104	        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results);
   105	        if (persisted.outcome != null) return persisted.outcome;
   106	
   107	        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
   108	            det.mlConfig);
   109	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   110	            matchConfig);
   111	        if (matchOutcome != null) return matchOutcome;
   112	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   113	    }
   114	
   115	    private MlServiceJobOutcome processExtraction(JSONObject jobData, String taxonomyString,
   116	        String taskId, String annId) {
   117	        ExtractionContext ext = null;
   118	
   119	        try {
   120	            ext = loadExtractionContext(taxonomyString, taskId, annId);
   121	        } catch (Exception ex) {
   122	            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
   123	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
   124	        }
   125	        if (ext.outcome != null) return ext.outcome;
   126	
   127	        JSONObject response;
   128	        try {
   129	            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
   130	                ext.mlConfig);
   131	        } catch (IAException ex) {
   132	            if (ex.shouldRequeue()) {
   133	                IAGateway.requeueJob(jobData, ex.shouldIncrement());
   134	                return MlServiceJobOutcome.requeue();
   135	            }
   136	            markTaskError(taskId, ex.getCode(), ex.getMessage());
   137	            return mapNonRetryableError(ex);
   138	        }
   139	
   140	        PersistResult persisted = persistExtraction(annId, taskId, ext, response);
   141	        if (persisted.outcome != null) return persisted.outcome;
   142	
   143	        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
   144	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   145	            matchConfig);
   146	        if (matchOutcome != null) return matchOutcome;
   147	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   148	    }
   149	
   150	    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
   151	        String encounterId, String maId) {
   152	        Shepherd shep = new Shepherd(context);
   153	        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
   154	        try {
   155	            FeatureType.initAll(shep);
   156	            shep.beginDBTransaction();
   157	            MediaAsset ma = shep.getMediaAsset(maId);
   158	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   159	            Task task = Task.load(taskId, shep);
   160	
   161	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   162	            if (staleReason != null) {
   163	                markTaskDroppedStale(shep, task, staleReason);
   164	                shep.commitDBTransaction();
   165	                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
   166	            }
   167	
   168	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
   169	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   170	            if (configs == null) {
   171	                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
   172	                markTaskCompleted(task);
   173	                shep.commitDBTransaction();
   174	                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
   175	            }
   176	
   177	            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
   178	                markTaskError(task, "INVALID",
   179	                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
   180	                shep.commitDBTransaction();

 succeeded in 320ms:
   400	
   401	            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   402	            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
   403	                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
   404	            shep.getPM().makePersistent(emb);
   405	            markTaskCompleted(task);
   406	            annotationIds.add(ann.getId());
   407	            shep.commitDBTransaction();
   408	            return PersistResult.ok(annotationIds);
   409	        } catch (Exception ex) {
   410	            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
   411	            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
   412	                ex.getMessage()));
   413	        } finally {
   414	            shep.rollbackAndClose();
   415	        }
   416	    }
   417	
   418	    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
   419	        JSONObject matchConfig) {
   420	        try {
   421	            OpenSearch os = new OpenSearch();
   422	            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
   423	                enqueueDeferredMatch(annotationIds, taskId);
   424	                return MlServiceJobOutcome.ok(annotationIds);
   425	            }
   426	        } catch (IOException ex) {
   427	            enqueueDeferredMatch(annotationIds, taskId);
   428	            return MlServiceJobOutcome.ok(annotationIds);
   429	        }
   430	        return runMatchProspects(annotationIds, taskId, matchConfig);
   431	    }
   432	
   433	    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
   434	        if (jobData == null) {
   435	            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
   436	        }
   437	        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
   438	        String taskId = jobData.optString("taskId", null);
   439	        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
   440	        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
   441	        return runMatchProspects(annotationIds, taskId, matchConfig);
   442	    }
   443	
   444	    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
   445	        JSONObject matchConfig) {
   446	        if (annotationIds == null || annotationIds.isEmpty()) {
   447	            markTaskCompleted(taskId);
   448	            return MlServiceJobOutcome.ok(new ArrayList<String>());
   449	        }
   450	
   451	        Shepherd shep = new Shepherd(context);
   452	        shep.setAction(ACTION_PREFIX + "runMatchProspects");
   453	        try {
   454	            shep.beginDBTransaction();
   455	            List<Annotation> anns = new ArrayList<Annotation>();
   456	            for (String annId : annotationIds) {
   457	                Annotation ann = shep.getAnnotation(annId);
   458	                if (ann != null) anns.add(ann);
   459	            }
   460	            if (anns.isEmpty()) {
   461	                Task task = Task.load(taskId, shep);
   462	                markTaskDroppedStale(shep, task, "annotations missing");
   463	                shep.commitDBTransaction();
   464	                return MlServiceJobOutcome.stale("annotations missing");
   465	            }
   466	
   467	            Task parent = Task.load(taskId, shep);
   468	            Task matchTask = (parent == null) ? new Task() : new Task(parent);
   469	            matchTask.setObjectAnnotations(anns);
   470	            matchTask.addParameter("mlServiceV2Match", true);
   471	            shep.getPM().makePersistent(matchTask);
   472	            // findMatchProspects returns false when the match config is not
   473	            // a vector config or matchConfig is null. Don't leave the match
   474	            // task without a terminal status — mark the parent task error.
   475	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
   476	            if (!ran) {
   477	                matchTask.setStatus("error");
   478	                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
   479	                    "findMatchProspects rejected match config: " +
   480	                    (matchConfig == null ? "null" : matchConfig.toString()));
   481	                matchTask.setCompletionDateInMilliseconds();
   482	                // Update the parent task in this same transaction (parent is
   483	                // already loaded above) so the two updates commit atomically.
   484	                // Splitting across transactions risks leaving the parent
   485	                // "completed" if the second commit fails or the JVM dies.
   486	                if (parent != null) {
   487	                    markTaskError(parent, "INVALID_MATCH_CONFIG",
   488	                        "no usable vector match config");
   489	                }
   490	                shep.commitDBTransaction();
   491	                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
   492	                    "no usable vector match config");
   493	            }
   494	            shep.commitDBTransaction();
   495	            return MlServiceJobOutcome.ok(annotationIds);
   496	        } catch (Exception ex) {
   497	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
   498	            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
   499	        } finally {
   500	            shep.rollbackAndClose();
   501	        }
   502	    }
   503	
   504	    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
   505	        String code = ex == null ? null : ex.getCode();
   506	        String message = ex == null ? null : ex.getMessage();
   507	        if ("INVALID".equals(code) || "SUCCESS_FALSE".equals(code)) {
   508	            return MlServiceJobOutcome.validationError(code, message);
   509	        }
   510	        if ("TIMEOUT".equals(code) || "NETWORK".equals(code) || "RATE_LIMITED".equals(code)
   511	            || "SERVER_ERROR".equals(code) || "CLIENT_ERROR".equals(code)) {
   512	            return MlServiceJobOutcome.networkError(code, message);
   513	        }
   514	        return MlServiceJobOutcome.networkError("UNKNOWN", message);
   515	    }
   516	
   517	    static String bboxKey(double[] bbox) {
   518	        if (bbox == null || bbox.length != 4) return null;
   519	        return Math.round(bbox[0]) + ":" + Math.round(bbox[1]) + ":" + Math.round(bbox[2]) +
   520	            ":" + Math.round(bbox[3]);
   521	    }
   522	
   523	    static String thetaKey(double theta) {
   524	        return String.format(Locale.US, "%.4f", theta);
   525	    }
   526	
   527	    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
   528	        String bboxKey, String thetaKey) {
   529	        if (ma == null) return null;
   530	        for (Annotation ann : ma.getAnnotations()) {
   531	            if (ann == null) continue;
   532	            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
   533	            if (!sameString(bboxKey, ann.getBboxKey())) continue;
   534	            if (!sameString(thetaKey, ann.getThetaKey())) continue;
   535	            return ann;
   536	        }
   537	        return null;
   538	    }
   539	
   540	    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
   541	        if (!Util.stringExists(taxonomyString)) return null;
   542	        IAJsonProperties iac = IAJsonProperties.iaConfig();
   543	        if (iac == null) return null;
   544	        Taxonomy taxy = shep.getOrCreateTaxonomy(taxonomyString, false);
   545	        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
   546	        if (configs == null || configs.length() == 0) return null;
   547	        JSONObject mlConfig = configs.optJSONObject(0);
   548	        if (mlConfig == null) return null;
   549	        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
   550	        return new ConfigPair(mlConfig, matchConfig);
   551	    }
   552	
   553	    private JSONObject defaultMatchConfig(IAJsonProperties iac, Taxonomy taxy,
   554	        JSONObject mlConfig) {
   555	        JSONObject matchConfig = null;
   556	        JSONArray identConfigs = iac.getIdentConfig(taxy);
   557	        if (identConfigs != null) {
   558	            for (int i = 0; i < identConfigs.length(); i++) {
   559	                JSONObject entry = identConfigs.optJSONObject(i);
   560	                if (entry == null) continue;
   561	                if (entry.optBoolean("default", false)
   562	                    && "vector".equals(entry.optString("pipeline_root", null))) {
   563	                    matchConfig = new JSONObject(entry.toString());
   564	                    break;
   565	                }
   566	            }
   567	        }
   568	        if (matchConfig == null) matchConfig = new JSONObject();
   569	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
   570	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   571	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
   572	        }
   573	        return matchConfig;
   574	    }
   575	
   576	    private JSONObject inferMatchConfig(List<String> annotationIds) {
   577	        if (annotationIds == null || annotationIds.isEmpty()) return null;
   578	        Shepherd shep = new Shepherd(context);
   579	        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
   580	        try {
   581	            shep.beginDBTransaction();
   582	            for (String annId : annotationIds) {
   583	                Annotation ann = shep.getAnnotation(annId);
   584	                if (ann == null) continue;
   585	                Embedding emb = ann.getAnEmbedding();
   586	                if (emb != null) {
   587	                    JSONObject config = new JSONObject();
   588	                    config.put("method", emb.getMethod());
   589	                    config.put("version", emb.getMethodVersion());
   590	                    config.put("pipeline_root", "vector");
   591	                    return config;
   592	                }
   593	            }
   594	            return null;
   595	        } finally {
   596	            shep.rollbackAndClose();
   597	        }
   598	    }
   599	
   600	    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
   601	        JSONObject mlConfig) {
   602	        JSONObject config = (matchConfig == null) ? new JSONObject()
   603	            : new JSONObject(matchConfig.toString());
   604	        if (embeddingSource != null) {
   605	            if (!Util.stringExists(config.optString("method", null))
   606	                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
   607	                config.put("method", embeddingSource.optString("embedding_model_id"));
   608	            }
   609	            if (!Util.stringExists(config.optString("version", null))
   610	                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
   611	                config.put("version", embeddingSource.optString("embedding_model_version"));
   612	            }
   613	        }
   614	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
   615	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   616	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
   617	        }
   618	        return config;
   619	    }
   620	
   621	    private String detectionStaleReason(MediaAsset ma, Encounter enc, String encounterId) {
   622	        if (ma == null) return "media asset missing";
   623	        if (Util.stringExists(encounterId) && enc == null) return "encounter missing";
   624	        String status = ma.getDetectionStatus();
   625	        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
   626	        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
   627	        return null;
   628	    }
   629	
   630	    private String effectiveTaxonomyString(String taxonomyString, Encounter enc) {
   631	        if (Util.stringExists(taxonomyString)) return taxonomyString;
   632	        if (enc != null) return enc.getTaxonomyString();
   633	        return null;
   634	    }
   635	
   636	    private void markTaskError(String taskId, String code, String message) {
   637	        Shepherd shep = new Shepherd(context);
   638	        shep.setAction(ACTION_PREFIX + "markTaskError");
   639	        try {
   640	            shep.beginDBTransaction();
   641	            Task task = Task.load(taskId, shep);
   642	            markTaskError(task, code, message);
   643	            shep.commitDBTransaction();
   644	        } finally {
   645	            shep.rollbackAndClose();
   646	        }
   647	    }
   648	
   649	    private void markTaskCompleted(String taskId) {
   650	        Shepherd shep = new Shepherd(context);
   651	        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
   652	        try {
   653	            shep.beginDBTransaction();
   654	            markTaskCompleted(Task.load(taskId, shep));
   655	            shep.commitDBTransaction();
   656	        } finally {
   657	            shep.rollbackAndClose();
   658	        }
   659	    }
   660	
   661	    private void markTaskError(Task task, String code, String message) {
   662	        if (task == null) return;
   663	        task.setStatus(IBEISIA.STATUS_ERROR);
   664	        task.setStatusDetailsAddError(code == null ? "UNKNOWN" : code, message);
   665	        task.setCompletionDateInMilliseconds();
   666	    }
   667	
   668	    private void markTaskCompleted(Task task) {
   669	        if (task == null) return;
   670	        task.setStatus("completed");
   671	        task.setCompletionDateInMilliseconds();
   672	    }
   673	
   674	    private void markTaskDroppedStale(Shepherd shep, Task task, String reason) {
   675	        if (task == null) return;
   676	        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
   677	        task.setStatusDetailsAddLog(reason);
   678	        task.setCompletionDateInMilliseconds();
   679	    }
   680	
   681	    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
   682	        JSONObject payload = new JSONObject();
   683	        payload.put("mlServiceV2", true);
   684	        payload.put("deferredMatch", true);
   685	        payload.put("annotationIds", new JSONArray(annotationIds));
   686	        if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
   687	        // Carry __context in the payload so the dispatcher's
   688	        // jobj.optString("__context", "context0") fallback at IAGateway.java
   689	        // doesn't silently route the deferred-match into context0 when this
   690	        // processor is running in a non-default context.
   691	        payload.put("__context", context);
   692	        try {
   693	            IAGateway.addToDetectionQueue(context, payload.toString());
   694	        } catch (IOException ex) {
   695	            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
   696	        }
   697	    }
   698	
   699	    private static JSONObject featureParams(double[] bbox, double theta, String viewpoint) {
   700	        JSONObject params = new JSONObject();
   701	        params.put("x", bbox[0]);
   702	        params.put("y", bbox[1]);
   703	        params.put("width", bbox[2]);
   704	        params.put("height", bbox[3]);
   705	        params.put("theta", theta);
   706	        params.put("viewpoint", viewpoint);
   707	        return params;
   708	    }
   709	
   710	    private static double[] parseBbox(JSONArray bbox) {
   711	        return new double[] {
   712	            bbox.getDouble(0), bbox.getDouble(1), bbox.getDouble(2), bbox.getDouble(3)
   713	        };
   714	    }
   715	
   716	    private static double[] toDoubleArray(int[] bbox) {
   717	        return new double[] { bbox[0], bbox[1], bbox[2], bbox[3] };
   718	    }
   719	
   720	    private static List<String> jsonArrayToStringList(JSONArray array) {
   721	        List<String> values = new ArrayList<String>();
   722	        if (array == null) return values;
   723	        for (int i = 0; i < array.length(); i++) {
   724	            String value = array.optString(i, null);
   725	            if (Util.stringExists(value)) values.add(value);
   726	        }
   727	        return values;
   728	    }
   729	
   730	    private static Double optionalFiniteDouble(JSONObject obj, String key, Double fallback) {

 succeeded in 271ms:
     1	package org.ecocean.ia;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertFalse;
     5	import static org.junit.jupiter.api.Assertions.assertNotNull;
     6	import static org.junit.jupiter.api.Assertions.assertNull;
     7	import static org.junit.jupiter.api.Assertions.assertTrue;
     8	
     9	import org.json.JSONObject;
    10	import org.junit.jupiter.api.Test;
    11	
    12	/**
    13	 * v2 commit #9: pure-logic tests for MlServiceProcessor.
    14	 *
    15	 * <p>The Phase 1-5 lifecycle methods require real Shepherd transactions,
    16	 * JDO mutations, and live OpenSearch — those are reviewable by diff and
    17	 * exercised by hand-test per the v2 plan's test-strategy decision
    18	 * (WireMock unit tests only). Here we cover:</p>
    19	 *
    20	 * <ul>
    21	 *   <li>Top-level {@code process()} payload routing (validation errors,
    22	 *       missing-payload-fields branches).</li>
    23	 *   <li>{@code mapNonRetryableError(IAException)} maps each typed code
    24	 *       to the right outcome Kind.</li>
    25	 *   <li>{@code bboxKey}/{@code thetaKey} formatting (rounding and
    26	 *       string-format invariants).</li>
    27	 *   <li>{@code findExistingAnnotation} dedupe matching.</li>
    28	 * </ul>
    29	 */
    30	class MlServiceProcessorTest {
    31	
    32	    // --- process() payload routing -------------------------------------
    33	
    34	    @Test void processRejectsNullPayload() {
    35	        MlServiceProcessor p = new MlServiceProcessor("context0");
    36	        MlServiceJobOutcome out = p.process(null);
    37	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    38	        assertEquals("INVALID_PAYLOAD", out.getCode());
    39	    }
    40	
    41	    @Test void processRejectsPayloadWithoutMediaAssetOrAnnotationId() {
    42	        MlServiceProcessor p = new MlServiceProcessor("context0");
    43	        JSONObject payload = new JSONObject()
    44	            .put("mlServiceV2", true)
    45	            .put("taxonomyString", "Rhincodon typus");
    46	        MlServiceJobOutcome out = p.process(payload);
    47	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    48	        assertEquals("INVALID_PAYLOAD", out.getCode());
    49	        assertNotNull(out.getMessage());
    50	    }
    51	
    52	    // --- mapNonRetryableError ------------------------------------------
    53	
    54	    @Test void mapNonRetryableInvalidIsValidationError() {
    55	        IAException ex = new IAException("INVALID", "bad bbox", false, false);
    56	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    57	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    58	        assertEquals("INVALID", out.getCode());
    59	    }
    60	
    61	    @Test void mapNonRetryableSuccessFalseIsValidationError() {
    62	        IAException ex = new IAException("SUCCESS_FALSE",
    63	            "ml-service success=false", false, false);
    64	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    65	        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
    66	        assertEquals("SUCCESS_FALSE", out.getCode());
    67	    }
    68	
    69	    @Test void mapNonRetryableNetworkIsNetworkError() {
    70	        IAException ex = new IAException("NETWORK",
    71	            "ml-service 502", false, false);
    72	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    73	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    74	        assertEquals("NETWORK", out.getCode());
    75	    }
    76	
    77	    @Test void mapNonRetryableTimeoutIsNetworkError() {
    78	        IAException ex = new IAException("TIMEOUT",
    79	            "ml-service read timed out", false, false);
    80	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    81	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    82	    }
    83	
    84	    @Test void mapNonRetryableClientErrorIsNetworkError() {
    85	        // 4xx surfaces as CLIENT_ERROR from the client. The processor's
    86	        // contract: anything not VALIDATION* maps to NETWORK (with code preserved).
    87	        IAException ex = new IAException("CLIENT_ERROR",
    88	            "ml-service 400", false, false);
    89	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    90	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    91	        assertEquals("CLIENT_ERROR", out.getCode());
    92	    }
    93	
    94	    @Test void mapNonRetryableUnknownCodeIsNetworkError() {
    95	        IAException ex = new IAException("WEIRD_CODE",
    96	            "something happened", false, false);
    97	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
    98	        // Defensive default: unknown codes route to NETWORK rather than
    99	        // crashing the processor.
   100	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
   101	    }
   102	
   103	    @Test void mapNonRetryableNullCode() {
   104	        // IAException constructed with the legacy 1/2/3-arg constructors
   105	        // leaves code null. Treat as ERROR_NETWORK.
   106	        IAException ex = new IAException("legacy message");
   107	        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
   108	        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
   109	    }
   110	
   111	    // --- bboxKey / thetaKey -------------------------------------------
   112	
   113	    @Test void bboxKeyRoundsToInts() {
   114	        // The composite-unique-index columns are literal strings so we get
   115	        // debugability over hash opacity. Rounded ints from a 4-element double[].
   116	        assertEquals("10:20:30:40",
   117	            MlServiceProcessor.bboxKey(new double[] { 10.0, 20.0, 30.0, 40.0 }));
   118	        assertEquals("10:20:30:40",
   119	            MlServiceProcessor.bboxKey(new double[] { 10.4, 20.4, 30.4, 40.4 }));
   120	        assertEquals("11:21:31:41",
   121	            MlServiceProcessor.bboxKey(new double[] { 10.5, 20.5, 30.5, 40.5 }));
   122	    }
   123	
   124	    @Test void thetaKeyRoundsToFourDecimals() {
   125	        assertEquals("0.0000", MlServiceProcessor.thetaKey(0.0));
   126	        assertEquals("3.1416", MlServiceProcessor.thetaKey(3.1415926));
   127	        // Negative angles round symmetrically.
   128	        assertEquals("-1.5708", MlServiceProcessor.thetaKey(-1.5707963));
   129	    }
   130	
   131	    @Test void thetaKeyHandlesNegativeZero() {
   132	        // Negative zero formats the same as positive zero, matching the
   133	        // expected key for "theta is zero".
   134	        String k = MlServiceProcessor.thetaKey(-0.0);
   135	        assertTrue(k.equals("0.0000") || k.equals("-0.0000"),
   136	            "unexpected thetaKey for -0.0: " + k);
   137	    }
   138	}

 succeeded in 283ms:
   450	    throws IOException {
   451	        if (scrollData == null) throw new IOException("null data passed");
   452	        String scrollId = scrollData.optString("_scroll_id", null);
   453	        if (scrollData == null) throw new IOException("no _scroll_id");
   454	        JSONObject data = new JSONObject();
   455	        data.put("scroll", SEARCH_SCROLL_TIME);
   456	        data.put("scroll_id", scrollId);
   457	        Request searchRequest = new Request("POST", "_search/scroll");
   458	        searchRequest.setJsonEntity(data.toString());
   459	        String rtn = getRestResponse(searchRequest);
   460	        return new JSONObject(rtn);
   461	    }
   462	
   463	    // ml-service migration v2 (commit #7): force pending writes in `indexName`
   464	    // through Lucene's refresh boundary so they are searchable. Synchronous;
   465	    // returns after targeted shards have completed the refresh. NOT a Wildbook
   466	    // queue drain — IndexingManager may still have unindexed entities queued.
   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
   468	    public void indexRefresh(final String indexName)
   469	    throws IOException {
   470	        if (!isValidIndexName(indexName))
   471	            throw new IOException("invalid index name: " + indexName);
   472	        Request req = new Request("POST", indexName + "/_refresh");
   473	        getRestResponse(req);   // discard body; non-2xx surfaces as IOException
   474	    }
   475	
   476	    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
   477	    // can see every id in `ids` in `indexName`. Used by MlServiceProcessor
   478	    // (commit #9) post-persist to avoid running findMatchProspects against an
   479	    // index that doesn't yet contain the freshly-written annotations.
   480	    //
   481	    // On entry:
   482	    //   - normalizes `ids` to a Set (drops nulls and duplicates so they can't
   483	    //     prevent the count check from ever succeeding);
   484	    //   - calls _refresh once (synchronous; pushes pending writes through
   485	    //     Lucene's refresh boundary);
   486	    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
   487	    //     every poll return zero hits regardless of how long we wait.
   488	    //
   489	    // Then polls a _count eligibility query with exponential backoff (start
   490	    // 100ms, double, cap 1s) until count >= |normalized ids| OR the total
   491	    // wait reaches timeoutMs. Returns true on visible-success, false on
   492	    // timeout. Caller decides what to do on false (e.g. enqueue a deferred-
   493	    // match job rather than match against a partial index).
   494	    //
   495	    // Does NOT try to drain the Wildbook IndexingManager queue. That queue
   496	    // may contain unrelated entities; queue-depth zero doesn't imply the
   497	    // specific IDs are queryable. Polling visibility IS the correctness gate.
   498	    public boolean waitForVisibility(String indexName, Collection<String> ids,
   499	        long timeoutMs)
   500	    throws IOException {
   501	        if (!isValidIndexName(indexName))
   502	            throw new IOException("invalid index name: " + indexName);
   503	        if (ids == null || ids.isEmpty()) return true;
   504	
   505	        // Normalize: drop nulls + duplicates so the count comparison is
   506	        // against the true number of distinct documents we expect to see.
   507	        Set<String> targetIds = new LinkedHashSet<String>();
   508	        for (String id : ids) {
   509	            if (id != null) targetIds.add(id);
   510	        }
   511	        if (targetIds.isEmpty()) return true;
   512	
   513	        if (skipAutoIndexing()) {
   514	            System.out.println(
   515	                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
   516	                "— every poll will return zero hits regardless of wait time.");
   517	        }
   518	
   519	        indexRefresh(indexName);
   520	
   521	        JSONObject query = buildIdEligibilityQuery(targetIds);
   522	        long deadline = System.currentTimeMillis() + timeoutMs;
   523	        long sleepMs = 100;
   524	        while (true) {
   525	            int seen = queryCount(indexName, query);
   526	            if (seen >= targetIds.size()) return true;
   527	            long remaining = deadline - System.currentTimeMillis();
   528	            if (remaining <= 0) return false;
   529	            try {
   530	                Thread.sleep(Math.min(sleepMs, remaining));
   531	            } catch (InterruptedException ex) {
   532	                Thread.currentThread().interrupt();
   533	                return false;
   534	            }
   535	            sleepMs = Math.min(sleepMs * 2, 1000);
   536	        }
   537	    }
   538	
   539	    // Package-visible for testing. Returns the _count-shaped query body that
   540	    // filters on _id ∈ ids, using OpenSearch's idiomatic `ids` query.
   541	    static JSONObject buildIdEligibilityQuery(Set<String> ids) {
   542	        JSONArray idArr = new JSONArray();
   543	        for (String id : ids) idArr.put(id);
   544	        JSONObject query = new JSONObject();
   545	        query.put("query",
   546	            new JSONObject().put("ids",
   547	                new JSONObject().put("values", idArr)));
   548	        return query;
   549	    }
   550	
   551	    // when you only care about how many this would return
   552	    public int queryCount(String indexName, final JSONObject query)
   553	    throws IOException {
   554	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   555	        Request searchRequest = new Request("POST", indexName + "/_count");
   556	        JSONObject cleanedQuery = new JSONObject(query.toString());
   557	        cleanedQuery.remove("_source"); // invalid for a _count query
   558	        searchRequest.setJsonEntity(cleanedQuery.toString());
   559	        JSONObject res = new JSONObject();
   560	        try {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '620,720p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,240p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class ImportTask|private.*encounter|List<Encounter>|encounters|getEncounters|addEncounter|IMPORTTASK_ENCOUNTERS|@Persistent|dependent|Task\\(|getParameters|addParameter|parent\" src/main/java/org/ecocean/servlet/importer/ImportTask.java src/main/java/org/ecocean/ia/Task.java src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/ia/package.jdo src/main/java/org/ecocean/api/BulkImport.java src/main/java/org/ecocean/api/bulk/BulkImporter.java src/main/java/org/ecocean/servlet/IAGateway.java" in /mnt/c/Wildbook-clean2
 succeeded in 276ms:
     1	/*
     2	    an instance of an ia.Task can be persisted and represents the state of that task
     3	    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
     4	 */
     5	package org.ecocean.ia;
     6	
     7	import java.util.ArrayList;
     8	import java.util.Collection;
     9	import java.util.Collections;
    10	import java.util.HashMap;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	import javax.jdo.Query;
    15	import org.apache.commons.lang3.builder.ToStringBuilder;
    16	import org.ecocean.Annotation;
    17	import org.ecocean.Encounter;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.shepherd.core.Shepherd;
    20	import org.ecocean.User;
    21	import org.ecocean.Util;
    22	import org.joda.time.DateTime;
    23	import org.json.JSONArray;
    24	import org.json.JSONObject;
    25	
    26	import org.ecocean.identity.IdentityServiceLog;
    27	
    28	public class Task implements java.io.Serializable {
    29	    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    30	    private String id = null;
    31	    private long created = -1;
    32	    private long modified = -1;
    33	    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    34	    private List<MediaAsset> objectMediaAssets = null;
    35	    private List<Annotation> objectAnnotations = null;
    36	    private Task parent = null;
    37	    private List<Task> children = null;
    38	    private String parameters = null;
    39	    private String status;
    40	    // general use, but notably will contain error details when status=error
    41	    private String statusDetails = null;
    42	    private Long completionDateInMilliseconds;
    43	    private String queueResumeMessage;
    44	
    45	    public Task() {
    46	        this(Util.generateUUID());
    47	    }
    48	    public Task(String id) {
    49	        this.id = id;
    50	        created = System.currentTimeMillis();
    51	        modified = System.currentTimeMillis();
    52	    }
    53	    // makes a child of the passed Task (and inherits the parameters!!)
    54	    public Task(Task p) {
    55	        this();
    56	        this.setParameters(p.getParameters());
    57	        this.setParent(p);
    58	    }
    59	
    60	    public String getId() {
    61	        return id;
    62	    }
    63	
    64	    public long getCreatedLong() {
    65	        return created;
    66	    }
    67	
    68	    public long getModifiedLong() {
    69	        return modified;
    70	    }
    71	
    72	    public long timeInactive() {
    73	        long now = System.currentTimeMillis();
    74	
    75	        if (modified > 0) return (now - modified);
    76	        if (created > 0) return (now - created);
    77	        // weird or inconclusive:
    78	        return -1l;
    79	    }
    80	
    81	    public boolean timedOutDueToInactivity() {
    82	        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    83	    }
    84	
    85	    public boolean statusInEndState() {
    86	        if ("completed".equals(status)) return true;
    87	        if ("error".equals(status)) return true;
    88	        // ml-service migration v2: "dropped-stale" is terminal — the task's
    89	        // target was deleted before the queued job ran. Neither success nor
    90	        // error; the inactivity-timeout watchdog must not flip it to error.
    91	        if ("dropped-stale".equals(status)) return true;
    92	        return false;
    93	    }
    94	
    95	    public void setModified() {
    96	        modified = System.currentTimeMillis();
    97	    }
    98	
    99	    public boolean canUserAccess(User user, Shepherd myShepherd) {
   100	        if (user == null) return false;
   101	        if (user.isAdmin(myShepherd)) return true;
   102	        Encounter enc = null;
   103	        // if we have annotations, use first to determine encounter
   104	        if (this.countObjectAnnotations() > 0) {
   105	            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
   106	        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
   107	            MediaAsset ma = this.getObjectMediaAssets().get(0);
   108	            // we iterate over all annots on this asset til we find an encounter.
   109	            // it might be better to find *all* encounters and return access based on each;
   110	            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
   111	            // so i think this means asset-based access of tasks will be rare or unused anyway
   112	            for (Annotation ann : ma.getAnnotations()) {
   113	                if (ann != null) enc = ann.findEncounter(myShepherd);
   114	                if (enc != null) break;
   115	            }
   116	        }
   117	        if (enc == null) return false;
   118	        if (enc.isPubliclyReadable()) return true;
   119	        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
   120	        return enc.canUserAccess(user, myShepherd.getContext());
   121	    }
   122	
   123	/*
   124	    // not really convinced these are accurate enough to use
   125	    //   actual computation of these things is complicated
   126	    //   leaving these for future potential exploration, if needed.
   127	
   128	    public boolean isTypeDetection() {
   129	        if (this.hasObjectMediaAssets()) return true;
   130	        if (this.hasObjectAnnotations()) return false;
   131	        if (this.parameters == null) return false;
   132	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
   133	        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
   134	        return false;
   135	    }
   136	    public boolean isTypeIdentification() {
   137	        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
   138	        if (this.parameters == null) return false;
   139	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
   140	        return false;
   141	    }
   142	
   143	    public boolean initiatedWithDetection() {
   144	        if (this.parameters == null) return false;
   145	        return this.getParameters().optBoolean("ibeis.detection", false);
   146	    }
   147	    public boolean initiatedWithIdentification() {
   148	        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
   149	    }
   150	 */
   151	    public int countObjectMediaAssets() {
   152	        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
   153	    }
   154	
   155	    public int countObjectAnnotations() {
   156	        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
   157	    }
   158	
   159	    public int countObjects() {
   160	        return countObjectMediaAssets() + countObjectAnnotations();
   161	    }
   162	
   163	    // not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
   164	    public boolean hasObjectMediaAssets() {
   165	        return (countObjectMediaAssets() > 0);
   166	    }
   167	
   168	    public boolean hasObjectAnnotations() {
   169	        return (countObjectAnnotations() > 0);
   170	    }
   171	
   172	    public boolean hasObjects() {
   173	        return (countObjects() > 0);
   174	    }
   175	
   176	    public void setObjectMediaAssets(List<MediaAsset> mas) {
   177	        objectMediaAssets = mas;
   178	    }
   179	
   180	    public void setObjectAnnotations(List<Annotation> anns) {
   181	        objectAnnotations = anns;
   182	    }
   183	
   184	    public List<MediaAsset> getObjectMediaAssets() {
   185	        return objectMediaAssets;
   186	    }
   187	
   188	    public List<Annotation> getObjectAnnotations() {
   189	        return objectAnnotations;
   190	    }
   191	
   192	    public int numberMediaAssets() {
   193	        return Util.collectionSize(objectMediaAssets);
   194	    }
   195	
   196	    public int numberAnnotations() {
   197	        return Util.collectionSize(objectAnnotations);
   198	    }
   199	
   200	    // kinda for convenience?
   201	    public boolean addObject(MediaAsset ma) {
   202	        if (ma == null) return false;
   203	        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
   204	        if (!objectMediaAssets.contains(ma)) {
   205	            objectMediaAssets.add(ma);
   206	            return true;
   207	        }
   208	        return false;
   209	    }
   210	
   211	    public boolean addObject(Annotation ann) {
   212	        if (ann == null) return false;
   213	        if (objectAnnotations == null) objectAnnotations = new ArrayList<Annotation>();
   214	        if (!objectAnnotations.contains(ann)) {
   215	            objectAnnotations.add(ann);
   216	            return true;
   217	        }
   218	        return false;
   219	    }
   220	
   221	    public boolean removeObject(Annotation ann) {
   222	        if (ann != null && objectAnnotations != null && objectAnnotations.contains(ann)) {
   223	            objectAnnotations.remove(ann);
   224	            return true;
   225	        }
   226	        return false;
   227	    }
   228	
   229	    public boolean removeObject(MediaAsset ma) {
   230	        if (ma != null && objectMediaAssets != null && objectMediaAssets.contains(ma)) {
   231	            objectMediaAssets.remove(ma);
   232	            return true;
   233	        }
   234	        return false;
   235	    }
   236	
   237	    public boolean contains(Annotation ann) {
   238	        if (objectAnnotations == null) return false;
   239	        return objectAnnotations.contains(ann);
   240	    }

 succeeded in 282ms:
   620	            }
   621	            myShepherd.getPM().deletePersistent(itask);
   622	            // myShepherd.commitDBTransaction();
   623	        } catch (Exception ex) {
   624	            throw new IOException("general exception on ImportTask delete: " + ex);
   625	        }
   626	        Util.mark("ImportTask.deleteWithRelated(" + id + ") completed");
   627	    }
   628	
   629	    // this is hobbled together from some complex code in import.jsp
   630	    // some of this is only necessary to handle legacy (non-api) uploads
   631	    // may the gods have mercy on our soul
   632	    // FIXME this can be OUTRAGEOUSLY slow for tasks with 100s of annotations
   633	    // for the GET api for listing tasks we very likely want to move this
   634	    // to detailed=true so it is not called for every task -- but this currently
   635	    // messes up the status :(
   636	    public JSONObject iaSummaryJson(Shepherd myShepherd) {
   637	        int numDetectionComplete = 0;
   638	        int numAcmId = 0;
   639	        int numAllowedIA = 0;
   640	        int numAssets = 0;
   641	        int numAnnotations = 0;
   642	        boolean pipelineStarted = false;
   643	        Map<String, Integer> statsMA = this.statsMediaAssets();
   644	        JSONObject statsAnn = this.statsAnnotations(myShepherd);
   645	
   646	        if (this.getMediaAssets() != null)
   647	            numAssets = this.getMediaAssets().size();
   648	        for (MediaAsset ma : this.getMediaAssets()) {
   649	            numAnnotations += ma.numAnnotations();
   650	            if (ma.getAcmId() != null) numAcmId++;
   651	            // check if we can get validity off the image before the expensive check of hitting the AssetStore
   652	            if (ma.isValidImageForIA() != null) {
   653	                if (ma.isValidImageForIA().booleanValue()) numAllowedIA++;
   654	            } else if (ma.validateSourceImage()) {
   655	                numAllowedIA++;
   656	            }
   657	/*
   658	                if ((ma.isValidImageForIA() == null) || !ma.isValidImageForIA().booleanValue()) {
   659	                    invalidMediaAssets.add(asset);
   660	                }
   661	 */
   662	            // ml-service migration v2 (commit #5): "complete-mlservice" is
   663	            // terminal alongside "complete" and "pending" for the bulk-import
   664	            // detection-complete tally. Without this, an ml-service-routed
   665	            // bulk import never reports 100% detection complete in the UI.
   666	            if ((ma.getDetectionStatus() != null) &&
   667	                (ma.getDetectionStatus().equals("complete") ||
   668	                ma.getDetectionStatus().equals("complete-mlservice") ||
   669	                ma.getDetectionStatus().equals("pending"))) numDetectionComplete++;
   670	        }
   671	        JSONObject pj = new JSONObject();
   672	        pj.put("statsMediaAssets", statsMA);
   673	        pj.put("statsAnnotations", statsAnn);
   674	        pj.put("numberMediaAssets", numAssets);
   675	        pj.put("numberAnnotations", numAnnotations);
   676	        pj.put("numberMediaAssetACMIds", numAcmId);
   677	        pj.put("numberMediaAssetValidIA", numAllowedIA);
   678	        pj.put("detectionNumberComplete", numDetectionComplete);
   679	        // non-legacy flavor
   680	        if ((this.getIATask() != null) && this.iaTaskStarted()) {
   681	            pipelineStarted = true;
   682	            if (numDetectionComplete == numAllowedIA) {
   683	                pj.put("detectionPercent", 1.0);
   684	                pj.put("detectionStatus", "complete");
   685	            } else {
   686	                if (numAssets > 0)
   687	                    pj.put("detectionPercent",
   688	                        new Double(numDetectionComplete) / new Double(numAssets));
   689	                pj.put("detectionStatus", "sent");
   690	            }
   691	            if (this.iaTaskRequestedIdentification()) {
   692	                int numIdentificationComplete = 0;
   693	                int numIdentificationTotal = 0;
   694	                // getOverallStatus() in imports.jsp is a nightmare. attempt to replicate here.
   695	                if (statsAnn.optInt("numLatestTasks", -1) >= 0)
   696	                    numIdentificationTotal = statsAnn.optInt("numLatestTasks");
   697	                // who is the genius who made this be 'completed' versus the (seemingly universal?) 'complete'
   698	                // (it may well have been me)
   699	                if (statsAnn.optInt("numLatestTask_completed", -1) >= 0)
   700	                    numIdentificationComplete = statsAnn.optInt("numLatestTask_completed");
   701	                // TODO do we have to deal with errors as "completed" somehow?
   702	                pj.put("identificationNumberComplete", numIdentificationComplete);
   703	                pj.put("identificationNumTotal", numIdentificationTotal);
   704	                if (numIdentificationTotal == 0) {
   705	                    pj.put("identificationStatus", "identification not started");
   706	                    pj.put("identificationPercent", 0.0);
   707	                } else if (numIdentificationComplete >= numIdentificationTotal) {
   708	                    pj.put("identificationStatus", "complete");
   709	                    pj.put("identificationPercent", 1.0);
   710	                } else {
   711	                    pj.put("identificationStatus", "sent");
   712	                    pj.put("identificationPercent",
   713	                        new Double(numIdentificationComplete) / new Double(numIdentificationTotal));
   714	                }
   715	            }
   716	            // legacy flavor
   717	        } else if ((this.getIATask() == null) && (numDetectionComplete > 0)) {
   718	            pipelineStarted = true;
   719	            if (numDetectionComplete == numAssets) {
   720	                pj.put("detectionPercent", 1.0);

 succeeded in 287ms:
     1	package org.ecocean.servlet.importer;
     2	
     3	import java.io.IOException;
     4	import java.util.ArrayList;
     5	import java.util.Collections;
     6	import java.util.Comparator;
     7	import java.util.HashMap;
     8	import java.util.HashSet;
     9	import java.util.List;
    10	import java.util.Map;
    11	import java.util.Set;
    12	import javax.jdo.Query;
    13	import javax.servlet.http.HttpServletRequest;
    14	import org.apache.commons.lang3.builder.ToStringBuilder;
    15	import org.ecocean.Annotation;
    16	import org.ecocean.Encounter;
    17	import org.ecocean.ia.Task;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.MarkedIndividual;
    20	import org.ecocean.Occurrence;
    21	import org.ecocean.Project;
    22	import org.ecocean.scheduled.ScheduledIndividualMerge;
    23	import org.ecocean.security.Collaboration;
    24	import org.ecocean.shepherd.core.Shepherd;
    25	import org.ecocean.social.SocialUnit;
    26	import org.ecocean.User;
    27	import org.ecocean.Util;
    28	import org.joda.time.DateTime;
    29	import org.json.JSONArray;
    30	import org.json.JSONObject;
    31	
    32	public class ImportTask implements java.io.Serializable {
    33	    private String id;
    34	    private User creator;
    35	    private DateTime created;
    36	    private List<Encounter> encounters;
    37	    private String parameters;
    38	    private List<String> log;
    39	    private String errors;
    40	    private String status;
    41	    private Task iaTask;
    42	    // processingProgress is really used for IMPORT progress only (0.0 thru 1.0)
    43	    private Double processingProgress;
    44	
    45	    public ImportTask() {
    46	        this((User)null);
    47	    }
    48	
    49	    public ImportTask(User u) {
    50	        this(u, Util.generateUUID());
    51	    }
    52	
    53	    public ImportTask(User u, String id) {
    54	        this.creator = u;
    55	        this.updateCreated();
    56	        this.id = id;
    57	    }
    58	
    59	    public String getId() {
    60	        return id;
    61	    }
    62	
    63	    public void updateCreated() {
    64	        created = new DateTime();
    65	    }
    66	
    67	    public DateTime getCreated() {
    68	        return created;
    69	    }
    70	
    71	    public int numberEncounters() {
    72	        return Util.collectionSize(encounters);
    73	    }
    74	
    75	    public Double getProcessingProgress() {
    76	        return processingProgress;
    77	    }
    78	
    79	    public void setProcessingProgress(Double prog) {
    80	        processingProgress = prog;
    81	    }
    82	
    83	    public List<Encounter> getEncounters() {
    84	        return encounters;
    85	    }
    86	
    87	    public List<Encounter> getEncountersOrderByCreated() {
    88	        if (encounters == null) return null;
    89	        List<Encounter> sorted = new ArrayList<Encounter>(encounters);
    90	        Collections.sort(sorted, new Comparator<Encounter>() {
    91	            @Override public int compare(Encounter encA, Encounter encB) {
    92	                Long longA = encA.getDWCDateAddedLong();
    93	                Long longB = encB.getDWCDateAddedLong();
    94	                if ((longA == null) || (longB == null)) return 0;
    95	                return Long.compare(longA, longB);
    96	            }
    97	        });
    98	        return sorted;
    99	    }
   100	
   101	    public void setEncounters(List<Encounter> encs) {
   102	        encounters = encs;
   103	    }
   104	
   105	    public void addEncounter(Encounter enc) {
   106	        if (enc == null) return;
   107	        if (encounters == null) encounters = new ArrayList<Encounter>();
   108	        if (!encounters.contains(enc)) encounters.add(enc);
   109	    }
   110	
   111	    public Set<Annotation> getAnnotations() {
   112	        Set<Annotation> anns = new HashSet<Annotation>();
   113	
   114	        if (encounters != null)
   115	            for (Encounter enc : encounters) {
   116	                if (enc.getAnnotations() != null)
   117	                    for (Annotation ann : enc.getAnnotations()) {
   118	                        anns.add(ann);
   119	                    }
   120	            }
   121	        return anns;
   122	    }
   123	
   124	    public Map<Annotation, List<Task> > getAnnotationTaskMap(Shepherd myShepherd) {
   125	        Map<Annotation, List<Task> > atm = new HashMap<Annotation, List<Task> >();
   126	
   127	        for (Annotation ann : this.getAnnotations()) {
   128	            atm.put(ann, Task.getTasksFor(ann, myShepherd, "created DESC"));
   129	        }
   130	        return atm;
   131	    }
   132	
   133	    public void setCreator(User u) {
   134	        creator = u;
   135	    }
   136	
   137	    public User getCreator() {
   138	        return creator;
   139	    }
   140	
   141	    // classically the excel filename the user uploaded, generalized for api usage
   142	    public String getSourceName() {
   143	        if (getParameters() == null) return null;
   144	        JSONObject passed = getParameters().optJSONObject("_passedParameters");
   145	        if (passed == null) return null;
   146	        String name = passed.optString("sourceName", null);
   147	        if (name != null) return name;
   148	        // for some reason (!???) these are arrays with 1 element
   149	        JSONArray nameArr = passed.optJSONArray("originalFilename");
   150	        if (nameArr == null) nameArr = passed.optJSONArray("filename");
   151	        if ((nameArr != null) && (nameArr.length() > 0)) return nameArr.optString(0, null);
   152	        return null;
   153	    }
   154	
   155	    public JSONArray getMatchingLocations() {
   156	        if (getParameters() == null) return null;
   157	        JSONObject passed = getParameters().optJSONObject("_passedParameters");
   158	        if (passed == null) return null;
   159	        return passed.optJSONArray("matchingLocations");
   160	    }
   161	
   162	    // this means was NOT sent via api
   163	    // NOTE this logic may end up being flaky; adjust accordingly
   164	    public boolean isLegacy() {
   165	        if (getParameters() == null) return true; // ????
   166	        JSONObject passed = getParameters().optJSONObject("_passedParameters");
   167	        if (passed == null) return true; // ?????
   168	        if (passed.optString("bulkImportId", null) != null) return false;
   169	        return true;
   170	    }
   171	
   172	    public List<MarkedIndividual> getMarkedIndividuals() {
   173	        if (encounters == null) return null;
   174	        List<MarkedIndividual> all = new ArrayList<MarkedIndividual>();
   175	        for (Encounter enc : encounters) {
   176	            MarkedIndividual indiv = enc.getIndividual();
   177	            if ((indiv != null) && !all.contains(indiv)) all.add(indiv);
   178	        }
   179	        return all;
   180	    }

 succeeded in 426ms:
src/main/resources/org/ecocean/package.jdo:56:        <collection element-type="org.ecocean.Observation" dependent-element="true" />
src/main/resources/org/ecocean/package.jdo:114:      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true" mapped-by="individual" >
src/main/resources/org/ecocean/package.jdo:152:        <field name="encounters"/>
src/main/resources/org/ecocean/package.jdo:170:      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true">
src/main/resources/org/ecocean/package.jdo:181:        <collection element-type="org.ecocean.Taxonomy" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:192:		<collection element-type="org.ecocean.User" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:197:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:220:        <collection element-type="org.ecocean.Observation" dependent-element="true" />
src/main/resources/org/ecocean/package.jdo:225:          <collection element-type="org.ecocean.media.MediaAsset" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:232:        <field name="encounters" />
src/main/resources/org/ecocean/package.jdo:243:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:248:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:253:        <collection element-type="org.ecocean.User" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:413:        <collection element-type="org.ecocean.genetics.TissueSample" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:418:        <collection element-type="org.ecocean.SinglePhotoVideo" dependent-element="true"/>
src/main/resources/org/ecocean/package.jdo:423:        <collection element-type="org.ecocean.Annotation" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:428:        <collection element-type="org.ecocean.Measurement" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:433:        <collection element-type="org.ecocean.tag.MetalTag" dependent-element="false"/>
src/main/resources/org/ecocean/package.jdo:438:        <collection element-type="org.ecocean.Observation" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:594:                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
src/main/resources/org/ecocean/package.jdo:618:		<collection element-type="org.ecocean.Embedding" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:720:      <field name="parentObjectClass">
src/main/resources/org/ecocean/package.jdo:724:      <field name="parentObjectID">
src/main/resources/org/ecocean/package.jdo:746:        <collection element-type="org.ecocean.User" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:750:      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true">
src/main/resources/org/ecocean/package.jdo:751:        <collection element-type="org.ecocean.Encounter" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:879:                	<collection element-type="org.ecocean.Organization" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:990:		<field name="children" mapped-by="parent">
src/main/resources/org/ecocean/package.jdo:993:		<field name="parent">
src/main/resources/org/ecocean/package.jdo:1026:		<!-- this points up to the parent -->
src/main/resources/org/ecocean/package.jdo:1027:      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.Organization" recursion-depth="3"/>
src/main/resources/org/ecocean/package.jdo:1029:		<!-- this uses parent to find child orgs -->
src/main/resources/org/ecocean/package.jdo:1030:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/package.jdo:1031:                	<collection element-type="org.ecocean.Organization" dependent-element="false" />
src/main/resources/org/ecocean/package.jdo:1056:		<field name="annotation" dependent-element="false" >
src/main/java/org/ecocean/api/BulkImport.java:67:                        if (isAdmin || ServletUtilities.isUserAuthorizedForImportTask(task, request, myShepherd))
src/main/java/org/ecocean/api/BulkImport.java:91:                    ImportTask task = myShepherd.getImportTask(bulkImportId);
src/main/java/org/ecocean/api/BulkImport.java:94:                    } else if (!isAdmin && !ServletUtilities.isUserAuthorizedForImportTask(task, request, myShepherd)) {
src/main/java/org/ecocean/api/BulkImport.java:430:                                initializeImportTask(bulkImportId, bgUser, payload,
src/main/java/org/ecocean/api/BulkImport.java:477:                                    rtn.remove("encounters");
src/main/java/org/ecocean/api/BulkImport.java:483:                                    // we must use our shepherd, as encounters are associated with it
src/main/java/org/ecocean/api/BulkImport.java:484:                                    ImportTask itask = bgShepherd.getImportTask(bulkImportId);
src/main/java/org/ecocean/api/BulkImport.java:492:                                        itask.setEncounters(importer.getEncounters());
src/main/java/org/ecocean/api/BulkImport.java:500:                                                importer.getEncounters());
src/main/java/org/ecocean/api/BulkImport.java:502:                                        bgShepherd.storeNewImportTask(itask);
src/main/java/org/ecocean/api/BulkImport.java:508:                                // taskShepherd.storeNewImportTask(bgTask);
src/main/java/org/ecocean/api/BulkImport.java:540:                    initializeImportTask(bulkImportId, currentUser, payload,
src/main/java/org/ecocean/api/BulkImport.java:575:                            rtn.remove("encounters");
src/main/java/org/ecocean/api/BulkImport.java:579:                        // we must use our shepherd, as encounters are associated with it
src/main/java/org/ecocean/api/BulkImport.java:580:                        ImportTask itask = myShepherd.getImportTask(bulkImportId);
src/main/java/org/ecocean/api/BulkImport.java:588:                            itask.setEncounters(importer.getEncounters());
src/main/java/org/ecocean/api/BulkImport.java:595:                                encAssets = generateEncAssets(importer.getEncounters());
src/main/java/org/ecocean/api/BulkImport.java:597:                            myShepherd.storeNewImportTask(itask);
src/main/java/org/ecocean/api/BulkImport.java:721:    private void initializeImportTask(String id, User passedUser, JSONObject payload,
src/main/java/org/ecocean/api/BulkImport.java:724:            System.out.println("[WARNING] initializeImportTask(" + id + ") got null user: " +
src/main/java/org/ecocean/api/BulkImport.java:734:            ImportTask itask = taskShepherd.getImportTask(id);
src/main/java/org/ecocean/api/BulkImport.java:742:                itask = new ImportTask(user, id);
src/main/java/org/ecocean/api/BulkImport.java:744:            itask.setIATask(null);
src/main/java/org/ecocean/api/BulkImport.java:755:            taskShepherd.storeNewImportTask(itask);
src/main/java/org/ecocean/api/BulkImport.java:763:        Util.mark("initializeImportTask(" + id + ", " + status + ")");
src/main/java/org/ecocean/api/BulkImport.java:773:            ImportTask itask = taskShepherd.getImportTask(id);
src/main/java/org/ecocean/api/BulkImport.java:784:            taskShepherd.storeNewImportTask(itask);
src/main/java/org/ecocean/api/BulkImport.java:806:            ImportTask itask = myShepherd.getImportTask(importId);
src/main/java/org/ecocean/api/BulkImport.java:834:    private JSONObject generateEncAssets(List<Encounter> encs) {
src/main/java/org/ecocean/api/BulkImport.java:899:            for (Encounter enc : task.getEncountersOrderByCreated()) {
src/main/java/org/ecocean/api/BulkImport.java:919:            jt.put("encounters", encArr);
src/main/java/org/ecocean/ia/Task.java:36:    private Task parent = null;
src/main/java/org/ecocean/ia/Task.java:45:    public Task() {
src/main/java/org/ecocean/ia/Task.java:48:    public Task(String id) {
src/main/java/org/ecocean/ia/Task.java:54:    public Task(Task p) {
src/main/java/org/ecocean/ia/Task.java:56:        this.setParameters(p.getParameters());
src/main/java/org/ecocean/ia/Task.java:109:            // it might be better to find *all* encounters and return access based on each;
src/main/java/org/ecocean/ia/Task.java:132:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
src/main/java/org/ecocean/ia/Task.java:133:        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
src/main/java/org/ecocean/ia/Task.java:139:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
src/main/java/org/ecocean/ia/Task.java:145:        return this.getParameters().optBoolean("ibeis.detection", false);
src/main/java/org/ecocean/ia/Task.java:148:        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
src/main/java/org/ecocean/ia/Task.java:271:        parent = t;
src/main/java/org/ecocean/ia/Task.java:276:        return parent;
src/main/java/org/ecocean/ia/Task.java:280:        if (parent == null) return null;
src/main/java/org/ecocean/ia/Task.java:281:        return parent.getId();
src/main/java/org/ecocean/ia/Task.java:306:    public Task getRootTask() {
src/main/java/org/ecocean/ia/Task.java:307:        if (parent == null) return this;
src/main/java/org/ecocean/ia/Task.java:308:        return parent.getRootTask();
src/main/java/org/ecocean/ia/Task.java:419:    public JSONObject getParameters() { // only return as JSONObject!
src/main/java/org/ecocean/ia/Task.java:445:    public void addParameter(String key, Object value) {
src/main/java/org/ecocean/ia/Task.java:447:        JSONObject j = this.getParameters();
src/main/java/org/ecocean/ia/Task.java:461:        j.put("parameters", this.getParameters());
src/main/java/org/ecocean/ia/Task.java:511:                   .append("params=" + ((this.getParameters() ==
src/main/java/org/ecocean/ia/Task.java:512:                null) ? "(none)" : this.getParameters().toString()))
src/main/java/org/ecocean/ia/Task.java:576:            Task r = t.getRootTask();
src/main/java/org/ecocean/ia/Task.java:693:        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
src/main/java/org/ecocean/ia/Task.java:726:        if (getParameters() == null) return null;
src/main/java/org/ecocean/ia/Task.java:727:        return getParameters().optJSONObject("matchingSetFilter");
src/main/java/org/ecocean/ia/Task.java:731:        if (getParameters() == null) return null;
src/main/java/org/ecocean/ia/Task.java:732:        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
src/main/java/org/ecocean/ia/Task.java:735:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
src/main/java/org/ecocean/ia/Task.java:737:            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
src/main/java/org/ecocean/ia/Task.java:749:        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
src/main/java/org/ecocean/ia/Task.java:752:            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
src/main/java/org/ecocean/ia/Task.java:757:            getParameters().getJSONObject("ibeis.identification").optString("description",
src/main/java/org/ecocean/ia/Task.java:810:        rtn.put("parentTaskId", getParentId());
src/main/java/org/ecocean/servlet/IAGateway.java:116:                if (task == null) task = new Task(taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:128:                myShepherd.storeNewTask(task);
src/main/java/org/ecocean/servlet/IAGateway.java:227:                task.addParameter("ibeis.detection", true);
src/main/java/org/ecocean/servlet/IAGateway.java:333:        Task parentTask = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/servlet/IAGateway.java:334:        if (parentTask == null) {
src/main/java/org/ecocean/servlet/IAGateway.java:337:            parentTask = new Task(taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:344:            JSONObject params = parentTask.getParameters();
src/main/java/org/ecocean/servlet/IAGateway.java:345:            parentTask.setParameters((String)null); // reset this, kids inherit params
src/main/java/org/ecocean/servlet/IAGateway.java:347:                Task newTask = new Task(parentTask);
src/main/java/org/ecocean/servlet/IAGateway.java:350:                myShepherd.storeNewTask(newTask);
src/main/java/org/ecocean/servlet/IAGateway.java:354:            myShepherd.storeNewTask(parentTask);
src/main/java/org/ecocean/servlet/IAGateway.java:356:        } else { // we just use the existing "parent" task
src/main/java/org/ecocean/servlet/IAGateway.java:357:            subTasks.add(parentTask);
src/main/java/org/ecocean/servlet/IAGateway.java:365:                taskRes = _sendIdentificationTask(ann, context, baseUrl, queryConfigDict, null,
src/main/java/org/ecocean/servlet/IAGateway.java:371:                System.out.println(">>>>>>> parentTask: " + parentTask);
src/main/java/org/ecocean/servlet/IAGateway.java:398:    private static JSONObject _sendIdentificationTask(Annotation ann, String context,
src/main/java/org/ecocean/servlet/IAGateway.java:472:                System.out.println("_sendIdentificationTask() unable to initiate identification: " +
src/main/java/org/ecocean/servlet/IAGateway.java:882:        Task parentTask = null;
src/main/java/org/ecocean/servlet/IAGateway.java:884:        if (importTaskId != null) itask = myShepherd.getImportTask(importTaskId);
src/main/java/org/ecocean/servlet/IAGateway.java:886:            parentTask = new Task(); // root task to hold all others, to connect to ImportTask
src/main/java/org/ecocean/servlet/IAGateway.java:887:            parentTask.setParameters(taskParameters);
src/main/java/org/ecocean/servlet/IAGateway.java:888:            myShepherd.storeNewTask(parentTask);
src/main/java/org/ecocean/servlet/IAGateway.java:889:            itask.setIATask(parentTask);
src/main/java/org/ecocean/servlet/IAGateway.java:890:            resTaskId = parentTask.getId();
src/main/java/org/ecocean/servlet/IAGateway.java:892:            System.out.println("IAGateway.handleBulkImport() created parentTask " + parentTask +
src/main/java/org/ecocean/servlet/IAGateway.java:900:        Task task = new Task();
src/main/java/org/ecocean/servlet/IAGateway.java:902:        myShepherd.storeNewTask(task);
src/main/java/org/ecocean/servlet/IAGateway.java:903:        if (parentTask != null) parentTask.addChild(task);
src/main/resources/org/ecocean/ia/package.jdo:16:		<!-- this points up to the parent -->
src/main/resources/org/ecocean/ia/package.jdo:17:      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" recursion-depth="3"/>
src/main/resources/org/ecocean/ia/package.jdo:19:		<!-- this uses parent to find child tasks -->
src/main/resources/org/ecocean/ia/package.jdo:20:            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
src/main/resources/org/ecocean/ia/package.jdo:21:                	<collection element-type="org.ecocean.ia.Task" dependent-element="true" />
src/main/resources/org/ecocean/ia/package.jdo:54:		<field name="parentId" persistence-modifier="persistent">
src/main/resources/org/ecocean/ia/package.jdo:89:			<collection element-type="org.ecocean.ia.MatchResultProspect" dependent-element="true" />
src/main/resources/org/ecocean/ia/package.jdo:107:		<field name="matchResult" dependent-element="false" >
src/main/java/org/ecocean/api/bulk/BulkImporter.java:49:    private Map<String, Encounter> encounterCache = new HashMap<String, Encounter>();
src/main/java/org/ecocean/api/bulk/BulkImporter.java:139:        rtn.put("encounters", arr);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:172:        // clears shepherd/pmf cache, which we seem to do when we create encounters (?)
src/main/java/org/ecocean/api/bulk/BulkImporter.java:201:            indiv.addEncounter(enc);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:205:        occ.addEncounterAndUpdateIt(enc);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:246:        // is, sadly, what public encounters seem to be assigned
src/main/java/org/ecocean/api/bulk/BulkImporter.java:333:            if (proj != null) proj.addEncounter(enc);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:717:        // we want our own shepherd here so we can persist this task independent of our main shepherd
src/main/java/org/ecocean/api/bulk/BulkImporter.java:722:            ImportTask itask = taskShepherd.getImportTask(this.importTaskId);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:726:            taskShepherd.storeNewImportTask(itask);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:735:    public List<Encounter> getEncounters() {
src/main/java/org/ecocean/api/bulk/BulkImporter.java:736:        return new ArrayList<Encounter>(encounterCache.values());
src/main/java/org/ecocean/api/bulk/BulkImporter.java:783:        // TODO but maybe these samples will be on encounters *created in this import*, in which case,
src/main/java/org/ecocean/api/bulk/BulkImporter.java:928:            // apparently this is a thing?
src/main/java/org/ecocean/servlet/importer/ImportTask.java:32:public class ImportTask implements java.io.Serializable {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:36:    private List<Encounter> encounters;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:45:    public ImportTask() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:49:    public ImportTask(User u) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:53:    public ImportTask(User u, String id) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:72:        return Util.collectionSize(encounters);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:83:    public List<Encounter> getEncounters() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:84:        return encounters;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:87:    public List<Encounter> getEncountersOrderByCreated() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:88:        if (encounters == null) return null;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:89:        List<Encounter> sorted = new ArrayList<Encounter>(encounters);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:101:    public void setEncounters(List<Encounter> encs) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:102:        encounters = encs;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:105:    public void addEncounter(Encounter enc) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:107:        if (encounters == null) encounters = new ArrayList<Encounter>();
src/main/java/org/ecocean/servlet/importer/ImportTask.java:108:        if (!encounters.contains(enc)) encounters.add(enc);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:114:        if (encounters != null)
src/main/java/org/ecocean/servlet/importer/ImportTask.java:115:            for (Encounter enc : encounters) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:143:        if (getParameters() == null) return null;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:144:        JSONObject passed = getParameters().optJSONObject("_passedParameters");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:156:        if (getParameters() == null) return null;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:157:        JSONObject passed = getParameters().optJSONObject("_passedParameters");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:165:        if (getParameters() == null) return true; // ????
src/main/java/org/ecocean/servlet/importer/ImportTask.java:166:        JSONObject passed = getParameters().optJSONObject("_passedParameters");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:173:        if (encounters == null) return null;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:175:        for (Encounter enc : encounters) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:183:        if (encounters == null) return null;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:185:        for (Encounter enc : encounters) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:196:        if (encounters == null) return null;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:198:        for (Encounter enc : encounters) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:219:    public String getParametersAsString() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:223:    public JSONObject getParameters() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:228:        JSONObject p = getParameters();
src/main/java/org/ecocean/servlet/importer/ImportTask.java:236:        JSONObject p = getParameters();
src/main/java/org/ecocean/servlet/importer/ImportTask.java:244:        if (this.getParameters() == null) return null;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:245:        return this.getParameters().optJSONObject("_passedParameters");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:307:                   .append("numEncs", Util.collectionSize(encounters))
src/main/java/org/ecocean/servlet/importer/ImportTask.java:313:        if (encounters == null) return;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:314:        if (encounters.contains(enc)) encounters.remove(enc);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:322:    public Task getIATask() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:326:    public void setIATask(Task t) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:339:        if (iaTask.getParameters() == null) return true; // has no skipIdent, so i guess?
src/main/java/org/ecocean/servlet/importer/ImportTask.java:340:        return !iaTask.getParameters().optBoolean("skipIdent", false);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:385:        // encounters with photographers/submitters/informOthers populated triggers
src/main/java/org/ecocean/servlet/importer/ImportTask.java:431:                        (atask.getParameters() != null) &&
src/main/java/org/ecocean/servlet/importer/ImportTask.java:432:                        atask.getParameters().has("ibeis.identification")) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:545:        ImportTask itask = myShepherd.getImportTask(id);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:547:        if (!user.isAdmin(myShepherd) && !Collaboration.canUserAccessImportTask(itask,
src/main/java/org/ecocean/servlet/importer/ImportTask.java:552:            List<Encounter> allEncs = new ArrayList<Encounter>(itask.getEncounters());
src/main/java/org/ecocean/servlet/importer/ImportTask.java:568:                    if (occ.getEncounters().size() == 0) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:577:                    if (mark.getEncounters().size() == 0) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:680:        if ((this.getIATask() != null) && this.iaTaskStarted()) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:717:        } else if ((this.getIATask() == null) && (numDetectionComplete > 0)) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:762:            "SELECT \"ID_OID\", count(*) FROM \"IMPORTTASK_ENCOUNTERS\" GROUP BY \"ID_OID\"");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:770:            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" me " +
src/main/java/org/ecocean/servlet/importer/ImportTask.java:779:            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"ENCOUNTER_ANNOTATIONS\" ea " +

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '730,770p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/ia/package.jdo | sed -n '1,45p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/BulkImport.java | sed -n '780,825p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '850,930p'" in /mnt/c/Wildbook-clean2
 succeeded in 277ms:
     1	<!DOCTYPE jdo PUBLIC
     2	    "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN"
     3	    "http://java.sun.com/dtd/jdo_2_0.dtd">
     4	
     5	<jdo>
     6	    <package name="org.ecocean.ia">
     7	
     8		<class name="Task" identity-type="application">
     9			<field name="id" primary-key="true">
    10	        		<column length="36"/>
    11			</field>
    12			<field name="created">
    13				<index name="TASK_CREATED_idx" />
    14			</field>
    15	
    16			<!-- this points up to the parent -->
    17	      		<field name="parent" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" recursion-depth="3"/>
    18	
    19			<!-- this uses parent to find child tasks -->
    20	            	<field name="children" persistence-modifier="persistent" default-fetch-group="false" mapped-by="parent" >
    21	                	<collection element-type="org.ecocean.ia.Task" dependent-element="true" />
    22	                	<join />
    23	            	</field>
    24	
    25	<!--  to update your db table:
    26	alter table "TASK" alter column "PARAMETERS" type text;
    27	-->
    28	      		<field name="parameters" persistence-modifier="persistent">
    29	        		<column jdbc-type="LONGVARCHAR"/>
    30	      		</field>
    31				
    32				<field name="status" persistence-modifier="persistent">
    33	        		<column jdbc-type="LONGVARCHAR"/>
    34	      		</field>
    35	
    36	      		<field name="statusDetails" persistence-modifier="persistent">
    37	        		<column jdbc-type="LONGVARCHAR"/>
    38	      		</field>
    39				
    40				<field name="queueResumeMessage" persistence-modifier="persistent">
    41	        		<column jdbc-type="LONGVARCHAR"/>
    42	      		</field>
    43				
    44				<field name="completionDateInMilliseconds" persistence-modifier="persistent">
    45					<column jdbc-type="BIGINT" allows-null="true"/>

 succeeded in 300ms:
   730	
   731	      <inheritance strategy="new-table" />
   732	
   733	      <field name="id" primary-key="true">
   734	        <column length="36" name="ID"/>
   735	      </field>
   736	
   737	      <field name="ownerId">
   738	        <column name="OWNERID"/>
   739	      </field>
   740	
   741	      <field name="nextIndividualIdIncrement">
   742	        <column jdbc-type="BIGINT" allows-null="true"/>
   743	      </field>
   744	
   745	      <field name="users" persistence-modifier="persistent" default-fetch-group="true">
   746	        <collection element-type="org.ecocean.User" dependent-element="false" />
   747	        <join/>
   748	      </field>
   749	
   750	      <field name="encounters" persistence-modifier="persistent" default-fetch-group="true">
   751	        <collection element-type="org.ecocean.Encounter" dependent-element="false" />
   752	        <join/>
   753	      </field>
   754	
   755	      <field name="researchProjectName">
   756	        <column name="RESEARCHPROJECTNAME"/>
   757	      </field>
   758	
   759	      <field name="projectIdPrefix">
   760	        <column name="PROJECTIDPREFIX"/>
   761	      </field>
   762	
   763	    </class>
   764	
   765	    <class name="Measurement" identity-type="application">
   766	      <inheritance strategy="new-table"/>
   767	      <field name="value">
   768	        <column name="VALUE"/>
   769	      </field>
   770	      <field name="units">

 succeeded in 256ms:
   780	            if (errors != null) {
   781	                itask.setErrors(errors);
   782	                itask.addLog("errors: " + errors);
   783	            }
   784	            taskShepherd.storeNewImportTask(itask);
   785	            taskShepherd.commitDBTransaction();
   786	        } catch (Exception ex) {
   787	            ex.printStackTrace();
   788	            taskShepherd.rollbackDBTransaction();
   789	        } finally {
   790	            taskShepherd.closeDBTransaction();
   791	        }
   792	        Util.mark("importTaskSet(" + id + ": " + status + ", " + progress + "% [etc] )");
   793	    }
   794	
   795	    // based on behavior in sendToIA() from import.jsp
   796	    private void initiateIA(String importId, boolean skipIdent, JSONObject encAssets,
   797	        JSONObject matchingSetFilter) {
   798	        Util.mark("[INFO] > > > > > > BulkImport.initiateIA(" + importId + ") with encAssets=" +
   799	            encAssets);
   800	        if ((importId == null) || (encAssets == null) || (encAssets.length() < 1)) return;
   801	        String context = "context0";
   802	        Shepherd myShepherd = new Shepherd(context);
   803	        myShepherd.setAction("BulkImport.initiateIA()");
   804	        myShepherd.beginDBTransaction();
   805	        try {
   806	            ImportTask itask = myShepherd.getImportTask(importId);
   807	            if (itask == null) throw new IOException("could not load ImportTask " + importId);
   808	            itask.setStatus("processing-detection");
   809	            myShepherd.getPM().makePersistent(itask);
   810	            // build data to send to handleBulkImport()
   811	            JSONObject taskParams = new JSONObject();
   812	            taskParams.put("importTaskId", importId);
   813	            taskParams.put("skipIdent", skipIdent);
   814	            if (!skipIdent) taskParams.put("matchingSetFilter", matchingSetFilter);
   815	            JSONObject data = new JSONObject();
   816	            data.put("taskParameters", taskParams);
   817	            data.put("bulkImport", encAssets);
   818	            Util.mark("[INFO] > > > > > > data => " + data);
   819	            JSONObject res = new JSONObject();
   820	            res.put("success", false);
   821	            JSONObject rtn = IAGateway.handleBulkImport(data, res, myShepherd, context,
   822	                IA.getBaseURL(context));
   823	            Util.mark("[INFO] > > > > > > rtn => " + rtn);
   824	            myShepherd.commitDBTransaction();
   825	        } catch (Exception ex) {

 succeeded in 261ms:
   850	        qjob.put("rootDir", request.getSession().getServletContext().getRealPath("/"));
   851	        qjob.put("requestMethod", request.getMethod());
   852	        qjob.put("requestUri", request.getRequestURI());
   853	        qjob.put("timestamp", System.currentTimeMillis());
   854	        String baseUrl = null;
   855	        try {
   856	            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
   857	        } catch (java.net.URISyntaxException ex) {}
   858	        qjob.put("baseUrl", baseUrl);
   859	        // real IA sends "jobid=jobid-xxxx" as body on POST, but this gives us a url-based alternative (for testing)
   860	        String jobId = request.getParameter("jobid");
   861	        if (raw.startsWith("jobid=") && (raw.length() > 6)) jobId = raw.substring(6);
   862	        qjob.put("jobId", jobId);
   863	
   864	        System.out.println("qjob => " + qjob);
   865	        queue.publish(qjob.toString());
   866	        rtn.put("success", true);
   867	        rtn.put("qid", qid);
   868	        return rtn;
   869	    }
   870	
   871	    public static JSONObject handleBulkImport(JSONObject jin, JSONObject res, Shepherd myShepherd,
   872	        String context, String baseUrl)
   873	    throws ServletException, IOException {
   874	        if (res == null)
   875	            throw new RuntimeException("IAGateway.handleBulkImport() called without res passed in");
   876	        if (baseUrl == null) return res;
   877	        if (jin == null) return res;
   878	        JSONObject taskParameters = jin.optJSONObject("taskParameters");
   879	        String importTaskId = null;
   880	        if (taskParameters != null) importTaskId = taskParameters.optString("importTaskId", null);
   881	        ImportTask itask = null;
   882	        Task parentTask = null;
   883	        String resTaskId = null;
   884	        if (importTaskId != null) itask = myShepherd.getImportTask(importTaskId);
   885	        if (itask != null) {
   886	            parentTask = new Task(); // root task to hold all others, to connect to ImportTask
   887	            parentTask.setParameters(taskParameters);
   888	            myShepherd.storeNewTask(parentTask);
   889	            itask.setIATask(parentTask);
   890	            resTaskId = parentTask.getId();
   891	            itask.addLog("handleBulkImport() initiated IA Task " + resTaskId);
   892	            System.out.println("IAGateway.handleBulkImport() created parentTask " + parentTask +
   893	                " to link to " + itask);
   894	        }
   895	        int okCount = 0;
   896	        JSONArray maIds = new JSONArray();
   897	        for (MediaAsset asset : itask.getMediaAssets()) {
   898	            maIds.put(asset.getId());
   899	        }
   900	        Task task = new Task();
   901	        task.setParameters(taskParameters);
   902	        myShepherd.storeNewTask(task);
   903	        if (parentTask != null) parentTask.addChild(task);
   904	        if (resTaskId == null) resTaskId = task.getId();
   905	        myShepherd.commitDBTransaction();
   906	        // System.out.println("[INFO] IAGateway.handleBulkImport() enc " + encId + " created and queued " + task);
   907	        JSONObject qjob = new JSONObject(jin.toString()); // clone it to start with so we get all same content
   908	        qjob.remove("bulkImport"); // ... but then lose this
   909	        qjob.put("taskId", task.getId());
   910	        qjob.put("mediaAssetIds", maIds);
   911	        qjob.put("v2", true);
   912	        qjob.put("__context", context);
   913	        qjob.put("__baseUrl", baseUrl);
   914	        qjob.put("__handleBulkImport", System.currentTimeMillis());
   915	        task.setQueueResumeMessage(qjob.toString());
   916	        boolean ok = addToDetectionQueue(context, qjob.toString());
   917	        if (ok) okCount++;
   918	        res.put("queuedCount", okCount);
   919	        res.remove("error");
   920	        res.put("success", true);
   921	        res.put("resTaskId", resTaskId);
   922	        return res;
   923	    }
   924	}

exec
/bin/bash -lc "rg -n \"class name=\\\"ImportTask\\\"|IMPORTTASK\" src/main/resources/org/ecocean/package.jdo src/main/resources/org/ecocean/*/package.jdo src/main/java/org/ecocean/servlet/importer/ImportTask.java" in /mnt/c/Wildbook-clean2
 succeeded in 508ms:
src/main/java/org/ecocean/servlet/importer/ImportTask.java:762:            "SELECT \"ID_OID\", count(*) FROM \"IMPORTTASK_ENCOUNTERS\" GROUP BY \"ID_OID\"");
src/main/java/org/ecocean/servlet/importer/ImportTask.java:770:            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" me " +
src/main/java/org/ecocean/servlet/importer/ImportTask.java:779:            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"ENCOUNTER_ANNOTATIONS\" ea " +

exec
/bin/bash -lc "rg --files src/main/resources | rg 'package\\.jdo"'$'"'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "ImportTask" src/main/resources src/main/java/org/ecocean/shepherd/core/Shepherd.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '740,800p'" in /mnt/c/Wildbook-clean2
 succeeded in 264ms:
   740	
   741	    // Batch-count helpers for imports.jsp — run 3 GROUP BY queries instead of 3*N.
   742	
   743	    static Map<String, Integer> parseSqlCountResults(Query query) {
   744	        Map<String, Integer> map = new HashMap<>();
   745	
   746	        try {
   747	            List<?> results = query.executeList();
   748	            for (Object row : results) {
   749	                Object[] cols = (Object[])row;
   750	                map.put((String)cols[0], ((Number)cols[1]).intValue());
   751	            }
   752	        } catch (Exception e) {
   753	            e.printStackTrace();
   754	        } finally {
   755	            if (query != null) query.closeAll();
   756	        }
   757	        return map;
   758	    }
   759	
   760	    public static Map<String, Integer> getAllEncounterCounts(Shepherd myShepherd) {
   761	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
   762	            "SELECT \"ID_OID\", count(*) FROM \"IMPORTTASK_ENCOUNTERS\" GROUP BY \"ID_OID\"");
   763	
   764	        return parseSqlCountResults(query);
   765	    }
   766	
   767	    public static Map<String, Integer> getAllIndividualCounts(Shepherd myShepherd) {
   768	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
   769	            "SELECT ie.\"ID_OID\", count(distinct me.\"INDIVIDUALID_OID\") " +
   770	            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" me " +
   771	            "ON ie.\"CATALOGNUMBER_EID\" = me.\"CATALOGNUMBER_EID\" " + "GROUP BY ie.\"ID_OID\"");
   772	
   773	        return parseSqlCountResults(query);
   774	    }
   775	
   776	    public static Map<String, Integer> getAllMediaAssetCounts(Shepherd myShepherd) {
   777	        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL",
   778	            "SELECT ie.\"ID_OID\", count(distinct mf.\"ID_OID\") " +
   779	            "FROM \"IMPORTTASK_ENCOUNTERS\" ie " + "JOIN \"ENCOUNTER_ANNOTATIONS\" ea " +
   780	            "ON ie.\"CATALOGNUMBER_EID\" = ea.\"CATALOGNUMBER_OID\" " +
   781	            "JOIN \"ANNOTATION_FEATURES\" af ON ea.\"ID_EID\" = af.\"ID_OID\" " +
   782	            "JOIN \"MEDIAASSET_FEATURES\" mf ON af.\"ID_EID\" = mf.\"ID_EID\" " +
   783	            "GROUP BY ie.\"ID_OID\"");
   784	
   785	        return parseSqlCountResults(query);
   786	    }
   787	}

 succeeded in 365ms:
src/main/resources/org/ecocean/package.jdo
src/main/resources/org/ecocean/social/package.jdo
src/main/resources/org/ecocean/media/package.jdo
src/main/resources/org/ecocean/scheduled/package.jdo
src/main/resources/org/ecocean/tag/package.jdo
src/main/resources/org/ecocean/grid/package.jdo
src/main/resources/org/ecocean/cache/package.jdo
src/main/resources/org/ecocean/movement/package.jdo
src/main/resources/org/ecocean/identity/package.jdo
src/main/resources/org/ecocean/ia/package.jdo
src/main/resources/org/ecocean/genetics/package.jdo
src/main/resources/org/ecocean/servlet/importer/package.jdo

 succeeded in 620ms:
src/main/java/org/ecocean/shepherd/core/Shepherd.java:28:import org.ecocean.servlet.importer.ImportTask;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:507:    public ImportTask getImportTask(String num) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:508:        ImportTask tempEnc = null;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:511:            tempEnc = ((ImportTask)(pm.getObjectById(pm.newObjectIdInstance(ImportTask.class,
src/main/java/org/ecocean/shepherd/core/Shepherd.java:519:    public void storeNewImportTask(ImportTask itask) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1500:    public ImportTask getImportTaskForEncounter(Encounter enc) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1502:            "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE encounters.contains(enc) && enc.catalogNumber=='"
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1506:        ImportTask itask = null;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:1509:            itask = (ImportTask)it.next();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4651:    public ImportTask getImportTaskForEncounter(String encounterID) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4653:            "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE encounters.contains(enc) && enc.catalogNumber == \""
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4660:            ImportTask task = (ImportTask)it.next();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4668:    public List<ImportTask> getImportTasksForUser(User user) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4669:        List<ImportTask> all = new ArrayList<ImportTask>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4671:            "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE creator.uuid == \"" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4679:            all.add((ImportTask)it.next());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4685:    public List<ImportTask> getImportTasks() {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4686:        List<ImportTask> all = new ArrayList<ImportTask>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4687:        String filter = "SELECT FROM org.ecocean.servlet.importer.ImportTask";
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4694:            all.add((ImportTask)it.next());
src/main/resources/openapi.yaml:234:        latestBulkImportTask:
src/main/resources/org/ecocean/servlet/importer/package.jdo:7:    <class name="ImportTask" identity-type="application" >

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/servlet/importer/package.jdo | sed -n '1,80p'" in /mnt/c/Wildbook-clean2
 succeeded in 281ms:
     1	<!DOCTYPE jdo PUBLIC "-//Sun Microsystems, Inc.//DTD Java Data Objects Metadata 2.0//EN" "http://java.sun.com/dtd/jdo_2_0.dtd">
     2	
     3	<jdo>
     4	  <package name="org.ecocean.servlet.importer">
     5	 
     6	 
     7	    <class name="ImportTask" identity-type="application" >
     8	      <field name="id" primary-key="true">
     9	        <column length="36" allows-null="false" />
    10	      </field>
    11	
    12	      <field name="encounters" persistence-modifier="persistent">
    13	        <collection element-type="org.ecocean.Encounter" />
    14	        <join/>
    15	      </field>
    16	
    17	      <field name="creator" persistence-modifier="persistent" element-type="org.ecocean.User" />
    18	
    19	      <field name="created" persistence-modifier="persistent" />
    20		  
    21		  <field name="status" persistence-modifier="persistent" />
    22	
    23	      <field name="parameters" persistence-modifier="persistent">
    24	          <column jdbc-type="LONGVARCHAR" name="parameters" />
    25	      </field>
    26	
    27	      <field name="errors" persistence-modifier="persistent">
    28	          <column jdbc-type="LONGVARCHAR" name="errors" />
    29	      </field>
    30	
    31	      <field name="log" persistence-modifier="persistent" serialized-element="true" />
    32	
    33	      <field name="iaTask" persistence-modifier="persistent" element-type="org.ecocean.ia.Task" />
    34	
    35	    </class> 
    36	  
    37	  </package>
    38	</jdo>

exec
/bin/bash -lc 'rg -n "mlServiceV2|mediaAssetIds|annotationId|deferredMatch|taskId" src/main/java/org/ecocean/servlet/IAGateway.java src/main/java/org/ecocean/ia/MlServiceProcessor.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '1,150p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '300,390p'" in /mnt/c/Wildbook-clean2
 succeeded in 285ms:
     1	package org.ecocean.servlet;
     2	
     3	import org.ecocean.AccessControl;
     4	import org.ecocean.Annotation;
     5	import org.ecocean.CommonConfiguration;
     6	import org.ecocean.ia.IA;
     7	import org.ecocean.ia.MLService;
     8	import org.ecocean.ia.Task;
     9	import org.ecocean.identity.*;
    10	import org.ecocean.media.*;
    11	import org.ecocean.Occurrence;
    12	import org.ecocean.queue.*;
    13	import org.ecocean.Resolver;
    14	import org.ecocean.servlet.importer.ImportTask;
    15	import org.ecocean.shepherd.core.Shepherd;
    16	import org.ecocean.User;
    17	import org.ecocean.Util;
    18	
    19	import javax.servlet.http.HttpServlet;
    20	import javax.servlet.http.HttpServletRequest;
    21	import javax.servlet.http.HttpServletResponse;
    22	import javax.servlet.ServletConfig;
    23	import javax.servlet.ServletException;
    24	import org.json.JSONArray;
    25	import org.json.JSONObject;
    26	
    27	import java.io.BufferedReader;
    28	import java.io.InputStreamReader;
    29	import java.io.IOException;
    30	import java.io.PrintWriter;
    31	import java.util.ArrayList;
    32	import java.util.List;
    33	import java.util.UUID;
    34	
    35	public class IAGateway extends HttpServlet {
    36	    private static Queue IAQueue = null;
    37	    private static Queue detectionQueue = null;
    38	    private static Queue acmIdQueue = null;
    39	    private static Queue IACallbackQueue = null;
    40	
    41	    public void init(ServletConfig config)
    42	    throws ServletException {
    43	        super.init(config);
    44	    }
    45	
    46	    public void doOptions(HttpServletRequest request, HttpServletResponse response)
    47	    throws ServletException, IOException {
    48	        ServletUtilities.doOptions(request, response);
    49	    }
    50	
    51	    public void doGet(HttpServletRequest request, HttpServletResponse response)
    52	    throws ServletException, IOException {
    53	        // "v2" is new IA package stuff -- so we just pass-thru and exit
    54	        String qstr = request.getQueryString();
    55	
    56	        // this seems to feed iaResults.jsp and is an unexpected dependency and extra URL call that shouldn't be needed
    57	        if ((qstr != null) && (qstr.matches(".*\\bv2\\b.*"))) {
    58	            response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
    59	            IA.handleGet(request, response);
    60	            return;
    61	        }
    62	        doPost(request, response);
    63	    }
    64	
    65	    public void doPost(HttpServletRequest request, HttpServletResponse response)
    66	    throws ServletException, IOException {
    67	        response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
    68	        String qstr = request.getQueryString();
    69	        // duplicated in both doGet and doPost
    70	        if ((qstr != null) && (qstr.matches(".*\\bcallback\\b.*"))) {
    71	            JSONObject rtn = queueCallback(request);
    72	            response.setContentType("text/plain");
    73	            PrintWriter out = response.getWriter();
    74	            out.println(rtn.toString());
    75	            out.close();
    76	            return;
    77	        }
    78	        String context = ServletUtilities.getContext(request); // note! this *must* be run after postStream stuff above
    79	
    80	        response.setContentType("text/plain");
    81	        PrintWriter out = response.getWriter();
    82	        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
    83	        JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
    84	        String taskId = Util.generateUUID();
    85	        res.put("taskId", taskId);
    86	        String baseUrl = null;
    87	        try {
    88	            String containerName = IA.getProperty("context0", "containerName");
    89	            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
    90	            if (containerName != null && containerName != "") {
    91	                baseUrl = baseUrl.replace("localhost", containerName);
    92	            }
    93	        } catch (Exception e) {
    94	            e.printStackTrace();
    95	        }
    96	        Shepherd myShepherd = new Shepherd(context);
    97	        myShepherd.setAction("IAGateway9");
    98	        myShepherd.beginDBTransaction();
    99	        try {
   100	            // v2 "forces" queueing -- onward to the glorious future!
   101	            // sendtoIAscripts for bulk command line detection use this v2 option
   102	            // uses detection queue
   103	            if (j.optBoolean("enqueue", false) || j.optBoolean("v2", false)) { // short circuits and just blindly writes out to queue and is done!
   104	                // TODO: if queue is not active/okay, fallback to synchronous
   105	                j.put("__context", context);
   106	                j.put("__baseUrl", baseUrl);
   107	                j.put("__enqueuedByIAGateway", System.currentTimeMillis());
   108	                // incoming json *probably* (should have) has taskId set... but if not i guess we use the one we generated???
   109	                if (j.optString("taskId", null) != null) {
   110	                    taskId = j.getString("taskId");
   111	                    res.put("taskId", taskId);
   112	                } else {
   113	                    j.put("taskId", taskId);
   114	                }
   115	                Task task = Task.load(taskId, myShepherd);
   116	                if (task == null) task = new Task(taskId);
   117	                JSONObject tparams = j.optJSONObject("taskParameters"); // optional
   118	                if (tparams == null) tparams = new JSONObject(); // but we want it, to set user:
   119	                User tuser = AccessControl.getUser(request, myShepherd);
   120	                if (tuser == null) { // "anonymous" but we want to make sure we zero these out to prevent them from being passed in
   121	                    tparams.remove("userId");
   122	                    tparams.remove("username");
   123	                } else {
   124	                    tparams.put("userId", tuser.getUUID());
   125	                    tparams.put("username", tuser.getUsername());
   126	                }
   127	                task.setParameters(tparams);
   128	                myShepherd.storeNewTask(task);
   129	                myShepherd.updateDBTransaction(); // hack
   130	                // myShepherd.closeDBTransaction();
   131	
   132	                boolean ok = false;
   133	                if (j.optJSONArray("annotationIds") != null) {
   134	                    // if this is just a single Encounter call, put it in the fast/detection lane to unblock small batch users
   135	                    if (j.optBoolean("fastlane", false)) {
   136	                        task.setQueueResumeMessage(j.toString());
   137	                        ok = addToDetectionQueue(context, j.toString());
   138	                    } else {
   139	                        task.setQueueResumeMessage(j.toString());
   140	                        ok = addToQueue(context, j.toString());
   141	                    }
   142	                } else {
   143	                    task.setQueueResumeMessage(j.toString());
   144	                    ok = addToDetectionQueue(context, j.toString());
   145	                }
   146	                if (ok) {
   147	                    System.out.println("INFO: taskId=" + taskId + " enqueued successfully");
   148	                    res.remove("error");
   149	                } else {
   150	                    System.out.println("ERROR: taskId=" + taskId +

 succeeded in 293ms:
src/main/java/org/ecocean/servlet/IAGateway.java:84:        String taskId = Util.generateUUID();
src/main/java/org/ecocean/servlet/IAGateway.java:85:        res.put("taskId", taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:108:                // incoming json *probably* (should have) has taskId set... but if not i guess we use the one we generated???
src/main/java/org/ecocean/servlet/IAGateway.java:109:                if (j.optString("taskId", null) != null) {
src/main/java/org/ecocean/servlet/IAGateway.java:110:                    taskId = j.getString("taskId");
src/main/java/org/ecocean/servlet/IAGateway.java:111:                    res.put("taskId", taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:113:                    j.put("taskId", taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:115:                Task task = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/servlet/IAGateway.java:116:                if (task == null) task = new Task(taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:133:                if (j.optJSONArray("annotationIds") != null) {
src/main/java/org/ecocean/servlet/IAGateway.java:147:                    System.out.println("INFO: taskId=" + taskId + " enqueued successfully");
src/main/java/org/ecocean/servlet/IAGateway.java:150:                    System.out.println("ERROR: taskId=" + taskId +
src/main/java/org/ecocean/servlet/IAGateway.java:192:        String taskId = res.optString("taskId", null);
src/main/java/org/ecocean/servlet/IAGateway.java:193:        if (taskId == null)
src/main/java/org/ecocean/servlet/IAGateway.java:194:            throw new RuntimeException("IAGateway._doDetect() has no taskId passed in");
src/main/java/org/ecocean/servlet/IAGateway.java:196:        Task task = Task.load(taskId, myShepherd); // might be null in some cases, such as non-queued
src/main/java/org/ecocean/servlet/IAGateway.java:197:        System.out.println("LOADED???? " + taskId + " --> " + task);
src/main/java/org/ecocean/servlet/IAGateway.java:207:        if (j.optJSONArray("mediaAssetIds") != null) {
src/main/java/org/ecocean/servlet/IAGateway.java:208:            JSONArray ids = j.getJSONArray("mediaAssetIds");
src/main/java/org/ecocean/servlet/IAGateway.java:235:            if (detectArgs != null) detectArgs.put("jobid", taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:251:                IBEISIA.log(taskId, validIds.toArray(new String[validIds.size()]), jobId,
src/main/java/org/ecocean/servlet/IAGateway.java:277:        String taskId = res.optString("taskId", null);
src/main/java/org/ecocean/servlet/IAGateway.java:278:        if (taskId == null)
src/main/java/org/ecocean/servlet/IAGateway.java:279:            throw new RuntimeException("IAGateway._doIdentify() has no taskId passed in");
src/main/java/org/ecocean/servlet/IAGateway.java:294:        JSONArray alist = j.optJSONArray("annotationIds");
src/main/java/org/ecocean/servlet/IAGateway.java:333:        Task parentTask = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/servlet/IAGateway.java:335:            System.out.println("WARNING: IAGateway._doIdentify() could not load Task id=" + taskId +
src/main/java/org/ecocean/servlet/IAGateway.java:337:            parentTask = new Task(taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:375:                jobj.getJSONObject("identify").put("annotationIds", new JSONArray());
src/main/java/org/ecocean/servlet/IAGateway.java:376:                jobj.getJSONObject("identify").getJSONArray("annotationIds").put(ann.getId());
src/main/java/org/ecocean/servlet/IAGateway.java:377:                jobj.put("taskId", subTask.getId());
src/main/java/org/ecocean/servlet/IAGateway.java:407:        taskRes.put("taskId", annTaskId);
src/main/java/org/ecocean/servlet/IAGateway.java:410:        taskRes.put("annotationIds", jids);
src/main/java/org/ecocean/servlet/IAGateway.java:493:    public static JSONObject taskSummary(JSONArray taskIds, Shepherd myShepherd) {
src/main/java/org/ecocean/servlet/IAGateway.java:496:        if ((taskIds == null) || (taskIds.length() < 1)) return rtn;
src/main/java/org/ecocean/servlet/IAGateway.java:497:        for (int i = 0; i < taskIds.length(); i++) {
src/main/java/org/ecocean/servlet/IAGateway.java:498:            String annId = taskIds.optString(i);
src/main/java/org/ecocean/servlet/IAGateway.java:620:        // this must have a taskId coming in, cuz otherwise how would (detached, async) caller know what it is!
src/main/java/org/ecocean/servlet/IAGateway.java:637:        if (jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:642:            System.out.println("IAGateway: mlServiceV2 job " +
src/main/java/org/ecocean/servlet/IAGateway.java:643:                jobj.optString("taskId", "?") + " → " + outcome.getKind() +
src/main/java/org/ecocean/servlet/IAGateway.java:649:        if ((jobj.optJSONObject("detect") != null) && (jobj.optString("taskId", null) != null)) {
src/main/java/org/ecocean/servlet/IAGateway.java:651:            res.put("taskId", jobj.getString("taskId"));
src/main/java/org/ecocean/servlet/IAGateway.java:692:        } else if ((jobj.optJSONObject("identify") != null) && (jobj.optString("taskId",
src/main/java/org/ecocean/servlet/IAGateway.java:693:            null) != null)) {                                                                               // ditto about taskId
src/main/java/org/ecocean/servlet/IAGateway.java:696:            res.put("taskId", jobj.getString("taskId"));
src/main/java/org/ecocean/servlet/IAGateway.java:703:            System.out.println(" > taskId = " + jobj.getString("taskId"));
src/main/java/org/ecocean/servlet/IAGateway.java:755:        String taskId = jobj.optString("taskId", "UNKNOWN_TASKID");
src/main/java/org/ecocean/servlet/IAGateway.java:764:            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
src/main/java/org/ecocean/servlet/IAGateway.java:768:        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
src/main/java/org/ecocean/servlet/IAGateway.java:786:                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
src/main/java/org/ecocean/servlet/IAGateway.java:792:                            jobj.optBoolean("mlServiceV2", false)) {
src/main/java/org/ecocean/servlet/IAGateway.java:793:                            // mlServiceV2 retries must land on the detection
src/main/java/org/ecocean/servlet/IAGateway.java:805:                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
src/main/java/org/ecocean/servlet/IAGateway.java:806:                            taskId + " due to " + ex.toString());
src/main/java/org/ecocean/servlet/IAGateway.java:909:        qjob.put("taskId", task.getId());
src/main/java/org/ecocean/servlet/IAGateway.java:910:        qjob.put("mediaAssetIds", maIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:30: * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:        if (jobData.optBoolean("deferredMatch", false)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:60:        String taskId = jobData.optString("taskId", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:65:            return processDetection(jobData, taxonomyString, taskId, encounterId, maId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:67:        if (jobData.has("annotationId")) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:68:            String annId = jobData.optString("annotationId", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:69:            return processExtraction(jobData, taxonomyString, taskId, annId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:72:            "neither mediaAssetId nor annotationId in payload");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:76:        String taskId, String encounterId, String maId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:80:            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:82:            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:95:            markTaskError(taskId, ex.getCode(), ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:101:            return finalizeZeroDetections(maId, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:104:        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:109:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:112:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:116:        String taskId, String annId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:120:            ext = loadExtractionContext(taxonomyString, taskId, annId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:122:            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:136:            markTaskError(taskId, ex.getCode(), ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:140:        PersistResult persisted = persistExtraction(annId, taskId, ext, response);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:144:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:147:        return MlServiceJobOutcome.ok(persisted.annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:150:    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:159:            Task task = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:204:    private ExtractionContext loadExtractionContext(String taxonomyString, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:211:            Task task = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:265:    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:271:            Task task = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:283:            markTaskError(taskId, "PERSIST", "zero-detection finalize failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:290:    private PersistResult persistDetections(String maId, String encounterId, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:294:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:301:            Task task = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:320:                    annotationIds.add(existing.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:362:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:368:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:370:            markTaskError(taskId, "PERSIST", "detection persist failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:378:    private PersistResult persistExtraction(String annId, String taskId, ExtractionContext ext,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:382:        List<String> annotationIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:387:            Task task = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:397:                annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:398:                return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:406:            annotationIds.add(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:408:            return PersistResult.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:410:            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:418:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:422:            if (!os.waitForVisibility("annotation", annotationIds, VISIBILITY_TIMEOUT_MS)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:424:                return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:428:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:430:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:437:        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:438:        String taskId = jobData.optString("taskId", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:441:        return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:446:        if (annotationIds == null || annotationIds.isEmpty()) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:447:            markTaskCompleted(taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:461:                Task task = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:467:            Task parent = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:495:            return MlServiceJobOutcome.ok(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:497:            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:576:    private JSONObject inferMatchConfig(List<String> annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:577:        if (annotationIds == null || annotationIds.isEmpty()) return null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:582:            for (String annId : annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:636:    private void markTaskError(String taskId, String code, String message) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:641:            Task task = Task.load(taskId, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:649:    private void markTaskCompleted(String taskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:654:            markTaskCompleted(Task.load(taskId, shep));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:683:        payload.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:684:        payload.put("deferredMatch", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:685:        payload.put("annotationIds", new JSONArray(annotationIds));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:686:        if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:826:        final List<String> annotationIds;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:829:        private PersistResult(List<String> annotationIds, MlServiceJobOutcome outcome) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:830:            this.annotationIds = annotationIds;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:834:        static PersistResult ok(List<String> annotationIds) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:835:            return new PersistResult(annotationIds, null);

 succeeded in 313ms:
   300	                    myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
   301	                if (ann == null) continue;
   302	                anns.add(ann);
   303	                validIds.add(aid);
   304	            }
   305	        }
   306	        // i think that "in the future" co-occurring annotations should be sent together as one set of query list; but since we dont have support for that now, we just send these all in one at a time.
   307	        JSONArray olist = j.optJSONArray("occurrenceIds");
   308	        if ((olist != null) && (olist.length() > 0)) {
   309	            for (int i = 0; i < olist.length(); i++) {
   310	                String oid = olist.optString(i, null);
   311	                if (oid == null) continue;
   312	                Occurrence occ = ((Occurrence)(myShepherd.getPM().getObjectById(
   313	                    myShepherd.getPM().newObjectIdInstance(Occurrence.class, oid), true)));
   314	// System.out.println("occ -> " + occ);
   315	                if (occ == null) continue;
   316	                List<MediaAsset> mas = occ.getAssets();
   317	// System.out.println("mas -> " + mas);
   318	                if ((mas == null) || (mas.size() < 1)) continue;
   319	                for (MediaAsset ma : mas) {
   320	                    ArrayList<Annotation> maAnns = ma.getAnnotations();
   321	// System.out.println("maAnns -> " + maAnns);
   322	                    if ((maAnns == null) || (maAnns.size() < 1)) continue;
   323	                    for (Annotation ann : maAnns) {
   324	                        if (validIds.contains(ann.getId())) continue;
   325	                        anns.add(ann);
   326	                        validIds.add(ann.getId());
   327	                    }
   328	                }
   329	            }
   330	        }
   331	        System.out.println("anns -> " + anns);
   332	
   333	        Task parentTask = Task.load(taskId, myShepherd);
   334	        if (parentTask == null) {
   335	            System.out.println("WARNING: IAGateway._doIdentify() could not load Task id=" + taskId +
   336	                "; creating it... yrros");
   337	            parentTask = new Task(taskId);
   338	        }
   339	        JSONArray taskList = new JSONArray();
   340	/* currently we are sending annotations one at a time (one per query list) but later we will have to support clumped sets...
   341	   things to consider for that - we probably have to further subdivide by species ... other considerations?   */
   342	        List<Task> subTasks = new ArrayList<Task>();
   343	        if (anns.size() > 1) { // need to create child Tasks
   344	            JSONObject params = parentTask.getParameters();
   345	            parentTask.setParameters((String)null); // reset this, kids inherit params
   346	            for (int i = 0; i < anns.size(); i++) {
   347	                Task newTask = new Task(parentTask);
   348	                newTask.setParameters(params);
   349	                newTask.addObject(anns.get(i));
   350	                myShepherd.storeNewTask(newTask);
   351	                myShepherd.beginDBTransaction();
   352	                subTasks.add(newTask);
   353	            }
   354	            myShepherd.storeNewTask(parentTask);
   355	            myShepherd.beginDBTransaction();
   356	        } else { // we just use the existing "parent" task
   357	            subTasks.add(parentTask);
   358	        }
   359	        for (int i = 0; i < anns.size(); i++) {
   360	            Annotation ann = anns.get(i);
   361	            JSONObject queryConfigDict = IBEISIA.queryConfigDict(myShepherd, opt);
   362	            JSONObject taskRes = new JSONObject();
   363	            Task subTask = subTasks.get(i);
   364	            try {
   365	                taskRes = _sendIdentificationTask(ann, context, baseUrl, queryConfigDict, null,
   366	                    limitTargetSize, subTask, myShepherd, fastlane);
   367	            } catch (Exception ex) { // unsure if maybe _some_ exceptions should be treated differently here?
   368	                System.out.println("subTask failure on " + subTask + ": " + ex.toString());
   369	                taskRes.put("success", false);
   370	                taskRes.put("error", ex.toString());
   371	                System.out.println(">>>>>>> parentTask: " + parentTask);
   372	                JSONObject jobj = new JSONObject();
   373	                jobj.put("identify", new JSONObject());
   374	                jobj.put("opt", opt);
   375	                jobj.getJSONObject("identify").put("annotationIds", new JSONArray());
   376	                jobj.getJSONObject("identify").getJSONArray("annotationIds").put(ann.getId());
   377	                jobj.put("taskId", subTask.getId());
   378	                jobj.put("__baseUrl", baseUrl);
   379	                jobj.put("__context", context);
   380	                jobj.put("__queueActualRetries", jin.optInt("__queueActualRetries", 0));
   381	                jobj.put("__queueRetries", jin.optInt("__queueRetries", 0));
   382	                jobj.put("__queueStart", jin.optLong("__queueStart", System.currentTimeMillis()));
   383	                System.out.println("_doIdentify() requeueing from jin=" + jin);
   384	                System.out.println("_doIdentify() requeueing as jobj=" + jobj);
   385	                requeueJob(jobj, true);
   386	            }
   387	            taskRes.put("subTaskId", subTask.getId());
   388	            taskRes.put("subTaskIndex", i);
   389	            taskList.put(taskRes);
   390	            System.out.println("subTask[" + i + "] => " + taskRes.toString());

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '600,660p'" in /mnt/c/Wildbook-clean2
 succeeded in 308ms:
   600	
   601	    public static Queue getIACallbackQueue(String context)
   602	    throws IOException {
   603	        IACallbackQueue = QueueUtil.getBest(context, "IACallback");
   604	        return IACallbackQueue;
   605	    }
   606	
   607	    public static void processQueueMessage(String message) {
   608	// System.out.println("DEBUG: IAGateway.processQueueMessage -> " + message);
   609	        if (message == null) return;
   610	        JSONObject jobj = null;
   611	        try {
   612	            jobj = new JSONObject(message);
   613	        } catch (org.json.JSONException jex) {
   614	            System.out.println(
   615	                "WARNING: IAGateway.processQueueMessage() failed to parse json from '" + message +
   616	                "' - " + jex.toString());
   617	            return;
   618	        }
   619	        if (jobj == null) return; // would this ever happen? #bsts
   620	        // this must have a taskId coming in, cuz otherwise how would (detached, async) caller know what it is!
   621	        // __context and __baseUrl should be set -- this is done automatically in IAGateway, but if getting here by some other method, do the work!
   622	        if (jobj.optBoolean("v2", false)) { // lets "new world" ia package do its thing
   623	            IA.handleRest(jobj);
   624	            return;
   625	        }
   626	        if (jobj.optBoolean("MLService", false)) {
   627	            MLService mlserv = new MLService();
   628	            mlserv.processQueueJob(jobj);
   629	            return;
   630	        }
   631	        // Migration plan v2 §commit #10a: ml-service v2 dispatcher branch.
   632	        // The new processor takes context in its constructor (no hardcoded
   633	        // "context0") and returns a typed outcome. Both the detection +
   634	        // extraction lifecycle (Phases 1-5) and the deferred-match path
   635	        // route here; MlServiceProcessor.process(jobj) handles routing
   636	        // internally based on the payload's flags.
   637	        if (jobj.optBoolean("mlServiceV2", false)) {
   638	            String mlContext = jobj.optString("__context", "context0");
   639	            org.ecocean.ia.MlServiceProcessor processor =
   640	                new org.ecocean.ia.MlServiceProcessor(mlContext);
   641	            org.ecocean.ia.MlServiceJobOutcome outcome = processor.process(jobj);
   642	            System.out.println("IAGateway: mlServiceV2 job " +
   643	                jobj.optString("taskId", "?") + " → " + outcome.getKind() +
   644	                (outcome.getCode() == null ? "" : " [" + outcome.getCode() + "]"));
   645	            return;
   646	        }
   647	        boolean requeue = false;
   648	        boolean requeueIncrement = false;
   649	        if ((jobj.optJSONObject("detect") != null) && (jobj.optString("taskId", null) != null)) {
   650	            JSONObject res = new JSONObject("{\"success\": false}");
   651	            res.put("taskId", jobj.getString("taskId"));
   652	            String context = jobj.optString("__context", "context0");
   653	            Shepherd myShepherd = new Shepherd(context);
   654	            myShepherd.setAction("IAGateway.processQueueMessage.detect");
   655	            myShepherd.beginDBTransaction();
   656	            String baseUrl = jobj.optString("__baseUrl", null);
   657	            try {
   658	                JSONObject rtn = _doDetect(jobj, res, myShepherd, baseUrl);
   659	                System.out.println(
   660	                    "INFO: IAGateway.processQueueMessage() 'detect' successful --> " +

exec
/bin/bash -lc 'rg -n "handleRest|mlServiceV2|mediaAssetIds|taskId|addToDetectionQueue|detect" src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/ia/MLService.java src/main/java/org/ecocean/ia/MlServiceClient.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '220,420p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '1,220p'" in /mnt/c/Wildbook-clean2
 succeeded in 258ms:
   220	        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
   221	            " assets; queued? = " + sent + "; " + topTask);
   222	        return topTask;
   223	    }
   224	
   225	    /**
   226	     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
   227	     * vector pipeline. Each MediaAsset gets its own child Task under
   228	     * topTask; each emits a {@code mlServiceV2:true} payload to the
   229	     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
   230	     * picks them up via the IAGateway dispatcher (commit #10a).
   231	     *
   232	     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
   233	     * topTask. The topTask itself remains as the aggregator that holds the
   234	     * full MediaAsset list for caller-side summary code.</p>
   235	     *
   236	     * <p>encounterId is derived best-effort from the MediaAsset's existing
   237	     * trivial annotation (every Encounter.addMediaAsset call creates one).
   238	     * If null, MlServiceProcessor persists annotations without explicit
   239	     * Encounter linkage and downstream MediaAsset.assignEncounters handles
   240	     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
   241	     */
   242	    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
   243	        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
   244	        int queued = 0;
   245	        for (MediaAsset ma : mas) {
   246	            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
   247	                queued++;
   248	            }
   249	        }
   250	        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
   251	            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
   252	        return topTask;
   253	    }
   254	
   255	    /**
   256	     * Build and enqueue one v2 ml-service job for a single MediaAsset.
   257	     * Returns {@code true} iff the FileQueue write succeeded.
   258	     *
   259	     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
   260	     * normal intake path) and the startup stale-mlservice reconciler in
   261	     * {@code StartupWildbook}. The reconciler relies on the boolean
   262	     * return to decide whether to commit accompanying state changes; the
   263	     * normal intake path tolerates the swallowed-failure behavior.</p>
   264	     *
   265	     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
   266	     * internally commits/reopens the transaction, so the child Task row
   267	     * is persisted before this method enqueues. On enqueue failure the
   268	     * child Task remains in the DB as an orphan — there is no queued
   269	     * job that will ever drive it. The orphan IS still discoverable
   270	     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
   271	     * the task references the MediaAsset through objectMediaAssets),
   272	     * so it may surface in operator-facing task listings until cleaned
   273	     * up by an out-of-band path. Callers that need cleanup should
   274	     * delete the orphan explicitly; the default posture here is to
   275	     * accept it since FileQueue write failures are rare.</p>
   276	     *
   277	     * <p>If {@code topTask} is null a fresh root task is created inside
   278	     * this method. This matches the reconciler's use case where there is
   279	     * no caller-side aggregator umbrella.</p>
   280	     */
   281	    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
   282	        Taxonomy taxy, Task topTask, String context, String baseUrl) {
   283	        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
   284	        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
   285	        singleton.add(ma);
   286	        childTask.setObjectMediaAssets(singleton);
   287	        myShepherd.storeNewTask(childTask);
   288	
   289	        // Best-effort encounterId via existing annotations on the MA.
   290	        String encounterId = null;
   291	        ArrayList<Annotation> existing = ma.getAnnotations();
   292	        if (existing != null) {
   293	            for (Annotation a : existing) {
   294	                Encounter enc = a.findEncounter(myShepherd);
   295	                if (enc != null) {
   296	                    encounterId = enc.getId();
   297	                    break;
   298	                }
   299	            }
   300	        }
   301	
   302	        JSONObject qjob = new JSONObject();
   303	        qjob.put("mlServiceV2", true);
   304	        qjob.put("mediaAssetId", ma.getId());
   305	        qjob.put("taxonomyString", taxy.getScientificName());
   306	        qjob.put("taskId", childTask.getId());
   307	        qjob.put("__context", context);
   308	        qjob.put("__baseUrl", baseUrl);
   309	        if (Util.stringExists(encounterId)) {
   310	            qjob.put("encounterId", encounterId);
   311	        }
   312	
   313	        try {
   314	            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
   315	        } catch (java.io.IOException iox) {
   316	            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
   317	                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
   318	            return false;
   319	        }
   320	    }
   321	
   322	    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
   323	        int count = 0;
   324	        int stopAfter = 200000;
   325	        int batchThreshold = 50;
   326	        int batchesSoFar = 0;
   327	        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();
   328	
   329	        try {
   330	            for (MediaAsset ma : mediaAssets) {
   331	                count++;
   332	                if (count > stopAfter) {
   333	                    break;
   334	                }
   335	                if (ma != null && !ma.hasAcmId()) {
   336	                    assetsWithMissingAcmids.add(ma);
   337	                }
   338	                if ((assetsWithMissingAcmids.size() >= batchThreshold) ||
   339	                    count == mediaAssets.size()) {
   340	                    if (assetsWithMissingAcmids.size() > 0) { // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this
   341	                        try {
   342	                            IBEISIA.sendMediaAssetsNew(assetsWithMissingAcmids,
   343	                                myShepherd.getContext());
   344	                        } catch (Exception e) {
   345	                            System.out.println(
   346	                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
   347	                            e.printStackTrace();
   348	                        }
   349	                    }
   350	                    batchesSoFar++;
   351	                    assetsWithMissingAcmids = new ArrayList<MediaAsset>();
   352	                    myShepherd.updateDBTransaction();
   353	                }
   354	            }
   355	        } catch (Exception e) {
   356	            System.out.println("Error in handleMissingAcmids in IA.java");
   357	            e.printStackTrace();
   358	            myShepherd.rollbackDBTransaction();
   359	        }
   360	    }
   361	
   362	    // similar behavior to above: basically fake /ia api call, but via queue
   363	    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
   364	    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
   365	        return intakeAnnotations(myShepherd, anns, null, false);
   366	    }
   367	
   368	    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
   369	        final Task parentTask, boolean fastlane) {
   370	        // List<List<Annotation>> annses = binAnnotsByIaClass(anns);
   371	        //// slightly complicated bc we need to create child tasks only if there are multiple iaClasses
   372	        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
   373	        //// here we make child tasks
   374	        // Task topTask = (parentTask==null) ? new Task() : parentTask;
   375	        // for (List<Annotation> annsOneIaClass: annses) {
   376	        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
   377	        // }
   378	        // return topTask;
   379	        // }
   380	        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
   381	        // System.out.println("Starting intakeAnnotations");
   382	        if ((anns == null) || (anns.size() < 1)) return null;
   383	        Task topTask = new Task();
   384	        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
   385	        topTask.setObjectAnnotations(anns);
   386	        String context = myShepherd.getContext();
   387	
   388	        /*
   389	            what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed for this we use
   390	               IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than one we need to make a set of subtasks
   391	         */
   392	
   393	/*
   394	        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
   395	           List<JSONObject> opts = null;
   396	        // below gets it working for dolphins but can be generalized easily from IA.properties String inferredIaClass =
   397	           IBEISIA.inferIaClass(anns.get(0), myShepherd);
   398	        String bottlenose = "dolphin_bottlenose_fin";
   399	        if (bottlenose.equals(iaClass) || bottlenose.equals(inferredIaClass)) {
   400	            System.out.println("IA.java is sending a Tursiops truncatus job");
   401	            opts = IBEISIA.identOpts(context, bottlenose);
   402	        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes opts = IBEISIA.identOpts(context);
   403	        }
   404	 */
   405	        // List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
   406	        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
   407	        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
   408	        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
   409	            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
   410	            // now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
   411	            if (opts != null) {
   412	                Iterator<JSONObject> itr = opts.iterator();
   413	                while (itr.hasNext()) {
   414	                    if (!itr.next().optBoolean("default", true)) itr.remove();
   415	                }
   416	            }
   417	            System.out.println("identOpts: " + opts);
   418	            List<Task> tasks = new ArrayList<Task>();
   419	            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
   420	            if (parentTask != null && parentTask.getParameters() != null) {

 succeeded in 253ms:
     1	/*
     2	    idea here is "simple": a gateway to all IA calls, mostly (now) cleaned up from identity/IBEISIA.java (guessing)
     3	
     4	    THIS IS A WORK-IN-PROGRESS
     5	
     6	    proposed key concepts:
     7	 * can handle multiple IA frameworks (not just historic-IBEIS)
     8	      - likely a base abstract class with a "isEnabled() / init()" concept
     9	      - classes would allow for instances of each IA framework?
    10	
    11	 * no idea how to handle crazy (and configurable!?) workflow!
    12	
    13	 * probably should "leverage" Queue stuff where applicable?
    14	      - possibly there is a NEED for both variations (as suggested by drew): an asynchronous (queued) and synchronous (not)
    15	
    16	 * simply entry point for: MediaAsset and Annotation???
    17	 */
    18	package org.ecocean.ia;
    19	
    20	import java.io.PrintWriter;
    21	import java.util.ArrayList;
    22	import java.util.Arrays;
    23	import java.util.HashMap;
    24	import java.util.Iterator;
    25	import java.util.List;
    26	import java.util.Map;
    27	import java.util.Properties;
    28	import javax.servlet.http.HttpServletRequest;
    29	import javax.servlet.http.HttpServletResponse;
    30	import org.ecocean.Annotation;
    31	import org.ecocean.CommonConfiguration;
    32	import org.ecocean.Embedding;
    33	import org.ecocean.Encounter;
    34	import org.ecocean.identity.IBEISIA;
    35	import org.ecocean.IAJsonProperties;
    36	import org.ecocean.media.MediaAsset;
    37	import org.ecocean.media.MediaAssetFactory;
    38	import org.ecocean.servlet.ServletUtilities;
    39	import org.ecocean.shepherd.core.Shepherd;
    40	import org.ecocean.shepherd.core.ShepherdProperties;
    41	import org.ecocean.Taxonomy;
    42	import org.ecocean.Util;
    43	import org.json.JSONArray;
    44	import org.json.JSONObject;
    45	
    46	public class IA {
    47	    private static final String PROP_FILE = "IA.properties";
    48	
    49	    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
    50	        - these should be treated as different in that an IA framework might batch together the list in some way (i.e. difference between sending as
    51	           list vs iterating over list with intake(each element)
    52	        - you only get one task ID for the list/group, is this a bad idea?
    53	     */
    54	    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
    55	        return intakeMediaAssets(myShepherd, new ArrayList<MediaAsset>(Arrays.asList(ma)));
    56	    }
    57	
    58	    // Annotations *may or may not* already be on an Encounter  #neverforget
    59	    public static Task intake(Shepherd myShepherd, Annotation ann) {
    60	        return intakeAnnotations(myShepherd, new ArrayList<Annotation>(Arrays.asList(ann)));
    61	    }
    62	
    63	/*  these have same erasure types so cant co-exist. :( another reason for a common baseclass.. sigh?
    64	    hence the overly-inclusive Object version below!
    65	    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
    66	        if ((mas == null) || (mas.size() < 1)) return null;
    67	        Task task = new Task();
    68	        return task;
    69	    }
    70	    public static Task intake(Shepherd myShepherd, List<Annotation> anns) {
    71	        if ((anns == null) || (anns.size() < 1)) return null;
    72	        Task task = new Task();
    73	        return task;
    74	    }
    75	 */
    76	
    77	    // i think objects ingested here must(?) be persisted (and committed), as we have to assume (or we know)
    78	    // that these processes will use queues which operate in different (Shepherd) threads and will thus try
    79	    // to find the objects via the db.  :/
    80	    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
    81	    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
    82	        return intakeMediaAssets(myShepherd, mas, null);
    83	    }
    84	
    85	    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
    86	        Task parentTask) {
    87	        List<List<MediaAsset> > assetsBySpecies = binAssetsBySpecies(mas, myShepherd);
    88	        int numSpecies = assetsBySpecies.size();
    89	
    90	        // System.out.println("IA.java.numSpecies="+numSpecies);
    91	        // in one-species case we don't need to create an extra layer of tasks
    92	        if (numSpecies == 1)
    93	            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
    94	        // in multi-species case we make sure we have a parent task and add each species task as a child
    95	        if (parentTask == null) parentTask = new Task();
    96	        for (List<MediaAsset> masOneSpecies : assetsBySpecies) {
    97	            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
    98	            parentTask.addChild(thisTask);
    99	        }
   100	        return parentTask;
   101	    }
   102	
   103	    public static List<List<MediaAsset> > binAssetsBySpecies(List<MediaAsset> mas,
   104	        Shepherd myShepherd) {
   105	        Map<String, List<MediaAsset> > assetsBySpecies = new HashMap<String, List<MediaAsset> >();
   106	
   107	        for (MediaAsset ma : mas) {
   108	            Taxonomy taxy = ma.getTaxonomy(myShepherd);
   109	            String scientificName = "null";
   110	            if (taxy != null && taxy.getScientificName() != null)
   111	                scientificName = taxy.getScientificName();
   112	            // System.out.println("     MA ID "+ma.getId()+" has taxy "+scientificName);
   113	            if (!assetsBySpecies.containsKey(scientificName))
   114	                assetsBySpecies.put(scientificName, new ArrayList<MediaAsset>());
   115	            assetsBySpecies.get(scientificName).add(ma);
   116	            // System.out.println("       Taxy size: "+assetsBySpecies.get(scientificName).size());
   117	        }
   118	        return new ArrayList<List<MediaAsset> >(assetsBySpecies.values());
   119	    }
   120	
   121	    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
   122	        final Task parentTask) {
   123	        if ((mas == null) || (mas.size() < 1)) return null;
   124	        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
   125	        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
   126	    }
   127	
   128	    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
   129	        Taxonomy taxy, final Task parentTask) {
   130	        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
   131	    }
   132	
   133	    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
   134	        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
   135	        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
   136	            " media assets:");
   137	        handleMissingAcmids(mas, myShepherd);
   138	        for (MediaAsset ma : mas) {
   139	            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
   140	            System.out.println("acmid is: " + ma.getAcmId());
   141	        }
   142	        JSONArray maArr = new JSONArray();
   143	        for (MediaAsset ma : mas) {
   144	            maArr.put(ma.getId());
   145	        }
   146	        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());
   147	
   148	        Task topTask = new Task();
   149	        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
   150	        topTask.setObjectMediaAssets(mas);
   151	        myShepherd.storeNewTask(topTask);
   152	
   153	        String context = myShepherd.getContext();
   154	        String baseUrl = getBaseURL(context);
   155	
   156	        // Ia configs are keyed off taxonomies
   157	        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
   158	
   159	        // Migration plan v2 §commit #10b: routing reroute.
   160	        // If the species' _id_conf.default.pipeline_root is "vector" AND
   161	        // _mlservice_conf is configured, route per-asset through the
   162	        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
   163	        // WBIA path below — production deployments without _mlservice_conf
   164	        // see no behavior change at all.
   165	        //
   166	        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
   167	        // child finalization is local; no first-finisher-wins. The topTask
   168	        // remains as the aggregator for the caller contract (and so legacy
   169	        // summary code that reads topTask.objectMediaAssets keeps working).
   170	        if (iaConfig != null && taxy != null &&
   171	            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
   172	            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
   173	                context, baseUrl);
   174	        }
   175	
   176	        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
   177	        JSONObject dj = new JSONObject();
   178	        dj.put("mediaAssetIds", maArr);
   179	        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
   180	        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
   181	        Boolean[] sent = new Boolean[numDetectAlgos];
   182	        for (int i = 0; i < numDetectAlgos; i++) {
   183	            // task for this job (only create new (child) tasks if multiple detect algos)
   184	            Task task = (numDetectAlgos == 1) ? topTask : new Task();
   185	            task.setObjectMediaAssets(mas);
   186	            task.setParameters(topTask.getParameters());
   187	
   188	            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
   189	            task.addParameter("detectArgs", detectArgs);
   190	
   191	            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
   192	            task.addParameter("__detect_url", detectionUrl);
   193	
   194	            JSONObject qjob = new JSONObject();
   195	            qjob.put("detect", dj);
   196	            qjob.put("__detect_args", detectArgs);
   197	            qjob.put("__detect_url", detectionUrl);
   198	            // task is queued here
   199	            qjob.put("taskId", topTask.getId());
   200	            qjob.put("__context", context);
   201	            qjob.put("__baseUrl", baseUrl);
   202	            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
   203	            if (tweetAssetId != -1) {
   204	                qjob.put("tweetAssetId", tweetAssetId);
   205	            }
   206	            System.out.println(
   207	                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
   208	            sent[i] = false;
   209	            try {
   210	                // job is queued here
   211	                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
   212	                    qjob.toString());
   213	            } catch (java.io.IOException iox) {
   214	                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
   215	                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
   216	                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
   217	                    iox.toString());
   218	            }
   219	        }
   220	        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +

 succeeded in 332ms:
src/main/java/org/ecocean/ia/MLService.java:91:        if (task != null) jobData.put("taskId", task.getId());
src/main/java/org/ecocean/ia/MLService.java:92:        IAGateway.addToDetectionQueue("context0", jobData.toString());
src/main/java/org/ecocean/ia/MLService.java:104:        data.put("mediaAssetIds", maIds);
src/main/java/org/ecocean/ia/MLService.java:127:        Task task = myShepherd.getTask(jobData.optString("taskId", null));
src/main/java/org/ecocean/ia/MLService.java:128:        JSONArray ids = jobData.optJSONArray("mediaAssetIds");
src/main/java/org/ecocean/ia/MLService.java:395:    // this is to request detection find an annotation and (optionally) return embedding as well
src/main/java/org/ecocean/ia/MlServiceClient.java:93:     * (user-drawn bbox; no detection step needed).
src/main/java/org/ecocean/ia/MlServiceClient.java:160:        // Zero detections is a valid response. Each present result must be
src/main/java/org/ecocean/ia/IA.java:178:        dj.put("mediaAssetIds", maArr);
src/main/java/org/ecocean/ia/IA.java:183:            // task for this job (only create new (child) tasks if multiple detect algos)
src/main/java/org/ecocean/ia/IA.java:188:            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
src/main/java/org/ecocean/ia/IA.java:189:            task.addParameter("detectArgs", detectArgs);
src/main/java/org/ecocean/ia/IA.java:191:            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
src/main/java/org/ecocean/ia/IA.java:192:            task.addParameter("__detect_url", detectionUrl);
src/main/java/org/ecocean/ia/IA.java:195:            qjob.put("detect", dj);
src/main/java/org/ecocean/ia/IA.java:196:            qjob.put("__detect_args", detectArgs);
src/main/java/org/ecocean/ia/IA.java:197:            qjob.put("__detect_url", detectionUrl);
src/main/java/org/ecocean/ia/IA.java:199:            qjob.put("taskId", topTask.getId());
src/main/java/org/ecocean/ia/IA.java:211:                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
src/main/java/org/ecocean/ia/IA.java:215:                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
src/main/java/org/ecocean/ia/IA.java:228:     * topTask; each emits a {@code mlServiceV2:true} payload to the
src/main/java/org/ecocean/ia/IA.java:229:     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
src/main/java/org/ecocean/ia/IA.java:240:     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
src/main/java/org/ecocean/ia/IA.java:303:        qjob.put("mlServiceV2", true);
src/main/java/org/ecocean/ia/IA.java:306:        qjob.put("taskId", childTask.getId());
src/main/java/org/ecocean/ia/IA.java:314:            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
src/main/java/org/ecocean/ia/IA.java:317:                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
src/main/java/org/ecocean/ia/IA.java:473:                qjob.put("taskId", tasks.get(i).getId());
src/main/java/org/ecocean/ia/IA.java:484:                        sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
src/main/java/org/ecocean/ia/IA.java:518:    // possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
src/main/java/org/ecocean/ia/IA.java:520:    public static void handleRest(JSONObject jin) {
src/main/java/org/ecocean/ia/IA.java:525:            throw new RuntimeException("IA.handleRest(): passed data has no __context");
src/main/java/org/ecocean/ia/IA.java:531:        myShepherd.setAction("IA.handleRest");
src/main/java/org/ecocean/ia/IA.java:534:            String taskId = jin.optString("taskId", Util.generateUUID());
src/main/java/org/ecocean/ia/IA.java:535:            Task topTask = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/ia/IA.java:536:            if (topTask == null) topTask = new Task(taskId);
src/main/java/org/ecocean/ia/IA.java:540:            JSONArray mlist = jin.optJSONArray("mediaAssetIds");
src/main/java/org/ecocean/ia/IA.java:553:                System.out.println("INFO: IA.handleRest() just intook MediaAssets as " + mtask +
src/main/java/org/ecocean/ia/IA.java:584:                System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask +
src/main/java/org/ecocean/ia/IA.java:610:        String taskId = request.getParameter("taskId");
src/main/java/org/ecocean/ia/IA.java:611:        if (taskId != null) {
src/main/java/org/ecocean/ia/IA.java:612:            Task task = Task.load(taskId, myShepherd);
src/main/java/org/ecocean/ia/IA.java:614:                response.sendError(404, "Not found: taskId=" + taskId);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '518,590p'" in /mnt/c/Wildbook-clean2
 succeeded in 311ms:
   518	    // possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
   519	    // note: this is processed *from the queue* and as such does not have "output"
   520	    public static void handleRest(JSONObject jin) {
   521	        System.out.println("JIN JIN JIN: " + jin);
   522	        if (jin == null) return;
   523	        String context = jin.optString("__context", null);
   524	        if (context == null)
   525	            throw new RuntimeException("IA.handleRest(): passed data has no __context");
   526	        Shepherd myShepherd = new Shepherd(context);
   527	
   528	        // check if these should be directed through the fastlane
   529	        boolean fastlane = false;
   530	        if (jin.optBoolean("fastlane", false)) { fastlane = true; }
   531	        myShepherd.setAction("IA.handleRest");
   532	        myShepherd.beginDBTransaction();
   533	        try {
   534	            String taskId = jin.optString("taskId", Util.generateUUID());
   535	            Task topTask = Task.load(taskId, myShepherd);
   536	            if (topTask == null) topTask = new Task(taskId);
   537	            if (fastlane) topTask.addParameter("fastlane", true);
   538	            myShepherd.storeNewTask(topTask);
   539	            JSONObject opt = jin.optJSONObject("opt"); // should use this to decide how to branch differently than "default"
   540	            JSONArray mlist = jin.optJSONArray("mediaAssetIds");
   541	            if ((mlist != null) && (mlist.length() > 0)) {
   542	                System.out.println("MLIST: " + mlist);
   543	                List<MediaAsset> mas = new ArrayList<MediaAsset>();
   544	                for (int i = 0; i < mlist.length(); i++) {
   545	                    int mid = mlist.optInt(i, -1);
   546	                    if (mid < 1) continue;
   547	                    MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
   548	                    System.out.println(i + " -> " + ma);
   549	                    if (ma == null) continue;
   550	                    mas.add(ma);
   551	                }
   552	                Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
   553	                System.out.println("INFO: IA.handleRest() just intook MediaAssets as " + mtask +
   554	                    " for (parent) " + topTask);
   555	                topTask.addChild(mtask);
   556	            }
   557	            JSONArray alist = jin.optJSONArray("annotationIds");
   558	            if ((alist != null) && (alist.length() > 0)) {
   559	                List<Annotation> anns = new ArrayList<Annotation>();
   560	                for (int i = 0; i < alist.length(); i++) {
   561	                    String aid = alist.optString(i, null);
   562	                    if (aid == null) continue;
   563	                    Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
   564	                        myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
   565	                    if (ann == null) continue;
   566	                    anns.add(ann);
   567	                }
   568	                // okay, if we are sending another ID job from the hburger menu, the media asset needs to be added to your top level 'root' task,
   569	                // or else you will link to the original root task
   570	                List<MediaAsset> masForNewRoot = new ArrayList<>();
   571	                for (Annotation ann : anns) {
   572	                    MediaAsset ma = ann.getMediaAsset();
   573	                    if (ma != null && !masForNewRoot.contains(ma)) {
   574	                        masForNewRoot.add(ma);
   575	                    }
   576	                }
   577	                // i cant think of a scenario where we would get here and accidently double-add mas... but jic
   578	                for (MediaAsset ma : masForNewRoot) {
   579	                    if (!topTask.getObjectMediaAssets().contains(ma)) {
   580	                        topTask.addObject(ma);
   581	                    }
   582	                }
   583	                Task atask = intakeAnnotations(myShepherd, anns, topTask, fastlane);
   584	                System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask +
   585	                    " for " + topTask);
   586	                myShepherd.getPM().refresh(topTask);
   587	                topTask.addChild(atask);
   588	                topTask.setModified();
   589	                myShepherd.getPM().makePersistent(atask);
   590	            }

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '590,607p'" in /mnt/c/Wildbook-clean2
 succeeded in 305ms:
   590	            }
   591	            myShepherd.commitDBTransaction();
   592	        } catch (Exception e) {
   593	            e.printStackTrace();
   594	            myShepherd.rollbackDBTransaction();
   595	        } finally {
   596	            myShepherd.closeDBTransaction();
   597	        }
   598	    }
   599	
   600	    // via IAGateway servlet, we handle the work
   601	    public static void handleGet(HttpServletRequest request, HttpServletResponse response)
   602	    throws java.io.IOException {
   603	        // JSONObject rtn = queueCallback(request);
   604	        JSONObject rtn = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
   605	        String context = ServletUtilities.getContext(request);
   606	        Shepherd myShepherd = new Shepherd(context);
   607	

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/bulk/BulkImporter.java | sed -n '700,742p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/BulkImport.java | sed -n '450,510p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/BulkImport.java | sed -n '721,760p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/BulkImport.java | sed -n '560,610p'" in /mnt/c/Wildbook-clean2
 succeeded in 298ms:
   700	                if (multi != null) kws.addAll(Arrays.asList(multi.split("_")));
   701	            }
   702	            handleKeywords(ma, kws);
   703	            Annotation ann = new Annotation(tx, ma);
   704	            ann.setIsExemplar(true);
   705	            ann.setSkipAutoIndexing(true);
   706	            if ((offset < maQuality.size()) && (maQuality.get(offset) != null))
   707	                ann.setQuality(fmap.get(maQuality.get(offset)).getValueDouble());
   708	            annots.add(ann);
   709	            offset++;
   710	        }
   711	        if (annots.size() > 0) enc.addAnnotations(annots);
   712	        System.out.println("+ populated " + annots.size() + " MediaAssets on " + enc);
   713	    }
   714	
   715	    public void markProgress(int ticks, int total, double base, double weight) {
   716	        if (this.importTaskId == null) return;
   717	        // we want our own shepherd here so we can persist this task independent of our main shepherd
   718	        Shepherd taskShepherd = new Shepherd(this.myShepherd.getContext());
   719	        taskShepherd.setAction("BulkImporter.markProgress");
   720	        taskShepherd.beginDBTransaction();
   721	        try {
   722	            ImportTask itask = taskShepherd.getImportTask(this.importTaskId);
   723	            if (itask == null) return;
   724	            Double progress = base + (weight * new Double(ticks) / new Double(total));
   725	            itask.setProcessingProgress(progress);
   726	            taskShepherd.storeNewImportTask(itask);
   727	        } catch (Exception ex) {
   728	            ex.printStackTrace();
   729	        } finally {
   730	            taskShepherd.commitDBTransaction();
   731	            taskShepherd.closeDBTransaction();
   732	        }
   733	    }
   734	
   735	    public List<Encounter> getEncounters() {
   736	        return new ArrayList<Encounter>(encounterCache.values());
   737	    }
   738	
   739	    private void handleKeywords(MediaAsset ma, Set<String> keywordValues) {
   740	        for (String kval : keywordValues) {
   741	            if (kval == null) continue;
   742	            Keyword key = keywordCache.get(kval);

 succeeded in 295ms:
   721	    private void initializeImportTask(String id, User passedUser, JSONObject payload,
   722	        String status) {
   723	        if ((passedUser == null) || (passedUser.getId() == null)) {
   724	            System.out.println("[WARNING] initializeImportTask(" + id + ") got null user: " +
   725	                passedUser);
   726	            return;
   727	        }
   728	        Shepherd taskShepherd = new Shepherd("context0");
   729	
   730	        taskShepherd.setAction("BulkImport.initializeImportTask");
   731	        taskShepherd.beginDBTransaction();
   732	        try {
   733	            User user = taskShepherd.getUserByUUID(passedUser.getId()); // needs to be on our shepherd
   734	            ImportTask itask = taskShepherd.getImportTask(id);
   735	            if (itask != null) {
   736	                itask.addLog(
   737	                    "WARNING! BulkImport api POST passed EXISTING bulkImportId, reusing this ImportTask");
   738	                System.out.println(
   739	                    "WARNING: BulkImport api POST passed EXISTING bulkImportId, reusing this ImportTask ***************** "
   740	                    + itask);
   741	            } else {
   742	                itask = new ImportTask(user, id);
   743	            }
   744	            itask.setIATask(null);
   745	            itask.setProcessingProgress(0.0D);
   746	            itask.setEncounters(null);
   747	            itask.setErrors(null);
   748	            itask.setStatus(status);
   749	            JSONObject passedParams = new JSONObject();
   750	            for (String k : payload.keySet()) {
   751	                if (k.equals("rows") || k.equals("fieldNames")) continue; // skip the data, basically
   752	                passedParams.put(k, payload.get(k));
   753	            }
   754	            itask.setPassedParameters(passedParams);
   755	            taskShepherd.storeNewImportTask(itask);
   756	            taskShepherd.commitDBTransaction();
   757	        } catch (Exception ex) {
   758	            ex.printStackTrace();
   759	            taskShepherd.rollbackDBTransaction();
   760	        } finally {

 succeeded in 298ms:
   450	                                    validatedRows, maMap, bgUser, bgShepherd);
   451	                                JSONObject results = null;
   452	                                if (!blockedByMAErrors) {
   453	                                    try {
   454	                                        BulkImporter.logProgress(bulkImportId,
   455	                                            "doPost: bg pre-createImport()", startTime);
   456	                                        results = importer.createImport();
   457	                                        BulkImporter.logProgress(bulkImportId,
   458	                                            "doPost: bg post-createImport()", startTime);
   459	                                        success = true;
   460	                                    } catch (ServletException ex) {
   461	                                        // this will overwrite existing errors, but likely we dont have any here?
   462	                                        rtn.put("errors", ex.toString());
   463	                                        System.out.println(
   464	                                            "ERROR: background importer on ImportTask " +
   465	                                            bulkImportId + "failed with: " + ex);
   466	                                        ex.printStackTrace();
   467	                                    }
   468	                                    System.out.println(bulkImportId + " IMPORTER RESULTS => " +
   469	                                        results);
   470	                                }
   471	                                if (results != null)
   472	                                    for (String rkey : results.keySet()) {
   473	                                        rtn.put(rkey, results.get(rkey));
   474	                                    }
   475	                                if (!verboseReturn) {
   476	                                    rtn.remove("mediaAssets");
   477	                                    rtn.remove("encounters");
   478	                                    rtn.remove("sightings");
   479	                                    rtn.remove("individuals");
   480	                                }
   481	                                rtn.put("success", success);
   482	                                if (success) {
   483	                                    // we must use our shepherd, as encounters are associated with it
   484	                                    ImportTask itask = bgShepherd.getImportTask(bulkImportId);
   485	                                    if (itask == null) {
   486	                                        System.out.println(
   487	                                            "[ERROR] successful bg import could not load ImportTask for "
   488	                                            + bulkImportId);
   489	                                    } else {
   490	                                        Util.mark("success; writing final ImportTask update");
   491	                                        itask.setProcessingProgress(1.0D);
   492	                                        itask.setEncounters(importer.getEncounters());
   493	                                        itask.addLog("import complete");
   494	                                        if (bgSkipDetection) {
   495	                                            itask.addLog("detection skipped; task complete");
   496	                                            itask.setStatus("complete");
   497	                                        } else {
   498	                                            itask.setStatus("imported");
   499	                                            bgEncAssets = generateEncAssets(
   500	                                                importer.getEncounters());
   501	                                        }
   502	                                        bgShepherd.storeNewImportTask(itask);
   503	                                    }
   504	                                } else {
   505	                                    // i think errors will be set on task at this point
   506	                                    importTaskSet(bulkImportId, "failed", null, null);
   507	                                }
   508	                                // taskShepherd.storeNewImportTask(bgTask);
   509	                            } catch (Exception ex) {
   510	                                System.out.println(

 succeeded in 350ms:
   560	                        rtn.put("success", false);
   561	                        statusCode = 400;
   562	                    } else {
   563	                        BulkImporter importer = new BulkImporter(bulkImportId, validatedRows, maMap,
   564	                            currentUser, myShepherd);
   565	                        BulkImporter.logProgress(bulkImportId, "doPost: fg pre-createImport()",
   566	                            startTime);
   567	                        JSONObject results = importer.createImport();
   568	                        BulkImporter.logProgress(bulkImportId, "doPost: fg post-createImport()",
   569	                            startTime);
   570	                        for (String rkey : results.keySet()) {
   571	                            rtn.put(rkey, results.get(rkey));
   572	                        }
   573	                        if (!verboseReturn) {
   574	                            rtn.remove("mediaAssets");
   575	                            rtn.remove("encounters");
   576	                            rtn.remove("sightings");
   577	                            rtn.remove("individuals");
   578	                        }
   579	                        // we must use our shepherd, as encounters are associated with it
   580	                        ImportTask itask = myShepherd.getImportTask(bulkImportId);
   581	                        if (itask == null) {
   582	                            System.out.println(
   583	                                "[ERROR] successful fg import could not load ImportTask for " +
   584	                                bulkImportId);
   585	                        } else {
   586	                            Util.mark("success; writing final ImportTask update");
   587	                            itask.setProcessingProgress(1.0D);
   588	                            itask.setEncounters(importer.getEncounters());
   589	                            itask.addLog("import complete");
   590	                            if (skipDetection) {
   591	                                itask.addLog("detection skipped; task complete");
   592	                                itask.setStatus("complete");
   593	                            } else {
   594	                                itask.setStatus("imported");
   595	                                encAssets = generateEncAssets(importer.getEncounters());
   596	                            }
   597	                            myShepherd.storeNewImportTask(itask);
   598	                        }
   599	                        rtn.put("success", true);
   600	                        statusCode = 200;
   601	                    }
   602	                    rtn.put("processingTime", System.currentTimeMillis() - startProcess);
   603	                    Util.mark("END [foreground] createImport() for " + bulkImportId, startProcess);
   604	                    BulkImporter.logProgress(bulkImportId, "doPost: fg DONE", startTime);
   605	                }
   606	            }
   607	        } catch (ServletException ex) { // should just be thrown, not caught (below)
   608	            System.out.println("BulkImport.doPost() threw " + ex);
   609	            ex.printStackTrace();
   610	            throw ex;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/BulkImport.java | sed -n '510,548p'" in /mnt/c/Wildbook-clean2
 succeeded in 302ms:
   510	                                System.out.println(
   511	                                    "ERROR: background importing process on ImportTask " +
   512	                                    bulkImportId + "failed with: " + ex);
   513	                                ex.printStackTrace();
   514	                            } finally {
   515	                                if (success) {
   516	                                    bgShepherd.commitDBTransaction();
   517	                                } else {
   518	                                    bgShepherd.rollbackDBTransaction();
   519	                                }
   520	                                bgShepherd.closeDBTransaction();
   521	                                if (success && !bgSkipDetection)
   522	                                    initiateIA(bulkImportId, bgSkipIdentification, bgEncAssets,
   523	                                        matchingSetFilter);
   524	                            }
   525	                            rtn.put("processingTime", System.currentTimeMillis() - startProcess);
   526	                            archiveBulkJson(rtn,
   527	                                "backgroundComplete_" + (success ? "success" : "failed"));
   528	                            Util.mark("END [background] createImport() for " + bulkImportId,
   529	                                startProcess);
   530	                            BulkImporter.logProgress(bulkImportId, "doPost: bg DONE", startTime);
   531	                        }
   532	                    };
   533	                    new Thread(r).start();
   534	                    rtn.put("processingTime", System.currentTimeMillis() - startProcess);
   535	                    rtn.put("backgrounded", true);
   536	                    rtn.put("success", true);
   537	                    statusCode = 200;
   538	                } else {
   539	                    // foreground processing
   540	                    initializeImportTask(bulkImportId, currentUser, payload,
   541	                        "processing-foreground");
   542	                    int numNewErrors = dataErrors.length();
   543	                    BulkImporter.logProgress(bulkImportId, "doPost: fg pre-createMediaAssets()",
   544	                        startTime);
   545	                    Map<String, MediaAsset> maMap = createMediaAssets(bulkImportId, validFiles,
   546	                        dataErrors, myShepherd, startTime);
   547	                    BulkImporter.logProgress(bulkImportId, "doPost: fg post-createMediaAssets()",
   548	                        startTime);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/BulkImport.java | sed -n '610,640p'" in /mnt/c/Wildbook-clean2
 succeeded in 302ms:
   610	            throw ex;
   611	        } catch (ApiException apiEx) {
   612	            statusCode = 400;
   613	            System.out.println("BulkImport.doPost() returning 400 due to " + apiEx + " errors=" +
   614	                apiEx.getErrors());
   615	            rtn.put("errors", apiEx.getErrors());
   616	        } catch (Exception ex) {
   617	            rtn.put("error", ex.toString());
   618	            statusCode = 500;
   619	            ex.printStackTrace();
   620	        } finally {
   621	            if ((statusCode == 200) && !validateOnly) {
   622	                myShepherd.commitDBTransaction();
   623	            } else {
   624	                myShepherd.rollbackDBTransaction();
   625	            }
   626	            myShepherd.closeDBTransaction();
   627	            if ((statusCode == 200) && !skipDetection)
   628	                initiateIA(dupId, skipIdentification, encAssets, matchingSetFilter);
   629	        }
   630	        rtn.put("statusCode", statusCode);
   631	        response.setStatus(statusCode);
   632	        response.setCharacterEncoding("UTF-8");
   633	        response.setHeader("Content-Type", "application/json");
   634	        response.getWriter().write(rtn.toString());
   635	        archiveBulkJson(rtn, "return" + statusCode);
   636	        BulkImporter.logProgress(rtn.optString("bulkImportId", "(unknown)"), "doPost: fg EXIT",
   637	            startTime);
   638	    }
   639	
   640	    protected void doDelete(HttpServletRequest request, HttpServletResponse response)

exec
/bin/bash -lc 'rg -n "getMatchingSetQuery|acmId|exists|ids|must_not|exclude|same" src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/Annotation.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '900,1010p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '240,360p'" in /mnt/c/Wildbook-clean2
 succeeded in 257ms:
   240	            if (txStr == null) {
   241	                embData.put(ann.getId(), "null taxonomy");
   242	                continue;
   243	            }
   244	            try {
   245	                mls.send(ann, txStr, myShepherd);
   246	                System.out.println("catchUpEmbeddings: completed " + ann);
   247	                ok++; // send() may have found duplicate and not added new, but we count as ok
   248	                /// maybe set on embData when we have *no embeddings* but did not have exception??
   249	            } catch (IAException ex) {
   250	                // certain cases we store in embData, so they *will not be retried later*
   251	                // TODO decide actual cases!!
   252	                embData.put(ann.getId(), ex.toString());
   253	                System.out.println("catchUpEmbeddings: exception " + ann + " -> " + ex);
   254	            }
   255	        }
   256	        System.out.println("catchUpEmbeddings: finished with lastId=" + lastId);
   257	        embData.put("_runCount", ct);
   258	        embData.put("_runOk", ok);
   259	        embData.put("_runIds", runIds);
   260	        embData.put("_lastId", lastId);
   261	        SystemValue.set(myShepherd, "EMBEDDING_CATCHUP", embData);
   262	        return embData;
   263	    }
   264	
   265	    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
   266	        // Migration plan v2 §commit #3: gate accepts the new _id_conf
   267	        // contract (entries with `method`/`version`/`pipeline_root` and no
   268	        // api_endpoint) as well as legacy entries (with `api_endpoint`).
   269	        if (iaConfig == null) return false;
   270	        boolean isVectorConfig = Util.stringExists(iaConfig.optString("method", null))
   271	            || Util.stringExists(iaConfig.optString("api_endpoint", null));
   272	        if (!isVectorConfig) return false;
   273	        // from here on out we should return true since this is a vector match, even when something goes wrong
   274	        // and we should also set status on the task (and subtasks)
   275	        if (task == null) return true; // cant really set status on this :(
   276	        if (task.numberAnnotations() < 1) {
   277	            task.setStatus("completed");
   278	            task.setCompletionDateInMilliseconds();
   279	            return true;
   280	        }
   281	        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
   282	            task.numberAnnotations() + " annots) has embedding match: " + iaConfig);
   283	        // Track per-subtask outcomes so the parent task's terminal state
   284	        // reflects reality (plan v2 §commit #3: previous code unconditionally
   285	        // marked the parent "completed" even if every subtask failed).
   286	        int subtasksOk = 0;
   287	        int subtasksFailed = 0;
   288	        for (Annotation ann : task.getObjectAnnotations()) {
   289	            // every ann gets a subTask
   290	            Task subTask = new Task(task);
   291	            subTask.addObject(ann);
   292	            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
   293	            // TODO not sure if this is wise, or it would be better to just fail outright and let some background process do this
   294	            if (ann.numberEmbeddings() < 1) {
   295	                System.out.println(
   296	                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
   297	                    ann);
   298	                try {
   299	                    ann.extractEmbeddings(myShepherd);
   300	                } catch (IAException ex) {
   301	                    System.out.println(
   302	                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
   303	                        " due to: " + ex);
   304	                }
   305	                // if none now, we just fail and continue onto next annot
   306	                if (ann.numberEmbeddings() < 1) {
   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
   308	                        " due to no suitable embeddings for " + iaConfig);
   309	                    subTask.setStatus("error");
   310	                    subTask.setStatusDetailsAddError("REQUIRED",
   311	                        "no suitable embeddings for getMatches()");
   312	                    subTask.setCompletionDateInMilliseconds();
   313	                    myShepherd.getPM().makePersistent(subTask);
   314	                    subtasksFailed++;
   315	                    continue;
   316	                }
   317	            }
   318	            // Build matchingSetQuery for the candidate count.
   319	            boolean useClauses = false; // TODO how??
   320	            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
   321	                useClauses);
   322	            // getMatchingSetQuery can return null (e.g. encounter missing,
   323	            // taxonomy filtered out). Skip this subtask cleanly rather than
   324	            // NPE'ing on the queryCount/getMatchQuery below.
   325	            if (matchingSetQuery == null) {
   326	                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
   327	                subTask.setStatus("error");
   328	                subTask.setStatusDetailsAddError("REQUIRED", "null matchingSetQuery");
   329	                subTask.setCompletionDateInMilliseconds();
   330	                myShepherd.getPM().makePersistent(subTask);
   331	                subtasksFailed++;
   332	                continue;
   333	            }
   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
   335	            // matchingSetQuery in place (Annotation.java:1203). The previous
   336	            // order returned the knn result count instead of the candidate
   337	            // count. Defense in depth: also pass a deep clone of
   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
   339	            // never silently changes shape if a future caller relies on it.
   340	            OpenSearch os = new OpenSearch();
   341	            int numberCandidates = -2;
   342	            try {
   343	                numberCandidates = os.queryCount("annotation", matchingSetQuery);
   344	            } catch (IOException ex) {
   345	                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
   346	            }
   347	            // Read method/version directly from the new _id_conf contract;
   348	            // fall back to splitting model_id for legacy configs.
   349	            String method = iaConfig.optString("method", null);
   350	            String version = iaConfig.optString("version", null);
   351	            if (!Util.stringExists(method)) {
   352	                String[] mv = MLService.getMethodValues(iaConfig);
   353	                method = mv[0];
   354	                version = mv[1];
   355	            }
   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
   357	                new JSONObject(matchingSetQuery.toString()));
   358	            // i think this will never happen now, due to on-the-fly fix above; but leaving to be safe
   359	            if (matchQuery == null) {
   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +

 succeeded in 267ms:
   900	
   901	    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request)
   902	    throws org.datanucleus.api.rest.orgjson.JSONException {
   903	        return this.sanitizeMedia(request, false);
   904	    }
   905	
   906	    public boolean isPart() {
   907	        return ((this.iaClass != null) && this.iaClass.contains("+"));
   908	    }
   909	
   910	    public String getPartIfPresent() {
   911	        String thisPart = "";
   912	
   913	        if (this.iaClass != null && this.iaClass.contains("+")) {
   914	            String[] arr = this.iaClass.split("\\+");
   915	            thisPart = arr[arr.length - 1];
   916	        }
   917	        return thisPart;
   918	    }
   919	
   920	/*
   921	   both must be arrays which contain objects.
   922	   these will be "mixed into" the built default query. TODO this might cause some conflict or
   923	   overwriting that needs to be addressed in the future
   924	 */
   925	    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
   926	        boolean useClauses) {
   927	        Encounter enc = this.findEncounter(myShepherd);
   928	
   929	        if (enc == null) {
   930	            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
   931	                this);
   932	            return null;
   933	        }
   934	        JSONObject query = new JSONObject(
   935	            "{\"query\": {\"bool\": {\"filter\": [], \"must_not\": []} } }");
   936	        JSONObject wrapper = new JSONObject();
   937	        JSONObject arg = new JSONObject();
   938	        String txStr = enc.getTaxonomyString();
   939	        if (txStr != null) {
   940	            useClauses = true;
   941	            if (txStr.endsWith(" sp")) {
   942	                arg.put("encounterTaxonomy", txStr.substring(0, txStr.length() - 2) + "*");
   943	                wrapper.put("wildcard", arg);
   944	            } else {
   945	                arg.put("encounterTaxonomy", txStr);
   946	                wrapper.put("match", arg);
   947	            }
   948	            query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
   949	        } else if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   950	            "allowIdentificationWithoutTaxonomy"))) {
   951	            System.out.println(
   952	                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
   953	            return null;
   954	        }
   955	        // it seems like useClauses=false only ever was used when no taxonomy was present and basically
   956	        // returned every annotation with matchAgainst=T and an acmId
   957	        if (useClauses) {
   958	            if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   959	                "ignoreViewpointMatching", this.getTaxonomy(myShepherd)))) {
   960	                String[] viewpoints = this.getViewpointAndNeighbors();
   961	                if (viewpoints != null) {
   962	                    arg = new JSONObject();
   963	                    arg.put("viewpoint", new JSONArray(viewpoints));
   964	                    wrapper = new JSONObject();
   965	                    wrapper.put("terms", arg);
   966	                    // query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
   967	                    // to handle allowing null viewpoint, opensearch query gets messy!
   968	                    JSONArray should = new JSONArray(
   969	                        "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"viewpoint\"}}}}]");
   970	                    should.put(wrapper);
   971	                    JSONObject bool = new JSONObject("{\"bool\": {}}");
   972	                    bool.getJSONObject("bool").put("should", should);
   973	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   974	                        bool);
   975	                }
   976	            }
   977	            // this does either/or part/iaClass - unsure if this is correct
   978	            boolean usedPart = false;
   979	            if (Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   980	                "usePartsForIdentification"))) {
   981	                String part = this.getPartIfPresent();
   982	                if (!Util.stringIsEmptyOrNull(part)) {
   983	                    arg = new JSONObject();
   984	                    arg.put("iaClass", "*" + part);
   985	                    wrapper = new JSONObject();
   986	                    wrapper.put("wildcard", arg);
   987	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   988	                        wrapper);
   989	                    usedPart = true;
   990	                }
   991	            }
   992	            if (!usedPart && (this.getIAClass() != null)) {
   993	                arg = new JSONObject();
   994	                arg.put("iaClass", this.getIAClass());
   995	                wrapper = new JSONObject();
   996	                wrapper.put("match", arg);
   997	                query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   998	                    wrapper);
   999	            }
  1000	        }
  1001	        // matchAgainst true
  1002	        arg = new JSONObject();
  1003	        arg.put("matchAgainst", true);
  1004	        wrapper = new JSONObject();
  1005	        wrapper.put("term", arg);
  1006	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1007	
  1008	        // must have acmId
  1009	        arg = new JSONObject();
  1010	        arg.put("field", "acmId");

 succeeded in 357ms:
src/main/java/org/ecocean/Embedding.java:320:            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
src/main/java/org/ecocean/Embedding.java:322:            // getMatchingSetQuery can return null (e.g. encounter missing,
src/main/java/org/ecocean/Annotation.java:53:    protected String acmId;
src/main/java/org/ecocean/Annotation.java:72:    //           acmId IS NOT NULL ("already registered via the historical
src/main/java/org/ecocean/Annotation.java:178:        map.put("acmId", keywordType);
src/main/java/org/ecocean/Annotation.java:213:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/Annotation.java:268:    // TODO should this also be limited by matchAgainst and acmId?
src/main/java/org/ecocean/Annotation.java:313:        this.acmId = id;
src/main/java/org/ecocean/Annotation.java:318:        return this.acmId;
src/main/java/org/ecocean/Annotation.java:322:        return (this.acmId != null);
src/main/java/org/ecocean/Annotation.java:573:        if (acmId == null)
src/main/java/org/ecocean/Annotation.java:574:            throw new IOException(this + " does not have acmId set; cannot get viewpoint from IA");
src/main/java/org/ecocean/Annotation.java:576:            JSONObject resp = IBEISIA.iaViewpointFromAnnotUUID(acmId, context);
src/main/java/org/ecocean/Annotation.java:670:    // note that this does not assume they are the same MediaAsset
src/main/java/org/ecocean/Annotation.java:925:    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
src/main/java/org/ecocean/Annotation.java:930:            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
src/main/java/org/ecocean/Annotation.java:935:            "{\"query\": {\"bool\": {\"filter\": [], \"must_not\": []} } }");
src/main/java/org/ecocean/Annotation.java:952:                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
src/main/java/org/ecocean/Annotation.java:956:        // returned every annotation with matchAgainst=T and an acmId
src/main/java/org/ecocean/Annotation.java:969:                        "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"viewpoint\"}}}}]");
src/main/java/org/ecocean/Annotation.java:1008:        // must have acmId
src/main/java/org/ecocean/Annotation.java:1010:        arg.put("field", "acmId");
src/main/java/org/ecocean/Annotation.java:1012:        wrapper.put("exists", arg);
src/main/java/org/ecocean/Annotation.java:1015:        // exclude our encounter
src/main/java/org/ecocean/Annotation.java:1020:        query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(wrapper);
src/main/java/org/ecocean/Annotation.java:1027:            query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(
src/main/java/org/ecocean/Annotation.java:1059:                            "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"encounterLocationId\"}}}}]");
src/main/java/org/ecocean/Annotation.java:1072:                JSONArray uids = new JSONArray();
src/main/java/org/ecocean/Annotation.java:1078:                            uids.put(userId);
src/main/java/org/ecocean/Annotation.java:1080:                            uids.put(opt);
src/main/java/org/ecocean/Annotation.java:1084:                if (uids.length() > 0) {
src/main/java/org/ecocean/Annotation.java:1086:                    arg.put("encounterUserUuid", uids);
src/main/java/org/ecocean/Annotation.java:1114:           arr = additionalQuery.optJSONArray("must_not");
src/main/java/org/ecocean/Annotation.java:1120:                        "must_not").put(clause);
src/main/java/org/ecocean/Annotation.java:1126:        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
src/main/java/org/ecocean/Annotation.java:1127:        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
src/main/java/org/ecocean/Annotation.java:1142:        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
src/main/java/org/ecocean/Annotation.java:1188:                getMatchingSetQuery(myShepherd, taskParams, useClauses));
src/main/java/org/ecocean/Annotation.java:1266:        this is a string that uniquely identifies the matchingSet, dependent of content (e.g. cant be based on content uuids)
src/main/java/org/ecocean/Annotation.java:1325:    // look for "sibling" Annotations on same MediaAsset.  if one of them has an Encounter, we clone that.
src/main/java/org/ecocean/Annotation.java:1381:                // if these two intersect and have a different detected class they are allowed to reside on the same encounter
src/main/java/org/ecocean/Annotation.java:1890:            "\" && annotations.contains(annot1) && annot1.acmId == \"" + annot.getAcmId() +
src/main/java/org/ecocean/Annotation.java:1905:        if (this.acmId == null) return;
src/main/java/org/ecocean/Annotation.java:1906:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
src/main/java/org/ecocean/Annotation.java:1912:        Util.mark("Annotation.refreshLiteTaxonomy() refreshing " + this.acmId);
src/main/java/org/ecocean/Annotation.java:1913:        AnnotationLite.setCache(this.acmId, annl);
src/main/java/org/ecocean/Annotation.java:1917:        if (this.acmId == null) return;
src/main/java/org/ecocean/Annotation.java:1918:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
src/main/java/org/ecocean/Annotation.java:1924:        Util.mark("Annotation.refreshLiteIndividual() refreshing " + this.acmId);
src/main/java/org/ecocean/Annotation.java:1925:        AnnotationLite.setCache(this.acmId, annl);
src/main/java/org/ecocean/Annotation.java:1929:        if (this.acmId == null) return;
src/main/java/org/ecocean/Annotation.java:1930:        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
src/main/java/org/ecocean/Annotation.java:1936:        Util.mark("Annotation.refreshLiteValid() refreshing " + this.acmId);
src/main/java/org/ecocean/Annotation.java:1937:        AnnotationLite.setCache(this.acmId, annl);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1000,1130p'" in /mnt/c/Wildbook-clean2
 succeeded in 287ms:
  1000	        }
  1001	        // matchAgainst true
  1002	        arg = new JSONObject();
  1003	        arg.put("matchAgainst", true);
  1004	        wrapper = new JSONObject();
  1005	        wrapper.put("term", arg);
  1006	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1007	
  1008	        // must have acmId
  1009	        arg = new JSONObject();
  1010	        arg.put("field", "acmId");
  1011	        wrapper = new JSONObject();
  1012	        wrapper.put("exists", arg);
  1013	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1014	
  1015	        // exclude our encounter
  1016	        arg = new JSONObject();
  1017	        arg.put("encounterId", enc.getId());
  1018	        wrapper = new JSONObject();
  1019	        wrapper.put("match", arg);
  1020	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(wrapper);
  1021	
  1022	        // skip dead animals
  1023	        Long dateMS = enc.getDateInMillisecondsFallback();
  1024	        if (dateMS != null) {
  1025	            wrapper = new JSONObject(
  1026	                "{\"range\": {\"encounterIndividualTimeOfDeath\": { \"lte\": " + dateMS + " } } }");
  1027	            query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(
  1028	                wrapper);
  1029	        }
  1030	        // now process taskParams
  1031	        if (taskParams != null) {
  1032	            String userId = taskParams.optString("userId", null);
  1033	            JSONObject filt = taskParams.optJSONObject("matchingSetFilter");
  1034	            if (filt != null) {
  1035	                // locationId=FOO and locationIds=[FOO,BAR]
  1036	                boolean useNullLocation = false;
  1037	                List<String> rawLocationIds = new ArrayList<String>();
  1038	                String tmp = Util.basicSanitize(filt.optString("locationId", null));
  1039	                if (Util.stringExists(tmp)) rawLocationIds.add(tmp);
  1040	                JSONArray larr = filt.optJSONArray("locationIds");
  1041	                if (larr != null) {
  1042	                    for (int i = 0; i < larr.length(); i++) {
  1043	                        tmp = Util.basicSanitize(larr.optString(i));
  1044	                        if ("__NULL__".equals(tmp)) {
  1045	                            useNullLocation = true;
  1046	                        } else if (Util.stringExists(tmp) && !rawLocationIds.contains(tmp)) {
  1047	                            rawLocationIds.add(tmp);
  1048	                        }
  1049	                    }
  1050	                }
  1051	                List<String> expandedLocationIds = LocationID.expandIDs(rawLocationIds);
  1052	                if (expandedLocationIds.size() > 0) {
  1053	                    arg = new JSONObject();
  1054	                    arg.put("encounterLocationId", new JSONArray(expandedLocationIds));
  1055	                    wrapper = new JSONObject();
  1056	                    wrapper.put("terms", arg);
  1057	                    if (useNullLocation) {
  1058	                        JSONArray should = new JSONArray(
  1059	                            "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"encounterLocationId\"}}}}]");
  1060	                        should.put(wrapper);
  1061	                        JSONObject bool = new JSONObject("{\"bool\": {}}");
  1062	                        bool.getJSONObject("bool").put("should", should);
  1063	                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1064	                            "filter").put(bool);
  1065	                    } else {
  1066	                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1067	                            "filter").put(wrapper);
  1068	                    }
  1069	                }
  1070	                // owner ... which requires we have userId in the taskParams
  1071	                JSONArray owner = filt.optJSONArray("owner");
  1072	                JSONArray uids = new JSONArray();
  1073	                if ((owner != null) && (userId != null)) {
  1074	                    for (int i = 0; i < owner.length(); i++) {
  1075	                        String opt = owner.optString(i, null);
  1076	                        if (!Util.stringExists(opt)) continue;
  1077	                        if (opt.equals("me")) {
  1078	                            uids.put(userId);
  1079	                        } else {
  1080	                            uids.put(opt);
  1081	                        }
  1082	                    }
  1083	                }
  1084	                if (uids.length() > 0) {
  1085	                    arg = new JSONObject();
  1086	                    arg.put("encounterUserUuid", uids);
  1087	                    wrapper = new JSONObject();
  1088	                    wrapper.put("terms", arg);
  1089	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
  1090	                        wrapper);
  1091	                }
  1092	                // projectId
  1093	                String projectId = filt.optString("projectId", null);
  1094	                if (Util.stringExists(projectId)) {
  1095	                    arg = new JSONObject();
  1096	                    arg.put("encounterProjectIds", projectId);
  1097	                    wrapper = new JSONObject();
  1098	                    wrapper.put("match", arg);
  1099	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
  1100	                        wrapper);
  1101	                }
  1102	            }
  1103	        }
  1104	        /* saving this for possible future passing raw queries
  1105	           JSONArray arr = additionalQuery.optJSONArray("filter");
  1106	           if (arr != null) {
  1107	            for (int i = 0; i < arr.length(); i++) {
  1108	                JSONObject clause = arr.optJSONObject(i);
  1109	                if (clause != null)
  1110	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1111	                        "filter").put(clause);
  1112	            }
  1113	           }
  1114	           arr = additionalQuery.optJSONArray("must_not");
  1115	           if (arr != null) {
  1116	            for (int i = 0; i < arr.length(); i++) {
  1117	                JSONObject clause = arr.optJSONObject(i);
  1118	                if (clause != null)
  1119	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1120	                        "must_not").put(clause);
  1121	            }
  1122	           }
  1123	         */
  1124	
  1125	        // this exludes the very noisy embeddings from opensearch results since we dont need it
  1126	        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
  1127	        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
  1128	        return query;
  1129	    }
  1130	

codex
A concrete risk is emerging around the import expansion source. Bulk imports create initial annotations on encounters before ml-service detections, so I’m checking whether an import-wide `Encounter -> annotations` query would accidentally include non-indexed placeholder annotations and make the visibility wait time out every time.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '260,375p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "setSkipAutoIndexing|getSkipAutoIndexing|skipAutoIndexing|setMatchAgainst|matchAgainst|setAcmId|predictModelId|bboxKey|thetaKey|new Annotation" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/api/bulk/BulkImporter.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/Encounter.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Annotation.java | sed -n '300,380p'" in /mnt/c/Wildbook-clean2
 succeeded in 263ms:
   260	        } finally {
   261	            shep.rollbackAndClose();
   262	        }
   263	    }
   264	
   265	    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
   266	        Shepherd shep = new Shepherd(context);
   267	        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
   268	        try {
   269	            shep.beginDBTransaction();
   270	            MediaAsset ma = shep.getMediaAsset(maId);
   271	            Task task = Task.load(taskId, shep);
   272	            String staleReason = detectionStaleReason(ma, null, null);
   273	            if (staleReason != null) {
   274	                markTaskDroppedStale(shep, task, staleReason);
   275	                shep.commitDBTransaction();
   276	                return MlServiceJobOutcome.stale(staleReason);
   277	            }
   278	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   279	            markTaskCompleted(task);
   280	            shep.commitDBTransaction();
   281	            return MlServiceJobOutcome.okZeroDetections();
   282	        } catch (Exception ex) {
   283	            markTaskError(taskId, "PERSIST", "zero-detection finalize failed: " + ex.getMessage());
   284	            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
   285	        } finally {
   286	            shep.rollbackAndClose();
   287	        }
   288	    }
   289	
   290	    private PersistResult persistDetections(String maId, String encounterId, String taskId,
   291	        DetectionContext det, JSONArray results) {
   292	        Shepherd shep = new Shepherd(context);
   293	        shep.setAction(ACTION_PREFIX + "persistDetections");
   294	        List<String> annotationIds = new ArrayList<String>();
   295	
   296	        try {
   297	            FeatureType.initAll(shep);
   298	            shep.beginDBTransaction();
   299	            MediaAsset ma = shep.getMediaAsset(maId);
   300	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   301	            Task task = Task.load(taskId, shep);
   302	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   303	            if (staleReason != null) {
   304	                markTaskDroppedStale(shep, task, staleReason);
   305	                shep.commitDBTransaction();
   306	                return PersistResult.done(MlServiceJobOutcome.stale(staleReason));
   307	            }
   308	
   309	            for (int i = 0; i < results.length(); i++) {
   310	                JSONObject result = results.getJSONObject(i);
   311	                double[] bbox = parseBbox(result.getJSONArray("bbox"));
   312	                double theta = result.getDouble("theta");
   313	                String bboxKey = bboxKey(bbox);
   314	                String thetaKey = thetaKey(theta);
   315	                String predictModelId = result.optString("predict_model_id",
   316	                    det.mlConfig.optString("predict_model_id", null));
   317	                Annotation existing = findExistingAnnotation(ma, predictModelId, bboxKey,
   318	                    thetaKey);
   319	                if (existing != null) {
   320	                    annotationIds.add(existing.getId());
   321	                    continue;
   322	                }
   323	
   324	                JSONObject featureParams = featureParams(bbox, theta,
   325	                    result.optString("viewpoint", null));
   326	                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
   327	                String iaClass = result.optString("iaClass",
   328	                    result.optString("class_name", result.optString("class", null)));
   329	                Annotation ann = new Annotation(null, feature, iaClass);
   330	                ann.__setMediaAsset(ma);
   331	                ann.setAcmId(ann.getId());
   332	                ann.setMatchAgainst(true);
   333	                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   334	                ann.setPredictModelId(predictModelId);
   335	                ann.setBboxKey(bboxKey);
   336	                ann.setThetaKey(thetaKey);
   337	                ann.setWbiaRegistered(Boolean.FALSE);
   338	                ann.setWbiaRegisterAttempts(0);
   339	                ann.setViewpoint(result.optString("viewpoint", null));
   340	                ann.setQuality(optionalFiniteDouble(result, "score",
   341	                    optionalFiniteDouble(result, "confidence", null)));
   342	
   343	                // Bidirectional linkage:
   344	                //   MediaAsset.addFeature sets Feature.asset
   345	                //   Feature.setAnnotation sets the OWNING side of the
   346	                //     Annotation.features collection (which is mapped-by
   347	                //     "annotation"). Without explicitly setting this, the
   348	                //     ANNOTATION_FEATURES join row depends on DataNucleus
   349	                //     relationship management — fragile.
   350	                //   Annotation.addFeature puts the feature in the in-memory
   351	                //     list so reload returns it as expected.
   352	                ma.addFeature(feature);
   353	                feature.setAnnotation(ann);
   354	                ann.addFeature(feature);
   355	                if (enc != null) enc.addAnnotation(ann);
   356	                shep.getPM().makePersistent(feature);
   357	                shep.getPM().makePersistent(ann);
   358	
   359	                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
   360	                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
   361	                shep.getPM().makePersistent(emb);
   362	                annotationIds.add(ann.getId());
   363	            }
   364	
   365	            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
   366	            markTaskCompleted(task);
   367	            shep.commitDBTransaction();
   368	            return PersistResult.ok(annotationIds);
   369	        } catch (Exception ex) {
   370	            markTaskError(taskId, "PERSIST", "detection persist failed: " + ex.getMessage());
   371	            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
   372	                ex.getMessage()));
   373	        } finally {
   374	            shep.rollbackAndClose();
   375	        }

 succeeded in 274ms:
     1	package org.ecocean;
     2	
     3	import com.fasterxml.jackson.core.JsonGenerator;
     4	import com.fasterxml.jackson.core.JsonProcessingException;
     5	import java.awt.Rectangle;
     6	import java.io.File;
     7	import java.io.IOException;
     8	import java.util.*;
     9	import javax.jdo.Query;
    10	import javax.servlet.http.HttpServletRequest;
    11	import org.apache.commons.codec.digest.DigestUtils;
    12	import org.apache.commons.lang3.builder.ToStringBuilder;
    13	import org.ecocean.api.ApiException;
    14	import org.ecocean.ia.IA;
    15	import org.ecocean.ia.IAException;
    16	import org.ecocean.ia.MatchResult;
    17	import org.ecocean.ia.MatchResultProspect;
    18	import org.ecocean.ia.MLService;
    19	import org.ecocean.ia.Task;
    20	import org.ecocean.identity.IBEISIA;
    21	import org.ecocean.media.Feature;
    22	import org.ecocean.media.FeatureType;
    23	import org.ecocean.media.MediaAsset;
    24	import org.ecocean.media.MediaAssetFactory;
    25	import org.ecocean.shepherd.core.Shepherd;
    26	import org.json.JSONArray;
    27	import org.json.JSONObject;
    28	
    29	public class Annotation extends Base implements java.io.Serializable {
    30	    public Annotation() {}
    31	    private String id;
    32	    public static final int KNN_K_DISTANCE_VALUE = 4;
    33	    private static final String[][] VALID_VIEWPOINTS = new String[][] {
    34	        { "up", "up", "up", "up", "up", "up", "up", "up", }, {
    35	            "upfront", "upfrontright", "upright", "upbackright", "upback", "upbackleft", "upleft",
    36	                "upfrontleft"
    37	        }, { "front", "frontright", "right", "backright", "back", "backleft", "left", "frontleft" },
    38	                {
    39	            "downfront", "downfrontright", "downright", "downbackright", "downback", "downbackleft",
    40	                "downleft", "downfrontleft"
    41	        }, { "down", "down", "down", "down", "down", "down", "down", "down" }
    42	    };
    43	    private String species;
    44	
    45	    private String iaClass;
    46	
    47	    private String name;
    48	    private boolean isExemplar = false;
    49	    private Boolean isOfInterest = null; // aka AoI (Annotation of Interest)
    50	    protected String identificationStatus;
    51	    private ArrayList<Feature> features;
    52	    private Set<Embedding> embeddings;
    53	    protected String acmId;
    54	
    55	    // ----- ml-service migration v2: idempotency + WBIA registration -----
    56	    // (commit #4 of the v2 plan)
    57	    //
    58	    // predictModelId / bboxKey / thetaKey form the composite that uniquely
    59	    // identifies an ml-service-created detection. The partial unique index
    60	    // in archive/sql/ml_service_idempotency.sql guards against concurrent
    61	    // retry creating duplicates. Null on legacy WBIA-era rows and on
    62	    // manually-drawn annotations.
    63	    protected String predictModelId;
    64	    protected String bboxKey;   // literal "x:y:w:h" of rounded ints
    65	    protected String thetaKey;  // theta rounded to 4 decimals, as String
    66	
    67	    // wbiaRegistered drives the DB-backed background poller that tells WBIA
    68	    // about ml-service-created annotations so HotSpotter remains available.
    69	    //
    70	    //   null  — legacy annotation (column is new; starts null on existing
    71	    //           rows). The DDL migration sets nulls to TRUE wherever
    72	    //           acmId IS NOT NULL ("already registered via the historical
    73	    //           IBEISIA flow"). Excluded from polling.
    74	    //   false — new ml-service annotation awaiting WBIA registration.
    75	    //           Polling thread picks these up.
    76	    //   true  — WBIA acknowledged. Terminal success.
    77	    //
    78	    // Contract: MlServiceProcessor MUST set this to false (not null) on
    79	    // new ml-service annotations.
    80	    protected Boolean wbiaRegistered;
    81	
    82	    // Failed-attempt counter. Polling filters wbiaRegisterAttempts < MAX so
    83	    // chronically-failing rows park rather than spin forever.
    84	    protected int wbiaRegisterAttempts = 0;
    85	
    86	    // this is used to decide "should we match against this"  problem is: that is not very (IA-)algorithm agnostic
    87	    // TODO: was this made obsolete by ACM and friends?
    88	    private boolean matchAgainst = false;
    89	
    90	    // TODO: can these (thru mediaAsset) be removed now that there Features?
    91	    private int x;
    92	    private int y;
    93	    private int width;
    94	    private int height;
    95	    private float[] transformMatrix;
    96	    private double theta;
    97	    private long version = System.currentTimeMillis();
    98	
    99	    // quality indicates the fidelity of the annotation, e.g. the overall image quality of a picture.
   100	    // This is useful e.g. for researchers who want to account for a bias where "better" images are
   101	    // more likely to produce matches.
   102	    private Double quality;
   103	    // distinctiveness indicates the real-wold distinctiveness of the feature *being recorded*, independent
   104	    // of the recording medium. Useful e.g. for researchers who want to account for a bias where more distinct
   105	    // animals like one with a large scar are easier to re-sight (match).
   106	    private Double distinctiveness;
   107	    private String viewpoint;
   108	    // *'annot_yaw': 'REAL',
   109	    // ~'annot_detect_confidence': 'REAL',
   110	    // ~'annot_exemplar_flag': 'INTEGER',
   111	    // ~'annot_note': 'TEXT',
   112	    // ~'annot_visual_uuid': 'UUID',
   113	    // ~'annot_semantic_uuid': 'UUID',
   114	    // *'annot_quality': 'INTEGER',
   115	    // ~'annot_tags': 'TEXT',
   116	
   117	    private MediaAsset mediaAsset = null;
   118	    // end of what will go away
   119	
   120	    // the "trivial" Annotation - will have a single feature which references the total MediaAsset

 succeeded in 291ms:
   300	                params.put("transformMatrix", getTransformMatrix());
   301	            } else {
   302	                params.put("x", getX());
   303	                params.put("y", getY());
   304	            }
   305	            f = new Feature("org.ecocean.boundingBox", params);
   306	        }
   307	        __getMediaAsset().addFeature(f);
   308	        addFeature(f);
   309	        return f;
   310	    }
   311	
   312	    public void setAcmId(String id) {
   313	        this.acmId = id;
   314	        this.setVersion();
   315	    }
   316	
   317	    public String getAcmId() {
   318	        return this.acmId;
   319	    }
   320	
   321	    public boolean hasAcmId() {
   322	        return (this.acmId != null);
   323	    }
   324	
   325	    // ----- ml-service migration v2 idempotency / WBIA-registration accessors -----
   326	
   327	    public String getPredictModelId() { return predictModelId; }
   328	    public void setPredictModelId(String s) { this.predictModelId = s; this.setVersion(); }
   329	
   330	    public String getBboxKey() { return bboxKey; }
   331	    public void setBboxKey(String s) { this.bboxKey = s; this.setVersion(); }
   332	
   333	    public String getThetaKey() { return thetaKey; }
   334	    public void setThetaKey(String s) { this.thetaKey = s; this.setVersion(); }
   335	
   336	    public Boolean getWbiaRegistered() { return wbiaRegistered; }
   337	    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
   338	
   339	    // Convenience: hides the tri-state from frontend JSON. Returns true only
   340	    // when the column is explicitly TRUE.
   341	    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
   342	
   343	    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
   344	    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
   345	    public void incrementWbiaRegisterAttempts() {
   346	        this.wbiaRegisterAttempts++;
   347	        this.setVersion();
   348	    }
   349	
   350	    public ArrayList<Feature> getFeatures() {
   351	        return features;
   352	    }
   353	
   354	    public Feature getFeature() {
   355	        if (Util.collectionSize(features) < 1) return null;
   356	        return features.get(0);
   357	    }
   358	
   359	    public void setFeatures(ArrayList<Feature> f) {
   360	        features = f;
   361	        this.setVersion();
   362	    }
   363	
   364	    public void addFeature(Feature f) {
   365	        if (features == null) features = new ArrayList<Feature>();
   366	        if (!features.contains(f)) features.add(f);
   367	        this.setVersion();
   368	    }
   369	
   370	    public String getId() {
   371	        return id;
   372	    }
   373	
   374	    public void setId(String id) {
   375	        this.id = id;
   376	        this.setVersion();
   377	    }
   378	
   379	    public Double getQuality() {
   380	        return quality;

 succeeded in 348ms:
src/main/java/org/ecocean/Annotation.java:58:    // predictModelId / bboxKey / thetaKey form the composite that uniquely
src/main/java/org/ecocean/Annotation.java:63:    protected String predictModelId;
src/main/java/org/ecocean/Annotation.java:64:    protected String bboxKey;   // literal "x:y:w:h" of rounded ints
src/main/java/org/ecocean/Annotation.java:65:    protected String thetaKey;  // theta rounded to 4 decimals, as String
src/main/java/org/ecocean/Annotation.java:88:    private boolean matchAgainst = false;
src/main/java/org/ecocean/Annotation.java:216:        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
src/main/java/org/ecocean/Annotation.java:268:    // TODO should this also be limited by matchAgainst and acmId?
src/main/java/org/ecocean/Annotation.java:312:    public void setAcmId(String id) {
src/main/java/org/ecocean/Annotation.java:327:    public String getPredictModelId() { return predictModelId; }
src/main/java/org/ecocean/Annotation.java:328:    public void setPredictModelId(String s) { this.predictModelId = s; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:330:    public String getBboxKey() { return bboxKey; }
src/main/java/org/ecocean/Annotation.java:331:    public void setBboxKey(String s) { this.bboxKey = s; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:333:    public String getThetaKey() { return thetaKey; }
src/main/java/org/ecocean/Annotation.java:334:    public void setThetaKey(String s) { this.thetaKey = s; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:602:     * partial unique index on (mediaAsset, predictModelId, bboxKey, thetaKey)
src/main/java/org/ecocean/Annotation.java:751:        return matchAgainst;
src/main/java/org/ecocean/Annotation.java:754:    public void setMatchAgainst(boolean b) {
src/main/java/org/ecocean/Annotation.java:755:        matchAgainst = b;
src/main/java/org/ecocean/Annotation.java:956:        // returned every annotation with matchAgainst=T and an acmId
src/main/java/org/ecocean/Annotation.java:1001:        // matchAgainst true
src/main/java/org/ecocean/Annotation.java:1003:        arg.put("matchAgainst", true);
src/main/java/org/ecocean/Annotation.java:1364:                ann.setMatchAgainst(false);
src/main/java/org/ecocean/Annotation.java:1445:        Annotation triv = new Annotation(this.species, ma); // not going to set IAClass or anything since starting fresh
src/main/java/org/ecocean/Annotation.java:1447:        this.setMatchAgainst(false);
src/main/java/org/ecocean/Annotation.java:1455:    // creates a new Annotation with the basic properties duplicated (but no "linked" objects, like Features etc)
src/main/java/org/ecocean/Annotation.java:1457:        Annotation ann = new Annotation();
src/main/java/org/ecocean/Annotation.java:1536:        Annotation ann = new Annotation(null, ft, iaClass);
src/main/java/org/ecocean/Annotation.java:1546:                ann.setMatchAgainst(true);
src/main/java/org/ecocean/Annotation.java:1552:            ann.setMatchAgainst(true);
src/main/java/org/ecocean/Annotation.java:1628:                        "\">Encounter cloned and <i>new Annotation</i> manually added by " +
src/main/java/org/ecocean/Annotation.java:1651:                    "\"><i>new Annotation</i> manually added by " + user.getDisplayName() + "</p>");
src/main/java/org/ecocean/Annotation.java:1908:            annl = new AnnotationLite(null, tax); // indiv = null here, but it is new so its what we got. :/
src/main/java/org/ecocean/Annotation.java:1920:            annl = new AnnotationLite(indiv);
src/main/java/org/ecocean/Annotation.java:1932:            annl = new AnnotationLite(validForId);
src/main/java/org/ecocean/Encounter.java:1352:            annotations.add(new Annotation(getTaxonomyString(), ma));
src/main/java/org/ecocean/Encounter.java:2907:                ann.setMatchAgainst(use);
src/main/java/org/ecocean/Encounter.java:3089:        Annotation ann = new Annotation(getTaxonomyString(), ma);
src/main/java/org/ecocean/Encounter.java:5030:        this.setSkipAutoIndexing(true);
src/main/java/org/ecocean/Encounter.java:5057:                occ.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5062:                indiv.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5070:        this.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5230:                goneAnnot.setSkipAutoIndexing(true);
src/main/java/org/ecocean/Encounter.java:5264:                    current.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5383:        current.setSkipAutoIndexing(true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:313:                String bboxKey = bboxKey(bbox);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:314:                String thetaKey = thetaKey(theta);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:315:                String predictModelId = result.optString("predict_model_id",
src/main/java/org/ecocean/ia/MlServiceProcessor.java:317:                Annotation existing = findExistingAnnotation(ma, predictModelId, bboxKey,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:318:                    thetaKey);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:329:                Annotation ann = new Annotation(null, feature, iaClass);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:331:                ann.setAcmId(ann.getId());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:332:                ann.setMatchAgainst(true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:334:                ann.setPredictModelId(predictModelId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:335:                ann.setBboxKey(bboxKey);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:336:                ann.setThetaKey(thetaKey);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:517:    static String bboxKey(double[] bbox) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:523:    static String thetaKey(double theta) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:527:    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:528:        String bboxKey, String thetaKey) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:532:            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:533:            if (!sameString(bboxKey, ann.getBboxKey())) continue;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:534:            if (!sameString(thetaKey, ann.getThetaKey())) continue;
src/main/java/org/ecocean/media/MediaAsset.java:132:    public void setAcmId(String id) {
src/main/java/org/ecocean/media/MediaAsset.java:661:        Collections.sort(ord, new AnnotationPositionalComparator());
src/main/java/org/ecocean/media/MediaAsset.java:1199:                    ma.setSkipAutoIndexing(true);
src/main/java/org/ecocean/media/MediaAsset.java:1629:                    tann.setMatchAgainst(false);
src/main/java/org/ecocean/media/MediaAsset.java:1663:                    tann.setMatchAgainst(false);
src/main/java/org/ecocean/media/MediaAsset.java:1763:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
src/main/java/org/ecocean/api/bulk/BulkImporter.java:111:            ma.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:116:            // see note on MediaAsset.getSkipAutoIndexing()
src/main/java/org/ecocean/api/bulk/BulkImporter.java:703:            Annotation ann = new Annotation(tx, ma);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:705:            ann.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:903:        indiv.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:940:        enc.setSkipAutoIndexing(true);
src/main/java/org/ecocean/api/bulk/BulkImporter.java:1014:        occ.setSkipAutoIndexing(true);

exec
/bin/bash -lc 'rg -n "class Base|skipAutoIndexing|setSkipAutoIndexing|getSkipAutoIndexing" src/main/java/org/ecocean/Base.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/IndexingManager.java src/main/java/org/ecocean/servlet/importer/ImportTask.java' in /mnt/c/Wildbook-clean2
 succeeded in 374ms:
src/main/java/org/ecocean/Base.java:31:    BaseDeserializer.class) public abstract class Base {
src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
src/main/java/org/ecocean/Base.java:102:    public void setSkipAutoIndexing(boolean skip) {
src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
src/main/java/org/ecocean/Base.java:106:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
src/main/java/org/ecocean/media/MediaAsset.java:1199:                    ma.setSkipAutoIndexing(true);
src/main/java/org/ecocean/media/MediaAsset.java:1763:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
src/main/java/org/ecocean/Encounter.java:5030:        this.setSkipAutoIndexing(true);
src/main/java/org/ecocean/Encounter.java:5057:                occ.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5062:                indiv.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5070:        this.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5230:                goneAnnot.setSkipAutoIndexing(true);
src/main/java/org/ecocean/Encounter.java:5264:                    current.setSkipAutoIndexing(false);
src/main/java/org/ecocean/Encounter.java:5383:        current.setSkipAutoIndexing(true);

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Base.java | sed -n '90,112p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "skipAutoIndexing|getSkipAutoIndexing|dirty|enqueue|IndexingManager|makeDirty|dirtyEntities|Base" src/main/java/org/ecocean src/main/java/org/ecocean/shepherd src/main/java/org/ecocean/media | head -200' in /mnt/c/Wildbook-clean2
 succeeded in 242ms:
    90	    public boolean canUserView(User user, Shepherd myShepherd) {
    91	        return false;
    92	    }
    93	
    94	    public boolean canUserEdit(User user, Shepherd myShepherd) {
    95	        return false;
    96	    }
    97	
    98	    // this allows us to delay indexing during heavy activity which triggers auto-indexing
    99	    // via lifecycle persisting triggers e.g. during bulk import
   100	    public boolean skipAutoIndexing = false;
   101	
   102	    public void setSkipAutoIndexing(boolean skip) {
   103	        skipAutoIndexing = skip;
   104	    }
   105	
   106	    public boolean getSkipAutoIndexing() {
   107	        return skipAutoIndexing;
   108	    }
   109	
   110	    // this is not persisted, but is a place to keep score when from search query results
   111	    public double opensearchScore = 0.0d;
   112	

 succeeded in 522ms:
src/main/java/org/ecocean/EmailTemplate.java:331:    public void setBaseState() {
src/main/java/org/ecocean/EmailTemplate.java:332:        subj.setBaseState();
src/main/java/org/ecocean/EmailTemplate.java:333:        plainBody.setBaseState();
src/main/java/org/ecocean/EmailTemplate.java:335:            htmlBody.setBaseState();
src/main/java/org/ecocean/EmailTemplate.java:873:        subj.setBaseState();
src/main/java/org/ecocean/EmailTemplate.java:887:        plainBody.setBaseState();
src/main/java/org/ecocean/EmailTemplate.java:909:            htmlBody.setBaseState();
src/main/java/org/ecocean/BaseDeserializer.java:11:public class BaseDeserializer extends StdDeserializer<Base> {
src/main/java/org/ecocean/BaseDeserializer.java:12:    public BaseDeserializer() {
src/main/java/org/ecocean/BaseDeserializer.java:16:    public BaseDeserializer(Class<Base> t) {
src/main/java/org/ecocean/Annotation.java:29:public class Annotation extends Base implements java.io.Serializable {
src/main/java/org/ecocean/Annotation.java:175:        // "id" is done in Base
src/main/java/org/ecocean/Annotation.java:274:    @Override public Base getById(Shepherd myShepherd, String id) {
src/main/java/org/ecocean/Annotation.java:278:    // comment cruft only needed for Base class
src/main/java/org/ecocean/Annotation.java:1467:    public static Base createFromApi(JSONObject payload, List<File> files, Shepherd myShepherd)
src/main/java/org/ecocean/Base.java:26: * Base class for other classes such as Encounter.java, Occurrence.java, and MarkedIndividual.java
src/main/java/org/ecocean/Base.java:30:@JsonSerialize(using = BaseSerializer.class) @JsonDeserialize(using =
src/main/java/org/ecocean/Base.java:31:    BaseDeserializer.class) public abstract class Base {
src/main/java/org/ecocean/Base.java:89:    // these should/must be overridden. they are used for generic access control, like in api/ApiBase.java
src/main/java/org/ecocean/Base.java:100:    public boolean skipAutoIndexing = false;
src/main/java/org/ecocean/Base.java:103:        skipAutoIndexing = skip;
src/main/java/org/ecocean/Base.java:106:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/Base.java:107:        return skipAutoIndexing;
src/main/java/org/ecocean/Base.java:238:        myShepherd.setAction("BaseSerializer");
src/main/java/org/ecocean/Base.java:256:    // this is so we can call it on Base obj, but really is only needed by [overridden by] Encounter (currently)
src/main/java/org/ecocean/Base.java:319:    public abstract Base getById(Shepherd myShepherd, String id);
src/main/java/org/ecocean/Base.java:324:    public static Base getByClassnameAndId(Shepherd myShepherd, String className, String id) {
src/main/java/org/ecocean/Base.java:326:        Base tmp = null;
src/main/java/org/ecocean/Base.java:351:        Base baseObj = (Base)tmpObj;
src/main/java/org/ecocean/Base.java:354:            System.out.println("Base.opensearchSyncIndex(" + indexName +
src/main/java/org/ecocean/Base.java:369:        System.out.println("Base.opensearchSyncIndex(" + indexName + "): stopAfter=" + stopAfter +
src/main/java/org/ecocean/Base.java:373:            Base obj = baseObj.getById(myShepherd, id);
src/main/java/org/ecocean/Base.java:377:                System.out.println("Base.opensearchSyncIndex(" + indexName + "): index failed " +
src/main/java/org/ecocean/Base.java:382:                System.out.println("Base.opensearchSyncIndex(" + indexName + ") needIndexing: " +
src/main/java/org/ecocean/Base.java:386:                System.out.println("Base.opensearchSyncIndex(" + indexName +
src/main/java/org/ecocean/Base.java:391:        System.out.println("Base.opensearchSyncIndex(" + indexName + ") finished needIndexing");
src/main/java/org/ecocean/Base.java:396:                System.out.println("Base.opensearchSyncIndex(" + indexName + ") needRemoval: " +
src/main/java/org/ecocean/Base.java:400:        System.out.println("Base.opensearchSyncIndex(" + indexName + ") finished needRemoval");
src/main/java/org/ecocean/Base.java:405:    public static Base createFromApi(JSONObject payload, List<File> files, Shepherd myShepherd)
src/main/java/org/ecocean/Base.java:442:        and Base.createFromApi() .... this is really ugly and unfortunate. in some wonderful future
src/main/java/org/ecocean/Base.java:467:        if (!(u2 instanceof Base)) return false;
src/main/java/org/ecocean/Base.java:468:        Base two = (Base)u2;
src/main/java/org/ecocean/media/LocalAssetStore.java:45:     * @param webRoot Base web url under which asset paths are appended.  If null, this store offers no web access to assets.
src/main/java/org/ecocean/media/LocalAssetStore.java:59:     * @param webRoot Base web url under which asset paths are appended.  If null, this store offers no web access to assets.
src/main/java/org/ecocean/WildbookLifecycleListener.java:6:import org.ecocean.Base;
src/main/java/org/ecocean/WildbookLifecycleListener.java:27:        if (Base.class.isInstance(obj)) {
src/main/java/org/ecocean/WildbookLifecycleListener.java:28:            Base base = (Base)obj;
src/main/java/org/ecocean/WildbookLifecycleListener.java:30:                if (base.getSkipAutoIndexing()) return;
src/main/java/org/ecocean/WildbookLifecycleListener.java:43:        if (OpenSearch.skipAutoIndexing()) {
src/main/java/org/ecocean/WildbookLifecycleListener.java:44:            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
src/main/java/org/ecocean/WildbookLifecycleListener.java:53:        if (Base.class.isInstance(obj)) {
src/main/java/org/ecocean/WildbookLifecycleListener.java:54:            Base base = (Base)obj;
src/main/java/org/ecocean/WildbookLifecycleListener.java:55:            if (base.getSkipAutoIndexing()) return;
src/main/java/org/ecocean/WildbookLifecycleListener.java:60:                IndexingManager im = IndexingManagerFactory.getIndexingManager();
src/main/java/org/ecocean/api/UserInfo.java:14:public class UserInfo extends ApiBase {
src/main/java/org/ecocean/Util.java:1293:        File ddir = astore.getBasePath().toFile();
src/main/java/org/ecocean/media/AssetStore.java:135:    public Path getBasePath() {
src/main/java/org/ecocean/media/AssetStore.java:426:     * Like above, but pass in MediaAsset and copy file to AssetStore accordingly (does the real dirty work for above).
src/main/java/org/ecocean/api/UserHome.java:24:public class UserHome extends ApiBase {
src/main/java/org/ecocean/media/MediaAsset.java:12:import java.util.Base64;
src/main/java/org/ecocean/media/MediaAsset.java:26:import org.ecocean.Base;
src/main/java/org/ecocean/media/MediaAsset.java:48:public class MediaAsset extends Base implements java.io.Serializable {
src/main/java/org/ecocean/media/MediaAsset.java:1475:    public void copyInBase64(String b64)
src/main/java/org/ecocean/media/MediaAsset.java:1477:        if (b64 == null) throw new IOException("copyInBase64() null string");
src/main/java/org/ecocean/media/MediaAsset.java:1480:            imgBytes = Base64.getDecoder().decode(b64);
src/main/java/org/ecocean/media/MediaAsset.java:1482:            throw new IOException("copyInBase64() could not parse: " + ex.toString());
src/main/java/org/ecocean/media/MediaAsset.java:1501:            throw new IOException("copyInBase64() could not write " + file);
src/main/java/org/ecocean/media/MediaAsset.java:1711:        // "id" is done in Base
src/main/java/org/ecocean/media/MediaAsset.java:1758:    @Override public Base getById(Shepherd myShepherd, String id) {
src/main/java/org/ecocean/media/MediaAsset.java:1762:    // we override Base version, as we want to (dont we?) always skip auto-indexing children assets
src/main/java/org/ecocean/media/MediaAsset.java:1763:    public boolean getSkipAutoIndexing() {
src/main/java/org/ecocean/media/MediaAsset.java:1764:        if (this.skipAutoIndexing) return true;
src/main/java/org/ecocean/media/MediaAsset.java:1773:    // comment cruft only needed for Base class
src/main/java/org/ecocean/BaseSerializer.java:10:public class BaseSerializer extends StdSerializer<Base> {
src/main/java/org/ecocean/BaseSerializer.java:11:    public BaseSerializer() {
src/main/java/org/ecocean/BaseSerializer.java:15:    public BaseSerializer(Class<Base> t) {
src/main/java/org/ecocean/BaseSerializer.java:19:    @Override public void serialize(Base obj, JsonGenerator jgen, SerializerProvider provider)
src/main/java/org/ecocean/api/GenericObject.java:25:// note: this is for use on any non-Base object
src/main/java/org/ecocean/api/GenericObject.java:26:// see api/BaseObject if object extends Base.java
src/main/java/org/ecocean/api/GenericObject.java:27:public class GenericObject extends ApiBase {
src/main/java/org/ecocean/api/UploadedFiles.java:112:                System.out.println("BaseObject.makeMediaAssets() failed isValidImage() on " + file);
src/main/java/org/ecocean/api/UploadedFiles.java:129:                System.out.println("BaseObject.makeMediaAssets() failed on " + file + ": " + ioe);
src/main/java/org/ecocean/api/EncounterExport.java:25:public class EncounterExport extends ApiBase {
src/main/java/org/ecocean/api/SiteSettings.java:41:public class SiteSettings extends ApiBase {
src/main/java/org/ecocean/api/BulkImport.java:34:public class BulkImport extends ApiBase {
src/main/java/org/ecocean/api/BulkImport.java:822:                IA.getBaseURL(context));
src/main/java/org/ecocean/api/SearchApi.java:17:public class SearchApi extends ApiBase {
src/main/java/org/ecocean/ia/IA.java:154:        String baseUrl = getBaseURL(context);
src/main/java/org/ecocean/ia/IA.java:226:     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
src/main/java/org/ecocean/ia/IA.java:246:            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
src/main/java/org/ecocean/ia/IA.java:256:     * Build and enqueue one v2 ml-service job for a single MediaAsset.
src/main/java/org/ecocean/ia/IA.java:267:     * is persisted before this method enqueues. On enqueue failure the
src/main/java/org/ecocean/ia/IA.java:281:    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
src/main/java/org/ecocean/ia/IA.java:316:            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
src/main/java/org/ecocean/ia/IA.java:467:            String baseUrl = getBaseURL(context);
src/main/java/org/ecocean/ia/IA.java:634:    public static String getBaseURL(String context) {
src/main/java/org/ecocean/ia/IA.java:640:            System.out.println("INFO: Wildbook is containerized: Server getBaseURL is returning: " +
src/main/java/org/ecocean/ia/IA.java:644:        System.out.println("INFO: Server getBaseURL is returning " + url);
src/main/java/org/ecocean/TemplateFiller.java:183:    public void setBaseState() {
src/main/java/org/ecocean/Encounter.java:60:public class Encounter extends Base implements java.io.Serializable {
src/main/java/org/ecocean/Encounter.java:1918:    // ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/Encounter.java:1924:    // ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Encounter.java:1930:    // ##DEPRECATED #509 - Base class getId() method
src/main/java/org/ecocean/Encounter.java:1936:    // ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Encounter.java:3132:    // down-n-dirty with no myShepherd passed!  :/
src/main/java/org/ecocean/Encounter.java:3836:        // System.out.println("Adding Observation in Base Class... : "+obs.toString());
src/main/java/org/ecocean/Encounter.java:4249:        return Base.opensearchQuery("encounter", query, numFrom, pageSize, sort, sortOrder);
src/main/java/org/ecocean/Encounter.java:4705:    @Override public Base getById(Shepherd myShepherd, String id) {
src/main/java/org/ecocean/Encounter.java:4733:        // note: "id" is done in Base.java
src/main/java/org/ecocean/Encounter.java:4941:    public static Base createFromApi(org.json.JSONObject payload, List<File> files,
src/main/java/org/ecocean/Encounter.java:5022:    // user should already have been validated -- via obj.canUserEdit() -- in api/BaseObject, so this
src/main/java/org/ecocean/Encounter.java:5075:    // Base.applyPatchOp()
src/main/java/org/ecocean/Encounter.java:5334:        List<Base> needsIndexing = new ArrayList<Base>();
src/main/java/org/ecocean/api/Projects.java:15:public class Projects extends ApiBase {
src/main/java/org/ecocean/ia/MatchResult.java:365:            ma.copyInBase64(b64);
src/main/java/org/ecocean/ia/MlServiceJobOutcome.java:12: * appropriately (e.g. enqueue a deferred match when {@code OK}).
src/main/java/org/ecocean/ia/MlServiceProcessor.java:423:                enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            enqueueDeferredMatch(annotationIds, taskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:681:    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
src/main/java/org/ecocean/StartupWildbook.java:214:        // stuck past a threshold and re-enqueue them.
src/main/java/org/ecocean/StartupWildbook.java:535:     * at-most-once FileQueue semantics) and re-enqueues them through
src/main/java/org/ecocean/StartupWildbook.java:549:     *       successful re-enqueue, because doing so from the stale
src/main/java/org/ecocean/StartupWildbook.java:551:     *       a fast queue consumer between enqueue and commit. REVISION
src/main/java/org/ecocean/StartupWildbook.java:554:     *       happens between enqueue and consumer pickup can re-enqueue
src/main/java/org/ecocean/StartupWildbook.java:642:                // Flip to error so the operator sees it; don't re-enqueue.
src/main/java/org/ecocean/StartupWildbook.java:649:            // Call the per-asset enqueue helper directly. This bypasses
src/main/java/org/ecocean/StartupWildbook.java:657:            //   - enqueue succeeded: the child Task + queue file are durable;
src/main/java/org/ecocean/StartupWildbook.java:658:            //   - enqueue failed: the child Task IS still persisted (orphan,
src/main/java/org/ecocean/StartupWildbook.java:661:            boolean enqueued = org.ecocean.ia.IA.enqueueOneAssetForMlService(
src/main/java/org/ecocean/StartupWildbook.java:663:            if (!enqueued) {
src/main/java/org/ecocean/StartupWildbook.java:664:                System.out.println("WARN: StaleMlServiceReconciliation: enqueue FAILED for " +
src/main/java/org/ecocean/StartupWildbook.java:669:            // No status update after a successful enqueue. The queued
src/main/java/org/ecocean/StartupWildbook.java:681:            System.out.println("[INFO] StaleMlServiceReconciliation: re-enqueued " + maId);
src/main/java/org/ecocean/StartupWildbook.java:728:        // contextDestroyed's cleanup (Shepherd / IndexingManager / etc.).
src/main/java/org/ecocean/StartupWildbook.java:870:        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in
src/main/java/org/ecocean/StartupWildbook.java:880:        IndexingManagerFactory.getIndexingManager().shutdown();
src/main/java/org/ecocean/OpenSearch.java:139:    public static boolean skipAutoIndexing() {
src/main/java/org/ecocean/OpenSearch.java:140:        return new java.io.File("/tmp/skipAutoIndexing").exists();
src/main/java/org/ecocean/OpenSearch.java:197:            Base.opensearchSyncIndex(myShepherd, Encounter.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:198:            Base.opensearchSyncIndex(myShepherd, Annotation.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:199:            Base.opensearchSyncIndex(myShepherd, MarkedIndividual.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:200:            Base.opensearchSyncIndex(myShepherd, Occurrence.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:201:            Base.opensearchSyncIndex(myShepherd, MediaAsset.class, BACKGROUND_SLICE_SIZE);
src/main/java/org/ecocean/OpenSearch.java:267:    public void index(String indexName, Base obj)
src/main/java/org/ecocean/OpenSearch.java:274:        IndexRequest<Base> indexRequest = new IndexRequest.Builder<Base>()
src/main/java/org/ecocean/OpenSearch.java:310:    public List<Base> queryx(String indexName, String query)
src/main/java/org/ecocean/OpenSearch.java:312:        List<Base> results = new ArrayList<Base>();
src/main/java/org/ecocean/OpenSearch.java:328:        // SearchResponse<Base> searchResponse = client.search(request, Base.class);
src/main/java/org/ecocean/OpenSearch.java:329:        SearchResponse<Base> searchResponse = client.search(s -> s.index(indexName), Base.class);
src/main/java/org/ecocean/OpenSearch.java:466:    // queue drain — IndexingManager may still have unindexed entities queued.
src/main/java/org/ecocean/OpenSearch.java:486:    //   - WARNs if /tmp/skipAutoIndexing is set, since that flag will make
src/main/java/org/ecocean/OpenSearch.java:492:    // timeout. Caller decides what to do on false (e.g. enqueue a deferred-
src/main/java/org/ecocean/OpenSearch.java:495:    // Does NOT try to drain the Wildbook IndexingManager queue. That queue
src/main/java/org/ecocean/OpenSearch.java:513:        if (skipAutoIndexing()) {
src/main/java/org/ecocean/OpenSearch.java:515:                "WARN: OpenSearch.waitForVisibility called with /tmp/skipAutoIndexing set " +
src/main/java/org/ecocean/OpenSearch.java:686:    public List<Base> queryResultsToObjects(Shepherd myShepherd, String indexName,
src/main/java/org/ecocean/OpenSearch.java:697:        List<Base> list = new ArrayList<Base>();
src/main/java/org/ecocean/OpenSearch.java:699:            Base obj = null;
src/main/java/org/ecocean/OpenSearch.java:749:    public void delete(String indexName, Base obj)
src/main/java/org/ecocean/OpenSearch.java:952:    public void indexAll(Shepherd myShepherd, Base obj)
src/main/java/org/ecocean/OpenSearch.java:971:        List<Base> all = null;
src/main/java/org/ecocean/OpenSearch.java:986:        for (Base item : all) {
src/main/java/org/ecocean/api/MarkedIndividualInfo.java:22:public class MarkedIndividualInfo extends ApiBase {
src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:18:import org.ecocean.Base;
src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:246:    public static void bulkOpensearchIndex(final List<Base> objs) {
src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:257:                for (Base obj : objs) {
src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:264:                        Base base = (Base)bgShepherd.getPM().getObjectById(myClass, id);
src/main/java/org/ecocean/api/Logout.java:19:public class Logout extends ApiBase {
src/main/java/org/ecocean/api/bulk/BulkImporter.java:19:import org.ecocean.Base;
src/main/java/org/ecocean/api/bulk/BulkImporter.java:73:        List<Base> needIndexing = new ArrayList<Base>();
src/main/java/org/ecocean/api/bulk/BulkImporter.java:116:            // see note on MediaAsset.getSkipAutoIndexing()
src/main/java/org/ecocean/api/Login.java:31:public class Login extends ApiBase {
src/main/java/org/ecocean/api/BaseObject.java:22:import org.ecocean.Base;
src/main/java/org/ecocean/api/BaseObject.java:38:// note: this is for use on any Base object (MarkedIndividual, Encounter, Occurrence)
src/main/java/org/ecocean/api/BaseObject.java:39:public class BaseObject extends ApiBase {
src/main/java/org/ecocean/api/BaseObject.java:115:        myShepherd.setAction("api.BaseObject.processPost");
src/main/java/org/ecocean/api/BaseObject.java:123:        Base obj = null;
src/main/java/org/ecocean/api/BaseObject.java:204:            // add for any flavor of Base
src/main/java/org/ecocean/api/BaseObject.java:212:            System.out.println("BaseObject.processPost() returning 400 due to " + apiEx +
src/main/java/org/ecocean/api/BaseObject.java:219:            System.out.println("BaseObject.processPost() success (200) creating " + obj +
src/main/java/org/ecocean/api/BaseObject.java:247:        myShepherd.setAction("api.BaseObject.processGet");
src/main/java/org/ecocean/api/BaseObject.java:252:            Base obj = null;
src/main/java/org/ecocean/api/BaseObject.java:253:            if (args.length > 0) obj = Base.getByClassnameAndId(myShepherd, args[0], args[1]);
src/main/java/org/ecocean/api/BaseObject.java:292:        myShepherd.setAction("api.BaseObject.processPatch");
src/main/java/org/ecocean/api/BaseObject.java:295:        Base obj = null;
src/main/java/org/ecocean/api/BaseObject.java:299:            if (args.length > 0) obj = Base.getByClassnameAndId(myShepherd, args[0], args[1]);
src/main/java/org/ecocean/api/BaseObject.java:354:                System.out.println("BaseObject.makeMediaAssets() failed isValidImage() on " + file);
src/main/java/org/ecocean/api/BaseObject.java:372:                System.out.println("BaseObject.makeMediaAssets() failed on " + file + ": " + ioe);
src/main/java/org/ecocean/api/BaseObject.java:377:                System.out.println("BaseObject.makeMediaAssets() failed on " + file +
src/main/java/org/ecocean/Occurrence.java:42:public class Occurrence extends Base implements java.io.Serializable {
src/main/java/org/ecocean/Occurrence.java:420:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:425:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:438:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:453:    // TODO: validate and remove if ##DEPRECATED #509 - Base class setId() method
src/main/java/org/ecocean/Occurrence.java:862:    // see note on Base class
src/main/java/org/ecocean/Occurrence.java:872:    // see note on Base class
src/main/java/org/ecocean/Occurrence.java:1413:        // note: "id" is done in Base.java
src/main/java/org/ecocean/Occurrence.java:1545:    @Override public Base getById(Shepherd myShepherd, String id) {
src/main/java/org/ecocean/grid/EncounterLite.java:374:            double maxBaseTR2 = 0;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/WildbookLifecycleListener.java | sed -n '20,70p'" in /mnt/c/Wildbook-clean2
 succeeded in 293ms:
    20	
    21	        // cannot actually use obj, as it will throw: javax.jdo.JDOUserException: Cannot read fields from a deleted object
    22	/*
    23	        System.out.println("WildbookLifecycleListener postDelete() event type=" +
    24	            event.getEventType() + "; source id=" + obj.dnGetObjectId());
    25	        // System.out.println("WildbookLifecycleListener postDelete() event type=" + event.getEventType() + "; source=" + obj + "; target=" + event.getTarget() + "; detachedInstance=" + event.getDetachedInstance() + "; persistentInstance=" + event.getPersistentInstance());
    26	 */
    27	        if (Base.class.isInstance(obj)) {
    28	            Base base = (Base)obj;
    29	            try {
    30	                if (base.getSkipAutoIndexing()) return;
    31	                base.opensearchUnindexDeep();
    32	            } catch (Exception ex) {
    33	                ex.printStackTrace();
    34	            }
    35	        }
    36	    }
    37	
    38	    public void preStore(InstanceLifecycleEvent event) {}
    39	
    40	    public void postStore(InstanceLifecycleEvent event) {
    41	        Persistable obj = (Persistable)event.getSource();
    42	
    43	        if (OpenSearch.skipAutoIndexing()) {
    44	            System.out.println("WildbookLifecycleListener skipAutoIndexing set");
    45	            return;
    46	        }
    47	/*
    48	        System.out.println("WildbookLifecycleListener postStore() event type=" +
    49	            event.getEventType() + "; source=" + obj + "; target=" + event.getTarget() +
    50	            "; detachedInstance=" + event.getDetachedInstance() + "; persistentInstance=" +
    51	            event.getPersistentInstance());
    52	 */
    53	        if (Base.class.isInstance(obj)) {
    54	            Base base = (Base)obj;
    55	            if (base.getSkipAutoIndexing()) return;
    56	            System.out.println("WildbookLifecycleListener postStore() event on " + base);
    57	            try {
    58	                // base.opensearchIndexDeep();
    59	                // new way - put indexing in managed queue
    60	                IndexingManager im = IndexingManagerFactory.getIndexingManager();
    61	                im.addIndexingQueueEntry(base, false);
    62	            } catch (Exception ex) {
    63	                ex.printStackTrace();
    64	            }
    65	        } else if (Collaboration.class.isInstance(obj)) {
    66	            System.out.println("WildbookLifecycleListener postStore() event on " + obj +
    67	                " triggering permissionsNeeded=true");
    68	            OpenSearch.setPermissionsNeeded(true);
    69	        }
    70	    }

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '560,630p'" in /mnt/c/Wildbook-clean2
 succeeded in 307ms:
   560			<column length="64" allows-null="true"/>
   561		</field>
   562		<field name="thetaKey">
   563			<column length="20" allows-null="true"/>
   564		</field>
   565		<field name="wbiaRegistered">
   566			<column allows-null="true"/>
   567		</field>
   568		<field name="wbiaRegisterAttempts">
   569			<column allows-null="false" default-value="0"/>
   570		</field>
   571	
   572			<field name="isExemplar">
   573	<!--  note, if you are using mysql, which maybe you shouldnt be?, you want this here: -->
   574	<!--			<column default-value="0" /> -->
   575	<!-- this is for postgresql-->
   576				<column default-value="false" />
   577	
   578			</field>
   579	
   580			<field name="matchAgainst">
   581				<column default-value="false" />
   582				<index name="ANNOTATION_MATCHAGAINST_idx" />
   583			</field>
   584			<field name="viewpoint">
   585				<index name="ANNOTATION_VIEWPOINT_IDX" />
   586			</field>
   587	
   588			<field jdbc-type="BIGINT" name="version" allows-null="false" >
   589				<column default-value="-1" />
   590				<index name="ANNOTATION_VERSION_IDX" />
   591			</field>
   592	
   593	            	<field name="features" persistence-modifier="persistent" default-fetch-group="false" recursion-depth="2" mapped-by="annotation" >
   594	                	<collection element-type="org.ecocean.media.Feature" dependent-element="true" />
   595	                	<join />
   596	            	</field>
   597	
   598	      <field name="mediaAsset" persistence-modifier="persistent" element-type="org.ecocean.media.MediaAsset" recursion-depth="3"/>
   599	
   600	      <field name="quality" persistence-modifier="persistent">
   601	        <column jdbc-type="DOUBLE" allows-null="true"/>
   602	      </field>
   603	
   604	      <field name="distinctiveness" persistence-modifier="persistent">
   605	        <column jdbc-type="DOUBLE" allows-null="true"/>
   606	      </field>
   607	
   608	      <!--
   609			<field name="annot_xtl" />
   610			<field name="annot_ytl" />
   611			<field name="annot_width" />
   612			<field name="annot_height" />
   613			<field name="annot_theta" />
   614			<field name="species_text" />
   615	-->
   616	
   617		<field name="embeddings" default-fetch-group="false" mapped-by="annotation">
   618			<collection element-type="org.ecocean.Embedding" dependent-element="false" />
   619		</field>
   620	
   621	    </class>
   622	
   623	    <class name="DataCollectionEvent" identity-type="application">
   624	      <inheritance strategy="new-table"/>
   625	      <field name="correspondingEncounterNumber">
   626	        <column name="CORRESPONDINGENCOUNTERNUMBER"/>
   627	      </field>
   628	
   629	      <field name="dataCollectionEventID" primary-key="true" value-strategy="native">
   630	        <column name="DATACOLLECTIONEVENTID"/>

exec
/bin/bash -lc "nl -ba src/main/resources/org/ecocean/package.jdo | sed -n '520,565p'" in /mnt/c/Wildbook-clean2
 succeeded in 292ms:
   520		</field>
   521	
   522	      <!-- List<String> commonNames should work naturally since List<String> Encounter.submitterResearchers does -->
   523	    </class>
   524	
   525		<class name="Setting" identity-type="application">
   526			<field name="group" primary-key="true" />
   527			<field name="id" primary-key="true" />
   528			<field name="value" jdbc-type="LONGVARCHAR" />
   529			<field name="created" jdbc-type="BIGINT" />
   530			<field name="modified" jdbc-type="BIGINT" />
   531		</class>
   532	
   533	
   534	    <class name="SuperSpot">
   535	      <datastore-identity strategy="increment"/>
   536	    </class>
   537	
   538	    <class name="Annotation" identity-type="application">
   539	      <field name="id" primary-key="true">
   540	        <column length="36"/>
   541	      </field>
   542	
   543	      <field name="iaClass">
   544	        <column name="IACLASS"/>
   545	      </field>
   546	
   547		<field name="acmId">
   548			<column length="100"/>
   549		</field>
   550	
   551		<!-- ml-service migration v2 (commit #4): idempotency + WBIA registration.
   552		     The partial unique index on (mediaAsset, predictModelId, bboxKey,
   553		     thetaKey) WHERE predictModelId IS NOT NULL is applied by
   554		     archive/sql/ml_service_idempotency.sql. -->
   555		<field name="predictModelId">
   556			<column length="100" allows-null="true"/>
   557			<index name="ANNOTATION_PREDICTMODELID_IDX"/>
   558		</field>
   559		<field name="bboxKey">
   560			<column length="64" allows-null="true"/>
   561		</field>
   562		<field name="thetaKey">
   563			<column length="20" allows-null="true"/>
   564		</field>
   565		<field name="wbiaRegistered">

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '440,640p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '730,835p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '90,240p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '680,815p'" in /mnt/c/Wildbook-clean2
 succeeded in 273ms:
   440	     * passed across the close/open boundary into Phase B.
   441	     *
   442	     * <p>Phase A is responsible for pre-validating that all required
   443	     * fields are populated; Phase B treats the DTO as opaque and does
   444	     * not re-touch any JDO-managed state.</p>
   445	     */
   446	    public static final class WbiaRegisterRequest {
   447	        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
   448	        public final String annotationAcmId;    // Annotation.acmId, may differ from id on legacy rows
   449	        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
   450	        public final int[]  bbox;               // x,y,w,h
   451	        public final double theta;
   452	        public final String iaClass;            // species/class string
   453	        public final String individualName;     // "____" if absent
   454	
   455	        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
   456	            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
   457	            String individualName) {
   458	            this.annotationId    = annotationId;
   459	            this.annotationAcmId = annotationAcmId;
   460	            this.mediaAssetAcmId = mediaAssetAcmId;
   461	            this.bbox            = bbox;
   462	            this.theta           = theta;
   463	            this.iaClass         = iaClass;
   464	            this.individualName  = individualName;
   465	        }
   466	    }
   467	
   468	    /**
   469	     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
   470	     * fetch failure rather than returning an empty list. Phase B needs
   471	     * this so a network failure during the already-present check is
   472	     * not silently treated as "go ahead and POST".
   473	     *
   474	     * <p>Honors the 15-minute QueryCache the same way the lenient
   475	     * variant does, so a cache hit avoids the network entirely.</p>
   476	     */
   477	    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
   478	        String cacheName = "iaAnnotationIds";
   479	        // QueryCacheFactory.getQueryCache(context) can return null on a
   480	        // context that has never been initialized; treat that as "no cache"
   481	        // rather than NPE-ing out and aborting the poll cycle.
   482	        QueryCache qc = null;
   483	        try {
   484	            qc = QueryCacheFactory.getQueryCache(context);
   485	        } catch (Exception ex) {
   486	            // Defensive: cache factory init can fail; degrade to no-cache.
   487	        }
   488	        if (qc != null && qc.getQueryByName(cacheName) != null &&
   489	            System.currentTimeMillis() <
   490	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   491	            try {
   492	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   493	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   494	                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
   495	                return parseAnnotationIdsArrayStrict(cached);
   496	            } catch (Exception ex) {
   497	                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
   498	                    + ex.getMessage());
   499	            }
   500	        }
   501	        JSONArray jids;
   502	        try {
   503	            jids = apiGetJSONArray("/api/annot/json/", context);
   504	        } catch (Exception ex) {
   505	            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
   506	        }
   507	        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
   508	        if (qc != null) {
   509	            try {
   510	                org.datanucleus.api.rest.orgjson.JSONObject jobj =
   511	                    new org.datanucleus.api.rest.orgjson.JSONObject();
   512	                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
   513	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   514	                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   515	                qc.addCachedQuery(cq);
   516	            } catch (Exception cacheEx) {
   517	                // Cache store failure is non-fatal; we still have the ids.
   518	            }
   519	        }
   520	        return parseAnnotationIdsArrayStrict(jids);
   521	    }
   522	
   523	    /**
   524	     * Strict element parser: throws IOException if any element is not a
   525	     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
   526	     * skips/null-pads malformed entries, which is fine for legacy paths but
   527	     * would let a corrupt response masquerade as "annotation not yet
   528	     * registered" in the polling thread's already-present check.
   529	     */
   530	    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
   531	        List<String> ids = new ArrayList<String>();
   532	        if (jids == null) return ids;
   533	        for (int i = 0; i < jids.length(); i++) {
   534	            JSONObject jo = jids.optJSONObject(i);
   535	            if (jo == null)
   536	                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
   537	            String decoded = fromFancyUUID(jo);
   538	            if (decoded == null)
   539	                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
   540	            ids.add(decoded);
   541	        }
   542	        return ids;
   543	    }
   544	
   545	    static List<String> parseAnnotationIdsArray(JSONArray jids) {
   546	        List<String> ids = new ArrayList<String>();
   547	        if (jids == null) return ids;
   548	        for (int i = 0; i < jids.length(); i++) {
   549	            JSONObject jo = jids.optJSONObject(i);
   550	            if (jo != null) ids.add(fromFancyUUID(jo));
   551	        }
   552	        return ids;
   553	    }
   554	
   555	    /**
   556	     * Build the forced-id POST body for a single DTO. Pure function;
   557	     * factored out so unit tests can verify the request shape without
   558	     * a network round trip.
   559	     */
   560	    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
   561	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   562	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   563	        map.put("annot_uuid_list", new ArrayList<JSONObject>());
   564	        map.put("annot_species_list", new ArrayList<String>());
   565	        map.put("annot_bbox_list", new ArrayList<int[]>());
   566	        map.put("annot_name_list", new ArrayList<String>());
   567	        map.put("annot_theta_list", new ArrayList<Double>());
   568	        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
   569	        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
   570	        map.get("annot_species_list").add(dto.iaClass);
   571	        map.get("annot_bbox_list").add(dto.bbox);
   572	        map.get("annot_name_list").add(
   573	            (dto.individualName == null) ? "____" : dto.individualName);
   574	        map.get("annot_theta_list").add(dto.theta);
   575	        return map;
   576	    }
   577	
   578	    /**
   579	     * Validate a forced-id response. Throws on any contract violation
   580	     * (length mismatch, missing entry, id mismatch). Pure function.
   581	     */
   582	    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
   583	        if (resp == null) throw new IOException("null forced-id response");
   584	        if (resp.has("status")) {
   585	            JSONObject status = resp.optJSONObject("status");
   586	            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
   587	                throw new IOException("forced-id response status.success=false: " + resp);
   588	            }
   589	        }
   590	        JSONArray respArr = resp.optJSONArray("response");
   591	        if (respArr == null) throw new IOException("no response array: " + resp);
   592	        if (respArr.length() != 1)
   593	            throw new IOException("expected response array length 1, got " + respArr.length());
   594	        JSONObject jid = respArr.optJSONObject(0);
   595	        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
   596	        String respId = fromFancyUUID(jid);
   597	        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
   598	        if (!respId.equals(sentAnnotId))
   599	            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
   600	    }
   601	
   602	    /**
   603	     * Phase B entry point. Does the already-present check, builds the
   604	     * forced-id POST, fires it, and classifies the outcome. Does NOT
   605	     * touch any Shepherd or JDO state; callers must hand it a DTO that
   606	     * was pre-validated and detached in Phase A.
   607	     */
   608	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
   609	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
   610	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   611	        if (u == null) {
   612	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
   613	            return WbiaRegisterOutcome.NETWORK_FAIL;
   614	        }
   615	        List<String> known;
   616	        try {
   617	            known = iaAnnotationIdsStrict(context);
   618	        } catch (IOException ex) {
   619	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
   620	                ex.getMessage());
   621	            return WbiaRegisterOutcome.NETWORK_FAIL;
   622	        }
   623	        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
   624	        // only check the annotation's id and acmId here. Comparing against
   625	        // the media-asset's acmId is wrong - that would compare an image
   626	        // identifier against a list of annotation identifiers.
   627	        if (known.contains(dto.annotationId) ||
   628	            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
   629	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
   630	        }
   631	        URL url;
   632	        try {
   633	            url = new URL(u);
   634	        } catch (MalformedURLException ex) {
   635	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
   636	            return WbiaRegisterOutcome.NETWORK_FAIL;
   637	        }
   638	        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
   639	        JSONObject rtn;
   640	        try {

 succeeded in 276ms:
   730	                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
   731	                }
   732	            } catch (Exception ex) {
   733	                ex.printStackTrace();
   734	                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
   735	            }
   736	        }
   737	        return ids;
   738	    }
   739	
   740	    // as above, but images
   741	    public List<String> iaImageIds() {
   742	        return iaImageIds(this.context);
   743	    }
   744	
   745	    public static List<String> iaImageIds(String context) {
   746	        List<String> ids = new ArrayList<String>();
   747	        JSONArray jids = null;
   748	
   749	        try {
   750	            jids = apiGetJSONArray("/api/image/json/", context);
   751	        } catch (Exception ex) {
   752	            ex.printStackTrace();
   753	            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
   754	                ex.toString());
   755	        }
   756	        if (jids != null) {
   757	            try {
   758	                for (int i = 0; i < jids.length(); i++) {
   759	                    if (jids.optJSONObject(i) != null)
   760	                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
   761	                }
   762	            } catch (Exception ex) {
   763	                ex.printStackTrace();
   764	                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
   765	            }
   766	        }
   767	        return ids;
   768	    }
   769	
   770	    public JSONArray apiGetJSONArray(String urlSuffix)
   771	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   772	        InvalidKeyException {
   773	        return apiGetJSONArray(urlSuffix, this.context);
   774	    }
   775	
   776	    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
   777	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   778	        InvalidKeyException {
   779	        URL u = IBEISIA.iaURL(context, urlSuffix);
   780	        JSONObject rtn = RestClient.get(u);
   781	
   782	        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
   783	            (rtn.optJSONArray("response") == null) ||
   784	            !rtn.getJSONObject("status").optBoolean("success", false)) {
   785	            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
   786	                rtn);
   787	            return null;
   788	        }
   789	        return rtn.getJSONArray("response");
   790	    }
   791	
   792	    public static String fromFancyUUID(JSONObject u) {
   793	        if (u == null) return null;
   794	        return u.optString("__UUID__", null);
   795	    }
   796	
   797	    public static JSONObject toFancyUUID(String u) {
   798	        JSONObject j = new JSONObject();
   799	
   800	        j.put("__UUID__", u);
   801	        return j;
   802	    }
   803	
   804	    private static Object mediaAssetToUri(MediaAsset ma) {
   805	        URL curl = ma.webURL();
   806	        String urlStr = curl.toString();
   807	
   808	        // THIS WILL BREAK if you need to append a query to the filename...
   809	        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
   810	        if (urlStr != null) {
   811	            urlStr = urlStr.replaceAll("\\?", "%3F");
   812	            if (ma.getStore() instanceof LocalAssetStore) {
   813	                return urlStr;
   814	            } else {
   815	                return urlStr;
   816	            }
   817	        }
   818	        return null;
   819	    }
   820	
   821	    // basically "should we send to IA?"
   822	    public static boolean validMediaAsset(MediaAsset ma) {
   823	        if (ma == null) return false;
   824	        if (!ma.isMimeTypeMajor("image")) return false;
   825	        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
   826	        if (mediaAssetToUri(ma) == null) {
   827	            System.out.println(
   828	                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
   829	                ma);
   830	            return false;
   831	        }
   832	        return true;
   833	    }
   834	
   835	    // this is used to give a string to IA for annot_species_list specifially

 succeeded in 294ms:
   680	            shep.commitDBTransaction();
   681	            System.out.println("[INFO] StaleMlServiceReconciliation: re-enqueued " + maId);
   682	        } catch (Exception ex) {
   683	            System.out.println("WARN: StaleMlServiceReconciliation registerOne failed for " +
   684	                maId + ": " + ex);
   685	            shep.rollbackDBTransaction();
   686	        } finally {
   687	            shep.closeDBTransaction();
   688	        }
   689	    }
   690	
   691	    /**
   692	     * Phase A/B/C split per Codex c11 fix-review.
   693	     * <ul>
   694	     *   <li>Phase A: Shepherd open, re-check state, build DTO, close.
   695	     *   <li>Phase B: no Shepherd held; WBIA HTTP via
   696	     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
   697	     *   <li>Phase C: Shepherd open, re-load, persist outcome, close.
   698	     * </ul>
   699	     * Ineligible annotations (missing media asset, missing acmId, fails
   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
   701	     * fall out of the polling query.
   702	     */
   703	    private static void registerOneAnnotationWithWbia(String context, String annId) {
   704	        // ---- Phase A: load DTO under a short transaction. ----
   705	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   706	            loadWbiaRegisterDto(context, annId);
   707	        if (dto == null) return;  // ineligible / already registered / parked
   708	
   709	        // Bail out before starting the non-interruptible HTTP call if
   710	        // shutdown was requested while Phase A was running. Otherwise we
   711	        // would start a 300s WBIA POST that contextDestroyed can't cancel.
   712	        if (Thread.currentThread().isInterrupted() ||
   713	            wbiaRegisterExecutor == null) {
   714	            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
   715	                " (shutdown requested)");
   716	            return;
   717	        }
   718	
   719	        // ---- Phase B: no Shepherd held; call WBIA. ----
   720	        org.ecocean.ia.plugin.WildbookIAM iam =
   721	            new org.ecocean.ia.plugin.WildbookIAM(context);
   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
   723	            iam.registerOneByDto(dto);
   724	
   725	        // Skip Phase C if shutdown has been requested while Phase B ran.
   726	        // RestClient is not interruptible mid-IO, so Phase B can outlive
   727	        // awaitTermination; this prevents Phase C from racing the rest of
   728	        // contextDestroyed's cleanup (Shepherd / IndexingManager / etc.).
   729	        if (Thread.currentThread().isInterrupted() ||
   730	            wbiaRegisterExecutor == null) {
   731	            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
   732	                " (shutdown requested)");
   733	            return;
   734	        }
   735	
   736	        // ---- Phase C: persist outcome under a short transaction. ----
   737	        persistWbiaRegisterResult(context, annId, outcome);
   738	    }
   739	
   740	    /**
   741	     * Phase A. Returns a detached DTO ready for Phase B, or null if the
   742	     * annotation does not need (or cannot get) a Phase-B network call.
   743	     * Null cases: missing annotation, already registered, parked at max
   744	     * attempts, or ineligible (missing media asset / acmId / bbox / etc.).
   745	     * Ineligible annotations are parked here so they stop being polled.
   746	     */
   747	    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
   748	        loadWbiaRegisterDto(String context, String annId) {
   749	        Shepherd shep = new Shepherd(context);
   750	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
   751	        shep.beginDBTransaction();
   752	        try {
   753	            org.ecocean.Annotation ann = shep.getAnnotation(annId);
   754	            if (ann == null) {
   755	                shep.commitDBTransaction();
   756	                return null;
   757	            }
   758	            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
   759	                shep.commitDBTransaction();
   760	                return null;
   761	            }
   762	            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   763	                shep.commitDBTransaction();
   764	                return null;
   765	            }
   766	            // Eligibility checks. Any failure here is permanent for this
   767	            // annotation under its current state, so park it.
   768	            org.ecocean.media.MediaAsset ma = ann.getMediaAsset();
   769	            String reason = null;
   770	            if (ma == null) reason = "missing media asset";
   771	            else if (!Util.stringExists(ma.getAcmId())) reason = "media asset has no acmId";
   772	            else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
   773	            else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
   774	                reason = "validForIdentification returned false (bbox/iaClass/etc.)";
   775	            if (reason != null) {
   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
   777	                    " (ineligible: " + reason + ")");
   778	                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
   779	                shep.commitDBTransaction();
   780	                return null;
   781	            }
   782	            // Resolve the individual name now while the Shepherd is open;
   783	            // Phase B has no DB access.
   784	            String name = ann.findIndividualId(shep);
   785	            // Copy bbox into a fresh array so the DTO is fully detached.
   786	            int[] bb = ann.getBbox();
   787	            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
   788	            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   789	                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
   790	                    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
   791	                    ann.getTheta(), ann.getIAClass(), name);
   792	            shep.commitDBTransaction();
   793	            return dto;
   794	        } catch (Exception ex) {
   795	            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
   796	                annId + ": " + ex);
   797	            shep.rollbackDBTransaction();
   798	            return null;
   799	        } finally {
   800	            shep.closeDBTransaction();
   801	        }
   802	    }
   803	
   804	    /**
   805	     * Phase C. Re-loads the annotation and writes the outcome of the
   806	     * Phase-B network call. On terminal-success outcomes the annotation
   807	     * is marked registered; on retryable outcomes the attempts counter
   808	     * is bumped and we WARN-log when we hit the abandonment threshold.
   809	     */
   810	    private static void persistWbiaRegisterResult(String context, String annId,
   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
   812	        Shepherd shep = new Shepherd(context);
   813	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
   814	        shep.beginDBTransaction();
   815	        try {

 succeeded in 309ms:
    90	 */
    91	    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
    92	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    93	        InvalidKeyException {
    94	        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
    95	
    96	        if (u == null)
    97	            throw new MalformedURLException(
    98	                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
    99	        URL url = new URL(u);
   100	        int batchSize = 30;
   101	        int numBatches = Math.round(mas.size() / batchSize + 1);
   102	
   103	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   104	        List<String> iaImageIds = new ArrayList<String>();
   105	        if (checkFirst) iaImageIds = iaImageIds();
   106	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   107	        map.put("image_uri_list", new ArrayList<JSONObject>());
   108	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   109	        map.put("image_unixtime_list", new ArrayList<Integer>());
   110	        map.put("image_gps_lat_list", new ArrayList<Double>());
   111	        map.put("image_gps_lon_list", new ArrayList<Double>());
   112	        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
   113	        int batchCt = 1;
   114	        JSONObject allRtn = new JSONObject();
   115	        allRtn.put("_batchSize", batchSize);
   116	        allRtn.put("_totalSize", mas.size());
   117	        JSONArray bres = new JSONArray();
   118	        for (int i = 0; i < mas.size(); i++) {
   119	            MediaAsset ma = mas.get(i);
   120	            if (iaImageIds.contains(ma.getAcmId())) continue;
   121	            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
   122	                IA.log(
   123	                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
   124	                    + ma.getId());
   125	                continue;
   126	            }
   127	            if (!validMediaAsset(ma)) {
   128	                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
   129	                continue;
   130	            }
   131	            acmList.add(ma);
   132	            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
   133	            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
   134	            map.get("image_uri_list").add(mediaAssetToUri(ma));
   135	            map.get("image_gps_lat_list").add(ma.getLatitude());
   136	            map.get("image_gps_lon_list").add(ma.getLongitude());
   137	            DateTime t = ma.getDateTime();
   138	            if (t == null) {
   139	                map.get("image_unixtime_list").add(null);
   140	            } else {
   141	                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
   142	            }
   143	            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
   144	                if (acmList.size() > 0) {
   145	                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
   146	                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
   147	                        " batches)");
   148	                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   149	                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
   150	                    List<String> acmIds = acmIdsFromResponse(rtn);
   151	                    if (acmIds == null) {
   152	                        IA.log(
   153	                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
   154	                            + rtn);
   155	                    } else {
   156	                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
   157	                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
   158	                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
   159	                    }
   160	                    bres.put(rtn);
   161	                    // initialize for next batch (if any)
   162	                    map.put("image_uri_list", new ArrayList<JSONObject>());
   163	                    map.put("image_uuid_list", new ArrayList<JSONObject>());
   164	                    map.put("image_unixtime_list", new ArrayList<Integer>());
   165	                    map.put("image_gps_lat_list", new ArrayList<Double>());
   166	                    map.put("image_gps_lon_list", new ArrayList<Double>());
   167	                    acmList = new ArrayList<MediaAsset>();
   168	                } else {
   169	                    bres.put("EMPTY BATCH");
   170	                }
   171	                batchCt++;
   172	            }
   173	        }
   174	        allRtn.put("batchResults", bres);
   175	        return allRtn;
   176	    }
   177	
   178	    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
   179	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   180	        InvalidKeyException {
   181	        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
   182	
   183	        if (u == null)
   184	            throw new MalformedURLException(
   185	                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
   186	        URL url = new URL(u);
   187	        int batchSize = 30;
   188	        int numBatches = Math.round(mas.size() / batchSize + 1);
   189	
   190	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   191	        List<String> iaImageIds = new ArrayList<String>();
   192	        if (checkFirst) iaImageIds = iaImageIds();
   193	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   194	        map.put("image_uri_list", new ArrayList<JSONObject>());
   195	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   196	        map.put("image_unixtime_list", new ArrayList<Integer>());
   197	        map.put("image_gps_lat_list", new ArrayList<Double>());
   198	        map.put("image_gps_lon_list", new ArrayList<Double>());
   199	        int batchCt = 1;
   200	        JSONObject allRtn = new JSONObject();
   201	        allRtn.put("_batchSize", batchSize);
   202	        allRtn.put("_totalSize", mas.size());
   203	        JSONArray bres = new JSONArray();
   204	        for (int i = 0; i < mas.size(); i++) {
   205	            MediaAsset ma = mas.get(i);
   206	            if (iaImageIds.contains(ma.getAcmId())) continue;
   207	            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
   208	                IA.log(
   209	                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
   210	                    + ma.getId());
   211	                continue;
   212	            }
   213	            if (!validMediaAsset(ma)) {
   214	                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
   215	                continue;
   216	            }
   217	            map.get("image_uri_list").add(mediaAssetToUri(ma));
   218	            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
   219	            map.get("image_gps_lat_list").add(ma.getLatitude());
   220	            map.get("image_gps_lon_list").add(ma.getLongitude());
   221	            DateTime t = ma.getDateTime();
   222	            if (t == null) {
   223	                map.get("image_unixtime_list").add(null);
   224	            } else {
   225	                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
   226	            }
   227	            int sendSize = map.get("image_uri_list").size();
   228	            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
   229	                if (sendSize > 0) {
   230	                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
   231	                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
   232	                        " batches)");
   233	                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   234	                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
   235	/*
   236	                    if (acmIds == null) {
   237	                        IA.log(
   238	                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
   239	 + rtn);
   240	                    } else {

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '640,725p' && nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '815,870p'" in /mnt/c/Wildbook-clean2
 succeeded in 307ms:
   640	        try {
   641	            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   642	        } catch (Exception ex) {
   643	            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
   644	            return WbiaRegisterOutcome.NETWORK_FAIL;
   645	        }
   646	        try {
   647	            validateForcedResponse(dto.annotationId, rtn);
   648	        } catch (IOException ex) {
   649	            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
   651	        }
   652	        return WbiaRegisterOutcome.REGISTERED_OK;
   653	    }
   654	
   655	    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
   656	    throws IOException {
   657	        if ((sentIds == null) || (respArr == null))
   658	            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
   659	        if (sentIds.size() != respArr.length())
   660	            throw new IOException("args diff length: " + sentIds.size() + " != " +
   661	                    respArr.length());
   662	        for (int i = 0; i < sentIds.size(); i++) {
   663	            String sentId = fromFancyUUID(sentIds.get(i));
   664	            if (sentId == null)
   665	                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
   666	            JSONObject jid = respArr.optJSONObject(i);
   667	            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
   668	            String respId = fromFancyUUID(jid);
   669	            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
   670	            if (!respId.equals(sentId))
   671	                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
   672	                        "; respId=" + respId);
   673	        }
   674	    }
   675	
   676	    public static List<String> acmIdsFromResponse(JSONObject rtn) {
   677	        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
   678	        List<String> ids = new ArrayList<String>();
   679	        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
   680	            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
   681	                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
   682	                ids.add(null);
   683	            } else {
   684	                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
   685	            }
   686	        }
   687	        System.out.println("fromResponse ---> " + ids);
   688	        return ids;
   689	    }
   690	
   691	    // instance version of below (since context is known)
   692	    public List<String> iaAnnotationIds() {
   693	        return iaAnnotationIds(this.context);
   694	    }
   695	
   696	    // this fails "gracefully" with empty list if network fubar.  bad decision?
   697	    public static List<String> iaAnnotationIds(String context) {
   698	        List<String> ids = new ArrayList<String>();
   699	        JSONArray jids = null;
   700	        String cacheName = "iaAnnotationIds";
   701	
   702	        try {
   703	            QueryCache qc = QueryCacheFactory.getQueryCache(context);
   704	            if (qc.getQueryByName(cacheName) != null &&
   705	                System.currentTimeMillis() <
   706	                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   707	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   708	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   709	                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
   710	            } else {
   711	                jids = apiGetJSONArray("/api/annot/json/", context);
   712	                if (jids != null) {
   713	                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
   714	                        new org.datanucleus.api.rest.orgjson.JSONObject();
   715	                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
   716	                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   717	                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   718	                    qc.addCachedQuery(cq);
   719	                }
   720	            }
   721	        } catch (Exception ex) {
   722	            ex.printStackTrace();
   723	            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
   724	                ex.toString());
   725	        }
   815	        try {
   816	            org.ecocean.Annotation ann = shep.getAnnotation(annId);
   817	            if (ann == null) {
   818	                shep.commitDBTransaction();
   819	                return;
   820	            }
   821	            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
   822	                // Some other path flipped it while Phase B ran; respect that.
   823	                shep.commitDBTransaction();
   824	                return;
   825	            }
   826	            switch (outcome) {
   827	                case REGISTERED_OK:
   828	                case REGISTERED_ALREADY_PRESENT:
   829	                    // Always honor a success outcome even if the row was
   830	                    // parked by a racing poller: stuck-at-attempts==MAX
   831	                    // would otherwise become permanent.
   832	                    ann.setWbiaRegistered(Boolean.TRUE);
   833	                    break;
   834	                case NETWORK_FAIL:
   835	                case RESPONSE_BAD:
   836	                default:
   837	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   838	                        // Already parked by another path; do not increment past MAX.
   839	                        break;
   840	                    }
   841	                    ann.incrementWbiaRegisterAttempts();
   842	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   843	                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
   844	                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
   845	                            " attempts (last outcome=" + outcome + "); will not retry");
   846	                    }
   847	                    break;
   848	            }
   849	            shep.commitDBTransaction();
   850	        } catch (Exception ex) {
   851	            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
   852	                annId + ": " + ex);
   853	            shep.rollbackDBTransaction();
   854	        } finally {
   855	            shep.closeDBTransaction();
   856	        }
   857	    }
   858	
   859	    public void contextDestroyed(ServletContextEvent sce) {
   860	        ServletContext sContext = sce.getServletContext();
   861	        String context = "context0";
   862	
   863	        System.out.println("* StartupWildbook destroyed called for: " +
   864	            servletContextInfo(sContext));
   865	
   866	        if (CommonConfiguration.useSpotPatternRecognition(context)) {
   867	            saveMatchGraph(sContext, context);
   868	        }
   869	        // Stop the WBIA poller first so it does not race teardown of
   870	        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/bulk/BulkImporter.java | sed -n '90,125p' && rg -n \"setAcmId\\(|getUUID\\(\\)|acmId == MediaAsset.uuid|MediaAsset.acmId\" src/main/java/org/ecocean src/test/java/org/ecocean docs/plans/2026-05-09-ml-service-migration-v2.md" in /mnt/c/Wildbook-clean2
 succeeded in 1078ms:
    90	            } catch (Exception ex) {
    91	                // TODO we could allow this some leeway with a tolerance setting
    92	                System.out.println("createImport() row=" + rowNum + " failed with " + ex);
    93	                ex.printStackTrace();
    94	                throw new ServletException("unexpected exception on processRow for row=" + rowNum +
    95	                        ": " + ex);
    96	            }
    97	            // (previous) MediaAsset creation counts as 20%, and this counts as 50%,
    98	            // with persisting making up the remaining 30%  #progressBarKludge
    99	            markProgress(rowNum, dataRows.size(), 0.2d, 0.5d);
   100	        }
   101	        logProgress("end processRows");
   102	        System.out.println(
   103	            "------------ all rows processed; beginning persistence -------------\n");
   104	        int persistenceTicksTotal = mediaAssetMap.values().size() + userCache.values().size() +
   105	            encounterCache.values().size() + occurrenceCache.values().size() +
   106	            individualCache.values().size() + projectCache.values().size();
   107	        int persistenceTicks = 0;
   108	        List<Integer> maIds = new ArrayList<Integer>(); // used later to build child MAs
   109	        JSONArray arr = new JSONArray();
   110	        for (MediaAsset ma : mediaAssetMap.values()) {
   111	            ma.setSkipAutoIndexing(true);
   112	            MediaAssetFactory.save(ma, myShepherd);
   113	            System.out.println("MMMM " + ma);
   114	            arr.put(ma.getIdInt());
   115	            maIds.add(ma.getIdInt());
   116	            // see note on MediaAsset.getSkipAutoIndexing()
   117	            // needIndexing.add(ma);
   118	            persistenceTicks++;
   119	            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
   120	        }
   121	        logProgress("end persist MediaAsset");
   122	        rtn.put("mediaAssets", arr);
   123	        for (User u : userCache.values()) {
   124	            myShepherd.getPM().makePersistent(u);
   125	            persistenceTicks++;
docs/plans/2026-05-09-ml-service-migration-v2.md:53:  Encounter + MediaAsset persisted (MediaAsset.acmId = uuid; existing)
src/main/java/org/ecocean/FormUtilities.java:91:            // List<Project> projects = myShepherd.getOwnedProjectsForUserId(usr.getUUID());
src/main/java/org/ecocean/FormUtilities.java:120:            List<Project> projects = myShepherd.getOwnedProjectsForUserId(usr.getUUID());
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:70:                int imgId = mediaAssetToImageId.get(ma.getUUID());
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:116:                    log.warning("COCO Export: Failed to export image " + ma.getUUID() + ": " + e.getMessage());
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:135:            log.fine("COCO Export: Skipping image " + ma.getUUID() + " - no URL available");
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:142:        ZipEntry imgEntry = new ZipEntry("coco/images/" + ma.getUUID() + ".jpg");
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:274:                if (ma != null && ma.getUUID() != null) {
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:275:                    mediaAssetMap.putIfAbsent(ma.getUUID(), ma);
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:334:        if (ma == null || ma.getUUID() == null) return false;
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:390:        img.put("file_name", ma.getUUID() + ".jpg");
src/main/java/org/ecocean/export/EncounterCOCOExportFile.java:393:        img.put("uuid", ma.getUUID());
src/main/java/org/ecocean/User.java:400:    public String getUUID() { return uuid; }
src/main/java/org/ecocean/User.java:493:        if ((this.uuid == null) || (two == null) || (two.getUUID() == null)) return false;
src/main/java/org/ecocean/User.java:494:        return this.uuid.equals(two.getUUID());
src/main/java/org/ecocean/identity/IBEISIA.java:146:            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
src/main/java/org/ecocean/identity/IBEISIA.java:206:            map.get("image_uuid_list").add(toFancyUUID(ann.getMediaAsset().getUUID()));
src/main/java/org/ecocean/identity/IBEISIA.java:207:            map.get("annot_uuid_list").add(toFancyUUID(ann.getUUID()));
src/main/java/org/ecocean/identity/IBEISIA.java:1271:        ann.setAcmId(fromFancyUUID(iaResult.optJSONObject("uuid")));
src/main/java/org/ecocean/identity/IBEISIA.java:2155:            ann.setAcmId(acmId);
src/main/java/org/ecocean/identity/IBEISIA.java:2202:        ma.setAcmId(maUUID);
src/main/java/org/ecocean/cache/StoredQuery.java:70:    public String getUUID() { return uuid; }
src/main/java/org/ecocean/identity/IAQueryCache.java:65:                q.getUUID());
src/main/java/org/ecocean/identity/IAQueryCache.java:69:                q.getUUID());
src/main/java/org/ecocean/ImageProcessor.java:103:            if (this.parentMA.getUUID() != null) {
src/main/java/org/ecocean/ImageProcessor.java:104:                maId = this.parentMA.getUUID();
src/main/java/org/ecocean/Encounter.java:4301:            jgen.writeStringField("uuid", ma.getUUID());
src/main/java/org/ecocean/Encounter.java:4336:            if (featuredAssetId == null) featuredAssetId = ma.getUUID();
src/main/java/org/ecocean/Encounter.java:4860:                maj.put("uuid", ma.getUUID());
src/main/java/org/ecocean/cache/CachedQuery.java:34:        this.uuid = sq.getUUID();
src/main/java/org/ecocean/cache/CachedQuery.java:96:    public String getUUID() { return uuid; }
src/main/java/org/ecocean/cache/CachedQuery.java:111:        this.uuid = sq.getUUID();
src/main/java/org/ecocean/acm/AcmUtil.java:28:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:33:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:56:                anns.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:61:                anns.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/OpenSearch.java:1010:        stored.put("creator", user.getUUID());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:132:            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:218:            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:449:        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
src/main/java/org/ecocean/Annotation.java:312:    public void setAcmId(String id) {
src/main/java/org/ecocean/Annotation.java:387:    public String getUUID() {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:331:                ann.setAcmId(ann.getId());
src/main/java/org/ecocean/api/BaseObject.java:166:                    maj.put("uuid", ma.getUUID());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2214:                + user.getUUID() + "\" VARIABLES org.ecocean.User user");
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2233:                + user1.getUUID() + "\" && user2.uuid == \"" + user2.getUUID() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2570:                spv.setDataCollectionEventID(ma.getUUID());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4431:            if (qc.getQueryByName(("numRecentEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4432:                CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + user.getUUID()));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4433:                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4434:                System.out.println("found " + "numRecentEncounters_" + user.getUUID() + "_" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4438:                    startTime + " && submitters.contains(user) && user.uuid == '" + user.getUUID() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4441:                CachedQuery cq = new CachedQuery(("numRecentEncounters_" + user.getUUID()),
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4444:                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4445:                System.out.println("not found " + "numRecentEncounters_" + user.getUUID() + "_" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4472:            if (qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4473:                CachedQuery cq = qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID()));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4474:                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4475:                System.out.println("found " + "numRecentPhotoEncounters_" + user.getUUID() + "_" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4480:                    user.getUUID() + "' VARIABLES org.ecocean.User user";
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4482:                CachedQuery cq = new CachedQuery(("numRecentPhotoEncounters_" + user.getUUID()),
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4485:                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4486:                System.out.println("not found " + "numRecentPhotoEncounters_" + user.getUUID() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4606:            + user.getUUID() + "\") || submitterID == \"" + user.getUsername() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4620:            + user.getUUID() + "\" VARIABLES org.ecocean.User user";
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4672:            user.getUUID() + "\"";
src/main/java/org/ecocean/security/Collaboration.java:425:        if (viewer != null && owner != null && viewer.getUUID() != null &&
src/main/java/org/ecocean/security/Collaboration.java:426:            viewer.getUUID().equals(owner.getUUID())) return true; // should really be user .equals() method
src/main/java/org/ecocean/media/YouTubeAssetStore.java:255:        sp.put("key", parent.getUUID() + "/" + f.getName());
src/main/java/org/ecocean/media/YouTubeAssetStore.java:280:        sp.put("key", parent.getUUID() + "/" + f.getName());
src/main/java/org/ecocean/media/YouTubeAssetStore.java:316:        sp.put("key", parent.getUUID() + "/" + f.getName());
src/main/java/org/ecocean/servlet/EncounterAddUser.java:77:                        user.getUUID() + " of type " + type + ".</p>");
src/main/java/org/ecocean/servlet/EncounterAddUser.java:90:                        user.getUUID() + " of type " + type + ".</p>");
src/main/java/org/ecocean/servlet/EncounterAddUser.java:103:                        user.getUUID() + " of type " + type + ".</p>");
src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java:33:        when(ma.getUUID()).thenReturn("test-media-uuid");
src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java:118:        when(ma.getUUID()).thenReturn("test-media-uuid");
src/main/java/org/ecocean/servlet/UserGetSimpleJSON.java:55:                uj.put("id", u.getUUID());
src/main/java/org/ecocean/servlet/EncounterRemoveUser.java:64:                        user.getUUID() + " of type " + type + ".</p>");
src/main/java/org/ecocean/servlet/EncounterRemoveUser.java:73:                        user.getUUID() + " of type " + type + ".</p>");
src/main/java/org/ecocean/servlet/EncounterRemoveUser.java:82:                        user.getUUID() + " of type " + type + ".</p>");
src/main/java/org/ecocean/servlet/UserConsolidate.java:297:            userToBeConsolidated.getUUID() + "\"";                                                                                  // &&
src/main/java/org/ecocean/servlet/UserConsolidate.java:298:                                                                                                                                    // user.uuid==\""+userToBeConsolidated.getUUID()+"\"
src/main/java/org/ecocean/servlet/UserConsolidate.java:406:            + userToBeConsolidated.getUUID() + "' VARIABLES org.ecocean.User user";                                                                                                  //
src/main/java/org/ecocean/servlet/UserConsolidate.java:408:                                                                                                                                                                                     // user.uuid==\""+userToBeConsolidated.getUUID()+"\"
src/main/java/org/ecocean/servlet/UserConsolidate.java:678:            user.getUUID() + "\" VARIABLES org.ecocean.User user";
src/main/java/org/ecocean/servlet/UserConsolidate.java:693:        // user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user" is not super tractable, we will go with three different database
src/main/java/org/ecocean/servlet/UserConsolidate.java:701:                + user.getUUID() + "\" VARIABLES org.ecocean.User user";
src/main/java/org/ecocean/servlet/UserConsolidate.java:705:                + user.getUUID() + "\" VARIABLES org.ecocean.User user";
src/main/java/org/ecocean/servlet/UserConsolidate.java:710:                    user.getUsername() + "\" && user.uuid==\"" + user.getUUID() +
src/main/java/org/ecocean/servlet/UserConsolidate.java:766:                    " && username!=null && uuid!=\'" + currentUser.getUUID() + "\'");
src/main/java/org/ecocean/servlet/UserConsolidate.java:773:                    if (currentMatchingUserCohortMember.getUUID() !=
src/main/java/org/ecocean/servlet/UserConsolidate.java:774:                        highestCredentialedMatch.getUUID()) {
src/main/java/org/ecocean/servlet/UserConsolidate.java:776:                            currentMatchingUserCohortMember.getUUID() + "@localhost");
src/main/java/org/ecocean/servlet/UserConsolidate.java:780:                            currentMatchingUserCohortMember.getUUID() + "@localhost");
src/main/java/org/ecocean/servlet/UserConsolidate.java:826:                        returnJson.put(currentUsernamelessUser.getUUID(),
src/main/java/org/ecocean/servlet/UserConsolidate.java:828:                            " being consolidated into user: " + highestCredentialedMatch.getUUID() +
src/main/java/org/ecocean/servlet/UserConsolidate.java:835:                        currentConsolidatedUserJson.put("uuid", currentUsernamelessUser.getUUID());
src/main/java/org/ecocean/servlet/UserConsolidate.java:839:                            highestCredentialedMatch.getUUID());
src/main/java/org/ecocean/servlet/UserConsolidate.java:860:        if (focalUser == null || focalUser.getUUID() == null || focalUser.getEmailAddress() == null)
src/main/java/org/ecocean/servlet/UserConsolidate.java:863:                persistenceManager, "&& this.uuid!=\'" + focalUser.getUUID() + "\'");
src/main/java/org/ecocean/servlet/UserConsolidate.java:1018:            + user.getUUID() + "\" VARIABLES org.ecocean.User user";
src/main/java/org/ecocean/servlet/UserConsolidate.java:1262:                            currentUser.getUUID());
src/main/java/org/ecocean/servlet/UserConsolidate.java:1263:                        currentUser.setUsername("Anonymous_" + currentUser.getUUID());
src/main/java/org/ecocean/servlet/UserConsolidate.java:1298:                            currentUser.getUUID() + "@localhost" + " to user: " +
src/main/java/org/ecocean/servlet/UserConsolidate.java:1300:                        currentUser.setEmailAddress(currentUser.getUUID() + "@localhost");
src/main/java/org/ecocean/servlet/UserConsolidate.java:1336:                            currentUser.getUUID() + "@localhost" + " and username: Anonymous_" +
src/main/java/org/ecocean/servlet/UserConsolidate.java:1337:                            currentUser.getUUID() + " to user: " + currentUser.toString());
src/main/java/org/ecocean/servlet/UserConsolidate.java:1338:                        currentUser.setEmailAddress(currentUser.getUUID() + "@localhost");
src/main/java/org/ecocean/servlet/UserConsolidate.java:1339:                        currentUser.setUsername("Anonymous_" + currentUser.getUUID());
src/main/java/org/ecocean/servlet/UserConsolidate.java:1380:                        currentUserJson.put("uuid", similarUsers.get(j).getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:117:        if (this.acmId == null) this.acmId = this.getUUID();
src/main/java/org/ecocean/media/MediaAsset.java:132:    public void setAcmId(String id) {
src/main/java/org/ecocean/media/MediaAsset.java:235:    public String getUUID() {
src/main/java/org/ecocean/media/MediaAsset.java:696:            System.out.println("MediaAsset " + this.getUUID() + " has no store!");
src/main/java/org/ecocean/media/MediaAsset.java:998:        j.put("uuid", getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1694:        if ((this.uuid == null) || (two == null) || (two.getUUID() == null)) return false;
src/main/java/org/ecocean/media/MediaAsset.java:1695:        return this.uuid.equals(two.getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1727:        jgen.writeStringField("uuid", this.getUUID());
src/main/java/org/ecocean/servlet/ResurrectDeletedEncounter.java:87:                        String uuid = a.getUUID();
src/main/java/org/ecocean/servlet/IAGateway.java:124:                    tparams.put("userId", tuser.getUUID());
src/main/java/org/ecocean/servlet/OrganizationEdit.java:56:                uj.put("id", u.getUUID());

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/media/MediaAsset.java | sed -n '100,137p'" in /mnt/c/Wildbook-clean2
 succeeded in 329ms:
   100	    public MediaAsset() {
   101	        this(MediaAssetFactory.NOT_SAVED, null, null);
   102	    }
   103	
   104	    public MediaAsset(final AssetStore store, final JSONObject params) {
   105	        // this(store, params, null);
   106	        this(MediaAssetFactory.NOT_SAVED, store, params);
   107	    }
   108	
   109	    public MediaAsset(final int id, final AssetStore store, final JSONObject params) {
   110	        this.id = id;
   111	        this.setUUID();
   112	        this.store = store;
   113	        this.parameters = params;
   114	        if (params != null) this.parametersAsString = params.toString();
   115	        this.setRevision();
   116	        this.setHashCode();
   117	        if (this.acmId == null) this.acmId = this.getUUID();
   118	    }
   119	
   120	    public AccessControl getAccessControl() {
   121	        return accessControl;
   122	    }
   123	
   124	    public void setAccessControl(AccessControl ac) {
   125	        accessControl = ac;
   126	    }
   127	
   128	    public void setAccessControl(HttpServletRequest request) {
   129	        this.setAccessControl(new AccessControl(request));
   130	    }
   131	
   132	    public void setAcmId(String id) {
   133	        this.acmId = id;
   134	    }
   135	
   136	    public String getAcmId() {
   137	        return this.acmId;

exec
/bin/bash -lc 'rg -n "cache|invalidate|iaImageIds|iaAnnotationIds|Phase 0|Phase B" docs/plans/2026-05-18-wbia-image-registration-design.md docs/plans/2026-05-18-empty-match-prospects-design.md' in /mnt/c/Wildbook-clean2
 succeeded in 292ms:
docs/plans/2026-05-18-empty-match-prospects-design.md:73:| `WildbookIAM.iaImageIds(context)` / `sendMediaAssetsForceId(…)` / `mediaAssetToUri(…)` / `validMediaAsset(…)` | `WildbookIAM.java` | Existing helpers the WBIA-side design reuses. See the paused WBIA design doc for the helper-by-helper audit. |
docs/plans/2026-05-18-empty-match-prospects-design.md:94:2. The strict `iaImageIdsStrict` variant introduces caching that the
docs/plans/2026-05-18-empty-match-prospects-design.md:95:   lenient `iaImageIds` doesn't currently have. The new cache key is
docs/plans/2026-05-18-empty-match-prospects-design.md:96:   `iaImageIdsStrict` (15-min QueryCache, matching the
docs/plans/2026-05-18-empty-match-prospects-design.md:97:   `iaAnnotationIdsStrict` pattern from the c11 fix-pass). The
docs/plans/2026-05-18-empty-match-prospects-design.md:98:   lenient variant stays cache-free for backward compatibility.
docs/plans/2026-05-18-empty-match-prospects-design.md:104:4. Phase B does NOT re-validate (no DTO trust-boundary check for MA
docs/plans/2026-05-18-empty-match-prospects-design.md:106:   Phase A commit and Phase B HTTP call. Documented in the design as
docs/plans/2026-05-18-empty-match-prospects-design.md:114:   distinguishing the phase via the existing log string (Phase 0 vs
docs/plans/2026-05-18-empty-match-prospects-design.md:304:gains coverage for the Phase 0 image POST, the cache invalidation
docs/plans/2026-05-18-wbia-image-registration-design.md:14:- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-wbia-image-registration-design.md:111:Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
docs/plans/2026-05-18-wbia-image-registration-design.md:121:parser helper is duplicated rather than generalized because the cache key
docs/plans/2026-05-18-wbia-image-registration-design.md:170:### Phase B `registerOneByDto` modification
docs/plans/2026-05-18-wbia-image-registration-design.md:172:Add Phase 0 at the entry; existing Phase 1 (annotation registration)
docs/plans/2026-05-18-wbia-image-registration-design.md:179:    // ---- Phase 0: ensure WBIA knows about the image ----
docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-wbia-image-registration-design.md:190:        // Invalidate the image-ids cache so the next annotation on this
docs/plans/2026-05-18-wbia-image-registration-design.md:191:        // image sees the updated list — without this, the 15-min cache
docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-wbia-image-registration-design.md:199:    try { knownAnnots = iaAnnotationIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-wbia-image-registration-design.md:217:Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-wbia-image-registration-design.md:226:15 minutes) would still see the cached image-ids list (which didn't
docs/plans/2026-05-18-wbia-image-registration-design.md:227:include this image), trigger Phase 0 again, attempt to register the
docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-wbia-image-registration-design.md:235:per image will see stale cache. Trade-off is minor since the cache TTL is
docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-wbia-image-registration-design.md:261:  iaAnnotationIdsStrict called after (Phase 1 reached).
docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-wbia-image-registration-design.md:283:| Image POST succeeds + cache invalidation not supported | Functional but mildly wasteful | Per-annotation re-check until cache TTL expires |
docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-wbia-image-registration-design.md:298:   expose a way to remove a single named cached query? If not, the
docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
docs/plans/2026-05-18-wbia-image-registration-design.md:312:   Phase A's eligibility check rather than Phase B. Acceptable, or
docs/plans/2026-05-18-wbia-image-registration-design.md:313:   should Phase B also defensively check (in case state changed between
docs/plans/2026-05-18-wbia-image-registration-design.md:314:   Phase A and Phase B)? The window is short (Phase A commits, Phase B
docs/plans/2026-05-18-wbia-image-registration-design.md:316:   pattern suggests Phase B could re-check via DTO field. My judgment is

exec
/bin/bash -lc 'rg -n "class QueryCache|removeCached|invalidate|getQueryByName|addCachedQuery" src/main/java/org/ecocean/cache src/main/java/org/ecocean | head -120' in /mnt/c/Wildbook-clean2
 succeeded in 802ms:
src/main/java/org/ecocean/cache/StoredQuery.java:32:    // FUTURE: invalidateAfter - number of milliseconds after the lastExecuted time after which the stored results are invalidated and the query must
src/main/java/org/ecocean/cache/StoredQuery.java:103:       FUTURE: setCachedResult FUTURE: getCachedResult FUTURE: invalidateCachedResult - immediately invalidates cachedResult FUTURE:
src/main/java/org/ecocean/cache/StoredQuery.java:104:          setInvalidateAfter Value of -1 never invalidates cachedResult if not null FUTURE: executeQuery
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:22:    public CachedQuery getQueryByName(String name) {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCache.java:49:            cachedQueries.get(name).invalidate();
src/main/java/org/ecocean/cache/QueryCache.java:53:    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
src/main/java/org/ecocean/cache/QueryCache.java:62:    public void addCachedQuery(CachedQuery cq) {
src/main/java/org/ecocean/cache/StoredQuery.java:32:    // FUTURE: invalidateAfter - number of milliseconds after the lastExecuted time after which the stored results are invalidated and the query must
src/main/java/org/ecocean/cache/StoredQuery.java:103:       FUTURE: setCachedResult FUTURE: getCachedResult FUTURE: invalidateCachedResult - immediately invalidates cachedResult FUTURE:
src/main/java/org/ecocean/cache/StoredQuery.java:104:          setInvalidateAfter Value of -1 never invalidates cachedResult if not null FUTURE: executeQuery
src/main/java/org/ecocean/cache/CachedQuery.java:206:    public synchronized void invalidate()
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:22:    public CachedQuery getQueryByName(String name) {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCache.java:49:            cachedQueries.get(name).invalidate();
src/main/java/org/ecocean/cache/QueryCache.java:53:    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
src/main/java/org/ecocean/cache/QueryCache.java:62:    public void addCachedQuery(CachedQuery cq) {
src/main/java/org/ecocean/identity/IAQueryCache.java:59:        CachedQuery q = qc.getQueryByName(qname);
src/main/java/org/ecocean/identity/IAQueryCache.java:62:            qc.addCachedQuery(q);
src/main/java/org/ecocean/identity/IAQueryCache.java:83:        CachedQuery q = qc.getQueryByName(qname);
src/main/java/org/ecocean/cache/CachedQuery.java:206:    public synchronized void invalidate()
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:45:            CachedQuery existingCache = qc.getQueryByName(cacheName);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:69:            CachedQuery existingCache = qc.getQueryByName(cacheName);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:92:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        if (qc != null && qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:490:            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:493:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:515:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:704:            if (qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:706:                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:708:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:718:                    qc.addCachedQuery(cq);
src/main/java/org/ecocean/api/Logout.java:82:                httpSession.invalidate();
src/main/java/org/ecocean/api/Logout.java:83:                ThreadContext.put("action", "logout_http_session_invalidated");
src/main/java/org/ecocean/api/Logout.java:84:                logger.debug("Logout attempt with http session invalidated");
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4394:                if (qc.getQueryByName(("numRecentEncounters_" + thisUser)) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4395:                    CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + thisUser));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4404:                    qc.addCachedQuery(cq);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4431:            if (qc.getQueryByName(("numRecentEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4432:                CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + user.getUUID()));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4443:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4472:            if (qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4473:                CachedQuery cq = qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID()));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4484:                qc.addCachedQuery(cq);
src/main/java/org/ecocean/servlet/LogoutUser.java:55:            session.invalidate();

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/cache/QueryCache.java | sed -n '1,70p'" in /mnt/c/Wildbook-clean2
 succeeded in 283ms:
     1	package org.ecocean.cache;
     2	
     3	import java.io.IOException;
     4	import java.util.HashMap;
     5	import java.util.List;
     6	import java.util.Map;
     7	
     8	import org.ecocean.shepherd.core.Shepherd;
     9	import org.json.JSONObject;
    10	
    11	// A singleton responsible for storing, retrieving, and eventually executing queries that are desired for caching. It will have all necessary getters
    12	// and setters for dealing with the cache.
    13	public class QueryCache {
    14	    private Map<String, CachedQuery> cachedQueries;
    15	    private String context = null;
    16	
    17	    public QueryCache() {}
    18	    public QueryCache(String context) {
    19	        this.context = context;
    20	    }
    21	
    22	    public CachedQuery getQueryByName(String name) {
    23	        if (cachedQueries == null) loadQueries();
    24	        return cachedQueries.get(name);
    25	    }
    26	
    27	    public Map<String, CachedQuery> cachedQueries() { return cachedQueries; }
    28	
    29	    public void loadQueries() {
    30	        if (context == null)
    31	            throw new RuntimeException("QueryCache.loadQueries() called with context null");
    32	        cachedQueries = new HashMap<String, CachedQuery>();
    33	        Shepherd myShepherd = new Shepherd(context);
    34	        myShepherd.beginDBTransaction();
    35	
    36	        List<StoredQuery> queries = myShepherd.getAllStoredQueries();
    37	        int numQueries = queries.size();
    38	        for (int i = 0; i < numQueries; i++) {
    39	            StoredQuery sq = queries.get(i);
    40	            cachedQueries.put(sq.getName(), sq.getCachedQueryCopy());
    41	        }
    42	        myShepherd.rollbackDBTransaction();
    43	        myShepherd.closeDBTransaction();
    44	    }
    45	
    46	    public void invalidateByName(String name)
    47	    throws IOException {
    48	        if (cachedQueries.containsKey(name)) {
    49	            cachedQueries.get(name).invalidate();
    50	        }
    51	    }
    52	
    53	    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
    54	        Shepherd myShepherd) {
    55	        // create CachedQuery object
    56	        CachedQuery cq = new CachedQuery(name, jsonobj, persistAsStoredQuery, myShepherd);
    57	
    58	        // put on HashMap
    59	        cachedQueries.put(name, cq);
    60	    }
    61	
    62	    public void addCachedQuery(CachedQuery cq) {
    63	        cachedQueries.put(cq.getName(), cq);
    64	    }
    65	}

exec
/bin/bash -lc "rg -n \"getMediaAssets\\(|getAnnotations\\(\" src/main/java/org/ecocean/servlet/importer/ImportTask.java src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/media/MediaAsset.java | head -80 && nl -ba src/main/java/org/ecocean/servlet/importer/ImportTask.java | sed -n '180,220p'" in /mnt/c/Wildbook-clean2
 succeeded in 324ms:
src/main/java/org/ecocean/media/MediaAsset.java:621:    public ArrayList<Annotation> getAnnotations() {
src/main/java/org/ecocean/media/MediaAsset.java:632:        return (getAnnotations().size() > 0);
src/main/java/org/ecocean/media/MediaAsset.java:636:        return getAnnotations().size();
src/main/java/org/ecocean/media/MediaAsset.java:642:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:650:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:658:        List<Annotation> ord = new ArrayList<Annotation>(this.getAnnotations());
src/main/java/org/ecocean/media/MediaAsset.java:676:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:1454:        for (Annotation ann : this.getAnnotations()) {
src/main/java/org/ecocean/media/MediaAsset.java:1556:        List<Annotation> annots = this.getAnnotations();
src/main/java/org/ecocean/media/MediaAsset.java:1742:        for (Annotation ann : this.getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:462:        setAnnotations(Util.combineArrayListsInPlace(getAnnotations(), enc2.getAnnotations()));
src/main/java/org/ecocean/Encounter.java:514:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:573:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:2842:    public ArrayList<Annotation> getAnnotations() {
src/main/java/org/ecocean/Encounter.java:2879:    public List<Annotation> getAnnotations(MediaAsset ma) {
src/main/java/org/ecocean/Encounter.java:2882:        for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:2905:        if (getAnnotations() != null && getAnnotations().size() >= 1) {
src/main/java/org/ecocean/Encounter.java:2906:            for (Annotation ann : getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:3420:        if ((this.getAnnotations() != null) && (this.getAnnotations().size() > 0)) {
src/main/java/org/ecocean/Encounter.java:3423:            for (Annotation ann : this.getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:3465:        if ((this.getAnnotations() != null) && (this.getAnnotations().size() > 0)) {
src/main/java/org/ecocean/Encounter.java:3466:            for (Annotation ann : this.getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:4315:                for (Annotation ann : ma.getAnnotations()) {
src/main/java/org/ecocean/Encounter.java:4661:                        for (Annotation ann : enc.getAnnotations()) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:111:    public Set<Annotation> getAnnotations() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:116:                if (enc.getAnnotations() != null)
src/main/java/org/ecocean/servlet/importer/ImportTask.java:117:                    for (Annotation ann : enc.getAnnotations()) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:127:        for (Annotation ann : this.getAnnotations()) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:182:    public List<MediaAsset> getMediaAssets() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:559:                ArrayList<Annotation> anns = enc.getAnnotations();
src/main/java/org/ecocean/servlet/importer/ImportTask.java:646:        if (this.getMediaAssets() != null)
src/main/java/org/ecocean/servlet/importer/ImportTask.java:647:            numAssets = this.getMediaAssets().size();
src/main/java/org/ecocean/servlet/importer/ImportTask.java:648:        for (MediaAsset ma : this.getMediaAssets()) {
   180	    }
   181	
   182	    public List<MediaAsset> getMediaAssets() {
   183	        if (encounters == null) return null;
   184	        List<MediaAsset> mas = new ArrayList<MediaAsset>();
   185	        for (Encounter enc : encounters) {
   186	            ArrayList<MediaAsset> encMAs = enc.getMedia();
   187	            if (Util.collectionSize(encMAs) > 0)
   188	                for (MediaAsset ma : encMAs) {
   189	                    if (!mas.contains(ma)) mas.add(ma); // dont want duplicates
   190	                }
   191	        }
   192	        return mas;
   193	    }
   194	
   195	    public List<Occurrence> getOccurrences(Shepherd myShepherd) {
   196	        if (encounters == null) return null;
   197	        List<Occurrence> occs = new ArrayList<Occurrence>();
   198	        for (Encounter enc : encounters) {
   199	            String occId = enc.getOccurrenceID();
   200	            if (occId == null) continue;
   201	            Occurrence occ = myShepherd.getOccurrence(occId);
   202	            if (occ != null) occs.add(occ);
   203	        }
   204	        return occs;
   205	    }
   206	
   207	    public void setParameters(String s) {
   208	        parameters = s;
   209	    }
   210	
   211	    public void setParameters(JSONObject j) {
   212	        if (j == null) {
   213	            parameters = null;
   214	        } else {
   215	            parameters = j.toString();
   216	        }
   217	    }
   218	
   219	    public String getParametersAsString() {
   220	        return parameters;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '250,315p'" in /mnt/c/Wildbook-clean2
 succeeded in 316ms:
   250	
   251	    public void setChildren(List<Task> kids) {
   252	        if (kids == null) {
   253	            children = null;
   254	            return;
   255	        }
   256	        children = new ArrayList<Task>();
   257	        for (Task kid : kids) {
   258	            this.addChild(kid); // let this do the work
   259	        }
   260	    }
   261	
   262	    public List<Task> addChild(Task kid) {
   263	        if (children == null) children = new ArrayList<Task>();
   264	        if (kid == null) return children;
   265	        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
   266	        if (!children.contains(kid)) children.add(kid);
   267	        return children;
   268	    }
   269	
   270	    public void setParent(Task t) {
   271	        parent = t;
   272	        t.addChild(this);
   273	    }
   274	
   275	    public Task getParent() {
   276	        return parent;
   277	    }
   278	
   279	    public String getParentId() {
   280	        if (parent == null) return null;
   281	        return parent.getId();
   282	    }
   283	
   284	    public int numChildren() {
   285	        return (children == null) ? 0 : children.size();
   286	    }
   287	
   288	    public boolean hasChildren() {
   289	        return (this.numChildren() > 0);
   290	    }
   291	
   292	    // omg i am going to assume no looping
   293	    public List<Task> getLeafTasks() {
   294	        List<Task> leaves = new ArrayList<Task>();
   295	
   296	        if (!this.hasChildren()) {
   297	            leaves.add(this);
   298	            return leaves;
   299	        }
   300	        for (Task kid : children) {
   301	            leaves.addAll(kid.getLeafTasks());
   302	        }
   303	        return leaves;
   304	    }
   305	
   306	    public Task getRootTask() {
   307	        if (parent == null) return this;
   308	        return parent.getRootTask();
   309	    }
   310	
   311	    public Task deepContains(Annotation ann) {
   312	        if (this.contains(ann)) return this;
   313	        if (!this.hasChildren()) return null;
   314	        for (Task kid : children) {
   315	            Task found = kid.deepContains(ann);

codex
**Findings**

1. **Blocker: the proposed import expansion cannot wait for annotations that do not exist yet.**  
   `waitAndRunMatch` runs immediately after one job persists its own detections ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:290)). If sibling ml-service jobs are still in HTTP or have not persisted, their annotation IDs are not in JDO yet, so `ImportTask -> Encounter -> Annotation` expansion cannot include them. This only fixes the narrower case where siblings are already in DB but not yet visible in OpenSearch. The actual “parallel per-image jobs” race still remains. You need an import/species-level barrier, or a “not all sibling media assets are terminal yet, defer and retry later” check before collecting IDs.

2. **Blocker: `ImportTask -> Encounter -> Annotation` will include bulk-import placeholder annotations.**  
   Bulk import creates initial annotations and marks them `skipAutoIndexing` ([BulkImporter.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:703)). Those rows are joined through `ENCOUNTER_ANNOTATIONS`, but they are not the ml-service detections and may never appear in OpenSearch because lifecycle indexing skips `skipAutoIndexing` objects ([WildbookLifecycleListener.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/WildbookLifecycleListener.java:53)). If the visibility set includes them, `waitForVisibility` will time out and fall into the deferred path for normal successful imports. The expansion query must filter to match-eligible ml-service annotations, at minimum `matchAgainst=true`, `acmId is not null`, and preferably “has embedding for this model/method” or `predictModelId is not null`.

3. **Major: the rejected barrier is closer to the needed shape than the chosen seam.**  
   The chosen seam widens visibility but does not know when the batch is complete. A practical fix could still live in `waitAndRunMatch`, but it needs two phases: first wait/defer until the sibling media assets for the current task group are in terminal detection states, then collect eligible annotation IDs and call `waitForVisibility`. The current per-asset task has a parent created with `new Task(topTask)` ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:281)), and that parent carries the same-species media asset group; that is probably a tighter boundary than `ImportTask`, which can be mixed-species and encounter-leaky.

4. **Major: deferred matching should reuse the same gate, with a retry cap.**  
   Leaving `runDeferredMatch` as a direct `runMatchProspects` call ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:433)) makes the first timeout degrade back to today’s bug. If the initial timeout was caused by sibling jobs not being terminal or OS not being caught up, the deferred job needs to re-check the batch gate. Add an attempt counter/backoff or max age so failed siblings do not cause infinite deferral.

5. **Major: the planned tests need a testability refactor.**  
   `waitAndRunMatch` is private and constructs `new OpenSearch()` directly ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:418)), while `enqueueDeferredMatch` is private. The proposed mock/wrap tests are not feasible cleanly without changing the seam. Add an injectable visibility client/factory and make the expansion/gating helper package-visible, or extract a small collaborator that can be unit-tested without live JDO/OpenSearch.

6. **Medium: Track 1 cache naming is inconsistent.**  
   The combined doc says the strict image cache key is `iaImageIdsStrict`, but the older WBIA doc still says invalidate `iaImageIds`. The actual cache API is `invalidateByName` ([QueryCache.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCache.java:46)). The implementation should consistently invalidate `iaImageIdsStrict` after successful image POST. I would also consider invalidating `iaAnnotationIds` after successful annotation POST to avoid stale-cache duplicate POSTs if Phase C fails or races.

**Open Questions**

1. **Performance:** do not cap the set for correctness. Use a scalar `SELECT DISTINCT` query returning only eligible annotation IDs rather than materializing `ImportTask -> Encounter -> Annotation` objects. Log above 5k; consider chunking only if OpenSearch rejects very large `ids` payloads.

2. **Deferred path:** yes, widen/gate it too. Otherwise timeout is just a delayed version of the current bug.

3. **Failed import state:** don’t key this off `ImportTask.status` alone. Gate on actual media asset detection terminal states and eligible annotation existence. Failed/zero-detection siblings should not contribute IDs to the visibility set.

4. **Deduplication:** yes, keep `LinkedHashSet`, and use SQL `DISTINCT`. But dedupe does not solve the bigger issue that encounter joins cannot prove annotation import ownership.

5. **Other gap:** `Annotation.getMatchingSetQuery` excludes the query annotation’s own encounter and requires `matchAgainst` plus `acmId` ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1001)). The visibility set should align with that eligibility, or it will wait on annotations that could never be candidates anyway.

**Bottom Line**

Track 1 is directionally fine with the cache-name fix. Track 2’s diagnosis is right, but the chosen implementation is not sufficient: it needs a real batch-completion gate and an eligible-annotation filter before the OpenSearch visibility wait.
tokens used
157,072
**Findings**

1. **Blocker: the proposed import expansion cannot wait for annotations that do not exist yet.**  
   `waitAndRunMatch` runs immediately after one job persists its own detections ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:290)). If sibling ml-service jobs are still in HTTP or have not persisted, their annotation IDs are not in JDO yet, so `ImportTask -> Encounter -> Annotation` expansion cannot include them. This only fixes the narrower case where siblings are already in DB but not yet visible in OpenSearch. The actual “parallel per-image jobs” race still remains. You need an import/species-level barrier, or a “not all sibling media assets are terminal yet, defer and retry later” check before collecting IDs.

2. **Blocker: `ImportTask -> Encounter -> Annotation` will include bulk-import placeholder annotations.**  
   Bulk import creates initial annotations and marks them `skipAutoIndexing` ([BulkImporter.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:703)). Those rows are joined through `ENCOUNTER_ANNOTATIONS`, but they are not the ml-service detections and may never appear in OpenSearch because lifecycle indexing skips `skipAutoIndexing` objects ([WildbookLifecycleListener.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/WildbookLifecycleListener.java:53)). If the visibility set includes them, `waitForVisibility` will time out and fall into the deferred path for normal successful imports. The expansion query must filter to match-eligible ml-service annotations, at minimum `matchAgainst=true`, `acmId is not null`, and preferably “has embedding for this model/method” or `predictModelId is not null`.

3. **Major: the rejected barrier is closer to the needed shape than the chosen seam.**  
   The chosen seam widens visibility but does not know when the batch is complete. A practical fix could still live in `waitAndRunMatch`, but it needs two phases: first wait/defer until the sibling media assets for the current task group are in terminal detection states, then collect eligible annotation IDs and call `waitForVisibility`. The current per-asset task has a parent created with `new Task(topTask)` ([IA.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:281)), and that parent carries the same-species media asset group; that is probably a tighter boundary than `ImportTask`, which can be mixed-species and encounter-leaky.

4. **Major: deferred matching should reuse the same gate, with a retry cap.**  
   Leaving `runDeferredMatch` as a direct `runMatchProspects` call ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:433)) makes the first timeout degrade back to today’s bug. If the initial timeout was caused by sibling jobs not being terminal or OS not being caught up, the deferred job needs to re-check the batch gate. Add an attempt counter/backoff or max age so failed siblings do not cause infinite deferral.

5. **Major: the planned tests need a testability refactor.**  
   `waitAndRunMatch` is private and constructs `new OpenSearch()` directly ([MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:418)), while `enqueueDeferredMatch` is private. The proposed mock/wrap tests are not feasible cleanly without changing the seam. Add an injectable visibility client/factory and make the expansion/gating helper package-visible, or extract a small collaborator that can be unit-tested without live JDO/OpenSearch.

6. **Medium: Track 1 cache naming is inconsistent.**  
   The combined doc says the strict image cache key is `iaImageIdsStrict`, but the older WBIA doc still says invalidate `iaImageIds`. The actual cache API is `invalidateByName` ([QueryCache.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCache.java:46)). The implementation should consistently invalidate `iaImageIdsStrict` after successful image POST. I would also consider invalidating `iaAnnotationIds` after successful annotation POST to avoid stale-cache duplicate POSTs if Phase C fails or races.

**Open Questions**

1. **Performance:** do not cap the set for correctness. Use a scalar `SELECT DISTINCT` query returning only eligible annotation IDs rather than materializing `ImportTask -> Encounter -> Annotation` objects. Log above 5k; consider chunking only if OpenSearch rejects very large `ids` payloads.

2. **Deferred path:** yes, widen/gate it too. Otherwise timeout is just a delayed version of the current bug.

3. **Failed import state:** don’t key this off `ImportTask.status` alone. Gate on actual media asset detection terminal states and eligible annotation existence. Failed/zero-detection siblings should not contribute IDs to the visibility set.

4. **Deduplication:** yes, keep `LinkedHashSet`, and use SQL `DISTINCT`. But dedupe does not solve the bigger issue that encounter joins cannot prove annotation import ownership.

5. **Other gap:** `Annotation.getMatchingSetQuery` excludes the query annotation’s own encounter and requires `matchAgainst` plus `acmId` ([Annotation.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1001)). The visibility set should align with that eligibility, or it will wait on annotations that could never be candidates anyway.

**Bottom Line**

Track 1 is directionally fine with the cache-name fix. Track 2’s diagnosis is right, but the chosen implementation is not sufficient: it needs a real batch-completion gate and an eligible-annotation filter before the OpenSearch visibility wait.
