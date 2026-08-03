# HotSpotter second-pass matching in the React bulk import page

Issue: [#1691](https://github.com/WildMeOrg/Wildbook/issues/1691) — "Restore Hotspotter
bulk matching as a second-pass option to Giraffespotter" (milestone 10.12)

## Problem

GiraffeSpotter's old JSP bulk-import page had an admin-only button, *"Resend unidentified
encounters to Hotspotter only"*. It ran a **second pass** of identification restricted to:

1. the **HotSpotter** matching algorithm only (the first pass uses MiewID), and
2. only Encounters that still had **no MarkedIndividual** after the first pass.

The bulk-import page is now React (`BulkImportTask.jsx`), and that button was never
carried over. It must be recreated.

## Why not port the legacy JSP

The legacy implementation is `appadmin/resendBulkImportIDHotspotterOnly.jsp`, which exists
only on `origin/giraffe`. It is a 281-line copy-paste of an older `IA.intakeAnnotations`
with two edits (filter identOpts to `query_config_dict.sv_on`; skip encounters with an
individual).

That copy has since gone stale against `IA.java`:

- it predates `Embedding.findMatchProspects()` (`IA.java:479`), the short-circuit that makes
  vector-based configs match inline — a ported copy would silently take the legacy queue
  path for vector configs;
- it predates `ParallelIdentify`, so it cannot honour `iaMatchThreads`;
- it carries a real bug: `taskParameters.optString("importTaskId", itask.getId())` where
  `put` was intended, so the import task id is never recorded on the task parameters.

Reviving it means maintaining a second, divergent copy of the matching driver. Instead we
parameterize the existing driver.

## Design

### 1. `IBEISIA.isHotspotterQueryConfig(JSONObject queryConfigDict)`

`IBEISIA.java:~255` already decides "is this HotSpotter?" inline:

```java
if (queryConfigDict != null && queryConfigDict.toString().indexOf("sv_on") > -1)
    isHotspotter = true;
```

Extract it to a named static so the definition lives in one place, and **tighten it**:

```java
public static boolean isHotspotterQueryConfig(JSONObject queryConfigDict) {
    return (queryConfigDict != null) && queryConfigDict.optBoolean("sv_on", false);
}
```

The `toString().indexOf(...)` form classifies `{"sv_on": false}` as HotSpotter and would
also match `sv_on` appearing anywhere in a nested value. The strict form matches what the
legacy giraffe filter used (`optBoolean("sv_on")`). No shipped config contains
`"sv_on": false`, so the only practical effect at the existing call site is that such a
config would now be eligible for the fast lane — which is the correct behavior. This is a
deliberate, documented behavior fix, not an incidental one.

### 2. `IA.intakeAnnotations`: a `matchingAlgorithmFilter` task parameter

No method signature change. `matchingAlgorithms` is **already** read out of
`parentTask.getParameters()` inside this method (`IA.java:432`), so a sibling key follows
established precedent, persists on the Task for auditability, and — critically — rides
along in `ParallelIdentify`'s `paramsStr` with no plumbing.

The opt-selection block is reordered so parameters are read *before* opts are filtered:

```java
JSONObject newTaskParams =
    (parentTask == null || parentTask.getParameters() == null)
        ? new JSONObject() : parentTask.getParameters();
boolean hotspotterOnly =
    "hotspotter".equals(newTaskParams.optString("matchingAlgorithmFilter", null));

List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));

// identOpts() returns null (not empty) for a class with no ident config, so every
// filter is guarded by opts != null — exactly as the current code guards the
// default-removal loop at IA.java:420. The trailing continue catches null/empty.
if (!hotspotterOnly && opts != null) removeWhereDefaultIsFalse(opts);   // today's behavior
// today's matchingAlgorithms swap, unchanged: only replaces opts when the converted
// list is NON-EMPTY (an explicit matchingAlgorithms:[] must keep defaults, not clear them)
if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
    List<JSONObject> newOpts = toList(newTaskParams.optJSONArray("matchingAlgorithms"));
    if (!newOpts.isEmpty()) opts = newOpts;
}
if (hotspotterOnly && opts != null)
    keepOnly(opts, opt -> IBEISIA.isHotspotterQueryConfig(opt.optJSONObject("query_config_dict")));
if (opts == null || opts.isEmpty()) continue;              // today's guard
```

Note the **nesting**: `sv_on` lives inside each option's `query_config_dict`, not at the
option's top level (see `IAJsonProperties.identOpts()` and the `_id_conf` entries in
`IA-wbia.json`). Applying the helper to the option object itself would match nothing and
silently filter every option away.


