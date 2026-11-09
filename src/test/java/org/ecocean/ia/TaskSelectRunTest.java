package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ecocean.Annotation;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** §4.0 run selection: newest eligible umbrella by umbrella creation time. */
class TaskSelectRunTest {

    private static Annotation annot(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        return a;
    }

    private static Set<String> ids(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    /** An umbrella: its own id is its run id; optionally stamped with an import. */
    static Task umbrella(String importTaskId, long created, Annotation... anns) {
        Task u = new Task();
        JSONObject p = new JSONObject();
        p.put(Task.PARAM_RUN_ID, u.getId());
        if (importTaskId != null) p.put(Task.PARAM_IMPORT_TASK_ID, importTaskId);
        u.setParameters(p);
        u.setCreated(created);
        for (Annotation a : anns) u.addObject(a);
        return u;
    }

    /** A child that inherits its parent's parameters (so it carries the parent's run id). */
    static Task under(Task parent, long created, Annotation... anns) {
        Task t = new Task(parent);
        t.setCreated(created);
        for (Annotation a : anns) t.addObject(a);
        return t;
    }

    @Test void runIdHelpersReadTheParameters() {
        Annotation a = annot("A");
        Task u = umbrella(null, 1000L, a);
        Task opt = under(u, 1001L, a);
        opt.addParameter(Task.PARAM_OPTION, true);
        assertTrue(Task.isUmbrella(u));
        assertFalse(Task.isUmbrella(opt), "a child carries the run id but is not the umbrella");
        assertEquals(u.getId(), Task.runIdOf(u));
        assertTrue(Task.isOptionTask(opt));
        assertFalse(Task.isOptionTask(u));
        Task leaf = under(u, 1002L, a);   // carries the run id, no option marker
        assertFalse(Task.isOptionTask(leaf), "a child carrying the run id is not an option without the marker");
        Task single = umbrella(null, 3000L, a);   // single-option umbrella
        single.addParameter(Task.PARAM_OPTION, true);
        assertTrue(Task.isOptionTask(single));
        assertTrue(Task.isUmbrella(single));
        assertTrue(Task.anyRunId(Arrays.asList(new Task(), opt)));
        assertFalse(Task.anyRunId(Arrays.asList(new Task())));
        assertTrue(u.holdsAnnotationId("A"));
        assertFalse(u.holdsAnnotationId("B"));
        assertFalse(u.holdsAnnotationId(null));
    }

    @Test void tasksWithoutParametersAreNeverUmbrellas() {
        Annotation a = annot("A");
        Task bare = new Task();          // parameters == null
        bare.addObject(a);
        assertNull(Task.runIdOf(bare));
        assertFalse(Task.isUmbrella(bare));
        assertFalse(Task.isOptionTask(bare));
        assertNull(Task.selectRun(Arrays.asList(bare), null, ids("A")));
        assertNull(Task.runIdOf(null));
        assertFalse(Task.isUmbrella(null));
    }

    @Test void newestUmbrellaByUmbrellaTimeBeatsLateFanOutOfOlderRun() {
        Annotation a = annot("A");
        Task u1 = umbrella(null, 1000L, a);
        Task opt1 = under(u1, 1001L, a);
        Task u2 = umbrella(null, 2000L, a);
        Task late = under(opt1, 3000L, a);   // HotSpotter fan-out child created after u2
        // created-DESC as getTasksFor returns it
        List<Task> direct = Arrays.asList(late, u2, opt1, u1);
        assertSame(u2, Task.selectRun(direct, null, ids("A")));
    }

    @Test void sortsByCreatedWhenCallerOrderIsNotNewestFirst() {
        Annotation a = annot("A");
        Task u1 = umbrella(null, 1000L, a);
        Task u2 = umbrella(null, 2000L, a);
        assertSame(u2, Task.selectRun(Arrays.asList(u1, u2), null, ids("A")));
    }

    @Test void scopedPrefersThisImportsUmbrellaOverNewerUnstamped() {
        Annotation a = annot("A");
        Task stamped = umbrella("IMP1", 1000L, a);
        Task unstamped = umbrella(null, 2000L, a);
        assertSame(stamped, Task.selectRun(Arrays.asList(unstamped, stamped), "IMP1", ids("A")));
    }

    @Test void scopedFallsBackToNewestUnstampedAndNeverToForeign() {
        Annotation a = annot("A");
        Task foreign = umbrella("IMP2", 3000L, a);
        Task unstamped = umbrella(null, 2000L, a);
        Task olderUnstamped = umbrella(null, 1000L, a);
        assertSame(unstamped, Task.selectRun(Arrays.asList(foreign, unstamped, olderUnstamped),
            "IMP1", ids("A")));
        assertNull(Task.selectRun(Arrays.asList(foreign), "IMP1", ids("A")));
    }

    @Test void unscopedReturnsNewestUmbrellaRegardlessOfStamp() {
        Annotation a = annot("A");
        Task foreign = umbrella("IMP2", 3000L, a);
        Task unstamped = umbrella(null, 2000L, a);
        assertSame(foreign, Task.selectRun(Arrays.asList(foreign, unstamped), null, ids("A")));
        assertSame(foreign, Task.selectRun(Arrays.asList(foreign, unstamped), "  ", ids("A")),
            "a blank importTaskId is unscoped");
    }

    @Test void umbrellaHoldingAnAnnotationOutsideTheEncounterIsIneligible() {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task mixed = umbrella(null, 2000L, a, b);
        Task own = umbrella(null, 1000L, a);
        assertSame(own, Task.selectRun(Arrays.asList(mixed, own), null, ids("A")));
        assertSame(mixed, Task.selectRun(Arrays.asList(mixed, own), null, ids("A", "B")));
        assertNull(Task.selectRun(Arrays.asList(mixed), null, ids("A")));
    }

    @Test void umbrellaWithANullAnnotationListIsEligible() {
        Task bare = umbrella(null, 2000L);   // no addObject: getObjectAnnotations() == null
        assertNull(bare.getObjectAnnotations());
        Task own = umbrella(null, 1000L, annot("A"));
        assertSame(bare, Task.selectRun(Arrays.asList(bare, own), null, ids("A")),
            "no annotation outside the set");
    }

    @Test void equalCreatedTimesKeepCallerOrder() {
        Annotation a = annot("A");
        Task first = umbrella(null, 1000L, a);
        Task second = umbrella(null, 1000L, a);
        assertSame(first, Task.selectRun(Arrays.asList(first, second), null, ids("A")));
        assertSame(second, Task.selectRun(Arrays.asList(second, first), null, ids("A")));
    }

    @Test void nullEncounterSetSkipsTheEncounterCheck() {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task mixed = umbrella(null, 2000L, a, b);
        assertSame(mixed, Task.selectRun(Arrays.asList(mixed), null, null));
    }

    @Test void optionTaskUnderAReusedParentIsNotAnUmbrella() {
        Annotation a = annot("A");
        Task reused = umbrella(null, 1000L, a);
        Task opt = under(reused, 2000L, a);   // run id == reused.id != opt.id
        opt.addParameter(Task.PARAM_OPTION, true);
        assertSame(reused, Task.selectRun(Arrays.asList(opt, reused), null, ids("A")));
        assertNull(Task.selectRun(Arrays.asList(opt), null, ids("A")));
    }

    @Test void emptyInputReturnsNull() {
        assertNull(Task.selectRun(null, null, ids("A")));
        assertNull(Task.selectRun(Arrays.<Task>asList(), "IMP1", ids("A")));
    }
}
