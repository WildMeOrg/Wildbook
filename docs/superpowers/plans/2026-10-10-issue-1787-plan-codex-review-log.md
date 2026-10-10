# Issue #1787 implementation plan — Codex review log

Plan: `docs/superpowers/plans/2026-10-10-issue-1787-ml-service-multi-algorithm.md`. Reviewer: Codex CLI (gpt-6.1-sol, read-only sandbox), one round per plan revision; each round's prompt embedded the previous round's findings and asked for a disposition of each fix plus a re-check of five points (spec coverage §4.0–4.5 / §5.1–5.2, code against the real source, test soundness, task ordering and interfaces, §3 non-goal regressions).

| round | plan rev | commit | findings |
|---|---|---|---|
| 1 | 1 | 7e411797bb | 13 Major, 2 Minor |
| 2 | 2 | 916d57f157 | 3 Major, 1 Minor |
| 3 | 3 | 846de96f81 | 3 Major |
| 4 | 4 | bbacfdacce | 4 Major, 1 Minor |
| 5 | 5 | be51c154df | 2 Major |
| 6 | 6 | dc8edf9b7a | 1 Major, 1 Minor |
| 7 | 7 | c5a228223c | CONVERGED, none |

## Round 1

**The plan is not correct or converged.** Read-only review; no builds or tests run.

1. **Major — Task 3: test code does not compile.**  
   `assertFalse(Embedding.isVectorConfig(...), "blank method is not vector")` and the other message-last assertions resolve against `EmbeddingTest`’s existing `import static org.junit.Assert.*;`. JUnit 4 has no `(boolean, String)` overload. **Fix:** replace that import with `org.junit.jupiter.api.Assertions.*`.

2. **Major — Task 6: Mockito setup executes the real priming wait.**  
   `mockStatic(IBEISIA.class, CALLS_REAL_METHODS)` followed by `ibeis.when(IBEISIA::waitForIAPriming).thenAnswer(inv -> null)` invokes the real method while defining the stub. It can wait approximately five minutes. The real `beginIdentifyAnnotations` and `log` bodies also execute during their stubbing. **Fix:** use a default static mock for `IBEISIA`, then explicitly stub the methods `_doIdentify` needs; keep `_doIdentify` itself real.

3. **Major — Task 1: string identity assertion fails with correct code.**  
   `assertSame(u.getId(), Task.runIdOf(u));` assumes reference identity. `Task.getParameters()` reparses serialized JSON, so the returned run-id string is a different object. **Fix:** use `assertEquals` and add its import.

4. **Major — Task 11: IA.json fixture cannot resolve its options.**  
   `new JSONObject().put("Pseudobatos.lentiginosus", guitarfish).put("Rhina.ancylostoma", rhina)` creates literal dotted keys. `JsonProperties.get` splits lookup keys on dots and expects nested genus/species objects. Consequently, these fixtures produce no options. **Fix:** nest `lentiginosus` inside `Pseudobatos`, and `ancylostoma` inside `Rhina`.

5. **Major — Task 11: import stamps invalidate the status assertions.**  
   `detectionTask(sh, "IMP-3")` stamps the run with `"IMP-3"`, while `importWith` constructs `new ImportTask(null, "import-" + (++seq))`. `statsAnnotations` scopes selection to the actual import id; those umbrellas are foreign. It falls back to the newest direct task instead of aggregating the run. **Fix:** construct the import first and stamp its actual id onto the root/detection task.

6. **Major — Task 11: error assertion contradicts §4.4.**  
   In `importStatusCountsEveryOptionOfTheRun`, `setStatus(vectorOptionId, "error")` leaves that option’s completed vector leaf unchanged, then expects `identificationNumberError == 1`. The completed leaf remains the representative under §4.4. **Fix:** mark the representative leaf error, or construct a failed option without a leaf for that annotation.