Two properties this shape guarantees:

- The branch key is a **boolean**, never "filter is non-null". An unrecognized value such as
  `matchingAlgorithmFilter=typo` is therefore completely inert — it cannot accidentally
  re-enable every `default:false` algorithm. (The JSP additionally rejects unknown values
  outright; see §4.)
- The `parentTask.getParameters() == null` guard that exists today is retained.

**Why skip the `default:false` removal.** On live GiraffeSpotter, the HotSpotter `_id_conf`
entry is marked `"default": false` precisely so it does *not* run in the first pass. If the
second pass applied the default filter, HotSpotter would be removed before the HotSpotter
filter ever ran, and the button would do nothing. The legacy giraffe code bypassed the
default filter the same way.

Note the repo's own `devops/deploy/.dockerfiles/tomcat/IA-wbia.json` has HotSpotter with
*no* `default` key, which `optBoolean("default", true)` treats as enabled — so on that
config the first pass runs both MiewID and HotSpotter and the second pass would repeat
HotSpotter work. That is harmless and still correct; it just means the button is most
useful on installs (like live GiraffeSpotter) where HotSpotter is explicitly
`default:false`.

`IA.java` mutating the parsed params object is not a concern: `Task.setParameters()`
serializes to a string and `getParameters()` parses a fresh `JSONObject`, so `IA` only ever
mutates its own local copy. Moving the read earlier does not change that.

### 3. `ParallelIdentify.processOne()`: re-check at the point of use

`processOne()` re-fetches each encounter on its own Shepherd, so both new checks belong
here — filtering the driver's `encIds`/annotation lists is not sufficient on its own:

- **`unidentifiedOnly`** — an encounter assigned an individual between the driver building
  `encIds` and this worker running would otherwise still be sent to HotSpotter.
- **hotspotter applicability** — the worker currently builds `matchMeAnns` from
  `validForIdentification` alone; for a hotspotter request it must additionally drop
  annotations whose class has no HotSpotter option, or `IA.intakeAnnotations` receives an
  inapplicable annotation and returns an empty `topTask` (§4).

Both parameters (`unidentifiedOnly`, `matchingAlgorithmFilter`) already travel in the task
parameters, so they reach the worker via `paramsStr` with no new plumbing. The worker parses
them, applies the individual re-check, builds the **filtered** `matchMeAnns`, and creates
`subParentTask` **only if that list is non-empty** — so an all-skipped or no-applicable
encounter persists nothing:

```java
Encounter enc = ws.getEncounter(encId);
if (enc == null) { ws.rollbackDBTransaction(); return null; }
JSONObject params = (paramsStr == null) ? new JSONObject() : new JSONObject(paramsStr);
// re-check at the point of use — the encounter may have been assigned an individual
// between the driver building encIds and this worker running.
if (params.optBoolean("unidentifiedOnly", false) && enc.hasMarkedIndividual()) {
    ws.rollbackDBTransaction();
    return null;   // same "no work for this encounter" contract as enc == null
}
boolean hotspotterOnly = "hotspotter".equals(params.optString("matchingAlgorithmFilter", null));
List<Annotation> matchMeAnns = new ArrayList<Annotation>();
if (enc.getAnnotations() != null) {
    for (Annotation a : enc.getAnnotations()) {
        if (!IBEISIA.validForIdentification(a)) continue;
        if (hotspotterOnly && !IA.annotationHasHotspotterOpt(ws, a)) continue;
        matchMeAnns.add(a);
    }
}
if (matchMeAnns.isEmpty()) { ws.rollbackDBTransaction(); return null; }
Task subParentTask = new Task();   // created only now that there is real work
```

