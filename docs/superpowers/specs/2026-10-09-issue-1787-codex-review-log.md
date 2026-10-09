# Issue #1787 design spec — Codex review log

Eight adversarial review rounds of `2026-10-09-issue-1787-ml-service-multi-algorithm-match-results-design.md` by Codex (codex-cli, read-only review of the spec plus the cited source files). Each round reviewed the revision named; the next revision answered it. Round 8 converged with no findings. Kept for PR provenance.

---

## Round 1 (spec rev 1)

**Not converged. Layer A’s shared-intake approach is sound, but the selector, status accounting, persistence contract, and WBIA registration need changes before implementation.** Static review only; no files modified or tests run.

1. **Major — §4.2’s ancestor-safety proof is too broad.**  
   The listed fresh orchestration builders do use media assets or no annotations: `intakeMediaAssets`, `enqueueOneAssetForMlService`, the newly created `handleRest` root, the `IBEISIA.processCallback` sub-parent, and the `ParallelIdentify` sub-parent. Intake’s per-class/per-option children **do** hold annotations and are legitimate identification ancestors. `_doIdentify` also retains annotations when clearing its parent’s parameters.

   However, an extraction task can hold annotations: `Annotation.createFromApi` constructs one that way, and `processExtraction` imposes no media-asset-only contract on its supplied task. The legacy callback creates a separate identification sub-parent, so its extraction task is not currently an ancestor of that umbrella; §4.1’s new direct attachment makes the extraction-parent boundary relevant. `handleRest` can also reuse an existing task rather than create a fresh root.

   A healthy parent/children chain cannot lose the winner’s results merely by hoisting: `matchResultsJson` recursively includes them. **Wrong additional content remains possible**, because the checks inspect the ancestor’s annotations and stamp, not its entire rendered subtree. Root fallback can already be empty; hoisting does not cure that.

   **Fix:** bound hoisting to the identification invocation’s umbrella. Explicitly exclude extraction/detection orchestration tasks, and validate subtree scope where legacy structure provides no reliable boundary. Correct the “every ancestor is an identification umbrella” claim.

2. **Major — scope-compatible ancestors do not guarantee import isolation.**  
   For scope `I`, the proposed rule permits:

   | Winner stamp | Ancestor stamp | Proposed result |
   |---|---|---|
   | `I` | `I` | Climb |
   | `I` | absent | Climb |
   | absent | `I` | Climb |
   | absent | absent | Climb |
   | either usable winner | foreign | Stop |

   Those two mixed stamped/unstamped cases are not proof that every rendered sibling belongs to the winning request. Unscoped selection performs **no ancestor stamp check at all**, allowing a stamped winner to climb across another stamp.

   `_doIdentify`’s `setParameters(null)` makes this concrete: a stamped HotSpotter leaf has an unstamped option parent. The proposed check allows climbing through it; it does not stop wrongly. Conversely, unconditional stamp equality would stop there and could leave the link showing only HotSpotter.

   **Fix:** use the winner’s normalized stamp as the default boundary, including unscoped selection. Preserve orchestration parameters, especially `importTaskId`, when `_doIdentify` clears algorithm parameters. For existing cleared parents, permit a transparent unstamped bridge only with verified descendant scope; otherwise stop. Blanket “unstamped or this import” is insufficient.

3. **Major — the claimed import-status behavior is false.**  
   [ImportTask.statsAnnotations](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/servlet/importer/ImportTask.java:427) counts only the newest direct task’s status per annotation. Option tasks are created before inline vector leaves; therefore a completed vector leaf can be newest while HotSpotter remains queued or processing. The import can report complete and stop polling. Later WBIA fan-out makes the result timing-dependent.

   Changing the preferred link or its displayed `getOverallStatus` does not change those counters. Detection/extraction already marks `D` completed before matching, and the ml-service consumer merely logs its outcome; neither requires synchronous HotSpotter completion. `finalizeZeroDetections` remains appropriately terminal without identification.

   **Fix:** calculate identification completion from every applicable algorithm branch of the selected identification invocation, scoped to the annotation/import. Count an annotation terminal only when all its required branches are terminal, retaining a separate error signal.

4. **Major — §4.1 is not an atomic persistence operation.**  
   [Shepherd.storeNewTask](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/shepherd/core/Shepherd.java:315) commits and reopens the **whole** transaction. Intake invokes it per class, publishes HotSpotter jobs, and leaves the final vector work pending for the caller’s commit. Thus results and jobs can exist before `D → U` is persisted; subsequent failure cannot roll back earlier work. Copying the existing callback pattern does not establish atomicity.

   `D.addChild(U)` also does not set `U.parent` in memory; it relies on DataNucleus relationship management for the mapped-by collection. Commit failures can be swallowed by `commitDBTransaction`, and intake ignores `storeNewTask` failure.

   **Fix:** define and check persistence stages explicitly; wire `U.setParent(D)` before dispatch where feasible, verify successful commits, and handle partially dispatched work. Roll back the active Shepherd before error marking through another Shepherd. Preserve the existing missing-parent fallback deliberately, or return stale explicitly.

5. **Major — on-demand WBIA registration can change annotation identity.**  
   The risk section understates this. `beginIdentifyAnnotations` calls `sendAnnotationsAsNeeded`, whose [non-forced registration](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:264) omits `annot_uuid_list`. `AcmUtil.rectifyAnnotationIds` then replaces `acmId` with WBIA’s returned UUID. Newly detected annotations start with `acmId == id` and `wbiaRegistered=false`; immediate HotSpotter dispatch can race the background forced-ID registrar.

   This is an existing New Match/resend weakness that the first-pass change exposes routinely. The forced-registration code explicitly documents identity churn and orphaned prospect references as hazards.

   **Fix:** ensure image and annotation registration using the stable forced `acmId` before identification, coordinated with the background registrar. Test identification before background registration, simultaneous registration, and an initially empty WBIA.

6. **Major — the new overload breaks compilation unless null calls change.**  
   [IA.intakeAnnotations](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/IA.java:373) currently calls:

   ```java
   intakeAnnotations(myShepherd, anns, null, false);
   ```

   Adding unrelated `Task` and `JSONObject` overloads makes that call ambiguous.

   Per-class deep copying otherwise preserves parameter isolation, provided null inherited parameters become a fresh empty object inside each iteration. Non-null tasks with null parameters are legitimate: a fresh `handleRest` root has that shape, and `_doIdentify` explicitly creates it. `setParameters` serializes immediately, so existing option siblings do not share later JSON mutations.

   **Fix:** preferably give the parameter-based entry point a distinct name; alternatively cast every literal-null call. Cover null parent, null parent parameters, and multiple classes. Replace “byte-for-byte identical” with the precise compatibility guarantee—the new metrics flag intentionally changes parameters.

7. **Major — first-pass multi-encounter trees still cannot show both algorithms safely.**  
   §4.1 sends the entire detection batch into one intake. Detection can assign those annotations to different encounters. If both share an iaClass, vector and HotSpotter option parents both hold annotations from multiple encounters. The #1744 guard correctly blocks climbing, leaving a single algorithm leaf selected.

   Thus the proposed rule preserves #1744 for these builder-generated trees, but fails the goal of showing both algorithms for every bulk-import encounter.

   **Fix:** group detected annotations by encounter before intake, creating one identification umbrella per encounter under `D`. Existing mixed-encounter trees need encounter-filtered rendering if they must also expose both algorithms without rebuilding tasks.

