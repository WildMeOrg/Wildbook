# Codex review log: bulk import of user-supplied bounding boxes

Companion to `2026-11-06-bulk-import-annotation-bboxes-design.md`. Each round was `codex exec`
(read-only sandbox) over the full design plus the cited source files; findings below are
condensed to what changed the design. Full outputs live in the session scratchpad.

## Round 1 (rev 1 -> rev 2): 16 findings, 11 Major

- Whole-image boxes: Codex argued `isTrivial()` would treat `0,0,W,H` as a placeholder. Rev 2
  rejected them; round 2 corrected this (feature-only boxes are never trivial) and rev 3 allows
  them again.
- Shared legacy extraction task (`MLService` + fake `embeddingExtraction` callback) can fire
  identification early or once per job on installs without `_mlservice_conf`. Dropped; rev 2
  uses the existing v2 `processExtraction` job per annotation.
- Legacy route bypasses the `MatchVisibilityGate`; v2 extraction keeps it.
- `handleBulkImport` rebuilds `mediaAssetIds` from `itask.getMediaAssets()`; a filter there
  covers both detectors. Inheritance of `importTaskId`/`skipIdent`/`matchingSetFilter` made
  explicit; `intakeAnnotationsUnder` instead of `intakeAnnotations`.
- Status math: annotations with no task stay pending; terminal outcomes required for dispatch
  failures.
- `new Feature(String, ...)` depends on the static `FeatureType` cache; `initAll` rolls back the
  open transaction. Use `FeatureType.load(id, shepherd)`.
- Header-only dimension checks cannot promise full validate-only/import parity; promise narrowed
  to "every check that does not decode the image", plus `fastFileValidation` in both modes.
- Frontend merges photo columns into one cell and renumbers on submit; box columns would attach
  to the wrong photo. Numbered slots preserved when box columns exist.
- Two class universes (`getValidIAClasses` vs `getValidIAClassesIgnoreRedirects`); one list.
- Theta semantics, finiteness, overflow-safe bounds, deterministic rounding.
- Quality reuse: define as detector score with provenance.
- Simpler design: drop the `processCallback` patch and the embed-task bridge. Adopted.
- Indexing rule misstated (matchAgainst OR embeddings). Corrected.
- PR1 feature flag unjustified; infrastructure-first ordering instead. Adopted.
- `AgentSkillContentTest` catalog assertion needs an exclusion for the new skill.

## Round 2 (rev 2 -> rev 3): 12 findings, 7 Major

- Gate never waits on boxed siblings (terminal at import; eligibility needs embeddings).
  Rev 3 documents best-effort within-import coverage instead of changing the gate; offered as a
  user decision.
- Extraction child marked completed before the gate: with the annotation attached, DEFER
  over-reports identification. Rev 3 removed the annotation from the success-path child; rev 4
  replaced that with the child-held, non-terminal lifecycle.
- `embedding-exists` returns without matching: replay after a crash loses identification.
  Continuation added.
- Direct intake branch ignores `skipIdent`; no-option classes end in `INVALID_MATCH_CONFIG`.
  Skip guard added; matchable-class validation rule added.
- Unchecked commit and `success:true` after a failed publish in `handleBulkImport`. Checked
  commit and publish result required.
- Slot preservation incomplete (slot-zero consumers; positional keywords). Listed and fixed.
- Simpler orchestration: reuse the `annotationIds` envelope with a mode; isolate branch
  failures; mutual exclusivity. Adopted. The on-the-fly extraction alternative was examined and
  rejected (synchronous, serial, no retry, no visibility wait).
- `embeddingExtraction` marker inheritance; unconditional scrub at the matching boundary.
- Dimension-equality qualification; whole-image rationale corrected; theta sign; PR A neutrality
  (`SubmissionProcessingTest` empty map). Estimate raised to 11-17 days.

## Round 3 (rev 3 -> rev 4): 9 findings, 5 Major

- "Exactly once, by the later box" fails at DEFER and retry reordering; coverage is best effort.
- Queue acknowledges before processing; startup recovery keys on media-asset status; task-less
  boxes pending forever. Persisted continuation required.
- Root publish failure before `handleRest` leaves task-less boxes; `dropped-stale` is not a
  terminal identification status. Error task holding every box; stale -> error.
- `identOpts` keeps `default:false`; `getValidIAClassesIgnoreRedirects` omits `_save_as`
  targets. Derived matchable list with the intake's default rule.