Returning `null` omits the encounter from `initiatedJobs`, the existing contract for "this
encounter produced no work". No writes have happened, so the rollback is a no-op and the
`finally`'s `rollbackAndClose()` remains correct.

The serial loop in the JSP applies the identical filter and the same
"create-`subParentTask`-only-if-non-empty" ordering (its current code creates and stores the
subtask *before* collecting `matchMeAnns`, leaving an orphan empty subtask on a no-work
encounter — this reordering fixes that too). These are the changes `ParallelIdentify` and the
serial driver need; nothing else in `ParallelIdentify`'s structure changes.

### 4. `resendBulkImportID.jsp`: two optional query parameters

When both are absent, behavior is identical to today.

- `algorithm=hotspotter` — sets `matchingAlgorithmFilter` in `taskParameters`, and
  **requires `request.isUserInRole("admin")`**. The Shiro chain (`web.xml:129`) admits
  `researcher` to this JSP, so the admin requirement must be enforced in the page body.
  Any other non-blank `algorithm` value is rejected with `400`.
- `unidentifiedOnly=true` — sets `unidentifiedOnly` in `taskParameters`, skips
  `enc.hasMarkedIndividual()` encounters in the serial loop, and omits them when building
  the `encIds` list for `ParallelIdentify`.

In the serial loop the check must **re-read** the encounter rather than trusting the
`queryEnc` instance obtained from `itask.getEncounters()` at request start. Over a long
serial run that JDO instance can be stale relative to a concurrent individual assignment —
the same staleness the parallel path avoids by re-fetching on its own Shepherd. So the
serial path re-fetches (`myShepherd.getEncounter(queryEnc.getId())`) immediately before the
`hasMarkedIndividual()` check.

#### Restructure: an early-out before the root is created

The current JSP stores `parentTask` and calls `itask.setIATask(parentTask)` **before** the
encounter loop. With `unidentifiedOnly`, the completely normal "everything is already
identified" state would then attach an **empty** root IA task to the import, which
`iaSummaryJson()` reads as a started-but-incomplete pipeline — flipping an otherwise
`complete` import to `processing-pipeline` / "identification not started". That is a
regression on a normal path.

The fix is a **static early-out computed before any write**, keeping the existing "attach the
root up front, then dispatch" ordering for the has-work path:

1. Validate parameters and (for hotspotter) `isUserInRole("admin")` — **before any write**.
2. Build `targetEncs`: iterate `itask.getEncounters()`, and when `unidentifiedOnly`, re-fetch
   each and drop those with `hasMarkedIndividual()`.
3. Collect the **eligible** annotations across `targetEncs` — `IBEISIA.validForIdentification(ann)`
   **and**, for a hotspotter request, `IA.annotationHasHotspotterOpt(ann)` (helper below); for
   a non-hotspotter request just `validForIdentification`, today's set. **If that set is empty,
   return the no-work response and persist nothing** — no `parentTask`, no `setIATask`.
4. Otherwise proceed exactly as today: create + store `parentTask`, `setIATask`, then the
   serial loop or `ParallelIdentify` over `targetEncs`, with each per-encounter dispatch
   applying the same eligibility filter (§3) and creating its `subParentTask` only for a
   non-empty annotation set.

Attaching the root up front (rather than deferring it until dispatch produces work) is the
deliberately robust choice: the serial loop commits each `subParentTask`/`childTask` as it
goes, so if a later encounter throws, the root is already attached and the earlier committed
identification work stays associated with the import. Deferring the attach would orphan that
committed work on a mid-loop exception.

