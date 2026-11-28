# Bulk import of user-supplied bounding boxes (design, rev 8)

Status: all eight decisions taken; awaiting the user's approval of the document before planning. Codex rounds 1-5 applied (see the companion review
log); rev 6 scopes crash recovery to parity with the existing detection path instead of the
sweep/pointer machinery Codex round 5 found incomplete. Nothing built. Rev 7 folds in the
user's doc feedback of 2026-11-07: `skipDetection` with boxes, row identity, theta units confirmed;
all eight decisions taken the same day (§7). Rev 8 applies Codex round 6: gate readiness by
asset settle in the processor (no gate change), API import-only state, bare media-field scans,
one boxed-import predicate, PR E re-estimated.

## 1. Intent and why

Wildbook users (camera-trap and BRUV video programmes first) are running their own detectors on
extracted video frames before import. Today bulk import accepts only whole photos; Wildbook then
runs its own detector, which for these users is redundant at best and wrong at worst (different
model, different classes, frames the site's detector was never trained on). They want the
spreadsheet to carry, per photo, the box their model found, so Wildbook stores that box as the
annotation, skips its own detection for that photo, and goes straight to individual matching.

Deliverables: importable annotation fields; authoritative backend validation; the pipeline
hand-off that skips detection and still runs embedding extraction and identification; the
submissions API and the React bulk import page accepting and validating the new fields; a served agent skill that turns a
detector's output into a valid spreadsheet.

## 2. Goals, non-goals, anti-goals

Goals
- Per photo slot in a row, accept one bounding box plus the metadata Wildbook needs to treat it
  like a detector result (class, optional viewpoint, optional rotation, optional source label,
  detector score).
- Validate everything the backend can validate without decoding the full image before anything is
  created, with errors in the existing row/field error contract so the current UI renders them
  unchanged.
- An import that carries boxes never runs Wildbook detection: on a species with an ml-service
  extractor every box gets an embedding and, unless re-identification is skipped, a match run
  (other species match through WBIA unless skipped); photos without a box in such an import are
  imported untouched (a second import without box columns gets them detected).
- Within-import matching: every box's match sees every other box of the same species in the
  import, as detection does today (the visibility gate waits for sibling extraction, with its
  existing 12-minute give-up).
- The submissions API accepts the same per-photo fields with the same validation, in this round,
  because third-party app pushes are the primary route; the agent skill documents that route
  first.
- Boxed annotations are first-class match candidates (acmId set, matchAgainst true, indexed) and
  reach embedding extraction and identification on the existing v2 ml-service extraction path,
  honoring the import's skip flags and matching-location filter. Every box ends in a terminal
  state the status page can see: a durable match run, a deliberate skip, or an error task; a
  crash mid-pipeline is recovered the way the detection path recovers today (next restart,
  re-send, watchdog).
- The import status page keeps working with no new states and never reports identification
  complete before a match run exists.
- A plain-language agent skill that converts COCO / YOLO / Pascal VOC / CSV detector output into
  the format, and refuses to prepare boxed sheets for a site that cannot accept them.

Non-goals (v1)
- Video upload or frame extraction inside Wildbook. Users extract frames; photos only.
- Detecting un-boxed photos inside an import that carries boxes (make a second import).
- Importing embeddings or match scores produced by the user's model. Identities (e.g.
  `MarkedIndividual.individualID`) on the import row are still honored.
- Editing or replacing boxes on encounters that already exist (re-import).
- Body + part boxes for one animal on one photo in one row, polygons, masks, keypoints.
- Normalized or centre-based box formats on the wire (the skill converts them).
- Per-box control of matchAgainst or exemplar flags.
- Guaranteed within-import pair coverage beyond the gate's existing 12-minute give-up: a very
  large import on the serial queue can still match early boxes against a partial set, exactly as
  detection does today.
- A separate wire format for the submissions API (`/api/v3/submissions`): it takes the same
  field names and values as the spreadsheet (§4.9).
- A pre-flight "validate only" call from the React page (it posts once today); follow-up.
- Fixing the pre-existing WBIA-path bug where identification after WBIA detection ignores the
  import's matching locations. Separate issue.

Anti-goals
- Boxed photos silently re-detected, producing duplicate annotations and cloned encounters.
- Imported boxes that silently never match (missing acmId, matchAgainst false, not indexed, or
  stuck pending after a failed enqueue, publish or extraction).
- An older site silently dropping the box columns as unknown fields and running its detector.
- Validation that passes in "validate only" mode but fails at import for a check that does not
  need the full image decoded.
- A skill written in ML or programmer jargon.

## 3. What exists today (verified against main at d0faca0446)

- Row model: one row = one Encounter; `Encounter.mediaAsset{N}` names an uploaded file; per-slot
  sub-columns exist (`.keywords`, labeled keywords) plus positional `Encounter.quality{N}`.
  `BulkImporter.processRow` creates one trivial (unity-feature) Annotation per slot; the same
  filename on several rows maps to ONE MediaAsset with one trivial annotation per encounter.
  Keywords and quality are matched to slots by position among occupied slots (`offset`,
  BulkImporter.java:761-770), not by N.
- `Annotation.isTrivial()` (Annotation.java:490) is true for a unity feature, or when the LEGACY
  integer fields `width/height` on the Annotation equal the media dimensions with no legacy
  transform. The feature-based constructor (`Annotation(species, feature, iaClass)`) leaves those
  legacy fields at zero, so a feature-only box is never trivial, even one covering the whole
  image. `getBbox()` reads feature parameters.
- Hand-off: `BulkImport.initiateIA` -> `IAGateway.handleBulkImport` (:1306) creates the IA root
  Task with `taskParameters` (`importTaskId`, `skipIdent`, `matchingSetFilter`, `{}` when no
  location was chosen, which `Annotation.getMatchingSetQuery` treats as "every location"),
  commits with the unchecked `commitDBTransaction` (Shepherd swallows failures), rebuilds
  `mediaAssetIds` from `itask.getMediaAssets()` (:1332, the supplied map is ignored; a test,
  `SubmissionProcessingTest:74`, relies on that), publishes via `publishToLane`, and reports
  `success:true` even when publish returned false (:1356-1360). `initiateIA` returns early when
  handed no assets. `laneFor` (:119): a `v2` envelope with non-empty `annotationIds` -> IA lane
  (or fastlane); non-empty `mediaAssetIds` -> detection lane. `IA.handleRest` (:913) processes
  `mediaAssetIds` (detection intake) then `annotationIds` (`intakeAnnotations`) inside one
  try/catch, so a media-branch failure skips the annotation branch.