- Sheet-wide slot preservation vs row-level exact-N semantics conflict. Exact-slot per import.
- Lane rationale wrong (New Match uses the fastlane and falls back to detection). Corrected.
- Job-only `importedBox` gating lost on DEFER; use task parameters and scrub unconditionally.
- `SubmissionProcessingTest` mocks Shepherd without the checked commit; stub required.
- Theta needs a signed convention. Fixed as clockwise on screen, two-service fixture.
- Simpler lifecycle offered: child holds its annotation, non-terminal until the run exists.
  Adopted in rev 4.

## Round 4 (rev 4 -> rev 5): 4 findings, 1 Major

- Startup-only replay with the existing one-hour threshold misses a quick restart. Rev 5:
  periodic sweep, fifteen-minute raw `modified` threshold, three-strike cap.
- Replay can duplicate matching; add `matchRunId` hand-off and consumer re-check.
- Finalization must sit at the common matching hand-off (READY, GIVE_UP, failed DEFER publish,
  deferred re-fire, skip guards); `ok` from a DEFER publication never completes; config loss ->
  error.
- Residual text contradictions (skill pair wording, positional tests, skipIdent wording, client
  class check under skip flags). Fixed.
- Verdicts on request: no simpler design for the retry, gating and recovery requirements; reuse
  `runMatchProspects`, `scrubRunParams`, `markTaskError`. Estimate 13-20 days.

## Round 5 (rev 5 -> rev 6): 2 Major, 1 Minor; NOT converged on recovery depth

- Quick-restart fix, single finalization point and the four text fixes: resolved.
- `matchRunId` records run creation but defines no protocol to resume a run whose options were
  never dispatched (intake commits the run before dispatching; a crash right after that commit
  leaves a pointer that suppresses replacement without continuation).
- The ten-minute sweep would strike healthy children still waiting in the serial detection
  queue and terminalize them before they ever ran.
- `replayCount`/`matchRunId` share the serialized parameters field; a sweep holding stale
  parameters can overwrite a freshly committed pointer (no version protection on Task).

Rev 6 decision (mine, not a Codex fix): each round of added recovery machinery produced a new
recovery surface, the classic over-engineering signal. v1 scopes recovery to parity with the
ml-service detection path, which has the same crash windows in production today: startup
replay of stale children (existing one-hour threshold) plus the page's existing "Send to
identification" action, with `embedding-exists` continuing to match so both remedies work.
The sweep/pointer design is recorded as §7 decision 7 (+3-5 days, own PR, benefits detection
equally). Rev 6 was not re-reviewed by Codex; Codex's last verdict on the mechanism it asked for
stands as "not converged", and the design says so.

## Round 6 (rev 7 deltas, 2026-11-07)

Verdict: not converged. Six Majors, three Minors; all applied in rev 8.

1. Gate could wait for another extractor's method/version on multi-pipeline species -> readiness is
   now expressed by the asset's detectionStatus, settled by the processor once every box on it has
   any embedding or a terminal task; the gate is unchanged.
2. "Every box gets an embedding" contradicted WBIA-only species -> qualified everywhere: extraction
   on species with an ml-service extractor; WBIA matching otherwise.
3. Submissions validator/importer treat every `Encounter.mediaAsset*` key as a filename -> both
   scans use `BulkImportUtil.findIndexedFieldNames`; skip flag on both validateRow passes; `.bbox`
   normalized value is the canonical string.
4. Import-only drafts are hard-wired to aiState "skipped" -> `Submission.boxed` + `requestsAi()`
   through imported/getAiState/dispatch/aiPhase; `annotationsImported` stamped by SubmissionJobs.
5. Dispatch failure rule must keep "unknown" on ambiguous publication -> §4.4 handling not
   transplanted; only the message changes.
6. "Box columns present" vs "persisted box exists" -> one predicate, `annotationsImported` (any
   non-empty validated `.bbox`); zero-survivor case specified; empty box columns = ordinary import.
7. 12-minute claim overstated -> age-out evaluated on re-entry; failed defer publish also runs.
8. Manifest dimensions unnecessary -> `verify` returns `inspect`'s width/height.
9. Stale statements (follow-ups, decision 7, "no code change") removed; PR E 4-6 days; total 19-29.

## Plan A round 1 (2026-11-07)

