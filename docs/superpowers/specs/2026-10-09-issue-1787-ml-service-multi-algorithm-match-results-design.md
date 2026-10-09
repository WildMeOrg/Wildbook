# Issue #1787 — Run every default algorithm on ml-service bulk imports, and link Match Results to the encounter-wide task

Date: 2026-10-09 (rev 3, after Codex rounds 1 and 2)
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
   run for that annotation's encounter, and only that.
3. Existing trees (e.g. the 2026-10-08 resend) show both algorithms as soon
   as the fix deploys, with no resend and no migration.
4. Preserve the issue #1744 fix: an image whose annotations belong to
   different encounters must not link to a page spanning those encounters.
5. Preserve per-import stamp isolation (issue #1624 / PR #1625).
6. The import's identification status reads complete only when every
   algorithm dispatched for every annotation has reached a terminal state;
   a failed dispatch is terminal and visible, never silently pending.
7. Annotations created by ml-service are registered with WBIA under their
   existing `acmId` (forced id), never through the on-demand path that lets
   WBIA mint a new uuid and rewrites `acmId`.

## 3. Non-goals

- Changing which options are default (IA.json content).
- Frontend changes. `matchResultsStore._processData` renders one section per
  task node; PR #1746 made Confirm act on the selected section's encounter;
  `BulkImportTask.jsx` renders `[taskId, status, label]` tuples verbatim.
- `loadExtractionContext`'s early `embedding-exists` return, which marks the
  extraction task completed without running any match. Pre-existing
  extraction idempotency; documented, not changed.
- Config drift between a deferred payload's `matchConfig` (visibility gate
  input) and the IA.json read at re-fire time (intake input). Both normally
  read the same file; documented.
- WBIA send retries. `IAGateway._doIdentify` marks `SEND_FAILED` on the
  task and requeues the job with bounded retries; a later success flips the
  task to completed through the callback. Unchanged; §4.4 states how the
  aggregate treats it.
- Pre-fix trees of images that hold annotations from several encounters
  (cloned encounters) keep linking to one algorithm's leaf. Showing every
  algorithm for those would need encounter-filtered rendering of a shared
  umbrella, which is out of scope. New trees get one umbrella per
  encounter (§4.1) so the case does not recur.
- Locale keys for `SEND_FAILED` / `PARTIAL` / `INVALID_MATCH_CONFIG`.
- The WBIA-detection pipeline (unsupported; untouched).

## 4. Design

### 4.0 One identifier for one identification run