8. **Major — no-work detection and queue-failure handling are incomplete.**  
   `intakeProducedNoWork` recognizes only total structural absence. With multiple classes, one class can have no options while another creates children, making the helper return false despite skipped annotations. Existing inherited `matchingAlgorithms` also overrides defaults, so “every default option” is conditional on that parameter.

   Intake logs `sent=false` or an enqueue `IOException` without marking the option terminal. Such a task still passes the proposed work check.

   **Fix:** return structured per-class/per-option outcomes, including skipped classes and failed publication. Mark failed queue submissions terminal and distinguish “no applicable options” from “dispatch failed.” Specify whether ml-service intake honors or removes explicit algorithm overrides.

9. **Minor — deferred/extraction behavior needs an explicit contract.**  
   Deferred re-fire still reaches the gate, and retaining `matchConfig` for visibility checks is coherent. However, intake reads current IA.json while the gate uses the deferred config; configuration changes or additional vector defaults can mean the gate does not cover the algorithms actually dispatched.

   `loadExtractionContext`’s `embedding-exists` early return is **pre-existing** and bypasses all matching. The later embedding-exists check in `persistExtraction` instead returns annotation IDs and proceeds to matching. Consequently, the design cannot claim every extraction request runs every default.

   **Fix:** distinguish extraction idempotency from identification completion. If extraction requests imply identification, route the early embedding-exists case through matching with duplicate-invocation protection. Otherwise document the exclusion. Align gating with the effective vector options.

10. **Minor — metrics placement works, but its description must change.**  
    Stamping the vector option before `findMatchProspects` correctly propagates `mlServiceV2Match` to vector leaves. HotSpotter siblings do not inherit that option-local addition. Counting New Match/resend leaves is acceptable **as the expressly chosen broader metric**; restricting it to ml-service provenance is unnecessary under that choice.

    [MetricsBot](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/MetricsBot.java:542) still describes ml-service v2 leaves, and the query tests key presence rather than Boolean truth.

    **Fix:** update the gauge HELP text and comments to “vector identification leaf tasks,” and keep the marker exclusively on vector branches. Document that this is a task count, including failures.

11. **Minor — Class merging is compatible; ordering and lookup failure need specification.**  
    The frontend accepts `[taskId, status, label]` and renders the label verbatim. Joined classes need no frontend change. Cached primary-key encounter loads avoid the documented `findByAnnotation` JDOQL bug, but add encounter/annotation collection loads on each summary request.

    Iterating `atm.keySet()` cannot establish encounter-annotation order because it is a `HashMap`.

    **Fix:** collect contributing classes per preferred task, then order them using the cached encounter’s annotation list. Exclude blank/null classes and deduplicate. If encounter loading fails, retain the existing unhoisted selector behavior. Measure a large import’s summary cost.

12. **Major — the test plan omits the failure modes above.**  
    Additional required coverage:

    - Full stamp matrix, unscoped stamp boundaries, and `_doIdentify`’s cleared-parameter tree.
    - Two encounters sharing an iaClass, each with both algorithms.
    - Waiting/error HotSpotter beside completed vector leaves, including later WBIA fan-out.
    - Commit/publication failure, persisted parent linkage, and reload through more than three ancestor levels.
    - Mixed supported/unsupported classes, explicit HotSpotter-only `matchingAlgorithms`, null parameters, and fastlane.
    - Embedding-exists extraction, deferred duplicates/configuration changes, and forced-registration races.

    `skipIdent` does not leak into WBIA algorithm options: intake constructs an explicit queue payload, and the ml-service gate handles skipping first. I found no implemented `hotspotterOnly` parameter; explicit `matchingAlgorithms` is the existing selection mechanism. Fastlane queue routing remains argument-driven, although intake currently adds fastlane parameters **after** serializing task parameters.

    `getOverallStatus` works at `U` for option tasks plus per-annotation leaves; it does not reliably summarize deeper orchestration trees. HotSpotter tasks commonly have null stored status, which [Task.getStatus](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/Task.java:799) explicitly exempts from the seven-day inactivity timeout. A missing callback can therefore remain pending indefinitely. Attached lazy loading makes the recursion-depth explanation reasonable, but a datastore reload test must verify it.

    **Fix:** add orchestration/datastore tests or a controlled pre-deployment integration check. Specify timeout recovery explicitly. Diff review plus a post-deployment live check is inadequate validation for this change.

**The design must change before implementation. The blocking changes are safe scope-aware hoisting, encounter-specific first-pass umbrellas, completion across all algorithms, checked persistence/dispatch outcomes, stable WBIA registration, and an unambiguous intake API.**
---

## Round 2 (spec rev 2)

**NOT CONVERGED.** Revision 2 fixes the intake API ambiguity and first-pass encounter grouping, but its completion, persistence, registration, and hoisting guarantees remain unsound. Static review only; no files modified or tests run.

The 12 round-1 dispositions are:

| R1 | Disposition |
|---|---|
| 1 | **Partially resolved.** New umbrellas provide a boundary; accepting old extraction ancestors still permits multiple identification invocations on one results page. |
| 2 | **Partially resolved.** Winner-stamp checks improve isolation, but immediate-child stamps do not validate the rendered subtree. Stamped ancestors bypass sibling validation altogether. |
| 3 | **Partially resolved.** Leaf aggregation fixes the successful-vector/pending-HotSpotter case. Error precedence, invocation selection, and parent errors remain wrong. |
| 4 | **Partially resolved.** `setParent` correctly wires the relationship, but unchecked internal commits and incomplete recovery remain. |
| 5 | **Partially resolved.** Forced registration is the right direction; visibility, transaction nesting, races, target registration, and UUID selection remain unresolved. |
| 6 | **Resolved.** The differently named five-argument entry point avoids overload ambiguity; null-safe parameter copying is specified. |
| 7 | **Resolved.** Grouping first-pass annotations by encounter supplies the required encounter umbrellas. |
| 8 | **Partially resolved.** Queue failures receive terminal statuses, but their durability and no-work/invocation accounting remain incomplete. |
| 9 | **Resolved.** Extraction idempotency and deferred configuration drift are explicitly excluded. Those exclusions narrow the “every default” guarantee. |
| 10 | **Resolved.** The broader vector metric and revised description are explicit. |
| 11 | **Resolved.** Full encounter membership, alphabetical class merging, and SQL-failure fallback are specified. |
| 12 | **Partially resolved.** Staging is now required, but the plan still omits the failure and concurrency cases below. |

1. **Major — §4.4 can report completion while an algorithm remains alive.**

   For leaves `[vector=error, HotSpotter=processing]`, the proposed aggregate returns `error`. `iaSummaryJson` counts that annotation as terminal and can stop polling before HotSpotter finishes. This violates goal 6.

   The formula remains usable, and `numLatestTasks` can remain exactly one per non-trivial annotation, **provided the aggregate counts an annotation terminal only after every required branch is terminal**. A completed vector plus pending HotSpotter correctly stays `sent`; an erroneous vector must do so too.

   `QUEUE_FAILED` and `WBIA_REGISTRATION_FAILED` can be terminal when publication never occurred and the error was committed. `SEND_FAILED` is different: [`IAGateway._doIdentify`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/servlet/IAGateway.java:429) requeues the exception after `_sendIdentificationTask` commits the error. That failed attempt can therefore still have a live retry.

   The inactivity assertion is also false. [`Task.modified`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/Task.java:95) advances through construction, `setModified`, and `setStatus`. Saving `IdentityServiceLog`, inspecting the WBIA queue, or receiving ordinary queue activity does not advance it. A null-status HotSpotter task can be alive for seven days and then be declared erroneous by this aggregate.

   **Fix:** give pending branches precedence over terminal classification; retain a separate error signal. Define retry exhaustion separately from attempt failure. Base inactivity on a worker heartbeat or verified job state, or explicitly describe it as a maximum-age policy that may terminate live work.