**Residual, documented, cosmetic.** Step 3 removes the common empty-root case (nothing
eligible at request time). One narrow window remains: if a concurrent process assigns
individuals to *every* remaining target between step 2 and dispatch, all `unidentifiedOnly`
re-checks come back "already identified" and the attached root ends up empty — a
`processing-pipeline` status instead of `complete`. This is not corrupting, is no worse than
the existing first-pass button (which likewise attaches its root up front), and requires an
all-or-nothing race to hit. It is not worth the transaction gymnastics of a deferred,
exception-safe attach.

#### Applicability helper

```java
// IA.java (already imports IBEISIA and IAJsonProperties; avoids an
// IAJsonProperties -> IBEISIA dependency cycle)
public static boolean annotationHasHotspotterOpt(Shepherd myShepherd, Annotation ann) {
    List<JSONObject> opts = IAJsonProperties.iaConfig().identOpts(myShepherd, ann);
    if (opts == null) return false;   // identOpts() returns null when the class has no
                                      // ident config — the "no option" case, must be false
    for (JSONObject opt : opts) {
        if (IBEISIA.isHotspotterQueryConfig(opt.optJSONObject("query_config_dict")))
            return true;
    }
    return false;
}
```

The null guard is load-bearing: `identOpts()` returns `null` (not an empty list) when
`getIdentConfig(taxonomy, iaClass)` is null, which is *exactly* the "this class has no
HotSpotter option" case the filter must classify as `false`. Without it that case throws and
becomes a `500` instead of the specified no-work response.

`identOpts` is a config lookup (no DB/ml round-trips), so applying it per annotation is
cheap. Because a hotspotter request drops any annotation whose class has no HotSpotter option
*before* dispatch, `IA.intakeAnnotations` never receives a set that would filter down to an
empty `opts` and return an empty `topTask`. That removes the round-4 #1 gap at its root — no
change to `IA.intakeAnnotations`'s return contract, and no documented residual case.

