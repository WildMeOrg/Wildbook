# Issue #1624 — Bulk-import "Class" column empty since 10.2

## Problem

On `/react/bulk-import-task`, the **Class** column should show a Match Results
link per encounter once identification completes. Since the 10.2 update the
links are missing — but **only for imports that existed before 10.2**.
Re-sending the import to ID makes the link reappear. Reported on Whiskerbook
and GiraffeSpotter.

## Root cause

The Class column is fed by `task.iaSummary.statsAnnotations.encounterTaskInfo`,
built in `ImportTask.statsAnnotations()`. For each annotation it selects the
task to link via:

```java
// ImportTask.java:447
Task preferred = Task.getPreferredMatchResultsTaskForAnnotation(ann, this.getId(), myShepherd);
if (preferred != null) { encTasks.get(encId).add(preferred); ... }
```

It passes the **import-task id** as `importTaskId`. Inside the selector
(`Task.getPreferredMatchResultsTaskForAnnotation`, added in commit
`6ae86eda35`, the 10.2-era "C15" work), a non-null `importTaskId` restricts
candidate tasks to those whose `parameters.importTaskId` equals it, and
returned `null` when none matched:

```java
if (importTaskId != null) {
    // keep only tasks whose parameters.importTaskId == importTaskId
    tasks = filtered;
    if (tasks.isEmpty()) return null;   // <- legacy imports land here
}
```

The `importTaskId` task parameter is stamped **only** by the modern bulk-import
path (`BulkImport.initiateIA()` → `taskParams.put("importTaskId", importId)`).
Tasks created by **pre-10.2 imports never carry it**, so the filter empties the
candidate list, the selector returns `null`, `encounterTaskInfo` gets no entry
for that encounter, and the frontend (`BulkImportTask.jsx`, which only renders a
link when the cell is a 3-element array) shows `-`.

This explains the QA notes exactly:
- *Only affects pre-10.2 imports* — only their tasks lack the parameter.
- *Re-sending to ID fixes it* — a re-run goes through `initiateIA()`, which
  stamps `importTaskId` onto the new tasks, so the filter matches again.

## Fix

A single fallback branch in `Task.getPreferredMatchResultsTaskForAnnotation`.
When no task carries this import's id, fall back to the annotation's
**un-stamped** tasks (those with no `importTaskId` at all) instead of returning
`null`:

```java
if (importTaskId != null) {
    List<Task> scoped = new ArrayList<Task>();  // tasks belonging to THIS import
    List<Task> legacy = new ArrayList<Task>();  // tasks with NO importTaskId at all
    for (Task t : tasks) {
        JSONObject p = t.getParameters();
        String tid = (p == null) ? null : p.optString("importTaskId", null);
        if (importTaskId.equals(tid)) scoped.add(t);
        else if (tid == null)         legacy.add(t);
    }
    if (!scoped.isEmpty())      tasks = scoped;
    else if (!legacy.isEmpty()) tasks = legacy;
    else                        return null;
}
```

The existing renderability + roots passes then run unchanged over the chosen
list.

### Why bucket into three, not just "fall back to all tasks"

A naive fallback to the full unfiltered list has a contamination hazard: if an
annotation was re-run under a **different, newer** import (which *does* carry
its own `importTaskId`), the old import's page could link to the newer import's
task. Splitting candidates into `scoped` (this import) / `legacy` (no id) /
*other-import* (silently dropped) prevents that — a legacy import's page never
surfaces another import's results.

## Blast radius

- **Only the bulk-import page is affected.** It is the only caller passing a
  non-null `importTaskId` (`ImportTask.java:447`).
- The **encounter page** calls the 2-arg overload
  (`Annotation.getPreferredMatchResultsTask` → `importTaskId == null`), which
  never enters the filter branch — behavior unchanged.
- No data migration. No schema change. Repairs every affected install at once.

## Behavior changes / caveats (the explicit review focus)