2. **Major — identification status cannot depend solely on the results-link selector.**

   A builder-produced intermediate tree can be:

   ```
   D
   ├── U_A(A): T_vec_A, T_hs_A
   └── U_B(B): T_vec_B, T_hs_B
   ```

   Before vector leaves exist, neither option holding one annotation qualifies under the existing renderability rules: its parent has two children and only one annotation. Neither umbrella qualifies when `D` has two children. Root fallback calls `onlyRoots`, which returns the orchestration root.

   The hoist checks never validate that initial winner. Its results page can span encounters. Likewise, a newer invocation without a renderable branch can lose to an older completed invocation, causing status aggregation to inspect the wrong run.

   **Fix:** select the identification invocation independently of result availability. Make a correctly scoped `identificationRoot` eligible while pending, and derive status from that invocation. Validate root fallback before using it as an encounter results link.

3. **Major — §4.3 still does not prove encounter, import, or invocation isolation.**

   The table walks as follows:

   | Shape | Actual landing and assessment |
   |---|---|
   | Old resend / legacy callback | `U`, assuming its rendered subtree belongs to the encounter and invocation. |
   | Fresh New Match | `U`; the media-asset parent stops ascent. |
   | Old ml-service single option | `M`; detection orchestration stops ascent. |
   | Old extraction | `E`; **incorrect when `E` contains multiple runs**. |
   | `_doIdentify` cleared option | Bridges to `U` when immediate children have the winner’s stamp; deeper scope remains unchecked. |
   | Old mixed-encounter fan-out | `SA`; encounter isolation survives, but this exposes only that algorithm branch. |
   | Foreign-stamped ancestor | Stops at the last permitted node, which need not be the leaf. |

   Concrete extraction counterexample:

   ```
   E(A)
   ├── M_old(A) → S_old(A)
   └── M_new(A) → S_new(A)
   ```

   With unstamped tasks, selection starts at `S_new`, climbs through `M_new` to `E`, and renders both invocations. Calling this accepted does not satisfy the requested exact-invocation guarantee.

   The bridge also accepts:

   ```
   bridge(unstamped)
   ├── branch(I) → winner(I)
   └── branch(I) → foreignLeaf(J)
   ```

   Every direct child carries `I`, yet recursive rendering includes `J`. Similarly, ancestor annotations belonging to the query encounter do not establish that every descendant’s annotations belong there.

   **Fix:** validate the rendered subtree’s encounter and invocation scope. Exclude extraction orchestration on legacy trees, or identify a reliable legacy invocation boundary. Where old mixed-encounter trees must expose every algorithm, add encounter-filtered rendering; selecting a single leaf cannot accomplish that.

4. **Major — the checked commit does not cover the persistence boundary that authorizes dispatch.**

   **The relationship wiring itself is sound:** with attached persistent `D`, `U.setParent(D)` sets the owning reference and updates `D.children`. A successful commit persists the relationship; another `makePersistent(D)` is unnecessary.

   But [`storeNewTask`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/shepherd/core/Shepherd.java:315) invokes `updateDBTransaction`, whose commit swallows exceptions. It can return `true` after a failed commit. A checked commit at the end cannot retrospectively prove that `U`, its options, and the link were durable before publication.

   Recovery is also insufficient. Marking `U` and `D` erroneous does not make their leaves erroneous: §4.4 ignores non-leaf status. A persisted childless vector option can remain pending after its vector leaf rolls back. The general exception path marks only `D`, potentially leaving committed umbrellas pending.

   The repository already has [`commitDBTransactionWithStatus`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/shepherd/core/Shepherd.java:3560); adding another near-duplicate is unnecessary.

   **Fix:** use checked persistence inside intake before each dispatch, abort on failure, and propagate structured outcomes. Define recovery for every committed required branch and already published job. Commit error marking—including step 6’s no-work marking—and make invocation failure visible to aggregation.

5. **Major — §4.2.4’s helper visibility does not compile.**

   `StartupWildbook` is in `org.ecocean`; `IA` is in `org.ecocean.ia`. Package-private helpers are inaccessible across those packages.

   **Fix:** expose a public registrar facade with a typed result. Keep implementation phases private rather than making callers compose them directly.

6. **Major — synchronous registration has neither transaction isolation nor an exclusive claim.**

   Intake still holds its outer Shepherd transaction while phases A and C open separate Shepherds. After inline extraction, the outer transaction can have dirty annotation state; nested persistence can contend with that state and stale attached objects. The poller’s “no Shepherd held during Phase B” architecture does not carry over merely by calling its helpers inside intake.

   `wbiaRegistered == false` is an eligibility predicate, **not a claim**. Two callers can both load DTOs, observe absence, and POST. Response validation checks returned IDs; it does not prevent duplicate requests or establish concurrent WBIA behavior.

   There is also an ordinary race: intake’s attached annotation says `false`, another caller registers it, and fresh Phase A returns `null`. That same `null` also means missing, parked, ineligible, or load failure. Phase C returns `void` and swallows persistence failure, so intake cannot distinguish these outcomes reliably.

   Exhausted/ineligible annotations do **not** “keep retrying”: the poller excludes attempts at the maximum, and the loader rejects them again on resend.

   **Fix:** orchestrate registration outside the caller’s active transaction, reload afterward, and return explicit `ALREADY_REGISTERED`, success, failure, and parked outcomes with checked persistence. Add coordination if duplicate requests are prohibited, or test and document concurrent forced POSTs as supported. Specify parked-state recovery.

   The registration safeguard should apply to every caller handling explicit `FALSE`, including servlet paths; there is no identity-based reason to restrict it to ml-service. The intake predicate correctly skips legacy `null`.

7. **Major — forced registration still does not protect every annotation used by identification.**

   [`registerOneByDto`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:934) forces `dto.annotationId`, not `annotationAcmId`. Equality holds for pristine ml-service annotations, but the spec claims the latter contract without enforcing equality.

   More substantially, `beginIdentifyAnnotations` passes query **and target** annotations to `sendAnnotationsAsNeeded`. Intake preregisters only queries. A pending ml-service target can therefore receive non-forced registration and have its `acmId` rewritten while another import or vector result references it. A later forced preregistration of that annotation no longer has the assumed pristine state.

   **Fix:** enforce stable registration for every explicit-`FALSE` annotation in the identification registration path, including targets. Force the identifier used by identification, validate it consistently, and preserve legacy registration behavior for `null`.

8. **Major — `identificationRoot` needs an ownership and lifecycle contract.**

   Parameters are inherited. If a reused parent already carries `identificationRoot`, copying its parameters into option tasks marks those options as ceilings. A vector winner can then stop at `T_vec`, omitting HotSpotter.

   Conversely, when a single HotSpotter option uses `U` itself and `_doIdentify` fans out, retaining **only** `importTaskId` deletes `U`’s invocation marker. The invocation ceiling then depends on structural accidents again. A single-option `topTask.setParameters(newTaskParams)` can also erase an earlier marker unless placement is specified.

   Keeping `importTaskId` on the cleared parent is otherwise sound: without `ibeis.identification`, its method renderability remains unchanged.

   **Fix:** strip inherited root flags and mark only the owning umbrella after parameter construction. Preserve that marker when clearing an actual umbrella; exclude it from children. An invocation ID compared with the owning task ID is more robust than an inherited Boolean.