Every task created by one call of the intake core carries
`identificationInvocationId` = the id of that call's umbrella. Intake strips
any inherited value, builds the inherited parameters, puts the umbrella's own
id in, and only then creates the option children, so option tasks, vector
leaves (`new Task(task)` in `Embedding.findMatchProspects`) and HotSpotter
fan-out children (`IAGateway._doIdentify` copies the option's parameters)
all carry it. The umbrella is the task whose own id equals the value; no
inherited boolean, so a reused parent cannot promote an option task to a
ceiling (Codex r2 #8). `IAGateway._doIdentify` keeps `importTaskId` and
`identificationInvocationId` when it clears the option parent's parameters
before fanning out; it still drops `ibeis.identification`, so renderability
of that parent is unchanged.

This one id gives Layer B its ceiling, lets the selector pick a run while it
is still pending, and gives Layer C the set of leaves that belong to one run.
Trees written before this change have no id; §4.3 and §4.4 state the
fallback rules for them.

### 4.1 Layer A — the ml-service match step hands each encounter to the shared intake

`runMatchProspects(annotationIds, taskId, matchConfig)` keeps everything
before it: the `skipIdent` short-circuit and the `MatchVisibilityGate` in
`waitAndRunMatchInternal`, and the deferred re-fire path still lands here.
`matchConfig` keeps its remaining roles (gate input,
`hasEmbeddingForMatchConfig`); it no longer selects the algorithm.

1. Load the parent task `D` (detection task, or the extraction task `E`
   created by `Annotation.createFromApi`) and the annotations as today;
   stale handling unchanged.
2. Group the annotations by encounter (`Annotation.findEncounter`, as
   `IBEISIA.processCallback` does). A detection batch is one media asset
   whose annotations may belong to cloned encounters; one umbrella per
   encounter is what lets Layer B reach a node spanning both algorithms
   without crossing an encounter boundary. Annotations with no encounter
   form their own group. `MlServiceProcessor.groupByEncounter` is pure over
   a precomputed annotation→encounter map.
3. Build the inherited parameters once:
   `MlServiceProcessor.matchIntakeParams(JSONObject parentParams)`, pure,
   returns a copy of `D`'s parameters without `ibeis.detection` (the
   detection gauges must not count match tasks). `importTaskId`,
   `matchingSetFilter`, `skipIdent` and anything else on `D` are inherited
   by every intake task, which stamps the bulk-import scope onto the match
   tree. `matchingAlgorithms`, if ever present, is honoured as for every
   other intake caller; it is never set on `D`.
4. For each group call
   `IA.intakeAnnotationsUnder(shep, anns, D, inheritedParams, /*fastlane*/ false)`
   (§4.2). It returns an `IA.IntakeOutcome` (§4.2.4). If
   `outcome.persistFailed`, nothing was dispatched: roll back and close
   `shep`, mark `D` error `MATCH_PERSIST` through a fresh Shepherd, return
   `persistError`. If `outcome.noWork`, mark the umbrella error
   `INVALID_MATCH_CONFIG` "no default identification options" (committed
   with the checked commit below).
5. After all groups: `shep.commitDBTransactionWithStatus()` (existing
   helper, returns false on any failure). On `false`: roll back and close
   `shep`; with a fresh Shepherd mark every vector option task in every
   outcome error `MATCH_PERSIST` (they were committed in §4.2.3 and are
   childless after the rollback, so §4.4 sees them as failed leaves), and
   mark `D` error `MATCH_PERSIST`. HotSpotter option tasks whose job was
   published keep their state; the job is durable and completes them.
   Return `persistError`.
6. If every group reported `noWork`, also mark `D` error
   `INVALID_MATCH_CONFIG` and return the same `validationError` as today.
7. Exceptions: roll back and close `shep` first, then
   `markTaskError(taskId, "MATCH", …)` through a fresh Shepherd (today the
   error is marked while the failed Shepherd is still open).

Resulting tree for one image with two encounters, two options each:

```
D (media asset only; ibeis.detection; importTaskId)
├── U1 (annA)  invocation=U1                    ← enc 1
│   ├── T_vec (annA) invocation=U1 ── SA (annA) invocation=U1, mlServiceV2Match
│   └── T_hs  (annA) invocation=U1
└── U2 (annB)  invocation=U2                    ← enc 2
    ├── T_vec (annB) ── SB (annB)
    └── T_hs  (annB)
```

For a species with one vector option only, intake uses `U` itself as the
single option task and `findMatchProspects` hangs the leaf under it
(`D → U → SA`), the same depth as today's `D → M → SA`.

Who marks `D` completed is unchanged: detection/extraction already mark it
before matching; the ml-service consumer only logs the outcome; the
HotSpotter leg completes asynchronously through the WBIA callback.

Method header: intake stores the IA.json option itself as
`ibeis.identification`, so `Task.getIdentificationMethodInfo` returns the
option's `description` ("MiewID msv4_v3 (vector)", "HotSpotter body
pattern-matcher") instead of the hard-coded "ml-service vector matching".
`isV2MatchTask` (root-only) is unaffected; `U` has a parent.

### 4.2 Intake changes (`IA.java`, shared by New Match, resend, ProjectIA, legacy callback and now ml-service)

**4.2.1 Entry point.** Add
`public static IA.IntakeOutcome intakeAnnotationsUnder(Shepherd, List<Annotation>, Task parent, JSONObject inheritedParams, boolean fastlane)`.
The existing `intakeAnnotations(Shepherd, List, Task parentTask, boolean)`
delegates to the same private core with `parent = null` (no linking, as
today) and `inheritedParams = parentTask == null ? null : parentTask.getParameters()`,
and returns `outcome.umbrella` to keep its signature. The two-argument
overload is untouched, so the literal-null call
`intakeAnnotations(myShepherd, anns, null, false)` stays unambiguous.
Inside the core each iaClass iteration works on a fresh copy of
`inheritedParams` (`new JSONObject(inheritedParams.toString())`, or an
empty object when null), matching today's per-iteration
`parentTask.getParameters()` re-parse, so classes never share
`ibeis.identification`. Null `parentTask`, null parameters on a non-null
`parentTask` (a fresh `handleRest` root, a `_doIdentify`-cleared parent) and
multiple classes are covered by tests. Compatibility: existing callers get
the same tree shape and the same parameters plus `identificationInvocationId`
on every task and `mlServiceV2Match` on vector option tasks and their
leaves.

**4.2.2 Markers.** `identificationInvocationId` as in §4.0. Each option
task whose option is a vector config (`Embedding.isVectorConfig(opt)`,
extracted from the first lines of `findMatchProspects`) gets
`mlServiceV2Match: true` before `findMatchProspects` creates its leaves, so
the leaves inherit it and the `/metrics` vector-match gauge keeps counting
them. HotSpotter siblings never carry it. Side effect, intentional: vector
leaves from New Match and resend are now counted too; `MetricsBot`'s gauge
HELP text and comment change to "vector identification leaf tasks (all
entry points; counts errors too)".

