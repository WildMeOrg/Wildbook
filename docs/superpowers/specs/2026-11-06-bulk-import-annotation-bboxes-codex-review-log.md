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