9. **Major — §5 does not test the blocking contracts.**

   Happy-path staging, stopped WBIA, and restart checks are insufficient. Add:

   - Actual `statsAnnotations → iaSummaryJson` checks for error-plus-pending, live retries, parent persistence errors, skipped classes, and exactly one total per annotation.
   - Selection during dispatch, newer pending invocation versus older completed invocation, repeated extraction runs, foreign grandchildren, and inherited root markers.
   - Failure injection at **internal** intake commits and final commits, plus a separate-PM check of parent/children durability before publication.
   - Concurrent poller/intake registration, already-registered/null-DTO races, Phase-C commit failure, parked annotations, false targets, differing IDs, and empty WBIA.
   - Single-option marker preservation, explicit algorithm overrides, fastlane/ProjectIA, and slow-but-live timeout behavior.
   - Compilation and datastore reload checks asserting the actual selected node and rendered subtree.

   Datastore testing support already exists in this repository. If these checks remain manual, specify controlled failure/concurrency procedures and require them before deployment.

**Verdict: NOT CONVERGED. Blocking items: 1–9.**
---

## Round 3 (spec rev 3)

**NOT CONVERGED.** Revision 3 fixes several mechanisms, but invocation selection, scope, recovery, and WBIA registration still have blocking gaps. Static review only; no files modified, tests run, or version-control commands used.

The nine round-2 dispositions are:

| R2 | Disposition |
|---|---|
| 1 | **Partially resolved.** Pending beats error; maximum age is accurately described. Live `SEND_FAILED` retries can still stop polling prematurely. |
| 2 | **Partially resolved.** Pending umbrellas become selectable, but newest direct task does not reliably identify the newest invocation. |
| 3 | **Partially resolved.** The parameterless extraction root is excluded; other builder-produced extraction ancestors and reused umbrellas remain unsafe. |
| 4 | **Partially resolved.** Checked persistence precedes dispatch. Recovery still misses exceptions, later-group persistence failures, and existing callers’ final commits. |
| 5 | **Resolved.** The chosen annotation-registration helper is public; the inaccessible registrar composition is removed. |
| 6 | **Partially resolved.** Nested registration Shepherds are removed. The consumer/poller “same UUID” assertion is not guaranteed. |
| 7 | **Partially resolved.** Queries and targets reach the shared path, and the annotation helper forces `acmId`. Image registration and failure propagation remain wrong. |
| 8 | **Partially resolved.** Own-ID comparison and retention through `_doIdentify` fix the original ceiling bugs. Reused parents can still contaminate unrelated pipeline tasks. |
| 9 | **Partially resolved.** Datastore and staging coverage improve, but important counterexamples and fixture requirements remain unspecified. |

The builder walk establishes this much:

| Builder | Invocation-ID propagation under the proposal | Remaining boundary issue |
|---|---|---|
| Intake core, multiple options/classes | Fresh umbrella ID copied into option tasks | Arbitrary input encounters and inherited run-local parameters |
| Intake core, single option | Umbrella itself retains its own invocation ID | Status rules incorrectly exclude this required option |
| `Embedding.findMatchProspects` | `new Task(task)` copies the ID into leaves | Late task creation cannot define invocation recency |
| `IAGateway._doIdentify` | Original parameter snapshot reaches children; cleared parent retains both IDs | Delayed old fan-out can supersede a newer run |
| Ml-service through `intakeAnnotationsUnder` | Encounter grouping and fresh IDs supply the intended boundary | Recovery across groups |
| `ParallelIdentify` | Encounter-local intake gets a fresh ID | Parent link and final persistence occur after dispatch |
| `IBEISIA.processCallback` | Encounter-local intake gets a fresh ID | Legacy extraction ancestors remain climbable |
| `IA.handleRest` | Annotation intake replaces inherited invocation ID | Unrestricted annotation lists and reused roots break scope |
| ProjectIA fastlane | Each single-annotation intake gets a fresh ID | Final-commit failure leaves durable pending options |

Thus propagation is sound for ordinary intake-created descendants. The stronger claims—nothing outside the run carries its ID, renderability cannot select an unsafe node, and legacy ascent cannot cross the umbrella—are false.

1. **Major — §§4.3–4.4 select the newest task, not the newest invocation.**

   Run `U1` waits in the HotSpotter queue. A newer resend creates `U2`. Then `_doIdentify` processes `U1` and creates its per-annotation children **after** `U2`. Those children are now the newest direct tasks and carry `U1`’s invocation ID. Both the results selector and status aggregate can revert to the older run. [Fan-out creation]( /mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/servlet/IAGateway.java:403)

   **Fix:** resolve invocation umbrellas first and compare their creation times, independently of descendant creation times. Within import scope, choose the newest eligible invocation, then select/render/aggregate that invocation.

2. **Major — umbrella renderability bypasses encounter validation, and reuse invalidates subtree isolation.**

   `IA.handleRest` accepts an arbitrary `annotationIds` list. A pending umbrella containing annotations from two encounters becomes renderable immediately. The hoist rejects its encounter scope, then returns `winner`—the same mixed umbrella.

   Reusing an existing umbrella as `handleRest`’s root also attaches a fresh invocation beneath it. Its own annotations and stamp can pass the proposed checks while recursive rendering includes another invocation. Media intake additionally copies that root’s invocation ID into detection tasks outside the original identification run. [Root reuse and intake]( /mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/IA.java:546)

   **Fix:** validate candidates before selection, including fallback winners. Partition or reject mixed-encounter intake. Prevent orchestration beneath identification umbrellas or filter rendering by invocation. Scrub invocation IDs, `mlServiceV2Match`, and inherited `ibeis.identification` at new pipeline boundaries; the latter also corrupts `noWork` detection when reused.

3. **Major — the old-tree climb still crosses a real extraction ancestor.**

   The parameterless-root stop covers `Annotation.createFromApi`’s `E`, but legacy detection callbacks create an extraction task using `new Task(task)`, retain inherited parameters, add `embeddingExtraction`, and remove `ibeis.detection`.

   For a same-encounter tree `D → E → {M_old, M_new}`, ascent from `S_new` passes through `M_new` into `E`. `E` has annotations, parameters, a parent, no media assets, and no detection marker. Its page renders both runs. [Extraction builder]( /mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/identity/IBEISIA.java:1733)

   Invariant I1 is also literally false: old `_doIdentify` clears the parent’s parameters while giving stamped parameters to children.

   **Fix:** stop at extraction orchestration, including `embeddingExtraction`; document the actual legacy copy/clear exceptions. Validate rendered descendants where structural scope is otherwise ambiguous.

4. **Major — §4.4 excludes a required single-option task.**

   With one class, one HotSpotter option, and one annotation, intake uses `U` itself as the option. `_doIdentify` does not create children. The rule “umbrella … never counted” therefore produces no leaves and reports completed/skipped while HotSpotter is pending. A single-vector umbrella has the same problem before its leaves exist and after their rollback.

   **Fix:** distinguish an orchestration-only umbrella from an umbrella that is also an option. Count every required childless option, including `U`; classify skipped only when that annotation actually received no option.

   Otherwise, the existing `iaSummaryJson` formula remains usable: increment exactly one terminal-or-pending counter and one `numLatestTasks` per non-trivial annotation. Skipped may increment completed plus an informational skipped counter. The null-invocation fallback is exactly today’s status behavior **if it executes before the new age/leaf rules**.

