# Fix: bulk-import Class column shows `<iaClass>: {}` and links to a resultless task (v2 ml-service imports)

Issue context: extends PR #1625 (issue #1624). #1624/#1625 restored the Class-column
link for **legacy** imports. This fixes a **second, distinct** failure that affects
**v2 ml-service imports** (the current default path).

## Symptom

On `/react/bulk-import-task`, every encounter's "Class" cell renders
`giraffe_whole: {}` instead of `giraffe_whole: completed`, and the Match Results
link for each row opens a task that shows **no candidates** — even though
identification completed and candidates exist.

## Evidence (live, import `72d4b063…`, 2026-06-18, 176 Giraffa encounters)

- 173/176 annotations have a `MatchResult` with candidates (up to 116) and
  13,582 ranked prospects (scores to 0.94). **Matching succeeded.**
- The API (`/api/v3/bulk-import/<id>` → `iaSummary.statsAnnotations.encounterTaskInfo`)
  returns, for **every** encounter, the identical triple:
  `["0ae9b091…", "{}", "giraffe_whole"]`.
- `0ae9b091…` is the **detection / import umbrella** IA task
  (`ImportTask.IATASK_ID_OID`), a 4-level linear chain:
  `0ae9b091 → fcc5338a → 001e1501 (holds 112 MediaAssets) → 5fd7bb72 (holds 173 annotations)`.
  It owns **zero** MatchResults.
- The real results live in **separate, per-annotation match trees**
  (e.g. `cf7c41ff → 2624fd0b → dd1dce92`), each its own root, **not** descendants
  of `0ae9b091`.

For a sample annotation `d3456e2d`, `getTasksFor(ann)` returns three tasks:

| Task | `importTaskId` stamped | renderable match task | owns MatchResult |
|------|------------------------|------------------------|------------------|
| `5fd7bb72` (detection L3) | **yes** | no (`ibeis.identification` absent, has parent) | 0 |
| `2624fd0b` (match middle) | no | yes | 0 |
| `dd1dce92` (match leaf)   | no | yes | **1** |

## Root cause (two compounding bugs)

`Task.getPreferredMatchResultsTaskForAnnotation(ann, importTaskId, …)`
(`Task.java:592`) selects the task that both the bulk-import page and the
encounter page link to, and on which the bulk-import page computes
`getOverallStatus()`.