- Detection routing is per species (IA.java:173): with active ml-service configs
  (`getActiveMlServiceConfigs(taxy) != null`), a per-species topTask holds the species' asset
  list and each asset gets a child Task plus a `{mlServiceV2:true, mediaAssetId, ...}` job
  (`enqueueOneAssetForMlService`, :284, unchecked persist then publish); otherwise the legacy
  WBIA detect job. ml-service detection creates annotations with a bounding-box Feature
  `{x,y,width,height,theta,viewpoint}`, `Annotation(null, feature, iaClass)`, `acmId = id`,
  `matchAgainst = true`, `wbiaRegistered = FALSE`, `quality = score|confidence`, explicit
  `ma.addFeature / feature.setAnnotation / ann.addFeature`, then `assignEncounters`.
- v2 extraction job `{mlServiceV2:true, annotationId, taxonomyString, taskId}` ->
  `MlServiceProcessor.processExtraction` (:351). `loadExtractionContext` (:467) loads the
  annotation and its media asset from the annotation (the task need not hold either), uses the
  supplied taxonomy, returns `pending-species` (task completed) when the species has no active
  ml-service config, and returns `embedding-exists` (task completed, NO matching) when the
  embedding is already stored (:512-521). After extraction `persistExtraction` stores the
  embedding and marks the child task completed (:894) BEFORE `waitAndRunMatch`, which reads
  `skipIdent` from the task parameters (:957), runs the `MatchVisibilityGate`, then
  `runMatchProspects`, which inherits the parent's parameters through `matchIntakeParams`
  (:1032; keeps `importTaskId`, `matchingSetFilter`, `skipIdent`; does not strip
  `embeddingExtraction`) and calls `IA.intakeAnnotationsUnder` per encounter group. No producer of
  this job exists in Java today; the processor side is live and tested.
- `MatchVisibilityGate` (MatchVisibilityGateImpl.java): siblings = `child.getParent()
  .getObjectMediaAssets()`; a sibling is terminal when its `detectionStatus` is in {complete,
  complete-mlservice, pending, pending-species, error, dropped-stale} OR its single-asset child
  task is terminal; eligible sibling annotations are those on contributing assets (complete,
  complete-mlservice) that ALREADY have an embedding for the match method/version
  (`MatchEligibilityQuery`, EMBEDDING join, matchAgainst true, acmId not null); phase 3 waits
  for those ids to be matchable in OpenSearch; age-out runs the match anyway.
- `IA.intakeCore` (:593) does not inspect `skipIdent`; a class with no option goes to
  `skippedIaClasses` (:640) and options with `default:false` are dropped (:644); when nothing
  remains it produces an `INVALID_MATCH_CONFIG` error umbrella (:700). `IAJsonProperties.identOpts`
  (:466) returns options WITHOUT filtering `default:false`; `getValidIAClassesIgnoreRedirects`
  (:552) enumerates explicit user-selectable keys, suppresses `_save_as` sources (:531) and never
  adds the `_save_as` targets, so a species whose detector class remaps to `body` with no explicit
  `body` key stores and matches `body` but does not list it. `IA.markTaskError` (:575, private) is
  the terminal-error helper. `Embedding.findMatchProspects` (:330) extracts a missing embedding on
  the fly through the legacy synchronous `MLService` call, inside the identification run.
- Queues: `FileQueue` acknowledges a message BEFORE processing it (FileQueue.java:265); the
  detection queue has one serial consumer (StartupWildbook:400) and the IA queue a configurable
  worker count (:371). Interactive New Match requests send `fastlane:true`
  (NewMatchStore.js:229); when the fastlane is disabled they fall back to the DETECTION lane
  (IAGateway.java:174), not the IA lane. The startup `StaleMlServiceReconciliation`
  (StartupWildbook:600-700) recovers only media assets whose `detectionStatus` is
  `processing-mlservice` older than a threshold, by re-enqueuing detection.
  `Task.queueResumeMessage` is written by `handleBulkImport` but nothing replays it.
- `Task.getTasksFor` (:558) finds tasks by direct annotation ownership; `ImportTask` counts an
  annotation's identification terminal only for `completed` and `error` (:901) while the rule-2
  fallback returns whatever status the newest direct task has, including `dropped-stale` (:594).
- `SubmissionProcessingTest` (:64) mocks `Shepherd` without stubbing
  `commitDBTransactionWithStatus`, so a checked commit in `handleBulkImport` returns false there
  unless the test stubs it.
- Import status (`ImportTask.iaSummaryJson`): an asset is detection-terminal by `detectionStatus`
  as above; detection "complete" once terminal count >= IA-valid assets; `pipelineStarted` when a
  root exists or (legacy, :922) any asset is terminal. Identification: non-trivial annotations;
  no task -> pending; with tasks, `identificationStatusForAnnotation` (:585) picks the run-aware
  umbrella, else (rule 2) the NEWEST DIRECT TASK's status. `Task.isOrchestrationNode` (:773)
  treats tasks with media assets or `ibeis.detection` / `embeddingExtraction` parameters as
  orchestration nodes for the structural climb.
- Manual annotation (`Annotation.createFromApi`) is the validation reference (x,y ints,
  width,height >= 1, theta in [-6.2832, 6.2832], NaN admitted; viewpoint; iaClass) but requires
  persisted objects and queues work before its caller commits; not reusable as a call.
- `new Feature(String typeId, params)` resolves through the static `FeatureType` cache, which
  throws when `initAll` never ran in this JVM (FeatureType.java:59); `initAll` begins and rolls
  back a transaction (:66-74). `FeatureType.load(id, shepherd)` (:54) and `Feature(FeatureType,
  params)` resolve through the current persistence manager. `MediaAsset.generateFeatureFromBbox`
  (:609) assembles the same parameters but uses the string constructor.
- JDO cascade: MediaAsset->features, Feature->annotation (owning side via `setAnnotation`),
  Encounter->annotations are persistent relations; `MediaAssetFactory.save` and
  `Shepherd.storeNewEncounter` call `makePersistent` (`storeNewEncounter` returns "fail" rather
  than throwing); `Shepherd.commitDBTransactionWithStatus` (:3612) is the checked commit.
- Image dimensions: `AssetStore.extractMetadataAttributes` (:600) stores width/height from
  `ImageIO.read` of the stored file (raw pixel grid, no EXIF rotation; `IIOException` suppressed
  and then no dimensions). `Util.fastFileValidation` (:1205; type sniff, rejects < 1000 bytes)
  runs only for real imports; `UploadedFiles.makeMediaAsset` fully decodes
  (`AssetStore.isValidImage`). `BulkImporter.processRow` (:746) silently skips a slot whose
  MediaAsset failed creation.