**4.2.3 Build, persist, then dispatch.** The core is restructured into two
phases over the same tree it builds today:

1. Build: umbrella, per-class option tasks (or the umbrella itself for the
   single-option single-class case), parameters and markers. When `parent`
   is non-null, `topTask.setParent(parent)` (which also adds the child;
   `parent` is attached and persistent, so DataNucleus persists the
   relationship on commit without a separate `makePersistent(parent)`).
2. Persist: `pm.makePersistent(topTask)` (children by reachability), then
   `commitDBTransactionWithStatus()`. On `false`: return an outcome with
   `persistFailed = true` and nothing dispatched. On `true`:
   `beginDBTransaction()` and continue. This replaces `storeNewTask`, whose
   internal commit swallows failures and returns `true` regardless.
3. Dispatch: for each option task, vector options run inline
   (`findMatchProspects`, as today); HotSpotter options are published to
   the queue. Publication failure (`addToQueue` returns false or throws)
   marks that option task error `QUEUE_FAILED` (terminal); today intake only
   logs it and the task stays pending forever.

By the time any job is published or any vector match runs, the umbrella,
its option tasks and the `U → parent` link are durable. The vector leaves and
MatchResults are committed by the caller (every existing caller already
commits after intake; the ml-service step uses the checked commit in §4.1).

**4.2.4 Outcome.** `IA.IntakeOutcome { Task umbrella; List<Task> vectorOptionTasks;
List<Task> queuedOptionTasks; List<String> skippedIaClasses; boolean noWork;
boolean persistFailed; }`. `noWork` is true when no class produced any
option (`IA.intakeProducedNoWork(umbrella)`: no children and no
`ibeis.identification`). A class with no options is listed in
`skippedIaClasses`; its annotations are on the umbrella but on no option
task, which §4.4 treats as terminal-skipped.