Verdict: not converged. Nine Majors, three Minors; all applied in plan rev 2.
1. Hand-off recovery: one persist boundary (tree + envelope + resume message), one fresh-transaction
   recovery for commit and publish failures with an error child holding every box; failed recovery
   commit logged and still `success:false`.
2. Species source: `Annotation.getTaxonomy` (encounter), not the asset's; settle ignores boxes
   that have no extraction task (WBIA-only species on a shared photo).
3. Envelope validation before any intake (`IA.envelopeRejection`), incl. extraction mode + media.
4. WBIA groups through `runMatchProspects`; extraction children staged then committed once before
   any publish; per-child ENQUEUE marks; `makePersistent` failures handled.
5. Every stale exit on an imported child writes `error` STALE and settles; skip-guard completion is
   one checked operation that reports failure.
6. `embedding-exists` continuation fills method/version through `ensureMatchConfig(mv)`.
7. Replay is duplicate-tolerant and says so; no pending-job check.
8. Neutrality stated exactly with its three failure-path exceptions; nothing stripped from clones.
9. Tests: `embedding_model_id` response shape, shared-asset fixtures, real gate with OpenSearch
   stub, status observed at dispatch, crash/restart/replay chain, single-box pending-species case.
10-12. Minors: `getStoredStatus`, `IAJsonProperties.iaConfig()`, regression labels, reuse of
   `scrubRunParams` and the existing test dispatchers, Shepherd argument dropped, threshold stays
   private in StartupWildbook.

## Plan A round 2 (2026-11-07)

Verdict: not converged. Seven Majors, Minors; applied in plan rev 3.
1. Staging exceptions before the single commit and WBIA dispatch exceptions -> input ids
   snapshotted first; one fresh-transaction recovery (error child holding every input box) for
   false commit AND exceptions; per-species WBIA dispatch isolated with a MATCH_DISPATCH error child.
2. Checked-finalization contract: stated as parity (existing processor commits stay unchecked, as
   for detection; replay/watchdog recover); the new skip-completion helper uses a strict load that
   distinguishes missing from datastore failure and reports a persistError.
3. ENQUEUE failure never settled the asset -> `ImportedBoxes.failChild` marks error and settles;
   tested with the deferred re-fire ordering.
4. Thrown publish/persist failures bypassed hand-off recovery -> exceptions routed to the same
   recovery; recovery guarded; throwing-mock tests added.
5. Boxed clone could keep `mediaAssetIds` -> routing keys replaced explicitly on boxed imports.
6. Restart test could not produce its run -> single-box fixture, crash simulated by a throwing
   gate after the embedding commit; deletion-during-extraction and deletion-before-re-fire tests.
7. Neutrality statement corrected: one successful-path metadata change (detection child resume
   message), three failure-path exceptions, reserved `annotationMode` validation.
8. Minors: zero-box decision before the commit; `new MatchVisibilityGateImpl(context, openSearch)`;
   gate-test factory overrides the new seam; regression labels; exact trailer lines.

## Plan A round 3 (2026-11-07)

Verdict: not fully converged; one Major, four Minors; the parity contract (existing processor
commits unchecked, no new catch-all; replay/watchdog recover) judged acceptable for v1. Applied in
plan rev 4; the next Codex pass is on the code.
1. A deleted annotation in the envelope aborted the whole extraction branch before any child
   existed -> `intakeImportedBoxes` takes the id list, loads null-tolerantly inside its staging
   try, and routes loading failures to the same recovery.
2. WBIA publication exceptions are handled by the existing intake (`QUEUE_FAILED`); a runner seam
   exercises the `MATCH_DISPATCH` recovery.
3. Deferred-deletion test settles the asset first so the re-fire reaches the stale exit.
4. `processorWithSkipIdent` is the factory to override.
5. Fourth neutrality exception: ordinary skipped jobs may report `persistError` on a datastore
   failure in the strict lookup.

## Branch A round 1 (2026-11-07, code review of feat/bbox-import-a-handoff)

Verdict: not converged. Three Majors, two Minors, neutrality edge cases, simplifications; all applied.
1. Hand-off recovery reused in-memory objects after a rollback (RestoreValues) -> reload the
   ImportTask and the root by id after the rollback, re-create the root when the commit never
   happened, restore the link explicitly, check the restarted transaction; real-Shepherd tests
   for a rolled-back commit and a failed publish (`HandoffRecoveryDbTest`).
