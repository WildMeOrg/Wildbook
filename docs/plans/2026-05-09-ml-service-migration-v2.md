# ml-service Migration: Design Proposal v2

**Date:** 2026-05-09 (revised 2026-05-10 post Codex review #1)
**Branch:** `migrate-ml-service-v2`
**Status:** Design — approved for implementation per commit #2 onward
**Predecessor:** `migrate-ml-service` (v1 attempt; 11 commits; preserved as reference)

## Goal

Replace WBIA's role for detection, embedding extraction, and embedding-based
identification with calls to the FastAPI ml-service. Keep WBIA only for
HotSpotter spot-pattern matching. Make WBIA non-blocking on the new critical
path: upload → ml-service → persist → OpenSearch knn → matches.

## Why a v2

The v1 attempt landed 11 commits including the load-bearing pieces, then
Codex's first code review (after the fact) found six runtime bugs and four
contract drifts. A fix-pass commit addressed most but introduced a
fundamental shared-task lifecycle defect. Verdict: the per-commit pattern of
"write 800 lines, then ask for review" was too coarse; bugs piled up.

v2 inverts the workflow: each commit goes design-note → Codex review →
implement → Codex code review → commit. Smaller commits (~100–250 lines).
Codex sees a Wildbook context bundle on every prompt so it has the same
gotchas the assistant has loaded.

## v1 lessons informing v2

| v1 antipattern | v2 fix |
|---|---|
| Single 810-line plugin class | Split into single-responsibility classes (HTTP client, processor, outcome record) |
| Shared top-level Task across per-asset queue jobs; first-finisher marked the whole batch terminal | Per-asset child Tasks under a retained topTask via real `setParent`/`addChild`; child finalization is local; topTask still holds all MAs for summary code |
| Mutable instance field for outcome propagation (`lastPersistOutcome`) | Methods return typed outcome (record/enum); no instance state |
| Routing on `_mlservice_conf` presence picked vector configs even when species default was HotSpotter | `_id_conf.default.pipeline_root == "vector"` is the ONLY switch (with `_mlservice_conf` also configured) |
| Synchronous `OpenSearch._refresh` after persist; matching ran before reindex completed | Real bounded poll-and-wait on knn-eligibility; deferred-match fallback on timeout |
| Mutating tasks from closed-tx Shepherd objects | Update task state from a fresh Shepherd transaction |
| `"HTTP error code = 502"` vs `": 502"` retry-classification typo | Match both spellings; verify against `RestClient.postRaw` actual throw text |
| `new Feature("org.ecocean.boundingBox", ...)` without prior `FeatureType.initAll` | Initialize at processor entry |
| Implicit `_mlservice_conf` appending in `IAJsonProperties.getIdentConfig` | `_id_conf.default.pipeline_root` is the single decision point; no implicit append |
| `wbiaRegister` queue with FileQueue at-most-once + manual reconcile servlet | DB-backed via `Annotation.wbiaRegistered`; periodic background thread does the polling and registration; no separate FileQueue |
| `bboxHash` as sha1 (opaque, hides rounding bugs) | Literal normalized key `"x:y:w:h"` (debuggable, no semantic loss) |
| Wrote code first, asked review after | Codex review at design *and* code level, every commit |

## Architecture

### Critical path (per-image)

```
Upload / Bulk Import / Add image
        │
        ▼
  Encounter + MediaAsset persisted (MediaAsset.acmId = uuid; existing)
        │
        ▼
  IA.intakeMediaAssetsOneSpecies routes per species:
    IAJsonProperties.getActiveMlServiceConfigs(taxy) returns non-null
       (i.e. _id_conf.default.pipeline_root == "vector" AND
        _mlservice_conf is populated)             → ml-service path
    else                                           → existing legacy WBIA path
        │
        ▼ (ml-service path; per-asset child Task on detection queue)
  MlServiceProcessor.process(jobPayload)
        │   load + revalidate (Shepherd open, no network)
        │   call MlServiceClient.pipeline(uri, conf) (no Shepherd held)
        │   validate response (no Shepherd)
        │   re-load + idempotent persist (Shepherd open):
        │     - Annotation: acmId = id; matchAgainst = true;
        │       mediaAsset set explicitly (so MEDIAASSET_ID_OID populated
        │       for the partial unique index to work);
        │       status = complete-mlservice; wbiaRegistered = false (so background poll picks it up)
        │     - Embedding: from same response
        │     - per-asset child Task: completed | error | dropped-stale
        │     - MediaAsset.detectionStatus = complete-mlservice
        │   commit
        ▼
  OpenSearch.waitForVisibility(annotationIndex, ids, timeout)
        │   refresh index; poll knn-eligibility query for ids; bounded
        │   sleep+retry; returns true|false. Does NOT try to drain the
        │   IndexingManager queue (which may contain unrelated entities).
        ▼
  if visible: Embedding.findMatchProspects → MatchResult per annotation
  if timed out: enqueue {deferredMatch:true, ids} on existing IA queue;
                deferred handler retries waitForVisibility with longer ceiling
                and runs match when visible.

[Independent of the critical path:]
  Background thread (every ~30s):
        SELECT … FROM "ANNOTATION"
         WHERE "WBIAREGISTERED" = false
           AND "WBIAREGISTERATTEMPTS" < MAX_ATTEMPTS  -- e.g. 10
         ORDER BY "WBIAREGISTERATTEMPTS" ASC, created ASC
         LIMIT N
        Call WildbookIAM.sendAnnotationsForceId
        On success: setWbiaRegistered(true)
        On failure: incrementWbiaRegisterAttempts(); leaves wbiaRegistered=false;
                    will retry next cycle (with attempts-ascending priority)
                    until MAX_ATTEMPTS, then row is excluded from polling
                    until operator manually resets attempts to 0.
  → HotSpotter remains available for ml-service-produced annotations.
  → No queue infrastructure; the columns ARE the durable queue. Restart-safe.
  → Legacy annotations are excluded from polling because the JDO migration
     backfills them with wbiaRegistered=true (they were already registered
     with WBIA via the historical IBEISIA flow).
```

### Class decomposition

| Class | Role | Size |
|---|---|---|
| `org.ecocean.ia.MlServiceClient` (new) | HTTP-only wrapper around `/pipeline/` and `/extract/`. Validates response shape. No Shepherd, no DB. | ~150 |
| `org.ecocean.ia.MlServiceProcessor` (new) | Single-job orchestrator: load+revalidate, call client, persist, kick off match. Owns the Shepherd lifecycle. Returns typed outcome. | ~250 |
| `org.ecocean.ia.MlServiceJobOutcome` (new) | Typed outcome enum/record returned by processor and persistence helpers. | ~30 |
| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
| `org.ecocean.IAJsonProperties` (modified) | New accessor `getActiveMlServiceConfigs(taxy)` (returns the configs IFF `_id_conf.default.pipeline_root == "vector"` AND `_mlservice_conf` populated; else null). New `getPipelineRoot(taxy)`. Remove implicit `_mlservice_conf` appending in `getIdentConfig`. | +80 |
| `org.ecocean.Embedding.findMatchProspects` (modified) | Drop the `api_endpoint` requirement; read method/version from the `_id_conf` entry directly; subtask-failure does not prematurely complete parent; clone-not-mutate `matchingSetQuery`. | +40 |
| `org.ecocean.Annotation` (modified) | New fields: `predictModelId`, `bboxKey`, `thetaKey`, `wbiaRegistered`, `wbiaRegisterAttempts`. Literal `bboxKey`/`thetaKey`, not hashed. `wbiaRegistered` Boolean (null on legacy → backfilled to true; false on new ml-service → polled by background thread; true on success — terminal). `wbiaRegisterAttempts` int counts failed attempts; ≥ MAX cuts off polling. | +80 |
| `org.ecocean.RestClient` (modified) | `postJSON` overload with separate connect/read timeouts. | +20 |
| `org.ecocean.servlet.IAGateway.processQueueMessage` (modified) | New dispatcher branch routing `mlServiceV2:true` payloads to `MlServiceProcessor`. Distinct from legacy `MLService:true`. | +15 |
| `org.ecocean.ia.IA.intakeMediaAssetsOneSpecies` (modified) | Per-species routing decision via `IAJsonProperties.getActiveMlServiceConfigs`. ml-service path creates per-asset child Tasks under the topTask (real parent/child via `setParent`/`addChild`); legacy WBIA path unchanged. | +50 |
| `org.ecocean.StartupWildbook` (modified) | New scheduled background thread polls `Annotation.wbiaRegistered = false AND wbiaRegisterAttempts < MAX` for WBIA registration. | +60 |
| `org.ecocean.ia.MatchResult.annotationDetails` (modified) | Expose `wbiaRegistered` so frontend can disable HotSpotter button until WBIA acknowledges. | +5 |
| `frontend/src/pages/Encounter/pollingHelpers.js` (modified) | `isTerminalDetectionStatus` recognizes `complete-mlservice`. | +5 |
| `frontend/src/pages/Encounter/stores/EncounterStore.js` (modified) | Match-results enablement check recognizes `complete-mlservice` alongside `complete`/`pending`. | +3 |
| `frontend/src/pages/Encounter/ImageCard.jsx` (modified) | Same recognition for the image-card UI gating. | +3 |
| `frontend/src/pages/SearchPages/stores/ImageModalStore.js` (modified) | Same recognition for search-page modal gating. | +3 |

Total: 3 new classes, 10 modified existing files. Each new class independently reviewable.

### What v2 explicitly does NOT do at MVP

- Bespoke "plugin" abstraction. v2 just adds classes; no `IAPlugin` inheritance.
- Implicit ident-config appending. The `_id_conf.default.pipeline_root` field is the only switch.
- Shared task state across per-asset jobs.
- Separate `MlServiceActivationCheck` admin servlet. Activation precondition checks are SQL the operator runs manually.
- Pending-species retroactive trigger. Users re-edit and re-trigger; PATCH-endpoint hook is a follow-up.
- Frontend "Match with HotSpotter" button. Existing UI for HotSpotter still works through the legacy `IBEISIA` path; the disabled-state polish is a follow-up.
- A separate `wbiaRegister` queue. The DB column IS the durable queue.
- IA.json `${VAR}` env-var substitution. Per-deployment private repo files solve the same problem more simply.

## State semantics

### `MediaAsset.detectionStatus`

| Status | Set by | Counted as detection-complete? |
|---|---|---|
| `pending-species` | new (commit #5): upload had no taxonomy / unconfigured species | no |
| `processing-mlservice` | new (commit #5): set on enqueue success | no |
| `complete-mlservice` | new (commit #5): processor persist commit | yes |
| `complete` (legacy WBIA) | unchanged | yes |
| `pending` (legacy review) | unchanged | yes |
| `error` | terminal failure | no |
| `dropped-stale` | new: target deleted mid-flight | no (excluded from total) |

### `Annotation.identificationStatus`

`STATUS_PROCESSING_MLSERVICE` and `STATUS_COMPLETE_MLSERVICE` constants exist on main; reused.

### `Task.status`

Existing `completed` / `error` plus new `dropped-stale` (added to `Task.statusInEndState`).

### Terminality consumers (commit #5 must update each)

- `ImportTask.iaSummaryJson:629` (the equivalent line on main; v1's branch had a similar version): count `complete-mlservice` in detection-complete tally.
- Any other backend code that hard-codes `getDetectionStatus().equals("complete")` (search and update).
- `frontend/src/pages/Encounter/pollingHelpers.js`: `isTerminalDetectionStatus` recognizes `complete-mlservice`.
- `frontend/src/pages/Encounter/stores/EncounterStore.js`: match-results / annotation status checks recognize `complete-mlservice`.
- `frontend/src/pages/Encounter/ImageCard.jsx`: same.
- `frontend/src/pages/SearchPages/stores/ImageModalStore.js`: same.
- `Task.statusInEndState`: add `dropped-stale`.
- `MediaAsset.setDetectionStatus`: bump `REVISION` (calls `setRevision()`) so direct-SQL-style timestamp queries work and OpenSearch reindex can pick up detection-status changes.
- The `TERMINAL_DETECTION_STATUSES` constant from the bulk-import-reid-race fix exists on `migrate-ml-service` but NOT on `main`; v2 either adds the constant ourselves (locally to `ImportTask`) or accepts that the bulk-import-fix PR will land first/separately and rebase on top. The plan keeps v2 standalone — v2 introduces its own status checks where needed without depending on the bulk-import fix's constant.

## IA.json schema (already nested per `IAJsonProperties.getAllTaxonomyStrings`)

```json
{
  "Rhincodon": {
    "typus": {
      "_mlservice_conf": [
        {
          "predict_model_id": "msv3",
          "extract_model_id": "miewid-msv4.1",
          "classify_model_id": "efficientnet-classifier",
          "orientation_model_id": "densenet-orientation",
          "embedding_dimension": 2152,
          "api_endpoint": "https://ml-service.example.org"
        }
      ],
      "_default": {
        "_id_conf": [
          { "default": true, "method": "miewid", "version": "4.1", "pipeline_root": "vector" },
          { "available_on_request": true, "pipeline_root": "HotSpotter" }
        ]
      }
    }
  }
}
```

### Schema rules

- One canonical IA.json per deployment in a private repo.
- Routing: `IAJsonProperties.getActiveMlServiceConfigs(taxy)` returns the
  `_mlservice_conf` array IFF `_id_conf.default.pipeline_root == "vector"`
  AND `_mlservice_conf` is configured. Else null. **`_id_conf.default.pipeline_root`
  is the only switch.** A species with `_mlservice_conf` but `pipeline_root == "HotSpotter"` does NOT route to ml-service.
- Malformed entry for a species disables that species (logged ERROR), others unaffected.
- `embedding_dimension` must match the live OpenSearch annotation index; mismatch disables the species (verified at activation, not boot).

## Idempotency

Schema additions to `Annotation` (commit #4):

| Column | Type | Notes |
|---|---|---|
| `PREDICTMODELID` | varchar(100), nullable | the ml-service `predict_model_id` |
| `BBOXKEY` | varchar(64), nullable | literal normalized `"x:y:w:h"` of rounded ints |
| `THETAKEY` | varchar(20), nullable | theta rounded to 4 decimals, formatted as string |
| `WBIAREGISTERED` | boolean, nullable | true = WBIA acknowledged (terminal success). false = needs registration / transient failure (polled by background thread). null = legacy annotation, pre-existing-and-already-registered (excluded from polling; backfilled to true via DDL migration when the column is added). |
| `WBIAREGISTERATTEMPTS` | integer, default 0 | Number of registration attempts. Background poll filters `< MAX_ATTEMPTS` (e.g. 10). After cutoff, row is parked until operator resets. |

For the partial unique index to actually constrain duplicates, `Annotation.MEDIAASSET_ID_OID` must be populated. The Wildbook context note that `MEDIAASSET_ID_OID` is "not populated in practice" applies to *legacy* annotations created before the Wildbook-owns-acmId era; v2 explicitly sets `ann.setMediaAsset(ma)` in MlServiceProcessor's persistence. For commit #4 to function correctly, every ml-service-created annotation has both `predictModelId` AND `MEDIAASSET_ID_OID` populated; legacy annotations remain unaffected by the partial unique constraint (filtered out by the `WHERE PREDICTMODELID IS NOT NULL` clause).

Postgres partial unique index + WBIA-registration backfill (per-deployment migration script):
```sql
-- Idempotency: partial unique index for ml-service-created rows only.
CREATE UNIQUE INDEX IF NOT EXISTS "ANNOTATION_MLSERVICE_IDEM_idx"
ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
WHERE "PREDICTMODELID" IS NOT NULL;

-- One-time backfill: legacy annotations with an acmId have been registered
-- with WBIA via the historical IBEISIA flow. Mark them so the new
-- background-polling thread does NOT re-register them.
UPDATE "ANNOTATION"
SET "WBIAREGISTERED" = TRUE, "WBIAREGISTERATTEMPTS" = 0
WHERE "ACMID" IS NOT NULL AND "WBIAREGISTERED" IS NULL;
```

`Embedding`: non-unique composite index on `(annotation, method, methodVersion)`; promote to UNIQUE in a follow-up after a per-deployment audit confirms no existing duplicates.

## OpenSearch visibility

`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)`:

1. Call `_refresh` on the index (non-blocking; published refresh).
2. Run a knn-eligibility query (filter by IDs) and count hits.
3. If `hits == ids.size()` → return `true`.
4. Else sleep with exponential backoff (start 100ms; max 1s; total ≤ timeoutMs).
5. On timeout → return `false`.

This polls knn visibility *only*. It deliberately does NOT try to drain the
`IndexingManager` queue: that queue may contain unrelated entities and queue-depth
zero does not imply the specific IDs are queryable. The knn-eligibility query
IS the correctness gate.

Diagnostic: at startup and at the entry of `waitForVisibility`, log a WARN if
`/tmp/skipAutoIndexing` exists (the global indexing-skip flag) — that flag
will silently break visibility in development and operators should know it's set.

## Per-asset task lifecycle

`IA.intakeMediaAssetsOneSpecies` (ml-service path):
1. Create `topTask` (existing pattern; one Task object holds all MAs in the batch — preserves the legacy summary contract).
2. Per MediaAsset: create `childTask`. Use real Task hierarchy: `topTask.addChild(childTask)` and `childTask.setParent(topTask)`. Each child holds *only this MA* via `setObjectMediaAssets(List.of(thisMa))`. Persist both. Enqueue per-asset job with `taskId = childTask.getId()`.
3. Return `topTask` to caller.

`MlServiceProcessor.process`:
1. Load `Task` (the child task). The child's terminal state reflects ONLY this MA.
2. After persist, set `childTask` status (`completed | error | dropped-stale`).
3. The topTask is NOT explicitly finalized by the processor. Detection-completion at the batch level is determined from the topTask's MAs' `detectionStatus` (the existing way), not from Task hierarchy aggregation.

## Stale-job reconciliation

A `MediaAsset` left in `processing-mlservice` after a JVM crash (FileQueue is
at-most-once; lost jobs leave MA stuck) needs recovery. Commit #12 adds a
startup pass that walks media assets stuck in that state and re-enqueues a
detection job for each. The query needs a real "when did this status
transition happen" timestamp; `MediaAsset` does not have a dedicated
`detectionStatusModifiedAt` column.

Solution: add an explicit bump to `MediaAsset.setDetectionStatus` that calls
`setRevision()` (the existing version-stamp setter, which writes
`System.currentTimeMillis()` to `REVISION`). The reconciler queries:
```sql
SELECT "ID" FROM "MEDIAASSET"
WHERE "DETECTIONSTATUS" = 'processing-mlservice'
  AND "REVISION" < (EXTRACT(EPOCH FROM now()) * 1000)::bigint - 3600000;  -- 1 hour
```
Threshold default: 1 hour (longer than any healthy detection job's worst-case
duration; short enough that operators don't wait days for recovery).

The `setDetectionStatus → setRevision` link is added in commit #5 (state
semantics). It also benefits OpenSearch reindex pickup of detection-status
changes for any direct SQL writes (per the wildbook-database "version column
must be bumped" rule).

## Commit plan (each gated by Codex review)

Each commit: design note → Codex review → implement → Codex code review → commit.

| # | Subject | Estimated lines | Dependencies |
|---|---|---|---|
| 1 | Plan doc (this file, post-Codex iteration) | (doc) | — |
| 2 | `IAJsonProperties` accessors (`getActiveMlServiceConfigs`, `getPipelineRoot`) + remove implicit `_mlservice_conf` appending | ~80 | — |
| 3 | `Embedding.findMatchProspects` bug fixes (api_endpoint, premature complete, mutation) | ~40 | #2 |
| 4 | `Annotation` JDO additions (`predictModelId`, `bboxKey`, `thetaKey`, `wbiaRegistered`, `wbiaRegisterAttempts`) + partial-unique-index DDL + WBIA-registered backfill UPDATE + Embedding composite index | ~100 + SQL | — |
| 5 | State-semantics propagation: backend (`complete-mlservice`/`pending-species`/`dropped-stale` in terminality checks; `MediaAsset.setDetectionStatus` bumps `revision`); frontend (`pollingHelpers.js`, `EncounterStore.js`, `ImageCard.jsx`, `ImageModalStore.js` recognize `complete-mlservice`) | ~60 | #4 |
| 6 | `RestClient.postJSON` timeout overload | ~20 | — |
| 7 | `OpenSearch.waitForVisibility` helper + `skipAutoIndexing` diagnostic | ~60 | — |
| 8 | `MlServiceClient` (HTTP) + `MlServiceJobOutcome` record | ~180 | #6 |
| 9 | `MlServiceProcessor` (lifecycle) — persist creates Annotation with `wbiaRegistered = false` and `wbiaRegisterAttempts = 0`; the background poll picks it up. No inline WBIA call. | ~250 | #2 #3 #4 #5 #7 #8 |
| 10a | `IAGateway.processQueueMessage` dispatcher branch for `mlServiceV2:true` | ~15 | #9 |
| 10b | `IA.intakeMediaAssetsOneSpecies` routing splice with per-asset child tasks | ~50 | #10a |
| 11 | DB-backed WBIA registration: `Annotation.wbiaRegistered` + `wbiaRegisterAttempts` background polling thread in `StartupWildbook`; `MatchResult.annotationDetails` exposes `wbiaRegistered` | ~120 | #4 #9 |
| 12 | Stale `processing-mlservice` startup reconciliation | ~60 | #5 #10b |

Total: ~1000 lines net across 12 commits, average ~85 lines per commit. Each new file independently reviewable.

## Activation gate (rollout precondition; manual operator SQL)

Before flipping a species to ml-service in IA.json, the operator runs (per species):

```sql
-- Embedding backfill check; method/version from candidate IA.json _id_conf
SELECT COUNT(*) FROM "ANNOTATION" a
JOIN "ENCOUNTER_ANNOTATIONS" ea ON ea."ID_EID" = a."ID"
JOIN "ENCOUNTER" e ON e."CATALOGNUMBER" = ea."CATALOGNUMBER_OID"
WHERE a."MATCHAGAINST" = TRUE
  AND e."GENUS" = ? AND e."SPECIFICEPITHET" = ?
  AND a."ID" NOT IN (
    SELECT "ANNOTATION_ID" FROM "EMBEDDING"
    WHERE "METHOD" = ? AND "METHODVERSION" = ?
  );
-- Result must be 0 before flipping _mlservice_conf for this species.
```

OpenSearch dim verification and ml-service health check are also operator-run.

After activation: pre-existing empty `MatchResult` rows for the activated species are deleted (no `MatchResult.status` field today). Scope by species; log/export deletion counts before running.

```sql
-- Pre-activation audit: count empty MatchResults for the activating species.
SELECT COUNT(*) FROM "MATCHRESULT" mr
JOIN "ANNOTATION" qa ON qa."ID" = mr."QUERYANNOTATION_ID_OID"
JOIN "ENCOUNTER_ANNOTATIONS" ea ON ea."ID_EID" = qa."ID"
JOIN "ENCOUNTER" e ON e."CATALOGNUMBER" = ea."CATALOGNUMBER_OID"
WHERE e."GENUS" = ? AND e."SPECIFICEPITHET" = ?
  AND mr."NUMBERCANDIDATES" = 0;

-- After audit, delete via Postgres DELETE … USING:
DELETE FROM "MATCHRESULT" mr
USING "ANNOTATION" qa, "ENCOUNTER_ANNOTATIONS" ea, "ENCOUNTER" e
WHERE mr."QUERYANNOTATION_ID_OID" = qa."ID"
  AND ea."ID_EID" = qa."ID"
  AND e."CATALOGNUMBER" = ea."CATALOGNUMBER_OID"
  AND e."GENUS" = ? AND e."SPECIFICEPITHET" = ?
  AND mr."NUMBERCANDIDATES" = 0;
```

Method-level scoping is a refinement: `MatchResult` does not currently store
the embedding method/version of the query, so scoping by method requires
joining through the query annotation's embeddings. The MVP cleanup uses
species-level scoping (above), which is correct for "delete pre-activation
empty results before flipping a species to ml-service" because all empty
results for that species are eligible.

## Test plan (WireMock unit only)

Coverage per commit:
- `IAJsonProperties` (#2): `getActiveMlServiceConfigs` returns null when pipeline_root is HotSpotter, returns configs when vector; malformed-per-species disables.
- `Embedding.findMatchProspects` (#3): subtask failure → parent `error`; matchingSetQuery clone-not-mutate.
- `MlServiceClient` (#8): `/pipeline/` happy path, zero detections, malformed embedding (length, NaN), 502 retry classification, 4xx non-retry.
- `MlServiceProcessor` (#9): Phase 4 stale check returns `dropped-stale`; idempotent retry skips persisted annotation; Annotation.mediaAsset is set on persist; outcome enum matches contract.
- `OpenSearch.waitForVisibility` (#7): visibility eventual-success returns true; timeout returns false; `skipAutoIndexing` flag triggers WARN.
- `wbiaRegister` background thread (#11): poll picks up annotations with `wbiaRegistered=false`; success sets true; transient WBIA failure increments `wbiaRegisterAttempts` (still false); next cycle retries; rows with `attempts >= MAX` are excluded from polling.

Documented blind spots: JDO cascade behavior, OpenSearch knn correctness, real ml-service contract drift, DataNucleus enhancement for new fields. These require manual hand-test on a dev deployment.

## Workflow commitment

For every commit in the plan above:

1. Write a short design note (≤200 lines) with the one or two architectural decisions involved.
2. Send to Codex with the skill-context bundle attached. Iterate until Codex returns no blocker findings.
3. Implement in code, no larger than the design note suggests.
4. Send the diff to Codex with the skill-context bundle. Iterate until Codex returns no blocker findings.
5. Commit.

Skill-context bundle path: `/tmp/codex-context-bundle.md` (machine-local; refreshed before each prompt).

## Codex feedback log

This plan was reviewed by Codex twice on 2026-05-10. Substantive findings folded in:

**Round 1 (initial design review):**

- Routing invariant fixed (was `_mlservice_conf` presence; now `pipeline_root == "vector"` with configs present).
- Idempotency unique index protected by explicit `Annotation.mediaAsset` set in processor (was assumed; the column had been documented as not populated).
- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
- `bboxKey` / `thetaKey` literal normalized strings (was sha1; rejected as opaque).
- Distinct payload key `mlServiceV2:true` (was reusing `MLService:true` which collides with legacy callers).
- `wbiaRegister` simplified to DB-backed polling (was FileQueue + reconcile servlet).
- Pre-activation `MatchResult` cleanup now scoped by species (was global).
- Frontend `pollingHelpers.js` updates included in commit #5 (was missing).
- Stale `processing-mlservice` reconciler is a real commit (was missing entirely).
- Commit #10 split into dispatcher (10a) and routing (10b) so risk is isolated.
- `_id_conf.default.pipeline_root` documented as the single decision point in three places (architecture diagram, schema rules, class accessor).

**Round 2 (verification):**

- WBIA registration state machine reworked: `wbiaRegistered` is now a real boolean, not tri-state. Backfill UPDATE marks legacy annotations true so the polling query doesn't sweep them. New `wbiaRegisterAttempts` column counts failed attempts; polling filters `< MAX_ATTEMPTS`. Failed registrations are rescheduled by the next poll cycle without flipping the registration flag.
- Stale-reconciliation timestamp source clarified: `MediaAsset.setDetectionStatus` is updated to bump `REVISION` (a real epoch-millis column); the reconciler queries on `REVISION < now - threshold`.
- Frontend status consumers expanded: `EncounterStore.js`, `ImageCard.jsx`, and `ImageModalStore.js` are added to commit #5 alongside `pollingHelpers.js`.
- Pre-activation MatchResult cleanup SQL fixed: `SELECT … JOIN` for the audit (was invalid `SELECT … USING`); `DELETE … USING` for the deletion. Method-level scoping noted as a refinement (MVP uses species-level).