5. **Major — persistence recovery is incomplete, including for existing callers.**

   Successful commit/rebegin on the same PersistenceManager does not inherently detach these objects. Repeating `makePersistent`/`storeNewTask` on an attached persistent umbrella does not inherently insert another row. The attached `U.setParent(D)` relationship is sound.

   Failure handling is not sound:

   - A later group’s `persistFailed` rolls back earlier inline vector work, but step 4 marks only `D`.
   - Step 7 likewise leaves already committed options pending after dispatch exceptions.
   - The next group’s internal commit can persist earlier vector leaves; step 5’s claim that **every** vector option becomes childless is false.
   - ParallelIdentify, callback, and `handleRest` use unchecked final commits. ProjectIA reports final-commit failure without reconciling durable options.
   - Existing-overload callers attach `U` afterward, so their parent link is not durable before dispatch.

   **Fix:** retain recovery state across the entire operation, including task IDs, dispatch outcomes, and successful commit boundaries. Reload and reconcile rolled-back or undispatched required branches through a fresh Shepherd. Apply checked final commits and structured failure handling to all four callers.

6. **Major — the forced image helper sends the wrong identifier.**

   `sendMediaAssetsForceId` checks presence using `ma.getAcmId()` but puts **`ma.getUUID()`** in `image_uuid_list`. It neither implements the spec’s claimed `acmId` contract nor validates successful image registration before annotation registration. Existing `acmId != UUID` assets fail this sequence. Setting a missing `acmId` to UUID covers only the missing-ID case. [Actual payload]( /mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:219)

   **Fix:** provide a public checked image-registration operation that forces the exact image `acmId` and validates acknowledgement/presence. Enforce image eligibility and valid UUIDs before calling it; verify the image exists before posting its annotation.

7. **Major — the stated registration-failure path does not exist.**

   `sendAnnotationsAsNeeded` catches exceptions into `sendAnnotMAException`; `beginIdentifyAnnotations` never checks that field and proceeds to `sendIdentify`. A helper returning `false` also needs explicit conversion into failure.

   The existing empty annotation/image-list guards reject a healthy empty WBIA before registration can populate it. The generic already-present `continue` can bypass setting an explicit-FALSE annotation to TRUE. Targets also enter `allAnns` without the query-side validity filter, although `sendAnnotationForcedByAcmId` requires caller-screened eligibility.

   **Fix:** explicitly abort identification on failed forced registration; accept successful empty-list responses; handle FALSE annotations before generic presence skipping; validate queries and targets consistently. Keep null/TRUE annotations on the legacy registration path. Do not allow missing-annotation repair to bypass the stable-ID contract.

8. **Major — consumer/poller concurrency does not always force the same UUID.**

   The consumer forces `annotation.acmId`; `registerOneByDto` forces `dto.annotationId`. Explicit FALSE records can already have differing IDs because the pre-fix non-forced path rewrites `acmId` without setting the registration flag. Concurrent absence checks can therefore lead to two different forced IDs. `REGISTERED_ALREADY_PRESENT` is a preflight outcome, not proof of concurrent POST behavior. [Poller registration]( /mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:934)

   Annotation indeed has **no JDO `<version>` strategy**; its ordinary `version` field does not supply optimistic locking. That confirms the metadata claim but does not establish registration correctness.

   **Fix:** make both paths register and confirm the same actual identification `acmId`. Test concurrent POST acknowledgement and flag persistence, including existing differing-ID records and commit failures.

9. **Major — live retries remain terminal to the import aggregate.**

   Pending-over-error fixes `[vector=error, HotSpotter=pending]`. It does not fix `[vector=completed, HotSpotter=SEND_FAILED with queued retry]`: the import reports complete and polling can stop. A successfully accepted retry can retain the stored error until callback completion. Naming this an unchanged non-goal does not satisfy goal 6.

   **Fix:** represent queued/accepted retries as pending and make retry exhaustion terminal. Otherwise explicitly weaken goal 6 to permit polling to stop during live retries. The seven-day maximum-age exception is now accurately documented.

10. **Major — §5 needs executable fixtures and the missing failure matrix.**

    Testcontainers can exercise intake and `statsAnnotations`, but the cited pattern alone is insufficient:

    - Initialize `FeatureType.initAll` **before** fixture transactions; it rolls back/commits internally.
    - Persist/reload ImportTask, its encounters, annotations, media/features, and IA root; configure identification requested and image validity.
    - Inject an IA.json fixture with taxonomy/class options. `iaConfig()` constructs a fresh instance, so `setJson` on another instance does not configure intake.
    - Stub vector dispatch as well as queue publication to avoid OpenSearch/ml-service/file-queue dependencies.

    **Fix:** specify those seams and fixtures, then add delayed old fan-out, single-HotSpotter umbrella, mixed encounters, root reuse, non-root extraction, later-group/final-commit failure, live retries, empty WBIA, differing IDs, invalid targets, and concurrent registration tests. Assert the reloaded selected node, rendered subtree, and actual `statsAnnotations → iaSummaryJson` output.

**Verdict: NOT CONVERGED. Blocking items: 1–10.**
---

## Round 4 (spec rev 4)

**NOT CONVERGED.** Revision 4 still has blocking gaps in rendering isolation, recovery, retry status, and forced registration. Read-only review; no tests, version-control commands, or file changes.

| Round-3 finding | Disposition |
|---|---|
| 1 — Invocation recency | **Resolved.** Comparing umbrella creation times defeats delayed old fan-out. |
| 2 — Encounter validation and reuse | **Partially resolved.** Candidate validation fixes direct selection; reused umbrellas still render other runs. Finding 1 below. |
| 3 — Extraction ascent | **Resolved for the reported escape.** The `embeddingExtraction` stop blocks ascent through that extraction ancestor; the `_doIdentify` exception is documented. |
| 4 — Single-option umbrella | **Resolved.** Required childless option umbrellas are counted. The orchestration-only case remains ambiguous. Finding 8. |
| 5 — Persistence recovery | **Partially resolved.** Per-group commits preserve earlier groups. Recovery still loses undispatched or unsuccessfully published options. Finding 2. Existing callers’ final commits are accepted non-goals. |
| 6 — Forced image identifier | **Partially resolved.** The proposed builder forces image `acmId` and validates acknowledgement. Eligibility checks remain unspecified. Finding 6. |
| 7 — Registration failure handling | **Partially resolved.** Query exceptions reach the outer catch. Query/target plumbing and repair outcomes remain incomplete. Finding 6. |
| 8 — Poller alignment | **Partially resolved.** Changing the request builder does not align the presence check and response validator. Finding 4. |
| 9 — Live retries | **Partially resolved.** Retry exhaustion is terminal, but resetting status to null does not guarantee pending. Finding 3. |
| 10 — Executable tests | **Partially resolved.** Fixtures and intake dispatch isolation improve substantially; fault injection and the remaining regression matrix are incomplete. Finding 7. |