2. `Shepherd.getAnnotation` swallows datastore failures -> `IA.strictAnnotation` (only
   JDOObjectNotFoundException means missing); base-URL and config resolution moved inside the
   staging try; recovery holders tolerate a per-id failure; datastore-failure test.
3. The settle flush could self-deadlock when the sibling query throws (second Shepherd on the
   same task row) -> `persistExtraction` rolls back before `markTaskError(String)`, as
   `persistDetections` does; the flush is mandatory (no catch-and-continue).
4. INVALID_ENVELOPE commit checked and logged.
5. Settle on every terminal write of an imported child: INVALID_MATCH_CONFIG, run completion,
   match-failure recovery, skip completion (empty-input completion stays accepted).
Neutrality edge cases now enumerated: a missing ImportTask returns a structured failure instead
of throwing; an ImportTask with a null media list publishes an empty envelope instead of throwing;
`matchIntakeParams` strips the two orchestration keys for every caller.
Simplifications applied: `Util.jsonArrayToStringList` instead of a new helper; the two
embedding-exists blocks collapsed into `finishExtractionTask`. Not applied: consolidating the
processor's null-safe `markTaskError(Task,...)` onto `IA.markTaskError` (behavior-preserving but
out of this PR's blast radius).

## Branch A round 2 (2026-11-07)

Verdict: not converged; round-1 findings 1, 3, 4, 5 resolved, 2 partially. One Major, two Minors;
all applied.
1. Recovery holders tolerated a lookup failure and could commit a tree omitting an existing box
   -> holders are built from already-loaded annotations where they exist (NO_TAXONOMY,
   MATCH_DISPATCH); the staging-failure recovery reloads by id and a datastore failure fails the
   recovery (rolled back, logged, boxes left pending) rather than committing a partial holder.
   Tests: transient lookup failure recovers holding every box; persistent failure commits no
   partial holder.
2. DB tests did not run production's `datanucleus.RestoreValues=true` -> added to the test PMF
   properties; `HandoffRecoveryDbTest` asserts it before injecting the rollback.
3. Intake recovery wrote without checking the restarted transaction -> `isDBTransactionActive`
   check; a dead restart attempts no recovery writes (test).
Reuse: the processor's `markTaskError(Task,...)` forwards to `IA.markTaskError`; one
`ImportedBoxes.settleHeldAsset` serves `failChild` and the processor.

## Branch A round 3 (2026-11-07)

Verdict: converged; no Critical, Major or Minor findings. Round-2 findings 1-3 resolved. Exit
audit clean under the accepted parity contract; neutrality holds against the stated exceptions;
the flush before `Task.getTasksFor` is to be kept; no simpler design and no reusable code for
asset settle and stale-task replay. Remaining coverage limits noted (they expose no defect): the
deferred ENQUEUE ordering test calls `failChild` directly; skip-failure tests substitute the
completion helper; the ordinary-lifecycle regression exercises extraction, not detection; no test
forces the post-flush sibling query to fail.

## Plan B round 1 (2026-11-07, plan rev 1 -> rev 2)

Verdict: not converged; 12 findings, 10 Major, no Critical; all applied.
1. `annotationsImported` was only written when true, so a client-supplied `annotationsImported:
   true` in the payload survived `initializeImportTask`'s copy -> the server writes the parameter
   on every import from its own predicate, overwriting any client value; forged true/false tests.
2. `initiateIA` overwrites the status with `processing-detection`, so "status stays imported"
   could not pass -> resolved in rev 2 by keeping `initiateIA` unchanged and stating the parity
   (round 2 accepted this over a rewrite).
3. `parseBbox(String)` could not carry the field name and `tryDouble` throws a two-argument
   exception -> `parseBbox(text, fieldName)`; every new case throws the four-argument exception
   with the real field name; null/blank guards before numeric checks; overflow, empty component,
   trailing comma and malformed quality cases.
4. Duplicate-photo scanning only looked at boxed slots, and the import-level pass sat after the
   error aggregation -> scan every bare slot; the pass runs before the aggregation loop and writes
   exceptions into the row maps.
5. The corrupt-image test contradicted the contract (`fastFileValidation` is MIME + size) and the
   64x48 fixtures could fall under the 1000-byte floor -> 200x150 noise fixtures; separate bad-MIME,
   unreadable-header and header-readable/undecodable cases; per-file caching.
6. `IAJsonProperties.iaConfig()` builds a fresh instance, so `iaConfig().setJson` configured
   nothing production code sees -> `TestIAJsonPropertiesUtil.override`; `shape` is matchable through
   the `_default._id_conf` fallback; lists sorted.
