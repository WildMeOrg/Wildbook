# ml-service v2 detection tasks missing from `/metrics` — design

**Date:** 2026-06-02
**Branch:** `fix/mlservice-v2-detection-task-metrics`
**Risk stance:** low-risk, non-blocking, non-critical (metrics-only observability fix)

## Problem

The `/metrics` Prometheus endpoint counts `org.ecocean.ia.Task` objects by **substring-grepping
`Task.parameters`** for magic strings — there is no task-type column. The relevant queries live in
`MetricsBot.addTasksToCsv()` (`src/main/java/org/ecocean/MetricsBot.java`) and a mirror copy in
`src/main/webapp/appadmin/wildbookIAQueueStats.jsp`:

- **Detection** = `parameters.indexOf('ibeis.detection') > -1`
- **Identification** = `parameters.indexOf('ibeis.identification') > -1 || ...'pipeline_root'... || ...'graph'...`

The **legacy** detection path stamps the flag at `IAGateway.java:288`
(`task.addParameter("ibeis.detection", true)`). The **new ml-service v2** detection path
(`IA.enqueueOneAssetForMlService`, `IA.java:281`) does **not** — it puts `mlServiceV2` and friends
into the *queue-job JSON* (`qjob`), never into the Task's `parameters`. Verified by full-repo grep:
the only writer of `ibeis.detection` is the legacy `IAGateway:288`.

### Consequences (today)

1. `wildbook_detection_tasks_added_last24` never counts v2 detection tasks → v2 detection volume is
   invisible in the detection metric.
2. v2 detection tasks still get `completionDateInMilliseconds` set (`markTaskCompleted`), so they land
   in the "all completed" bucket. `wildbook_identification_tasks_completed_last24` is computed as
   `all_completed − detection_completed` (`MetricsBot.java:584`); since v2 detection completions are not
   recognized as detection, they get **misclassified as identification completions** (inflation).

The new vector/MiewID **re-ID** path is *already* counted, because `runMatchProspects` stamps an
`ibeis.identification` block into the match task (`MlServiceProcessor.java:685`) and `new Task(parent)`
copies parameters to the per-annotation leaf subtasks. So this fix is **detection-only**.

## Why the obvious one-liner is wrong

`processDetection` reuses the **same `taskId`** through to the match step (`MlServiceProcessor.java:128`),
so the match task is created as `new Task(parent)` (`MlServiceProcessor.java:667`) — a **child of the
detection task that inherits its parameters** (`Task(Task p)` copies params, `Task.java:54-57`).

If we naively add `ibeis.detection` to the detection task, the match task **and** its per-annotation
subtasks (`Embedding.findMatchProspects` → `new Task(matchTask)`, `Embedding.java:290`) inherit the
flag. That breaks things two ways:
- Detection-added uses an existential child clause — "a child whose params lack `ibeis.detection`". If
  every child inherits the flag, no such child exists → the detection task is **not** counted (fix
  silently does nothing).
- The leaf subtasks would gain `ibeis.detection` and be counted as *detections* while also carrying
  `ibeis.identification` → double-counted / miscategorized.

The legacy path already solves exactly this: `IBEISIA.java:1718` does
`params.remove("ibeis.detection")` when spawning the child ID task.

## Fix (mirror legacy — make v2 data shape-match what the metrics already handle)

Two small, in-memory, non-blocking edits. **Metrics queries are left untouched** (lower risk than
rewriting JDOQL in two places, and avoids affecting legacy counting).

1. **`IA.enqueueOneAssetForMlService`** (`IA.java`, right after `setObjectMediaAssets`): stamp the
   detection task:
   ```java
   childTask.addParameter("ibeis.detection", true);
   ```
   This is the v2 mirror of `IAGateway.java:288`. This is the sole birthplace of v2 per-asset detection
   tasks (callers: `IA.intakeMediaAssetsOneSpeciesMlService` and the `StartupWildbook` reconciler).

2. **`MlServiceProcessor.runMatchProspects`** (right after `Task matchTask = ... new Task(parent)`):
   strip the inherited flag so it does **not** propagate to the match task or its subtasks:
   ```java
   JSONObject mp = matchTask.getParameters();
   if (mp != null && mp.has("ibeis.detection")) {
       mp.remove("ibeis.detection");
       matchTask.setParameters(mp);
   }
   ```
   This is the v2 mirror of `IBEISIA.java:1718`. The strip runs **before** `findMatchProspects` creates
   the subtasks, so subtasks (constructed from `matchTask`) never inherit the flag.

