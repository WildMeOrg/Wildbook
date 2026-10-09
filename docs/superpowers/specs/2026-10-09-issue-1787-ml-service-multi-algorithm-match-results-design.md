# Issue #1787 — Run every default algorithm on ml-service bulk imports, and link Match Results to the encounter-wide task

Date: 2026-10-09
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
MiewID only. Two independent layers produce that symptom.

### Layer A — the ml-service match step runs only the vector option

`MlServiceProcessor.runMatchProspects` (src/main/java/org/ecocean/ia/
MlServiceProcessor.java) builds one match task and calls
`Embedding.findMatchProspects(matchConfig, matchTask, shep)`. `matchConfig`
comes from `MlServiceProcessor.defaultMatchConfig`, which selects the single
`_id_conf` entry that is both `default: true` and `pipeline_root: "vector"`.
Every other default option is ignored. This step is reached from ml-service
detection (`processDetection`), ml-service extraction (`processExtraction`),
and the deferred re-fire path (`runDeferredMatch`), all through
`waitAndRunMatchInternal`. WBIA detection is no longer supported, so this is
the first-pass identification path for every new bulk import on a
vector-routed species.

### Layer B — the shared selector links to the newest result-owning leaf

Resending an import to identification (`appadmin/resendBulkImportID.jsp` →
`ParallelIdentify.processOne` → `IA.intakeAnnotations`) does run both
options. The task tree for one encounter with a body annotation and a
rostrum annotation is:

```
subParent (no annotations, stamped importTaskId)
└── U  umbrella (body, rostrum)
    ├── T_vec_body (body)   ── SA_body (body)   ← vector leaf, owns MatchResult
    ├── T_hs_body  (body)                       ← HotSpotter task, WBIA logs
    ├── T_vec_rostrum (rostrum) ── SA_rostrum
    └── T_hs_rostrum (rostrum)
```