7. `_save_as` discovery must follow `@` links -> `get(...)` lookups.
8. Exact-slot mode triggered on any box suffix; the spec says a `.bbox` column -> `.bbox` only.
9. The DB test's "no errors" claim proved nothing about `storeNewEncounter` -> reopen assertions
   plus the envelope (strengthened again in round 2).
10. Tests named more than they proved -> real-import empty-columns case, direct flag assertions.
11. Minor: startup warning was lazy, `new Taxonomy` vs `getTaxonomy`, extensions unrecorded ->
    startup call added, deviation and extensions recorded.
12. Minor: redundant state (`boxColumnsPresent`, `RESERVED_MEDIA_SUFFIXES`, `boxedAnnotations`)
    removed; `bv.getIndexInt()` and `findIndexedFieldNames` reused.

## Plan B round 2 (2026-11-07, plan rev 2 -> rev 3)

Verdict: not converged; 9 findings (8 Major, 1 Minor), no Critical; decisions (a) keep `initiateIA`
unchanged and (b) `new Taxonomy(sciName)` both accepted. All applied.
1. A whole-photo bounding-box feature is NOT trivial (`isTrivial` returns false when the legacy
   width/height are 0, and feature-based annotations never set them) -> the invented `ImportTask`
   tally change deleted; the test asserts `!isTrivial()`.
2. Mirroring `convertIAClassForTaxonomy` dropped explicit classes when `_default._save_as` is set
   (spec: selectable keys PLUS redirect targets) -> canonical = `getValidIAClassesIgnoreRedirects`
   plus resolved class-level and default `_save_as` targets; counterexample fixture D.
3. Labeled keywords are part of the spec's exact-slot metadata -> attached by exact slot through
   `getOrCreateLabeledKeyword(label, value, false)` in exact-slot mode only; positional imports
   keep dropping them as today.
4. `fmap.get(...)` returns a `BulkValidator` even for an empty cell, and `indexPrefixValue` throws
   for tolerated unknown fields -> `valueIsNull()` checks; mode detection via
   `findIndexedFieldNames`; tests for empty cells and unknown columns.
5. The positional regression test asserted behavior that does not exist (second occupied slot
   reads list index 1) -> distinct metadata at indices 1 and 2; both modes asserted.
6. The copied API harness (`MediaAsset` mock, user without id) could not run the hand-off ->
   real in-memory assets with ids and metadata, a user id, a held `ImportTask`, `FeatureType` and
   `IAGateway` static mocks; ordinary-envelope assertion; two zero-survivor cases.
7. Persistence proof omitted the `storeNewEncounter` return and built the ImportTask from closed
   objects -> delegating spy records the returns; ids snapshotted, encounters reloaded before the
   ImportTask is built; relationship assertions on both sides.
8. Corrupt-file behavior was never connected to creation failure, and no EXIF fixture ->
   `AssetStore.isValidImage` qualifies the truncated fixtures; an APP1-injected EXIF orientation
   fixture asserts the raw grid on both readers; a tolerant real import with a corrupt boxed file.
9. Minor: staging work was "recorded", not assigned -> Task 7 carries the checklist, PENDING
   until run with recorded results.

## Plan B round 3 (2026-11-07, plan rev 3 -> rev 4)

Verdict: not converged; 6 findings (4 Major, 2 Minor), no Critical; round-2 findings 1, 2, 4-7, 9
resolved, 3 and 8 partially. All applied.
1. The existing file loop runs only when `dataErrors` is empty, so the boxed-file pass's new
   pre-loop errors would make a tolerant import create zero media -> the loop also runs when
   `!toleranceFailImportOnError`, skipping files the pass already rejected; recorded as a change
   limited to tolerant (API-only) imports, with the `skipRowOnError` no-op noted; a tolerant
   bad-MIME-plus-valid test.
2. Labeled keywords cached in `keywordCache` under `label=value` collide with an ordinary
   keyword of that text -> a separate cache keyed by the (label, value) pair; order-swapped test.
3. Fixture A's `dorsal` link does not end in `.dorsal`, so it is not selectable, and its options
   are null -> expectations corrected; a `flank` link whose `_save_as` target exists only through
   the link proves linked targets are collected.
4. The corrupt-file API test injected the exception it claimed to prove -> the real
   `UploadedFiles.makeMediaAsset` and `AssetStore.isValidImage` run for the corrupt fixtures, with
   `AssetStore.getDefault` stubbed.