`Task.parameters` is a real `String` field (`getParameters()` parses it, `setParameters(JSONObject)`
re-serializes — `Task.java:419-434`), so this mutate-and-set pattern persists correctly (not the
synthetic-`<property>` footgun).

### Net effect on Task params

- Detection task: **gains** `ibeis.detection` (new).
- Match task + subtasks: **unchanged vs today** — strip removes only the newly-inherited flag, restoring
  the exact param shape they have in production now. So the only behavioral delta in the entire system is
  "the per-asset detection task now carries `ibeis.detection`."

## Scope / what this does and does NOT fix

**This is an added-metric-focused fix.** `wildbook_detection_tasks_added_last24` is the metric we set
out to repair; the completed metric is touched only incidentally and is left as a partial/best-effort
side effect (see below). Codex design review (2026-06-02, verdict GO-WITH-CHANGES, no critical/high
blockers) confirmed the core reasoning and prompted the precise wording in this section.

- ✅ `wildbook_detection_tasks_added_last24` now counts v2 detection tasks (the existential child clause
  is satisfied: detection task has the flag; for tasks that go on to match, the match child does not have
  it after the strip; for tasks with no child yet, `children == null` is satisfied).
- ✅ `wildbookIAQueueStats.jsp` admin page classifies v2 detection tasks correctly (beneficial side
  effect; admin-only display).
- ⚠️ **`*_tasks_added*` counts persisted task rows, not successful queue writes.**
  `enqueueOneAssetForMlService` persists the detection task (`storeNewTask`) *before* the queue write,
  and a queue-write failure leaves a persisted, now-flagged orphan that the startup reconciler retries
  later (`StartupWildbook.java:656`). Such orphans will be counted as detection-added. This is acceptable
  and consistent with the legacy path (which also persists+flags before downstream work) and with the
  plain meaning of "tasks added" = "detection tasks created". Not cleaning up orphans here (scope creep,
  higher risk); a follow-up could unflag/remove failed-enqueue orphans if the count proves noisy.
- ⚠️ **`wildbook_detection_tasks_completed_last24` becomes partial / timing-dependent for v2 — not a
  clean fix, but not regressed either.** It requires `ibeis.detection` *and* leaf status
  (`children == null || children.size() == 0`, `MetricsBot.java:566`). After this change:
  - v2 detection tasks that never spawn a match child — **zero-detection** (`MlServiceProcessor:291`),
    **skip-ident** (`:587`), or sampled **before** the match child is created (detection is marked
    completed at `:484`, the match child is created later at `:667`) — are leaf tasks with the flag and
    **will now be counted** as completed detections (an improvement; they also stop polluting the
    `all_completed − detection_completed` ID-completed figure).
  - v2 detection tasks that have already spawned a match child by snapshot time are **not** counted as
    completed detections — identical to legacy detection→identification chains.
  So the completed figure is partial and snapshot-timing-dependent. Making it exact would require
  deliberately changing the JDOQL/JSP completed semantics for both v2 *and* legacy (higher risk) and is
  **out of scope** for this low-risk fix.
- ⚠️ The residual `all_completed − detection_completed` ID-completed inflation (for detection tasks that
  already have a match child) is pre-existing and shared with legacy; not addressed here.
- ❌ Algorithm-breakdown gauges (`wildbook_tasks_hotspotter/pieTwo/MiewId`) still miss v2 re-ID (they grep
  literal `MiewId`/`PieTwo`/`sv_on`); separate concern, not in scope.

## Safety / blast radius

- Nothing reads `ibeis.detection` except: the metrics JDOQL (target consumer), the admin JSP (target
  consumer), and `IBEISIA.java:1718` (only *removes* it). The `Task.isTypeDetection()` /
  `initiatedWithDetection()` readers are **commented-out dead code** (`Task.java:122-151`). So adding the
  flag changes no control flow.
- Both edits are pure in-memory `JSONObject` ops before existing persistence calls — no new I/O, no new
  transactions, non-blocking.
- No DB migration. No schema change. No frontend change. No config change.
- Worst-case if edit #2 (strip) were wrong: it cannot under-count worse than today (status quo = 0 v2
  detection counted); it is a no-op when the key is absent. It is load-bearing only for *correctness of
  the new counting*, which we verify.

## Test / verification plan

- `javac`/compile sanity on both touched files.
- Confirm LF endings (repo CRLF gotcha).
- Manual trace assertion documented above (no unit-test harness exists for MetricsBot JDOQL; the queries
  run against a live PM). Post-deploy: confirm `wildbook_detection_tasks_added_last24` becomes non-zero on
  a v2 install after a detection run.
