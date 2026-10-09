# Issue #1787 — Run every default algorithm on ml-service bulk imports, and link Match Results to the encounter-wide task

Date: 2026-10-09 (rev 2, after Codex round 1)
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
MiewID only. Two independent layers produce that symptom, and a third
problem surfaces as soon as the first two are fixed.

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
annotation's single newest direct task. In the tree above the newest task is
the vector leaf, so the import reports identification complete while
HotSpotter is still queued. Today that is masked (the link shows MiewID only
anyway); once both algorithms are linked, "complete" must mean both.

### Evidence (Mantamatcher import 26e2c50c, 2026-10-09 SQL)

| created | tasks | meaning |
|---|---|---|
| 2026-10-08 | 105 HotSpotter completed, 210 "MiewID msv4_v3 (vector)" completed, 55 umbrellas | resend ran BOTH options for all 105 annotations (55 encounters: 50 with body+rostrum, 5 single) |
| any | 0 tasks with `mlServiceV2Match` | this import predates the vector config; no first-pass ml-service match ran |

On this import the live symptom is Layer B alone. New imports hit Layer A
first, then Layer B, then Layer C.

## 2. Goals

1. After ml-service detection or extraction, run every `default: true`
   identification option for each annotation, as New Match and resend do.
2. The Match Results link on the bulk-import page and the encounter page
   opens a page showing every algorithm's results for that annotation's
   encounter.
3. Existing trees (e.g. the 2026-10-08 resend) show both algorithms as soon
   as the fix deploys, with no resend and no migration.
4. Preserve the issue #1744 fix: an image whose annotations belong to
   different encounters must not link to a page spanning those encounters.
5. Preserve per-import stamp isolation (issue #1624 / PR #1625).
6. The import's identification status reads complete only when every
   algorithm dispatched for every annotation has reached a terminal state,
   and a failed dispatch is terminal, never silently pending.
7. HotSpotter on ml-service-created annotations uses the stable forced WBIA
   id, never the on-demand registration that rewrites `acmId`.

## 3. Non-goals

- Changing which options are default (IA.json content).
- Frontend changes. `matchResultsStore._processData` renders one section per
  task node; PR #1746 made Confirm act on the selected section's encounter;
  `BulkImportTask.jsx` renders `[taskId, status, label]` tuples verbatim.
- `loadExtractionContext`'s early `embedding-exists` return, which marks the
  extraction task completed without running any match. Pre-existing
  extraction idempotency; documented, not changed.
- Config drift between a deferred payload's `matchConfig` (used by the
  visibility gate) and the IA.json read at re-fire time (used by intake).
  Both normally read the same file; documented.
- Locale keys for `SEND_FAILED` / `PARTIAL` / `INVALID_MATCH_CONFIG`.
- The WBIA-detection pipeline (unsupported; untouched).

## 4. Design

### 4.1 Layer A — the ml-service match step hands each encounter to the shared intake

`runMatchProspects(annotationIds, taskId, matchConfig)` keeps everything
before it: the `skipIdent` short-circuit and the `MatchVisibilityGate` in
`waitAndRunMatchInternal`, and the deferred re-fire path still lands here.
`matchConfig` keeps its remaining roles (gate input,
`hasEmbeddingForMatchConfig`); it no longer selects the algorithm.

Inside:

1. Load the parent task `D` (detection task, or the extraction task `E`
   created by `Annotation.createFromApi`) and the annotations as today;
   stale handling unchanged.
2. **Group the annotations by encounter** (`Annotation.findEncounter`, as
   `IBEISIA.processCallback` does). A detection batch is one media asset
   whose annotations may belong to cloned encounters; one umbrella per
   encounter is what lets Layer B reach a node that spans both algorithms
   without crossing an encounter boundary. Annotations with no encounter
   form their own group.
3. Build the inherited parameters once:
   `MlServiceProcessor.matchIntakeParams(JSONObject parentParams)`, a pure
   static helper returning a copy of `D`'s parameters without
   `ibeis.detection` (the detection gauges must not count match tasks).
   `importTaskId`, `matchingSetFilter`, `skipIdent` and anything else on `D`
   are inherited by every intake task; that is what stamps the bulk-import
   scope onto the match tree. `matchingAlgorithms`, if ever present, is
   honoured exactly as for other intake callers; it is never set on `D`.
4. For each encounter group call
   `IA.intakeAnnotationsUnder(shep, anns, D, inheritedParams, /*fastlane*/ false)`
   (§4.2). It links the umbrella under `D` and persists it **before** any
   work is dispatched, runs vector options inline, and dispatches HotSpotter
   options to the WBIA identify queue after forced registration.