7. **Major — Task 5: substituted roots lose request parameters.**  
   `resolveRequestRoot` replaces the loaded task with `root = new Task();` and restores only `fastlane`. It drops inherited `matchingAlgorithms`, `matchingSetFilter`, `importTaskId`, and other request parameters. This violates §4.0’s “copying the request’s parameters” requirement and changes identification behavior. **Fix:** copy the intended request parameters onto the fresh root, scrubbing run-specific keys, and test preservation of the filter and algorithm override.

8. **Major — Tasks 4/7: §4.0 detection-boundary scrubbing has no implementing task.**  
   The plan implements `scrubRunParams` for annotation intake and `matchIntakeParams` for matching, but never edits `IA.intakeMediaAssetsOneSpecies`’s real parameter copy: `topTask.setParameters(parentTask.getParameters())`. Detection tasks therefore still inherit identification-run keys. **Fix:** scrub the run id, vector marker, option marker, and identification option at media intake before persistence; retain unrelated parameters.

9. **Major — Task 4: the core can throw after persistence and lose the recovery record.**  
   `String baseUrl = getBaseURL(context);` is outside the dispatch exception guards. For example, a configured `containerName` with a null server URL makes the real `getBaseURL` throw. The umbrella/options are already durable, but Task 7 never receives the outcome and never adds its `GroupRecord`; recovery marks only `D`. This violates §4.2.3’s “never throws after the persist step” contract. **Fix:** guard the entire post-persist phase and always return the populated outcome, terminalizing options that could not dispatch.

10. **Major — Task 9: pending queries can bypass forced registration.**  
    The plan says `allAnns` contains “only the validated query annotations” and passes it to `sendAnnotationsAsNeeded(allAnns, tanns, ...)`. The unchanged preceding `validForIdentification` filter excludes a FALSE query with an invalid bbox. In a multi-query call, another valid query can proceed instead of the required query failure. **Fix:** include every FALSE query in the role-aware registration call; preserve the existing validation filter for legacy queries.

11. **Major — Task 9: target failures can escape instead of pruning targets.**  
    In the target loop, `new HashSet<String>(WildbookIAM.iaAnnotationIdsStrict(context))` runs outside a target-specific failure guard. Its `IOException` aborts the identify job rather than dropping the pending target. Runtime failures in eligibility likewise escape. **Fix:** handle lookup, eligibility, and registration failures per target; record/drop failed targets while retaining exception propagation for queries.

12. **Major — Task 11: fixtures omit the required media relationships.**  
    `new Annotation(null, f, iaClass)` is used without creating any `MediaAsset`, attaching `f` to an asset, or explicitly setting `f.setAnnotation(ann)`. These are not the §5.2 fixtures “with media assets and bbox features”; the constructor only fills the inverse feature list. **Fix:** create persistent assets, wire both feature owners explicitly, set skip-auto-indexing on assets too, and verify those relationships after reload.

13. **Major — Tasks 10/11: required test cases remain unimplemented.**  
    The supplied tests omit §5.1’s **error plus pending → pending** case. Task 11 also omits datastore coverage for initial persist failure with zero dispatches; error-plus-pending, single-HotSpotter pending, skipped-class, and older-run late-fan-out summaries; actual retry failure with an HTTP-error log and polling between commits; persisted registration flag changes and real identify failure; and the partial-dispatch/mid-fan-out aggregate cases. `setStatus(id, Task.STATUS_REQUEUED)` does not test the required failure/requeue ordering. **Fix:** add those cases using the actual intake/identify paths and second PersistenceManager polling.

14. **Minor — Task 9: the specified public interface is missing.**  
    The plan produces only `sendAnnotationsAsNeeded(qanns, tanns, Shepherd, JSONObject report)`. §4.2.5 requires the three-argument role-aware method. **Fix:** add that overload delegating to the four-argument implementation with a null report.

15. **Minor — Task 9: the asset-minting test does not verify the forced identifier.**  
    `assetWithoutAcmIdIsMintedFromItsUuidBeforeTheImageIsForced` uses a mock whose `getAcmId()` remains null after `setAcmId(MA_UUID)`, then asserts only `plugin.imageCalls.size() == 1`. It passes even though the recorded image id is null. **Fix:** make the mock stateful and assert `plugin.imageCalls.equals(Arrays.asList(MA_UUID))`.