1. **Major — §§4.0/4.3 validate the umbrella’s annotations, not its rendered subtree.**

   Counterexample: `U1(A, run=U1)` is reused as `IA.handleRest`’s root. A later intake attaches `U2(B, run=U2)` beneath it. Selecting results for A returns U1: its own annotations satisfy A’s encounter set. `matchResultsJson` recursively renders U2 as well.

   Scrubbing inherited run IDs changes descendants’ parameters; it does not remove them from U1’s rendered subtree. B can belong to another encounter, or the same encounter but another run. Both violate goal 2.

   **Fix:** prohibit attaching new pipeline or identification work beneath an identification umbrella. Create or resolve a separate orchestration parent. Since encounter-filtered rendering is excluded, structural isolation must enforce the rendering boundary.

   For the listed **unreused old builder shapes**, the new extraction stop closes the reported climb above M: extraction, media, and zero-annotation orchestration ancestors stop ascent. That does not establish subtree isolation for reused new umbrellas.

2. **Major — §4.1’s recovery record cannot reconcile every durable option.**

   Two remaining cases:

   - U, vector V, and HotSpotter H are committed. Vector dispatch throws before the intake core returns its outcome. The caller has no completed group record; H was never published.
   - H publication fails and intake marks `QUEUE_FAILED`. The group’s final commit fails, rolling that mark back. Recovery leaves HotSpotter tasks alone on the assertion that their jobs are durable. H has no job.

   Either leaves a durable option pending without a job or error mark.

   **Fix:** populate recovery state before dispatch, expose it even when dispatch throws, and record each option’s actual publication outcome. Recovery must mark every unpublished branch, including rolled-back queue failures, while preserving successfully published jobs. Check the recovery commit.

3. **Major — §4.4’s null reset can still report complete during a live retry.**

   `Task.getStatus` derives null status from WBIA logs. A failed send can leave the newest `sendIdentify` log containing `"HTTP error code"`; `getStatus` explicitly returns `error` for that text. A task-details entry saying “retry queued” does not override those logs.

   Thus `[vector=completed, HotSpotter=null with HTTP-error log and scheduled retry]` remains terminal to `iaSummaryJson`. This occurs before the seven-day exception.

   **Fix:** persist an explicit non-terminal retry status, or give a durable retry marker precedence over log-derived terminal states. Ensure polling cannot observe a terminal send failure while a retry is scheduled or accepted.

   The **old-tree status fallback does equal today’s behavior** when rule 2 runs before all new aggregation/age rules, including preservation of today’s no-task pending count.

4. **Major — §4.2.5 changes only part of the poller’s identifier contract.**

   `registerOneByDto` still:

   - accepts presence under either `annotationId` or `annotationAcmId`;
   - validates the POST response against `dto.annotationId`.

   With differing IDs, presence under the old ID can incorrectly mark TRUE while identification’s `acmId` is absent. Conversely, a successful POST forcing `acmId` fails validation against `annotationId`.

   **Fix:** compute one effective forced identifier and use it consistently for presence, request construction, acknowledgement validation, and success classification.

5. **Major — the FALSE-only branch cannot satisfy goal 7 after successful registration.**

   Registration changes an ml-service annotation’s flag to TRUE. If WBIA later lacks that annotation, §4.2.5 sends it through today’s non-forced path because TRUE is treated as legacy. That path can mint another UUID and rewrite `acmId`.

   The pending-registration flag does not identify annotation provenance. This violates the explicit “every path … never through the on-demand path” goal.

   **Fix:** preserve forced-registration provenance independently of pending state, and apply it to initial registration and missing-annotation repair. Alternatively, force current `acmId` for all explicit-flag records if that compatibility change is accepted. Keep null-flag legacy behavior explicit.

6. **Major — §4.2.5 still lacks a complete checked registration contract.**

   `validForIdentification` checks bbox availability; it does not establish image eligibility or valid UUIDs. `registerImageIfMissing` relies on eligibility already checked by the poller. Reusing its builder does not transfer those checks to the consumer.

   Also, `sendAnnotationsAsNeeded(allAnns, shep)` receives neither query membership nor the actual target list. `iaCheckMissing` has the same role problem, and today its `srtn` remains null, so successful repair never requests another identify attempt.

   **Fix:** specify a role-aware registration API over the actual query/target lists; check media presence, image validity, usable URI/dimensions, and UUID validity before POSTs. Return checked repair outcomes and explicitly update `iaCheckMissing`’s retry decision. Remove failed targets from the list actually sent to WBIA. Preserve the null-flag branch and test it.

7. **Major — §5 does not yet verify the remaining contracts reproducibly.**

   The datastore fixtures and Dispatcher seam are useful, but the plan omits concrete fault injection for processor-created Shepherds, recovery commits, identify sending/priming, and registration HTTP/presence checks.

   **Fix:** name the injection or Mockito strategy, restore the IA configuration override and PMF state after tests, and add cases for findings 1–6. In particular: exception before intake returns; queue failure followed by commit failure; real HTTP-error logs during retry; differing-ID presence/acknowledgement; TRUE-origin repair; empty WBIA; missing image `acmId`; and null-flag compatibility. Assert reloaded state and actual `statsAnnotations → iaSummaryJson` output.

8. **Minor — §4.4 conflates childless options with childless orchestration umbrellas.**

   An existing intake caller with no applicable options returns a childless U without `ibeis.identification`. Rule 3 includes childless umbrellas, but also says orchestration parents are never counted; rule 4 defines skipped only when U has children.

   **Fix:** distinguish required options from orchestration explicitly. Define childless `noWork` status for every caller rather than leaving it pending or relying on the age policy.

**Verdict: NOT CONVERGED. Blocking items: 1–7.**
---

## Round 5 (spec rev 5)

Read-only review completed. No tests, version-control commands, or file modifications.

| Round-4 finding | Revision-5 disposition |
|---|---|
| 1 — Umbrella renders other runs | **Partially resolved.** Fresh-root substitution prevents future reuse; previously reused trees remain unsafe. Finding 1. |
| 2 — Recovery loses unpublished options | **Resolved for the reported cases.** Outcomes survive dispatch exceptions; recovery includes unsuccessfully published HotSpotter options. A separate partial-vector-dispatch gap remains. Finding 3. |
| 3 — Retry reads terminal from logs | **Partially resolved.** Explicit `requeued` defeats log-derived error after it is committed. The earlier terminal commit remains observable. Finding 2. |
| 4 — Poller uses inconsistent identifiers | **Resolved.** `forcedId` governs presence, request, validation, and success. |
| 5 — TRUE records later missing | **Resolved by the revised scope.** Goal 7 now concerns FALSE records; reconciliation owns TRUE records. Accepted non-goal. |
| 6 — Registration contract incomplete | **Resolved at design level.** Actual query/target lists, eligibility, image minting, checked registration, and forced repair outcomes are specified. |
| 7 — Tests not reproducible | **Partially resolved.** Datastore and HTTP strategies are specified; the remaining failure cases and identify isolation need additions. Finding 4. |
| 8 — Childless orchestration status | **Resolved.** Every caller marks `noWork` as `INVALID_MATCH_CONFIG`; aggregation sees a failed leaf. |