5. Commit with a checked commit (§4.2.5). On failure: roll back and close
   the active Shepherd first, then with a fresh Shepherd mark every umbrella
   created in this call, and `D`, error `MATCH_PERSIST`. The umbrellas were
   committed by `storeNewTask` in step 4, so they are loadable and the
   import sees a terminal error instead of a silent "pending".
6. Empty outcome per group: if `IA.intakeProducedNoWork(U)` (no children and
   no `ibeis.identification`), mark `U` error `INVALID_MATCH_CONFIG` "no
   default identification options". If every group produced no work, mark
   `D` error `INVALID_MATCH_CONFIG` as today and return the same
   `validationError`. Partial cases (one class without options) are handled
   by Layer C: an annotation with no option task in its umbrella counts as
   terminal-skipped, not pending.
7. Exceptions: roll back and close the active Shepherd, then
   `markTaskError(taskId, "MATCH", …)` through a fresh Shepherd (today the
   error is marked while the failed Shepherd is still open).

Resulting tree for one image with two encounters, two options each:

```
D (media asset only; ibeis.detection; importTaskId)
├── U1 (annA)  identificationRoot            ← enc 1
│   ├── T_vec (annA) ── SA (annA)  mlServiceV2Match
│   └── T_hs  (annA)
└── U2 (annB)  identificationRoot            ← enc 2
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
`public static Task intakeAnnotationsUnder(Shepherd, List<Annotation>, Task parent, JSONObject inheritedParams, boolean fastlane)`.
The existing `intakeAnnotations(Shepherd, List, Task parentTask, boolean)`
delegates to a private core with `parent = null` (no linking, as today) and
`inheritedParams = parentTask == null ? null : parentTask.getParameters()`.
The existing two-argument overload is untouched, so the literal-null call
`intakeAnnotations(myShepherd, anns, null, false)` stays unambiguous (a
same-arity `JSONObject` overload would not compile; the 5-argument name
avoids that). Inside the core each iaClass iteration works on a fresh copy
of `inheritedParams` (`new JSONObject(inheritedParams.toString())`, or an
empty object when null), matching today's per-iteration
`parentTask.getParameters()` re-parse, so classes never share
`ibeis.identification`. Null `parentTask`, null parameters on a non-null
`parentTask` (a fresh `handleRest` root, a `_doIdentify`-cleared parent) and
multiple classes are all covered by tests. Compatibility guarantee: existing
callers produce the same tree shape and the same parameters, plus the two
new markers below.

**4.2.2 Markers.** The umbrella (`topTask`) gets `identificationRoot: true`.
Each option task whose option is a vector config
(`Embedding.isVectorConfig(opt)`, extracted from the first lines of
`findMatchProspects`) gets `mlServiceV2Match: true` before
`findMatchProspects` creates its leaves, so the leaves inherit it and the
`/metrics` vector-match gauge keeps counting them. HotSpotter siblings never
carry it. Side effect, intentional: vector leaves from New Match and resend
are now counted too; `MetricsBot`'s gauge HELP text and comment change to
"vector identification leaf tasks (all entry points; counts errors too)".

**4.2.3 Linking before dispatch.** When `parent` is non-null the core calls
`topTask.setParent(parent)` (which also adds the child) **before** the first
`storeNewTask(topTask)`. `storeNewTask` persists the umbrella and, by
reachability, its option children in its own commit, so by the time any job
is published or any vector match runs, `U`, its children and the `U → D`
link are durable.

**4.2.4 HotSpotter dispatch.** For each HotSpotter option task, before the
queue job is published:

- For every annotation in the option task with
  `Boolean.FALSE.equals(ann.getWbiaRegistered())` (ml-service-created,
  not yet registered), run the background registrar's three phases
  synchronously for that annotation: `StartupWildbook.loadWbiaRegisterDto`
  → `WildbookIAM.registerOneByDto` → `StartupWildbook.persistWbiaRegisterResult`.
  The two StartupWildbook phases become package-private static helpers
  callable from `IA` (no behaviour change for the poller). This is the
  forced-id path (`sendAnnotationsForceId`, `annot_uuid_list` = `acmId`),
  so `acmId` never changes. `null` (legacy, backfilled) and `TRUE` skip
  this step.
- If any annotation's registration fails or is parked (attempts exhausted,
  ineligible), the option task is marked error `WBIA_REGISTRATION_FAILED`
  (terminal, with the reason) and the job is **not** published. The
  background poller keeps retrying the annotation; a later resend picks it
  up. The vector option is unaffected.
- If `IAGateway.addToQueue` returns false or throws, the option task is
  marked error `QUEUE_FAILED` (terminal). Today intake only logs this and
  the task stays pending forever.

Because the dispatch now goes through the forced path first,
`IBEISIA.beginIdentifyAnnotations`' on-demand `sendAnnotationsAsNeeded`
finds the annotation already present and does not rewrite `acmId`. The
race with the background poller is closed by the `wbiaRegistered` flag:
the poller claims only `wbiaRegistered == false`, and
`persistWbiaRegisterResult` sets it `TRUE` on success in its own
transaction; if both run at once, the second forced send is idempotent
(`checkForcedIds`).

**4.2.5 Checked commit.** Add `public boolean Shepherd.commitDBTransactionChecked()`:
same body as `commitDBTransaction` but returns `false` from every catch
branch. Only the ml-service step uses it; nothing else changes.

**4.2.6 `IAGateway._doIdentify`.** When it clears the parent's parameters
before fanning out per annotation (`parentTask.setParameters((String)null)`),
it now keeps `importTaskId` (only) on the parent. The parent still has no
`ibeis.identification`, so renderability is unchanged; the stamp bridge in
§4.3 then sees a stamped parent on new trees.

### 4.3 Layer B — hoist the selected task to the encounter-wide ancestor

`Task.selectPreferredMatchTask` gains a third argument:

```java
static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId,
                                     Set<String> encounterAnnotationIds)