## Round 2

**The plan is not correct or converged.** Read-only review; no builds or tests run.

Round-1 disposition: **#1–8, #10–12, and #14–15 are correctly and completely fixed. #9 and #13 remain incomplete**, as detailed below. No additional Java 11, Mockito stubbing, missing-signature/import, task-ordering, or §3 legacy-caller regression was identified.

1. **Major — Task 4: the core still can throw after persistence and lose the recovery record.**  
   At [plan line 1734], `myShepherd.beginDBTransaction();` executes **before** `outcome.options` is populated and outside any exception guard. The real method catches `JDOUserException` and `NullPointerException`; other datastore failures can escape. The dispatch catch also calls `markTaskError` and `terminalizeUnfinishedChildren` without guarding failures in those calls. Task 7 then never receives the durable group’s outcome. Guarding `getBaseURL` alone does not satisfy §4.2.3.  
   **Fix:** populate every `OptionResult` before the durable commit, guard the entire post-commit phase—including transaction restart and failure marking—and always return the populated outcome. Add a restart-failure test proving Task 7 recovers all unpublished options. **Round-1 #9 remains incomplete.**

2. **Major — Tasks 9/11: required registration-through-identify tests remain omitted.**  
   [Task 11’s explicit deviation] says:  
   > “the ‘persisted registration flag change through a real identify’ case is not here.”  
   
   Task 9’s `verify(q).setWbiaRegistered(Boolean.TRUE)` proves a mock invocation, not persistence or the real `beginIdentifyAnnotations` failure result. The OpenSearch justification is incorrect: the real method skips `getMatchingSet` when given a **nonempty explicit target list**. The §5.2 registration cases remain unit-only.  
   **Fix:** add datastore cases using attached annotations, explicit nonempty targets, mocked WBIA calls, and the specified construction mocks for internally opened Shepherds. Reload to verify the flag and minted asset id; exercise the real identify method to assert failed queries return `success=false` and dropped targets are absent from the sent request. **Round-1 #13 remains incomplete.**

3. **Major — Tasks 10/11: additional §5.2 datastore cases still have no implementing test.**  
   `midFanOutReadsPendingForTheUnfannedAnnotation` covers an ordinary multi-option run. `vectorDispatchExceptionMarksTheOptionAndItsAttachedLeaf` uses separate classes with one annotation each. Neither implements these required persisted cases:
   - Partial vector dispatch: `V_A` exists, `V_B` does not, and `H_B` completed.
   - Single-HotSpotter `U(A,B)` mid-fan-out beside a newer completed run for A.
   - Dispatch throws after attaching A’s leaf; successful commit leaves B represented by the failed option.
   - Pre-fix reused-root selection and scoped foreign-only root fallback.

   Also, `selectorLandsOnTheUmbrellaAfterReloadAndRendersExactlyItsRun` selects a **new stamped umbrella directly**; it never exercises the five-level old-tree ancestor walk whose attached-object behavior §5.2 requires validating.  
   **Fix:** add the missing persisted fixtures and assertions through `statsAnnotations → iaSummaryJson`, plus a reloaded five-level **old tree** that actually invokes the structural climb. Task 10’s unit cases do not replace these datastore requirements.

4. **Minor — Task 9: pending-query failures do not consistently become `IOException`.**  
   At [plan line 3449], the query branch calls `registerPendingAnnotation(...)` without an exception guard. That helper performs media access, minting, and eligibility outside its POST-specific `IOException` catch. Runtime failures therefore escape as runtime exceptions, contrary to §4.2.5’s query-failure contract. The target guard is correctly fixed.  
   **Fix:** wrap the complete pending-query lookup/registration operation and convert failures to `IOException` with the annotation id and original cause. Add a runtime-eligibility-failure assertion.
## Round 3

**The plan is not correct or converged.** Read-only review; no builds or tests run.

Round-2 disposition: **#1–3 are partially fixed but remain incomplete. #4 is correctly and completely fixed**: pending-query lookup and registration now convert runtime failures to `IOException` with the annotation id and cause.