1. **No regression in task selection — strict superset of the prior behavior.**
   Before, when `importTaskId != null` the method kept only exact-match tasks
   (`filtered`) and returned `null` if none. The new code computes the same
   exact-match set as `scoped`:
   - `scoped` non-empty → `tasks = scoped` → **byte-identical** to the old
     `tasks = filtered` and every downstream decision (renderability pass, root
     fallback) is unchanged.
   - `scoped` empty, `legacy` non-empty → returns a legacy task where the old
     code returned `null`. **This is the only behavioral delta — the fix.**
   - `scoped` empty, `legacy` empty → `null`, same as before.

   So for every input the method returns either exactly what it returned before,
   or a legacy task in the specific case that previously yielded `null`. It never
   returns a *different* non-null task. Any imperfect selection on the
   `scoped`-non-empty path (see caveat 2) is pre-existing C15 behavior, not
   introduced here.

2. **The freeze-on-import guarantee was always partial, and this change does not
   extend or weaken it.** The freeze relies on match tasks carrying
   `parameters.importTaskId`. That holds for the ml-service v2 path but **not for
   the legacy WBIA path**: `IBEISIA.processCallback` builds the ID task tree with
   *fresh* parameters (`IBEISIA.java:1504-1516` — `new JSONObject()`,
   `new Task()`, only `matchingSetFilter` added), so WBIA match tasks are
   **un-stamped** regardless of import vintage. Consequently:
   - For un-stamped match tasks (all pre-10.2 imports, and WBIA imports
     generally), `scoped` is empty and the **legacy fallback is exactly what
     renders the link** — this is the mechanism that fixes #1624.
   - For a legacy import the page follows the **newest renderable un-stamped
     task** for the annotation; a later encounter-page re-ID (also un-stamped)
     will be tracked. This equals pre-10.2 behavior — there is no persisted
     discriminator to freeze on.

   Codex's round-1 suggestion to scope the fallback to `ImportTask.iaTask`
   descendants does not help: `iaTask` is set only in the modern flow
   (`IAGateway.java:981`) and pre-10.2 imports have `iaTask == null` (the
   `iaSummaryJson` "legacy flavor" branch is gated on `getIATask() == null`), so
   there is nothing to scope to.

   **Recommended follow-up (separate PR, out of scope here):** propagate
   `importTaskId` into the `taskParameters` built in `IBEISIA.processCallback`
   (line 1504) before `IA.intakeAnnotations`, copying only safe scoping fields
   (never `ibeis.detection`). That would make *future* WBIA match tasks carry the
   id so the freeze holds for them too. It does **not** repair already-persisted
   un-stamped tasks, so the legacy fallback in this PR is still required for
   existing data.

3. **Cross-import isolation against *stamped* tasks.** Tasks stamped with a
   *different* import's id are excluded from both buckets, so a legacy import
   never links to another import's *stamped* task. Note the limit: two distinct
   *un-stamped* (legacy) import runs on the same annotation cannot be told apart
   — there is no persisted discriminator — so the fallback follows the newest
   un-stamped task across them. Isolation is enforced only against stamped tasks
   from other imports.

4. **Modern (stamped) imports are untouched.** Whenever any task matches this
   import's id, the fallback is skipped entirely and behavior is identical to
   before.

5. **Non-renderable legacy roots.** If the legacy tasks are not "renderable" by
   the structural tests, the final `onlyRoots(tasks).get(0)` returns the root of
   the newest *candidate* task (the input list is ordered `created DESC`, and
   `onlyRoots` preserves that order — so this is the root of the newest
   candidate, not necessarily the newest root by `root.created`). This matches
   the pre-existing encounter-page (null-importTaskId) path exactly and is not
   changed here. If such a task happens to have no `MatchResult` records, the
   Match Results page would be empty; this too is pre-existing behavior for the
   encounter page and is not introduced by this change.

6. **Blank `importTaskId`** is normalized to `null` (unscoped) at method entry,
   and a blank `importTaskId` *task parameter* is treated as un-stamped (folded
   into the `legacy` bucket). This is defensive — the real caller always passes
   `ImportTask.getId()` and no task is written with a blank id — but it keeps the
   public helper from matching or scoping on a stray empty string.

## Verification plan

- Pre-10.2 import (tasks lack `importTaskId`): Class column links render again.
- Modern import (tasks carry `importTaskId`): link still frozen on that import's
  task after a subsequent encounter-page re-ID — unchanged from current.
- Annotation shared across a legacy import and a newer modern import: the legacy
  import's page does **not** link to the modern import's task.
- Encounter page (`getPreferredMatchResultsTask`): unchanged.