5. Minor: metadata-extractor 2.6.2 has `getDirectory`, not `getFirstDirectoryOfType`; seven valid
   fixtures, not six.
6. Minor: a bare photo cell arrives as `""` (it falls through `validateValue`), so the row rule
   treats null or blank as absent; empty and whitespace-only tests.

## Plan B round 4 (2026-11-07, plan rev 4 -> rev 5)

Verdict: not converged; round-3 findings all resolved; one new Major, applied.
1. Under tolerance the file loop now creates the photo of a row whose `.iaClass` was rejected or
   missing, `createImport` drops exception fields, and the importer selected box construction on
   `.bbox` alone, so a classless box could reach the constructor -> construction guard: `.bbox`
   AND `.iaClass` present with values AND the row's taxonomy string non-null, else a trivial
   annotation with the reported errors standing; never a defaulted class. Unit test with the three
   dropped-field shapes; tolerant endpoint test with missing class, rejected class and rejected
   taxonomy beside one valid boxed row, envelope holding only the valid box.

## Plan B round 5 (2026-11-07, plan rev 5 -> rev 6)

Verdict: not converged; round-4 finding partially resolved (class half resolved); one Major on the
taxonomy half, applied.
1. `tx != null` does not prove the row's taxonomy validated: `Util.taxonomyString` yields a
   genus-only string when the epithet was rejected and dropped, and a reused encounter keeps an
   earlier row's taxonomy -> the guard requires the `Encounter.genus` and
   `Encounter.specificEpithet` validators in `fmap` with values, and the box's taxonomy is built
   from those row values; tests for genus-only, epithet-only and a reused encounter with an earlier
   valid taxonomy, in the importer unit test and the tolerant endpoint test.

## Plan B round 6 (2026-11-07, plan rev 6 -> rev 7): no Major findings

Verdict: round-5 finding resolved; no Critical or Major findings; one Minor, applied. The seven-item
audit passes (spec fidelity, layer placement, importer/PR A hand-off, class derivation, tests,
no simpler design, reuse). Implementation proceeds from rev 7; the branch review follows.
1. Minor: the reused-encounter taxonomy case existed only in the importer unit test -> a tolerant
   endpoint test with a shared `Encounter.id` (row A valid and un-boxed, row B rejected taxonomy
   and boxed, row C valid and boxed) asserting the retained taxonomy, the trivial fallback and an
   envelope holding only C's box; missing taxonomy cells sent as `JSONObject.NULL`; counts fixed.

## Branch B round 1 (2026-11-07, code review of feat/bbox-import-b-fields)

Verdict: not converged; 4 Major, 8 Minor, no Critical; all applied.
1. The boxed-import predicate was read inside the file pass, after skipping rows whose photo had
   already failed, so a tolerant import whose only boxed file was missing came out un-stamped and
   could detect -> the predicate is read first over every validated .bbox value.
2. Row rule (1) fabricated an exception under a .bbox key the sheet did not have, which switched
   the importer to exact-slot mode -> pairing errors go on the metadata column that exists; rule (3)
   goes on .iaClass when that column exists, else on .bbox; no fabricated .bbox key.
3. Filenames were trimmed for the file lookup while uploads keep exact names -> matched exactly;
   only a blank cell counts as empty.
4. `_save_as` leaf links were read with optString -> resolved through a new quiet, link-following
   `JsonProperties.opt`.
5. Minor: an unreadable header left the file cached as valid -> marked invalid for the file loop.
6. Minor: a file failure skipped the cross-row check -> cross-row runs first, independently.
7. Minor: -0.0 theta defeated duplicate detection -> normalized.
8. Minor: plain, boxed, plain on one photo missed the third slot -> "any earlier boxed" tracked.
9. Minor: identOpts lookups printed stack traces for ordinary configs on every row with a photo
   -> quiet `opt` lookups in the class lists; the allowed list resolved lazily per boxed slot.
10. Minor: the default submitter overwrote an explicitly rejected one -> only a missing column
    gets the default.
11. Minor: `annotationsImported` was absent from early 400s -> initialized on every response.
12. Minor: tests -> creation-call assertions, exact-slot proof on the unknown-column test,
    per-encounter ids / exact feature ids / geometry / envelope length in the DB test, settings
    content assertions, plus regressions for 1-11.