1. **Major — Task 4: post-commit logging can still throw and lose the recovery outcome.**  
   [Line 1835] logs `"dispatch threw " + ex + " on " + t` **before** setting `failureCode`. The marking-failure catch at line 1842 also concatenates `t`; the final log at line 1846 concatenates `topTask` outside any guard. Real `Task.toString()` reads persistent fields and collection sizes, including the lazily fetched `children`. A datastore failure during those reads escapes, so Task 7 never records the durable outcome. Recording options before commit and guarding restart are correct; the §4.2.3 “never throws after persistence” contract remains unmet.  
   **Fix:** capture scalar ids before committing, remove entity serialization from post-commit logging, and guard the entire post-commit phase. Add a broken-PersistenceManager dispatch test proving the populated outcome returns and recovery marks every unpublished option.

2. **Major — Tasks 9/11: §5.2 registration coverage remains incomplete.**  
   [Task 11’s registration seam] always answers `iaAnnotationIdsStrict` with an empty list. Its two new real-identify tests cover successful pending registration, minting, target pruning and query refusal, but cannot cover **FALSE already present under `acmId` → persisted TRUE without a POST**. Persisted null-flag legacy registration and persisted `iaCheckMissing` forced repair also remain absent; those cases still exist only in Task 9’s mock tests.  
   **Fix:** add the remaining datastore registration cases with attached annotations and reload assertions. Use the real registration methods with mocked WBIA requests, and construction mocks where real code opens Shepherds. Assert no POST for an already-present pending annotation, persisted TRUE after repair, and unchanged null-flag behavior.

3. **Major — Task 11: the added persisted fixtures do not fully implement Round-2 #3.**  
   [The partial-dispatch test] only executes:
   ```java
   for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
   ```
   It never creates the required completed **`H_B` fan-out leaf** beside missing `V_B`. Also, [the old-tree fixture] checks selectors directly; its reused-root and foreign-only cases have no persisted import or `statsAnnotations → iaSummaryJson` assertions. The five-level old-tree structural climb itself is correctly added.  
   **Fix:** persist a completed `H_B` with the option marker removed and assert B’s aggregate error. Attach the old-tree fixtures to persisted imports and assert their `encounterTaskInfo` landing ids through `iaSummaryJson`.

The other five-point checks found no additional signature/import errors, Java 11 or Mockito defects, interface/task-order mismatches, selector stop-rule or representative-rule violations, recovery-exemption errors, or forbidden §3 caller changes.
## Round 4

**The plan is not correct or CONVERGED.** Read-only review; no builds or tests run.

Round-3 disposition:

- **#1 partially fixed:** entity serialization is removed and dispatch has an outer guard. Pre-commit id snapshots remain absent, and the new datastore regression has invalid expectations.
- **#2 partially fixed:** all three cases are added with reload assertions, but registration still runs through fake plugin methods.
- **#3 correctly and completely fixed:** completed `H_B` is persisted without the option marker; the old-tree fixtures now assert landing ids through persisted imports’ `iaSummaryJson`.

1. **Major — Task 4: the test does not compile.**  
   [Line 1085] declares `static final class RecordingDispatcher`, but [line 1346] uses `new RecordingDispatcher() { ... }`. Java forbids subclassing a final class.  
   **Fix:** remove `final`, or add a runtime-publication-failure flag to the helper and use an ordinary instance.

2. **Major — Task 11: datastore setup persists outside a transaction.**  
   [Lines 4564–4566]:
   ```java
   sh.beginDBTransaction();
   FeatureType.initAll(sh);
   sh.getPM().makePersistent(store);
   ```
   Real `FeatureType.initAll` rolls back its query transaction and, when initializing types, commits its creation transaction. Either path leaves no active transaction. With the plan’s `datanucleus.NontransactionalWrite=false`, persisting `store` fails, blocking the entire class.  
   **Fix:** call `FeatureType.initAll(sh)` first, then `sh.beginDBTransaction()` before persisting the store.