`Task.selectPreferredMatchTask` returns the newest renderable task holding
the annotation. For the body annotation that is `SA_body` (its parent has
exactly one child), so the link opens a MiewID-only page. `T_hs_body` is
also renderable (issue #1744 single-annotation-branch rule) but older.
The unit test `nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf`
on main pins this as a documented trade-off.

### Evidence (Mantamatcher import 26e2c50c, 2026-10-09 SQL)

| created | tasks | meaning |
|---|---|---|
| 2026-10-08 | 105 HotSpotter completed, 210 "MiewID msv4_v3 (vector)" completed, 55 umbrellas | resend ran BOTH options for all 105 annotations (55 encounters: 50 with body+rostrum, 5 single) |
| any | 0 tasks with `mlServiceV2Match` | this import predates the vector config; no first-pass ml-service match ran |

So on this import the live symptom is Layer B alone. New imports will hit
Layer A first, then Layer B.

## 2. Goals

1. After ml-service detection or extraction, run every `default: true`
   identification option for each annotation, exactly as New Match and
   resend already do.
2. The Match Results link on the bulk-import page and the encounter page
   opens a page that shows every algorithm's results for that annotation's
   encounter.
3. Existing task trees (e.g. the 2026-10-08 resend) show both algorithms
   as soon as the fix deploys, with no resend and no migration.
4. Preserve the issue #1744 fix: an image whose annotations belong to
   different encounters must not link to a page spanning those encounters.
5. Keep the per-import stamp isolation from issue #1624 / PR #1625.

## 3. Non-goals

- Changing which options are default (IA.json content).
- Changing the frontend. `matchResultsStore._processData` already renders
  one section per task node and PR #1746 made Confirm act on the selected
  section's encounter.
- Restructuring `IA.intakeAnnotations`' tree shape.
- Locale keys for `SEND_FAILED` / `PARTIAL` / `INVALID_MATCH_CONFIG`
  (separate issue; see memory note on Mantamatcher "Unknown error").
- The WBIA-detection pipeline (unsupported; untouched).

## 4. Design

### 4.1 Layer A — hand the ml-service match step to the shared intake

`MlServiceProcessor.runMatchProspects(annotationIds, taskId, matchConfig)`
keeps everything before it: the `skipIdent` short-circuit and the
`MatchVisibilityGate` run in `waitAndRunMatchInternal` unchanged, and the
deferred re-fire path still lands here. Inside, replace the single vector
match task with a call to the shared intake:

1. Load the parent task `D` (the ml-service detection/extraction task) and
   the annotations as today. Stale handling (`markTaskDroppedStale`)
   unchanged.
2. Build the inherited parameters from `D`:
   `MlServiceProcessor.matchIntakeParams(JSONObject parentParams)` — a
   pure static helper that returns a copy of `D`'s parameters with
   `ibeis.detection` removed (same reason as today: the detection gauges
   must not count match tasks). `importTaskId`, `matchingSetFilter`,
   `skipIdent` and anything else on `D` are inherited by every intake
   task, which is what stamps the bulk-import scope onto the match tree.
3. Call `IA.intakeAnnotations(shep, anns, inheritedParams, /*fastlane*/ false)`
   (new overload, §4.3). It bins by iaClass, reads `identOpts`, drops
   `default: false`, creates the umbrella and per-option children when
   there is more than one option, runs vector options inline through
   `Embedding.findMatchProspects`, and queues each HotSpotter option to the
   WBIA identify queue via `IAGateway.addToQueue` (same as resend).
4. Link the returned umbrella `U` under `D`: `shep.storeNewTask(U)`,
   `D.addChild(U)`, commit. This mirrors `IBEISIA.processCallback` and
   `ParallelIdentify.processOne`.
5. Empty outcome: if `U` has no children and no `ibeis.identification`
   parameter (no default option for any class), mark `U` and `D` as error
   `INVALID_MATCH_CONFIG` with the message "no default identification
   options" and return `validationError("INVALID_MATCH_CONFIG", ...)`. This
   is the existing outcome code for "nothing usable to match with". A pure
   helper `IA.intakeProducedNoWork(Task topTask)` encodes the check.
6. Exceptions keep the existing `catch` → `markTaskError(taskId, "MATCH", …)`.

Resulting tree for a single-annotation image with two options:

```
D (media asset only; ibeis.detection; importTaskId)
└── U (ann)                 ← intake umbrella; inherits importTaskId
    ├── T_vec (ann) ── SA (ann)   ← vector leaf owns MatchResult; carries mlServiceV2Match
    └── T_hs (ann)               ← HotSpotter via WBIA; MatchResult generated from logs
```

For a species with one vector option only, intake uses `U` itself as the
single task and `findMatchProspects` hangs the leaf under it:
`D → U → SA`, the same depth as today's `D → M → SA`.

`matchConfig` is no longer used to choose the algorithm; intake reads
IA.json. It still feeds the visibility gate and
`hasEmbeddingForMatchConfig`, so the `inferMatchConfig` fallback for
deferred payloads stays.

Metrics: `MetricsBot` counts childless tasks whose parameters contain
`mlServiceV2Match` as vector matches. Today `runMatchProspects` stamps that
flag on `M` and the leaves inherit it. In the new path intake stamps it
(§4.3) on each vector option task before `findMatchProspects` creates the
leaves, so the gauge keeps counting bulk-import vector leaves. Side effect,
intentional: vector leaves from New Match and resend now carry the flag too
and are counted, which makes the gauge "all vector match leaves" instead of
"ml-service first-pass vector match leaves".

Method header: intake stores the IA.json option itself as
`ibeis.identification`. `Task.getIdentificationMethodInfo` returns the
option's `description` for a new-contract vector entry (no `api_endpoint`,
no `query_config_dict`), so the React header reads "MiewID msv4_v3 (vector)"
or "HotSpotter body pattern-matcher" instead of the hard-coded
"ml-service vector matching". `isV2MatchTask` (root-only check) is not
affected: `U` has a parent.

Status: the bulk-import page's identification status aggregates the latest
task per annotation. The HotSpotter task stays "processing" until the WBIA
callback, so the import reads as in progress until both options have
returned. This is the intended meaning of "identification complete" and
matches pre-10.14 behaviour; the existing resend button covers a WBIA that
never answers.

### 4.2 Layer B — hoist the selected task to the encounter-wide ancestor

`Task.selectPreferredMatchTask` gains a third argument:

```java
static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId,
                                     Set<String> encounterAnnotationIds)
```

`encounterAnnotationIds` is the set of annotation ids belonging to the
query annotation's encounter. The existing two-argument method delegates
with `null`, which means "do not hoist" and keeps today's behaviour for any
caller that does not opt in. Both known callers opt in (§4.4).

Selection is unchanged up to the point where a winner exists (renderable
first, then stamp precedence, then root fallback). Then, when the set is
non-null, the winner is hoisted:

```
best = winner
up = winner.parent; hops = 0; visited = {winner.id}
while up != null and up.id != null and hops++ < 20 and visited.add(up.id):
    if up.numberAnnotations() == 0:                       stop   # detection root / New Match root
    if any annotation on up has an id not in encounterAnnotationIds: stop   # #1744: other encounter
    if importTaskId != null and stamp(up) != null and stamp(up) != importTaskId: stop   # #1624 isolation
    best = up
    up = up.parent
return best
```

Wrapped in try/catch that falls back to the best task found so far, like
the hoist on the unpushed branch `fix/match-results-multialgo-parent-task`,
which this design supersedes.

The walk calls `getParent()` and `getObjectAnnotations()` on attached
objects inside the caller's open Shepherd transaction, so DataNucleus lazy
loads each level on demand; the `recursion-depth="3"` on `Task.parent` in
`ia/package.jdo` bounds fetch-plan depth for detached copies, not this
walk. Both call sites (§4.4) run inside a transaction. The hop cap and the
fail-closed catch cover a broken parent row.

Why this is safe to land on a non-"renderable" ancestor: every ancestor
that passes the first two checks holds one or more annotations, all from
the query encounter, and is an identification umbrella by construction.
Every builder that creates tasks above identification umbrellas
(`IA.intakeMediaAssets`, `IA.enqueueOneAssetForMlService`, `IA.handleRest`
root, `IBEISIA.processCallback` and `ParallelIdentify` sub-parents) gives
them media assets or nothing, never annotations, so the climb stops there.
`Task.matchResultsJson` recurses into children, so the ancestor renders a
superset of what the winner rendered.

Behaviour on the known tree shapes:

| tree | before | after |
|---|---|---|
| resend / New Match, one encounter, body + rostrum, two options (the live Mantamatcher tree) | `SA_body` → MiewID only | `U` → four sections: body MiewID, body HotSpotter, rostrum MiewID, rostrum HotSpotter |
| resend / New Match, one annotation, two options | `SA` → MiewID only | `U` → MiewID + HotSpotter |
| ml-service first pass after §4.1 | n/a | `U` → both options |
| ml-service single vector option, `D → M → SA` | `SA` | `M` (content-equivalent: `M` renders `SA`) |
| image with annotations from two encounters, `D → M(annA, annB) → SA, SB` (issue #1744) | `SA` | `SA` (climb stops at `M`, which holds the other encounter's annotation) |
| legacy per-algorithm siblings with results on themselves, `root → umbrella → algo1, algo2` | `umbrella` | `umbrella` (root holds no annotations) |
| scoped: winner is this import's stamped leaf, ancestor stamped with another import | leaf | leaf |

The "Class column collapses" objection that kept the earlier branch from
hoisting the bulk-import path is accepted here on purpose: when several
annotations of one encounter hoist to one umbrella, that encounter gets one
row whose label lists every iaClass (§4.4). The page it links to shows every
annotation's sections, and Confirm already resolves the encounter per
section.

### 4.3 `IA.intakeAnnotations` overload and vector flag

Add
`public static Task intakeAnnotations(Shepherd, List<Annotation>, JSONObject inheritedParams, boolean fastlane)`.
The existing `(Shepherd, List<Annotation>, Task parentTask, boolean)`
overload delegates with `parentTask == null ? null : parentTask.getParameters()`.
Behaviour is byte-for-byte the same for existing callers, with one
subtlety preserved: today each iaClass iteration calls
`parentTask.getParameters()` and gets a fresh JSONObject (parameters are
stored as a string). The overload copies `inheritedParams` per iteration
(`new JSONObject(inheritedParams.toString())`) so classes do not leak
`ibeis.identification` into each other.

Inside the per-option loop, before `Embedding.findMatchProspects(opt, task)`
is called, stamp `task.addParameter("mlServiceV2Match", true)` when
`Embedding.isVectorConfig(opt)` is true. `isVectorConfig` is extracted from
the first lines of `findMatchProspects` (has `method` or `api_endpoint`)
into a public static helper; `findMatchProspects` calls it.

### 4.4 Call sites

- `Encounter.jsonForApiGet` → `Annotation.getPreferredMatchResultsTask`:
  pass the ids of `this.getAnnotations()` (the encounter is in hand; no
  extra query).
- `ImportTask.statsAnnotations`: it already maps each annotation to its
  encounter id via one SQL pass (`annIdToEncId`). For each encounter id,
  load the encounter once (`myShepherd.getEncounter(encId)`, primary-key
  lookup, cached in a local map for the call) and pass the ids of its
  annotations. Annotations the import did not create but that were added
  to the encounter later are therefore part of the allowed set.
  When two annotations of one encounter resolve to the same preferred
  task, `taskTmp` keeps a label of the distinct iaClasses joined with
  ", " in encounter-annotation order instead of the last writer's class.
  `BulkImportTask.jsx` renders whatever label it receives.

## 5. Testing

Unit (JUnit 5, `src/test/java/org/ecocean/ia/TaskSelectPreferredMatchTaskTest.java`,
hand-built trees, no datastore):

- hoist to umbrella, unscoped, one annotation, two options;
- hoist to umbrella, scoped, stamped tree (the live Mantamatcher shape:
  body + rostrum, four option tasks, two vector leaves) — body and rostrum
  both resolve to `U`;
- climb stops at a multi-encounter ancestor (`#1744` tree) — leaf kept;
- climb stops at a zero-annotation ancestor (detection root) — umbrella kept;
- climb stops at a foreign-stamped ancestor;
- null set → no hoist (two-argument overload unchanged);
- cycle guard and hop cap;
- `perAlgorithmSiblingsDoNotQualifyAsFanOut`, `legacyDoIdentifyFanOutSelectsChild`,
  `iaClassPartitionChildIsSelected` updated to pass an encounter set and
  assert the new targets where they change; `nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf`
  is replaced by the scoped Mantamatcher-shape test above.

Unit (new, pure helpers):

- `MlServiceProcessor.matchIntakeParams`: strips `ibeis.detection`, keeps
  `importTaskId`, `matchingSetFilter`, `skipIdent`; null-safe.
- `IA.intakeProducedNoWork`: true for a childless umbrella without
  `ibeis.identification`; false when either is present.
- `Embedding.isVectorConfig`: method / api_endpoint / neither / null.
- `ImportTask` label merge: a small static helper for joining classes.

Not unit-testable here (datastore-bound): `runMatchProspects` orchestration
and `IA.intakeAnnotations` itself. Proof is Codex review of the diff plus a
live check on Mantamatcher after deploy:

1. Open the bulk-import page for import 26e2c50c; every encounter's Match
   Results link opens a page with MiewID and HotSpotter sections for each
   annotation. No resend needed.
2. Run a small new guitarfish bulk import; after identification completes,
   the task tree under each detection task has an umbrella with one vector
   leaf and one HotSpotter task per annotation, and the link shows both.
3. `/metrics` vector-match gauge still moves with vector leaves.

## 6. Risks and mitigations

- HotSpotter on ml-service-created annotations relies on on-demand WBIA
  registration inside `IBEISIA.beginIdentifyAnnotations`. That is how New
  Match and resend already behave on Mantamatcher. A registration failure
  errors that option's task (`SEND_FAILED` since 10.14) and the vector
  option still completes.
- A bulk import now stays "processing" until WBIA answers the HotSpotter
  jobs. Intended; the resend button exists for a dead WBIA.
- Hoisting changes the task id the pages link to for existing single-option
  v2 trees (`SA` → `M`). Content is identical.
- The vector-match gauge now counts New Match and resend vector leaves as
  well. Documented in the PR.
- `Task.getOverallStatus` walks two levels. For the hoisted umbrella `U`,
  its children are option tasks (status set by `findMatchProspects` or WBIA
  logs) and grandchildren are vector leaves — both counted, as on the resend
  tree today.

## 7. Blast radius

Files: `ia/MlServiceProcessor.java` (runMatchProspects + helper),
`ia/IA.java` (overload + vector flag + helper), `Embedding.java` (helper),
`ia/Task.java` (selector), `Annotation.java` / `Encounter.java` (opt-in),
`servlet/importer/ImportTask.java` (opt-in + label merge), tests.
Pages affected: encounter page Match Results button, bulk-import page Class
column links, `/metrics`. No schema change, no migration, no frontend
change.

## 8. Supersedes

Local branch `fix/match-results-multialgo-parent-task` (unpushed,
2026-07-29): its unconditional unscoped hoist would re-open issue #1744 and
it left the bulk-import page unfixed. Its tests for cycle guard and
non-ancestor targets are carried over in spirit.