Simplifications: `Util.asSortedList` in SiteSettings; `getValueStringTrimmedNonEmpty` delegates.

## Branch B round 2 (2026-11-07)

Verdict: not converged; no Critical or Major; 3 Minor (round-1 findings 9, 11, 12 partially
resolved); all applied.
1. Unknown species still logged a stack trace through `hasIA` -> the species node is read with the
   quiet `opt`; both list methods asserted silent (stdout captured) on every fixture species and an
   unknown one.
2. The POST's 401 branch wrote a literal body without `annotationsImported` -> it serializes the
   response object; 401 test.
3. Tests: the rejected-submitter case ran in strict mode where the old code also returned 400 ->
   a tolerant case captures the rows handed to the importer (rejected kept, empty cell defaulted);
   the settings test checks the complete sorted indexable list and the species key set; the
   quiet-lookup test captures stdout; one validation and one dimension read per boxed file,
   reused by the file loop. `hasSubmitter` dropped as redundant beside `hasSubmitterColumn`.

## Branch B round 3 (2026-11-07)

Verdict: not converged on tests only; no Critical or Major; no new production defect; 2 Minor
test gaps, applied. Round-2 findings 1 and 2 resolved, 3 partially (species map).
1. The settings species-map assertion compared two empty key sets -> the test stubs one species
   through `getAllTaxonomyCommonNames` and an IA fixture (body matchable, fin not) and asserts the
   exact `bulkImportMatchableClasses` and `iaClassesForTaxonomy` maps.
2. The 401 test never asserted the status -> `verify(mockResponse).setStatus(401)`.
Neutrality table unchanged; one more intended difference named: a reused encounter's existing
imported boxes enter the indexing list (Task 6 collects every imported box on persisted
encounters).

## Branch B round 4 (2026-11-07): converged