3. **Major — Task 11: the dead-transaction test expects publications that cannot occur.**  
   [Line 5954]:
   ```java
   assertEquals(2, dispatcher.published.size(), "the HotSpotter jobs were still published");
   ```
   The dispatcher rolls back the transaction. With nontransactional writes disabled, Task 4’s subsequent `t.setQueueResumeMessage(...)` fails **before** `dispatcher.publish(...)`. Both HotSpotter options therefore remain unpublished; their recovery marks must be `MATCH_PERSIST`, not null.  
   **Fix:** expect zero publications and assert `MATCH_PERSIST` on all four options and the umbrella. Also add the requested broken-PersistenceManager case; rollback alone leaves the manager open and does not exercise failed lazy reads.

4. **Major — Tasks 9/11: required registration integration coverage still bypasses the real methods.**  
   [The `RecordingPlugin` overrides] replace `registerImageForced`, `sendAnnotationForcedByAcmId`, `sendAnnotations` and `sendMediaAssets` with recording/success stubs. The new tests prove persisted flag changes and branch routing, but cannot verify actual forced request construction, acknowledgement validation, or legacy registration behavior on attached annotations. This leaves §5.2’s specified registration coverage incomplete.  
   **Fix:** use real `WildbookIAM` registration methods with mocked `RestClient` requests and id-list responses; intercept internally constructed Shepherds where necessary. Assert request UUIDs, zero registration POSTs when already present, persisted TRUE after repair, and unchanged null flags.

5. **Minor — Task 4: scalar ids are captured after commit.**  
   [Line 1800] assigns `String umbrellaId = topTask.getId()` after persistence and outside the dispatch guard; option ids are also read afterward. This omits Round-3’s requested pre-commit snapshots.  
   **Fix:** capture umbrella and option ids before committing and use those values throughout post-commit logging.

The five-point re-check found no other unassigned production requirements, source-signature/import or Java 11 defects, Mockito defects, task-order/interface mismatches, selector/status/recovery-rule violations, or forbidden §3 caller changes. The remaining §5.2 coverage gap and test failures are listed above.
## Round 5

**The plan is not correct or CONVERGED.** Read-only review; no builds or tests run.

Round-4 disposition:

- **#1 correct and complete:** `RecordingDispatcher` is no longer final.
- **#2 correct and complete:** the store persistence transaction now begins after `FeatureType.initAll`.
- **#3 correct and complete:** rollback expects zero publications and `MATCH_PERSIST` throughout; the closed-manager variant is added.
- **#4 partially fixed:** registration now calls real `WildbookIAM` methods, but the datastore defects below prevent the required verification.
- **#5 correct and complete:** umbrella and option ids are captured before commit.

1. **Major — Task 11: ambiguous constructor prevents compilation.**  
   [Line 5381]:
   ```java
   new IdentityServiceLog(hsId, null, "IBEISIA", "-1",
       new JSONObject()...)
   ```
   The real class has both `(String, String[], String, String, JSONObject)` and `(String, String, String, String, JSONObject)` constructors. Literal `null` cannot select between them.  
   **Fix:** use `(String[]) null`, or the existing four-argument constructor.

2. **Major — Task 11: fixtures assume persistent fields survive commit and close.**  
   The test properties leave DataNucleus 5.2.7’s `RetainValues=false` and `DetachAllOnCommit=false` defaults in effect. Commit clears non-primary-key fields; closing the manager disconnects the objects.

   The setup [persists, commits and closes the static store], then [line 4634] calls:
   ```java
   MediaAsset ma = store.create(testImage);
   ```
   Its persistent `config` has been cleared, so `LocalAssetStore.root()` dereferences null. Every encounter fixture fails.

   Independently, [line 5061] calls `idSet(e1)` after close, although `e1.annotations` has been cleared. The registration fixtures likewise read:
   ```java
   wbia.knownAnnotIds.add(k.getAcmId());
   wbia.knownImageIds.add(k.getMediaAsset().getAcmId());
   ```
   [Lines 5786–5787] therefore obtain cleared fields and dereference a null asset; the legacy fixture repeats this with `t`.

   **Fix:** retain only the store’s scalar id and load an attached store inside each fixture’s Shepherd. Snapshot annotation-id collections and WBIA-known UUIDs before commit, and change helpers such as `umbrellaFor` and `vectorLeafId` to accept those snapshots—or reload the encounter in their active Shepherd. Audit every post-close fixture dereference.