**4.2.5 Stable WBIA registration at identify time.** `IBEISIA.sendAnnotationsAsNeeded`
(called by `beginIdentifyAnnotations` for query **and** target annotations,
inside the queue consumer's Shepherd) gains a forced branch. For each
annotation with `Boolean.FALSE.equals(getWbiaRegistered())` (set only by
ml-service persistence, where `acmId == id`):

- if its media asset has no `acmId`, set `ma.setAcmId(ma.getUUID())` (the
  convention `AcmIdBot` already uses) so the image has a forced uuid;
- if the image is not in `iaImageIds`, register it with
  `sendMediaAssetsForceId` (forces the image uuid = `MediaAsset.acmId`);
- register the annotation with `sendAnnotationForcedByAcmId(ann, shep)`,
  which forces the identifier identification actually sends (`acmId`) and
  returns true only when WBIA echoes that exact uuid;
- on success `ann.setWbiaRegistered(TRUE)` on the attached object; the
  consumer's transaction commits it. On failure the existing exception path
  applies (`sendAnnotMAException` → `beginIdentifyAnnotations` fails →
  `SEND_FAILED` + bounded requeue).

Annotations with `null` (legacy, backfilled) or `TRUE` take today's
non-forced path unchanged. No nested Shepherd: the forced calls use the
caller's Shepherd exactly as the non-forced calls do, and
`_sendIdentificationTask` performs no annotation writes before
`beginIdentifyAnnotations` (verified: it only reads the matching set).
Concurrency with the background poller (`StartupWildbook` WBIA registration
poll): both force the same uuid, WBIA treats a repeated forced add as
already present (`REGISTERED_ALREADY_PRESENT`), and the poller's persist
phase skips an annotation that is already `TRUE`; the consumer's commit of
`TRUE` is idempotent with the poller's. Annotation has no JDO optimistic
version strategy, so concurrent `TRUE` writes do not throw. Parked
annotations (attempts exhausted) are still registered here, because this
branch does not consult `wbiaRegisterAttempts`; that is intended — identify
time is the moment the registration is needed. This closes Codex r2 #5–#7
without a registrar facade or intake-side registration: targets are
covered, the identifier forced is the one identification uses, and intake
never touches WBIA.

### 4.3 Layer B — hoist the selected task to the run's umbrella

`Task.selectPreferredMatchTask` gains a third argument:

```java
static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId,
                                     Set<String> encounterAnnotationIds)
```

`encounterAnnotationIds` is the set of annotation ids of the query
annotation's encounter. The two-argument method delegates with `null` =
"do not hoist", so callers that do not opt in keep today's behaviour. Both
known callers opt in (§4.5).

**Renderability addition.** A task whose own id equals its
`identificationInvocationId` (an umbrella) is renderable. Within the
existing created-DESC precedence the newest umbrella holding the annotation
therefore wins as soon as the run is created, before any leaf exists; a
newer pending run beats an older completed one; and the root fallback is no
longer reached for new trees (Codex r2 #2). The umbrella renders every
option subtree under it, which is the intended page.

**Hoist.** After the winner is chosen, when the set is non-null:

```
inv = invocationId(winner)
if inv != null:                                   # new tree
    target = nearest ancestor-or-self with id == inv (≤ 20 hops, cycle-guarded)
    if target found and every annotation on target ∈ encounterAnnotationIds
       and stamp(target) == stamp(winner):   return target
    return winner
else:                                             # old tree: structural climb
    best = winner; winnerStamp = stamp(winner)
    up = winner.parent; hops = 0; visited = {winner.id}
    while up != null and up.id != null and hops++ < 20 and visited.add(up.id):
        if up.numberAnnotations() == 0:                                   stop  # roots, sub-parents
        if up has objectMediaAssets or params has "ibeis.detection":      stop  # detection orchestration
        if up.parent == null and up.parameters == null:                   stop  # parameterless root: extraction task E
        if any annotation on up ∉ encounterAnnotationIds:                 stop  # #1744 boundary
        if stamp(up) != null and !stamp(up).equals(winnerStamp):          stop  # #1624 boundary
        if stamp(up) == null and winnerStamp != null and
           not every direct child of up carries winnerStamp:              stop  # unstamped bridge only when its children are ours
        best = up; up = up.parent
    return best
```

Wrapped in try/catch that falls back to the best task found so far. The walk
calls `getParent()` / `getObjectAnnotations()` / `getChildren()` on attached
objects inside the caller's open transaction (lazy loading;
`recursion-depth="3"` on `Task.parent` bounds detached fetch plans, not
this walk); a datastore test reloads a five-level tree and checks the
landing node.

Builder invariants the old-tree rules rely on, stated so they can be
checked: (I1) a child's parameters are copied from its parent's (or are
null); no builder gives a child a stamp its parent lacks, so "every direct
child carries the winner's stamp" implies the whole subtree does.
(I2) a descendant's annotation set is a subset of its ancestor's (intake
bins the umbrella's annotations by class; `findMatchProspects` and
`_doIdentify` fan out one annotation per child), so an ancestor whose
annotations are all in the encounter has no descendant outside it.

Old-tree shapes:

| old tree | landing | note |
|---|---|---|
| resend / legacy callback: `subParent(no annots) → U → options` | `U` | |
| New Match: `root(media assets) → U → …` | `U` | |
| ml-service single option: `D(media assets, ibeis.detection) → M → SA` | `M` | renders `SA`; same content |
| extraction: `E(annotation, no params, no parent) → M_old → S_old; E → M_new → S_new` | `M_new` | `E` is a parameterless root; resolves the Codex r2 #3 counterexample |
| `_doIdentify` fan-out: `U(stamped) → T_hs(params null) → H_A, H_B(stamped)` | `U` | bridge: both children carry the winner's stamp |
| image with two encounters: `D → M(annA, annB) → SA, SB` | `SA` | #1744 preserved; one algorithm, see §3 |
| ancestor stamped with another import | last permitted node | never crosses the foreign stamp |

Unscoped selection (encounter page) applies the same stamp rule with the
winner's own stamp.

### 4.4 Layer C — identification status across every algorithm of the run

In `ImportTask.statsAnnotations`, the per-annotation "latest task" status
becomes an aggregate over the leaves of the annotation's newest run:

- run = `identificationInvocationId` of the annotation's newest direct
  task (`atm.get(ann)` is created-DESC). If null (old tree), today's rule
  applies: the newest direct task's status.
- leaves = the annotation's direct tasks that carry that run id and have
  no children. The umbrella and option parents are never counted.
- per-annotation status, in this precedence: any leaf non-terminal →
  that status (`sent` side); else any leaf `error` → `error`; else
  `completed`. Pending always wins over error, so a failed vector leaf next
  to a running HotSpotter leaf keeps the import at "sent" (Codex r2 #1).
  No leaves (a class with no option, §4.2.4) → `completed`, plus an
  informational `numLatestTask_skipped` counter.
- `SEND_FAILED`: the leaf is in error and a bounded requeue may still
  succeed and flip it to completed through the callback. The aggregate
  reports error (terminal) at that moment; a later success is visible on
  the next page load. Existing WBIA retry semantics, see §3.
- maximum age: a childless non-terminal leaf whose `timeInactive()` exceeds
  `Task.TIMEOUT_INACTIVE_MILLIS` (seven days since the task row was last
  modified; `Task.modified` moves on `setStatus`/`setModified`, not on
  WBIA log activity) is reported as `error` by this aggregate even when
  its stored status is null, which `Task.getStatus` exempts from its own
  watchdog. This is a maximum-age policy, not liveness detection: a WBIA
  job that is still alive after seven days is reported as failed until it
  completes, at which point the callback sets the task completed and the
  next poll shows it. Reported only; nothing is persisted.

`numLatestTasks` stays exactly one per non-trivial annotation, so
`iaSummaryJson`'s formula (`completed + error >= total` → complete) is
unchanged. Per-task `sa.put(status)` tallies are unchanged.

### 4.5 Call sites

- `Encounter.jsonForApiGet` → `Annotation.getPreferredMatchResultsTask`:
  pass the ids of `this.getAnnotations()`; the encounter is in hand.
- `ImportTask.statsAnnotations`: after `annotationIdsToEncounterIds`, one
  more SQL pass over `ENCOUNTER_ANNOTATIONS` keyed by the distinct encounter
  ids (`encounterIdsToAnnotationIds`) gives each encounter's **full**
  annotation id set, including annotations added to the encounter after
  the import (the Mantamatcher workflow: annotate failed detections by
  hand, then resend, so the resend umbrella holds annotations the import
  never created). No object loads, no JDOQL. When several annotations of
  one encounter resolve to the same preferred task, the Class label is the
  distinct non-blank iaClasses sorted alphabetically and joined with ", ".
  `BulkImportTask.jsx` renders the label verbatim. If the SQL pass fails,
  selection falls back to the unhoisted two-argument call.

## 5. Testing

### 5.1 Unit (JUnit 5, no datastore)

`TaskSelectPreferredMatchTaskTest` (hand-built trees):
- new-tree hoist: leaf with invocation id → umbrella; stops at the umbrella
  even when the parent would pass every structural check; umbrella
  renderable while pending (no leaves yet) and chosen over an older
  completed run's leaf; inherited id on a reused parent does not make an
  option task a ceiling;
- old-tree hoist: the live Mantamatcher shape (scoped; body and rostrum
  both land on `U`); unscoped one-annotation two-option shape; stamp matrix
  (winner stamped/unstamped × ancestor same/other/unstamped-all-children-
  stamped/unstamped-with-a-foreign-child); `_doIdentify` bridge; stops at
  multi-encounter ancestor, zero-annotation ancestor, media-asset ancestor,
  `ibeis.detection` ancestor, parameterless root (`E` with two runs);
- null set → no hoist; cycle guard; hop cap; throwing parent → best so far.
- Existing tests updated where the target changes;
  `nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf` is replaced by
  the scoped Mantamatcher-shape test.

Pure helpers:
- `MlServiceProcessor.matchIntakeParams`, `groupByEncounter`.
- `IA.intakeProducedNoWork`; intake-core parameter handling: null parent;
  non-null parent with null parameters; two classes do not share
  `ibeis.identification`; inherited `identificationInvocationId` is
  replaced; markers on umbrella / vector option only; single-option case
  keeps the id on the umbrella.
- `Embedding.isVectorConfig`.
- `ImportTask` status aggregate (pure over leaf statuses + ages): all
  completed; error + pending → pending; error only → error; none → skipped;
  aged null-status leaf → error; old tree → newest-task rule.
- `ImportTask` class-label merge: order, dedupe, blanks.

### 5.2 Datastore (JUnit 5 + Testcontainers PostgreSQL, the pattern of `MarkedIndividualTaxonomyBackfillDbTest`)

- Persist-before-dispatch: call the intake core with a stub dispatcher that
  records calls; assert from a second PersistenceManager that the umbrella,
  its option tasks and the `U → parent` link exist before the first
  dispatch call, and that a forced commit failure (closed connection)
  yields `persistFailed` with zero dispatch calls.
- Hoist over reloaded objects: persist a five-level tree, reload in a new
  PersistenceManager, run the selector, assert the landing node and that
  `matchResultsJson` of the landing node lists exactly the run's leaves.
- `statsAnnotations → iaSummaryJson` on a persisted import: completed
  vector + pending HotSpotter → "sent"; error vector + pending HotSpotter →
  "sent"; both terminal → "complete" with the error count; skipped class;
  exactly one `numLatestTasks` per annotation; two encounters sharing a
  class each report independently.

### 5.3 Controlled integration before any production deploy

On a Wild Me staging Wildbook with WBIA and ml-service reachable (host
chosen by the user), with the WAR built from this branch:
1. resend one encounter of a two-option species: umbrella under the
   sub-parent, both option tasks terminal, bulk page shows both algorithms;
2. a two-encounter image: two umbrellas under `D`;
3. WBIA stopped: HotSpotter option ends `SEND_FAILED` after retries, vector
   leaf completes, import reaches complete-with-errors;
4. queue directory unwritable: `QUEUE_FAILED` terminal;
5. an ml-service annotation with `wbiaRegistered = false`: after the
   HotSpotter send, WBIA holds it under its original `acmId`, the flag is
   `TRUE`, and the poller skips it; run the poller and the send
   concurrently on one annotation and confirm one registration;
6. restart Tomcat and reload the bulk page: links unchanged.

### 5.4 Mantamatcher after deploy

Import 26e2c50c's links show both algorithms per annotation with no
resend; a small new guitarfish import produces one umbrella per encounter
with a vector leaf and a HotSpotter task each; `/metrics` vector gauge
still moves.

## 6. Risks and mitigations

- Forced registration adds WBIA round trips inside the identify job for
  not-yet-registered annotations (queries and targets). This replaces the
  non-forced round trips the same job already makes for the same
  annotations; no new network dependency.
- A bulk import stays "sent" until WBIA answers; capped at seven days by
  the maximum-age rule.
- Hoisting changes the linked task id for existing single-option v2 trees
  (`SA` → `M`); content identical.
- The vector-match gauge now counts New Match and resend vector leaves.
- `Task.getOverallStatus` walks two levels; for the umbrella its children
  are option tasks and grandchildren vector leaves, both counted.
- Intake's internal commit now aborts on failure for every caller instead
  of proceeding blind. Callers that ignored `storeNewTask`'s boolean get a
  null umbrella (existing overload) or `persistFailed` (new entry point)
  and nothing dispatched, which is strictly safer.

## 7. Blast radius

Files: `ia/MlServiceProcessor.java` (runMatchProspects, two helpers),
`ia/IA.java` (entry point, core restructure, markers, outcome, dispatch
hardening, helper), `Embedding.java` (helper), `ia/Task.java` (selector,
renderability clause), `servlet/IAGateway.java` (keep two params on the
cleared parent), `identity/IBEISIA.java` (forced branch in
`sendAnnotationsAsNeeded`), `Annotation.java` / `Encounter.java` (opt-in),
`servlet/importer/ImportTask.java` (opt-in, status aggregate, label merge,
SQL helper), `MetricsBot.java` (gauge text), tests. No schema change, no
migration, no frontend change.

## 8. Supersedes

Local branch `fix/match-results-multialgo-parent-task` (unpushed,
2026-07-29): its unconditional unscoped hoist would re-open issue #1744 and
it left the bulk-import page unfixed.

## 9. Codex dispositions

Round 1 (12 items) and round 2 (9 items) are in the review log for this
branch. Round-2 items and where rev 3 answers them:

| r2 | finding | rev 3 |
|---|---|---|
| 1 | aggregate reports completion while an algorithm is alive; inactivity claim false | §4.4 pending-over-error precedence; `SEND_FAILED` stated as retry semantics; maximum-age policy named as such |
| 2 | status cannot depend on the results-link selector; pending runs unselectable | §4.0 invocation id; §4.3 umbrella renderability; §4.4 keyed by the newest run id, not the link |
| 3 | hoist isolation unproven (extraction `E` with two runs; bridge grandchildren) | §4.3 parameterless-root stop, invariants I1/I2, new trees stop at the umbrella by id |
| 4 | checked commit does not cover the dispatch boundary; recovery incomplete | §4.2.3 persist-then-dispatch with `commitDBTransactionWithStatus`; §4.1 steps 4–7 recovery marks durable vector options |
| 5 | helper visibility does not compile | registrar helpers no longer used; §4.2.5 |
| 6 | registration lacks transaction isolation / claim | §4.2.5: no nested Shepherd, idempotent forced uuid, no claim needed |
| 7 | targets unprotected; wrong identifier forced | §4.2.5 forced branch in the shared registration path, forces `acmId` |
| 8 | marker lifecycle | §4.0 invocation id equals the umbrella's own id |
| 9 | tests | §5.2 datastore tests, §5.3 failure and concurrency checks |
