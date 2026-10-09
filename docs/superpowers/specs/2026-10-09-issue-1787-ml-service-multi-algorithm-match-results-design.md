# Issue #1787 — Run every default algorithm on ml-service bulk imports, and link Match Results to the encounter-wide task

Date: 2026-10-09 (rev 4, after Codex rounds 1–3)
Issue: https://github.com/WildMeOrg/Wildbook/issues/1787
Forum: https://community.wildme.org/t/match-results-not-showing-all-results/5770
Branch: `fix/bulk-import-multi-algorithm-1787` (off `origin/main` 764268c5aa)

## 1. Problem

Mantamatcher guitarfish species have two `default: true` identification
options in IA.json: a MiewID vector option (`pipeline_root: "vector"`,
`method: miewid`, `version: msv4_v3`) and a HotSpotter option
(`query_config_dict: {sv_on: true}`). Users expect every guitarfish bulk
import to run both and the bulk-import page's Match Results link to show
both. Since the 10.14 upgrade and the move to the vector config they see
MiewID only. Three layers are involved.

### Layer A — the ml-service match step runs only the vector option

`MlServiceProcessor.runMatchProspects` builds one match task and calls
`Embedding.findMatchProspects(matchConfig, matchTask, shep)`. `matchConfig`
comes from `MlServiceProcessor.defaultMatchConfig`, which selects the single
`_id_conf` entry that is both `default: true` and `pipeline_root: "vector"`.
Every other default option is ignored. This step is reached from ml-service
detection (`processDetection`), ml-service extraction (`processExtraction`)
and the deferred re-fire path (`runDeferredMatch`), all through
`waitAndRunMatchInternal`. WBIA detection is no longer supported, so this is
the first-pass identification path for every new bulk import on a
vector-routed species.

### Layer B — the shared selector links to the newest result-owning leaf

Resending an import (`appadmin/resendBulkImportID.jsp` →
`ParallelIdentify.processOne` → `IA.intakeAnnotations`) does run both
options. The tree for one encounter with a body and a rostrum annotation:

```
subParent (no annotations; params: importTaskId, matchingSetFilter)
└── U  umbrella (body, rostrum)          ← inherits subParent params
    ├── T_vec_body (body)   ── SA_body   ← vector leaf, owns MatchResult
    ├── T_hs_body  (body)                ← HotSpotter task, MatchResult from WBIA logs
    ├── T_vec_rostrum (rostrum) ── SA_rostrum
    └── T_hs_rostrum (rostrum)
```

