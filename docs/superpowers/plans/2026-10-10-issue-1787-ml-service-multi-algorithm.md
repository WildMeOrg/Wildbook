# Issue #1787 — ml-service multi-algorithm match results — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After ml-service detection or extraction, run every `default: true` identification option per encounter, link Match Results to the task that spans all of them, and make the bulk-import identification status count every algorithm.

**Architecture:** Every task of one identification run carries the run's umbrella id (`identificationInvocationId`) and every option task carries `identificationOption`. The ml-service match step hands each encounter's annotations to the shared intake (`IA.intakeAnnotationsUnder`), which builds, persists (checked commit) and then dispatches through a `Dispatcher` seam. The shared selector picks the run's umbrella when it is scoped to an encounter and climbs old trees structurally; the import status aggregates one representative per option. WBIA registration of ml-service annotations is forced under `acmId` on every path that registers them.

**Tech Stack:** Java 11, Maven, DataNucleus JDO on PostgreSQL, JUnit 5 (5.11.0), Mockito 4.11.0 (`mockito-inline`: `mockStatic`, `mockConstruction`), Testcontainers 2.0.3 (`PostgreSQLContainer`, pattern of `MarkedIndividualTaxonomyBackfillDbTest`), org.json.

**Spec:** `docs/superpowers/specs/2026-10-09-issue-1787-ml-service-multi-algorithm-match-results-design.md` (rev 8, Codex converged). Section numbers below (§4.0 … §5.2) refer to it. Review log: `docs/superpowers/specs/2026-10-09-issue-1787-codex-review-log.md`.

**Worktree / branch:** `/mnt/c/Wildbook-multialgo`, branch `fix/bulk-import-multi-algorithm-1787` (off `origin/main` 764268c5aa). Run every command from `/mnt/c/Wildbook-multialgo`.

## Global Constraints

