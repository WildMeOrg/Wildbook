package org.ecocean.ia;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static Set<String> ids(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    // Stamp a task with an import id without touching its other parameters.
    private static Task stamp(Task t, String importTaskId) {
        t.addParameter("importTaskId", importTaskId);
        return t;
    }

    // Live Mantamatcher resend shape (§1 Layer B / §5.1): subParent -> U(body, rostrum) ->
    // {T_vec_body -> SA_body, T_hs_body, T_vec_rostrum -> SA_rostrum, T_hs_rostrum}.
    // Returns {subParent, U, T_vec_body, SA_body, T_hs_body, T_vec_rostrum, SA_rostrum, T_hs_rostrum}.
    private static Task[] mantamatcherTree(String importTaskId, Annotation body, Annotation rostrum) {
        Task subParent = new Task();
        if (importTaskId != null) subParent.addParameter("importTaskId", importTaskId);
        subParent.addParameter("matchingSetFilter", new JSONObject());
        Task u = withAnnots(new Task(subParent), body, rostrum);
        u.setCreated(1000L);
        Task tVecBody = withAnnots(new Task(u), body);
        tVecBody.addParameter("ibeis.identification", new JSONObject("{\"method\":\"miewid\"}"));
        tVecBody.setCreated(1001L);
        Task tHsBody = withAnnots(new Task(u), body);
        tHsBody.addParameter("ibeis.identification", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"));
        tHsBody.setCreated(1002L);
        Task tVecRostrum = withAnnots(new Task(u), rostrum);
        tVecRostrum.addParameter("ibeis.identification", new JSONObject("{\"method\":\"miewid\"}"));
        tVecRostrum.setCreated(1003L);
        Task tHsRostrum = withAnnots(new Task(u), rostrum);
        tHsRostrum.addParameter("ibeis.identification", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"));
        tHsRostrum.setCreated(1004L);
        Task saBody = withAnnots(new Task(tVecBody), body);
        saBody.setCreated(1005L);
        Task saRostrum = withAnnots(new Task(tVecRostrum), rostrum);
        saRostrum.setCreated(1006L);
        return new Task[] { subParent, u, tVecBody, saBody, tHsBody, tVecRostrum, saRostrum, tHsRostrum };
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

    // ---- issue #1787: encounter-scoped selection ----

    /** The live Mantamatcher shape, scoped to the import: both annotations land on the umbrella U. */
    @Test void scopedMantamatcherShapeLandsOnTheUmbrellaForBothAnnotations() {
        Annotation body = annot("body");
        Annotation rostrum = annot("rostrum");
        Task[] t = mantamatcherTree("IMP1", body, rostrum);
        Task u = t[1], saBody = t[3], tHsBody = t[4], tVecBody = t[2];
        Task saRostrum = t[6], tHsRostrum = t[7], tVecRostrum = t[5];
        Set<String> enc = ids("body", "rostrum");
        assertSame(u, Task.selectPreferredMatchTask(
            Arrays.asList(saBody, tHsBody, tVecBody, u), "IMP1", enc));
        assertSame(u, Task.selectPreferredMatchTask(
            Arrays.asList(saRostrum, tHsRostrum, tVecRostrum, u), "IMP1", enc));
    }

    /** A null encounter set is today's behaviour: the newest renderable leaf. */
    @Test void nullEncounterSetKeepsTodaysSelection() {
        Annotation body = annot("body");
        Annotation rostrum = annot("rostrum");
        Task[] t = mantamatcherTree("IMP1", body, rostrum);
        assertSame(t[3], Task.selectPreferredMatchTask(
            Arrays.asList(t[3], t[4], t[2], t[1]), "IMP1", null));
        assertSame(t[3], Task.selectPreferredMatchTask(Arrays.asList(t[3], t[4], t[2], t[1]), "IMP1"));
    }

    /** New Match shape, unscoped: root(media) -> U(A) -> {T_vec -> SA, T_hs}; the root stops the climb. */
    @Test void unscopedOneAnnotationTwoOptionShapeLandsOnTheUmbrella() {
        Annotation a = annot("A");
        Task root = new Task();
        root.addObject(mock(org.ecocean.media.MediaAsset.class));
        Task u = withAnnots(new Task(root), a);
        Task tVec = withAnnots(task(null, true, false, u), a);
        Task tHs = withAnnots(task(null, true, false, u), a);
        Task sa = withAnnots(new Task(tVec), a);
        assertSame(u, Task.selectPreferredMatchTask(Arrays.asList(sa, tHs, tVec, u), null, ids("A")));
    }

    /** #1744 boundary: an ancestor holding an annotation outside the encounter stops the climb. */
    @Test void climbStopsAtAMultiEncounterAncestor() {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task d = new Task();
        d.addParameter("ibeis.detection", true);
        Task m = withAnnots(task(null, true, true, d), a, b);
        Task sa = withAnnots(new Task(m), a);
        withAnnots(new Task(m), b);
        assertSame(sa, Task.selectPreferredMatchTask(Arrays.asList(sa, m), null, ids("A")));
    }

    @Test void climbStopsAtZeroAnnotationMediaAssetDetectionAndExtractionAncestors() {
        Annotation a = annot("A");
        // zero-annotation ancestor
        Task sub = new Task();
        Task u1 = withAnnots(new Task(sub), a);
        Task leaf1 = withAnnots(task(null, true, false, u1), a);
        assertSame(u1, Task.selectPreferredMatchTask(Arrays.asList(leaf1, u1), null, ids("A")));
        // media-asset ancestor
        Task ma = withAnnots(new Task(), a);
        ma.addObject(mock(org.ecocean.media.MediaAsset.class));
        Task u2 = withAnnots(new Task(ma), a);
        Task leaf2 = withAnnots(task(null, true, false, u2), a);
        assertSame(u2, Task.selectPreferredMatchTask(Arrays.asList(leaf2, u2, ma), null, ids("A")));
        // ibeis.detection ancestor
        Task det = withAnnots(new Task(), a);
        det.addParameter("ibeis.detection", true);
        Task u3 = withAnnots(new Task(det), a);
        u3.setParameters(new JSONObject());
        Task leaf3 = withAnnots(task(null, true, false, u3), a);
        assertSame(u3, Task.selectPreferredMatchTask(Arrays.asList(leaf3, u3, det), null, ids("A")));
        // embeddingExtraction ancestor (legacy callback extraction: D -> E -> M_old, M_new)
        Task e = withAnnots(new Task(), a);
        e.addParameter("embeddingExtraction", true);
        Task mOld = withAnnots(new Task(e), a);
        mOld.setParameters(new JSONObject().put("ibeis.identification", new JSONObject()));
        Task mNew = withAnnots(new Task(e), a);
        mNew.setParameters(new JSONObject().put("ibeis.identification", new JSONObject()));
        assertSame(mNew, Task.selectPreferredMatchTask(Arrays.asList(mNew, mOld, e), null, ids("A")));
    }

    /** API extraction: E(annotation, no params, no parent) -> M_old, M_new: the parameterless root stops the climb. */
    @Test void parameterlessRootWithTwoRunsLandsOnTheNewestRun() {
        Annotation a = annot("A");
        Task e = withAnnots(new Task(), a);        // parameters == null, parent == null
        Task mOld = withAnnots(task(null, true, false, e), a);
        Task mNew = withAnnots(task(null, true, false, e), a);
        assertSame(mNew, Task.selectPreferredMatchTask(Arrays.asList(mNew, mOld, e), null, ids("A")));
    }

    /** Stamp matrix for the climb (§4.3): winner stamped IMP1. */
    @Test void stampedWinnerClimbsOnlyThroughSameStampOrBridgingAncestors() {
        Annotation a = annot("A");
        // same stamp: climb
        Task u1 = stamp(withAnnots(new Task(), a), "IMP1");
        Task l1 = withAnnots(task("IMP1", true, false, u1), a);
        assertSame(u1, Task.selectPreferredMatchTask(Arrays.asList(l1, u1), "IMP1", ids("A")));
        // other stamp: stop
        Task u2 = stamp(withAnnots(new Task(), a), "IMP2");
        Task l2 = withAnnots(task("IMP1", true, false, u2), a);
        assertSame(l2, Task.selectPreferredMatchTask(Arrays.asList(l2, u2), "IMP1", ids("A")));
        // unstamped ancestor (under a zero-annotation sub-parent, as a _doIdentify parent is),
        // every direct child stamped IMP1: bridge
        Task u3 = withAnnots(new Task(new Task()), a);
        Task l3 = withAnnots(task("IMP1", true, false, u3), a);
        withAnnots(task("IMP1", true, false, u3), a);
        assertSame(u3, Task.selectPreferredMatchTask(Arrays.asList(l3, u3), "IMP1", ids("A")));
        // unstamped ancestor with a foreign-stamped child: stop
        Task sub4 = new Task();
        new Task(sub4);   // a second child keeps u4 itself from being renderable, so the climb is what is tested
        Task u4 = withAnnots(new Task(sub4), a);
        Task l4 = withAnnots(task("IMP1", true, false, u4), a);
        withAnnots(task("IMP2", true, false, u4), a);
        assertSame(l4, Task.selectPreferredMatchTask(Arrays.asList(l4, u4), "IMP1", ids("A")));
    }

    /** Stamp matrix: winner unstamped. */
    @Test void unstampedWinnerStopsAtAStampedAncestor() {
        Annotation a = annot("A");
        Task stamped = stamp(withAnnots(new Task(), a), "IMP1");
        Task l1 = withAnnots(task(null, true, false, stamped), a);
        assertSame(l1, Task.selectPreferredMatchTask(Arrays.asList(l1, stamped), null, ids("A")));
        Task unstamped = withAnnots(new Task(), a);
        unstamped.setParameters(new JSONObject());   // a root with parameters is not the createFromApi stop
        Task l2 = withAnnots(task(null, true, false, unstamped), a);
        assertSame(unstamped, Task.selectPreferredMatchTask(Arrays.asList(l2, unstamped), null, ids("A")));
    }

    /** _doIdentify fan-out: U(stamped) -> T_hs(params null) -> H_A, H_B(stamped): the bridge rule reaches U. */
    @Test void doIdentifyBridgeReachesTheUmbrella() {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task sub = new Task();
        Task u = stamp(withAnnots(new Task(sub), a, b), "IMP1");
        Task tHs = withAnnots(new Task(u), a, b);
        tHs.setParameters((String)null);
        Task hA = withAnnots(task("IMP1", true, false, tHs), a);
        withAnnots(task("IMP1", true, false, tHs), b);
        assertSame(u, Task.selectPreferredMatchTask(Arrays.asList(hA, tHs, u), "IMP1", ids("A", "B")));
    }

    /** Pre-fix reused root (§4.3 table): phase 1 excludes U1 (media assets); phase 2 seeds H4; climb stops at U1. */
    @Test void preFixReusedRootLandsOnTheInnerUmbrella() {
        Annotation a = annot("A");
        Task r = new Task();
        r.addObject(mock(org.ecocean.media.MediaAsset.class));
        Task u1 = withAnnots(new Task(r), a);
        u1.addObject(mock(org.ecocean.media.MediaAsset.class));
        Task h1 = withAnnots(task(null, true, false, u1), a);
        Task h2 = withAnnots(task(null, true, false, u1), a);
        Task u2 = withAnnots(new Task(u1), a);
        Task h3 = withAnnots(task(null, true, false, u2), a);
        Task h4 = withAnnots(task(null, true, false, u2), a);
        assertSame(u2, Task.selectPreferredMatchTask(Arrays.asList(h4, h3, u2, h2, h1, u1), null, ids("A")));
    }

    /** Only foreign-stamped identification tasks under this import's scope: phases 1 and 2 find nothing; root fallback as today. */
    @Test void foreignOnlyIdentificationTasksFallToTheRootFallback() {
        Annotation a = annot("A");
        Task r = withAnnots(new Task(), a);             // unstamped root, no params
        Task f = withAnnots(task("IMP2", true, false, r), a);
        assertSame(r, Task.selectPreferredMatchTask(Arrays.asList(f, r), "IMP1", ids("A")));
    }

    /** New tree: an eligible umbrella wins outright, even over a newer leaf of an older run. */
    @Test void newTreeReturnsTheRunUmbrella() {
        Annotation a = annot("A");
        Task u1 = TaskSelectRunTest.umbrella(null, 1000L, a);
        Task opt1 = TaskSelectRunTest.under(u1, 1001L, a);
        opt1.addParameter("ibeis.identification", new JSONObject());
        Task u2 = TaskSelectRunTest.umbrella(null, 2000L, a);
        Task opt2 = TaskSelectRunTest.under(u2, 2001L, a);
        opt2.addParameter("ibeis.identification", new JSONObject());
        Task late = TaskSelectRunTest.under(opt1, 3000L, a);
        late.addParameter("ibeis.identification", new JSONObject());
        assertSame(u2, Task.selectPreferredMatchTask(Arrays.asList(late, opt2, u2, opt1, u1), null, ids("A")));
    }

    /** New tree whose newest umbrella spans encounters (§3): today's selection restricted to that run. */
    @Test void mixedEncounterRunFallsBackToTodaysSelectionWithinTheRun() {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = TaskSelectRunTest.umbrella(null, 1000L, a, b);
        Task opt = TaskSelectRunTest.under(u, 1001L, a, b);
        opt.addParameter("ibeis.identification", new JSONObject());
        Task leafA = TaskSelectRunTest.under(opt, 1002L, a);
        leafA.addParameter("ibeis.identification", new JSONObject());
        TaskSelectRunTest.under(opt, 1003L, b).addParameter("ibeis.identification", new JSONObject());
        // an older, unrelated renderable leaf outside the run must not be chosen
        Task oldRoot = withAnnots(new Task(), a);
        Task oldLeaf = withAnnots(task(null, true, false, oldRoot), a);
        oldLeaf.setCreated(10L);
        assertSame(leafA, Task.selectPreferredMatchTask(
            Arrays.asList(leafA, opt, u, oldLeaf, oldRoot), null, ids("A")));
    }

    /** The climb is bounded: a cycle in parent links and more than 20 ancestors both terminate. */
    @Test void climbIsBoundedByVisitedSetAndHopCap() {
        Annotation a = annot("A");
        Task p = withAnnots(new Task(), a);
        Task w = withAnnots(task(null, true, false, p), a);
        p.setParent(w);                      // cycle: p -> w -> p
        assertNotNull(Task.selectPreferredMatchTask(Arrays.asList(w, p), null, ids("A")));
        // 25-deep chain of annotation-holding, unstamped ancestors
        Task[] chain = new Task[26];
        chain[25] = withAnnots(new Task(), a);
        chain[25].setParameters(new JSONObject());
        for (int i = 24; i >= 1; i--) {
            chain[i] = withAnnots(new Task(chain[i + 1]), a);
        }
        chain[0] = withAnnots(task(null, true, false, chain[1]), a);
        assertSame(chain[20], Task.selectPreferredMatchTask(Arrays.asList(chain[0]), null, ids("A")));
    }

    /** A throwing parent link falls back to the best task found so far. */
    @Test void throwingParentFallsBackToBestSoFar() {
        Annotation a = annot("A");
        Task top = withAnnots(new Task(), a);
        Task bad = new Task(top) {
            @Override public Task getParent() { throw new IllegalStateException("boom"); }
        };
        bad.addObject(a);
        Task mid = withAnnots(new Task(bad), a);
        Task w = withAnnots(task(null, true, false, mid), a);
        assertSame(mid, Task.selectPreferredMatchTask(Arrays.asList(w), null, ids("A")));
    }
}