- Index rule (Annotation.java:314): indexed when `matchAgainst` OR has embeddings; acmId matters
  for candidate selection. Bulk import indexes encounters/occurrences/individuals shallowly in
  background (`BulkImportUtil.bulkOpensearchIndex`), never annotations.
- Frontend: `usePostBulkImport` posts ONCE with `processInBackground:true`; nothing calls
  `validateOnly`; a 400 lands in `store.setSubmissionErrors` and the failure modal / review table.
  `BulkImportSpreadsheet.jsx` (:81-172) merges every `Encounter.mediaAsset*` column (spreadsheet
  order) into one `Encounter.mediaAsset0` cell; `BulkImportStore.updateRawFromNormalizedRow`
  (:1301) re-explodes to consecutive `Encounter.mediaAsset{idx}` while suffixed columns keep their
  original N. Slot zero is also assumed by `missingPhotos` (:493), upload validation (:788) and
  `EditableDataTable` error reconciliation (:245). `isDynamicKnownColumn` (:1450) recognizes
  `.keywords` and labeled-keyword suffixes; the generic labeled-keyword rule (:1513) claims any
  other suffix; bare `Encounter.mediaAssetN` (N > 0) is not a known column.
- `/api/v3/site-settings` exposes `annotationViewpoint`, per-species
  `getValidIAClassesIgnoreRedirects` (SiteSettings.java:127), `bulkImportMinimalFields` and
  `bulkImportFieldNameSynonyms`, but not the indexable field names.
- Agent skills: markdown under `src/main/resources/agent-skills/`, `AgentSkill.SKILL_RESOURCES`,
  `index.md`; `AgentSkillContentTest` enforces sections, a jargon ban outside "How to do it",
  the catalog assertion (:221, registered keys minus three named skills = four analytical) and the
  import-prep pattern (:247).

## 4. Design

### 4.1 Spreadsheet fields (wire format, backend-authoritative)

Per photo slot `N`, parallel to `Encounter.mediaAsset{N}`:

| Column | Type | Required | Rule |
|---|---|---|---|
| `Encounter.mediaAsset{N}.bbox` | string `x,y,width,height` | when any other slot-N box column is set | four integers, comma separated, whitespace tolerated; `x,y >= 0`; `width,height >= 1`; pixel coordinates of the stored image (raw grid, no EXIF rotation), origin top-left; normalized to the canonical string (the importer parses it to `int[4]`). A box covering the whole photo is allowed. |
| `Encounter.mediaAsset{N}.iaClass` | string (validated against allowed values in IA.json) | when `.bbox` is set | member of the matchable list (§4.2); error text lists it |
| `Encounter.mediaAsset{N}.viewpoint` | string (validated against allowed viewpoints in Wildbook) | no | `Annotation.isValidViewpoint`; null allowed |
| `Encounter.mediaAsset{N}.theta` | double, radians (confirmed: `Annotation.createFromApi` rejects out-of-range values as "invalid theta value in radians", Annotation.java:1926; forwarded unchanged to ml-service and WBIA) | no | finite, `-6.2832 <= theta <= 6.2832`; default 0; radians about the box centre in the raw image grid; positive = clockwise as displayed (image y axis pointing down), the WBIA convention; Wildbook forwards theta unchanged to the ml-service `/extract/` and to WBIA (`MlServiceClient:139`, `WildbookIAM:320`), so one non-zero fixture is verified through both services and, should either disagree, the sign is converted at that boundary, never on the wire; bounds apply to the un-rotated box; rotated corners may leave the image (no clipping) |
| `Encounter.mediaAsset{N}.bboxSource` | string | no | trimmed, 1-128 chars; stored in feature parameters |
| `Encounter.quality{N}` (existing) | double | no | on a boxed row: the detector score, finite, no range enforced, read by exact N; provenance records `scoreSource: "detector"` |

Decisions (mine; Codex concurred):
- One box per slot per row; a second animal on the same photo is a second row naming the same
  file; an animal across frames is one row with several slots.
- Single `bbox` cell; pixel xywh only; the skill converts COCO / YOLO / VOC with deterministic
  rounding (left/top floored, right/bottom ceiled, clamped to the image).
- Whole-photo boxes are accepted: with the feature-based constructor they are ordinary boxes
  (never "trivial"), and pre-cropped frames make them common.
- Matchable classes only (unless the import skips identification). The matchable list for a
  species = the canonical stored classes (explicit keys from `getValidIAClassesIgnoreRedirects`
  plus every `_save_as` target configured under the species or its `_default`) that have at
  least one identification option whose `default` is not false (`identOpts` filtered with the
  same `optBoolean("default", true)` rule intake uses). A class outside that list would end its
  run in the existing `INVALID_MATCH_CONFIG` error, which is a mapping mistake surfaced too late;
  rejecting it at validation with the list is the honest contract. The derived list is computed
  in one new `IAJsonProperties.getMatchableIAClasses(taxy)` and exposed by site settings as
  `bulkImportMatchableClasses` (per species), so validation, the page and the skill agree. When
  `skipIdentification` (or `skipDetection`) is set, any canonical stored class is accepted.
- Part classes (`+`) are accepted when matchable; a standalone part box attaches to its row's
  encounter; no body box is manufactured; imported boxes never pass through `assignEncounters`.
- Provenance in feature parameters: `_bulkImportBbox: {importTaskId, source?, scoreSource?,
  created}`, mirroring `_manualAnnotationViaApiV3`. Every later rule ("imported box", detection
  partition) keys on this marker, never on geometry.
- The five suffixes are reserved: `BulkImportUtil.getLabeledKeywordLabel` and
  `getLabeledKeywordOffset` (:376, :388) exclude them; a configured keyword label equal to one
  logs a startup warning and loses.
- Exact-slot imports: when any `Encounter.mediaAsset#.bbox` name is present (in the request's
  `fieldNames` on the page, or on any row for the API; `BulkImporter` derives the mode from the
  validated rows), EVERY row of that import reads all per-slot metadata (`.keywords`, labeled
  keywords, `Encounter.quality{N}`, box columns) by exact slot N, boxed or not; the page keeps
  numbered slots for the whole sheet in that case (§4.6). Imports without box columns keep
  today's positional behavior unchanged. Slot reading is keyed
  on the column; the boxed-import behavior (no detection, §4.4) is keyed on ONE predicate,
  `annotationsImported`: at least one non-empty `.bbox` value in the validated rows. A sheet with
  box columns and no values is an ordinary import.

### 4.2 Validation (three layers, existing `BulkValidatorException` types only)