```

`encounterAnnotationIds` is the set of annotation ids of the query
annotation's encounter. The two-argument method delegates with `null` =
"do not hoist", so callers that do not opt in keep today's behaviour. Both
known callers opt in (§4.5).

Selection is unchanged up to the point where a winner exists (renderable
first, stamp precedence, root fallback). Then, when the set is non-null:

```
best = winner; winnerStamp = stamp(winner)           # null when unstamped
up = winner.parent; hops = 0; visited = {winner.id}
while up != null and up.id != null and hops++ < 20 and visited.add(up.id):
    if up.numberAnnotations() == 0:                                   stop  # detection/New Match roots, sub-parents
    if up has objectMediaAssets or params has "ibeis.detection":      stop  # detection orchestration
    if any annotation on up is not in encounterAnnotationIds:         stop  # #1744 boundary
    if stamp(up) != null and !stamp(up).equals(winnerStamp):          stop  # #1624 boundary
    if stamp(up) == null and winnerStamp != null and
       not every direct child of up carries winnerStamp:              stop  # unstamped bridge only when its whole subtree is ours
    best = up
    if params(up) has identificationRoot:                             stop  # the invocation umbrella is the ceiling
    up = up.parent
return best
```

Wrapped in try/catch that falls back to the best task found so far; the
walk calls `getParent()` / `getObjectAnnotations()` / `getChildren()` on
attached objects inside the caller's open transaction (lazy loading;
`recursion-depth="3"` on `Task.parent` bounds detached fetch plans, not
this walk), covered by a datastore reload test in the plan.

Why the ceiling matters: an ancestor that holds the query annotation can be
a container of several identification runs. `Annotation.createFromApi`
builds the extraction task `E` with the annotation on it, and every
re-fired match under `E` hangs a new umbrella there; rendering `E` would
show every run. New trees stop at the marked umbrella. Old trees have no
marker, so the structural rules apply:

| old tree | climb from the leaf | lands on |
|---|---|---|
| resend / legacy callback: `subParent(no annots) → U → options` | `U`, then `subParent` has no annotations | `U` |
| New Match: `root(media assets) → U → …` | `U`, then `root` has media assets | `U` |
| ml-service single option: `D(media assets, ibeis.detection) → M → SA` | `M`, then `D` stops | `M` (renders `SA`; same content) |
| extraction single option: `E(annotation) → M → SA` | `M`, then `E` (annotation ∈ encounter, no media assets) | `E` — renders every run under `E`; normally exactly one. Accepted and documented; new extraction trees stop at the marked umbrella |
| `_doIdentify` fan-out: `U(stamped) → T_hs(params null) → H_A, H_B(stamped)` | `T_hs` is unstamped; both children carry the winner's stamp → bridge | `U` |
| image with two encounters: `D → M(annA, annB) → SA, SB` | `M` holds the other encounter's annotation | `SA` (issue #1744 preserved) |
| ancestor stamped with another import | stops | leaf |

Unscoped selection (encounter page) applies the same stamp rule with the
winner's own stamp, so a stamped leaf never climbs into an unstamped or
differently stamped container.

### 4.4 Layer C — identification status across every algorithm of the invocation

In `ImportTask.statsAnnotations`, the per-annotation "latest task" status
becomes an aggregate over the annotation's leaves inside its preferred
(hoisted) task:

- `P` = the task Layer B selected for the annotation (hoisted). If `P` is
  null, today's rule (newest direct task) applies.
- leaves = the annotation's direct tasks (`atm.get(ann)`, already loaded)
  that have no children and are `P` itself or a descendant of `P` (parent
  walk, ≤ 20 hops, cycle-guarded).
- per-annotation status: `error` if any leaf is in error; else the first
  non-terminal leaf's status (e.g. `processing`, `queuing`, `waiting to
  queue`); else `completed`. No leaves at all (a class with no
  identification option) → `completed`, and an informational
  `numLatestTask_skipped` counter is incremented as well.
- inactivity: a childless non-terminal leaf whose `timeInactive()` exceeds
  `Task.TIMEOUT_INACTIVE_MILLIS` counts as `error` for this aggregate even
  when its stored status is null (`Task.getStatus` exempts null status from
  its own watchdog, so a WBIA callback that never arrives would otherwise
  hold the import at "sent" forever). Reported only; nothing is persisted.

`numLatestTasks` stays one per non-trivial annotation, so
`iaSummaryJson`'s formula (`completed + error >= total` → complete) is
unchanged. The per-task `sa.put(status)` tallies are unchanged.

Consequence: a bulk import reads "sent" until the HotSpotter leg returns
from WBIA, which is the intended meaning of identification complete and the
pre-10.14 behaviour; the resend button covers a dead WBIA, and the
inactivity rule caps the wait at seven days.

### 4.5 Call sites

- `Encounter.jsonForApiGet` → `Annotation.getPreferredMatchResultsTask`:
  pass the ids of `this.getAnnotations()`; the encounter is in hand.
- `ImportTask.statsAnnotations`: after `annotationIdsToEncounterIds`, one
  more SQL pass over `ENCOUNTER_ANNOTATIONS` keyed by the distinct
  encounter ids (`encounterIdsToAnnotationIds`) gives each encounter's
  **full** annotation id set, including annotations added to the encounter
  after the import (the Mantamatcher workflow: annotate failed detections
  by hand, then resend, so the resend umbrella holds annotations the
  import never created). No object loads, no JDOQL.
  When several annotations of one encounter resolve to the same preferred
  task, the Class label is the distinct non-blank iaClasses sorted
  alphabetically and joined with ", ". `BulkImportTask.jsx` renders the
  label verbatim. If the encounter set cannot be built (SQL failure),
  selection falls back to the unhoisted two-argument call.

## 5. Testing

Unit, JUnit 5, no datastore:

`TaskSelectPreferredMatchTaskTest` (hand-built trees):
- hoist to umbrella, unscoped, one annotation, two options;
- hoist to umbrella, scoped, the live Mantamatcher shape (body + rostrum,
  four option tasks, two vector leaves): body and rostrum both land on `U`;
- stamp matrix, scoped and unscoped: winner stamped/unstamped × ancestor
  stamped-same/stamped-other/unstamped-with-all-children-stamped/
  unstamped-with-a-foreign-child → climb or stop as §4.3;
- `_doIdentify` cleared-parameter bridge: climbs through to `U`;
- stop at a multi-encounter ancestor (#1744 tree), at a zero-annotation
  ancestor, at a media-asset ancestor, at an `ibeis.detection` ancestor,
  at the `identificationRoot` ceiling (does not climb past it even when
  the parent would qualify);
- null set → no hoist (two-argument overload unchanged);
- cycle guard and hop cap; broken parent (throws) → best-so-far.
- Existing tests updated where the target changes; the
  `nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf` trade-off test
  is replaced by the scoped Mantamatcher-shape test.

New pure helpers:
- `MlServiceProcessor.matchIntakeParams`: strips `ibeis.detection`; keeps
  `importTaskId`, `matchingSetFilter`, `skipIdent`; null-safe.
- `MlServiceProcessor.groupByEncounter` (pure over a precomputed
  annotation→encounter map): two encounters sharing a class → two groups;
  no-encounter annotations → own group.
- `IA.intakeProducedNoWork`: childless + no `ibeis.identification` → true;
  either present → false.
- `Embedding.isVectorConfig`: method / api_endpoint / neither / null.
- `ImportTask` status aggregate (pure over a list of leaf statuses +
  inactivity flags): all completed; one error; one pending; none (skipped);
  inactive null-status leaf → error.
- `ImportTask` class-label merge: order, dedupe, blanks.
- `IA` intake-core parameter copying: null parent; non-null parent with
  null parameters; two classes do not share `ibeis.identification`;
  markers present on umbrella / vector option only.

Datastore-bound and therefore not unit-tested here:
`runMatchProspects` orchestration, `IA.intakeAnnotationsUnder` persistence
ordering, forced registration, the hoist over reloaded objects more than
three levels deep. Validation plan:

1. **Staging check before any production deploy** on a Wild Me staging
   Wildbook with WBIA and ml-service reachable (the user picks the host):
   (a) resend one encounter of a two-option species: tree has the umbrella
   under the sub-parent, both option tasks terminal, bulk page shows both;
   (b) a two-encounter image: two umbrellas under `D`; (c) WBIA stopped:
   HotSpotter option task ends `WBIA_REGISTRATION_FAILED` or `SEND_FAILED`,
   vector leaf completes, import reaches complete-with-errors; (d) queue
   directory unwritable: `QUEUE_FAILED` terminal; (e) reload the page
   after Tomcat restart: hoist still lands on the umbrella (objects loaded
   fresh, more than three levels).
2. **Mantamatcher after deploy:** import 26e2c50c's links show both
   algorithms per annotation with no resend; a small new guitarfish import
   produces one umbrella per encounter with a vector leaf and a HotSpotter
   task each; `/metrics` vector gauge still moves.

## 6. Risks and mitigations

- Forced registration adds one synchronous WBIA round trip per
  not-yet-registered annotation before the HotSpotter job is queued. Bulk
  imports run through the ml-service consumer thread; the vector leg has
  already run inline, so this only delays the HotSpotter queue insert.
  Failure is terminal and visible, not blocking.
- A bulk import stays "sent" until WBIA answers; capped at seven days by
  the inactivity rule.
- Hoisting changes the linked task id for existing single-option v2 trees
  (`SA` → `M`); content identical. Old extraction trees may link to `E`
  (§4.3 table).
- The vector-match gauge now counts New Match and resend vector leaves.
- `Task.getOverallStatus` walks two levels; for the hoisted umbrella its
  children are option tasks and grandchildren vector leaves, both counted.

## 7. Blast radius

Files: `ia/MlServiceProcessor.java` (runMatchProspects, two helpers),
`ia/IA.java` (entry point, core, markers, dispatch hardening, helper),
`Embedding.java` (helper), `ia/Task.java` (selector), `servlet/IAGateway.java`
(keep `importTaskId` on cleared parent), `StartupWildbook.java` (visibility
of two phase helpers), `shepherd/core/Shepherd.java` (checked commit),
`Annotation.java` / `Encounter.java` (opt-in), `servlet/importer/ImportTask.java`
(opt-in, status aggregate, label merge, SQL helper), `MetricsBot.java`
(gauge text), tests. No schema change, no migration, no frontend change.

## 8. Supersedes

Local branch `fix/match-results-multialgo-parent-task` (unpushed,
2026-07-29): its unconditional unscoped hoist would re-open issue #1744 and
it left the bulk-import page unfixed.

## 9. Codex round-1 dispositions

| # | finding | disposition |
|---|---|---|
| 1 | ancestor-safety proof too broad (extraction task holds annotations) | §4.3: media-asset / `ibeis.detection` stops, `identificationRoot` ceiling on new trees, old-extraction caveat documented |
| 2 | stamp rule not isolation-proof | §4.3: winner-stamp equality, unstamped bridge only with all children stamped, unscoped included; §4.2.6 keeps the stamp on cleared parents |
| 3 | import status counts only the newest task | §4.4 aggregate over all leaves of the invocation, inactivity rule |
| 4 | persistence not atomic | §4.2.3 link + persist before dispatch, §4.2.5 checked commit, §4.1 steps 5 and 7 roll back before marking through another Shepherd |
| 5 | on-demand WBIA registration rewrites acmId | §4.2.4 forced registration first; terminal error on failure |
| 6 | overload ambiguity | §4.2.1 five-argument `intakeAnnotationsUnder`; compatibility guarantee reworded |
| 7 | multi-encounter detection batches | §4.1 step 2 one umbrella per encounter |
| 8 | no-work / queue failure | §4.1 step 6, §4.2.4 `QUEUE_FAILED`, §4.4 skipped class = terminal |
| 9 | deferred / extraction contract | §3 documented exclusions |
| 10 | metrics text | §4.2.2 |
| 11 | class merge ordering, encounter lookup cost | §4.5 SQL pass, alphabetical order, fallback |
| 12 | test plan gaps | §5 |