`Task.selectPreferredMatchTask` returns the newest renderable task holding
the annotation: `SA_body`, a MiewID-only page. `T_hs_body` is also
renderable (issue #1744 single-annotation-branch rule) but older. The unit
test `nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf` on main pins
this as a documented trade-off.

### Layer C — the import's identification status counts one task per annotation

`ImportTask.statsAnnotations` derives `numLatestTask_<status>` from each
annotation's single newest direct task. In the tree above that is the vector
leaf, so the import reports identification complete while HotSpotter is
still queued. Today the link hides it; once both algorithms are linked,
"complete" must mean both.

### Evidence (Mantamatcher import 26e2c50c, 2026-10-09 SQL)

| created | tasks | meaning |
|---|---|---|
| 2026-10-08 | 105 HotSpotter completed, 210 "MiewID msv4_v3 (vector)" completed, 55 umbrellas | resend ran BOTH options for all 105 annotations (55 encounters: 50 with body+rostrum, 5 single) |
| any | 0 tasks with `mlServiceV2Match` | this import predates the vector config; no first-pass ml-service match ran |

On this import the live symptom is Layer B alone. New imports hit Layer A
first, then B, then C.

## 2. Goals

1. After ml-service detection or extraction, run every `default: true`
   identification option for each annotation, as New Match and resend do.
2. The Match Results link on the bulk-import page and the encounter page
   opens a page showing every algorithm's results of one identification
   run for that annotation's encounter, and only that run.
3. Existing trees (e.g. the 2026-10-08 resend) show both algorithms as soon
   as the fix deploys, with no resend and no migration.
4. Preserve the issue #1744 fix: an image whose annotations belong to
   different encounters must not link to a page spanning those encounters.
5. Preserve per-import stamp isolation (issue #1624 / PR #1625).
6. The import's identification status reads complete only when every
   algorithm dispatched for every annotation has reached a terminal state.
   A failed dispatch is terminal and visible; a queued retry is pending.
7. Annotations created by ml-service are registered with WBIA under their
   `acmId` (forced id) by every path that registers them, never through the
   on-demand path that lets WBIA mint a new uuid and rewrites `acmId`.

## 3. Non-goals (decided)

- Changing which options are default (IA.json content).
- Frontend changes. `matchResultsStore._processData` renders one section per
  task node; PR #1746 made Confirm act on the selected section's encounter;
  `BulkImportTask.jsx` renders `[taskId, status, label]` tuples verbatim.
- `loadExtractionContext`'s early `embedding-exists` return (extraction
  idempotency; documented, unchanged).
- Config drift between a deferred payload's `matchConfig` (gate input) and
  the IA.json read at re-fire time (intake input). Documented.
- The final-commit behaviour of the existing intake callers
  (`ParallelIdentify`, `IBEISIA.processCallback`, `IA.handleRest`,
  `ProjectIA`). They keep their unchecked commits and their attach-after
  parent linking; only the new ml-service caller gets the checked,
  link-before-dispatch contract. Pre-existing; a separate hardening.
- New Match requests that list annotations from several encounters in one
  `/ia` call. The run's umbrella spans encounters; the selector then falls
  back to today's per-leaf behaviour for that run (§4.3), as it does now.
- Pre-fix trees of images holding annotations from several encounters keep
  linking to one algorithm's leaf. New trees get one umbrella per
  encounter (§4.1).
- Encounter-filtered rendering of a shared umbrella.
- Locale keys for `SEND_FAILED` / `PARTIAL` / `INVALID_MATCH_CONFIG`.
- The WBIA-detection pipeline (unsupported; untouched).

## 4. Design

### 4.0 One identifier for one identification run

Every task created by one call of the intake core carries
`identificationInvocationId` = the id of that call's umbrella. The core
strips `identificationInvocationId`, `mlServiceV2Match` and
`ibeis.identification` from the inherited parameters, puts the umbrella's
own id in, and only then creates the option children, so option tasks,
vector leaves (`new Task(task)` in `Embedding.findMatchProspects`) and
HotSpotter fan-out children (`IAGateway._doIdentify` copies the option's
parameters) carry it. A task is the umbrella of a run iff its own id equals
its `identificationInvocationId`; a reused parent can therefore never make
an option task look like an umbrella. `IAGateway._doIdentify` keeps
`importTaskId` and `identificationInvocationId` when it clears the option
parent's parameters before fanning out; it still drops
`ibeis.identification`, so that parent's renderability is unchanged.

Tasks outside a run never carry its id: the only copies are made by the
three builders above, all beneath the umbrella. `IA.intakeMediaAssets` copies
a root's parameters into detection tasks, and `MlServiceProcessor` inherits
from the detection task; both boundaries scrub the three keys
(`matchIntakeParams`, §4.1), so a detection task that descends from a
reused umbrella does not carry a run id, and even if it did it holds media
assets, never annotations, so it is never a candidate.

**Run selection** (shared by the results link and the import status):
`Task.selectRun(List<Task> direct, String importTaskId, Set<String> encounterAnnotationIds)`
returns the newest **eligible umbrella** among the annotation's direct
tasks (`getTasksFor(ann)`, created-DESC), where eligible means: the task
is an umbrella (id equals its run id); its stamp satisfies the existing
import rule (scoped: this import's stamp, else unstamped, never foreign;
unscoped: any); and, when the encounter set is given, every annotation on
it is in the set. Umbrella creation time is what is compared, never leaf
creation time, so a late HotSpotter fan-out under an older run cannot
outrank a newer run (Codex r3 #1). The umbrella holds every annotation of
its run (intake sets `objectAnnotations` on it), so it is always a direct
task of each of its annotations.

### 4.1 Layer A — the ml-service match step hands each encounter to the shared intake

`runMatchProspects(annotationIds, taskId, matchConfig)` keeps everything
before it: the `skipIdent` short-circuit and the `MatchVisibilityGate` in
`waitAndRunMatchInternal`, and the deferred re-fire path still lands here.
`matchConfig` keeps its remaining roles (gate input,
`hasEmbeddingForMatchConfig`); it no longer selects the algorithm.

1. Load the parent task `D` (detection task, or the extraction task `E`)
   and the annotations as today; stale handling unchanged.
2. Group the annotations by encounter (`Annotation.findEncounter`, as
   `IBEISIA.processCallback` does); annotations with no encounter form
   their own group. `MlServiceProcessor.groupByEncounter` is pure over a
   precomputed annotation→encounter map.
3. Build the inherited parameters once:
   `MlServiceProcessor.matchIntakeParams(JSONObject parentParams)`, pure,
   returns a copy of `D`'s parameters without `ibeis.detection`,
   `identificationInvocationId`, `mlServiceV2Match` and
   `ibeis.identification`. `importTaskId`, `matchingSetFilter`, `skipIdent`
   and anything else on `D` are inherited by every intake task.
   `matchingAlgorithms`, if ever present, is honoured as for every other
   caller; it is never set on `D`.
4. For each group, in sequence:
   a. `IA.IntakeOutcome o = IA.intakeAnnotationsUnder(shep, anns, D, inheritedParams, false)` (§4.2).
   b. If `o.persistFailed`: nothing of this group was dispatched. Record
      the failure and stop processing further groups.
   c. If `o.noWork`: mark `o.umbrella` error `INVALID_MATCH_CONFIG` "no
      default identification options".
   d. `shep.commitDBTransactionWithStatus()` — one checked commit **per
      group**, so a later group's failure can never roll back an earlier
      group's vector leaves. On `false`: record the failure and stop.
      On `true`: `beginDBTransaction()` and continue.
   The recovery record holds, per processed group: the umbrella id, the
   vector option task ids, the queued option task ids, and whether the
   group's commit succeeded.
5. Recovery (after a recorded failure, or in the `catch` of any
   exception): roll back and close `shep` first. With a fresh Shepherd:
   for every group whose commit did not succeed but whose umbrella was
   persisted by §4.2.3, mark the umbrella and its vector option tasks error
   `MATCH_PERSIST` (they are childless after the rollback, so §4.4 sees
   them as failed leaves); leave queued HotSpotter option tasks alone
   (their jobs are durable and complete them); mark `D` error
   `MATCH_PERSIST` (or `MATCH` for an exception, with the message). Return
   `persistError`. Groups whose commit succeeded are untouched.
6. If every group reported `noWork`, mark `D` error `INVALID_MATCH_CONFIG`
   and return the same `validationError` as today.

Resulting tree for one image with two encounters, two options each:

```
D (media asset only; ibeis.detection; importTaskId)
├── U1 (annA)  run=U1                    ← enc 1
│   ├── T_vec (annA) run=U1 ── SA (annA) run=U1, mlServiceV2Match
│   └── T_hs  (annA) run=U1
└── U2 (annB)  run=U2                    ← enc 2
    ├── T_vec (annB) ── SB (annB)
    └── T_hs  (annB)
```

For a species with one option only, intake uses `U` itself as the option
task (`D → U → SA` for vector; `D → U` for HotSpotter, with `_doIdentify`
fanning out under `U` only when it holds more than one annotation).

Who marks `D` completed is unchanged: detection/extraction already mark it
before matching; the ml-service consumer only logs the outcome; the
HotSpotter leg completes asynchronously through the WBIA callback.

Method header: intake stores the IA.json option itself as
`ibeis.identification`, so `Task.getIdentificationMethodInfo` returns the
option's `description`. `isV2MatchTask` (root-only) is unaffected.

### 4.2 Intake changes (`IA.java`)

**4.2.1 Entry point.** Add
`public static IA.IntakeOutcome intakeAnnotationsUnder(Shepherd, List<Annotation>, Task parent, JSONObject inheritedParams, boolean fastlane)`.
The existing `intakeAnnotations(Shepherd, List, Task parentTask, boolean)`
delegates to the same private core with `parent = null` (no linking, as
today) and `inheritedParams = parentTask == null ? null : parentTask.getParameters()`,
and returns `outcome.umbrella` (null when `persistFailed`). The
two-argument overload is untouched, so the literal-null call
`intakeAnnotations(myShepherd, anns, null, false)` stays unambiguous.
Each iaClass iteration works on a fresh copy of `inheritedParams`
(`new JSONObject(inheritedParams.toString())`, or an empty object when
null), matching today's per-iteration `parentTask.getParameters()`
re-parse. Compatibility for existing callers: same tree shape, same
parameters plus the run id on every task and `mlServiceV2Match` on vector
option tasks and their leaves, and a null umbrella instead of a blind
continue when the run cannot be persisted.

**4.2.2 Markers.** Run id as in §4.0. Each option task whose option is a
vector config (`Embedding.isVectorConfig(opt)`, extracted from the first
lines of `findMatchProspects`) gets `mlServiceV2Match: true` before
`findMatchProspects` creates its leaves. HotSpotter siblings never carry
it. Side effect, intentional: vector leaves from New Match and resend are
now counted by the `/metrics` vector gauge; `MetricsBot`'s HELP text and
comment change to "vector identification leaf tasks (all entry points;
counts errors too)".

**4.2.3 Build, persist, then dispatch.** The core is restructured into
phases over the same tree it builds today:

1. Build: umbrella, per-class option tasks (or the umbrella itself for the
   single-option single-class case), parameters and markers. When `parent`
   is non-null, `topTask.setParent(parent)` (also adds the child; `parent`
   is attached and persistent, so the relationship persists on commit).
2. Persist: `pm.makePersistent(topTask)` (children by reachability), then
   `commitDBTransactionWithStatus()`. On `false`: return an outcome with
   `persistFailed = true` and nothing dispatched. On `true`:
   `beginDBTransaction()` and continue. This replaces `storeNewTask`, whose
   internal commit swallows failures.
3. Dispatch through a `Dispatcher` seam (default: real): vector options run
   inline (`findMatchProspects`); HotSpotter options are published to the
   queue. Publication failure (`addToQueue` returns false or throws) marks
   that option task error `QUEUE_FAILED` (terminal); today intake only logs
   it.

**4.2.4 Outcome.** `IA.IntakeOutcome { Task umbrella; List<Task> vectorOptionTasks;
List<Task> queuedOptionTasks; List<String> skippedIaClasses; boolean noWork;
boolean persistFailed; }`. `noWork` = `IA.intakeProducedNoWork(umbrella)`:
no children and no `ibeis.identification`.

**4.2.5 Stable WBIA registration at identify time.** `IBEISIA.sendAnnotationsAsNeeded`
(called by `beginIdentifyAnnotations` for query **and** target annotations,
inside the queue consumer's Shepherd, which performs no annotation writes
before that call) gains a forced branch that runs **before** the existing
non-forced flow and its empty-list guards. For each annotation with
`Boolean.FALSE.equals(getWbiaRegistered())`:

- validity: if `!IBEISIA.validForIdentification(ann)` the annotation is
  skipped here; if it is a target it is removed from the target list for
  this job; if it is a query the job fails (below).
- image: if the media asset has no `acmId`, set `ma.setAcmId(ma.getUUID())`
  (the `AcmIdBot` convention). Register the image through a new public
  `WildbookIAM.registerImageForced(MediaAsset ma)`, which builds the
  request the way `registerImageIfMissing` does (forces
  `image_uuid = MediaAsset.acmId`, validates the acknowledgement, treats
  "already present" as success). `sendMediaAssetsForceId` is **not** used:
  it forces `MediaAsset.getUUID()` while checking presence by `acmId`
  (Codex r3 #6).
- annotation: `sendAnnotationForcedByAcmId(ann, shep)`; it forces the
  identifier identification sends (`acmId`) and returns true only when
  WBIA echoes it. An annotation already present under its `acmId` counts
  as success.
- on success `ann.setWbiaRegistered(TRUE)` on the attached object; the
  consumer's transaction commits it.
- on any failure for a **query** annotation the branch throws
  `IOException`. `beginIdentifyAnnotations` does not inspect the
  `sendAnnotMAException` field (Codex r3 #7), so the throw is what makes
  its outer `catch` return `success=false`, which `_sendIdentificationTask`
  turns into `SEND_FAILED` and a bounded requeue (§4.4). Failed **targets**
  are dropped from this job's target list instead.

`iaCheckMissing` (WBIA reports `missing_annot_uuid_list` during identify)
uses the same forced branch for annotations flagged `FALSE` and the legacy
non-forced `__sendAnnotations` for the rest, so the missing-annotation
repair cannot bypass the stable-id contract either.

Annotations with `null` (legacy, backfilled) or `TRUE` take today's path
unchanged. The background poller (`StartupWildbook` WBIA registration poll)
is aligned to force the same identifier: `buildForcedRequestMap(dto)` passes
`dto.annotationAcmId` when present (else `dto.annotationId`); for a pristine
ml-service annotation the two are equal, and for a `FALSE` annotation whose
`acmId` was already rewritten by the pre-fix non-forced path the poller now
finds it present under that `acmId` and marks it `TRUE` instead of
registering a second uuid (Codex r3 #8). With both paths forcing the same
uuid, concurrent registration is idempotent at WBIA (a repeated forced add
of a present uuid is acknowledged as present) and the flag write is
idempotent (Annotation has no JDO optimistic version strategy). Staging
check §5.3-5 exercises the concurrent case.

### 4.3 Layer B — link to the run's umbrella

`Task.selectPreferredMatchTask` gains a third argument:

```java
static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId,
                                     Set<String> encounterAnnotationIds)
```

The two-argument method delegates with `null` = "today's behaviour".
Both known callers opt in (§4.5). With a non-null set:

```
run = selectRun(tasks, importTaskId, encounterAnnotationIds)     # §4.0
if run != null:                                   return run      # new tree, scoped to the encounter
if any task in tasks carries a run id:                            # new tree whose newest umbrella spans encounters (§3)
    return today's selection restricted to the tasks of that newest umbrella's run
                                                                  # newest renderable leaf of that run; no hoist
old tree (no run ids anywhere): winner = today's selection; then the structural climb:
    best = winner; winnerStamp = stamp(winner)
    up = winner.parent; hops = 0; visited = {winner.id}
    while up != null and up.id != null and hops++ < 20 and visited.add(up.id):
        if up.numberAnnotations() == 0:                                   stop  # roots, sub-parents
        if up has objectMediaAssets or params has "ibeis.detection"
           or params has "embeddingExtraction":                           stop  # detection / extraction orchestration
        if up.parent == null and up.parameters == null:                   stop  # parameterless root: createFromApi's E
        if any annotation on up ∉ encounterAnnotationIds:                 stop  # #1744 boundary
        if stamp(up) != null and !stamp(up).equals(winnerStamp):          stop  # #1624 boundary
        if stamp(up) == null and winnerStamp != null and
           not every direct child of up carries winnerStamp:              stop  # unstamped bridge only when its children are ours
        best = up; up = up.parent
    return best
```

The umbrella renders every option subtree under it (`matchResultsJson`
recurses children), which is the intended page. Because `selectRun` only
ever returns an umbrella whose annotations are all in the encounter, the
"renderable while pending" property holds without a separate renderability
clause, and the root fallback is never reached for a new tree with an
eligible umbrella (Codex r3 #2).

Wrapped in try/catch that falls back to the best task found so far. The
walk uses attached objects inside the caller's open transaction;
`recursion-depth="3"` on `Task.parent` bounds detached fetch plans, not
this walk; §5.2 reloads a five-level tree.

Old-tree invariants the climb relies on: (I1) every builder copies a
child's parameters from its parent's, with one exception — the
`_doIdentify` parent cleared to null while its children keep the stamped
copy — which the bridge rule handles explicitly; so "every direct child
carries the winner's stamp" implies the subtree does. (I2) a descendant's
annotation set is a subset of its ancestor's (intake bins the umbrella's
annotations by class; `findMatchProspects` and `_doIdentify` fan out one
annotation per child).

Old-tree shapes:

| old tree | landing |
|---|---|
| resend / legacy callback: `subParent(no annots) → U → options` | `U` |
| New Match: `root(media assets) → U → …` | `U` |
| ml-service single option: `D(media assets, ibeis.detection) → M → SA` | `M` (renders `SA`) |
| API extraction: `E(annotation, no params, no parent) → M_old, M_new` | `M_new` (parameterless root) |
| legacy callback extraction: `D → E(annots, embeddingExtraction) → M_old, M_new` | `M_new` (`embeddingExtraction` stop; Codex r3 #3) |
| `_doIdentify` fan-out: `U(stamped) → T_hs(params null) → H_A, H_B(stamped)` | `U` (bridge) |
| image with two encounters: `D → M(annA, annB) → SA, SB` | `SA` (#1744) |
| ancestor stamped with another import | last permitted node |

### 4.4 Layer C — identification status across every algorithm of the run

In `ImportTask.statsAnnotations`, per non-trivial annotation, in this
order:

1. `run = selectRun(atm.get(ann), importTaskId, encounterSet)`; if the
   annotation has tasks with run ids but no eligible umbrella, use the
   newest umbrella holding it regardless of the encounter set (status is
   per annotation, not per page).
2. If `run == null` (old tree): today's rule, unchanged — the newest direct
   task's `getStatus`, counted once. No new rule applies.
3. Otherwise leaves = the annotation's direct tasks that carry the run id
   and have no children, **including the umbrella itself when it is
   childless** (single-option runs use the umbrella as the option task;
   a vector umbrella before its leaves exist; a vector option after a
   rollback) (Codex r3 #4). Orchestration parents are never counted.
4. Per-annotation status: any leaf non-terminal → that status (`sent`
   side); else any leaf `error` → `error`; else `completed`. No leaves and
   the umbrella has children (a class with no option) → `completed` plus
   an informational `numLatestTask_skipped` counter.
5. Maximum age: a non-terminal leaf whose `timeInactive()` exceeds
   `Task.TIMEOUT_INACTIVE_MILLIS` (seven days since `Task.modified`, which
   moves on `setStatus`/`setModified`, not on WBIA log activity) is
   reported as `error` even when its stored status is null, which
   `Task.getStatus` exempts from its own watchdog. A maximum-age policy,
   not liveness detection; a job that completes later flips the task to
   completed and the next poll shows it. Reported only.

**Queued retries are pending.** In `IAGateway._doIdentify`'s catch around
`_sendIdentificationTask`: when `requeueJob(...)` returns true the subtask's
status is reset to null and a `SEND_FAILED; retry queued` log entry is
added, so `Task.getStatus` derives a non-terminal state from the
`initIdentify` log until the retry runs; when `requeueJob` returns false
(retry budget or two-day window exhausted) the `SEND_FAILED` error stays
and is terminal. Goal 6 therefore holds across retries (Codex r3 #9).

`numLatestTasks` stays exactly one per non-trivial annotation;
`iaSummaryJson`'s formula (`completed + error >= total` → complete) is
unchanged; per-task `sa.put(status)` tallies are unchanged.

### 4.5 Call sites

- `Encounter.jsonForApiGet` → `Annotation.getPreferredMatchResultsTask`:
  pass the ids of `this.getAnnotations()`.
- `ImportTask.statsAnnotations`: after `annotationIdsToEncounterIds`, one
  SQL pass over `ENCOUNTER_ANNOTATIONS` keyed by the distinct encounter ids
  (`encounterIdsToAnnotationIds`) gives each encounter's full annotation id
  set, including annotations added after the import. When several
  annotations of one encounter resolve to the same preferred task, the
  Class label is the distinct non-blank iaClasses sorted alphabetically and
  joined with ", ". If the SQL pass fails, selection falls back to the
  two-argument call and status to rule 2.

## 5. Testing

### 5.1 Unit (JUnit 5, no datastore)

`TaskSelectPreferredMatchTaskTest` (hand-built trees):
- `selectRun`: newest umbrella by umbrella time beats a late fan-out child
  of an older run; scoped stamp rule; encounter-set rejection → fallback
  to the run's newest renderable leaf; reused parent with a run id does
  not make an option task an umbrella;
- old-tree climb: the live Mantamatcher shape (scoped; body and rostrum
  both land on `U`); unscoped one-annotation two-option shape; stamp
  matrix (winner stamped/unstamped × ancestor same/other/unstamped-all-
  children-stamped/unstamped-with-a-foreign-child); `_doIdentify` bridge;
  stops at multi-encounter ancestor, zero-annotation ancestor, media-asset
  ancestor, `ibeis.detection` ancestor, `embeddingExtraction` ancestor,
  parameterless root with two runs;
- null set → today's behaviour; cycle guard; hop cap; throwing parent →
  best so far. Existing tests updated where the target changes;
  `nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf` replaced by
  the scoped Mantamatcher-shape test.

Pure helpers: `MlServiceProcessor.matchIntakeParams` (scrubs the four
keys; keeps `importTaskId`, `matchingSetFilter`, `skipIdent`),
`groupByEncounter`; `IA.intakeProducedNoWork`; intake-core parameter
handling (null parent; null parameters; two classes isolated; inherited run
id replaced; markers on umbrella / vector option only; single-option
umbrella keeps its own id); `Embedding.isVectorConfig`; `ImportTask` status
aggregate over leaf statuses + ages (all completed; error + pending →
pending; error only → error; childless umbrella counted; none → skipped;
aged null-status leaf → error; old tree → today's rule); class-label merge.

### 5.2 Datastore (JUnit 5 + Testcontainers PostgreSQL, pattern of `MarkedIndividualTaxonomyBackfillDbTest`)

Fixtures and seams: `FeatureType.initAll(shep)` before any fixture
transaction; `CommonConfiguration.initialize("context0", props)`; an
IA.json fixture installed through a new package-private
`IAJsonProperties.overrideForTests(IAJsonProperties)` consulted by
`iaConfig()` (today it builds a fresh instance from the file on every
call); intake's `Dispatcher` seam stubbed so no OpenSearch, ml-service or
file queue is touched; persisted ImportTask with encounters, annotations
(with media assets and bbox features) and the IA root; `setSkipAutoIndexing`
on every stored entity.

- Persist-before-dispatch: the stub dispatcher asserts, from a second
  PersistenceManager, that the umbrella, its option tasks and the
  `U → parent` link exist at the first dispatch call; a forced commit
  failure yields `persistFailed` with zero dispatch calls.
- Reload: persist a five-level tree, reload in a new PersistenceManager,
  run `selectPreferredMatchTask`, assert the landing node and that
  `matchResultsJson` of it lists exactly the run's leaves.
- `statsAnnotations → iaSummaryJson` on the persisted import: completed
  vector + pending HotSpotter → "sent"; error vector + pending HotSpotter →
  "sent"; both terminal → "complete" with the error count; single-HotSpotter
  umbrella pending → "sent"; skipped class; late fan-out child under an
  older run next to a newer pending run → "sent"; two encounters sharing a
  class report independently; exactly one `numLatestTasks` per annotation.
- Per-group recovery: second group's commit forced to fail → first group's
  leaves durable, second group's umbrella and vector option marked
  `MATCH_PERSIST`, `D` marked, import reaches complete-with-errors.
- Retry pending: `_doIdentify` with a throwing send and a requeue that
  returns true → subtask non-terminal; with a requeue that returns false →
  `SEND_FAILED` terminal.

### 5.3 Controlled integration before any production deploy

On a Wild Me staging Wildbook with WBIA and ml-service reachable (host
chosen by the user), WAR built from this branch:
1. resend one encounter of a two-option species: umbrella under the
   sub-parent, both option tasks terminal, bulk page shows both algorithms;
2. a two-encounter image: two umbrellas under `D`;
3. WBIA stopped: HotSpotter option pending through the retry window, then
   `SEND_FAILED` terminal; vector leaf completes; import ends
   complete-with-errors;
4. queue directory unwritable: `QUEUE_FAILED` terminal;
5. an ml-service annotation with `wbiaRegistered = false`: after the
   HotSpotter send WBIA holds it under its `acmId`, the flag is `TRUE`,
   the poller skips it; run the poller and the send concurrently on one
   annotation and confirm WBIA holds one uuid; repeat with an annotation
   whose `acmId` differs from its id (pre-fix rewritten) and confirm no new
   uuid is created;
6. a FALSE-flagged target that fails validity is dropped from the matching
   set and the job still completes;
7. restart Tomcat and reload the bulk page: links unchanged.

### 5.4 Mantamatcher after deploy

Import 26e2c50c's links show both algorithms per annotation with no
resend; a small new guitarfish import produces one umbrella per encounter
with a vector leaf and a HotSpotter task each; `/metrics` vector gauge
still moves.

## 6. Risks and mitigations

- Forced registration replaces the non-forced round trips the identify
  job already makes for the same annotations; no new dependency.
- A bulk import stays "sent" until WBIA answers or the retry window
  (two days) and then the maximum age (seven days) elapse.
- Linked task ids change for existing single-option v2 trees (`SA` → `M`);
  content identical.
- The vector-match gauge now counts New Match and resend vector leaves.
- `Task.getOverallStatus` walks two levels; for the umbrella its children
  are option tasks and grandchildren vector leaves, both counted.
- Intake's internal commit now aborts on failure for every caller
  (null umbrella) instead of proceeding blind; strictly safer.

## 7. Blast radius

`ia/MlServiceProcessor.java` (runMatchProspects, two helpers),
`ia/IA.java` (entry point, core restructure, dispatcher seam, markers,
outcome, helper), `Embedding.java` (helper), `ia/Task.java` (`selectRun`,
selector), `servlet/IAGateway.java` (keep two params on the cleared parent;
retry-pending reset), `identity/IBEISIA.java` (forced branch in
`sendAnnotationsAsNeeded` and `iaCheckMissing`), `ia/plugin/WildbookIAM.java`
(`registerImageForced`; poller forces `acmId`), `IAJsonProperties.java`
(test override), `Annotation.java` / `Encounter.java` (opt-in),
`servlet/importer/ImportTask.java` (opt-in, status aggregate, label merge,
SQL helper), `MetricsBot.java` (gauge text), tests. No schema change, no
migration, no frontend change.

## 8. Supersedes

Local branch `fix/match-results-multialgo-parent-task` (unpushed,
2026-07-29).

## 9. Codex round-3 dispositions

| r3 | finding | rev 4 |
|---|---|---|
| 1 | newest task, not newest run | §4.0 `selectRun` compares umbrella times; shared by §4.3 and §4.4 |
| 2 | umbrella renderability bypassed the encounter check; reuse | §4.3 `selectRun` applies the check before selection; mixed-encounter runs fall back to per-leaf; §4.0/4.1 scrub at pipeline boundaries |
| 3 | legacy extraction ancestor; I1 wording | §4.3 `embeddingExtraction` stop; I1 restated with the `_doIdentify` exception |
| 4 | single-option umbrella excluded | §4.4 rule 3 counts a childless umbrella |
| 5 | recovery incomplete | §4.1 per-group commit and recovery record; existing callers are a decided non-goal (§3) |
| 6 | forced image helper sends the UUID | §4.2.5 `registerImageForced` via the validated acmId builder |
| 7 | failure path did not exist | §4.2.5 query failure throws; targets dropped; `iaCheckMissing` covered |
| 8 | poller forces a different id | §4.2.5 poller forces `annotationAcmId` |
| 9 | live retries terminal | §4.4 queued retry resets status; exhaustion terminal |
| 10 | fixtures and matrix | §5.2 seams, fixtures and cases |
