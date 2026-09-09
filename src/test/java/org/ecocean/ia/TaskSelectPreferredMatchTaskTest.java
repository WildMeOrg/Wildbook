package org.ecocean.ia;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.ecocean.Annotation;
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

    // ---- issue #1744: per-annotation fan-out under a multi-annotation umbrella ----

    private static Annotation annot(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        return a;
    }

    private static Task withAnnots(Task t, Annotation... anns) {
        for (Annotation a : anns) t.addObject(a);
        return t;
    }

    /**
     * v2 ml-service shape (MlServiceProcessor.runMatchProspects +
     * Embedding.findMatchProspects): detection root D -> umbrella M holding EVERY
     * annotation on the image -> one subtask per annotation (inherits params, owns
     * the MatchResult). For annA the newest candidate is SA, but SA's parent has two
     * children, so the pre-fix clause rejected it and the umbrella M — which renders
     * BOTH annotations' results — was selected. Issue #1744.
     */
    @Test void v2FanOutSelectsPerAnnotationSubtaskNotUmbrella() {
        Annotation annA = annot("annA");
        Annotation annB = annot("annB");
        Task d = new Task();
        Task m = withAnnots(task(null, true, true, d), annA, annB);
        Task sa = withAnnots(new Task(m), annA); // new Task(parent) inherits params
        Task sb = withAnnots(new Task(m), annB);
        assertSame(sa, Task.selectPreferredMatchTask(Arrays.asList(sa, m), null));
        assertSame(sb, Task.selectPreferredMatchTask(Arrays.asList(sb, m), null));
    }

    /** Same tree on the bulk-import (scoped) path: the import stamp is inherited down to the subtask. */
    @Test void v2FanOutScopedSelectsStampedSubtask() {
        Annotation annA = annot("annA");
        Annotation annB = annot("annB");
        Task d = task("IMP1", false, false, null);
        Task m = withAnnots(new Task(d), annA, annB);
        m.addParameter("ibeis.identification", new JSONObject());
        Task sa = withAnnots(new Task(m), annA);
        withAnnots(new Task(m), annB);
        assertSame(sa, Task.selectPreferredMatchTask(Arrays.asList(sa, m), "IMP1"));
    }

    /** One annotation on the image: unchanged — the single subtask already won. */
    @Test void singleAnnotationSubtaskStillSelected() {
        Annotation annA = annot("annA");
        Task d = new Task();
        Task m = withAnnots(task(null, true, true, d), annA);
        Task sa = withAnnots(new Task(m), annA);
        assertSame(sa, Task.selectPreferredMatchTask(Arrays.asList(sa, m), null));
    }

    /**
     * Per-ALGORITHM siblings (legacy IA.intakeAnnotations with >1 ident opt) hold the
     * SAME annotation set as their parent, so they are not fan-out children: the
     * umbrella still wins (unchanged behavior).
     */
    @Test void perAlgorithmSiblingsDoNotQualifyAsFanOut() {
        Annotation annA = annot("annA");
        Task root = new Task();
        Task umbrella = withAnnots(task(null, false, false, root), annA);
        Task algo1 = withAnnots(task(null, true, false, umbrella), annA);
        Task algo2 = withAnnots(task(null, true, false, umbrella), annA);
        assertSame(umbrella,
            Task.selectPreferredMatchTask(Arrays.asList(algo2, algo1, umbrella), null));
    }

    /**
     * Legacy WBIA fan-out (IAGateway._doIdentify): the umbrella's params are reset to
     * null and each per-annotation child carries ibeis.identification.
     */
    @Test void legacyDoIdentifyFanOutSelectsChild() {
        Annotation annA = annot("annA");
        Annotation annB = annot("annB");
        Task root = new Task();
        Task umbrella = withAnnots(task(null, false, false, root), annA, annB);
        Task childA = withAnnots(task(null, true, false, umbrella), annA);
        withAnnots(task(null, true, false, umbrella), annB);
        assertSame(childA, Task.selectPreferredMatchTask(Arrays.asList(childA, umbrella), null));
    }

    /** A single-annotation child WITHOUT ibeis.identification (e.g. an embedding-extraction task) is not a match task. */
    @Test void fanOutChildWithoutIdentificationParamIsNotRenderable() {
        Annotation annA = annot("annA");
        Annotation annB = annot("annB");
        Task root = new Task();
        Task umbrella = withAnnots(task(null, true, false, root), annA, annB);
        Task childA = withAnnots(task(null, false, false, umbrella), annA);
        withAnnots(task(null, false, false, umbrella), annB);
        assertSame(umbrella, Task.selectPreferredMatchTask(Arrays.asList(childA, umbrella), null));
    }

    /**
     * Legacy umbrella as an in-memory ROOT: Task.addChild does not set the
     * child's parent (DataNucleus completes the FK from the mapped-by children
     * side on persist), so a freshly built _doIdentify umbrella is rootless.
     * Pre-fix a two-child rootless umbrella was reached only via root fallback;
     * post-fix the per-annotation child wins outright.
     */
    @Test void rootlessLegacyUmbrellaStillYieldsPerAnnotationChild() {
        Annotation annA = annot("annA");
        Annotation annB = annot("annB");
        Task umbrella = withAnnots(new Task(), annA, annB); // parent == null
        Task childA = withAnnots(task(null, true, false, umbrella), annA);
        withAnnots(task(null, true, false, umbrella), annB);
        assertSame(childA, Task.selectPreferredMatchTask(Arrays.asList(childA, umbrella), null));
    }

    /**
     * IA.intakeAnnotations partitions by iaClass: one identified child per
     * class under an umbrella holding every annotation. A single-annotation
     * class branch is selected for its annotation.
     */
    @Test void iaClassPartitionChildIsSelected() {
        Annotation body = annot("body");
        Annotation fin = annot("fin");
        Task root = new Task();
        Task umbrella = withAnnots(task(null, false, false, root), body, fin);
        Task bodyBranch = withAnnots(task(null, true, false, umbrella), body);
        withAnnots(task(null, true, false, umbrella), fin);
        assertSame(bodyBranch,
            Task.selectPreferredMatchTask(Arrays.asList(bodyBranch, umbrella), null));
    }

    /**
     * Documented trade-off: multi-ALGORITHM x multi-annotation. The umbrella
     * fans out per algorithm (each child still holding every annotation) and
     * each algorithm fans out per annotation. There is no per-annotation
     * umbrella, so the newest per-annotation leaf (one algorithm) is selected
     * rather than the image-wide umbrella that previously rendered everything.
     */
    @Test void nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf() {
        Annotation annA = annot("annA");
        Annotation annB = annot("annB");
        Task root = new Task();
        Task umbrella = withAnnots(task(null, false, false, root), annA, annB);
        Task algo1 = withAnnots(task(null, true, false, umbrella), annA, annB);
        Task algo2 = withAnnots(task(null, true, false, umbrella), annA, annB);
        Task algo1A = withAnnots(task(null, true, false, algo1), annA);
        withAnnots(task(null, true, false, algo1), annB);
        Task algo2A = withAnnots(task(null, true, false, algo2), annA);
        withAnnots(task(null, true, false, algo2), annB);
        // created-DESC: newest first
        assertSame(algo2A, Task.selectPreferredMatchTask(
            Arrays.asList(algo2A, algo1A, algo2, algo1, umbrella), null));
    }
}