1. **Major — §4.3 can still return a previously reused umbrella containing multiple runs.**

   A concrete pre-fix tree uses one annotation A and two HotSpotter options per invocation:

   ```
   R(media assets)
   └── U1(A)
       ├── H1(A)
       ├── H2(A)
       └── U2(A)           ← later handleRest request reused U1
           ├── H3(A)
           └── H4(A)
   ```

   Existing `handleRest` permits this reuse and adds media assets to U1. H3/H4 fail today’s renderability test: their parent has two children and the same annotation count. U2 also fails: its parent has three children. U1 passes because R has only one child.

   Consequently, today’s selection returns U1, and §4.3 initializes `best = U1`. The climb stops at R, returning U1; `matchResultsJson` renders both invocations. The media/extraction checks apply only to **ancestors**, so U1’s own media marker does not protect the initial winner.

   This uses one encounter and no mixed-encounter request. None of the decided non-goals excludes it. Future root substitution does not repair an existing tree.

   **Fix:** validate the initial legacy winner too. When it is an orchestration/reused node, seed selection from the newest identification leaf in the allowed import partition and climb to its closed identification subtree—U2 here. Add this exact pre-fix fixture.

   **B:** The new `selectRun` umbrella is isolated under the proposed builder contracts. For ordinary unreused old trees, the extraction, media, and zero-annotation stops prevent ascent above the identification umbrella. They do not establish isolation for every returned legacy winner.

2. **Major — §4.4 still permits “complete” while a retry is alive.**

   [`_sendIdentificationTask`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/servlet/IAGateway.java:515) stores `error` and calls `updateDBTransaction()` before throwing. Its `finally` commits again before `_doIdentify` handles the exception.

   Revision 5 schedules the retry and then sets `requeued`. Between scheduling and committing that reset, another transaction can observe:

   ```
   vector = completed
   HotSpotter = error
   retry = scheduled
   ```

   Rules 3–4 therefore report complete before seven days. With several queries, a subsequent send can prolong that interval before the outer caller commits the reset.

   **Fix:** persist a non-terminal send/retry state before any failure-path commit. Let `_doIdentify` commit terminal `SEND_FAILED` only when requeue is refused; accepted retries retain an explicit pending state. Do not commit a terminal error in the inner sender while retry disposition remains undecided.

   **C:** Rule 2 otherwise exactly preserves today’s status behavior, provided its no-task branch still counts one pending annotation and new age/aggregation rules never run afterward.

3. **Major — caught vector exceptions can leave durable, jobless pending leaves.**

   [`Embedding.findMatchProspects`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/Embedding.java:303) attaches `new Task(task)` before matching operations finish. Suppose matching throws after that attachment and before the leaf receives a terminal status.

   §4.2.3 catches the exception, marks the **option task** `DISPATCH_FAILED`, and continues. If the group commit succeeds, persistence by reachability saves the unfinished leaf. Recovery does not run.

   §4.4 counts childless tasks and excludes the errored option because it has children. The unfinished vector leaf has no queued job and remains pending until the age policy. The failed dispatch is therefore neither terminal nor immediately visible in the aggregate.

   **Fix:** on vector dispatch failure, terminalize every unfinished descendant created by that dispatch, preserving already terminal leaves, and represent annotations whose leaves were never created. Alternatively, roll back that option’s partial subtree before persisting an explicit failed option. Test an exception **after leaf attachment**, followed by a successful group commit.

   **E/F:** Per-group recovery fixes the reported unpublished-option cases. This remaining gap arises from exception containment combined with leaf-only aggregation.

4. **Minor — §5 still needs concrete identify isolation and the missing regression fixtures.**

   Dispatcher isolation does not cover `_doIdentify`, which reaches `IAQueryCache`, `waitForIAPriming`, and real retry scheduling. Mocking `RestClient` alone does not isolate those paths. Also, `MatchResultTest` imports `mockConstruction` but does not provide the claimed construction-mocking pattern.

   **Fix:** specify selective static mocks or injectable seams for cache lookup, priming, and requeue scheduling while executing the real identify handler. Delegate processor-created Shepherds to real datastore managers with scripted commit outcomes; restore overrides and priming state after each test. Add:
   
   - the pre-fix reused tree from finding 1;
   - a second-manager status poll between send failure and retry-state commit;
   - partial vector-leaf creation followed by successful commit;
   - FALSE-only registration against initially empty WBIA lists.

   **G:** The principal fixtures are executable, but the current matrix cannot prove the remaining contracts.

**D:** The revised forced-registration contract covers query failures, target pruning, missing image `acmId`, FALSE repair, and poller identifier alignment. The explicitly preserved null-flag path introduces no specified regression.

**Verdict: NOT CONVERGED. Blocking items: 1–3.**
---

## Round 6 (spec rev 6)

Read-only review completed. No tests, version-control commands, or file modifications.

| Round-5 finding | Revision-6 disposition |
|---|---|
| 1 — Previously reused umbrella renders several runs | **Not resolved.** Today’s root fallback runs before the new seed. Finding 1. |
| 2 — Terminal error observable before retry disposition | **Resolved.** The inner failure commit now stores explicit non-terminal `requeued`. |
| 3 — Partial vector dispatch leaves unfinished work | **Partially resolved.** Attached leaves become terminal, but missing-leaf failures remain hidden by another algorithm’s leaf. Finding 2. |
| 4 — Identify isolation and regression fixtures | **Resolved at design level.** Selective static mocks and the four requested cases are specified. |

1. **Major — §4.3 still returns an orchestration root containing multiple runs.**

   The specified pre-fix fixture remains a counterexample:

   ```text
   R(media) → U1(A, media) → H1, H2, U2(A) → H3, H4
   ```

   Excluding U1 removes the only renderable candidate. However, “today’s selection” includes root fallback: [`onlyRoots()`](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/ia/Task.java:760) calls `getRootTask()` on the remaining candidates and returns R. Filtering candidates does not filter their ancestors.

   Thus `winner` is R, the identification-seed branch never executes, and the climb returns R immediately. Its recursive rendering includes both runs. The §4.3 table’s claimed U2 landing does not follow from its algorithm. The `embeddingExtraction` stop cannot protect an initial winner already above the identification umbrella.

   **Fix:** explicitly run only today’s **renderable-selection phase** before attempting the identification seed. Delay root fallback until afterward, and validate every returned initial node—including roots reached indirectly—against the orchestration and encounter boundaries. Execute the exact reused-root fixture.

2. **Major — §4.4 needs fallback per option, not only when the annotation has no leaves anywhere.**

   Consider U(A,B) with vector option V and HotSpotter option H. Vector dispatch creates V_A, then throws before creating V_B. Revision 6 correctly terminalizes V and V_A. HotSpotter subsequently completes H_B.

   For B, rule 3 finds H_B. Consequently, rule 4’s “when no childless task … holds the annotation” fallback never runs. V’s `DISPATCH_FAILED` is omitted, and B reads `completed` instead of `error`.

   The same omission can hide live work during [`_doIdentify`’s incremental fan-out commits](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/servlet/IAGateway.java:410): H has acquired H_A but not H_B, so H is excluded for B while V_B reads completed. If A selects a newer completed run, the whole import can report complete while B’s HotSpotter work remains alive—before seven days. Both requests can concern one encounter.

   **Fix:** for each dispatched option holding the annotation, use that option’s annotation leaf; if it has none, count the option itself. Aggregate those representatives afterward. Preserve an option’s identity after `_doIdentify` clears its identification parameters. Test partial vector dispatch with a completed sibling algorithm, plus the intermediate fan-out state.

3. **Major — the new §4.3 identification seed bypasses import isolation.**

   When today’s scoped selection returns null because all identification candidates belong to IMP2, the next instruction—“newest candidate carrying `ibeis.identification`”—can select an IMP2 task for IMP1. The climb compares ancestors with the **winner’s** stamp, so it accepts the foreign subtree.

   This introduces a fallback that violates goal 5; it is not an excluded mixed-encounter case.

   **Fix:** apply the existing import partition to seed candidates: prefer IMP1, otherwise unstamped, never foreign. Apply the same rule to the new-tree fallback paths. Add an opt-in selector test containing only foreign-stamped identification tasks.