Five-point re-check:

- **Coverage:** all requested production requirements and §5.1/§5.2 cases have task assignments; no additional unassigned requirement or production contradiction found.
- **Real-source correctness and test soundness:** the two blockers above remain.
- **Ordering/interfaces:** no additional mismatch found.
- **Existing callers:** no additional forbidden §3 behavior change found.

**Minor items: none.**
## Round 6

**The plan is not correct or CONVERGED.** Read-only review; no builds or tests run.

Round-5 disposition:

- **#1 correct and complete:** the four-argument `IdentityServiceLog` constructor exists and removes the ambiguity.
- **#2 partially fixed:** attached-store loading and the snapshot registry eliminate the reported lifecycle defects, but the registry implementation introduces the compilation blocker below. Auditing all remaining Task 11 post-commit reads found no further unsafe fixture dereferences: they read primary keys, snapshots, or objects loaded through an active Shepherd. This includes `annsD`, outcome task ids, and annotations retained in `wbia.sentTargets`.

1. **Major — Task 11: conflicting local variables prevent compilation.**  
   [Lines 4666–4697]:
   ```java
   Fx f = new Fx(enc.getCatalogNumber());
   ...
   Feature f = new Feature("org.ecocean.boundingBox", ...);
   ...
   f.annIds.add(ann.getId());
   f.acmByAnn.put(ann.getId(), ann.getAcmId());
   f.assetAcmByAnn.put(ann.getId(), ma.getAcmId());
   ```
   Java forbids redeclaring `f` within the enclosing local variable’s scope. `Feature` also has none of those snapshot fields.  
   **Fix:** rename the outer variable to `snapshot`; use `snapshot.annIds`, `snapshot.acmByAnn`, `snapshot.assetAcmByAnn`, and `fixtures.put(snapshot.catalogNumber, snapshot)`. Keep `f` exclusively for the feature.

Minor items:

2. **Minor — Task 11: the late fan-out fixture incorrectly retains option identity.**  
   [Line 5232]:
   ```java
   Task child = new Task(hs1);
   ```
   This inherits `identificationOption: true`, unlike real `_doIdentify` fan-out children required by §4.0.  
   **Fix:** immediately add:
   ```java
   child.setParameters(Task.withoutOptionMarker(hs1.getParameters()));
   ```

Five-point re-check: all specified requirements and §5.1/§5.2 case classes have task assignments; no additional production contradiction or source mismatch found. Test soundness has the findings above. Task ordering and Interfaces have no additional mismatch. Existing callers have no additional forbidden §3 behaviour change.
## Round 7

**CONVERGED.** No Critical, Major, or Minor findings remain. Read-only review; no builds or tests run.

Round-6 dispositions:

1. **Task 11 — snapshot rename: correct and complete.** `Fx snapshot` owns all registry writes and `fixtures.put(snapshot.catalogNumber, snapshot)`; `Feature f` no longer conflicts.
2. **Task 11 — late fan-out marker removal: correct and complete.** The child immediately receives `Task.withoutOptionMarker(hs1.getParameters())`, preserving run identity while removing option identity.

Five-point re-check:

1. **Coverage:** every required §4.0–§4.5 requirement and §5.1/§5.2 test case class maps to a task. No omissions or contradictions found.
2. **Implementation:** no additional source/signature/import mismatch, Java 11 violation, or divergence from the selector, representative, or recovery rules found.
3. **Tests:** no additional Mockito defect, unsafe fixture dereference, persistence assumption, or assertion contradicting correct behavior found.
4. **Ordering/interfaces:** consumed names and signatures are available before use.
5. **Existing callers:** no additional behavior change forbidden by §3 found.

Minor items: none.