Verdict: converged; round-3 findings resolved; no Critical, Major or Minor findings. Plan fidelity,
correctness, neutrality for imports without box columns (every difference intended: nonfinite
quality rejected, `annotationsImported:false` on responses and task parameters, the two tolerant-mode
changes, reserved labels and the two settings, reused encounters' imported boxes indexed), tests,
no simpler design, no reusable code. Pre-existing `SettingApiTest.apiPostSuccess` early return noted
as supplying no evidence (not touched by this PR). Full suite 226 classes / 1972 tests / 0 failures.

## Plan E round 1 (2026-11-07, plan rev 1 -> rev 2)

Verdict: not converged; 11 findings (8 Major, 3 Minor), no Critical; all applied. The two declared
deviations (inspect keeps its own reader; the status keeps its two phase objects with two new
codes) accepted.
1. Bare-slot scans ran over the unfiltered keys, so an unsupported huge index reached the slot
   helper (which grows a list to the index) -> scan `copied.keySet()` after unsupported removal;
   the spreadsheet route's unbounded growth noted as a pre-existing follow-up.
2. The cross-row pass checked identical boxes only when every use was boxed and could double-report
   in-row repeats -> eligible uses collected after validation (in-row repeats excluded), mixed-use
   and duplicate-box reported independently, a rejected `.bbox` counts as boxed with an unknown key.
3. Blanket dedupe-by-field could suppress independent date/value errors -> one explicit
   suppression only (the shared rules' INVALID_DUPLICATE on an already-reported in-row repeat).
4. Empty optional box values hit "Provided value cannot be empty" and an empty `.bbox` column
   would be dropped from normalized rows -> null values of the new fields keep the key as `""`
   (exact-slot mode survives, draft stays un-boxed).
5. Reason mapping missed two bbox families and one theta family -> all mapped by prefix.
6. `/results` builds the phase objects itself and `aiPhase()` had to change -> `aiPhase` keyed on
   `requestsAi`; `detectionPhase`/`identificationPhase` used by `json()` and `results()`; the
   boxed import-only identification view keeps the hand-off state with `BOXES_EXTRACTED_ONLY`.
7. Harness facts: `readyJob` has no `normalizedRows` (optJSONArray), dispatch tests live in the
   store DB test, the issue test's comparison validator needs the same skip flag, the boxed
   envelope test must stub `ImportTask.getId()`.
8. Missing tests -> capabilities JSON, boxed `pending()` after reload, boxed ambiguous publication,
   both modes through enqueue -> execute -> prepare with the PR A accessors, box identity across
   spellings, boxed legacy-contract comparison, the staging matrix enumerated.
9. Minor: persistence upgrade proof -> SQL NULL round trip; wording.
10. Minor: the no-box inventory completed (quality accepted, issue order, stamp, id order, keys).
11. Minor: ownership cleanup (prepare tests in Task 4, OpenAPI in Task 5, skip flag in Task 1).

## Plan E round 2 (2026-11-07, plan rev 2 -> rev 3)

Verdict: not converged; 7 findings (4 Major, 3 Minor), no Critical; round-1 findings 1, 2, 3, 5, 7
resolved, 4, 6, 8, 9, 10, 11 partially. All applied.
1. Blank quality cannot be accepted by submissions-only handling (the shared validator parses it
   first) -> quality keeps the shared validation: omit unset, `""` is UNPARSEABLE; blank handling
   limited to the five box suffixes.
2. Three task checkpoints depended on later tasks (UNKNOWN_CLASS reason, hasBoxes, prepare
   envelope) -> assertions moved to the owning tasks.
3. `skippedIdentification()` is true before any IA task exists (PR A's accessor) -> asserted only
   after real prepare, in Task 4, before and after reload.
4. The pairing message starts with the field name -> matched as `field + " needs a box in "`; a
   theta-without-box case added.
5. Minor: SQL NULL proves null reads, not a live upgrade -> an old-shaped-table test (drop the
   column, reopen with autoCreateAll, retained row reads false).
6. Minor: in-row repeats drop from two issues to one and slot order decides the flagged field ->
   recorded; an all-un-boxed in-row regression case.
7. Minor: boxed import-only text contradicted its state and could replace failure messages ->
   an extraction-only message; failure phases returned untouched; both representations asserted
   for state, code and message.

## Plan E round 3 (2026-11-07, plan rev 3 -> rev 4)

Verdict: not converged; 1 Major (test setup) and 3 Minor, no Critical; round-2 findings 2-6
resolved, 1 and 7 partially (stale wording). All applied.
1. Minor: a stale "or quality" clause in Task 2's null-to-empty rule -> the five suffixes only.
2. Minor: a state test required "not identified" while the contract says "identification is not
   run" -> aligned.
3. Major: the real-prepare test never reached prepare because realImport leaves derivatives
   pending -> `mutate(derivatives("complete"))` and the callback-URL mock before dispatch; the
   publisher asserted called exactly once.
4. Minor: the real-import test could not detect a missing skip flag in execute's second pass ->
   a `fin` (default false) box imported in import-only with its persisted class asserted, and the
   same row rejected in detect-and-identify.

## Plan E round 4 (2026-11-07): converged

Verdict: converged; round-3 findings resolved; no Critical, Major or Minor findings across spec
fidelity, correctness, no-box neutrality (all differences recorded), persistence (upgrade test),
tests, no simpler design, reuse. Implementation proceeds from rev 4; the branch review follows.

## Branch E round 1 (2026-11-08)

Verdict: not converged; no Critical or Major; 7 Minor, all applied (delimiter-safe class
translation, narrowed suppression, BOXES_EXTRACTED_ONLY on normal states only, the missing-class
message recorded as an accepted deviation, missing tests and edge cases, boxed phase docs,
THETA_LIMIT / isBoxField / hasBoxes reuse). Process note: targeted Maven runs overlapped the
background full build in the same worktree; that build was discarded and rerun.

## Branch E round 2 (2026-11-08)

Verdict: not converged on tests only; no Critical, Major or production defect; 2 Minor test gaps,
applied (the full mode-by-phase matrix through both status endpoints with exact text; the
repeated-slot test reuses the excluded slot's box). Clean full build: 226 classes, 2018 tests,
0 failures, 7 skips.

## Branch E round 3 (2026-11-08): converged

Verdict: converged; round-2 gaps resolved; no Critical, Major or Minor findings. Two recorded
deviations stand (inspect keeps its reader; the missing-class message names .iaClass).

## Staging fix review (2026-11-08, PR A commit a0108162a)

Flakebook staging found import-only boxes dropped silently when a species lacks species-level
ml-service routing. Fix: a terminal holder (NO_EXTRACTION_CONFIG error for vector-configured
classes, else completed). Codex: converged; keep the shared species-level routing rule; one Minor
(identification-enabled regression test), applied. Follow-up recorded: MlServiceClient sends only
extract_model_id while older matching reads model_id; installs with model_id-only entries fail v2
extraction with 422 (pre-existing; separate fix).