- Java 11 source level; no `var`, no records, no `switch` expressions. Follow the file's existing style (4-space indent, `System.out.println` logging, `Util.stringExists`, `Util.collectionIsEmptyOrNull`).
- No schema change, no migration, no frontend change, no IA.json content change (§3).
- The WBIA-detection pipeline is untouched (§3). Only the ml-service match step gets the checked, link-before-dispatch contract; `ParallelIdentify`, `IBEISIA.processCallback`, `IA.handleRest`, `ProjectIA` keep their unchecked commits and attach-after linking (§3).
- Parameter keys, verbatim: `identificationInvocationId`, `identificationOption`, `mlServiceV2Match`, `importTaskId`, `ibeis.identification`, `ibeis.detection`, `embeddingExtraction`, `matchingSetFilter`, `skipIdent`, `matchingAlgorithms`.
- Status strings, verbatim: `completed`, `error`, `dropped-stale`, `requeued` (new, non-terminal). Error codes, verbatim: `INVALID_MATCH_CONFIG`, `QUEUE_FAILED`, `DISPATCH_FAILED`, `MATCH_PERSIST`, `MATCH`, `SEND_FAILED`.
- `Task.TIMEOUT_INACTIVE_MILLIS` stays 7 days; the climb cap is 20 hops.
- JUnit 5: assertion message is the LAST argument. Run a single test class with `mvn -q test -Dtest=ClassName -DfailIfNoTests=false` (surefire's `argLine` with `--add-opens` is in `pom.xml`; do not override it). Testcontainers tests need Docker (the user is in the docker group).
- LF line endings only (strip `\r` before committing). Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Do not push or open a PR from this plan; that is a separate step after the whole-branch review.
- Codex reviews every task's diff before the next task starts (user instruction). Commit first, then point Codex at the committed files (`codex-review` skill).

## Review Focus

Inputs the spec implies but no §5 case names; each has a test pinned in the owning task.

1. **`matchingAlgorithms` on the inherited parameters** (a New Match where the user picked algorithms): the override must replace the default option list exactly as it does today, and the chosen options still get the run id and option marker. → Task 4 test `matchingAlgorithmsOverrideReplacesDefaults`.
2. **One image, two encounters, only one of which has default options**: the no-option encounter's umbrella is error `INVALID_MATCH_CONFIG`, the other encounter runs normally, and the detection task is NOT marked `INVALID_MATCH_CONFIG`. → Task 11 test `mixedNoWorkGroupsDoNotFailTheDetectionTask`.
3. **Tasks with null parameters** in the annotation's direct list (legacy roots, `_doIdentify` parents): `runIdOf`, `isUmbrella`, `isOptionTask`, `identityParams` and the selector must treat them as unstamped, non-run tasks, never throw. → Task 1 test `tasksWithoutParametersAreNeverUmbrellas`, Task 6 test `identityParamsOfNullIsNull`.
4. **Retry re-entry of `_doIdentify` with one annotation** (the requeued job): no second fan-out, the stored `requeued` status is left for the WBIA callback to overwrite. → Task 6 test `retryReentryWithOneAnnotationDoesNotFanOut`.
5. **`handleRest` reuse of a media-asset-only root** (the hamburger-menu New Match that attaches annotations to the detection root): must NOT be substituted; only a root holding annotations or carrying a run id is. → Task 5 test `mediaAssetOnlyRootIsReused`.

## File Structure

| File | Responsibility in this change |
|---|---|
| `src/main/java/org/ecocean/ia/Task.java` | Run-id vocabulary (`PARAM_*`, `runIdOf`, `isUmbrella`, `isOptionTask`, `anyRunId`, `holdsAnnotationId`, `identityParams`, `withoutOptionMarker`, `STATUS_REQUEUED`), `selectRun`, 3-arg `selectPreferredMatchTask` with old-tree phases and hoist, 4-arg `getPreferredMatchResultsTaskForAnnotation`. |
| `src/main/java/org/ecocean/Embedding.java` | `isVectorConfig`; leaf parameters drop the option marker. |
| `src/main/java/org/ecocean/ia/IA.java` | `IntakeOutcome`, `OptionResult`, `Dispatcher` seam, `intakeAnnotationsUnder`, build/persist/dispatch core, `intakeProducedNoWork`, `resolveRequestRoot` used by `handleRest`. |
| `src/main/java/org/ecocean/IAJsonProperties.java` | Package-private `overrideForTests`. |
| `src/main/java/org/ecocean/MetricsBot.java` | Vector gauge HELP text. |
| `src/main/java/org/ecocean/servlet/IAGateway.java` | `_doIdentify` keeps identity params on the cleared parent, children drop the option marker; failed send stores `requeued`; exhausted requeue stores `error`. |
| `src/main/java/org/ecocean/ia/MlServiceProcessor.java` | `matchIntakeParams`, `groupByEncounter`, `openShepherd` seam, `runMatchProspects` per-group intake with checked commits and recovery. |
| `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java` | `WbiaRegisterRequest.forcedId`, `registerOneByDto` on `forcedId`, `wbiaEligibilityReason`, `registerImageForced`. |
| `src/main/java/org/ecocean/StartupWildbook.java` | Poller eligibility through `wbiaEligibilityReason`. |
| `src/main/java/org/ecocean/identity/IBEISIA.java` | Role-aware `sendAnnotationsAsNeeded(qanns, tanns, shep, report)`, `registerPendingAnnotation`, `iaCheckMissing` forced branch, `beginIdentifyAnnotations` wiring. |
| `src/main/java/org/ecocean/servlet/importer/ImportTask.java` | `encounterIdsToAnnotationIds`, `identificationStatusForAnnotation`, `mergeClassLabel`, `statsAnnotations` opt-in. |
| `src/main/java/org/ecocean/Encounter.java`, `Annotation.java` | Opt-in to the encounter-scoped selector. |
| `src/test/java/org/ecocean/TestIAJsonPropertiesUtil.java` | Test bridge to the package-private IA.json override (pattern of `TestPMFUtil`). |
| Tests | `ia/TaskSelectRunTest`, `ia/TaskSelectPreferredMatchTaskTest` (extended), `EmbeddingTest` (extended), `ia/IAIntakeCoreTest`, `ia/IAHandleRestRootTest`, `servlet/IAGatewayDoIdentifyTest`, `ia/MlServiceProcessorMatchIntakeTest`, `ia/plugin/WildbookIAMForcedIdTest`, `identity/IBEISIASendAnnotationsAsNeededTest`, `servlet/importer/ImportTaskIdentificationStatusTest`, `ia/MultiAlgorithmIntakeDbTest` (Testcontainers). |

Task order is dependency order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12.

---

### Task 1: Run-id vocabulary and `Task.selectRun`

**Files:**
- Modify: `src/main/java/org/ecocean/ia/Task.java` (constants after line 29; helpers next to `importTaskIdOf` ~line 668; `setCreated` next to `getCreatedLong` line 64)
- Test: `src/test/java/org/ecocean/ia/TaskSelectRunTest.java` (new)

**Interfaces:**
- Consumes: nothing new.
- Produces (all `public static` on `Task` unless noted):
  - `String PARAM_RUN_ID = "identificationInvocationId"`, `String PARAM_OPTION = "identificationOption"`, `String PARAM_V2_MATCH = "mlServiceV2Match"`, `String PARAM_IMPORT_TASK_ID = "importTaskId"`, `String STATUS_REQUEUED = "requeued"`.
  - `String runIdOf(Task t)` — the task's run id, or null (null task, null parameters, blank value).
  - `boolean isUmbrella(Task t)` — `t.getId()` equals `runIdOf(t)`.
  - `boolean isOptionTask(Task t)` — parameters carry `identificationOption: true`.
  - `boolean anyRunId(List<Task> tasks)`.
  - `boolean holdsAnnotationId(String annId)` (instance) — membership by annotation id.
  - `Task selectRun(List<Task> direct, String importTaskId, Set<String> encounterAnnotationIds)` — §4.0 run selection.
  - `void setCreated(long millis)` (package-private, instance) — test fixtures only.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/ia/TaskSelectRunTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=TaskSelectRunTest -DfailIfNoTests=false`
Expected: compilation error — `PARAM_RUN_ID`, `setCreated`, `selectRun`, `runIdOf`, `isUmbrella`, `isOptionTask`, `anyRunId`, `holdsAnnotationId` do not exist.

- [ ] **Step 3: Add the vocabulary and `selectRun` to `Task.java`**

Add `import java.util.Comparator;` and `import java.util.HashSet;` to the imports (lines 7-14).

After `public static long TIMEOUT_INACTIVE_MILLIS = ...;` (line 29) add:

```java
    // ---- issue #1787: one identifier for one identification run (spec §4.0) ----
    /** Parameter key: the umbrella id of the identification run every task of that run carries. */
    public static final String PARAM_RUN_ID = "identificationInvocationId";
    /** Parameter key: marks a per-option task, or the umbrella when it is the single option. */
    public static final String PARAM_OPTION = "identificationOption";
    /** Parameter key: vector (ml-service) option tasks and their per-annotation leaves. */
    public static final String PARAM_V2_MATCH = "mlServiceV2Match";
    /** Parameter key: the bulk import that stamped this task (PR #1625). */
    public static final String PARAM_IMPORT_TASK_ID = "importTaskId";
    /** Stored status of a HotSpotter task whose send failed while a retry may still be scheduled (§4.4). Non-terminal. */
    public static final String STATUS_REQUEUED = "requeued";
```

After `getCreatedLong()` (line 64-66) add:

```java
    // test fixtures only: lets a hand-built tree carry distinct creation times
    void setCreated(long millis) {
        created = millis;
    }
```

After `public boolean contains(MediaAsset ma) {...}` (~line 245) add:

```java
    /** Membership by annotation id (Annotation.equals is id-based, but this never depends on it). */
    public boolean holdsAnnotationId(String annId) {
        if ((annId == null) || (objectAnnotations == null)) return false;
        for (Annotation a : objectAnnotations) {
            if ((a != null) && annId.equals(a.getId())) return true;
        }
        return false;
    }
```

Directly after `private static String importTaskIdOf(Task t) {...}` (~line 668) add:

```java
    // ---- issue #1787 run vocabulary ----

    /** The run id a task carries, or null for a task outside any run (null task / parameters / blank). */
    public static String runIdOf(Task t) {
        if (t == null) return null;
        JSONObject p = t.getParameters();
        String rid = (p == null) ? null : p.optString(PARAM_RUN_ID, null);
        if ((rid != null) && rid.trim().isEmpty()) rid = null;
        return rid;
    }

    /** A task is the umbrella of a run iff its own id equals its run id (§4.0). */
    public static boolean isUmbrella(Task t) {
        if ((t == null) || (t.getId() == null)) return false;
        return t.getId().equals(runIdOf(t));
    }

    /** Option tasks carry {@link #PARAM_OPTION}; leaves and fan-out children never do (§4.0). */
    public static boolean isOptionTask(Task t) {
        if (t == null) return false;
        JSONObject p = t.getParameters();
        return (p != null) && p.optBoolean(PARAM_OPTION, false);
    }

    public static boolean anyRunId(List<Task> tasks) {
        if (tasks == null) return false;
        for (Task t : tasks) {
            if (runIdOf(t) != null) return true;
        }
        return false;
    }

    /**
     * Spec §4.0 run selection, shared by the Match Results link and the import status.
     * Returns the newest ELIGIBLE umbrella among the annotation's direct tasks, comparing
     * umbrella creation times (never leaf times, so a late HotSpotter fan-out under an older
     * run cannot outrank a newer run). Eligible: the task is an umbrella; its stamp satisfies
     * the import rule (scoped: this import's stamp first, else unstamped, never foreign;
     * unscoped: any); and, when {@code encounterAnnotationIds} is non-null, every annotation
     * on it is in that set. {@code direct} may be in any order; it is sorted (stably) here.
     */
    public static Task selectRun(List<Task> direct, String importTaskId,
        Set<String> encounterAnnotationIds) {
        if (Util.collectionIsEmptyOrNull(direct)) return null;
        if ((importTaskId != null) && importTaskId.trim().isEmpty()) importTaskId = null;
        List<Task> sorted = new ArrayList<Task>(direct);
        Collections.sort(sorted, new Comparator<Task>() {  // stable: equal times keep caller order
            @Override public int compare(Task a, Task b) {
                return Long.compare(b.getCreatedLong(), a.getCreatedLong());
            }
        });
        Task newestStamped = null;
        Task newestUnstamped = null;
        for (Task t : sorted) {
            if (!isUmbrella(t)) continue;
            if (!allAnnotationsIn(t, encounterAnnotationIds)) continue;
            if (importTaskId == null) return t;   // unscoped: newest eligible umbrella wins
            String tid = importTaskIdOf(t);
            if (importTaskId.equals(tid)) {
                if (newestStamped == null) newestStamped = t;
            } else if ((tid == null) && (newestUnstamped == null)) {
                newestUnstamped = t;
            }
        }
        if (newestStamped != null) return newestStamped;
        return newestUnstamped;
    }

    // every annotation on the task is in the set (a null set means "no encounter check")
    private static boolean allAnnotationsIn(Task t, Set<String> encounterAnnotationIds) {
        if (encounterAnnotationIds == null) return true;
        if (t.getObjectAnnotations() == null) return true;
        for (Annotation a : t.getObjectAnnotations()) {
            if ((a == null) || (a.getId() == null) || !encounterAnnotationIds.contains(a.getId()))
                return false;
        }
        return true;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=TaskSelectRunTest -DfailIfNoTests=false`
Expected: PASS (11 tests). Also run `mvn -q test -Dtest=TaskSelectPreferredMatchTaskTest -DfailIfNoTests=false` — still PASS (nothing changed for it yet).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/ia/Task.java src/test/java/org/ecocean/ia/TaskSelectRunTest.java
git commit -m "Task run-id vocabulary and selectRun for #1787

Every task of one identification run will carry its umbrella's id; a task
is the umbrella iff its own id equals that run id. selectRun returns the
newest eligible umbrella by umbrella creation time under the import stamp
rule and an optional encounter annotation set (spec §4.0).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review of the commit** (codex-review skill; point it at `Task.java` and the new test; ask it to verify the stamp precedence, the stable sort, and the null handling against spec §4.0). Fix findings, re-run the two test classes, amend or add a fix-up commit before Task 2.

---

### Task 2: Encounter-scoped selector with old-tree phases and the structural hoist

**Files:**
- Modify: `src/main/java/org/ecocean/ia/Task.java` (`getPreferredMatchResultsTaskForAnnotation` ~line 567; `selectPreferredMatchTask` lines 613-660; new helpers)
- Test: `src/test/java/org/ecocean/ia/TaskSelectPreferredMatchTaskTest.java` (replace one test, add the §5.1 cases)

**Interfaces:**
- Consumes: Task 1 (`selectRun`, `runIdOf`, `anyRunId`, `importTaskIdOf`, `setCreated`).
- Produces:
  - `static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId, Set<String> encounterAnnotationIds)` (package-private; `null` set = today's behaviour; the 2-arg method delegates with null).
  - `public static Task getPreferredMatchResultsTaskForAnnotation(Annotation ann, String importTaskId, Set<String> encounterAnnotationIds, Shepherd myShepherd)` (the 2- and 3-arg forms delegate with a null set).

- [ ] **Step 1: Write the failing tests**

In `TaskSelectPreferredMatchTaskTest.java`:

1. Add imports: `import java.util.ArrayList; import java.util.HashSet; import java.util.Set; import static org.junit.jupiter.api.Assertions.assertNotNull; import static org.junit.jupiter.api.Assertions.assertTrue;` (keep the existing `org.junit.Assert` static imports).
2. Add a helper after `withAnnots`:

```java
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
```

3. Delete `nestedMultiAlgorithmFanOutSelectsNewestPerAnnotationLeaf` and add, at the end of the class:

```java
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
        Task u4 = withAnnots(new Task(new Task()), a);
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
```

Add `import static org.mockito.Mockito.mock;` for the MediaAsset mocks.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=TaskSelectPreferredMatchTaskTest -DfailIfNoTests=false`
Expected: compilation error — no 3-argument `selectPreferredMatchTask`.

- [ ] **Step 3: Implement the 3-arg selector**

In `Task.java`, replace `getPreferredMatchResultsTaskForAnnotation(Annotation, String, Shepherd)` (lines 567-572) with:

```java
    public static Task getPreferredMatchResultsTaskForAnnotation(Annotation ann,
        String importTaskId, Shepherd myShepherd) {
        return getPreferredMatchResultsTaskForAnnotation(ann, importTaskId, null, myShepherd);
    }

    /**
     * Encounter-scoped variant (issue #1787, spec §4.3): {@code encounterAnnotationIds} is the
     * id set of every annotation on the annotation's encounter. Non-null opts in to run
     * selection and the structural hoist; null keeps today's behaviour.
     */
    public static Task getPreferredMatchResultsTaskForAnnotation(Annotation ann,
        String importTaskId, Set<String> encounterAnnotationIds, Shepherd myShepherd) {
        List<Task> tasks = getTasksFor(ann, myShepherd, "created DESC");

        return selectPreferredMatchTask(tasks, importTaskId, encounterAnnotationIds);
    }
```

Rename today's `selectPreferredMatchTask(List<Task>, String)` body: keep its javadoc, but change the method to

```java
    static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId) {
        return selectPreferredMatchTask(tasks, importTaskId, null);
    }

    /**
     * Issue #1787 (spec §4.3). With a null {@code encounterAnnotationIds} this is exactly the
     * two-argument behaviour above. With a set:
     * <ol>
     *   <li>new tree: the run's umbrella from {@link #selectRun}, scoped to the encounter;</li>
     *   <li>new tree whose newest umbrella spans encounters: today's selection restricted to
     *       that run's tasks (no hoist);</li>
     *   <li>old tree: three ordered phases (renderable candidates minus orchestration nodes
     *       under the import partition; else the newest {@code ibeis.identification} task under
     *       the same partition; else today's root fallback, returned as is), then a structural
     *       climb from the phase-1/2 winner with explicit stop rules.</li>
     * </ol>
     */
    static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId,
        Set<String> encounterAnnotationIds) {
        if (Util.collectionIsEmptyOrNull(tasks)) return null;
        if ((importTaskId != null) && importTaskId.trim().isEmpty()) importTaskId = null;
        if (encounterAnnotationIds == null) return selectLegacy(tasks, importTaskId);
        Task run = selectRun(tasks, importTaskId, encounterAnnotationIds);
        if (run != null) return run;
        Task newestUmbrella = selectRun(tasks, importTaskId, null);
        if (newestUmbrella != null) {
            List<Task> ofRun = new ArrayList<Task>();
            for (Task t : tasks) {
                if (newestUmbrella.getId().equals(runIdOf(t))) ofRun.add(t);
            }
            return selectLegacy(ofRun, importTaskId);
        }
        List<Task> oldTree = new ArrayList<Task>();
        for (Task t : tasks) {
            if (runIdOf(t) == null) oldTree.add(t);
        }
        if (oldTree.isEmpty()) return selectLegacy(tasks, importTaskId);  // only foreign-stamped runs
        return selectOldTree(oldTree, importTaskId, encounterAnnotationIds);
    }

    // Today's two-argument behaviour, verbatim (renderable phase, then root fallback).
    private static Task selectLegacy(List<Task> tasks, String importTaskId) {
        if (Util.collectionIsEmptyOrNull(tasks)) return null;
        List<Task> renderable = new ArrayList<Task>();
        for (Task t : tasks) {
            if (isRenderableMatchTask(t) || isV2MatchTask(t)) renderable.add(t);
        }
        if (!renderable.isEmpty()) {
            if (importTaskId == null) return renderable.get(0);
            Task newestUnstamped = null;
            for (Task t : renderable) {  // newest-first
                String tid = importTaskIdOf(t);
                if (importTaskId.equals(tid)) return t;
                if ((tid == null) && (newestUnstamped == null)) newestUnstamped = t;
            }
            if (newestUnstamped != null) return newestUnstamped;
        }
        return rootFallback(tasks, importTaskId);
    }

    // PR #1625's scoped/legacy root partition, verbatim from the previous selector body.
    private static Task rootFallback(List<Task> tasks, String importTaskId) {
        List<Task> pool = tasks;
        if (importTaskId != null) {
            List<Task> scoped = new ArrayList<Task>();
            List<Task> legacy = new ArrayList<Task>();
            for (Task t : tasks) {
                String tid = importTaskIdOf(t);
                if (importTaskId.equals(tid)) {
                    scoped.add(t);
                } else if (tid == null) {
                    legacy.add(t);
                }
            }
            if (!scoped.isEmpty()) {
                pool = scoped;
            } else if (!legacy.isEmpty()) {
                pool = legacy;
            } else {
                return null;
            }
        }
        List<Task> roots = onlyRoots(pool);
        if (!roots.isEmpty()) return roots.get(0);
        return null;
    }

    // §4.3 old tree: phases 1-3, then the climb.
    private static Task selectOldTree(List<Task> tasks, String importTaskId,
        Set<String> encounterAnnotationIds) {
        List<Task> phase1 = new ArrayList<Task>();
        for (Task t : tasks) {
            if ((isRenderableMatchTask(t) || isV2MatchTask(t)) && !isOrchestrationNode(t))
                phase1.add(t);
        }
        Task winner = newestInPartition(phase1, importTaskId);
        if (winner == null) {
            List<Task> phase2 = new ArrayList<Task>();
            for (Task t : tasks) {
                JSONObject p = t.getParameters();
                if ((p != null) && p.has("ibeis.identification") && !isOrchestrationNode(t))
                    phase2.add(t);
            }
            winner = newestInPartition(phase2, importTaskId);
        }
        if (winner == null) return rootFallback(tasks, importTaskId);  // phase 3: as is, never climbed
        return hoistToRunRoot(winner, encounterAnnotationIds);
    }

    // candidates are newest-first; scoped: this import's stamp, else unstamped, never foreign
    private static Task newestInPartition(List<Task> candidates, String importTaskId) {
        if (candidates.isEmpty()) return null;
        if (importTaskId == null) return candidates.get(0);
        Task newestUnstamped = null;
        for (Task t : candidates) {
            String tid = importTaskIdOf(t);
            if (importTaskId.equals(tid)) return t;
            if ((tid == null) && (newestUnstamped == null)) newestUnstamped = t;
        }
        return newestUnstamped;
    }

    // detection / extraction orchestration, or a pre-fix umbrella a later handleRest loaded with media assets
    private static boolean isOrchestrationNode(Task t) {
        if (t.numberMediaAssets() > 0) return true;
        JSONObject p = t.getParameters();
        return (p != null) && (p.has("ibeis.detection") || p.has("embeddingExtraction"));
    }

    private static final int HOIST_MAX_HOPS = 20;

    // §4.3 structural climb with the stop rules; falls back to the best task found so far on any throw
    private static Task hoistToRunRoot(Task winner, Set<String> encounterAnnotationIds) {
        Task best = winner;
        try {
            String winnerStamp = importTaskIdOf(winner);
            Set<String> visited = new HashSet<String>();
            visited.add(winner.getId());
            Task up = winner.getParent();
            int hops = 0;
            while ((up != null) && (up.getId() != null) && (hops++ < HOIST_MAX_HOPS) && visited.add(up.getId())) {
                if (up.numberAnnotations() == 0) break;                                   // roots, sub-parents
                if (isOrchestrationNode(up)) break;                                       // detection / extraction
                if ((up.getParent() == null) && (up.getParameters() == null)) break;       // parameterless root (createFromApi E)
                if (!allAnnotationsIn(up, encounterAnnotationIds)) break;                 // #1744 boundary
                String upStamp = importTaskIdOf(up);
                if ((upStamp != null) && !upStamp.equals(winnerStamp)) break;             // #1624 boundary
                if ((upStamp == null) && (winnerStamp != null) && !allChildrenStamped(up, winnerStamp)) break;
                best = up;
                up = up.getParent();
            }
        } catch (RuntimeException ex) {
            System.out.println("WARNING: Task.hoistToRunRoot() stopped at " + best.getId() + " due to " + ex);
        }
        return best;
    }

    // unstamped bridge: every direct child carries the winner's stamp (an empty child list never bridges)
    private static boolean allChildrenStamped(Task up, String stamp) {
        List<Task> kids = up.getChildren();
        if ((kids == null) || kids.isEmpty()) return false;
        for (Task kid : kids) {
            if (!stamp.equals(importTaskIdOf(kid))) return false;
        }
        return true;
    }
```

Delete the old body of the 2-arg method (lines 614-660) since `selectLegacy` and `rootFallback` now hold it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q test -Dtest='TaskSelectPreferredMatchTaskTest,TaskSelectRunTest' -DfailIfNoTests=false`
Expected: PASS. Every pre-existing test in the class still passes unchanged (they use the 2-arg method).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/ia/Task.java src/test/java/org/ecocean/ia/TaskSelectPreferredMatchTaskTest.java
git commit -m "Link Match Results to the encounter-scoped run for #1787

selectPreferredMatchTask gains an encounter annotation set. New trees land
on the run's umbrella; old trees are selected in three ordered phases and
hoisted structurally with explicit stops for orchestration nodes, foreign
stamps, the #1744 encounter boundary and parameterless roots (spec §4.3).
A null set keeps today's behaviour.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** of `Task.java` + the test against spec §4.3 (phases, stop rules, bridge rule, the §4.3 shape table). Fix, re-run, commit before Task 3.

---

### Task 3: `Embedding.isVectorConfig` and leaf parameters without the option marker

**Files:**
- Modify: `src/main/java/org/ecocean/Embedding.java` (lines 277-303)
- Test: `src/test/java/org/ecocean/EmbeddingTest.java` (add cases)

**Interfaces:**
- Consumes: `Task.PARAM_OPTION`, `Task.PARAM_RUN_ID`, `Task.PARAM_V2_MATCH` (Task 1).
- Produces: `public static boolean isVectorConfig(JSONObject iaConfig)` — true when the config has a `method` or an `api_endpoint` (the gate `findMatchProspects` already applies). `findMatchProspects` strips `identificationOption` from every leaf it creates.

- [ ] **Step 1: Write the failing tests**

Append to `EmbeddingTest.java`. The file's assertions come from `import static org.junit.Assert.*;` (JUnit 4, message FIRST, no `(boolean, String)` overload), so the new tests call `org.junit.jupiter.api.Assertions` explicitly. Add imports: `import org.junit.jupiter.api.Assertions; import org.ecocean.shepherd.core.Shepherd; import javax.jdo.PersistenceManager; import static org.mockito.ArgumentMatchers.any; import static org.mockito.ArgumentMatchers.anyBoolean; import static org.mockito.Mockito.mock; import static org.mockito.Mockito.when;` (`org.ecocean.ia.Task` is already imported):

```java
    // ---- issue #1787 ----

    @Test void isVectorConfig_acceptsMethodOrApiEndpointOnly() {
        Assertions.assertFalse(Embedding.isVectorConfig(null));
        Assertions.assertFalse(Embedding.isVectorConfig(new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}")));
        Assertions.assertTrue(Embedding.isVectorConfig(new JSONObject("{\"method\":\"miewid\",\"version\":\"msv4_v3\"}")));
        Assertions.assertTrue(Embedding.isVectorConfig(new JSONObject("{\"api_endpoint\":\"http://x\"}")));
        Assertions.assertFalse(Embedding.isVectorConfig(new JSONObject("{\"method\":\"\"}")), "blank method is not vector");
    }

    /** Leaves inherit the option task's parameters minus the option marker (spec §4.0). */
    @Test void findMatchProspects_leavesDropTheOptionMarkerButKeepRunIdAndV2Flag() {
        PersistenceManager pm = mock(PersistenceManager.class);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);
        Annotation ann = mock(Annotation.class);
        when(ann.numberEmbeddings()).thenReturn(1);
        when(ann.getMatchingSetQuery(any(Shepherd.class), any(), anyBoolean())).thenReturn(null); // error leaf, no network

        Task option = new Task();
        JSONObject p = new JSONObject();
        p.put(Task.PARAM_RUN_ID, "RUN-1");
        p.put(Task.PARAM_OPTION, true);
        p.put(Task.PARAM_V2_MATCH, true);
        p.put("ibeis.identification", new JSONObject("{\"method\":\"miewid\"}"));
        option.setParameters(p);
        option.addObject(ann);

        Assertions.assertTrue(Embedding.findMatchProspects(new JSONObject("{\"method\":\"miewid\",\"version\":\"v\"}"), option, sh));
        Assertions.assertEquals(1, option.getChildren().size());
        JSONObject leaf = option.getChildren().get(0).getParameters();
        Assertions.assertFalse(leaf.has(Task.PARAM_OPTION), "a leaf is not an option");
        Assertions.assertEquals("RUN-1", leaf.getString(Task.PARAM_RUN_ID));
        Assertions.assertTrue(leaf.getBoolean(Task.PARAM_V2_MATCH));
        Assertions.assertTrue(leaf.has("ibeis.identification"));
        Assertions.assertTrue(option.getParameters().getBoolean(Task.PARAM_OPTION), "the option keeps its marker");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=EmbeddingTest -DfailIfNoTests=false`
Expected: compilation error — `isVectorConfig` undefined.

- [ ] **Step 3: Implement**

In `Embedding.java`, replace lines 277-284 (`findMatchProspects` head through `if (!isVectorConfig) return false;`) with:

```java
    /**
     * The new _id_conf contract (entries with method/version/pipeline_root) and legacy entries
     * (api_endpoint) are both vector configs; everything else (HotSpotter etc.) is not. Pure.
     */
    public static boolean isVectorConfig(JSONObject iaConfig) {
        if (iaConfig == null) return false;
        return Util.stringExists(iaConfig.optString("method", null))
            || Util.stringExists(iaConfig.optString("api_endpoint", null));
    }

    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
        // Migration plan v2 §commit #3: gate accepts the new _id_conf
        // contract (entries with `method`/`version`/`pipeline_root` and no
        // api_endpoint) as well as legacy entries (with `api_endpoint`).
        if (!isVectorConfig(iaConfig)) return false;
```

and directly after `Task subTask = new Task(task);` (line 302) add:

```java
            // issue #1787: new Task(task) copies the option's parameters; a leaf is not an
            // option, so drop the marker (the run id and mlServiceV2Match stay).
            JSONObject leafParams = subTask.getParameters();
            if ((leafParams != null) && leafParams.has(Task.PARAM_OPTION)) {
                leafParams.remove(Task.PARAM_OPTION);
                subTask.setParameters(leafParams);
            }
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=EmbeddingTest -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/Embedding.java src/test/java/org/ecocean/EmbeddingTest.java
git commit -m "Extract Embedding.isVectorConfig; vector leaves drop the option marker (#1787)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** (small diff; confirm the gate is unchanged for every existing caller).

---

### Task 4: Intake core — outcome, dispatcher seam, build → persist → dispatch, markers, `noWork`

**Files:**
- Modify: `src/main/java/org/ecocean/ia/IA.java` (lines 373-509: `intakeAnnotations` 4-arg and its body)
- Modify: `src/main/java/org/ecocean/MetricsBot.java` (lines 545-559: vector gauge comment and HELP text)
- Test: `src/test/java/org/ecocean/ia/IAIntakeCoreTest.java` (new)

**Interfaces:**
- Consumes: `Task.PARAM_*`, `Task.isUmbrella` (Task 1); `Embedding.isVectorConfig` (Task 3); `Shepherd.commitDBTransactionWithStatus()` (exists); `IAGateway.addToQueue` / `addToDetectionQueue` (exist).
- Produces (in `IA`):
  - `public static final class OptionResult { public final Task task; public final boolean vector; public boolean published; public String failureCode; }`
  - `public static final class IntakeOutcome { public Task umbrella; public final List<OptionResult> options; public final List<String> skippedIaClasses; public boolean noWork; public boolean persistFailed; }`
  - `interface Dispatcher { void runVector(JSONObject opt, Task optionTask, Shepherd shep); boolean publish(String context, String job, boolean fastlane) throws IOException; }` (package-private) and `static void setDispatcherForTests(Dispatcher d)` (package-private; null restores the real one).
  - `public static IntakeOutcome intakeAnnotationsUnder(Shepherd, List<Annotation>, Task parent, JSONObject inheritedParams, boolean fastlane)` — returns null only for an empty annotation list.
  - `public static Task intakeAnnotations(Shepherd, List<Annotation>, Task parentTask, boolean fastlane)` — unchanged signature; now returns `outcome.umbrella`, or null when `persistFailed`.
  - `public static boolean intakeProducedNoWork(Task umbrella)` — no children and no `ibeis.identification`.
  - `static JSONObject scrubRunParams(JSONObject inherited)` (package-private, pure, never null) — a copy without `identificationInvocationId`, `mlServiceV2Match`, `identificationOption`, `ibeis.identification`; also applied at the media-intake boundary (`intakeMediaAssetsOneSpecies`).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/ia/IAIntakeCoreTest.java`:

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jdo.PersistenceManager;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.IAJsonProperties;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

/**
 * Spec §4.2: the intake core builds, persists (checked commit), then dispatches through the
 * Dispatcher seam. No OpenSearch, ml-service or file queue is touched.
 */
class IAIntakeCoreTest {

    /** Records dispatches; builds vector leaves the way Embedding.findMatchProspects does. */
    static final class RecordingDispatcher implements IA.Dispatcher {
        final List<Task> vectorRuns = new ArrayList<Task>();
        final List<JSONObject> published = new ArrayList<JSONObject>();
        boolean publishResult = true;
        boolean publishThrows = false;
        String vectorThrowsForIaClass = null;
        boolean attachLeafBeforeThrow = false;

        @Override public void runVector(JSONObject opt, Task optionTask, Shepherd shep) {
            vectorRuns.add(optionTask);
            String iaClass = optionTask.getObjectAnnotations().get(0).getIAClass();
            if (iaClass.equals(vectorThrowsForIaClass)) {
                if (attachLeafBeforeThrow) {
                    Task leaf = new Task(optionTask);
                    leaf.addObject(optionTask.getObjectAnnotations().get(0));
                }
                throw new IllegalStateException("vector dispatch failed for " + iaClass);
            }
            for (Annotation a : optionTask.getObjectAnnotations()) {
                Task leaf = new Task(optionTask);
                leaf.addObject(a);
                leaf.setStatus("completed");
            }
            optionTask.setStatus("completed");
        }

        @Override public boolean publish(String context, String job, boolean fastlane)
        throws java.io.IOException {
            if (publishThrows) throw new java.io.IOException("queue unwritable");
            published.add(new JSONObject(job));
            return publishResult;
        }
    }

    @AfterEach void resetDispatcher() {
        IA.setDispatcherForTests(null);
    }

    static Shepherd shepherd(boolean commitOk) {
        PersistenceManager pm = mock(PersistenceManager.class);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);
        when(sh.getContext()).thenReturn("context0");
        when(sh.commitDBTransactionWithStatus()).thenReturn(commitOk);
        return sh;
    }

    static Annotation ann(String id, String iaClass) {
        Annotation a = new Annotation();
        a.setId(id);
        a.setIAClass(iaClass);
        return a;
    }

    static JSONObject hotspotter() {
        return new JSONObject(
            "{\"query_config_dict\":{\"sv_on\":true},\"default\":true,\"description\":\"HotSpotter\"}");
    }

    static JSONObject miewid() {
        return new JSONObject(
            "{\"pipeline_root\":\"vector\",\"method\":\"miewid\",\"version\":\"msv4_v3\",\"default\":true,\"description\":\"MiewID\"}");
    }

    /** identOpts answered per annotation iaClass (fresh copies each call, as the real one does). */
    static IAJsonProperties config(final Map<String, List<JSONObject>> optsByClass) {
        IAJsonProperties cfg = mock(IAJsonProperties.class);
        when(cfg.identOpts(any(Shepherd.class), any(Annotation.class))).thenAnswer(inv -> {
            Annotation a = inv.getArgument(1);
            List<JSONObject> src = optsByClass.get(a.getIAClass());
            if (src == null) return null;
            List<JSONObject> copy = new ArrayList<JSONObject>();
            for (JSONObject o : src) copy.add(new JSONObject(o.toString()));
            return copy;
        });
        return cfg;
    }

    static Map<String, List<JSONObject>> twoClassesTwoOptions() {
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        m.put("guitarfish+rostrum", Arrays.asList(miewid(), hotspotter()));
        return m;
    }

    interface Body { void run() throws Exception; }

    static void withStatics(IAJsonProperties cfg, Body body) throws Exception {
        try (MockedStatic<CommonConfiguration> cc = mockStatic(CommonConfiguration.class);
             MockedStatic<IAJsonProperties> jp = mockStatic(IAJsonProperties.class)) {
            cc.when(() -> CommonConfiguration.getServerURL(anyString())).thenReturn("http://wb");
            jp.when(IAJsonProperties::iaConfig).thenReturn(cfg);
            body.run();
        }
    }

    static String errorCode(Task t) {
        JSONObject sd = t.getStatusDetails();
        if ((sd == null) || (sd.optJSONArray("errors") == null)) return null;
        JSONArray errs = sd.getJSONArray("errors");
        return errs.getJSONObject(errs.length() - 1).optString("code", null);
    }

    @Test void twoClassesTwoOptionsBuildOneOptionTaskPerClassAndOption() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Annotation body = ann("body", "guitarfish");
        Annotation rostrum = ann("rostrum", "guitarfish+rostrum");
        withStatics(config(twoClassesTwoOptions()), () -> {
            JSONObject inherited = new JSONObject().put("importTaskId", "IMP1")
                .put("matchingSetFilter", new JSONObject().put("locationIds", new JSONArray().put("X")));
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(body, rostrum), null, inherited, false);

            Task u = o.umbrella;
            assertNotNull(u);
            assertTrue(Task.isUmbrella(u));
            assertFalse(Task.isOptionTask(u));
            assertFalse(u.getParameters().has(Task.PARAM_V2_MATCH));
            assertEquals("IMP1", u.getParameters().getString("importTaskId"));
            assertEquals(2, u.numberAnnotations());
            assertFalse(o.noWork);
            assertFalse(o.persistFailed);
            assertEquals(4, o.options.size());
            assertEquals(4, u.getChildren().size());
            int vectors = 0;
            for (IA.OptionResult r : o.options) {
                Task t = r.task;
                assertEquals(u.getId(), Task.runIdOf(t));
                assertTrue(Task.isOptionTask(t));
                assertFalse(Task.isUmbrella(t));
                assertEquals("IMP1", t.getParameters().getString("importTaskId"));
                assertTrue(t.getParameters().has("matchingSetFilter"));
                assertEquals(1, t.numberAnnotations(), "one class per option task");
                JSONObject ident = t.getParameters().getJSONObject("ibeis.identification");
                if (r.vector) {
                    vectors++;
                    assertEquals("miewid", ident.getString("method"));
                    assertTrue(t.getParameters().getBoolean(Task.PARAM_V2_MATCH));
                } else {
                    assertTrue(ident.has("query_config_dict"));
                    assertFalse(t.getParameters().has(Task.PARAM_V2_MATCH), "HotSpotter never carries the v2 flag");
                }
                assertTrue(r.published);
                assertNull(r.failureCode);
            }
            assertEquals(2, vectors);
            assertEquals(2, d.vectorRuns.size());
            assertEquals(2, d.published.size());
            for (JSONObject job : d.published) {
                String tid = job.getString("taskId");
                Task opt = null;
                for (IA.OptionResult r : o.options) if (r.task.getId().equals(tid)) opt = r.task;
                assertNotNull(opt, "published job names an option task");
                assertEquals(opt.getObjectAnnotations().get(0).getId(),
                    job.getJSONObject("identify").getJSONArray("annotationIds").getString(0));
                assertEquals("context0", job.getString("__context"));
                assertEquals(job.toString(), opt.getQueueResumeMessage());
            }
        });
    }

    @Test void inheritedRunIdAndMarkersAreReplaced() throws Exception {
        IA.setDispatcherForTests(new RecordingDispatcher());
        Shepherd sh = shepherd(true);
        Annotation body = ann("body", "guitarfish");
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        withStatics(config(one), () -> {
            JSONObject inherited = new JSONObject()
                .put(Task.PARAM_RUN_ID, "OLD-RUN").put(Task.PARAM_V2_MATCH, true)
                .put(Task.PARAM_OPTION, true).put("ibeis.identification", new JSONObject().put("x", 1))
                .put("importTaskId", "IMP1").put("skipIdent", false);
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(body), null, inherited, false);
            Task u = o.umbrella;
            assertEquals(u.getId(), Task.runIdOf(u));
            assertFalse(u.getParameters().has(Task.PARAM_OPTION));
            assertFalse(u.getParameters().has(Task.PARAM_V2_MATCH));
            assertFalse(u.getParameters().has("ibeis.identification"));
            assertEquals("IMP1", u.getParameters().getString("importTaskId"));
            assertFalse(u.getParameters().getBoolean("skipIdent"));
            for (IA.OptionResult r : o.options) {
                assertEquals(u.getId(), Task.runIdOf(r.task));
                assertFalse(r.task.getParameters().toString().contains("OLD-RUN"));
            }
            assertEquals("OLD-RUN", inherited.getString(Task.PARAM_RUN_ID), "the caller's object is not mutated");
        });
    }

    @Test void singleOptionSingleClassUsesTheUmbrellaAsTheOption() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertEquals(1, o.options.size());
            assertSame(o.umbrella, o.options.get(0).task);
            assertTrue(Task.isUmbrella(o.umbrella));
            assertTrue(Task.isOptionTask(o.umbrella));
            assertFalse(o.umbrella.hasChildren());
            assertTrue(o.umbrella.getParameters().has("ibeis.identification"));
            assertEquals(1, d.published.size());
        });
    }

    @Test void persistFailureDispatchesNothingAndOldOverloadReturnsNull() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(false);
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish")), null, null, false);
            assertTrue(o.persistFailed);
            assertNotNull(o.umbrella);
            assertTrue(o.options.isEmpty(), "nothing is recorded as dispatched");
            assertTrue(d.vectorRuns.isEmpty());
            assertTrue(d.published.isEmpty());
            assertNull(IA.intakeAnnotations(sh, Arrays.asList(ann("body", "guitarfish")), null, false));
        });
    }

    @Test void parentIsLinkedBeforeThePersistCommit() throws Exception {
        IA.setDispatcherForTests(new RecordingDispatcher());
        Shepherd sh = shepherd(true);
        Task parent = new Task();
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish")), parent, null, false);
            assertSame(parent, o.umbrella.getParent());
            assertTrue(parent.getChildren().contains(o.umbrella));
            InOrder order = inOrder(sh.getPM(), sh);
            order.verify(sh.getPM()).makePersistent(o.umbrella);
            order.verify(sh).commitDBTransactionWithStatus();
            order.verify(sh).beginDBTransaction();
        });
    }

    @Test void publishFailureMarksQueueFailedAndKeepsTheVectorOption() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        d.publishResult = false;
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            IA.OptionResult hs = o.options.get(1);
            assertFalse(hs.vector);
            assertFalse(hs.published);
            assertEquals("QUEUE_FAILED", hs.failureCode);
            assertEquals("error", hs.task.getStoredStatus());
            assertEquals("QUEUE_FAILED", errorCode(hs.task));
            assertNotNull(hs.task.getCompletionDateInMilliseconds());
            assertTrue(o.options.get(0).published);
        });
    }

    @Test void publishExceptionMarksQueueFailed() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        d.publishThrows = true;
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertEquals("QUEUE_FAILED", o.options.get(0).failureCode);
            assertEquals("error", o.umbrella.getStoredStatus(), "single option: the umbrella is the option");
        });
    }

    @Test void vectorDispatchThrowTerminalizesAttachedLeavesAndContinues() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        d.vectorThrowsForIaClass = "guitarfish";
        d.attachLeafBeforeThrow = true;
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish"), ann("rostrum", "guitarfish+rostrum")), null, null, false);
            assertEquals(4, o.options.size(), "the outcome lists every option even after a throw");
            IA.OptionResult failed = null;
            int published = 0;
            for (IA.OptionResult r : o.options) {
                if ("DISPATCH_FAILED".equals(r.failureCode)) failed = r;
                if (r.published) published++;
            }
            assertNotNull(failed);
            assertTrue(failed.vector);
            assertEquals("error", failed.task.getStoredStatus());
            assertEquals("DISPATCH_FAILED", errorCode(failed.task));
            assertEquals(1, failed.task.getChildren().size());
            Task leaf = failed.task.getChildren().get(0);
            assertEquals("error", leaf.getStoredStatus(), "an attached, unfinished leaf is terminalized");
            assertEquals("DISPATCH_FAILED", errorCode(leaf));
            assertEquals(3, published, "the other three options were still dispatched");
            assertEquals(2, d.vectorRuns.size());
            assertEquals(2, d.published.size());
        });
    }

    @Test void noDefaultOptionsMarksTheUmbrellaInvalidAndDispatchesNothing() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> none = new HashMap<String, List<JSONObject>>();
        JSONObject off = hotspotter().put("default", false);
        none.put("guitarfish", Arrays.asList(off));
        withStatics(config(none), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertTrue(o.noWork);
            assertTrue(IA.intakeProducedNoWork(o.umbrella));
            assertEquals("error", o.umbrella.getStoredStatus());
            assertEquals("INVALID_MATCH_CONFIG", errorCode(o.umbrella));
            assertNotNull(o.umbrella.getCompletionDateInMilliseconds());
            assertEquals(Arrays.asList("guitarfish"), o.skippedIaClasses);
            assertTrue(o.options.isEmpty());
            assertTrue(d.published.isEmpty() && d.vectorRuns.isEmpty());
            verify(sh.getPM()).makePersistent(o.umbrella);
            verify(sh).commitDBTransactionWithStatus();
        });
    }

    @Test void classWithoutOptionsIsSkippedWhileOthersRun() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(miewid()));
        withStatics(config(m), () -> {   // "mystery" has no entry -> identOpts returns null
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("a", "guitarfish"), ann("b", "mystery")), null, null, false);
            assertFalse(o.noWork);
            assertEquals(Arrays.asList("mystery"), o.skippedIaClasses);
            assertEquals(1, o.options.size());
            assertEquals(2, o.umbrella.numberAnnotations(), "the umbrella still holds every annotation");
            assertNull(o.umbrella.getStoredStatus());
        });
    }

    /** Review Focus 1: a matchingAlgorithms override replaces the default list exactly as today. */
    @Test void matchingAlgorithmsOverrideReplacesDefaults() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        withStatics(config(twoClassesTwoOptions()), () -> {
            JSONObject inherited = new JSONObject().put("matchingAlgorithms", new JSONArray().put(hotspotter()));
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, inherited, false);
            assertEquals(1, o.options.size());
            assertFalse(o.options.get(0).vector);
            assertTrue(Task.isOptionTask(o.options.get(0).task));
            assertEquals(o.umbrella.getId(), Task.runIdOf(o.options.get(0).task));
            assertTrue(d.vectorRuns.isEmpty());
        });
    }

    @Test void defaultFalseOptionsAreDroppedAndFastlanePublishesToTheFastQueue() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(miewid().put("default", false), hotspotter()));
        withStatics(config(m), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, true);
            assertEquals(1, o.options.size());
            assertFalse(o.options.get(0).vector);
            assertTrue(d.published.get(0).getBoolean("fastlane"));
            assertEquals("fast", d.published.get(0).getString("lane"));
        });
    }

    @Test void baseUrlFailureMarksHotspotterOptionsQueueFailedAndStillRunsVector() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        IAJsonProperties cfg = config(one);
        try (MockedStatic<CommonConfiguration> cc = mockStatic(CommonConfiguration.class);
             MockedStatic<IAJsonProperties> jp = mockStatic(IAJsonProperties.class)) {
            cc.when(() -> CommonConfiguration.getServerURL(anyString())).thenThrow(new IllegalStateException("no server URL"));
            jp.when(IAJsonProperties::iaConfig).thenReturn(cfg);
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertTrue(o.options.get(0).published, "the vector option does not need a base URL");
            assertEquals("QUEUE_FAILED", o.options.get(1).failureCode);
            assertEquals("error", o.options.get(1).task.getStoredStatus());
            assertTrue(d.published.isEmpty());
        }
    }

    @Test void emptyInputReturnsNull() throws Exception {
        assertNull(IA.intakeAnnotationsUnder(shepherd(true), new ArrayList<Annotation>(), null, null, false));
        assertNull(IA.intakeAnnotationsUnder(shepherd(true), null, null, null, false));
    }

    @Test void scrubRunParamsRemovesExactlyTheRunKeys() {
        JSONObject in = new JSONObject().put(Task.PARAM_RUN_ID, "R").put(Task.PARAM_V2_MATCH, true)
            .put(Task.PARAM_OPTION, true).put("ibeis.identification", new JSONObject())
            .put("importTaskId", "IMP1").put("matchingSetFilter", new JSONObject().put("k", 1)).put("ibeis.detection", true);
        JSONObject out = IA.scrubRunParams(in);
        assertEquals(3, out.length());
        assertEquals("IMP1", out.getString("importTaskId"));
        assertTrue(out.has("matchingSetFilter"));
        assertTrue(out.getBoolean("ibeis.detection"), "the media boundary keeps its own keys; only run keys go");
        assertEquals(7, in.length(), "input not mutated");
        assertEquals(0, IA.scrubRunParams(null).length());
    }

    @Test void intakeProducedNoWorkIsPure() {
        assertFalse(IA.intakeProducedNoWork(null));
        Task bare = new Task();
        assertTrue(IA.intakeProducedNoWork(bare));
        bare.addParameter("ibeis.identification", new JSONObject());
        assertFalse(IA.intakeProducedNoWork(bare), "a single-option umbrella is work");
        Task withKid = new Task();
        new Task(withKid);
        assertFalse(IA.intakeProducedNoWork(withKid));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=IAIntakeCoreTest -DfailIfNoTests=false`
Expected: compilation error — `IA.Dispatcher`, `IA.IntakeOutcome`, `intakeAnnotationsUnder`, `setDispatcherForTests`, `intakeProducedNoWork` do not exist.

- [ ] **Step 3: Implement the core in `IA.java`**

Replace lines 373-509 (the 2-arg `intakeAnnotations`, the 4-arg `intakeAnnotations` and its whole body, up to and including `return topTask; }`) with:

```java
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
        return intakeAnnotations(myShepherd, anns, null, false);
    }

    // ---- issue #1787 (spec §4.2): outcome record and dispatcher seam ----

    /** One option task of an intake run and what happened to its dispatch. */
    public static final class OptionResult {
        public final Task task;
        public final boolean vector;
        public boolean published = false;
        public String failureCode = null;

        OptionResult(Task task, boolean vector) {
            this.task = task;
            this.vector = vector;
        }
    }

    /** What {@link #intakeAnnotationsUnder} built, persisted and dispatched. */
    public static final class IntakeOutcome {
        public Task umbrella = null;
        public final List<OptionResult> options = new ArrayList<OptionResult>();
        public final List<String> skippedIaClasses = new ArrayList<String>();
        public boolean noWork = false;
        public boolean persistFailed = false;
    }

    /** Seam between the core and its two dispatch targets, so tests run with no OpenSearch, ml-service or file queue. */
    interface Dispatcher {
        /** Runs a vector option inline (Embedding.findMatchProspects); may throw. */
        void runVector(JSONObject opt, Task optionTask, Shepherd myShepherd);

        /** Publishes a HotSpotter job; false or an exception means "not published". */
        boolean publish(String context, String job, boolean fastlane) throws java.io.IOException;
    }

    private static final Dispatcher REAL_DISPATCHER = new Dispatcher() {
        @Override public void runVector(JSONObject opt, Task optionTask, Shepherd myShepherd) {
            Embedding.findMatchProspects(opt, optionTask, myShepherd);
        }

        @Override public boolean publish(String context, String job, boolean fastlane)
        throws java.io.IOException {
            if (fastlane) return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, job);
            return org.ecocean.servlet.IAGateway.addToQueue(context, job);
        }
    };
    private static volatile Dispatcher dispatcher = REAL_DISPATCHER;

    /** Test hook; null restores the real dispatcher. */
    static void setDispatcherForTests(Dispatcher d) {
        dispatcher = (d == null) ? REAL_DISPATCHER : d;
    }

    /**
     * Existing entry point, unchanged signature. The umbrella is NOT linked to
     * {@code parentTask} here (callers attach it afterwards, as before); the parent only
     * supplies the inherited parameters. Returns null when the run could not be persisted
     * (today's code continued blind after a swallowed commit failure).
     */
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
        final Task parentTask, boolean fastlane) {
        JSONObject inherited = (parentTask == null) ? null : parentTask.getParameters();
        IntakeOutcome o = intakeCore(myShepherd, anns, null, inherited, fastlane);

        if ((o == null) || o.persistFailed) return null;
        return o.umbrella;
    }

    /**
     * Issue #1787 entry point for the ml-service match step (spec §4.2.1). When {@code parent}
     * is non-null the umbrella is linked under it BEFORE the persist commit. Returns null only
     * for an empty annotation list.
     */
    public static IntakeOutcome intakeAnnotationsUnder(Shepherd myShepherd, List<Annotation> anns,
        Task parent, JSONObject inheritedParams, boolean fastlane) {
        return intakeCore(myShepherd, anns, parent, inheritedParams, fastlane);
    }

    /** A childless umbrella without an option: no class had a default identification option. */
    public static boolean intakeProducedNoWork(Task umbrella) {
        if (umbrella == null) return false;
        if (umbrella.hasChildren()) return false;
        JSONObject p = umbrella.getParameters();
        return (p == null) || !p.has("ibeis.identification");
    }

    /**
     * Inherited parameters minus the keys that belong to a run (the new run sets its own).
     * Also applied where a root's parameters are copied into detection tasks
     * (intakeMediaAssetsOneSpecies) and onto a substituted handleRest root, so a run id can
     * never leak across a pipeline boundary (spec §4.0). Never returns null; never mutates.
     */
    static JSONObject scrubRunParams(JSONObject inherited) {
        JSONObject p = (inherited == null) ? new JSONObject() : new JSONObject(inherited.toString());
        p.remove(Task.PARAM_RUN_ID);
        p.remove(Task.PARAM_V2_MATCH);
        p.remove(Task.PARAM_OPTION);
        p.remove("ibeis.identification");
        return p;
    }

    private static void markTaskError(Task t, String code, String message) {
        t.setStatus("error");
        t.setStatusDetailsAddError(code, message);
        t.setCompletionDateInMilliseconds();
    }

    // §4.2.3 step 3: a vector dispatch that threw may have attached unfinished leaves
    private static void terminalizeUnfinishedChildren(Task option, String code, String message) {
        if (option.getChildren() == null) return;
        for (Task kid : option.getChildren()) {
            if (!kid.statusInEndState()) markTaskError(kid, code, message);
        }
    }

    /*
     * The core: build the whole tree, persist it with a checked commit, then dispatch each
     * option through the Dispatcher seam. Never throws after the persist step; the outcome
     * always lists every option with its publication result (spec §4.2.3).
     */
    private static IntakeOutcome intakeCore(Shepherd myShepherd, List<Annotation> anns,
        Task parent, JSONObject inheritedParams, boolean fastlane) {
        if ((anns == null) || (anns.size() < 1)) return null;
        String context = myShepherd.getContext();
        IntakeOutcome outcome = new IntakeOutcome();

        // ---- build ----
        Task topTask = new Task();
        outcome.umbrella = topTask;
        JSONObject runParams = scrubRunParams(inheritedParams);
        runParams.put(Task.PARAM_RUN_ID, topTask.getId());   // every task of this run carries it
        topTask.setParameters(runParams);
        topTask.setObjectAnnotations(anns);
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
        List<JSONObject> plannedOpts = new ArrayList<JSONObject>();
        List<Task> plannedTasks = new ArrayList<Task>();
        List<List<Annotation> > plannedAnns = new ArrayList<List<Annotation> >();
        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
            String iaClass = annsOneIAClass.get(0).getIAClass();
            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
            // drop default=false options (a matchingAlgorithms parameter may re-add them below)
            if (opts != null) {
                Iterator<JSONObject> itr = opts.iterator();
                while (itr.hasNext()) {
                    if (!itr.next().optBoolean("default", true)) itr.remove();
                }
            }
            System.out.println("identOpts: " + opts);
            // a fresh copy of the inherited parameters per iaClass, as before
            JSONObject newTaskParams = new JSONObject(runParams.toString());
            if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
                JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
                System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
                ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
                int maLength = matchingAlgorithms.length();
                for (int y = 0; y < maLength; y++) {
                    newOpts.add(matchingAlgorithms.getJSONObject(y));
                }
                System.out.println("matchingAlgorithms2: " + newOpts.toString());
                if (newOpts.size() > 0) {
                    opts = newOpts;
                    System.out.println("Swapping opts for newOpts!!");
                }
            }
            if ((opts == null) || (opts.size() < 1)) {   // no ID for this iaClass
                outcome.skippedIaClasses.add(iaClass);
                continue;
            }
            if (fastlane) {
                newTaskParams.put("fastlane", true);
                newTaskParams.put("lane", "fast");
            }
            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
                // one IA class, one algorithm: the umbrella is *the* (only) option task
                JSONObject opt = opts.get(0);
                newTaskParams.put("ibeis.identification", (opt == null) ? "DEFAULT" : opt);
                newTaskParams.put(Task.PARAM_OPTION, true);
                if (Embedding.isVectorConfig(opt)) newTaskParams.put(Task.PARAM_V2_MATCH, true);
                topTask.setParameters(newTaskParams);
                plannedOpts.add(opt);
                plannedTasks.add(topTask);
                plannedAnns.add(annsOneIAClass);
            } else {
                for (int i = 0; i < opts.size(); i++) {
                    JSONObject opt = opts.get(i);
                    Task t = new Task();
                    t.setObjectAnnotations(annsOneIAClass);
                    JSONObject tp = new JSONObject(newTaskParams.toString());
                    tp.put("ibeis.identification", (opt == null) ? "DEFAULT" : opt);
                    tp.put(Task.PARAM_OPTION, true);
                    if (Embedding.isVectorConfig(opt)) tp.put(Task.PARAM_V2_MATCH, true);
                    t.setParameters(tp);
                    topTask.addChild(t);
                    plannedOpts.add(opt);
                    plannedTasks.add(t);
                    plannedAnns.add(annsOneIAClass);
                }
            }
        } // end for each iaClass
        if (plannedTasks.isEmpty()) {   // §4.2.3 step 4: terminal, for every caller
            outcome.noWork = true;
            markTaskError(topTask, "INVALID_MATCH_CONFIG",
                "no default identification options for iaClass(es) " + outcome.skippedIaClasses);
        }
        if (parent != null) topTask.setParent(parent);   // also parent.addChild(topTask)

        // ---- persist (checked) ----
        myShepherd.beginDBTransaction();   // no-op when already active
        myShepherd.getPM().makePersistent(topTask);   // children by reachability
        if (!myShepherd.commitDBTransactionWithStatus()) {
            System.out.println("ERROR: IA.intakeAnnotations() could not persist run " +
                topTask.getId() + "; nothing dispatched");
            outcome.persistFailed = true;
            return outcome;
        }
        myShepherd.beginDBTransaction();
        if (outcome.noWork) {
            System.out.println("INFO: IA.intakeAnnotations() finished with no work as " + topTask);
            return outcome;
        }

        // ---- outcome record before any dispatch ----
        for (int i = 0; i < plannedTasks.size(); i++) {
            outcome.options.add(new OptionResult(plannedTasks.get(i),
                Embedding.isVectorConfig(plannedOpts.get(i))));
        }

        // ---- dispatch (never throws: every failure lands on the option task and the outcome) ----
        String baseUrl = null;
        String baseUrlError = null;
        try {
            baseUrl = getBaseURL(context);
        } catch (RuntimeException ex) {
            baseUrlError = ex.toString();
            System.out.println("ERROR: IA.intakeAnnotations() getBaseURL() threw " + ex);
        }
        for (int i = 0; i < outcome.options.size(); i++) {
            OptionResult r = outcome.options.get(i);
            JSONObject opt = plannedOpts.get(i);
            Task t = r.task;
            try {
                if (r.vector) {
                    dispatcher.runVector(opt, t, myShepherd);
                    r.published = true;
                    continue;
                }
                if (baseUrlError != null) {
                    r.failureCode = "QUEUE_FAILED";
                    markTaskError(t, "QUEUE_FAILED", "no base URL for the queue job: " + baseUrlError);
                    continue;
                }
                JSONArray annArr = new JSONArray();
                for (Annotation ann : plannedAnns.get(i)) {
                    annArr.put(ann.getId());
                }
                JSONObject aj = new JSONObject();
                aj.put("annotationIds", annArr);
                JSONObject qjob = new JSONObject();
                qjob.put("identify", aj);
                qjob.put("taskId", t.getId());
                qjob.put("__context", context);
                qjob.put("__baseUrl", baseUrl);
                if (opt != null) qjob.put("opt", opt);
                if (fastlane) {
                    qjob.put("fastlane", true);
                    qjob.put("lane", "fast");
                }
                boolean sent = false;
                try {
                    t.setQueueResumeMessage(qjob.toString());
                    sent = dispatcher.publish(context, qjob.toString(), fastlane);
                } catch (Exception ex) {
                    System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() addToQueue() threw " + ex);
                }
                if (sent) {
                    r.published = true;
                } else {
                    r.failureCode = "QUEUE_FAILED";
                    markTaskError(t, "QUEUE_FAILED", "identification job could not be published to the queue");
                }
                System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " +
                    plannedAnns.get(i).size() + " annots; queued? = " + sent + "; " + t);
            } catch (RuntimeException ex) {
                // a vector dispatch that threw, or anything unexpected in the HotSpotter branch
                System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() dispatch threw " + ex + " on " + t);
                ex.printStackTrace();
                r.failureCode = r.vector ? "DISPATCH_FAILED" : "QUEUE_FAILED";
                markTaskError(t, r.failureCode, "dispatch threw: " + ex);
                terminalizeUnfinishedChildren(t, r.failureCode, "option dispatch threw before this leaf finished");
            }
        }
        System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
        return outcome;
    }
```

Also in `IA.intakeMediaAssetsOneSpecies` (line 149) replace

```java
        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
```

with

```java
        // issue #1787 (spec §4.0): a detection task never carries a run's keys, even when the
        // root it descends from is a reused umbrella (handleRest)
        if ((parentTask != null) && (parentTask.getParameters() != null))
            topTask.setParameters(scrubRunParams(parentTask.getParameters()));
```

(`enqueueOneAssetForMlService` builds its per-asset child with `new Task(topTask)`, so it inherits the scrubbed copy.)

Notes for the implementer:
- Today's code put `fastlane`/`lane` into `newTaskParams` AFTER the tasks had serialized their parameters, so the flag never reached the task parameters; it only reached the queue job. The code above puts it on the task parameters only when `fastlane` is true (a `fastlane:false` key would make the `/metrics` fastlane gauge, which matches the substring `fastlane`, count every task).
- `storeNewTask` is no longer used here; `makePersistent` + `commitDBTransactionWithStatus` replaces it (§4.2.3).
- `IA.java` already imports `Iterator`, `ArrayList`, `List`, `JSONArray`, `JSONObject`, `Embedding`, `IAJsonProperties`.

In `MetricsBot.java` replace the comment block and HELP text of the `wildbook_tasks_vectorMatch` gauge (lines 545-559) with:

```java
        // Vector (ml-service) identification leaf tasks carry mlServiceV2Match=true on their
        // option task and inherit it (Embedding.findMatchProspects -> new Task(option)).
        // Since issue #1787 every entry point (bulk import, New Match, resend) marks its vector
        // option tasks, so this counts all of them. children==null counts the leaves (one per
        // annotation; the option task has children and is excluded). A vector option that
        // failed before creating any leaf is childless and counts as one. Match the quoted JSON
        // key (like the hotspotter '"sv_on"' gauge) so we hit the key, not a substring.
        addLineIfNotNull(csvLines, buildGauge(
            "SELECT count(this) FROM org.ecocean.ia.Task where children == null && parameters.indexOf('\"mlServiceV2Match\"')>-1",
            "wildbook_tasks_vectorMatch",
            "Number of vector identification leaf tasks (all entry points; counts errors too)",
            context));
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='IAIntakeCoreTest,IdentificationTest,ProjectIATransactionTest' -DfailIfNoTests=false`
Expected: PASS. (`IdentificationTest.basicAddToQueue` calls the old overload with a mocked Shepherd whose `commitDBTransactionWithStatus()` returns false by default, so it now gets null back; it asserts nothing on the result, so it still passes. If it fails on the unstubbed `getContext()` returning null, add `when(myShepherd.getContext()).thenReturn("context0");` to that test.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/MetricsBot.java src/test/java/org/ecocean/ia/IAIntakeCoreTest.java
git commit -m "Intake core: build, persist with a checked commit, then dispatch (#1787)

IA.intakeAnnotationsUnder returns an IntakeOutcome listing every option task
and whether its job was published. The run id goes on every task, the
option marker on every option task, mlServiceV2Match on vector options.
Publication failures mark QUEUE_FAILED, a throwing vector dispatch marks
DISPATCH_FAILED and terminalizes unfinished leaves, and a run with no
default option is marked INVALID_MATCH_CONFIG for every caller (spec §4.2).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** of `IA.java` + the test against §4.2.1-4.2.4; ask specifically whether any existing caller (`ParallelIdentify`, `IBEISIA.processCallback`, `handleRest`, `ProjectIA`) observes a behaviour change beyond the documented null-on-persist-failure and the new parameters.

---

### Task 5: `handleRest` never attaches beneath an umbrella

**Files:**
- Modify: `src/main/java/org/ecocean/ia/IA.java` (`handleRest` lines 531-609)
- Test: `src/test/java/org/ecocean/ia/IAHandleRestRootTest.java` (new)

**Interfaces:**
- Consumes: `Task.runIdOf` (Task 1), `Task.hasObjectAnnotations()` (exists).
- Produces: `static Task resolveRequestRoot(Task loaded, String taskId, boolean fastlane)` (package-private, pure) — the root `handleRest` builds under.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/ia/IAHandleRestRootTest.java`:

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Spec §4.0: nothing is attached beneath an umbrella; handleRest substitutes a fresh root. */
class IAHandleRestRootTest {

    @Test void unknownTaskIdCreatesARootWithThatId() {
        Task root = IA.resolveRequestRoot(null, "req-1", false);
        assertEquals("req-1", root.getId());
        assertTrue(root.getParameters() == null || !root.getParameters().has("fastlane"));
    }

    @Test void fastlaneIsStampedOnTheRoot() {
        Task root = IA.resolveRequestRoot(null, "req-1", true);
        assertTrue(root.getParameters().getBoolean("fastlane"));
    }

    /** Review Focus 5: the hamburger-menu New Match reuses the media-asset-only detection root. */
    @Test void mediaAssetOnlyRootIsReused() {
        Task loaded = new Task("req-2");
        loaded.addObject(mock(MediaAsset.class));
        assertSame(loaded, IA.resolveRequestRoot(loaded, "req-2", false));
    }

    @Test void rootHoldingAnnotationsIsReplacedAndKeepsTheRequestParameters() {
        Task loaded = new Task("req-3");
        loaded.setParameters(new JSONObject()
            .put("matchingSetFilter", new JSONObject().put("locationIds", new JSONArray().put("X")))
            .put("matchingAlgorithms", new JSONArray().put(new JSONObject().put("default", true)))
            .put("userId", "u1").put(Task.PARAM_RUN_ID, "req-3").put(Task.PARAM_OPTION, true)
            .put("ibeis.identification", new JSONObject()));
        Annotation a = new Annotation();
        a.setId("A");
        loaded.addObject(a);
        Task root = IA.resolveRequestRoot(loaded, "req-3", true);
        assertNotSame(loaded, root);
        assertNotEquals("req-3", root.getId());
        assertFalse(root.hasObjectAnnotations());
        JSONObject p = root.getParameters();
        assertTrue(p.getBoolean("fastlane"));
        assertTrue(p.has("matchingSetFilter"), "the matching-set filter survives the substitution");
        assertTrue(p.has("matchingAlgorithms"));
        assertEquals("u1", p.getString("userId"));
        assertFalse(p.has(Task.PARAM_RUN_ID));
        assertFalse(p.has(Task.PARAM_OPTION));
        assertFalse(p.has("ibeis.identification"));
    }

    @Test void rootCarryingARunIdIsReplaced() {
        Task loaded = new Task("req-4");
        loaded.addParameter(Task.PARAM_RUN_ID, "req-4");   // an umbrella
        Task root = IA.resolveRequestRoot(loaded, "req-4", false);
        assertNotSame(loaded, root);
        assertTrue(root.getParameters() == null || !root.getParameters().has(Task.PARAM_RUN_ID));
        Task inherited = new Task("req-5");
        inherited.addParameter(Task.PARAM_RUN_ID, "someone-else");   // an option/leaf of a run
        assertNotSame(inherited, IA.resolveRequestRoot(inherited, "req-5", false));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=IAHandleRestRootTest -DfailIfNoTests=false`
Expected: compilation error — `resolveRequestRoot` undefined.

- [ ] **Step 3: Implement**

In `IA.java` add before `handleRest`:

```java
    /**
     * Issue #1787 (spec §4.0): a client-supplied root id may name an existing task. If that task
     * holds annotations or belongs to an identification run (umbrella, option or leaf), a new
     * run must not hang beneath it — the umbrella's Match Results page renders exactly its own
     * run — so a fresh root is used instead. A media-asset-only root (the detection root the
     * hamburger-menu New Match attaches to) is reused as before.
     */
    static Task resolveRequestRoot(Task loaded, String taskId, boolean fastlane) {
        Task root = loaded;

        if ((root != null) && (root.hasObjectAnnotations() || (Task.runIdOf(root) != null))) {
            System.out.println("WARN: IA.handleRest() root task " + taskId +
                " holds annotations or belongs to identification run " + Task.runIdOf(root) +
                "; creating a fresh root instead");
            root = new Task();
            // the request's parameters (IAGateway stores taskParameters — matchingSetFilter,
            // matchingAlgorithms, userId… — on the loaded task) travel to the fresh root,
            // minus the keys that belong to the old run
            if (loaded.getParameters() != null) root.setParameters(scrubRunParams(loaded.getParameters()));
        }
        if (root == null) root = new Task(taskId);
        if (fastlane) root.addParameter("fastlane", true);
        return root;
    }
```

In `handleRest`, replace

```java
            String taskId = jin.optString("taskId", Util.generateUUID());
            Task topTask = Task.load(taskId, myShepherd);
            if (topTask == null) topTask = new Task(taskId);
            if (fastlane) topTask.addParameter("fastlane", true);
            myShepherd.storeNewTask(topTask);
```

with

```java
            String taskId = jin.optString("taskId", Util.generateUUID());
            Task topTask = resolveRequestRoot(Task.load(taskId, myShepherd), taskId, fastlane);
            myShepherd.storeNewTask(topTask);
```

and replace the media-asset guard

```java
                for (MediaAsset ma : masForNewRoot) {
                    if (!topTask.getObjectMediaAssets().contains(ma)) {
                        topTask.addObject(ma);
                    }
                }
```

with

```java
                for (MediaAsset ma : masForNewRoot) {
                    topTask.addObject(ma);   // dedupes; also safe on a fresh root whose list is null
                }
```

(the old guard threw NullPointerException on a root with no media-asset list, which a fresh root always is).

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='IAHandleRestRootTest,IAIntakeCoreTest' -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/ia/IA.java src/test/java/org/ecocean/ia/IAHandleRestRootTest.java
git commit -m "handleRest: never attach a new run beneath an umbrella (#1787)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** (confirm the React New Match page's request shape — `frontend/src` calls `/ia` with a fresh taskId or none — is unaffected; confirm the NPE fix is behaviour-preserving for the reuse path).

---

### Task 6: `_doIdentify` keeps the option's identity; a failed send is pending while a retry is queued

**Files:**
- Modify: `src/main/java/org/ecocean/ia/Task.java` (two pure helpers next to `runIdOf`)
- Modify: `src/main/java/org/ecocean/servlet/IAGateway.java` (`_doIdentify` lines 404-411 and 428-447; `_sendIdentificationTask` lines 505-522)
- Test: `src/test/java/org/ecocean/servlet/IAGatewayDoIdentifyTest.java` (new)

**Interfaces:**
- Consumes: `Task.PARAM_*`, `Task.STATUS_REQUEUED` (Task 1); `IAGateway.requeueJob(JSONObject, boolean)` returns false when exhausted (exists).
- Produces (on `Task`): `public static JSONObject identityParams(JSONObject params)` — a new object holding only `importTaskId`, `identificationInvocationId`, `identificationOption` from `params`, or null when none is present (or `params` is null); `public static JSONObject withoutOptionMarker(JSONObject params)` — a copy without `identificationOption`, or null for null.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/servlet/IAGatewayDoIdentifyTest.java`:

```java
package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.jdo.PersistenceManager;

import org.ecocean.Annotation;
import org.ecocean.ia.Task;
import org.ecocean.identity.IAQueryCache;
import org.ecocean.identity.IBEISIA;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Spec §4.0 (fan-out identity) and §4.4 (queued retries are pending). */
class IAGatewayDoIdentifyTest {

    // The Shepherd's PM answers getObjectById from a map, so Task.load and the annotation
    // lookups in _doIdentify resolve to our fixtures. Ids must be distinct across types.
    private static Shepherd shepherdWith(Map<String, Object> byId) {
        PersistenceManager pm = mock(PersistenceManager.class);
        when(pm.newObjectIdInstance(any(Class.class), any())).thenAnswer(inv -> inv.getArgument(1));
        when(pm.getObjectById(any(), anyBoolean())).thenAnswer(inv -> byId.get(inv.getArgument(0)));
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);
        when(sh.getContext()).thenReturn("context0");
        return sh;
    }

    private static Annotation ann(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        a.setIAClass("guitarfish");
        return a;
    }

    private static JSONObject jin(String... annIds) {
        JSONArray ids = new JSONArray();
        for (String id : annIds) ids.put(id);
        return new JSONObject().put("identify", new JSONObject().put("annotationIds", ids))
            .put("opt", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"));
    }

    private static JSONObject sendResult(boolean ok) {
        JSONObject r = new JSONObject().put("success", ok);
        if (ok) {
            r.put("status", new JSONObject().put("success", true)).put("response", "job-1");
        } else {
            r.put("error", "WBIA refused");
        }
        return r;
    }

    interface Body { void run() throws Exception; }

    // Partial static mocks through a custom default Answer: the few IBEISIA / IAGateway statics
    // _doIdentify needs are answered here, everything else runs real. (MockedStatic.when(...)
    // on a CALLS_REAL_METHODS mock would execute the real method while defining the stub —
    // waitForIAPriming can block for minutes and requeueJob(null, …) would throw.)
    private static void withIdentifyStubs(JSONObject sendResult, boolean requeueResult, Body body)
    throws Exception {
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("waitForIAPriming")) return null;
                 if (m.equals("queryConfigDict")) return new JSONObject();
                 if (m.equals("beginIdentifyAnnotations")) return sendResult;
                 if (m.equals("log")) return null;
                 return inv.callRealMethod();
             });
             MockedStatic<IAQueryCache> cache = mockStatic(IAQueryCache.class);
             MockedStatic<IAGateway> gw = mockStatic(IAGateway.class, inv -> {
                 if (inv.getMethod().getName().equals("requeueJob")) return requeueResult;
                 return inv.callRealMethod();
             })) {
            cache.when(() -> IAQueryCache.tryTargetAnnotationsCache(anyString(), any(Annotation.class),
                any(JSONObject.class), any(Shepherd.class))).thenReturn(null);
            body.run();
        }
    }

    private static String lastErrorCode(Task t) {
        JSONObject sd = t.getStatusDetails();
        JSONArray errs = (sd == null) ? null : sd.optJSONArray("errors");
        if ((errs == null) || (errs.length() == 0)) return null;
        return errs.getJSONObject(errs.length() - 1).optString("code", null);
    }

    @Test void failedSendStoresRequeuedWhileARetryIsScheduled() throws Exception {
        Task t = new Task("T1");
        Annotation a = ann("A1");
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T1", t);
        byId.put("A1", a);
        Shepherd sh = shepherdWith(byId);
        JSONObject res = new JSONObject().put("taskId", "T1");
        withIdentifyStubs(sendResult(false), true, () -> {
            JSONObject out = IAGateway._doIdentify(jin("A1"), res, sh, "context0", "http://wb", false);
            assertTrue(out.getBoolean("success"));
            assertEquals(Task.STATUS_REQUEUED, t.getStoredStatus());
            assertFalse(t.statusInEndState());
            assertEquals("SEND_FAILED", lastErrorCode(t));
            assertNull(t.getCompletionDateInMilliseconds(), "not terminal: no completion date");
        });
    }

    @Test void failedSendStoresErrorWhenTheRetryBudgetIsExhausted() throws Exception {
        Task t = new Task("T2");
        Annotation a = ann("A2");
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T2", t);
        byId.put("A2", a);
        Shepherd sh = shepherdWith(byId);
        JSONObject res = new JSONObject().put("taskId", "T2");
        withIdentifyStubs(sendResult(false), false, () -> {
            IAGateway._doIdentify(jin("A2"), res, sh, "context0", "http://wb", false);
            assertEquals("error", t.getStoredStatus());
            assertTrue(t.statusInEndState());
            assertEquals("SEND_FAILED", lastErrorCode(t));
            assertNotNull(t.getCompletionDateInMilliseconds());
        });
    }

    @Test void fanOutParentKeepsIdentityParamsAndChildrenDropTheOptionMarker() throws Exception {
        Task parent = new Task("T3");
        JSONObject params = new JSONObject()
            .put("importTaskId", "IMP1").put(Task.PARAM_RUN_ID, "RUN-1").put(Task.PARAM_OPTION, true)
            .put("ibeis.identification", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"))
            .put("matchingSetFilter", new JSONObject().put("locationIds", new JSONArray().put("X")));
        parent.setParameters(params);
        Annotation a = ann("A3");
        Annotation b = ann("B3");
        parent.addObject(a);
        parent.addObject(b);
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T3", parent);
        byId.put("A3", a);
        byId.put("B3", b);
        Shepherd sh = shepherdWith(byId);
        JSONObject res = new JSONObject().put("taskId", "T3");
        withIdentifyStubs(sendResult(true), true, () -> {
            IAGateway._doIdentify(jin("A3", "B3"), res, sh, "context0", "http://wb", false);
            JSONObject kept = parent.getParameters();
            assertEquals(3, kept.length(), "exactly importTaskId, run id and option marker survive the reset");
            assertEquals("IMP1", kept.getString("importTaskId"));
            assertEquals("RUN-1", kept.getString(Task.PARAM_RUN_ID));
            assertTrue(kept.getBoolean(Task.PARAM_OPTION));
            assertFalse(kept.has("ibeis.identification"), "renderability of the parent is unchanged");
            assertEquals(2, parent.getChildren().size());
            for (Task kid : parent.getChildren()) {
                JSONObject kp = kid.getParameters();
                assertEquals("IMP1", kp.getString("importTaskId"));
                assertEquals("RUN-1", kp.getString(Task.PARAM_RUN_ID));
                assertTrue(kp.has("ibeis.identification"));
                assertTrue(kp.has("matchingSetFilter"));
                assertFalse(kp.has(Task.PARAM_OPTION), "fan-out children are leaves, not options");
                assertEquals(1, kid.numberAnnotations());
            }
        });
    }

    @Test void fanOutParentWithoutIdentityParamsIsClearedToNullAsToday() throws Exception {
        Task parent = new Task("T4");
        parent.setParameters(new JSONObject().put("ibeis.identification", new JSONObject()));
        Annotation a = ann("A4");
        Annotation b = ann("B4");
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T4", parent);
        byId.put("A4", a);
        byId.put("B4", b);
        Shepherd sh = shepherdWith(byId);
        withIdentifyStubs(sendResult(true), true, () -> {
            IAGateway._doIdentify(jin("A4", "B4"), new JSONObject().put("taskId", "T4"), sh, "context0", "http://wb", false);
            assertNull(parent.getParameters());
            assertTrue(parent.getChildren().get(0).getParameters().has("ibeis.identification"));
        });
    }

    /** Review Focus 4: the requeued job re-enters with one annotation; no second fan-out, status left for the callback. */
    @Test void retryReentryWithOneAnnotationDoesNotFanOut() throws Exception {
        Task t = new Task("T5");
        t.setStatus(Task.STATUS_REQUEUED);
        t.setParameters(new JSONObject().put("ibeis.identification", new JSONObject()).put(Task.PARAM_RUN_ID, "RUN-5"));
        Annotation a = ann("A5");
        t.addObject(a);
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T5", t);
        byId.put("A5", a);
        Shepherd sh = shepherdWith(byId);
        withIdentifyStubs(sendResult(true), true, () -> {
            JSONObject out = IAGateway._doIdentify(jin("A5"), new JSONObject().put("taskId", "T5"), sh, "context0", "http://wb", false);
            assertTrue(out.getBoolean("success"));
            assertFalse(t.hasChildren());
            assertEquals(Task.STATUS_REQUEUED, t.getStoredStatus(), "the WBIA callback overwrites it with completed");
            assertEquals(IBEISIA.STATUS_PROCESSING, a.getIdentificationStatus());
            assertEquals("job-1", out.getJSONArray("tasks").getJSONObject(0).getString("jobId"));
        });
    }

    @Test void identityParamsOfNullIsNull() {
        assertNull(Task.identityParams(null));
        assertNull(Task.identityParams(new JSONObject().put("ibeis.identification", new JSONObject())));
        JSONObject p = Task.identityParams(new JSONObject().put("importTaskId", "IMP1").put("x", 1));
        assertEquals(1, p.length());
        assertEquals("IMP1", p.getString("importTaskId"));
        assertNull(Task.withoutOptionMarker(null));
        JSONObject src = new JSONObject().put(Task.PARAM_OPTION, true).put("k", "v");
        JSONObject copy = Task.withoutOptionMarker(src);
        assertFalse(copy.has(Task.PARAM_OPTION));
        assertEquals("v", copy.getString("k"));
        assertTrue(src.has(Task.PARAM_OPTION), "input not mutated");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=IAGatewayDoIdentifyTest -DfailIfNoTests=false`
Expected: compilation error — `Task.identityParams` / `withoutOptionMarker` undefined.

- [ ] **Step 3: Implement**

In `Task.java`, after `anyRunId` add:

```java
    /**
     * The keys that identify a task within its run (issue #1787): kept on the option parent
     * when {@code IAGateway._doIdentify} clears its parameters before fanning out. Returns
     * null when nothing applies, so a legacy parent still ends up with null parameters.
     */
    public static JSONObject identityParams(JSONObject params) {
        if (params == null) return null;
        JSONObject keep = new JSONObject();
        for (String key : new String[] { PARAM_IMPORT_TASK_ID, PARAM_RUN_ID, PARAM_OPTION }) {
            if (params.has(key)) keep.put(key, params.get(key));
        }
        return (keep.length() == 0) ? null : keep;
    }

    /** A copy of {@code params} without the option marker (fan-out children are leaves). */
    public static JSONObject withoutOptionMarker(JSONObject params) {
        if (params == null) return null;
        JSONObject copy = new JSONObject(params.toString());
        copy.remove(PARAM_OPTION);
        return copy;
    }
```

In `IAGateway._doIdentify` replace lines 404-411

```java
        if (anns.size() > 1) { // need to create child Tasks
            JSONObject params = parentTask.getParameters();
            parentTask.setParameters((String)null); // reset this, kids inherit params
            for (int i = 0; i < anns.size(); i++) {
                Task newTask = new Task(parentTask);
                newTask.setParameters(params);
```

with

```java
        if (anns.size() > 1) { // need to create child Tasks
            JSONObject params = parentTask.getParameters();
            // issue #1787 (spec §4.0): the cleared parent keeps importTaskId, its run id and its
            // option marker so the option task survives the fan-out; ibeis.identification is
            // still dropped, so the parent's renderability is unchanged. Children get the full
            // copy minus the option marker: they are leaves, not options.
            parentTask.setParameters(Task.identityParams(params));
            JSONObject childParams = Task.withoutOptionMarker(params);
            for (int i = 0; i < anns.size(); i++) {
                Task newTask = new Task(parentTask);
                newTask.setParameters(childParams);
```

Replace the requeue line in the catch block (line 446)

```java
                requeueJob(jobj, true);
```

with

```java
                boolean requeued = requeueJob(jobj, true);
                if (!requeued) {
                    // retry budget / two-day window exhausted: the only terminal commit on this
                    // path (spec §4.4). While a retry is queued the task stays "requeued".
                    subTask.setStatus("error");
                    subTask.setStatusDetailsAddError("SEND_FAILED",
                        "identification could not be sent and the retry budget is exhausted: " + ex.toString());
                    subTask.setCompletionDateInMilliseconds(Long.valueOf(System.currentTimeMillis()));
                    myShepherd.updateDBTransaction();
                }
```

In `_sendIdentificationTask` replace lines 515-520

```java
                if (task != null) {
                    task.setStatus("error");
                    task.setStatusDetailsAddError("SEND_FAILED",
                        "beginIdentifyAnnotations() failed: " + errorMsg);
                    task.setCompletionDateInMilliseconds(Long.valueOf(System.currentTimeMillis()));
                    myShepherd.updateDBTransaction();
                }
```

with

```java
                if (task != null) {
                    // issue #1787 (spec §4.4): non-terminal while _doIdentify may still schedule a
                    // retry. An explicit stored status, because a null status would make
                    // Task.getStatus derive "error" from the HTTP-error log this failure leaves.
                    task.setStatus(Task.STATUS_REQUEUED);
                    task.setStatusDetailsAddError("SEND_FAILED",
                        "beginIdentifyAnnotations() failed: " + errorMsg + "; retry pending");
                    myShepherd.updateDBTransaction();
                }
```

(the comment above that block about "surface this as an error rather than completed" should be updated to say the terminal error is now written by `_doIdentify` when the requeue is refused).

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='IAGatewayDoIdentifyTest,TaskSelectRunTest' -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/ia/Task.java src/main/java/org/ecocean/servlet/IAGateway.java src/test/java/org/ecocean/servlet/IAGatewayDoIdentifyTest.java
git commit -m "_doIdentify keeps the option's run identity; failed sends are pending while requeued (#1787)

The cleared fan-out parent keeps importTaskId, the run id and the option
marker; fan-out children drop the marker. A failed WBIA send stores the
non-terminal status 'requeued' and becomes error/SEND_FAILED only when
requeueJob refuses (spec §4.0, §4.4).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** (ask it to confirm `processCallbackIdentify` still overwrites `requeued` with `completed`, and that `Task.getStatus`'s inactivity watchdog treats `requeued` like any non-terminal stored status).

---

### Task 7: ml-service match step hands each encounter to the shared intake

**Files:**
- Modify: `src/main/java/org/ecocean/ia/MlServiceProcessor.java` (imports; `runMatchProspects` lines 709-803; new helpers)
- Test: `src/test/java/org/ecocean/ia/MlServiceProcessorMatchIntakeTest.java` (new; pure helpers). The end-to-end flow is covered by Task 11.

**Interfaces:**
- Consumes: `IA.intakeAnnotationsUnder`, `IA.IntakeOutcome`, `IA.OptionResult` (Task 4); `Task.PARAM_*` (Task 1); `Shepherd.commitDBTransactionWithStatus()`.
- Produces (on `MlServiceProcessor`):
  - `static JSONObject matchIntakeParams(JSONObject parentParams)` (package-private, pure).
  - `static List<List<Annotation>> groupByEncounter(List<Annotation> anns, Map<String, String> annIdToEncId)` (package-private, pure).
  - `Shepherd openShepherd(String action)` (package-private seam, overridable).
  - `runMatchProspects` unchanged signature; `MlServiceJobOutcome` codes `MATCH_PERSIST` (persistError), `INVALID_MATCH_CONFIG` (validationError), `MATCH` (persistError on exception).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/ia/MlServiceProcessorMatchIntakeTest.java`:

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Spec §4.1 steps 2-3: the pure helpers of the ml-service match step. */
class MlServiceProcessorMatchIntakeTest {

    private static Annotation ann(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        return a;
    }

    @Test void matchIntakeParamsScrubsRunKeysAndKeepsTheRest() {
        JSONObject d = new JSONObject()
            .put("ibeis.detection", true).put(Task.PARAM_RUN_ID, "OLD").put(Task.PARAM_V2_MATCH, true)
            .put(Task.PARAM_OPTION, true).put("ibeis.identification", new JSONObject())
            .put("importTaskId", "IMP1").put("matchingSetFilter", new JSONObject().put("x", 1)).put("skipIdent", false);
        JSONObject p = MlServiceProcessor.matchIntakeParams(d);
        assertFalse(p.has("ibeis.detection"));
        assertFalse(p.has(Task.PARAM_RUN_ID));
        assertFalse(p.has(Task.PARAM_V2_MATCH));
        assertFalse(p.has(Task.PARAM_OPTION));
        assertFalse(p.has("ibeis.identification"));
        assertEquals("IMP1", p.getString("importTaskId"));
        assertEquals(1, p.getJSONObject("matchingSetFilter").getInt("x"));
        assertFalse(p.getBoolean("skipIdent"));
        assertTrue(d.has("ibeis.detection"), "input not mutated");
        assertEquals(0, MlServiceProcessor.matchIntakeParams(null).length());
    }

    @Test void groupByEncounterKeepsFirstSeenOrderAndIsolatesOrphans() {
        Annotation a = ann("A"), b = ann("B"), c = ann("C"), d = ann("D");
        Map<String, String> enc = new HashMap<String, String>();
        enc.put("A", "enc-1");
        enc.put("B", "enc-2");
        enc.put("C", "enc-1");
        // D has no encounter
        List<List<Annotation> > groups = MlServiceProcessor.groupByEncounter(Arrays.asList(a, b, c, d), enc);
        assertEquals(3, groups.size());
        assertEquals(Arrays.asList(a, c), groups.get(0));
        assertEquals(Arrays.asList(b), groups.get(1));
        assertEquals(Arrays.asList(d), groups.get(2));
        assertSame(a, groups.get(0).get(0));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=MlServiceProcessorMatchIntakeTest -DfailIfNoTests=false`
Expected: compilation error — helpers undefined.

- [ ] **Step 3: Implement**

In `MlServiceProcessor.java` add imports `java.util.HashMap`, `java.util.LinkedHashMap`, `java.util.Map`. Replace `runMatchProspects` (lines 709-803, through its closing `return MlServiceJobOutcome.ok(annotationIds); }`) with:

```java
    /** Package-private seam so tests can substitute a Shepherd whose checked commit fails. */
    Shepherd openShepherd(String action) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + action);
        return shep;
    }

    /** Spec §4.1 step 3: D's parameters minus the keys a run sets for itself. Pure. */
    static JSONObject matchIntakeParams(JSONObject parentParams) {
        JSONObject p = (parentParams == null) ? new JSONObject() : new JSONObject(parentParams.toString());
        p.remove("ibeis.detection");          // detection gauges must not count match tasks
        p.remove(Task.PARAM_RUN_ID);
        p.remove(Task.PARAM_V2_MATCH);
        p.remove(Task.PARAM_OPTION);
        p.remove("ibeis.identification");
        return p;
    }

    /** Spec §4.1 step 2: one group per encounter in first-seen order; no-encounter annotations form their own group. Pure. */
    static List<List<Annotation> > groupByEncounter(List<Annotation> anns, Map<String, String> annIdToEncId) {
        Map<String, List<Annotation> > groups = new LinkedHashMap<String, List<Annotation> >();
        for (Annotation ann : anns) {
            String enc = annIdToEncId.get(ann.getId());
            String key = (enc == null) ? "\u0000no-encounter" : enc;
            List<Annotation> g = groups.get(key);
            if (g == null) {
                g = new ArrayList<Annotation>();
                groups.put(key, g);
            }
            g.add(ann);
        }
        return new ArrayList<List<Annotation> >(groups.values());
    }

    // per-group recovery record (spec §4.1 step 4)
    private static final class GroupRecord {
        final IA.IntakeOutcome outcome;
        boolean committed = false;

        GroupRecord(IA.IntakeOutcome outcome) {
            this.outcome = outcome;
        }
    }

    private static String annotationIdList(List<Annotation> anns) {
        List<String> ids = new ArrayList<String>();
        for (Annotation a : anns) ids.add(a.getId());
        return ids.toString();
    }

    /**
     * Spec §4.1: every default identification option runs for each encounter's annotations
     * through the shared intake. {@code matchConfig} keeps its gate / embedding-presence roles
     * upstream (waitAndRunMatchInternal); it no longer selects the algorithm.
     */
    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            markTaskCompleted(taskId);
            return MlServiceJobOutcome.ok(new ArrayList<String>());
        }
        List<GroupRecord> processed = new ArrayList<GroupRecord>();
        String failureCode = null;
        String failureMessage = null;
        MlServiceJobOutcome result = null;
        Shepherd shep = openShepherd("runMatchProspects");
        try {
            shep.beginDBTransaction();
            List<Annotation> anns = new ArrayList<Annotation>();
            for (String annId : annotationIds) {
                Annotation ann = shep.getAnnotation(annId);
                if (ann != null) anns.add(ann);
            }
            if (anns.isEmpty()) {
                Task task = Task.load(taskId, shep);
                markTaskDroppedStale(shep, task, "annotations missing");
                shep.commitDBTransaction();
                return MlServiceJobOutcome.stale("annotations missing");
            }
            Task parent = Task.load(taskId, shep);
            if (parent == null) {   // as before: an orphan root when the detection task is gone
                parent = new Task(taskId);
                shep.getPM().makePersistent(parent);
            }
            Map<String, String> annToEnc = new HashMap<String, String>();
            for (Annotation ann : anns) {
                Encounter enc = ann.findEncounter(shep);
                annToEnc.put(ann.getId(), (enc == null) ? null : enc.getCatalogNumber());
            }
            List<List<Annotation> > groups = groupByEncounter(anns, annToEnc);
            JSONObject inherited = matchIntakeParams(parent.getParameters());
            int noWorkGroups = 0;
            for (List<Annotation> group : groups) {
                IA.IntakeOutcome o = IA.intakeAnnotationsUnder(shep, group, parent, inherited, false);
                if (o == null) continue;
                GroupRecord rec = new GroupRecord(o);
                processed.add(rec);
                if (o.persistFailed) {
                    failureCode = "MATCH_PERSIST";
                    failureMessage = "could not persist the identification run for annotations " +
                        annotationIdList(group);
                    break;
                }
                if (o.noWork) noWorkGroups++;
                // one checked commit per group: a later group's failure never rolls back an
                // earlier group's vector leaves
                if (!shep.commitDBTransactionWithStatus()) {
                    failureCode = "MATCH_PERSIST";
                    failureMessage = "group commit failed for identification run " + o.umbrella.getId();
                    break;
                }
                rec.committed = true;
                shep.beginDBTransaction();
            }
            if (failureCode == null) {
                if (noWorkGroups == groups.size()) {
                    markTaskError(parent, "INVALID_MATCH_CONFIG", "no default identification options");
                    shep.commitDBTransaction();
                    result = MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
                        "no default identification options");
                } else {
                    result = MlServiceJobOutcome.ok(annotationIds);
                }
            }
        } catch (Exception ex) {
            failureCode = "MATCH";
            failureMessage = "identification intake failed: " + ex.getMessage();
            ex.printStackTrace();
        } finally {
            shep.rollbackAndClose();   // rolls back whatever the failed group left open
        }
        if (failureCode != null) {
            recoverMatchFailure(taskId, processed, failureCode, failureMessage);
            return MlServiceJobOutcome.persistError(failureCode, failureMessage);
        }
        // PairX inspection-image generation is intentionally OFF the synchronous match path.
        // The UI lazy-fetches each prospect's inspection asset via
        // POST /api/v3/match-inspection/{mrId}/{annId} when the inspector opens.
        return result;
    }

    /**
     * Spec §4.1 step 5. With a fresh Shepherd: for every processed group whose commit did not
     * succeed but whose run was persisted by the intake core, mark every option task whose job
     * was not successfully published (every vector option; HotSpotter options whose publication
     * failed) error MATCH_PERSIST, and the umbrella too unless it is itself a published option;
     * leave published HotSpotter options alone (their job is durable and will complete them).
     * Mark the detection/extraction task with {@code code}. Groups whose commit succeeded are
     * untouched. The recovery commit is checked; on failure the ids are logged.
     */
    private void recoverMatchFailure(String parentTaskId, List<GroupRecord> processed, String code,
        String message) {
        Shepherd shep = openShepherd("runMatchProspects.recover");
        List<String> marked = new ArrayList<String>();
        try {
            shep.beginDBTransaction();
            for (GroupRecord rec : processed) {
                if (rec.committed || rec.outcome.persistFailed) continue;   // durable, or nothing durable
                IA.IntakeOutcome o = rec.outcome;
                Task umbrella = Task.load(o.umbrella.getId(), shep);
                if (umbrella == null) continue;
                boolean umbrellaIsPublishedOption = false;
                for (IA.OptionResult r : o.options) {
                    if (r.published && !r.vector) {
                        if (r.task.getId().equals(umbrella.getId())) umbrellaIsPublishedOption = true;
                        continue;
                    }
                    Task opt = Task.load(r.task.getId(), shep);
                    if ((opt == null) || opt.statusInEndState()) continue;
                    markTaskError(opt, "MATCH_PERSIST", message);
                    marked.add(opt.getId());
                }
                if (!umbrellaIsPublishedOption && !umbrella.statusInEndState()) {
                    markTaskError(umbrella, "MATCH_PERSIST", message);
                    marked.add(umbrella.getId());
                }
            }
            Task parent = Task.load(parentTaskId, shep);
            markTaskError(parent, code, message);
            if (!shep.commitDBTransactionWithStatus()) {
                System.out.println("ERROR: MlServiceProcessor.runMatchProspects() recovery commit failed; " +
                    "tasks left pending: " + marked + "; parent " + parentTaskId + "; " + message);
            }
        } catch (Exception ex) {
            System.out.println("ERROR: MlServiceProcessor.runMatchProspects() recovery threw " + ex +
                "; tasks: " + marked + "; parent " + parentTaskId);
            ex.printStackTrace();
        } finally {
            shep.rollbackAndClose();
        }
    }
```

Delete the old `runMatchProspects` body entirely, including its `mlServiceV2Match` and `ibeis.identification` stamping (intake now stores the option as `ibeis.identification` on each option task, §4.1 "Method header"). Keep `defaultMatchConfig` and `activeConfigs` (still the gate input).

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='MlServiceProcessorMatchIntakeTest,MlServiceProcessorGateTest,MlServiceProcessorTest' -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/ia/MlServiceProcessor.java src/test/java/org/ecocean/ia/MlServiceProcessorMatchIntakeTest.java
git commit -m "ml-service match step runs every default option per encounter (#1787)

runMatchProspects groups annotations by encounter and hands each group to
IA.intakeAnnotationsUnder beneath the detection/extraction task, with one
checked commit per group and a recovery pass that marks unpublished option
tasks and the umbrella MATCH_PERSIST when a group's commit fails (spec §4.1).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** against §4.1 steps 1-6 (especially the recovery exemption for a published single-HotSpotter umbrella and the "nothing durable" skip for `persistFailed`).

---

### Task 8: WildbookIAM forced-id alignment, eligibility helper, forced image registration

**Files:**
- Modify: `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java` (`WbiaRegisterRequest` lines 506-553; `registerOneByDto` lines 934-1000; new methods after `registerImageIfMissing`)
- Modify: `src/main/java/org/ecocean/StartupWildbook.java` (`loadWbiaRegisterDto` lines 782-806)
- Test: `src/test/java/org/ecocean/ia/plugin/WildbookIAMForcedIdTest.java` (new)

**Interfaces:**
- Consumes: `Util.isUUID(String)` (exists, null-safe), `IBEISIA.validForIdentification(Annotation)` (exists), `registerImageIfMissing` / `buildForcedRequestMap(dto, id)` / `validateForcedResponse` (exist).
- Produces:
  - `WbiaRegisterRequest.forcedId` (`public final String`) = `annotationAcmId` when non-blank, else `annotationId`.
  - `registerOneByDto` checks presence, builds the request and validates the response with `dto.forcedId` only.
  - `public static String wbiaEligibilityReason(Annotation ann)` — null when eligible, else the reason text.
  - `public boolean registerImageForced(MediaAsset ma)` — true when WBIA holds the image under `ma.getAcmId()` after the call.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/ia/plugin/WildbookIAMForcedIdTest.java`:

```java
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.RestClient;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.ia.IA;
import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome;
import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

/** Spec §4.2.5: the poller and the identify-time registration force the same identifier end to end. */
class WildbookIAMForcedIdTest {

    static final String ANN_ID = "11111111-1111-1111-1111-111111111111";
    static final String ACM_ID = "22222222-2222-2222-2222-222222222222";
    static final String MA_ACM = "33333333-3333-3333-3333-333333333333";

    static WbiaRegisterRequest dto(String annotationId, String acmId) {
        return new WbiaRegisterRequest(annotationId, acmId, MA_ACM, new int[] { 1, 2, 3, 4 },
            0.0d, "guitarfish", "____", "https://x/img.jpg", null, null, null);
    }

    static JSONObject echo(String uuid) {
        return new JSONObject().put("status", new JSONObject().put("success", true))
            .put("response", new JSONArray().put(WildbookIAM.toFancyUUID(uuid)));
    }

    interface Body { void run() throws Exception; }

    static Answer<JSONObject> reply(final JSONObject r) {
        return inv -> r;
    }

    // IA.getProperty answers the two WBIA endpoints (plain mock); WildbookIAM is a PARTIAL static
    // mock through a custom Answer (the two id-list fetches are answered, everything else —
    // toFancyUUID, buildForcedRequestMap, validateForcedResponse, validMediaAsset — runs real);
    // RestClient.post is answered by `post`; the query cache is a plain mock.
    static void withWbia(List<String> knownAnnots, List<String> knownImages, Answer<JSONObject> post, Body body)
    throws Exception {
        try (MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<WildbookIAM> iam = mockStatic(WildbookIAM.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("iaAnnotationIdsStrict")) return knownAnnots;
                 if (m.equals("iaImageIdsStrict")) return knownImages;
                 return inv.callRealMethod();
             });
             MockedStatic<RestClient> rest = mockStatic(RestClient.class);
             MockedStatic<QueryCacheFactory> qc = mockStatic(QueryCacheFactory.class)) {
            ia.when(() -> IA.getProperty(anyString(), eq("IBEISIARestUrlAddAnnotations"))).thenReturn("http://wbia/api/annot/json/");
            ia.when(() -> IA.getProperty(anyString(), eq("IBEISIARestUrlAddImages"))).thenReturn("http://wbia/api/image/json/");
            rest.when(() -> RestClient.post(any(URL.class), any(JSONObject.class))).thenAnswer(post);
            body.run();
        }
    }

    @Test void forcedIdIsTheAcmIdWhenPresentElseTheAnnotationId() {
        assertEquals(ACM_ID, dto(ANN_ID, ACM_ID).forcedId);
        assertEquals(ANN_ID, dto(ANN_ID, null).forcedId);
        assertEquals(ANN_ID, dto(ANN_ID, "  ").forcedId);
    }

    @Test void presenceIsCheckedOnTheForcedIdOnly() throws Exception {
        WildbookIAM plugin = new WildbookIAM("context0");
        // present under the acmId: already registered, no POST
        withWbia(Arrays.asList(ACM_ID), Arrays.asList(MA_ACM), reply(echo(ACM_ID)), () -> {
            assertEquals(WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
        });
        // present only under the annotation id: NOT evidence the acmId is registered -> POST the acmId
        withWbia(Arrays.asList(ANN_ID), Arrays.asList(MA_ACM), reply(echo(ACM_ID)), () -> {
            assertEquals(WbiaRegisterOutcome.REGISTERED_OK, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
        });
    }

    @Test void postBodyCarriesTheAcmIdAndAMismatchedEchoIsRejected() throws Exception {
        WildbookIAM plugin = new WildbookIAM("context0");
        final JSONObject[] captured = new JSONObject[1];
        withWbia(new ArrayList<String>(), Arrays.asList(MA_ACM), inv -> {
            captured[0] = inv.getArgument(1);
            return echo(ANN_ID);   // WBIA echoes the wrong id
        }, () -> {
            assertEquals(WbiaRegisterOutcome.RESPONSE_BAD, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
            JSONObject sent = captured[0].getJSONArray("annot_uuid_list").getJSONObject(0);
            assertEquals(ACM_ID, WildbookIAM.fromFancyUUID(sent));
        });
        withWbia(new ArrayList<String>(), Arrays.asList(MA_ACM), reply(echo(ACM_ID)), () -> {
            assertEquals(WbiaRegisterOutcome.REGISTERED_OK, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
        });
    }

    // ---- wbiaEligibilityReason ----

    static MediaAsset validAsset() throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getAcmId()).thenReturn(MA_ACM);
        when(ma.isMimeTypeMajor("image")).thenReturn(true);
        when(ma.getWidth()).thenReturn(100.0);
        when(ma.getHeight()).thenReturn(100.0);
        when(ma.webURL()).thenReturn(new URL("http://x/img.jpg"));
        when(ma.isValidImageForIA()).thenReturn(null);
        return ma;
    }

    static Annotation validAnnotation(MediaAsset ma) {
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn(ANN_ID);
        when(ann.getAcmId()).thenReturn(ACM_ID);
        when(ann.getMediaAsset()).thenReturn(ma);
        return ann;
    }

    @Test void eligibilityReasonIsNullForAWellFormedPendingAnnotation() throws Exception {
        MediaAsset ma = validAsset();
        Annotation ann = validAnnotation(ma);
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(true);
            assertNull(WildbookIAM.wbiaEligibilityReason(ann));
        }
    }

    @Test void eligibilityReasonNamesEachFailure() throws Exception {
        assertEquals("null annotation", WildbookIAM.wbiaEligibilityReason(null));
        Annotation noAsset = mock(Annotation.class);
        when(noAsset.getId()).thenReturn(ANN_ID);
        assertEquals("missing media asset", WildbookIAM.wbiaEligibilityReason(noAsset));

        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(false);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(validAsset())).startsWith("validForIdentification"));
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(true);

            MediaAsset corrupt = validAsset();
            when(corrupt.isValidImageForIA()).thenReturn(Boolean.FALSE);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(corrupt)).startsWith("MediaAsset.isValidImageForIA"));

            MediaAsset notImage = validAsset();
            when(notImage.isMimeTypeMajor("image")).thenReturn(false);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(notImage)).startsWith("MediaAsset failed validMediaAsset"));

            Annotation badAcm = validAnnotation(validAsset());
            when(badAcm.getAcmId()).thenReturn("not-a-uuid");
            assertTrue(WildbookIAM.wbiaEligibilityReason(badAcm).startsWith("annotation acmId"));

            MediaAsset noAcm = validAsset();
            when(noAcm.getAcmId()).thenReturn(null);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(noAcm)).startsWith("media asset acmId"));
        }
    }

    // ---- registerImageForced ----

    @Test void registerImageForcedSucceedsWhenPresentOrWhenWbiaEchoesTheAcmId() throws Exception {
        WildbookIAM plugin = new WildbookIAM("context0");
        MediaAsset ma = validAsset();
        withWbia(new ArrayList<String>(), Arrays.asList(MA_ACM), reply(null), () -> {
            assertTrue(plugin.registerImageForced(ma), "already present: no POST needed");
        });
        withWbia(new ArrayList<String>(), new ArrayList<String>(), reply(echo(MA_ACM)), () -> {
            assertTrue(plugin.registerImageForced(ma));
        });
        withWbia(new ArrayList<String>(), new ArrayList<String>(), reply(echo(ACM_ID)), () -> {
            assertFalse(plugin.registerImageForced(ma), "a mismatched echo is a failure");
        });
        MediaAsset noAcm = validAsset();
        when(noAcm.getAcmId()).thenReturn(null);
        assertFalse(plugin.registerImageForced(noAcm));
        assertFalse(plugin.registerImageForced(null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=WildbookIAMForcedIdTest -DfailIfNoTests=false`
Expected: compilation error — `forcedId`, `wbiaEligibilityReason`, `registerImageForced` undefined.

- [ ] **Step 3: Implement**

In `WbiaRegisterRequest` add the field and set it in the 11-arg constructor:

```java
        /**
         * Issue #1787 (spec §4.2.5): the ONE identifier every registration path forces —
         * the annotation's acmId when it has one (what IBEISIA.sendIdentify asks WBIA
         * about), else its id. Presence, request and validation all use this.
         */
        public final String forcedId;
```
and in the constructor body: `this.forcedId = Util.stringExists(annotationAcmId) ? annotationAcmId : annotationId;`

In `registerOneByDto` replace the presence check

```java
        if (known.contains(dto.annotationId) ||
            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
        }
```
with
```java
        // issue #1787: presence under the forced id only. Presence of the annotation id is
        // never evidence that the acmId is registered (the two differ on pre-fix rows).
        if (known.contains(dto.forcedId)) {
            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
        }
```
replace `HashMap<String, ArrayList> map = buildForcedRequestMap(dto);` with `HashMap<String, ArrayList> map = buildForcedRequestMap(dto, dto.forcedId);` and `validateForcedResponse(dto.annotationId, rtn);` with `validateForcedResponse(dto.forcedId, rtn);`. Update the javadoc's Phase 1 sentence to say "the already-present check, the forced-id POST and the validation all on `dto.forcedId`".

After `registerImageIfMissing` add:

```java
    /**
     * Issue #1787 (spec §4.2.5): the eligibility chain shared by the background registrar
     * (StartupWildbook) and the identify-time registration of pending annotations. Returns
     * null when the annotation can be registered under its acmId, else the reason.
     */
    public static String wbiaEligibilityReason(Annotation ann) {
        if (ann == null) return "null annotation";
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null) return "missing media asset";
        if (!Util.stringExists(ann.getId())) return "annotation has no id";
        if (!IBEISIA.validForIdentification(ann))
            return "validForIdentification returned false (bbox/iaClass/etc.)";
        if ((ma.isValidImageForIA() != null) && !ma.isValidImageForIA())
            return "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
        if (!validMediaAsset(ma)) return "MediaAsset failed validMediaAsset (mime/dims/url)";
        if (!Util.isUUID(ann.getAcmId())) return "annotation acmId is not a uuid: " + ann.getAcmId();
        if (!Util.isUUID(ma.getAcmId())) return "media asset acmId is not a uuid: " + ma.getAcmId();
        return null;
    }

    /**
     * Issue #1787 (spec §4.2.5): register one image under its acmId (forced). Builds the request
     * the way the registrar does ({@link #registerImageIfMissing}: forces
     * {@code image_uuid = MediaAsset.acmId}, validates the acknowledgement, treats "already
     * present" as success). Replaces {@link #sendMediaAssetsForceId} for this purpose, which
     * forces {@code MediaAsset.getUUID()} while checking presence by acmId. Returns true when
     * WBIA holds the image under {@code ma.getAcmId()} after the call.
     */
    public boolean registerImageForced(MediaAsset ma) {
        if ((ma == null) || !Util.stringExists(ma.getAcmId())) return false;
        org.joda.time.DateTime dt = ma.getDateTime();
        WbiaRegisterRequest dto = new WbiaRegisterRequest("image-only", null, ma.getAcmId(), null, 0.0d,
            null, null, mediaAssetToUri(ma), ma.getLatitude(), ma.getLongitude(),
            (dt == null) ? null : Long.valueOf(dt.getMillis()));
        WbiaRegisterOutcome out = registerImageIfMissing(dto);
        return (out == WbiaRegisterOutcome.REGISTERED_OK) ||
               (out == WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT);
    }
```

In `StartupWildbook.loadWbiaRegisterDto` replace the eligibility chain (from `org.ecocean.media.MediaAsset ma = ann.getMediaAsset();` through the `validMediaAsset` branch, lines 782-800) with:

```java
            org.ecocean.media.MediaAsset ma = ann.getMediaAsset();
            // Issue #1787: one eligibility chain shared with the identify-time registration
            // (WildbookIAM.wbiaEligibilityReason). Any failure is permanent for this
            // annotation under its current state, so park it.
            String reason = org.ecocean.ia.plugin.WildbookIAM.wbiaEligibilityReason(ann);
```
(keep the `if (reason != null) { ... park ... }` block and everything after it; `ma` is non-null whenever `reason` is null). Update the javadoc of `registerOneAnnotationWithWbia` ("Ineligible annotations ...") to point at the helper.

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='WildbookIAMForcedIdTest,WildbookIAMRegisterTest,WildbookIAMImagePhase0Test,WbiaRegisterRequestImageFieldsTest' -DfailIfNoTests=false`
Expected: PASS. (`WildbookIAMRegisterTest.defaultBuildForcedRequestMapStillForcesTheAnnotationId` tests the 1-arg builder, which is unchanged and now unused by the poller; leave it.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/StartupWildbook.java src/test/java/org/ecocean/ia/plugin/WildbookIAMForcedIdTest.java
git commit -m "WBIA registration forces one identifier end to end (#1787)

WbiaRegisterRequest.forcedId (acmId, else id) drives the registrar's
presence check, request and validation. The poller's eligibility chain
becomes WildbookIAM.wbiaEligibilityReason, shared with identify-time
registration, and registerImageForced registers an image under its acmId
through the validated builder (spec §4.2.5).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** (confirm the poller's parked-reason semantics are unchanged for every case the old chain covered, and that `sendMediaAssetsForceId` has no remaining caller that this change should have switched).

---

### Task 9: Role-aware registration at identify time; `iaCheckMissing` forced repair

**Files:**
- Modify: `src/main/java/org/ecocean/identity/IBEISIA.java` (`iaCheckMissing` lines 790-836; `beginIdentifyAnnotations` lines 863-920; `sendAnnotationsAsNeeded` ~line 3732)
- Test: `src/test/java/org/ecocean/identity/IBEISIASendAnnotationsAsNeededTest.java` (new)

**Interfaces:**
- Consumes: `WildbookIAM.wbiaEligibilityReason`, `registerImageForced`, `sendAnnotationForcedByAcmId`, `iaAnnotationIdsStrict` (Task 8 / existing); the existing single-list `sendAnnotationsAsNeeded(ArrayList<Annotation>, Shepherd)` (kept for its other callers).
- Produces (static on `IBEISIA`):
  - `public static ArrayList<Annotation> sendAnnotationsAsNeeded(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, Shepherd myShepherd, JSONObject report) throws IOException` — `qanns` is EVERY query annotation (unfiltered): pending ones (`wbiaRegistered == FALSE`) are registered under their acmId first, the rest go through today's `validForIdentification` filter and non-forced flow; returns the pruned target list (null when `tanns` is null); throws for a pending query that cannot be registered (for any reason); a pending target that cannot be registered (for any reason, including an id-list fetch failure) is dropped. `report` (nullable) receives `forcedRegistered`, `droppedTargets`, `legacy`.
  - `public static ArrayList<Annotation> sendAnnotationsAsNeeded(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, Shepherd myShepherd) throws IOException` — the §4.2.5 signature; delegates with a null report.
  - `static String registerPendingAnnotation(WildbookIAM plugin, Annotation ann, Set<String> knownAcmIds, Shepherd myShepherd)` (package-private) — null on success (flag set TRUE), else the reason.
  - `iaCheckMissing` returns true only when the forced branch newly registered at least one annotation and every forced attempt succeeded; the legacy branch is unchanged.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/identity/IBEISIASendAnnotationsAsNeededTest.java`:

```java
package org.ecocean.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.ia.IA;
import org.ecocean.ia.plugin.WildbookIAM;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Spec §4.2.5: pending annotations are registered under their acmId at identify time. */
class IBEISIASendAnnotationsAsNeededTest {

    static final String ACM_Q = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    static final String ACM_T = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    static final String ACM_N = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    static final String MA_UUID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    /** Records forced calls; answers the legacy id lists. */
    static final class FakePlugin extends WildbookIAM {
        final List<String> imageCalls = new ArrayList<String>();
        final List<String> annCalls = new ArrayList<String>();
        boolean imageOk = true;
        boolean annOk = true;
        List<String> legacyKnown = new ArrayList<String>();

        FakePlugin() { super("context0"); }

        @Override public boolean registerImageForced(MediaAsset ma) {
            imageCalls.add(ma.getAcmId());
            return imageOk;
        }

        @Override public boolean sendAnnotationForcedByAcmId(Annotation ann, Shepherd sh) {
            annCalls.add(ann.getAcmId());
            return annOk;
        }

        @Override public List<String> iaAnnotationIds() { return legacyKnown; }
        @Override public List<String> iaImageIds() { return legacyKnown; }
    }

    static Annotation ann(String id, String acmId, Boolean flag, MediaAsset ma) {
        Annotation a = mock(Annotation.class);
        when(a.getId()).thenReturn(id);
        when(a.getAcmId()).thenReturn(acmId);
        when(a.getWbiaRegistered()).thenReturn(flag);
        when(a.getMediaAsset()).thenReturn(ma);
        return a;
    }

    static MediaAsset asset(String acmId) {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getAcmId()).thenReturn(acmId);
        when(ma.getUUID()).thenReturn(MA_UUID);
        return ma;
    }

    static Shepherd shepherd() {
        Shepherd sh = mock(Shepherd.class);
        when(sh.getContext()).thenReturn("context0");
        return sh;
    }

    interface Body { void run() throws Exception; }

    // Partial static mocks through custom Answers: IBEISIA.getPluginInstance -> the fake and
    // validForIdentification -> true (the legacy filter; the real one needs bbox features);
    // WildbookIAM.iaAnnotationIdsStrict -> the test's list (null = the fetch throws IOException)
    // and wbiaEligibilityReason -> the test's reason (null = eligible); everything else runs real.
    // IA is a plain mock: getProperty -> null, so the legacy __sendAnnotations throws and is
    // swallowed exactly as in production without WBIA configured.
    static void withPlugin(FakePlugin plugin, List<String> knownForced, String eligibilityReason, Body body)
    throws Exception {
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("getPluginInstance")) return plugin;
                 if (m.equals("validForIdentification")) return true;
                 return inv.callRealMethod();
             });
             MockedStatic<WildbookIAM> iam = mockStatic(WildbookIAM.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("iaAnnotationIdsStrict")) {
                     if (knownForced == null) throw new IOException("WBIA unreachable");
                     return knownForced;
                 }
                 if (m.equals("wbiaEligibilityReason")) return eligibilityReason;
                 return inv.callRealMethod();
             });
             MockedStatic<IA> ia = mockStatic(IA.class)) {
            body.run();
        }
    }

    @Test void pendingQueryIsRegisteredUnderItsAcmIdAndFlaggedTrue() throws Exception {
        FakePlugin plugin = new FakePlugin();
        MediaAsset ma = asset(MA_UUID);
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, ma);
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), new ArrayList<Annotation>(), sh, report);
            assertTrue(targets.isEmpty());
            assertEquals(Arrays.asList(MA_UUID), plugin.imageCalls);
            assertEquals(Arrays.asList(ACM_Q), plugin.annCalls);
            verify(q).setWbiaRegistered(Boolean.TRUE);
            assertEquals("q", report.getJSONArray("forcedRegistered").getString(0));
            assertEquals(0, report.getJSONArray("droppedTargets").length());
        });
    }

    @Test void pendingQueryThatCannotBeRegisteredFailsTheJob() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.annOk = false;
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            assertThrows(IOException.class, () -> IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), null, sh, null));
            verify(q, never()).setWbiaRegistered(Boolean.TRUE);
        });
        // an ineligible pending query fails the job even when a valid legacy query is in the same call
        FakePlugin eligible = new FakePlugin();
        eligible.legacyKnown = Arrays.asList(ACM_N);
        Annotation legacyQuery = ann("n", ACM_N, null, asset(MA_UUID));
        withPlugin(eligible, new ArrayList<String>(), "missing media asset", () -> {
            assertThrows(IOException.class, () -> IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(legacyQuery, q)), null, sh, null));
            assertTrue(eligible.annCalls.isEmpty());
        });
    }

    @Test void idListFetchFailureFailsAPendingQueryButOnlyDropsAPendingTarget() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation legacyQuery = ann("n", ACM_N, Boolean.TRUE, asset(MA_UUID));
        Annotation t = ann("t", ACM_T, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, null /* the fetch throws */, null, () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(legacyQuery)), new ArrayList<Annotation>(Arrays.asList(t)), sh, report);
            assertTrue(targets.isEmpty(), "the pending target is dropped, the job goes on");
            assertEquals("t", report.getJSONArray("droppedTargets").getString(0));
            Annotation pendingQuery = ann("p", ACM_Q, Boolean.FALSE, asset(MA_UUID));
            assertThrows(IOException.class, () -> IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(pendingQuery)), null, sh, null));
        });
    }

    @Test void threeArgumentOverloadDelegatesWithoutAReport() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation t = ann("t", ACM_T, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(), new ArrayList<Annotation>(Arrays.asList(t)), sh);
            assertEquals(Arrays.asList(t), targets);
            assertEquals(Arrays.asList(ACM_T), plugin.annCalls);
        });
    }

    @Test void ineligiblePendingTargetIsDroppedAndTheJobContinues() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation q = ann("q", ACM_N, null, asset(MA_UUID));          // legacy query, already known
        Annotation t = ann("t", ACM_T, Boolean.FALSE, null);            // pending target without an asset
        Annotation keep = ann("k", ACM_N, Boolean.TRUE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), "missing media asset", () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), new ArrayList<Annotation>(Arrays.asList(t, keep)), sh, report);
            assertEquals(Arrays.asList(keep), targets);
            assertEquals("t", report.getJSONArray("droppedTargets").getString(0));
            assertTrue(plugin.annCalls.isEmpty());
        });
    }

    @Test void pendingAnnotationAlreadyPresentUnderItsAcmIdIsFlaggedWithoutAPost() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, Arrays.asList(ACM_Q), null, () -> {
            IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(Arrays.asList(q)), null, sh, null);
            assertTrue(plugin.annCalls.isEmpty());
            assertTrue(plugin.imageCalls.isEmpty());
            verify(q).setWbiaRegistered(Boolean.TRUE);
        });
    }

    @Test void assetWithoutAcmIdIsMintedFromItsUuidBeforeTheImageIsForced() throws Exception {
        FakePlugin plugin = new FakePlugin();
        MediaAsset ma = mock(MediaAsset.class);   // stateful: setAcmId is visible to getAcmId
        final String[] acm = { null };
        when(ma.getAcmId()).thenAnswer(inv -> acm[0]);
        when(ma.getUUID()).thenReturn(MA_UUID);
        doAnswer(inv -> {
            acm[0] = inv.getArgument(0);
            return null;
        }).when(ma).setAcmId(anyString());
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, ma);
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(Arrays.asList(q)), null, sh, null);
            verify(ma).setAcmId(MA_UUID);
            assertEquals(Arrays.asList(MA_UUID), plugin.imageCalls, "the image is forced under the minted acmId");
            assertEquals(Arrays.asList(ACM_Q), plugin.annCalls);
        });
    }

    @Test void legacyAnnotationsTakeTodaysPathUntouched() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation n = ann("n", ACM_N, null, asset(MA_UUID));
        Annotation t = ann("t", ACM_N, Boolean.TRUE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(n)), new ArrayList<Annotation>(Arrays.asList(t)), sh, report);
            assertEquals(Arrays.asList(t), targets);
            assertTrue(plugin.annCalls.isEmpty());
            assertEquals(2, report.getJSONObject("legacy").getInt("numAnnotsTotal"));
            verify(n, never()).setWbiaRegistered(any());
        });
    }

    @Test void pendingOnlyRegistrationWorksAgainstAnEmptyWbia() throws Exception {
        FakePlugin plugin = new FakePlugin();   // legacyKnown empty: the legacy path would throw if reached
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Annotation t = ann("t", ACM_T, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), new ArrayList<Annotation>(Arrays.asList(t)), sh, null);
            assertEquals(Arrays.asList(t), targets);
            assertEquals(Arrays.asList(ACM_Q, ACM_T), plugin.annCalls);
        });
    }

    @Test void nullTargetsStayNull() throws Exception {
        Shepherd sh = shepherd();
        withPlugin(new FakePlugin(), new ArrayList<String>(), null, () -> {
            assertNull(IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(), null, sh, null));
        });
    }

    // ---- iaCheckMissing ----

    static JSONObject missing(String acmId) {
        return new JSONObject()
            .put("status", new JSONObject().put("code", 600).put("success", false).put("message", "missing"))
            .put("response", new JSONObject().put("missing_annot_uuid_list",
                new JSONArray().put(WildbookIAM.toFancyUUID(acmId))));
    }

    @Test void iaCheckMissingForcedRepairRetriesOnlyWhenEveryForcedAttemptSucceeded() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation pending = ann("p", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        when(sh.getAnnotationsWithACMId(ACM_Q)).thenReturn(new ArrayList<Annotation>(Arrays.asList(pending)));
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            assertTrue(IBEISIA.iaCheckMissing(missing(ACM_Q), "context0", sh));
            assertEquals(Arrays.asList(ACM_Q), plugin.annCalls);
        });
        FakePlugin failing = new FakePlugin();
        failing.annOk = false;
        withPlugin(failing, new ArrayList<String>(), null, () -> {
            assertFalse(IBEISIA.iaCheckMissing(missing(ACM_Q), "context0", sh));
        });
    }

    @Test void iaCheckMissingLegacyBranchIsUnchanged() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation legacy = ann("n", ACM_N, null, asset(MA_UUID));
        Shepherd sh = shepherd();
        when(sh.getAnnotationsWithACMId(ACM_N)).thenReturn(new ArrayList<Annotation>(Arrays.asList(legacy)));
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            // IA.getProperty is a plain static mock here -> null -> __sendAnnotations throws and is
            // swallowed, exactly as before: no retry, no forced call
            assertFalse(IBEISIA.iaCheckMissing(missing(ACM_N), "context0", sh));
            assertTrue(plugin.annCalls.isEmpty());
        });
        assertFalse(IBEISIA.iaCheckMissing(null, "context0", sh));
        assertFalse(IBEISIA.iaCheckMissing(new JSONObject().put("status", new JSONObject().put("code", 200)), "context0", sh));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=IBEISIASendAnnotationsAsNeededTest -DfailIfNoTests=false`
Expected: compilation error — no 4-argument `sendAnnotationsAsNeeded`.

- [ ] **Step 3: Implement**

In `IBEISIA.java`, directly before the existing single-list `sendAnnotationsAsNeeded(ArrayList<Annotation> anns, Shepherd myShepherd)` (~line 3732) add:

```java
    /**
     * Issue #1787 (spec §4.2.5): role-aware registration at identify time. Annotations whose
     * WBIA registration is pending ({@code wbiaRegistered == FALSE}, set only by ml-service
     * persistence) are registered under their acmId (forced) BEFORE the legacy non-forced flow
     * runs for the rest, so WBIA can never mint a new uuid for them. A pending QUERY that
     * cannot be registered fails the job (IOException -> beginIdentifyAnnotations returns
     * success=false -> SEND_FAILED + bounded requeue); a pending TARGET that cannot be
     * registered is dropped from the returned target list. Returns the pruned targets (null
     * stays null). {@code report} (nullable) receives forcedRegistered / droppedTargets / legacy.
     */
    /** The §4.2.5 signature; delegates with no report. */
    public static ArrayList<Annotation> sendAnnotationsAsNeeded(ArrayList<Annotation> qanns,
        ArrayList<Annotation> tanns, Shepherd myShepherd)
    throws IOException {
        return sendAnnotationsAsNeeded(qanns, tanns, myShepherd, null);
    }

    public static ArrayList<Annotation> sendAnnotationsAsNeeded(ArrayList<Annotation> qanns,
        ArrayList<Annotation> tanns, Shepherd myShepherd, JSONObject report)
    throws IOException {
        if (report == null) report = new JSONObject();
        String context = myShepherd.getContext();
        WildbookIAM plugin = getPluginInstance(context);
        ArrayList<Annotation> legacy = new ArrayList<Annotation>();
        JSONArray forced = new JSONArray();
        JSONArray dropped = new JSONArray();
        Set<String> knownAcmIds = null;   // fetched once, only when a pending annotation is seen
        if (qanns != null) {
            for (Annotation q : qanns) {
                if (!Boolean.FALSE.equals(q.getWbiaRegistered())) {
                    // registered / legacy queries: today's filter, today's non-forced flow
                    if (validForIdentification(q, context)) legacy.add(q);
                    continue;
                }
                // a pending QUERY is registered under its acmId or the job fails (any failure)
                if (knownAcmIds == null)
                    knownAcmIds = new HashSet<String>(WildbookIAM.iaAnnotationIdsStrict(context));
                String why = registerPendingAnnotation(plugin, q, knownAcmIds, myShepherd);
                if (why != null)
                    throw new IOException("query annotation " + q.getId() +
                            " could not be registered with WBIA: " + why);
                forced.put(q.getId());
            }
        }
        ArrayList<Annotation> keptTargets = null;
        if (tanns != null) {
            keptTargets = new ArrayList<Annotation>();
            for (Annotation t : tanns) {
                if (!Boolean.FALSE.equals(t.getWbiaRegistered())) {
                    legacy.add(t);
                    keptTargets.add(t);
                    continue;
                }
                // a pending TARGET that cannot be registered — for any reason, including an
                // id-list fetch failure — is dropped from this job; the job goes on
                String why;
                try {
                    if (knownAcmIds == null)
                        knownAcmIds = new HashSet<String>(WildbookIAM.iaAnnotationIdsStrict(context));
                    why = registerPendingAnnotation(plugin, t, knownAcmIds, myShepherd);
                } catch (Exception ex) {
                    why = ex.toString();
                }
                if (why != null) {
                    System.out.println("WARNING: IBEISIA.sendAnnotationsAsNeeded() dropping target " +
                        t.getId() + " from this job: " + why);
                    dropped.put(t.getId());
                    continue;
                }
                forced.put(t.getId());
                keptTargets.add(t);
            }
        }
        report.put("forcedRegistered", forced);
        report.put("droppedTargets", dropped);
        report.put("legacy", sendAnnotationsAsNeeded(legacy, myShepherd));   // today's flow, unchanged
        return keptTargets;
    }

    /**
     * Registers one pending annotation under its acmId. Mints the media asset's acmId from its
     * UUID when missing (the AcmIdBot convention), applies the shared eligibility chain, forces
     * the image then the annotation (skipping the POSTs when WBIA already holds the acmId), and
     * flips the flag to TRUE on the attached object. Returns null on success, else the reason.
     */
    static String registerPendingAnnotation(WildbookIAM plugin, Annotation ann,
        Set<String> knownAcmIds, Shepherd myShepherd) {
        MediaAsset ma = ann.getMediaAsset();

        if ((ma != null) && !Util.stringExists(ma.getAcmId())) ma.setAcmId(ma.getUUID());
        String reason = WildbookIAM.wbiaEligibilityReason(ann);
        if (reason != null) return reason;
        if (!knownAcmIds.contains(ann.getAcmId())) {
            try {
                if (!plugin.registerImageForced(ma))
                    return "image registration failed for media asset acmId " + ma.getAcmId();
                if (!plugin.sendAnnotationForcedByAcmId(ann, myShepherd))
                    return "WBIA did not confirm annotation uuid " + ann.getAcmId();
            } catch (IOException ex) {
                return ex.toString();
            }
            knownAcmIds.add(ann.getAcmId());
        }
        ann.setWbiaRegistered(Boolean.TRUE);
        return null;
    }
```

In `beginIdentifyAnnotations` (8-arg): delete the `ArrayList<Annotation> allAnns = new ArrayList<Annotation>();` declaration and the loop at the top of the `try` that filled it (`for (Annotation ann : qanns) { if (validForIdentification(ann, …)) allAnns.add(ann); }`) — the role-aware method applies that filter itself to non-pending queries, and a pending query with an invalid bbox must reach the forced branch (and fail the job) rather than be silently skipped. Then replace

```java
            if (tanns != null && !tanns.isEmpty()) {
                System.out.println("INFO: tanns, (matchingSet) is not null. Contains " +
                    tanns.size() + " annotations.");
                for (Annotation ann : tanns) {
                    allAnns.add(ann);
                }
            }
            Util.mark("OPT bia 4", tt);

            results.put("sendAnnotationsAsNeeded", sendAnnotationsAsNeeded(allAnns, myShepherd));
```
with
```java
            if (tanns != null && !tanns.isEmpty()) {
                System.out.println("INFO: tanns, (matchingSet) is not null. Contains " +
                    tanns.size() + " annotations.");
            }
            Util.mark("OPT bia 4", tt);

            // issue #1787: pending (ml-service) annotations are registered under their acmId
            // first; a pending query that cannot be registered throws here, which the catch
            // below turns into success=false. The identify request is built from the pruned
            // target list this returns. qanns is passed unfiltered on purpose.
            JSONObject registration = new JSONObject();
            tanns = sendAnnotationsAsNeeded(qanns, tanns, myShepherd, registration);
            results.put("sendAnnotationsAsNeeded", registration);
```

In `iaCheckMissing`, replace the `missing_annot_uuid_list` branch body (from `ArrayList<Annotation> anns = new ArrayList<Annotation>();` through the `tryAgain = true;    // it "worked"?` line) with:

```java
                ArrayList<Annotation> anns = new ArrayList<Annotation>();
                try {
                    for (int i = 0; i < list.length(); i++) {
                        String acmId = fromFancyUUID(list.getJSONObject(i));
                        ArrayList<Annotation> annsTemp = myShepherd.getAnnotationsWithACMId(acmId);
                        anns.add(annsTemp.get(0));
                    }
                } catch (Exception e) { e.printStackTrace(); }
                // issue #1787 (spec §4.2.5): pending annotations are repaired under their acmId
                // (forced); the retry decision comes from the actual outcomes, and only when at
                // least one annotation was newly registered here (its flag is TRUE afterwards,
                // so a second report of the same annotation never loops back into this branch).
                ArrayList<Annotation> legacyAnns = new ArrayList<Annotation>();
                boolean forcedAny = false;
                boolean forcedAllOk = true;
                Set<String> knownAcmIds = null;
                for (Annotation a : anns) {
                    if (!Boolean.FALSE.equals(a.getWbiaRegistered())) {
                        legacyAnns.add(a);
                        continue;
                    }
                    forcedAny = true;
                    try {
                        if (knownAcmIds == null)
                            knownAcmIds = new HashSet<String>(WildbookIAM.iaAnnotationIdsStrict(context));
                        String why = registerPendingAnnotation(getPluginInstance(context), a, knownAcmIds, myShepherd);
                        if (why != null) {
                            System.out.println("WARNING: iaCheckMissing() forced repair of " + a.getId() + " failed: " + why);
                            forcedAllOk = false;
                        }
                    } catch (IOException ex) {
                        System.out.println("WARNING: iaCheckMissing() forced repair of " + a.getId() + " threw " + ex);
                        forcedAllOk = false;
                    }
                }
                if (forcedAny && forcedAllOk) tryAgain = true;
                if (!legacyAnns.isEmpty()) {
                    // legacy branch: exactly as before (spec §3), including the never-assigned result
                    System.out.println("**** attempting to make up for missing Annotation(s): " +
                        legacyAnns.toString());
                    JSONObject srtn = null;
                    try {
                        __sendAnnotations(legacyAnns, context, myShepherd);
                    } catch (Exception ex) {}
                    System.out.println(" returned --> " + srtn);
                    if ((srtn != null) && (srtn.getJSONObject("status") != null) &&
                        srtn.getJSONObject("status").getBoolean("success")) tryAgain = true;    // it "worked"?
                }
```

`IBEISIA.java` already imports `HashSet`, `Set`, `IOException`, `MediaAsset`, `WildbookIAM`, `Util`, `JSONArray`.

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='IBEISIASendAnnotationsAsNeededTest,IBEISIAValidForIdentificationTest,IAGatewayDoIdentifyTest' -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/identity/IBEISIA.java src/test/java/org/ecocean/identity/IBEISIASendAnnotationsAsNeededTest.java
git commit -m "Register pending ml-service annotations under their acmId at identify time (#1787)

beginIdentifyAnnotations registers wbiaRegistered==FALSE annotations
through the forced path before the legacy flow: a pending query that
cannot be registered fails the job, a pending target is dropped. The
missing-annotation repair uses the same branch and its retry decision
comes from the actual outcomes (spec §4.2.5).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** (ask it to trace the `while (tryAgain)` loops in `beginIdentifyAnnotations` and `__sendAnnotations` for any new unbounded retry, and to confirm null-flag and TRUE-flag annotations see no behaviour change).

---

### Task 10: Import status across every option of the run; encounter-scoped call sites

**Files:**
- Modify: `src/main/java/org/ecocean/servlet/importer/ImportTask.java` (`statsAnnotations` lines 375-486; new helpers after `annotationIdsToEncounterIds`)
- Modify: `src/main/java/org/ecocean/Annotation.java` (`getPreferredMatchResultsTask` line 1941)
- Modify: `src/main/java/org/ecocean/Encounter.java` (`jsonForApiGet` ~line 5173)
- Test: `src/test/java/org/ecocean/servlet/importer/ImportTaskIdentificationStatusTest.java` (new)

**Interfaces:**
- Consumes: `Task.selectRun`, `Task.anyRunId`, `Task.runIdOf`, `Task.isOptionTask`, `holdsAnnotationId`, 4-arg `getPreferredMatchResultsTaskForAnnotation` (Tasks 1-2); `IA.intakeProducedNoWork` (Task 4); `Task.timedOutDueToInactivity()`, `TIMEOUT_INACTIVE_MILLIS` (exist).
- Produces (package-private static on `ImportTask`):
  - `static final class AnnotationIdStatus { final String status; final boolean skipped; }`
  - `static AnnotationIdStatus identificationStatusForAnnotation(Annotation ann, List<Task> direct, String importTaskId, Set<String> encounterAnnotationIds, boolean runAware, Shepherd myShepherd)` — §4.4 rules 1-5.
  - `static String mergeClassLabel(String existing, String iaClass)` — distinct non-blank classes, sorted, joined with ", ".
  - `static Map<String, Set<String>> encounterIdsToAnnotationIds(Collection<String> encIds, Shepherd myShepherd)` — one SQL pass over `ENCOUNTER_ANNOTATIONS`; null when the query fails.
  - `Annotation.getPreferredMatchResultsTask(Shepherd, Set<String> encounterAnnotationIds)` (public).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/ecocean/servlet/importer/ImportTaskIdentificationStatusTest.java`:

```java
package org.ecocean.servlet.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.ecocean.Annotation;
import org.ecocean.ia.Task;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Spec §4.4: one representative per option; pending > error > completed; seven-day maximum age. */
class ImportTaskIdentificationStatusTest {

    private static Annotation annot(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        a.setIAClass("guitarfish");
        return a;
    }

    private static Set<String> ids(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    private static Task umbrella(Annotation... anns) {
        Task u = new Task();
        u.setParameters(new JSONObject().put(Task.PARAM_RUN_ID, u.getId()));
        for (Annotation a : anns) u.addObject(a);
        return u;
    }

    private static Task umbrellaAsOption(Annotation... anns) {
        Task u = umbrella(anns);
        u.addParameter(Task.PARAM_OPTION, true);
        u.addParameter("ibeis.identification", new JSONObject());
        return u;
    }

    private static Task option(Task parent, boolean vector, Annotation... anns) {
        Task t = new Task(parent);
        t.addParameter(Task.PARAM_OPTION, true);
        t.addParameter("ibeis.identification", new JSONObject());
        if (vector) t.addParameter(Task.PARAM_V2_MATCH, true);
        for (Annotation a : anns) t.addObject(a);
        return t;
    }

    private static Task leaf(Task option, String status, Annotation a) {
        Task l = new Task(option);
        JSONObject p = l.getParameters();
        p.remove(Task.PARAM_OPTION);
        l.setParameters(p);
        l.addObject(a);
        if (status != null) l.setStatus(status);
        return l;
    }

    private static Shepherd shepherd() {
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(mock(PersistenceManager.class));
        return sh;
    }

    interface Body { void run() throws Exception; }

    // null stored statuses make Task.getStatus read WBIA logs; none exist -> "waiting to queue"
    private static void withNoLogs(Body body) throws Exception {
        try (MockedStatic<IdentityServiceLog> logs = mockStatic(IdentityServiceLog.class)) {
            logs.when(() -> IdentityServiceLog.loadByTaskID(anyString(), anyString(), any(Shepherd.class)))
                .thenReturn(new ArrayList<IdentityServiceLog>());
            body.run();
        }
    }

    private static ImportTask.AnnotationIdStatus status(Annotation a, List<Task> direct, Set<String> enc, Shepherd sh) {
        return ImportTask.identificationStatusForAnnotation(a, direct, "IMP1", enc, true, sh);
    }

    @Test void allOptionsCompletedReadsCompleted() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "completed", a);
        v.setStatus("completed");
        Task h = option(u, false, a);
        h.setStatus("completed");
        withNoLogs(() -> {
            ImportTask.AnnotationIdStatus s = status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd());
            assertEquals("completed", s.status);
            assertFalse(s.skipped);
        });
    }

    @Test void pendingHotspotterBeatsCompletedVector() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "completed", a);
        v.setStatus("completed");
        Task h = option(u, false, a);   // stored status null: queued at WBIA
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd()).status);
        });
    }

    @Test void errorVectorWithPendingHotspotterReadsPending() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "error", a);
        v.setStatus("error");
        Task h = option(u, false, a);   // queued at WBIA: pending beats the error
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd()).status);
        });
    }

    @Test void requeuedHotspotterIsPending() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        leaf(v, "completed", a);
        v.setStatus("completed");
        Task h = option(u, false, a);
        h.setStatus(Task.STATUS_REQUEUED);
        withNoLogs(() -> {
            assertEquals(Task.STATUS_REQUEUED, status(a, Arrays.asList(h, v, u), ids("A"), shepherd()).status);
        });
    }

    @Test void errorVectorWithCompletedHotspotterReadsError() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "error", a);
        v.setStatus("error");
        Task h = option(u, false, a);
        h.setStatus("completed");
        withNoLogs(() -> {
            assertEquals("error", status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd()).status);
        });
    }

    /** Partial vector dispatch: V_A exists, V_B was never created; the option itself represents B. */
    @Test void failedVectorOptionWithoutALeafForThisAnnotationReadsErrorThroughTheOption() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);
        Task v = option(u, true, a, b);
        leaf(v, "error", a);
        v.setStatus("error");
        Task h = option(u, false, a, b);
        Task hb = leaf(h, "completed", b);
        withNoLogs(() -> {
            assertEquals("error", status(b, Arrays.asList(hb, h, v, u), ids("A", "B"), shepherd()).status);
        });
    }

    /** _doIdentify mid fan-out: H_A exists, H_B not yet; the option represents B and reads pending. */
    @Test void hotspotterMidFanOutReadsPendingForTheAnnotationWithoutAChild() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);
        Task v = option(u, true, a, b);
        Task vb = leaf(v, "completed", b);
        v.setStatus("completed");
        Task h = option(u, false, a, b);
        leaf(h, null, a);
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(b, Arrays.asList(vb, h, v, u), ids("A", "B"), shepherd()).status);
        });
    }

    /** Single-HotSpotter umbrella U(A,B) mid fan-out next to a newer completed run for A: B's representative is U. */
    @Test void singleHotspotterUmbrellaMidFanOutStaysPendingForTheUnfannedAnnotation() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrellaAsOption(a, b);
        leaf(u, "completed", a);
        Task newer = umbrellaAsOption(a);
        newer.setStatus("completed");
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(b, Arrays.asList(u), ids("A", "B"), shepherd()).status);
            assertEquals("completed", status(a, Arrays.asList(newer, u), ids("A", "B"), shepherd()).status);
        });
    }

    @Test void noWorkUmbrellaReportsItsOwnError() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        u.setStatus("error");
        withNoLogs(() -> {
            ImportTask.AnnotationIdStatus s = status(a, Arrays.asList(u), ids("A"), shepherd());
            assertEquals("error", s.status);
            assertFalse(s.skipped);
        });
    }

    @Test void classWithoutOptionReadsCompletedAndSkipped() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);
        Task h = option(u, false, a);   // B's class had no option
        h.setStatus("completed");
        withNoLogs(() -> {
            ImportTask.AnnotationIdStatus s = status(b, Arrays.asList(u), ids("A", "B"), shepherd());
            assertEquals("completed", s.status);
            assertTrue(s.skipped);
        });
    }

    @Test void agedPendingLeafReportsError() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task h = option(u, false, a);   // null status, never completed
        long saved = Task.TIMEOUT_INACTIVE_MILLIS;
        Task.TIMEOUT_INACTIVE_MILLIS = -1L;   // everything is older than the maximum age
        try {
            withNoLogs(() -> {
                assertEquals("error", status(a, Arrays.asList(h, u), ids("A"), shepherd()).status);
                assertNull(h.getStoredStatus(), "reported only; the stored status is untouched");
            });
        } finally {
            Task.TIMEOUT_INACTIVE_MILLIS = saved;
        }
    }

    @Test void oldTreeUsesTodaysRule() throws Exception {
        Annotation a = annot("A");
        Task root = new Task();
        root.addObject(a);
        Task newest = new Task(root);
        newest.addObject(a);
        newest.setStatus("completed");
        root.setStatus("error");
        withNoLogs(() -> {
            assertEquals("completed", status(a, Arrays.asList(newest, root), ids("A"), shepherd()).status);
            assertEquals("completed", ImportTask.identificationStatusForAnnotation(a, Arrays.asList(newest, root),
                "IMP1", null, false, shepherd()).status);
        });
    }

    @Test void notRunAwareUsesTodaysRuleEvenWithRunIds() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task h = option(u, false, a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "completed", a);
        withNoLogs(() -> {
            assertEquals("completed", ImportTask.identificationStatusForAnnotation(a, Arrays.asList(vl, h, v, u),
                "IMP1", null, false, shepherd()).status);
        });
    }

    @Test void fallsBackToTheNewestUmbrellaWhenNoneIsEligibleForTheEncounterSet() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);          // spans encounters: ineligible for {A}
        Task h = option(u, false, a, b);
        h.setStatus("completed");
        withNoLogs(() -> {
            assertEquals("completed", status(a, Arrays.asList(h, u), ids("A"), shepherd()).status);
        });
    }

    @Test void mergeClassLabelSortsDistinctNonBlankClasses() {
        assertEquals("guitarfish", ImportTask.mergeClassLabel(null, "guitarfish"));
        assertEquals("guitarfish, guitarfish+rostrum", ImportTask.mergeClassLabel("guitarfish", "guitarfish+rostrum"));
        assertEquals("guitarfish, guitarfish+rostrum", ImportTask.mergeClassLabel("guitarfish+rostrum", "guitarfish"));
        assertEquals("guitarfish", ImportTask.mergeClassLabel("guitarfish", "guitarfish"));
        assertEquals("guitarfish", ImportTask.mergeClassLabel("guitarfish", " "));
        assertNull(ImportTask.mergeClassLabel(null, null));
    }

    @Test void encounterIdsToAnnotationIdsGroupsRowsAndReturnsNullOnFailure() {
        PersistenceManager pm = mock(PersistenceManager.class);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);
        Query q = mock(Query.class);
        when(pm.newQuery(eq("javax.jdo.query.SQL"), anyString())).thenReturn(q);
        List<Object> rows = new ArrayList<Object>();
        rows.add(new Object[] { "enc-1", "A" });
        rows.add(new Object[] { "enc-1", "B" });
        rows.add(new Object[] { "enc-2", "C" });
        when(q.execute()).thenReturn(rows);
        Map<String, Set<String>> m = ImportTask.encounterIdsToAnnotationIds(Arrays.asList("enc-1", "enc-2"), sh);
        assertEquals(ids("A", "B"), m.get("enc-1"));
        assertEquals(ids("C"), m.get("enc-2"));
        verify(q).closeAll();

        when(q.execute()).thenThrow(new RuntimeException("boom"));
        assertNull(ImportTask.encounterIdsToAnnotationIds(Arrays.asList("enc-1"), sh));
        assertTrue(ImportTask.encounterIdsToAnnotationIds(new ArrayList<String>(), sh).isEmpty());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=ImportTaskIdentificationStatusTest -DfailIfNoTests=false`
Expected: compilation error — `AnnotationIdStatus`, `identificationStatusForAnnotation`, `mergeClassLabel`, `encounterIdsToAnnotationIds` undefined.

- [ ] **Step 3: Implement**

In `ImportTask.java` add `import org.ecocean.ia.IA;` and `import java.util.TreeSet;`. Replace the body of `statsAnnotations` (lines 375-486) with:

```java
    public JSONObject statsAnnotations(Shepherd myShepherd) {
        JSONObject sa = new JSONObject();
        // List<Task> is ordered 'created desc'
        Map<Annotation, List<Task> > atm = this.getAnnotationTaskMap(myShepherd);
        int numTasks = 0;
        int numLatestTasks = 0;
        Map<String, Set<Task> > encTasks = new HashMap<String, Set<Task> >();
        Map<String, String> taskTmp = new HashMap<String, String>();

        // Bulk-resolve annotation -> encounter catalogNumber via the join table.
        // Replaces per-annotation Encounter.findByAnnotation() JDOQL, which on
        // encounters with photographers/submitters/informOthers populated triggers
        // a DataNucleus 5.2.7 SQL-generation bug that produces malformed SQL.
        Map<String, String> annIdToEncId = annotationIdsToEncounterIds(atm.keySet(),
            myShepherd);
        // Issue #1787 (spec §4.5): each encounter's FULL annotation id set (including
        // annotations added after the import) scopes the Match Results selection and the
        // per-annotation status. null = the SQL pass failed: fall back to the two-argument
        // selection and today's status rule.
        Map<String, Set<String> > encIdToAnnIds = encounterIdsToAnnotationIds(
            new HashSet<String>(annIdToEncId.values()), myShepherd);
        boolean runAware = (encIdToAnnIds != null);

        for (Annotation ann : atm.keySet()) {
            String encId = annIdToEncId.get(ann.getId());
            if ((encId != null) && !encTasks.containsKey(encId))
                encTasks.put(encId, new HashSet<Task>());
            // trivial annots will not be sent correctly to ident (no iaClass etc)
            // so we skip them in counts as if not sent
            if (ann.isTrivial()) {
                sa.put(ann.getId(), 0);
                continue;
            }
            sa.put(ann.getId(), Util.collectionSize(atm.get(ann)));
            // Annotation exists but has no task yet (e.g., its match was gate-deferred and
            // runMatchProspects has not fired yet). Count it as a pending "latest task" so
            // the aggregate identificationStatus in iaSummaryJson stays "sent".
            List<Task> annTasks = atm.get(ann);
            if ((annTasks == null) || annTasks.isEmpty()) {
                String latestStatus = "numLatestTask_pending";
                sa.put(latestStatus, sa.optInt(latestStatus, 0) + 1);
                numLatestTasks++;
                continue;
            }
            for (Task atask : annTasks) {   // per-task tallies, unchanged
                String status = atask.getStatus(myShepherd);
                sa.put(status, sa.optInt(status, 0) + 1);
                numTasks++;
            }
            Set<String> encSet = (!runAware || (encId == null)) ? null : encIdToAnnIds.get(encId);
            // Issue #1787 (spec §4.4): one status per annotation across every option of its run.
            AnnotationIdStatus agg = identificationStatusForAnnotation(ann, annTasks, this.getId(),
                encSet, runAware, myShepherd);
            String latestStatus = "numLatestTask_" + agg.status;
            System.out.println("[DEBUG] (ImportTask " + this.getId() +
                ") latestStatus for Annotation " + ann.getId() + ": " + latestStatus);
            sa.put(latestStatus, sa.optInt(latestStatus, 0) + 1);
            if (agg.skipped) sa.put("numLatestTask_skipped", sa.optInt("numLatestTask_skipped", 0) + 1);
            numLatestTasks++;
            // Shared selector, scoped to the encounter (spec §4.3), so this page and the
            // encounter page agree on which task they link to.
            if (encId != null) {
                Task preferred = Task.getPreferredMatchResultsTaskForAnnotation(ann, this.getId(),
                    encSet, myShepherd);
                if (preferred != null) {
                    encTasks.get(encId).add(preferred);
                    String key = preferred.getId() + ".iaClass";
                    taskTmp.put(key, mergeClassLabel(taskTmp.get(key), ann.getIAClass()));
                }
            }
        }
        sa.put("numTasks", numTasks);
        sa.put("numLatestTasks", numLatestTasks);
```
(keep the rest of the method — the `encounterTaskInfo` block — exactly as it is).

After `annotationIdsToEncounterIds` add:

```java
    // Returns {encounter catalogNumber -> every annotation id on it} via one SQL pass through
    // ENCOUNTER_ANNOTATIONS, or null when the query fails (callers fall back). Issue #1787.
    static Map<String, Set<String> > encounterIdsToAnnotationIds(
        java.util.Collection<String> encIds, Shepherd myShepherd) {
        Map<String, Set<String> > result = new HashMap<String, Set<String> >();

        if ((encIds == null) || encIds.isEmpty()) return result;
        StringBuilder inList = new StringBuilder();
        int n = 0;
        for (String encId : encIds) {
            if (encId == null) continue;
            if (n > 0) inList.append(",");
            inList.append("'").append(encId.replace("'", "''")).append("'");
            n++;
        }
        if (n == 0) return result;
        String sql =
            "SELECT \"CATALOGNUMBER_OID\", \"ID_EID\" FROM \"ENCOUNTER_ANNOTATIONS\" WHERE \"CATALOGNUMBER_OID\" IN ("
            + inList.toString() + ")";
        Query q = null;
        try {
            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
            List rows = (List)q.execute();
            for (Object row : rows) {
                Object[] r = (Object[])row;
                String catNum = (String)r[0];
                String annId = (String)r[1];
                if ((catNum == null) || (annId == null)) continue;
                Set<String> ids = result.get(catNum);
                if (ids == null) {
                    ids = new HashSet<String>();
                    result.put(catNum, ids);
                }
                ids.add(annId);
            }
        } catch (Exception ex) {
            System.out.println("[ERROR] ImportTask.encounterIdsToAnnotationIds() failed: " + ex);
            ex.printStackTrace();
            return null;
        } finally {
            if (q != null) q.closeAll();
        }
        return result;
    }

    // ---- issue #1787 (spec §4.4): per-annotation identification status over every option of its run ----

    static final class AnnotationIdStatus {
        final String status;
        final boolean skipped;   // no option held the annotation (its class had none): informational

        AnnotationIdStatus(String status, boolean skipped) {
            this.status = status;
            this.skipped = skipped;
        }
    }

    /**
     * Rules: (1) the run is the newest eligible umbrella, else the newest umbrella holding the
     * annotation regardless of the encounter set; (2) no run (old tree, or not run-aware):
     * today's rule, the newest direct task's status; (3) one representative per option task of
     * the run that holds the annotation — its newest childless descendant-or-self holding the
     * annotation, else the option itself; (4) any non-terminal representative -> that status;
     * else any error -> error; else completed. No option: a noWork umbrella's own status, else
     * completed + skipped. (5) a non-terminal representative older than the maximum age is
     * reported as error (reported only).
     */
    static AnnotationIdStatus identificationStatusForAnnotation(Annotation ann, List<Task> direct,
        String importTaskId, Set<String> encounterAnnotationIds, boolean runAware,
        Shepherd myShepherd) {
        if (Util.collectionIsEmptyOrNull(direct)) return new AnnotationIdStatus("pending", false);
        Task run = null;
        if (runAware && Task.anyRunId(direct)) {
            run = Task.selectRun(direct, importTaskId, encounterAnnotationIds);
            if (run == null) run = Task.selectRun(direct, importTaskId, null);
        }
        if (run == null) return new AnnotationIdStatus(direct.get(0).getStatus(myShepherd), false);
        List<Task> options = new ArrayList<Task>();
        collectOptionTasks(run, run.getId(), ann.getId(), options, new HashSet<String>(), 0);
        if (options.isEmpty()) {
            if (IA.intakeProducedNoWork(run))
                return new AnnotationIdStatus(reportedStatus(run, myShepherd), false);
            return new AnnotationIdStatus("completed", true);
        }
        String firstNonTerminal = null;
        boolean anyError = false;
        for (Task option : options) {
            Task rep = representativeFor(option, run.getId(), ann.getId());
            String s = reportedStatus(rep, myShepherd);
            if (!isTerminalStatus(s)) {
                if (firstNonTerminal == null) firstNonTerminal = s;
            } else if ("error".equals(s)) {
                anyError = true;
            }
        }
        if (firstNonTerminal != null) return new AnnotationIdStatus(firstNonTerminal, false);
        return new AnnotationIdStatus(anyError ? "error" : "completed", false);
    }

    private static boolean isTerminalStatus(String s) {
        return "completed".equals(s) || "error".equals(s) || "dropped-stale".equals(s);
    }

    // rule 5: maximum age, reported only (Task.getStatus exempts null stored statuses from its watchdog)
    private static String reportedStatus(Task t, Shepherd myShepherd) {
        String s = t.getStatus(myShepherd);

        if (!isTerminalStatus(s) && t.timedOutDueToInactivity()) return "error";
        return s;
    }

    private static final int RUN_WALK_MAX_DEPTH = 20;

    // option tasks of the run (by marker, not tree shape) that hold the annotation
    private static void collectOptionTasks(Task t, String runId, String annId, List<Task> out,
        Set<String> visited, int depth) {
        if ((t == null) || (t.getId() == null) || (depth > RUN_WALK_MAX_DEPTH) || !visited.add(t.getId())) return;
        if (!runId.equals(Task.runIdOf(t))) return;
        if (Task.isOptionTask(t) && t.holdsAnnotationId(annId)) out.add(t);
        if (t.getChildren() != null) {
            for (Task kid : t.getChildren()) collectOptionTasks(kid, runId, annId, out, visited, depth + 1);
        }
    }

    // the option's newest childless descendant-or-self holding the annotation, else the option
    private static Task representativeFor(Task option, String runId, String annId) {
        if (!option.hasChildren()) return option;
        List<Task> leaves = new ArrayList<Task>();
        collectChildlessHolding(option, runId, annId, leaves, new HashSet<String>(), 0);
        Task best = null;
        for (Task leaf : leaves) {
            if ((best == null) || (leaf.getCreatedLong() > best.getCreatedLong())) best = leaf;
        }
        return (best == null) ? option : best;
    }

    private static void collectChildlessHolding(Task t, String runId, String annId, List<Task> out,
        Set<String> visited, int depth) {
        if ((t == null) || (t.getId() == null) || (depth > RUN_WALK_MAX_DEPTH) || !visited.add(t.getId())) return;
        if (!runId.equals(Task.runIdOf(t))) return;
        if (!t.hasChildren()) {
            if (t.holdsAnnotationId(annId)) out.add(t);
            return;
        }
        for (Task kid : t.getChildren()) collectChildlessHolding(kid, runId, annId, out, visited, depth + 1);
    }

    // Class label of a shared preferred task: distinct non-blank iaClasses, sorted, ", "-joined.
    static String mergeClassLabel(String existing, String iaClass) {
        TreeSet<String> classes = new TreeSet<String>();

        if (Util.stringExists(existing)) {
            for (String s : existing.split(", ")) {
                if (Util.stringExists(s.trim())) classes.add(s.trim());
            }
        }
        if (Util.stringExists(iaClass) && Util.stringExists(iaClass.trim())) classes.add(iaClass.trim());
        if (classes.isEmpty()) return null;
        return String.join(", ", classes);
    }
```

In `Annotation.java` replace `getPreferredMatchResultsTask` (line 1941-1943) with:

```java
    public Task getPreferredMatchResultsTask(Shepherd myShepherd) {
        return Task.getPreferredMatchResultsTaskForAnnotation(this, myShepherd);
    }

    /** Issue #1787: scoped to the encounter's annotation ids (spec §4.3); null keeps today's behaviour. */
    public Task getPreferredMatchResultsTask(Shepherd myShepherd, Set<String> encounterAnnotationIds) {
        return Task.getPreferredMatchResultsTaskForAnnotation(this, null, encounterAnnotationIds, myShepherd);
    }
```
(`Annotation.java` already imports `java.util.Set`; if not, add it.)

In `Encounter.jsonForApiGet`, before the `for` loop over `rtn.getJSONArray("mediaAssets")` that contains the `getPreferredMatchResultsTask` call, build the set once:

```java
        // issue #1787: the Match Results link is scoped to this encounter's annotations
        Set<String> encounterAnnotationIds = new HashSet<String>();
        if (this.getAnnotations() != null) {
            for (Annotation a : this.getAnnotations()) {
                if ((a != null) && (a.getId() != null)) encounterAnnotationIds.add(a.getId());
            }
        }
```
and change `Task matchTask = ann.getPreferredMatchResultsTask(myShepherd);` to `Task matchTask = ann.getPreferredMatchResultsTask(myShepherd, encounterAnnotationIds);`. (Use fully qualified `java.util.Set` / `java.util.HashSet` if `Encounter.java` does not import them.)

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='ImportTaskIdentificationStatusTest,ImportTaskTest,ImportTaskCountsTest' -DfailIfNoTests=false`
Expected: PASS (the `iaSummaryJson` tests mock `statsAnnotations` and are unaffected).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/servlet/importer/ImportTask.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/servlet/importer/ImportTaskIdentificationStatusTest.java
git commit -m "Bulk-import identification status counts every option of the run (#1787)

statsAnnotations resolves each encounter's full annotation set in one SQL
pass, selects the Match Results task scoped to it, aggregates one
representative per option task (pending > error > completed, seven-day
maximum age) and merges iaClass labels for a shared task (spec §4.4, §4.5).
The encounter page opts in to the same scoped selection.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 6: Codex review** against §4.4 rules 1-5 and §4.5 (ask it to check `iaSummaryJson`'s `completed + error >= total` still holds with `numLatestTask_skipped` being informational only).

---

### Task 11: Datastore integration tests (Testcontainers PostgreSQL)

**Files:**
- Modify: `src/main/java/org/ecocean/IAJsonProperties.java` (`iaConfig()` line 43; test override)
- Create: `src/test/java/org/ecocean/TestIAJsonPropertiesUtil.java`
- Create: `src/test/java/org/ecocean/ia/MultiAlgorithmIntakeDbTest.java`

**Interfaces:**
- Consumes: everything above; `TestPMFUtil.closePMF`, `CommonConfiguration.initialize`, `FeatureType.initAll(Shepherd)`, `Shepherd(String, Properties)`, `IA.setDispatcherForTests`, `MlServiceProcessor.openShepherd`, the package-private `MlServiceProcessor(String, MlServiceClient, MatchVisibilityGate, DeferredMatchPublisher)` constructor, `LocalAssetStore(String, Path, String, boolean)` + `create(File)` (pattern of `EncounterExportImagesTest`), the test image `src/test/bulk-images/image-ok-0.jpg`, `IdentityServiceLog(String, String[], String, String, JSONObject)`.
- Produces: `IAJsonProperties.overrideForTests(IAJsonProperties)` (package-private static) and the public bridge `TestIAJsonPropertiesUtil.override(IAJsonProperties)`.

- [ ] **Step 1: Add the IA.json test seam**

In `IAJsonProperties.java` replace `iaConfig()` (lines 43-51) with:

```java
    private static volatile IAJsonProperties testOverride = null;

    /** Test seam (issue #1787 datastore tests): a fixture instance returned by iaConfig() until cleared with null. */
    static void overrideForTests(IAJsonProperties override) {
        testOverride = override;
    }

    // hackey constructor so we don't need to catch an exception every time we use these. Is there a better way?
    public static IAJsonProperties iaConfig() {
        IAJsonProperties override = testOverride;
        if (override != null) return override;
        try {
            return new IAJsonProperties();
        } catch (Exception e) {
            System.out.println(
                "IAJsonProperties ERROR: Could not find IA.json file! returning null and therefore about to hit an NPE elsewhere");
            return null;
        }
    }
```

Create `src/test/java/org/ecocean/TestIAJsonPropertiesUtil.java`:

```java
package org.ecocean;

/** Test-only bridge to the package-private IA.json override (pattern of shepherd.core.TestPMFUtil). */
public final class TestIAJsonPropertiesUtil {
    private TestIAJsonPropertiesUtil() {}

    public static void override(IAJsonProperties config) {
        IAJsonProperties.overrideForTests(config);
    }
}
```

- [ ] **Step 2: Write the datastore test**

Create `src/test/java/org/ecocean/ia/MultiAlgorithmIntakeDbTest.java`:

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.identity.IAQueryCache;
import org.ecocean.identity.IBEISIA;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spec §5.2: the intake core, the ml-service match step, the identify handler's retry
 * contract, the selector and the import status against real DataNucleus on real Postgres.
 * The dispatcher seam builds vector leaves the way Embedding.findMatchProspects does and
 * records HotSpotter jobs; WBIA is answered by partial static mocks. No OpenSearch,
 * ml-service, WBIA or file queue is touched.
 */
@Testcontainers
class MultiAlgorithmIntakeDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static final String CTX = "context0";
    static final String BODY = "guitarfish";
    static final String ROSTRUM = "guitarfish+rostrum";
    static int seq = 0;
    static Path imageDir;
    static File testImage;
    static LocalAssetStore store;

    /** Production-shaped stand-in for the two dispatch targets. */
    static final class LeafBuildingDispatcher implements IA.Dispatcher {
        volatile Runnable onFirstVector = null;      // persist-before-dispatch probe
        String throwForIaClass = null;
        final List<JSONObject> published = new ArrayList<JSONObject>();

        @Override public void runVector(JSONObject opt, Task optionTask, Shepherd shep) {
            Runnable probe = onFirstVector;
            onFirstVector = null;
            if (probe != null) probe.run();
            String iaClass = optionTask.getObjectAnnotations().get(0).getIAClass();
            if (iaClass.equals(throwForIaClass)) {
                Task leaf = leaf(optionTask, optionTask.getObjectAnnotations().get(0));
                shep.getPM().makePersistent(leaf);           // attached, unfinished
                throw new IllegalStateException("vector dispatch failed for " + iaClass);
            }
            for (Annotation a : optionTask.getObjectAnnotations()) {
                Task leaf = leaf(optionTask, a);
                leaf.setStatus("completed");
                leaf.setCompletionDateInMilliseconds();
                shep.getPM().makePersistent(leaf);
            }
            optionTask.setStatus("completed");
            optionTask.setCompletionDateInMilliseconds();
        }

        @Override public boolean publish(String context, String job, boolean fastlane) {
            published.add(new JSONObject(job));
            return true;
        }

        static Task leaf(Task option, Annotation a) {
            Task leaf = new Task(option);                   // inherits the option's parameters
            JSONObject p = leaf.getParameters();
            p.remove(Task.PARAM_OPTION);
            leaf.setParameters(p);
            leaf.addObject(a);
            return leaf;
        }
    }

    static LeafBuildingDispatcher dispatcher;

    @BeforeAll
    static void setUp() throws Exception {
        CommonConfiguration.initialize(CTX, new Properties());
        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF(CTX);
        imageDir = Files.createTempDirectory("multialgo-1787");
        testImage = Files.copy(Paths.get("src", "test", "bulk-images", "image-ok-0.jpg").toAbsolutePath(),
            imageDir.resolve("image.jpg"), StandardCopyOption.REPLACE_EXISTING).toFile();
        store = new LocalAssetStore("local-1787", imageDir, "file://" + imageDir, false);
        Shepherd sh = new Shepherd(CTX, props);
        try {
            sh.beginDBTransaction();
            FeatureType.initAll(sh);
            sh.getPM().makePersistent(store);
            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
        IAJsonProperties iac = IAJsonProperties.iaConfig();   // file lookup failures are swallowed by the ctor
        iac.setJson(iaJson());
        TestIAJsonPropertiesUtil.override(iac);
    }

    @AfterAll
    static void tearDown() {
        IA.setDispatcherForTests(null);
        TestIAJsonPropertiesUtil.override(null);
        TestPMFUtil.closePMF(CTX);
    }

    @BeforeEach
    void freshDispatcher() {
        dispatcher = new LeafBuildingDispatcher();
        IA.setDispatcherForTests(dispatcher);
    }

    // JsonProperties.get walks dotted keys through NESTED objects, so the fixture nests
    // genus -> species -> iaClass exactly like a real IA.json.
    // Pseudobatos lentiginosus: MiewID + HotSpotter default for both classes.
    // Rhina ancylostoma: HotSpotter only (single-option runs).
    static JSONObject iaJson() {
        JSONArray both = new JSONArray()
            .put(new JSONObject("{\"pipeline_root\":\"vector\",\"method\":\"miewid\",\"version\":\"msv4_v3\",\"default\":true,\"description\":\"MiewID\"}"))
            .put(new JSONObject("{\"query_config_dict\":{\"sv_on\":true},\"default\":true,\"description\":\"HotSpotter\"}"));
        JSONArray hsOnly = new JSONArray()
            .put(new JSONObject("{\"query_config_dict\":{\"sv_on\":true},\"default\":true,\"description\":\"HotSpotter\"}"));
        JSONObject lentiginosus = new JSONObject()
            .put(BODY, new JSONObject().put("_id_conf", both))
            .put(ROSTRUM, new JSONObject().put("_id_conf", new JSONArray(both.toString())));
        JSONObject ancylostoma = new JSONObject().put(BODY, new JSONObject().put("_id_conf", hsOnly));
        return new JSONObject()
            .put("Pseudobatos", new JSONObject().put("lentiginosus", lentiginosus))
            .put("Rhina", new JSONObject().put("ancylostoma", ancylostoma));
    }

    // ---- fixtures ----

    static String nextImportId() {
        return "import-" + (++seq);
    }

    // An encounter with one annotation per iaClass, each on its own persisted MediaAsset with a
    // bbox feature wired on both owning sides (Feature.asset via addFeature, Feature.annotation
    // via setAnnotation — the mapped-by side of Annotation.features).
    static Encounter encounter(Shepherd sh, String genus, String species, String... iaClasses) {
        Encounter enc = new Encounter();
        enc.setSkipAutoIndexing(true);
        enc.setCatalogNumber("enc-" + (++seq));
        enc.setGenus(genus);
        enc.setSpecificEpithet(species);
        for (String iaClass : iaClasses) {
            MediaAsset ma = store.create(testImage);
            ma.setId(100000 + (++seq));
            ma.setSkipAutoIndexing(true);
            ma.setAcmId(ma.getUUID());
            Feature f = new Feature("org.ecocean.boundingBox",
                new JSONObject().put("x", 1).put("y", 1).put("width", 10).put("height", 10));
            ma.addFeature(f);
            Annotation ann = new Annotation(null, f, iaClass);
            f.setAnnotation(ann);
            ann.setSkipAutoIndexing(true);
            ann.setMatchAgainst(true);
            ann.setAcmId(ann.getId());
            sh.getPM().makePersistent(ma);
            sh.getPM().makePersistent(ann);
            enc.addAnnotation(ann);
        }
        sh.getPM().makePersistent(enc);
        return enc;
    }

    static Encounter guitarfish(Shepherd sh, String... iaClasses) {
        return encounter(sh, "Pseudobatos", "lentiginosus", iaClasses);
    }

    // root (stamped with the import) -> D (detection task, completed)
    static Task detectionTask(Shepherd sh, String importId) {
        Task root = new Task();
        root.setParameters(new JSONObject().put("importTaskId", importId));
        Task d = new Task(root);
        d.addParameter("ibeis.detection", true);
        d.setStatus("completed");
        sh.getPM().makePersistent(root);
        sh.getPM().makePersistent(d);
        return d;
    }

    // the ImportTask's own id is what statsAnnotations scopes by, so it MUST equal the stamp
    static ImportTask importWith(Shepherd sh, String importId, Task root, Encounter... encs) {
        ImportTask itask = new ImportTask(null, importId);
        for (Encounter e : encs) itask.addEncounter(e);
        itask.setIATask(root);
        sh.storeNewImportTask(itask);
        return itask;
    }

    static List<String> annotationIds(Encounter... encs) {
        List<String> ids = new ArrayList<String>();
        for (Encounter e : encs) {
            for (Annotation a : e.getAnnotations()) ids.add(a.getId());
        }
        return ids;
    }

    static Set<String> idSet(Encounter enc) {
        return new HashSet<String>(annotationIds(enc));
    }

    static Shepherd open(String action) {
        Shepherd sh = new Shepherd(CTX);
        sh.setAction("MultiAlgorithmIntakeDbTest." + action);
        sh.beginDBTransaction();
        return sh;
    }

    static JSONObject matchConfig() {
        return new JSONObject("{\"method\":\"miewid\",\"version\":\"msv4_v3\"}");
    }

    static MlServiceProcessor processor() {
        return processorFailingCommit(-1);
    }

    // The processor's own Shepherds come from openShepherd; the Nth checked commit (counting
    // across the main and the recovery Shepherd) returns false, as a lost connection would.
    static MlServiceProcessor processorFailingCommit(final int failOnCall) {
        final int[] calls = { 0 };
        MatchVisibilityGate gate = (ids, task, cfg, attempt, first) ->
            MatchVisibilityGate.GateOutcome.ready(attempt, System.currentTimeMillis());
        DeferredMatchPublisher publisher = payload -> { };
        return new MlServiceProcessor(CTX, new MlServiceClient(), gate, publisher) {
            @Override Shepherd openShepherd(String action) {
                Shepherd s = new Shepherd(CTX) {
                    @Override public boolean commitDBTransactionWithStatus() {
                        if (++calls[0] == failOnCall) return false;
                        return super.commitDBTransactionWithStatus();
                    }
                };
                s.setAction("MultiAlgorithmIntakeDbTest.processor." + action);
                return s;
            }
        };
    }

    interface Body { void run() throws Exception; }

    // Partial static mocks for a real _doIdentify run (same shape as IAGatewayDoIdentifyTest):
    // IBEISIA.beginIdentifyAnnotations answers sendResult, requeueJob answers through `requeue`.
    static void withIdentifyStubs(JSONObject sendResult, Answer<Boolean> requeue, Body body)
    throws Exception {
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("waitForIAPriming")) return null;
                 if (m.equals("queryConfigDict")) return new JSONObject();
                 if (m.equals("beginIdentifyAnnotations")) return sendResult;
                 if (m.equals("log")) return null;
                 return inv.callRealMethod();
             });
             MockedStatic<IAQueryCache> cache = mockStatic(IAQueryCache.class);
             MockedStatic<IAGateway> gw = mockStatic(IAGateway.class, inv -> {
                 if (inv.getMethod().getName().equals("requeueJob")) return requeue.answer(inv);
                 return inv.callRealMethod();
             })) {
            cache.when(() -> IAQueryCache.tryTargetAnnotationsCache(anyString(), any(Annotation.class),
                any(JSONObject.class), any(Shepherd.class))).thenReturn(null);
            body.run();
        }
    }

    static List<Task> childrenOf(Task t) {
        return (t.getChildren() == null) ? new ArrayList<Task>() : t.getChildren();
    }

    static Task optionOf(Task umbrella, boolean vector, String iaClass) {
        for (Task t : childrenOf(umbrella)) {
            boolean v = t.getParameters().optBoolean(Task.PARAM_V2_MATCH, false);
            if ((v == vector) && iaClass.equals(t.getObjectAnnotations().get(0).getIAClass())) return t;
        }
        return null;
    }

    static String errorCode(Task t) {
        JSONObject sd = t.getStatusDetails();
        JSONArray errs = (sd == null) ? null : sd.optJSONArray("errors");
        if ((errs == null) || (errs.length() == 0)) return null;
        return errs.getJSONObject(errs.length() - 1).optString("code", null);
    }

    static void setStatus(String taskId, String status) {
        Shepherd sh = open("setStatus");
        try {
            Task t = Task.load(taskId, sh);
            t.setStatus(status);
            if ("completed".equals(status) || "error".equals(status)) t.setCompletionDateInMilliseconds();
            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
    }

    // ---- cases ----

    @Test void fixtureRelationshipsSurviveReload() {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY);
        String annId = annotationIds(e1).get(0);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        Shepherd check = open("check");
        try {
            Annotation ann = check.getAnnotation(annId);
            assertNotNull(ann.getMediaAsset(), "the bbox feature links the annotation to its asset");
            assertEquals(1, ann.getFeatures().size());
            assertTrue(Arrays.equals(new int[] { 1, 1, 10, 10 }, ann.getBbox()));
            assertFalse(ann.isTrivial());
            assertEquals(e1.getCatalogNumber(), ann.findEncounter(check).getCatalogNumber());
            assertNotNull(ann.getTaxonomy(check), "identOpts resolves the taxonomy through the encounter");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void processorBuildsOneUmbrellaPerEncounterAndPersistsBeforeDispatch() {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Encounter e2 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        final String dId = d.getId();
        dispatcher.onFirstVector = () -> {
            Shepherd probe = open("probe");   // a second PersistenceManager
            try {
                Task dd = Task.load(dId, probe);
                assertEquals(1, childrenOf(dd).size(), "the first group's umbrella is committed before its dispatch");
                Task u = childrenOf(dd).get(0);
                assertTrue(Task.isUmbrella(u));
                assertEquals(4, childrenOf(u).size(), "its option tasks are committed too");
            } finally {
                probe.rollbackAndClose();
            }
        };
        MlServiceJobOutcome out = processor().runMatchProspects(ids, dId, matchConfig());
        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertNull(dispatcher.onFirstVector, "the probe ran");

        Shepherd check = open("check");
        try {
            Task dd = Task.load(dId, check);
            assertEquals("completed", dd.getStoredStatus(), "D is untouched");
            assertEquals(2, childrenOf(dd).size(), "one umbrella per encounter");
            int options = 0, leaves = 0;
            for (Task u : childrenOf(dd)) {
                assertTrue(Task.isUmbrella(u));
                assertEquals(dId, u.getParent().getId());
                assertEquals(dd.getParameters().getString("importTaskId"), u.getParameters().getString("importTaskId"));
                assertFalse(u.getParameters().has("ibeis.detection"));
                for (Task opt : childrenOf(u)) {
                    options++;
                    assertEquals(u.getId(), Task.runIdOf(opt));
                    assertTrue(Task.isOptionTask(opt));
                    if (opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) {
                        assertEquals("completed", opt.getStoredStatus());
                        for (Task leaf : childrenOf(opt)) {
                            leaves++;
                            assertEquals(u.getId(), Task.runIdOf(leaf));
                            assertFalse(Task.isOptionTask(leaf));
                            assertEquals("completed", leaf.getStoredStatus());
                        }
                    } else {
                        assertNull(opt.getStoredStatus(), "HotSpotter waits for WBIA");
                        assertNotNull(opt.getQueueResumeMessage());
                    }
                }
            }
            assertEquals(6, options);
            assertEquals(3, leaves);
            assertEquals(3, dispatcher.published.size());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void initialPersistFailureDispatchesNothingAndMarksOnlyD() {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        // checked commit 1 = the intake core's persist commit
        MlServiceJobOutcome out = processorFailingCommit(1).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());
        assertTrue(dispatcher.published.isEmpty(), "nothing was dispatched");
        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("MATCH_PERSIST", errorCode(dd));
            assertEquals(0, childrenOf(dd).size(), "nothing of the run is durable");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void selectorLandsOnTheUmbrellaAfterReloadAndRendersExactlyItsRun() {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task r = new Task();
        r.setParameters(new JSONObject().put("importTaskId", "IMP-2"));
        Task subParent = new Task(r);
        subParent.addParameter("matchingSetFilter", new JSONObject());
        sh.getPM().makePersistent(r);
        sh.getPM().makePersistent(subParent);
        IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, new ArrayList<Annotation>(e1.getAnnotations()),
            subParent, subParent.getParameters(), false);
        assertFalse(o.persistFailed);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String uId = o.umbrella.getId();
        Set<String> encSet = idSet(e1);

        Shepherd sh2 = open("reload");   // five levels: r -> subParent -> U -> T_vec -> SA
        try {
            for (String annId : annotationIds(e1)) {
                Annotation ann = sh2.getAnnotation(annId);
                Task picked = Task.getPreferredMatchResultsTaskForAnnotation(ann, "IMP-2", encSet, sh2);
                assertEquals(uId, picked.getId(), "both annotations link to the run's umbrella");
                assertEquals(uId, Task.getPreferredMatchResultsTaskForAnnotation(ann, null, encSet, sh2).getId(),
                    "the encounter page (unscoped) agrees");
            }
            Task u = Task.load(uId, sh2);
            JSONObject json = u.matchResultsJson(10, null, sh2);
            JSONArray options = json.getJSONArray("children");
            assertEquals(4, options.length());
            Set<String> leafIds = new HashSet<String>();
            for (int i = 0; i < options.length(); i++) {
                JSONArray kids = options.getJSONObject(i).optJSONArray("children");
                if (kids == null) continue;
                for (int j = 0; j < kids.length(); j++) leafIds.add(kids.getJSONObject(j).getString("id"));
            }
            Set<String> expected = new HashSet<String>();
            for (Task opt : childrenOf(u)) for (Task leaf : childrenOf(opt)) expected.add(leaf.getId());
            assertEquals(2, expected.size());
            assertEquals(expected, leafIds, "the page renders exactly the run's leaves");
        } finally {
            sh2.rollbackAndClose();
        }
    }

    @Test void importStatusCountsEveryOptionOfTheRun() {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Encounter e2 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());

        // vector leaves completed, HotSpotter options still queued -> sent
        JSONObject sum = summary(itask.getId());
        assertEquals("sent", sum.getString("identificationStatus"));
        assertEquals(3, sum.getInt("identificationNumTotal"), "exactly one latest task per non-trivial annotation");
        assertEquals(0, sum.getInt("identificationNumberComplete"));

        // every HotSpotter option completes (the WBIA callback) -> complete
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(3, sum.getInt("identificationNumberComplete"));
        assertEquals(0, sum.getInt("identificationNumberError"));

        // e2's vector LEAF (the option's representative) fails -> still complete, one error
        setStatus(vectorLeafId(d.getId(), e2), "error");
        sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"));

        // the Class label of a shared task merges the classes
        JSONObject info = sum.getJSONObject("statsAnnotations").getJSONObject("encounterTaskInfo");
        JSONArray e1Tasks = info.getJSONArray(e1.getCatalogNumber());
        assertEquals(1, e1Tasks.length(), "both annotations of e1 link to one task");
        assertEquals(BODY + ", " + ROSTRUM, e1Tasks.getJSONArray(0).getString(2));
    }

    /** §5.2 status matrix: error + pending, single-HotSpotter pending, skipped class, late fan-out next to a newer run. */
    @Test void importStatusMatrixVariants() {
        // (a) error vector leaf + pending HotSpotter -> sent
        Shepherd sh = open("fixture-a");
        String importA = nextImportId();
        Encounter ea = guitarfish(sh, BODY);
        Task da = detectionTask(sh, importA);
        ImportTask ia = importWith(sh, importA, da.getParent(), ea);
        List<String> idsA = annotationIds(ea);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(idsA, da.getId(), matchConfig());
        setStatus(vectorLeafId(da.getId(), ea), "error");
        assertEquals("sent", summary(ia.getId()).getString("identificationStatus"), "pending beats error");

        // (b) single-HotSpotter umbrella pending -> sent; completed -> complete
        sh = open("fixture-b");
        String importB = nextImportId();
        Encounter eb = encounter(sh, "Rhina", "ancylostoma", BODY);
        Task db = detectionTask(sh, importB);
        ImportTask ib = importWith(sh, importB, db.getParent(), eb);
        List<String> idsB = annotationIds(eb);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(idsB, db.getId(), matchConfig());
        assertEquals("sent", summary(ib.getId()).getString("identificationStatus"));
        for (String id : hotspotterOptionIds(db.getId())) setStatus(id, "completed");
        assertEquals("complete", summary(ib.getId()).getString("identificationStatus"));

        // (c) a class without options next to one with -> completed + skipped for that annotation
        sh = open("fixture-c");
        String importC = nextImportId();
        Encounter ec = guitarfish(sh, BODY, "mystery");
        Task dc = detectionTask(sh, importC);
        ImportTask ic = importWith(sh, importC, dc.getParent(), ec);
        List<String> idsC = annotationIds(ec);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(idsC, dc.getId(), matchConfig());
        for (String id : hotspotterOptionIds(dc.getId())) setStatus(id, "completed");
        JSONObject sumC = summary(ic.getId());
        assertEquals("complete", sumC.getString("identificationStatus"));
        assertEquals(1, sumC.getJSONObject("statsAnnotations").getInt("numLatestTask_skipped"));
        assertEquals(2, sumC.getInt("identificationNumberComplete"));

        // (d) a late fan-out child under an OLDER run next to a NEWER pending run -> sent
        sh = open("fixture-d");
        String importD = nextImportId();
        Encounter ed = guitarfish(sh, BODY);
        Task dd = detectionTask(sh, importD);
        ImportTask idTask = importWith(sh, importD, dd.getParent(), ed);
        List<Annotation> annsD = new ArrayList<Annotation>(ed.getAnnotations());
        JSONObject inherited = MlServiceProcessor.matchIntakeParams(dd.getParameters());
        IA.IntakeOutcome run1 = IA.intakeAnnotationsUnder(sh, annsD, dd, inherited, false);
        sh.commitDBTransaction();
        sh.beginDBTransaction();
        IA.IntakeOutcome run2 = IA.intakeAnnotationsUnder(sh, annsD, dd, inherited, false);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String run1Hs = null;
        for (IA.OptionResult r : run1.options) if (!r.vector) run1Hs = r.task.getId();
        setStatus(run1Hs, "completed");                    // run 1 fully complete
        Shepherd late = open("late");                      // the late fan-out child, created after run 2
        try {
            Task hs1 = Task.load(run1Hs, late);
            Task child = new Task(hs1);
            child.addObject(late.getAnnotation(annsD.get(0).getId()));
            child.setStatus("completed");
            late.getPM().makePersistent(child);
            late.commitDBTransaction();
        } finally {
            late.closeDBTransaction();
        }
        assertEquals("sent", summary(idTask.getId()).getString("identificationStatus"),
            "run 2's pending HotSpotter wins over run 1's late completed child");
        assertNotNull(run2.umbrella);
    }

    /** _doIdentify mid fan-out: the option itself represents the annotation without a child. */
    @Test void midFanOutReadsPendingForTheUnfannedAnnotation() {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, BODY);        // two body annotations share one option
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(ids, d.getId(), matchConfig());
        String hsId = hotspotterOptionIds(d.getId()).get(0);
        Shepherd fan = open("fanout");                      // H_A exists, H_B not yet
        try {
            Task hs = Task.load(hsId, fan);
            Task hA = new Task(hs);
            hA.setParameters(Task.withoutOptionMarker(hs.getParameters()));
            hA.addObject(fan.getAnnotation(ids.get(0)));
            hA.setStatus("completed");
            fan.getPM().makePersistent(hA);
            fan.commitDBTransaction();
        } finally {
            fan.closeDBTransaction();
        }
        JSONObject sum = summary(itask.getId());
        assertEquals("sent", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberComplete"), "A is complete, B still waits on its fan-out");
    }

    @Test void secondGroupCommitFailureKeepsTheFirstGroupAndMarksTheSecond() {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Encounter e2 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        // checked commits: 1 = group-1 intake persist, 2 = group-1 commit, 3 = group-2 intake persist, 4 = group-2 commit
        MlServiceJobOutcome out = processorFailingCommit(4).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());

        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("error", dd.getStoredStatus());
            assertEquals("MATCH_PERSIST", errorCode(dd));
            assertEquals(2, childrenOf(dd).size(), "both umbrellas are durable (the intake persist commit)");
            Task u1 = umbrellaFor(dd, e1);
            Task u2 = umbrellaFor(dd, e2);
            assertNull(u1.getStoredStatus(), "group 1 untouched");
            for (Task opt : childrenOf(u1)) {
                if (opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) {
                    assertEquals("completed", opt.getStoredStatus());
                    assertEquals(1, childrenOf(opt).size(), "group 1's leaves are durable");
                }
            }
            assertEquals("error", u2.getStoredStatus());
            assertEquals("MATCH_PERSIST", errorCode(u2));
            Task v2 = optionOf(u2, true, BODY);
            assertEquals("error", v2.getStoredStatus());
            assertEquals("MATCH_PERSIST", errorCode(v2));
            assertEquals(0, childrenOf(v2).size(), "its leaves were rolled back");
            Task h2 = optionOf(u2, false, BODY);
            assertNull(h2.getStoredStatus(), "a published HotSpotter option is left to its job");
        } finally {
            check.rollbackAndClose();
        }
        // the HotSpotter jobs complete later -> the import ends complete-with-errors
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"));
    }

    @Test void vectorDispatchExceptionMarksTheOptionAndItsAttachedLeaf() {
        dispatcher.throwForIaClass = ROSTRUM;
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind(),
            "a dispatch failure of one option is not a job failure");

        Shepherd check = open("check");
        try {
            Task u = umbrellaFor(Task.load(d.getId(), check), e1);
            Task vr = optionOf(u, true, ROSTRUM);
            assertEquals("error", vr.getStoredStatus());
            assertEquals("DISPATCH_FAILED", errorCode(vr));
            assertEquals(1, childrenOf(vr).size());
            assertEquals("error", childrenOf(vr).get(0).getStoredStatus(), "the attached leaf is terminalized");
            Task vb = optionOf(u, true, BODY);
            assertEquals("completed", vb.getStoredStatus(), "the other option still ran");
            assertEquals(2, dispatcher.published.size());
        } finally {
            check.rollbackAndClose();
        }
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"), "rostrum reads error through its option");
    }

    @Test void dispatchExceptionThenGroupCommitFailureMarksEveryUnpublishedOption() {
        dispatcher.throwForIaClass = ROSTRUM;
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        // 1 = intake persist, 2 = the group commit
        MlServiceJobOutcome out = processorFailingCommit(2).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());

        Shepherd check = open("check");
        try {
            Task u = umbrellaFor(Task.load(d.getId(), check), e1);
            assertEquals("MATCH_PERSIST", errorCode(u));
            assertEquals("MATCH_PERSIST", errorCode(optionOf(u, true, BODY)), "published vector option: its leaves were rolled back");
            assertEquals("MATCH_PERSIST", errorCode(optionOf(u, true, ROSTRUM)), "its DISPATCH_FAILED mark was rolled back too");
            assertNull(optionOf(u, false, BODY).getStoredStatus(), "published HotSpotter options are untouched");
            assertNull(optionOf(u, false, ROSTRUM).getStoredStatus());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void singleHotspotterRunWhosePublishedJobSurvivesACommitFailureStaysPending() {
        Shepherd sh = open("fixture");
        Encounter rhina = encounter(sh, "Rhina", "ancylostoma", BODY);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(rhina);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = processorFailingCommit(2).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());

        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("MATCH_PERSIST", errorCode(dd));
            Task u = childrenOf(dd).get(0);
            assertTrue(Task.isUmbrella(u));
            assertTrue(Task.isOptionTask(u), "the umbrella is the single option");
            assertNull(u.getStoredStatus(), "its job was published and will complete it");
            assertEquals(1, dispatcher.published.size());
        } finally {
            check.rollbackAndClose();
        }
    }

    /** §4.4: a poll between the inner commit and the requeue decision sees a pending task, not a terminal error. */
    @Test void failedSendIsPendingWhilePollingBetweenTheInnerCommitAndTheRequeueDecision() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());
        String hsId = hotspotterOptionIds(d.getId()).get(0);
        // the log line a failed send leaves behind: with a NULL stored status getStatus reads it as "error"
        Shepherd logSh = open("log");
        try {
            logSh.getPM().makePersistent(new IdentityServiceLog(hsId, null, "IBEISIA", "-1",
                new JSONObject().put("_action", "sendIdentify")
                    .put("_response", new JSONObject().put("error", "HTTP error code : 500"))));
            logSh.commitDBTransaction();
        } finally {
            logSh.closeDBTransaction();
        }
        final String[] polled = new String[1];
        JSONObject failed = new JSONObject().put("success", false).put("error", "WBIA refused");
        JSONObject jin = new JSONObject()
            .put("identify", new JSONObject().put("annotationIds", new JSONArray().put(ids.get(0))))
            .put("opt", new JSONObject());
        Shepherd consumer = open("consumer");   // the queue consumer's Shepherd
        try {
            withIdentifyStubs(failed, inv -> {
                polled[0] = summary(itask.getId()).getString("identificationStatus");   // a second PM
                return true;                                                              // retry scheduled
            }, () -> {
                IAGateway._doIdentify(jin, new JSONObject().put("taskId", hsId), consumer, CTX, "http://wb", false);
            });
            consumer.commitDBTransaction();
        } finally {
            consumer.closeDBTransaction();
        }
        assertEquals("sent", polled[0], "pending between the inner commit and the requeue decision");
        Shepherd check = open("check");
        try {
            Task h = Task.load(hsId, check);
            assertEquals(Task.STATUS_REQUEUED, h.getStoredStatus());
            assertEquals(Task.STATUS_REQUEUED, h.getStatus(check), "the stored status wins over the HTTP-error log");
            assertNull(h.getCompletionDateInMilliseconds());
        } finally {
            check.rollbackAndClose();
        }
        assertEquals("sent", summary(itask.getId()).getString("identificationStatus"));

        // the retry budget is exhausted on the next attempt -> terminal SEND_FAILED
        Shepherd consumer2 = open("consumer2");
        try {
            withIdentifyStubs(failed, inv -> false, () -> {
                IAGateway._doIdentify(jin, new JSONObject().put("taskId", hsId), consumer2, CTX, "http://wb", false);
            });
            consumer2.commitDBTransaction();
        } finally {
            consumer2.closeDBTransaction();
        }
        Shepherd check2 = open("check2");
        try {
            Task h = Task.load(hsId, check2);
            assertEquals("error", h.getStoredStatus());
            assertEquals("SEND_FAILED", errorCode(h));
            assertNotNull(h.getCompletionDateInMilliseconds());
        } finally {
            check2.rollbackAndClose();
        }
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"));
    }

    /** Review Focus 2: one image, two encounters, one of which has no default option. */
    @Test void mixedNoWorkGroupsDoNotFailTheDetectionTask() {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY);
        Encounter e2 = guitarfish(sh, "mystery");   // no _id_conf for this class
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());

        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("completed", dd.getStoredStatus(), "D is not INVALID_MATCH_CONFIG");
            Task u2 = umbrellaFor(dd, e2);
            assertEquals("error", u2.getStoredStatus());
            assertEquals("INVALID_MATCH_CONFIG", errorCode(u2));
            assertTrue(IA.intakeProducedNoWork(u2));
            assertEquals(2, childrenOf(umbrellaFor(dd, e1)).size());
        } finally {
            check.rollbackAndClose();
        }
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"), "the no-option encounter's annotation reads error");
    }

    @Test void allGroupsWithoutOptionsMarkTheDetectionTaskInvalid() {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, "mystery");
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = processor().runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("INVALID_MATCH_CONFIG", out.getCode());
        Shepherd check = open("check");
        try {
            assertEquals("INVALID_MATCH_CONFIG", errorCode(Task.load(d.getId(), check)));
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void handleRestWithAnUmbrellaAsRootCreatesAFreshRootThatKeepsTheRequestParameters() {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY);
        JSONObject requestParams = new JSONObject().put("matchingSetFilter", new JSONObject().put("locationIds", new JSONArray().put("X")));
        IA.IntakeOutcome first = IA.intakeAnnotationsUnder(sh, new ArrayList<Annotation>(e1.getAnnotations()), null, requestParams, false);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String oldUmbrella = first.umbrella.getId();
        String annId = annotationIds(e1).get(0);

        JSONObject jin = new JSONObject().put("__context", CTX).put("taskId", oldUmbrella)
            .put("annotationIds", new JSONArray().put(annId));
        IA.handleRest(jin);

        Shepherd check = open("check");
        try {
            Task old = Task.load(oldUmbrella, check);
            assertEquals(2, childrenOf(old).size(), "the old run gained nothing");
            Annotation ann = check.getAnnotation(annId);
            Task newUmbrella = null;
            for (Task t : Task.getTasksFor(ann, check)) {
                if (Task.isUmbrella(t) && !t.getId().equals(oldUmbrella)) newUmbrella = t;
            }
            assertNotNull(newUmbrella, "a second run exists");
            Task freshRoot = newUmbrella.getParent();
            assertNotNull(freshRoot);
            assertFalse(freshRoot.getId().equals(oldUmbrella));
            assertNull(Task.runIdOf(freshRoot));
            assertEquals(0, freshRoot.numberAnnotations());
            assertTrue(freshRoot.getParameters().has("matchingSetFilter"), "the request parameters moved to the fresh root");
            assertTrue(newUmbrella.getParameters().has("matchingSetFilter"), "and the new run inherited them");
        } finally {
            check.rollbackAndClose();
        }
    }

    // ---- lookups ----

    static Task umbrellaFor(Task d, Encounter enc) {
        String firstAnn = annotationIds(enc).get(0);
        for (Task u : childrenOf(d)) {
            if (u.holdsAnnotationId(firstAnn)) return u;
        }
        return null;
    }

    static List<String> hotspotterOptionIds(String dId) {
        List<String> ids = new ArrayList<String>();
        Shepherd sh = open("hsOptions");
        try {
            for (Task u : childrenOf(Task.load(dId, sh))) {
                if (Task.isOptionTask(u) && !u.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) ids.add(u.getId());
                for (Task opt : childrenOf(u)) {
                    if (Task.isOptionTask(opt) && !opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) ids.add(opt.getId());
                }
            }
        } finally {
            sh.rollbackAndClose();
        }
        return ids;
    }

    // the vector leaf (the option's representative) for the encounter's first annotation
    static String vectorLeafId(String dId, Encounter enc) {
        String firstAnn = annotationIds(enc).get(0);
        Shepherd sh = open("vectorLeaf");
        try {
            Task u = umbrellaFor(Task.load(dId, sh), enc);
            for (Task opt : childrenOf(u)) {
                if (!opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) continue;
                for (Task leaf : childrenOf(opt)) {
                    if (leaf.holdsAnnotationId(firstAnn)) return leaf.getId();
                }
            }
            throw new IllegalStateException("no vector leaf for " + firstAnn);
        } finally {
            sh.rollbackAndClose();
        }
    }

    static JSONObject summary(String importId) {
        Shepherd sh = open("summary");
        try {
            ImportTask itask = sh.getImportTask(importId);
            return itask.iaSummaryJson(sh);
        } finally {
            sh.rollbackAndClose();
        }
    }
}
```

Deviation from §5.2, stated: the "persisted registration flag change through a real identify" case is not here. A real `beginIdentifyAnnotations` builds the matching set through OpenSearch (`Annotation.getMatchingSet`), which this test has no seam for; the flag flip is verified at the unit level in Task 9 (`verify(ann).setWbiaRegistered(TRUE)` on the attached object, which the consumer's transaction commits) and live by staging check §5.3-5.

- [ ] **Step 3: Run the datastore test**

Run: `mvn -q test -Dtest=MultiAlgorithmIntakeDbTest -DfailIfNoTests=false` (Docker must be running; first run pulls `postgres:15-alpine`).
Expected: PASS (16 tests). If `FeatureType.initAll` complains about a missing table on the very first run, run the class a second time (autoCreateTables creates it on first touch). If `LocalAssetStore.create` needs a different webRoot form, mirror `EncounterExportImagesTest` lines 797-802 exactly.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ecocean/IAJsonProperties.java src/test/java/org/ecocean/TestIAJsonPropertiesUtil.java src/test/java/org/ecocean/ia/MultiAlgorithmIntakeDbTest.java
git commit -m "Datastore tests for the multi-algorithm intake, selector and import status (#1787)

Testcontainers PostgreSQL coverage of spec §5.2: persist-before-dispatch,
initial persist failure, reload and render of a five-level tree, the status
matrix, mid fan-out, per-group recovery, dispatch exceptions, the
single-HotSpotter exemption, the requeue ordering with a real _doIdentify,
mixed no-option groups and handleRest root substitution.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 5: Codex review** of the test class against §5.2 (ask which listed cases are still uncovered; add any it names that do not need WBIA or OpenSearch).

---

### Task 12: Whole-branch verification

**Files:** none new.

- [ ] **Step 1: Compile and run every test class this plan touched**

```bash
mvn -q compile
mvn -q test -Dtest='TaskSelectRunTest,TaskSelectPreferredMatchTaskTest,EmbeddingTest,IAIntakeCoreTest,IAHandleRestRootTest,IAGatewayDoIdentifyTest,MlServiceProcessorMatchIntakeTest,MlServiceProcessorGateTest,MlServiceProcessorTest,WildbookIAMForcedIdTest,WildbookIAMRegisterTest,WildbookIAMImagePhase0Test,IBEISIASendAnnotationsAsNeededTest,ImportTaskIdentificationStatusTest,ImportTaskTest,IdentificationTest,ProjectIATransactionTest,MultiAlgorithmIntakeDbTest' -DfailIfNoTests=false
```
Expected: all PASS.

- [ ] **Step 2: Run the whole suite** (in the background; it is long)

```bash
mvn -q test > /tmp/claude-1000/-mnt-c-Wildbook-clean2/5b68f463-03b0-46fb-9699-0e06b195adfa/scratchpad/full-test.log 2>&1; echo EXIT=$?
```
Expected: `EXIT=0`. Any failure in a class this plan did not touch is reported to the user as is (do not fix unrelated flakiness silently).

- [ ] **Step 3: Line endings and diff hygiene**

```bash
git diff origin/main --name-only | xargs -I{} sh -c 'grep -l $'"'"'\r'"'"' {} && echo "CRLF in {}"' ; git status --short
```
Expected: no `CRLF in` lines; clean tree.

- [ ] **Step 4: Codex whole-branch review** (codex-review skill): point it at `git diff origin/main...HEAD --stat` file list and the spec; ask for a verdict on spec coverage (§4.0-4.5, §5.1-5.2) and any Critical/Major defect. Fix findings in follow-up commits and re-run Step 1.

- [ ] **Step 5: Update the memory file** `project_issue_1787_ml_service_multi_algorithm.md` with the implementation state (commits, what is verified, what the §5.3 staging checks still owe), and stop. Pushing, opening the PR (with Codex credit and the attribution line), the milestone PATCH and the §5.3/§5.4 live checks are the next conversation steps, not part of this plan.