For the remaining requested checks:

- **B:** `selectRun`’s new umbrellas are isolated under the specified builder contracts. Ordinary old-tree ascent respects the extraction boundary, but finding 1 still permits an initial landing above an umbrella.
- **C:** The revised retry ordering closes the reported terminal window. Finding 2 prevents confirmation of run-wide liveness accounting. Rule 2 otherwise preserves today’s newest-direct-task behavior, including the no-task pending branch and existing old-tree early-completion limitation.
- **D:** The forced contract covers queries, target pruning, image `acmId` minting, forced missing-annotation repair, and poller identifier alignment. No null-flag regression is specified.
- **E:** No additional handled-exception recovery gap was found for the new ml-service caller, assuming recovery commits succeed. Process death after pre-dispatch persistence, or a failed recovery commit, can still leave jobless pending tasks; no crash-recovery guarantee is specified.
- **F/G:** No further blocker found. §5’s seams and fixtures are executable at design level, but must include the counterexamples above.

**Verdict: NOT CONVERGED. Blocking items: 1–3.**
---

## Round 7 (spec rev 7)

Read-only review completed. No tests, version-control commands, or file modifications.

| Round-6 finding | Revision-7 disposition |
|---|---|
| 1 — Root fallback precedes identification seed | **Resolved.** §4.3 explicitly orders renderable selection, seed, then unchanged root fallback. |
| 2 — Sibling leaf hides another option’s failure or pending work | **Partially resolved.** The multi-option counterexamples are fixed; single-option fan-out still fails. Finding 1. |
| 3 — Identification seed bypasses import isolation | **Resolved.** Phase 2 now applies the same import partition. |

1. **Major — §4.4 loses the dispatched option’s identity when a single-option umbrella gains children.**

   For a single HotSpotter option, U(A,B) **is the option task**. During [`_doIdentify`’s incremental fan-out commits](/mnt/c/Wildbook-multialgo/src/main/java/org/ecocean/servlet/IAGateway.java:410), H_A can exist before H_B.

   Rule 3 now treats U’s children as options. For B, none holds B, so rule 4 reports `completed` plus `skipped`, although B’s dispatched HotSpotter work remains alive.

   Whole-import counterexample: a newer completed run for A exists alongside U(A,B); both runs carry this import’s stamp and contain annotations from one encounter. A selects the newer run; B selects U and reads skipped/completed. The import reports complete during U’s fan-out, before seven days. The retry-pending change does not prevent this.

   **Fix:** preserve explicit identity for an umbrella that is itself an option through `_doIdentify`’s parameter reset. Enumerate `{U}` for that run regardless of children, then choose its annotation leaf or fall back to U. Add the intermediate single-HotSpotter fan-out fixture, including the whole-import counterexample.

2. **Major — §4.1 recovery contradicts itself when a published option is the umbrella.**

   For a single-HotSpotter group, `option.task == umbrella`. Suppose pre-dispatch persistence succeeds, publication succeeds, and the group’s final checked commit fails.

   Recovery says both “mark the umbrella error `MATCH_PERSIST`” and “leave option tasks whose job was published alone.” Marking U errors the published option itself. With one annotation and no fan-out children yet, §4.4 counts U as terminal and reports complete while its durable job remains queued. This concerns the new ml-service caller, not an excluded existing caller’s commit.

   **Fix:** explicitly exempt an umbrella that is also a successfully published option from the umbrella error mark. Mark D, preserve U’s pending state, and let its job finish. Add a single-HotSpotter publication-success/final-commit-failure recovery test.

The remaining requested checks:

- **B:** Under the specified builder contracts, a selected new umbrella renders only its run. The old-tree climb stops at extraction/detection orchestration and cannot cross those boundaries; the reused-root fixture now lands on U2. Phase 3 retains today’s root fallback without climbing.
- **C:** Retry ordering closes the terminal window between send failure and requeue disposition. Findings 1–2 still prevent confirmation of goal 6. The old-tree **status** fallback exactly preserves today’s newest-direct-task rule, including the existing no-task pending branch.
- **D:** The specified identify-time forced contract covers queries, target pruning, image `acmId` minting, missing-annotation repair, and poller identifier alignment. No null-flag regression is specified.
- **E:** No additional handled-exception case leaves a durable option jobless and unmarked, assuming recovery commits succeed. Process death and failed recovery commits retain the explicitly documented exposure.
- **F/G:** No further blocker found. §5’s seams and fixtures are executable at design level, but coverage is insufficient without the two single-option cases above.

**Verdict: NOT CONVERGED. Blocking items: 1–2.**
---

## Round 8 (spec rev 8)

No findings: **0 Critical, 0 Major, 0 Minor** against the stated goals and decided non-goals.

| Round-7 finding | Revision-8 disposition |
|---|---|
| 1 — Single-option umbrella loses option identity during fan-out | **Resolved.** §4.0 preserves `identificationOption` through `_doIdentify`’s reset; §4.4 enumerates marked options. Before H_B exists, B uses U and remains pending. |
| 2 — Recovery errors an umbrella whose job was published | **Resolved.** §4.1 explicitly exempts a successfully published single-HotSpotter umbrella. U remains pending; D receives the recovery error. |

1. **B — Confirmed under the specified builder contracts.** An eligible new umbrella contains only its run, and its annotation set lies within the query encounter. The root-substitution rule prevents later runs being attached beneath it. For old trees, the media/detection/extraction, zero-annotation, parameterless-root, encounter and stamp boundaries prevent climbing above the identification umbrella. `embeddingExtraction` stops the legacy extraction case; the reused-root fixture stops at U2.

2. **C — Confirmed for the selected runs, excluding the documented maximum age.** Every applicable option contributes one representative. Missing fan-out leaves fall back to the pending option; a pending sibling outranks errors. The explicit `requeued` commit closes the send-failure terminal window. The old-tree calculation preserves today’s newest-direct-task `getStatus()` rule and the no-task pending branch. Retry status changes intentionally apply to the shared handler.

3. **D — Complete as specified.** FALSE queries fail the job on registration failure; FALSE targets are pruned from the actual identify list. Images without `acmId` receive their UUID before eligibility validation and are forced under that identifier. Annotation registration, missing-annotation repair, and poller presence/request/acknowledgement checks consistently use `acmId`. The null-flag path remains legacy.

4. **E — No additional handled-exception hole found.** The prefilled outcome and recovery record cover undispatched options, partial vector dispatch, publication failures whose marks roll back, and failed final group commits. Successfully committed groups remain durable. Process death and failed recovery commits retain the explicitly documented exposure.

5. **F — No additional blocker from revision 4’s changes.** Root reuse, recovery coverage, forced identifiers and retry state have concrete contracts consistent with the decided scope.

6. **G — §5 is sufficient and executable at design level.** The dispatcher/configuration seams, existing Mockito support, PostgreSQL fixture pattern and handler isolation support the proposed tests. Revision 8 adds both missing single-HotSpotter counterexamples, including intermediate polling and publication-success/final-commit-failure recovery.

Read-only review; no tests, version-control commands or file modifications.

**Verdict: CONVERGED — no Critical/Major findings.**