1. **`importTaskId` scoping pre-filters before renderability is considered.**
   The method partitions candidates into `scoped` (this import's id) vs `legacy`
   (no id) and, if `scoped` is non-empty, **discards everything else**
   (`if (!scoped.isEmpty()) tasks = scoped;`). In a v2 import the only stamped
   task that carries the annotation is the **detection** task `5fd7bb72`, which
   is **not** a renderable match task and owns no results. So `scoped = [5fd7bb72]`,
   the renderable match tasks (`dd1dce92`, `2624fd0b`) are dropped into `legacy`
   and never consulted, the renderability loop finds nothing, and the method
   returns `onlyRoots(scoped).get(0)` = the detection root `0ae9b091`.

2. **`getOverallStatus()` only walks two levels** (`Task.java:789`, "this should
   only ever be two layers deep"). On `0ae9b091` the depth-2 task it inspects is
   the detection task holding *MediaAssets*; the annotations are at depth 3. The
   `matchAgainst && iaClass != null` filter matches nothing → empty map →
   `resultsMap.toString()` = `"{}"`.

Net: the page links to a resultless tree (no candidates) **and** renders `"{}"`.
Both symptoms collapse to bug #1 — the wrong task is selected. Bug #2 is what
turns that wrong selection into the literal `"{}"` string.

Why PR #1625 doesn't cover this: #1625's `scoped`-wins / `legacy`-fallback logic
targets imports with **no** stamped task at all. Here a stamped *detection* task
exists, so `scoped` is non-empty and wins — cementing the wrong selection.

## Fix (primary): renderability-first selection, `importTaskId` as a tiebreaker

Evaluate renderable match tasks across **all** candidates first; use
`importTaskId` only to choose **among renderable tasks**. A stamped-but-resultless
detection task can then never outrank a real match task.

New precedence in `getPreferredMatchResultsTaskForAnnotation`:

1. Build `renderable` = candidates (created-DESC) where
   `isRenderableMatchTask(t) || isV2MatchTask(t)`.
2. If `renderable` is non-empty:
   - `importTaskId != null`: return the first renderable task stamped with
     `importTaskId`; else the first renderable task with **no** `importTaskId`
     (legacy/unstamped — the v2 case, since match tasks are never stamped);
     else **fall through** (all renderable tasks belong to *other* imports —
     preserve #1624's "never surface another import's results" rule).
   - `importTaskId == null`: return `renderable.get(0)`.
3. No renderable task anywhere → existing root fallback, applying the current
   `scoped`/`legacy` partition over all tasks and returning `onlyRoots(...).get(0)`.
   This preserves PR #1625's legacy-import behavior unchanged.

### Behavior preservation / regression analysis

- **#1624 legacy imports** (no renderable task, no stamped task): unchanged —
  hits step 3, legacy partition, root fallback.
- **Freeze-on-import for v2**: never actually held (match tasks are unstamped),
  so no regression. With the fix the v2 page follows the newest renderable
  unstamped match task — identical to the documented legacy behavior in the
  current javadoc.
- **Foreign-import isolation**: still enforced — step 2 falls through rather than
  returning a renderable task stamped with a *different* import's id.

### Why not "stamp the match tasks with `importTaskId`" instead

That alone does **not** fix it: the detection L3 task is *also* stamped and
carries the annotation, so it stays in `scoped`; renderability-first ordering is
still required to skip it. Stamping match tasks is a separate enhancement (to
make a real freeze-on-import guarantee possible) and is **out of scope** here.

## Fix (secondary, optional): harden `getOverallStatus` depth

`getOverallStatus` should recurse to any depth (and key off tasks that own a
`MatchResult` / pass the annotation filter) rather than assuming exactly two
levels. With the primary fix the selected task is a shallow match task and the
2-level walk already works, so this is **defensive only**. Recommend deferring to
a follow-up unless Codex argues it belongs here.

## Testability

Extract the post-`getTasksFor` selection into a package-private static helper:

```java
static Task selectPreferredMatchTask(List<Task> tasksNewestFirst, String importTaskId)
```

The public method becomes `getTasksFor(...)` → `selectPreferredMatchTask(...)`.
The helper is pure (no DB), so it is unit-testable with hand-built `Task` objects.

### Test plan (`TaskSelectPreferredMatchTaskTest`, JUnit 5, no Testcontainers)

1. **v2 regression (this bug):** candidates = [stamped non-renderable detection
   task, unstamped renderable match leaf] → returns the match leaf, NOT the
   detection task. (Primary assertion.)
2. **#1624 legacy:** candidates = [unstamped non-renderable root only] →
   returns that root (root fallback unchanged).
3. **Foreign import isolation:** the only renderable task is stamped with a
   different importTaskId → does not return it (falls through to root logic).
4. **Scoped wins among renderable:** two renderable match tasks, one stamped with
   this import → returns the stamped one.
5. **Unscoped (`importTaskId == null`):** returns newest renderable.
6. **Empty input:** returns null.

If constructing `Task` parent/children/params in a unit test proves impractical
(getParent/getChildren wiring), fall back to a Testcontainers integration test
mirroring the live tree shape; flagged for Codex's call.

## Codex design review (round 1) — resolutions

- **C1/C4 (explicit ordering):** within the renderable set the precedence is
  exactly `stamped-with-THIS-import > newest-unstamped > (never foreign)`.
  Foreign-stamped renderable tasks are never returned; if only foreign renderable
  tasks exist, fall through to the root fallback. First-class + directly tested.
- **C2 (cross-import leak):** selection never "sorts all renderable by time and
  takes newest" — it takes newest *unstamped*, and a foreign-stamped renderable
  can't win. Test: this-import unstamped v2 task + foreign-stamped renderable →
  returns the unstamped v2 task.
- **C3 (annotation shared across imports — accepted limitation):** v2 match tasks
  are unstamped, so `importTaskId` cannot fully isolate ownership when one
  annotation participates in multiple imports; "newest unstamped renderable" is
  the best available. Same class of limitation PR #1625 already documents for
  legacy imports. Documented; real isolation needs the stamping follow-up.
- **C5 (rerun-from-encounter supersedes):** with unstamped v2 match tasks a later
  rerun's task is newer and will be followed by the bulk-import page. This matches
  the encounter page (unscoped) and the documented legacy behavior, and is
  strictly better than today's behavior (frozen on a resultless detection task →
  `"{}"`). Accepted; true freeze-on-import is the stamping follow-up.
- **C6 (fallback reachability):** root fallback runs only when no usable
  renderable task was selected. Explicit in the algorithm.
- **C9 (`getOverallStatus` depth):** deferred — not causal once the right task is
  selected.

## Files

- `src/main/java/org/ecocean/ia/Task.java` — refactor selector + helper, javadoc.
- `src/test/java/org/ecocean/ia/TaskSelectPreferredMatchTaskTest.java` — new.
- (LF line endings; no reindentation of untouched lines.)