Field level, `BulkValidator.validateValue` (names join `FIELD_NAMES_INDEXABLE` as
`Encounter.mediaAsset#.bbox` etc.): `.bbox` parse/range -> `INVALID_VALUE` naming the text;
`.iaClass` non-empty trimmed <= 64; `.viewpoint`; `.theta` finite in range; `.bboxSource`
length; `Encounter.quality#` finite for all rows (today `tryDouble` admits NaN/Infinity).

Row level, `BulkImportUtil.validateRow`:
- Slot pairing: a box sub-column without `.bbox` -> `REQUIRED_VALUE` on `.bbox`; `.bbox` on a
  slot with no `Encounter.mediaAsset{N}` -> `INVALID_VALUE` on `.bbox`.
- Class resolution, only when genus/epithet validated: taxonomy via `myShepherd.getTaxonomy`;
  matchable list as in §4.1 (the request's skip flags are passed into `validateRow` as a flag so
  the list widens when identification is skipped); missing `.iaClass` -> `REQUIRED_VALUE`;
  present but not listed ->
  `INVALID_VALUE` "valid classes for Genus species: a, b, c".
- Same filename twice in one row when either slot is boxed -> `INVALID_DUPLICATE`.

Import level, `BulkImport.doPost`, in the loop that matches `filenamesNeeded` against uploaded
files (runs for `validateOnly` and real imports):
- For every boxed filename, both modes: `Util.fastFileValidation(file)` and a header-only
  dimension read (`Util.fastImageDimensions(File)`: `ImageIO.getImageReaders` +
  `getWidth(0)/getHeight(0)`, reader disposed, stream closed), cached per uploaded file for the
  request and reused by the later file loop. Equality with the stored dimensions holds whenever
  the stored file decodes with the same ImageIO provider (JPEG incl. EXIF-tagged and progressive,
  PNG, BMP, GIF first frame); a fixture test pins that set and includes a header-readable but
  undecodable PNG and GIF, which fail later at MediaAsset creation as today.
- Bounds in `long` arithmetic: `x + width > W` or `y + height > H` -> `INVALID_VALUE` "box
  10,20,500,400 exceeds image 640x480".
- Cross-row consistency: a filename boxed on one row and un-boxed on another -> `INVALID_VALUE`
  on the un-boxed row's `Encounter.mediaAsset{N}`; an identical box (x,y,width,height,theta) on
  the same photo on two rows -> `INVALID_DUPLICATE` on the later row.
- A photo that is never boxed in a boxed import is accepted and reported as a warning (count),
  not an error: it is imported without detection (§7 decision 8).
- Parity promise: `validateOnly` performs every check that does not require decoding the full
  image. A file that decodes badly still fails at MediaAsset creation and is reported through the
  existing file-error list; with row tolerance the slot is skipped as today and its box with it
  (the existing WARN gains the box case).
- All new errors carry `rowNumber` + `fieldName`; `BulkImportTableReview` needs no change.

### 4.3 Importer (`BulkImporter.processRow`)

For a boxed slot, instead of the trivial annotation:
- `FeatureType bboxType = FeatureType.load("org.ecocean.boundingBox", myShepherd)` once per
  importer; a new `MediaAsset.generateFeatureFromBbox(FeatureType, w, h, x, y, params)` overload
  builds the feature with `{theta, viewpoint?, _bulkImportBbox:{...}}` in `params`. Never the
  string constructor; never `FeatureType.initAll` inside the import transaction.
- `new Annotation(null, feature, iaClass)`; `setAcmId(getId())`; `setMatchAgainst(true)`;
  `setWbiaRegistered(FALSE)`; `setWbiaRegisterAttempts(0)`; `setViewpoint`; `setQuality` from
  `Encounter.quality{N}`; `setSkipAutoIndexing(true)`. Identification status stays null.
- Linkage as `MlServiceProcessor`: `ma.addFeature` (done by the helper), `feature.setAnnotation
  (ann)`, `ann.addFeature(feature)`; the annotation joins the row's encounter via
  `enc.addAnnotations`.
- `ma.setDetectionStatus` is NOT called at import: the boxed asset's detectionStatus stays unset,
  so the visibility gate treats it as unfinished until every box on it is extracted (§4.4); the
  status page reports detection skipped through `annotationsImported`.
- Boxed annotations are collected and indexed explicitly after the commit (added to the
  `needIndexing` list for `bulkOpensearchIndex`); later embeddings re-index through the version
  bump.
- Persistence proof: a Testcontainers test runs `createImport` with boxed and un-boxed slots and
  a photo shared by two rows, closes and reopens the persistence context, and checks the
  `ANNOTATION_FEATURES` and `ENCOUNTER_ANNOTATIONS` rows, feature parameters, encounter ownership
  and `storeNewEncounter`'s return value.
- Row identity: the row's `MarkedIndividual.individualID` and every other row field apply to the
  encounter exactly as today; the boxed annotation belongs to that encounter, so an identified
  row stays identified. Identification runs for such rows as it does today for detected
  annotations on identified encounters; this design adds no rule for it.

### 4.4 IA hand-off (skip detection; extract, then identify)

Envelope and partition
- `IAGateway.handleBulkImport` keeps deriving its asset list from `itask.getMediaAssets()` (the
  supplied map stays ignored, so `SubmissionProcessingTest` and the `/ia` POST contract are
  untouched) but, when the import carries any `_bulkImportBbox` annotation, sends NO `mediaAssetIds`
  and instead the imported boxes as `annotationIds` with `annotationMode:
  "extract-then-identify"` (decision 8: a boxed import never detects). The mode is exclusive: an envelope carries either plain
  `annotationIds` (today's identification request) or moded ones, never both; `handleRest`
  rejects a mixed envelope with a logged error.
- `initiateIA` proceeds when the import has any encounter with media (not only when the detection
  map is non-empty).
- Durability: the root/child tasks are committed with `commitDBTransactionWithStatus`; a failed
  commit or a false `publishToLane` marks the root task error (`IA.markTaskError`), logs on the
  ImportTask, and returns `success:false` instead of today's unconditional true. Extraction and
  identification children are persisted with the same checked commit before each publish, through
  one helper extracted from `enqueueOneAssetForMlService` (persist, commit with status, publish,
  report) that both detection and extraction use.
- `laneFor`: a `v2` envelope whose `annotationIds` carry the extraction mode -> detection lane
  (checked before the plain `annotationIds` rule, which keeps its IA-lane precedence).
- Boxed imports never detect (§7 decision 8): when `annotationsImported` is set on the
  ImportTask (at least one non-empty box value validated; the persisted `_bulkImportBbox` markers
  name the annotations), the envelope has NO `mediaAssetIds`, only the moded `annotationIds`; un-boxed photos in it are imported
  untouched (trivial annotation, no detection) and the species topTask holds only boxed assets.
  `doPost` calls `initiateIA` for a boxed import regardless of the skip boxes, because extraction
  always runs; the user's `skipIdentification` (implied by "Skip Detection and Identification",
  BulkImport.java:247) becomes the envelope's `skipIdent`, so a skipped re-ID stores embeddings
  and creates no run. The ImportTask passed parameters gain `annotationsImported:true`;
  `skippedDetection()` is true for such imports and `skippedIdentification()` no longer follows
  from it when `annotationsImported` is set; the task status is `imported`, not `complete` (:609).
  Un-boxed imports are unchanged. A boxed import whose boxes all failed with their rows has no
  annotation ids: `handleBulkImport` marks the root completed with a log line, and the summary
  shows detection skipped and identification not started.