This also closes the normal "all already identified" case (round 4 #2): step 4 returns
before `setIATask`, so a `complete` import's IA task/status is left untouched.

#### Response contract

All responses are a single JSON object with a **boolean** `success`, so a caller can test
`success === true` unambiguously (the current code emits the *string* `"false"`, which is
truthy in JS — see §6). Failures carry `error`:

| Case | Status | Body |
| --- | --- | --- |
| ok | 200 | `{"success":true,"initiatedJobs":[...]}` |
| missing/blank `importIdTask` | 400 | `{"success":false,"error":"..."}` |
| unknown `algorithm` value | 400 | `{"success":false,"error":"..."}` |
| `algorithm=hotspotter`, non-admin | 403 | `{"success":false,"error":"..."}` |
| no such import task | 404 | `{"success":false,"error":"..."}` |
| no eligible encounters/annotations | 200 | `{"success":false,"error":"..."}` |
| exception | 500 | `{"success":false,"error":"..."}` |

(The current JSP answers `200`/`success:"false"` for both a missing id and an unknown task;
`400`/`404` are strictly more informative and both existing consumers already treat any
non-`200` as failure.)

Normalizing `success` to boolean changes the first-pass button's response shape too, but the
UI is being changed in the same commit and both buttons go through the same `success === true`
check, so they stay consistent.

The response deliberately does **not** report a skipped count. A worker-side skip returns
`null` from `processOne()` and is simply absent from `initiatedJobs`, so any such counter
would be wrong in exactly the race this design added handling for. The UI already knows the
unidentified count before it fires the request, so the counter would buy nothing.

Two pre-existing defects in this file are fixed, because the response body now carries
information the UI must read:

- the body is currently written **twice** — `out.println(res)` in the `try` *and* in the
  `finally` — so a success response is two concatenated JSON objects. It only "works"
  because the current UI checks `response.status === 200` and never parses. Now written
  once.
- `finally` uses `rollbackDBTransaction()` + `closeDBTransaction()`; switch to
  `rollbackAndClose()`, consistent with `ParallelIdentify.processOne` and the
  Shepherd-leak fixes.

**The cleanup must not be able to swallow the response.** `rollbackAndClose()` can throw, so
it is wrapped in its own `try`/`catch` and the single body write happens after it
unconditionally. Otherwise a cleanup failure would turn an already-successful first-pass
request into a response with no body — a regression for the existing button.

### 5. Server-authoritative HotSpotter availability

The button must not appear on installs with no HotSpotter configured, and must not be
hardcoded to GiraffeSpotter.

Deriving this client-side from `site-settings.iaConfig` is **not sound**: that block is
built by iterating `sciNames`, which comes from `myShepherd.getAllTaxonomyCommonNames()` →
`CommonConfiguration.getIndexedPropertyValues("genusSpecies")`. That list is independent of
`IA.json`, so a taxonomy can have a HotSpotter `_id_conf` while being absent from
`genusSpecies` — producing a false negative that silently hides the button.

Instead the server answers the question directly:

```java
// IAJsonProperties
public boolean hasHotspotterIdentOpt()   // recursive walk of getJson(), true if any
                                         // object has a query_config_dict that
                                         // isHotspotterQueryConfig()
```

Walking the raw config rather than looking for `_id_conf` by name keeps it independent of
key naming and `@`-link resolution. The config is a few KB, so the walk is negligible next
to the rest of `site-settings`.

`SiteSettings` emits `settings.put("hotspotterAvailable", iaConfig.hasHotspotterIdentOpt())`.

This flag is **install-global**, not per-import: it gates whether the button is *possible*
on this install, not whether *this specific import's* iaClasses have a HotSpotter option. That
per-import distinction is handled correctly at action time by §4's eligibility filter — an
import whose species has no HotSpotter option yields an empty eligible set and the honest
no-work response — so the button can safely render on the coarser install-global signal
without a per-import preflight before it appears. On GiraffeSpotter (single species) the two
signals coincide anyway.

### 6. `BulkImportTask.jsx`

- Second `MainButton` beside the existing "Send to Identification", rendered only when
  `siteData?.hotspotterAvailable` **and** `userRoles.includes("admin")`.
- Unidentified count from `task.encounters.filter(e => !e.individualId).length`
  (`BulkImport.taskJson` emits `individualId` only when the encounter has one).
- Disabled when sending, when that count is `0`, under the same conditions as the existing
  button (task complete, detection complete, a location selected), **and** when the first
  identification pass is not terminal — `iaSummary.identificationStatus` must be `complete`
  or `skipped`.
- Helper text: *"N of M encounters have no individual assigned"*, or *"All encounters
  already have an individual assigned"* at zero.
- `window.confirm` before firing (the legacy giraffe button had `confirmCommitID()`; this
  pass is expensive and admin-only).
- The two buttons' send logic collapses into one
  `sendToIdentification({ algorithm, unidentifiedOnly })`. Success is
  `response.data?.success === true` (a strict boolean check — the endpoint now emits a
  boolean, and a truthy check would wrongly pass the string `"false"`), **not**
  `status === 200`. On failure the alert prefers `error.response?.data?.error` (axios rejects
  4xx/5xx) or `response.data.error` (the `200`/`success:false` no-work case), falling back to
  the existing generic localized message.
- New i18n keys in `en/de/es/fr/it`: `BULK_IMPORT_SEND_TO_HOTSPOTTER`,
  `BULK_IMPORT_HOTSPOTTER_CONFIRM`, `BULK_IMPORT_HOTSPOTTER_UNIDENTIFIED_COUNT`,
  `BULK_IMPORT_HOTSPOTTER_NONE_UNIDENTIFIED`.

#### On the identification-terminal gate

This is belt-and-braces, and deliberately so. It is *already* implied transitively: a
running first pass leaves `identificationStatus: "sent"` → `pipelineComplete` false
(`ImportTask.java:768`) → `taskJson` rewrites `status` to `processing-pipeline` → the
existing `task.status !== "complete"` condition disables the button. But that is a three-hop
chain across two files, and "the second pass must not race the first pass" is the whole
point of the feature. One explicit condition makes the contract legible and survives a
refactor of the status-override chain.

**Server-side rejection of a non-terminal first pass is deliberately not implemented.**
Computing identification state server-side means calling `iaSummaryJson()`, which
`BulkImport.java:886` documents as slow enough that it is already confined to the detailed
task fetch. The endpoint is admin-only, the UI gate closes the realistic path, and the
failure mode of racing is duplicated match work — wasteful and confusing, not corrupting.
Paying a known-slow aggregation on every request to defend an admin against a hand-crafted
URL is not proportionate. Flagged here as an accepted, bounded risk rather than an oversight.

## Testing

**Java** — `IdentificationTest.java` already mocks `IAJsonProperties.identOpts` and already
uses an `sv_on` fixture. `IA.intakeAnnotations` **removes entries from the returned list**,
so the mock must use `thenAnswer` to hand back a *fresh* list per invocation; otherwise an
earlier null-filter case strips `default:false` HotSpotter before the hotspotter case runs.

- hotspotter filter selects the HotSpotter opt even when it is `default:false`, using an
  option shaped like a real `identOpts()` entry — i.e. `sv_on` **nested** under
  `query_config_dict`, so the nesting bug cannot regress
- no filter reproduces today's behavior (HotSpotter dropped when `default:false`)
- unrecognized filter value behaves exactly as no filter
- `isHotspotterQueryConfig`: `sv_on:true` → true; `sv_on:false` → **false**; absent → false;
  null → false
- `hasHotspotterIdentOpt()` (install-global) on a config with and without a HotSpotter entry
- `annotationHasHotspotterOpt()` (per-annotation applicability): true when the annotation's
  iaClass has a HotSpotter opt, false when it does not — this is the check that keeps a
  hotspotter request from ever handing `IA.intakeAnnotations` an inapplicable annotation

**Jest** — `BulkImportTask.test.js`: hidden when `hotspotterAvailable` is false; hidden for
non-admin; disabled at zero unidentified; disabled while `identificationStatus` is `sent`;
correct URL parameters on click; a `200`/`success:false` no-work response surfaces
`data.error` rather than a generic message; a `{"success":"false"}` (string) response is
treated as failure, proving the `=== true` check.

**Not automated.** The JSP's `403`/`400`/no-work paths. JSPs are not unit-testable in this
repo, and standing up a servlet-container integration test for one endpoint is
disproportionate. The JSP logic is kept to a handful of lines so it is verifiable by
inspection. Stated here so the gap is not mistaken for coverage. Manual validation before
sign-off **must explicitly cover**:

- `algorithm=hotspotter` as a `researcher` (not admin) → `403` + `success:false`, no root
  Task persisted
- `algorithm=somethingelse` → `400` + `success:false`
- `algorithm=hotspotter` on an import whose species has no HotSpotter option → `200`,
  `success:false`, **no root Task attached and the import's prior status unchanged**
- `algorithm=hotspotter&unidentifiedOnly=true` when every encounter is already identified →
  `200`, `success:false`, prior status unchanged
- missing `importIdTask` → `400`; unknown `importIdTask` → `404`
- no parameters → unchanged first-pass behavior (single parseable JSON body, `status 200`)

## Out of scope

- Converting `resendBulkImportID.jsp` to a `/api/v3` endpoint. The sibling button still uses
  the JSP; migrating both is its own change.
- The `ParallelIdentify` / serial driver duplication.

## Sign-off

Issue requires GCF sign-off on feature completion.
