# ml-service: multiple detection pipelines per species

Date: 2026-10-16
Branch: `feat/mlservice-multi-config`

## Problem

A species can list several entries in `<Genus>.<species>._mlservice_conf`, but the
v2 ml-service path only uses entry `[0]`:

- `MlServiceProcessor.activeConfigs()` returns `configs.optJSONObject(0)`. Detection
  runs one `/pipeline/` call on one `api_endpoint`.
- Every annotation from that job is matched with one match config, the default
  vector entry under `<species>._default._id_conf`.
- The v2 `annotationId` extraction job also uses entry `[0]`.
- `MLService.send(Annotation)` (manual boxes, `Annotation.createFromApi`,
  `Embedding.catchUpEmbeddings`, `SubmitSpotsAndImage`) does the opposite: it loops
  over every species entry, so a box gets one embedding per configured extractor.
- PairX (`MatchResult._getPairxConfig`) always uses entry `[0]` for endpoint and model.

Motivating case (Whiskerbook, *Canis familiaris*): run MegaDetector + a viewpoint
labeler + MiewID msv4.1 on one ml-service to produce body boxes stored as `dog`,
and a face OBB detector + the pet MiewID model on a **different** ml-service to
produce face boxes stored as `dog+face`. Each class must be embedded and matched
with its own model.

## Config contract (IA.json)

No new keys. Existing keys are given a defined meaning when the species-level
array has more than one entry:

```
"Canis": { "familiaris": {
  "_mlservice_conf": [ <body pipeline, endpoint A>, <face pipeline, endpoint B> ],
  "animal":   { "_save_as": "dog",      ... },          // MegaDetector raw class
  "dog":      { "_id_conf": [msv4.1 vector default, HotSpotter], "_mlservice_conf": [<body>] },
  "dog_face": { "_save_as": "dog+face", ... },          // face detector raw class
  "dog+face": { "_id_conf": [pet vector default, HotSpotter],   "_mlservice_conf": [<face>] },
  "_default": { "_id_conf": [...], "_mlservice_conf": [<body>] }
}}
```

- Species-level `_mlservice_conf` = the ordered list of detection pipelines. All of
  them run, in array order, for each MediaAsset.
- `<species>.<iaClass>._mlservice_conf[0]` = the extraction config for an existing
  annotation of that class (endpoint + `extract_model_id`).
- `<species>.<iaClass>._id_conf` default vector entry = match config for
  annotations of that class.

### Back-compat rule

**When the species-level array has exactly one entry, behavior is byte-for-byte
unchanged**: that entry is used for detection and extraction, and the match config
is the `_default` default vector entry, as today. Per-class resolution below only
applies when the array has two or more entries. Every production IA.json (17 files
scanned 2026-10-16) has at most one entry per species, so this branch changes no
deployed behavior until a second entry is added. The only per-class
`_mlservice_conf` that differs from its species entry today is Sharkbook
(whaleshark, leopard_shark), and only in `orientation_model_id`; under this rule it
keeps today's behavior.

## Changes

### 1. `IAJsonProperties` (pure accessors, unit tested)

- `getMlServiceConfigsForIAClass(Taxonomy, String iaClass)` → the class-level array
  if non-empty, else null. No `_default` fallback (the caller decides).
- `getDefaultVectorIdentConfig(Taxonomy, String iaClass)` → copy of the first
  `default: true` entry with `pipeline_root == "vector"` under that class's
  `_id_conf`, else null. No fallback.
- `isMultiPipeline(Taxonomy)` → active species array length > 1.
- `selectExtractionConfig(Taxonomy, iaClass)`: single-pipeline → species `[0]`;
  multi → class-level `[0]` if present, else species `[0]` (logged WARN). This is the
  **one** extractor selector; the v2 extraction job, `MLService.send(Annotation)` and
  the PairX fallback all use it, so a multi-pipeline annotation is embedded by exactly
  one extractor on every path.
- `extractorIdentity(JSONObject mlConfig)` → `(method, version)` of
  `extract_model_id`, parsed with the v2 rule (`MlServiceProcessor.parseEmbeddingMethodVersion`:
  split on the first dash, keep everything after it).

### 2. `MlServiceProcessor` detection

- `activeConfigs` returns the whole active array (plus the legacy `[0]` match
  config) instead of one entry. `loadDetectionContext` validates that **every**
  entry has `predict_model_id`; any missing → same INVALID terminal path as today.
- `processDetection` calls `client.pipeline(endpoint_i, imageUri, config_i)` for each
  entry in order, collecting `(configIndex, results)`. Network I/O stays outside any
  Shepherd transaction, as today.
  - Retryable `IAException` on any entry → requeue the whole job (nothing has been
    persisted yet; on re-run, `findExistingAnnotation` dedup makes any repeated
    persistence idempotent).
  - Non-retryable on any entry → whole detection fails exactly as today
    (`markDetectionFailure`). Rationale: a half-configured species should fail
    loudly, not silently drop one class. (Open question for review.)
- All results from all entries are persisted in **one** `persistDetections`
  transaction, so `ma.assignEncounters` runs once over the union of boxes (one
  Encounter per animal-part box, as today) and the MA status flips to complete once.
  Zero results across all entries → `finalizeZeroDetections`.
- Each persisted annotation remembers which entry produced it and the
  `(method, version)` of the embedding that came back with it.
- Dedup (`findExistingAnnotation`) for multi-pipeline species additionally requires
  the existing annotation's iaClass to equal the newly resolved iaClass, so a body
  box and a face box with identical rounded geometry stay two annotations.
  Single-pipeline dedup unchanged (geometry only).

### 3. Match after detection