`IA.handleRest`, extraction mode (its own try/catch, separate from the media branch's):
- Read `skipIdent` once from the root (`IBEISIA.taskParametersSkipIdent`).
- Group the annotations by species (taxonomy of their encounter; a missing taxonomy ends in a
  terminal error task holding the annotation).
- Species with active ml-service configs (same test as detection, IA.java:173): one topTask per
  species under the root (`new Task(root)`, `scrubRunParams`, `setObjectMediaAssets` = that
  species' assets in this import); per annotation a child `new Task(topTask)` holding the
  annotation and its media asset (`addObject(ann)`, `addObject(ma)`, for the gate's
  single-asset-child rule), parameters `embeddingExtraction:true` and `importedBox:true`
  (task parameters, so deferred re-fires and replays read them from the task, never from the
  job), status `initiated`, `queueResumeMessage` = the job; `ann.setIdentificationStatus(
  processing-mlservice)`; job `{mlServiceV2:true, annotationId, taxonomyString, taskId: child,
  __context, __baseUrl}` on the detection queue via the checked helper. With `skipIdent` the
  extraction still runs (embeddings exist for a later New Match) and `waitAndRunMatch` skips
  matching, as ml-service detection does.
- Species without ml-service and `skipIdent` false: after the skip guard, reuse
  `MlServiceProcessor.runMatchProspects(ids, rootId, null)` for its encounter grouping,
  inherited parameters and recovery (the builder confirms the null match-config path produces
  WBIA-only runs; otherwise call `IA.intakeAnnotationsUnder` per group and check
  `IntakeOutcome.persistFailed`). With `skipIdent`: nothing (boxes are imported; no run).
- Any enqueue/persist failure inside the branch: `IA.markTaskError` (made accessible) on the
  child that holds the annotation.

Lifecycle of an extraction child (the simpler lifecycle from Codex round 3; replaces
"attach on error"):
- The child holds its annotation from creation and stays NON-terminal (`initiated`, then
  `processing`) through extraction and any gate deferral. Status math therefore reports the box
  as pending until a run exists (rule-2 fallback reads the child), then switches to the run-aware
  status. For children whose task parameters carry `importedBox`, completion happens at ONE
  place, the common matching hand-off that every exit passes through (READY, GIVE_UP, failed
  DEFER publication, the deferred re-fire in `runDeferredMatch`, and both skip guards): the
  child is marked completed only after the run is durably created, or at the skip guard when
  `skipIdent`. A successful DEFER publication returns `ok` without a run, so an `ok` outcome
  alone never completes an imported child. Loss of the species' ml-service configuration
  (`pending-species`) and stale outcomes (annotation or media asset gone) are recorded as `error`
  on imported children, because the import summary counts only `completed` and `error` as
  terminal. The inactivity watchdog (seven days on `modified`) remains the last backstop.
- Recovery, at parity with the ml-service detection path (deliberate v1 scope): the startup
  stale reconciler gains a second query that re-publishes, once per startup, the
  `queueResumeMessage` of non-terminal `importedBox` children older than the existing one-hour
  threshold (first consumer of that field; the child query is independent of the media-asset
  query). The import task page's existing "Send to identification" action
  (`appadmin/resendBulkImportID.jsp`) re-runs identification for the import's annotations and
  covers imported boxes too (the builder verifies it reaches boxes without embeddings; on
  vector species `findMatchProspects` then extracts on the fly, acceptable for a manual
  re-send). `embedding-exists` on an `importedBox` child continues to gate and match instead of
  returning, so both remedies actually produce the run. There is no periodic sweep, no durable
  dispatch pointer and no strike accounting in v1: the crash windows (queue acknowledges before
  processing; crash between embedding commit and match dispatch; crash between run commit and
  option dispatch) are exactly the ones the detection path has today, with the same remedies,
  and duplicate work after a replay or re-send is tolerated as it is today (run-aware status
  selection picks the newest run). Stronger recovery (periodic sweep counting execution
  attempts rather than republications, a locked dispatch pointer, resumption of partially
  dispatched runs) would benefit detection equally and belongs in its own PR; it is a decision
  in §7.
- Failure: non-retryable extraction errors mark the child `error` as today.
- Root failure before `handleRest` runs (checked commit failed, or `publishToLane` returned
  false): `handleBulkImport` opens a fresh transaction, creates ONE error task under the root
  holding every boxed annotation, commits with status, logs on the ImportTask and returns
  `success:false`. Task-less boxes can then never stay pending.
- Marker hygiene: `matchIntakeParams` calls `scrubRunParams` and strips `embeddingExtraction`,
  `importedBox` and `ibeis.detection` unconditionally (keeps `importTaskId`, `skipIdent`,
  `matchingSetFilter`), so match umbrellas never carry orchestration markers; the deferred-match
  payload needs no new field.

Ordering and the visibility gate (symmetric within-import matching, §7 decision 4)
- Today's ml-service detection path already gives symmetric within-import matching: phase 1 of
  the gate defers a child's match until every sibling asset of the species topTask is
  detection-terminal (MA status in `TERMINAL_MA_STATUSES` or the asset's single-asset child task
  terminal), and the ml-service pipeline stores each asset's embeddings with its detections, so
  when phase 1 passes every sibling annotation is eligible in phase 2. `MAX_DEFER_AGE_MILLIS`
  (12 minutes, measured from a child's first deferral and evaluated whenever a deferred match
  re-enters the gate) returns GIVE_UP and runs the match against the siblings ready so far; a
  failed deferred-match publication also runs the match immediately. Early photos of a very
  large import on the serial queue can therefore match against a partial set; late ones see
  everything.
- The gate is NOT changed. A boxed asset is imported with its detectionStatus unset (§4.3), so
  phase 1 treats it as unfinished, and the processor settles it: after storing a box's embedding
  (and on every terminal error of an imported-box child, and in the `embedding-exists` branch)
  `MlServiceProcessor.settleImportedBoxAsset(ma)` sets `detectionStatus = complete` once every
  `_bulkImportBbox` annotation on that asset either has an embedding (any method: each box is
  extracted by its own class's extractor, so a sibling of another class is never waited on for
  the caller's method) or has a terminal imported-box task. The extraction child holds its asset
  as well as its annotation (`addObject(ma)`), so the existing single-asset-child rule finds it;
  two boxes on one photo are two such children, and the SQL's existential rule defers until the
  asset settles. Imported children stay non-terminal until their run exists (the lifecycle
  above); no deadlock, because settle happens before the gate runs.
- Result: every box's match sees every other same-species box in the import whose extraction
  finished (there are no mixed imports after decision 8: the topTask holds only boxed assets);
  the two existing fallbacks above are the only asymmetry, shared with detection. Boxed assets
  reach `complete` only through settle; the startup reconciler keys on `processing-mlservice`
  and never re-detects a boxed asset.

Alternative considered and rejected: send the boxes as a plain `annotationIds` identification
request (IA lane) and rely on `Embedding.findMatchProspects`' on-the-fly extraction. It deletes
the extraction jobs, but it extracts synchronously inside one identification run (one long
transaction per encounter group, no per-box retry, no class-aware multi-pipeline extraction, no
visibility wait) and leaves no durable per-box task to replay. Lane footprint is not the reason:
extraction jobs share the serial detection lane exactly as detection of the same photos would
(one job per box instead of one per photo), and interactive New Match uses the fastlane when it
is enabled, falling back to the detection lane otherwise. Kept here so the question is not
reopened.

### 4.5 Status (`ImportTask.iaSummaryJson` / `statsAnnotations`): skip-method changes only

- Boxed import: detection reported skipped (`annotationsImported`); identification pending (the child is
  non-terminal) until the run's umbrella exists, then the run-aware status; every failure is an
  `error` child holding the annotation; a crash is recovered at the next restart or by the
  page's re-send action.
- Un-boxed photos inside a boxed import: imported untouched; the summary reports detection
  skipped for the whole import.
- Un-boxed import: untouched. `skipIdent` imports: on ml-service species the extraction child
  completes at the skip guard (embeddings stored, no run); on other species the boxes stay
  task-less; the import reports identification skipped as today.
- Boxed import with re-ID skipped: the extraction children complete at the skip guard;
  identification reported skipped.

### 4.6 Frontend (React bulk import)

- Slot identity: when any `Encounter.mediaAsset{N}.bbox` column exists, the whole sheet is an
  exact-slot import (§4.1): the page keeps numbered photo columns end to end for every row (no
  merge into one cell, no renumbering) and renders
  `Encounter.mediaAsset{N}` and every `Encounter.mediaAsset{N}.*` column read-only in the grid
  (fix in the CSV and re-upload). Every slot-zero consumer iterates numbered slots:
  `missingPhotos`, upload validation, `EditableDataTable` reconciliation, and
  `isDynamicKnownColumn` accepts bare `Encounter.mediaAssetN`. Un-boxed sheets keep today's
  merged cell.
- `isDynamicKnownColumn`: accept suffixes `bbox|iaClass|viewpoint|theta|bboxSource`; the generic
  labeled-keyword rule excludes them. Client rules: bbox regex, theta finite, viewpoint in
  `siteSettings.annotationViewpoint`, iaClass in the species' `bulkImportMatchableClasses` list,
  skipped when either skip checkbox is on (the server then accepts any canonical class). Bounds,
  duplicate and cross-row checks stay server side.
- Errors from the server surface on the Import click as today (400 -> failure modal + review
  table); nothing is created on a 400. A pre-flight validate-only call is a follow-up.
- Capability signal: `SiteSettings` adds `bulkImportIndexableFields` (= `FIELD_NAMES_INDEXABLE`),
  used by the page and the skill to know the site accepts box columns.
- Locale keys (en/de/es/fr/it) for new client messages and one instructions-modal paragraph.
- The two skip boxes keep their meaning; the instructions-modal paragraph states that a sheet
  with box columns never runs detection, that photos without a box are imported untouched (a
  second import without box columns gets them detected), and that "Skip Detection and
  Identification" on a boxed sheet still stores embeddings for the boxes on species with an
  ml-service extractor. The client warns with
  the count of un-boxed photos in a boxed sheet before the Import click.

### 4.7 Agent skill: `import-your-own-detections`

Served at `/api/v3/agent-skill/import-your-own-detections`, registered in `AgentSkill`, listed in
`index.md` under "Get sightings into Wildbook (import prep)"; structure as
`inat-to-wildbook-import.md`. The API route comes first, the page second; `submit-sightings.md`
gains the per-photo fields (§4.9). Content:
- First step, capability check: for the API route `GET /api/v3/submissions/capabilities` must
  list `bbox` under `rowFields.perMedia`; for the page route `/api/v3/site-settings`
  `bulkImportIndexableFields` must list `Encounter.mediaAsset#.bbox`. Otherwise stop and say the
  site does not accept animal boxes yet (an older site would reject or silently drop those fields
  and run its own detector).
- Inputs: photo folder (frames already extracted), detector output (COCO JSON, YOLO txt, VOC XML,
  CSV), species, location ID, submitter.
- Steps: read the species' matchable class names and the viewpoint list from site settings; map
  model labels (ask when ambiguous, never guess); read each photo's pixel size as stored (no EXIF
  transpose; warn when an orientation tag is present and offer to rotate first); convert boxes
  with the §4.1 rounding; drop boxes under the confidence floor; one row per animal per photo,
  the same photo repeated for several animals, several photos in one row only for the same
  animal; whole-photo boxes allowed; leave theta out unless the detector's rotation convention
  was verified against the documented sign; `.bboxSource` = model name;
  `Encounter.quality{N}` = score; the 200-row guidance; then either push through the submissions
  API (follow `submit-sightings` for the token, draft, uploads, rows, validate, fix, commit and
  status; the per-photo fields ride in the same row objects; the same photo may be used on
  several rows only when every use is boxed) or hand the sheet to the Bulk Import page.
- Report plainly; never claim anything was imported.
- Cautions: the photo's own pixel grid; boxes on every row a photo appears in or none; Wildbook
  never runs its detector on any photo in an import that carries boxes, so a photo without a box
  is imported untouched: for detection on those photos make a second, separate import without
  box columns; one box per photo per row; large frame sets multiply
  matching work; two frames of the same animal in different rows of one import show as a match
  on both frames' results.
- Vocabulary: "box"/"animal box", "animal type label"; no "annotation", "embedding", "iaClass"
  outside "How to do it".
- Tests: import-prep pattern (`AgentSkillContentTest`:247), catalog exclusion (:221), vocabulary
  and index-link assertions.
- Follow-ups outside this repo: local `wildbook-import` skill; wildbook.docs page.

### 4.8 Tests

- `BulkValidator` unit cases per field; `BulkImportUtil.validateRow` pairing, matchable-list
  widening under skip flags, duplicate-in-row, class error text.
- `BulkImport.doPost` file layer in both modes: bounds, identical-box, cross-row; fixtures JPEG
  (plain, EXIF-rotated, progressive), PNG, BMP, GIF, header-readable corrupt PNG/GIF; header vs
  stored dimension equality.
- `BulkImporter.processRow` + Testcontainers persistence test (§4.3); exact-slot metadata on
  every row of an import with box columns, positional on imports without them.
- Hand-off: `handleBulkImport` sends no `mediaAssetIds` for a boxed import, carries moded
  `annotationIds`, checked
  commit, false publish -> error task holding every box + `success:false`;
  `SubmissionProcessingTest` keeps its empty-map expectation but stubs the checked commit;
  `laneFor` precedence; `handleRest` mode exclusivity, species split, skipIdent on both
  branches, boxed-import envelope without `mediaAssetIds` regardless of the skip boxes,
  `annotationsImported` and status `imported`, per-branch
  failure isolation, enqueue failure -> error child.
- Lifecycle: child non-terminal through extraction and DEFER (no false completion); completed at
  the common hand-off only (READY, GIVE_UP, failed DEFER publish, deferred re-fire, skip guards);
  `ok` from a DEFER publication does not complete; stale and configuration loss -> error;
  `embedding-exists` continues to match; a restart after a simulated crash between embedding
  commit and match dispatch, with the child older than the threshold, is replayed at startup and
  produces the run; the page's re-send produces a run for a box left without one; every
  box's match includes every other same-species box in the import (symmetry), a deferred box's
  match still waits for its siblings' embeddings, GIVE_UP after 12 minutes runs with the partial
  set, and a sibling without the imported-box marker follows the old rule; marker scrub
  unconditional; an
  un-boxed row with slot gaps inside an exact-slot import keeps its keywords and quality.
- `ImportTask.iaSummaryJson`: boxed with and without the skip boxes, un-boxed photos inside a
  boxed import, un-boxed, skipIdent, failed-enqueue.
- Frontend jest: slot preservation and read-only columns with box columns present, dynamic column
  recognition, client rules; `npx jest --ci` recipe.
- Staging on flakebook.wildme.org (access from the user when PR B lands): HotSpotter-only, mixed
  HotSpotter + MiewID, and MiewID-only species configurations; each with all-boxed, boxed-with-un-boxed-photos
  and multi-animal-per-photo imports through the API and the page, with and without the skip flags,
  plus a forced ml-service outage during extraction.

### 4.9 Submissions API (`/api/v3/submissions`, primary route)

Third-party pushes are the primary way boxes will arrive, so the push API takes the same per-photo
fields as the spreadsheet with the same validation, in this PR series (PR E).

- Fields: `SubmissionValidator.supported` accepts `Encounter.mediaAsset{N}.bbox|iaClass|viewpoint|
  theta|bboxSource` and `Encounter.quality{N}` for N in 0-199 (the same regex family as
  `Encounter.mediaAsset{N}`); anything else stays `UNSUPPORTED_FIELD`. The two media scans
  (`SubmissionValidator.validate`:57 and `SubmissionImporter.execute`:40) switch from
  `startsWith("Encounter.mediaAsset")` to `BulkImportUtil.findIndexedFieldNames(fields.keySet(),
  "Encounter.mediaAsset")` (:224, nulls skipped) so only bare numbered slots are filenames.
  `capabilities()` advertises the suffix list as `rowFields.perMedia` and adds `Encounter.quality#`
  to `rowFields.supported`; the skill checks `perMedia` before preparing boxed rows, as the page
  route checks `bulkImportIndexableFields`.
- Row validation is shared: `validate` and `execute` both run `BulkImportUtil.validateRow`, so the
  §4.2 row rules apply unchanged; both passes receive the skip flag derived from the processing
  mode (`import-only` = skipped). The `.bbox` validator value is the canonical string, so it
  survives `normalizedRows` and revalidation unchanged. `SubmissionValueIssues.explain` gains
  reasons for the new fields (`INVALID_BOX`, `MISSING_CLASS`, `UNKNOWN_CLASS`, `INVALID_VIEWPOINT`,
  `INVALID_THETA`), each with the plain message the page shows.
- Image reuse: today `DUPLICATE_MEDIA` rejects the same upload on two rows ("Use each image in one
  row only"). With boxes, the same photo may appear on several rows when every one of those slots
  is boxed and the boxes differ (one animal per row); an un-boxed repeat, or a boxed and an
  un-boxed use of one photo, stays `DUPLICATE_MEDIA` with the §4.2 cross-row message; an identical
  box twice -> `DUPLICATE_BOX`. Within one row a photo still appears once.
- File-level checks at `/validate` and again at commit (`execute` re-validates): `verify(entry)`
  already reopens, hashes and inspects every staged file; it now returns the width and height
  that `inspect` already reads from the header, and `validate` checks box bounds from them. No
  manifest fields are added. `inspect`'s header-reading portion becomes the shared helper behind
  `Util.fastImageDimensions`, without the submissions format and pixel limits. Out of bounds ->
  `INVALID_VALUE` reason `BOX_OUT_OF_BOUNDS`.
- Importer: `execute` runs `BulkImporter.createImport` on the validated rows and `BulkImporter`
  derives exact-slot mode from the rows. `SubmissionJobs.enqueue` (:58) stamps the ImportTask
  passed parameters with `annotationsImported:true` and `skipDetection:true` for a boxed draft;
  `execute` sets the task status `pending-detection` when the draft requests AI (below).
- Boxed import-only drafts (decision 8): `Submission` gains a persisted `boxed` flag, set at
  queue time from the validated rows (any non-empty `.bbox`), and `requestsAi()` =
  `requestsIdentification() || boxed`. `imported()` (:96), `getAiState()` (:46), `pending()` and
  `dispatch` eligibility (SubmissionProcessing:47) and `aiPhase()` use `requestsAi()`; `prepare`
  sets `skipIdent = !requestsIdentification()`; `aiPhase()` reports detection `skipped` for a
  boxed draft and identification per mode.
- Hand-off: `prepare` builds its message through the shared `IAGateway.buildImportEnvelope(...)`
  (reads `annotationsImported` from the ImportTask; returns the moded `annotationIds` or the asset
  ids), so `SubmissionProcessingTest:74` keeps its empty-map expectation and the legacy-contract
  test keeps passing for un-boxed drafts. The dispatch and `aiState` machinery is unchanged: an
  ambiguous publication (`publishChecked` can throw after the atomic move) stays `unknown`; the
  §4.4 false-return handling is NOT transplanted here.
- Status: `GET /api/v3/submissions/{id}` keeps its shape; the ImportTask summary math is §4.5.
- Docs and skill: the `openapi.yaml` rows schema gains the fields; `submit-sightings.md` gains the
  per-photo rows in its field table, the image-reuse rule, the `perMedia` capability and the
  boxed import-only semantics; `import-your-own-detections` (§4.7) leads with this route.
- Tests: `SubmissionValidatorTest` field acceptance, bare-slot media scan, duplicate-media
  relaxation, bounds from `verify`, issue reasons, canonical `.bbox` round trip; `Submission`
  state (`boxed`, `requestsAi`, `imported`, `getAiState`, `aiPhase`); `SubmissionProcessingTest`
  envelope through the shared helper, import-only-with-boxes dispatch eligibility, unknown on
  ambiguous publication; an importer test with boxed rows through `execute`; the capabilities
  JSON; staging on flakebook through the API in all three IA configurations (§4.8).

| PR | Scope | Est. |
|---|---|---|
| A Hand-off (behavior-neutral) | §4.4: boxed-import envelope in `handleBulkImport` (no `mediaAssetIds`), moded `annotationIds`, checked commit/publish with the root-failure error task, `laneFor` rule, `handleRest` extraction mode with both branches and failure isolation, extraction-child lifecycle (single finalization point, stale/config-loss->error, `embedding-exists` continuation, unconditional marker scrub), startup replay from `queueResumeMessage`, re-send coverage check, `annotationsImported` status semantics, shared checked enqueue helper, visibility-gate extension for boxed siblings (decision 4), lifecycle and restart tests. Existing imports carry no boxed annotations and behave as before. | 8-12 days |
| B Fields + validation + importer (activates) | §4.1-4.3, `fastImageDimensions`, reserved suffixes, exact-slot imports, `getMatchableIAClasses` + `bulkImportMatchableClasses` + `bulkImportIndexableFields`, unit + Testcontainers tests, `openapi.yaml` note, staging runs. Merges only after A. | 3-5 days |
| E Submissions API | §4.9: field acceptance, bare media-field scans, duplicate-media relaxation, dimensions from `verify` and bounds, issue reasons, capabilities, shared envelope helper in `prepare`, boxed import-only drafts (`boxed`, `requestsAi`, state, dispatch, `aiPhase`, ImportTask flags), `openapi.yaml`, `submit-sightings.md` field table; tests. Merges after B. | 4-6 days |
| D Agent skill | §4.7 with the API route first and the page second; content test. Merges after E. | 2-3 days |
| C Frontend | §4.6, locale x5, jest. | 2-3 days |

Total 19-29 developer days plus a Codex round per PR and the staging runs; six to eight
calendar weeks. Codex's own estimate before decisions 4 and 5 was 13-20. No feature flag. Follow-ups (not estimated): pre-flight validate-only on the page, the recovery sweep, local
skill, docs site, WBIA matching-locations bug.

## 6. Risks

- Coordinate frame: EXIF-rotated, mirrored or 180-degree rotated frames can stay in bounds with
  wrong geometry; mitigated in the skill, not detectable server side.
- The gate itself is unchanged (§4.4): a boxed asset's readiness is expressed through its
  detectionStatus, settled by the processor once every box on it is extracted or errored. Two
  existing fallbacks still run a match against a partial corpus: the 12-minute age-out, evaluated
  whenever a deferred match re-enters the gate, and a failed deferred-match publication.
- Recovery is at parity with the detection path, not better: a crash within an hour of a
  restart is recovered only at the following restart or by re-send. Codex round 5 rated the
  periodic-sweep alternative incomplete as specified (dispatch resumption, strike accounting
  for queued work, parameter-write races); doing it properly is its own PR (§7 decision 7).
- The extraction-child lifecycle changes inside `MlServiceProcessor` touch shared matching code;
  each is keyed on the `importedBox` task parameter and covered by a test, and the marker scrub
  is the one unconditional change (it removes only orchestration markers).
- Theta sign: the wire convention is fixed (clockwise on screen); the fixture verifies both
  services and any conversion happens at the service boundary.
- Non-unity feature persistence through the importer cascade is proven by the Testcontainers
  test, not assumed.
- Large frame sets multiply annotations and match runs on a serial queue.

## 7. Settled calls and decisions for the user

Fixed in this spec: provenance `_bulkImportBbox`; request-local file cache; existing error
envelope; no status counter; `FeatureType.load(id, shepherd)`; quality on boxed rows = detector
score; whole-photo boxes allowed; matchable-class rule with `_save_as` targets and the default
flag; exact-slot semantics per import; child-held annotation with one finalization point;
symmetric within-import matching through asset settle in the processor (gate unchanged);
recovery at parity with the detection path (startup replay + re-send); theta clockwise on
screen; no feature flag; a boxed import never detects, always extracts, identifies unless skipped;
PR order A -> B -> E -> D -> C.

Still the builder's: exact error wording; locale wording; fixture images; the theta fixture.

User decisions (answers change what is built); my default first:
1. DECIDED 2026-11-07: pixel `x,y,width,height` in the stored image's own grid as the only wire
   format; the skill converts other formats.
2. DECIDED 2026-11-07: `.iaClass` is always required on a boxed slot; no defaulting.
3. DECIDED 2026-11-07: reject classes with no identification option (unless identification is
   skipped).
4. DECIDED 2026-11-07: symmetric within-import matching; a boxed asset counts as finished once
   every box on it is extracted or errored (asset settle in the processor; the gate is
   unchanged; included in the PR A estimate).
5. DECIDED 2026-11-07: the submissions API is in this round as the primary route (PR E, §4.9);
   the skill documents it first.
6. DECIDED 2026-11-07: staging on flakebook.wildme.org (the user grants access): HotSpotter-only,
   mixed HotSpotter + MiewID, and MiewID-only imports.
7. DECIDED 2026-11-07: crash recovery at parity with the detection path (startup replay after
   one hour, plus the page's re-send); the sweep is a later PR.
8. DECIDED 2026-11-07 (keep it simple): an import that carries boxes never runs detection;
   every box on a species with an ml-service extractor gets an embedding; re-ID runs unless the
   user skipped it ("Skip Detection and
   Identification" or "Skip only identification" on the page, `import-only` on the API). Photos
   without a box in such an import are imported untouched; for detection on them the user makes
   a second, separate import without box columns, and the skill says so.
