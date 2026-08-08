package org.ecocean.ia;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Selection logic behind getPreferredMatchResultsTaskForAnnotation
 * (Task.selectPreferredMatchTask). The driving bug: for v2 ml-service bulk
 * imports the selector returned the detection/umbrella task — stamped with
 * importTaskId but NOT a renderable match task and owning no results — instead
 * of the per-annotation match task (renderable, owns the MatchResult, but
 * unstamped). Root cause was importTaskId pre-filtering candidates before
 * renderability was considered. See
 * docs/plans/2026-06-18-bulk-import-match-task-selection-fix.md.
 */
class TaskSelectPreferredMatchTaskTest {

    // Build a task with the given importTaskId / renderability ingredients.
    // A non-null parent (with this as its only child) plus ibeis.identification
    // makes the task pass isRenderableMatchTask's first clause.
    private static Task task(String importTaskId, boolean ibeisIdent, boolean v2Match,
        Task parent) {
        Task t = new Task();
        if (parent != null) t.setParent(parent);          // also wires parent.addChild(this)
        if (importTaskId != null) t.addParameter("importTaskId", importTaskId);
        if (ibeisIdent) t.addParameter("ibeis.identification", new JSONObject());
        if (v2Match) t.addParameter("mlServiceV2Match", true);
        return t;
    }

    // A renderable match leaf stamped with the given importTaskId (null = unstamped).
    private static Task matchLeaf(String importTaskId) {
        return task(importTaskId, true, false, new Task());
    }

    /**
     * The bug: a stamped-but-resultless detection task must NOT outrank the
     * unstamped renderable match task that owns the result.
     */
    @Test void rendersUnstampedMatchTaskOverStampedDetectionTask() {
        Task detectionParent = new Task();
        // detection leaf: stamped with this import, no ibeis.identification,
        // has a parent and no children -> NOT renderable.
        Task detectionLeaf = task("IMP1", false, false, detectionParent);
        Task match = matchLeaf(null);  // unstamped, renderable

        // created-DESC order as getTasksFor returns it
        List<Task> candidates = Arrays.asList(match, detectionLeaf);
        assertSame(match, Task.selectPreferredMatchTask(candidates, "IMP1"));
    }

    /** A renderable task stamped with THIS import beats a newer unstamped one. */
    @Test void stampedRenderableBeatsNewerUnstamped() {
        Task newerUnstamped = matchLeaf(null);
        Task olderStamped = matchLeaf("IMP1");
        List<Task> candidates = Arrays.asList(newerUnstamped, olderStamped); // newest-first
        assertSame(olderStamped, Task.selectPreferredMatchTask(candidates, "IMP1"));
    }

    /** A v2 match task (parent==null, mlServiceV2Match) is renderable even without children. */
    @Test void v2MatchTaskIsSelected() {
        Task v2 = task(null, false, true, null);
        List<Task> candidates = Collections.singletonList(v2);
        assertSame(v2, Task.selectPreferredMatchTask(candidates, "IMP1"));
    }

    /** Never surface another import's renderable task (foreign-import isolation, #1624). */
    @Test void foreignStampedRenderableIsNotReturned() {
        Task foreign = matchLeaf("IMP2");
        List<Task> candidates = Collections.singletonList(foreign);
        assertNull(Task.selectPreferredMatchTask(candidates, "IMP1"));
    }

    /**
     * Foreign-stamped renderable + this import's unstamped renderable: ignore the
     * foreign task and return the unstamped one (the v2 case).
     */
    @Test void unstampedRenderableWinsOverForeignStamped() {
        Task foreign = matchLeaf("IMP2");      // newest
        Task unstamped = matchLeaf(null);
        List<Task> candidates = Arrays.asList(foreign, unstamped); // newest-first
        assertSame(unstamped, Task.selectPreferredMatchTask(candidates, "IMP1"));
    }

    /**
     * Root fallback, scoped branch: no renderable task, but a root stamped with
     * THIS import must win over a newer unstamped root and a foreign-stamped root.
     */
    @Test void rootFallbackPrefersThisImportsStampedRoot() {
        Task newerUnstampedRoot = task(null, false, false, null);   // not renderable
        Task foreignRoot = task("IMP2", false, false, null);        // not renderable
        Task thisImportRoot = task("IMP1", false, false, null);     // not renderable, older
        // newest-first
        List<Task> candidates = Arrays.asList(newerUnstampedRoot, foreignRoot, thisImportRoot);
        assertSame(thisImportRoot, Task.selectPreferredMatchTask(candidates, "IMP1"));
    }

    /**
     * Legacy import (#1624): no renderable task, an unstamped root -> root
     * fallback returns it (PR #1625 behavior preserved).
     */
    @Test void legacyUnstampedRootFallback() {
        Task root = new Task();  // parent==null, no children, unstamped -> not renderable
        List<Task> candidates = Collections.singletonList(root);
        assertSame(root, Task.selectPreferredMatchTask(candidates, "IMP1"));
    }

    /** Unscoped (importTaskId==null): newest renderable wins. */
    @Test void unscopedReturnsNewestRenderable() {
        Task newer = matchLeaf(null);
        Task older = matchLeaf(null);
        List<Task> candidates = Arrays.asList(newer, older);  // newest-first
        assertSame(newer, Task.selectPreferredMatchTask(candidates, null));
    }

    /** Blank importTaskId is treated as unscoped (no phantom-import filtering). */
    @Test void blankImportTaskIdTreatedAsUnscoped() {
        Task match = matchLeaf(null);
        List<Task> candidates = Collections.singletonList(match);
        assertSame(match, Task.selectPreferredMatchTask(candidates, "   "));
    }

    @Test void emptyAndNullInputsReturnNull() {
        assertNull(Task.selectPreferredMatchTask(null, "IMP1"));
        assertNull(Task.selectPreferredMatchTask(Collections.<Task>emptyList(), "IMP1"));
    }
}