- Single-pipeline: unchanged (one `waitAndRunMatch` over all ids with the
  `_default` match config).
- Multi-pipeline: resolve a match config per annotation:
  1. class-level default vector entry for the annotation's stored iaClass,
     else `_default`'s default vector entry;
  2. if that entry's `(method, version)` does not equal the embedding the
     annotation actually received, fall back to
     `{pipeline_root: vector, method, version, description: <producing entry's description>}`
     derived from the embedding, and log a WARN (a config error that would
     otherwise match against an empty corpus).
  3. `api_endpoint` defaults from the producing entry (as `ensureMatchConfig` does).
- Group annotation ids by the resolved match config (canonical JSON string as the
  key, insertion-ordered) and call `waitAndRunMatch(groupIds, taskId, cfg)` once per
  group. Each group gets its own match child Task under the same detection task,
  so the match-results page shows one tree per class/model. Every group is
  attempted/deferred even if an earlier one fails; the returned outcome is the first
  non-ok group outcome, else ok with all ids. (The outcome is only logged by
  `IAGateway`; `findMatchProspects` returning true with an error subtask is a
  pre-existing limitation and is not changed here.)
- For multi-pipeline species `runMatchProspects` also stamps `method` and `version`
  into the match task's `ibeis.identification` block (it already stamps
  `description`), so every MatchResult's task carries the embedding space it was
  computed in (§6).

### 4. v2 `annotationId` extraction job

Multi-pipeline species only:
- `loadExtractionContext` uses `selectExtractionConfig(taxy, ann.getIAClass())`.
- "Already has an embedding" is tested against `extractorIdentity(selected)`, not the
  match config, so an old embedding from another model never suppresses extraction.
- `persistExtraction` dedups against the identity parsed from the **response**
  (`parseEmbeddingMethodVersion(response)`), as detection does.
- The match config is resolved with the full §3 rules (class-level → `_default` →
  embedding-derived fallback when the configured identity differs from the
  embedding just stored).
Single-pipeline: unchanged.

### 5. `MLService.send(Annotation)` (manual / API / catch-up path)

Multi-pipeline species: exactly one config, `selectExtractionConfig(taxy,
ann.getIAClass())`, and the stored `(method, version)` is parsed with the v2 rule
(first-dash split of the legacy response's `model_id` field, via a small
`firstDashSplit(String)` helper shared with `parseEmbeddingMethodVersion`; the v1
response carries `model_id` + `embeddings[0]`, not `embedding_model_id`) instead of
`MLService.getMethodValues` (which keeps only the
second dash token: `miewid-pet-v1` would be stored as version `pet` on this path but
`pet-v1` on the detection path). Single-pipeline species: unchanged (loop over all
species entries, `getMethodValues`).

### 6. PairX

New `_getPairxConfig(txStr, iaClass, method, version)`; both callers
(`createInspectionPairxAsset`, `MatchInspectionPairxEnricher`) pass the query
annotation's iaClass and the `method`/`version` from the MatchResult task's
`ibeis.identification` block. That block is the `_id_conf` entry on the legacy/manual
ident path, and is stamped by §3 on the detection path. Selection for multi-pipeline
species:
1. the config (species entries, then class-level entries) whose
   `extractorIdentity` equals the match's `(method, version)`, so PairX visualises
   the space the match was computed in, including after a §3 fallback;
2. if the task carries a `(method, version)` that matches no configured extractor
   (e.g. a historical match from a model since removed), **skip PairX with a WARN**;
   never visualise with a different model;
3. only when the task carries no identity at all, `selectExtractionConfig(taxy, iaClass)`.
Single-pipeline: unchanged (species `[0]`).

## Not changed

- Legacy WBIA multi-`_detect_conf` path (`IA.intakeMediaAssetsOneSpecies`).
- `getActiveMlServiceConfigs` routing gate and `StartupWildbook` reconciler (both
  only test "is the array populated").
- `_save_as` semantics. The face detector's raw `dog_face` → `dog+face` and
  MegaDetector's raw `animal` → `dog` are pure IA.json.

## Encounters for body + part boxes

`MediaAsset.assignEncounters` treats a class containing `+` as a part. One `dog` +
one `dog+face` on a photo, contiguous → both on one Encounter. Two or more bodies, or
repeated parts → every unassigned box gets its own Encounter, with no body-to-face
association (two dogs → four Encounters). This is today's behavior for any species
whose detector emits parts (e.g. `lion` + `lion+head`); this branch does not change
it, and IA.json authors must name the part class with `+` (`dog+face`, not
`dog_face`) for the single-animal case to work.

## Out of scope

- Requeue exhaustion: `IAGateway.requeueJob` can refuse a requeue and the processor
  still reports `requeue`, leaving the MA in `processing-mlservice`. Pre-existing and
  fixed separately on `fix/mlservice-retry-exhaustion-terminal`; this branch keeps a
  single requeue call site per job so that fix applies unchanged.

## Tests

Unit (no DB): `IAJsonPropertiesTest` for the new accessors incl. back-compat;
pure static helpers in `MlServiceProcessor` for match-config resolution and
grouping, multi-config validation, and the per-entry call loop with a stub
`MlServiceClient` that records `(endpoint, predict_model_id)` and can throw
retryable/non-retryable errors on the Nth call; `MLServiceCreatePayloadTest`-style
test for the `send(Annotation)` config filter; PairX config selection.

## Risks

- Detection latency per asset = sum of the pipelines (serial). Acceptable for 2.
- A detection job now holds the queue slot across two remote calls; a retryable
  failure on entry 2 re-runs entry 1 (idempotent via dedup).
- Overlapping boxes from two detectors are kept (different geometry → no dedup);
  that is the point (body + face).
